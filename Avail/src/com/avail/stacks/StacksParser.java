/**
 * StacksParser.java
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.avail.descriptor.A_String;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.TokenDescriptor;

/**
 * Parses a List of {@link AbstractStacksToken stacks tokens}.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksParser
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
		AUTHOR("@author")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
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
				 final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
			{
				builder.addStacksCategoryTag(tagContentTokens);
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
				 final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
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
				final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
			{
				builder.addStacksForbidTag(tagContentTokens);
			}
		},
		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		Global("@global")
		{
			@Override
			void addTokensToBuilder (
				final CommentImplementationBuilder builder,
				 final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
			{
				builder.addStacksGlobalTag(tagContentTokens);
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
				 final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
			{
				builder.addStacksMethodTag(tagContentTokens);
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
				 final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
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
				 final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
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
				 final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
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
				 final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
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
				 final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
			{
				builder.addStacksSeesTag(tagContentTokens);
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
				 final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
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
				 final ArrayList<AbstractStacksToken> tagContentTokens)
					 throws ClassCastException
			{
				builder.addStacksTypeTag(tagContentTokens);
			}
		};

		/** The string representation of the keyword */
		final String lexeme;


		/**
		 * The constructor of the {@link StacksTagKeyword}
		 * @param lexeme
		 * 		{@link String} String representation of the keyword.
		 */
		private StacksTagKeyword(final String lexeme) {
			this.lexeme = lexeme;
		}

		/**
		 * A {@linkplain Map mapping} from the string lexeme of the keyword to
		 * the {@link Enum StacksKeywords}
		 */
		static final Map<String, StacksTagKeyword> keywordTable =
			new HashMap<String, StacksTagKeyword>();

		// Learn the lexeme's of the keywords.
		static
		{
			for (final StacksTagKeyword keyword : StacksTagKeyword.values())
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
		 * @throws ClassCastException
		 */
		 abstract void addTokensToBuilder (
			 CommentImplementationBuilder builder,
			 ArrayList<AbstractStacksToken> tagContentTokens)
		 throws ClassCastException;
	}

	/**
	 *  The tokens to be parsed.
	 */
	final private ArrayList<AbstractStacksToken> tokens;

	/**
	 * The index locations where a new {@link SectionKeywordStacksToken section}
	 * begins in in the token list to be parsed.
	 */
	final ArrayList<Integer> sectionStartLocations;

	/**
	 * The name of the module the comment originates from.
	 */
	private final A_String moduleName;

	/**
	 * Construct a new {@link StacksParser}.
	 * @param tokens
	 * 		The tokens to be parsed.
	 * @param sectionStartLocations
	 * 		The index locations where a new {@link SectionKeywordStacksToken
	 * 		section} begins in in the token list to be parsed.
	 * @param moduleName
	 * 		The name of the module the comment occurs in.
	 */
	private StacksParser (
		final ArrayList<AbstractStacksToken> tokens,
		final ArrayList<Integer> sectionStartLocations,
		final A_String moduleName)
	{
		this.tokens = tokens;
		this.sectionStartLocations = sectionStartLocations;
		this.moduleName = moduleName;
		this.builder = new CommentImplementationBuilder(moduleName);
	}

	/**
	 * @return the tokens
	 */
	public ArrayList<AbstractStacksToken> tokens ()
	{
		return tokens;
	}

	/**
	 * @return the moduleName
	 */
	public A_String moduleName ()
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
	 * @param moduleName
	 * 		The name of the module from which the comment originates.
	 * @return
	 * 		The resulting {@link AbstractCommentImplementation} of the token
	 * 		parsing.
	 * @throws StacksException If scanning fails.
	 */
	public static AbstractCommentImplementation parseCommentString (
		final ArrayList<AbstractStacksToken> tokens,
		final ArrayList<Integer> sectionStartLocations,
		final A_String moduleName)
		throws StacksException
	{
		final StacksParser parser =
			new StacksParser(tokens, sectionStartLocations, moduleName);
		return parser.parse();
	}

	/**
	 * @return
	 */
	private AbstractCommentImplementation parse ()
	{
		int nextSectionStartLocationsIndex = -1;
		final int currentSectionStartLocationsIndex = 0;

		if (sectionStartLocations.get(0) != 0)
		{
			builder.addStacksCommentDescription(
				(ArrayList<AbstractStacksToken>) tokens()
					.subList(0, currentSectionStartLocationsIndex));
		}

		while (nextSectionStartLocationsIndex != sectionStartLocations.size())
		{
			nextSectionStartLocationsIndex =
				currentSectionStartLocationsIndex + 1;

			final String key =
				tokens().get(currentSectionStartLocationsIndex).lexeme();

			//Add the new tag section to the map.
			StacksTagKeyword.keywordTable.get(key)
				.addTokensToBuilder(builder,
					(ArrayList<AbstractStacksToken>) tokens()
						.subList(currentSectionStartLocationsIndex,
							nextSectionStartLocationsIndex));
		}

		return builder.createStacksComment();
	}
}
