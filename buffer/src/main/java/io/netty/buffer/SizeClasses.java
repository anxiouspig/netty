/*
 * Copyright 2020 The Netty Project
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

import static io.netty.buffer.PoolThreadCache.*;

/**
 * SizeClasses 要求在之前定义 {@code pageShifts} ,
 * 它定义了:
 * <p>
 *   LOG2_SIZE_CLASS_GROUP: 每个大小组的大小等级数量的对数.
 *   LOG2_MAX_LOOKUP_SIZE:  lookup table 中的最大大小等级对数.
 *   sizeClasses: [index, log2Group, log2Delta, nDelta, isMultiPageSize,
 *                 isSubPage, log2DeltaLookup] 类型的全表.
 *     index: 大小等级索引.
 *     log2Group: 基础大小的对数组 (没有δ添加).
 *     log2Delta: 上一个大小等级的对数δ
 *     nDelta: δ 倍数.
 *     isMultiPageSize: 'yes' 如果是页大小的倍数, 'no' 否则.
 *     isSubPage: 'yes' 如果是子页大小等级, 'no' otherwise.
 *     log2DeltaLookup: Same as log2Delta if a lookup table size class, 'no'
 *                      otherwise.
 * <p>
 *   nSubpages: 子页大小等级的编号.
 *   nSizes: 大小等级的编号.
 *   nPSizes: 页大小倍数的大小等级编号.
 *
 *   smallMaxSizeIdx: 最大的小的大小等级索引.
 *
 *   lookupMaxclass: 包含在 lookup table 的大小等级.
 *   log2NormalMinClass: 最小的标准大小等级的对数.
 * <p>
 *   第一个 size class 和 空间是 1 << LOG2_QUANTUM = 16 = 2B.
 *   每个组有 1 << LOG2_SIZE_CLASS_GROUP = 4 of size classes.
 *
 *   size计算方法
 *   size = 1 << log2Group + nDelta * (1 << log2Delta)
 *
 *   第一个 size class 有一个不同的编码, 因为大小必须在 group and delta*nDelta 中分割.
 *
 *   If pageShift = 13, sizeClasses 类似这样:
 *
 *   (index, log2Group, log2Delta, nDelta, isMultiPageSize, isSubPage, log2DeltaLookup)
 * <p>
 *   ( 0,     4,        4,         0,       no,             yes,        4)
 *   ( 1,     4,        4,         1,       no,             yes,        4)
 *   ( 2,     4,        4,         2,       no,             yes,        4)
 *   ( 3,     4,        4,         3,       no,             yes,        4)
 * <p>
 *   ( 4,     6,        4,         1,       no,             yes,        4)
 *   ( 5,     6,        4,         2,       no,             yes,        4)
 *   ( 6,     6,        4,         3,       no,             yes,        4)
 *   ( 7,     6,        4,         4,       no,             yes,        4)
 * <p>
 *   ( 8,     7,        5,         1,       no,             yes,        5)
 *   ( 9,     7,        5,         2,       no,             yes,        5)
 *   ( 10,    7,        5,         3,       no,             yes,        5)
 *   ( 11,    7,        5,         4,       no,             yes,        5)
 *   ...
 *   ...
 *   ( 72,    23,       21,        1,       yes,            no,        no)
 *   ( 73,    23,       21,        2,       yes,            no,        no)
 *   ( 74,    23,       21,        3,       yes,            no,        no)
 *   ( 75,    23,       21,        4,       yes,            no,        no)
 * <p>
 *   ( 76,    24,       22,        1,       yes,            no,        no)
 */
abstract class SizeClasses implements SizeClassesMetric {

    static final int LOG2_QUANTUM = 4; // 对数量子=4

    private static final int LOG2_SIZE_CLASS_GROUP = 2; // 每个组的等级数量=4
    private static final int LOG2_MAX_LOOKUP_SIZE = 12; //

