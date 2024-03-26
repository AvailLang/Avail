/*
 * L2_CREATE_FUNCTION.kt
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

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.numOuters
import avail.descriptor.functions.A_RawFunction.Companion.outerTypeAt
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OldInstruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.CONSTANT
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED_VECTOR
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_BOXED
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.IMMUTABLE_FLAG
import avail.optimizer.L2Generator
import avail.optimizer.jvm.JVMTranslator
import avail.utility.Strings.increaseIndentation
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Synthesize a new [function][FunctionDescriptor] from the provided
 * constant compiled code and the vector of captured ("outer") variables.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_CREATE_FUNCTION : L2Operation(
	CONSTANT.named("compiled code"),
	READ_BOXED_VECTOR.named("captured variables"),
	WRITE_BOXED.named("new function"))
{
	override fun extractFunctionOuter(
		instruction: L2Instruction,
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type,
		generator: L2Generator): L2ReadBoxedOperand
	{
		val code = instruction.operand<L2ConstantOperand>(0)
		val outers = instruction.operand<L2ReadBoxedVectorOperand>(1)
		// val function = instruction.operand<L2WriteBoxedOperand>(2)

		val originalRead = outers.elements[outerIndex - 1]
		// Intersect the read's restriction, the given type, and the type that
		// the code says the outer must have.
		var intersection = originalRead.restriction().intersectionWithType(
			outerType.typeIntersection(
				code.constant.outerTypeAt(outerIndex)))
		assert(!intersection.type.isBottom)
		val manifest = generator.currentManifest
		val semanticValue = originalRead.semanticValue()
		if (manifest.hasSemanticValue(semanticValue))
		{
			// This semantic value is still live.  Use it directly.
			val restriction = manifest.restrictionFor(semanticValue)
			if (restriction.isBoxed)
			{
				// It's still live *and* boxed.
				return manifest.readBoxed(semanticValue)
			}
		}
		// The registers that supplied the value are no longer live.  Extract
		// the value from the actual function.  Note that it's still guaranteed
		// to have the strengthened type.
		if (functionRegister.restriction().isImmutable)
		{
			// An immutable function has immutable captured outers.
			intersection = intersection.withFlag(IMMUTABLE_FLAG)
		}
		val tempWrite = generator.boxedWriteTemp(intersection)
		generator.addInstruction(
			L2_MOVE_OUTER_VARIABLE,
			L2IntImmediateOperand(outerIndex),
			functionRegister,
			tempWrite)
		return generator.readBoxed(tempWrite)
	}

	/**
	 * Extract the constant [A_RawFunction] from the given [L2Instruction],
	 * which must have `L2_CREATE_FUNCTION` as its operation.
	 *
	 * @param instruction
	 *   The instruction to examine.
	 * @return
	 *   The constant [A_RawFunction] extracted from the instruction.
	 */
	override fun getConstantCodeFrom(
		instruction: L2Instruction): A_RawFunction
	{
		val constant = instruction.operand<L2ConstantOperand>(0)
		return constant.constant
	}

	override fun appendToWithWarnings(
		instruction: L2OldInstruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		val code = instruction.operand<L2ConstantOperand>(0)
		val outers = instruction.operand<L2ReadBoxedVectorOperand>(1)
		val function = instruction.operand<L2WriteBoxedOperand>(2)
		instruction.renderPreamble(builder)
		builder.append(' ')
		builder.append(function.registerString())
		builder.append(" ← ")
		var decompiled = code.toString()
		var i = 0
		val limit = outers.elements.size
		while (i < limit)
		{
			decompiled = decompiled.replace(
				"Outer#" + (i + 1), outers.elements[i].toString())
			i++
		}
		builder.append(increaseIndentation(decompiled, 1))
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val code = instruction.operand<L2ConstantOperand>(0)
		val outerRegs = instruction.operand<L2ReadBoxedVectorOperand>(1)
		val newFunctionReg = instruction.operand<L2WriteBoxedOperand>(2)
		val numOuters = outerRegs.elements.size

		assert(numOuters == code.constant.numOuters)
		translator.literal(method, code.constant)
		assert(numOuters != 0)
		if (numOuters <= 5)
		{
			outerRegs.registers().forEach { translator.load(method, it) }
		}
		when (numOuters)
		{
			1 -> FunctionDescriptor.createWithOuters1Method.generateCall(method)
			2 -> FunctionDescriptor.createWithOuters2Method.generateCall(method)
			3 -> FunctionDescriptor.createWithOuters3Method.generateCall(method)
			4 -> FunctionDescriptor.createWithOuters4Method.generateCall(method)
			5 -> FunctionDescriptor.createWithOuters5Method.generateCall(method)
			else ->
			{
				// :: function = createExceptOuters(code, numOuters);
				translator.intConstant(method, numOuters)
				FunctionDescriptor.createExceptOutersMethod.generateCall(method)
				for (i in 0 until numOuters)
				{
					// :: function.outerVarAtPut(«i + 1», «outerRegs[i]»);
					method.visitInsn(Opcodes.DUP)
					translator.intConstant(method, i + 1)
					translator.load(method, outerRegs.elements[i].register())
					FunctionDescriptor.outerVarAtPutMethod.generateCall(method)
				}
			}
		}
		// :: newFunction = function;
		translator.store(method, newFunctionReg.register())
	}

	/**
	 * Given an [L2Instruction] using this operation, extract the constant
	 * [A_RawFunction] that is closed into a function by the instruction.
	 *
	 * @param instruction
	 *   The function-closing instruction to examine.
	 * @return
	 *   The constant [A_RawFunction] that is closed by the instruction.
	 */
	@JvmStatic
	fun constantRawFunctionOf(instruction: L2Instruction): A_RawFunction
	{
		val constant = instruction.operand<L2ConstantOperand>(0)
		return constant.constant
	}
}
