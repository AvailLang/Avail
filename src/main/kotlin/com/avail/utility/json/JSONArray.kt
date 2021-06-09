/*
 * JSONArray.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package com.avail.utility.json

/**
 * A `JSONArray` is produced by a [JSONReader] when an array is
 * read. Each element is a [JSONData].
 *
 * @property array
 *   The array of [JSONData].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [JSONArray].
 *
 * @param array
 *   The array of [JSONData]s. This must not be modified by the caller
 *   afterward; this call transfers ownership of the reference.
 */
@Suppress("unused")
class JSONArray internal constructor(
	private val array: Array<JSONData>) : JSONData(), Iterable<JSONData>
{
	override val isArray: Boolean get() = true

	/**
	 * Answer the length of the [receiver][JSONArray].
	 *
	 * @return
	 *   The length of the receiver.
	 */
	fun size(): Int = array.size

	/**
	 * Get a [JSONData] at the requested subscript.
	 *
	 * @param index
	 *   The array subscript.
	 * @return
	 *   The `JSONData` at the requested subscript.
	 * @throws ArrayIndexOutOfBoundsException
	 *   If the subscript is out of bounds.
	 */
	@Throws(ArrayIndexOutOfBoundsException::class)
	operator fun get(index: Int): JSONData = array[index]

	/**
	 * Get a `Boolean` at the requested subscript.
	 *
	 * @param index
	 *   The array subscript.
	 * @return
	 *   The `Boolean` at the requested subscript.
	 * @throws ArrayIndexOutOfBoundsException
	 *   If the subscript is out of bounds.
	 * @throws ClassCastException
	 *   If the element at the requested subscript is not a [JSONValue].
	 */
	@Throws(ArrayIndexOutOfBoundsException::class, ClassCastException::class)
	fun getBoolean(index: Int): Boolean = (array[index] as JSONValue).boolean

	/**
	 * Get a [JSONNumber] at the requested subscript.
	 *
	 * @param index
	 *   The array subscript.
	 * @return
	 *   The `JSONNumber` at the requested subscript.
	 * @throws ArrayIndexOutOfBoundsException
	 *   If the subscript is out of bounds.
	 * @throws ClassCastException
	 *   If the element at the requested subscript is not a `JSONNumber`.
	 */
	@Throws(ArrayIndexOutOfBoundsException::class, ClassCastException::class)
	fun getNumber(index: Int): JSONNumber = array[index] as JSONNumber

	/**
	 * Get a [String] at the requested subscript.
	 *
	 * @param index
	 *   The array subscript.
	 * @return
	 *   The `String` at the requested subscript.
	 * @throws ArrayIndexOutOfBoundsException
	 *   If the subscript is out of bounds.
	 * @throws ClassCastException
	 *   If the element at the requested subscript is not a [JSONValue].
	 */
	@Throws(ArrayIndexOutOfBoundsException::class, ClassCastException::class)
	fun getString(index: Int): String = (array[index] as JSONValue).string

	/**
	 * Get a [JSONArray] at the requested subscript.
	 *
	 * @param index
	 *   The array subscript.
	 * @return
	 *   The `JSONArray` at the requested subscript.
	 * @throws ArrayIndexOutOfBoundsException
	 *   If the subscript is out of bounds.
	 * @throws ClassCastException
	 *   If the element at the requested subscript is not a `JSONArray`.
	 */
	@Throws(ArrayIndexOutOfBoundsException::class, ClassCastException::class)
	fun getArray(index: Int): JSONArray = array[index] as JSONArray

	/**
	 * Get a [JSONObject] at the requested subscript.
	 *
	 * @param index
	 *   The array subscript.
	 * @return
	 *   The `JSONObject` at the requested subscript.
	 * @throws ArrayIndexOutOfBoundsException
	 *   If the subscript is out of bounds.
	 * @throws ClassCastException
	 *   If the element at the requested subscript is not a `JSONObject`.
	 */
	@Throws(ArrayIndexOutOfBoundsException::class, ClassCastException::class)
	fun getObject(index: Int): JSONObject = array[index] as JSONObject

	override fun iterator(): ListIterator<JSONData> =
		listOf(*array).listIterator()

	override fun writeTo(writer: JSONWriter)
	{
		writer.startArray()
		for (value in array)
		{
			value.writeTo(writer)
		}
		writer.endArray()
	}

	companion object
	{
		/** The canonical [emptySet][empty] [JSONArray].  */
		private val empty = JSONArray(arrayOf())

		/**
		 * Answer an emptySet [JSONArray].
		 *
		 * @return
		 *   The `JSONArray`.
		 */
		internal fun empty(): JSONArray
		{
			return empty
		}

		/**
		 * Answer a singleton [JSONArray].
		 *
		 * @param value
		 *   The sole [element][JSONData] of the `JSONArray`.
		 * @return
		 *   The `JSONArray`.
		 */
		internal fun singleton(value: JSONData): JSONArray =
			JSONArray(arrayOf(value))
	}
}
