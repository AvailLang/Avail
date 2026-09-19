/*
 * P_ExtractTagOrdinal.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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
package avail.interpreter.primitive.general

import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instanceTag
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i31
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.TypeTag
import avail.descriptor.types.TypeTag.Companion.restrictionForTagRestriction
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.dispatch.L2_EXTRACT_TAG_ORDINAL
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.Private
import avail.interpreter.primitive.Primitive1
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticValue

/**
 * **Primitive:** Answer the [ordinal][Enum.ordinal] of the [TypeTag] of the
 * argument.
 *
 * This primitive is [Private], and is never made available to Avail code.  It
 * exists so that an extracted tag can be named by an ordinary
 * [L2SemanticPrimitiveInvocation], which relates it to the value it was
 * extracted from structurally, rather than through a relationship that the
 * [L2ValueManifest] has to maintain by hand.  The instruction that actually
 * computes it is [L2_EXTRACT_TAG_ORDINAL].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_ExtractTagOrdinal
	: Primitive1(Private, CannotFail, CanFold, CanInline)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject = fromInt(arg1.typeTag.ordinal)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type = argumentTypes[0].run {
		when
		{
			// There is no value to have a tag, so there is no ordinal.
			isBottom -> bottom
			else -> instanceTag.tagRangeType
		}
	}

	override fun propagateManifestRestrictions(
		arguments: List<L2SemanticValue>,
		manifest: L2ValueManifest,
		restriction: TypeRestriction)
	{
		// Knowing the tag says a great deal about the value it came from.
		manifest.equivalentSemanticValue(arguments[0])?.let { taggedValue ->
			manifest.updateRestriction(taggedValue) {
				restrictionForTagRestriction(restriction)
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(ANY()), i31)

	override val canDestroyArguments get() = false
}
