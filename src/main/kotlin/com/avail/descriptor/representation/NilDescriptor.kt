/*
 * NilDescriptor.kt
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
package com.avail.descriptor.representation

import com.avail.annotations.ThreadSafe
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeTag
import com.avail.serialization.SerializerOperation
import java.util.*

/**
 * `NilDescriptor` implements the Avail [nil] value, the sole direct instance of
 * the invisible and uninstantiable root type, ⊤ (top).
 *
 * @constructor
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class NilDescriptor private constructor() : Descriptor(
	Mutability.SHARED, TypeTag.NIL_TAG, null, null
) {
	@ThreadSafe
	override fun o_Equals(
		self: AvailObject, another: A_BasicObject
	): Boolean {
		return another.equalsNil()
	}

	@ThreadSafe
	override fun o_Hash(self: AvailObject): Int {
		// Nil should hash to zero, because the only place it can appear in a
		// data structure is as a placeholder object. This currently (as of July
		// 1998) applies to sets, maps, variables, and continuations.
		return 0
	}

	@ThreadSafe
	override fun o_Kind(self: AvailObject): A_Type = Types.TOP.o()

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.NIL

	@ThreadSafe
	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		builder.append("nil")
	}

	override fun mutable() = shared

	override fun immutable() = shared

	override fun shared() = shared

	companion object {
		/** The shared [NilDescriptor].  */
		private val shared = NilDescriptor()

		/** The sole instance of `NilDescriptor`, called "nil".  */
		@JvmField
		val nil: AvailObject = shared.create()
	}
}
