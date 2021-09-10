/*
 * A_SetBin.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.descriptor.sets

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.dispatch
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.types.A_Type

/**
 * `A_SetBin` is a collection of values which are part of a [set][A_Set].
 *
 * Bins below a particular scale ([LinearSetBinDescriptor.thresholdToHash]) are
 * usually represented via [LinearSetBinDescriptor].
 *
 * Above that threshold, a [HashedSetBinDescriptor] is used, which organizes the
 * values into a tree based on their hash values.
 *
 * Bins are not allowed to occur inside any other data structures, such as in a
 * tuple, stored in a variable, or passed to or from a function.  Because of
 * this restriction, a single element can itself act as a bin.  This may happen
 * to represent a one-element set, or it may occur at the leaves of a tree of
 * hashed bins.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_SetBin : A_BasicObject
{
	companion object
	{
		fun A_SetBin.binElementAt(index: Int): AvailObject =
			dispatch { o_BinElementAt(it, index) }

		/**
		 * Asked of the top bin of a set.  If the set is large enough to be
		 * hashed then compute/cache the union's nearest kind, otherwise just
		 * answer nil.
		 */
		fun A_SetBin.binElementsAreAllInstancesOfKind(kind: A_Type): Boolean =
			dispatch { o_BinElementsAreAllInstancesOfKind(it, kind) }

		fun A_SetBin.binHasElementWithHash (
			elementObject: A_BasicObject,
			elementObjectHash: Int
		): Boolean = dispatch {
			o_BinHasElementWithHash(it, elementObject, elementObjectHash)
		}

		fun A_SetBin.binRemoveElementHashLevelCanDestroy(
			elementObject: A_BasicObject,
			elementObjectHash: Int,
			myLevel: Int,
			canDestroy: Boolean
		): A_SetBin = dispatch {
			o_BinRemoveElementHashLevelCanDestroy(
				it, elementObject, elementObjectHash, myLevel, canDestroy)
		}

		val A_SetBin.binUnionKind: A_Type
			get() = dispatch { o_BinUnionKind(it) }

		fun A_SetBin.isBinSubsetOf(potentialSuperset: A_Set): Boolean =
			dispatch { o_IsBinSubsetOf(it, potentialSuperset) }

		val A_SetBin.isSetBin get() = dispatch { o_IsSetBin(it) }

		fun A_SetBin.setBinAddingElementHashLevelCanDestroy(
			elementObject: A_BasicObject,
			elementObjectHash: Int,
			myLevel: Int,
			canDestroy: Boolean
		): A_SetBin = dispatch {
			o_SetBinAddingElementHashLevelCanDestroy(
				it, elementObject, elementObjectHash, myLevel, canDestroy)
		}

		val A_SetBin.setBinHash: Int get() = dispatch { o_SetBinHash(it) }

		val A_SetBin.setBinIterator: SetDescriptor.SetIterator
			get() = dispatch { o_SetBinIterator(it) }

		val A_SetBin.setBinSize: Int get() = dispatch { o_SetBinSize(it) }
	}
}
