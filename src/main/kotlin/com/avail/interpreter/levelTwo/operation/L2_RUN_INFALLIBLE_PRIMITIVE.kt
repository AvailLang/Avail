/*
 * L2_RUN_INFALLIBLE_PRIMITIVE.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.interpreter.levelTwo.operation

import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2OperandType.CONSTANT
import com.avail.interpreter.levelTwo.L2OperandType.PRIMITIVE
import com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED_VECTOR
import com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_CONTINUATION
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_FUNCTION
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.GLOBAL_STATE
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.LATEST_RETURN_VALUE
import com.avail.interpreter.levelTwo.ReadsHiddenVariable
import com.avail.interpreter.levelTwo.WritesHiddenVariable
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor

/**
 * Execute a primitive with the provided arguments, writing the result into
 * the specified register.  The primitive must not fail.  Don't check the result
 * type, since the VM has already guaranteed it is correct.
 *
 *
 *
 * Unlike for [L2_INVOKE] and related operations, we do not provide
 * the calling continuation here.  That's because by inlining the primitive
 * attempt we have avoided (or at worst postponed) construction of the
 * continuation that reifies the current function execution.  This is a Good
 * Thing, performance-wise.
 *
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct an `L2_RUN_INFALLIBLE_PRIMITIVE`.
 */