    private static final int INDEX_IDX = 0;
    private static final int LOG2GROUP_IDX = 1;
    private static final int LOG2DELTA_IDX = 2;
    private static final int NDELTA_IDX = 3;
    private static final int PAGESIZE_IDX = 4;
    private static final int SUBPAGE_IDX = 5;
    private static final int LOG2_DELTA_LOOKUP_IDX = 6;

    private static final byte no = 0, yes = 1;

    protected SizeClasses(int pageSize, int pageShifts, int chunkSize, int directMemoryCacheAlignment) {
        this.pageSize = pageSize; // 页大小，默认1kb
        this.pageShifts = pageShifts; // 页大小二进制位 13
        this.chunkSize = chunkSize; // 块大小，默认2m
        this.directMemoryCacheAlignment = directMemoryCacheAlignment; // huge内存块使用 0

        // 计算group的数量 21
        int group = log2(chunkSize) + 1 - LOG2_QUANTUM;

        //产生 size classes
        //[index, log2Group, log2Delta, nDelta, isMultiPageSize, isSubPage, log2DeltaLookup]
        // 4倍，每组4个，84个等级
        sizeClasses = new short[group << LOG2_SIZE_CLASS_GROUP][7];
        nSizes = sizeClasses(); // 76个级

        //产生 lookup table
        sizeIdx2sizeTab = new int[nSizes]; // 76 存size
        pageIdx2sizeTab = new int[nPSizes]; // 页倍数 存size
        idx2SizeTab(sizeIdx2sizeTab, pageIdx2sizeTab);

        size2idxTab = new int[lookupMaxSize >> LOG2_QUANTUM];
        size2idxTab(size2idxTab);
    }

    protected final int pageSize;
    protected final int pageShifts;
    protected final int chunkSize;
    protected final int directMemoryCacheAlignment;

    final int nSizes;
    int nSubpages;
    int nPSizes;

    int smallMaxSizeIdx;

    private int lookupMaxSize;

    private final short[][] sizeClasses;

    private final int[] pageIdx2sizeTab;

    // lookup table for sizeIdx <= smallMaxSizeIdx
    private final int[] sizeIdx2sizeTab;

    // lookup table used for size <= lookupMaxclass
    // spacing is 1 << LOG2_QUANTUM, so the size of array is lookupMaxclass >> LOG2_QUANTUM
    private final int[] size2idxTab;

    // 大小等级
    private int sizeClasses() {
        int normalMaxSize = -1;

        int index = 0;
        int size = 0;

        int log2Group = LOG2_QUANTUM; // 4
        int log2Delta = LOG2_QUANTUM; // 4
        int ndeltaLimit = 1 << LOG2_SIZE_CLASS_GROUP; // 4

        //First small group, nDelta start at 0.
        //first size class is 1 << LOG2_QUANTUM = 16
        int nDelta = 0; // 初始化为0
        while (nDelta < ndeltaLimit) { // nDelta = 0，1，2，3
            size = sizeClass(index++, log2Group, log2Delta, nDelta++); // 16，
        }
        log2Group += LOG2_SIZE_CLASS_GROUP; // +2=6

        //所有其余的组，nDelta从1开始。
        while (size < chunkSize) { // 64 < 2m 在块内存内分配
            nDelta = 1; // 1，2，3，4

            while (nDelta <= ndeltaLimit && size < chunkSize) {
                size = sizeClass(index++, log2Group, log2Delta, nDelta++);
                normalMaxSize = size;
            }

            log2Group++;
            log2Delta++;
        }

        //chunkSize must be normalMaxSize
        assert chunkSize == normalMaxSize;

        //return number of size index
        return index;
    }

