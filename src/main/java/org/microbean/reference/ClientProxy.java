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

/**
 * An interface whose implementations pretend to be another type and alter the behavior of instances of that type in
 * some way.
 *
 * <p>An implementation of this interface must, somehow, be able to be {@linkplain #$cast() cast} to the type of the
 * instance it logically proxies.</p>
 *
 * @param <T> the proxied type
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see #$proxied()
 *
 * @see #$cast()
 */
// TODO: this interface may move to another module or package.
public interface ClientProxy<T> {

  /**
   * Returns the instance being proxied, not this {@link ClientProxy}.
   *
   * @return the instance being proxied; never {@code null}; never {@code this}
   *
   * @microbean.nullability Implementations of this method must never return {@code null}.
   *
   * @microbean.idempotency Implementations of this method must be idempotent and deterministic. Specifically, they must
   * always return the instance being proxied, not {@code this} or anything else.
   *
   * @microbean.threadsafety Implementations of this method must be safe for concurrent use by multiple threads.
   */
  public T $proxied();

  /**
   * Returns this {@link ClientProxy}, or a completely substitutable replacement for it, cast to the type of the
   * instance it proxies.
   *
   * <p>The default implementation of this method returns, effectively, {@code (T)this}.</p>
   *
   * @return this {@link ClientProxy}; never {@code null}
   *
   * @exception ClassCastException if the cast could not take place for any reason; this indicates a violation of this
   * interface's structural contracts by the implementation class
   *
   * @microbean.nullability Implementations of this method must never return {@code null}.
   *
   * @microbean.idempotency Implementations of this method must be idempotent and deterministic. Specifically, they must
   * always return this {@link ClientProxy} or a completely substitutable replacement for it, not anything else.
   *
   * @microbean.threadsafety Implementations of this method must be safe for concurrent use by multiple threads.
   */
  @SuppressWarnings("unchecked")
  public default T $cast() {
    return (T)this;
  }

}
