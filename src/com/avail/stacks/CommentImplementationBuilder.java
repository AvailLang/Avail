/**
 * CommentImplementationBuilder.java
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

import java.util.ArrayList;
import java.util.TreeMap;

/**
 * A builder class for an {@link AbstractCommentImplementation}.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class CommentImplementationBuilder
{
	/**
	 * The available {@linkplain LinkingFileMap}
	 */
	private LinkingFileMap linkingFileMap;

	/**
	 * The alias keyword provides alias to which the method/macro is referred
	 * to by
	 */
	private final ArrayList<StacksAliasTag> aliases;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @param htmlFileMap
	 * 		A map containing HTML file links in stacks
	 * 		A map containing HTML file links in stacks
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksAliasTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens,
		 final LinkingFileMap htmlFileMap)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final ArrayList<QuotedStacksToken> tempTokens =
			new ArrayList<QuotedStacksToken>();

		linkingFileMap = htmlFileMap;

		for (final AbstractStacksToken token : tagContentTokens)
		{
			try
			{
				tempTokens.add((QuotedStacksToken) token);
			}
			catch (final ClassCastException e)
			{
				final String errorMessage = String.format("\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @category "
					+ "tag section; expected a series of quoted category "
					+ "names immediately following the @category tag, however "
					+ "does not start with a quoted category is listed.</li>",
					moduleLeafName,
					commentStartLine());
				throw new StacksCommentBuilderException(errorMessage, this);
			}
		}
		if (!tempTokens.isEmpty())
		{
			aliases.clear();
		}
		aliases.add(new StacksAliasTag (tempTokens));
	}

	/**
	 * The author keyword indicates the method implementation author.
	 */
	private final ArrayList<StacksAuthorTag> authors;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 */
	public void addStacksAuthorTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException
	{
		authors.add(new StacksAuthorTag (tagContentTokens));
	}


	/**
	 * The category keyword provides a category to which the method
	 * implementation belongs.
	 */
	private final ArrayList<StacksCategoryTag> categories;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @param htmlFileMap
	 * 		A map containing HTML file links in stacks
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksCategoryTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens,
		 final LinkingFileMap htmlFileMap)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final ArrayList<QuotedStacksToken> tempTokens =
			new ArrayList<QuotedStacksToken>();

		linkingFileMap = htmlFileMap;

		for (final AbstractStacksToken token : tagContentTokens)
		{
			try
			{
				tempTokens.add((QuotedStacksToken) token);
			}
			catch (final ClassCastException e)
			{
				final String errorMessage = String.format("\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @category "
					+ "tag section; expected a series of quoted category "
					+ "names immediately following the @category tag, however "
					+ "does not start with a quoted category is listed.</li>",
					moduleLeafName,
					commentStartLine());
				throw new StacksCommentBuilderException(errorMessage, this);
			}
		}
		if (!tempTokens.isEmpty())
		{
			categories.clear();
		}
		categories.add(new StacksCategoryTag (tempTokens));
	}

	/**
	 * The general description provided by the comment
	 */
	private StacksDescription description;

	/**
	 * Get the {@linkplain StacksDescription description}, if it is null, create
	 * an empty one.
	 * @return
	 */
	private StacksDescription description()
	{
		if (description == null)
		{
			description =
				new StacksDescription(new ArrayList<AbstractStacksToken>());
		}
		return description;
	}

	/**
	 * @param list
	 * 		List of {@linkplain AbstractStacksToken tokens} that make up the
	 * 		description
	 * @throws ClassCastException
	 */
	public void addStacksCommentDescription(
		 final ArrayList<AbstractStacksToken> list)
			 throws ClassCastException
	{
		description = new StacksDescription(list);
	}

	/**
	 * The field keyword indicates a field in the class implementation.
	 */
	private final ArrayList<StacksFieldTag> fields;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksFieldTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final int tokenCount = tagContentTokens.size();

		if (tokenCount < 2)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed @field tag section; "
				+ "too few components.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		final QuotedStacksToken tempName;
		try
		{
			tempName = (QuotedStacksToken) tagContentTokens.get(0);
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed @field "
				+ "tag section; expected quoted field name.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		final QuotedStacksToken tempType;
		try
		{
			tempType = (QuotedStacksToken) tagContentTokens.get(1);
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed "
				+ "@field tag section; expected a quoted field type\n.",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		if (tokenCount == 2)
		{
			fields.add(new StacksFieldTag (tempName, tempType,
				new StacksDescription(new ArrayList<AbstractStacksToken>(0))));
		}
		else
		{
			final ArrayList<AbstractStacksToken> rest =
				new ArrayList<AbstractStacksToken>(
					tagContentTokens.subList(2, tokenCount));
			fields.add(new StacksFieldTag (tempName, tempType,
				new StacksDescription(rest)));
		}
	}

	/**
	 * The forbids keyword indicates the methods forbidden by a
	 * Grammatical Restriction for the method implementation.
	 */
	private final TreeMap<Integer,StacksForbidsTag> forbids;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksForbidTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		int arity;
		try
		{
			arity = Integer.parseInt(tagContentTokens.get(0).lexeme);
		}
		catch(final NumberFormatException e)
		{
				final String errorMessage = String.format("\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed "
					+ "@forbids tag section; expected a number immediately "
					+ "the @forbids tag, but did not receive one.</li>",
					moduleLeafName,
					commentStartLine());
				throw new StacksCommentBuilderException(errorMessage, this);
		}

		try
		{
			final ArrayList<QuotedStacksToken> tempTokens =
				new ArrayList<QuotedStacksToken>(1);

			for (int i =1 ; i < tagContentTokens.size(); i++)
			{
				tempTokens.add((QuotedStacksToken) tagContentTokens.get(i));
			}

			forbids.put(arity,
				new StacksForbidsTag (tagContentTokens.get(0), tempTokens));
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed "
				+ "@forbids tag section; expected a series of quoted method "
				+ "names immediately following the @forbids tag arity number, "
				+ "however the first argument following the @forbids tag is "
				+ "not quoted.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}
	}

	/**
	 * The globals keyword indicates the name of the method implementation.
	 */
	private final ArrayList<StacksGlobalTag> globalVariables;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksGlobalTag (
		final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final int tokenCount = tagContentTokens.size();

		if (tokenCount < 2)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed "
				+ "@global tag section; has too few components.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		final QuotedStacksToken tempName;
		try
		{
			tempName = (QuotedStacksToken) tagContentTokens.get(0);
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed "
				+ "@global tag section; expected a quoted module variable name "
				+ "immediately following the @global tag, however no such "
				+ "quoted name is listed.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		final QuotedStacksToken tempType;
		try
		{
			tempType = (QuotedStacksToken) tagContentTokens.get(1);
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed "
				+ "@global tag section; expected a quoted type "
				+ "immediately following the @global tag, however no such "
				+ "quoted type is listed.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}
			globalVariables.add(new StacksGlobalTag (tempName, tempType));
	}

	/**
	 * The method keyword indicates the name of the method implementation.
	 */
	private final ArrayList<StacksMethodTag> methods;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksMethodTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		if (tagContentTokens.size() == 1)
		{
			try
			{
				methods.add(new StacksMethodTag (
					(QuotedStacksToken) tagContentTokens.get(0)));
			}
			catch (final ClassCastException e)
			{
				final String errorMessage = String.format("\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @method "
					+ "tag section; expected a quoted method name immediately "
					+ "following the @method tag.</li>",
					moduleLeafName,
					commentStartLine());
				throw new StacksCommentBuilderException(errorMessage, this);
			}
		}
		else
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed @method tag section; "
				+ "has wrong # of @method components.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}
	}

	/**
	 * The macro keyword indicates the name of the macro implementation.
	 */
	private final ArrayList<StacksMacroTag> macros;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksMacroTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		if (tagContentTokens.size() == 1)
		{
			try
			{
				macros.add(new StacksMacroTag (
					(QuotedStacksToken) tagContentTokens.get(0)));
			}
			catch (final ClassCastException e)
			{
				final String errorMessage = String.format("\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @macro "
					+ "tag section; expected a quoted method name immediately "
					+ "following the @macro tag.</li>",
					moduleLeafName,
					commentStartLine());
				throw new StacksCommentBuilderException(errorMessage, this);
			}
		}
		else
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed @macro tag section; "
				+ "has wrong # of @macro components.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}
	}

	/**
	 * The parameter keyword indicates an input for the method
	 * implementation.
	 */
	private final ArrayList<StacksParameterTag> parameters;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksParameterTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final int tokenCount = tagContentTokens.size();
		if (tokenCount < 2)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed @param tag section; "
				+ " has too few @param components.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		final QuotedStacksToken tempName;
		try
		{
			tempName = (QuotedStacksToken) tagContentTokens.get(0);
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed @param tag section; "
				+ "expected a quoted parameter type/name.",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		final QuotedStacksToken tempType;
		try
		{
			tempType = (QuotedStacksToken) tagContentTokens.get(1);
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed "
				+ "@param tag section; expected a quoted parameter type/name.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		if (tokenCount == 2)
		{
			parameters.add(new StacksParameterTag (tempName, tempType,
				new StacksDescription(new ArrayList<AbstractStacksToken>(0))));
		}
		else
		{
			final ArrayList<AbstractStacksToken> rest =
				new ArrayList<AbstractStacksToken>(
					tagContentTokens.subList(2, tokenCount));
			parameters.add(new StacksParameterTag (tempName, tempType,
				new StacksDescription(rest)));
		}
	}

	/**
	 * The raises keyword indicates the exceptions thrown by the method
	 * implementation.
	 */
	private final ArrayList<StacksRaisesTag> raises;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksRaisesTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final int tokenCount = tagContentTokens.size();


		if (tokenCount < 1)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed @raises tag section; "
				+ "has too few components.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		final QuotedStacksToken tempName;
		try
		{
			tempName = (QuotedStacksToken) tagContentTokens.get(0);
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed "
				+ "@raises tag section; expected a quoted exception type "
				+ "immediately following the @raises tag, however no such "
				+ "quoted exception type is listed.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		if (tokenCount == 1)
		{
			raises.add(new StacksRaisesTag (tempName,
				new StacksDescription(new ArrayList<AbstractStacksToken>(0))));
		}
		else
		{
			final ArrayList<AbstractStacksToken> rest =
				new ArrayList<AbstractStacksToken>(
					tagContentTokens.subList(1, tokenCount));
			raises.add(new StacksRaisesTag (tempName,
				new StacksDescription(rest)));
		}
	}


	/**
	 * The restricts keyword indicates the input types used by the method
	 * implementation's semantic restriction.
	 */
	private final ArrayList<StacksRestrictsTag> restricts;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksRestrictsTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final int tokenCount = tagContentTokens.size();

		if (tokenCount < 1)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed @restricts "
				+ "tag section; has too few components.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		final QuotedStacksToken tempName;
			try
		{
			tempName = (QuotedStacksToken) tagContentTokens.get(0);
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed "
				+ "@restricts tag section; expected a quoted type "
				+ "immediately following the @restricts tag, however no such "
				+ "quoted type is listed.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		if (tokenCount == 1)
		{
			restricts.add(new StacksRestrictsTag (tempName,
				new StacksDescription(new ArrayList<AbstractStacksToken>(0))));
		}
		else
		{
			final ArrayList<AbstractStacksToken> rest =
				new ArrayList<AbstractStacksToken>(
					tagContentTokens.subList(1, tokenCount));
			restricts.add(new StacksRestrictsTag (tempName,
				new StacksDescription(rest)));
		}
	}

	/**
	 * The returns keyword indicates the output for the method
	 * implementation.
	 */
	private final ArrayList<StacksReturnTag> returns;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksReturnsTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		final int tokenCount = tagContentTokens.size();

		if (tokenCount < 1)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed "
				+ "@returns tag section; has too few components.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		final RegionStacksToken tempName;
		try
		{
			tempName =(RegionStacksToken) tagContentTokens.get(0);
		}
		catch (final ClassCastException e)
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed @returns "
				+ "tag section; expected a quoted return type immediately  "
				+ "following the @returns tag, however no such quoted "
				+ "return type is identifiable.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		if (tokenCount == 1)
		{
			returns.add(new StacksReturnTag (tempName,
				new StacksDescription(new ArrayList<AbstractStacksToken>(0))));
		}
		else
		{
			final ArrayList<AbstractStacksToken> rest =
				new ArrayList<AbstractStacksToken>(
					tagContentTokens.subList(1, tokenCount));
			returns.add(new StacksReturnTag (tempName,
				new StacksDescription(rest)));
		}
	}

	/**
	 * The see keyword refers the reader to something else.  Not
	 * inherently linked.
	 */
	private final ArrayList<StacksSeeTag> sees;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksSeesTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
			if (tagContentTokens.size() == 1)
			{
				try
				{
					sees.add(new StacksSeeTag (
						(RegionStacksToken) tagContentTokens.get(0)));
				}
				catch (final ClassCastException e)
				{
					final String errorMessage = String.format("\n<li><strong>%s"
						+ "</strong><em> Line #: %d</em>: Malformed @sees "
						+ "tag section; expected a semnatic link (bracketed "
						+ "{}) or quoted @sees content.</li>",
						moduleLeafName,
						commentStartLine());
					throw new StacksCommentBuilderException(errorMessage, this);
				}
			}
			else
			{
				final String errorMessage = String.format("\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @sees tag section; "
					+ "wrong # of @sees components.</li>",
					moduleLeafName,
					commentStartLine());
				throw new StacksCommentBuilderException(errorMessage, this);
			}
	}

	/**
	 * The sticky keyword indicates an implementation should be documented
	 * regardless of visibility.
	 */
	private final ArrayList<StacksStickyTag> stickies;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksStickyTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		if (tagContentTokens.size() == 0)
		{
			stickies.add(new StacksStickyTag ());
		}
		else
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed @sticky tag section; "
				+ "expected no tokens to follow @sticky tag.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}
	}

	/**
	 * The supertype keyword indicates the supertype of the class
	 * implementation.
	 */
	private final ArrayList<StacksSuperTypeTag> supertypes;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksSupertypeTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		for (final AbstractStacksToken token : tagContentTokens)
		{
			try
			{
				supertypes.add(new StacksSuperTypeTag
					((QuotedStacksToken) token));
			}
			catch (final ClassCastException e)
			{
				final String errorMessage = String.format("\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @supertype "
					+ "tag section; expected a series of quoted supertype "
					+ "names immediately following the @supertype tag, however "
					+ "does not start with a quoted supertype is listed.</li>",
					moduleLeafName,
					commentStartLine());
				throw new StacksCommentBuilderException(errorMessage, this);
			}
		}
	}

	/**
	 * The type keyword indicates the name of the class implementation.
	 */
	private final ArrayList<StacksTypeTag> types;

	/**
	 * @param tagContentTokens
	 * 		The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	public void addStacksTypeTag(
		 final ArrayList<AbstractStacksToken> tagContentTokens)
			 throws ClassCastException, StacksCommentBuilderException
	{
		if (tagContentTokens.size() == 1)
		{
			try
			{
				types.add(new StacksTypeTag (
					(QuotedStacksToken) tagContentTokens.get(0)));
			}
			catch (final ClassCastException e)
			{
				final String errorMessage = String.format("\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @returns "
					+ "tag section; expected quoted type.</li>",
					moduleLeafName,
					commentStartLine());
				throw new StacksCommentBuilderException(errorMessage, this);
			}
		}
		else
		{
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed @type tag section; "
				+ "has wrong # components.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}
	}

	/**
	 * The name of the module the comment originates from.
	 */
	private final String moduleName;

	/**
	 * The start line in the module the comment being parsed appears.
	 */
	private final int commentStartLine;

	/**
	 * The module file name without the path.
	 */
	final String moduleLeafName;

	/**
	 * Construct a new {@link CommentImplementationBuilder}.
	 * @param moduleName
	 * 		The name of the module the comment is in.
	 * @param commentStartLine
	 * 		The start line in the module of the comment being built
	 *
	 */
	private CommentImplementationBuilder (final String moduleName,
		final int commentStartLine)
	{
		this.moduleLeafName = moduleName
			.substring( moduleName.lastIndexOf("/") + 1);
		this.moduleName = moduleName;
		this.commentStartLine = commentStartLine;
		this.authors = new ArrayList<StacksAuthorTag>(0);
		this.categories = new ArrayList<StacksCategoryTag>(1);
		this.fields = new ArrayList<StacksFieldTag>(0);
		this.forbids = new TreeMap<Integer,StacksForbidsTag>();
		this.globalVariables = new ArrayList<StacksGlobalTag>(0);
		this.methods = new ArrayList<StacksMethodTag>(0);
		this.macros = new ArrayList<StacksMacroTag>(0);
		this.parameters = new ArrayList<StacksParameterTag>(0);
		this.raises = new ArrayList<StacksRaisesTag>(0);
		this.restricts = new ArrayList<StacksRestrictsTag>(0);
		this.returns = new ArrayList<StacksReturnTag>(0);
		this.sees = new ArrayList<StacksSeeTag>(0);
		this.supertypes = new ArrayList<StacksSuperTypeTag>(0);
		this.types = new ArrayList<StacksTypeTag>(0);
		this.aliases = new ArrayList<StacksAliasTag>(0);
		this.stickies = new ArrayList<StacksStickyTag>(0);
		final ArrayList<QuotedStacksToken> tempTokens =
			new ArrayList<QuotedStacksToken>();

		tempTokens
			.add(QuotedStacksToken.create("Unclassified", 0, 0, 0, moduleName));

		this.categories.add(new StacksCategoryTag (tempTokens));
	}

	/**
	 *
	 * @param moduleName
	 * 		The name of the module the comment is in.
	 * @param commentStartLine
	 * 		The start line in the module of the comment being built
	 * @return
	 * 		A {@link CommentImplementationBuilder}
	 * @throws StacksCommentBuilderException
	 */
	public static CommentImplementationBuilder createBuilder (
		final String moduleName,
		final int commentStartLine)
			throws StacksCommentBuilderException
	{
		return new CommentImplementationBuilder (moduleName, commentStartLine);
	}

	/**
	 * @return the commentStartLine
	 */
	public int commentStartLine ()
	{
		return commentStartLine;
	}

	/**
	 * @return the moduleName
	 */
	public String moduleName ()
	{
		return moduleName;
	}

	/**
	 * @return
	 * 		The appropriately built {@link AbstractCommentImplementation
	 * 		Comment Implementation}
	 * @throws StacksCommentBuilderException
	 */
	public AbstractCommentImplementation createStacksComment ()
		throws StacksCommentBuilderException
	{

		if (!types.isEmpty() && methods.isEmpty() && globalVariables.isEmpty())
		{
			if (types.size() == 1)
			{
				final CommentSignature signature =
					new CommentSignature(
							types.get(0).typeName().lexeme,moduleName());
				return new ClassCommentImplementation (
					signature,
					commentStartLine (),
					authors,
					sees,
					description(),
					categories,
					aliases,
					supertypes,
					fields,
					!stickies.isEmpty());
			}
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed has wrong # of "
				+ "@types identifying tags.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}

		if (types.isEmpty() && (!methods.isEmpty() || !macros.isEmpty())
			&& globalVariables.isEmpty())
		{
			if (macros.isEmpty())
			{
				if (methods.size() > 1)
				{
					final String errorMessage = String.format("\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed has wrong # of "
						+ "@method identifying tags.</li>",
						moduleLeafName,
						commentStartLine());
					throw new StacksCommentBuilderException(errorMessage, this);
				}
				if (!restricts.isEmpty() && parameters.isEmpty() &&
					forbids.isEmpty())
				{
					final ArrayList<String> orderedInputTypes =
						new ArrayList<String>(0);
					for (final StacksRestrictsTag restrict : restricts)
					{
						orderedInputTypes.add(restrict.paramMetaType().lexeme());
					}

					final SemanticRestrictionCommentSignature signature =
						new SemanticRestrictionCommentSignature(
							methods.get(0).methodName().lexeme(),moduleName,
							orderedInputTypes);

					return new SemanticRestrictionCommentImplementation(signature,
						commentStartLine (), authors, sees, description(),
						categories, aliases, restricts,returns);
				}

				if (restricts.isEmpty() && !parameters.isEmpty() &&
					forbids.isEmpty())
				{
					if (returns.isEmpty())
					{
						final String errorMessage = String.format("\n<li><strong>%s"
							+ "</strong><em> Line #: %d</em>: Malformed comment; "
							+ "has no obvious @returns tag.</li>",
							moduleLeafName,
							commentStartLine());
						throw new StacksCommentBuilderException(errorMessage, this);
					}
					final ArrayList<String> orderedInputTypes =
						new ArrayList<String>(0);
					for (final StacksParameterTag param : parameters)
					{
						orderedInputTypes.add(param.paramType().lexeme());
					}

					final MethodCommentSignature signature =
						new MethodCommentSignature(
							methods.get(0).methodName().lexeme(),
							moduleName(), orderedInputTypes,
							returns.get(0).returnType().lexeme());

					return new MethodCommentImplementation(signature,
						commentStartLine (), authors, sees, description(),
						categories, aliases, parameters,returns.get(0), raises,
						!stickies.isEmpty());
				}

				if (restricts.isEmpty() && parameters.isEmpty() &&
					!forbids.isEmpty())
				{
					final CommentSignature signature =
						new CommentSignature(
							methods.get(0).methodName().lexeme(), moduleName());

					return new GrammaticalRestrictionCommentImplementation(
						signature, commentStartLine (), authors, sees,
						description(), categories, aliases, forbids);
				}

				if (restricts.isEmpty() && parameters.isEmpty() &&
					forbids.isEmpty() && !returns.isEmpty())
				{
					final ArrayList<String> orderedInputTypes =
						new ArrayList<String>(0);

					final MethodCommentSignature signature =
						new MethodCommentSignature(
							methods.get(0).methodName().lexeme(), moduleName(),
							orderedInputTypes,
							returns.get(0).returnType().lexeme());

					return new MethodCommentImplementation(signature,
						commentStartLine (), authors, sees, description(),
						categories, aliases, parameters, returns.get(0), raises,
						!stickies.isEmpty());
				}
			} else {
				if (macros.size() > 1)
				{
					final String errorMessage = String.format("\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed has wrong # of "
						+ "@macro identifying tags.</li>",
						moduleLeafName,
						commentStartLine());
					throw new StacksCommentBuilderException(errorMessage, this);
				}
				if (!restricts.isEmpty() && parameters.isEmpty() &&
					forbids.isEmpty())
				{
					final ArrayList<String> orderedInputTypes =
						new ArrayList<String>(0);
					for (final StacksRestrictsTag restrict : restricts)
					{
						orderedInputTypes.add(restrict.paramMetaType().lexeme());
					}

					final SemanticRestrictionCommentSignature signature =
						new SemanticRestrictionCommentSignature(
							macros.get(0).methodName().lexeme(),moduleName,
							orderedInputTypes);

					return new SemanticRestrictionCommentImplementation(signature,
						commentStartLine (), authors, sees, description(),
						categories, aliases, restricts,returns);
				}

				if (restricts.isEmpty() && !parameters.isEmpty() &&
					forbids.isEmpty())
				{
					if (returns.isEmpty())
					{
						final String errorMessage = String.format("\n<li><strong>%s"
							+ "</strong><em> Line #: %d</em>: Malformed comment; "
							+ "has no obvious @returns tag.</li>",
							moduleLeafName,
							commentStartLine());
						throw new StacksCommentBuilderException(errorMessage, this);
					}
					final ArrayList<String> orderedInputTypes =
						new ArrayList<String>(0);
					for (final StacksParameterTag param : parameters)
					{
						orderedInputTypes.add(param.paramType().lexeme());
					}

					final MethodCommentSignature signature =
						new MethodCommentSignature(
							macros.get(0).methodName().lexeme(),
							moduleName(), orderedInputTypes,
							returns.get(0).returnType().lexeme());

					return new MacroCommentImplementation(signature,
						commentStartLine (), authors, sees, description(),
						categories, aliases, parameters,returns.get(0), raises,
						!stickies.isEmpty());
				}

				if (restricts.isEmpty() && parameters.isEmpty() &&
					!forbids.isEmpty())
				{
					final CommentSignature signature =
						new CommentSignature(
							macros.get(0).methodName().lexeme(), moduleName());

					return new GrammaticalRestrictionCommentImplementation(
						signature, commentStartLine (), authors, sees,
						description(), categories, aliases, forbids);
				}

				if (restricts.isEmpty() && parameters.isEmpty() &&
					forbids.isEmpty() && !returns.isEmpty())
				{
					final ArrayList<String> orderedInputTypes =
						new ArrayList<String>(0);

					final MethodCommentSignature signature =
						new MethodCommentSignature(
							macros.get(0).methodName().lexeme(), moduleName(),
							orderedInputTypes,
							returns.get(0).returnType().lexeme());

					return new MacroCommentImplementation(signature,
						commentStartLine (), authors, sees, description(),
						categories, aliases, parameters, returns.get(0), raises,
						!stickies.isEmpty());
				}
			}
		}

		if (types.isEmpty() && methods.isEmpty() && !globalVariables.isEmpty())
		{
			if (globalVariables.size() == 1)
			{
				final GlobalCommentSignature signature =
					new GlobalCommentSignature(
						globalVariables.get(0).globalName().lexeme(),
						moduleName(),
						globalVariables.get(0).globalType().lexeme());

				return new GlobalCommentImplementation(signature,
					commentStartLine (), authors, sees,description(),
					categories, aliases, globalVariables.get(0));
			}
			final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed comment; has "
				+ "wrong # of @global identifying tags.</li>",
				moduleLeafName,
				commentStartLine());
			throw new StacksCommentBuilderException(errorMessage, this);
		}
		if (types.isEmpty() && methods.isEmpty() && globalVariables.isEmpty()
			&& authors.isEmpty() && fields.isEmpty() && forbids.isEmpty()
			&& parameters.isEmpty() && raises.isEmpty() && restricts.isEmpty()
			&& returns.isEmpty() && sees.isEmpty() && supertypes.isEmpty()
			&& types.isEmpty() && !categories.isEmpty())
		{
			//Defining of a category
			final StacksCategoryTag onlyTag = categories.get(0);

			if (onlyTag.categories().size() == 1)
			{
				linkingFileMap
					.addCategoryToDescription(
						onlyTag.categories().get(0).lexeme()
						,description());
				return null;
			}
		}

		final String errorMessage = String.format("\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed comment has no "
				+ "distinguishing identifying tags that indicate the type of "
				+ "comment.</li>",
				moduleLeafName,
				commentStartLine());
		throw new StacksCommentBuilderException(errorMessage, this);
	}
}
