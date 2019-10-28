package nachos.threads;

import java.util.Iterator;
import java.util.LinkedList;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	private LinkedList<wakeup> waitQueue;
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p><b>Note</b>: Nachos will not function correctly with more than one
	 * alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() { timerInterrupt(); }
		});
		waitQueue = new LinkedList<wakeup>();
	}
	/**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */

	class wakeup {
		long wake;
		KThread waitThread;
		public wakeup (long wakeTime, KThread wakeThread) {
			this.wake = wakeTime;
			this.waitThread = wakeThread;
		}

} 
	public void timerInterrupt() {
		boolean status = Machine.interrupt().disable();
		wakeup threadNext;

		for (Iterator<wakeup> obj = waitQueue.iterator(); obj.hasNext();) {
			threadNext = (wakeup) obj.next();
			if (Machine.timer().getTime() >= threadNext.wake) {
				obj.remove();
				threadNext.waitThread.ready();
			}	}
		KThread.yield();
		Machine.interrupt().restore(status);
	}
	/**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */

	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x;
		boolean status = Machine.interrupt().disable();
		
		wakeup thread = new wakeup(wakeTime, KThread.currentThread());

		waitQueue.add(thread);

		KThread.sleep();
		Machine.interrupt().restore(status);
	}
	
}
