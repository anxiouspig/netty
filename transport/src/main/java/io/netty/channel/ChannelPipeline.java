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
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;


/**
 * 一个{@link ChannelHandler}的列表，用于处理或拦截{@link Channel}的入站事件和出站操作。
 * {@link ChannelPipeline}实现了<a href="http://www.oracle.com/technetwork/java/interceptingfilter-142169.html">拦截过滤器</a>
 * 模式的高级形式，使用户可以完全控制事件的处理方式以及管道中的{@link ChannelHandler}之间的交互方式。
 *
 * <h3>建立一个管道</h3>
 *
 * 每个通道都有自己的管道，当有新的通道创建时，它会自动创建。
 *
 * <h3>事件如何在管道中流动</h3>
 *
 * 下图描述了在一个{@link ChannelPipeline}中，I/O事件是如何被{@link ChannelHandler}处理的。一个I/O事件由{@link ChannelInboundHandler}或
 * {@link ChannelOutboundHandler}处理，并通过调用{@link ChannelHandlerContext}中定义的事件传播方法，如
 * {@link ChannelHandlerContext#fireChannelRead(Object)}和{@link ChannelHandlerContext#fireChannelRead(Object)}转发到最近的处理程序。
 *
 * <pre>
 *                                                 I/O Request
 *                                            via {@link Channel} or
 *                                        {@link ChannelHandlerContext}
 *                                                      |
 *  +---------------------------------------------------+---------------+
 *  |                           ChannelPipeline         |               |
 *  |                                                  \|/              |
 *  |    +---------------------+            +-----------+----------+    |
 *  |    | Inbound Handler  N  |            | Outbound Handler  1  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler N-1 |            | Outbound Handler  2  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  .               |
 *  |               .                                   .               |
 *  | ChannelHandlerContext.fireIN_EVT() ChannelHandlerContext.OUT_EVT()|
 *  |        [ method call]                       [method call]         |
 *  |               .                                   .               |
 *  |               .                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  2  |            | Outbound Handler M-1 |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  1  |            | Outbound Handler  M  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  +---------------+-----------------------------------+---------------+
 *                  |                                  \|/
 *  +---------------+-----------------------------------+---------------+
 *  |               |                                   |               |
 *  |       [ Socket.read() ]                    [ Socket.write() ]     |
 *  |                                                                   |
 *  |  Netty Internal I/O Threads (Transport Implementation)            |
 *  +-------------------------------------------------------------------+
 * </pre>
 * 入站事件由入站处理程序自下而上的方向处理，如图左侧所示。 入站处理程序通常处理图中底部的I/O线程产生的入站数据。
 * 入站数据通常是通过实际的输入操作，如{@link SocketChannel#read(ByteBuffer)}从远程对等体读取。
 * 如果一个入站事件超出了顶部的入站处理程序，就会被默默地丢弃，如果需要你关注，就会被记录下来。
 * <p>
 * 如图右侧所示，出站事件由出站处理程序按自上而下的方向处理。 出站处理程序通常会产生或转换出站流量，如写请求。
 * 如果一个出站事件超出了底层的出站处理程序，则由与{@link Channel}相关联的I/O线程处理。
 * I/O线程通常执行实际的输出操作，如{@link SocketChannel#write(ByteBuffer)}。
 * <p>
 * 例如，让我们假设我们创建了以下管道。
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * p.addLast("1", new InboundHandlerA());
 * p.addLast("2", new InboundHandlerB());
 * p.addLast("3", new OutboundHandlerA());
 * p.addLast("4", new OutboundHandlerB());
 * p.addLast("5", new InboundOutboundHandlerX());
 * </pre>
 * 在上面的例子中，名字以{@code Inbound}开头的类意味着它是一个入站处理程序。名字以{@code Outbound}开头的类意味着它是一个出站处理程序。
 * <p>
 * 在给定的示例配置中，当一个事件传入时，处理程序的评估顺序是1、2、3、4、5。在这个原则之上，{@link ChannelPipeline}跳过了某些处理程序的评估以缩短堆栈深度。
 * <ul>
 * <li>3和4没有实现{@link ChannelInboundHandler}，因此入站事件的实际评估顺序将是。1、2、5。</li>
 * <li>1和2没有实现{@link ChannelOutboundHandler}，因此出站事件的实际评估顺序将是。5、4、3。</li>
 * <li>如果5同时实现了{@link ChannelInboundHandler}和{@link ChannelOutboundHandler}，那么一个入站事件和一个出站事件的评估顺序可以分别是125和543。</li>
 * </ul>
 *
 * <h3>将事件转发到下一个处理程序</h3>
 *
 * 如图所示，处理程序必须调用{@link ChannelHandlerContext}中的事件传播方法来将事件转发到下一个处理程序。
 * 这些方法包括： {@link ChannelHandlerContext}转发事件到下一个处理程序。
 * <ul>
 * <li>入站事件传播方法:
 *     <ul>
 *     <li>{@link ChannelHandlerContext#fireChannelRegistered()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelActive()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelRead(Object)}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelReadComplete()}</li>
 *     <li>{@link ChannelHandlerContext#fireExceptionCaught(Throwable)}</li>
 *     <li>{@link ChannelHandlerContext#fireUserEventTriggered(Object)}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelWritabilityChanged()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelInactive()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelUnregistered()}</li>
 *     </ul>
 * </li>
 * <li>Outbound 事件传播方法:
 *     <ul>
 *     <li>{@link ChannelHandlerContext#bind(SocketAddress, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#connect(SocketAddress, SocketAddress, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#write(Object, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#flush()}</li>
 *     <li>{@link ChannelHandlerContext#read()}</li>
 *     <li>{@link ChannelHandlerContext#disconnect(ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#close(ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#deregister(ChannelPromise)}</li>
 *     </ul>
 * </li>
 * </ul>
 *
 * 而下面的例子显示了事件传播通常是如何进行的:
 *
 * <pre>
 * public class MyInboundHandler extends {@link ChannelInboundHandlerAdapter} {
 *     {@code @Override}
 *     public void channelActive({@link ChannelHandlerContext} ctx) {
 *         System.out.println("Connected!");
 *         ctx.fireChannelActive();
 *     }
 * }
 *
 * public class MyOutboundHandler extends {@link ChannelOutboundHandlerAdapter} {
 *     {@code @Override}
 *     public void close({@link ChannelHandlerContext} ctx, {@link ChannelPromise} promise) {
 *         System.out.println("Closing ..");
 *         ctx.close(promise);
 *     }
 * }
 * </pre>
 *
 * <h3>建立一个管道</h3>
 * <p>
 * 用户应该在管道中拥有一个或多个{@link ChannelHandler}来接收I/O事件（如读）和请求I/O操作（如写和关闭）。
 * 例如，一个典型的服务器将在每个通道的管道中拥有以下处理程序，但你的里程可能会根据协议和业务逻辑的复杂性和特性而有所不同。
 *
 * <ol>
 * <li>协议解码器--将二进制数据(如{@link ByteBuf})翻译成Java对象。</li>
 * <li>协议编码器--将一个Java对象翻译成二进制数据。</li>
 * <li>业务逻辑处理程序 - 执行实际的业务逻辑（如数据库访问）。</li>
 * </ol>
 *
 * 并且可以用下面的例子来表示。
 *
 * <pre>
 * static final {@link EventExecutorGroup} group = new {@link DefaultEventExecutorGroup}(16);
 * ...
 *
 * {@link ChannelPipeline} pipeline = ch.pipeline();
 *
 * pipeline.addLast("decoder", new MyProtocolDecoder());
 * pipeline.addLast("encoder", new MyProtocolEncoder());
 *
 * 告诉流水线在与I/O线程不同的线程中运行MyBusinessLogicHandler的事件处理方法，这样I/O线程就不会被耗时的任务阻塞。
 * 如果你的业务逻辑是完全异步的，或者很快就完成了，你就不需要指定一个组。
 * pipeline.addLast(group, "handler", new MyBusinessLogicHandler());
 * </pre>
 *
 * 请注意，虽然使用{@link DefaultEventLoopGroup}会从{@link EventLoop}中卸载操作，但它仍然会按照
 * {@link ChannelHandlerContext}以串行方式处理任务，因此要保证排序。由于排序，它仍然可能成为一个瓶颈。
 * 如果排序不是你的用例的要求，你可能需要考虑使用{@link UnorderedThreadPoolEventExecutor}来最大化任务执行的并行性。
 *
 * <h3>线程安全</h3>
 * <p>
 * 因为{@link ChannelPipeline}是线程安全的，所以可以随时添加或删除{@link ChannelHandler}。
 * 例如，你可以在即将交换敏感信息时插入一个加密处理程序，并在交换后将其删除。
 */
