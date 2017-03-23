package org.sorcerers.git2neo

import java.io.File

/**
 * Created by vovak on 3/23/17.
 */
fun createEmbeddedIndex(path: String): CommitIndex {
    val dir = File(path)
    dir.mkdirs()
    dir.deleteOnExit()
    return CommitIndexFactory().loadOrCreateCommitIndex(dir)
}