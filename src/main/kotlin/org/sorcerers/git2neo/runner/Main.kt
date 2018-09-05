package org.sorcerers.git2neo.runner

import org.sorcerers.git2neo.driver.loader.processUnzippedRepo
import org.sorcerers.git2neo.driver.loader.unzipRepo
import java.io.File


fun main(args: Array<String>) {
    val name = "git2neo"
    val repo = unzipRepo(name)

    val gitPath = repo.absolutePath + "/.git"

    processUnzippedRepo(name, File(gitPath))
}
