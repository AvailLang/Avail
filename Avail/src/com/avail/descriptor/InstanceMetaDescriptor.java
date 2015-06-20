/**
 * InstanceMetaDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.InstanceMetaDescriptor.ObjectSlots.*;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.IdentityHashMap;
import java.util.List;
import com.avail.annotations.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

/**
 * My instances are called <em>instance metas</em>, the types of types.  These
 * are the only representation (modulo {@link IndirectionDescriptor indirection
 * objects}) of metatypes in Avail, as attempting to carry enumeration types up
 * the instance-of hierarchy leads to an unsound type theory.
 *
 * <p>
 * An {@code instance meta} behaves much like an {@link InstanceTypeDescriptor
 * instance type} but always has a type as its instance (which normal instance
 * types are forbidden to have).
 * </p>
 *
 * <p>
 * Instance metas preserve metacovariance:
 * <span style="border-width:thin; border-style:solid"><nobr>
 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr;
 * T(x)&sube;T(y))</nobr></span>.
 * </p>
 *
 * <p>
 * The uniform use of instance types trivially ensures the additional, stronger
 * property we call <em>metavariance</em>, which states that every type has a
 * unique type of its own:
 * <span style="border-width:thin; border-style:solid"><nobr>
 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&ne;y &equiv;
 * T(x)&ne;T(y))</nobr></span>.
 * Note that metavariance requires this to hold for all types, but instance
 * types ensure this condition holds for all objects.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class InstanceMetaDescriptor
extends AbstractEnumerationTypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain TypeDescriptor type} for which I am the {@linkplain
		 * InstanceTypeDescriptor instance meta}.
		 */
		INSTANCE
	}

	/**
	 * Answer the instance (a type) that the provided instance meta contains.
	 *
	 * @param object An instance type.
	 * @return The instance represented by the given instance type.
	 */
	private static AvailObject getInstance (final AvailObject object)
	{
		return object.slot(INSTANCE);
	}

	/**
	 * Answer the kind that is nearest to the given object, an {@linkplain
	 * InstanceMetaDescriptor instance meta}.  Since all metatypes are
	 * instance metas, we must answer {@linkplain Types#ANY any}.
	 *
	 * @param object
	 *        An instance meta.
	 * @return
	 *        The kind (a {@linkplain TypeDescriptor type} but <em>not</em>
	 *        an {@linkplain AbstractEnumerationTypeDescriptor enumeration})
	 *        that is nearest the specified instance meta.
	 */
	private static AvailObject getSuperkind (final A_BasicObject object)
	{
		return ANY.o();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append("(");
		getInstance(object).printOnAvoidingIndent(
			aStream,
			recursionMap,
			indent);
		aStream.append(")'s type");
	}

	/**
	 * Compute the type intersection of the object which is an instance meta,
	 * and the argument, which is some type (it may be an {@linkplain
	 * AbstractEnumerationTypeDescriptor enumeration}).
	 *
	 * @param object
	 *        An instance meta.
	 * @param another
	 *        Another type.
	 * @return
	 *        The most general type that is a subtype of both object and
	 *        another.
	 */
	@Override
	final A_Type computeIntersectionWith (
		final AvailObject object,
		final A_Type another)
	{
		if (another.isBottom())
		{
			return another;
		}
		if (another.isInstanceMeta())
		{
			return on(getInstance(object).typeIntersection(another.instance()));
		}
		// Another is not an enumeration, and definitely not a meta, and the
		// only possible superkinds of object (a meta) are ANY and TOP.
		if (another.isSupertypeOfPrimitiveTypeEnum(ANY))
		{
			return object;
		}
		return BottomTypeDescriptor.bottom();
	}

	/**
	 * Compute the type union of the object, which is an {@linkplain
	 * InstanceMetaDescriptor instance meta}, and the argument, which may or may
	 * not be an {@linkplain AbstractEnumerationTypeDescriptor enumeration} (but
	 * must be a {@linkplain TypeDescriptor type}).
	 *
	 * @param object
	 *        An instance meta.
	 * @param another
	 *        Another type.
	 * @return
	 *        The most specific type that is a supertype of both {@code object}
	 *        and {@code another}.
	 */
	@Override
	final A_Type computeUnionWith (
		final AvailObject object,
		final A_Type another)
	{
		if (another.isBottom())
		{
			return object;
		}
		if (another.isInstanceMeta())
		{
			return on(getInstance(object).typeUnion(another.instance()));
		}
		// Unless another is top, then the answer will be any.
		return ANY.o().typeUnion(another);
	}

	@Override
	AvailObject o_Instance (final AvailObject object)
	{
		return getInstance(object);
	}

	@Override @AvailMethod
	boolean o_IsInstanceMeta (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	A_Type o_ComputeSuperkind (final AvailObject object)
	{
		return getSuperkind(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * An instance meta is only equal to another instance meta, and only when
	 * they refer to equal instances.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		final boolean equal = another.isInstanceMeta()
			&& getInstance(object).equals(((A_Type)another).instance());
		if (equal)
		{
			if (!isShared())
			{
				another.makeImmutable();
				object.becomeIndirectionTo(another);
			}
			else if (!another.descriptor().isShared())
			{
				object.makeImmutable();
				another.becomeIndirectionTo(object);
			}
		}
		return equal;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * An instance meta is never equal to an instance type.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_EqualsInstanceTypeFor (
		final AvailObject object,
		final AvailObject anObject)
	{
		return false;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return (getInstance(object).hash() - 0x361b5d51) * multiplier;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return getInstance(object).isInstanceOf(aType);
	}

	@Override @AvailMethod
	A_Number o_InstanceCount (final AvailObject object)
	{
		// Technically my instance is the instance I specify, which is a type,
		// *plus* all subtypes of it.  However, to distinguish metas from kinds
		// we need it to answer one here.
		return IntegerDescriptor.one();
	}

	@Override @AvailMethod
	A_Set o_Instances (final AvailObject object)
	{
		return SetDescriptor.empty().setWithElementCanDestroy(
			getInstance(object),
			true);
	}

	@Override @AvailMethod
	boolean o_EnumerationIncludesInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return potentialInstance.isType()
			&& potentialInstance.isSubtypeOf(getInstance(object));
	}

	@Override @AvailMethod
	boolean o_IsInstanceOf (final AvailObject object, final A_Type aType)
	{
		if (aType.isInstanceMeta())
		{
			// I'm an instance meta on some type, and aType is (also) an
			// instance meta (the only sort of meta that exists these
			// days -- 2012.07.17).  See if my instance (a type) is an
			// instance of aType's instance (also a type, but maybe a meta).
			return getInstance(object).isInstanceOf(aType.instance());
		}
		// I'm a meta, a singular enumeration of a type, so I could only be an
		// instance of a meta meta (already excluded), or of ANY or TOP.
		return aType.isSupertypeOfPrimitiveTypeEnum(ANY);
	}

	@Override @AvailMethod
	boolean o_RangeIncludesInt (final AvailObject object, final int anInt)
	{
		// A metatype can't have an integer as an instance.
		return false;
	}

	@Override @AvailMethod
	A_Map o_FieldTypeMap (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Number o_LowerBound (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_LowerInclusive (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Number o_UpperBound (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_UpperInclusive (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_TypeAtIndex (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_DefaultType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_SizeRange (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Tuple o_TypeTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_IsIntegerRangeType (final AvailObject object)
	{
		// A metatype can't be an integer range type.
		return false;
	}

	@Override @AvailMethod
	boolean o_IsLiteralTokenType (final AvailObject object)
	{
		// A metatype can't be a literal token type.
		return false;
	}

	@Override @AvailMethod
	boolean o_IsMapType (final AvailObject object)
	{
		// A metatype can't be a map type.
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSetType (final AvailObject object)
	{
		// A metatype can't be a set type.
		return false;
	}

	@Override @AvailMethod
	boolean o_IsTupleType (final AvailObject object)
	{
		// A metatype can't be a tuple type.
		return false;
	}

	@Override @AvailMethod
	boolean o_AcceptsArgTypesFromFunctionType (
		final AvailObject object,
		final A_Type functionType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<? extends A_BasicObject> argValues)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final A_Tuple argTypes)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final A_Tuple arguments)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_ArgsTupleType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Set o_DeclaredExceptions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_FunctionType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_ContentType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_KeyType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	AvailObject o_Name (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_BasicObject o_Parent (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_ReturnType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_ValueType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_ReadType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_WriteType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.INSTANCE_META;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		getSuperkind(object).writeTo(writer);
		writer.write("instances");
		object.instances().writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		getSuperkind(object).writeSummaryTo(writer);
		writer.write("instances");
		object.instances().writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Construct a new {@link InstanceMetaDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private InstanceMetaDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, null);
	}

	/** The mutable {@link InstanceMetaDescriptor}. */
	private static final AbstractEnumerationTypeDescriptor mutable =
		new InstanceMetaDescriptor(Mutability.MUTABLE);

	@Override
	AbstractEnumerationTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link InstanceMetaDescriptor}. */
	private static final AbstractEnumerationTypeDescriptor immutable =
		new InstanceMetaDescriptor(Mutability.IMMUTABLE);

	@Override
	AbstractEnumerationTypeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link InstanceMetaDescriptor}. */
	private static final AbstractEnumerationTypeDescriptor shared =
		new InstanceMetaDescriptor(Mutability.SHARED);

	@Override
	AbstractEnumerationTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * ⊤'s type, cached statically for convenience.
	 */
	private static final A_Type topMeta = on(TOP.o()).makeShared();

	/**
	 * Answer ⊤'s type, the most general metatype.
	 *
	 * @return ⊤'s type.
	 */
	public static A_Type topMeta ()
	{
		return topMeta;
	}

	/**
	 * Any's type, cached statically for convenience.
	 */
	private static final A_Type anyMeta = on(ANY.o()).makeShared();

	/**
	 * Answer any's type, a metatype.
	 *
	 * @return any's type.
	 */
	public static A_Type anyMeta ()
	{
		return anyMeta;
	}

	/**
	 * Answer a new instance of this descriptor based on some object whose type
	 * it will represent.
	 *
	 * @param instance The object whose type to represent.
	 * @return An {@link AvailObject} representing the type of the argument.
	 */
	public static A_Type on (final A_Type instance)
	{
		assert instance.isType();
		final AvailObject result = mutable.create();
		instance.makeImmutable();
		result.setSlot(INSTANCE, instance);
		return result;
	}
}
