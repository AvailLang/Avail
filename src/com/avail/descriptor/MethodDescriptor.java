/**
 * MethodDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
import static com.avail.descriptor.MethodDescriptor.IntegerSlots.*;
import static com.avail.descriptor.MethodDescriptor.ObjectSlots.*;
import static java.lang.Math.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.compiler.MessageSplitter;
import com.avail.compiler.ParsingOperation;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.primitive.atoms.P_AtomSetProperty;
import com.avail.interpreter.primitive.continuations.P_ContinuationCaller;
import com.avail.interpreter.primitive.controlflow.P_InvokeWithTuple;
import com.avail.interpreter.primitive.controlflow.P_ResumeContinuation;
import com.avail.interpreter.primitive.general.P_EmergencyExit;
import com.avail.interpreter.primitive.general.P_DeclareStringificationAtom;
import com.avail.interpreter.primitive.methods.*;
import com.avail.interpreter.primitive.modules.P_DeclareAllExportedAtoms;
import com.avail.interpreter.primitive.variables.P_GetValue;
import com.avail.optimizer.L2Translator;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.*;
import com.avail.utility.json.JSONWriter;
import com.avail.utility.*;

/**
 * A method maintains all definitions that have the same name.  At compile time
 * a name is looked up and the corresponding method is stored as a literal in
 * the object code for a call site.  At runtime the actual function is located
 * within the method and then invoked.  The methods also keep track of
 * bidirectional dependencies, so that a change of membership causes an
 * immediate invalidation of optimized level two code that depends on the
 * previous membership.
 *
 * <p>Methods and macros are stored in separate lists.  Note that macros may be
 * polymorphic (multiple {@linkplain MacroDefinitionDescriptor definitions}),
 * and a lookup structure is used at compile time to decide which macro is most
 * specific.</p>
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
		 * {@link BitField}s for the hash and the argument count.  See below.
		 */
		@HideFieldInDebugger
		HASH_AND_NUM_ARGS;

		/**
		 * The hash of this method.  It's set to a random number during
		 * construction.
		 */
		static final BitField HASH = bitField(
			HASH_AND_NUM_ARGS, 0, 32);

		/**
		 * The number of arguments expected by this method.  Set at construction
		 * time.
		 */
		static final BitField NUM_ARGS = bitField(
			HASH_AND_NUM_ARGS, 32, 32);
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
		 *
		 * TODO [MvG] - This should be a weak set, and the members should first
		 * be forced to be traversed (across indirections) and Shared.
		 */
		OWNING_BUNDLES,

		/**
		 * The {@linkplain TupleDescriptor tuple} of {@linkplain
		 * DefinitionDescriptor definitions} that constitute this multimethod.
		 */
		DEFINITIONS_TUPLE,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} holding a {@link
		 * LookupTree} used to determine the most specific method definition
		 * that satisfies the supplied argument types.  A {@linkplain
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
		 *
		 * <p>When the {@link L2Translator} inlines a {@link Primitive} method
		 * definition, it asks the primitive what type it guarantees ({@link
		 * Primitive#returnTypeGuaranteedByVM(List)}) to return for the specific
		 * provided argument types.  If that return type is sufficiently strong,
		 * the above runtime check may be waived.</p>
		 */
		SEMANTIC_RESTRICTIONS_SET,

		/**
		 * A {@linkplain TupleDescriptor tuple} of {@linkplain
		 * TupleTypeDescriptor tuple types} below which new signatures may no
		 * longer be added.
		 */
		SEALED_ARGUMENTS_TYPES_TUPLE,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} holding a weak set
		 * (implemented as the {@linkplain Map#keySet() key set} of a {@link
		 * WeakHashMap}) of {@link L2Chunk}s that depend on the membership of
		 * this method.  A change to the membership will invalidate all such
		 * chunks.  This field holds the {@linkplain NilDescriptor#nil() nil}
		 * object initially.
		 */
		DEPENDENT_CHUNKS_WEAK_SET_POJO,

		/**
		 * The {@linkplain A_Tuple tuple} of {@linkplain
		 * MacroDefinitionDescriptor macro definitions} that are defined for
		 * this macro.
		 */
		MACRO_DEFINITIONS_TUPLE,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} holding a {@link
		 * LookupTree} used to determine the most specific {@linkplain
		 * MacroDefinitionDescriptor macro definition} that satisfies the
		 * supplied argument types.  A {@linkplain NilDescriptor#nil() nil}
		 * indicates the tree has not yet been constructed.
		 */
		MACRO_TESTING_TREE,

		/**
		 * A {@linkplain A_Tuple tuple} of (unordered) tuples of {@linkplain
		 * A_Function functions} to invoke each time a {@linkplain
		 * ParsingOperation#RUN_PREFIX_FUNCTION} parsing operation is
		 * encountered, which corresponds to reaching an occurrence of a
		 * section checkpoint ("§") in the message name.
		 */
		MACRO_PREFIX_FUNCTIONS;
	}

	/**
	 * {@code LookupTree} is used to look up method definitions during calls,
	 * based on the passed arguments or argument types.
	 */
	public abstract static class LookupTree
	{
		/**
		 * Lookup the most-specific definitions that match the provided list of
		 * arguments.  An empty list indicates there were no applicable
		 * definitions.  More than one entry indicates an ambiguous lookup, due
		 * to multiple incomparable, locally most-specific definitions.
		 *
		 * @param argValues The arguments of the call.
		 * @return The list of most applicable definitions.
		 */
		abstract LookupTree lookupStepByValues (
			List<? extends A_BasicObject> argValues);

		/**
		 * Lookup the most-specific definitions that match the provided tuple of
		 * arguments.  An empty list indicates there were no applicable
		 * definitions.  More than one entry indicates an ambiguous lookup, due
		 * to multiple incomparable, locally most-specific definitions.
		 *
		 * @param argValues The arguments of the call.
		 * @return The list of most applicable definitions.
		 */
		abstract LookupTree lookupStepByValues (
			A_Tuple argValues);

		/**
		 * Answer the lookup solution ({@link List} of {@linkplain A_Definition
		 * definitions} at this leaf node, or {@code null} if this is not a leaf
		 * node.
		 *
		 * @return The solution or null.
		 */
		abstract @Nullable List<A_Definition> solutionOrNull ();

		/**
		 * Lookup the most-specific definitions that satisfy the provided
		 * argument types.  An empty list indicates there were no applicable
		 * definitions.  More than one entry indicates an ambiguous lookup, due
		 * to multiple incomparable, locally most-specific definitions.
		 *
		 * @param argTypes The types of arguments of the call.
		 * @return The list of most applicable definitions.
		 */
		abstract LookupTree lookupStepByTypes (
			List<? extends A_Type> argTypes);

		/**
		 * Create a {@link LookupTree}, using the provided list of {@link
		 * DefinitionDescriptor definitions} of a {@link MethodDescriptor
		 * method}.
		 *
		 * @param method
		 *            The method containing the provided definitions.
		 * @param allDefinitions
		 *            The list of definitions.
		 * @param knownArgumentTypes
		 *            The initial knowledge about the argument types.
		 * @return A LookupTree, potentially lazy, suitable for dispatching.
		 */
		public static LookupTree createRoot (
			final A_Method method,
			final List<A_Definition> allDefinitions,
			final List<A_Type> knownArgumentTypes)
		{
			final List<A_Definition> prequalified = new ArrayList<>(1);
			final List<A_Definition> undecided =
				new ArrayList<>(allDefinitions.size());
			final int numArgs = method.numArgs();
			for (final A_Definition definition : allDefinitions)
			{
				final A_Type argsTupleType =
					definition.bodySignature().argsTupleType();
				boolean allComply = true;
				boolean impossible = false;
				for (int i = 1; i <= numArgs; i++)
				{
					final A_Type knownType = knownArgumentTypes.get(i - 1);
					final A_Type definitionArgType =
						argsTupleType.typeAtIndex(i);
					if (!knownType.isSubtypeOf(definitionArgType))
					{
						allComply = false;
					}
					if (knownType.typeIntersection(definitionArgType)
						.isBottom())
					{
						impossible = true;
					}
				}
				if (allComply)
				{
					prequalified.add(definition);
				}
				else if (!impossible)
				{
					undecided.add(definition);
				}
			}
			final LookupTree tree = LookupTree.createTree(
				prequalified,
				undecided,
				knownArgumentTypes);
			return tree;
		}

		/**
		 * Create a LookupTree suitable for deciding which definition to use
		 * when actual argument types are provided.
		 *
		 * @param positive
		 *            Definitions which definitely apply at this node.
		 * @param undecided
		 *            Definitions which are not known to apply or not apply at
		 *            this node.
		 * @param knownArgumentTypes
		 *            The types that the arguments are known to comply with at
		 *            this point.
		 * @return A (potentially lazy) LookupTree used to look up method
		 *         definitions during calls.
		 */
		public static LookupTree createTree (
			final List<A_Definition> positive,
			final List<A_Definition> undecided,
			final List<A_Type> knownArgumentTypes)
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
					for (int inner = 0; inner < size; inner++)
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
			return new InternalLookupTree(
				positive, undecided, knownArgumentTypes);
		}

		/**
		 * Traverse the entire {@link LookupTree}, expanding nodes as necessary.
		 * For each leaf node, invoke the forEachLeafNode {@link Continuation1
		 * continuation}.  For each non-leaf node, first invoke the
		 * preInternalNode, save the memento, recurse into the ifCheckHolds
		 * subtree, invoke the intraInternalNode with the saved memento, recurse
		 * into the ifCheckFails subtree, then invoke the postInternalNode with
		 * the same memento as before.
		 *
		 * @param preInternalNode
		 *            What to do with the the argument number and criterion type
		 *            found at each non-leaf node.  It must produce a memento to
		 *            be passed (eventually) to the next two operations.
		 * @param intraInternalNode
		 *            What to do between the two branches of a non-leaf, given
		 *            the Memento produced before the first branch.
		 * @param postInternalNode
		 *            What to do after the second branch of a non-leaf, given
		 *            the Memento produced before the first branch.
		 * @param forEachLeafNode
		 *            What to do with the {@link List} of {@linkplain
		 *            A_Definition definitions} found in a leaf node.
		 */
		public final <Memento> void traverseEntireTree (
			final Transformer2<Integer, A_Type, Memento> preInternalNode,
			final Continuation1<Memento> intraInternalNode,
			final Continuation1<Memento> postInternalNode,
			final Continuation1<List<A_Definition>> forEachLeafNode)
		{
			final List<Continuation0> actionStack = new ArrayList<>();
			final MutableOrNull<Continuation1<LookupTree>> visit =
				new MutableOrNull<>();
			visit.value = new Continuation1<LookupTree>()
			{
				@Override
				public void value (final @Nullable LookupTree node)
				{
					assert node != null;
					final List<A_Definition> solution =
						node.solutionOrNull();
					if (solution != null)
					{
						forEachLeafNode.value(solution);
					}
					else
					{
						final InternalLookupTree internalNode =
							(InternalLookupTree) node;
						internalNode.expandIfNecessary();
						final MutableOrNull<Memento> memento =
							new MutableOrNull<>();
						// Push some actions in *reverse* order of their
						// execution.
						actionStack.add(new Continuation0()
						{
							@Override
							public void value ()
							{
								postInternalNode.value(memento.value());
							}
						});
						actionStack.add(new Continuation0()
						{
							@Override
							public void value ()
							{
								visit.value().value(
									internalNode.ifCheckFails());
							}
						});
						actionStack.add(new Continuation0()
						{
							@Override
							public void value ()
							{
								intraInternalNode.value(memento.value());
							}
						});
						actionStack.add(new Continuation0()
						{
							@Override
							public void value ()
							{
								visit.value().value(
									internalNode.ifCheckHolds());
							}
						});
						actionStack.add(new Continuation0()
						{
							@Override
							public void value ()
							{
								memento.value = preInternalNode.value(
									internalNode.argumentPositionToTest,
									internalNode.argumentTypeToTest());
							}
						});
					}
				}
			};
			visit.value().value(this);
			while (!actionStack.isEmpty())
			{
				actionStack.remove(actionStack.size() - 1).value();
			}
		}

		/**
		 * Describe this {@code LookupTree} at a given indent level.
		 *
		 * @param indent How much to indent.
		 * @return The description of this {@code LookupTree}
		 */
		public abstract String toString (final int indent);

		@Override
		public final String toString ()
		{
			return toString(0);
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

		@Override List<A_Definition> solutionOrNull()
		{
			return finalResult;
		}

		@Override
		LookupTree lookupStepByTypes(
			final List<? extends A_Type> argTypes)
		{
			error("Attempting to lookup past leaf of decision tree");
			return this;
		}

		@Override
		LookupTree lookupStepByValues (
			final List<? extends A_BasicObject> argValues)
		{
			error("Attempting to lookup past leaf of decision tree");
			return this;
		}

		@Override
		LookupTree lookupStepByValues (
			final A_Tuple argValues)
		{
			error("Attempting to lookup past leaf of decision tree");
			return this;
		}

		@Override
		public final String toString (final int indent)
		{
			if (finalResult.size() == 1)
			{
				return String.format(
					"Success: %s",
					finalResult.get(0).bodySignature().argsTupleType());
			}
			return String.format(
				"Failure: (%d solutions)",
				finalResult.size());
		}
	}

	/**
	 * A {@code LookupTree} representing an incomplete search.  To further the
	 * search, the indicated {@linkplain #argumentTypeToTest type test} will be
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

		/**
		 * The types that the arguments must satisfy to have reached this
		 * position in the decision tree.
		 */
		private final List<A_Type> knownArgumentTypes;

		/** The type to test against an argument type at this node. */
		private volatile @Nullable A_Type argumentTypeToTest;

		/** The 1-based index of the argument to be tested at this node. */
		@InnerAccess int argumentPositionToTest = -1;

		/** The tree to visit if the supplied arguments conform. */
		@InnerAccess @Nullable LookupTree ifCheckHolds;

		/** The tree to visit if the supplied arguments do not conform. */
		@InnerAccess @Nullable LookupTree ifCheckFails;

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
		 * @param knownArgumentTypes
		 *            The list of argument types known to hold at this position
		 *            in the decision tree.  Each element corresponds with an
		 *            argument position for the method.
		 */
		public InternalLookupTree (
			final List<A_Definition> positiveDefinitions,
			final List<A_Definition> undecidedDefinitions,
			final List<A_Type> knownArgumentTypes)
		{
			this.positiveDefinitions = positiveDefinitions;
			this.undecidedDefinitions = undecidedDefinitions;
			this.knownArgumentTypes = knownArgumentTypes;
		}

		/**
		 * Return an argument type to test against the supplied argument.  The
		 * argument's 1-based index is provided by {@link
		 * #argumentPositionToTest}.
		 *
		 * @return A list of argument types to check, expanding this node if
		 *         necessary.
		 */
		@InnerAccess final A_Type argumentTypeToTest ()
		{
			final A_Type testType = argumentTypeToTest;
			assert testType != null;
			return testType;
		}

		/**
		 * Answer the decision subtree to explore if the condition holds.
		 *
		 * @return The "yes" subtree previously set by chooseCriterion().
		 */
		final LookupTree ifCheckHolds ()
		{
			final LookupTree subtree = ifCheckHolds;
			assert subtree != null;
			return subtree;
		}

		/**
		 * Answer the decision subtree to explore if the condition does not
		 * hold.
		 *
		 * @return The "no" subtree previously set by chooseCriterion().
		 */
		final LookupTree ifCheckFails ()
		{
			final LookupTree subtree = ifCheckFails;
			assert subtree != null;
			return subtree;
		}

		/**
		 * If they have not already been computed, compute and cache information
		 * about this node's {@link #argumentTypeToTest}, {@link
		 * #argumentPositionToTest}, and {@link #ifCheckHolds}, and {@link
		 * #ifCheckFails}.
		 */
		final void expandIfNecessary ()
		{
			if (argumentTypeToTest == null)
			{
				synchronized (this)
				{
					// We have to double-check if another thread has run
					// chooseCriterion() since our first check.  We're in a
					// synchronized mutual exclusion, so this is a stable check.
					// Also, argumentTypeToTest is volatile, ensuring Java's
					// infamous double-check problem won't bite us.
					if (argumentTypeToTest == null)
					{
						chooseCriterion();
					}
				}
			}
		}

		/**
		 * We're doing a method lookup, but {@link #argumentTypeToTest} was
		 * null, indicating a lazy subtree.  Expand it by choosing and recording
		 * a criterion to test at this node, then populating the two branches of
		 * the tree with nodes that may themselves need to be expanded in the
		 * future.
		 *
		 * <p>The criterion to choose should be one which serves to eliminate at
		 * least one of the {@linkplain #undecidedDefinitions}, regardless of
		 * whether the test happens to be affirmative or negative.  Eliminating
		 * more than one is better, however.  Ideally, we should choose a test
		 * which serves to eliminate as much indecision as possible in the worst
		 * case (i.e., along the path that is the least effective of the two).
		 * We do this, but we also break ties by eliminating as much indecision
		 * as possible in the <em>best</em> case.</p>
		 *
		 * <p>We eliminate some of the redundancy of a naïve decision tree by
		 * testing a single argument at a time, keeping track of the types we
		 * have tested that argument against.</p>
		 *
		 * </p>Since the negative case is already efficient at eliminating
		 * uncertainty, we only need to track positive information about the
		 * argument types.  Thus, for each argument we maintain precise
		 * information about what type each argument must be at this point in
		 * the tree.  A single type for each argument suffices, since Avail's
		 * type lattice is precise with respect to type intersection, which is
		 * exactly what we use during decision tree construction.</p>
		 */
		final private void chooseCriterion ()
		{
			// Choose a signature to test that guarantees it eliminates the most
			// undecided definitions, regardless of whether the test passes or
			// fails.  If the larger of the two cases (success or failure of the
			// test) is a tie between two criteria, break it by choosing the
			// criterion that eliminates the most undecided definitions in the
			// *best* case.
			assert argumentTypeToTest == null;
			A_Type bestSignature = null;
			int smallestMax = Integer.MAX_VALUE;
			int smallestMin = Integer.MAX_VALUE;
			for (final A_Definition criterion : undecidedDefinitions)
			{
				final A_Type criterionSignature = criterion.bodySignature();
				final A_Type criterionArgsType =
					criterionSignature.argsTupleType();
				int undecidedCountIfTrue = 0;
				int undecidedCountIfFalse = 0;
				for (final A_Definition each : undecidedDefinitions)
				{
					switch (TypeComparison.compare(
						criterionArgsType,
						each.bodySignature().argsTupleType()))
					{
						case SAME_TYPE:
							break;
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
						case DISJOINT_TYPE:
							undecidedCountIfFalse++;
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
			// We have chosen one of the best signatures to test.  However, we
			// still need to decide which argument position to test.  Use the
			// leftmost one which is not already guaranteed by tests that have
			// already been performed.  In particular, ignore arguments whose
			// knownArgumentTypes information is a subtype of the chosen
			// signature's argument type at that position.
			final A_Type bestArgsTupleType = bestSignature.argsTupleType();
			final int numArgs =
				bestArgsTupleType.sizeRange().lowerBound().extractInt();
			A_Type selectedArgumentTypeToTest = null;
			for (int i = 1; i <= numArgs; i++)
			{
				final A_Type knownType = knownArgumentTypes.get(i - 1);
				final A_Type criterionType = bestArgsTupleType.typeAtIndex(i);
				if (!knownType.isSubtypeOf(criterionType))
				{
					argumentPositionToTest = i;
					selectedArgumentTypeToTest = criterionType;
					break;
				}
			}
			assert argumentPositionToTest >= 1;
			assert selectedArgumentTypeToTest != null;
			final A_Type oldArgType =
				knownArgumentTypes.get(argumentPositionToTest - 1);
			final List<A_Type> newPositiveKnownTypes =
				new ArrayList<>(knownArgumentTypes);
			final A_Type replacementArgType =
				selectedArgumentTypeToTest.typeIntersection(oldArgType);
			// Sanity check:  Make sure we at least improve type knowledge in
			// the positive case.
			assert !replacementArgType.equals(oldArgType);
			newPositiveKnownTypes.set(
				argumentPositionToTest - 1, replacementArgType);
			// Compute the positive/undecided lists, both for the condition
			// being true and for the condition being false.
			final List<A_Definition> positiveIfTrue =
				new ArrayList<>(positiveDefinitions);
			final List<A_Definition> undecidedIfTrue = new ArrayList<>();
			final List<A_Definition> undecidedIfFalse = new ArrayList<>();
			final A_Type criterionTupleType = TupleTypeDescriptor.forTypes(
				newPositiveKnownTypes.toArray(new A_Type[numArgs]));
			final A_Type knownTupleType = TupleTypeDescriptor.forTypes(
				knownArgumentTypes.toArray(new A_Type[numArgs]));
			for (final A_Definition undecidedDefinition : undecidedDefinitions)
			{
				// We need to synthesize a tuple type with the knowledge we
				// currently have about the element types.
				final TypeComparison comparison = TypeComparison.compare(
					criterionTupleType,
					undecidedDefinition.bodySignature().argsTupleType()
						.typeIntersection(knownTupleType));
				comparison.applyEffect(
					undecidedDefinition,
					positiveIfTrue,
					undecidedIfTrue,
					undecidedIfFalse);
			}
			ifCheckHolds = LookupTree.createTree(
				positiveIfTrue, undecidedIfTrue, newPositiveKnownTypes);
			ifCheckFails = LookupTree.createTree(
				positiveDefinitions, undecidedIfFalse, knownArgumentTypes);
			assert undecidedIfFalse.size() < undecidedDefinitions.size();
			// This is a volatile write, so all previous writes had to precede
			// it.  If another process runs expandIfNecessary(), it will either
			// see null for this field, or see non-null and be guaranteed that
			// all subsequent reads will see all the previous writes.
			argumentTypeToTest = selectedArgumentTypeToTest;
		}

		/**
		 * Answer the relationship between two signatures, the argument tuple
		 * types of function types representing (1) a criterion to test, and (2)
		 * a definition's signature to be classified.
		 */
		enum TypeComparison
		{
			/**
			 * The definition's signature equals the criterion.
			 */
			SAME_TYPE
			{
				@Override
				public void applyEffect (
					final A_Definition undecidedDefinition,
					final List<A_Definition> ifTruePositiveDefinitions,
					final List<A_Definition> ifTrueUndecidedDefinitions,
					final List<A_Definition> ifFalseUndecidedDefinitions)
				{
					ifTruePositiveDefinitions.add(undecidedDefinition);
				}
			},

			/**
			 * The definition is a proper ancestor of the criterion.
			 */
			PROPER_ANCESTOR_TYPE
			{
				@Override
				public void applyEffect (
					final A_Definition undecidedDefinition,
					final List<A_Definition> ifTruePositiveDefinitions,
					final List<A_Definition> ifTrueUndecidedDefinitions,
					final List<A_Definition> ifFalseUndecidedDefinitions)
				{
					ifTruePositiveDefinitions.add(undecidedDefinition);
					ifFalseUndecidedDefinitions.add(undecidedDefinition);
				}
			},

			/**
			 * The definition is a proper descendant of the criterion.
			 */
			PROPER_DESCENDANT_TYPE
			{
				@Override
				public void applyEffect (
					final A_Definition undecidedDefinition,
					final List<A_Definition> ifTruePositiveDefinitions,
					final List<A_Definition> ifTrueUndecidedDefinitions,
					final List<A_Definition> ifFalseUndecidedDefinitions)
				{
					ifTrueUndecidedDefinitions.add(undecidedDefinition);
				}
			},


			/**
			 * The definition's signature and the criterion are not directly
			 * related, but may share subtypes other than {@linkplain
			 * BottomTypeDescriptor bottom} (⊥).
			 */
			UNRELATED_TYPE
			{
				@Override
				public void applyEffect (
					final A_Definition undecidedDefinition,
					final List<A_Definition> ifTruePositiveDefinitions,
					final List<A_Definition> ifTrueUndecidedDefinitions,
					final List<A_Definition> ifFalseUndecidedDefinitions)
				{
					ifTrueUndecidedDefinitions.add(undecidedDefinition);
					ifFalseUndecidedDefinitions.add(undecidedDefinition);
				}
			},


			/**
			 * The definition's signature and the criterion have ⊥ as their
			 * nearest common descendant.  Thus, there are no tuples of actual
			 * arguments that satisfy both signatures simultaneously.  This is
			 * a useful distinction from {@link #UNRELATED_TYPE}, since a
			 * successful test against the criterion <em>eliminates</em> the
			 * other definition from being considered possible.
			 */
			DISJOINT_TYPE
			{
				@Override
				public void applyEffect (
					final A_Definition undecidedDefinition,
					final List<A_Definition> ifTruePositiveDefinitions,
					final List<A_Definition> ifTrueUndecidedDefinitions,
					final List<A_Definition> ifFalseUndecidedDefinitions)
				{
					ifFalseUndecidedDefinitions.add(undecidedDefinition);
				}
			};

			/**
			 * Conditionally augment the supplied lists with the provided
			 * undecided {@linkplain DefinitionDescriptor definition}.  The
			 * decision of which lists to augment depends on this instance,
			 * which is the result of a previous {@linkplain #compareTo(
			 * TypeComparison) comparison} between the two signatures.
			 *
			 * @param undecidedDefinition
			 *            A {@linkplain DefinitionDescriptor definition} whose
			 *            applicability has not yet been decided at the current
			 *            position in the {@link LookupTree}.
			 * @param ifTruePositiveDefinitions
			 *            A list of definitions that will be applicable to some
			 *            arguments if the arguments meet the criterion.
			 * @param ifTrueUndecidedDefinitions
			 *            A list of definitions that will be undecided for some
			 *            arguments if the arguments meet the criterion.
			 * @param ifFalseUndecidedDefinitions
			 *            A list of definitions that will be applicable to some
			 *            arguments if the arguments do not meet the criterion.
			 */
			public abstract void applyEffect (
				final A_Definition undecidedDefinition,
				final List<A_Definition> ifTruePositiveDefinitions,
				final List<A_Definition> ifTrueUndecidedDefinitions,
				final List<A_Definition> ifFalseUndecidedDefinitions);

			/**
			 * Compare two signatures (tuple types).  The first is the
			 * criterion, which will eventually be tested against arguments.
			 * The second signature is the one being compared by specificity
			 * with the criterion.
			 *
			 * @param criterionTupleType
			 *            The criterion signature to test against.
			 * @param someTupleType
			 *            A signature to test against the criterion signature.
			 * @return A TypeComparison representing the relationship between
			 *         the criterion and the other signature.
			 */
			public static TypeComparison compare (
				final A_Type criterionTupleType,
				final A_Type someTupleType)
			{
				assert criterionTupleType.isTupleType();
				assert someTupleType.isTupleType();
				final A_Type intersection =
					criterionTupleType.typeIntersection(someTupleType);
				if (intersection.isBottom())
				{
					return DISJOINT_TYPE;
				}
				final boolean below =
					someTupleType.isSubtypeOf(criterionTupleType);
				final boolean above =
					criterionTupleType.isSubtypeOf(someTupleType);
				return
					below
						? (above ? SAME_TYPE : PROPER_DESCENDANT_TYPE)
						: (above ? PROPER_ANCESTOR_TYPE : UNRELATED_TYPE);
			}
		}

		@Override
		@Nullable List<A_Definition> solutionOrNull ()
		{
			return null;
		}

		@Override
		LookupTree lookupStepByValues (
			final List<? extends A_BasicObject> argValues)
		{
			expandIfNecessary();
			final A_Type testType = argumentTypeToTest();
			final A_BasicObject argument =
				argValues.get(argumentPositionToTest - 1);
			if (argument.isInstanceOf(testType))
			{
				return ifCheckHolds();
			}
			return ifCheckFails();
		}

		@Override
		LookupTree lookupStepByValues (
			final A_Tuple argValues)
		{
			expandIfNecessary();
			final A_Type testType = argumentTypeToTest();
			final A_BasicObject argument =
				argValues.tupleAt(argumentPositionToTest);
			if (argument.isInstanceOf(testType))
			{
				return ifCheckHolds();
			}
			return ifCheckFails();
		}

		@Override
		LookupTree lookupStepByTypes (
			final List<? extends A_Type> argTypes)
		{
			expandIfNecessary();
			final A_Type testType = argumentTypeToTest();
			final A_Type argumentType =
				argTypes.get(argumentPositionToTest - 1);
			if (argumentType.isSubtypeOf(testType))
			{
				return ifCheckHolds();
			}
			return ifCheckFails();
		}

		@Override
		public final String toString (final int indent)
		{
			if (argumentTypeToTest == null)
			{
				return String.format(
					"Lazy internal node: known=%s",
					knownArgumentTypes);
			}
			final StringBuilder builder = new StringBuilder();
			builder.append(
				String.format(
					"#%d ∈ %s: known=%s%n",
					argumentPositionToTest,
					argumentTypeToTest,
					knownArgumentTypes));
			for (int i = 0; i <= indent; i++)
			{
				builder.append("\t");
			}
			builder.append(ifCheckHolds().toString(indent + 1));
			builder.append(String.format("%n"));
			for (int i = 0; i <= indent; i++)
			{
				builder.append("\t");
			}
			builder.append(ifCheckFails().toString(indent + 1));
			return builder.toString();
		}
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == OWNING_BUNDLES
			|| e == DEFINITIONS_TUPLE
			|| e == PRIVATE_TESTING_TREE
			|| e == SEMANTIC_RESTRICTIONS_SET
			|| e == SEALED_ARGUMENTS_TYPES_TUPLE
			|| e == DEPENDENT_CHUNKS_WEAK_SET_POJO
			|| e == MACRO_DEFINITIONS_TUPLE
			|| e == MACRO_TESTING_TREE
			|| e == MACRO_PREFIX_FUNCTIONS;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final int size =
			object.definitionsTuple().tupleSize()
			+ object.macroDefinitionsTuple().tupleSize();
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
			aStream.append(eachBundle.message().toString());
			first = false;
		}
	}

	@SuppressWarnings("unchecked")
	@Override @AvailMethod
	void o_AddDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		// Record the fact that the given chunk depends on this object not
		// changing.  Local synchronization is sufficient, since invalidation
		// can't happen while L2 code is running (and therefore when the
		// L2Translator could be calling this).
		synchronized (object)
		{
			final A_BasicObject pojo =
				object.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO);
			final Set<L2Chunk> chunkSet;
			if (pojo.equalsNil())
			{
				chunkSet = Collections.newSetFromMap(
					new WeakHashMap<L2Chunk, Boolean>());
				object.setSlot(
					DEPENDENT_CHUNKS_WEAK_SET_POJO,
					RawPojoDescriptor.identityWrap(chunkSet).makeShared());
			}
			else
			{
				chunkSet = (Set<L2Chunk>) pojo.javaObjectNotNull();
			}
			chunkSet.add(chunk);
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

	@Override
	A_Set o_Bundles (final AvailObject object)
	{
		return object.slot(OWNING_BUNDLES);
	}

	@Override
	A_Bundle o_ChooseBundle (final AvailObject object)
	{
		final AvailLoader loader = Interpreter.current().availLoader();
		final A_Set visibleModules = loader.module().allAncestors();
		final A_Set bundles = object.slot(OWNING_BUNDLES);
		for (final A_Bundle bundle : bundles)
		{
			if (visibleModules.hasElement(bundle.message().issuingModule()))
			{
				return bundle;
			}
		}
		return bundles.iterator().next();
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
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	/**
	 * Look up all method definitions that could match the given argument
	 * types. Answer a {@linkplain List list} of {@linkplain
	 * MethodDefinitionDescriptor method signatures}.
	 */
	@Override @AvailMethod
	List<A_Definition> o_FilterByTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
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

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(HASH);
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
			if (object.slot(MACRO_DEFINITIONS_TUPLE).tupleSize() > 0)
			{
				return false;
			}
			for (final A_Tuple nthPrefixFunctions : object.prefixFunctions())
			{
				if (nthPrefixFunctions.tupleSize() > 0)
				{
					return false;
				}
			}
			return true;
		}
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return METHOD.o();
	}

	/**
	 * Look up the definition to invoke, given a tuple of argument types.
	 * Use the testingTree to find the definition to invoke.
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
			LookupTree tree = object.testingTree();
			List<A_Definition> solutions;
			while ((solutions = tree.solutionOrNull()) == null)
			{
				tree = tree.lookupStepByTypes(argumentTypesList);
			}
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
	 * Use the testingTree to find the definition to invoke (answer nil if
	 * a lookup error occurs).
	 */
	@Override @AvailMethod
	A_Definition o_LookupByValuesFromList (
		final AvailObject object,
		final List<? extends A_BasicObject> argumentList,
		final MutableOrNull<AvailErrorCode> errorCode)
	{
		LookupTree tree = object.testingTree();
		List<A_Definition> solutions;
		while ((solutions = tree.solutionOrNull()) == null)
		{
			tree = tree.lookupStepByValues(argumentList);
		}
		if (solutions.size() == 1)
		{
			return solutions.get(0);
		}
		if (solutions.size() == 0)
		{
			errorCode.value = AvailErrorCode.E_NO_METHOD_DEFINITION;
		}
		else
		{
			errorCode.value = AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION;
		}
		return NilDescriptor.nil();
	}

	/**
	 * Look up the macro definition to invoke, given an array of argument
	 * phrases.  Use the macroTestingTree to find the macro definition to invoke
	 * (answer nil if a lookup error occurs).
	 *
	 * <p>Note that this testing tree approach is only applicable if all of the
	 * macro definitions are visible (defined in the current module or an
	 * ancestor.  That should be the <em>vast</em> majority of the use of
	 * macros, but when it isn't, other lookup approaches are necessary.</p>
	 */
	@Override @AvailMethod
	A_Definition o_LookupMacroByPhraseTuple (
		final AvailObject object,
		final A_Tuple argumentPhraseTuple,
		final MutableOrNull<AvailErrorCode> errorCode)
	{
		LookupTree tree = object.macroTestingTree();
		List<A_Definition> solutions;
		while ((solutions = tree.solutionOrNull()) == null)
		{
			tree = tree.lookupStepByValues(argumentPhraseTuple);
		}
		if (solutions.size() == 1)
		{
			return solutions.get(0);
		}
		if (solutions.size() == 0)
		{
			errorCode.value = AvailErrorCode.E_NO_METHOD_DEFINITION;
		}
		else
		{
			errorCode.value = AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION;
		}
		return NilDescriptor.nil();
	}

	@Override @AvailMethod
	A_Tuple o_MacroDefinitionsTuple (final AvailObject object)
	{
		assert isShared();
		synchronized (object)
		{
			return object.slot(MACRO_DEFINITIONS_TUPLE);
		}
	}

	/**
	 * Answer the cached macroTestingTree. If there's a nil in that slot,
	 * compute and cache the testing tree based on the macroDefinitionsTuple.
	 * The tree should be a {@linkplain RawPojoDescriptor raw pojo} holding a
	 * {@link LookupTree}.
	 */
	@Override @AvailMethod
	LookupTree o_MacroTestingTree (final AvailObject object)
	{
		A_BasicObject result = object.slot(MACRO_TESTING_TREE);
		if (result.equalsNil())
		{
			synchronized (object)
			{
				AvailRuntime.readBarrier();
				result = object.slot(MACRO_TESTING_TREE);
				if (result.equalsNil())
				{
					final List<A_Type> initialTypes =
						Collections.<A_Type>nCopies(object.numArgs(), ANY.o());
					final A_Tuple allDefinitions =
						object.slot(MACRO_DEFINITIONS_TUPLE);
					final LookupTree tree = LookupTree.createRoot(
						object,
						TupleDescriptor.<A_Definition>toList(allDefinitions),
						initialTypes);
					result = RawPojoDescriptor.identityWrap(tree).makeShared();
					// Assure all writes are committed before setting the tree.
					AvailRuntime.writeBarrier();
					object.setSlot(MACRO_TESTING_TREE, result);
				}
			}
		}
		return (LookupTree) result.javaObject();
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
	void o_MethodAddBundle (final AvailObject object, final A_Bundle bundle)
	{
		A_Set bundles = object.slot(OWNING_BUNDLES);
		bundles = bundles.setWithElementCanDestroy(bundle, false);
		bundles.makeShared();
		object.setSlot(OWNING_BUNDLES, bundles);
	}

	@Override @AvailMethod
	void o_MethodAddDefinition (
		final AvailObject object,
		final A_Definition definition)
	throws SignatureException
	{
		// Method manipulation takes place while all fibers are L1-precise and
		// suspended.  Use a global lock at the outermost calls to side-step
		// deadlocks.  Because no fiber is running we don't have to protect
		// subsystems like the L2Translator from these changes.
		L2Chunk.invalidationLock.lock();
		try
		{
			final A_Type bodySignature = definition.bodySignature();
			final A_Type paramTypes = bodySignature.argsTupleType();
			if (definition.isMacroDefinition())
			{
				// Install the macro.
				final A_Tuple oldTuple = object.slot(MACRO_DEFINITIONS_TUPLE);

				final A_Tuple newTuple = oldTuple.appendCanDestroy(
					definition, true);
				object.setSlot(MACRO_DEFINITIONS_TUPLE, newTuple.makeShared());
			}
			else
			{
				final A_Tuple oldTuple = object.slot(DEFINITIONS_TUPLE);
				final A_Tuple seals = object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
				for (final A_Tuple seal : seals)
				{
					final A_Type sealType = TupleTypeDescriptor.forTypes(
						TupleDescriptor.toArray(seal));
					if (paramTypes.isSubtypeOf(sealType))
					{
						throw new SignatureException(
							AvailErrorCode.E_METHOD_IS_SEALED);
					}
				}
				final A_Tuple newTuple = oldTuple.appendCanDestroy(
					definition, true);
				object.setSlot(DEFINITIONS_TUPLE, newTuple.makeShared());
			}
			membershipChanged(object);
		}
		finally
		{
			L2Chunk.invalidationLock.unlock();
		}
	}

	@Override
	A_String o_MethodName (final AvailObject object)
	{
		return object.chooseBundle().message().atomName();
	}

	@Override @AvailMethod
	int o_NumArgs (final AvailObject object)
	{
		return object.slot(NUM_ARGS);
	}

	@Override
	A_Tuple o_PrefixFunctions (final AvailObject object)
	{
		return object.slot(MACRO_PREFIX_FUNCTIONS);
	}

	@Override
	public void o_PrefixFunctions (
		final AvailObject object,
		final A_Tuple prefixFunctions)
	{
		object.setSlot(MACRO_PREFIX_FUNCTIONS, prefixFunctions);
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
		// Method manipulation takes place while all fibers are L1-precise and
		// suspended.  Use a global lock at the outermost calls to side-step
		// deadlocks.  Because no fiber is running we don't have to protect
		// subsystems like the L2Translator from these changes.
		L2Chunk.invalidationLock.lock();
		try
		{
			final ObjectSlotsEnum slot =
				!definition.isMacroDefinition()
				? DEFINITIONS_TUPLE
				: MACRO_DEFINITIONS_TUPLE;
			A_Tuple definitionsTuple = object.slot(slot);
			definitionsTuple = TupleDescriptor.without(
				definitionsTuple,
				definition);
			object.setSlot(
				slot,
				definitionsTuple.traversed().makeShared());
			membershipChanged(object);

		}
		finally
		{
			L2Chunk.invalidationLock.unlock();
		}
	}

	/**
	 * Remove the chunk from my set of dependent chunks because it has been
	 * invalidated by a new definition in either me or another method on which
	 * the chunk is contingent.
	 */
	@Override @AvailMethod
	void o_RemoveDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		assert L2Chunk.invalidationLock.isHeldByCurrentThread();
		final A_BasicObject pojo =
			object.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO);
		if (!pojo.equalsNil())
		{
			@SuppressWarnings("unchecked")
			final Set<L2Chunk> chunkSet =
				(Set<L2Chunk>) pojo.javaObjectNotNull();
			chunkSet.remove(chunk);
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
	A_Tuple o_SealedArgumentsTypesTuple (final AvailObject object)
	{
		return object.slot(SEALED_ARGUMENTS_TYPES_TUPLE);
	}

	@Override @AvailMethod
	A_Set o_SemanticRestrictions (final AvailObject object)
	{
		synchronized (object)
		{
			return object.slot(SEMANTIC_RESTRICTIONS_SET);
		}
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.METHOD;
	}

	/**
	 * Answer the cached privateTestingTree. If there's a nil in that slot,
	 * compute and cache the testing tree based on the definitionsTuple.
	 * The tree should be a {@linkplain RawPojoDescriptor raw pojo} holding a
	 * {@link LookupTree}.
	 */
	@Override @AvailMethod
	LookupTree o_TestingTree (final AvailObject object)
	{
		A_BasicObject result = object.slot(PRIVATE_TESTING_TREE);
		if (result.equalsNil())
		{
			synchronized (object)
			{
				AvailRuntime.readBarrier();
				result = object.slot(PRIVATE_TESTING_TREE);
				if (result.equalsNil())
				{
					final List<A_Type> initialTypes =
						Collections.<A_Type>nCopies(object.numArgs(), ANY.o());
					final A_Tuple allDefinitions =
						object.slot(DEFINITIONS_TUPLE);
					final LookupTree tree = LookupTree.createRoot(
						object,
						TupleDescriptor.<A_Definition>toList(allDefinitions),
						initialTypes);
					result = RawPojoDescriptor.identityWrap(tree).makeShared();
					// Assure all writes are committed before setting the tree.
					AvailRuntime.writeBarrier();
					object.setSlot(PRIVATE_TESTING_TREE, result);
				}
			}
		}
		return (LookupTree) result.javaObject();
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("method");
		writer.write("aliases");
		object.slot(OWNING_BUNDLES).writeTo(writer);
		writer.write("definitions");
		object.slot(DEFINITIONS_TUPLE).writeTo(writer);
		writer.write("macro definitions");
		object.slot(MACRO_DEFINITIONS_TUPLE).writeTo(writer);
		writer.write("macro prefix functions");
		object.slot(MACRO_PREFIX_FUNCTIONS).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("method");
		writer.write("aliases");
		object.slot(OWNING_BUNDLES).writeSummaryTo(writer);
		writer.write("definitions");
		object.slot(DEFINITIONS_TUPLE).writeSummaryTo(writer);
		writer.write("macro definitions");
		object.slot(MACRO_DEFINITIONS_TUPLE).writeSummaryTo(writer);
		writer.write("macro prefix functions");
		object.slot(MACRO_PREFIX_FUNCTIONS).writeSummaryTo(writer);
		writer.endObject();
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
	 * @param numArgs
	 *        The number of arguments that this method expects.
	 * @param numSections
	 *        The number of {@link MessageSplitter#numberOfSectionCheckpoints()
	 *        section checkpoints} (§).
	 * @return A new method with no name.
	 */
	public static AvailObject newMethod (
		final int numArgs,
		final int numSections)
	{
		final AvailObject result = mutable.create();
		result.setSlot(HASH, AvailRuntime.nextHash());
		result.setSlot(NUM_ARGS, numArgs);
		result.setSlot(OWNING_BUNDLES, SetDescriptor.empty());
		result.setSlot(DEFINITIONS_TUPLE, TupleDescriptor.empty());
		result.setSlot(PRIVATE_TESTING_TREE, NilDescriptor.nil());
		result.setSlot(SEMANTIC_RESTRICTIONS_SET, SetDescriptor.empty());
		result.setSlot(SEALED_ARGUMENTS_TYPES_TUPLE, TupleDescriptor.empty());
		result.setSlot(DEPENDENT_CHUNKS_WEAK_SET_POJO, NilDescriptor.nil());
		result.setSlot(MACRO_DEFINITIONS_TUPLE, TupleDescriptor.empty());
		result.setSlot(MACRO_TESTING_TREE, NilDescriptor.nil());
		result.setSlot(
			MACRO_PREFIX_FUNCTIONS,
			RepeatedElementTupleDescriptor.createRepeatedElementTuple(
				numSections, TupleDescriptor.empty()));
		result.makeShared();
		return result;
	}

	/**
	 * The membership of this {@linkplain MethodDescriptor method} has changed.
	 * Invalidate anything that depended on the previous membership, including
	 * the {@linkplain ObjectSlots#PRIVATE_TESTING_TREE testing tree} and any
	 * {@linkplain ObjectSlots#DEPENDENT_CHUNKS_WEAK_SET_POJO dependent}
	 * {@link L2Chunk}s.
	 *
	 * @param object The method that changed.
	 */
	private static void membershipChanged (final AvailObject object)
	{
		assert L2Chunk.invalidationLock.isHeldByCurrentThread();
		// Invalidate any affected level two chunks.
		final A_BasicObject pojo = object.slot(DEPENDENT_CHUNKS_WEAK_SET_POJO);
		if (!pojo.equalsNil())
		{
			// Copy the set of chunks to avoid modification during iteration.
			@SuppressWarnings("unchecked")
			final Set<L2Chunk> originalSet =
				(Set<L2Chunk>) pojo.javaObjectNotNull();
			final Set<L2Chunk> chunksToInvalidate = new HashSet<>(originalSet);
			for (final L2Chunk chunk : chunksToInvalidate)
			{
				chunk.invalidate();
			}
			// The chunk invalidations should have removed all dependencies.
			assert originalSet.isEmpty();
		}
		// Clear the privateTestingTree cache.
		object.setSlot(PRIVATE_TESTING_TREE, NilDescriptor.nil());
		object.setSlot(MACRO_TESTING_TREE, NilDescriptor.nil());
	}

	/**
	 * Construct a new {@link MethodDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MethodDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
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
		final A_Atom atom = AtomDescriptor.createSpecialAtom(name);
		final A_Bundle bundle;
		try
		{
			bundle = atom.bundleOrCreate();
		}
		catch (final MalformedMessageException e)
		{
			assert false : "This should not happen!";
			throw new RuntimeException(
				"VM method name is invalid: " + name.toString(), e);
		}
		final A_Method method = bundle.bundleMethod();
		for (final Primitive primitive : primitives)
		{
			final A_Function function = FunctionDescriptor.newPrimitiveFunction(
				primitive, NilDescriptor.nil(), 0);
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
				assert false : "This should not happen!";
				throw new RuntimeException(
					"VM method name is invalid: " + name.toString(), e);
			}
		}
		assert atom.descriptor().isShared();
		assert atom.isAtomSpecial();
		return atom;
	}

	/**
	 * A special {@linkplain AtomDescriptor atom} used to name the VM's method
	 * to crash during early bootstrapping problems.
	 */
	private static final A_Atom vmCrashAtom = createSpecialMethodAtom(
		"vm crash:(«_‡,»)",
		P_EmergencyExit.instance);

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
		P_SimpleMethodDeclaration.instance,
		P_MethodDeclarationFromAtom.instance);

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
	 * The (special) name of the VM-built method used to define a macro without
	 * also defining prefix functions.
	 */
	private static final A_Atom vmJustMacroDefinerAtom =
		createSpecialMethodAtom(
			"vm macro_is_",
			P_JustMacroDefinitionForAtom.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to define a macro without also defining prefix functions.
	 *
	 * @return The name of the bootstrap macro-defining method.
	 */
	public static A_Atom vmJustMacroDefinerAtom ()
	{
		return vmJustMacroDefinerAtom;
	}

	/**
	 * The (special) name of the VM-built pre-bootstrap macro-defining method.
	 */
	private static final A_Atom vmMacroDefinerAtom = createSpecialMethodAtom(
		"vm macro_is«_,»_",
		P_SimpleMacroDeclaration.instance);

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
	 * The (special) name of the VM-built function that adds a prefix function.
	 */
	private static final A_Atom vmDeclarePrefixFunctionAtom =
		createSpecialMethodAtom(
			"vm declare prefix function_at_is_",
			P_DeclarePrefixFunctionForAtom.instance);

	/**
	 * Answer the (special) name of the VM-built continuation caller atom.
	 *
	 * @return The name of the VM's continuation caller atom.
	 */
	public static A_Atom vmDeclarePrefixFunctionAtom ()
	{
		return vmDeclarePrefixFunctionAtom;
	}

	/**
	 * The (special) name of the VM-built method used to load abstract
	 * definitions.
	 */
	private static final A_Atom vmAbstractDefinerAtom = createSpecialMethodAtom(
		"vm abstract_for_",
		P_AbstractMethodDeclarationForAtom.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to load abstract definitions.
	 *
	 * @return The name of the abstract definer method.
	 */
	public static A_Atom vmAbstractDefinerAtom ()
	{
		return vmAbstractDefinerAtom;
	}

	/**
	 * The (special) name of the VM-built method used to load forward
	 * definitions.
	 */
	private static final A_Atom vmForwardDefinerAtom = createSpecialMethodAtom(
		"vm forward_for_",
		P_ForwardMethodDeclarationForAtom.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to load forward method definitions.
	 *
	 * @return The name of the forward definer method.
	 */
	public static A_Atom vmForwardDefinerAtom ()
	{
		return vmForwardDefinerAtom;
	}

	/**
	 * The (special) name of the VM-built method used to define grammatical
	 * restrictions.
	 */
	private static final A_Atom vmGrammaticalRestrictionsAtom =
		createSpecialMethodAtom(
			"vm grammatical restriction_is_",
			P_GrammaticalRestrictionFromAtoms.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to define grammatical restrictions.
	 *
	 * @return The name of the grammatical restriction method.
	 */
	public static A_Atom vmGrammaticalRestrictionsAtom ()
	{
		return vmGrammaticalRestrictionsAtom;
	}

	/**
	 * The (special) name of the VM-built method used to define semantic
	 * restrictions.
	 */
	private static final A_Atom vmSemanticRestrictionAtom =
		createSpecialMethodAtom(
			"vm semantic restriction_is_",
			P_AddSemanticRestrictionForAtom.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to declare semantic restrictions.
	 *
	 * @return The name of the method for adding semantic restrictions.
	 */
	public static A_Atom vmSemanticRestrictionAtom ()
	{
		return vmSemanticRestrictionAtom;
	}

	/**
	 * The (special) name of the VM-built method used to define seal methods.
	 */
	private static final A_Atom vmSealAtom =
		createSpecialMethodAtom(
			"vm seal_at_",
			P_SealMethodByAtom.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to seal methods.
	 *
	 * @return The name of the sealing method.
	 */
	public static A_Atom vmSealAtom ()
	{
		return vmSealAtom;
	}

	/**
	 * The (special) name of the VM-built method used to define aliased atoms.
	 */
	private static final A_Atom vmAliasAtom =
		createSpecialMethodAtom(
			"vm alias new name_to_",
			P_Alias.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to created aliased atoms.
	 *
	 * @return The name of the aliasing method.
	 */
	public static A_Atom vmAliasAtom ()
	{
		return vmAliasAtom;
	}

	/**
	 * The (special) name of the VM-built method used to add atom properties.
	 */
	private static final A_Atom vmAddAtomPropertyAtom =
		createSpecialMethodAtom(
			"vm atom_at property_put_",
			P_AtomSetProperty.instance);

	/**
	 * Answer the (special) {@linkplain AtomDescriptor name} of the VM method
	 * used to created add properties to atoms.
	 *
	 * @return The name of the method that adds atom properties.
	 */
	public static A_Atom vmAddAtomPropertyAtom ()
	{
		return vmAddAtomPropertyAtom;
	}

	/**
	 * The (special) name of the VM-built function application method.
	 */
	private static final A_Atom vmFunctionApplyAtom = createSpecialMethodAtom(
		"vm function apply_(«_‡,»)",
		P_InvokeWithTuple.instance);

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
		P_DeclareAllExportedAtoms.instance);

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
			P_DeclareStringificationAtom.instance);

	/**
	 * Answer the (special) name of the VM-built stringifier declaration atom.
	 *
	 * @return The name of the VM's stringifier declaration atom.
	 */
	public static A_Atom vmDeclareStringifierAtom ()
	{
		return vmDeclareStringifierAtom;
	}

	/**
	 * The (special) name of the VM-built continuation caller atom.
	 */
	private static final A_Atom vmContinuationCallerAtom =
		createSpecialMethodAtom(
			"vm_'s caller",
			P_ContinuationCaller.instance);

	/**
	 * Answer the (special) name of the VM-built continuation caller atom.
	 *
	 * @return The name of the VM's continuation caller atom.
	 */
	public static A_Atom vmContinuationCallerAtom ()
	{
		return vmContinuationCallerAtom;
	}

	/**
	 * The (special) name of the VM-built variable accessor atom.
	 */
	private static final A_Atom vmVariableGetAtom =
		createSpecialMethodAtom(
			"vm↓_",
			P_GetValue.instance);

	/**
	 * Answer the (special) name of the VM-built variable accessor atom.
	 *
	 * @return The name of the VM's variable accessor atom.
	 */
	public static A_Atom vmVariableGetAtom ()
	{
		return vmVariableGetAtom;
	}

	/**
	 * The (special) name of the VM-built continuation resumption atom.
	 */
	private static final A_Atom vmResumeContinuationAtom =
		createSpecialMethodAtom(
			"vm resume_",
			P_ResumeContinuation.instance);

	/**
	 * Answer the (special) name of the VM-built continuation resumption atom.
	 *
	 * @return The name of the VM's continuation resumption atom.
	 */
	public static A_Atom vmResumeContinuationAtom ()
	{
		return vmResumeContinuationAtom;
	}
}
