/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023–2025 microBean™.
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

import java.util.function.Supplier;

import org.microbean.bean.Request;

/**
 * A supplier of {@link ClientProxy} instances.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see #clientProxy(Request, Supplier)
 */
@FunctionalInterface
public interface ClientProxier {

  /**
   * Returns a <dfn>contextual reference</dfn> which is also a {@link ClientProxy}, given a
   * {@link Supplier} of <dfn>contextual instances</dfn> of the appropriate type.
   *
   * <p>Implementations of this method may return {@code null}.</p>
   *
   * @param <R> the type of the contextual reference
   *
   * @param r the {@link Request} necessitating this invocation; must not be {@code null}
   *
   * @param instanceSupplier a {@link Supplier} of contextual instances of the appropriate type; must not be {@code null}
   *
   * @return a contextual reference, which may be {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @see ClientProxy
   */
  public <R> R clientProxy(final Request<R> r, final Supplier<? extends R> instanceSupplier);

}
