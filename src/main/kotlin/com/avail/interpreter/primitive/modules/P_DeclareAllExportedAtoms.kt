/*
 * P_DeclareAllExportedAtoms.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_Type
import com.avail.descriptor.AtomDescriptor
import com.avail.descriptor.EnumerationTypeDescriptor
import com.avail.descriptor.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.ModuleDescriptor.ObjectSlots
import com.avail.descriptor.ModuleDescriptor.currentModule
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.Primitive.Flag.Private

/**
 * **Primitive:** This private primitive is used to ensure that
 * a module can deserialize correctly. It forces the given set of atoms to be
 * included in the current module's [ ][ObjectSlots.IMPORTED_NAMES] or [ ][ObjectSlots.PRIVATE_NAMES], depending on the
 * value of the supplied [ boolean][EnumerationTypeDescriptor.booleanType] ([true][AtomDescriptor.trueObject] for public, [ ][AtomDescriptor.falseObject] for private).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_DeclareAllExportedAtoms : Primitive(2, CannotFail, Private)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val names = interpreter.argument(0)
		val isPublic = interpreter.argument(1)
		val module = currentModule()
		assert(!module.equalsNil())
		if (isPublic.extractBoolean())
		{
			module.addImportedNames(names)
		}
		else
		{
			module.addPrivateNames(names)
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				setTypeForSizesContentType(
					wholeNumbers(),
					ATOM.o()),
				booleanType()),
			TOP.o())
	}

}