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

import org.microbean.bean.Assignability;
import org.microbean.bean.Bean;
import org.microbean.bean.BeanSet;
import org.microbean.bean.Creation;
import org.microbean.bean.Id;
import org.microbean.bean.Selector;
import org.microbean.bean.References;
import org.microbean.bean.UnsatisfiedResolutionException;

import org.microbean.lang.TypeAndElementSource;

import static org.microbean.bean.Qualifiers.defaultQualifiers;

public final class DefaultReferences<R> implements References<R> {

  private final Assignability assignability;

  private final TypeAndElementSource tes;

  // Hosts (hopefully non-existent) dependent objects of "real" Instances and "real" ClientProxier. Closed in the
  // close() method.
  private final Creation<?> rootCreation;

  // For the iterator() method, chooses what will be selected and iterated over.
  private final Selector selector;

  // @GuardedBy("itself")
  private final IdentityHashMap<R, Id> ids;

  private Instances instances;

  private ClientProxier clientProxier;

  public DefaultReferences(final Assignability assignability,
                           final TypeAndElementSource tes,
                           final Selector selector,
                           final Instances bootstrapInstances,
                           final ClientProxier clientProxier) {
    super();
    this.tes = Objects.requireNonNull(tes, "tes");
    this.assignability = assignability == null ? new Assignability(tes) : assignability;
    this.selector = Objects.requireNonNull(selector, "selector");
    this.ids = new IdentityHashMap<>();

    this.instances = Objects.requireNonNull(bootstrapInstances, "bootstrapInstances");
    this.clientProxier = clientProxier == null ? BootstrapClientProxier.INSTANCE : clientProxier;
    this.rootCreation = this.creation();
    
    Selector s = new Selector(Instances.class);
    Bean<?> b = this.beanSet().bean(s);
    if (b != null) {
      this.instances =
        Objects.requireNonNull(this.reference(s, b.cast(), this.rootCreation.cast()),
                               "this.reference(" + s + ", " + b + ", " + this.rootCreation + ")");
    }

    s = new Selector(ClientProxier.class);
    b = this.beanSet().bean(s);
    if (b != null) {
      this.clientProxier =
        Objects.requireNonNull(this.reference(s, b.cast(), this.rootCreation.cast()),
                               "this.reference(" + s + ", " + b + ", " + this.rootCreation + ")");
    }
  }

  @Override
  public final BeanSet beanSet() {
    return this.instances.beanSet();
  }

  // Destroys r if and only if it is (a) dependent and (b) supplied by get()
  @Override
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
    this.rootCreation.close();
  }

  @Override
  public final Iterator<R> iterator() {
    return new ReferenceIterator(this.creation(), this.beanSet().beans(this.selector).iterator());
  }

  @Override
  public final <I> Creation<I> creation() {
    return
      this.reference(new Selector(this.assignability,
                                  this.tes.declaredType(null,
                                                        this.tes.typeElement(Creation.class),
                                                        this.tes.wildcardType(null, null)), // or Object.class?
                                  defaultQualifiers()),
                     BootstrapCreation.INSTANCE.cast()); // Note that BootstrapCreation does not implement AutoCloseableRegistry.
  }

  @Override // References
  public final <R> R reference(final Selector selector,
                               Bean<R> bean,
                               Creation<R> c) {
    if (bean == null) {
      final Bean<?> b = this.beanSet().bean(selector);
      if (b == null) {
        throw new UnsatisfiedResolutionException(selector);
      }
      bean = b.cast();
    } else if (!selector.selects(bean)) {
      throw new IllegalArgumentException("bean: " + bean);
    }
    if (c != null) {
      // Critical. Clones c and registers the clone for subsequent closing with its ancestor. See
      // org.microbean.bean.DefaultCreation for example, and DefaultAutoCloseableRegistry.
      c = c.clone();
    }
    return
      this.clientProxier.needsClientProxy(selector, bean.id(), c, this, this.instances) ?
      this.clientProxier.clientProxy(selector, bean, c, this, this.instances) :
      this.instances.instance(selector, bean, c, this);
  }

  private final boolean remove(final Id id) {
    return id != null && this.instances.remove(id);
  }


  /*
   * Inner and nested classes.
   */


  private final class ReferenceIterator implements Iterator<R> {


    /*
     * Instance fields.
     */


    private final Creation<R> creation;

    private final Iterator<? extends Bean<?>> beanIterator;

    private final AtomicReference<R> last;


    /*
     * Constructors.
     */


    private ReferenceIterator(final Creation<R> creation, final Iterator<? extends Bean<?>> beanIterator) {
      super();
      this.creation = Objects.requireNonNull(creation, "creation");
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
      final R r = DefaultReferences.this.reference(DefaultReferences.this.selector, bean, this.creation);
      if (r != null) {
        synchronized (DefaultReferences.this.ids) {
          DefaultReferences.this.ids.putIfAbsent(r, bean.id());
        }
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

  private static final class BootstrapClientProxier implements ClientProxier {

    private static final BootstrapClientProxier INSTANCE = new BootstrapClientProxier();

    private BootstrapClientProxier() {
      super();
    }

    @Override // ClientProxier
    public final boolean needsClientProxy(final Selector s,
                                          final Id id,
                                          final Creation<?> c,
                                          final References<?> r,
                                          final Instances instances) {
      return false;
    }

    @Override // ClientProxier
    public final <R> R clientProxy(final Selector s,
                                   final Bean<R> b,
                                   final Creation<R> c,
                                   final References<?> r,
                                   final Instances instances) {
      throw new DynamicClientProxiesNotSupportedException();
    }

  }

  // Note that this does not implement (or contain, or reference) AutoCloseableRegistry, so is suitable only for cases
  // where it is known that no dependent objects will be created. One such case (the only?) is when a Creation itself is
  // being retrieved.
  private static final class BootstrapCreation<I> implements Creation<I> {

    private static final BootstrapCreation<?> INSTANCE = new BootstrapCreation<>();

    private BootstrapCreation() {
      super();
    }

    @Override
    @SuppressWarnings("unchecked")
    public final Creation<I> clone() {
      try {
        return (Creation<I>)super.clone();
      } catch (final CloneNotSupportedException e) {
        throw new AssertionError(e.getMessage(), e);
      }
    }

    // MUST be idempotent
    // During creation (as opposed to destruction) this method should throw an IllegalStateException.
    @Override // AutoCloseable
    public final void close() {

    }

  }


}
