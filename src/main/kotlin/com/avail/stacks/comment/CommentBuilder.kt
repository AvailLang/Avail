/*
 * CommentBuilder.kt
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

package com.avail.stacks.comment

import com.avail.stacks.LinkingFileMap
import com.avail.stacks.StacksDescription
import com.avail.stacks.comment.signature.CommentSignature
import com.avail.stacks.comment.signature.GlobalCommentSignature
import com.avail.stacks.comment.signature.MethodCommentSignature
import com.avail.stacks.comment.signature.SemanticRestrictionCommentSignature
import com.avail.stacks.exceptions.StacksCommentBuilderException
import com.avail.stacks.tags.StacksAliasTag
import com.avail.stacks.tags.StacksAuthorTag
import com.avail.stacks.tags.StacksCategoryTag
import com.avail.stacks.tags.StacksFieldTag
import com.avail.stacks.tags.StacksForbidsTag
import com.avail.stacks.tags.StacksGlobalTag
import com.avail.stacks.tags.StacksMacroTag
import com.avail.stacks.tags.StacksMethodTag
import com.avail.stacks.tags.StacksModuleTag
import com.avail.stacks.tags.StacksParameterTag
import com.avail.stacks.tags.StacksRaisesTag
import com.avail.stacks.tags.StacksRestrictsTag
import com.avail.stacks.tags.StacksReturnTag
import com.avail.stacks.tags.StacksSeeTag
import com.avail.stacks.tags.StacksStickyTag
import com.avail.stacks.tags.StacksSuperTypeTag
import com.avail.stacks.tags.StacksTypeTag
import com.avail.stacks.tokens.AbstractStacksToken
import com.avail.stacks.tokens.QuotedStacksToken
import com.avail.stacks.tokens.RegionStacksToken
import java.util.TreeMap

/**
 * A builder class for an [AvailComment].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property moduleName
 *   The name of the module the comment is in.
 * @property commentStartLine
 *   The start line in the module of the comment being built
 * @property linkingFileMap
 *
 * @constructor
 * Construct a new [CommentBuilder].
 *
 * @param moduleName
 *   The name of the module the comment is in.
 * @param commentStartLine
 *   The start line in the module of the comment being built
 * @param linkingFileMap
 */
