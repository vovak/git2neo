package org.sorcerers.git2neo

import org.junit.Assert
import java.io.File
import java.util.*

/**
 * Created by vovak on 3/23/17.
 */
val TEST_DB_PATH = "./tempdb"

fun removeTestDb() {
    File(TEST_DB_PATH).deleteRecursively()
}

fun createEmbeddedIndex(): CommitIndex {
    removeTestDb()
    val dir = File(TEST_DB_PATH)
    dir.mkdirs()
    dir.deleteOnExit()
    return CommitIndexFactory().loadOrCreateCommitIndex(dir, "EmbeddedIndexTest")
}

class EmbeddedIndexPerfTest: TimeComplexityTestBase() {
    fun createDbAndAddUnconnectedNodes(nodesCount: Int) {
        val index = createEmbeddedIndex()
        val commits: MutableList<Commit> = ArrayList()
        for (i in 1..nodesCount) {
            commits.add(createCommit("$i", null))
        }
        index.addAll(commits)
        index.db.shutdown()
        removeTestDb()
    }

//    @Test
    fun testUnconnectedNodesAddInLinearTime() {
        Assert.assertTrue(isLinearPerformance({createDbAndAddUnconnectedNodes(it) }, Pair(1000,100000), 15))
    }
}