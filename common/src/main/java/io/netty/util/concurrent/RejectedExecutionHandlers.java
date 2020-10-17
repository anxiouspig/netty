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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * 暴露帮助方法用于创建不同的 {@link RejectedExecutionHandler}s.
 */
// 策略模式
public final class RejectedExecutionHandlers {
    private static final RejectedExecutionHandler REJECT = new RejectedExecutionHandler() {
        // 拒绝会抛异常
        @Override
        public void rejected(Runnable task, SingleThreadEventExecutor executor) {
            throw new RejectedExecutionException();
        }
    };

    private RejectedExecutionHandlers() { }

    /**
     * 返回一个 {@link RejectedExecutionHandler}，这总会抛出一个 {@link RejectedExecutionException}.
     */
    public static RejectedExecutionHandler reject() {
        return REJECT;
    }

    /**
     * 在配置的时间范围内，由于限制无法添加任务时，会尝试后退。
     * 如果事件在 EventLoop 外添加，这意味着
     * {@link EventExecutor#inEventLoop()} 返回 {@code false}.
     */
    public static RejectedExecutionHandler backoff(final int retries, long backoffAmount, TimeUnit unit) {
        ObjectUtil.checkPositive(retries, "retries"); // 检查是否正数
        final long backOffNanos = unit.toNanos(backoffAmount); // 纳秒
        return new RejectedExecutionHandler() {
            @Override
            public void rejected(Runnable task, SingleThreadEventExecutor executor) {
                if (!executor.inEventLoop()) { // 执行器是否在EventLoop中
                    for (int i = 0; i < retries; i++) { // 尝试次数
                        // 尝试唤醒执行器，清空执行队列.
                        executor.wakeup(false);

                        LockSupport.parkNanos(backOffNanos); // 锁住一段时间
                        if (executor.offerTask(task)) { // 如果能添加任务，则直接返回
                            return;
                        }
                    }
                }
                // 或者尝试往EventLoop中添加任务，或者不能添加的话就回退。
                throw new RejectedExecutionException(); // 抛出拒绝异常
            }
        };
    }
}
