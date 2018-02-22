package org.sorcerers.git2neo.loader

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.neo4j.test.TestGraphDatabaseFactory
import org.sorcerers.git2neo.driver.CommitIndex
import org.sorcerers.git2neo.loader.util.cleanUnpackedRepos
import org.sorcerers.git2neo.loader.util.isGitRepo
import org.sorcerers.git2neo.loader.util.unzipRepo
import org.sorcerers.git2neo.model.Action
import org.sorcerers.git2neo.model.CommitId
import java.io.File

class GitLoaderTest {
    lateinit var myIndex: CommitIndex

    @Before
    fun initIndex() {
        val path = "./testdb"
        val db = TestGraphDatabaseFactory().newImpermanentDatabase(File(path))
        myIndex = CommitIndex(db, javaClass.canonicalName)
    }

    @After
    fun clean() {
        cleanUnpackedRepos()
    }

    private fun loadRepo(name: String) {
        val repo = unzipRepo(name)
        val loader = GitLoader(myIndex)
        loader.loadGitRepo(repo.absolutePath)
    }


    @Test
    fun testUnzip() {
        val repo = unzipRepo("ima")
        Assert.assertTrue(isGitRepo(repo.absolutePath))
    }

    @Test
    fun testIma() {
        loadRepo("ima")

        val history = myIndex.getChangesHistoriesForCommit(CommitId("1cb7a8f941790cbe4b56bae135cda108962b28dd"))
        println(history)
        Assert.assertTrue(history.isNotEmpty())
        Assert.assertEquals(20, history[0].items.size)
        Assert.assertTrue(history[0].items.any { it.action == Action.CREATED })
    }

    @Test
    fun testLinearHistory() {
        loadRepo("repo1")

        val history = myIndex.getChangesHistoriesForCommit(CommitId("d8d4a6fa7cde15cd974e0d765b2a54619f8993a9"))
        println(history)
        Assert.assertTrue(history.isNotEmpty())
        Assert.assertEquals(3, history[0].items.size)
        Assert.assertTrue(history[0].items.any { it.action == Action.CREATED })
    }

    @Test
    fun testHistoryWithMerge() {
        loadRepo("repo2")

        val history = myIndex.getChangesHistoriesForCommit(CommitId("13e7d745549489fb89b0fd7e63b358a03e32bcbf"))
        println(history)
        Assert.assertTrue(history.isNotEmpty())
        Assert.assertEquals(5, history[0].items.size)
        Assert.assertTrue(history[0].items.any { it.action == Action.CREATED })
    }
}