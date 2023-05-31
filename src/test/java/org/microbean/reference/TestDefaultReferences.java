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
import java.util.Set;

import javax.lang.model.type.TypeMirror;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.microbean.bean.Alternate;
import org.microbean.bean.Bean;
import org.microbean.bean.BeanSet;
import org.microbean.bean.Creation;
import org.microbean.bean.DefaultBeanSet;
import org.microbean.bean.DefaultCreation;
import org.microbean.bean.DefaultDestruction;
import org.microbean.bean.Destruction;
import org.microbean.bean.Factory;
import org.microbean.bean.Id;
import org.microbean.bean.Instances;
import org.microbean.bean.Qualifiers;
import org.microbean.bean.ReferenceTypeList;
import org.microbean.bean.References;
import org.microbean.bean.Selector;

import org.microbean.instance.DefaultInstances;

import org.microbean.scopelet.NoneScopelet;
import org.microbean.scopelet.Scopelet;
import org.microbean.scopelet.SingletonScopelet;

import static java.util.stream.StreamSupport.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.microbean.bean.Qualifiers.anyQualifiers;
import static org.microbean.bean.Qualifiers.anyAndDefaultQualifiers;
import static org.microbean.bean.Qualifiers.defaultQualifiers;

import static org.microbean.lang.Lang.declaredType;
import static org.microbean.lang.Lang.typeElement;
import static org.microbean.lang.Lang.wildcardType;

import static org.microbean.scope.Scope.NONE_ID;
import static org.microbean.scope.Scope.SINGLETON_ID;

final class TestDefaultReferences {

  private static Bean<Simple> simpleBean;

  private static Bean<Host> hostBean;

  private static Bean<Parasite> parasiteBean;

  private TestDefaultReferences() {
    super();
  }

  @BeforeAll
  static final void staticSetup() {
    simpleBean =
      new Bean<>(new Id(declaredType(Simple.class), anyAndDefaultQualifiers(), NONE_ID),
                 c -> new Simple());
    hostBean =
      new Bean<>(new Id(declaredType(Host.class), anyAndDefaultQualifiers(), NONE_ID),
                 c -> {
                   assert c.references() instanceof DefaultReferences : c.references();
                   assert c instanceof DefaultCreation;
                   assert c.destruction() instanceof DefaultDestruction;
                   return new Host(c.references().supplyReference(new Selector(Parasite.class)));
      });
    parasiteBean =
      new Bean<>(new Id(declaredType(Parasite.class), anyAndDefaultQualifiers(), NONE_ID),
                 c -> new Parasite());
  }

  @BeforeEach
  final void setup() {

  }

  // A ridiculous test showing that even with no user-supplied beans there are still beans representing the system
  // constructs, including the BeanSet itself.
  @Test
  final void testWithNoBeans() {
    final References<Object> references = new Container(Set.of());
    List<?> all = stream(references.spliterator(), false /* not parallel */).toList();
    // The bootstrapping materials deliberately do not have Object.class among their bean types, so they won't show up:
    assertEquals(0, all.size());
    // However, note you can of course get them deliberately:
    assertNotNull(references.supplyReference(new Selector(Alternate.Resolver.class)));
    assertNotNull(references.supplyReference(new Selector(BeanSet.class)));
    assertNotNull(references.supplyReference(new Selector(Creation.Factory.class)));
    assertNotNull(references.supplyReference(new Selector(Instances.class)));
    assertNotNull(references.supplyReference(new Selector(NoneScopelet.class, anyQualifiers())));
    assertNotNull(references.supplyReference(new Selector(References.class)));
    assertNotNull(references.supplyReference(new Selector(SingletonScopelet.class, anyQualifiers())));
  }

  @Test
  final void testDestroySingletonScope() {
    final References<Object> references = new Container(Set.of());
    final SingletonScopelet ss = references.supplyReference(new Selector(SingletonScopelet.class, anyQualifiers()));
    assertNotNull(ss);
    assertFalse(references.destroy(ss));
  }

  @Test
  final void testDestroyNull() {
    final References<Object> references = new Container(Set.of(simpleBean));
    references.destroy(null);
  }

  @Test
  final void test() {
    final References<Object> references = new Container(Set.of(simpleBean));
    final Selector s = new Selector(Simple.class);
    final Simple simple = references.supplyReference(s);
    assertNotNull(simple);
    // Doesn't do anything, on purpose, because simple wasn't handed out via iterator.next().
    references.destroy(simple);
  }

  @Test
  final void testHost() {
    final References<Object> references = new Container(Set.of(hostBean, parasiteBean));
    final Host host = references.supplyReference(new Selector(Host.class));
    assertNotNull(host);
    assertNotNull(host.parasite());
  }

  private static final class Simple {

    private Simple() {
      super();
    }

  }

  private static final class Host {

    private final Parasite parasite;

    private Host(final Parasite parasite) {
      super();
      this.parasite = Objects.requireNonNull(parasite, "parasite");
    }

    private final Parasite parasite() {
      return this.parasite;
    }

  }

  private static final class Parasite {

    private Parasite() {
      super();
    }

  }

  private static final class Container implements References<Object> {

    private final References<Object> references;

    private Container(final Collection<? extends Bean<?>> beans) {
      super();
      final Collection<Bean<?>> newBeans = new ArrayList<>(beans.size() + 1);
      newBeans.addAll(beans);
      // Holds zero or one References<Object> instances. Exists to handle forward reference.
      @SuppressWarnings({"unchecked", "rawtypes"})
      final References<Object>[] ref = new References[1];
      final TypeMirror objectType = declaredType(Object.class);
      final TypeMirror referencesType = declaredType(null, typeElement(References.class), objectType);
      newBeans.add(new Bean<>(new Id(List.of(referencesType),
                                     anyAndDefaultQualifiers(),
                                     SINGLETON_ID),
                              new Factory<References<Object>>() {
          @Override // Factory<References<Object>>
          public final References<Object> singleton() {
            return ref[0]; // can be null when DefaultInstances/ReferencesSwitcher encounters it
          }
          @Override // Factory<References<Object>>
          public final References<Object> produce(final Creation<References<Object>> c) {
            assert ref[0] != null;
            return ref[0];
          }
        }));
      ref[0] = new DefaultReferences<Object>(new Selector(objectType), new DefaultInstances(newBeans));
      ref[0] = ref[0].supplyReference(new Selector(referencesType));
      this.references = ref[0];
    }

    @Override // References<Object>
    public final BeanSet beanSet() {
      return this.references.beanSet();
    }

    @Override // References<R>
    public final <R> R supplyReference(final Selector selector) {
      return this.references.supplyReference(selector);
    }

    @Override // References
    public final <R> R supplyReference(final Selector selector, Bean<R> bean) {
      return this.references.supplyReference(selector, bean);
    }

    @Override
    public final void close() {
      this.references.close();
    }

    @Override
    public final boolean destroy(final Object r) {
      return this.references.destroy(r);
    }

    @Override
    public final Iterator<Object> iterator() {
      return this.references.iterator();
    }

    @Override
    public final <R2> References<R2> withSelector(final Selector selector) {
      return this.references.withSelector(selector);
    }

  }

}
