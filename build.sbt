// Project info
name := "elasticsearch-lib"
organization := "springnz"
scalaVersion := "2.11.8"

releaseVersionBump := sbtrelease.Version.Bump.Bugfix

val publishRepo = "https://nexus.prod.corp/content"

// Resolvers
resolvers ++= Seq(
  Resolver.mavenLocal,
  "spring" at s"$publishRepo/groups/public",
  "Java.net Maven2 Repository" at "http://download.java.net/maven/2/",
  "gphat" at "https://raw.github.com/gphat/mvn-repo/master/releases/" // for wabisabi
)

// Library versions
val elasticsearchVersion = "2.3.4"

// Library dependencies
val elasticsearch = "org.elasticsearch" % "elasticsearch" % elasticsearchVersion
val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4" % Test
val commonsIO = "commons-io" % "commons-io" % "2.4"
val wabiSabi = "wabisabi" %% "wabisabi" % "2.1.4"
val json4s = "org.json4s" %% "json4s-jackson" % "3.2.10"
val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.1.3" % Test
val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
val typesafeConfig = "com.typesafe" % "config" % "1.3.0"
val zip4j = "net.lingala.zip4j" % "zip4j" % "1.3.2"
val dispatch = "net.databinder.dispatch" %% "dispatch-core" % "0.11.2" % Test
val betterFiles = "com.github.pathikrit" %% "better-files" % "2.14.0"

libraryDependencies ++= Seq(typesafeConfig, scalaLogging, elasticsearch, commonsIO, scalaTest, wabiSabi, json4s, logbackClassic, zip4j, dispatch, betterFiles)

// Dependency overrides
val nettyOverride = "io.netty" % "netty" % "3.9.2.Final"
val dependencyOverridesSet = Set(elasticsearch, nettyOverride)
dependencyOverrides := dependencyOverridesSet

// Test options
parallelExecution in Test := false
testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
testOptions in Test += Tests.Setup(() => System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn"))

// Publish options
publishTo <<= version { (v: String) ⇒
  if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at s"$publishRepo/repositories/snapshots")
  else Some("releases" at s"$publishRepo/repositories/releases")
}

