package com.tnt.assessment.api

import com.tnt.assessment.QueuingService.ServiceDataMapping
import com.tnt.assessment.Validation

object PricingApi {
  type Price = Double

  val dataMapping: ServiceDataMapping =
    ServiceDataMapping.plainValidation[Price](Validation.countryCodeValidator)
}
