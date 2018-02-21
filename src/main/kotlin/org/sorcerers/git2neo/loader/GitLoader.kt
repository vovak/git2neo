package org.sorcerers.git2neo.loader

import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
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

    fun DiffEntry.toFileRevision(commit: CommitInfo): FileRevision {
        //TODO meaningful IDs and actions
        return FileRevision(FileRevisionId("id"), this.oldPath, null, commit, Action.MODIFIED)
    }

    fun RevCommit.getChanges(commit: CommitInfo, repository: Repository): List<FileRevision> {
        fun emptyTree(): ObjectId {
            var oi: ObjectInserter? = null
            try {
                oi = repository.newObjectInserter()
                return oi!!.insert(Constants.OBJ_TREE, byteArrayOf())
            } finally {
                if (oi != null) {
                    oi.flush()
                }
            }
        }

        val treeWalk = TreeWalk(repository)
        treeWalk.addTree(this.tree)
        val parents = this.parents

        val revWalk = RevWalk(repository)
        var from: RevCommit? = null
        if (parents.isEmpty()) {
            //TODO handle initial commit
            treeWalk.addTree(emptyTree())
        } else {
            //TODO multiple parents (figure out treewalk structure)
            from = parents[0]
        }
        if (from != null) treeWalk.addTree(from.tree)

        val diffEntries = DiffEntry.scan(treeWalk)

        return diffEntries.map { it.toFileRevision(commit) }
    }


    fun RevCommit.toGit2NeoCommit(repository: Repository): Commit {
        val commitInfo = this.getCommitInfo()
        return Commit(commitInfo, this.getChanges(commitInfo, repository))
    }
}