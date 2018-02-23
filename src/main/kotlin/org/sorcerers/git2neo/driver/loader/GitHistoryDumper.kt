package org.sorcerers.git2neo.driver.loader

import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.sorcerers.git2neo.driver.CommitIndexFactory
import org.sorcerers.git2neo.driver.loader.util.extractFolder
import org.sorcerers.git2neo.driver.loader.util.getRepoArchivePath
import org.sorcerers.git2neo.driver.loader.util.getRepoUnpackedPath
import org.sorcerers.git2neo.model.CommitId
import java.io.File

fun getAllPaths(dir: File): List<String> {
    val iter = FileUtils.iterateFiles(dir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE)
    return iter.asSequence()
            .filter { it.isFile }
            .map { it.toRelativeString(dir) }
            .filter { !it.startsWith(".git/") && !it.startsWith(".idea/") }
            .toList()
}

fun unzipRepo(name: String): File {
    extractFolder(getRepoArchivePath(name), getRepoUnpackedPath())
    return File(getRepoUnpackedPath() + "/$name")
}

fun loadDb(name: String): GraphDatabaseService {
    val path = "./testneo4jdb_$name"

    val graphDb = GraphDatabaseFactory()
            .newEmbeddedDatabaseBuilder(File(path))
            .newGraphDatabase()

    return graphDb
}

fun processRepo(name: String) {
    val testDir = unzipRepo(name)

    val allPaths = getAllPaths(testDir)


    println(allPaths)

    val db = loadDb(name)
    val commitIndex = CommitIndexFactory().loadCommitIndex(db, "db_$name")
    val repoInfo = GitLoader(commitIndex).loadGitRepo(testDir.absolutePath)



    println("head: " + repoInfo.headSha)
    println("Retrieving commit history from head...")
    val history = commitIndex.getCommitHistory(CommitId(repoInfo.headSha), { _ -> true })
    println(history)
    println("Files in history: " + history.items.map { it.changes.map { it.path }.toList() }.flatten().toSet().size)
    println("Files in repo: " + allPaths.size)

    //TODO traverse the repo and calculate histories for files present in the repo, both ways. Store it and dump.

    db.shutdown()
}