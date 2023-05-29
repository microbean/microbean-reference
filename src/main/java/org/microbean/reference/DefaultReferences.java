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
import org.microbean.bean.Id;
import org.microbean.bean.Instances;
import org.microbean.bean.References;
import org.microbean.bean.Selector;
import org.microbean.bean.UnsatisfiedResolutionException;

import static org.microbean.bean.Qualifiers.defaultQualifiers;

import static org.microbean.lang.Lang.declaredType;

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


  public DefaultReferences(final Instances bootstrapInstances) {
    this(bootstrapInstances, null);
  }

  public DefaultReferences(final Instances bootstrapInstances,
                           final ClientProxier clientProxier) {
    super();
    this.ids = new IdentityHashMap<>();
    this.selector = new Selector(declaredType(Object.class), defaultQualifiers());    
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
    Selector s = new Selector(declaredType(Instances.class), defaultQualifiers());
    Bean<?> b = this.instances.beanSet().bean(s);
    if (b != null) {
      this.instances = Objects.requireNonNull(this.supplyReference(s, b.cast()), "this.supplyReference(" + s + ", " + b + ")");
    }

    s = new Selector(declaredType(ClientProxier.class), defaultQualifiers());
    b = this.instances.beanSet().bean(s);
    if (b != null) {
      this.clientProxier = Objects.requireNonNull(this.supplyReference(s, b.cast()), "this.supplyReference(" + s + ", " + b + ")");
    }
  }

  @Override // References<R>
  public final Iterator<R> iterator() {
    return new ReferenceIterator(this.instances.beanSet().beans().iterator());
  }

  @Override // References<R>
  @SuppressWarnings("unchecked")
  public final <R2 extends R> DefaultReferences<R2> withSelector(final Selector selector) {
    return (DefaultReferences<R2>)new DefaultReferences<>(this.ids, Objects.requireNonNull(selector, "selector"), this.instances, this.clientProxier);
  }

  @Override // References<R>
  public final void destroy(final R r) {
    if (r != null) {
      synchronized (this.ids) {
        this.remove(this.ids.remove(r));
      }
    }
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

  private final void remove(final Id id) {
    if (id != null) {
      this.instances.remove(id);
    }
  }
  
  @Override // References<R>
  public final <R> R supplyReference(final Selector selector) {
    return this.supplyReference(selector, null);
  }
  
  @Override // References
  public final <R> R supplyReference(final Selector selector, Bean<R> bean) {
    if (bean == null) {
      final Bean<?> b = this.instances.beanSet().bean(selector);
      if (b == null) {
        throw new UnsatisfiedResolutionException(selector);
      }
      bean = b.cast();
    }
    // Note that if the incoming Bean was NOT null, then it is possible that the incoming Selector does not select it. I
    // think this is OK because at this point the selector is informational only, and identifies the reference request,
    // really.
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


    private final Iterator<? extends Bean<?>> bi;

    private final AtomicReference<R> last;


    /*
     * Constructors.
     */


    private ReferenceIterator(final Iterator<? extends Bean<?>> bi) {
      super();
      this.bi = Objects.requireNonNull(bi, "bi");
      this.last = new AtomicReference<>();
    }


    /*
     * Instance methods.
     */


    @Override // Iterator<R>
    public final boolean hasNext() {
      return this.bi.hasNext();
    }

    @Override // Iterator<R>
    public final R next() {
      final Bean<R> bean = this.bi.next().cast();
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
