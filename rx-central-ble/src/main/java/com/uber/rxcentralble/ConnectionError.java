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

import android.support.annotation.Nullable;

/**
 * A ConnectionError is thrown whenever an issue occurs that prevents connection to a peripheral.
 *
 * <p>Check the {@code cause} to see if there is a root cause for this error.
 */
public class ConnectionError extends Exception {

  /** Code for the error; enumerates specific error conditions. */
  public enum Code {
    SCAN_IN_PROGRESS,
    SCAN_FAILED,
    SCAN_TIMEOUT,
    CONNECTION_IN_PROGRESS,
    CONNECT_FAILED,
    CONNECT_TIMEOUT,
    CONNECTION_FAILED,
    DISCONNECTION,
  }

  private final Code code;

  public ConnectionError(Code code) {
    this(code, null);
  }

  public ConnectionError(Code code, @Nullable Throwable cause) {
    super(cause);
    this.code = code;
  }

  @Override
  public String getMessage() {
    return code.toString();
  }

  /**
   * Get the error code.
   *
   * @return the error code.
   */
  public Code getCode() {
    return code;
  }
}
