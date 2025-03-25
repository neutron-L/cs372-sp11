#ifndef _MAX_NW_SCHEDULER_H_
#define _MAX_NW_SCHEDULER_H_

#include <queue> 

#include "NWScheduler.h"
#include "sthread.h"
class MaxNWScheduler:public NWScheduler{
  /*
   * TBD: Fill this in
   */
 private:
    long bytesPerSec_{};
    std::queue<long> deadlines_{};

    smutex_t mtx_{};
    scond_t newDeadline_{};
    scond_t timeout_{};
 public:
  MaxNWScheduler(long bytesPerSec);
  void waitMyTurn(int flowId, float weight, int lenToSend);
  long long signalNextDeadline(long long deadlineMS);
};
#endif 
