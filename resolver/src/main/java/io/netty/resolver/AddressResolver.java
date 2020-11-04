/*
 * Copyright 2015 The Netty Project
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
import java.net.SocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.List;

/**
 * 解决可能未解决的{@link SocketAddress}。
 */
public interface AddressResolver<T extends SocketAddress> extends Closeable {

  /**
   * 当且仅当此解析支持指定的地址时，返回{@code true}。
   */
  boolean isSupported(SocketAddress address);

  /**
   * 返回{@code true}，当且仅当指定的地址已被解析。
   *
   * @throws UnsupportedAddressTypeException 如果指定的地址不被此解析器支持
   */
  boolean isResolved(SocketAddress address);

  /**
   * 解析指定的地址。如果指定的地址已经解析，此方法只返回原始地址。
   *
   * @param address the address to resolve
   *
   * @return 作为解析结果的{@link SocketAddress}
   */
  Future<T> resolve(SocketAddress address);

  /**
   * 解析指定的地址。如果指定的地址已经解析，此方法只返回原始地址。
   *
   * @param address 要解析的地址
   * @param promise 当名称解析完成时，{@link Promise}将被实现
   *
   * @return 作为解析结果的{@link SocketAddress}
   */
  Future<T> resolve(SocketAddress address, Promise<T> promise);

  /**
   * 解析指定的地址。如果指定的地址已经解析，此方法只返回原始地址。
   *
   * @param address the address to resolve
   *
   * @return the list of the {@link SocketAddress}es as the result of the resolution
   */
  Future<List<T>> resolveAll(SocketAddress address);

  /**
   * 解析指定的地址。如果指定的地址已经解析，此方法只返回原始地址。
   *
   * @param address 要解析的地址
   * @param promise 当名称解析完成时，{@link Promise}将被实现
   *
   * @return the list of the {@link SocketAddress}es as the result of the resolution
   */
  Future<List<T>> resolveAll(SocketAddress address, Promise<List<T>> promise);

  /**
   * 关闭此解析器分配和使用的所有资源。
   */
  @Override
  void close();
}
