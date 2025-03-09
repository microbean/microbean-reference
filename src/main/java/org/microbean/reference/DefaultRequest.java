/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2024–2025 microBean™.
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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

import org.microbean.bean.AttributedType;
import org.microbean.bean.AutoCloseableRegistry;
import org.microbean.bean.Bean;
import org.microbean.bean.BeanQualifiersMatcher;
import org.microbean.bean.BeanReduction;
import org.microbean.bean.BeanTypeMatcher;
import org.microbean.bean.Creation;
import org.microbean.bean.DefaultAutoCloseableRegistry;
import org.microbean.bean.IdMatcher;
import org.microbean.bean.InterceptorBindingsMatcher;
import org.microbean.bean.RankedReducer;
import org.microbean.bean.Reducer;
import org.microbean.bean.Reducible;
import org.microbean.bean.Request;
import org.microbean.bean.Selectable;

import org.microbean.construct.Domain;

import static org.microbean.bean.Beans.cachingSelectableOf;

/**
 * A basic implementation of the {@link Request} interface.
 *
 * @param <I> the type of contextual instance initiating the request
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public class DefaultRequest<I>
  implements Request<I>,
             AutoCloseableRegistry,
             Reducible<AttributedType, Bean<?>>,
             Selectable<AttributedType, Bean<?>> {


  /*
   * Static fields.
   */


  private static final ThreadLocal<Queue<Request<?>>> REQUESTS = ThreadLocal.withInitial(ArrayDeque::new);


  /*
   * Instance fields.
   */


  private final AutoCloseableRegistry acr;

  private final BeanReduction<I> beanReduction;

  private final Creation<I> c;

  private final ClientProxier cp;

  private final Instances instances;

  private final Reducible<AttributedType, Bean<?>> reducible;

  private final Selectable<AttributedType, Bean<?>> selectable;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link DefaultRequest}.
   *
   * @param selectable a {@link Selectable} that selects {@link Bean}s given an {@link AttributedType}; must not be {@code null}
   *
   * @param reducible a {@link Reducible} that reduces collections of {@link Bean}s; must not be {@code null}
   *
   * @param instances an {@link Instances} implementation; must not be {@code null}
   *
   * @param cp a {@link ClientProxier}; must not be {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}
   */
  public DefaultRequest(final Selectable<AttributedType, Bean<?>> selectable,
                        final Reducible<AttributedType, Bean<?>> reducible, // normally Reducible.of(selectable, RankedReducer.of())
                        final Instances instances,
                        final ClientProxier cp) { // not nullable
    this(selectable,
         reducible,
         instances,
         null,
         null,
         cp,
         null);
  }

  /**
   * Creates a new {@link DefaultRequest}.
   *
   * @param selectable a {@link Selectable} that selects {@link Bean}s given an {@link AttributedType}; must not be {@code null}
   *
   * @param reducible a {@link Reducible} that reduces collections of {@link Bean}s; must not be {@code null}
   *
   * @param instances an {@link Instances} implementation; must not be {@code null}
   *
   * @param acr an {@link AutoCloseableRegistry}; may be {@code null} in which case a default implementation will be
   * used instead
   *
   * @param c a {@link Creation} implementation; may be {@code null} in which case a default implementation will be used
   * instead
   *
   * @param cp a {@link ClientProxier}; must not be {@code null}
   *
   * @exception NullPointerException if {@code selectable}, {@code reducible}, {@code instances} or {@code cp} is {@code
   * null}
   */
  public DefaultRequest(final Selectable<AttributedType, Bean<?>> selectable,
                        final Reducible<AttributedType, Bean<?>> reducible, // normally Reducible.of(selectable, RankedReducer.of())
                        final Instances instances,
                        final AutoCloseableRegistry acr, // nullable
                        final Creation<I> c, // nullable
                        final ClientProxier cp) { // not nullable
    this(selectable,
         reducible,
         instances,
         acr,
         c,
         cp,
         null);
  }

  private DefaultRequest(final Selectable<AttributedType, Bean<?>> selectable,
                         final Reducible<AttributedType, Bean<?>> reducible, // normally Reducible.of(selectable, RankedReducer.of())
                         final Instances instances,
                         final AutoCloseableRegistry acr, // nullable
                         final Creation<I> c, // nullable
                         final ClientProxier cp, // not nullable
                         final BeanReduction<I> beanReduction) { // nullable, but only for the first one
    super();
    this.selectable = Objects.requireNonNull(selectable, "selectable");
    this.reducible = Objects.requireNonNull(reducible, "reducible");
    this.instances = Objects.requireNonNull(instances, "instances");
    this.cp = Objects.requireNonNull(cp, "cp");
    this.beanReduction = beanReduction;
    this.acr = acr == null ? new DefaultAutoCloseableRegistry() : acr;
    this.c = c == null ? i -> {} : c;
  }


  /*
   * Instance methods.
   */


  @Override // Request<I>
  public final BeanReduction<I> beanReduction() {
    return this.beanReduction; // will be null for the first/primordial Request and no other
  }

  @Override // Request<I>
  @SuppressWarnings("unchecked")
  public <J> DefaultRequest<J> child(final BeanReduction<J> beanReduction) {
    if (beanReduction.equals(this.beanReduction)) {
      return (DefaultRequest<J>)this;
    }
    final DefaultRequest<J> child =
      new DefaultRequest<J>(this.selectable,
                            this.reducible,
                            this.instances,
                            this.newChild(), // NOTE
                            (Creation<J>)this.c,
                            this.cp,
                            beanReduction);
    if (!this.register(child)) {
      throw new IllegalStateException();
    }
    return child;
  }

  @Override // AutoCloseableRegistry, Instances (AutoCloseable), Request (AutoCloseable)
  public void close() {
    this.acr.close();
    this.instances.close();
  }

  @Override // AutoCloseableRegistry
  public boolean closed() {
    return this.acr.closed();
  }

  @Override // Creation<I>
  public final void created(final I i) {
    this.c.created(i);
  }

  @Override // AutoCloseableRegistry
  public final AutoCloseableRegistry newChild() {
    return this.acr.newChild();
  }

  @Override // Reducible<AttributedType, Bean<?>> // mostly for convenience
  public final Bean<?> reduce(final AttributedType attributedType) {
    return this.reducible.reduce(attributedType);
  }

  @Override // Request<I>
  @SuppressWarnings("unchecked")
  public final <R> R reference(final AttributedType attributedType) {
    return this.reference(new BeanReduction<>(attributedType, this.reduce(attributedType).cast()), (Request<R>)this);
  }

  @Override // ReferenceSelector
  public final <R> R reference(final AttributedType attributedType, final Creation<R> c) {
    return this.reference(new BeanReduction<>(attributedType, this.reduce(attributedType).cast()), this.toRequest(c));
  }

  @Override // ReferenceSelector
  public final <R> R reference(final AttributedType attributedType, final Bean<R> bean, final Creation<R> c) {
    return this.reference(new BeanReduction<>(attributedType, bean == null ? this.reduce(attributedType).cast() : bean.cast()), this.toRequest(c));
  }

  // Must not call any other reference() method
  final <R> R reference(final BeanReduction<R> beanReduction, Request<R> r) {
    final Request<R> request = (r == null ? this : r).child(beanReduction); // child(beanReduction) is critical
    final Queue<Request<?>> requests = REQUESTS.get();
    requests.offer(request); // push
    try {
      return
        this.instances.proxiable(request) ?
        this.cp.clientProxy(request, this.instances.supplier(request)) :
        this.instances.supplier(request).get();
    } finally {
      requests.poll(); // pop
    }
  }

  @Override // AutoCloseableRegistry
  public boolean register(final AutoCloseable closeable) {
    return this.acr.register(closeable);
  }

  @Override // Selectable<AttributedType, Bean<?>> // mostly for convenience
  public final List<Bean<?>> select(final AttributedType attributedType) {
    return this.selectable.select(attributedType);
  }

  @SuppressWarnings("unchecked")
  private final <R> Request<R> toRequest(final Creation<R> c) {
    // Note: deliberately no child/newChild semantics at all
    return switch (c) {
    case null -> null;
    case Request<R> r -> r;
    default -> new DefaultRequest<>(this.selectable,
                                    this.reducible,
                                    this.instances,
                                    this.acr,
                                    c,
                                    this.cp,
                                    (BeanReduction<R>)this.beanReduction);
    };
  }

}
