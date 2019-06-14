package com.tnt.assessment

import Services._
import syntax.MapOps
import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.Lowercase
import io.circe.{Encoder, Json}
import io.circe.syntax._

class ShipmentApi {
  import ShipmentApi.Product

  type Request  = Set[OrderNumber]
  type Response = Map[OrderNumber, List[Product]]

  def translateQuery(payload: Json): Either[ServiceError, List[Query]] =
    payload
      .as[Request]
      .left.map(RequestParsingError(_))
      .map {
        _.map(keyToQuery).toList
      }

  def translateResponse(response: Json, queried: List[Query]): Either[ServiceError, Response] =
    response
      .as[Response]
      .left.map(ResponseParsingError(_))
      .map(_.guaranteeKeysSeq(queried, keyToQuery))

  private def keyToQuery(key: OrderNumber): Query = key.value.toString
}

object ShipmentApi {
  sealed trait Product extends EnumEntry with Lowercase

  object Product extends Enum[Product] with CirceEnum[Product] {
    case object Envelope extends Product
    case object Box      extends Product
    case object Pallet   extends Product

    override val values = findValues
  }

  def pipeline(implicit encoder: Encoder[Product]): Pipeline = {
    val api = new ShipmentApi
    Pipeline(api.translateQuery, api.translateResponse(_, _).map(_.asJson))
  }
}
