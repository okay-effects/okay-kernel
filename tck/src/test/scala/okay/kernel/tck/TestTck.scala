package okay.kernel.tck

import okay.kernel.steps.{Steps, StepsJava}

/** the TCK on the reference machines: Scala's and Java's, each nesting through both */
class TestTckSteps extends TckSuite("steps", Steps, others = List(StepsJava.INSTANCE, Steps))
class TestTckStepsJava extends TckSuite("steps-java", StepsJava.INSTANCE, others = List(Steps, StepsJava.INSTANCE))
