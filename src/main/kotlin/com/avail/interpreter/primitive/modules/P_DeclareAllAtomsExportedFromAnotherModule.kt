/*
 * P_DeclareAllAtomsExportedFromAnotherModule.kt
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

package com.avail.interpreter.primitive.modules

import com.avail.descriptor.ModuleDescriptor.ObjectSlots
import com.avail.descriptor.ModuleDescriptor.currentModule
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.EnumerationTypeDescriptor
import com.avail.descriptor.types.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.types.SetTypeDescriptor.setTypeForSizesContentType
import com.avail.descriptor.types.TupleTypeDescriptor.stringType
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

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
object P_DeclareAllAtomsExportedFromAnotherModule : Primitive(
	2, CannotFail, Private, HasSideEffect, WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val (importedModuleNames, isPublic) = interpreter.argsBuffer
		val module = currentModule()
		assert(!module.equalsNil())
		val runtime = interpreter.runtime()
		importedModuleNames.forEach { importedModuleName ->
			val importedModule = runtime.moduleAt(importedModuleName)
			val names = importedModule.exportedNames()
			when(isPublic.extractBoolean()) {
				true -> module.addImportedNames(names)
				else -> module.addPrivateNames(names)
			}
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				setTypeForSizesContentType(
					naturalNumbers(),
					stringType()),
				booleanType()),
			TOP.o())
}
