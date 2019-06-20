package com.tnt.assessment

import QueuingService.ServiceIdentifier
import io.circe.Json
import utest._
import zio.{IO, Ref, Runtime}
import zio.clock.Clock
import zio.duration._
import zio.internal.PlatformLive
import zio.internal.tracing.TracingConfig

import scala.concurrent.ExecutionContext

object QueryExecutorTests extends TestSuite {
  private def testRuntime = {
    val platform =
      PlatformLive
        .fromExecutionContext(ExecutionContext.global)
        .withTracingConfig(TracingConfig.disabled)

    Runtime(Clock.Live, platform)
  }

  val tests = Tests {
    test("HttpQueryExecutor") {
      import org.asynchttpclient.Request

      test("should format queries with expected format") {
        val program =
          for {
            maybeReq <- Ref.make[Option[Request]](None)
            response = HttpQueryExecutor.StrictResponse(200, "{}")
            executor = new HttpQueryExecutor("http://localhost", req => maybeReq.set(Some(req)).const(response))

            _       <- executor.execute(ServiceIdentifier.Pricing, List("US", "UK"))
            request <- maybeReq.get
          } yield request.map(_.getUrl)

        val expect = Some("http://localhost/pricing?q=US%2CUK")
        val actual = testRuntime.unsafeRun(program)

        assert(actual == expect)
      }
    }

    test("ThrottlingQueryExecutor") {
      test("should throttle with preconfigured number of requests") {
        val program =
          for {
            requests            <- Ref.make(List.empty[List[String]])
            (executor, drainer) <-
              ThrottlingQueryExecutor(
                  (_, queries) => requests.update(queries :: _).const(Map.empty)
                , 2
                , 1.second
              )

            fiber <- drainer.fork
            _     <- executor.execute(ServiceIdentifier.Shipment, List("100000001"))
            _     <- executor.execute(ServiceIdentifier.Shipment, List("100000002", "100000003"))
            _     <- executor.execute(ServiceIdentifier.Shipment, List("100000004"))
            _     <- executor.execute(ServiceIdentifier.Shipment, List("100000005"))
            _     <- fiber.interrupt
            lists <- requests.get
          } yield lists

        val expect = List(List("100000003", "100000004"), List("100000001", "100000002"))
        val actual = testRuntime.unsafeRun(program)

        assert(actual == expect)
      }

      test("should sent out after preconfigured timeout") {
        val program =
          for {
            requests            <- Ref.make(List.empty[List[String]])
            (executor, drainer) <-
              ThrottlingQueryExecutor(
                (_, queries) =>
                  for {
                    _ <- IO.unit.delay(40.millis)
                    _ <- requests.update(queries :: _)
                  } yield
                    queries.map(_ -> Json.obj()).toMap
                , 1
                , 50.millis
              )

            fiber   <- drainer.fork
            result  <- executor.execute(ServiceIdentifier.Shipment, List("100000001", "100000002"))
            _       <- fiber.interrupt
            lists   <- requests.get
          } yield (lists, result)

        val expect = (List(List("100000001")), Map("100000001" -> Json.obj()))
        val actual = testRuntime.unsafeRun(program)

        assert(actual == expect)
      }
    }
  }
}
