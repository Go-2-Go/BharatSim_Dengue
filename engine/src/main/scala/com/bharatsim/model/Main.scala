package com.bharatsim.model

import com.bharatsim.engine.ContextBuilder._
import com.bharatsim.engine._
import com.bharatsim.engine.actions.StopSimulation
import com.bharatsim.engine.basicConversions.decoders.DefaultDecoders._
import com.bharatsim.engine.basicConversions.encoders.DefaultEncoders._
import com.bharatsim.engine.dsl.SyntaxHelpers._
import com.bharatsim.engine.graph.{GraphData, Relation}
import com.bharatsim.engine.listners.{CsvOutputGenerator, SimulationListenerRegistry}
import com.bharatsim.engine.models.Agent
import com.typesafe.scalalogging.LazyLogging

object Main extends LazyLogging {
  def main(args: Array[String]): Unit = {
    val config = SimulationConfig(5000)
    implicit val context: Context = Context(Disease, config)

    createSchedules()

    registerAction(StopSimulation, (c: Context) => {
      getInfectedCount(c) == 0 && getSusceptibleCount(c) == 0
    })

    ingestCSVData("src/main/resources/citizen.csv", csvDataExtractor)
    logger.debug("Ingestion done")
    val beforeCount = getInfectedCount(context)

    registerAgent[Person]

    SimulationListenerRegistry.register(
      new CsvOutputGenerator("src/main/resources/output.csv", new SEIROutputSpec(context))
    )

    Simulation.run()

    printStats(beforeCount)

    teardown()
  }

  private def createSchedules()(implicit context: Context): Unit = {
    val employeeScheduleOnWeekDays = (Day, Hour)
      .add[House](0, 8)
      .add[Office](9, 18)
      .add[House](19, 23)

    val employeeScheduleOnWeekEnd = (Day, Hour).add[House](0, 23)

    val employeeSchedule = (Week, Day)
      .add(employeeScheduleOnWeekDays, 0, 4)
      .add(employeeScheduleOnWeekEnd, 5, 6)

    val studentScheduleOnWeekDay = (Day, Hour)
      .add[House](0, 8)
      .add[School](9, 15)
      .add[House](16, 23)

    val studentScheduleOnWeekEnd = (Day, Hour).add[House](0, 23)

    val studentSchedule = (Week, Day)
      .add(studentScheduleOnWeekDay, 0, 4)
      .add(studentScheduleOnWeekEnd, 5, 6)

    registerSchedules(
      (employeeSchedule, (agent: Agent, _: Context) => agent.asInstanceOf[Person].age >= 30),
      (studentSchedule, (agent: Agent, _: Context) => agent.asInstanceOf[Person].age < 30)
    )
  }

  private def csvDataExtractor(map: Map[String, String]): GraphData = {

    val citizenId = map("id").toInt
    val age = map("age").toInt
    val citizen: Person = Person(citizenId, age, InfectionStatus.withName(map("infectionState")), 0)

    val homeId = map("house_id").toInt
    val officeId = map("office_id").toInt
    val schoolId = map("school_id").toInt

    val home = House(homeId)
    val office = Office(officeId)
    val school = School(schoolId)

    val staysAt = Relation[Person, House](citizenId, "STAYS_AT", homeId)
    val worksAt = Relation[Person, Office](citizenId, "WORKS_AT", officeId)
    val studiesAt = Relation[Person, School](citizenId, "STUDIES_AT", schoolId)

    val memberOf = Relation[House, Person](homeId, "HOUSES", citizenId)
    val employerOf = Relation[Office, Person](officeId, "EMPLOYER_OF", citizenId)
    val studentOf = Relation[School, Person](schoolId, "STUDENT_OF", citizenId)

    val graphData = new GraphData()
    graphData.addNode(citizenId, citizen)
    graphData.addNode(homeId, home)
    graphData.addNode(officeId, office)
    graphData.addNode(schoolId, school)

    graphData.addRelations(List(staysAt, worksAt, studiesAt, memberOf, employerOf, studentOf))
    graphData
  }

  private def printStats(beforeCount: Int)(implicit context: Context): Unit = {
    val afterCountSusceptible = getSusceptibleCount(context)
    val afterCountInfected = getInfectedCount(context)
    val afterCountRecovered = context.graphProvider.fetchNodes("Person", ("infectionState", "Recovered")).size
    val afterCountDeceased = context.graphProvider.fetchNodes("Person", ("infectionState", "Deceased")).size

    logger.info("Infected before: {}", beforeCount)
    logger.info("Infected after: {}", afterCountInfected)
    logger.info("Recovered: {}", afterCountRecovered)
    logger.info("Deceased: {}", afterCountDeceased)
    logger.info("Susceptible: {}", afterCountSusceptible)
  }

  private def getSusceptibleCount(context: Context) = {
    context.graphProvider.fetchNodes("Person", ("infectionState", "Susceptible")).size
  }

  private def getInfectedCount(context: Context): Int = {
    context.graphProvider.fetchNodes("Person", ("infectionState", "Infected")).size
  }
}
