/*
 * Copyright 2009 Robey Pointer <robeypointer@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lag.logging

import java.util.{Calendar, logging => javalog}
import scala.collection.Map
import scala.collection.{jcl, mutable}
import net.lag.configgy.ConfigMap


// replace java's ridiculous log levels with the standard ones.
sealed case class Level(name: String, value: Int) extends javalog.Level(name, value) {
  Logger.levelNamesMap(name) = this
  Logger.levelsMap(value) = this
}

object Level {
  case object OFF extends Level("OFF", Math.MAX_INT)
  case object FATAL extends Level("FATAL", 1000)
  case object CRITICAL extends Level("CRITICAL", 970)
  case object ERROR extends Level("ERROR", 930)
  case object WARNING extends Level("WARNING", 900)
  case object INFO extends Level("INFO", 800)
  case object DEBUG extends Level("DEBUG", 500)
  case object TRACE extends Level("TRACE", 400)
  case object ALL extends Level("ALL", Math.MIN_INT)
}


class LoggingException(reason: String) extends Exception(reason)


private[logging] class LazyLogRecord(level: javalog.Level, messageGenerator: => AnyRef) extends javalog.LogRecord(level, "") {
  // for each logged line, generate this string only once, regardless of how many handlers there are:
  var cached: Option[AnyRef] = None

  def generate = {
    cached match {
      case Some(value) =>
        value
      case None =>
        cached = Some(messageGenerator)
        cached.get
    }
  }
}


/**
 * Scala wrapper for logging.
 */
class Logger private(val name: String, private val wrapped: javalog.Logger) {
  // wrapped methods:
  def addHandler(handler: javalog.Handler) = wrapped.addHandler(handler)
  def getFilter() = wrapped.getFilter()
  def getHandlers() = wrapped.getHandlers()
  def getLevel() = wrapped.getLevel()
  def getParent() = wrapped.getParent()
  def getUseParentHandlers() = wrapped.getUseParentHandlers()
  def isLoggable(level: javalog.Level) = wrapped.isLoggable(level)
  def log(record: javalog.LogRecord) = wrapped.log(record)
  def removeHandler(handler: javalog.Handler) = wrapped.removeHandler(handler)
  def setFilter(filter: javalog.Filter) = wrapped.setFilter(filter)
  def setLevel(level: javalog.Level) = wrapped.setLevel(level)
  def setUseParentHandlers(use: Boolean) = wrapped.setUseParentHandlers(use)

  override def toString = {
    "<%s name='%s' level=%s handlers=%s use_parent=%s>".format(getClass.getName, name, getLevel(),
      getHandlers().toList.mkString("[", ", ", "]"), if (getUseParentHandlers()) "true" else "false")
  }

  /**
   * Log a message, with sprintf formatting, at the desired level.
   */
  def log(level: Level, msg: String, items: Any*): Unit = log(level, null: Throwable, msg, items: _*)

  /**
   * Log a message, with sprintf formatting, at the desired level, and
   * attach an exception and stack trace.
   */
  def log(level: Level, thrown: Throwable, message: String, items: Any*): Unit = {
    val myLevel = getLevel
    if ((myLevel eq null) || (level.intValue >= myLevel.intValue)) {
      val record = new javalog.LogRecord(level, message)
      if (items.size > 0) {
        record.setParameters(items.toArray.asInstanceOf[Array[Object]])
      }
      record.setLoggerName(wrapped.getName)
      if (thrown ne null) {
        record.setThrown(thrown)
      }
      wrapped.log(record)
    }
  }

  // convenience methods:
  def fatal(msg: String, items: Any*) = log(Level.FATAL, msg, items: _*)
  def fatal(thrown: Throwable, msg: String, items: Any*) = log(Level.FATAL, thrown, msg, items: _*)
  def critical(msg: String, items: Any*) = log(Level.CRITICAL, msg, items: _*)
  def critical(thrown: Throwable, msg: String, items: Any*) = log(Level.CRITICAL, thrown, msg, items: _*)
  def error(msg: String, items: Any*) = log(Level.ERROR, msg, items: _*)
  def error(thrown: Throwable, msg: String, items: Any*) = log(Level.ERROR, thrown, msg, items: _*)
  def warning(msg: String, items: Any*) = log(Level.WARNING, msg, items: _*)
  def warning(thrown: Throwable, msg: String, items: Any*) = log(Level.WARNING, thrown, msg, items: _*)
  def info(msg: String, items: Any*) = log(Level.INFO, msg, items: _*)
  def info(thrown: Throwable, msg: String, items: Any*) = log(Level.INFO, thrown, msg, items: _*)
  def debug(msg: String, items: Any*) = log(Level.DEBUG, msg, items: _*)
  def debug(thrown: Throwable, msg: String, items: Any*) = log(Level.DEBUG, thrown, msg, items: _*)
  def trace(msg: String, items: Any*) = log(Level.TRACE, msg, items: _*)
  def trace(thrown: Throwable, msg: String, items: Any*) = log(Level.TRACE, thrown, msg, items: _*)

