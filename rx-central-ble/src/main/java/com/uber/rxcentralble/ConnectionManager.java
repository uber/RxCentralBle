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

import android.support.annotation.IntRange;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.annotations.SchedulerSupport;

/** Implementers provide the ability to scan and connect to a Bluetooth LE peripheral. */
public interface ConnectionManager {

  /** Constant value for the default scan timeout, in milliseconds. */
  int DEFAULT_SCAN_TIMEOUT = 45000;

  /** Constant value for the default timeout during connection, in milliseconds. */
  int DEFAULT_CONNECTION_TIMEOUT = 30000;

  /** State of the ConnectionManager. */
  enum State {
    DISCONNECTED,
    DISCONNECTED_WITH_ERROR,
    SCANNING,
    CONNECTING,
    CONNECTED
  }

  /**
   * Scan and connect to a Bluetooth LE peripheral whose advertisement package matches the provided
   * {@link ScanMatcher} . Initiate the operation by subscribing to the returned Observable.
   *
   * <p>{@code connect} must allow for usage of reactive Retry operators.
   *
   * <p>{@code connect} is idempotent if called consecutively with equal {@link ScanMatcher}
   * instances. If a subscriber already has an active subscription to connect(), and a call is made
   * with an inequal {@link ScanMatcher}, just a Single exception will be returned.
   *
   * <dl>
   *   <dt><b>Scheduler:</b>
   *   <dd>{@code connect} does not operate by default on a particular {@link Scheduler}.
   * </dl>
   *
   * @param scanMatcher dictates the logic used to match a peripheral for connection. The {@link
   *     ConnectionManager} will connect to the first discovered peripheral that is a match.
   * @param scanTimeoutMs scan timeout in milliseconds. If a timeout occurs, a {@link
   *     ConnectionError} will be thrown with SCAN_TIMEOUT code.  Maximum timeout should be 29
   *     minutes due to scan throttling in Android 7+
   * @param connectionTimeoutMs connection timeout in milliseconds. This is defined by the period
   *     between matching a peripheral for connection and establishing a connection. If a timeout
   *     occurs, a {@link ConnectionError} with CONNECT_TIMEOUT code.  Must be positive.
   * @return Observable stream of connected GattIO. In event of an error, expect {@link
   *     ConnectionError} for errors that may be retried for a new connection attempt.
   */
  @SchedulerSupport(SchedulerSupport.NONE)
  Observable<GattIO> connect(ScanMatcher scanMatcher,
                             @IntRange(from = 0, to = 1740000) int scanTimeoutMs,
                             @IntRange(from = 0) int connectionTimeoutMs);

  /**
   * Observe the internal state of the {@link ConnectionManager}.
   *
   * @return Observable stream of the {@link ConnectionManager} internal state.
   */
  Observable<State> state();
}
