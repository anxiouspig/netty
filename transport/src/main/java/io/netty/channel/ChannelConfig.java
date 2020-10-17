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

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.socket.SocketChannelConfig;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Map;

/**
 * 一个{@link Channel}的一组配置属性。
 * <p>
 * 请下传到更具体的配置类型，如{@link SocketChannelConfig}，或使用{@link #setOptions(Map)}来设置。
 * <pre>
 * {@link Channel} ch = ...;
 * {@link SocketChannelConfig} cfg = <strong>({@link SocketChannelConfig}) ch.getConfig();</strong>
 * cfg.setTcpNoDelay(false);
 * </pre>
 *
 * <h3>选项图</h3>
 *
 * 选项图属性是一个动态的只写属性，它允许配置一个{@link Channel}，而不需要下挂其关联的{@link ChannelConfig}。
 * 要更新选项图，请调用{@link #setOptions(Map)}。
 * <p>
 * 所有{@link ChannelConfig}有以下选项。
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>名称</th><th>相关的设置方法</th>。
 * </tr><tr>
 * <td>{@link ChannelOption#CONNECT_TIMEOUT_MILLIS}</td><td>{@link #setConnectTimeoutMillis(int)}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#WRITE_SPIN_COUNT}</td><td>{@link #setWriteSpinCount(int)}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#WRITE_BUFFER_WATER_MARK}</td><td>{@link #setWriteBufferWaterMark(WriteBufferWaterMark)}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#ALLOCATOR}</td><td>{@link #setAllocator(ByteBufAllocator)}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#AUTO_READ}</td><td>{@link #setAutoRead(boolean)}</td>
 * </tr>
 * </table>
 * <p>
 * 在{@link ChannelConfig}的子类型中，有更多的选项。 例如，你可以配置TCP/IP套接字所特有的参数，如
 * {@link SocketChannelConfig}中所述。
 */
public interface ChannelConfig {

    /**
     * 返回所有设置的{@link ChannelOption}。
     */
    Map<ChannelOption<?>, Object> getOptions();

    /**
     * 从指定的{@link Map}中设置配置属性。
     */
    boolean setOptions(Map<ChannelOption<?>, ?> options);

    /**
     * 返回给定的{@link ChannelOption}的值。
     */
    <T> T getOption(ChannelOption<T> option);

    /**
     * 用指定的名称和值设置配置属性。要正确地重写这个方法，你必须调用超级类。
     * <pre>
     * public boolean setOption(ChannelOption&lt;T&gt; option, T value) {
     *     if (super.setOption(option, value)) {
     *         return true;
     *     }
     *
     *     if (option.equals(additionalOption)) {
     *         ....
     *         return true;
     *     }
     *
     *     return false;
     * }
     * </pre>
     *
     * @return {@code true} if and only if the property has been set
     */
    <T> boolean setOption(ChannelOption<T> option, T value);

    /**
     * 返回通道的连接超时时间，单位为毫秒。 如果{@link Channel}不支持连接操作，
     * 那么这个属性就不会被使用，因此会被忽略。
     *
     * @return 连接超时时间，单位为毫秒。 如果禁用，则为{@code 0}。
     */
    int getConnectTimeoutMillis();

    /**
     * 设置通道的连接超时，单位为毫秒。 如果{@link Channel}不支持连接操作，那么这个属性就不会被使用，因此会被忽略。
     *
     * @param connectTimeoutMillis 连接超时时间，以毫秒为单位。
     *                              {@code 0}禁用。
     */
    ChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    /**
     * @deprecated 使用 {@link MaxMessagesRecvByteBufAllocator} and
     * {@link MaxMessagesRecvByteBufAllocator#maxMessagesPerRead()}.
     * <p>
     * 返回每个读取循环的最大读取信息数量。
     * 一个 {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object) channelRead()}事件。
     * 如果这个值大于1，事件循环可能会尝试多次读取以获取多个消息。
     */
    @Deprecated
    int getMaxMessagesPerRead();