public interface ChannelPipeline
        extends ChannelInboundInvoker, ChannelOutboundInvoker, Iterable<Entry<String, ChannelHandler>> {

    /**
     * 在这个管道的第一个位置插入一个{@link ChannelHandler}。
     *
     * @param name     要先插入的处理程序的名称
     * @param handler  首先插入的处理程序
     *
     * @throws IllegalArgumentException
     *         如果已经有一个同名的条目在处理中
     * @throws NullPointerException
     *         如果指定的处理程序是{@code null}。
     */
    ChannelPipeline addFirst(String name, ChannelHandler handler);

    /**
     * 在这个管道的第一个位置插入一个{@link ChannelHandler}。
     *
     * @param group    {@link EventExecutorGroup}将被用来执行{@link ChannelHandler}方法。
     * @param name     要先插入的处理程序的名称
     * @param handler  首先插入的处理程序
     *
     * @throws IllegalArgumentException
     *         如果已经有一个同名的条目在处理中
     * @throws NullPointerException
     *         如果指定的处理程序是{@code null}。
     */
    ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler);

    /**
     * 在这个管道的最后一个位置添加一个{@link ChannelHandler}。
     *
     * @param name     要附加的处理程序的名称
     * @param handler  要附加的处理程序
     *
     * @throws IllegalArgumentException
     *         如果已经有一个同名的条目在处理中
     * @throws NullPointerException
     *         如果指定的处理程序是{@code null}。
     */
    ChannelPipeline addLast(String name, ChannelHandler handler);

    /**
     * 在这个管道的最后一个位置添加一个{@link ChannelHandler}。
     *
     * @param group    {@link EventExecutorGroup}将被用来执行{@link ChannelHandler}方法。
     * @param name     要附加的处理程序的名称
     * @param handler  要附加的处理程序
     *
     * @throws IllegalArgumentException
     *         如果已经有一个同名的条目在处理中
     * @throws NullPointerException
     *         如果指定的处理程序是{@code null}。
     */
    ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler);

    /**
     * 在这个管道的现有处理程序之前插入一个{@link ChannelHandler}。
     *
     * @param baseName  现有处理程序的名称
     * @param name      前插入的处理程序的名称
     * @param handler   前要插入的处理程序
     *
     * @throws NoSuchElementException
     *         如果没有指定的{@code baseName}的条目，就会出现这样的情况
     * @throws IllegalArgumentException
     *         如果已经有一个同名的条目在处理中
     * @throws NullPointerException
     *         如果指定的处理程序是{@code null}。
     */
    ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler);

    /**
     * 在这个管道的现有处理程序之前插入一个{@link ChannelHandler}。
     *
     * @param group     {@link EventExecutorGroup}将被用来执行{@link ChannelHandler}方法。
     * @param baseName  现有处理程序的名称
     * @param name      前插入的处理程序的名称
     * @param handler   前要插入的处理程序
     *
     * @throws NoSuchElementException
     *         如果没有指定的{@code baseName}的条目，就会出现这样的情况
     * @throws IllegalArgumentException
     *         如果已经有一个同名的条目在处理中
     * @throws NullPointerException
     *         如果指定的baseName或handler是{@code null}。
     */
    ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    /**
     * 在这个管道的现有处理程序后插入一个{@link ChannelHandler}。
     *
     * @param baseName  现有处理程序的名称
     * @param name      后面要插入的处理程序的名称
     * @param handler   后插入的处理程序
     *
     * @throws NoSuchElementException
     *         如果没有指定的{@code baseName}的条目，就会出现这样的情况
     * @throws IllegalArgumentException
     *         如果已经有一个同名的条目在处理中
     * @throws NullPointerException
     *         如果指定的baseName或handler是{@code null}。
     */
    ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler);

    /**
     * 在这个管道的现有处理程序后插入一个{@link ChannelHandler}。
     *
     * @param group     {@link EventExecutorGroup}将被用来执行{@link ChannelHandler}方法。
     * @param baseName  现有处理程序的名称
     * @param name      后面要插入的处理程序的名称
     * @param handler   后插入的处理程序
     *
     * @throws NoSuchElementException
     *         如果没有指定的{@code baseName}的条目，就会出现这样的情况
     * @throws IllegalArgumentException
     *         如果已经有一个同名的条目在处理中
     * @throws NullPointerException
     *         如果指定的baseName或handler是{@code null}。
     */
    ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    /**
     * 在这个管道的第一个位置插入{@link ChannelHandler}s。
     *
     * @param handlers  the handlers to insert first
     *
     */
    ChannelPipeline addFirst(ChannelHandler... handlers);

    /**
     * 在这个管道的第一个位置插入{@link ChannelHandler}s。
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}s
     *                  methods.
     * @param handlers  the handlers to insert first
     *
     */
    ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers);

    /**
     * 在这个管道的最后一个位置插入{@link ChannelHandler}s。
     *
     * @param handlers  the handlers to insert last
     *
     */
    ChannelPipeline addLast(ChannelHandler... handlers);

    /**
     * 在这个管道的最后一个位置插入{@link ChannelHandler}s。
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}s
     *                  methods.
     * @param handlers  the handlers to insert last
     *
     */
    ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers);

    /**
     * 从这个管道中删除指定的{@link ChannelHandler}。
     *
     * @param  handler          the {@link ChannelHandler} to remove
     *
     * @throws NoSuchElementException
     *         if there's no such handler in this pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline remove(ChannelHandler handler);

    /**
     * 从这个管道中删除指定名称的{@link ChannelHandler}。
     *
     * @param  name             the name under which the {@link ChannelHandler} was stored.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler with the specified name in this pipeline
     * @throws NullPointerException
     *         if the specified name is {@code null}
     */
    ChannelHandler remove(String name);

    /**
     * 从这个管道中删除指定类型的{@link ChannelHandler}。
     *
     * @param <T>           the type of the handler
     * @param handlerType   the type of the handler
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler of the specified type in this pipeline
     * @throws NullPointerException
     *         if the specified handler type is {@code null}
     */
    <T extends ChannelHandler> T remove(Class<T> handlerType);

    /**
     * 删除此管道中的第一个{@link ChannelHandler}。
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeFirst();

    /**
     * 删除此管道中最后一个{@link ChannelHandler}。
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeLast();

    /**
     * 将指定的{@link ChannelHandler}替换为这个管道中的新处理程序。
     *
     * @param  oldHandler    the {@link ChannelHandler} to be replaced
     * @param  newName       the name under which the replacement should be added
     * @param  newHandler    the {@link ChannelHandler} which is used as replacement
     *
     * @return itself

     * @throws NoSuchElementException
     *         if the specified old handler does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler or new handler is
     *         {@code null}
     */
    ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler);

    /**
     * 将指定名称的{@link ChannelHandler}替换为该管道中的新处理程序。
     *
     * @param  oldName       the name of the {@link ChannelHandler} to be replaced
     * @param  newName       the name under which the replacement should be added
     * @param  newHandler    the {@link ChannelHandler} which is used as replacement
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler with the specified old name does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler or new handler is
     *         {@code null}
     */
    ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler);

    /**
     * 将指定类型的{@link ChannelHandler}替换为该管道中的新处理程序。
     *
     * @param  oldHandlerType   the type of the handler to be removed
     * @param  newName          the name under which the replacement should be added
     * @param  newHandler       the {@link ChannelHandler} which is used as replacement
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler of the specified old handler type does not exist
     *         in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler or new handler is
     *         {@code null}
     */
    <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName,
                                         ChannelHandler newHandler);

    /**
     * 返回这个管道中的第一个{@link ChannelHandler}。
     *
     * @return the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler first();

    /**
     * 返回此管道中第一个{@link ChannelHandler}的上下文。
     *
     * @return the context of the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandlerContext firstContext();

    /**
     * 返回这个管道中的最后一个{@link ChannelHandler}。
     *
     * @return the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler last();

    /**
     * 返回此管道中最后一个{@link ChannelHandler}的上下文。
     *
     * @return the context of the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandlerContext lastContext();

    /**
     * 返回本管道中指定名称的{@link ChannelHandler}。
     *
     * @return the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandler get(String name);

    /**
     * 返回此管道中指定类型的{@link ChannelHandler}。
     *
     * @return the handler of the specified handler type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    <T extends ChannelHandler> T get(Class<T> handlerType);

    /**
     * 返回此管道中指定的{@link ChannelHandler}的上下文对象。
     *
     * @return the context object of the specified handler.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(ChannelHandler handler);

    /**
     * 返回本管道中指定名称的{@link ChannelHandler}的上下文对象。
     *
     * @return the context object of the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(String name);

    /**
     * 返回本管道中指定类型的{@link ChannelHandler}的上下文对象。
     *
     * @return the context object of the handler of the specified type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType);

    /**
     * 返回此管道所连接的{@link Channel}。
     *
     * @return the channel. {@code null} if this pipeline is not attached yet.
     */
    Channel channel();

    /**
     * 返回处理程序名称的{@link List}。
     */
    List<String> names();

    /**
     * 将此管道转换为一个有序的{@link Map}，其键是处理程序名称，其值是处理程序。
     */
    Map<String, ChannelHandler> toMap();

    @Override
    ChannelPipeline fireChannelRegistered();

    @Override
    ChannelPipeline fireChannelUnregistered();

    @Override
    ChannelPipeline fireChannelActive();

    @Override
    ChannelPipeline fireChannelInactive();

    @Override
    ChannelPipeline fireExceptionCaught(Throwable cause);

    @Override
    ChannelPipeline fireUserEventTriggered(Object event);

    @Override
    ChannelPipeline fireChannelRead(Object msg);

    @Override
    ChannelPipeline fireChannelReadComplete();

    @Override
    ChannelPipeline fireChannelWritabilityChanged();

    @Override
    ChannelPipeline flush();
}
