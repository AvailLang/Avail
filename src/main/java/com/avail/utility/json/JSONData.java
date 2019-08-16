/*
 * JSONData.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.utility.json;

/**
 * {@code JSONData} is the superclass of {@link JSONValue}, {@link JSONNumber},
 * {@link JSONArray}, and {@link JSONObject}. A {@link JSONReader} {@linkplain
 * JSONReader#read() reads} a single {@code JSONData}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class JSONData
implements JSONFriendly
{
	/**
	 * Is the {@linkplain JSONData receiver} a JSON null?
	 *
	 * @return {@code true} if the receiver is a JSON null, {@code false}
	 *         otherwise.
	 */
	public boolean isNull ()
	{
		return false;
	}

	/**
	 * Is the {@linkplain JSONData receiver} a JSON boolean?
	 *
	 * @return {@code true} if the receiver is a JSON boolean, {@code false}
	 *         otherwise.
	 */
	public boolean isBoolean ()
	{
		return false;
	}

	/**
	 * Is the {@linkplain JSONData receiver} a {@linkplain JSONNumber JSON
	 * number}?
	 *
	 * @return {@code true} if the receiver is a JSON number, {@code false}
	 *         otherwise.
	 */
	public boolean isNumber ()
	{
		return false;
	}

	/**
	 * Is the {@linkplain JSONData receiver} a JSON string?
	 *
	 * @return {@code true} if the receiver is a JSON string, {@code false}
	 *         otherwise.
	 */
	public boolean isString ()
	{
		return false;
	}

	/**
	 * Is the {@linkplain JSONData receiver} a {@linkplain JSONArray JSON
	 * array}?
	 *
	 * @return {@code true} if the receiver is a JSON array, {@code false}
	 *         otherwise.
	 */
	public boolean isArray ()
	{
		return false;
	}

	/**
	 * Is the {@linkplain JSONData receiver} a {@linkplain JSONObject JSON
	 * object}?
	 *
	 * @return {@code true} if the receiver is a JSON object, {@code false}
	 *         otherwise.
	 */
	public boolean isObject ()
	{
		return false;
	}

	/** The sole JSON {@code null}. */
	private static final JSONData jsonNull = new JSONData()
	{
		@Override
		public boolean isNull()
		{
			return true;
		}

		@Override
		public void writeTo (final JSONWriter writer)
		{
			writer.writeNull();
		}
	};

	/**
	 * Answer a JSON {@code null}.
	 *
	 * @return A JSON {code null}.
	 */
	public static JSONData jsonNull ()
	{
		return jsonNull;
	}
}