    /**
     * @deprecated 使用 {@link MaxMessagesRecvByteBufAllocator} 和
     * {@link MaxMessagesRecvByteBufAllocator#maxMessagesPerRead(int)}.
     * <p>
     * 设置每个读取循环的最大读取信息数量。
     * 如果这个值大于1，事件循环可能会尝试多次读取以获取多个消息。
     */
    @Deprecated
    ChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead);

    /**
     * 返回写操作的最大循环次数，直到{@link WritableByteChannel#write(ByteBuffer)}返回一个非零值。
     * 它类似于并发编程中自旋锁的作用。它可以提高内存利用率和写入吞吐量，具体取决于JVM运行的平台。 默认值是{@code 16}。
     */
    int getWriteSpinCount();

    /**
     * 设置写操作的最大循环次数，直到{@link WritableByteChannel#write(ByteBuffer)}返回一个非零值。
     * 它类似于并发编程中使用的自旋锁。它可以提高内存利用率和写入吞吐量，具体取决于JVM运行的平台。 默认值是{@code 16}。
     *
     * @throws IllegalArgumentException
     *         如果指定的值是{@code 0}或小于{@code 0}。
     */
    ChannelConfig setWriteSpinCount(int writeSpinCount);

    /**
     * 返回{@link ByteBufAllocator}，它用于通道分配缓冲区。
     */
    ByteBufAllocator getAllocator();

    /**
     * 设置用于通道分配缓冲区的{@link ByteBufAllocator}。
     */
    ChannelConfig setAllocator(ByteBufAllocator allocator);

    /**
     * 返回 {@link RecvByteBufAllocator}，它用于通道分配接收缓冲区。
     */
    <T extends RecvByteBufAllocator> T getRecvByteBufAllocator();

    /**
     * 设置{@link RecvByteBufAllocator}，用于通道分配接收缓冲区。
     */
    ChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    /**
     * 返回{@code true}，如果且仅如果{@link ChannelHandlerContext#read()}将被自动调用，
     * 那么用户应用程序根本不需要调用它。默认值是{@code true}。
     */
    boolean isAutoRead();

    /**
     * 设置是否会自动调用{@link ChannelHandlerContext#read()}，以便用户应用程序根本不需要调用它。默认值是{@code true}。
     */
    ChannelConfig setAutoRead(boolean autoRead);

    /**
     * 返回{@code true}，如果且仅如果{@link Channel}在写入失败时自动关闭。默认为{@code true}。
     */
    boolean isAutoClose();

    /**
     * 设置{@link Channel}是否应该在写入失败时立即自动关闭。默认值是{@code true}。
     */
    ChannelConfig setAutoClose(boolean autoClose);

    /**
     * 返回写入缓冲区的高水位线。 如果写缓冲区中排队的字节数超过这个值，{@link Channel#isWritable()}将开始返回{@code false}。
     */
    int getWriteBufferHighWaterMark();

    /**
     * <p>
     * 设置写入缓冲区的高水位线。 如果写缓冲区中排队的字节数超过这个值，{@link Channel#isWritable()}将开始返回{@code false}。
     */
    ChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    /**
     * 返回写缓冲区的低水位。 一旦写缓冲区中排队的字节数超过{@linkplain #setWriteBufferHighWaterMark(int) high water mark}，
     * 然后降到这个值以下，{@link Channel#isWritable()}就会重新开始返回{@code true}。
     */
    int getWriteBufferLowWaterMark();

    /**
     * <p>
     * 设置写入缓冲区的最低水位线。 一旦写缓冲区中排队的字节数超过了{@linkplain #setWriteBufferHighWaterMark(int) high water mark}，
     * 然后降到这个值以下，{@link Channel#isWritable()}又会开始返回{@code true}。
     */
    ChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

    /**
     * 返回 {@link MessageSizeEstimator}，用于检测通道中消息的大小。
     */
    MessageSizeEstimator getMessageSizeEstimator();

    /**
     * 设置{@link MessageSizeEstimator}，用于通道检测消息的大小。
     */
    ChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator);

    /**
     * 返回{@link WriteBufferWaterMark}，用于设置写缓冲区的高低水位线。
     */
    WriteBufferWaterMark getWriteBufferWaterMark();

    /**
     * 设置{@link WriteBufferWaterMark}，用于设置写缓冲区的高低水位线。
     */
    ChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark);
}
