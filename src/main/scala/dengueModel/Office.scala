package dengueModel

import com.bharatsim.engine.models.Network

/**
  * Defines Office network
  * @constructor make instance of office with id
  * @param id [[Long]] Id of the office
  *
  * This office is not special and only defines
  * a network where the contact probability of agents 
  * associated with this node is 1.
  */
case class Office(id: Long) extends Network {
  addRelation[Person]("EMPLOYER_OF")

  /**
    * Returns the contact probability which is 
    * hardcoded as 1.
    */
  override def getContactProbability(): Double = 1
}
