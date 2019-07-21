package dev.sshobotov
package api

import enumeratum.EnumEntry.UpperWords
import enumeratum.{CirceEnum, Enum, EnumEntry}
import QueuingService.ServiceDataMapping

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

  val dataMapping: ServiceDataMapping =
    ServiceDataMapping.plainValidation[Status](Validation.orderNumberValidator)
}
