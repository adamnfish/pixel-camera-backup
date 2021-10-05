package example

import example.Hello.groupByDate
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers


class MainTest extends AnyFreeSpec with Matchers {
  "groupByDate" - {
    "is correct for this example" in {
      groupByDate(List(
        ("20200920", "IMG_20200920_121437.jpg"),
        ("20200911", "PXL_20200911_211218498.jpg"),
        ("20200920", "IMG_20200920_128665.jpg"),
        ("20210627", "PXL_20210627_134736911.jpg"),
      )) shouldEqual Map(
        "20200920" -> List(
          "IMG_20200920_121437.jpg",
          "IMG_20200920_128665.jpg",
        ),
        "20200911" -> List("PXL_20200911_211218498.jpg"),
        "20210627" -> List("PXL_20210627_134736911.jpg"),
      )
    }
  }
}
