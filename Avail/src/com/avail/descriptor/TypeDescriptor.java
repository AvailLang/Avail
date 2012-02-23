/**
 * TypeDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.compiler.scanning.AvailScanner;
import com.avail.test.TypeConsistencyTest;

/**
 * Every object in Avail has a type.  Types are also Avail objects.  The types
 * are related to each other by the {@linkplain
 * AvailObject#isSubtypeOf(AvailObject) subtype} relation in such a way that
 * they form a lattice.  The top of the lattice is {@linkplain Types#TOP ⊤
 * (pronounced "top")}, which is the most general type.  Every object conforms
 * with this type, and every subtype is a subtype of it.  The bottom of the
 * lattice is {@linkplain BottomTypeDescriptor ⊥ (pronounced "bottom")}, which
 * is the most specific type.  It has no instances, and it is a subtype of all
 * other types.
 *
 * <p>
 * The type lattice has a number of {@linkplain TypeConsistencyTest useful
 * properties}, such as closure under type union.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public abstract class TypeDescriptor
extends AbstractTypeDescriptor
{
	/**
	 * The {@code TypeDescriptor.Types} enumeration provides a place and a way
	 * to statically declare the upper stratum of Avail's type lattice,
	 * specifically all {@linkplain PrimitiveTypeDescriptor primitive types}.
	 *
	 * <p>
	 * Since Java classes are loaded on first use, we postpone creation of the
	 * actual AvailObjects until an explicit call to {@linkplain
	 * TypeDescriptor#createWellKnownObjects()}.  The Avail objects are
	 * extracted from the {@code Types} objects via the {@linkplain #o()}
	 * method.
	 * </p>
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum Types
	{
		/**
		 * The {@linkplain TopTypeDescriptor most general type} of the type
		 * lattice, also written ⊤ and pronounced "top".  All types are
		 * subtypes of this, and all objects are instances of it.  However, this
		 * top type has an additional role:  No variable or argument may be of
		 * this type, so the only thing that can be done with the result
		 * of a function call of type ⊤ is to implicitly discard it.  This is a
		 * precise way of making the traditional distinction between functions
		 * and procedures.  In fact, Avail requires all statements except the
		 * last one in a block to be of type ⊤, to ensure that functions are
		 * not accidentally used as procedures – and to ensure that the reader
		 * of the code knows it.
		 */
		TOP(null, "TYPE", TopTypeDescriptor.mutable()),

		/**
		 * This is the second-most general type in Avail's type lattice.  It is
		 * the only direct descendant of {@linkplain #TOP top (⊤)}, and all
		 * types except ⊤ are subtypes of it.  Like ⊤, all Avail objects are
		 * instances of {@code ANY}.  Technically there is also a {@linkplain
		 * NullDescriptor#nullObject() null object}, but that is only used
		 * internally by the Avail machinery (e.g., the value of an unassigned
		 * {@linkplain VariableDescriptor variable}) and can never be
		 * manipulated by an Avail program.
		 */
		ANY(TOP),

		/**
		 * This is the kind of all {@linkplain AtomDescriptor atoms}.  Atoms
		 * have fiat identity and their corresponding type structure is trivial.
		 */
		ATOM(ANY),

		/**
		 * This is the kind of all {@linkplain CharacterDescriptor characters},
		 * as defined by the <a href="http://www.unicode.org">Unicode</a>
		 * standard.  Note that all characters in the supplementary multilingual
		 * planes are explicitly supported.
		 */
		CHARACTER(ANY),

		/**
		 * {@code Number} is the generalization of all numeric types, which
		 * includes {@linkplain #FLOAT float}, {@linkplain #DOUBLE double}, and
		 * the {@linkplain IntegerRangeTypeDescriptor integer types} (which can
		 * contain both {@linkplain IntegerDescriptor integers} and the signed
		 * {@linkplain InfinityDescriptor integral infinities}),
		 */
		NUMBER(ANY),

		/**
		 * The type of all double-precision floating point numbers.  This
		 * includes the double precision {@linkplain
		 * DoubleDescriptor#positiveInfinity() positive} and {@linkplain
		 * DoubleDescriptor#negativeInfinity() negative} infinities and
		 * {@linkplain DoubleDescriptor#notANumber() Not-a-Number}.
		 */
		DOUBLE(NUMBER),

		/**
		 * The type of all single-precision floating point numbers.  This
		 * includes the single precision {@linkplain
		 * FloatDescriptor#positiveInfinity() positive} and {@linkplain
		 * FloatDescriptor#negativeInfinity() negative} infinities and
		 * {@linkplain FloatDescriptor#notANumber() Not-a-Number}.
		 */
		FLOAT(NUMBER),

		/**
		 * All {@linkplain MethodDescriptor methods} are
		 * of this kind.
		 */
		METHOD(ANY),

		/**
		 * This is the kind of all {@linkplain MessageBundleDescriptor message
		 * bundles}, which are used during parsing of Avail code.
		 */
		MESSAGE_BUNDLE(ANY),

		/**
		 * This is the kind of all {@linkplain MessageBundleTreeDescriptor
		 * message bundle trees}, which are lazily expanded during parallel
		 * parsing of Avail expressions.  They collapse together the cost of
		 * parsing method or macro invocations that start with the same tokens
		 * and arguments.
		 */
		MESSAGE_BUNDLE_TREE(ANY),

		/**
		 * {@linkplain TokenDescriptor Tokens} all have the same kind, except
		 * for {@linkplain #LITERAL_TOKEN literal tokens}.  They are produced by
		 * a {@linkplain AvailScanner lexical scanner} and are consumed by the
		 * {@linkplain AbstractAvailCompiler parser}.
		 */
		TOKEN(ANY),

		/**
		 * This type is the kind of all {@linkplain LiteralTokenDescriptor
		 * literal tokens}, which represent occurrences of values directly
		 * embedded in Avail text which are extracted during {@linkplain
		 * AvailScanner lexical scanning}.
		 */
		LITERAL_TOKEN(TOKEN),

		/**
		 * The general kind of {@linkplain ImplementationDescriptor method
		 * signatures}.
		 */
		SIGNATURE(ANY),

		/**
		 * The specific kind of a signature which is an {@linkplain
		 * AbstractDeclarationDescriptor abstract declaration}.
		 */
		ABSTRACT_SIGNATURE(SIGNATURE),

		/**
		 * The specific kind of signature which is a {@linkplain
		 * ForwardDeclarationDescriptor forward declaration}.  Such declarations
		 * must be resolved by the end of the module in which they occur.
		 */
		FORWARD_SIGNATURE(SIGNATURE),

		/**
		 * The specific kind of signature which is an actual {@linkplain
		 * MethodImplementationDescriptor method function}, by far the most common
		 * case.
		 */
		METHOD_SIGNATURE(SIGNATURE),

		/**
		 * The specific kind of signature which is an actual {@linkplain
		 * MacroImplementationDescriptor macro definition}.  An {@linkplain
		 * MethodDescriptor method} may not contain
		 * multiple macro signatures, nor may it mix macro signatures and any
		 * other type of signature.
		 */
		MACRO_SIGNATURE(SIGNATURE),

		/**
		 * {@linkplain ModuleDescriptor Modules} are maintained mostly
		 * automatically by Avail's runtime environment.  Modules are not
		 * currently visible to the Avail programmer, but there may still be a
		 * need for modules to be placed in sets and maps maintained by the
		 * runtime, so the type story has to at least be consistent.
		 */
		MODULE(ANY),

		/**
		 * A {@linkplain PojoDescriptor POJO} is a Plain Old Java {@linkplain
		 * Object}.  Avail is able to interface to arbitrary Java code via its
		 * implementation of POJOs.  POJOs contain (and conform to) their own
		 * POJO types, but that requires a separate concept of <em>raw</em>
		 * POJOs.  Avail code only works with the typed POJOs, but the Avail
		 * machinery has to be able to use the raw POJOs, placing them in sets
		 * and doing other things that occasionally require their kind to be
		 * extracted.
		 */
		RAW_POJO(ANY),

		/**
		 * {@linkplain ProcessDescriptor Processes} are the way Avail represents
		 * independent execution.
		 */
		PROCESS(ANY),

		/**
		 * Types are objects too, so they have to have their own types.  This is
		 * the most general kind of all types.  Since it's a type's type, it can
		 * also be called a metatype (or often just {@linkplain #META meta} for
		 * short).
		 */
		TYPE(ANY, "META"),

		/**
		 * Metatypes are special cases of both types and objects.  This is the
		 * most general kind for objects that are themselves metatypes.  That
		 * makes this a meta-metatype (i.e., a type of a type of a type of an
		 * object).
		 */
		META(TYPE, "META");


		/**
		 * The {@link Types} object representing this type's supertype.
		 */
		public final Types parent;

		/**
		 * The {@link Types} object representing this type's own type (a
		 * metatype).
		 */
		protected final String myTypeName;

		/**
		 * The descriptor to instantiate.  This allows {@link TopTypeDescriptor}
		 * to be used in place of {@link PrimitiveTypeDescriptor} for the top
		 * type.
		 */
		protected final PrimitiveTypeDescriptor descriptor;

		/**
		 * The {@link AvailObject} itself that this
		 */
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
		 * parent and the name of the new type's type.  Use a {@link
		 * PrimitiveTypeDescriptor} for the new type's descriptor.
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
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
	{
		/* By default, nothing is a supertype of a variable type unless it
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
	boolean o_IsSupertypeOfEnumerationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anEnumerationType)
	{
		return false;
	}

	@Override
	boolean o_IsSupertypeOfPojoBottomType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
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
	@NotNull AvailObject o_TypeIntersectionOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
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
	@NotNull AvailObject o_TypeUnionOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
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
		final @NotNull AvailObject anIntegerRangeType)
	{
		return object.typeUnion(NUMBER.o());
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
		return object.typeUnion(ANY.o());
	}

	@Override @AvailMethod
	boolean o_AcceptsArgTypesFromFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject functionType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_AcceptsArgumentTypesFromContinuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp,
		final int numArgs)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argValues)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argTypes)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final @NotNull AvailObject arguments)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ArgsTupleType (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_CheckedExceptions (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_FunctionType (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ContentType (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
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
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThanTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		throw unsupportedOperationException();
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
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_LowerInclusive (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Parent (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ReturnType (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_UpperInclusive (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ValueType (
		final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint)
	{
		// Most Avail types are opaque to Java, and can be characterized by the
		// class of AvailObject.
		return AvailObject.class;
	}

	/**
	 * Create any cached {@link AvailObject}s.
	 */
	static void createWellKnownObjects ()
	{
		// Build all the objects with null fields.
		for (final Types spec : Types.values())
		{
			final String name = spec == TOP
				? "⊤"
				: spec.name().toLowerCase().replace('_', ' ');
			final AvailObject o =
				spec.descriptor.createPrimitiveObjectNamed(name);
			spec.set_o(o);
		}
		// Connect and name the objects.
		for (final Types spec : Types.values())
		{
			final AvailObject o = spec.o();
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
