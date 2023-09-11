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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import java.util.function.Supplier;

import org.microbean.bean.Bean;
import org.microbean.bean.Creation;
import org.microbean.bean.Id;
import org.microbean.bean.References;
import org.microbean.bean.ReferenceSelector;
import org.microbean.bean.BeanSelectionCriteria;
import org.microbean.bean.Singleton;

import org.microbean.lang.TypeAndElementSource;

import org.microbean.scope.Scope;

import static java.lang.invoke.MethodType.methodType;

import static org.microbean.bean.Qualifiers.anyAndDefaultQualifiers;

import static org.microbean.scope.Scope.SINGLETON_ID;

public class DefaultClientProxier implements ClientProxier {

  private final Predicate tester;

  private final ConcurrentMap<Id, Proxy<?>> cache;

  private final ClientProxyClassSupplier cpcs;

  private final ClientProxyInstantiator cpi;

  public DefaultClientProxier(final Predicate tester,
                              final Map<? extends Id, ? extends Proxy<?>> precomputedProxies,
                              final ClientProxyClassSupplier cpcs,
                              final ClientProxyInstantiator cpi) {
    super();
    this.cache = new ConcurrentHashMap<>(precomputedProxies);
    this.tester = Objects.requireNonNull(tester, "tester");
    this.cpcs = Objects.requireNonNull(cpcs, "cpcs");
    this.cpi = Objects.requireNonNull(cpi, "cpi");
  }

  @Override // ClientProxier
  public final boolean needsClientProxy(final BeanSelectionCriteria beanSelectionCriteria,
                                        final Id id,
                                        final Creation<?> c,
                                        final ReferenceSelector r) {
    return this.tester.needsClientProxy(beanSelectionCriteria, id, c, r);
  }

  @Override // ClientProxier
  @SuppressWarnings("unchecked")
  public <R> R clientProxy(final BeanSelectionCriteria beanSelectionCriteria,
                           final Bean<R> bean,
                           final Creation<R> c,
                           final ReferenceSelector r,
                           final InstanceManager instanceManager) {
    return
      ((Proxy<R>)this.cache.computeIfAbsent(bean.id(),
                                            x -> this.cpi.instantiate(this.cpcs.clientProxyClass(beanSelectionCriteria, bean, instanceManager),
                                                                      () -> instanceManager.instance(beanSelectionCriteria, bean, c, r))))
      .cast();
  }


  /*
   * Static methods.
   */


  public static final Bean<DefaultClientProxier> bean(final TypeAndElementSource tes) {
    return bean(tes, Map.of());
  }

  public static final Bean<DefaultClientProxier> bean(final TypeAndElementSource tes,
                                                      final Map<? extends Id, ? extends Proxy<?>> precomputedProxies) {
    return
      new Bean<>(new Id(List.of(tes.declaredType(DefaultClientProxier.class),
                                tes.declaredType(ClientProxier.class)),
                        anyAndDefaultQualifiers(),
                        SINGLETON_ID),
                 (c, r) -> {
                   return new DefaultClientProxier(r.<Predicate>reference(new BeanSelectionCriteria(Predicate.class),
                                                                          null,
                                                                          cast(c)),
                                                   precomputedProxies, // defensive copying guaranteed to happen downstream
                                                   r.<ClientProxyClassSupplier>reference(new BeanSelectionCriteria(ClientProxyClassSupplier.class),
                                                                                         null,
                                                                                         cast(c)),
                                                   r.<ClientProxyInstantiator>reference(new BeanSelectionCriteria(ClientProxyInstantiator.class),
                                                                                        null,
                                                                                        cast(c)));
      });
  }

  @SuppressWarnings("unchecked")
  private static final <F, T> Creation<T> cast(final Creation<F> c) {
    return (Creation<T>)c;
  }


  /*
   * Inner and nested classes.
   */


  @FunctionalInterface
  public static interface ClientProxyInstantiator {

    public <R> Proxy<R> instantiate(final Class<? extends Proxy<R>> clientProxyClass,
                                    final Supplier<? extends R> supplier);

  }

  public static final class DefaultClientProxyInstantiator implements ClientProxyInstantiator {

    private static final MethodType PROXY_CONSTRUCTOR_METHOD_TYPE = methodType(void.class, Supplier.class);

    private static final Lookup lookup = MethodHandles.lookup();

    public DefaultClientProxyInstantiator() {
      super();
    }

    @Override // ClientProxyInstantiator
    public final <R> Proxy<R> instantiate(final Class<? extends Proxy<R>> clientProxyClass,
                                          final Supplier<? extends R> supplier) {
      try {
        this.getClass().getModule().addReads(clientProxyClass.getModule());
        return clientProxyClass.cast(lookup.findConstructor(clientProxyClass, PROXY_CONSTRUCTOR_METHOD_TYPE).invoke(supplier));
      } catch (final RuntimeException | Error e) {
        throw e;
      } catch (final Throwable e) {
        throw new ClientProxyInstantiationException(e.getMessage(), e);
      }
    }

    public static final Bean<DefaultClientProxyInstantiator> bean(final TypeAndElementSource tes) {
      return
        new Bean<>(new Id(List.of(tes.declaredType(DefaultClientProxyInstantiator.class),
                                  tes.declaredType(ClientProxyInstantiator.class)),
                          anyAndDefaultQualifiers(),
                          SINGLETON_ID),
                   new Singleton<>(new DefaultClientProxyInstantiator()));
    }

  }

  public static interface ClientProxyClassSupplier {

