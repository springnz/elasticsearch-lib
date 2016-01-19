// Project info
name := "elasticsearch-lib"
organization := "springnz"
scalaVersion := "2.11.7"

releaseVersionBump := sbtrelease.Version.Bump.Bugfix

// Resolvers
resolvers ++= Seq(
  Resolver.mavenLocal,
  "Java.net Maven2 Repository" at "http://download.java.net/maven/2/",
  Resolver.bintrayRepo("pathikrit", "maven"), // for betterfiles
  "gphat" at "https://raw.github.com/gphat/mvn-repo/master/releases/" // for wabisabi
)

// Library versions
val elasticsearchVersion = "2.0.2"
val utilLibVersion = "2.8.0"
val akkaVersion = "2.3.12"

// Library dependencies
val utilLib = "springnz" %% "util-lib" % utilLibVersion
val betterFiles = "com.github.pathikrit" %% "better-files" % "2.4.1"
val elasticsearch = "org.elasticsearch" % "elasticsearch" % elasticsearchVersion
val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4" % Test
val commonsIO = "commons-io" % "commons-io" % "2.4"
val wabiSabi = "wabisabi" %% "wabisabi" % "2.1.4"
// TODO: choose one of the 2 below
val playJson = "com.typesafe.play" %% "play-json" % "2.4.2" exclude ("org.slf4j", "slf4j-log4j12")
val json4s = "org.json4s" %% "json4s-jackson" % "3.2.10"
val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.1.3" % Test
val jts = "com.vividsolutions" % "jts" % "1.13" % Compile

libraryDependencies ++= Seq(utilLib, betterFiles, elasticsearch, commonsIO, scalaTest, wabiSabi, playJson, json4s, logbackClassic, jts)

// Dependency overrides
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

