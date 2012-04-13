/**
 * EnumerationTypeDescriptor.java
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

import static com.avail.descriptor.AvailObject.Multiplier;
import static com.avail.descriptor.TypeDescriptor.Types.TYPE;
import java.util.List;
import com.avail.annotations.*;

/**
 * My instances are called <em>enumerations</em>. This descriptor family is
 * used for enumerations with two or more instances (i.e., enumerations for
 * which two or more elements survive canonicalization). For the case of one
 * instance, see {@link InstanceTypeDescriptor}, and for the case of zero
 * instances, see {@link BottomTypeDescriptor}.
 *
 * <p>
 * An enumeration is created from a set of objects that are considered instances
 * of the resulting type.  For example, Avail's {@linkplain #booleanObject()
 * boolean type} is simply an enumeration whose instances are {@linkplain
 * AtomDescriptor atoms} representing {@linkplain AtomDescriptor#trueObject()
 * true} and {@linkplain AtomDescriptor#falseObject() false}.  This flexibility
 * allows an enumeration mechanism simply not available in other programming
 * languages. In particular, it allows one to define enumerations whose
 * memberships overlap.  The subtype relationship mimics the subset relationship
 * of the enumerations' membership sets.
 * </p>
 *
 * <p>
 * Because of metacovariance and the useful properties it bestows, enumerations
 * that contain a type as a member (i.e., that type is an instance of the union)
 * also automatically include all subtypes as members.  Thus, an enumeration
 * whose instances are {5, "cheese", {@linkplain
 * TupleTypeDescriptor#mostGeneralType() tuple}} also has the type {@linkplain
 * TupleTypeDescriptor#stringTupleType() string} as a member (string being one
 * of the many subtypes of tuple).  This condition ensures that enumerations
 * satisfy metacovariance, which states that types' types vary the same way as
 * the types: <span style="border-width:thin; border-style:solid"><nobr>
 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr;
 * T(x)&sube;T(y))</nobr></span>.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class EnumerationTypeDescriptor
extends AbstractEnumerationTypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The set of {@linkplain AvailObject objects} for which I am the
		 * {@linkplain EnumerationTypeDescriptor enumeration}. If any of the
		 * objects are {@linkplain TypeDescriptor types}, then their subtypes
		 * are also automatically members of this enumeration.
		 */
		INSTANCES,

		/**
		 * Either {@linkplain NullDescriptor#nullObject() the null object} or
		 * this enumeration's nearest superkind (i.e., the nearest type that
		 * isn't a union}.
		 */
		CACHED_SUPERKIND
	}

	/**
	 * Extract my set of instances. If any object is itself a type then all of
	 * its subtypes are automatically instances, but they're not returned by
	 * this method. Also, any object that's a type and has a supertype in this
	 * set will have been removed during creation of this enumeration.
	 *
	 * @param object
	 *            The enumeration for which to extract the instances.
	 * @return The instances of this enumeration.
	 */
	static final @NotNull
	AvailObject getInstances (final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.INSTANCES);
	}

	/**
	 * Answer my nearest superkind (the most specific supertype of me that isn't
	 * also an {@linkplain AbstractEnumerationTypeDescriptor enumeration}).
	 *
	 * @param object
	 *            An enumeration.
	 * @return The kind closest to the given enumeration.
	 */
	static final @NotNull
	AvailObject getSuperkind (final @NotNull AvailObject object)
	{
		AvailObject cached = object.slot(ObjectSlots.CACHED_SUPERKIND);
		if (cached.equalsNull())
		{
			cached = BottomTypeDescriptor.bottom();
			for (final AvailObject instance : getInstances(object))
			{
				cached = cached.typeUnion(instance.kind());
			}
			object.setSlot(ObjectSlots.CACHED_SUPERKIND, cached);
		}
		return cached;
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == ObjectSlots.CACHED_SUPERKIND;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ComputeSuperkind (
		final @NotNull AvailObject object)
	{
		return getSuperkind(object);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_InstanceCount (final @NotNull AvailObject object)
	{
		return IntegerDescriptor.fromInt(getInstances(object).setSize());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Instances (final @NotNull AvailObject object)
	{
		return getInstances(object);
	}

	@Override
	void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		// Print boolean specially.
		if (object.equals(Boolean))
		{
			aStream.append("boolean");
			return;
		}
		// Default printing.
		aStream.append("enumeration of ");
		object.instances().printOnAvoidingIndent(
			aStream,
			recursionList,
			indent + 1);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * An instance type is only equal to another instance type, and only when
	 * they refer to equal instances.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		final boolean equal = another
			.equalsEnumerationWithSet(getInstances(object));
		if (equal)
		{
			another.becomeIndirectionTo(object);
		}
		return equal;
	}

	@Override @AvailMethod
	boolean o_EqualsEnumerationWithSet (
		final @NotNull AvailObject object,
		final AvailObject aSet)
	{
		return getInstances(object).equals(aSet);
	}

	/**
	 * The potentialInstance is a {@linkplain ObjectDescriptor user-defined
	 * object}. See if it is an instance of the object.  It is an instance
	 * precisely when it is in object's set of {@linkplain ObjectSlots#INSTANCES
	 * instances}, or if it is a subtype of any type that occurs in the set of
	 * instances.
	 */
	@Override @AvailMethod
	boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance)
	{
		return getInstances(object).hasElement(potentialInstance);
	}

	@Override @AvailMethod
	int o_Hash (final @NotNull AvailObject object)
	{
		return (object.instances().hash() ^ 0x15b5b059) * Multiplier;
	}

	/**
	 * Compute the type intersection of the object, which is an {@linkplain
	 * EnumerationTypeDescriptor enumeration}, and
	 * the argument, which may or may not be an enumeration (but must be a
	 * {@linkplain TypeDescriptor type}).
	 *
	 * @param object
	 *            An enumeration.
	 * @param another
	 *            Another type.
	 * @return The most general type that is a subtype of both {@code object}
	 *         and {@code another}.
	 */
	@Override final
	@NotNull AvailObject computeIntersectionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		assert another.isType();
		AvailObject set = SetDescriptor.empty();
		final AvailObject elements = object.instances();
		if (another.isEnumeration())
		{
			// Create a new enumeration containing all non-type elements that
			// are simultaneously present in object and another, plus the type
			// intersections of all pairs of types in the product of the sets.
			// This should even correctly deal with bottom as an element.
			final AvailObject otherElements = another.instances();
			AvailObject myTypes = SetDescriptor.empty();
			for (final AvailObject element : elements)
			{
				if (element.isType())
				{
					myTypes = myTypes.setWithElementCanDestroy(element, true);
				}
				else if (otherElements.hasElement(element))
				{
					set = set.setWithElementCanDestroy(element, true);
				}
			}
			// We have the non-types now, so add the pair-wise intersection of
			// the types.
			if (myTypes.setSize() > 0)
			{
				for (final AvailObject anotherElement : otherElements)
				{
					if (anotherElement.isType())
					{
						for (final AvailObject myType : myTypes)
						{
							set = set.setWithElementCanDestroy(
								anotherElement.typeIntersection(myType),
								true);
						}
					}
				}
			}
		}
		else
		{
			// Keep the instances that comply with another, which is not a union
			// type.
			for (final AvailObject element : object.instances())
			{
				if (element.isInstanceOfKind(another))
				{
					set = set.setWithElementCanDestroy(element, true);
				}
			}
		}
		if (set.setSize() == 0)
		{
			// Decide whether this should be bottom or bottom's type
			// based on whether object and another are both metas.  Note that
			// object is a meta precisely when one of its instances is a type.
			// One more thing:  The special case of another being bottom should
			// not be treated as being a meta for our purposes, even though
			// bottom technically is a meta.
			if (object.isSubtypeOf(TYPE.o())
				&& another.isSubtypeOf(TYPE.o())
				&& !another.equals(BottomTypeDescriptor.bottom()))
			{
				return InstanceTypeDescriptor.on(BottomTypeDescriptor.bottom());
			}
		}
		return AbstractEnumerationTypeDescriptor.withInstances(set);
	}

	/**
	 * Compute the type union of the object, which is an {@linkplain
	 * EnumerationTypeDescriptor enumeration}, and the argument, which may or
	 * may not be an enumeration (but must be a {@linkplain TypeDescriptor
	 * type}).
	 *
	 * @param object
	 *            An enumeration.
	 * @param another
	 *            Another type.
	 * @return The most general type that is a subtype of both {@code object}
	 *         and {@code another}.
	 */
	@Override final
	@NotNull AvailObject computeUnionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		if (another.isEnumeration())
		{
			// Create a new enumeration containing all elements from both
			// enumerations.
			return AbstractEnumerationTypeDescriptor.withInstances(
				object.instances().setUnionCanDestroy(
					another.instances(),
					false));
		}
		// Go up to my nearest kind, then compute the union with the given kind.
		AvailObject union = another;
		for (final AvailObject instance : object.instances())
		{
			union = union.typeUnion(instance.kind());
		}
		return union;
	}

	@Override
	public @NotNull
	AvailObject o_FieldTypeMap (final @NotNull AvailObject object)
	{
		return getSuperkind(object).fieldTypeMap();
	}

	@Override
	public @NotNull
	AvailObject o_LowerBound (final @NotNull AvailObject object)
	{
		return getSuperkind(object).lowerBound();
	}

	@Override @AvailMethod
	boolean o_LowerInclusive (final @NotNull AvailObject object)
	{
		return getSuperkind(object).lowerInclusive();
	}

	@Override
	public @NotNull
	AvailObject o_UpperBound (final @NotNull AvailObject object)
	{
		return getSuperkind(object).upperBound();
	}

	@Override @AvailMethod
	boolean o_UpperInclusive (final @NotNull AvailObject object)
	{
		return getSuperkind(object).upperInclusive();
	}

	@Override @AvailMethod
	boolean o_EnumerationIncludesInstance (
		final @NotNull AvailObject object,
		final AvailObject potentialInstance)
	{
		if (object.instances().hasElement(potentialInstance))
		{
			return true;
		}
		if (potentialInstance.isType())
		{
			for (final AvailObject element : object.instances())
			{
				if (element.isType() && potentialInstance.isSubtypeOf(element))
				{
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public @NotNull
	AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		// This is only intended for a TupleType stand-in. Answer what type the
		// given index would have in an object instance of me. Answer
		// bottom if the index is out of bounds.
		assert object.isTupleType();
		return getSuperkind(object).typeAtIndex(index);
	}

	@Override
	public @NotNull
	AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		// Answer the union of the types that object's instances could have in
		// the given range of indices. Out-of-range indices are treated as
		// bottom, which don't affect the union (unless all indices are out
		// of range).
		assert object.isTupleType();
		return getSuperkind(object).unionOfTypesAtThrough(startIndex, endIndex);
	}

	@Override
	public @NotNull
	AvailObject o_DefaultType (final @NotNull AvailObject object)
	{
		assert object.isTupleType();
		return getSuperkind(object).defaultType();
	}

	@Override
	public @NotNull
	AvailObject o_SizeRange (final @NotNull AvailObject object)
	{
		return getSuperkind(object).sizeRange();
	}

	@Override
	public @NotNull
	AvailObject o_TypeTuple (final @NotNull AvailObject object)
	{
		return getSuperkind(object).typeTuple();
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		// Check if object (an enumeration) is a subtype of aType (should also
		// be a type).  All members of me must also be instances of aType.
		for (final AvailObject instance : object.instances())
		{
			if (!instance.isInstanceOf(aType))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsIntegerRangeType (final @NotNull AvailObject object)
	{
		for (final AvailObject instance : object.instances())
		{
			if (!instance.isExtendedInteger())
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsMapType (final @NotNull AvailObject object)
	{
		for (final AvailObject instance : object.instances())
		{
			if (!instance.isMap())
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSetType (final @NotNull AvailObject object)
	{
		for (final AvailObject instance : object.instances())
		{
			if (!instance.isSet())
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsTupleType (final @NotNull AvailObject object)
	{
		for (final AvailObject instance : object.instances())
		{
			if (!instance.isTuple())
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_AcceptsArgTypesFromFunctionType (
		final @NotNull AvailObject object,
		final AvailObject functionType)
	{
		return getSuperkind(object).acceptsArgTypesFromFunctionType(functionType);
	}

	@Override @AvailMethod
	boolean o_AcceptsArgumentTypesFromContinuation (
		final @NotNull AvailObject object,
		final AvailObject continuation,
		final int stackp,
		final int numArgs)
	{
		return getSuperkind(object).acceptsArgumentTypesFromContinuation(
			continuation,
			stackp,
			numArgs);
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		return getSuperkind(object).acceptsListOfArgTypes(argTypes);
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final List<AvailObject> argValues)
	{
		return getSuperkind(object).acceptsListOfArgValues(argValues);
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final AvailObject argTypes)
	{
		return getSuperkind(object).acceptsTupleOfArgTypes(argTypes);
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final AvailObject arguments)
	{
		return getSuperkind(object).acceptsTupleOfArguments(arguments);
	}

	@Override @AvailMethod
	AvailObject o_ArgsTupleType (final AvailObject object)
	{
		return getSuperkind(object).argsTupleType();
	}

	@Override @AvailMethod
	AvailObject o_CheckedExceptions (final AvailObject object)
	{
		return getSuperkind(object).checkedExceptions();
	}

	@Override @AvailMethod
	AvailObject o_FunctionType (final AvailObject object)
	{
		return getSuperkind(object).functionType();
	}

	@Override @AvailMethod
	AvailObject o_ContentType (final AvailObject object)
	{
		return getSuperkind(object).contentType();
	}

	@Override @AvailMethod
	boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		return getSuperkind(object).couldEverBeInvokedWith(argTypes);
	}

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final AvailObject anotherObject)
	{
		// An enumeration with a cached superkind is pretty good.
		return !object.slot(ObjectSlots.CACHED_SUPERKIND).equalsNull();
	}

	@Override @AvailMethod
	AvailObject o_KeyType (final AvailObject object)
	{
		return getSuperkind(object).keyType();
	}

	@Override @AvailMethod
	AvailObject o_Name (final AvailObject object)
	{
		return getSuperkind(object).name();
	}

	@Override @AvailMethod
	AvailObject o_Parent (final AvailObject object)
	{
		return getSuperkind(object).parent();
	}

	@Override @AvailMethod
	AvailObject o_ReturnType (final AvailObject object)
	{
		return getSuperkind(object).returnType();
	}

	@Override @AvailMethod
	AvailObject o_TypeIntersectionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		AvailObject complyingInstances = SetDescriptor.empty();
		for (final AvailObject instance : getInstances(object))
		{
			if (instance.isInstanceOf(aCompiledCodeType))
			{
				complyingInstances =
					complyingInstances.setWithElementCanDestroy(
						instance,
						true);
			}
		}
		return AbstractEnumerationTypeDescriptor.withInstances(complyingInstances);
	}

	@Override @AvailMethod
	AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType)
	{
		AvailObject complyingInstances = SetDescriptor.empty();
		for (final AvailObject instance : getInstances(object))
		{
			if (instance.isInstanceOf(aContinuationType))
			{
				complyingInstances =
					complyingInstances.setWithElementCanDestroy(
						instance,
						true);
			}
		}
		return AbstractEnumerationTypeDescriptor.withInstances(complyingInstances);
	}

	@Override @AvailMethod
	AvailObject o_TypeIntersectionOfParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType)
	{
		AvailObject complyingInstances = SetDescriptor.empty();
		for (final AvailObject instance : getInstances(object))
		{
			if (instance.isInstanceOf(aParseNodeType))
			{
				complyingInstances =
					complyingInstances.setWithElementCanDestroy(
						instance,
						true);
			}
		}
		return AbstractEnumerationTypeDescriptor.withInstances(complyingInstances);
	}

	@Override @AvailMethod
	AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType)
	{
		return getSuperkind(object).typeUnionOfContinuationType(
			aContinuationType);
	}

	@Override @AvailMethod
	AvailObject o_TypeUnionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		return getSuperkind(object).typeUnionOfContinuationType(
			aCompiledCodeType);
	}

	@Override @AvailMethod
	AvailObject o_TypeUnionOfParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType)
	{
		return getSuperkind(object).typeUnionOfParseNodeType(aParseNodeType);
	}

	@Override @AvailMethod
	AvailObject o_ValueType (final AvailObject object)
	{
		return getSuperkind(object).valueType();
	}

	@Override
	Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint)
	{
		if (object.isSubtypeOf(Boolean))
		{
			return java.lang.Boolean.TYPE;
		}
		return super.o_MarshalToJava(object, ignoredClassHint);
	}

	@Override
	AvailObject o_ReadType (final AvailObject object)
	{
		return getSuperkind(object).readType();
	}

	@Override
	AvailObject o_WriteType (final AvailObject object)
	{
		return getSuperkind(object).writeType();
	}

	/**
	 * Construct an {@linkplain EnumerationTypeDescriptor enumeration} from a
	 * {@linkplain SetDescriptor set} with at least two instances.  The set
	 * must have already been normalized, such that at most one of the elements
	 * is itself a {@linkplain TypeDescriptor type}.
	 *
	 * @param normalizedSet The set of instances.
	 * @return The resulting enumeration.
	 */
	static @NotNull AvailObject fromNormalizedSet (
		final @NotNull AvailObject normalizedSet)
	{
		assert normalizedSet.setSize() > 1;
		final AvailObject result = EnumerationTypeDescriptor.mutable().create();
		result.setSlot(
			ObjectSlots.INSTANCES,
			normalizedSet.makeImmutable());
		result.setSlot(
			ObjectSlots.CACHED_SUPERKIND,
			NullDescriptor.nullObject());
		return result;
	}

	/**
	 * Avail's boolean type, the equivalent of Java's primitive {@code boolean}
	 * pseudo-type, and Java's other non-primitive boxed {@link Boolean} class.
	 */
	private static AvailObject Boolean;

	/**
	 * Return Avail's boolean type.
	 *
	 * @return The {@linkplain EnumerationTypeDescriptor enumeration} that
	 *         acts as Avail's boolean type.
	 */
	public static AvailObject booleanObject ()
	{
		return Boolean;
	}

	/**
	 * Create the boolean type, which is simply an {@linkplain
	 * EnumerationTypeDescriptor instance union} of {@linkplain
	 * AtomDescriptor#trueObject()} and {@linkplain
	 * AtomDescriptor#falseObject()}.
	 */
	static void createWellKnownObjects ()
	{
		final AvailObject tuple = TupleDescriptor.from(
			AtomDescriptor.trueObject(),
			AtomDescriptor.falseObject());
		Boolean = withInstances(tuple.asSet());
	}

	/**
	 * Release any well-known objects held by this class.
	 */
	static void clearWellKnownObjects ()
	{
		Boolean = null;
	}

	/**
	 * Construct a new {@link EnumerationTypeDescriptor}.
	 *
	 * @param isMutable
	 *            Does the {@linkplain Descriptor descriptor} represent a
	 *            mutable object?
	 */
	protected EnumerationTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link EnumerationTypeDescriptor}.
	 */
	private static final AbstractEnumerationTypeDescriptor mutable = new EnumerationTypeDescriptor(
		true);

	/**
	 * Answer the mutable {@link EnumerationTypeDescriptor}.
	 *
	 * @return The mutable {@link EnumerationTypeDescriptor}.
	 */
	public static AbstractEnumerationTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link EnumerationTypeDescriptor}.
	 */
	private static final AbstractEnumerationTypeDescriptor immutable = new EnumerationTypeDescriptor(
		false);

	/**
	 * Answer the immutable {@link EnumerationTypeDescriptor}.
	 *
	 * @return The immutable {@link EnumerationTypeDescriptor}.
	 */
	public static AbstractEnumerationTypeDescriptor immutable ()
	{
		return immutable;
	}
}
