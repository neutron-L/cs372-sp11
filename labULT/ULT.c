#include <assert.h>

/* We want the extra information from these definitions */
#ifndef __USE_GNU
#define __USE_GNU
#endif /* __USE_GNU */
#include <assert.h>
#include <stdbool.h>
#include <stdlib.h>
#include <ucontext.h>

#include "ULT.h"
#include "ult_queue.h"

static ult_queue_t ready_queue;
static ThrdCtlBlk *running_thread, *scheduler;
static Tid next_tid;

static void stub(void (*root)(void *), void *arg);

static void ULT_Init();
static void swtch(ThrdCtlBlk *, ThrdCtlBlk *);
static void *schedule(void *);

Tid ULT_CreateThread(void (*fn)(void *), void *parg) {
    // assert(0); /* TBD */
    ULT_Init();

    ThrdCtlBlk *tcb = NULL;
    stack_t stack;
    if ((tcb = (ThrdCtlBlk *)calloc(1, sizeof(ThrdCtlBlk))) == NULL) {
        return ULT_NOMEMORY;
    }

    if (fn != NULL) {

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
        tcb->ctx.uc_mcontext.__gregs[REG_RIP] = (greg_t)stub;
        tcb->ctx.uc_mcontext.__gregs[REG_RDI] = (greg_t)fn;
        tcb->ctx.uc_mcontext.__gregs[REG_RSI] = (greg_t)parg;
        tcb->ctx.uc_mcontext.__gregs[REG_RSP] = (greg_t)(stack.ss_sp + ULT_MIN_STACK - 8);
    }

    tcb->tid = next_tid;
    ++next_tid;

    return tcb->tid;
}

Tid ULT_Yield(Tid wantTid) {
    ULT_Init();

    // assert(0); /* TBD */
    Tid ret = ULT_INVALID;
    // 当前线程负责将wantTid的线程调整到队首
    if (wantTid == ULT_SELF) {
        return running_thread->tid;
    }
    if (ult_queue_is_empty(ready_queue)) {
        return (wantTid == ULT_ANY) ? ULT_NONE : ULT_INVALID;
    }

    if (wantTid != ULT_ANY) {
        int size = ult_queue_size(ready_queue);
        while (ult_queue_front(ready_queue)->tid != wantTid && size-- > 0) {
            ThrdCtlBlk *tcb = ult_dequeue(ready_queue);
            ult_enqueue(ready_queue, tcb);
        }
    }
    ret = ult_queue_front(ready_queue)->tid;

    // 切换到scheduler
    return ret;
}

Tid ULT_DestroyThread(Tid tid) {
    ULT_Init();

    assert(0); /* TBD */
    return ULT_FAILED;
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

static void ULT_Init() {
    static bool inited = false;

    if (inited) {
        return;
    }
    inited = true;

    // 将当前thread(main thread)初始化并赋值running thread
    // main thread 得到tid 0
    running_thread = ULT_CreateThread(NULL, NULL);

    ready_queue = ult_new_queue();
    scheduler = ULT_CreateThread(schedule, NULL); // 这里有点奇怪，内部还会调用一次init，只不过什么也不做
}

static void swtch(ThrdCtlBlk *tcb1, ThrdCtlBlk *tcb2) {
    tcb1->swtch_flag = 1;
    // 保存当前上下文到 oucp
    if (getcontext(&tcb1->ctx) == -1) {
        perror("getcontext");
    }
    // 切换到目标上下文 ucp
    if (tcb1->swtch_flag) {
        tcb2->swtch_flag = 0;
        if (setcontext(&tcb2->ctx) == -1) {
            perror("setcontext");
        }
    }
}

static void *schedule(void *_) {
    (void)_;

    while (true) {
        ult_enqueue(ready_queue, running_thread);
        ThrdCtlBlk *tcb = ult_dequeue(ready_queue);
        running_thread = tcb;
        setcontext(&running_thread->ctx);
    }
}