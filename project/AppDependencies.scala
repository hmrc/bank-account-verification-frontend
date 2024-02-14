import sbt.*

object AppDependencies {
  private val bootstrapPlayVersion = "8.4.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30" % "1.6.0",
    "uk.gov.hmrc" %% "bootstrap-frontend-play-30" % bootstrapPlayVersion,
    "uk.gov.hmrc" %% "play-frontend-hmrc-play-30" % "8.5.0",
    "uk.gov.hmrc" %% "play-partials-play-30" % "9.1.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapPlayVersion % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % "1.6.0" % Test,
    "org.jsoup" % "jsoup" % "1.15.4" % Test,
  )
}
