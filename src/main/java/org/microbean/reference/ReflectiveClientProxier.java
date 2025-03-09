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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import java.util.List;
import java.util.Objects;

import java.util.function.Function;
import java.util.function.Supplier;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import javax.lang.model.element.TypeElement;

import org.microbean.construct.Domain;

import static java.lang.reflect.Proxy.newProxyInstance;

/**
 * An {@link AbstractClientProxier} implementation that uses {@link java.lang.reflect.Proxy} machinery.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public class ReflectiveClientProxier extends AbstractClientProxier<Class<?>> {

  private static final Lookup lookup = MethodHandles.publicLookup();
  
  private final Function<? super List<? extends Class<?>>, ? extends ClassLoader> clf;

  /**
   * Creates a new {@link ReflectiveClientProxier}.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @exception NullPointerException if {@code domain} is {@code null}
   *
   * @see #ReflectiveClientProxier(Domain, Function)
   */
  public ReflectiveClientProxier(final Domain domain) {
    this(domain, i -> Thread.currentThread().getContextClassLoader());
  }

  /**
   * Creates a new {@link ReflectiveClientProxier}.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @param clf a {@link Function} that returns a {@link ClassLoader} appropriate for a {@link List} of {@link Class}
   * instances; must not be {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}
   */
  public ReflectiveClientProxier(final Domain domain,
                                 final Function<? super List<? extends Class<?>>, ? extends ClassLoader> clf) {
    super(domain);
    this.clf = Objects.requireNonNull(clf, "clf");
  }

  @Override // AbstractClientProxier<Class<?>>
  @SuppressWarnings("unchecked")
  protected <R> ClientProxy<R> instantiate(final ProxySpecification ps, final Supplier<? extends R> instanceSupplier) {
    final Domain domain = this.domain();
    if (!domain.javaLangObject(ps.superclass())) {
      throw new IllegalArgumentException("ps: " + ps);
    }
    final List<? extends TypeMirror> interfaceTypeMirrors = ps.interfaces();
    final int size = interfaceTypeMirrors.size();
    final Class<?>[] interfaces = new Class<?>[size];
    try {
      for (int i = 0; i < size; i++) {
        final TypeElement e = (TypeElement)((DeclaredType)interfaceTypeMirrors.get(i)).asElement();
        final String binaryName = domain.toString(domain.binaryName(e));
        interfaces[i] = Class.forName(binaryName, false, this.classLoader(e));
      }
    } catch (final ClassNotFoundException cnfe) {
      throw new IllegalArgumentException("ps: " + ps, cnfe);
    }
    return (ClientProxy<R>)newProxyInstance(this.clf.apply(List.of(interfaces)), interfaces, new InvocationHandler() {
        @Override // InvocationHandler
        public final Object invoke(final Object p, final Method m, final Object[] a) throws Throwable {
          return switch (m) {
          case null -> throw new NullPointerException("m");
          case Method x when
            x.getDeclaringClass() == Object.class &&
            x.getReturnType() == boolean.class &&
            x.getParameterCount() == 1 &&
            x.getParameterTypes()[0] == Object.class &&
            x.getName().equals("equals") -> p == a[0];
          case Method x when
            x.getDeclaringClass() == Object.class &&
            x.getReturnType() == int.class &&
            x.getParameterCount() == 0 &&
            x.getName().equals("hashCode") -> System.identityHashCode(p);
          default -> m.invoke(instanceSupplier.get(), a);
          };
        }
      });
  }

  @Override // AbstractClientProxier<Void>
  protected Lookup lookup(final Class<?> c) {
    return lookup;
  }

}
