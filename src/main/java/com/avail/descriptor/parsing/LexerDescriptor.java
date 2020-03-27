/*
 * LexerDescriptor.java
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

package com.avail.descriptor.parsing;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.compiler.scanning.LexingState;
import com.avail.descriptor.*;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.descriptor.functions.A_Function;
import com.avail.descriptor.methods.A_Method;
import com.avail.descriptor.methods.MacroDefinitionDescriptor;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.IntegerSlotsEnum;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.representation.ObjectSlotsEnum;
import com.avail.descriptor.tokens.A_Token;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.TypeTag;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.parsing.LexerDescriptor.IntegerSlots.HASH;
import static com.avail.descriptor.parsing.LexerDescriptor.ObjectSlots.*;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.types.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.types.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.types.SetTypeDescriptor.setTypeForSizesContentType;
import static com.avail.descriptor.types.TupleTypeDescriptor.oneOrMoreOf;
import static com.avail.descriptor.types.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.types.TypeDescriptor.Types.*;

/**
 * A method maintains all definitions that have the same name.  At compile time
 * a name is looked up and the corresponding method is stored as a literal in
 * the object code for a call site.  At runtime the actual function is located
 * within the method and then invoked.  The methods also keep track of
 * bidirectional dependencies, so that a change of membership causes an
 * immediate invalidation of optimized level two code that depends on the
 * previous membership.
 *
 * <p>Methods and macros are stored in separate lists.  Note that macros may be
 * polymorphic (multiple {@linkplain MacroDefinitionDescriptor definitions}),
 * and a lookup structure is used at compile time to decide which macro is most
 * specific.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class LexerDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * {@link BitField}s for the hash and the argument count.  See below.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * The hash of this lexer.  Set during construction.
		 */
		static final BitField HASH = bitField(
			HASH_AND_MORE, 0, 32);
	}

	/**
	 * The fields that are of type {@code AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/** The {@link A_Method} in which this lexer is defined. */
		LEXER_METHOD,

		/**
		 * The module in which this lexer was defined.  Other modules cannot see
		 * this lexer (i.e., it doesn't get invoke to produce tuples) unless the
		 * lexer's definition module is an ancestor of the module being
		 * compiled.
		 */
		DEFINITION_MODULE,

		/**
		 * The function to run (as the base call of a fiber), with the character
		 * at the current lexing point, to determine if the body function should
		 * be attempted.
		 */
		LEXER_FILTER_FUNCTION,

		/**
		 * The function to run (as the base call of a fiber) to generate some
		 * tokens from the source string and position.  The function should
		 * produce a tuple of potential {@link A_Token}s at this position, as
		 * produced by this lexer.  Each token may be seeded with the potential
		 * tokens that follow it.  Since each token also records the {@link
		 * LexingState} after it, there's no need to produce that separately.
		 */
		LEXER_BODY_FUNCTION;
	}

	private static final A_Type lexerFilterFunctionType =
		functionType(
			tuple(CHARACTER.o()),
			booleanType()
		).makeShared();

	public static A_Type lexerFilterFunctionType ()
	{
		return lexerFilterFunctionType;
	}

	private static final A_Type lexerBodyFunctionType =
		functionType(
			tuple(
				stringType(),
				inclusive(1, (1L << 32) - 1),
				inclusive(1, (1L << 28) - 1)),
			setTypeForSizesContentType(
				wholeNumbers(),
				oneOrMoreOf(TOKEN.o()))
		).makeShared();

	public static A_Type lexerBodyFunctionType ()
	{
		return lexerBodyFunctionType;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append("Lexer for ");
		boolean first = true;
		for (final A_Bundle eachBundle : object.lexerMethod().bundles())
		{
			if (!first)
			{
				aStream.append(" a.k.a. ");
			}
			aStream.append(eachBundle.message());
			first = false;
		}
	}

	@Override
	protected A_Module o_DefinitionModule (final AvailObject object)
	{
		return object.slot(DEFINITION_MODULE);
	}

	@Override
	protected A_Method o_LexerMethod (final AvailObject object)
	{
		return object.slot(LEXER_METHOD);
	}

	@Override
	protected A_Function o_LexerFilterFunction (final AvailObject object)
	{
		return object.slot(LEXER_FILTER_FUNCTION);
	}

	@Override
	protected A_Function o_LexerBodyFunction (final AvailObject object)
	{
		return object.slot(LEXER_BODY_FUNCTION);
	}

	@Override @AvailMethod
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		final AvailObject otherTraversed = another.traversed();
		if (otherTraversed.sameAddressAs(object))
		{
			return true;
		}
		if (otherTraversed.typeTag() != TypeTag.LEXER_TAG)
		{
			return false;
		}
		if (object.slot(HASH) != otherTraversed.hash())
		{
			return false;
		}
		return object.slot(LEXER_METHOD).equals(
				otherTraversed.definitionModule())
			&& object.slot(LEXER_FILTER_FUNCTION).equals(
				otherTraversed.lexerFilterFunction())
			&& object.slot(LEXER_BODY_FUNCTION).equals(
				otherTraversed.lexerBodyFunction())
			&& object.slot(DEFINITION_MODULE).equals(
				otherTraversed.definitionModule());
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		return object.slot(HASH);
	}

	@Override @AvailMethod
	protected A_Type o_Kind (final AvailObject object)
	{
		return LEXER.o();
	}

	@Override @AvailMethod
	protected AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// A method is always shared. Never make it immutable.
			return object.makeShared();
		}
		return object;
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("lexer");
		writer.write("filter");
		object.slot(LEXER_FILTER_FUNCTION).writeTo(writer);
		writer.write("body");
		object.slot(LEXER_BODY_FUNCTION).writeTo(writer);
		writer.write("method");
		object.slot(LEXER_METHOD).writeTo(writer);
		writer.write("module");
		object.slot(DEFINITION_MODULE).writeTo(writer);
		writer.endObject();
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("lexer");
		writer.write("filter");
		object.slot(LEXER_FILTER_FUNCTION).writeSummaryTo(writer);
		writer.write("body");
		object.slot(LEXER_BODY_FUNCTION).writeSummaryTo(writer);
		writer.write("method");
		object.slot(LEXER_METHOD).writeSummaryTo(writer);
		writer.write("module");
		object.slot(DEFINITION_MODULE).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Answer a new, fully populated lexer.  Also install it in the given module
	 * and method.  Note that the references from the lexer to the module and
	 * method should be considered back-pointers.
	 *
	 * @param lexerFilterFunction
	 *        A function that tests the character at the current lexing point to
	 *        determine whether to run the body of this lexer.
	 * @param lexerBodyFunction
	 *        The function that creates runs of tokens from source code.
	 * @param lexerMethod
	 *        The method associated with the lexer.
	 * @param definitionModule
	 *        The module in which the lexer is defined.
	 * @return A new method with no name.
	 */
	public static A_Lexer newLexer (
		final A_Function lexerFilterFunction,
		final A_Function lexerBodyFunction,
		final A_Method lexerMethod,
		final A_Module definitionModule)
	{
		AvailObject lexer = mutable.create();
		lexer.setSlot(LEXER_FILTER_FUNCTION, lexerFilterFunction);
		lexer.setSlot(LEXER_BODY_FUNCTION, lexerBodyFunction);
		lexer.setSlot(LEXER_METHOD, lexerMethod);
		lexer.setSlot(DEFINITION_MODULE, definitionModule);
		int hash = lexerFilterFunction.hash() + 0xC3C6A2F3;
		hash *= multiplier;
		hash -= lexerFilterFunction.hash() ^ 0x8080BF9B;
		hash *= multiplier;
		hash ^= lexerMethod.hash() + 0x520C1078;
		hash *= multiplier;
		hash ^= definitionModule.hash() - 0xB9C1F1E9;
		lexer.setSlot(HASH, hash);
		lexer = lexer.makeShared();
		lexerMethod.setLexer(lexer);
		if (!definitionModule.equalsNil())
		{
			definitionModule.addLexer(lexer);
		}
		return lexer;
	}

	/**
	 * Construct a new {@code LexerDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private LexerDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.LEXER_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link LexerDescriptor}. */
	private static final LexerDescriptor mutable =
		new LexerDescriptor(Mutability.MUTABLE);

	@Override
	public LexerDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link LexerDescriptor}. */
	private static final LexerDescriptor shared =
		new LexerDescriptor(Mutability.SHARED);

	@Override
	public LexerDescriptor immutable ()
	{
		// There is no immutable descriptor. Use the shared one.
		return shared;
	}

	@Override
	public LexerDescriptor shared ()
	{
		return shared;
	}
}
