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

import io.netty.bootstrap.Bootstrap;
import io.netty.util.concurrent.BlockingOperationException;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.TimeUnit;


/**
 * 异步{@link Channel}I/O操作的结果。
 * <p>
 * Netty中所有的I/O操作都是异步的。 这意味着任何I/O调用都会立即返回，而不保证在调用结束时请求的I/O操作已经完成。
 * 取而代之的是，你将返回一个{@link ChannelFuture}实例，它为你提供了有关I/O操作的结果或状态的信息。
 * <p>
 * 一个{@link ChannelFuture}要么是<em>未完成的</em>，要么是<em>完成的</em>。当一个I/O操作开始时，
 * 会创建一个新的未来对象。 新的未来对象最初是未完成的--它既没有成功、失败，也没有被取消，因为I/O操作还没有完成。
 * 如果I/O操作成功完成、失败或取消，那么未来将被标记为完成，并提供更具体的信息，例如失败的原因。 请注意，即使失败和取消也属于完成状态。
 * <pre>
 *                                      +---------------------------+
 *                                      | Completed successfully    |
 *                                      +---------------------------+
 *                                 +---->      isDone() = true      |
 * +--------------------------+    |    |   isSuccess() = true      |
 * |        Uncompleted       |    |    +===========================+
 * +--------------------------+    |    | Completed with failure    |
 * |      isDone() = false    |    |    +---------------------------+
 * |   isSuccess() = false    |----+---->      isDone() = true      |
 * | isCancelled() = false    |    |    |       cause() = non-null  |
 * |       cause() = null     |    |    +===========================+
 * +--------------------------+    |    | Completed by cancellation |
 *                                 |    +---------------------------+
 *                                 +---->      isDone() = true      |
 *                                      | isCancelled() = true      |
 *                                      +---------------------------+
 * </pre>
 *
 * 提供了各种方法让你检查I/O操作是否已经完成，等待完成，并检索I/O操作的结果。
 * 它还允许你添加{@link ChannelFutureListener}，这样你就可以在I/O操作完成时得到通知。
 *
 * <h3>更倾向于{@link #addListener(GenericFutureListener)}而不是{@link #await()}。</h3>
 *
 * 建议尽可能选择{@link #addListener(GenericFutureListener)}而不是{@link #await()}，
 * 以便在一个I/O操作完成后得到通知，并进行任何后续任务。
 * <p>
 * {@link #addListener(GenericFutureListener)}是非阻塞的。 它只是将指定的
 * {@link ChannelFutureListener}添加到{@link ChannelFuture}中，当与未来相关的I/O操作完成后，I/O线程会通知监听器。
 * {@link ChannelFutureListener}产生了最好的性能和资源利用率，
 * 因为它完全不阻塞，但如果你不习惯事件驱动的编程，实现一个顺序逻辑可能会很棘手。
 * <p>
 * 相比之下，{@link #await()}是一个阻塞操作。 一旦被调用，调用者线程就会阻塞，直到操作完成。
 * 用{@link #await()}实现顺序逻辑比较容易，但调用者线程在I/O操作完成之前会进行不必要的阻塞，而且线程间通知的成本相对较高。
 * 另外，在特殊情况下有可能出现死锁，下面会介绍。
 *
 * <h3>不要在{@link ChannelHandler}里面调用{@link #await()}。</h3>
 * <p>
 * {@link ChannelHandler}中的事件处理程序方法通常由一个I/O线程调用。
 * 如果{@link #await()}被一个事件处理方法调用，而这个方法是由I/O线程调用的，
 * 那么它所等待的I/O操作可能永远不会完成，因为
 * {@link #await()}会阻塞它所等待的I/O操作，这就是一个死锁。
 * <pre>
 * // 坏的--永远不要这样做
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.awaitUninterruptibly();
 *     // Perform post-closure operation
 *     // ...
 * }
 *
 * // GOOD
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.addListener(new {@link ChannelFutureListener}() {
 *         public void operationComplete({@link ChannelFuture} future) {
 *             // Perform post-closure operation
 *             // ...
 *         }
 *     });
 * }
 * </pre>
 * <p>
 * 尽管有上述的缺点，但当然也有一些情况下，调用{@link #await()}更方便。在这种情况下，
 * 请确保不要在I/O线程中调用{@link #await()}。 否则，将引发
 * {@link BlockingOperationException}以防止死锁。
 *
 * <h3>不要把I/O超时和等待超时混为一谈。</h3>
 *
 * 你用{@link #await(long)}、{@link #await(long, TimeUnit)}、
 * {@link #awaitUninterruptibly(long)}或{@link #awaitUninterruptibly(long, TimeUnit)}指定的超时值与I/O超时完全无关。
 * 如果一个I/O操作超时，未来将被标记为 "失败完成"，如上图所示。
 * 例如，连接超时应该通过传输专用选项来配置。
 * <pre>
 * // BAD - NEVER DO THIS
 * {@link Bootstrap} b = ...;
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly(10, TimeUnit.SECONDS);
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     // You might get a NullPointerException here because the future
 *     // might not be completed yet.
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 *
 * // GOOD
 * {@link Bootstrap} b = ...;
 * // Configure the connect timeout option.
 * <b>b.option({@link ChannelOption}.CONNECT_TIMEOUT_MILLIS, 10000);</b>
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly();
 *
 * // Now we are sure the future is completed.
 * assert f.isDone();
 *
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 * </pre>
 */
public interface ChannelFuture extends Future<Void> {

    /**
     * 返回与该未来相关的I/O操作发生的通道。
     */
    Channel channel();

    @Override
    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture sync() throws InterruptedException;

    @Override
    ChannelFuture syncUninterruptibly();

    @Override
    ChannelFuture await() throws InterruptedException;

    @Override
    ChannelFuture awaitUninterruptibly();

    /**
     * 如果这个{@link ChannelFuture}是一个void future，并且不允许调用以下任何方法，则返回{@code true}。
     * <ul>
     *     <li>{@link #addListener(GenericFutureListener)}</li>
     *     <li>{@link #addListeners(GenericFutureListener[])}</li>
     *     <li>{@link #await()}</li>
     *     <li>{@link #await(long, TimeUnit)} ()}</li>
     *     <li>{@link #await(long)} ()}</li>
     *     <li>{@link #awaitUninterruptibly()}</li>
     *     <li>{@link #sync()}</li>
     *     <li>{@link #syncUninterruptibly()}</li>
     * </ul>
     */
    boolean isVoid();
}
