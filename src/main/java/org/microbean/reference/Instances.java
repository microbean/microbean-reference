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

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.microbean.bean.Bean;
import org.microbean.bean.Id;
import org.microbean.bean.Creation;
import org.microbean.bean.ReferencesSelector;

/**
 * A factory for {@link Supplier}s of contextual instances.
 *
 * <p>{@link Instances} instances are used by {@link Request} instances.</p>
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see #supplier(Bean, Creation)
 *
 * @see Request
 */
public interface Instances {

  /**
   * Returns {@code true} if and only if it is possible for a client proxy to be created for contextual instances
   * described by the supplied {@link Id}.
   *
   * @param id an {@link Id}; must not be {@code null}
   *
   * @return {@code true} if and only if it is possible for a client proxy to be created for contextual instances
   * described by the supplied {@link Id}; {@code false} otherwise
   *
   * @exception NullPointerException if {@code id} is {@code null}
   */
  public boolean proxiable(final Id id);

  /**
   * Returns a {@link Supplier} of contextual instances appropriate for the given {@link Creation}.
   *
   * @param <I> the type of the contextual instance being requested
   *
   * @param bean the {@link Bean} whose {@link Bean#factory() Factory} will create any contextual instances; must not be
   * {@code null}
   *
   * @param creation a {@link Creation}; may be {@code null}
   *
   * @return a non-{@code null} {@link Supplier} of contextual instances of the appropriate type
   */
  public <I> Supplier<? extends I> supplier(final Bean<I> bean, final Creation<I> creation);

}
