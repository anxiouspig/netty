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

/**
 * {@link ChannelHandler}，为状态更改添加回调。这允许用户轻松地挂钩到状态更改。
 */
public interface ChannelInboundHandler extends ChannelHandler {

    /**
     * 使用其{@link EventLoop}注册了{@link ChannelHandlerContext}的{@link Channel}。
     */
    void channelRegistered(ChannelHandlerContext ctx) throws Exception;

    /**
     * {@link ChannelHandlerContext}的{@link Channel}从其{@link EventLoop}中被取消注册。
     */
    void channelUnregistered(ChannelHandlerContext ctx) throws Exception;

    /**
     * {@link ChannelHandlerContext}的{@link Channel}现在是活动的
     */
    void channelActive(ChannelHandlerContext ctx) throws Exception;

    /**
     * 已注册的{@link ChannelHandlerContext}的{@link Channel}现在处于非活动状态，并已到达生命周期的末尾。
     */
    void channelInactive(ChannelHandlerContext ctx) throws Exception;

    /**
     * Invoked when the current {@link Channel} has read a message from the peer.
     */
    void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception;

    /**
     * 当当前读取操作读取的最后一条消息被{@link #channelRead(ChannelHandlerContext, Object)}使用时调用。
     * 如果{@link ChannelOption#AUTO_READ}是关闭的，那么在调用{@link ChannelHandlerContext#read()}之前，
     * 不会再尝试从当前的{@link Channel}读取入站数据。
     */
    void channelReadComplete(ChannelHandlerContext ctx) throws Exception;

    /**
     * 如果用户事件被触发，则调用。
     */
    void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception;

    /**
     * 当{@link Channel}的可写状态更改时调用。您可以使用{@link Channel#isWritable()}检查状态。
     */
    void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception;

    /**
     * 在抛出{@link Throwable}时调用。
     */
    @Override
    @SuppressWarnings("deprecation")
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;
}
