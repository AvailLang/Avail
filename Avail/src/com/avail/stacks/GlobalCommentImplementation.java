/**
 * GlobalCommentImplementation.java
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
import com.avail.descriptor.A_String;
import com.avail.descriptor.StringDescriptor;

/**
 * A module global variable comment
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class GlobalCommentImplementation extends AbstractCommentImplementation
{
	/**
	 * A global module variable comment tag
	 */
	final StacksGlobalTag globalTag;

	/**
	 * The hash id for this implementation
	 */
	final private int hashID;

	/**
	 * Construct a new {@link GlobalCommentImplementation}.
	 *
	 * @param signature
	 * 		The {@link MethodCommentSignature signature} of the class/method the
	 * 		comment describes.
	 * @param commentStartLine
	 * 		The start line in the module the comment being parsed appears.
	 * @param author
	 * 		The {@link StacksAuthorTag author} of the implementation.
	 * @param sees
	 * 		A {@link ArrayList} of any {@link StacksSeeTag "@sees"} references.
	 * @param description
	 * 		The overall description of the implementation
	 * @param categories
	 * 		The categories the implementation appears in
	 * @param aliases
	 * 		The aliases the implementation
	 * @param globalTag
	 * 		A global module variable comment tag
	 */
	public GlobalCommentImplementation (
		final CommentSignature signature,
		final int commentStartLine,
		final ArrayList<StacksAuthorTag> author,
		final ArrayList<StacksSeeTag> sees,
		final StacksDescription description,
		final ArrayList<StacksCategoryTag> categories,
		final ArrayList<StacksAliasTag> aliases,
		final StacksGlobalTag globalTag)
	{
		super(signature, commentStartLine, author, sees, description,
			categories,aliases);
		this.globalTag = globalTag;

		this.hashID = StringDescriptor.from(
			signature.name()).hash();
	}

	@Override
	public void addToImplementationGroup(
		final ImplementationGroup implementationGroup)
	{
		implementationGroup.global(this);
	}

	@Override
	public String toHTML (final HTMLFileMap htmlFileMap,
		final String nameOfGroup, final StacksErrorLog errorLog)
	{
		final StringBuilder stringBuilder = new StringBuilder()
		.append(signature().toHTML(nameOfGroup));

		if (categories.size() > 0)
		{
			stringBuilder.append(categories.get(0).toHTML(htmlFileMap, hashID,
				errorLog));
		}

		stringBuilder.append(tabs(1) + "<div "
				+ HTMLBuilder.tagClass(HTMLClass.classSignatureDescription)
				+ ">")
			.append(description.toHTML(htmlFileMap, hashID, errorLog))
			.append("</div>\n");

		return stringBuilder.toString();
	}

	@Override
	public void addImplementationToImportModule (
		final A_String name, final StacksImportModule importModule)
	{
		//Do nothing as globals will never be defined outside of its module.
	}
}
