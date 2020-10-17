/*
 * Copyright 2016 The Netty Project
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
import io.netty.util.internal.UnstableApi;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link EventExecutorGroup} 将保证 {@link Runnable} 的执行顺序，但不保证用哪个
 * {@link EventExecutor} (就是{@link Thread}) 去执行 {@link Runnable}s.
 *
 * 这个 {@link EventExecutorGroup#next()} 包装的 {@link EventExecutorGroup} 不能是 {@link OrderedEventExecutor} 类型.
 */
// 包装器模式？
@UnstableApi // 多线程使用，未加锁而是使用判断
public final class NonStickyEventExecutorGroup implements EventExecutorGroup {
    private final EventExecutorGroup group; // 包装这个
    private final int maxTaskExecutePerRun;

    /**
     * 创建一个实例. 想要给 {@link EventExecutorGroup} 不能包含
     * 任何 {@link OrderedEventExecutor}s.
     */
    public NonStickyEventExecutorGroup(EventExecutorGroup group) {
        this(group, 1024);
    }

    /**
     * 创建一个实例. 想要给 {@link EventExecutorGroup} 不能包含
     * 任何 {@link OrderedEventExecutor}s.
     */
    public NonStickyEventExecutorGroup(EventExecutorGroup group, int maxTaskExecutePerRun) {
        this.group = verify(group);
        // 最大任务执行数量
        this.maxTaskExecutePerRun = ObjectUtil.checkPositive(maxTaskExecutePerRun, "maxTaskExecutePerRun");
    }

    // 判断不是OrderedEventExecutor类型
    private static EventExecutorGroup verify(EventExecutorGroup group) {
        Iterator<EventExecutor> executors = ObjectUtil.checkNotNull(group, "group").iterator();
        while (executors.hasNext()) {
            EventExecutor executor = executors.next();
            if (executor instanceof OrderedEventExecutor) {
                throw new IllegalArgumentException("EventExecutorGroup " + group
                        + " contains OrderedEventExecutors: " + executor);
            }
        }
        return group;
    }

    // 产生非严格顺序执行器，包装一下
    private NonStickyOrderedEventExecutor newExecutor(EventExecutor executor) {
        return new NonStickyOrderedEventExecutor(executor, maxTaskExecutePerRun);
    }

    @Override
    public boolean isShuttingDown() {
        return group.isShuttingDown();
    }

    @Override
    public Future<?> shutdownGracefully() {
        return group.shutdownGracefully();
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        return group.shutdownGracefully(quietPeriod, timeout, unit);
    }

