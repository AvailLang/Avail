/*
 * P_BundleSignatures.kt
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
package com.avail.interpreter.primitive.methods

import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.bundles.MessageBundleDescriptor
import com.avail.descriptor.methods.DefinitionDescriptor
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.types.SetTypeDescriptor.setTypeForSizesContentType
import com.avail.descriptor.types.TypeDescriptor.Types.DEFINITION
import com.avail.descriptor.types.TypeDescriptor.Types.MESSAGE_BUNDLE
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail

/**
 * **Primitive:** Answer a [set][SetDescriptor] of all currently defined
 * [definitions][DefinitionDescriptor] for the [true message
 * name][AtomDescriptor] represented by [bundle][MessageBundleDescriptor]. This
 * includes abstract signatures and forward signatures.
 */
@Suppress("unused")
object P_BundleSignatures : Primitive(1, CannotFail, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val bundle = interpreter.argument(0)
		val method = bundle.bundleMethod()
		val definitions = method.definitionsTuple().asSet()
		definitions.makeImmutable()
		return interpreter.primitiveSuccess(definitions)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				MESSAGE_BUNDLE.o()),
			setTypeForSizesContentType(wholeNumbers(), DEFINITION.o()))
}
