/*
 * VariableSharedDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

import com.avail.annotations.HideFieldInDebugger
import com.avail.annotations.HideFieldJustForPrinting
import com.avail.descriptor.atoms.A_Atom
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
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.TypeTag
import com.avail.descriptor.types.VariableTypeDescriptor
import com.avail.descriptor.variables.VariableSharedDescriptor.IntegerSlots.Companion.HASH_ALWAYS_SET
import com.avail.descriptor.variables.VariableSharedDescriptor.ObjectSlots.DEPENDENT_CHUNKS_WEAK_SET_POJO
import com.avail.descriptor.variables.VariableSharedDescriptor.ObjectSlots.KIND
import com.avail.descriptor.variables.VariableSharedDescriptor.ObjectSlots.VALUE
import com.avail.descriptor.variables.VariableSharedDescriptor.ObjectSlots.WRITE_REACTORS
import com.avail.exceptions.AvailException
import com.avail.exceptions.VariableGetException
import com.avail.exceptions.VariableSetException
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport
import com.avail.utility.json.JSONWriter
import java.util.Collections
import java.util.WeakHashMap

/**
 * My [object&#32;instances][AvailObject] are [shared][Mutability.SHARED]
 * variables.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see VariableDescriptor
 *
 * @constructor
 * Construct a new [shared][Mutability.SHARED] [variable][A_Variable].
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
open class VariableSharedDescriptor protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?
) : VariableDescriptor(
	mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
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
		@HideFieldInDebugger
		HASH_AND_MORE;

		companion object
		{
			/**
			 * A slot to hold the hash value.  Must be computed when (or before)
			 * making a variable shared.
			 */
			@HideFieldInDebugger
			@JvmField
			val HASH_ALWAYS_SET = BitField(HASH_AND_MORE, 0, 32)

			init
			{
				assert(VariableDescriptor.IntegerSlots.HASH_AND_MORE.ordinal
			       == HASH_AND_MORE.ordinal)
				assert(VariableDescriptor.IntegerSlots.HASH_OR_ZERO
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
		 * A [raw&#32;pojo][RawPojoDescriptor] that wraps a [map][Map] from
		 * arbitrary [Avail&#32;values][AvailObject] to
		 * [write&#32;reactors][VariableDescriptor.VariableAccessReactor] that
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
		DEPENDENT_CHUNKS_WEAK_SET_POJO;

		companion object
		{
			init
			{
				assert(VariableDescriptor.ObjectSlots.VALUE.ordinal
			       == VALUE.ordinal)
				assert(VariableDescriptor.ObjectSlots.KIND.ordinal
				   == KIND.ordinal)
				assert(VariableDescriptor.ObjectSlots.WRITE_REACTORS.ordinal
				   == WRITE_REACTORS.ordinal)
			}
		}
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = (super.allowsImmutableToMutableReferenceInField(e)
		|| e === VALUE
		|| e === WRITE_REACTORS
		|| e === DEPENDENT_CHUNKS_WEAK_SET_POJO
		|| e === IntegerSlots.HASH_AND_MORE) // only for flags.

	override fun o_Hash(self: AvailObject): Int =
		self.slot(HASH_ALWAYS_SET)

	override fun o_Value(self: AvailObject): AvailObject
	{
		recordReadFromSharedVariable(self)
		return synchronized(self) { super.o_Value(self) }
	}

	@Throws(VariableGetException::class)
	override fun o_GetValue(self: AvailObject): AvailObject
	{
		recordReadFromSharedVariable(self)
		return synchronized(self) { super.o_GetValue(self) }
	}

	override fun o_HasValue(self: AvailObject): Boolean
	{
		recordReadFromSharedVariable(self)
		return synchronized(self) { super.o_HasValue(self) }
	}

	@Throws(VariableSetException::class)
	override fun o_SetValue(self: AvailObject, newValue: A_BasicObject)
	{
		synchronized(self) {
			super.o_SetValue(self, newValue.makeShared())
		}
		recordWriteToSharedVariable()
	}

	/**
	 * Write to a newly-constructed variable, bypassing synchronization and
	 * the capture of writes to shared variables for detecting top-level
	 * statements that have side-effect.
	 *
	 * @param self
	 *   The variable.
	 * @param newValue
	 *   The value to write.
	 */
	@Suppress("FunctionName")
	protected fun bypass_VariableDescriptor_SetValue(
		self: AvailObject,
		newValue: A_BasicObject
	) = super.o_SetValue(self, newValue)

	override fun o_SetValueNoCheck(
		self: AvailObject,
		newValue: A_BasicObject)
	{
		synchronized(self) {
			super.o_SetValueNoCheck(self, newValue.makeShared())
		}
		recordWriteToSharedVariable()
	}

	/**
	 * Write to a newly-constructed variable, bypassing synchronization, type
	 * checking, and the capture of writes to shared variables for detecting
	 * top-level statements that have side-effect.
	 *
	 * @param self
	 *   The variable.
	 * @param newValue
	 *   The value to write.
	 */
	@Suppress("FunctionName")
	protected fun bypass_VariableDescriptor_SetValueNoCheck(
		self: AvailObject,
		newValue: A_BasicObject
	) = super.o_SetValueNoCheck(self, newValue)

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_GetAndSetValue(
		self: AvailObject, newValue: A_BasicObject): AvailObject
	{
		// Because the separate read and write operations are performed within
		// the critical section, atomicity is ensured.
		try
		{
			synchronized(self) {
				return super.o_GetAndSetValue(self, newValue.makeShared())
			}
		}
		finally
		{
			recordWriteToSharedVariable()
		}
	}

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_CompareAndSwapValues(
		self: AvailObject,
		reference: A_BasicObject,
		newValue: A_BasicObject): Boolean
	{
		// Because the separate read, compare, and write operations are all
		// performed within the critical section, atomicity is ensured.
		try
		{
			synchronized(self) {
				return super.o_CompareAndSwapValues(
					self, reference, newValue.makeShared())
			}
		}
		finally
		{
			recordWriteToSharedVariable()
		}
	}

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_FetchAndAddValue(
		self: AvailObject,
		addend: A_Number): A_Number
	{
		// Because the separate read and write operations are all performed
		// within the critical section, atomicity is ensured.
		try
		{
			synchronized(self) {
				return super.o_FetchAndAddValue(self, addend.makeShared())
			}
		}
		finally
		{
			recordWriteToSharedVariable()
		}
	}

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_AtomicAddToMap(
		self: AvailObject,
		key: A_BasicObject,
		value: A_BasicObject)
	{
		// Because the separate read and write operations are all performed
		// within the critical section, atomicity is ensured.
		synchronized(self) {
			super.o_AtomicAddToMap(
				self, key.makeShared(), value.makeShared())
		}
	}

	@Throws(VariableGetException::class)
	override fun o_VariableMapHasKey(
		self: AvailObject, key: A_BasicObject): Boolean
	{
		synchronized(self) {
			return super.o_VariableMapHasKey(self, key)
		}
	}

	override fun o_ClearValue(self: AvailObject)
	{
		synchronized(self) { super.o_ClearValue(self) }
		recordWriteToSharedVariable()
	}

	/**
	 * Record the fact that the chunk depends on this object not changing.
	 */
	override fun o_AddDependentChunk(self: AvailObject, chunk: L2Chunk)
	{
		// Record the fact that the given chunk depends on this object not
		// changing.  Local synchronization is sufficient, since invalidation
		// can't happen while L2 code is running (and therefore when the
		// L2Generator could be calling this).
		synchronized(self) {
			val pojo: A_BasicObject =
				self.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO)
			val chunkSet: MutableSet<L2Chunk>
			if (pojo.equalsNil())
			{
				chunkSet = Collections.synchronizedSet(
					Collections.newSetFromMap(WeakHashMap()))
				self.setSlot(
					DEPENDENT_CHUNKS_WEAK_SET_POJO,
					RawPojoDescriptor.identityPojo(chunkSet).makeShared())
			}
			else
			{
				chunkSet = pojo.javaObjectNotNull()
			}
			chunkSet.add(chunk)
		}
	}

	override fun o_RemoveDependentChunk(self: AvailObject, chunk: L2Chunk)
	{
		assert(L2Chunk.invalidationLock.isHeldByCurrentThread)
		val pojo: A_BasicObject =
			self.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO)
		if (!pojo.equalsNil())
		{
			val chunkSet =
				pojo.javaObjectNotNull<MutableSet<L2Chunk>>()
			chunkSet.remove(chunk)
		}
	}

	override fun o_AddWriteReactor(
		self: AvailObject,
		key: A_Atom,
		reactor: VariableAccessReactor)
	{
		recordReadFromSharedVariable(self)
		synchronized(self) {
			super.o_AddWriteReactor(self, key, reactor)
		}
	}

	@Throws(AvailException::class)
	override fun o_RemoveWriteReactor(self: AvailObject, key: A_Atom)
	{
		recordReadFromSharedVariable(self)
		synchronized(self) { super.o_RemoveWriteReactor(self, key) }
	}

	override fun o_ValidWriteReactorFunctions(self: AvailObject): A_Set
	{
		synchronized(self) {
			return super.o_ValidWriteReactorFunctions(self)
		}
	}

	override fun o_MakeImmutable(self: AvailObject): AvailObject =
		// Do nothing; just answer the (shared) receiver.
		self

	override fun o_MakeShared(self: AvailObject): AvailObject =
		// Do nothing; just answer the (shared) receiver.
		self

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("variable") }
			at("variable type") { self.slot(KIND).writeTo(writer) }
			at("value") { self.value().writeSummaryTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("variable") }
			at("variable type") { self.kind().writeSummaryTo(writer) }
		}

	companion object
	{
		/**
		 * Indicate in the current fiber's
		 * [availLoader][Interpreter.availLoader] that a shared variable has
		 * just been modified.
		 */
		@JvmStatic
		protected fun recordWriteToSharedVariable()
		{
			Interpreter.current().availLoaderOrNull()
				?.statementCanBeSummarized(false)
		}

		/**
		 * Indicate in the current fiber's
		 * [availLoader][Interpreter.availLoader] that a shared variable has
		 * just been read.
		 *
		 * @param self
		 *   The shared variable that was read.
		 */
		private fun recordReadFromSharedVariable(self: AvailObject)
		{
			val loader = Interpreter.current().availLoaderOrNull()
			if (loader !== null && loader.statementCanBeSummarized()
			    && !self.slot(VALUE).equalsNil()
			    && !self.valueWasStablyComputed())
			{
				loader.statementCanBeSummarized(false)
			}
		}

		/**
		 * Invalidate any dependent [Level&#32;Two&#32;chunks][L2Chunk].
		 *
		 * @param self
		 *   The method that changed.
		 */
		@Suppress("unused")
		private fun invalidateChunks(self: AvailObject)
		{
			assert(L2Chunk.invalidationLock.isHeldByCurrentThread)
			// Invalidate any affected level two chunks.
			val pojo: A_BasicObject = self.slot(
				DEPENDENT_CHUNKS_WEAK_SET_POJO)
			if (!pojo.equalsNil())
			{
				// Copy the set of chunks to avoid modification during iteration.
				val originalSet =
					pojo.javaObjectNotNull<Set<L2Chunk>>()
				val chunksToInvalidate = originalSet.toSet()
				chunksToInvalidate.forEach {
					it.invalidate(invalidationForSlowVariable)
				}
				assert(originalSet.isEmpty())
			}
		}

		/**
		 * The [Statistic] tracking the cost of invalidations for a change to a
		 * nearly-constant variable.
		 */
		private val invalidationForSlowVariable = Statistic(
			"(invalidation for slow variable change)",
			StatisticReport.L2_OPTIMIZATION_TIME)

		/**
		 * Create a [shared][Mutability.SHARED] [variable][A_Variable]. This
		 * method should only be used to "upgrade" a variable's representation.
		 *
		 * @param kind
		 *   The [variable&#32;type][VariableTypeDescriptor].
		 * @param hash
		 *   The hash of the variable.
		 * @param value
		 *   The contents of the variable.
		 * @param oldVariable
		 *   The variable being made shared.
		 * @return
		 *   The shared variable.
		 */
		fun createSharedFrom(
			kind: A_Type,
			hash: Int,
			value: A_BasicObject,
			oldVariable: AvailObject
		): AvailObject {
			// Make the parts immutable (not shared), just so they won't be
			// destroyed when the original variable becomes an indirection.
			kind.makeImmutable()
			value.makeImmutable()

			// Create the new variable, but allow the slots to be made shared
			// *after* its initialization.  The existence of a shared object
			// temporarily having non-shared fields is not a violation of the
			// invariant, since no other fibers can access the value until the
			// entire makeShared activity has completed.
			return mutableInitial.create {
				setSlot(KIND, kind)
				setSlot(HASH_ALWAYS_SET, hash)
				setSlot(VALUE, value)
				setSlot(WRITE_REACTORS, nil)
				setSlot(DEPENDENT_CHUNKS_WEAK_SET_POJO, nil)
				assert(!oldVariable.descriptor().isShared)
				oldVariable.becomeIndirectionTo(this)

				// Make the parts shared.  This may recurse, but it will terminate
				// when it sees this variable again.  Write back the shared versions
				// for efficiency.
				setSlot(KIND, kind.makeShared())
				setSlot(VALUE, value.makeShared())
				assert(descriptor() === mutableInitial)
				setDescriptor(shared)

				// For safety, make sure the indirection is also shared.
				oldVariable.makeShared()
			}
		}

		/**
		 * The mutable [VariableSharedDescriptor]. Exists only to support
		 * creation.
		 */
		private val mutableInitial = VariableSharedDescriptor(
			Mutability.MUTABLE,
			TypeTag.VARIABLE_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)

		/** The shared [VariableSharedDescriptor].  */
		val shared = VariableSharedDescriptor(
			Mutability.SHARED,
			TypeTag.VARIABLE_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)
	}
}
