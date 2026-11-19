/*
 * RegisterDumpDescriptor.kt
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
package avail.descriptor.functions

import avail.descriptor.functions.RegisterDumpDescriptor.IntegerSlots.FREEZE_OFFSET
import avail.descriptor.functions.RegisterDumpDescriptor.IntegerSlots.INTEGER_SLOTS_
import avail.descriptor.functions.RegisterDumpDescriptor.ObjectSlots.OBJECT_SLOTS_
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Continuation
import avail.descriptor.representation.A_RegisterDump
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.Mutability.IMMUTABLE
import avail.descriptor.representation.Mutability.MUTABLE
import avail.descriptor.representation.Mutability.SHARED
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TypeTag
import avail.interpreter.JavaLibrary.int
import avail.interpreter.JavaLibrary.long
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.operation.L2_CREATE_CONTINUATION
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPointCatalog
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.ExecutableChunk
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * A [RegisterDumpDescriptor] instance holds a collection of [AvailObject] and
 * [Long] slots for use by an [L2Chunk].  It's typically stored in the
 * [A_Continuation.registerDump] slot of an [A_Continuation].  The
 * interpretation of its fields depends on the [L2Chunk] that's both creating
 * and consuming it.
 *
 * An [L2_SAVE_ALL_AND_PC_TO_INT] creates the dump, saving the registers that
 * are live where the continuation will resume.  When the continuation it
 * feeds has elided variables, that instruction also reserves room after its
 * own values, which the [L2_CREATE_CONTINUATION] fills with the registers
 * that its freeze subgraph needs, and records in [FREEZE_OFFSET] where that
 * subgraph starts (doc/Optimization/Level_Two/Variable_elision.md §Freezing).
 *
 * @constructor
 * @property fallbackEntryPoint
 *   The [DefaultEntryPoint] at which to eventually re-enter the
 *   [DefaultL1Chunk], should the containing [A_Continuation] become immutable
 *   or shared – at which time any elided local variables are first created by
 *   the chunk's [freeze][ExecutableChunk.freeze] method.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param fallbackEntryPoint
 *   The [DefaultEntryPoint] for re-entry into the [DefaultL1Chunk].
 *
 * @author Mark van Gulik&lt;mark@availlang.org&gt;
 */
