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
 * A PeripheralError is thrown whenever an issue occurs that prevents function of an operation on {@link
 * android.bluetooth.BluetoothGatt} and accompanying {@link
 * android.bluetooth.BluetoothGattCallback}.
 *
 * <p>Check the {@code cause} to see if there is a root cause for this error.
 */
public class PeripheralError extends Exception {

  /**
   * Represents that a call to initiate an operation on {@link android.bluetooth.BluetoothGatt}
   * returned false.
   */
  public static final int ERROR_STATUS_CALL_FAILED = -1;

  /** Code for the error; enumerates specific error conditions. */
  public enum Code {
    DISCONNECTED,
    CONNECTION_FAILED,
    CONNECTION_LOST,
    SERVICE_DISCOVERY_FAILED,
    READ_CHARACTERISTIC_FAILED,
    WRITE_CHARACTERISTIC_FAILED,
    REGISTER_NOTIFICATION_FAILED,
    UNREGISTER_NOTIFICATION_FAILED,
    SET_CHARACTERISTIC_NOTIFICATION_MISSING_PROPERTY,
    SET_CHARACTERISTIC_NOTIFICATION_FAILED,
    SET_CHARACTERISTIC_NOTIFICATION_CCCD_MISSING,
    WRITE_DESCRIPTOR_FAILED,
    REQUEST_MTU_FAILED,
    READ_RSSI_FAILED,
    OPERATION_IN_PROGRESS,
    OPERATION_RESULT_MISMATCH,
    MISSING_CHARACTERISTIC,
    MINIMUM_SDK_UNSUPPORTED,
    CHARACTERISTIC_SET_VALUE_FAILED,
  }

  private final Code code;
  private final int errorStatus;

  public PeripheralError(Code code) {
    this(code, 0);
  }

  public PeripheralError(Code code, int errorStatus) {
    this(code, errorStatus, null);
  }

  public PeripheralError(Code code, @Nullable Throwable cause) {
    this(code, 0, cause);
  }

  public PeripheralError(Code code, int errorStatus, @Nullable Throwable cause) {
    super(cause);
    this.code = code;
    this.errorStatus = errorStatus;
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

  /**
   * Get the error status. The status is result of operating on {@link
   * android.bluetooth.BluetoothGatt}
   *
   * @return the error status.
   */
  public int getErrorStatus() {
    return errorStatus;
  }
}
