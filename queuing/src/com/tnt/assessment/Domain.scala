package com.tnt.assessment

import java.util.Locale

object Domain {
  type ServiceIdentifier = String
  type Query             = String

  final case class CountryCode private(value: String) extends AnyVal

  object CountryCode {
    def fromString(raw: String): Either[String, CountryCode] = {
      val normalized = raw.toUpperCase

      if (Locale.getISOCountries.contains(normalized)) Right(CountryCode(normalized))
      else Left(s"Unexpected country code $normalized")
    }
  }

  final case class OrderNumber private(value: Int) extends AnyVal

  object OrderNumber {
    def fromInt(raw: Int): Either[String, OrderNumber] = {
      val length = math.log10(raw) + 1

      if (length == 9) Right(OrderNumber(raw))
      else Left(s"Illegal order number $raw")
    }
  }
}
