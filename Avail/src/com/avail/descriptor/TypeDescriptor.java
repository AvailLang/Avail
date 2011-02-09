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
import static com.avail.descriptor.TypeDescriptor.Types.*;
import com.avail.annotations.NotNull;

public abstract class TypeDescriptor
extends Descriptor
{

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		/* Check if object (a type) is a subtype of aType (should also be a
		 * type).
		 */

		error("Subclass responsibility: Object:isSubtypeOf: in Avail.TypeDescriptor", object);
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		//  Redefined for subclasses.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		/* By default, nothing is a supertype of a container type unless it
		 * states otherwise.
		 */

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		//  GENERATED pure (abstract) method.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfCyclicType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCyclicType)
	{
		//  Redefined for subclasses.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		//  Redefined for subclasses.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		//  By default, nothing is a supertype of an integer range type unless it states otherwise.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		//  Redefined for subclasses.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		/* Check if I'm a supertype of the given object meta.  Redefined for
		 * subclasses.
		 */

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfObjectMetaMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		/* Check if I'm a supertype of the given object meta meta.  Redefined
		 * for subclasses.
		 */

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aLazyObjectType)
	{
		/* By default, nothing is a supertype of an eager object type unless it
		 * states otherwise.
		 */

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType)
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
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		//  Redefined for subclasses.

		return false;
	}

	@Override
	public boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		//  Redefined for subclasses.

		return false;
	}

	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		error("Subclass responsibility: Object:typeIntersection: in Avail.TypeDescriptor", object);
		return VoidDescriptor.voidObject();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfCyclicType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCyclicType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return object.typeIntersectionOfMeta(aCyclicType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject someMeta)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.  Since metatypes intersect at terminatesType rather than
		 * terminates, we must be very careful to override this properly.
		 */

		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return object.typeIntersectionOfMeta(anObjectMeta);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectMetaMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMetaMeta)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return object.typeIntersectionOfMeta(anObjectMetaMeta);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.
		 */

		return TERMINATES.o();
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		/* Answer the most specific type that still includes both of these.
		 */

		error("Subclass responsibility: Object:typeUnion: in Avail.TypeDescriptor", object);
		return VoidDescriptor.voidObject();
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(CLOSURE.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(CONTAINER.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(CONTINUATION.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfCyclicType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCyclicType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(CYCLIC_TYPE.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  "all" is the nearest supertype of [...]->void.
		 */

		return object.typeUnion(ALL.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(ALL.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(ALL.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  Because type 'objectType' is an objectMeta, not a primitive
		 * type.
		 */

		return object.typeUnion(TYPE.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectMetaMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMetaMeta)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  Because type 'objectMeta' is an objectMetaMeta, not a
		 * primitive type.
		 */

		return object.typeUnion(META.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anEagerObjectType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  Because type 'object' is also an objectType.
		 */

		return object.typeUnion(ALL.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.
		 */

		return object.typeUnion(ALL.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  This is just above extended integer, the most general integer
		 * range.
		 */

		return object.typeUnion(ALL.o());
	}

	@Override
	public boolean o_IsSupertypeOfTerminates (
		final @NotNull AvailObject object)
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
		final @NotNull AvailObject object)
	{
		/* Only void is a supertype of void.  Overridden in VoidTypeDescriptor.
		 */

		return false;
	}

	@Override
	public boolean o_IsType (
		final @NotNull AvailObject object)
	{
		return true;
	}

	// Startup/shutdown

	public enum Types
	{
		VOID_TYPE(null, "TYPE", VoidTypeDescriptor.mutable()),
		ALL(VOID_TYPE, "TYPE"),
		BOOLEAN_TYPE(ALL),
		TRUE_TYPE(BOOLEAN_TYPE),
		FALSE_TYPE(BOOLEAN_TYPE),
		CHARACTER(ALL),
		CLOSURE(ALL),
		COMPILED_CODE(ALL),
		CONTAINER(ALL, "CONTAINER_TYPE"),
		CONTINUATION(ALL),
		DOUBLE(ALL),
		FLOAT(ALL),
		IMPLEMENTATION_SET(ALL),
		MESSAGE_BUNDLE(ALL),
		MESSAGE_BUNDLE_TREE(ALL),

		PARSE_NODE(ALL),
		MARKER_NODE(PARSE_NODE),
		EXPRESSION_NODE(PARSE_NODE),
		ASSIGNMENT_NODE(EXPRESSION_NODE),
		BLOCK_NODE(EXPRESSION_NODE),
		LITERAL_NODE(EXPRESSION_NODE),
		REFERENCE_NODE(EXPRESSION_NODE),
		SEND_NODE(EXPRESSION_NODE),
		SUPER_CAST_NODE(EXPRESSION_NODE),
		TUPLE_NODE(EXPRESSION_NODE),
		VARIABLE_USE_NODE(EXPRESSION_NODE),
		DECLARATION_NODE(EXPRESSION_NODE),
		ARGUMENT_NODE(DECLARATION_NODE),
		LABEL_NODE(DECLARATION_NODE),
		LOCAL_VARIABLE_NODE(DECLARATION_NODE),
		LOCAL_CONSTANT_NODE(DECLARATION_NODE),
		MODULE_VARIABLE_NODE(DECLARATION_NODE),
		MODULE_CONSTANT_NODE(DECLARATION_NODE),

		TOKEN(ALL),
		LITERAL_TOKEN(TOKEN),

		PROCESS(ALL),
		SIGNATURE(ALL),
		ABSTRACT_SIGNATURE(SIGNATURE),
		FORWARD_SIGNATURE(SIGNATURE),
		METHOD_SIGNATURE(SIGNATURE),
		MACRO_SIGNATURE(SIGNATURE),
		TYPE(ALL, "META"),
		INTEGER_TYPE(TYPE, "META"),
		MAP_TYPE(TYPE, "META"),
		META(TYPE, "META"),
		CYCLIC_TYPE(META, "CYCLIC_TYPE"),
		OBJECT_META_META(META, "META"),
		CONTAINER_TYPE(TYPE, "META"),
		CONTINUATION_TYPE(TYPE, "META"),
		PRIMITIVE_TYPE(TYPE, "META"),
		GENERALIZED_CLOSURE_TYPE(PRIMITIVE_TYPE, "META"),
		CLOSURE_TYPE(GENERALIZED_CLOSURE_TYPE, "META"),
		SET_TYPE(TYPE, "META"),
		TUPLE_TYPE(TYPE, "META"),
		TERMINATES_TYPE(
			null,
			"TERMINATES_TYPE",
			TerminatesMetaDescriptor.mutable()),
		TERMINATES(
			null,
			"TERMINATES_TYPE",
			TerminatesTypeDescriptor.mutable());

		protected final Types parent;
		protected final String myTypeName;
		protected final AbstractDescriptor descriptor;
		private AvailObject o;

		// Constructors
		Types (
		final @NotNull Types parent,
		final @NotNull String myTypeName,
		final @NotNull AbstractDescriptor descriptor)
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
			this(parent,"PRIMITIVE_TYPE");
		}

		public @NotNull AvailObject o ()
		{
			return o;
		}

		void o (final AvailObject object)
		{
			this.o = object;
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
			final AvailObject o = spec.descriptor.create();
			assert o.descriptorId() != 0;
			o.name(voidObject);
			o.parent(voidObject);
			o.myType(voidObject);
			o.hash(spec.name().hashCode());
			spec.o(o);
		}
		// Connect and name the objects.
		for (final Types spec : Types.values())
		{
			final AvailObject o = spec.o();
			o.name(ByteStringDescriptor.from(spec.name()));
			o.parent(
				spec.parent == null
				? voidObject
				: spec.parent.o());
			o.myType(
				Types.valueOf(spec.myTypeName).o());
		}
		for (final Types spec : Types.values())
		{
			spec.o().makeImmutable();
		}
		// Sanity check them for metacovariance: a<=b -> a.type<=b.type
		for (final Types spec : Types.values())
		{
			if (spec.parent != null)
			{
				assert spec.o().isSubtypeOf(spec.parent.o());
				assert spec.o().type().isSubtypeOf(
					spec.parent.o().type());
			}
		}
	}

	static void clearWellKnownObjects ()
	{
		for (final Types spec : Types.values())
		{
			spec.o(null);
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
