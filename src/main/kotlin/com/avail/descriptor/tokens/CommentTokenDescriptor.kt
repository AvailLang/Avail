/*
 * CommentTokenDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.tokens

import com.avail.annotations.AvailMethod
import com.avail.annotations.HideFieldInDebugger
import com.avail.compiler.scanning.LexingState
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.types.TypeTag
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter

/**
 * This is a token of an Avail method/class comment.  More specifically, this
 * is text contained between forward slash-asterisk-asterisk and
 * asterisk-forward slash.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new `CommentTokenDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class CommentTokenDescriptor private constructor(mutability: Mutability)
	: TokenDescriptor(
		mutability,
		TypeTag.TOKEN_TAG,
		ObjectSlots::class.java,
		IntegerSlots::class.java)
{
	//this.string().asNativeString() - gets at the string contents.
	/**
	 * My class's slots of type int.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * [BitField]s for the token type code, the starting byte position, and
		 * the line number.
		 */
		START_AND_LINE;

		companion object
		{
			/**
			 * The line number in the source file. Currently signed 28 bits,
			 * which should be plenty.
			 */
			@JvmField
			val LINE_NUMBER = BitField(START_AND_LINE, 4, 28)

			/**
			 * The starting position in the source file. Currently signed 32
			 * bits, but this may change at some point -- not that we really
			 * need to parse 2GB of *Avail* source in one file, due to its
			 * deeply flexible syntax.
			 */
			@HideFieldInDebugger
			@JvmField
			val START = BitField(START_AND_LINE, 32, 32)

			init
			{
				assert(TokenDescriptor.IntegerSlots
			       .TOKEN_TYPE_AND_START_AND_LINE.ordinal == START_AND_LINE.ordinal)
				assert(TokenDescriptor.IntegerSlots.START.isSamePlaceAs(START))
				assert(TokenDescriptor.IntegerSlots.LINE_NUMBER
			       .isSamePlaceAs(LINE_NUMBER))
			}
		}
	}

	/**
	 * My class's slots of type AvailObject.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [string][StringDescriptor], exactly as it appeared in the source.
		 */
		STRING,

		/**
		 * A [raw pojo][RawPojoDescriptor] holding the [LexingState] after this
		 * token.
		 */
		NEXT_LEXING_STATE_POJO;

		companion object
		{
			init
			{
				assert(TokenDescriptor.ObjectSlots.STRING.ordinal
			       == STRING.ordinal)
				assert(TokenDescriptor.ObjectSlots.NEXT_LEXING_STATE_POJO.ordinal
			       == NEXT_LEXING_STATE_POJO.ordinal)
			}
		}
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum): Boolean =
			(e === ObjectSlots.NEXT_LEXING_STATE_POJO
		        || super.allowsImmutableToMutableReferenceInField(e))

	override fun o_SerializerOperation(
		self: AvailObject): SerializerOperation =
			SerializerOperation.COMMENT_TOKEN

	@AvailMethod
	override fun o_TokenType(self: AvailObject): TokenType =
		TokenType.COMMENT

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("token")
		writer.write("token type")
		writer.write(self.tokenType().name.toLowerCase().replace(
			'_', ' '))
		writer.write("start")
		writer.write(self.slot(IntegerSlots.START))
		writer.write("line number")
		writer.write(self.slot(IntegerSlots.LINE_NUMBER))
		writer.write("lexeme")
		self.slot(ObjectSlots.STRING).writeTo(writer)
		writer.endObject()
	}

	override fun mutable(): CommentTokenDescriptor = mutable

	// Answer the shared descriptor, since there isn't an immutable one.
	override fun immutable(): CommentTokenDescriptor = shared

	override fun shared(): CommentTokenDescriptor = shared

	companion object
	{
		/**
		 * Create and initialize a new comment token.
		 *
		 * @param string
		 *   The token text.
		 * @param start
		 *   The token's starting character position in the file.
		 * @param lineNumber
		 *   The line number on which the token occurred.
		 * @return The new comment token.
		 */
		fun newCommentToken(
			string: A_String?,
			start: Int,
			lineNumber: Int): A_Token
		{
			val instance = mutable.create()
			instance.setSlot(ObjectSlots.STRING, string!!)
			instance.setSlot(IntegerSlots.START, start)
			instance.setSlot(IntegerSlots.LINE_NUMBER, lineNumber)
			instance.setSlot(ObjectSlots.NEXT_LEXING_STATE_POJO, NilDescriptor.nil)
			return instance.makeShared()
		}

		/** The mutable [LiteralTokenDescriptor].  */
		private val mutable = CommentTokenDescriptor(Mutability.MUTABLE)

		/** The shared [LiteralTokenDescriptor].  */
		private val shared = CommentTokenDescriptor(Mutability.SHARED)
	}
}