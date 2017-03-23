package org.sorcerers.git2neo

import org.junit.Assert
import org.junit.Test
import java.io.File
import java.util.*

/**
 * Created by vovak on 3/23/17.
 * Base class for testing the index using an embedded database instead of neo4j in-memory test impl
 */

fun createEmbeddedIndex(path: String): CommitIndex {
    val dir = File(path)
    dir.mkdirs()
    dir.deleteOnExit()
    return CommitIndexFactory().loadOrCreateCommitIndex(dir)
}

class EmbeddedDbTest: CommitIndexTestBase() {
    fun generateInputs(limits: Pair<Int, Int>, steps: Int): List<Int> {
        if (steps < 3) throw IllegalArgumentException("The minimum number of steps is 3")
        if (limits.first >= limits.second) {
            throw IllegalArgumentException("Lower limit should be less than upper")
        }
        val result: MutableList<Int> = ArrayList()
        val increment = 1.0 * (limits.second - limits.first) / (steps-1)

        result.add(limits.first)
        (1..steps-2).mapTo(result) { Math.round(limits.first + it * increment).toInt() }
        result.add(limits.second)
        return result
    }

    @Test
    fun testInputGeneration() {
        val reference = listOf(1,4,7,10)
        val generated = generateInputs(Pair(1,10), 4)
        Assert.assertArrayEquals(reference.toIntArray(), generated.toIntArray())
    }

    fun assertLinearPerformance(function: (Int) -> Any, limits: Pair<Int, Int>, steps: Int) {

    }
}