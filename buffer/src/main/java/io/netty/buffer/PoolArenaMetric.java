/*
 * Copyright 2015 The Netty Project
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

import java.util.List;

/**
 * Expose metrics for an arena.
 */
public interface PoolArenaMetric extends SizeClassesMetric {

    /**
     * 返回此arena所支持的线程缓存的数目。
     */
    int numThreadCaches();

    /**
     * 返回arena的小子页面的数量。
     *
     * @deprecated 小的子页面被合并成小的子页面。
     */
    @Deprecated
    int numTinySubpages();

    /**
     * 返回arena小子页面的数目。
     */
    int numSmallSubpages();

    /**
     * 返回arena的块列表的数量。
     */
    int numChunkLists();

    /**
     * 返回一个不可修改的{@link List}，其中包含{@link PoolSubpageMetric}用于小的子页面。
     *
     * @deprecated Tiny sub-pages have been merged into small sub-pages.
     */
    @Deprecated
    List<PoolSubpageMetric> tinySubpages();

    /**
     * 返回一个不可修改的{@link列表}，该列表为小的子页面保存{@link PoolSubpageMetric}。
     */
    List<PoolSubpageMetric> smallSubpages();

    /**
     * 返回一个不可修改的{@link List}，其中包含{@link PoolChunkListMetric}。
     */
    List<PoolChunkListMetric> chunkLists();

    /**
     * 返回通过arena完成的分配数量。这包括所有大小。
     */
    long numAllocations();

    /**
     * 返回通过arena完成的小分配的数量。
     *
     * @deprecated Tiny allocations have been merged into small allocations.
     */
    @Deprecated
    long numTinyAllocations();

    /**
     * 返回通过arena完成的小分配的数量。
     */
    long numSmallAllocations();

    /**
     * 返回通过arena完成的正常分配的数量。
     */
    long numNormalAllocations();

    /**
     * 返回通过竞技场完成的巨大分配的数量。
     */
    long numHugeAllocations();

    /**
     * 返回通过arena完成的deallocations的数量。这包括所有大小。
     */
    long numDeallocations();

    /**
     * Return the number of tiny deallocations done via the arena.
     *
     * @deprecated Tiny deallocations have been merged into small deallocations.
     */
    @Deprecated
    long numTinyDeallocations();

    /**
     * Return the number of small deallocations done via the arena.
     */
    long numSmallDeallocations();

    /**
     * Return the number of normal deallocations done via the arena.
     */
    long numNormalDeallocations();

    /**
     * Return the number of huge deallocations done via the arena.
     */
    long numHugeDeallocations();

    /**
     * Return the number of currently active allocations.
     */
    long numActiveAllocations();

    /**
     * Return the number of currently active tiny allocations.
     *
     * @deprecated Tiny allocations have been merged into small allocations.
     */
    @Deprecated
    long numActiveTinyAllocations();

    /**
     * Return the number of currently active small allocations.
     */
    long numActiveSmallAllocations();

    /**
     * Return the number of currently active normal allocations.
     */
    long numActiveNormalAllocations();

    /**
     * Return the number of currently active huge allocations.
     */
    long numActiveHugeAllocations();

    /**
     * Return the number of active bytes that are currently allocated by the arena.
     */
    long numActiveBytes();
}
