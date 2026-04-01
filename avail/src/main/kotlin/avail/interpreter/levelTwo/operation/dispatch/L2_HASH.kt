/*
 * L2_HASH.kt
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
package avail.interpreter.levelTwo.operation.dispatch

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.hashMethod
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.jvm.JVMTranslator

/**
 * Answer the [hash][A_BasicObject.hash] of the specified value.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_HASH(
	var value: L2ReadBoxedOperand,
	var hash: L2WriteIntOperand
): L2Instruction()
{
	override fun L2GeneratorInterface.analyzeAndOptionallyRewrite(
	): L2Instruction?
	{
		value.constantOrNull?.let { constant ->
			// Hash the constant now.
			moveIntRegister(
				unboxedIntConstant(constant.hash()).semanticValue(),
				hash.semanticValues())
			return null
		}
		// Fall back to hashing dynamically.
		return this@L2_HASH
	}

	override fun traceCandidateSplitConditions(
		writeOperand: L2WriteOperand<*>,
		restriction: TypeRestriction,
		tracer: L2SplitCondition.RestrictionTracer)
	{
		assert(writeOperand == hash)
		assert(restriction.isUnboxedInt)
		if (!tracer.traceArithmetic) return
		// Obviously we can't "unhash" an int.  However, if the source value is
		// an enuumeration, we can transform the restriction on the hash into a
		// restriction on which values would cause the hash to satisfy the int
		// restriction.
		val valueType = value.restriction().type
		if (!valueType.isEnumeration || valueType.isInstanceMeta)
		{
			// The value isn't constrained to a finite enumeration.  Give up.
			return
		}
		val values = valueType.instances
		val satisfiedValues = values.filter { value ->
			restriction.containsEntireType(singleInt(value.hash()))
		}
		if (satisfiedValues.isEmpty())
		{
			// Everything was disqualified by the hash's restriction.  Give up.
			return
		}
		if (satisfiedValues.size == values.setSize)
		{
			// Nothing was disqualified by the hash's restriction.  Give up.
			return
		}
		val valueRestriction = boxedRestrictionForType(
			enumerationWith(setFromCollection(satisfiedValues)))
		tracer.continueTracing(value.register(), valueRestriction)
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: hash = tuple.hash();
		load(value)
		generateCall(hashMethod)
		store(hash.register())
	}
}
