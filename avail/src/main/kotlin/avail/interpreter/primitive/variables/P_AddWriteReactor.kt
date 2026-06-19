/*
 * P_AddWriteReactor.kt
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

package avail.interpreter.primitive.variables

import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.representation.A_Atom.Companion.isAtomSpecial
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instance
import avail.descriptor.representation.A_Variable.Companion.addWriteReactor
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.variables.VariableDescriptor
import avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive3

/**
 * **Primitive:** Add a [write&#32;reactor][VariableAccessReactor] to the
 * specified [variable][VariableDescriptor]. The supplied [key][AtomDescriptor]
 * may be used subsequently to remove the write reactor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_AddWriteReactor : Primitive3(HasSideEffect)
{
	override fun Interpreter.attempt3(
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		val variable = arg1
		val key = arg2
		val reactorFunction = arg3
		// Forbid special atoms.
		if (key.isAtomSpecial)
		{
			return fail(E_SPECIAL_ATOM)
		}
		val sharedFunction = reactorFunction.makeShared()
		val writeReactor = VariableAccessReactor(sharedFunction)
		variable.addWriteReactor(key, writeReactor)
		return nil
	}

	/**
	 * The variable gets a reactor here.
	 */
	override fun mightMakeEscapedVariableShared(
		argumentTypes: List<A_Type>
	): Boolean = true

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralVariableType,
				ATOM(),
				functionType(
					emptyTuple,
					TOP())),
			TOP())

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
			val allSpecial = keyType.instance.all { it.isAtomSpecial }
			val noneSpecial = keyType.instance.none { it.isAtomSpecial }
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
