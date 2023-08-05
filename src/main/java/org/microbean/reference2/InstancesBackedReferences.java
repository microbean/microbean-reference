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
package org.microbean.reference2;

import java.util.Iterator;
import java.util.Objects;

import org.microbean.bean2.Assignability;
import org.microbean.bean2.Bean;
import org.microbean.bean2.BeanSet;
import org.microbean.bean2.Creation;
import org.microbean.bean2.References;
import org.microbean.bean2.Selector;

import org.microbean.lang.TypeAndElementSource;

import static java.util.Collections.emptyIterator;

import static org.microbean.bean2.Qualifiers.defaultQualifiers;

final class InstancesBackedReferences<R> implements References<R> {

  private final Assignability assignability;

  private final TypeAndElementSource tes;
  
  private final Instances instances;

  InstancesBackedReferences(final Assignability assignability,
                            final TypeAndElementSource tes,
                            final Instances instances) {
    super();
    this.tes = Objects.requireNonNull(tes, "tes");
    this.assignability = assignability == null ? new Assignability(tes) : assignability;
    this.instances = Objects.requireNonNull(instances, "instances");
  }

  @Override
  public final BeanSet beanSet() {
    return this.instances.beanSet();
  }

  @Override
  public final <I> Creation<I> creation() {
    return
      this.reference(new Selector(this.assignability,
                                  this.tes.declaredType(null,
                                                        this.tes.typeElement(Creation.class),
                                                        this.tes.wildcardType(null, null)),
                                  defaultQualifiers()),
                     null,
                     null);
  }

  @Override
  public final boolean destroy(final R r) {
    return false;
  }

  @Override
  public final Iterator<R> iterator() {
    return emptyIterator();
  }

  @Override
  public final <R> R reference(final Selector selector, final Bean<R> bean, final Creation<R> creation) {
    return this.instances.instance(selector, bean, creation, this);
  }

}
