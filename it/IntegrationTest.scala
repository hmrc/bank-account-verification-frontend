/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

import org.scalatest.{Args, Status, Suite, SuiteMixin}
import org.scalatestplus.play.ServerProvider
import play.api.Application
import play.api.Mode.Test
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{Helpers, TestServer}

trait IntegrationTest extends SuiteMixin with ServerProvider {
  this: Suite =>

  def additionalConfiguration: Map[String, String] = Map("akka.logger-startup-timeout" -> "7s")

  def beforeAppServerStarts() {}

  def afterAppServerStops() {}

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .in(Test)
    .build()

  final def testPort: Int = Helpers.testServerPort

  abstract override def run(testName: Option[String], args: Args): Status = {
    beforeAppServerStarts()
    stabilise()
    val myApp      = app
    val testServer = TestServer(testPort, myApp)
    testServer.start()
    stabilise()
    try {
      val newConfigMap =
        args.configMap + ("org.scalatestplus.play.app" -> myApp) + ("org.scalatestplus.play.port" -> testPort)
      val newArgs = args.copy(configMap = newConfigMap)
      val status  = super.run(testName, newArgs)
      status.waitUntilCompleted()
      status
    } finally {
      testServer.stop()
      afterAppServerStops()
      stabilise()
    }
  }

  // starting the stubs takes finite time; there is a race condition and we need to wait. Sigh.
  private def stabilise() {
    Thread.sleep(100)
  }

  def appEndpoint = s"http://localhost:$testPort"
}
