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
package io.netty.util;

import io.netty.util.internal.InternalThreadLocalMap;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.Map;

/**
 * 提供与{@link Charset}及其相关类相关的各种常见操作和常量的实用程序类。
 */
public final class CharsetUtil {

    /**
     * 16位UTF (UCS转换格式)，其字节顺序由一个可选的字节顺序标记标识
     */
    public static final Charset UTF_16 = Charset.forName("UTF-16");

    /**
     * 16位UTF (UCS转换格式)，字节顺序为big-endian
     */
    public static final Charset UTF_16BE = Charset.forName("UTF-16BE");

    /**
     * 16位UTF (UCS转换格式)，字节顺序是little-endian
     */
    public static final Charset UTF_16LE = Charset.forName("UTF-16LE");

    /**
     * 8位UTF (UCS转换格式)
     */
    public static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * ISO拉丁字母表第1号，即<tt>ISO- Latin -1</tt>
     */
    public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

    /**
     * 7位ASCII，称为ISO646-US或Unicode字符集的基本拉丁块
     */
    public static final Charset US_ASCII = Charset.forName("US-ASCII");

    private static final Charset[] CHARSETS = new Charset[]
            { UTF_16, UTF_16BE, UTF_16LE, UTF_8, ISO_8859_1, US_ASCII };

    public static Charset[] values() {
        return CHARSETS;
    }

    /**
     * @deprecated Use {@link #encoder(Charset)}.
     */
    @Deprecated
    public static CharsetEncoder getEncoder(Charset charset) {
        return encoder(charset);
    }

    /**
     * 为带有指定错误操作的{@link Charset}返回一个新的{@link CharsetEncoder}。
     *
     * @param charset The specified charset
     * @param malformedInputAction 编码器对错误输入的操作
     * @param unmappableCharacterAction 编码器对不可映射字符错误的操作
     * @return The encoder for the specified {@code charset}
     */
    public static CharsetEncoder encoder(Charset charset, CodingErrorAction malformedInputAction,
                                         CodingErrorAction unmappableCharacterAction) {
        checkNotNull(charset, "charset");
        CharsetEncoder e = charset.newEncoder();
        e.onMalformedInput(malformedInputAction).onUnmappableCharacter(unmappableCharacterAction);
        return e;
    }

    /**
     * 为带有指定错误操作的{@link字符集}返回一个新的{@link CharsetEncoder}。
     *
     * @param charset The specified charset
     * @param codingErrorAction The encoder's action for malformed-input and unmappable-character errors
     * @return The encoder for the specified {@code charset}
     */
    public static CharsetEncoder encoder(Charset charset, CodingErrorAction codingErrorAction) {
        return encoder(charset, codingErrorAction, codingErrorAction);
    }

    /**
     * 为指定的{@link Charset}返回缓存的线程本地{@link CharsetEncoder}。
     *
     * @param charset The specified charset
     * @return 指定{@code charset}的编码器
     */
    public static CharsetEncoder encoder(Charset charset) {
        checkNotNull(charset, "charset");

        Map<Charset, CharsetEncoder> map = InternalThreadLocalMap.get().charsetEncoderCache();
        CharsetEncoder e = map.get(charset);
        if (e != null) {
            e.reset().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
            return e;
        }

        e = encoder(charset, CodingErrorAction.REPLACE, CodingErrorAction.REPLACE);
        map.put(charset, e);
        return e;
    }

    /**
     * @deprecated Use {@link #decoder(Charset)}.
     */
    @Deprecated
    public static CharsetDecoder getDecoder(Charset charset) {
        return decoder(charset);
    }

    /**
     * 为带有指定错误操作的{@link Charset}返回一个新的{@link CharsetDecoder}。
     *
     * @param charset The specified charset
     * @param malformedInputAction The decoder's action for malformed-input errors
     * @param unmappableCharacterAction The decoder's action for unmappable-character errors
     * @return The decoder for the specified {@code charset}
     */
    public static CharsetDecoder decoder(Charset charset, CodingErrorAction malformedInputAction,
                                         CodingErrorAction unmappableCharacterAction) {
        checkNotNull(charset, "charset");
        CharsetDecoder d = charset.newDecoder();
        d.onMalformedInput(malformedInputAction).onUnmappableCharacter(unmappableCharacterAction);
        return d;
    }

    /**
     * 为带有指定错误操作的{@link Charset}返回一个新的{@link CharsetDecoder}。
     *
     * @param charset The specified charset
     * @param codingErrorAction The decoder's action for malformed-input and unmappable-character errors
     * @return The decoder for the specified {@code charset}
     */
    public static CharsetDecoder decoder(Charset charset, CodingErrorAction codingErrorAction) {
        return decoder(charset, codingErrorAction, codingErrorAction);
    }

    /**
     * 为指定的{@link字符集}返回缓存的线程本地{@link CharsetDecoder}。
     *
     * @param charset The specified charset
     * @return The decoder for the specified {@code charset}
     */
    public static CharsetDecoder decoder(Charset charset) {
        checkNotNull(charset, "charset");

        Map<Charset, CharsetDecoder> map = InternalThreadLocalMap.get().charsetDecoderCache();
        CharsetDecoder d = map.get(charset);
        if (d != null) {
            d.reset().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
            return d;
        }

        d = decoder(charset, CodingErrorAction.REPLACE, CodingErrorAction.REPLACE);
        map.put(charset, d);
        return d;
    }

    private CharsetUtil() { }
}