  /**
   * Log a message, with lazy (call-by-name) computation of the message,
   * at the desired level.
   */
  def logLazy(level: Level, message: => AnyRef): Unit = logLazy(level, null: Throwable, message)

  /**
   * Log a message, with lazy (call-by-name) computation of the message,
   * and attach an exception and stack trace.
   */
  def logLazy(level: Level, thrown: Throwable, message: => AnyRef): Unit = {
    val myLevel = getLevel
    if ((myLevel eq null) || (level.intValue >= myLevel.intValue)) {
      val record = new LazyLogRecord(level, message)
      record.setLoggerName(wrapped.getName)
      if (thrown ne null) {
        record.setThrown(thrown)
      }
      wrapped.log(record)
    }
  }

  // convenience methods:
  def ifFatal(message: => AnyRef) = logLazy(Level.FATAL, message)
  def ifFatal(thrown: Throwable, message: => AnyRef) = logLazy(Level.FATAL, thrown, message)
  def ifCritical(message: => AnyRef) = logLazy(Level.CRITICAL, message)
  def ifCritical(thrown: Throwable, message: => AnyRef) = logLazy(Level.CRITICAL, thrown, message)
  def ifError(message: => AnyRef) = logLazy(Level.ERROR, message)
  def ifError(thrown: Throwable, message: => AnyRef) = logLazy(Level.ERROR, thrown, message)
  def ifWarning(message: => AnyRef) = logLazy(Level.WARNING, message)
  def ifWarning(thrown: Throwable, message: => AnyRef) = logLazy(Level.WARNING, thrown, message)
  def ifInfo(message: => AnyRef) = logLazy(Level.INFO, message)
  def ifInfo(thrown: Throwable, message: => AnyRef) = logLazy(Level.INFO, thrown, message)
  def ifDebug(message: => AnyRef) = logLazy(Level.DEBUG, message)
  def ifDebug(thrown: Throwable, message: => AnyRef) = logLazy(Level.DEBUG, thrown, message)
  def ifTrace(message: => AnyRef) = logLazy(Level.TRACE, message)
  def ifTrace(thrown: Throwable, message: => AnyRef) = logLazy(Level.TRACE, thrown, message)
}


object Logger {
  private[logging] val levelNamesMap = new mutable.HashMap[String, Level]
  private[logging] val levelsMap = new mutable.HashMap[Int, Level]

  // A cache of scala Logger objects by name
  //
  // Using a low concurrencyLevel (2), with the assumption that we aren't ever creating too
  // many loggers at the same time
  private val loggersCache = new java.util.concurrent.ConcurrentHashMap[String, Logger](128, 0.75f, 2)


  private val root: Logger = get("")

  // clear out some cruft from the java root logger.
  private val javaRoot = javalog.Logger.getLogger("")


  // ----- convenience methods:

  /** OFF is used to turn off logging entirely. */
  def OFF = Level.OFF

  /** Describes an event which will cause the application to exit immediately, in failure. */
  def FATAL = Level.FATAL

  /** Describes an event which will cause the application to fail to work correctly, but
   *  keep attempt to continue. The application may be unusable.
   */
  def CRITICAL = Level.CRITICAL

  /** Describes a user-visible error that may be transient or not affect other users. */
  def ERROR = Level.ERROR

  /** Describes a problem which is probably not user-visible but is notable and/or may be
   *  an early indication of a future error.
   */
  def WARNING = Level.WARNING

  /** Describes information about the normal, functioning state of the application. */
  def INFO = Level.INFO

  /** Describes information useful for general debugging, but probably not normal,
   *  day-to-day use.
   */
  def DEBUG = Level.DEBUG

  /** Describes information useful for intense debugging. */
  def TRACE = Level.TRACE

  /** ALL is used to log everything. */
  def ALL = Level.ALL

