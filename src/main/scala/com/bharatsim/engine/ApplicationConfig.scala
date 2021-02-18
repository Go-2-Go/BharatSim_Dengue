package com.bharatsim.engine

import com.bharatsim.engine.distributed.Role
import com.typesafe.config.ConfigFactory

class ApplicationConfig {
  private val config = ConfigFactory.load()
  private lazy val clusterConfig = ConfigFactory.load("cluster.conf")


  val storeActorCount: Int = config.getInt("bharatsim.engine.distributed.data-store-node.actor-count")
  val workerActorCount: Int = config.getInt("bharatsim.engine.distributed.worker-node.actor-count")

  val role: Role.Value = Role.withName(clusterConfig.getStringList("akka.cluster.roles").get(0))

  val executionMode: ExecutionMode = {
    val mode = config.getString("bharatsim.engine.execution.mode")
    mode match {
      case "collection-based" => CollectionBased
      case "actor-based"      => ActorBased
      case "distributed"      => Distributed
      case _                  => NoParallelism
    }
  }

  val simulationSteps: Int = config.getInt("bharatsim.engine.execution.simulation-steps")

  def hasDataStoreRole(): Boolean = {
    executionMode == Distributed && role == Role.DataStore
  }
  def hasEngineMainRole(): Boolean = {
    executionMode == Distributed && role == Role.EngineMain
  }
  def hasWorkerRole(): Boolean = {
    executionMode == Distributed && role == Role.Worker
  }

  val numProcessingActors: Int = config.getInt("bharatsim.engine.execution.actor-based.num-processing-actors")
}
