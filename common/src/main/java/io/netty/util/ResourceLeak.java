/*
 * Copyright 2013 The Netty Project
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
 * @deprecated 请使用{@link ResourceLeakTracker}，因为它可能会导致误报。
 */
@Deprecated
public interface ResourceLeak {
    /**
     * 记录调用者的当前堆栈跟踪，以便{@link ResourceLeakDetector}可以知道泄漏的资源最后访问了哪里。
     * 这个方法是{@link #record(Object) record(null)}的快捷方式。
     */
    void record();

    /**
     * 记录调用者的当前堆栈跟踪和指定的附加任意信息，以便{@link ResourceLeakDetector}可以知道泄漏的资源最后访问了哪里。
     */
    void record(Object hint);

    /**
     * 关闭泄漏，使{@link ResourceLeakDetector}不会对泄漏的资源发出警告。
     *
     * @return {@code true}如果第一次调用，{@code false}如果已经调用
     */
    boolean close();
}
