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

import io.netty.util.ByteProcessor;
import io.netty.util.ReferenceCounted;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

/**
 * 由0或多个字节（八位字节）组成的随机和顺序访问序列。
 * 这个接口为一个或多个原始字节数组提供一个抽象视图 ({@code byte[]}) 和 {@linkplain ByteBuffer NIO buffers}.
 *
 * <h3>创建一个缓冲数组</h3>
 *
 * 建议使用{@link Unpooled}中的助手方法创建一个新的缓冲区，而不是调用单独实现的构造函数。
 *
 * <h3>随机访问索引</h3>
 *
 * 就像一个普通的原始字节数组，{@link ByteBuf}使用
 * <a href=" http://en.wikipedia.org/wiki/zero based_number" >基于索引</a>。
 * 它意味着第一个字节的索引总是{@code 0}，最后一个字节的索引总是{@link #capacity() capacity - 1}。
 * 例如，要迭代一个缓冲区的所有字节，您可以做以下操作，而不管它的内部实现:
 *
 * <pre>
 * {@link ByteBuf} buffer = ...;
 * for (int i = 0; i &lt; buffer.capacity(); i ++) {
 *     byte b = buffer.getByte(i);
 *     System.out.println((char) b);
 * }
 * </pre>
 *
 * <h3>索引顺序存取</h3>
 *
 * {@link ByteBuf}提供了两个指针变量来支持顺序读和写操作——分别为读操作提供{@link #readerIndex() readerIndex}
 * 和为写操作提供{@link #writerIndex() writerIndex}。下图显示了缓冲区是如何通过两个指针分割成三个区域的:
 *
 * <pre>
 *      +-------------------+------------------+------------------+
 *      | discardable bytes |  readable bytes  |  writable bytes  |
 *      |                   |     (CONTENT)    |                  |
 *      +-------------------+------------------+------------------+
 *      |                   |                  |                  |
 *      0      <=      readerIndex   <=   writerIndex    <=    capacity
 * </pre>
 *
 * <h4>可读字节(实际内容)</h4>
 *
 * 这个段是存储实际数据的地方。任何名称以{@code read}或{@code skip}开头的操作将获得或跳过当前
 * {@link #readerIndex() readerIndex}处的数据，并增加读取字节数。
 * 如果读操作的参数也是{@link ByteBuf}，并且没有指定目标索引，则指定缓冲区的{@link #writerIndex() writerIndex}一起增加。
 * <p>
 * 如果没有足够的内容剩下，{@link IndexOutOfBoundsException}被抛出。
 * 新分配、包装或复制的缓冲区的{@link #readerIndex() readerIndex}的默认值是{@code 0}。
 *
 * <pre>
 * // 迭代缓冲区的可读字节。
 * {@link ByteBuf} buffer = ...;
 * while (buffer.isReadable()) {
 *     System.out.println(buffer.readByte());
 * }
 * </pre>
 *
 * <h4>可写的字节数</h4>
 *
 * 这个段是一个未定义的空间，需要被填充。任何名称以{@code write}开头的操作都将在当前的
 * {@link #writerIndex() writerIndex}处写入数据，并增加写入字节数。
 * 如果写操作的参数也是{@link ByteBuf}，并且没有指定源索引，则指定缓冲区的{@link #readerIndex() readerIndex}一起增加。
 * <p>
 * 如果没有足够的可写字节，{@link IndexOutOfBoundsException}被抛出。
 * 新分配缓冲区的默认值{@link #writerIndex() writerIndex}是{@code 0}。
 * 包装或复制缓冲区的{@link #writerIndex() writerIndex}的默认值是该缓冲区的{@link #capacity() capacity}。
 *
 * <pre>
 * // 用随机整数填充缓冲区的可写字节。
 * {@link ByteBuf} buffer = ...;
 * while (buffer.maxWritableBytes() >= 4) {
 *     buffer.writeInt(random.nextInt());
 * }
 * </pre>
 *
 * <h4>Discardable bytes</h4>
 *
 * 此段包含已被读操作读取的字节。
 * 最初，这个段的大小是{@code 0}，但是当执行读操作时，它的大小会增加到{@link #writerIndex() writerIndex}。
 * 读取的字节可以通过调用{@link #discardReadBytes()}来回收未使用的区域来丢弃，如下图所示:
 *
 * <pre>
 *  BEFORE discardReadBytes()
 *
 *      +-------------------+------------------+------------------+
 *      | discardable bytes |  readable bytes  |  writable bytes  |
 *      +-------------------+------------------+------------------+
 *      |                   |                  |                  |
 *      0      <=      readerIndex   <=   writerIndex    <=    capacity
 *
 *
 *  AFTER discardReadBytes()
 *
 *      +------------------+--------------------------------------+
 *      |  readable bytes  |    writable bytes (got more space)   |
 *      +------------------+--------------------------------------+
 *      |                  |                                      |
 * readerIndex (0) <= writerIndex (decreased)        <=        capacity
 * </pre>
 *
 * 请注意，在调用{@link #discardReadBytes()}之后，不能保证可写字节的内容。
 * 在大多数情况下，可写字节不会被移动，甚至可以根据底层缓冲区实现使用完全不同的数据来填充。
 *
 * <h4>清除缓冲区索引</h4>
 *
 * 通过调用{@link #readerIndex()}，可以将{@link #readerIndex() readerIndex}和
 * {@link #writerIndex() writerIndex}设置为{@code 0}。
 * 它不清除缓冲区内容(例如，用{@code 0}填充)，而只是清除两个指针。
 * 还请注意，此操作的语义与{@link ByteBuffer#clear()}不同。
 *
 * <pre>
 *  BEFORE clear()
 *
 *      +-------------------+------------------+------------------+
 *      | discardable bytes |  readable bytes  |  writable bytes  |
 *      +-------------------+------------------+------------------+
 *      |                   |                  |                  |
 *      0      <=      readerIndex   <=   writerIndex    <=    capacity
 *
 *
 *  AFTER clear()
 *
 *      +---------------------------------------------------------+
 *      |             writable bytes (got more space)             |
 *      +---------------------------------------------------------+
 *      |                                                         |
 *      0 = readerIndex = writerIndex            <=            capacity
 * </pre>
 *
 * <h3>搜索操作</h3>
 *
 * 对于简单的单字节搜索，使用{@link #indexOf(int, int, byte)}和{@link #bytesBefore(int, int, byte)}。
 * 当处理以{@code NUL}结尾的字符串时，{@link #bytesBefore(byte)}特别有用。
 * 对于复杂的搜索，使用{@link #forEachByte(int, int, ByteProcessor)}和{@link ByteProcessor}实现。
 *
 * <h3>Mark and reset</h3>
 *
 * 每个缓冲区中都有两个标记索引。一个用于存储{@link #readerIndex() readerIndex}，
 * 另一个用于存储{@link #writerIndex() writerIndex}。您总是可以通过调用reset方法来重新定位两个索引中的一个。
 * 它的工作方式类似于{@link InputStream}中的标记和重置方法，只是没有{@code readlimit}。
 *
 * <h3>Derived buffers</h3>
 *
 * 您可以通过调用下列方法之一来创建现有缓冲区的视图:
 * <ul>
 *   <li>{@link #duplicate()}</li>
 *   <li>{@link #slice()}</li>
 *   <li>{@link #slice(int, int)}</li>
 *   <li>{@link #readSlice(int)}</li>
 *   <li>{@link #retainedDuplicate()}</li>
 *   <li>{@link #retainedSlice()}</li>
 *   <li>{@link #retainedSlice(int, int)}</li>
 *   <li>{@link #readRetainedSlice(int)}</li>
 * </ul>
 * 派生缓冲区将拥有独立的{@link #readerIndex() readerIndex}、
 * {@link #writerIndex() writerIndex}和标记索引，同时它共享其他内部数据表示，就像NIO缓冲区所做的那样。
 * <p>
 * 如果需要对现有缓冲区进行全新的复制，请调用{@link #copy()}方法。
 *
 * <h4>非保留和保留的派生缓冲区</h4>
 *
 * 注意，{@link #duplicate()}、{@link #slice()}、{@link #slice(int, int)}和
 * {@link #readSlice(int)}不会在返回的派生缓冲区上调用{@link #retain()}，
 * 因此它的引用计数不会增加。如果需要创建一个引用计数增加的派生缓冲区，
 * 可以考虑使用{@link #retainedDuplicate()}、{@link #retainedSlice()}、
 * {@link #retainedSlice(int, int)}和{@link #readRetainedSlice(int)}，它们可能会返回一个产生更少垃圾的缓冲区实现。
 *
 * <h3>转换到现有JDK类型</h3>
 *
 * <h4>Byte array</h4>
 *
 * 如果一个{@link ByteBuf}由一个字节数组支持(即{@code byte[]})，
 * 您可以通过{@link #array()}方法直接访问它。要确定缓冲区是否由字节数组支持，应该使用{@link #hasArray()}。
 *
 * <h4>NIO Buffers</h4>
 *
 * 如果一个{@link ByteBuf}可以转换为一个共享其内容(即视图缓冲区)的NIO {@link ByteBuffer}，
 * 你可以通过{@link #nioBuffer()}方法获得它。要确定缓冲区是否可以转换为NIO缓冲区，使用{@link #nioBufferCount()}。
 *
 * <h4>Strings</h4>
 *
 * 各种{@link #toString(Charset)}方法将{@link ByteBuf}转换为{@link String}。
 * 请注意{@link #toString()}不是一个转换方法。
 *
 * <h4>I/O Streams</h4>
 *
 * Please refer to {@link ByteBufInputStream} and
 * {@link ByteBufOutputStream}.
 */
