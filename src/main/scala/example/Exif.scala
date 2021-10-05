package example

import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.tiff.constants.{ExifTagConstants, TiffDirectoryType}
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoAscii

import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


object Exif {
  val tzOffsetTagInfo = new TagInfoAscii("OffsetTimeOriginal", 0x9011, 8, TiffDirectoryType.EXIF_DIRECTORY_EXIF_IFD)

    def readPixelDate(file: File): Either[String, ZonedDateTime] = {
      val m = Imaging.getMetadata(file)
      m match {
        case jpegMetadata: JpegImageMetadata =>
          val dateField = jpegMetadata.getExif.findField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)
          val tzOffsetField = jpegMetadata.getExif.findField(tzOffsetTagInfo)
          val dateTimeStr = s"${dateField.getValue.asInstanceOf[String]}${tzOffsetField.getValue.asInstanceOf[String]}"
          println(dateTimeStr)
          val imageTime = ZonedDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ssxxx"))
          println(imageTime)
          Right(imageTime)
        case _ =>
          Left(s"incorrect metadata type ${m.getClass.getName}")
      }
    }
}
