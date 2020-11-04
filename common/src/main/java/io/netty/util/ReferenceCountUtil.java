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

import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * 用于处理可能实现{@link ReferenceCounted}的对象的方法集合。
 */
public final class ReferenceCountUtil {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ReferenceCountUtil.class);

    static {
        ResourceLeakDetector.addExclusions(ReferenceCountUtil.class, "touch");
    }

    /**
     * 如果指定的消息实现了{@link ReferenceCounted#retain()}，则尝试调用{@link ReferenceCounted}。
     * 如果指定的消息没有实现{@link ReferenceCounted}，那么这个方法什么都不做。
     */
    @SuppressWarnings("unchecked")
    public static <T> T retain(T msg) {
        if (msg instanceof ReferenceCounted) {
            return (T) ((ReferenceCounted) msg).retain();
        }
        return msg;
    }

    /**
     * 如果指定的消息实现了{@link ReferenceCounted#retain(int)}，则尝试调用{@link ReferenceCounted}。
     * 如果指定的消息没有实现{@link ReferenceCounted}，那么这个方法什么都不做。
     */
    @SuppressWarnings("unchecked")
    public static <T> T retain(T msg, int increment) {
        if (msg instanceof ReferenceCounted) {
            return (T) ((ReferenceCounted) msg).retain(increment);
        }
        return msg;
    }

    /**
     * 如果指定的消息实现了{@link ReferenceCounted#touch()}，则尝试调用{@link ReferenceCounted}。
     * 如果指定的消息没有实现{@link ReferenceCounted}，那么这个方法什么都不做。
     */
    @SuppressWarnings("unchecked")
    public static <T> T touch(T msg) {
        if (msg instanceof ReferenceCounted) {
            return (T) ((ReferenceCounted) msg).touch();
        }
        return msg;
    }

    /**
     * 如果指定的消息实现了{@link ReferenceCounted#touch(Object)}，
     * 则尝试调用{@link ReferenceCounted}。 如果指定的消息没有实现{@link ReferenceCounted}，本方法不做任何操作。
     */
    @SuppressWarnings("unchecked")
    public static <T> T touch(T msg, Object hint) {
        if (msg instanceof ReferenceCounted) {
            return (T) ((ReferenceCounted) msg).touch(hint);
        }
        return msg;
    }

    /**
     * 如果指定的消息实现了{@link ReferenceCounted#release()}，则尝试调用{@link ReferenceCounted}。
     * 如果指定的消息没有实现{@link ReferenceCounted}，那么这个方法什么都不做。
     */
    public static boolean release(Object msg) {
        if (msg instanceof ReferenceCounted) {
            return ((ReferenceCounted) msg).release();
        }
        return false;
    }

    /**
     * 如果指定的消息实现了{@link ReferenceCounted#release(int)}，则尝试调用{@link ReferenceCounted}。
     * 如果指定的消息没有实现{@link ReferenceCounted}，那么这个方法什么都不做。
     */
    public static boolean release(Object msg, int decrement) {
        if (msg instanceof ReferenceCounted) {
            return ((ReferenceCounted) msg).release(decrement);
        }
        return false;
    }

    /**
     * 如果指定的消息实现了{@link ReferenceCounted#release()}，则尝试调用{@link ReferenceCounted}。
     * 如果指定的消息没有实现{@link ReferenceCounted}，那么这个方法什么都不做。
     * 与{@link #release(Object)}不同的是，本方法捕获了由{@link ReferenceCounted#release()}引发的异常并将其记录下来，
     * 而不是将其重新抛给调用者。 通常建议使用{@link #release(Object)}代替，除非你绝对需要吞下一个异常。
     */
    public static void safeRelease(Object msg) {
        try {
            release(msg);
        } catch (Throwable t) {
            logger.warn("Failed to release a message: {}", msg, t);
        }
    }

    /**
     * 如果指定的消息实现了{@link ReferenceCounted#release(int)}，则尝试调用{@link ReferenceCounted}。
     * 如果指定的消息没有实现{@link ReferenceCounted}，那么这个方法什么都不做。
     * 与{@link #release(Object)}不同的是，这个方法捕获了由{@link ReferenceCounted#release(int)}引发的异常并将其记录下来，
     * 而不是将其重新抛给调用者。 通常建议使用{@link #release(Object, int)}代替，除非你绝对需要吞下一个异常。
     */
    public static void safeRelease(Object msg, int decrement) {
        try {
            release(msg, decrement);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to release a message: {} (decrement: {})", msg, decrement, t);
            }
        }
    }

    /**
     * 调度指定对象在调用者线程终止时被释放。请注意，此操作旨在简化单元测试期间对短暂对象的引用计数。不要在预定的用例之外使用它。
     *
     * @deprecated 这可能会引入大量的内存使用，所以一般最好是手动释放对象。
     */
    @Deprecated
    public static <T> T releaseLater(T msg) {
        return releaseLater(msg, 1);
    }

    /**
     * 调度指定对象在调用者线程终止时被释放。请注意，这个操作的目的是为了简化单元测试期间对短暂对象的引用计数。不要在预定的用例之外使用它。
     *
     * @deprecated 这可能会引入大量的内存使用，所以一般最好是手动释放对象。
     */
    @Deprecated
    public static <T> T releaseLater(T msg, int decrement) {
        if (msg instanceof ReferenceCounted) {
            ThreadDeathWatcher.watch(Thread.currentThread(), new ReleasingTask((ReferenceCounted) msg, decrement));
        }
        return msg;
    }

    /**
     * 返回{@link ReferenceCounted}对象的引用次数。如果对象的类型不是{@link ReferenceCounted}，则返回{@code -1}。
     */
    public static int refCnt(Object msg) {
        return msg instanceof ReferenceCounted ? ((ReferenceCounted) msg).refCnt() : -1;
    }

    /**
     * 当调用{@link #releaseLater(Object)}的线程被终止时，释放对象。
     */
    private static final class ReleasingTask implements Runnable {

        private final ReferenceCounted obj;
        private final int decrement;

        ReleasingTask(ReferenceCounted obj, int decrement) {
            this.obj = obj;
            this.decrement = decrement;
        }

        @Override
        public void run() {
            try {
                if (!obj.release(decrement)) {
                    logger.warn("Non-zero refCnt: {}", this);
                } else {
                    logger.debug("Released: {}", this);
                }
            } catch (Exception ex) {
                logger.warn("Failed to release an object: {}", obj, ex);
            }
        }

        @Override
        public String toString() {
            return StringUtil.simpleClassName(obj) + ".release(" + decrement + ") refCnt: " + obj.refCnt();
        }
    }

    private ReferenceCountUtil() { }
}
