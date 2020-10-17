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
 * 可以用来从{@link AttributeMap}中访问{@link Attribute}的key。
 * 请注意，不可能有多个同名的键。
 *
 * @param <T>   可以通过这个{@link AttributeKey}来访问的{@link Attribute}的类型。
 */
@SuppressWarnings("UnusedDeclaration") // 'T' is used only at compile time
public final class AttributeKey<T> extends AbstractConstant<AttributeKey<T>> {

    // key池，静态属性，全局唯一内容池
    private static final ConstantPool<AttributeKey<Object>> pool = new ConstantPool<AttributeKey<Object>>() {
        @Override
        protected AttributeKey<Object> newConstant(int id, String name) {
            return new AttributeKey<Object>(id, name);
        }
    };

    /**
     * 返回具有指定的{@code name}的{@link AttributeKey}的单例实例。
     */
    @SuppressWarnings("unchecked")
    public static <T> AttributeKey<T> valueOf(String name) {
        return (AttributeKey<T>) pool.valueOf(name);
    }

    /**
     * 如果给定的{@code name}存在{@link AttributeKey}，则返回{@code true}。
     */
    public static boolean exists(String name) {
        return pool.exists(name);
    }

    /**
     * 为给定的{@code name}创建一个新的{@link AttributeKey}，
     * 或者如果给定的{@code name}存在一个{@link AttributeKey}，则使用{@link IllegalArgumentException}失败。
     */
    @SuppressWarnings("unchecked")
    public static <T> AttributeKey<T> newInstance(String name) {
        return (AttributeKey<T>) pool.newInstance(name);
    }

    @SuppressWarnings("unchecked")
    public static <T> AttributeKey<T> valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return (AttributeKey<T>) pool.valueOf(firstNameComponent, secondNameComponent);
    }

    private AttributeKey(int id, String name) {
        super(id, name);
    }
}
