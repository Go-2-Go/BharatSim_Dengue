package dengueModel

import java.util.Date

import com.bharatsim.engine.ContextBuilder._
import com.bharatsim.engine._
import com.bharatsim.engine.actions.StopSimulation
import com.bharatsim.engine.basicConversions.decoders.DefaultDecoders._
import com.bharatsim.engine.basicConversions.encoders.DefaultEncoders._
import com.bharatsim.engine.dsl.SyntaxHelpers._
import com.bharatsim.engine.execution.Simulation
import com.bharatsim.engine.graph.ingestion.{GraphData, Relation}
import com.bharatsim.engine.graph.patternMatcher.MatchCondition._
import com.bharatsim.engine.listeners.{CsvOutputGenerator, SimulationListenerRegistry}
import com.bharatsim.engine.models.Agent
import com.bharatsim.engine.utils.Probability.biasedCoinToss
import com.bharatsim.engine.distributions.LogNormal
import dengueModel.InfectionStatus._
import dengueModel.Parameters._
import com.typesafe.scalalogging.LazyLogging

object Main extends LazyLogging {
  val mosquitoLifeDistribution = LogNormal(20, 5)
  val mosquitoPerPersonDistribution = LogNormal(10, 10)

  def main(args: Array[String]): Unit = {
    // Count of infected people before starting the simulation.
    var beforeCount = 0

    // Simulation class has a companion object with the apply() method; 
    // hence can be initialised without the 'new' keyword.
    val simulation = Simulation()

    // Signature -> ingestData(f: (Context) => Unit): Unit
    // Here we are passing a function which takes an implicit variable
    // context.
    // Because of the implicit keyword, the function will look for
    // an implicit variable type [[Context]] in the scope.
    simulation.ingestData(implicit context => {
      // The ingestCSVData function in ContextBuilder is also defined 
      // with a implicit parameter. 
      // Thus we can write this as
      // ingestCSVData("src/main/resources/citizen.csv", csvDataExtractor, context)
      ingestCSVData("src/main/resources/citizen.csv", csvDataExtractor)
      logger.debug("Ingestion done")
    })

    // Signature -> defineSimulation(f: Context => Unit): Unit.
    // Like ingestData here also we pass a function which uses implicit context.
    simulation.defineSimulation(implicit context => {
      // Create the different schedules and pass them
      // to ContextBuilder.registerSchedules
      create12HourSchedules()

      // Call ContextBuilder.registerAction
      // Adds a ConditionalAction to the context; 
      // in this case the action is to stop the simulation 
      // when the number of infected people reaches 0.
      registerAction(
        StopSimulation,
        (c: Context) => {
          getInfectedCount(c) == 0
        }
      )

      // Stores the exact number of infected people before the simulation started.
      beforeCount = getInfectedCount(context)

      // Will be expanded to registerAgent[Person]().
      // registerAgent[T] requires a [[BasicMapDecoder[T]]]
      // and a [[Context]] to be passed implicitly.
      registerAgent[Person]
      registerAgent[Mosquito]

      val currentTime = new Date().getTime

      // Add an object which implements SimulationListener to the
      // SimulationListenerRegistry. Here, the object is 
      // CsvOutputGenerator which takes the output file path and 
      // an object which dictates the specifications of the output 
      // csv file; here SIROutputSpec which is defined in SIROutputSpec.scala
      SimulationListenerRegistry.register(
        new CsvOutputGenerator("src/main/resources/output_" + currentTime + ".csv", new SIROutputSpec(context))
      )
    })

    // Signature -> onCompleteSimulation(f: Context => Unit): Unit.
    // Like ingestData here also we pass a function which uses implicit context.
    simulation.onCompleteSimulation { implicit context =>
      printStats(beforeCount)
      // ContextBuilder.teardown() deallocates memory for graceful shutdown
      teardown()
    }

    val startTime = System.currentTimeMillis()

    // Run the simulation
    simulation.run()

    val endTime = System.currentTimeMillis()
    logger.info("Total time: {} s", (endTime - startTime) / 1000)
  }

