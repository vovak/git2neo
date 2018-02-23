package org.sorcerers.git2neo.driver.loader

import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.RenameDetector
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.merge.ThreeWayMergeStrategy
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
    data class RepositoryInfo(val headSha: String)

    fun loadGitRepo(path: String): RepositoryInfo {
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
            val revWalk = RevWalk(repo)
            revWalk.use {
                val headCommit = revWalk.parseCommit(headId)
                revWalk.markStart(headCommit)
                revWalk.forEach {
                    println("Git2Neo Loader: processing commit ${it.id.abbreviate(8).name()} :: ${it.fullMessage} ")
                    loadCommit(it, repo, revWalk)
                }
            }
        }
        commitIndex.updateChangeParentConnectionsForAllNodes()
        return RepositoryInfo(headId.name)
    }

    fun loadCommit(commit: RevCommit, repository: Repository, revWalk: RevWalk) {
        val git2NeoCommit = commit.toGit2NeoCommit(repository, revWalk)
        commitIndex.add(git2NeoCommit, updateParents = false)
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
        val action = this.changeType.toGit2NeoAction()
        val effectiveOldPath = if (this.oldPath == "/dev/null") null else this.oldPath
        println(action.toString() + "  " + effectiveOldPath + "->" + this.newPath)
        return FileRevision(FileRevisionId("id"),
                this.newPath,
                effectiveOldPath,
                commit,
                this.changeType.toGit2NeoAction())
    }


    fun RevCommit.getChanges(commit: CommitInfo, repository: Repository, revWalk: RevWalk): List<FileRevision> {
        val parents = this.parents
        println(parents.count())

        fun getChangesForSimpleCommit(): List<DiffEntry> {
            val treeWalk = TreeWalk(repository)
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

        fun getDiffByAutomerge(): List<DiffEntry> {
            return try {
                val autoMergedTree = AutoMerger
                        .automerge(repository, revWalk, this, ThreeWayMergeStrategy.RECURSIVE, false)
                val treeWalk = TreeWalk(repository)

                treeWalk.addTree(autoMergedTree)
                treeWalk.addTree(this.tree)

                //if a file is similar to one of parents, it is NOT changed in the merge commit!
                //Entries should be empty then.
                val entries = DiffEntry.scan(treeWalk)
                entries
            } catch (e: Exception) {
                println(e.message)
                emptyList()
            }
        }

        var diffEntries: List<DiffEntry>
        diffEntries = if (parents.count() < 2) {
            getChangesForSimpleCommit()
        } else {
            getDiffByAutomerge()
        }

        val renameDetector = RenameDetector(repository)

        renameDetector.addAll(diffEntries)

        diffEntries = renameDetector.compute()

        return diffEntries.map { it.toFileRevision(commit) }
    }


    fun RevCommit.toGit2NeoCommit(repository: Repository, revWalk: RevWalk): Commit {
        val commitInfo = this.getCommitInfo()
        return Commit(commitInfo, this.getChanges(commitInfo, repository, revWalk))
    }
}