  // to force them to get loaded from class files:
  root.setLevel(OFF)
  root.setLevel(FATAL)
  root.setLevel(CRITICAL)
  root.setLevel(ERROR)
  root.setLevel(WARNING)
  root.setLevel(INFO)
  root.setLevel(DEBUG)
  root.setLevel(TRACE)
  root.setLevel(ALL)
  reset


  /**
   * Return a map of log level values to the corresponding Level objects.
   */
  def levels: Map[Int, Level] = levelsMap.readOnly

  /**
   * Return a map of log level names to the corresponding Level objects.
   */
  def levelNames: Map[String, Level] = levelNamesMap.readOnly

  /**
   * Reset logging to an initial state, where all logging is set at
   * INFO level and goes to the console (stderr). Any existing log
   * handlers are removed.
   */
  def reset() = {
    clearHandlers
    javaRoot.addHandler(new ConsoleHandler(new FileFormatter))
  }

  /**
   * Remove all existing log handlers from all existing loggers.
   */
  def clearHandlers() = {
    for (logger <- elements) {
      // some custom Logger implementations may return null from getHandlers
      val handlers = logger.getHandlers()
      if (handlers ne null) {
        for (handler <- logger.getHandlers) {
          try {
            handler.close()
          } catch { case _ => () }
          logger.removeHandler(handler)
        }
      }
      logger.setLevel(null)
    }
  }

  /**
   * Return a logger for the given package name. If one doesn't already
   * exist, a new logger will be created and returned.
   */
  def get(name: String): Logger = {
    loggersCache.get(name) match {
      case logger: Logger =>
        logger
      case null =>
        val logger = new Logger(name, javalog.Logger.getLogger(name))
        logger.setUseParentHandlers(true)

        val oldLogger = loggersCache.putIfAbsent(name, logger)
        if (oldLogger != null) {
          oldLogger
        } else {
          logger
        }
    }
  }

  /** An alias for `get(name)` */
  def apply(name: String) = get(name)

  private def get(depth: Int): Logger = getForClassName(new Throwable().getStackTrace()(depth).getClassName)

  /**
   * Return a logger for the class name of the class/object that called
   * this method. Normally you would use this in a "private val"
   * declaration on the class/object. The class name is determined
   * by sniffing around on the stack.
   */
  def get: Logger = get(2)

  /** An alias for `get()` */
  def apply() = get(2)

  private def getForClassName(className: String) = {
    if (className.endsWith("$")) {
      get(className.substring(0, className.length - 1))
    } else {
      get(className)
    }
  }

  /**
   * Return a logger for the package of the class given.
   */
  def get(cls: Class[_]): Logger = getForClassName(cls.getName)

  /** An alias for `get(class)` */
  def apply(cls: Class[_]) = get(cls)

  /**
   * Iterate the Logger objects that have been created.
   */
  def elements: Iterator[Logger] = (new jcl.IterableWrapper[Logger] { val underlying = loggersCache.values() }).elements

