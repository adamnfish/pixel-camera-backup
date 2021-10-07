ThisBuild / scalaVersion     := "2.13.4"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "io.adamnfish"
ThisBuild / organizationName := "adamnfish"

lazy val root = (project in file("."))
  .settings(
    name := "pixel-camera-backup",
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-imaging" % "1.0-alpha2",
      "org.scalatest" %% "scalatest" % "3.2.2" % Test,
    )
  )
