package probability

/**
  * General probability class
  *
  * @param ratio
  * @param generalProbabilityRate
  */
class Probability(val ratio: Double, val generalProbabilityRate: GeneralProbabilityRate)
{
  def calculateRatio(productType: String, percent: Double): Double = productType match {
    case "configurable" => percent * (1d - ratio)
    case _ => percent * ratio
  }

  def browseRatio(productType: String, multiplier: Double = 1): Double = {
    calculateRatio(productType, generalProbabilityRate.browse * multiplier)
  }

  def abandonmentRatio(productType: String, multiplier: Double = 1): Double = {
    calculateRatio(productType, generalProbabilityRate.abandonment * multiplier)
  }

  def checkoutRatio(productType: String, multiplier: Double = 1): Double = {
    calculateRatio(productType, generalProbabilityRate.checkout * multiplier)
  }
}