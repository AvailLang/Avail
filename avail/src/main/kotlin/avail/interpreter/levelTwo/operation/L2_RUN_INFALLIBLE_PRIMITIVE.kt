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

import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_CONTINUATION
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_FUNCTION
import avail.interpreter.levelTwo.HiddenVariable.GLOBAL_STATE
import avail.interpreter.levelTwo.HiddenVariable.LATEST_RETURN_VALUE
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.optimizer.L2SplitCondition
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.utility.cast
import org.objectweb.asm.MethodVisitor

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
sealed class L2_RUN_INFALLIBLE_PRIMITIVE(
	var rawFunction: L2ConstantOperand,
	var primitive: L2PrimitiveOperand,
	var arguments: L2ReadBoxedVectorOperand,
	var result: L2WriteBoxedOperand
): L2Instruction()
{
	/** The subclass for primitives that have no global dependency. */
	@WritesHiddenVariable(
		LATEST_RETURN_VALUE::class)
	private class L2_RUN_INFALLIBLE_PRIMITIVE_no_dependency(
		rawFunction: L2ConstantOperand,
		primitive: L2PrimitiveOperand,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand
	): L2_RUN_INFALLIBLE_PRIMITIVE(rawFunction, primitive, arguments, result)

	/** The subclass for primitives that have global read dependency. */
	@ReadsHiddenVariable(
		GLOBAL_STATE::class)
	@WritesHiddenVariable(
		CURRENT_CONTINUATION::class,
		CURRENT_FUNCTION::class,
		LATEST_RETURN_VALUE::class)
	private class L2_RUN_INFALLIBLE_PRIMITIVE_read_dependency(
		rawFunction: L2ConstantOperand,
		primitive: L2PrimitiveOperand,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand
	): L2_RUN_INFALLIBLE_PRIMITIVE(rawFunction, primitive, arguments, result)

	/** The subclass for primitives that have global write dependency. */
	@WritesHiddenVariable(
		CURRENT_CONTINUATION::class,
		CURRENT_FUNCTION::class,
		LATEST_RETURN_VALUE::class,
		GLOBAL_STATE::class)
	private class L2_RUN_INFALLIBLE_PRIMITIVE_write_dependency(
		rawFunction: L2ConstantOperand,
		primitive: L2PrimitiveOperand,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand
	): L2_RUN_INFALLIBLE_PRIMITIVE(rawFunction, primitive, arguments, result)

	/** The subclass for primitives that have global read/write dependency. */
	@ReadsHiddenVariable(
		GLOBAL_STATE::class)
	@WritesHiddenVariable(
		CURRENT_CONTINUATION::class,
		CURRENT_FUNCTION::class,
		LATEST_RETURN_VALUE::class,
		GLOBAL_STATE::class)
	private class L2_RUN_INFALLIBLE_PRIMITIVE_readwrite_dependency(
		rawFunction: L2ConstantOperand,
		primitive: L2PrimitiveOperand,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand
	): L2_RUN_INFALLIBLE_PRIMITIVE(rawFunction, primitive, arguments, result)

	/** It depends on the primitive. */
	override val hasSideEffect: Boolean
		get()
		{
			val prim = primitive.primitive
			return (prim.hasFlag(Flag.HasSideEffect)
				|| prim.hasFlag(Flag.CatchException)
				|| prim.hasFlag(Flag.Invokes)
				|| prim.hasFlag(Flag.CanSwitchContinuations)
				|| prim.hasFlag(Flag.ReadsFromHiddenGlobalState)
				|| prim.hasFlag(Flag.WritesToHiddenGlobalState)
				|| prim.hasFlag(Flag.Unknown))
		}

	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		//val rawFunction = instruction.operand<L2ConstantOperand>(0)
		renderPreamble(builder)
		builder.append("\n\t")
		builder.append(result.registerString())
		builder.append(" ← ")
		builder.append(primitive)
		builder.append('(')
		builder.append(arguments.elements)
		builder.append(')')
	}

	/**
	 * Give the primitive another chance to produce something more specific
	 * than a basic infallible primitive invocation.
	 */
	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
	{
		val strongerResultType =
			primitive.primitive.returnTypeGuaranteedByVM(
				rawFunction.constant,
				arguments.elements.map(L2ReadBoxedOperand::type))
		val strongerRestriction =
			result.restriction().intersectionWithType(strongerResultType)
		val strongerResult = L2WriteBoxedOperand(
			result.semanticValues(),
			strongerRestriction,
			result.register())
		strongerRestriction.constantOrNull?.let { constant ->
			if (primitive.primitive.hasFlag(Flag.CanFold))
			{
				// This invocation is now known to produce a constant that can
				// be folded.  Generate a constant move instead.
				regenerator.moveRegister(
					BOXED_KIND,
					regenerator.boxedConstant(constant).semanticValue(),
					strongerResult.semanticValues())
				return
			}
		}
		primitive.primitive.emitTransformedInfalliblePrimitive(
			rawFunction.constant, arguments, strongerResult, regenerator)
	}

	override fun interestingConditions(): List<L2SplitCondition?>
	{
		return primitive.primitive.interestingSplitConditions(
			arguments.elements,
			rawFunction.constant)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		primitive.primitive.generateJvmCode(
			translator, method, arguments, result)
	}

	companion object
	{
		/**
		 * Select an appropriate variant of the operation for the supplied
		 * [Primitive], based on its global interference declarations.
		 *
		 * @param primitive
		 *   The primitive that this operation is for.
		 * @return
		 *   A suitable `L2_RUN_INFALLIBLE_PRIMITIVE` instance.
		 */
		@JvmStatic
		fun createInstruction(
			rawFunction: L2ConstantOperand,
			primitive: L2PrimitiveOperand,
			arguments: L2ReadBoxedVectorOperand,
			result: L2WriteBoxedOperand
		): L2_RUN_INFALLIBLE_PRIMITIVE
		{
			// Until we have all primitives annotated with global read/write
			// flags, pay attention to other flags that we expect to prevent
			// commutation of invocations.
			val prim = primitive.primitive
			if (prim.hasFlag(Flag.HasSideEffect)
				|| prim.hasFlag(Flag.Unknown))
			{
				return L2_RUN_INFALLIBLE_PRIMITIVE_readwrite_dependency(
					rawFunction, primitive, arguments, result)
			}
			val read = prim.hasFlag(Flag.ReadsFromHiddenGlobalState)
			val write = prim.hasFlag(Flag.WritesToHiddenGlobalState)
			return when
			{
				read && write ->
					L2_RUN_INFALLIBLE_PRIMITIVE_readwrite_dependency(
						rawFunction, primitive, arguments, result)
				read -> L2_RUN_INFALLIBLE_PRIMITIVE_read_dependency(
					rawFunction, primitive, arguments, result)
				write -> L2_RUN_INFALLIBLE_PRIMITIVE_write_dependency(
					rawFunction, primitive, arguments, result)
				else -> L2_RUN_INFALLIBLE_PRIMITIVE_no_dependency(
					rawFunction, primitive, arguments, result)
			}
		}

		/**
		 * Extract the [Primitive] from the provided instruction.
		 *
		 * @param instruction
		 *   The [L2Instruction] from which to extract the [Primitive].
		 * @return
		 *   The [Primitive] invoked by this instruction.
		 */
		@JvmStatic
		fun primitiveOf(instruction: L2Instruction): Primitive
		{
			val primitive = instruction.operand<L2PrimitiveOperand>(1)
			return primitive.primitive
		}

		/**
		 * Extract the [List] of [L2ReadBoxedOperand]s that supply the arguments
		 * to the primitive.
		 *
		 * @param instruction
		 *   The [L2Instruction] from which to extract the list of arguments.
		 * @return
		 *   The [List] of [L2ReadBoxedOperand]s that supply arguments to the
		 *   primitive.
		 */
		@JvmStatic
		fun argsOf(instruction: L2Instruction): List<L2ReadBoxedOperand>
		{
			val vector = instruction.operand<L2ReadBoxedVectorOperand>(2)
			return vector.elements.cast()
		}
	}
}
