package com.tnt.assessment

import Domain.{CountryCode, Query}
import ServiceApi.{Pipeline, RequestParsingError, ResponseParsingError, ServiceError}
import io.circe.{Encoder, Json}

class PricingApi extends {
  import PricingApi._

  type Request  = Set[CountryCode]
  type Response = Map[CountryCode, Price]

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

object PricingApi {
  type Price = Double

  def pipeline(implicit encoder: Encoder[Price]): Pipeline[Price] = {
    val api = new PricingApi
    Pipeline(api.translateQuery, api.translateResponse)
  }
}
