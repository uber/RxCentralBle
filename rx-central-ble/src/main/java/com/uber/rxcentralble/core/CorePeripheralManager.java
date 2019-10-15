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

import android.support.annotation.Nullable;

import com.jakewharton.rxrelay2.BehaviorRelay;
import com.uber.rxcentralble.Peripheral;
import com.uber.rxcentralble.PeripheralManager;
import com.uber.rxcentralble.PeripheralOperation;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.Single;

/** Core PeripheralManager implementation. */
public class CorePeripheralManager implements PeripheralManager {

  private final Queue<PeripheralOperation> operationQueue = new ArrayDeque<>();
  private final BehaviorRelay<Peripheral> peripheralRelay = BehaviorRelay.create();
  private final Object queueSync = new Object();

  @Nullable private PeripheralOperation currentOperation;

  @Override
  public void setPeripheral(Peripheral peripheral) {
    peripheralRelay.accept(peripheral);

    synchronized (queueSync) {
      if (!operationQueue.isEmpty() && currentOperation == null) {
        currentOperation = operationQueue.remove();
        currentOperation.execute(peripheralRelay.getValue());
      }
    }

  }

  @Override
  public <T> Single<T> queueOperation(PeripheralOperation<T> peripheralOperation) {
    return peripheralOperation
        .result()
        .doOnSubscribe(disposable -> processOperation(peripheralOperation))
        .doFinally(() -> endOperation(peripheralOperation));
  }

  @Override
  public Observable<Boolean> connected() {
    return peripheralRelay.switchMap(Peripheral::connected).startWith(false);
  }

  @Override
  public Observable<byte[]> notification(UUID characteristic) {
    return peripheralRelay.switchMap(peripheral -> peripheral.notification(characteristic));
  }

  protected BehaviorRelay<Peripheral> peripheral() {
    return peripheralRelay;
  }

  private void processOperation(PeripheralOperation peripheralOperation) {
    synchronized (queueSync) {
      if (currentOperation == null && peripheralRelay.getValue() != null) {
        currentOperation = peripheralOperation;
        peripheralOperation.execute(peripheralRelay.getValue());
      } else {
        operationQueue.add(peripheralOperation);
      }
    }

  }

  private void endOperation(PeripheralOperation peripheralOperation) {
    synchronized (queueSync) {
      operationQueue.remove(peripheralOperation);

      if (currentOperation == peripheralOperation) {
        currentOperation = null;

        if (!operationQueue.isEmpty() && peripheralRelay.getValue() != null) {
          currentOperation = operationQueue.remove();
          currentOperation.execute(peripheralRelay.getValue());
        }
      }
    }
  }
}
