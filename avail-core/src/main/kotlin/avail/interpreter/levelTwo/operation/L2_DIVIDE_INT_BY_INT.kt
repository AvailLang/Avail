/*
 * L2_DIVIDE_INT_BY_INT.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * If the divisor is zero, then jump to the zero divisor label.  Otherwise
 * divide the dividend int by the divisor int.  If the quotient and remainder
 * each fit in an [Int], then store them and continue, otherwise jump to
 * an out-of-range label.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_DIVIDE_INT_BY_INT : L2ControlFlowOperation(
	L2OperandType.READ_INT.named("dividend"),
	L2OperandType.READ_INT.named("divisor"),
	L2OperandType.WRITE_INT.named("quotient", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.WRITE_INT.named("remainder", L2NamedOperandType.Purpose.SUCCESS),
	L2OperandType.PC.named("out of range", L2NamedOperandType.Purpose.FAILURE),
	L2OperandType.PC.named("zero divisor", L2NamedOperandType.Purpose.OFF_RAMP),
	L2OperandType.PC.named("success", L2NamedOperandType.Purpose.SUCCESS))
{
	// It jumps for division by zero or out-of-range.
	override val hasSideEffect get() = true

	override fun instructionWasAdded(
		instruction: L2Instruction, manifest: L2ValueManifest)
	{
		assert(this == instruction.operation)
		//		final L2ReadIntOperand dividend = instruction.operand(0);
		val divisor =
			instruction.operand<L2ReadIntOperand>(1)
		//		final L2WriteIntOperand quotient = instruction.operand(2);
//		final L2WriteIntOperand remainder = instruction.operand(3);
//		final L2PcOperand outOfRange = instruction.operand(4);
		val zeroDivisor =
			instruction.operand<L2PcOperand>(5)
		//		final L2PcOperand success = instruction.operand(6);
		super.instructionWasAdded(instruction, manifest)

		// On the zeroDivisor edge, the divisor is definitely zero.
		zeroDivisor.manifest().setRestriction(
			divisor.semanticValue(),
			restrictionForConstant(zero, UNBOXED_INT_FLAG))
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val dividend = instruction.operand<L2ReadIntOperand>(0)
		val divisor = instruction.operand<L2ReadIntOperand>(1)
		val quotient = instruction.operand<L2WriteIntOperand>(2)
		val remainder = instruction.operand<L2WriteIntOperand>(3)
		//		final L2PcOperand outOfRange = instruction.operand(4);
//		final L2PcOperand zeroDivisor = instruction.operand(5);
//		final L2PcOperand success = instruction.operand(6);
		renderPreamble(instruction, builder)
		builder.append("quo=")
		builder.append(quotient.registerString())
		builder.append(", rem=")
		builder.append(remainder.registerString())
		builder.append(" ← ")
		builder.append(dividend.registerString())
		builder.append(" ÷ ")
		builder.append(divisor.registerString())
		renderOperandsStartingAt(instruction, 4, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val dividend = instruction.operand<L2ReadIntOperand>(0)
		val divisor = instruction.operand<L2ReadIntOperand>(1)
		val quotient = instruction.operand<L2WriteIntOperand>(2)
		val remainder = instruction.operand<L2WriteIntOperand>(3)
		val outOfRange = instruction.operand<L2PcOperand>(4)
		val zeroDivisor = instruction.operand<L2PcOperand>(5)
		val success = instruction.operand<L2PcOperand>(6)

		// :: if (divisor == 0) goto zeroDivisorIndex;
		translator.load(method, divisor.register())
		method.visitJumpInsn(Opcodes.IFEQ, translator.labelFor(zeroDivisor.offset()))

		// a/b for b<0:  use -(a/-b)
		// :: if (divisor < 0) quotient = -(dividend / -divisor);
		translator.load(method, divisor.register())
		val checkDividend = Label()
		method.visitJumpInsn(Opcodes.IFGE, checkDividend)
		translator.load(method, dividend.register())
		method.visitInsn(Opcodes.I2L)
		translator.load(method, divisor.register())
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LNEG)
		method.visitInsn(Opcodes.LDIV)
		method.visitInsn(Opcodes.LNEG)
		val quotientLong = translator.nextLocal(Type.LONG_TYPE)
		method.visitVarInsn(Opcodes.LSTORE, quotientLong)
		val quotientComputed = Label()
		method.visitJumpInsn(Opcodes.GOTO, quotientComputed)

		// a/b for a<0, b>0:  use -1-(-1-a)/b
		// :: else if (dividend < 0)
		// ::    quotient = -1L - ((-1L - dividend) / divisor);
		translator.load(method, dividend.register())
		val bothNonNegative = Label()
		method.visitJumpInsn(Opcodes.IFGE, bothNonNegative)
		translator.longConstant(method, -1)
		method.visitInsn(Opcodes.DUP)
		translator.load(method, dividend.register())
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LSUB)
		translator.load(method, divisor.register())
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LDIV)
		method.visitInsn(Opcodes.LSUB)
		method.visitVarInsn(Opcodes.LSTORE, quotientLong)
		method.visitJumpInsn(Opcodes.GOTO, quotientComputed)

		// :: else quotient = dividend / divisor;
		translator.load(method, dividend.register())
		method.visitInsn(Opcodes.I2L)
		translator.load(method, divisor.register())
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LDIV)
		method.visitVarInsn(Opcodes.LSTORE, quotientLong)

		// Remainder is always non-negative.
		// :: remainder = dividend - (quotient * divisor);
		method.visitLabel(quotientComputed)
		translator.load(method, dividend.register())
		method.visitInsn(Opcodes.I2L)
		method.visitVarInsn(Opcodes.LLOAD, quotientLong)
		translator.load(method, divisor.register())
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LMUL)
		method.visitInsn(Opcodes.LSUB)
		val remainderLong = translator.nextLocal(Type.LONG_TYPE)
		method.visitVarInsn(Opcodes.LSTORE, remainderLong)

		// :: if (quotient != (int) quotient) goto outOfRange;
		method.visitVarInsn(Opcodes.LLOAD, quotientLong)
		method.visitInsn(Opcodes.DUP)
		method.visitInsn(Opcodes.L2I)
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LCMP)
		translator.jumpIf(method, Opcodes.IFNE, outOfRange)
		// ::  if (remainder != (int) remainder) goto outOfRange;
		method.visitVarInsn(Opcodes.LLOAD, remainderLong)
		method.visitInsn(Opcodes.DUP)
		method.visitInsn(Opcodes.L2I)
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LCMP)
		translator.jumpIf(method, Opcodes.IFNE, outOfRange)
		// ::    intQuotient = (int) quotient;
		method.visitVarInsn(Opcodes.LLOAD, quotientLong)
		method.visitInsn(Opcodes.L2I)
		translator.store(method, quotient.register())
		// ::    intRemainder = (int) remainder;
		method.visitVarInsn(Opcodes.LLOAD, remainderLong)
		method.visitInsn(Opcodes.L2I)
		translator.store(method, remainder.register())
		// ::    goto success;
		translator.jump(method, success)
	}
}
