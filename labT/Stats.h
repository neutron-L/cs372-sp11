#ifndef _STATS_H_
#define _STATS_H_
#include <sys/time.h>

#include <vector>

#include "sthread.h"
class Stats{
 public:
  Stats();
  ~Stats();
  void update(int flowId, int byteCount);
  char *toString(char *buffer, int maxLen);
  void unitTest();

  static const int MAX_FLOW_ID = 1023;

 private:

   void sequentialTest();
   void concurrencyTest();
  std::vector<size_t> flowByteCount{};
  int maxFlowId{-1};

  smutex_t mtx{};
};
#endif  
