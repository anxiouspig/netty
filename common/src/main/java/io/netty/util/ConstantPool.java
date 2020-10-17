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

package io.netty.util;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一个由{@link Constant}组成的池子。
 *
 * @param <T> 常数型
 */
public abstract class ConstantPool<T extends Constant<T>> {

    // 保存常量和值
    private final ConcurrentMap<String, T> constants = PlatformDependent.newConcurrentHashMap();

    private final AtomicInteger nextId = new AtomicInteger(1); // 原子int，初始化为1

    /**
     * {@link #valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent)}.
     */
    // 返回Constant的名
    public T valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return valueOf(
                ObjectUtil.checkNotNull(firstNameComponent, "firstNameComponent").getName() +
                '#' +
                ObjectUtil.checkNotNull(secondNameComponent, "secondNameComponent"));
    }

    /**
     * 返回分配给指定的{@code name}的{@link Constant}。
     * 如果没有这样的{@link Constant}，将创建一个新的并返回。
     * 一旦创建了，后续使用相同的{@code name}的调用将总是返回之前创建的那个（即单体）。
     *
     * @param name the name of the {@link Constant}
     */
    public T valueOf(String name) {
        checkNotNullAndNotEmpty(name);
        return getOrCreate(name);
    }

    /**
     * 通过名称获取现有的常量或创建新的常量（如果不存在）。线程安全
     *
     * @param name the name of the {@link Constant}
     */
    private T getOrCreate(String name) {
        T constant = constants.get(name); // 返回值
        if (constant == null) { // 值不存在的话，创建，值的key是原子id
            final T tempConstant = newConstant(nextId(), name); // 子类实现
            constant = constants.putIfAbsent(name, tempConstant); // 如果不存在则put
            if (constant == null) { // 返回旧值
                return tempConstant;
            }
        }

        return constant;
    }

    /**
     * 如果给定的{@code name}存在一个{@link AttributeKey}，则返回{@code true}。
     */
    public boolean exists(String name) {
        checkNotNullAndNotEmpty(name);
        return constants.containsKey(name);
    }

    /**
     * 为给定的{@code name}创建一个新的{@link Constant}，如果给定的{@code name}存在一个{@link Constant}，
     * 则用一个{@link IllegalArgumentException}失败。
     */
    public T newInstance(String name) {
        checkNotNullAndNotEmpty(name);
        return createOrThrow(name);
    }

    /**
     * 通过名称创建常量或抛出异常。线程安全
     *
     * @param name the name of the {@link Constant}
     */
    private T createOrThrow(String name) {
        T constant = constants.get(name);
        if (constant == null) {
            final T tempConstant = newConstant(nextId(), name);
            constant = constants.putIfAbsent(name, tempConstant);
            if (constant == null) {
                return tempConstant;
            }
        }

        throw new IllegalArgumentException(String.format("'%s' is already in use", name));
    }

    private static String checkNotNullAndNotEmpty(String name) {
        ObjectUtil.checkNotNull(name, "name");

        if (name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }

        return name;
    }

    // 实现 AttributeKey、Signal、ChannelOption
    protected abstract T newConstant(int id, String name);

    // 这方法太多余了，不应该给外部调用
    @Deprecated
    public final int nextId() {
        return nextId.getAndIncrement();
    }
}
