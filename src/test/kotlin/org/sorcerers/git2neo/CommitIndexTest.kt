package org.sorcerers.git2neo

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.neo4j.test.TestGraphDatabaseFactory
import java.io.File
import java.util.*

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

    fun createCommit(id: String, parentIds: List<String>, changes: List<Triple<Action, String, String?>>): Commit {
        val commitInfo = CommitInfo(CommitId(id), Contributor(""), Contributor(""), 0, 0, parentIds.map(::CommitId))
        return Commit(commitInfo, changes.map {
            FileRevision(
                    FileRevisionId("${id}#${it.second}"),
                    it.second,
                    commitInfo.id,
                    it.first,
                    it.third)
        })
    }

    fun createCommit(id: String, parentId: String?, changes: List<Triple<Action, String, String?>>): Commit {
        return createCommit(id, (if (parentId == null) emptyList<String>() else listOf(parentId)), changes)
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

        val trivialHistory = index.getCommitHistory(CommitId("0"), { true })
        Assert.assertEquals(1, trivialHistory.items.size)

        val fullHistory = index.getCommitHistory(CommitId("1"), { true })
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

        val fullHistory = index.getCommitHistory(CommitId("head"), { true })
        Assert.assertEquals(5, fullHistory.items.size)
    }

    @Test
    fun testOneNodeWithChange() {
        val index = getIndex()
        index.add(createCommit("0", null, listOf(Triple(Action.CREATED, "a.txt", null))))
        val commitFromDb = index.get(CommitId("0"))

        Assert.assertEquals(1, commitFromDb!!.changes.size)
    }

    @Test
    fun testTrivialChangesHistory() {
        val index = getIndex()
        index.add(createCommit("0", null, listOf(Triple(Action.CREATED, "a.txt", null))))
        index.add(createCommit("1", "0", listOf(Triple(Action.MODIFIED, "a.txt", null))))
        val headCommit = index.get(CommitId("1"))
        Assert.assertNotNull(headCommit)
        val headCommitChange = headCommit!!.changes.first()

        val changesHistory = index.getChangesHistory(headCommitChange.id, {true})
        Assert.assertEquals(2, changesHistory.items.size)
    }

    @Test
    fun testTrivialChangesHistoryWithReverseInsertionOrder() {
        val index = getIndex()
        index.add(createCommit("1", "0", listOf(Triple(Action.MODIFIED, "a.txt", null))))
        index.add(createCommit("0", null, listOf(Triple(Action.CREATED, "a.txt", null))))
        val headCommit = index.get(CommitId("1"))
        Assert.assertNotNull(headCommit)
        val headCommitChange = headCommit!!.changes.first()

        val changesHistory = index.getChangesHistory(headCommitChange.id, {true})
        Assert.assertEquals(2, changesHistory.items.size)
    }

    @Test
    fun testTrivialChangesHistoryWithBulkAdd() {
        val index = getIndex()
        val commits = listOf(
                createCommit("0", null, listOf(Triple(Action.CREATED, "a.txt", null))),
                createCommit("1", "0", listOf(Triple(Action.MODIFIED, "a.txt", null)))
        )
        index.addAll(commits)
        val headCommit = index.get(CommitId("1"))
        Assert.assertNotNull(headCommit)
        val headCommitChange = headCommit!!.changes.first()

        val changesHistory = index.getChangesHistory(headCommitChange.id, {true})
        Assert.assertEquals(2, changesHistory.items.size)
    }

    @Test
    fun testLongerLinearChangesHistoryWithOneFile() {
        val index = getIndex()
        index.add(createCommit("0", null, listOf(Triple(Action.CREATED, "a.txt", null))))
        index.add(createCommit("1", "0", listOf(Triple(Action.MODIFIED, "a.txt", null))))
        index.add(createCommit("2", "1", listOf(Triple(Action.MODIFIED, "a.txt", null))))
        index.add(createCommit("3", "2", listOf(Triple(Action.MODIFIED, "a.txt", null))))
        index.add(createCommit("4", "3", listOf(Triple(Action.MODIFIED, "a.txt", null))))
        index.add(createCommit("5", "4", listOf(Triple(Action.MODIFIED, "a.txt", null))))
        val headCommit = index.get(CommitId("5"))
        Assert.assertNotNull(headCommit)
        val headCommitChange = headCommit!!.changes.first()

        val changesHistory = index.getChangesHistory(headCommitChange.id, {true})
        Assert.assertEquals(6, changesHistory.items.size)
    }

    @Test
    fun testLongerLinearChangesHistoryWithTwoFiles() {
        val index = getIndex()
        index.add(createCommit("0", null, listOf(Triple(Action.CREATED, "a.txt", null))))
        index.add(createCommit("1", "0", listOf(Triple(Action.MODIFIED, "a.txt", null), Triple(Action.CREATED, "b.txt", null))))
        index.add(createCommit("2", "1", listOf(Triple(Action.MODIFIED, "a.txt", null), Triple(Action.MODIFIED, "b.txt", null))))
        index.add(createCommit("3", "2", listOf(Triple(Action.MODIFIED, "a.txt", null))))
        index.add(createCommit("4", "3", listOf(Triple(Action.MODIFIED, "b.txt", null))))
        index.add(createCommit("5", "4", listOf(Triple(Action.MODIFIED, "a.txt", null))))
        val headCommit = index.get(CommitId("5"))
        Assert.assertNotNull(headCommit)
        val headCommitChange = headCommit!!.changes.first()

        val changesHistory = index.getChangesHistory(headCommitChange.id, {true})
        Assert.assertEquals(5, changesHistory.items.size)

        val headForB = index.get(CommitId("4"))!!.changes.first()
        val changesForBHistory = index.getChangesHistory(headForB.id, {true})
        Assert.assertEquals(3, changesForBHistory.items.size)
    }

    @Test
    fun testManyCommits() {
        val height = 1000
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

        val leftHistory = index.getCommitHistory(CommitId("left_$height"), { true })
        executionTime = System.currentTimeMillis() - start
        println("Acquired history of ${2 * height} revisions in ${1.0 * executionTime / 1000} seconds")

        Assert.assertEquals(2 * height - 1, leftHistory.items.size)


        start = System.currentTimeMillis()

        val rightHistory = index.getCommitHistory(CommitId("right_${height}"), { true })
        executionTime = System.currentTimeMillis() - start
        println("Acquired history of ${height} revisions in ${1.0 * executionTime / 1000} seconds")

        Assert.assertEquals(height, rightHistory.items.size)
    }
}