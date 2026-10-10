ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "dev.okay"
ThisBuild / version := "0.1.0-SNAPSHOT"
// okay-java, published by Gradle to ~/.m2: the JVM's tests run its machine and handlers with this one's
ThisBuild / resolvers += Resolver.mavenLocal

/**
 * THE KERNEL API, BINARY: pure Java, no dependency — `dev.okay:okay-kernel-api`. The JVM's implementations, this
 * one's Scala and okay-java's Java, compiled against it: one class an interface, an operation, an answer — a program,
 * a handler, a monad of one works with those of another (`sbt api/publishM2` for okay-java)
 */
lazy val api = (project in file("api"))
  .settings(
    name := "okay-kernel-api",
    crossPaths := false,
    autoScalaLibrary := false,
    javacOptions ++= Seq("--release", "17"),
    Compile / packageDoc / publishArtifact := false)

/** the API's mirror for Scala.js and Scala Native, no Java there: the same signatures, in Scala */
def mirror(base: File) = base.getParentFile / "src" / "main" / "scala-mirror"

/**
 * THE KERNEL, CROSS-BUILT: the JVM, Scala.js, Scala Native — one source tree, `src/`, shared; the JVM's own tests
 * (Java's among them) in `src/test/scala-jvm`. On the JVM the API is `api`'s jar, elsewhere its mirror
 */
lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("."))
  .settings(
    name := "okay-kernel",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.2.3" % Test
  )
  .jvmConfigure(_.dependsOn(api))
  .jsSettings(Compile / unmanagedSourceDirectories += mirror(baseDirectory.value))
  .nativeSettings(Compile / unmanagedSourceDirectories += mirror(baseDirectory.value))
  .jvmSettings(
    libraryDependencies += "dev.okay" % "okay-java" % "0.1.0-SNAPSHOT" % Test,
    Test / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "test" / "scala-jvm",
    // forked: Java's compiler in the tests (`Javac`) given the test's classpath as `java.class.path`
    Test / fork := true
  )

/** Scala 3's stdlib for the Scala 2.13 project, resolved here and placed BEHIND 2.13's by hand */
lazy val Scala3Stdlib = config("scala3Stdlib").hide

/** SCALA 2.13 READING THE KERNEL by its TASTy: 2.13's stdlib first, Scala 3.9's behind it (okay-core's recipe) */
lazy val scala2 = (project in file("scala2"))
  .dependsOn(core.jvm)
  .settings(
    scalaVersion := "2.13.18",
    projectDependencies ~= (_.map(_.exclude("org.scala-lang", "scala-library").exclude("org.scala-lang", "scala3-library_3"))),
    scalacOptions := Seq("-Ytasty-reader", "-deprecation", "-feature"),
    ivyConfigurations += Scala3Stdlib,
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-library" % "3.9.0" % Scala3Stdlib,
      "org.scala-lang" % "scala3-library_3" % "3.9.0" % Scala3Stdlib,
      "org.scalameta" %% "munit" % "1.2.3" % Test),
    publish / skip := true
  )
  .settings(Seq(Compile, Runtime, Test).flatMap(c => Seq(
    c / dependencyClasspath ++= Classpaths.managedJars(Scala3Stdlib, Set("jar"), update.value),
    c / dependencyClasspathAsJars ++= Classpaths.managedJars(Scala3Stdlib, Set("jar"), update.value))))

/** THE KERNEL'S MONADS ON OKAY-CORE: its machines `zero` and `cont`, the kernel's suites on them (okay-core
 * published locally: `sbt coreJVM/publishLocal` in okay-core) */
lazy val okaycore = (project in file("okaycore"))
  .enablePlugins(JmhPlugin)
  .dependsOn(core.jvm % "compile->compile;test->test")
  .settings(
    libraryDependencies ++= Seq("okay-core" %% "okay-core" % "0.1.0-SNAPSHOT", "org.scalameta" %% "munit" % "1.2.3" % Test),
    Test / fork := true,
    publish / skip := true)

lazy val root = (project in file("."))
  .aggregate(api, core.jvm, core.js, core.native, scala2, okaycore)
  // the aggregate only: the sources are the cross-project's
  .settings(publish / skip := true, Compile / sources := Nil, Test / sources := Nil)
