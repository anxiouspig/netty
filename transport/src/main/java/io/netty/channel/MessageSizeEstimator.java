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
package io.netty.channel;

/**
 * 负责估计消息的大小。大小大约表示消息将在内存中保留多少内存。
 */
public interface MessageSizeEstimator {

    /**
     * 创建一个新句柄。句柄提供实际的操作。
     */
    Handle newHandle();

    interface Handle {

        /**
         * 计算给定消息的大小。
         *
         * @param msg       需要为其计算大小的消息
         * @return size     以字节为单位的大小。返回的大小必须是>= 0
         */
        int size(Object msg);
    }
}
