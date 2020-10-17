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
package io.netty.buffer;

/**
 * 实现负责分配缓冲区。这个接口的实现应该是线程安全的。
 */
public interface ByteBufAllocator {

    ByteBufAllocator DEFAULT = ByteBufUtil.DEFAULT_ALLOCATOR;

    /**
     * 分配{@link ByteBuf}。它是直接缓冲区还是堆缓冲区取决于实际的实现。
     */
    ByteBuf buffer();

    /**
     * 分配一个具有给定初始容量的{@link ByteBuf}。它是直接缓冲区还是堆缓冲区取决于实际的实现。
     */
    ByteBuf buffer(int initialCapacity);

    /**
     * 分配一个具有给定初始容量和给定最大容量的{@link ByteBuf}。它是直接缓冲区还是堆缓冲区取决于实际的实现。
     */
    ByteBuf buffer(int initialCapacity, int maxCapacity);

    /**
     * 分配一个{@link ByteBuf}，最好是一个适合I/O的直接缓冲区。
     */
    ByteBuf ioBuffer();

    /**
     * 分配一个{@link ByteBuf}，最好是一个适合I/O的直接缓冲区。
     */
    ByteBuf ioBuffer(int initialCapacity);

    /**
     * 分配一个{@link ByteBuf}，最好是一个适合I/O的直接缓冲区。
     */
    ByteBuf ioBuffer(int initialCapacity, int maxCapacity);

    /**
     * 分配一个堆{@link ByteBuf}。
     */
    ByteBuf heapBuffer();

    /**
     * 分配一个具有给定初始容量的堆{@link ByteBuf}。
     */
    ByteBuf heapBuffer(int initialCapacity);

    /**
     * 分配一个具有给定初始容量和给定最大容量的heap {@link ByteBuf}。
     */
    ByteBuf heapBuffer(int initialCapacity, int maxCapacity);

    /**
     * 分配一个直接{@link ByteBuf}。
     */
    ByteBuf directBuffer();

    /**
     * 分配一个具有给定初始容量的{@link ByteBuf}。
     */
    ByteBuf directBuffer(int initialCapacity);

    /**
     * 分配一个具有给定初始容量和给定最大容量的{@link ByteBuf}。
     */
    ByteBuf directBuffer(int initialCapacity, int maxCapacity);

    /**
     * 分配一个{@link CompositeByteBuf}。
     * 它是直接缓冲区还是堆缓冲区取决于实际的实现。
     */
    CompositeByteBuf compositeBuffer();

    /**
     * 分配一个{@link CompositeByteBuf}，并在其中存储给定的最大数量的组件。
     * 它是直接缓冲区还是堆缓冲区取决于实际的实现。
     */
    CompositeByteBuf compositeBuffer(int maxNumComponents);

    /**
     * 分配一个堆{@link CompositeByteBuf}。
     */
    CompositeByteBuf compositeHeapBuffer();

    /**
     * 分配一个堆{@link CompositeByteBuf}，其中包含给定的最大数量的可以存储的组件。
     */
    CompositeByteBuf compositeHeapBuffer(int maxNumComponents);

    /**
     * 分配一个直接{@link CompositeByteBuf}。
     */
    CompositeByteBuf compositeDirectBuffer();

    /**
     * 分配一个直接{@link CompositeByteBuf}，并在其中存储给定的最大数量的组件。
     */
    CompositeByteBuf compositeDirectBuffer(int maxNumComponents);

    /**
     * 如果直接{@link ByteBuf}被池化，则返回{@code true}
     */
    boolean isDirectBufferPooled();

    /**
     * 计算当{@link ByteBuf}需要以{@code maxCapacity}为上限扩展{@code minNewCapacity}时使用的{@link ByteBuf}的新容量。
     */
    int calculateNewCapacity(int minNewCapacity, int maxCapacity);
 }
