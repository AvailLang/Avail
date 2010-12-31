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

import static com.avail.descriptor.AvailObject.error;

public abstract class TypeDescriptor extends Descriptor
{


	// operations

	@Override
	public boolean o_Equals (
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

	@Override
	public boolean o_IsSubtypeOf (
		final AvailObject object,
		final AvailObject aType)
	{
		/* Check if object (a type) is a subtype of aType (should also be a
		 * type).
		 */

		error("Subclass responsibility: Object:isSubtypeOf: in Avail.TypeDescriptor", object);
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType)
	{
		//  Redefined for subclasses.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType)
	{
		/* By default, nothing is a supertype of a container type unless it
		 * states otherwise.
		 */

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		//  GENERATED pure (abstract) method.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfCyclicType (
		final AvailObject object,
		final AvailObject aCyclicType)
	{
		//  Redefined for subclasses.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType)
	{
		//  Redefined for subclasses.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		//  By default, nothing is a supertype of an integer range type unless it states otherwise.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		//  Redefined for subclasses.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfObjectMeta (
		final AvailObject object,
		final AvailObject anObjectMeta)
	{
		/* Check if I'm a supertype of the given object meta.  Redefined for
		 * subclasses.
		 */

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfObjectMetaMeta (
		final AvailObject object,
		final AvailObject anObjectMeta)
	{
		/* Check if I'm a supertype of the given object meta meta.  Redefined
		 * for subclasses.
		 */

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject aLazyObjectType)
	{
		/* By default, nothing is a supertype of an eager object type unless it
		 * states otherwise.
		 */

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfPrimitiveType (
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

	@Override
	public boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		//  Redefined for subclasses.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		//  Redefined for subclasses.

		return false;
	}

	@Override
	public AvailObject o_TypeIntersection (
		final AvailObject object,
		final AvailObject another)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		error("Subclass responsibility: Object:typeIntersection: in Avail.TypeDescriptor", object);
		return VoidDescriptor.voidObject();
	}

	@Override
	public AvailObject o_TypeIntersectionOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	@Override
	public AvailObject o_TypeIntersectionOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	@Override
	public AvailObject o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	@Override
	public AvailObject o_TypeIntersectionOfCyclicType (
		final AvailObject object,
		final AvailObject aCyclicType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return object.typeIntersectionOfMeta(aCyclicType);
	}

	@Override
	public AvailObject o_TypeIntersectionOfGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	@Override
	public AvailObject o_TypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	@Override
	public AvailObject o_TypeIntersectionOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	@Override
	public AvailObject o_TypeIntersectionOfMeta (
		final AvailObject object,
		final AvailObject someMeta)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.  Since metatypes intersect at terminatesType rather than
		 * terminates, we must be very careful to override this properly.
		 */

		return Types.terminates.object();
	}

	@Override
	public AvailObject o_TypeIntersectionOfObjectMeta (
		final AvailObject object,
		final AvailObject anObjectMeta)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return object.typeIntersectionOfMeta(anObjectMeta);
	}

	@Override
	public AvailObject o_TypeIntersectionOfObjectMetaMeta (
		final AvailObject object,
		final AvailObject anObjectMetaMeta)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return object.typeIntersectionOfMeta(anObjectMetaMeta);
	}

	@Override
	public AvailObject o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	@Override
	public AvailObject o_TypeIntersectionOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	@Override
	public AvailObject o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return Types.terminates.object();
	}

	@Override
	public AvailObject o_TypeUnion (
		final AvailObject object,
		final AvailObject another)
	{
		/* Answer the most specific type that still includes both of these.
		 */

		error("Subclass responsibility: Object:typeUnion: in Avail.TypeDescriptor", object);
		return VoidDescriptor.voidObject();
	}

