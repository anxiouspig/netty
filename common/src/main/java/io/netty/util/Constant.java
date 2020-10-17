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
package io.netty.util;

/**
 * 一个可以通过{@code ==}操作符进行安全比较的单个值，由{@link ConstantPool}创建和管理。
 */
public interface Constant<T extends Constant<T>> extends Comparable<T> {

    /**
     * 返回分配给这个{@link Constant}的唯一编号。
     */
    int id();

    /**
     * 返回这个{@link Constant}的名称。
     */
    String name();
}
