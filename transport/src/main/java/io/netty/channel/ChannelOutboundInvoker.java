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

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FutureListener;

import java.net.ConnectException;
import java.net.SocketAddress;

public interface ChannelOutboundInvoker {

    /**
     * 要求绑定到给定的{@link SocketAddress}上，一旦操作完成，通知{@link ChannelFuture}，或者是因为操作成功，或者是因为出错。
     * <p>
     * 这将导致调用了{@link ChannelOutboundHandler#bind(ChannelHandlerContext, SocketAddress, ChannelPromise)}
     * 方法的下一个{@link ChannelPipeline}中的{@link ChannelOutboundHandler}。
     */
    ChannelFuture bind(SocketAddress localAddress);

    /**
     * 请求连接到给定的{@link SocketAddress}，并在操作完成后通知{@link ChannelFuture}，或者是因为操作成功，或者是因为错误。
     * <p>
     * 如果因为连接超时而导致连接失败，{@link ChannelFuture}将得到一个{@link ConnectTimeoutException}。
     * 如果因为连接拒绝而失败，则会使用{@link ConnectException}。
     * <p>
     * 这将导致在{@link Channel}的{@link ChannelPipeline}中包含的下一个{@link ChannelOutboundHandler}
     * 的{@link ChannelPipeline}中调用
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, ChannelPromise)}方法。
     */
    ChannelFuture connect(SocketAddress remoteAddress);

