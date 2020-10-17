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
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;
import io.netty.util.concurrent.EventExecutor;

import java.nio.channels.Channels;

/**
 * 使{@link ChannelHandler}能够与它的{@link ChannelPipeline}和其他处理程序进行交互。其中，处理程序可以通知
 * {@link ChannelPipeline}中的下一个{@link ChannelHandler}，也可以动态地修改它所属的{@link ChannelPipeline}。
 *
 * <h3>通知</h3>
 *
 * 你可以通过调用这里提供的各种方法之一来通知同一个{@link ChannelPipeline}中最近的处理程序。
 *
 * 请参考{@link ChannelPipeline}来了解事件的流程。
 *
 * <h3>修改管道</h3>
 *
 * 你可以通过调用{@link #pipeline()}来获取你的处理程序所属的{@link ChannelPipeline}。
 * 一个非平凡的应用程序可以插入、删除或添加。
 *
 * <h3>检索供以后使用</h3>
 *
 * 你可以保留{@link ChannelHandlerContext}供以后使用，比如在处理方法之外触发一个事件，甚至从不同的线程触发。
 * <pre>
 * public class MyHandler extends {@link ChannelDuplexHandler} {
 *
 *     <b>private {@link ChannelHandlerContext} ctx;</b>
 *
 *     public void beforeAdd({@link ChannelHandlerContext} ctx) {
 *         <b>this.ctx = ctx;</b>
 *     }
 *
 *     public void login(String username, password) {
 *         ctx.write(new LoginMessage(username, password));
 *     }
 *     ...
 * }
 * </pre>
 *
 * <h3>存储有状态信息</h3>
 *
 * {@link #attr(AttributeKey)}允许你存储和访问与{@link ChannelHandler}相关的状态信息。/ {@link Channel}及其上下文。
 * 请参考{@link ChannelHandler}来了解各种推荐的管理有状态信息的方法。
 *
 * <h3>一个处理程序可以有多个{@link ChannelHandlerContext}。</h3>
 *
 * 请注意，一个{@link ChannelHandler}实例可以被添加到多个{@link ChannelPipeline}中。
 * 这意味着一个{@link ChannelHandler}实例可以有多个{@link ChannelHandlerContext}，
 * 因此如果一个实例被添加到一个或多个{@link ChannelPipeline}中，它可以被不同的{@link ChannelHandlerContext}调用。
 * 同时要注意的是，一个{@link ChannelHandler}如果应该被添加到多个{@link ChannelPipeline}中，
 * 应该标记为{@link io.netty.channel.ChannelHandler.Sharable}。
 *
 * <h3>其他值得阅读的资源</h3>
 * <p>
 * 请参考{@link ChannelHandler}，和{@link ChannelPipeline}来了解更多关于入站操作和出站操作的信息，
 * 它们有什么根本性的区别，它们在管道中是如何流动的，以及如何在你的应用程序中处理操作。
 */
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {

    /**
     * 返回绑定在{@link ChannelHandlerContext}上的{@link Channel}。
     */
    Channel channel();

    /**
     * 返回用于执行任意任务的{@link EventExecutor}。
     */
    EventExecutor executor();

    /**
     * {@link ChannelHandlerContext}.这个名字是在{@link ChannelPipeline}中添加{@link ChannelHandler}时使用的。
     * 这个名字也可以用来从{@link ChannelPipeline}访问注册的{@link ChannelHandler}。
     */
    String name();

    /**
     * 绑定这个{@link ChannelHandlerContext}的{@link ChannelHandler}。
     */
    ChannelHandler handler();

    /**
     * 如果属于这个上下文的{@link ChannelHandler}被从{@link ChannelPipeline}中移除，返回{@code true}。
     * 请注意，这个方法只能在{@link EventLoop}中调用。
     */
    boolean isRemoved();

    @Override
    ChannelHandlerContext fireChannelRegistered();

    @Override
    ChannelHandlerContext fireChannelUnregistered();

    @Override
    ChannelHandlerContext fireChannelActive();

    @Override
    ChannelHandlerContext fireChannelInactive();

    @Override
    ChannelHandlerContext fireExceptionCaught(Throwable cause);

    @Override
    ChannelHandlerContext fireUserEventTriggered(Object evt);

    @Override
    ChannelHandlerContext fireChannelRead(Object msg);

    @Override
    ChannelHandlerContext fireChannelReadComplete();

    @Override
    ChannelHandlerContext fireChannelWritabilityChanged();

    @Override
    ChannelHandlerContext read();

    @Override
    ChannelHandlerContext flush();

    /**
     * 返回被分配的{@link ChannelPipeline}。
     */
    ChannelPipeline pipeline();

    /**
     * 返回分配的{@link ByteBufAllocator}，它将被用来分配{@link ByteBuf}s。
     */
    ByteBufAllocator alloc();

    /**
     * 使用 {@link Channel#attr(AttributeKey)}。
     */
    @Deprecated
    @Override
    <T> Attribute<T> attr(AttributeKey<T> key);

    /**
     * @deprecated 使用 {@link Channel#hasAttr(AttributeKey)}。
     */
    @Deprecated
    @Override
    <T> boolean hasAttr(AttributeKey<T> key);
}
