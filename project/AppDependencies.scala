import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc.mongo"     %%  "hmrc-mongo-play-28"          % "0.68.0",
    "uk.gov.hmrc"           %%  "bootstrap-frontend-play-28"  % "5.18.0",
    "uk.gov.hmrc"           %%  "play-frontend-hmrc"          % "1.31.0-play-28",
    "uk.gov.hmrc"           %%  "play-partials"               % "8.2.0-play-28",
    "uk.gov.hmrc"           %%  "play-language"               % "5.1.0-play-28"
  )

  val test = Seq(
    "uk.gov.hmrc.mongo"     %%  "hmrc-mongo-test-play-28"     % "0.59.0"  % "test, it",
    "uk.gov.hmrc"           %%  "bootstrap-test-play-28"      % "5.18.0"  % "test, it",
    "org.jsoup"             %   "jsoup"                       % "1.15.2"  % "test, it",
    "com.typesafe.play"     %%  "play-test"                   % current   % "test, it",
    "org.scalatestplus"     %%  "mockito-3-4"                 % "3.2.10.0" % "test, it",
    "com.vladsch.flexmark"  %   "flexmark-all"                % "0.36.8"  % "test, it"
  )
}
