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

/**
 * A {@link RuntimeException} thrown to indicate a problem with references.
 *
 * @author <a href="https://about.me/lairdnelson/" target="_top">Laird Nelson</a>
 */
public class ReferenceException extends RuntimeException {


  /*
   * Static fields.
   */


  /**
   * The version of this class for {@linkplain java.io.Serializable serialization purposes}.
   *
   * @see java.io.Serializable
   */
  private static final long serialVersionUID = 1L;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link ReferenceException}.
   *
   * @see #ReferenceException(String, Throwable)
   */
  public ReferenceException() {
    super();
  }

  /**
   * Creates a new {@link ReferenceException}.
   *
   * @param message a detail message; may be {@code null}
   *
   * @see #ReferenceException(String, Throwable)
   */
  public ReferenceException(final String message) {
    super(message);
  }

  /**
   * Creates a new {@link ReferenceException}.
   *
   * @param cause a {@link Throwable} that caused the creation of this {@link ReferenceException}; may be {@code null}
   *
   * @see #ReferenceException(String, Throwable)
   */
  public ReferenceException(final Throwable cause) {
    super(cause);
  }

  /**
   * Creates a new {@link ReferenceException}.
   *
   * @param message a detail message; may be {@code null}
   *
   * @param cause a {@link Throwable} that caused the creation of this {@link ReferenceException}; may be {@code null}
   *
   * @see Throwable#Throwable(String, Throwable)
   */
  public ReferenceException(final String message, final Throwable cause) {
    super(message, cause);
  }

}
