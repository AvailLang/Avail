/*
 * TokenDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *    list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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

 import com.avail.annotations.EnumField
 import com.avail.annotations.EnumField.Converter
 import com.avail.annotations.HideFieldInDebugger
 import com.avail.compiler.scanning.LexingState
 import com.avail.descriptor.atoms.A_Atom
 import com.avail.descriptor.atoms.A_Atom.Companion.setAtomProperty
 import com.avail.descriptor.atoms.AtomDescriptor.Companion.createSpecialAtom
 import com.avail.descriptor.numbers.IntegerDescriptor
 import com.avail.descriptor.pojos.RawPojoDescriptor
 import com.avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
 import com.avail.descriptor.representation.A_BasicObject
 import com.avail.descriptor.representation.AbstractSlotsEnum
 import com.avail.descriptor.representation.AvailObject
 import com.avail.descriptor.representation.BitField
 import com.avail.descriptor.representation.Descriptor
 import com.avail.descriptor.representation.IntegerEnumSlotDescriptionEnum
 import com.avail.descriptor.representation.IntegerSlotsEnum
 import com.avail.descriptor.representation.Mutability
 import com.avail.descriptor.representation.NilDescriptor
 import com.avail.descriptor.representation.NilDescriptor.Companion.nil
 import com.avail.descriptor.representation.ObjectSlotsEnum
 import com.avail.descriptor.tokens.CommentTokenDescriptor.Companion.newCommentToken
 import com.avail.descriptor.tokens.TokenDescriptor.IntegerSlots.Companion.LINE_NUMBER
 import com.avail.descriptor.tokens.TokenDescriptor.IntegerSlots.Companion.START
 import com.avail.descriptor.tokens.TokenDescriptor.IntegerSlots.Companion.TOKEN_TYPE_CODE
 import com.avail.descriptor.tokens.TokenDescriptor.ObjectSlots.NEXT_LEXING_STATE_POJO
 import com.avail.descriptor.tokens.TokenDescriptor.ObjectSlots.STRING
 import com.avail.descriptor.tokens.TokenDescriptor.TokenType.Companion.lookupTokenType
 import com.avail.descriptor.tuples.A_String
 import com.avail.descriptor.tuples.A_Tuple
 import com.avail.descriptor.tuples.StringDescriptor
 import com.avail.descriptor.types.A_Type
 import com.avail.descriptor.types.TokenTypeDescriptor.tokenType
 import com.avail.descriptor.types.TypeDescriptor.Types
 import com.avail.descriptor.types.TypeTag
 import com.avail.serialization.SerializerOperation
 import com.avail.utility.PrefixSharingList.append
 import com.avail.utility.json.JSONWriter
 import java.util.*

/**
 * I represent a token scanned from Avail source code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `TokenDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] to embed in the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no integer slots.
 */
