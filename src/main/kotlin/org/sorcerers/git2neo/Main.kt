package org.sorcerers.git2neo

import org.sorcerers.git2neo.driver.loader.processRepo


/**
 * Created by vovak on 17/11/16.
 */


fun main(args: Array<String>) {
    val repoName = "webpack"
    processRepo(repoName)
}

