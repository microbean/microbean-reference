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

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.microbean.bean.Bean;
import org.microbean.bean.Id;
import org.microbean.bean.Qualifiers;
import org.microbean.bean.ReferenceTypeList;
import org.microbean.bean.Selector;

import org.microbean.instance.DefaultInstances;

import org.microbean.scopelet.Scopelet;
import org.microbean.scopelet.SingletonScopelet;

import static java.util.stream.StreamSupport.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
  
  private TestDefaultReferences() {
    super();
  }

  @BeforeAll
  static final void staticSetup() {
    simpleBean = new Bean<>(new Id(declaredType(Simple.class), anyAndDefaultQualifiers(), NONE_ID), c -> new Simple());
  }
  
  @BeforeEach
  final void setup() {

  }

  // A ridiculous test showing that even with no user-supplied beans there are still beans representing the system
  // constructs, including the BeanSet itself.
  @Test
  final void testWithNoBeans() {
    final DefaultReferences<Object> references = new DefaultReferences<>(new DefaultInstances(Set.of()));
    List<?> all = stream(references.spliterator(), false /* not parallel */).toList();
    assertEquals(5, all.size()); // 5 == NoneScopelet, SingletonScopelet, DefaultBeanSet, DefaultBeanSet$StockResolver, DefaultInstances
    SingletonScopelet ss = references.supplyReference(new Selector(declaredType(SingletonScopelet.class), anyQualifiers()));
    assertNotNull(ss);
    references.destroy(ss);
  }

  @Test
  final void testDestroyNull() {
    final DefaultReferences<Object> references = new DefaultReferences<>(new DefaultInstances(Set.of(simpleBean)));
    references.destroy(null);
  }
  
  @Test
  final void test() {
    final DefaultReferences<Object> references = new DefaultReferences<>(new DefaultInstances(Set.of(simpleBean)));
    final Selector s = new Selector(declaredType(Simple.class), defaultQualifiers());
    final Simple simple = references.supplyReference(s);
    assertNotNull(simple);
    // Doesn't do anything, on purpose, because simple wasn't handed out via iterator.next().
    references.destroy(simple);
  }

  private static final class Simple {

    private Simple() {
      super();
      System.out.println("*** created Simple");
    }
    
  }
  
}
