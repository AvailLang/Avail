/*
 * L2_MULTIPLY_INT_BY_INT.kt
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
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.A_Number.Companion.timesCanDestroy
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i31
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.operation.L2ControlFlowInstruction
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT_BOXED
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT_INT
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Mul
import avail.interpreter.primitive.numbers.P_Division
import avail.interpreter.primitive.numbers.P_Multiplication
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticUnboxedInt.Companion.boxed
import avail.utility.mapToSet
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Multiply the value in one int register by the value in another int register,
 * storing back in the second if the result fits in an int without overflow.
 * Otherwise jump to the specified target.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_MULTIPLY_INT_BY_INT(
	var multiplicand: L2ReadIntOperand,
	var multiplier: L2ReadIntOperand,
	@On(SUCCESS) var product: L2WriteIntOperand,
	@On(FAILURE) var outOfRange: L2PcOperand,
	@On(SUCCESS) var inRange: L2PcOperand
): L2ControlFlowInstruction()
{
	// It jumps if the result doesn't fit in an int.
	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(product.registerString())
		append(" ← ")
		append(multiplicand.registerString())
		append(" × ")
		append(multiplier.registerString())
		renderOperandsExcludingFields(
			desiredOperandTypes,
			::multiplicand,
			::multiplier,
			::product)
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		// See if we can reduce it to an always-succeeds version.
		if (multiplicand.isConstantRead && multiplier.isConstantRead)
		{
			val constantProduct =
				multiplicand.constantOrNull!!.timesCanDestroy(
					multiplier.constantOrNull!!, false)
			if (constantProduct.isInt)
			{
				val constantInt = constantProduct.extractInt
				+L2_MOVE_CONSTANT_INT(
					L2IntImmediateOperand(constantInt),
					L2WriteIntOperand(
						product.semanticValues(),
						intRestrictionForConstant(constantInt),
						product.register()))
				jumpTo(inRange.targetBlock())
				return
			}
			jumpTo(outOfRange.targetBlock())
			return
		}
		var range = P_Multiplication.returnTypeGuaranteedByVM(
			null,
			listOf(multiplicand.type(), multiplier.type()))
		// The result isn't a constant.  See if it has the form (y / x) * x.
		for ((a, b) in
			listOf(multiplicand to multiplier, multiplier to multiplicand))
		{
			// Look for it having the form (y / x) * x.
			currentManifest.equivalentSemanticValue(a.semanticValue())
			val divisions = currentManifest
				.semanticValueToSynonym(a.semanticValue())
				.semanticValues()
				.filterIsInstance<L2SemanticUnboxedInt>()
				.map { it.boxed }
				.filterIsInstance<L2SemanticPrimitiveInvocation>()
				.filter { div ->
					div.primitive == P_Division &&
						currentManifest.isEquivalentSemanticValue(
							div.argumentSemanticValues[1], // denominator
							b.semanticValue().boxed)
				}
			for (div in divisions)
			{
				val (numerator, denominator) = div.argumentSemanticValues
				val numeratorType =
					currentManifest.restrictionFor(numerator).type
				val denominatorType =
					currentManifest.restrictionFor(denominator).type
				if (numeratorType.isSubtypeOf(i31)
					&& denominatorType.isSubtypeOf(inclusive(1, Int.MAX_VALUE)))
				{
					val minNum = numeratorType.lowerBound.extractInt
					val maxNum = numeratorType.upperBound.extractInt
					if (denominatorType.lowerBound.equals(
							denominatorType.upperBound))
					{
						// We can easily compute the exact bound.
						val den = denominatorType.lowerBound.extractInt
						val lower = (minNum / den) * den
						val upper = (maxNum / den) * den
						range = range.typeIntersection(inclusive(lower, upper))
						break
					}
					else
					{
						//TODO Implement a general form that accepts a
						// non-constant denominator.
						// It has the form (n / d) * d, where n is non-negative
						// and d is positive.  The lower bound is n's lower
						// bound, but rounded down to the nearest lower multiple
						// of any possible d.
						// The upper bound is equally tricky, using the highest
						// rounded down upper bound of n for any possible d.

						// For now, just use 0 for the lower bound, and the
						// numerator's upper bound.
						range = range.typeIntersection(inclusive(0, maxNum))
						break
					}
				}
			}
		}
		when
		{
			range.upperBound.equals(range.lowerBound) ->
			{
				// The result is a constant.
				if (range.isSubtypeOf(i32))
				{
					// The result is an int constant.
					+L2_MOVE_CONSTANT_INT(
						L2IntImmediateOperand(range.upperBound.extractInt),
						product)
				}
				else
				{
					// The result is an integer constant outside i32.
					+L2_MOVE_CONSTANT_BOXED(
						L2ConstantOperand(range.upperBound),
						boxedWrite(
							product.semanticValues().mapToSet { it.boxed },
							boxedRestrictionForType(range)))
				}
				jumpTo(inRange.targetBlock())
			}
			range.isSubtypeOf(i32) ->
			{
				// The result of this multiplication will not overflow i31.
				+L2_BIT_LOGIC_OP(
					Mul,
					multiplicand,
					multiplier,
					intWrite(
						product.semanticValues(),
						intRestrictionForType(range)))
				jumpTo(inRange.targetBlock())
			}
			range.typeIntersection(i32).isVacuousType ->
			{
				// The result definitely will not fit in an int.
				jumpTo(outOfRange.targetBlock())
			}
			else ->
			{
				// It's still unknown whether the result will fit in an int.
				+this@L2_MULTIPLY_INT_BY_INT
			}
		}
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: longProduct = (long) multiplicand * (long) multiplier;
		translator.load(method, multiplicand)
		method.visitInsn(Opcodes.I2L)
		translator.load(method, multiplier)
		method.visitInsn(Opcodes.I2L)
		method.visitInsn(Opcodes.LMUL)
		val longProductStart = Label()
		val longProductEnd = Label()
		val longProductLocal = translator.nextLocal(Type.LONG_TYPE)
		method.visitLocalVariable(
			"longProduct",
			Type.LONG_TYPE.descriptor,
			null,
			longProductStart,
			longProductEnd,
			longProductLocal)
		method.visitVarInsn(Opcodes.LSTORE, longProductLocal)
		method.visitLabel(longProductStart)
		// :: if (longProduct != intProduct) goto outOfRange;
		method.visitVarInsn(Opcodes.LLOAD, longProductLocal)
		method.visitInsn(Opcodes.L2I)
		method.visitInsn(Opcodes.I2L)
		method.visitVarInsn(Opcodes.LLOAD, longProductLocal)
		method.visitInsn(Opcodes.LCMP)
		translator.jumpIf(method, Opcodes.IFNE, outOfRange)
		// :: else {
		// ::    product = (int)longProduct;
		// ::    goto inRange;
		// :: }
		method.visitVarInsn(Opcodes.LLOAD, longProductLocal)
		method.visitInsn(Opcodes.L2I)
		translator.store(method, product.register())
		translator.jump(method, inRange)
		method.visitLabel(longProductEnd)
		translator.endLocal(longProductLocal, Type.LONG_TYPE)
	}
}
