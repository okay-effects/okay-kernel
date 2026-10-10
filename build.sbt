ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "dev.okay"
ThisBuild / version := "0.1.0-SNAPSHOT"
// okay-core and okay-java, the libraries the kernel adapts, published locally (okay-core: `sbt coreJVM/publishLocal`;
// okay-java: `./gradlew publishToMavenLocal`, to ~/.m2)
ThisBuild / resolvers += Resolver.mavenLocal

// THE KERNEL BEFORE THE PROTOCOL (legacy/): the first API (Monad, Program.in, Effect by strings) and what is built on
// it — machines, the typed view, the bridges — unchanged, beside the new API (docs/design.md) until each is moved onto
// it; never on one classpath with it, its artifacts `*-legacy`

/**
 * THE KERNEL API, BINARY: pure Java, no dependency — `dev.okay:okay-kernel-api`. The JVM's implementations, this
 * one's Scala and its Java (`java/`), compiled against it: one class an interface, an operation, an answer — a program,
 * a handler, a monad of one works with those of another
 */
lazy val legacyApi = (project in file("legacy/api"))
  .settings(
    name := "okay-kernel-api-legacy",
    crossPaths := false,
    autoScalaLibrary := false,
    javacOptions ++= Seq("--release", "17"),
    Compile / packageDoc / publishArtifact := false)

/**
 * THE KERNEL IN JAVA: a machine of the API (`Steps`, okay-java's machine a step a node), the standard handlers and
 * `Async` in Java — pure Java, on the API alone (`dev.okay:okay-kernel-java`). Here, not in okay-java: okay-java and
 * okay-core stay libraries of no dependency, the kernel the layer over them
 */
lazy val legacyJava = (project in file("legacy/java"))
  .dependsOn(legacyApi)
  .settings(
    name := "okay-kernel-java-legacy",
    crossPaths := false,
    autoScalaLibrary := false,
    javacOptions ++= Seq("--release", "21", "-Xlint:all", "-Xlint:-serial"),
    libraryDependencies ++= Seq("com.github.sbt.junit" % "jupiter-interface" % "0.19.0" % Test, "org.junit.jupiter" % "junit-jupiter" % "5.13.4" % Test),
    // the stack-safety tests prove constant stack: a small stack makes a regression fail loudly
    Test / fork := true,
    Test / javaOptions += "-Xss512k",
    Compile / packageDoc / publishArtifact := false)

/** the API's mirror for Scala.js and Scala Native, no Java there: the same signatures, in Scala */
def mirror(base: File) = base.getParentFile / "src" / "main" / "scala-mirror"

/**
 * THE KERNEL, CROSS-BUILT: the JVM, Scala.js, Scala Native — one source tree, `src/`, shared; the JVM's own tests
 * (Java's among them) in `src/test/scala-jvm`. On the JVM the API is `api`'s jar, elsewhere its mirror
 */
lazy val legacyCore = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("legacy/core"))
  .settings(
    name := "okay-kernel-legacy",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.2.3" % Test
  )
  .jvmConfigure(_.dependsOn(legacyApi, legacyJava % Test))
  .jsSettings(Compile / unmanagedSourceDirectories += mirror(baseDirectory.value))
  .nativeSettings(Compile / unmanagedSourceDirectories += mirror(baseDirectory.value))
  .jvmSettings(
    Test / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "test" / "scala-jvm",
    // forked: Java's compiler in the tests (`Javac`) given the test's classpath as `java.class.path`
    Test / fork := true
  )

/** Scala 3's stdlib for the Scala 2.13 project, resolved here and placed BEHIND 2.13's by hand */
lazy val Scala3Stdlib = config("scala3Stdlib").hide

/** SCALA 2.13 READING THE KERNEL by its TASTy: 2.13's stdlib first, Scala 3.9's behind it (okay-core's recipe) */
lazy val legacyScala2 = (project in file("legacy/scala2"))
  .dependsOn(legacyCore.jvm)
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
lazy val legacyOkaycore = (project in file("legacy/okaycore"))
  .enablePlugins(JmhPlugin)
  .dependsOn(legacyCore.jvm % "compile->compile;test->test", legacyJava % Test)
  .settings(
    libraryDependencies ++= Seq("okay-core" %% "okay-core" % "0.1.0-SNAPSHOT", "org.scalameta" %% "munit" % "1.2.3" % Test),
    Test / fork := true,
    publish / skip := true)

/** THE KERNEL'S MONAD ON OKAY-JAVA: its machine of prompts, the kernel's suites on it (okay-java published locally:
 * `./gradlew publishToMavenLocal` in okay-java) */
lazy val legacyOkayjava = (project in file("legacy/okayjava"))
  .dependsOn(legacyCore.jvm % "compile->compile;test->test", legacyJava % Test)
  .settings(
    libraryDependencies ++= Seq("dev.okay" % "okay-java" % "0.1.0-SNAPSHOT", "org.scalameta" %% "munit" % "1.2.3" % Test),
    Test / fork := true,
    publish / skip := true)

/**
 * THE KERNEL'S API (docs/design.md): the protocol — Type, Op, Computation, Step, Resume, Answers; code written once —
 * Effects, Program, the shapes of handlers, Direct; the vocabulary of the standard effects. Pure Java, no dependency:
 * `dev.okay:okay-kernel-api`
 */
lazy val api = (project in file("api"))
  .settings(
    name := "okay-kernel-api",
    crossPaths := false,
    autoScalaLibrary := false,
    javacOptions ++= Seq("--release", "21", "-Xlint:all", "-Xlint:-serial", "-Werror"),
    Compile / doc / javacOptions := Seq("--release", "21"),
    libraryDependencies ++= Seq("com.github.sbt.junit" % "jupiter-interface" % "0.19.0" % Test, "org.junit.jupiter" % "junit-jupiter" % "5.13.4" % Test),
    Compile / packageDoc / publishArtifact := false)

lazy val root = (project in file("."))
  .aggregate(api, legacy)
  .settings(publish / skip := true, Compile / sources := Nil, Test / sources := Nil)

lazy val legacy = (project in file("legacy"))
  .aggregate(legacyApi, legacyJava, legacyCore.jvm, legacyCore.js, legacyCore.native, legacyScala2, legacyOkaycore, legacyOkayjava)
  // the aggregate only: the sources are the cross-project's
  .settings(publish / skip := true, Compile / sources := Nil, Test / sources := Nil)
