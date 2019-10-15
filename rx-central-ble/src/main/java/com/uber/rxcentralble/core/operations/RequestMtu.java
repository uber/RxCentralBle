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
package com.uber.rxcentralble.core.operations;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.jakewharton.rxrelay2.Relay;
import com.uber.rxcentralble.Peripheral;
import com.uber.rxcentralble.PeripheralOperation;
import com.uber.rxcentralble.Optional;

import java.util.concurrent.TimeUnit;

import io.reactivex.Single;

/**
 * Request to negotiate a new MTU (Maximum Transmission Unit) with a peripheral. The theoretical max
 * MTU is 512. Peripheral maxWriteLength() will reflect the most recent negotiated MTU size.
 */
public class RequestMtu implements PeripheralOperation<Integer> {

  private final Relay<Optional<Peripheral>> peripheralRelay = BehaviorRelay.createDefault(Optional.empty());
  private final Single<Integer> resultSingle;

  public RequestMtu(int requestedMtu, int timeoutMs) {
    resultSingle =
        peripheralRelay
            .filter(Optional::isPresent)
            .map(Optional::get)
            .firstOrError()
            .doOnSuccess(g -> peripheralRelay.accept(Optional.empty()))
            .flatMap(peripheral -> peripheral.requestMtu(requestedMtu))
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
  public void execute(Peripheral peripheral) {
    peripheralRelay.accept(Optional.of(peripheral));
  }

  @Override
  public Single<Integer> executeWithResult(Peripheral peripheral) {
    return resultSingle
            .doOnSubscribe(disposable -> execute(peripheral));
  }
}
