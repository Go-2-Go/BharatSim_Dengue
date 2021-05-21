package com.bharatsim.engine.distributed.worker

import akka.Done
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox}
import akka.actor.typed.ActorSystem
import akka.actor.typed.receptionist.Receptionist
import com.bharatsim.engine.Context
import com.bharatsim.engine.distributed.engineMain.Barrier.WorkFinished
import com.bharatsim.engine.distributed.engineMain.DistributedTickLoop.StartOfNewTickAck
import com.bharatsim.engine.distributed.engineMain.WorkDistributor.{AgentLabelExhausted, FetchWork}
import com.bharatsim.engine.distributed.engineMain.{Barrier, DistributedTickLoop, WorkDistributor}
import com.bharatsim.engine.distributed.worker.WorkerActor.{ExecutePendingWrites, StartOfNewTick, Work, workerServiceId}
import com.bharatsim.engine.distributed.{ContextData, DBBookmark}
import com.bharatsim.engine.execution.SimulationDefinition
import com.bharatsim.engine.graph.GraphNode
import com.bharatsim.engine.graph.neo4j.BatchNeo4jProvider
import org.mockito.Mockito.clearInvocations
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

class WorkerActorTest
    extends AnyFunSpec
    with MockitoSugar
    with BeforeAndAfterEach
    with Matchers
    with ArgumentMatchersSugar {

  val mockGraphProvider = mock[BatchNeo4jProvider]

  val ingestionStep = spyLambda((context: Context) => {})
  val body = spyLambda((context: Context) => {})
  val onComplete = spyLambda((context: Context) => {})
  val simDef = SimulationDefinition(ingestionStep, body, onComplete)

  val mockAgentProcessor = mock[DistributedAgentProcessor]
  val context = Context(mockGraphProvider)
  val agentLabel = "testLabel"
  val skip = 0
  val limit = 10
  val agentWithState = List((mock[GraphNode], Some(mock[GraphNode])))
  override def beforeEach() = {
    when(mockGraphProvider.fetchWithStates(agentLabel, skip, limit)).thenReturn(agentWithState)

    when(mockAgentProcessor.process(any, any, any)(any)).thenReturn(Future.successful(Done))

  }
  override def afterEach(): Unit = {
    clearInvocations(ingestionStep)
    clearInvocations(body)
    clearInvocations(onComplete)
    reset(mockGraphProvider)
    reset(mockAgentProcessor)
  }

  describe("Start") {

    it("should register self with receptionist") {
      val workerActor = new WorkerActor(mockAgentProcessor).start(simDef, context)
      val workerTestKit = BehaviorTestKit(workerActor)
      workerTestKit.receptionistInbox().expectMessage(Receptionist.register(workerServiceId, workerTestKit.ref))
    }

    it("should only execute simulation body") {
      val workerActor = new WorkerActor(mockAgentProcessor).start(simDef, context)

      BehaviorTestKit(workerActor)

      verify(ingestionStep, never)(any[Context])
      verify(onComplete, never)(any[Context])
      verify(body)(context)
    }
  }
  describe("worker behaviour") {

    describe("Work") {
      it("should execute given work") {
        val workDistributorInbox = TestInbox[WorkDistributor.Command]()
        val workerActor = new WorkerActor(mockAgentProcessor).start(simDef, context)
        val workerTestKit = BehaviorTestKit(workerActor)
        workerTestKit.run(Work(agentLabel, skip, limit, workDistributorInbox.ref))
        verify(mockAgentProcessor).process(eqTo(agentWithState), eqTo(context), any[DistributedAgentExecutor])(
          any[ActorSystem[_]]
        )
        workDistributorInbox.expectMessage(FetchWork(workerTestKit.ref))
      }

      it("should request next work when there are no agent left for current label") {
        val exhaustedLabel = "exhaustedLabel"
        val workDistributorInbox = TestInbox[WorkDistributor.Command]()
        val workerActor = new WorkerActor(mockAgentProcessor).start(simDef, context)
        when(mockGraphProvider.fetchWithStates(exhaustedLabel, skip, limit)).thenReturn(List.empty)

        val workerTestKit = BehaviorTestKit(workerActor)

        workerTestKit.run(Work(exhaustedLabel, skip, limit, workDistributorInbox.ref))

        verify(mockAgentProcessor, never).process(any, any, any)(any[ActorSystem[_]])
        workDistributorInbox.expectMessage(AgentLabelExhausted(exhaustedLabel))
        workDistributorInbox.expectMessage(FetchWork(workerTestKit.ref))
      }
    }
    describe("StartOfNewTick") {

      it("should update context and bookmark") {
        val tickLoop = TestInbox[DistributedTickLoop.StartOfNewTickAck]()
        val workerActor = new WorkerActor(mockAgentProcessor).start(simDef, context)
        val workerTestKit = BehaviorTestKit(workerActor)
        val contextData = ContextData(10, Set("test"))
        val bookmarks = List(DBBookmark(java.util.Set.of("BK1")))
        workerTestKit.run(StartOfNewTick(contextData, bookmarks, tickLoop.ref))
        tickLoop.expectMessage(StartOfNewTickAck())
        verify(mockGraphProvider).setBookmarks(bookmarks)
        context.getCurrentStep shouldBe contextData.currentTick
        context.activeInterventionNames shouldBe contextData.activeIntervention
      }
    }

    describe("Execute Writes") {
      it("should execute pending writes and reply with bookmarks") {
        val barrier = TestInbox[Barrier.Request]()
        val workerActor = new WorkerActor(mockAgentProcessor).start(simDef, context)
        val bookmark = DBBookmark(java.util.Set.of("BK1"))

        when(mockGraphProvider.executePendingWrites(any)).thenReturn(Future.successful(bookmark))
        val workerTestKit = BehaviorTestKit(workerActor)

        workerTestKit.run(ExecutePendingWrites(barrier.ref))
        verify(mockGraphProvider).executePendingWrites()
        barrier.expectMessage(WorkFinished(Some(bookmark)))
      }
    }

  }

}
