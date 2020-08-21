/*
 * L2_CREATE_FUNCTION.kt
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

import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.functions.FunctionDescriptor.Companion.createExceptOuters
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.utility.Strings.increaseIndentation
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
	L2OperandType.CONSTANT.named("compiled code"),
	L2OperandType.READ_BOXED_VECTOR.named("captured variables"),
	L2OperandType.WRITE_BOXED.named("new function"))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		val code = instruction.operand<L2ConstantOperand>(0)
		val outers = instruction.operand<L2ReadBoxedVectorOperand>(1)
		val function = instruction.operand<L2WriteBoxedOperand>(2)
		registerSet.typeAtPut(
			function.register(), code.constant.functionType(), instruction)
		if (registerSet.allRegistersAreConstant(outers.elements()))
		{
			// This can be replaced with a statically constructed function
			// during regeneration, but for now capture the exact function that
			// will be constructed.
			val numOuters = outers.elements().size
			assert(numOuters == code.constant.numOuters())
			val newFunction: A_Function =
				createExceptOuters(code.constant, numOuters)
			for (i in 1 .. numOuters)
			{
				newFunction.outerVarAtPut(
					i,
					registerSet.constantAt(
						outers.elements()[i - 1].register()))
			}
			registerSet.constantAtPut(
				function.register(), newFunction, instruction)
		}
		else
		{
			registerSet.removeConstantAt(function.register())
		}
	}

	override fun extractFunctionOuter(
		instruction: L2Instruction,
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type,
		generator: L2Generator): L2ReadBoxedOperand
	{
		assert(this == instruction.operation())
		val code = instruction.operand<L2ConstantOperand>(0)
		val outers = instruction.operand<L2ReadBoxedVectorOperand>(1)
		// val function = instruction.operand<L2WriteBoxedOperand>(2)

		val originalRead = outers.elements()[outerIndex - 1]
		// Intersect the read's restriction, the given type, and the type that
		// the code says the outer must have.
		val intersection = originalRead.restriction().intersectionWithType(
			outerType.typeIntersection(
				code.constant.outerTypeAt(outerIndex)))
		assert(!intersection.type.isBottom)
		val manifest = generator.currentManifest()
		val semanticValue = originalRead.semanticValue()
		if (manifest.hasSemanticValue(semanticValue))
		{
			// This semantic value is still live.  Use it directly.
			val restriction = manifest.restrictionFor(semanticValue)
			if (restriction.isBoxed)
			{
				// It's still live *and* boxed.  Make it immutable if necessary.
				return generator.makeImmutable(
					manifest.readBoxed(semanticValue))
			}
		}
		// The registers that supplied the value are no longer live.  Extract
		// the value from the actual function.  Note that it's still guaranteed
		// to have the strengthened type.
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
		instruction: L2Instruction): A_RawFunction?
	{
		assert(instruction.operation() === this)
		val constant = instruction.operand<L2ConstantOperand>(0)
		return constant.constant
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val code = instruction.operand<L2ConstantOperand>(0)
		val outers = instruction.operand<L2ReadBoxedVectorOperand>(1)
		val function = instruction.operand<L2WriteBoxedOperand>(2)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(function.registerString())
		builder.append(" ← ")
		var decompiled = code.toString()
		var i = 0
		val limit = outers.elements().size
		while (i < limit)
		{
			decompiled = decompiled.replace(
				"Outer#" + (i + 1), outers.elements()[i].toString())
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
		val numOuters = outerRegs.elements().size

		assert(numOuters == code.constant.numOuters())
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
					translator.load(method, outerRegs.elements()[i].register())
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
	@kotlin.jvm.JvmStatic
	fun constantRawFunctionOf(instruction: L2Instruction): A_RawFunction
	{
		assert(instruction.operation() is L2_CREATE_FUNCTION)
		val constant = instruction.operand<L2ConstantOperand>(0)
		return constant.constant
	}
}
