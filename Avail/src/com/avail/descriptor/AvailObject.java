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
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.compiler.Continuation1;
import com.avail.compiler.Generator;
import com.avail.interpreter.AvailInterpreter;
import com.avail.visitor.AvailMarkUnreachableSubobjectVisitor;
import com.avail.visitor.AvailSubobjectVisitor;

public abstract class AvailObject
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

	public boolean acceptsArgTypesFromClosureType (
		final AvailObject closureType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAcceptsArgTypesFromClosureType(this, closureType);
	}

	public boolean acceptsArgumentsFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAcceptsArgumentsFromContinuationStackp(
			this,
			continuation,
			stackp);
	}

	public boolean acceptsArgumentTypesFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAcceptsArgumentTypesFromContinuationStackp(
			this,
			continuation,
			stackp);
	}

	public boolean acceptsArrayOfArgTypes (
		final List<AvailObject> argTypes)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAcceptsArrayOfArgTypes(this, argTypes);
	}

	public boolean acceptsArrayOfArgValues (
		final List<AvailObject> argValues)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAcceptsArrayOfArgValues(this, argValues);
	}

	public boolean acceptsTupleOfArgTypes (
		final AvailObject argTypes)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAcceptsTupleOfArgTypes(this, argTypes);
	}

	public boolean acceptsTupleOfArguments (
		final AvailObject arguments)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAcceptsTupleOfArguments(this, arguments);
	}

	public void addDependentChunkId (
		final int aChunkIndex)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectAddDependentChunkId(this, aChunkIndex);
	}

	public void addImplementation (
		final AvailObject implementation)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectAddImplementation(this, implementation);
	}

	public void addRestrictions (
		final AvailObject restrictions)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectAddRestrictions(this, restrictions);
	}

	public AvailObject addToInfinityCanDestroy (
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAddToInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	public AvailObject addToIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAddToIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	public void argsLocalsStackOutersPrimitive (
		final int args,
		final int locals,
		final int stack,
		final int outers,
		final int primitive)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectArgsLocalsStackOutersPrimitive(
			this,
			args,
			locals,
			stack,
			outers,
			primitive);
	}

	public AvailObject argTypeAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectArgTypeAt(this, index);
	}

	public void argTypeAtPut (
		final int index,
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectArgTypeAtPut(
			this,
			index,
			value);
	}

	public String asNativeString ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAsNativeString(this);
	}

	public AvailObject asObject ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAsObject(this);
	}

	public AvailObject asSet ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAsSet(this);
	}

	public AvailObject asTuple ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectAsTuple(this);
	}

	public void atAddMessageRestrictions (
		final AvailObject methodName,
		final AvailObject illegalArgMsgs)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectAtAddMessageRestrictions(
			this,
			methodName,
			illegalArgMsgs);
	}

	public void atAddMethodImplementation (
		final AvailObject methodName,
		final AvailObject implementation)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectAtAddMethodImplementation(
			this,
			methodName,
			implementation);
	}

	public void atMessageAddBundle (
		final AvailObject message,
		final AvailObject bundle)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectAtMessageAddBundle(
			this,
			message,
			bundle);
	}

	public void atNameAdd (
		final AvailObject stringName,
		final AvailObject trueName)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectAtNameAdd(
			this,
			stringName,
			trueName);
	}

	public void atNewNamePut (
		final AvailObject stringName,
		final AvailObject trueName)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectAtNewNamePut(
			this,
			stringName,
			trueName);
	}

	public void atPrivateNameAdd (
		final AvailObject stringName,
		final AvailObject trueName)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectAtPrivateNameAdd(
			this,
			stringName,
			trueName);
	}

	public AvailObject becomeExactType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBecomeExactType(this);
	}

	public void becomeRealTupleType ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectBecomeRealTupleType(this);
	}

	public AvailObject binAddingElementHashLevelCanDestroy (
		final AvailObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBinAddingElementHashLevelCanDestroy(
			this,
			elementObject,
			elementObjectHash,
			myLevel,
			canDestroy);
	}

	public AvailObject binElementAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBinElementAt(this, index);
	}

	public void binElementAtPut (
		final int index,
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectBinElementAtPut(
			this,
			index,
			value);
	}

	public boolean binHasElementHash (
		final AvailObject elementObject,
		final int elementObjectHash)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBinHasElementHash(
			this,
			elementObject,
			elementObjectHash);
	}

	public int binHash ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBinHash(this);
	}

	public void binHash (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectBinHash(this, value);
	}

	public AvailObject binRemoveElementHashCanDestroy (
		final AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBinRemoveElementHashCanDestroy(
			this,
			elementObject,
			elementObjectHash,
			canDestroy);
	}

	public int binSize ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBinSize(this);
	}

	public void binSize (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectBinSize(this, value);
	}

	public AvailObject binUnionType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBinUnionType(this);
	}

	public void binUnionType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectBinUnionType(this, value);
	}

	public int bitsPerEntry ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBitsPerEntry(this);
	}

	public int bitVector ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBitVector(this);
	}

	public void bitVector (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectBitVector(this, value);
	}

	public AvailObject bodyBlock ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBodyBlock(this);
	}

	public void bodyBlock (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectBodyBlock(this, value);
	}

	public void bodyBlockRequiresBlockReturnsBlock (
		final AvailObject bb,
		final AvailObject rqb,
		final AvailObject rtb)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectBodyBlockRequiresBlockReturnsBlock(
			this,
			bb,
			rqb,
			rtb);
	}

	public AvailObject bodySignature ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBodySignature(this);
	}

	public void bodySignature (
		final AvailObject signature)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectBodySignature(this, signature);
	}

	public void bodySignatureRequiresBlockReturnsBlock (
		final AvailObject bs,
		final AvailObject rqb,
		final AvailObject rtb)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectBodySignatureRequiresBlockReturnsBlock(
			this,
			bs,
			rqb,
			rtb);
	}

	public AvailObject breakpointBlock ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBreakpointBlock(this);
	}

	public void breakpointBlock (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectBreakpointBlock(this, value);
	}

	public void buildFilteredBundleTreeFrom (
		final AvailObject bundleTree)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectBuildFilteredBundleTreeFrom(this, bundleTree);
	}

	public AvailObject bundleAtMessageParts (
		final AvailObject message,
		final AvailObject parts)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectBundleAtMessageParts(
			this,
			message,
			parts);
	}

	public AvailObject caller ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCaller(this);
	}

	public void caller (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectCaller(this, value);
	}

	public boolean canComputeHashOfType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCanComputeHashOfType(this);
	}

	public int capacity ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCapacity(this);
	}

	public void cleanUpAfterCompile ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectCleanUpAfterCompile(this);
	}

	public void clearModule ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectClearModule(this);
	}

	public void clearValue ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectClearValue(this);
	}

	public AvailObject closure ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectClosure(this);
	}

	public void closure (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectClosure(this, value);
	}

	public AvailObject closureType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectClosureType(this);
	}

	public void closureType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectClosureType(this, value);
	}

	public AvailObject code ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCode(this);
	}

	public void code (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectCode(this, value);
	}

	public int codePoint ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCodePoint(this);
	}

	public void codePoint (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectCodePoint(this, value);
	}

	public boolean compareFromToWithStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject anotherObject,
		final int startIndex2)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCompareFromToWithStartingAt(
			this,
			startIndex1,
			endIndex1,
			anotherObject,
			startIndex2);
	}

	public boolean compareFromToWithAnyTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTuple,
		final int startIndex2)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCompareFromToWithAnyTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aTuple,
			startIndex2);
	}

	public boolean compareFromToWithByteStringStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteString,
		final int startIndex2)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCompareFromToWithByteStringStartingAt(
			this,
			startIndex1,
			endIndex1,
			aByteString,
			startIndex2);
	}

	public boolean compareFromToWithByteTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteTuple,
		final int startIndex2)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCompareFromToWithByteTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aByteTuple,
			startIndex2);
	}

	public boolean compareFromToWithNybbleTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject aNybbleTuple,
		final int startIndex2)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCompareFromToWithNybbleTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aNybbleTuple,
			startIndex2);
	}

	public boolean compareFromToWithObjectTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject anObjectTuple,
		final int startIndex2)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCompareFromToWithObjectTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			anObjectTuple,
			startIndex2);
	}

	public boolean compareFromToWithTwoByteStringStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTwoByteString,
		final int startIndex2)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCompareFromToWithTwoByteStringStartingAt(
			this,
			startIndex1,
			endIndex1,
			aTwoByteString,
			startIndex2);
	}

	public AvailObject complete ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectComplete(this);
	}

	public void complete (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectComplete(this, value);
	}

	public int computeHashFromTo (
		final int start,
		final int end)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectComputeHashFromTo(
			this,
			start,
			end);
	}

	public AvailObject computeReturnTypeFromArgumentTypesInterpreter (
		final List<AvailObject> argTypes,
		final AvailInterpreter anAvailInterpreter)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectComputeReturnTypeFromArgumentTypesInterpreter(
			this,
			argTypes,
			anAvailInterpreter);
	}

	public AvailObject concatenateTuplesCanDestroy (
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectConcatenateTuplesCanDestroy(this, canDestroy);
	}

	public AvailObject constantBindings ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectConstantBindings(this);
	}

	public void constantBindings (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectConstantBindings(this, value);
	}

	public boolean containsBlock (
		final AvailObject aClosure)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectContainsBlock(this, aClosure);
	}

	public AvailObject contentType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectContentType(this);
	}

	public void contentType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectContentType(this, value);
	}

	public AvailObject contingentImpSets ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectContingentImpSets(this);
	}

	public void contingentImpSets (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectContingentImpSets(this, value);
	}

	public AvailObject continuation ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectContinuation(this);
	}

	public void continuation (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectContinuation(this, value);
	}

	public AvailObject copyAsMutableContinuation ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCopyAsMutableContinuation(this);
	}

	public AvailObject copyAsMutableObjectTuple ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCopyAsMutableObjectTuple(this);
	}

	public AvailObject copyAsMutableSpliceTuple ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCopyAsMutableSpliceTuple(this);
	}

	public AvailObject copyMutable ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCopyMutable(this);
	}

	public void copyToRestrictedTo (
		final AvailObject filteredBundleTree,
		final AvailObject visibleNames)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectCopyToRestrictedTo(
			this,
			filteredBundleTree,
			visibleNames);
	}

	public AvailObject copyTupleFromToCanDestroy (
		final int start,
		final int end,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCopyTupleFromToCanDestroy(
			this,
			start,
			end,
			canDestroy);
	}

	public boolean couldEverBeInvokedWith (
		final ArrayList<AvailObject> argTypes)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCouldEverBeInvokedWith(this, argTypes);
	}

	public AvailObject createTestingTreeWithPositiveMatchesRemainingPossibilities (
		final AvailObject positiveTuple,
		final AvailObject possibilities)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectCreateTestingTreeWithPositiveMatchesRemainingPossibilities(
			this,
			positiveTuple,
			possibilities);
	}

	public AvailObject dataAtIndex (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectDataAtIndex(this, index);
	}

	public void dataAtIndexPut (
		final int index,
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectDataAtIndexPut(
			this,
			index,
			value);
	}

	public AvailObject defaultType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectDefaultType(this);
	}

	public void defaultType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectDefaultType(this, value);
	}

	public AvailObject dependentChunks ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectDependentChunks(this);
	}

	public void dependentChunks (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectDependentChunks(this, value);
	}

	public int depth ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectDepth(this);
	}

	public void depth (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectDepth(this, value);
	}

	public void displayTestingTree ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectDisplayTestingTree(this);
	}

	public AvailObject divideCanDestroy (
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectDivideCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	public AvailObject divideIntoInfinityCanDestroy (
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectDivideIntoInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	public AvailObject divideIntoIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectDivideIntoIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	public AvailObject elementAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectElementAt(this, index);
	}

	public void elementAtPut (
		final int index,
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectElementAtPut(
			this,
			index,
			value);
	}

	public int endOfZone (
		final int zone)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEndOfZone(this, zone);
	}

	public int endSubtupleIndexInZone (
		final int zone)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEndSubtupleIndexInZone(this, zone);
	}

	public void ensureMetacovariant ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectEnsureMetacovariant(this);
	}

	public AvailObject ensureMutable ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEnsureMutable(this);
	}

	public boolean equals (
		final AvailObject another)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEquals(this, another);
	}

	public boolean equalsAnyTuple (
		final AvailObject anotherTuple)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsAnyTuple(this, anotherTuple);
	}

	public boolean equalsBlank ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsBlank(this);
	}

	public boolean equalsByteString (
		final AvailObject aByteString)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsByteString(this, aByteString);
	}

	public boolean equalsByteTuple (
		final AvailObject aByteTuple)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsByteTuple(this, aByteTuple);
	}

	public boolean equalsCharacterWithCodePoint (
		final int otherCodePoint)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsCharacterWithCodePoint(this, otherCodePoint);
	}

	public boolean equalsClosure (
		final AvailObject aClosure)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsClosure(this, aClosure);
	}

	public boolean equalsClosureType (
		final AvailObject aClosureType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsClosureType(this, aClosureType);
	}

	public boolean equalsCompiledCode (
		final AvailObject aCompiledCode)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsCompiledCode(this, aCompiledCode);
	}

	public boolean equalsContainer (
		final AvailObject aContainer)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsContainer(this, aContainer);
	}

	public boolean equalsContainerType (
		final AvailObject aContainerType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsContainerType(this, aContainerType);
	}

	public boolean equalsContinuation (
		final AvailObject aContinuation)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsContinuation(this, aContinuation);
	}

	public boolean equalsContinuationType (
		final AvailObject aType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsContinuationType(this, aType);
	}

	public boolean equalsDouble (
		final AvailObject aDoubleObject)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsDouble(this, aDoubleObject);
	}

	public boolean equalsFalse ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsFalse(this);
	}

	public boolean equalsFloat (
		final AvailObject aFloatObject)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsFloat(this, aFloatObject);
	}

	public boolean equalsGeneralizedClosureType (
		final AvailObject aType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsGeneralizedClosureType(this, aType);
	}

	public boolean equalsInfinity (
		final AvailObject anInfinity)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsInfinity(this, anInfinity);
	}

	public boolean equalsInteger (
		final AvailObject anAvailInteger)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsInteger(this, anAvailInteger);
	}

	public boolean equalsIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsIntegerRangeType(this, anIntegerRangeType);
	}

	public boolean equalsList (
		final AvailObject aList)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsList(this, aList);
	}

	public boolean equalsListType (
		final AvailObject aListType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsListType(this, aListType);
	}

	public boolean equalsMap (
		final AvailObject aMap)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsMap(this, aMap);
	}

	public boolean equalsMapType (
		final AvailObject aMapType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsMapType(this, aMapType);
	}

	public boolean equalsNybbleTuple (
		final AvailObject aNybbleTuple)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsNybbleTuple(this, aNybbleTuple);
	}

	public boolean equalsObject (
		final AvailObject anObject)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsObject(this, anObject);
	}

	public boolean equalsObjectTuple (
		final AvailObject anObjectTuple)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsObjectTuple(this, anObjectTuple);
	}

	public boolean equalsPrimitiveType (
		final AvailObject aPrimitiveType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsPrimitiveType(this, aPrimitiveType);
	}

	public boolean equalsSet (
		final AvailObject aSet)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsSet(this, aSet);
	}

	public boolean equalsSetType (
		final AvailObject aSetType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsSetType(this, aSetType);
	}

	public boolean equalsTrue ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsTrue(this);
	}

	public boolean equalsTupleType (
		final AvailObject aTupleType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsTupleType(this, aTupleType);
	}

	public boolean equalsTwoByteString (
		final AvailObject aTwoByteString)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsTwoByteString(this, aTwoByteString);
	}

	public boolean equalsVoid ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsVoid(this);
	}

	public boolean equalsVoidOrBlank ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectEqualsVoidOrBlank(this);
	}

	public void evictedByGarbageCollector ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectEvictedByGarbageCollector(this);
	}

	public AvailObject exactType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectExactType(this);
	}

	public int executionMode ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectExecutionMode(this);
	}

	public void executionMode (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectExecutionMode(this, value);
	}

	public int executionState ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectExecutionState(this);
	}

	public void executionState (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectExecutionState(this, value);
	}

	public AvailObject expand ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectExpand(this);
	}

	public boolean extractBoolean ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectExtractBoolean(this);
	}

	public short extractByte ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectExtractByte(this);
	}

	public double extractDouble ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectExtractDouble(this);
	}

	public float extractFloat ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectExtractFloat(this);
	}

	public int extractInt ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectExtractInt(this);
	}

	public byte extractNybble ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectExtractNybble(this);
	}

	public byte extractNybbleFromTupleAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectExtractNybbleFromTupleAt(this, index);
	}

	public AvailObject fieldMap ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectFieldMap(this);
	}

	public void fieldMap (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectFieldMap(this, value);
	}

	public AvailObject fieldTypeMap ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectFieldTypeMap(this);
	}

	public void fieldTypeMap (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectFieldTypeMap(this, value);
	}

	public List<AvailObject> filterByTypes (
		final List<AvailObject> argTypes)
		{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectFilterByTypes(this, argTypes);
		}

	public AvailObject filteredBundleTree ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectFilteredBundleTree(this);
	}

	public void filteredBundleTree (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectFilteredBundleTree(this, value);
	}

	public AvailObject firstTupleType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectFirstTupleType(this);
	}

	public void firstTupleType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectFirstTupleType(this, value);
	}

	public AvailObject forZoneSetSubtupleStartSubtupleIndexEndOfZone (
		final int zone,
		final AvailObject newSubtuple,
		final int startSubtupleIndex,
		final int endOfZone)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectForZoneSetSubtupleStartSubtupleIndexEndOfZone(
			this,
			zone,
			newSubtuple,
			startSubtupleIndex,
			endOfZone);
	}

	public int getInteger ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectGetInteger(this);
	}

	public AvailObject getValue ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectGetValue(this);
	}

	public boolean greaterThanInteger (
		final AvailObject another)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectGreaterThanInteger(this, another);
	}

	public boolean greaterThanSignedInfinity (
		final AvailObject another)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectGreaterThanSignedInfinity(this, another);
	}

	public boolean hasElement (
		final AvailObject elementObject)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectHasElement(this, elementObject);
	}

	public int hashFromTo (
		final int startIndex,
		final int endIndex)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectHashFromTo(
			this,
			startIndex,
			endIndex);
	}

	public int hashOfType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectHashOfType(this);
	}

	public int hashOrZero ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectHashOrZero(this);
	}

	public void hashOrZero (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectHashOrZero(this, value);
	}

	public boolean hasKey (
		final AvailObject keyObject)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectHasKey(this, keyObject);
	}

	public boolean hasObjectInstance (
		final AvailObject potentialInstance)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectHasObjectInstance(this, potentialInstance);
	}

	public boolean hasRestrictions ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectHasRestrictions(this);
	}

	public int hiLevelTwoChunkLowOffset ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectHiLevelTwoChunkLowOffset(this);
	}

	public void hiLevelTwoChunkLowOffset (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectHiLevelTwoChunkLowOffset(this, value);
	}

	public int hiNumLocalsLowNumArgs ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectHiNumLocalsLowNumArgs(this);
	}

	public void hiNumLocalsLowNumArgs (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectHiNumLocalsLowNumArgs(this, value);
	}

	public int hiPrimitiveLowNumArgsAndLocalsAndStack ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectHiPrimitiveLowNumArgsAndLocalsAndStack(this);
	}

	public void hiPrimitiveLowNumArgsAndLocalsAndStack (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectHiPrimitiveLowNumArgsAndLocalsAndStack(this, value);
	}

	public int hiStartingChunkIndexLowNumOuters ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectHiStartingChunkIndexLowNumOuters(this);
	}

	public void hiStartingChunkIndexLowNumOuters (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectHiStartingChunkIndexLowNumOuters(this, value);
	}

	public ArrayList<AvailObject> implementationsAtOrBelow (
		final ArrayList<AvailObject> argTypes)
		{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectImplementationsAtOrBelow(this, argTypes);
		}

	public AvailObject implementationsTuple ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectImplementationsTuple(this);
	}

	public void implementationsTuple (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectImplementationsTuple(this, value);
	}

	public AvailObject includeBundleAtMessageParts (
		final AvailObject message,
		final AvailObject parts)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIncludeBundleAtMessageParts(
			this,
			message,
			parts);
	}

	public boolean includes (
		final AvailObject imp)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIncludes(this, imp);
	}

	public int inclusiveFlags ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectInclusiveFlags(this);
	}

	public void inclusiveFlags (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectInclusiveFlags(this, value);
	}

	public AvailObject incomplete ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIncomplete(this);
	}

	public void incomplete (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectIncomplete(this, value);
	}

	public int index ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIndex(this);
	}

	public void index (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectIndex(this, value);
	}

	public AvailObject innerType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectInnerType(this);
	}

	public void innerType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectInnerType(this, value);
	}

	public AvailObject instance ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectInstance(this);
	}

	public void instance (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectInstance(this, value);
	}

	public int internalHash ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectInternalHash(this);
	}

	public void internalHash (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectInternalHash(this, value);
	}

	public int interruptRequestFlag ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectInterruptRequestFlag(this);
	}

	public void interruptRequestFlag (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectInterruptRequestFlag(this, value);
	}

	public int invocationCount ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectInvocationCount(this);
	}

	public void invocationCount (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectInvocationCount(this, value);
	}

	public boolean isAbstract ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsAbstract(this);
	}

	public boolean isBetterRepresentationThan (
		final AvailObject anotherObject)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsBetterRepresentationThan(this, anotherObject);
	}

	public boolean isBetterRepresentationThanTupleType (
		final AvailObject aTupleType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsBetterRepresentationThanTupleType(this, aTupleType);
	}

	public boolean isBinSubsetOf (
		final AvailObject potentialSuperset)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsBinSubsetOf(this, potentialSuperset);
	}

	public boolean isBoolean ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsBoolean(this);
	}

	public boolean isByte ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsByte(this);
	}

	public boolean isCharacter ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsCharacter(this);
	}

	public boolean isClosure ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsClosure(this);
	}

	public boolean isCyclicType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsCyclicType(this);
	}

	public boolean isExtendedInteger ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsExtendedInteger(this);
	}

	public boolean isFinite ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsFinite(this);
	}

	public boolean isForward ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsForward(this);
	}

	public boolean isHashAvailable ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsHashAvailable(this);
	}

	public boolean isImplementation ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsImplementation(this);
	}

	public boolean isInstanceOfSubtypeOf (
		final AvailObject aType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsInstanceOfSubtypeOf(this, aType);
	}

	public boolean isIntegerRangeType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsIntegerRangeType(this);
	}

	public boolean isList ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsList(this);
	}

	public boolean isListType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsListType(this);
	}

	public boolean isMap ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsMap(this);
	}

	public boolean isMapType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsMapType(this);
	}

	public boolean isNybble ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsNybble(this);
	}

	public boolean isPositive ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsPositive(this);
	}

	public boolean isSaved ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSaved(this);
	}

	public void isSaved (
		final boolean aBoolean)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectIsSaved(this, aBoolean);
	}

	public boolean isSet ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSet(this);
	}

	public boolean isSetType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSetType(this);
	}

	public boolean isSplice ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSplice(this);
	}

	public boolean isSubsetOf (
		final AvailObject another)
	{
		//  GENERATED for descriptor dispatch

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

	public boolean isSubtypeOf (
		final AvailObject aType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSubtypeOf(this, aType);
	}

	public boolean isSupertypeOfClosureType (
		final AvailObject aClosureType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfClosureType(this, aClosureType);
	}

	public boolean isSupertypeOfContainerType (
		final AvailObject aContainerType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfContainerType(this, aContainerType);
	}

	public boolean isSupertypeOfContinuationType (
		final AvailObject aContinuationType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfContinuationType(this, aContinuationType);
	}

	public boolean isSupertypeOfCyclicType (
		final AvailObject aCyclicType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfCyclicType(this, aCyclicType);
	}

	public boolean isSupertypeOfGeneralizedClosureType (
		final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfGeneralizedClosureType(this, aGeneralizedClosureType);
	}

	public boolean isSupertypeOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfIntegerRangeType(this, anIntegerRangeType);
	}

	public boolean isSupertypeOfListType (
		final AvailObject aListType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfListType(this, aListType);
	}

	public boolean isSupertypeOfMapType (
		final AvailObject aMapType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfMapType(this, aMapType);
	}

	public boolean isSupertypeOfObjectMeta (
		final AvailObject anObjectMeta)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfObjectMeta(this, anObjectMeta);
	}

	public boolean isSupertypeOfObjectMetaMeta (
		final AvailObject anObjectMetaMeta)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfObjectMetaMeta(this, anObjectMetaMeta);
	}

	public boolean isSupertypeOfObjectType (
		final AvailObject anObjectType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfObjectType(this, anObjectType);
	}

	public boolean isSupertypeOfPrimitiveType (
		final AvailObject aPrimitiveType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfPrimitiveType(this, aPrimitiveType);
	}

	public boolean isSupertypeOfSetType (
		final AvailObject aSetType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfSetType(this, aSetType);
	}

	public boolean isSupertypeOfTerminates ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfTerminates(this);
	}

	public boolean isSupertypeOfTupleType (
		final AvailObject aTupleType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfTupleType(this, aTupleType);
	}

	public boolean isSupertypeOfVoid ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsSupertypeOfVoid(this);
	}

	public boolean isTuple ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsTuple(this);
	}

	public boolean isTupleType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsTupleType(this);
	}

	public boolean isType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsType(this);
	}

	public boolean isValid ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsValid(this);
	}

	public void isValid (
		final boolean aBoolean)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectIsValid(this, aBoolean);
	}

	public boolean isValidForArgumentTypesInterpreter (
		final List<AvailObject> argTypes,
		final AvailInterpreter interpreter)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectIsValidForArgumentTypesInterpreter(
			this,
			argTypes,
			interpreter);
	}

	public AvailObject keyAtIndex (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectKeyAtIndex(this, index);
	}

	public void keyAtIndexPut (
		final int index,
		final AvailObject keyObject)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectKeyAtIndexPut(
			this,
			index,
			keyObject);
	}

	public List<AvailObject> keysAsArray ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectKeysAsArray(this);
	}

	public AvailObject keysAsSet ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectKeysAsSet(this);
	}

	public AvailObject keyType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectKeyType(this);
	}

	public void keyType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectKeyType(this, value);
	}

	public boolean lessOrEqual (
		final AvailObject another)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLessOrEqual(this, another);
	}

	public boolean lessThan (
		final AvailObject another)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLessThan(this, another);
	}

	public int levelTwoChunkIndex ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLevelTwoChunkIndex(this);
	}

	public void levelTwoChunkIndexOffset (
		final int index,
		final int offset)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectLevelTwoChunkIndexOffset(
			this,
			index,
			offset);
	}

	public int levelTwoOffset ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLevelTwoOffset(this);
	}

	public AvailObject literalAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLiteralAt(this, index);
	}

	public void literalAtPut (
		final int index,
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectLiteralAtPut(
			this,
			index,
			value);
	}

	public AvailObject localOrArgOrStackAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLocalOrArgOrStackAt(this, index);
	}

	public void localOrArgOrStackAtPut (
		final int index,
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectLocalOrArgOrStackAtPut(
			this,
			index,
			value);
	}

	public AvailObject localTypeAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLocalTypeAt(this, index);
	}

	public AvailObject lookupByTypesFromArray (
		final List<AvailObject> argumentTypeArray)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLookupByTypesFromArray(this, argumentTypeArray);
	}

	public AvailObject lookupByTypesFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLookupByTypesFromContinuationStackp(
			this,
			continuation,
			stackp);
	}

	public AvailObject lookupByTypesFromTuple (
		final AvailObject argumentTypeTuple)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLookupByTypesFromTuple(this, argumentTypeTuple);
	}

	public AvailObject lookupByValuesFromArray (
		final List<AvailObject> argumentArray)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLookupByValuesFromArray(this, argumentArray);
	}

	public AvailObject lookupByValuesFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLookupByValuesFromContinuationStackp(
			this,
			continuation,
			stackp);
	}

	public AvailObject lookupByValuesFromTuple (
		final AvailObject argumentTuple)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLookupByValuesFromTuple(this, argumentTuple);
	}

	public AvailObject lowerBound ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLowerBound(this);
	}

	public void lowerBound (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectLowerBound(this, value);
	}

	public boolean lowerInclusive ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectLowerInclusive(this);
	}

	public void lowerInclusiveUpperInclusive (
		final boolean lowInc,
		final boolean highInc)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectLowerInclusiveUpperInclusive(
			this,
			lowInc,
			highInc);
	}

	public AvailObject makeImmutable ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMakeImmutable(this);
	}

	public void makeSubobjectsImmutable ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectMakeSubobjectsImmutable(this);
	}

	public AvailObject mapAt (
		final AvailObject keyObject)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMapAt(this, keyObject);
	}

	public AvailObject mapAtPuttingCanDestroy (
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMapAtPuttingCanDestroy(
			this,
			keyObject,
			newValueObject,
			canDestroy);
	}

	public int mapSize ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMapSize(this);
	}

	public void mapSize (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectMapSize(this, value);
	}

	public AvailObject mapWithoutKeyCanDestroy (
		final AvailObject keyObject,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMapWithoutKeyCanDestroy(
			this,
			keyObject,
			canDestroy);
	}

	public short maxStackDepth ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMaxStackDepth(this);
	}

	public AvailObject message ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMessage(this);
	}

	public void message (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectMessage(this, value);
	}

	public AvailObject messageParts ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMessageParts(this);
	}

	public void messageParts (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectMessageParts(this, value);
	}

	public AvailObject methods ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMethods(this);
	}

	public void methods (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectMethods(this, value);
	}

	public AvailObject minusCanDestroy (
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMinusCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	public void moveToHead ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectMoveToHead(this);
	}

	public AvailObject multiplyByInfinityCanDestroy (
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMultiplyByInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	public AvailObject multiplyByIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMultiplyByIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	public AvailObject myObjectMeta ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMyObjectMeta(this);
	}

	public void myObjectMeta (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectMyObjectMeta(this, value);
	}

	public AvailObject myObjectType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMyObjectType(this);
	}

	public void myObjectType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectMyObjectType(this, value);
	}

	public AvailObject myRestrictions ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMyRestrictions(this);
	}

	public void myRestrictions (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectMyRestrictions(this, value);
	}

	public AvailObject myType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectMyType(this);
	}

	public void myType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectMyType(this, value);
	}

	public AvailObject name ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectName(this);
	}

	public void name (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectName(this, value);
	}

	public AvailObject names ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNames(this);
	}

	public void names (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectNames(this, value);
	}

	public boolean nameVisible (
		final AvailObject trueName)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNameVisible(this, trueName);
	}

	public void necessaryImplementationSetChanged (
		final AvailObject anImplementationSet)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectNecessaryImplementationSetChanged(this, anImplementationSet);
	}

	public AvailObject newNames ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNewNames(this);
	}

	public void newNames (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectNewNames(this, value);
	}

	public AvailObject next ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNext(this);
	}

	public void next (
		final AvailObject nextChunk)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectNext(this, nextChunk);
	}

	public int nextIndex ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNextIndex(this);
	}

	public void nextIndex (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectNextIndex(this, value);
	}

	public short numArgs ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNumArgs(this);
	}

	public short numArgsAndLocalsAndStack ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNumArgsAndLocalsAndStack(this);
	}

	public int numberOfZones ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNumberOfZones(this);
	}

	public int numBlanks ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNumBlanks(this);
	}

	public void numBlanks (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectNumBlanks(this, value);
	}

	public int numFloats ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNumFloats(this);
	}

	public void numFloats (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectNumFloats(this, value);
	}

	public int numIntegers ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNumIntegers(this);
	}

	public void numIntegers (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectNumIntegers(this, value);
	}

	public short numLiterals ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNumLiterals(this);
	}

	public short numLocals ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNumLocals(this);
	}

	public int numLocalsOrArgsOrStack ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNumLocalsOrArgsOrStack(this);
	}

	public int numObjects ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNumObjects(this);
	}

	public void numObjects (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectNumObjects(this, value);
	}

	public short numOuters ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNumOuters(this);
	}

	public int numOuterVars ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNumOuterVars(this);
	}

	public AvailObject nybbles ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectNybbles(this);
	}

	public void nybbles (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectNybbles(this, value);
	}

	public boolean optionallyNilOuterVar (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectOptionallyNilOuterVar(this, index);
	}

	public AvailObject outerTypeAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectOuterTypeAt(this, index);
	}

	public void outerTypesLocalTypes (
		final AvailObject tupleOfOuterTypes,
		final AvailObject tupleOfLocalContainerTypes)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectOuterTypesLocalTypes(
			this,
			tupleOfOuterTypes,
			tupleOfLocalContainerTypes);
	}

	public AvailObject outerVarAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectOuterVarAt(this, index);
	}

	public void outerVarAtPut (
		final int index,
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectOuterVarAtPut(
			this,
			index,
			value);
	}

	public AvailObject pad ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPad(this);
	}

	public void pad (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectPad(this, value);
	}

	public AvailObject parent ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectParent(this);
	}

	public void parent (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectParent(this, value);
	}

	public int pc ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPc(this);
	}

	public void pc (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectPc(this, value);
	}

	public AvailObject plusCanDestroy (
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPlusCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	public int populateTupleStartingAt (
		final AvailObject mutableTuple,
		final int startingIndex)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPopulateTupleStartingAt(
			this,
			mutableTuple,
			startingIndex);
	}

	public void postFault ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectPostFault(this);
	}

	public AvailObject previous ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPrevious(this);
	}

	public void previous (
		final AvailObject previousChunk)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectPrevious(this, previousChunk);
	}

	public int previousIndex ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPreviousIndex(this);
	}

	public void previousIndex (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectPreviousIndex(this, value);
	}

	public short primitiveNumber ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPrimitiveNumber(this);
	}

	public int priority ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPriority(this);
	}

	public void priority (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectPriority(this, value);
	}

	public AvailObject privateAddElement (
		final AvailObject element)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPrivateAddElement(this, element);
	}

	public AvailObject privateExcludeElement (
		final AvailObject element)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPrivateExcludeElement(this, element);
	}

	public AvailObject privateExcludeElementKnownIndex (
		final AvailObject element,
		final int knownIndex)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPrivateExcludeElementKnownIndex(
			this,
			element,
			knownIndex);
	}

	public AvailObject privateExcludeKey (
		final AvailObject keyObject)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPrivateExcludeKey(this, keyObject);
	}

	public AvailObject privateMapAtPut (
		final AvailObject keyObject,
		final AvailObject valueObject)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPrivateMapAtPut(
			this,
			keyObject,
			valueObject);
	}

	public AvailObject privateNames ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPrivateNames(this);
	}

	public void privateNames (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectPrivateNames(this, value);
	}

	public AvailObject privateTestingTree ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectPrivateTestingTree(this);
	}

	public void privateTestingTree (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectPrivateTestingTree(this, value);
	}

	public AvailObject processGlobals ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectProcessGlobals(this);
	}

	public void processGlobals (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectProcessGlobals(this, value);
	}

	public short rawByteAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRawByteAt(this, index);
	}

	public void rawByteAtPut (
		final int index,
		final short anInteger)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRawByteAtPut(
			this,
			index,
			anInteger);
	}

	public short rawByteForCharacterAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRawByteForCharacterAt(this, index);
	}

	public void rawByteForCharacterAtPut (
		final int index,
		final short anInteger)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRawByteForCharacterAtPut(
			this,
			index,
			anInteger);
	}

	public byte rawNybbleAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRawNybbleAt(this, index);
	}

	public void rawNybbleAtPut (
		final int index,
		final byte aNybble)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRawNybbleAtPut(
			this,
			index,
			aNybble);
	}

	public int rawQuad1 ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRawQuad1(this);
	}

	public void rawQuad1 (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRawQuad1(this, value);
	}

	public int rawQuad2 ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRawQuad2(this);
	}

	public void rawQuad2 (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRawQuad2(this, value);
	}

	public int rawQuadAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRawQuadAt(this, index);
	}

	public void rawQuadAtPut (
		final int index,
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRawQuadAtPut(
			this,
			index,
			value);
	}

	public short rawShortForCharacterAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRawShortForCharacterAt(this, index);
	}

	public void rawShortForCharacterAtPut (
		final int index,
		final short anInteger)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRawShortForCharacterAtPut(
			this,
			index,
			anInteger);
	}

	public int rawSignedIntegerAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRawSignedIntegerAt(this, index);
	}

	public void rawSignedIntegerAtPut (
		final int index,
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRawSignedIntegerAtPut(
			this,
			index,
			value);
	}

	public long rawUnsignedIntegerAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRawUnsignedIntegerAt(this, index);
	}

	public void rawUnsignedIntegerAtPut (
		final int index,
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRawUnsignedIntegerAtPut(
			this,
			index,
			value);
	}

	public void readBarrierFault ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectReadBarrierFault(this);
	}

	public void releaseVariableOrMakeContentsImmutable ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectReleaseVariableOrMakeContentsImmutable(this);
	}

	public void removeDependentChunkId (
		final int aChunkIndex)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRemoveDependentChunkId(this, aChunkIndex);
	}

	public void removeFrom (
		final AvailInterpreter anInterpreter)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRemoveFrom(this, anInterpreter);
	}

	public void removeFromQueue ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRemoveFromQueue(this);
	}

	public void removeImplementation (
		final AvailObject implementation)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRemoveImplementation(this, implementation);
	}

	public boolean removeMessageParts (
		final AvailObject message,
		final AvailObject parts)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRemoveMessageParts(
			this,
			message,
			parts);
	}

	public void removeRestrictions ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRemoveRestrictions(this);
	}

	public void removeRestrictions (
		final AvailObject obsoleteRestrictions)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRemoveRestrictions(this, obsoleteRestrictions);
	}

	public AvailObject requiresBlock ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRequiresBlock(this);
	}

	public void requiresBlock (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRequiresBlock(this, value);
	}

	public void resolvedForwardWithName (
		final AvailObject forwardImplementation,
		final AvailObject methodName)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectResolvedForwardWithName(
			this,
			forwardImplementation,
			methodName);
	}

	public AvailObject restrictions ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRestrictions(this);
	}

	public void restrictions (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRestrictions(this, value);
	}

	public AvailObject returnsBlock ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectReturnsBlock(this);
	}

	public void returnsBlock (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectReturnsBlock(this, value);
	}

	public AvailObject returnType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectReturnType(this);
	}

	public void returnType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectReturnType(this, value);
	}

	public AvailObject rootBin ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectRootBin(this);
	}

	public void rootBin (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectRootBin(this, value);
	}

	public void scanSubobjects (
		final AvailSubobjectVisitor visitor)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectScanSubobjects(this, visitor);
	}

	public AvailObject secondTupleType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSecondTupleType(this);
	}

	public void secondTupleType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectSecondTupleType(this, value);
	}

	public AvailObject setIntersectionCanDestroy (
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSetIntersectionCanDestroy(
			this,
			otherSet,
			canDestroy);
	}

	public AvailObject setMinusCanDestroy (
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSetMinusCanDestroy(
			this,
			otherSet,
			canDestroy);
	}

	public int setSize ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSetSize(this);
	}

	public void setSize (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectSetSize(this, value);
	}

	public void setSubtupleForZoneTo (
		final int zoneIndex,
		final AvailObject newTuple)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectSetSubtupleForZoneTo(
			this,
			zoneIndex,
			newTuple);
	}

	public AvailObject setUnionCanDestroy (
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSetUnionCanDestroy(
			this,
			otherSet,
			canDestroy);
	}

	public void setValue (
		final AvailObject newValue)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectSetValue(this, newValue);
	}

	public AvailObject setWithElementCanDestroy (
		final AvailObject newElementObject,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSetWithElementCanDestroy(
			this,
			newElementObject,
			canDestroy);
	}

	public AvailObject setWithoutElementCanDestroy (
		final AvailObject elementObjectToExclude,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSetWithoutElementCanDestroy(
			this,
			elementObjectToExclude,
			canDestroy);
	}

	public AvailObject signature ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSignature(this);
	}

	public void signature (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectSignature(this, value);
	}

	public void size (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectSize(this, value);
	}

	public int sizeOfZone (
		final int zone)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSizeOfZone(this, zone);
	}

	public AvailObject sizeRange ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSizeRange(this);
	}

	public void sizeRange (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectSizeRange(this, value);
	}

	public AvailObject stackAt (
		final int slotIndex)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectStackAt(this, slotIndex);
	}

	public void stackAtPut (
		final int slotIndex,
		final AvailObject anObject)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectStackAtPut(
			this,
			slotIndex,
			anObject);
	}

	public int stackp ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectStackp(this);
	}

	public void stackp (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectStackp(this, value);
	}

	public int startingChunkIndex ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectStartingChunkIndex(this);
	}

	public void startingChunkIndex (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectStartingChunkIndex(this, value);
	}

	public int startOfZone (
		final int zone)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectStartOfZone(this, zone);
	}

	public int startSubtupleIndexInZone (
		final int zone)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectStartSubtupleIndexInZone(this, zone);
	}

	public void step ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectStep(this);
	}

	public AvailObject subtractFromInfinityCanDestroy (
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSubtractFromInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	public AvailObject subtractFromIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSubtractFromIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	public AvailObject subtupleForZone (
		final int zone)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectSubtupleForZone(this, zone);
	}

	public AvailObject target ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTarget(this);
	}

	public void target (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectTarget(this, value);
	}

	public AvailObject testingTree ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTestingTree(this);
	}

	public AvailObject timesCanDestroy (
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTimesCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	public int translateToZone (
		final int tupleIndex,
		final int zoneIndex)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTranslateToZone(
			this,
			tupleIndex,
			zoneIndex);
	}

	public AvailObject traversed ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTraversed(this);
	}

	public void trimExcessLongs ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectTrimExcessLongs(this);
	}

	public AvailObject trueNamesForStringName (
		final AvailObject stringName)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTrueNamesForStringName(this, stringName);
	}

	public AvailObject truncateTo (
		final int newTupleSize)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTruncateTo(this, newTupleSize);
	}

	public AvailObject tuple ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTuple(this);
	}

	public void tuple (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectTuple(this, value);
	}

	public AvailObject tupleAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTupleAt(this, index);
	}

	public void tupleAtPut (
		final int index,
		final AvailObject aNybbleObject)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectTupleAtPut(
			this,
			index,
			aNybbleObject);
	}

	public AvailObject tupleAtPuttingCanDestroy (
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTupleAtPuttingCanDestroy(
			this,
			index,
			newValueObject,
			canDestroy);
	}

	public int tupleIntAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTupleIntAt(this, index);
	}

	public int tupleSize ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTupleSize(this);
	}

	public AvailObject tupleType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTupleType(this);
	}

	public void tupleType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectTupleType(this, value);
	}

	public AvailObject type ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectType(this);
	}

	public void type (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectType(this, value);
	}

	public AvailObject typeAtIndex (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeAtIndex(this, index);
	}

	public boolean typeEquals (
		final AvailObject aType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeEquals(this, aType);
	}

	public AvailObject typeIntersection (
		final AvailObject another)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersection(this, another);
	}

	public AvailObject typeIntersectionOfClosureType (
		final AvailObject aClosureType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfClosureType(this, aClosureType);
	}

	public AvailObject typeIntersectionOfClosureTypeCanDestroy (
		final AvailObject aClosureType,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfClosureTypeCanDestroy(
			this,
			aClosureType,
			canDestroy);
	}

	public AvailObject typeIntersectionOfContainerType (
		final AvailObject aContainerType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfContainerType(this, aContainerType);
	}

	public AvailObject typeIntersectionOfContinuationType (
		final AvailObject aContinuationType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfContinuationType(this, aContinuationType);
	}

	public AvailObject typeIntersectionOfCyclicType (
		final AvailObject aCyclicType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfCyclicType(this, aCyclicType);
	}

	public AvailObject typeIntersectionOfGeneralizedClosureType (
		final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfGeneralizedClosureType(this, aGeneralizedClosureType);
	}

	public AvailObject typeIntersectionOfGeneralizedClosureTypeCanDestroy (
		final AvailObject aGeneralizedClosureType,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfGeneralizedClosureTypeCanDestroy(
			this,
			aGeneralizedClosureType,
			canDestroy);
	}

	public AvailObject typeIntersectionOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfIntegerRangeType(this, anIntegerRangeType);
	}

	public AvailObject typeIntersectionOfListType (
		final AvailObject aListType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfListType(this, aListType);
	}

	public AvailObject typeIntersectionOfMapType (
		final AvailObject aMapType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfMapType(this, aMapType);
	}

	public AvailObject typeIntersectionOfMeta (
		final AvailObject someMeta)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfMeta(this, someMeta);
	}

	public AvailObject typeIntersectionOfObjectMeta (
		final AvailObject anObjectMeta)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfObjectMeta(this, anObjectMeta);
	}

	public AvailObject typeIntersectionOfObjectMetaMeta (
		final AvailObject anObjectMetaMeta)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfObjectMetaMeta(this, anObjectMetaMeta);
	}

	public AvailObject typeIntersectionOfObjectType (
		final AvailObject anObjectType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfObjectType(this, anObjectType);
	}

	public AvailObject typeIntersectionOfSetType (
		final AvailObject aSetType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfSetType(this, aSetType);
	}

	public AvailObject typeIntersectionOfTupleType (
		final AvailObject aTupleType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeIntersectionOfTupleType(this, aTupleType);
	}

	public AvailObject typeTuple ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeTuple(this);
	}

	public void typeTuple (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectTypeTuple(this, value);
	}

	public AvailObject typeUnion (
		final AvailObject another)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnion(this, another);
	}

	public AvailObject typeUnionOfClosureType (
		final AvailObject aClosureType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfClosureType(this, aClosureType);
	}

	public AvailObject typeUnionOfClosureTypeCanDestroy (
		final AvailObject aClosureType,
		final boolean canDestroy)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfClosureTypeCanDestroy(
			this,
			aClosureType,
			canDestroy);
	}

	public AvailObject typeUnionOfContainerType (
		final AvailObject aContainerType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfContainerType(this, aContainerType);
	}

	public AvailObject typeUnionOfContinuationType (
		final AvailObject aContinuationType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfContinuationType(this, aContinuationType);
	}

	public AvailObject typeUnionOfCyclicType (
		final AvailObject aCyclicType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfCyclicType(this, aCyclicType);
	}

	public AvailObject typeUnionOfGeneralizedClosureType (
		final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfGeneralizedClosureType(this, aGeneralizedClosureType);
	}

	public AvailObject typeUnionOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfIntegerRangeType(this, anIntegerRangeType);
	}

	public AvailObject typeUnionOfListType (
		final AvailObject aListType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfListType(this, aListType);
	}

	public AvailObject typeUnionOfMapType (
		final AvailObject aMapType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfMapType(this, aMapType);
	}

	public AvailObject typeUnionOfObjectMeta (
		final AvailObject anObjectMeta)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfObjectMeta(this, anObjectMeta);
	}

	public AvailObject typeUnionOfObjectMetaMeta (
		final AvailObject anObjectMetaMeta)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfObjectMetaMeta(this, anObjectMetaMeta);
	}

	public AvailObject typeUnionOfObjectType (
		final AvailObject anObjectType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfObjectType(this, anObjectType);
	}

	public AvailObject typeUnionOfSetType (
		final AvailObject aSetType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfSetType(this, aSetType);
	}

	public AvailObject typeUnionOfTupleType (
		final AvailObject aTupleType)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectTypeUnionOfTupleType(this, aTupleType);
	}

	public AvailObject unclassified ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectUnclassified(this);
	}

	public void unclassified (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectUnclassified(this, value);
	}

	public AvailObject unionOfTypesAtThrough (
		final int startIndex,
		final int endIndex)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectUnionOfTypesAtThrough(
			this,
			startIndex,
			endIndex);
	}

	public int untranslatedDataAt (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectUntranslatedDataAt(this, index);
	}

	public void untranslatedDataAtPut (
		final int index,
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectUntranslatedDataAtPut(
			this,
			index,
			value);
	}

	public AvailObject upperBound ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectUpperBound(this);
	}

	public void upperBound (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectUpperBound(this, value);
	}

	public boolean upperInclusive ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectUpperInclusive(this);
	}

	public AvailObject validateArgumentTypesInterpreterIfFail (
		final List<AvailObject> argTypes,
		final AvailInterpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectValidateArgumentTypesInterpreterIfFail(
			this,
			argTypes,
			anAvailInterpreter,
			failBlock);
	}

	public int validity ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectValidity(this);
	}

	public void validity (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectValidity(this, value);
	}

	public AvailObject value ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectValue(this);
	}

	public void value (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectValue(this, value);
	}

	public AvailObject valueAtIndex (
		final int index)
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectValueAtIndex(this, index);
	}

	public void valueAtIndexPut (
		final int index,
		final AvailObject valueObject)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectValueAtIndexPut(
			this,
			index,
			valueObject);
	}

	public AvailObject valuesAsTuple ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectValuesAsTuple(this);
	}

	public AvailObject valueType ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectValueType(this);
	}

	public void valueType (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectValueType(this, value);
	}

	public AvailObject variableBindings ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectVariableBindings(this);
	}

	public void variableBindings (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectVariableBindings(this, value);
	}

	public AvailObject vectors ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectVectors(this);
	}

	public void vectors (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectVectors(this, value);
	}

	public void verify ()
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectVerify(this);
	}

	public AvailObject visibleNames ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectVisibleNames(this);
	}

	public void visibleNames (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectVisibleNames(this, value);
	}

	public int whichOne ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectWhichOne(this);
	}

	public void whichOne (
		final int value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectWhichOne(this, value);
	}

	public AvailObject wordcodes ()
	{
		//  GENERATED for descriptor dispatch

		return descriptor().ObjectWordcodes(this);
	}

	public void wordcodes (
		final AvailObject value)
	{
		//  GENERATED for descriptor dispatch

		descriptor().ObjectWordcodes(this, value);
	}

	public int zoneForIndex (
		final int index)
	{
		//  GENERATED for descriptor dispatch

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
		if (! descriptor().isMutable())
		{
			return;
		}
		if (! CanDestroyObjects)
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

	public void hash (
		final int value)
	{
		//  GENERATED for descriptor dispatch

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
		++ NumLockedObjects;
	};
	public static void unlock (AvailObject obj)
	{
		-- NumLockedObjects;
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
