package org.sorcerers.git2neo.loader

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.neo4j.test.TestGraphDatabaseFactory
import org.sorcerers.git2neo.driver.CommitIndex
import org.sorcerers.git2neo.loader.util.isGitRepo
import org.sorcerers.git2neo.loader.util.unzipRepo
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


    @Test
    fun testUnzip() {
        val repo = unzipRepo("ima")
        Assert.assertTrue(isGitRepo(repo.absolutePath))
    }

    @Test
    fun testIma() {
        val repo = unzipRepo("ima")
        val loader = GitLoader(myIndex)
        loader.loadGitRepo(repo.absolutePath)

        val history = myIndex.getChangesHistoriesForCommit(CommitId("1cb7a8f941790cbe4b56bae135cda108962b28dd"))
        println(history)
        Assert.assertTrue(history.isNotEmpty())
        Assert.assertEquals(20, history[0].items.size)
    }
}