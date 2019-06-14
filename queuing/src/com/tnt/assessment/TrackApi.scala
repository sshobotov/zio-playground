package com.tnt.assessment

import Domain.{OrderNumber, Query}
import ServiceApi.{Pipeline, RequestParsingError, ResponseParsingError, ServiceError}
import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.UpperWords
import io.circe.{Encoder, Json}

class TrackApi {
  import TrackApi._

  type Request  = Set[OrderNumber]
  type Response = Map[OrderNumber, Status]

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

object TrackApi {
  sealed trait Status extends EnumEntry with UpperWords

  object Status extends Enum[Status] with CirceEnum[Status] {
    case object New         extends Status
    case object InTransit   extends Status
    case object Collecting  extends Status
    case object Collected   extends Status
    case object Delivering  extends Status
    case object Delivered   extends Status

    override val values = findValues
  }

  def pipeline(implicit encoder: Encoder[Status]): Pipeline[Status] = {
    val api = new TrackApi
    Pipeline(api.translateQuery, api.translateResponse)
  }
}
