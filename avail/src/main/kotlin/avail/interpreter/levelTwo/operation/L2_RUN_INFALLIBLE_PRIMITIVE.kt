/*
 * L2_RUN_INFALLIBLE_PRIMITIVE.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.AvailObject
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.HideInAllVisualizations
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
import avail.interpreter.primitive.Primitive
import avail.interpreter.primitive.Primitive.Flag
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.optimizer.L1Translator
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.constantConditions
import avail.optimizer.L2SplitCondition.Companion.existsCondition
import avail.optimizer.L2SplitCondition.RestrictionTracer
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.values.L2SemanticValue.Companion.constant
import avail.utility.cast

/**
 * Execute a primitive with the provided arguments, writing the result into the
 * specified register.  The primitive must not fail.  Don't check the result
 * type, since the VM has already guaranteed it is correct.
 *
 * Unlike for [L2_INVOKE] and related operations, we do not provide the calling
 * continuation here.  That's because by inlining the primitive attempt we have
 * avoided (or at worst postponed) construction of the continuation that reifies
 * the current function execution.  This is a Good Thing, performance-wise.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct an `L2_RUN_INFALLIBLE_PRIMITIVE`.
 */
class L2_RUN_INFALLIBLE_PRIMITIVE
private constructor(
	@HideInAllVisualizations
	var rawFunction: L2ConstantOperand,
	var primitive: L2ArbitraryConstantOperand<Primitive>,
	var arguments: L2ReadBoxedVectorOperand,
	var result: L2WriteBoxedOperand
): L2Instruction()
{
	constructor(
		rawFunction: A_RawFunction,
		primitive: Primitive,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand
	) : this(
		L2ConstantOperand(rawFunction),
		L2ArbitraryConstantOperand(primitive),
		arguments,
		result)

	override val readsHiddenVariablesMask: Int
		get() = primitive.constant.l2ReadInterferenceMask

	override val writesHiddenVariablesMask: Int
		get() = primitive.constant.l2WriteInterferenceMask

	/** It depends on the primitive. */
	override val hasSideEffect: Boolean = primitive.constant.run {
		(hasFlag(Flag.HasSideEffect)
			|| hasFlag(Flag.CatchException)
			|| hasFlag(Flag.Invokes)
			|| hasFlag(Flag.CanSwitchContinuations)
			|| hasFlag(Flag.ReadsFromHiddenGlobalState)
			|| hasFlag(Flag.WritesToHiddenGlobalState)
			|| hasFlag(Flag.Unknown))
	}

	/** Defer to the primitive. */
	override fun mightMakeEscapedVariableShared(
		manifest: L2ValueManifest
	): Boolean =
		primitive.constant.mightMakeEscapedVariableShared(
			arguments.elements.map(L2ReadBoxedOperand::type))

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append("\n\t")
		append(result.registerString())
		append(" ← ")
		append(primitive)
		append('(')
		append(arguments.elements)
		append(')')
	}

	/**
	 * Answer the [TypeRestriction] this invocation's result is guaranteed to
	 * satisfy, given the [TypeRestriction]s of the arguments.  This is the
	 * single definition of "what this primitive guarantees", shared by
	 * [emitTransformedInstruction] and [impliedWriteRestriction] so that eager
	 * narrowing cannot drift from what is finally emitted.
	 *
	 * @param argumentRestrictions
	 *   The [TypeRestriction]s of the arguments, in order.
	 * @return
	 *   The strengthened [TypeRestriction] for [result].
	 */
	private fun resultRestrictionGiven(
		argumentRestrictions: List<TypeRestriction>
	): TypeRestriction
	{
		foldedResultOrNull(argumentRestrictions)?.let { folded ->
			return result.restriction().intersection(
				restrictionForConstant(folded))
		}
		return result.restriction().intersectionWithType(
			primitive.constant.returnTypeGuaranteedByVM(
				rawFunction.constant,
				argumentRestrictions.map(TypeRestriction::type)))
	}

	/**
	 * If this invocation can be folded and every argument is now known to be a
	 * particular constant, evaluate the primitive and answer its result,
	 * otherwise answer `null`.
	 *
	 * [L1Translator] already folds a call whose arguments are constants at the
	 * moment the call is first translated.  This is the same thing for an
	 * invocation that was *postponed* while its arguments were still unknown,
	 * and only became constant afterwards - typically because it was sunk past
	 * a branch that narrowed one of them.  Without this, such an invocation is
	 * emitted as a real call even though its answer is already determined.
	 *
	 * Answers `null` rather than folding when there is no [Interpreter] on this
	 * thread, since a foldable primitive is evaluated by running it.
	 *
	 * @param argumentRestrictions
	 *   The [TypeRestriction]s of the arguments, in order.
	 * @return
	 *   The constant result, already immutable, or `null`.
	 */
	private fun foldedResultOrNull(
		argumentRestrictions: List<TypeRestriction>
	): AvailObject?
	{
		val prim = primitive.constant
		if (!prim.hasFlag(CanFold) || hasSideEffect) return null
		val constants = argumentRestrictions.map { restriction ->
			restriction.constantOrNull ?: return null
		}
		val interpreter = Interpreter.currentOrNull() ?: return null
		// A foldable primitive must not require access to the enclosing
		// function or its code.
		val savedFunction = interpreter.function
		interpreter.function = null
		val value = try
		{
			interpreter.argsBuffer.clear()
			interpreter.argsBuffer.addAll(constants)
			prim.attempt(interpreter)
		}
		finally
		{
			interpreter.function = savedFunction
		}
		if (value === null || interpreter.currentReifier !== null) return null
		return value.makeImmutable().cast()
	}

	override fun impliedWriteRestriction(
		readRestrictions: List<TypeRestriction>
	): TypeRestriction = resultRestrictionGiven(readRestrictions)

	/**
	 * Give the primitive another chance to produce something more specific
	 * than a basic infallible primitive invocation.
	 */
	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		val strongerRestriction = resultRestrictionGiven(
			arguments.elements.map(L2ReadBoxedOperand::restriction))
		val strongerResult = L2WriteBoxedOperand(
			result.semanticValues(), strongerRestriction)
		strongerRestriction.constantOrNull?.let { constant ->
			if (primitive.constant.hasFlag(CanFold))
			{
				// This invocation is now known to produce a constant that can
				// be folded.  Generate a constant move instead.
				move(
					constant(constant),
					strongerResult.semanticValues())
				return
			}
		}
		primitive.constant.run {
			emitTransformedInfalliblePrimitive(
				rawFunction.constant, arguments, strongerResult)
		}
	}

	override fun L2GeneratorInterface.analyzeAndOptionallyRewrite(
	): L2Instruction? =
		primitive.constant.analyzeAndOptionallyRewriteInstruction(
			this,
			this@L2_RUN_INFALLIBLE_PRIMITIVE)

	override fun interestingConditions(): List<L2SplitCondition?> = buildList {
		addAll(
			primitive.constant.interestingSplitConditions(arguments.elements))
		// Split based on whether a value for an equivalent primitive invocation
		// already exists in some history.
		add(existsCondition(result.semanticValues()))
		// We can't quite split based on whether *all* inputs to the primitive
		// are simultaneously true, so we check if each argument *could* be
		// constant, and if the product of the argument constant counts is
		// acceptably low.  If so, we split on each argument constant
		// individually, hoping that sometimes the combination will allow
		// folding along some split paths.
		val constantConditionsByArgument = arguments.elements.map { argRead ->
			constantConditions(setOf(argRead.register()))
		}
		if (primitive.constant.hasFlag(CanFold))
		{
			// See how many distinct *combinations* of constants might occur.
			val explosion = constantConditionsByArgument
				.map { it.size.toLong() }
				.fold(1L, Long::times)
			if (explosion > 0 && explosion <= 20)
			{
				// This seems like an acceptable number of code splits, since
				// they will *probably* lead to folded primitives, and hopefully
				// not lead to too much nearby code being duplicated.
				constantConditionsByArgument.forEach { argConditions ->
					addAll(argConditions)
				}
			}
		}
	}

	override fun traceCandidateSplitConditions(
		writeOperand: L2WriteOperand<*>,
		restriction: TypeRestriction,
		tracer: RestrictionTracer)
	{
		assert(writeOperand === result)
		primitive.constant.run {
			traceCandidateSplitConditions(restriction, tracer)
		}
	}

	override fun JVMTranslator.translateToJVM()
	{
		primitive.constant.run {
			generateJvmCode(arguments, result)
		}
	}

	override val readsThatMightDestroy: List<L2ReadBoxedOperand>
		get() = when (primitive.constant.canDestroyArguments)
		{
			true -> super.readsThatMightDestroy
			else -> emptyList()
		}
}
