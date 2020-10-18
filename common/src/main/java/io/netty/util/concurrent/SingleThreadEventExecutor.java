/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThreadExecutorMap;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * {@link OrderedEventExecutor}的抽象基类，在单个线程中执行其提交的所有任务。
 *
 */
public abstract class SingleThreadEventExecutor extends AbstractScheduledEventExecutor implements OrderedEventExecutor {

    // 默认最大挂起执行器任务,最小16，最大Integer.MAX_VALUE
    static final int DEFAULT_MAX_PENDING_EXECUTOR_TASKS = Math.max(16,
            SystemPropertyUtil.getInt("io.netty.eventexecutor.maxPendingTasks", Integer.MAX_VALUE));

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SingleThreadEventExecutor.class);

    // 运行状态
    private static final int ST_NOT_STARTED = 1; // 未开始
    private static final int ST_STARTED = 2; // 开始
    private static final int ST_SHUTTING_DOWN = 3; // 正在关闭
    private static final int ST_SHUTDOWN = 4; // 关闭
    private static final int ST_TERMINATED = 5; // 终止

    private static final Runnable NOOP_TASK = new Runnable() {
        @Override
        public void run() {
            // Do nothing.
        }
    }; // 标记

    // 原子更新状态值
    private static final AtomicIntegerFieldUpdater<SingleThreadEventExecutor> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor.class, "state");

    // 原子更新线程参数
    private static final AtomicReferenceFieldUpdater<SingleThreadEventExecutor, ThreadProperties> PROPERTIES_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(
                    SingleThreadEventExecutor.class, ThreadProperties.class, "threadProperties");

    private final Queue<Runnable> taskQueue; // 任务队列

    private volatile Thread thread; // 该执行器线程
    @SuppressWarnings("unused")
    private volatile ThreadProperties threadProperties; // 线程属性
    private final Executor executor; // 执行器
    private volatile boolean interrupted; // 是否中断

    private final CountDownLatch threadLock = new CountDownLatch(1); // 计数门闸
    private final Set<Runnable> shutdownHooks = new LinkedHashSet<Runnable>(); // 任务关闭钩子
    private final boolean addTaskWakesUp; // 添加任务唤醒
    private final int maxPendingTasks; // 最大任务数量
    private final RejectedExecutionHandler rejectedExecutionHandler; // 拒绝策略

    private long lastExecutionTime; // 最后执行时间

    @SuppressWarnings({ "FieldMayBeFinal", "unused" })
    private volatile int state = ST_NOT_STARTED; // 初始状态

    private volatile long gracefulShutdownQuietPeriod; // 优雅关闭静默期
    private volatile long gracefulShutdownTimeout; // 优雅关闭超时时间
    private long gracefulShutdownStartTime; // 优雅关闭开始时间

    private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);

    /**
     * 创建一个新的实例
     *
     * @param parent            {@link EventExecutorGroup}，它是这个实例的父实例并属于它
     * @param threadFactory     用于已使用的{@link Thread}的{@link ThreadFactory}
     * @param addTaskWakesUp    {@code true}当且仅当调用{@link #addTask(Runnable)}将唤醒执行线程
     */
    protected SingleThreadEventExecutor(
            EventExecutorGroup parent, ThreadFactory threadFactory, boolean addTaskWakesUp) {
        this(parent, new ThreadPerTaskExecutor(threadFactory), addTaskWakesUp);
    }

    /**
     * 创建一个新的实例
     *
     * @param parent            {@link EventExecutorGroup}，它是这个实例的父实例并属于它
     * @param threadFactory     用于已使用的{@link Thread}的{@link ThreadFactory}
     * @param addTaskWakesUp    {@code true}当且仅当调用{@link #addTask(Runnable)}将唤醒执行线程
     * @param maxPendingTasks   新任务将被拒绝之前挂起任务的最大数量。
     * @param rejectedHandler   使用{@link RejectedExecutionHandler}。
     */
    protected SingleThreadEventExecutor(
            EventExecutorGroup parent, ThreadFactory threadFactory,
            boolean addTaskWakesUp, int maxPendingTasks, RejectedExecutionHandler rejectedHandler) {
        this(parent, new ThreadPerTaskExecutor(threadFactory), addTaskWakesUp, maxPendingTasks, rejectedHandler);
    }

    /**
     * 创建一个新的实例
     *
     * @param parent            {@link EventExecutorGroup}，它是这个实例的父实例并属于它
     * @param executor          the {@link Executor} which will be used for executing
     * @param addTaskWakesUp    {@code true}当且仅当调用{@link #addTask(Runnable)}将唤醒执行线程
     */
    protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor, boolean addTaskWakesUp) {
        this(parent, executor, addTaskWakesUp, DEFAULT_MAX_PENDING_EXECUTOR_TASKS, RejectedExecutionHandlers.reject());
    }

    /**
     * 创建一个新的实例
     *
     * @param parent            {@link EventExecutorGroup}，它是这个实例的父实例并属于它
     * @param executor          the {@link Executor} which will be used for executing
     * @param addTaskWakesUp    {@code true}当且仅当调用{@link #addTask(Runnable)}将唤醒执行线程
     * @param maxPendingTasks   新任务将被拒绝之前挂起任务的最大数量。
     * @param rejectedHandler   使用{@link RejectedExecutionHandler}。
     */
    protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor,
                                        boolean addTaskWakesUp, int maxPendingTasks,
                                        RejectedExecutionHandler rejectedHandler) {
        super(parent);
        this.addTaskWakesUp = addTaskWakesUp;
        this.maxPendingTasks = Math.max(16, maxPendingTasks); // 最大挂起任务
        this.executor = ThreadExecutorMap.apply(executor, this); //
        taskQueue = newTaskQueue(this.maxPendingTasks); // 任务队列
        rejectedExecutionHandler = ObjectUtil.checkNotNull(rejectedHandler, "rejectedHandler");
    }

    protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor,
                                        boolean addTaskWakesUp, Queue<Runnable> taskQueue,
                                        RejectedExecutionHandler rejectedHandler) {
        super(parent);
        this.addTaskWakesUp = addTaskWakesUp;
        this.maxPendingTasks = DEFAULT_MAX_PENDING_EXECUTOR_TASKS; // 最大挂起任务
        this.executor = ThreadExecutorMap.apply(executor, this);
        this.taskQueue = ObjectUtil.checkNotNull(taskQueue, "taskQueue");
        this.rejectedExecutionHandler = ObjectUtil.checkNotNull(rejectedHandler, "rejectedHandler");
    }

    /**
     * @deprecated Please use and override {@link #newTaskQueue(int)}.
     */
    @Deprecated
    protected Queue<Runnable> newTaskQueue() {
        return newTaskQueue(maxPendingTasks);
    }

    /**
     * 创建一个新的{@link Queue}，用来存放要执行的任务。
     * 这个默认的实现将返回一个{@link LinkedBlockingQueue}，
     * 但是如果你的{@link SingleThreadEventExecutor}的子类不会在这个{@link Queue}上做任何阻塞调用，
     * 那么{@code @Override}这可能是有意义的，并返回一些完全不支持阻塞操作的更高性能的实现。
     */
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return new LinkedBlockingQueue<Runnable>(maxPendingTasks);
    }

    /**
     * 中断当前运行的{@link Thread}。
     */
    protected void interruptThread() {
        Thread currentThread = thread;
        if (currentThread == null) {
            interrupted = true;
        } else {
            currentThread.interrupt();
        }
    }

    /**
     * @see Queue#poll()
     */
    // 取任务
    protected Runnable pollTask() {
        assert inEventLoop();
        return pollTaskFrom(taskQueue);
    }

    protected static Runnable pollTaskFrom(Queue<Runnable> taskQueue) {
        for (;;) {
            // 循环取任务，无任务的话阻塞住
            Runnable task = taskQueue.poll(); // 取出任务
            // 不为唤醒任务的话
            if (task != WAKEUP_TASK) {
                return task;
            }
        }
    }

    /**
     * 从任务队列中抽取下一个{@link Runnable}，所以如果当前没有任务存在，就会阻塞。
     * <p>
     * 请注意，如果通过{@link #newTaskQueue()}创建的任务队列没有实现{@link BlockingQueue}，
     * 这个方法将抛出一个{@link UnsupportedOperationException}。
     * </p>
     *
     * @return {@code null} if the executor thread has been interrupted or waken up.
     */
    protected Runnable takeTask() {
        assert inEventLoop();
        // 如果非阻塞队列，则抛出异常
        if (!(taskQueue instanceof BlockingQueue)) {
            throw new UnsupportedOperationException();
        }

        // 拿到阻塞队列
        BlockingQueue<Runnable> taskQueue = (BlockingQueue<Runnable>) this.taskQueue;

        for (;;) {
            ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
            if (scheduledTask == null) { // 如果优先队列没有任务
                Runnable task = null;
                try {
                    task = taskQueue.take(); // 取阻塞队列任务
                    if (task == WAKEUP_TASK) { // 如果是标记任务，则返回null
                        task = null;
                    }
                } catch (InterruptedException e) {
                    // Ignore
                }
                return task;
            } else { // 执行优先队列的任务
                long delayNanos = scheduledTask.delayNanos();
                Runnable task = null;
                if (delayNanos > 0) { // 没到时间
                    try {
                        // 先取阻塞队列里的
                        task = taskQueue.poll(delayNanos, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        // Waken up.
                        return null;
                    }
                }
                // 到时间的话
                if (task == null) { // 如果没取出来
                    // 我们现在需要获取计划任务，否则如果任务队列中总是有一个任务，那么计划任务可能永远不会被执行。
                    // 例如，对于 OIO Transport 的读取任务来说就是如此。
                    // See https://github.com/netty/netty/issues/1614
                    fetchFromScheduledTaskQueue(); // 从调度队列取出来再运行
                    task = taskQueue.poll();
                }

                if (task != null) {
                    return task;
                }
            }
        }
    }

    // taskQueue添加到期的调度任务
    // 取出所有到时的任务
    private boolean fetchFromScheduledTaskQueue() {
        if (scheduledTaskQueue == null || scheduledTaskQueue.isEmpty()) {
            return true;
        }
        long nanoTime = AbstractScheduledEventExecutor.nanoTime();
        for (;;) {
            Runnable scheduledTask = pollScheduledTask(nanoTime);
            if (scheduledTask == null) {
                return true;
            }
            if (!taskQueue.offer(scheduledTask)) {
                // 任务队列中没有空了，把它添加回scheduledTaskQueue，这样我们就可以再次提取它。
                scheduledTaskQueue.add((ScheduledFutureTask<?>) scheduledTask);
                return false;
            }
        }
    }

    /**
     * @return {@code true} 如果至少执行了一个计划任务。
     */
    // 取出到时间的调度任务
    private boolean executeExpiredScheduledTasks() {
        if (scheduledTaskQueue == null || scheduledTaskQueue.isEmpty()) {
            return false;
        }
        long nanoTime = AbstractScheduledEventExecutor.nanoTime();
        Runnable scheduledTask = pollScheduledTask(nanoTime);
        if (scheduledTask == null) {
            return false;
        }
        do {
            safeExecute(scheduledTask);
        } while ((scheduledTask = pollScheduledTask(nanoTime)) != null);
        return true;
    }

    /**
     * @see Queue#peek()
     */
    protected Runnable peekTask() {
        assert inEventLoop();
        return taskQueue.peek();
    }

    /**
     * @see Queue#isEmpty()
     */
    // 是否还有任务
    protected boolean hasTasks() {
        assert inEventLoop();
        return !taskQueue.isEmpty();
    }

    /**
     * 返回等待处理的任务数量。
     *
     * <strong>请注意，这个操作的开销可能很大，因为它依赖于SingleThreadEventExecutor的内部实现。所以要小心使用!</strong>
     */
    public int pendingTasks() {
        return taskQueue.size();
    }

    /**
     * 在任务队列中添加一个任务，或者抛出一个{@link RejectedExecutionException}，如果这个实例之前被关闭了。
     */
    protected void addTask(Runnable task) {
        ObjectUtil.checkNotNull(task, "task");
        if (!offerTask(task)) {
            reject(task);
        }
    }

    final boolean offerTask(Runnable task) {
        if (isShutdown()) {
            reject();
        }
        return taskQueue.offer(task);
    }

    /**
     * @see Queue#remove(Object)
     */
    protected boolean removeTask(Runnable task) {
        return taskQueue.remove(ObjectUtil.checkNotNull(task, "task"));
    }

    /**
     * 轮询任务队列中的所有任务，并通过{@link Runnable#run()}方法运行它们。
     *
     * @return {@code true} if and only if at least one task was run
     */
    protected boolean runAllTasks() {
        assert inEventLoop();
        boolean fetchedAll;
        boolean ranAtLeastOne = false;

        do {
            fetchedAll = fetchFromScheduledTaskQueue();
            if (runAllTasksFrom(taskQueue)) {
                ranAtLeastOne = true;
            }
        } while (!fetchedAll); // 继续处理，直到获取所有计划任务为止。

        // 计算时间
        if (ranAtLeastOne) {
            lastExecutionTime = ScheduledFutureTask.nanoTime();
        }
        // 运行所有任务之后取运行任务
        afterRunningAllTasks();
        return ranAtLeastOne;
    }

    /**
     * 执行执行器队列中所有过期的计划任务和所有当前任务，直到两个队列都为空，或者超过了{@code maxdrain尝试}。
     * @param maxDrainAttempts 此方法尝试从队列中排干的最大次数。
     *                         这是为了防止任务的连续执行和调度阻止EventExecutor线程继续执行并返回到选择器机制来处理入站I/O事件。
     * @return {@code true} if at least one task was run.
     */
    protected final boolean runScheduledAndExecutorTasks(final int maxDrainAttempts) {
        assert inEventLoop();
        boolean ranAtLeastOneTask;
        int drainAttempt = 0;
        do {
            // 我们必须首先运行taskQueue任务，因为从EventLoop外部调度的任务在这里排队，
            // 因为taskQueue是线程安全的，而scheduledTaskQueue不是线程安全的。
            ranAtLeastOneTask = runExistingTasksFrom(taskQueue) | executeExpiredScheduledTasks();
        } while (ranAtLeastOneTask && ++drainAttempt < maxDrainAttempts);

        if (drainAttempt > 0) {
            lastExecutionTime = ScheduledFutureTask.nanoTime();
        }
        afterRunningAllTasks();

        return drainAttempt > 0;
    }

    /**
     * 从传递的{@code taskQueue}运行所有任务。
     *
     * @param taskQueue To poll and execute all tasks.
     *
     * @return {@code true} if at least one task was executed.
     */
    protected final boolean runAllTasksFrom(Queue<Runnable> taskQueue) {
        Runnable task = pollTaskFrom(taskQueue);
        if (task == null) {
            return false;
        }
        for (;;) {
            safeExecute(task);
            task = pollTaskFrom(taskQueue);
            if (task == null) {
                return true;
            }
        }
    }

    /**
     * 调用此方法时，{@code taskQueue}中出现的任务将是{@link Runnable#run()}。
     * @param taskQueue the task queue to drain.
     * @return {@code true} if at least {@link Runnable#run()} was called.
     */
    private boolean runExistingTasksFrom(Queue<Runnable> taskQueue) {
        Runnable task = pollTaskFrom(taskQueue);
        if (task == null) {
            return false;
        }
        int remaining = Math.min(maxPendingTasks, taskQueue.size());
        safeExecute(task);
        // 直接使用taskQueue.poll()而不是pollTaskFrom()，因为后者可能无声地使用队列中的多个项目(跳过WAKEUP_TASK实例)
        while (remaining-- > 0 && (task = taskQueue.poll()) != null) {
            safeExecute(task);
        }
        return true;
    }

    /**
     * 轮询任务队列中的所有任务，并通过{@link Runnable#run()}方法运行它们。
     * 此方法停止运行任务队列中的任务，如果运行时间超过{@code timeoutNanos}，则返回。
     */
    protected boolean runAllTasks(long timeoutNanos) {
        // 从调度任务
        fetchFromScheduledTaskQueue();
        // 取任务
        Runnable task = pollTask();
        if (task == null) {
            // 无任务，则运行tailTask
            afterRunningAllTasks();
            return false;
        }
        // 若有剩余时间
        final long deadline = timeoutNanos > 0 ? ScheduledFutureTask.nanoTime() + timeoutNanos : 0;
        // 运行的任务数
        long runTasks = 0;
        // 最后执行时间
        long lastExecutionTime;
        // 轮询执行
        for (;;) {
            // 执行任务
            safeExecute(task);

            runTasks ++; // 执行任务数++

            // 每64个任务检查一次超时，因为nanoTime()的开销相对较大。
            // XXX: 硬编码的值——如果确实存在问题，将使其可配置。
            // 每64次计算一下时间
            if ((runTasks & 0x3F) == 0) {
                lastExecutionTime = ScheduledFutureTask.nanoTime();
                // 计算时间
                if (lastExecutionTime >= deadline) {
                    break;
                }
            }
            // 拿出任务
            task = pollTask();
            // 无任务则返回
            if (task == null) {
                lastExecutionTime = ScheduledFutureTask.nanoTime();
                break;
            }
        }
        // 最后执行tailTask任务
        afterRunningAllTasks();
        this.lastExecutionTime = lastExecutionTime;
        return true;
    }

    /**
     * 在从{@link #runAllTasks()}和{@link #runAllTasks(long)}返回之前调用。
     */
    @UnstableApi
    protected void afterRunningAllTasks() { }

    /**
     * 返回在执行最接近死线的计划任务之前剩下的时间。
     */
    protected long delayNanos(long currentTimeNanos) {
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        if (scheduledTask == null) {
            return SCHEDULE_PURGE_INTERVAL;
        }

        return scheduledTask.delayNanos(currentTimeNanos);
    }

    /**
     * 返回下一个最近调度任务运行的绝对时间点(相对于{@link #nanoTime()})。
     */
    @UnstableApi
    protected long deadlineNanos() {
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        if (scheduledTask == null) {
            return nanoTime() + SCHEDULE_PURGE_INTERVAL;
        }
        return scheduledTask.deadlineNanos();
    }

    /**
     * 更新内部时间戳，该时间戳告知提交的任务最近执行的时间。{@link #runAllTasks()}和{@link #runAllTasks(long)}自动更新这个时间戳，
     * 因此通常不需要调用这个方法。
     * 但是，如果您使用{@link #takeTask()}或{@link #pollTask()}手动执行任务，则必须在任务执行循环结束时调用此方法来进行精确的安静期检查。
     */
    protected void updateLastExecutionTime() {
        lastExecutionTime = ScheduledFutureTask.nanoTime();
    }

    /**
     * 执行任务 {@link #taskQueue}
     */
    protected abstract void run();

    /**
     * 什么都不做，子类可以重写吗
     */
    // 关闭选择器
    protected void cleanup() {
        // NOOP
    }

    // 唤醒线程
    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop) {
            // 使用offer，因为我们实际上只需要它来解除线程阻塞，如果offer失败，我们不关心，因为队列中已经有了一些东西。
            taskQueue.offer(WAKEUP_TASK);
        }
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    /**
     * 添加一个{@link Runnable}，它将在关闭此实例时执行
     */
    public void addShutdownHook(final Runnable task) {
        if (inEventLoop()) {
            shutdownHooks.add(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    shutdownHooks.add(task);
                }
            });
        }
    }

    /**
     * 删除之前添加的作为关机钩子的{@link Runnable}
     */
    public void removeShutdownHook(final Runnable task) {
        if (inEventLoop()) {
            shutdownHooks.remove(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    shutdownHooks.remove(task);
                }
            });
        }
    }

    // 运行关闭钩子
    private boolean runShutdownHooks() {
        boolean ran = false;
        // Note shutdown hooks can add / remove shutdown hooks.
        while (!shutdownHooks.isEmpty()) {
            List<Runnable> copy = new ArrayList<Runnable>(shutdownHooks);
            shutdownHooks.clear();
            for (Runnable task: copy) {
                try {
                    task.run();
                } catch (Throwable t) {
                    logger.warn("Shutdown hook raised an exception.", t);
                } finally {
                    ran = true;
                }
            }
        }

        if (ran) {
            lastExecutionTime = ScheduledFutureTask.nanoTime();
        }

        return ran;
    }

    // 优雅关闭
    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        ObjectUtil.checkPositiveOrZero(quietPeriod, "quietPeriod");
        if (timeout < quietPeriod) {
            throw new IllegalArgumentException(
                    "timeout: " + timeout + " (expected >= quietPeriod (" + quietPeriod + "))");
        }
        ObjectUtil.checkNotNull(unit, "unit");

        if (isShuttingDown()) { // 是否已经关闭
            return terminationFuture();
        }

        boolean inEventLoop = inEventLoop(); // 是否当前线程关闭的
        boolean wakeup; // 唤醒
        int oldState; // 旧状态
        for (;;) {
            // 是否已经关闭
            if (isShuttingDown()) {
                return terminationFuture();
            }
            // 新状态
            int newState;
            // 唤醒
            wakeup = true;
            // 旧状态
            oldState = state;
            if (inEventLoop) { // 若是当前线程，则赋予关闭状态
                newState = ST_SHUTTING_DOWN;
            } else {
                // 否则判断，若是开始状态，则改为关闭状态
                switch (oldState) {
                    case ST_NOT_STARTED:
                    case ST_STARTED:
                        newState = ST_SHUTTING_DOWN;
                        break;
                    default:
                        newState = oldState;
                        wakeup = false;
                }
            }
            if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
                break;
            }
        }
        gracefulShutdownQuietPeriod = unit.toNanos(quietPeriod);
        gracefulShutdownTimeout = unit.toNanos(timeout);

        // 确定线程状态
        if (ensureThreadStarted(oldState)) {
            return terminationFuture;
        }

        if (wakeup) { // 唤醒阻塞线程
            taskQueue.offer(WAKEUP_TASK);
            if (!addTaskWakesUp) {
                // 唤醒
                wakeup(inEventLoop);
            }
        }

        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    // 关闭
    @Override
    @Deprecated
    public void shutdown() {
        if (isShutdown()) {
            return;
        }

        boolean inEventLoop = inEventLoop();
        boolean wakeup;
        int oldState;
        for (;;) {
            if (isShuttingDown()) {
                return;
            }
            int newState;
            wakeup = true;
            oldState = state;
            if (inEventLoop) {
                newState = ST_SHUTDOWN;
            } else {
                switch (oldState) {
                    case ST_NOT_STARTED:
                    case ST_STARTED:
                    case ST_SHUTTING_DOWN:
                        newState = ST_SHUTDOWN;
                        break;
                    default:
                        newState = oldState;
                        wakeup = false;
                }
            }
            if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
                break;
            }
        }

        if (ensureThreadStarted(oldState)) {
            return;
        }

        if (wakeup) {
            taskQueue.offer(WAKEUP_TASK);
            if (!addTaskWakesUp) {
                wakeup(inEventLoop);
            }
        }
    }

    @Override
    // 是否正在关闭
    public boolean isShuttingDown() {
        return state >= ST_SHUTTING_DOWN;
    }

    @Override
    // 是否关闭
    public boolean isShutdown() {
        return state >= ST_SHUTDOWN;
    }

    @Override
    // 是否终止
    public boolean isTerminated() {
        return state == ST_TERMINATED;
    }

    /**
     * 确认是否应该立即关闭实例!
     */
    protected boolean confirmShutdown() {
        if (!isShuttingDown()) {
            return false;
        }
        // 必须是本线程才能调用
        if (!inEventLoop()) {
            throw new IllegalStateException("must be invoked from an event loop");
        }
        // 取消调度任务
        cancelScheduledTasks();

        if (gracefulShutdownStartTime == 0) {
            // 开始时间
            gracefulShutdownStartTime = ScheduledFutureTask.nanoTime();
        }

        // 运行剩余任务及勾子
        if (runAllTasks() || runShutdownHooks()) {
            if (isShutdown()) {
                // 执行器关闭-不再有新的任务。
                return true;
            }

            // 队列中有任务。再等待一段时间，直到没有任务排队等待静默期，或者在静默期为0时终止。
            // See https://github.com/netty/netty/issues/4241
            if (gracefulShutdownQuietPeriod == 0) {
                return true;
            }
            taskQueue.offer(WAKEUP_TASK);
            return false;
        }

        // 时间差
        final long nanoTime = ScheduledFutureTask.nanoTime();

        // 时间差值大于超时时间
        if (isShutdown() || nanoTime - gracefulShutdownStartTime > gracefulShutdownTimeout) {
            return true;
        }

        if (nanoTime - lastExecutionTime <= gracefulShutdownQuietPeriod) {
            // Check if any tasks were added to the queue every 100ms.
            // TODO: Change the behavior of takeTask() so that it returns on timeout.
            taskQueue.offer(WAKEUP_TASK);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }

            return false;
        }

        // 最后一段静默期没有添加任何任务——希望可以安全地关闭。
        // (希望如此，因为我们确实无法保证用户不会调用execute()。)
        return true;
    }

    // 等待终止
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        ObjectUtil.checkNotNull(unit, "unit");
        if (inEventLoop()) {
            throw new IllegalStateException("cannot await termination of the current thread");
        }

        threadLock.await(timeout, unit);

        return isTerminated();
    }

    // 执行
    @Override
    public void execute(Runnable task) {
        ObjectUtil.checkNotNull(task, "task");
        execute(task, !(task instanceof LazyRunnable) && wakesUpForTask(task));
    }

    @Override
    public void lazyExecute(Runnable task) {
        execute(ObjectUtil.checkNotNull(task, "task"), false);
    }

    // 任务添加到阻塞队列
    private void execute(Runnable task, boolean immediate) {
        boolean inEventLoop = inEventLoop();
        addTask(task);
        if (!inEventLoop) { // 若不是当前执行器的任务
            startThread();
            if (isShutdown()) {
                boolean reject = false;
                try {
                    if (removeTask(task)) {
                        reject = true;
                    }
                } catch (UnsupportedOperationException e) {
                    // 任务队列不支持删除，所以我们能做的最好的事情就是继续前进，希望我们能够在任务完全终止之前取回它。
                    // 在最坏的情况下，我们将登录终止。
                }
                if (reject) {
                    reject();
                }
            }
        }

        if (!addTaskWakesUp && immediate) { // 是否唤醒
            wakeup(inEventLoop);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        throwIfInEventLoop("invokeAny");
        return super.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        throwIfInEventLoop("invokeAny");
        return super.invokeAny(tasks, timeout, unit);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        throwIfInEventLoop("invokeAll");
        return super.invokeAll(tasks);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        throwIfInEventLoop("invokeAll");
        return super.invokeAll(tasks, timeout, unit);
    }

    private void throwIfInEventLoop(String method) {
        if (inEventLoop()) {
            throw new RejectedExecutionException("Calling " + method + " from within the EventLoop is not allowed");
        }
    }

    /**
     * 返回为{@link SingleThreadEventExecutor}提供能量的{@link线程}的{@link ThreadProperties}。
     * 如果{@link SingleThreadEventExecutor}尚未启动，该操作将启动并阻塞，直到完全启动为止。
     */
    public final ThreadProperties threadProperties() {
        // 线程属性
        ThreadProperties threadProperties = this.threadProperties;
        // 为null的话，初始化属性
        if (threadProperties == null) {
            Thread thread = this.thread;
            if (thread == null) {
                assert !inEventLoop();
                // 提交一个空任务
                submit(NOOP_TASK).syncUninterruptibly();
                // 赋值线程
                thread = this.thread;
                assert thread != null;
            }
            // 初始化
            threadProperties = new DefaultThreadProperties(thread);
            // 赋值
            if (!PROPERTIES_UPDATER.compareAndSet(this, null, threadProperties)) {
                threadProperties = this.threadProperties;
            }
        }

        return threadProperties;
    }

    /**
     * @deprecated use {@link AbstractEventExecutor.LazyRunnable}
     */
    @Deprecated
    protected interface NonWakeupRunnable extends LazyRunnable { }

    /**
     * 可以重写来控制哪些任务需要唤醒{@link EventExecutor}线程，如果它正在等待，以便它们可以立即运行。
     */
    protected boolean wakesUpForTask(Runnable task) {
        return true;
    }

    // 拒绝
    protected static void reject() {
        throw new RejectedExecutionException("event executor terminated");
    }

    /**
     * 将任务提供给相关的{@link RejectedExecutionHandler}。
     *
     * @param task to reject.
     */
    protected final void reject(Runnable task) {
        rejectedExecutionHandler.rejected(task, this);
    }

    // ScheduledExecutorService 实现
    // 一秒
    private static final long SCHEDULE_PURGE_INTERVAL = TimeUnit.SECONDS.toNanos(1);

    // 启动线程
    private void startThread() {
        if (state == ST_NOT_STARTED) { // 线程状态变为开始执行
            if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
                boolean success = false;
                try {
                    // 启动
                    doStartThread();
                    success = true;
                } finally {
                    if (!success) { // 执行不成功，状态变为未开始
                        STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED);
                    }
                }
            }
        }
    }

    private boolean ensureThreadStarted(int oldState) {
        // 若是未开始状态
        if (oldState == ST_NOT_STARTED) {
            try {
                // 启动线程
                doStartThread();
            } catch (Throwable cause) {
                // 若有异常改为终止状态
                STATE_UPDATER.set(this, ST_TERMINATED);
                terminationFuture.tryFailure(cause);

                if (!(cause instanceof Exception)) {
                    // Also rethrow as it may be an OOME for example
                    PlatformDependent.throwException(cause);
                }
                return true;
            }
        }
        return false;
    }

    private void doStartThread() {
        assert thread == null;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                // 当前线程为执行器线程
                thread = Thread.currentThread();
                if (interrupted) { // 判断线程中断
                    thread.interrupt();
                }

                boolean success = false;
                updateLastExecutionTime(); // 更新最后执行时间
                try {
                    SingleThreadEventExecutor.this.run(); // 执行run方法，线程会阻塞在这直到关闭
                    success = true;
                } catch (Throwable t) {
                    logger.warn("Unexpected exception from an event executor: ", t);
                } finally {
                    for (;;) {
                        int oldState = state;
                        if (oldState >= ST_SHUTTING_DOWN || STATE_UPDATER.compareAndSet(
                                SingleThreadEventExecutor.this, oldState, ST_SHUTTING_DOWN)) {
                            break;
                        }
                    }

                    // 检查在循环结束时是否调用了confirmShutdown()。
                    if (success && gracefulShutdownStartTime == 0) {
                        if (logger.isErrorEnabled()) {
                            logger.error("Buggy " + EventExecutor.class.getSimpleName() + " implementation; " +
                                    SingleThreadEventExecutor.class.getSimpleName() + ".confirmShutdown() must " +
                                    "be called before run() implementation terminates.");
                        }
                    }

                    try {
                        // 运行所有剩余的任务并关闭钩子。此时，事件循环处于ST_SHUTTING_DOWN状态，
                        // 仍然接受任务，这是优雅地关闭和安静期所需要的。
                        for (;;) {
                            if (confirmShutdown()) {
                                break;
                            }
                        }

                        // 现在，我们希望确保从此时起不再添加任何任务。这是通过切换状态来实现的。超过这一点的任何新任务都将被拒绝。
                        for (;;) {
                            int oldState = state;
                            if (oldState >= ST_SHUTDOWN || STATE_UPDATER.compareAndSet(
                                    SingleThreadEventExecutor.this, oldState, ST_SHUTDOWN)) {
                                break;
                            }
                        }

                        // 我们现在在队列中有了最后一组任务，不能再添加了，运行剩下的所有任务。这里不需要循环，这是最后一次循环。
                        confirmShutdown();
                    } finally {
                        try {
                            // 关闭
                            cleanup();
                        } finally {
                            // Lets remove all FastThreadLocals for the Thread as we are about to terminate and notify
                            // the future. The user may block on the future and once it unblocks the JVM may terminate
                            // and start unloading classes.
                            // See https://github.com/netty/netty/issues/6596.
                            FastThreadLocal.removeAll();

                            STATE_UPDATER.set(SingleThreadEventExecutor.this, ST_TERMINATED);
                            threadLock.countDown();
                            int numUserTasks = drainTasks();
                            if (numUserTasks > 0 && logger.isWarnEnabled()) {
                                logger.warn("An event executor terminated with " +
                                        "non-empty task queue (" + numUserTasks + ')');
                            }
                            terminationFuture.setSuccess(null);
                        }
                    }
                }
            }
        });
    }

    // 查看任务数
    final int drainTasks() {
        int numTasks = 0;
        for (;;) {
            Runnable runnable = taskQueue.poll();
            if (runnable == null) {
                break;
            }
            // WAKEUP_TASK应该被丢弃，因为它们是在内部添加的。
            // 重要的一点是，我们没有任何用户任务剩下。
            if (WAKEUP_TASK != runnable) {
                numTasks++;
            }
        }
        return numTasks;
    }

    // 默认线程属性
    private static final class DefaultThreadProperties implements ThreadProperties {
        private final Thread t;

        DefaultThreadProperties(Thread t) {
            this.t = t;
        }

        @Override
        public State state() {
            return t.getState();
        }

        @Override
        public int priority() {
            return t.getPriority();
        }

        @Override
        public boolean isInterrupted() {
            return t.isInterrupted();
        }

        @Override
        public boolean isDaemon() {
            return t.isDaemon();
        }

        @Override
        public String name() {
            return t.getName();
        }

        @Override
        public long id() {
            return t.getId();
        }

        @Override
        public StackTraceElement[] stackTrace() {
            return t.getStackTrace();
        }

        @Override
        public boolean isAlive() {
            return t.isAlive();
        }
    }
}
