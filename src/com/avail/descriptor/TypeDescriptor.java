/*
 * TypeDescriptor.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.compiler.AvailCompiler;
import com.avail.interpreter.AvailLoader.LexicalScanner;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.PrimitiveTypeDescriptor.createMutablePrimitiveObjectNamed;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.utility.Nulls.stripNull;

/**
 * Every object in Avail has a type.  Types are also Avail objects.  The types
 * are related to each other by the {@linkplain
 * AvailObject#isSubtypeOf(A_Type) subtype} relation in such a way that
 * they form a lattice.  The top of the lattice is {@linkplain Types#TOP ⊤
 * (pronounced "top")}, which is the most general type.  Every object conforms
 * with this type, and every subtype is a subtype of it.  The bottom of the
 * lattice is {@linkplain BottomTypeDescriptor ⊥ (pronounced "bottom")}, which
 * is the most specific type.  It has no instances, and it is a subtype of all
 * other types.
 *
 * <p>
 * The type lattice has a number of useful properties, such as closure under
 * type union.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class TypeDescriptor
extends AbstractTypeDescriptor
{
	/**
	 * The {@code TypeDescriptor.Types} enumeration provides a place and a way
	 * to statically declare the upper stratum of Avail's type lattice,
	 * specifically all {@linkplain PrimitiveTypeDescriptor primitive types}.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
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
		TOP(TypeTag.TOP_TYPE_TAG),

		/**
		 * This is the second-most general type in Avail's type lattice.  It is
		 * the only direct descendant of {@linkplain #TOP top (⊤)}, and all
		 * types except ⊤ are subtypes of it.  Like ⊤, all Avail objects are
		 * instances of {@code ANY}. Technically there is also a {@linkplain
		 * NilDescriptor#nil nil}, but that is only used internally by the
		 * Avail machinery (e.g., the value of an unassigned {@linkplain
		 * VariableDescriptor variable}) and can never be manipulated by an
		 * Avail program.
		 */
		ANY(TOP, TypeTag.ANY_TYPE_TAG),

		/**
		 * This is the kind of all nontypes.
		 */
		NONTYPE(ANY, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * This is the kind of all {@linkplain AtomDescriptor atoms}.  Atoms
		 * have fiat identity and their corresponding type structure is trivial.
		 */
		ATOM(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * This is the kind of all {@linkplain CharacterDescriptor characters},
		 * as defined by the <a href="http://www.unicode.org">Unicode</a>
		 * standard.  Note that all characters in the supplementary multilingual
		 * planes are explicitly supported.
		 */
		CHARACTER(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * {@code Number} is the generalization of all numeric types, which
		 * includes {@linkplain #FLOAT float}, {@linkplain #DOUBLE double}, and
		 * the {@linkplain IntegerRangeTypeDescriptor integer types} (which can
		 * contain both {@linkplain IntegerDescriptor integers} and the signed
		 * {@linkplain InfinityDescriptor integral infinities}),
		 */
		NUMBER(NONTYPE, TypeTag.NUMBER_TYPE_TAG),

		/**
		 * The type of all double-precision floating point numbers.  This
		 * includes the double precision {@linkplain
		 * DoubleDescriptor#doublePositiveInfinity() positive} and {@linkplain
		 * DoubleDescriptor#doubleNegativeInfinity() negative} infinities and
		 * {@linkplain DoubleDescriptor#doubleNotANumber() Not-a-Number}.
		 */
		DOUBLE(NUMBER, TypeTag.NUMBER_TYPE_TAG),

		/**
		 * The type of all single-precision floating point numbers.  This
		 * includes the single precision {@linkplain
		 * FloatDescriptor#floatPositiveInfinity() positive} and {@linkplain
		 * FloatDescriptor#floatNegativeInfinity() negative} infinities and
		 * {@linkplain FloatDescriptor#floatNotANumber() Not-a-Number}.
		 */
		FLOAT(NUMBER, TypeTag.NUMBER_TYPE_TAG),

		/**
		 * All {@linkplain LexerDescriptor lexers} are of this kind.
		 */
		LEXER(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * All {@linkplain MethodDescriptor methods} are of this kind.
		 */
		METHOD(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * This is the kind of all {@linkplain MessageBundleDescriptor message
		 * bundles}, which are used during parsing of Avail code.
		 */
		MESSAGE_BUNDLE(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * This is the kind of all {@linkplain DefinitionParsingPlanDescriptor
		 * definition parsing plans}, which are used during parsing of Avail
		 * code.
		 */
		DEFINITION_PARSING_PLAN(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * This is the kind of all {@linkplain ParsingPlanInProgressDescriptor
		 * parsing-plans-in-progress}, which are used during parsing of Avail
		 * code.
		 */
		PARSING_PLAN_IN_PROGRESS(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * This is the kind of all {@linkplain MessageBundleTreeDescriptor
		 * message bundle trees}, which are lazily expanded during parallel
		 * parsing of Avail expressions.  They collapse together the cost of
		 * parsing method or macro invocations that start with the same tokens
		 * and arguments.
		 */
		MESSAGE_BUNDLE_TREE(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * {@linkplain TokenDescriptor Tokens} all have the same kind, except
		 * for {@linkplain LiteralTokenDescriptor literal tokens}, which are
		 * parametrically typed by the type of value they contain.  They are
		 * produced by a {@linkplain LexicalScanner lexical scanner} and are
		 * consumed by the {@linkplain AvailCompiler parser}.
		 */
		TOKEN(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * The general kind of {@linkplain DefinitionDescriptor method
		 * signatures}.
		 */
		DEFINITION(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * The specific kind of a definition which is an {@linkplain
		 * AbstractDefinitionDescriptor abstract declaration} of a method.
		 */
		ABSTRACT_DEFINITION(DEFINITION, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * The specific kind of definition which is a {@linkplain
		 * ForwardDefinitionDescriptor forward declaration}.  Such declarations
		 * must be resolved by the end of the module in which they occur.
		 */
		FORWARD_DEFINITION(DEFINITION, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * The specific kind of signature which is an actual {@linkplain
		 * MethodDefinitionDescriptor method function}, by far the most
		 * common case.
		 */
		METHOD_DEFINITION(DEFINITION, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * The specific kind of signature which is an actual {@linkplain
		 * MacroDefinitionDescriptor macro definition}.  A {@linkplain
		 * MethodDescriptor method} may not contain multiple macro
		 * definition sites, nor may it mix macro definition sites and
		 * any other type of sites.
		 */
		MACRO_DEFINITION(DEFINITION, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * {@linkplain ModuleDescriptor Modules} are maintained mostly
		 * automatically by Avail's runtime environment.  Modules are not
		 * currently visible to the Avail programmer, but there may still be a
		 * need for modules to be placed in sets and maps maintained by the
		 * runtime, so the type story has to at least be consistent.
		 */
		MODULE(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

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
		RAW_POJO(NONTYPE, TypeTag.NONTYPE_TYPE_TAG);

		/** The total count of {@link Types} enum values. */
		public static final int enumCount = 22;

		static
		{
			assert Types.values().length == enumCount;
		}

		/**
		 * The {@link Types} object representing this type's supertype.
		 */
		public final @Nullable Types parent;

		/** The {@link TypeTag} for this primitive type. */
		final TypeTag typeTag;

		/**
		 * Answer the parent {@code Types} object.  Fail if this is the top
		 * type.
		 *
		 * @return The parent of this Types object.
		 */
		public final Types parent ()
		{
			return stripNull(parent);
		}

		/**
		 * The {@link AvailObject} itself that this represents.
		 */
		private final AvailObject o;

		/**
		 * A boolean array where the entries correspond to ordinals of other
		 * Types. They are true precisely when the type with that ordinal is a
		 * supertype of the current type.
		 */
		public final boolean[] superTests = new boolean[enumCount];

		/**
		 * An array of {@code A_Type}s, where the entries correspond to ordinals
		 * of other Types, and hold the unions of that type and the current
		 * type.
		 */
		public final A_Type[] unionTypes = new A_Type[enumCount];

		/**
		 * An array of {@code A_Type}s, where the entries correspond to ordinals
		 * of other Types, and hold the intersection of that type and the
		 * current type.
		 */
		public final A_Type[] intersectionTypes = new A_Type[enumCount];

		/**
		 * Construct the new top {@code Types} instance.
		 *
		 * @param typeTag
		 *        The {@link TypeTag} for this primitive type's descriptor.
		 */
		Types (final TypeTag typeTag)
		{
			this.parent = null;
			this.typeTag = typeTag;
			this.o = createMutablePrimitiveObjectNamed("⊤");
		}

		/**
		 * Construct a new {@code Types} instance with the specified
		 * parent.  Use {@link PrimitiveTypeDescriptor} for the new type's
		 * descriptor.
		 *
		 * @param parent
		 *        The new type's parent, or {@code null} if the type has no
		 *        parent.
		 */
		Types (
			final @Nullable Types parent,
			final TypeTag typeTag)
		{
			this.parent = parent;
			this.typeTag = typeTag;
			this.o = createMutablePrimitiveObjectNamed(
				name().toLowerCase().replace('_', ' '));
		}

		/**
		 * Answer the {@link AvailObject} representing this Avail type.
		 *
		 * @return The actual {@linkplain TypeDescriptor type}, an AvailObject.
		 */
		public AvailObject o ()
		{
			return o;
		}

		/**
		 * Stash a copy of the array of all {@link Types} enum values.
		 */
		private static final Types[] all = values();

		/**
		 * Answer the previously stashed copy of the array of all {@code Types}
		 * enum values.
		 *
		 * @return The array of {@code Types} values.  Do not modify the array.
		 */
		public static Types[] all ()
		{
			return all;
		}

		static
		{
			// Build all the objects with null fields.
			assert all.length == enumCount;
			// Connect the objects.
			for (final Types spec : all)
			{
				final A_Type o = spec.o();
				final PrimitiveTypeDescriptor descriptor =
					spec == TOP
						? new TopTypeDescriptor(spec.typeTag, spec)
						: new PrimitiveTypeDescriptor(spec.typeTag, spec);
				descriptor.finishInitializingPrimitiveTypeWithParent(
					(AvailObject) o,
					spec.parent == null
						? nil
						: spec.parent.o());
				@Nullable Types pointer = spec;
				while (pointer != null)
				{
					final int ancestorOrdinal = pointer.ordinal();
					spec.superTests[ancestorOrdinal] = true;
					pointer = pointer.parent;
				}
			}
			// Precompute all type unions and type intersections.
			for (final Types a : all)
			{
				for (final Types b : all)
				{
					// First, compute the union.  Move both pointers up the tree
					// repeatedly until one is a supertype of the other.  Use
					// that supertype as the union.
					final int bOrdinal = b.ordinal();
					Types aAncestor = a;
					Types bAncestor = b;
					final Types union;
					while (true)
					{
						final int bAncestorOrdinal = bAncestor.ordinal();
						if (a.superTests[bAncestorOrdinal])
						{
							union = bAncestor;
							break;
						}
						if (b.superTests[aAncestor.ordinal()])
						{
							union = aAncestor;
							break;
						}
						aAncestor = aAncestor.parent();
						bAncestor = bAncestor.parent();
						// Neither pointer can be null, because if one were at
						// "top", it would have already been detected as a
						// supertype of the other.
					}
					a.unionTypes[bOrdinal] = union.o();
					assert a.superTests[union.ordinal()];
					assert b.superTests[union.ordinal()];
					// Now compute the type intersection.  Note that since the
					// types form a tree, any two types related by sub/super
					// typing have an intersection that's the subtype, and all
					// other type pairs have bottom as their intersection.
					a.intersectionTypes[bOrdinal] =
						a.superTests[bOrdinal] ? a.o() :
							b.superTests[a.ordinal()] ? b.o() :
								bottom();
				}
			}
			// Now make all the objects shared.
			for (final Types spec : all)
			{
				spec.o().makeShared();
			}
			// Sanity check them for metacovariance: a<=b -> a.type<=b.type
			for (final Types spec : all)
			{
				if (spec.parent != null)
				{
					assert spec.o().isSubtypeOf(spec.parent.o());
					assert spec.o().isInstanceOfKind(spec.parent.o().kind());
				}
			}
		}
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
	A_Type o_ArgsTupleType (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_ContentType (
		final AvailObject object)
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
	A_Set o_DeclaredExceptions (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_DefaultType (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	abstract boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another);

	@Override @AvailMethod
	A_Type o_FunctionType (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Map o_FieldTypeMap (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return false;
	}

	@Override @AvailMethod
	A_Number o_InstanceCount (final AvailObject object)
	{
		return positiveInfinity();
	}

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final A_BasicObject anotherObject)
	{
		return true;
	}

	@Override @AvailMethod
	int o_RepresentationCostOfTupleType (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	boolean o_IsBottom (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		return object.kind().isSubtypeOf(aType);
	}

	@Override @AvailMethod
	boolean o_IsIntegerRangeType (
		final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsMapType (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSetType (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		// By default, nothing is a supertype of a variable type unless it
		// states otherwise.
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		// By default, nothing is a supertype of an integer range type unless
		// it states otherwise.
		return false;
	}

	@Override
	boolean o_IsSupertypeOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		// By default, nothing is a supertype of a list node type unless it
		// states otherwise.
		return false;
	}

	@Override
	boolean o_IsSupertypeOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		// By default, nothing is a supertype of a literal token type unless it
		// states otherwise.
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		// By default, nothing is a supertype of an eager object type unless it
		// states otherwise.
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
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
		final AvailObject object,
		final AvailObject aSetType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfEnumerationType (
		final AvailObject object,
		final A_BasicObject anEnumerationType)
	{
		return false;
	}

	@Override
	boolean o_IsSupertypeOfPojoBottomType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return false;
	}

	@Override
	boolean o_IsTop (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsTupleType (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	A_Type o_KeyType (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Number o_LowerBound (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_LowerInclusive (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	@Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		// Most Avail types are opaque to Java, and can be characterized by the
		// class of AvailObject.
		return AvailObject.class;
	}

	@Override @AvailMethod
	A_BasicObject o_Parent (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	boolean o_RangeIncludesInt (
		final AvailObject object,
		final int anInt)
	{
		return false;
	}

	@Override @AvailMethod
	A_Type o_ReturnType (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_SizeRange (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_TypeAtIndex (
		final AvailObject object,
		final int index)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return NONTYPE.superTests[primitiveTypeEnum.ordinal()]
			? object
			: bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return bottom();
	}

	@Override @AvailMethod
	A_Tuple o_TypeTuple (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return object.typeUnion(NONTYPE.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return object.typeUnion(NONTYPE.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		return object.typeUnion(NONTYPE.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return object.typeUnion(NONTYPE.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return object.typeUnion(NONTYPE.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return object.typeUnion(NUMBER.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return object.typeUnion(NONTYPE.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return object.typeUnion(TOKEN.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		return object.typeUnion(NONTYPE.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return object.typeUnion(NONTYPE.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		return object.typeUnion(NONTYPE.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return object.typeUnion(NONTYPE.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		Types anotherAncestor = primitiveTypeEnum;
		while (true)
		{
			if (object.isSubtypeOf(anotherAncestor.o()))
			{
				return anotherAncestor.o();
			}
			anotherAncestor = stripNull(anotherAncestor.parent);
		}
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		return object.typeUnion(NONTYPE.o());
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return object.typeUnion(NONTYPE.o());
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
	A_Number o_UpperBound (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	boolean o_UpperInclusive (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_ValueType (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	/**
	 * Construct a new {@code TypeDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param typeTag
	 *            The {@link TypeTag} to embed in the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected TypeDescriptor (
		final Mutability mutability,
		final TypeTag typeTag,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
	}
}
