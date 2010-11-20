/**
 * descriptor/AvailObject.java
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.compiler.Continuation1;
import com.avail.compiler.Generator;
import com.avail.interpreter.AvailInterpreter;
import com.avail.visitor.AvailMarkUnreachableSubobjectVisitor;
import com.avail.visitor.AvailSubobjectVisitor;

public abstract class AvailObject
implements Iterable<AvailObject>
{
	// gc helpers

	public AvailObject saveOrForward ()
	{
		//  The object is in FromSpace.  If its slotsSize is >= 32768, it represents a forwarding
		//  pointer into ToSpace (and the pointer is in the first slot).  Otherwise, save the
		//  object as per GCReadBarrierDescriptor class>>documentation.

		error("Subclass responsibility: saveOrForward in Avail.AvailObject");
		return VoidDescriptor.voidObject();
	}



	// GENERATED methods

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsArgTypesFromClosureType (
		final AvailObject closureType)
	{
		return descriptor().ObjectAcceptsArgTypesFromClosureType(this, closureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsArgumentsFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		return descriptor().ObjectAcceptsArgumentsFromContinuationStackp(
			this,
			continuation,
			stackp);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsArgumentTypesFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		return descriptor().ObjectAcceptsArgumentTypesFromContinuationStackp(
			this,
			continuation,
			stackp);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsArrayOfArgTypes (
		final List<AvailObject> argTypes)
	{
		return descriptor().ObjectAcceptsArrayOfArgTypes(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsArrayOfArgValues (
		final List<AvailObject> argValues)
	{
		return descriptor().ObjectAcceptsArrayOfArgValues(this, argValues);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsTupleOfArgTypes (
		final AvailObject argTypes)
	{
		return descriptor().ObjectAcceptsTupleOfArgTypes(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsTupleOfArguments (
		final AvailObject arguments)
	{
		return descriptor().ObjectAcceptsTupleOfArguments(this, arguments);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void addDependentChunkId (
		final int aChunkIndex)
	{
		descriptor().ObjectAddDependentChunkId(this, aChunkIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void addImplementation (
		final AvailObject implementation)
	{
		descriptor().ObjectAddImplementation(this, implementation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void addRestrictions (
		final AvailObject restrictions)
	{
		descriptor().ObjectAddRestrictions(this, restrictions);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject addToInfinityCanDestroy (
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		return descriptor().ObjectAddToInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject addToIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor().ObjectAddToIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void argsLocalsStackOutersPrimitive (
		final int args,
		final int locals,
		final int stack,
		final int outers,
		final int primitive)
	{
		descriptor().ObjectArgsLocalsStackOutersPrimitive(
			this,
			args,
			locals,
			stack,
			outers,
			primitive);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject argTypeAt (
		final int index)
	{
		return descriptor().ObjectArgTypeAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void argTypeAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().ObjectArgTypeAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public String asNativeString ()
	{
		return descriptor().ObjectAsNativeString(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject asObject ()
	{
		return descriptor().ObjectAsObject(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject asSet ()
	{
		return descriptor().ObjectAsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject asTuple ()
	{
		return descriptor().ObjectAsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void atAddMessageRestrictions (
		final AvailObject methodName,
		final AvailObject illegalArgMsgs)
	{
		descriptor().ObjectAtAddMessageRestrictions(
			this,
			methodName,
			illegalArgMsgs);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void atAddMethodImplementation (
		final AvailObject methodName,
		final AvailObject implementation)
	{
		descriptor().ObjectAtAddMethodImplementation(
			this,
			methodName,
			implementation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void atMessageAddBundle (
		final AvailObject message,
		final AvailObject bundle)
	{
		descriptor().ObjectAtMessageAddBundle(
			this,
			message,
			bundle);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void atNameAdd (
		final AvailObject stringName,
		final AvailObject trueName)
	{
		descriptor().ObjectAtNameAdd(
			this,
			stringName,
			trueName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void atNewNamePut (
		final AvailObject stringName,
		final AvailObject trueName)
	{
		descriptor().ObjectAtNewNamePut(
			this,
			stringName,
			trueName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void atPrivateNameAdd (
		final AvailObject stringName,
		final AvailObject trueName)
	{
		descriptor().ObjectAtPrivateNameAdd(
			this,
			stringName,
			trueName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject becomeExactType ()
	{
		return descriptor().ObjectBecomeExactType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void becomeRealTupleType ()
	{
		descriptor().ObjectBecomeRealTupleType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject binAddingElementHashLevelCanDestroy (
		final AvailObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		return descriptor().ObjectBinAddingElementHashLevelCanDestroy(
			this,
			elementObject,
			elementObjectHash,
			myLevel,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject binElementAt (
		final int index)
	{
		return descriptor().ObjectBinElementAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binElementAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().ObjectBinElementAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean binHasElementHash (
		final AvailObject elementObject,
		final int elementObjectHash)
	{
		return descriptor().ObjectBinHasElementHash(
			this,
			elementObject,
			elementObjectHash);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int binHash ()
	{
		return descriptor().ObjectBinHash(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binHash (
		final int value)
	{
		descriptor().ObjectBinHash(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject binRemoveElementHashCanDestroy (
		final AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{
		return descriptor().ObjectBinRemoveElementHashCanDestroy(
			this,
			elementObject,
			elementObjectHash,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int binSize ()
	{
		return descriptor().ObjectBinSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binSize (
		final int value)
	{
		descriptor().ObjectBinSize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject binUnionType ()
	{
		return descriptor().ObjectBinUnionType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binUnionType (
		final AvailObject value)
	{
		descriptor().ObjectBinUnionType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int bitsPerEntry ()
	{
		return descriptor().ObjectBitsPerEntry(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int bitVector ()
	{
		return descriptor().ObjectBitVector(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void bitVector (
		final int value)
	{
		descriptor().ObjectBitVector(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject bodyBlock ()
	{
		return descriptor().ObjectBodyBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void bodyBlock (
		final AvailObject value)
	{
		descriptor().ObjectBodyBlock(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void bodyBlockRequiresBlockReturnsBlock (
		final AvailObject bb,
		final AvailObject rqb,
		final AvailObject rtb)
	{
		descriptor().ObjectBodyBlockRequiresBlockReturnsBlock(
			this,
			bb,
			rqb,
			rtb);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject bodySignature ()
	{
		return descriptor().ObjectBodySignature(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void bodySignature (
		final AvailObject signature)
	{
		descriptor().ObjectBodySignature(this, signature);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void bodySignatureRequiresBlockReturnsBlock (
		final AvailObject bs,
		final AvailObject rqb,
		final AvailObject rtb)
	{
		descriptor().ObjectBodySignatureRequiresBlockReturnsBlock(
			this,
			bs,
			rqb,
			rtb);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject breakpointBlock ()
	{
		return descriptor().ObjectBreakpointBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void breakpointBlock (
		final AvailObject value)
	{
		descriptor().ObjectBreakpointBlock(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void buildFilteredBundleTreeFrom (
		final AvailObject bundleTree)
	{
		descriptor().ObjectBuildFilteredBundleTreeFrom(this, bundleTree);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject bundleAtMessageParts (
		final AvailObject message,
		final AvailObject parts)
	{
		return descriptor().ObjectBundleAtMessageParts(
			this,
			message,
			parts);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject caller ()
	{
		return descriptor().ObjectCaller(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void caller (
		final AvailObject value)
	{
		descriptor().ObjectCaller(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean canComputeHashOfType ()
	{
		return descriptor().ObjectCanComputeHashOfType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int capacity ()
	{
		return descriptor().ObjectCapacity(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void cleanUpAfterCompile ()
	{
		descriptor().ObjectCleanUpAfterCompile(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void clearModule ()
	{
		descriptor().ObjectClearModule(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void clearValue ()
	{
		descriptor().ObjectClearValue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject closure ()
	{
		return descriptor().ObjectClosure(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void closure (
		final AvailObject value)
	{
		descriptor().ObjectClosure(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject closureType ()
	{
		return descriptor().ObjectClosureType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void closureType (
		final AvailObject value)
	{
		descriptor().ObjectClosureType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject code ()
	{
		return descriptor().ObjectCode(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void code (
		final AvailObject value)
	{
		descriptor().ObjectCode(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int codePoint ()
	{
		return descriptor().ObjectCodePoint(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void codePoint (
		final int value)
	{
		descriptor().ObjectCodePoint(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean compareFromToWithStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject anotherObject,
		final int startIndex2)
	{
		return descriptor().ObjectCompareFromToWithStartingAt(
			this,
			startIndex1,
			endIndex1,
			anotherObject,
			startIndex2);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean compareFromToWithAnyTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTuple,
		final int startIndex2)
	{
		return descriptor().ObjectCompareFromToWithAnyTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aTuple,
			startIndex2);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean compareFromToWithByteStringStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteString,
		final int startIndex2)
	{
		return descriptor().ObjectCompareFromToWithByteStringStartingAt(
			this,
			startIndex1,
			endIndex1,
			aByteString,
			startIndex2);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean compareFromToWithByteTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteTuple,
		final int startIndex2)
	{
		return descriptor().ObjectCompareFromToWithByteTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aByteTuple,
			startIndex2);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean compareFromToWithNybbleTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject aNybbleTuple,
		final int startIndex2)
	{
		return descriptor().ObjectCompareFromToWithNybbleTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aNybbleTuple,
			startIndex2);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean compareFromToWithObjectTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject anObjectTuple,
		final int startIndex2)
	{
		return descriptor().ObjectCompareFromToWithObjectTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			anObjectTuple,
			startIndex2);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean compareFromToWithTwoByteStringStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTwoByteString,
		final int startIndex2)
	{
		return descriptor().ObjectCompareFromToWithTwoByteStringStartingAt(
			this,
			startIndex1,
			endIndex1,
			aTwoByteString,
			startIndex2);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject complete ()
	{
		return descriptor().ObjectComplete(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void complete (
		final AvailObject value)
	{
		descriptor().ObjectComplete(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int computeHashFromTo (
		final int start,
		final int end)
	{
		return descriptor().ObjectComputeHashFromTo(
			this,
			start,
			end);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject computeReturnTypeFromArgumentTypesInterpreter (
		final List<AvailObject> argTypes,
		final AvailInterpreter anAvailInterpreter)
	{
		return descriptor().ObjectComputeReturnTypeFromArgumentTypesInterpreter(
			this,
			argTypes,
			anAvailInterpreter);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject concatenateTuplesCanDestroy (
		final boolean canDestroy)
	{
		return descriptor().ObjectConcatenateTuplesCanDestroy(this, canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject constantBindings ()
	{
		return descriptor().ObjectConstantBindings(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void constantBindings (
		final AvailObject value)
	{
		descriptor().ObjectConstantBindings(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean containsBlock (
		final AvailObject aClosure)
	{
		return descriptor().ObjectContainsBlock(this, aClosure);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject contentType ()
	{
		return descriptor().ObjectContentType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void contentType (
		final AvailObject value)
	{
		descriptor().ObjectContentType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject contingentImpSets ()
	{
		return descriptor().ObjectContingentImpSets(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void contingentImpSets (
		final AvailObject value)
	{
		descriptor().ObjectContingentImpSets(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject continuation ()
	{
		return descriptor().ObjectContinuation(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void continuation (
		final AvailObject value)
	{
		descriptor().ObjectContinuation(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyAsMutableContinuation ()
	{
		return descriptor().ObjectCopyAsMutableContinuation(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyAsMutableObjectTuple ()
	{
		return descriptor().ObjectCopyAsMutableObjectTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyAsMutableSpliceTuple ()
	{
		return descriptor().ObjectCopyAsMutableSpliceTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyMutable ()
	{
		return descriptor().ObjectCopyMutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void copyToRestrictedTo (
		final AvailObject filteredBundleTree,
		final AvailObject visibleNames)
	{
		descriptor().ObjectCopyToRestrictedTo(
			this,
			filteredBundleTree,
			visibleNames);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyTupleFromToCanDestroy (
		final int start,
		final int end,
		final boolean canDestroy)
	{
		return descriptor().ObjectCopyTupleFromToCanDestroy(
			this,
			start,
			end,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean couldEverBeInvokedWith (
		final ArrayList<AvailObject> argTypes)
	{
		return descriptor().ObjectCouldEverBeInvokedWith(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject createTestingTreeWithPositiveMatchesRemainingPossibilities (
		final AvailObject positiveTuple,
		final AvailObject possibilities)
	{
		return descriptor().ObjectCreateTestingTreeWithPositiveMatchesRemainingPossibilities(
			this,
			positiveTuple,
			possibilities);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject dataAtIndex (
		final int index)
	{
		return descriptor().ObjectDataAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void dataAtIndexPut (
		final int index,
		final AvailObject value)
	{
		descriptor().ObjectDataAtIndexPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject defaultType ()
	{
		return descriptor().ObjectDefaultType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void defaultType (
		final AvailObject value)
	{
		descriptor().ObjectDefaultType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject dependentChunks ()
	{
		return descriptor().ObjectDependentChunks(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void dependentChunks (
		final AvailObject value)
	{
		descriptor().ObjectDependentChunks(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int depth ()
	{
		return descriptor().ObjectDepth(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void depth (
		final int value)
	{
		descriptor().ObjectDepth(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void displayTestingTree ()
	{
		descriptor().ObjectDisplayTestingTree(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject divideCanDestroy (
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return descriptor().ObjectDivideCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject divideIntoInfinityCanDestroy (
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		return descriptor().ObjectDivideIntoInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject divideIntoIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor().ObjectDivideIntoIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject elementAt (
		final int index)
	{
		return descriptor().ObjectElementAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void elementAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().ObjectElementAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int endOfZone (
		final int zone)
	{
		return descriptor().ObjectEndOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int endSubtupleIndexInZone (
		final int zone)
	{
		return descriptor().ObjectEndSubtupleIndexInZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void ensureMetacovariant ()
	{
		descriptor().ObjectEnsureMetacovariant(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject ensureMutable ()
	{
		return descriptor().ObjectEnsureMutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equals (
		final AvailObject another)
	{
		return descriptor().ObjectEquals(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsAnyTuple (
		final AvailObject anotherTuple)
	{
		return descriptor().ObjectEqualsAnyTuple(this, anotherTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsBlank ()
	{
		return descriptor().ObjectEqualsBlank(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsByteString (
		final AvailObject aByteString)
	{
		return descriptor().ObjectEqualsByteString(this, aByteString);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsByteTuple (
		final AvailObject aByteTuple)
	{
		return descriptor().ObjectEqualsByteTuple(this, aByteTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsCharacterWithCodePoint (
		final int otherCodePoint)
	{
		return descriptor().ObjectEqualsCharacterWithCodePoint(this, otherCodePoint);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsClosure (
		final AvailObject aClosure)
	{
		return descriptor().ObjectEqualsClosure(this, aClosure);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsClosureType (
		final AvailObject aClosureType)
	{
		return descriptor().ObjectEqualsClosureType(this, aClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsCompiledCode (
		final AvailObject aCompiledCode)
	{
		return descriptor().ObjectEqualsCompiledCode(this, aCompiledCode);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContainer (
		final AvailObject aContainer)
	{
		return descriptor().ObjectEqualsContainer(this, aContainer);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContainerType (
		final AvailObject aContainerType)
	{
		return descriptor().ObjectEqualsContainerType(this, aContainerType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContinuation (
		final AvailObject aContinuation)
	{
		return descriptor().ObjectEqualsContinuation(this, aContinuation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContinuationType (
		final AvailObject aType)
	{
		return descriptor().ObjectEqualsContinuationType(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsDouble (
		final AvailObject aDoubleObject)
	{
		return descriptor().ObjectEqualsDouble(this, aDoubleObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsFalse ()
	{
		return descriptor().ObjectEqualsFalse(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsFloat (
		final AvailObject aFloatObject)
	{
		return descriptor().ObjectEqualsFloat(this, aFloatObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsGeneralizedClosureType (
		final AvailObject aType)
	{
		return descriptor().ObjectEqualsGeneralizedClosureType(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsInfinity (
		final AvailObject anInfinity)
	{
		return descriptor().ObjectEqualsInfinity(this, anInfinity);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsInteger (
		final AvailObject anAvailInteger)
	{
		return descriptor().ObjectEqualsInteger(this, anAvailInteger);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor().ObjectEqualsIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsList (
		final AvailObject aList)
	{
		return descriptor().ObjectEqualsList(this, aList);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsListType (
		final AvailObject aListType)
	{
		return descriptor().ObjectEqualsListType(this, aListType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsMap (
		final AvailObject aMap)
	{
		return descriptor().ObjectEqualsMap(this, aMap);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsMapType (
		final AvailObject aMapType)
	{
		return descriptor().ObjectEqualsMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsNybbleTuple (
		final AvailObject aNybbleTuple)
	{
		return descriptor().ObjectEqualsNybbleTuple(this, aNybbleTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsObject (
		final AvailObject anObject)
	{
		return descriptor().ObjectEqualsObject(this, anObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsObjectTuple (
		final AvailObject anObjectTuple)
	{
		return descriptor().ObjectEqualsObjectTuple(this, anObjectTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsPrimitiveType (
		final AvailObject aPrimitiveType)
	{
		return descriptor().ObjectEqualsPrimitiveType(this, aPrimitiveType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsSet (
		final AvailObject aSet)
	{
		return descriptor().ObjectEqualsSet(this, aSet);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsSetType (
		final AvailObject aSetType)
	{
		return descriptor().ObjectEqualsSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsTrue ()
	{
		return descriptor().ObjectEqualsTrue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsTupleType (
		final AvailObject aTupleType)
	{
		return descriptor().ObjectEqualsTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsTwoByteString (
		final AvailObject aTwoByteString)
	{
		return descriptor().ObjectEqualsTwoByteString(this, aTwoByteString);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsVoid ()
	{
		return descriptor().ObjectEqualsVoid(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsVoidOrBlank ()
	{
		return descriptor().ObjectEqualsVoidOrBlank(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void evictedByGarbageCollector ()
	{
		descriptor().ObjectEvictedByGarbageCollector(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject exactType ()
	{
		return descriptor().ObjectExactType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int executionMode ()
	{
		return descriptor().ObjectExecutionMode(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void executionMode (
		final int value)
	{
		descriptor().ObjectExecutionMode(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int executionState ()
	{
		return descriptor().ObjectExecutionState(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void executionState (
		final int value)
	{
		descriptor().ObjectExecutionState(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject expand ()
	{
		return descriptor().ObjectExpand(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean extractBoolean ()
	{
		return descriptor().ObjectExtractBoolean(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short extractByte ()
	{
		return descriptor().ObjectExtractByte(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public double extractDouble ()
	{
		return descriptor().ObjectExtractDouble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public float extractFloat ()
	{
		return descriptor().ObjectExtractFloat(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int extractInt ()
	{
		return descriptor().ObjectExtractInt(this);
	}

	/**
	 * Extract a 64-bit signed Java {@code long} from the {@linkplain
	 * AvailObject receiver}.
	 * 
	 * @return A 64-bit signed Java {@code long}
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public long extractLong ()
	{
		return descriptor().ObjectExtractLong(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public byte extractNybble ()
	{
		return descriptor().ObjectExtractNybble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public byte extractNybbleFromTupleAt (
		final int index)
	{
		return descriptor().ObjectExtractNybbleFromTupleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject fieldMap ()
	{
		return descriptor().ObjectFieldMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void fieldMap (
		final AvailObject value)
	{
		descriptor().ObjectFieldMap(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject fieldTypeMap ()
	{
		return descriptor().ObjectFieldTypeMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void fieldTypeMap (
		final AvailObject value)
	{
		descriptor().ObjectFieldTypeMap(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public List<AvailObject> filterByTypes (
		final List<AvailObject> argTypes)
		{
		return descriptor().ObjectFilterByTypes(this, argTypes);
		}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject filteredBundleTree ()
	{
		return descriptor().ObjectFilteredBundleTree(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void filteredBundleTree (
		final AvailObject value)
	{
		descriptor().ObjectFilteredBundleTree(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject firstTupleType ()
	{
		return descriptor().ObjectFirstTupleType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void firstTupleType (
		final AvailObject value)
	{
		descriptor().ObjectFirstTupleType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject forZoneSetSubtupleStartSubtupleIndexEndOfZone (
		final int zone,
		final AvailObject newSubtuple,
		final int startSubtupleIndex,
		final int endOfZone)
	{
		return descriptor().ObjectForZoneSetSubtupleStartSubtupleIndexEndOfZone(
			this,
			zone,
			newSubtuple,
			startSubtupleIndex,
			endOfZone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int getInteger ()
	{
		return descriptor().ObjectGetInteger(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject getValue ()
	{
		return descriptor().ObjectGetValue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean greaterThanInteger (
		final AvailObject another)
	{
		return descriptor().ObjectGreaterThanInteger(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean greaterThanSignedInfinity (
		final AvailObject another)
	{
		return descriptor().ObjectGreaterThanSignedInfinity(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasElement (
		final AvailObject elementObject)
	{
		return descriptor().ObjectHasElement(this, elementObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hashFromTo (
		final int startIndex,
		final int endIndex)
	{
		return descriptor().ObjectHashFromTo(
			this,
			startIndex,
			endIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hashOfType ()
	{
		return descriptor().ObjectHashOfType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hashOrZero ()
	{
		return descriptor().ObjectHashOrZero(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hashOrZero (
		final int value)
	{
		descriptor().ObjectHashOrZero(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasKey (
		final AvailObject keyObject)
	{
		return descriptor().ObjectHasKey(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasObjectInstance (
		final AvailObject potentialInstance)
	{
		return descriptor().ObjectHasObjectInstance(this, potentialInstance);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasRestrictions ()
	{
		return descriptor().ObjectHasRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hiLevelTwoChunkLowOffset ()
	{
		return descriptor().ObjectHiLevelTwoChunkLowOffset(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hiLevelTwoChunkLowOffset (
		final int value)
	{
		descriptor().ObjectHiLevelTwoChunkLowOffset(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hiNumLocalsLowNumArgs ()
	{
		return descriptor().ObjectHiNumLocalsLowNumArgs(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hiNumLocalsLowNumArgs (
		final int value)
	{
		descriptor().ObjectHiNumLocalsLowNumArgs(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hiPrimitiveLowNumArgsAndLocalsAndStack ()
	{
		return descriptor().ObjectHiPrimitiveLowNumArgsAndLocalsAndStack(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hiPrimitiveLowNumArgsAndLocalsAndStack (
		final int value)
	{
		descriptor().ObjectHiPrimitiveLowNumArgsAndLocalsAndStack(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hiStartingChunkIndexLowNumOuters ()
	{
		return descriptor().ObjectHiStartingChunkIndexLowNumOuters(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hiStartingChunkIndexLowNumOuters (
		final int value)
	{
		descriptor().ObjectHiStartingChunkIndexLowNumOuters(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public ArrayList<AvailObject> implementationsAtOrBelow (
		final ArrayList<AvailObject> argTypes)
		{
		return descriptor().ObjectImplementationsAtOrBelow(this, argTypes);
		}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject implementationsTuple ()
	{
		return descriptor().ObjectImplementationsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void implementationsTuple (
		final AvailObject value)
	{
		descriptor().ObjectImplementationsTuple(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject includeBundleAtMessageParts (
		final AvailObject message,
		final AvailObject parts)
	{
		return descriptor().ObjectIncludeBundleAtMessageParts(
			this,
			message,
			parts);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean includes (
		final AvailObject imp)
	{
		return descriptor().ObjectIncludes(this, imp);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int inclusiveFlags ()
	{
		return descriptor().ObjectInclusiveFlags(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void inclusiveFlags (
		final int value)
	{
		descriptor().ObjectInclusiveFlags(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject incomplete ()
	{
		return descriptor().ObjectIncomplete(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void incomplete (
		final AvailObject value)
	{
		descriptor().ObjectIncomplete(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int index ()
	{
		return descriptor().ObjectIndex(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void index (
		final int value)
	{
		descriptor().ObjectIndex(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject innerType ()
	{
		return descriptor().ObjectInnerType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void innerType (
		final AvailObject value)
	{
		descriptor().ObjectInnerType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject instance ()
	{
		return descriptor().ObjectInstance(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void instance (
		final AvailObject value)
	{
		descriptor().ObjectInstance(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int internalHash ()
	{
		return descriptor().ObjectInternalHash(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void internalHash (
		final int value)
	{
		descriptor().ObjectInternalHash(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int interruptRequestFlag ()
	{
		return descriptor().ObjectInterruptRequestFlag(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void interruptRequestFlag (
		final int value)
	{
		descriptor().ObjectInterruptRequestFlag(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int invocationCount ()
	{
		return descriptor().ObjectInvocationCount(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void invocationCount (
		final int value)
	{
		descriptor().ObjectInvocationCount(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isAbstract ()
	{
		return descriptor().ObjectIsAbstract(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBetterRepresentationThan (
		final AvailObject anotherObject)
	{
		return descriptor().ObjectIsBetterRepresentationThan(this, anotherObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBetterRepresentationThanTupleType (
		final AvailObject aTupleType)
	{
		return descriptor().ObjectIsBetterRepresentationThanTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBinSubsetOf (
		final AvailObject potentialSuperset)
	{
		return descriptor().ObjectIsBinSubsetOf(this, potentialSuperset);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBoolean ()
	{
		return descriptor().ObjectIsBoolean(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isByte ()
	{
		return descriptor().ObjectIsByte(this);
	}

	/**
	 * Is the {@link AvailObject receiver} an Avail byte tuple?
	 * 
	 * @return {@code true} if the receiver is an Avail byte tuple, {@code
	 *         false} otherwise.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public boolean isByteTuple ()
	{
		return descriptor().ObjectIsByteTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isCharacter ()
	{
		return descriptor().ObjectIsCharacter(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isClosure ()
	{
		return descriptor().ObjectIsClosure(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isCyclicType ()
	{
		return descriptor().ObjectIsCyclicType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isExtendedInteger ()
	{
		return descriptor().ObjectIsExtendedInteger(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isFinite ()
	{
		return descriptor().ObjectIsFinite(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isForward ()
	{
		return descriptor().ObjectIsForward(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isHashAvailable ()
	{
		return descriptor().ObjectIsHashAvailable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isImplementation ()
	{
		return descriptor().ObjectIsImplementation(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isInstanceOfSubtypeOf (
		final AvailObject aType)
	{
		return descriptor().ObjectIsInstanceOfSubtypeOf(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isIntegerRangeType ()
	{
		return descriptor().ObjectIsIntegerRangeType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isList ()
	{
		return descriptor().ObjectIsList(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isListType ()
	{
		return descriptor().ObjectIsListType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isMap ()
	{
		return descriptor().ObjectIsMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isMapType ()
	{
		return descriptor().ObjectIsMapType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isNybble ()
	{
		return descriptor().ObjectIsNybble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isPositive ()
	{
		return descriptor().ObjectIsPositive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSaved ()
	{
		return descriptor().ObjectIsSaved(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void isSaved (
		final boolean aBoolean)
	{
		descriptor().ObjectIsSaved(this, aBoolean);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSet ()
	{
		return descriptor().ObjectIsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSetType ()
	{
		return descriptor().ObjectIsSetType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSplice ()
	{
		return descriptor().ObjectIsSplice(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSubsetOf (
		final AvailObject another)
	{
		return descriptor().ObjectIsSubsetOf(this, another);
	}

	/**
	 * Is the {@link AvailObject receiver} an Avail string?
	 * 
	 * @return {@code true} if the receiver is an Avail string, {@code false}
	 *         otherwise.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public boolean isString ()
	{
		return descriptor().ObjectIsString(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSubtypeOf (
		final AvailObject aType)
	{
		return descriptor().ObjectIsSubtypeOf(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfClosureType (
		final AvailObject aClosureType)
	{
		return descriptor().ObjectIsSupertypeOfClosureType(this, aClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfContainerType (
		final AvailObject aContainerType)
	{
		return descriptor().ObjectIsSupertypeOfContainerType(this, aContainerType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfContinuationType (
		final AvailObject aContinuationType)
	{
		return descriptor().ObjectIsSupertypeOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfCyclicType (
		final AvailObject aCyclicType)
	{
		return descriptor().ObjectIsSupertypeOfCyclicType(this, aCyclicType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfGeneralizedClosureType (
		final AvailObject aGeneralizedClosureType)
	{
		return descriptor().ObjectIsSupertypeOfGeneralizedClosureType(this, aGeneralizedClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor().ObjectIsSupertypeOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfListType (
		final AvailObject aListType)
	{
		return descriptor().ObjectIsSupertypeOfListType(this, aListType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfMapType (
		final AvailObject aMapType)
	{
		return descriptor().ObjectIsSupertypeOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfObjectMeta (
		final AvailObject anObjectMeta)
	{
		return descriptor().ObjectIsSupertypeOfObjectMeta(this, anObjectMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfObjectMetaMeta (
		final AvailObject anObjectMetaMeta)
	{
		return descriptor().ObjectIsSupertypeOfObjectMetaMeta(this, anObjectMetaMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor().ObjectIsSupertypeOfObjectType(this, anObjectType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfPrimitiveType (
		final AvailObject aPrimitiveType)
	{
		return descriptor().ObjectIsSupertypeOfPrimitiveType(this, aPrimitiveType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfSetType (
		final AvailObject aSetType)
	{
		return descriptor().ObjectIsSupertypeOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfTerminates ()
	{
		return descriptor().ObjectIsSupertypeOfTerminates(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor().ObjectIsSupertypeOfTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfVoid ()
	{
		return descriptor().ObjectIsSupertypeOfVoid(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isTuple ()
	{
		return descriptor().ObjectIsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isTupleType ()
	{
		return descriptor().ObjectIsTupleType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isType ()
	{
		return descriptor().ObjectIsType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isValid ()
	{
		return descriptor().ObjectIsValid(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void isValid (
		final boolean aBoolean)
	{
		descriptor().ObjectIsValid(this, aBoolean);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isValidForArgumentTypesInterpreter (
		final List<AvailObject> argTypes,
		final AvailInterpreter interpreter)
	{
		return descriptor().ObjectIsValidForArgumentTypesInterpreter(
			this,
			argTypes,
			interpreter);
	}

	/**
	 * Answer an {@linkplain Iterator iterator} suitable for traversing the
	 * elements of the {@linkplain AvailObject receiver} with a Java
	 * <em>foreach</em> construct.
	 * 
	 * @return An {@linkplain Iterator iterator}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public @NotNull Iterator<AvailObject> iterator ()
	{
		return descriptor().ObjectIterator(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject keyAtIndex (
		final int index)
	{
		return descriptor().ObjectKeyAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void keyAtIndexPut (
		final int index,
		final AvailObject keyObject)
	{
		descriptor().ObjectKeyAtIndexPut(
			this,
			index,
			keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public List<AvailObject> keysAsArray ()
	{
		return descriptor().ObjectKeysAsArray(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject keysAsSet ()
	{
		return descriptor().ObjectKeysAsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject keyType ()
	{
		return descriptor().ObjectKeyType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void keyType (
		final AvailObject value)
	{
		descriptor().ObjectKeyType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean lessOrEqual (
		final AvailObject another)
	{
		return descriptor().ObjectLessOrEqual(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean lessThan (
		final AvailObject another)
	{
		return descriptor().ObjectLessThan(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int levelTwoChunkIndex ()
	{
		return descriptor().ObjectLevelTwoChunkIndex(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void levelTwoChunkIndexOffset (
		final int index,
		final int offset)
	{
		descriptor().ObjectLevelTwoChunkIndexOffset(
			this,
			index,
			offset);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int levelTwoOffset ()
	{
		return descriptor().ObjectLevelTwoOffset(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject literalAt (
		final int index)
	{
		return descriptor().ObjectLiteralAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void literalAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().ObjectLiteralAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject localOrArgOrStackAt (
		final int index)
	{
		return descriptor().ObjectLocalOrArgOrStackAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void localOrArgOrStackAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().ObjectLocalOrArgOrStackAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject localTypeAt (
		final int index)
	{
		return descriptor().ObjectLocalTypeAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByTypesFromArray (
		final List<AvailObject> argumentTypeArray)
	{
		return descriptor().ObjectLookupByTypesFromArray(this, argumentTypeArray);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByTypesFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		return descriptor().ObjectLookupByTypesFromContinuationStackp(
			this,
			continuation,
			stackp);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByTypesFromTuple (
		final AvailObject argumentTypeTuple)
	{
		return descriptor().ObjectLookupByTypesFromTuple(this, argumentTypeTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByValuesFromArray (
		final List<AvailObject> argumentArray)
	{
		return descriptor().ObjectLookupByValuesFromArray(this, argumentArray);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByValuesFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		return descriptor().ObjectLookupByValuesFromContinuationStackp(
			this,
			continuation,
			stackp);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByValuesFromTuple (
		final AvailObject argumentTuple)
	{
		return descriptor().ObjectLookupByValuesFromTuple(this, argumentTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lowerBound ()
	{
		return descriptor().ObjectLowerBound(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void lowerBound (
		final AvailObject value)
	{
		descriptor().ObjectLowerBound(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean lowerInclusive ()
	{
		return descriptor().ObjectLowerInclusive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void lowerInclusiveUpperInclusive (
		final boolean lowInc,
		final boolean highInc)
	{
		descriptor().ObjectLowerInclusiveUpperInclusive(
			this,
			lowInc,
			highInc);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject makeImmutable ()
	{
		return descriptor().ObjectMakeImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void makeSubobjectsImmutable ()
	{
		descriptor().ObjectMakeSubobjectsImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject mapAt (
		final AvailObject keyObject)
	{
		return descriptor().ObjectMapAt(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject mapAtPuttingCanDestroy (
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		return descriptor().ObjectMapAtPuttingCanDestroy(
			this,
			keyObject,
			newValueObject,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int mapSize ()
	{
		return descriptor().ObjectMapSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void mapSize (
		final int value)
	{
		descriptor().ObjectMapSize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject mapWithoutKeyCanDestroy (
		final AvailObject keyObject,
		final boolean canDestroy)
	{
		return descriptor().ObjectMapWithoutKeyCanDestroy(
			this,
			keyObject,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short maxStackDepth ()
	{
		return descriptor().ObjectMaxStackDepth(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject message ()
	{
		return descriptor().ObjectMessage(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void message (
		final AvailObject value)
	{
		descriptor().ObjectMessage(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject messageParts ()
	{
		return descriptor().ObjectMessageParts(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void messageParts (
		final AvailObject value)
	{
		descriptor().ObjectMessageParts(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject methods ()
	{
		return descriptor().ObjectMethods(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void methods (
		final AvailObject value)
	{
		descriptor().ObjectMethods(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject minusCanDestroy (
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return descriptor().ObjectMinusCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void moveToHead ()
	{
		descriptor().ObjectMoveToHead(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject multiplyByInfinityCanDestroy (
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		return descriptor().ObjectMultiplyByInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject multiplyByIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor().ObjectMultiplyByIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject myObjectMeta ()
	{
		return descriptor().ObjectMyObjectMeta(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void myObjectMeta (
		final AvailObject value)
	{
		descriptor().ObjectMyObjectMeta(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject myObjectType ()
	{
		return descriptor().ObjectMyObjectType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void myObjectType (
		final AvailObject value)
	{
		descriptor().ObjectMyObjectType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject myRestrictions ()
	{
		return descriptor().ObjectMyRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void myRestrictions (
		final AvailObject value)
	{
		descriptor().ObjectMyRestrictions(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject myType ()
	{
		return descriptor().ObjectMyType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void myType (
		final AvailObject value)
	{
		descriptor().ObjectMyType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject name ()
	{
		return descriptor().ObjectName(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void name (
		final AvailObject value)
	{
		descriptor().ObjectName(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject names ()
	{
		return descriptor().ObjectNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void names (
		final AvailObject value)
	{
		descriptor().ObjectNames(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean nameVisible (
		final AvailObject trueName)
	{
		return descriptor().ObjectNameVisible(this, trueName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void necessaryImplementationSetChanged (
		final AvailObject anImplementationSet)
	{
		descriptor().ObjectNecessaryImplementationSetChanged(this, anImplementationSet);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject newNames ()
	{
		return descriptor().ObjectNewNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void newNames (
		final AvailObject value)
	{
		descriptor().ObjectNewNames(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject next ()
	{
		return descriptor().ObjectNext(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void next (
		final AvailObject nextChunk)
	{
		descriptor().ObjectNext(this, nextChunk);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int nextIndex ()
	{
		return descriptor().ObjectNextIndex(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void nextIndex (
		final int value)
	{
		descriptor().ObjectNextIndex(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short numArgs ()
	{
		return descriptor().ObjectNumArgs(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short numArgsAndLocalsAndStack ()
	{
		return descriptor().ObjectNumArgsAndLocalsAndStack(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numberOfZones ()
	{
		return descriptor().ObjectNumberOfZones(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numBlanks ()
	{
		return descriptor().ObjectNumBlanks(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void numBlanks (
		final int value)
	{
		descriptor().ObjectNumBlanks(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numFloats ()
	{
		return descriptor().ObjectNumFloats(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void numFloats (
		final int value)
	{
		descriptor().ObjectNumFloats(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numIntegers ()
	{
		return descriptor().ObjectNumIntegers(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void numIntegers (
		final int value)
	{
		descriptor().ObjectNumIntegers(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short numLiterals ()
	{
		return descriptor().ObjectNumLiterals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short numLocals ()
	{
		return descriptor().ObjectNumLocals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numLocalsOrArgsOrStack ()
	{
		return descriptor().ObjectNumLocalsOrArgsOrStack(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numObjects ()
	{
		return descriptor().ObjectNumObjects(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void numObjects (
		final int value)
	{
		descriptor().ObjectNumObjects(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short numOuters ()
	{
		return descriptor().ObjectNumOuters(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numOuterVars ()
	{
		return descriptor().ObjectNumOuterVars(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject nybbles ()
	{
		return descriptor().ObjectNybbles(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void nybbles (
		final AvailObject value)
	{
		descriptor().ObjectNybbles(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean optionallyNilOuterVar (
		final int index)
	{
		return descriptor().ObjectOptionallyNilOuterVar(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject outerTypeAt (
		final int index)
	{
		return descriptor().ObjectOuterTypeAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void outerTypesLocalTypes (
		final AvailObject tupleOfOuterTypes,
		final AvailObject tupleOfLocalContainerTypes)
	{
		descriptor().ObjectOuterTypesLocalTypes(
			this,
			tupleOfOuterTypes,
			tupleOfLocalContainerTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject outerVarAt (
		final int index)
	{
		return descriptor().ObjectOuterVarAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void outerVarAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().ObjectOuterVarAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject pad ()
	{
		return descriptor().ObjectPad(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void pad (
		final AvailObject value)
	{
		descriptor().ObjectPad(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject parent ()
	{
		return descriptor().ObjectParent(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void parent (
		final AvailObject value)
	{
		descriptor().ObjectParent(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int pc ()
	{
		return descriptor().ObjectPc(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void pc (
		final int value)
	{
		descriptor().ObjectPc(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject plusCanDestroy (
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return descriptor().ObjectPlusCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int populateTupleStartingAt (
		final AvailObject mutableTuple,
		final int startingIndex)
	{
		return descriptor().ObjectPopulateTupleStartingAt(
			this,
			mutableTuple,
			startingIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void postFault ()
	{
		descriptor().ObjectPostFault(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject previous ()
	{
		return descriptor().ObjectPrevious(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void previous (
		final AvailObject previousChunk)
	{
		descriptor().ObjectPrevious(this, previousChunk);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int previousIndex ()
	{
		return descriptor().ObjectPreviousIndex(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void previousIndex (
		final int value)
	{
		descriptor().ObjectPreviousIndex(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short primitiveNumber ()
	{
		return descriptor().ObjectPrimitiveNumber(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int priority ()
	{
		return descriptor().ObjectPriority(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void priority (
		final int value)
	{
		descriptor().ObjectPriority(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateAddElement (
		final AvailObject element)
	{
		return descriptor().ObjectPrivateAddElement(this, element);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateExcludeElement (
		final AvailObject element)
	{
		return descriptor().ObjectPrivateExcludeElement(this, element);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateExcludeElementKnownIndex (
		final AvailObject element,
		final int knownIndex)
	{
		return descriptor().ObjectPrivateExcludeElementKnownIndex(
			this,
			element,
			knownIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateExcludeKey (
		final AvailObject keyObject)
	{
		return descriptor().ObjectPrivateExcludeKey(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateMapAtPut (
		final AvailObject keyObject,
		final AvailObject valueObject)
	{
		return descriptor().ObjectPrivateMapAtPut(
			this,
			keyObject,
			valueObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateNames ()
	{
		return descriptor().ObjectPrivateNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void privateNames (
		final AvailObject value)
	{
		descriptor().ObjectPrivateNames(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateTestingTree ()
	{
		return descriptor().ObjectPrivateTestingTree(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void privateTestingTree (
		final AvailObject value)
	{
		descriptor().ObjectPrivateTestingTree(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject processGlobals ()
	{
		return descriptor().ObjectProcessGlobals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void processGlobals (
		final AvailObject value)
	{
		descriptor().ObjectProcessGlobals(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short rawByteAt (
		final int index)
	{
		return descriptor().ObjectRawByteAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawByteAtPut (
		final int index,
		final short anInteger)
	{
		descriptor().ObjectRawByteAtPut(
			this,
			index,
			anInteger);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short rawByteForCharacterAt (
		final int index)
	{
		return descriptor().ObjectRawByteForCharacterAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawByteForCharacterAtPut (
		final int index,
		final short anInteger)
	{
		descriptor().ObjectRawByteForCharacterAtPut(
			this,
			index,
			anInteger);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public byte rawNybbleAt (
		final int index)
	{
		return descriptor().ObjectRawNybbleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawNybbleAtPut (
		final int index,
		final byte aNybble)
	{
		descriptor().ObjectRawNybbleAtPut(
			this,
			index,
			aNybble);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int rawQuad1 ()
	{
		return descriptor().ObjectRawQuad1(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawQuad1 (
		final int value)
	{
		descriptor().ObjectRawQuad1(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int rawQuad2 ()
	{
		return descriptor().ObjectRawQuad2(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawQuad2 (
		final int value)
	{
		descriptor().ObjectRawQuad2(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int rawQuadAt (
		final int index)
	{
		return descriptor().ObjectRawQuadAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawQuadAtPut (
		final int index,
		final int value)
	{
		descriptor().ObjectRawQuadAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short rawShortForCharacterAt (
		final int index)
	{
		return descriptor().ObjectRawShortForCharacterAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawShortForCharacterAtPut (
		final int index,
		final short anInteger)
	{
		descriptor().ObjectRawShortForCharacterAtPut(
			this,
			index,
			anInteger);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int rawSignedIntegerAt (
		final int index)
	{
		return descriptor().ObjectRawSignedIntegerAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawSignedIntegerAtPut (
		final int index,
		final int value)
	{
		descriptor().ObjectRawSignedIntegerAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public long rawUnsignedIntegerAt (
		final int index)
	{
		return descriptor().ObjectRawUnsignedIntegerAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawUnsignedIntegerAtPut (
		final int index,
		final int value)
	{
		descriptor().ObjectRawUnsignedIntegerAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void readBarrierFault ()
	{
		descriptor().ObjectReadBarrierFault(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void releaseVariableOrMakeContentsImmutable ()
	{
		descriptor().ObjectReleaseVariableOrMakeContentsImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeDependentChunkId (
		final int aChunkIndex)
	{
		descriptor().ObjectRemoveDependentChunkId(this, aChunkIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeFrom (
		final AvailInterpreter anInterpreter)
	{
		descriptor().ObjectRemoveFrom(this, anInterpreter);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeFromQueue ()
	{
		descriptor().ObjectRemoveFromQueue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeImplementation (
		final AvailObject implementation)
	{
		descriptor().ObjectRemoveImplementation(this, implementation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean removeMessageParts (
		final AvailObject message,
		final AvailObject parts)
	{
		return descriptor().ObjectRemoveMessageParts(
			this,
			message,
			parts);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeRestrictions ()
	{
		descriptor().ObjectRemoveRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeRestrictions (
		final AvailObject obsoleteRestrictions)
	{
		descriptor().ObjectRemoveRestrictions(this, obsoleteRestrictions);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject requiresBlock ()
	{
		return descriptor().ObjectRequiresBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void requiresBlock (
		final AvailObject value)
	{
		descriptor().ObjectRequiresBlock(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void resolvedForwardWithName (
		final AvailObject forwardImplementation,
		final AvailObject methodName)
	{
		descriptor().ObjectResolvedForwardWithName(
			this,
			forwardImplementation,
			methodName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject restrictions ()
	{
		return descriptor().ObjectRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void restrictions (
		final AvailObject value)
	{
		descriptor().ObjectRestrictions(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject returnsBlock ()
	{
		return descriptor().ObjectReturnsBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void returnsBlock (
		final AvailObject value)
	{
		descriptor().ObjectReturnsBlock(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject returnType ()
	{
		return descriptor().ObjectReturnType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void returnType (
		final AvailObject value)
	{
		descriptor().ObjectReturnType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject rootBin ()
	{
		return descriptor().ObjectRootBin(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rootBin (
		final AvailObject value)
	{
		descriptor().ObjectRootBin(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void scanSubobjects (
		final AvailSubobjectVisitor visitor)
	{
		descriptor().ObjectScanSubobjects(this, visitor);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject secondTupleType ()
	{
		return descriptor().ObjectSecondTupleType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void secondTupleType (
		final AvailObject value)
	{
		descriptor().ObjectSecondTupleType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setIntersectionCanDestroy (
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		return descriptor().ObjectSetIntersectionCanDestroy(
			this,
			otherSet,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setMinusCanDestroy (
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		return descriptor().ObjectSetMinusCanDestroy(
			this,
			otherSet,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int setSize ()
	{
		return descriptor().ObjectSetSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void setSize (
		final int value)
	{
		descriptor().ObjectSetSize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void setSubtupleForZoneTo (
		final int zoneIndex,
		final AvailObject newTuple)
	{
		descriptor().ObjectSetSubtupleForZoneTo(
			this,
			zoneIndex,
			newTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setUnionCanDestroy (
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		return descriptor().ObjectSetUnionCanDestroy(
			this,
			otherSet,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void setValue (
		final AvailObject newValue)
	{
		descriptor().ObjectSetValue(this, newValue);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setWithElementCanDestroy (
		final AvailObject newElementObject,
		final boolean canDestroy)
	{
		return descriptor().ObjectSetWithElementCanDestroy(
			this,
			newElementObject,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setWithoutElementCanDestroy (
		final AvailObject elementObjectToExclude,
		final boolean canDestroy)
	{
		return descriptor().ObjectSetWithoutElementCanDestroy(
			this,
			elementObjectToExclude,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject signature ()
	{
		return descriptor().ObjectSignature(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void signature (
		final AvailObject value)
	{
		descriptor().ObjectSignature(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void size (
		final int value)
	{
		descriptor().ObjectSize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int sizeOfZone (
		final int zone)
	{
		return descriptor().ObjectSizeOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject sizeRange ()
	{
		return descriptor().ObjectSizeRange(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void sizeRange (
		final AvailObject value)
	{
		descriptor().ObjectSizeRange(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject stackAt (
		final int slotIndex)
	{
		return descriptor().ObjectStackAt(this, slotIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void stackAtPut (
		final int slotIndex,
		final AvailObject anObject)
	{
		descriptor().ObjectStackAtPut(
			this,
			slotIndex,
			anObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int stackp ()
	{
		return descriptor().ObjectStackp(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void stackp (
		final int value)
	{
		descriptor().ObjectStackp(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int startingChunkIndex ()
	{
		return descriptor().ObjectStartingChunkIndex(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void startingChunkIndex (
		final int value)
	{
		descriptor().ObjectStartingChunkIndex(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int startOfZone (
		final int zone)
	{
		return descriptor().ObjectStartOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int startSubtupleIndexInZone (
		final int zone)
	{
		return descriptor().ObjectStartSubtupleIndexInZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void step ()
	{
		descriptor().ObjectStep(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject subtractFromInfinityCanDestroy (
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		return descriptor().ObjectSubtractFromInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject subtractFromIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor().ObjectSubtractFromIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject subtupleForZone (
		final int zone)
	{
		return descriptor().ObjectSubtupleForZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject target ()
	{
		return descriptor().ObjectTarget(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void target (
		final AvailObject value)
	{
		descriptor().ObjectTarget(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject testingTree ()
	{
		return descriptor().ObjectTestingTree(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject timesCanDestroy (
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return descriptor().ObjectTimesCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int translateToZone (
		final int tupleIndex,
		final int zoneIndex)
	{
		return descriptor().ObjectTranslateToZone(
			this,
			tupleIndex,
			zoneIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject traversed ()
	{
		return descriptor().ObjectTraversed(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void trimExcessLongs ()
	{
		descriptor().ObjectTrimExcessLongs(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject trueNamesForStringName (
		final AvailObject stringName)
	{
		return descriptor().ObjectTrueNamesForStringName(this, stringName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject truncateTo (
		final int newTupleSize)
	{
		return descriptor().ObjectTruncateTo(this, newTupleSize);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject tuple ()
	{
		return descriptor().ObjectTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void tuple (
		final AvailObject value)
	{
		descriptor().ObjectTuple(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject tupleAt (
		final int index)
	{
		return descriptor().ObjectTupleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void tupleAtPut (
		final int index,
		final AvailObject aNybbleObject)
	{
		descriptor().ObjectTupleAtPut(
			this,
			index,
			aNybbleObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject tupleAtPuttingCanDestroy (
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		return descriptor().ObjectTupleAtPuttingCanDestroy(
			this,
			index,
			newValueObject,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int tupleIntAt (
		final int index)
	{
		return descriptor().ObjectTupleIntAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int tupleSize ()
	{
		return descriptor().ObjectTupleSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject tupleType ()
	{
		return descriptor().ObjectTupleType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void tupleType (
		final AvailObject value)
	{
		descriptor().ObjectTupleType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject type ()
	{
		return descriptor().ObjectType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void type (
		final AvailObject value)
	{
		descriptor().ObjectType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeAtIndex (
		final int index)
	{
		return descriptor().ObjectTypeAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean typeEquals (
		final AvailObject aType)
	{
		return descriptor().ObjectTypeEquals(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersection (
		final AvailObject another)
	{
		return descriptor().ObjectTypeIntersection(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfClosureType (
		final AvailObject aClosureType)
	{
		return descriptor().ObjectTypeIntersectionOfClosureType(this, aClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfClosureTypeCanDestroy (
		final AvailObject aClosureType,
		final boolean canDestroy)
	{
		return descriptor().ObjectTypeIntersectionOfClosureTypeCanDestroy(
			this,
			aClosureType,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfContainerType (
		final AvailObject aContainerType)
	{
		return descriptor().ObjectTypeIntersectionOfContainerType(this, aContainerType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfContinuationType (
		final AvailObject aContinuationType)
	{
		return descriptor().ObjectTypeIntersectionOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfCyclicType (
		final AvailObject aCyclicType)
	{
		return descriptor().ObjectTypeIntersectionOfCyclicType(this, aCyclicType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfGeneralizedClosureType (
		final AvailObject aGeneralizedClosureType)
	{
		return descriptor().ObjectTypeIntersectionOfGeneralizedClosureType(this, aGeneralizedClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfGeneralizedClosureTypeCanDestroy (
		final AvailObject aGeneralizedClosureType,
		final boolean canDestroy)
	{
		return descriptor().ObjectTypeIntersectionOfGeneralizedClosureTypeCanDestroy(
			this,
			aGeneralizedClosureType,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor().ObjectTypeIntersectionOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfListType (
		final AvailObject aListType)
	{
		return descriptor().ObjectTypeIntersectionOfListType(this, aListType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfMapType (
		final AvailObject aMapType)
	{
		return descriptor().ObjectTypeIntersectionOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfMeta (
		final AvailObject someMeta)
	{
		return descriptor().ObjectTypeIntersectionOfMeta(this, someMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfObjectMeta (
		final AvailObject anObjectMeta)
	{
		return descriptor().ObjectTypeIntersectionOfObjectMeta(this, anObjectMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfObjectMetaMeta (
		final AvailObject anObjectMetaMeta)
	{
		return descriptor().ObjectTypeIntersectionOfObjectMetaMeta(this, anObjectMetaMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor().ObjectTypeIntersectionOfObjectType(this, anObjectType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfSetType (
		final AvailObject aSetType)
	{
		return descriptor().ObjectTypeIntersectionOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor().ObjectTypeIntersectionOfTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeTuple ()
	{
		return descriptor().ObjectTypeTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void typeTuple (
		final AvailObject value)
	{
		descriptor().ObjectTypeTuple(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnion (
		final AvailObject another)
	{
		return descriptor().ObjectTypeUnion(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfClosureType (
		final AvailObject aClosureType)
	{
		return descriptor().ObjectTypeUnionOfClosureType(this, aClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfClosureTypeCanDestroy (
		final AvailObject aClosureType,
		final boolean canDestroy)
	{
		return descriptor().ObjectTypeUnionOfClosureTypeCanDestroy(
			this,
			aClosureType,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfContainerType (
		final AvailObject aContainerType)
	{
		return descriptor().ObjectTypeUnionOfContainerType(this, aContainerType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfContinuationType (
		final AvailObject aContinuationType)
	{
		return descriptor().ObjectTypeUnionOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfCyclicType (
		final AvailObject aCyclicType)
	{
		return descriptor().ObjectTypeUnionOfCyclicType(this, aCyclicType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfGeneralizedClosureType (
		final AvailObject aGeneralizedClosureType)
	{
		return descriptor().ObjectTypeUnionOfGeneralizedClosureType(this, aGeneralizedClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor().ObjectTypeUnionOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfListType (
		final AvailObject aListType)
	{
		return descriptor().ObjectTypeUnionOfListType(this, aListType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfMapType (
		final AvailObject aMapType)
	{
		return descriptor().ObjectTypeUnionOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfObjectMeta (
		final AvailObject anObjectMeta)
	{
		return descriptor().ObjectTypeUnionOfObjectMeta(this, anObjectMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfObjectMetaMeta (
		final AvailObject anObjectMetaMeta)
	{
		return descriptor().ObjectTypeUnionOfObjectMetaMeta(this, anObjectMetaMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor().ObjectTypeUnionOfObjectType(this, anObjectType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfSetType (
		final AvailObject aSetType)
	{
		return descriptor().ObjectTypeUnionOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor().ObjectTypeUnionOfTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject unclassified ()
	{
		return descriptor().ObjectUnclassified(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void unclassified (
		final AvailObject value)
	{
		descriptor().ObjectUnclassified(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject unionOfTypesAtThrough (
		final int startIndex,
		final int endIndex)
	{
		return descriptor().ObjectUnionOfTypesAtThrough(
			this,
			startIndex,
			endIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int untranslatedDataAt (
		final int index)
	{
		return descriptor().ObjectUntranslatedDataAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void untranslatedDataAtPut (
		final int index,
		final int value)
	{
		descriptor().ObjectUntranslatedDataAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject upperBound ()
	{
		return descriptor().ObjectUpperBound(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void upperBound (
		final AvailObject value)
	{
		descriptor().ObjectUpperBound(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean upperInclusive ()
	{
		return descriptor().ObjectUpperInclusive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject validateArgumentTypesInterpreterIfFail (
		final List<AvailObject> argTypes,
		final AvailInterpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock)
	{
		return descriptor().ObjectValidateArgumentTypesInterpreterIfFail(
			this,
			argTypes,
			anAvailInterpreter,
			failBlock);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int validity ()
	{
		return descriptor().ObjectValidity(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void validity (
		final int value)
	{
		descriptor().ObjectValidity(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject value ()
	{
		return descriptor().ObjectValue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void value (
		final AvailObject value)
	{
		descriptor().ObjectValue(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject valueAtIndex (
		final int index)
	{
		return descriptor().ObjectValueAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void valueAtIndexPut (
		final int index,
		final AvailObject valueObject)
	{
		descriptor().ObjectValueAtIndexPut(
			this,
			index,
			valueObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject valuesAsTuple ()
	{
		return descriptor().ObjectValuesAsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject valueType ()
	{
		return descriptor().ObjectValueType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void valueType (
		final AvailObject value)
	{
		descriptor().ObjectValueType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject variableBindings ()
	{
		return descriptor().ObjectVariableBindings(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void variableBindings (
		final AvailObject value)
	{
		descriptor().ObjectVariableBindings(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject vectors ()
	{
		return descriptor().ObjectVectors(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void vectors (
		final AvailObject value)
	{
		descriptor().ObjectVectors(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void verify ()
	{
		descriptor().ObjectVerify(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject visibleNames ()
	{
		return descriptor().ObjectVisibleNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void visibleNames (
		final AvailObject value)
	{
		descriptor().ObjectVisibleNames(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int whichOne ()
	{
		return descriptor().ObjectWhichOne(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void whichOne (
		final int value)
	{
		descriptor().ObjectWhichOne(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject wordcodes ()
	{
		return descriptor().ObjectWordcodes(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void wordcodes (
		final AvailObject value)
	{
		descriptor().ObjectWordcodes(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int zoneForIndex (
		final int index)
	{
		return descriptor().ObjectZoneForIndex(this, index);
	}

	/**
	 * Recursively print the {@linkplain AvailObject receiver} to the {@link
	 * StringBuilder} unless it is already present in the {@linkplain List
	 * recursion list}. Printing will begin at the specified indent level,
	 * measured in horizontal tab characters.
	 * 
	 * <p>This operation exists primarily to provide useful representations of
	 * {@code AvailObject}s for Java-side debugging.</p>
	 * 
	 * @param builder A {@link StringBuilder}.
	 * @param recursionList A {@linkplain List list} containing {@link
	 *                      AvailObject}s already visited during the recursive
	 *                      print.
	 * @param indent The indent level, in horizontal tabs, at which the {@link
	 *               AvailObject} should be printed.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public void printOnAvoidingIndent (
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		error(
			"Subclass responsibility: printOnAvoidingIndent() in "
			+ getClass().getCanonicalName());
		return;
	}

	@Override
	public String toString ()
	{
		final StringBuilder stringBuilder = new StringBuilder(100);
		final List<AvailObject> recursionList = new ArrayList<AvailObject>(10);
		try
		{
			printOnAvoidingIndent(stringBuilder, recursionList, 1);
		}
		catch (final Throwable e)
		{
			stringBuilder.insert(
				0, "EXCEPTION while printing: " + e.toString() + "\n");
		}
		assert recursionList.size() == 0;
		return stringBuilder.toString();
	}



	// message redirection - translate

	public boolean greaterThan (
		final AvailObject another)
	{
		//  Translate >= into an inverted lessThan:.  This manual method simplifies renaming rules
		//  greatly, and avoids the need for an Object:greaterThan: in the Descriptors.

		// Reverse the arguments and dispatch to the argument's descriptor.
		return another.descriptor().ObjectLessThan(another, this);
	}

	public boolean greaterOrEqual (
		final AvailObject another)
	{
		//  Translate >= into an inverted lessOrEqual:.  This manual method simplifies renaming rules
		//  greatly, and avoids the need for an Object:greaterOrEqual: in the Descriptors.

		// Reverse the arguments and dispatch to the argument's descriptor.
		return another.descriptor().ObjectLessOrEqual(another, this);
	}

	public int size ()
	{
		//  Catch this because it's defined uselessly in Object.

		return descriptor().ObjectSize(this);
	}



	// primitive accessing

	public void assertObjectUnreachableIfMutable ()
	{
		//  Set up the object to report nice obvious errors if anyone ever accesses it again.

		assertObjectUnreachableIfMutableExcept(VoidDescriptor.voidObject());
	}

	public void assertObjectUnreachableIfMutableExcept (
		final AvailObject exceptMe)
	{
		//  Set up the object to report nice obvious errors if anyone ever accesses it again.

		checkValidAddress();
		if (!descriptor().isMutable())
		{
			return;
		}
		if (!CanDestroyObjects)
		{
			error("Don't invoke this if destructions are disallowed");
		}
		if (sameAddressAs(exceptMe))
		{
			error("What happened?  This object is also the excluded one.");
		}

		// Recursively invoke the iterator on the subobjects of self...
		AvailSubobjectVisitor vis = new AvailMarkUnreachableSubobjectVisitor(exceptMe);
		scanSubobjects(vis);
		setToInvalidDescriptor();
		return;
	}

	public void becomeIndirectionTo (
		final AvailObject anotherObject)
	{
		//  Turn me into an indirection to anotherObject.  WARNING: This alters my slots and descriptor.

		error("Subclass responsibility: becomeIndirectionTo: in Avail.AvailObject");
		return;
	}

	public short byteSlotAtByteIndex (
		final int index)
	{
		//  Extract the byte at the given byte-index.

		error("Subclass responsibility: byteSlotAtByteIndex: in Avail.AvailObject");
		return 0;
	}

	public void byteSlotAtByteIndexPut (
		final int index,
		final short aByte)
	{
		//  Store the byte at the given byte-index.

		error("Subclass responsibility: byteSlotAtByteIndex:put: in Avail.AvailObject");
		return;
	}

	public void checkValidAddress ()
	{
		//  Check if my address is valid.  Fail if it's outside all the current pages.

		error("Subclass responsibility: checkValidAddress in Avail.AvailObject");
		return;
	}

	public void checkValidAddressWithByteIndex (
		final int byteIndex)
	{
		//  Check if my address is valid.  Fail if it's outside all the current pages.

		error("Subclass responsibility: checkValidAddressWithByteIndex: in Avail.AvailObject");
		return;
	}

	public void checkWriteAtByteIndex (
		final int index)
	{
		descriptor().checkWriteAtByteIndex(index);
	}

	public Descriptor descriptor ()
	{
		error("Subclass responsibility: descriptor in Avail.AvailObject");
		return null;
	}

	public void descriptor (
		final Descriptor aDescriptor)
	{
		error("Subclass responsibility: descriptor: in Avail.AvailObject");
		return;
	}

	public short descriptorId ()
	{
		error("Subclass responsibility: descriptorId in Avail.AvailObject");
		return 0;
	}

	public void descriptorId (
		final short anInteger)
	{
		error("Subclass responsibility: descriptorId: in Avail.AvailObject");
		return;
	}

	public int integerSlotAtByteIndex (
		final int index)
	{
		//  Extract the (unsigned 32-bit) integer at the given byte-index.

		error("Subclass responsibility: integerSlotAtByteIndex: in Avail.AvailObject");
		return 0;
	}

	public void integerSlotAtByteIndexPut (
		final int index,
		final int anInteger)
	{
		//  Store the (unsigned 32-bit) integer in the four bytes starting at the given byte-index.

		error("Subclass responsibility: integerSlotAtByteIndex:put: in Avail.AvailObject");
		return;
	}

	public int integerSlotsCount ()
	{
		error("Subclass responsibility: integerSlotsCount in Avail.AvailObject");
		return 0;
	}

	public boolean isDestroyed ()
	{
		checkValidAddress();
		return descriptorId() == FillerDescriptor.mutableDescriptor().id();
	}

	public AvailObject objectSlotAtByteIndex (
		final int index)
	{
		//  Extract the object at the given byte-index.  It must be an object.

		error("Subclass responsibility: objectSlotAtByteIndex: in Avail.AvailObject");
		return VoidDescriptor.voidObject();
	}

	public void objectSlotAtByteIndexPut (
		final int index,
		final AvailObject anAvailObject)
	{
		//  Store the object at the given byte-index.

		error("Subclass responsibility: objectSlotAtByteIndex:put: in Avail.AvailObject");
		return;
	}

	public int objectSlotsCount ()
	{
		error("Subclass responsibility: objectSlotsCount in Avail.AvailObject");
		return 0;
	}

	public boolean sameAddressAs (
		final AvailObject anotherObject)
	{
		//  Answer whether the objects occupy the same memory addresses.

		error("Subclass responsibility: sameAddressAs: in Avail.AvailObject");
		return false;
	}

	public void setToInvalidDescriptor ()
	{
		//  Replace my descriptor field with a FillerDescriptor.  This blows up for most messages, catching
		//  incorrect (all, by definition) further uses of this object.

		verifyToSpaceAddress();
		descriptor(FillerDescriptor.mutableDescriptor());
	}

	public short shortSlotAtByteIndex (
		final int index)
	{
		//  Extract the 16-bit signed integer at the given byte-index.

		error("Subclass responsibility: shortSlotAtByteIndex: in Avail.AvailObject");
		return 0;
	}

	public void shortSlotAtByteIndexPut (
		final int index,
		final short aShort)
	{
		//  Store the byte at the given byte-index.

		error("Subclass responsibility: shortSlotAtByteIndex:put: in Avail.AvailObject");
		return;
	}

	public void truncateWithFillerForNewIntegerSlotsCount (
		final int newSlotsCount)
	{
		//  Slice the current object into two objects, the left one (at the same starting
		//  address as the input), and the right one (a Filler object that nobody should
		//  ever create a pointer to).  The new Filler can have zero post-header slots
		//  (i.e., just the header), but the left object must not, since it may turn into an
		//  Indirection some day and will require at least one slot for the target pointer.

		error("Subclass responsibility: truncateWithFillerForNewIntegerSlotsCount: in Avail.AvailObject");
		return;
	}

	public void truncateWithFillerForNewObjectSlotsCount (
		final int newSlotsCount)
	{
		//  Slice the current object into two parts, one of which is a Filler object and
		//  is never refered to directly (so doesn't need any slots for becoming an
		//  indirection.

		error("Subclass responsibility: truncateWithFillerForNewObjectSlotsCount: in Avail.AvailObject");
		return;
	}

	public void verifyFromSpaceAddress ()
	{
		//  Check that my address is a valid pointer to FromSpace.

		error("Subclass responsibility: verifyFromSpaceAddress in Avail.AvailObject");
		return;
	}

	public void verifyToSpaceAddress ()
	{
		//  Check that my address is a valid pointer to ToSpace.

		error("Subclass responsibility: verifyToSpaceAddress in Avail.AvailObject");
		return;
	}



	// special methods

	public int hash ()
	{
		//  Object also implements hash.

		return descriptor().ObjectHash(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hash (
		final int value)
	{
		descriptor().ObjectHash(this, value);
	}

	@Override
	public int hashCode ()
	{
		//  Implemented so Java can use AvailObjects in Sets and Maps.

		return descriptor().ObjectHash(this);
	}



	public static void clearAllWellKnownObjects ()
	{
		VoidDescriptor.clearWellKnownObjects();
		TupleDescriptor.clearWellKnownObjects();
		BlankDescriptor.clearWellKnownObjects();
		TypeDescriptor.clearWellKnownObjects();
		BooleanDescriptor.clearWellKnownObjects();
		MapDescriptor.clearWellKnownObjects();
		CharacterDescriptor.clearWellKnownObjects();
		SetDescriptor.clearWellKnownObjects();
		InfinityDescriptor.clearWellKnownObjects();
		IntegerDescriptor.clearWellKnownObjects();
		Descriptor.clearWellKnownObjects();
		IntegerRangeTypeDescriptor.clearWellKnownObjects();
		L2ChunkDescriptor.clearWellKnownObjects();
	}

	public static void createAllWellKnownObjects ()
	{
		VoidDescriptor.createWellKnownObjects();
		TupleDescriptor.createWellKnownObjects();
		BlankDescriptor.createWellKnownObjects();
		TypeDescriptor.createWellKnownObjects();
		BooleanDescriptor.createWellKnownObjects();
		MapDescriptor.createWellKnownObjects();
		CharacterDescriptor.createWellKnownObjects();
		SetDescriptor.createWellKnownObjects();
		InfinityDescriptor.createWellKnownObjects();
		IntegerDescriptor.createWellKnownObjects();
		Descriptor.createWellKnownObjects();
		IntegerRangeTypeDescriptor.createWellKnownObjects();
		L2ChunkDescriptor.createWellKnownObjects();
	}

	public static void error(Object... args)
	{
		throw new RuntimeException((String)args[0]);
	};

	public static AvailObject newIndexedDescriptor(int size, Descriptor descriptor)
	{
		return AvailObjectUsingArrays.newIndexedDescriptor(size, descriptor);
	};

	public static AvailObject newObjectIndexedIntegerIndexedDescriptor(
		int variableObjectSlots,
		int variableIntegerSlots,
		Descriptor descriptor)
	{
		return AvailObjectUsingArrays.newObjectIndexedIntegerIndexedDescriptor(
			variableObjectSlots,
			variableIntegerSlots,
			descriptor);
	};

	static int scanAnObject()
	{
		// Scan the next object, saving its subobjects and removing its barrier.
		return 0;   // AvailObjectUsingArrays.scanAnObject();
	};


	/* Synchronization with GC.  Locked objects can be moved, but not coalesced. */

	private static AvailObject [] LockedObjects = new AvailObject [1000];

	private static int NumLockedObjects = 0;

	public static void lock (AvailObject obj)
	{
		LockedObjects [NumLockedObjects] = obj;
		NumLockedObjects++;
	};
	public static void unlock (AvailObject obj)
	{
		NumLockedObjects--;
		LockedObjects [NumLockedObjects] = null;
	};

	private static boolean CanAllocateObjects = true;
	private static boolean CanDestroyObjects = true;

	public static void CanAllocateObjects (boolean flag)
	{
		CanAllocateObjects = flag;
	}

	public static boolean CanAllocateObjects ()
	{
		return CanAllocateObjects;
	}

	public static void CanDestroyObjects (boolean flag)
	{
		CanDestroyObjects = flag;
	}

	public static boolean CanDestroyObjects ()
	{
		return CanDestroyObjects;
	}
}
