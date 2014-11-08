/**
 * KeywordStacksToken.java
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

import java.util.HashMap;
import java.util.Map;

/**
 * A tokenized stacks keyword.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public abstract class KeywordStacksToken extends AbstractStacksToken
{

	/**
	 * Construct a new {@link KeywordStacksToken}.
	 *
	 * @param string
	 * 		The string to be tokenized.
	 * @param lineNumber
	 * 		The line number where the token occurs/begins
	 * @param postion
	 * 		The absolute start position of the token
	 * @param startOfTokenLinePostion
	 * 		The position on the line where the token starts.
	 * @param moduleName
	 * 		The module this token is in.
	 */
	public KeywordStacksToken (
		final String string,
		final int lineNumber,
		final int postion,
		final int startOfTokenLinePostion,
		final String moduleName)
	{
		super(string, lineNumber, postion, startOfTokenLinePostion, moduleName);
	}


	/**
	 *
	 * The collection of keywords that a comment can have.
	 *
	 * @author Richard Arriaga &lt;rich@availlang.org&gt;
	 */
	private enum StacksKeyword
	{
		/**
		 * The author keyword indicates the method implementation author.
		 */
		ALIAS("@alias")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},
		/**
		 * The author keyword indicates the method implementation author.
		 */
		AUTHOR("@author")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The category keyword provides a category to which the method
		 * implementation belongs.
		 */
		CATEGORY("@category")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The link keyword creates an external web link.
		 */
		CODE("@code")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return InlineKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The field keyword indicates a field in the class implementation.
		 */
		FIELD("@field")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The forbids keyword indicates the methods forbidden by a
 		 * Grammatical Restriction for the method implementation.
		 */
		FORBIDS("@forbids")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		GLOBAL("@global")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The link keyword creates an external web link.
		 */
		LINK("@link")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return InlineKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		METHOD("@method")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The parameter keyword indicates an input for the method
		 * implementation.
		 */
		PARAMETER("@param")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The raises keyword indicates the exceptions thrown by the method
		 * implementation.
		 */
		RAISES("@raises")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The restricts keyword indicates the input types used by the method
		 * implementation's semantic restriction.
		 */
		RESTRICTS("@restricts")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The returns keyword indicates the output for the method
		 * implementation.
		 */
		RETURNS("@returns")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The see keyword refers the reader to something else.  Not
		 * inherently linked.
		 */
		SEE("@see")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The supertype keyword indicates the supertype of the class
		 * implementation.
		 */
		SUPERTYPE("@supertype")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		},

		/**
		 * The type keyword indicates the name of the class implementation.
		 */
		TYPE("@type")
		{
			@Override
			KeywordStacksToken createToken(
				final int lineNumber,
				final int postion,
				final int startOfTokenLinePostion,
				final String moduleName)
			{
				return SectionKeywordStacksToken.create(
					lexeme, lineNumber, postion,
					startOfTokenLinePostion, moduleName);
			}
		};

		/** An array of all {@link StacksKeyword} enumeration values. */
		private static StacksKeyword[] all = values();

		/**
		 * Answer an array of all {@link StacksKeyword} enumeration values.
		 *
		 * @return An array of all {@link StacksKeyword} enum values.  Do not
		 *         modify the array.
		 */
		public static StacksKeyword[] all ()
		{
			return all;
		}

		/** The string representation of the keyword */
		final String lexeme;

		/**
		 * The constructor of the {@link StacksKeyword}
		 * @param lexeme {@link String} String representation of
		 * the keyword.
		 */
		private StacksKeyword(final String lexeme) {
			this.lexeme = lexeme;
		}

		/**
		 * A {@linkplain Map mapping} from the string lexeme of the keyword to
		 * the {@link Enum StacksKeywords}
		 */
		static final Map<String, StacksKeyword> keywordTable =
			new HashMap<String, StacksKeyword>();

		// Learn the lexemes of the keywords.
		static
		{
			for (final StacksKeyword keyword : StacksKeyword.all())
			{
				keywordTable.put(keyword.lexeme, keyword);
			}
		}

		/**
		 * Create the appropriate keyword token
		 * @param lineNumber
		 * 		The line number where the token occurs/begins
		 * @param postion
		 * 		The absolute start position of the token
		 * @param startOfTokenLinePostion
		 * 		The position on the line where the token starts.
		 * @param moduleName
		 * 		The module the token appears in.
		 * @return
		 */
		 abstract KeywordStacksToken createToken (
			final int lineNumber,
			final int postion,
			final int startOfTokenLinePostion,
			final String moduleName);
	}

	/**
	 * Create the appropriate {@link AbstractStacksToken token type}
	 *
	 * @param string
	 * 		String representation of the keyword being looked up for
	 * 		creation
	 * @param lineNumber
	 * 		The line number where the token occurs/begins
	 * @param postion
	 * 		The absolute start position of the token
	 * @param startOfTokenLinePostion
	 * 		The position on the line where the token starts.
	 * @param moduleName
	 * 		The module the token appears in
	 * @return The {@link AbstractStacksToken token} of the appropriate
	 * 		type.
	 */
	public static AbstractStacksToken create(
		final String string,
		final int lineNumber,
		final int postion,
		final int startOfTokenLinePostion,
		final String moduleName)
	{
		final StacksKeyword keyword = StacksKeyword.keywordTable.get(string);
		if (keyword == null)
		{
			return StacksToken.create(
				string, lineNumber, postion,
				startOfTokenLinePostion, moduleName);
		}

		return keyword.createToken(
			lineNumber, postion, startOfTokenLinePostion,moduleName);
	}
}
