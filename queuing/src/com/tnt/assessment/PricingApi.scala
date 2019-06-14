package com.tnt.assessment

import Services._
import syntax.MapOps
import io.circe.{Encoder, Json}
import io.circe.syntax._

class PricingApi extends {
  import PricingApi.Price

  type Request  = Set[CountryCode]
  type Response = Map[CountryCode, Price]

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

  private def keyToQuery(key: CountryCode): Query = key.value
}

object PricingApi {
  type Price = Double

  def pipeline(implicit encoder: Encoder[Price]): Pipeline = {
    val api = new PricingApi
    Pipeline(api.translateQuery, api.translateResponse(_, _).map(_.asJson))
  }
}
