#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>

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

  smutex_init(&mtx);
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
    smutex_lock(&mtx);

    if (flowId > maxFlowId) {
      flowByteCount.resize(flowId + 1);
      maxFlowId = flowId;
    }
    flowByteCount[flowId] += byteCount;
    smutex_unlock(&mtx);
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
    smutex_lock(&mtx);

    int end = 0;
     char str[22];
     long long tot = 0;
    // 将整数 num 转换为字符串，最多写入 sizeof(str) - 1 个字符到 str 中
    for (int i = 0; i <= maxFlowId + 1; ++i) {
      int num = (i <= maxFlowId) ? flowByteCount[i] : tot;
      int n = snprintf(str, sizeof(str), "%d", num);
      assert(n < 22);
      strncpy(buffer + end, str, n);
      end += n;

      if (i <= maxFlowId) {
          buffer[end] = ' ';
          ++end;
          tot += flowByteCount[i];
      } else {
          buffer[end] = '\0'; // FIXME
      }
      assert(end < maxLen);
    }
    smutex_unlock(&mtx);

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
  assert(0); // TBD: add some simple unit tests. 
             // You need to design these tests based on
             // your data structures.
  printf("Stats self test passes.\n");
  return;
  
}
