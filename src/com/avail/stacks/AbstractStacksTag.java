/**
 * AbstractStacksTag.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import com.avail.utility.json.JSONWriter;

/**
 * An Avail comment @ tag
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public abstract class AbstractStacksTag
{
	/**
	 * Creating a shared super class to move all Tags.
	 */

	/**
	 * Create HTML content from implementation
	 * @param htmlFileMap
	 * 		The map of all HTML files in Stacks
	 * @param hashID
	 * 		The hash portion of the id for linking to this element on page.
	 * @param errorLog The {@linkplain StacksErrorLog}
	 * @param position The ordered position of the parameter in the method
	 * 		signature.
	 * @return the HTML tagged content
	 */
	public abstract String toHTML(final LinkingFileMap htmlFileMap,
		final int hashID, final StacksErrorLog errorLog, int position);

	/**
	 * Create JSON content from implementation
	 * @param linkingFileMap
	 * 		The map of all the files in Stacks
	 * @param hashID
	 * 		The hash portion of the id for linking to this element on page.
	 * @param errorLog The {@linkplain StacksErrorLog}
	 * @param position The ordered position of the parameter in the method
	 * 		signature.
	 * @param jsonWriter The {@linkplain JSONWriter writer} used to build the
	 * 		document.
	 */
	public abstract void toJSON(final LinkingFileMap linkingFileMap,
		final int hashID, final StacksErrorLog errorLog, int position,
		JSONWriter jsonWriter);
}