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

/**
 * A hierarchical {@link DestructorRegistry} that is {@link AutoCloseable}.
 *
 * <p>This interface is often used by {@link org.microbean.bean.ReferencesSelector} implementors, and normally by no
 * other kinds of users.</p>
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see #close()
 *
 * @see DestructorRegistry
 */
// Needed and used only by ReferencesSelector implementations.
//
// TODO: maybe could move it to microbean-reference? microbean-scopelet depends on microbean-reference already?
public interface DestructorTree extends AutoCloseable, DestructorRegistry {

  /**
   * Creates a new <dfn>child</dfn> instance of this implementation, or a subtype, {@linkplain #register(Object,
   * Destructor) registers it} with this implementation, using a method reference to its {@link #close()} method as the
   * {@link Destructor}, and returns it.
   *
   * @return a new (non-{@code null}) child instance of this implementation, or a subtype, {@linkplain #register(Object,
   * Destructor) registered} with this implementation such that {@link #close() closing} this implementation will also
   * {@linkplain #close() close} the child instance
   *
   * @see #close()
   *
   * @see #register(Object, Destructor)
   */
  public DestructorTree newChild();

  /**
   * Closes this {@link DestructorTree} implementation by effectively {@linkplain #remove(Object) removing} all
   * {@linkplain #register(Object, Destructor) registered} contextual instances and {@linkplain Destructor#destroy()
   * running their affiliated <code>Destructor</code>s}.
   *
   * @see #remove(Object)
   */
  @Override // AutoCloseable
  public void close();

  /**
   * Removes the supplied contextual instance and the {@link Destructor} that was {@linkplain #register(Object,
   * Destructor) registered with it}.
   *
   * <p>The {@link Destructor#destroy()} will not be invoked by implementations of this method.</p>
   *
   * @param instance the contextual instance to remove; may be {@code null} in which case {@code null} will be returned
   *
   * @return a {@link Destructor} {@linkplain #register(Object, Destructor) registered} with the supplied instance, or
   * {@code null} if no such {@link Destructor} exists
   */
  public Destructor remove(final Object instance);

}
