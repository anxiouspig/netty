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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.AttributeMap;

import java.net.InetSocketAddress;
import java.net.SocketAddress;


/**
 * 连接到网络套接字或能够进行I/O操作的组件，如读、写、连接和绑定。
 * <p>
 * 通道为用户提供:
 * <ul>
 * <li>通道的当前状态(例如，它是打开的吗?)它是连接吗?)</li>
 * <li>通道的{@linkplain ChannelConfig 配置参数}(例如接收缓冲区大小)，</li>
 * <li>通道支持的I/O操作(例如，读、写、连接和绑定)，以及</li>
 * <li>处理与通道相关的所有I/O事件和请求的@{link ChannelPipeline}。</li>
 * </ul>
 *
 * <h3>所有的I/O操作都是异步的。</h3>
 * <p>
 * 所有的I/O操作在Netty是异步的。这意味着任何I/O调用将立即返回，而不能保证所请求的I/O操作已经在调用结束时完成。
 * 相反，您将返回一个{@link ChannelFuture}实例，当请求的I/O操作成功、失败或取消时，该实例将通知您。
 *
 * <h3>通道是分层的</h3>
 * <p>
 * 一个{@link Channel}可以有一个{@linkplain #parent() parent}，这取决于它是如何创建的。
 * 例如，被{@link ServerSocketChannel}接受的{@link SocketChannel}将在{@link #parent()}上返回{@link ServerSocketChannel}作为它的父节点。
 * <p>
 * 层次结构的语义取决于{@link Channel}所属的传输实现。
 * 例如，你可以编写一个新的{@link Channel}实现，创建共享一个套接字连接的子通道，
 * 就像<a href="http://beepcore.org/">BEEP</a>和<a href="http://en.wikipedia.org/wiki/Secure_Shell">SSH</a>那样。
 *
 * <h3>访问特定传输操作</h3>
 * <p>
 * 有些传输会暴露出传输所特有的附加操作。 将{@link Channel}下传到子类型来调用这些操作。
 * 例如，在旧的I/O数据报传输中，多播数据报是一个很好的选择。
 *
 * <h3>释放资源</h3>
 * <p>
 * 重要的是，一旦你完成了对{@link Channel}的操作，就要调用{@link #close()}或{@link #close(ChannelPromise)}来释放所有资源。
 * 这可以确保所有资源都以正确的方式释放，即文件柄。
 */
