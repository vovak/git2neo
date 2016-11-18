package org.sorcerers.git2neo

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.neo4j.test.TestGraphDatabaseFactory
import java.io.File
import java.util.*
import java.util.function.Predicate

/**
 * @author vovak
 * @since 17/11/16
 */
class CommitIndexTest {
    lateinit var myIndex: CommitIndex

    @Before
    fun initIndex() {
        val path = "./testdb"
        val db = TestGraphDatabaseFactory().newImpermanentDatabase(File(path))
        myIndex = CommitIndex(db)
    }

    fun getIndex(): CommitIndex {
        return myIndex
    }

    fun createCommit(id: String, parents: List<String>): Commit {
        val commitInfo = CommitInfo(CommitId(id), Contributor(""), Contributor(""), 0, 0, parents.map(::CommitId))
        return Commit(commitInfo, emptyList())
    }

    fun createCommit(id: String, parent: String?): Commit {
        val parents = if (parent == null) emptyList<String>() else listOf(parent)
        return createCommit(id, parents)
    }

    @Test
    fun testAddCommit() {
        val index = getIndex()

        Assert.assertNull(index.get(CommitId("0")))
        index.add(createCommit("0", null))

        val nonExistingCommit = index.get(CommitId("unknownIndex"))
        Assert.assertNull(nonExistingCommit)

        val commitFromIndex = index.get(CommitId("0"))
        Assert.assertNotNull(commitFromIndex)

        Assert.assertEquals(index.get(CommitId("0"))?.info?.id, createCommit("0", null).info.id)
    }

    @Test
    fun testAddTwoCommits() {
        val index = getIndex()
        index.add(createCommit("0", null))
        index.add(createCommit("1", "0"))

        val trivialHistory = index.getHistory(CommitId("0"), Predicate({ true }))
        Assert.assertEquals(1, trivialHistory.items.size)

        val fullHistory = index.getHistory(CommitId("1"), Predicate({ true }))
        Assert.assertEquals(2, fullHistory.items.size)
    }

    @Test
    fun testOneMerge() {
        val index = getIndex()
        index.add(createCommit("0", null))
        index.add(createCommit("left", "0"))
        index.add(createCommit("right", "0"))
        index.add(createCommit("merge", listOf("left", "right")))
        index.add(createCommit("head", "merge"))

        val fullHistory = index.getHistory(CommitId("head"), Predicate { true })
        Assert.assertEquals(5, fullHistory.items.size)
    }

    @Test
    fun testManyCommits() {
        val height = 10000
        val index = getIndex()
        val allCommits: MutableList<Commit> = ArrayList()

        for (i in 1..height) {
            allCommits.add(createCommit("left_$i", if (i == 1) emptyList() else listOf("left_${i - 1}", "right_${i - 1}")))
            allCommits.add(createCommit("right_$i", if (i == 1) null else "right_${i - 1}"))
        }
        var start = System.currentTimeMillis()
        index.addAll(allCommits)
        var executionTime = System.currentTimeMillis() - start
        println("Inserted ${2 * height} revisions in ${1.0 * executionTime / 1000} seconds")


        start = System.currentTimeMillis()

        val leftHistory = index.getHistory(CommitId("left_$height"), Predicate { true })
        executionTime = System.currentTimeMillis() - start
        println("Acquired history of ${2 * height} revisions in ${1.0 * executionTime / 1000} seconds")

        Assert.assertEquals(2 * height - 1, leftHistory.items.size)


        start = System.currentTimeMillis()

        val rightHistory = index.getHistory(CommitId("right_${height}"), Predicate { true })
        executionTime = System.currentTimeMillis() - start
        println("Acquired history of ${height} revisions in ${1.0 * executionTime / 1000} seconds")

        Assert.assertEquals(height, rightHistory.items.size)
    }
}