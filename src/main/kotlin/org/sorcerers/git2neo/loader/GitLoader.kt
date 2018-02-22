package org.sorcerers.git2neo.loader

import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.sorcerers.git2neo.driver.CommitIndex
import org.sorcerers.git2neo.model.*
import org.sorcerers.git2neo.util.use
import java.io.File

/**
 * Created by vovak on 5/1/17.
 */
class GitLoader(val commitIndex: CommitIndex) {
    fun loadGitRepo(path: String) {
        val repoDir = File(path + "/.git")
        val repoBuilder = FileRepositoryBuilder()
        val repo = repoBuilder
                .setGitDir(repoDir)
                .readEnvironment()
                .findGitDir()
                .setMustExist(true)
                .build()

        val headId = repo.resolve(Constants.HEAD)

        repo.use {
            val walk = RevWalk(repo)
            walk.use {
                val headCommit = walk.parseCommit(headId)
                walk.markStart(headCommit)
                walk.forEach {
                    //                    println("Git2Neo Loader: processing commit ${it.id.abbreviate(8).name()} :: ${it.fullMessage} ")
                    loadCommit(it, repo)
                }
            }
        }
        commitIndex.updateChangeParentConnectionsForAllNodes()
    }

    fun loadCommit(commit: RevCommit, repository: Repository) {
        val git2NeoCommit = commit.toGit2NeoCommit(repository)
        commitIndex.add(git2NeoCommit, updateParents = true)
    }

    fun PersonIdent.toContributor(): Contributor {
        return Contributor(this.emailAddress)
    }

    fun RevCommit.getCommitInfo(): CommitInfo {
        val id = this.id.toObjectId().name
        val authorTime = this.authorIdent.`when`.time
        val committerTime = this.committerIdent.`when`.time
        val parentIds = this.parents.map { CommitId(it.id.toObjectId().name) }

        return CommitInfo(CommitId(id),
                this.authorIdent.toContributor(),
                this.committerIdent.toContributor(),
                authorTime, committerTime,
                parentIds)
    }

    fun DiffEntry.ChangeType.toGit2NeoAction(): Action {
        if (this == DiffEntry.ChangeType.ADD) return Action.CREATED
        if (this == DiffEntry.ChangeType.DELETE) return Action.DELETED
        if (this == DiffEntry.ChangeType.MODIFY) return Action.MODIFIED
        return Action.MOVED
    }

    fun DiffEntry.toFileRevision(commit: CommitInfo): FileRevision {
        return FileRevision(FileRevisionId("id"), this.newPath, null, commit, this.changeType.toGit2NeoAction())
    }



    fun RevCommit.getChanges(commit: CommitInfo, repository: Repository): List<FileRevision> {
        val treeWalk = TreeWalk(repository)

        val parents = this.parents
        println(parents.count())

        fun getChangesForSimpleCommit(): List<DiffEntry> {
            var from: RevCommit? = null
            if (parents.isEmpty()) {
                treeWalk.addTree(EmptyTreeIterator())
            } else {
                from = parents[0]
                treeWalk.addTree(from.tree)
            }

            treeWalk.addTree(this.tree)
            return DiffEntry.scan(treeWalk)
        }

        fun getMergeDiff(): List<DiffEntry> {
            return emptyList()
        }

        var diffEntries: List<DiffEntry> = emptyList()
        diffEntries = if (parents.count() < 2) {
            getChangesForSimpleCommit()
        } else {
            getMergeDiff()
        }

        return diffEntries.map { it.toFileRevision(commit) }
    }


    fun RevCommit.toGit2NeoCommit(repository: Repository): Commit {
        val commitInfo = this.getCommitInfo()
        return Commit(commitInfo, this.getChanges(commitInfo, repository))
    }
}