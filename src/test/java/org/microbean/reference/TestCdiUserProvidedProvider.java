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

import java.util.Collection;

import jakarta.enterprise.context.Dependent;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;

import jakarta.enterprise.util.TypeLiteral;

import jakarta.inject.Provider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class TestCdiUserProvidedProvider {

  private SeContainer container;
  
  private TestCdiUserProvidedProvider() {
    super();
  }

  @BeforeEach
  public void startContainer() {
    this.container = SeContainerInitializer.newInstance()
      .disableDiscovery()
      .addBeanClasses(DummyProvider.class)
      .initialize();
  }

  @AfterEach
  public void stopContainer() {
    if (this.container != null) {
      this.container.close();
    }
  }

  @Test
  final void testReferenceAcquisition() {
    final TypeLiteral<Provider<String>> t = new TypeLiteral<>() {};
    final Collection<?> c = this.container.getBeanManager().getBeans(t.getType());
    assertEquals(1, c.size()); // DummyProvider was never registered
    final Provider<String> p = this.container.select(t).get();
    // https://github.com/weld/core/discussions/3155
    assertNotEquals(DummyProvider.class, p.getClass());
  }

  @Dependent
  private static final class DummyProvider implements Provider<String> {

    DummyProvider() {
      super();
    }

    @Override // Provider<String>
    public final String get() {
      return "Foo";
    }
    
  }
  
}
