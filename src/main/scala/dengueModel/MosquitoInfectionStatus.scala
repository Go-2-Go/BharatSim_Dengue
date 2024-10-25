package dengueModel

import com.bharatsim.engine.basicConversions.StringValue
import com.bharatsim.engine.basicConversions.decoders.BasicDecoder
import com.bharatsim.engine.basicConversions.encoders.BasicEncoder

/**
  * Stores information about the state a mosquito is in.
  */
object MosquitoInfectionStatus extends Enumeration {
  type MosquitoInfectionStatus = Value
  val Susceptible, Infected, Removed, Deceased = Value

  implicit val infectionStatusDecoder: BasicDecoder[MosquitoInfectionStatus] = {
    case StringValue(v) => withName(v)
    case _ => throw new RuntimeException("Infection status was not stored as a string")
  }

  implicit val infectionStatusEncoder: BasicEncoder[MosquitoInfectionStatus] = {
    case Susceptible => StringValue("Susceptible")
    case Infected => StringValue("Infected")
    case Removed => StringValue("Removed")
    case Deceased => StringValue("Deceased")
  }
}
