/*
 * Copyright 2014 The Netty Project
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

import io.netty.util.internal.PromiseNotificationUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * {@link GenericFutureListener}的实现可以添加{@link Promise}s
 * 并在完成时通知他们.
 *
 * @param <V> 返回的结果类型
 * @param <F> future类型
 */
// 任务成功通知类
public class PromiseNotifier<V, F extends Future<V>> implements GenericFutureListener<F> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PromiseNotifier.class);
    private final Promise<? super V>[] promises;
    private final boolean logNotifyFailure;

    /**
     * 创建一个实例
     *
     * @param promises  这个 {@link Promise}s 去通知，一旦 {@link GenericFutureListener} 被通知.
     */
    @SafeVarargs
    public PromiseNotifier(Promise<? super V>... promises) {
        this(true, promises);
    }

    /**
     * 创建一个实例
     *
     * @param logNotifyFailure {@code true} 通知失败时是否记录日志
     * @param promises  这个 {@link Promise}s 去通知，一旦 {@link GenericFutureListener} 被通知.
     */
    @SafeVarargs
    public PromiseNotifier(boolean logNotifyFailure, Promise<? super V>... promises) {
        // 检查不能为null
        checkNotNull(promises, "promises");
        for (Promise<? super V> promise: promises) {
            if (promise == null) {
                throw new IllegalArgumentException("promises contains null Promise");
            }
        }
        // 克隆一个
        this.promises = promises.clone();
        this.logNotifyFailure = logNotifyFailure;
    }

    @Override
    public void operationComplete(F future) throws Exception {
        InternalLogger internalLogger = logNotifyFailure ? logger : null;
        if (future.isSuccess()) {
            V result = future.get();
            for (Promise<? super V> p: promises) {
                PromiseNotificationUtil.trySuccess(p, result, internalLogger);
            }
        } else if (future.isCancelled()) {
            for (Promise<? super V> p: promises) {
                PromiseNotificationUtil.tryCancel(p, internalLogger);
            }
        } else {
            Throwable cause = future.cause();
            for (Promise<? super V> p: promises) {
                PromiseNotificationUtil.tryFailure(p, cause, internalLogger);
            }
        }
    }
}
