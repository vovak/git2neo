package org.sorcerers.git2neo

import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.traversal.Evaluation
import org.neo4j.graphdb.traversal.Evaluators
import org.neo4j.graphdb.traversal.Uniqueness
import java.util.*

class RelatedChangeFinder (val db: GraphDatabaseService) {
    data class ChangeConnections(val parentsPerChange: Map<Node, Collection<Node>>)

    val pathNodesCache: MutableMap<String, Collection<Long>> = HashMap()

    fun findCommitById(id: String): Node {
        return db.findNode(COMMIT, "id", id)
    }

    fun getCommitNodesWithChangedPath(path: String): Collection<Long> {
        if (pathNodesCache.containsKey(path)) return pathNodesCache[path]!!
        val result = HashSet<Long>()

        val changeNodes = db.findNodes(CHANGE, "path", path)
        changeNodes.forEach { result.add(it.getCommit().id) }

//        val query = "MATCH (commit:${COMMIT.name()})-[:${CONTAINS.name()}]->(change:${CHANGE.name()}{path:\"$path\"}) return commit"
//        val queryResult = db.execute(query)
//        Iterators.asIterable(queryResult.columnAs<Node>("commit")).forEach { result.add(it) }
        pathNodesCache[path] = result
        return result
    }

    fun getParents(changeNode: Node, commitNode: Node): List<Node> {
        //find next parents, if any
        val action = changeNode.getAction()
        val parentPath = (if (action == Action.MOVED) changeNode.getOldPath() else changeNode.getPath()) ?: return emptyList()

        val commitNodeId = commitNode.id

        val parentCandidates = getCommitNodesWithChangedPath(parentPath)

        val parentNodesWithPath = db.traversalDescription()
                .uniqueness(Uniqueness.NODE_GLOBAL)
                .relationships(PARENT, Direction.OUTGOING)
                .evaluator(Evaluators.excludeStartPosition())
                .evaluator {
                    val currentNode = it.endNode()
                    if (currentNode.id == commitNodeId) return@evaluator Evaluation.INCLUDE_AND_CONTINUE
                    if (parentCandidates.contains(currentNode.id)) return@evaluator Evaluation.INCLUDE_AND_PRUNE
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

    fun getChangeConnections(commitNode: Node): ChangeConnections {
        val parentsPerNode: MutableMap<Node, List<Node>> = HashMap()

        commitNode.getChanges().forEach {
            parentsPerNode[it] = getParents(it, commitNode)
        }

        return ChangeConnections(parentsPerNode)
    }
}