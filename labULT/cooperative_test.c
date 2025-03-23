#include <assert.h>
#include <stdio.h>
#include "ULT.h"

#define EXTRA_THREADS 5


void self_intruduce(void * name) {
  printf("Hello, my name is %s\n", (char *)name);
  printf("You can call me %s\n", (char *)name);
  printf("What is your name?\n");
}



void thread_run(void * name){
  int x;
  for(x = 0; x < EXTRA_THREADS; x++){
    printf("Thread %s Running\n", (char *)name);
    ULT_Yield(ULT_ANY);
  }
  printf("Thread %s Finished\n", (char *)name);
  ULT_Yield(0); // 都让给主线程执行
}


int main(){
  Tid ret = ULT_CreateThread(self_intruduce, "Jaserv");
  ULT_Yield(ret);

  int x,y;

  Tid threads[EXTRA_THREADS];
  char * names[EXTRA_THREADS] = {"Alice", "Bob", "Cindy", "David", "Eric"};

  for(x = 0; x < EXTRA_THREADS; x++){
    threads[x] = ULT_CreateThread((void*) thread_run, (void*) names[x]);
  }

  for(y = 0; y < EXTRA_THREADS; y++){
    printf("Main Thread Running\n");
    ULT_Yield(ULT_ANY);
  }

  // 最后一次运行，其他五个线程会退出，退出通过yield指定顺序
  for (y = 0; y < EXTRA_THREADS; y++) {
    printf("Main Thread Running\n");
    ULT_Yield(threads[EXTRA_THREADS - y - 1]);
  }

  printf("Main Thread Resuming\n");
  printf("Main Thread Exitting\n");
  
  return 0;
}


