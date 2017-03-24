package org.sorcerers.git2neo

import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.traversal.Evaluation
import org.neo4j.graphdb.traversal.Evaluators
import org.neo4j.graphdb.traversal.Uniqueness
import java.util.*

class RelatedChangeFinder {
    data class ChangeConnections(val parentsPerChange: Map<Node, Collection<Node>>, val childrenPerChange: Map<Node, Collection<Node>>)

    fun getParents(db: GraphDatabaseService, changeNode: Node): List<Node> {
        //find next parents, if any
        val action = changeNode.getAction()
        val parentPath = if (action == Action.MOVED) changeNode.getOldPath() else changeNode.getPath()

        val commitNode = changeNode.getCommit()

        val parentNodesWithPath = db.traversalDescription()
                .uniqueness(Uniqueness.NODE_GLOBAL)
                .relationships(PARENT, Direction.OUTGOING)
                .evaluator(Evaluators.excludeStartPosition())
                .evaluator {
                    val currentNode = it.endNode()
                    if (currentNode == commitNode) return@evaluator Evaluation.INCLUDE_AND_CONTINUE
                    if (currentNode.getChanges().map { it.getPath() }.contains(parentPath)) return@evaluator Evaluation.INCLUDE_AND_PRUNE
                    return@evaluator Evaluation.EXCLUDE_AND_CONTINUE
                }
                .traverse(commitNode).nodes()

        assert(commitNode !in parentNodesWithPath)
        val parentChangeNodes: MutableList<Node> = ArrayList()
        parentNodesWithPath.forEach {
            val changesWithPath = it.getChanges().filter { it.getPath() == parentPath }
            assert(changesWithPath.size == 1)
            val targetChangeNode = changesWithPath.first()
            parentChangeNodes.add(targetChangeNode)
        }
        return parentChangeNodes
    }

    fun getChildren(db: GraphDatabaseService, changeNode: Node): List<Node> {

        val commitNode = changeNode.getCommit()
        val childPath = changeNode.getPath()
        //find next children, if any
        val childNodesWithPath = db.traversalDescription()
                .uniqueness(Uniqueness.NODE_GLOBAL)
                .relationships(PARENT, Direction.INCOMING)
                .evaluator(Evaluators.excludeStartPosition())
                .evaluator {
                    val currentNode = it.endNode()
                    if (currentNode == commitNode) return@evaluator Evaluation.INCLUDE_AND_CONTINUE
                    if (currentNode.getChanges().map { it.getPath() }.contains(childPath) || currentNode.getChanges().map { it.getOldPath() }.contains(childPath)) return@evaluator Evaluation.INCLUDE_AND_PRUNE
                    return@evaluator Evaluation.EXCLUDE_AND_CONTINUE
                }
                .traverse(commitNode).nodes()

        val childChangeNodes: MutableList<Node> = ArrayList()
        childNodesWithPath.forEach {
            val changesWithPath = it.getChanges().filter { it.getPath() == childPath || it.getOldPath() == childPath }
            assert(changesWithPath.size == 1)
            val targetChangeNode = changesWithPath.first()
            childChangeNodes.add(targetChangeNode)
        }
        return childChangeNodes
    }

    fun getChangeConnections(db: GraphDatabaseService, commitNode: Node): ChangeConnections {
        val parentsPerNode: MutableMap<Node, List<Node>> = HashMap()
        val childrenPerNode: MutableMap<Node, List<Node>> = HashMap()

        commitNode.getChanges().forEach {
            parentsPerNode[it] = getParents(db, it)
//            childrenPerNode[it] = getChildren(db, it)
        }

        return ChangeConnections(parentsPerNode, childrenPerNode)
    }
}