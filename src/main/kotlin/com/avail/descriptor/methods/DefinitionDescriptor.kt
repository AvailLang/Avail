/*
 * DefinitionDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.methods

import com.avail.annotations.HideFieldJustForPrinting
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.methods.A_Method.Companion.numArgs
import com.avail.descriptor.methods.A_Sendable.Companion.bodySignature
import com.avail.descriptor.methods.DefinitionDescriptor.ObjectSlots.DEFINITION_METHOD
import com.avail.descriptor.methods.DefinitionDescriptor.ObjectSlots.MODULE
import com.avail.descriptor.methods.DefinitionDescriptor.ObjectSlots.STYLERS
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.module.A_Module.Companion.moduleName
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.ListPhraseTypeDescriptor.Companion.createListPhraseType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.mappingElementTypes
import com.avail.descriptor.types.TypeTag
import com.avail.descriptor.variables.A_Variable
import com.avail.serialization.SerializerOperation

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
		MODULE,

		/**
		 * The [A_Set] of [A_Styler]s that have been added to this definition.
		 * At most one may be added to each definition per module.
		 *
		 * Styling only happens when a top-level statement of a module has been
		 * unambiguously compiled.  Child phrases are processed before their
		 * parent.
		 *
		 * The function accepts three arguments:
		 *   1. The send phrase, which is possibly the original phrase of a
		 *      macro substitution.
		 *   2. An [A_Variable] containing an [A_Map] from [A_Phrase] to a
		 *      style, and
		 *   3. Another [A_Variable] containing a map from [A_Token] to style.
		 *
		 * The maps will already have been populated by running the styling
		 * function for each of the children (perhaps running all children in
		 * parallel).  These variables should be updated to reflect how the
		 * invocation of this bundle should be presented.
		 *
		 * These maps will later be used to produce a linear sequence of styled
		 * substrings, suitable for passing to an IDE or other technology
		 * capable of presenting styled text.
		 *
		 * The linearization of the fully populated style information proceeds
		 * by scanning the map from phrase to style, and for each phrase adding
		 * an entry to the map from token to style for each of the phrase's
		 * static tokens.  If there is already an entry for some static token,
		 * the map is not updated for that token.  Afterward, the token map is
		 * sorted, and used to partition the module text.
		 *
		 * The resulting tuple of ranges and styles (perhaps with line numbers)
		 * might even be accessed with a binary search, to deliver a windowed
		 * view into the styled text.  This allows enormous files to be
		 * presented in an IDE without having to transfer and decode all of the
		 * styling information for the entire file.
		 */
		@HideFieldJustForPrinting
		STYLERS
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = e === STYLERS

	abstract override fun o_BodySignature(self: AvailObject): A_Type

	override fun o_DefinitionMethod(self: AvailObject): A_Method =
		self.slot(DEFINITION_METHOD)

	override fun o_DefinitionModule(self: AvailObject): A_Module =
		self.slot(MODULE)

	override fun o_DefinitionModuleName(self: AvailObject): A_String =
		self.slot(MODULE).run {
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
			== self.slot(DEFINITION_METHOD).numArgs)
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

	override fun o_UpdateStylers (
		self: AvailObject,
		updater: A_Set.() -> A_Set)
	{
		self.atomicUpdateSlot(STYLERS, 1, updater)
	}

	override fun o_Stylers(self: AvailObject): A_Set =
		self.volatileSlot(STYLERS)

	companion object
	{
		/** The fake module name to use for built-in methods. */
		val builtInNoModuleName: A_String =
			stringFrom("(built-in)").makeShared()
	}
}
