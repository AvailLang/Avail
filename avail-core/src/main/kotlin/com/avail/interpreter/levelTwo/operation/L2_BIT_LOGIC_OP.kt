/*
 * L2_BIT_LOGIC_OP.kt
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
package com.avail.interpreter.levelTwo.operation

import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2OperandType.READ_INT
import com.avail.interpreter.levelTwo.L2OperandType.WRITE_INT
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import com.avail.optimizer.L1Translator
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.values.L2SemanticUnboxedInt
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * My instances are logic operations that take two [Int]s and produce an [Int].
 * They must not overflow.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_BIT_LOGIC_OP(
	name: String,
	private vararg val jvmOpcodes: Int
) : L2Operation(
	"BIT_LOGIC_OP($name)",
	READ_INT.named("input1"),
	READ_INT.named("input2"),
	WRITE_INT.named("output"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val input1 = instruction.operand<L2ReadIntOperand>(0)
		val input2 = instruction.operand<L2ReadIntOperand>(1)
		val output = instruction.operand<L2WriteIntOperand>(2)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(output.registerString())
		builder.append(" ← ")
		builder.append(input1.registerString())
		builder.append(" ${name()} ")
		builder.append(input2.registerString())
	}

	fun generateBinaryIntOperation(
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: L1Translator.CallSiteHelper,
		typeGuaranteeFunction: (List<A_Type>) -> A_Type,
		fallbackBody: L1Translator.() -> Unit
	): Boolean
	{
		val (a, b) = arguments
		val (aType, bType) = argumentTypes
		// If either of the argument types does not intersect with int32, then
		// fall back to the primitive invocation.
		if (aType.typeIntersection(int32).isBottom
			|| bType.typeIntersection(int32).isBottom)
		{
			return false
		}

		// Attempt to unbox the arguments.
		val generator = callSiteHelper.generator()
		val fallback = generator.createBasicBlock("fall back to boxed logic")
		val intA = generator.readInt(
			L2SemanticUnboxedInt(a.semanticValue()), fallback)
		val intB = generator.readInt(
			L2SemanticUnboxedInt(b.semanticValue()), fallback)
		if (generator.currentlyReachable())
		{
			// The happy path is reachable.  In this region, the output is
			// guaranteed to be an Int.
			val semanticTemp = generator.newTemp()
			val typeGuarantee = typeGuaranteeFunction(
				listOf(
					aType.typeIntersection(int32),
					bType.typeIntersection(int32)))
			val tempWriter =
				generator.intWrite(
					setOf(L2SemanticUnboxedInt(semanticTemp)),
					restrictionForType(typeGuarantee, UNBOXED_INT_FLAG))
			// Note that both the unboxed and boxed registers end up in the same
			// synonym, so subsequent uses of the result might use either
			// register, depending whether an unboxed value is desired.
			generator.addInstruction(this, intA, intB, tempWriter)
			// Even though we're just using the boxed value again, the unboxed
			// form is also still available for use by subsequent primitives,
			// which could allow the boxing instruction to evaporate.
			callSiteHelper.useAnswer(generator.readBoxed(semanticTemp))
		}
		if (fallback.predecessorEdges().isNotEmpty())
		{
			// The fallback block is reachable, so generate the slow case within
			// it.  Fallback may happen from conversion of non-int32 arguments,
			// or from int32 overflow calculating the sum.
			generator.startBlock(fallback)
			callSiteHelper.translator().fallbackBody()
		}
		return true
	}


	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val input1 = instruction.operand<L2ReadIntOperand>(0)
		val input2 = instruction.operand<L2ReadIntOperand>(1)
		val output = instruction.operand<L2WriteIntOperand>(2)

		// :: output = input1 op input2;
		translator.load(method, input1.register())
		translator.load(method, input2.register())
		jvmOpcodes.forEach(method::visitInsn)
		translator.store(method, output.register())
	}

	companion object {
		/**
		 * The [L2Operation] for computing the bit-wise [Int.and] of two [Int]s.
		 **/
		val bitwiseAnd = L2_BIT_LOGIC_OP("and", Opcodes.IAND)

		/**
		 * The [L2Operation] for computing the bit-wise [Int.or] of two [Int]s.
		 **/
		val bitwiseOr = L2_BIT_LOGIC_OP("or", Opcodes.IOR)

		/**
		 * The [L2Operation] for computing the bit-wise [Int.xor] of two [Int]s.
		 **/
		val bitwiseXor = L2_BIT_LOGIC_OP("xor", Opcodes.IXOR)

		/**
		 * The [L2Operation] for computing the bit-wise [Int.plus] of two
		 * [Int]s, wrapping around with 2's complement semantics as needed.
		 **/
		val wrappedAdd = L2_BIT_LOGIC_OP("wrappedAdd", Opcodes.IADD)

		/**
		 * The [L2Operation] for computing the bit-wise [Int.minus] of two
		 * [Int]s (the first minus the second), wrapping around with 2's
		 * complement semantics as needed.
		 **/
		val wrappedSubtract = L2_BIT_LOGIC_OP("wrappedSubtract", Opcodes.ISUB)
	}
}
