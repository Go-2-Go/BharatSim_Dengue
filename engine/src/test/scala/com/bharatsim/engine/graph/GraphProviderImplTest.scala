package com.bharatsim.engine.graph

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GraphProviderImplTest extends AnyWordSpec with Matchers {
  "fetchNode" when {
    "provided with only label" should {
      "return any node with given label" in {
        val graphProvider = new GraphProviderImpl()
        graphProvider.createNode("Person", ("name", "Ramesh"))

        val someNode = graphProvider.fetchNode("Person")
        someNode.isDefined shouldBe true
        someNode.get.apply("name").get shouldBe "Ramesh"
      }

      "return no node if label doesnt exist" in {
        val graphProvider = new GraphProviderImpl()

        val someNode = graphProvider.fetchNode("Person")
        someNode.isDefined shouldBe false
      }
    }

    "provided with label and properties" should {
      "return any node matching with label and properties" in {
        val graphProvider = new GraphProviderImpl()
        graphProvider.createNode("Person", Map(("name", "Suresh"), ("age", 32)))


        val result1 = graphProvider.fetchNode("Person", Map(("name", "Suresh"), ("age", 32)))
        val result2 = graphProvider.fetchNode("Person", Map(("name", "Suresh")))
        val result3 = graphProvider.fetchNode("Person", Map(("age", 32)))

        result1.isDefined shouldBe true
        result2.isDefined shouldBe true
        result3.isDefined shouldBe true
        result1.get.apply("name").get shouldBe "Suresh"
        result2.get.apply("name").get shouldBe "Suresh"
        result3.get.apply("name").get shouldBe "Suresh"
      }

      "return no node if label and properties does not match with any node" in {
        val graphProvider = new GraphProviderImpl()
        graphProvider.createNode("Person", Map(("name", "Suresh"), ("age", 32)))


        val result1 = graphProvider.fetchNode("Person", Map(("name", "Suresh"), ("age", 32)))
        val result2 = graphProvider.fetchNode("Person", Map(("name", "Suresh")))
        val result3 = graphProvider.fetchNode("Person", Map(("age", 32)))

        result1.isDefined shouldBe true
        result2.isDefined shouldBe true
        result3.isDefined shouldBe true
        result1.get.apply("name").get shouldBe "Suresh"
        result2.get.apply("name").get shouldBe "Suresh"
        result3.get.apply("name").get shouldBe "Suresh"
      }
    }
  }

  "fetchNodes" when {
    "provided with only label" should {
      "return all nodes with given label" in {
        val graphProvider = new GraphProviderImpl()

        graphProvider.createNode("Person", ("name", "Ramesh"), ("age", 23))
        graphProvider.createNode("Person", ("name", "Suresh"), ("age", 36))

        val nodes = graphProvider.fetchNodes("Person")
        nodes.size shouldBe 2
      }

      "return empty list if label doesnt exist" in {
        val graphProvider = new GraphProviderImpl()

        graphProvider.createNode("Person", ("name", "Ramesh"), ("age", 23))
        graphProvider.createNode("Person", ("name", "Suresh"), ("age", 36))

        val nodes = graphProvider.fetchNodes("School")
        nodes.size shouldBe 0
      }
    }

    "provided with label and properties" should {
      "return any node matching with label and properties" in {
        val graphProvider = new GraphProviderImpl()

        graphProvider.createNode("Person", ("name", "Ramesh"), ("age", 23))
        graphProvider.createNode("Person", ("name", "Suresh"), ("age", 36))

        val nodes = graphProvider.fetchNodes("Person", ("name", "Ramesh"))
        nodes.size shouldBe 1
        nodes.head.apply("age").get shouldBe 23
      }

      "return no node if label and properties does not match with any node" in {
        val graphProvider = new GraphProviderImpl()

        graphProvider.createNode("Person", ("name", "Ramesh"), ("age", 23))
        graphProvider.createNode("Person", ("name", "Suresh"), ("age", 36))

        val nodes = graphProvider.fetchNodes("Person", ("name", "Aadesh"))
        nodes.size shouldBe 0
      }
    }
  }

  "fetchNeighboursOf" when {
    "node exists" should {
      "return nodes with provided labels" in {
        val graphProvider = new GraphProviderImpl()
        val node1 = graphProvider.createNode("Person", ("name", "Ramesh"))
        val node2 = graphProvider.createNode("Person", ("name", "Suresh"))
        val node3 = graphProvider.createNode("Person", ("name", "Harish"))
        graphProvider.createRelationship(node1, "OWES", node2)
        graphProvider.createRelationship(node1, "OWES", node3)

        val neighbors = graphProvider.fetchNeighborsOf(node1, "OWES")
        neighbors.size shouldBe 2
      }

      "return unique nodes always" in {
        val graphProvider = new GraphProviderImpl()
        val node1 = graphProvider.createNode("Person", ("name", "Ramesh"))
        val node2 = graphProvider.createNode("Person", ("name", "Suresh"))
        val node3 = graphProvider.createNode("Person", ("name", "Harish"))
        graphProvider.createRelationship(node1, "OWES", node2)
        graphProvider.createRelationship(node1, "OWES", node3)
        graphProvider.createRelationship(node1, "LIKES", node3)

        val neighbors = graphProvider.fetchNeighborsOf(node1, "OWES", "LIKES")
        neighbors.size shouldBe 2
      }

      "return no nodes if relationship with label does not exist" in {
        val graphProvider = new GraphProviderImpl()
        val node1 = graphProvider.createNode("Person", ("name", "Ramesh"))
        val node2 = graphProvider.createNode("Person", ("name", "Suresh"))
        val node3 = graphProvider.createNode("Person", ("name", "Harish"))
        graphProvider.createRelationship(node1, "OWES", node2)
        graphProvider.createRelationship(node1, "OWES", node3)

        val neighbors = graphProvider.fetchNeighborsOf(node1, "KNOWS")
        neighbors.size shouldBe 0
      }
    }

    "node does not exist" should {
      "return no nodes" in {
        val graphProvider = new GraphProviderImpl()
        val node1 = graphProvider.createNode("Person", ("name", "Ramesh"))
        val node2 = graphProvider.createNode("Person", ("name", "Suresh"))
        val node3 = graphProvider.createNode("Person", ("name", "Harish"))
        graphProvider.createRelationship(node1, "OWES", node2)
        graphProvider.createRelationship(node1, "OWES", node3)

        val neighbors = graphProvider.fetchNeighborsOf(10, "OWES")
        neighbors.size shouldBe 0
      }
    }
  }

  "updateProps" when {
    "node exists" should {
      "update existing or add new props" in {
        val graphProvider = new GraphProviderImpl()
        val nodeId = graphProvider.createNode("Person", ("name", "Rajesh"))

        graphProvider.updateNode(nodeId, ("name", "Suresh"), ("age", 23))

        val maybeNode = graphProvider.fetchNode("Person", Map(("name", "Suresh")))
        maybeNode.isDefined shouldBe true
        maybeNode.get.apply("age").get shouldBe 23
        maybeNode.get.apply("name").get shouldBe "Suresh"
      }
    }
  }

  "deleteNode" should {
    "remove node from the store" in {
      val graphProvider = new GraphProviderImpl
      val nodeId = graphProvider.createNode("Person", ("name", "Ramesh"))

      graphProvider.fetchNodes("Person").size shouldBe 1

      graphProvider.deleteNode(nodeId)

      graphProvider.fetchNodes("Person").size shouldBe 0
    }
  }

  "deleteNodes" should {
    "remove all nodes matching the label and properties" in {
      val graphProvider = new GraphProviderImpl

      graphProvider.createNode("Person", ("name", "Ramesh"), ("age", 24))
      graphProvider.createNode("Person", ("name", "Suresh"), ("age", 24))
      graphProvider.createNode("Person", ("name", "Harish"), ("age", 24))
      graphProvider.createNode("Person", ("name", "Girish"), ("age", 26))

      graphProvider.deleteNodes("Person", Map(("age", 24)))

      val personList = graphProvider.fetchNodes("Person")
      personList.size shouldBe 1
      personList.head.apply("name").get shouldBe "Girish"
    }
  }

  "deleteRelationship" should {
    "remove relationship between the nodes" in {
      val graphProvider = new GraphProviderImpl
      val from = graphProvider.createNode("Person", ("name", "Ramesh"))
      val to = graphProvider.createNode("Person", ("name", "Suresh"))
      graphProvider.createRelationship(from, "OWES", to)

      graphProvider.fetchNeighborsOf(from, "OWES").size shouldBe 1

      graphProvider.deleteRelationship(from, "OWES", to)

      graphProvider.fetchNeighborsOf(from, "OWES").size shouldBe 0
    }
  }

  "deleteAll" should {
    "remove all the nodes and relationships from the store" in {
      val graphProvider = new GraphProviderImpl
      val node1 = graphProvider.createNode("Person", ("age", 34))
      val node2 = graphProvider.createNode("Person", ("age", 34))
      graphProvider.createNode("Person", ("age", 34))
      graphProvider.createNode("Person", ("age", 34))
      graphProvider.createNode("Person", ("age", 34))
      graphProvider.createRelationship(node1, "OWES", node2)

      graphProvider.deleteAll()

      graphProvider.fetchNodes("Person").size shouldBe 0
      graphProvider.fetchNeighborsOf(node1, "Person").size shouldBe 0
    }
  }
}