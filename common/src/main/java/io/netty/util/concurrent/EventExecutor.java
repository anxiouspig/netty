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

/**
 * 这个 {@link EventExecutor} 是一个特殊的 {@link EventExecutorGroup} ，
 * 包含一些方便的方法去查看一个 {@link Thread} 是否在事件循环中被执行.
 * 除此之外, 它也继承自 {@link EventExecutorGroup} 允许用通用的方式来访问方法
 *
 */
public interface EventExecutor extends EventExecutorGroup {

    /**
     * 返回它自己的引用
     */
    @Override
    EventExecutor next();

    /**
     * 返回 {@link EventExecutorGroup} ， 它是这个 {@link EventExecutor} 的父亲,
     */
    EventExecutorGroup parent();

    /**
     * 调用 {@link #inEventLoop(Thread)} 用 {@link Thread#currentThread()} 作为参数
     */
    boolean inEventLoop();

    /**
     * 返回 {@code true} 如果给定的 {@link Thread} 在事件循环中被执行,
     * {@code false} otherwise.
     */
    boolean inEventLoop(Thread thread);

    /**
     * 返回一个新的 {@link Promise}.
     */
    <V> Promise<V> newPromise();

    /**
     * 创建一个新的 {@link ProgressivePromise}.
     */
    <V> ProgressivePromise<V> newProgressivePromise();

    /**
     * 创建一个新的 {@link Future} ，它已被标记为成功. 所以 {@link Future#isSuccess()} 方法
     * 将返回 {@code true}. 所有 {@link FutureListener} 将会直接被通知. 而且
     * 每次调用阻塞方法都会返回，而不阻塞.
     */
    <V> Future<V> newSucceededFuture(V result);

    /**
     * 创建一个新的 {@link Future} ，它已被标记为失败. 所以 {@link Future#isSuccess()}
     * 将返回 {@code false}. 所有 {@link FutureListener} 将会直接被通知. 而且
     * 每次调用阻塞方法都会返回，而不阻塞.
     */
    <V> Future<V> newFailedFuture(Throwable cause);
}
