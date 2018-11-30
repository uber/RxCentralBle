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
package com.uber.rx_central_ble;

import io.reactivex.Single;

/**
 * An operation to perform against a peripheral. Meant to be queued on a GattManager implementation
 * for serial execution.
 *
 * @param <T> the type of data returned by this successful operation.
 */
public interface GattOperation<T> {

  /**
   * A Single emission for the result of execution.
   *
   * <p>Subscriptions to the result stream must be active prior to execution; execution performs a
   * publish of result onto the result stream.
   *
   * @return Single of type T on successful execution, or else an error.
   */
  Single<T> result();

  /**
   * Execute the operation. There must be an active subscription to the result stream for the
   * operation to execute.
   *
   * @param gattIO the GattIO to execute the operation against.
   */
  void execute(GattIO gattIO);

  /**
   * Execute the operation upon subscription to returned result stream.
   *
   * <p>This allows for one-line execution and result subscription.
   *
   * @param gattIO the GattIO to execute the operation against.
   * @return the Single result.
   */
  Single<T> executeWithResult(GattIO gattIO);
}
