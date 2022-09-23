/*
 * WeakObjectTypeReference.kt
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
import java.lang.ref.WeakReference

/**
 * A [WeakObjectTypeReference] is a specialization of [WeakReference] that
 * weakly holds an [AvailObject] which is always an
 * [object&#32;type][ObjectTypeDescriptor] whose descriptor indicates it's
 * [Mutability.SHARED].  It also holds that object type's hash value.
 *
 * @constructor
 * Create the instance, caching the hash value.
 *
 * @param objectType
 *   The [object&#32;type][ObjectTypeDescriptor] to hold weakly.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class WeakObjectTypeReference(
	objectType: AvailObject,
) : WeakReference<AvailObject>(objectType)
{
	/** The cached hash value of the object type. */
	val hash = objectType.hash()

	/**
	 * Check if the given someObjectType and its hash value correspond with the
	 * stored content.
	 */
	fun matches(someHash: Int, someObjectType: AvailObject): Boolean
	{
		// Vast majority of false cases.
		if (someHash != hash) return false
		val objectType = get()
		// Stored object type has evaporated.
		if (objectType === null) return false
		// Technically the identity check is sufficient, but the extra safety is
		// cheap.
		return someObjectType.traversed().sameAddressAs(objectType) ||
			someObjectType.equals(objectType)
	}
}
