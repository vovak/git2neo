package org.sorcerers.git2neo

import java.util.function.Predicate

/**
 * Created by vovak on 17/11/16.
 */

interface Id<T>;

data class CommitId(val id: String) : Id<Commit>

data class FileRevisionId(val id: String) : Id<FileRevision>

data class Contributor(val email: String)

data class CommitInfo(
        val id:            CommitId,
        val author:        Contributor,
        val committer:     Contributor,
        val authorTime:    Long,
        val committerTime: Long,
        val parents:       Collection<CommitId>
)

enum class Action {CREATED, MODIFIED, DELETED}

data class FileRevision(
        val id: FileRevisionId,
        val path: String,
        val commitInfo: CommitInfo,
        val action: Action,
        val relatedPath: String?
)

data class Commit(val info: CommitInfo, val changes: Collection<FileRevision>)

data class History<T>(val items: List<T>)

interface CommitStorage {
    fun add(commit: Commit)
    fun get(id: CommitId): Commit?
}

interface HistoryQueriable<T> {
    fun getHistory(head: Id<T>, filter: Predicate<T>): History<T>
}