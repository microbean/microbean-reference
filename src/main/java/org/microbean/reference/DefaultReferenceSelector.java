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

import java.util.Objects;

import java.util.function.Supplier;

import org.microbean.bean.Assignability;
import org.microbean.bean.Bean;
import org.microbean.bean.BeanSet;
import org.microbean.bean.Creation;
import org.microbean.bean.Id;
import org.microbean.bean.BeanSelector;
import org.microbean.bean.References;
import org.microbean.bean.ReferenceSelector;
import org.microbean.bean.UnsatisfiedResolutionException;

import org.microbean.lang.TypeAndElementSource;

import static org.microbean.bean.Qualifiers.defaultQualifiers;

public class DefaultReferenceSelector implements ReferenceSelector {

  private final TypeAndElementSource tes;
  
  private final Assignability assignability;

  // Hosts (hopefully non-existent) dependent objects of "real" Instances and "real" ClientProxier. Closed in the
  // close() method.
  private final Creation<?> rootCreation;

  // A Supplier of Creations that backs the #creation() method.
  private final Supplier<? extends Creation<?>> creationSupplier;

  // Treat as effectively final.
  private Instances instances;

  // Treat as effectively final.
  private ClientProxier clientProxier;

  public DefaultReferenceSelector(final TypeAndElementSource tes,
                                  final Assignability assignability,
                                  final Instances instances,
                                  final ClientProxier clientProxier) {
    super();
    this.tes = Objects.requireNonNull(tes, "tes");
    this.assignability = assignability == null ? new Assignability(tes) : assignability;
    this.instances = Objects.requireNonNull(instances, "instances");
    this.clientProxier = clientProxier == null ? BootstrapClientProxier.INSTANCE : clientProxier;

    final BeanSelector creationSelector =
      new BeanSelector(this.assignability,
                   this.tes.declaredType(null,
                                         this.tes.typeElement(Creation.class),
                                         this.tes.wildcardType(null, null)), // or Object.class?
                   defaultQualifiers());
    // Note that BootstrapCreation does not implement AutoCloseableRegistry.
    this.rootCreation = this.reference(creationSelector, BootstrapCreation.INSTANCE.cast());

    BeanSelector s = new BeanSelector(Instances.class);
    Bean<?> b = this.beanSet().bean(s);
    if (b != null) {
      this.instances =
        Objects.requireNonNull(this.reference(s, b.cast(), this.rootCreation.cast()),
                               "this.reference(" + s + ", " + b + ", " + this.rootCreation + ")");
    }

    s = new BeanSelector(ClientProxier.class);
    b = this.beanSet().bean(s);
    if (b != null) {
      this.clientProxier =
        Objects.requireNonNull(this.reference(s, b.cast(), this.rootCreation.cast()),
                               "this.reference(" + s + ", " + b + ", " + this.rootCreation + ")");
    }

    final Bean<Creation<?>> creationBean = this.beanSet().bean(creationSelector).cast();
    this.creationSupplier = () -> this.reference(creationSelector, creationBean, this.rootCreation.cast());
  }

  @Override // ReferenceSelector
  public final BeanSet beanSet() {
    return this.instances.beanSet();
  }

  @Override // ReferenceSelector
  public final void close() {
    this.rootCreation.close();
  }

  @Override // ReferenceSelector
  public final <I> Creation<I> creation() {
    return this.creationSupplier.get().cast();
  }

  @Override // ReferenceSelector
  public final <R> R reference(final BeanSelector selector,
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
      this.clientProxier.needsClientProxy(selector, bean.id(), c, this) ?
      this.clientProxier.clientProxy(selector, bean, c, this, this.instances) :
      this.instances.instance(selector, bean, c, this);
  }


  /*
   * Inner and nested classes.
   */

  
  private static final class BootstrapClientProxier implements ClientProxier {

    private static final BootstrapClientProxier INSTANCE = new BootstrapClientProxier();

    private BootstrapClientProxier() {
      super();
    }

    @Override // ClientProxier
    public final boolean needsClientProxy(final BeanSelector s,
                                          final Id id,
                                          final Creation<?> c,
                                          final ReferenceSelector r) {
      return false;
    }

    @Override // ClientProxier
    public final <R> R clientProxy(final BeanSelector s,
                                   final Bean<R> b,
                                   final Creation<R> c,
                                   final ReferenceSelector r,
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