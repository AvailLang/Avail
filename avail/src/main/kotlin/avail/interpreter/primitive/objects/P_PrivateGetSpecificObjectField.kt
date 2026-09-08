/*
 * P_PrivateGetSpecificObjectField.kt
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
package avail.interpreter.primitive.objects

import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.representation.A_Atom
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Function.Companion.numOuterVars
import avail.descriptor.representation.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instance
import avail.descriptor.representation.A_Type.Companion.instanceCount
import avail.descriptor.representation.A_Type.Companion.returnType
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.L2_GET_OBJECT_FIELD
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.Private
import avail.interpreter.primitive.Primitive1
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.values.L2SemanticValue.Companion.constant

/**
 * **Primitive:** Given an [object][ObjectDescriptor], extract the field
 * corresponding to the [A_Atom] saved in the sole outer variable.
 *
 * This primitive is private, and an [A_RawFunction] using it can only be
 * constructed via [P_CreateObjectFieldGetter].  That primitive ensures that
 * <em>this</em> primitive's raw function has a type-safe signature and cannot
 * fail.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_PrivateGetSpecificObjectField : Primitive1(
	Private, CanInline, CannotFail)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val obj = arg1
		val primitiveFunction = function!!
		//TODO Comment out these assertions.
		assert(primitiveFunction.code().codePrimitive() ===
			this@P_PrivateGetSpecificObjectField)
		assert(primitiveFunction.numOuterVars == 1)
		val field = primitiveFunction.outerVarAt(1)
		assert(field.isAtom)
		return obj.fieldAt(field)
	}

	/** Specific [A_RawFunction]s will have suitable signatures. */
	override fun privateBlockTypeRestriction(): A_Type = bottom

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type
	{
		// We don't have the function closure, so we don't have the field atom,
		// so we simply use the raw function's function type's return type.
		return rawFunction!!.functionType().returnType
	}

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean {
		// This primitive is private, and the function *should* only have been
		// constructed by P_CreateObjectFieldGetter.  Play it safe if the
		// function appears to have been created some other way.
		val function = functionToCallReg.constantOrNull ?: return false

		val objectReg = arguments[0]
		val objectType = argumentTypes[0]
		val fieldAtom = function.outerVarAt(1)
		val fieldType = objectType.fieldTypeAt(fieldAtom)
		val constant = objectReg.constantOrNull

		val semanticFieldValue = P_GetObjectField.semanticInvocation(
			objectReg.semanticValue(),
			constant(fieldAtom))

		when
		{
			// Do the folding here.  If we made this primitive CanFold, it would
			// attempt to access the interpreter's function during evaluation,
			// which is not available during folding.
			constant !== null ->
			{
				move(
					constant(constant.fieldAt(fieldAtom)),
					setOf(semanticFieldValue))
			}

			fieldType.isEnumeration
				&& !fieldType.isInstanceMeta
				&& fieldType.instanceCount.equalsInt(1) ->
			{
				move(
					constant(fieldType.instance),
					setOf(semanticFieldValue))
			}

			else ->
			{
				+L2_GET_OBJECT_FIELD(
					objectReg,
					L2ConstantOperand(fieldAtom),
					boxedWrite(
						semanticFieldValue, restrictionForType(fieldType)))
				//TODO - Generate L2 code to collect statistics on the variants
				// that are encountered, then at the next reoptimization, inline
				// L2 instructions that access the field by index.
			}
		}
		callSiteHelper.useAnswer(readBoxed(semanticFieldValue), false)
		return true
	}
}
