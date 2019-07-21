package dev.sshobotov
package api

import QueuingService.ServiceDataMapping

object PricingApi {
  type Price = Double

  val dataMapping: ServiceDataMapping =
    ServiceDataMapping.plainValidation[Price](Validation.countryCodeValidator)
}
