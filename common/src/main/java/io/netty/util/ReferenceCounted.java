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
 * 需要显式释放的引用计数对象。
 * <p>
 * 当实例化一个新的{@link ReferenceCounted}时，它从引用计数{@code 1}开始。
 * {@link #retain()}增加引用计数，{@link #release()}减少引用计数。
 * 如果引用计数减少到{@code 0}，对象将显式地被释放，而访问deallocated对象通常会导致访问冲突。
 * </p>
 * <p>
 * 如果实现{@link ReferenceCounted}的对象是其他实现{@link ReferenceCounted}的对象的容器，
 * 那么当容器的引用计数变为0时，所包含的对象也将通过{@link #release()}被释放。
 * </p>
 */
// 引用计数接口
public interface ReferenceCounted {
    /**
     * 返回此对象的引用计数。如果{@code 0}，则表示该对象已被释放。
     */
    int refCnt();

    /**
     * 增加引用计数{@code 1}。
     */
    ReferenceCounted retain();

    /**
     * 以指定的{@code increment}增加引用计数。
     */
    ReferenceCounted retain(int increment);

    /**
     * 记录此对象的当前访问位置，以便调试。
     * 如果确定该对象被泄露，此操作记录的信息将通过{@link ResourceLeakDetector}提供给您。
     * 这个方法是{@link #touch(Object) touch(null)}的快捷方式。
     */
    ReferenceCounted touch();

    /**
     * 记录此对象的当前访问位置以及用于调试的附加任意信息。
     * 如果确定该对象被泄露，此操作记录的信息将通过{@link ResourceLeakDetector}提供给您。
     */
    ReferenceCounted touch(Object hint);

    /**
     * 减少引用计数{@code 1}，并在引用计数达到{@code 0}时释放该对象。
     *
     * @return {@code true}当且仅当引用计数变为{@code 0}且该对象已被释放
     */
    boolean release();

    /**
     * 将引用计数按指定的{@code decrement}减少，并在引用计数达到{@code 0}时释放该对象。
     *
     * @return {@code true}当且仅当引用计数变为{@code 0}且该对象已被释放
     */
    boolean release(int decrement);
}
