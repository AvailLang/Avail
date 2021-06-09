/*
 * P_AddWriteReactor.kt
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

package com.avail.interpreter.primitive.variables

import com.avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.instance
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import com.avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Add a [write&#32;reactor][VariableAccessReactor] to the
 * specified [variable][VariableDescriptor]. The supplied [key][AtomDescriptor]
 * may be used subsequently to remove the write reactor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_AddWriteReactor : Primitive(3, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val variable = interpreter.argument(0)
		val key = interpreter.argument(1)
		val reactorFunction = interpreter.argument(2)
		// Forbid special atoms.
		if (key.isAtomSpecial())
		{
			return interpreter.primitiveFailure(E_SPECIAL_ATOM)
		}
		val sharedFunction = reactorFunction.makeShared()
		val writeReactor = VariableAccessReactor(sharedFunction)
		variable.addWriteReactor(key, writeReactor)
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralVariableType(),
				ATOM.o,
				functionType(
					emptyTuple,
					TOP.o
				)),
			TOP.o
		)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_SPECIAL_ATOM))

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>): Fallibility
	{
		//		final A_Type varType = argumentTypes.get(0);
		val keyType = argumentTypes[1]
		//		final A_Type functionType = argumentTypes.get(2);
		if (keyType.isEnumeration)
		{
			val allSpecial = keyType.instance().all { it.isAtomSpecial() }
			val noneSpecial = keyType.instance().none { it.isAtomSpecial() }
			// The aggregate booleans can only both be true in the degenerate
			// case that keyType is ⊥, which should be impossible.
			when {
				allSpecial -> return Fallibility.CallSiteMustFail
				noneSpecial -> return Fallibility.CallSiteCannotFail
			}
		}
		return Fallibility.CallSiteCanFail
	}
}
