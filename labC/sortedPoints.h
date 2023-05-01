#ifndef _SORTEDPOINTS_H_
#define _SORTEDPOINTS_H_
#include "point.h"


typedef struct SortedPointsStruct {
  /*
   * You can define this struct to have whatever
   * fields you want.
   */
} SortedPoints;

SortedPoints *sp_init(SortedPoints *sp);

/*
 * Allocate a new point and initialize it to x,y. Then
 * add that point to the SortedPoints list. Return
 * 1 on success and 0 on error (e.g., out of memory).
 */
int sp_addNewPoint(SortedPoints *sp, double x, double y);

/*
  * Note: Points are sorted by their distance from
  * the origin (0,0). If two points are the same
  * distance form the origin, then the one with
  * a smaller x coordinate should appear before
  * one with a larger one. If two points
  * are the same distance and have the same
  * x coordinate, then the one with the smaller
  * y coordinate should appear first.
  *
  * E.g., the follwing order is legal:
  * (0,0), (0, 1), (1, 0), (-2, 0), (0, 2), (2, 0)
  */

/*
  * Remove the first point from the sorted list.
  * Caller provides a pointer to a Point where
  * this procedure stores the values of that
  * point. Returns 1 on success
  * and 0 on failure (empty list).
  */
int sp_removeFirst(SortedPoints *sp, Point *ret);

/*
  * Remove the last point from the sorted list,
  * storing its value in *ret. Returns 1 on success
  * and 0 on failure (empty list).
  */
int sp_removeLast(SortedPoints *sp, Point *ret);


/*
  * Remove the point that appears in position
  * <index> on the sorted list, storing its
  * value in *ret. Returns 1 on success
  * and 0 on failure (too short list).
  *
  * The first item on the list is at index 0.
  */
int sp_removeIndex(SortedPoints *sp, int index, Point *ret);

/*
  * Delete any duplicate records. E.g., if
  * two points on the list have *identical*
  * x and y values, then delete one of them.
  * Return the number of records deleted.
  */
int sp_deleteDuplicates(SortedPoints *sp);

#endif
