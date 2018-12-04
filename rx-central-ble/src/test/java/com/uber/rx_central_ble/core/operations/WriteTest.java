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

import static com.uber.rx_central_ble.GattIO.DEFAULT_MTU;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

public class WriteTest  {

  @Mock GattIO gattIO;

  private final TestScheduler testScheduler = new TestScheduler();
  private final UUID svcUuid = UUID.randomUUID();
  private final UUID chrUuid = UUID.randomUUID();

  private CompletableSubject writeCompletable = CompletableSubject.create();
  private TestObserver<Irrelevant> writeResultTestObserver;
  private byte[] data;
  private Write write;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    RxJavaPlugins.setComputationSchedulerHandler(schedulerCallable -> testScheduler);

    when(gattIO.write(any(), any(), any())).thenReturn(writeCompletable);
    when(gattIO.getMaxWriteLength()).thenReturn(DEFAULT_MTU);

    data = new byte[128];
    for (int i = 0; i < 128; i++) {
      data[i] = (byte) i;
    }

    write = new Write(svcUuid, chrUuid, data, 5000);
    writeResultTestObserver = write.result().test();
  }

  @After
  public void after() {
    RxJavaPlugins.reset();
  }

  @Test
  public void write_timeout() {
    write.execute(gattIO);

    testScheduler.advanceTimeBy(5000 + 1000, TimeUnit.MILLISECONDS);

    writeResultTestObserver.assertError(TimeoutException.class);
  }

  @Test
  public void write_error() {
    write.execute(gattIO);

    writeCompletable.onError(new GattError(GattError.Code.MISSING_CHARACTERISTIC));

    writeResultTestObserver.assertError(GattError.class);
  }

  @Test
  public void write_success() {
    write.execute(gattIO);

    writeCompletable.onComplete();

    verify(gattIO, times((128 / DEFAULT_MTU) + 1)).write(any(), any(), any());

    writeResultTestObserver.assertComplete();
  }

  @Test
  public void write_execute_withResult() {
    writeResultTestObserver = write.executeWithResult(gattIO).test();

    writeCompletable.onComplete();

    verify(gattIO, times((128 / DEFAULT_MTU) + 1)).write(any(), any(), any());

    writeResultTestObserver.assertComplete();
  }
}
