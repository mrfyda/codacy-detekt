import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import scala.util.parsing.json.JSON
import scala.io.Source

organization := "codacy"

name := "codacy-detekt"

version := "1.0.0-SNAPSHOT"

val languageVersion = "2.12.7"

scalaVersion := languageVersion

resolvers ++= Seq(
  "Arturbosch Detekt" at "https://dl.bintray.com/arturbosch/code-analysis/"
)

lazy val toolVersionKey = settingKey[String]("The version of the underlying tool retrieved from patterns.json")

toolVersionKey := {
  val jsonFile = (resourceDirectory in Compile).value / "docs" / "patterns.json"
  val toolMap = JSON.parseFull(Source.fromFile(jsonFile).getLines().mkString)
    .getOrElse(throw new Exception("patterns.json is not a valid json"))
    .asInstanceOf[Map[String, String]]
  toolMap.getOrElse[String]("version", throw new Exception("Failed to retrieve 'version' from patterns.json"))
}

libraryDependencies ++= {
  val toolVersion = toolVersionKey.value
  Seq(
    "com.codacy" %% "codacy-engine-scala-seed" % "3.0.183" withSources (),
    "org.scala-lang.modules" %% "scala-xml" % "1.1.0",
    "io.gitlab.arturbosch.detekt" % "detekt-core" % toolVersion,
    "io.gitlab.arturbosch.detekt" % "detekt-api" % toolVersion,
    "io.gitlab.arturbosch.detekt" % "detekt-rules" % toolVersion,
    "io.gitlab.arturbosch.detekt" % "detekt-cli" % toolVersion,
    "io.gitlab.arturbosch.detekt" % "detekt-generator" % toolVersion,
    "org.yaml" % "snakeyaml" % "1.23",
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.8.4"
  )
}

enablePlugins(AshScriptPlugin)

enablePlugins(DockerPlugin)

version in Docker := "1.0"

mappings in Universal ++= {
  (resourceDirectory in Compile) map { (resourceDir: File) =>
    val src = resourceDir / "docs"
    val dest = "/docs"

    for {
      path <- src.allPaths.get if !path.isDirectory
    } yield path -> path.toString.replaceFirst(src.toString, dest)
  }
}.value

val dockerUser = "docker"
val dockerGroup = "docker"

daemonUser in Docker := dockerUser

daemonGroup in Docker := dockerGroup

dockerBaseImage := "openjdk:8-jre-alpine"

mainClass in Compile := Some("codacy.Engine")

dockerCommands := dockerCommands.value.flatMap {
  case cmd @ Cmd("ADD", _) =>
    List(
      Cmd("RUN", s"adduser -u 2004 -D $dockerUser"),
      cmd,
      Cmd("RUN", "mv /opt/docker/docs /docs")
    )
  case other => List(other)
}
