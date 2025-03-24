#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>

#include <ctime>
#include <sstream>
#include <string>
#include "Stats.h"


/*
 *------------------------------------------------------------------
 *
 * Stats --
 *
 *          Constructor
 *
 * Arguments:
 *      None.
 *
 * Results:
 *      None.
 *
 * Side effects:
 *      None.
 *
 *
 *------------------------------------------------------------------
 */
Stats::Stats()
{

    //
    // Initialize per-object state
    //
    //           REMEMBER
    // 
    // You *must* follow the coding standards
    // distributed on the class web page.
    // Solutions failing to conform to these
    // standards will receive little or
    // no credit.
    //

  smutex_init(&mtx_);
}



/*
 *------------------------------------------------------------------
 *
 * ~Stats --
 *
 *          Destructor
 *
 * Arguments:
 *      None.
 *
 * Results:
 *      None.
 *
 * Side effects:
 *      None.
 *
 *------------------------------------------------------------------
 */
Stats::~Stats()
{
}


/*
 *------------------------------------------------------------------
 *
 * update --
 *
 *          Update the count for a stream.
 *
 * Arguments:
 *      int flowId -- the Id of the stream
 *      int count -- the number of bytes sent/received
 *
 * Results:
 *      None.
 *
 * Side effects:
 *      None.
 *
 *------------------------------------------------------------------
 */
void 
Stats::update(int flowId, int byteCount)
{
    //
    // Fill in this code. 
    //
    // Keep track of how many bytes each flowId
    // sent since the start of the run.
    //
    // Also keep track of the maximum flowID
    // you've seen.
    //
    // You *may* assume that flowIDs are small
    // consecutive non-negative integers between 0 
    // and MAX_FLOW_ID, inclusive.
    //
    // Do synchronize access to your shared
    // state. 
    //
    //
    //           REMEMBER
    // 
    // You *must* follow the coding standards
    // distributed on the class web page.
    // Solutions failing to conform to these
    // standards will receive little or
    // no credit.
    //
    assert(flowId >= 0 && flowId <= MAX_FLOW_ID);
    smutex_lock(&mtx_);

    if (flowId > maxFlowId_) {
      flowByteCount_.resize(flowId + 1);
      maxFlowId_ = flowId;
    }
    flowByteCount_[flowId] += byteCount;
    smutex_unlock(&mtx_);
}


/*
 *------------------------------------------------------------------
 *
 * toString --
 *
 *          Return a string of counts separated by ' '.
 *
 * Arguments:
 *      char *buffer -- a buffer into which the string
 *                      should be written
 *      int maxLen -- the number of bytes available in this buffer
 *                    (Caller should always hand in large enough
 *                    buffer; callee should be sure not to write 
 *                    past the end of the buffer!)
 *
 * Results:
 *      Return the pointer to the buffer.
 *
 * Side effects:
 *      None.
 *
 *------------------------------------------------------------------
 */
char *
Stats::toString(char *buffer, int maxLen)
{
    //
    // Fill in this code. 
    //
    // Produce a string that has a space-separated
    // list of how much data has been sent by
    // each flow since the last call to toString.
    // e.g.,
    // [sent_0] [sent_1] [sent_2] ... [tot]
    //
    // [sent_i] is the number of bytes for flow i whose
    // IO completed since the last call to toString().
    //
    // [tot] is the total number of bytes whose
    // IO completed since the last call to toString()
    //
    // Note that C or C++ represents strings
    // as an array of characters terminated
    // by the null character (0 or '\0'). Also
    // note that the newline character is '\n'.
    //
    //           REMEMBER
    // 
    // You *must* follow the coding standards
    // distributed on the class web page.
    // Solutions failing to conform to these
    // standards will receive little or
    // no credit.
    //
    smutex_lock(&mtx_);

    int end = 0;
     char str[22];
     long long tot = 0;
    // 将整数 num 转换为字符串，最多写入 sizeof(str) - 1 个字符到 str 中
    for (int i = 0; i <= maxFlowId_ + 1; ++i) {
      int num = (i <= maxFlowId_) ? flowByteCount_[i] : tot;
      int n = snprintf(str, sizeof(str), "%d", num);
      assert(n < 22);
      strncpy(buffer + end, str, n);
      end += n;

      if (i <= maxFlowId_) {
          buffer[end] = ' ';
          ++end;
          tot += flowByteCount_[i];
      } else {
          buffer[end] = '\0'; // FIXME
      }
      assert(end < maxLen);
    }
    smutex_unlock(&mtx_);

  return buffer;
}


/*
 *------------------------------------------------------------------
 *
 * unitTest --
 *
 *          Verify simple things that must be true.
 *
 * Arguments:
 *      None.
 *
 * Results:
 *      None.
 *
 * Side effects:
 *      None.
 *
 *------------------------------------------------------------------
 */
void
Stats::unitTest()
{
  // assert(0); // TBD: add some simple unit tests. 
             // You need to design these tests based on
             // your data structures.
  sequentialTest();
  concurrencyTest();
  printf("Stats self test passes.\n");
  return;
  
}


/* 自定义的两个测试 */
// 顺序测试，仅一个线程执行update和toString
void 
Stats::sequentialTest() {
  const int maxLen = 1024;
  char buffer[maxLen];

  update(0, 1);
  assert(!strcmp(toString(buffer, maxLen), "1 1"));
  update(3, 2);
  assert(!strcmp(toString(buffer, maxLen), "1 0 0 2 3"));
  update(2, 11);
  assert(!strcmp(toString(buffer, maxLen), "1 0 11 2 14"));
  update(2, 2);
  assert(!strcmp(toString(buffer, maxLen), "1 0 13 2 16"));
  update(8, 8);
  assert(!strcmp(toString(buffer, maxLen), "1 0 13 2 0 0 0 0 8 24"));
}


struct ThreadArg {
  Stats * stats_{};
  int times_{};
  int fd_{};
};

static void *thread_func(void *arg) {
  ThreadArg *targ = (ThreadArg *)arg;
  const int maxLen = 1024 * 1024;
  char buffer[maxLen];

   // 使用当前时间作为随机数种子
    std::srand(static_cast<unsigned int>(time(0)));

    auto getFlowCount = [](const char *str, int idx) {
        std::string s(str);
        std::stringstream ss(s);
        std::string token;
        int i = 0;
        while (ss >> token && i < idx) {
            ++i;
        }
        return stoi(token);
    };

    long long tot{};

    // 线程产生指定个数的随机值并累加到流字节数中
    for (int _ = 0; _ < targ->times_; ++_) {
      int count = std::rand() % 1001; // rand() % 1001 生成0到1000;
      tot += count;
      targ->stats_->update(targ->fd_, count);
    }
    // 判断流字节总数是否和调用update传入的总数想等
    targ->stats_->toString(buffer, maxLen);
    assert(tot == getFlowCount(buffer, targ->fd_));

    printf("%d update normally\n", targ->fd_);
    return NULL;
}

void 
Stats::concurrencyTest() {
  const int thread_num = 100;
  const int times = 100;
  struct Stats stats;
  sthread_t threads[thread_num];

  for(int i = 0; i < thread_num; i++){
      ThreadArg * arg = (ThreadArg *)malloc(sizeof(ThreadArg));
      arg->stats_ = &stats;
      arg->times_ = times;
      arg->fd_ = i;
      sthread_create(&threads[i], (void*(*)(void*))thread_func, (void *)arg);
    }
    // 等待其他线程执行完毕输出
    sthread_sleep(3, 0);
}