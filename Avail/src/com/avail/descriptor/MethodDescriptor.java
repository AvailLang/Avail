/**
 * MethodDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import static com.avail.descriptor.MethodDescriptor.IntegerSlots.*;
import static com.avail.descriptor.MethodDescriptor.ObjectSlots.*;
import static java.lang.Math.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.exceptions.*;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.primitive.*;
import com.avail.serialization.SerializerOperation;

/**
 * A method maintains all definitions that have the same name.  At
 * compile time a name is looked up and the corresponding method is
 * stored as a literal in the object code.  At runtime the actual function is
 * located within the method and then invoked.  The methods also keep track of
 * bidirectional dependencies, so that a change of membership causes an
 * immediate invalidation of optimized level two code that depends on the
 * previous membership.
 *
 * <p>To support macros safely, a method must contain either all
 * {@linkplain MacroDefinitionDescriptor macro signatures} or all non-macro
 * {@linkplain DefinitionDescriptor signatures}, but not both.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class MethodDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The hash value of this {@linkplain AtomDescriptor atom}.  It is a
		 * random number (but not 0), computed at construction time.
		 */
		@HideFieldInDebugger
		HASH,

		/**
		 * The number of arguments expected by this method.  Set at construction
		 * time.
		 */
		NUM_ARGS
	}

	/**
	 * The fields that are of type {@code AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain
		 * MessageBundleDescriptor message bundles} that name this method.  The
		 * method itself has no intrinsic name, as its bundles completely
		 * determine what it is called in various modules (based on the module
		 * scope of the bundles' {@linkplain AtomDescriptor atomic names}).
		 */
		OWNING_BUNDLES,

		/**
		 * The {@linkplain TupleDescriptor tuple} of {@linkplain
		 * DefinitionDescriptor definitions} that constitute this multimethod
		 * (or multimacro).
		 */
		DEFINITIONS_TUPLE,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} holding a {@link
		 * LookupTree} used to determine the most specific method definition
		 * that satisfies supplied argument types.  A {@linkplain
		 * NilDescriptor#nil() nil} indicates the tree has not yet been
		 * constructed.
		 */
		PRIVATE_TESTING_TREE,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain
		 * SemanticRestrictionDescriptor semantic restrictions} which, when
		 * their functions are invoked with suitable {@linkplain TypeDescriptor
		 * types} as arguments, will determine whether the call arguments have
		 * mutually compatible types, and if so produce a type to which the
		 * call's return value is expected to conform.  This type strengthening
		 * is <em>assumed</em> to hold at compile time (of the call) and
		 * <em>checked</em> at runtime.
		 */
		SEMANTIC_RESTRICTIONS_SET,

		/**
		 * A {@linkplain TupleDescriptor tuple} of {@linkplain
		 * TupleTypeDescriptor tuple types} below which new signatures may no
		 * longer be added.
		 */
		SEALED_ARGUMENTS_TYPES_TUPLE,

		/**
		 * The {@linkplain SetDescriptor set} of {@linkplain L2Chunk#index()
		 * indices} of {@linkplain L2Chunk level two chunks} that depend on the
		 * membership of this {@linkplain MethodDescriptor method}.  A change to
		 * the membership should cause these chunks to be invalidated.
		 */
		DEPENDENT_CHUNK_INDICES
	}

	/**
	 * {@code LookupTree} is used to look up method definitions during calls,
	 * based on the passed arguments or argument types.
	 */
	public abstract static class LookupTree
	{
		/**
		 * Lookup the most-specific definitions that match the provided
		 * arguments.  An empty list indicates there were no applicable
		 * definitions.  More than one entry indicates an ambiguous lookup, due
		 * to multiple incomparable, locally most-specific definitions.
		 *
		 * @param argValues The arguments of the call.
		 * @return The list of most applicable definitions.
		 */
		abstract List<A_Definition> lookupByValues (
			List<? extends A_BasicObject> argValues);

		/**
		 * Lookup the most-specific definitions that satisfy the provided
		 * argument types.  An empty list indicates there were no applicable
		 * definitions.  More than one entry indicates an ambiguous lookup, due
		 * to multiple incomparable, locally most-specific definitions.
		 *
		 * @param argTypes The types of arguments of the call.
		 * @return The list of most applicable definitions.
		 */
		abstract List<A_Definition> lookupByTypes (
			List<? extends A_Type> argTypes);

		/**
		 * Create a LookupTree suitable for deciding which definition to use
		 * when actual argument types are provided.
		 *
		 * @param positive
		 *            Definitions which definitely apply at this node.
		 * @param undecided
		 *            Definitions which are not known to apply or not apply at
		 *            this node.
		 * @return A (potentially lazy) LookupTree used to look up method
		 *         definitions during calls.
		 */
		static LookupTree createTree (
			final List<A_Definition> positive,
			final List<A_Definition> undecided)
		{
			if (undecided.size() == 0)
			{
				// Find the most specific applicable definitions.
				if (positive.size() <= 1)
				{
					return new LeafLookupTree(positive);
				}
				final int size = positive.size();
				final List<A_Definition> mostSpecific =
					new ArrayList<A_Definition>(1);
				outer: for (int outer = 0; outer < size; outer++)
				{
					final A_Type outerType =
						positive.get(outer).bodySignature();
					for (int inner = 0 ; inner < size; inner++)
					{
						if (outer != inner)
						{
							final A_Type innerType =
								positive.get(inner).bodySignature();
							if (outerType.acceptsArgTypesFromFunctionType(
								innerType))
							{
								// A more specific definition was found
								// (i.e., inner was more specific than outer).
								// This disqualifies outer from being considered
								// most specific.
								continue outer;
							}
						}
					}
					mostSpecific.add(positive.get(outer));
				}
				return new LeafLookupTree(mostSpecific);
			}
			return new InternalLookupTree(positive, undecided);
		}
	}

	/**
	 * A {@code LookupTree} representing a solution.
	 */
	public final static class LeafLookupTree extends LookupTree
	{
		/** The result of the lookup, zero or more most-specific definitions. */
		private final List<A_Definition> finalResult;

		/**
		 * Construct a new {@link MethodDescriptor.LeafLookupTree}.
		 *
		 * @param finalResult
		 *            The most specific definitions for the provided arguments.
		 *            Thus, if this is empty, there are no applicable
		 *            definitions, and if there's more than one element the
		 *            actual call is ambiguous.
		 */
		public LeafLookupTree (final List<A_Definition> finalResult)
		{
			this.finalResult = finalResult;
		}

		@Override
		final List<A_Definition> lookupByValues (
			final List<? extends A_BasicObject> argValues)
		{
			return finalResult;
		}

		@Override
		final List<A_Definition> lookupByTypes (
			final List<? extends A_Type> argTypes)
		{
			return finalResult;
		}
	}

	/**
	 * A {@code LookupTree} representing an incomplete search.  To further the
	 * search, the indicated {@linkplain #argumentTypesToTest type test} will be
	 * made.  If successful, the {@link #ifCheckHolds} child will be visited,
	 * otherwise the {@link #ifCheckFails} child will be visited.
	 */
	public static class InternalLookupTree extends LookupTree
	{
		/** The definitions that are applicable at this tree node. */
		private final List<A_Definition> positiveDefinitions;

		/**
		 * The definitions whose applicability has not yet been decided at this
		 * tree node.
		 */
		private final List<A_Definition> undecidedDefinitions;

		/** The types to test against corresponding supplied arguments. */
		private @Nullable List<A_Type> argumentTypesToTest;

		/** The tree to visit if the supplied arguments conform. */
		private @Nullable LookupTree ifCheckHolds;

		/** The tree to visit if the supplied arguments do not conform. */
		private @Nullable LookupTree ifCheckFails;

		/**
		 * Construct a new {@link InternalLookupTree}.  It is constructed lazily
		 * at first.  An attempt to lookup that reaches this node will cause
		 * it to be expanded locally.
		 *
		 * @param positiveDefinitions
		 *            The definitions which definitely apply to the supplied
		 *            arguments at this point in the decision tree.
		 * @param undecidedDefinitions
		 *            The definitions for which a decision about whether they
		 *            apply to the supplied arguments has not yet been made at
		 *            this point in the decision tree.
		 */
		public InternalLookupTree (
			final List<A_Definition> positiveDefinitions,
			final List<A_Definition> undecidedDefinitions)
		{
			this.positiveDefinitions = positiveDefinitions;
			this.undecidedDefinitions = undecidedDefinitions;
		}

		/**
		 * Return the list of argument types to test against the supplied
		 * arguments.  Expand the node if the list is null.
		 *
		 * @return A list of argument types to check, expanding this node if
		 *         necessary.
		 */
		final private List<A_Type> argumentTypesToTest ()
		{
			List<A_Type> test = argumentTypesToTest;
			if (test == null)
			{
				chooseCriterion();
				test = argumentTypesToTest;
			}
			return test;
		}

		final LookupTree ifCheckHolds ()
		{
			final LookupTree subtree = ifCheckHolds;
			assert subtree != null;
			return subtree;
		}

		final LookupTree ifCheckFails ()
		{
			final LookupTree subtree = ifCheckFails;
			assert subtree != null;
			return subtree;
		}

		final private void chooseCriterion ()
		{
			// Choose a signature to test that guarantees it eliminates the most
			// undecided definitions, regardless of whether the test passes or
			// fails.  If the larger of the two cases (success or failure of the
			// test) is a tie between two criteria, break it by choosing the
			// criterion that eliminates the most undecided definitions in the
			// *best* case.
			A_Type bestSignature = null;
			int smallestMax = Integer.MAX_VALUE;
			int smallestMin = Integer.MAX_VALUE;
			for (final A_Definition criterion : undecidedDefinitions)
			{
				final A_Type criterionSignature = criterion.bodySignature();
				int undecidedCountIfTrue = 0;
				int undecidedCountIfFalse = 0;
				for (final A_Definition each : undecidedDefinitions)
				{
					switch (TypeComparison.compare(
						criterionSignature,
						each.bodySignature()))
					{
						case DISJOINT_TYPE:
						case PROPER_ANCESTOR_TYPE:
							undecidedCountIfFalse++;
							break;
						case PROPER_DESCENDANT_TYPE:
							undecidedCountIfTrue++;
							break;
						case UNRELATED_TYPE:
							undecidedCountIfTrue++;
							undecidedCountIfFalse++;
							break;
						case SAME_TYPE:
							break;
					}
				}
				final int maxCount =
					max(undecidedCountIfTrue, undecidedCountIfFalse);
				final int minCount =
					min(undecidedCountIfTrue, undecidedCountIfFalse);
				assert maxCount < undecidedDefinitions.size();
				if (maxCount < smallestMax ||
					(maxCount == smallestMax && minCount < smallestMin))
				{
					smallestMax = maxCount;
					smallestMin = minCount;
					bestSignature = criterionSignature;
				}
			}
			assert bestSignature != null;
			final A_Type bestArgsTupleType = bestSignature.argsTupleType();
			final int numArgs =
				bestArgsTupleType.sizeRange().lowerBound().extractInt();
			final List<A_Type> typesToTest = new ArrayList<A_Type>(numArgs);
			for (int argIndex = 0; argIndex < numArgs; argIndex++)
			{
				typesToTest.add(bestArgsTupleType.typeAtIndex(argIndex + 1));
			}
			argumentTypesToTest = typesToTest;
			// Compute the positive/undecided lists, both for the condition
			// being true and for the condition being false.
			final List<A_Definition> positiveIfTrue =
				new ArrayList<>(positiveDefinitions);
			final List<A_Definition> undecidedIfTrue = new ArrayList<>();
			final List<A_Definition> positiveIfFalse =
				new ArrayList<>(positiveDefinitions);
			final List<A_Definition> undecidedIfFalse = new ArrayList<>();
			for (final A_Definition undecidedDefinition : undecidedDefinitions)
			{
				final TypeComparison comparison = TypeComparison.compare(
					bestSignature,
					undecidedDefinition.bodySignature());
				comparison.applyEffect(
					true,
					undecidedDefinition,
					positiveIfTrue,
					undecidedIfTrue);
				comparison.applyEffect(
					false,
					undecidedDefinition,
					positiveIfFalse,
					undecidedIfFalse);
			}
			ifCheckHolds = LookupTree.createTree(
				positiveIfTrue,
				undecidedIfTrue);
			ifCheckFails = LookupTree.createTree(
				positiveIfFalse,
				undecidedIfFalse);
		}

		/**
		 * Answer how two signatures (function types) are related.
		 */
		enum TypeComparison
		{
			SAME_TYPE,
			PROPER_ANCESTOR_TYPE,
			PROPER_DESCENDANT_TYPE,
			UNRELATED_TYPE,
			DISJOINT_TYPE;

			public void applyEffect (
				final boolean comparisonValue,
				final A_Definition undecidedDefinition,
				final List<A_Definition> positiveDefinitions,
				final List<A_Definition> undecidedDefinitions)
			{
				if (comparisonValue)
				{
					switch (this)
					{
						case SAME_TYPE:
						case PROPER_ANCESTOR_TYPE:
							positiveDefinitions.add(undecidedDefinition);
							break;
						case PROPER_DESCENDANT_TYPE:
						case UNRELATED_TYPE:
							undecidedDefinitions.add(undecidedDefinition);
							break;
						case DISJOINT_TYPE:
							break;
					}
				}
				else
				{
					switch (this)
					{
						case PROPER_ANCESTOR_TYPE:
						case UNRELATED_TYPE:
						case DISJOINT_TYPE:
							undecidedDefinitions.add(undecidedDefinition);
							break;
						case SAME_TYPE:
						case PROPER_DESCENDANT_TYPE:
							break;
					}
				}
			}

			public static TypeComparison compare (
				final A_Type criterion,
				final A_Type someType)
			{
				final A_Type intersection =
					criterion.argsTupleType().typeIntersection(
						someType.argsTupleType());
				if (intersection == BottomTypeDescriptor.bottom())
				{
					return DISJOINT_TYPE;
				}
				final boolean below =
					criterion.acceptsArgTypesFromFunctionType(someType);
				final boolean above =
					someType.acceptsArgTypesFromFunctionType(criterion);
				return
					below
						? above ? SAME_TYPE : PROPER_DESCENDANT_TYPE
						: above ? PROPER_ANCESTOR_TYPE : UNRELATED_TYPE;
			}
		}

		@Override
		List<A_Definition> lookupByValues (
			final List<? extends A_BasicObject> argValues)
		{
			final List<A_Type> testTypes = argumentTypesToTest();
			final int numArgs = argValues.size();
			assert numArgs == testTypes.size();
			for (int i = 0; i < numArgs; i++)
			{
				if (!argValues.get(i).isInstanceOf(testTypes.get(i)))
				{
					return ifCheckFails().lookupByValues(argValues);
				}
			}
			return ifCheckHolds().lookupByValues(argValues);
		}

		@Override
		List<A_Definition> lookupByTypes (
			final List<? extends A_Type> argTypes)
		{
			final List<A_Type> testTypes = argumentTypesToTest();
			final int numArgs = argTypes.size();
			assert numArgs == testTypes.size();
			for (int i = 0; i < numArgs; i++)
			{
				if (!argTypes.get(i).isSubtypeOf(testTypes.get(i)))
				{
					return ifCheckFails().lookupByTypes(argTypes);
				}
			}
			return ifCheckHolds().lookupByTypes(argTypes);
		}
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == OWNING_BUNDLES
			|| e == DEFINITIONS_TUPLE
			|| e == PRIVATE_TESTING_TREE
			|| e == DEPENDENT_CHUNK_INDICES
			|| e == SEMANTIC_RESTRICTIONS_SET
			|| e == SEALED_ARGUMENTS_TYPES_TUPLE;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		final int size = object.definitionsTuple().tupleSize();
		aStream.append(Integer.toString(size));
		aStream.append(" definition");
		if (size != 1)
		{
			aStream.append('s');
		}
		aStream.append(" of ");
		boolean first = true;
		for (final A_Bundle eachBundle : object.bundles())
		{
			if (!first)
			{
				aStream.append(" a.k.a. ");
			}
			aStream.append(eachBundle.message()/*.name()*/.toString());
			first = false;
		}
	}

	@Override @AvailMethod
	A_Tuple o_DefinitionsTuple (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(DEFINITIONS_TUPLE);
		}
	}

	@Override @AvailMethod
	A_Set o_SemanticRestrictions (final AvailObject object)
	{
		return object.slot(SEMANTIC_RESTRICTIONS_SET);
	}

	@Override @AvailMethod
	A_Tuple o_SealedArgumentsTypesTuple (final AvailObject object)
	{
		return object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
	}

	@Override @AvailMethod
	void o_MethodAddBundle (final AvailObject object, final A_Bundle bundle)
	{
		A_Set bundles = object.slot(OWNING_BUNDLES);
		bundles = bundles.setWithElementCanDestroy(bundle, false);
		bundles.makeShared();
		object.setSlot(OWNING_BUNDLES, bundles);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(HASH);
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// A method is always shared. Never make it immutable.
			return object.makeShared();
		}
		return object;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return METHOD.o();
	}

	@Override @AvailMethod
	void o_AddDependentChunkIndex (
		final AvailObject object,
		final int aChunkIndex)
	{
		synchronized (object)
		{
			// Record the fact that the chunk indexed by aChunkIndex depends on
			// this object not changing.
			A_Set indices = object.slot(DEPENDENT_CHUNK_INDICES);
			indices = indices.setWithElementCanDestroy(
				IntegerDescriptor.fromInt(aChunkIndex),
				true);
			object.setSlot(
				DEPENDENT_CHUNK_INDICES,
				indices.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	void o_MethodAddDefinition (
		final AvailObject object,
		final A_Definition definition)
	throws SignatureException
	{
		synchronized (object)
		{
			final A_Tuple oldTuple = object.definitionsTuple();
			if (oldTuple.tupleSize() > 0)
			{
				// Ensure that we're not mixing macro and non-macro signatures.
				if (definition.isMacroDefinition()
					!= oldTuple.tupleAt(1).isMacroDefinition())
				{
					throw new SignatureException(
						AvailErrorCode
							.E_CANNOT_MIX_METHOD_AND_MACRO_DEFINITIONS);
				}
			}
			final A_Tuple seals = object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
			final A_Type bodySignature = definition.bodySignature();
			final A_Type paramTypes = bodySignature.argsTupleType();
			for (final A_Tuple seal : seals)
			{
				final A_Type sealType =
					TupleTypeDescriptor.forTypes(TupleDescriptor.toArray(seal));
				if (paramTypes.isSubtypeOf(sealType))
				{
					throw new SignatureException(
						AvailErrorCode.E_METHOD_IS_SEALED);
				}
			}
			final A_Tuple newTuple =
				oldTuple.appendCanDestroy(definition, true);
			object.setSlot(DEFINITIONS_TUPLE, newTuple.makeShared());
			membershipChanged(object);
		}
	}

	/**
	 * Look up all method definitions that could match the given argument
	 * types. Answer a {@linkplain List list} of {@linkplain
	 * MethodDefinitionDescriptor method signatures}.
	 */
	@Override @AvailMethod
	List<A_Definition> o_FilterByTypes (
		final AvailObject object,
		final List<A_Type> argTypes)
	{
		final List<A_Definition> result = new ArrayList<>(3);
		// Use the accessor instead of reading the slot directly (to acquire the
		// monitor first).
		final A_Tuple impsTuple = object.definitionsTuple();
		for (int i = 1, end = impsTuple.tupleSize(); i <= end; i++)
		{
			final AvailObject imp = impsTuple.tupleAt(i);
			if (imp.bodySignature().acceptsListOfArgTypes(argTypes))
			{
				result.add(imp);
			}
		}
		return result;
	}

	/**
	 * Look up all method definitions that could match arguments with the
	 * given types, or anything more specific.  This should return the
	 * definitions that could be invoked at runtime at a call site with the
	 * given static types.  This set is subject to change as new methods and
	 * types are created.  If an argType and the corresponding argument type of
	 * a definition have no possible descendant except bottom, then
	 * disallow the definition (it could never actually be invoked because
	 * bottom is uninstantiable).  Answer a {@linkplain List list} of
	 * {@linkplain MethodDefinitionDescriptor method signatures}.
	 *
	 * <p>
	 * Don't do coverage analysis yet (i.e., determining if one method would
	 * always override a strictly more abstract method).  We can do that some
	 * other day.
	 * </p>
	 */
	@Override @AvailMethod
	List<A_Definition> o_DefinitionsAtOrBelow (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		final List<A_Definition> result = new ArrayList<>(3);
		// Use the accessor instead of reading the slot directly (to acquire the
		// monitor first).
		final A_Tuple definitionsTuple = object.definitionsTuple();
		for (final A_Definition definition : definitionsTuple)
		{
			if (definition.bodySignature().couldEverBeInvokedWith(argTypes))
			{
				result.add(definition);
			}
		}
		return result;
	}

	/**
	 * Test if the definition is present within this method.
	 */
	@Override @AvailMethod
	boolean o_IncludesDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		// Use the accessor instead of reading the slot directly (to acquire the
		// monitor first).
		for (final A_BasicObject eachDefinition : object.definitionsTuple())
		{
			if (eachDefinition.equals(definition))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Look up the definition to invoke, given a tuple of argument types.
	 * Use the testingTree to find the definition to invoke (answer nil if
	 * a lookup error occurs).
	 */
	@Override @AvailMethod
	A_Definition o_LookupByTypesFromTuple (
			final AvailObject object,
			final A_Tuple argumentTypeTuple)
		throws MethodDefinitionException
	{
		final List<A_Type> argumentTypesList =
			TupleDescriptor.toList(argumentTypeTuple);
		synchronized (object)
		{
			final LookupTree tree =
				(LookupTree) (object.testingTree().javaObject());
			final List<A_Definition> solutions =
				tree.lookupByTypes(argumentTypesList);
			if (solutions.size() != 1)
			{
				throw solutions.size() < 1
					? MethodDefinitionException.noMethodDefinition()
					: MethodDefinitionException.ambiguousMethodDefinition();
			}
			return solutions.get(0);
		}
	}

	/**
	 * Look up the definition to invoke, given an array of argument values.
	 * Use the testingTree to find the definition to invoke (answer void if
	 * a lookup error occurs).
	 */
	@Override @AvailMethod
	A_Definition o_LookupByValuesFromList (
		final AvailObject object,
		final List<? extends A_BasicObject> argumentList)
	{
		synchronized (object)
		{
			final LookupTree tree =
				(LookupTree) (object.testingTree().javaObject());
			final List<A_Definition> solutions =
				tree.lookupByValues(argumentList);
			return solutions.size() == 1
				? solutions.get(0)
				: NilDescriptor.nil();
		}
	}

	/**
	 * Remove the chunk from my set of dependent chunks. This is probably
	 * because the chunk has been (A) removed by the garbage collector, or (B)
	 * invalidated by a new definition in either me or another method that the
	 * chunk is contingent on.
	 */
	@Override @AvailMethod
	void o_RemoveDependentChunkIndex (
		final AvailObject object,
		final int aChunkIndex)
	{
		synchronized (object)
		{
			A_Set indices =
				object.slot(DEPENDENT_CHUNK_INDICES);
			indices = indices.setWithoutElementCanDestroy(
				IntegerDescriptor.fromInt(aChunkIndex),
				true);
			object.setSlot(
				DEPENDENT_CHUNK_INDICES, indices.traversed().makeShared());
		}
	}

	/**
	 * Remove the definition from me. Causes dependent chunks to be
	 * invalidated.
	 */
	@Override @AvailMethod
	void o_RemoveDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		assert !definition.definitionModule().equalsNil();
		synchronized (object)
		{
			A_Tuple definitionsTuple = object.slot(DEFINITIONS_TUPLE);
			definitionsTuple = TupleDescriptor.without(
				definitionsTuple,
				definition);
			object.setSlot(
				DEFINITIONS_TUPLE,
				definitionsTuple.traversed().makeShared());
			membershipChanged(object);
		}
	}

	@Override @AvailMethod
	int o_NumArgs (final AvailObject object)
	{
		return object.slot(NUM_ARGS);
	}

	/**
	 * Answer the cached privateTestingTree. If there's a nil in that slot,
	 * compute and cache the testing tree based on the definitionsTuple.
	 * The tree should be a {@linkplain RawPojoDescriptor raw pojo} holding a
	 * {@link LookupTree}.
	 */
	@Override @AvailMethod
	A_BasicObject o_TestingTree (final AvailObject object)
	{
		synchronized (object)
		{
			A_Tuple result = object.slot(PRIVATE_TESTING_TREE);
			if (result.equalsNil())
			{
				// Compute the tree.
				final LookupTree tree = LookupTree.createTree(
					Collections.<A_Definition>emptyList(),
					TupleDescriptor.<A_Definition>toList(
						object.slot(DEFINITIONS_TUPLE)));
				result = RawPojoDescriptor.identityWrap(tree).makeShared();
				object.setSlot(PRIVATE_TESTING_TREE, result);
			}
			return result;
		}
	}

	@Override @AvailMethod
	void o_AddSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction restriction)
	{
		synchronized (object)
		{
			A_Set set = object.slot(SEMANTIC_RESTRICTIONS_SET);
			set = set.setWithElementCanDestroy(restriction, true);
			object.setSlot(SEMANTIC_RESTRICTIONS_SET, set.makeShared());
		}
	}

	@Override @AvailMethod
	void o_RemoveSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction restriction)
	{
		synchronized (object)
		{
			A_Set set = object.slot(SEMANTIC_RESTRICTIONS_SET);
			set = set.setWithoutElementCanDestroy(restriction, true);
			object.setSlot(SEMANTIC_RESTRICTIONS_SET, set.makeShared());
		}
	}

	@Override @AvailMethod
	void o_AddSealedArgumentsType (
		final AvailObject object,
		final A_Tuple typeTuple)
	{
		synchronized (object)
		{
			assert typeTuple.isTuple();
			final A_Tuple oldTuple = object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
			final A_Tuple newTuple = oldTuple.appendCanDestroy(typeTuple, true);
			object.setSlot(
				SEALED_ARGUMENTS_TYPES_TUPLE,
				newTuple.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	void o_RemoveSealedArgumentsType (
		final AvailObject object,
		final A_Tuple typeTuple)
	{
		synchronized (object)
		{
			final A_Tuple oldTuple = object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
			final A_Tuple newTuple =
				TupleDescriptor.without(oldTuple, typeTuple);
			assert newTuple.tupleSize() == oldTuple.tupleSize() - 1;
			object.setSlot(
				SEALED_ARGUMENTS_TYPES_TUPLE,
				newTuple.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	boolean o_IsMethodEmpty (final AvailObject object)
	{
		synchronized (object)
		{
			final A_Tuple definitionsTuple = object.slot(DEFINITIONS_TUPLE);
			if (definitionsTuple.tupleSize() > 0)
			{
				return false;
			}
			final A_Set semanticRestrictions =
				object.slot(SEMANTIC_RESTRICTIONS_SET);
			if (semanticRestrictions.setSize() > 0)
			{
				return false;
			}
			final A_Tuple sealedArgumentsTypesTuple =
				object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
			if (sealedArgumentsTypesTuple.tupleSize() > 0)
			{
				return false;
			}
			return true;
		}
	}

	@Override
	A_Set o_Bundles (final AvailObject object)
	{
		return object.slot(OWNING_BUNDLES);
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.METHOD;
	}

	/**
	 * Answer a new {@linkplain MethodDescriptor method}. It has no name yet,
	 * but will before it gets used in a send node.  It gets named by virtue of
	 * it being referenced by one or more {@linkplain MessageBundleDescriptor
	 * message bundle}s, each of which keeps track of how to parse it using that
	 * bundle's name.  The bundles will be grouped into a bundle tree to allow
	 * parsing of many possible message sends in aggregate.
	 *
	 * <p>A method is always {@linkplain Mutability#SHARED shared}, but its
	 * tuple of owning bundles, its tuple of definitions, its cached
	 * privateTestingTree, and its set of dependents chunk indices can all be
	 * updated (while holding a lock).</p>
	 *
	 * @param numArgs The number of arguments that this method expects.
	 * @return A new method with no name.
	 */
	public static AvailObject newMethod (final int numArgs)
	{
		final AvailObject result = mutable.create();
		result.setSlot(HASH, AvailRuntime.nextHash());
		result.setSlot(NUM_ARGS, numArgs);
		result.setSlot(OWNING_BUNDLES, SetDescriptor.empty());
		result.setSlot(DEFINITIONS_TUPLE, TupleDescriptor.empty());
		result.setSlot(PRIVATE_TESTING_TREE, NilDescriptor.nil());
		result.setSlot(SEMANTIC_RESTRICTIONS_SET, SetDescriptor.empty());
		result.setSlot(SEALED_ARGUMENTS_TYPES_TUPLE, TupleDescriptor.empty());
		result.setSlot(DEPENDENT_CHUNK_INDICES, SetDescriptor.empty());
		result.makeShared();
		return result;
	}

	/**
	 * The membership of this {@linkplain MethodDescriptor method} has changed.
	 * Invalidate anything that depended on the previous membership, including
	 * the {@linkplain ObjectSlots#PRIVATE_TESTING_TREE testing tree} and any
	 * dependent level two chunks.
	 *
	 * @param object The method that changed.
	 */
	private static void membershipChanged (final AvailObject object)
	{
		assert Thread.holdsLock(object);
		// Invalidate any affected level two chunks.
		final A_Set chunkIndices =
			object.slot(DEPENDENT_CHUNK_INDICES);
		if (chunkIndices.setSize() > 0)
		{
			// Use makeImmutable() to avoid membership changes while iterating.
			for (final A_Number chunkIndex : chunkIndices.makeImmutable())
			{
				L2Chunk.invalidateChunkAtIndex(chunkIndex.extractInt());
			}
			// The chunk invalidations should have removed all dependencies...
			final A_Set chunkIndicesAfter =
				object.slot(DEPENDENT_CHUNK_INDICES);
			assert chunkIndicesAfter.setSize() == 0;
		}
		// Clear the privateTestingTree cache.
		object.setSlot(PRIVATE_TESTING_TREE, NilDescriptor.nil());
	}

	/**
	 * Construct a new {@link MethodDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MethodDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link MethodDescriptor}. */
	private static final MethodDescriptor mutable =
		new MethodDescriptor(Mutability.MUTABLE);

	@Override
	MethodDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link MethodDescriptor}. */
	private static final MethodDescriptor shared =
		new MethodDescriptor(Mutability.SHARED);

	@Override
	MethodDescriptor immutable ()
	{
		// There is no immutable descriptor. Use the shared one.
		return shared;
	}

	@Override
	MethodDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Create a new special atom with the given name.  The name is not globally
	 * unique, but serves to help to visually distinguish atoms.  Also create
	 * a {@linkplain MessageBundleDescriptor message bundle} within it, with its
	 * own {@linkplain MethodDescriptor method}.  Add {@linkplain
	 * MethodDefinitionDescriptor method definitions} implementing the specified
	 * primitives.
	 *
	 * @param name
	 *        A string used to help identify the new atom.
	 * @param primitives
	 *        The {@link Primitive}s to instantiate as method definitions in
	 *        this atom's message bundle's method.
	 * @return
	 *        The new atom, not equal to any object in use before this method
	 *        was invoked.
	 */
	public static A_Atom createSpecialMethodAtom (
		final String name,
		final Primitive... primitives)
	{
		final A_String string = StringDescriptor.from(name);
		final A_Atom atom = AtomDescriptor.createSpecialAtom(string);
		final A_Bundle bundle = atom.bundleOrCreate();
		final A_Method method = bundle.bundleMethod();
		for (final Primitive primitive : primitives)
		{
			final A_Function function =
				FunctionDescriptor.newPrimitiveFunction(primitive);
			final A_Definition definition = MethodDefinitionDescriptor.create(
				method,
				NilDescriptor.nil(),  // System definitions have no module.
				function);
			try
			{
				method.methodAddDefinition(definition);
			}
			catch (final SignatureException e)
			{
				throw new RuntimeException(
					"VM method name is invalid: " + name.toString(), e);
			}
		}
		return atom.traversed().makeShared();
	}

	/**
	 * A special {@linkplain AtomDescriptor atom} used to name the VM's method
	 * to crash during early bootstrapping problems.
	 */
	private static final A_Atom vmCrashAtom = createSpecialMethodAtom(
		"vm crash:(«_‡,»)",
		P_256_EmergencyExit.instance);

	/**
	 * Answer the {@linkplain AtomDescriptor atom} used by the VM to name the
	 * method which is invoked in the event of a bootstrapping problem.
	 *
	 * @return The atom.
	 */
	public static A_Atom vmCrashAtom ()
	{
		return vmCrashAtom;
	}

	/**
	 * The (special) name of the VM-built pre-bootstrap method-defining method.
	 */
	private static final A_Atom vmMethodDefinerAtom = createSpecialMethodAtom(
		"vm method_is_",
		P_253_SimpleMethodDeclaration.instance,
		P_228_MethodDeclarationFromAtom.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to bootstrap new methods.
	 *
	 * @return The name of the bootstrap method-defining method.
	 */
	public static A_Atom vmMethodDefinerAtom ()
	{
		return vmMethodDefinerAtom;
	}

	/**
	 * The (special) name of the VM-built pre-bootstrap macro-defining method.
	 */
	private static final A_Atom vmMacroDefinerAtom = createSpecialMethodAtom(
		"vm macro_is«_,»_",
		P_249_SimpleMacroDeclaration.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to bootstrap new macros.
	 *
	 * @return The name of the bootstrap macro-defining method.
	 */
	public static A_Atom vmMacroDefinerAtom ()
	{
		return vmMacroDefinerAtom;
	}

	/**
	 * The (special) name of the VM-built function application method.
	 */
	private static final A_Atom vmFunctionApplyAtom = createSpecialMethodAtom(
		"vm function apply_(«_‡,»)",
		P_040_InvokeWithTuple.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM's
	 * function application method.
	 *
	 * @return The name of the VM's function application method.
	 */
	public static A_Atom vmFunctionApplyAtom ()
	{
		return vmFunctionApplyAtom;
	}

	/**
	 * The (special) name of the VM-built atom-set publication method.
	 */
	private static final A_Atom vmPublishAtomsAtom = createSpecialMethodAtom(
		"vm publish atom set_(public=_)",
		P_263_DeclareAllExportedAtoms.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM's
	 * function which publishes a set of atoms from the current module.
	 *
	 * @return The name of the VM's function application method.
	 */
	public static A_Atom vmPublishAtomsAtom ()
	{
		return vmPublishAtomsAtom;
	}

	/**
	 * The (special) name of the VM-built stringifier declaration atom.
	 */
	private static final A_Atom vmDeclareStringifierAtom =
		createSpecialMethodAtom(
			"vm stringifier:=_",
			P_266_DeclareStringificationAtom.instance);

	/**
	 * Answer the (special) name of the VM-built stringifier declaration atom.
	 *
	 * @return The name of the VM's stringifier declaration atom.
	 */
	public static A_Atom vmDeclareStringifierAtom ()
	{
		return vmDeclareStringifierAtom;
	}
}