	@Override
	public AvailObject o_TypeUnionOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.closure.object());
	}

	@Override
	public AvailObject o_TypeUnionOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.container.object());
	}

	@Override
	public AvailObject o_TypeUnionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.continuation.object());
	}

	@Override
	public AvailObject o_TypeUnionOfCyclicType (
		final AvailObject object,
		final AvailObject aCyclicType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.cyclicType.object());
	}

	@Override
	public AvailObject o_TypeUnionOfGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  "all" is the nearest supertype of [...]->void.
		 */

		return object.typeUnion(Types.all.object());
	}

	@Override
	public AvailObject o_TypeUnionOfIntegerRangeType (
		final AvailObject object,
		final AvailObject another)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.all.object());
	}

	@Override
	public AvailObject o_TypeUnionOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.all.object());
	}

	@Override
	public AvailObject o_TypeUnionOfObjectMeta (
		final AvailObject object,
		final AvailObject anObjectMeta)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  Because type 'objectType' is an objectMeta, not a primitive
		 * type.
		 */

		return object.typeUnion(Types.type.object());
	}

	@Override
	public AvailObject o_TypeUnionOfObjectMetaMeta (
		final AvailObject object,
		final AvailObject anObjectMetaMeta)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  Because type 'objectMeta' is an objectMetaMeta, not a
		 * primitive type.
		 */

		return object.typeUnion(Types.meta.object());
	}

	@Override
	public AvailObject o_TypeUnionOfObjectType (
		final AvailObject object,
		final AvailObject anEagerObjectType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  Because type 'object' is also an objectType.
		 */

		return object.typeUnion(Types.all.object());
	}

	@Override
	public AvailObject o_TypeUnionOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(Types.all.object());
	}

	@Override
	public AvailObject o_TypeUnionOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  This is just above extended integer, the most general integer
		 * range.
		 */

		return object.typeUnion(Types.all.object());
	}

	@Override
	public boolean o_IsSupertypeOfTerminates (
		final AvailObject object)
	{
		/* All types are supertypes of terminates.  This method only exists so
		 * that nontypes will cause a doesNotUnderstand: message to occur.
		 * Otherwise true would be embedded in
		 * TerminatesTypeDescriptor>>Object:isSubtypeOf:.
		 */

		return true;
	}

	@Override
	public boolean o_IsSupertypeOfVoid (
		final AvailObject object)
	{
		/* Only void is a supertype of void.  Overridden in VoidTypeDescriptor.
		 */

		return false;
	}

	@Override
	public boolean o_IsType (
		final AvailObject object)
	{
		return true;
	}




	// Startup/shutdown

	public enum Types
	{
		voidType(null, "type", VoidTypeDescriptor.mutable()),
		all(voidType, "type"),
		booleanType(all),
		trueType(booleanType),
		falseType(booleanType),
		character(all),
		closure(all),
		compiledCode(all),
		container(all, "containerType"),
		continuation(all),
		doubleObject(all),
		floatObject(all),
		implementationSet(all),
		messageBundle(all),
		messageBundleTree(all),

		parseNode(all),
		markerNode(parseNode),
		expressionNode(parseNode),
		assignmentNode(expressionNode),
		blockNode(expressionNode),
		literalNode(expressionNode),
		referenceNode(expressionNode),
		sendNode(expressionNode),
		superCastNode(expressionNode),
		tupleNode(expressionNode),
		variableUseNode(expressionNode),
		declarationNode(expressionNode),
		argumentNode(declarationNode),
		labelNode(declarationNode),
		localVariableNode(declarationNode),
		localConstantNode(declarationNode),
		moduleVariableNode(declarationNode),
		moduleConstantNode(declarationNode),

		process(all),
		signature(all),
		abstractSignature(signature),
		forwardSignature(signature),
		methodSignature(signature),
		type(all, "meta"),
		integerType(type, "meta"),
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
		terminatesType(null, "terminatesType", TerminatesMetaDescriptor.mutable()),
		terminates(null, "terminatesType", TerminatesTypeDescriptor.mutable());

		protected final Types parent;
		protected final String myTypeName;
		protected final AbstractDescriptor descriptor;
		protected AvailObject object;

		// Constructors
		Types (final Types parent, final String myTypeName, final AbstractDescriptor descriptor)
		{
			this.parent = parent;
			this.myTypeName = myTypeName;
			this.descriptor = descriptor;
		}

		Types (final Types parent, final String myTypeName)
		{
			this(
				parent,
				myTypeName,
				PrimitiveTypeDescriptor.mutable());
		}

		Types (final Types parent)
		{
			this(parent,"primType");
		}

		public AvailObject object ()
		{
			return object;
		}
	};

	static void createWellKnownObjects ()
	{
		//  Default implementation - subclasses may need more variations.

		final AvailObject voidObject = VoidDescriptor.voidObject();
		assert voidObject != null;

		// Build all the objects with void fields.
		for (final Types spec : Types.values())
		{
			spec.object = spec.descriptor.create();
			assert spec.object.descriptorId() != 0;
			spec.object.name(voidObject);
			spec.object.parent(voidObject);
			spec.object.myType(voidObject);
			spec.object.hash(spec.name().hashCode());
		}
		// Connect and name the objects.
		for (final Types spec : Types.values())
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
		}
		for (final Types spec : Types.values())
		{
			spec.object.makeImmutable();
		}
		// Sanity check them for metacovariance: a<=b -> a.type<=b.type
		for (final Types spec : Types.values())
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
		for (final Types spec : Types.values())
		{
			spec.object = null;
		}
	}

	/**
	 * Construct a new {@link TypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected TypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}
}
