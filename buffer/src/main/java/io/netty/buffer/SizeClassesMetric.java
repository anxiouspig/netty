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

/**
 * 暴露一个SizeClasses的指标.
 */
public interface SizeClassesMetric {

    /**
     * 根据sizeIdx计算查找表的大小。
     *
     * @return size
     */
    int sizeIdx2size(int sizeIdx);

    /**
     * 根据sizeIdx计算大小。
     *
     * @return size
     */
    int sizeIdx2sizeCompute(int sizeIdx);

    /**
     * 根据pageIdx计算查找表的大小。
     *
     * @return 大小是页面大小的倍数。
     */
    long pageIdx2size(int pageIdx);

    /**
     * 根据pageIdx计算大小。
     *
     * @return 大小是页面大小的倍数
     */
    long pageIdx2sizeCompute(int pageIdx);

    /**
     * 将请求大小标准化到最接近的大小类。
     *
     * @param size request size
     *
     * @return sizeIdx of the size class
     */
    int size2SizeIdx(int size);

    /**
     * 将请求大小标准化到最近的pageSize类。
     *
     * @param pages multiples of pageSizes
     *
     * @return pageIdx of the pageSize class
     */
    int pages2pageIdx(int pages);

    /**
     * 将请求大小规范化为最近的pageSize类。
     *
     * @param pages multiples of pageSizes
     *
     * @return pageIdx of the pageSize class
     */
    int pages2pageIdxFloor(int pages);

    /**
     * 规范化分配具有指定大小和对齐方式的对象所产生的可用大小。
     *
     * @param size request size
     *
     * @return normalized size
     */
    int normalizeSize(int size);
}
