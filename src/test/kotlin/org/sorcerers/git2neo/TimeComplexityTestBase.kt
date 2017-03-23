package org.sorcerers.git2neo

import org.junit.Assert
import org.junit.Test
import java.util.*

/**
 * Created by vovak on 3/23/17.
 * Base class for testing the time complexity of methods by evaluating the "time vs input size" fun convexity
 */


open class TimeComplexityTestBase : CommitIndexTestBase() {
    fun generateInputs(limits: Pair<Long, Long>, steps: Int): List<Long> {
        if (steps < 3) throw IllegalArgumentException("The minimum number of steps is 3")
        if (limits.first >= limits.second) {
            throw IllegalArgumentException("Lower limit should be less than upper")
        }
        val result: MutableList<Long> = ArrayList()
        val increment = 1.0 * (limits.second - limits.first) / (steps - 1)

        result.add(limits.first)
        (1..steps - 2).mapTo(result) { Math.round(limits.first + it * increment) }
        result.add(limits.second)
        return result
    }

    @Test
    fun testInputGeneration() {
        val reference = listOf(1L, 4, 7, 10)
        val generated = generateInputs(Pair(1, 10), 4)
        Assert.assertArrayEquals(reference.toLongArray(), generated.toLongArray())
    }

    fun measureTime(function: (Int) -> Any, input: Long): Long {
        val now = System.currentTimeMillis()
        function.invoke(input.toInt())
        return System.currentTimeMillis() - now
    }

    fun isNotAboveLinear(inputs: List<Long>, times: List<Long>): Boolean {
        val linearTimes = generateInputs(Pair(times.first(), times.last()), times.size)
        val ratios: MutableList<Double> = ArrayList()
        for (i in 1..inputs.size - 2) {
            val timeIncrement = times[i] - times.first()
            val idealTimeIncrement = linearTimes[i] - times.first()
            //in quadratic case actual time is LOWER than ideal: the time function is convex.
            val ratio = 1.0 * timeIncrement / idealTimeIncrement
            ratios.add(ratio)
        }
        val avgRatio = ratios.average()
        println("Average ratio to ideal: $avgRatio")
        println("Inputs:")
        println(inputs)
        println("Times:")
        println(times)
        val threshold = 0.9
        return (avgRatio >= threshold)
    }

    fun isLinearPerformance(function: (Int) -> Any, limits: Pair<Long, Long>, steps: Int): Boolean {
        val inputs = generateInputs(limits, steps)
        val times = inputs.map { measureTime(function, it) }
        return isNotAboveLinear(inputs, times)
    }

    fun waitLinear(t: Int) {
        Thread.sleep(t.toLong())
    }

    fun waitQuadratic(t: Int) {
        Thread.sleep(t.toLong() * t.toLong())
    }

    fun waitConstant(t: Int) {
        Thread.sleep(t.toLong())
    }

    fun waitLinearPlusConstant(t: Int) {
        waitConstant(1000)
        waitLinear(t)
    }

    fun waitQuadraticPlusConstant(t: Int) {
        waitConstant(1000)
        waitQuadratic(t)
    }

    fun printLinear(t: Int) {
        for (i in 1..t) {
            println(i)
        }
    }

    fun printQuadratic(t: Int) {
        for (i in 1..t) {
            for (j in 1..i) {
                println("$j out of $i")
            }
        }
    }

    @Test
    fun testLinearMethodLooksLinear1() {
        Assert.assertTrue(isLinearPerformance({ waitLinear(it) }, Pair(100, 1000), 10))
    }

    @Test
    fun testLinearMethodLooksLinear2() {
        Assert.assertTrue(isLinearPerformance({ printLinear(it) }, Pair(100, 10000), 10))
    }

    @Test
    fun testLinearMethodLooksLinear3() {
        Assert.assertTrue(isLinearPerformance({ waitLinearPlusConstant(it) }, Pair(1, 10), 10))
    }

    @Test
    fun testSlowerMethodLooksNonLinear1() {
        Assert.assertFalse(isLinearPerformance({ waitQuadratic(it) }, Pair(10, 100), 5))
    }

    @Test
    fun testSlowerMethodLooksNonLinear2() {
        Assert.assertFalse(isLinearPerformance({ printQuadratic(it) }, Pair(10, 1000), 5))
    }

    @Test
    fun testSlowerMethodLooksNonLinear3() {
        Assert.assertFalse(isLinearPerformance({ waitQuadraticPlusConstant(it) }, Pair(10, 100), 5))
    }

}