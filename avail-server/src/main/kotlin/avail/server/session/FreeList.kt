/*
 * FreeList.kt
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

package avail.server.session

/**
 * `Link` is the wrapper for an object stored in the [FreeList]. It acts as a
 * node in a link list of available storage locations in the [FreeList].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property Value
 *   The type of objects stored in this [Link].
 * @property next
 *   The index location in the [FreeList.list] of the next empty location if
 *   [value] is `null` and this [Link] not being the immediate next available
 *   storage location indicated by the [FreeList.freeSpace]. It may be `null` if
 *   it is either the last available location not containing a [value] putting
 *   it in the location [FreeList.freeSpace] points to, or if the `value` is
 *   populated (not `null`).
 * @property value
 *   The `nullable` [Value] stored in this [Link]. If it is `null`, the space
 *   it resides in the [FreeList] is available as a storage location.
 *
 * @constructor
 * Construct a [Link].
 *
 * @param next
 *   The index location in the [FreeList.list] of the next empty location if
 *   [value] is `null` and this [Link] not being the immediate next available
 *   storage location indicated by the [FreeList.freeSpace]. It may be `null` if
 *   it is either the last available location not containing a [value] putting
 *   it in the location [FreeList.freeSpace] points to, or if the `value` is
 *   populated (not `null`).
 * @param value
 *   The `nullable` [Value] stored in this [Link]. If it is `null`, the space
 *   it resides in the [FreeList] is available as a storage location.
 */
internal class Link<Value> constructor(
	var next: Int? = null, var value: Value? = null)

/**
 * A `FreeList` is a generic data structure for dynamic memory allocation for
 * storing objects and providing them a local memory location id for quick
 * retrieval.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property Value
 *   The type of objects stored in this [FreeList].
 */
internal class FreeList<Value>
{
	/**
	 * The index into next empty index into the [list]; 'null' if the list is
	 * fully populated and must be extended if a new value is added.
	 */
	private var freeSpace: Int? = null

	/** The [List] that is the storage for the `Value`s. */
	private val list = mutableListOf<Link<Value>>()

	/**
	 * Remove the value at the provided index location. This location, if valid
	 * becomes the next [freeSpace].
	 *
	 * @param id
	 *   The location in this [FreeList] to remove this value from.
	 * @return
	 *   The removed `Value` or `null` if none.
	 */
	@Synchronized
	fun remove (id: Int): Value?
	{
		if (id >= list.size || id < 0)
		{
			// Log invalid
			return null
		}
		val link = list[id]
		// If this null it is already empty and no more work is required as it
		// is linked somewhere in the path of next nodes to be filled
		val value = link.value ?: return null
		link.value = null
		link.next = freeSpace
		freeSpace = id
		return value
	}

	/**
	 * Add the provided `Value` to this [FreeList] and answer its storage index.
	 *
	 * @param value
	 *   The `Value` to add.
	 * @return
	 *   The index that can be used to retrieve the value.
	 */
	@Synchronized
	fun add (value: Value): Int =
		freeSpace?.apply {
			val link = list[this]
			link.value = value
			freeSpace = link.next
			link.next = null
		} ?: run {
			val link = Link(null, value)
			val id = list.size
			list.add(link)
			id
		}

	/**
	 * Answer the `Value` at the provided [list] location.
	 *
	 * @param id
	 *   The index into this [FreeList] to retrieve data from.
	 * @return
	 *   A `Value` if one is available or `null` if either id is invalid or the
	 *   id points to a location with no `Value`.
	 */
	@Synchronized
	operator fun get(id: Int): Value?
	{
		if (id >= list.size || id < 0)
		{
			return null
		}
		return list[id].value
	}
}
