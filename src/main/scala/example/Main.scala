package example

import java.io.File


object Hello {
  def main(args: Array[String]): Unit = {
    val result = for {
      arguments <- parseArgs(args)
      inputDir <- validateDirExists(arguments.inputDir)
      outputDir <- validateDirExists(arguments.outputDir)
      files <- listFilesAt(inputDir)
      fileNames = files.map(_.getName)
      filenamesWithDirs <- elTraverse(fileNames)(getFilenameWithDirectory)
      groupedFilenamesWithDirs = groupByDate(filenamesWithDirs)
      _ <- elTraverse(groupedFilenamesWithDirs) { case (dirname, filenames) =>
        for {
          _ <- createDirectory(outputDir, dirname)
          _ <- elTraverse(filenames)(filename => moveFile(inputDir, outputDir)((dirname, filename)))
        } yield ()
      }
    } yield filenamesWithDirs
    println(result)
  }

  def parseArgs(args: Array[String]): Either[String, Arguments] = {
    args.toList match {
      case input :: output :: _ =>
        Right(Arguments(input, output))
      case _ =>
        Left("first arg is input dir, second arg is output dir")
    }
  }

  def validateDirExists(path: String): Either[String, String] = {
    val dir = new File(path)
    if (!dir.exists) {
      Left(s"$path does not exist")
    } else if (!dir.isDirectory) {
      Left(s"$path is not a directory")
    } else {
      Right(path)
    }
  }

  def groupByDate(filenamesWithDirs: List[(String, String)]): List[(String, List[String])] = {
    filenamesWithDirs.groupMap(_._1)(_._2).toList
  }

  def listFilesAt(dirname: String): Either[String, List[File]] = {
    val dir = new File(dirname)
    if (!dir.exists) {
      Left(s"$dirname does not exist")
    } else if (!dir.isDirectory) {
      Left(s"$dirname is not a directory")
    } else {
      Right(dir.listFiles.toList)
    }
  }

  def getFilenameWithDirectory(filename: String): Either[String, (String, String)] = {
    filename.split('_').toList match {
      case _ :: dateTimeStr :: _ =>
        Right((dateTimeStr, filename))
      case _ =>
        Left(s"Invalid filename $filename")
    }
  }

  def createDirectory(root: String, dirname: String): Either[String, Unit] = {
    if (!root.endsWith(File.separator)) {
      Left(s"The root must be a directory and end with ${File.separator}")
    } else {
      val newDirPath = s"$root$dirname"
      val newDir = new File(newDirPath)
      if (newDir.exists() && !newDir.isDirectory) {
        Left(s"$dirname already exists and is not a directory")
      } else if (newDir.exists() && newDir.isDirectory) {
        Right(())
      } else {
        try {
          println(s"+ create dir $dirname")
          Right(())
//          if (newDir.mkdir()) {
//            Right(())
//          } else {
//            Left(s"$dirname was not created")
//          }
        } catch {
          case e: SecurityException =>
            Left(s"Did not have permission to write directory $newDirPath (${e.getMessage})")
        }
      }
    }
  }

  def moveFile(oldroot: String, newRoot: String)(filenameWithDirectory: (String, String)): Either[String, Unit] = {
    if (!oldroot.endsWith(File.separator)) {
      Left(s"The old root (input) must be a directory and end with ${File.separator}")
    } else if (!newRoot.endsWith(File.separator)) {
      Left(s"The new root (output) must be a directory and end with ${File.separator}")
    } else {
      val (dirname, filename) = filenameWithDirectory
      val currentFilePath = s"$oldroot$filename"
      val newFilePath = s"$newRoot$dirname${File.separator}$filename"
      val currentFile = new File(currentFilePath)
      val newFile = new File(newFilePath)
      if (!currentFile.exists()) {
        Left(s"File $currentFilePath does not exist")
      } else if (newFile.exists()) {
        Left(s"File $newFilePath already exists") // TODO: do we want to silently ignore this?
      } else {
        println(s"> move $filename \t $dirname${File.separator}$filename")
        Right(())
//        if (currentFile.renameTo(newFile)) {
//          Right(())
//        } else {
//          Left(s"$currentFilePath $newFilePath was not moved")
//        }
      }
    }
  }

  // decided to use the filename date instead, for simplicity

  def elTraverse[A, B, L](la: List[A])(f: A => Either[L, B]): Either[L, List[B]] = {
    la.foldRight[Either[L, List[B]]](Right(Nil)) { case (a, accE) =>
      for {
        b <- f(a)
        acc <- accE
      } yield b :: acc
    }
  }
}

case class Arguments(
  inputDir: String,
  outputDir: String,
)