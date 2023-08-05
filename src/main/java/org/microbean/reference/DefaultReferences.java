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

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;

import java.util.concurrent.atomic.AtomicReference;

import java.util.function.Function;

import org.microbean.bean.Bean;
import org.microbean.bean.BeanSet;
import org.microbean.bean.Id;
import org.microbean.bean.Instances;
import org.microbean.bean.References;
import org.microbean.bean.Selector;
import org.microbean.bean.UnsatisfiedResolutionException;

public final class DefaultReferences<R> implements References<R> {


  /*
   * Instance fields.
   */


  private final Selector selector;

  // Treat as effectively final, please.
  private Instances instances;

  // Treat as effectively final, please.
  private ClientProxier clientProxier;

  // TODO: see, perhaps: https://stackoverflow.com/a/37403083/208288 and
  // https://github.com/spring-projects/spring-loaded/blob/master/springloaded/src/main/java/org/springsource/loaded/support/ConcurrentWeakIdentityHashMap.java
  
  // @GuardedBy("itself")
  private final IdentityHashMap<R, Id> ids;


  /*
   * Constructors.
   */


  public DefaultReferences(final Selector selector, final Instances bootstrapInstances) {
    this(selector, bootstrapInstances, null);
  }

  public DefaultReferences(final Selector selector, final Instances bootstrapInstances, final ClientProxier clientProxier) {
    super();
    this.ids = new IdentityHashMap<>();
    this.selector = Objects.requireNonNull(selector, "selector");
    this.instances = Objects.requireNonNull(bootstrapInstances, "bootstrapInstances"); // possibly temporary
    this.clientProxier = clientProxier == null ? NoopClientProxier.INSTANCE : clientProxier; // possibly temporary
    this.initialize();
  }

  // Non-bootstrapping; non-validating; intended for clone-like operations
  private DefaultReferences(final IdentityHashMap<R, Id> ids,
                            final Selector selector,
                            final Instances instances,
                            final ClientProxier clientProxier) {
    super();
    this.ids = ids;
    this.selector = selector;
    this.instances = instances;
    this.clientProxier = clientProxier;
  }


  /*
   * Instance methods.
   */


  private final void initialize() {
    Selector s = new Selector(Instances.class);
    Bean<?> b = this.beanSet().bean(s);
    if (b != null) {
      this.instances = Objects.requireNonNull(this.supplyReference(s, b.cast()), "this.supplyReference(" + s + ", " + b + ")");
    }

    s = new Selector(ClientProxier.class);
    b = this.beanSet().bean(s);
    if (b != null) {
      this.clientProxier = Objects.requireNonNull(this.supplyReference(s, b.cast()), "this.supplyReference(" + s + ", " + b + ")");
    }
  }

  @Override // References<R>
  public final BeanSet beanSet() {
    return this.instances.beanSet();
  }

  @Override // References<R>
  public final Iterator<R> iterator() {
    return new ReferenceIterator(this.beanSet().beans(this.selector).iterator());
  }

  @Override // References<R>
  @SuppressWarnings("unchecked")
  public final <R2 extends R> DefaultReferences<R2> withSelector(final Selector selector) {
    return (DefaultReferences<R2>)new DefaultReferences<>(this.ids, Objects.requireNonNull(selector, "selector"), this.instances, this.clientProxier);
  }

  @Override // References<R>
  public final boolean destroy(final R r) {
    if (r != null) {
      synchronized (this.ids) {
        return this.remove(this.ids.remove(r));
      }
    }
    return false;
  }

  @Override // AutoCloseable
  public final void close() {
    synchronized (this.ids) {
      final Iterator<Entry<R, Id>> i = this.ids.entrySet().iterator();
      while (i.hasNext()) {
        this.remove(i.next().getValue());
        i.remove();
      }
    }
  }

  private final boolean remove(final Id id) {
    return id != null && this.instances.remove(id);
  }
  
  @Override // References<R>
  public final <R> R supplyReference(final Selector selector) {
    return this.supplyReference(selector, null);
  }
  
  @Override // References
  public final <R> R supplyReference(final Selector selector, Bean<R> bean) {
    if (bean == null) {
      final Bean<?> b = this.beanSet().bean(selector);
      if (b == null) {
        throw new UnsatisfiedResolutionException(selector);
      }
      bean = b.cast();
    } else if (!selector.selects(bean)) {
      throw new IllegalArgumentException("bean: " + bean);
    }
    return
      this.clientProxier.needsClientProxy(selector, bean.id(), this.instances) ?
      this.clientProxier.clientProxy(selector, bean, this.instances) :
      this.instances.supply(selector, bean);
  }


  /*
   * Inner and nested classes.
   */


  private final class ReferenceIterator implements Iterator<R> {


    /*
     * Instance fields.
     */


    private final Iterator<? extends Bean<?>> beanIterator;

    private final AtomicReference<R> last;


    /*
     * Constructors.
     */


    private ReferenceIterator(final Iterator<? extends Bean<?>> beanIterator) {
      super();
      this.beanIterator = Objects.requireNonNull(beanIterator, "beanIterator");
      this.last = new AtomicReference<>();
    }


    /*
     * Instance methods.
     */


    @Override // Iterator<R>
    public final boolean hasNext() {
      return this.beanIterator.hasNext();
    }

    @Override // Iterator<R>
    public final R next() {
      final Bean<R> bean = this.beanIterator.next().cast();
      final R r = DefaultReferences.this.supplyReference(DefaultReferences.this.selector, bean);
      synchronized (DefaultReferences.this.ids) {
        DefaultReferences.this.ids.putIfAbsent(r, bean.id());
      }
      return this.last.updateAndGet(old -> r);
    }

    @Override // Iterator<R>
    public final void remove() {
      final R r = this.last.getAndSet(null);
      if (r != null) {
        DefaultReferences.this.destroy(r);
      }
    }

  }
  
  private static final class NoopClientProxier implements ClientProxier {

    private static final NoopClientProxier INSTANCE = new NoopClientProxier();

    private NoopClientProxier() {
      super();
    }

    @Override // ClientProxier
    public final boolean needsClientProxy(final Selector s, final Id id, final Instances instances) {
      return false;
    }

    @Override // ClientProxier
    public final <R> R clientProxy(final Selector s, final Bean<R> b, final Instances instances) {
      throw new DynamicClientProxiesNotSupportedException();
    }

  }

}
