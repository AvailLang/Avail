/*
 * DefinitionDescriptor.kt
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
package avail.descriptor.methods

import avail.annotations.HideFieldJustForPrinting
import avail.descriptor.methods.A_Method.Companion.numArgs
import avail.descriptor.methods.A_Sendable.Companion.bodySignature
import avail.descriptor.methods.DefinitionDescriptor.ObjectSlots.DEFINITION_METHOD
import avail.descriptor.methods.DefinitionDescriptor.ObjectSlots.MODULE
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.ListPhraseTypeDescriptor.Companion.createListPhraseType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.TupleTypeDescriptor.Companion.mappingElementTypes
import avail.descriptor.types.TypeTag
import avail.serialization.SerializerOperation

/**
 * `DefinitionDescriptor` is an abstraction for things placed into a
 * [method][MethodDescriptor].  They can be:
 *
 *  * [abstract&#32;declarations][AbstractDefinitionDescriptor],
 *  * [forward&#32;declarations][ForwardDefinitionDescriptor],
 *  * [method&#32;definitions][MethodDefinitionDescriptor], or
 *  * [macro&#32;definitions][MacroDescriptor].
 *
 * @constructor
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's integer slots layout, or null if there are no integer slots.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class DefinitionDescriptor protected constructor(
	mutability: Mutability,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?
) : Descriptor(
	mutability,
	TypeTag.DEFINITION_TAG,
	objectSlotsEnumClass,
	integerSlotsEnumClass)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/** The [method][MethodDescriptor] in which this is a definition. */
		@HideFieldJustForPrinting
		DEFINITION_METHOD,

		/** The [module][ModuleDescriptor] in which this definition occurs. */
		@HideFieldJustForPrinting
		MODULE
	}

	abstract override fun o_BodySignature(self: AvailObject): A_Type

	override fun o_DefinitionMethod(self: AvailObject): A_Method =
		self[DEFINITION_METHOD]

	override fun o_DefinitionModule(self: AvailObject): A_Module =
		self[MODULE]

	override fun o_DefinitionModuleName(self: AvailObject): A_String =
		self[MODULE].run {
			if (isNil) builtInNoModuleName
			else moduleName
		}

	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		another.traversed().sameAddressAs(self)

	abstract override fun o_Hash(self: AvailObject): Int

	override fun o_IsAbstractDefinition(self: AvailObject) = false

	override fun o_IsForwardDefinition(self: AvailObject) = false

	override fun o_IsMethodDefinition(self: AvailObject) = false

	abstract override fun o_Kind(self: AvailObject): A_Type

	override fun o_ParsingSignature(self: AvailObject): A_Type
	{
		// Non-macro definitions have a signature derived from the
		// bodySignature.  We can safely make it a list phrase type.
		val argsTupleType = self.bodySignature().argsTupleType
		val sizes = argsTupleType.sizeRange
		assert(sizes.lowerBound.extractInt == sizes.upperBound.extractInt)
		assert(sizes.lowerBound.extractInt
			== self[DEFINITION_METHOD].numArgs)
		return createListPhraseType(
			PhraseKind.LIST_PHRASE,
			argsTupleType,
			mappingElementTypes(argsTupleType) {
				yieldType -> PhraseKind.EXPRESSION_PHRASE.create(yieldType)
			})
	}

	abstract override fun o_SerializerOperation(
		self: AvailObject
	): SerializerOperation

	companion object
	{
		/** The fake module name to use for built-in methods. */
		val builtInNoModuleName: A_String =
			stringFrom("(built-in)").makeShared()
	}
}
