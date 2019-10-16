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

import com.jakewharton.rxrelay2.PublishRelay;
import com.uber.rxcentralble.Peripheral;
import com.uber.rxcentralble.PeripheralOperation;
import com.uber.rxcentralble.Irrelevant;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.SingleSubject;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CorePeripheralManagerTest {

  @Mock
  Peripheral peripheral;
  @Mock
  PeripheralOperation<Irrelevant> peripheralOperation1;
  @Mock
  PeripheralOperation<Irrelevant> peripheralOperation2;
  @Mock
  PeripheralOperation<Irrelevant> peripheralOperation3;
  @Mock
  PeripheralOperation<Irrelevant> peripheralOperation4;

  private final UUID chrUuid = UUID.randomUUID();
  private final SingleSubject<Irrelevant> operationResultSubject1 = SingleSubject.create();
  private final SingleSubject<Irrelevant> operationResultSubject2 = SingleSubject.create();
  private final SingleSubject<Irrelevant> operationResultSubject3 = SingleSubject.create();
  private final SingleSubject<Irrelevant> operationResultSubject4 = SingleSubject.create();
  private final PublishRelay<Boolean> connectedRelay = PublishRelay.create();
  private final PublishRelay<byte[]> notificationRelay = PublishRelay.create();

  private CorePeripheralManager corePeripheralManager;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    corePeripheralManager = new CorePeripheralManager();
    corePeripheralManager.setPeripheral(peripheral);
  }

  @Test
  public void queueOperations() {
    when(peripheralOperation1.result()).thenReturn(operationResultSubject1.hide());
    when(peripheralOperation2.result()).thenReturn(operationResultSubject2.hide());
    when(peripheralOperation3.result()).thenReturn(operationResultSubject3.hide());
    when(peripheralOperation4.result()).thenReturn(operationResultSubject4.hide());

    Single<Irrelevant> op1 = corePeripheralManager.queueOperation(peripheralOperation1);
    verify(peripheralOperation1, times(0)).execute(any());

    Single<Irrelevant> op2 = corePeripheralManager.queueOperation(peripheralOperation2);
    verify(peripheralOperation2, times(0)).execute(any());

    Single<Irrelevant> op3 = corePeripheralManager.queueOperation(peripheralOperation3);
    verify(peripheralOperation3, times(0)).execute(any());

    op1.test();
    op2.test();
    TestObserver op3Observer = op3.test();

    verify(peripheralOperation1).execute(any());
    verify(peripheralOperation2, times(0)).execute(any());

    // Subscriber for Operation 3 decides they want to cancel the operation.
    op3Observer.dispose();

    operationResultSubject1.onSuccess(Irrelevant.INSTANCE);
    verify(peripheralOperation2).execute(any());

    Single<Irrelevant> op4 = corePeripheralManager.queueOperation(peripheralOperation4);
    op4.test();
    verify(peripheralOperation4, times(0)).execute(any());

    operationResultSubject2.onError(new Exception());
    verify(peripheralOperation4).execute(any());

    // Operation 3 should never have executed.
    verify(peripheralOperation3, times(0)).execute(any());
  }

  @Test
  public void noitifcation() {
    when(peripheral.notification(any())).thenReturn(notificationRelay.hide());

    TestObserver<byte[]> notificationTestObserver = corePeripheralManager.notification(chrUuid).test();

    byte[] notification = new byte[] {0x00};
    notificationRelay.accept(notification);

    notificationTestObserver.assertValue(notification);
  }

  @Test
  public void connected() {
    when(peripheral.connected()).thenReturn(connectedRelay.hide());

    TestObserver<Boolean> connectedTestObserver = corePeripheralManager.connected().test();

    connectedRelay.accept(true);

    connectedTestObserver.assertValues(false, true);
  }
}
