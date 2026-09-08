/*
 * Primitive.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package avail.interpreter.primitive

import avail.AvailRuntime.HookType.IMPLICIT_OBSERVE
import avail.AvailRuntimeSupport.captureNanos
import avail.annotations.DSLHelper
import avail.compiler.PragmaKind
import avail.descriptor.functions.CompiledCodeDescriptor.Companion.specialPrimitivePatterns
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Function
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Phrase
import avail.descriptor.representation.A_Phrase.Companion.declaredType
import avail.descriptor.representation.A_Phrase.Companion.token
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Tuple
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.argsTupleType
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.returnType
import avail.descriptor.representation.A_Type.Companion.sizeRange
import avail.descriptor.representation.A_Type.Companion.typeAtIndex
import avail.descriptor.representation.A_Type.Companion.typeIntersection
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TypeDescriptor
import avail.interpreter.JavaLibrary
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.afterAttemptPrimitiveMethod
import avail.interpreter.execution.Interpreter.Companion.argsBufferField
import avail.interpreter.execution.Interpreter.Companion.beforeAttemptPrimitiveMethod
import avail.interpreter.levelOne.L1InstructionWriter
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2SimpleChunk
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwoSimple.L2SimpleTranslator
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.L2Simple_RunInfalliblePrimitiveNoCheck
import avail.interpreter.levelTwoSimple.instructions.registers.Read
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.interpreter.primitive.Primitive.Flag.SpecialForm
import avail.interpreter.primitive.controlflow.P_CatchException
import avail.interpreter.primitive.hooks.P_SetImplicitObserveFunction
import avail.interpreter.primitive.privatehelpers.P_PushConstant
import avail.optimizer.CallSiteHelper
import avail.optimizer.ExecutableChunk
import avail.optimizer.L1Translator
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2GeneratorInterface.Companion.readTwoInts
import avail.optimizer.L2Optimizer
import avail.optimizer.L2SplitCondition
import avail.optimizer.StackReifier
import avail.optimizer.StackReifier.AfterReification.CONTINUE_FIBER
import avail.optimizer.StackReifier.AfterReification.SWITCH_FROM_FIBER
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation
import avail.performance.Statistic
import avail.performance.StatisticReport
import avail.performance.StatisticReport.PRIMITIVES
import avail.performance.StatisticReport.PRIMITIVE_RETURNER_TYPE_CHECKS
import avail.performance.StatisticReport.REIFICATIONS
import avail.utility.isNullOr
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.POP
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.String.format
import java.nio.charset.StandardCharsets.UTF_8
import java.util.EnumSet
import java.util.regex.Pattern

/**
 * This abstraction represents the interface between Avail's Level One
 * nybblecode interpreter and the underlying interfaces of the built-in objects,
 * providing functionality that is (generally) inexpressible within Level One in
 * terms of other Level One operations.  A conforming Avail implementation must
 * provide these primitives with equivalent semantics and names.
 *
 * The subclasses must define [attempt], which expects its arguments to be
 * accessed via [Interpreter.argument].  Each subclass operates on its arguments
 * to produce a side-effect and/or produce a result.  The primitive's [Flag]s
 * indicate any special preparations that must be made before the invocation,
 * such as reifying the Java stack.
 *
 * Primitives may succeed or fail, or cause some other action like non-local
 * control flow.  This is handled via the return from the [attempt] method,
 * where a non-null value means primitive success, and null means either a
 * reification or a primitive failure has happened.  If a primitive fails, the
 * statements in the containing function will be invoked, as though the
 * primitive had never been attempted.
 *
 * In addition, the `Primitive` subclasses collaborate with the [L1Translator]
 * and [L2Generator] to produce appropriate [L2Instruction]s and ultimately JVM
 * bytecode instructions within a calling [ExecutableChunk].  Again, the [Flag]s
 * and some `Primitive` methods indicate general properties of the primitive,
 * like whether it can be applied ahead of time ([Flag.CanFold]) to constant
 * arguments, whether it could fail, given particular types of arguments, and
 * what return type it guarantees to produce, given particular argument types.
 *
 * The main hook for primitive-specific optimization is
 * [tryToGenerateSpecialPrimitiveInvocation].  Because of the way the L2
 * translation makes use of [L2SemanticValue]s, and
 * [L2SemanticPrimitiveInvocation]s in particular, a primitive can effectively
 * examine the history of its arguments and compose or cancel a chain of actions
 * in the L2 code.  For example, a primitive that extracts an element of a tuple
 * might notice that the tuple was created by a tuple-building primitive, and
 * then choose to directly use one of the inputs to the tuple-building
 * primitive, rather than decompose the tuple. If all such uses of the tuple
 * disappear, the invocation of the tuple-building primitive can be elided
 * entirely, since it has no side-effects.  Arithmetic provides similarly rich
 * opportunities for these high-level optimizations.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property argCount
 *   The number of arguments the primitive expects.  The value -1 is used by
 *   the special primitive [P_PushConstant] to indicate it may have any
 *   number of arguments.  However, note that that primitive cannot be used
 *   explicitly in Avail code.
 *
 * @constructor
 * Construct a new [Primitive].  The first argument is the number of arguments
 * with which the primitive expects to be invoked, and the remaining arguments
 * are [flags][Flag].  The name of the primitive is implicit in the name of the
 * class that it's an instance of, by stripping off the "P_" prefix.
 *
 * Note that it's essential that this method, invoked during static
 * initialization of each Primitive subclass, install this new instance into
 * this primitive's [PrimitiveHolder.holdersByClassName].
 *
 * @param argCount
 *   The number of arguments the primitive expects.  The value -1 is used by
 *   the special primitive [P_PushConstant] to indicate it may have any
 *   number of arguments.  However, note that that primitive cannot be used
 *   explicitly in Avail code.
 * @param flags
 *   The flags that describe how the [Interpreter] and [L2Generator] should deal
 *   with this primitive.
 */
