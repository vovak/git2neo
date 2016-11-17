package org.sorcerers.git2neo

import org.junit.Assert
import org.junit.Test
import java.util.function.Predicate

/**
* @author vovak
* @since 17/11/16
*/
class CommitIndexTest {
    fun getIndex(): CommitIndex {
        return CommitIndex()
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
        index.add(createCommit("0", null))

        val nonExistingCommit = index.get(CommitId("unknownIndex"))
        Assert.assertNull(nonExistingCommit)

        val commitFromIndex = index.get(CommitId("0"))
        Assert.assertNotNull(commitFromIndex)
    }

    @Test
    fun testAddTwoCommits() {
        val index = getIndex()
        index.add(createCommit("0", null))
        index.add(createCommit("1", "0"))

        val trivialHistory = index.getHistory(CommitId("0"), Predicate({true}))
        Assert.assertEquals(1, trivialHistory.items.size)

        val fullHistory = index.getHistory(CommitId("1"), Predicate({true}))
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
}