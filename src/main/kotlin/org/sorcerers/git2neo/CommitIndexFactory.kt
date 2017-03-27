package org.sorcerers.git2neo

import org.neo4j.graphdb.factory.GraphDatabaseFactory
import java.io.File

/**
 * @author vovak
 * @since 22/11/16
 */
class CommitIndexFactory {
    fun loadOrCreateCommitIndex(dir: File): CommitIndex {
        val db = GraphDatabaseFactory().newEmbeddedDatabaseBuilder(dir)
                .newGraphDatabase()
        Runtime.getRuntime().addShutdownHook(Thread({
            println("shutting down db")
            db.shutdown()
        }))
        return CommitIndex(db)
    }
}
