ThisBuild / scalaVersion := "3.9.0"

/**
 * THE KERNEL, CROSS-BUILT: the JVM, Scala.js, Scala Native — one source tree, `src/`, shared; the JVM's own tests
 * (Java's among them) in `src/test/scala-jvm`
 */
lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("."))
  .settings(
    name := "okay-kernel",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.2.3" % Test
  )
  .jvmSettings(
    Test / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "test" / "scala-jvm"
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

lazy val root = (project in file("."))
  .aggregate(core.jvm, core.js, core.native, scala2)
  .settings(publish / skip := true)
