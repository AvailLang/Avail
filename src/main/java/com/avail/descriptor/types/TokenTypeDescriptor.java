/*
 * TokenTypeDescriptor.java
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

package com.avail.descriptor.types;

import com.avail.annotations.AvailMethod;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.representation.IntegerSlotsEnum;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.tokens.TokenDescriptor;
import com.avail.descriptor.tokens.TokenDescriptor.TokenType;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;

import static com.avail.descriptor.tokens.TokenDescriptor.TokenType.lookupTokenType;
import static com.avail.descriptor.types.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.types.TokenTypeDescriptor.IntegerSlots.TOKEN_TYPE_CODE;
import static com.avail.descriptor.types.TypeDescriptor.Types.TOKEN;

/**
 * I represent the type of some {@link TokenDescriptor tokens}. Like any object,
 * a particular token has an exact {@link InstanceTypeDescriptor instance type},
 * but {@code TokenTypeDescriptor} covariantly constrains a token's type by its
 * {@link TokenType}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class TokenTypeDescriptor
extends TypeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The {@linkplain TokenType type} constraint on a token's value.
		 */
		TOKEN_TYPE_CODE
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append(String.format(
			"%s token",
			object.tokenType().name().toLowerCase().replace('_', ' ')));
	}

	@Override
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsTokenType(object);
	}

	@Override
	protected boolean o_EqualsTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return object.tokenType() == aTokenType.tokenType();
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		return Integer.hashCode((int) object.slot(TOKEN_TYPE_CODE)) ^ 0xCD9A63B7;
	}

	@Override
	protected boolean o_IsTokenType (final AvailObject object)
	{
		return true;
	}

	@Override
	protected AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// There is no immutable descriptor, so share the object.
			return object.makeShared();
		}
		return object;
	}

	@Override
	protected boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		// Check if object (a type) is a subtype of aType (should also be a
		// type).
		return aType.isSupertypeOfTokenType(object);
	}

	@Override
	protected boolean o_IsSupertypeOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return object.tokenType() == aTokenType.tokenType();
	}

	@Override @AvailMethod
	protected TokenType o_TokenType (final AvailObject object)
	{
		return lookupTokenType((int) object.slot(TOKEN_TYPE_CODE));
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.TOKEN_TYPE;
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		if (object.equals(another))
		{
			return object;
		}
		if (object.isSubtypeOf(another))
		{
			return object;
		}
		if (another.isSubtypeOf(object))
		{
			return another;
		}
		return another.typeIntersectionOfTokenType(object);
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersectionOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return object.tokenType() == aTokenType.tokenType()
			? object
			: bottom();
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersectionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return TOKEN.superTests[primitiveTypeEnum.ordinal()]
			? object
			: bottom();
	}

	@Override
	protected A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return another.typeUnionOfTokenType(object);
	}

	@Override @AvailMethod
	protected A_Type o_TypeUnionOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return object.tokenType() == aTokenType.tokenType()
			? object
			: TOKEN.o();
	}

	@Override @AvailMethod
	protected A_Type o_TypeUnionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return TOKEN.unionTypes[primitiveTypeEnum.ordinal()];
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("token type");
		writer.write("token type");
		writer.write(object.tokenType().name().toLowerCase().replace(
			'_', ' '));
		writer.endObject();
	}

	/**
	 * Create a new token type whose values comply with the given
	 * {@link TokenType}.
	 *
	 * @param tokenType
	 *        The type with which to constrain values.
	 * @return A {@link TokenTypeDescriptor token type}.
	 */
	public static AvailObject tokenType (final TokenType tokenType)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(TOKEN_TYPE_CODE, tokenType.ordinal());
		return instance;
	}

	/**
	 * Construct a new {@link TokenTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private TokenTypeDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.NONTYPE_TYPE_TAG, null, IntegerSlots.class);
	}

	/** The mutable {@link TokenTypeDescriptor}. */
	private static final TokenTypeDescriptor mutable =
		new TokenTypeDescriptor(Mutability.MUTABLE);

	@Override
	public TokenTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link TokenTypeDescriptor}. */
	private static final TokenTypeDescriptor shared =
		new TokenTypeDescriptor(Mutability.SHARED);

	@Override
	public TokenTypeDescriptor immutable ()
	{
		// There is no immutable variant.
		return shared;
	}

	@Override
	public TokenTypeDescriptor shared ()
	{
		return shared;
	}
}
