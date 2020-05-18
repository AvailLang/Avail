/*
 * L2_JUMP_IF_SUBTYPE_OF_CONSTANT.java
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

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.InstanceMetaDescriptor
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.function.Consumer

/**
 * Conditionally jump, depending on whether the type to check is a subtype of
 * the constant type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_JUMP_IF_SUBTYPE_OF_CONSTANT
/**
 * Construct an `L2_JUMP_IF_SUBTYPE_OF_CONSTANT`.
 */
private constructor() : L2ConditionalJump(
	L2OperandType.READ_BOXED.`is`("type to check"),
	L2OperandType.CONSTANT.`is`("constant type"),
	L2OperandType.PC.`is`("is subtype", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.`is`("not subtype", L2NamedOperandType.Purpose.FAILURE))
{
	override fun branchReduction(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator): BranchReduction
	{
		// Eliminate tests due to type propagation.
		val typeToCheck = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1)
		//		final L2PcOperand isSubtype = instruction.operand(2);
//		final L2PcOperand notSubtype = instruction.operand(3);
		val exactType: A_BasicObject? = typeToCheck.constantOrNull()
		if (exactType != null)
		{
			return if (exactType.isInstanceOf(constantType.`object`)) BranchReduction.AlwaysTaken else BranchReduction.NeverTaken
		}
		if (typeToCheck.type().instance().isSubtypeOf(constantType.`object`))
		{
			// It's a subtype, so it must always pass the type test.
			return BranchReduction.AlwaysTaken
		}
		val intersection = typeToCheck.type().instance().typeIntersection(constantType.`object`)
		return if (intersection.isBottom)
		{
			// The types don't intersect, so it can't ever pass the type test.
			BranchReduction.NeverTaken
		}
		else BranchReduction.SometimesTaken
	}

	override fun propagateTypes(
		instruction: L2Instruction,
		registerSets: List<RegisterSet>,
		generator: L2Generator)
	{
		val typeToCheck = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1)
		assert(registerSets.size == 2)
		val isSubtypeSet = registerSets[0]
		assert(isSubtypeSet.hasTypeAt(typeToCheck.register()))
		if (isSubtypeSet.hasConstantAt(typeToCheck.register()))
		{
			// The *exact* type is already known.  Don't weaken it by recording
			// type information for it (a meta).
		}
		else
		{
			val existingMeta = isSubtypeSet.typeAt(typeToCheck.register())
			val existingType: A_Type = existingMeta.instance()
			val intersectionType = existingType.typeIntersection(constantType.`object`)
			val intersectionMeta = InstanceMetaDescriptor.instanceMeta(intersectionType)
			isSubtypeSet.strengthenTestedTypeAtPut(
				typeToCheck.register(), intersectionMeta)
		}
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
		val typeToCheck = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1)
		//		final L2PcOperand isSubtype = instruction.operand(2);
//		final L2PcOperand notSubtype = instruction.operand(3);
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(typeToCheck.registerString())
		builder.append(" ⊆ ")
		builder.append(constantType.`object`)
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val typeToCheck = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1)
		val isSubtype = instruction.operand<L2PcOperand>(2)
		val notSubtype = instruction.operand<L2PcOperand>(3)

		// :: if (type.isSubtypeOf(constant)) goto isSubtype;
		// :: else goto notSubtype;
		translator.load(method, typeToCheck.register())
		translator.literal(method, constantType.`object`)
		A_Type.isSubtypeOfMethod.generateCall(method)
		emitBranch(
			translator, method, instruction, Opcodes.IFNE, isSubtype, notSubtype)
	}

	companion object
	{
		/**
		 * Initialize the sole instance.
		 */
		@kotlin.jvm.JvmField
		val instance = L2_JUMP_IF_SUBTYPE_OF_CONSTANT()
	}
}