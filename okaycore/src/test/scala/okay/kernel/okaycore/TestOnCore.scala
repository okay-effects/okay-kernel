package okay.kernel.okaycore

import okay.kernel.{TestKernel, TestFromJava, TestLexical}

/** the kernel's suites, the same programs, on okay-core's machines */
class TestKernelZero extends TestKernel(OnCore.zero, 10_000)
class TestKernelCont extends TestKernel(OnCore.cont, 10_000)
class TestFromJavaZero extends TestFromJava(OnCore.zero)
class TestFromJavaCont extends TestFromJava(OnCore.cont)
class TestLexicalZero extends TestLexical(OnCore.zero)
class TestLexicalCont extends TestLexical(OnCore.cont)
class TestTypedZero extends okay.kernel.TestTypedOn(OnCore.zero)
class TestTypedCont extends okay.kernel.TestTypedOn(OnCore.cont)
class TestControlFromJavaZero extends okay.kernel.TestControlFromJava(OnCore.zero)
class TestControlFromJavaCont extends okay.kernel.TestControlFromJava(OnCore.cont)
