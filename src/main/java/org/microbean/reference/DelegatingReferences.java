/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.microbean.reference;

import java.util.Iterator;
import java.util.Objects;

import org.microbean.bean.Bean;
import org.microbean.bean.BeanSet;
import org.microbean.bean.Creation;
import org.microbean.bean.References;
import org.microbean.bean.Selector;

class DelegatingReferences<R> implements References<R> {

  private volatile References<R> delegate;

  DelegatingReferences(final References<R> delegate) {
    super();
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public final BeanSet beanSet() {
    return this.delegate.beanSet();
  }

  @Override
  public final Cardinality cardinality() {
    return this.delegate.cardinality();
  }

  @Override
  public final void close() {
    this.delegate.close();
  }

  @Override
  public final <I> Creation<I> creation() {
    return this.delegate.creation();
  }

  final References<R> delegate() {
    return this.delegate;
  }

  final DelegatingReferences<R> delegate(final References<R> delegate) {
    if (Objects.requireNonNull(delegate, "delegate") == this) {
      throw new IllegalArgumentException("delegate == this");
    }
    this.delegate = delegate;
    return this;
  }

  @Override
  public final boolean destroy(final R r) {
    return this.delegate.destroy(r);
  }

  @Override
  public final R get() {
    return this.delegate.get();
  }

  @Override
  public final Iterator<R> iterator() {
    return this.delegate.iterator();
  }

  @Override
  public final <R> R reference(final Selector selector, final Creation<R> creation) {
    return this.delegate.reference(selector, creation);
  }

  @Override
  public final <R> R reference(final Selector selector, final Bean<R> bean, final Creation<R> creation) {
    return this.delegate.reference(selector, bean, creation);
  }


}
