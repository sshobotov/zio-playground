package com.tnt.assessment

import java.util.Locale

import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.Lowercase
import io.circe.{Decoder, Encoder, Error, Json}
import zio.{IO, UIO}

object Services {
  import QueryExecutor.{ExecutionFailure, HttpExecutionFailure, RuntimeExecutionFailure}

  type Requests     = List[RequestEntry]
  type Response     = List[ResponseEntry]
  type Query        = String
  type PipelineMap  = ServiceIdentifier => Pipeline

  sealed trait ServiceIdentifier extends EnumEntry with Lowercase

  object ServiceIdentifier extends Enum[ServiceIdentifier] with CirceEnum[ServiceIdentifier] {
    case object Shipment extends ServiceIdentifier
    case object Track    extends ServiceIdentifier
    case object Pricing  extends ServiceIdentifier

    override val values = findValues
  }

  case class RequestEntry(identifier: ServiceIdentifier, query: Json)

  sealed trait ResponseEntry

  case class SuccessResponseEntry(identifier: ServiceIdentifier, response: Json) extends ResponseEntry
  case class FailureResponseEntry(identifier: ServiceIdentifier, error: String)  extends ResponseEntry

  sealed trait ServiceError

  final case class RequestParsingError(error: Error)              extends ServiceError
  final case class ResponseParsingError(error: Error)             extends ServiceError
  final case class RequestExecutionError(error: ExecutionFailure) extends ServiceError

  final case class CountryCode private(value: String) extends AnyVal

  object CountryCode {
    def fromString(raw: String): Either[String, CountryCode] = {
      val normalized = raw.toUpperCase

      if (Locale.getISOCountries.contains(normalized)) Right(CountryCode(normalized))
      else Left(s"Unexpected country code $normalized")
    }
  }

  final case class OrderNumber private(value: Int) extends AnyVal

  object OrderNumber {
    def fromInt(raw: Int): Either[String, OrderNumber] = {
      val length = math.log10(raw) + 1

      if (length == 9) Right(OrderNumber(raw))
      else Left(s"Illegal order number $raw")
    }
  }

  case class Pipeline(
      readQueries: Json => Either[ServiceError, List[Query]]
    , mapResults:  Json => Either[ServiceError, Json]
  )

  def pipelineMap: PipelineMap = {
    case ServiceIdentifier.Shipment => ShipmentApi.pipeline
    case ServiceIdentifier.Track    => TrackApi.pipeline
    case ServiceIdentifier.Pricing  => PricingApi.pipeline
  }

  def executeRequests(
      requests:  Requests
    , pipelines: PipelineMap
    , executor:  QueryExecutor
  ): UIO[Response] =
    UIO.collectAllPar(
      requests map { request =>
        executeSingleRequest(request, pipelines(request.identifier), executor)
      }
    )

  private def executeSingleRequest(
      request:  RequestEntry
    , pipeline: Pipeline
    , executor: QueryExecutor
  ): UIO[ResponseEntry] = {
    val executed =
      for {
        query    <- IO.fromEither(pipeline.readQueries(request.query))
        response <- executor.execute(request.identifier, query).mapError(RequestExecutionError)
        result   <- IO.fromEither(pipeline.mapResults(response))
      } yield
        SuccessResponseEntry(request.identifier, result)

    executed.catchAll { reason =>
        val message = reason match {
          case RequestParsingError(err)                            => s"Bad data provided: $err"
          case ResponseParsingError(err)                           => s"Unexpected response data: $err"
          case RequestExecutionError(HttpExecutionFailure(status)) => s"Unexpected response status: $status"
          case RequestExecutionError(RuntimeExecutionFailure(err)) => s"Internal error: $err"
        }
        IO.succeed(FailureResponseEntry(request.identifier, message))
      }
  }

  implicit val countryCodeEncoder: Encoder[CountryCode] =
    Encoder.encodeString.contramap(_.value)
  implicit val countryCodeDecoder: Decoder[CountryCode] =
    Decoder.decodeString.emap(CountryCode.fromString)

  implicit val orderNumberEncoder: Encoder[OrderNumber] =
    Encoder.encodeInt.contramap(_.value)
  implicit val orderNumberDecoder: Decoder[OrderNumber] =
    Decoder.decodeInt.emap(OrderNumber.fromInt)
}
