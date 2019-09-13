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
package com.uber.rxcentralble.core;

import android.bluetooth.BluetoothDevice;

import com.uber.rxcentralble.ParsedAdvertisement;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CoreParsedAdvertisementTest {

  /** EIR 0x01; EIR 0xFF; EIR 0x08; EIR 0x03; EIR 0x07. */
  private static final byte[] RAW_DATA = new byte[] {
      0x02,
      0x01,
      0x06,
      0x0E,
      (byte) 0xFF,
      0x15,
      0x04,
      0x00,
      0x00,
      0x00,
      0x01,
      0x41,
      0x41,
      0x45,
      0x4D,
      0x46,
      0x46,
      0x00,
      0x0B,
      0x08,
      0x55,
      0x62,
      0x65,
      0x72,
      0x42,
      0x65,
      0x61,
      0x63,
      0x6F,
      0x6E,
      0x03,
      0x03,
      0x0A,
      0x18,
      0x11,
      0x07,
      (byte) 0x97,
      0x4A,
      (byte) 0xA1,
      0x75,
      0x15,
      (byte) 0xC7,
      0x65,
      (byte) 0x81,
      0x36,
      0x4F,
      0x42,
      0x79,
      0x00,
      0x10,
      (byte) 0x97,
      (byte) 0xC7
  };

  /** EIR 0x01; EIR 0xFF; EIR 0x08; EIR 0x03; EIR 0x07. */
  private static final byte[] INVALID_RAW_DATA = new byte[] {
      0x02,
      0x01,
      0x06,
      0x02, // Invalid length!
      (byte) 0xFF,
      0x15,
      0x04,
      0x00,
      0x00,
      0x00,
      0x01,
      0x41,
      0x41,
      0x45,
      0x4D,
      0x46,
      0x46,
      0x00,
      0x0B,
      0x08,
      0x55,
      0x62,
      0x65,
      0x72,
      0x42,
      0x65,
      0x61,
      0x63,
      0x6F,
      0x6E,
      0x03,
      0x03,
      0x0A,
      0x18,
      0x11,
      0x07,
      (byte) 0x97,
      0x4A,
      (byte) 0xA1,
      0x75,
      0x15,
      (byte) 0xC7,
      0x65,
      (byte) 0x81,
      0x36,
      0x4F,
      0x42,
      0x79,
      0x00,
      0x10,
      (byte) 0x97,
      (byte) 0xC7
  };

  private static final String DEVICE_NAME = "UberBeacon";
  private static final int RSSI = 100;
  private static final int MFG_ID = 0x0415;
  private static final UUID SVC_UUID = UUID.fromString("c7971000-7942-4f36-8165-c71575a14a97");

  @Mock BluetoothDevice bluetoothDevice;

  private ParsedAdvertisement parsedAdvertisement;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void test() {
    parsedAdvertisement = new CoreParsedAdvertisement(RAW_DATA);
    assertEquals(DEVICE_NAME, parsedAdvertisement.getName());
    assertArrayEquals(
        Arrays.copyOfRange(RAW_DATA, 7, 18), parsedAdvertisement.getManufacturerData(MFG_ID));
    assertEquals(true, parsedAdvertisement.hasService(SVC_UUID));
  }

  @Test
  public void test_invalid_data() {
    parsedAdvertisement = new CoreParsedAdvertisement(INVALID_RAW_DATA);

    // Also asserts no exception was thrown.
    assertNull(parsedAdvertisement.getName());
  }
}
