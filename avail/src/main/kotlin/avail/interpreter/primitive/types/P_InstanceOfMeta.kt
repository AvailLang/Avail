/*
 * P_InstanceOfMeta.kt
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
package avail.interpreter.primitive.types

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instance
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.exceptions.AvailErrorCode.E_NOT_AN_ENUMERATION
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operation.L2_INSTANCE_OF_META
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.Private
import avail.interpreter.primitive.Primitive1
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator

/**
 * **Primitive:** Obtain the sole instance, a type, of the specified metatype.
 */
@Suppress("unused")
object P_InstanceOfMeta : Primitive1(Private, CannotFail, CanFold, CanInline)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val meta = arg1
		return meta.instance
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(instanceMeta(topMeta)),
			topMeta)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_NOT_AN_ENUMERATION))

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val metaReg = arguments[0]
		val returnType = argumentTypes[0].instance
		val restriction = boxedRestrictionForType(returnType)
		val writer = boxedWriteTemp("instance of meta", restriction)
		+L2_INSTANCE_OF_META(metaReg, writer)
		callSiteHelper.useAnswer(
			readBoxed(writer.onlySemanticValue()), false)
		return true
	}
}
