/*
 * VariableSharedGlobalDescriptor.kt
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
package com.avail.descriptor.variables

import com.avail.AvailRuntimeSupport
import com.avail.annotations.HideFieldInDebugger
import com.avail.annotations.HideFieldJustForPrinting
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.writeType
import com.avail.descriptor.types.TypeTag
import com.avail.descriptor.types.VariableTypeDescriptor
import com.avail.descriptor.variables.VariableSharedGlobalDescriptor.IntegerSlots.Companion.HASH_ALWAYS_SET
import com.avail.descriptor.variables.VariableSharedGlobalDescriptor.IntegerSlots.Companion.VALUE_IS_STABLE
import com.avail.descriptor.variables.VariableSharedGlobalDescriptor.IntegerSlots.HASH_AND_MORE
import com.avail.descriptor.variables.VariableSharedGlobalDescriptor.ObjectSlots.DEPENDENT_CHUNKS_WEAK_SET_POJO
import com.avail.descriptor.variables.VariableSharedGlobalDescriptor.ObjectSlots.GLOBAL_NAME
import com.avail.descriptor.variables.VariableSharedGlobalDescriptor.ObjectSlots.KIND
import com.avail.descriptor.variables.VariableSharedGlobalDescriptor.ObjectSlots.MODULE
import com.avail.descriptor.variables.VariableSharedGlobalDescriptor.ObjectSlots.VALUE
import com.avail.descriptor.variables.VariableSharedGlobalDescriptor.ObjectSlots.WRITE_REACTORS
import com.avail.exceptions.AvailErrorCode.E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE
import com.avail.exceptions.AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE
import com.avail.exceptions.VariableGetException
import com.avail.exceptions.VariableSetException
import com.avail.interpreter.effects.LoadingEffect
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.serialization.SerializerOperation
import java.util.WeakHashMap

/**
 * My [object&#32;instances][AvailObject] are [shared][Mutability.SHARED] variables
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
 *   Construct a new `VariableSharedGlobalDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param writeOnce
 *   Whether the variable can only be assigned once.  This is only intended to
 *   be used to implement module constants
 */
