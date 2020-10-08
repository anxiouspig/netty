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

import io.netty.util.concurrent.EventExecutorGroup;

/**
 * 特别的 {@link EventExecutorGroup} ，允许注册 {@link Channel}s ，
 * 以便之后在事件循环中选择处理.
 *
 */
public interface EventLoopGroup extends EventExecutorGroup {
    /**
     * 返回下一个 {@link EventLoop} 去使用
     */
    @Override
    EventLoop next();

    /**
     * 注册一个 {@link Channel} 用这个 {@link EventLoop}. 返回 {@link ChannelFuture}
     * 一旦注册完成将通知.
     */
    ChannelFuture register(Channel channel);

    /**
     * 注册一个 {@link Channel} 用这个 {@link EventLoop} 使用一个 {@link ChannelFuture}. 通过
     * {@link ChannelFuture} 一旦注册成功将得到通知和也会返回.
     */
    ChannelFuture register(ChannelPromise promise);

    /**
     * 注册一个 {@link Channel} 用这个 {@link EventLoop}. 通过 {@link ChannelFuture}
     * 一旦注册成功将得到通知和也会返回.
     *
     * @deprecated 使用 {@link #register(ChannelPromise)} 代替.
     */
    // 方法过时了。
    @Deprecated
    ChannelFuture register(Channel channel, ChannelPromise promise);
}