public abstract class ByteBuf implements ReferenceCounted, Comparable<ByteBuf> {

    /**
     * 返回此缓冲区可以包含的字节数(八进制)。
     */
    public abstract int capacity();

    /**
     * 调整此缓冲区的容量。如果{@code newCapacity}小于当前容量，则该缓冲区的内容将被截断。
     * 如果{@code newCapacity}大于当前容量，缓冲区将附加未指定的数据，其长度为{@code (newCapacity - currentCapacity)}。
     *
     * @throws IllegalArgumentException 如果{@code newCapacity}大于{@link #maxCapacity()}
     */
    public abstract ByteBuf capacity(int newCapacity);

    /**
     * 返回此缓冲区允许的最大容量。此值提供了{@link #capacity()}的上限。
     */
    public abstract int maxCapacity();

    /**
     * 返回创建此缓冲区的{@link ByteBufAllocator}。
     */
    public abstract ByteBufAllocator alloc();

    /**
     * 返回该缓冲区的<a href="http://en.wikipedia.org/wiki/Endianness">endianness</a>。
     *
     * @deprecated 使用小端访问器，例如{@code getShortLE}， {@code getIntLE}，而不是用交换的{@code endianness}来创建缓冲区。
     */
    @Deprecated
    public abstract ByteOrder order();

    /**
     * 返回一个具有指定的{@code endianness}的缓冲区，该缓冲区共享该缓冲区的整个区域、索引和标记。
     * 修改返回缓冲区或此缓冲区的内容、索引或标记会影响彼此的内容、索引和标记。
     * 如果指定的{@code endianness}与此缓冲区的字节顺序相同，此方法可以返回{@code this}。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @deprecated 使用小端访问器，例如{@code getShortLE}， {@code getIntLE}，
     * 而不是用交换的{@code endianness}来创建缓冲区。
     */
    @Deprecated
    public abstract ByteBuf order(ByteOrder endianness);

    /**
     * 如果此缓冲区是另一个缓冲区的包装器，则返回基础缓冲区实例。
     *
     * @return 如果此缓冲区不是包装器，则为{@code null}
     */
    public abstract ByteBuf unwrap();

    /**
     * 当且仅当此缓冲区由NIO直接缓冲区支持时，返回{@code true}。
     */
    public abstract boolean isDirect();

    /**
     * 当且仅当此缓冲区为只读时，返回{@code true}。
     */
    public abstract boolean isReadOnly();

    /**
     * 返回此缓冲区的只读版本。
     */
    public abstract ByteBuf asReadOnly();

    /**
     * 返回该缓冲区的{@code readerIndex}。
     */
    public abstract int readerIndex();

    /**
     * 设置此缓冲区的{@code readerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         如果指定的{@code readerIndex}小于{@code 0}或大于{@code this.writerIndex}
     */
    public abstract ByteBuf readerIndex(int readerIndex);

    /**
     * 返回该缓冲区的{@code writerIndex}。
     */
    public abstract int writerIndex();

    /**
     * 设置此缓冲区的{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code writerIndex} is
     *            less than {@code this.readerIndex} or
     *            greater than {@code this.capacity}
     */
    public abstract ByteBuf writerIndex(int writerIndex);

    /**
     * 一次设置这个bufferindex的{@code readerIndex}和{@code writerIndex}。
     * 当你不得不担心{@link #readerIndex(int)}和{@link #writerIndex(int)}方法的调用顺序时，
     * 这个方法非常有用。例如，下面的代码将失败:
     *
     * <pre>
     * 创建一个缓冲区，其readerIndex、writerIndex和容量分别为0、0和8。
     * {@link ByteBuf} buf = {@link Unpooled}.buffer(8);
     *
     * 由于指定的readerIndex(2)不能大于当前的writerIndex(0)，因此抛出indextofboundsexception。
     * buf.readerIndex(2);
     * buf.writerIndex(4);
     * </pre>
     *
     * 下面的代码也会失败:
     *
     * <pre>
     * 创建一个缓冲区，其readerIndex、writerIndex和容量分别为0、8和8。
     * {@link ByteBuf} buf = {@link Unpooled}.wrappedBuffer(new byte[8]);
     *
     * readerIndex成为8。
     * buf.readLong();
     *
     * 抛出IndexOutOfBoundsException，因为指定的writerIndex(4)不能小于当前的readerIndex(8)。
     * buf.writerIndex(4);
     * buf.readerIndex(2);
     * </pre>
     *
     * 相反，该方法保证只要指定的索引满足基本约束条件，无论当前缓冲区的索引值是多少，
     * 都不会抛出{@link IndexOutOfBoundsException}:
     *
     * <pre>
     * 不管缓冲区的当前状态是什么，只要缓冲区的容量不小于4，下面的调用总是成功的。
     * buf.setIndex(2, 4);
     * </pre>
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code readerIndex} is less than 0,
     *         if the specified {@code writerIndex} is less than the specified
     *         {@code readerIndex} or if the specified {@code writerIndex} is
     *         greater than {@code this.capacity}
     */
    public abstract ByteBuf setIndex(int readerIndex, int writerIndex);

    /**
     * 返回可读字节数，该字节数等于{@code (this)。writerIndex - this.readerIndex)}。
     */
    public abstract int readableBytes();

    /**
     * 返回可写字节数，该字节数等于{@code (this.capacity- this.writerIndex)}。
     */
    public abstract int writableBytes();

    /**
     * 返回可写字节的最大可能数目，等于{@code (this)。maxCapacity - this.writerIndex)}。
     */
    public abstract int maxWritableBytes();

    /**
     * 返回可以确定写入的最大字节数，而不涉及内部重新分配或数据复制。返回值为
     * &ge;{@link #writableBytes()}和&le;{@link # maxWritableBytes()}。
     */
    public int maxFastWritableBytes() {
        return writableBytes();
    }

    /**
     * 返回{@code true}当且仅当{@code (this。writerIndex - this.readerIndex)}大于{@code 0}。
     */
    public abstract boolean isReadable();

    /**
     * 返回{@code true}，当且仅当此缓冲区包含等于或超过指定数量的元素时。
     */
    public abstract boolean isReadable(int size);

    /**
     * 返回{@code true}当且仅当{@code (this。capacity - this.writerIndex)}大于{@code 0}。
     */
    public abstract boolean isWritable();

    /**
     * 当且仅当缓冲区有足够的空间允许写入指定数量的元素时，返回{@code true}。
     */
    public abstract boolean isWritable(int size);

    /**
     * 将此缓冲区的{@code readerIndex}和{@code writerIndex}设置为{@code 0}。
     * 这个方法与{@link #setIndex(int, int) setIndex(0,0)}相同。
     * <p>
     * 请注意，此方法的行为与NIO缓冲区不同，后者将缓冲区的{@code limit}设置为{@code容量}。
     */
    public abstract ByteBuf clear();

    /**
     * 标记缓冲区中的当前{@code readerIndex}。通过调用{@link #resetReaderIndex()}，
     * 可以将当前的{@code readerIndex}重新定位为标记的{@code readerIndex}。
     * 标记的{@code readerIndex}的初始值是{@code 0}。
     */
    public abstract ByteBuf markReaderIndex();

