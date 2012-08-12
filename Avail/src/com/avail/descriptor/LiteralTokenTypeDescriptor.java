/**
 * LiteralTokenTypeDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.LiteralTokenTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * I represent the type of some {@link LiteralTokenDescriptor literal tokens}.
 * Like any object, a particular literal token has an exact {@link
 * InstanceTypeDescriptor instance type}, and {@link TokenDescriptor tokens} in
 * general have a simple {@link PrimitiveTypeDescriptor primitive type} of
 * {@link TypeDescriptor.Types#TOKEN}, but {@code LiteralTokenTypeDescriptor}
 * covariantly constraints a literal token's type with the type of the value it
 * contains.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class LiteralTokenTypeDescriptor
extends TypeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The type constraint on a literal token's value.
		 */
		LITERAL_TYPE
	}


	@Override @AvailMethod
	AvailObject o_LiteralType (
		final AvailObject object)
	{
		return object.slot(LITERAL_TYPE);
	}

	@Override @AvailMethod int o_Hash(
		final AvailObject object)
	{
		return object.slot(LITERAL_TYPE).hash() ^ 0xF47FF1B1;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append("literal token⇒");
		object.literalType().printOnAvoidingIndent(
			aStream,
			recursionList,
			indent + 1);
	}

	@Override
	boolean o_IsLiteralTokenType (
		final AvailObject object)
	{
		return true;
	}

	@Override
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return another.equalsLiteralTokenType(object);
	}

	@Override
	boolean o_EqualsLiteralTokenType (
		final AvailObject object,
		final AvailObject aLiteralTokenType)
	{
		return object.literalType().equals(aLiteralTokenType.literalType());
	}

	@Override
	boolean o_IsSubtypeOf (
		final AvailObject object,
		final AvailObject aType)
	{
		// Check if object (a type) is a subtype of aType (should also be a
		// type).
		return aType.isSupertypeOfLiteralTokenType(object);
	}

	@Override
	boolean o_IsSupertypeOfLiteralTokenType (
		final AvailObject object,
		final AvailObject aLiteralTokenType)
	{
		return aLiteralTokenType.literalType().isSubtypeOf(
			object.literalType());
	}

	@Override @AvailMethod
	AvailObject o_TypeIntersection (
		final AvailObject object,
		final AvailObject another)
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
		return another.typeIntersectionOfLiteralTokenType(object);
	}

	@Override @AvailMethod
	AvailObject o_TypeIntersectionOfLiteralTokenType (
		final AvailObject object,
		final AvailObject aLiteralTokenType)
	{
		// Note that the 'inner' type must be made immutable in case one of the
		// input literal token types is mutable (and may be destroyed
		// *recursively* by post-primitive code).
		final AvailObject instance = object.literalType().typeIntersection(
			aLiteralTokenType.literalType());
		instance.makeImmutable();
		return LiteralTokenTypeDescriptor.create(instance);
	}

	@Override
	AvailObject o_TypeUnion (
		final AvailObject object,
		final AvailObject another)
	{
		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return another.typeUnionOfLiteralTokenType(object);
	}

	@Override @AvailMethod
	AvailObject o_TypeUnionOfLiteralTokenType (
		final AvailObject object,
		final AvailObject aLiteralTokenType)
	{
		// Note that the 'inner' type must be made immutable in case one of the
		// input literal token types is mutable (and may be destroyed
		// *recursively* by post-primitive code).
		final AvailObject instance = object.literalType().typeUnion(
			aLiteralTokenType.literalType());
		instance.makeImmutable();
		return LiteralTokenTypeDescriptor.create(instance);
	}

	@Override
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.LITERAL_TOKEN_TYPE;
	}

	/**
	 * Create a new literal token type whose literal values comply with the
	 * given type.
	 *
	 * @param literalType The type with which to constrain literal values.
	 * @return A {@link LiteralTokenTypeDescriptor literal token type}.
	 */
	public static AvailObject create (
		final AvailObject literalType)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(LITERAL_TYPE, literalType.makeImmutable());
		return instance;
	}


	/** The most general literal token type */
	private static AvailObject mostGeneralType;

	/**
	 * Answer the most general literal token type, specifically the literal
	 * token type whose literal tokens' literal values are constrained by
	 * {@link TypeDescriptor.Types#ANY any}.
	 *
	 * @return The most general literal token type.
	 */
	public static AvailObject mostGeneralType()
	{
		return mostGeneralType;
	}

	public static void clearWellKnownObjects ()
	{
		mostGeneralType = null;
	}

	public static void createWellKnownObjects ()
	{
		mostGeneralType = create(ANY.o());
		mostGeneralType.makeImmutable();
	}



	/**
	 * Construct a new {@link LiteralTokenTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected LiteralTokenTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link LiteralTokenTypeDescriptor}.
	 */
	private static final LiteralTokenTypeDescriptor mutable =
		new LiteralTokenTypeDescriptor(true);

	/**
	 * Answer the mutable {@link LiteralTokenTypeDescriptor}.
	 *
	 * @return The mutable {@link LiteralTokenTypeDescriptor}.
	 */
	public static LiteralTokenTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link LiteralTokenTypeDescriptor}.
	 */
	private static final LiteralTokenTypeDescriptor immutable =
		new LiteralTokenTypeDescriptor(false);

	/**
	 * Answer the immutable {@link LiteralTokenTypeDescriptor}.
	 *
	 * @return The immutable {@link LiteralTokenTypeDescriptor}.
	 */
	public static LiteralTokenTypeDescriptor immutable ()
	{
		return immutable;
	}
}
