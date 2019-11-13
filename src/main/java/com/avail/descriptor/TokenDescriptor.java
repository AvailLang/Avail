/*
 * TokenDescriptor.java
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

package com.avail.descriptor;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.EnumField;
import com.avail.annotations.EnumField.Converter;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.HideFieldJustForPrinting;
import com.avail.compiler.scanning.LexingState;
import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.descriptor.tuples.A_String;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;

import static com.avail.descriptor.AtomDescriptor.createSpecialAtom;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.CommentTokenDescriptor.newCommentToken;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TokenDescriptor.IntegerSlots.*;
import static com.avail.descriptor.TokenDescriptor.ObjectSlots.NEXT_LEXING_STATE_POJO;
import static com.avail.descriptor.TokenDescriptor.ObjectSlots.STRING;
import static com.avail.descriptor.TokenDescriptor.StaticInit.tokenTypeOrdinalKey;
import static com.avail.descriptor.TokenDescriptor.TokenType.lookupTokenType;
import static com.avail.descriptor.TokenTypeDescriptor.tokenType;
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;
import static com.avail.utility.PrefixSharingList.append;


/**
 * I represent a token scanned from Avail source code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class TokenDescriptor
extends Descriptor
{
	/**
	 * My class's slots of type int.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * {@link BitField}s for the token type code, the starting byte
		 * position, and the line number.
		 */
		TOKEN_TYPE_AND_START_AND_LINE;

		/**
		 * The {@link Enum#ordinal() ordinal} of the {@link TokenType} that
		 * indicates what basic kind of token this is.  Currently four bits are
		 * reserved for this purpose.
		 */
		@EnumField(describedBy = TokenType.class)
		static final BitField TOKEN_TYPE_CODE =
			bitField(TOKEN_TYPE_AND_START_AND_LINE, 0, 4);

		/**
		 * The line number in the source file. Currently signed 28 bits, which
		 * should be plenty.
		 */
		@EnumField(
			describedBy = Converter.class,
			lookupMethodName = "decimal")
		static final BitField LINE_NUMBER =
			bitField(TOKEN_TYPE_AND_START_AND_LINE, 4, 28);

		/**
		 * The starting position in the source file. Currently signed 32 bits,
		 * but this may change at some point -- not that we really need to parse
		 * 2GB of <em>Avail</em> source in one file, due to its deeply flexible
		 * syntax.
		 */
		@HideFieldInDebugger
		static final BitField START =
			bitField(TOKEN_TYPE_AND_START_AND_LINE, 32, 32);
	}

	/**
	 * My class's slots of type AvailObject.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain StringDescriptor string}, exactly as it appeared in
		 * the source.
		 */
		STRING,

		/**
		 * A {@link RawPojoDescriptor raw pojo} holding the {@link LexingState}
		 * after this token.
		 */
		@HideFieldJustForPrinting
		NEXT_LEXING_STATE_POJO
	}

	/**
	 * An enumeration that lists the basic kinds of tokens that can be
	 * encountered.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public enum TokenType
	implements IntegerEnumSlotDescriptionEnum
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
		 * for a {@link LiteralTokenDescriptor}.
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
		 * applicable for {@link CommentTokenDescriptor}.
		 */
		COMMENT,

		/**
		 * A token representing one or more whitespace characters separating
		 * other tokens.  These tokens are skipped by the normal parsing
		 * machinery, although some day we'll provide a notation within method
		 * names to capture whitespace tokens as arguments.
		 */
		WHITESPACE;

		/** An array of all {@link TokenType} enumeration values. */
		private static final TokenType[] all = values();

		/** The associated special atom. */
		public final A_Atom atom;

		/**
		 * Construct a {@code TokenType} and its associated {@linkplain A_Atom
		 * special atom}.
		 */
		TokenType ()
		{
			atom = createSpecialAtom(name().toLowerCase().replace('_', ' '));
			atom.setAtomProperty(tokenTypeOrdinalKey, fromInt(ordinal()));
		}

		/**
		 * Answer the {@code TokenType} enumeration value having the given
		 * ordinal.
		 *
		 * @param ordinal
		 *        The {@link #ordinal()} of the {@code TokenType}.
		 * @return The {@code TokenType}.
		 */
		public static TokenType lookupTokenType (final int ordinal)
		{
			return all[ordinal];
		}
	}

	/** A static class for untangling enum initialization. */
	public static final class StaticInit
	{
		/** Don't instantiate this. */
		private StaticInit () { };

		/**
		 * An internal [atom][A_Atom] for the other atoms of this
		 * enumeration. It is keyed to the [ordinal][TokenType.ordinal]
		 * of a [TokenType].
		 */
		public static A_Atom tokenTypeOrdinalKey = createSpecialAtom(
			"token type ordinal key");
	}

	@Override
	protected boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return e == NEXT_LEXING_STATE_POJO;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append(String.format(
			"%s (%s) @ %d:%d",
			object.tokenType().name().toLowerCase().replace('_', ' '),
			object.slot(STRING),
			object.slot(START),
			object.slot(LINE_NUMBER)));
	}

	/**
	 * Lazily compute and install the lowercase variant of the specified token's
	 * lexeme.  The caller must handle locking as needed.  Cache the lowercase
	 * variant within the object.
	 *
	 * @param token A token.
	 * @return The lowercase lexeme (an Avail string).
	 */
	private static A_String lowerCaseStringFrom (final AvailObject token)
	{
		final String nativeOriginal = token.slot(STRING).asNativeString();
		final String nativeLowerCase = nativeOriginal.toLowerCase();
		return stringFrom(nativeLowerCase);
	}

	@Override
	protected void o_ClearLexingState (final AvailObject object)
	{
		object.setSlot(NEXT_LEXING_STATE_POJO, nil);
	}

	@Override @AvailMethod
	protected boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsToken(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsToken (final AvailObject object, final A_Token aToken)
	{
		return object.string().equals(aToken.string())
			&& object.start() == aToken.start()
			&& object.tokenType() == aToken.tokenType()
			&& object.isLiteralToken() == aToken.isLiteralToken()
			&& (!object.isLiteralToken()
				|| object.literal().equals(aToken.literal()));
	}

	@Override @AvailMethod
	protected int o_Hash (final AvailObject object)
	{
		return
			(object.string().hash() * multiplier
				+ object.start()) * multiplier
				+ object.tokenType().ordinal()
			^ 0x62CE7BA2;
	}

	@Override @AvailMethod
	protected A_Type o_Kind (final AvailObject object)
	{
		return tokenType(object.tokenType());
	}

	@Override @AvailMethod
	protected boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aTypeObject)
	{
		return aTypeObject.isSupertypeOfPrimitiveTypeEnum(TOKEN)
			|| aTypeObject.isTokenType()
			&& object.tokenType() == aTypeObject.tokenType();
	}

	@Override @AvailMethod
	protected int o_LineNumber (final AvailObject object)
	{
		return object.slot(LINE_NUMBER);
	}

	@Override @AvailMethod
	protected A_String o_LowerCaseString (final AvailObject object)
	{
		return lowerCaseStringFrom(object);
	}

	@Override
	LexingState o_NextLexingState (final AvailObject object)
	{
		return object.slot(NEXT_LEXING_STATE_POJO).javaObjectNotNull();
	}

	@Override
	protected void o_SetNextLexingStateFromPrior (
		final AvailObject object,
		final LexingState priorLexingState)
	{
		// First, figure out where the token ends.
		final A_Tuple string = object.slot(STRING);
		final int stringSize = string.tupleSize();
		final int positionAfter = object.slot(START) + stringSize;
		int line = object.slot(LINE_NUMBER);
		for (int i = 1; i <= stringSize; i++)
		{
			if (string.tupleCodePointAt(i) == '\n')
			{
				line++;
			}
		}
		// Now lookup/capture the next state.
		final List<A_Token> allTokens =
			append(priorLexingState.getAllTokens(), object);
		final LexingState state = new LexingState(
			priorLexingState.getCompilationContext(),
			positionAfter,
			line,
			allTokens);
		object.setSlot(
			NEXT_LEXING_STATE_POJO, identityPojo(state).makeShared());
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.TOKEN;
	}

	@Override @AvailMethod
	protected int o_Start (final AvailObject object)
	{
		return object.slot(START);
	}

	@Override @AvailMethod
	protected A_String o_String (final AvailObject object)
	{
		return object.slot(STRING);
	}

	@Override @AvailMethod
	TokenType o_TokenType (final AvailObject object)
	{
		return lookupTokenType(object.slot(TOKEN_TYPE_CODE));
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("token");
		writer.write("token type");
		writer.write(object.tokenType().name().toLowerCase().replace(
			'_', ' '));
		writer.write("start");
		writer.write(object.slot(START));
		writer.write("line number");
		writer.write(object.slot(LINE_NUMBER));
		writer.write("lexeme");
		object.slot(STRING).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Create and initialize a new {@link A_Token}.  The {@link
	 * ObjectSlots#NEXT_LEXING_STATE_POJO} is initially set to {@link
	 * NilDescriptor#nil}.  For a token constructed by a lexer body, this pojo
	 * is updated automatically by the lexical scanning machinery to wrap a new
	 * {@link LexingState}.  That machinery also sets up the new scanning
	 * position, the new line number, and the list of {@link
	 * LexingState#getAllTokens()}.
	 *
	 * @param string
	 *        The token text.
	 * @param start
	 *        The token's starting character position in the file.
	 * @param lineNumber
	 *        The line number on which the token occurred.
	 * @param tokenType
	 *        The type of token to create.
	 * @return The new token.
	 */
	public static A_Token newToken (
		final A_String string,
		final int start,
		final int lineNumber,
		final TokenType tokenType)
	{
		if (tokenType == TokenType.COMMENT)
		{
			return newCommentToken(string, start, lineNumber);
		}
		final AvailObject instance = mutable.create();
		instance.setSlot(STRING, string);
		instance.setSlot(START, start);
		instance.setSlot(LINE_NUMBER, lineNumber);
		instance.setSlot(TOKEN_TYPE_CODE, tokenType.ordinal());
		instance.setSlot(NEXT_LEXING_STATE_POJO, nil);
		return instance.makeShared();
	}

	/**
	 * Construct a new {@code TokenDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param typeTag
	 *            The {@link TypeTag} to embed in the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected TokenDescriptor (
		final Mutability mutability,
		final TypeTag typeTag,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	/** The mutable {@link TokenDescriptor}. */
	private static final TokenDescriptor mutable =
		new TokenDescriptor(
			Mutability.MUTABLE,
			TypeTag.TOKEN_TAG,
			ObjectSlots.class,
			IntegerSlots.class);

	@Override
	TokenDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link TokenDescriptor}. */
	private static final TokenDescriptor shared =
		new TokenDescriptor(
			Mutability.SHARED,
			TypeTag.TOKEN_TAG,
			ObjectSlots.class,
			IntegerSlots.class);

	@Override
	TokenDescriptor immutable ()
	{
		// Answer the shared descriptor, since there isn't an immutable one.
		return shared;
	}

	@Override
	TokenDescriptor shared ()
	{
		return shared;
	}
}
