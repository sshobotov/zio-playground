package com.tnt.assessment

import com.tnt.assessment.api.{PricingApi, ShipmentApi, TrackApi}
import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.Lowercase
import io.circe.{Decoder, Error, Json}
import zio.{IO, UIO}

object QueuingService {
  import QueryExecutor.ExecutionFailure

  type Requests     = List[RequestEntry]
  type Response     = List[ResponseEntry]
  type Query        = String
  type BatchResult  = Map[Query, Json]
  type ServiceMap   = ServiceIdentifier => ServiceDataMapping

  sealed trait ServiceIdentifier extends EnumEntry with Lowercase

  object ServiceIdentifier extends Enum[ServiceIdentifier] {
    case object Shipment extends ServiceIdentifier
    case object Track    extends ServiceIdentifier
    case object Pricing  extends ServiceIdentifier

    override val values = findValues
  }

  case class RequestEntry(identifier: ServiceIdentifier, queries: List[Query])

  sealed trait ResponseEntry

  case class SuccessResponseEntry(identifier: ServiceIdentifier, response: BatchResult) extends ResponseEntry
  case class FailureResponseEntry(identifier: ServiceIdentifier, error: ServiceError)   extends ResponseEntry

  sealed trait ServiceError

  final case class RequestParsingError(errors: List[Validation.ErrorMessage]) extends ServiceError
  final case class ResponseParsingError(error: Error)                         extends ServiceError
  final case class RequestExecutionError(error: ExecutionFailure)             extends ServiceError

  // TODO: Decide whether we need data validation/mapping layer or keep our intermediate service dumb
  case class ServiceDataMapping(
      readQuery: Query => Either[Validation.ErrorMessage, Query]
    , mapResult: Json  => Either[Error, Json]
  )

  object ServiceDataMapping {
    def plainValidation[T: Decoder](validateQuery: Validation.Validator[Query]): ServiceDataMapping =
      new ServiceDataMapping(
          validateQuery
        , json  => implicitly[Decoder[T]].decodeJson(json).map(_ => json)
      )
  }

  def serviceMap: ServiceMap = {
    case ServiceIdentifier.Shipment => ShipmentApi.dataMapping
    case ServiceIdentifier.Track    => TrackApi.dataMapping
    case ServiceIdentifier.Pricing  => PricingApi.dataMapping
  }

  def executeRequests(
      requests: Requests
    , services: ServiceMap
    , executor: QueryExecutor
  ): UIO[Response] =
    UIO.collectAllPar(
      requests map { request =>
        executeSingleRequest(request, services(request.identifier), executor)
      }
    )

  private def executeSingleRequest(
      request:     RequestEntry
    , dataMapping: ServiceDataMapping
    , executor:    QueryExecutor
  ): UIO[ResponseEntry] = {
    val executed =
      for {
        queries  <- IO.fromEither(validateQueries(request.queries, dataMapping)).mapError(RequestParsingError)
        response <- executor.execute(request.identifier, queries).mapError(RequestExecutionError)
        result   <- IO.collectAll(mapResults(response, dataMapping)).mapError(ResponseParsingError)
      } yield
        SuccessResponseEntry(request.identifier, result.toMap)

    executed.catchAll { reason =>
      IO.succeed(FailureResponseEntry(request.identifier, reason))
    }
  }

  private def validateQueries(queries: List[Query], dataMapping: ServiceDataMapping) =
    Validation.accumulated(queries.map(dataMapping.readQuery))

  private def mapResults(results: BatchResult, dataMapping: ServiceDataMapping) =
    results map { case (query, data) =>
      IO.fromEither(dataMapping.mapResult(data).map((query, _)))
    }
}
