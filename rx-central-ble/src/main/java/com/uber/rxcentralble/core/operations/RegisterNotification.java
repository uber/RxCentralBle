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

import androidx.annotation.Nullable;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.jakewharton.rxrelay2.Relay;
import com.uber.rxcentralble.Peripheral;
import com.uber.rxcentralble.PeripheralOperation;
import com.uber.rxcentralble.Irrelevant;
import com.uber.rxcentralble.Optional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;

/** Register for characteristic notifications. */
public class RegisterNotification implements PeripheralOperation<Irrelevant> {

  private final Relay<Optional<Peripheral>> peripheralRelay = BehaviorRelay.createDefault(Optional.empty());
  private final Single<Irrelevant> resultSingle;

  public RegisterNotification(UUID svc, UUID chr, int timeoutMs) {
    this(svc, chr, null, timeoutMs);
  }

  public RegisterNotification(
          UUID svc, UUID chr, @Nullable Peripheral.Preprocessor preprocessor, int timeoutMs) {
    resultSingle =
        peripheralRelay
            .filter(Optional::isPresent)
            .map(Optional::get)
            .firstOrError()
            .doOnSuccess(g -> peripheralRelay.accept(Optional.empty()))
            .flatMap(
                peripheral -> peripheral
                        .registerNotification(svc, chr, preprocessor)
                        .andThen(Single.just(Irrelevant.INSTANCE)))
            .toObservable()
            .share()
            .firstOrError()
            .timeout(timeoutMs, TimeUnit.MILLISECONDS);
  }

  @Override
  public Single<Irrelevant> result() {
    return resultSingle;
  }

  @Override
  public void execute(Peripheral peripheral) {
    peripheralRelay.accept(Optional.of(peripheral));
  }

  @Override
  public Single<Irrelevant> executeWithResult(Peripheral peripheral) {
    return resultSingle
            .doOnSubscribe(disposable -> execute(peripheral));
  }
}
