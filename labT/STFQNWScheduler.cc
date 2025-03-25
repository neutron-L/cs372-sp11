#include <assert.h>
#include <sys/time.h>
#include "STFQNWScheduler.h"
#include "util.h"


STFQNWScheduler::STFQNWScheduler(long bytesPerSec) : bytesPerSec_(bytesPerSec)
{
  // assert(0); // TBD
  smutex_init(&mtx_);
  scond_init(&front_);
  scond_init(&newDeadline_);
  scond_init(&timeout_);
}


void 
STFQNWScheduler::waitMyTurn(int flowId, float weight, int lenToSend)
{
  static int prevLenToSend{};

  // assert(0); // TBD
  smutex_lock(&mtx_);

  // 检查currentVirtualTime更新
  if (nowMS() >= prevSentTime_ + prevLenToSend / bytesPerSec_ && stag_queue_.empty() && deadlines_.empty()) {
    for (auto & tag : flowPrevFTag_) {
        currentVirtualTime_ = std::max(currentVirtualTime_, tag);
    }
  }
  // 计算start tag finish tag
  if ((int)flowPrevFTag_.size() <= flowId) {
    flowPrevFTag_.resize(flowId + 1);
  }
  long stag = std::max(currentVirtualTime_, flowPrevFTag_[flowId]);
  long ftag = stag + lenToSend / weight;
  flowPrevFTag_[flowId] = std::max(flowPrevFTag_[flowId], ftag);
  stag_queue_.push(stag);

  // NOTE: 不必担心stag重复的问题导致多个线程同时满足下述条件，因为只有一个线程能获得锁
  while (stag_queue_.top() != stag) {
    scond_wait(&front_, &mtx_);
  }
  stag_queue_.pop();

  // 设置deadline
  long deadline = prevSentTime_ + lenToSend / bytesPerSec_;
  deadlines_.push(deadline);

  scond_signal(&newDeadline_, &mtx_);
  prevLenToSend = lenToSend;

  while (!(deadlines_.empty() || deadlines_.front() > deadline)) {
    scond_wait(&timeout_, &mtx_);
  } 

  // 准备发送，则唤醒stag排序的下一个数据包开始设置ddl等待
  scond_broadcast(&front_, &mtx_);
  // 更新上次数据包发送的时间
  prevSentTime_ = nowMS();

  smutex_unlock(&mtx_);
}

long long 
STFQNWScheduler::signalNextDeadline(long long prevDeadline)
{
  long long deadline{};
  bool trigger{false};

  smutex_lock(&mtx_);

  while (!deadlines_.empty() && deadlines_.front() <= prevDeadline) {
    deadlines_.pop();
    trigger = true;
  }

  if (trigger) {
    scond_broadcast(&timeout_, &mtx_);
  }

  while (deadlines_.empty()) {
    scond_wait(&newDeadline_, &mtx_);
  }
  deadline = deadlines_.front();
  smutex_unlock(&mtx_);
  
  return deadline;
}


