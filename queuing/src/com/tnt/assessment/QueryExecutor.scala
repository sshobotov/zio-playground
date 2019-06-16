package com.tnt.assessment

import QueuingService.{Query, BatchResult, ServiceIdentifier}
import QueryExecutor._
import io.circe.parser
import org.asynchttpclient._
import org.asynchttpclient.Dsl._
import zio.{IO, Task}
import zio.interop.javaconcurrent._

class QueryExecutor(host: String, runHttpRequest: RequestExecution) {
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

object QueryExecutor {
  type RequestExecution = Request => Task[Response]

  def httpClient(httpClient: AsyncHttpClient): RequestExecution =
    request => Task.fromFutureJava(IO(httpClient.executeRequest(request)))

  sealed trait ExecutionFailure

  final case class RuntimeExecutionFailure(error: Throwable) extends ExecutionFailure
  final case class HttpExecutionFailure(status: Int)         extends ExecutionFailure
}