  /**
    * Creates the schedules which agents have to follow.
    * implicit @param context Current [[Context]] of simulation.
    *
    * Creates 3 schedules; one for persons with age > 18 which 
    * are part of a House and an Office, another for persons
    * with age < 18 which are part of a House and a School network,
    * and lastly; one for quarantined people.
    */
  private def create12HourSchedules()(implicit context: Context): Unit = {
    // myDay and myTick imported from Parameters.scala

    // Since in Parameters.scala we chose the numberOfTicksInADay
    // to be 2, the time goes from 0 to 1. 
    
    // Employees spend the first half of their day in a house
    // and the rest half in Office.
    val employeeSchedule: Schedule = (myDay, myTick)
      .add[House](0, 0)
      .add[Office](1, 1)

    // Students spend the first half of their day in a house
    // and the rest half in School.
    val studentSchedule: Schedule = (myDay, myTick)
      .add[House](0, 0)
      .add[School](1, 1)

    // Quarantined people spend the entire day in quarantine.
    val quarantinedSchedule: Schedule = (myDay, myTick)
      .add[House](0, 1)

    // Mosquito schedule
    val mosquitoSchedule: Schedule = (myDay, myTick)
      .add[House](0, 1)

    // ContextBuilder.registerSchedules registers
    // the various schedules defined here along with a
    // function which tells which agents to associate with
    // the schedule and an integer which indicates the priority.
    registerSchedules(
      (quarantinedSchedule, (agent: Agent, _: Context) => agent.isInstanceOf[Person] && agent.asInstanceOf[Person].isInfected, 1),
      (employeeSchedule, (agent: Agent, _: Context) => agent.isInstanceOf[Person] && agent.asInstanceOf[Person].age >= 18, 2),
      (studentSchedule, (agent: Agent, _: Context) => agent.isInstanceOf[Person] && agent.asInstanceOf[Person].age < 18, 3),
      (mosquitoSchedule, (agent: Agent, _: Context) => agent.isInstanceOf[Mosquito], 4)
    )
  }

  /**
    * Passed to [[ContextBuilder.ingestCSVData]] to extract
    * data from the input csv file.
    * @param map [[String]] to [[String]] mapping which maps
    *            row header to content
    * @param context Current [[Context]]
    * @returns [[GraphData]] instance with the [[Node]] and 
    *          [[Relation]] information
    *
    * Uses information in map to register [[Person]], [[House]],
    * [[Office]] and/or [[School]] nodes and corresponding 
    * [[Relation]]s between these.
    */
  private def csvDataExtractor(map: Map[String, String])(implicit context: Context): GraphData = {

    // Mapping input csv headers from strings to 
    // relevant data types.
    val citizenId = map("Agent_ID").toLong
    val age = map("Age").toInt
    val homeId = map("HHID").toLong
    val schoolId = map("school_id").toLong
    val officeId = map("WorkPlaceID").toLong

    // initialInfectedFraction defined in Parameters.scala
    // Conduct a biased coin toss to determine if person 
    // is infected initially.
    val initialInfectionState = if (biasedCoinToss(initialInfectedFraction)) "Infected" else "Susceptible"


    // Define Person node from the values read.
    val citizen: Person = Person(
      id = citizenId,
      age = age,
      infectionState = InfectionStatus.withName(initialInfectionState),
      daysInfected = 0
    )

    // Define House node
    val home = House(homeId)

    // Define relations between Person and House.
    // Note that we have a directional graph so the relation
    // has to be defined both ways.

    // Relation from Person to House
    val staysAt = Relation[Person, House](citizenId, "STAYS_AT", homeId)
    // Relation from House to Person
    val memberOf = Relation[House, Person](homeId, "HOUSES", citizenId)

    // Add these nodes to the graph
    val graphData = GraphData()
    graphData.addNode(citizenId, citizen)
    graphData.addNode(homeId, home)
    graphData.addRelations(staysAt, memberOf)

    // Define either Office or School depending
    // on the age.
    if (age >= 18) {
      val office = Office(officeId)
      val worksAt = Relation[Person, Office](citizenId, "WORKS_AT", officeId)
      val employerOf = Relation[Office, Person](officeId, "EMPLOYER_OF", citizenId)

      graphData.addNode(officeId, office)
      graphData.addRelations(worksAt, employerOf)
    } else {
      val school = School(schoolId)
      val studiesAt = Relation[Person, School](citizenId, "STUDIES_AT", schoolId)
      val studentOf = Relation[School, Person](schoolId, "STUDENT_OF", citizenId)

      graphData.addNode(schoolId, school)
      graphData.addRelations(studiesAt, studentOf)
    }

    /* Add [[Mosquito]] instances to the simulation */
    val mosquitoId = citizenId * 100
    for ( mosquitoNumber <- 0 to mosquitoPerPersonDistribution.sample().asInstanceOf[Int]) 
      defineMosquito(context, mosquitoId + mosquitoNumber, homeId, graphData) 

    graphData
  }

