ThisBuild / scalaVersion := "2.13.16"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "io.adamnfish"
ThisBuild / organizationName := "adamnfish"

lazy val root = (project in file("."))
  .settings(
    name := "pixel-camera-backup",
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-imaging" % "1.0.0-alpha6",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )
