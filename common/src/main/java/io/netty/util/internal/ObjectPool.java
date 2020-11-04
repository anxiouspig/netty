/*
 * Copyright 2019 The Netty Project
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
package io.netty.util.internal;

import io.netty.util.Recycler;

/**
 * 轻量级的对象池。
 *
 * @param <T> 被池对象的类型
 */
public abstract class ObjectPool<T> {

    ObjectPool() { }

    /**
     * 从{@link ObjectPool}获得{@link对象}。
     * 返回的{@link对象}可以通过{@link ObjectCreator#newObject(Handle)}创建，
     * 如果没有准备好重用的池{@link对象}。
     */
    public abstract T get();

    /**
     * 池中的{@link对象}的句柄，当{@link对象池}可以再次重用池中的{@link对象}时，
     * 它将用于通知{@link对象池}。
     * @param <T>
     */
    public interface Handle<T> {
        /**
         * 如果可能的话回收{@link Object}，这样就可以让它准备好被重用。
         */
        void recycle(T self);
    }

    /**
     * 创建一个新对象，该对象引用给定的{@link句柄}，
     * 并在它可以被重用时调用{@link句柄#recycle(Object)}。
     *
     * @param <T> 被池对象的类型
     */
    public interface ObjectCreator<T> {

        /**
         * 创建一个返回一个新的{@link对象}，它可以被使用，并在以后通过{@link句柄#recycle(Object)}回收。
         */
        T newObject(Handle<T> handle);
    }

    /**
     * 创建一个新的{@link ObjectPool}，
     * 它将使用给定的{@link ObjectCreator}创建应该被池化的{@link对象}。
     */
    public static <T> ObjectPool<T> newPool(final ObjectCreator<T> creator) {
        return new RecyclerObjectPool<T>(ObjectUtil.checkNotNull(creator, "creator"));
    }

    private static final class RecyclerObjectPool<T> extends ObjectPool<T> {
        private final Recycler<T> recycler;

        RecyclerObjectPool(final ObjectCreator<T> creator) {
             recycler = new Recycler<T>() {
                @Override
                protected T newObject(Handle<T> handle) {
                    return creator.newObject(handle);
                }
            };
        }

        @Override
        public T get() {
            return recycler.get();
        }
    }
}
