/*
 * P_TupleToObjectType.kt
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

package com.avail.interpreter.primitive.objects

import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.objects.ObjectDescriptor
import com.avail.descriptor.objects.ObjectDescriptor.Companion.objectFromTuple
import com.avail.descriptor.objects.ObjectLayoutVariant.Companion.variantForFields
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.objectTypeFromMap
import com.avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.TupleTypeDescriptor.tupleTypeForTypes
import com.avail.descriptor.types.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED
import com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType
import com.avail.interpreter.levelTwo.operation.L2_CREATE_OBJECT
import com.avail.optimizer.L1Translator

/**
 * **Primitive:** Convert a [tuple][TupleDescriptor] of field assignment into an
 * [object][ObjectDescriptor]. A field assignment is a 2-tuple whose first
 * element is an [atom][AtomDescriptor] that represents the field and whose
 * second element is its value.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_TupleToObject : Primitive(1, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val tuple = interpreter.argument(0)
		return interpreter.primitiveSuccess(objectFromTuple(tuple))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(zeroOrMoreOf(tupleTypeForTypes(ATOM.o(), ANY.o()))),
			mostGeneralObjectType())

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		val tupleType = argumentTypes[0]
		val tupleSizes = tupleType.sizeRange()
		val tupleSizeLowerBound = tupleSizes.lowerBound()
		if (!tupleSizeLowerBound.equals(tupleSizes.upperBound())
		    || !tupleSizeLowerBound.isInt)
		{
			// Variable number of <key,value> pairs.  Give up.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		}
		val tupleSize = tupleSizeLowerBound.extractInt()
		var fieldTypeMap = emptyMap()
		for (i in 1 .. tupleSize)
		{
			val pairType = tupleType.typeAtIndex(i)
			assert(pairType.sizeRange().lowerBound().extractInt() == 2)
			assert(pairType.sizeRange().upperBound().extractInt() == 2)
			val keyType = pairType.typeAtIndex(1)
			if (!keyType.isEnumeration || !keyType.instanceCount().equalsInt(1))
			{
				// Can only strengthen if all key atoms are statically known.
				return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
			}
			val keyValue = keyType.instance()
			if (fieldTypeMap.hasKey(keyValue))
			{
				// In case the semantics of this situation change.  Give up.
				return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
			}
			assert(keyValue.isAtom)
			val valueType = pairType.typeAtIndex(2)
			fieldTypeMap =
				fieldTypeMap.mapAtPuttingCanDestroy(keyValue, valueType, true)
		}
		return objectTypeFromMap(fieldTypeMap)
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: L1Translator.CallSiteHelper): Boolean
	{
		// If we know the exact keys, we can statically determine the
		// ObjectLayoutVariant to populate, and write the fields into the fixed
		// offsets.

		val pairsReg = arguments[0]
		val pairsType = argumentTypes[0]
		val sizeRange = pairsType.sizeRange()

		val generator = translator.generator

		if (!sizeRange.lowerBound().isInt) return false
		val size = sizeRange.lowerBound().extractInt()
		if (!sizeRange.upperBound().equalsInt(size)) return false
		// The tuple size is known.  See if the order of field atoms is known.
		val atoms = (1..size).map {
			val keyType = pairsType.typeAtIndex(it).typeAtIndex(1)
			if (!keyType.isEnumeration || !keyType.instanceCount().equalsInt(1))
			{
				return false
			}
			// It's at known to be a particular atom, and not instanceMeta.
			keyType.instance()
		}
		// Check that the atoms are unique.
		val atomsSet = setFromCollection(atoms)
		if (atomsSet.setSize() != size) return false
		// Look up the ObjectLayoutVariant statically.
		val variant = variantForFields(atomsSet)
		val fieldMap = variant.fieldToSlotIndex
		val sourcesByFieldIndex = arrayOfNulls<L2ReadBoxedOperand>(size)
		val pairSources: List<L2ReadBoxedOperand> =
			generator.explodeTupleIfPossible(pairsReg, argumentTypes)
				?: return false
		(atoms zip pairSources).forEach { (atom, pairSource) ->
			fieldMap[atom]?.let { index ->
				if (index != 0)
				{
					sourcesByFieldIndex[index - 1] =
						generator.extractTupleElement(pairSource, 2)
				}
			}
		}
		val write = generator.boxedWriteTemp(
			restrictionForType(callSiteHelper.expectedType, BOXED))

		generator.addInstruction(
			L2_CREATE_OBJECT.instance,
			L2ConstantOperand(variant.thisPojo),
			L2ReadBoxedVectorOperand(
				Array(sourcesByFieldIndex.size)
				{
					sourcesByFieldIndex[it]!!
				}.toList()),
			write)
		callSiteHelper.useAnswer(generator.readBoxed(write))
		return true
	}
}
