package org.sorcerers.git2neo

import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.factory.GraphDatabaseSettings
import java.io.File

/**
 * @author vovak
 * @since 22/11/16
 */
class CommitIndexFactory {
    fun loadOrCreateCommitIndex(dir: File, logPrefix: String): CommitIndex {
        val db = GraphDatabaseFactory().newEmbeddedDatabaseBuilder(dir)
                .setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
                .newGraphDatabase()
        Runtime.getRuntime().addShutdownHook(Thread({
            println("shutting down db")
            db.shutdown()
        }))
        return CommitIndex(db, logPrefix)
    }
}
