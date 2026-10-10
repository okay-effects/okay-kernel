package okay.jkernel;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

/** THE STATIC GUARANTEES, BY JAVA'S COMPILER: a capability does not leave its handler's body; a shift's answer, moved,
 * is the reset's type */
class JavacTest {
  /** Java source compiled here, against the test's classpath: its errors, none if it compiles */
  static List<String> errors(String body) throws Exception {
    String src = """
        import okay.jkernel.lexical.*;
        import okay.jkernel.Cont;
        public class Probe {
          static Object probe() {
            %s
          }
        }""".formatted(body);
    JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Probe.java"), JavaFileObject.Kind.SOURCE) {
      @Override public CharSequence getCharContent(boolean ignore) { return src; }
    };
    DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
    String out = Files.createTempDirectory("javac").toString();
    ToolProvider.getSystemJavaCompiler().getTask(null, null, diags,
        List.of("-classpath", System.getProperty("java.class.path"), "-d", out), null, List.of(file)).call();
    return diags.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).map(d -> d.getMessage(null)).toList();
  }

  static final String WRITING = """
      return Prog.writer(new WriterBody<Top, String, Integer>() {
        public <M extends Top> Prog<M, Integer> in(Writer<M, String> w) { %s }
      });""";

  @Test void aCapabilityUsedInsideItsBodyCompiles() throws Exception {
    assertEquals(List.of(), errors(WRITING.formatted("return w.tell(\"x\").andThen(Prog.pure(1));")));
  }

  @Test void aCapabilityKeptOutsideItsBodyDoesNot() throws Exception {
    List<String> e = errors(WRITING.formatted("Writer<Top, String> out = w; return Prog.pure(1);"));
    assertTrue(e.stream().anyMatch(s -> s.contains("incompatible types")), e.toString());
  }

  @Test void aProgramOfTheCapabilityAtTheTopsLevelDoesNot() throws Exception {
    List<String> e = errors(WRITING.formatted("Prog<Top, Integer> p = w.tell(\"x\").map(u -> 1); return Prog.pure(1);"));
    assertTrue(e.stream().anyMatch(s -> s.contains("incompatible types")), e.toString());
  }

  static final String MOVING = """
      Cont<Cont<Integer, Integer, Integer>, Cont<String, String, String>, Integer> b =
        Cont.<Integer, String, Integer>shift(k -> Cont.pure("x"));
      %s""";

  @Test void theResetsAnswerWhatTheShiftMovedItTo() throws Exception {
    assertEquals(List.of(), errors(MOVING.formatted(
        "Cont<Cont<Object, Object, Object>, Cont<Object, Object, Object>, String> r = Cont.reset(b); return r;")));
  }

  @Test void takenAsTheAnswerBeforeTheMoveDoesNotCompile() throws Exception {
    assertFalse(errors(MOVING.formatted(
        "Cont<Cont<Object, Object, Object>, Cont<Object, Object, Object>, Integer> r = Cont.reset(b); return r;")).isEmpty());
  }
}
