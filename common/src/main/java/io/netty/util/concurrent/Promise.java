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

/**
 * 指定可写的 {@link Future} .
 */
public interface Promise<V> extends Future<V> {

    /**
     * 标记 future为成功状态，并通知所有监听者。
     *
     * 如果成功或已经失败，它将抛出一个{@link IllegalStateException}。
     */
    Promise<V> setSuccess(V result);

    /**
     * 标记这个 future成功，并通知所有监听者。
     *
     * @return {@code true} 仅当成功，标记这个.future为成功。
     * 否则 {@code false}，因为这个future已经标记为成功或者失败。
     */
    boolean trySuccess(V result);

    /**
     * 标记这个 future 失败，并通知所有监听者。
     *
     * 如果成功或已经失败，它将抛出一个{@link IllegalStateException}。
     */
    Promise<V> setFailure(Throwable cause);

    /**
     * 标记这个 future 失败，并通知所有监听者。
     *
     * @return {@code true} 仅当成功标记这个. 否则 {@code false}，因为这个future已经标记为成功或者失败。
     */
    boolean tryFailure(Throwable cause);

    /**
     * 标记这个future不能被取消
     * @return {@code true} 如果成功标记这个 future 不能取消，
     *  {@code false} 如果这个.future已经被取消
     */
    boolean setUncancellable();

    @Override
    Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> await() throws InterruptedException;

    @Override
    Promise<V> awaitUninterruptibly();

    @Override
    Promise<V> sync() throws InterruptedException;

    @Override
    Promise<V> syncUninterruptibly();
}
