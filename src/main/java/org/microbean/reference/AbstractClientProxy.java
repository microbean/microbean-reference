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

import java.util.Objects;

import java.util.function.Supplier;

/**
 * A skeletal implementation of the {@link ClientProxy} interface.
 *
 * @param <I> the type of contextual instance the implementation proxies
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public abstract class AbstractClientProxy<I> implements ClientProxy<I> {

  private final Supplier<? extends I> s;

  /**
   * Creates a new {@link AbstractClientProxy}.
   *
   * @param s a {@link Supplier} of contextual instances; must not be {@code null}
   *
   * @exception NullPointerException if {@code s} is {@code null}
   */
  protected AbstractClientProxy(final Supplier<? extends I> s) {
    super();
    this.s = Objects.requireNonNull(s, "s");
  }

  @Override // ClientProxy<I>
  public final I $proxied() {
    return this.s.get(); // yes, each time
  }

}
