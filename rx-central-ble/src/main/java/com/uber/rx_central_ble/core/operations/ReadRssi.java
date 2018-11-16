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
package com.uber.rx_central_ble.core.operations;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.jakewharton.rxrelay2.Relay;
import com.uber.rx_central_ble.GattIO;
import com.uber.rx_central_ble.GattOperation;
import com.uber.rx_central_ble.Optional;

import java.util.concurrent.TimeUnit;

import io.reactivex.Single;

/** Read the relative signal strength for the peripheral. */
public class ReadRssi implements GattOperation<Integer> {

  private final Relay<Optional<GattIO>> gattRelay = BehaviorRelay.createDefault(Optional.empty());
  private final Single<Integer> resultSingle;

  public ReadRssi(int timeoutMs) {
    resultSingle =
        gattRelay
            .filter(Optional::isPresent)
            .map(Optional::get)
            .firstOrError()
            .doOnSuccess(g -> gattRelay.accept(Optional.empty()))
            .flatMap(GattIO::readRssi)
            .toObservable()
            .share()
            .firstOrError()
            .timeout(timeoutMs, TimeUnit.MILLISECONDS);
  }

  @Override
  public final Single<Integer> result() {
    return resultSingle;
  }

  @Override
  public void execute(GattIO gattIO) {
    gattRelay.accept(Optional.of(gattIO));
  }

  @Override
  public Single<Integer> executeWithResult(GattIO gattIO) {
    return resultSingle
            .doOnSubscribe(disposable -> execute(gattIO));
  }
}
