import mill._
import scalalib._

object queuing extends ScalaModule {
  override def scalaVersion = "2.12.8"

  val zioVersion   = "1.0.0-RC8-4"
  val circeVersion = "0.10.0"

  override def ivyDeps = Agg(
    ivy"dev.zio::zio:$zioVersion",
    ivy"dev.zio::zio-interop-java:$zioVersion",
    ivy"dev.zio::zio-streams:$zioVersion",
    ivy"org.asynchttpclient:async-http-client:2.10.0",
    ivy"io.circe::circe-core:$circeVersion",
    ivy"io.circe::circe-generic:$circeVersion",
    ivy"io.circe::circe-parser:$circeVersion",
    ivy"com.beachape::enumeratum:1.5.13",
    ivy"com.beachape::enumeratum-circe:1.5.21"
  )

  override def scalacPluginIvyDeps = Agg(
    ivy"com.olegpy::better-monadic-for:0.3.0"
  )

  object test extends Tests {
    override def ivyDeps = Agg(
      ivy"com.lihaoyi::utest:0.7.1"
    )

    override def testFrameworks = Seq("utest.runner.Framework")
  }
}

