/*
 * MarkUnreachableSubobjectVisitor.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.utility.visitor

import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.A_BasicObject

/**
 * Provide the ability to iterate over an object's fields, marking each child
 * object as unreachable.  Also recurse into the children, but avoid a specific
 * object during the recursion.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property exclusion
 *    An object which we should *not* recurse into if encountered.
 * @constructor
 * Construct a new [MarkUnreachableSubobjectVisitor].
 *
 * @param exclusion
 *   The [object][A_BasicObject] within which to *avoid* marking subobjects as
 *   unreachable. Use NilDescriptor.nilObject() if no such object is necessary,
 *   as it's always already immutable.
 */
class MarkUnreachableSubobjectVisitor constructor (
	private val exclusion: A_BasicObject) : AvailSubobjectVisitor
{
	override fun invoke(childObject: AvailObject): AvailObject = when {
		!childObject.descriptor().isMutable -> childObject
		// The excluded object was reached.
		childObject.sameAddressAs(exclusion) -> childObject
		else -> {
			// Recursively invoke the iterator on the subobjects of
			// subobject... Indicate the object is no longer valid and
			// should not ever be used again.
			childObject.scanSubobjects(this)
			// Indicate the object is no longer valid and should not ever be used
			// again.
			childObject.destroy()
			childObject
		}
	}
}
