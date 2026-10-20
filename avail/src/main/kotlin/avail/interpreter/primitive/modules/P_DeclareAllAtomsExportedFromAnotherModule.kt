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

import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.representation.A_Atom.Companion.extractBoolean
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Module.Companion.addImportedNames
import avail.descriptor.representation.A_Module.Companion.addPrivateNames
import avail.descriptor.representation.A_Module.Companion.exportedNames
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive.Flag.Private
import avail.interpreter.primitive.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.primitive.Primitive2

/**
 * **Primitive:** This private primitive is used to ensure that a module can
 * deserialize correctly. It's given a set of fully qualified module names, and
 * each such module should have all of its exported names included in the
 * current module's imported names or private names., depending on the value of
 * the supplied [booleanType] ([trueObject] for public, [falseObject] for
 * private).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_DeclareAllAtomsExportedFromAnotherModule : Primitive2(
	CannotFail, Private, HasSideEffect, WritesToHiddenGlobalState)
{
	override fun attempt2(
		interpreter: Interpreter,
		importedModuleNames: AvailObject,
		isPublic: AvailObject
	): A_BasicObject? = interpreter.run {
		val module = module()
		assert(module.notNil)
		val union = buildSet {
			importedModuleNames.forEach { importedModuleName ->
				addAll(runtime.moduleAt(importedModuleName).exportedNames)
			}
		}
		union.forEach(AvailObject::makeShared)
		when (isPublic.extractBoolean) {
			true -> module.addImportedNames(union)
			else -> module.addPrivateNames(union)
		}
		return nil
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				setTypeForSizesContentType(
					naturalNumbers,
					Types.ANY()  /* stringType - weakened for performance. */),
				booleanType),
			TOP())
}
