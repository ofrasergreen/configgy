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

package net.lag.configgy

import java.io.File
import net.lag.logging.Logger


/**
 * Main API entry point into the configgy library.
 */
object Configgy {
  private var _config: Config = null
  private val subscriber = new LoggingConfigSubscriber

  /**
   * The base Config object for this server. This will only be defined
   * after calling one of `configure` or `configureFromResource`.
   */
  def config = _config

  /** 
   * Sets the base Config object for this server.  You might want to 
   * call one of the configure methods instead of this, but if those
   * don't work for your needs, use this as a fallback.
   */
  def config_=(c: Config) {
    Logger.reset
    _config = c
    configLogging
  }

  /**
   * Configure the server by loading a config file from the given path
   * and filename. The filename must be relative to the path. The path is
   * used to resolve filenames given in "include" lines.
   */
  def configure(path: String, filename: String): Unit = {
    config = Config.fromFile(path, filename)
  }

  /**
   * Configure the server by loading a config file from the given filename.
   * The base folder will be extracted from the filename and used as a base
   * path for resolving filenames given in "include" lines.
   */
  def configure(filename: String): Unit = {
    val n = filename.lastIndexOf('/')
    if (n < 0) {
      configure(new File(".").getCanonicalPath, filename)
    } else {
      configure(filename.substring(0, n), filename.substring(n + 1))
    }
  }

  /**
   * Reload the previously-loaded config file from disk. Any changes will
   * take effect immediately. **All** subscribers will be called to
   * verify and commit the change (even if their nodes didn't actually
   * change).
   */
  def reload() = _config.reload()

  /**
   * Configure the server by loading a config file from the given named
   * resource inside this jar file. "include" lines will also operate
   * on resource paths.
   */
  def configureFromResource(name: String) = {
    config = Config.fromResource(name)
  }

  /**
   * Configure the server by loading a config file from the given named
   * resource inside this jar file, using a specific class loader.
   * "include" lines will also operate on resource paths.
   */
  def configureFromResource(name: String, classLoader: ClassLoader) = {
    config = Config.fromResource(name, classLoader)
  }

  /**
   * Configure the server by loading config data from given string.
   */
  def configureFromString(data: String) = {
    config = Config.fromString(data)
  }

  private def configLogging = {
    val log = Logger.get("")

    try {
      val attr = _config.getConfigMap("log")
      subscriber.commit(None, attr)
      if (attr.isDefined) {
        attr.get.subscribe(subscriber)
      }
    } catch {
      case e: Throwable =>
        log.critical(e, "Failed to configure logging")
        throw e
    }
  }

  /**
   * Temporarily configure logging with a passed-in "log" config block.
   * Changes made to the config block *after* calling this method will
   * not be picked up by logging.
   */
  def configLogging(config: ConfigMap): Unit = synchronized {
    subscriber.commit(None, Some(config))
  }

  private class LoggingConfigSubscriber extends Subscriber {
    @throws(classOf[ValidationException])
    def validate(current: Option[ConfigMap], replacement: Option[ConfigMap]): Unit = {
      try {
        for (logConfig <- replacement) {
          Logger.configure(logConfig, true, true)
          for (key <- logConfig.keys; block <- logConfig.getConfigMap(key)) {
            Logger.configure(block, true, false)
          }
        }
      } catch {
        case e: Throwable => throw new ValidationException(e.toString)
      }
    }

    def commit(current: Option[ConfigMap], replacement: Option[ConfigMap]): Unit = {
      Logger.reset

      for (logConfig <- replacement) {
        Logger.configure(logConfig, false, true)
        for (key <- logConfig.keys; block <- logConfig.getConfigMap(key)) {
          Logger.configure(block, false, false)
        }
      }

      val log = Logger.get("")
      if (log.getLevel() eq null) {
        log.setLevel(Logger.INFO)
      }
    }
  }
}
