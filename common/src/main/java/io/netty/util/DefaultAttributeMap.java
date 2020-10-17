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

import io.netty.util.internal.ObjectUtil;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * 默认的{@link AttributeMap}实现，在修改路径上使用copy-on-write方式时，不会对属性查找产生任何阻塞行为。
 * <br> 属性查找和删除产生{@code O(logn)}最坏情况下的时间。
 */
public class DefaultAttributeMap implements AttributeMap {
    // 原子更新attributes
    private static final AtomicReferenceFieldUpdater<DefaultAttributeMap, DefaultAttribute[]> ATTRIBUTES_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(DefaultAttributeMap.class, DefaultAttribute[].class, "attributes");
    // 空ATTRIBUTES，静态，全局唯一
    private static final DefaultAttribute[] EMPTY_ATTRIBUTES = new DefaultAttribute[0];

    /**
     * 类似于{@code Arrays::binarySearch}，它对这个用例进行了二进制搜索优化，以节省多态调用（在比较器端）和不必要的类检查。
     */
    // 二分搜索法，比循环便利快
    private static int searchAttributeByKey(DefaultAttribute[] sortedAttributes, AttributeKey<?> key) {
        int low = 0; // 低位索引
        int high = sortedAttributes.length - 1; // 高位索引

        while (low <= high) {
            int mid = low + high >>> 1; // 中位数
            DefaultAttribute midVal = sortedAttributes[mid]; // 中间值
            AttributeKey midValKey = midVal.key; // 中间值的key
            if (midValKey == key) { // 如果中间值为key，则返回
                return mid;
            }
            int midValKeyId = midValKey.id(); // 中间值id
            int keyId = key.id();
            assert midValKeyId != keyId;
            boolean searchRight = midValKeyId < keyId; // 搜索的方向
            if (searchRight) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return -(low + 1); // 返回负值代表没查到
    }

    // 插入，排序
    private static void orderedCopyOnInsert(DefaultAttribute[] sortedSrc, int srcLength, DefaultAttribute[] copy,
                                            DefaultAttribute toInsert) {
        // 让我们倒着看，因为根据经验，对于新键，toInsert.key.id()往往更高
        final int id = toInsert.key.id();
        int i;
        for (i = srcLength - 1; i >= 0; i--) { // 从最大索引开始
            DefaultAttribute attribute = sortedSrc[i];
            assert attribute.key.id() != id; // 非重复
            if (attribute.key.id() < id) { // 结束，不用换
                break;
            }
            copy[i + 1] = sortedSrc[i]; // 否则换位置
        }

        copy[i + 1] = toInsert; // 赋值
        final int toCopy = i + 1;
        if (toCopy > 0) { // 前面的元素换位置
            System.arraycopy(sortedSrc, 0, copy, 0, toCopy);
        }
    }

    // 空DefaultAttribute
    private volatile DefaultAttribute[] attributes = EMPTY_ATTRIBUTES;

    @SuppressWarnings("unchecked")
    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) { // 获取值
        ObjectUtil.checkNotNull(key, "key"); // 检查值不为null
        DefaultAttribute newAttribute = null; // 新值
        for (;;) {
            final DefaultAttribute[] attributes = this.attributes;
            // 获取值索引
            final int index = searchAttributeByKey(attributes, key);
            final DefaultAttribute[] newAttributes;
            if (index >= 0) { // 索引存在
                final DefaultAttribute attribute = attributes[index]; // 拿到值
                assert attribute.key() == key;
                if (!attribute.isRemoved()) { // 未移除的话返回值
                    return attribute;
                }
                // 让我们尝试用一个新的属性替换被删除的属性
                if (newAttribute == null) { // 如果被删除了，替换
                    newAttribute = new DefaultAttribute<T>(this, key);
                }
                final int count = attributes.length;
                newAttributes = Arrays.copyOf(attributes, count); // 新数组
                newAttributes[index] = newAttribute; // 赋值
            } else { // 索引不存在
                if (newAttribute == null) {
                    newAttribute = new DefaultAttribute<T>(this, key); // 添加
                }
                final int count = attributes.length;
                // 新的空的
                newAttributes = new DefaultAttribute[count + 1];
                // 添加
                orderedCopyOnInsert(attributes, count, newAttributes, newAttribute);
            }
            if (ATTRIBUTES_UPDATER.compareAndSet(this, attributes, newAttributes)) { // 替换
                return newAttribute;
            }
        }
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) { // 是否有这个值
        ObjectUtil.checkNotNull(key, "key");
        return searchAttributeByKey(attributes, key) >= 0;
    }

