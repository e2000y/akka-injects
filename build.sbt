
enablePlugins(GitVersioning)

organization := "com.github.jw3"
name := "akka-injects"
git.useGitDescribe := true
licenses +=("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

crossScalaVersions := Seq("2.11.8", "2.12.6", "2.13.1")

scalaVersion := "2.13.1"

scalacOptions ++= Seq(
    "-target:jvm-1.8",
    "-encoding", "UTF-8",

    "-feature",
    "-unchecked",
    "-deprecation",

    "-language:postfixOps",
    "-language:implicitConversions",

    "-Xlint:unused",
    "-Xfatal-warnings",
    "-Xlint:_"
)

resolvers ++= Seq(
    Resolver.bintrayRepo("jw3", "maven"),
    Resolver.jcenterRepo
)

libraryDependencies ++= {
    val akkaVersion = "2.6.1"
    val scalatestVersion = "3.0.8"

    Seq(
        "com.iheart" %% "ficus" % "1.4.7",
        "net.codingwell" %% "scala-guice" % "4.2.6",

        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion % Runtime,
        "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",

        "org.scalactic" %% "scalactic" % scalatestVersion % Test,
        "org.scalatest" %% "scalatest" % scalatestVersion % Test,
        "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
    )
}

com.updateimpact.Plugin.apiKey in ThisBuild := sys.env.getOrElse("UPDATEIMPACT_API_KEY", (com.updateimpact.Plugin.apiKey in ThisBuild).value)
