/*
 * Copyright 2013 The Netty Project
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
package io.netty.buffer;

import io.netty.util.ReferenceCounted;

/**
 * 正在发送或接收的数据包。
 */
public interface ByteBufHolder extends ReferenceCounted {

    /**
     * 返回由这个{@link ByteBufHolder}持有的数据。
     */
    ByteBuf content();

    /**
     * 创建此{@link ByteBufHolder}的深层副本。
     */
    ByteBufHolder copy();

    /**
     * 复制此{@link ByteBufHolder}。注意，这不会自动调用{@link #retain()}。
     */
    ByteBufHolder duplicate();

    /**
     * 复制此{@link ByteBufHolder}。与{@link #duplicate()}不同，该方法返回保留的副本。
     *
     * @see ByteBuf#retainedDuplicate()
     */
    ByteBufHolder retainedDuplicate();

    /**
     * 返回一个新的{@link ByteBufHolder}，其中包含指定的{@code内容}。
     */
    ByteBufHolder replace(ByteBuf content);

    @Override
    ByteBufHolder retain();

    @Override
    ByteBufHolder retain(int increment);

    @Override
    ByteBufHolder touch();

    @Override
    ByteBufHolder touch(Object hint);
}
