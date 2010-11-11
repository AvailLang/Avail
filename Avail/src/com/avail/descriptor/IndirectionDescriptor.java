/**
 * descriptor/IndirectionDescriptor.java
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

import com.avail.annotations.NotNull;
import com.avail.compiler.Continuation1;
import com.avail.compiler.Generator;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.IndirectionDescriptor;
import com.avail.interpreter.AvailInterpreter;
import com.avail.visitor.AvailSubobjectVisitor;
import java.util.ArrayList;
import java.util.List;

@ObjectSlots("target")
public class IndirectionDescriptor extends Descriptor
{


	// GENERATED accessors

	void ObjectTarget (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	AvailObject ObjectTarget (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}



	// GENERATED reflex methods

	boolean ObjectAcceptsArgTypesFromClosureType (
			final AvailObject object, 
			final AvailObject closureType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAcceptsArgTypesFromClosureType(destination, closureType);
	}

	boolean ObjectAcceptsArgumentsFromContinuationStackp (
			final AvailObject object, 
			final AvailObject continuation, 
			final int stackp)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAcceptsArgumentsFromContinuationStackp(
			destination,
			continuation,
			stackp);
	}

	boolean ObjectAcceptsArgumentTypesFromContinuationStackp (
			final AvailObject object, 
			final AvailObject continuation, 
			final int stackp)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAcceptsArgumentTypesFromContinuationStackp(
			destination,
			continuation,
			stackp);
	}

	boolean ObjectAcceptsArrayOfArgTypes (
			final AvailObject object, 
			final List<AvailObject> argTypes)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAcceptsArrayOfArgTypes(destination, argTypes);
	}

	boolean ObjectAcceptsArrayOfArgValues (
			final AvailObject object, 
			final List<AvailObject> argValues)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAcceptsArrayOfArgValues(destination, argValues);
	}

	boolean ObjectAcceptsTupleOfArgTypes (
			final AvailObject object, 
			final AvailObject argTypes)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAcceptsTupleOfArgTypes(destination, argTypes);
	}

	boolean ObjectAcceptsTupleOfArguments (
			final AvailObject object, 
			final AvailObject arguments)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAcceptsTupleOfArguments(destination, arguments);
	}

	void ObjectAddDependentChunkId (
			final AvailObject object, 
			final int aChunkIndex)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectAddDependentChunkId(destination, aChunkIndex);
	}

	void ObjectAddImplementation (
			final AvailObject object, 
			final AvailObject implementation)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectAddImplementation(destination, implementation);
	}

	void ObjectAddRestrictions (
			final AvailObject object, 
			final AvailObject restrictions)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectAddRestrictions(destination, restrictions);
	}

	AvailObject ObjectAddToInfinityCanDestroy (
			final AvailObject object, 
			final AvailObject anInfinity, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAddToInfinityCanDestroy(
			destination,
			anInfinity,
			canDestroy);
	}

	AvailObject ObjectAddToIntegerCanDestroy (
			final AvailObject object, 
			final AvailObject anInteger, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAddToIntegerCanDestroy(
			destination,
			anInteger,
			canDestroy);
	}

	void ObjectArgsLocalsStackOutersPrimitive (
			final AvailObject object, 
			final int args, 
			final int locals, 
			final int stack, 
			final int outers, 
			final int primitive)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectArgsLocalsStackOutersPrimitive(
			destination,
			args,
			locals,
			stack,
			outers,
			primitive);
	}

	AvailObject ObjectArgTypeAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectArgTypeAt(destination, index);
	}

	void ObjectArgTypeAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectArgTypeAtPut(
			destination,
			index,
			value);
	}

	void ObjectAtAddMessageRestrictions (
			final AvailObject object, 
			final AvailObject methodName, 
			final AvailObject illegalArgMsgs)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectAtAddMessageRestrictions(
			destination,
			methodName,
			illegalArgMsgs);
	}

	void ObjectAtAddMethodImplementation (
			final AvailObject object, 
			final AvailObject methodName, 
			final AvailObject implementation)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectAtAddMethodImplementation(
			destination,
			methodName,
			implementation);
	}

	void ObjectAtMessageAddBundle (
			final AvailObject object, 
			final AvailObject message, 
			final AvailObject bundle)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectAtMessageAddBundle(
			destination,
			message,
			bundle);
	}

	void ObjectAtNameAdd (
			final AvailObject object, 
			final AvailObject stringName, 
			final AvailObject trueName)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectAtNameAdd(
			destination,
			stringName,
			trueName);
	}

	void ObjectAtNewNamePut (
			final AvailObject object, 
			final AvailObject stringName, 
			final AvailObject trueName)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectAtNewNamePut(
			destination,
			stringName,
			trueName);
	}

	void ObjectAtPrivateNameAdd (
			final AvailObject object, 
			final AvailObject stringName, 
			final AvailObject trueName)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectAtPrivateNameAdd(
			destination,
			stringName,
			trueName);
	}

	AvailObject ObjectBinAddingElementHashLevelCanDestroy (
			final AvailObject object, 
			final AvailObject elementObject, 
			final int elementObjectHash, 
			final byte myLevel, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBinAddingElementHashLevelCanDestroy(
			destination,
			elementObject,
			elementObjectHash,
			myLevel,
			canDestroy);
	}

	AvailObject ObjectBinElementAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBinElementAt(destination, index);
	}

	void ObjectBinElementAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectBinElementAtPut(
			destination,
			index,
			value);
	}

	boolean ObjectBinHasElementHash (
			final AvailObject object, 
			final AvailObject elementObject, 
			final int elementObjectHash)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBinHasElementHash(
			destination,
			elementObject,
			elementObjectHash);
	}

	void ObjectBinHash (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectBinHash(destination, value);
	}

	AvailObject ObjectBinRemoveElementHashCanDestroy (
			final AvailObject object, 
			final AvailObject elementObject, 
			final int elementObjectHash, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBinRemoveElementHashCanDestroy(
			destination,
			elementObject,
			elementObjectHash,
			canDestroy);
	}

	void ObjectBinSize (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectBinSize(destination, value);
	}

	void ObjectBinUnionType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectBinUnionType(destination, value);
	}

	void ObjectBitVector (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectBitVector(destination, value);
	}

	void ObjectBodyBlock (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectBodyBlock(destination, value);
	}

	void ObjectBodyBlockRequiresBlockReturnsBlock (
			final AvailObject object, 
			final AvailObject bb, 
			final AvailObject rqb, 
			final AvailObject rtb)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectBodyBlockRequiresBlockReturnsBlock(
			destination,
			bb,
			rqb,
			rtb);
	}

	void ObjectBodySignature (
			final AvailObject object, 
			final AvailObject signature)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectBodySignature(destination, signature);
	}

	void ObjectBodySignatureRequiresBlockReturnsBlock (
			final AvailObject object, 
			final AvailObject bs, 
			final AvailObject rqb, 
			final AvailObject rtb)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectBodySignatureRequiresBlockReturnsBlock(
			destination,
			bs,
			rqb,
			rtb);
	}

	void ObjectBreakpointBlock (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectBreakpointBlock(destination, value);
	}

	void ObjectBuildFilteredBundleTreeFrom (
			final AvailObject object, 
			final AvailObject bundleTree)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectBuildFilteredBundleTreeFrom(destination, bundleTree);
	}

	AvailObject ObjectBundleAtMessageParts (
			final AvailObject object, 
			final AvailObject message, 
			final AvailObject parts)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBundleAtMessageParts(
			destination,
			message,
			parts);
	}

	void ObjectCaller (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectCaller(destination, value);
	}

	void ObjectClosure (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectClosure(destination, value);
	}

	void ObjectClosureType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectClosureType(destination, value);
	}

	void ObjectCode (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectCode(destination, value);
	}

	void ObjectCodePoint (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectCodePoint(destination, value);
	}

	boolean ObjectCompareFromToWithStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject anotherObject, 
			final int startIndex2)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCompareFromToWithStartingAt(
			destination,
			startIndex1,
			endIndex1,
			anotherObject,
			startIndex2);
	}

	boolean ObjectCompareFromToWithAnyTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aTuple, 
			final int startIndex2)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCompareFromToWithAnyTupleStartingAt(
			destination,
			startIndex1,
			endIndex1,
			aTuple,
			startIndex2);
	}

	boolean ObjectCompareFromToWithByteStringStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aByteString, 
			final int startIndex2)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCompareFromToWithByteStringStartingAt(
			destination,
			startIndex1,
			endIndex1,
			aByteString,
			startIndex2);
	}

	boolean ObjectCompareFromToWithByteTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aByteTuple, 
			final int startIndex2)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCompareFromToWithByteTupleStartingAt(
			destination,
			startIndex1,
			endIndex1,
			aByteTuple,
			startIndex2);
	}

	boolean ObjectCompareFromToWithNybbleTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aNybbleTuple, 
			final int startIndex2)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCompareFromToWithNybbleTupleStartingAt(
			destination,
			startIndex1,
			endIndex1,
			aNybbleTuple,
			startIndex2);
	}

	boolean ObjectCompareFromToWithObjectTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject anObjectTuple, 
			final int startIndex2)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCompareFromToWithObjectTupleStartingAt(
			destination,
			startIndex1,
			endIndex1,
			anObjectTuple,
			startIndex2);
	}

	boolean ObjectCompareFromToWithTwoByteStringStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aTwoByteString, 
			final int startIndex2)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCompareFromToWithTwoByteStringStartingAt(
			destination,
			startIndex1,
			endIndex1,
			aTwoByteString,
			startIndex2);
	}

	void ObjectComplete (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectComplete(destination, value);
	}

	int ObjectComputeHashFromTo (
			final AvailObject object, 
			final int start, 
			final int end)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectComputeHashFromTo(
			destination,
			start,
			end);
	}

	AvailObject ObjectComputeReturnTypeFromArgumentTypesInterpreter (
			final AvailObject object, 
			final List<AvailObject> argTypes, 
			final AvailInterpreter anAvailInterpreter)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectComputeReturnTypeFromArgumentTypesInterpreter(
			destination,
			argTypes,
			anAvailInterpreter);
	}

	AvailObject ObjectConcatenateTuplesCanDestroy (
			final AvailObject object, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectConcatenateTuplesCanDestroy(destination, canDestroy);
	}

	void ObjectConstantBindings (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectConstantBindings(destination, value);
	}

	boolean ObjectContainsBlock (
			final AvailObject object, 
			final AvailObject aClosure)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectContainsBlock(destination, aClosure);
	}

	void ObjectContentType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectContentType(destination, value);
	}

	void ObjectContingentImpSets (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectContingentImpSets(destination, value);
	}

	void ObjectContinuation (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectContinuation(destination, value);
	}

	void ObjectCopyToRestrictedTo (
			final AvailObject object, 
			final AvailObject filteredBundleTree, 
			final AvailObject visibleNames)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectCopyToRestrictedTo(
			destination,
			filteredBundleTree,
			visibleNames);
	}

	AvailObject ObjectCopyTupleFromToCanDestroy (
			final AvailObject object, 
			final int start, 
			final int end, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCopyTupleFromToCanDestroy(
			destination,
			start,
			end,
			canDestroy);
	}

	boolean ObjectCouldEverBeInvokedWith (
			final AvailObject object, 
			final ArrayList<AvailObject> argTypes)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCouldEverBeInvokedWith(destination, argTypes);
	}

	AvailObject ObjectCreateTestingTreeWithPositiveMatchesRemainingPossibilities (
			final AvailObject object, 
			final AvailObject positiveTuple, 
			final AvailObject possibilities)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCreateTestingTreeWithPositiveMatchesRemainingPossibilities(
			destination,
			positiveTuple,
			possibilities);
	}

	AvailObject ObjectDataAtIndex (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectDataAtIndex(destination, index);
	}

	void ObjectDataAtIndexPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectDataAtIndexPut(
			destination,
			index,
			value);
	}

	void ObjectDefaultType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectDefaultType(destination, value);
	}

	void ObjectDependentChunks (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectDependentChunks(destination, value);
	}

	void ObjectDepth (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectDepth(destination, value);
	}

	AvailObject ObjectDivideCanDestroy (
			final AvailObject object, 
			final AvailObject aNumber, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectDivideCanDestroy(
			destination,
			aNumber,
			canDestroy);
	}

	AvailObject ObjectDivideIntoInfinityCanDestroy (
			final AvailObject object, 
			final AvailObject anInfinity, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectDivideIntoInfinityCanDestroy(
			destination,
			anInfinity,
			canDestroy);
	}

	AvailObject ObjectDivideIntoIntegerCanDestroy (
			final AvailObject object, 
			final AvailObject anInteger, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectDivideIntoIntegerCanDestroy(
			destination,
			anInteger,
			canDestroy);
	}

	AvailObject ObjectElementAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectElementAt(destination, index);
	}

	void ObjectElementAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectElementAtPut(
			destination,
			index,
			value);
	}

	int ObjectEndOfZone (
			final AvailObject object, 
			final int zone)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEndOfZone(destination, zone);
	}

	int ObjectEndSubtupleIndexInZone (
			final AvailObject object, 
			final int zone)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEndSubtupleIndexInZone(destination, zone);
	}

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEquals(destination, another);
	}

	boolean ObjectEqualsAnyTuple (
			final AvailObject object, 
			final AvailObject anotherTuple)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsAnyTuple(destination, anotherTuple);
	}

	boolean ObjectEqualsByteString (
			final AvailObject object, 
			final AvailObject aByteString)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsByteString(destination, aByteString);
	}

	boolean ObjectEqualsByteTuple (
			final AvailObject object, 
			final AvailObject aByteTuple)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsByteTuple(destination, aByteTuple);
	}

	boolean ObjectEqualsCharacterWithCodePoint (
			final AvailObject object, 
			final int otherCodePoint)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsCharacterWithCodePoint(destination, otherCodePoint);
	}

	boolean ObjectEqualsClosure (
			final AvailObject object, 
			final AvailObject aClosure)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsClosure(destination, aClosure);
	}

	boolean ObjectEqualsClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsClosureType(destination, aClosureType);
	}

	boolean ObjectEqualsCompiledCode (
			final AvailObject object, 
			final AvailObject aCompiledCode)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsCompiledCode(destination, aCompiledCode);
	}

	boolean ObjectEqualsContainer (
			final AvailObject object, 
			final AvailObject aContainer)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsContainer(destination, aContainer);
	}

	boolean ObjectEqualsContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsContainerType(destination, aContainerType);
	}

	boolean ObjectEqualsContinuation (
			final AvailObject object, 
			final AvailObject aContinuation)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsContinuation(destination, aContinuation);
	}

	boolean ObjectEqualsContinuationType (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsContinuationType(destination, aType);
	}

	boolean ObjectEqualsDouble (
			final AvailObject object, 
			final AvailObject aDoubleObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsDouble(destination, aDoubleObject);
	}

	boolean ObjectEqualsFloat (
			final AvailObject object, 
			final AvailObject aFloatObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsFloat(destination, aFloatObject);
	}

	boolean ObjectEqualsGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsGeneralizedClosureType(destination, aType);
	}

	boolean ObjectEqualsInfinity (
			final AvailObject object, 
			final AvailObject anInfinity)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsInfinity(destination, anInfinity);
	}

	boolean ObjectEqualsInteger (
			final AvailObject object, 
			final AvailObject anAvailInteger)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsInteger(destination, anAvailInteger);
	}

	boolean ObjectEqualsIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsIntegerRangeType(destination, anIntegerRangeType);
	}

	boolean ObjectEqualsList (
			final AvailObject object, 
			final AvailObject aList)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsList(destination, aList);
	}

	boolean ObjectEqualsListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsListType(destination, aListType);
	}

	boolean ObjectEqualsMap (
			final AvailObject object, 
			final AvailObject aMap)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsMap(destination, aMap);
	}

	boolean ObjectEqualsMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsMapType(destination, aMapType);
	}

	boolean ObjectEqualsNybbleTuple (
			final AvailObject object, 
			final AvailObject aNybbleTuple)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsNybbleTuple(destination, aNybbleTuple);
	}

	boolean ObjectEqualsObject (
			final AvailObject object, 
			final AvailObject anObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsObject(destination, anObject);
	}

	boolean ObjectEqualsObjectTuple (
			final AvailObject object, 
			final AvailObject anObjectTuple)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsObjectTuple(destination, anObjectTuple);
	}

	boolean ObjectEqualsPrimitiveType (
			final AvailObject object, 
			final AvailObject aPrimitiveType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsPrimitiveType(destination, aPrimitiveType);
	}

	boolean ObjectEqualsSet (
			final AvailObject object, 
			final AvailObject aSet)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsSet(destination, aSet);
	}

	boolean ObjectEqualsSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsSetType(destination, aSetType);
	}

	boolean ObjectEqualsTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsTupleType(destination, aTupleType);
	}

	boolean ObjectEqualsTwoByteString (
			final AvailObject object, 
			final AvailObject aTwoByteString)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsTwoByteString(destination, aTwoByteString);
	}

	void ObjectExecutionMode (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectExecutionMode(destination, value);
	}

	void ObjectExecutionState (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectExecutionState(destination, value);
	}

	byte ObjectExtractNybbleFromTupleAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectExtractNybbleFromTupleAt(destination, index);
	}

	void ObjectFieldMap (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectFieldMap(destination, value);
	}

	void ObjectFieldTypeMap (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectFieldTypeMap(destination, value);
	}

	List<AvailObject> ObjectFilterByTypes (
			final AvailObject object, 
			final List<AvailObject> argTypes)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectFilterByTypes(destination, argTypes);
	}

	void ObjectFilteredBundleTree (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectFilteredBundleTree(destination, value);
	}

	void ObjectFirstTupleType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectFirstTupleType(destination, value);
	}

	AvailObject ObjectForZoneSetSubtupleStartSubtupleIndexEndOfZone (
			final AvailObject object, 
			final int zone, 
			final AvailObject newSubtuple, 
			final int startSubtupleIndex, 
			final int endOfZone)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectForZoneSetSubtupleStartSubtupleIndexEndOfZone(
			destination,
			zone,
			newSubtuple,
			startSubtupleIndex,
			endOfZone);
	}

	boolean ObjectGreaterThanInteger (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectGreaterThanInteger(destination, another);
	}

	boolean ObjectGreaterThanSignedInfinity (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectGreaterThanSignedInfinity(destination, another);
	}

	boolean ObjectHasElement (
			final AvailObject object, 
			final AvailObject elementObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectHasElement(destination, elementObject);
	}

	void ObjectHash (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectHash(destination, value);
	}

	int ObjectHashFromTo (
			final AvailObject object, 
			final int startIndex, 
			final int endIndex)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectHashFromTo(
			destination,
			startIndex,
			endIndex);
	}

	void ObjectHashOrZero (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectHashOrZero(destination, value);
	}

	boolean ObjectHasKey (
			final AvailObject object, 
			final AvailObject keyObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectHasKey(destination, keyObject);
	}

	boolean ObjectHasObjectInstance (
			final AvailObject object, 
			final AvailObject potentialInstance)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectHasObjectInstance(destination, potentialInstance);
	}

	void ObjectHiLevelTwoChunkLowOffset (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectHiLevelTwoChunkLowOffset(destination, value);
	}

	void ObjectHiNumLocalsLowNumArgs (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectHiNumLocalsLowNumArgs(destination, value);
	}

	void ObjectHiPrimitiveLowNumArgsAndLocalsAndStack (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectHiPrimitiveLowNumArgsAndLocalsAndStack(destination, value);
	}

	void ObjectHiStartingChunkIndexLowNumOuters (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectHiStartingChunkIndexLowNumOuters(destination, value);
	}

	ArrayList<AvailObject> ObjectImplementationsAtOrBelow (
			final AvailObject object, 
			final ArrayList<AvailObject> argTypes)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectImplementationsAtOrBelow(destination, argTypes);
	}

	void ObjectImplementationsTuple (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectImplementationsTuple(destination, value);
	}

	AvailObject ObjectIncludeBundleAtMessageParts (
			final AvailObject object, 
			final AvailObject message, 
			final AvailObject parts)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIncludeBundleAtMessageParts(
			destination,
			message,
			parts);
	}

	boolean ObjectIncludes (
			final AvailObject object, 
			final AvailObject imp)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIncludes(destination, imp);
	}

	void ObjectInclusiveFlags (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectInclusiveFlags(destination, value);
	}

	void ObjectIncomplete (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectIncomplete(destination, value);
	}

	void ObjectIndex (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectIndex(destination, value);
	}

	void ObjectInnerType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectInnerType(destination, value);
	}

	void ObjectInstance (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectInstance(destination, value);
	}

	void ObjectInternalHash (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectInternalHash(destination, value);
	}

	void ObjectInterruptRequestFlag (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectInterruptRequestFlag(destination, value);
	}

	void ObjectInvocationCount (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectInvocationCount(destination, value);
	}

	boolean ObjectIsBetterRepresentationThan (
			final AvailObject object, 
			final AvailObject anotherObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsBetterRepresentationThan(destination, anotherObject);
	}

	boolean ObjectIsBetterRepresentationThanTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsBetterRepresentationThanTupleType(destination, aTupleType);
	}

	boolean ObjectIsBinSubsetOf (
			final AvailObject object, 
			final AvailObject potentialSuperset)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsBinSubsetOf(destination, potentialSuperset);
	}

	boolean ObjectIsInstanceOfSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsInstanceOfSubtypeOf(destination, aType);
	}

	void ObjectIsSaved (
			final AvailObject object, 
			final boolean aBoolean)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectIsSaved(destination, aBoolean);
	}

	boolean ObjectIsSubsetOf (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSubsetOf(destination, another);
	}

	boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSubtypeOf(destination, aType);
	}

	boolean ObjectIsSupertypeOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfClosureType(destination, aClosureType);
	}

	boolean ObjectIsSupertypeOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfContainerType(destination, aContainerType);
	}

	boolean ObjectIsSupertypeOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfContinuationType(destination, aContinuationType);
	}

	boolean ObjectIsSupertypeOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfCyclicType(destination, aCyclicType);
	}

	boolean ObjectIsSupertypeOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfGeneralizedClosureType(destination, aGeneralizedClosureType);
	}

	boolean ObjectIsSupertypeOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfIntegerRangeType(destination, anIntegerRangeType);
	}

	boolean ObjectIsSupertypeOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfListType(destination, aListType);
	}

	boolean ObjectIsSupertypeOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfMapType(destination, aMapType);
	}

	boolean ObjectIsSupertypeOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfObjectMeta(destination, anObjectMeta);
	}

	boolean ObjectIsSupertypeOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfObjectMetaMeta(destination, anObjectMetaMeta);
	}

	boolean ObjectIsSupertypeOfObjectType (
			final AvailObject object, 
			final AvailObject anObjectType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfObjectType(destination, anObjectType);
	}

	boolean ObjectIsSupertypeOfPrimitiveType (
			final AvailObject object, 
			final AvailObject aPrimitiveType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfPrimitiveType(destination, aPrimitiveType);
	}

	boolean ObjectIsSupertypeOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfSetType(destination, aSetType);
	}

	boolean ObjectIsSupertypeOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfTupleType(destination, aTupleType);
	}

	void ObjectIsValid (
			final AvailObject object, 
			final boolean aBoolean)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectIsValid(destination, aBoolean);
	}

	boolean ObjectIsValidForArgumentTypesInterpreter (
			final AvailObject object, 
			final List<AvailObject> argTypes, 
			final AvailInterpreter interpreter)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsValidForArgumentTypesInterpreter(
			destination,
			argTypes,
			interpreter);
	}

	AvailObject ObjectKeyAtIndex (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectKeyAtIndex(destination, index);
	}

	void ObjectKeyAtIndexPut (
			final AvailObject object, 
			final int index, 
			final AvailObject keyObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectKeyAtIndexPut(
			destination,
			index,
			keyObject);
	}

	void ObjectKeyType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectKeyType(destination, value);
	}

	boolean ObjectLessOrEqual (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLessOrEqual(destination, another);
	}

	boolean ObjectLessThan (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLessThan(destination, another);
	}

	void ObjectLevelTwoChunkIndexOffset (
			final AvailObject object, 
			final int index, 
			final int offset)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectLevelTwoChunkIndexOffset(
			destination,
			index,
			offset);
	}

	AvailObject ObjectLiteralAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLiteralAt(destination, index);
	}

	void ObjectLiteralAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectLiteralAtPut(
			destination,
			index,
			value);
	}

	AvailObject ObjectLocalOrArgOrStackAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLocalOrArgOrStackAt(destination, index);
	}

	void ObjectLocalOrArgOrStackAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectLocalOrArgOrStackAtPut(
			destination,
			index,
			value);
	}

	AvailObject ObjectLocalTypeAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLocalTypeAt(destination, index);
	}

	AvailObject ObjectLookupByTypesFromArray (
			final AvailObject object, 
			final List<AvailObject> argumentTypeArray)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLookupByTypesFromArray(destination, argumentTypeArray);
	}

	AvailObject ObjectLookupByTypesFromContinuationStackp (
			final AvailObject object, 
			final AvailObject continuation, 
			final int stackp)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLookupByTypesFromContinuationStackp(
			destination,
			continuation,
			stackp);
	}

	AvailObject ObjectLookupByTypesFromTuple (
			final AvailObject object, 
			final AvailObject argumentTypeTuple)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLookupByTypesFromTuple(destination, argumentTypeTuple);
	}

	AvailObject ObjectLookupByValuesFromArray (
			final AvailObject object, 
			final List<AvailObject> argumentArray)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLookupByValuesFromArray(destination, argumentArray);
	}

	AvailObject ObjectLookupByValuesFromContinuationStackp (
			final AvailObject object, 
			final AvailObject continuation, 
			final int stackp)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLookupByValuesFromContinuationStackp(
			destination,
			continuation,
			stackp);
	}

	AvailObject ObjectLookupByValuesFromTuple (
			final AvailObject object, 
			final AvailObject argumentTuple)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLookupByValuesFromTuple(destination, argumentTuple);
	}

	void ObjectLowerBound (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectLowerBound(destination, value);
	}

	void ObjectLowerInclusiveUpperInclusive (
			final AvailObject object, 
			final boolean lowInc, 
			final boolean highInc)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectLowerInclusiveUpperInclusive(
			destination,
			lowInc,
			highInc);
	}

	AvailObject ObjectMapAt (
			final AvailObject object, 
			final AvailObject keyObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMapAt(destination, keyObject);
	}

	AvailObject ObjectMapAtPuttingCanDestroy (
			final AvailObject object, 
			final AvailObject keyObject, 
			final AvailObject newValueObject, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMapAtPuttingCanDestroy(
			destination,
			keyObject,
			newValueObject,
			canDestroy);
	}

	void ObjectMapSize (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectMapSize(destination, value);
	}

	AvailObject ObjectMapWithoutKeyCanDestroy (
			final AvailObject object, 
			final AvailObject keyObject, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMapWithoutKeyCanDestroy(
			destination,
			keyObject,
			canDestroy);
	}

	void ObjectMessage (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectMessage(destination, value);
	}

	void ObjectMessageParts (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectMessageParts(destination, value);
	}

	void ObjectMethods (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectMethods(destination, value);
	}

	AvailObject ObjectMinusCanDestroy (
			final AvailObject object, 
			final AvailObject aNumber, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMinusCanDestroy(
			destination,
			aNumber,
			canDestroy);
	}

	AvailObject ObjectMultiplyByInfinityCanDestroy (
			final AvailObject object, 
			final AvailObject anInfinity, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMultiplyByInfinityCanDestroy(
			destination,
			anInfinity,
			canDestroy);
	}

	AvailObject ObjectMultiplyByIntegerCanDestroy (
			final AvailObject object, 
			final AvailObject anInteger, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMultiplyByIntegerCanDestroy(
			destination,
			anInteger,
			canDestroy);
	}

	void ObjectMyObjectMeta (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectMyObjectMeta(destination, value);
	}

	void ObjectMyObjectType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectMyObjectType(destination, value);
	}

	void ObjectMyRestrictions (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectMyRestrictions(destination, value);
	}

	void ObjectMyType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectMyType(destination, value);
	}

	void ObjectName (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectName(destination, value);
	}

	void ObjectNames (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectNames(destination, value);
	}

	boolean ObjectNameVisible (
			final AvailObject object, 
			final AvailObject trueName)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNameVisible(destination, trueName);
	}

	void ObjectNecessaryImplementationSetChanged (
			final AvailObject object, 
			final AvailObject anImplementationSet)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectNecessaryImplementationSetChanged(destination, anImplementationSet);
	}

	void ObjectNewNames (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectNewNames(destination, value);
	}

	void ObjectNext (
			final AvailObject object, 
			final AvailObject nextChunk)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectNext(destination, nextChunk);
	}

	void ObjectNextIndex (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectNextIndex(destination, value);
	}

	void ObjectNumBlanks (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectNumBlanks(destination, value);
	}

	void ObjectNumFloats (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectNumFloats(destination, value);
	}

	void ObjectNumIntegers (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectNumIntegers(destination, value);
	}

	void ObjectNumObjects (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectNumObjects(destination, value);
	}

	void ObjectNybbles (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectNybbles(destination, value);
	}

	boolean ObjectOptionallyNilOuterVar (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectOptionallyNilOuterVar(destination, index);
	}

	AvailObject ObjectOuterTypeAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectOuterTypeAt(destination, index);
	}

	void ObjectOuterTypesLocalTypes (
			final AvailObject object, 
			final AvailObject tupleOfOuterTypes, 
			final AvailObject tupleOfLocalContainerTypes)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectOuterTypesLocalTypes(
			destination,
			tupleOfOuterTypes,
			tupleOfLocalContainerTypes);
	}

	AvailObject ObjectOuterVarAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectOuterVarAt(destination, index);
	}

	void ObjectOuterVarAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectOuterVarAtPut(
			destination,
			index,
			value);
	}

	void ObjectPad (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectPad(destination, value);
	}

	void ObjectParent (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectParent(destination, value);
	}

	void ObjectPc (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectPc(destination, value);
	}

	AvailObject ObjectPlusCanDestroy (
			final AvailObject object, 
			final AvailObject aNumber, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPlusCanDestroy(
			destination,
			aNumber,
			canDestroy);
	}

	int ObjectPopulateTupleStartingAt (
			final AvailObject object, 
			final AvailObject mutableTuple, 
			final int startingIndex)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPopulateTupleStartingAt(
			destination,
			mutableTuple,
			startingIndex);
	}

	void ObjectPrevious (
			final AvailObject object, 
			final AvailObject previousChunk)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectPrevious(destination, previousChunk);
	}

	void ObjectPreviousIndex (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectPreviousIndex(destination, value);
	}

	void ObjectPriority (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectPriority(destination, value);
	}

	AvailObject ObjectPrivateAddElement (
			final AvailObject object, 
			final AvailObject element)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPrivateAddElement(destination, element);
	}

	AvailObject ObjectPrivateExcludeElement (
			final AvailObject object, 
			final AvailObject element)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPrivateExcludeElement(destination, element);
	}

	AvailObject ObjectPrivateExcludeElementKnownIndex (
			final AvailObject object, 
			final AvailObject element, 
			final int knownIndex)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPrivateExcludeElementKnownIndex(
			destination,
			element,
			knownIndex);
	}

	AvailObject ObjectPrivateExcludeKey (
			final AvailObject object, 
			final AvailObject keyObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPrivateExcludeKey(destination, keyObject);
	}

	AvailObject ObjectPrivateMapAtPut (
			final AvailObject object, 
			final AvailObject keyObject, 
			final AvailObject valueObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPrivateMapAtPut(
			destination,
			keyObject,
			valueObject);
	}

	void ObjectPrivateNames (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectPrivateNames(destination, value);
	}

	void ObjectPrivateTestingTree (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectPrivateTestingTree(destination, value);
	}

	void ObjectProcessGlobals (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectProcessGlobals(destination, value);
	}

	short ObjectRawByteAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRawByteAt(destination, index);
	}

	void ObjectRawByteAtPut (
			final AvailObject object, 
			final int index, 
			final short anInteger)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRawByteAtPut(
			destination,
			index,
			anInteger);
	}

	short ObjectRawByteForCharacterAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRawByteForCharacterAt(destination, index);
	}

	void ObjectRawByteForCharacterAtPut (
			final AvailObject object, 
			final int index, 
			final short anInteger)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRawByteForCharacterAtPut(
			destination,
			index,
			anInteger);
	}

	byte ObjectRawNybbleAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRawNybbleAt(destination, index);
	}

	void ObjectRawNybbleAtPut (
			final AvailObject object, 
			final int index, 
			final byte aNybble)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRawNybbleAtPut(
			destination,
			index,
			aNybble);
	}

	void ObjectRawQuad1 (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRawQuad1(destination, value);
	}

	void ObjectRawQuad2 (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRawQuad2(destination, value);
	}

	int ObjectRawQuadAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRawQuadAt(destination, index);
	}

	void ObjectRawQuadAtPut (
			final AvailObject object, 
			final int index, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRawQuadAtPut(
			destination,
			index,
			value);
	}

	short ObjectRawShortForCharacterAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRawShortForCharacterAt(destination, index);
	}

	void ObjectRawShortForCharacterAtPut (
			final AvailObject object, 
			final int index, 
			final short anInteger)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRawShortForCharacterAtPut(
			destination,
			index,
			anInteger);
	}

	int ObjectRawSignedIntegerAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRawSignedIntegerAt(destination, index);
	}

	void ObjectRawSignedIntegerAtPut (
			final AvailObject object, 
			final int index, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRawSignedIntegerAtPut(
			destination,
			index,
			value);
	}

	long ObjectRawUnsignedIntegerAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRawUnsignedIntegerAt(destination, index);
	}

	void ObjectRawUnsignedIntegerAtPut (
			final AvailObject object, 
			final int index, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRawUnsignedIntegerAtPut(
			destination,
			index,
			value);
	}

	void ObjectRemoveDependentChunkId (
			final AvailObject object, 
			final int aChunkIndex)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRemoveDependentChunkId(destination, aChunkIndex);
	}

	void ObjectRemoveFrom (
			final AvailObject object, 
			final AvailInterpreter anInterpreter)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRemoveFrom(destination, anInterpreter);
	}

	void ObjectRemoveImplementation (
			final AvailObject object, 
			final AvailObject implementation)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRemoveImplementation(destination, implementation);
	}

	boolean ObjectRemoveMessageParts (
			final AvailObject object, 
			final AvailObject message, 
			final AvailObject parts)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRemoveMessageParts(
			destination,
			message,
			parts);
	}

	void ObjectRemoveRestrictions (
			final AvailObject object, 
			final AvailObject obsoleteRestrictions)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRemoveRestrictions(destination, obsoleteRestrictions);
	}

	void ObjectRequiresBlock (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRequiresBlock(destination, value);
	}

	void ObjectResolvedForwardWithName (
			final AvailObject object, 
			final AvailObject forwardImplementation, 
			final AvailObject methodName)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectResolvedForwardWithName(
			destination,
			forwardImplementation,
			methodName);
	}

	void ObjectRestrictions (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRestrictions(destination, value);
	}

	void ObjectReturnsBlock (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectReturnsBlock(destination, value);
	}

	void ObjectReturnType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectReturnType(destination, value);
	}

	void ObjectRootBin (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRootBin(destination, value);
	}

	void ObjectSecondTupleType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectSecondTupleType(destination, value);
	}

	AvailObject ObjectSetIntersectionCanDestroy (
			final AvailObject object, 
			final AvailObject otherSet, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSetIntersectionCanDestroy(
			destination,
			otherSet,
			canDestroy);
	}

	AvailObject ObjectSetMinusCanDestroy (
			final AvailObject object, 
			final AvailObject otherSet, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSetMinusCanDestroy(
			destination,
			otherSet,
			canDestroy);
	}

	void ObjectSetSize (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectSetSize(destination, value);
	}

	void ObjectSetSubtupleForZoneTo (
			final AvailObject object, 
			final int zoneIndex, 
			final AvailObject newTuple)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectSetSubtupleForZoneTo(
			destination,
			zoneIndex,
			newTuple);
	}

	AvailObject ObjectSetUnionCanDestroy (
			final AvailObject object, 
			final AvailObject otherSet, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSetUnionCanDestroy(
			destination,
			otherSet,
			canDestroy);
	}

	void ObjectSetValue (
			final AvailObject object, 
			final AvailObject newValue)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectSetValue(destination, newValue);
	}

	AvailObject ObjectSetWithElementCanDestroy (
			final AvailObject object, 
			final AvailObject newElementObject, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSetWithElementCanDestroy(
			destination,
			newElementObject,
			canDestroy);
	}

	AvailObject ObjectSetWithoutElementCanDestroy (
			final AvailObject object, 
			final AvailObject elementObjectToExclude, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSetWithoutElementCanDestroy(
			destination,
			elementObjectToExclude,
			canDestroy);
	}

	void ObjectSignature (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectSignature(destination, value);
	}

	void ObjectSize (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectSize(destination, value);
	}

	int ObjectSizeOfZone (
			final AvailObject object, 
			final int zone)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSizeOfZone(destination, zone);
	}

	void ObjectSizeRange (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectSizeRange(destination, value);
	}

	AvailObject ObjectStackAt (
			final AvailObject object, 
			final int slotIndex)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectStackAt(destination, slotIndex);
	}

	void ObjectStackAtPut (
			final AvailObject object, 
			final int slotIndex, 
			final AvailObject anObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectStackAtPut(
			destination,
			slotIndex,
			anObject);
	}

	void ObjectStackp (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectStackp(destination, value);
	}

	void ObjectStartingChunkIndex (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectStartingChunkIndex(destination, value);
	}

	int ObjectStartOfZone (
			final AvailObject object, 
			final int zone)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectStartOfZone(destination, zone);
	}

	int ObjectStartSubtupleIndexInZone (
			final AvailObject object, 
			final int zone)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectStartSubtupleIndexInZone(destination, zone);
	}

	AvailObject ObjectSubtractFromInfinityCanDestroy (
			final AvailObject object, 
			final AvailObject anInfinity, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSubtractFromInfinityCanDestroy(
			destination,
			anInfinity,
			canDestroy);
	}

	AvailObject ObjectSubtractFromIntegerCanDestroy (
			final AvailObject object, 
			final AvailObject anInteger, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSubtractFromIntegerCanDestroy(
			destination,
			anInteger,
			canDestroy);
	}

	AvailObject ObjectSubtupleForZone (
			final AvailObject object, 
			final int zone)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSubtupleForZone(destination, zone);
	}

	AvailObject ObjectTimesCanDestroy (
			final AvailObject object, 
			final AvailObject aNumber, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTimesCanDestroy(
			destination,
			aNumber,
			canDestroy);
	}

	int ObjectTranslateToZone (
			final AvailObject object, 
			final int tupleIndex, 
			final int zoneIndex)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTranslateToZone(
			destination,
			tupleIndex,
			zoneIndex);
	}

	AvailObject ObjectTrueNamesForStringName (
			final AvailObject object, 
			final AvailObject stringName)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTrueNamesForStringName(destination, stringName);
	}

	AvailObject ObjectTruncateTo (
			final AvailObject object, 
			final int newTupleSize)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTruncateTo(destination, newTupleSize);
	}

	void ObjectTuple (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectTuple(destination, value);
	}

	AvailObject ObjectTupleAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTupleAt(destination, index);
	}

	void ObjectTupleAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject aNybbleObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectTupleAtPut(
			destination,
			index,
			aNybbleObject);
	}

	AvailObject ObjectTupleAtPuttingCanDestroy (
			final AvailObject object, 
			final int index, 
			final AvailObject newValueObject, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTupleAtPuttingCanDestroy(
			destination,
			index,
			newValueObject,
			canDestroy);
	}

	int ObjectTupleIntAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTupleIntAt(destination, index);
	}

	void ObjectTupleType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectTupleType(destination, value);
	}

	void ObjectType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectType(destination, value);
	}

	AvailObject ObjectTypeAtIndex (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeAtIndex(destination, index);
	}

	boolean ObjectTypeEquals (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeEquals(destination, aType);
	}

	AvailObject ObjectTypeIntersection (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersection(destination, another);
	}

	AvailObject ObjectTypeIntersectionOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfClosureType(destination, aClosureType);
	}

	AvailObject ObjectTypeIntersectionOfClosureTypeCanDestroy (
			final AvailObject object, 
			final AvailObject aClosureType, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfClosureTypeCanDestroy(
			destination,
			aClosureType,
			canDestroy);
	}

	AvailObject ObjectTypeIntersectionOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfContainerType(destination, aContainerType);
	}

	AvailObject ObjectTypeIntersectionOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfContinuationType(destination, aContinuationType);
	}

	AvailObject ObjectTypeIntersectionOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfCyclicType(destination, aCyclicType);
	}

	AvailObject ObjectTypeIntersectionOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfGeneralizedClosureType(destination, aGeneralizedClosureType);
	}

	AvailObject ObjectTypeIntersectionOfGeneralizedClosureTypeCanDestroy (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfGeneralizedClosureTypeCanDestroy(
			destination,
			aGeneralizedClosureType,
			canDestroy);
	}

	AvailObject ObjectTypeIntersectionOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfIntegerRangeType(destination, anIntegerRangeType);
	}

	AvailObject ObjectTypeIntersectionOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfListType(destination, aListType);
	}

	AvailObject ObjectTypeIntersectionOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfMapType(destination, aMapType);
	}

	AvailObject ObjectTypeIntersectionOfMeta (
			final AvailObject object, 
			final AvailObject someMeta)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfMeta(destination, someMeta);
	}

	AvailObject ObjectTypeIntersectionOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfObjectMeta(destination, anObjectMeta);
	}

	AvailObject ObjectTypeIntersectionOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfObjectMetaMeta(destination, anObjectMetaMeta);
	}

	AvailObject ObjectTypeIntersectionOfObjectType (
			final AvailObject object, 
			final AvailObject anObjectType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfObjectType(destination, anObjectType);
	}

	AvailObject ObjectTypeIntersectionOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfSetType(destination, aSetType);
	}

	AvailObject ObjectTypeIntersectionOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeIntersectionOfTupleType(destination, aTupleType);
	}

	void ObjectTypeTuple (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectTypeTuple(destination, value);
	}

	AvailObject ObjectTypeUnion (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnion(destination, another);
	}

	AvailObject ObjectTypeUnionOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfClosureType(destination, aClosureType);
	}

	AvailObject ObjectTypeUnionOfClosureTypeCanDestroy (
			final AvailObject object, 
			final AvailObject aClosureType, 
			final boolean canDestroy)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfClosureTypeCanDestroy(
			destination,
			aClosureType,
			canDestroy);
	}

	AvailObject ObjectTypeUnionOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfContainerType(destination, aContainerType);
	}

	AvailObject ObjectTypeUnionOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfContinuationType(destination, aContinuationType);
	}

	AvailObject ObjectTypeUnionOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfCyclicType(destination, aCyclicType);
	}

	AvailObject ObjectTypeUnionOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfGeneralizedClosureType(destination, aGeneralizedClosureType);
	}

	AvailObject ObjectTypeUnionOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfIntegerRangeType(destination, anIntegerRangeType);
	}

	AvailObject ObjectTypeUnionOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfListType(destination, aListType);
	}

	AvailObject ObjectTypeUnionOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfMapType(destination, aMapType);
	}

	AvailObject ObjectTypeUnionOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfObjectMeta(destination, anObjectMeta);
	}

	AvailObject ObjectTypeUnionOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfObjectMetaMeta(destination, anObjectMetaMeta);
	}

	AvailObject ObjectTypeUnionOfObjectType (
			final AvailObject object, 
			final AvailObject anObjectType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfObjectType(destination, anObjectType);
	}

	AvailObject ObjectTypeUnionOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfSetType(destination, aSetType);
	}

	AvailObject ObjectTypeUnionOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeUnionOfTupleType(destination, aTupleType);
	}

	void ObjectUnclassified (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectUnclassified(destination, value);
	}

	AvailObject ObjectUnionOfTypesAtThrough (
			final AvailObject object, 
			final int startIndex, 
			final int endIndex)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectUnionOfTypesAtThrough(
			destination,
			startIndex,
			endIndex);
	}

	int ObjectUntranslatedDataAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectUntranslatedDataAt(destination, index);
	}

	void ObjectUntranslatedDataAtPut (
			final AvailObject object, 
			final int index, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectUntranslatedDataAtPut(
			destination,
			index,
			value);
	}

	void ObjectUpperBound (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectUpperBound(destination, value);
	}

	AvailObject ObjectValidateArgumentTypesInterpreterIfFail (
			final AvailObject object, 
			final List<AvailObject> argTypes, 
			final AvailInterpreter anAvailInterpreter, 
			final Continuation1<Generator<String>> failBlock)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectValidateArgumentTypesInterpreterIfFail(
			destination,
			argTypes,
			anAvailInterpreter,
			failBlock);
	}

	void ObjectValidity (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectValidity(destination, value);
	}

	void ObjectValue (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectValue(destination, value);
	}

	AvailObject ObjectValueAtIndex (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectValueAtIndex(destination, index);
	}

	void ObjectValueAtIndexPut (
			final AvailObject object, 
			final int index, 
			final AvailObject valueObject)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectValueAtIndexPut(
			destination,
			index,
			valueObject);
	}

	void ObjectValueType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectValueType(destination, value);
	}

	void ObjectVariableBindings (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectVariableBindings(destination, value);
	}

	void ObjectVectors (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectVectors(destination, value);
	}

	void ObjectVisibleNames (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectVisibleNames(destination, value);
	}

	void ObjectWhichOne (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectWhichOne(destination, value);
	}

	void ObjectWordcodes (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectWordcodes(destination, value);
	}

	int ObjectZoneForIndex (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectZoneForIndex(destination, index);
	}

	String ObjectAsNativeString (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAsNativeString(destination);
	}

	AvailObject ObjectAsObject (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAsObject(destination);
	}

	AvailObject ObjectAsSet (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAsSet(destination);
	}

	AvailObject ObjectAsTuple (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectAsTuple(destination);
	}

	AvailObject ObjectBecomeExactType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBecomeExactType(destination);
	}

	void ObjectBecomeRealTupleType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectBecomeRealTupleType(destination);
	}

	int ObjectBinHash (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBinHash(destination);
	}

	int ObjectBinSize (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBinSize(destination);
	}

	AvailObject ObjectBinUnionType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBinUnionType(destination);
	}

	int ObjectBitsPerEntry (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBitsPerEntry(destination);
	}

	int ObjectBitVector (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBitVector(destination);
	}

	AvailObject ObjectBodyBlock (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBodyBlock(destination);
	}

	AvailObject ObjectBodySignature (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBodySignature(destination);
	}

	AvailObject ObjectBreakpointBlock (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectBreakpointBlock(destination);
	}

	AvailObject ObjectCaller (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCaller(destination);
	}

	boolean ObjectCanComputeHashOfType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCanComputeHashOfType(destination);
	}

	int ObjectCapacity (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCapacity(destination);
	}

	void ObjectCleanUpAfterCompile (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectCleanUpAfterCompile(destination);
	}

	void ObjectClearModule (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectClearModule(destination);
	}

	void ObjectClearValue (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectClearValue(destination);
	}

	AvailObject ObjectClosure (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectClosure(destination);
	}

	AvailObject ObjectClosureType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectClosureType(destination);
	}

	AvailObject ObjectCode (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCode(destination);
	}

	int ObjectCodePoint (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCodePoint(destination);
	}

	AvailObject ObjectComplete (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectComplete(destination);
	}

	AvailObject ObjectConstantBindings (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectConstantBindings(destination);
	}

	AvailObject ObjectContentType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectContentType(destination);
	}

	AvailObject ObjectContingentImpSets (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectContingentImpSets(destination);
	}

	AvailObject ObjectContinuation (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectContinuation(destination);
	}

	AvailObject ObjectCopyAsMutableContinuation (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCopyAsMutableContinuation(destination);
	}

	AvailObject ObjectCopyAsMutableObjectTuple (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCopyAsMutableObjectTuple(destination);
	}

	AvailObject ObjectCopyAsMutableSpliceTuple (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCopyAsMutableSpliceTuple(destination);
	}

	AvailObject ObjectCopyMutable (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectCopyMutable(destination);
	}

	AvailObject ObjectDefaultType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectDefaultType(destination);
	}

	AvailObject ObjectDependentChunks (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectDependentChunks(destination);
	}

	int ObjectDepth (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectDepth(destination);
	}

	void ObjectDisplayTestingTree (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectDisplayTestingTree(destination);
	}

	void ObjectEnsureMetacovariant (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectEnsureMetacovariant(destination);
	}

	AvailObject ObjectEnsureMutable (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEnsureMutable(destination);
	}

	boolean ObjectEqualsBlank (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsBlank(destination);
	}

	boolean ObjectEqualsFalse (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsFalse(destination);
	}

	boolean ObjectEqualsTrue (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsTrue(destination);
	}

	boolean ObjectEqualsVoid (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsVoid(destination);
	}

	boolean ObjectEqualsVoidOrBlank (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectEqualsVoidOrBlank(destination);
	}

	void ObjectEvictedByGarbageCollector (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectEvictedByGarbageCollector(destination);
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectExactType(destination);
	}

	int ObjectExecutionMode (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectExecutionMode(destination);
	}

	int ObjectExecutionState (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectExecutionState(destination);
	}

	AvailObject ObjectExpand (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectExpand(destination);
	}

	boolean ObjectExtractBoolean (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectExtractBoolean(destination);
	}

	short ObjectExtractByte (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectExtractByte(destination);
	}

	double ObjectExtractDouble (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectExtractDouble(destination);
	}

	float ObjectExtractFloat (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectExtractFloat(destination);
	}

	int ObjectExtractInt (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectExtractInt(destination);
	}

	byte ObjectExtractNybble (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectExtractNybble(destination);
	}

	AvailObject ObjectFieldMap (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectFieldMap(destination);
	}

	AvailObject ObjectFieldTypeMap (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectFieldTypeMap(destination);
	}

	AvailObject ObjectFilteredBundleTree (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectFilteredBundleTree(destination);
	}

	AvailObject ObjectFirstTupleType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectFirstTupleType(destination);
	}

	int ObjectGetInteger (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectGetInteger(destination);
	}

	AvailObject ObjectGetValue (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectGetValue(destination);
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectHash(destination);
	}

	int ObjectHashOfType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectHashOfType(destination);
	}

	int ObjectHashOrZero (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectHashOrZero(destination);
	}

	boolean ObjectHasRestrictions (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectHasRestrictions(destination);
	}

	int ObjectHiLevelTwoChunkLowOffset (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectHiLevelTwoChunkLowOffset(destination);
	}

	int ObjectHiNumLocalsLowNumArgs (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectHiNumLocalsLowNumArgs(destination);
	}

	int ObjectHiPrimitiveLowNumArgsAndLocalsAndStack (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectHiPrimitiveLowNumArgsAndLocalsAndStack(destination);
	}

	int ObjectHiStartingChunkIndexLowNumOuters (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectHiStartingChunkIndexLowNumOuters(destination);
	}

	AvailObject ObjectImplementationsTuple (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectImplementationsTuple(destination);
	}

	int ObjectInclusiveFlags (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectInclusiveFlags(destination);
	}

	AvailObject ObjectIncomplete (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIncomplete(destination);
	}

	int ObjectIndex (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIndex(destination);
	}

	AvailObject ObjectInnerType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectInnerType(destination);
	}

	AvailObject ObjectInstance (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectInstance(destination);
	}

	int ObjectInternalHash (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectInternalHash(destination);
	}

	int ObjectInterruptRequestFlag (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectInterruptRequestFlag(destination);
	}

	int ObjectInvocationCount (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectInvocationCount(destination);
	}

	boolean ObjectIsAbstract (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsAbstract(destination);
	}

	boolean ObjectIsBoolean (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsBoolean(destination);
	}

	boolean ObjectIsByte (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsByte(destination);
	}
	
	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	boolean ObjectIsByteTuple (final @NotNull AvailObject object)
	{
		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsByteTuple(destination);
	}

	boolean ObjectIsCharacter (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsCharacter(destination);
	}

	boolean ObjectIsClosure (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsClosure(destination);
	}

	boolean ObjectIsCyclicType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsCyclicType(destination);
	}

	boolean ObjectIsExtendedInteger (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsExtendedInteger(destination);
	}

	boolean ObjectIsFinite (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsFinite(destination);
	}

	boolean ObjectIsForward (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsForward(destination);
	}

	boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsHashAvailable(destination);
	}

	boolean ObjectIsImplementation (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsImplementation(destination);
	}

	boolean ObjectIsIntegerRangeType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsIntegerRangeType(destination);
	}

	boolean ObjectIsList (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsList(destination);
	}

	boolean ObjectIsListType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsListType(destination);
	}

	boolean ObjectIsMap (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsMap(destination);
	}

	boolean ObjectIsMapType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsMapType(destination);
	}

	boolean ObjectIsNybble (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsNybble(destination);
	}

	boolean ObjectIsPositive (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsPositive(destination);
	}

	boolean ObjectIsSaved (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSaved(destination);
	}

	boolean ObjectIsSet (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSet(destination);
	}

	boolean ObjectIsSetType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSetType(destination);
	}

	boolean ObjectIsSplice (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSplice(destination);
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	boolean ObjectIsString (final @NotNull AvailObject object)
	{
		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsString(destination);
	}

	boolean ObjectIsSupertypeOfTerminates (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfTerminates(destination);
	}

	boolean ObjectIsSupertypeOfVoid (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsSupertypeOfVoid(destination);
	}

	boolean ObjectIsTuple (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsTuple(destination);
	}

	boolean ObjectIsTupleType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsTupleType(destination);
	}

	boolean ObjectIsType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsType(destination);
	}

	boolean ObjectIsValid (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectIsValid(destination);
	}

	List<AvailObject> ObjectKeysAsArray (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectKeysAsArray(destination);
	}

	AvailObject ObjectKeysAsSet (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectKeysAsSet(destination);
	}

	AvailObject ObjectKeyType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectKeyType(destination);
	}

	int ObjectLevelTwoChunkIndex (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLevelTwoChunkIndex(destination);
	}

	int ObjectLevelTwoOffset (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLevelTwoOffset(destination);
	}

	AvailObject ObjectLowerBound (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLowerBound(destination);
	}

	boolean ObjectLowerInclusive (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectLowerInclusive(destination);
	}

	void ObjectMakeSubobjectsImmutable (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectMakeSubobjectsImmutable(destination);
	}

	int ObjectMapSize (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMapSize(destination);
	}

	short ObjectMaxStackDepth (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMaxStackDepth(destination);
	}

	AvailObject ObjectMessage (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMessage(destination);
	}

	AvailObject ObjectMessageParts (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMessageParts(destination);
	}

	AvailObject ObjectMethods (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMethods(destination);
	}

	void ObjectMoveToHead (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectMoveToHead(destination);
	}

	AvailObject ObjectMyObjectMeta (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMyObjectMeta(destination);
	}

	AvailObject ObjectMyObjectType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMyObjectType(destination);
	}

	AvailObject ObjectMyRestrictions (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMyRestrictions(destination);
	}

	AvailObject ObjectMyType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectMyType(destination);
	}

	AvailObject ObjectName (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectName(destination);
	}

	AvailObject ObjectNames (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNames(destination);
	}

	AvailObject ObjectNewNames (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNewNames(destination);
	}

	AvailObject ObjectNext (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNext(destination);
	}

	int ObjectNextIndex (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNextIndex(destination);
	}

	short ObjectNumArgs (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNumArgs(destination);
	}

	short ObjectNumArgsAndLocalsAndStack (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNumArgsAndLocalsAndStack(destination);
	}

	int ObjectNumberOfZones (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNumberOfZones(destination);
	}

	int ObjectNumBlanks (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNumBlanks(destination);
	}

	int ObjectNumFloats (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNumFloats(destination);
	}

	int ObjectNumIntegers (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNumIntegers(destination);
	}

	short ObjectNumLiterals (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNumLiterals(destination);
	}

	short ObjectNumLocals (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNumLocals(destination);
	}

	int ObjectNumLocalsOrArgsOrStack (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNumLocalsOrArgsOrStack(destination);
	}

	int ObjectNumObjects (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNumObjects(destination);
	}

	short ObjectNumOuters (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNumOuters(destination);
	}

	int ObjectNumOuterVars (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNumOuterVars(destination);
	}

	AvailObject ObjectNybbles (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectNybbles(destination);
	}

	AvailObject ObjectPad (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPad(destination);
	}

	AvailObject ObjectParent (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectParent(destination);
	}

	int ObjectPc (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPc(destination);
	}

	void ObjectPostFault (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectPostFault(destination);
	}

	AvailObject ObjectPrevious (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPrevious(destination);
	}

	int ObjectPreviousIndex (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPreviousIndex(destination);
	}

	short ObjectPrimitiveNumber (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPrimitiveNumber(destination);
	}

	int ObjectPriority (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPriority(destination);
	}

	AvailObject ObjectPrivateNames (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPrivateNames(destination);
	}

	AvailObject ObjectPrivateTestingTree (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectPrivateTestingTree(destination);
	}

	AvailObject ObjectProcessGlobals (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectProcessGlobals(destination);
	}

	int ObjectRawQuad1 (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRawQuad1(destination);
	}

	int ObjectRawQuad2 (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRawQuad2(destination);
	}

	void ObjectReadBarrierFault (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectReadBarrierFault(destination);
	}

	void ObjectReleaseVariableOrMakeContentsImmutable (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectReleaseVariableOrMakeContentsImmutable(destination);
	}

	void ObjectRemoveFromQueue (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRemoveFromQueue(destination);
	}

	void ObjectRemoveRestrictions (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectRemoveRestrictions(destination);
	}

	AvailObject ObjectRequiresBlock (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRequiresBlock(destination);
	}

	AvailObject ObjectRestrictions (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRestrictions(destination);
	}

	AvailObject ObjectReturnsBlock (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectReturnsBlock(destination);
	}

	AvailObject ObjectReturnType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectReturnType(destination);
	}

	AvailObject ObjectRootBin (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectRootBin(destination);
	}

	AvailObject ObjectSecondTupleType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSecondTupleType(destination);
	}

	int ObjectSetSize (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSetSize(destination);
	}

	AvailObject ObjectSignature (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSignature(destination);
	}

	int ObjectSize (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSize(destination);
	}

	AvailObject ObjectSizeRange (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectSizeRange(destination);
	}

	int ObjectStackp (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectStackp(destination);
	}

	int ObjectStartingChunkIndex (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectStartingChunkIndex(destination);
	}

	void ObjectStep (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectStep(destination);
	}

	AvailObject ObjectTestingTree (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTestingTree(destination);
	}

	void ObjectTrimExcessLongs (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectTrimExcessLongs(destination);
	}

	AvailObject ObjectTuple (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTuple(destination);
	}

	int ObjectTupleSize (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTupleSize(destination);
	}

	AvailObject ObjectTupleType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTupleType(destination);
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectType(destination);
	}

	AvailObject ObjectTypeTuple (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectTypeTuple(destination);
	}

	AvailObject ObjectUnclassified (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectUnclassified(destination);
	}

	AvailObject ObjectUpperBound (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectUpperBound(destination);
	}

	boolean ObjectUpperInclusive (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectUpperInclusive(destination);
	}

	int ObjectValidity (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectValidity(destination);
	}

	AvailObject ObjectValue (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectValue(destination);
	}

	AvailObject ObjectValuesAsTuple (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectValuesAsTuple(destination);
	}

	AvailObject ObjectValueType (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectValueType(destination);
	}

	AvailObject ObjectVariableBindings (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectVariableBindings(destination);
	}

	AvailObject ObjectVectors (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectVectors(destination);
	}

	void ObjectVerify (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		destination.descriptor().ObjectVerify(destination);
	}

	AvailObject ObjectVisibleNames (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectVisibleNames(destination);
	}

	int ObjectWhichOne (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectWhichOne(destination);
	}

	AvailObject ObjectWordcodes (
			final AvailObject object)
	{
		//  GENERATED reflex method.  Traverse the indirection and pass along the message.

		final AvailObject destination = object.traversed();
		return destination.descriptor().ObjectWordcodes(destination);
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
		return false;
	}



	// java printing

	void printObjectOnAvoidingIndent (
			final AvailObject object, 
			final StringBuilder aStream, 
			final List<AvailObject> recursionList, 
			final int indent)
	{
		object.traversed().printOnAvoidingIndent(
			aStream,
			recursionList,
			indent);
	}



	// operations

	void ObjectScanSubobjects (
			final AvailObject object, 
			final AvailSubobjectVisitor visitor)
	{
		//  Manually constructed scanning method.

		visitor.invokeWithParentIndex(object, -4);
	}

	AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.  If I was mutable I have to make my
		//  target immutable as well (recursively down to immutable descendants).

		if (_isMutable)
		{
			object.descriptor(IndirectionDescriptor.immutableDescriptor());
			object.target().makeImmutable();
		}
		return object;
	}



	// operations-indirections

	AvailObject ObjectTraversed (
			final AvailObject object)
	{
		//  Answer a non-indirection pointed to (transitively) by object.

		final AvailObject finalObject = object.target().traversed();
		//  Shorten the path to one step to reduce amortized traversal costs to approximately inv_Ackermann(N).
		object.target(finalObject);
		return finalObject;
	}





	/* Descriptor lookup */
	public static IndirectionDescriptor mutableDescriptor()
	{
		return (IndirectionDescriptor) allDescriptors [74];
	};
	public static IndirectionDescriptor immutableDescriptor()
	{
		return (IndirectionDescriptor) allDescriptors [75];
	};

}
