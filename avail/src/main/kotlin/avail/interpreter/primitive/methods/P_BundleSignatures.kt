/*
 * P_BundleSignatures.kt
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

import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.MessageBundleDescriptor
import avail.descriptor.methods.A_Method.Companion.definitionsTuple
import avail.descriptor.methods.DefinitionDescriptor
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.tuples.A_Tuple.Companion.asSet
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.DEFINITION
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.MESSAGE_BUNDLE
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Answer a [set][SetDescriptor] of all currently defined
 * [definitions][DefinitionDescriptor] for the
 * [true&#32;message&#32;name][AtomDescriptor] represented by
 * [bundle][MessageBundleDescriptor]. This includes abstract signatures and
 * forward signatures.
 */
@Suppress("unused")
object P_BundleSignatures : Primitive(1, CannotFail, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val bundle = interpreter.argument(0)
		val method = bundle.bundleMethod
		val definitions = method.definitionsTuple.asSet
		definitions.makeImmutable()
		return interpreter.primitiveSuccess(definitions)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				MESSAGE_BUNDLE.o),
			setTypeForSizesContentType(wholeNumbers, DEFINITION.o))
}
