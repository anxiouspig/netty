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

/**
 * 一个允许存储值引用的属性。它可以原子地更新，所以是线程安全的。
 *
 * @param <T>   它所拥有的值的类型。
 */
public interface Attribute<T> {

    /**
     * 返回此属性的键。
     */
    AttributeKey<T> key();

    /**
     * 返回当前值，可能是{@code null}。
     */
    T get();

    /**
     * 设定值
     */
    void set(T value);

    /**
     *  原子化地设置为给定值，并返回旧值，如果之前没有设置，则可能是{@code null}。
     */
    T getAndSet(T value);

    /**
     *  如果这个{@link Attribute}的值是{@code null}，则原子地设置为给定值。
     *  如果无法设置值，因为它包含一个值，它将只返回当前值。
     */
    T setIfAbsent(T value);

    /**
     * 从{@link AttributeMap}中删除这个属性，并返回旧值。随后的{@link #get()}调用将返回{@code null}。
     *
     * 如果你只想返回旧的值，并清除{@link Attribute}，同时仍将其保留在{@link AttributeMap}中，
     * 则使用{@link #getAndSet(Object)}，值为{@code null}。
     *
     * <p>
     * 要注意，即使你调用了这个方法，另一个通过{@link AttributeMap#attr(AttributeKey)}获得了对这个{@link Attribute}的引用的线程仍然会对同一个实例进行操作。
     * 也就是说如果现在另一个线程甚至同一个线程以后会再次调用{@link AttributeMap#attr(AttributeKey)}，
     * 就会创建一个新的{@link Attribute}实例，所以和之前被删除的实例是不一样的。由于 当你调用{@link #remove()}或{@link #getAndRemove()}时，应特别注意。
     *
     * @deprecated 请考虑使用{@link #getAndSet(Object)}（值为{@code null}）。
     */
    @Deprecated
    T getAndRemove();

    /**
     * 如果当前值==预期值，则原子式地将其设置为给定的更新值，如果设置成功则返回{@code true}，否则返回{@code false}。
     * 如果设置成功，则返回{@code true}，否则返回{@code false}。
     */
    boolean compareAndSet(T oldValue, T newValue);

    /**
     * 从{@link AttributeMap}中删除这个属性。随后的{@link #get()}调用将返回@{code null}。
     *
     * 如果你只想移除该值并清除{@link Attribute}，
     * 同时仍将其保留在{@link AttributeMap}中，则使用{@link #set(Object)}，值为{@code null}。
     *
     * <p>
     * 要注意，即使你调用了这个方法，另一个通过{@link AttributeMap#attr(AttributeKey)}获得了对这个{@link Attribute}的引用的线程仍然会对同一个实例进行操作。
     * 也就是说如果现在另一个线程甚至同一个线程以后会再次调用{@link AttributeMap#attr(AttributeKey)}，
     * 就会创建一个新的{@link Attribute}实例，所以和之前被删除的实例是不一样的。正因为如此，当你调用{@link #remove()}或{@link #getAndRemove()}时，应该特别注意。
     *
     * @deprecated 请考虑使用{@link #set(Object)}（值为{@code null}）。
     */
    @Deprecated
    void remove();
}