    /**
     * 将当前的{@code readerIndex}重新定位到该缓冲区中标记的{@code readerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the current {@code writerIndex} is less than the marked
     *         {@code readerIndex}
     */
    public abstract ByteBuf resetReaderIndex();

    /**
     * 标记缓冲区中的当前{@code writerIndex}。通过调用{@link #resetWriterIndex()}，
     * 可以将当前的{@code writerIndex}重新定位到标记的{@code writerIndex}。标记的{@code writerIndex}的初始值是{@code 0}。
     */
    public abstract ByteBuf markWriterIndex();

    /**
     * 将当前的{@code writerIndex}重新定位到该缓冲区中标记的{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the current {@code readerIndex} is greater than the marked
     *         {@code writerIndex}
     */
    public abstract ByteBuf resetWriterIndex();

    /**
     * 丢弃第0个索引和{@code readerIndex}之间的字节。
     * 它将{@code readerIndex}和{@code writerIndex}之间的字节移动到第0个索引，
     * 并将{@code readerIndex}和{@code writerIndex}分别设置为{@code oldWriterIndex - oldReaderIndex}。
     * <p>
     * Please refer to the class documentation for more detailed explanation.
     */
    public abstract ByteBuf discardReadBytes();

    /**
     * 类似于{@link ByteBuf#discardReadBytes()}，除了这个方法可能会丢弃一些、全部或不丢弃读字节，这取决于它的内部实现，
     * 以减少总体内存带宽消耗为代价，可能会增加内存消耗。
     */
    public abstract ByteBuf discardSomeReadBytes();

    /**
     * 扩展缓冲区{@link #capacity()}以确保{@linkplain #writableBytes() 可写字节}的数量等于或大于指定的值。
     * 如果缓冲区中有足够的可写字节，此方法返回而没有副作用。
     *
     * @param minWritableBytes
     *        the expected minimum number of writable bytes
     * @throws IndexOutOfBoundsException
     *         if {@link #writerIndex()} + {@code minWritableBytes} &gt; {@link #maxCapacity()}.
     * @see #capacity(int)
     */
    public abstract ByteBuf ensureWritable(int minWritableBytes);

    /**
     * 扩展缓冲区{@link #capacity()}以确保{@linkplain #writableBytes() 可写字节}的数量等于或大于指定的值。
     * 与{@link #ensureWritable(int)}不同，此方法返回状态代码。
     *
     * @param minWritableBytes
     *        the expected minimum number of writable bytes
     * @param force
     *        When {@link #writerIndex()} + {@code minWritableBytes} &gt; {@link #maxCapacity()}:
     *        <ul>
     *        <li>{@code true} - the capacity of the buffer is expanded to {@link #maxCapacity()}</li>
     *        <li>{@code false} - the capacity of the buffer is unchanged</li>
     *        </ul>
     * @return {@code 0} if the buffer has enough writable bytes, and its capacity is unchanged.
     *         {@code 1} if the buffer does not have enough bytes, and its capacity is unchanged.
     *         {@code 2} if the buffer has enough writable bytes, and its capacity has been increased.
     *         {@code 3} if the buffer does not have enough bytes, but its capacity has been
     *                   increased to its maximum.
     */
    public abstract int ensureWritable(int minWritableBytes, boolean force);

    /**
     * 获取此缓冲区中指定的绝对(@code index)处的布尔值。此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     */
    public abstract boolean getBoolean(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的字节。此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     */
    public abstract byte  getByte(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的无符号字节。此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     */
    public abstract short getUnsignedByte(int index);

    /**
     * 获取位于此缓冲区中指定的绝对{@code index}处的16位短整数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract short getShort(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的16位短整数，该索引以小端字节顺序。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract short getShortLE(int index);

    /**
     * 获取位于此缓冲区中指定的绝对{@code index}处的无符号16位短整数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract int getUnsignedShort(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的无符号16位短整数，以小端字节顺序。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract int getUnsignedShortLE(int index);

    /**
     * 在此缓冲区中指定的绝对{@code index}处获取一个24位的中等整数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    public abstract int   getMedium(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的24位中整数，该绝对整数按小尾数字节顺序。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    public abstract int getMediumLE(int index);

    /**
     * 获取位于此缓冲区中指定的绝对{@code index}处的无符号24位中等整数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    public abstract int   getUnsignedMedium(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的无符号24位中整数，按小端字节顺序。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    public abstract int   getUnsignedMediumLE(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的32位整数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract int   getInt(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的32位整数，并具有小尾数字节顺序。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract int   getIntLE(int index);

    /**
     * 获取位于此缓冲区中指定的绝对{@code index}处的一个32位无符号整数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract long  getUnsignedInt(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的无符号32位整数，按小端字节顺序。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract long  getUnsignedIntLE(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的64位长整数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public abstract long  getLong(int index);

    /**
     * 获取该缓冲区中指定的绝对{@code index}处的64位长整数，以小端字节顺序。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public abstract long  getLongLE(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code索引}处的2字节UTF-16字符。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract char  getChar(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的32位浮点数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract float getFloat(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的32位浮点数，以小端字节顺序。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public float getFloatLE(int index) {
        return Float.intBitsToFloat(getIntLE(index));
    }

    /**
     * 获取此缓冲区中指定的绝对{@code index}处的64位浮点数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public abstract double getDouble(int index);

    /**
     * 获取此缓冲区中指定的绝对{@code索引}处的64位浮点数，以小端字节顺序。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public double getDoubleLE(int index) {
        return Double.longBitsToDouble(getLongLE(index));
    }

    /**
     * 从指定的绝对{@code索引}开始，将此缓冲区的数据传输到指定的目的地，直到目的地变为不可写。
     * 这个方法与{@link #getBytes(int, ByteBuf, int, int)}基本相同，
     * 除了这个方法增加了目标的{@code writerIndex}传输的字节数，而{@link #getBytes(int, ByteBuf, int, int)}不增加。
     * 这个方法不会修改源缓冲区的{@code readerIndex}或{@code writerIndex}(即{@code This})。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + dst.writableBytes} is greater than
     *            {@code this.capacity}
     */
    public abstract ByteBuf getBytes(int index, ByteBuf dst);

    /**
     * 从指定的绝对{@code索引}开始，将此缓冲区的数据传输到指定的目的地。
     * 这个方法与{@link #getBytes(int, ByteBuf, int, int)}基本相同，
     * 除了这个方法增加了目标的{@code writerIndex}传输的字节数，
     * 而{@link #getBytes(int, ByteBuf, int, int)}不增加。
     * 这个方法不会修改源缓冲区的{@code readerIndex}或{@code writerIndex}(即{@code This})。
     *
     * @param length the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code length} is greater than {@code dst.writableBytes}
     */
    public abstract ByteBuf getBytes(int index, ByteBuf dst, int length);

    /**
     * 从指定的绝对{@code索引}开始，将此缓冲区的数据传输到指定的目的地。
     * 此方法不会同时修改源(即{@code This})和目标的{@code readerIndex}或{@code writerIndex}。
     *
     * @param dstIndex the first index of the destination
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code dstIndex + length} is greater than
     *            {@code dst.capacity}
     */
    public abstract ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length);

    /**
     * 从指定的绝对{@code索引}开始，将此缓冲区的数据传输到指定的目的地。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + dst.length} is greater than
     *            {@code this.capacity}
     */
    public abstract ByteBuf getBytes(int index, byte[] dst);

    /**
     * 从指定的绝对{@code索引}开始，将此缓冲区的数据传输到指定的目的地。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @param dstIndex the first index of the destination
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code dstIndex + length} is greater than
     *            {@code dst.length}
     */
    public abstract ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length);

    /**
     * 从指定的绝对{@code索引}开始，将此缓冲区的数据传输到指定的目的地，直到目的地的位置达到极限为止。
     * 此方法不会修改此缓冲区的{@code readerIndex}或{@code writerIndex}，而目的地的{@code position}将被增加。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + dst.remaining()} is greater than
     *            {@code this.capacity}
     */
    public abstract ByteBuf getBytes(int index, ByteBuffer dst);