abstract class L2_RUN_INFALLIBLE_PRIMITIVE private constructor()
	: L2Operation(
	CONSTANT.named("raw function"),  // Used for inlining/reoptimization.
	PRIMITIVE.named("primitive to run"),
	READ_BOXED_VECTOR.named("arguments"),
	WRITE_BOXED.named("primitive result"))
{
	/** The subclass for primitives that have no global dependency.  */
	@WritesHiddenVariable(
		LATEST_RETURN_VALUE::class)
	private class L2_RUN_INFALLIBLE_PRIMITIVE_no_dependency
		: L2_RUN_INFALLIBLE_PRIMITIVE()

	/** The subclass for primitives that have global read dependency.  */
	@ReadsHiddenVariable(
		GLOBAL_STATE::class)
	@WritesHiddenVariable(
		CURRENT_CONTINUATION::class,
		CURRENT_FUNCTION::class,
		LATEST_RETURN_VALUE::class)
	private class L2_RUN_INFALLIBLE_PRIMITIVE_read_dependency
		: L2_RUN_INFALLIBLE_PRIMITIVE()

	/** The subclass for primitives that have global write dependency.  */
	@WritesHiddenVariable(
		CURRENT_CONTINUATION::class,
		CURRENT_FUNCTION::class,
		LATEST_RETURN_VALUE::class,
		GLOBAL_STATE::class)
	private class L2_RUN_INFALLIBLE_PRIMITIVE_write_dependency
		: L2_RUN_INFALLIBLE_PRIMITIVE()

	/** The subclass for primitives that have global read/write dependency.  */
	@ReadsHiddenVariable(
		GLOBAL_STATE::class)
	@WritesHiddenVariable(
		CURRENT_CONTINUATION::class,
		CURRENT_FUNCTION::class,
		//		CURRENT_ARGUMENTS.class,
		LATEST_RETURN_VALUE::class,
		GLOBAL_STATE::class)
	private class L2_RUN_INFALLIBLE_PRIMITIVE_readwrite_dependency
		: L2_RUN_INFALLIBLE_PRIMITIVE()

	override fun hasSideEffect(instruction: L2Instruction): Boolean
	{
		// It depends on the primitive.
		assert(instruction.operation() === this)
		//		final L2ConstantOperand rawFunction = instruction.operand(0);
		val primitive = instruction.operand<L2PrimitiveOperand>(1)
		//		final L2ReadBoxedVectorOperand arguments = instruction.operand(2);
//		final L2WriteBoxedOperand result = instruction.operand(3);
		val prim = primitive.primitive
		return (prim.hasFlag(Flag.HasSideEffect)
			|| prim.hasFlag(Flag.CatchException)
			|| prim.hasFlag(Flag.Invokes)
			|| prim.hasFlag(Flag.CanSwitchContinuations)
			|| prim.hasFlag(Flag.ReadsFromHiddenGlobalState)
			|| prim.hasFlag(Flag.WritesToHiddenGlobalState)
			|| prim.hasFlag(Flag.Unknown))
	}

	override fun primitiveResultRegister(
		instruction: L2Instruction): L2WriteBoxedOperand?
	{
		assert(instruction.operation() === this)
		return instruction.operand(3)
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this === instruction.operation())
		//		final L2ConstantOperand rawFunction = instruction.operand(0);
		val primitive = instruction.operand<L2PrimitiveOperand>(1)
		val arguments = instruction.operand<L2ReadBoxedVectorOperand>(2)
		val result = instruction.operand<L2WriteBoxedOperand>(3)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(result.registerString())
		builder.append(" ← ")
		builder.append(primitive)
		builder.append('(')
		builder.append(arguments.elements())
		builder.append(')')
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
//		final L2ConstantOperand rawFunction = instruction.operand(0);
		val primitive = instruction.operand<L2PrimitiveOperand>(1)
		val arguments = instruction.operand<L2ReadBoxedVectorOperand>(2)
		val result = instruction.operand<L2WriteBoxedOperand>(3)
		primitive.primitive.generateJvmCode(
			translator, method, arguments, result)
	}

	companion object
	{
		/** An instance for no global dependencies.  */
		private val noDependency: L2_RUN_INFALLIBLE_PRIMITIVE =
			L2_RUN_INFALLIBLE_PRIMITIVE_no_dependency()

		/** An instance for global read dependencies.  */
		private val readDependency: L2_RUN_INFALLIBLE_PRIMITIVE =
			L2_RUN_INFALLIBLE_PRIMITIVE_read_dependency()

		/** An instance for global write dependencies.  */
		private val writeDependency: L2_RUN_INFALLIBLE_PRIMITIVE =
			L2_RUN_INFALLIBLE_PRIMITIVE_write_dependency()

		/** An instance for global read/write dependencies.  */
		private val readWriteDependency: L2_RUN_INFALLIBLE_PRIMITIVE =
			L2_RUN_INFALLIBLE_PRIMITIVE_readwrite_dependency()

		/**
		 * Select an appropriate variant of the operation for the supplied
		 * [Primitive], based on its global interference declarations.
		 *
		 * @param primitive
		 *   The primitive that this operation is for.
		 * @return
		 *   A suitable `L2_RUN_INFALLIBLE_PRIMITIVE` instance.
		 */
		@kotlin.jvm.JvmStatic
		fun forPrimitive(primitive: Primitive): L2_RUN_INFALLIBLE_PRIMITIVE
		{
			// Until we have all primitives annotated with global read/write
			// flags, pay attention to other flags that we expect to prevent
			// commutation of invocations.
			if (primitive.hasFlag(Flag.HasSideEffect)
				|| primitive.hasFlag(Flag.Unknown))
			{
				return readWriteDependency
			}
			val read = primitive.hasFlag(Flag.ReadsFromHiddenGlobalState)
			val write = primitive.hasFlag(Flag.WritesToHiddenGlobalState)
			return when
			{
				read && write -> readWriteDependency
				read -> readDependency
				write -> writeDependency
				else -> noDependency
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
		@kotlin.jvm.JvmStatic
		fun primitiveOf(instruction: L2Instruction): Primitive
		{
			assert(instruction.operation() is L2_RUN_INFALLIBLE_PRIMITIVE)
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
		@kotlin.jvm.JvmStatic
		fun argsOf(instruction: L2Instruction): List<L2ReadBoxedOperand>
		{
			assert(instruction.operation() is L2_RUN_INFALLIBLE_PRIMITIVE)
			val vector = instruction.operand<L2ReadBoxedVectorOperand>(2)
			return vector.elements()
		}
	}
}
