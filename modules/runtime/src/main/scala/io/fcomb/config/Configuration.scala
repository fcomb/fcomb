/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

package io.fcomb.config

import com.typesafe.config.{Config, ConfigValue}
import com.typesafe.scalalogging.LazyLogging
import pureconfig.error.ConfigReaderFailures
import pureconfig.{loadConfig => pureLoadConfig, _}

object Configuration extends LazyLogging {
  def loadSettings(config: Config): Either[ConfigReaderFailures, Settings] =
    pureLoadConfig[Settings](config, "fcomb")

  implicit def hiddenConfigReader[T](implicit cr: ConfigReader[T]): ConfigReader[Hidden[T]] =
    new ConfigReader[Hidden[T]] {
      override def from(config: ConfigValue): Either[ConfigReaderFailures, Hidden[T]] =
        cr.from(config).map(Hidden(_))
    }
}
