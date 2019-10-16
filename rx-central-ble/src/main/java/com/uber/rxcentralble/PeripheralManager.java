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

import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.annotations.SchedulerSupport;

/** Manages communication with a connected Peripheral. */
public interface PeripheralManager {

  /**
   * Set the underlying Peripheral interface the PeripheralManager will communicate with. May be set multiple
   * times as connection cycles occur.
   *
   * <p>Triggers execution of queued operations the first time the Peripheral is set.
   *
   * @param peripheral the Peripheral interface used to communicate with the peripheral.
   */
  void setPeripheral(Peripheral peripheral);

  /**
   * Queue a GATT operation to execute against the Peripheral supplied to the PeripheralManager.
   *
   * <p>Operations will be executed in a serial fashion in the order they are subscribed to; the
   * operation will not execute nor be queued without an active subscription to the Observable
   * returned by this method.
   *
   * <p>The operation will execute immediately upon subscription if there are no active or queued
   * operations.
   *
   * <p>The underlying operation queue runs regardless off peripheral connection; if a disconnection
   * occurs, the queue will continue to execute operations in a serial fashion.
   *
   * <p>Supports Reactive retry operators.
   *
   * <dl>
   *   <dt><b>Scheduler:</b>
   *   <dd>{@code queueOperation} does not operate by default on a particular {@link Scheduler}.
   * </dl>
   *
   * @param peripheralOperation the operation to queue and execute.
   * @param <T> the type of object returned by a successfully executed operation.
   * @return Single result of the operation; subscribe to queue and execute the PeripheralOperation.
   */
  @SchedulerSupport(SchedulerSupport.NONE)
  <T> Single<T> queueOperation(PeripheralOperation<T> peripheralOperation);

  /**
   * Observable stream of connection state.
   *
   * @return emits true if the PeripheralManager is connected to a peripheral, else false.
   */
  Observable<Boolean> connected();

  /**
   * Observe characteristic notifications from the underlying Peripheral.
   *
   * <p>Subscriptions to Observables returned by this method remain active across connection cycles;
   * there is no need to re-subscribe to notifications upon reconnection.
   *
   * @param characteristic the UUID of the characteristic we want to observe notifications from.
   * @return bytes, potentially processed, resulting from notifications from the characteristic.
   */
  Observable<byte[]> notification(UUID characteristic);
}