    /**
     * 请求连接到给定的{@link SocketAddress}，同时绑定到localAddress，并在操作完成后通知{@link ChannelFuture}，或者是因为操作成功，
     * 或者是因为错误。
     * <p>
     * 这将导致在{@link Channel}的{@link ChannelPipeline}中包含的下一个{@link ChannelOutboundHandler}的
     * {@link ChannelPipeline}中调用
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, ChannelPromise)}方法。
     */
    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress);

    /**
     * 要求与远程对等体断开连接，并在操作完成后通知{@link ChannelFuture}，或者是因为操作成功，或者是因为错误。
     * <p>
     * 这将会导致调用ChannelOutboundHandler#disconnect(ChannelHandlerContext, ChannelPromise)}
     * 方法的下一个{@link ChannelPipeline}中包含的{@link ChannelOutboundHandler}的方法。
     */
    ChannelFuture disconnect();

    /**
     * 要求关闭{@link Channel}，并在操作完成后通知{@link ChannelFuture}，原因是操作成功或出错。
     *
     * 关闭后就不能再使用了。
     * <p>
     * 这将导致在{@link Channel}的{@link ChannelPipeline}中包含的下一个{@link ChannelOutboundHandler}中调用
     * {@link ChannelOutboundHandler#close(ChannelHandlerContext, ChannelPromise)}方法。
     */
    ChannelFuture close();

    /**
     * 要求从之前分配的{@link EventExecutor}中注销注册，一旦操作完成，就通知{@link ChannelFuture}，或者是因为操作成功，或者是因为
     *
     * <p>
     * 这将导致在{@link Channel}的{@link ChannelPipeline}中包含的下一个{@link ChannelOutboundHandler}中调用
     * {@link ChannelOutboundHandler#deregister(ChannelHandlerContext, ChannelPromise)}方法。
     *
     */
    ChannelFuture deregister();

    /**
     * 要求绑定到给定的{@link SocketAddress}上，一旦操作完成，通知{@link ChannelFuture}，或者是因为操作成功，或者是因为出错。
     *
     * 给定的{@link ChannelPromise}将被通知。
     * <p>
     * 这将导致在{@link Channel}的{@link ChannelPipeline}中包含的下一个{@link ChannelOutboundHandler}中调用
     * {@link ChannelOutboundHandler#bind(ChannelHandlerContext, SocketAddress, ChannelPromise)}方法。
     */
    ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise);

    /**
     * 请求连接到给定的{@link SocketAddress}，并在操作完成后通知{@link ChannelFuture}，或者是因为操作成功，或者是因为错误。
     *
     * 给定的{@link ChannelFuture}将被通知。
     *
     * <p>
     * 如果因为连接超时而导致连接失败，{@link ChannelFuture}将得到一个{@link ConnectTimeoutException}。
     * 如果因为连接拒绝而失败，则会使用{@link ConnectException}。
     * <p>
     * 这将导致在{@link Channel}的{@link ChannelPipeline}中包含的下一个
     * {@link ChannelOutboundHandler}的{@link ChannelPipeline}中调用
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, ChannelPromise)}方法。
     */
    ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise);

    /**
     * 请求连接到给定的{@link SocketAddress}，同时绑定到localAddress，并在操作完成后通知{@link ChannelFuture}，
     * 或者是因为操作成功，或者是因为错误。
     *
     * 给定的{@link ChannelPromise}将被通知并返回。
     * <p>
     * 这将导致在{@link Channel}的{@link ChannelPipeline}中包含的下一个{@link ChannelOutboundHandler}
     * 的{@link ChannelPipeline}中调用
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, ChannelPromise)}方法。
     */
    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

    /**
     * 要求与远程对等体断开连接，并在操作完成后通知{@link ChannelFuture}，或者是因为操作成功，或者是因为错误。
     *
     * 给定的{@link ChannelPromise}将被通知。
     * <p>
     * 这将导致在{@link Channel}的{@link ChannelPipeline}中包含的下一个{@link ChannelOutboundHandler}中调用
     * {@link ChannelOutboundHandler#disconnect(ChannelHandlerContext, ChannelPromise)}方法。
     */
    ChannelFuture disconnect(ChannelPromise promise);

    /**
     * 要求关闭{@link Channel}，并在操作完成后通知{@link ChannelFuture}，原因是操作成功或出错。
     *
     * 关闭后就不能再重复使用了。给定的{@link ChannelPromise}将被通知。
     * <p>
     * 这将导致在{@link Channel}的{@link ChannelPipeline}中包含的下一个{@link ChannelOutboundHandler}中调用
     * {@link ChannelOutboundHandler#close(ChannelHandlerContext, ChannelPromise)}方法。
     */
    ChannelFuture close(ChannelPromise promise);

    /**
     * 要求从之前分配的{@link EventExecutor}中注销注册，一旦操作完成，
     * 就通知{@link ChannelFuture}，或者是因为操作成功，或者是因为错误。
     *
     * 给定的{@link ChannelPromise}将被通知。
     * <p>
     * 这将导致在{@link Channel}的{@link ChannelPipeline}中包含的下一个{@link ChannelOutboundHandler}
     * 中调用{@link ChannelOutboundHandler#deregister(ChannelHandlerContext, ChannelPromise)}方法。
     */
    ChannelFuture deregister(ChannelPromise promise);

    /**
     * 请求从{@link Channel}读取数据到第一个入站缓冲区，如果数据被读取，则触发
     * {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object)}事件，并触发
     * {@link ChannelInboundHandler#channelReadComplete(ChannelHandlerContext) channelReadComplete}事件，
     * 以便处理程序可以决定继续读取。 如果已经有一个待定的读取操作，本方法不做任何操作。
     * <p>
     * 这将导致{@link Channel}中包含的下一个
     * {@link ChannelOutboundHandler#read(ChannelHandlerContext)}方法被调用。
     */
    ChannelOutboundInvoker read();

    /**
     * 请求通过这个{@link ChannelHandlerContext}通过{@link ChannelPipeline}
     * 写入一条消息。这个方法不会请求实际的刷新，所以一旦要请求将所有待处理的数据刷新到实际传输中，一定要调用{@link #flush()}。
     */
    ChannelFuture write(Object msg);

    /**
     * 请求通过这个{@link ChannelHandlerContext}通过{@link ChannelPipeline}写入一条消息。
     * 这个方法不会请求实际的刷新，所以一旦要请求将所有待处理的数据刷新到实际传输中，一定要调用{@link #flush()}。
     */
    ChannelFuture write(Object msg, ChannelPromise promise);

    /**
     * 要求通过这个ChannelOutboundInvoker冲掉所有待处理的消息。
     */
    ChannelOutboundInvoker flush();

    /**
     * 调用{@link #write(Object, ChannelPromise)}和{@link #flush()}的快捷方式。
     */
    ChannelFuture writeAndFlush(Object msg, ChannelPromise promise);

    /**
     * 调用{@link #write(Object)}和{@link #flush()}的快捷方式。
     */
    ChannelFuture writeAndFlush(Object msg);

    /**
     * 返回一个新的{@link ChannelPromise}。
     */
    ChannelPromise newPromise();

    /**
     * 返回一个新的{@link ChannelProgressivePromise}。
     */
    ChannelProgressivePromise newProgressivePromise();

    /**
     * 创建一个新的{@link ChannelFuture}，它已经被标记为成功。所以
     * {@link ChannelFuture#isSuccess()}将返回{@code true}。所有添加到它上面的
     * {@link FutureListener}都会被直接通知。同时每次调用阻塞方法都会只返回不阻塞。
     */
    ChannelFuture newSucceededFuture();

    /**
     * 创建一个新的{@link ChannelFuture}，它已经被标记为失败。所以{@link ChannelFuture#isSuccess()}将返回{@code false}。
     * 所有添加到它上面的{@link FutureListener}都会被直接通知。同时每次调用阻塞方法都会只返回不阻塞。
     */
    ChannelFuture newFailedFuture(Throwable cause);

    /**
     * 返回一个特殊的ChannelPromise，这个ChannelPromise可以被重复使用于不同的操作。
     * <p>
     * 它只支持用于{@link ChannelOutboundInvoker#write(Object, ChannelPromise)}。
     * </p>
     * <p>
     * 请注意，返回的{@link ChannelPromise}将不支持大多数操作，
     * 只有当你想为每一次写操作节省一个对象分配时才应该使用。你将无法检测操作是否完成，只能检测操作是否失败，因为实现将调用
     * </p>
     * <strong>要知道这是一个专家级的功能，使用时要谨慎!</strong>
     */
    ChannelPromise voidPromise();
}