class VariableSharedGlobalDescriptor private constructor(
	mutability: Mutability,
	private val writeOnce: Boolean
) : VariableSharedDescriptor(
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
			val HASH_ALWAYS_SET = BitField(HASH_AND_MORE, 0, 32)

			/**
			 * A flag indicating whether this variable was initialized to a
			 * value that was produced by a pure computation, specifically the
			 * kind of computation that does not disqualify [LoadingEffect]s set
			 * being recorded in place of top level statements.
			 */
			val VALUE_IS_STABLE = BitField(HASH_AND_MORE, 32, 1)

			init
			{
				assert(VariableSharedDescriptor.IntegerSlots
			       .HASH_AND_MORE.ordinal == HASH_AND_MORE.ordinal)
				assert(VariableSharedDescriptor.IntegerSlots.HASH_ALWAYS_SET
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
		 * that this is always a [variable&#32;type][VariableTypeDescriptor].
		 */
		KIND,

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] that wraps a [map][Map] set
		 * arbitrary [Avail&#32;values][AvailObject] to
		 * [writer&#32;reactors][VariableDescriptor.VariableAccessReactor] that
		 * respond to writes of the [variable][VariableDescriptor].
		 */
		@HideFieldJustForPrinting
		WRITE_REACTORS,

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] holding a weak set (implemented
		 * as the [key&#32;set][Map.keys] of a [WeakHashMap]) of [L2Chunk]s that
		 * depend on the membership of this method.  A change to the membership
		 * will invalidate all such chunks.  This field holds the
		 * [nil][NilDescriptor.nil] object initially.
		 */
		@HideFieldJustForPrinting
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
				assert(VariableSharedDescriptor.ObjectSlots
					.WRITE_REACTORS.ordinal
						== WRITE_REACTORS.ordinal)
				assert(VariableSharedDescriptor.ObjectSlots
			       .DEPENDENT_CHUNKS_WEAK_SET_POJO.ordinal
						== DEPENDENT_CHUNKS_WEAK_SET_POJO.ordinal)
			}
		}
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	): Boolean = (super.allowsImmutableToMutableReferenceInField(e)
		|| e === VALUE
		|| e === WRITE_REACTORS
		|| e === DEPENDENT_CHUNKS_WEAK_SET_POJO
		|| e === HASH_AND_MORE) // only for flags.

	override fun o_GlobalModule(self: AvailObject): A_Module =
		self.slot(MODULE)

	override fun o_GlobalName(self: AvailObject): A_String =
		self.slot(GLOBAL_NAME)

	@Throws(VariableSetException::class)
	override fun o_SetValue(self: AvailObject, newValue: A_BasicObject)
	{
		val outerKind = self.slot(KIND)
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw VariableSetException(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		o_SetValueNoCheck(self, newValue)
	}

	override fun o_SetValueNoCheck(self: AvailObject, newValue: A_BasicObject)
	{
		if (!writeOnce)
		{
			super.o_SetValueNoCheck(self, newValue)
			return
		}
		assert(!newValue.equalsNil())
		try
		{
			handleVariableWriteTracing(self)
			if (!self.compareAndSetVolatileSlot(
					VALUE, nil, newValue.makeShared()))
			{
				// The variable is writeOnce, but was not nil.
				throw VariableSetException(
					E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
			}
		}
		finally
		{
			recordWriteToSharedVariable()
		}
	}

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_GetAndSetValue(
		self: AvailObject, newValue: A_BasicObject): AvailObject
	{
		if (writeOnce)
		{
			throw VariableSetException(
				E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
		}
		return super.o_GetAndSetValue(self, newValue)
	}

	@Throws(VariableSetException::class)
	override fun o_CompareAndSwapValuesNoCheck(
		self: AvailObject,
		reference: A_BasicObject,
		newValue: A_BasicObject): Boolean
	{
		if (writeOnce)
		{
			throw VariableSetException(
				E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
		}
		return super.o_CompareAndSwapValuesNoCheck(self, reference, newValue)
	}

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_FetchAndAddValue(
		self: AvailObject, addend: A_Number): A_Number
	{
		if (writeOnce)
		{
			throw VariableSetException(
				E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
		}
		return super.o_FetchAndAddValue(self, addend)
	}

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_AtomicAddToMap(
		self: AvailObject,
		key: A_BasicObject,
		value: A_BasicObject)
	{
		if (writeOnce)
		{
			throw VariableSetException(E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
		}
		super.o_AtomicAddToMap(self, key, value)
	}

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_AtomicRemoveFromMap(self: AvailObject, key: A_BasicObject)
	{
		if (writeOnce)
		{
			throw VariableSetException(E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
		}
		super.o_AtomicRemoveFromMap(self, key)
	}

	override fun o_ClearValue(self: AvailObject)
	{
		if (writeOnce)
		{
			throw VariableSetException(E_CANNOT_OVERWRITE_WRITE_ONCE_VARIABLE)
		}
		super.o_ClearValue(self)
	}

	override fun o_IsGlobal(self: AvailObject): Boolean = true

	override fun o_IsInitializedWriteOnceVariable(self: AvailObject) = writeOnce

	override fun o_SetValueWasStablyComputed(
		self: AvailObject, wasStablyComputed: Boolean)
	{
		// Only meaningful for write-once variables.
		assert(writeOnce)
		self.setSlot(VALUE_IS_STABLE, if (wasStablyComputed) 1 else 0)
	}

	override fun o_ValueWasStablyComputed(
		self: AvailObject): Boolean =
			// Can only be set for write-once variables.
			self.slot(VALUE_IS_STABLE) != 0

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
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
		 *   The [variable&#32;type][VariableTypeDescriptor].
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
			return mutableInitial.create {
				setSlot(KIND, variableType!!)
				setSlot(HASH_ALWAYS_SET, AvailRuntimeSupport.nextNonzeroHash())
				setSlot(VALUE, nil)
				setSlot(WRITE_REACTORS, nil)
				setSlot(DEPENDENT_CHUNKS_WEAK_SET_POJO, nil)
				setSlot(MODULE, module.makeShared())
				setSlot(GLOBAL_NAME, name.makeShared())
				setDescriptor(if (writeOnce) sharedWriteOnce else shared)
			}
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
