/*
 * CharacterInfo.kt
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

package avail.tools.unicode

import avail.utility.json.JSONArray
import avail.utility.json.JSONData
import avail.utility.json.JSONFriendly
import avail.utility.json.JSONWriter

import java.lang.String.format

/**
 * A `CharacterInfo` collects information about a Unicode code point.
 *
 * @property codePoint
 *   The Unicode code point.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [CharacterInfo].
 *
 * @param codePoint
 *   The Unicode code point.
 */
internal class CharacterInfo constructor(internal val codePoint: Int)
	: Comparable<CharacterInfo>, JSONFriendly
{
	/** The HTML entity name of the code point. */
	var htmlEntityName: String? = null

	/**
	 * `true` if information for this code point was previously fetched from a
	 * (reasonably) authoritative source, `false` otherwise.
	 */
	internal var previouslyFetched = false

	override fun equals(other: Any?) =
		if (other is CharacterInfo) codePoint == other.codePoint else false

	override fun hashCode() = 49831 * codePoint

	override fun compareTo(other: CharacterInfo) =
		codePoint.compareTo(other.codePoint)

	/** A [String] that contains only the UTF-16 encoding of the code point. */
	private val character: String
		get() = String(Character.toChars(codePoint))

	/** The Unicode name of the code point. */
	private val unicodeName: String
		get() = Character.getName(codePoint)

	/**
	 * The decimal coding of the HTML entity corresponding to the code point.
	 */
	private val htmlEntityDecimal: String
		get() = format("&#%d;", codePoint)

	/**
	 * Answer the hexadecimal coding of the HTML entity corresponding to the
	 * code point.
	 *
	 * @return The HTML entity.
	 */
	private val htmlEntityHexadecimal: String
		get() = format("&#x%x;", codePoint)

	override fun writeTo(writer: JSONWriter) =
		writer.writeArray {
			write(codePoint)
			write(character)
			write(unicodeName)
			write(htmlEntityDecimal)
			write(htmlEntityHexadecimal)
			write(htmlEntityName)
		}

	override fun toString(): String = format("U+%04x", codePoint)

	companion object
	{
		/**
		 * Decode a [CharacterInfo] from the specified [JSONData].
		 *
		 * @param data
		 *   A `JSONData`.
		 * @return
		 *   A `CharacterInfo`.
		 */
		fun readFrom(data: JSONData): CharacterInfo
		{
			val array = data as JSONArray
			val info = CharacterInfo(array.getNumber(0).int)
			info.previouslyFetched = true
			info.htmlEntityName =
				if (array[5].isNull)
					null
				else
					array.getString(5)
			return info
		}
	}
}
