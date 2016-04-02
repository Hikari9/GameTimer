import java.util.*;
import java.util.concurrent.*;

/**
 * The <pre>GameTimer</pre> class executes tasks in intervals intended
 * for game elements such as animation and rigidbody physics.
 * 
 * A <pre>GameTimer</pre> runs tasks in frames per second (fps) and loops
 * indefinitely until the <pre>stop</pre> or <pre>interrupt</pre> methods are called.
 *
 * <pre>GameTimer</pre> is thread-safe. However, unlike <pre>java.util.Timer</pre>, this class executes tasks using a thread pool,
 * which recycles threads concurrently without "hogging" the execution thread. Specifically,
 * each timer uses a single <pre>ScheduledExecutorService</pre> as its scheduler,
 * which can be accessed using the <pre>getScheduler</pre> method.
 *
 * Note that this is an abstract class, where you need to override four methods. For example:
 * 
 * <pre>
 * GameTimer timer = new GameTimer() {
 * 		public float fps()			{return 24;}		// 24 frames per second	
 * 		public float delay()		{return 1;}			// 1 second delay before first execution
 * 		public boolean fixedRate()	{return true;}		// true = fixed rate, false = fixed delay
 * 		public void run() {
 * 			// the main task
 * 		}
 * };
 * </pre>
 * 
 * @see  java.lang.Runnable
 */

public abstract class GameTimer implements Runnable {

	////////////////////////
	/// ABSTRACT METHODS ///
	////////////////////////
	
	/**
	 * This is the task that is executed per frame.
	 * @see  java.lang.Runnable#run
	 */

	public abstract void run();

	/**
	 * Describes the number of times the <pre>run</pre> method will be executed per second.
	 * If <pre>fixedRate</pre> is false, the value of this function will refer to the number of
	 * frames per second between each call of <pre>run</pre>.
	 * @return a float describing the number of frames per second
	 */

	public abstract float fps();

	/**
	 * When the <pre>start<pre> method is called, there will be a delay (in seconds) before the first execution of the <pre>run<pre> method.
	 * @return a float describing the number of seconds of delay before the first <pre>run</pre>
	 */

	public abstract float delay();

	/**
	 * The timer can either run in a fixed rate or with fixed delay.
	 * If <pre>fixedRate</pre> is true, that means that the task will be run every 1/<pre>fps</pre> seconds,
	 * regardless of the running time of the executed task.
	 * Otherwise, if <pre>fixedRate<pre> is false, that means that there will be an fixed delay of 1/<pre>fps</pre> seconds 
	 * between tasks. This guarantees tasks to not overlap one another.
	 * @return a boolean describing if the timer runs in a fixed rate or with fixed delay
	 */

	public abstract boolean fixedRate();

	///////////////////
	/// CONSTRUCTOR	///
	///////////////////
	
	/**
	 * Constructs a non-reschedulable <pre>GameTimer</pre> object.
	 */

	public GameTimer() {
		this(false);
	}

	/**
	 * Constructs a <pre>GameTimer</pre> with an option to be reschedulable or not.
	 * If the object is reschedulable, any changes to the values of <pre>fps</pre> and <pre>fixedRate</pre> will be updated every 200 milliseconds.
	 * In this case, this object will be added to the reschedulable queue in another thread.
	 * It is best to keep the value false if you're sure that the <pre>fps</pre> and <pre>fixedRate</pre> will remain the same throughout. 
	 * @param  reschedulable a boolean describing if changes in <pre>fps</pre> and <pre>fixedRate</pre> are allowed or not.
	 */

	public GameTimer(boolean reschedulable) {
		this.reschedulable = reschedulable;
		if (reschedulable) Rescheduler.add(this);
	}

	///////////////////
	/// THREAD POOL ///
	///////////////////
	
	private static int poolSize = 20;
	private static ScheduledExecutorService scheduler = null;

	/**
	 * Gets the core number of threads maintained and recycled by all timers.
	 * The thread pool is maintained by a ScheduledExecutorService.
	 * Default thread pool size = 20.
	 * @return an integer containing the number of threads used in the thread pool
	 */
	
	public static int getPoolSize() {
		return poolSize;
	}

	/**
	 * Sets the number of recyclable threads for the timer's thread pool.
	 * @param size the new size of the thread pool
	 * @throws  IllegalArgumentException if size is not positive
	 */
	
	public static void setPoolSize(int size) {
		if (size <= 0) throw new IllegalArgumentException("GameTimer: thread pool size must be positive");
		poolSize = size;
		if (getScheduler() != null)
			// replace current thread pool
			// note: it is good practice to set thread pool size before any thread scheduling
			scheduler = Executors.newScheduledThreadPool(getPoolSize());
	}

	/**
	 * Gets the the thread pool service used by all timers.
	 * @return the ScheduledExecutorService that maintains the thread pool
	 */

	public static ScheduledExecutorService getScheduler() {
		if (scheduler == null) // create if there is no threadpool yet
			scheduler = Executors.newScheduledThreadPool(getPoolSize());
		return scheduler;
	}

