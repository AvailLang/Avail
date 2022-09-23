/*
 * ObjectLayoutVariant.kt
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
package avail.descriptor.objects

import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Mutability
import avail.descriptor.types.A_Type.Companion.hasObjectInstance

/**
 * A [VettingsCache] is stored as a pojo in an [object][ObjectDescriptor] to
 * cache tests of membership against [object&#32;types][ObjectTypeDescriptor].
 * It consists of two 'positive' arrays of [WeakObjectTypeReference]s that the
 * owning object was found to be an instance of, and two 'negative' arrays of
 * [WeakObjectTypeReference]s that the owning object was found *not* to be an
 * instance of.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class VettingsCache
{
	/**
	 * One of the two positive caches.  If the object that holds this
	 * [VettingsCache] has a particular object type in this slot (inside a
	 * [WeakObjectTypeReference]), the object is known to be an instance of that
	 * object type.
	 */
	@Volatile
	var positiveCache1 = emptyArray

	/**
	 * One of the two negative caches.  If the object that holds this
	 * [VettingsCache] has a particular object type in this slot (inside a
	 * [WeakObjectTypeReference]), the object is known *not* to be an instance
	 * of that object type.
	 */
	@Volatile
	var negativeCache1 = emptyArray

	/**
	 * One of the two positive caches.  If the object that holds this
	 * [VettingsCache] has a particular object type in this slot (inside a
	 * [WeakObjectTypeReference]), the object is known to be an instance of that
	 * object type.
	 */
	@Volatile
	var positiveCache2 = emptyArray

	/**
	 * One of the two negative caches.  If the object that holds this
	 * [VettingsCache] has a particular object type in this slot (inside a
	 * [WeakObjectTypeReference]), the object is known *not* to be an instance
	 * of that object type.
	 */
	@Volatile
	var negativeCache2 = emptyArray

	/**
	 * Test if [theObject], which is instantiation of [ObjectDescriptor], is an
	 * instance of [objectType], which is a [Mutability.SHARED] instantiation of
	 * [ObjectTypeDescriptor].  The receiver is [theObject]'s vettings cache,
	 * and may be updated by this test.
	 */
	fun testObjectAgainstType(
		theObject: AvailObject,
		objectType: AvailObject
	): Boolean
	{
		assert(objectType.descriptor().isShared)
		val objectTypeHash = objectType.hash()
		positiveCache1.forEach {
			if (it.matches(objectTypeHash, objectType)) return true
		}
		negativeCache1.forEach {
			if (it.matches(objectTypeHash, objectType)) return false
		}
		positiveCache2.forEach {
			if (it.matches(objectTypeHash, objectType)) return true
		}
		negativeCache2.forEach {
			if (it.matches(objectTypeHash, objectType)) return false
		}
		// We don't have a record of checking against this type.  Do so now.
		// Note that when we write back the result into the cache, we only go so
		// far as volatile semantics, since we don't care if we occasionally
		// lose a value and have to retest it.  Even if we sometimes lose an
		// entire array of results, it's not that big a deal.
		val weakRef = WeakObjectTypeReference(objectType)
		if (objectType.hasObjectInstance(theObject))
		{
			// It's an instance.
			if (positiveCache1.size < maximumVettingSetSize)
			{
				positiveCache1 += weakRef
			}
			else
			{
				// Flip cache 1 -> cache 2 and start anew on cache 1.
				positiveCache2 = positiveCache1
				positiveCache1 = arrayOf(weakRef)
			}
			return true
		}
		// It's not an instance.
		if (negativeCache1.size < maximumVettingSetSize)
		{
			negativeCache1 += weakRef
		}
		else
		{
			// Flip cache 1 -> cache 2 and start anew on cache 1.
			negativeCache2 = negativeCache1
			negativeCache1 = arrayOf(weakRef)
		}
		return false
	}

	companion object
	{
		/**
		 * The maximum size that one of the four arrays in a [VettingsCache] may
		 * be before taking action to reduce it. If [positiveCache1] tries to
		 * exceed this size, move the array to [positiveCache2] and clear
		 * [positiveCache1].  Likewise, if [negativeCache1] tries to exceed this
		 * size, move the array to [negativeCache2] and clear [negativeCache1].
		 */
		private const val maximumVettingSetSize = 20

		/**
		 * A reusable empty array for indicating an empty slot in a
		 * [VettingsCache].
		 */
		private val emptyArray = arrayOf<WeakObjectTypeReference>()
	}
}
