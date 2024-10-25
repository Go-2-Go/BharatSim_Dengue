package dengueModel

import com.bharatsim.engine.basicConversions.StringValue
import com.bharatsim.engine.basicConversions.decoders.BasicDecoder
import com.bharatsim.engine.basicConversions.encoders.BasicEncoder

object PersonInfectionStatus extends Enumeration {
  type InfectionStatus = Value
  val Susceptible, Exposed, Asymptomatic, InfectedMild, InfectedSevere, Hospitalized, Recovered, Deceased = Value

  implicit val infectionStatusDecoder: BasicDecoder[InfectionStatus] = {
    case StringValue(v) => withName(v)
    case _              => throw new RuntimeException("Infection status was not stored as a string")
  }

  implicit val infectionStatusEncoder: BasicEncoder[InfectionStatus] = {
    case Susceptible    => StringValue("Susceptible")
    case Exposed        => StringValue("Exposed")
    case Asymptomatic   => StringValue("Asymptomatic")
    case InfectedMild   => StringValue("InfectedMild")
    case InfectedSevere => StringValue("InfectedSevere")
    case Hospitalized   => StringValue("Hospitalized")
    case Deceased       => StringValue("Deceased")
    case Recovered      => StringValue("Recovered")
  }
}

object PersonInfectionSeverity extends Enumeration {
  type InfectionSeverity = Value
  val Mild, Severe = Value

  implicit val infectionSeverityDecoder: BasicDecoder[InfectionSeverity] = {
    case StringValue(v) => withName(v)
    case _              => throw new RuntimeException("Infection severity was not stored as a string")
  }

  implicit val infectionSeverityEncoder: BasicEncoder[InfectionSeverity] = {
    case Mild   => StringValue("Mild")
    case Severe => StringValue("Severe")
  }
}
