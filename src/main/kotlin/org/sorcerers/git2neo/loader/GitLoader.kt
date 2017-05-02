package org.sorcerers.git2neo.loader

import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.sorcerers.git2neo.model.Commit
import org.sorcerers.git2neo.model.CommitId
import org.sorcerers.git2neo.model.CommitInfo
import org.sorcerers.git2neo.model.Contributor
import org.sorcerers.git2neo.util.use
import java.io.File

/**
 * Created by vovak on 5/1/17.
 */
class GitLoader {
    fun loadGitRepo(path: String) {
        val repoDir = File(path)
        val repoBuilder = FileRepositoryBuilder()
        val repo = repoBuilder
                .setGitDir(repoDir)
                .readEnvironment()
                .findGitDir()
                .build()
        repo.use {
            val head = repo.getRef("refs/head/master")
            val walk = RevWalk(repo)
            walk.use {
                val headCommit = walk.parseCommit(head.objectId)
                walk.markStart(headCommit)
                walk.forEach {
                    loadCommit(it)
                }
            }
        }
    }

    fun loadCommit(commit: RevCommit) {
        val git2NeoCommit = commit.toGit2NeoCommit()
        //todo prepare db and add commit
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
        //todo also prepare changes
        return Commit(this.getCommitInfo(), emptyList())
    }
}