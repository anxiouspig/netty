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

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 处理一个I/O事件或拦截一个I/O操作，并将其转发给它的{@link ChannelPipeline}中的下一个处理程序。
 *
 * <h3>子类型</h3>
 * <p>
 * {@link ChannelHandler}本身并没有提供很多方法，但你通常必须实现它的一个子类型。
 * <ul>
 * <li>{@link ChannelInboundHandler}来处理入站I/O事件，以及</li>
 * <li>{@link ChannelOutboundHandler}来处理出站I/O操作。</li>
 * </ul>
 * </p>
 * <p>
 * 另外，为了方便起见，还提供了以下适配器类。
 * <ul>
 * <li>{@link ChannelInboundHandlerAdapter} 以处理入站I/O事件,</li>
 * <li>{@link ChannelOutboundHandlerAdapter} 来处理出站I/O操作，以及</li>
 * <li>{@link ChannelDuplexHandler} 以处理入站和出站事件</li>
 * </ul>
 * </p>
 * <p>
 * 更多信息，请参考各子类型的文件。
 * </p>
 *
 * <h3>上下文对象</h3>
 * <p>
 * 一个{@link ChannelHandler}被提供了一个{@link ChannelHandlerContext}对象。
 * 一个{@link ChannelHandler}应该通过上下文对象与其所属的{@link ChannelPipeline}进行交互。
 * 使用上下文对象，{@link ChannelHandler}可以在上游或下游传递事件，动态修改管道，或者存储处理程序特有的信息（使用{@link AttributeKey}s）。
 *
 * <h3>状态管理</h3>
 *
 * 一个{@link ChannelHandler}通常需要存储一些有状态的信息。最简单和推荐的方法是使用成员变量。
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *
 *     <b>private boolean loggedIn;</b>
 *
 *     {@code @Override}
 *     public void channelRead0({@link ChannelHandlerContext} ctx, Message message) {
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) message);
 *             <b>loggedIn = true;</b>
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>loggedIn</b>) {
 *                 ctx.writeAndFlush(fetchSecret((GetDataMessage) message));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * 因为处理程序实例有一个状态变量，这个状态变量专门用于一个连接，所以你必须为每一个新的通道创建一个新的处理程序实例，
 * 以避免出现竞赛条件，使未经认证的客户端能够获得机密信息。
 * <pre>
 * // 为每个通道创建一个新的处理程序实例。
 * // 参见{@link ChannelInitializer#initChannel(Channel)}。
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>new DataServerHandler()</b>);
 *     }
 * }
 *
 * </pre>
 *
 * <h4>使用 {@link AttributeKey}s</h4>
 *
 * 虽然建议使用成员变量来存储处理程序的状态，但出于某些原因，你可能不想创建很多处理程序实例。
 * 在这种情况下，你可以使用{@link AttributeKey}s，它是由{@link AttributeKey}提供的。
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * {@code @Sharable}
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *     private final {@link AttributeKey}&lt;{@link Boolean}&gt; auth =
 *           {@link AttributeKey#valueOf(String) AttributeKey.valueOf("auth")};
 *
 *     {@code @Override}
 *     public void channelRead({@link ChannelHandlerContext} ctx, Message message) {
 *         {@link Attribute}&lt;{@link Boolean}&gt; attr = ctx.attr(auth);
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>attr.set(true)</b>;
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>Boolean.TRUE.equals(attr.get())</b>) {
 *                 ctx.writeAndFlush(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * 现在，处理程序的状态已经附加到{@link ChannelHandlerContext}中，你可以将同一个处理程序实例添加到不同的管道中。
 * <pre>
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *
 *     private static final DataServerHandler <b>SHARED</b> = new DataServerHandler();
 *
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>SHARED</b>);
 *     }
 * }
 * </pre>
 *
 *
 * <h4>{@code @Sharable}注解。</h4>
 * <p>
 * 在上面使用{@link AttributeKey}的例子中。
 * 你可能已经注意到了{@code @Sharable}注解。
 * <p>
 * 如果一个{@link ChannelHandler}被注解为{@code @Sharable}注解，这意味着你可以只创建一次处理程序的实例，
 * 并将其多次添加到一个或多个{@link ChannelPipeline}中，而不会出现竞赛条件。
 * <p>
 * 如果没有指定这个注解，每次将它添加到管道中时都必须创建一个新的处理程序实例，因为它有成员变量等非共享状态。
 * <p>
 * 这个注释是为了文档目的而提供的，就像<a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">JCIP注释</a>一样。
 *
 * <h3>其他值得阅读的资源</h3>
 * <p>
 * 请参考{@link ChannelHandler}，和{@link ChannelPipeline}来了解更多关于入站操作和出站操作的信息，
 * 它们有什么根本性的区别，它们在管道中是如何流动的，以及如何在你的应用程序中处理操作。
 */
public interface ChannelHandler {

    /**
     * 在{@link ChannelHandler}被添加到实际上下文后被调用，并且它已经准备好处理事件。
     */
    void handlerAdded(ChannelHandlerContext ctx) throws Exception;

    /**
     * 在{@link ChannelHandler}从实际上下文中移除后被调用，并且它不再处理事件。
     */
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception;

    /**
     * 在抛出{@link Throwable}时调用。
     *
     * @deprecated if you want to handle this event you should implement {@link ChannelInboundHandler} and
     *      * implement the method there.
     */
    @Deprecated
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;

    /**
     * 表示注解的{@link ChannelHandler}的同一个实例可以多次添加到一个或多个{@link ChannelPipeline}中，而不存在竞赛条件。
     * <p>
     * 如果没有指定这个注解，每次将它添加到管道中时都必须创建一个新的处理程序实例，因为它有成员变量等非共享状态。
     * <p>
     * 这个注解是为了文档的目的而提供的，就如同
     * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
     */
    @Inherited
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Sharable {
        // no value
    }
}
