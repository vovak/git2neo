package org.sorcerers.git2neo.loader.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile


fun getRepoArchivePath(name: String): String = "testData/zipped/$name.zip"
fun getRepoUnpackedPath(): String = "testData/unpacked/"

fun unzipRepo(name: String): File {
    extractFolder(getRepoArchivePath(name), getRepoUnpackedPath())
    return File(getRepoUnpackedPath()+"/$name")
}

private fun extractFolder(zipFile: String, extractFolder: String) {
    try {
        val BUFFER = 2048
        val file = File(zipFile)

        val zip = ZipFile(file)

        File(extractFolder).mkdir()
        val zipFileEntries = zip.entries()

        // Process each entry
        while (zipFileEntries.hasMoreElements()) {
            // grab a zip file entry
            val entry = zipFileEntries.nextElement() as ZipEntry
            val currentEntry = entry.name

            val destFile = File(extractFolder, currentEntry)
            //destFile = new File(newPath, destFile.getName());
            val destinationParent = destFile.parentFile

            // create the parent directory structure if needed
            destinationParent.mkdirs()

            if (!entry.isDirectory) {
                val inputStream = BufferedInputStream(zip
                        .getInputStream(entry))
                var currentByte: Int
                // establish buffer for writing file
                val data = ByteArray(BUFFER)

                // write the current file to disk
                val fos = FileOutputStream(destFile)
                val dest = BufferedOutputStream(fos,
                        BUFFER)

                // read and write until last byte is encountered
                currentByte = inputStream.read(data, 0, BUFFER)
                while (currentByte != -1) {
                    dest.write(data, 0, currentByte)
                    currentByte = inputStream.read(data, 0, BUFFER)
                }
                dest.flush()
                dest.close()
                inputStream.close()
            }


        }
    } catch (e: Exception) {
        println("ERROR: " + e.message)
    }

}

fun isGitRepo(path: String): Boolean {
    var gitDataFolder = File(path+File.separator+".git")
    println("Checking if ${gitDataFolder.absolutePath} is a git folder...")
    return gitDataFolder.exists()
}