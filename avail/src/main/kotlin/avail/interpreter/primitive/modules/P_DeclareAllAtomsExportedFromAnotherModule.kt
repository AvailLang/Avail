/*
 * P_DeclareAllAtomsExportedFromAnotherModule.kt
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

package avail.interpreter.primitive.modules

import avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.module.A_Module.Companion.addImportedNames
import avail.descriptor.module.A_Module.Companion.addPrivateNames
import avail.descriptor.module.A_Module.Companion.exportedNames
import avail.descriptor.module.ModuleDescriptor.ObjectSlots
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.EnumerationTypeDescriptor
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.Primitive.Flag.Private
import avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** This private primitive is used to ensure that a module can
 * deserialize correctly. It's given a set of fully qualified module names, and
 * each such module should have all of its exported names included in the
 * current module's [public&nbsp;names][ObjectSlots.IMPORTED_NAMES] or
 * [private&nbsp;names][ObjectSlots.PRIVATE_NAMES], depending on the value of
 * the supplied [boolean][EnumerationTypeDescriptor.booleanType]
 * ([true][AtomDescriptor.trueObject] for public,
 * [false][AtomDescriptor.falseObject] for private).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_DeclareAllAtomsExportedFromAnotherModule : Primitive(
	2, CannotFail, Private, HasSideEffect, WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val (importedModuleNames, isPublic) = interpreter.argsBuffer
		val module = interpreter.module()
		assert(module.notNil)
		val runtime = interpreter.runtime
		val sets = importedModuleNames.map { importedModuleName ->
			val importedModule = runtime.moduleAt(importedModuleName)
			importedModule.exportedNames
		}.sortedByDescending { it.setSize }
		val union = sets.fold(emptySet) { union, nextSet ->
			union.setUnionCanDestroy(nextSet, true)
		}
		when (isPublic.extractBoolean) {
			true -> module.addImportedNames(union.makeShared())
			else -> module.addPrivateNames(union.makeShared())
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				setTypeForSizesContentType(
					naturalNumbers,
					Types.ANY.o  /* stringType - weakened for performance. */),
				booleanType),
			TOP.o)
}
