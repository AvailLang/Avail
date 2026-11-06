/*
 * L2_DIVIDE_INT_BY_INT.kt
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

import avail.descriptor.representation.A_Number.Companion.extractLong
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.L2ControlFlowInstruction
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Div
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.manifest.L2ValueManifest
import org.objectweb.asm.Opcodes

/**
 * If [dividend] is ≥ 0 and [divisor] is > 0, perform a [Long] division of
 * dividend/divisor, write the result to [quotient], and jump to [success].
 * Otherwise jump to [outOfRangeOrZeroDiv] without writing to [quotient].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_DIVIDE_INT_BY_INT(
	var dividend: L2ReadIntOperand,
	var divisor: L2ReadIntOperand,
	@On(SUCCESS) var quotient: L2WriteIntOperand,
	@On(FAILURE) var outOfRangeOrZeroDiv: L2PcOperand,
	@On(SUCCESS) var success: L2PcOperand
): L2ControlFlowInstruction()
{
	// It jumps for division by zero or out-of-range.
	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(" quo=")
		append(quotient.registerString())
		append(" ← ")
		append(dividend.registerString())
		append(" ÷ ")
		append(divisor.registerString())
		renderOperandsExcludingFields(
			desiredOperandTypes,
			::dividend,
			::divisor,
			::quotient)
	}

	override fun instructionWasAdded(
		manifest: L2ValueManifest
	) = manifest.run {
		super.instructionWasAdded(manifest)
		val initialNumerator = dividend.restriction.intersection(
			manifest.restrictionFor(dividend.semanticValue()))
		val initialDenominator = divisor.restriction.intersection(
			manifest.restrictionFor(divisor.semanticValue()))
		success.manifest().run {
			val successNumerator =
				initialNumerator.intersectionWithType(nonnegativeLongs)
			val successDenominator =
				initialDenominator.intersectionWithType(positiveLongs)
			setRestriction(dividend.semanticValue(), successNumerator)
			setRestriction(divisor.semanticValue(), successDenominator)
			// If the division can't succeed, the success path is dead, and
			// there are no bounds to compute for the quotient.
			if (successNumerator.isImpossible || successDenominator.isImpossible)
			{
				return@run
			}
			val numeratorRange = successNumerator.type
			val denominatorRange = successDenominator.type
			val lowQuotient = numeratorRange.lowerBound.extractLong /
				denominatorRange.upperBound.extractLong
			val highQuotient = numeratorRange.upperBound.extractLong /
				denominatorRange.lowerBound.extractLong
			setRestriction(
				quotient.pickSemanticValue(),
				restrictionForType(inclusive(lowQuotient, highQuotient)))
		}
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		// The unchecked division truncates, which only agrees with Avail's
		// rounding toward negative infinity when the dividend is non-negative
		// and the divisor is positive.
		if (dividend.type.isSubtypeOf(nonnegativeLongs)
			&& divisor.type.isSubtypeOf(positiveLongs))
		{
			+L2_BIT_LOGIC_OP(Div, dividend, divisor, quotient)
			jumpTo(success.targetBlock())
			return
		}
		+this@L2_DIVIDE_INT_BY_INT
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: if (divisor <= 0) goto outOfRangeOrZeroDiv;
		loadLong(divisor)
		method.visitInsn(Opcodes.LCONST_0)
		method.visitInsn(Opcodes.LCMP)
		jumpIf(Opcodes.IFLE, outOfRangeOrZeroDiv)
		// :: if (dividend < 0) goto outOfRangeOrZeroDiv;
		loadLong(dividend)
		method.visitInsn(Opcodes.LCONST_0)
		method.visitInsn(Opcodes.LCMP)
		jumpIf(Opcodes.IFLT, outOfRangeOrZeroDiv)

		loadLong(dividend)
		// :: dividend
		loadLong(divisor)
		// :: dividend, divisor
		method.visitInsn(Opcodes.LDIV)
		// :: quotient
		storeLong(quotient.register())
		// ::
		jump(success)
	}

	companion object
	{
		/** The non-negative [Long]s, the dividends this division handles. */
		private val nonnegativeLongs = inclusive(0L, Long.MAX_VALUE)

		/** The positive [Long]s, the divisors this division handles. */
		private val positiveLongs = inclusive(1L, Long.MAX_VALUE)
	}
}
