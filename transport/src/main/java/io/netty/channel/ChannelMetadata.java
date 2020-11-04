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
package io.netty.channel;

import static io.netty.util.internal.ObjectUtil.checkPositive;

import java.net.SocketAddress;

/**
 * 表示一个{@link Channel}实现的属性。
 */
public final class ChannelMetadata {

    private final boolean hasDisconnect;
    private final int defaultMaxMessagesPerRead;

    /**
     * 创建一个新的实例
     *
     * @param hasDisconnect     {@code true}如果且仅如果通道有{@code disconnect()}操作，允许用户断开连接，然后调用
     *                          {@link Channel#connect(SocketAddress)}。
     */
    public ChannelMetadata(boolean hasDisconnect) {
        this(hasDisconnect, 1);
    }

    /**
     * 创建一个新的实例
     *
     * @param hasDisconnect     {@code true}如果且仅如果通道有{@code disconnect()}操作，允许用户断开连接，然后再次调用
     *                          {@link Channel#connect(SocketAddress)}，如UDP/IP。
     * @param defaultMaxMessagesPerRead 如果使用了{@link MaxMessagesRecvByteBufAllocator}，那么这个值将被设置为
     *                                  {@link MaxMessagesRecvByteBufAllocator#maxMessagesPerRead()}。必须是{@code > 0}。
     */
    public ChannelMetadata(boolean hasDisconnect, int defaultMaxMessagesPerRead) { // false 16
        checkPositive(defaultMaxMessagesPerRead, "defaultMaxMessagesPerRead");
        this.hasDisconnect = hasDisconnect;
        this.defaultMaxMessagesPerRead = defaultMaxMessagesPerRead;
    }

    /**
     * 如果且仅如果通道有{@code disconnect()}操作，允许用户断开连接，然后再次调用{@link Channel#connect(SocketAddress)}，
     * 如UDP/IP，则返回{@code true}。
     */
    public boolean hasDisconnect() {
        return hasDisconnect;
    }

    /**
     * 如果使用了{@link MaxMessagesRecvByteBufAllocator}，那么这就是
     * {@link MaxMessagesRecvByteBufAllocator#maxMessagesPerRead()}的默认值。
     */
    public int defaultMaxMessagesPerRead() {
        return defaultMaxMessagesPerRead;
    }
}
