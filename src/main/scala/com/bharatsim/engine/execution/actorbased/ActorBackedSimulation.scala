package com.bharatsim.engine.execution.actorbased

import akka.Done
import akka.actor.typed.ActorSystem
import com.bharatsim.engine.execution.AgentExecutor
import com.bharatsim.engine.execution.actorbased.actors.TickLoop
import com.bharatsim.engine.execution.simulation.{PostSimulationActions, PreSimulationActions}
import com.bharatsim.engine.execution.tick.{PostTickActions, PreTickActions}
import com.bharatsim.engine.{ApplicationConfig, Context}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ActorBackedSimulation(
                             applicationConfig: ApplicationConfig,
                             preSimulationActions: PreSimulationActions,
                             postSimulationActions: PostSimulationActions,
                             preTickActions: PreTickActions,
                             agentExecutor: AgentExecutor,
                             postTickActions: PostTickActions
                           ) extends LazyLogging {
  def run(context: Context): Future[Done] = {
    preSimulationActions.execute()

    val tickLoop = new TickLoop(context, applicationConfig, preTickActions, agentExecutor, postTickActions)
    val actorSystem = ActorSystem(tickLoop.Tick(1), "ticks-loop")
    val executionContext = ExecutionContext.global
    actorSystem.whenTerminated.andThen {
      case Failure(exception) =>
        logger.error("Error occurred while executing simulation using actor system: {}", exception.getMessage)
        postSimulationActions.execute()
      case Success(_) =>
        logger.info("Finished running simulation")
        postSimulationActions.execute()
    }(executionContext)
  }
}