    @Override
    public Future<?> terminationFuture() {
        return group.terminationFuture();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void shutdown() {
        group.shutdown();
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<Runnable> shutdownNow() {
        return group.shutdownNow();
    }

    @Override
    public EventExecutor next() {
        return newExecutor(group.next());
    }

    // 迭代方法
    @Override
    public Iterator<EventExecutor> iterator() {
        final Iterator<EventExecutor> itr = group.iterator();
        return new Iterator<EventExecutor>() {
            @Override
            public boolean hasNext() {
                return itr.hasNext();
            }

            @Override
            public EventExecutor next() {
                return newExecutor(itr.next());
            }

            @Override
            public void remove() {
                itr.remove();
            }
        };
    }

    @Override
    public Future<?> submit(Runnable task) {
        return group.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return group.submit(task, result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return group.submit(task);
    }

    // 调度
    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return group.schedule(command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return group.schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return group.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return group.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    public boolean isShutdown() {
        return group.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return group.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return group.awaitTermination(timeout, unit);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return group.invokeAll(tasks);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return group.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return group.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return group.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        group.execute(command);
    }

    // 实现
    private static final class NonStickyOrderedEventExecutor extends AbstractEventExecutor
            implements Runnable, OrderedEventExecutor {
        private final EventExecutor executor; // 包装它
        private final Queue<Runnable> tasks = PlatformDependent.newMpscQueue(); // 任务队列

        private static final int NONE = 0; // 无任务状态
        private static final int SUBMITTED = 1; // 提交状态
        private static final int RUNNING = 2; // 执行状态

        private final AtomicInteger state = new AtomicInteger(); // 原子状态
        private final int maxTaskExecutePerRun; // 最大执行数量

        NonStickyOrderedEventExecutor(EventExecutor executor, int maxTaskExecutePerRun) {
            super(executor);
            this.executor = executor; // 父执行器组
            this.maxTaskExecutePerRun = maxTaskExecutePerRun;
        }

        @Override
        public void run() {
            if (!state.compareAndSet(SUBMITTED, RUNNING)) { // state设为执行状态
                return;
            }
            for (;;) {
                int i = 0;
                try {
                    for (; i < maxTaskExecutePerRun; i++) { // 每次循环的最大执行数
                        Runnable task = tasks.poll(); // 拿出任务
                        if (task == null) {
                            break;
                        }
                        safeExecute(task); // 执行任务
                    }
                } finally {
                    if (i == maxTaskExecutePerRun) {
                        try {
                            state.set(SUBMITTED);
                            // 循环执行
                            executor.execute(this);
                            return; // 返回
                        } catch (Throwable ignore) { // 若有异常
                            // 将状态重置回运行状态，因为我们将继续执行任务。
                            state.set(RUNNING);
                            // 如果发生了错误，我们应该忽略它，让循环再次运行，因为我们没有什么别的办法。
                            // 很有可能是由于任务队列满了而引发的。在这种情况下，我们只需要运行更多的任务，稍后再试。
                        }
                    } else { // 没那么多任务了
                        state.set(NONE); // 设为无任务状态
                        // 将状态设置为NONE后，再看一次任务队列。如果它是空的，那么我们就可以从这个方法返回。
                        // 否则，就意味着生产者线程在上面的tasks.poll()和这里的state.set(NONE)之间调用了execute(Runnable)并enqueued了一个任务。
                        // 当这种情况发生时，有两种可能的情况
                        //
                        // 1. 生产者线程看到state == NONE，因此compareAndSet(NONE, SUBMITTED)成功将状态设置为SUBMITTED。
                        // 这意味着生产者将调用/已经调用executor.execute(this)。
                        // 在这种情况下，我们可以直接返回。
                        //
                        // 2. 生产者线程看不到状态变化，因此compareAndSet(NONE, SUBMITTED)返回false。
                        // 在这种情况下，生产者线程不会调用executor.execute。
                        // 在这种情况下，我们需要将状态改为RUNNING，并继续运行。
                        //
                        // 上述情况可以通过执行compareAndSet(NONE, RUNNING)来区分。
                        // 如果返回 "false"，则为case 1；否则为case 2。
                        //
                        // 如果其他线程把NONE变成RUNNING，这个就会失败
                        if (tasks.isEmpty() || !state.compareAndSet(NONE, RUNNING)) {
                            return; // 若任务为空，或状态非无任务状态
                        }
                    }
                }
            }
        }

        @Override
        public boolean inEventLoop(Thread thread) {
            return false;
        }

        @Override
        public boolean inEventLoop() {
            return false;
        }

        @Override
        public boolean isShuttingDown() {
            return executor.isShutdown();
        }

        @Override
        public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
            return executor.shutdownGracefully(quietPeriod, timeout, unit);
        }

        @Override
        public Future<?> terminationFuture() {
            return executor.terminationFuture();
        }

        @Override
        public void shutdown() {
            executor.shutdown();
        }

        @Override
        public boolean isShutdown() {
            return executor.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return executor.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return executor.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            if (!tasks.offer(command)) { // 队列不能添加，拒绝
                throw new RejectedExecutionException();
            }
            if (state.compareAndSet(NONE, SUBMITTED)) { // 刚开始把state变成提交状态
                // 其实这中间也有可能发生runnable被拾取的情况，但我们不必太在意，只需自己执行即可。最坏的情况是，当调用run()时，这将是一个NOOP。
                executor.execute(this);
            }
        }
    }
}
