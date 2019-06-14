package com.tnt.assessment

import com.tnt.assessment.Domain.{CountryCode, OrderNumber, Query, ServiceIdentifier}
import io.circe.{Decoder, Encoder, Error, Json}

object ServiceApi {
  type ErrorMessage = String
  type Request      = List[RequestEntry]
  type Response     = List[ResponseEntry]

  case class RequestEntry(identifier: ServiceIdentifier, query: Json)

  sealed trait ResponseEntry

  case class SuccessResponseEntry(identifier: ServiceIdentifier, response: Json) extends ResponseEntry
  case class FailureResponseEntry(identifier: ServiceIdentifier, error: ErrorMessage) extends ResponseEntry

  sealed trait ServiceError

  final case class RequestParsingError(error: Error)  extends ServiceError
  final case class ResponseParsingError(error: Error) extends ServiceError

  case class Pipeline[T : Encoder](
      query:  Json => Either[ServiceError, List[Query]]
    , result: Json => Either[ServiceError, T]
  )

  def pipelines =
    Map(
        "shipment" -> ShipmentApi.pipeline
      , "track"    -> TrackApi.pipeline
      , "pricing"  -> PricingApi.pipeline
    )

  implicit val countryCodeEncoder: Encoder[CountryCode] =
    Encoder.encodeString.contramap(_.value)
  implicit val countryCodeDecoder: Decoder[CountryCode] =
    Decoder.decodeString.emap(CountryCode.fromString)

  implicit val orderNumberEncoder: Encoder[OrderNumber] =
    Encoder.encodeInt.contramap(_.value)
  implicit val orderNumberDecoder: Decoder[OrderNumber] =
    Decoder.decodeInt.emap(OrderNumber.fromInt)
}
