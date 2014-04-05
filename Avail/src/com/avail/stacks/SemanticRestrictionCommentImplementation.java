/**
 * SemanticRestrictionCommentImplementation.java
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
 * A comment implementation of grammatical restrictions
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class SemanticRestrictionCommentImplementation extends
	AbstractCommentImplementation
{
	/**
	 *  The list of input types in the semantic restriction.
	 */
	final ArrayList<StacksRestrictsTag> restricts;

	/**
	 * The {@link StacksReturnTag "@returns"} content
	 */
	final ArrayList<StacksReturnTag> returnsContent;

	/**
	 * Construct a new {@link SemanticRestrictionCommentImplementation}.
	 *
	 * @param signature
	 * 		The {@link SemanticRestrictionCommentSignature signature} of the
	 * 		class/method the comment describes.
	 * @param commentStartLine
	 * 		The start line in the module the comment being parsed appears.
	 * @param author
	 * 		The {@link StacksAuthorTag authors} of the implementation.
	 * @param sees
	 * 		A {@link ArrayList} of any {@link StacksSeeTag "@sees"} references.
	 * @param description
	 * 		The overall description of the implementation
	 * @param categories
	 * 		The categories the implementation appears in
	 * @param restricts
	 * 		The list of input types in the semantic restriction.
	 * @param returnsContent
	 * 		The {@link StacksReturnTag "@returns"} content
	 */
	public SemanticRestrictionCommentImplementation (
		final SemanticRestrictionCommentSignature signature,
		final int commentStartLine,
		final ArrayList<StacksAuthorTag> author,
		final ArrayList<StacksSeeTag> sees,
		final StacksDescription description,
		final ArrayList<StacksCategoryTag> categories,
		final ArrayList<StacksRestrictsTag> restricts,
		final ArrayList<StacksReturnTag> returnsContent)
	{
		super(signature, commentStartLine, author, sees, description,
			categories);
		this.restricts = restricts;
		this.returnsContent = returnsContent;
	}

	@Override
	public void addToImplementationGroup(
		final ImplementationGroup implementationGroup)
	{
		implementationGroup.addSemanticRestriction(this);
	}

	@Override
	public String toHTML ()
	{
		final int paramCount = restricts.size();
		final int colSpan = 1;
		final StringBuilder stringBuilder = new StringBuilder()
			.append(signature.toHTML());

		stringBuilder.append(tabs(2) + "<div class=\"SignatureDescription\">\n")
			.append(tabs(3) + description.toHTML())
			.append("\n" + tabs(2) + "</div>\n")
			.append(tabs(2) + "<table>\n")
			.append(tabs(3) + "<thead>\n")
			.append(tabs(4) + "<tr>\n")
			.append(tabs(5) + "<th class=\"Transparent\" scope=\"col\">"
				+ "</th>\n");

		stringBuilder
			.append(tabs(5) + "<th class=\"IColLabelNarrow\" scope=\"col\">"
				+ "Type</th>\n")
			.append(tabs(5) + "<th class=\"IColLabelWide\" scope=\"col\">"
				+ "Description</th>\n")
			.append(tabs(4) + "</tr>\n")
			.append(tabs(3) + "</thead>\n")
			.append(tabs(3) + "<tbody>\n");

		if (paramCount > 0)
		{
			stringBuilder
				.append(tabs(4) + "<tr>\n")
				.append(tabs(5) + "<th class=\"IRowLabel\" rowspan=\"")
			.append(paramCount + 1).append("\">Parameter Types</th>\n")
			.append(tabs(4) + "</tr>\n");
		}

		for (final StacksRestrictsTag restrictsTag : restricts)
		{
			stringBuilder.append(restrictsTag.toHTML());
		}

		if (!returnsContent.isEmpty())
		{
			stringBuilder.append(tabs(4) + "<tr>\n")
			.append(tabs(5) + "<th class=\"IRowLabel\" colspan=\"")
				.append(colSpan).append("\">Returns</th>\n")
				.append(returnsContent.get(0).toHTML());
		}

		return stringBuilder.append(tabs(3) + "</tbody>\n")
			.append(tabs(2) + "</table>\n").toString();
	}

	@Override
	public void addImplementationToExtendsModule (
		final A_String name,
		final StacksExtendsModule extendsModule)
	{
		extendsModule.addSemanticImplementation(name, this);
	}
}