open class TokenDescriptor protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?
) : Descriptor(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
{
	/**
	 * My class's slots of type int.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * [BitField]s for the token type code, the starting byte position, and
		 * the line number.
		 */
		TOKEN_TYPE_AND_START_AND_LINE;

		companion object
		{
			/**
			 * The [ordinal][Enum.ordinal] of the [TokenType] that indicates
			 * what basic kind of token this is.  Currently four bits are
			 * reserved for this purpose.
			 */
			@JvmField
			@EnumField(describedBy = TokenType::class)
			val TOKEN_TYPE_CODE = BitField(TOKEN_TYPE_AND_START_AND_LINE, 0, 4)

			/**
			 * The line number in the source file. Currently signed 28 bits,
			 * which should be plenty.
			 */
			@JvmField
			@EnumField(
				describedBy = Converter::class,
				lookupMethodName = "decimal")
			val LINE_NUMBER = BitField(TOKEN_TYPE_AND_START_AND_LINE, 4, 28)

			/**
			 * The starting position in the source file. Currently signed 32
			 * bits, but this may change at some point -- not that we really
			 * need to parse 2GB of *Avail* source in one file, due to its
			 * deeply flexible syntax.
			 */
			@JvmField
			@HideFieldInDebugger
			val START = BitField(TOKEN_TYPE_AND_START_AND_LINE, 32, 32)
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
		 * A [raw&#32;pojo][RawPojoDescriptor] holding the [LexingState] after
		 * this token.
		 */
		NEXT_LEXING_STATE_POJO
	}

	/**
	 * An enumeration that lists the basic kinds of tokens that can be
	 * encountered.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	enum class TokenType : IntegerEnumSlotDescriptionEnum
	{
		/**
		 * A special type of token that is appended to the actual tokens of the
		 * file to simplify end-of-file processing.
		 */
		END_OF_FILE,

		/**
		 * A sequence of characters suitable for an Avail identifier, which
		 * roughly corresponds to characters in a Java identifier.
		 */
		KEYWORD,

		/**
		 * A literal token, detected at lexical scanning time. Only applicable
		 * for a [LiteralTokenDescriptor].
		 */
		LITERAL,

		/**
		 * A single operator character, which is anything that isn't whitespace,
		 * a keyword character, or an Avail reserved character.
		 */
		OPERATOR,

		/**
		 * A token that is the entirety of an Avail method/class comment.  This
		 * is text contained between slash-asterisk and asterisk-slash.  Only
		 * applicable for [CommentTokenDescriptor].
		 */
		COMMENT,

		/**
		 * A token representing one or more whitespace characters separating
		 * other tokens.  These tokens are skipped by the normal parsing
		 * machinery, although some day we'll provide a notation within method
		 * names to capture whitespace tokens as arguments.
		 */
		WHITESPACE;

		override fun fieldName(): String = name

		override fun fieldOrdinal(): Int = ordinal

		/** The associated special atom.  */
		@JvmField
		val atom: A_Atom =
			createSpecialAtom(name.toLowerCase().replace('_', ' ')).apply {
				setAtomProperty(
					StaticInit.tokenTypeOrdinalKey,
					IntegerDescriptor.fromInt(ordinal))
			}

		companion object
		{
			/** An array of all [TokenType] enumeration values.  */
			private val all = values()

			/**
			 * Answer the `TokenType` enumeration value having the given
			 * ordinal.
			 *
			 * @param ordinal
			 *   The [ordinal] of the `TokenType`.
			 * @return
			 *   The `TokenType`.
			 */
			@JvmStatic
			fun lookupTokenType(ordinal: Int): TokenType = all[ordinal]
		}

	}

	/** A static class for untangling enum initialization.  */
	object StaticInit
	{
		/**
		 * An internal [atom][A_Atom] for the other atoms of this
		 * enumeration. It is keyed to the [ordinal][TokenType.fieldOrdinal]
		 * of a [TokenType].
		 */
		@JvmField
		var tokenTypeOrdinalKey: A_Atom = createSpecialAtom(
			"token type ordinal key")
	}

	public override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = e === NEXT_LEXING_STATE_POJO

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append(String.format(
			"%s (%s) @ %d:%d",
			self.tokenType().name.toLowerCase().replace('_', ' '),
			self.slot(STRING),
			self.slot(START),
			self.slot(LINE_NUMBER)))
	}

	override fun o_ClearLexingState(self: AvailObject)
	{
		self.setSlot(NEXT_LEXING_STATE_POJO, nil)
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject)
		: Boolean = another.equalsToken(self)

	override fun o_EqualsToken(self: AvailObject, aToken: A_Token): Boolean =
		(self.string().equals(aToken.string())
			&& self.start() == aToken.start()
			&& self.tokenType() == aToken.tokenType()
			&& self.isLiteralToken() == aToken.isLiteralToken()
			&& (!self.isLiteralToken()
				|| self.literal().equals(aToken.literal())))

	override fun o_Hash(self: AvailObject): Int =
		((self.string().hash() * AvailObject.multiplier
		         + self.start()) * AvailObject.multiplier
			+ self.tokenType().ordinal
			xor 0x62CE7BA2)

	override fun o_Kind(self: AvailObject): A_Type = tokenType(self.tokenType())

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type
	): Boolean =
		(aType.isSupertypeOfPrimitiveTypeEnum(Types.TOKEN)
			|| (aType.isTokenType && self.tokenType() == aType.tokenType()))

	override fun o_LineNumber(self: AvailObject): Int = self.slot(LINE_NUMBER)

	override fun o_LowerCaseString(self: AvailObject): A_String =
		lowerCaseStringFrom(self)

	override fun o_NextLexingState(self: AvailObject): LexingState =
		self.slot(NEXT_LEXING_STATE_POJO).javaObjectNotNull()

	override fun o_NextLexingStatePojo(self: AvailObject): AvailObject =
		self.slot(NEXT_LEXING_STATE_POJO)

	override fun o_SetNextLexingStateFromPrior(
		self: AvailObject,
		priorLexingState: LexingState
	) {
		// First, figure out where the token ends.
		val string: A_Tuple = self.slot(STRING)
		val stringSize = string.tupleSize()
		val positionAfter = self.slot(START) + stringSize
		var line = self.slot(LINE_NUMBER)
		line += (1..stringSize).count {
			string.tupleCodePointAt(it) == '\n'.toInt()
		}
		// Now lookup/capture the next state.
		val allTokens = append(priorLexingState.allTokens, self)
		val state = LexingState(
			priorLexingState.compilationContext, positionAfter, line, allTokens)
		self.setSlot(NEXT_LEXING_STATE_POJO, identityPojo(state).makeShared())
		self.makeShared()
	}

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.TOKEN

	override fun o_Start(self: AvailObject): Int = self.slot(START)

	override fun o_String(self: AvailObject): A_String = self.slot(STRING)

	override fun o_TokenType(self: AvailObject): TokenType =
		lookupTokenType(self.slot(TOKEN_TYPE_CODE))

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("token")
		writer.write("token type")
		writer.write(self.tokenType().name.toLowerCase().replace('_', ' '))
		writer.write("start")
		writer.write(self.slot(START))
		writer.write("line number")
		writer.write(self.slot(LINE_NUMBER))
		writer.write("lexeme")
		self.slot(STRING).writeTo(writer)
		writer.endObject()
	}

	override fun mutable() = mutable

	// Answer the shared descriptor, since there isn't an immutable one.
	override fun immutable() = shared

	override fun shared() = shared

	companion object
	{
		/**
		 * Lazily compute and install the lowercase variant of the specified
		 * token's lexeme.  The caller must handle locking as needed.  Cache the
		 * lowercase variant within the object.
		 *
		 * @param token
		 *   A token.
		 * @return
		 *   The lowercase lexeme (an Avail string).
		 */
		private fun lowerCaseStringFrom(token: AvailObject): A_String
		{
			val nativeOriginal = token.slot(STRING).asNativeString()
			val nativeLowerCase = nativeOriginal.toLowerCase()
			return StringDescriptor.stringFrom(nativeLowerCase)
		}

		/**
		 * Create and initialize a new [A_Token].  The [NEXT_LEXING_STATE_POJO]
		 * is initially set to [NilDescriptor.nil].  For a token constructed by
		 * a lexer body, this pojo is updated automatically by the lexical
		 * scanning machinery to wrap a new [LexingState].  That machinery also
		 * sets up the new scanning position, the new line number, and the list
		 * of [LexingState.allTokens].
		 *
		 * @param string
		 *   The token text.
		 * @param start
		 *   The token's starting character position in the file.
		 * @param lineNumber
		 *   The line number on which the token occurred.
		 * @param tokenType
		 *   The type of token to create.
		 * @return
		 *   The new token.
		 */
		fun newToken(
			string: A_String,
			start: Int,
			lineNumber: Int,
			tokenType: TokenType): A_Token
		{
			if (tokenType == TokenType.COMMENT)
			{
				return newCommentToken(string, start, lineNumber)
			}
			return with(mutable.create()) {
				setSlot(STRING, string)
				setSlot(START, start)
				setSlot(LINE_NUMBER, lineNumber)
				setSlot(TOKEN_TYPE_CODE, tokenType.ordinal)
				setSlot(NEXT_LEXING_STATE_POJO, nil)
				makeShared()
			}
		}

		/** The mutable [TokenDescriptor].  */
		private val mutable = TokenDescriptor(
			Mutability.MUTABLE,
			TypeTag.TOKEN_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)

		/** The shared [TokenDescriptor].  */
		private val shared = TokenDescriptor(
			Mutability.SHARED,
			TypeTag.TOKEN_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)
	}
}
