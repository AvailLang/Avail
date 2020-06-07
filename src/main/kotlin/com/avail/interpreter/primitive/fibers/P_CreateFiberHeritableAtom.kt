/*
 * P_CreateFiberHeritableAtom.kt
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

package com.avail.interpreter.primitive.fibers

import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.setAtomProperty
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomDescriptor.Companion.createAtom
import com.avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.HERITABLE_KEY
import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.module.ModuleDescriptor.Companion.currentModule
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.TupleTypeDescriptor.stringType
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.AvailErrorCode.E_AMBIGUOUS_NAME
import com.avail.exceptions.AvailErrorCode.E_ATOM_ALREADY_EXISTS
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter
import com.avail.utility.MutableOrNull

/**
 * **Primitive:** Create a new [atom][AtomDescriptor] with the given name that
 * represents a [heritable][HERITABLE_KEY] [fiber][FiberDescriptor]
 * variable.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_CreateFiberHeritableAtom : Primitive(1, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val name = interpreter.argument(0)
		val module = currentModule()
		val trueName = MutableOrNull<A_Atom>()
		val errorCode = MutableOrNull<AvailErrorCode>()
		if (!module.equalsNil())
		{
			module.lock {
				val trueNames = module.trueNamesForStringName(name)
				when (trueNames.setSize()) {
					0 -> {
						val newName = createAtom(name, module)
						newName.setAtomProperty(
							HERITABLE_KEY.atom, trueObject())
						module.addPrivateName(newName)
						trueName.value = newName
					}
					1 -> errorCode.value = E_ATOM_ALREADY_EXISTS
					else -> errorCode.value = E_AMBIGUOUS_NAME
				}
			}
		}
		else
		{
			val newName = createAtom(name, nil)
			newName.setAtomProperty(HERITABLE_KEY.atom, trueObject())
			trueName.value = newName
		}
		return if (errorCode.value !== null)
		{
			interpreter.primitiveFailure(errorCode.value!!)
		}
		else interpreter.primitiveSuccess(trueName.value().makeShared())
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(stringType()), ATOM.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_ATOM_ALREADY_EXISTS, E_AMBIGUOUS_NAME))
}
