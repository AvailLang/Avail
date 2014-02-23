/**
 * AvailDocumentationJSONObject.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.stacks;

import java.sql.Timestamp;

/**
 * A full string representation of the JSON object.
 *
 * @author Richard A Arriaga &lt;rich@availlang.org&gt;
 */
public final class AvailDocumentationJSONObject
{

	/**
	 *
	 * Construct a new {@link AvailDocumentationJSONObject}.
	 *
	 * @param jsonObjectString The string representing the JSON object
	 */
	public AvailDocumentationJSONObject (final String jsonObjectString)
	{
		this.created = new Timestamp(new java.util.Date().getTime());
		this.jsonObjectString = new StringBuilder('{')
			.append(jsonObjectString)
			.append('}')
			.toString();
	}

	/**
	 * The timestamp that indicates when {@link AvailDocumentationJSONObject}
	 * was created.
	 */
	final private Timestamp created;

	/**
	 * The string representing the json object
	 */
	final private String jsonObjectString;

	/**
	 * Get method for {@link AvailDocumentationJSONObject} created
	 * @return Timestamp when object was created.
	 */
	public Timestamp getCreated()
	{
		return created;
	}

	/**
	 * Get method for {@link AvailDocumentationJSONObject} jsonObjectString
	 * @return the JSON string body
	 */
	public String getJsonObjectString()
	{
		return jsonObjectString;
	}
}
