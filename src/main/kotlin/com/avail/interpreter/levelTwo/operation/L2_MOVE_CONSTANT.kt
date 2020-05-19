/*
 * L2_MOVE_CONSTANT.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.types.A_Type
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.*
import com.avail.interpreter.levelTwo.register.L2BoxedRegister
import com.avail.interpreter.levelTwo.register.L2FloatRegister
import com.avail.interpreter.levelTwo.register.L2IntRegister
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.utility.evaluation.Continuation3NotNull
import org.objectweb.asm.MethodVisitor
import java.util.function.Consumer

/**
 * Move a constant [AvailObject] into a register.  Instances of this
 * operation are customized for different [RegisterKind]s.
 *
 * @param C
 *   The [L2Operand] that provides the constant value.
 * @param R
 *   The kind of [L2Register] to populate.
 * @param WR
 *   The kind of [L2WriteOperand] used to write to the register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property pushConstant
 *   A [Continuation3NotNull] to invoke to push the constant value.
 *
 * @constructor
 * Construct an `L2_MOVE_CONSTANT` operation.
 *
 * @param pushConstant
 *   A [Continuation3NotNull] to invoke to generate JVM code to push the
 *   constant value.
 * @param theNamedOperandTypes
 *   An array of [L2NamedOperandType]s that describe this particular L2Operation,
 *   allowing it to be specialized by register type.
 */
class L2_MOVE_CONSTANT<C : L2Operand, R : L2Register, WR : L2WriteOperand<R>>
private constructor(
	private val pushConstant: Continuation3NotNull<JVMTranslator, MethodVisitor, C>,
	vararg theNamedOperandTypes: L2NamedOperandType) : L2Operation(*theNamedOperandTypes)
{
	override fun instructionWasAdded(
		instruction: L2Instruction, manifest: L2ValueManifest)
	{
		assert(this == instruction.operation())
		val source: C = instruction.operand(0)
		val destination: WR = instruction.operand(1)

		// Ensure the new write ends up in the same synonym as the source.
		source.instructionWasAdded(manifest)
		val semanticValue = destination.pickSemanticValue()
		if (manifest.hasSemanticValue(semanticValue))
		{
			// The constant semantic value exists, but for another register
			// kind.
			destination.instructionWasAddedForMove(semanticValue, manifest)
		}
		else
		{
			// The constant semantic value has not been encountered for any
			// register kinds yet.
			destination.instructionWasAdded(manifest)
		}
	}

	override fun extractFunctionOuter(
		instruction: L2Instruction,
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type,
		generator: L2Generator): L2ReadBoxedOperand
	{
		// The exact function is known statically.
		assert(this == instruction.operation() && this == boxed)
		val constantFunction: A_Function = constantOf(instruction)
		return generator.boxedConstant(constantFunction.outerVarAt(outerIndex))
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
		val constant: C = instruction.operand(0)
		val destination: WR = instruction.operand(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		destination.appendWithWarningsTo(builder, 0, warningStyleChange)
		builder.append(" ← ")
		builder.append(constant)
	}

	override fun toString(): String
	{
		val kind =
			when
			{
				this == boxed ->
				{
					"boxed"
				}
				this == unboxedInt ->
				{
					"int"
				}
				this == unboxedFloat ->
				{
					"float"
				}
				else ->
				{
					"unknown"
				}
			}
		return super.toString() + "(" + kind + ")"
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val constantOperand: C = instruction.operand(0)
		val destinationWriter: WR = instruction.operand(1)

		// :: destination = constant;
		pushConstant.value(translator, method, constantOperand)
		translator.store(method, destinationWriter.register())
	}

	companion object
	{
		/**
		 * Initialize the move-constant operation for boxed values.
		 */
		@kotlin.jvm.JvmField
		val boxed = L2_MOVE_CONSTANT<L2ConstantOperand, L2BoxedRegister, L2WriteBoxedOperand>(
			Continuation3NotNull {
				translator: JVMTranslator,
				method: MethodVisitor,
				operand: L2ConstantOperand ->
					translator.literal(method, operand.constant)
			},
			L2OperandType.CONSTANT.`is`("constant"),
			L2OperandType.WRITE_BOXED.`is`("destination boxed"))

		/**
		 * Initialize the move-constant operation for int values.
		 */
		@kotlin.jvm.JvmField
		val unboxedInt = L2_MOVE_CONSTANT<L2IntImmediateOperand, L2IntRegister, L2WriteIntOperand>(
			Continuation3NotNull {
				translator: JVMTranslator,
				method: MethodVisitor,
				operand: L2IntImmediateOperand ->
				translator.literal(method, operand.value)
			},
			L2OperandType.INT_IMMEDIATE.`is`("constant int"),
			L2OperandType.WRITE_INT.`is`("destination int"))

		/**
		 * Initialize the move-constant operation for float values.
		 */
		@kotlin.jvm.JvmField
		val unboxedFloat = L2_MOVE_CONSTANT<L2FloatImmediateOperand, L2FloatRegister, L2WriteFloatOperand>(
			Continuation3NotNull {
				translator: JVMTranslator,
				method: MethodVisitor,
				operand: L2FloatImmediateOperand ->
				translator.literal(method, operand.value)
			},
			L2OperandType.FLOAT_IMMEDIATE.`is`("constant float"),
			L2OperandType.WRITE_FLOAT.`is`("destination float"))

		/**
		 * Given an [L2Instruction] using the boxed form of this operation,
		 * extract the boxed constant that is moved by the instruction.
		 *
		 * @param instruction
		 *   The boxed-constant-moving instruction to examine.
		 * @return
		 *   The constant [AvailObject] that is moved by the instruction.
		 */
		@kotlin.jvm.JvmStatic
		fun constantOf(instruction: L2Instruction): AvailObject
		{
			assert(instruction.operation() === boxed)
			val constant =
				instruction.operand<L2ConstantOperand>(0)
			return constant.constant
		}
	}
}