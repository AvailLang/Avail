/*
 * CompiledCodeTypeDescriptor.java
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
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.functions.CompiledCodeDescriptor;
import com.avail.descriptor.representation.Mutability;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;

import static com.avail.descriptor.types.CompiledCodeTypeDescriptor.ObjectSlots.FUNCTION_TYPE;
import static com.avail.descriptor.types.FunctionTypeDescriptor.mostGeneralFunctionType;
import static com.avail.descriptor.types.InstanceMetaDescriptor.instanceMeta;

/**
 * A {@linkplain CompiledCodeTypeDescriptor compiled code type} is the type for
 * a {@linkplain CompiledCodeDescriptor compiled code object}.  It contains a
 * {@linkplain FunctionTypeDescriptor function type} with which it covaries.
 * That is, a compiled code type is a subtype of another if and only if the
 * first's related function type is a subtype of another's function type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class CompiledCodeTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * The type of function that this {@linkplain CompiledCodeTypeDescriptor
		 * compiled code type} supports.  Compiled code types are contravariant
		 * with respect to the function type's argument types and covariant with
		 * respect to the function type's return type.
		 */
		FUNCTION_TYPE
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append('¢');
		object.functionType().printOnAvoidingIndent(
			aStream,
			recursionMap,
			(indent + 1));
	}

	@Override @AvailMethod
	protected A_Type o_FunctionType (final AvailObject object)
	{
		return object.slot(FUNCTION_TYPE);
	}

	@Override
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsCompiledCodeType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compiled code types compare for equality by comparing their function
	 * types.
	 * </p>
	 */
	@Override @AvailMethod
	protected boolean o_EqualsCompiledCodeType (
		final AvailObject object,
		final A_Type aType)
	{
		if (object.sameAddressAs(aType))
		{
			return true;
		}
		return aType.functionType().equals(object.functionType());
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		return object.functionType().hash() * 71 ^ 0xA78B01C3;
	}

	@Override @AvailMethod
	protected boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfCompiledCodeType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compiled code types exactly covary with their function types.
	 * </p>
	 */
	@Override @AvailMethod
	protected boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		final A_Type subFunctionType = aCompiledCodeType.functionType();
		final A_Type superFunctionType = object.functionType();
		return subFunctionType.isSubtypeOf(superFunctionType);
	}

	@Override
	protected boolean o_IsVacuousType (final AvailObject object)
	{
		return object.slot(FUNCTION_TYPE).isVacuousType();
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
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

	@Override @AvailMethod
	protected A_Type o_TypeIntersectionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		final A_Type functionType1 = object.functionType();
		final A_Type functionType2 = aCompiledCodeType.functionType();
		if (functionType1.equals(functionType2))
		{
			return object;
		}
		return compiledCodeTypeForFunctionType(functionType1.typeIntersection(functionType2));
	}

	@Override @AvailMethod
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
		return another.typeUnionOfCompiledCodeType(object);
	}

	@Override @AvailMethod
	protected A_Type o_TypeUnionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		final A_Type functionType1 = object.functionType();
		final A_Type functionType2 = aCompiledCodeType.functionType();
		if (functionType1.equals(functionType2))
		{
			// Optimization only
			return object;
		}
		return compiledCodeTypeForFunctionType(functionType1.typeUnion(functionType2));
	}

	@Override @AvailMethod
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.COMPILED_CODE_TYPE;
	}

	@Override @AvailMethod
	protected AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Make the object shared.
			return object.makeShared();
		}
		return object;
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("function implementation type");
		writer.write("function type");
		object.slot(FUNCTION_TYPE).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Create a compiled code type based on the passed {@linkplain
	 * FunctionTypeDescriptor function type}. Ignore the function type's
	 * exception set.
	 *
	 * @param functionType
	 *        A {@linkplain FunctionTypeDescriptor function type} on which to
	 *        base the new compiled code type.
	 * @return A new compiled code type.
	 */
	public static AvailObject compiledCodeTypeForFunctionType (final A_BasicObject functionType)
	{
		final AvailObject result = mutable.create();
		result.setSlot(FUNCTION_TYPE, functionType.makeImmutable());
		result.makeImmutable();
		return result;
	}

	/**
	 * Construct a new {@code CompiledCodeTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private CompiledCodeTypeDescriptor (final Mutability mutability)
	{
		super(
			mutability, TypeTag.RAW_FUNCTION_TYPE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link CompiledCodeTypeDescriptor}. */
	private static final TypeDescriptor mutable =
		new CompiledCodeTypeDescriptor(Mutability.MUTABLE);

	@Override
	public TypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link CompiledCodeTypeDescriptor}. */
	private static final TypeDescriptor shared =
		new CompiledCodeTypeDescriptor(Mutability.SHARED);

	@Override
	public TypeDescriptor immutable ()
	{
		// There is only a shared descriptor, not an immutable one.
		return shared;
	}

	@Override
	public TypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * The most general compiled code type. Since compiled code types are
	 * contravariant by argument types and contravariant by return type, the
	 * most general type is the one taking bottom as the arguments list
	 * (i.e., not specific enough to be able to call it), and having the return
	 * type bottom.
	 */
	private static final A_Type mostGeneralType =
		compiledCodeTypeForFunctionType(mostGeneralFunctionType()).makeShared();

	/**
	 * Answer the most general compiled code type.
	 *
	 * @return A compiled code type which has no supertypes that are themselves
	 *         compiled code types.
	 */
	public static A_Type mostGeneralCompiledCodeType ()
	{
		return mostGeneralType;
	}

	/**
	 * The metatype for all compiled code types. In particular, it's just the
	 * {@linkplain InstanceTypeDescriptor instance type} for the {@linkplain
	 * #mostGeneralType most general compiled code type}.
	 */
	private static final A_Type meta =
		instanceMeta(mostGeneralType).makeShared();

	/**
	 * Answer the metatype for all compiled code types.
	 *
	 * @return The statically referenced metatype.
	 */
	public static A_Type meta ()
	{
		return meta;
	}
}
