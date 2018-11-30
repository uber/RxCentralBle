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

/** Provides the capability to specify means to identify discovered peripheral matches. */
public interface ScanMatcher {

  /**
   * Determines if the ScanData for a discovered peripheral is a match against parameters and logic
   * specified by this ScanMatcher.
   *
   * @param scanData the ScanData for a discovered peripheral.
   * @return true if it's a match, else false.
   */
  boolean match(ScanData scanData);
}
