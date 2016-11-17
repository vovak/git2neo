package org.sorcerers.git2neo

import java.util.function.Predicate

/**
* @author vovak
* @since 17/11/16
*/
class CommitIndex() : CommitStorage, HistoryQueriable<Commit> {
    override fun add(commit: Commit) {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun get(id: CommitId): Commit? {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getHistory(head: Id<Commit>, filter: Predicate<Commit>): History<Commit> {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}