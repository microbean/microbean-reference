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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import java.util.function.Function;

import javax.lang.model.element.TypeElement;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

import org.microbean.bean.AmbiguousResolutionException;
import org.microbean.bean.Assignability;
import org.microbean.bean.AutoCloseableRegistry;
import org.microbean.bean.Bean;
import org.microbean.bean.BeanSet;
import org.microbean.bean.Creation;
import org.microbean.bean.DefaultAutoCloseableRegistry;
import org.microbean.bean.DefaultBeanSet;
import org.microbean.bean.DefaultCreation;
import org.microbean.bean.Factory;
import org.microbean.bean.Id;
import org.microbean.bean.ReferenceSelector;
import org.microbean.bean.BeanSelectionCriteria;
import org.microbean.bean.Singleton;
import org.microbean.bean.UnsatisfiedResolutionException;

import org.microbean.lang.TypeAndElementSource;

import org.microbean.scopelet.NoneScopelet;
import org.microbean.scopelet.SingletonScopelet;
import org.microbean.scopelet.Scopelet;
import org.microbean.scopelet.TooManyActiveScopeletsException;

import static org.microbean.bean.Creation.cast;
import static org.microbean.bean.Qualifiers.anyQualifier;
import static org.microbean.bean.Qualifiers.anyAndDefaultQualifiers;
import static org.microbean.bean.Qualifiers.defaultQualifiers;

import static org.microbean.lang.Lang.typeAndElementSource;
import static org.microbean.scope.Scope.NONE_ID;
import static org.microbean.scope.Scope.SINGLETON_ID;

// Public mostly only for testing scenarios.
public final class DefaultInstanceManager implements InstanceManager {


  /*
   * Instance fields.
   */


  private final Assignability assignability;

  private final TypeMirror scopeletType;

  private final BeanSelectionCriteria anyScopeletSelector;

  private final BeanSet beanSet;


  /*
   * Constructors.
   */


  public DefaultInstanceManager(final Assignability assignability,
                                final Collection<? extends Bean<?>> beans) {
    super();
    this.assignability = assignability == null ? new Assignability() : assignability;
    final TypeAndElementSource tes = this.assignability.typeAndElementSource();
    this.scopeletType = tes.declaredType(null, tes.typeElement(Scopelet.class), tes.wildcardType(null, null));
    this.anyScopeletSelector = new BeanSelectionCriteria(this.assignability, this.scopeletType, List.of(anyQualifier()), true);
    final Collection<Bean<?>> newBeans = new ArrayList<>(beans.size() + 5);
    newBeans.addAll(beans);
    newBeans.add(new SingletonScopelet().bean());
    newBeans.add(new NoneScopelet().bean());
    newBeans.add(new Bean<>(new Id(List.of(tes.declaredType(DefaultAutoCloseableRegistry.class),
                                           tes.declaredType(AutoCloseableRegistry.class)),
                                   anyAndDefaultQualifiers(),
                                   NONE_ID),
                            (c, r) -> new DefaultAutoCloseableRegistry()));
    TypeElement e = tes.typeElement(DefaultCreation.class);
    TypeVariable tv = (TypeVariable)e.getTypeParameters().get(0).asType();
    final TypeMirror t0 = tes.declaredType(null, e, tv);
    final TypeMirror t1 = tes.declaredType(null, e);
    e = tes.typeElement(Creation.class);
    tv = (TypeVariable)e.getTypeParameters().get(0).asType();
    final TypeMirror t2 = tes.declaredType(null, e, tv);
    final TypeMirror t3 = tes.declaredType(null, e);
    final BeanSelectionCriteria bsc =
      new BeanSelectionCriteria(this.assignability, tes.declaredType(AutoCloseableRegistry.class), defaultQualifiers(), true);
    newBeans.add(new Bean<>(new Id(List.of(t0, t1, t2, t3),
                                   anyAndDefaultQualifiers(),
                                   NONE_ID),
                            (c, r) -> new DefaultCreation<>(r.reference(bsc, cast(c)))));
    newBeans.add(new Bean<>(new Id(List.of(tes.declaredType(DefaultInstanceManager.class)),
                                   anyAndDefaultQualifiers(),
                                   SINGLETON_ID),
                            new Singleton<>(this)));
    this.beanSet = new DefaultBeanSet(assignability, newBeans);
  }


  /*
   * Instance methods.
   */


