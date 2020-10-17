/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.util.internal;

import java.util.Collection;

/**
 * 实用方法
 */
public final class ObjectUtil {

    private ObjectUtil() {
    }

    /**
     * 检查给定参数是否为null. 如果是null, 抛出 {@link NullPointerException}.
     * 否则, 返回参数.
     */
    public static <T> T checkNotNull(T arg, String text) {
        if (arg == null) {
            throw new NullPointerException(text);
        }
        return arg;
    }

    /**
     * 检查给定参数是否是正值. 如果不是, 抛出 {@link IllegalArgumentException}.
     * 否则, 返回参数.
     */
    public static int checkPositive(int i, String name) {
        if (i <= 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: > 0)");
        }
        return i;
    }

    /**
     * 检查给定参数是否是正值. 如果不是, 抛出 {@link IllegalArgumentException}.
     * 否则, 返回参数.
     */
    public static long checkPositive(long l, String name) {
        if (l <= 0) {
            throw new IllegalArgumentException(name + ": " + l + " (expected: > 0)");
        }
        return l;
    }

    /**
     * 检查给定参数是否是正值或0. 如果不是 , 抛出 {@link IllegalArgumentException}.
     * 否则, 返回参数.
     */
    public static int checkPositiveOrZero(int i, String name) {
        if (i < 0) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: >= 0)");
        }
        return i;
    }

    /**
     * 检查给定参数是否是正值或0. 如果不是 , 抛出 {@link IllegalArgumentException}.
     * 否则, 返回参数.
     */
    public static long checkPositiveOrZero(long l, String name) {
        if (l < 0) {
            throw new IllegalArgumentException(name + ": " + l + " (expected: >= 0)");
        }
        return l;
    }

    /**
     * 检查给定参数是否为null或空
     * 如果是, 抛出 {@link NullPointerException} 或 {@link IllegalArgumentException}.
     * 否则, 返回参数.
     */
    public static <T> T[] checkNonEmpty(T[] array, String name) {
        checkNotNull(array, name);
        checkPositive(array.length, name + ".length");
        return array;
    }

    /**
     * 检查给定参数是否为null或空
     * 如果是, 抛出 {@link NullPointerException} 或 {@link IllegalArgumentException}.
     * 否则, 返回参数.
     */
    public static <T extends Collection<?>> T checkNonEmpty(T collection, String name) {
        checkNotNull(collection, name);
        checkPositive(collection.size(), name + ".size");
        return collection;
    }

    /**
     * 使用一个默认值将可能为空的Integer解析为int
     * @param wrapper the wrapper
     * @param defaultValue the default value
     * @return the primitive value
     */
    public static int intValue(Integer wrapper, int defaultValue) {
        return wrapper != null ? wrapper : defaultValue;
    }

    /**
     * 使用一个默认值将可能为空的Long解析为long
     * @param wrapper the wrapper
     * @param defaultValue the default value
     * @return the primitive value
     */
    public static long longValue(Long wrapper, long defaultValue) {
        return wrapper != null ? wrapper : defaultValue;
    }
}
