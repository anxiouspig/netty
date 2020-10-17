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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  {@link EventExecutorGroup} 实现的抽象基类， 处理并发多线程下的任务。
 */
// 多线程事件执行器组
public abstract class MultithreadEventExecutorGroup extends AbstractEventExecutorGroup {

    private final EventExecutor[] children; // 执行器组中的执行器
    private final Set<EventExecutor> readonlyChildren; // 只读执行器set
    private final AtomicInteger terminatedChildren = new AtomicInteger(); // 原子int
    private final Promise<?> terminationFuture = new DefaultPromise(GlobalEventExecutor.INSTANCE); // 任务终止
    private final EventExecutorChooserFactory.EventExecutorChooser chooser; // 执行器选择

    /**
     * 创建一个实例.
     *
     * @param nThreads          这个实例将使用的线程数量.
     * @param threadFactory     使用的线程工厂, 或者默认为 {@code null} .
     * @param args             通过每个 {@link #newChild(Executor, Object...)} 调用的参数
     */
    protected MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        this(nThreads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory), args);
    }

    /**
     * 创建一个实例.
     *
     * @param nThreads          这个实例将使用的线程数量.
     * @param executor          使用的执行器, 或者默认为 {@code null}.
     * @param args              通过每个 {@link #newChild(Executor, Object...)} 调用的参数
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
    }

    /**
     * 创建一个实例.
     *
     * @param nThreads          这个实例将使用的线程数量.
     * @param executor          使用的执行器, 或者默认为 {@code null}.
     * @param chooserFactory    这个 {@link EventExecutorChooserFactory} 使用的.
     * @param args              通过每个 {@link #newChild(Executor, Object...)} 调用的参数
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
                                            EventExecutorChooserFactory chooserFactory, Object... args) {
        // 线程数必须大于0
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }
        // 执行器为null的话，新建默认线程执行器
        if (executor == null) {
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }
        // 初始化执行器
        children = new EventExecutor[nThreads];
        for (int i = 0; i < nThreads; i ++) {
            boolean success = false;
            try {

                children[i] = newChild(executor, args);
                // 设置成功
                success = true;
            } catch (Exception e) {
                // 作者在思考这地方是不是抛异常会好一点
                // TODO: Think about if this is a good exception type
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                // 设置失败的话，可能linux线程满了，或者jvm堆满了
                if (!success) {
                    for (int j = 0; j < i; j ++) {
                        // 优雅关闭所有执行器
                        children[j].shutdownGracefully();
                    }

                    for (int j = 0; j < i; j ++) {
                        EventExecutor e = children[j];
                        try {
                            while (!e.isTerminated()) {
                                // 等待所有任务都完成，再关闭执行器
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            // 使调用的线程中断掉.
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }
        // 执行器设置成功，传入执行器选择工厂
        chooser = chooserFactory.newChooser(children);
        // 观察者模式，任务成功，如何通知观察者
        final FutureListener<Object> terminationListener = new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                // 当所有执行器全部执行成功，terminationFuture设置成功
                if (terminatedChildren.incrementAndGet() == children.length) {
                    terminationFuture.setSuccess(null);
                }
            }
        };
        // 执行器添加观察者
        for (EventExecutor e: children) {
            e.terminationFuture().addListener(terminationListener);
        }
        // 转set
        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        // 转为只读的
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }

    // 默认线程工厂，将产生FastThread线程
    protected ThreadFactory newDefaultThreadFactory() {
        return new DefaultThreadFactory(getClass());
    }

    // 选择下一个执行器
    @Override
    public EventExecutor next() {
        return chooser.next();
    }

    // 返回执行器的迭代器，这个迭代器是不可修改的
    @Override
    public Iterator<EventExecutor> iterator() {
        return readonlyChildren.iterator();
    }

    /**
     * 返回 {@link EventExecutor}  实现使用的执行器数量. 这个数量等于使用的线程数。
     */
    public final int executorCount() {
        return children.length;
    }

    /**
     * 创建一个新的线程执行器， 之后将通过 {@link #next()}  方法访问.
     * 这个方法将被服务于 {@link MultithreadEventExecutorGroup} 的每个线程调用.
     */
    // 创建执行器，子类实现
    protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;

    // 优雅的关闭
    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        for (EventExecutor l: children) {
            // 等待静默期任务提交
            l.shutdownGracefully(quietPeriod, timeout, unit);
        }
        return terminationFuture();
    }

    // 返回是否所有执行器执行完成
    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    // 直接关闭，这样不优雅，过时的方法。
    @Override
    @Deprecated
    public void shutdown() {
        for (EventExecutor l: children) {
            l.shutdown();
        }
    }

    // 执行器组是否全部正在关闭
    @Override
    public boolean isShuttingDown() {
        for (EventExecutor l: children) {
            if (!l.isShuttingDown()) {
                return false;
            }
        }
        return true;
    }

    // 试行器是否全部关闭
    @Override
    public boolean isShutdown() {
        for (EventExecutor l: children) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    // 执行器是否终止
    @Override
    public boolean isTerminated() {
        for (EventExecutor l: children) {
            if (!l.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    // 等待执行器终止，返回是否终止
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        loop: for (EventExecutor l: children) {
            for (;;) {
                long timeLeft = deadline - System.nanoTime();
                if (timeLeft <= 0) {
                    break loop;
                }
                if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                    break;
                }
            }
        }
        return isTerminated();
    }
}