public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel> {

    /**
     * 返回这个{@link Channel}的全局唯一标识符。
     */
    ChannelId id();

    /**
     * 返回这个{@link Channel}注册的{@link EventLoop}。
     */
    EventLoop eventLoop();

    /**
     * 返回这个通道的父通道。
     *
     * @return 父通道.
     *         {@code null}如果这个Channel没有父Channel。
     */
    Channel parent();

    /**
     * 返回该通道的配置。
     */
    ChannelConfig config();

    /**
     * 如果{@link Channel}是开放的，并且以后可能会被激活，则返回{@code true}。
     */
    boolean isOpen();

    /**
     * 如果{@link Channel}注册了{@link EventLoop}，返回{@code true}。
     */
    boolean isRegistered();

    /**
     * 如果{@link Channel}是活动的，并且连接了，返回{@code true}。
     */
    boolean isActive();

    /**
     * 返回{@link Channel}的{@link ChannelMetadata}，其中描述了{@link Channel}的性质。
     */
    ChannelMetadata metadata();

    /**
     * 返回此通道绑定的本地地址。 返回的{@link SocketAddress}应该被向下转换为更具体的类型，如
     * {@link InetSocketAddress}，以获取详细信息。
     *
     * @return 这个通道的本地地址。
     *         {@code null}如果这个通道没有绑定。
     */
    SocketAddress localAddress();

    /**
     * 返回这个通道连接的远程地址。 返回的{@link SocketAddress}应该被向下转换为更具体的类型，如
     * {@link InetSocketAddress}，以获取详细信息。
     *
     * @return 这个通道的远程地址。
     *         {@code null}如果这个通道没有连接，但它可以接收来自任意远程地址的消息（例如
     *         {@link DatagramChannel}，使用
     *         {@link DatagramPacket#recipient()}来确定接收消息的来源，因为这个方法将返回
     *         {@code null}。
     */
    SocketAddress remoteAddress();

    /**
     * 返回{@link ChannelFuture}，当这个通道被关闭时，将通知它。 此方法总是返回相同的未来实例。
     */
    ChannelFuture closeFuture();

    /**
     * 如果且仅如果I/O线程将立即执行请求的写操作，则返回{@code true}。
     * 当本方法返回{@code false}时，任何写入请求都会被排队，直到I/O线程准备好处理排队的写入请求。
     */
    boolean isWritable();

    /**
     * 获取可以写入的字节数，直到{@link #isWritable()}返回{@code false}。这个数量永远是非负数。
     * 如果{@link #isWritable()}为{@code false}，则为0。
     */
    long bytesBeforeUnwritable();

    /**
     * 获取从底层缓冲区中排出的字节数，直到
     * {@link #isWritable()}返回{@code true}。
     * 这个数量总是非负数。如果{@link #isWritable()}为{@code true}，则为0。
     */
    long bytesBeforeWritable();

    /**
     * 返回一个提供不安全操作的<em>内部使用</em>对象。
     */
    Unsafe unsafe();

    /**
     * 返回分配的{@link ChannelPipeline}。
     */
    ChannelPipeline pipeline();

    /**
     * 返回分配的{@link ByteBufAllocator}，它将被用来分配{@link ByteBuf}s。
     */
    ByteBufAllocator alloc();

    @Override
    Channel read();

    @Override
    Channel flush();

    /**
     * <em>不安全的<em></em>操作，<em>绝对不能</em>从用户代码中调用。
     * 这些方法只是为了实现实际的传输而提供的，必须从一个 I/O 线程中调用，除了
     * <ul>
     *   <li>{@link #localAddress()}</li>
     *   <li>{@link #remoteAddress()}</li>
     *   <li>{@link #closeForcibly()}</li>
     *   <li>{@link #register(EventLoop, ChannelPromise)}</li>
     *   <li>{@link #deregister(ChannelPromise)}</li>
     *   <li>{@link #voidPromise()}</li>
     * </ul>
     */
    interface Unsafe {

        /**
         * 返回被分配的{@link RecvByteBufAllocator.Handle}，它将被用于分配{@link ByteBuf}，当
         */
        RecvByteBufAllocator.Handle recvBufAllocHandle();

        /**
         * 返回本地绑定的{@link SocketAddress}，如果没有，则返回{@code null}。
         */
        SocketAddress localAddress();

        /**
         * 返回远端或远程绑定的{@link SocketAddress}。
         */
        SocketAddress remoteAddress();

        /**
         * 注册{@link ChannelPromise}的{@link ChannelPromise}，注册完成后通知{@link ChannelFuture}。
         */
        void register(EventLoop eventLoop, ChannelPromise promise);

        /**
         * 将{@link SocketAddress}绑定到{@link ChannelPromise}的{@link Channel}上，并在绑定完成后通知它。
         */
        void bind(SocketAddress localAddress, ChannelPromise promise);

        /**
         * 将给定的{@link ChannelFuture}的{@link Channel}与给定的远程{@link SocketAddress}连接。如果需要使用一个特定的本地
         * {@link SocketAddress}，则需要给定它作为参数，否则只需给定{@link ChannelFuture}和远程{@link SocketAddress}。否则只需将{@code null}传递给它。
         *
         * 连接操作完成后，{@link ChannelPromise}将得到通知。
         */
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

        /**
         * 断开{@link ChannelFuture}的{@link ChannelFuture}，操作完成后通知{@link ChannelPromise}。
         */
        void disconnect(ChannelPromise promise);

        /**
         * 关闭{@link ChannelPromise}的{@link ChannelPromise}，操作完成后通知{@link ChannelPromise}。
         */
        void close(ChannelPromise promise);

        /**
         * 在不触发任何事件的情况下立即关闭{@link Channel}。 可能只在注册失败时有用。
         */
        void closeForcibly();

        /**
         * 将{@link ChannelPromise}的{@link ChannelPromise}的
         * {@link ChannelPromise}从{@link EventLoop}中取消注册，并通知{@link EventLoop}。
         */
        void deregister(ChannelPromise promise);

        /**
         * 安排一个读取操作，填充{@link ChannelPipeline}中第一个{@link ChannelInboundHandler}的入站缓冲区。
         * 如果已经有一个挂起的读取操作，本方法不做任何操作。
         */
        void beginRead();

        /**
         * 安排写操作。
         */
        void write(Object msg, ChannelPromise promise);

        /**
         * 通过{@link #write(Object, ChannelPromise)}冲出所有预定的写操作。
         */
        void flush();

        /**
         * 返回一个特殊的ChannelPromise，该ChannelPromise可以重复使用，并传递给
         * {@link Unsafe}中的操作。它永远不会被通知成功或出错，因此对于那些使用
         * {@link ChannelPromise}作为参数，但不想被通知的操作来说，它只是一个占位符。
         */
        ChannelPromise voidPromise();

        /**
         * Returns the {@link ChannelOutboundBuffer} for the
         * {@link Channel} where the pending write requests are stored.
         */
        ChannelOutboundBuffer outboundBuffer();
    }
}
