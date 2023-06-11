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
import java.util.Objects;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.microbean.bean.BeanTypeList;
import org.microbean.bean.Id;
import org.microbean.bean.Selector;

import org.microbean.lang.TypeAndElementSource;

import org.microbean.lang.type.DelegatingTypeMirror;

public final class DefaultClassNamer implements ClassNamer {

  private final TypeAndElementSource typeAndElementSource;

  public DefaultClassNamer(final TypeAndElementSource typeAndElementSource) {
    this.typeAndElementSource = Objects.requireNonNull(typeAndElementSource, "typeAndElementSource");
  }

  @Override
  public final String className(final Selector s, final Id id) {
    final TypeElement supertypeElement = (TypeElement)supertype(id.types()).asElement();
    final PackageElement supertypePackage = packageElementOf(supertypeElement);
    final ModuleElement supertypeModule = (ModuleElement)supertypePackage.getEnclosingElement();

    final ModuleElement myModule = moduleElementOf(this.typeAndElementSource.typeElement(this.getClass()));

    // Somewhat counterintuitively: if the superclassModule can read myModule (if java.base can read com.foo), then we
    // use the superclass as the lookup class.  This feels backwards but works. I need to think more on this. For now
    // just blindly deliver a name that will break in many cases.

    final StringBuilder sb = new StringBuilder();
    if (!supertypePackage.isUnnamed()) {
      sb.append(supertypePackage.getQualifiedName()).append('.');
    }
    return sb.append("Proxy$")
      .append(supertypeElement.getQualifiedName().toString().replace('.', '$'))
      .toString();
  }

  private final DeclaredType supertype(final BeanTypeList btl) {
    final List<? extends TypeMirror> classes = btl.classes();
    final DeclaredType supertypeMirror = (DeclaredType)(classes.isEmpty() ? (DeclaredType)this.typeAndElementSource.typeElement("java.lang.Object").asType() : classes.get(0));
    assert supertypeMirror instanceof DelegatingTypeMirror; // no comletion locks needed
    assert supertypeMirror.getKind() == TypeKind.DECLARED; // not a TypeVariable, not an ArrayType
    return supertypeMirror;
  }

  private static final ModuleElement moduleElementOf(Element e) {
    return (ModuleElement)packageElementOf(e).getEnclosingElement();
  }

  private static final PackageElement packageElementOf(Element e) {
    while (e != null && e.getKind() != ElementKind.PACKAGE) {
      e = e.getEnclosingElement();
    }
    return (PackageElement)e;
  }

}
