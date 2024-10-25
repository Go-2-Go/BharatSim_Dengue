package dengueModel

import com.bharatsim.engine.models.Network

/**
  * Defines House network
  * @constructor make instance of House with id
  * @param id [[Long]] Id of the house
  *
  * This house is not special and only defines
  * a network where the contact probability of agents 
  * associated with this node is 1.
  */
case class House(id: Long) extends Network {
  // Add relation to person.
  addRelation[Person]("HOUSES")
  addRelation[Mosquito]("HOME_TO")

  /**
    * Returns the contact probability which is 
    * hardcoded as 1.
    */
  override def getContactProbability(): Double = 1
}
