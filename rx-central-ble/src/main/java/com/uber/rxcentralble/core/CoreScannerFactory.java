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

import android.os.Build;

import com.uber.rxcentralble.Scanner;
import com.uber.rxcentralble.core.scanners.JellyBeanScanner;
import com.uber.rxcentralble.core.scanners.LollipopScanner;

/** Core implementation of Scanner.Factory. */
public class CoreScannerFactory implements Scanner.Factory {

  @Override
  public Scanner produce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return new JellyBeanScanner();
    } else {
      return new LollipopScanner();
    }
  }
}
