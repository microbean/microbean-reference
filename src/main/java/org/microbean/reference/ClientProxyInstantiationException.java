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

/**
 * A {@link ReferenceException} indicating that a client proxy class could not be instantiated.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public class ClientProxyInstantiationException extends ReferenceException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a new {@link ClientProxyInstantiationException}.
   */
  public ClientProxyInstantiationException() {
    super();
  }

  /**
   * Creates a new {@link ClientProxyInstantiationException}.
   *
   * @param message a detail message; may be {@code null}
   */
  public ClientProxyInstantiationException(final String message) {
    super(message);
  }

  /**
   * Creates a new {@link ClientProxyInstantiationException}.
   *
   * @param cause a {@link Throwable} causing this {@link ClientProxyInstantiationException} to be created; may be
   * {@code null}
   */
  public ClientProxyInstantiationException(final Throwable cause) {
    super(cause);
  }

  /**
   * Creates a new {@link ClientProxyInstantiationException}.
   *
   * @param message a detail message; may be {@code null}
   *
   * @param cause a {@link Throwable} causing this {@link ClientProxyInstantiationException} to be created; may be
   * {@code null}
   */
  public ClientProxyInstantiationException(final String message,
                                           final Throwable cause) {
    super(message, cause);
  }

}