abstract class Primitive
constructor(
	val argCount: Int,
	vararg flags: Flag)
{
	/**
	 * To simplify styling during bootstrapping, a method defined by the
	 * [pragma][PragmaKind] mechanism can have its primitive declare a styler
	 * primitive to plug in as that method definition's styler.
	 */
	open fun bootstrapStyler(): Primitive? = null

	/**
	 * A [function&#32;type][FunctionTypeDescriptor] that restricts the type of
	 * block that can use this primitive.  This is set during initialization to
	 * the value provided by [privateBlockTypeRestriction], to avoid having to
	 * compute this function type multiple times.
	 */
	@Suppress("LeakingThis")
	private val blockTypeRestriction =
		privateBlockTypeRestriction().makeShared()

	/** Capture the name of the primitive class once for performance. */
	val name: String = PrimitiveHolder.holdersByClassName[javaClass.name]!!.name

	/** Capture the simpleName of the primitive class once for performance. */
	val simpleName: String = javaClass.simpleName

	/**
	 * The flags that indicate to the [L2Generator] how an invocation of
	 * this primitive should be handled.
	 */
	private val primitiveFlags = EnumSet.noneOf(Flag::class.java)

	/**
	 * A [type][TypeDescriptor] to constrain the local constant holding the
	 * reason that this primitive failed.  The actual failure constant's type
	 * must be this or a supertype.
	 */
	val failureVariableType: AvailObject =
		privateFailureVariableType().makeShared()

	/**
	 * A performance metric indicating how long was spent executing each
	 * primitive.
	 */
	private val runningNanos: Statistic =
		Statistic(PRIMITIVES, "$simpleName (running)")

	/**
	 * A performance metric indicating how long a successful nilpotent attempt
	 * took in an [L2SimpleChunk].
	 */
	val l2SimpleNilpotentSuccessStatistic = Statistic(
		StatisticReport.L2SIMPLE_NILPOTENT, "success: $simpleName")

	/**
	 * A performance metric indicating how long an aborted nilpotent attempt
	 * took in an [L2SimpleChunk].
	 */
	val l2SimpleNilpotentAbortStatistic = Statistic(
		StatisticReport.L2SIMPLE_NILPOTENT, "aborted: $simpleName")

	/**
	 * The [Statistic] for abandoning the stack due to a primitive attempt
	 * changing the continuaation.
	 */
	lateinit var reificationAbandonmentStat: Statistic
		private set

	/**
	 * The [Statistic] for reification prior to invoking a primitive that
	 * *does not* have [Flag.CanInline] set.
	 */
	lateinit var reificationForNoninlineStat: Statistic
		private set

	/**
	 * A performance metric indicating how long was spent checking the return
	 * result for all invocations of this primitive in level two code.  An
	 * excessively large value indicates a profitable opportunity for
	 * [returnTypeGuaranteedByVM] to return a stronger type, perhaps allowing
	 * the level two optimizer to skip more checks.
	 */
	private val resultTypeCheckingNanos = Statistic(
		PRIMITIVE_RETURNER_TYPE_CHECKS,
		"$simpleName (checking result)")

	init
	{
		assert(primitiveFlags.isEmpty())
		for (flag in flags)
		{
			assert(!primitiveFlags.contains(flag))
			{
				"Duplicate flag in ${javaClass.simpleName}"
			}
			primitiveFlags.add(flag)
		}
		// Sanity check certain conditions.
		assert(!primitiveFlags.contains(Flag.CanFold)
				|| primitiveFlags.contains(Flag.CanInline))
		{
			"Primitive ${javaClass.simpleName} has CanFold without CanInline"
		}
		assert(!primitiveFlags.contains(Flag.Invokes)
				|| primitiveFlags.contains(Flag.CanInline))
		{
			"Primitive ${javaClass.simpleName} has Invokes without CanInline"
		}
		if (hasFlag(Flag.CanSwitchContinuations))
		{
			reificationAbandonmentStat = Statistic(
				REIFICATIONS, "Abandoned for continuation change from $name")
		}
		if (!hasFlag(Flag.CanInline))
		{
			reificationForNoninlineStat = Statistic(
				REIFICATIONS, "Reification for $name")
		}
	}

	/**
	 * These flags are used by the execution machinery and optimizer to indicate
	 * the potential mischief that the corresponding primitives may get into.
	 */
	enum class Flag
	{
		/**
		 * The primitive can be attempted by the `L2Generator` at
		 * re-optimization time if the arguments are known constants. The result
		 * should be stable, such that invoking the primitive again with the
		 * same arguments should produce the same value. The primitive should
		 * not have side-effects.
		 */
		CanFold,

		/**
		 * The invocation of the primitive can be safely inlined. In particular,
		 * it simply computes a value or changes the state of something and does
		 * not replace the current continuation in unusual ways. Thus, something
		 * more specific than a general invocation can be embedded in the
		 * calling [L2Chunk].  Since code for potential reification is
		 * still needed in the failure case, this flag is less useful than it
		 * used to be when a continuation had to be reified on *every*
		 * non-primitive call.
		 */
		CanInline,

		/**
		 * A primitive must have this flag if it might suspend the current
		 * fiber.  The L2 invocation machinery ensures the Java stack has been
		 * reified into a continuation chain *prior* to invoking the
		 * primitive.
		 */
		CanSuspend,

		/**
		 * The primitive has a side-effect, such as writing to a file, modifying
		 * a variable, or defining a new method.
		 */
		HasSideEffect,

		/**
		 * The primitive can invoke a function. If the function is a
		 * non-primitive (or a primitive that fails), the current continuation
		 * must be reified before the call.
		 */
		Invokes,

		/**
		 * The primitive can replace the current continuation, and care should
		 * be taken to ensure the current continuation is fully reified prior to
		 * attempting this primitive. Note that the primitive is not obligated
		 * to switch continuations.
		 */
		CanSwitchContinuations,

		/**
		 * The primitive is guaranteed to replace the current continuation, and
		 * care should be taken to ensure that the current continuation is fully
		 * reified prior to attempting this primitive.
		 */
		AlwaysSwitchesContinuation,

		/**
		 * The raw function has a particular form that qualifies it as a special
		 * primitive, such as immediately returning a constant or argument.  The
		 * raw function won't be displayed as a primitive, but it will execute
		 * and be inlineable as one.
		 */
		SpecialForm,

		/**
		 * The primitive cannot fail. Hence, there is no need for Avail code
		 * to run in the event of a primitive failure. Hence, such code is
		 * forbidden (because it would be unreachable).
		 */
		CannotFail,

		/**
		 * The primitive is not exposed to an Avail program. The compiler
		 * forbids direct compilation of primitive linkages to such primitives.
		 * [A_RawFunction]-creating primitives also forbid creation of
		 * code that links a `Private` primitive.
		 */
		Private,

		/**
		 * This is a bootstrap primitive. It must be made available to the
		 * origin module of an Avail system via a special pragma.
		 */
		Bootstrap,

		/**
		 * The primitive is the special exception catching primitive. Its sole
		 * purpose is to fail, causing an actual continuation to be built. The
		 * exception raising mechanism searches for such a continuation to find
		 * a suitable handler function.
		 */
		CatchException,

		/**
		 * The "guard" local variable of a [P_CatchException] frame should not
		 * be cleared after its last usage.
		 */
		PreserveGuardVariable,

		/**
		 * The primitive arguments should not be cleared after their last
		 * usages.
		 */
		PreserveArguments,

		/**
		 * The primitive writes to some global state that isn't directly
		 * accessible with Avail code.  An example would be modifying the global
		 * implicit observer function ([P_SetImplicitObserveFunction]).
		 */
		WritesToHiddenGlobalState,

		/**
		 * The primitive reads from some global state that isn't directly
		 * accessible with Avail code.  An example would be fetching the global
		 * implicit observer function ([IMPLICIT_OBSERVE]).
		 */
		ReadsFromHiddenGlobalState,

		/**
		 * The semantics of the primitive fall outside the usual capacity of the
		 * [L2Generator]. The current continuation should be reified prior
		 * to attempting the primitive. Do not attempt to fold or inline this
		 * primitive.
		 */
		Unknown
	}

	/**
	 * The actual fallibility of a fallible [Primitive] when invoked
	 * with arguments of specific [types][TypeDescriptor].
	 */
	enum class Fallibility
	{
		/**
		 * The fallible [primitive][Primitive] cannot fail when
		 * invoked with arguments of the specified [types][TypeDescriptor].
		 */
		CallSiteCannotFail,

		/**
		 * The fallible [primitive][Primitive] can indeed fail when
		 * invoked with arguments of the specified [types][TypeDescriptor].
		 */
		CallSiteCanFail,

		/**
		 * The fallible [primitive][Primitive] must fail when invoked
		 * with arguments of the specified [types][TypeDescriptor].
		 */
		CallSiteMustFail,

		/**
		 * The fallible [primitive][Primitive] may have the effect of invoking
		 * some function body, which makes it subject to reification while it
		 * runs.  The call site should be prepared to produce a reified
		 * continuation if this happens.
		 */
		CallSiteMayInvoke
	}

	/**
	 * Attempt this primitive with the given [Interpreter].  The interpreter's
	 * [argument&#32;list][Interpreter.argsBuffer] must be set up prior to this
	 * call.  If the primitive fails, it should set the primitive failure code
	 * by calling [Interpreter.fail] and return null.  If the primitive
	 * needs to reify and then perform some action that suspends or terminates
	 * the fiber, or performs a context change with a fully reified stack, it
	 * should set the [Interpreter.currentReifier], and have
	 * that reifier's [StackReifier.postReificationAction] return either
	 * [CONTINUE_FIBER] or [SWITCH_FROM_FIBER].
	 *
	 * @param interpreter
	 *   The [Interpreter] that is executing.
	 * @return
	 *   The resulting [A_BasicObject] if successful, otherwise `null` to
	 *   indicate either a primitive failure or reification.
	 */
	@ReferencedInGeneratedCode
	abstract fun attempt(interpreter: Interpreter): A_BasicObject?

	/**
	 * Return a function type that restricts actual primitive blocks defined
	 * using that primitive.  The actual block's argument types must be at least
	 * as specific as this function type's argument types, and the actual
	 * block's return type must be at least as general as this function type's
	 * return type.  That's equivalent to the condition that the actual block's
	 * type is a subtype of this function type.
	 *
	 * @return
	 * A function type that restricts the type of a block that uses
	 * this primitive.
	 */
	protected abstract fun privateBlockTypeRestriction(): A_Type

	/**
	 * Return a function type that restricts actual primitive blocks defined
	 * using that primitive.  The actual block's argument types must be at least
	 * as specific as this function type's argument types, and the actual
	 * block's return type must be at least as general as this function type's
	 * return type.  That's equivalent to the condition that the actual block's
	 * type is a subtype of this function type.
	 *
	 * Cache the value in this `Primitive` so subsequent requests are
	 * fast.
	 *
	 * @return
	 *   A function type that restricts the type of a block that uses this
	 *   primitive.
	 */
	fun blockTypeRestriction(): A_Type = blockTypeRestriction

	/**
	 * Answer the type of the result that will be produced by a call site with
	 * the given argument types.  Don't include semantic restrictions defined
	 * in the Avail code, but if convenient answer something stronger than the
	 * return type in the primitive's basic function type.
	 *
	 * @param rawFunction
	 *   The [A_RawFunction] being invoked, if available.
	 * @param argumentTypes
	 *   A [List] of argument [types][TypeDescriptor].
	 * @return
	 *   The return type guaranteed by the VM at some call site.
	 */
	open fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>): A_Type
	{
		assert(rawFunction.isNullOr { codePrimitive() == this@Primitive })
		return blockTypeRestriction().returnType
	}

	/**
	 * Compute the type guaranteed by the VM for this primitive, given the two
	 * argument types.
	 *
	 * This may only be used for 2-argument primitives, and only if they don't
	 * also require access to the called [A_RawFunction].
	 *
	 * @param argType1
	 *   The type of the first operand to the primitive call.
	 * @param argType2
	 *   The type of the second operand to the primitive call.
	 * @return
	 *   A return type guaranteed by the VM when invoking the primitive with
	 *   arguments satisfying the given types.
	 *
	 */
	fun binaryBound(
		argType1: A_Type,
		argType2: A_Type
	): A_Type
	{
		assert(argCount == 2)
		return returnTypeGuaranteedByVM(null, listOf(argType1, argType2))
	}

	/**
	 * If `true`, which is the default, this primitive can be considered to
	 * destroy any of its arguments if they're mutable.  It doesn't *have* to
	 * destroy them, but it *may*.
	 *
	 * Override (with `false`) for primitives for which the following conditions
	 * are *all* true:
	 *  * It doesn't alter the arguments,
	 *  * It doesn't answer a subobject of any of its arguments, and
	 *  * It doesn't embed the arguments or any parts of the arguments into the
	 *    result.
	 */
	open val canDestroyArguments get() = true

	/**
	 * Answer whether this primitive could theoretically cause an escaped local
	 * variable to become shared, or to have read/write reactors added to it.
	 * In either case, we must not continue executing L2 code that assumes that
	 * any local variables that may have escaped can use a register to track
	 * what value would have been written and read.  Instead, it falls back to
	 * L1 execution if this happens.
	 *
	 * Most primitives can't cause that situation, but writing into another
	 * already shared variable could do it, as could launching a fiber with a
	 * function outer or argument that has captured a local variable. Updating
	 * a map from a variable and writing it back into the variable could cause
	 * this, but only if it was updating or adding an entry, not if it's
	 * removing one.
	 *
	 * @param argumentTypes
	 *   The types of the arguments at the call site.
	 */
	open fun mightMakeEscapedVariableShared(
		argumentTypes: List<A_Type>,
	) = false

	/**
	 * Return an Avail [type][TypeDescriptor] that a failure variable
	 * must accept in order to be compliant with this primitive.  A more general
	 * type is acceptable for the variable.  This type is cached upon first
	 * request and should be accessed via [failureVariableType].
	 *
	 * By default, expect the primitive to fail with a natural number.
	 *
	 * @return
	 *   A type which is at least as specific as the type of the failure
	 *   variable declared in a block using this primitive.
	 */
	protected open fun privateFailureVariableType(): A_Type =
		if (Flag.CannotFail in primitiveFlags) bottom
		else naturalNumbers

	/**
	 * Answer the [fallibility][Fallibility] of the [primitive][Primitive] for a
	 * call site with the given argument [types][TypeDescriptor].
	 *
	 * @param argumentTypes
	 *   A [list][List] of argument types.
	 * @return
	 *   The fallibility of the call site.
	 */
	open fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>
	): Fallibility =
		if (hasFlag(Flag.CannotFail)) Fallibility.CallSiteCannotFail
		else Fallibility.CallSiteCanFail

	/**
	 * Test whether the specified [Flag] is set for this primitive.
	 *
	 * @param flag
	 *   The `Flag` to test.
	 * @return Whether that `Flag` is set for this primitive.
	 */
	fun hasFlag(flag: Flag): Boolean = primitiveFlags.contains(flag)

	/**
	 * A helper class to assist with lazy loading of [Primitive]s.
	 *
	 * @property name
	 *   The name by which a primitive function is declared in Avail code.
	 * @property className
	 *   The full name of the Java class implementing the primitive.
	 * @property classLoader
	 *   The [ClassLoader] used to load the [Primitive] [className].
	 *
	 * @constructor
	 * Construct a new `PrimitiveHolder`.
	 *
	 * @param name
	 *   The primitive's textual name.
	 * @param className
	 *   The fully qualified name of the Primitive subclass.
	 *
	 */
	class PrimitiveHolder internal constructor(
		val name: String,
		val className: String,
		internal val classLoader: ClassLoader)
	{
		/**
		 * The sole instance of the specific subclass of [Primitive].  It is
		 * initialized only when needed for the first time, since that causes
		 * Java class loading to happen, and we'd rather smear out that startup
		 * performance cost.
		 */
		val primitive: Primitive by lazy {
			try
			{
				val primClass = classLoader.loadClass(className)
				// Trigger the linker.
				primClass.kotlin.objectInstance as Primitive
			}
			catch (e: ClassNotFoundException)
			{
				throw RuntimeException(e)
			}
			catch (e: NoSuchFieldException)
			{
				throw RuntimeException(e)
			}
			catch (e: IllegalAccessException)
			{
				throw RuntimeException(e)
			}
		}

		companion object
		{
			/** A map of all [PrimitiveHolder]s, by name. */
			val holdersByName: MutableMap<String, PrimitiveHolder>

			/** A map of all [PrimitiveHolder]s, by class name. */
			internal val holdersByClassName: MutableMap<String, PrimitiveHolder>

			/**
			 * The name of a generated file which lists all primitive classes.
			 * The file is generated by the build process and is included in
			 * build products as necessary.
			 */
			private const val allPrimitivesFileName =
				"/avail/interpreter/All_Primitives.txt"

			/**
			 * The pattern of the simple names of [Primitive] classes.
			 */
			private val primitiveNamePattern = Pattern.compile("P_(\\w+)")

			/**
			 * Split the fully qualified class name into its package parts with
			 * the last element in the list being the
			 * [simple class name][Class.getSimpleName].
			 *
			 * @param className
			 *   The [binary name][ClassLoader] of the class to split.
			 */
			fun splitClassName (className: String): List<String> =
				className
					.split("\\.".toRegex())
					.dropLastWhile { it.isEmpty() }

			/*
			 * Read from allPrimitivesFileName to get a complete manifest of
			 * accessible primitives.  Don't actually load the primitives yet.
			 */
			init
			{
				val byNames = mutableMapOf<String, PrimitiveHolder>()
				val byClassNames = mutableMapOf<String, PrimitiveHolder>()
				try
				{
					val resource = PrimitiveHolder::class.java
						.getResource(allPrimitivesFileName)!!
					BufferedReader(
						InputStreamReader(resource.openStream(), UTF_8)
					).use { input ->
						val loader = Primitive::class.java.classLoader
						while (true)
						{
							val className = input.readLine() ?: break
							val parts = splitClassName(className)
							val lastPart = parts.last()
							val matcher = primitiveNamePattern.matcher(lastPart)
							if (matcher.matches())
							{
								val name = matcher.group(1)
								assert(!byNames.containsKey(name))
								val holder = PrimitiveHolder(
									name, className, loader)
								byNames[name] = holder
								byClassNames[className] = holder
							}
						}
					}
				} catch (e: Exception)
				{
					throw RuntimeException(e)
				}
				holdersByName = byNames
				holdersByClassName = byClassNames
			}

			/**
			 * Given a primitive name, look it up and answer the `Primitive` if
			 * found, or `null` if not found.
			 *
			 * @param name
			 *   The primitive name to look up.
			 * @return The primitive, or `null` if the name is not a valid
			 *   primitive.
			 */
			fun primitiveByName(name: String): Primitive? =
				holdersByName[name]?.primitive
		}
	}

	/**
	 * Answer whether a raw function using this primitive can/should have
	 * nybblecode instructions.
	 *
	 * @return Whether this primitive has failure/alternative code.
	 */
	fun canHaveNybblecodes(): Boolean =
		!hasFlag(Flag.CannotFail) || hasFlag(Flag.SpecialForm)

	/**
	 * Determine whether this [Primitive], already identified as a [SpecialForm]
	 * by the [specialPrimitivePatterns] map, actually applies, given the number
	 * of arguments and the first literal, if any. This should only be called if
	 * it has the flag [SpecialForm].  Return true if the primitive applies, or
	 * false if it doesn't.
	 *
	 * @param numArgs
	 *   The number of arguments the function takes.
	 * @param literals
	 *   The tuple of literals.
	 * @return
	 *   Whether the primitive applies to this situation.
	 */
	open fun checkSpecialForm(
		numArgs: Int,
		literals: A_Tuple
	): Boolean
	{
		assert(hasFlag(Flag.SpecialForm))
		return false
	}

	/**
	 * Generate suitable primitive failure code on the given
	 * [L1InstructionWriter]. Some primitives may have special requirements, but
	 * most (fallible) primitives follow the same pattern.
	 *
	 * @param lineNumber
	 *   The line number at which to consider a future failure to occur.
	 * @param writer
	 *   Where to write the failure code.
	 * @param numArgs
	 *   The number of arguments that the function will accept.
	 */
	open fun writeDefaultFailureCode(
		lineNumber: Int,
		writer: L1InstructionWriter,
		numArgs: Int)
	{
		if (!hasFlag(Flag.CannotFail))
		{
			// Produce failure code.  First declare the local that holds
			// primitive failure information.
			val failureConstant = writer.createConstant(failureVariableType)
			for (i in 1 .. numArgs)
			{
				writer.write(lineNumber, L1Operation.L1_doPushLastLocal, i)
			}
			// Get the failure code.
			writer.write(
				lineNumber, L1Operation.L1_doPushLocal, failureConstant)
			// Put the arguments and failure code into a tuple.
			writer.write(lineNumber, L1Operation.L1_doMakeTuple, numArgs + 1)
			// Call the Crash function with tha tuple.
			writer.write(
				lineNumber,
				L1Operation.L1_doCall,
				writer.addLiteral(SpecialMethodAtom.CRASH.bundle),
				writer.addLiteral(bottom))
		}
	}

	/**
	 * Record that some number of nanoseconds were just expended running this
	 * primitive.
	 *
	 * @param deltaNanoseconds
	 *   The sample to add, in nanoseconds.
	 * @param interpreterIndex
	 *   The contention bin in which to add the sample.
	 */
	fun addNanosecondsRunning(
		deltaNanoseconds: Long,
		interpreterIndex: Int
	) = runningNanos.record(deltaNanoseconds, interpreterIndex)

	/**
	 * Record that some number of nanoseconds were just expended checking the
	 * type of the value returned by this primitive.
	 *
	 * @param deltaNanoseconds
	 *   The amount of time just spent checking the result type.
	 * @param interpreterIndex
	 *   The interpreterIndex of the current thread's interpreter.
	 */
	fun addNanosecondsCheckingResultType(
		deltaNanoseconds: Long,
		interpreterIndex: Int
	) = resultTypeCheckingNanos.record(deltaNanoseconds, interpreterIndex)

	/**
	 * The primitive couldn't be folded out, and the primitive failed to produce
	 * specialized L2 instructions for itself.  If the primitive is still known
	 * to be infallible (and does not affect the continuation stack) at this
	 * site, generate an [L2_RUN_INFALLIBLE_PRIMITIVE] for it and answer `true`,
	 * ensuring control flow will go to the appropriate [CallSiteHelper] exit
	 * point, and leave the translator NOT at a currentReachable() point.
	 *
	 * If the primitive might fail for this site, do not generate anything,
	 * answer `false`, and generate nothing.
	 *
	 * @receiver
	 *   The [L1Translator] on which to emit code, if possible.
	 * @param rawFunction
	 *   The primitive raw function whose invocation is being generated.
	 * @param arguments
	 *   The argument [L2ReadBoxedOperand]s supplied to the function.
	 * @param argumentTypes
	 *   The list of [A_Type]s of the arguments.
	 * @param callSiteHelper
	 *   Information about the call site being generated.
	 * @return
	 *   `true` if a specialized [L2Instruction] sequence was generated, `false`
	 *   if nothing was emitted and the general mechanism should be used
	 *   instead.
	 */
	fun L1Translator.tryToGenerateGeneralPrimitiveInvocation(
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		// In the general case, avoid producing failure and reification code if
		// the primitive is infallible.  However, if the primitive can suspend
		// the fiber (which can happen even if it's infallible), be careful not
		// to inline it.
		if (hasFlag(Flag.CanSuspend)
			|| hasFlag(Flag.Invokes)
			|| !hasFlag(Flag.CanInline)
			|| hasFlag(Flag.CanSwitchContinuations)
			|| hasFlag(Flag.Unknown)
			|| fallibilityForArgumentTypes(argumentTypes) != Fallibility.CallSiteCannotFail
		)
		{
			return false
		}
		// The primitive cannot fail at this site.  Output code to run the
		// primitive as simply as possible, feeding a register with as strong a
		// type as possible.
		val guaranteedType =
			returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		val restriction = restrictionForType(
			if (guaranteedType.isBottom) TOP() else guaranteedType)
		val semanticValue: L2SemanticValue
		if (hasFlag(Flag.CanFold) && !guaranteedType.isBottom)
		{
			semanticValue = primitiveInvocation(
				this@Primitive,
				arguments.map(L2ReadBoxedOperand::semanticValue))
			// See if we already have a value for an equivalent invocation.
			currentManifest.equivalentPopulatedSemanticValue(
				semanticValue,
				BOXED_KIND
			)?.let { equivalent ->
				// Reuse the previously computed result.
				currentManifest.updateRestriction(equivalent) {
					intersectionWithType(guaranteedType)
				}
				callSiteHelper.useAnswer(
					readBoxed(equivalent),
					mightMakeEscapedVariableShared(argumentTypes))
				return true
			}
		}
		else
		{
			semanticValue = newTemp("$name result")
		}
		val writer = boxedWrite(semanticValue, restriction)
		+L2_RUN_INFALLIBLE_PRIMITIVE.createInstruction(
			L2ConstantOperand(rawFunction),
			this@Primitive,
			L2ReadBoxedVectorOperand(arguments),
			writer)
		when
		{
			guaranteedType.isBottom -> addUnreachableCode()
			else -> callSiteHelper.useAnswer(
				readBoxed(writer),
				mightMakeEscapedVariableShared(argumentTypes))
		}
		return true
	}


	/**
	 * The primitive couldn't be folded out, so see if alternative instructions
	 * can be generated for its invocation.  If so, answer `true`, ensure
	 * control flow will go to the appropriate [CallSiteHelper] exit point,
	 * and leave the translator NOT at a currentReachable() point.  If
	 * the alternative instructions could not be generated for this primitive,
	 * answer `false`, and generate nothing.
	 *
	 * @param functionToCallReg
	 *   The [L2ReadBoxedOperand] register that holds the function being
	 *   invoked.  The function's primitive is known to be the receiver.
	 * @param rawFunction
	 *   The primitive raw function whose invocation is being generated.
	 * @param arguments
	 *   The argument [L2ReadBoxedOperand]s supplied to the function.
	 * @param argumentTypes
	 *   The list of [A_Type]s of the arguments.
	 * @param callSiteHelper
	 *   Information about the call site being generated.
	 * @return
	 *   `true` if a specialized [L2Instruction] sequence was generated, `false`
	 *   if nothing was emitted and the general mechanism should be used
	 *   instead.
	 */
	open fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean = false

	/**
	 * Attempt to generate a simplified, faster invocation of the given constant
	 * function, with the given argument restrictions.  The arguments have
	 * already been assembled for this function into a [ReadArray], and
	 * arrangements should be made to write the result to
	 * [answer], if the receiver primitive can do that in some way.  In that
	 * case, answer true, indicate the code generation was successful.
	 * Otherwise answer false to allow a general call to be created.
	 *
	 * If a subclass needs to access [Primitive]'s implementation, it can't just
	 * do a super call, because of the secondary receiver.  Therefore, a base
	 * implementation is provided in [defaultAttemptToGenerateSimpleInvocation].
	 */
	open fun L2SimpleTranslator.attemptToGenerateSimpleInvocation(
		functionIfKnown: A_Function?,
		rawFunction: A_RawFunction,
		functionRead: Read,
		expectedType: A_Type,
		args: ReadArray,
		argRestrictions: List<TypeRestriction>,
		stateOfL1: StateOfL1,
		answer: Write,
	): Boolean =
		defaultAttemptToGenerateSimpleInvocation(
			functionIfKnown = functionIfKnown,
			rawFunction = rawFunction,
			argRestrictions = argRestrictions,
			expectedType = expectedType,
			arguments = args,
			stateOfL1 = stateOfL1,
			answer = answer)

	/**
	 * Attempt to generate a simplified, faster invocation of the given constant
	 * function, with the given argument restrictions.  The arguments will be on
	 * the stack, the last-pushed one at stackp.  Return null to fall back
	 * statically to a regular invocation if the primitive can't guarantee to
	 * meet the strengthened type at this call site.  Likewise fall back if the
	 * primitive might fail or suspend.
	 *
	 * If this code generation attempt is successful, return a
	 * [TypeRestriction], indicating the guaranteed result type for the call.
	 * An invocation will be emitted to the [L2SimpleTranslator] in this case.
	 *
	 * This is the (final) default implementation.  Subclasses can perform more
	 * specific operations by overriding [attemptToGenerateSimpleInvocation],
	 * but can still fall back to this method (they can't just do a Kotlin super
	 * call because the method has a secondary receiver).
	 */
	fun L2SimpleTranslator.defaultAttemptToGenerateSimpleInvocation(
		functionIfKnown: A_Function?,
		rawFunction: A_RawFunction,
		argRestrictions: List<TypeRestriction>,
		expectedType: A_Type,
		arguments: ReadArray,
		stateOfL1: StateOfL1,
		answer: Write
	): Boolean
	{
		if (functionIfKnown === null)
		{
			// Subclasses may be more lenient about the function being absent.
			return false
		}
		if (!hasFlag(Flag.CanInline)
			|| hasFlag(Flag.CanSwitchContinuations)
			|| hasFlag(Flag.CanSuspend)
			|| hasFlag(Flag.Invokes)
			|| hasFlag(Flag.Unknown))
		{
			// The primitive might suspend or invoke.  Fall back to a general
			// invocation.
			return false
		}
		val argTypes = argRestrictions.map { it.type }
		val guaranteedType = returnTypeGuaranteedByVM(rawFunction, argTypes)
		if (!guaranteedType.isSubtypeOf(expectedType))
		{
			// The result isn't strong enough to satisfy the expectedType.
			return false
		}
		when (fallibilityForArgumentTypes(argTypes))
		{
			Fallibility.CallSiteCanFail ->
			{
				// This primitive invocation might fail.  However, this might be
				// a very rare situation.  If the primitive has no side-effect
				// on failure, it may be beneficial to just try it, falling back
				// to a general invocation dynamically – which re-attempts the
				// primitive.
				val nilpotentAttempt = simplePrimitiveNilpotentInvocation(
					functionIfKnown,
					rawFunction,
					argRestrictions,
					expectedType)
				if (nilpotentAttempt !== null)
				{
					generateGeneralInvocation(
						nilpotentAttempt = nilpotentAttempt,
						calledCode = rawFunction,
						calledFunction = constant(functionIfKnown),
						arguments = arguments,
						argumentRestrictions = argRestrictions,
						expectedType = expectedType,
						stateOfL1 = stateOfL1,
						answer = answer)
					return true
				}
				// It can fail, but there's no nilpotent function to invoke.
				// Fall back to a general invocation.
				return false
			}
			Fallibility.CallSiteCannotFail ->
			{
				// The primitive cannot fail.
				+L2Simple_RunInfalliblePrimitiveNoCheck(
					function = functionIfKnown,
					rawFunction = rawFunction,
					arguments = arguments,
					answer = answer)
				return true // restrictionForType(guaranteedType)
			}
			else ->
			{
				// Fall back to a general invocation.
				return false
			}
		}
	}

	/**
	 * This call site may fail.  The result type must have been verified strong
	 * enough for this call site.  Answer a function that will attempt to run a
	 * specialized version of the fallible primitive, answering the result, or
	 * `null` if there was a problem. This will be plugged into the L2Simple
	 * code in such a way that if the primitive fails, a full invocation will
	 * take place instead.
	 *
	 * Answer null if the fallible primitive invocation should not happen this
	 * way, which will cause a regular function invocation to occur instead.
	 */
	open fun L2SimpleTranslator.simplePrimitiveNilpotentInvocation(
		functionIfKnown: A_Function?,
		rawFunction: A_RawFunction,
		argRestrictions: List<TypeRestriction>,
		expectedType: A_Type
	): ((Interpreter)->A_BasicObject?)?
	{
		functionIfKnown ?: return null
		return ::nilpotentAttempt
	}

	/**
	 * A convenient operation that satisfies the function signature for the
	 * returned function from [simplePrimitiveNilpotentInvocation].  While this
	 * can just as easily be a lambda, it prints itself nicer if it's a named
	 * function reference instead.
	 */
	open fun nilpotentAttempt(interpreter: Interpreter): A_BasicObject?
	{
		// At this point, the arguments have been pushed in the interpreter.
		val before = captureNanos()
		val valueOrNull = interpreter.afterAttemptPrimitive(
			this,
			interpreter.beforeAttemptPrimitive(this),
			attempt(interpreter))
		assert(valueOrNull != null || interpreter.currentReifier == null)
		val stat = when (valueOrNull)
		{
			null -> l2SimpleNilpotentAbortStatistic
			else -> l2SimpleNilpotentSuccessStatistic
		}
		stat.record(captureNanos() - before, interpreter.interpreterIndex)
		return valueOrNull
	}

	/**
	 * Re-emit an infallible primitive invocation to the [L2GeneratorInterface]
	 * in the implicit receiver.  The default implementation just outputs an
	 * equivalent instruction, but specific primitives might try to strengthen
	 * the invocation into custom instructions due to code splitting or other
	 * type strengthening.
	 *
	 * @receiver
	 *   The [L2GeneratorInterface] on which to emit the instructions.
	 * @param rawFunction
	 *   The [A_RawFunction] that is implemented by this [Primitive].
	 * @param arguments
	 *   The [L2ReadBoxedVectorOperand] containing the sources of the arguments
	 *   being passed to the primitive function.
	 * @param result
	 *   The [L2WriteBoxedOperand] in which to store the result of the primitive
	 *   invocation.
	 */
	open fun L2GeneratorInterface.emitTransformedInfalliblePrimitive(
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand
	) = emitBasicInfalliblePrimitive(rawFunction, arguments, result)

	/**
	 * Generate an [L2_RUN_INFALLIBLE_PRIMITIVE] for this primitive.  This
	 * would normally be in the body of [emitTransformedInfalliblePrimitive],
	 * but instance methods with an implied receiver can't be invoked with the
	 * super syntax (as of 2024-12-14).
	 *
	 * @receiver
	 *   The [L2GeneratorInterface] on which to emit the instructions.
	 * @param rawFunction
	 *   The [A_RawFunction] that is implemented by this [Primitive].
	 * @param arguments
	 *   The [L2ReadBoxedVectorOperand] containing the sources of the arguments
	 *   being passed to the primitive function.
	 * @param result
	 *   The [L2WriteBoxedOperand] in which to store the result of the primitive
	 *   invocation.
	 */
	open fun L2GeneratorInterface.emitBasicInfalliblePrimitive(
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand
	) = +L2_RUN_INFALLIBLE_PRIMITIVE.createInstruction(
		L2ConstantOperand(rawFunction),
		this@Primitive,
		arguments,
		result)

	/**
	 * A syntactic helper class for [attemptToGenerateTwoIntToIntPrimitive] to
	 * use as the receiver when invoking its lambdas that produce code that
	 * operates on ints.
	 *
	 * Note that this class's val fields are visible to the lambdas.
	 */
	@DSLHelper
	class BinaryIntGeneratorHelper(
		val intA: L2ReadIntOperand,
		val intB: L2ReadIntOperand,
		val intWrite: L2WriteIntOperand,
		val boxedWrite: L2WriteBoxedOperand,
		val intSuccess: L2BasicBlock,
		val intFailure: L2BasicBlock,
		private val translator: L1Translator
	) : L2GeneratorInterface by translator

	/**
	 * A syntactic helper class for [attemptToGenerateTwoIntToIntPrimitive] to
	 * use as the receiver when invoking its lambdas that produce code that
	 * operates on ints.
	 *
	 * Note that this class's val fields are visible to the lambdas.
	 */
	@DSLHelper
	class BinaryNonIntGeneratorHelper(
		@Suppress("unused")
		val boxedA: L2ReadBoxedOperand,
		@Suppress("unused")
		val boxedB: L2ReadBoxedOperand,
		val boxedWrite: L2WriteBoxedOperand,
		val translator: L1Translator
	) : L2GeneratorInterface by translator

	/**
	 * Emit code that attempts to unbox ints, and uses the supplied values in
	 * code generated by the [ifOutputIsInt] or [ifOutputIsPossiblyInt] lambdas,
	 * or the [fallback] when the values happen not to be ints.
	 *
	 * @param callSiteHelper
	 *   The [CallSiteHelper] that coordinates the larger messege send.
	 * @param functionToCallReg
	 *   The [L2ReadBoxedOperand] capable to producing the function to be
	 *   called.
	 * @param rawFunction
	 *   The raw function that [functionToCallReg]'s value will have closed.
	 * @param arguments
	 *   The list of [L2ReadBoxedOperand]s providing arguments to the primitive.
	 * @param argumentTypes
	 *   The known static types of the [arguments].
	 * @param ifOutputIsInt
	 *   A lambda with [BinaryIntGeneratorHelper] as receiver, that generates
	 *   code to handle the case that the values are both [i32]s in int
	 *   registers, and the result, if performed, would also be an [i32].
	 * @param ifOutputIsPossiblyInt
	 *   A lambda with [BinaryIntGeneratorHelper] as receiver, that generates
	 *   code to handle the case that the values are both [i32]s in int
	 *   registers, and the result is not known to also be an [i32].
	 * @param fallback
	 *   A lambda with [BinaryNonIntGeneratorHelper] as receiver, that generates
	 *   code to handle the case that the inputs can't be converted into [i32]s,
	 *   or the result is known always to be out of range of an [i32].
	 */
	fun attemptToGenerateTwoIntToIntPrimitive(
		callSiteHelper: CallSiteHelper,
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		ifOutputIsInt: BinaryIntGeneratorHelper.() -> Unit,
		ifOutputIsPossiblyInt: BinaryIntGeneratorHelper.() -> Unit,
		fallback: BinaryNonIntGeneratorHelper.() -> Unit =
			{
				translator.generateGeneralFunctionInvocation(
					functionToCallReg, false, callSiteHelper, arguments)
			}
	): Boolean
	{
		val (boxedA, boxedB) = arguments
		val (aType, bType) = argumentTypes

		val aIntersectInt32 = boxedA.restriction()
			.intersectionWithType(aType.typeIntersection(i32))
		val bIntersectInt32 = boxedB.restriction()
			.intersectionWithType(bType.typeIntersection(i32))
		if (aIntersectInt32.isImpossible || bIntersectInt32.isImpossible)
		{
			// They can't both be an i32, so tell the caller to fall back.
			return false
		}

		// Attempt to unbox the arguments.
		val translator = callSiteHelper.translator
		val valueA = boxedA.semanticValue()
		val valueB = boxedB.semanticValue()
		val intSuccess = translator.createBasicBlock("output is i32")
		val intFallback = translator.createBasicBlock("fall back to boxed")
		val (intA, intB) = translator.readTwoInts(valueA, valueB, intFallback)
		{
			return false
		}
		assert(translator.currentlyReachable())
		// The happy path is reachable.  Generate the most efficient available
		// unboxed arithmetic.
		val returnTypeIfInts = returnTypeGuaranteedByVM(
			rawFunction, listOf(aIntersectInt32.type, bIntersectInt32.type))
		val semanticPrimitive = semanticInvocation(valueA, valueB)
		val intWriter = translator.intWrite(
			setOf(semanticPrimitive),
			restrictionForType(returnTypeIfInts.typeIntersection(i32)))
		val boxedWrite = translator.boxedWrite(
			setOf(semanticPrimitive),
			restrictionForType(returnTypeIfInts))
		val helper = BinaryIntGeneratorHelper(
			intA = intA,
			intB = intB,
			intWrite = intWriter,
			boxedWrite = boxedWrite,
			intSuccess = intSuccess,
			intFailure = intFallback,
			translator = translator)
		if (returnTypeIfInts.isSubtypeOf(i32))
		{
			// The result is guaranteed not to overflow, so emit an instruction
			// that won't bother with an overflow check.  Note that both the
			// unboxed and boxed registers end up in the same synonym, so
			// subsequent uses of the result might use either register,
			// depending whether an unboxed value is desired.

			// Check if there's already an equivalent int value available.
			val equivalent = helper.currentManifest
				.equivalentPopulatedSemanticValue(
					primitiveInvocation(
						this,
						arguments.map(L2ReadBoxedOperand::semanticValue)),
					INTEGER_KIND)
			when (equivalent)
			{
				null -> helper.ifOutputIsInt()
				else -> callSiteHelper.translator.move(
					equivalent, intWriter.semanticValues())
			}
		}
		else
		{
			// The result could exceed an int32.
			helper.ifOutputIsPossiblyInt()
			translator.startBlock(intSuccess)
		}

		// Even though we're just using the boxed value again, the unboxed form
		// is also still available for use by subsequent primitives, which could
		// allow the boxing instruction to evaporate.  Note that the prior
		// int-specific generation blocks are allowed to have simply emitted a
		// jump to the fallback, so only use the int/boxed value if it exists.
		val manifest = translator.currentManifest
		if (manifest.hasSemanticValue(semanticPrimitive))
		{
			callSiteHelper.useAnswer(
				translator.readBoxed(semanticPrimitive), false)
		}
		if (intFallback.predecessorEdges().isNotEmpty())
		{
			// The fallback block is reachable, so generate the slow case within
			// it.  Fallback may happen from conversion of non-int32 arguments,
			// or from int32 overflow calculating the product.
			translator.startBlock(intFallback)
			BinaryNonIntGeneratorHelper(
				boxedA, boxedB, boxedWrite, translator
			).fallback()
		}
		return true
	}

	/**
	 * Write a JVM invocation of this primitive, *under the assumption that the
	 * primitive cannot fail or reify*.  This sets up the interpreter, calls
	 * [Interpreter.beforeAttemptPrimitive], calls [Primitive.attempt], calls
	 * [Interpreter.afterAttemptPrimitive], and records statistics as needed. It
	 * also deals with primitive failures, and reifications.
	 *
	 * Subclasses may do something more specific and efficient, and should be
	 * free to neglect the statistics.  However, the [result] register must be
	 * written, even if it's always [nil], to satisfy the JVM bytecode verifier.
	 *
	 * @receiver
	 *   The [JVMTranslator] through which to write bytecodes.
	 * @param arguments
	 *   The [L2ReadBoxedVectorOperand] containing arguments for the primitive.
	 * @param result
	 *   The [L2WriteBoxedOperand] that will be assigned the result of running
	 *   the primitive.
	 */
	fun JVMTranslator.generateJvmCode(
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand)
	{
		if (Interpreter.trackInlineInfalliblePrimitives)
		{
			generateJvmCodeWithTracking(arguments, result)
		}
		else
		{
			generateJvmCodeWithoutTracking(arguments, result)
		}
	}

	/**
	 * Write a JVM invocation of this primitive, *under the assumption that the
	 * primitive cannot fail or reify*.  This sets up the interpreter, calls
	 * [Interpreter.beforeAttemptPrimitive], calls [Primitive.attempt], calls
	 * [Interpreter.afterAttemptPrimitive], and records statistics as needed. It
	 * also deals with primitive failures, and reifications.
	 *
	 * Subclasses may do something more specific and efficient, and should be
	 * free to neglect the statistics.  However, the [result] register must be
	 * written, even if it's always [nil], to satisfy the JVM bytecode verifier.
	 *
	 * @receiver
	 *   The [JVMTranslator] through which to write bytecodes.
	 * @param arguments
	 *   The [L2ReadBoxedVectorOperand] containing arguments for the primitive.
	 * @param result
	 *   The [L2WriteBoxedOperand] that will be assigned the result of running
	 *   the primitive.
	 */
	fun JVMTranslator.generateJvmCodeWithTracking(
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand)
	{
		// :: argsBuffer = interpreter.argsBuffer;
		loadInterpreter()
		// [interpreter]
		load(argsBufferField)
		// [argsBuffer]
		when (arguments.elements.size)
		{
			0 ->
			{
				// :: argsBuffer.clear();
				generateCall(JavaLibrary.listClearMethod)
			}
			else ->
			{
				method.visitInsn(DUP)
				// [argsBuffer, argsBuffer]
				// :: argsBuffer.clear();
				generateCall(JavaLibrary.listClearMethod)
				// [argsBuffer]
				val limit = arguments.elements.size
				for (i in 0 until limit)
				{
					// :: argsBuffer.add(«argument[i]»);
					if (i < limit - 1)
					{
						method.visitInsn(DUP)
						// :: [argsBuffer, argsBuffer]
					}
					// :: argsBuffer.add(«arguments[i]»);
					load(arguments.elements[i])
					// :: [{argsBuffer}, argsBuffer, arg]
					generateCall(JavaLibrary.listAddMethod)
					// :: [{argsBuffer}, boolean]
					method.visitInsn(POP)
					// :: [{argsBuffer}]
				}
				// :: []
			}
		}
		// []
		loadInterpreter()
		// [interpreter]
		loadLiteralObject(this@Primitive)
		// [interpreter, prim]
		loadInterpreter()
		// [interpreter, prim, interpreter]
		loadLiteralObject(this@Primitive)
		// [interpreter, prim, interpreter, prim]
		// :: long timeBefore = beforeAttemptPrimitive(primitive);
		generateCall(beforeAttemptPrimitiveMethod)
		// [interpreter, prim, timeBeforeLong]
		loadLiteralObject(this@Primitive)
		// [interpreter, prim, timeBeforeLong, prim]
		loadInterpreter()
		// [interpreter, prim, timeBeforeLong, prim, interpreter]
		// :: Result success = primitive.attempt(interpreter)
		generateCall(attemptMethod)
		// [interpreter, prim, timeBeforeLong, valueOrNull]
		// :: afterAttemptPrimitive(primitive, timeBeforeLong, valueOrNull)
		generateCall(afterAttemptPrimitiveMethod)
		// :: [valueOrNull] (returned as a nicety by afterAttemptPrimitive)
		// Write the result into the given [result] register.
		store(result.register())
	}

	/**
	 * Write a JVM invocation of this primitive, *under the assumption that the
	 * primitive cannot fail or reify*.  This sets up the interpreter, calls
	 * [Interpreter.beforeAttemptPrimitive], calls [Primitive.attempt], calls
	 * [Interpreter.afterAttemptPrimitive], and records statistics as needed. It
	 * also deals with primitive failures, and reifications.
	 *
	 * Subclasses may do something more specific and efficient, and should be
	 * free to neglect the statistics.  However, the [result] register must be
	 * written, even if it's always [nil], to satisfy the JVM bytecode verifier.
	 *
	 * @receiver
	 *   The [JVMTranslator] through which to write bytecodes.
	 * @param arguments
	 *   The [L2ReadBoxedVectorOperand] containing arguments for the primitive.
	 * @param result
	 *   The [L2WriteBoxedOperand] that will be assigned the result of running
	 *   the primitive.
	 */
	fun JVMTranslator.generateJvmCodeWithoutTracking(
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand)
	{
		loadLiteralObject(this@Primitive)
		loadInterpreter()
		val method: CheckedMethod = when (this@Primitive)
		{
			is Primitive0 -> Primitive0.attempt0Method
			is Primitive1 -> Primitive1.attempt1Method
			is Primitive2 -> Primitive2.attempt2Method
			is Primitive3 -> Primitive3.attempt3Method
			is Primitive4 -> Primitive4.attempt4Method
			is PrimitiveN ->
			{
				objectArray(arguments.elements, AvailObject::class.java)
				generateCall(PrimitiveN.attemptNMethod)
				store(result.register())
				return
			}
			else -> error("Unsupported primitive argument count")
		}
		arguments.elements.forEach<L2ReadBoxedOperand>(::load)
		generateCall(method)
		store(result.register())
	}

	/**
	 * Answer the list of [L2SplitCondition]s which, if true, would allow better
	 * L2 code to be regenerated.  The [L2Optimizer] checks if any of these are
	 * true on edges leading to ancestor phis, and if so, it may perform code
	 * splitting to avoid erasing that information prematurely through a control
	 * flow merge.
	 *
	 * @return
	 *   The [List] of [L2SplitCondition]s which would be profitable to preserve
	 *   upstream.
	 */
	open fun interestingSplitConditions(
		readBoxedOperands: List<L2ReadBoxedOperand>,
		rawFunction: A_RawFunction
	): List<L2SplitCondition?> = emptyList()

	/**
	 * Answer a semantic value representing the result of invoking this
	 * primitive with the provided list of boxed semantic values.
	 *
	 * @param arguments
	 *   [L2SemanticValue]s that supplied the arguments to the primitive.
	 * @return
	 *   The [L2SemanticValue] representing the primitive result.
	 */
	fun semanticInvocation(
		arguments: List<L2SemanticValue>
	): L2SemanticPrimitiveInvocation = primitiveInvocation(this, arguments)

	/**
	 * Answer a semantic value representing the result of invoking this
	 * primitive with the provided varargs array of boxed semantic values.
	 *
	 * @param arguments
	 *   [L2SemanticValue]s that supplied the arguments to the primitive.
	 * @return
	 *   The [L2SemanticValue] representing the primitive result.
	 */
	fun semanticInvocation(
		vararg arguments: L2SemanticValue
	): L2SemanticPrimitiveInvocation
	{
		assert(argCount == -1 || arguments.size == argCount)
		return primitiveInvocation(this, arguments.toList())
	}

	/**
	 * The [manifest] has just gotten a narrower [TypeRestriction] set for a
	 * semantic invocation of this primitive with the given semantic
	 * [arguments].  Within the given [manifest], propagate that narrowing to
	 * any related [L2SemanticValue]s.
	 *
	 * @param arguments
	 *   The argument [L2SemanticValue]s of the primitive invocation.
	 * @param manifest
	 *   The [L2ValueManifest] to update.
	 * @param restriction
	 *   The current boxed [TypeRestriction] of the primitive invocation.
	 */
	open fun propagateManifestRestrictions(
		arguments: List<L2SemanticValue>,
		manifest: L2ValueManifest,
		restriction: TypeRestriction)
	{
		// Do nothing by default.
	}

	override fun toString(): String
	{
		return this::class.java.simpleName
	}

	/**
	 * Given a [L2SemanticPrimitiveInvocation] whose primitive is the receiver,
	 * render it as text.
	 */
	open fun printSemanticInvocation(
		invocation: L2SemanticPrimitiveInvocation
	): String
	{
		assert(invocation.primitive == this)
		semanticInfixOperatorString?.let { infix ->
			assert(argCount == 2)
			val (left, right) = invocation.argumentSemanticValues
			var leftString =
				left.constant?.let(AvailObject::toString) ?: left.toString()
			if (left.requiresParentheses()) leftString = "($leftString)"
			var rightString =
				right.constant?.let(AvailObject::toString) ?: right.toString()
			if (right.requiresParentheses()) rightString = "($rightString)"
			return "$leftString $infix $rightString"
		}
		return invocation.argumentSemanticValues.joinToString(
			separator = ", ",
			prefix = "$name(",
			postfix = ")" )
		{
			it.constant?.let(AvailObject::toString) ?: it.toString()
		}
	}

	/**
	 * If an [L2SemanticPrimitiveInvocation] of this primitive should be
	 * described with an infix syntax, answer the infix operator string,
	 * otherwise `null`.
	 */
	open val semanticInfixOperatorString: String? get() = null

	companion object
	{
		/**
		 * Determine whether the specified primitive declaration is acceptable
		 * to be used with the given list of parameter declarations.  Answer
		 * null if they are acceptable, otherwise answer a suitable `String`
		 * that is expected to appear after the prefix "Expecting...".
		 *
		 * @param primitive
		 *   Which primitive.
		 * @param arguments
		 *   The argument declarations that we should check are legal for this
		 *   primitive.
		 * @return Whether the primitive accepts arguments with types that
		 *   conform to the given argument declarations.
		 */
		fun validatePrimitiveAcceptsArguments(
			primitive: Primitive,
			arguments: List<A_Phrase>): String?
		{
			val expected = primitive.argCount
			if (expected == -1) return null
			if (arguments.size != expected)
			{
				return format(
					"number of declared arguments (%d) to agree with " +
						"primitive's required number of arguments (%d).",
					arguments.size,
					expected)
			}
			val expectedTypes = primitive.blockTypeRestriction().argsTupleType
			assert(expectedTypes.sizeRange.upperBound.extractInt == expected)
			val string = buildString {
				for (i in 1 .. expected)
				{
					val declaredType = arguments[i - 1].declaredType
					val expectedType = expectedTypes.typeAtIndex(i)
					if (!declaredType.isSubtypeOf(expectedType))
					{
						if (isNotEmpty()) append("\n")
						append(
							format(
								"argument #%d (%s) of primitive %s to be a " +
									"subtype of %s, not %s.",
								i,
								arguments[i - 1].token.string(),
								primitive.name,
								expectedType,
								declaredType))
					}
				}
			}
			return string.ifEmpty { null }
		}

		/** The method [attempt]. */
		val attemptMethod = instanceMethod(
			Primitive::class.java,
			Primitive::attempt.name,
			A_BasicObject::class.java,
			Interpreter::class.java)
	}
}