    // 如果找到了删除
    private <T> void removeAttributeIfMatch(AttributeKey<T> key, DefaultAttribute<T> value) {
        for (;;) {
            final DefaultAttribute[] attributes = this.attributes;
            final int index = searchAttributeByKey(attributes, key); // 搜索
            if (index < 0) { // 没找到
                return;
            }
            final DefaultAttribute attribute = attributes[index]; // 取值
            assert attribute.key() == key;
            if (attribute != value) {
                return;
            }
            final int count = attributes.length;
            final int newCount = count - 1;
            // 删除
            final DefaultAttribute[] newAttributes =
                    newCount == 0? EMPTY_ATTRIBUTES : new DefaultAttribute[newCount];
            // 按比较索引删除
            System.arraycopy(attributes, 0, newAttributes, 0, index);
            final int remaining = count - index - 1;
            if (remaining > 0) {
                System.arraycopy(attributes, index + 1, newAttributes, index, remaining);
            }
            if (ATTRIBUTES_UPDATER.compareAndSet(this, attributes, newAttributes)) {
                return;
            }
        }
    }

    @SuppressWarnings("serial")
    // 实现Attribute，实现原子操作，线程安全
    private static final class DefaultAttribute<T> extends AtomicReference<T> implements Attribute<T> {
        // 原子更新attributeMap
        private static final AtomicReferenceFieldUpdater<DefaultAttribute, DefaultAttributeMap> MAP_UPDATER =
                AtomicReferenceFieldUpdater.newUpdater(DefaultAttribute.class,
                                                       DefaultAttributeMap.class, "attributeMap");

        private static final long serialVersionUID = -2661411462200283011L;

        private volatile DefaultAttributeMap attributeMap;
        private final AttributeKey<T> key;

        // DefaultAttribute来做操作，而不是DefaultAttributeMap
        DefaultAttribute(DefaultAttributeMap attributeMap, AttributeKey<T> key) {
            this.attributeMap = attributeMap;
            this.key = key;
        }

        @Override
        public AttributeKey<T> key() {
            return key;
        }
        // 移除
        private boolean isRemoved() {
            return attributeMap == null;
        }

        // 这个value是测试类用的，不知道作者有什么想法
        @Override
        public T setIfAbsent(T value) { // 不如
            while (!compareAndSet(null, value)) { // 设置值
                T old = get(); // 获得值
                if (old != null) {
                    return old; // 返回值
                }
            }
            return null;
        }

        @Override
        public T getAndRemove() { // 获得再移除
            final DefaultAttributeMap attributeMap = this.attributeMap;
            // 移除
            final boolean removed = attributeMap != null && MAP_UPDATER.compareAndSet(this, attributeMap, null);
            // 清空引用
            T oldValue = getAndSet(null);
            if (removed) {
                attributeMap.removeAttributeIfMatch(key, this);
            }
            return oldValue; // 返回旧值
        }

        @Override
        public void remove() { // 移除
            final DefaultAttributeMap attributeMap = this.attributeMap;
            final boolean removed = attributeMap != null && MAP_UPDATER.compareAndSet(this, attributeMap, null);
            set(null);
            if (removed) {
                attributeMap.removeAttributeIfMatch(key, this);
            }
        }
    }
}
