package dengueModel

import com.bharatsim.engine.Context
import com.bharatsim.engine.basicConversions.decoders.DefaultDecoders._
import com.bharatsim.engine.basicConversions.encoders.DefaultEncoders._
import com.bharatsim.engine.graph.GraphNode
import com.bharatsim.engine.models.{Agent, Node}
import com.bharatsim.engine.utils.Probability.toss
import dengueModel.Parameters._
import dengueModel.MosquitoInfectionStatus._

/**
  * Defines Mosquito class as an extension of [[Agent]].
  *
  * @param id Identification number
  * @param infectionState [[InfectionStatus]] instance 
  *                       to encode current status of 
  *                       infection for Person.
  * @param daysInfected Number of days the Person has 
  *                     been infected.
  */
case class Mosquito(
  id: Long,
  life: Int,
  infectionState: MosquitoInfectionStatus,
  daysInfected: Int
) extends Agent {

  /**
    * Increments daysInfected parameter in the context
    * @param context [[Context]] instance whose parameter is to be incremented.
    */
  private val incrementInfectedDuration: Context => Unit = (context: Context) => {
    if (context.getCurrentStep % numberOfTicksInADay == 0) {
      updateParam("life", life - 1)
      if (isInfected) {
        updateParam("daysInfected", daysInfected + 1)
      }
    }
  }

  /**
    * Checks if [[Mosquito]] is infected and updates the parameter
    * 'infectionState' accordingly.
    *
    * @param context Current [[Context]] of simulation
    */
  private val checkForInfection: Context => Unit = (context: Context) => {
    if (isSusceptible) {
      val infectionRate = beta*dt

      val schedule = context.fetchScheduleFor(this).get

      val currentStep = context.getCurrentStep

      // Get the current place according to the schedule
      val placeType: String = schedule.getForStep(currentStep)

      val places = getConnections(getRelation(placeType).getOrElse("")).toList
      if (places.nonEmpty) {
        // List.head gives a reference to the first element in List
        // Takes only the first place from 'places'. Thus there is 
        // an assumpution inherent that at a time [[Agent]] can 
        // only be at one place according to [[Schedule]].
        val place = places.head
        val decodedPlace = decodeNode(placeType, place)

        var infectedNeighbourCount = decodedPlace.getRelation[Person]() match {
          case Some(relations) => decodedPlace.getConnections(relations).count(x => x.as[Person].isInfected)
          case None => 0
        }


        val toBeInfected = toss(infectionRate, infectedNeighbourCount)

        if (toBeInfected) {
          updateParam("infectionState", Infected)
        }
      }
    }
  }

  /**
    * Checks if this is recovered and update the parameters accordingly.
    * @param context Currnent [[Context]] for the simulation.
    */
  private val checkForRecovery: Context => Unit = (context: Context) => {
    if (isInfected && toss(gamma*dt,1))
      updateParam("infectionState", Removed)
  }

  /**
    * Transition to Deceased for mosquito in case of age end
    * @param context Currnent [[Context]] for the simulation.
    */
  private val checkForDeath: Context => Unit = (context: Context) => {
    if (life <= 0)
      updateParam("infectionState", Deceased)
  }

  def isSusceptible: Boolean = infectionState == Susceptible

  def isInfected: Boolean = infectionState == Infected

  def isRecovered: Boolean = infectionState == Removed

  def isDeceased: Boolean = infectionState == Deceased

  /**
    * Returns [[Node]] according to the given class name.
    * @param classType Name of class
    * @param node [[Node]] which will be decoded according to
    *             classType.
    */
  private def decodeNode(classType: String, node: GraphNode): Node = {
    classType match {
      case "House" => node.as[House]
    }
  }

  /** [[Agent.addBehaviour]] */
  // Add these behaviours to [[Agent]] subclass [[Person]]
  addBehaviour(incrementInfectedDuration)
  addBehaviour(checkForInfection)
  addBehaviour(checkForRecovery)
  addBehaviour(checkForDeath)

  /** [[Node.addRelation]] */
  // Add these relations, the Node to which the relation is defined
  // is in the square brackets.
  addRelation[House]("STAYS_IN")
}
