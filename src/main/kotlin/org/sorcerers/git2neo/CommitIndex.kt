package org.sorcerers.git2neo

import org.apache.commons.lang3.SerializationUtils
import org.neo4j.graphdb.*
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

//TODO check node type (it should not be possible to call *ChangeNode*.getChanges())

fun Node.getChanges(): List<Node> {
    return this.relationships.filter { it.isType(CONTAINS) }.map { it.endNode }
}

fun Node.getCommit(): Node {
    val startNodes = this.relationships.filter { it.isType(CONTAINS) }.map { it.startNode }
    assert(startNodes.size == 1)
    return startNodes.first()
}

fun Node.getAction(): Action {
    return Action.valueOf(getProperty("action") as String)
}

fun Node.getOldPath(): String? {
    if (!this.hasProperty("oldPath")) return null
    return this.getProperty("oldPath") as String
}

fun Node.getPath(): String {
    return this.getProperty("path") as String
}

open class CommitIndex(val db: GraphDatabaseService) : CommitStorage {
    fun withDb(block: () -> Unit) {
        db.beginTx().use({ tx: Transaction ->
            block.invoke()
            tx.success()
        })
    }

    init {
        withDb {
            db.schema().indexFor(COMMIT).on("id").create()
            db.schema().indexFor(CHANGE).on("path").create()
        }
    }

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
//        println("Connections for node ${commitNode.id}: ${connections.childrenPerChange.values.sumBy { it.size }} ->child, ${connections.parentsPerChange.values.sumBy { it.size }} ->parent")
        connections.parentsPerChange.forEach {
            val change = it.key
            val parents = it.value
            parents.forEach { change.createRelationshipTo(it, PARENT) }
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
    }

    fun updateChangeParentConnectionsForAllNodes() {
        val allNodes = db.findNodes(COMMIT)
        println("Updating parent connections for all nodes.")
        var done = 0
        val startTime = System.currentTimeMillis()
        var currentStartTime = startTime
        allNodes.forEach {
//            println("Updating parent connections for node ${it.getProperty("id")}")
            updateChangeParentConnections(it)
            done++
            val windowSize = 50
            if (done % windowSize == 0) {
                val now = System.currentTimeMillis()
                println("$done done, $windowSize processed in ${now - currentStartTime} ms")
                currentStartTime = now
            }
        }
        println("all $done done in ${System.currentTimeMillis() - startTime} ms")
    }


    override fun add(commit: Commit) {
        withDb {
            doAdd(commit)
            updateChangeParentConnectionsForAllNodes()
        }
    }

    override fun addAll(commits: Collection<Commit>) {
        withDb {
            println("Adding ${commits.size} nodes to db")
            val windowSize = 1000
            val startTime = System.currentTimeMillis()
            var currentStartTime = startTime
            commits.forEachIndexed { i, commit ->
                run {
                    doAdd(commit)
                    if (i > windowSize && i % windowSize == 0) {
                        val now = System.currentTimeMillis()
                        val msTaken = now - currentStartTime
                        currentStartTime = now
                        println("added $windowSize in $msTaken ms")
                    }
                }
            }
            val totalMs = System.currentTimeMillis() - startTime
            println("added all ${commits.size} nodes in $totalMs ms")

            updateChangeParentConnectionsForAllNodes()
        }
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

