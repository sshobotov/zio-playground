package com.tnt.assessment

import com.tnt.assessment.api.{PricingApi, ShipmentApi, TrackApi}
import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.Lowercase
import io.circe.{Decoder, Error, Json}
import zio.{IO, ZIO}

object QueuingService {
  import QueryExecutor.ExecutionFailure

  type Requests     = List[RequestEntry]
  type Response     = List[ResponseEntry]
  type Query        = String
  type QueryResult  = Json
  type BatchResult  = Map[Query, QueryResult]
  type ServiceMap   = ServiceIdentifier => ServiceDataMapping

  sealed trait ServiceIdentifier extends EnumEntry with Lowercase

  object ServiceIdentifier extends Enum[ServiceIdentifier] {
    case object Shipment extends ServiceIdentifier
    case object Track    extends ServiceIdentifier
    case object Pricing  extends ServiceIdentifier

    override val values = findValues
  }

  case class RequestEntry(identifier: ServiceIdentifier, query: Query)

  sealed trait ResponseEntry

  case class SuccessResponseEntry(identifier: ServiceIdentifier, response: Option[QueryResult]) extends ResponseEntry
  case class FailureResponseEntry(identifier: ServiceIdentifier, error: ServiceError)           extends ResponseEntry

  sealed trait ServiceError

  final case class RequestParsingError(errors: Validation.ErrorMessage) extends ServiceError
  final case class ResponseParsingError(error: Error)                   extends ServiceError
  final case class RequestExecutionError(error: ExecutionFailure)       extends ServiceError

  // TODO: Decide whether we need data validation/mapping layer or keep our intermediate service dumb
  case class ServiceDataMapping(
      readQuery: Query => Either[Validation.ErrorMessage, Query]
    , mapResult: Json  => Either[Error, QueryResult]
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

  def executeRequests[R](
      requests: Requests
    , services: ServiceMap
    , executor: QueryExecutor[R]
  ): ZIO[R, Nothing, Response] =
    ZIO.collectAllPar(
      requests map { request =>
        executeSingleRequest(request, services(request.identifier), executor)
      }
    )

  private def executeSingleRequest[R](
      request:     RequestEntry
    , dataMapping: ServiceDataMapping
    , executor:    QueryExecutor[R]
  ): ZIO[R, Nothing, ResponseEntry] = {
    val executed =
      for {
        query    <- IO.fromEither(dataMapping.readQuery(request.query)).mapError(RequestParsingError)
        response <- executor.execute(request.identifier, List(query)).mapError(RequestExecutionError)
        result   <-
          IO
            .foreach(response.get(query)) { data =>
              IO.fromEither(dataMapping.mapResult(data))
            }
            .mapError(ResponseParsingError)
      } yield
        SuccessResponseEntry(request.identifier, result.headOption)

    executed.catchAll { reason =>
      IO.succeed(FailureResponseEntry(request.identifier, reason))
    }
  }
}
