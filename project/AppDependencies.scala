import sbt.*

object AppDependencies {

  private val bootstrapPlayVersion = "10.1.0"
  private val hmrcMongoPlayVersion = "2.7.0"
  private val playSuffix           = "-play-30"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo$playSuffix"         % hmrcMongoPlayVersion,
    "uk.gov.hmrc"       %% s"bootstrap-frontend$playSuffix" % bootstrapPlayVersion,
    "uk.gov.hmrc"       %% s"play-frontend-hmrc$playSuffix" % "12.8.0",
    "uk.gov.hmrc"       %% s"play-partials$playSuffix"      % "10.1.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-test$playSuffix"     % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test$playSuffix"    % hmrcMongoPlayVersion,
    "org.jsoup"          % "jsoup"                          % "1.21.2"
  ).map(_ % Test)
}
