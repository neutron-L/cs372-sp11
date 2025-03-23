#include <assert.h>
#include <stdio.h>
#include "ULT.h"

void self_intruduce(void * name) {
  printf("Hello, my name is %s\n", (char *)name);
  printf("You can call me %s\n", (char *)name);
  printf("What is your name?\n", (char *)name);
}

int main() {
  Tid ret = ULT_CreateThread(self_intruduce, "Jaserv");
  ULT_Yield(ret);
  return 0;
}

