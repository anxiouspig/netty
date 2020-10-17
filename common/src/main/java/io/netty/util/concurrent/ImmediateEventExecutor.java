/*
 * Copyright 2013 The Netty Project
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
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * 在调用者的线程中执行{@link Runnable}对象。如果{@link #execute(Runnable)}是可重入的，它将被排队，直到原始的{@link Runnable}完成执行。
 * <p>
 * 所有从{@link #execute(Runnable)}抛出的{@link Throwable}对象将被吞入并记录。
 * 这是为了确保所有排队的{@link Runnable}对象都有机会运行。
 */
public final class ImmediateEventExecutor extends AbstractEventExecutor {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ImmediateEventExecutor.class);
    // 单例模式
    public static final ImmediateEventExecutor INSTANCE = new ImmediateEventExecutor();
    /**
     * 如果我们正在执行一个Runnable，那么一个Runnable将会被排队。这是为了防止{@link StackOverflowError}。
     */
    // 延迟运行队列
    private static final FastThreadLocal<Queue<Runnable>> DELAYED_RUNNABLES = new FastThreadLocal<Queue<Runnable>>() {
        @Override
        protected Queue<Runnable> initialValue() throws Exception {
            return new ArrayDeque<Runnable>();
        }
    };
    /**
     * 如果我们正在执行一个runnable，设置为{@code true}。
     */
    // 运行状态
    private static final FastThreadLocal<Boolean> RUNNING = new FastThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() throws Exception {
            return false;
        }
    };

    private final Future<?> terminationFuture = new FailedFuture<Object>(
            GlobalEventExecutor.INSTANCE, new UnsupportedOperationException());

    private ImmediateEventExecutor() { }

    @Override
    public boolean inEventLoop() {
        return true;
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return true;
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    @Deprecated
    public void shutdown() { }

    @Override
    public boolean isShuttingDown() {
        return false;
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return false;
    }

    @Override
    public void execute(Runnable command) {
        ObjectUtil.checkNotNull(command, "command");
        if (!RUNNING.get()) { // 若不在执行
            RUNNING.set(true);
            try {
                command.run(); // 执行
            } catch (Throwable cause) {
                logger.info("Throwable caught while executing Runnable {}", command, cause);
            } finally {
                Queue<Runnable> delayedRunnables = DELAYED_RUNNABLES.get(); // 拿到任务队列
                Runnable runnable;
                while ((runnable = delayedRunnables.poll()) != null) { // 顺序执行
                    try {
                        runnable.run();
                    } catch (Throwable cause) {
                        logger.info("Throwable caught while executing Runnable {}", runnable, cause);
                    }
                }
                RUNNING.set(false); // 执行结束
            }
        } else { // 若正在执行，则加入队列
            DELAYED_RUNNABLES.get().add(command);
        }
    }

    @Override
    public <V> Promise<V> newPromise() {
        return new ImmediatePromise<V>(this);
    }

    @Override
    public <V> ProgressivePromise<V> newProgressivePromise() {
        return new ImmediateProgressivePromise<V>(this);
    }

    static class ImmediatePromise<V> extends DefaultPromise<V> {
        ImmediatePromise(EventExecutor executor) {
            super(executor);
        }

        @Override
        protected void checkDeadLock() {
            // No check
        }
    }

    static class ImmediateProgressivePromise<V> extends DefaultProgressivePromise<V> {
        ImmediateProgressivePromise(EventExecutor executor) {
            super(executor);
        }

        @Override
        protected void checkDeadLock() {
            // No check
        }
    }
}
