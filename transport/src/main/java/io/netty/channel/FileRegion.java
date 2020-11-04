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

import io.netty.util.ReferenceCounted;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

/**
 * 通过支持的{@link Channel}发送的文件的一个区域
 * <a href="http://en.wikipedia.org/wiki/Zero-copy">零拷贝文件传输</a>.
 *
 * <h3>升级你的 JDK / JRE</h3>
 *
 * 在Sun JDK的旧版本以及派生版本中，至少有四个已知的bug。
 * 如果您打算使用零拷贝文件传输，请将JDK升级到1.6.0_18或更高版本。
 * <ul>
 * <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5103988">5103988</a>
 *   - FileChannel.transferTo() should return -1 for EAGAIN instead throws IOException</li>
 * <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6253145">6253145</a>
 *   - FileChannel.transferTo() on Linux fails when going beyond 2GB boundary</li>
 * <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6427312">6427312</a>
 *   - FileChannel.transferTo() throws IOException "system call interrupted"</li>
 * <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6524172">6470086</a>
 *   - FileChannel.transferTo(2147483647, 1, channel) causes "Value too large" exception</li>
 * </ul>
 *
 * <h3>检查你的操作系统和 JDK / JRE</h3>
 *
 * 如果您的操作系统(或JDK / JRE)不支持零拷贝文件传输，发送一个文件{@link FileRegion}可能会失败或产生更差的性能。
 * 例如，发送一个大文件在Windows中就不能很好地工作。
 *
 * <h3>并不是所有的传输都支持它</h3>
 */
public interface FileRegion extends ReferenceCounted {

    /**
     * 返回传输开始的文件中的偏移量。
     */
    long position();

    /**
     * 返回已经传输的字节。
     *
     * @deprecated Use {@link #transferred()} instead.
     */
    @Deprecated
    long transfered();

    /**
     * 返回已经传输的字节。
     */
    long transferred();

    /**
     * 返回要传输的字节数。
     */
    long count();

    /**
     * 将此文件区域的内容传输到指定的通道。
     *
     * @param target    the destination of the transfer
     * @param position  the relative offset of the file where the transfer
     *                  begins from.  For example, <tt>0</tt> will make the
     *                  transfer start from {@link #position()}th byte and
     *                  <tt>{@link #count()} - 1</tt> will make the last
     *                  byte of the region transferred.
     */
    long transferTo(WritableByteChannel target, long position) throws IOException;

    @Override
    FileRegion retain();

    @Override
    FileRegion retain(int increment);

    @Override
    FileRegion touch();

    @Override
    FileRegion touch(Object hint);
}
