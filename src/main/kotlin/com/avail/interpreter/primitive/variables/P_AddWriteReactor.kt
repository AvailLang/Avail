/*
 * P_AddWriteReactor.kt
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.variables

import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.AtomDescriptor
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.descriptor.VariableDescriptor
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor
import com.avail.descriptor.VariableTypeDescriptor.mostGeneralVariableType
import com.avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Fallibility.*
import com.avail.interpreter.Primitive.Flag.HasSideEffect

/**
 * **Primitive:** Add a [ write reactor][VariableAccessReactor] to the specified [variable][VariableDescriptor].
 * The supplied [key][AtomDescriptor] may be used subsequently to
 * remove the write reactor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_AddWriteReactor : Primitive(3, HasSideEffect)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val `var` = interpreter.argument(0)
		val key = interpreter.argument(1)
		val reactorFunction = interpreter.argument(2)
		// Forbid special atoms.
		if (key.isAtomSpecial)
		{
			return interpreter.primitiveFailure(E_SPECIAL_ATOM)
		}
		val sharedFunction = reactorFunction.makeShared()
		val writeReactor = VariableAccessReactor(sharedFunction)
		`var`.addWriteReactor(key, writeReactor)
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				mostGeneralVariableType(),
				ATOM.o(),
				functionType(
					emptyTuple(),
					TOP.o())),
			TOP.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(E_SPECIAL_ATOM))
	}

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>): Primitive.Fallibility
	{
		//		final A_Type varType = argumentTypes.get(0);
		val keyType = argumentTypes[1]
		//		final A_Type functionType = argumentTypes.get(2);
		if (keyType.isEnumeration)
		{
			var allSpecial = true
			var noneSpecial = true
			for (key in keyType.instances())
			{
				val isSpecial = key.isAtomSpecial
				allSpecial = allSpecial && isSpecial
				noneSpecial = noneSpecial && !isSpecial
			}
			// The aggregate booleans can only both be true in the degenerate
			// case that keyType is ⊥, which should be impossible.
			if (noneSpecial)
			{
				return CallSiteCannotFail
			}
			if (allSpecial)
			{
				return CallSiteMustFail
			}
		}
		return CallSiteCanFail
	}

}