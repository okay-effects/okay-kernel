package okay.kernel.okayjava

import okay.kernel.{TestKernel, TestFromJava, TestLexical}

/** the kernel's suites, the same programs, on okay-java's machine */
class TestKernelJava extends TestKernel(OnJava.monad, 10_000)
class TestFromJavaJava extends TestFromJava(OnJava.monad)
class TestLexicalJava extends TestLexical(OnJava.monad)
class TestTypedJava extends okay.kernel.TestTypedOn(OnJava.monad)
class TestControlFromJavaJava extends okay.kernel.TestControlFromJava(OnJava.monad)
class TestContJava extends okay.kernel.TestContOn(OnJava.monad)
class TestContFromJavaJava extends okay.kernel.TestContFromJava(OnJava.monad)
class TestShift0Java extends okay.kernel.TestShift0On(OnJava.monad)
class TestContShortJava extends okay.kernel.TestContShortOn(OnJava.monad)
