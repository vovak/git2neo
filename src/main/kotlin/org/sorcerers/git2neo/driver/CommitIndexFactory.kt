package org.sorcerers.git2neo.driver

import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.factory.GraphDatabaseSettings
import java.io.File

/**
 * @author vovak
 * @since 22/11/16
 */
class CommitIndexFactory {
    fun loadCommitIndex(db: GraphDatabaseService, logPrefix: String): CommitIndex {
        Runtime.getRuntime().addShutdownHook(Thread({
            println("shutting down db")
            db.shutdown()
        }))
        return CommitIndex(db, logPrefix)
    }

    fun loadOrCreateCommitIndex(dir: File, logPrefix: String): CommitIndex {
        val db = GraphDatabaseFactory().newEmbeddedDatabaseBuilder(dir)
                .setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
                .newGraphDatabase()
        return loadCommitIndex(db, logPrefix)
    }
}
