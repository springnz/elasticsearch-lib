// Project info
name := "elasticsearchlib-lib"
organization := "springnz"
scalaVersion := "2.11.7"
releaseVersionBump := sbtrelease.Version.Bump.Bugfix

// Resolvers
resolvers ++= Seq(
  Resolver.mavenLocal,
  "Java.net Maven2 Repository" at "http://download.java.net/maven/2/",
  Resolver.bintrayRepo("pathikrit", "maven"),
  Resolver.bintrayRepo("pathikrit", "maven"),
  "gphat" at "https://raw.github.com/gphat/mvn-repo/master/releases/"
)

// Library versions
val elasticsearchVersion = "2.0.0"
val utilLibVersion = "2.4.0-SNAPSHOT"
val akkaVersion = "2.3.12"

// Library dependencies
val utilLib = "springnz" %% "util-lib" % utilLibVersion
val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
val betterFiles = "com.github.pathikrit" %% "better-files" % "2.4.1"
val elasticsearch = "org.elasticsearch" % "elasticsearch" % elasticsearchVersion
val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4" % Test
val commonsIO = "commons-io" % "commons-io" % "2.4"
val wabiSabi = "wabisabi" %% "wabisabi" % "2.1.4"
val playJson = "com.typesafe.play" %% "play-json" % "2.4.2" exclude ("org.slf4j", "slf4j-log4j12")

libraryDependencies ++= Seq(utilLib, scalaLogging, betterFiles, elasticsearch, commonsIO, scalaTest, wabiSabi, playJson)

// Dependency overridesx
val nettyOverride = "io.netty" % "netty" % "3.9.2.Final"
val dependencyOverridesSet = Set(elasticsearch, nettyOverride)
dependencyOverrides := dependencyOverridesSet

// Test options
parallelExecution in Test := false
testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
testOptions in Test += Tests.Setup( () => System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn"))

// Publish options
val repo = "https://nexus.prod.corp/content"
publishTo <<= version { (v: String) ⇒
  if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at s"$repo/repositories/snapshots")
  else Some("releases" at s"$repo/repositories/releases")
}

