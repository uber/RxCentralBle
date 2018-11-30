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

import io.reactivex.Observable;

/** Provide the ability to observe Bluetooth LE capability and enabled state. */
public interface BluetoothDetector {

  /** Capability of the system to provide Bluetooth LE connectivity. */
  enum Capability {
    UNSUPPORTED,
    DISABLED,
    ENABLED
  }

  /**
   * Observe Bluetooth capability. If Bluetooth LE is unsupported, that will be the only emission.
   *
   * @return Observable stream of Bluetooth LE capability.
   */
  Observable<Capability> capability();

  /**
   * Observe Bluetooth enabled/disabled state. If Bluetooth LE is unsupported, a false value will be
   * the only emission.
   *
   * @return Observable stream of Bluetooth LE state.
   */
  Observable<Boolean> enabled();
}
