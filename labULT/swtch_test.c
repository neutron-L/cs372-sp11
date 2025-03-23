#include <assert.h>

/* We want the extra information from these definitions */
#ifndef __USE_GNU
#define __USE_GNU
#endif /* __USE_GNU */
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <ucontext.h>

// 定义两个用户上下文

struct Thread {
    ucontext_t ctx;
};

struct Thread th1, th2, main_th;

// 函数用于设置上下文的栈和入口点
void setup_context(ucontext_t *ctx, void (*func)()) {
    // 初始化上下文
    getcontext(ctx);

    stack_t stack;
    stack.ss_sp = malloc(1024 * 1024);
    stack.ss_size = 1024 * 1024;
    stack.ss_flags = 0;

    ctx->uc_stack = stack;
    ctx->uc_link = NULL;
    // makecontext(&tcb->ctx, (void (*)(void))fn, 1, parg); may not use this
    ctx->uc_mcontext.gregs[REG_RIP] = (greg_t)func;
    ctx->uc_mcontext.gregs[REG_RSP] = (greg_t)(stack.ss_sp + 1024 * 1024 - 8);

    ctx->uc_stack.ss_sp = malloc(1024 * 1024 * sizeof(char));
    ctx->uc_stack.ss_size = 1024 * 1024;
    ctx->uc_stack.ss_flags = 0;

    ctx->uc_link = NULL;
}

void swtch(struct Thread *th1, struct Thread *th2) {
    volatile int flag = 1;
    // 保存当前上下文到 oucp
    if (getcontext(&th1->ctx) == -1) {
        perror("getcontext");
    }
    // 切换到目标上下文 ucp
    if (flag) {
        flag = 0;
        if (setcontext(&th2->ctx) == -1) {
            perror("setcontext");
        }
    }
}

// 线程函数1
void thread_func1() {
    swtch(&th1, &th2);
    printf("In thread 1\n");

    printf("Go to thread 2\n");
    swtch(&th1, &th2);

    // 从ctx2上下文切换回来，继续执行
    printf("Back to thread 1\n");
    printf("thread 1 do something...\n");
    printf("Go to thread 2\n");
    swtch(&th1, &th2);
    printf("Back to thread 1\n");
    printf("thread 1 exit...\n");
    swtch(&th1, &th2);
}

// 线程函数2
void thread_func2() {
    swtch(&th2, &th1);

    printf("In thread 2\n");
    printf("thread 2 do something...\n");
    printf("Go to thread 1\n");
    swtch(&th2, &th1);

    printf("thread 2 exit...\n");

    swtch(&th2, &main_th);
}

int main() {
    // 设置ctx1的栈和入口点
    setup_context(&th1.ctx, thread_func1);

    // 设置ctx2的栈和入口点
    setup_context(&th2.ctx, thread_func2);
    swtch(&main_th, &th1);
    printf("Exit\n");

    return 0;
}