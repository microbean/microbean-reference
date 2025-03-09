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

import java.util.function.Supplier;

import org.microbean.bean.Request;

/**
 * A factory for {@link Supplier}s of contextual instances.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see #supplier(Request)
 */
public interface Instances extends AutoCloseable, InstanceRemover {

  @Override // AutoCloseable
  public default void close() {

  }

  /**
   * Returns {@code true} if and only if the supplied {@link Request} should result in a contextual reference that is a
   * <dfn>client proxy</dfn>.
   *
   * @param request a {@link Request}; may be {@code null}
   *
   * @return {@code true} if and only if the supplied {@link Request} should result in a contextual reference that is a
   * <dfn>client proxy</dfn>
   */
  public boolean proxiable(final Request<?> request);

  /**
   * Returns a {@link Supplier} of contextual instances appropriate for the given {@link Request}.
   *
   * @param <I> the type of the contextual instance being requested
   *
   * @param request a {@link Request}; may be {@code null}
   *
   * @return a non-{@code null} {@link Supplier} of contextual instances of the appropriate type
   */
  public <I> Supplier<? extends I> supplier(final Request<I> request);

}
