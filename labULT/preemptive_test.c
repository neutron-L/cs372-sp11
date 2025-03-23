#include <assert.h>
#include <stdio.h>
#include <unistd.h>
#include "ULT.h"
#include "interrupt.h"

#define EXTRA_THREADS 5
#define LOOP_TIMES 100

/* 抢占式ULT测试，应该可以看到各个线程交替执行直至结束 */

void thread_run(void * name){
  int x;
  for(x = 0; x < LOOP_TIMES; x++){
    printf("Thread %s Running\n", (char *)name);
    usleep(10000);
  }
  printf("Thread %s Finished\n", (char *)name);
}


int main(){
  registerHandler();
  
  int x,y;

  Tid threads[EXTRA_THREADS];
  char * names[EXTRA_THREADS] = {"Alice", "Bob", "Cindy", "David", "Eric"};

  for(x = 0; x < EXTRA_THREADS; x++){
    threads[x] = ULT_CreateThread((void*) thread_run, (void*) names[x]);
  }

  for(y = 0; y < LOOP_TIMES; y++){
    printf("Main Thread Running\n");
    usleep(10000);
  }

  printf("Main Thread Resuming\n");
  printf("Main Thread Exitting\n");
  
  return 0;
}


