/* Note: ult_queue_t is not synchronized. If used from multiple
 * threads, it is the users responsibility to provide suitable mutual
 * exclusion. The ult queue library maintains a global free list
 * from which it allocates links, so even if all queues are freed via
 * ult_free_queue(), Valgrind will still report memory as "in use
 * at exit". Applications or libraries building on the ult library
 * may use ult_queue_clear_free_list() to free the memory associated
 * with this free list prior to exit in order to avoid such reports.
 */

#ifndef ULT_QUEUE_H
#define ULT_QUEUE_H

#include "ULT.h"

struct _ult_queue;
typedef struct _ult_queue* ult_queue_t;

/* Create a new, empty queue */
ult_queue_t ult_new_queue();

/* Destroy the given queue. Asserts that the queue is empty. */
void ult_free_queue(ult_queue_t queue);

/* Add the given thread to the end of the queue */
void ult_enqueue(ult_queue_t queue, ThrdCtlBlk* tcb);

/* Return, and remove, the next thread from the queue, or NULL
 * if queue is empty */
ThrdCtlBlk* ult_dequeue(ult_queue_t queue);

/* Return the number of threads currently in the queue */
int ult_queue_size(ult_queue_t queue);

/* Return true if queue has no threads, false otherwise */
int ult_queue_is_empty(ult_queue_t queue);

/* Clear the global free list associated with the ult
 * queue library. In order to maintain efficiency of queue
 * insertions, this should be called a single time when
 * the ult library has no further use for queues. */
void ult_queue_clear_free_list(void);

#endif /* ult_QUEUE_H */
