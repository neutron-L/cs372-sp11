#include <assert.h>

/* We want the extra information from these definitions */
#ifndef __USE_GNU
#define __USE_GNU
#endif /* __USE_GNU */
#include <ucontext.h>
#include <stdlib.h>

#include "ULT.h"


static Tid next_tid;


static void stub(void (*root)(void *), void *arg);

static void * schedule(void *);

Tid 
ULT_CreateThread(void (*fn)(void *), void *parg)
{
  // assert(0); /* TBD */
  ThrdCtlBlk * tcb = NULL;
  stack_t stack;
  if ((tcb = (ThrdCtlBlk *)calloc(1, sizeof(ThrdCtlBlk))) == NULL) {
    return ULT_NOMEMORY;
  }
  
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

  tcb->tid = next_tid;
  ++next_tid;

  setcontext(&tcb->ctx);

  return tcb->tid;
}



Tid ULT_Yield(Tid wantTid)
{
  assert(0); /* TBD */
  return ULT_FAILED;

}


Tid ULT_DestroyThread(Tid tid)
{
  assert(0); /* TBD */
  return ULT_FAILED;
}

static void
stub(void (*root)(void *), void *arg)
{
  // thread starts here
  Tid ret;
  root(arg); // call root function
  ret = ULT_DestroyThread(ULT_SELF);
  assert(ret == ULT_NONE); // we should only get here if we are the last thread.
  exit(0); // all threads are done, so process should exit
}




