/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2025 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.microbean.reference;

import java.util.List;

import javax.lang.model.element.TypeElement;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.microbean.construct.DefaultDomain;
import org.microbean.construct.Domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TestProxySpecification {

  private Domain domain;
  
  private TestProxySpecification() {
    super();
  }

  @BeforeEach
  public void setup() {
    this.domain = new DefaultDomain();
  }

  @Disabled
  @Test
  final void testNameComputation() {
    final TypeElement e = this.domain.typeElement(this.getClass().getCanonicalName());
    assertEquals(this.getClass().getPackage().getName() + "$Proxy$" + this.getClass().getSimpleName(),
                 ProxySpecification.computeName(this.domain, (DeclaredType)e.asType(), List.of()));
  }

  @Test
  final void testClassForNameAndCanonicalNames() throws ClassNotFoundException {
    assertThrows(ClassNotFoundException.class, () -> Class.forName("java.util.Map.Entry", true, null));
    Class.forName("java.util.Map$Entry", true, null);
  }
  
}
