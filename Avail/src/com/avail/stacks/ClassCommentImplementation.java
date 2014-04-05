/**
 * ClassCommentImplementation.java
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
 * A comment that describes a particular class.
 *
 * @author Richard Arriaga &lt;Rich@availlang.org&gt;
 */
public class ClassCommentImplementation extends AbstractCommentImplementation
{
	/**
	 * The {@link ArrayList} of the class's {@link StacksSuperTypeTag supertypes}
	 */
	final ArrayList<StacksSuperTypeTag> supertypes;

	/**
	 * The {@link ArrayList} of the class's {@link StacksFieldTag fields}
	 */
	final ArrayList<StacksFieldTag> fields;

	/**
	 * Construct a new {@link ClassCommentImplementation}.
	 *
	 * @param signature
	 * 		The {@link CommentSignature signature} of the class/method the
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
	 * @param supertypes
	 * 		The {@link ArrayList} of the class's
	 * 		{@link StacksSuperTypeTag supertypes}
	 * @param fields
	 * 		The {@link ArrayList} of the class's {@link StacksFieldTag fields}
	 */
	public ClassCommentImplementation (
		final CommentSignature signature,
		final int commentStartLine,
		final ArrayList<StacksAuthorTag> author,
		final ArrayList<StacksSeeTag> sees,
		final StacksDescription description,
		final ArrayList<StacksCategoryTag> categories,
		final ArrayList<StacksSuperTypeTag> supertypes,
		final ArrayList<StacksFieldTag> fields)
	{
		super(signature, commentStartLine, author, sees, description,
			categories);
		this.supertypes = supertypes;
		this.fields = fields;
	}

	@Override
	public void addToImplementationGroup(
		final ImplementationGroup implementationGroup)
	{
		implementationGroup.classImplemenataion(this);
	}

	@Override
	public String toHTML ()
	{
		final int fieldCount = fields.size();
		final StringBuilder stringBuilder = new StringBuilder()
			.append(signature.toHTML());

		if (categories.size() > 0)
		{
			stringBuilder.append(categories.get(0).toHTML());
		}

		final int listSize = supertypes.size();
		if (listSize > 0)
		{
			stringBuilder
				.append(tabs(1) + "<div class=\"MethodSectionContent\">\n")
				.append(tabs(2) + "<div class=\"SignatureHeading\">\n")
				.append(tabs(3) + "Supertypes: ");

			//Right now there is no link information for supertypes
			for (int i = 0; i < listSize - 1; i++)
			{
				stringBuilder.append(supertypes.get(i).toHTML()).append(", ");
			}
			stringBuilder.append(supertypes.get(listSize - 1).toHTML())
				.append("\n" + tabs(2) + "</div>\n");
		}

		stringBuilder.append(tabs(2) + "<div class=\"SignatureDescription\">\n")
			.append(tabs(3) + description.toHTML())
			.append("\n" + tabs(2) + "</div>\n");
		if (fieldCount > 0)
		{
			stringBuilder.append(tabs(5) + "<th class=\"IColLabelNarrow\" "
				+ "scope=\"col\">Name</th>\n");

			stringBuilder
				.append(tabs(2) + "<table>\n")
				.append(tabs(3) + "<thead>\n")
				.append(tabs(4) + "<tr>\n")
				.append(tabs(5) + "<th class=\"Transparent\" "
					+ "scope=\"col\"></th>\n")
				.append(tabs(5) + "<th class=\"IColLabelNarrow\" "
					+ "scope=\"col\">Type</th>\n"
					+ tabs(5) + "<th class=\"IColLabelWide\" scope=\"col\">"
					+ "Description</th>\n"
					+ tabs(4) + "</tr>\n"
					+ tabs(3) + "</thead>\n"
					+ tabs(3) + "<tbody>\n"
					+ tabs(4) + "<tr>\n"
					+ tabs(5) + "<th class=\"IRowLabel\" rowspan=\"")
				.append(fieldCount).append("\">Fields</th>\n"
					+ tabs(4) + "</tr>\n");

			for (final StacksFieldTag fieldTag : fields)
			{
				stringBuilder.append(fieldTag.toHTML());
			}
			stringBuilder.append(tabs(3) + "</tbody>\n")
				.append(tabs(2) + "</table>\n");
		}

		return stringBuilder.append(tabs(1) + "</div>\n").toString();
	}

	@Override
	public void addImplementationToExtendsModule (
		final A_String name,
		final StacksExtendsModule extendsModule)
	{
		//Do nothing as new implentations can't be introduced for classes

	}
}
