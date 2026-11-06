/*
 * L2_SUBTRACT_INT_MINUS_INT.kt
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

import avail.descriptor.representation.A_Number.Companion.minusCanDestroy
import avail.descriptor.representation.A_Number.Companion.plusCanDestroy
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i64
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.i64Restriction
import avail.interpreter.levelTwo.operation.L2ControlFlowInstruction
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Sub
import avail.interpreter.primitive.numbers.P_Subtraction
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.manifest.L2ValueManifest
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Subtract the subtrahend from the minuend, jumping to the specified target if
 * the result does not fit in a long.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_SUBTRACT_INT_MINUS_INT(
	var minuend: L2ReadIntOperand,
	var subtrahend: L2ReadIntOperand,
	@On(SUCCESS) var difference: L2WriteIntOperand,
	@On(FAILURE) var outOfRange: L2PcOperand,
	@On(SUCCESS) var inRange: L2PcOperand
): L2ControlFlowInstruction()
{
	// It jumps if the result doesn't fit in a long.
	override val hasSideEffect: Boolean get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(difference.registerString())
		append(" ← ")
		append(minuend.registerString())
		append(" - ")
		append(subtrahend.registerString())
		renderOperandsExcludingFields(
			desiredOperandTypes, ::minuend, ::subtrahend, ::difference)
	}

	override fun instructionWasAdded(
		manifest: L2ValueManifest
	): Unit = manifest.run {
		difference.restrict { i64Restriction }
		super.instructionWasAdded(manifest)
		minuend.constantOrNull?.let { minuendValue ->
			// By virtue of the overflow and the fact that the minuend is
			// constant, we can compute a tighter constraint on the subtrahend.
			outOfRange.manifest().updateRestriction(subtrahend.semanticValue()) {
				// m-s is not in i64, and m is constant, so s-m is not in -i64,
				// so s is not in m-i64
				minusType(
					inclusive(
						minuendValue.minusCanDestroy(i64.upperBound, false),
						minuendValue.minusCanDestroy(i64.lowerBound, false)))
			}
		}
		subtrahend.constantOrNull?.let { subtrahendValue ->
			// Similarly, by virtue of the overflow and the fact that the
			// subtrahend is constant, we can compute a tighter constraint on
			// the minuend.
			outOfRange.manifest().updateRestriction(minuend.semanticValue()) {
				// m-s is not in i64, and s is constant, so m is not in i64+s.
				minusType(
					inclusive(
						i64.lowerBound.plusCanDestroy(subtrahendValue, false),
						i64.upperBound.plusCanDestroy(subtrahendValue, false)))
			}
		}
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		val outputRange = P_Subtraction.returnTypeGuaranteedByVM(
			null, listOf(minuend.type, subtrahend.type))
		if (outputRange.isSubtypeOf(i64))
		{
			// It can't overflow, so use the unchecked subtraction.
			+L2_BIT_LOGIC_OP(Sub, minuend, subtrahend, difference)
			jumpTo(inRange.targetBlock())
			return
		}
		+this@L2_SUBTRACT_INT_MINUS_INT
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: longDifference = minuend - subtrahend;
		loadLong(minuend)
		loadLong(subtrahend)
		method.visitInsn(Opcodes.LSUB)
		val longDifferenceStart = Label()
		val longDifferenceEnd = Label()
		val longDifferenceLocal = nextLocal(Type.LONG_TYPE)
		method.visitLocalVariable(
			"longDifference",
			Type.LONG_TYPE.descriptor,
			null,
			longDifferenceStart,
			longDifferenceEnd,
			longDifferenceLocal)
		method.visitVarInsn(Opcodes.LSTORE, longDifferenceLocal)
		method.visitLabel(longDifferenceStart)
		// The difference overflowed exactly when the operands have different
		// signs and the difference's sign differs from the minuend's.
		// :: if (((minuend ^ subtrahend) & (minuend ^ longDifference)) < 0)
		// ::    goto outOfRange;
		loadLong(minuend)
		loadLong(subtrahend)
		method.visitInsn(Opcodes.LXOR)
		loadLong(minuend)
		method.visitVarInsn(Opcodes.LLOAD, longDifferenceLocal)
		method.visitInsn(Opcodes.LXOR)
		method.visitInsn(Opcodes.LAND)
		method.visitInsn(Opcodes.LCONST_0)
		method.visitInsn(Opcodes.LCMP)
		jumpIf(Opcodes.IFLT, outOfRange)
		// :: else {
		// ::    difference = longDifference;
		// ::    goto inRange;
		// :: }
		method.visitVarInsn(Opcodes.LLOAD, longDifferenceLocal)
		storeLong(difference.register())
		jump(inRange)
		method.visitLabel(longDifferenceEnd)
		endLocal(longDifferenceLocal, Type.LONG_TYPE)
	}
}
