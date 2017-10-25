package org.sorcerers.git2neo.loader

import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.sorcerers.git2neo.driver.CommitIndex
import org.sorcerers.git2neo.model.Commit
import org.sorcerers.git2neo.model.CommitId
import org.sorcerers.git2neo.model.CommitInfo
import org.sorcerers.git2neo.model.Contributor
import org.sorcerers.git2neo.util.use
import java.io.File

/**
 * Created by vovak on 5/1/17.
 */
class GitLoader(val commitIndex: CommitIndex) {
    fun loadGitRepo(path: String) {
        val repoDir = File(path)
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
                    loadCommit(it)
                }
            }
        }
        commitIndex.updateChangeParentConnectionsForAllNodes()
    }

    fun loadCommit(commit: RevCommit) {
        val git2NeoCommit = commit.toGit2NeoCommit()
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


    fun RevCommit.toGit2NeoCommit(): Commit {
//        val changes = this.
        return Commit(this.getCommitInfo(), emptyList())
    }
}