  /*
   * Defines mosquito belonging to the same house.
   * @param context Current [[Context]]
   * @param id [[Long]] Id of the [[Mosquito]]
   * @param homeID [[Long]] [[House's]] Id to which
   *               [[Mosquito]] belongs
   * @param lat [[String]] latitude
   * @param long [[String]] longitude
   * @param graphData [[GraphData]] instance for the simulation
   */
  def defineMosquito(context: Context, id: Long, homeId: Long, graphData: GraphData): Unit = {
    val initialInfectionState = "Susceptible"// if (biasedCoinToss(initialInfectedFraction)) "Infected" else "Susceptible"
    val mosquito: Mosquito = Mosquito(
      id,
      mosquitoLifeDistribution.sample().asInstanceOf[Int],
      MosquitoInfectionStatus.withName(initialInfectionState),
      0,
      )
    graphData.addNode(id, mosquito)
    val mosquitoStaysAt = Relation[Mosquito, House](id, "STAYS_IN", homeId)
    val mosquitoMemberOf = Relation[House, Mosquito](homeId, "HOME_TO", id)
    graphData.addRelations(mosquitoStaysAt, mosquitoMemberOf)
  }

  /**
    * Logs stats of the simulation.
    * @param beforeCount Number of initially infected people
    *
    * Prints the number of people infected before the simulation
    * started and, the number of infected, recovered and susceptible 
    * persons after simulation has ended.
    */
  private def printStats(beforeCount: Int)(implicit context: Context): Unit = {
    val afterCountSusceptible = getSusceptibleCount(context)
    val afterCountInfected = getInfectedCount(context)
    val afterCountRecovered = getRemovedCount(context)

    logger.info("Infected before: {}", beforeCount)
    logger.info("Infected after: {}", afterCountInfected)
    logger.info("Recovered: {}", afterCountRecovered)
    logger.info("Susceptible: {}", afterCountSusceptible)
  }

  /**
    * Gets the number of susceptible people.
    * @param context [[Context]] from which to fetch information.
    */
  private def getSusceptibleCount(context: Context) = {
    context.graphProvider.fetchCount("Person", "infectionState" equ Susceptible)
  }

  /**
    * Gets the number of infected people.
    * @param context [[Context]] from which to fetch information.
    */
  private def getInfectedCount(context: Context): Int = {
    context.graphProvider.fetchCount("Person", ("infectionState" equ Infected))
  }

  /**
    * Gets the number of removed people.
    * @param context [[Context]] from which to fetch information.
    */
  private def getRemovedCount(context: Context) = {
    context.graphProvider.fetchCount("Person", "infectionState" equ Removed)
  }
}
