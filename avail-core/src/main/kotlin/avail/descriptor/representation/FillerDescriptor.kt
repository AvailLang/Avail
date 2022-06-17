/*
 * FillerDescriptor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.representation

import avail.descriptor.types.TypeTag
import java.util.IdentityHashMap

/**
 * [FillerDescriptor] represents an unreachable [AvailObject] of arbitrary size.
 * It exists solely to occupy dead space formerly occupied by a real object.
 *
 * In the current Java (/Kotlin) VM, it merely catches bad accesses of objects
 * that are _supposed_ to have zero remaining references.
 *
 * In VM implementations with Avail-controlled memory management, it should also
 * act as a free space region, keeping memory scans tidy and consistent.
 *
 * @constructor
 *
 */
class FillerDescriptor private constructor() : Descriptor(
	Mutability.MUTABLE, TypeTag.UNKNOWN_TAG, null, null
) {
	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		builder.append("(*** a destroyed object ***)")
	}

	override fun o_MakeImmutable(self: AvailObject): AvailObject = unsupported

	override fun o_MakeShared(self: AvailObject): AvailObject = unsupported

	override fun o_MakeSubobjectsImmutable(self: AvailObject): AvailObject =
		unsupported

	override fun o_MakeSubobjectsShared(self: AvailObject): AvailObject =
		unsupported

	override fun mutable(): FillerDescriptor {
		return mutable
	}

	override fun immutable(): FillerDescriptor = unsupported

	override fun shared(): FillerDescriptor  = unsupported

	companion object {
		/**
		 * The sole [FillerDescriptor], which is [Mutability.MUTABLE].  There
		 * are no immutable or shared filler descriptors, because if a filler is
		 * embedded in some other object structure which is being made immutable
		 * or shared, it's always an error.
		 */
		val mutable = FillerDescriptor()
	}
}
