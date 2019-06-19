package com.tnt.assessment

import QueuingService.{BatchResult, Query, QueryResult, ServiceIdentifier}
import QueryExecutor._
import io.circe.{Json, parser}
import org.asynchttpclient._
import org.asynchttpclient.Dsl._
import zio.{IO, Promise, Queue, Task, UIO, ZIO}
import zio.clock.Clock
import zio.duration._
import zio.interop.javaconcurrent._

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
        if (response.getStatusCode < 400)
          IO
            .fromEither {
              parser
                .parse(response.getResponseBody)
                .flatMap(_.as[BatchResult])
            }
            .mapError(RuntimeExecutionFailure(_))
        else
          IO.fail(HttpExecutionFailure(response.getStatusCode))
    } yield json
  }
}

object HttpQueryExecutor {
  type RequestExecution = Request => Task[Response]

  def httpClient(httpClient: AsyncHttpClient): RequestExecution =
    request => Task.fromFutureJava(IO(httpClient.executeRequest(request)))
}

class ThrottlingQueryExecutor private(
    queues:  Map[ServiceIdentifier, Queue[ThrottlingQueryExecutor.EnqueuedJob]]
  , timeout: Duration
) extends QueryExecutor[Clock] {
  def execute(service: ServiceIdentifier, queries: List[Query]): ZIO[Clock, ExecutionFailure, BatchResult] =
    ZIO.collectAllPar {
      queries map { query =>
        for {
          promise <- Promise.make[ExecutionFailure, QueryResult]
          _       <- queues(service).offer(query, promise)
          result  <- promise.await.timeout(timeout)
        } yield query -> result.getOrElse(Json.Null)
      }
    } map { _.toMap }
}

object ThrottlingQueryExecutor {
  type QueuesDrainer[R] = ZIO[R, Nothing, Nothing]
  type ResolvableResult = Promise[ExecutionFailure, QueryResult]
  type EnqueuedJob      = (Query, ResolvableResult)

  def apply[R](
      underlying: QueryExecutor[R]
    , batchSize:  Int
    , timeout:    Duration
  ): UIO[(ThrottlingQueryExecutor, QueuesDrainer[R])] =
    UIO.collectAll {
      ServiceIdentifier.values map { identifier =>
        Queue.unbounded[EnqueuedJob] map { (identifier, _) }
      }
    } map { queues =>
      val consumeAll =
        queues
          .map { case (identifier, queue) =>
            queue.take
              .filterOutAndBatch(_._2.isDone, batchSize)
              .flatMap { dequeued =>
                val (queries, promises) = dequeued.unzip

                underlying.execute(identifier, queries.distinct)
                  .foldM(
                      error   => UIO.foreach_(promises)(_.fail(error))
                    , results =>
                        UIO.foreach_(dequeued) { case (query, promise) =>
                          promise.succeed(results.getOrElse(query, Json.Null))
                        }
                  )
              }
              .forever
          }
          .reduce(_ race _)
          .refailWithTrace

      (new ThrottlingQueryExecutor(queues.toMap, timeout), consumeAll)
    }

  private implicit class BatchingOps[T](val io: UIO[T]) extends AnyVal {
    def filterOutAndBatch(reject: T => UIO[Boolean], size: Int): UIO[List[T]] =
      if (size > 0)
        for {
          value     <- io
          rejected  <- reject(value)
          acc       <-
            if (!rejected)
              filterOutAndBatch(reject, size - 1) map { value :: _ }
            else
              filterOutAndBatch(reject, size)
        } yield acc
      else
        UIO.succeed(Nil)
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
