package com.tnt.assessment

import QueuingService.{BatchResult, Query, QueryResult, ServiceIdentifier}
import QueryExecutor._
import io.circe.parser
import org.asynchttpclient._
import org.asynchttpclient.Dsl._
import zio.{IO, Promise, Queue, Task, UIO, ZIO}
import zio.clock.Clock
import zio.duration._
import zio.interop.javaconcurrent._
import zio.stream.Stream

trait QueryExecutor[R] {
  def execute(service: ServiceIdentifier, queries: List[Query]): ZIO[R, ExecutionFailure, BatchResult]
}

class HttpQueryExecutor(host: String, runHttpRequest: HttpQueryExecutor.RequestExecution) extends QueryExecutor[Any] {
  def execute(service: ServiceIdentifier, queries: List[Query]): IO[ExecutionFailure, BatchResult] = {
    val request =
      get(s"$host/${service.entryName}")
        .addQueryParam("q", queries.mkString(","))
        .build()

    for {
      response <- runHttpRequest(request).mapError(RuntimeExecutionFailure)
      json     <-
        if (response.statusCode < 400)
          IO
            .fromEither {
              parser
                .parse(response.body)
                .flatMap(_.as[BatchResult])
            }
            .mapError(RuntimeExecutionFailure(_))
        else
          IO.fail(HttpExecutionFailure(response.statusCode))
    } yield json
  }
}

object HttpQueryExecutor {
  type RequestExecution = Request => Task[StrictResponse]

  final case class StrictResponse(statusCode: Int, body: String)

  object StrictResponse {
    def fromResponse(response: Response): StrictResponse =
      new StrictResponse(response.getStatusCode, response.getResponseBody)
  }

  def httpClient(httpClient: AsyncHttpClient): RequestExecution =
    request =>
      Task
        .fromFutureJava(IO(httpClient.executeRequest(request)))
        .map(StrictResponse.fromResponse)
}

class ThrottlingQueryExecutor private(
    queues:  Map[ServiceIdentifier, Queue[ThrottlingQueryExecutor.EnqueuedJob]]
  , timeout: Duration
) extends QueryExecutor[Clock] {
  def execute(service: ServiceIdentifier, queries: List[Query]): ZIO[Clock, ExecutionFailure, BatchResult] =
    ZIO.collectAllPar {
      queries map { query =>
        for {
          promise <- Promise.make[ExecutionFailure, Option[QueryResult]]
          _       <- queues(service).offer(query, promise)
          result  <- promise.await.timeout(timeout)
        } yield
          result.flatten map { (query, _) }
      }
    } map { _.flatten.toMap }
}

object ThrottlingQueryExecutor {
  type QueuesDrainer[R] = ZIO[R, Nothing, Unit]
  type ResolvableResult = Promise[ExecutionFailure, Option[QueryResult]]
  type EnqueuedJob      = (Query, ResolvableResult)

  def apply[R](
      underlying:   QueryExecutor[R]
    , batchSize:    Int
    , timeout:      Duration
    , parallelism:  Int               = 1
  ): UIO[(ThrottlingQueryExecutor, QueuesDrainer[R])] =
    UIO.collectAll {
      ServiceIdentifier.values map { identifier =>
        Queue.unbounded[EnqueuedJob] map { (identifier, _) }
      }
    } map { queues =>
      val consumeAll =
        queues
          .map { case (identifier, queue) =>
            Stream
              .fromQueue(queue)
              .filterM(_._2.isDone map { !_ })
              .batch(batchSize)
              .mapMPar(parallelism) { dequeued =>
                val (queries, promises) = dequeued.unzip

                underlying.execute(identifier, queries.distinct)
                  .foldM(
                      error   => UIO.foreach_(promises)(_.fail(error))
                    , results =>
                        UIO.foreach_(dequeued) { case (query, promise) =>
                          promise.succeed(results.get(query))
                        }
                  )
              }
              .runDrain
          }
          .reduce(_ race _)
          .refailWithTrace

      (new ThrottlingQueryExecutor(queues.toMap, timeout), consumeAll)
    }

  private implicit class BatchingOps[E, T](val stream: Stream[E, T]) extends AnyVal {
    def batch(size: Int): Stream[E, List[T]] =
      (1 until size)
        .map(_ => stream)
        .foldLeft( stream.map(List(_)) ) { (s1, s2) =>
          s1 zip s2 map { case (acc, elem) => elem :: acc }
        }
  }
}

object QueryExecutor {
  def throttlingHttpQueryExecutor(
      host:      String
    , batchSize: Int      = 5
    , timeout:   Duration = 5.seconds
  ) =
    ThrottlingQueryExecutor(
        new HttpQueryExecutor(host, HttpQueryExecutor.httpClient(asyncHttpClient()))
      , batchSize
      , timeout
    )

  sealed trait ExecutionFailure

  final case class RuntimeExecutionFailure(error: Throwable) extends ExecutionFailure
  final case class HttpExecutionFailure(status: Int)         extends ExecutionFailure
}
