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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.SingleSubject;

import static com.uber.rx_central_ble.GattError.Code.MISSING_CHARACTERISTIC;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ReadRssiTest {

  @Mock
  GattIO gattIO;

  private final TestScheduler testScheduler = new TestScheduler();

  private SingleSubject<Integer> readOperationSingle = SingleSubject.create();
  private TestObserver<Integer> readResultTestObserver;
  private ReadRssi readRssi;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);

    RxJavaPlugins.setComputationSchedulerHandler(schedulerCallable -> testScheduler);

    when(gattIO.readRssi()).thenReturn(readOperationSingle);

    readRssi = new ReadRssi(5000);
    readResultTestObserver = readRssi.result().test();
  }

  @After
  public void after() {
    RxJavaPlugins.reset();
  }

  @Test
  public void read_timeout() {
    readRssi.execute(gattIO);

    testScheduler.advanceTimeBy(5000 + 1000, TimeUnit.MILLISECONDS);

    readResultTestObserver.assertError(TimeoutException.class);
  }

  @Test
  public void read_error() {
    readRssi.execute(gattIO);

    readOperationSingle.onError(new GattError(MISSING_CHARACTERISTIC));

    readResultTestObserver.assertError(GattError.class);
  }

  @Test
  public void read_success_verifyOnlyOnce() {
    readRssi.execute(gattIO);

    int result = 100;
    readOperationSingle.onSuccess(result);

    readResultTestObserver.assertValue(result);

    TestObserver<Integer> invalidObserver = readRssi.result().test();
    invalidObserver.assertNotComplete();

    verify(gattIO, times(1)).readRssi();
  }

  @Test
  public void read_execute_withResult_verifyOnlyOnce() {
    readResultTestObserver = readRssi.executeWithResult(gattIO).test();

    int result = 100;
    readOperationSingle.onSuccess(result);

    readResultTestObserver.assertValue(result);

    TestObserver<Integer> invalidObserver = readRssi.result().test();
    invalidObserver.assertNotComplete();

    verify(gattIO, times(1)).readRssi();
  }
}
