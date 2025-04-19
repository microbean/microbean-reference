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
import java.util.Objects;

import javax.lang.model.type.TypeMirror;

import org.microbean.bean.AmbiguousReductionException;
import org.microbean.bean.AttributedType;
import org.microbean.bean.AutoCloseableRegistry;
import org.microbean.bean.Bean;
import org.microbean.bean.BeanReduction;
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

import static javax.lang.model.type.TypeKind.VOID;

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
public class Request<I, R> implements Creation<I>, Destruction, References<R> {


  /*
   * Instance fields.
   */


  private final AttributedType voidType;

  private final Selectable<? super AttributedType, Bean<?>> s;

  private final Instances instances;

  private final AutoCloseableRegistry acr;

  private final ClientProxier cp;

  private final BeanReduction<I> br;

  private final AttributedType rType;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link Request}.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @param s a {@link Selectable} providing access to {@link Bean}s by {@link AttributedType}; must not be {@code null}
   *
   * @param instances an {@link Instances} responsible for using a {@link Bean} to acquire an appropriate {@link
   * java.util.function.Supplier} of contextual instances; must not be {@code null}
   *
   * @param acr an {@link AutoCloseableRegistry}; may be {@code null} in which case a default implementation will be
   * used instead
   *
   * @param cp a {@link ClientProxier}; must not be {@code null}
   *
   * @param br a {@link BeanReduction} describing the request in progress; may be {@code null} in certain rare
   * primordial cases
   *
   * @exception NullPointerException if {@code domain}, {@code s}, {@code instances}, or {@code cp} is {@code null}
   */
  public Request(final Domain domain,
                 final Selectable<? super AttributedType, Bean<?>> s,
                 final Instances instances,
                 final AutoCloseableRegistry acr,
                 final ClientProxier cp,
                 final BeanReduction<I> br) {
    this(new AttributedType(domain.noType(VOID)),
         s,
         instances,
         acr,
         cp,
         br);
  }

  private Request(final AttributedType voidType,
                  final Selectable<? super AttributedType, Bean<?>> s,
                  final Instances instances,
                  final AutoCloseableRegistry acr,
                  final ClientProxier cp,
                  final BeanReduction<I> br) {
    this(voidType, s, instances, acr, cp, br, voidType);
  }

  private Request(final AttributedType voidType,
                  final Selectable<? super AttributedType, Bean<?>> s,
                  final Instances instances,
                  final AutoCloseableRegistry acr, // nullable
                  final ClientProxier cp,
                  final BeanReduction<I> br, // the bean being made
                  final AttributedType rType) { // the type of the references returned
    if (voidType.type().getKind() != VOID) {
      throw new IllegalArgumentException("voidType");
    }
    this.voidType = voidType;
    this.s = Objects.requireNonNull(s, "s");
    this.instances = Objects.requireNonNull(instances, "instances");
    this.rType = Objects.requireNonNull(rType, "rType");
    this.cp = Objects.requireNonNull(cp, "cp");
    this.acr = acr == null ? new DefaultAutoCloseableRegistry() : acr;
    this.br = br;
  }


  /*
   * Instance methods.
   */


  @Override // Destruction
  public void close() {
    this.acr.close();
    this.instances.close();
  }

  @Override
  public Iterator<R> iterator() {
    return new ReferencesIterator();
  }

  // Called by ReferencesIterator below
  private final R get(final Request<R, Void> r) {
    final Bean<R> bean = r.br.bean();
    final Id id = bean.id();
    if (this.instances.proxiable(id)) {
      // TODO: this means destroyable
      this.cp.clientProxy(id, instances.supplier(bean, r));
    }
    // TODO: ask instances if r is destroyable and save it off
    return instances.supplier(bean, r).get();
  }

  @Override // ReferencesSelector
  public <R> References<R> references(final AttributedType t) {
    return
      new Request<>(this.voidType,
                    this.s,
                    this.instances,
                    this.acr, // no newChild() call yet
                    this.cp,
                    this.br, // we're not changing the bean being made
                    t);
  }

  @Override // References<R>
  public boolean destroy(final R r) {
    if (r != null) { // and is in dependent scope; we'll deal with that later
      // TODO: remove it from a collection of dependent refs returned by get(Request) above
    }
    return false;
  }


  /*
   * Inner and nested classes.
   */


  private final class ReferencesIterator implements Iterator<R> {

    private final Iterator<Bean<?>> i;

    private R ref;

    private ReferencesIterator() {
      super();
      this.i = s.select(rType).iterator();
    }

    @Override // Iterator<R>
    public final boolean hasNext() {
      return this.i.hasNext();
    }

    @Override // Iterator<R>
    @SuppressWarnings("unchecked")
    public final R next() {
      return
        this.ref = get(new Request<>(voidType,
                                     s,
                                     instances,
                                     acr.newChild(), // critical
                                     cp,
                                     new BeanReduction<>(rType, (Bean<R>)this.i.next()),
                                     voidType));
    }

    @Override // Iterator<R>
    public final void remove() {
      final R ref = this.ref;
      this.ref = null;
      Request.this.destroy(ref);
    }

  }

}
