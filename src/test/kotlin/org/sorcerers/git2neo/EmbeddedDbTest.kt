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
    fun generateInputs(limits: Pair<Long, Long>, steps: Int): List<Long> {
        if (steps < 3) throw IllegalArgumentException("The minimum number of steps is 3")
        if (limits.first >= limits.second) {
            throw IllegalArgumentException("Lower limit should be less than upper")
        }
        val result: MutableList<Long> = ArrayList()
        val increment = 1.0 * (limits.second - limits.first) / (steps-1)

        result.add(limits.first)
        (1..steps-2).mapTo(result) { Math.round(limits.first + it * increment) }
        result.add(limits.second)
        return result
    }

    @Test
    fun testInputGeneration() {
        val reference = listOf(1L,4,7,10)
        val generated = generateInputs(Pair(1,10), 4)
        Assert.assertArrayEquals(reference.toLongArray(), generated.toLongArray())
    }

    fun measureTime(function: (Int) -> Any, input: Int): Long {
        val now = System.currentTimeMillis()
        function.invoke(input)
        return System.currentTimeMillis() - now
    }

    fun testLinearity(inputs: List<Int>, times: List<Long>) {
        val linearTimes = generateInputs(Pair(times.first(), times.last()), times.size)
        for (i in 1..inputs.size - 2) {
            val actualTime = times[i]
            val idealTime = linearTimes[i]
            //in quadratic case actual time is LOWER than ideal.
        }
    }

    fun assertLinearPerformance(function: (Int) -> Any, limits: Pair<Int, Int>, steps: Int) {

    }
}