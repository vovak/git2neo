package org.sorcerers.git2neo

import org.junit.Assert
import org.junit.Test
import java.util.*

/**
 * @author vovak
 * @since 21/11/16
 */
class CommitIndexBulkTest : CommitIndexTestBase() {
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