package io.adamnfish.pcb

import io.adamnfish.pcb.Main.program
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.{Files, Path}
import scala.util.{Try, Using}

class IntegrationTest extends AnyFreeSpec with Matchers {

  "program integration tests" - {
    
    "error conditions" - {
      
      "should report error when input directory does not exist" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDir { outputDir =>
          val nonExistentInput = "/tmp/does-not-exist-12345"
          program(List(nonExistentInput, outputDir.toString))
          
          val stderr = testOutput.getStderr.mkString("")
          stderr should include("failed to copy files:")
          stderr should include(s"$nonExistentInput does not exist")
        }
      }
      
      "should report error when output directory does not exist" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDir { inputDir =>
          val nonExistentOutput = "/tmp/does-not-exist-67890"
          program(List(inputDir.toString, nonExistentOutput))
          
          val stderr = testOutput.getStderr.mkString("")
          stderr should include("failed to copy files:")
          stderr should include(s"$nonExistentOutput does not exist")
        }
      }
      
      "should report error with invalid arguments" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        program(List("only-one-arg"))
        
        val stderr = testOutput.getStderr.mkString("")
        stderr should include("failed to copy files:")
        stderr should include("1st arg is input dir, 2nd arg is output dir")
      }
    }
    
    "dry run mode" - {
      
      "should report planned operations without making changes" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          // Create test files with valid names
          createTestFile(inputDir, "PXL_20210424_123456789.jpg")
          createTestFile(inputDir, "IMG_20200920_121437.jpg")
          createTestFile(inputDir, "VID_20200711_214648_LS.mp4")
          
          program(List(inputDir.toString, outputDir.toString))
          
          val stdout = testOutput.getStdout.mkString("")
          stdout should include("Would have processed 3 files, but this was a dry run")
          
          // Verify no actual files were moved
          outputDir.toFile.listFiles() should have length 0
        }
      }
      
      "should show directory creation plans" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          createTestFile(inputDir, "PXL_20210424_123456789.jpg")
          
          program(List(inputDir.toString, outputDir.toString))
          
          val stdout = testOutput.getStdout.mkString("")
          stdout should include(s"+ ${outputDir}${File.separator}2021${File.separator}04${File.separator}24")
        }
      }
      
      "should show file move plans" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          createTestFile(inputDir, "PXL_20210424_123456789.jpg")
          
          program(List(inputDir.toString, outputDir.toString))
          
          val stdout = testOutput.getStdout.mkString("")
          stdout should include("> PXL_20210424_123456789.jpg")
          stdout should include(s"2021${File.separator}04${File.separator}24${File.separator}PXL_20210424_123456789.jpg")
        }
      }
      
      "should skip files with invalid filenames" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          createTestFile(inputDir, "PXL_20210424_123456789.jpg")  // valid
          createTestFile(inputDir, ".pending-1747559140-test.jpg")  // invalid (should be skipped by isValidFilename)
          
          program(List(inputDir.toString, outputDir.toString))
          
          val stdout = testOutput.getStdout.mkString("")
          stdout should include("Would have processed 1 files, but this was a dry run")
        }
      }
      
      "should report error for files with invalid date patterns" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          createTestFile(inputDir, "invalid-name.jpg")  // invalid (no date pattern)
          
          program(List(inputDir.toString, outputDir.toString))
          
          val stderr = testOutput.getStderr.mkString("")
          stderr should include("Problems found:")
          stderr should include("Invalid filename invalid-name.jpg")
        }
      }
      
      "should handle multiple file types correctly" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          createTestFile(inputDir, "PXL_20210424_123456789.jpg")
          createTestFile(inputDir, "IMG_20200920_121437.jpg")
          createTestFile(inputDir, "VID_20200711_214648_LS.mp4")
          createTestFile(inputDir, "PXL_20210424_183416412.PORTRAIT.jpg")
          createTestFile(inputDir, "IMG_20200921_134908")  // directory format
          
          program(List(inputDir.toString, outputDir.toString))
          
          val stdout = testOutput.getStdout.mkString("")
          stdout should include("Would have processed 5 files, but this was a dry run")
        }
      }
      
      "should handle empty input directory gracefully" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          // No files in input directory
          program(List(inputDir.toString, outputDir.toString))
          
          val stdout = testOutput.getStdout.mkString("")
          stdout should include("Would have processed 0 files, but this was a dry run")
        }
      }
      
      "should list all problems at once in dry run mode" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          // Create multiple files with problems
          createTestFile(inputDir, "invalid-file1.jpg")       // No date pattern
          createTestFile(inputDir, "another-bad-file.mp4")    // No date pattern
          createTestFile(inputDir, "PXL_20210424_123456789.jpg")  // Valid file
          createTestFile(inputDir, ".pending-test.jpg")       // Should be filtered out by isValidFilename
          
          program(List(inputDir.toString, outputDir.toString))
          
          val stderr = testOutput.getStderr.mkString("")
          val stdout = testOutput.getStdout.mkString("")
          
          // Should report all filename problems at once
          stderr should include("Problems found:")
          stderr should include("Invalid filename invalid-file1.jpg")
          stderr should include("Invalid filename another-bad-file.mp4")
          stderr should include("The above problems would need to be resolved before running with --commit")
          
          // Should still process the valid file
          stdout should include("Would have processed 1 files, but this was a dry run")
          
          // Should not fail fast - both errors should be present
          val errorLines = stderr.split("\n").filter(_.contains("Invalid filename"))
          errorLines should have length 2
        }
      }
      
      "should list all file conflict problems at once in commit mode" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          // Create files that will have conflicts
          val fileName1 = "PXL_20210424_123456789.jpg"
          val fileName2 = "PXL_20210424_999999999.jpg"
          val fileName3 = "IMG_20200920_121437.jpg"
          
          createTestFile(inputDir, fileName1, "original content 1")
          createTestFile(inputDir, fileName2, "original content 2")
          createTestFile(inputDir, fileName3, "original content 3")
          
          // Create target directories and files with different content to cause conflicts
          val targetDir1 = outputDir.resolve("2021/04/24")
          val targetDir2 = outputDir.resolve("2020/09/20")
          Files.createDirectories(targetDir1)
          Files.createDirectories(targetDir2)
          createTestFile(targetDir1, fileName1, "different content 1")
          createTestFile(targetDir1, fileName2, "different content 2")
          createTestFile(targetDir2, fileName3, "different content 3")
          
          program(List(inputDir.toString, outputDir.toString, "--commit"))
          
          val stderr = testOutput.getStderr.mkString("")
          
          // Should report all conflicts at once
          stderr should include("Problems found:")
          stderr should include(s"File ${targetDir1}${File.separator}${fileName1} already exists and appears to have different contents")
          stderr should include(s"File ${targetDir1}${File.separator}${fileName2} already exists and appears to have different contents")
          stderr should include(s"File ${targetDir2}${File.separator}${fileName3} already exists and appears to have different contents")
          
          // Should not fail fast - all errors should be present
          val conflictLines = stderr.split("\n").filter(_.contains("already exists and appears to have different contents"))
          conflictLines should have length 3
        }
      }
      
      "should show both problems and successful operations together" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          // Mix of valid and invalid files
          createTestFile(inputDir, "PXL_20210424_123456789.jpg")   // Valid
          createTestFile(inputDir, "IMG_20200920_121437.jpg")      // Valid
          createTestFile(inputDir, "invalid-file.jpg")             // Invalid - no date
          createTestFile(inputDir, "VID_20200711_214648_LS.mp4")   // Valid
          createTestFile(inputDir, "another-bad.mp4")              // Invalid - no date
          
          program(List(inputDir.toString, outputDir.toString))
          
          val stderr = testOutput.getStderr.mkString("")
          val stdout = testOutput.getStdout.mkString("")
          
          // Should show problems
          stderr should include("Problems found:")
          stderr should include("Invalid filename invalid-file.jpg")
          stderr should include("Invalid filename another-bad.mp4")
          stderr should include("The above problems would need to be resolved before running with --commit")
          
          // Should also show successful operations count
          stdout should include("Would have processed 3 files, but this was a dry run")
          
          // Both errors should be listed
          val errorLines = stderr.split("\n").filter(_.contains("Invalid filename"))
          errorLines should have length 2
        }
      }
    }
    
    "commit mode" - {
      
      "should actually move files to correct directory structure" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          createTestFile(inputDir, "PXL_20210424_123456789.jpg")
          createTestFile(inputDir, "IMG_20200920_121437.jpg")
          
          program(List(inputDir.toString, outputDir.toString, "--commit"))
          
          val stdout = testOutput.getStdout.mkString("")
          stdout should include("Processed 2 files")
          
          // Verify directory structure was created
          val expectedDir1 = outputDir.resolve("2021/04/24")
          val expectedDir2 = outputDir.resolve("2020/09/20")
          expectedDir1.toFile should exist
          expectedDir2.toFile should exist
          
          // Verify files were moved
          expectedDir1.resolve("PXL_20210424_123456789.jpg").toFile should exist
          expectedDir2.resolve("IMG_20200920_121437.jpg").toFile should exist
          
          // Verify files were removed from input
          inputDir.resolve("PXL_20210424_123456789.jpg").toFile should not(exist)
          inputDir.resolve("IMG_20200920_121437.jpg").toFile should not(exist)
        }
      }
      
      "should handle existing files with same size gracefully" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          val fileName = "PXL_20210424_123456789.jpg"
          val testContent = "test file content"
          
          createTestFile(inputDir, fileName, testContent)
          
          // Create the target directory and file with same content
          val targetDir = outputDir.resolve("2021/04/24")
          Files.createDirectories(targetDir)
          createTestFile(targetDir, fileName, testContent)
          
          program(List(inputDir.toString, outputDir.toString, "--commit"))
          
          val stdout = testOutput.getStdout.mkString("")
          stdout should include("File already exists (skipping)")
          stdout should include("Processed 1 files")
        }
      }
      
      "should report error for existing files with different size" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          val fileName = "PXL_20210424_123456789.jpg"
          
          createTestFile(inputDir, fileName, "original content")
          
          // Create target directory and file with different content
          val targetDir = outputDir.resolve("2021/04/24")
          Files.createDirectories(targetDir)
          createTestFile(targetDir, fileName, "different content size")
          
          program(List(inputDir.toString, outputDir.toString, "--commit"))
          
          val stderr = testOutput.getStderr.mkString("")
          stderr should include("Problems found:")
          stderr should include("already exists and appears to have different contents")
        }
      }
      
      "should organize files by date in subdirectories correctly" in {
        val testOutput = new TestOutput()
        given Output = testOutput
        
        withTempDirs { (inputDir, outputDir) =>
          // Create files from different dates
          createTestFile(inputDir, "PXL_20210424_123456789.jpg")
          createTestFile(inputDir, "PXL_20210424_999999999.jpg")  // same date
          createTestFile(inputDir, "IMG_20200920_121437.jpg")     // different date
          createTestFile(inputDir, "VID_20200711_214648_LS.mp4")  // another date
          
          program(List(inputDir.toString, outputDir.toString, "--commit"))
          
          val stdout = testOutput.getStdout.mkString("")
          stdout should include("Processed 4 files")
          
          // Verify correct directory structure
          outputDir.resolve("2021/04/24/PXL_20210424_123456789.jpg").toFile should exist
          outputDir.resolve("2021/04/24/PXL_20210424_999999999.jpg").toFile should exist
          outputDir.resolve("2020/09/20/IMG_20200920_121437.jpg").toFile should exist
          outputDir.resolve("2020/07/11/VID_20200711_214648_LS.mp4").toFile should exist
        }
      }
    }
  }
  
  // Helper methods
  
  private def withTempDir[T](test: Path => T): T = {
    val tempDir = Files.createTempDirectory("pcb-test")
    try {
      test(tempDir)
    } finally {
      deleteRecursively(tempDir)
    }
  }
  
  private def withTempDirs[T](test: (Path, Path) => T): T = {
    val inputDir = Files.createTempDirectory("pcb-input")
    val outputDir = Files.createTempDirectory("pcb-output")
    try {
      test(inputDir, outputDir)
    } finally {
      deleteRecursively(inputDir)
      deleteRecursively(outputDir)
    }
  }
  
  private def createTestFile(dir: Path, fileName: String, content: String = "test content"): Unit = {
    Files.write(dir.resolve(fileName), content.getBytes)
  }
  
  private def deleteRecursively(path: Path): Unit = {
    Try {
      if (Files.isDirectory(path)) {
        Files.list(path).forEach(deleteRecursively)
      }
      Files.deleteIfExists(path)
    }
  }
}