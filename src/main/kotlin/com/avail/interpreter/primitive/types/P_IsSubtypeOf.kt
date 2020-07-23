/*
 * P_IsSubtypeOf.kt
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
package com.avail.interpreter.primitive.types

import com.avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import com.avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import com.avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operation.L2_GET_TYPE
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_OBJECT
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_SUBTYPE_OF_CONSTANT
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_SUBTYPE_OF_OBJECT
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.optimizer.L2Generator.Companion.edgeTo

/**
 * **Primitive:** Answer whether type1 is a subtype of type2 (or equal).
 */
@Suppress("unused")
object P_IsSubtypeOf : Primitive(2, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val type1 = interpreter.argument(0)
		val type2 = interpreter.argument(1)
		return interpreter.primitiveSuccess(
			objectFromBoolean(type1.isSubtypeOf(type2)))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				topMeta(),
				topMeta()),
			booleanType)

	/**
	 * Some identities apply.  The terms x and y are the values being compared
	 * (not necessarily known statically), and x' and y' are their static types
	 * (making them metatypes).
	 *
	 *  1. The test is always true if the exact type y1 is known (not a
	 * subtype) and x' ⊆ y1'.
	 *  1. The test is always false if the exact type x1 is known (not a
	 * subtype) and x1' ⊈ y'.
	 *  1. The test is always true if x = ⊥.
	 *
	 */
	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val xTypeReg = arguments[0]
		val yTypeReg = arguments[1]

		val xMeta = xTypeReg.type()
		val yMeta = yTypeReg.type()
		val xType = xMeta.instance()
		val yType = yMeta.instance()

		val constantYType = yTypeReg.constantOrNull()
		if (constantYType !== null)
		{
			assert(constantYType.isSubtypeOf(yType))
			if (xType.isSubtypeOf(constantYType))
			{
				// The y type is known precisely, and the x type is constrained
				// to always be a subtype of it.
				callSiteHelper.useAnswer(
					translator.generator.boxedConstant(trueObject))
				return true
			}
		}

		val constantXType = xTypeReg.constantOrNull()
		if (constantXType !== null)
		{
			assert(constantXType.isSubtypeOf(xType))
			if (!constantXType.isSubtypeOf(yType))
			{
				// In x ⊆ y, the exact type x happens to be known statically,
				// and it is not a subtype of y.  The actual y might be more
				// specific at runtime, but x still can't be a subtype of the
				// stronger y.
				callSiteHelper.useAnswer(
					translator.generator.boxedConstant(falseObject))
				return true
			}
		}

		if (xType.isBottom)
		{
			// ⊥ is a subtype of all other types.  We test this separately from
			// looking for a constant x, since ⊥'s type is special and doesn't
			// report that it only has one instance (i.e., ⊥).
			callSiteHelper.useAnswer(
				translator.generator.boxedConstant(trueObject))
			return true
		}

		val ifSubtype = translator.generator.createBasicBlock("if subtype")
		val ifNotSubtype = translator.generator.createBasicBlock("not subtype")

		val xDef = xTypeReg.definitionSkippingMoves(true)
		if (xDef.operation() == L2_GET_TYPE)
		{
			// X is an L2_GET_TYPE of some other register.
			// Convert this into an L2_JUMP_IF_KIND_OF_OBJECT/CONSTANT, but
			// use the value that was provided to L2_GET_TYPE.
			val xInstanceRead = L2_GET_TYPE.sourceValueOf(xDef)
			if (constantYType !== null)
			{
				translator.jumpIfKindOfConstant(
					xInstanceRead, constantYType, ifSubtype, ifNotSubtype)
			}
			else
			{
				translator.addInstruction(
					L2_JUMP_IF_KIND_OF_OBJECT,
					xInstanceRead,
					yTypeReg,
					edgeTo(ifSubtype),
					edgeTo(ifNotSubtype))
			}
		}
		else if (constantYType !== null)
		{
			translator.addInstruction(
				L2_JUMP_IF_SUBTYPE_OF_CONSTANT,
				xTypeReg,
				L2ConstantOperand(constantYType),
				edgeTo(ifSubtype),
				edgeTo(ifNotSubtype))
		}
		else
		{
			translator.addInstruction(
				L2_JUMP_IF_SUBTYPE_OF_OBJECT,
				xTypeReg,
				yTypeReg,
				edgeTo(ifSubtype),
				edgeTo(ifNotSubtype))
		}
		translator.generator.startBlock(ifSubtype)
		callSiteHelper.useAnswer(
			translator.generator.boxedConstant(trueObject))
		translator.generator.startBlock(ifNotSubtype)
		callSiteHelper.useAnswer(
			translator.generator.boxedConstant(falseObject))
		return true
	}
}