    /**
     * 将此缓冲区的数据从指定的绝对{@code索引}开始传输到指定的流。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @param length the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than
     *            {@code this.capacity}
     * @throws IOException
     *         if the specified stream threw an exception during I/O
     */
    public abstract ByteBuf getBytes(int index, OutputStream out, int length) throws IOException;

    /**
     * 将此缓冲区的数据传输到从指定的绝对{@code索引}开始的指定通道。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes written out to the specified channel
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than
     *            {@code this.capacity}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int getBytes(int index, GatheringByteChannel out, int length) throws IOException;

    /**
     * 将从指定的绝对{@code索引}开始的缓冲区数据传输到从给定文件位置开始的指定通道。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。此方法不会修改通道的位置。
     *
     * @param position the file position at which the transfer is to begin
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes written out to the specified channel
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than
     *            {@code this.capacity}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int getBytes(int index, FileChannel out, long position, int length) throws IOException;

    /**
     * 获取在给定索引处具有给定长度的{@link CharSequence}。
     *
     * @param length the length to read
     * @param charset that should be used
     * @return the sequence
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract CharSequence getCharSequence(int index, int length, Charset charset);

    /**
     * 在此缓冲区中指定的绝对{@code索引}处设置指定的布尔值。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setBoolean(int index, boolean value);

    /**
     * 在此缓冲区中指定的绝对{@code索引}处设置指定字节。指定值的24个高阶位将被忽略。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setByte(int index, int value);

    /**
     * 在此缓冲区中指定的绝对{@code索引}处设置指定的16位短整数。指定值的16个高阶位将被忽略。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setShort(int index, int value);

    /**
     * 以小端字节顺序在此缓冲区中指定的绝对{@code索引}处设置指定的16位短整数。
     * 指定值的16个高阶位将被忽略。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setShortLE(int index, int value);

    /**
     * 在此缓冲区中指定的绝对{@code索引}处设置指定的24位媒体整数。请注意，指定值中的最高有效字节将被忽略。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setMedium(int index, int value);

    /**
     * 以小端字节顺序在此缓冲区中指定的绝对{@code索引}处设置指定的24位中整数。
     * 请注意，指定值中的最高有效字节将被忽略。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setMediumLE(int index, int value);

    /**
     * 在此缓冲区中指定的绝对{@code索引}处设置指定的32位整数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setInt(int index, int value);

    /**
     * 以小端字节顺序在此缓冲区中指定的绝对{@code索引}处设置指定的32位整数
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setIntLE(int index, int value);

    /**
     * 在此缓冲区中指定的绝对{@code index}处设置指定的64位长整数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setLong(int index, long value);

    /**
     * 以小端字节顺序在此缓冲区中指定的绝对{@code索引}处设置指定的64位长整数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setLongLE(int index, long value);

    /**
     * 在此缓冲区中指定的绝对{@code索引}处设置指定的2字节UTF-16字符。
     * 指定值的16个高阶位将被忽略。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setChar(int index, int value);

    /**
     * 在此缓冲区中指定的绝对{@code索引}处设置指定的32位浮点数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setFloat(int index, float value);

    /**
     * 以小端字节顺序在此缓冲区中指定的绝对{@code索引}处设置指定的32位浮点数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    public ByteBuf setFloatLE(int index, float value) {
        return setIntLE(index, Float.floatToRawIntBits(value));
    }

    /**
     * 在此缓冲区中指定的绝对{@code索引}处设置指定的64位浮点数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setDouble(int index, double value);

    /**
     * 以小端字节顺序在此缓冲区中指定的绝对{@code索引}处设置指定的64位浮点数。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    public ByteBuf setDoubleLE(int index, double value) {
        return setLongLE(index, Double.doubleToRawLongBits(value));
    }

    /**
     * 从指定的绝对{@code索引}开始，将指定的源缓冲区的数据传输到此缓冲区，直到源缓冲区变得不可读为止。
     * 这个方法与{@link #setBytes(int, ByteBuf, int, int)}基本相同，
     * 除了这个方法增加了源缓冲区的{@code readerIndex}传输的字节数，
     * 而{@link #setBytes(int, ByteBuf, int, int)}不增加。
     * 这个方法不会修改源缓冲区的{@code readerIndex}或{@code writerIndex}(即{@code This})。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + src.readableBytes} is greater than
     *            {@code this.capacity}
     */
    public abstract ByteBuf setBytes(int index, ByteBuf src);

    /**
     * 从指定的绝对{@code索引}开始，将指定的源缓冲区的数据传输到此缓冲区。
     * 这个方法与{@link #setBytes(int, ByteBuf, int, int)}基本相同，
     * 除了这个方法增加了源缓冲区的{@code readerIndex}传输的字节数，而{@link #setBytes(int, ByteBuf, int, int)}不增加。
     * 这个方法不会修改源缓冲区的{@code readerIndex}或{@code writerIndex}(即{@code This})。
     *
     * @param length the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code length} is greater than {@code src.readableBytes}
     */
    public abstract ByteBuf setBytes(int index, ByteBuf src, int length);

