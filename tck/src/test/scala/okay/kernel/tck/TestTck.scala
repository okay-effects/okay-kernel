package okay.kernel.tck

import okay.kernel.steps.Steps

/** the TCK on the reference machine, nesting through itself */
class TestTckSteps extends TckSuite("steps", Steps, others = List(Steps))
