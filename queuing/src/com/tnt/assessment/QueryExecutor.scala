package com.tnt.assessment

import QueuingService.{BatchResult, Query, ServiceIdentifier}
import QueryExecutor._
import io.circe.parser
import org.asynchttpclient._
import org.asynchttpclient.Dsl._
import zio.{IO, Promise, Queue, Task, UIO}
import zio.interop.javaconcurrent._

import scala.annotation.tailrec

trait QueryExecutor {
  def execute(service: ServiceIdentifier, queries: List[Query]): IO[ExecutionFailure, BatchResult]
}

class HttpQueryExecutor(host: String, runHttpRequest: RequestExecution) extends QueryExecutor {
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

class ThrottlingQueryExecutor private(
    queues: Map[ServiceIdentifier, Queue[ThrottlingQueryExecutor.EnqueuedJob]]
) extends QueryExecutor {
  def execute(service: ServiceIdentifier, queries: List[Query]): IO[ExecutionFailure, BatchResult] =
    for {
      promise <- Promise.make[ExecutionFailure, BatchResult]
      _       <- queues(service).offer((queries, promise))
      result  <- promise.await
    } yield result
}

object ThrottlingQueryExecutor {
  type QueuesConsumer   = UIO[Nothing]
  type ResolvableResult = Promise[ExecutionFailure, BatchResult]
  type EnqueuedJob      = (List[Query], ResolvableResult)

  def apply(underlying: QueryExecutor, batchSize: Int): UIO[(ThrottlingQueryExecutor, QueuesConsumer)] =
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

      (new ThrottlingQueryExecutor(queues.toMap), consumeAll)
    }

  implicit class BatchingOps[T](val io: UIO[T]) extends AnyVal {
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
  type RequestExecution = Request => Task[Response]

  def httpClient(httpClient: AsyncHttpClient): RequestExecution =
    request => Task.fromFutureJava(IO(httpClient.executeRequest(request)))

  sealed trait ExecutionFailure

  final case class RuntimeExecutionFailure(error: Throwable) extends ExecutionFailure
  final case class HttpExecutionFailure(status: Int)         extends ExecutionFailure
}