  /**
   * Create a Logger (or find an existing one) and configure it according
   * to a set of config keys.
   *
   * @param config a config block to parse
   * @param validateOnly don't actually configure the Logger, just throw an
   *     exception if the configuration is invalid
   * @param allowNestedBlocks consider the configuration valid if it
   *     contains nested config blocks, which are normally invalid
   *
   * @throws LoggingException if a config value can't be parsed correctly
   *     (some settings can only be one of a small possible set of values)
   */
  def configure(config: ConfigMap, validateOnly: Boolean, allowNestedBlocks: Boolean): Logger = {
    // make sure no other screwy attributes are in this AttributeMap
    val allowed = List("node", "console", "filename", "roll", "utc",
                       "truncate", "truncate_stack_traces", "level",
                       "use_parents", "syslog_host", "syslog_server_name",
                       "syslog_use_iso_date_format", "prefix_format", "format",
                       "use_full_package_names", "append", "scribe_server",
                       "scribe_buffer_msec", "scribe_backoff_msec",
                       "scribe_max_packet_size", "scribe_category",
                       "throttle_period_msec", "throttle_rate", "handle_sighup",
                       "scribe_max_buffer", "syslog_priority", "rotate_count")
    var forbidden = config.keys.filter(x => !(allowed contains x)).toList
    if (allowNestedBlocks) {
      forbidden = forbidden.filter(x => !config.getConfigMap(x).isDefined)
    }
    if (forbidden.length > 0) {
      throw new LoggingException("Unknown logging config attribute(s): " + forbidden.mkString(", "))
    }

    val logger = Logger.get(config.getString("node", ""))
    if (!validateOnly && allowNestedBlocks) {
      for (handler <- logger.getHandlers) {
        logger.removeHandler(handler)
      }
    }

    val formatter = config.getString("format") match {
      case None => {
        config.getString("prefix_format") match {
          case None => {
            new FileFormatter
          }
          case Some(format) => new GenericFormatter(format)
        }
      }
      case Some("bare") => BareFormatter
      case Some("exception_json") => new ExceptionJsonFormatter
      case Some(unknown) => throw new LoggingException("Unknown format: " + unknown)
    }

    var handlers: List[Handler] = Nil

    if (config.getBool("console", false)) {
      val handler = new ConsoleHandler(formatter)
      handlers = handler :: handlers
    }

    for (filename <- config.getString("filename")) {
      var handleSighup = config.getBool("handle_sighup", false)
      val policy = config.getString("roll", "never").toLowerCase match {
        case "never" => Never
        case "hourly" => Hourly
        case "daily" => Daily
        case "sunday" => Weekly(Calendar.SUNDAY)
        case "monday" => Weekly(Calendar.MONDAY)
        case "tuesday" => Weekly(Calendar.TUESDAY)
        case "wednesday" => Weekly(Calendar.WEDNESDAY)
        case "thursday" => Weekly(Calendar.THURSDAY)
        case "friday" => Weekly(Calendar.FRIDAY)
        case "saturday" => Weekly(Calendar.SATURDAY)
        case "hup" => handleSighup = true; Never
        case x => throw new LoggingException("Unknown logfile rolling policy: " + x)
      }
      val handler =
        new FileHandler(filename, policy, formatter, config.getBool("append", true), handleSighup)
      val rotateCount = config.getInt("rotate_count", -1 )
      if (rotateCount != -1) {
        handler.rotateCount = rotateCount
      }
      handlers = handler :: handlers
    }

    for (hostname <- config.getString("syslog_host")) {
      val useIsoDateFormat = config.getBool("syslog_use_iso_date_format", true)
      val handler = new SyslogHandler(useIsoDateFormat, hostname)
      for (serverName <- config.getString("syslog_server_name")) {
        handler.serverName = serverName
      }
      for (priority <- config.getInt("syslog_priority")) {
        handler.priority = priority
      }
      handlers = handler :: handlers
    }

    for (scribeServer <- config.getString("scribe_server")) {
      val sh = new ScribeHandler(formatter)
      sh.server = scribeServer
      config.getInt("scribe_buffer_msec").map { sh.bufferTimeMilliseconds = _ }
      config.getInt("scribe_backoff_msec").map { sh.connectBackoffMilliseconds = _ }
      config.getInt("scribe_max_packet_size").map { sh.maxMessagesPerTransaction = _ }
      config.getString("scribe_category").map { sh.category = _ }
      config.getInt("scribe_max_buffer").map { sh.maxMessagesToBuffer = _ }
      handlers = sh :: handlers
    }

    /* if they didn't specify a level, use "null", which is a secret
     * signal to javalog to use the parent logger's level. this is the
     * usual desired behavior, but not really documented anywhere. sigh.
     */
    val level = config.getString("level").map { levelName =>
      levelNamesMap.get(levelName.toUpperCase) match {
        case Some(x) => x
        case None => throw new LoggingException("Unknown log level: " + levelName)
      }
    }

    for (period <- config.getLong("throttle_period_msec"); rate <- config.getInt("throttle_rate")) {
      handlers = handlers.map(new ThrottledHandler(_, period.toInt, rate))
    }

    for (handler <- handlers) {
      level.map(handler.setLevel(_))
      handler.formatter.useUtc = config.getBool("utc", false)
      handler.formatter.truncateAt = config.getInt("truncate", 0)
      handler.formatter.truncateStackTracesAt = config.getInt("truncate_stack_traces", 30)
      handler.formatter.useFullPackageNames = config.getBool("use_full_package_names", false)
      if (! validateOnly) {
        logger.addHandler(handler)
      }
    }

    if (! validateOnly) {
      logger.setUseParentHandlers(config.getBool("use_parents", true))
      level.foreach { level =>
        if (logger.getLevel() eq null) {
          logger.setLevel(level)
        }
      }
    }

    logger
  }
}
