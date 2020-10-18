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
package io.netty.channel.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

// 优化后的选择器
final class SelectedSelectionKeySetSelector extends Selector {
    private final SelectedSelectionKeySet selectionKeys; // 优化后的keys
    private final Selector delegate; // 委托选择器

    SelectedSelectionKeySetSelector(Selector delegate, SelectedSelectionKeySet selectionKeys) {
        this.delegate = delegate;
        this.selectionKeys = selectionKeys;
    }

    // 选择器是否打开
    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    // 选择器提供者
    @Override
    public SelectorProvider provider() {
        return delegate.provider();
    }

    // 返回注册的key
    @Override
    public Set<SelectionKey> keys() {
        return delegate.keys();
    }

    // 就绪的key
    @Override
    public Set<SelectionKey> selectedKeys() {
        return delegate.selectedKeys();
    }

    // 立即查一次
    @Override
    public int selectNow() throws IOException {
        selectionKeys.reset();
        return delegate.selectNow();
    }
    // 超时轮训
    @Override
    public int select(long timeout) throws IOException {
        selectionKeys.reset();
        return delegate.select(timeout);
    }

    // 阻塞轮训
    @Override
    public int select() throws IOException {
        selectionKeys.reset();
        return delegate.select();
    }
    // 唤醒
    @Override
    public Selector wakeup() {
        return delegate.wakeup();
    }
    // 关闭
    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
