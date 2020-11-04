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

package io.netty.channel;

import java.io.Serializable;

/**
 * 表示{@link Channel}的全局唯一标识符。
 * <p>
 * 该标识符由以下列出的各种来源生成:
 * <ul>
 * <li>MAC地址(EUI-48或EUI-64)或网络适配器，最好是全球唯一的适配器，</li>
 * <li>当前进程ID，</li>
 * <li>{@link System#currentTimeMillis()},</li>
 * <li>{@link System#nanoTime()},</li>
 * <li>一个随机的32位整数，和</li>
 * <li>按顺序递增的32位整数。</li>
 * </ul>
 * </p>
 * <p>
 * 生成的标识符的全局唯一性主要取决于MAC地址和当前进程ID，它们在类加载时以最佳方式自动检测。
 * 如果所有获取它们的尝试都失败，则会记录一条警告消息，并使用随机值。或者，您可以通过系统属性手动指定它们:
 * <ul>
 * <li>{@code io.netty.machineId} - 48(或64)位整数的十六进制表示，可选用冒号或连字符分隔。</li>
 * <li>{@code io.netty.processId} - 0到65535之间的整数</li>
 * </ul>
 * </p>
 */
public interface ChannelId extends Serializable, Comparable<ChannelId> {
    /**
     * 返回{@link ChannelId}的简短但全局非唯一的字符串表示形式。
     */
    String asShortText();

    /**
     * 返回{@link ChannelId}的长但全局唯一的字符串表示形式。
     */
    String asLongText();
}
