package org.sorcerers.git2neo

import java.io.Serializable

/**
* @author vovak
* @since 17/11/16
*/

interface Id<T> : Serializable {
    fun stringId(): String
}

data class CommitId(val idString: String) : Id<Commit> {
    override fun stringId(): String {
        return idString
    }
}

data class FileRevisionId(val id: String) : Id<FileRevision> {
    override fun stringId(): String {
        return id
    }
}

data class Contributor(val email: String) : Serializable

data class CommitInfo(
        val id:            CommitId,
        val author:        Contributor,
        val committer:     Contributor,
        val authorTime:    Long,
        val committerTime: Long,
        val parents:       Collection<CommitId>
) : Serializable

enum class Action {CREATED, MODIFIED, DELETED}

data class FileRevision(
        val id: FileRevisionId,
        val path: String,
        val commitId: CommitId,
        val action: Action,
        val relatedPath: String?
)

data class Commit(val info: CommitInfo, val changes: Collection<FileRevision>)

data class History<T>(val items: List<T>)

interface CommitStorage {
    fun add(commit: Commit)
    fun addAll(commits: Collection<Commit>)
    fun get(id: CommitId): Commit?
}

interface HistoryQueriable<T> {
    fun getHistory(head: Id<T>, filter: (T) -> Boolean): History<T>
}