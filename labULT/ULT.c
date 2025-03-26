#include <assert.h>

/* We want the extra information from these definitions */
#ifndef __USE_GNU
#define __USE_GNU
#endif /* __USE_GNU */

#include <assert.h>
#include <errno.h>
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <ucontext.h>

#include "ULT.h"
#include "interrupt.h"
#include "ult_queue.h"

static ult_queue_t ready_queue;
static ThrdCtlBlk *running_thread, *scheduler;
static Tid next_tid;
static size_t thread_num;

static void stub(void (*root)(void *), void *arg);

/* 针对ult库接口的队列查找函数，会将线程tid轮转调整到队首，用于destroy或yield接口
   基本是不断pop + push + 判度当前队首，操作时间比较长
   可以考虑为queue添加一个search和remove push front的接口
 */
// 分离线程分配的功能，因为在init时需要初始化scheduler和main thread
static Tid ULT_NewThread(void (*fn)(void *), void *parg);
static bool ULT_Search(ult_queue_t queue, Tid tid);
static void ULT_Init();
static void ULT_DeleteThread(ThrdCtlBlk *);
static void swtch(ThrdCtlBlk *, ThrdCtlBlk *);
static void schedule(void *);

Tid ULT_CreateThread(void (*fn)(void *), void *parg) {
    // assert(0); /* TBD */
    Tid tid = ULT_INVALID;

    interruptsOff();

    ULT_Init();

    // 获取当前进程的信号掩码
    assert(!alarmIsEnabled());
    tid = ULT_NewThread(fn, parg);

end:
    interruptsOn();

    return tid;
}

Tid ULT_Yield(Tid wantTid) {
    interruptsOff();

    ULT_Init();

    // assert(0); /* TBD */
    Tid ret = wantTid;
    // 当前线程负责将wantTid的线程调整到队首
    if (wantTid == ULT_SELF || wantTid == running_thread->tid) {
        ret = running_thread->tid;
        goto end;
    }
    if (ult_queue_is_empty(ready_queue)) {
        ret = (wantTid == ULT_ANY) ? ULT_NONE : ULT_INVALID;
        goto end;
    }

    if (wantTid != ULT_ANY) {
        if (!ULT_Search(ready_queue, wantTid)) {
            ret = ULT_INVALID;
            goto end;
        }
        if (ult_queue_front(ready_queue)->state == RUNNABLE) {
            ret = ult_queue_front(ready_queue)->tid;
            swtch(running_thread, scheduler);
        } else {
            ret = ULT_INVALID;
            goto end;
        }
    } else {
        // 找到第一个非退出线程
        int size = ult_queue_size(ready_queue);
        while (size-- > 0 && ult_queue_front(ready_queue)->state != RUNNABLE) {
            ult_enqueue(ready_queue, ult_dequeue(ready_queue));
        }

        if (size < 0) {
            ret = ULT_NONE;
            goto end;
        }
        ret = ult_queue_front(ready_queue)->tid;
        swtch(running_thread, scheduler);
    }

end:
    interruptsOn();
    return ret;
}

Tid ULT_DestroyThread(Tid tid) {
    interruptsOff();

    ULT_Init();

    Tid ret = ULT_INVALID;

    // assert(0); /* TBD */
    if (tid == ULT_SELF) {
        running_thread->state = TERMINATED;
        --thread_num;
        swtch(running_thread, scheduler);
        // 如果是最后一个线程，则会被schedule调度回来
        ++thread_num;
        ret = ULT_NONE;
        goto end;
    }
    if (tid == ULT_ANY) {
        // 只剩下当前running线程和scheduler
        if (thread_num == 1 + 1) {
            ret = ULT_NONE;
            goto end;
        }

        // 找到第一个非退出线程
        int size = ult_queue_size(ready_queue);
        while (size-- > 0 && ult_queue_front(ready_queue)->state == TERMINATED) {
            ult_enqueue(ready_queue, ult_dequeue(ready_queue));
        }

        tid = ult_queue_front(ready_queue)->tid;
    }
    if (!ULT_Search(ready_queue, tid)) {
        ret = ULT_INVALID;
        goto end;
    }
    if (ult_queue_front(ready_queue)->state == TERMINATED) {
        ret = ULT_INVALID;
        goto end;
    }
    ult_queue_front(ready_queue)->state = TERMINATED;
    --thread_num;
    ret = tid;
end:
    interruptsOn();
    return ret;
}

static void
stub(void (*root)(void *), void *arg) {
    // thread starts here
    Tid ret;
    root(arg); // call root function
    ret = ULT_DestroyThread(ULT_SELF);
    assert(ret == ULT_NONE); // we should only get here if we are the last thread.
    exit(0);                 // all threads are done, so process should exit
}

