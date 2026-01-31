## Meditation Timer Android APP

### Functionality

This is a Meditation Timer App designed for Android phones.

The home screen has a 'start' button, and gui tools for selecting
three values.
 * C, the number of seconds to countdown before meditation starts (default is 10).
 * N, the number of minutes between interval bells (default is 7).
 * K, the number of intervals the user will meditate (default is 4).

So N*K is the total meditation time in minutes.  There is also an
'stop' button in case the user needs to end early, and a 'countdown'
gui element for viewing a elapsed time.

There are also two audio files for bells, the interval bell and the
final bell.  These will be hardwired into the app.

After the user presses 'start':
 * a user-visible timer starts to count down the seconds from C to 0.
 * when the countdown reaches zero, the interval bell sounds, the
 countdown is reset to N*K minutes, and the countdown begins again.
 At this point the user will be meditating.  During meditation the
 interval bell sounds every N minutes. 
 * when N*K minutes have elapsed and the meditation session is over,
 the final bell sounds, instead of the interval bell.
 * if the user presses 'stop' during the N*K minutes of meditation time
 he's asked if he wants to keep the session or discard it, and the
 session ends.  No bells are sounded after a 'stop'.
 
After the session ends, unless it is discarded by the user, it is
logged.  Each log entry has date, start time, and elapsed time.  Logs
will be retained by the app, and can be exported as a CSV file.

### Appearance

The app should look modern but uncluttered. It uses restful earth-tone
colors, not bright ones.
