package com.bharatsim.examples.epidemiology.sir

import com.bharatsim.engine.Context
import com.bharatsim.engine.graph.patternMatcher.MatchCondition._
import com.bharatsim.engine.listeners.CSVSpecs
import com.bharatsim.examples.epidemiology.sir.InfectionStatus.{Susceptible, Infected, Removed}

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
      "Removed"
    )

  /**
    * Returns a list of list of information to be put
    * in each row.
    * @returns [[List[List[Any]]]] which is a list of
    *           list of values for each row.
    */
  override def getRows(): List[List[Any]] = {
    val graphProvider = context.graphProvider
    val label = "Person"
    val row = List(
      context.getCurrentStep,
      graphProvider.fetchCount(label, "infectionState" equ Susceptible),
      graphProvider.fetchCount(label, "infectionState" equ Infected),
      graphProvider.fetchCount(label, "infectionState" equ Removed)
    )
    List(row)
  }
}
