package org.sorcerers.git2neo

import org.apache.commons.lang3.SerializationUtils
import org.neo4j.graphdb.*
import org.neo4j.graphdb.traversal.Evaluation
import org.neo4j.graphdb.traversal.Evaluators
import org.neo4j.graphdb.traversal.Uniqueness
import java.util.*


/**
 * @author vovak
 * @since 17/11/16
 */

val COMMIT: Label = Label { "commit" }
val CHANGE: Label = Label { "change" }
val PARENT: RelationshipType = RelationshipType { "PARENT" }
val CONTAINS: RelationshipType = RelationshipType { "CONTAINS" }

fun Node.getChanges(): List<Node> {
    assert(this.hasLabel(COMMIT))
    return this.relationships.filter { it.isType(CONTAINS) }.map { it.endNode }
}

fun Node.getCommit(): Node {
    assert(this.hasLabel(CHANGE))
    val startNodes = this.relationships.filter { it.isType(CONTAINS) }.map { it.startNode }
    assert(startNodes.size == 1)
    return startNodes.first()
}

fun Node.getAction(): Action {
    assert(this.hasLabel(CHANGE))
    return Action.valueOf(getProperty("action") as String)
}

fun Node.getOldPath(): String? {
    assert(this.hasLabel(CHANGE))
    if (!this.hasProperty("oldPath")) return null
    return this.getProperty("oldPath") as String
}

fun Node.getPath(): String {
    assert(this.hasLabel(CHANGE))
    return this.getProperty("path") as String
}

class CommitIndex(val db: GraphDatabaseService) : CommitStorage {
    fun withDb(block: () -> Unit) {
        db.beginTx().use({ tx: Transaction ->
            block.invoke()
            tx.success()
        })
    }

//    init {
//        withDb {
//            db.index().forNodes(COMMIT.name())
//                    .on("id")
//                    .create()
//        }
//    }

    private fun findOrCreateCommitNode(id: CommitId): Node {
        val result: Node?
        val node = db.findNode(COMMIT, "id", id.idString)
        if (node == null) {
            val newNode = db.createNode(COMMIT)
            newNode.addLabel(COMMIT)
            newNode.setProperty("id", id.idString)
            result = newNode
        } else result = node
        if (result == null) throw Exception("Cannot get node: exception occurred during transaction")
        return result
    }

    fun addChangeNode(commitNode: Node, change: FileRevision) {
        assert(commitNode.hasLabel(COMMIT))
        val changeNode = db.createNode(CHANGE)
        changeNode.setProperty("id", change.id.stringId())
        changeNode.setProperty("action", change.action.name)
        changeNode.setProperty("path", change.path)
        if (change.oldPath != null) {
            changeNode.setProperty("oldPath", change.oldPath)
        }
        changeNode.setProperty("commitId", change.commitInfo.id.stringId())
        commitNode.createRelationshipTo(changeNode, CONTAINS)
    }


    fun updateChangeParentConnections(commitNode: Node) {
        val connections = RelatedChangeFinder().getChangeConnections(db, commitNode)
        println("Connections for node ${commitNode}: ${connections.childrenPerChange.values.sumBy { it.size }} ->child, ${connections.parentsPerChange.values.sumBy { it.size }} ->parent")
        connections.parentsPerChange.forEach {
            val change = it.key
            val parents = it.value
            parents.forEach { change.createRelationshipTo(it, PARENT) }
        }

        connections.childrenPerChange.forEach {
            val change = it.key
            val children = it.value
            children.forEach { it.createRelationshipTo(change, PARENT) }
        }

    }

    fun updateChangesForNewRevision(commitNode: Node) {
        assert(commitNode.hasLabel(COMMIT))
        updateChangeParentConnections(commitNode)
    }

    fun doAdd(commit: Commit) {
        val nodeId = commit.info.id
        val node = findOrCreateCommitNode(nodeId)
        node.setProperty("info", SerializationUtils.serialize(commit.info))
        commit.changes.forEach { addChangeNode(node, it) }

        val parentIds = commit.info.parents
        parentIds.forEach {
            val parentNode = findOrCreateCommitNode(it)
            node.createRelationshipTo(parentNode, PARENT)
        }

        //TODO Current impl is quite slow (up to 200 ms/node)
        //TODO update changes connection with one traversal, try adding index on path/oldPath for change nodes.
        updateChangesForNewRevision(node)
    }

