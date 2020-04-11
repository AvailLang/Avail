/*
 * VariableSharedGlobalDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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
package com.avail.descriptor.variables

import com.avail.AvailRuntimeSupport
import com.avail.annotations.AvailMethod
import com.avail.annotations.HideFieldInDebugger
import com.avail.descriptor.A_Module
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.NilDescriptor
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.TypeTag
import com.avail.descriptor.types.VariableTypeDescriptor
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.VariableGetException
import com.avail.exceptions.VariableSetException
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.serialization.SerializerOperation
import java.util.*

/**
 * My [object instances][AvailObject] are [shared][Mutability.SHARED] variables
 * that are acting as module variables or module constants.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @see VariableDescriptor
 *
 * @property writeOnce
 *   A descriptor field to indicate whether the instances (variables) can only
 *   be written to once.
 *
 * @constructor
 * Construct a new `VariableSharedGlobalDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param writeOnce
 *   Whether the variable can only be assigned once.  This is only intended to
 *   be used to implement module constants
 */
class VariableSharedGlobalDescriptor  protected constructor(
	mutability: Mutability?, val writeOnce: Boolean)
	: VariableSharedDescriptor(
	mutability,
	TypeTag.VARIABLE_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java)
{

	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the hash, but the upper 32 can be used
		 * by subclasses.
		 */
		HASH_AND_MORE;

		companion object
		{
			/**
			 * A slot to hold the hash value.  Must be computed when (or before)
			 * making a variable shared.
			 */
			@HideFieldInDebugger
			@JvmStatic
			val HASH_ALWAYS_SET = BitField(HASH_AND_MORE, 0, 32)

			/**
			 * A flag indicating whether this variable was initialized to a value
			 * that was produced by a pure computation, specifically the kind of
			 * computation that does not disqualify [LoadingEffect]s set being recorded
			 * in place of top level statements.
			 */
			@JvmStatic
			val VALUE_IS_STABLE = BitField(HASH_AND_MORE, 32, 1)

			init
			{
				assert(VariableSharedDescriptor.IntegerSlots.HASH_AND_MORE.ordinal
					       == HASH_AND_MORE.ordinal)
				assert(VariableSharedDescriptor.IntegerSlots.Companion.HASH_ALWAYS_SET
					       .isSamePlaceAs(HASH_ALWAYS_SET))
			}
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [contents][AvailObject] of the [variable][VariableDescriptor].
		 */
		VALUE,

		/**
		 * The [kind][AvailObject] of the [variable][VariableDescriptor].  Note
		 * that this is always a [variable type][VariableTypeDescriptor].
		 */
		KIND,

		/**
		 * A [raw pojo][RawPojoDescriptor] that wraps a [map][Map] set arbitrary
		 * [Avail values][AvailObject] to [writer
		 * reactors][VariableAccessReactor] that respond to writes of the
		 * [variable][VariableDescriptor].
		 */
		WRITE_REACTORS,

		/**
		 * A [raw pojo][RawPojoDescriptor] holding a weak set (implemented as
		 * the [key set][Map.keySet] of a [WeakHashMap]) of [L2Chunk]s that
		 * depend on the membership of this method.  A change to the membership
		 * will invalidate all such chunks.  This field holds the
		 * [nil][NilDescriptor.nil] object initially.
		 */
		DEPENDENT_CHUNKS_WEAK_SET_POJO,

		/**
		 * The [module][A_Module] in which this variable is defined.
		 */
		MODULE,

		/**
		 * A [string][A_String] naming this variable or constant within its
		 * defining module.
		 */
		GLOBAL_NAME;

		companion object
		{
			init
			{
				assert(VariableSharedDescriptor.ObjectSlots.VALUE.ordinal
					== VALUE.ordinal)
				assert(VariableSharedDescriptor.ObjectSlots.KIND.ordinal
					== KIND.ordinal)
				assert(VariableSharedDescriptor.ObjectSlots.WRITE_REACTORS.ordinal
					== WRITE_REACTORS.ordinal)
				assert(VariableSharedDescriptor.ObjectSlots.DEPENDENT_CHUNKS_WEAK_SET_POJO.ordinal
					== DEPENDENT_CHUNKS_WEAK_SET_POJO.ordinal)
			}
		}
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum): Boolean =
			(super.allowsImmutableToMutableReferenceInField(e)
		        || e === ObjectSlots.VALUE
			    || e === ObjectSlots.WRITE_REACTORS
			    || e === ObjectSlots.DEPENDENT_CHUNKS_WEAK_SET_POJO
			 || e === IntegerSlots.HASH_AND_MORE) // only for flags.

	@AvailMethod
	override fun o_GlobalModule(`object`: AvailObject): A_Module =
		`object`.slot(ObjectSlots.MODULE)

	@AvailMethod
	override fun o_GlobalName(`object`: AvailObject): A_String =
		`object`.slot(ObjectSlots.GLOBAL_NAME)

	@AvailMethod
	@Throws(VariableSetException::class)
	override fun o_SetValue(`object`: AvailObject, newValue: A_BasicObject)
	{
		synchronized(`object`) {
			if (writeOnce && `object`.hasValue())
			{
				throw VariableSetException(
					AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
			}
			bypass_VariableDescriptor_SetValue(`object`, newValue.makeShared())
		}
		recordWriteToSharedVariable()
	}

	@AvailMethod
	override fun o_SetValueNoCheck(
		`object`: AvailObject, newValue: A_BasicObject)
	{
		synchronized(`object`) {
			if (writeOnce && `object`.hasValue())
			{
				throw VariableSetException(
					AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
			}
			bypass_VariableDescriptor_SetValueNoCheck(
				`object`, newValue.makeShared())
		}
		VariableSharedDescriptor.Companion.recordWriteToSharedVariable()
	}

	@AvailMethod
	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_GetAndSetValue(
		`object`: AvailObject, newValue: A_BasicObject): AvailObject
	{
		if (writeOnce)
		{
			throw VariableSetException(
				AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
		}
		return super.o_GetAndSetValue(`object`, newValue)
	}

	@AvailMethod
	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_CompareAndSwapValues(
		`object`: AvailObject,
		reference: A_BasicObject,
		newValue: A_BasicObject): Boolean
	{
		if (writeOnce)
		{
			throw VariableSetException(
				AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
		}
		return super.o_CompareAndSwapValues(`object`, reference, newValue)
	}

	@AvailMethod
	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_FetchAndAddValue(
		`object`: AvailObject, addend: A_Number): A_Number
	{
		if (writeOnce)
		{
			throw VariableSetException(
				AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
		}
		return super.o_FetchAndAddValue(`object`, addend)
	}

	@AvailMethod
	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_AtomicAddToMap(
		`object`: AvailObject,
		key: A_BasicObject,
		value: A_BasicObject)
	{
		if (writeOnce)
		{
			throw VariableSetException(
				AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
		}
		super.o_AtomicAddToMap(`object`, key, value)
	}

	@AvailMethod
	override fun o_ClearValue(`object`: AvailObject)
	{
		if (writeOnce)
		{
			throw VariableSetException(
				AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
		}
		super.o_ClearValue(`object`)
	}

	@AvailMethod
	override fun o_IsGlobal(`object`: AvailObject): Boolean = true

	@AvailMethod
	override fun o_IsInitializedWriteOnceVariable(`object`: AvailObject)
		: Boolean = writeOnce

	@AvailMethod
	override fun o_ValueWasStablyComputed(
		`object`: AvailObject, wasStablyComputed: Boolean)
	{
		// Only meaningful for write-once variables.
		assert(writeOnce)
		`object`.setSlot(
			IntegerSlots.VALUE_IS_STABLE, if (wasStablyComputed) 1 else 0)
	}

	@AvailMethod
	override fun o_ValueWasStablyComputed(
		`object`: AvailObject): Boolean =
			// Can only be set for write-once variables.
			`object`.slot(IntegerSlots.VALUE_IS_STABLE) != 0

	@AvailMethod
	override fun o_SerializerOperation(
		`object`: AvailObject): SerializerOperation =
			SerializerOperation.GLOBAL_VARIABLE

	companion object
	{
		/**
		 * Create a write-once, shared variable. This method should only be used
		 * to create module constants, and *maybe* eventually local constants.
		 * It should *not* be used for converting existing variables to be
		 * shared.
		 *
		 * @param variableType
		 *   The [variable type][VariableTypeDescriptor].
		 * @param module
		 *  The [A_Module] that this global is being defined in.
		 * @param name
		 *   The name of the global.  This is captured by the actual variable to
		 *   make it easier to quickly and accurately reproduce the effect of
		 *   loading the module.
		 * @param writeOnce
		 *   Whether the variable is to be written to exactly once.
		 * @return
		 *   The new shared variable.
		 */
		fun createGlobal(
			variableType: A_Type?,
			module: A_Module,
			name: A_String,
			writeOnce: Boolean): AvailObject
		{
			val result = mutableInitial.create()
			result.setSlot(ObjectSlots.KIND, variableType!!)
			result.setSlot(
				IntegerSlots.HASH_ALWAYS_SET, AvailRuntimeSupport.nextHash())
			result.setSlot(ObjectSlots.VALUE, NilDescriptor.nil)
			result.setSlot(ObjectSlots.WRITE_REACTORS, NilDescriptor.nil)
			result.setSlot(
				ObjectSlots.DEPENDENT_CHUNKS_WEAK_SET_POJO, NilDescriptor.nil)
			result.setSlot(ObjectSlots.MODULE, module.makeShared())
			result.setSlot(ObjectSlots.GLOBAL_NAME, name.makeShared())
			result.setDescriptor(if (writeOnce) sharedWriteOnce else shared)
			return result
		}

		/**
		 * The mutable [VariableSharedGlobalDescriptor]. Exists only to support
		 * creation.
		 */
		private val mutableInitial =
			VariableSharedGlobalDescriptor(Mutability.MUTABLE, false)

		/** The shared [VariableSharedGlobalDescriptor].  */
		val shared =
			VariableSharedGlobalDescriptor(Mutability.SHARED, false)

		/**
		 * The shared [VariableSharedGlobalDescriptor] which is used for
		 * write-once variables.
		 */
		private val sharedWriteOnce =
			VariableSharedGlobalDescriptor(Mutability.SHARED, true)
	}

}