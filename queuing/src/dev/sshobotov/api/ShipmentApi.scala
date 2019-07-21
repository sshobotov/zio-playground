package dev.sshobotov
package api

import enumeratum.EnumEntry.Lowercase
import enumeratum.{CirceEnum, Enum, EnumEntry}
import QueuingService.ServiceDataMapping

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
