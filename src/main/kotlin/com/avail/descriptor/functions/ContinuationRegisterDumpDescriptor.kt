/*
 * ContinuationRegisterDumpDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *    list of conditions and the following disclaimer in the documentation
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
package com.avail.descriptor.functions

import com.avail.descriptor.Descriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.functions.ContinuationRegisterDumpDescriptor.IntegerSlots.INTEGER_SLOTS_
import com.avail.descriptor.functions.ContinuationRegisterDumpDescriptor.ObjectSlots.OBJECT_SLOTS_
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.newObjectIndexedIntegerIndexedDescriptor
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.types.TypeTag
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

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
		 * A vector of [AvailObject] slots, to be interpreted by the [L2Chunk]
		 * that both creates and consumes it.
		 */
		OBJECT_SLOTS_
	}

	override fun o_ShowValueInNameForDebugger(self: AvailObject) = false

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object {
		/**
		 * Create a new register dump [AvailObject] with the given data.  If
		 * both arrays are empty, answer [nil].
		 *
		 * @param objects
		 *   The array of [AvailObject]s to capture.
		 * @param longs
		 *   The array of [Long]s to capture.
		 * @return
		 *   The new register dump object or [nil].
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun createRegisterDump(
			objects: Array<AvailObject>,
			longs: LongArray
		): AvailObject =
			if (objects.size == 0 && longs.size == 0) {
				nil
			} else {
				newObjectIndexedIntegerIndexedDescriptor(
					objects.size, longs.size, mutable
				).apply {
					setSlotsFromArray(
						OBJECT_SLOTS_, 1, objects, 0, objects.size)
					setSlotsFromArray(INTEGER_SLOTS_, 1, longs, 0, longs.size)
				}
			}

		/** Access the method [createRegisterDump]. */
		@JvmField
		var createRegisterDumpMethod: CheckedMethod = staticMethod(
			ContinuationRegisterDumpDescriptor::class.java,
			::createRegisterDump.name,
			AvailObject::class.java,
			Array<AvailObject>::class.java,
			LongArray::class.java)

		/**
		 * Given a continuation register dump, extract the object at the given
		 * slot index.
		 *
		 * @param dump
		 *   The continuation register dump to extract from.
		 * @param index
		 *   The index at which to extract an [AvailObject].
		 * @return
		 *   The extracted [AvailObject].
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun extractObjectAt(
			dump: AvailObject,
			index: Int
		) = dump.slot(OBJECT_SLOTS_, index)

		/**
		 * A [CheckedMethod] for invoking the static method
		 * [.extractObjectAt].
		 */
		@JvmField
		var extractObjectAtMethod: CheckedMethod = staticMethod(
			ContinuationRegisterDumpDescriptor::class.java,
			::extractObjectAt.name,
			AvailObject::class.java,
			AvailObject::class.java,
			Int::class.javaPrimitiveType)

		/**
		 * Given a continuation register dump, extract the [Long] at the given
		 * slot index.
		 *
		 * @param dump
		 *   The continuation register dump to extract from.
		 * @param index
		 *   The index at which to extract a [Long].
		 * @return
		 *   The extracted [Long].
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun extractLongAt(
			dump: AvailObject,
			index: Int): Long {
			return dump.slot(INTEGER_SLOTS_, index)
		}

		/**
		 * A [CheckedMethod] for invoking the static method [extractLongAt].
		 */
		@JvmField
		var extractLongAtMethod: CheckedMethod = staticMethod(
			ContinuationRegisterDumpDescriptor::class.java,
			::extractLongAt.name,
			Long::class.javaPrimitiveType!!,
			AvailObject::class.java,
			Int::class.javaPrimitiveType)

		/** The mutable [ContinuationRegisterDumpDescriptor]. */
		private val mutable =
			ContinuationRegisterDumpDescriptor(Mutability.MUTABLE)

		/** The immutable [ContinuationRegisterDumpDescriptor]. */
		private val immutable =
			ContinuationRegisterDumpDescriptor(Mutability.IMMUTABLE)

		/** The shared [ContinuationRegisterDumpDescriptor]. */
		private val shared =
			ContinuationRegisterDumpDescriptor(Mutability.SHARED)
	}
}