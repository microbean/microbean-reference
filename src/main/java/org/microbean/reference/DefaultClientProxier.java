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

import org.microbean.bean.Bean;
import org.microbean.bean.Id;
import org.microbean.bean.Instances;
import org.microbean.bean.Selector;

public class DefaultClientProxier implements ClientProxier {

  private final ConcurrentMap<Id, Proxy<?>> cache;

  private final Predicate tester;

  private final Factory factory;

  public DefaultClientProxier(final Predicate tester,
                              final Map<? extends Id, ? extends Proxy<?>> precomputedProxies,
                              final Factory factory) {
    super();
    this.cache = new ConcurrentHashMap<>(precomputedProxies);
    this.tester = Objects.requireNonNull(tester, "tester");
    this.factory = Objects.requireNonNull(factory, "factory");
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
    return ((Proxy<R>)this.cache.computeIfAbsent(bean.id(), x -> this.factory.create(selector, bean, instances))).cast();
  }

  @FunctionalInterface
  public static interface Factory {

    public <R> Proxy<R> create(final Selector selector, final Bean<R> bean, final Instances instances);

  }

  @FunctionalInterface
  public static interface Predicate {

    public boolean needsClientProxy(final Selector selector, final Id id, final Instances instances);
    
  }

}
