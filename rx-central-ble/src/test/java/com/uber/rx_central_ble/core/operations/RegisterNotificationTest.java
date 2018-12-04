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

import com.uber.rx_central_ble.GattError;
import com.uber.rx_central_ble.GattIO;
import com.uber.rx_central_ble.Irrelevant;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.CompletableSubject;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RegisterNotificationTest {

  @Mock GattIO gattIO;

  private final TestScheduler testScheduler = new TestScheduler();
  private final UUID svcUuid = UUID.randomUUID();
  private final UUID chrUuid = UUID.randomUUID();

  private CompletableSubject registerCompletable = CompletableSubject.create();
  private TestObserver<Irrelevant> registerResultTestObserver;
  private RegisterNotification registerNotification;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    RxJavaPlugins.setComputationSchedulerHandler(schedulerCallable -> testScheduler);

    when(gattIO.registerNotification(any(), any(), any())).thenReturn(registerCompletable);

    registerNotification = new RegisterNotification(svcUuid, chrUuid, 5000);
    registerResultTestObserver = registerNotification.result().test();
  }

  @After
  public void after() {
    RxJavaPlugins.reset();
  }

  @Test
  public void register_timeout() {
    registerNotification.execute(gattIO);

    testScheduler.advanceTimeBy(5000 + 1000, TimeUnit.MILLISECONDS);

    registerResultTestObserver.assertError(TimeoutException.class);
  }

  @Test
  public void register_error() {
    registerNotification.execute(gattIO);

    registerCompletable.onError(new GattError(GattError.Code.MISSING_CHARACTERISTIC));

    registerResultTestObserver.assertError(GattError.class);
  }

  @Test
  public void register_success_verifyOnlyOnce() {
    registerNotification.execute(gattIO);

    registerCompletable.onComplete();

    registerResultTestObserver.assertComplete();

    TestObserver<Irrelevant> invalidObserver = registerNotification.result().test();
    invalidObserver.assertNotComplete();

    verify(gattIO, times(1)).registerNotification(any(), any(), any());
  }

  @Test
  public void register_execute_withResult_verifyOnlyOnce() {
    registerResultTestObserver = registerNotification.executeWithResult(gattIO).test();

    registerCompletable.onComplete();

    registerResultTestObserver.assertComplete();

    TestObserver<Irrelevant> invalidObserver = registerNotification.result().test();
    invalidObserver.assertNotComplete();

    verify(gattIO, times(1)).registerNotification(any(), any(), any());
  }
}
