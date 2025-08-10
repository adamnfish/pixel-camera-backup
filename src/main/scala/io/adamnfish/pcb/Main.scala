package io.adamnfish.pcb

import java.io.File

object Main {
  val separator = File.separator

  def main(args: Array[String]): Unit = {
    given Output = ConsoleOutput
    program(args.toList)
  }

  def program(args: List[String])(using output: Output): Unit = {
    val result = for {
      arguments <- parseArgs(args)
      inputDir <- validateDirExists(arguments.inputDir)
      outputDir <- validateDirExists(arguments.outputDir)
      files <- listFilesAt(inputDir)
      names = files.map(_.getName).filter(isValidFilename)
      (filenameErrors, validFilenames) = processFilenames(names)
      groupedFilenames = groupByDate(validFilenames)
      (operationErrors, successfulMoves) = processFileOperations(arguments, inputDir, outputDir, groupedFilenames)
      allErrors = filenameErrors ++ operationErrors
    } yield (arguments, allErrors, successfulMoves)
    
    result.fold(
      { err =>
        reportInitialError(err)
      },
      { case (arguments, allErrors, successfulMoves) =>
        reportResults(arguments, allErrors, successfulMoves)
      }
    )
  }

  def isValidFilename(filename: String): Boolean = {
    !filename.startsWith(".pending-")
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

  def groupByDate(filenames: List[Filenames]): List[(String, List[String])] = {
    filenames.groupMap(_.dirs)(_.filename).toList
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

  // optimistically allow this to run for 80 yrs
  private val DateTimeStr = raw"(2[01]\d\d)([01]\d)([0123]\d)".r

  def getFilenameWithDirectory(filename: String): Either[String, Filenames] = {
    filename.split('_').toList match {
      case _ :: dateTimeStr :: _ =>
        dateTimeStr match {
          case DateTimeStr(year, month, day) =>
            Right {
              Filenames(
                s"$year$separator$month$separator$day",
                filename
              )
            }
          case _ =>
            Left(
              s"$filename didn't appear to include a valid date fragment e.g. 20210424 in `PXL_20210424_123063941.jpg`"
            )
        }
      case _ =>
        Left(s"Invalid filename $filename")
    }
  }

  def createDirectories(
      dryRun: Boolean,
      root: String,
      dirs: String
  )(using output: Output): Either[String, Unit] = {
    if (root.endsWith(separator)) {
      Left(s"The root must be a directory and must not end with $separator")
    } else {
      val newDirPath = s"$root$separator$dirs"
      val newDir = new File(newDirPath)
      if (newDir.exists() && !newDir.isDirectory) {
        Left(s"$newDirPath already exists and is not a directory")
      } else if (newDir.exists() && newDir.isDirectory) {
        Right(())
      } else if (dryRun) {
        // TODO: debug flag argument for this info?
        output.stdoutln(s"+ $newDirPath")
        Right(())
      } else {
        try {
          if (newDir.mkdirs()) {
            Right(())
          } else {
            Left(s"$newDirPath was not created")
          }
        } catch {
          case e: SecurityException =>
            Left(
              s"Did not have permission to write directory $newDirPath (${e.getMessage})"
            )
        }
      }
    }
  }

  def moveFile(
      dryRun: Boolean,
      oldroot: String,
      newRoot: String,
      dirs: String,
      filename: String
  )(using output: Output): Either[String, Unit] = {
    if (oldroot.endsWith(separator)) {
      Left(
        s"The old root (input) must be a directory and must not end with $separator"
      )
    } else if (newRoot.endsWith(separator)) {
      Left(
        s"The new root (output) must be a directory and must not end with $separator"
      )
    } else {
      val currentFilePath = s"$oldroot$separator$filename"
      val newFilePath = s"$newRoot$separator$dirs$separator$filename"
      val currentFile = new File(currentFilePath)
      val newFile = new File(newFilePath)
      if (!currentFile.exists()) {
        Left(s"File $currentFilePath does not exist")
      } else if (newFile.exists()) {
        val newLength = newFile.length()
        if (newLength == currentFile.length()) {
          output.stdoutln(s"File already exists (skipping): $newFilePath")
          Right(())
        } else {
          Left(
            s"File $newFilePath already exists and appears to have different contents to $currentFilePath"
          ) // TODO: do we want to silently ignore this?
        }
      } else if (dryRun) {
        // TODO: debug flag argument for this info?
        output.stdoutln(s"> $filename \t $dirs$separator$filename")
        Right(())
      } else {
        try {
          if (currentFile.renameTo(newFile)) {
            Right(())
          } else {
            Left(s"$currentFilePath $newFilePath was not moved")
          }
        } catch {
          case e: SecurityException =>
            Left(
              s"Did not have permission to move file to $newFilePath (${e.getMessage})"
            )
        }
      }
    }
  }

  def parseArgs(args: List[String]): Either[String, Arguments] = {
    args match {
      case input :: output :: "--commit" :: _ =>
        Right(Arguments(input, output, false))
      case input :: output :: _ =>
        Right(Arguments(input, output, true))
      case _ =>
        Left(
          "1st arg is input dir, 2nd arg is output dir. To run for real, add --commit to the end"
        )
    }
  }

  def elTraverse[A, B, L](
      la: List[A]
  )(f: A => Either[L, B]): Either[L, List[B]] = {
    la.foldRight[Either[L, List[B]]](Right(Nil)) { case (a, accE) =>
      for {
        b <- f(a)
        acc <- accE
      } yield b :: acc
    }
  }

  // Collects all results and all errors instead of failing fast
  def collectAllResults[A, B](
      la: List[A]
  )(f: A => Either[String, B]): (List[String], List[B]) = {
    val (errors, successes) = la.foldLeft((List.empty[String], List.empty[B])) { case ((errors, successes), a) =>
      f(a) match {
        case Left(error) => (errors :+ error, successes)
        case Right(success) => (errors, successes :+ success)
      }
    }
    (errors, successes)
  }

  def processFilenames(names: List[String]): (List[String], List[Filenames]) = {
    collectAllResults(names)(getFilenameWithDirectory)
  }

  def processFileOperations(
      arguments: Arguments,
      inputDir: String, 
      outputDir: String,
      groupedFilenames: List[(String, List[String])]
  )(using output: Output): (List[String], List[String]) = {
    groupedFilenames.foldLeft((List.empty[String], List.empty[String])) {
      case ((allErrors, allSuccesses), (dirs, filenames)) =>
        // Try to create directory
        val dirResult = createDirectories(arguments.dryRun, outputDir, dirs)
        val dirErrors = dirResult.fold(List(_), _ => List.empty)
        
        // Try to move all files in this directory
        val (moveErrors, moveSuccesses) = collectAllResults(filenames) { filename =>
          moveFile(arguments.dryRun, inputDir, outputDir, dirs, filename).map(_ => s"$filename -> $dirs$separator$filename")
        }
        
        (allErrors ++ dirErrors ++ moveErrors, allSuccesses ++ moveSuccesses)
    }
  }

  def reportInitialError(err: String)(using output: Output): Unit = {
    output.stderrln("failed to copy files:")
    output.stderrln(err)
  }

  def reportResults(
      arguments: Arguments,
      allErrors: List[String],
      successfulMoves: List[String]
  )(using output: Output): Unit = {
    if (allErrors.nonEmpty) {
      output.stderrln("Problems found:")
      allErrors.foreach(output.stderrln)
      if (arguments.dryRun) {
        output.stderrln("")
        output.stderrln("The above problems would need to be resolved before running with --commit")
      }
    }
    
    if (successfulMoves.nonEmpty || allErrors.isEmpty) {
      if (arguments.dryRun) {
        output.stdoutln(
          s"Would have processed ${successfulMoves.length} files, but this was a dry run"
        )
      } else {
        output.stdoutln(s"Processed ${successfulMoves.length} files")
      }
    }
  }
}
