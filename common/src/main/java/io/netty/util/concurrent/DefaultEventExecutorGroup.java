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

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * {@link MultithreadEventExecutorGroup} 的默认实现，将会使用 {@link DefaultEventExecutor} 实例去处理任务。
 */
public class DefaultEventExecutorGroup extends MultithreadEventExecutorGroup {
    /**
     * @see #DefaultEventExecutorGroup(int, ThreadFactory)
     */
    public DefaultEventExecutorGroup(int nThreads) {
        this(nThreads, null);
    }

    /**
     * 创一个实例
     *
     * @param nThreads          实例使用的线程数量
     * @param threadFactory     使用的线程工厂, 或者 {@code null} 的话，将使用默认执行器.
     */
    public DefaultEventExecutorGroup(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, SingleThreadEventExecutor.DEFAULT_MAX_PENDING_EXECUTOR_TASKS,
                RejectedExecutionHandlers.reject());
    }

    // 参考父类构造方法，这里把后面不是同种类型的参数变为变长参数，觉得这样设计不太好，
    public DefaultEventExecutorGroup(int nThreads, ThreadFactory threadFactory, int maxPendingTasks,
                                     RejectedExecutionHandler rejectedHandler) {
        super(nThreads, threadFactory, maxPendingTasks, rejectedHandler);
    }

    // 构造线程执行器，上面的方法的参数会传到这里来
    @Override
    protected EventExecutor newChild(Executor executor, Object... args) throws Exception {
        return new DefaultEventExecutor(this, executor, (Integer) args[0], (RejectedExecutionHandler) args[1]);
    }
}
