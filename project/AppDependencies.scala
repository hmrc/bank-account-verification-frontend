import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"      % "0.50.0",
    "uk.gov.hmrc" %% "bootstrap-frontend-play-28" % "5.3.0",
    "uk.gov.hmrc" %% "play-frontend-hmrc" % "1.31.0-play-28",
    "uk.gov.hmrc" %% "play-partials" % "8.1.0-play-28",
    "uk.gov.hmrc" %% "play-language" % "5.0.0-play-28"
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-28" % "5.3.0" % "test, it",
    "org.scalatest" %% "scalatest" % "3.1.2" % "test, it",
    "org.jsoup" % "jsoup" % "1.10.2" % "test, it",
    "com.typesafe.play" %% "play-test" % current % "test, it",
    "org.scalatestplus" %% "mockito-3-2" % "3.1.2.0" % "test, it",
    "com.vladsch.flexmark" % "flexmark-all" % "0.35.10" % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % "test, it",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % "0.50.0"
  )
}
