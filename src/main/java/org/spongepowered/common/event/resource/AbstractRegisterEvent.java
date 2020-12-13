/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.event.resource;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.Engine;
import org.spongepowered.api.event.Cause;
import org.spongepowered.api.event.GenericEvent;
import org.spongepowered.api.event.impl.AbstractEvent;

import java.util.LinkedList;
import java.util.List;

public abstract class AbstractRegisterEvent<E extends Engine, T> extends AbstractEvent implements GenericEvent<E> {

    private final Cause cause;
    private final TypeToken<E> type;
    private final List<T> list = new LinkedList<>();

    public AbstractRegisterEvent(Cause cause, TypeToken<E> type) {
        this.cause = cause;
        this.type = type;
    }

    @Override
    public Cause getCause() {
        return cause;
    }

    @Override
    public TypeToken<E> getGenericType() {
        return type;
    }

    public void register(@NonNull T obj) {
        list.add(Preconditions.checkNotNull(obj));
    }

    protected List<T> getInternalList() {
        return list;
    }
}