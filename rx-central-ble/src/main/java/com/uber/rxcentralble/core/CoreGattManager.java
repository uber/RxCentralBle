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
import com.uber.rxcentralble.GattIO;
import com.uber.rxcentralble.GattManager;
import com.uber.rxcentralble.GattOperation;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.Single;

/** Core GattManager implementation. */
public class CoreGattManager implements GattManager {

  private final Queue<GattOperation> operationQueue = new ArrayDeque<>();
  private final BehaviorRelay<GattIO> gattRelay = BehaviorRelay.create();
  private final Object queueSync = new Object();

  @Nullable private GattOperation currentOperation;

  @Override
  public void setGattIO(GattIO gattIO) {
    gattRelay.accept(gattIO);

    synchronized (queueSync) {
      if (!operationQueue.isEmpty() && currentOperation == null) {
        currentOperation = operationQueue.remove();
        currentOperation.execute(gattRelay.getValue());
      }
    }

  }

  @Override
  public <T> Single<T> queueOperation(GattOperation<T> gattOperation) {
    return gattOperation
        .result()
        .doOnSubscribe(disposable -> processOperation(gattOperation))
        .doFinally(() -> endOperation(gattOperation));
  }

  @Override
  public Observable<Boolean> connected() {
    return gattRelay.switchMap(GattIO::connected).startWith(false);
  }

  @Override
  public Observable<byte[]> notification(UUID characteristic) {
    return gattRelay.switchMap(gatt -> gatt.notification(characteristic));
  }

  protected BehaviorRelay<GattIO> gattIO() {
    return gattRelay;
  }

  private void processOperation(GattOperation gattOperation) {
    synchronized (queueSync) {
      if (currentOperation == null && gattRelay.getValue() != null) {
        currentOperation = gattOperation;
        gattOperation.execute(gattRelay.getValue());
      } else {
        operationQueue.add(gattOperation);
      }
    }

  }

  private void endOperation(GattOperation gattOperation) {
    synchronized (queueSync) {
      operationQueue.remove(gattOperation);

      if (currentOperation == gattOperation) {
        currentOperation = null;

        if (!operationQueue.isEmpty() && gattRelay.getValue() != null) {
          currentOperation = operationQueue.remove();
          currentOperation.execute(gattRelay.getValue());
        }
      }
    }
  }
}
