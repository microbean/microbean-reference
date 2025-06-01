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

import java.lang.invoke.MethodHandles.Lookup;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Function;
import java.util.function.Supplier;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import javax.lang.model.element.TypeElement;

import org.microbean.bean.Id;

import org.microbean.construct.Domain;

import org.microbean.proxy.AbstractReflectiveProxier;
import org.microbean.proxy.Proxy;
import org.microbean.proxy.ProxySpecification;

import static java.lang.invoke.MethodHandles.publicLookup;

import static java.lang.reflect.Proxy.newProxyInstance;

/**
 * An {@link AbstractReflectiveProxier} implementation that uses {@link java.lang.reflect.Proxy java.lang.reflect.Proxy}
 * machinery.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public class ReflectiveClientProxier extends AbstractReflectiveProxier<ProxySpecification> implements ClientProxier {

  private static final Lookup lookup = publicLookup();

  private static final Map<ProxySpecification, Object> proxyInstances = new ConcurrentHashMap<>();

  /**
   * Creates a new {@link ReflectiveClientProxier}.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @exception NullPointerException if {@code domain} is {@code null}
   */
  public ReflectiveClientProxier(final Domain domain) {
    super(domain);
  }

  @Override // ClientProxier
  public <R> R clientProxy(final Id id, final Supplier<? extends R> instanceSupplier) {
    return this.proxy(new ProxySpecification(this.domain(), id), instanceSupplier).$cast();
  }

  /**
   * Returns a {@link Proxy} appropriate for the supplied {@linkplain ProxySpecification specification} and {@link
   * Supplier} of contextual instances.
   *
   * @param <R> the contextual instance type
   *
   * @param ps an appropriate {@linkplain ProxySpecification proxy specification}; must not be {@code null}
   *
   * @param interfaces the interfaces to implement; every element is guaranteed to {@linkplain Class#isInterface() be an
   * interface}
   *
   * @param instanceSupplier a {@link Supplier} of contextual instances; must not be {@code null}; may or may not create
   * a new contextual instance each time it is invoked; may or may not be invoked multiple times depending on the
   * subclass implementation
   *
   * @return a non-{@code null} {@link Proxy}
   *
   * @exception NullPointerException if any argument is {@code null}
   */
  @Override // AbstractReflectiveProxier
  @SuppressWarnings("unchecked")
  protected final <R> Proxy<R> proxy(final ProxySpecification ps,
                                     final Class<?>[] interfaces,
                                     final Supplier<? extends R> instanceSupplier) {
    return
      (Proxy<R>)proxyInstances
      .computeIfAbsent(ps,
                       ps0 -> newProxyInstance(this.classLoader(),
                                               interfaces,
                                               new InvocationHandler() {
                                                 @Override // InvocationHandler
                                                 public final Object invoke(final Object p,
                                                                            final Method m,
                                                                            final Object[] a)
                                                   throws Throwable {
                                                   return switch (m) {
                                                   case Method x when equalsMethod(x) -> p == a[0];
                                                   case Method x when hashCodeMethod(x) -> System.identityHashCode(p);
                                                   default -> m.invoke(instanceSupplier.get(), a);
                                                   };
                                                 }
                                               }));
  }

}
