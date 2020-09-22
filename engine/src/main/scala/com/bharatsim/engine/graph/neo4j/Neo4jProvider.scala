package com.bharatsim.engine.graph.neo4j

import java.nio.file.Path
import java.util

import com.bharatsim.engine.graph.GraphProvider.NodeId
import com.bharatsim.engine.graph.{GraphNode, GraphNodeImpl, GraphProvider}
import com.typesafe.scalalogging.LazyLogging
import org.neo4j.driver.Values.{parameters, value}
import org.neo4j.driver.{AuthTokens, GraphDatabase, Transaction}

import scala.jdk.CollectionConverters.{MapHasAsJava, MapHasAsScala}


class Neo4jProvider(config: Neo4jConfig) extends GraphProvider with LazyLogging {
  private val neo4jConnection = config.username match {
    case Some(_) => GraphDatabase.driver(config.uri, AuthTokens.basic(config.username.get, config.password.get))
    case None => GraphDatabase.driver(config.uri)
  }

  def close(): Unit = {
    neo4jConnection.close()
  }

  override def createNode(label: String, props: Map[String, Any]): NodeId = {
    val session = neo4jConnection.session()

    val nodeId = session.writeTransaction((tx: Transaction) => {
      val javaMap = new util.HashMap[String, java.lang.Object]()
      props.foreach(kv => javaMap.put(kv._1, kv._2.asInstanceOf[java.lang.Object]))

      val result = tx.run(
        s"CREATE (n:$label) SET n=$$props return id(n) as nodeId",
        parameters("props", javaMap))

      result.next().get("nodeId").asInt()
    })

    session.close()
    nodeId
  }

  override def createNode(label: String, props: (String, Any)*): NodeId = createNode(label, props.toMap)

  override def createRelationship(node1: NodeId, label: String, node2: NodeId): Unit = {
    val session = neo4jConnection.session()

    try {
      session.writeTransaction((tx: Transaction) => {
        tx.run(
          s"""
             |OPTIONAL MATCH (node1) WHERE id(node1) = $$nodeId1
             |OPTIONAL MATCH (node2) WHERE id(node2) = $$nodeId2
             |CREATE (node1)-[:$label]-> (node2)
             |""".stripMargin,
          parameters("nodeId1", node1, "nodeId2", node2))
      })
    } catch {
      case e: Exception => logger.error(s"Failed to create relation '{}' due to reason -> {}", label, e.getMessage)
    } finally {
      session.close()
    }
  }

  override def ingestNodes(csvPath: Path): Unit = ???

  override def ingestRelationships(csvPath: Path): Unit = ???

  override def fetchNode(label: String, params: Map[String, Any]): Option[GraphNode] = {
    val session = neo4jConnection.session()
    val matchCriteria = toMatchCriteria(params)

    session.readTransaction((tx: Transaction) => {
      val paramsMapJava = params.map(kv => (kv._1, value(kv._2))).asJava

      val result = tx.run(
        s"MATCH (n:$label) where $matchCriteria return properties(n) as node, id(n) as nodeId",
        value(paramsMapJava)
      )

      if (result.hasNext) {
        val record = result.next()
        val node = record.get("node").asMap()
        val nodeId = record.get("nodeId").asInt()

        val mapWithValueTypeAny = node.asScala.map(kv => (kv._1, kv._2.asInstanceOf[Any])).toMap
        val graphNode = new GraphNodeImpl(label, nodeId, mapWithValueTypeAny)
        Some(graphNode)
      } else {
        None
      }
    })
  }

  private def toMatchCriteria(params: Map[String, Any]): String = {
    params.keys.map(key => s"n.$key = $$$key").mkString(" and ")
  }

  override def fetchNodes(label: String, params: Map[String, Any]): Iterable[GraphNode] = ???

  override def fetchNodes(label: String, params: (String, Any)*): Iterable[GraphNode] = ???

  override def fetchNeighborsOf(nodeId: NodeId, label: String, labels: String*): Iterable[GraphNode] = ???

  override def updateNode(nodeId: NodeId, props: Map[String, Any]): Unit = ???

  override def updateNode(nodeId: NodeId, prop: (String, Any), props: (String, Any)*): Unit = ???

  override def deleteNode(nodeId: NodeId): Unit = ???

  override def deleteRelationship(from: NodeId, label: String, to: NodeId): Unit = ???

  override def deleteNodes(label: String, props: Map[String, Any]): Unit = ???

  override def deleteAll(): Unit = ???
}
