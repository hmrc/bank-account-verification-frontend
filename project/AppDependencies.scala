import sbt.*

object AppDependencies {

  private val bootstrapPlayVersion = "9.18.0"
  private val hmrcMongoPlayVersion = "2.7.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"         % hmrcMongoPlayVersion,
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-30" % bootstrapPlayVersion,
    "uk.gov.hmrc"       %% "play-frontend-hmrc-play-30" % "12.7.0",
    "uk.gov.hmrc"       %% "play-partials-play-30"      % "10.1.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"   % bootstrapPlayVersion  % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30"  % hmrcMongoPlayVersion  % Test,
    "org.jsoup"          % "jsoup"                    % "1.19.1"              % Test
  )
}
