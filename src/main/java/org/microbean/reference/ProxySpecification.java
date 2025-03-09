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
import java.util.Objects;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.microbean.bean.BeanTypeList;

import org.microbean.construct.Domain;

import static org.microbean.bean.BeanTypes.proxiableBeanType;

/**
 * Information about a client proxy.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public final class ProxySpecification {

  private final Domain domain;

  private final DeclaredType sc;

  private final List<TypeMirror> interfaces;

  private final String name;


  /*
   * Constructors.
   */


  ProxySpecification(final Domain domain, final BeanTypeList types) {
    super();
    this.domain = Objects.requireNonNull(domain, "domain");
    final TypeMirror t = types.get(0);
    if (t.getKind() != TypeKind.DECLARED || domain.javaLangObject(t) && types.size() == 1) {
      throw new IllegalArgumentException("types: " + types);
    } else if (((DeclaredType)t).asElement().getKind() == ElementKind.INTERFACE) {
      this.sc = (DeclaredType)domain.javaLangObject().asType();
      this.interfaces = types;
    } else if (!proxiableBeanType(t)) {
      throw new IllegalArgumentException("types: " + types);
    } else {
      this.sc = (DeclaredType)t;
      final int interfaceIndex = types.interfaceIndex();
      this.interfaces = interfaceIndex < 0 ? List.of() : types.subList(interfaceIndex, types.size());
    }
    this.name = computeName(domain, this.sc, this.interfaces);
  }


  /*
   * Instance methods.
   */


  @Override // Object
  public final boolean equals(final Object other) {
    if (other == this) {
      return true;
    } else if (other != null && other.getClass() == this.getClass()) {
      final ProxySpecification her = (ProxySpecification)other;
      if (!this.domain.equals(her.domain)) {
        return false;
      }
      if (!this.domain.sameType(this.superclass(), her.superclass())) {
        return false;
      }
      final List<TypeMirror> interfaces = this.interfaces();
      final List<TypeMirror> herInterfaces = her.interfaces();
      final int size = interfaces.size();
      if (herInterfaces.size() != size) {
        return false;
      }
      for (int i = 0; i < size; i++) {
        if (!this.domain.sameType(interfaces.get(i), herInterfaces.get(i))) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  @Override // Object
  public final int hashCode() {
    int hashCode = 31;
    hashCode = 17 * hashCode + this.domain.hashCode();
    hashCode = 17 * hashCode + this.sc.hashCode();
    hashCode = 17 * hashCode + this.interfaces.hashCode();
    return hashCode;
  }

  /**
   * Returns the interfaces the proxy should implement.
   *
   * @return a non-{@code null}, immutable {@link List} of {@link TypeMirror}s
   */
  public final List<TypeMirror> interfaces() {
    return this.interfaces;
  }

  /**
   * Returns the name the proxy class should have.
   *
   * @return a non-{@code null} {@link String}
   */
  public final String name() {
    return this.name;
  }

  /**
   * Returns the superclass the proxy should specialize.
   *
   * @return a non-{@code null} {@link DeclaredType}
   */
  public final DeclaredType superclass() {
    return this.sc;
  }


  /*
   * Static methods.
   */


  static final String computeName(final Domain domain, final DeclaredType superclass, final List<TypeMirror> interfaces) {

    // TODO: there will absolutely be edge cases here and we know this is not complete.

    if (superclass.getKind() != TypeKind.DECLARED) {
      throw new IllegalArgumentException("superclass: " + superclass);
    }
    final DeclaredType proxyClassSibling;
    if (domain.javaLangObject(superclass)) {
      if (interfaces.isEmpty()) {
        throw new IllegalArgumentException("interfaces.isEmpty(); superclass: java.lang.Object");
      }
      // Interface-only. There will be at least one and it will be the most specialized.
      proxyClassSibling = (DeclaredType)interfaces.get(0);
      if (proxyClassSibling.getKind() != TypeKind.DECLARED) {
        throw new IllegalArgumentException("interfaces: " + interfaces);
      }
    } else {
      proxyClassSibling = superclass;
    }
    return domain.toString(domain.binaryName((TypeElement)proxyClassSibling.asElement())) + "_Proxy";
  }

}
