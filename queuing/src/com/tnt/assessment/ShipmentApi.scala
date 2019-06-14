package com.tnt.assessment

import Domain.{OrderNumber, Query}
import ServiceApi.{Pipeline, RequestParsingError, ResponseParsingError, ServiceError}
import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.Lowercase
import io.circe.{Encoder, Json}

class ShipmentApi {
  import ShipmentApi._

  type Request  = Set[OrderNumber]
  type Response = Map[OrderNumber, List[Product]]

  def translateQuery(payload: Json): Either[ServiceError, List[Query]] =
    payload
      .as[Request]
      .left.map(RequestParsingError.apply)
      .map {
        _.map(_.value.toString).toList
      }

  def translateResponse(response: Json): Either[ServiceError, Response] =
    response
      .as[Response]
      .left.map(ResponseParsingError.apply)
}

object ShipmentApi {
  sealed trait Product extends EnumEntry with Lowercase

  object Product extends Enum[Product] with CirceEnum[Product] {
    case object Envelope extends Product
    case object Box      extends Product
    case object Pallet   extends Product

    override val values = findValues
  }

  def pipeline(implicit encoder: Encoder[Product]): Pipeline[List[Product]] = {
    val api = new ShipmentApi
    Pipeline(api.translateQuery, api.translateResponse)
  }
}