static Tid ULT_NewThread(void (*fn)(void *), void *parg) {
    // +1是因为我们额外创建了一个scheduler线程
    if (thread_num >= ULT_MAX_THREADS + 1) {
        return ULT_NOMORE;
    }
    ThrdCtlBlk *tcb = NULL;

    if ((tcb = (ThrdCtlBlk *)calloc(1, sizeof(ThrdCtlBlk))) == NULL) {
        return ULT_NOMEMORY;
    }

    if (fn != NULL) {
        stack_t stack;

        if ((stack.ss_sp = malloc(ULT_MIN_STACK)) == NULL) {
            free(tcb);
            return ULT_NOMEMORY;
        }
        stack.ss_size = ULT_MIN_STACK;
        stack.ss_flags = 0;

        // 初始化上下文
        getcontext(&tcb->ctx);

        tcb->ctx.uc_stack = stack;
        tcb->ctx.uc_link = NULL;
        // makecontext(&tcb->ctx, (void (*)(void))fn, 1, parg); may not use this
        tcb->ctx.uc_mcontext.gregs[REG_RIP] = (greg_t)stub;
        tcb->ctx.uc_mcontext.gregs[REG_RDI] = (greg_t)fn;
        tcb->ctx.uc_mcontext.gregs[REG_RSI] = (greg_t)parg;
        tcb->ctx.uc_mcontext.gregs[REG_RSP] = (greg_t)(stack.ss_sp + ULT_MIN_STACK - 8);

        // 此时上下文中信号是阻塞的，一般线程需要清空
        assert(sigismember(&tcb->ctx.uc_sigmask, SIGALRM));
        sigemptyset(&tcb->ctx.uc_sigmask);

        // 初始时，main thread为当前running thread，随后由scheduler加入队列
        ult_enqueue(ready_queue, tcb);
    } else {
        if (running_thread != NULL) {
            return ULT_INVALID;
        }
        running_thread = tcb;
    }

    assert(!alarmIsEnabled());

    tcb->tid = next_tid;
    tcb->state = RUNNABLE;
    ++next_tid;
    ++thread_num;

    return tcb->tid;
}

static bool ULT_Search(ult_queue_t queue, Tid wantTid) {
    int size = ult_queue_size(queue);
    while (size-- > 0 && ult_queue_front(queue)->tid != wantTid) {
        ThrdCtlBlk *tcb = ult_dequeue(queue);
        ult_enqueue(ready_queue, tcb);
    }

    return size >= 0;
}

static void ULT_Init() {
    static bool inited = false;

    assert(!alarmIsEnabled());

    if (inited) {
        return;
    }
    inited = true;

    // registerHandler();
    // 将当前thread(main thread)初始化并赋值running thread
    // main thread 得到tid 0
    ready_queue = ult_new_queue();
    assert(ULT_isOKRet(ULT_NewThread(NULL, NULL)) && running_thread != NULL);
    assert(ULT_isOKRet(ULT_NewThread(schedule, NULL))); // 这里有点奇怪，内部还会调用一次init，只不过什么也不做

    scheduler = ult_dequeue(ready_queue);
    assert(ult_queue_is_empty(ready_queue));

    // scheduler需要屏蔽信号
    sigemptyset(&scheduler->ctx.uc_sigmask);
    sigaddset(&scheduler->ctx.uc_sigmask, SIGALRM);

    assert(!alarmIsEnabled());

    swtch(running_thread, scheduler);
}

static void ULT_DeleteThread(ThrdCtlBlk *tcb) {
    free(tcb->ctx.uc_stack.ss_sp);
    free(tcb);
}

static void swtch(ThrdCtlBlk *tcb1, ThrdCtlBlk *tcb2) {
    volatile int swtch_flag = 1;
    // 保存当前上下文到 oucp
    if (getcontext(&tcb1->ctx) == -1) {
        perror("getcontext");
    }
    assert(!alarmIsEnabled());
    // 切换到目标上下文 ucp
    if (swtch_flag) {
        swtch_flag = 0;
        if (setcontext(&tcb2->ctx) == -1) {
            perror("setcontext");
        }
    }
}

static void schedule(void *_) {
    (void)_;
    ThrdCtlBlk *tcb = NULL;
    ucontext_t ctx;

    while (true) {
        // 检查信号
        getcontext(&ctx);
        assert(sigismember(&ctx.uc_sigmask, SIGALRM) && sigismember(&scheduler->ctx.uc_sigmask, SIGALRM));

        ult_enqueue(ready_queue, running_thread);

        tcb = NULL;
        // 在这里实际销毁退出线程
        while (!ult_queue_is_empty(ready_queue) && (tcb = ult_dequeue(ready_queue)) && tcb->state == TERMINATED) {
            ULT_DeleteThread(tcb);
        }

        if (tcb || (ult_queue_size(ready_queue) == 1)) {
            running_thread = tcb;
            // printf("switch to %d\n", tcb->tid);
            swtch(scheduler, running_thread);
        }
    }
}