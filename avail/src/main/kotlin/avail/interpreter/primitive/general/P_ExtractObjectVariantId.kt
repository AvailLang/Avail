/*
 * P_ExtractObjectVariantId.kt
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
import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.objects.ObjectLayoutVariant.Companion.variantFromId
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.objectVariant
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instances
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i31
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.dispatch.L2_EXTRACT_OBJECT_VARIANT_ID
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.Private
import avail.interpreter.primitive.Primitive1
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticValue

/**
 * **Primitive:** Answer the [variantId][ObjectLayoutVariant.variantId] of the
 * [ObjectLayoutVariant] of the [object][ObjectDescriptor] argument.
 *
 * This primitive is [Private], and is never made available to Avail code.  It
 * exists so that an extracted variant id can be named by an ordinary
 * [L2SemanticPrimitiveInvocation], which relates it to the object it was
 * extracted from structurally, rather than through a relationship that the
 * [L2ValueManifest] has to maintain by hand.  The instruction that actually
 * computes it is [L2_EXTRACT_OBJECT_VARIANT_ID].
 *
 * Note that an object and an object *type* have separate primitives, unlike the
 * single semantic value that used to name both; see
 * [P_ExtractObjectTypeVariantId].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_ExtractObjectVariantId
	: Primitive1(Private, CannotFail, CanFold, CanInline)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject = fromInt(arg1.objectVariant.variantId)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type = argumentTypes[0].run {
		when
		{
			isBottom -> bottom
			// The exact objects are known, so their variants are too.  An
			// object type does *not* determine the variant of its instances,
			// since a subvariant's objects are instances of it as well.
			isEnumeration && !isInstanceMeta -> enumerationWith(
				setFromCollection(
					instances.map { fromInt(it.objectVariant.variantId) }))
			else -> i31
		}
	}

	override fun propagateManifestRestrictions(
		arguments: List<L2SemanticValue>,
		manifest: L2ValueManifest,
		restriction: TypeRestriction)
	{
		// Only a variant narrowed all the way to one id says anything about the
		// object it came from.
		val variantId = restriction.constantOrNull ?: return
		val variant = variantFromId(variantId.extractInt) ?: return
		manifest.equivalentSemanticValue(arguments[0])?.let { objectValue ->
			manifest.updateRestriction(objectValue) {
				restrictionForType(variant.mostGeneralObjectType)
					.intersectionWithObjectVariant(variant)
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralObjectType), i31)

	override val canDestroyArguments get() = false
}
