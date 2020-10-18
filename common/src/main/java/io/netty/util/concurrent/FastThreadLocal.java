/*
 * Copyright 2014 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 一个 {@link ThreadLocal} 的特殊变体， 当从 {@link FastThreadLocalThread} 访问时会有更高的访问性能.
 * <p>
 * 内部的, 一个 {@link FastThreadLocal} 使用一个数组内容索引, 而非使用hash表,
 * 去查找变量.  虽然看起来很微妙, 这比hash表会产生些许性能优势
 * 当频繁获取时很有用.
 * </p><p>
 * 利用这个线程局部变量, 你的线程必须是一个 {@link FastThreadLocalThread} 或它的子类行.
 * 默认情况下, {@link DefaultThreadFactory} 创建的所有线程都是 {@link FastThreadLocalThread} .
 * </p><p>
 * 注意，线程的快速方法只可能继承 {@link FastThreadLocalThread}, 因为它要求
 * 一个特定的字段来存储特定的状态.  任何其他种类的线程访问都会落到常规的
 * {@link ThreadLocal}.
 * </p>
 *
 * @param <V> 线程本地变量类型
 * @see ThreadLocal
 */
public class FastThreadLocal<V> {

    // 线程id自增，保存的事这个FastThreadLocal的id，全局唯一
    private static final int variablesToRemoveIndex = InternalThreadLocalMap.nextVariableIndex();

    /**
     * 移除所有 {@link FastThreadLocal} 绑定到此线程的变量.
     * 这个操作在容器环境下很有用, 你不想要把线程局部变量放在你不管理的线程。
     */
    public static void removeAll() {
        // 拿到ThreadLocal的值
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap == null) {
            return;
        }

