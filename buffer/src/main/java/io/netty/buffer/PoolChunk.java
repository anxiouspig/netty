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
package io.netty.buffer;

import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.PriorityQueue;

/**
 * 从PoolChunk分配PageRun/PoolSubpage的算法描述
 *
 * 注释: 以下术语对于理解代码很重要
 * > page  - 一page是内存块可以分配的最小单元
 * > run   - run是页面的集合
 * > chunk - 一个chunk是runs的集合
 * > 在这个代码 chunkSize = maxPages * pageSize 块大小等于最大页数量乘以
 *
 * 首先我们分配一个字节数组的大小 = chunkSize
 * 每当一个给定大小的 ByteBuf 需要去创建我们搜索的第一个位置的字节数组，
 * 有足够的空间去容纳请求的大小，并返回一个 (long) 处理编码这个偏移量信息,
 * (这个内存段被标记为保留， 所以总是恰好的被一个 ByteBuf 使用，或没有使用)
 *
 * 为了简单起见，所有大小都按照 {@link PoolArena#size2SizeIdx(int)} 方法.（将请求大小标准化到最接近的大小类。）
 * 这确保，当我们请求的内存段大小 > 标准化的页大小时，
 * 等于 {@link SizeClasses} 中最近的大小.
 *
 *
 *  一个块有如下布局：
 *
 *     /-----------------\
 *     | run             |
 *     |                 |
 *     |                 |
 *     |-----------------|
 *     | run             |
 *     |                 |
 *     |-----------------|
 *     | unalloctated    |
 *     | (freed)         |
 *     |                 |
 *     |-----------------|
 *     | subpage         |
 *     |-----------------|
 *     | unallocated     |
 *     | (freed)         |
 *     | ...             |
 *     | ...             |
 *     | ...             |
 *     |                 |
 *     |                 |
 *     |                 |
 *     \-----------------/
 *
 *
 * 处理:
 * -------
 * 一个处理是一个long型, 一个run的比特结构类似:
 *
 * oooooooo ooooooos ssssssss ssssssue bbbbbbbb bbbbbbbb bbbbbbbb bbbbbbbb
 *
 * o: runOffset (块中的页偏移量), 15bit
 * s: size (页的数量) of this run, 15bit
 * u: 是否使用?, 1bit
 * e: 是否是子页?, 1bit
 * b: 子页的bitmapIdx, 如果不是子页则为0, 32bit
 *
 * runsAvailMap:
 * ------
 * 一个 map 管理的所有 runs (使用或不在使用).
 * 对于每个 run, 第一个 runOffset 和最后一个 runOffset 存储在 runsAvailMap.
 * key: runOffset
 * value: handle
 *
 * runsAvail:
 * ----------
 * 一个数组 {@link PriorityQueue}.（堆）
 * 每个队列管理着相同大小的 runs.
 * Runs 通过 offset 排序, 这样我们可以用较小的偏移量分配 runs.
 *
 *
 * 算法:
 * ----------
 *
 *   如同我们分配 runs, 我们更新存储在 runsAvailMap 中的值， 并且在 runsAvail 中维护属性.
 *
 * 初始化 -
 *  在开始，我们存储初始化的整个 chunk的 run.
 *  这个初始化的 run:
 *  runOffset = 0 // 偏移量
 *  size = chunkSize // 大小
 *  isUsed = no // 未使用
 *  isSubpage = no // 是否子页
 *  bitmapIdx = 0 // 指针
 *
 *
 * 算法: [allocateRun(size)] // 分配run
 * ----------
 * 1) 根据大小，使用runsAvails查找第一个 avail run。
 * 2) 如果 run 的页大小比分配给它的请求页大, 存储在run的尾部之后使用
 *
 * 算法: [allocateSubpage(size)] // 分配子页
 * ----------
 * 1) 根据大小查找一个不满的子页.
 *    如果已经存在了就返回, 否则分配一个新的 PoolSubpage ，并且调用 init()
 *    注意，这个子页对象被添加到 subpagesPool 在 PoolArena 当我们 init() 它
 * 2) 调用 subpage.allocate()
 *
 * 算法: [free(handle, length, nioBuffer)]
 * ----------
 * 1) 如果是一个子页, 将 slab 返回给 subpage
 * 2) 如果子页未被使用或是一个 run, 那么开始释放这个 run
 * 3) 合并连续的 avail runs
 * 4) 保存合并的 run
 *
 */