    //计算大小等级
    private int sizeClass(int index, int log2Group, int log2Delta, int nDelta) {
        // 0 4 4 0，1 4 4 1，2 4 4 2，3 4 4 3，
        short isMultiPageSize; // 是否是子页的倍数
        if (log2Delta >= pageShifts) {
            // pageShifts默认13
            isMultiPageSize = yes;
        } else {
            int pageSize = 1 << pageShifts; // 8192=1kb
            int size = (1 << log2Group) + (1 << log2Delta) * nDelta; // 16，32，48，64
            // size是1kb的整数倍
            isMultiPageSize = size == size / pageSize * pageSize? yes : no; // 0，0，0，0
        }

        int log2Ndelta = nDelta == 0? 0 : log2(nDelta); // 0，0，1，1

        byte remove = 1 << log2Ndelta < nDelta? yes : no; // 0，0，0，1 nDelta只有4才是no

        // 4 + 0 == 4 ? 4 + 1 : 4
        int log2Size = log2Delta + log2Ndelta == log2Group? log2Group + 1 : log2Group; // 5，5，4，4
        if (log2Size == log2Group) {
            remove = yes; // 0，0，1，1
        }

        // 是不是子页 = 5 < 13 + 2 = 15
        short isSubpage = log2Size < pageShifts + LOG2_SIZE_CLASS_GROUP? yes : no; // 1，1，1，1
        // 4
        int log2DeltaLookup = log2Size < LOG2_MAX_LOOKUP_SIZE ||
                              log2Size == LOG2_MAX_LOOKUP_SIZE && remove == no
                ? log2Delta : no;

        short[] sz = {
                (short) index, (short) log2Group, (short) log2Delta,
                (short) nDelta, isMultiPageSize, isSubpage, (short) log2DeltaLookup
        };

        sizeClasses[index] = sz;
        int size = (1 << log2Group) + (nDelta << log2Delta);

        if (sz[PAGESIZE_IDX] == yes) { // 是否是1kb的倍数
            nPSizes++;
        }
        if (sz[SUBPAGE_IDX] == yes) { // 是否是子页
            nSubpages++; // 子页数量
            smallMaxSizeIdx = index; // 类级别索引
        }
        if (sz[LOG2_DELTA_LOOKUP_IDX] != no) {
            lookupMaxSize = size; // 观察大小
        }
        return size;
    }

    private void idx2SizeTab(int[] sizeIdx2sizeTab, int[] pageIdx2sizeTab) {
        int pageIdx = 0;
        // sizeIdx2sizeTab = 76，pageIdx2sizeTab = 40
        for (int i = 0; i < nSizes; i++) {
            short[] sizeClass = sizeClasses[i];
            int log2Group = sizeClass[LOG2GROUP_IDX];
            int log2Delta = sizeClass[LOG2DELTA_IDX];
            int nDelta = sizeClass[NDELTA_IDX];

            int size = (1 << log2Group) + (nDelta << log2Delta);
            sizeIdx2sizeTab[i] = size; // 记录所有级大小

            if (sizeClass[PAGESIZE_IDX] == yes) {
                pageIdx2sizeTab[pageIdx++] = size; // 记录1kb倍数级的size
            }
        }
    }
    // TODO
    private void size2idxTab(int[] size2idxTab) {
        int idx = 0;
        int size = 0;
        // size2idxTab.length=lookupMaxSize/16=256
        for (int i = 0; size <= lookupMaxSize; i++) {
            int log2Delta = sizeClasses[i][LOG2DELTA_IDX];
            int times = 1 << log2Delta - LOG2_QUANTUM;
            // [0,1,2,3,4,5,6,7,8,8,9,9,9,9,10,10,10,10,10,10,10,10...]
            while (size <= lookupMaxSize && times-- > 0) {
                size2idxTab[idx++] = i;
                size = idx + 1 << LOG2_QUANTUM; // idx+1<256 -> idx < 255
            }
        }
    }

    @Override
    public int sizeIdx2size(int sizeIdx) {
        return sizeIdx2sizeTab[sizeIdx];
    }