        try {
            // 取本ThreadLocal的参数
            Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
            // 若参数不为null并且不为unset，则有值
            if (v != null && v != InternalThreadLocalMap.UNSET) {
                // v是Set<FastThreadLocal<?>>类型
                @SuppressWarnings("unchecked")
                        Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
                // set转数组
                FastThreadLocal<?>[] variablesToRemoveArray =
                        variablesToRemove.toArray(new FastThreadLocal[0]);
                // 遍历删除
                for (FastThreadLocal<?> tlv : variablesToRemoveArray) {
                    tlv.remove(threadLocalMap);
                }
            }
        } finally {
            // 最后移除本地ThreadLocal
            InternalThreadLocalMap.remove();
        }
    }

    /**
     * 返回当前线程的本地变量数量
     */
    public static int size() {
        // 取本线程的threadLocalMap
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap == null) {
            return 0;
        } else {
            return threadLocalMap.size();
        }
    }

    /**
     * 销毁这个数据结构，保存所有的从 非{@link FastThreadLocalThread}s. 中保存的 {@link FastThreadLocal} 变量
     * 当你处于容器环境，这个操作时很有用的,
     * 如果你不想把线程本地变量留在你不能管理的线程中.
     * 当你的应用程序从容器中卸载时，调用此方法。
     */
    public static void destroy() {
        InternalThreadLocalMap.destroy();
    }

    @SuppressWarnings("unchecked")
    // 这个方法是把线程变量重新赋值
    private static void addToVariablesToRemove(InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {
        // 拿到这个FastThreadLocal保存的值
        Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);

        Set<FastThreadLocal<?>> variablesToRemove;
        // 若参数不为null并且不为unset，则有值
        if (v == InternalThreadLocalMap.UNSET || v == null) {
            // 从IdentityHashMap生成set
            variablesToRemove = Collections.newSetFromMap(new IdentityHashMap<FastThreadLocal<?>, Boolean>());
            // 本地变量重新赋值
            threadLocalMap.setIndexedVariable(variablesToRemoveIndex, variablesToRemove);
        } else {
            variablesToRemove = (Set<FastThreadLocal<?>>) v;
        }
        // 把变量加入set里
        variablesToRemove.add(variable);
    }

    // 从set中删除变量
    private static void removeFromVariablesToRemove(
            InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {
        // 拿到这个FastThreadLocal保存的set
        Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
        // 若set为空直接返回
        if (v == InternalThreadLocalMap.UNSET || v == null) {
            return;
        }
        // 否则删除变量
        @SuppressWarnings("unchecked")
        Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
        variablesToRemove.remove(variable);
    }

    private final int index; // 对象id

    public FastThreadLocal() {
        // 线程本地变量保存的id
        index = InternalThreadLocalMap.nextVariableIndex();
    }

    /**
     * 返回当前线程的当前值
     */
    @SuppressWarnings("unchecked")
    public final V get() {
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();
        Object v = threadLocalMap.indexedVariable(index);
        if (v != InternalThreadLocalMap.UNSET) {
            return (V) v;
        }
        return initialize(threadLocalMap);
    }

    /**
     * 如果存在，返回当前线程的值, 否则 {@code null}.
     */
    @SuppressWarnings("unchecked")
    public final V getIfExists() {
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap != null) {
            Object v = threadLocalMap.indexedVariable(index);
            if (v != InternalThreadLocalMap.UNSET) {
                return (V) v;
            }
        }
        return null;
    }

    /**
     * 返回InternalThreadLocalMap的当前值.
     * The specified thread local map must be for the current thread.
     */
    @SuppressWarnings("unchecked")
    public final V get(InternalThreadLocalMap threadLocalMap) {
        Object v = threadLocalMap.indexedVariable(index);
        if (v != InternalThreadLocalMap.UNSET) {
            return (V) v;
        }

        return initialize(threadLocalMap);
    }

    private V initialize(InternalThreadLocalMap threadLocalMap) {
        V v = null;
        try {
            v = initialValue();
        } catch (Exception e) {
            PlatformDependent.throwException(e);
        }

        threadLocalMap.setIndexedVariable(index, v);
        addToVariablesToRemove(threadLocalMap, this);
        return v;
    }

    /**
     * 设置当前线程的值.
     */
    public final void set(V value) {
        if (value != InternalThreadLocalMap.UNSET) {
            InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();
            setKnownNotUnset(threadLocalMap, value);
        } else {
            remove();
        }
    }

    /**
     * 设置特定线程的值. 指定的线程本地映射必须是针对当前线程的。
     */
    public final void set(InternalThreadLocalMap threadLocalMap, V value) {
        if (value != InternalThreadLocalMap.UNSET) {
            setKnownNotUnset(threadLocalMap, value);
        } else {
            remove(threadLocalMap);
        }
    }

    /**
     * @return see {@link InternalThreadLocalMap#setIndexedVariable(int, Object)}.
     */
    private void setKnownNotUnset(InternalThreadLocalMap threadLocalMap, V value) {
        if (threadLocalMap.setIndexedVariable(index, value)) {
            addToVariablesToRemove(threadLocalMap, this);
        }
    }

    /**
     * 如果线程本地变量被设置了，返回 {@code true}.
     */
    public final boolean isSet() {
        return isSet(InternalThreadLocalMap.getIfSet());
    }

    /**
     * 如果线程本地变量被设置了，返回 {@code true}.
     * 指定的线程本地映射必须是针对当前线程的。
     */
    public final boolean isSet(InternalThreadLocalMap threadLocalMap) {
        return threadLocalMap != null && threadLocalMap.isIndexedVariableSet(index);
    }

    /**
     * 设置值为未初始化; 继续调用 get() 将出发调用 initialValue().
     */
    public final void remove() {
        remove(InternalThreadLocalMap.getIfSet());
    }

    /**
     * 对于指定线程本地map，设置值为未初始化;
     * 继续调用 get() 将出发调用 initialValue().
     * 指定的线程本地映射必须是针对当前线程的。
     */
    @SuppressWarnings("unchecked")
    public final void remove(InternalThreadLocalMap threadLocalMap) {
        if (threadLocalMap == null) {
            return;
        }

        Object v = threadLocalMap.removeIndexedVariable(index);
        removeFromVariablesToRemove(threadLocalMap, this);

        if (v != InternalThreadLocalMap.UNSET) {
            try {
                onRemoval((V) v);
            } catch (Exception e) {
                PlatformDependent.throwException(e);
            }
        }
    }

    /**
     * 返回线程本地变量的初始化值.
     */
    protected V initialValue() throws Exception {
        return null;
    }

    /**
     * 当线程本地变量通过调用 {@link #remove()} 移除时调用本方法. 注意 {@link #remove()} 方法
     * 不保证在线程完成时被调用， 这意味着你不能依赖这个方法去
     * 在线程完成情况下清除资源.
     */
    protected void onRemoval(@SuppressWarnings("UnusedParameters") V value) throws Exception {
    }
}
