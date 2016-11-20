package org.sorcerers.git2neo

import org.apache.commons.lang3.SerializationUtils
import org.neo4j.graphdb.*
import org.neo4j.graphdb.traversal.Uniqueness
import java.util.*


/**
 * @author vovak
 * @since 17/11/16
 */
class CommitIndex(val db: GraphDatabaseService) : CommitStorage, HistoryQueriable<Commit> {
    val COMMIT: Label = Label { "commit" }
    val CHANGE: Label = Label { "change" }
    val PARENT: RelationshipType = RelationshipType { "PARENT" }
    val CONTAINS: RelationshipType = RelationshipType { "CONTAINS" }

    fun withDb(block: () -> Unit) {
        db.beginTx().use({ tx: Transaction ->
            block.invoke()
            tx.success()
        })
    }

    init {
        withDb {
            db.schema().indexFor(COMMIT)
                    .on("id")
                    .create()
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
        if (change.relatedPath != null) {
            changeNode.setProperty("relatedPath", change.relatedPath)
        }
        changeNode.setProperty("commitId", change.commitId.stringId())
        commitNode.createRelationshipTo(changeNode, CONTAINS)
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

    override fun add(commit: Commit) {
        withDb { doAdd(commit) }
    }

    override fun addAll(commits: Collection<Commit>) {
        withDb { commits.forEach { doAdd(it) } }
    }

    fun Node.toFileRevision(): FileRevision {
        assert(this.hasLabel(CHANGE))
        val hasRelatedPath = this.hasProperty("relatedPath")
        return FileRevision(
                FileRevisionId(this.getProperty("id") as String),
                this.getProperty("path") as String,
                CommitId(this.getProperty("commitId") as String),
                Action.valueOf(this.getProperty("action") as String),
                if (hasRelatedPath) this.getProperty("relatedPath") as String else null
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

    override fun getHistory(head: Id<Commit>, filter: (Commit) -> Boolean): History<Commit> {
        val commits: MutableList<Commit> = ArrayList()
        withDb {
            val headNode = db.findNode(COMMIT, "id", head.stringId())
            val traversal = db.traversalDescription().depthFirst().relationships(PARENT, Direction.OUTGOING).uniqueness(Uniqueness.NODE_GLOBAL)
            val result = traversal.traverse(headNode)
            result.nodes().forEach { commits.add(it.toCommit()) }
        }
        return History(commits)
    }
}