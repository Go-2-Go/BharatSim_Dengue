package dengueModel

import com.bharatsim.engine.models.Network

/**
  * Defines School network
  * @constructor make instance of School with id
  * @param id [[Long]] Id of the school
  *
  * This school is not special and only defines
  * a network where the contact probability of agents 
  * associated with this node is 1.
  */
case class School(id: Long) extends Network {
  addRelation[Person]("TEACHES")

  /**
    * Returns the contact probability which is 
    * hardcoded as 1.
    */
  override def getContactProbability(): Double = 1
}
