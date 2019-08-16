/*
 * StacksParser.java
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

package com.avail.stacks;

import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.TokenDescriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a List of {@link AbstractStacksToken stacks tokens}.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public final class StacksParser
{
	/**
	 * The {@link CommentImplementationBuilder builder} responsible for
	 * building the {@link AbstractCommentImplementation comment implementation}
	 */
	final CommentImplementationBuilder builder;

	/**
	 *
	 * The collection of section keywords that a comment can have.
	 *
	 * @author Richard Arriaga &lt;rich@availlang.org&gt;
	 */
	private enum StacksTagKeyword
	{
		/**
		 * The author keyword indicates the method implementation author.
		 */
		ALIAS("@alias")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksAliasTag(tagContentTokens, categories);
			}
		},
		/**
		 * The author keyword indicates the method implementation author.
		 */
		AUTHOR("@author")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
			{
				builder.addStacksAuthorTag(tagContentTokens);
			}
		},

		/**
		 * The category keyword provides a category to which the method
		 * implementation belongs.
		 */
		CATEGORY("@category")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksCategoryTag(tagContentTokens, categories);
			}
		},

		/**
		 * The field keyword indicates a field in the class implementation.
		 */
		FIELD("@field")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{

				builder.addStacksFieldTag(tagContentTokens);
			}
		},

		/**
		 * The forbids keyword indicates the methods forbidden by a
		 * Grammatical Restriction for the method implementation.
		 */
		FORBIDS("@forbids")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksForbidTag(tagContentTokens);
			}
		},
		/**
		 * The global keyword represents a module variable
		 */
		GLOBAL("@global")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksGlobalTag(tagContentTokens);
			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		MACRO("@macro")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksMacroTag(tagContentTokens);
			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		METHOD("@method")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksMethodTag(tagContentTokens);
			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		MODULE("@module")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksModuleTag(tagContentTokens);
			}
		},

		/**
		 * The parameter keyword indicates an input for the method
		 * implementation.
		 */
		PARAMETER("@param")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksParameterTag(tagContentTokens);
			}
		},

		/**
		 * The raises keyword indicates the exceptions thrown by the method
		 * implementation.
		 */
		RAISES("@raises")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksRaisesTag(tagContentTokens);
			}
		},

		/**
		 * The restricts keyword indicates the input types used by the method
		 * implementation's semantic restriction.
		 */
		RESTRICTS("@restricts")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksRestrictsTag(tagContentTokens);
			}
		},

		/**
		 * The returns keyword indicates the output for the method
		 * implementation.
		 */
		RETURNS("@returns")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksReturnsTag(tagContentTokens);
			}
		},

		/**
		 * The see keyword refers the reader to something else.  Not
		 * inherently linked.
		 */
		SEE("@see")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksSeesTag(tagContentTokens);
			}
		},

		/**
		 * The sticky keyword indicates the method/macro/class should be
		 * documented regardless of visibility.
		 */
		STICKY("@sticky")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksStickyTag(tagContentTokens);
			}
		},

		/**
		 * The supertype keyword indicates the supertype of the class
		 * implementation.
		 */
		SUPERTYPE("@supertype")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksSupertypeTag(tagContentTokens);
			}
		},

		/**
		 * The type keyword indicates the name of the class implementation.
		 */
		TYPE("@type")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens,
				 final LinkingFileMap categories)
					 throws ClassCastException, StacksCommentBuilderException
			{
				builder.addStacksTypeTag(tagContentTokens);
			}
		};

		/** An array of all {@link StacksTagKeyword} enumeration values. */
		private static final StacksTagKeyword[] all = values();

		/**
		 * Answer an array of all {@link StacksTagKeyword} enumeration values.
		 *
		 * @return An array of all {@link StacksTagKeyword} enum values.  Do not
		 *         modify the array.
		 */
		public static StacksTagKeyword[] all ()
		{
			return all;
		}

		/** The string representation of the keyword */
		final String lexeme;

		/**
		 * The constructor of the {@link StacksTagKeyword}
		 * @param lexeme
		 * 		{@link String} String representation of the keyword.
		 */
		StacksTagKeyword (final String lexeme) {
			this.lexeme = lexeme;
		}

		/**
		 * A {@linkplain Map mapping} from the string lexeme of the keyword to
		 * the {@link Enum StacksKeywords}
		 */
		static final Map<String, StacksTagKeyword> keywordTable =
			new HashMap<>();

		// Learn the lexeme's of the keywords.
		static
		{
			for (final StacksTagKeyword keyword : StacksTagKeyword.all())
			{
				keywordTable.put(keyword.lexeme, keyword);
			}
		}

		/**
		 * Add the tokens to the appropriate portion of the builder.
		 * @param builder
		 * 		The comment builder
		 * @param tagContentTokens
		 * 		The tokens contained in the indicated section.
		 * @param categories The holder for all categories presently available
		 * @throws ClassCastException
		 * @throws StacksCommentBuilderException
		 */
		 abstract void addTokensToBuilder (
			 CommentImplementationBuilder builder,
			 ArrayList<AbstractStacksToken> tagContentTokens, LinkingFileMap categories)
		 throws ClassCastException, StacksCommentBuilderException;
	}

	/**
	 *  The tokens to be parsed.
	 */
	private final List<AbstractStacksToken> tokens;

	/**
	 * The index locations where a new {@link SectionKeywordStacksToken section}
	 * begins in in the token list to be parsed.
	 */
	final List<Integer> sectionStartLocations;

	/**
	 * The name of the module the comment originates from.
	 */
	private final String moduleName;

	/**
	 * The start line in the module the comment being parsed appears.
	 */
	private final int commentStartLine;

	/**
	 * Construct a new {@link StacksParser}.
	 * @param tokens
	 * 		The tokens to be parsed.
	 * @param sectionStartLocations
	 * 		The index locations where a new {@link SectionKeywordStacksToken
	 * 		section} begins in in the token list to be parsed.
	 * @param moduleName
	 * 		The name of the module the comment occurs in.
	 * @param commentStartLine
	 * 		The line the comment being parsed appears on
	 * @param linkingFileMap A map for all the files in stacks
	 * @throws StacksCommentBuilderException
	 */
	private StacksParser (
		final List<AbstractStacksToken> tokens,
		final List<Integer> sectionStartLocations,
		final String moduleName,
		final int commentStartLine,
		final LinkingFileMap linkingFileMap)
	{
		this.tokens = tokens;
		this.sectionStartLocations = sectionStartLocations;
		this.moduleName = moduleName;
		this.commentStartLine = commentStartLine;
		this.builder =
			CommentImplementationBuilder
				.createBuilder(moduleName,commentStartLine, linkingFileMap);
	}

	/**
	 * @return the tokens
	 */
	public List<AbstractStacksToken> tokens ()
	{
		return tokens;
	}

	/**
	 * @return the moduleName
	 */
	public String moduleName ()
	{
		return moduleName;
	}

	/**
	 * Answer the {@linkplain List list} of {@linkplain TokenDescriptor tokens}
	 * that comprise a {@linkplain CommentTokenDescriptor Avail comment}.
	 *
	 * @param tokens
	 *		The {@linkplain AbstractStacksToken Stacks tokens} to be
	 *		parsed.
	 * @param sectionStartLocations
	 * 		The index locations where a new {@link SectionKeywordStacksToken
	 * 		section} begins in in the token list to be parsed.
	 * @param string
	 * 		The name of the module from which the comment originates.
	 * @param commentStartLine
	 * 		The line where the comment being parsed starts in the module.
	 * @param linkingFileMap
	 * 		A map for all the files in stacks
	 * @return
	 * 		The resulting {@link AbstractCommentImplementation} of the token
	 * 		parsing.
	 * @throws StacksCommentBuilderException if the builder fails.
	 */
	public static AbstractCommentImplementation parseCommentString (
		final List<AbstractStacksToken> tokens,
		final List<Integer> sectionStartLocations,
		final String string,
		final int commentStartLine, final LinkingFileMap linkingFileMap)
		throws StacksCommentBuilderException
	{
		final StacksParser parser =
			new StacksParser(tokens, sectionStartLocations, string,
				commentStartLine,linkingFileMap);
		return parser.parse(linkingFileMap);
	}

	/**
	 * @param linkingFileMap
	 * 		A map for all the files in stacks
	 * @return
	 * @throws StacksCommentBuilderException
	 */
	private AbstractCommentImplementation parse (
		final LinkingFileMap linkingFileMap)
		throws StacksCommentBuilderException
	{
		int currentSectionStartLocationsIndex = 0;

		if (sectionStartLocations.get(0) != 0)
		{
			final List<AbstractStacksToken> description =
				new ArrayList<>(tokens()
					.subList(0, sectionStartLocations
						.get(currentSectionStartLocationsIndex)));
			builder.addStacksCommentDescription(description);
		}

		int nextSectionStartLocationsIndex = -1;
		while (nextSectionStartLocationsIndex < sectionStartLocations.size())
		{
			nextSectionStartLocationsIndex =
				currentSectionStartLocationsIndex + 1;

			final String key = tokens().get(
					sectionStartLocations.get(currentSectionStartLocationsIndex)
				).lexeme();

			final int getDataStartingFrom = sectionStartLocations.get(
				currentSectionStartLocationsIndex) + 1;

			final int getDataUntil;
			if (nextSectionStartLocationsIndex >
				sectionStartLocations.size() - 1)
			{
				getDataUntil = tokens().size();
			}
			else
			{
				getDataUntil = sectionStartLocations.get(
					nextSectionStartLocationsIndex);
			}

			//Add the new tag section to the map.
			StacksTagKeyword.keywordTable.get(key).addTokensToBuilder(
				builder,
				new ArrayList<>(
					tokens().subList(getDataStartingFrom, getDataUntil)),
				linkingFileMap);
			currentSectionStartLocationsIndex = nextSectionStartLocationsIndex;
		}

		return builder.createStacksComment();
	}

	/**
	 * @return the commentStartLine
	 */
	public int commentStartLine ()
	{
		return commentStartLine;
	}
}
