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
    fun testHistoryWithMergeAndNonTrivialConflict() {
        loadRepo("repo2")

        val history = myIndex.getChangesHistoriesForCommit(CommitId("13e7d745549489fb89b0fd7e63b358a03e32bcbf"))
        println(history)
        Assert.assertTrue(history.isNotEmpty())
        //During the merge, the file was modified. It counts as an edit.
        Assert.assertEquals(5, history[0].items.size)
        Assert.assertTrue(history[0].items.any { it.action == Action.CREATED })
    }

    @Test
    fun testHistoryWithMergeAndNoConflict() {
        loadRepo("repo4")

        val history = myIndex.getChangesHistoriesForCommit(CommitId("08267689cbc24080c9bf655b3bccfeb5fecc4bcd"))
        println(history)
        Assert.assertTrue(history.isNotEmpty())
        //During the merge, the version from master was accepted. It does NOT count as an edit. (tricky to detect)
        Assert.assertEquals(4, history[0].items.size)
        Assert.assertTrue(history[0].items.any { it.action == Action.CREATED })
    }

    @Test
    fun testHistoryWithMergeAndTrivialConflict() {
        loadRepo("repo3")

        val history = myIndex.getChangesHistoriesForCommit(CommitId("94abe6763cb0c1eb0b131ef11af23611701b6c20"))
        println(history)
        Assert.assertTrue(history.isNotEmpty())
        //During the merge, the version from master was accepted. It does NOT count as an edit. (tricky to detect)
        Assert.assertEquals(5, history[0].items.size)
        Assert.assertTrue(history[0].items.any { it.action == Action.CREATED })
    }

    @Test
    fun testHistoryWithRename() {
        loadRepo("repo5")

        val history = myIndex.getChangesHistoriesForCommit(CommitId("820ab8873d6689e879cd2ba97f5c5cd6db09f82f"))
        println(history)
        Assert.assertTrue(history.isNotEmpty())
        Assert.assertEquals(4, history[0].items.size)
        Assert.assertTrue(history[0].items.any { it.action == Action.CREATED })
    }



    @Test
    fun testLargerHistory200Commits() {
        loadRepo("changedb")
    }

//    @Test
    fun testLargerHistory5kCommits() {
        loadRepo("webpack")
        val history = myIndex.getChangesHistoriesForCommit(CommitId("000b34e0c2a23563de9b0e862215846deb3710e7"))
        Assert.assertTrue(history.isNotEmpty())
        history[0].items.forEach{println(it.commitInfo.id)}
        Assert.assertEquals(35, history[0].items.size)
        Assert.assertEquals(1, history[0].items.filter { it.action == Action.CREATED }.size)
    }

//    @Test
    fun testLargerHistory50kCommits() {
        loadRepo("git")
    }
}