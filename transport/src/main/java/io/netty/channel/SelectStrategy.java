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
package io.netty.channel;

import io.netty.util.IntSupplier;

/**
 * 选择策略接口。
 *
 * 提供控制选择循环行为的能力。例如，如果有需要立即处理的事件，则阻塞选择操作可以被延迟或完全跳过。
 */
public interface SelectStrategy {

    /**
     * 指示随后应该进行阻塞选择。
     */
    int SELECT = -1;
    /**
     * 指示应重试IO循环，不直接执行阻塞选择。
     */
    int CONTINUE = -2;
    /**
     * 指示在不阻塞的情况下轮询新事件的IO循环。
     */
    int BUSY_WAIT = -3;

    /**
     * {@link SelectStrategy}可用于控制潜在的select调用的结果。
     *
     * @param selectSupplier The supplier with the result of a select result.
     * @param hasTasks true if tasks are waiting to be processed.
     * @return {@link #SELECT} if the next step should be blocking select {@link #CONTINUE} if
     *         the next step should be to not select but rather jump back to the IO loop and try
     *         again. Any value >= 0 is treated as an indicator that work needs to be done.
     */
    int calculateStrategy(IntSupplier selectSupplier, boolean hasTasks) throws Exception;
}