    // You need beanSelectionCriteria and bean because they're what you'll pass to instanceManager to get an instance that your proxy class
    // will proxy.
    //
    // You don't need References because all a client proxy does is wrap an instance.
    public <R> Class<? extends Proxy<R>> clientProxyClass(final BeanSelectionCriteria beanSelectionCriteria,
                                                          final Bean<R> bean,
                                                          final InstanceManager instanceManager);

    // Useful interface for determining a ClassLoader to use while supplying a class, if needed.
    @FunctionalInterface
    public static interface ClassLoaderSelector {

      public ClassLoader classLoader(final String className);

    }

    public static final class ContextClassLoaderSelector implements ClassLoaderSelector {

      public ContextClassLoaderSelector() {
        super();
      }

      @Override // ClassLoaderSelector
      public final ClassLoader classLoader(final String ignoredClassName) {
        return Thread.currentThread().getContextClassLoader();
      }

      public static final Bean<ContextClassLoaderSelector> bean(final TypeAndElementSource tes) {
        return
          new Bean<>(new Id(List.of(tes.declaredType(ContextClassLoaderSelector.class),
                                    tes.declaredType(ClassLoaderSelector.class)),
                            anyAndDefaultQualifiers(),
                            SINGLETON_ID),
                     new Singleton<>(new ContextClassLoaderSelector()));
      }

    }

  }

  public static final class GeneratingClientProxyClassSupplier implements ClientProxyClassSupplier {

    private final ClassNamer namer;

    private final ClassLoaderSelector classLoaderSelector;

    private final ClientProxyClassGenerator cg;

    public GeneratingClientProxyClassSupplier(final ClassNamer namer,
                                              final ClassLoaderSelector classLoaderSelector,
                                              final ClientProxyClassGenerator cg) {
      super();
      this.namer = Objects.requireNonNull(namer, "namer");
      this.classLoaderSelector = Objects.requireNonNull(classLoaderSelector, "classLoaderSelector");
      this.cg = Objects.requireNonNull(cg, "cg");
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <R> Class<? extends Proxy<R>> clientProxyClass(final BeanSelectionCriteria beanSelectionCriteria, final Bean<R> bean, final InstanceManager instanceManager) {
      final String className = this.namer.className(beanSelectionCriteria, bean.id());
      final ClassLoader cl = this.classLoaderSelector.classLoader(className);
      try {
        return (Class<? extends Proxy<R>>)Class.forName(className, false, cl);
      } catch (final ClassNotFoundException cnfe) {
        return this.cg.unloadedClientProxyClassDefinition(className, beanSelectionCriteria, bean, instanceManager).load(cl);
      }
    }

    public static final Bean<GeneratingClientProxyClassSupplier> bean(final TypeAndElementSource tes) {
      return
        new Bean<>(new Id(List.of(tes.declaredType(GeneratingClientProxyClassSupplier.class),
                                  tes.declaredType(ClientProxyClassSupplier.class)),
                          anyAndDefaultQualifiers(),
                          SINGLETON_ID),
                   (c, r) -> {
                     return
                       new GeneratingClientProxyClassSupplier(r.<ClassNamer>reference(new BeanSelectionCriteria(ClassNamer.class),
                                                                                      null,
                                                                                      cast(c)),
                                                              r.<ClassLoaderSelector>reference(new BeanSelectionCriteria(ClassLoaderSelector.class),
                                                                                               null,
                                                                                               cast(c)),
                                                              r.<ClientProxyClassGenerator>reference(new BeanSelectionCriteria(ClientProxyClassGenerator.class),
                                                                                                     null,
                                                                                                     cast(c)));
        });

    }

    public static interface ClientProxyClassGenerator {

      public <R> UnloadedClientProxyClassDefinition<R> unloadedClientProxyClassDefinition(final String className,
                                                                                          final BeanSelectionCriteria s,
                                                                                          final Bean<R> b,
                                                                                          final InstanceManager i); // yes, InstanceManager, no, not ReferenceSelector

      @FunctionalInterface
      public static interface UnloadedClientProxyClassDefinition<R> {

        public Class<? extends Proxy<R>> load(final ClassLoader cl);

      }

    }

  }

  @FunctionalInterface
  public static interface Predicate {

    public boolean needsClientProxy(final BeanSelectionCriteria beanSelectionCriteria,
                                    final Id id,
                                    final Creation<?> c,
                                    final ReferenceSelector r);

  }

  public static final class DefaultPredicate implements Predicate {

    private final TypeAndElementSource tes;

    public DefaultPredicate(final TypeAndElementSource tes) {
      super();
      this.tes = Objects.requireNonNull(tes, "tes");
    }

    @Override // Predicate
    public final boolean needsClientProxy(final BeanSelectionCriteria beanSelectionCriteria,
                                          final Id id,
                                          final Creation<?> c,
                                          final ReferenceSelector r) {
      final Scope governingScope =
        r.reference(new BeanSelectionCriteria(this.tes.declaredType(Scope.class), List.of(id.governingScopeId())),
                    null, // bean
                    cast(c));
      return governingScope != null && governingScope.normal();
    }

    public static final Bean<DefaultPredicate> bean(final TypeAndElementSource tes) {
      return
        new Bean<>(new Id(List.of(tes.declaredType(DefaultPredicate.class),
                                  tes.declaredType(Predicate.class)),
                          anyAndDefaultQualifiers(),
                          SINGLETON_ID),
                   new Singleton<>(new DefaultPredicate(tes)));
    }

  }

}
