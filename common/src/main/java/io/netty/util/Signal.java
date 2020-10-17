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
 * 一个特殊的{@link Error}，用于通过抛出错误来提示某些状态或请求。
 * {@link Signal}有一个空的堆栈跟踪，没有原因节省实例化开销。
 */
public final class Signal extends Error implements Constant<Signal> {

    private static final long serialVersionUID = -221145131122459977L; // 序列化id

    // key池，静态属性，全局唯一内容池
    private static final ConstantPool<Signal> pool = new ConstantPool<Signal>() {
        @Override
        protected Signal newConstant(int id, String name) {
            return new Signal(id, name);
        }
    };

    /**
     * 返回指定名称的{@link Signal}。
     */
    public static Signal valueOf(String name) {
        return pool.valueOf(name);
    }

    /**
     * {@链接#valueOf(String) valueOf(firstNameComponent.getName()+"#"+secondNameComponent)}的快捷方式。
     */
    public static Signal valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return pool.valueOf(firstNameComponent, secondNameComponent);
    }

    private final SignalConstant constant;

    /**
     * 用指定的{@code name}创建一个新的{@link Signal}。
     */
    private Signal(int id, String name) {
        constant = new SignalConstant(id, name);
    }

    /**
     * 检查给定的{@link Signal}是否与本实例相同。如果不是，则会出现一个{@link IllegalStateException}。
     */
    public void expect(Signal signal) {
        if (this != signal) {
            throw new IllegalStateException("unexpected signal: " + signal);
        }
    }

    @Override
    public Throwable initCause(Throwable cause) {
        return this;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public int id() {
        return constant.id();
    }

    @Override
    public String name() {
        return constant.name();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public int compareTo(Signal other) {
        if (this == other) {
            return 0;
        }

        return constant.compareTo(other.constant);
    }

    @Override
    public String toString() {
        return name();
    }

    private static final class SignalConstant extends AbstractConstant<SignalConstant> {
        SignalConstant(int id, String name) {
            super(id, name);
        }
    }
}