    @Override
    public int sizeIdx2sizeCompute(int sizeIdx) {
        int group = sizeIdx >> LOG2_SIZE_CLASS_GROUP;
        int mod = sizeIdx & (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        int groupSize = group == 0? 0 :
                1 << LOG2_QUANTUM + LOG2_SIZE_CLASS_GROUP - 1 << group;

        int shift = group == 0? 1 : group;
        int lgDelta = shift + LOG2_QUANTUM - 1;
        int modSize = mod + 1 << lgDelta;

        return groupSize + modSize;
    }

    @Override
    public long pageIdx2size(int pageIdx) {
        return pageIdx2sizeTab[pageIdx];
    }

    @Override
    public long pageIdx2sizeCompute(int pageIdx) {
        int group = pageIdx >> LOG2_SIZE_CLASS_GROUP;
        int mod = pageIdx & (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        long groupSize = group == 0? 0 :
                1L << pageShifts + LOG2_SIZE_CLASS_GROUP - 1 << group;

        int shift = group == 0? 1 : group;
        int log2Delta = shift + pageShifts - 1;
        int modSize = mod + 1 << log2Delta;

        return groupSize + modSize;
    }

    @Override
    public int size2SizeIdx(int size) {
        if (size == 0) {
            return 0;
        }
        if (size > chunkSize) {
            return nSizes;
        }

        if (directMemoryCacheAlignment > 0) {
            size = alignSize(size);
        }

        if (size <= lookupMaxSize) {
            //size-1 / MIN_TINY
            return size2idxTab[size - 1 >> LOG2_QUANTUM];
        }

        int x = log2((size << 1) - 1);
        int shift = x < LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM + 1
                ? 0 : x - (LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM);

        int group = shift << LOG2_SIZE_CLASS_GROUP;

        int log2Delta = x < LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM + 1
                ? LOG2_QUANTUM : x - LOG2_SIZE_CLASS_GROUP - 1;

        int deltaInverseMask = -1 << log2Delta;
        int mod = (size - 1 & deltaInverseMask) >> log2Delta &
                  (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        return group + mod;
    }

    @Override
    public int pages2pageIdx(int pages) {
        return pages2pageIdxCompute(pages, false);
    }

    @Override
    public int pages2pageIdxFloor(int pages) {
        return pages2pageIdxCompute(pages, true);
    }

    private int pages2pageIdxCompute(int pages, boolean floor) {
        int pageSize = pages << pageShifts;
        if (pageSize > chunkSize) {
            return nPSizes;
        }

        int x = log2((pageSize << 1) - 1);

        int shift = x < LOG2_SIZE_CLASS_GROUP + pageShifts
                ? 0 : x - (LOG2_SIZE_CLASS_GROUP + pageShifts);

        int group = shift << LOG2_SIZE_CLASS_GROUP;

        int log2Delta = x < LOG2_SIZE_CLASS_GROUP + pageShifts + 1?
                pageShifts : x - LOG2_SIZE_CLASS_GROUP - 1;

        int deltaInverseMask = -1 << log2Delta;
        int mod = (pageSize - 1 & deltaInverseMask) >> log2Delta &
                  (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        int pageIdx = group + mod;

        if (floor && pageIdx2sizeTab[pageIdx] > pages << pageShifts) {
            pageIdx--;
        }

        return pageIdx;
    }

    // 四舍五入大小，直到对齐的最接近倍数。
    private int alignSize(int size) {
        int delta = size & directMemoryCacheAlignment - 1;
        return delta == 0? size : size + directMemoryCacheAlignment - delta;
    }

    @Override
    public int normalizeSize(int size) {
        if (size == 0) {
            return sizeIdx2sizeTab[0];
        }
        if (directMemoryCacheAlignment > 0) {
            size = alignSize(size);
        }

        if (size <= lookupMaxSize) {
            int ret = sizeIdx2sizeTab[size2idxTab[size - 1 >> LOG2_QUANTUM]];
            assert ret == normalizeSizeCompute(size);
            return ret;
        }
        return normalizeSizeCompute(size);
    }

    private static int normalizeSizeCompute(int size) {
        int x = log2((size << 1) - 1);
        int log2Delta = x < LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM + 1
                ? LOG2_QUANTUM : x - LOG2_SIZE_CLASS_GROUP - 1;
        int delta = 1 << log2Delta;
        int delta_mask = delta - 1;
        return size + delta_mask & ~delta_mask;
    }
}