final class PoolChunk<T> implements PoolChunkMetric {
    // oooooooo ooooooos ssssssss ssssssue bbbbbbbb bbbbbbbb bbbbbbbb bbbbbbbb
    private static final int OFFSET_BIT_LENGTH = 15;
    private static final int SIZE_BIT_LENGTH = 15; // 比特长度
    private static final int INUSED_BIT_LENGTH = 1; // 使用比特长度
    private static final int SUBPAGE_BIT_LENGTH = 1;  // 子页比特长度
    private static final int BITMAP_IDX_BIT_LENGTH = 32; //

    static final int IS_SUBPAGE_SHIFT = BITMAP_IDX_BIT_LENGTH;
    static final int IS_USED_SHIFT = SUBPAGE_BIT_LENGTH + IS_SUBPAGE_SHIFT;
    static final int SIZE_SHIFT = INUSED_BIT_LENGTH + IS_USED_SHIFT;
    static final int RUN_OFFSET_SHIFT = SIZE_BIT_LENGTH + SIZE_SHIFT;

    final PoolArena<T> arena;
    final T memory;
    final boolean unpooled;
    final int offset;

    /**
     * store the first page and last page of each avail run
     */
    private final IntObjectMap<Long> runsAvailMap;

    /**
     * manage all avail runs
     */
    private final PriorityQueue<Long>[] runsAvail;

    /**
     * manage all subpages in this chunk
     */
    private final PoolSubpage<T>[] subpages;

    private final int pageSize;
    private final int pageShifts;
    private final int chunkSize;

    // Use as cache for ByteBuffer created from the memory. These are just duplicates and so are only a container
    // around the memory itself. These are often needed for operations within the Pooled*ByteBuf and so
    // may produce extra GC, which can be greatly reduced by caching the duplicates.
    //
    // This may be null if the PoolChunk is unpooled as pooling the ByteBuffer instances does not make any sense here.
    private final Deque<ByteBuffer> cachedNioBuffers;

    int freeBytes;

    PoolChunkList<T> parent;
    PoolChunk<T> prev;
    PoolChunk<T> next;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    @SuppressWarnings("unchecked")
    PoolChunk(PoolArena<T> arena, T memory, int pageSize, int pageShifts, int chunkSize, int maxPageIdx, int offset) {
        unpooled = false;
        this.arena = arena;
        this.memory = memory; // 2m的ByteBuffer
        this.pageSize = pageSize; // 1kb
        this.pageShifts = pageShifts; // 13
        this.chunkSize = chunkSize; // 2m
        this.offset = offset; // 0
        freeBytes = chunkSize; // 空闲字节2m

        runsAvail = newRunsAvailqueueArray(maxPageIdx); // 40
        runsAvailMap = new IntObjectHashMap<Long>();
        subpages = new PoolSubpage[chunkSize >> pageShifts]; // 2048

        //insert initial run, offset = 0, pages = chunkSize / pageSize
        int pages = chunkSize >> pageShifts;
        long initHandle = (long) pages << SIZE_SHIFT;
        insertAvailRun(0, pages, initHandle);

        cachedNioBuffers = new ArrayDeque<ByteBuffer>(8);
    }

    /** Creates a special chunk that is not pooled. */
    PoolChunk(PoolArena<T> arena, T memory, int size, int offset) {
        unpooled = true;
        this.arena = arena;
        this.memory = memory;
        this.offset = offset;
        pageSize = 0;
        pageShifts = 0;
        runsAvailMap = null;
        runsAvail = null;
        subpages = null;
        chunkSize = size;
        cachedNioBuffers = null;
    }

