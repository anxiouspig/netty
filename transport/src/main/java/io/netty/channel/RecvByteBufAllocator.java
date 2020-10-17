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
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.UnstableApi;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * 分配一个新的接收缓冲区，其容量可能大到足以读取所有入站数据，而小到不会浪费空间。
 */
public interface RecvByteBufAllocator {
    /**
     * 创建一个新处理器。处理提供实际操作并保存预测最佳缓冲区容量所需的内部信息。
     */
    Handle newHandle();

    /**
     * @deprecated 使用 {@link ExtendedHandle}.
     */
    @Deprecated
    interface Handle {
        /**
         * 创建一个新的接收缓冲区，该缓冲区的容量可能大到足以读取所有入站数据，并小到不会浪费空间。
         */
        ByteBuf allocate(ByteBufAllocator alloc);

        /**
         * 类似于{@link #allocate(ByteBufAllocator)}，除了它不分配任何东西，只是告诉容量。
         */
        int guess();

        /**
         * 重置所有已经累积的计数器，并建议为下一次读取循环应该读取多少消息/字节。
         * <p>
         * {@link #continueReading()}可以使用它来确定读操作是否应该完成。
         * </p>
         * 这只是一个提示，可能会被实现忽略。
         * @param config 可能影响此对象行为的通道配置。
         */
        void reset(ChannelConfig config);

        /**
         * 增加已为当前读循环读取的消息数。
         * @param numMessages The amount to increment by.
         */
        void incMessagesRead(int numMessages);

        /**
         * 设置为上一次读取操作读取的字节。
         * 这可以用来增加已读取的字节数。
         * @param bytes 前一次读取操作的字节数。如果出现读取错误，这可能是负数。如果看到一个负值，那么下一次调用{@link #lastBytesRead()}时应该返回它。
         *              如果是负值，则表示在这个类外部强制执行的终止条件，而不需要在{@link #continueReading()}中强制执行。
         */
        void lastBytesRead(int bytes);

        /**
         * 获取上一个读取操作的字节数。
         * @return 上一个读操作的字节数。
         */
        int lastBytesRead();

        /**
         * 设置读取操作将(或已)尝试读取的字节数。
         * @param bytes 读取操作将(或已)尝试读取多少字节。
         */
        void attemptedBytesRead(int bytes);

        /**
         * 获取读取操作将(或已)尝试读取的字节数。
         * @return 读取操作将(或已)尝试读取多少字节。
         */
        int attemptedBytesRead();

        /**
         * 确定当前读循环是否应该继续。
         * @return {@code true} 读取循环是否应该继续读取。{@code false}如果读取循环完成。
         */
        boolean continueReading();

        /**
         * 阅读已经完成。
         */
        void readComplete();
    }

    @SuppressWarnings("deprecation")
    @UnstableApi
    interface ExtendedHandle extends Handle {
        /**
         * 与{@link Handle #continueReading()}相同，只是“更多数据”由供应商参数决定。
         * @param maybeMoreDataSupplier supplier决定是否有更多的数据要读取。
         */
        boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier);
    }

    /**
     * 一个{@link Handle}，将所有的调用委托给其他{@link Handle}。
     */
    class DelegatingHandle implements Handle {
        private final Handle delegate;

        public DelegatingHandle(Handle delegate) {
            this.delegate = checkNotNull(delegate, "delegate");
        }

        /**
         * 获取所有方法将被委托给的{@link Handle}。
         * @return 所有方法将被委托给的{@link Handle}。
         */
        protected final Handle delegate() {
            return delegate;
        }

        @Override
        public ByteBuf allocate(ByteBufAllocator alloc) {
            return delegate.allocate(alloc);
        }

        @Override
        public int guess() {
            return delegate.guess();
        }

        @Override
        public void reset(ChannelConfig config) {
            delegate.reset(config);
        }

        @Override
        public void incMessagesRead(int numMessages) {
            delegate.incMessagesRead(numMessages);
        }

        @Override
        public void lastBytesRead(int bytes) {
            delegate.lastBytesRead(bytes);
        }

        @Override
        public int lastBytesRead() {
            return delegate.lastBytesRead();
        }

        @Override
        public boolean continueReading() {
            return delegate.continueReading();
        }

        @Override
        public int attemptedBytesRead() {
            return delegate.attemptedBytesRead();
        }

        @Override
        public void attemptedBytesRead(int bytes) {
            delegate.attemptedBytesRead(bytes);
        }

        @Override
        public void readComplete() {
            delegate.readComplete();
        }
    }
}
