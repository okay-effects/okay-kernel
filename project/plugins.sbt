// the core and async/ cross-built: the JVM, Scala.js, Scala Native — okay's versions
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.4.0")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.8")
// JUnit 5 for the Java module (java/): its tests found and run by sbt
addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.19.0")
