/**
 * CharacterInfo.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.tools.unicode;

import com.avail.utility.json.JSONArray;
import com.avail.utility.json.JSONData;
import com.avail.utility.json.JSONFriendly;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;

import static java.lang.String.format;

/**
 * A {@code CharacterInfo} collects information about a Unicode code point.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class CharacterInfo
implements Comparable<CharacterInfo>, JSONFriendly
{
	/** The Unicode code point. */
	private final int codePoint;

	/**
	 * Answer the Unicode code point.
	 *
	 * @return The Unicode code point.
	 */
	public int codePoint ()
	{
		return codePoint;
	}

	/**
	 * Construct a new {@link CharacterInfo}.
	 *
	 * @param codePoint
	 *        The Unicode code point.
	 */
	public CharacterInfo (final int codePoint)
	{
		this.codePoint = codePoint;
	}

	@Override
	public boolean equals (final @Nullable Object obj)
	{
		if (obj instanceof CharacterInfo)
		{
			return codePoint == ((CharacterInfo) obj).codePoint;
		}
		return false;
	}

	@Override
	public int hashCode ()
	{
		return 49831 * codePoint;
	}

	@Override
	public int compareTo (final @Nullable CharacterInfo o)
	{
		assert o != null;
		return Integer.compare(codePoint, o.codePoint);
	}

	/**
	 * Answer a {@link String} that contains only the UTF-16 encoding of the
	 * code point.
	 *
	 * @return A {@code String} containing the character.
	 */
	public String character ()
	{
		return new String(Character.toChars(codePoint));
	}

	/**
	 * Answer the Unicode name of the code point.
	 *
	 * @return The Unicode name of the code point.
	 */
	public @Nullable String unicodeName ()
	{
		return Character.getName(codePoint);
	}

	/**
	 * Answer the decimal coding of the HTML entity corresponding to the code
	 * point.
	 *
	 * @return The HTML entity.
	 */
	public String htmlEntityDecimal ()
	{
		return format("&#%d;", codePoint);
	}

	/**
	 * Answer the hexadecimal coding of the HTML entity corresponding to the
	 * code point.
	 *
	 * @return The HTML entity.
	 */
	public String htmlEntityHexadecimal ()
	{
		return format("&#x%x;", codePoint);
	}

	/** The HTML entity name of the code point. */
	@Nullable String htmlEntityName;

	/**
	 * Answer the HTML entity name of the code point.
	 *
	 * @return The HTML entity.
	 */
	public @Nullable String htmlEntityName ()
	{
		return htmlEntityName;
	}

	/** Was information previously fetched for this code point? */
	private transient boolean previouslyFetched = false;

	/**
	 * Was information previously fetched for this code point?
	 *
	 * @return {@code} if information for this code point was previously
	 *         fetched from a (reasonably) authoritative source, {@code false}
	 *         otherwise.
	 */
	public boolean previouslyFetched ()
	{
		return previouslyFetched;
	}

	/**
	 * Decode a {@link CharacterInfo} from the specified {@link JSONData}.
	 *
	 * @param data
	 *        A {@code JSONData}.
	 * @return A {@code CharacterInfo}.
	 */
	public static CharacterInfo readFrom (final JSONData data)
	{
		final JSONArray array = (JSONArray) data;
		final CharacterInfo info =
			new CharacterInfo(array.getNumber(0).getInt());
		info.previouslyFetched = true;
		info.htmlEntityName = array.get(5).isNull()
			? null
			: array.getString(5);
		return info;
	}

	@Override
	public void writeTo (final JSONWriter writer)
	{
		writer.startArray();
		writer.write(codePoint);
		writer.write(character());
		writer.write(unicodeName());
		writer.write(htmlEntityDecimal());
		writer.write(htmlEntityHexadecimal());
		writer.write(htmlEntityName);
		writer.endArray();
	}

	@Override
	public String toString ()
	{
		return format("U+%04x", codePoint);
	}
}
