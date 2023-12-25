/*
 SingleTaskWorker.java / Frost-Next
 Copyright (C) 2015  "The Kitty@++U6QppAbIb1UBjFBRtcIBZg6NU"

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation; either version 2 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

package frost.util;

import java.lang.Runnable;
import java.util.Timer;
import java.util.TimerTask;

public class SingleTaskWorker
{
    private Timer fTimer;
    private TimerTask fTimerTask;
    private Runnable fRunnable;

    public SingleTaskWorker()
    {
        // creates a single Timer queue-thread, which will be re-used during the lifetime of this class.
        // the thread is marked as a "daemon thread", which means that the application will be allowed
        // to exit if the only remaining threads are daemons. this means that waiting jobs don't prolong
        // the lifetime of the application and therefore won't delay the user if they're trying to quit.
        fTimer = new Timer(true);
    }

    /**
     * Schedules a Runnable task to happen after the given delay. If another delayed Runnable was
     * already waiting in the queue, then the previous waiting task will be *canceled* (if it hasn't
     * yet started executing). This means that you only have a single-task queue, where any subsequent
     * calls to schedule() *before* the previous task has executed means that the old task gets aborted
     * and the new task gets scheduled instead.
     * The most common use for this class is basically to allow you to schedule a delayed task whenever
     * a certain GUI event fires, and to be sure that it keeps re-scheduling itself each time the event
     * fires so that only the *final* scheduled task actually runs (instead of *all* of them).
     * IMPORTANT NOTE: All tasks run in a separate Thread. If you intend to interact with the GUI from
     * within your task, you must use "invokeLater" inside the task's run() function. Also: Never use
     * "invokeAndWait" in your task, since any task you provide must be able to execute rapidly
     * to allow future tasks to be scheduled.
     * @param {long} aDelay - the delay in milliseconds before your task will run (if it doesn't get
     * canceled by further calls to schedule() before it has had a chance to execute)
     * @param {Runnable} aRunnable - a "Runnable" instance (with a "run()" method); you can provide
     * your own variables to the task inside of the Runnable using various methods, such as subclassing
     * the Runnable and having your own class with private fields readable by its run() method, or
     * by reading any variables marked "final" from the parent scope.
     */
    public void schedule(
        final long aDelay,
        final Runnable aRunnable)
    {
        // if we had a previous timertask, tell it to cancel. this removes it from the Timer work
        // queue and prevents it from running (if it hasn't already started). this basically kills
        // the previous task from the Timer, as well as marks it for garbage collection.
        if( fTimerTask != null ) {
            fTimerTask.cancel();
        }

        // store the runnable provided by the user
        fRunnable = aRunnable;

        // create a brand new timer task (since the old task object is dead)
        fTimerTask = new TimerTask() {
            public void run() {
                fRunnable.run();
            }
        };

        // schedule the task to happen once, with the given delay
        fTimer.schedule(fTimerTask, aDelay);
    }
}
