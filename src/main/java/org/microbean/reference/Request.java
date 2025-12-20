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

import java.util.Iterator;

import java.util.function.Supplier;

import org.microbean.assign.AttributedType;
import org.microbean.assign.Selectable;

import org.microbean.bean.Bean;
import org.microbean.bean.BeanException;
import org.microbean.bean.Creation;
import org.microbean.bean.Destruction;
import org.microbean.bean.Id;
import org.microbean.bean.References;
import org.microbean.bean.ReferencesSelector;

import org.microbean.construct.Domain;

import org.microbean.proxy.Proxy;

import static java.util.Collections.emptyIterator;

import static java.util.Objects.requireNonNull;

/**
 * A central object representing a request for dependencies that is a {@link Creation} (and therefore also a {@link
 * Destruction}), a {@link DestructorRegistry}, and a {@link References}.
 *
 * <p>Instances of this class are the heart and soul of a dependency injection and acquisition system.</p>
 *
 * @param <I> the contextual instance type being instantiated (see for example {@link Creation})
 *
 * @param <R> the contextual reference type being sought (see {@link References})
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see Creation
 *
 * @see Destruction
 *
 * @see DestructorRegistry
 *
 * @see References
 *
 * @see org.microbean.bean.Factory#create(Creation)
 *
 * @see org.microbean.bean.Factory#destroy(Object, Destruction)
 */
public final class Request<I, R> implements Creation<I>, Destruction, DestructorRegistry, References<R> {


  /*
   * Instance fields.
   */


  private final Domain domain;

  private final Selectable<? super AttributedType, Bean<?>> beans;

  private final Instances instances;

  private final DestructorTree destructorTree;

  private final ClientProxier cp;

  private final Bean<I> b; // nullable; B and R must then be (effectively) Void

