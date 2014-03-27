/**
 * MethodCommentImplementation.java
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

/**
 * A comment that describes a particular method implementation
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class MethodCommentImplementation extends AbstractCommentImplementation
{
	/**
	 * The list of {@link StacksParameterTag parameters} of the method
	 * implementation.
	 */
	final ArrayList<StacksParameterTag> parameters;

	/**
	 * The {@link StacksReturnTag "@returns"} content
	 */
	final StacksReturnTag returnsContent;

	/**
	 *
	 */
	final ArrayList<StacksRaisesTag> exceptions;

	/**
	 * Construct a new {@link MethodCommentImplementation}.
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
	 * @param parameters
	 * 		The list of {@link StacksParameterTag parameters} of the method
	 * 		implementation.
	 * @param returnsContent
	 * 		The {@link StacksReturnTag "@returns"} content
	 * @param exceptions
	 * 		A {@link ArrayList} of any {@link StacksRaisesTag exceptions} the method
	 * 		throws.
	 */
	public MethodCommentImplementation (
		final MethodCommentSignature signature,
		final int commentStartLine,
		final ArrayList<StacksAuthorTag> author,
		final ArrayList<StacksSeeTag> sees,
		final StacksDescription description,
		final ArrayList<StacksCategoryTag> categories,
		final ArrayList<StacksParameterTag> parameters,
		final StacksReturnTag returnsContent,
		final ArrayList<StacksRaisesTag> exceptions)
	{
		super(signature, commentStartLine, author, sees, description,
			categories);
		this.parameters = parameters;
		this.returnsContent = returnsContent;
		this.exceptions = exceptions;
	}

	@Override
	public void addToImplementationGroup(
		final ImplementationGroup implementationGroup)
	{
		implementationGroup.addMethod(this);
	}

	@Override
	public String toHTML ()
	{
		final int paramCount = parameters.size();
		final int exceptionCount = exceptions.size();
		int colSpan = 1;
		final StringBuilder stringBuilder = new StringBuilder()
			.append(signature.toHTML());

		if (categories.size() > 0)
		{
			stringBuilder.append(categories.get(0).toHTML());
		}

		stringBuilder.append("<div class=\"SignatureDescription\">")
			.append(description.toHTML()).append("</div")
			.append("<table><thead><tr><th class=\"Transparent\" scope=\"col\">"
				+ "</th>");
		if (paramCount > 0)
		{
			stringBuilder.append("<th class=\"IColLabelNarrow\" "
				+ "scope=\"col\">Name</th>");
			colSpan = 2;
		}

		stringBuilder
			.append("<th class=\"IColLabelNarrow\" scope=\"col\">Type</th>"
				+ "<th class=\"IColLabelWide\" scope=\"col\">Description</th>"
				+ "</tr></thead><tbody><tr><th class=\"IRowLabel\" rowspan=\"")
			.append(paramCount).append("\">Parameters</th></tr>");

		for (final StacksParameterTag paramTag : parameters)
		{
			stringBuilder.append(paramTag.toHTML());
		}

		stringBuilder.append("<tr><th class=\"IRowLabel\" colspan=\"")
			.append(colSpan).append("\">Returns</th>")
			.append(returnsContent.toHTML());

		if (exceptionCount > 0)
		{
			stringBuilder.append("<th class=\"IRowLabel\" colspan=\"")
				.append(colSpan).append("\">Raises</th>");

			for (final StacksRaisesTag exception : exceptions)
			{
				stringBuilder.append(exception);
			}
		}

		return stringBuilder.toString();
	}

	@Override
	public void addImplementationToExtendsModule (
		final A_String name,
		final StacksExtendsModule extendsModule)
	{
		extendsModule.addMethodImplementation(name, this);
	}
}
