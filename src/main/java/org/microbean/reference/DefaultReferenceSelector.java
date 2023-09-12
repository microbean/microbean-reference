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
    this(tes, assignability, new DefaultInstanceManager(tes, assignability, beans));
  }

  public DefaultReferenceSelector(final TypeAndElementSource tes,
                                  final Assignability assignability,
                                  final InstanceManager instanceManager) {
    this(tes, assignability, instanceManager, BootstrapClientProxier.INSTANCE);
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

    // Here are the criteria for selecting a Bean that can make Creation<?> instances.
    final BeanSelectionCriteria creationBeanSelectionCriteria =
      new BeanSelectionCriteria(this.tes,
                                this.assignability,
                                this.tes.declaredType(null,
                                                      this.tes.typeElement(Creation.class),
                                                      this.tes.wildcardType(null, null)), // or Object.class?
                                defaultQualifiers(),
                                true);

    // Here is the Bean so selected.
    final Bean<Creation<?>> creationBean = this.beanSet().bean(creationBeanSelectionCriteria).cast();

    // Bootstrapping is tricky. "new BootstrapCreation<>()" below does not accept a BeanSelectionCriteria since the
    // Creation going "into" a reference selection will be cloned internally by the ReferenceSelector (this class) with
    // the BeanSelectionCriteria representing the current request. The Creation going "into" the reference selection in
    // this case represents no reference selection at all (it is the first possible reference selection, so there is
    // none prior to it).
    //
    // This is the most primordial Creation in the entire system. All other Creations notionally descend from
    // it. However, note that it does not implement AutoCloseableRegistry, so its descendants are not registered as
    // AutoCloseables with it.  It is like a "dummy root" of an in-actuality multi-rooted tree.
    final BootstrapCreation<?> bootstrapCreation = new BootstrapCreation<>();

    // The rootCreation field is the first "root" of the multi-rooted Creation<?> tree. In 99.99999% of all cases, it
    // will end up hosting nothing and effectively doing nothing.  It exists to host any (again, unlikely) dependent
    // objects sourced while looking up the "real" InstanceManager and the "real" ClientProxier. It is closed/released
    // only when this DefaultReferenceSelector is closed (see #close()).  Its notional parent is bootstrapCreation.
    this.rootCreation =
      Objects.requireNonNull(this.reference(creationBeanSelectionCriteria, creationBean.cast(), bootstrapCreation.cast()),
                             "this.reference(" + creationBeanSelectionCriteria + ", " + creationBean + ", " + bootstrapCreation + ")");
    assert this.rootCreation != bootstrapCreation;

    // We have found a reference that corresponds to a "real" Creation<?>-producing Bean. Make it so that our creation()
    // method will return appropriate references now that we've bootstrapped this foundational part.
    this.creationSupplier = new CreationSupplier() {
        @Override
        public final <I> Creation<I> creation() {
          return
            DefaultReferenceSelector.this.reference(creationBeanSelectionCriteria,
                                                    creationBean.cast(),
                                                    DefaultReferenceSelector.this.rootCreation.cast());
        }
      };

    // Find the "real" InstanceManager.  Any dependent objects encountered along the way will be registered with the
    // rootCreation.
    BeanSelectionCriteria bsc = new BeanSelectionCriteria(this.tes, this.assignability, this.tes.declaredType(InstanceManager.class), defaultQualifiers(), true);
    Bean<?> b = this.beanSet().bean(bsc);
    if (b != null) {
      this.instanceManager =
        Objects.requireNonNull(this.reference(bsc, b.cast(), this.rootCreation.cast()),
                               "this.reference(" + bsc + ", " + b + ", " + this.rootCreation + ")");
    }

    // Find the "real" ClientProxier.  Any dependent objects encountered along the way will be registered with the
    // rootCreation.
    bsc = new BeanSelectionCriteria(this.tes, this.assignability, this.tes.declaredType(ClientProxier.class), defaultQualifiers(), true);
    b = this.beanSet().bean(bsc);
    if (b != null) {
      this.clientProxier =
        Objects.requireNonNull(this.reference(bsc, b.cast(), this.rootCreation.cast()),
                               "this.reference(" + bsc + ", " + b + ", " + this.rootCreation + ")");
    }

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
    // really a dummy assignment; since no bootstrapping is happening in this constructor this will never be used
    this.rootCreation = new BootstrapCreation<>();
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
      c = c.clone(beanSelectionCriteria);
    }
    if (this.clientProxier.needsClientProxy(beanSelectionCriteria, bean.id(), c, this)) {
      return this.clientProxier.clientProxy(beanSelectionCriteria, bean, c, this, this.instanceManager);
    }
    return this.instanceManager.instance(beanSelectionCriteria, bean, c, this);
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

    private final BeanSelectionCriteria beanSelectionCriteria;

    private BootstrapCreation() {
      this(null);
    }

    private BootstrapCreation(final BeanSelectionCriteria beanSelectionCriteria) {
      super();
      this.beanSelectionCriteria = beanSelectionCriteria;
    }

    @Override
    public final BeanSelectionCriteria beanSelectionCriteria() {
      return this.beanSelectionCriteria;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final Creation<I> clone() {
      return this.clone(this.beanSelectionCriteria());
    }

    @Override
    public final Creation<I> clone(final BeanSelectionCriteria beanSelectionCriteria) {
      return new BootstrapCreation<>(beanSelectionCriteria);
    }

    // MUST be idempotent
    // During creation (as opposed to destruction) this method should throw an IllegalStateException.
    @Override // AutoCloseable
    public final void close() {

    }

  }

}
