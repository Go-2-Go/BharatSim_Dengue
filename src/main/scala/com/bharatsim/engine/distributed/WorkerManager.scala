package com.bharatsim.engine.distributed

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.bharatsim.engine.Context
import com.bharatsim.engine.distributed.SimulationContextReplicator.ContextData
import com.bharatsim.engine.distributed.WorkerManager._
import com.bharatsim.engine.distributed.actors.Barrier.WorkFinished
import com.bharatsim.engine.distributed.actors.DistributedTickLoop.ContextUpdateDone
import com.bharatsim.engine.distributed.actors.WorkDistributorV2.{AckNoWork, ExhaustedFor, FetchWork}
import com.bharatsim.engine.distributed.actors.{Barrier, WorkDistributorV2}
import com.bharatsim.engine.distributed.streams.AgentProcessingStream
import com.bharatsim.engine.execution.AgentExecutor
import com.bharatsim.engine.execution.control.{BehaviourControl, StateControl}
import com.bharatsim.engine.graph.neo4j.BatchWriteNeo4jProvider
import com.typesafe.scalalogging.LazyLogging
import org.neo4j.driver.Bookmark

import scala.util.{Failure, Success}

class WorkerManager(simulationContext: Context) extends LazyLogging {
  def default(): Behavior[Command] =
    Behaviors.receivePartial {
      case (context, message) =>
        message match {
          case Work(label, skip, limit, sender) =>
            context.log.info(
              "Received work for label {} with skip {} for tick {}",
              label,
              skip,
              simulationContext.getCurrentStep
            )

            val nodeIds = simulationContext.graphProvider
              .asInstanceOf[BatchWriteNeo4jProvider]
              .fetchWithStates(label, skip, limit)

            if (nodeIds.nonEmpty) {
              context.log.info(
                "Stream has {} elements for label {} with skip {} for tick {}",
                nodeIds.size,
                label,
                skip,
                simulationContext.getCurrentStep
              )
              val behaviourControl = new BehaviourControl(simulationContext)
              val stateControl = new StateControl(simulationContext)
              val agentExecutor = new AgentExecutor(behaviourControl, stateControl)
              new AgentProcessingStream(label, agentExecutor, simulationContext)(context.system)
                .startWithState(nodeIds)
                .onComplete {
                  case Success(_)  => sender ! FetchWork(context.self)
                  case Failure(ex) => ex.printStackTrace()
                }(context.executionContext)
              Behaviors.same
            } else {
              sender ! ExhaustedFor(label)
              sender ! FetchWork(context.self)
              Behaviors.same
            }

          case NoWork(confirmTo) =>
            confirmTo ! AckNoWork(context.self)
            Behaviors.same

          case StartOfNewTick(updatedContext, bookmarks, replyTo) =>
            simulationContext.setCurrentStep(updatedContext.currentTick)
            simulationContext.setActiveIntervention(updatedContext.activeIntervention)
            simulationContext.perTickCache.clear()
            simulationContext.graphProvider
              .asInstanceOf[BatchWriteNeo4jProvider]
              .setBookmarks(bookmarks)
            replyTo ! ContextUpdateDone()
            Behaviors.same

          case ExecutePendingWrites(replyTo) =>
            simulationContext.graphProvider
              .asInstanceOf[BatchWriteNeo4jProvider]
              .executePendingWrites(context.system)
              .onComplete {
                case Success(bookmark) =>
                  logger.info("Pending writes executed")
                  replyTo ! WorkFinished(DBBookmark(bookmark.values()))
              }(context.executionContext)

            Behaviors.same
        }
    }

  def waitForChildren(distributor: ActorRef[WorkDistributorV2.Command]): Behavior[Command] =
    Behaviors.receivePartial {
      case (context, BarrierReply(_)) =>
        context.log.info("All children finished")
        distributor ! FetchWork(context.self)
        default()
    }
}

object WorkerManager {
  sealed trait Command extends CborSerializable
  case class StartOfNewTick(
      updatedContext: ContextData,
      bookmarks: List[DBBookmark],
      replyTo: ActorRef[ContextUpdateDone]
  ) extends Command
  case class Work(label: String, skip: Int, limit: Int, sender: ActorRef[WorkDistributorV2.Command]) extends Command
  case class NoWork(confirmTo: ActorRef[WorkDistributorV2.Command]) extends Command
  case class ChildrenFinished(distributor: ActorRef[WorkDistributorV2.Command]) extends Command
  case class ExecutePendingWrites(replyTo: ActorRef[Barrier.Request]) extends Command
  case class BarrierReply(barrierMessage: Barrier.Reply) extends Command
}
