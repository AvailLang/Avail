/**
 * descriptor/TypeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this *   list of conditions and the following disclaimer.
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import java.util.HashMap;
import java.util.Map;
import static com.avail.descriptor.AvailObject.*;

public abstract class TypeDescriptor extends Descriptor
{


	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		//  A type can only be equal to another type, and only if each type is a subtype of the other.
		//  This is rewritten in descriptor subclasses for efficiency and reversing the direction of the
		//  recursion between subtype checking and equality checking.

		return (another.isType() && (object.isSubtypeOf(another) && another.isSubtypeOf(object)));
	}



	// operations-types

	boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		error("Subclass responsibility: Object:isSubtypeOf: in Avail.TypeDescriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  Redefined for subclasses.

		return false;
	}

	boolean ObjectIsSupertypeOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  By default, nothing is a supertype of a container type unless it states otherwise.

		return false;
	}

	boolean ObjectIsSupertypeOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		//  GENERATED pure (abstract) method.

		return false;
	}

	boolean ObjectIsSupertypeOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  Redefined for subclasses.

		return false;
	}

	boolean ObjectIsSupertypeOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  Redefined for subclasses.

		return false;
	}

	boolean ObjectIsSupertypeOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		//  By default, nothing is a supertype of an integer range type unless it states otherwise.

		return false;
	}

	boolean ObjectIsSupertypeOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  List types are covariant by their content type.

		return false;
	}

	boolean ObjectIsSupertypeOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  Redefined for subclasses.

		return false;
	}

	boolean ObjectIsSupertypeOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  Check if I'm a supertype of the given object meta.  Redefined for subclasses.

		return false;
	}

	boolean ObjectIsSupertypeOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  Check if I'm a supertype of the given object meta meta.  Redefined for subclasses.

		return false;
	}

	boolean ObjectIsSupertypeOfObjectType (
			final AvailObject object, 
			final AvailObject aLazyObjectType)
	{
		//  By default, nothing is a supertype of an eager object type unless it states otherwise.

		return false;
	}

	boolean ObjectIsSupertypeOfPrimitiveType (
			final AvailObject object, 
			final AvailObject aPrimitiveType)
	{
		//  Check if object (some specialized type) is a supertype of aPrimitiveType (some primitive
		//  type).  The only primitive type this specialized type could be a supertype of is :terminates,
		//  but :terminates doesn't dispatch this message.  Overidden in PrimitiveTypeDescriptor.

		return false;
	}

	boolean ObjectIsSupertypeOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  Redefined for subclasses.

		return false;
	}

	boolean ObjectIsSupertypeOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Redefined for subclasses.

		return false;
	}

	AvailObject ObjectTypeIntersection (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Answer the most general type that is still at least as specific as these.

		error("Subclass responsibility: Object:typeIntersection: in Avail.TypeDescriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return TypeDescriptor.terminates();
	}

	AvailObject ObjectTypeIntersectionOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return TypeDescriptor.terminates();
	}

	AvailObject ObjectTypeIntersectionOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return TypeDescriptor.terminates();
	}

	AvailObject ObjectTypeIntersectionOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return object.typeIntersectionOfMeta(aCyclicType);
	}

	AvailObject ObjectTypeIntersectionOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return TypeDescriptor.terminates();
	}

	AvailObject ObjectTypeIntersectionOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return TypeDescriptor.terminates();
	}

	AvailObject ObjectTypeIntersectionOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return TypeDescriptor.terminates();
	}

	AvailObject ObjectTypeIntersectionOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return TypeDescriptor.terminates();
	}

	AvailObject ObjectTypeIntersectionOfMeta (
			final AvailObject object, 
			final AvailObject someMeta)
	{
		//  Answer the most general type that is still at least as specific as these.
		//  Since metas intersect at terminatesType rather than terminates, we must
		//  be very careful to overide this properly.

		return TypeDescriptor.terminates();
	}

	AvailObject ObjectTypeIntersectionOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  Answer the most general type that is still at least as specific as these.

		return object.typeIntersectionOfMeta(anObjectMeta);
	}

	AvailObject ObjectTypeIntersectionOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  Answer the most general type that is still at least as specific as these.

		return object.typeIntersectionOfMeta(anObjectMetaMeta);
	}

	AvailObject ObjectTypeIntersectionOfObjectType (
			final AvailObject object, 
			final AvailObject anObjectType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return TypeDescriptor.terminates();
	}

	AvailObject ObjectTypeIntersectionOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return TypeDescriptor.terminates();
	}

	AvailObject ObjectTypeIntersectionOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return TypeDescriptor.terminates();
	}

	AvailObject ObjectTypeUnion (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Answer the most specific type that still includes both of these.

		error("Subclass responsibility: Object:typeUnion: in Avail.TypeDescriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.typeUnion(TypeDescriptor.closure());
	}

	AvailObject ObjectTypeUnionOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.typeUnion(TypeDescriptor.container());
	}

	AvailObject ObjectTypeUnionOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.typeUnion(TypeDescriptor.continuation());
	}

	AvailObject ObjectTypeUnionOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.typeUnion(TypeDescriptor.cyclicType());
	}

	AvailObject ObjectTypeUnionOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.

		//  all is the supertype of [...]->void
		return object.typeUnion(TypeDescriptor.all());
	}

	AvailObject ObjectTypeUnionOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.typeUnion(TypeDescriptor.all());
	}

	AvailObject ObjectTypeUnionOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  Answer the most specific type that is still at least as general as these.

		//  Note: list is not under the type all.
		return TypeDescriptor.voidType();
	}

	AvailObject ObjectTypeUnionOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.typeUnion(TypeDescriptor.all());
	}

	AvailObject ObjectTypeUnionOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  Answer the most specific type that is still at least as general as these.

		//  Because type 'objectType' is an objectMeta, not a primitive type.
		return object.typeUnion(TypeDescriptor.type());
	}

	AvailObject ObjectTypeUnionOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  Answer the most specific type that is still at least as general as these.

		//  Because type 'objectMeta' is an objectMetaMeta, not a primitive type.
		return object.typeUnion(TypeDescriptor.meta());
	}

	AvailObject ObjectTypeUnionOfObjectType (
			final AvailObject object, 
			final AvailObject anEagerObjectType)
	{
		//  Answer the most specific type that is still at least as general as these.

		//  Because type 'object' is also an objectType.
		return object.typeUnion(TypeDescriptor.all());
	}

	AvailObject ObjectTypeUnionOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.typeUnion(TypeDescriptor.all());
	}

	AvailObject ObjectTypeUnionOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Answer the most specific type that is still at least as general as these.

		//  just above extended integer, the most general integer range
		return object.typeUnion(TypeDescriptor.all());
	}

	boolean ObjectIsSupertypeOfTerminates (
			final AvailObject object)
	{
		//  All types are supertypes of :terminates.  This method only exists so that nontypes
		//  will cause a doesNotUnderstand: message to occur.  Otherwise true would be
		//  embedded in TerminatesTypeDescriptor>>Object:isSubtypeOf:

		return true;
	}

	boolean ObjectIsSupertypeOfVoid (
			final AvailObject object)
	{
		//  Only :void is a supertype of :void.  Overridden in VoidTypeDescriptor.

		return false;
	}

	boolean ObjectIsType (
			final AvailObject object)
	{
		return true;
	}




	// Startup/shutdown

	static Map<String, AvailObject> SpecialTypes;

	static void createWellKnownObjects ()
	{
		//  Default implementation - subclasses may need more variations.

		AvailObject voidObject = VoidDescriptor.voidObject();
		assert voidObject != null;
		class TypeSpec
		{
			String name;
			String parent;
			String myType;
			Descriptor descriptor;
			TypeSpec(String name, String parent, String myType, Descriptor descriptor)
			{
				this.name = name;
				this.parent = parent;
				this.myType = myType;
				this.descriptor = descriptor;
			}
		}
		TypeSpec [] specs = {
			new TypeSpec("voidType", "*", "primType", VoidTypeDescriptor.mutableDescriptor()),
			new TypeSpec(    "all", "voidType", "type", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "booleanType", "all", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "trueType", "booleanType", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "falseType", "booleanType", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "character", "all", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "closure", "all", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "compiledCode", "all", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "container", "all", "containerType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "continuation", "all", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "doubleObject", "all", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "floatObject", "all", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "implementationSet", "all", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "messageBundle", "all", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "messageBundleTree", "all", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "process", "all", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "signature", "all", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "abstractSignature", "signature", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "forwardSignature", "signature", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "methodSignature", "signature", "primType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(        "type", "all", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "generalizedClosureType", "primType", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(                "closureType", "generalizedClosureType", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "containerType", "primType", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "continuationType", "primType", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "integerType", "primType", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "listType", "type", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "mapType", "primType", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "meta", "type", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(                "cyclicType", "meta", "cyclicType", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(                "objectMetaMeta", "meta", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "primType", "type", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "setType", "primType", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "tupleType", "primType", "meta", PrimitiveTypeDescriptor.mutableDescriptor()),
			new TypeSpec(            "terminatesType", "*", "terminatesType", TerminatesMetaDescriptor.mutableDescriptor()),
			new TypeSpec("terminates", "*", "terminatesType", TerminatesTypeDescriptor.mutableDescriptor())
		};
		Map<String,AvailObject> typeMap = new HashMap<String,AvailObject>(100);
		for (TypeSpec spec : specs)
		{
			AvailObject type = AvailObject.newIndexedDescriptor(
				0,
				spec.descriptor);
			assert type.descriptorId() != 0;
			typeMap.put(spec.name, type);
			// Maintain memory integrity while we connect stuff
			type.name(voidObject);
			type.parent(voidObject);
			type.myType(voidObject);
			// Now we can create the name string safely.
			type.name(ByteStringDescriptor.mutableObjectFromNativeByteString(spec.name));
		}
		// Now link them together.
		for (TypeSpec spec : specs)
		{
			int hash = spec.name.hashCode();
			AvailObject type = typeMap.get(spec.name);
			type.hash(hash);
			type.parent(
				spec.parent.equals("*")
					? voidObject
					: typeMap.get(spec.parent));
			type.myType(typeMap.get(spec.myType));
		}
		for (AvailObject type : typeMap.values())
		{
			type.makeImmutable();
		}
		SpecialTypes = typeMap;
	}

	static void clearWellKnownObjects ()
	{
		SpecialTypes = null;
	}



	/* Accessing special types... */
	public static final AvailObject abstractSignature ()
	{
		return SpecialTypes.get("abstractSignature");
	}

	public static final AvailObject all ()
	{
		return SpecialTypes.get("all");
	}

	public static final AvailObject booleanType ()
	{
		return SpecialTypes.get("booleanType");
	}

	public static final AvailObject character ()
	{
		return SpecialTypes.get("character");
	}

	public static final AvailObject closure ()
	{
		return SpecialTypes.get("closure");
	}

	public static final AvailObject closureType ()
	{
		return SpecialTypes.get("closureType");
	}

	public static final AvailObject compiledCode ()
	{
		return SpecialTypes.get("compiledCode");
	}

	public static final AvailObject container ()
	{
		return SpecialTypes.get("container");
	}

	public static final AvailObject containerType ()
	{
		return SpecialTypes.get("containerType");
	}

	public static final AvailObject continuation ()
	{
		return SpecialTypes.get("continuation");
	}

	public static final AvailObject continuationType ()
	{
		return SpecialTypes.get("continuationType");
	}

	public static final AvailObject cyclicType ()
	{
		return SpecialTypes.get("cyclicType");
	}

	public static final AvailObject doubleObject ()
	{
		return SpecialTypes.get("doubleObject");
	}

	public static final AvailObject falseType ()
	{
		return SpecialTypes.get("falseType");
	}

	public static final AvailObject floatObject ()
	{
		return SpecialTypes.get("floatObject");
	}

	public static final AvailObject forwardSignature ()
	{
		return SpecialTypes.get("forwardSignature");
	}

	public static final AvailObject generalizedClosureType ()
	{
		return SpecialTypes.get("generalizedClosureType");
	}

	public static final AvailObject implementationSet ()
	{
		return SpecialTypes.get("implementationSet");
	}

	public static final AvailObject integerType ()
	{
		return SpecialTypes.get("integerType");
	}

	public static final AvailObject listType ()
	{
		return SpecialTypes.get("listType");
	}

	public static final AvailObject mapType ()
	{
		return SpecialTypes.get("mapType");
	}

	public static final AvailObject messageBundle ()
	{
		return SpecialTypes.get("messageBundle");
	}

	public static final AvailObject messageBundleTree ()
	{
		return SpecialTypes.get("messageBundleTree");
	}

	public static final AvailObject meta ()
	{
		return SpecialTypes.get("meta");
	}

	public static final AvailObject methodSignature ()
	{
		return SpecialTypes.get("methodSignature");
	}

	public static final AvailObject objectMetaMeta ()
	{
		return SpecialTypes.get("objectMetaMeta");
	}

	public static final AvailObject primType ()
	{
		return SpecialTypes.get("primType");
	}

	public static final AvailObject process ()
	{
		return SpecialTypes.get("process");
	}

	public static final AvailObject setType ()
	{
		return SpecialTypes.get("setType");
	}

	public static final AvailObject signature ()
	{
		return SpecialTypes.get("signature");
	}

	public static final AvailObject terminates ()
	{
		return SpecialTypes.get("terminates");
	}

	public static final AvailObject terminatesType ()
	{
		return SpecialTypes.get("terminatesType");
	}

	public static final AvailObject trueType ()
	{
		return SpecialTypes.get("trueType");
	}

	public static final AvailObject tupleType ()
	{
		return SpecialTypes.get("tupleType");
	}

	public static final AvailObject type ()
	{
		return SpecialTypes.get("type");
	}

	public static final AvailObject voidType ()
	{
		return SpecialTypes.get("voidType");
	}

}
