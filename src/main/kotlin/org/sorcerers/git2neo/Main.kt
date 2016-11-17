package org.sorcerers.git2neo

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.factory.GraphDatabaseFactory
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
}

fun loadDb(): GraphDatabaseService {
    val path = "./neo4jdb"
    val db = GraphDatabaseFactory().newEmbeddedDatabase(File(path))
    return db
}