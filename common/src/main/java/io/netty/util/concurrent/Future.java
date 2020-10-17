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

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;


/**
 * 异步操作的结果.
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
// netty重写了Future实现，并且使用一样的名字
// 观察者模式，添加观察者
public interface Future<V> extends java.util.concurrent.Future<V> {

    /**
     * 返回 {@code true}，仅当I/O操作完成。
     */
    boolean isSuccess();

    /**
     * 返回 {@code true}，仅当操作通过 {@link #cancel(boolean)} 取消.
     */
    boolean isCancellable();

    /**
     * 如果I/O操作失败了，返回操作失败的原因。
     *
     * @return 失败操作的原因.
     *         如果成功或这个操作未完成，返回{@code null}
     */
    Throwable cause();

    /**
     * 添加特定的监听器到这个future.
     * 如果 future {@linkplain #isDone() done}，这个指定的监听器将得到通知.
     * 如果这个 future 已经完成, 指定的监听器即刻会得到通知.
     */
    Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    // 一次性添加多个观察者
    Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * 从这个 future 移除指定监听器的第一个.
     * 当这个 future 调用 {@linkplain #isDone() done}，这个监听器将不会得到通知.  If the specified
     * 如果指定监听器未与这个 future 关联, 这个方法直接返回，啥也不做.
     */
    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    // 一次性移除多个观察者
    Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * 等待这个 future 完成, 若这个 future 失败了，则抛出原因。
     */
    Future<V> sync() throws InterruptedException;

    // 等待任务完成，但是不受线程中断影响
    Future<V> syncUninterruptibly();

    /**
     * 等待这个 future 完成.
     *
     * @throws InterruptedException
     *         如果当前线程被中断了
     */
    Future<V> await() throws InterruptedException;


    // 这个方法等待任务完成，但是不受线程中断影响。
    Future<V> awaitUninterruptibly();

    /**
     * 在特定的时间范围内等待 future 完成
     *
     * @return {@code true} 如果future在指定的时间内完成
     *
     * @throws InterruptedException
     *         如果当前线程被中断
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    // 作用同上，但是入参是毫秒
    boolean await(long timeoutMillis) throws InterruptedException;

    // 作用同上，但是不受线程中断影响
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    // 作用同上，但是不受线程中断影响，入参是毫秒
    boolean awaitUninterruptibly(long timeoutMillis);

    /**
     * 不阻塞，直接返回结果. 如果任务还未完成，将返回 {@code null}.
     *
     * 由于 {@code null} 也可能标记为任务成功， you also need to check
     * 你也需要通过 {@link #isDone()} 检查任务是否完成，或者不依赖返回 {@code null} 值.
     */
    V getNow();

    /**
     * {@inheritDoc}
     *
     * 如果取消成功，是否通过抛 {@link CancellationException} 来表示失败.
     */
    @Override
    boolean cancel(boolean mayInterruptIfRunning);
}
