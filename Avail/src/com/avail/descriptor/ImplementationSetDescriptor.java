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

import com.avail.compiler.Continuation1;
import com.avail.compiler.Generator;
import com.avail.compiler.Mutable;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ByteTupleDescriptor;
import com.avail.descriptor.ImplementationSetDescriptor;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.L2ChunkDescriptor;
import com.avail.descriptor.SetDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.AvailInterpreter;
import java.util.ArrayList;
import java.util.List;
import static java.lang.Math.*;

@ObjectSlots({
	"implementationsTuple", 
	"privateTestingTree", 
	"dependentChunks", 
	"name"
})
public class ImplementationSetDescriptor extends Descriptor
{


	// GENERATED accessors

	void ObjectDependentChunks (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-12, value);
	}

	void ObjectImplementationsTuple (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	void ObjectName (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-16, value);
	}

	void ObjectPrivateTestingTree (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-8, value);
	}

	AvailObject ObjectDependentChunks (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-12);
	}

	AvailObject ObjectImplementationsTuple (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}

	AvailObject ObjectName (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-16);
	}

	AvailObject ObjectPrivateTestingTree (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-8);
	}



	// GENERATED special mutable slots

	boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if ((index == -4))
		{
			return true;
		}
		if ((index == -8))
		{
			return true;
		}
		if ((index == -12))
		{
			return true;
		}
		return false;
	}



	// java printing

	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		final int size = object.implementationsTuple().tupleSize();
		aStream.append(Integer.toString(size));
		aStream.append(" implementations of ");
		aStream.append(object.name().name().asNativeString());
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		//  ImplementationSets compare by identity.

		return another.traversed().sameAddressAs(object);
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		return TypeDescriptor.implementationSet();
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value.  The hash can just depend on
		//  the name (a cyclicType), because the name's hash is random and permanent,
		//  and an implementationSet's name is permanent, too.

		return ((object.name().hash() + 0x61AF3FC) & HashMask);
	}

	AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.  If I was mutable I have to
		//  scan my children and make them immutable as well (recursively down to immutable
		//  descendants).  Actually, I allow my implementationsTuple, my privateTestingTree,
		//  and my dependentsChunks slots to be mutable even when I'm immutable.

		object.descriptor(ImplementationSetDescriptor.immutableDescriptor());
		object.name().makeImmutable();
		//  Don't bother scanning implementationsTuple, privateTestingTree and dependentChunks.
		//  They're allowed to be mutable even when object is immutable.
		return object;
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return TypeDescriptor.implementationSet();
	}



	// operations-implementation sets

	void ObjectAddDependentChunkId (
			final AvailObject object, 
			final int aChunkIndex)
	{
		//  Record the fact that the chunk indexed by aChunkIndex depends on
		//  this implementationSet not changing.

		object.dependentChunks(object.dependentChunks().setWithElementCanDestroy(IntegerDescriptor.objectFromInt(aChunkIndex), true));
	}

	void ObjectAddImplementation (
			final AvailObject object, 
			final AvailObject implementation)
	{
		//  Add the implementation to me.  Causes dependent chunks to be invalidated.

		object.implementationsTuple(object.implementationsTuple().asSet().setWithElementCanDestroy(implementation, true).asTuple());
		final AvailObject chunks = object.dependentChunks();
		if ((chunks.setSize() > 0))
		{
			final AvailObject chunksAsTuple = chunks.asTuple();
			for (int i = 1, _end1 = chunksAsTuple.tupleSize(); i <= _end1; i++)
			{
				final AvailObject chunkId = chunksAsTuple.tupleAt(i);
				L2ChunkDescriptor.chunkFromId(chunkId.extractInt()).necessaryImplementationSetChanged(object);
			}
			assert chunks.traversed().sameAddressAs(object.dependentChunks().traversed()) : "dependentChunks must not change shape during invalidation loop";
			object.dependentChunks(SetDescriptor.empty());
		}
		//  Clear the privateTestingTree cache.
		object.privateTestingTree(VoidDescriptor.voidObject());
	}

	AvailObject ObjectCreateTestingTreeWithPositiveMatchesRemainingPossibilities (
			final AvailObject object, 
			final AvailObject positiveTuple, 
			final AvailObject possibilities)
	{
		//  Create the testing tree for computing which implementation to invoke when given
		//  a list of arguments.  The tree is flattened into a tuple of integers.  Testing begins
		//  with the first element of the tuple.  If it's odd, divide by two to get the index into
		//  implementationsTuple (a zero index indicates an ambiguous lookup).  If it's even,
		//  divide by two to get an index into implementationsTuple, then test the list of
		//  arguments against it.  If the arguments agree with the signature, add 2 to the
		//  current position (to skip the test number and an offset) and continue.  If the
		//  arguments did not agree with the signature, add 2 + the value in the next slot of
		//  the tuple to the current position, then continue.
		//  We use a simple one-layer MinMax algorithm to produce a reasonable testing
		//  tree, where the choice of signature to test for is the one that minimizes the
		//  maximum number of remaining possible solutions after a test.

		final AvailObject imps = object.implementationsTuple();
		AvailObject result;
		if ((possibilities.tupleSize() == 0))
		{
			for (int i = 1, _end1 = positiveTuple.tupleSize(); i <= _end1; i++)
			{
				boolean all = true;
				for (int k = 1, _end2 = positiveTuple.tupleSize(); k <= _end2; k++)
				{
					all = (all && imps.tupleAt(positiveTuple.tupleAt(k).extractInt()).bodySignature().acceptsArgTypesFromClosureType(imps.tupleAt(positiveTuple.tupleAt(i).extractInt()).bodySignature()));
				}
				if (all)
				{
					result = AvailObject.newIndexedDescriptor(1, ByteTupleDescriptor.isMutableSize(true, 1));
					result.hashOrZero(0);
					result = result.tupleAtPuttingCanDestroy(
						1,
						IntegerDescriptor.objectFromInt(((positiveTuple.tupleAt(i).extractInt() * 2) + 1)),
						true);
					return result;
				}
			}
			//  There was no most specific positive signature.  Indicate an ambiguity error at this
			//  point in the tree.
			result = AvailObject.newIndexedDescriptor(1, ByteTupleDescriptor.isMutableSize(true, 1));
			result.hashOrZero(0);
			result = result.tupleAtPuttingCanDestroy(
				1,
				IntegerDescriptor.objectFromInt(((0 * 2) + 1)),
				true);
			return result;
		}
		//  See if there are any solutions still possible.  Scan the list of possibilities (and known positives),
		//  and for each one see if it's more specific than everything in the positive collection.  If there are
		//  no such solutions, we are already at a point that represents an ambiguous lookup.
		boolean possibleSolutionExists = false;
		AvailObject possibility;
		for (int i = 1, _end3 = possibilities.tupleSize(); i <= _end3; i++)
		{
			if (! possibleSolutionExists)
			{
				possibility = imps.tupleAt(possibilities.tupleAt(i).extractInt()).bodySignature();
				boolean allPossibleAreParents = true;
				for (int k = 1, _end4 = positiveTuple.tupleSize(); k <= _end4; k++)
				{
					allPossibleAreParents = (allPossibleAreParents && imps.tupleAt(positiveTuple.tupleAt(k).extractInt()).bodySignature().acceptsArgTypesFromClosureType(possibility));
				}
				possibleSolutionExists = allPossibleAreParents;
			}
		}
		for (int i = 1, _end5 = positiveTuple.tupleSize(); i <= _end5; i++)
		{
			if (! possibleSolutionExists)
			{
				possibility = imps.tupleAt(positiveTuple.tupleAt(i).extractInt()).bodySignature();
				boolean allPossibleAreParents = true;
				for (int k = 1, _end6 = positiveTuple.tupleSize(); k <= _end6; k++)
				{
					allPossibleAreParents = (allPossibleAreParents && imps.tupleAt(positiveTuple.tupleAt(k).extractInt()).bodySignature().acceptsArgTypesFromClosureType(possibility));
				}
				possibleSolutionExists = allPossibleAreParents;
			}
		}
		if (! possibleSolutionExists)
		{
			result = AvailObject.newIndexedDescriptor(1, ByteTupleDescriptor.isMutableSize(true, 1));
			result.hashOrZero(0);
			result = result.tupleAtPuttingCanDestroy(
				1,
				IntegerDescriptor.objectFromInt(((0 * 2) + 1)),
				true);
			return result;
		}
		//  Compute a one-layer MinMax to find a good signature to check next.
		int bestIndex = 0;
		int bestMax = (possibilities.tupleSize() + 2);
		for (int i = 1, _end7 = possibilities.tupleSize(); i <= _end7; i++)
		{
			possibility = imps.tupleAt(possibilities.tupleAt(i).extractInt());
			int trueCount = 0;
			int falseCount = 0;
			for (int k = 1, _end8 = possibilities.tupleSize(); k <= _end8; k++)
			{
				if (possibility.bodySignature().acceptsArgTypesFromClosureType(imps.tupleAt(possibilities.tupleAt(k).extractInt()).bodySignature()))
				{
					++trueCount;
				}
				else
				{
					++falseCount;
				}
			}
			final int maxCount = max (trueCount, falseCount);
			if ((maxCount < bestMax))
			{
				bestMax = maxCount;
				bestIndex = possibilities.tupleAt(i).extractInt();
			}
		}
		//  Ok, first recurse assuming the test came out true.  Move all ancestors of what was
		//  tested into the positive collection and out of the possibilities collection.  Also remove
		//  from the possibilities any signatures that are strictly disjoint from the tested signature.
		//  By disjoint I mean that one or more arguments is terminates when the intersection of
		//  the signatures is computed.
		AvailObject newPossible = possibilities.asSet();
		AvailObject newPositive = positiveTuple.asSet();
		for (int i = 1, _end9 = possibilities.tupleSize(); i <= _end9; i++)
		{
			AvailObject boxedIndex = possibilities.tupleAt(i);
			possibility = imps.tupleAt(boxedIndex.extractInt());
			if (possibility.bodySignature().acceptsArgTypesFromClosureType(imps.tupleAt(bestIndex).bodySignature()))
			{
				newPositive = newPositive.setWithElementCanDestroy(boxedIndex, true);
				newPossible = newPossible.setWithoutElementCanDestroy(boxedIndex, true);
			}
			else
			{
				final AvailObject sig1 = possibility.bodySignature();
				final AvailObject sig2 = imps.tupleAt(bestIndex).bodySignature();
				for (int argIndex = 1, _end10 = sig1.numArgs(); argIndex <= _end10; argIndex++)
				{
					if (sig1.argTypeAt(argIndex).typeIntersection(sig2.argTypeAt(argIndex)).equals(TypeDescriptor.terminates()))
					{
						newPossible = newPossible.setWithoutElementCanDestroy(boxedIndex, true);
					}
				}
			}
		}
		final AvailObject trueTree = object.createTestingTreeWithPositiveMatchesRemainingPossibilities(newPositive.asTuple(), newPossible.asTuple());
		//  Now recurse assuming the test came out false.  Remove all descendants of the
		//  tested signature from the possibility collection.
		newPossible = possibilities.asSet();
		for (int i = 1, _end11 = possibilities.tupleSize(); i <= _end11; i++)
		{
			AvailObject boxedIndex = possibilities.tupleAt(i);
			possibility = imps.tupleAt(boxedIndex.extractInt());
			if (imps.tupleAt(bestIndex).bodySignature().acceptsArgTypesFromClosureType(possibility.bodySignature()))
			{
				newPossible = newPossible.setWithoutElementCanDestroy(boxedIndex, true);
			}
		}
		final AvailObject falseTree = object.createTestingTreeWithPositiveMatchesRemainingPossibilities(positiveTuple, newPossible.asTuple());
		//  Combine the subtrees together, preceded by a test-and-branch.
		final int newSize = ((2 + trueTree.tupleSize()) + falseTree.tupleSize());
		result = ByteTupleDescriptor.isMutableSize(true, newSize).privateMutableObjectOfSize(newSize);
		result.hashOrZero(0);
		result = result.tupleAtPuttingCanDestroy(
			1,
			IntegerDescriptor.objectFromInt((bestIndex * 2)),
			true);
		result = result.tupleAtPuttingCanDestroy(
			2,
			IntegerDescriptor.objectFromInt(trueTree.tupleSize()),
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
				((2 + trueTree.tupleSize()) + i),
				falseTree.tupleAt(i),
				true);
		}
		return result;
	}

	List<AvailObject> ObjectFilterByTypes (
			final AvailObject object, 
			final List<AvailObject> argTypes)
	{
		//  Look up all method implementations that could match the given argument types.
		//  Answer an OrderedCollection (for now).

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

	ArrayList<AvailObject> ObjectImplementationsAtOrBelow (
			final AvailObject object, 
			final ArrayList<AvailObject> argTypes)
	{
		//  Look up all method implementations that could match arguments with the given types,
		//  or anything more specific.  This should return the implementations that could be invoked
		//  at runtime at a call site with the given static types.  This set is subject to change as new
		//  methods and types are created.  If an argType and the corresponding argument type of
		//  an implementation have no possible descendant except terminates, then disallow the
		//  implementation (it could never actually be invoked because terminates is uninstantiable).
		//  Answer an OrderedCollection (for now).
		//
		//  Don't do coverage analysis yet (i.e., determining if one method would always override
		//  a strictly more abstract method).  We can do that some other day.

		ArrayList<AvailObject> result;
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

	boolean ObjectIncludes (
			final AvailObject object, 
			final AvailObject imp)
	{
		//  Test if the implementation is present.

		final AvailObject tuple = object.implementationsTuple();
		for (int i = 1, _end1 = tuple.tupleSize(); i <= _end1; i++)
		{
			if (tuple.tupleAt(i).equals(imp))
			{
				return true;
			}
		}
		return false;
	}

	AvailObject ObjectLookupByTypesFromArray (
			final AvailObject object, 
			final List<AvailObject> argumentTypeArray)
	{
		//  Look up the implementation to invoke, given an array of argument types.
		//  Use the testingTree to find the implementation to invoke (answer void
		//  if a lookup error occurs).

		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true) {
			int test = tree.tupleAt(index).extractInt();
			final int lowBit = (test & 1);
			test = (test >>> 1);
			if ((lowBit == 1))
			{
				return ((test == 0) ? VoidDescriptor.voidObject() : impsTuple.tupleAt(test));
			}
			if (impsTuple.tupleAt(test).bodySignature().acceptsArrayOfArgTypes(argumentTypeArray))
			{
				index += 2;
			}
			else
			{
				index = ((index + 2) + tree.tupleAt((index + 1)).extractInt());
			}
		}
	}

	AvailObject ObjectLookupByTypesFromContinuationStackp (
			final AvailObject object, 
			final AvailObject continuation, 
			final int stackp)
	{
		//  Assume the argument types have been pushed in the continuation.  The object
		//  at stackp is the last argument, and the object at stackp + numArgs - 1 is the
		//  first.  Use the testingTree to find the implementation to invoke (answer void
		//  if a lookup error occurs).

		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true) {
			int test = tree.tupleAt(index).extractInt();
			final int lowBit = (test & 1);
			test = (test >>> 1);
			if ((lowBit == 1))
			{
				return ((test == 0) ? VoidDescriptor.voidObject() : impsTuple.tupleAt(test));
			}
			if (impsTuple.tupleAt(test).bodySignature().acceptsArgumentTypesFromContinuationStackp(continuation, stackp))
			{
				index += 2;
			}
			else
			{
				index = ((index + 2) + tree.tupleAt((index + 1)).extractInt());
			}
		}
	}

	AvailObject ObjectLookupByTypesFromTuple (
			final AvailObject object, 
			final AvailObject argumentTypeTuple)
	{
		//  Look up the implementation to invoke, given an array of argument types.
		//  Use the testingTree to find the implementation to invoke (answer void
		//  if a lookup error occurs).  There may be more entries in the tuple of
		//  argument types than we need, to allow the tuple to be a reuseable buffer.

		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true) {
			int test = tree.tupleAt(index).extractInt();
			final int lowBit = (test & 1);
			test = (test >>> 1);
			if ((lowBit == 1))
			{
				return ((test == 0) ? VoidDescriptor.voidObject() : impsTuple.tupleAt(test));
			}
			if (impsTuple.tupleAt(test).bodySignature().acceptsTupleOfArgTypes(argumentTypeTuple))
			{
				index += 2;
			}
			else
			{
				index = ((index + 2) + tree.tupleAt((index + 1)).extractInt());
			}
		}
	}

	AvailObject ObjectLookupByValuesFromArray (
			final AvailObject object, 
			final List<AvailObject> argumentArray)
	{
		//  Look up the implementation to invoke, given an array of argument values.
		//  Use the testingTree to find the implementation to invoke (answer void
		//  if a lookup error occurs).

		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true) {
			int test = tree.tupleAt(index).extractInt();
			final int lowBit = (test & 1);
			test = (test >>> 1);
			if ((lowBit == 1))
			{
				return ((test == 0) ? VoidDescriptor.voidObject() : impsTuple.tupleAt(test));
			}
			if (impsTuple.tupleAt(test).bodySignature().acceptsArrayOfArgValues(argumentArray))
			{
				index += 2;
			}
			else
			{
				index = ((index + 2) + tree.tupleAt((index + 1)).extractInt());
			}
		}
	}

	AvailObject ObjectLookupByValuesFromContinuationStackp (
			final AvailObject object, 
			final AvailObject continuation, 
			final int stackp)
	{
		//  Assume the arguments have been pushed in the continuation.  The object at
		//  stackp is the last argument, and the object at stackp + numArgs - 1 is the
		//  first.  Use the testingTree to find the implementation to invoke (answer void
		//  if a lookup error occurs).

		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true) {
			int test = tree.tupleAt(index).extractInt();
			final int lowBit = (test & 1);
			test = (test >>> 1);
			if ((lowBit == 1))
			{
				return ((test == 0) ? VoidDescriptor.voidObject() : impsTuple.tupleAt(test));
			}
			if (impsTuple.tupleAt(test).bodySignature().acceptsArgumentsFromContinuationStackp(continuation, stackp))
			{
				index += 2;
			}
			else
			{
				index = ((index + 2) + tree.tupleAt((index + 1)).extractInt());
			}
		}
	}

	AvailObject ObjectLookupByValuesFromTuple (
			final AvailObject object, 
			final AvailObject argumentTuple)
	{
		//  Look up the implementation to invoke, given a tuple of argument values.
		//  Use the testingTree to find the implementation to invoke (answer void
		//  if a lookup error occurs).  There may be more entries in the tuple of
		//  arguments than we're interested in (to allow the tuple to be a reuseable
		//  buffer).

		final AvailObject impsTuple = object.implementationsTuple();
		final AvailObject tree = object.testingTree();
		int index = 1;
		while (true) {
			int test = tree.tupleAt(index).extractInt();
			final int lowBit = (test & 1);
			test = (test >>> 1);
			if ((lowBit == 1))
			{
				return ((test == 0) ? VoidDescriptor.voidObject() : impsTuple.tupleAt(test));
			}
			if (impsTuple.tupleAt(test).bodySignature().acceptsTupleOfArguments(argumentTuple))
			{
				index += 2;
			}
			else
			{
				index = ((index + 2) + tree.tupleAt((index + 1)).extractInt());
			}
		}
	}

	void ObjectRemoveDependentChunkId (
			final AvailObject object, 
			final int aChunkIndex)
	{
		//  Remove the chunk from my set of dependent chunks.  This is probably because
		//  the chunk has been (A) removed by the garbage collector, or (B) invalidated by a
		//  new implementation in either me or another implementation set that the chunk is
		//  contingent on.

		object.dependentChunks(object.dependentChunks().setWithoutElementCanDestroy(IntegerDescriptor.objectFromInt(aChunkIndex), true));
	}

	void ObjectRemoveImplementation (
			final AvailObject object, 
			final AvailObject implementation)
	{
		//  Remove the implementation from me.  Causes dependent chunks to be invalidated.

		object.implementationsTuple(object.implementationsTuple().asSet().setWithoutElementCanDestroy(implementation, true).asTuple());
		final AvailObject chunks = object.dependentChunks();
		if ((chunks.setSize() > 0))
		{
			final AvailObject chunksAsTuple = chunks.asTuple();
			for (int i = 1, _end1 = chunksAsTuple.tupleSize(); i <= _end1; i++)
			{
				final AvailObject chunkId = chunksAsTuple.tupleAt(i);
				L2ChunkDescriptor.chunkFromId(chunkId.extractInt()).necessaryImplementationSetChanged(object);
			}
			assert chunks.traversed().sameAddressAs(object.dependentChunks().traversed()) : "dependentChunks must not change shape during invalidation loop";
			object.dependentChunks(SetDescriptor.empty());
		}
		//  Clear the privateTestingTree cache.
		object.privateTestingTree(VoidDescriptor.voidObject());
	}

	AvailObject ObjectValidateArgumentTypesInterpreterIfFail (
			final AvailObject object, 
			final List<AvailObject> argTypes, 
			final AvailInterpreter anAvailInterpreter, 
			final Continuation1<Generator<String>> failBlock)
	{
		//  Answers the return type.  Fails if no (or >1) applicable implementation.

		final Mutable<List<AvailObject>> mostSpecific = new Mutable<List<AvailObject>>();
		for (int index = 1, _end1 = argTypes.size(); index <= _end1; index++)
		{
			final int finalIndex = index;
			if (argTypes.get((finalIndex - 1)).equals(TypeDescriptor.terminates()))
			{
				failBlock.value(new Generator<String> ()
				{
					public String value()
					{
						return "argument #" + Integer.toString(finalIndex) + " of message \"" + object.name().name().asNativeString() + "\" to have a type other than terminates";
					}
				});
				return VoidDescriptor.voidObject();
			}
		}
		//  Filter the implementations down to those that are locally most
		//  specific.  Fail if more than one survives.
		final List<AvailObject> satisfyingTypes = object.filterByTypes(argTypes);
		if ((satisfyingTypes.size() == 1))
		{
			mostSpecific.value = satisfyingTypes;
		}
		else
		{
			mostSpecific.value = new ArrayList<AvailObject>(2);
			for (int typeIndex = 1, _end2 = satisfyingTypes.size(); typeIndex <= _end2; typeIndex++)
			{
				final AvailObject imp = satisfyingTypes.get((typeIndex - 1));
				final AvailObject impType = imp.bodySignature();
				boolean isBest = true;
				for (int otherIndex = 1, _end3 = satisfyingTypes.size(); otherIndex <= _end3; otherIndex++)
				{
					final AvailObject other = satisfyingTypes.get((otherIndex - 1));
					if ((isBest && (! imp.equals(other))))
					{
						final AvailObject otherType = other.bodySignature();
						for (int argIndex = 1, _end4 = impType.numArgs(); argIndex <= _end4; argIndex++)
						{
							isBest = (isBest && impType.argTypeAt(argIndex).isSubtypeOf(otherType.argTypeAt(argIndex)));
						}
					}
				}
				if (isBest)
				{
					mostSpecific.value.add(imp);
				}
			}
		}
		if ((mostSpecific.value.size() != 1))
		{
			failBlock.value(new Generator<String> ()
			{
				public String value()
				{
					final AvailObject implementationsTuple = object.implementationsTuple();
					List<AvailObject> signatures;
					signatures = new ArrayList<AvailObject>(2);
					for (int i = 1, _end5 = implementationsTuple.tupleSize(); i <= _end5; i++)
					{
						signatures.add(implementationsTuple.tupleAt(i).bodySignature());
					}
					String string;
					if ((mostSpecific.value.size() == 0))
					{
						List<Integer> allFailedIndices;
						allFailedIndices = new ArrayList<Integer>(3);
						for (int index = 1, _end6 = argTypes.size(); index <= _end6; index++)
						{
							boolean any = false;
							for (int signatureIndex = 1, _end7 = signatures.size(); signatureIndex <= _end7; signatureIndex++)
							{
								if (! any)
								{
									if (argTypes.get((index - 1)).isSubtypeOf(signatures.get((signatureIndex - 1)).argTypeAt(index)))
									{
										any = true;
									}
								}
							}
							if (any)
							{
								allFailedIndices.add(index);
							}
						}
						if (((allFailedIndices.size() >= 1) && (allFailedIndices.size() <= (argTypes.size() - 1))))
						{
							string = "arguments at indices " + allFailedIndices.toString() + " of message " + object.name().name().asNativeString() + " to match a method.  I got: " + argTypes.toString();
						}
						else
						{
							string = "arguments of " + object.name().name().asNativeString() + " to have applicable types like " + signatures.toString() + ", ***not*** " + argTypes.toString();
						}
					}
					else
					{
						string = "arguments of " + object.name().name().asNativeString() + " to unambiguously select method.  Choices are: " + signatures.toString();
					}
					return string;
				}
			});
			return VoidDescriptor.voidObject();
		}
		//  The requires clauses are only checked after a top-level statement has been parsed and is being validated.
		return mostSpecific.value.get(0).computeReturnTypeFromArgumentTypesInterpreter(argTypes, anAvailInterpreter);
	}

	short ObjectNumArgs (
			final AvailObject object)
	{
		//  Answer how many arguments my implementations require.

		assert (object.implementationsTuple().tupleSize() >= 1);
		return object.implementationsTuple().tupleAt(1).bodySignature().numArgs();
	}

	AvailObject ObjectTestingTree (
			final AvailObject object)
	{
		//  Answer the cached privateTestingTree.  If there's a voidObject in that slot,
		//  compute the testing tree based on implementationsSet.  The tree is flattened
		//  into a tuple of integers.  Testing begins with the first element of the tuple.  If
		//  it's odd, divide by two to get the index into implementationsTuple (a zero index
		//  indicates an ambiguous lookup).  If it's even, divide by two to get an index into
		//  implementationsTuple, then test the list of arguments against it.  If the arguments
		//  agree with the signature, add 2 to the current position (to skip the test number
		//  and an offset) and continue.  If the arguments did not agree with the signature,
		//  add 2 + the value in the next slot of the tuple to the current position, then continue.
		//  We use a simple one-layer MinMax algorithm to produce a reasonable testing
		//  tree, where the choice of signature to test for is the one that minimizes the
		//  maximum number of remaining possible solutions after a test.

		AvailObject result = object.privateTestingTree();
		if (! result.equalsVoid())
		{
			return result;
		}
		//  Compute the tree.
		final int indicesSize = object.implementationsTuple().tupleSize();
		AvailObject indices = ByteTupleDescriptor.isMutableSize(true, indicesSize).privateMutableObjectOfSize(indicesSize);
		for (int i = 1; i <= indicesSize; i++)
		{
			indices = indices.tupleAtPuttingCanDestroy(
				i,
				IntegerDescriptor.objectFromInt(i),
				true);
		}
		result = object.createTestingTreeWithPositiveMatchesRemainingPossibilities(TupleDescriptor.empty(), indices);
		object.privateTestingTree(result);
		return result;
	}





	/* Object creation */
	public static AvailObject newImplementationSetWithName (AvailObject methodName)
	{
		// Answer a new implementation set.  Use the passed cyclicType as its name.
		// An implementation set is always immutable, but its implementationsTuple,
		// privateTestingTree, and dependentsChunks can can all be assigned to.

		assert methodName.isCyclicType();
		AvailObject result = AvailObject.newIndexedDescriptor(0, mutableDescriptor());
		result.implementationsTuple(TupleDescriptor.empty());
		result.privateTestingTree(TupleDescriptor.empty());
		result.dependentChunks(SetDescriptor.empty());
		result.name(methodName);
		result.makeImmutable();
		return result;
	};


	/* Descriptor lookup */
	public static ImplementationSetDescriptor mutableDescriptor()
	{
		return (ImplementationSetDescriptor) AllDescriptors [72];
	};
	public static ImplementationSetDescriptor immutableDescriptor()
	{
		return (ImplementationSetDescriptor) AllDescriptors [73];
	};

}
