package com.tnt.assessment

import QueuingService.{BatchResult, Query, ServiceIdentifier}
import QueryExecutor._
import io.circe.parser
import org.asynchttpclient._
import org.asynchttpclient.Dsl._
import zio.{IO, Promise, Queue, Task, UIO, ZIO}
import zio.clock.Clock
import zio.duration.Duration
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
  , timeout: Duration.Finite
) extends QueryExecutor[Clock] {
  def execute(service: ServiceIdentifier, queries: List[Query]): ZIO[Clock, ExecutionFailure, BatchResult] =
    for {
      promise <- Promise.make[ExecutionFailure, BatchResult]
      _       <- queues(service).offer((queries, promise))
      result  <- promise.await.timeout(timeout)
    } yield
      result.getOrElse(Map.empty)
}

object ThrottlingQueryExecutor {
  type QueuesConsumer[R]  = ZIO[R, Nothing, Nothing]
  type ResolvableResult   = Promise[ExecutionFailure, BatchResult]
  type EnqueuedJob        = (List[Query], ResolvableResult)

  def apply[R](
      underlying: QueryExecutor[R]
    , batchSize:  Int
    , timeout:    Duration.Finite
  ): UIO[(ThrottlingQueryExecutor, QueuesConsumer[R])] =
    UIO.collectAll {
      ServiceIdentifier.values map { identifier =>
        Queue.unbounded[EnqueuedJob] map { (identifier, _) }
      }
    } map { queues =>
      val consumeAll =
        queues
          .map { case (identifier, queue) =>
            queue.take
              .batched(batchSize)
              .flatMap { dequeued =>
                val (queries, promises) = dequeued.unzip

                underlying.execute(identifier, queries.flatten.distinct)
                  .foldM(
                      error   => UIO.foreach_(promises)(_.fail(error))
                    , results =>
                        UIO.foreach_(dequeued) { case (query, promise) =>
                          promise.succeed(results.filterKeys(query.contains))
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
    def batched(size: Int): UIO[List[T]] =
      if (size > 0)
        for {
          value <- io
          rest  <- batched(size - 1)
        } yield value :: rest
      else
        UIO.succeed(Nil)
  }
}

object QueryExecutor {
  def throttlingHttpQueryExecutor(
      host:      String
    , batchSize: Int
    , timeout:   Duration.Finite
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
