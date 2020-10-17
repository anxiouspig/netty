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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 这个 {@link EventExecutorGroup} 负责提供 {@link EventExecutor}'s 去使用
 * 通过这个 {@link #next()} 方法. 除此之外, 也负责去处理他们的生命周期
 * 允许在全局范围关闭他们.
 *
 */
public interface EventExecutorGroup extends ScheduledExecutorService, Iterable<EventExecutor> {

    /**
     * 如果  {@link EventExecutorGroup} 管理的所有 {@link EventExecutor}s 调用
     * {@linkplain #shutdownGracefully() 优雅的关闭} 或者调用 {@linkplain #isShutdown() 关闭} 的话，返回 {@code true}.
     */
    boolean isShuttingDown();

    /**
     * 便捷的方法 {@link #shutdownGracefully(long, long, TimeUnit)} 用合理的默认值.
     *
     * @return the {@link #terminationFuture()}
     */
    Future<?> shutdownGracefully();

    /**
     * 标记这个执行器，调用者想要执行器关闭.  一旦方法被调用,
     * {@link #isShuttingDown()} 将返回 {@code true}, 试行器就会关闭他自己.
     * 不像 {@link #shutdown()}, 优雅的关闭确保在静默期无任务被提交
     * (通常是几秒钟) 在关闭之前. 如果一个任务在静默期被提交,
     * 它保证会被接受，之后静默期会重新开始
     *
     * @param quietPeriod 文档中描述的静默期
     * @param timeout     直到执行器被 {@linkplain #shutdown()} ，等待的最大时间
     *                    如果一个任务在静默期提交，将会被忽略
     * @param unit         {@code quietPeriod} 和 {@code timeout} 的单位
     *
     * @return the {@link #terminationFuture()}
     */
    Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);

    /**
     * 返回这个 {@link Future} ， 当这个 {@link EventExecutorGroup} 管理的所有 {@link EventExecutor}s 被终止，会得到通知.
     */
    Future<?> terminationFuture();

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} 或者 {@link #shutdownGracefully()} 代替.
     */
    @Override
    @Deprecated
    void shutdown();

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} 或者 {@link #shutdownGracefully()} 代替.
     */
    @Override
    @Deprecated
    List<Runnable> shutdownNow();

    /**
     * Returns 这个 {@link EventExecutorGroup} 管理的 {@link EventExecutor}s 之一.
     */
    EventExecutor next();

    @Override
    Iterator<EventExecutor> iterator();

    @Override
    Future<?> submit(Runnable task);

    @Override
    <T> Future<T> submit(Runnable task, T result);

    @Override
    <T> Future<T> submit(Callable<T> task);

    @Override
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    @Override
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}
