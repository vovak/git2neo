package org.sorcerers.git2neo.util

import org.sorcerers.git2neo.model.FileRevisionId

fun getFileRevisionId(commit: String, path: String): FileRevisionId {
    return FileRevisionId("$commit#$path")
}