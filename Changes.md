Changes to make in the codebase:

 1. Change the name of the app from 'MedTimer' to 'ZenTimer'.  We
    don't need to change the name of the github repository, just the
    name as seen by the user.
	
 2. On the timer screen: 

 * switch the 'Interval(min)' tool and the 'Intervals' tool, to the
    number of intervals, default 4, is on the left.

 * remove the debug option from the screen (but keep it in the code
 for now, but disabled, in case we need to use it again).

 3. On the history screen
 
 * Remove the 'clear history' button
 * Remove the 'delete' icons from the previous lines.
 * Present the history items in a more compact format, and only present the last few - say 14, only what fits 
 comfortably on the screen.
 * Below the history items, present these statistics:
   1. Last 7 days: average time, and percentage of days with a meditation session
   2. Last 30 days: average time, and percentage of days with a meditation session
