ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "dev.okay"
ThisBuild / version := "0.2.0-SNAPSHOT"
// okay-core and okay-java, the libraries the kernel adapts, published locally (okay-core: `sbt coreJVM/publishLocal`;
// okay-java: `./gradlew publishToMavenLocal`, to ~/.m2)
ThisBuild / resolvers += Resolver.mavenLocal

/**
 * THE KERNEL'S API (docs/design.md): the protocol — Type, Key, Op, Computation, Step, Resume, Answers; code written once —
 * Effects, Control, Program, the shapes of handlers; the vocabulary of the standard effects. Pure Java, no dependency:
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

/** the API's mirror for Scala.js and Scala Native, where there is no Java: the same names and signatures, in Scala */
def apiMirror(base: File) = base.getParentFile / "src" / "main" / "scala-mirror"

/**
 * SCALA'S OWN OVER THE API: exact types found by the compiler (`TypeOf`, `@@`); on Scala.js and Scala Native the API's
 * mirror too. Scala 3 and Scala 2.13 from one source, the subset they share: `dev.okay::okay-kernel-scala`
 */
lazy val scalaApi = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("scala"))
  .settings(
    name := "okay-kernel-scala",
    crossScalaVersions := Seq("3.9.0", "2.13.18"),
    scalacOptions ++= (if (scalaVersion.value.startsWith("2.")) Seq("-Xfatal-warnings", "-deprecation", "-Xsource:3") else Seq("-Werror", "-deprecation")),
    libraryDependencies += "org.scalameta" %%% "munit" % "1.2.3" % Test)
  .jvmConfigure(_.dependsOn(api))
  .jsSettings(Compile / unmanagedSourceDirectories += apiMirror(baseDirectory.value))
  .nativeSettings(Compile / unmanagedSourceDirectories += apiMirror(baseDirectory.value))

/**
 * THE KERNEL'S REFERENCE MACHINE: `Effects` and the protocol, exact rather than fast — what the TCK checks every
 * implementation against. Scala 3 and 2.13, the JVM, JS, Native: `dev.okay::okay-kernel-steps`
 */
lazy val steps = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("steps"))
  .dependsOn(scalaApi)
  .settings(
    name := "okay-kernel-steps",
    crossScalaVersions := Seq("3.9.0", "2.13.18"),
    scalacOptions ++= (if (scalaVersion.value.startsWith("2.")) Seq("-deprecation", "-Xsource:3") else Seq("-deprecation")),
    libraryDependencies += "org.scalameta" %%% "munit" % "1.2.3" % Test)

/**
 * THE TCK: what every implementation is checked by — the laws of `Effects`, the standard handlers written once, the
 * protocol (export, import, multi-shot k, fail, cancel, a resume from another thread, nesting through systems), random
 * programs with one digest. A library: an implementation runs it on its own `Effects` — `dev.okay::okay-kernel-tck`
 */
lazy val tck = (project in file("tck"))
  .dependsOn(scalaApi.jvm, steps.jvm % Test)
  .settings(
    name := "okay-kernel-tck",
    crossScalaVersions := Seq("3.9.0", "2.13.18"),
    scalacOptions ++= (if (scalaVersion.value.startsWith("2.")) Seq("-deprecation", "-Xsource:3") else Seq("-deprecation")),
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.3",
    Test / fork := true)

/**
 * EVERY IMPLEMENTATION IN ONE PROCESS: the TCK on each implementation's bridge, published locally from its own
 * repository (okay-java: `./gradlew publishToMavenLocal`; okay-core: `sbt kernel/publishM2`), nesting through the
 * others. Tests only, not published
 */
lazy val integration = (project in file("integration"))
  .dependsOn(tck, steps.jvm)
  .settings(
    // okay-core and okay-java implement the API themselves (okay.core.kernel.OnCore, okay.core.Kernel), published locally
    libraryDependencies ++= Seq("dev.okay" % "okay-java" % "0.2.0-SNAPSHOT" % Test, "okay-core" %% "okay-core" % "0.1.0-SNAPSHOT" % Test),
    Test / fork := true,
    publish / skip := true)

/** THE IMPLEMENTATIONS MEASURED: one program written once over `Effects`, on each (JMH; the bridges published
 * locally from their repositories). Not published */
lazy val bench = (project in file("bench"))
  .enablePlugins(JmhPlugin)
  .dependsOn(steps.jvm)
  .settings(
    libraryDependencies ++= Seq("dev.okay" % "okay-java" % "0.2.0-SNAPSHOT", "okay-core" %% "okay-core" % "0.1.0-SNAPSHOT"),
    publish / skip := true)

/**
 * THE PROTOCOL IN JAVASCRIPT: a computation as a plain JS object (start, its steps, k, a host), exported from Scala.js —
 * so a JS implementation (okay-ts) and the kernel's run each other's programs. An ES module: `kernelJs/fastLinkJS`
 */
lazy val kernelJs = (project in file("js"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(steps.js)
  .settings(
    name := "okay-kernel-js",
    scalacOptions ++= Seq("-deprecation"),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)),
    publish / skip := true)

/**
 * OKAY-EFFECTS' INTERFACE ON THE PROTOCOL (okay-simple's `okay.effects`, one interface of effects for Scala 3 and 2):
 * the bridge `okay.effects.kernel.OnKernel.on(effects)`, on any implementation of the protocol — the reference
 * machine, okay-java, okay-core's — an operation of one key carrying its place in the row. Scala 3 and 2.13:
 * `dev.okay::okay-effects-kernel`. okay-effects and its suite (`okay-effects-tck`) published locally from okay-simple
 * (`sbt +effects/publishLocal +tck/publishLocal` there); okay-java and okay-core as `integration` has them
 */
lazy val effects = (project in file("effects"))
  .dependsOn(api, steps.jvm % Test)
  .settings(
    name := "okay-effects-kernel",
    crossScalaVersions := Seq("3.9.0", "2.13.18"),
    scalacOptions ++= Seq("-deprecation"),
    libraryDependencies ++= Seq(
      "dev.okay" %% "okay-effects" % "0.1.0-SNAPSHOT",
      "dev.okay" %% "okay-effects-tck" % "0.1.0-SNAPSHOT" % Test,
      "dev.okay" % "okay-java" % "0.2.0-SNAPSHOT" % Test) ++
      (if (scalaVersion.value.startsWith("3")) Seq("okay-core" %% "okay-core" % "0.1.0-SNAPSHOT" % Test) else Nil),
    Test / fork := true)

lazy val root = (project in file("."))
  .aggregate(api, scalaApi.jvm, scalaApi.js, scalaApi.native, steps.jvm, steps.js, steps.native, tck, effects)
  .settings(publish / skip := true, Compile / sources := Nil, Test / sources := Nil)

