/*
 * VariablePlaceholderDescriptor.kt
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
package avail.descriptor.variables

import avail.AvailRuntimeSupport
import avail.annotations.HideFieldInDebugger
import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_RegisterDump
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.BitField
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.types.A_Type
import avail.descriptor.types.TypeTag
import avail.descriptor.types.VariableTypeDescriptor
import avail.descriptor.variables.A_Variable.Companion.placeholderVariableLocalIndex
import avail.descriptor.variables.VariablePlaceholderDescriptor.IntegerSlots.Companion.HASH
import avail.descriptor.variables.VariablePlaceholderDescriptor.IntegerSlots.Companion.LOCAL_INDEX
import avail.descriptor.variables.VariablePlaceholderDescriptor.ObjectSlots.KIND
import avail.exceptions.unsupported
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.optimizer.values.Frame
import java.util.IdentityHashMap

/**
 * My instances are placeholders for elided local variables in L2 code.  Each
 * placeholder represents a local variable that has not yet been created, but
 * whose initialization value is known.
 *
 * It knows the local number that identifies which local was elided.  The
 * information about which register holds the value with which to initialize the
 * variable (in the event of a continuation using this placeholder becoming
 * immutable or shared) is recorded in an [A_RegisterDump] which gets stored
 * inside the [A_Continuation] during reification.
 *
 * TODO: Make this be [Frame]-relative somehow.  Might not be necessary, since
 *  it has a usable identity.
 *
 * The type of the variable is also recorded within the instance, to ensure the
 * placeholder doesn't violate any [TypeRestriction].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new [VariablePlaceholderDescriptor]
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] to embed in the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no integer slots.
 */
open class VariablePlaceholderDescriptor protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?
) : Descriptor(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The local index and hash value.
		 */
		@HideFieldInDebugger
		LOCAL_INDEX_AND_HASH;

		companion object
		{
			/**
			 * The index of this local's slot within its [A_Continuation].
			 */
			val LOCAL_INDEX = BitField(LOCAL_INDEX_AND_HASH, 0, 32)

			/**
			 * A slot to hold the cached hash value.  Zero if not yet computed.
			 */
			val HASH = BitField(LOCAL_INDEX_AND_HASH, 32, 32)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [A_Type] of the placeholder variable.  Note that this is always a
		 * [variable&#32;type][VariableTypeDescriptor].
		 */
		KIND
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Unit>,
		indent: Int
	): Unit = with(builder) {
		append("Elided local #")
		append(self.placeholderVariableLocalIndex())
	}

	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean = another.traversed().sameAddressAs(self)

	override fun o_Hash(self: AvailObject): Int = self[HASH]

	override fun o_SerializerOperation(self: AvailObject) = unsupported

	override fun o_Kind(self: AvailObject): A_Type = self[KIND]

	override fun o_IsPlaceholderVariable(self: AvailObject): Boolean = true

	override fun o_PlaceholderVariableLocalIndex(
		self: AvailObject
	): Int = self[LOCAL_INDEX]

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object
	{
		/**
		 * Create a placeholder variable for the specified local index and
		 * variable type.
		 *
		 * @param variableType
		 *   The [variable&#32;type][VariableTypeDescriptor].
		 * @param localIndex
		 *   The index of the local in an [A_Continuation] in which the *real*
		 *   version of this local variable is to be created, if needed.
		 * @return
		 *   A new placeholder variable of the given type.
		 */
		fun newPlaceholder(
			variableType: A_Type,
			localIndex: Int
		): AvailObject = mutable.create {
			setSlot(KIND, variableType.makeShared())
			setSlot(LOCAL_INDEX, localIndex)
			setSlot(HASH, AvailRuntimeSupport.nextNonzeroHash())
		}

		/** The mutable [VariablePlaceholderDescriptor]. */
		private val mutable = VariablePlaceholderDescriptor(
			Mutability.MUTABLE,
			TypeTag.VARIABLE_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)

		/** The immutable [VariablePlaceholderDescriptor]. */
		private val immutable = VariablePlaceholderDescriptor(
			Mutability.IMMUTABLE,
			TypeTag.VARIABLE_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)

		/** The shared [VariablePlaceholderDescriptor]. */
		private val shared = VariablePlaceholderDescriptor(
			Mutability.SHARED,
			TypeTag.VARIABLE_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)
	}
}
