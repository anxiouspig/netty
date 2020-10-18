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
package io.netty.channel.nio;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 * 当{@link SelectableChannel}准备就绪时，可以由{@link NioEventLoop}执行的任意任务。
 *
 * @see NioEventLoop#register(SelectableChannel, int, NioTask)
 */
public interface NioTask<C extends SelectableChannel> {
    /**
     * 当{@link Selector}选择了{@link SelectableChannel}时调用。
     */
    void channelReady(C ch, SelectionKey key) throws Exception;

    /**
     * 当指定的{@link SelectionKey}的{@link SelectionKey}被取消，因此这个{@link NioTask}将不再被通知时调用。
     *
     * @param cause 取消注册的原因。如果调用{@link SelectionKey#cancel()}的用户或事件循环已被关闭，则为{@code null}。
     */
    void channelUnregistered(C ch, Throwable cause) throws Exception;
}
