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

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.annotations.SchedulerSupport;

/** Scanner allows consumers to scan for Bluetooth LE Peripherals. */
public interface Scanner {

  /**
   * Scan for peripherals that match the provided ScanMatcher.
   *
   * <dl>
   *   <dt><b>Scheduler:</b>
   *   <dd>{@code connect} does not operate by default on a particular {@link Scheduler}.
   * </dl>
   *
   * @return Observable stream of discovered peripheral ScanDat or else an error.
   * {@link ConnectionError} will occur in cases where you canretry scanning
   */
  @SchedulerSupport(SchedulerSupport.NONE)
  Observable<ScanData> scan();

  /** Factory pattern to produce Scanner instances. */
  interface Factory {

    /**
     * Produce an appropriate Scanner for the current android version.
     *
     * @return a Scanner instance.
     */
    Scanner produce();
  }
}
