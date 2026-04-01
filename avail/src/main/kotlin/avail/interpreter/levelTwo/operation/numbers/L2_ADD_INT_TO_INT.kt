/*
 * L2_ADD_INT_TO_INT.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter.levelTwo.operation.numbers

import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.operation.L2ControlFlowInstruction
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Add
import avail.interpreter.primitive.numbers.P_Addition
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Add the value in one int register to another int register, jumping to the
 * specified target if the result does not fit in an int.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_ADD_INT_TO_INT(
	var augend: L2ReadIntOperand,
	var addend: L2ReadIntOperand,
	@On(SUCCESS) var sum: L2WriteIntOperand,
	@On(FAILURE) var outOfRange: L2PcOperand,
	@On(SUCCESS) var inRange: L2PcOperand
): L2ControlFlowInstruction()
{
	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		sum.restrict { intRestrictionForType(i32) }
		super.instructionWasAdded(manifest)
		addend.constantOrNull?.let { addendValue ->
			// By virtue of the overflow and the fact that the addend is
			// constant, we can compute a tighter constraint on the augend.
			outOfRange.manifest().updateRestriction(augend.semanticValue()) {
				minusType(
					inclusive(
						Int.MIN_VALUE.toLong() - addendValue.extractLong,
						Int.MAX_VALUE.toLong() - addendValue.extractInt))
			}
		}
		augend.constantOrNull?.let { augendValue ->
			// Similarly, by virtue of the overflow and the fact that the augend
			// is constant, we can compute a tighter constraint on the addend.
			outOfRange.manifest().updateRestriction(addend.semanticValue()) {
				minusType(
					inclusive(
						Int.MIN_VALUE.toLong() - augendValue.extractLong,
						Int.MAX_VALUE.toLong() - augendValue.extractLong))
			}
		}
	}

	// It jumps if the result doesn't fit in an int.
	override val hasSideEffect get() = true

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		val outputRange = P_Addition.returnTypeGuaranteedByVM(
			null, listOf(augend.type(), addend.type()))
		if (outputRange.isSubtypeOf(i32))
		{
			+L2_BIT_LOGIC_OP(Add, augend, addend, sum)
			jumpTo(inRange.targetBlock())
			return
		}
		+this@L2_ADD_INT_TO_INT
	}

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(sum.registerString())
		append(" ← ")
		append(augend.registerString())
		append(" + ")
		append(addend.registerString())
		renderOperandsExcludingFields(
			desiredOperandTypes,
			::augend,
			::addend,
			::sum)
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: longSum = (long) augend + (long) addend;
		load(augend)
		method.visitInsn(Opcodes.I2L)
		load(addend)
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LADD)
		val longSumStart = Label()
		val longSumEnd = Label()
		val longSumLocal = nextLocal(Type.LONG_TYPE)
		method.visitLocalVariable(
			"longSum",
			Type.LONG_TYPE.descriptor,
			null,
			longSumStart,
			longSumEnd,
			longSumLocal)
		method.visitVarInsn(Opcodes.LSTORE, longSumLocal)
		method.visitLabel(longSumStart)
		// :: if ((long) (int) longSum != longSum) goto outOfRange;
		method.visitVarInsn(Opcodes.LLOAD, longSumLocal)
		method.visitInsn(Opcodes.L2I)
		method.visitInsn(Opcodes.I2L)
		method.visitVarInsn(Opcodes.LLOAD, longSumLocal)
		method.visitInsn(Opcodes.LCMP)
		jumpIf(Opcodes.IFNE, outOfRange)
		// :: else {
		// ::    sum = (int)longSum;
		// ::    goto inRange;
		// :: }
		method.visitVarInsn(Opcodes.LLOAD, longSumLocal)
		method.visitInsn(Opcodes.L2I)
		store(sum.register())
		jump(inRange)
		method.visitLabel(longSumEnd)
		endLocal(longSumLocal, Type.LONG_TYPE)
	}
}
