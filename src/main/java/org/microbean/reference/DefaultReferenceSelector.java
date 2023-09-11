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

import java.util.Collection;
import java.util.Objects;

import java.util.function.Supplier;

import org.microbean.bean.Assignability;
import org.microbean.bean.Bean;
import org.microbean.bean.BeanSelectionCriteria;
import org.microbean.bean.BeanSet;
import org.microbean.bean.Creation;
import org.microbean.bean.CreationSupplier;
import org.microbean.bean.Id;
import org.microbean.bean.ReferenceSelector;
import org.microbean.bean.References;
import org.microbean.bean.UnsatisfiedResolutionException;

import org.microbean.lang.TypeAndElementSource;

import static org.microbean.bean.Qualifiers.defaultQualifiers;

import static org.microbean.lang.Lang.typeAndElementSource;

public class DefaultReferenceSelector implements ReferenceSelector, InstanceRemover {

  private final TypeAndElementSource tes;

  private final Assignability assignability;

  // Hosts (hopefully non-existent) dependent objects of "real" Instances and "real" ClientProxier. Closed in the
  // close() method.
  private final Creation<?> rootCreation;

  // A Supplier of Creations that backs the #creation() method.
  private final CreationSupplier creationSupplier;

  // Treat as effectively final.
  private InstanceManager instanceManager;

  // Treat as effectively final.
  private ClientProxier clientProxier;

  public DefaultReferenceSelector(final Collection<? extends Bean<?>> beans) {
    this(typeAndElementSource(), beans);
  }

  public DefaultReferenceSelector(final TypeAndElementSource tes,
                                  final Collection<? extends Bean<?>> beans) {
    this(tes, new Assignability(tes), beans);
  }

  public DefaultReferenceSelector(final TypeAndElementSource tes,
                                  final Assignability assignability,
                                  final Collection<? extends Bean<?>> beans) {
    this(tes, assignability, new DefaultInstanceManager(tes, assignability, beans), BootstrapClientProxier.INSTANCE);
  }

  public DefaultReferenceSelector(final TypeAndElementSource tes,
                                  final Assignability assignability,
                                  final InstanceManager instanceManager,
                                  final ClientProxier clientProxier) {
    super();
    this.tes = Objects.requireNonNull(tes, "tes");
    this.assignability = assignability == null ? new Assignability(tes) : assignability;
    this.instanceManager = Objects.requireNonNull(instanceManager, "instanceManager");
    this.clientProxier = clientProxier == null ? BootstrapClientProxier.INSTANCE : clientProxier;

    final BeanSelectionCriteria creationSelector =
      new BeanSelectionCriteria(this.assignability,
                   this.tes.declaredType(null,
                                         this.tes.typeElement(Creation.class),
                                         this.tes.wildcardType(null, null)), // or Object.class?
                   defaultQualifiers());
    // Note that BootstrapCreation does not implement AutoCloseableRegistry.
    this.rootCreation = this.reference(creationSelector, BootstrapCreation.INSTANCE.cast());

    BeanSelectionCriteria s = new BeanSelectionCriteria(InstanceManager.class);
    Bean<?> b = this.beanSet().bean(s);
    if (b != null) {
      this.instanceManager =
        Objects.requireNonNull(this.reference(s, b.cast(), this.rootCreation.cast()),
                               "this.reference(" + s + ", " + b + ", " + this.rootCreation + ")");
    }

    s = new BeanSelectionCriteria(ClientProxier.class);
    b = this.beanSet().bean(s);
    if (b != null) {
      this.clientProxier =
        Objects.requireNonNull(this.reference(s, b.cast(), this.rootCreation.cast()),
                               "this.reference(" + s + ", " + b + ", " + this.rootCreation + ")");
    }

    final Bean<Creation<?>> creationBean = this.beanSet().bean(creationSelector).cast();
    this.creationSupplier = new CreationSupplier() {
        @Override
        public final <I> Creation<I> creation() {
          return DefaultReferenceSelector.this.reference(creationSelector, creationBean.cast(), rootCreation.cast());
        }
      };
  }

  // No bootstrapping, no validation
  public DefaultReferenceSelector(final TypeAndElementSource tes,
                                  final Assignability assignability,
                                  final InstanceManager instanceManager,
                                  final ClientProxier clientProxier,
                                  final CreationSupplier creationSupplier) {
    this.tes = Objects.requireNonNull(tes, "tes");
    this.assignability = assignability == null ? new Assignability(tes) : assignability;
    this.instanceManager = Objects.requireNonNull(instanceManager, "instanceManager");
    this.clientProxier = clientProxier == null ? BootstrapClientProxier.INSTANCE : clientProxier;
    this.creationSupplier = Objects.requireNonNull(creationSupplier, "creationSupplier");
    this.rootCreation = BootstrapCreation.INSTANCE;
  }

  @Override // ReferenceSelector
  public final BeanSet beanSet() {
    return this.instanceManager.beanSet();
  }

  @Override // ReferenceSelector
  public final void close() {
    this.rootCreation.close();
  }

  @Override // ReferenceSelector
  public final <I> Creation<I> creation() {
    return this.creationSupplier.creation();
  }

  @Override // ReferenceSelector
  public final <R> R reference(final BeanSelectionCriteria beanSelectionCriteria,
                               Bean<R> bean,
                               Creation<R> c) {
    if (bean == null) {
      final Bean<?> b = this.beanSet().bean(beanSelectionCriteria);
      if (b == null) {
        throw new UnsatisfiedResolutionException(beanSelectionCriteria);
      }
      bean = b.cast();
    } else if (!beanSelectionCriteria.selects(bean)) {
      throw new IllegalArgumentException("bean: " + bean);
    }
    if (c != null) {
      // Critical. Clones c and registers the clone for subsequent closing with its ancestor. See
      // org.microbean.bean.DefaultCreation for example, and DefaultAutoCloseableRegistry.
      c = c.clone();
    }
    return
      this.clientProxier.needsClientProxy(beanSelectionCriteria, bean.id(), c, this) ?
      this.clientProxier.clientProxy(beanSelectionCriteria, bean, c, this, this.instanceManager) :
      this.instanceManager.instance(beanSelectionCriteria, bean, c, this);
  }

  @Override // InstanceRemover
  public final boolean remove(final Id id) {
    return this.instanceManager.remove(id);
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
    public final boolean needsClientProxy(final BeanSelectionCriteria s,
                                          final Id id,
                                          final Creation<?> c,
                                          final ReferenceSelector r) {
      return false;
    }

    @Override // ClientProxier
    public final <R> R clientProxy(final BeanSelectionCriteria s,
                                   final Bean<R> b,
                                   final Creation<R> c,
                                   final ReferenceSelector r,
                                   final InstanceManager instanceManager) {
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
