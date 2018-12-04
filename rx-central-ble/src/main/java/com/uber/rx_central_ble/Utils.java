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

/** Useful utilities. */
public final class Utils {

  private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

  private Utils() { }

  /**
   * Return a string representation of a byte array.
   *
   * @param bytes bytes to convert to string.
   * @return string of hex characters.
   */
  public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];

    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }

    return new String(hexChars);
  }

  /**
   * Construct a 128bit BLE UUID (xxxxxxxx-0000-1000-8000-00805f9b34fb) from either 16 or 32 bit
   * UUID.
   *
   * @param value 16 or 32bit value of the UUID
   * @return 128bit BLE UUID
   */
  public static UUID uuidFromInteger(int value) {
    final long msb = 0x0000000000001000L;
    final long lsb = 0x800000805f9b34fbL;

    long v = value & 0xFFFFFFFF;

    return new UUID(msb | (v << 32), lsb);
  }
}
