name := "ScalaSemanticEmbeddings"

organization := "org.kuleuven.mai"

version := "1.0"

scalaVersion in ThisBuild := "2.11.6"


lazy val root = (project in file(".")).aggregate(word2vec, glove, languagemodel)

lazy val word2vec = project in file("word2vec")

lazy val glove = project

lazy val languagemodel = project.dependsOn(word2vec, glove)

val data = SettingKey[String]("root", "/var/Datasets/textBasedIR/")

mainClass in (Compile, run) := Some("org.kuleuven.mai.glove.Glove")

libraryDependencies in ThisBuild ++= Seq("org.apache.spark" %% "spark-core" % "1.3.1",
  "org.apache.spark" % "spark-mllib_2.11" % "1.3.1",
  "com.github.tototoshi" %% "scala-csv" % "1.2.1")

resolvers += "Cloudera Repository" at "https://repository.cloudera.com/artifactory/cloudera-repos/"

resolvers += Resolver.mavenLocal