    /**
     * 从指定的绝对{@code索引}开始，将指定的源缓冲区的数据传输到此缓冲区。
     * 此方法不会同时修改源(即{@code This})和目标的{@code readerIndex}或{@code writerIndex}。
     *
     * @param srcIndex the first index of the source
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code srcIndex} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code srcIndex + length} is greater than
     *            {@code src.capacity}
     */
    public abstract ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length);

    /**
     * 将指定源数组的数据从指定的绝对{@code索引}开始传输到此缓冲区。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + src.length} is greater than
     *            {@code this.capacity}
     */
    public abstract ByteBuf setBytes(int index, byte[] src);

    /**
     * 将指定源数组的数据从指定的绝对{@code索引}开始传输到此缓冲区。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code srcIndex} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code srcIndex + length} is greater than {@code src.length}
     */
    public abstract ByteBuf setBytes(int index, byte[] src, int srcIndex, int length);

    /**
     * 从指定的绝对{@code索引}开始，将指定的源缓冲区的数据传输到此缓冲区，直到源缓冲区的位置达到其限制为止。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + src.remaining()} is greater than
     *            {@code this.capacity}
     */
    public abstract ByteBuf setBytes(int index, ByteBuffer src);

    /**
     * 从指定的绝对{@code索引}开始，将指定源流的内容传输到此缓冲区。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @param length the number of bytes to transfer
     *
     * @return the actual number of bytes read in from the specified channel.
     *         {@code -1} if the specified channel is closed.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than {@code this.capacity}
     * @throws IOException
     *         if the specified stream threw an exception during I/O
     */
    public abstract int setBytes(int index, InputStream in, int length) throws IOException;

    /**
     * 从指定的绝对{@code索引}开始，将指定源通道的内容传输到此缓冲区。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes read in from the specified channel.
     *         {@code -1} if the specified channel is closed.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than {@code this.capacity}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int setBytes(int index, ScatteringByteChannel in, int length) throws IOException;

    /**
     * 将从给定文件位置开始的指定源通道的内容传输到从指定绝对{@code索引}开始的缓冲区。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。此方法不会修改通道的位置。
     *
     * @param position the file position at which the transfer is to begin
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes read in from the specified channel.
     *         {@code -1} if the specified channel is closed.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than {@code this.capacity}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int setBytes(int index, FileChannel in, long position, int length) throws IOException;

    /**
     * 从指定的绝对{@code索引}开始，用<tt>NUL (0x00)</tt>填充该缓冲区。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @param length the number of <tt>NUL</tt>s to write to the buffer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than {@code this.capacity}
     */
    public abstract ByteBuf setZero(int index, int length);

    /**
     * 在当前的{@code writerIndex}处写入指定的{@link CharSequence}，并增加{@code writerIndex}的写入字节数。
     *
     * @param index on which the sequence should be written
     * @param sequence to write
     * @param charset that should be used.
     * @return the written number of bytes.
     * @throws IndexOutOfBoundsException
     *         if {@code this.writableBytes} is not large enough to write the whole sequence
     */
    public abstract int setCharSequence(int index, CharSequence sequence, Charset charset);

    /**
     * 获取当前{@code readerIndex}处的布尔值，并将该缓冲区中的{@code readerIndex}增加{@code 1}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 1}
     */
    public abstract boolean readBoolean();

    /**
     * 获取当前{@code readerIndex}处的一个字节，并将该缓冲区中的{@code readerIndex}增加{@code 1}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 1}
     */
    public abstract byte  readByte();

    /**
     * 获取当前{@code readerIndex}处的无符号字节，并将该缓冲区中的{@code readerIndex}增加{@code 1}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 1}
     */
    public abstract short readUnsignedByte();

    /**
     * 获取当前{@code readerIndex}处的16位短整数，并在该缓冲区中将{@code readerIndex}增加{@code 2}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    public abstract short readShort();

    /**
     * 以小尾数字节顺序获取当前{@code readerIndex}的16位短整数，并在该缓冲区中将{@code readerIndex}增加{@code 2}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    public abstract short readShortLE();

    /**
     * 获取当前{@code readerIndex}处的无符号16位短整数，并在该缓冲区中将{@code readerIndex}增加{@code 2}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    public abstract int   readUnsignedShort();

    /**
     * 获取当前以小尾数字节顺序{@code readerIndex}为单位的无符号16位短整数，并在该缓冲区中将{@code readerIndex}增加{@code 2}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    public abstract int   readUnsignedShortLE();

    /**
     * 在当前的{@code readerIndex}处获取一个24位的中等整数，并在该缓冲区中将{@code readerIndex}增加{@code 3}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 3}
     */
    public abstract int   readMedium();

    /**
     * 以小尾数字节顺序获取当前{@code readerIndex}的24位中等整数，并在该缓冲区中将{@code readerIndex}增加{@code 3}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 3}
     */
    public abstract int   readMediumLE();

    /**
     * 获取当前{@code readerIndex}处的无符号24位中等整数，并将该缓冲区中的{@code readerIndex}增加{@code 3}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 3}
     */
    public abstract int   readUnsignedMedium();

    /**
     * 以小尾数字节顺序获取当前{@code readerIndex}的无符号24位中等整数，并在该缓冲区中将{@code readerIndex}增加{@code 3}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 3}
     */
    public abstract int   readUnsignedMediumLE();

    /**
     * 获取当前{@code readerIndex}处的32位整数，并在该缓冲区中将{@code readerIndex}增加{@code 4}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    public abstract int   readInt();

    /**
     * 以小尾数字节顺序获取当前{@code readerIndex}的32位整数，并在此缓冲区中将{@code readerIndex}增加{@code 4}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    public abstract int   readIntLE();

    /**
     * 获取当前{@code readerIndex}处的无符号32位整数，并在此缓冲区中将{@code readerIndex}增加{@code 4}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    public abstract long  readUnsignedInt();

    /**
     * 以小尾数字节顺序获取当前{@code readerIndex}的32位无符号整数，并在此缓冲区中将{@code readerIndex}增加{@code 4}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    public abstract long  readUnsignedIntLE();

    /**
     * 获取当前{@code readerIndex}处的一个64位整数，并在该缓冲区中将{@code readerIndex}增加{@code 8}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 8}
     */
    public abstract long  readLong();

    /**
     * 以小端字节顺序获取当前{@code readerIndex}的64位整数，并在该缓冲区中将{@code readerIndex}增加{@code 8}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 8}
     */
    public abstract long  readLongLE();

    /**
     * 获取当前{@code readerIndex}处的2字节UTF-16字符，并在该缓冲区中将{@code readerIndex}增加{@code 2}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    public abstract char  readChar();

    /**
     * 获取当前{@code readerIndex}处的32位浮点数，并将该缓冲区中的{@code readerIndex}增加{@code 4}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    public abstract float readFloat();

    /**
     * 以小端字节顺序获取当前{@code readerIndex}的32位浮点数，并在该缓冲区中将{@code readerIndex}增加{@code 4}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    public float readFloatLE() {
        return Float.intBitsToFloat(readIntLE());
    }

    /**
     * 获取当前{@code readerIndex}处的一个64位浮点数，并在该缓冲区中将{@code readerIndex}增加{@code 8}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 8}
     */
    public abstract double readDouble();

    /**
     * 以小端字节顺序获取当前{@code readerIndex}的64位浮点数，并在该缓冲区中将{@code readerIndex}增加{@code 8}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 8}
     */
    public double readDoubleLE() {
        return Double.longBitsToDouble(readLongLE());
    }

    /**
     * 将该缓冲区的数据传输到从当前的{@code readerIndex}开始的新创建的缓冲区，
     * 并增加{@code readerIndex}传输的字节数(= {@code length})。
     * 返回的缓冲区的{@code readerIndex}和{@code writerIndex}分别是{@code 0}和{@code length}。
     *
     * @param length the number of bytes to transfer
     *
     * @return the newly created buffer which contains the transferred bytes
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract ByteBuf readBytes(int length);

    /**
     * 从当前的{@code readerIndex}开始返回这个缓冲区的子区域的一个新片，并将{@code readerIndex}增加到新片的大小(= {@code length})。
     * <p>
     * 还要注意，这个方法不会调用{@link #retain()}，因此引用计数不会增加。
     *
     * @param length the size of the new slice
     *
     * @return the newly created slice
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract ByteBuf readSlice(int length);

    /**
     * 从当前的{@code readerIndex}开始返回缓冲区子区域的新保留片，并将{@code readerIndex}增加到新片的大小(= {@code length})。
     * <p>
     * 注意，这个方法会返回{@linkplain #retain() retained}缓冲区，而不像{@link #readSlice(int)}那样。
     * 这个方法的行为类似于{@code readSlice(…).retain()}，不同的是，这个方法可能会返回一个产生更少垃圾的缓冲区实现。
     *
     * @param length the size of the new slice
     *
     * @return the newly created slice
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract ByteBuf readRetainedSlice(int length);

    /**
     * 将此缓冲区的数据从当前的{@code readerIndex}传输到指定的目的地，
     * 直到目的地变为不可写，并增加{@code readerIndex}传输的字节数。
     * 这个方法与{@link #readBytes(ByteBuf, int, int)}基本相同，
     * 除了这个方法增加了目标的{@code writerIndex}传输的字节数，
     * 而{@link #readBytes(ByteBuf, int, int)}没有增加。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code dst.writableBytes} is greater than
     *            {@code this.readableBytes}
     */
    public abstract ByteBuf readBytes(ByteBuf dst);

    /**
     * 将此缓冲区的数据传输到从当前的{@code readerIndex}开始的指定目的地，
     * 并将{@code readerIndex}增加传输的字节数(= {@code length})。
     * 这个方法和{@link #readBytes(ByteBuf, int, int)}基本相同，
     * 除了这个方法增加了目标的{@code writerIndex}传输的字节数(= {@code length})，
     * 而{@link #readBytes(ByteBuf, int, int)}没有增加。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes} or
     *         if {@code length} is greater than {@code dst.writableBytes}
     */
    public abstract ByteBuf readBytes(ByteBuf dst, int length);

    /**
     * 将此缓冲区的数据传输到从当前的{@code readerIndex}开始的指定目的地，
     * 并将{@code readerIndex}增加传输的字节数(= {@code length})。
     *
     * @param dstIndex the first index of the destination
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code length} is greater than {@code this.readableBytes}, or
     *         if {@code dstIndex + length} is greater than
     *            {@code dst.capacity}
     */
    public abstract ByteBuf readBytes(ByteBuf dst, int dstIndex, int length);

    /**
     * 将此缓冲区的数据传输到从当前的{@code readerIndex}开始的指定目的地，
     * 并将{@code readerIndex}增加传输字节数(= {@code dst.length})。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code dst.length} is greater than {@code this.readableBytes}
     */
    public abstract ByteBuf readBytes(byte[] dst);

    /**
     * 将此缓冲区的数据传输到从当前的{@code readerIndex}开始的指定目的地，
     * 并将{@code readerIndex}增加传输的字节数(= {@code length})。
     *
     * @param dstIndex the first index of the destination
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code length} is greater than {@code this.readableBytes}, or
     *         if {@code dstIndex + length} is greater than {@code dst.length}
     */
    public abstract ByteBuf readBytes(byte[] dst, int dstIndex, int length);

    /**
     * 将此缓冲区的数据从当前的{@code readerIndex}传输到指定的目的地，
     * 直到目的地的位置达到极限，并将{@code readerIndex}增加传输的字节数。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code dst.remaining()} is greater than
     *            {@code this.readableBytes}
     */
    public abstract ByteBuf readBytes(ByteBuffer dst);

    /**
     * 将此缓冲区的数据传输到从当前{@code readerIndex}开始的指定流。
     *
     * @param length the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     * @throws IOException
     *         if the specified stream threw an exception during I/O
     */
    public abstract ByteBuf readBytes(OutputStream out, int length) throws IOException;

    /**
     * 将此缓冲区的数据传输到从当前{@code readerIndex}开始的指定流。
     *
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes written out to the specified channel
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int readBytes(GatheringByteChannel out, int length) throws IOException;

    /**
     * 获取当前{@code readerIndex}中给定长度的{@link CharSequence}，并将{@code readerIndex}增加给定长度。
     *
     * @param length the length to read
     * @param charset that should be used
     * @return the sequence
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract CharSequence readCharSequence(int length, Charset charset);

    /**
     * 将从当前{@code readerIndex}开始的缓冲区数据传输到从给定文件位置开始的指定通道。
     * 此方法不会修改通道的位置。
     *
     * @param position the file position at which the transfer is to begin
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes written out to the specified channel
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int readBytes(FileChannel out, long position, int length) throws IOException;

    /**
     * 将当前的{@code readerIndex}增加到此缓冲区中指定的{@code length}。
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract ByteBuf skipBytes(int length);

    /**
     * 在当前的{@code writerIndex}处设置指定的布尔值，并在此缓冲区中将{@code writerIndex}增加{@code 1}。
     * 如果{@code这个。writableBytes}小于{@code 1}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeBoolean(boolean value);

    /**
     * 在当前的{@code writerIndex}处设置指定的字节，并在此缓冲区中将{@code writerIndex}增加{@code 1}。
     * 指定值的24个高阶位将被忽略。
     * 如果{@code这个。writableBytes}小于{@code 1}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeByte(int value);

    /**
     * 在当前的{@code writerIndex}处设置指定的16位短整数，并在此缓冲区中将{@code writerIndex}增加{@code 2}。
     * 指定值的16个高阶位将被忽略。
     * 如果{@code这个。writableBytes}小于{@code 2}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeShort(int value);

    /**
     * 在当前的{@code writerIndex}以小尾数字节顺序设置指定的16位短整数，并在该缓冲区中将{@code writerIndex}增加{@code 2}。
     * 指定值的16个高阶位将被忽略。
     * 如果{@code这个。writableBytes}小于{@code 2}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeShortLE(int value);

    /**
     * 在当前的{@code writerIndex}处设置指定的24位中整数，并在此缓冲区中将{@code writerIndex}增加{@code 3}。
     * 如果{@code这个。writableBytes}小于{@code 3}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeMedium(int value);

    /**
     * 以小端字节顺序在当前{@code writerIndex}处设置指定的24位中整数，并在此缓冲区中将{@code writerIndex}增加{@code 3}。
     * 如果{@code这个。writableBytes}小于{@code 3}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeMediumLE(int value);

    /**
     * 在当前的{@code writerIndex}处设置指定的32位整数，并在此缓冲区中将{@code writerIndex}增加{@code 4}。
     * 如果{@code这个。writableBytes}小于{@code 4}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeInt(int value);

    /**
     * 以小尾数字节顺序在当前的{@code writerIndex}处设置指定的32位整数，并在此缓冲区中将{@code writerIndex}增加{@code 4}。
     * 如果{@code这个。writableBytes}小于{@code 4}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeIntLE(int value);

    /**
     * 在当前的{@code writerIndex}处设置指定的64位长整数，并在此缓冲区中将{@code writerIndex}增加{@code 8}。
     * 如果{@code这个。writableBytes}小于{@code 8}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeLong(long value);

    /**
     * 以小端字节顺序在当前{@code writerIndex}处设置指定的64位长整数，并在此缓冲区中将{@code writerIndex}增加{@code 8}。
     * 如果{@code这个。writableBytes}小于{@code 8}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeLongLE(long value);

    /**
     * 在当前的{@code writerIndex}处设置指定的2字节UTF-16字符，并在此缓冲区中将{@code writerIndex}增加{@code 2}。、
     * 指定值的16个高阶位将被忽略。
     * 如果{@code这个。writableBytes}小于{@code 2}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeChar(int value);

    /**
     * 在当前的{@code writerIndex}处设置指定的32位浮点数，并在该缓冲区中将{@code writerIndex}增加{@code 4}。
     * 如果{@code这个。writableBytes}小于{@code 4}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeFloat(float value);

    /**
     * 在当前位置设置指定的32位浮点数
     * {@code writerIndex}以小端字节顺序排列，并在此缓冲区中将{@code writerIndex}增加{@code 4}。
     * 如果{@code这个。writableBytes}小于{@code 4}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public ByteBuf writeFloatLE(float value) {
        return writeIntLE(Float.floatToRawIntBits(value));
    }

    /**
     * 在当前的{@code writerIndex}处设置指定的64位浮点数，并在该缓冲区中将{@code writerIndex}增加{@code 8}。
     * 如果{@code这个。writableBytes}小于{@code 8}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeDouble(double value);

    /**
     * 以小端字节顺序设置当前{@code writerIndex}的指定64位浮点数，并在此缓冲区中将{@code writerIndex}增加{@code 8}。
     * 如果{@code这个。writableBytes}小于{@code 8}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public ByteBuf writeDoubleLE(double value) {
        return writeLongLE(Double.doubleToRawLongBits(value));
    }

    /**
     * 将指定的源缓冲区的数据从当前的{@code writerIndex}传输到这个缓冲区，直到源缓冲区变得不可读，
     * 并将{@code writerIndex}增加传输字节数。这个方法和{@link #writeBytes(ByteBuf, int, int)}
     * 基本相同，除了这个方法增加了源缓冲区的{@code readerIndex}的传输字节数，而{@link #writeBytes
     * (ByteBuf, int, int)}没有增加。
     * 如果{@code这个。writableBytes}小于{@code src。将调用readableBytes}，
     * {@link #ensureWritable(int)}来扩展容量以适应。
     */
    public abstract ByteBuf writeBytes(ByteBuf src);

    /**
     * 从当前的{@code writerIndex}开始，将指定的源缓冲区的数据传输到该缓冲区，
     * 并将{@code writerIndex}增加传输字节数(= {@code length})。
     * 这个方法和{@link #writeBytes(ByteBuf, int, int)}基本相同，
     * 除了这个方法增加了源缓冲区的{@code readerIndex}的传输字节数(= {@code length})，
     * 而{@link #writeBytes(ByteBuf, int, int)}不增加。
     * 如果{@code这个。writableBytes}小于{@code length}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     *
     * @param length the number of bytes to transfer
     * @throws IndexOutOfBoundsException if {@code length} is greater then {@code src.readableBytes}
     */
    public abstract ByteBuf writeBytes(ByteBuf src, int length);

    /**
     * 从当前的{@code writerIndex}开始，将指定的源缓冲区的数据传输到该缓冲区，
     * 并将{@code writerIndex}增加传输字节数(= {@code length})。
     * 如果{@code这个。writableBytes}小于{@code length}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     *
     * @param srcIndex the first index of the source
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code srcIndex} is less than {@code 0}, or
     *         if {@code srcIndex + length} is greater than {@code src.capacity}
     */
    public abstract ByteBuf writeBytes(ByteBuf src, int srcIndex, int length);

    /**
     * 将指定的源数组的数据从当前的{@code writerIndex}开始传输到这个缓冲区，
     * 并将{@code writerIndex}增加传输字节数(= {@code src.length})。
     * 如果{@code这个。writableBytes}小于{@code src。length}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     */
    public abstract ByteBuf writeBytes(byte[] src);

    /**
     * 将指定的源数组的数据从当前的{@code writerIndex}开始传输到此缓冲区，
     * 并将{@code writerIndex}增加传输字节数(= {@code length})。
     * 如果{@code这个。writableBytes}小于{@code length}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     *
     * @param srcIndex the first index of the source
     * @param length   the number of bytes to transfer
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code srcIndex} is less than {@code 0}, or
     *         if {@code srcIndex + length} is greater than {@code src.length}
     */
    public abstract ByteBuf writeBytes(byte[] src, int srcIndex, int length);

    /**
     * 将指定的源缓冲区的数据传输到这个缓冲区，从当前的{@code writerIndex}开始，
     * 直到源缓冲区的位置达到极限，并将{@code writerIndex}增加传输的字节数。
     * 如果{@code这个。writableBytes}小于{@code src.remaining()}，
     * {@link #ensureWritable(int)}将被调用来扩展容量以适应。
     */
    public abstract ByteBuf writeBytes(ByteBuffer src);

    /**
     * 将指定流的内容传输到这个缓冲区，从当前的{@code writerIndex}开始，并将{@code writerIndex}增加传输的字节数。
     * 如果{@code这个。writableBytes}小于{@code length}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     *
     * @param length the number of bytes to transfer
     *
     * @return the actual number of bytes read in from the specified stream
     *
     * @throws IOException if the specified stream threw an exception during I/O
     */
    public abstract int writeBytes(InputStream in, int length) throws IOException;

    /**
     * 将指定通道的内容传输到这个缓冲区，从当前的{@code writerIndex}开始，并增加{@code writerIndex}传输的字节数。
     * 如果{@code这个。writableBytes}小于{@code length}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     *
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes read in from the specified channel
     *
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int writeBytes(ScatteringByteChannel in, int length) throws IOException;

    /**
     * 将从给定文件位置开始的指定通道的内容传输到从当前的{@code writerIndex}开始的缓冲区，并增加{@code writerIndex}传输的字节数。
     * 此方法不会修改通道的位置。
     * 如果{@code这个。writableBytes}小于{@code length}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     *
     * @param position the file position at which the transfer is to begin
     * @param length the maximum number of bytes to transfer
     *
     * @return the actual number of bytes read in from the specified channel
     *
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    public abstract int writeBytes(FileChannel in, long position, int length) throws IOException;

    /**
     * 从当前的{@code writerIndex}开始用<tt>NUL (0x00)</tt>填充该缓冲区，并将{@code writerIndex}增加到指定的{@code长度}。
     * 如果{@code这个。writableBytes}小于{@code length}， {@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     *
     * @param length the number of <tt>NUL</tt>s to write to the buffer
     */
    public abstract ByteBuf writeZero(int length);

    /**
     * 在当前的{@code writerIndex}处写入指定的{@link CharSequence}，并增加{@code writerIndex}的写入字节数。在这个缓冲区。
     * 如果{@code这个。writableBytes}不够大，不能写整个序列，{@link #ensureWritable(int)}将被调用，试图扩展容量以适应。
     *
     * @param sequence to write
     * @param charset that should be used
     * @return the written number of bytes
     */
    public abstract int writeCharSequence(CharSequence sequence, Charset charset);

    /**
     * 查找此缓冲区中第一个指定的{@code值}。搜索从指定的{@code fromIndex}(包含)到指定的{@code toIndex}(排除)进行。
     * <p>
     * 如果{@code fromIndex}大于{@code toIndex}，搜索将按照从{@code fromIndex}(排他)到{@code toIndex}(包含)的相反顺序执行。
     * <p>
     * 注意，较低的索引总是被包含，较高的索引总是被排除。
     * <p>
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @return the absolute index of the first occurrence if found.
     *         {@code -1} otherwise.
     */
    public abstract int indexOf(int fromIndex, int toIndex, byte value);

    /**
     * 查找此缓冲区中第一个指定的{@code值}。搜索从当前的{@code readerIndex}(包含)进行到当前的{@code writerIndex}(排除)。
     * <p>
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @return the number of bytes between the current {@code readerIndex}
     *         and the first occurrence if found. {@code -1} otherwise.
     */
    public abstract int bytesBefore(byte value);

    /**
     * 查找此缓冲区中第一个指定的{@code值}。搜索从当前的{@code readerIndex}(包含)开始，持续到指定的{@code length}。
     * <p>
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @return the number of bytes between the current {@code readerIndex}
     *         and the first occurrence if found. {@code -1} otherwise.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    public abstract int bytesBefore(int length, byte value);

    /**
     * 也可以使用一个类文字来获取{@code Class}对象的类型（或void）。
     * 请参阅<cite>The Java&trade; Language Specification</cite>的第15.8.2节。例如
     * <p>
     * 本方法不修改该缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @return the number of bytes between the specified {@code index}
     *         and the first occurrence if found. {@code -1} otherwise.
     *
     * @throws IndexOutOfBoundsException
     *         if {@code index + length} is greater than {@code this.capacity}
     */
    public abstract int bytesBefore(int index, int length, byte value);

    /**
     * 使用指定的{@code处理器}按升序遍历此缓冲区的可读字节。
     *
     * @return {@code -1} if the processor iterated to or beyond the end of the readable bytes.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public abstract int forEachByte(ByteProcessor processor);

    /**
     * 使用指定的{@code处理器}按升序遍历此缓冲区的指定区域。(即{@code索引}、{@code(索引+ 1)}、..{@code(索引+长度- 1)}
     *
     * @return {@code -1} if the processor iterated to or beyond the end of the specified area.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public abstract int forEachByte(int index, int length, ByteProcessor processor);

    /**
     * 使用指定的{@code处理器}按降序遍历此缓冲区的可读字节。
     *
     * @return {@code -1} if the processor iterated to or beyond the beginning of the readable bytes.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public abstract int forEachByteDesc(ByteProcessor processor);

    /**
     * 使用指定的{@code处理器}按降序遍历此缓冲区的指定区域。
     * (i.e. {@code (index + length - 1)}, {@code (index + length - 2)}, ... {@code index})
     *
     *
     * @return {@code -1} if the processor iterated to or beyond the beginning of the specified area.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    public abstract int forEachByteDesc(int index, int length, ByteProcessor processor);

    /**
     * 返回该缓冲区可读字节的副本。修改返回缓冲区或此缓冲区的内容根本不会相互影响。
     * This method is identical to {@code buf.copy(buf.readerIndex(), buf.readableBytes())}.
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     */
    public abstract ByteBuf copy();

    /**
     * 返回该缓冲区的子区域的副本。修改返回缓冲区或此缓冲区的内容根本不会相互影响。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     */
    public abstract ByteBuf copy(int index, int length);

    /**
     * 返回该缓冲区可读字节的一个片段。修改返回缓冲区或此缓冲区的内容会影响彼此的内容，同时它们维护单独的索引和标记。
     * 这个方法与{@code buf.slice(buf.readerIndex()， buf.readableBytes())}相同。
     * d此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     * <p>
     * 还要注意，这个方法不会调用{@link #retain()}，因此引用计数不会增加。
     */
    public abstract ByteBuf slice();

    /**
     * 返回该缓冲区可读字节的保留片。修改返回缓冲区或此缓冲区的内容会影响彼此的内容，同时它们维护单独的索引和标记。
     * 这个方法与{@code buf.slice(buf.readerIndex()， buf.readableBytes())}相同。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     * <p>
     * 注意，这个方法会返回{@linkplain #retain() retained}缓冲区，而不像{@link #slice()}那样。
     * 这个方法的行为类似于{@code slice().retain()}，不同的是，这个方法可能会返回一个产生更少垃圾的缓冲区实现。
     */
    public abstract ByteBuf retainedSlice();

    /**
     * 返回该缓冲区子区域的一个片段。修改返回缓冲区或此缓冲区的内容会影响彼此的内容，同时它们维护单独的索引和标记。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     * <p>
     * 还要注意，这个方法不会调用{@link #retain()}，因此引用计数不会增加。
     */
    public abstract ByteBuf slice(int index, int length);

    /**
     * 返回此缓冲区子区域的保留片。修改返回缓冲区或此缓冲区的内容会影响彼此的内容，同时它们维护单独的索引和标记。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     * <p>
     * 注意，这个方法会返回{@linkplain #retain() retained}缓冲区，而不像{@link #slice(int, int)}那样。
     * 这个方法的行为类似于{@code slice(…).retain()}，不同的是，这个方法可能会返回一个产生更少垃圾的缓冲区实现。
     */
    public abstract ByteBuf retainedSlice(int index, int length);

    /**
     * 返回一个缓冲区，该缓冲区共享该缓冲区的整个区域。
     * 修改返回缓冲区或此缓冲区的内容会影响彼此的内容，同时它们维护单独的索引和标记。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     * <p>
     * 读者和作者的标记不会被复制。还要注意，这个方法不会调用{@link #retain()}，因此引用计数不会增加。
     * @return 一个缓冲区，其可读内容相当于由{@link #slice()}返回的缓冲区。
     * 然而，该缓冲区将共享底层缓冲区的容量，因此允许在必要时访问所有底层内容。
     */
    public abstract ByteBuf duplicate();

    /**
     * 返回一个保留的缓冲区，该缓冲区共享该缓冲区的整个区域。
     * 修改返回缓冲区或此缓冲区的内容会影响彼此的内容，同时它们维护单独的索引和标记。
     * 此方法与{@code buf相同。片(0,buf.capacity())}。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     * <p>
     * 注意，这个方法会返回{@linkplain #retain() retained}缓冲区，而不像{@link #slice(int, int)}那样。
     * 这个方法的行为类似于{@code duplicate().retain()}，不同的是，这个方法可能会返回一个产生更少垃圾的缓冲区实现。
     */
    public abstract ByteBuf retainedDuplicate();

    /**
     * 返回包含此缓冲区的NIO {@link ByteBuffer}的最大数目。
     * 注意，{@link #nioBuffers()}或{@link #nioBuffers(int, int)}可能会返回较少的{@link ByteBuffer}。
     *
     * @return {@code -1} 如果此缓冲区没有底层{@link ByteBuffer}。
     *         如果缓冲区至少有一个底层{@link ByteBuffer}，则底层{@link ByteBuffer}的数目。注意，此方法不返回{@code 0}，以免混淆。
     *
     * @see #nioBuffer()
     * @see #nioBuffer(int, int)
     * @see #nioBuffers()
     * @see #nioBuffers(int, int)
     */
    public abstract int nioBufferCount();

    /**
     * 将该缓冲区的可读字节公开为NIO {@link ByteBuffer}。返回的缓冲区共享或包含该缓冲区的复制内容，
     * 而更改返回的NIO缓冲区的位置和限制不会影响该缓冲区的索引和标记。
     * 这个方法与{@code buf.nioBuffer(buf.readerIndex()， buf.readableBytes())}相同。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     * 请注意，如果这个缓冲区是动态缓冲区并且它调整了容量，那么返回的NIO缓冲区将不会看到这个缓冲区的变化。
     *
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     *
     * @see #nioBufferCount()
     * @see #nioBuffers()
     * @see #nioBuffers(int, int)
     */
    public abstract ByteBuffer nioBuffer();

    /**
     * 将此缓冲区的子区域公开为NIO {@link ByteBuffer}。
     * 返回的缓冲区共享或包含该缓冲区的复制内容，而更改返回的NIO缓冲区的位置和限制不会影响该缓冲区的索引和标记。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     * 请注意，如果这个缓冲区是动态缓冲区并且它调整了容量，那么返回的NIO缓冲区将不会看到这个缓冲区的变化。
     *
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     *
     * @see #nioBufferCount()
     * @see #nioBuffers()
     * @see #nioBuffers(int, int)
     */
    public abstract ByteBuffer nioBuffer(int index, int length);

    /**
     * 仅内部使用:公开内部NIO缓冲区。
     */
    public abstract ByteBuffer internalNioBuffer(int index, int length);

    /**
     * 将此缓冲区的可读字节公开为NIO {@link ByteBuffer}的。返回的缓冲区共享或包含该缓冲区的复制内容，
     * 而更改返回的NIO缓冲区的位置和限制不会影响该缓冲区的索引和标记。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     * 请注意，如果这个缓冲区是动态缓冲区并且它调整了容量，那么返回的NIO缓冲区将不会看到这个缓冲区的变化。
     *
     *
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     *
     * @see #nioBufferCount()
     * @see #nioBuffer()
     * @see #nioBuffer(int, int)
     */
    public abstract ByteBuffer[] nioBuffers();

    /**
     * 返回的缓冲区共享或包含复制的该缓冲区的内容，而改变返回的NIO缓冲区的位置和限制不会影响该缓冲区的索引和标记。
     * 此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。请注意，如果这个缓冲区是动态缓冲区并且它调整了容量，
     * 那么返回的NIO缓冲区将不会看到这个缓冲区的变化。
     *
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     *
     * @see #nioBufferCount()
     * @see #nioBuffer()
     * @see #nioBuffer(int, int)
     */
    public abstract ByteBuffer[] nioBuffers(int index, int length);

    /**
     * 当且仅当此缓冲区有后备字节数组时，返回{@code true}。
     * 如果这个方法返回true，您可以安全地调用{@link #array()}和{@link #arrayOffset()}。
     */
    public abstract boolean hasArray();

    /**
     * 返回此缓冲区的后备字节数组。
     *
     * @throws UnsupportedOperationException
     *         if there no accessible backing byte array
     */
    public abstract byte[] array();

    /**
     * 返回此缓冲区的后备字节数组中的第一个字节的偏移量。
     *
     * @throws UnsupportedOperationException
     *         if there no accessible backing byte array
     */
    public abstract int arrayOffset();

    /**
     * 返回{@code true}当且仅当这个缓冲区有一个指向后备数据的低级内存地址的引用。
     */
    public abstract boolean hasMemoryAddress();

    /**
     * 返回指向支持数据的第一个字节的低级内存地址。
     *
     * @throws UnsupportedOperationException
     *         if this buffer does not support accessing the low-level memory address
     */
    public abstract long memoryAddress();

    /**
     * 如果这个{@link ByteBuf}实现由单个内存区域支持，则返回{@code true}。
     * 复合缓冲区实现必须返回false，即使它们当前持有&低于;1组件。
     * 对于返回{@code true}的缓冲区，可以保证成功调用{@link #discardReadBytes()}将增加当前
     * {@code readerIndex}的{@link #maxFastWritableBytes()}的值。
     * <p>
     * This method will return {@code false} by default, and a {@code false} return value does not necessarily
     * mean that the implementation is composite or that it is <i>not</i> backed by a single memory region.
     */
    public boolean isContiguous() {
        return false;
    }

    /**默认情况下，该方法将返回{@code false}，而{@code false}返回值并不一定意味着该实现是复合的，
     * 或者它是<i>而不是</i>由单一内存区域支持的。
     * 将此缓冲区的可读字节解码为具有指定字符集名称的字符串。这个方法与
     * {@code buf.toString(buf.readerIndex()， buf.readableBytes()， charsetName)}
     * 相同。此方法不修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     *
     * @throws UnsupportedCharsetException
     *         if the specified character set name is not supported by the
     *         current VM
     */
    public abstract String toString(Charset charset);

    /**
     * 将此缓冲区的子区域解码为具有指定字符集的字符串。此方法不会修改此缓冲区的{@code readerIndex}或{@code writerIndex}。
     */
    public abstract String toString(int index, int length, Charset charset);

    /**
     * 返回一个哈希码，该哈希码是从该缓冲区的内容计算出来的。
     * 如果有一个字节数组是{@linkplain #equals(Object) =}这个数组，两个数组应该返回相同的值。
     */
    @Override
    public abstract int hashCode();

    /**
     * 确定指定缓冲区的内容是否与此数组的内容相同。“相同的”在这里的意思是:
     * <ul>
     * <li>两个缓冲区的内容大小是相同的</li>
     * <li>两个缓冲区内容的每一个字节都是相同的。</li>
     * </ul>
     * 请注意，它没有比较{@link #readerIndex()}和{@link #writerIndex()}。
     * 该方法还为{@code null}和一个非{@link ByteBuf}类型实例的对象返回{@code false}。
     */
    @Override
    public abstract boolean equals(Object obj);

    /**
     * 将指定缓冲区的内容与此缓冲区的内容进行比较。
     * 对各种语言的字符串比较函数，如{@code strcmp}、{@code memcmp}和
     * {@link String #compareTo(string)}，以相同的方式进行比较。
     */
    @Override
    public abstract int compareTo(ByteBuf buffer);

    /**
     * 返回此缓冲区的字符串表示形式。该方法不一定返回缓冲区的全部内容，但返回关键属性的值，
     * 如{@link #readerIndex()}、{@link #writerIndex()}和{@link #capacity()}。
     */
    @Override
    public abstract String toString();

    @Override
    public abstract ByteBuf retain(int increment);

    @Override
    public abstract ByteBuf retain();

    @Override
    public abstract ByteBuf touch();

    @Override
    public abstract ByteBuf touch(Object hint);

    /**
     * 由{@link AbstractByteBuf#ensureAccessible()}在内部使用，以防止在缓冲区释放后使用它(尽最大努力)。
     */
    boolean isAccessible() {
        return refCnt() != 0;
    }
}
