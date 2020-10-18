/*
 * Copyright 2012 The Netty Project
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

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopException;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.IntSupplier;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ReflectionUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;

import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link SingleThreadEventLoop}实现，它将{@link Channel}注册到{@link Selector}，并且在事件循环中对这些通道进行多处理。
 *
 */
public final class NioEventLoop extends SingleThreadEventLoop {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioEventLoop.class);
    // 清理间隔
    private static final int CLEANUP_INTERVAL = 256; // XXX Hard-coded value, but won't need customization.
    // 关闭key集合优化，默认false，也就是优化
    private static final boolean DISABLE_KEY_SET_OPTIMIZATION =
            SystemPropertyUtil.getBoolean("io.netty.noKeySetOptimization", false);

    private static final int MIN_PREMATURE_SELECTOR_RETURNS = 3;
    private static final int SELECTOR_AUTO_REBUILD_THRESHOLD; // 选择器自动重新生成阈值

    // 立即返回
    private final IntSupplier selectNowSupplier = new IntSupplier() {
        @Override
        public int get() throws Exception {
            return selectNow();
        }
    };

    // JDK NIO bug 的替代方法.
    // 替代java自己的优化。
    // Epoll空轮训bug
    // See:
    // - http://bugs.sun.com/view_bug.do?bug_id=6427854
    // - https://github.com/netty/netty/issues/203
    static {
        final String key = "sun.nio.ch.bugLevel";
        final String bugLevel = SystemPropertyUtil.get(key);
        if (bugLevel == null) { // nio bug 级别
            try {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    @Override
                    public Void run() { // 赋予权限
                        System.setProperty(key, "");
                        return null;
                    }
                });
            } catch (final SecurityException e) {
                logger.debug("Unable to get/set System Property: " + key, e);
            }
        }
        // selector轮训阈值，默认512
        int selectorAutoRebuildThreshold = SystemPropertyUtil.getInt("io.netty.selectorAutoRebuildThreshold", 512);
        if (selectorAutoRebuildThreshold < MIN_PREMATURE_SELECTOR_RETURNS) {
            selectorAutoRebuildThreshold = 0;
        }

        SELECTOR_AUTO_REBUILD_THRESHOLD = selectorAutoRebuildThreshold;

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.noKeySetOptimization: {}", DISABLE_KEY_SET_OPTIMIZATION);
            logger.debug("-Dio.netty.selectorAutoRebuildThreshold: {}", SELECTOR_AUTO_REBUILD_THRESHOLD);
        }
    }

    /**
     * The NIO {@link Selector}.
     */
    private Selector selector; // 选择器
    private Selector unwrappedSelector; // 未包装选择器
    private SelectedSelectionKeySet selectedKeys; // 选择key集合

    private final SelectorProvider provider; // jdk选择器提供

    private static final long AWAKE = -1L;
    private static final long NONE = Long.MAX_VALUE;

    // 下一个唤醒纳秒:
    //    AWAKE            when EL is awake
    //    NONE             when EL is waiting with no wakeup scheduled
    //    other value T    when EL is waiting with wakeup scheduled at time T
    private final AtomicLong nextWakeupNanos = new AtomicLong(AWAKE);

    private final SelectStrategy selectStrategy;

    private volatile int ioRatio = 50; // io比率
    private int cancelledKeys; // 取消key的数量
    private boolean needsToSelectAgain; // 是否需要再次epoll

    // 构造方法
    NioEventLoop(NioEventLoopGroup parent, Executor executor, SelectorProvider selectorProvider,
                 SelectStrategy strategy, RejectedExecutionHandler rejectedExecutionHandler,
                 EventLoopTaskQueueFactory queueFactory) {
        super(parent, executor, false, newTaskQueue(queueFactory), newTaskQueue(queueFactory),
                rejectedExecutionHandler);

        this.provider = ObjectUtil.checkNotNull(selectorProvider, "selectorProvider"); // 选择提供器
        this.selectStrategy = ObjectUtil.checkNotNull(strategy, "selectStrategy"); // 选择策略
        final SelectorTuple selectorTuple = openSelector(); // 选择器包装类
        this.selector = selectorTuple.selector; // 选择器
        this.unwrappedSelector = selectorTuple.unwrappedSelector; // 未包装的选择器
    }

    // 新任务队列
    private static Queue<Runnable> newTaskQueue(
            EventLoopTaskQueueFactory queueFactory) {
        if (queueFactory == null) {
            return newTaskQueue0(DEFAULT_MAX_PENDING_TASKS);
        }
        return queueFactory.newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
    }

    // 选择器包装类
    private static final class SelectorTuple {
        final Selector unwrappedSelector; // 未包装选择器
        final Selector selector; // 选择器

        SelectorTuple(Selector unwrappedSelector) {
            this.unwrappedSelector = unwrappedSelector;
            this.selector = unwrappedSelector;
        }

        SelectorTuple(Selector unwrappedSelector, Selector selector) {
            this.unwrappedSelector = unwrappedSelector;
            this.selector = selector;
        }
    }

    // 返回选择器包装类
    private SelectorTuple openSelector() {
        final Selector unwrappedSelector; // 未包装选择器
        try {
            unwrappedSelector = provider.openSelector(); // 由java提供
        } catch (IOException e) {
            throw new ChannelException("failed to open a new selector", e);
        }

        if (DISABLE_KEY_SET_OPTIMIZATION) { // 禁用优化key选择，默认false
            return new SelectorTuple(unwrappedSelector);
        }
        // 使用选择器优化
        Object maybeSelectorImplClass = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    return Class.forName(
                            "sun.nio.ch.SelectorImpl",
                            false,
                            PlatformDependent.getSystemClassLoader());
                } catch (Throwable cause) {
                    return cause;
                }
            }
        });

        if (!(maybeSelectorImplClass instanceof Class) || // 如果不是类
            // 确保当前选择器的实现是继承自SelectorImpl
            !((Class<?>) maybeSelectorImplClass).isAssignableFrom(unwrappedSelector.getClass())) {
            if (maybeSelectorImplClass instanceof Throwable) {
                Throwable t = (Throwable) maybeSelectorImplClass;
                logger.trace("failed to instrument a special java.util.Set into: {}", unwrappedSelector, t);
            }
            return new SelectorTuple(unwrappedSelector); // 未优化
        }

        // 开始优化
        // SelectorImpl抽象类
        final Class<?> selectorImplClass = (Class<?>) maybeSelectorImplClass;
        // 优化的数据结构
        final SelectedSelectionKeySet selectedKeySet = new SelectedSelectionKeySet();

        Object maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    // 反射拿到selectedKeys字段
                    Field selectedKeysField = selectorImplClass.getDeclaredField("selectedKeys");
                    // 反射拿到publicSelectedKeys字段
                    Field publicSelectedKeysField = selectorImplClass.getDeclaredField("publicSelectedKeys");
                    // java版本若大于等于9并且是否能用unsafe
                    if (PlatformDependent.javaVersion() >= 9 && PlatformDependent.hasUnsafe()) {
                        // 让我们试图去使用 sun.misc.Unsafe 去替换 SelectionKeySet.
                        // java9之前，需要通过反射获取Unsafe，之后直接获取。
                        // 字段偏移量
                        long selectedKeysFieldOffset = PlatformDependent.objectFieldOffset(selectedKeysField);
                        long publicSelectedKeysFieldOffset =
                                PlatformDependent.objectFieldOffset(publicSelectedKeysField);
                        // 若指针存在
                        if (selectedKeysFieldOffset != -1 && publicSelectedKeysFieldOffset != -1) {
                            // 替换成优化后的字段
                            PlatformDependent.putObject(
                                    unwrappedSelector, selectedKeysFieldOffset, selectedKeySet);
                            PlatformDependent.putObject(
                                    unwrappedSelector, publicSelectedKeysFieldOffset, selectedKeySet);
                            return null; // 无异常
                        }
                        // 我们不能检索偏移量，让我们尝试反射作为最后的手段。
                    }
                    // java8及以下
                    // 检查是否允许反射修改字段
                    Throwable cause = ReflectionUtil.trySetAccessible(selectedKeysField, true);
                    if (cause != null) {
                        return cause;
                    }
                    cause = ReflectionUtil.trySetAccessible(publicSelectedKeysField, true);
                    if (cause != null) {
                        return cause;
                    }
                    // 成功修改
                    selectedKeysField.set(unwrappedSelector, selectedKeySet);
                    publicSelectedKeysField.set(unwrappedSelector, selectedKeySet);
                    return null;
                } catch (NoSuchFieldException e) {
                    return e;
                } catch (IllegalAccessException e) {
                    return e;
                }
            }
        });

        // 检查异常
        if (maybeException instanceof Exception) {
            selectedKeys = null;
            Exception e = (Exception) maybeException;
            logger.trace("failed to instrument a special java.util.Set into: {}", unwrappedSelector, e);
            return new SelectorTuple(unwrappedSelector); // 未优化
        }
        //
        selectedKeys = selectedKeySet; // 优化后的数据结构
        logger.trace("instrumented a special java.util.Set into: {}", unwrappedSelector);

        // 包装
        return new SelectorTuple(unwrappedSelector,
                                 new SelectedSelectionKeySetSelector(unwrappedSelector, selectedKeySet));
    }

    /**
     * 返回此{@link NioEventLoop}使用的{@link SelectorProvider}以获取{@link选择器}。
     */
    public SelectorProvider selectorProvider() {
        return provider;
    }

    // 新建任务队列，mpsc队列
    @Override
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return newTaskQueue0(maxPendingTasks);
    }

    // 最大等待任务
    private static Queue<Runnable> newTaskQueue0(int maxPendingTasks) {
        // 事件循环不会调用 takeTask()
        return maxPendingTasks == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue()
                : PlatformDependent.<Runnable>newMpscQueue(maxPendingTasks);
    }

    /**
     * 注册一个任意的{@link SelectableChannel}(不一定是由Netty创建的)到这个事件循环的{@link Selector}。
     * 一旦注册了指定的{@link SelectableChannel}，当{@link SelectableChannel}准备好时，指定的{@code任务}将由该事件循环执行。
     */
    // 事实上不用这个方法，指示测试用
    public void register(final SelectableChannel ch, final int interestOps, final NioTask<?> task) {
        ObjectUtil.checkNotNull(ch, "ch");
        if (interestOps == 0) { // 兴趣事件为0的话
            throw new IllegalArgumentException("interestOps must be non-zero.");
        }
        if ((interestOps & ~ch.validOps()) != 0) { // 通道不支持操作
            throw new IllegalArgumentException(
                    "invalid interestOps: " + interestOps + "(validOps: " + ch.validOps() + ')');
        }
        ObjectUtil.checkNotNull(task, "task");

        if (isShutdown()) { // 关闭
            throw new IllegalStateException("event loop shut down");
        }

        if (inEventLoop()) { // 是否在线程内
            register0(ch, interestOps, task);
        } else {
            try {
                // 将其卸载到EventLoop，否则java.nil.channels.spl.abstractselectablechannel。
                // 寄存器可能阻塞很长一段时间，而试图获得一个内部锁，可能是持有而选择。
                submit(new Runnable() {
                    @Override
                    public void run() {
                        register0(ch, interestOps, task);
                    }
                }).sync();
            } catch (InterruptedException ignore) {
                // Even if interrupted we did schedule it so just mark the Thread as interrupted.
                Thread.currentThread().interrupt();
            }
        }
    }

    private void register0(SelectableChannel ch, int interestOps, NioTask<?> task) {
        try {
            ch.register(unwrappedSelector, interestOps, task);
        } catch (Exception e) {
            throw new EventLoopException("failed to register a channel", e);
        }
    }

    /**
     * 返回事件循环中I/O所需时间的百分比。
     */
    public int getIoRatio() {
        return ioRatio;
    }

    /**
     * 设置在事件循环中用于I/O的所需时间的百分比。值的范围为1-100。
     * 默认值是{@code 50}，这意味着事件循环将尝试为I/O任务花费与非I/O任务相同的时间。
     * 数目越少，花在非i /O任务上的时间就越多。如果值设置为
     * {@code 100}，此功能将被禁用，事件循环将不会尝试平衡I/O和非I/O任务。
     */
    public void setIoRatio(int ioRatio) {
        if (ioRatio <= 0 || ioRatio > 100) {
            throw new IllegalArgumentException("ioRatio: " + ioRatio + " (expected: 0 < ioRatio <= 100)");
        }
        this.ioRatio = ioRatio;
    }

    /**
     * 用新创建的{@link Selector}替换当前事件循环的{@link Selector}，以解决臭名昭著的epoll 100% CPU bug。
     */
    public void rebuildSelector() {
        // 是否在当前线程
        if (!inEventLoop()) {
            execute(new Runnable() {
                @Override
                public void run() {
                    rebuildSelector0();
                }
            });
            return;
        }
        rebuildSelector0();
    }

    // 注册keys数
    @Override
    public int registeredChannels() {
        return selector.keys().size() - cancelledKeys;
    }

    // 从新创建Selector
    private void rebuildSelector0() {
        final Selector oldSelector = selector;
        final SelectorTuple newSelectorTuple;

        if (oldSelector == null) {
            return;
        }

        try {
            newSelectorTuple = openSelector();
        } catch (Exception e) {
            logger.warn("Failed to create a new Selector.", e);
            return;
        }

        // 将所有通道注册到新的选择器。
        int nChannels = 0;
        for (SelectionKey key: oldSelector.keys()) {
            // 附加对象
            Object a = key.attachment();
            try {
                // key无效或Selector非当前Selector
                if (!key.isValid() || key.channel().keyFor(newSelectorTuple.unwrappedSelector) != null) {
                    continue;
                }
                // 感兴趣事件
                int interestOps = key.interestOps();
                // 取消key
                key.cancel();
                // 把key注册到新Selector上
                SelectionKey newKey = key.channel().register(newSelectorTuple.unwrappedSelector, interestOps, a);
                if (a instanceof AbstractNioChannel) { // 附加对象如果是AbstractNioChannel
                    // 更新 SelectionKey
                    ((AbstractNioChannel) a).selectionKey = newKey;
                }
                nChannels ++; // 有效key数量
            } catch (Exception e) {
                // 失败的话取消注册
                logger.warn("Failed to re-register a Channel to the new Selector.", e);
                if (a instanceof AbstractNioChannel) {
                    AbstractNioChannel ch = (AbstractNioChannel) a;
                    // 关闭channel要得到通知
                    ch.unsafe().close(ch.unsafe().voidPromise());
                } else { // 这个不知道啥情况，应该是计划以后弄
                    @SuppressWarnings("unchecked")
                    NioTask<SelectableChannel> task = (NioTask<SelectableChannel>) a;
                    invokeChannelUnregistered(task, key, e);
                }
            }
        }

        selector = newSelectorTuple.selector; // 包装后的选择器
        unwrappedSelector = newSelectorTuple.unwrappedSelector; // 未包装选择器

        try {
            // 是时候关闭旧的选择器了，因为所有的东西都注册到了新的选择器
            oldSelector.close();
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close the old Selector.", t);
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("Migrated " + nChannels + " channel(s) to the new Selector.");
        }
    }

    // 执行逻辑
    @Override
    protected void run() {
        int selectCnt = 0; // 选择次数
        for (;;) {
            try {
                int strategy;
                try {
                    // 是否有任务，如果有任务则立即select一次，如果无任务则阻塞
                    strategy = selectStrategy.calculateStrategy(selectNowSupplier, hasTasks());
                    switch (strategy) {
                    case SelectStrategy.CONTINUE: // 如果继续循环
                        continue;

                    case SelectStrategy.BUSY_WAIT: // 不阻塞情况下轮询
                        // 由于NIO不支持忙碌等待，因此无法选择

                    case SelectStrategy.SELECT: // 阻塞操作
                        // 下一个定时任务等待时间
                        long curDeadlineNanos = nextScheduledTaskDeadlineNanos();
                        // 如果无定时任务
                        if (curDeadlineNanos == -1L) {
                            curDeadlineNanos = NONE;
                        }
                        // 设置唤醒时间
                        nextWakeupNanos.set(curDeadlineNanos);
                        try {
                            // 如果还是无任务
                            if (!hasTasks()) {
                                // 阻塞轮询
                                strategy = select(curDeadlineNanos);
                            }
                        } finally {
                            // 这个更新只是为了帮助阻止不必要的选择器唤醒
                            // 使用lazySet是可以的(没有竞争条件)
                            // 非立即可见
                            nextWakeupNanos.lazySet(AWAKE);
                        }
                        // fall through
                    default:
                    }
                } catch (IOException e) {
                    // 如果我们在这里接收到IOException，那是因为选择器混乱了。让我们重新构建选择器并重试。
                    // https://github.com/netty/netty/issues/8566
                    rebuildSelector0();
                    selectCnt = 0;
                    handleLoopException(e); // 打印异常
                    continue;
                }

                selectCnt++; // 选择次数+1
                cancelledKeys = 0; // 取消keys
                needsToSelectAgain = false; // 是否需要再次选择
                final int ioRatio = this.ioRatio; // io比率
                boolean ranTasks; // 是否运行任务
                if (ioRatio == 100) { // 100的话禁用此功能
                    try {
                        if (strategy > 0) { // 说明有事件
                            processSelectedKeys(); // 直接执行事件
                        }
                    } finally {
                        // 确保我们始终运行任务。
                        ranTasks = runAllTasks();
                    }
                } else if (strategy > 0) {
                    // 时间
                    final long ioStartTime = System.nanoTime();
                    try {
                        processSelectedKeys();
                    } finally {
                        // 确保我们始终运行任务。
                        // 执行任务的时间
                        final long ioTime = System.nanoTime() - ioStartTime;
                        ranTasks = runAllTasks(ioTime * (100 - ioRatio) / ioRatio);
                    }
                } else {
                    // 不io，一直运行任务
                    ranTasks = runAllTasks(0); // 这将运行最小数量的任务
                }

                // 运行了任务或者执行了io
                if (ranTasks || strategy > 0) {
                    // 选择了3次
                    if (selectCnt > MIN_PREMATURE_SELECTOR_RETURNS && logger.isDebugEnabled()) {
                        logger.debug("Selector.select() returned prematurely {} times in a row for Selector {}.",
                                selectCnt - 1, selector);
                    }
                    selectCnt = 0; // 重置
                } else if (unexpectedSelectorWakeup(selectCnt)) { // 不希望唤醒 (unusual case)
                    // 重置成功
                    selectCnt = 0;
                }
            } catch (CancelledKeyException e) {  // key被取消异常
                // 无害异常-日志
                if (logger.isDebugEnabled()) {
                    logger.debug(CancelledKeyException.class.getSimpleName() + " raised by a Selector {} - JDK bug?",
                            selector, e);
                }
            } catch (Throwable t) {
                // 处理轮询异常
                handleLoopException(t);
            }
            // 始终处理关机，即使循环处理抛出异常。
            try {
                if (isShuttingDown()) {
                    closeAll();
                    // 确定关闭
                    if (confirmShutdown()) {
                        return;
                    }
                }
            } catch (Throwable t) {
                handleLoopException(t);
            }
        }
    }

    // 如果selectCnt应该重置，则返回true
    private boolean unexpectedSelectorWakeup(int selectCnt) {
        // 检查线程中断
        if (Thread.interrupted()) {
            // 线程被中断，所以重置选择的键并中断，这样我们就不会进入一个繁忙的循环。
            // 因为这很可能是用户的处理程序或它的客户端库中的错误，我们将
            // 还需要进行日志记录。
            //
            // See https://github.com/netty/netty/issues/2426
            if (logger.isDebugEnabled()) {
                logger.debug("Selector.select() returned prematurely because " +
                        "Thread.currentThread().interrupt() was called. Use " +
                        "NioEventLoop.shutdownGracefully() to shutdown the NioEventLoop.");
            }
            return true;
        }
        if (SELECTOR_AUTO_REBUILD_THRESHOLD > 0 &&
                selectCnt >= SELECTOR_AUTO_REBUILD_THRESHOLD) {
            // 选择器在一行中提前返回许多次。
            // 重新构建选择器以解决这个问题。
            logger.warn("Selector.select() returned prematurely {} times in a row; rebuilding Selector {}.",
                    selectCnt, selector);
            rebuildSelector();
            return true;
        }
        return false;
    }

    // 处理循环异常
    private static void handleLoopException(Throwable t) {
        logger.warn("Unexpected exception in the selector loop.", t);

        // 防止可能导致过多CPU消耗的连续即时故障。
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Ignore.
        }
    }

    // 执行选择key
    private void processSelectedKeys() {
        if (selectedKeys != null) {
            // 优化后
            processSelectedKeysOptimized();
        } else {
            // 未优化
            processSelectedKeysPlain(selector.selectedKeys());
        }
    }

    // 关闭选择器
    @Override
    protected void cleanup() {
        try {
            selector.close();
        } catch (IOException e) {
            logger.warn("Failed to close a selector.", e);
        }
    }

    // 取消key
    void cancel(SelectionKey key) {
        key.cancel();
        cancelledKeys ++; // 取消key++
        // 取消key达到清理间隔
        if (cancelledKeys >= CLEANUP_INTERVAL) {
            cancelledKeys = 0;
            // 轮询取消key
            needsToSelectAgain = true;
        }
    }

    // 未优化
    private void processSelectedKeysPlain(Set<SelectionKey> selectedKeys) {
        // 检查该集合是否为空，如果是空的，则每次返回时都创建一个新的迭代器，即使没有需要处理的东西，也不要创建垃圾。
        // See https://github.com/netty/netty/issues/597
        if (selectedKeys.isEmpty()) {
            return;
        }
        // 循环处理
        Iterator<SelectionKey> i = selectedKeys.iterator();
        for (;;) {
            final SelectionKey k = i.next();
            // channel
            final Object a = k.attachment();
            // 移除key
            i.remove();

            if (a instanceof AbstractNioChannel) {
                // 处理通道
                processSelectedKey(k, (AbstractNioChannel) a);
            } else {
                // 暂未使用
                @SuppressWarnings("unchecked")
                NioTask<SelectableChannel> task = (NioTask<SelectableChannel>) a;
                processSelectedKey(k, task);
            }

            if (!i.hasNext()) {
                break;
            }
            // 再次轮询
            if (needsToSelectAgain) {
                selectAgain();
                selectedKeys = selector.selectedKeys();

                // Create the iterator again to avoid ConcurrentModificationException
                if (selectedKeys.isEmpty()) {
                    break;
                } else {
                    i = selectedKeys.iterator();
                }
            }
        }
    }

    // 优化后的事件轮询
    private void processSelectedKeysOptimized() {
        for (int i = 0; i < selectedKeys.size; ++i) {
            // 事件key
            final SelectionKey k = selectedKeys.keys[i];
            // 空出数组中的项，以便在通道关闭后对其进行GC
            // See https://github.com/netty/netty/issues/2363
            selectedKeys.keys[i] = null;

            final Object a = k.attachment(); // 通道

            if (a instanceof AbstractNioChannel) { // 若是通道

                processSelectedKey(k, (AbstractNioChannel) a);
            } else {
                // 暂时没有使用
                @SuppressWarnings("unchecked")
                NioTask<SelectableChannel> task = (NioTask<SelectableChannel>) a;
                processSelectedKey(k, task);
            }

            // 取消key多的话，采集select，删除key，优化
            if (needsToSelectAgain) {
                // 空出数组中的项，以便在通道关闭后对其进行GC
                // See https://github.com/netty/netty/issues/2363
                // 清空后再次轮询
                selectedKeys.reset(i + 1);
                selectAgain();
                i = -1;
            }
        }
    }

    // 执行选择key
    private void processSelectedKey(SelectionKey k, AbstractNioChannel ch) {
        // 拿到Unsafe
        final AbstractNioChannel.NioUnsafe unsafe = ch.unsafe();
        if (!k.isValid()) { // key无效
            final EventLoop eventLoop;
            try {
                eventLoop = ch.eventLoop(); // 通道的执行器
            } catch (Throwable ignored) {
                // 如果通道实现因为没有事件循环而抛出异常，我们将忽略此异常，
                // 因为我们只是试图确定ch是否已注册到该事件循环，因此有权关闭ch。
                return;
            }
            // 只有在ch仍然注册到这个EventLoop时才关闭ch。ch可能已经从事件循环取消注册，
            // 因此可以取消SelectionKey作为取消注册过程的一部分，但是通道仍然健康，不应该关闭。
            // See https://github.com/netty/netty/issues/5125
            if (eventLoop == this) {
                // 如果key不再有效，请关闭通道
                unsafe.close(unsafe.voidPromise());
            }
            return;
        }

        try {
            // 兴趣事件
            int readyOps = k.readyOps();
            // 在尝试触发读(…)或写(…)之前，我们首先需要调用finishConnect()，
            // 否则NIO JDK通道实现可能会抛出NotYetConnectedException。
            if ((readyOps & SelectionKey.OP_CONNECT) != 0) { // 若是连接事件
                // 删除OP_CONNECT，否则Selector.select(..)将始终不阻塞返回
                // See https://github.com/netty/netty/issues/924
                int ops = k.interestOps(); // 感兴趣事件
                // 如果对连接事件感兴趣，则置为0
                ops &= ~SelectionKey.OP_CONNECT;

                k.interestOps(ops);
                // 结束连接
                unsafe.finishConnect();
            }

            // 先处理OP_WRITE，因为我们可以写一些队列缓冲区，这样就可以释放内存。
            if ((readyOps & SelectionKey.OP_WRITE) != 0) { // 如果是写事件
                // 调用forceFlush，它还会在没有东西可写时清除OP_WRITE
                ch.unsafe().forceFlush();
            }

            // 还要检查readOps是否为0，以解决JDK中可能导致的错误
            // 变成一个自旋循环
            if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
                unsafe.read();
            }
        } catch (CancelledKeyException ignored) {
            unsafe.close(unsafe.voidPromise());
        }
    }

    // 这个暂未使用
    private static void processSelectedKey(SelectionKey k, NioTask<SelectableChannel> task) {
        int state = 0;
        try {
            task.channelReady(k.channel(), k);
            state = 1;
        } catch (Exception e) {
            k.cancel();
            invokeChannelUnregistered(task, k, e);
            state = 2;
        } finally {
            switch (state) {
            case 0:
                k.cancel();
                invokeChannelUnregistered(task, k, null);
                break;
            case 1:
                if (!k.isValid()) { // Cancelled by channelReady()
                    invokeChannelUnregistered(task, k, null);
                }
                break;
            default:
                 break;
            }
        }
    }

    // 关闭
    private void closeAll() {
        // 再次select
        selectAgain();
        // 拿到注册key
        Set<SelectionKey> keys = selector.keys();

        Collection<AbstractNioChannel> channels = new ArrayList<AbstractNioChannel>(keys.size());
        // 拿到channel
        for (SelectionKey k: keys) {
            Object a = k.attachment();
            if (a instanceof AbstractNioChannel) {
                channels.add((AbstractNioChannel) a);
            } else {
                k.cancel();
                @SuppressWarnings("unchecked")
                NioTask<SelectableChannel> task = (NioTask<SelectableChannel>) a;
                invokeChannelUnregistered(task, k, null);
            }
        }
        // 关闭channel
        for (AbstractNioChannel ch: channels) {
            ch.unsafe().close(ch.unsafe().voidPromise());
        }
    }

    // channel取消注册
    private static void invokeChannelUnregistered(NioTask<SelectableChannel> task, SelectionKey k, Throwable cause) {
        try {
            // 取消注册
            task.channelUnregistered(k.channel(), cause);
        } catch (Exception e) {
            logger.warn("Unexpected exception while running NioTask.channelUnregistered()", e);
        }
    }

    // 唤醒
    @Override
    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop && nextWakeupNanos.getAndSet(AWAKE) != AWAKE) {
            selector.wakeup();
        }
    }

    // 调度提交的任务之前
    @Override
    protected boolean beforeScheduledTaskSubmitted(long deadlineNanos) {
        // 注意，这对于nextWakeupNanos == -1 (AWAKE)情况也是正确的
        return deadlineNanos < nextWakeupNanos.get();
    }

    // 调度提交的任务之后
    @Override
    protected boolean afterScheduledTaskSubmitted(long deadlineNanos) {
        // 注意，这对于nextWakeupNanos == -1 (AWAKE)情况也是正确的
        return deadlineNanos < nextWakeupNanos.get();
    }

    // 返回未包装的selector
    Selector unwrappedSelector() {
        return unwrappedSelector;
    }

    // 立即返回，key size
    int selectNow() throws IOException {
        return selector.selectNow();
    }

    // 轮询
    private int select(long deadlineNanos) throws IOException {
        if (deadlineNanos == NONE) {
            return selector.select();
        }
        // 如果截止日期在5微秒内，立即selectNow
        long timeoutMillis = deadlineToDelayNanos(deadlineNanos + 995000L) / 1000000L;
        return timeoutMillis <= 0 ? selector.selectNow() : selector.select(timeoutMillis);
    }

    // 再次轮询
    private void selectAgain() {
        needsToSelectAgain = false;
        try {
            // 更新SelectionKeys
            selector.selectNow();
        } catch (Throwable t) {
            logger.warn("Failed to update SelectionKeys.", t);
        }
    }
}
