/**
 * THE KERNEL API, binary: one jar, pure Java, no dependency — what every implementation on the JVM (okay-kernel's
 * Scala, okay-java's Java) is compiled against, so a program, a handler, a monad of one works with those of another.
 * A program is a recipe ({@link okay.kernel.Program}) built in any monad; operations are data of an effect; a handler
 * takes those of its effect, the nearest in force
 */
package okay.kernel;
