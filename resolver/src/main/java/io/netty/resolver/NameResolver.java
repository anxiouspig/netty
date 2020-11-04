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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.io.Closeable;
import java.util.List;

/**
 * 将表示端点名称的任意字符串解析为地址。
 */
public interface NameResolver<T> extends Closeable {

    /**
     * 将指定的名称解析为地址。
     *
     * @param inetHost the name to resolve
     *
     * @return the address as the result of the resolution
     */
    Future<T> resolve(String inetHost);

    /**
     * 将指定的名称解析为地址。
     *
     * @param inetHost the name to resolve
     * @param promise the {@link Promise} which will be fulfilled when the name resolution is finished
     *
     * @return the address as the result of the resolution
     */
    Future<T> resolve(String inetHost, Promise<T> promise);

    /**
     * 将指定的主机名和端口解析为地址列表。
     *
     * @param inetHost the name to resolve
     *
     * @return the list of the address as the result of the resolution
     */
    Future<List<T>> resolveAll(String inetHost);

    /**
     * 将指定的主机名和端口解析为地址列表。
     *
     * @param inetHost the name to resolve
     * @param promise the {@link Promise} which will be fulfilled when the name resolution is finished
     *
     * @return the list of the address as the result of the resolution
     */
    Future<List<T>> resolveAll(String inetHost, Promise<List<T>> promise);

    /**
     * 关闭此解析器分配和使用的所有资源。
     */
    @Override
    void close();
}