    override fun add(commit: Commit) {
        withDb { doAdd(commit) }
    }

    override fun addAll(commits: Collection<Commit>) {
        withDb { commits.forEach { doAdd(it) } }
    }

    fun Node.toFileRevision(): FileRevision {
        assert(this.hasLabel(CHANGE))
        val hasOldPath = this.hasProperty("oldPath")
        return FileRevision(
                FileRevisionId(this.getProperty("id") as String),
                this.getProperty("path") as String,
                if (hasOldPath) this.getProperty("oldPath") as String else null,
                SerializationUtils.deserialize(this.getCommit().getProperty("info") as ByteArray),
                Action.valueOf(this.getProperty("action") as String)
        )
    }

    fun Node.toCommit(): Commit {
        assert(this.hasLabel(COMMIT))
        val changeNodes = this.relationships.filter { it.isType(CONTAINS) }.map { it.endNode }

        return Commit(
                SerializationUtils.deserialize(this.getProperty("info") as ByteArray),
                changeNodes.map { it.toFileRevision() }
        )
    }

    override fun get(id: CommitId): Commit? {
        var result: Commit? = null
        withDb {
            val node = db.findNode(COMMIT, "id", id.idString)
            if (node != null) result = node.toCommit()
        }
        return result
    }

    fun getCommitHistory(head: Id<Commit>, filter: (Commit) -> Boolean): History<Commit> {
        val commits: MutableList<Commit> = ArrayList()
        withDb {
            val headNode = db.findNode(COMMIT, "id", head.stringId())
            val traversal = db.traversalDescription().depthFirst().relationships(PARENT, Direction.OUTGOING).uniqueness(Uniqueness.NODE_GLOBAL)
            val result = traversal.traverse(headNode)
            result.nodes().forEach { commits.add(it.toCommit()) }
        }
        return History(commits)
    }

    fun getChangesHistory(head: Id<FileRevision>, filter: (FileRevision) -> Boolean): History<FileRevision> {
        val changes: MutableList<FileRevision> = ArrayList()
        withDb {
            val headNode = db.findNode(CHANGE, "id", head.stringId())
            val traversal = db.traversalDescription().depthFirst().relationships(PARENT, Direction.OUTGOING).uniqueness(Uniqueness.NODE_GLOBAL)
            val result = traversal.traverse(headNode)
            result.nodes().forEach { changes.add(it.toFileRevision()) }
        }
        return History(changes)
    }

    fun getChangesHistoriesForCommit(head: Id<Commit>): List<History<FileRevision>> {
        val result: MutableList<History<FileRevision>> = ArrayList()
        withDb {
            val headCommitNode = db.findNode(COMMIT, "id", head.stringId())
            val changeNodes = headCommitNode.getChanges()
            changeNodes.forEach {
                val traversal = db.traversalDescription().depthFirst().relationships(PARENT, Direction.OUTGOING).uniqueness(Uniqueness.NODE_GLOBAL)
                val history = History(traversal.traverse(it).nodes().map { it.toFileRevision() })
                result.add(history)
            }
        }
        return result
    }
}

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
                    if (currentNode.getChanges().map{it.getPath()}.contains(parentPath)) return@evaluator Evaluation.INCLUDE_AND_PRUNE
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
//        parentChangeNodes.forEach {
//            changeNode.createRelationshipTo(it, PARENT)
//        }
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
                    if (currentNode.getChanges().map{it.getPath()}.contains(childPath) || currentNode.getChanges().map{it.getOldPath()}.contains(childPath)) return@evaluator Evaluation.INCLUDE_AND_PRUNE
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
//        childChangeNodes.forEach {
//            it.createRelationshipTo(changeNode, PARENT)
//        }
    }

    fun getChangeConnections(db: GraphDatabaseService, commitNode: Node): ChangeConnections {
        val parentsPerNode: MutableMap<Node, List<Node>> = HashMap()
        val childrenPerNode: MutableMap<Node, List<Node>> = HashMap()

        commitNode.getChanges().forEach {
            parentsPerNode[it] = getParents(db, it)
            childrenPerNode[it] = getChildren(db, it)
        }

        return ChangeConnections(parentsPerNode, childrenPerNode)
    }
}