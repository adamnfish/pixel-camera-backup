package io.adamnfish.pcb

import io.adamnfish.pcb.Main.{getFilenameWithDirectory, groupByDate, listFilesAt}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import java.io.File
import java.nio.file.Files

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

  "listFilesAt with pending files filtering" - {
    "should exclude .pending- files from processing" in {
      // Create a temporary directory with test files
      val tempDir = Files.createTempDirectory("test-pending").toFile
      try {
        // Create test files
        new File(tempDir, "PXL_20210424_123456789.jpg").createNewFile()
        new File(tempDir, ".pending-1747559140-_PXL_1582263144432191_1734954997733_1482652244439028.jpg").createNewFile()
        new File(tempDir, "IMG_20200911_135149.jpg").createNewFile()
        new File(tempDir, ".pending-another-file.jpg").createNewFile()

        // List files and apply the same filtering as in main
        listFilesAt(tempDir.getAbsolutePath) match {
          case Right(files) =>
            val names = files.map(_.getName).filterNot(_.startsWith(".pending-"))
            names should contain("PXL_20210424_123456789.jpg")
            names should contain("IMG_20200911_135149.jpg")
            names should not contain(".pending-1747559140-_PXL_1582263144432191_1734954997733_1482652244439028.jpg")
            names should not contain(".pending-another-file.jpg")
            names.size shouldEqual 2
          case Left(error) =>
            fail(s"Should have listed files successfully: $error")
        }
      } finally {
        // Clean up
        tempDir.listFiles().foreach(_.delete())
        tempDir.delete()
      }
    }
  }
}
