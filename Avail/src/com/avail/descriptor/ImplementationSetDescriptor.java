/**
 * descriptor/ImplementationSetDescriptor.java
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
import static java.lang.Math.max;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.interpreter.Interpreter;
import com.avail.utility.*;

/**
 * An implementation set maintains all methods that have the same name.  At
 * compile time a name is looked up and the corresponding implementation set is
 * stored as a literal in the object code.  At runtime the actual method is
 * located within the implementation set and then invoked.  The implementation
 * sets also keep track of bidirectional dependencies, so that a change of
 * membership causes an immediate invalidation of optimized level two code that
 * depends on the previous membership.
 *
 * <p>To support macros safely, an implementation set must contain either all
 * {@linkplain MacroSignatureDescriptor macro signatures} or all non-macro
 * {@linkplain SignatureDescriptor signatures}, but not both.</p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class ImplementationSetDescriptor
extends Descriptor
{
	/**
	 * The fields that are of type {@code AvailObject}.
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@linkplain SetDescriptor set} of {@linkplain
		 * L2ChunkDescriptor.IntegerSlots#INDEX indices} of {@linkplain
		 * L2ChunkDescriptor level two chunks} that depend on the membership of
		 * this {@linkplain ImplementationSetDescriptor implementation set}.  A
		 * change to the membership should cause these chunks to be invalidated.
		 */
		DEPENDENT_CHUNK_INDICES,

		/**
		 * The {@linkplain TupleDescriptor tuple} of {@link SignatureDescriptor
		 * signatures} that constitute this multimethod (or multimacro).
		 */
		IMPLEMENTATIONS_TUPLE,

		/**
		 * The {@link CyclicTypeDescriptor cyclic type} that acts as the true
		 * name of this {@link ImplementationSetDescriptor implementation set}.
		 */
		NAME,

		/**
		 * A {@link TupleDescriptor tuple} of {@link IntegerDescriptor integers}
		 * that encodes a decision tree for selecting the most specific
		 * multimethod appropriate for the argument types.
		 */
		PRIVATE_TESTING_TREE
	}

	@Override
	public void o_DependentChunkIndices (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.DEPENDENT_CHUNK_INDICES, value);
	}

	@Override
	public void o_ImplementationsTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.IMPLEMENTATIONS_TUPLE, value);
	}

	@Override
	public void o_Name (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.NAME, value);
	}

	@Override
	public void o_PrivateTestingTree (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.PRIVATE_TESTING_TREE, value);
	}

	@Override
	public @NotNull AvailObject o_DependentChunkIndices (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.DEPENDENT_CHUNK_INDICES);
	}

	@Override
	public @NotNull AvailObject o_ImplementationsTuple (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.IMPLEMENTATIONS_TUPLE);
	}

	@Override
	public @NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NAME);
	}

	@Override
	public @NotNull AvailObject o_PrivateTestingTree (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.PRIVATE_TESTING_TREE);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull Enum<?> e)
	{
		return e == ObjectSlots.IMPLEMENTATIONS_TUPLE
			|| e == ObjectSlots.PRIVATE_TESTING_TREE
			|| e == ObjectSlots.DEPENDENT_CHUNK_INDICES;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		final int size = object.implementationsTuple().tupleSize();
		aStream.append(Integer.toString(size));
		aStream.append(" implementation");
		if (size != 1)
		{
			aStream.append('s');
		}
		aStream.append(" of ");
		aStream.append(object.name().name().asNativeString());
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		// ImplementationSets compare by identity.
		return another.traversed().sameAddressAs(object);
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		return IMPLEMENTATION_SET.o();
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.name().hash() + 0x61AF3FC;
	}

	/**
	 * Make the object immutable so it can be shared safely.  If I was mutable I
	 * have to scan my children and make them immutable as well (recursively
	 * down to immutable descendants).  Actually, I allow my
	 * implementationsTuple, my privateTestingTree, and my dependentsChunks
	 * slots to be mutable even when I'm immutable.
	 */
	@Override
	public @NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		object.descriptor(ImplementationSetDescriptor.immutable());
		object.name().makeImmutable();
		return object;
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		return IMPLEMENTATION_SET.o();
	}

	@Override
	public void o_AddDependentChunkIndex (
		final @NotNull AvailObject object,
		final int aChunkIndex)
	{
		// Record the fact that the chunk indexed by aChunkIndex depends on
		// this implementationSet not changing.
		object.dependentChunkIndices(
			object.dependentChunkIndices().setWithElementCanDestroy(
				IntegerDescriptor.fromInt(aChunkIndex),
				true));
	}

	/**
	 * Add the {@linkplain SignatureDescriptor signature implementation} to me.
	 * Causes dependent chunks to be invalidated.
	 *
	 * <p>Macro signatures and non-macro signatures should not be combined in
	 * the same implementation set.
	 *
	 * @param object The implementation set.
	 * @param implementation A {@linkplain SignatureDescriptor signature} to be
	 *
	 */
	@Override
	public void o_AddImplementation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject implementation)
	{
		AvailObject oldTuple = object.implementationsTuple();
		if (oldTuple.tupleSize() > 0)
		{
			// Ensure that we're not mixing macro and non-macro signatures.
			assert implementation.isMacro() == oldTuple.tupleAt(1).isMacro();
		}
		AvailObject set = oldTuple.asSet();
		set = set.setWithElementCanDestroy(
			implementation,
			true);
		object.implementationsTuple(set.asTuple());
		membershipChanged(object);
	}

	/**
	 * Create the testing tree for computing which implementation to invoke when
	 * given a list of arguments.  The tree is flattened into a tuple of
	 * integers.  Testing begins with the first element of the tuple.  If it's
	 * odd, divide by two to get the index into implementationsTuple (a zero
	 * index indicates an ambiguous lookup).  If it's even, divide by two to get
	 * an index into implementationsTuple, then test the list of arguments
	 * against it.  If the arguments agree with the signature, add 2 to the
	 * current position (to skip the test number and an offset) and continue.
	 * If the arguments did not agree with the signature, add 2 + the value in
	 * the next slot of the tuple to the current position, then continue.  We
	 * use a simple one-layer MinMax algorithm to produce a reasonable testing
	 * tree, where the choice of signature to test for is the one that minimizes
	 * the maximum number of remaining possible solutions after a test.
	 */
	@Override
	public @NotNull AvailObject
		o_CreateTestingTreeWithPositiveMatchesRemainingPossibilities (
		final @NotNull AvailObject object,
		final @NotNull AvailObject positiveTuple,
		final @NotNull AvailObject possibilities)
	{
		final AvailObject imps = object.implementationsTuple();
		AvailObject result;
		if (possibilities.tupleSize() == 0)
		{
			for (final AvailObject index1 : positiveTuple)
			{
				boolean all = true;
				for (final AvailObject index2 : positiveTuple)
				{
					all = all && imps.tupleAt(index2.extractInt())
						.bodySignature().acceptsArgTypesFromClosureType(
							imps.tupleAt(index1.extractInt()).bodySignature());
				}
				if (all)
				{
					result = ByteTupleDescriptor.mutableObjectOfSize(1);
					result.hashOrZero(0);
					result = result.tupleAtPuttingCanDestroy(
						1,
						IntegerDescriptor.fromInt(
							index1.extractInt() * 2 + 1),
						true);
					return result;
				}
			}
			// There was no most specific positive signature.  Indicate an
			// ambiguity error at this point in the tree.
			result = ByteTupleDescriptor.mutableObjectOfSize(1);
			result.hashOrZero(0);
			result = result.tupleAtPuttingCanDestroy(
				1,
				IntegerDescriptor.fromInt(0 * 2 + 1),
				true);
			return result;
		}
		// See if there are any solutions still possible.  Scan the list of
		// possibilities (and known positives), and for each one see if it's
		// more specific than everything in the positive collection.  If there
		// are no such solutions, we are already at a point that represents an
		// ambiguous lookup.
		boolean possibleSolutionExists = false;
		AvailObject possibility;
		for (final AvailObject possibleIndex : possibilities)
		{
			if (!possibleSolutionExists)
			{
				possibility = imps.tupleAt(possibleIndex.extractInt())
					.bodySignature();
				boolean allPossibleAreParents = true;
				for (final AvailObject index2 : positiveTuple)
				{
					allPossibleAreParents = allPossibleAreParents
						&& imps.tupleAt(index2.extractInt())
							.bodySignature()
							.acceptsArgTypesFromClosureType(possibility);
				}
				possibleSolutionExists = allPossibleAreParents;
			}
		}
		for (final AvailObject index1 : positiveTuple)
		{
			if (!possibleSolutionExists)
			{
				possibility = imps.tupleAt(index1.extractInt()).bodySignature();
				boolean allPossibleAreParents = true;
				for (final AvailObject index2 : positiveTuple)
				{
					allPossibleAreParents = allPossibleAreParents &&
						imps.tupleAt(index2.extractInt())
							.bodySignature()
							.acceptsArgTypesFromClosureType(possibility);
				}
				possibleSolutionExists = allPossibleAreParents;
			}
		}
		if (!possibleSolutionExists)
		{
			result = ByteTupleDescriptor.mutableObjectOfSize(1);
			result.hashOrZero(0);
			result = result.tupleAtPuttingCanDestroy(
				1,
				IntegerDescriptor.fromInt(0 * 2 + 1),
				true);
			return result;
		}
		//  Compute a one-layer MinMax to find a good signature to check next.
		int bestIndex = 0;
		int bestMax = possibilities.tupleSize() + 2;
		for (final AvailObject index1 : possibilities)
		{
			possibility = imps.tupleAt(index1.extractInt());
			int trueCount = 0;
			int falseCount = 0;
			for (final AvailObject index2 : possibilities)
			{
				if (possibility.bodySignature().acceptsArgTypesFromClosureType(
					imps.tupleAt(index2.extractInt()).bodySignature()))
				{
					trueCount++;
				}
				else
				{
					falseCount++;
				}
			}
		final int maxCount = max(trueCount, falseCount);
			if (maxCount < bestMax)
			{
				bestMax = maxCount;
				bestIndex = index1.extractInt();
			}
		}
		// First recurse assuming the test came out true.  Move all ancestors of
		// what was tested into the positive collection and out of the
		// possibilities collection.  Also remove from the possibilities any
		// signatures that are strictly disjoint from the tested signature.  By
		// disjoint I mean that one or more arguments is terminates when the
		// intersection of the signatures is computed.
		AvailObject newPossible = possibilities.asSet();
		AvailObject newPositive = positiveTuple.asSet();
		for (final AvailObject index1 : possibilities)
		{
			possibility = imps.tupleAt(index1.extractInt());
			if (possibility.bodySignature().acceptsArgTypesFromClosureType(
				imps.tupleAt(bestIndex).bodySignature()))
			{
				newPositive = newPositive.setWithElementCanDestroy(
					index1,
					true);
				newPossible = newPossible.setWithoutElementCanDestroy(
					index1,
					true);
			}
			else
			{
				final AvailObject sig1 = possibility.bodySignature();
				final AvailObject sig2 =
					imps.tupleAt(bestIndex).bodySignature();
				for (
						int argIndex = 1, _end10 = sig1.numArgs();
						argIndex <= _end10;
						argIndex++)
				{
					if (sig1.argTypeAt(argIndex)
							.typeIntersection(sig2.argTypeAt(argIndex))
						.equals(TERMINATES.o()))
					{
						newPossible = newPossible.setWithoutElementCanDestroy(
							index1,
							true);
					}
				}
			}
		}
		final AvailObject trueTree = object
			.createTestingTreeWithPositiveMatchesRemainingPossibilities(
				newPositive.asTuple(),
				newPossible.asTuple());
		// Now recurse assuming the test came out false.  Remove all descendants
		// of the tested signature from the possibility collection.
		newPossible = possibilities.asSet();
		for (final AvailObject index1 : possibilities)
		{
			possibility = imps.tupleAt(index1.extractInt());
			if (imps.tupleAt(bestIndex).bodySignature()
				.acceptsArgTypesFromClosureType(
					possibility.bodySignature()))
			{
				newPossible = newPossible.setWithoutElementCanDestroy(
					index1,
					true);
			}
		}
		final AvailObject falseTree = object
			.createTestingTreeWithPositiveMatchesRemainingPossibilities(
				positiveTuple,
				newPossible.asTuple());
		//  Combine the subtrees together, preceded by a test-and-branch.
		final int newSize = 2 + trueTree.tupleSize() + falseTree.tupleSize();
		result = ByteTupleDescriptor.mutableObjectOfSize(newSize);
		result.hashOrZero(0);
		result = result.tupleAtPuttingCanDestroy(
			1,
			IntegerDescriptor.fromInt(bestIndex * 2),
			true);
		result = result.tupleAtPuttingCanDestroy(
			2,
			IntegerDescriptor.fromInt(trueTree.tupleSize()),
			true);
		for (int i = 1, _end12 = trueTree.tupleSize(); i <= _end12; i++)
		{
			result = result.tupleAtPuttingCanDestroy(
				(2 + i),
				trueTree.tupleAt(i),
				true);
		}
		for (int i = 1, _end13 = falseTree.tupleSize(); i <= _end13; i++)
		{
			result = result.tupleAtPuttingCanDestroy(
				(2 + trueTree.tupleSize() + i),
				falseTree.tupleAt(i),
				true);
		}
		return result;
	}

	/**
	 * Look up all method implementations that could match the given argument
	 * types.  Answer a {@link List list} of {@link
	 * MethodSignatureDescriptor method signatures}.
	 */
	@Override
	public List<AvailObject> o_FilterByTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		List<AvailObject> result;
		result = new ArrayList<AvailObject>(3);
		final AvailObject impsTuple = object.implementationsTuple();
		for (int i = 1, _end1 = impsTuple.tupleSize(); i <= _end1; i++)
		{
			final AvailObject imp = impsTuple.tupleAt(i);
			if (imp.bodySignature().acceptsArrayOfArgTypes(argTypes))
			{
				result.add(imp);
			}
		}
		return result;
	}

	/**
	 * Look up all method implementations that could match arguments with the
	 * given types, or anything more specific.  This should return the
	 * implementations that could be invoked at runtime at a call site with the
	 * given static types.  This set is subject to change as new methods and
	 * types are created.  If an argType and the corresponding argument type of
	 * an implementation have no possible descendant except terminates, then
	 * disallow the implementation (it could never actually be invoked because
	 * terminates is uninstantiable).  Answer a {@link List list} of {@link
	 * MethodSignatureDescriptor method signatures}.
	 * <p>
	 * Don't do coverage analysis yet (i.e., determining if one method would
	 * always override a strictly more abstract method).  We can do that some
	 * other day.
	 */
	@Override
	public List<AvailObject> o_ImplementationsAtOrBelow (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		List<AvailObject> result;
		result = new ArrayList<AvailObject>(3);
		final AvailObject impsTuple = object.implementationsTuple();
		for (int i = 1, _end1 = impsTuple.tupleSize(); i <= _end1; i++)
		{
			final AvailObject imp = impsTuple.tupleAt(i);
			if (imp.bodySignature().couldEverBeInvokedWith(argTypes))
			{
				result.add(imp);
			}
		}
		return result;
	}

	/**
	 * Test if the implementation is present.
	 */
	@Override
	public boolean o_Includes (
		final @NotNull AvailObject object,
		final @NotNull AvailObject imp)
	{
		for (final AvailObject signature : object.implementationsTuple())
		{
			if (signature.equals(imp))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Look up the implementation to invoke, given an array of argument types.
	 * Use the testingTree to find the implementation to invoke (answer void if
	 * a lookup error occurs).
	 */
	@Override
	public @NotNull AvailObject o_LookupByTypesFromList (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argumentTypeList)
	{
		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true) {
			int test = tree.tupleAt(index).extractInt();
		final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? VoidDescriptor.voidObject()
					: impsTuple.tupleAt(test);
			}
			if (impsTuple.tupleAt(test).bodySignature().acceptsArrayOfArgTypes(
				argumentTypeList))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleAt(index + 1).extractInt();
			}
		}
	}

	/**
	 * Assume the argument types have been pushed in the continuation.  The
	 * object at {@code stackp} is the last argument, and the object at {@code
	 * stackp + numArgs - 1} is the first.  Use the testingTree to find the
	 * implementation to invoke (answer void if a lookup error occurs).
	 */
	@Override
	public @NotNull AvailObject o_LookupByTypesFromContinuationStackp (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp)
	{
		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true) {
			int test = tree.tupleAt(index).extractInt();
		final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? VoidDescriptor.voidObject()
					: impsTuple.tupleAt(test);
			}
			if (impsTuple.tupleAt(test).bodySignature()
				.acceptsArgumentTypesFromContinuationStackp(
					continuation,
					stackp))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleAt(index + 1).extractInt();
			}
		}
	}

	/**
	 * Look up the implementation to invoke, given an array of argument types.
	 * Use the testingTree to find the implementation to invoke (answer void if
	 * a lookup error occurs).  There may be more entries in the tuple of
	 * argument types than we need, to allow the tuple to be a reusable buffer.
	 */
	@Override
	public @NotNull AvailObject o_LookupByTypesFromTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argumentTypeTuple)
	{
		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true) {
			int test = tree.tupleAt(index).extractInt();
		final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? VoidDescriptor.voidObject()
					: impsTuple.tupleAt(test);
			}
			if (impsTuple.tupleAt(test).bodySignature()
				.acceptsTupleOfArgTypes(argumentTypeTuple))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleAt(index + 1).extractInt();
			}
		}
	}

	/**
	 * Look up the implementation to invoke, given an array of argument values.
	 * Use the testingTree to find the implementation to invoke (answer void if
	 * a lookup error occurs).
	 */
	@Override
	public @NotNull AvailObject o_LookupByValuesFromList (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argumentList)
	{
		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true) {
			int test = tree.tupleAt(index).extractInt();
		final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? VoidDescriptor.voidObject()
					: impsTuple.tupleAt(test);
			}
			if (impsTuple.tupleAt(test).bodySignature()
					.acceptsArrayOfArgValues(argumentList))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleAt(index + 1).extractInt();
			}
		}
	}

	/**
	 * Look up the implementation to invoke, given a tuple of argument values.
	 * Use the testingTree to find the implementation to invoke (answer void if
	 * a lookup error occurs).  There may be more entries in the tuple of
	 * arguments than we're interested in (to allow the tuple to be a reusable
	 * buffer).
	 */
	@Override
	public @NotNull AvailObject o_LookupByValuesFromTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argumentTuple)
	{
		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true) {
			int test = tree.tupleAt(index).extractInt();
		final int lowBit = test & 1;
			test = test >>> 1;
			if (lowBit == 1)
			{
				return test == 0
					? VoidDescriptor.voidObject()
					: impsTuple.tupleAt(test);
			}
			if (impsTuple.tupleAt(test).bodySignature().acceptsTupleOfArguments(
				argumentTuple))
			{
				index += 2;
			}
			else
			{
				index = index + 2 + tree.tupleAt(index + 1).extractInt();
			}
		}
	}

	/**
	 * Remove the chunk from my set of dependent chunks.  This is probably
	 * because the chunk has been (A) removed by the garbage collector, or (B)
	 * invalidated by a new implementation in either me or another
	 * implementation set that the chunk is contingent on.
	 */
	@Override
	public void o_RemoveDependentChunkIndex (
		final @NotNull AvailObject object,
		final int aChunkIndex)
	{
		object.dependentChunkIndices(
			object.dependentChunkIndices().setWithoutElementCanDestroy(
				IntegerDescriptor.fromInt(aChunkIndex),
				true));
	}

	/**
	 * Remove the implementation from me.  Causes dependent chunks to be
	 * invalidated.
	 */
	@Override
	public void o_RemoveImplementation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject implementation)
	{
		AvailObject set = object.implementationsTuple().asSet();
		set = set.setWithoutElementCanDestroy(implementation, true);
		object.implementationsTuple(set.asTuple());
		membershipChanged(object);
	}

	/**
	 * Answers the return type.  Fails if no (or >1) applicable implementation.
	 */
	@Override
	public @NotNull AvailObject o_ValidateArgumentTypesInterpreterIfFail (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull Interpreter anAvailInterpreter,
		final @NotNull Continuation1<Generator<String>> failBlock)
	{
		final Mutable<List<AvailObject>> mostSpecific =
			new Mutable<List<AvailObject>>();
		for (int index = 1, _end1 = argTypes.size(); index <= _end1; index++)
		{
			final int finalIndex = index;
			if (argTypes.get(finalIndex - 1).equals(TERMINATES.o()))
			{
				failBlock.value(new Generator<String> ()
				{
					@Override
					public String value()
					{
						return "argument #"
							+ Integer.toString(finalIndex)
							+ " of message \""
							+ object.name().name().asNativeString()
							+ "\" to have a type other than terminates";
					}
				});
				return VoidDescriptor.voidObject();
			}
		}
		//  Filter the implementations down to those that are locally most
		//  specific.  Fail if more than one survives.
		final List<AvailObject> satisfyingTypes =
			object.filterByTypes(argTypes);
		if (satisfyingTypes.size() == 1)
		{
			mostSpecific.value = satisfyingTypes;
		}
		else
		{
			mostSpecific.value = new ArrayList<AvailObject>(2);
			for (final AvailObject imp : satisfyingTypes)
			{
				final AvailObject impType = imp.bodySignature();
				boolean isBest = true;
				for (final AvailObject other : satisfyingTypes)
				{
					if (isBest && !imp.equals(other))
					{
						final AvailObject otherType = other.bodySignature();
						for (
								int argIndex = impType.numArgs();
								argIndex >= 1;
								argIndex--)
						{
							isBest = isBest
								&& impType.argTypeAt(argIndex).isSubtypeOf(
									otherType.argTypeAt(argIndex));
						}
					}
				}
				if (isBest)
				{
					mostSpecific.value.add(imp);
				}
			}
		}
		if (mostSpecific.value.size() != 1)
		{
			failBlock.value(new Generator<String> ()
			{
				@Override
				public String value()
				{
					final AvailObject implementationsTuple =
						object.implementationsTuple();
					List<AvailObject> signatures;
					signatures = new ArrayList<AvailObject>(2);
					for (final AvailObject imp : implementationsTuple)
					{
						signatures.add(imp.bodySignature());
					}
					String string;
					if (mostSpecific.value.size() == 0)
					{
						List<Integer> allFailedIndices;
						allFailedIndices = new ArrayList<Integer>(3);
						for (int index = argTypes.size(); index >= 1; index--)
						{
							boolean any = false;
							for (final AvailObject signature : signatures)
							{
								if (!any)
								{
									if (argTypes.get(index - 1).isSubtypeOf(
										signature.argTypeAt(index)))
									{
										any = true;
									}
								}
							}
							if (any)
							{
								allFailedIndices.add(0, index);
							}
						}
						if (allFailedIndices.size() >= 1
								&& allFailedIndices.size()
									<= argTypes.size() - 1)
						{
							string = "arguments at indices "
								+ allFailedIndices.toString()
								+ " of message "
								+ object.name().name().asNativeString()
								+ " to match a method.  I got: "
								+ argTypes.toString();
						}
						else
						{
							string = "arguments of "
							+ object.name().name().asNativeString()
							+ " to have applicable types like "
							+ signatures.toString()
							+ ", ***not*** "
							+ argTypes.toString();
						}
					}
					else
					{
						string = "arguments of "
							+ object.name().name().asNativeString()
							+ " to unambiguously select method.  Choices are: "
							+ signatures.toString();
					}
					return string;
				}
			});
			return VoidDescriptor.voidObject();
		}
		// The requires clauses are only checked after a top-level statement has
		// been parsed and is being validated.
		return mostSpecific.value.get(0).computeReturnTypeFromArgumentTypes(
			argTypes,
			anAvailInterpreter);
	}

	/**
	 * Answer how many arguments my implementations require.
	 */
	@Override
	public int o_NumArgs (
		final @NotNull AvailObject object)
	{
		assert object.implementationsTuple().tupleSize() >= 1;
		final AvailObject first = object.implementationsTuple().tupleAt(1);
		return first.bodySignature().numArgs();
	}

	/**
	 * Answer the cached privateTestingTree.  If there's a voidObject in that
	 * slot, compute the testing tree based on implementationsSet.  The tree is
	 * flattened into a tuple of integers.  Testing begins with the first
	 * element of the tuple.  If it's odd, divide by two to get the index into
	 * implementationsTuple (a zero index indicates an ambiguous lookup).  If
	 * it's even, divide by two to get an index into implementationsTuple, then
	 * test the list of arguments against it.  If the arguments agree with the
	 * signature, add 2 to the current position (to skip the test number and an
	 * offset) and continue.  If the arguments did not agree with the signature,
	 * add 2 + the value in the next slot of the tuple to the current position,
	 * then continue.  We use a simple one-layer MinMax algorithm to produce a
	 * reasonable testing tree, where the choice of signature to test for is the
	 * one that minimizes the maximum number of remaining possible solutions
	 * after a test.
	 */
	@Override
	public @NotNull AvailObject o_TestingTree (
		final @NotNull AvailObject object)
	{
		AvailObject result = object.privateTestingTree();
		if (!result.equalsVoid())
		{
			return result;
		}
		//  Compute the tree.
		final int indicesSize = object.implementationsTuple().tupleSize();
		AvailObject indices =
			ByteTupleDescriptor.mutableObjectOfSize(indicesSize);
		for (int i = 1; i <= indicesSize; i++)
		{
			indices = indices.tupleAtPuttingCanDestroy(
				i,
				IntegerDescriptor.fromInt(i),
				true);
		}
		result = object.createTestingTreeWithPositiveMatchesRemainingPossibilities(
			TupleDescriptor.empty(),
			indices);
		object.privateTestingTree(result);
		return result;
	}

	/**
	 * Answer a new implementation set.  Use the passed cyclicType as its name.
	 * An implementation set is always immutable, but its implementationsTuple,
	 * privateTestingTree, and dependentsChunks can all be assigned to.
	 *
	 * @param messageName The {@link CyclicTypeDescriptor cyclic type}
	 *                    acting as the message name.
	 * @return A new {@link ImplementationSetDescriptor implementation set}.
	 */
	public static AvailObject newImplementationSetWithName (
		final AvailObject messageName)
	{
		assert messageName.isCyclicType();
		final AvailObject result = mutable().create();
		result.implementationsTuple(TupleDescriptor.empty());
		result.privateTestingTree(TupleDescriptor.empty());
		result.dependentChunkIndices(SetDescriptor.empty());
		result.name(messageName);
		result.makeImmutable();
		return result;
	};

	/**
	 * The membership of this {@linkplain ImplementationSetDescriptor
	 * implementation set} has changed.  Invalidate anything that depended on
	 * the previous membership, including the {@linkplain
	 * ObjectSlots#PRIVATE_TESTING_TREE testing tree} and any dependent level
	 * two chunks.
	 *
	 * @param object The implementation set that changed.
	 */
	private static void membershipChanged (
		final AvailObject object)
	{
		// Invalidate any affected level two chunks.
		final AvailObject chunkIndices = object.dependentChunkIndices();
		if (chunkIndices.setSize() > 0)
		{
			for (AvailObject chunkIndex : chunkIndices.asTuple())
			{
				L2ChunkDescriptor.invalidateChunkAtIndex(
					chunkIndex.extractInt());
			}
			assert object.dependentChunkIndices().setSize() == 0;
		}
		// Clear the privateTestingTree cache.
		object.privateTestingTree(VoidDescriptor.voidObject());
	}

	/**
	 * Construct a new {@link ImplementationSetDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ImplementationSetDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ImplementationSetDescriptor}.
	 */
	private final static ImplementationSetDescriptor mutable =
		new ImplementationSetDescriptor(true);

	/**
	 * Answer the mutable {@link ImplementationSetDescriptor}.
	 *
	 * @return The mutable {@link ImplementationSetDescriptor}.
	 */
	public static ImplementationSetDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ImplementationSetDescriptor}.
	 */
	private final static ImplementationSetDescriptor immutable =
		new ImplementationSetDescriptor(false);

	/**
	 * Answer the immutable {@link ImplementationSetDescriptor}.
	 *
	 * @return The immutable {@link ImplementationSetDescriptor}.
	 */
	public static ImplementationSetDescriptor immutable ()
	{
		return immutable;
	}
}
