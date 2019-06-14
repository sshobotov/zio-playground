package com.tnt.assessment

import Services._
import enumeratum.{CirceEnum, Enum, EnumEntry}
import enumeratum.EnumEntry.UpperWords
import io.circe.{Encoder, Json}
import io.circe.syntax._

class TrackApi {
  import TrackApi._

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

object TrackApi {
  type Request  = Set[OrderNumber]
  type Response = Map[OrderNumber, Status]

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

  def pipeline(implicit encoder: Encoder[Status]): Pipeline = {
    val api = new TrackApi
    Pipeline(api.translateQuery, api.translateResponse(_).map(_.asJson))
  }
}
