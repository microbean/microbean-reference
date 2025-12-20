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
 * An interface whose implementations can register contextual instances for idempotent destruction at some later point.
 *
 * <p>This interface is often used by implementors of systems of dependent object destruction, and normally by no other
 * kinds of users.</p>
 *
 * <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see #register(Object, Destructor)
 */
// Needed by "dependent"/"none" scopes/lifecycle managers.
// Not used by "normal" factories etc.
//
// TODO: the package feels wrong, but stashing it in microbean-scopelet, alongside NoneScopelet, doesn't let
// microbean-reference's Request implement it
//
// TODO: maybe could move it to microbean-reference? microbean-scopelet depends on microbean-reference already?
public interface DestructorRegistry {

  /**
   * <dfn>Registers</dfn> the supplied contextual instance such that at some future moment, or perhaps not at all, the
   * supplied {@link Destructor} will be {@linkplain Destructor#destroy() run} to destroy it idempotently, and returns
   * {@code true} if and only if the registration was successful.
   *
   * @param instance a contextual instance; may be {@code null} in which case no action will be taken and {@code false}
   * will be returned
   *
   * @param destructor a {@link Destructor}; may be {@code null} in which case no action will be taken and {@code false}
   * will be returned
   *
   * @return {@code true} if and only if registration was successful; {@code false} otherwise
   *
   * @see Destructor
   */
  public boolean register(final Object instance, final Destructor destructor);

  /**
   * An interface indicating that an implementation is capable of <dfn>destroying</dfn> an object that it opaquely
   * references such that the destroyed object will no longer be suitable for use.
   *
   * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
   *
   * @see #destroy()
   *
   * @see DestructorRegistry#register(Object, Destructor)
   */
  @FunctionalInterface
  public static interface Destructor {

    /**
     * Destroys an object that this implementation opaquely references such that the destroyed object will no longer be
     * suitable for use.
     *
     * <p>Implementations of this method must be safe for concurrent use by multiple threads.</p>
     *
     * <p>Implementations of this method must be idempotent, performing no action if destruction of the implicit object
     * has already taken place.</p>
     *
     * @see DestructorRegistry#register(Object, Destructor)
     */
    public void destroy();

  }

}
