package com.bharatsim.engine.distributed

import akka.actor
import akka.actor.Terminated
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.cluster.typed.Cluster
import akka.pattern.retry
import akka.util.Timeout
import com.bharatsim.engine.distributed.Role._
import com.bharatsim.engine.distributed.store.ActorBasedStore
import com.bharatsim.engine.distributed.store.ActorBasedStore.DBQuery
import com.bharatsim.engine.execution.AgentExecutor
import com.bharatsim.engine.execution.control.{BehaviourControl, StateControl}
import com.bharatsim.engine.graph.GraphProviderFactory
import com.bharatsim.engine.{Context, SimulationDefinition}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.Success

object Guardian {
  private val storeServiceKey: ServiceKey[DBQuery] = ServiceKey[DBQuery]("DataStore")
  val workerServiceKey: ServiceKey[DistributedAgentProcessor.Command] =
    ServiceKey[DistributedAgentProcessor.Command]("Worker")

  private def getStoreRef(actorContext: ActorContext[Nothing]): Future[ActorRef[DBQuery]] = {
    val system = actorContext.system
    implicit val seconds: Timeout = 3.seconds
    implicit val scheduler: Scheduler = system.scheduler

    val storeList = Await.result(
      system.receptionist.ask[Receptionist.Listing](replyTo => Receptionist.find(storeServiceKey, replyTo)),
      Duration.Inf
    ) match {
      case storeServiceKey.Listing(listings) => listings
    }

    if (storeList.nonEmpty) {
      Future.successful(storeList.head)
    } else {
      Future.failed(new Exception("Data service not found"));
    }
  }

  private def awaitStoreRef(context: ActorContext[Nothing]): ActorRef[DBQuery] = {
    implicit val scheduler: actor.Scheduler = context.system.scheduler.toClassic
    implicit val executionContext: ExecutionContextExecutor = context.system.executionContext
    val retried = retry(() => getStoreRef(context), 10, 1.second)
    Await.result(retried, Duration.Inf)
  }

  private def start(context: ActorContext[Nothing], simulationDefinition: SimulationDefinition): Unit = {
    val cluster = Cluster(context.system)
    if (cluster.selfMember.hasRole(DataStore.toString)) {
      val actorBasedStore = context.spawn(ActorBasedStore(), "store")
      GraphProviderFactory.initDataStore()
      val simulationContext = Context()
      simulationDefinition.ingestionStep(simulationContext)
      context.system.receptionist ! Receptionist.register(storeServiceKey, actorBasedStore)
    }

    if (cluster.selfMember.hasRole(Worker.toString)) {
      val storeRef = awaitStoreRef(context)
      GraphProviderFactory.init(storeRef, context.system)
      val simulationContext = Context()
      simulationDefinition.simulationBody(simulationContext)
      createWorker(context, simulationContext)
    }

    if(cluster.selfMember.hasRole(EngineMain.toString)) {
      val storeRef = awaitStoreRef(context)
      GraphProviderFactory.init(storeRef, context.system)
      val simulationContext = Context()
      simulationDefinition.simulationBody(simulationContext)
      createMain(context, storeRef, simulationContext)

      context.system.whenTerminated.andThen {
        case Success(_) => simulationDefinition.onComplete(simulationContext)
      }(ExecutionContext.global)
    }
  }

  private def createWorker(context: ActorContext[Nothing], simulationContext: Context): Unit = {
    val behaviourControl = new BehaviourControl(simulationContext)
    val stateControl = new StateControl(simulationContext)
    val agentExecutor = new AgentExecutor(behaviourControl, stateControl)
    context.spawn(SimulationContextSubscriber(simulationContext), "SimulationContextSubscriber")
    val worker = context.spawn(DistributedAgentProcessor(agentExecutor, simulationContext), "Worker")
    context.system.receptionist ! Receptionist.register(workerServiceKey, worker)
  }

  def createMain(context: ActorContext[Nothing], store: ActorRef[DBQuery], simulationContext: Context): Unit = {
    context.spawn(EngineMainActor(store, simulationContext), "EngineMain")
  }

  def apply(simulationDefinition: SimulationDefinition): Behavior[Nothing] =
    Behaviors.setup[Nothing](context => {
      start(context, simulationDefinition)
      Behaviors.receiveMessagePartial[Terminated.type] {
        case Terminated => Behaviors.stopped
      }.narrow[Nothing]
    })
}
