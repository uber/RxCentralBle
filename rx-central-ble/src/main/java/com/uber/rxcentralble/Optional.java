/**
 *  Copyright (c) 2018 Uber Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.uber.rxcentralble;

import java.util.Objects;

/** Simple optional implementation to avoid usage of nulls in reactive streams. **/
public class Optional<T> {

  private T value;

  private Optional() {
    this.value = null;
  }

  private Optional(T value) {
    this.value = Objects.requireNonNull(value);
  }

  public static <T> Optional<T> empty() {
    return new Optional<>();
  }

  public static <T> Optional<T> of(T value) {
    return new Optional<>(value);
  }

  public boolean isPresent() {
    return value != null;
  }

  public T get() {
    return value;
  }
}
