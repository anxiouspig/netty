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

import io.netty.channel.ChannelHandlerMask.Skip;

/**
 * {@link ChannelInboundHandler}实现的抽象基类，提供它们所有方法的实现。
 *
 * <p>
 * 这个实现只是将操作转发给{@link ChannelPipeline}中的下一个{@link ChannelHandler}。子类可以通过重写方法实现来改变这一点。
 * </p>
 * <p>
 * 注意，在{@link #channelRead(ChannelHandlerContext, Object)}方法自动返回后，消息不会被释放。
 * 如果您正在寻找一个自动释放接收到的消息的{@link ChannelInboundHandler}实现，请参见{@link SimpleChannelInboundHandler}。
 * </p>
 */
public class ChannelInboundHandlerAdapter extends ChannelHandlerAdapter implements ChannelInboundHandler {

    /**
     * 调用{@link ChannelHandlerContext#fireChannelRegistered()}以转发到
     * {@link ChannelInboundHandler}中{@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}。
     *
     * 子类可以重写此方法来改变行为。
     */
    @Skip
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelRegistered();
    }

    /**
     * 调用{@link ChannelHandlerContext#fireChannelUnregistered()}以转发到
     * {@link ChannelInboundHandler}中{@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}。
     *
     * 子类可以重写此方法来改变行为。
     */
    @Skip
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    /**
     * 调用{@link ChannelHandlerContext#fireChannelActive()}以转发到
     * {@link ChannelInboundHandler}中{@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}。
     *
     * 子类可以重写此方法来改变行为。
     */
    @Skip
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
    }

    /**
     * 调用{@link ChannelHandlerContext#fireChannelInactive()}以转发到
     * {@link ChannelInboundHandler}中{@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}。
     *
     * 子类可以重写此方法来改变行为。
     */
    @Skip
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelInactive();
    }

    /**
     * 调用{@link ChannelHandlerContext#fireChannelRead(Object)}转发到{@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}。
     *
     * 子类可以重写这个方法来改变行为。
     */
    @Skip
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.fireChannelRead(msg);
    }

    /**
     * 调用{@link ChannelHandlerContext#fireChannelReadComplete()}转发到
     * {@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}。
     *
     * 子类可以重写这个方法来改变行为。
     */
    @Skip
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadComplete();
    }

    /**
     * 调用{@link ChannelHandlerContext#fireUserEventTriggered(Object)}转发到
     * {@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}。
     *
     * 子类可以重写这个方法来改变行为。
     */
    @Skip
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    /**
     * 调用{@link ChannelHandlerContext#fireChannelWritabilityChanged()}
     * 来转发到{@link ChannelPipeline}中的下一个{@link ChannelInboundHandler}。
     *
     * 子类可以重写这个方法来改变行为。
     */
    @Skip
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelWritabilityChanged();
    }

    /**
     * 调用{@link ChannelHandlerContext#fireExceptionCaught(Throwable)}
     * 转发到{@link ChannelPipeline}中的下一个{@link ChannelHandler}。
     *
     * 子类可以重写这个方法来改变行为。
     */
    @Skip
    @Override
    @SuppressWarnings("deprecation")
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        ctx.fireExceptionCaught(cause);
    }
}
