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

public interface ChannelInboundInvoker {

    /**
     * 一个{@link Channel}被注册到它的{@link EventLoop}。
     *
     * 这将会导致调用ChannelInboundHandler#channelRegistered(ChannelHandlerContext)}方法的下一个
     * {@link ChannelPipeline}中的{@link ChannelInboundHandler}。
     */
    ChannelInboundInvoker fireChannelRegistered();

    /**
     * 一个{@link Channel}从它的{@link EventLoop}中被取消注册。
     *
     * 这将会导致调用ChannelUnregistered(ChannelHandlerContext)}方法的下一个
     * {@link ChannelPipeline}中包含的{@link ChannelInboundHandler}。
     */
    ChannelInboundInvoker fireChannelUnregistered();

    /**
     * 一个{@link Channel}现在是活动的，这意味着它是连接的。
     *
     * 这将会导致调用ChannelInboundHandler#channelActive(ChannelHandlerContext)}方法的下一个
     * {@link ChannelPipeline}中的{@link ChannelInboundHandler}。
     */
    ChannelInboundInvoker fireChannelActive();

    /**
     * 一个{@link Channel}现在是不活跃的，这意味着它是关闭的。
     *
     * 这将会导致调用{@link ChannelInboundHandler#channelInactive(ChannelHandlerContext)}方法的下一个
     * {@link ChannelPipeline}中的{@link ChannelInboundHandler}。
     */
    ChannelInboundInvoker fireChannelInactive();

    /**
     * 一个{@link Channel}在其一次入站操作中收到了一个{@link Throwable}。
     *
     * 这将导致调用ChannelInboundHandler#exceptionCaught(ChannelHandlerContext, Throwable)}方法的下一个
     * {@link ChannelPipeline}中包含的{@link ChannelInboundHandler}。
     */
    ChannelInboundInvoker fireExceptionCaught(Throwable cause);

    /**
     * 一个{@link Channel}收到了一个用户定义的事件。
     *
     * 这将导致在{@link Channel}的{@link ChannelPipeline}中包含的下一个{@link ChannelInboundHandler}中调用
     * {@link ChannelInboundHandler#userEventTriggered(ChannelHandlerContext, Object)}方法。
     */
    ChannelInboundInvoker fireUserEventTriggered(Object event);

    /**
     * 一个{@link Channel}收到了一条消息。
     *
     * 这将导致在{@link Channel}的{@link ChannelPipeline}中包含的下一个{@link ChannelInboundHandler}中调用
     * {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object)}方法。
     */
    ChannelInboundInvoker fireChannelRead(Object msg);

    /**
     * 触发一个{@link ChannelInboundHandler#channelReadComplete(ChannelHandlerContext)}事件到
     * {@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}。
     */
    ChannelInboundInvoker fireChannelReadComplete();

    /**
     * 触发{@link ChannelInboundHandler#channelWritabilityChanged(ChannelHandlerContext)}。
     */
    ChannelInboundInvoker fireChannelWritabilityChanged();
}
