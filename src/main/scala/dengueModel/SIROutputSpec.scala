package dengueModel

import com.bharatsim.engine.Context
import com.bharatsim.engine.graph.patternMatcher.MatchCondition._
import com.bharatsim.engine.listeners.CSVSpecs
import dengueModel.InfectionStatus
import dengueModel.MosquitoInfectionStatus

/**
  * Sets the specifications for the output csv file
  * @param context Current [[Context]] of the simulation.
  *
  * Makes 4 columns names Step, Susceptible, Infected and
  * Removed to store the number of persons in each
  * category at every step.
  */
class SIROutputSpec(context: Context) extends CSVSpecs {
  /**
    * Defines the headers for columns.
    */
  override def getHeaders: List[String] =
    List(
      "Step",
      "Susceptible",
      "Infected",
      "Removed",
      "Mosquito Suscptible",
      "Mosquito Infected",
      "Mosquito Recovered",
      "Mosquito Deceased",
    )

  /**
    * Returns a list of list of information to be put
    * in each row.
    * @returns [[List[List[Any]]]] which is a list of
    *           list of values for each row.
    */
  override def getRows(): List[List[Any]] = {
    val graphProvider = context.graphProvider
    val citizen = "Person"
    val mosquito = "Mosquito"
    val row = List(
      context.getCurrentStep,
      graphProvider.fetchCount(citizen, "infectionState" equ InfectionStatus.Susceptible),
      graphProvider.fetchCount(citizen, "infectionState" equ InfectionStatus.Infected),
      graphProvider.fetchCount(citizen, "infectionState" equ InfectionStatus.Removed),
      graphProvider.fetchCount(mosquito, "infectionState" equ MosquitoInfectionStatus.Susceptible),
      graphProvider.fetchCount(mosquito, "infectionState" equ MosquitoInfectionStatus.Infected),
      graphProvider.fetchCount(mosquito, "infectionState" equ MosquitoInfectionStatus.Removed),
      graphProvider.fetchCount(mosquito, "infectionState" equ MosquitoInfectionStatus.Deceased)
    )
    List(row)
  }
}
