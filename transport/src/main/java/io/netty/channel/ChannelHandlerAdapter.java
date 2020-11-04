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

package io.netty.channel;

import io.netty.channel.ChannelHandlerMask.Skip;
import io.netty.util.internal.InternalThreadLocalMap;

import java.util.Map;

/**
 * {@link ChannelHandler}的框架实现。
 */
public abstract class ChannelHandlerAdapter implements ChannelHandler {

    // 不使用volatile，因为它只用于完整性检查。
    boolean added;

    /**
     * Throws {@link IllegalStateException} if {@link ChannelHandlerAdapter#isSharable()} returns {@code true}
     */
    protected void ensureNotSharable() {
        if (isSharable()) {
            throw new IllegalStateException("ChannelHandler " + getClass().getName() + " is not allowed to be shared");
        }
    }

    /**
     * 如果实现是{@link Sharable}，那么返回{@code true}，这样可以添加到不同的{@link ChannelPipeline}中。
     */
    public boolean isSharable() {
        /**
         * 缓存{@link Sharable}注释检测的结果以解决一个条件。
         * 我们使用{@link ThreadLocal}和{@link WeakHashMap}来消除易失性的写/读。
         * 每个{@link线程}使用不同的{@link WeakHashMap}实例对我们来说已经足够好了，
         * 而且无论如何{@link线程}的数量是相当有限的。
         *
         * See <a href="https://github.com/netty/netty/issues/2289">#2289</a>.
         */
        Class<?> clazz = getClass();
        Map<Class<?>, Boolean> cache = InternalThreadLocalMap.get().handlerSharableCache();
        Boolean sharable = cache.get(clazz);
        if (sharable == null) {
            sharable = clazz.isAnnotationPresent(Sharable.class);
            cache.put(clazz, sharable);
        }
        return sharable;
    }

    /**
     * 默认情况下，子类可以重写此方法。
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    /**
     * 默认情况下，子类可以重写此方法。
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    /**
     * 调用{@link ChannelHandlerContext#fireExceptionCaught(Throwable)}转发到{@link ChannelPipeline}中的下一个{@link ChannelHandler}。
     *
     * 子类可以重写此方法来改变行为。
     *
     * @deprecated is part of {@link ChannelInboundHandler}
     */
    @Skip
    @Override
    @Deprecated
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }
}
