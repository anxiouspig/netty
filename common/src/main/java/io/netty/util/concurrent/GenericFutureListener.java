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
package io.netty.util.concurrent;

import java.util.EventListener;

/**
 * 监听一个 {@link Future} 的结果.  异步操作的结果将被通知， once this listener
 * 一旦这个监听器通过 {@link Future#addListener(GenericFutureListener)} 被添加的话.
 */
// 观察者模式，应该。
public interface GenericFutureListener<F extends Future<?>> extends EventListener {

    /**
     * 当与操作关联的 {@link Future} 完成时调用.
     *
     * @param future  调用这个回调的 {@link Future}
     */
    void operationComplete(F future) throws Exception;
}
