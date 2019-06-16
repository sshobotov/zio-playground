package com.tnt.assessment.api

import com.tnt.assessment.QueuingService.ServiceDataMapping
import com.tnt.assessment.Validation
import enumeratum.EnumEntry.Lowercase
import enumeratum.{CirceEnum, Enum, EnumEntry}

object ShipmentApi {
  sealed trait Product extends EnumEntry with Lowercase

  object Product extends Enum[Product] with CirceEnum[Product] {
    case object Envelope extends Product
    case object Box      extends Product
    case object Pallet   extends Product

    override val values = findValues
  }

  val dataMapping: ServiceDataMapping =
    ServiceDataMapping.plainValidation[Product](Validation.orderNumberValidator)
}