class RegisterDumpDescriptor private constructor(
	mutability: Mutability,
	val fallbackEntryPoint: Int
) : Descriptor(
	mutability,
	TypeTag.OTHER_NONTYPE_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * The L2 offset at which the creating chunk's
		 * [freeze][ExecutableChunk.freeze] method starts creating the elided
		 * variables of the continuation that holds this dump, or -1 if there
		 * are none to create.  An [L2_CREATE_CONTINUATION] sets it after the
		 * dump is created, and it never changes after that.
		 */
		FREEZE_OFFSET,

		/**
		 * A vector of [Long] slots, to be interpreted by the [L2Chunk] that
		 * both creates and consumes it.
		 */
		INTEGER_SLOTS_
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * A vector of [AvailObject] slots, to be interpreted by the [L2Chunk]
		 * that both creates and consumes it.
		 */
		OBJECT_SLOTS_
	}

	override fun o_NameForDebugger(self: AvailObject) =
		super.o_NameForDebugger(self) + " (fallback = $fallbackEntryPoint)"

	override fun o_Hash(self: AvailObject): Int
	{
		return System.identityHashCode(self)
	}

	override fun o_Kind(self: AvailObject) = Types.OTHER_NONTYPE()

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean
	{
		return another.traversed().sameAddressAs(self)
	}

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	override fun o_ExtractDumpedObjectAt(self: AvailObject, index: Int) =
		self[OBJECT_SLOTS_, index]

	override fun o_ExtractDumpedLongAt(self: AvailObject, index: Int): Long =
		self[INTEGER_SLOTS_, index]

	override fun o_FreezeOffset(self: AvailObject): Int =
		self[FREEZE_OFFSET].toInt()

	override fun o_FallbackEntryPoint(self: AvailObject) = fallbackEntryPoint

	override fun mutable() =
		mutables[fallbackEntryPoint + 1]!!

	override fun immutable() =
		immutables[fallbackEntryPoint + 1]!!

	override fun shared() =
		shareds[fallbackEntryPoint + 1]!!

	companion object
	{
		/**
		 * Mutable instances, keyed by 1 + the offset of the [DefaultEntryPoint]
		 * at which a continuation having this register dump would resume in the
		 * [DefaultL1Chunk] if the continuation becomes immutable or shared.
		 *
		 * It's offset by 1 so that [DefaultEntryPoint.TRANSIENT] can have -1 as
		 * its offset (indicating invalid), while still being included in this
		 * array.
		 */
		private val mutables: Array<RegisterDumpDescriptor?>

		/**
		 * Immutable instances, keyed by 1 + the offset of the
		 * [DefaultEntryPoint] at which a continuation having this register dump
		 * would resume in the [DefaultL1Chunk] if the continuation becomes
		 * immutable or shared.
		 */
		private val immutables: Array<RegisterDumpDescriptor?>

		/**
		 * Shared instances, keyed by 1 + the offset of the [DefaultEntryPoint]
		 * at which a continuation having this register dump would resume in the
		 * [DefaultL1Chunk] if the continuation becomes immutable or shared.
		 */
		private val shareds: Array<RegisterDumpDescriptor?>

		/**
		 * An array of empty register dump, keyed by 1 + the offset of the
		 * [DefaultEntryPoint] used as the fallback offset into the
		 * [DefaultL1Chunk].
		 */
		private val emptyRegisterDumps: Array<AvailObject?>

		init
		{
			val size = DefaultEntryPointCatalog.maxEntryPointOffset + 2
			mutables = arrayOfNulls(size)
			immutables = arrayOfNulls(size)
			shareds = arrayOfNulls(size)
			emptyRegisterDumps = arrayOfNulls(size)
			(-1..DefaultEntryPointCatalog.maxEntryPointOffset).forEach { entry ->
				mutables[entry + 1] =
					RegisterDumpDescriptor(MUTABLE, entry)
				immutables[entry + 1] =
					RegisterDumpDescriptor(IMMUTABLE, entry)
				shareds[entry + 1] =
					RegisterDumpDescriptor(SHARED, entry)
				emptyRegisterDumps[entry + 1] =
					mutables[entry + 1]!!.createShared(0) {
						this[FREEZE_OFFSET] = -1L
					}.makeShared()
			}
		}

		/**
		 * Create a new register dump [AvailObject] with the given data,
		 * followed by room for the given number of additional objects and
		 * longs.  The additional objects start as [nil], the additional longs
		 * start as zero, and the [FREEZE_OFFSET] starts as -1, until an
		 * [L2_CREATE_CONTINUATION] fills them in.
		 *
		 * @param fallbackEntryPoint
		 *   The offset of the [DefaultEntryPoint] at which to resume in the
		 *   [DefaultL1Chunk] if the continuation becomes immutable or shared.
		 * @param objects
		 *   The array of [AvailObject]s to capture.
		 * @param longs
		 *   The array of [Long]s to capture.
		 * @param reservedObjects
		 *   How many object slots to reserve after the [objects].
		 * @param reservedLongs
		 *   How many long slots to reserve after the [longs].
		 * @return
		 *   The new, mutable [A_RegisterDump].
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun createRegisterDump(
			fallbackEntryPoint: Int,
			objects: Array<AvailObject>,
			longs: LongArray,
			reservedObjects: Int,
			reservedLongs: Int
		): AvailObject =
			mutables[fallbackEntryPoint + 1]!!.create(
				objects.size + reservedObjects,
				longs.size + reservedLongs
			) {
				setSlot(FREEZE_OFFSET, -1L)
				setSlotsFromArray(OBJECT_SLOTS_, 1, objects, 0, objects.size)
				for (i in objects.size + 1 .. objects.size + reservedObjects)
				{
					setSlot(OBJECT_SLOTS_, i, nil)
				}
				setSlotsFromArray(INTEGER_SLOTS_, 1, longs, 0, longs.size)
			}

		/** Access the method [createRegisterDump]. */
		val createRegisterDumpMethod = staticMethod(
			::createRegisterDump,
			int,
			Array<AvailObject>::class.java,
			LongArray::class.java,
			int,
			int)

		/**
		 * Answer the [A_RegisterDump.freezeOffset] of the given register dump.
		 *
		 * @param dump
		 *   The register dump.
		 * @return
		 *   The L2 offset at which to start freezing, or -1.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun freezeOffsetStatic(dump: AvailObject): Int =
			dump.descriptor.o_FreezeOffset(dump)

		/** Access the method [freezeOffsetStatic]. */
		val freezeOffsetMethod = staticMethod(
			::freezeOffsetStatic,
			AvailObject::class.java)

		/**
		 * Record in the given freshly created, still mutable register dump the
		 * L2 offset at which the creating chunk's
		 * [freeze][ExecutableChunk.freeze] method starts creating the elided
		 * variables of its continuation.
		 *
		 * @param dump
		 *   The mutable register dump.
		 * @param freezeOffset
		 *   The L2 offset of the freeze subgraph's entry.
		 * @return
		 *   The same register dump, to simplify the generated code.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun storeFreezeOffset(dump: AvailObject, freezeOffset: Int): AvailObject
		{
			assert(dump.descriptor.isMutable)
			dump.setSlot(FREEZE_OFFSET, freezeOffset.toLong())
			return dump
		}

		/** Access the method [storeFreezeOffset]. */
		val storeFreezeOffsetMethod = staticMethod(
			::storeFreezeOffset,
			AvailObject::class.java,
			int)

		/**
		 * Store an object into one of the slots that an
		 * [L2_SAVE_ALL_AND_PC_TO_INT] reserved in the given freshly created,
		 * still mutable register dump.
		 *
		 * @param dump
		 *   The mutable register dump.
		 * @param index
		 *   The one-based index of the object slot.
		 * @param value
		 *   The object to store.
		 * @return
		 *   The same register dump, to simplify the generated code.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun storeDumpedObject(
			dump: AvailObject,
			index: Int,
			value: AvailObject
		): AvailObject
		{
			assert(dump.descriptor.isMutable)
			dump.setSlot(OBJECT_SLOTS_, index, value)
			return dump
		}

		/** Access the method [storeDumpedObject]. */
		val storeDumpedObjectMethod = staticMethod(
			::storeDumpedObject,
			AvailObject::class.java,
			int,
			AvailObject::class.java)

		/**
		 * Store a [Long] into one of the slots that an
		 * [L2_SAVE_ALL_AND_PC_TO_INT] reserved in the given freshly created,
		 * still mutable register dump.
		 *
		 * @param dump
		 *   The mutable register dump.
		 * @param index
		 *   The one-based index of the long slot.
		 * @param value
		 *   The [Long] to store.
		 * @return
		 *   The same register dump, to simplify the generated code.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun storeDumpedLong(
			dump: AvailObject,
			index: Int,
			value: Long
		): AvailObject
		{
			assert(dump.descriptor.isMutable)
			dump.setSlot(INTEGER_SLOTS_, index, value)
			return dump
		}

		/** Access the method [storeDumpedLong]. */
		val storeDumpedLongMethod = staticMethod(
			::storeDumpedLong,
			AvailObject::class.java,
			int,
			long)

		/**
		 * Answer an empty register dump that uses the given fallback entry
		 * point into the [DefaultL1Chunk].
		 */
		fun emptyRegisterDump(fallbackEntryPoint: Int) =
			emptyRegisterDumps[fallbackEntryPoint + 1]!!
	}
}