    @SuppressWarnings("unchecked")
    private static PriorityQueue<Long>[] newRunsAvailqueueArray(int size) {
        PriorityQueue<Long>[] queueArray = new PriorityQueue[size]; // 40
        for (int i = 0; i < queueArray.length; i++) {
            queueArray[i] = new PriorityQueue<Long>();
        }
        return queueArray;
    }

    private void insertAvailRun(int runOffset, int pages, Long handle) {
        int pageIdxFloor = arena.pages2pageIdxFloor(pages);
        PriorityQueue<Long> queue = runsAvail[pageIdxFloor];
        queue.offer(handle);

        //insert first page of run
        insertAvailRun0(runOffset, handle);
        if (pages > 1) {
            //insert last page of run
            insertAvailRun0(lastPage(runOffset, pages), handle);
        }
    }

    private void insertAvailRun0(int runOffset, Long handle) {
        Long pre = runsAvailMap.put(runOffset, handle);
        assert pre == null;
    }

    private void removeAvailRun(long handle) {
        int pageIdxFloor = arena.pages2pageIdxFloor(runPages(handle));
        PriorityQueue<Long> queue = runsAvail[pageIdxFloor];
        removeAvailRun(queue, handle);
    }

    private void removeAvailRun(PriorityQueue<Long> queue, long handle) {
        queue.remove(handle);

        int runOffset = runOffset(handle);
        int pages = runPages(handle);
        //remove first page of run
        runsAvailMap.remove(runOffset);
        if (pages > 1) {
            //remove last page of run
            runsAvailMap.remove(lastPage(runOffset, pages));
        }
    }

    private static int lastPage(int runOffset, int pages) {
        return runOffset + pages - 1;
    }

    private Long getAvailRunByOffset(int runOffset) {
        return runsAvailMap.get(runOffset);
    }

    @Override
    public int usage() {
        final int freeBytes;
        synchronized (arena) {
            freeBytes = this.freeBytes;
        }
        return usage(freeBytes);
    }