  private final AttributedType rType; // nullable; R must then be Void


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link Request}.
   *
   * @param d a {@link Domain}; must not be {@code null}
   *
   * @param s a {@link Selectable} providing access to {@link Bean}s by {@link AttributedType}; must not be {@code
   * null}; must be safe for concurrent use by multiple threads; often assembled out of methods present in the {@link
   * org.microbean.bean.Selectables} and {@link org.microbean.bean.Beans} classes, among other such utility classes
   *
   * @param instances an {@link Instances} responsible for using a {@link Bean} to acquire an appropriate {@link
   * Supplier} of contextual instances; must not be {@code null}
   *
   * @param cp a {@link ClientProxier}; must not be {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @see #Request(Domain, Selectable, Instances, DestructorTree, ClientProxier)
   */
  public Request(final Domain d,
                 final Selectable<? super AttributedType, Bean<?>> s,
                 final Instances instances,
                 final ClientProxier cp) {
    this(d, s, instances, null, cp, null, null);
  }

  /**
   * Creates a new {@link Request}.
   *
   * @param d a {@link Domain}; must not be {@code null}
   *
   * @param s a {@link Selectable} providing access to {@link Bean}s by {@link AttributedType}; must not be {@code
   * null}; must be safe for concurrent use by multiple threads; often assembled out of methods present in the {@link
   * org.microbean.bean.Selectables} and {@link org.microbean.bean.Beans} classes, among other such utility classes
   *
   * @param instances an {@link Instances} responsible for using a {@link Bean} to acquire an appropriate {@link
   * Supplier} of contextual instances; must not be {@code null}
   *
   * @param destructorTree a {@link DestructorTree}; may be {@code null} in which case a default implementation will be used
   * instead
   *
   * @param cp a {@link ClientProxier}; must not be {@code null}
   *
   * @exception NullPointerException if {@code s}, {@code instances}, or {@code cp} is {@code null}
   */
  public Request(final Domain d,
                 final Selectable<? super AttributedType, Bean<?>> s,
                 final Instances instances,
                 final DestructorTree destructorTree, // nullable
                 final ClientProxier cp) {
    this(d, s, instances, destructorTree, cp, null, null);
  }

  private Request(final Domain d,
                  final Selectable<? super AttributedType, Bean<?>> s,
                  final Instances instances,
                  final DestructorTree destructorTree, // nullable
                  final ClientProxier cp,
                  final Bean<I> b, // nullable
                  final AttributedType rType) { // the type of the references returned (<R>); nullable
    this.domain = requireNonNull(d, "d");
    this.beans = requireNonNull(s, "s");
    this.instances = requireNonNull(instances, "instances");
    this.cp = requireNonNull(cp, "cp");
    this.destructorTree = destructorTree == null ? new DefaultDestructorTree() : destructorTree;
    this.b = b;
    this.rType = rType;
  }


  /*
   * Instance methods.
   */


  @Override // Destruction
  public final void close() {
    this.destructorTree.close();
  }

  @Override // ReferencesSelector
  public final boolean destroy(final Object r) {
    final Destructor destructor = this.destructorTree.remove(r instanceof Proxy<?> p ? p.$proxied() : r);
    if (destructor != null) {
      destructor.destroy(); // I keep going back and forth on whether this should be under some kind of lock, or whether the Destructor contract covers it
      return true;
    }
    return false;
  }

  @Override // ReferencesSelector
  public final Domain domain() {
    return this.domain;
  }

  @Override // Creation<I>
  public final Id id() {
    return this.b == null ? null : this.b.id();
  }

  @Override // References<R> (Iterable<R>)
  public final Iterator<R> iterator() {
    return new ReferencesIterator(); // inner class; see below
  }

  @Override // ReferencesSelector
  public final <R> R reference(final Bean<R> bean) {
    final Supplier<? extends R> supplier = this.instances.supplier(bean, this.newChild(bean)); // newChild is critical
    final Id id = bean.id();
    return this.instances.proxiable(id) ? this.cp.clientProxy(id, supplier) : supplier.get();
  }

  @Override // ReferencesSelector
  @SuppressWarnings("unchecked")
  public final <X> References<X> references(final AttributedType rType) {
    return this.rType == rType ? (References<X>)this :
      // This basically returns "this" but with a new rType. But Request is immutable so we make a copy.
      new Request<>(this.domain,
                    this.beans,
                    this.instances,
                    this.destructorTree, // deliberately NO this.destructorTree.newChild() call
                    this.cp,
                    this.b, // nullable; <I> will then be (effectively) Void
                    rType); // nullable; <X> will then be Void
  }

  @Override // DestructorTree (DestructorRegistry)
  public final boolean register(final Object reference, final Destructor destructor) {
    return this.destructorTree.register(reference, destructor);
  }

  @Override // References<R>
  public final int size() {
    return this.rType == null ? 0 : this.beans.select(this.rType).size();
  }

  /*
   * Private instance methods.
   */

  private final Iterator<Bean<?>> beanIterator() {
    return this.rType == null ? emptyIterator() : this.beans.select(this.rType).iterator();
  }

  @SuppressWarnings("unchecked")
  private final <X> Request<X, ?> newChild(final Bean<X> b) {
    if (b == null) {
      if (this.b == null) {
        return (Request<X, R>)this; // both <X> and <R> are effectively Void
      }
    } else if (b.equals(this.b)) {
      return (Request<X, R>)this;
    }
    return
      new Request<X, Void>(this.domain,
                           this.beans,
                           this.instances,
                           this.destructorTree.newChild(), // critical; !b.equals(this.b)
                           this.cp,
                           b, // nullable; if so, <X> better resolve to Void
                           null); // rType; <R> resolves to Void
  }


  /*
   * Inner and nested classes.
   */


  // NOT thread-safe.
  private final class ReferencesIterator implements Iterator<R> {

    private Iterator<Bean<?>> i;

    private R ref;

    private ReferencesIterator() {
      super();
      if (rType == null) {
        this.i = emptyIterator();
      }
    }

    @Override // Iterator<R>
    public final boolean hasNext() {
      if (this.i == null) {
        this.i = beanIterator();
      }
      return this.i.hasNext();
    }

    @Override // Iterator<R>
    @SuppressWarnings("unchecked")
    public final R next() {
      if (this.i == null) {
        this.i = beanIterator();
      }
      return this.ref = reference((Bean<R>)this.i.next());
    }

    @Override // Iterator<R>
    public final void remove() {
      final R ref = this.ref;
      if (ref == null) {
        throw new IllegalStateException(); // per Iterator#remove() contract
      }
      this.ref = null;
      destroy(ref);
    }

  }

}
