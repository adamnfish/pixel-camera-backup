package io.adamnfish.pcb

import io.adamnfish.pcb.Main.{getFilenameWithDirectory, groupByDate, isValidFilename, listFilesAt}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayOutputStream, File, PrintStream}
import java.nio.file.Files
import scala.util.{Try, Using}

class IntegrationTests extends AnyFreeSpec with Matchers {

  // Helper methods for integration tests
  def withTempDirectory[T](f: File => T): T = {
    val tempDir = Files.createTempDirectory("pcb-test").toFile
    try {
      f(tempDir)
    } finally {
      deleteRecursively(tempDir)
    }
  }

  def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) {
      file.listFiles().foreach(deleteRecursively)
    }
    file.delete()
  }

  def createTestFile(dir: File, filename: String, content: String = "test content"): File = {
    val file = new File(dir, filename)
    Files.write(file.toPath, content.getBytes)
    file
  }

  def captureOutputToString[T](f: => T): (T, String) = {
    // For sbt, we need to use a different approach since System.out redirection doesn't work
    // We'll use a combination of temporarily redirecting and reading from our own capture
    import java.io._
    
    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos)
    val oldOut = System.out
    val oldErr = System.err
    
    try {
      System.setOut(ps)
      System.setErr(ps)
      val result = f
      ps.flush()
      (result, baos.toString())
    } finally {
      System.setOut(oldOut)  
      System.setErr(oldErr)
    }
  }

  // For now, let's focus on testing the file system effects rather than console output
  // since sbt makes it difficult to capture output in tests
  def runMainAndGetResult(args: Array[String]): Unit = {
    // Suppress output during testing by redirecting to nowhere temporarily
    val nullStream = new PrintStream(new ByteArrayOutputStream())
    val originalOut = System.out
    val originalErr = System.err
    
    try {
      System.setOut(nullStream)
      System.setErr(nullStream)
      Main.main(args)
    } finally {
      System.setOut(originalOut)
      System.setErr(originalErr)
    }
  }

  def listFilesRecursively(dir: File): List[String] = {
    if (!dir.exists()) List.empty
    else {
      def loop(file: File, relativePath: String): List[String] = {
        if (file.isDirectory) {
          file.listFiles().toList.flatMap { child =>
            val childPath = if (relativePath.isEmpty) child.getName else s"$relativePath/${child.getName}"
            loop(child, childPath)
          }
        } else {
          List(relativePath)
        }
      }
      loop(dir, "").sorted
    }
  }

  // Integration tests
  "Integration tests" - {
    "Error scenarios" - {
      "should handle non-existent input directory" in {
        withTempDirectory { outputDir =>
          val nonExistentInput = new File(outputDir, "non-existent")
          
          // Should not throw exception, but should handle gracefully
          noException should be thrownBy {
            runMainAndGetResult(Array(nonExistentInput.getAbsolutePath, outputDir.getAbsolutePath))
          }
          
          // Output directory should remain empty since input doesn't exist
          listFilesRecursively(outputDir) shouldBe empty
        }
      }

      "should handle non-existent output directory" in {
        withTempDirectory { inputDir =>
          val nonExistentOutput = new File(inputDir, "non-existent")
          
          // Should not throw exception, but should handle gracefully
          noException should be thrownBy {
            runMainAndGetResult(Array(inputDir.getAbsolutePath, nonExistentOutput.getAbsolutePath))
          }
        }
      }

      "should handle input that is not a directory" in {
        withTempDirectory { tempDir =>
          val inputFile = createTestFile(tempDir, "input.txt")
          val outputDir = new File(tempDir, "output")
          outputDir.mkdir()
          
          // Should not throw exception, but should handle gracefully  
          noException should be thrownBy {
            runMainAndGetResult(Array(inputFile.getAbsolutePath, outputDir.getAbsolutePath))
          }
          
          // Output directory should remain empty
          listFilesRecursively(outputDir) shouldBe empty
        }
      }

      "should handle output that is not a directory" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          inputDir.mkdir()
          val outputFile = createTestFile(tempDir, "output.txt")
          
          // Should not throw exception, but should handle gracefully
          noException should be thrownBy {
            runMainAndGetResult(Array(inputDir.getAbsolutePath, outputFile.getAbsolutePath))
          }
        }
      }

      "should handle invalid arguments gracefully" in {
        // Should not throw exception with too few arguments
        noException should be thrownBy {
          runMainAndGetResult(Array("single-arg"))
        }
        
        noException should be thrownBy {
          runMainAndGetResult(Array.empty)
        }
      }
    }

    "Dry-run mode (default)" - {
      "should not move files in dry-run mode" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          val outputDir = new File(tempDir, "output")
          inputDir.mkdir()
          outputDir.mkdir()
          
          // Create test photo files
          createTestFile(inputDir, "PXL_20210424_123456789.jpg", "photo1")
          createTestFile(inputDir, "IMG_20200911_135149.jpg", "photo2")
          createTestFile(inputDir, "VID_20200813_191831_LS.mp4", "video1")
          
          // Run in dry-run mode (default)
          runMainAndGetResult(Array(inputDir.getAbsolutePath, outputDir.getAbsolutePath))
          
          // Verify no files were actually moved
          listFilesRecursively(outputDir) shouldBe empty
          // All files should still be in input directory
          inputDir.listFiles().length shouldBe 3
          new File(inputDir, "PXL_20210424_123456789.jpg") should exist
          new File(inputDir, "IMG_20200911_135149.jpg") should exist
          new File(inputDir, "VID_20200813_191831_LS.mp4") should exist
        }
      }

      "should process only valid filenames in dry-run" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          val outputDir = new File(tempDir, "output")
          inputDir.mkdir()
          outputDir.mkdir()
          
          // Create mix of valid and invalid files
          createTestFile(inputDir, "PXL_20210424_123456789.jpg", "valid")
          createTestFile(inputDir, ".pending-1747559140-test.jpg", "invalid")
          createTestFile(inputDir, "invalid-format.jpg", "invalid")
          createTestFile(inputDir, "IMG_70200911_135149.jpg", "invalid-date")
          
          runMainAndGetResult(Array(inputDir.getAbsolutePath, outputDir.getAbsolutePath))
          
          // Verify no files were moved (dry-run mode)
          listFilesRecursively(outputDir) shouldBe empty
          // All files should still be in input directory
          inputDir.listFiles().length shouldBe 4
        }
      }

      "should handle files with same date in dry-run" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          val outputDir = new File(tempDir, "output")
          inputDir.mkdir()
          outputDir.mkdir()
          
          // Create files from same date
          createTestFile(inputDir, "PXL_20210424_123456789.jpg", "photo1")
          createTestFile(inputDir, "PXL_20210424_987654321.PORTRAIT.jpg", "photo2")
          createTestFile(inputDir, "IMG_20210424_555555.jpg", "photo3")
          
          runMainAndGetResult(Array(inputDir.getAbsolutePath, outputDir.getAbsolutePath))
          
          // Verify no files were moved (dry-run mode)
          listFilesRecursively(outputDir) shouldBe empty
          inputDir.listFiles().length shouldBe 3
        }
      }
    }

    "Commit mode (--commit)" - {
      "should actually move files to correct directory structure" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          val outputDir = new File(tempDir, "output")
          inputDir.mkdir()
          outputDir.mkdir()
          
          // Create test files
          createTestFile(inputDir, "PXL_20210424_123456789.jpg", "photo1")
          createTestFile(inputDir, "IMG_20200911_135149.jpg", "photo2")
          
          runMainAndGetResult(Array(inputDir.getAbsolutePath, outputDir.getAbsolutePath, "--commit"))
          
          // Verify files were moved to correct structure
          val outputFiles = listFilesRecursively(outputDir)
          outputFiles should contain("2021/04/24/PXL_20210424_123456789.jpg")
          outputFiles should contain("2020/09/11/IMG_20200911_135149.jpg")
          
          // Verify input directory is now empty (files moved, not copied)
          inputDir.listFiles() should be (empty)
          
          // Verify file contents are preserved
          val movedFile1 = new File(outputDir, "2021/04/24/PXL_20210424_123456789.jpg")
          val movedFile2 = new File(outputDir, "2020/09/11/IMG_20200911_135149.jpg")
          Files.readString(movedFile1.toPath) shouldBe "photo1"
          Files.readString(movedFile2.toPath) shouldBe "photo2"
        }
      }

      "should skip files that already exist with same size" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          val outputDir = new File(tempDir, "output")
          inputDir.mkdir()
          outputDir.mkdir()
          
          val testContent = "identical content"
          createTestFile(inputDir, "PXL_20210424_123456789.jpg", testContent)
          
          // Pre-create the target file with same content
          val targetDir = new File(outputDir, "2021/04/24")
          targetDir.mkdirs()
          createTestFile(targetDir, "PXL_20210424_123456789.jpg", testContent)
          
          runMainAndGetResult(Array(inputDir.getAbsolutePath, outputDir.getAbsolutePath, "--commit"))
          
          // Original file should still exist since it was skipped
          new File(inputDir, "PXL_20210424_123456789.jpg") should exist
          // Target file should still exist  
          new File(outputDir, "2021/04/24/PXL_20210424_123456789.jpg") should exist
        }
      }

      "should handle file exists with different size error" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          val outputDir = new File(tempDir, "output")
          inputDir.mkdir()
          outputDir.mkdir()
          
          createTestFile(inputDir, "PXL_20210424_123456789.jpg", "original content")
          
          // Pre-create target file with different content
          val targetDir = new File(outputDir, "2021/04/24")
          targetDir.mkdirs()
          createTestFile(targetDir, "PXL_20210424_123456789.jpg", "different content here")
          
          // Should handle gracefully without throwing
          noException should be thrownBy {
            runMainAndGetResult(Array(inputDir.getAbsolutePath, outputDir.getAbsolutePath, "--commit"))
          }
          
          // Original file should still exist since move failed
          new File(inputDir, "PXL_20210424_123456789.jpg") should exist
        }
      }

      "should create necessary directory structure" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          val outputDir = new File(tempDir, "output")
          inputDir.mkdir()
          outputDir.mkdir()
          
          createTestFile(inputDir, "PXL_20210424_123456789.jpg", "photo")
          
          runMainAndGetResult(Array(inputDir.getAbsolutePath, outputDir.getAbsolutePath, "--commit"))
          
          // Verify directory structure was created
          new File(outputDir, "2021") should exist
          new File(outputDir, "2021/04") should exist  
          new File(outputDir, "2021/04/24") should exist
          new File(outputDir, "2021/04/24/PXL_20210424_123456789.jpg") should exist
          
          // Input should be empty
          inputDir.listFiles() should be (empty)
        }
      }
    }

    "Edge cases" - {
      "should handle empty input directory" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          val outputDir = new File(tempDir, "output")
          inputDir.mkdir()
          outputDir.mkdir()
          
          // Should handle gracefully without errors
          noException should be thrownBy {
            runMainAndGetResult(Array(inputDir.getAbsolutePath, outputDir.getAbsolutePath))
          }
          
          // Output should remain empty
          listFilesRecursively(outputDir) shouldBe empty
        }
      }

      "should handle directory files (IMG_20200921_134908)" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          val outputDir = new File(tempDir, "output")
          inputDir.mkdir()
          outputDir.mkdir()
          
          // Create a directory that looks like a file (burst photos create these)
          val burstDir = new File(inputDir, "IMG_20200921_134908")
          burstDir.mkdir()
          createTestFile(burstDir, "photo1.jpg", "burst1")
          createTestFile(burstDir, "photo2.jpg", "burst2")
          
          runMainAndGetResult(Array(inputDir.getAbsolutePath, outputDir.getAbsolutePath, "--commit"))
          
          // Verify the entire directory was moved
          val movedDir = new File(outputDir, "2020/09/21/IMG_20200921_134908")
          movedDir should exist
          movedDir.isDirectory shouldBe true
          new File(movedDir, "photo1.jpg") should exist
          new File(movedDir, "photo2.jpg") should exist
          
          // Verify contents are preserved
          Files.readString(new File(movedDir, "photo1.jpg").toPath) shouldBe "burst1"
          Files.readString(new File(movedDir, "photo2.jpg").toPath) shouldBe "burst2"
          
          // Input should be empty
          inputDir.listFiles() should be (empty)
        }
      }

      "should handle various file extensions" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          val outputDir = new File(tempDir, "output")
          inputDir.mkdir()
          outputDir.mkdir()
          
          createTestFile(inputDir, "PXL_20210424_123456789.jpg", "photo")
          createTestFile(inputDir, "PXL_20210424_987654321.PORTRAIT.jpg", "portrait")
          createTestFile(inputDir, "VID_20210424_555555_LS.mp4", "video")
          
          runMainAndGetResult(Array(inputDir.getAbsolutePath, outputDir.getAbsolutePath, "--commit"))
          
          // All should be in same date directory
          val targetDir = new File(outputDir, "2021/04/24")
          new File(targetDir, "PXL_20210424_123456789.jpg") should exist
          new File(targetDir, "PXL_20210424_987654321.PORTRAIT.jpg") should exist
          new File(targetDir, "VID_20210424_555555_LS.mp4") should exist
          
          // Verify content preservation
          Files.readString(new File(targetDir, "PXL_20210424_123456789.jpg").toPath) shouldBe "photo"
          Files.readString(new File(targetDir, "PXL_20210424_987654321.PORTRAIT.jpg").toPath) shouldBe "portrait"
          Files.readString(new File(targetDir, "VID_20210424_555555_LS.mp4").toPath) shouldBe "video"
          
          // Input should be empty
          inputDir.listFiles() should be (empty)
        }
      }

      "should process only files with valid names and skip filtered files" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          val outputDir = new File(tempDir, "output")
          inputDir.mkdir()
          outputDir.mkdir()
          
          // Valid filename and files filtered out by isValidFilename
          createTestFile(inputDir, "PXL_20210424_123456789.jpg", "valid")
          createTestFile(inputDir, ".pending-file.jpg", "pending") // filtered out by isValidFilename
          
          // Should only process valid files  
          runMainAndGetResult(Array(inputDir.getAbsolutePath, outputDir.getAbsolutePath, "--commit"))
          
          // Only valid file should be moved
          val outputFiles = listFilesRecursively(outputDir)
          outputFiles should contain("2021/04/24/PXL_20210424_123456789.jpg")
          outputFiles should have size 1
          
          // Filtered file should remain in input
          val remainingFiles = inputDir.listFiles().map(_.getName).sorted
          remainingFiles should contain(".pending-file.jpg")
          remainingFiles should have length 1
        }
      }

      "should handle files with invalid date format" in {
        withTempDirectory { tempDir =>
          val inputDir = new File(tempDir, "input")
          val outputDir = new File(tempDir, "output")
          inputDir.mkdir()
          outputDir.mkdir()
          
          // Files with invalid date format cause early failure
          createTestFile(inputDir, "IMG_99999999_123456.jpg", "invalid-date")
          
          // Should handle gracefully without throwing
          noException should be thrownBy {
            runMainAndGetResult(Array(inputDir.getAbsolutePath, outputDir.getAbsolutePath, "--commit"))
          }
          
          // No files should be moved due to error
          listFilesRecursively(outputDir) shouldBe empty
          
          // Original file should remain in input
          new File(inputDir, "IMG_99999999_123456.jpg") should exist
        }
      }
    }
  }
}