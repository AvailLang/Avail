/**
 * MethodCommentSignature.java
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

import java.util.ArrayList;

/**
 * The defining characteristic of a method comment as it pertains to the
 * implementation it describes.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class MethodCommentSignature extends CommentSignature
{
	/**
	 * The method parameter input types in order of input
	 */
	final ArrayList<String> orderedInputTypes;

	/**
	 * The return type, if none,⊤.
	 */
	final String returnType;

	/**
	 * Construct a new {@link MethodCommentSignature}.
	 *
	 * @param name
	 * 		The name of the class/method the comment describes.
	 * @param module
	 * 		The module this implementation appears in.
	 * @param orderedInputTypes
	 * 		The method parameter input types in order of input
	 * @param returnType
	 * 		The return type, if none,⊤.
	 */
	public MethodCommentSignature (
		final String name,
		final String module,
		final ArrayList<String> orderedInputTypes,
		final String returnType)
	{
		super(name, module);
		this.orderedInputTypes = orderedInputTypes;
		this.returnType = returnType;
	}

	@Override
	public String toString ()
	{
		return String.format("%s -> %s : %s", name(),
			orderedInputTypes.toString(),returnType);
	}

	@Override
	public String toHTML (final String nameOfGroup)
	{
		final StringBuilder stringBuilder = new StringBuilder()
			.append(tabs(2) + "<div "
				+ HTMLBuilder.tagClass(HTMLClass.classSignatureHeading)
				+ ">");

		final int listSize = orderedInputTypes.size();

		if (listSize > 0)
		{
			for (int i = 0; i < listSize - 1; i++)
			{
				stringBuilder.append(orderedInputTypes.get(i)
					.replace("<", "&lt;")).append(", ");
			}
			stringBuilder.append(orderedInputTypes.get(listSize - 1)
					.replace("<", "&lt;"))
				.append("</div>\n");
		}

		if (!name().equals(nameOfGroup))
		{
			stringBuilder.append(tabs(2) + "<div "
					+ HTMLBuilder.tagClass(HTMLClass.classModuleLocation)
					+ "><em>Source</em>: ")
				.append(module()).append(": <strong>")
				.append(name().replace("<", "&lt;"))
				.append("</strong></div>\n");
		}
		else
		{
			stringBuilder.append(tabs(2) + "<div "
					+ HTMLBuilder.tagClass(HTMLClass.classModuleLocation)
					+ "><em>Source</em>: ")
				.append(module())
				.append("</div>\n");
		}

		return stringBuilder.toString();
	}
}