    private int usage(int freeBytes) {
        if (freeBytes == 0) {
            return 100;
        }

        int freePercentage = (int) (freeBytes * 100L / chunkSize);
        if (freePercentage == 0) {
            return 99;
        }
        return 100 - freePercentage;
    }

    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int sizeIdx, PoolThreadCache cache) {
        final long handle;
        if (sizeIdx <= arena.smallMaxSizeIdx) {
            // small
            handle = allocateSubpage(sizeIdx);
            if (handle < 0) {
                return false;
            }
            assert isSubpage(handle);
        } else {
            // normal
            // runSize must be multiple of pageSize
            int runSize = arena.sizeIdx2size(sizeIdx);
            handle = allocateRun(runSize);
            if (handle < 0) {
                return false;
            }
        }

        ByteBuffer nioBuffer = cachedNioBuffers != null? cachedNioBuffers.pollLast() : null;
        initBuf(buf, nioBuffer, handle, reqCapacity, cache);
        return true;
    }

    private long allocateRun(int runSize) {
        int pages = runSize >> pageShifts;
        int pageIdx = arena.pages2pageIdx(pages);

        synchronized (runsAvail) {
            //find first queue which has at least one big enough run
            int queueIdx = runFirstBestFit(pageIdx);
            if (queueIdx == -1) {
                return -1;
            }

            //get run with min offset in this queue
            PriorityQueue<Long> queue = runsAvail[queueIdx];
            long handle = queue.poll();

            assert !isUsed(handle);

            removeAvailRun(queue, handle);

            if (handle != -1) {
                handle = splitLargeRun(handle, pages);
            }

            freeBytes -= runSize(pageShifts, handle);
            return handle;
        }
    }

    private int calculateRunSize(int sizeIdx) {
        int maxElements = 1 << pageShifts - SizeClasses.LOG2_QUANTUM;
        int runSize = 0;
        int nElements;

        final int elemSize = arena.sizeIdx2size(sizeIdx);

        //find lowest common multiple of pageSize and elemSize
        do {
            runSize += pageSize;
            nElements = runSize / elemSize;
        } while (nElements < maxElements && runSize != nElements * elemSize);

        while (nElements > maxElements) {
            runSize -= pageSize;
            nElements = runSize / elemSize;
        }

        assert nElements > 0;
        assert runSize <= chunkSize;
        assert runSize >= elemSize;

        return runSize;
    }

    private int runFirstBestFit(int pageIdx) {
        if (freeBytes == chunkSize) {
            return arena.nPSizes - 1;
        }
        for (int i = pageIdx; i < arena.nPSizes; i++) {
            PriorityQueue<Long> queue = runsAvail[i];
            if (queue != null && !queue.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private long splitLargeRun(long handle, int needPages) {
        assert needPages > 0;

        int totalPages = runPages(handle);
        assert needPages <= totalPages;

        int remPages = totalPages - needPages;

        if (remPages > 0) {
            int runOffset = runOffset(handle);

            // keep track of trailing unused pages for later use
            int availOffset = runOffset + needPages;
            long availRun = toRunHandle(availOffset, remPages, 0);
            insertAvailRun(availOffset, remPages, availRun);

            // not avail
            return toRunHandle(runOffset, needPages, 1);
        }

        //mark it as used
        handle |= 1L << IS_USED_SHIFT;
        return handle;
    }

    /**
     * Create / initialize a new PoolSubpage of normCapacity. Any PoolSubpage created / initialized here is added to
     * subpage pool in the PoolArena that owns this PoolChunk
     *
     * @param sizeIdx sizeIdx of normalized size
     *
     * @return index in memoryMap
     */
    private long allocateSubpage(int sizeIdx) {
        // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
        // This is need as we may add it back and so alter the linked-list structure.
        PoolSubpage<T> head = arena.findSubpagePoolHead(sizeIdx);
        synchronized (head) {
            //allocate 一个新的 run
            int runSize = calculateRunSize(sizeIdx);
            //runSize 必须是页面大小的倍数
            long runHandle = allocateRun(runSize);
            if (runHandle < 0) {
                return -1;
            }

            int runOffset = runOffset(runHandle);
            int elemSize = arena.sizeIdx2size(sizeIdx);

            PoolSubpage<T> subpage = new PoolSubpage<T>(head, this, pageShifts, runOffset,
                               runSize(pageShifts, runHandle), elemSize);

            subpages[runOffset] = subpage;
            return subpage.allocate();
        }
    }

    /**
     * 释放子页或运行的页当子页从PoolSubpage中释放时，它可能会被添加回所属PoolArena的子页池。
     * 如果PoolArena中的子页面池至少有一个给定元素大小的其他PoolSubpage，
     * 我们可以完全释放拥有该页面的页面，
     * 以便它可以用于后续的分配
     *
     * @param handle handle to free
     */
    void free(long handle, int normCapacity, ByteBuffer nioBuffer) {
        if (isSubpage(handle)) {
            int sizeIdx = arena.size2SizeIdx(normCapacity);
            PoolSubpage<T> head = arena.findSubpagePoolHead(sizeIdx);

            PoolSubpage<T> subpage = subpages[runOffset(handle)];
            assert subpage != null && subpage.doNotDestroy;

            // 获取PoolArena拥有的PoolSubPage池的头，并在其上进行同步。
            // 这是需要的，因为我们可以把它添加回去，从而改变链表结构。
            synchronized (head) {
                if (subpage.free(head, bitmapIdx(handle))) {
                    //the subpage is still used, do not free it
                    return;
                }
            }
        }

        //start free run
        int pages = runPages(handle);

        synchronized (runsAvail) {
            // collapse continuous runs, successfully collapsed runs
            // will be removed from runsAvail and runsAvailMap
            long finalRun = collapseRuns(handle);

            //set run as not used
            finalRun &= ~(1L << IS_USED_SHIFT);
            //if it is a subpage, set it to run
            finalRun &= ~(1L << IS_SUBPAGE_SHIFT);

            insertAvailRun(runOffset(finalRun), runPages(finalRun), finalRun);
            freeBytes += pages << pageShifts;
        }

        if (nioBuffer != null && cachedNioBuffers != null &&
            cachedNioBuffers.size() < PooledByteBufAllocator.DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK) {
            cachedNioBuffers.offer(nioBuffer);
        }
    }

    private long collapseRuns(long handle) {
        return collapseNext(collapsePast(handle));
    }

    private long collapsePast(long handle) {
        for (;;) {
            int runOffset = runOffset(handle);
            int runPages = runPages(handle);

            Long pastRun = getAvailRunByOffset(runOffset - 1);
            if (pastRun == null) {
                return handle;
            }

            int pastOffset = runOffset(pastRun);
            int pastPages = runPages(pastRun);

            //is continuous
            if (pastRun != handle && pastOffset + pastPages == runOffset) {
                //remove past run
                removeAvailRun(pastRun);
                handle = toRunHandle(pastOffset, pastPages + runPages, 0);
            } else {
                return handle;
            }
        }
    }

    private long collapseNext(long handle) {
        for (;;) {
            int runOffset = runOffset(handle);
            int runPages = runPages(handle);

            Long nextRun = getAvailRunByOffset(runOffset + runPages);
            if (nextRun == null) {
                return handle;
            }

            int nextOffset = runOffset(nextRun);
            int nextPages = runPages(nextRun);

            //is continuous
            if (nextRun != handle && runOffset + runPages == nextOffset) {
                //remove next run
                removeAvailRun(nextRun);
                handle = toRunHandle(runOffset, runPages + nextPages, 0);
            } else {
                return handle;
            }
        }
    }

    private static long toRunHandle(int runOffset, int runPages, int inUsed) {
        return (long) runOffset << RUN_OFFSET_SHIFT
               | (long) runPages << SIZE_SHIFT
               | (long) inUsed << IS_USED_SHIFT;
    }

    void initBuf(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity,
                 PoolThreadCache threadCache) {
        if (isRun(handle)) {
            buf.init(this, nioBuffer, handle, runOffset(handle) << pageShifts,
                     reqCapacity, runSize(pageShifts, handle), arena.parent.threadCache());
        } else {
            initBufWithSubpage(buf, nioBuffer, handle, reqCapacity, threadCache);
        }
    }

    void initBufWithSubpage(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity,
                            PoolThreadCache threadCache) {
        int runOffset = runOffset(handle);
        int bitmapIdx = bitmapIdx(handle);

        PoolSubpage<T> s = subpages[runOffset];
        assert s.doNotDestroy;
        assert reqCapacity <= s.elemSize;

        buf.init(this, nioBuffer, handle,
                 (runOffset << pageShifts) + bitmapIdx * s.elemSize + offset,
                 reqCapacity, s.elemSize, threadCache);
    }

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    @Override
    public int freeBytes() {
        synchronized (arena) {
            return freeBytes;
        }
    }

    @Override
    public String toString() {
        final int freeBytes;
        synchronized (arena) {
            freeBytes = this.freeBytes;
        }

        return new StringBuilder()
                .append("Chunk(")
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append(": ")
                .append(usage(freeBytes))
                .append("%, ")
                .append(chunkSize - freeBytes)
                .append('/')
                .append(chunkSize)
                .append(')')
                .toString();
    }

    void destroy() {
        arena.destroyChunk(this);
    }

    static int runOffset(long handle) {
        return (int) (handle >> RUN_OFFSET_SHIFT);
    }

    static int runSize(int pageShifts, long handle) {
        return runPages(handle) << pageShifts;
    }

    static int runPages(long handle) {
        return (int) (handle >> SIZE_SHIFT & 0x7fff);
    }

    static boolean isUsed(long handle) {
        return (handle >> IS_USED_SHIFT & 1) == 1L;
    }

    static boolean isRun(long handle) {
        return !isSubpage(handle);
    }

    static boolean isSubpage(long handle) {
        return (handle >> IS_SUBPAGE_SHIFT & 1) == 1L;
    }

    static int bitmapIdx(long handle) {
        return (int) handle;
    }
}
