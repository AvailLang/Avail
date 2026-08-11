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

import avail.descriptor.functions.RegisterDumpDescriptor.Companion.createRegisterDump
import avail.descriptor.functions.RegisterDumpDescriptor.IntegerSlots.INTEGER_SLOTS_
import avail.descriptor.functions.RegisterDumpDescriptor.ObjectSlots.ENCODED_ELIDED_LOCALS
import avail.descriptor.functions.RegisterDumpDescriptor.ObjectSlots.OBJECT_SLOTS_
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Continuation
import avail.descriptor.representation.A_Continuation.Companion.registerDump
import avail.descriptor.representation.A_RegisterDump
import avail.descriptor.representation.A_RegisterDump.Companion.decodeBoxedValueFromDump
import avail.descriptor.representation.A_RegisterDump.Companion.encodeLocalValue
import avail.descriptor.representation.A_Tuple
import avail.descriptor.representation.A_Tuple.Companion.tupleIntAt
import avail.descriptor.representation.A_Tuple.Companion.tupleSize
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.Mutability.IMMUTABLE
import avail.descriptor.representation.Mutability.MUTABLE
import avail.descriptor.representation.Mutability.SHARED
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TypeTag
import avail.interpreter.JavaLibrary.int
import avail.interpreter.levelTwo.L2Chunk
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPointCatalog
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * A [RegisterDumpDescriptor] instance holds a collection of [AvailObject] and
 * [Long] slots for use by an [L2Chunk].  It's typically stored in the
 * [A_Continuation.registerDump] slot of an [A_Continuation].  The
 * interpretation of its fields depends on the [L2Chunk] that's both creating
 * and consuming it.
 *
 * @constructor
 * @property fallbackEntryPoint
 *   The [DefaultEntryPoint] at which to eventually re-enter the
 *   [DefaultL1Chunk], should the containing [A_Continuation] become immutable
 *   or shared – at which time any elided local fields will also be created and
 *   populated via information recorded in [ENCODED_ELIDED_LOCALS].
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
		 * An encoding of local variables that have not yet been constructed in
		 * the corresponding continuation.
		 *
		 * The encoding is in consecutive pairs of [i32]s, where the first one
		 * is a local number and the second one indicates where to find its
		 * initial value:
		 *  - If positive, it's an index into [OBJECT_SLOTS_],
		 *  - If negative and even, -2×N, the Nth entry in [INTEGER_SLOTS_]
		 *    contains an [Int] to box and store in the new variable.
		 *  - If negative and odd, -2×N+1, the Nth entry in [INTEGER_SLOTS_]
		 *    array contains a [Long] whose bit pattern can produce a [Double]
		 *    to box and store in the new variable.
		 *
		 * These are encoded and decoded via [encodeLocalValue]
		 * and [decodeBoxedValueFromDump].
		 *
		 * These values are used to initialize new local variables if a
		 * continuation using this register dump is made immutable or shared.
		 * If a local isn't mentioned, it still gets created in that
		 * circumstance, but has no initial value (i.e., it's unassigned).
		 */
		ENCODED_ELIDED_LOCALS,

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

	override fun o_EncodedElidedLocals(self: AvailObject): A_Tuple =
		self[ENCODED_ELIDED_LOCALS]

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
						this[ENCODED_ELIDED_LOCALS] = emptyTuple
					}.makeShared()
			}
		}

		/**
		 * Create a new register dump [AvailObject] with the given data.
		 *
		 * @param objects
		 *   The array of [AvailObject]s to capture.
		 * @param longs
		 *   The array of [Long]s to capture.
		 * @return
		 *   The new [A_RegisterDump] or [nil].
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun createRegisterDump(
			fallbackEntryPoint: Int,
			encodedElidedLocals: A_Tuple,
			objects: Array<AvailObject>,
			longs: LongArray
		): AvailObject =
			mutables[fallbackEntryPoint + 1]!!.create(
				objects.size,
				longs.size
			) {
				assert(
					encodedElidedLocals.run {
						isNil || (2 .. tupleSize step 2).all {
							tupleIntAt(it) != 0
						}
					})
				setSlot(ENCODED_ELIDED_LOCALS, encodedElidedLocals)
				setSlotsFromArray(OBJECT_SLOTS_, 1, objects, 0, objects.size)
				setSlotsFromArray(INTEGER_SLOTS_, 1, longs, 0, longs.size)
			}

		/** Access the method [createRegisterDump]. */
		val createRegisterDumpMethod = staticMethod(
			RegisterDumpDescriptor::class.java,
			::createRegisterDump.name,
			AvailObject::class.java,
			int,
			A_Tuple::class.java,
			Array<AvailObject>::class.java,
			LongArray::class.java)

		/**
		 * Answer an empty register dump that uses the given fallback entry
		 * point into the [DefaultL1Chunk].
		 */
		fun emptyRegisterDump(fallbackEntryPoint: Int) =
			emptyRegisterDumps[fallbackEntryPoint + 1]!!
	}
}
