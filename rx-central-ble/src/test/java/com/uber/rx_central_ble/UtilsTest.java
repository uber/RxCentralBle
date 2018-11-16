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

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class UtilsTest {

  @Test
  public void bytesToHex() {
    byte[] bytes =
        new byte[] {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF};
    assertEquals("0123456789abcdef", Utils.bytesToHex(bytes));
  }

  @Test
  public void uuidFromInteger() {
    assertEquals(
        UUID.fromString("c7971000-0000-1000-8000-00805f9b34fb"), Utils.uuidFromInteger(0xC7971000));
  }
}
