package com.tnt.assessment.api

import com.tnt.assessment.QueuingService.ServiceDataMapping
import com.tnt.assessment.Validation
import enumeratum.EnumEntry.UpperWords
import enumeratum.{CirceEnum, Enum, EnumEntry}

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
