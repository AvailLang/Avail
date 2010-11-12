/**
 * descriptor/TypeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;

import static com.avail.descriptor.AvailObject.*;

public abstract class TypeDescriptor extends Descriptor
{


	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		/* A type can only be equal to another type, and only if each type is a
		 * subtype of the other.  This is rewritten in descriptor subclasses for
		 * efficiency and reversing the direction of the recursion between
		 * subtype checking and equality checking.
		 */

		return another.isType()
			&& object.isSubtypeOf(another)
			&& another.isSubtypeOf(object);
	}



	// operations-types

	boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		/* Check if object (a type) is a subtype of aType (should also be a
		 * type).
		 */

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
		/* By default, nothing is a supertype of a container type unless it
		 * states otherwise.
		 */

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
		/* Check if I'm a supertype of the given object meta.  Redefined for
		 * subclasses.
		 */

		return false;
	}

	boolean ObjectIsSupertypeOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		/* Check if I'm a supertype of the given object meta meta.  Redefined
		 * for subclasses.
		 */

		return false;
	}

	boolean ObjectIsSupertypeOfObjectType (
			final AvailObject object, 
			final AvailObject aLazyObjectType)
	{
		/* By default, nothing is a supertype of an eager object type unless it
		 * states otherwise.
		 */

		return false;
	}

	boolean ObjectIsSupertypeOfPrimitiveType (
			final AvailObject object, 
			final AvailObject aPrimitiveType)
	{
		/* Check if object (some specialized type) is a supertype of
		 * aPrimitiveType (some primitive type).  The only primitive type this
		 * specialized type could be a supertype of is terminates, but
		 * terminates doesn't dispatch this message.  Overridden in
		 * PrimitiveTypeDescriptor.
		 */

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
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		error("Subclass responsibility: Object:typeIntersection: in Avail.TypeDescriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	AvailObject ObjectTypeIntersectionOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	AvailObject ObjectTypeIntersectionOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	AvailObject ObjectTypeIntersectionOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return object.typeIntersectionOfMeta(aCyclicType);
	}

	AvailObject ObjectTypeIntersectionOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	AvailObject ObjectTypeIntersectionOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	AvailObject ObjectTypeIntersectionOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	AvailObject ObjectTypeIntersectionOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	AvailObject ObjectTypeIntersectionOfMeta (
			final AvailObject object, 
			final AvailObject someMeta)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.  Since metatypes intersect at terminatesType rather than
		 * terminates, we must be very careful to override this properly.
		 */

		return Types.terminates.object();
	}

	AvailObject ObjectTypeIntersectionOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return object.typeIntersectionOfMeta(anObjectMeta);
	}

	AvailObject ObjectTypeIntersectionOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return object.typeIntersectionOfMeta(anObjectMetaMeta);
	}

	AvailObject ObjectTypeIntersectionOfObjectType (
			final AvailObject object, 
			final AvailObject anObjectType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	AvailObject ObjectTypeIntersectionOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	AvailObject ObjectTypeIntersectionOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	AvailObject ObjectTypeUnion (
			final AvailObject object, 
			final AvailObject another)
	{
		/* Answer the most specific type that still includes both of these.
		 */

		error("Subclass responsibility: Object:typeUnion: in Avail.TypeDescriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.closure.object());
	}

	AvailObject ObjectTypeUnionOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.container.object());
	}

	AvailObject ObjectTypeUnionOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.continuation.object());
	}

	AvailObject ObjectTypeUnionOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.cyclicType.object());
	}

	AvailObject ObjectTypeUnionOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  "all" is the nearest supertype of [...]->void.
		 */
		
		return object.typeUnion(Types.all.object());
	}

	AvailObject ObjectTypeUnionOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject another)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.all.object());
	}

	AvailObject ObjectTypeUnionOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  Note: list is not under the type all.
		 */
		
		return Types.voidType.object();
	}

	AvailObject ObjectTypeUnionOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.all.object());
	}

	AvailObject ObjectTypeUnionOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  Because type 'objectType' is an objectMeta, not a primitive
		 * type.
		 */
		
		return object.typeUnion(Types.type.object());
	}

	AvailObject ObjectTypeUnionOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  Because type 'objectMeta' is an objectMetaMeta, not a
		 * primitive type.
		 */
		
		return object.typeUnion(Types.meta.object());
	}

	AvailObject ObjectTypeUnionOfObjectType (
			final AvailObject object, 
			final AvailObject anEagerObjectType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  Because type 'object' is also an objectType.
		 */
		
		return object.typeUnion(Types.all.object());
	}

	AvailObject ObjectTypeUnionOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.all.object());
	}

	AvailObject ObjectTypeUnionOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  This is just above extended integer, the most general integer
		 * range.
		 */
		
		return object.typeUnion(Types.all.object());
	}

	boolean ObjectIsSupertypeOfTerminates (
			final AvailObject object)
	{
		/* All types are supertypes of terminates.  This method only exists so
		 * that nontypes will cause a doesNotUnderstand: message to occur.
		 * Otherwise true would be embedded in
		 * TerminatesTypeDescriptor>>Object:isSubtypeOf:.
		 */

		return true;
	}

	boolean ObjectIsSupertypeOfVoid (
			final AvailObject object)
	{
		/* Only void is a supertype of void.  Overridden in VoidTypeDescriptor.
		 */

		return false;
	}

	boolean ObjectIsType (
			final AvailObject object)
	{
		return true;
	}




	// Startup/shutdown

	public enum Types
	{
		voidType(null, "type", VoidTypeDescriptor.mutableDescriptor()),
			all(voidType, "type"),
				booleanType(all, "primType"),
					trueType(booleanType, "primType"),
					falseType(booleanType, "primType"),
				character(all, "primType"),
				closure(all, "primType"),
				compiledCode(all, "primType"),
				container(all, "containerType"),
				continuation(all, "primType"),
				doubleObject(all, "primType"),
				floatObject(all, "primType"),
				implementationSet(all, "primType"),
				messageBundle(all, "primType"),
				messageBundleTree(all, "primType"),
				parseNode(all, "primType"),
					assignmentNode(parseNode, "primType"),
					blockNode(parseNode, "primType"),
					listNode(parseNode, "primType"),
					literalNode(parseNode, "primType"),
					referenceNode(parseNode, "primType"),
					sendNode(parseNode, "primType"),
					superCastNode(parseNode, "primType"),
					variableDeclarationNode(parseNode, "primType"),
						syntheticConstantNode(variableDeclarationNode, "primType"),
						initializingDeclarationNode(variableDeclarationNode, "primType"),
							constantDeclarationNode(initializingDeclarationNode, "primType"),
						labelNode(variableDeclarationNode, "primType"),
						syntheticDeclarationNode(variableDeclarationNode, "primType"),
					variableUseNode(parseNode, "primType"),
				process(all, "primType"),
				signature(all, "primType"),
					abstractSignature(signature, "primType"),
					forwardSignature(signature, "primType"),
					methodSignature(signature, "primType"),
				type(all, "meta"),
					integerType(type, "meta"),
					listType(type, "meta"),
					mapType(type, "meta"),
					meta(type, "meta"),
						cyclicType(meta, "cyclicType"),
						objectMetaMeta(meta, "meta"),
					containerType(type, "meta"),
					continuationType(type, "meta"),
					primType(type, "meta"),
						generalizedClosureType(primType, "meta"),
							closureType(generalizedClosureType, "meta"),
					setType(type, "meta"),
					tupleType(type, "meta"),
			terminatesType(null, "terminatesType", TerminatesMetaDescriptor.mutableDescriptor()),
			terminates(null, "terminatesType", TerminatesTypeDescriptor.mutableDescriptor());
				
		private final Types parent;
		private final String myTypeName;
		private final Descriptor descriptor;
		private AvailObject object;

		// Constructors
		Types (Types parent, String myTypeName, Descriptor descriptor)
		{
			this.parent = parent;
			this.myTypeName = myTypeName;
			this.descriptor = descriptor;
		}

		Types (Types parent, String myTypeName)
		{
			this(
				parent,
				myTypeName,
				PrimitiveTypeDescriptor.mutableDescriptor());
		}
		
		public AvailObject object ()
		{
			return object;
		}
	};

	static void createWellKnownObjects ()
	{
		//  Default implementation - subclasses may need more variations.

		AvailObject voidObject = VoidDescriptor.voidObject();
		assert voidObject != null;

		// Build all the objects with void fields.
		for (Types spec : Types.values())
		{
			spec.object = AvailObject.newIndexedDescriptor(0, spec.descriptor);
			assert spec.object.descriptorId() != 0;
			spec.object.name(voidObject);
			spec.object.parent(voidObject);
			spec.object.myType(voidObject);
			spec.object.hash(spec.name().hashCode());
		}
		// Connect and name the objects.
		for (Types spec : Types.values())
		{
			spec.object.name(
				ByteStringDescriptor.mutableObjectFromNativeByteString(
					spec.name()));
			spec.object.parent(
				spec.parent == null
					? voidObject
					: spec.parent.object);
			spec.object.myType(
				Types.valueOf(spec.myTypeName).object);
			spec.object.makeImmutable();
		}
		// Sanity check them for metacovariance: a<=b -> a.type<=b.type
		for (Types spec : Types.values())
		{
			if (spec.parent != null)
			{
				assert spec.object.isSubtypeOf(spec.parent.object);
				assert spec.object.type().isSubtypeOf(
					spec.parent.object.type());
			}
		}
	}

	static void clearWellKnownObjects ()
	{
		for (Types spec : Types.values())
		{
			spec.object = null;
		}
	}

}
