package org.sorcerers.git2neo

import org.apache.commons.lang3.SerializationUtils
import org.neo4j.graphdb.*
import org.neo4j.graphdb.traversal.Uniqueness
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors


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

fun Node.getCommitId(): String {
    return this.getProperty("commitId") as String
}


open class CommitIndex(val db: GraphDatabaseService, val logPrefix: String) : CommitStorage {
    fun withDb(block: () -> Unit) {
        db.beginTx().use({ tx: Transaction ->
            block.invoke()
            tx.success()
            tx.close()
        })
    }

    init {
        withDb {
            val commitIndexAbsent = db.schema().getIndexes(COMMIT).none()
            val changeIndexAbsent = db.schema().getIndexes(CHANGE).none()
            if (commitIndexAbsent) db.schema().indexFor(COMMIT).on("id").create()
            if (changeIndexAbsent) {
                db.schema().indexFor(CHANGE).on("path").create()
                db.schema().indexFor(CHANGE).on("id").create()
            }
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


    fun getChangeParentsConnections(relatedChangeFinder: RelatedChangeFinder, commitNodeId: Long): RelatedChangeFinder.ChangeConnections {
        val commitNode = db.getNodeById(commitNodeId)
        val connections = relatedChangeFinder.getChangeConnections(commitNode)
        return connections
    }

    fun createChangeConnectionRelations(connections: RelatedChangeFinder.ChangeConnections) {
        connections.parentsPerChange.forEach {
            val change = db.getNodeById(it.key)
            val parents = it.value
            parents.forEach { change.createRelationshipTo(db.getNodeById(it), PARENT) }
        }
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

    fun processNodes(nodeIds: List<Long>, chunkIndex: Int, relatedChangeFinder: RelatedChangeFinder): List<RelatedChangeFinder.ChangeConnections> {
        val total = nodeIds.size
        var done = 0
        val startTime = System.currentTimeMillis()
        var currentStartTime = startTime


        val chunk: MutableList<Long> = ArrayList()
        val windowSize = 200
        val connections: MutableList<RelatedChangeFinder.ChangeConnections> = ArrayList()
        fun processChunk() {
            withDb {
                chunk.forEach {
                    connections.add(getChangeParentsConnections(relatedChangeFinder, it))
                }
            }
            val now = System.currentTimeMillis()
            println("$logPrefix Chunk $chunkIndex: $done/$total done, ${chunk.size} processed in ${1.0 * (now - currentStartTime) / 1000} s")
            currentStartTime = now
            chunk.clear()
        }

        nodeIds.forEach {
            //            println("$logPrefix Updating parent connections for node ${it.getProperty("id")}")
            chunk.add(it)
            done++
            if (done % windowSize == 0 && done > 0) {
                processChunk()
            }
        }
        processChunk()
        println("$logPrefix Chunk $chunkIndex: all $done done in ${System.currentTimeMillis() - startTime} ms")
        return connections
    }

    fun getParentConnectionsSearchJob(nodeIds: List<Long>, workerIndex: Int, relatedChangeFinder: RelatedChangeFinder): Callable<List<RelatedChangeFinder.ChangeConnections>> {
        return Callable {
            try {
                return@Callable processNodes(nodeIds, workerIndex, relatedChangeFinder)
            } catch (e: Throwable) {
                throw e
            }
        }
    }

    fun createRelationshipConnectionsInBulk(connections: Collection<RelatedChangeFinder.ChangeConnections>) {
        val connectionsPerTransaction = 200
        val total = connections.size

        val startTime = System.currentTimeMillis()
        var currentStartTime = startTime
        val currentChunk: MutableList<RelatedChangeFinder.ChangeConnections> = ArrayList()

        fun flushToDb() {
            println("$logPrefix creating ${currentChunk.size} connection relationships...")
            withDb { currentChunk.forEach { createChangeConnectionRelations(it) } }
            println("$logPrefix done")
            currentChunk.clear()
        }

        connections.forEachIndexed { i, connection ->
            run {
                currentChunk.add(connection)
                if (i >= connectionsPerTransaction && i % connectionsPerTransaction == 0) {
                    flushToDb()
                    val now = System.currentTimeMillis()
                    val msTaken = now - currentStartTime
                    currentStartTime = now
                    println("$logPrefix added $connectionsPerTransaction in $msTaken ms, $i/$total done")
                }
            }
        }
        flushToDb()
    }

    fun updateChangeParentConnectionsForAllNodes() {
        val allNodes: MutableList<Long> = ArrayList()
        val nCores = Runtime.getRuntime().availableProcessors()
        val nThreads = nCores / 2
        withDb {
            db.findNodes(COMMIT).forEach { allNodes.add(it.id) }
        }
        println("$logPrefix Updating parent connections for all nodes.")
        val chunkSizeLimit = allNodes.size / (nThreads * 5) + 1
        println("$logPrefix $nCores cores available, will use $nThreads threads for change layer build, max $chunkSizeLimit nodes per thread")
        val nodeChunks: MutableList<List<Long>> = ArrayList()

        var currentChunk: MutableList<Long> = ArrayList()
        var currentChunkSize = 0

        fun dumpChunk() {
            nodeChunks.add(currentChunk)
            currentChunk = ArrayList()
            currentChunkSize = 0
        }

        allNodes.forEach { node ->
            currentChunk.add(node)
            currentChunkSize++
            if (currentChunkSize >= chunkSizeLimit) dumpChunk()
        }
        dumpChunk()
        val totalChangesInChunks = nodeChunks.map { it.size }.sum()
        println("$logPrefix $totalChangesInChunks nodes in chunks from ${allNodes.size} total")

        val executorService = Executors.newFixedThreadPool(nThreads)

        val jobs: MutableList<Callable<List<RelatedChangeFinder.ChangeConnections>>> = ArrayList()
        val relatedChangeFinder = RelatedChangeFinder(db)
        nodeChunks.forEachIndexed { index, chunk ->
            val job = getParentConnectionsSearchJob(chunk, index, relatedChangeFinder)
            jobs.add(job)
        }
        val results = executorService.invokeAll(jobs).map { it.get() }

        println("$logPrefix Found parents for all nodes, creating relationships")

        createRelationshipConnectionsInBulk(results.flatten())

        println("$logPrefix Done creating relationships!")

    }


    override fun add(commit: Commit) {
        withDb {
            doAdd(commit)
        }
        updateChangeParentConnectionsForAllNodes()
    }

    override fun addAll(commits: Collection<Commit>) {
        println("$logPrefix Adding ${commits.size} nodes to db")
        val windowSize = 1000
        val startTime = System.currentTimeMillis()
        var currentStartTime = startTime
        val currentChunk: MutableList<Commit> = ArrayList()

        fun flushToDb() {
            println("$logPrefix flushing $windowSize commits to db...")
            withDb { currentChunk.forEach { doAdd(it) } }
            println("$logPrefix done")
            currentChunk.clear()
        }

        commits.forEachIndexed { i, commit ->
            run {
                currentChunk.add(commit)
                if (i > windowSize && i % windowSize == 0) {
                    flushToDb()
                    val now = System.currentTimeMillis()
                    val msTaken = now - currentStartTime
                    currentStartTime = now
                    println("$logPrefix added $windowSize in $msTaken ms")
                }
            }
        }
        flushToDb()
        val totalMs = System.currentTimeMillis() - startTime
        println("$logPrefix added all ${commits.size} nodes in $totalMs ms")

        updateChangeParentConnectionsForAllNodes()
    }

    fun Node.toFileRevision(commitInfo: CommitInfo): FileRevision {
        val hasOldPath = this.hasProperty("oldPath")
        return FileRevision(
                FileRevisionId(this.getProperty("id") as String),
                this.getProperty("path") as String,
                if (hasOldPath) this.getProperty("oldPath") as String else null,
                commitInfo,
                Action.valueOf(this.getProperty("action") as String)
        )
    }

    fun Node.toFileRevision(): FileRevision {
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
        val changeNodes = this.relationships.filter { it.isType(CONTAINS) }.map { it.endNode }

        val commitInfo: CommitInfo = SerializationUtils.deserialize(this.getProperty("info") as ByteArray)
        return Commit(
                commitInfo,
                changeNodes.map { it.toFileRevision(commitInfo) }
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
            val commitInfo: CommitInfo = SerializationUtils.deserialize(headCommitNode.getProperty("info") as ByteArray)
            val changeNodes = headCommitNode.getChanges()
            changeNodes.forEach {
                val traversal = db.traversalDescription().depthFirst().relationships(PARENT, Direction.OUTGOING).uniqueness(Uniqueness.NODE_GLOBAL)
                val history = History(traversal.traverse(it).nodes().map { it.toFileRevision(commitInfo) })
                result.add(history)
            }
        }
        return result
    }

}

