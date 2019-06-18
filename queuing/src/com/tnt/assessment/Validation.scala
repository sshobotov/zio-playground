package com.tnt.assessment

import java.util.Locale

object Validation {
  type ErrorMessage = String
  type Validator[T] = T => Either[ErrorMessage, T]

  val countryCodeValidator: Validator[String] = raw => {
    val normalized = raw.toUpperCase

    if (Locale.getISOCountries.contains(normalized)) Right(normalized)
    else Left(s"Unexpected country code $normalized")
  }

  val orderNumberValidator: Validator[String] = raw => {
    if (raw.length == 9 && raw.forall(_.isDigit)) Right(raw)
    else Left(s"Illegal order number $raw")
  }
}