  @Override // InstanceManager
  public final BeanSet beanSet() {
    return this.beanSet;
  }

  @Override // InstanceManager;
  @SuppressWarnings("unchecked")
  public final <I> I instance(final BeanSelectionCriteria beanSelectionCriteria,
                              final Bean<I> bean, // nullable
                              final Creation<I> creation, // nullable
                              final ReferenceSelector referenceSelector) { // nullable
    final Bean<I> b;
    if (bean == null) {
      b = (Bean<I>)this.beanSet.bean(beanSelectionCriteria, DefaultInstanceManager::handleInactiveScopelets);
      if (b == null) {
        throw new UnsatisfiedResolutionException(beanSelectionCriteria);
      }
    } else if (!beanSelectionCriteria.selects(bean)) {
      throw new IllegalArgumentException("!beanSelectionCriteria.selects(bean); beanSelectionCriteria: " +
                                         beanSelectionCriteria +
                                         "; bean: "
                                         + bean);
    } else {
      b = bean;
    }
    return
      this.instance(b,
                    s -> s.instance(b.id(), b.factory(), creation, referenceSelector),
                    creation,
                    referenceSelector);
  }

  public final boolean remove(final Id id) {
    return
      id != null &&
      this.<Scopelet<?>>instance(new BeanSelectionCriteria(this.assignability, scopeletType, List.of(id.governingScopeId()), true),
                                 null, // Factory
                                 null, // Creation
                                 null) // ReferenceSelector
        .remove(id);
  }

  /*
   * Private methods.
   */

  private final <I> I instance(final Bean<I> bean,
                               final Function<? super Scopelet<?>, ? extends I> f,
                               final Creation<I> creation,
                               final ReferenceSelector referenceSelector) {
    final Factory<I> factory = bean.factory();
    final I singleton = factory.singleton();
    if (singleton == null) {
      final BeanSelectionCriteria scopeletBeanSelectionCriteria =
        new BeanSelectionCriteria(this.assignability, scopeletType, List.of(bean.id().governingScopeId()), true);
      if (bean.equals(this.beanSet.bean(scopeletBeanSelectionCriteria, DefaultInstanceManager::handleInactiveScopelets))) {
        return factory.create(creation, referenceSelector);
      }
      return f.apply((Scopelet<?>)this.instance(scopeletBeanSelectionCriteria, null, creation, referenceSelector));
    }
    return singleton;
  }


  /*
   * Static methods.
   */


  // Invoked by method reference only as part of BeanSet#bean(BeanSelectionCriteria, BiFunction).
  private static final Bean<?> handleInactiveScopelets(final BeanSelectionCriteria beanSelectionCriteria,
                                                       final Collection<? extends Bean<?>> beans) {
    if (beans.size() < 2) { // 2 because we're disambiguating
      throw new IllegalArgumentException("beans: " + beans);
    }
    Bean<?> b2 = null;
    Scopelet<?> s2 = null;
    final Iterator<? extends Bean<?>> i = beans.iterator(); // we use Iterator for good reasons
    while (i.hasNext()) {
      final Bean<?> b1 = i.next();
      if (b1.factory() instanceof Scopelet<?> s1) {
        if (s2 == null) {
          assert b2 == null;
          if (i.hasNext()) {
            b2 = i.next();
            if (b2.factory() instanceof Scopelet<?> s) {
              s2 = s;
            } else {
              s2 = null;
              b2 = null;
              break;
            }
          } else {
            s2 = s1;
            b2 = b1;
            break;
          }
        }
        assert b2 != null;
        if (s2.scopeId().equals(s1.scopeId())) {
          if (s2.active()) {
            if (s1.active()) {
              throw new TooManyActiveScopeletsException("scopelet1: " + s1 + "; scopelet2: " + s2);
            } else {
              // drop s1; keep s2
            }
          } else if (s1.active()) {
            // drop s2; keep s1
            s2 = s1;
            b2 = b1;
          } else {
            // both are inactive; drop 'em both and keep going
            s2 = null;
            b2 = null;
          }
        } else {
          s2 = null;
          b2 = null;
          break;
        }
      } else {
        s2 = null;
        b2 = null;
        break;
      }
    }
    if (s2 == null) {
      throw new AmbiguousResolutionException(beanSelectionCriteria,
                                             beans,
                                             "TODO: this message needs to be better; can't resolve these alternates: " + beans);
    }
    assert b2 != null;
    return b2;
  }

}
