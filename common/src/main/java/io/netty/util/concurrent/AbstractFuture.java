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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 抽象 {@link Future} 实现，当不允许取消的时候.
 *
 * @param <V>
 */
public abstract class AbstractFuture<V> implements Future<V> {

    @Override
    public V get() throws InterruptedException, ExecutionException {
        await(); // 等待

        Throwable cause = cause(); // 失败原因
        if (cause == null) { // 为null的话，获取结果
            return getNow();
        }
        if (cause instanceof CancellationException) {
            throw (CancellationException) cause; // 取消异常的话抛出
        }
        throw new ExecutionException(cause); // 其他抛出执行异常
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (await(timeout, unit)) { // 等待固定时间
            Throwable cause = cause();
            if (cause == null) {
                return getNow(); // 到时间了，获取结果
            }
            if (cause instanceof CancellationException) {
                throw (CancellationException) cause; // 取消异常的话抛出
            }
            throw new ExecutionException(cause); // 其他抛出执行异常
        }
        throw new TimeoutException(); // 抛出超时异常
    }
}
