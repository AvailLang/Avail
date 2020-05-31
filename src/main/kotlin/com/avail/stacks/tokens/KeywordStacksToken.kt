/*
 * KeywordStacksToken.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.stacks.tokens

import java.util.*

/**
 * A tokenized stacks keyword.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new `KeywordStacksToken`.
 *
 * @param string
 *   The string to be tokenized.
 * @param lineNumber
 *   The line number where the token occurs/begins
 * @param position
 *   The absolute start position of the token
 * @param startOfTokenLinePosition
 *   The position on the line where the token starts.
 * @param moduleName
 *   The module this token is in.
 * @param isSectionToken
 *   Whether the token is a section (§) indicator.
 */
abstract class KeywordStacksToken constructor(
		string: String,
		lineNumber: Int,
		position: Int,
		startOfTokenLinePosition: Int,
		moduleName: String,
		isSectionToken: Boolean)
	: AbstractStacksToken(
		string,
		lineNumber,
		position,
		startOfTokenLinePosition,
		moduleName,
		isSectionToken)
{

	/**
	 * The collection of keywords that a comment can have.
	 *
	 * @author Richard Arriaga &lt;rich@availlang.org&gt;
	 *
	 * @property lexeme
	 *   The string representation of the keyword
	 * @constructor
	 * The constructor of the [StacksKeyword]
	 * @param lexeme
	 *   String representation of the keyword.
	 */
	private enum class StacksKeyword constructor(val lexeme: String)
	{
		/**
		 * The author keyword indicates the method implementation author.
		 */
		ALIAS("@alias"),

		/**
		 * The author keyword indicates the method implementation author.
		 */
		AUTHOR("@author"),

		/**
		 * The category keyword provides a category to which the method
		 * implementation belongs.
		 */
		CATEGORY("@category"),

		/**
		 * This is an inline keyword.  It does not get a top level tag.
		 */
		CODE("@code")
		{
			override fun createToken(
				lineNumber: Int,
				position: Int,
				startOfTokenLinePosition: Int,
				moduleName: String): KeywordStacksToken
			{
				return InlineKeywordStacksToken.create(
					lexeme, lineNumber, position,
					startOfTokenLinePosition, moduleName)
			}
		},

		/**
		 * The field keyword indicates a field in the class implementation.
		 */
		FIELD("@field"),

		/**
		 * The forbids keyword indicates the methods forbidden by a
		 * Grammatical Restriction for the method implementation.
		 */
		FORBIDS("@forbids"),

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		GLOBAL("@global"),

		/**
		 * The link keyword creates an external web link.
		 */
		LINK("@link")
		{
			override fun createToken(
				lineNumber: Int,
				position: Int,
				startOfTokenLinePosition: Int,
				moduleName: String): KeywordStacksToken
			{
				return InlineKeywordStacksToken.create(
					lexeme, lineNumber, position,
					startOfTokenLinePosition, moduleName)
			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		MACRO("@macro"),

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		METHOD("@method"),

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		MODULE("@module"),

		/**
		 * The parameter keyword indicates an input for the method
		 * implementation.
		 */
		PARAMETER("@param"),

		/**
		 * The raises keyword indicates the exceptions thrown by the method
		 * implementation.
		 */
		RAISES("@raises"),

		/**
		 * The restricts keyword indicates the input types used by the method
		 * implementation's semantic restriction.
		 */
		RESTRICTS("@restricts"),

		/**
		 * The returns keyword indicates the output for the method
		 * implementation.
		 */
		RETURNS("@returns"),

		/**
		 * The see keyword refers the reader to something else.  Not
		 * inherently linked.
		 */
		SEE("@see"),

		/**
		 * The supertype keyword indicates the supertype of the class
		 * implementation.
		 */
		STICKY("@sticky"),

		/**
		 * The supertype keyword indicates the supertype of the class
		 * implementation.
		 */
		SUPERTYPE("@supertype"),

		/**
		 * The type keyword indicates the name of the class implementation.
		 */
		TYPE("@type");

		/**
		 * Create the appropriate keyword token
		 * @param lineNumber
		 * The line number where the token occurs/begins
		 * @param position
		 * The absolute start position of the token
		 * @param startOfTokenLinePosition
		 * The position on the line where the token starts.
		 * @param moduleName
		 * The module the token appears in.
		 * @return
		 */
		open fun createToken(
			lineNumber: Int,
			position: Int,
			startOfTokenLinePosition: Int,
			moduleName: String): KeywordStacksToken =
			SectionKeywordStacksToken.create(
				lexeme, lineNumber, position,
				startOfTokenLinePosition, moduleName)

		companion object
		{

			/** An array of all [StacksKeyword] enumeration values.  */
			private val all = values()

			/**
			 * Answer an array of all [StacksKeyword] enumeration values.
			 *
			 * @return An array of all [StacksKeyword] enum values.  Do not
			 * modify the array.
			 */
			fun all(): Array<StacksKeyword>
			{
				return all
			}

			/**
			 * A [mapping][Map] from the string lexeme of the keyword to
			 * the [StacksKeywords][Enum]
			 */
			internal val keywordTable: MutableMap<String, StacksKeyword> =
				HashMap()

			// Learn the lexemes of the keywords.
			init
			{
				for (keyword in all())
				{
					keywordTable[keyword.lexeme] = keyword
				}
			}
		}
	}

	companion object
	{

		/**
		 * Create the appropriate [token&#32;type][AbstractStacksToken]
		 *
		 * @param string
		 * String representation of the keyword being looked up for
		 * creation
		 * @param lineNumber
		 * The line number where the token occurs/begins
		 * @param position
		 * The absolute start position of the token
		 * @param startOfTokenLinePosition
		 * The position on the line where the token starts.
		 * @param moduleName
		 * The module the token appears in
		 * @return The [token][AbstractStacksToken] of the appropriate
		 * type.
		 */
		fun create(
			string: String,
			lineNumber: Int,
			position: Int,
			startOfTokenLinePosition: Int,
			moduleName: String): AbstractStacksToken
		{
			val keyword = StacksKeyword.keywordTable[string]
				?: return StacksToken.create(
					string, lineNumber, position,
					startOfTokenLinePosition, moduleName)

			return keyword.createToken(
				lineNumber, position, startOfTokenLinePosition, moduleName)
		}
	}
}
