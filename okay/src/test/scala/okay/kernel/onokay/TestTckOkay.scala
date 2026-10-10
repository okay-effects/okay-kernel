package okay.kernel.onokay

import okay.kernel.steps.{Steps, StepsJava}

/** the TCK on okay's machine, nesting through the reference machines, Scala's and Java's */
class TestTckOkay extends okay.kernel.tck.TckSuite("okay", OnOkay, others = List(Steps, StepsJava.INSTANCE))
