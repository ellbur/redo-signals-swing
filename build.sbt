
organization := "com.github.ellbur"

name := "redo-signals-swing"

version := "0.9.1"

scalaVersion := "2.11.6"

scalaSource in Compile <<= baseDirectory(_ / "src")

javaSource in Compile <<= baseDirectory(_ / "src")

scalaSource in Test <<= baseDirectory(_ / "test")

javaSource in Test <<= baseDirectory(_ / "test")

resourceDirectory in Compile <<= baseDirectory(_ / "resources")

resourceDirectory in Test <<= baseDirectory(_ / "test-resources")

libraryDependencies ++= Seq(
  "com.github.ellbur" %% "redo-signals-core" % "0.9.9",
  "com.github.ellbur" %% "dependent-map" % "2.0-SNAPSHOT",
  "org.scala-lang" % "scala-actors" % "2.11.7",
  "com.github.ellbur" %% "lapper" % "1.0-SNAPSHOT"
)

resolvers += "Local Maven Repository" at file(Path.userHome.absolutePath + "/.m2/repository").toURL.toString

resolvers += "ellbur repo" at "s3://ellbur-public-maven-repository/"

credentials += Credentials(Path.userHome / ".ivy2" / ".local-archive-internal-credentials.txt")

publishMavenStyle := true

//publishTo := Some("ellbur repo" at "s3://ellbur-public-maven-repository/")
publishTo := Some("Local Maven Repository" at file(Path.userHome.absolutePath + "/.m2/repository").toURL.toString)

