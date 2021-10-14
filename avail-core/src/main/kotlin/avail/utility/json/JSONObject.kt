/*
 * JSONObject.kt
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

package avail.utility.json

import kotlin.collections.Map.Entry

/**
 * A `JSONObject` is produced by a [JSONReader] when an object is
 * read. Each key of the object is a [String] and each value is a [JSONData].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property map
 *   The field assignments of the [JSONObject] as a [map][Map] from
 *   [key][String]s to [value][JSONData]s.
 *
 * @constructor
 *
 * Construct a new [JSONObject].
 *
 * @param map
 *   The field assignments of the [JSONObject] as a [Map] from
 *   [key][String]s to [value][JSONData]s. This must not be modified by the
 *   caller afterward; this call transfers ownership of the reference.
 */
class JSONObject internal constructor(
	private val map: Map<String, JSONData>) : JSONData(),
	Iterable<Entry<String, JSONData>>
{
	override val isObject: Boolean
		get() = true

	/**
	 * Does the `JSONObject` include a binding for the specified key?
	 *
	 * @param k
	 *   The key.
	 * @return
	 *   `true` if the `JSONObject` includes a binding for the key, `false`
	 *   otherwise.
	 */
	fun containsKey(k: String): Boolean = map.containsKey(k)

	/**
	 * Get a [JSONData] associated with requested key.
	 *
	 * @param k
	 *   The key.
	 * @return
	 *   The `JSONData` associated with requested key.
	 * @throws NullPointerException
	 *   If the requested key is not present.
	 */
	operator fun get(k: String): JSONData =
		map[k] ?: throw NullPointerException()

	/**
	 * Get a `Boolean` associated with requested key.
	 *
	 * @param k
	 *   The key.
	 * @return
	 *   The `Boolean` associated with requested key.
	 * @throws NullPointerException
	 *   If the requested key is not present.
	 * @throws ClassCastException
	 *   If the value associated with the requested key is not a [JSONValue].
	 */
	@Throws(NullPointerException::class, ClassCastException::class)
	fun getBoolean(k: String): Boolean = (get(k) as JSONValue).boolean

	/**
	 * Get a [JSONNumber] associated with requested key.
	 *
	 * @param k
	 *   The key.
	 * @return
	 *   The `JSONNumber` associated with requested key.
	 * @throws NullPointerException
	 *   If the requested key is not present.
	 * @throws ClassCastException
	 *   If the value associated with the requested key is not a `JSONNumber`.
	 */
	@Throws(NullPointerException::class, ClassCastException::class)
	fun getNumber(k: String): JSONNumber = get(k) as JSONNumber

	/**
	 * Get a [String] associated with requested key.
	 *
	 * @param k
	 *   The key.
	 * @return
	 *   The `String` associated with requested key.
	 * @throws NullPointerException
	 *   If the requested key is not present.
	 * @throws ClassCastException
	 *   If the value associated with the requested key is not a [JSONValue].
	 */
	@Throws(NullPointerException::class, ClassCastException::class)
	fun getString(k: String): String = (get(k) as JSONValue).string

	/**
	 * Get a [JSONArray] associated with requested key.
	 *
	 * @param k
	 *   The key.
	 * @return
	 *   The `JSONArray` associated with requested key.
	 * @throws NullPointerException
	 *   If the requested key is not present.
	 * @throws ClassCastException
	 *   If the value associated with the requested key is not a `JSONArray`.
	 */
	@Throws(NullPointerException::class, ClassCastException::class)
	fun getArray(k: String): JSONArray = get(k) as JSONArray

	/**
	 * Get a [JSONObject] associated with requested key.
	 *
	 * @param k
	 *   The key.
	 * @return
	 *   The `JSONObject` associated with requested key.
	 * @throws NullPointerException
	 *   If the requested key is not present.
	 * @throws ClassCastException
	 *   If the value associated with the requested key is not a `JSONObject`.
	 */
	@Throws(NullPointerException::class, ClassCastException::class)
	fun getObject(k: String): JSONObject = get(k) as JSONObject

	override fun iterator(): Iterator<Entry<String, JSONData>> =
		map.entries.iterator()

	override fun writeTo(writer: JSONWriter) =
		writer.writeObject {
			map.forEach { (key, value) ->
				at(key) { value.writeTo(writer) }
			}
		}

	companion object
	{
		/** The sole empty `JSONObject`. */
		val empty = JSONObject(emptyMap())
	}
}
