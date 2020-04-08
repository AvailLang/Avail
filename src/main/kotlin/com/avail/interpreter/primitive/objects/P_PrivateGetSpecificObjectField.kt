/*
 * P_PrivateGetSpecificObjectField.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.bottom
import com.avail.descriptor.objects.ObjectDescriptor
import com.avail.descriptor.atoms.A_Atom
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED
import com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType
import com.avail.interpreter.levelTwo.operation.L2_GET_OBJECT_FIELD
import com.avail.optimizer.L1Translator

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
object P_PrivateGetSpecificObjectField : Primitive(
	1, Private, CanInline, CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val obj = interpreter.argument(0)
		val primitiveFunction = interpreter.function!!
		//TODO Comment out these assertions.
		assert(primitiveFunction.code().primitive() === this)
		assert(primitiveFunction.numOuterVars() == 1)
		val field = primitiveFunction.outerVarAt(1)
		assert(field.isAtom)

		return interpreter.primitiveSuccess(obj.fieldAt(field))
	}

	/** Specific [A_RawFunction]s will have suitable signatures. */
	override fun privateBlockTypeRestriction(): A_Type = bottom()

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>
	): A_Type {
		// We don't have the function closure, so we don't have the field atom,
		// so we simply use the raw function's function type's return type.
		return rawFunction.functionType().returnType()
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: L1Translator.CallSiteHelper
	): Boolean {
		// This primitive is private, and the function *should* only have been
		// constructed by P_CreateObjectFieldGetter.  Play it safe if the
		// function appears to have been created some other way.
		val function = functionToCallReg.constantOrNull() ?: return false

		val objectReg = arguments[0]
		val objectType = argumentTypes[0]
		val fieldAtom = function.outerVarAt(1)
		val fieldType = objectType.fieldTypeAt(fieldAtom)
		val constant = objectReg.restriction().constantOrNull
		when {
			// Do the folding here.  If we made this primitive CanFold, it would
			// attempt to access the interpreter.function during evaluation,
			// which is not available during folding.
			constant !== null ->
				callSiteHelper.useAnswer(
					translator.generator.boxedConstant(
						constant.fieldAt(fieldAtom)))

			fieldType.isEnumeration
					&& !fieldType.isInstanceMeta
					&& fieldType.instanceCount().equalsInt(1) ->
				callSiteHelper.useAnswer(
					translator.generator.boxedConstant(fieldType.instance()))

			else -> {
				val write = translator.generator.boxedWriteTemp(
					restrictionForType(fieldType, BOXED))
				translator.addInstruction(
					L2_GET_OBJECT_FIELD.instance,
					objectReg,
					L2ConstantOperand(fieldAtom),
					write)
				callSiteHelper.useAnswer(translator.readBoxed(write))
				// TODO - Generate L2 code to collect statistics on the variants
				// that are encountered, then at the next reoptimization, inline
				// L2 instructions that access the field by index.
			}
		}
		return true
	}
}
