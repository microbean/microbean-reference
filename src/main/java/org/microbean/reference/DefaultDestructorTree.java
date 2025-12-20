/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023–2025 microBean™.
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

import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.microbean.reference.DestructorRegistry.Destructor;

/**
 * A straightforward {@link DestructorTree} implementation.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see DestructorTree
 */
public class DefaultDestructorTree implements DestructorTree {


  /*
   * Instance fields.
   */


  private final Lock lock;

  // @GuardedBy("lock")
  private Map<Object, Destructor> destructors; // identity hashmap when open for business


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link DefaultDestructorTree}.
   */
  public DefaultDestructorTree() {
    super();
    this.lock = new ReentrantLock();
  }


  /*
   * Instance methods.
   */


  /*
   * Returns a new {@link DefaultDestructorTree} instance that is not {@linkplain #close() closed}, has no {@linkplain
   * #register(Object, Destructor) registrations} yet, and is itself {@linkplain #register(Object, Destructor) registered}
   * as a destructor with this {@link DefaultDestructorTree}.
   *
   * @return a new, {@linkplain #close() unclosed} {@link DefaultDestructorTree} {@linkplain #register(Object, Destructor)
   * registered} as a destructor with this {@link DefaultDestructorTree}
   *
   * @exception IllegalStateException if this {@link DefaultDestructorTree} is {@linkplain #close() closed}
   *
   * @microbean.nullability This method does not, and its overrides must not, return {@code null}.
   *
   * @microbean.idempotency Overrides of this method must return new, distinct {@link DefaultDestructorTree} instances.
   *
   * @microbean.threadsafety This method is, and its overrides must be, safe for concurrent use by multiple threads.
   *
   * @see #register(Object, Destructor)
   *
   * @see #close()
   */
  @Override // DestructorTree
  public DefaultDestructorTree newChild() {
    final DefaultDestructorTree child = new DefaultDestructorTree();
    if (!this.register(child, child::close)) { // CRITICAL
      throw new IllegalStateException();
    }
    return child;
  }

  /**
   * Closes this {@link DefaultDestructorTree} and destroys its {@linkplain #register(Object, Destructor) registrants}
   * by {@linkplain Destructor#destroy() running} their destructors {@linkplain #register(Object, Destructor) supplied
   * at registration time}.
   *
   * <p>{@link Destructor#destroy()} is called on all {@linkplain #register(Object, Destructor) registrants}, even in the
   * presence of exceptions. {@link RuntimeException}s consequently thrown may {@linkplain Throwable#getSuppressed()
   * contain suppressed exceptions}.</p>
   *
   * <p>Overrides of this method wishing to add semantics to this behavior should perform that work before calling
   * {@link #close() super.close()}.</p>
   *
   * <p>Overrides of this method must call {@link #close() super.close()} or undefined behavior may result.</p>
   *
   * <p>After any successful invocation of this method, this {@link DefaultDestructorTree} is deemed to be
   * irrevocably closed. Invoking this method again will have no effect.</p>
   *
   * @microbean.idempotency This method is, and its overrides must be, idempotent.
   *
   * @microbean.threadsafety This method is, and its overrides must be, safe for concurrent use by multiple threads.
   */
  @Override // DestructorTree
  public void close() {
    Map<Object, Destructor> destructors;
    lock.lock();
    try {
      destructors = this.destructors;
      if (destructors == Map.<Object, Destructor>of()) {
        // Already closed
        return;
      }
      this.destructors = Map.of();
    } finally {
      lock.unlock();
    }

    if (destructors == null) {
      // nothing to do
      return;
    }

    RuntimeException re = null;
    for (final Destructor d : destructors.values()) {
      try {
        d.destroy();
      } catch (final RuntimeException e) {
        if (re == null) {
          re = e;
        } else {
          re.addSuppressed(e);
        }
      }
    }

    if (re != null) {
      throw re;
    }
  }

  /**
   * If this {@link DefaultDestructorTree} is not closed, and if the supplied {@code reference} has not yet been
   * registered, registers it such that it will be destroyed by the supplied {@code destructor} when this {@link
   * DefaultDestructorTree} is {@linkplain #close() closed}, and returns {@code true}.
   *
   * <p>This method takes no action and returns {@code false} in all other cases.</p>
   *
   * @param reference a contextual reference that will be destroyed later; if {@code null} then no action will be taken
   * and {@code false} will be returned
   *
   * @param destructor a {@link Destructor} that, when {@linkplain Destructor#destroy() run}, will destroy the supplied
   * {@code reference} in some way; if {@code null} then no action will be taken and {@code false} will be returned; if
   * non-{@code null} <strong>must be idempotent and safe for concurrent use by multiple threads</strong>
   *
   * @return {@code true} if and only if this {@link DefaultDestructorTree} is not closed, and the supplied {@code
   * reference} is not already registered and registration completed successfully; {@code false} in all other cases
   *
   * @microbean.idempotency This method is idempotent.
   *
   * @microbean.threadsafety This method is safe for concurrent use by multiple threads.
   */
  @Override // DestructorRegistry
  public final boolean register(final Object reference, final Destructor destructor) {
    if (reference == null || destructor == null) {
      return false;
    }
    lock.lock();
    try {
      if (this.destructors == null) {
        this.destructors = new IdentityHashMap<>(); // critical that this is an IdentityHashMap
      } else if (this.destructors == Map.<Object, Destructor>of()) {
        // Already closed; register must therefore be a no-op.
        return false;
      }
      return this.destructors.putIfAbsent(reference, destructor) == null;
    } finally {
      lock.unlock();
    }
  }

  @Override // DestructorTree
  public final Destructor remove(final Object reference) {
    if (reference == null) {
      return null;
    }
    lock.lock();
    try {
      return this.destructors == null ? null : this.destructors.remove(reference);
    } finally {
      lock.unlock();
    }
  }

}
