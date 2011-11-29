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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.*;

public abstract class TypeDescriptor
extends AbstractTypeDescriptor
{
	public enum Types
	{
		TOP(null, "TYPE", TopTypeDescriptor.mutable()),
		ANY(TOP, "TYPE"),
		ATOM(ANY),
		CHARACTER(ANY),
		DOUBLE(ANY),
		FLOAT(ANY),
		IMPLEMENTATION_SET(ANY),
		MESSAGE_BUNDLE(ANY),
		MESSAGE_BUNDLE_TREE(ANY),

		TOKEN(ANY),
		LITERAL_TOKEN(TOKEN),

		SIGNATURE(ANY),
		ABSTRACT_SIGNATURE(SIGNATURE),
		FORWARD_SIGNATURE(SIGNATURE),
		METHOD_SIGNATURE(SIGNATURE),
		MACRO_SIGNATURE(SIGNATURE),
		RESTRICTION_SIGNATURE(SIGNATURE),

		OBJECT(ANY),
		RAW_POJO(ANY),

		PROCESS(ANY),

		TYPE(ANY, "META"),
		META(TYPE, "META");

		public final Types parent;
		protected final String myTypeName;
		protected final PrimitiveTypeDescriptor descriptor;
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
			final @NotNull PrimitiveTypeDescriptor descriptor)
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

	@Override @AvailMethod
	boolean o_Equals (
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

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		return object.kind().isSubtypeOf(aType);
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		/* Check if object (a type) is a subtype of aType (should also be a
		 * type).
		 */
		unsupportedOperation();
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		/* By default, nothing is a supertype of a container type unless it
		 * states otherwise.
		 */

		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		//  By default, nothing is a supertype of an integer range type unless it states otherwise.

		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aLazyObjectType)
	{
		/* By default, nothing is a supertype of an eager object type unless it
		 * states otherwise.
		 */

		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPrimitiveType (
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

	@Override @AvailMethod
	boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfUnionMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aUnionMeta)
	{
		return false;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject someMeta)
	{
		/* Answer the most general type that is still at least as specific as
		 * these.  Since metatypes intersect at bottom's type rather than
		 * bottom, we must be very careful to override this properly.
		 */
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return object.typeUnion(ANY.o());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anEagerObjectType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return object.typeUnion(ANY.o());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		/* Answer the most specific type that is still at least as general as
		 * these.  This is just above extended integer, the most general integer
		 * range.
		 */
		return object.typeUnion(ANY.o());
	}

	@Override @AvailMethod
	boolean o_AcceptsArgTypesFromFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject functionType)
	{
		unsupportedOperation();
		return false;
	}

	@Override @AvailMethod
	boolean o_AcceptsArgumentTypesFromContinuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp,
		final int numArgs)
	{
		unsupportedOperation();
		return false;
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		unsupportedOperation();
		return false;
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argValues)
	{
		unsupportedOperation();
		return false;
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argTypes)
	{
		unsupportedOperation();
		return false;
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final @NotNull AvailObject arguments)
	{
		unsupportedOperation();
		return false;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ArgsTupleType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_CheckedExceptions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_FunctionType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ContentType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		unsupportedOperation();
		return false;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherObject)
	{
		unsupportedOperation();
		return false;
	}

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThanTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		unsupportedOperation();
		return false;
	}

	@Override @AvailMethod
	boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsMapType (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSetType (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsTupleType (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_KeyType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	boolean o_LowerInclusive (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Parent (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ReturnType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override @AvailMethod
	boolean o_UpperInclusive (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ValueType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	/**
	 * Create any cached {@link AvailObject}s.
	 */
	static void createWellKnownObjects ()
	{
		// Build all the objects with null fields.
		for (final Types spec : Types.values())
		{
			final AvailObject o =
				spec.descriptor.createPrimitiveObjectNamed(spec.name());
			spec.set_o(o);
		}
		// Connect and name the objects.
		for (final Types spec : Types.values())
		{
			final AvailObject o = spec.o();
			o.name(ByteStringDescriptor.from(
				spec == TOP ? "⊤" : spec.name()));
			o.parent(
				spec.parent == null
					 ? NullDescriptor.nullObject()
					: spec.parent.o());
			o.myType(Types.valueOf(spec.myTypeName).o());
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

	/**
	 * Release all references to {@link AvailObject}s held by this class.
	 */
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
