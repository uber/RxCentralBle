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
import com.uber.rxcentralble.GattIO;
import com.uber.rxcentralble.GattOperation;
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

public class CoreGattManagerTest {

  @Mock
  GattIO gattIO;
  @Mock GattOperation<Irrelevant> gattOperation1;
  @Mock GattOperation<Irrelevant> gattOperation2;
  @Mock GattOperation<Irrelevant> gattOperation3;

  private final UUID chrUuid = UUID.randomUUID();
  private final SingleSubject<Irrelevant> operationResultSubject1 = SingleSubject.create();
  private final SingleSubject<Irrelevant> operationResultSubject2 = SingleSubject.create();
  private final SingleSubject<Irrelevant> operationResultSubject3 = SingleSubject.create();
  private final PublishRelay<Boolean> connectedRelay = PublishRelay.create();
  private final PublishRelay<byte[]> notificationRelay = PublishRelay.create();

  private CoreGattManager coreGattManager;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    coreGattManager = new CoreGattManager();
    coreGattManager.setGattIO(gattIO);
  }

  @Test
  public void queueOperations() {
    when(gattOperation1.result()).thenReturn(operationResultSubject1.hide());
    when(gattOperation2.result()).thenReturn(operationResultSubject2.hide());
    when(gattOperation3.result()).thenReturn(operationResultSubject3.hide());

    Single<Irrelevant> op1 = coreGattManager.queueOperation(gattOperation1);
    verify(gattOperation1, times(0)).execute(any());

    Single<Irrelevant> op2 = coreGattManager.queueOperation(gattOperation2);
    verify(gattOperation2, times(0)).execute(any());

    op1.test();
    op2.test();

    verify(gattOperation1).execute(any());
    verify(gattOperation2, times(0)).execute(any());

    operationResultSubject1.onSuccess(Irrelevant.INSTANCE);
    verify(gattOperation2).execute(any());

    Single<Irrelevant> op3 = coreGattManager.queueOperation(gattOperation3);
    op3.test();
    verify(gattOperation3, times(0)).execute(any());

    operationResultSubject2.onError(new Exception());
    verify(gattOperation3).execute(any());
  }

  @Test
  public void noitifcation() {
    when(gattIO.notification(any())).thenReturn(notificationRelay.hide());

    TestObserver<byte[]> notificationTestObserver = coreGattManager.notification(chrUuid).test();

    byte[] notification = new byte[] {0x00};
    notificationRelay.accept(notification);

    notificationTestObserver.assertValue(notification);
  }

  @Test
  public void connected() {
    when(gattIO.connected()).thenReturn(connectedRelay.hide());

    TestObserver<Boolean> connectedTestObserver = coreGattManager.connected().test();

    connectedRelay.accept(true);

    connectedTestObserver.assertValues(false, true);
  }
}
