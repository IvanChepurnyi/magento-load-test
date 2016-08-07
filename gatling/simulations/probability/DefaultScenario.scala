package probability

object DefaultScenario {

  // 50% browsing
  // 10% checkout
  // 40% lost shopping carts
  val rate = new GeneralProbabilityRate(50d, 10d, 40d)

  def calculateProbability(csvFileName: String): Probability = csvFileName match {
    // In original set it is extremely unlikely that customer even finds configurable product
    // as well as customer can find a configurable product
    case "product_simple_original" => new Probability(0.5d, rate)

    // In large set it is unlikely that customer checkouts configurable product
    // as well as customer can find a configurable product, but chances are higher than in original dataset
    case "product_simple_large" => new Probability(0.375d, rate)

    // In default it is 50% checkout chance of configurable and simple products
    // As well as finding them
    case _ => new Probability(0.5d, rate)
  }
}