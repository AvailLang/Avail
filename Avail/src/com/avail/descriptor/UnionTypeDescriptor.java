/**
 * descriptor/UnionTypeDescriptor.java Copyright (c) 2011, Mark van Gulik. All
 * rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.NotNull;

/**
 * My instances are called <em>instance union types</em>, or just union types.
 * This descriptor family is used for union types with two or more instances
 * (i.e., union types for which two or more elements survive canonicalization).
 * For the case of one instance, see {@link InstanceTypeDescriptor}, and for the
 * case of zero instances, see {@link TerminatesTypeDescriptor}.
 *
 * <p>
 * A union type is created from a set of objects that are considered instances
 * of the resulting type.  For example, Avail {@linkplain #booleanObject()
 * booleans} are simply a union type for the instances representing {@linkplain
 * AtomDescriptor#trueObject() true} and {@linkplain
 * AtomDescriptor#falseObject() false}.  This flexibility allows an
 * enumeration mechanism simply not available in other programming languages.
 * In particular, it allows one to define an enumeration (a union type), then
 * define another enumeration with a membership that overlaps the first
 * enumeration.  The subtype relationship mimics the subset relationship of the
 * union types' membership sets.
 * </p>
 *
 * <p>
 * Because of metacovariance and the useful properties it bestows, unions that
 * contain a type as a member (i.e., that type is an instance of the union) also
 * automatically include all subtypes as members.  Thus, a union type whose
 * instances are {5, cheese, {@linkplain
 * TupleTypeDescriptor#mostGeneralType() tuple}} also has the type
 * {@linkplain TupleTypeDescriptor#stringTupleType() string} as a member (string
 * being one of the many subtypes of tuple).  This condition ensures that union
 * types satisfy metacovariance, which states that types' types vary the same
 * way as the types: <span style="border-width:thin; border-style:solid"><nobr>
 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr;
 * T(x)&sube;T(y))</nobr></span>.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class UnionTypeDescriptor extends AbstractUnionTypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The set of {@linkplain AvailObject objects} for which I am the
		 * {@linkplain InstanceTypeDescriptor instance type}. If any of the
		 * objects are types, then their subtypes are also automatically members
		 * of this union type.
		 */
		INSTANCES,

		/**
		 * Either {@linkplain NullDescriptor#nullObject() the null object} or
		 * this union type's nearest superkind (i.e., the nearest type that
		 * isn't a union}.
		 */
		CACHED_SUPERKIND
	}

	/**
	 * Extract my set of instances. If any object is itself a type then all of
	 * its subtypes are automatically instances, but they're not returned by
	 * this method. Also, any object that's a type and has a supertype in this
	 * set will have been removed during creation of this union type.
	 *
	 * @param object
	 *            The union type for which to extract the instances.
	 * @return The instances of this union type.
	 */
	static final @NotNull
	AvailObject getInstances (final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INSTANCES);
	}

	/**
	 * Answer my nearest superkind (the most specific supertype of me that isn't
	 * also a union type).
	 *
	 * @param object
	 *            A union type.
	 * @return The kind closest to the given union type.
	 */
	static final @NotNull
	AvailObject getSuperkind (final @NotNull AvailObject object)
	{
		AvailObject cached = object.objectSlot(ObjectSlots.CACHED_SUPERKIND);
		if (cached.equalsVoid())
		{
			cached = TerminatesTypeDescriptor.terminates();
			for (final AvailObject instance : getInstances(object))
			{
				cached = cached.typeUnion(instance.kind());
			}
			object.objectSlotPut(ObjectSlots.CACHED_SUPERKIND, cached);
		}
		return cached;
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (final Enum<?> e)
	{
		return e == ObjectSlots.CACHED_SUPERKIND;
	}

	@Override
	public @NotNull AvailObject o_ComputeSuperkind (
		final @NotNull AvailObject object)
	{
		return getSuperkind(object);
	}

	@Override
	public @NotNull
	AvailObject o_Instances (final @NotNull AvailObject object)
	{
		return getInstances(object);
	}

	@Override
	public void printObjectOnAvoidingIndent (
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
		aStream.append("UnionType of ");
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
	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		final boolean equal = another
			.equalsUnionTypeWithSet(getInstances(object));
		if (equal)
		{
			another.becomeIndirectionTo(object);
		}
		return equal;
	}

	@Override
	public boolean o_EqualsUnionTypeWithSet (
		final AvailObject object,
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
	@Override
	public boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance)
	{
		return getInstances(object).hasElement(potentialInstance);
	}

	@Override
	public int o_Hash (final @NotNull AvailObject object)
	{
		return (object.instances().hash() ^ 0x15b5b059) * Multiplier;
	}

	/**
	 * Compute the type intersection of the object which is a union type, and
	 * the argument, which may or may not be a union type (but must be a type).
	 *
	 * @param object
	 *            A union type.
	 * @param another
	 *            Another type.
	 * @return The most general type that is a subtype of both object and
	 *         another.
	 */
	final @NotNull
	AvailObject computeIntersectionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		AvailObject set = SetDescriptor.empty();
		final AvailObject elements = object.instances();
		if (another.isAbstractUnionType())
		{
			// Create a new union type containing all non-type elements that are
			// simultaneously present in object and another, plus the type
			// intersections of all pairs of types in the product of the sets.
			// This should even correctly deal with terminates as an element.
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
		return AbstractUnionTypeDescriptor.withInstances(set);
	}

	/**
	 * Compute the type union of the object which is a union type, and the
	 * argument, which may or may not be a union type (but must be a type).
	 *
	 * @param object
	 *            A union type.
	 * @param another
	 *            Another type.
	 * @return The most specific type that is a supertype of both object and
	 *         another.
	 */
	final @NotNull
	AvailObject computeUnionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		if (another.isAbstractUnionType())
		{
			// Create a new union type containing all elements from both union
			// types.
			return AbstractUnionTypeDescriptor.withInstances(
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

	@Override
	public boolean o_LowerInclusive (final @NotNull AvailObject object)
	{
		return getSuperkind(object).lowerInclusive();
	}

	@Override
	public @NotNull
	AvailObject o_UpperBound (final @NotNull AvailObject object)
	{
		return getSuperkind(object).upperBound();
	}

	@Override
	public boolean o_UpperInclusive (final @NotNull AvailObject object)
	{
		return getSuperkind(object).upperInclusive();
	}

	@Override
	public boolean o_AbstractUnionTypeIncludesInstance (
		final AvailObject object,
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
		// terminates if the index is out of bounds.
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
		// terminates, which don't affect the union (unless all indices are out
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

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		// Check if object (a union type) is a subtype of aType (should also be
		// a type).  All members of me must also be instances of aType.
		for (final AvailObject instance : object.instances())
		{
			if (!instance.isInstanceOf(aType))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean o_IsIntegerRangeType (final @NotNull AvailObject object)
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

	@Override
	public boolean o_IsMapType (final @NotNull AvailObject object)
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

	@Override
	public boolean o_IsSetType (final @NotNull AvailObject object)
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

	@Override
	public boolean o_IsTupleType (final @NotNull AvailObject object)
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

	@Override
	public boolean o_AcceptsArgTypesFromClosureType (
		final AvailObject object,
		final AvailObject closureType)
	{
		return getSuperkind(object).acceptsArgTypesFromClosureType(closureType);
	}

	@Override
	public boolean o_AcceptsArgumentTypesFromContinuation (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp,
		final int numArgs)
	{
		return getSuperkind(object).acceptsArgumentTypesFromContinuation(
			continuation,
			stackp,
			numArgs);
	}

	@Override
	public boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		return getSuperkind(object).acceptsListOfArgTypes(argTypes);
	}

	@Override
	public boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<AvailObject> argValues)
	{
		return getSuperkind(object).acceptsListOfArgValues(argValues);
	}

	@Override
	public boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final AvailObject argTypes)
	{
		return getSuperkind(object).acceptsTupleOfArgTypes(argTypes);
	}

	@Override
	public boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final AvailObject arguments)
	{
		return getSuperkind(object).acceptsTupleOfArguments(arguments);
	}

	@Override
	public AvailObject o_ArgsTupleType (final AvailObject object)
	{
		return getSuperkind(object).argsTupleType();
	}

	@Override
	public AvailObject o_CheckedExceptions (final AvailObject object)
	{
		return getSuperkind(object).checkedExceptions();
	}

	@Override
	public AvailObject o_ClosureType (final AvailObject object)
	{
		return getSuperkind(object).closureType();
	}

	@Override
	public AvailObject o_ContentType (final AvailObject object)
	{
		return getSuperkind(object).contentType();
	}

	@Override
	public boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		return getSuperkind(object).couldEverBeInvokedWith(argTypes);
	}

	@Override
	public boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final AvailObject anotherObject)
	{
		// A union type with a cached superkind is pretty good.
		return !object.objectSlot(ObjectSlots.CACHED_SUPERKIND).equalsVoid();
	}

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                	@Override
	public boolean o_IsBetterRepresentationThanTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		return true;
	}

	@Override
	public boolean o_IsInstanceOfKind (
		final AvailObject object,
		final AvailObject aType)
	{
		return UNION_TYPE.o().isSubtypeOf(aType)
			|| getSuperkind(object).isInstanceOfKind(aType);
	}

	@Override
	public boolean o_IsSupertypeOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject aLazyObjectType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfPrimitiveType (
		final AvailObject object,
		final AvailObject aPrimitiveType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		return false;
	}

	@Override
	public AvailObject o_KeyType (final AvailObject object)
	{
		return getSuperkind(object).keyType();
	}

	@Override
	public AvailObject o_MyType (final AvailObject object)
	{
		return getSuperkind(object).myType();
	}

	@Override
	public AvailObject o_Name (final AvailObject object)
	{
		return getSuperkind(object).name();
	}

	@Override
	public AvailObject o_Parent (final AvailObject object)
	{
		return getSuperkind(object).parent();
	}

	@Override
	public AvailObject o_ReturnType (final AvailObject object)
	{
		return getSuperkind(object).returnType();
	}

	@Override
	public AvailObject o_TypeIntersectionOfContinuationType (
		final AvailObject object,
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
		return AbstractUnionTypeDescriptor.withInstances(complyingInstances);
	}

	@Override
	public AvailObject o_TypeIntersectionOfCompiledCodeType (
		final AvailObject object,
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
		return AbstractUnionTypeDescriptor.withInstances(complyingInstances);
	}

	@Override
	public AvailObject o_TypeUnionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		return getSuperkind(object).typeUnionOfContinuationType(aContinuationType);
	}

	@Override
	public AvailObject o_TypeUnionOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		return getSuperkind(object).typeUnionOfContinuationType(aCompiledCodeType);
	}

	@Override
	public AvailObject o_ValueType (final AvailObject object)
	{
		return getSuperkind(object).valueType();
	}

	/**
	 * Avail's <code>boolean</code> type, the equivalent of Java's primitive
	 * <code>boolean</code> pseudo-type, and Java's other non-primitive boxed
	 * {@link Boolean} class.
	 */
	private static AvailObject Boolean;

	/**
	 * Return Avail's boolean type.
	 *
	 * @return The {@linkplain UnionTypeDescriptor instance union type} that
	 *         acts as Avail's <code>boolean</code> type.
	 */
	public static AvailObject booleanObject ()
	{
		return Boolean;
	}

	/**
	 * Create the boolean type, which is simply an {@linkplain
	 * UnionTypeDescriptor instance union} of {@linkplain
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
	 * Construct a new {@link UnionTypeDescriptor}.
	 *
	 * @param isMutable
	 *            Does the {@linkplain Descriptor descriptor} represent a
	 *            mutable object?
	 */
	protected UnionTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link UnionTypeDescriptor}.
	 */
	private final static UnionTypeDescriptor mutable = new UnionTypeDescriptor(
		true);

	/**
	 * Answer the mutable {@link UnionTypeDescriptor}.
	 *
	 * @return The mutable {@link UnionTypeDescriptor}.
	 */
	public static UnionTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link UnionTypeDescriptor}.
	 */
	private final static UnionTypeDescriptor immutable = new UnionTypeDescriptor(
		false);

	/**
	 * Answer the immutable {@link UnionTypeDescriptor}.
	 *
	 * @return The immutable {@link UnionTypeDescriptor}.
	 */
	public static UnionTypeDescriptor immutable ()
	{
		return immutable;
	}
}
