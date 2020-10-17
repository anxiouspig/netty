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
package io.netty.util.internal;

import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;

/**
 * 通知 {@link Promise}s 的内部工具.
 */
public final class PromiseNotificationUtil {

    private PromiseNotificationUtil() { }

    /**
     * 试图取消 {@link Promise} ，如果{@code logger}不是{@code null}，以防失败。
     */
    public static void tryCancel(Promise<?> p, InternalLogger logger) {
        if (!p.cancel(false) && logger != null) {
            Throwable err = p.cause();
            if (err == null) {
                logger.warn("Failed to cancel promise because it has succeeded already: {}", p);
            } else {
                logger.warn(
                        "Failed to cancel promise because it has failed already: {}, unnotified cause:",
                        p, err);
            }
        }
    }

    /**
     * 试图标记 {@link Promise} 为成功， 并在{@code logger}不是{@code null}时进行记录，以防失败。
     */
    public static <V> void trySuccess(Promise<? super V> p, V result, InternalLogger logger) {
        if (!p.trySuccess(result) && logger != null) {
            Throwable err = p.cause();
            if (err == null) {
                logger.warn("Failed to mark a promise as success because it has succeeded already: {}", p);
            } else {
                logger.warn(
                        "Failed to mark a promise as success because it has failed already: {}, unnotified cause:",
                        p, err);
            }
        }
    }

    /**
     * 试图标记 {@link Promise} 为失败， 并在{@code logger}不是{@code null}时进行记录，以防失败。
     */
    public static void tryFailure(Promise<?> p, Throwable cause, InternalLogger logger) {
        if (!p.tryFailure(cause) && logger != null) {
            Throwable err = p.cause();
            if (err == null) {
                logger.warn("Failed to mark a promise as failure because it has succeeded already: {}", p, cause);
            } else if (logger.isWarnEnabled()) {
                logger.warn(
                        "Failed to mark a promise as failure because it has failed already: {}, unnotified cause: {}",
                        p, ThrowableUtil.stackTraceToString(err), cause);
            }
        }
    }

}
