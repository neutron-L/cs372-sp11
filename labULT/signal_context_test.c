#include <assert.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <ucontext.h>
#include <unistd.h>

// 定义两个用户上下文
ucontext_t ctx1, ctx2;

// 信号处理函数
void sig_handler(int signum, siginfo_t *sip, void *contextVP) {
    sigset_t sig_mask;
    ucontext_t *context = (ucontext_t *)contextVP;

    // 获取当前进程的信号掩码
    if (sigprocmask(SIG_BLOCK, NULL, &sig_mask) == 0) {
        // 检查当前信号是否在掩码中被设置为阻塞状态
        if (sigismember(&sig_mask, signum)) {
            printf("Signal %d is blocked in the signal handler.\n", signum);
        } else {
            printf("Signal %d is not blocked in the signal handler.\n", signum);
        }
    } else {
        perror("sigprocmask");
    }

    // 检查当前信号是否在掩码中被设置为阻塞状态
    if (sigismember(&context->uc_sigmask, signum)) {
        printf("Signal %d is blocked in the ucontext.\n", signum);
    } else {
        printf("Signal %d is not blocked in the ucontext.\n", signum);
    }
    swapcontext(&ctx1, &ctx2);
    // 获取当前进程的信号掩码
    if (sigprocmask(SIG_BLOCK, NULL, &sig_mask) == 0) {
        // 检查当前信号是否在掩码中被设置为阻塞状态
        if (sigismember(&sig_mask, signum)) {
            printf("Signal %d is blocked in the signal handler.\n", signum);
        } else {
            printf("Signal %d is not blocked in the signal handler.\n", signum);
        }
    } else {
        perror("sigprocmask");
    }
    // 检查当前信号是否在掩码中被设置为阻塞状态
       if (sigismember(&context->uc_sigmask, signum)) {
        printf("Signal %d is blocked in the ucontext.\n", signum);
    } else {
        printf("Signal %d is not blocked in the ucontext.\n", signum);
    }
}

// 函数用于设置上下文的栈和入口点
void setup_context(ucontext_t *ctx, void (*func)()) {
    char *stack = (char *)malloc(1024 * 1024);
    if (stack == NULL) {
        perror("malloc");
        exit(EXIT_FAILURE);
    }
    ctx->uc_stack.ss_sp = stack;
    ctx->uc_stack.ss_size = sizeof(char) * 1024 * 1024;
    ctx->uc_stack.ss_flags = 0;
    ctx->uc_link = NULL;
    makecontext(ctx, func, 0);
}

void free_context(ucontext_t *ctx) {
    free(ctx->uc_stack.ss_sp);
}

// 线程函数1
void thread_func1() {
    printf("In thread 1\n");

    sigset_t sig_mask;
    // 获取当前进程的信号掩码
    if (sigprocmask(SIG_BLOCK, NULL, &sig_mask) == 0) {
        // 检查当前信号是否在掩码中被设置为阻塞状态
        if (sigismember(&sig_mask, SIGINT)) {
            printf("Signal %d is blocked in the thread 1.\n", SIGINT);
        } else {
            printf("Signal %d is not blocked in the thread 1.\n", SIGINT);
        }
    }

    sleep(3);

    printf("Back to thread 1\n");
    // 获取当前进程的信号掩码
    if (sigprocmask(SIG_BLOCK, NULL, &sig_mask) == 0) {
        // 检查当前信号是否在掩码中被设置为阻塞状态
        if (sigismember(&sig_mask, SIGINT)) {
            printf("Signal %d is blocked in the thread 1.\n", SIGINT);
        } else {
            printf("Signal %d is not blocked in the thread 1.\n", SIGINT);
        }
    }
}

// 线程函数2
void thread_func2() {
    sigset_t sig_mask;

    printf("In thread 2\n");
    // 切换到ctx1上下文
    // 获取当前进程的信号掩码
    if (sigprocmask(SIG_BLOCK, NULL, &sig_mask) == 0) {
        // 检查当前信号是否在掩码中被设置为阻塞状态
        if (sigismember(&sig_mask, SIGINT)) {
            printf("Signal %d is blocked in the thread 2.\n", SIGINT);
        } else {
            printf("Signal %d is not blocked in the thread 2.\n", SIGINT);
        }
        // 检查当前信号是否在掩码中被设置为阻塞状态
        if (sigismember(&sig_mask, SIGALRM)) {
            printf("Signal %d is blocked in the thread 2.\n", SIGALRM);
        } else {
            printf("Signal %d is not blocked in the thread 2.\n", SIGALRM);
        }
    }
    setcontext(&ctx1);
}

void register_handler(int signum, void (*handler)(int, siginfo_t *, void *)) {
    struct sigaction action;
    int error;
    static int hasBeenCalled = 0;

    assert(!hasBeenCalled); /* should only register once */
    hasBeenCalled = 1;

    action.sa_handler = NULL;
    action.sa_sigaction = handler;
    error = sigemptyset(&action.sa_mask); /* SIGNAL_TYPE will be blocked by default
                                           * while handler invoked.
                                           * No mask needed */
    assert(!error);
    action.sa_flags = SA_SIGINFO; /* use sa_sigaction as handler
                                   * instead of sa_handler*/

    if (sigaction(signum, &action, NULL)) {
        perror("Setting up signal handler");
        assert(0);
    }
}

int main() {
    // 注册信号处理函数
    register_handler(SIGINT, sig_handler);
    // 获取当前上下文
    getcontext(&ctx1);
    // 设置ctx1的栈和入口点
    setup_context(&ctx1, thread_func1);

    // 获取当前上下文
    getcontext(&ctx2);
    // 设置ctx2的栈和入口点
    setup_context(&ctx2, thread_func2);
    sigemptyset(&ctx2.uc_sigmask);
    sigaddset(&ctx2.uc_sigmask, SIGINT);
    // sigaddset(&ctx2.uc_sigmask, SIGALRM);

    // 切换到ctx1上下文，开始执行thread_func1
    setcontext(&ctx1);

    // 释放栈内存
    free_context(&ctx1);
    free_context(&ctx2);

    return 0;
}