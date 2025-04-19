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

import java.lang.System.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;

import java.util.List;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import java.util.function.Supplier;

import javax.lang.model.element.TypeElement;

import org.microbean.bean.BeanTypeList;
import org.microbean.bean.Creation;
import org.microbean.bean.Id;

import org.microbean.construct.Domain;

import static java.lang.System.getLogger;
import static java.lang.System.Logger.Level.DEBUG;

import static java.lang.invoke.MethodType.methodType;

/**
 * A skeletal implementation of the {@link ClientProxier} interface.
 *
 * @param <T> the type of descriptor this implementation uses to represent (normally unloaded) generated classes
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
// <T>: the type of descriptor the underlying mechanism uses to represent (normally unloaded) generated classes;
// ByteBuddy uses DynamicType.Unloaded<?>; Red Hat projects use ClassFile or something like it, ReflectiveClientProxier
// uses Class<?>, and so on
public abstract class AbstractClientProxier<T> implements ClientProxier {


  /*
   * Static fields.
   */


  private static final Logger LOGGER = getLogger(AbstractClientProxier.class.getName());

  private static final ConcurrentMap<Key, ClientProxy<?>> clientProxyInstances = new ConcurrentHashMap<>();


  /*
   * Instance fields.
   */


  // Not sure the cache is worth it if we're storing clientProxyInstances
  private final ClassValue<MethodHandle> clientProxyClassConstructorMethodHandles;

  private final Domain domain;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link AbstractClientProxier}.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @exception NullPointerException if {@code domain} is {@code null}
   */
  protected AbstractClientProxier(final Domain domain) {
    super();
    this.domain = Objects.requireNonNull(domain, "domain");
    this.clientProxyClassConstructorMethodHandles = new ClassValue<>() {
        @Override
        protected final MethodHandle computeValue(final Class<?> c) {
          this.getClass().getModule().addReads(c.getModule());
          try {
            // The class the constructor creates will not be ClientProxy.class (that's an interface), but any invoker of
            // the corresponding MethodHandle won't know that, so adapt the handle's return type to be ClientProxy.class
            // (the erasure of one of the supertypes of c) instead of c. Now callers can call
            // (ClientProxy<?>)h.invokeExact(someSupplier) on it. See #instantiate(Class, Supplier) and
            // MethodHandle#invokeExact().
            return
              lookup(c).findConstructor(c, methodType(void.class, Supplier.class))
              .asType(methodType(ClientProxy.class, Supplier.class));
          } catch (final RuntimeException | Error e) {
            throw e;
          } catch (final Throwable e) {
            throw new ReferenceException(e.getMessage(), e);
          }
        }
      };
  }


  /*
   * Instance methods.
   */


  /**
   * Given a {@link TypeElement} that {@linkplain javax.lang.model.element.ElementKind#isDeclaredType() is a declared
   * type}, returns a {@link ClassLoader} suitable for eventually transforming it into a {@link Class}.
   *
   * <p>The default implementation of this method returns the {@linkplain Thread#getContextClassLoader() context
   * classloader}.</p>
   *
   * @param e a {@link TypeElement}; must not be {@code null}; must be a {@linkplain
   * javax.lang.model.element.ElementKind#isDeclaredType() declared type}
   *
   * @return a non-{@code null} {@link ClassLoader}
   *
   * @exception NullPointerException if {@code e} is {@code null}
   *
   * @exception IllegalArgumentException if {@code e} {@linkplain javax.lang.model.element.ElementKind#isDeclaredType()
   * is not a declared type}
   */
  protected ClassLoader classLoader(final TypeElement e) {
    if (!e.getKind().isDeclaredType()) {
      throw new IllegalArgumentException("e: " + e);
    }
    return Thread.currentThread().getContextClassLoader();
  }

  /**
   * Returns a <dfn>contextual reference</dfn> of type {@code R}, which is also a {@link ClientProxy
   * ClientProxy&lt;R&gt;}, given a {@link Supplier} of <dfn>contextual instances</dfn> of the appropriate type.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param <R> the type of the contextual reference
   *
   * @param id an {@link Id} qualifying the contextual instance that will be proxied; must not be {@code null}
   *
   * @param instanceSupplier a {@link Supplier} of contextual instances of type {@code R}; must not be {@code null}
   *
   * @return a contextual reference that is also a {@link ClientProxy ClientProxy&lt;R&gt;}; never {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}, or if the {@link #instantiate(ProxySpecification,
   * Supplier)} method, invoked as part of the implementation of this method, returns {@code null}
   *
   * @exception IllegalArgumentException if the supplied {@link Id} is not proxiable for any reason
   *
   * @exception ReferenceException if the {@link #instantiate(ProxySpecification, Supplier)} method throws a checked
   * {@link Exception}
   *
   * @see #instantiate(ProxySpecification, Supplier)
   *
   * @see ClientProxy
   */
  // By the time we get here, proxying is absolutely called for. (If we can't return an R, something went wrong, it's
  // not that the inputs were unsuitable.)
  @Override // ClientProxier
  public final <R> R clientProxy(final Id id, final Supplier<? extends R> instanceSupplier) {
    return this.clientProxy(id.types(), id.attributes(), instanceSupplier);
  }

  // Called only by #clientProxy(Id, Supplier).
  final <R> R clientProxy(final BeanTypeList types, final Object attributes, final Supplier<? extends R> instanceSupplier) {
    @SuppressWarnings("unchecked")
    final ClientProxy<R> cp =
      (ClientProxy<R>)clientProxyInstances.computeIfAbsent(new Key(new ProxySpecification(this.domain(), types), attributes),
                                                           k -> {
                                                             try {
                                                               return this.instantiate(k.proxySpecification(), instanceSupplier);
                                                             } catch (final RuntimeException | Error e) {
                                                               throw e;
                                                             } catch (final Throwable e) {
                                                               throw new ReferenceException(e.getMessage(), e);
                                                             }
                                                           });
    return cp.$cast();
  }

  // Called only by default implementation of #instantiate(ProxySpecification, Supplier)
  private final Class<?> clientProxyClass(final ProxySpecification ps) throws ClassNotFoundException {
    final String name = ps.name();
    final ClassLoader cl = this.classLoader((TypeElement)ps.superclass().asElement());
    Class<?> c;
    try {
      c = cl.loadClass(name);
    } catch (ClassNotFoundException primaryLoadException) {
      try {
        c = this.clientProxyClass(this.generate(ps), cl);
        if (c == null) {
          primaryLoadException = new ClassNotFoundException(name + "; clientProxyClass(generate(" + ps + "), " + cl + " == null");
          throw primaryLoadException;
        }
        if (LOGGER.isLoggable(DEBUG)) {
          LOGGER.log(DEBUG, "Proxy class generated because it was not initially found", primaryLoadException);
        }
      } catch (final ClassNotFoundException | RuntimeException generationException) {
        try {
          // We try again; generation may have failed because of a race condition defining a class in the
          // classloader. This will probably fail 99.9% of the time.
          c = cl.loadClass(name);
        } catch (final ClassNotFoundException secondaryLoadException) {
          generationException.addSuppressed(primaryLoadException);
          secondaryLoadException.addSuppressed(generationException);
          throw secondaryLoadException;
        } catch (final Error e) {
          e.addSuppressed(generationException);
          throw e;
        } catch (final Throwable wtf) {
          generationException.addSuppressed(wtf);
          throw generationException;
        }
      } catch (final Error e) {
        e.addSuppressed(primaryLoadException);
        throw e;
      } catch (final Throwable wtf) {
        primaryLoadException.addSuppressed(wtf);
        throw primaryLoadException;
      }
    }
    return c;
  }

  /**
   * Called by the default implementation of the {@link #instantiate(ProxySpecification, Supplier)} method, converts the
   * supplied class definition into a {@link Class}, using the supplied {@link ClassLoader}, and returns it (optional
   * operation).
   *
   * <p>If an override of the {@link #instantiate(ProxySpecification, Supplier)} method does something entirely
   * different, this method may never be called.</p>
   *
   * <p><strong>The default implementation of this method throws an {@link UnsupportedOperationException}.</strong></p>
   *
   * <p>In general, implementations of this method should not call the {@link #instantiate(ProxySpecification,
   * Supplier)} method, or undefined behavior may result.</p>
   *
   * @param t a class definition; must not be {@code null}
   *
   * @param cl a {@link ClassLoader}; must not be {@code null}
   *
   * @return a non-{@code null} {@link Class}
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @exception UnsupportedOperationException if class generation is not supported by this {@link AbstractClientProxier}
   *
   * @exception ClassNotFoundException if an invocation of {@link ClassLoader#loadClass(String)} fails
   *
   * @see #instantiate(ProxySpecification, Supplier)
   */
  // Turns a T into a Class (loads it) using a ClassLoader.
  protected Class<?> clientProxyClass(final T t, final ClassLoader cl) throws ClassNotFoundException {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the {@link Domain} {@linkplain #AbstractClientProxier(Domain) supplied at construction time}.
   *
   * @return a non-{@code null} {@link Domain}
   *
   * @see #AbstractClientProxier(Domain)
   */
  protected final Domain domain() {
    return this.domain;
  }

  /**
   * Called indirectly by the default implementation of the {@link #instantiate(ProxySpecification, Supplier)} method,
   * creates a generated class definition from the information present in the supplied {@link ProxySpecification}, and
   * returns it for eventual supplying to an inovcation of the {@link #clientProxyClass(Object, ClassLoader)} method
   * (optional operation).
   *
   * <p>If an override of the {@link #instantiate(ProxySpecification, Supplier)} method does something entirely
   * different, this method may never be called.</p>
   *
   * <p><strong>The default implementation of this method throws an {@link UnsupportedOperationException}.</strong></p>
   *
   * <p>Implementations of this method must not call the {@link #instantiate(ProxySpecification, Supplier)} method, or
   * undefined behavior may result.</p>
   *
   * @param ps a {@link ProxySpecification}; must not be {@code null}
   *
   * @return a non-{@code null} generated class definition
   *
   * @exception NullPointerException if {@code ps} is {@code null}
   *
   * @exception UnsupportedOperationException if class generation is unsupported by this {@link AbstractClientProxier}
   * implementation
   *
   * @exception Throwable if creation of the generated class definition fails
   *
   * @see #clientProxyClass(Object, ClassLoader)
   */
  protected T generate(final ProxySpecification ps) throws Throwable {
    throw new UnsupportedOperationException();
  }

  /**
   * Called indirectly by the {@link #clientProxy(Id, Supplier)} method when a new {@link ClientProxy
   * ClientProxy&lt;R&gt;} instance needs to be created, creates a new instance of a {@link ClientProxy
   * ClientProxy&lt;R&gt;} that proxies contextual instances supplied by the supplied {@code instanceSupplier}, and
   * returns it.
   *
   * <p>The default implementation of this method eventually calls the {@link #instantiate(Class, Supplier)} method with
   * the return value of an indirect invocation of the {@link #clientProxyClass(Object, ClassLoader)} method and the
   * supplied {@link Supplier} and returns the result.</p>
   *
   * <p>Overrides of this method that do something entirely different are not obligated to call the {@link
   * #clientProxyClass(Object, ClassLoader)} method or the {@link #instantiate(Class, Supplier)} method. In such a case,
   * <strong>those methods will be effectively orphaned</strong>.</p>
   *
   * @param <R> the type of contextual instance being proxied
   *
   * @param ps a {@link ProxySpecification}; must not be {@code null}
   *
   * @param instanceSupplier a {@link Supplier} of contextual instances of the {@code <R>} type; must not be {@code null}
   *
   * @return a non-{@code null} {@link ClientProxy ClientProxy&lt;R&gt;}
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @exception ReferenceException if instantiation fails
   *
   * @exception Throwable if instantiation fails
   *
   * @see #clientProxyClass(Object, ClassLoader)
   *
   * @see #instantiate(Class, Supplier)
   */
  protected <R> ClientProxy<R> instantiate(final ProxySpecification ps, final Supplier<? extends R> instanceSupplier)
    throws Throwable {
    return this.instantiate(this.clientProxyClass(ps), instanceSupplier);
  }

  /**
   * Called by the {@link #instantiate(ProxySpecification, Supplier)} method, creates a new instance of the supplied
   * {@code clientProxyClass} that proxies contextual instances supplied by the supplied {@code instanceSupplier}.
   *
   * <p>If a subclass overrides the {@link #instantiate(ProxySpecification, Supplier)} method to do something entirely
   * different, <strong>this method may not be called</strong>.</p>
   *
   * <p>Overrides of this method must not call the {@link #instantiate(ProxySpecification, Supplier)} method or
   * undefined behavior may result.</p>
   *
   * @param <R> the type of contextual instance being proxied
   *
   * @param clientProxyClass a {@link Class} {@linkplain Class#isAssignableFrom(Class) assignable to} {@link ClientProxy
   * ClientProxy.class} and {@linkplain ClientProxy#$cast() castable to} the {@code R} type; must not be {@code null}
   *
   * @param instanceSupplier a {@link Supplier} of contextual instances of the {@code R} type; must not be {@code
   * null}
   *
   * @return a non-{@code null} {@link ClientProxy ClientProxy&lt;R&gt;}
   *
   * @see #instantiate(ProxySpecification, Supplier)
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @exception ReferenceException if instantiation fails
   *
   * @exception Throwable if instantiation fails
   */
  protected <R> ClientProxy<R> instantiate(final Class<?> clientProxyClass, final Supplier<? extends R> instanceSupplier)
    throws Throwable {
    return (ClientProxy<R>)this.clientProxyClassConstructorMethodHandles
      .get(clientProxyClass)
      .invokeExact(instanceSupplier);
  }

  /**
   * Returns a {@link Lookup} suitable for the supplied {@link Class}.
   *
   * @param c a {@link Class}; must not be {@code null}
   *
   * @return a {@link Lookup}; never {@code null}
   *
   * @exception NullPointerException if {@code c} is {@code null}
   *
   * @see java.lang.invoke.MethodHandles#lookup()
   *
   * @see Lookup#in(Class)
   *
   * @see java.lang.invoke.MethodHandles#privateLookupIn(Class, Lookup)
   */
  protected abstract Lookup lookup(final Class<?> c);


  /*
   * Inner and nested classes.
   */


  private static final record Key(ProxySpecification proxySpecification, Object attributes) {}

}
