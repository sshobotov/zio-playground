package com.tnt.assessment

import Services._
import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.Lowercase
import io.circe.{Encoder, Json}
import io.circe.syntax._

class ShipmentApi {
  import ShipmentApi._

  def translateQuery(payload: Json): Either[ServiceError, List[Query]] =
    payload
      .as[Request]
      .left.map(RequestParsingError(_))
      .map {
        _.map(_.value.toString).toList
      }

  def translateResponse(response: Json): Either[ServiceError, Response] =
    response
      .as[Response]
      .left.map(ResponseParsingError(_))
}

object ShipmentApi {
  type Request  = Set[OrderNumber]
  type Response = Map[OrderNumber, List[Product]]

  sealed trait Product extends EnumEntry with Lowercase

  object Product extends Enum[Product] with CirceEnum[Product] {
    case object Envelope extends Product
    case object Box      extends Product
    case object Pallet   extends Product

    override val values = findValues
  }

  def pipeline(implicit encoder: Encoder[Product]): Pipeline = {
    val api = new ShipmentApi
    Pipeline(api.translateQuery, api.translateResponse(_).map(_.asJson))
  }
}
