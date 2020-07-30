/*
 * StacksParser.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.stacks

import com.avail.descriptor.tokens.CommentTokenDescriptor
import com.avail.descriptor.tokens.TokenDescriptor
import com.avail.stacks.comment.AvailComment
import com.avail.stacks.comment.CommentBuilder
import com.avail.stacks.exceptions.StacksCommentBuilderException
import com.avail.stacks.tokens.AbstractStacksToken
import com.avail.stacks.tokens.SectionKeywordStacksToken
import java.util.HashMap

/**
 * Parses a List of [stacks&#32;tokens][AbstractStacksToken].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property tokens
 *   The tokens to be parsed.
 * @property sectionStartLocations
 *   The index locations where a new [section][SectionKeywordStacksToken] begins
 *   in in the token list to be parsed.
 * @property moduleName
 *   The name of the module the comment occurs in.
 * @property commentStartLine
 *   The line the comment being parsed appears on
 *
 * @constructor
 * Construct a new [StacksParser].
 *
 * @param tokens
 *   The tokens to be parsed.
 * @param sectionStartLocations
 *   The index locations where a new [section][SectionKeywordStacksToken] begins
 *   in in the token list to be parsed.
 * @param moduleName
 *   The name of the module the comment occurs in.
 * @param commentStartLine
 *   The line the comment being parsed appears on
 * @param linkingFileMap
 *   A map for all the files in stacks
 * @throws StacksCommentBuilderException
 */
