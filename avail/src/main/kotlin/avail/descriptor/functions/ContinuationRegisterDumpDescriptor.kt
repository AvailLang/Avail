/*
 * ContinuationRegisterDumpDescriptor.kt
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

import avail.descriptor.functions.ContinuationRegisterDumpDescriptor.IntegerSlots.INTEGER_SLOTS_
import avail.descriptor.functions.ContinuationRegisterDumpDescriptor.ObjectSlots.ENCODED_ELIDED_LOCALS
import avail.descriptor.functions.ContinuationRegisterDumpDescriptor.ObjectSlots.OBJECT_SLOTS_
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.TypeTag
import avail.interpreter.levelTwo.L2Chunk
import avail.optimizer.jvm.CheckedField.Companion.staticField
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * A `ContinuationRegisterDumpDescriptor` instance holds a collection of
 * [AvailObject] and [Long] slots for use by an [L2Chunk].  It's typically
 * stored in the [A_Continuation.registerDump] slot of an [A_Continuation].  The
 * interpretation of its fields depends on the [L2Chunk] that's both creating
 * and consuming it.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik&lt;mark@availlang.org&gt;
 */
class ContinuationRegisterDumpDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability,
	TypeTag.UNKNOWN_TAG,
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
	enum class ObjectSlots : ObjectSlotsEnum {
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
		 * These are encoded and decoded via [A_RegisterDump.encodeLocalValue]
		 * and [A_RegisterDump.decodeBoxedValueFromDump].
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

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	override fun o_ExtractDumpedObjectAt(self: AvailObject, index: Int) =
		self[OBJECT_SLOTS_, index]

	override fun o_ExtractDumpedLongAt(self: AvailObject, index: Int): Long =
		self[INTEGER_SLOTS_, index]

	override fun o_EncodedElidedLocals(self: AvailObject): A_Tuple =
		self[ENCODED_ELIDED_LOCALS]

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object {
		/**
		 * Create a new register dump [AvailObject] with the given data.  If
		 * both arrays are empty, answer the [emptyRegisterDump].
		 *
		 * @param objects
		 *   The array of [AvailObject]s to capture.
		 * @param longs
		 *   The array of [Long]s to capture.
		 * @return
		 *   The new register dump object or [nil].
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun createRegisterDump(
			encodedElidedLocals: A_Tuple,
			objects: Array<AvailObject>,
			longs: LongArray
		): AvailObject = mutable.create(objects.size, longs.size) {
			setSlot(ENCODED_ELIDED_LOCALS, encodedElidedLocals)
			setSlotsFromArray(OBJECT_SLOTS_, 1, objects, 0, objects.size)
			setSlotsFromArray(INTEGER_SLOTS_, 1, longs, 0, longs.size)
		}

		/** The mutable [ContinuationRegisterDumpDescriptor]. */
		private val mutable =
			ContinuationRegisterDumpDescriptor(Mutability.MUTABLE)

		/** The immutable [ContinuationRegisterDumpDescriptor]. */
		private val immutable =
			ContinuationRegisterDumpDescriptor(Mutability.IMMUTABLE)

		/** The shared [ContinuationRegisterDumpDescriptor]. */
		private val shared =
			ContinuationRegisterDumpDescriptor(Mutability.SHARED)

		/** A pre-built register dump with nothing in it. */
		@ReferencedInGeneratedCode
		@JvmField
		val emptyRegisterDump: AvailObject = mutable.createShared(0) {
			this[ENCODED_ELIDED_LOCALS] = nil
		}

		/** Access the method [createRegisterDump]. */
		val createRegisterDumpMethod = staticMethod(
			ContinuationRegisterDumpDescriptor::class.java,
			::createRegisterDump.name,
			AvailObject::class.java,
			A_Tuple::class.java,
			Array<AvailObject>::class.java,
			LongArray::class.java)

		/** Access the static field [emptyRegisterDump]. */
		val emptyRegisterDumpField = staticField(
			ContinuationRegisterDumpDescriptor::class.java,
			::emptyRegisterDump.name,
			AvailObject::class.java)
	}
}
