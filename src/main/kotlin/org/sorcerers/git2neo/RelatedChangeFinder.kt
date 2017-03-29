package org.sorcerers.git2neo

import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.traversal.Evaluation
import org.neo4j.graphdb.traversal.Evaluators
import org.neo4j.graphdb.traversal.Uniqueness
import java.util.*

class RelatedChangeFinder(val db: GraphDatabaseService) {
    data class ChangeConnections(val parentsPerChange: Map<Node, Collection<Node>>)

    val intern: StringIntern = StringIntern()

    val pathNodesCache: FixedSizeCache<String, Collection<Long>> = FixedSizeCache(50000)

    fun findCommitById(id: String): Node {
        return db.findNode(COMMIT, "id", id)
    }

    fun getCommitNodesPerChangedPaths(paths: Collection<String>): Map<String, Collection<Long>> {
        val result: MutableMap<String, Collection<Long>> = HashMap()
        val pathsToSearch: MutableSet<String> = HashSet()
        paths.forEach {
            run {
                if (pathNodesCache.containsKey(it)) {
                    result[it] = pathNodesCache.get(it)!!
                } else {
                    pathsToSearch.add(it)
                }
            }
        }

        pathsToSearch.forEach {
            val idsForPaths = db.findNodes(CHANGE, "path", it).map { it.getCommit().id }.asSequence().toList()
            result[it] = HashSet(idsForPaths)

        }

        result.forEach { path, nodes -> pathNodesCache.put(path, nodes) }

//        val query = "MATCH (commit:${COMMIT.name()})-[:${CONTAINS.name()}]->(change:${CHANGE.name()}{path:\"$path\"}) return commit"
//        val queryResult = db.execute(query)
//        Iterators.asIterable(queryResult.columnAs<Node>("commit")).forEach { result.add(it) }
        return result
    }

    fun getParents(commitNode: Node): Map<Node, List<Node>> {
        val paths: MutableSet<String> = HashSet()
        val nodesPerPath: MutableMap<String, MutableCollection<Node>> = HashMap()

        fun recordParentPathForNode(parentPath: String?, changeNode: Node) {
            if (parentPath == null) return
            paths.add(intern.intern(parentPath))
            if (parentPath !in nodesPerPath) nodesPerPath[parentPath] = ArrayList()
            nodesPerPath[parentPath]!!.add(changeNode)
        }

        commitNode.getChanges().forEach {
            val path = it.getPath()
            val oldPath = it.getOldPath()
            recordParentPathForNode(path, it)
            recordParentPathForNode(oldPath, it)
        }

        val commitNodeId = commitNode.id

        val parentCandidates = getCommitNodesPerChangedPaths(paths)

        val remainingIds: MutableSet<Long> = HashSet(parentCandidates.values.flatten())

        val parentNodesWithOneOfPaths = db.traversalDescription()
                .uniqueness(Uniqueness.NODE_GLOBAL)
                .relationships(PARENT, Direction.OUTGOING)
                .evaluator(Evaluators.excludeStartPosition())
                .evaluator {
                    val currentNode = it.endNode()
                    if (currentNode.id == commitNodeId) return@evaluator Evaluation.INCLUDE_AND_CONTINUE
                    if (remainingIds.contains(currentNode.id)) {
                        remainingIds.remove(currentNode.id)
                        return@evaluator if (remainingIds.isEmpty()) Evaluation.INCLUDE_AND_PRUNE else Evaluation.INCLUDE_AND_CONTINUE
                    }
                    return@evaluator Evaluation.EXCLUDE_AND_CONTINUE
                }
                .traverse(commitNode).nodes()

        val parentNodesPerNode: MutableMap<Node, MutableList<Node>> = HashMap()

        parentNodesWithOneOfPaths.forEach {
            val changeNodes = it.getChanges()
            val foundPerPath: MutableMap<String, Node> = HashMap()
            changeNodes.forEach { foundPerPath[it.getPath()] = it }

            nodesPerPath.forEach {
                val path = it.key
                it.value.forEach { node ->
                    if (foundPerPath.containsKey(path)) {
                        if (!parentNodesPerNode.containsKey(node)) parentNodesPerNode[node] = ArrayList()
                        parentNodesPerNode[node]!!.add(foundPerPath[path]!!)
                    }
                }
            }

        }

        return parentNodesPerNode
    }

    fun getChangeConnections(commitNode: Node): ChangeConnections {
        val parentsPerNode: Map<Node, List<Node>> = getParents(commitNode)

        return ChangeConnections(parentsPerNode)
    }
}