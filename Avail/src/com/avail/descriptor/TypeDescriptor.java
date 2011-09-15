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
import java.util.List;
import com.avail.annotations.NotNull;

public abstract class TypeDescriptor
extends AbstractTypeDescriptor
{
	public enum Types
	{
		TOP(null, "TYPE", TopTypeDescriptor.mutable()),
		ANY(TOP, "TYPE"),
		ATOM(ANY),
		CHARACTER(ANY),
		CONTAINER(ANY, "CONTAINER_TYPE"),
		DOUBLE(ANY),
		FLOAT(ANY),
		IMPLEMENTATION_SET(ANY),
		MESSAGE_BUNDLE(ANY),
		MESSAGE_BUNDLE_TREE(ANY),

		PARSE_NODE(ANY),
		MARKER_NODE(PARSE_NODE),
		EXPRESSION_NODE(PARSE_NODE),
		ASSIGNMENT_NODE(EXPRESSION_NODE),
		BLOCK_NODE(EXPRESSION_NODE),
		LITERAL_NODE(EXPRESSION_NODE),
		REFERENCE_NODE(EXPRESSION_NODE),
		SEND_NODE(EXPRESSION_NODE),
		SEQUENCE_NODE(EXPRESSION_NODE),
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

		TOKEN(ANY),
		LITERAL_TOKEN(TOKEN),

		SIGNATURE(ANY),
		ABSTRACT_SIGNATURE(SIGNATURE),
		FORWARD_SIGNATURE(SIGNATURE),
		METHOD_SIGNATURE(SIGNATURE),
		MACRO_SIGNATURE(SIGNATURE),

		OBJECT(ANY),
		PROCESS(ANY),
		TYPE(ANY, "META"),
		CONTAINER_TYPE(TYPE, "META"),
		META(TYPE, "META"),
		UNION_TYPE(TYPE, "META");

		public final Types parent;
		protected final String myTypeName;
		protected final AbstractTypeDescriptor descriptor;
		private AvailObject o;


		/**
		 * Construct a new {@linkplain Types} instance with the specified
		 * parent, the name of the new type's type, and the descriptor to use.
		 *
		 * @param parent The new type's parent.
		 * @param myTypeName The new type's type's name.
		 * @param descriptor The descriptor for the new type.
		 */
		Types (
			final @NotNull Types parent,
			final @NotNull String myTypeName,
			final @NotNull AbstractTypeDescriptor descriptor)
		{
			this.parent = parent;
			this.myTypeName = myTypeName;
			this.descriptor = descriptor;
		}

		/**
		 * Construct a new {@linkplain Types} instance with the specified
		 * parent and the name of the new type's type.  Use a
		 * {@link PrimitiveTypeDescriptor} for the new type's descriptor.
		 *
		 * @param parent The new type's parent.
		 * @param myTypeName The new type's type's name.
		 */
		Types (final Types parent, final String myTypeName)
		{
			this(
				parent,
				myTypeName,
				PrimitiveTypeDescriptor.mutable());
		}

		/**
		 * Construct a new {@linkplain Types} instance with the specified
		 * parent.  Use {@linkplain #TYPE} for the new type's type, and use
		 * {@link PrimitiveTypeDescriptor} for the new type's descriptor.
		 *
		 * @param parent The new type's parent.
		 */
		Types (final Types parent)
		{
			this(parent,"TYPE");
		}

		/**
		 * Answer the {@link AvailObject} representing this Avail type.
		 *
		 * @return The actual {@linkplain TypeDescriptor type}, an AvailObject.
		 */
		public @NotNull AvailObject o ()
		{
			return o;
		}

		/**
		 * Set the AvailObject held by this enumeration.
		 *
		 * @param object An AvailObject or null.
		 */
		void set_o (final AvailObject object)
		{
			this.o = object;
		}
	}

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

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	@Override
	public boolean o_IsInstanceOfKind (
		final AvailObject object,
		final AvailObject aType)
	{
		return object.kind().isSubtypeOf(aType);
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
	public boolean o_IsSupertypeOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		//  GENERATED pure (abstract) method.

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
		 * specialized type could be a supertype of is bottom, but
		 * bottom doesn't dispatch this message.  Overridden in
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
	public @NotNull AvailObject o_TypeIntersectionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject someMeta)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.  Since metatypes intersect at bottom's type rather than
		 * bottom, we must be very careful to override this properly.
		 */
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		return object.typeUnion(CONTAINER.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return object.typeUnion(ANY.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anEagerObjectType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return object.typeUnion(ANY.o());
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
		return object.typeUnion(ANY.o());
	}

	@Override
	public boolean o_AcceptsArgTypesFromClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject closureType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	public boolean o_AcceptsArgumentTypesFromContinuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp,
		final int numArgs)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	public boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	public boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argValues)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	public boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argTypes)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	public boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final @NotNull AvailObject arguments)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	public @NotNull AvailObject o_ArgsTupleType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_CheckedExceptions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_ClosureType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_ContentType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	public @NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance)
	{
		return false;
	}

	@Override
	public boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherObject)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	public boolean o_IsBetterRepresentationThanTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	public boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	public boolean o_IsMapType (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	public boolean o_IsSetType (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	public boolean o_IsTupleType (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	public @NotNull AvailObject o_KeyType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public boolean o_LowerInclusive (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	public @NotNull AvailObject o_MyType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_Parent (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_ReturnType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public boolean o_UpperInclusive (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	public @NotNull AvailObject o_ValueType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	static void createWellKnownObjects ()
	{
		final AvailObject nullObject = NullDescriptor.nullObject();
		assert nullObject != null;

		// Build all the objects with top fields.
		for (final Types spec : Types.values())
		{
			final AvailObject o = spec.descriptor.create();
			assert o.descriptorId() != 0;
			o.name(nullObject);
			o.parent(nullObject);
			o.myType(nullObject);
			o.hash(spec.name().hashCode());
			spec.set_o(o);
		}
		// Connect and name the objects.
		for (final Types spec : Types.values())
		{
			final AvailObject o = spec.o();
			o.name(ByteStringDescriptor.from(spec.name()));
			o.parent(
				spec.parent == null
				? nullObject
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
				assert spec.o().isInstanceOfKind(spec.parent.o().kind());
			}
		}
	}

	static void clearWellKnownObjects ()
	{
		for (final Types spec : Types.values())
		{
			spec.set_o(null);
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
