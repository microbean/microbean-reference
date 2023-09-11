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

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import org.microbean.bean.Id;
import org.microbean.bean.BeanSelectionCriteria;

import org.microbean.lang.TypeAndElementSource;

@FunctionalInterface
public interface ClassNamer {

  public String className(final BeanSelectionCriteria s, final Id id);


  /*
   * Static methods.
   */


  public static DeclaredType supertype(final TypeAndElementSource tes, final Id id) {
    final List<? extends TypeMirror> classes = id.types().classes();
    return classes.isEmpty() ? tes.declaredType("java.lang.Object") : (DeclaredType)classes.get(0);
  }

  public static ModuleElement moduleElementOf(final Element e) {
    return (ModuleElement)packageElementOf(e).getEnclosingElement();
  }

  public static PackageElement packageElementOf(Element e) {
    while (e != null && e.getKind() != ElementKind.PACKAGE) {
      e = e.getEnclosingElement();
    }
    return (PackageElement)e;
  }

}
