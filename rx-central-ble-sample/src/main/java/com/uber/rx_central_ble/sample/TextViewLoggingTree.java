package com.uber.rx_central_ble.sample;

import android.util.Log;
import android.widget.TextView;

import io.reactivex.android.schedulers.AndroidSchedulers;
import timber.log.Timber;

public class TextViewLoggingTree extends Timber.DebugTree {

  private static final String TAG = TextViewLoggingTree.class.getSimpleName();

  private TextView logTextView;

  public TextViewLoggingTree(TextView logTextView) {
    this.logTextView = logTextView;
  }

  @Override
  protected void log(int priority, String tag, String message, Throwable t) {

    try {

      String logTimeStamp = String.valueOf(System.currentTimeMillis());

      if (logTextView != null) {
        AndroidSchedulers.mainThread().scheduleDirect(() ->
                logTextView.setText(logTimeStamp + " | " + message + "\n" + logTextView.getText().toString()));
      }

    } catch (Exception e) {
      Log.e(TAG, "Error while logging into file : " + e);
    }

  }
}
