/*
 * P_AtomSetFieldBound.kt
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
package avail.interpreter.primitive.atoms

import avail.descriptor.atoms.A_Atom.Companion.fieldAtomConstraint
import avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode.E_PROPERTY_MAY_ONLY_BE_SET_ONCE
import avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Within the first [atom][AtomDescriptor], associate the given
 * property key (another atom) and property value.  This is a destructive
 * operation.
 */
@Suppress("unused")
object P_AtomSetFieldBound : Primitive(
	2, CanInline, HasSideEffect, WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val (atom, typeBound) = interpreter.argsBuffer
		if (atom.isAtomSpecial)
		{
			return interpreter.primitiveFailure(E_SPECIAL_ATOM)
		}
		if (atom
			.getAtomProperty(SpecialAtom.EXPLICIT_SUBCLASSING_KEY.atom)
			.notNil)
		{
			// Not quite right, but it should get the idea across that this
			// field atom should not have a type bound set on it.
			return interpreter.primitiveFailure(E_PROPERTY_MAY_ONLY_BE_SET_ONCE)
		}
		if (atom.fieldAtomConstraint.notNil)
		{
			return interpreter.primitiveFailure(E_PROPERTY_MAY_ONLY_BE_SET_ONCE)
		}
		atom.fieldAtomConstraint = typeBound

		// Statement summarization replaces the effect of running a series of
		// statements that only invoke 'safe" primitives with simple calls that
		// have the same system effect (installing methods, etc).  We can't
		// consider this primitive "safe" in that regard, since a subsequent
		// statement may use the affected field atom within an object type,
		// which will fail to deserialize if the field bound has not yet been
		// set.
		interpreter.availLoaderOrNull()?.statementCanBeSummarized(false)
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(ATOM(), anyMeta), TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_SPECIAL_ATOM,
				E_PROPERTY_MAY_ONLY_BE_SET_ONCE))
}
