package org.sorcerers.git2neo

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.kernel.configuration.BoltConnector
import org.sorcerers.git2neo.driver.CommitIndexFactory
import org.sorcerers.git2neo.loader.GitLoader
import java.io.File


/**
 * Created by vovak on 17/11/16.
 */
fun main(args: Array<String>) {
    println("Hello world")
    val db: GraphDatabaseService = loadDb()
    Runtime.getRuntime().addShutdownHook(Thread({
        println("shutting down db")
        db.shutdown()
    }))

    val repoPath = "/Users/vovak/code/repos/ima/.git"
    val commitIndex = CommitIndexFactory().loadCommitIndex(db, "test_db")
    val loader = GitLoader(commitIndex)
    loader.loadGitRepo(repoPath)

    while(true) {
        Thread.sleep(1000)
    }
}

fun loadDb(): GraphDatabaseService {
    val path = "./neo4jdb"

    val bolt = BoltConnector()

    val graphDb = GraphDatabaseFactory()
            .newEmbeddedDatabaseBuilder(File(path))
            .setConfig(bolt.type, "BOLT")
            .setConfig(bolt.enabled, "true")
            .setConfig(bolt.listen_address, "localhost:10000")
            .newGraphDatabase()

    return graphDb
}