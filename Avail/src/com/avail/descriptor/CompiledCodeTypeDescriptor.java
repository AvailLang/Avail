/**
 * com.avail.descriptor/CompiledCodeTypeDescriptor.java
 * Copyright (c) 2011, Mark van Gulik.
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

import static com.avail.descriptor.TypeDescriptor.Types.TYPE;
import java.util.List;
import com.avail.annotations.NotNull;

/**
 * TODO: Document this type!
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class CompiledCodeTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The type of closure that this {@linkplain CompiledCodeTypeDescriptor
		 * compiled code type} supports.  Compiled code types are contravariant
		 * with respect to the closure type's argument types and covariant with
		 * respect to the closure type's return type.
		 */
		CLOSURE_TYPE
	}

	@Override
	public @NotNull AvailObject o_ClosureType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CLOSURE_TYPE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append('¢');
		object.closureType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compiled code types compare for equality by comparing their closureTypes.
	 * </p>
	 */
	@Override
	public boolean o_EqualsCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		if (object.sameAddressAs(aType))
		{
			return true;
		}
		return aType.closureType().equals(object.closureType());
	}

	@Override
	public int o_Hash (final @NotNull AvailObject object)
	{
		return object.closureType().hash() * 71 ^ 0xA78B01C3;
	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return TYPE.o();
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfCompiledCodeType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compiled code types exactly covary with their closure types.
	 * </p>
	 */
	@Override
	public boolean o_IsSupertypeOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		final AvailObject subClosureType = aCompiledCodeType.closureType();
		final AvailObject superClosureType = object.closureType();
		return subClosureType.isSubtypeOf(superClosureType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		if (object.isSubtypeOf(another))
		{
			return object;
		}
		if (another.isSubtypeOf(object))
		{
			return another;
		}
		return another.typeIntersectionOfCompiledCodeType(object);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		final AvailObject closType1 = object.closureType();
		final AvailObject closType2 = aCompiledCodeType.closureType();
		if (closType1.equals(closType2))
		{
			return object;
		}
		if (closType1.numArgs() != closType2.numArgs())
		{
			return TerminatesTypeDescriptor.terminates();
		}
		final AvailObject intersection = ClosureTypeDescriptor.create(
			closType1.argsTupleType().typeUnion(closType2.argsTupleType()),
			closType1.returnType().typeUnion(closType2.returnType()));
		return forClosureType(intersection);
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return another.typeUnionOfCompiledCodeType(object);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		final AvailObject closType1 = object.closureType();
		final AvailObject closType2 = aCompiledCodeType.closureType();
		if (closType1.equals(closType2))
		{
			// Optimization only
			return object;
		}
		final AvailObject union = ClosureTypeDescriptor.create(
			closType1.argsTupleType().typeIntersection(
				closType2.argsTupleType()),
				closType1.returnType().typeIntersection(closType2.returnType()));
		return forClosureType(union);
	}

	/**
	 * Create a {@linkplain CompiledCodeTypeDescriptor compiled code type} based
	 * on the passed {@linkplain ClosureTypeDescriptor closure type}.  Ignore
	 * the closure type's exception set.
	 *
	 * @param closureType
	 *            A {@linkplain ClosureTypeDescriptor closure type} on which to
	 *            base the new {@linkplain CompiledCodeTypeDescriptor
	 *            compiled code type}.
	 * @return
	 *            A new {@linkplain CompiledCodeTypeDescriptor}.
	 */
	public static @NotNull AvailObject forClosureType (
		final @NotNull AvailObject closureType)
	{
		closureType.makeImmutable();
		final AvailObject result = mutable().create();
		result.objectSlotPut(ObjectSlots.CLOSURE_TYPE, closureType);
		result.makeImmutable();
		return result;
	}

	/**
	 * @return
	 */
	public static AvailObject mostGeneralType ()
	{
		return MostGeneralType;
	}

	/**
	 * The most general compiled code type.  Since compiled code types are
	 * contravariant by argument types and contravariant by return type, the
	 * most general type is the one taking terminates as the arguments list
	 * (i.e., not specific enough to be able to call it), and having the return
	 * type terminates.
	 */
	private static AvailObject MostGeneralType;

	/**
	 * The metatype for all compiled code types.  In particular, it's just the
	 * {@linkplain InstanceTypeDescriptor instance type} for the {@linkplain
	 * #MostGeneralType most general compiled code type}.
	 */
	private static AvailObject Meta;

	/**
	 * Answer the metatype for all compiled code types.
	 *
	 * @return The statically referenced metatype.
	 */
	public static AvailObject meta ()
	{
		return Meta;
	}

	public static void clearWellKnownObjects ()
	{
		MostGeneralType = null;
		Meta = null;
	}

	public static void createWellKnownObjects ()
	{
		MostGeneralType = forClosureType(
			ClosureTypeDescriptor.mostGeneralType());
		MostGeneralType.makeImmutable();
		Meta = InstanceTypeDescriptor.withInstance(MostGeneralType);
		Meta.makeImmutable();
	}

	/**
	 * Construct a new {@link CompiledCodeTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected CompiledCodeTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link CompiledCodeTypeDescriptor}.
	 */
	private final static CompiledCodeTypeDescriptor mutable =
		new CompiledCodeTypeDescriptor(true);

	/**
	 * Answer the mutable {@link CompiledCodeTypeDescriptor}.
	 *
	 * @return The mutable {@link CompiledCodeTypeDescriptor}.
	 */
	public static CompiledCodeTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link CompiledCodeTypeDescriptor}.
	 */
	private final static CompiledCodeTypeDescriptor immutable =
		new CompiledCodeTypeDescriptor(false);

	/**
	 * Answer the immutable {@link CompiledCodeTypeDescriptor}.
	 *
	 * @return The immutable {@link CompiledCodeTypeDescriptor}.
	 */
	public static CompiledCodeTypeDescriptor immutable ()
	{
		return immutable;
	}

}
