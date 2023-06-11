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

import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import java.util.function.Function;
import java.util.function.Supplier;

import org.microbean.bean.Bean;
import org.microbean.bean.Id;
import org.microbean.bean.Instances;
import org.microbean.bean.Selector;

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

  // (Most implementations that are aware of Scopes will need to use an Instances internally to find the Scope
  // corresponding to the supplied Id and to see if it is a normal (as opposed to pseudo-) scope.)
  @Override // ClientProxier
  public final boolean needsClientProxy(final Selector selector, final Id id, final Instances instances) {
    return this.tester.needsClientProxy(selector, id, instances);
  }

  @Override // ClientProxier
  @SuppressWarnings("unchecked")
  public <R> R clientProxy(final Selector selector, final Bean<R> bean, final Instances instances) {
    return
      ((Proxy<R>)this.cache.computeIfAbsent(bean.id(),
                                            x -> this.cpi.instantiate(this.cpcs.clientProxyClass(selector, bean, instances),
                                                                      () -> instances.supply(selector, bean))))
      .cast();
  }

  public static interface ClientProxyInstantiator {

    public default <R> Proxy<R> instantiate(final Class<? extends Proxy<R>> clientProxyClass,
                                            final Supplier<? extends R> supplier) {
      try {
        return clientProxyClass.getDeclaredConstructor(Supplier.class).newInstance(supplier);
      } catch (final ReflectiveOperationException e) {
        throw new ClientProxyInstantiationException(e.getMessage(), e);
      }
    }

  }

  public static interface ClientProxyClassSupplier {

    public <R> Class<? extends Proxy<R>> clientProxyClass(final Selector selector, final Bean<R> bean, final Instances instances);

  }

  public static final class GeneratingClientProxyClassSupplier implements ClientProxyClassSupplier {

    private final ClassNamer namer;

    private final Function<? super String, ? extends ClassLoader> classLoaderFunction;

    private final ClientProxyClassGenerator cg;

    public GeneratingClientProxyClassSupplier(final ClassNamer namer,
                                              final Function<? super String, ? extends ClassLoader> classLoaderFunction,
                                              final ClientProxyClassGenerator cg) {
      super();
      this.namer = Objects.requireNonNull(namer, "namer");
      this.classLoaderFunction = Objects.requireNonNull(classLoaderFunction, "classLoaderFunction");
      this.cg = Objects.requireNonNull(cg, "cg");
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <R> Class<? extends Proxy<R>> clientProxyClass(final Selector selector, final Bean<R> bean, final Instances instances) {
      final String className = this.namer.className(selector, bean.id());
      final ClassLoader cl = this.classLoaderFunction.apply(className);
      try {
        return (Class<? extends Proxy<R>>)Class.forName(className, false, cl);
      } catch (final ClassNotFoundException cnfe) {
        return this.cg.unloadedClientProxyClassDefinition(className, selector, bean, instances).load(cl);
      }
    }

    public static interface ClientProxyClassGenerator {

      public <R> UnloadedClientProxyClassDefinition<R> unloadedClientProxyClassDefinition(final String className, final Selector s, final Bean<R> b, final Instances i);

      @FunctionalInterface
      public static interface UnloadedClientProxyClassDefinition<R> {

        public Class<? extends Proxy<R>> load(final ClassLoader cl);

      }

    }

  }

  @FunctionalInterface
  public static interface Predicate {

    public boolean needsClientProxy(final Selector selector, final Id id, final Instances instances);

  }

}
