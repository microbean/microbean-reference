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
package org.microbean.instance;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import java.util.function.Function;

import javax.lang.model.type.TypeMirror;

import org.microbean.bean.AmbiguousResolutionException;
import org.microbean.bean.Bean;
import org.microbean.bean.BeanSet;
import org.microbean.bean.Creation;
import org.microbean.bean.Qualifiers;
import org.microbean.bean.Selector;
import org.microbean.bean.UnsatisfiedResolutionException;

import org.microbean.lang.JavaLanguageModel;

import org.microbean.scopelet.Scopelet;
import org.microbean.scopelet.TooManyActiveScopeletsException;

public final class Instances implements org.microbean.bean.Instances {

  private static final TypeMirror SCOPELET_TYPE;

  static {
    final JavaLanguageModel jlm = new JavaLanguageModel();
    SCOPELET_TYPE = jlm.types().getDeclaredType(null, jlm.typeElement(Scopelet.class).asType(), jlm.wildcardType());
  }

  private final BeanSet beanSet;

  private final Function<? super Selector<?>, ? extends Creation<?>> creationFunction;

  public Instances(final BeanSet beanSet,
                   final Function<? super Selector<?>, ? extends Creation<?>> creationFunction) {
    super();
    this.beanSet = Objects.requireNonNull(beanSet, "beanSet");
    this.creationFunction = Objects.requireNonNull(creationFunction, "creationFunction");
    // TODO: this is kind of a hack, but should initialize all scopelets, and in the proper order.
    final Selector<?> anyScopeletSelector = new Selector<>(SCOPELET_TYPE, List.of(Qualifiers.anyQualifier()), true);
    for (final Bean<?> scopeletBean : beanSet.beans(anyScopeletSelector)) {
      scopeletBean.factory().create(creationFunction.apply(anyScopeletSelector).cast());
    }
  }

  @Override // org.microbean.bean.Instances
  public final Creation<?> creation(final Selector<?> selector) {
    return this.creationFunction.apply(selector);
  }

  @Override // org.microbean.bean.Instances
  public final <I> I find(final Selector<?> selector) {
    final Bean<?> bean = this.beanSet.bean(Objects.requireNonNull(selector, "selector"), Instances::handleInactiveScopelets);
    if (bean == null) {
      throw new UnsatisfiedResolutionException(selector);
    }
    return this.find(bean.cast());
  }

  @Override // org.microbean.bean.Instances
  public final <I> I find(final Bean<I> bean) {
    return this.supply(Objects.requireNonNull(bean, "bean"), scopelet -> scopelet.get(bean.id()));
  }

  @Override // org.microbean.bean.Instances
  public final <I> I supply(final Selector<?> selector, final Bean<I> suppliedBean) {
    final Bean<I> bean;
    if (suppliedBean == null) {
      final Bean<?> b = this.beanSet.bean(Objects.requireNonNull(selector, "selector"), Instances::handleInactiveScopelets);
      if (b == null) {
        throw new UnsatisfiedResolutionException(selector);
      }
      bean = b.cast();
    } else {
      bean = suppliedBean;
    }
    // TODO: selector might be inappropriate for bean
    return this.supply(bean, scopelet -> scopelet.supply(bean.id(), bean.factory(), this.creation(selector).cast()));
  }

  private final <I> I supply(final Bean<I> bean, final Function<? super Scopelet<?>, ? extends I> f) {
    final I singleton = bean.factory().singleton();
    if (singleton == null) {
      final Selector<?> scopeletSelector = new Selector<>(SCOPELET_TYPE, List.of(bean.id().governingScopeId()), true);
      if (bean.equals(this.beanSet.bean(scopeletSelector))) {
        // The sought bean is a Bean<Scopelet> and is its own governing scope.  Stop recursion.
        return bean.factory().create(this.creation(scopeletSelector).cast()); // yes, create()
      } else {
        // Indirectly recursive; yes, supply() not get()
        return f.apply(this.supply(scopeletSelector, null)); // #supply(Selector, Bean)
      }
    } else {
      return singleton;
    }
  }


  /*
   * Static methods.
   */


  private static final Bean<?> handleInactiveScopelets(final Selector<?> selector, final Collection<? extends Bean<?>> beans) {
    if (beans == null || beans.size() < 2) {
      throw new IllegalArgumentException("beans: " + beans);
    }
    Bean<?> b2 = null;
    Scopelet<?> s2 = null;
    final Iterator<? extends Bean<?>> i = beans.iterator();
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
      throw new AmbiguousResolutionException(selector,
                                             beans,
                                             "TODO: this message needs to be better; can't resolve these alternates: " + beans);
    }
    assert b2 != null;
    return b2;
  }

}