class CommentBuilder private constructor(
	val moduleName: String,
	private val commentStartLine: Int,
	private val linkingFileMap: LinkingFileMap)
{
	/**
	 * The alias keyword provides alias to which the method/macro is referred
	 * to by.
	 */
	private val aliases = mutableListOf<StacksAliasTag>()

	/**
	 * The author keyword indicates the method implementation author.
	 */
	private val authors = mutableListOf<StacksAuthorTag>()

	/**
	 * The category keyword provides a category to which the method
	 * implementation belongs.
	 */
	private val categories = mutableListOf<StacksCategoryTag>()

	/**
	 * The general description provided by the comment
	 */
	private var description: StacksDescription = StacksDescription(listOf())

	/**
	 * The field keyword indicates a field in the class implementation.
	 */
	private val fields = mutableListOf<StacksFieldTag>()

	/**
	 * The forbids keyword indicates the methods forbidden by a
	 * Grammatical Restriction for the method implementation.
	 */
	private val forbids = TreeMap<Int, StacksForbidsTag>()

	/**
	 * The globals keyword indicates the name of the method implementation.
	 */
	private val globalVariables = mutableListOf<StacksGlobalTag>()

	/**
	 * The method keyword indicates the name of the method implementation.
	 */
	private val methods = mutableListOf<StacksMethodTag>()

	/**
	 * The method keyword indicates the name of the method implementation.
	 */
	private val modules = mutableListOf<StacksModuleTag>()

	/**
	 * The macro keyword indicates the name of the macro implementation.
	 */
	private val macros = mutableListOf<StacksMacroTag>()

	/**
	 * The parameter keyword indicates an input for the method
	 * implementation.
	 */
	private val parameters = mutableListOf<StacksParameterTag>()

	/**
	 * The raises keyword indicates the exceptions thrown by the method
	 * implementation.
	 */
	private val raises = mutableListOf<StacksRaisesTag>()

	/**
	 * The restricts keyword indicates the input types used by the method
	 * implementation's semantic restriction.
	 */
	private val restricts = mutableListOf<StacksRestrictsTag>()

	/**
	 * The returns keyword indicates the output for the method
	 * implementation.
	 */
	private val returns = mutableListOf<StacksReturnTag>()

	/**
	 * The see keyword refers the reader to something else.  Not
	 * inherently linked.
	 */
	private val sees = mutableListOf<StacksSeeTag>()

	/**
	 * The sticky keyword indicates an implementation should be documented
	 * regardless of visibility.
	 */
	private val stickies = mutableListOf<StacksStickyTag>()

	/**
	 * The supertype keyword indicates the supertype of the class
	 * implementation.
	 */
	private val supertypes = mutableListOf<StacksSuperTypeTag>()

	/**
	 * The type keyword indicates the name of the class implementation.
	 */
	private val types = mutableListOf<StacksTypeTag>()

	/**
	 * The module file name without the path.
	 */
	private val moduleLeafName: String = moduleName
		.substring(moduleName.lastIndexOf('/') + 1)

	/**
	 * @param tagContentTokens
	 * The tokens held by the tag
	 * @param fileMap
	 * A map containing the file links in stacks
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksAliasTag(
		tagContentTokens: List<AbstractStacksToken>,
		@Suppress("UNUSED_PARAMETER") fileMap: LinkingFileMap)
	{
		val tempTokens = mutableListOf<QuotedStacksToken>()

		for (token in tagContentTokens)
		{
			try
			{
				tempTokens.add(token as QuotedStacksToken)
			}
			catch (e: ClassCastException)
			{
				val errorMessage = String.format(
					"\n<li><strong>%s"
						+ "</strong><em> Line #: %d</em>: Malformed @category "
						+ "tag section; expected a series of quoted category "
						+ "names immediately following the @category tag, however "
						+ "does not start with a quoted category is listed.</li>",
					moduleLeafName,
					commentStartLine)
				throw StacksCommentBuilderException(
					errorMessage,
					this)
			}

		}
		if (tempTokens.isNotEmpty())
		{
			aliases.clear()
		}
		aliases.add(StacksAliasTag(tempTokens))
	}

	/**
	 * @param tagContentTokens
	 * The tokens held by the tag
	 * @throws StacksCommentBuilderException
	 */
	fun addStacksAuthorTag(tagContentTokens: List<AbstractStacksToken>)
	{
		authors.add(StacksAuthorTag(tagContentTokens))
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @param fileMap
	 *   A map containing the file links in stacks
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksCategoryTag(
		tagContentTokens: List<AbstractStacksToken>,
		@Suppress("UNUSED_PARAMETER") fileMap: LinkingFileMap)
	{
		val tempTokens = mutableListOf<QuotedStacksToken>()

		for (token in tagContentTokens)
		{
			try
			{
				tempTokens.add(token as QuotedStacksToken)
			}
			catch (e: ClassCastException)
			{
				val errorMessage = String.format(
					"\n<li><strong>%s"
						+ "</strong><em> Line #: %d</em>: Malformed @category "
						+ "tag section; expected a series of quoted category "
						+ "names immediately following the @category tag, however "
						+ "does not start with a quoted category is listed.</li>",
					moduleLeafName,
					commentStartLine)
				throw StacksCommentBuilderException(
					errorMessage,
					this)
			}

		}
		if (tempTokens.isNotEmpty())
		{
			categories.clear()
		}
		categories.add(StacksCategoryTag(tempTokens))
	}

	/**
	 * @param list
	 *   List of [tokens][AbstractStacksToken] that make up the description
	 * @throws ClassCastException
	 */
	@Throws(ClassCastException::class)
	fun addStacksCommentDescription(list: List<AbstractStacksToken>)
	{
		description = StacksDescription(list)
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksFieldTag(
		tagContentTokens: List<AbstractStacksToken>)
	{
		val tokenCount = tagContentTokens.size

		if (tokenCount < 2)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @field tag section; "
					+ "too few components.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		val tempName: QuotedStacksToken
		try
		{
			tempName = tagContentTokens[0] as QuotedStacksToken
		}
		catch (e: ClassCastException)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @field "
					+ "tag section; expected quoted field name.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		val tempType: QuotedStacksToken
		try
		{
			tempType = tagContentTokens[1] as QuotedStacksToken
		}
		catch (e: ClassCastException)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed "
					+ "@field tag section; expected a quoted field type\n.",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		if (tokenCount == 2)
		{
			fields.add(
				StacksFieldTag(
					tempName, tempType,
					StacksDescription(mutableListOf())))
		}
		else
		{
			val rest = tagContentTokens.subList(2, tokenCount)
			fields.add(
				StacksFieldTag(
					tempName, tempType,
					StacksDescription(rest)))
		}
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksForbidTag(tagContentTokens: List<AbstractStacksToken>)
	{
		val arity: Int
		try
		{
			arity = Integer.parseInt(tagContentTokens[0].lexeme)
		}
		catch (e: NumberFormatException)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed "
					+ "@forbids tag section; expected a number immediately "
					+ "the @forbids tag, but did not receive one.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		try
		{
			val tempTokens = mutableListOf<QuotedStacksToken>()

			for (i in 1 until tagContentTokens.size)
			{
				tempTokens.add(tagContentTokens[i] as QuotedStacksToken)
			}

			forbids[arity] = StacksForbidsTag(tagContentTokens[0], tempTokens)
		}
		catch (e: ClassCastException)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed "
					+ "@forbids tag section; expected a series of quoted method "
					+ "names immediately following the @forbids tag arity number, "
					+ "however the first argument following the @forbids tag is "
					+ "not quoted.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksGlobalTag(tagContentTokens: List<AbstractStacksToken>)
	{
		val tokenCount = tagContentTokens.size

		if (tokenCount < 2)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed "
					+ "@global tag section; has too few components.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		val tempName: QuotedStacksToken
		try
		{
			tempName = tagContentTokens[0] as QuotedStacksToken
		}
		catch (e: ClassCastException)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed "
					+ "@global tag section; expected a quoted module variable name "
					+ "immediately following the @global tag, however no such "
					+ "quoted name is listed.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		val tempType: QuotedStacksToken
		try
		{
			tempType = tagContentTokens[1] as QuotedStacksToken
		}
		catch (e: ClassCastException)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed "
					+ "@global tag section; expected a quoted type "
					+ "immediately following the @global tag, however no such "
					+ "quoted type is listed.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		globalVariables.add(
			StacksGlobalTag(
				tempName,
				tempType))
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksMethodTag(tagContentTokens: List<AbstractStacksToken>)
	{
		if (tagContentTokens.size == 1)
		{
			try
			{
				methods.add(
					StacksMethodTag(
						tagContentTokens[0] as QuotedStacksToken))
			}
			catch (e: ClassCastException)
			{
				val errorMessage = String.format(
					"\n<li><strong>%s"
						+ "</strong><em> Line #: %d</em>: Malformed @method "
						+ "tag section; expected a quoted method name immediately "
						+ "following the @method tag.</li>",
					moduleLeafName,
					commentStartLine)
				throw StacksCommentBuilderException(
					errorMessage,
					this)
			}

		}
		else
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @method tag section; "
					+ "has wrong # of @method components.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksModuleTag(tagContentTokens: List<AbstractStacksToken>)
	{
		if (tagContentTokens.size == 0)
		{
			modules.add(StacksModuleTag())
		}
		else
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @module tag section; "
					+ "expected no tokens to follow @module tag.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksMacroTag(tagContentTokens: List<AbstractStacksToken>)
	{
		if (tagContentTokens.size == 1)
		{
			try
			{
				macros.add(
					StacksMacroTag(
						tagContentTokens[0] as QuotedStacksToken))
			}
			catch (e: ClassCastException)
			{
				val errorMessage = String.format(
					"\n<li><strong>%s"
						+ "</strong><em> Line #: %d</em>: Malformed @macro "
						+ "tag section; expected a quoted method name immediately "
						+ "following the @macro tag.</li>",
					moduleLeafName,
					commentStartLine)
				throw StacksCommentBuilderException(
					errorMessage,
					this)
			}

		}
		else
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @macro tag section; "
					+ "has wrong # of @macro components.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksParameterTag(tagContentTokens: List<AbstractStacksToken>)
	{
		val tokenCount = tagContentTokens.size
		if (tokenCount < 2)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @param tag section; "
					+ " has too few @param components.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		val tempName: QuotedStacksToken
		try
		{
			tempName = tagContentTokens[0] as QuotedStacksToken
		}
		catch (e: ClassCastException)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @param tag section; "
					+ "expected a quoted parameter type/name.",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		val tempType: QuotedStacksToken
		try
		{
			tempType = tagContentTokens[1] as QuotedStacksToken
		}
		catch (e: ClassCastException)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed "
					+ "@param tag section; expected a quoted parameter type/name.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		if (tokenCount == 2)
		{
			parameters.add(
				StacksParameterTag(
					tempName, tempType,
					StacksDescription(mutableListOf())))
		}
		else
		{
			val rest = tagContentTokens.subList(2, tokenCount)
			parameters.add(
				StacksParameterTag(
					tempName, tempType,
					StacksDescription(rest)))
		}
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksRaisesTag(tagContentTokens: List<AbstractStacksToken>)
	{
		val tokenCount = tagContentTokens.size


		if (tokenCount < 1)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @raises tag section; "
					+ "has too few components.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		val tempName: QuotedStacksToken
		try
		{
			tempName = tagContentTokens[0] as QuotedStacksToken
		}
		catch (e: ClassCastException)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed "
					+ "@raises tag section; expected a quoted exception type "
					+ "immediately following the @raises tag, however no such "
					+ "quoted exception type is listed.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		if (tokenCount == 1)
		{
			raises.add(
				StacksRaisesTag(
					tempName,
					StacksDescription(mutableListOf())))
		}
		else
		{
			val rest = tagContentTokens.subList(1, tokenCount)
			raises.add(
				StacksRaisesTag(
					tempName,
					StacksDescription(rest)))
		}
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksRestrictsTag(tagContentTokens: List<AbstractStacksToken>)
	{
		val tokenCount = tagContentTokens.size

		if (tokenCount < 1)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @restricts "
					+ "tag section; has too few components.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		val tempName: QuotedStacksToken
		try
		{
			tempName = tagContentTokens[0] as QuotedStacksToken
		}
		catch (e: ClassCastException)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed "
					+ "@restricts tag section; expected a quoted type "
					+ "immediately following the @restricts tag, however no such "
					+ "quoted type is listed.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		if (tokenCount == 1)
		{
			restricts.add(
				StacksRestrictsTag(
					tempName,
					StacksDescription(mutableListOf())))
		}
		else
		{
			val rest = tagContentTokens.subList(1, tokenCount)
			restricts.add(
				StacksRestrictsTag(
					tempName,
					StacksDescription(rest)))
		}
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksReturnsTag(
		tagContentTokens: List<AbstractStacksToken>)
	{
		val tokenCount = tagContentTokens.size

		if (tokenCount < 1)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed "
					+ "@returns tag section; has too few components.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		val tempName: RegionStacksToken
		try
		{
			tempName = tagContentTokens[0] as RegionStacksToken
		}
		catch (e: ClassCastException)
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @returns "
					+ "tag section; expected a quoted return type immediately  "
					+ "following the @returns tag, however no such quoted "
					+ "return type is identifiable.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		if (tokenCount == 1)
		{
			returns.add(
				StacksReturnTag(
					tempName,
					StacksDescription(mutableListOf())))
		}
		else
		{
			val rest = tagContentTokens.subList(1, tokenCount)
			returns.add(
				StacksReturnTag(
					tempName,
					StacksDescription(rest)))
		}
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksSeesTag(tagContentTokens: List<AbstractStacksToken>)
	{
		if (tagContentTokens.size == 1)
		{
			try
			{
				sees.add(
					StacksSeeTag(
						tagContentTokens[0] as RegionStacksToken))
			}
			catch (e: ClassCastException)
			{
				val errorMessage = String.format(
					"\n<li><strong>%s"
						+ "</strong><em> Line #: %d</em>: Malformed @sees "
						+ "tag section; expected a semantic link (bracketed "
						+ "{}) or quoted @sees content.</li>",
					moduleLeafName,
					commentStartLine)
				throw StacksCommentBuilderException(
					errorMessage,
					this)
			}

		}
		else
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @sees tag section; "
					+ "wrong # of @sees components.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksStickyTag(tagContentTokens: List<AbstractStacksToken>)
	{
		if (tagContentTokens.size == 0)
		{
			stickies.add(StacksStickyTag())
		}
		else
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @sticky tag section; "
					+ "expected no tokens to follow @sticky tag.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksSupertypeTag(tagContentTokens: List<AbstractStacksToken>)
	{
		for (token in tagContentTokens)
		{
			try
			{
				supertypes.add(StacksSuperTypeTag(token as QuotedStacksToken))
			}
			catch (e: ClassCastException)
			{
				val errorMessage = String.format(
					"\n<li><strong>%s"
						+ "</strong><em> Line #: %d</em>: Malformed @supertype "
						+ "tag section; expected a series of quoted supertype "
						+ "names immediately following the @supertype tag, however "
						+ "does not start with a quoted supertype is listed.</li>",
					moduleLeafName,
					commentStartLine)
				throw StacksCommentBuilderException(
					errorMessage,
					this)
			}

		}
	}

	/**
	 * @param tagContentTokens
	 *   The tokens held by the tag
	 * @throws ClassCastException
	 * @throws StacksCommentBuilderException
	 */
	@Throws(ClassCastException::class, StacksCommentBuilderException::class)
	fun addStacksTypeTag(tagContentTokens: List<AbstractStacksToken>)
	{
		if (tagContentTokens.size == 1)
		{
			try
			{
				types.add(
					StacksTypeTag(
						tagContentTokens[0] as QuotedStacksToken))
			}
			catch (e: ClassCastException)
			{
				val errorMessage = String.format(
					"\n<li><strong>%s"
						+ "</strong><em> Line #: %d</em>: Malformed @returns "
						+ "tag section; expected quoted type.</li>",
					moduleLeafName,
					commentStartLine)
				throw StacksCommentBuilderException(
					errorMessage,
					this)
			}

		}
		else
		{
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed @type tag section; "
					+ "has wrong # components.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}
	}

	init
	{
		val tempTokens = mutableListOf<QuotedStacksToken>()
		tempTokens.add(
			QuotedStacksToken.create("Unclassified", 0, 0, 0, moduleName))
		this.categories.add(StacksCategoryTag(tempTokens))
	}

	/**
	 * @return The appropriately built
	 * [Comment&#32;Implementation][AvailComment]
	 * @throws StacksCommentBuilderException
	 */
	@Throws(StacksCommentBuilderException::class)
	fun createStacksComment(): AvailComment?
	{

		if (types.isNotEmpty() && methods.isEmpty() && globalVariables.isEmpty()
			&& modules.isEmpty())
		{
			if (types.size == 1)
			{
				val signature =
					CommentSignature(
						types[0].typeName().lexeme, moduleName)
				return ClassComment(
					signature,
					commentStartLine,
					authors,
					sees,
					description,
					categories,
					aliases,
					supertypes,
					fields,
					stickies.isNotEmpty())
			}
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed has wrong # of "
					+ "@types identifying tags.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}

		if (types.isEmpty() && (methods.isNotEmpty() || macros.isNotEmpty())
			&& globalVariables.isEmpty() && modules.isEmpty())
		{
			if (macros.isEmpty())
			{
				if (methods.size > 1)
				{
					val errorMessage = String.format(
						"\n<li><strong>%s"
							+ "</strong><em> Line #: %d</em>: Malformed has wrong # of "
							+ "@method identifying tags.</li>",
						moduleLeafName,
						commentStartLine)
					throw StacksCommentBuilderException(
						errorMessage,
						this)
				}
				if (restricts.isNotEmpty() && parameters.isEmpty() &&
					forbids.isEmpty())
				{
					val orderedInputTypes = mutableListOf<String>()
					for (restrict in restricts)
					{
						orderedInputTypes.add(restrict.paramMetaType().lexeme)
					}

					val signature =
						SemanticRestrictionCommentSignature(
							methods[0].methodName().lexeme,
							moduleName,
							orderedInputTypes)

					return SemanticRestrictionComment(
						signature,
						commentStartLine,authors,
						sees,
						description,
						categories,
						aliases,
						restricts,
						returns)
				}

				if (restricts.isEmpty() && parameters.isNotEmpty() &&
					forbids.isEmpty())
				{
					if (returns.isEmpty())
					{
						val errorMessage = String.format(
							"\n<li><strong>%s"
								+ "</strong><em> Line #: %d</em>: Malformed comment; "
								+ "has no obvious @returns tag.</li>",
							moduleLeafName,
							commentStartLine)
						throw StacksCommentBuilderException(
							errorMessage,
							this)
					}
					val orderedInputTypes = mutableListOf<String>()
					for (param in parameters)
					{
						orderedInputTypes.add(param.paramType().lexeme)
					}

					val signature = MethodCommentSignature(
							methods[0].methodName().lexeme,
							moduleName, orderedInputTypes,
							returns[0].returnType().lexeme)

					return MethodComment(
						signature,
						commentStartLine, authors, sees, description,
						categories, aliases, parameters, returns[0], raises,
						stickies.isNotEmpty())
				}

				if (restricts.isEmpty() && parameters.isEmpty() &&
					!forbids.isEmpty())
				{
					val signature =
						CommentSignature(
							methods[0].methodName().lexeme, moduleName)

					return GrammaticalRestrictionComment(
						signature,
						commentStartLine,
						authors,
						sees,
						description,
						categories,
						aliases,
						forbids)
				}

				if (restricts.isEmpty() && parameters.isEmpty() &&
					forbids.isEmpty() && returns.isNotEmpty())
				{
					val orderedInputTypes = mutableListOf<String>()

					val signature = MethodCommentSignature(
						methods[0].methodName().lexeme,
						moduleName,
						orderedInputTypes,
						returns[0].returnType().lexeme)

					return MethodComment(
						signature,
						commentStartLine,
						authors,
						sees,
						description,
						categories,
						aliases,
						parameters,
						returns[0],
						raises,
						stickies.isNotEmpty())
				}
			}
			else
			{
				if (macros.size > 1)
				{
					val errorMessage = String.format(
						"\n<li><strong>%s"
							+ "</strong><em> Line #: %d</em>: Malformed has wrong # of "
							+ "@macro identifying tags.</li>",
						moduleLeafName,
						commentStartLine)
					throw StacksCommentBuilderException(
						errorMessage,
						this)
				}
				if (restricts.isNotEmpty() && parameters.isEmpty() &&
					forbids.isEmpty())
				{
					val orderedInputTypes = mutableListOf<String>()
					for (restrict in restricts)
					{
						orderedInputTypes.add(restrict.paramMetaType().lexeme)
					}

					val signature =
						SemanticRestrictionCommentSignature(
							macros[0].methodName().lexeme,
							moduleName,
							orderedInputTypes)

					return SemanticRestrictionComment(
						signature,
						commentStartLine,
						authors,
						sees,
						description,
						categories,
						aliases,
						restricts,
						returns)
				}

				if (
					restricts.isEmpty()
					&& parameters.isNotEmpty()
					&& forbids.isEmpty())
				{
					if (returns.isEmpty())
					{
						val errorMessage = String.format(
							"\n<li><strong>%s"
								+ "</strong><em> Line #: %d</em>: Malformed comment; "
								+ "has no obvious @returns tag.</li>",
							moduleLeafName,
							commentStartLine)
						throw StacksCommentBuilderException(
							errorMessage,
							this)
					}
					val orderedInputTypes = mutableListOf<String>()
					for (param in parameters)
					{
						orderedInputTypes.add(param.paramType().lexeme)
					}

					val signature = MethodCommentSignature(
						macros[0].methodName().lexeme,
						moduleName,
						orderedInputTypes,
						returns[0].returnType().lexeme)

					return MacroComment(
						signature,
						commentStartLine,
						authors,
						sees,
						description,
						categories,
						aliases,
						parameters,
						returns[0],
						raises,
						stickies.isNotEmpty())
				}

				if (restricts.isEmpty() && parameters.isEmpty() &&
					!forbids.isEmpty())
				{
					val signature =
						CommentSignature(
							macros[0].methodName().lexeme, moduleName)

					return GrammaticalRestrictionComment(
							signature, commentStartLine, authors, sees,
							description, categories, aliases, forbids)
				}

				if (restricts.isEmpty() && parameters.isEmpty() &&
					forbids.isEmpty() && returns.isNotEmpty())
				{
					val orderedInputTypes = mutableListOf<String>()

					val signature = MethodCommentSignature(
							macros[0].methodName().lexeme, moduleName,
							orderedInputTypes,
							returns[0].returnType().lexeme)

					return MacroComment(
						signature,
						commentStartLine, authors, sees, description,
						categories, aliases, parameters, returns[0], raises,
						stickies.isNotEmpty())
				}
			}
		}

		if (types.isEmpty() && methods.isEmpty() && globalVariables.isNotEmpty()
			&& modules.isEmpty())
		{
			if (globalVariables.size == 1)
			{
				val signature =
					GlobalCommentSignature(
						globalVariables[0].globalName().lexeme,
						moduleName,
						globalVariables[0].globalType().lexeme)

				return GlobalComment(
					signature,
					commentStartLine,
					authors,
					sees,
					description,
					categories,
					aliases,
					globalVariables[0])
			}
			val errorMessage = String.format(
				"\n<li><strong>%s"
					+ "</strong><em> Line #: %d</em>: Malformed comment; has "
					+ "wrong # of @global identifying tags.</li>",
				moduleLeafName,
				commentStartLine)
			throw StacksCommentBuilderException(
				errorMessage,
				this)
		}
		if (types.isEmpty() && methods.isEmpty() && globalVariables.isEmpty()
			&& modules.isNotEmpty())
		{
			val signature =
				CommentSignature(moduleLeafName, moduleName)

			val moduleComment = ModuleComment(
				signature, commentStartLine,
				authors, sees, description, false)

			linkingFileMap.addModuleComment(moduleComment)

			return null
		}
		if (types.isEmpty() && methods.isEmpty() && globalVariables.isEmpty()
			&& authors.isEmpty() && fields.isEmpty() && forbids.isEmpty()
			&& parameters.isEmpty() && raises.isEmpty() && restricts.isEmpty()
			&& returns.isEmpty() && sees.isEmpty() && supertypes.isEmpty()
			&& types.isEmpty() && categories.isNotEmpty())
		{
			//Defining of a category
			val onlyTag = categories[0]

			if (onlyTag.categories().size == 1)
			{
				linkingFileMap
					.addCategoryToDescription(
						onlyTag.categories()[0].lexeme, description)
				return null
			}
		}

		val errorMessage = String.format(
			"\n<li><strong>%s"
				+ "</strong><em> Line #: %d</em>: Malformed comment has no "
				+ "distinguishing identifying tags that indicate the type of "
				+ "comment.</li>",
			moduleLeafName,
			commentStartLine)
		throw StacksCommentBuilderException(
			errorMessage,
			this)
	}

	companion object
	{

		/**
		 * Create a [CommentBuilder].
		 *
		 * @param moduleName
		 *   The name of the module the comment is in.
		 * @param commentStartLine
		 *   The start line in the module of the comment being built
		 * @param linkingFileMap
		 * @return
		 *   A `CommentBuilder`.
		 * @throws StacksCommentBuilderException
		 */
		fun createBuilder(
			moduleName: String,
			commentStartLine: Int,
			linkingFileMap: LinkingFileMap): CommentBuilder =
			CommentBuilder(
				moduleName, commentStartLine, linkingFileMap)
	}
}
