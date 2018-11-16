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

import android.support.annotation.Nullable;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.jakewharton.rxrelay2.Relay;
import com.uber.rx_central_ble.GattIO;
import com.uber.rx_central_ble.GattOperation;
import com.uber.rx_central_ble.Irrelevant;
import com.uber.rx_central_ble.Optional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;

/** Register for characteristic notifications. */
public class RegisterNotification implements GattOperation<Irrelevant> {

  private final Relay<Optional<GattIO>> gattRelay = BehaviorRelay.createDefault(Optional.empty());
  private final Single<Irrelevant> resultSingle;

  public RegisterNotification(UUID svc, UUID chr, int timeoutMs) {
    this(svc, chr, null, timeoutMs);
  }

  public RegisterNotification(
      UUID svc, UUID chr, @Nullable GattIO.Preprocessor preprocessor, int timeoutMs) {
    resultSingle =
        gattRelay
            .filter(Optional::isPresent)
            .map(Optional::get)
            .firstOrError()
            .doOnSuccess(g -> gattRelay.accept(Optional.empty()))
            .flatMap(
                gattIO ->
                    gattIO
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
  public void execute(GattIO gattIO) {
    gattRelay.accept(Optional.of(gattIO));
  }

  @Override
  public Single<Irrelevant> executeWithResult(GattIO gattIO) {
    return resultSingle
            .doOnSubscribe(disposable -> execute(gattIO));
  }
}
