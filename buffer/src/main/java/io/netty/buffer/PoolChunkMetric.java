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

/**
 * 一个内存块的度量.
 */
public interface PoolChunkMetric {

    /**
     * 返回当前内存块使用的百分比.
     */
    int usage();

    /**
     * 返回字节块的大小, 这是块中可以提供的最大字节数。
     */
    int chunkSize();

    /**
     * 返回块中的空闲字节数。
     */
    int freeBytes();
}
