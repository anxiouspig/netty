/*
 * Copyright 2016 The Netty Project
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

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * WriteBufferWaterMark用于设置写缓冲区的低水位标志和高水位标志。
 * <p>
 * 如果写入缓冲区中排队的字节数超过了{@linkplain #high - high water mark}，
 * {@link Channel#isWritable()}将开始返回{@code false}。
 * <p>
 * 如果写入缓冲区中排队的字节数超过{@linkplain #high - high water mark}，
 * 然后下降到{@linkplain #low - low water mark}之下，
 * {@link Channel#isWritable()} 将开始返回{@code true}。
 */
public final class WriteBufferWaterMark {
    // 默认低水位标记
    private static final int DEFAULT_LOW_WATER_MARK = 32 * 1024;
    // 默认高水位标记
    private static final int DEFAULT_HIGH_WATER_MARK = 64 * 1024;
    // 单例
    public static final WriteBufferWaterMark DEFAULT =
            new WriteBufferWaterMark(DEFAULT_LOW_WATER_MARK, DEFAULT_HIGH_WATER_MARK, false);

    private final int low;
    private final int high;

    /**
     * 创建一个实例。
     *
     * @param low l写buffer的低水位
     * @param high 写buffer的高水位
     */
    public WriteBufferWaterMark(int low, int high) {
        this(low, high, true);
    }

    /**
     * 为了保持向后兼容性，需要使用此构造函数。
     */
    WriteBufferWaterMark(int low, int high, boolean validate) {
        if (validate) {
            checkPositiveOrZero(low, "low");
            if (high < low) {
                throw new IllegalArgumentException(
                        "write buffer's high water mark cannot be less than " +
                                " low water mark (" + low + "): " +
                                high);
            }
        }
        this.low = low;
        this.high = high;
    }

    /**
     * 返回写缓冲区的低水位标记。
     */
    public int low() {
        return low;
    }

    /**
     * 返回写缓冲区的高点标记。
     */
    public int high() {
        return high;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(55)
            .append("WriteBufferWaterMark(low: ")
            .append(low)
            .append(", high: ")
            .append(high)
            .append(")");
        return builder.toString();
    }

}
