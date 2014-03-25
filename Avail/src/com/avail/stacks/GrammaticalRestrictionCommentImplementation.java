/**
 * GrammaticalRestrictionCommentImplementation.java
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
import java.util.TreeMap;

/**
 * A comment implementation of grammatical restrictions
 *
 * @author Richard Arriaga &lt;Rich@availlang.org&gt;
 */
public class GrammaticalRestrictionCommentImplementation extends
	AbstractCommentImplementation
{
	/**
	 * The forbids tag contents
	 */
	final TreeMap<Integer,StacksForbidsTag> forbids;

	/**
	 * Construct a new {@link GrammaticalRestrictionCommentImplementation}.
	 *
	 * @param signature
	 * 		The {@link CommentSignature signature} of the class/method the
	 * 		comment describes.
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
	 * @param forbids
	 * 		The forbids tag contents
	 */
	public GrammaticalRestrictionCommentImplementation (
		final CommentSignature signature,
		final int commentStartLine,
		final ArrayList<StacksAuthorTag> author,
		final ArrayList<StacksSeeTag> sees,
		final StacksDescription description,
		final ArrayList<StacksCategoryTag> categories,
		final TreeMap<Integer,StacksForbidsTag> forbids)
	{
		super(signature, commentStartLine, author, sees, description,
			categories);
		this.forbids = forbids;
	}

	@Override
	public void addToImplementationGroup(
		final ImplementationGroup implementationGroup)
	{
		implementationGroup.addGrammaticalRestriction(this);
	}

	/**
	 * Merge two {@linkplain GrammaticalRestrictionCommentImplementation}
	 * @param implementation
	 * 		The {@linkplain GrammaticalRestrictionCommentImplementation} to
	 * 		merge with
	 */
	public void mergeGrammaticalRestrictionImplementations(
		final GrammaticalRestrictionCommentImplementation implementation)
	{
		for (final Integer arity : implementation.forbids.keySet())
		{
			if(forbids.containsKey(arity))
			{
				forbids.get(arity).forbidMethods()
					.addAll(implementation.forbids.get(arity).forbidMethods());
			}
			else
			{
				forbids.put(arity,
					implementation.forbids.get(arity));
			}
		}
	}

	@Override
	public String toHTML ()
	{
		final StringBuilder stringBuilder = new StringBuilder();

		stringBuilder.append("<div class=\"MethodSectionHeader\">Grammatical "
			+ "restrictions:</div><div class=\"MethodSectionContent\">"
            + "<table><thead><tr><th style=\"white-space:nowrap\" "
            + "class=\"GColLabelNarrow\" scope=\"col\">Argument Position</th>"
            + "<th class=\"GColLabelWide\" scope=\"col\">Prohibited "
            + "Expression</th></tr></thead><tbody><tr>");

		for (final int arity : forbids.navigableKeySet())
		{
			stringBuilder.append(forbids.get(arity).toHTML());
		}

		stringBuilder.append("</tbody></table></div>");
		return stringBuilder.toString();
	}
}
