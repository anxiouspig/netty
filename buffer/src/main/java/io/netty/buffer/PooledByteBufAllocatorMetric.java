/*
 * Copyright 2017 The Netty Project
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
package io.netty.buffer;

import io.netty.util.internal.StringUtil;

import java.util.List;

/**
 * {@link PooledByteBufAllocator}暴露度量方法.
 */
@SuppressWarnings("deprecation")
public final class PooledByteBufAllocatorMetric implements ByteBufAllocatorMetric {

    private final PooledByteBufAllocator allocator;

    PooledByteBufAllocatorMetric(PooledByteBufAllocator allocator) {
        this.allocator = allocator;
    }

    /**
     * 返回场的数量。
     */
    public int numHeapArenas() {
        return allocator.numHeapArenas();
    }

    /**
     * 返回直接场的数量。
     */
    public int numDirectArenas() {
        return allocator.numDirectArenas();
    }

    /**
     * 返回此池提供的所有堆{@link PoolArenaMetric}的{@link List}。
     */
    public List<PoolArenaMetric> heapArenas() {
        return allocator.heapArenas();
    }

    /**
     * 返回此池提供的所有直接{@link PoolArenaMetric}的{@link List}。
     */
    public List<PoolArenaMetric> directArenas() {
        return allocator.directArenas();
    }

    /**
     * 返回此{@link PooledByteBufAllocator}所使用的线程本地缓存数。
     */
    public int numThreadLocalCaches() {
        return allocator.numThreadLocalCaches();
    }

    /**
     * 返回小缓存的大小。
     *
     * @deprecated Tiny caches have been merged into small caches.
     */
    @Deprecated
    public int tinyCacheSize() {
        return allocator.tinyCacheSize();
    }

    /**
     * 返回小缓存的大小。
     */
    public int smallCacheSize() {
        return allocator.smallCacheSize();
    }

    /**
     * 返回正常缓存的大小。
     */
    public int normalCacheSize() {
        return allocator.normalCacheSize();
    }

    /**
     * 返回场的块大小。
     */
    public int chunkSize() {
        return allocator.chunkSize();
    }

    @Override
    public long usedHeapMemory() {
        return allocator.usedHeapMemory();
    }

    @Override
    public long usedDirectMemory() {
        return allocator.usedDirectMemory();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append(StringUtil.simpleClassName(this))
                .append("(usedHeapMemory: ").append(usedHeapMemory())
                .append("; usedDirectMemory: ").append(usedDirectMemory())
                .append("; numHeapArenas: ").append(numHeapArenas())
                .append("; numDirectArenas: ").append(numDirectArenas())
                .append("; smallCacheSize: ").append(smallCacheSize())
                .append("; normalCacheSize: ").append(normalCacheSize())
                .append("; numThreadLocalCaches: ").append(numThreadLocalCaches())
                .append("; chunkSize: ").append(chunkSize()).append(')');
        return sb.toString();
    }
}
