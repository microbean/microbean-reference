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

import org.microbean.bean.Bean;
import org.microbean.bean.Creation;
import org.microbean.bean.Id;
import org.microbean.bean.References;
import org.microbean.bean.Selector;

public interface ClientProxier {

  public boolean needsClientProxy(final Selector selector,
                                  final Id id,
                                  final Creation<?> c,
                                  final References<?> r);
  
  public <R> R clientProxy(final Selector selector,
                           final Bean<R> bean, // maybe just Id? 
                           final Creation<R> c,
                           final References<?> r,
                           final Instances instances);
  
}
