/*
 * P_CurrentMacroName.kt
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
package com.avail.interpreter.primitive.compiler

import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.MACRO_BUNDLE_KEY
import com.avail.descriptor.bundles.A_Bundle.Companion.message
import com.avail.descriptor.fiber.FiberDescriptor.GeneralFlag.IS_EVALUATING_MACRO
import com.avail.descriptor.maps.A_Map.Companion.mapAt
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.exceptions.AvailErrorCode.E_NOT_EVALUATING_MACRO
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Answer the [atom][A_Atom] for which a send phrase is being
 * macro-evaluated in the current fiber.  Fail if macro evaluation is not
 * happening in this fiber.
 */
@Suppress("unused")
object P_CurrentMacroName : Primitive(0, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(0)
		if (!interpreter.fiber().generalFlag(IS_EVALUATING_MACRO))
		{
			return interpreter.primitiveFailure(E_NOT_EVALUATING_MACRO)
		}
		interpreter.fiber().availLoader()
			?: error("Macro expansion shouldn't be possible after loading")
		val fiberGlobals = interpreter.fiber().fiberGlobals()
		val clientData = fiberGlobals.mapAt(CLIENT_DATA_GLOBAL_KEY.atom)
		val currentMacroBundle = clientData.mapAt(MACRO_BUNDLE_KEY.atom)
		return interpreter.primitiveSuccess(currentMacroBundle.message())
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			emptyTuple,
			ATOM.o
		)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_NOT_EVALUATING_MACRO))
}
