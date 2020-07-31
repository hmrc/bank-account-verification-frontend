/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.bankaccountverificationfrontend

import org.slf4j.Logger
import org.slf4j.helpers.MessageFormatter

trait SimpleLogger {
  def info(format: String, arguments: AnyRef*)

  def info(msg: String, t: Throwable)

  def warn(format: String, arguments: AnyRef*)

  def warn(msg: String, t: Throwable)

  def error(format: String, arguments: AnyRef*)

  def error(msg: String, t: Throwable)
}

class LoggerFacade(underlying: Logger) extends SimpleLogger {
  override def info(format: String, arguments: AnyRef*) {
    underlying.info(format, arguments.toArray)
  }

  override def info(msg: String, t: Throwable) {
    underlying.info(msg, t)
  }

  override def warn(format: String, arguments: AnyRef*) {
    underlying.warn(format, arguments.toArray)
  }

  override def warn(msg: String, t: Throwable) {
    underlying.warn(msg, t)
  }

  override def error(format: String, arguments: AnyRef*) {
    underlying.error(format, arguments.toArray)
  }

  override def error(msg: String, t: Throwable) {
    underlying.error(msg, t)
  }
}

/** Logger to stdout for command-line apps. */
object Stdout extends SimpleLogger {
  override def info(format: String, arguments: AnyRef*) {
    val tp = MessageFormatter.arrayFormat(format, arguments.toArray)
    println(tp.getMessage)
  }

  override def info(msg: String, t: Throwable) {
    println(msg)
    t.printStackTrace(System.out)
  }

  override def warn(format: String, arguments: AnyRef*) {
    val tp = MessageFormatter.arrayFormat(format, arguments.toArray)
    println("WARN: " + tp.getMessage)
  }

  override def warn(msg: String, t: Throwable) {
    println("WARN: " + msg)
    t.printStackTrace(System.out)
  }

  override def error(format: String, arguments: AnyRef*) {
    val tp = MessageFormatter.arrayFormat(format, arguments.toArray)
    println("ERROR: " + tp.getMessage)
  }

  override def error(msg: String, t: Throwable) {
    println("ERROR: " + msg)
    t.printStackTrace(System.out)
  }
}
