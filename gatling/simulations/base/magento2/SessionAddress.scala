package base.magento2

import io.gatling.core.session.Session


class SessionAddress(session: Session, countryId: String = "US", company: String = "") {
  val fullAddress: Map[String, Any] = Map(
    "firstname" -> session("firstname").as[String],
    "lastname" -> session("firstname").as[String],
    "street" -> Array(session("street").as[String]),
    "postcode" -> session("postcode").as[String],
    "region" -> session("region").as[String],
    "region_id" -> session("region_id").as[String],
    "telephone" -> session("telephone").as[String],
    "city" -> session("city").as[String],
    "country_id" -> countryId,
    "company" -> company
  )

  val billingAddress: Map[String, Any] = Map(
    "firstname" -> session("firstname").as[String],
    "lastname" -> session("firstname").as[String],
    "street" -> Array(session("street").as[String]),
    "postcode" -> session("postcode").as[String],
    "region" -> session("region").as[String],
    "region_id" -> session("region_id").as[String],
    "telephone" -> session("telephone").as[String],
    "city" -> session("city").as[String],
    "country_id" -> countryId,
    "company" -> company,
    "saveInAddressBook" -> false
  )

  val shortAddress : Map[String, Any] = Map(
    "postcode" -> session("postcode").as[String],
    "region" -> session("region").as[String],
    "region_id" -> session("region_id").as[String],
    "country_id" -> countryId
  )
}
