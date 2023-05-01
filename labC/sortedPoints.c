#include <assert.h>
#include <stdlib.h>
#include "point.h"
#include "sortedPoints.h"

/*
 * Initialize data structures, returning pointer
 * to the object.
 */
SortedPoints *sp_init(SortedPoints *sp)
{
  // assert(0); // TBD
  sp->head = sp->tail = NULL;
  sp->length = 0;

  return sp;
}

/*
 * Allocate a new point and initialize it to x,y. Then
 * add that point to the SortedPointss list. Return
 * 1 on success and 0 on error (e.g., out of memory).
 */
int sp_addNewPoint(SortedPoints *sp, double x, double y)
{
  // assert(0); // TBD
  LNode *node = (LNode *)malloc(sizeof(LNode));
  if (node)
  {
    node->point.x = x;
    node->point.y = y;

    if (sp->tail)
      sp->tail->next = node;
    sp->tail = node;
    if (!sp->head)
      sp->head = node;
    ++sp->length;
    return 1;
  }

  return 0;
}

/*
 * Remove the first point from the sorted list,
 * storing its value in *ret. Returns 1 on success
 * and 0 on failure (empty list).
 */
int sp_removeFirst(SortedPoints *sp, Point *ret)
{
  // assert(0); // TBD
  LNode *node = sp->head;
  if (node)
  {
    ret->x = node->point.x;
    ret->y = node->point.y;

    sp->head = node->next;
    if (!sp->head)
      sp->tail = NULL;

    --sp->length;
    free(node);

    return 1;
  }

  return 0;
}

/*
 * Remove the last point from the sorted list,
 * storing its value in *ret. Returns 1 on success
 * and 0 on failure (empty list).
 */
int sp_removeLast(SortedPoints *sp, Point *ret)
{
  // assert(0); // TBD
  LNode *node = sp->tail;
  if (node)
  {
    ret->x = node->point.x;
    ret->y = node->point.y;

    LNode *pre = NULL;
    LNode *cur = sp->head;
    while (cur != sp->tail)
    {
      pre = cur;
      cur = cur->next;
    }

    if (!pre)
      sp->head = sp->tail = NULL;
    else
    {
      pre->next = NULL;
      sp->tail = pre;
    }
    --sp->length;
    free(node);

    return 1;
  }

  return 0;
}

/*
 * Remove the point that appears in position
 * <index> on the sorted list, storing its
 * value in *ret. Returns 1 on success
 * and 0 on failure (too short list).
 *
 * The first item on the list is at index 0.
 */
int sp_removeIndex(SortedPoints *sp, int index, Point *ret)
{
  // assert(0); // TBD
  if (index < 0 || index >= sp->length)
    return 0;
  else
  {
    if (index == 0)
      return sp_removeFirst(sp, ret);
    else if (index == sp->length - 1)
      return sp_removeLast(sp, ret);
    else
    {
      int i = 0;
      LNode *pre = NULL;
      LNode *cur = sp->head;
      while (cur && i < index)
      {
        pre = cur;
        cur = cur->next;
        ++i;
      }
      pre->next = cur->next;
      ret->x = cur->point.x;
      ret->y = cur->point.y;
      --sp->length;
      free(cur);

      return 1;
    }
  }
}

/*
 * Delete any duplicate records. E.g., if
 * two points on the list have *identical*
 * x and y values, then delete one of them.
 * Return the number of records deleted.
 */
int sp_deleteDuplicates(SortedPoints *sp)
{
  // assert(0); // TBD
  if (sp && sp->head)
  {
    LNode *pre = sp->head;
    LNode *cur = sp->head->next;
    while (cur)
    {
      if (cur->point.x == pre->point.x && cur->point.y == pre->point.y)
      {
        pre->next = cur->next;
        free(cur);
        cur = pre->next;
      }
      else
      {
        pre = cur;
        cur = cur->next;
      }
    }
  }

  return 0;
}
