/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.internal.ObjectUtil;

/**
 * <p>promise组合器监控一些离散futures的结果，然后在所有组合futures完成后通知一个最终的集合promise。
 * 只有当所有的组合futures都成功时，集合promise才会成功。如果任何一个合并futures失败，则总promise将失败。
 * 合并promise的失败原因将是失败的其中一个合并futures的失败原因；
 * 如果超过一个合并futures失败，具体哪个失败原因将分配给合并promise是没有定义的。</p>
 *
 * <p>调用者可以通过{@link PromiseCombiner#add(Future)}和
 * {@link PromiseCombiner#addAll(Future[])}方法来组合任意数量的futures来填充promise组合器。
 * 当所有要组合的futures都已添加，调用者必须提供一个聚合promise，当所有组合的promise都已完成时，
 *  * 调用者将通过{@link PromiseCombiner#finish(Promise)}方法得到通知。</p>
 *
 * <p>This implementation is <strong>NOT</strong> thread-safe and all methods must be called
 * from the {@link EventExecutor} thread.</p>
 */
public final class PromiseCombiner {
    private int expectedCount;
    private int doneCount;
    private Promise<Void> aggregatePromise;
    private Throwable cause;
    private final GenericFutureListener<Future<?>> listener = new GenericFutureListener<Future<?>>() {
        @Override
        public void operationComplete(final Future<?> future) {
            if (executor.inEventLoop()) {
                operationComplete0(future);
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        operationComplete0(future);
                    }
                });
            }
        }

        private void operationComplete0(Future<?> future) {
            assert executor.inEventLoop();
            ++doneCount;
            if (!future.isSuccess() && cause == null) {
                cause = future.cause();
            }
            if (doneCount == expectedCount && aggregatePromise != null) {
                tryPromise();
            }
        }
    };

    private final EventExecutor executor;

    /**
     * Deprecated use {@link PromiseCombiner#PromiseCombiner(EventExecutor)}.
     */
    @Deprecated
    public PromiseCombiner() {
        this(ImmediateEventExecutor.INSTANCE);
    }

    /**
     * 用于通知的{@link EventExecutor}。
     * 您必须在{@link EventExecutor}线程中调用{@link #add(Future)}、{@link #addAll(Future[])}和{@link #finish(Promise)}。
     *
     * @param executor the {@link EventExecutor} to use for notifications.
     */
    public PromiseCombiner(EventExecutor executor) {
        this.executor = ObjectUtil.checkNotNull(executor, "executor");
    }

    /**
     * 添加要组合的新承诺。可以添加新的承诺，直到通过{@link PromiseCombiner#finish(Promise)}方法添加了一个聚合promise。
     *
     * @param promise the promise to add to this promise combiner
     *
     * @deprecated Replaced by {@link PromiseCombiner#add(Future)}.
     */
    @Deprecated
    public void add(Promise promise) {
        add((Future) promise);
    }

    /**
     * 增加了一个新的未来被结合。可以添加新的期货，直到通过{@link PromiseCombiner#finish(Promise)}方法添加了一个聚合promise。
     *
     * @param future the future to add to this promise combiner
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void add(Future future) {
        checkAddAllowed();
        checkInEventLoop();
        ++expectedCount;
        future.addListener(listener);
    }

    /**
     * 添加要合并的新promises。新的promises可以被添加，直到一个总的promises通过
     *
     * @param promises the promises to add to this promise combiner
     *
     * @deprecated Replaced by {@link PromiseCombiner#addAll(Future[])}
     */
    @Deprecated
    public void addAll(Promise... promises) {
        addAll((Future[]) promises);
    }

    /**
     * 添加新的futures以进行组合。新的futures可以被添加，
     * 直到通过{@link PromiseCombiner#finish(Promise)}方法添加一个集合promise。
     *
     * @param futures the futures to add to this promise combiner
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addAll(Future... futures) {
        for (Future future : futures) {
            this.add(future);
        }
    }

    /**
     * <p>设置当所有组合futures结束时通知的promise。如果所有合并futures都成功，那么集合promise将成功。
     * 如果一个或多个合并futures失败，那么集合promise将以其中一个失败futures的原因失败。
     * 如果一个以上的合并futures失败，那么到底哪一个失败将被分配给集合promise是未定义的。</p>
     *
     * <p>After this method is called, no more futures may be added via the {@link PromiseCombiner#add(Future)} or
     * {@link PromiseCombiner#addAll(Future[])} methods.</p>
     *
     * @param aggregatePromise the promise to notify when all combined futures have finished
     */
    public void finish(Promise<Void> aggregatePromise) {
        ObjectUtil.checkNotNull(aggregatePromise, "aggregatePromise");
        checkInEventLoop();
        if (this.aggregatePromise != null) {
            throw new IllegalStateException("Already finished");
        }
        this.aggregatePromise = aggregatePromise;
        if (doneCount == expectedCount) {
            tryPromise();
        }
    }

    private void checkInEventLoop() {
        if (!executor.inEventLoop()) {
            throw new IllegalStateException("Must be called from EventExecutor thread");
        }
    }

    private boolean tryPromise() {
        return (cause == null) ? aggregatePromise.trySuccess(null) : aggregatePromise.tryFailure(cause);
    }

    private void checkAddAllowed() {
        if (aggregatePromise != null) {
            throw new IllegalStateException("Adding promises is not allowed after finished adding");
        }
    }
}
