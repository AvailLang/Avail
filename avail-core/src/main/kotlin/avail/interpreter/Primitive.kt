/*
 * Primitive.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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

package avail.interpreter

import avail.AvailRuntime.HookType.IMPLICIT_OBSERVE
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.declaredType
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.TypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.interpreter.Primitive.Companion.holdersByClassName
import avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.Primitive.Flag
import avail.interpreter.Primitive.Flag.AlwaysSwitchesContinuation
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CanSuspend
import avail.interpreter.Primitive.Flag.CanSwitchContinuations
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Flag.Invokes
import avail.interpreter.Primitive.Flag.SpecialForm
import avail.interpreter.Primitive.PrimitiveHolder
import avail.interpreter.Primitive.Result.SUCCESS
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.afterAttemptPrimitiveMethod
import avail.interpreter.execution.Interpreter.Companion.argsBufferField
import avail.interpreter.execution.Interpreter.Companion.beforeAttemptPrimitiveMethod
import avail.interpreter.execution.Interpreter.Companion.getLatestResultMethod
import avail.interpreter.levelOne.L1InstructionWriter
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.primitive.hooks.P_SetImplicitObserveFunction
import avail.interpreter.primitive.privatehelpers.P_PushConstant
import avail.optimizer.ExecutableChunk
import avail.optimizer.L1Translator
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2Generator
import avail.optimizer.jvm.CheckedField
import avail.optimizer.jvm.CheckedField.Companion.enumField
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation
import avail.performance.Statistic
import avail.performance.StatisticReport.PRIMITIVES
import avail.performance.StatisticReport.PRIMITIVE_RETURNER_TYPE_CHECKS
import avail.performance.StatisticReport.REIFICATIONS
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.DUP2
import org.objectweb.asm.Opcodes.DUP2_X2
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.IF_ACMPNE
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Opcodes.POP2
import org.objectweb.asm.Opcodes.SWAP
import java.io.BufferedReader
import java.io.IOException
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
 * control flow.  This is handled via [Interpreter.primitiveSuccess] and
 * [Interpreter.primitiveFailure] and similar methods.  If a primitive fails,
 * the statements in the containing function will be invoked, as though the
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
 * this primitive's [PrimitiveHolder] in [holdersByClassName].
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
abstract class Primitive constructor (val argCount: Int, vararg flags: Flag)
{
	/**
	 * A [function&#32;type][FunctionTypeDescriptor] that restricts the type of
	 * block that can use this primitive.  This is set during initialization to
	 * the value provided by [privateBlockTypeRestriction], to avoid having to
	 * compute this function type multiple times.
	 */
	@Suppress("LeakingThis")
	private val blockTypeRestriction =
		privateBlockTypeRestriction().makeShared()

	/**
	 * A [type][TypeDescriptor] to constrain the [A_Type.writeType] of the
	 * variable declaration within the primitive declaration of a block.  The
	 * actual variable's inner type must be this or a supertype.
	 */
	@Suppress("LeakingThis")
	val failureVariableType: AvailObject =
		privateFailureVariableType().makeShared()

	/**
	 * The flags that indicate to the [L2Generator] how an invocation of
	 * this primitive should be handled.
	 */
	private val primitiveFlags = EnumSet.noneOf(Flag::class.java)

	/**
	 * The [Statistic] for abandoning the stack due to a primitive attempt
	 * answering [Result.CONTINUATION_CHANGED].
	 */
	var reificationAbandonmentStat: Statistic? = null
		private set

	/**
	 * The [Statistic] for reification prior to invoking a primitive that
	 * *does not* have [Flag.CanInline] set.
	 */
	var reificationForNoninlineStat: Statistic? = null
		private set

	/** Capture the name of the primitive class once for performance. */
	val name: String

	/**
	 * A performance metric indicating how long was spent executing each
	 * primitive.
	 */
	private var runningNanos: Statistic

	/**
	 * A performance metric indicating how long was spent checking the return
	 * result for all invocations of this primitive in level two code.  An
	 * excessively large value indicates a profitable opportunity for
	 * [returnTypeGuaranteedByVM] to return a stronger type, perhaps allowing
	 * the level two optimizer to skip more checks.
	 */
	private val resultTypeCheckingNanos = Statistic(
		PRIMITIVE_RETURNER_TYPE_CHECKS,
		"${this@Primitive.javaClass.simpleName} (checking result)")

	init
	{
		val holder = holdersByClassName[javaClass.name]
		name = holder!!.name
		assert(primitiveFlags.isEmpty())
		for (flag in flags)
		{
			assert(!primitiveFlags.contains(flag))
			{
				"Duplicate flag in ${this@Primitive.javaClass.simpleName}"
			}
			primitiveFlags.add(flag)
		}
		// Sanity check certain conditions.
		assert(!primitiveFlags.contains(CanFold)
				|| primitiveFlags.contains(CanInline))
		{
			("Primitive ${this@Primitive.javaClass.simpleName} has CanFold " +
				"without CanInline")
		}
		assert(!primitiveFlags.contains(Invokes)
				|| primitiveFlags.contains(CanInline))
		{
			("Primitive ${this@Primitive.javaClass.simpleName} has Invokes " +
				"without CanInline")
		}
		runningNanos = Statistic(
			PRIMITIVES,
			(if (hasFlag(CanInline)) "" else "[NOT INLINE] ")
				+ "${this@Primitive.javaClass.simpleName} (running)")
		if (hasFlag(CanSwitchContinuations))
		{
			reificationAbandonmentStat = Statistic(
				REIFICATIONS, "Abandoned for CONTINUATION_CHANGED from $name")
		}
		if (!hasFlag(CanInline))
		{
			reificationForNoninlineStat = Statistic(
				REIFICATIONS, "Reification for non-inline $name")
		}
	}

	/**
	 * The success state of a primitive attempt.
	 */
	enum class Result
	{
		/**
		 * The primitive succeeded, and the result, if any, has been stored for
		 * subsequent use in the [Interpreter.latestResult].
		 */
		@ReferencedInGeneratedCode
		SUCCESS,

		/**
		 * The primitive failed.  The backup Avail code should be executed
		 * instead.
		 */
		FAILURE,

		/**
		 * The continuation was replaced as a consequence of the primitive.
		 * This is a specific form of success, but no result can be produced due
		 * to the fact that the new continuation does not have a place to write
		 * it.
		 */
		CONTINUATION_CHANGED,

		/**
		 * A primitive with [Flag.CanInline] and [Flag.Invokes] has set up the
		 * [Interpreter.function] and [Interpreter.argsBuffer] for a call, but
		 * has not called it because that's not permitted from within a
		 * `Primitive`.
		 */
		READY_TO_INVOKE,

		/**
		 * The current fiber has been suspended as a consequence of this
		 * primitive executing, so the [interpreter][Interpreter]
		 * should switch processes now.
		 */
		FIBER_SUSPENDED;

		/** The [CheckedField] for this instance. */
		val checkedField: CheckedField = enumField(this)

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
		 * The primitive failure variable should not be cleared after its last
		 * usage.
		 */
		PreserveFailureVariable,

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
	 * by calling [Interpreter.primitiveFailure] and returning its result from
	 * the primitive.  Otherwise it should set the interpreter's primitive
	 * result by calling [Interpreter.primitiveSuccess] and then return its
	 * result from the primitive.  For unusual primitives that replace the
	 * current continuation, [Result.CONTINUATION_CHANGED] is more appropriate,
	 * and the latestResult need not be set.  For primitives that need to cause
	 * a context switch, [Result.FIBER_SUSPENDED] should be returned.
	 *
	 * @param interpreter
	 *   The [Interpreter] that is executing.
	 * @return The [Result] code indicating success or failure (or special
	 *   circumstance).
	 */
	@ReferencedInGeneratedCode
	abstract fun attempt(interpreter: Interpreter): Result

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
	 *   The [A_RawFunction] being invoked.
	 * @param argumentTypes
	 *   A [List] of argument [types][TypeDescriptor].
	 * @return
	 *   The return type guaranteed by the VM at some call site.
	 */
	open fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		assert(rawFunction.codePrimitive() === this)
		return blockTypeRestriction().returnType
	}

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
	protected open fun privateFailureVariableType(): A_Type = naturalNumbers

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
		if (hasFlag(CannotFail)) CallSiteCannotFail
		else CallSiteCanFail

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
	 *
	 * @constructor
	 * Construct a new `PrimitiveHolder`.
	 *
	 * @param name
	 *   The primitive's textual name.
	 * @param className
	 *   The fully qualified name of the Primitive subclass.
	 */
	class PrimitiveHolder internal constructor(
		val name: String,
		val className: String)
	{
		/**
		 * The sole instance of the specific subclass of [Primitive].  It is
		 * initialized only when needed for the first time, since that causes
		 * Java class loading to happen, and we'd rather smear out that startup
		 * performance cost.
		 */
		val primitive: Primitive by lazy {
			val loader = Primitive::class.java.classLoader
			try
			{
				val primClass = loader.loadClass(className)

				val field =
					primClass.getField("INSTANCE") ?:
					throw NoSuchFieldException(
						"Couldn't find instance field of primitive $className")
				// Trigger the linker.
				field.get(null) as Primitive
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
	}

	/**
	 * Answer whether a raw function using this primitive can/should have
	 * nybblecode instructions.
	 *
	 * @return Whether this primitive has failure/alternative code.
	 */
	fun canHaveNybblecodes(): Boolean =
		!hasFlag(CannotFail) || hasFlag(SpecialForm)

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
		lineNumber: Int, writer: L1InstructionWriter, numArgs: Int)
	{
		if (!hasFlag(CannotFail))
		{
			// Produce failure code.  First declare the local that holds
			// primitive failure information.
			val failureLocal = writer.createLocal(
				variableTypeFor(failureVariableType))
			for (i in 1 .. numArgs)
			{
				writer.write(lineNumber, L1Operation.L1_doPushLastLocal, i)
			}
			// Get the failure code.
			writer.write(lineNumber, L1Operation.L1_doGetLocal, failureLocal)
			// Put the arguments and failure code into a tuple.
			writer.write(lineNumber, L1Operation.L1_doMakeTuple, numArgs + 1)
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
		deltaNanoseconds: Long, interpreterIndex: Int)
	{
		runningNanos.record(deltaNanoseconds, interpreterIndex)
	}

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
		deltaNanoseconds: Long, interpreterIndex: Int) =
			resultTypeCheckingNanos.record(deltaNanoseconds, interpreterIndex)

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
	 * @param translator
	 *   The [L1Translator] on which to emit code, if possible.
	 * @param callSiteHelper
	 *   Information about the call site being generated.
	 * @return
	 *   `true` if a specialized [L2Instruction] sequence was generated, `false`
	 *   if nothing was emitted and the general mechanism should be used
	 *   instead.
	 */
	open fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		// In the general case, avoid producing failure and reification code if
		// the primitive is infallible.  However, if the primitive can suspend
		// the fiber (which can happen even if it's infallible), be careful not
		// to inline it.
		if (hasFlag(CanSuspend)
			|| hasFlag(Invokes)
			|| fallibilityForArgumentTypes(argumentTypes) != CallSiteCannotFail)
		{
			return false
		}
		// The primitive cannot fail at this site.  Output code to run the
		// primitive as simply as possible, feeding a register with as strong a
		// type as possible.
		val generator = translator.generator
		val guaranteedType =
			returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		val restriction = restrictionForType(
			if (guaranteedType.isBottom) TOP.o else guaranteedType, BOXED_FLAG)
		val semanticValue: L2SemanticValue
		if (hasFlag(CanFold) && !guaranteedType.isBottom)
		{
			semanticValue = primitiveInvocation(
				this, arguments.map(L2ReadBoxedOperand::semanticValue))
			// See if we already have a value for an equivalent invocation.
			val manifest = generator.currentManifest
			manifest.equivalentSemanticValue(semanticValue)?.let { equivalent ->
				// Reuse the previously computed result.
				manifest.setRestriction(
					equivalent,
					manifest.restrictionFor(equivalent)
						.intersectionWithType(guaranteedType))
				callSiteHelper.useAnswer(generator.readBoxed(equivalent))
				return true
			}
		}
		else
		{
			semanticValue = generator.newTemp()
		}
		val writer = generator.boxedWrite(semanticValue, restriction)
		translator.addInstruction(
			L2_RUN_INFALLIBLE_PRIMITIVE.forPrimitive(this),
			L2ConstantOperand(rawFunction),
			L2PrimitiveOperand(this),
			L2ReadBoxedVectorOperand(arguments),
			writer)
		if (guaranteedType.isBottom)
		{
			generator.addUnreachableCode()
		}
		else
		{
			callSiteHelper.useAnswer(translator.readBoxed(writer))
		}
		return true
	}

	companion object
	{
		/** A map of all [PrimitiveHolder]s, by name. */
		val holdersByName: Map<String, PrimitiveHolder>

		/** A map of all [PrimitiveHolder]s, by class name. */
		private val holdersByClassName: Map<String, PrimitiveHolder>

		/**
		 * The name of a generated file which lists all primitive classes.  The
		 * file is generated by the build process and is included in build
		 * products as necessary.
		 */
		private const val allPrimitivesFileName = "All_Primitives.txt"

		/**
		 * The pattern of the simple names of [Primitive] classes.
		 */
		private val primitiveNamePattern = Pattern.compile("P_(\\w+)")

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
				val resource =
					Primitive::class.java.getResource(allPrimitivesFileName)!!
				BufferedReader(
					InputStreamReader(resource.openStream(), UTF_8)).use {
					input ->
					while (true)
					{
						val className = input.readLine() ?: break
						val parts = className
							.split("\\.".toRegex())
							.dropLastWhile { it.isEmpty() }
						val lastPart = parts.last()
						val matcher = primitiveNamePattern.matcher(lastPart)
						if (matcher.matches())
						{
							val name = matcher.group(1)
							assert(!byNames.containsKey(name))
							val holder = PrimitiveHolder(name, className)
							byNames[name] = holder
							byClassNames[className] = holder
						}
					}
				}
			}
			catch (e: IOException)
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
			assert(expectedTypes.sizeRange.upperBound.extractInt
				== expected)
			val string = buildString {
				for (i in 1..expected) {
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
			Result::class.java,
			Interpreter::class.java)
	}

	/**
	 * Write a JVM invocation of this primitive.  This sets up the interpreter,
	 * calls [Interpreter.beforeAttemptPrimitive], calls [Primitive.attempt],
	 * calls [Interpreter.afterAttemptPrimitive], and records statistics as
	 * needed. It also deals with primitive failures, suspensions, and
	 * reifications.
	 *
	 * Subclasses may do something more specific and efficient, and should be
	 * free to neglect the statistics.  However, the resultRegister must be
	 * written, even if it's always [nil], to satisfy the JVM bytecode verifier.
	 *
	 * @param translator
	 *   The [JVMTranslator] through which to write bytecodes.
	 * @param method
	 *   The [MethodVisitor] into which bytecodes are being written.
	 * @param arguments
	 *   The [L2ReadBoxedVectorOperand] containing arguments for the primitive.
	 * @param result
	 *   The [L2WriteBoxedOperand] that will be assigned the result of running
	 *   the primitive, if successful.
	 */
	open fun generateJvmCode(
		translator: JVMTranslator,
		method: MethodVisitor,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand
	) {
		// :: argsBuffer = interpreter.argsBuffer;
		translator.loadInterpreter(method)
		// [interpreter]
		argsBufferField.generateRead(method)
		// [argsBuffer]
		// :: argsBuffer.clear();
		if (arguments.elements().isNotEmpty()) {
			method.visitInsn(DUP)
		}
		// [argsBuffer[, argsBuffer if #args > 0]]
		JavaLibrary.listClearMethod.generateCall(method)
		// [argsBuffer if #args > 0]
		var i = 0
		val limit = arguments.elements().size
		while (i < limit) {
			// :: argsBuffer.add(«argument[i]»);
			if (i < limit - 1) {
				method.visitInsn(DUP)
			}
			translator.load(method, arguments.elements()[i].register())
			JavaLibrary.listAddMethod.generateCall(method)
			method.visitInsn(POP)
			i++
		}
		// []
		translator.loadInterpreter(method)
		// [interpreter]
		translator.literal(method, this)
		// [interpreter, prim]
		method.visitInsn(DUP2)
		// [interpreter, prim, interpreter, prim]
		method.visitInsn(DUP2)
		// [interpreter, prim, interpreter, prim, interpreter, prim]
		// :: long timeBefore = beforeAttemptPrimitive(primitive);
		beforeAttemptPrimitiveMethod.generateCall(method)
		// [interpreter, prim, interpreter, prim, timeBeforeLong]
		method.visitInsn(DUP2_X2) // Form 2: v3,v2,v1x2 -> v1x2,v3,v2,v1x2
		// [interpreter, prim, timeBeforeLong, interpreter, prim, timeBeforeLong]
		method.visitInsn(POP2) // Form 2: v1x2 -> empty
		// [interpreter, prim, timeBeforeLong, interpreter, prim]
		method.visitInsn(SWAP)
		// [interpreter, prim, timeBeforeLong, prim, interpreter]
		// :: Result success = primitive.attempt(interpreter)
		attemptMethod.generateCall(method)
		// [interpreter, prim, timeBeforeLong, success]

		// :: afterAttemptPrimitive(primitive, timeBeforeLong, success);
		afterAttemptPrimitiveMethod.generateCall(method)
		// [success] (returned as a nicety by afterAttemptPrimitive)

		// If the infallible primitive definitely switches continuations, then
		// return null to force the context switch.
		when
		{
			hasFlag(AlwaysSwitchesContinuation) ->
			{
				// :: return null;
				method.visitInsn(POP)
				method.visitInsn(ACONST_NULL)
				method.visitInsn(ARETURN)
			}
			hasFlag(CanSwitchContinuations) ->
			{
				// :: if (res == Result.SUCCESS) {
				SUCCESS.checkedField.generateRead(method)
				val switchedContinuations = Label()
				method.visitJumpInsn(IF_ACMPNE, switchedContinuations)
				// ::    result = interpreter.getLatestResult();
				translator.loadInterpreter(method)
				getLatestResultMethod.generateCall(method)
				translator.store(method, result.register())
				// ::    goto success;
				val success = Label()
				method.visitJumpInsn(GOTO, success)
				// :: } else {
				method.visitLabel(switchedContinuations)
				// We switched continuations, so we need to return control to
				// the caller in order to honor the switch.
				// ::    return null;
				method.visitInsn(ACONST_NULL)
				method.visitInsn(ARETURN)
				// :: }
				method.visitLabel(success)
			}
			else ->
			{
				// :: result = interpreter.getLatestResult();
				method.visitInsn(POP)
				translator.loadInterpreter(method)
				getLatestResultMethod.generateCall(method)
				translator.store(method, result.register())
			}
		}
	}
}
