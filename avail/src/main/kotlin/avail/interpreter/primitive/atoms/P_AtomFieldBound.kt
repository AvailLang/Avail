/*
 * P_AtomFieldBound.kt
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
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.exceptions.AvailErrorCode.E_KEY_NOT_FOUND
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Extract the type previously set for this field atom via
 * [P_AtomSetFieldBound], or fail if there is none.  Note that the type is
 * permanent once set, so L2 can make use of the type as a constant if it's
 * found to be already set.  If not set, it will fail until it has bene set,
 * which actually makes it foldable ([CanFold]).
 */
@Suppress("unused")
object P_AtomFieldBound : Primitive(
	1, CanInline, CanFold, WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val atom = interpreter.argument(0)
		if (atom.getAtomProperty(EXPLICIT_SUBCLASSING_KEY.atom).notNil)
		{
			// It's an explicit subclassing atom, so its type bound is just the
			// same atom's type.
			return interpreter.primitiveSuccess(instanceType(atom))
		}
		val typeBound = atom.fieldAtomConstraint
		if (typeBound.isNil)
		{
			return interpreter.primitiveFailure(E_KEY_NOT_FOUND)
		}
		assert(typeBound.isSubtypeOf(ANY()))
		return interpreter.primitiveSuccess(typeBound)
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type
	{
		val atomType = argumentTypes[0]
		if (!atomType.instanceCount.equalsInt(1)) return anyMeta
		val atom = atomType.instance
		if (atom.isAtomSpecial) return anyMeta
		val typeBound = atom.getAtomProperty(EXPLICIT_SUBCLASSING_KEY.atom)
		if (typeBound.isNil) return anyMeta
		assert(typeBound.isSubtypeOf(ANY()))
		return typeBound
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(ATOM()),
			anyMeta)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_KEY_NOT_FOUND))
}
