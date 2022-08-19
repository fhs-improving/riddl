package com.reactific.riddl.utils

import java.io.File
import java.net.URL
import java.nio.file.{Files, Path}
import java.io.InputStream
import java.nio.file.{StandardCopyOption}

object PathUtils {

  /**
   * Determine if a program is in the current system PATH environment var
   * @param program The name of the program to find
   * @return True if the program is in the path, false otherwise
   */
  def existsInPath(program: String): Boolean = {
    System.getenv("PATH").split(java.util.regex.Pattern.quote(
      File.pathSeparator)
    ).map(Path.of(_)).exists(p => Files.isExecutable(p.resolve(program)))
  }


  def copyURLToDir(from: URL, destDir: Path): String = {
    val nameParts = from.getFile.split('/')
    if (nameParts.nonEmpty) {
      val fileName = nameParts.last
      val in: InputStream = from.openStream
      destDir.toFile.mkdirs()
      val dl_path = destDir.resolve(fileName)
      Files.copy(in, dl_path, StandardCopyOption.REPLACE_EXISTING)
      fileName
    } else {""}
  }
}