	//////////////////////////////
	/// SINGLE TASK SCHEDULING ///
	//////////////////////////////

	/**
	 * Schedules a command once using the thread pool service without delay.
	 * 
	 * @param  command the task to execute
	 * @return  a ScheduledFuture representing pending completion of the task and whose get method will return null upon completion
	 * @throws  RejectedExecutionException 	if the task cannot be scheduled for execution
	 * @throws  NullPointerException 		if command is null
	 * @see  java.util.concurrent.ScheduledExecutorService#schedule
	 */

	public static ScheduledFuture<?> schedule(Runnable command) {
		return getScheduler().schedule(command, 0, TimeUnit.MILLISECONDS);
	}

	/////////////////////////////
	/// REPETITIVE SCHEDULING ///
	/////////////////////////////
	
	protected Future future = null;
	
	/**
	 * Checks if the timer is currently running.
	 * @return  a boolean describing whether the timer is currently running or not
	 */
	
	public boolean isRunning() {
		return future != null && !future.isCancelled();
	}

	/**
	 * Starts and schedules the task associated with this timer, either at a fixed rate or with fixed delay.
	 * Interrupts the currently running task, if it exists.
	 * The task will loop forever until manually terminated by the user.
	 * @see  GameTimer#interrupt
	 * @see  GameTimer#fixedRate
	 */

	public void start() {

		// interrupt if there's a currently running task
		if (isRunning()) interrupt(); 

		// convert delay and fps from seconds to microseconds
		long delay = (long) (delay() * 1000000L);
		long fps = (long) (1000000L / fps());

		// schedule at either at a fixed rate or with fixed delay.
		if (fixedRate())
			this.future = getScheduler().scheduleAtFixedRate(this, delay, fps, TimeUnit.MICROSECONDS);
		else
			this.future = getScheduler().scheduleWithFixedDelay(this, delay, fps, TimeUnit.MICROSECONDS);

	}

	/**
	 * Gracefully stops the current timer task by waiting for it to finish before terminating.
	 */

	public void stop() {
		if (future != null) future.cancel(false);
	}

	/**
	 * Forcefully stops the current timer task even while it's in the middle of running.
	 */

	public void interrupt() {
		if (future != null) future.cancel(true);
	}

	////////////////////
	/// RESCHEDULING ///
	////////////////////

	protected boolean reschedulable;

	/**
	 * Checks whether the timer would reschedule whenever there are changes in <pre>fps</pre> and <pre>fixedRate</pre>. 
	 */

	public boolean isReschedulable() {
		return reschedulable;
	}

	/**
	 * Sets a new value to the reschedulable property of this timer.
	 * @param  reschedulable a boolean describing if changes in <pre>fps</pre> and <pre>fixedRate</pre> are allowed or not.
	 */

	public void setReschedulable(boolean reschedulable) {
		if (this.reschedulable != reschedulable) {
			this.reschedulable = reschedulable;
			if (reschedulable) Rescheduler.add(this);
		}
	}

	/**
	 * The <pre>Rescheduler</pre> allows reschedulable game timers to be
	 * reupdated dynamically and concurrently every 200ms.
	 */

	private static class Rescheduler implements Runnable {
		
		/**
		 * The <pre>Node</pre> class encapsulates changes made to the timer every reschedule.
		 */

		private static class Node {

			GameTimer timer;
			float previousFps;
			boolean previousFixedRate;

			public Node(GameTimer timer) {
				this.timer = timer;
				this.previousFps = timer.fps();
				this.previousFixedRate = timer.fixedRate();
			}

			/**
			 * Updates and mirrors the values of the node to timer values.
			 * @return a boolean describing a successful update
			 */

			public boolean update() {
				float fps = timer.fps();
				boolean fixedRate = timer.fixedRate();
				if (fps != previousFps || fixedRate != previousFixedRate) {
					previousFps = fps;
					previousFixedRate = fixedRate;
					return true;
				}
				return false;
			}
		}
		
		private static LinkedList<Node> list = null;

		/**
		 * Adds a timer to the reschedulables. Starts a scheduled thread if called for the first time.
		 * @param timer a <pre>GameTimer</pre> that can potentially reschedule
		 */

		public static void add(GameTimer timer) {
			if (timer.isReschedulable()) {
				if (list == null) {
					list = new LinkedList<Node>();
					GameTimer.getScheduler().scheduleAtFixedRate(new Rescheduler(), 1, 200, TimeUnit.MILLISECONDS);
				}
				list.add(new Node(timer));
			}
		}
		
		/**
		 * This is the main method called by the rescheduler every 200ms.
		 */

		public void run() {
			try {
				for (ListIterator<Node> it = list.listIterator(); it.hasNext();) {
					Node node = it.next();
					if (!node.timer.isReschedulable())
						it.remove();
					else if (node.update()) {
						boolean wasRunning = node.timer.isRunning();
						node.timer.stop();
						if (wasRunning)
							node.timer.start();
					}
				}
			} catch (Exception ex) {
				System.err.println("Rescheduler: " + ex);
			}
		}
		

	}

}