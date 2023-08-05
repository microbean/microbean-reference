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
package org.microbean.reference2;

import org.microbean.bean2.Bean;
import org.microbean.bean2.BeanSet;
import org.microbean.bean2.Creation;
import org.microbean.bean2.Id;
import org.microbean.bean2.References;
import org.microbean.bean2.Selector;

public interface Instances {

  public BeanSet beanSet();

  public <I> I instance(final Selector selector,
                        final Bean<I> bean,
                        final Creation<I> creation,
                        final References<?> references);

  public boolean remove(final Id id);
  
}
