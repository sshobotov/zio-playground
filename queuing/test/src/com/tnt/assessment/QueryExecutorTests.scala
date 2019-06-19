package com.tnt.assessment

import QueuingService.ServiceIdentifier
import utest._
import zio.{Ref, Runtime}
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
    test("ThrottlingQueryExecutor") {
      test("should throttle expected number of requests") {
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
            _     <- fiber.interrupt
            lists <- requests.get
          } yield lists

        val expect =
          List(List("100000001", "100000002"), List("100000003", "100000004"))
        val actual = testRuntime.unsafeRun(program)

        assert(actual == expect)
      }
    }
  }
}
