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

import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.annotations.SchedulerSupport;

/** Manages communication with the GATT interface exposed by a peripheral. */
public interface GattManager {

  /**
   * Set the underlying GattIO interface the GattManager will communicate with. May be set multiple
   * times as connection cycles occur.
   *
   * <p>Triggers execution of queued operations the first time the GattIO is set.
   *
   * @param gattIO the GattIO interface used to communicate with the peripheral.
   */
  void setGattIO(GattIO gattIO);

  /**
   * Queue a GATT operation to execute against the peripheral represented by the latest GattIO
   * supplied to the GattManager.
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
   * @param gattOperation the operation to queue and execute.
   * @param <T> the type of object returned by a successfully executed operation.
   * @return Single result of the operation; subscribe to queue and execute the GattOperation.
   */
  @SchedulerSupport(SchedulerSupport.NONE)
  <T> Single<T> queueOperation(GattOperation<T> gattOperation);

  /**
   * Observable stream of connection state.
   *
   * @return emits true if the GattManager is connected to a peripheral, else false.
   */
  Observable<Boolean> connected();

  /**
   * Observe characteristic notifications from the underlying GattIO.
   *
   * <p>Subscriptions to Observables returned by this method remain active across connection cycles;
   * there is no need to re-subscribe to notifications upon reconnection.
   *
   * @param characteristic the UUID of the characteristic we want to observe notifications from.
   * @return bytes, potentially processed, resulting from notifications from the characteristic.
   */
  Observable<byte[]> notification(UUID characteristic);
}
