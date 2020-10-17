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

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.AbstractConstant;
import io.netty.util.ConstantPool;
import io.netty.util.internal.ObjectUtil;

import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * 一个{@link ChannelOption}允许以类型安全的方式配置一个{@link ChannelConfig}。
 * 支持哪个{@link ChannelOption}取决于{@link ChannelConfig}的实际实现，也可能取决于它所属的传输的性质。
 *
 * @param <T>   对{@link ChannelOption}有效的值的类型
 */
public class ChannelOption<T> extends AbstractConstant<ChannelOption<T>> {

    // key池，静态属性，全局唯一内容池
    private static final ConstantPool<ChannelOption<Object>> pool = new ConstantPool<ChannelOption<Object>>() {
        @Override
        protected ChannelOption<Object> newConstant(int id, String name) {
            return new ChannelOption<Object>(id, name);
        }
    };

    /**
     * 返回指定名称的{@link ChannelOption}。
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> valueOf(String name) {
        return (ChannelOption<T>) pool.valueOf(name);
    }

    /**
     * {@link #valueOf(String) valueOf(firstNameComponent.getName()+"#"+secondNameComponent)}的快捷方式。
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return (ChannelOption<T>) pool.valueOf(firstNameComponent, secondNameComponent);
    }

    /**
     * 如果给定的{@code name}存在一个{@link ChannelOption}，则返回{@code true}。
     */
    public static boolean exists(String name) {
        return pool.exists(name);
    }

    /**
     * 为给定的{@code name}创建一个新的{@link ChannelOption}，
     * 如果给定的{@code name}存在一个{@link ChannelOption}，则用{@link IllegalArgumentException}失败。
     *
     * @deprecated 使用 {@link #valueOf(String)}.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> newInstance(String name) {
        return (ChannelOption<T>) pool.newInstance(name);
    }

    // 普通ByteBuffer
    public static final ChannelOption<ByteBufAllocator> ALLOCATOR = valueOf("ALLOCATOR"); //分配ByteBuffer
    // 接受全部数据的ByteBuffer
    public static final ChannelOption<RecvByteBufAllocator> RCVBUF_ALLOCATOR = valueOf("RCVBUF_ALLOCATOR");
    // 计算数据大小
    public static final ChannelOption<MessageSizeEstimator> MESSAGE_SIZE_ESTIMATOR = valueOf("MESSAGE_SIZE_ESTIMATOR");
    // 连接超时毫秒
    public static final ChannelOption<Integer> CONNECT_TIMEOUT_MILLIS = valueOf("CONNECT_TIMEOUT_MILLIS");
    /**
     * @deprecated 使用 {@link MaxMessagesRecvByteBufAllocator}
     * 和 {@link MaxMessagesRecvByteBufAllocator#maxMessagesPerRead(int)}.
     */
    @Deprecated
    // 每次阅读最大消息
    public static final ChannelOption<Integer> MAX_MESSAGES_PER_READ = valueOf("MAX_MESSAGES_PER_READ");
    // 写入自旋数
    public static final ChannelOption<Integer> WRITE_SPIN_COUNT = valueOf("WRITE_SPIN_COUNT");
    /**
     * @deprecated 使用 {@link #WRITE_BUFFER_WATER_MARK}
     */
    @Deprecated
    public static final ChannelOption<Integer> WRITE_BUFFER_HIGH_WATER_MARK = valueOf("WRITE_BUFFER_HIGH_WATER_MARK");
    /**
     * @deprecated 使用 {@link #WRITE_BUFFER_WATER_MARK}
     */
    @Deprecated
    public static final ChannelOption<Integer> WRITE_BUFFER_LOW_WATER_MARK = valueOf("WRITE_BUFFER_LOW_WATER_MARK");
    // 写缓冲的水位标记
    public static final ChannelOption<WriteBufferWaterMark> WRITE_BUFFER_WATER_MARK =
            valueOf("WRITE_BUFFER_WATER_MARK");
    // 是否允许半关闭
    public static final ChannelOption<Boolean> ALLOW_HALF_CLOSURE = valueOf("ALLOW_HALF_CLOSURE");
    // 是否允许自动读
    public static final ChannelOption<Boolean> AUTO_READ = valueOf("AUTO_READ");

    /**
     * 如果{@code true}，那么{@link Channel}会在写入失败时立即自动关闭。 默认值是{@code true}。
     */
    public static final ChannelOption<Boolean> AUTO_CLOSE = valueOf("AUTO_CLOSE");
    // 广播
    public static final ChannelOption<Boolean> SO_BROADCAST = valueOf("SO_BROADCAST");
    // 保活
    public static final ChannelOption<Boolean> SO_KEEPALIVE = valueOf("SO_KEEPALIVE");
    // 子buffer
    public static final ChannelOption<Integer> SO_SNDBUF = valueOf("SO_SNDBUF");
    // 接收buffer
    public static final ChannelOption<Integer> SO_RCVBUF = valueOf("SO_RCVBUF");
    // 重新使用地址
    public static final ChannelOption<Boolean> SO_REUSEADDR = valueOf("SO_REUSEADDR");
    //
    public static final ChannelOption<Integer> SO_LINGER = valueOf("SO_LINGER");
    //
    public static final ChannelOption<Integer> SO_BACKLOG = valueOf("SO_BACKLOG");
    // 超时
    public static final ChannelOption<Integer> SO_TIMEOUT = valueOf("SO_TIMEOUT");
    //
    public static final ChannelOption<Integer> IP_TOS = valueOf("IP_TOS");
    //
    public static final ChannelOption<InetAddress> IP_MULTICAST_ADDR = valueOf("IP_MULTICAST_ADDR");
    //
    public static final ChannelOption<NetworkInterface> IP_MULTICAST_IF = valueOf("IP_MULTICAST_IF");
    //
    public static final ChannelOption<Integer> IP_MULTICAST_TTL = valueOf("IP_MULTICAST_TTL");
    //
    public static final ChannelOption<Boolean> IP_MULTICAST_LOOP_DISABLED = valueOf("IP_MULTICAST_LOOP_DISABLED");
    // tcp无延迟
    public static final ChannelOption<Boolean> TCP_NODELAY = valueOf("TCP_NODELAY");

    @Deprecated
    public static final ChannelOption<Boolean> DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION =
            valueOf("DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION");
    // 每个组单个事件执行器
    public static final ChannelOption<Boolean> SINGLE_EVENTEXECUTOR_PER_GROUP =
            valueOf("SINGLE_EVENTEXECUTOR_PER_GROUP");

    /**
     * 用指定的唯一的{@@code name}创建一个新的{@link ChannelOption}。
     */
    private ChannelOption(int id, String name) {
        super(id, name);
    }

    @Deprecated
    protected ChannelOption(String name) {
        this(pool.nextId(), name);
    }

    /**
     * 验证为{@link ChannelOption}设置的值。子类可能会因为特殊的检查而重写它。
     */
    public void validate(T value) {
        ObjectUtil.checkNotNull(value, "value");
    }
}
