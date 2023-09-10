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

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;

import java.util.function.Supplier;

import org.microbean.bean.Assignability;
import org.microbean.bean.Bean;
import org.microbean.bean.BeanSet;
import org.microbean.bean.Creation;
import org.microbean.bean.Id;
import org.microbean.bean.BeanSelector;
import org.microbean.bean.References;
import org.microbean.bean.ReferenceSelector;
import org.microbean.bean.UnsatisfiedResolutionException;

import org.microbean.lang.TypeAndElementSource;

import static org.microbean.bean.Qualifiers.defaultQualifiers;

public final class DefaultReferences<R> implements References<R> {

  private final ReferenceSelector referenceSelector;

  private final BeanSelector beanSelector;

  private final InstanceRemover instanceRemover;
  
  // @GuardedBy("itself")
  private final IdentityHashMap<R, Id> ids;
  
  public DefaultReferences(final BeanSelector beanSelector,
                           final ReferenceSelector referenceSelector,
                           final InstanceRemover instanceRemover) {
    super();
    this.beanSelector = Objects.requireNonNull(beanSelector, "beanSelector");
    this.referenceSelector = Objects.requireNonNull(referenceSelector, "referenceSelector");
    this.instanceRemover = Objects.requireNonNull(instanceRemover, "instanceRemover");
    this.ids = new IdentityHashMap<>();    
  }

  @Override // AutoCloseable
  public final void close() {
    synchronized (this.ids) {
      final Iterator<Entry<R, Id>> i = this.ids.entrySet().iterator();
      while (i.hasNext()) {
        this.remove(i.next().getValue());
        i.remove();
      }
    }
  }
  
  // Destroys r if and only if it is (a) dependent and (b) supplied by get()
  @Override
  public final boolean destroy(final R r) {
    if (r != null) {
      synchronized (this.ids) {
        return this.remove(this.ids.remove(r));
      }
    }
    return false;
  }

  @Override
  public final Iterator<R> iterator() {
    return new ReferenceIterator(this.referenceSelector.creation(), this.referenceSelector.beanSet().beans(this.beanSelector).iterator());
  }

  private final boolean remove(final Id id) {
    return id != null && this.instanceRemover.remove(id);
  }


  /*
   * Inner and nested classes.
   */


  private final class ReferenceIterator implements Iterator<R> {


    /*
     * Instance fields.
     */


    private final Creation<R> creation;

    private final Iterator<? extends Bean<?>> beanIterator;

    // @GuardedBy("this")
    private R r;


    /*
     * Constructors.
     */


    private ReferenceIterator(final Creation<R> creation, final Iterator<? extends Bean<?>> beanIterator) {
      super();
      this.creation = Objects.requireNonNull(creation, "creation");
      this.beanIterator = Objects.requireNonNull(beanIterator, "beanIterator");
    }


    /*
     * Instance methods.
     */


    @Override // Iterator<R>
    public final boolean hasNext() {
      return this.beanIterator.hasNext();
    }

    @Override // Iterator<R>
    public final R next() {
      final Bean<R> bean = this.beanIterator.next().cast();
      final R r = referenceSelector.reference(beanSelector, bean, this.creation);
      if (r != null) {
        synchronized (ids) {
          ids.putIfAbsent(r, bean.id());
        }
      }
      synchronized (this) {
        this.r = r;
      }
      return r;
    }

    @Override // Iterator<R>
    public final void remove() {
      final R r;
      synchronized (this) {
        r = this.r;
        this.r = null;
      }
      if (r != null) {
        destroy(r);
      }
    }

  }

}
