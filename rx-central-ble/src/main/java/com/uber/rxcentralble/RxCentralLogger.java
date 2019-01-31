package com.uber.rxcentralble;

import com.jakewharton.rxrelay2.PublishRelay;
import com.jakewharton.rxrelay2.Relay;

import io.reactivex.Observable;

/**
 * RxCentralLogger is a static utility to subscribe to the internal log stream.
 */
public class RxCentralLogger {

  /**
   * Logging level.
   */
  public enum LogLevel {
    OFF(0),
    DEBUG(1),
    ERROR(2);

    public int value;

    LogLevel(int value) {
      this.value = value;
    }
  }

  private static LogLevel logLevel = LogLevel.OFF;
  private static Relay<RxCentralLog> logRelay = PublishRelay.create();

  private RxCentralLogger() { }

  /**
   * Are debug logs enabled?
   * @return true if enabled.
   */
  public static boolean isDebug() {
    return logLevel.value >= LogLevel.DEBUG.value;
  }

  /**
   * Are error logs enabled?
   * @return true if enabled
   */
  public static boolean isError() {
    return logLevel.value >= LogLevel.ERROR.value;
  }

  /**
   * Log a debug message.
   * @param message
   */
  public static void d(String message) {
    if (isDebug()) {
      logRelay.accept(new RxCentralLog(LogLevel.DEBUG, message));
    }
  }

  /**
   * Log an error message.
   * @param message
   */
  public static void e(String message) {
    if (isError()) {
      logRelay.accept(new RxCentralLog(LogLevel.ERROR, message));
    }
  }

  /**
   * Subscribe to a stream of logs.  This method should only be used by a single subscriber.
   *
   * By default, logging is disabled.  Subscription enables logs to the specified level.  Disposing
   * the subscription will disable logging.
   *
   * @param level level of logs to enable upon subscription.
   * @return Observable stream of logs for the specified level.
   */
  public static Observable<String> logs(LogLevel level) {
    return logRelay
            .filter(log -> log.level.value >= level.value)
            .map(log -> log.message)
            .doOnSubscribe(disposable -> logLevel = level)
            .doFinally(() -> logLevel = LogLevel.OFF);
  }

  /**
   * Helper class for logs.
   */
  private static class RxCentralLog {
    public LogLevel level;
    public String message;

    public RxCentralLog(LogLevel level, String message) {
      this.level = level;
      this.message = message;
    }
  }
}
