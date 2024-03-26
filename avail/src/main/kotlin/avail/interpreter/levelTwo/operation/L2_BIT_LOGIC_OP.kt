/*
 * L2_BIT_LOGIC_OP.kt
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

import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OldInstruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_INT
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_INT
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.optimizer.L1Translator
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.L2IsUnboxedIntCondition.Companion.unboxedIntCondition
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticUnboxedInt
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * My instances are logic operations that take two [Int]s and produce an [Int].
 * They must not overflow.
 *
 * @constructor
 *   Instantiate a two-arcgument i32 logic operation for a particular purpose,
 *   such as addition or exclusive-or.
 * @property jvmOpcodes
 *   A sequence of Int opcodes to emit for this logic operation, once the two
 *   int values have been pushed.  It should have the effect of removing them
 *   from the JVM operand stack and leaving a single int as the result.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_BIT_LOGIC_OP
private constructor(
	name: String,
	private vararg val jvmOpcodes: Int
) : L2Operation(
	"BIT_LOGIC_OP($name)",
	READ_INT.named("input1"),
	READ_INT.named("input2"),
	WRITE_INT.named("output"))
{
	override fun appendToWithWarnings(
		instruction: L2OldInstruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		val input1 = instruction.operand<L2ReadIntOperand>(0)
		val input2 = instruction.operand<L2ReadIntOperand>(1)
		val output = instruction.operand<L2WriteIntOperand>(2)
		instruction.renderPreamble(builder)
		builder.append(' ')
		builder.append(output.registerString())
		builder.append(" ← ")
		builder.append(input1.registerString())
		builder.append(" $name ")
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
		if (aType.typeIntersection(i32).isBottom
			|| bType.typeIntersection(i32).isBottom)
		{
			return false
		}

		// Attempt to unbox the arguments.
		val generator = callSiteHelper.generator
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
					aType.typeIntersection(i32),
					bType.typeIntersection(i32)))
			val tempWriter =
				generator.intWrite(
					setOf(L2SemanticUnboxedInt(semanticTemp)),
					intRestrictionForType(typeGuarantee))
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
			callSiteHelper.translator.fallbackBody()
		}
		return true
	}

	override fun interestingSplitConditions(
		instruction: L2Instruction
	): List<L2SplitCondition?>
	{
		val input1 = instruction.operand<L2ReadIntOperand>(0)
		val input2 = instruction.operand<L2ReadIntOperand>(1)
		val output = instruction.operand<L2WriteIntOperand>(2)
		return listOf(
			unboxedIntCondition(
				listOf(
					input1.register(), input2.register(), output.register())))
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
		 */
		val bitwiseAnd = L2_BIT_LOGIC_OP("and", Opcodes.IAND)

		/**
		 * The [L2Operation] for computing the bit-wise [Int.or] of two [Int]s.
		 */
		val bitwiseOr = L2_BIT_LOGIC_OP("or", Opcodes.IOR)

		/**
		 * The [L2Operation] for computing the bit-wise [Int.xor] of two [Int]s.
		 */
		val bitwiseXor = L2_BIT_LOGIC_OP("xor", Opcodes.IXOR)

		/**
		 * The [L2Operation] for computing the bit-wise sum of two [Int]s,
		 * wrapping around with 2's complement semantics as needed.
		 */
		val wrappedAdd = L2_BIT_LOGIC_OP("wrappedAdd", Opcodes.IADD)

		/**
		 * The [L2Operation] for computing the difference of two [Int]s (the
		 * first minus the second), wrapping around with 2's complement
		 * semantics as needed.
		 */
		val wrappedSubtract = L2_BIT_LOGIC_OP("wrappedSubtract", Opcodes.ISUB)

		/**
		 * The [L2Operation] for computing the product of two [Int]s, wrapping
		 * around with 2's complement semantics as needed.
		 */
		val wrappedMultiply = L2_BIT_LOGIC_OP("wrappedMultiply", Opcodes.IMUL)

		/**
		 * The [L2Operation] for computing the ratio of two [Int]s (i.e.,
		 * intA / intB), wrapping around with 2's complement semantics as needed.
		 */
		val wrappedDivide = L2_BIT_LOGIC_OP("wrappedDivide", Opcodes.IDIV)

		/**
		 * The [L2Operation] for shifting an [Int] rightward by the specified
		 * number of bit positions, treating it as unsigned.  The second operand
		 * should be between 0 and 31 (otherwise only the bottom five bits will
		 * be used).  The result can be negative if the first argument is
		 * negative and the shift is zero.
		 */
		val bitwiseUnsignedShiftRight =
			L2_BIT_LOGIC_OP("unsignedShiftRight", Opcodes.IUSHR)

		/**
		 * The [L2Operation] for shifting an [Int] rightward by the specified
		 * number of bit positions, respecting its sign.  The second operand
		 * should be between 0 and 31 (otherwise only the bottom five bits will
		 * be used).
		 */
		val bitwiseSignedShiftRight =
			L2_BIT_LOGIC_OP("signedShiftRight", Opcodes.ISHR)

		/**
		 * The [L2Operation] for shifting an [Int] leftward by the specified
		 * number of bit positions, respecting its sign.  The second operand
		 * should be between 0 and 31 (otherwise only the bottom five bits will
		 * be used).
		 */
		val bitwiseShiftLeft = L2_BIT_LOGIC_OP("shiftLeft", Opcodes.ISHL)
	}
}
