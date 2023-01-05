import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc.mongo"     %%  "hmrc-mongo-play-28"          % "0.74.0",
    "uk.gov.hmrc"           %%  "bootstrap-frontend-play-28"  % "7.12.0",
    "uk.gov.hmrc"           %%  "play-frontend-hmrc"          % "5.5.0-play-28",
    "uk.gov.hmrc"           %%  "play-partials"               % "8.3.0-play-28"
  )

  val test = Seq(
    "uk.gov.hmrc.mongo"     %%  "hmrc-mongo-test-play-28"     % "0.74.0"   % "test, it",
    "uk.gov.hmrc"           %%  "bootstrap-test-play-28"      % "7.12.0"   % "test, it",
    "org.jsoup"             %   "jsoup"                       % "1.15.2"   % "test, it",
    "com.typesafe.play"     %%  "play-test"                   % current    % "test, it",
    "org.scalatestplus"     %   "mockito-4-6_2.13"            % "3.2.14.0" % "test, it",
    "com.vladsch.flexmark"  %   "flexmark-all"                % "0.36.8"   % "test, it"
  )
}
