#ifndef _STFQ_NW_SCHEDULER_H_
#define _STFQ_NW_SCHEDULER_H_

#include <queue>
#include <vector>

#include "NWScheduler.h"
#include "sthread.h"



class STFQNWScheduler:public NWScheduler{
  /*
   * TBD: Fill this in
   */
  private:
    long currentVirtualTime_{};
    long long prevSentTime_{};
    long bytesPerSec_{};
    std::queue<long> deadlines_{};
    std::priority_queue<long, std::vector<long>, std::greater<>> stag_queue_{};
    std::vector<long> flowPrevFTag_{};

    smutex_t mtx_{};
    scond_t front_{};             // 等待设置deadline
    scond_t newDeadline_{};       // 新的deadline
    scond_t timeout_{};           // 当前等待报文可以发送
 public:
  STFQNWScheduler(long bytesPerSec);
  void waitMyTurn(int flowId, float weight, int lenToSend);
  long long signalNextDeadline(long long deadlineMS);

};
#endif 
