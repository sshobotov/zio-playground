import mill._
import mill.api.Loose
import mill.define.Target
import scalalib._

object queuing extends ScalaModule {
  override def scalaVersion = "2.12.8"

  override def ivyDeps = Agg(
    ivy"dev.zio::zio:1.0.0-RC8-4",
    ivy"dev.zio::zio-interop-java:1.0.0-RC8-4",
    ivy"org.asynchttpclient:async-http-client:2.10.0",
    ivy"io.circe::circe-core:0.10.0",
    ivy"io.circe::circe-generic:0.10.0",
    ivy"io.circe::circe-parser:0.10.0",
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

