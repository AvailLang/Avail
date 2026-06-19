/*
 * P_CopyMacros.kt
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

package avail.interpreter.primitive.methods

import avail.compiler.splitter.MessageSplitter.Companion.possibleErrors
import avail.descriptor.representation.A_Atom
import avail.descriptor.representation.A_Atom.Companion.bundleOrNil
import avail.descriptor.representation.A_Atom.Companion.isAtomSpecial
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Bundle
import avail.descriptor.representation.A_Bundle.Companion.macrosTuple
import avail.descriptor.representation.A_Module.Companion.allAncestors
import avail.descriptor.representation.A_Sendable.Companion.bodyBlock
import avail.descriptor.representation.A_Set.Companion.hasElement
import avail.descriptor.representation.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode.E_AMBIGUOUS_NAME
import avail.exceptions.AvailErrorCode.E_ATOM_ALREADY_EXISTS
import avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.exceptions.AvailErrorCode.E_REDEFINED_WITH_SAME_ARGUMENT_TYPES
import avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import avail.exceptions.AvailException
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanSuspend
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive2

/**
 * **Primitive:** Copy all macros from the first [A_Atom]'s [A_Bundle] into the
 * second [A_Atom]'s [A_Bundle].  Fail if the second bundle has an incompatible
 * signature, or if there are duplicates with the same signature.  Only copy the
 * macros that are visible in the current module, or were defined by the system.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_CopyMacros : Primitive2(CanSuspend, HasSideEffect)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val oldAtom: A_Atom = arg1
		val newAtom: A_Atom = arg2

		val loader = availLoaderOrNull()
		loader ?: return fail(E_LOADING_IS_OVER)
		if (!loader.phase.isExecuting)
		{
			return fail(E_CANNOT_DEFINE_DURING_COMPILATION)
		}
		if (oldAtom.isAtomSpecial || newAtom.isAtomSpecial)
		{
			return fail(E_SPECIAL_ATOM)
		}

		val oldBundle = oldAtom.bundleOrNil
		if (oldBundle.isNil)
			return nil

		val currentModule = loader.module
		return suspendInSafePointThen {
			try
			{
				for (macro in oldBundle.macrosTuple)
				{
					val definitionModule = macro.definitionModule()
					if (definitionModule.isNil
						|| definitionModule.equals(currentModule)
						|| currentModule.allAncestors.hasElement(
							definitionModule))
					{
						// The macro definition is within the ancestry (or a
						// system-defined macro), so it's safe to copy it over.
						loader.addMacroBody(
							newAtom,
							macro.bodyBlock(),
							macro.prefixFunctions(),
							false)
					}
				}
				succeed(nil)
			}
			catch (e: AvailException)
			{
				// MalformedMessageException
				// SignatureException
				// AmbiguousNameException
				fail(e.errorCode)
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				ATOM(),
				ATOM()),
			TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION,
				E_REDEFINED_WITH_SAME_ARGUMENT_TYPES,
				E_SPECIAL_ATOM,
				E_AMBIGUOUS_NAME,
				E_ATOM_ALREADY_EXISTS
			).setUnionCanDestroy(possibleErrors, true))
}
