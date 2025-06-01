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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.lang.model.type.TypeMirror;

import org.microbean.bean.AmbiguousReductionException;
import org.microbean.bean.AttributedType;
import org.microbean.bean.AutoCloseableRegistry;
import org.microbean.bean.Bean;
import org.microbean.bean.BeanException;
import org.microbean.bean.Creation;
import org.microbean.bean.DefaultAutoCloseableRegistry;
import org.microbean.bean.Destruction;
import org.microbean.bean.Factory;
import org.microbean.bean.Id;
import org.microbean.bean.Reducible;
import org.microbean.bean.References;
import org.microbean.bean.ReferencesSelector;
import org.microbean.bean.Selectable;
import org.microbean.bean.UnsatisfiedReductionException;

import org.microbean.construct.Domain;

/**
 * A central object representing a request for dependencies that is a {@link Creation} (and therefore also a {@link
 * Destruction}) and a {@link References}.
 *
 * <p>Instances of this class are the heart and soul of a dependency injection and acquisition system.</p>
 *
 * @param <I> the contextual instance type (see for example {@link Creation})
 *
 * @param <R> the contextual reference type (see {@link References})
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see Creation
 *
 * @see Destruction
 *
 * @see References
 *
 * @see Factory#create(Creation)
 *
 * @see Factory#destroy(Object, Destruction)
 */
public final class Request<I, R> implements Creation<I>, Destruction, References<R> {


  /*
   * Instance fields.
   */


  private final Selectable<? super AttributedType, Bean<?>> s;

  private final Instances instances;

  private final AutoCloseableRegistry acr;

  private final ClientProxier cp;

  private final Bean<I> b;

  private final AttributedType rType;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link Request}.
   *
   * @param s a {@link Selectable} providing access to {@link Bean}s by {@link AttributedType}; must not be {@code
   * null}; must be safe for concurrent use by multiple threads; often assembled out of methods present in the {@link
   * org.microbean.bean.Selectables} and {@link org.microbean.bean.Beans} classes, among other such utility classes
   *
   * @param instances an {@link Instances} responsible for using a {@link Bean} to acquire an appropriate {@link
   * java.util.function.Supplier} of contextual instances; must not be {@code null}
   *
   * @param acr an {@link AutoCloseableRegistry}; may be {@code null} in which case a default implementation will be
   * used instead
   *
   * @param cp a {@link ClientProxier}; must not be {@code null}
   *
   * @exception NullPointerException if {@code s}, {@code instances}, or {@code cp} is {@code null}
   */
  public Request(final Selectable<? super AttributedType, Bean<?>> s,
                 final Instances instances,
                 final AutoCloseableRegistry acr,
                 final ClientProxier cp) {
    this(s, instances, acr, cp, null, null);
  }

  private Request(final Selectable<? super AttributedType, Bean<?>> s,
                  final Instances instances,
                  final AutoCloseableRegistry acr, // nullable
                  final ClientProxier cp,
                  final Bean<I> b,
                  final AttributedType rType) { // the type of the references returned; nullable
    this.s = Objects.requireNonNull(s, "s");
    this.instances = Objects.requireNonNull(instances, "instances");
    this.cp = Objects.requireNonNull(cp, "cp");
    this.acr = acr == null ? new DefaultAutoCloseableRegistry() : acr;
    this.b = b;
    this.rType = rType;
  }


  /*
   * Instance methods.
   */


  @Override // Destruction
  public final void close() {
    try (this.instances; this.acr) {}
  }

  @Override // Creation<I>
  public final Id id() {
    return this.b == null ? null : this.b.id();
  }

  @Override // References<R> (Iterable<R>)
  public final Iterator<R> iterator() {
    return new ReferencesIterator();
  }

  // Called by ReferencesIterator below
  private final R get(final Request<R, Void> r) {
    final Bean<R> bean = r.b;
    final Id id = bean.id();
    if (this.instances.proxiable(id)) {
      final R ref = this.cp.clientProxy(id, instances.supplier(bean, r));
      // TODO: we know that ref can be destroyed because it's from a normal scope, i.e. it is not itself a contextual
      // instance, so save it in a collection somewhere so it can be destroyed via the #destroy(R) method (see CDI's
      // Instances#destroy(Object))
      return ref;
    }
    // TODO: ask instances if ref is destroyable and save it off
    return instances.supplier(bean, r).get();
  }

  @Override // ReferencesSelector
  public final <R> References<R> references(final AttributedType t) {
    return new Request<>(this.s, this.instances, this.acr, this.cp, this.b, t);
  }

  @Override // References<R>
  public final int size() {
    return this.s.select(this.rType).size();
  }

  @Override // References<R>
  public final boolean destroy(final R r) {
    if (r != null) { // and is in dependent scope; we'll deal with that later
      // TODO: remove it from a collection of dependent refs returned by get(Request) above
    }
    return false;
  }


  /*
   * Inner and nested classes.
   */


  private final class ReferencesIterator implements Iterator<R> {

    private Iterator<Bean<?>> i;

    private R ref;

    private ReferencesIterator() {
      super();
    }

    @Override // Iterator<R>
    public final boolean hasNext() {
      if (rType == null) {
        return false;
      }
      if (this.i == null) {
        this.i = s.select(rType).iterator();
      }
      return this.i.hasNext();
    }

    @Override // Iterator<R>
    public final R next() {
      if (rType == null) {
        throw new NoSuchElementException();
      }
      if (this.i == null) {
        this.i = s.select(rType).iterator();
      }
      @SuppressWarnings("unchecked")
      final R ref = get(new Request<>(s, instances, acr.newChild(), cp, (Bean<R>)this.i.next(), null));
      this.ref = ref;
      return ref;
    }

    @Override // Iterator<R>
    public final void remove() {
      final R ref = this.ref;
      this.ref = null;
      Request.this.destroy(ref);
    }

  }

}
