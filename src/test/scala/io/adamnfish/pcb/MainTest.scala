package io.adamnfish.pcb

import io.adamnfish.pcb.Main.{getFilenameWithDirectory, groupByDate, isValidFilename, listFilesAt, reportResults, reportInitialError, collectAllResults}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class MainTest extends AnyFreeSpec with Matchers {
  "groupByDate" - {
    "is correct for this example" in {
      groupByDate(
        List(
          Filenames("2020/09/20", "IMG_20200920_121437.jpg"),
          Filenames("2020/09/11", "PXL_20200911_211218498.jpg"),
          Filenames("2020/09/20", "IMG_20200920_128665.jpg"),
          Filenames("2021/06/27", "PXL_20210627_134736911.jpg")
        )
      ) shouldEqual List(
        "2020/09/20" -> List(
          "IMG_20200920_121437.jpg",
          "IMG_20200920_128665.jpg"
        ),
        "2020/09/11" -> List("PXL_20200911_211218498.jpg"),
        "2021/06/27" -> List("PXL_20210627_134736911.jpg")
      )
    }
  }

  "getFilenameWithDirectory" - {
    "works for an IMG example" in {
      getFilenameWithDirectory("IMG_20200920_121437.jpg") shouldEqual Right(
        Filenames(
          "2020/09/20",
          "IMG_20200920_121437.jpg"
        )
      )
    }

    "works for a PXL example" in {
      getFilenameWithDirectory("PXL_20200911_211218498.jpg") shouldEqual Right(
        Filenames(
          "2020/09/11",
          "PXL_20200911_211218498.jpg"
        )
      )
    }

    "works for a PXL portrait example" in {
      getFilenameWithDirectory(
        "PXL_20210424_183416412.PORTRAIT.jpg"
      ) shouldEqual Right(
        Filenames(
          "2021/04/24",
          "PXL_20210424_183416412.PORTRAIT.jpg"
        )
      )
    }

    "works for an IMG directory example" in {
      getFilenameWithDirectory("IMG_20200921_134908") shouldEqual Right(
        Filenames(
          "2020/09/21",
          "IMG_20200921_134908"
        )
      )
    }

    "works for a VID example" in {
      getFilenameWithDirectory("VID_20200711_214648_LS.mp4") shouldEqual Right(
        Filenames(
          "2020/07/11",
          "VID_20200711_214648_LS.mp4"
        )
      )
    }

    "fails if the date fragment is missing" in {
      getFilenameWithDirectory("IMG.jpg").isLeft shouldEqual true
    }

    "fails if the date fragment is invalid" in {
      getFilenameWithDirectory("IMG_7020921_134908.jpg").isLeft shouldEqual true
    }

    "fails for pending files" in {
      getFilenameWithDirectory(".pending-1747559140-_PXL_1582263144432191_1734954997733_1482652244439028.jpg").isLeft shouldEqual true
    }
  }

  "isValidFilename" - {
    "should return true for normal photo files" in {
      isValidFilename("PXL_20210424_123456789.jpg") shouldEqual true
      isValidFilename("IMG_20200911_135149.jpg") shouldEqual true
      isValidFilename("VID_20200711_214648_LS.mp4") shouldEqual true
    }

    "should return false for .pending- files" in {
      isValidFilename(".pending-1747559140-_PXL_1582263144432191_1734954997733_1482652244439028.jpg") shouldEqual false
      isValidFilename(".pending-another-file.jpg") shouldEqual false
      isValidFilename(".pending-test") shouldEqual false
    }

    "should return true for files with dots that don't start with .pending-" in {
      isValidFilename(".hidden-file.jpg") shouldEqual true
      isValidFilename("file.with.dots.jpg") shouldEqual true
    }
  }

  "collectAllResults" - {
    "should collect all successes when all operations succeed" in {
      val input = List("a", "b", "c")
      val f = (s: String) => Right(s.toUpperCase)
      
      val (errors, successes) = collectAllResults(input)(f)
      
      errors shouldEqual List.empty
      successes shouldEqual List("A", "B", "C")
    }

    "should collect all errors when all operations fail" in {
      val input = List("1", "2", "3")
      val f = (s: String) => Left(s"Error with $s")
      
      val (errors, successes) = collectAllResults(input)(f)
      
      errors shouldEqual List("Error with 1", "Error with 2", "Error with 3")
      successes shouldEqual List.empty
    }

    "should collect both errors and successes when operations are mixed" in {
      val input = List("good1", "bad", "good2", "bad2")
      val f = (s: String) => if (s.startsWith("good")) Right(s.toUpperCase) else Left(s"Error with $s")
      
      val (errors, successes) = collectAllResults(input)(f)
      
      errors shouldEqual List("Error with bad", "Error with bad2")
      successes shouldEqual List("GOOD1", "GOOD2")
    }

    "should handle empty input" in {
      val input = List.empty[String]
      val f = (s: String) => Right(s.toUpperCase)
      
      val (errors, successes) = collectAllResults(input)(f)
      
      errors shouldEqual List.empty
      successes shouldEqual List.empty
    }
  }



  "reportInitialError" - {
    "should output error message to stderr" in {
      val testOutput = new TestOutput()
      given Output = testOutput
      
      reportInitialError("Test error message")
      
      val stderr = testOutput.getStderr.mkString("")
      stderr should include("failed to copy files:")
      stderr should include("Test error message")
    }
  }

  "reportResults" - {
    "should report problems and dry run message when errors exist in dry run mode" in {
      val testOutput = new TestOutput()
      given Output = testOutput
      val arguments = Arguments("/input", "/output", true)
      val errors = List("Error 1", "Error 2")
      val successes = List("file1", "file2")
      
      reportResults(arguments, errors, successes)
      
      val stderr = testOutput.getStderr.mkString("")
      val stdout = testOutput.getStdout.mkString("")
      
      stderr should include("Problems found:")
      stderr should include("Error 1")
      stderr should include("Error 2")
      stderr should include("The above problems would need to be resolved before running with --commit")
      stdout should include("Would have processed 2 files, but this was a dry run")
    }

    "should report problems without dry run message in commit mode" in {
      val testOutput = new TestOutput()
      given Output = testOutput
      val arguments = Arguments("/input", "/output", false)
      val errors = List("Conflict error")
      val successes = List("file1")
      
      reportResults(arguments, errors, successes)
      
      val stderr = testOutput.getStderr.mkString("")
      val stdout = testOutput.getStdout.mkString("")
      
      stderr should include("Problems found:")
      stderr should include("Conflict error")
      stderr should not include("The above problems would need to be resolved before running with --commit")
      stdout should include("Processed 1 files")
    }

    "should report successful operations when no errors in dry run mode" in {
      val testOutput = new TestOutput()
      given Output = testOutput
      val arguments = Arguments("/input", "/output", true)
      val errors = List.empty[String]
      val successes = List("file1", "file2", "file3")
      
      reportResults(arguments, errors, successes)
      
      val stderr = testOutput.getStderr.mkString("")
      val stdout = testOutput.getStdout.mkString("")
      
      stderr shouldEqual ""
      stdout should include("Would have processed 3 files, but this was a dry run")
    }

    "should report successful operations when no errors in commit mode" in {
      val testOutput = new TestOutput()
      given Output = testOutput
      val arguments = Arguments("/input", "/output", false)
      val errors = List.empty[String]
      val successes = List("file1", "file2")
      
      reportResults(arguments, errors, successes)
      
      val stderr = testOutput.getStderr.mkString("")
      val stdout = testOutput.getStdout.mkString("")
      
      stderr shouldEqual ""
      stdout should include("Processed 2 files")
    }

    "should handle empty results gracefully" in {
      val testOutput = new TestOutput()
      given Output = testOutput
      val arguments = Arguments("/input", "/output", true)
      val errors = List.empty[String]
      val successes = List.empty[String]
      
      reportResults(arguments, errors, successes)
      
      val stderr = testOutput.getStderr.mkString("")
      val stdout = testOutput.getStdout.mkString("")
      
      stderr shouldEqual ""
      stdout should include("Would have processed 0 files, but this was a dry run")
    }
  }
}
