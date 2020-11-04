/*
 * Copyright 2014 The Netty Project
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

package io.netty.resolver;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.Closeable;
import java.net.SocketAddress;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * 创建和管理{@link NameResolver}，以便每个{@link EventExecutor}都有自己的解析器实例。
 */
public abstract class AddressResolverGroup<T extends SocketAddress> implements Closeable {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AddressResolverGroup.class);

    /**
     * 注意，我们在这里没有使用{@link ConcurrentMap}，因为实例化一个解析器通常开销很大。
     */
    private final Map<EventExecutor, AddressResolver<T>> resolvers =
            new IdentityHashMap<EventExecutor, AddressResolver<T>>();

    private final Map<EventExecutor, GenericFutureListener<Future<Object>>> executorTerminationListeners =
            new IdentityHashMap<EventExecutor, GenericFutureListener<Future<Object>>>();

    protected AddressResolverGroup() { }

    /**
     * 返回与指定的{@link EventExecutor}关联的{@link AddressResolver}。如果没有找到关联的解析器，此方法将创建并返回一个由
     * {@link #newResolver(EventExecutor)}创建的新解析器实例，
     * 以便在使用相同的{@link EventExecutor}的另一个{@code #getResolver(EventExecutor)}调用时重用新的解析器。
     */
    public AddressResolver<T> getResolver(final EventExecutor executor) {
        ObjectUtil.checkNotNull(executor, "executor");

        // 若执行器已关闭
        if (executor.isShuttingDown()) {
            throw new IllegalStateException("executor not accepting a task");
        }

        AddressResolver<T> r;
        synchronized (resolvers) {
            r = resolvers.get(executor); // 得到解析器
            if (r == null) { // 若不存在
                final AddressResolver<T> newResolver;
                try {
                    // 新建解析器
                    newResolver = newResolver(executor);
                } catch (Exception e) {
                    throw new IllegalStateException("failed to create a new resolver", e);
                }
                // 添加解析器
                resolvers.put(executor, newResolver);

                final FutureListener<Object> terminationListener = new FutureListener<Object>() {
                    @Override
                    public void operationComplete(Future<Object> future) {
                        synchronized (resolvers) {
                            // 解析完成删除
                            resolvers.remove(executor);
                            executorTerminationListeners.remove(executor);
                        }
                        newResolver.close();
                    }
                };
                // 执行完成监听
                executorTerminationListeners.put(executor, terminationListener);
                executor.terminationFuture().addListener(terminationListener);

                r = newResolver;
            }
        }

        return r;
    }

    /**
     * 由{@link #getResolver(EventExecutor)}调用，以创建一个新的{@link AddressResolver}。
     */
    protected abstract AddressResolver<T> newResolver(EventExecutor executor) throws Exception;

    /**
     * 关闭该组创建的所有{@link NameResolver}。
     */
    @Override
    @SuppressWarnings({ "unchecked", "SuspiciousToArrayCall" })
    public void close() {
        final AddressResolver<T>[] rArray;
        final Map.Entry<EventExecutor, GenericFutureListener<Future<Object>>>[] listeners;

        synchronized (resolvers) {
            rArray = (AddressResolver<T>[]) resolvers.values().toArray(new AddressResolver[0]);
            resolvers.clear();
            listeners = executorTerminationListeners.entrySet().toArray(new Map.Entry[0]);
            executorTerminationListeners.clear();
        }

        for (final Map.Entry<EventExecutor, GenericFutureListener<Future<Object>>> entry : listeners) {
            // 移除监听器
            entry.getKey().terminationFuture().removeListener(entry.getValue());
        }

        for (final AddressResolver<T> r: rArray) {
            try {
                // 关闭解析器
                r.close();
            } catch (Throwable t) {
                logger.warn("Failed to close a resolver:", t);
            }
        }
    }
}
