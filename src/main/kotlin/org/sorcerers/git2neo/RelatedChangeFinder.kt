package org.sorcerers.git2neo

import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.traversal.Evaluation
import org.neo4j.graphdb.traversal.Evaluators
import org.neo4j.graphdb.traversal.Uniqueness
import java.util.*

class RelatedChangeFinder {
    data class ChangeConnections(val parentsPerChange: Map<Node, Collection<Node>>)

    fun getCommitNodesWithChangedPath(db: GraphDatabaseService, path: String): Collection<Node> {
        val result = HashSet<Node>()

        val changeNodes = db.findNodes(CHANGE, "path", path)

        changeNodes.forEach { result.add(it.getCommit()) }

        return result
    }

    fun getParents(db: GraphDatabaseService, changeNode: Node): List<Node> {
        //find next parents, if any
        val action = changeNode.getAction()
        val parentPath = (if (action == Action.MOVED) changeNode.getOldPath() else changeNode.getPath()) ?: return emptyList()

        val commitNode = changeNode.getCommit()

        val parentCandidates = getCommitNodesWithChangedPath(db, parentPath)

        val parentNodesWithPath = db.traversalDescription()
                .uniqueness(Uniqueness.NODE_GLOBAL)
                .relationships(PARENT, Direction.OUTGOING)
                .evaluator(Evaluators.excludeStartPosition())
                .evaluator {
                    val currentNode = it.endNode()
                    if (currentNode == commitNode) return@evaluator Evaluation.INCLUDE_AND_CONTINUE
                    if (parentCandidates.contains(currentNode)) return@evaluator Evaluation.INCLUDE_AND_PRUNE
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

    fun getChangeConnections(db: GraphDatabaseService, commitNode: Node): ChangeConnections {
        val parentsPerNode: MutableMap<Node, List<Node>> = HashMap()

        commitNode.getChanges().forEach {
            parentsPerNode[it] = getParents(db, it)
        }

        return ChangeConnections(parentsPerNode)
    }
}