class StacksParser private constructor(
	private val tokens: List<AbstractStacksToken>,
	private val sectionStartLocations: List<Int>,
	private val moduleName: String,
	private val commentStartLine: Int,
	linkingFileMap: LinkingFileMap)
{
	/**
	 * The [builder][CommentBuilder] responsible for
	 * building the [comment&#32;implementation][AvailComment]
	 */
	internal val builder: CommentBuilder = CommentBuilder
		.createBuilder(moduleName, commentStartLine, linkingFileMap)

	/**
	 * The collection of section keywords that a comment can have.
	 *
	 * @author Richard Arriaga &lt;rich@availlang.org&gt;
	 *
	 * @property lexeme
	 *   String representation of the keyword.
	 * @constructor
	 * Construct a [StacksTagKeyword].
	 *
	 * @param lexeme
	 *   String representation of the keyword.
	 */
	enum class StacksTagKeyword constructor(
		internal val lexeme: String)
	{
		/**
		 * The author keyword indicates the method implementation author.
		 */
		ALIAS("@alias")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksAliasTag(tagContentTokens, categories)
			}
		},
		/**
		 * The author keyword indicates the method implementation author.
		 */
		AUTHOR("@author")
		{
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksAuthorTag(tagContentTokens)
			}
		},

		/**
		 * The category keyword provides a category to which the method
		 * implementation belongs.
		 */
		CATEGORY("@category")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksCategoryTag(tagContentTokens, categories)
			}
		},

		/**
		 * The field keyword indicates a field in the class implementation.
		 */
		FIELD("@field")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{

				builder.addStacksFieldTag(tagContentTokens)
			}
		},

		/**
		 * The forbids keyword indicates the methods forbidden by a
		 * Grammatical Restriction for the method implementation.
		 */
		FORBIDS("@forbids")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksForbidTag(tagContentTokens)
			}
		},
		/**
		 * The global keyword represents a module variable
		 */
		GLOBAL("@global")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksGlobalTag(tagContentTokens)
			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		MACRO("@macro")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksMacroTag(tagContentTokens)
			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		METHOD("@method")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksMethodTag(tagContentTokens)
			}
		},

		/**
		 * The method keyword indicates the name of the method implementation.
		 */
		MODULE("@module")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksModuleTag(tagContentTokens)
			}
		},

		/**
		 * The parameter keyword indicates an input for the method
		 * implementation.
		 */
		PARAMETER("@param")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksParameterTag(tagContentTokens)
			}
		},

		/**
		 * The raises keyword indicates the exceptions thrown by the method
		 * implementation.
		 */
		RAISES("@raises")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksRaisesTag(tagContentTokens)
			}
		},

		/**
		 * The restricts keyword indicates the input types used by the method
		 * implementation's semantic restriction.
		 */
		RESTRICTS("@restricts")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksRestrictsTag(tagContentTokens)
			}
		},

		/**
		 * The returns keyword indicates the output for the method
		 * implementation.
		 */
		RETURNS("@returns")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksReturnsTag(tagContentTokens)
			}
		},

		/**
		 * The see keyword refers the reader to something else.  Not
		 * inherently linked.
		 */
		SEE("@see")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksSeesTag(tagContentTokens)
			}
		},

		/**
		 * The sticky keyword indicates the method/macro/class should be
		 * documented regardless of visibility.
		 */
		STICKY("@sticky")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksStickyTag(tagContentTokens)
			}
		},

		/**
		 * The supertype keyword indicates the supertype of the class
		 * implementation.
		 */
		SUPERTYPE("@supertype")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksSupertypeTag(tagContentTokens)
			}
		},

		/**
		 * The type keyword indicates the name of the class implementation.
		 */
		TYPE("@type")
		{
			@Throws(
				ClassCastException::class,
				StacksCommentBuilderException::class)
			override fun addTokensToBuilder(
				builder: CommentBuilder,
				tagContentTokens: List<AbstractStacksToken>,
				categories: LinkingFileMap)
			{
				builder.addStacksTypeTag(tagContentTokens)
			}
		};

		/**
		 * Add the tokens to the appropriate portion of the builder.
		 * @param builder
		 * The comment builder
		 * @param tagContentTokens
		 * The tokens contained in the indicated section.
		 * @param categories The holder for all categories presently available
		 * @throws ClassCastException
		 * @throws StacksCommentBuilderException
		 */
		@Throws(ClassCastException::class, StacksCommentBuilderException::class)
		internal abstract fun addTokensToBuilder(
			builder: CommentBuilder,
			tagContentTokens: List<AbstractStacksToken>,
			categories: LinkingFileMap)

		companion object
		{
			/** An array of all [StacksTagKeyword] enumeration values.  */
			private val all = values()

			/**
			 * Answer an array of all [StacksTagKeyword] enumeration values.
			 *
			 * @return An array of all [StacksTagKeyword] enum values.  Do not
			 * modify the array.
			 */
			fun all(): Array<StacksTagKeyword> = all

			/**
			 * A [mapping][Map] from the string lexeme of the keyword to
			 * the [StacksKeywords][Enum]
			 */
			internal val keywordTable = HashMap<String, StacksTagKeyword>()

			// Learn the lexeme's of the keywords.
			init
			{
				for (keyword in all)
				{
					keywordTable[keyword.lexeme] = keyword
				}
			}
		}
	}

	/**
	 * @return the tokens
	 */
	fun tokens(): List<AbstractStacksToken>
	{
		return tokens
	}

	/**
	 * @param linkingFileMap
	 * A map for all the files in stacks
	 * @return
	 * @throws StacksCommentBuilderException
	 */
	@Throws(StacksCommentBuilderException::class)
	private fun parse(
		linkingFileMap: LinkingFileMap): AvailComment?
	{
		var currentSectionStartLocationsIndex = 0

		if (sectionStartLocations[0] != 0)
		{
			val description =
				tokens()
					.subList(
						0,
						sectionStartLocations[currentSectionStartLocationsIndex]
					)
					.toList()
			builder.addStacksCommentDescription(description)
		}

		var nextSectionStartLocationsIndex = -1
		while (nextSectionStartLocationsIndex < sectionStartLocations.size)
		{
			nextSectionStartLocationsIndex =
				currentSectionStartLocationsIndex + 1

			val key =
				tokens()[sectionStartLocations[currentSectionStartLocationsIndex]]
					.lexeme

			val getDataStartingFrom =
				sectionStartLocations[currentSectionStartLocationsIndex] + 1

			val getDataUntil: Int = if (nextSectionStartLocationsIndex > sectionStartLocations.size - 1)
			{
				tokens().size
			}
			else
			{
				sectionStartLocations[nextSectionStartLocationsIndex]
			}

			//Add the new tag section to the map.
			StacksTagKeyword.keywordTable[key]?.addTokensToBuilder(
				builder,
				tokens().subList(getDataStartingFrom, getDataUntil).toList(),
				linkingFileMap)
			currentSectionStartLocationsIndex = nextSectionStartLocationsIndex
		}

		return builder.createStacksComment()
	}

	companion object
	{

		/**
		 * Answer the [list][List] of [tokens][TokenDescriptor]
		 * that comprise a [Avail&#32;comment][CommentTokenDescriptor].
		 *
		 * @param tokens
		 *   The [Stacks&#32;tokens][AbstractStacksToken] to be parsed.
		 * @param sectionStartLocations
		 *   The index locations where a new `SectionKeywordStacksToken`
		 *   begins in in the token list to be parsed.
		 * @param string
		 *   The name of the module from which the comment originates.
		 * @param commentStartLine
		 *   The line where the comment being parsed starts in the module.
		 * @param linkingFileMap
		 *   A map for all the files in stacks
		 * @return The resulting [AvailComment] of the token parsing.
		 * @throws StacksCommentBuilderException if the builder fails.
		 */
		@Throws(StacksCommentBuilderException::class)
		fun parseCommentString(
			tokens: List<AbstractStacksToken>,
			sectionStartLocations: List<Int>,
			string: String,
			commentStartLine: Int,
			linkingFileMap: LinkingFileMap): AvailComment?
		{
			val parser = StacksParser(
				tokens, sectionStartLocations, string,
				commentStartLine, linkingFileMap)
			return parser.parse(linkingFileMap)
		}
	}
}
