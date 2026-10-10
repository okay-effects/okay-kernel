package okay.kernel

import javax.tools.{DiagnosticCollector, JavaFileObject, SimpleJavaFileObject, ToolProvider}
import java.net.URI

/** Java source compiled in the test, against the test's classpath: its errors, none if it compiles */
object Javac:
  def errors(body: String): List[String] =
    val src = s"""
      |import okay.lexical.*;
      |public class Probe {
      |  static Writer<?, String> kept;
      |  static Object probe() {
      |    $body
      |  }
      |}""".stripMargin
    val file = new SimpleJavaFileObject(URI.create("string:///Probe.java"), JavaFileObject.Kind.SOURCE):
      override def getCharContent(ignore: Boolean): CharSequence = src
    val diags = DiagnosticCollector[JavaFileObject]()
    val out = java.nio.file.Files.createTempDirectory("javac")
    val task = ToolProvider.getSystemJavaCompiler.getTask(null, null, diags,
      java.util.List.of("-classpath", System.getProperty("java.class.path"), "-d", out.toString), null, java.util.List.of(file))
    task.call()
    import scala.jdk.CollectionConverters.*
    diags.getDiagnostics.asScala.toList.filter(_.getKind == javax.tools.Diagnostic.Kind.ERROR).map(_.getMessage(null))
