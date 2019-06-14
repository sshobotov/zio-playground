package com.tnt.assessment

import Services._
import io.circe.{Encoder, Json}
import io.circe.syntax._

class PricingApi extends {
  import PricingApi._

  def translateQuery(payload: Json): Either[ServiceError, List[Query]] =
    payload
      .as[Request]
      .left.map(RequestParsingError(_))
      .map {
        _.map(_.value.toString).toList
      }

  def translateResponse(response: Json): Either[ServiceError, Response] =
    response
      .as[Map[CountryCode, Double]]
      .left.map(ResponseParsingError(_))
}

object PricingApi {
  type Price = Double

  type Request  = Set[CountryCode]
  type Response = Map[CountryCode, Price]

  def pipeline(implicit encoder: Encoder[Price]): Pipeline = {
    val api = new PricingApi
    Pipeline(api.translateQuery, api.translateResponse(_).map(_.asJson))
  }
}
