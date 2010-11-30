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
import java.util.Iterator;
import java.util.List;

public class IndirectionDescriptor extends AbstractDescriptor
{

	enum ObjectSlots
	{
		TARGET
	}


	// GENERATED accessors

	/**
	 * Setter for field target.
	 */
	@Override
	public void ObjectTarget (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.TARGET, value);
	}

	/**
	 * Getter for field target.
	 */
	@Override
	public AvailObject ObjectTarget (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.TARGET);
	}



	// GENERATED reflex methods

	@Override
	public boolean ObjectAcceptsArgTypesFromClosureType (
			final AvailObject object,
			final AvailObject closureType)
	{
		return ObjectTraversed(object).acceptsArgTypesFromClosureType(closureType);
	}

	@Override
	public boolean ObjectAcceptsArgumentsFromContinuationStackp (
			final AvailObject object,
			final AvailObject continuation,
			final int stackp)
	{
		return ObjectTraversed(object).acceptsArgumentsFromContinuationStackp(
			continuation,
			stackp);
	}

	@Override
	public boolean ObjectAcceptsArgumentTypesFromContinuationStackp (
			final AvailObject object,
			final AvailObject continuation,
			final int stackp)
	{
		return ObjectTraversed(object).acceptsArgumentTypesFromContinuationStackp(
			continuation,
			stackp);
	}

	@Override
	public boolean ObjectAcceptsArrayOfArgTypes (
			final AvailObject object,
			final List<AvailObject> argTypes)
	{
		return ObjectTraversed(object).acceptsArrayOfArgTypes(argTypes);
	}

	@Override
	public boolean ObjectAcceptsArrayOfArgValues (
			final AvailObject object,
			final List<AvailObject> argValues)
	{
		return ObjectTraversed(object).acceptsArrayOfArgValues(argValues);
	}

	@Override
	public boolean ObjectAcceptsTupleOfArgTypes (
			final AvailObject object,
			final AvailObject argTypes)
	{
		return ObjectTraversed(object).acceptsTupleOfArgTypes(argTypes);
	}

	@Override
	public boolean ObjectAcceptsTupleOfArguments (
			final AvailObject object,
			final AvailObject arguments)
	{
		return ObjectTraversed(object).acceptsTupleOfArguments(arguments);
	}

	@Override
	public void ObjectAddDependentChunkId (
			final AvailObject object,
			final int aChunkIndex)
	{
		ObjectTraversed(object).addDependentChunkId(aChunkIndex);
	}

	@Override
	public void ObjectAddImplementation (
			final AvailObject object,
			final AvailObject implementation)
	{
		ObjectTraversed(object).addImplementation(implementation);
	}

	@Override
	public void ObjectAddRestrictions (
			final AvailObject object,
			final AvailObject restrictions)
	{
		ObjectTraversed(object).addRestrictions(restrictions);
	}

	@Override
	public AvailObject ObjectAddToInfinityCanDestroy (
			final AvailObject object,
			final AvailObject anInfinity,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).addToInfinityCanDestroy(
			anInfinity,
			canDestroy);
	}

	@Override
	public AvailObject ObjectAddToIntegerCanDestroy (
			final AvailObject object,
			final AvailObject anInteger,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).addToIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	public void ObjectArgsLocalsStackOutersPrimitive (
			final AvailObject object,
			final int args,
			final int locals,
			final int stack,
			final int outers,
			final int primitive)
	{
		ObjectTraversed(object).argsLocalsStackOutersPrimitive(
			args,
			locals,
			stack,
			outers,
			primitive);
	}

	@Override
	public AvailObject ObjectArgTypeAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).argTypeAt(index);
	}

	@Override
	public void ObjectArgTypeAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		ObjectTraversed(object).argTypeAtPut(
			index,
			value);
	}

	@Override
	public void ObjectAtAddMessageRestrictions (
			final AvailObject object,
			final AvailObject methodName,
			final AvailObject illegalArgMsgs)
	{
		ObjectTraversed(object).atAddMessageRestrictions(
			methodName,
			illegalArgMsgs);
	}

	@Override
	public void ObjectAtAddMethodImplementation (
			final AvailObject object,
			final AvailObject methodName,
			final AvailObject implementation)
	{
		ObjectTraversed(object).atAddMethodImplementation(
			methodName,
			implementation);
	}

	@Override
	public void ObjectAtMessageAddBundle (
			final AvailObject object,
			final AvailObject message,
			final AvailObject bundle)
	{
		ObjectTraversed(object).atMessageAddBundle(
			message,
			bundle);
	}

	@Override
	public void ObjectAtNameAdd (
			final AvailObject object,
			final AvailObject stringName,
			final AvailObject trueName)
	{
		ObjectTraversed(object).atNameAdd(
			stringName,
			trueName);
	}

	@Override
	public void ObjectAtNewNamePut (
			final AvailObject object,
			final AvailObject stringName,
			final AvailObject trueName)
	{
		ObjectTraversed(object).atNewNamePut(
			stringName,
			trueName);
	}

	@Override
	public void ObjectAtPrivateNameAdd (
			final AvailObject object,
			final AvailObject stringName,
			final AvailObject trueName)
	{
		ObjectTraversed(object).atPrivateNameAdd(
			stringName,
			trueName);
	}

	@Override
	public AvailObject ObjectBinAddingElementHashLevelCanDestroy (
			final AvailObject object,
			final AvailObject elementObject,
			final int elementObjectHash,
			final byte myLevel,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).binAddingElementHashLevelCanDestroy(
			elementObject,
			elementObjectHash,
			myLevel,
			canDestroy);
	}

	@Override
	public AvailObject ObjectBinElementAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).binElementAt(index);
	}

	@Override
	public void ObjectBinElementAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		ObjectTraversed(object).binElementAtPut(
			index,
			value);
	}

	@Override
	public boolean ObjectBinHasElementHash (
			final AvailObject object,
			final AvailObject elementObject,
			final int elementObjectHash)
	{
		return ObjectTraversed(object).binHasElementHash(
			elementObject,
			elementObjectHash);
	}

	@Override
	public void ObjectBinHash (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).binHash(value);
	}

	@Override
	public AvailObject ObjectBinRemoveElementHashCanDestroy (
			final AvailObject object,
			final AvailObject elementObject,
			final int elementObjectHash,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).binRemoveElementHashCanDestroy(
			elementObject,
			elementObjectHash,
			canDestroy);
	}

	@Override
	public void ObjectBinSize (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).binSize(value);
	}

	@Override
	public void ObjectBinUnionType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).binUnionType(value);
	}

	@Override
	public void ObjectBitVector (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).bitVector(value);
	}

	@Override
	public void ObjectBodyBlock (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).bodyBlock(value);
	}

	@Override
	public void ObjectBodyBlockRequiresBlockReturnsBlock (
			final AvailObject object,
			final AvailObject bb,
			final AvailObject rqb,
			final AvailObject rtb)
	{
		ObjectTraversed(object).bodyBlockRequiresBlockReturnsBlock(
			bb,
			rqb,
			rtb);
	}

	@Override
	public void ObjectBodySignature (
			final AvailObject object,
			final AvailObject signature)
	{
		ObjectTraversed(object).bodySignature(signature);
	}

	@Override
	public void ObjectBodySignatureRequiresBlockReturnsBlock (
			final AvailObject object,
			final AvailObject bs,
			final AvailObject rqb,
			final AvailObject rtb)
	{
		ObjectTraversed(object).bodySignatureRequiresBlockReturnsBlock(
			bs,
			rqb,
			rtb);
	}

	@Override
	public void ObjectBreakpointBlock (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).breakpointBlock(value);
	}

	@Override
	public void ObjectBuildFilteredBundleTreeFrom (
			final AvailObject object,
			final AvailObject bundleTree)
	{
		ObjectTraversed(object).buildFilteredBundleTreeFrom(bundleTree);
	}

	@Override
	public AvailObject ObjectBundleAtMessageParts (
			final AvailObject object,
			final AvailObject message,
			final AvailObject parts)
	{
		return ObjectTraversed(object).bundleAtMessageParts(
			message,
			parts);
	}

	@Override
	public void ObjectCaller (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).caller(value);
	}

	@Override
	public void ObjectClosure (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).closure(value);
	}

	@Override
	public void ObjectClosureType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).closureType(value);
	}

	@Override
	public void ObjectCode (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).code(value);
	}

	@Override
	public void ObjectCodePoint (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).codePoint(value);
	}

	@Override
	public boolean ObjectCompareFromToWithStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject anotherObject,
			final int startIndex2)
	{
		return ObjectTraversed(object).compareFromToWithStartingAt(
			startIndex1,
			endIndex1,
			anotherObject,
			startIndex2);
	}

	@Override
	public boolean ObjectCompareFromToWithAnyTupleStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aTuple,
			final int startIndex2)
	{
		return ObjectTraversed(object).compareFromToWithAnyTupleStartingAt(
			startIndex1,
			endIndex1,
			aTuple,
			startIndex2);
	}

	@Override
	public boolean ObjectCompareFromToWithByteStringStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aByteString,
			final int startIndex2)
	{
		return ObjectTraversed(object).compareFromToWithByteStringStartingAt(
			startIndex1,
			endIndex1,
			aByteString,
			startIndex2);
	}

	@Override
	public boolean ObjectCompareFromToWithByteTupleStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aByteTuple,
			final int startIndex2)
	{
		return ObjectTraversed(object).compareFromToWithByteTupleStartingAt(
			startIndex1,
			endIndex1,
			aByteTuple,
			startIndex2);
	}

	@Override
	public boolean ObjectCompareFromToWithNybbleTupleStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aNybbleTuple,
			final int startIndex2)
	{
		return ObjectTraversed(object).compareFromToWithNybbleTupleStartingAt(
			startIndex1,
			endIndex1,
			aNybbleTuple,
			startIndex2);
	}

	@Override
	public boolean ObjectCompareFromToWithObjectTupleStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject anObjectTuple,
			final int startIndex2)
	{
		return ObjectTraversed(object).compareFromToWithObjectTupleStartingAt(
			startIndex1,
			endIndex1,
			anObjectTuple,
			startIndex2);
	}

	@Override
	public boolean ObjectCompareFromToWithTwoByteStringStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aTwoByteString,
			final int startIndex2)
	{
		return ObjectTraversed(object).compareFromToWithTwoByteStringStartingAt(
			startIndex1,
			endIndex1,
			aTwoByteString,
			startIndex2);
	}

	@Override
	public void ObjectComplete (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).complete(value);
	}

	@Override
	public int ObjectComputeHashFromTo (
			final AvailObject object,
			final int start,
			final int end)
	{
		return ObjectTraversed(object).computeHashFromTo(
			start,
			end);
	}

	@Override
	public AvailObject ObjectComputeReturnTypeFromArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final AvailInterpreter anAvailInterpreter)
	{
		return ObjectTraversed(object).computeReturnTypeFromArgumentTypesInterpreter(
			argTypes,
			anAvailInterpreter);
	}

	@Override
	public AvailObject ObjectConcatenateTuplesCanDestroy (
			final AvailObject object,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).concatenateTuplesCanDestroy(canDestroy);
	}

	@Override
	public void ObjectConstantBindings (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).constantBindings(value);
	}

	@Override
	public boolean ObjectContainsBlock (
			final AvailObject object,
			final AvailObject aClosure)
	{
		return ObjectTraversed(object).containsBlock(aClosure);
	}

	@Override
	public void ObjectContentType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).contentType(value);
	}

	@Override
	public void ObjectContingentImpSets (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).contingentImpSets(value);
	}

	@Override
	public void ObjectContinuation (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).continuation(value);
	}

	@Override
	public void ObjectCopyToRestrictedTo (
			final AvailObject object,
			final AvailObject filteredBundleTree,
			final AvailObject visibleNames)
	{
		ObjectTraversed(object).copyToRestrictedTo(
			filteredBundleTree,
			visibleNames);
	}

	@Override
	public AvailObject ObjectCopyTupleFromToCanDestroy (
			final AvailObject object,
			final int start,
			final int end,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).copyTupleFromToCanDestroy(
			start,
			end,
			canDestroy);
	}

	@Override
	public boolean ObjectCouldEverBeInvokedWith (
			final AvailObject object,
			final ArrayList<AvailObject> argTypes)
	{
		return ObjectTraversed(object).couldEverBeInvokedWith(argTypes);
	}

	@Override
	public AvailObject ObjectCreateTestingTreeWithPositiveMatchesRemainingPossibilities (
			final AvailObject object,
			final AvailObject positiveTuple,
			final AvailObject possibilities)
	{
		return ObjectTraversed(object).createTestingTreeWithPositiveMatchesRemainingPossibilities(
			positiveTuple,
			possibilities);
	}

	@Override
	public AvailObject ObjectDataAtIndex (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).dataAtIndex(index);
	}

	@Override
	public void ObjectDataAtIndexPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		ObjectTraversed(object).dataAtIndexPut(
			index,
			value);
	}

	@Override
	public void ObjectDefaultType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).defaultType(value);
	}

	@Override
	public void ObjectDependentChunks (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).dependentChunks(value);
	}

	@Override
	public void ObjectDepth (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).depth(value);
	}

	@Override
	public AvailObject ObjectDivideCanDestroy (
			final AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).divideCanDestroy(
			aNumber,
			canDestroy);
	}

	@Override
	public AvailObject ObjectDivideIntoInfinityCanDestroy (
			final AvailObject object,
			final AvailObject anInfinity,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).divideIntoInfinityCanDestroy(
			anInfinity,
			canDestroy);
	}

	@Override
	public AvailObject ObjectDivideIntoIntegerCanDestroy (
			final AvailObject object,
			final AvailObject anInteger,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).divideIntoIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	public AvailObject ObjectElementAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).elementAt(index);
	}

	@Override
	public void ObjectElementAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		ObjectTraversed(object).elementAtPut(
			index,
			value);
	}

	@Override
	public int ObjectEndOfZone (
			final AvailObject object,
			final int zone)
	{
		return ObjectTraversed(object).endOfZone(zone);
	}

	@Override
	public int ObjectEndSubtupleIndexInZone (
			final AvailObject object,
			final int zone)
	{
		return ObjectTraversed(object).endSubtupleIndexInZone(zone);
	}

	@Override
	public boolean ObjectEquals (
			final AvailObject object,
			final AvailObject another)
	{
		return ObjectTraversed(object).equals(another);
	}

	@Override
	public boolean ObjectEqualsAnyTuple (
			final AvailObject object,
			final AvailObject anotherTuple)
	{
		return ObjectTraversed(object).equalsAnyTuple(anotherTuple);
	}

	@Override
	public boolean ObjectEqualsByteString (
			final AvailObject object,
			final AvailObject aByteString)
	{
		return ObjectTraversed(object).equalsByteString(aByteString);
	}

	@Override
	public boolean ObjectEqualsByteTuple (
			final AvailObject object,
			final AvailObject aByteTuple)
	{
		return ObjectTraversed(object).equalsByteTuple(aByteTuple);
	}

	@Override
	public boolean ObjectEqualsCharacterWithCodePoint (
			final AvailObject object,
			final int otherCodePoint)
	{
		return ObjectTraversed(object).equalsCharacterWithCodePoint(otherCodePoint);
	}

	@Override
	public boolean ObjectEqualsClosure (
			final AvailObject object,
			final AvailObject aClosure)
	{
		return ObjectTraversed(object).equalsClosure(aClosure);
	}

	@Override
	public boolean ObjectEqualsClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		return ObjectTraversed(object).equalsClosureType(aClosureType);
	}

	@Override
	public boolean ObjectEqualsCompiledCode (
			final AvailObject object,
			final AvailObject aCompiledCode)
	{
		return ObjectTraversed(object).equalsCompiledCode(aCompiledCode);
	}

	@Override
	public boolean ObjectEqualsContainer (
			final AvailObject object,
			final AvailObject aContainer)
	{
		return ObjectTraversed(object).equalsContainer(aContainer);
	}

	@Override
	public boolean ObjectEqualsContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		return ObjectTraversed(object).equalsContainerType(aContainerType);
	}

	@Override
	public boolean ObjectEqualsContinuation (
			final AvailObject object,
			final AvailObject aContinuation)
	{
		return ObjectTraversed(object).equalsContinuation(aContinuation);
	}

	@Override
	public boolean ObjectEqualsContinuationType (
			final AvailObject object,
			final AvailObject aType)
	{
		return ObjectTraversed(object).equalsContinuationType(aType);
	}

	@Override
	public boolean ObjectEqualsDouble (
			final AvailObject object,
			final AvailObject aDoubleObject)
	{
		return ObjectTraversed(object).equalsDouble(aDoubleObject);
	}

	@Override
	public boolean ObjectEqualsFloat (
			final AvailObject object,
			final AvailObject aFloatObject)
	{
		return ObjectTraversed(object).equalsFloat(aFloatObject);
	}

	@Override
	public boolean ObjectEqualsGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aType)
	{
		return ObjectTraversed(object).equalsGeneralizedClosureType(aType);
	}

	@Override
	public boolean ObjectEqualsInfinity (
			final AvailObject object,
			final AvailObject anInfinity)
	{
		return ObjectTraversed(object).equalsInfinity(anInfinity);
	}

	@Override
	public boolean ObjectEqualsInteger (
			final AvailObject object,
			final AvailObject anAvailInteger)
	{
		return ObjectTraversed(object).equalsInteger(anAvailInteger);
	}

	@Override
	public boolean ObjectEqualsIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		return ObjectTraversed(object).equalsIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public boolean ObjectEqualsList (
			final AvailObject object,
			final AvailObject aList)
	{
		return ObjectTraversed(object).equalsList(aList);
	}

	@Override
	public boolean ObjectEqualsListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		return ObjectTraversed(object).equalsListType(aListType);
	}

	@Override
	public boolean ObjectEqualsMap (
			final AvailObject object,
			final AvailObject aMap)
	{
		return ObjectTraversed(object).equalsMap(aMap);
	}

	@Override
	public boolean ObjectEqualsMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		return ObjectTraversed(object).equalsMapType(aMapType);
	}

	@Override
	public boolean ObjectEqualsNybbleTuple (
			final AvailObject object,
			final AvailObject aNybbleTuple)
	{
		return ObjectTraversed(object).equalsNybbleTuple(aNybbleTuple);
	}

	@Override
	public boolean ObjectEqualsObject (
			final AvailObject object,
			final AvailObject anObject)
	{
		return ObjectTraversed(object).equalsObject(anObject);
	}

	@Override
	public boolean ObjectEqualsObjectTuple (
			final AvailObject object,
			final AvailObject anObjectTuple)
	{
		return ObjectTraversed(object).equalsObjectTuple(anObjectTuple);
	}

	@Override
	public boolean ObjectEqualsPrimitiveType (
			final AvailObject object,
			final AvailObject aPrimitiveType)
	{
		return ObjectTraversed(object).equalsPrimitiveType(aPrimitiveType);
	}

	@Override
	public boolean ObjectEqualsSet (
			final AvailObject object,
			final AvailObject aSet)
	{
		return ObjectTraversed(object).equalsSet(aSet);
	}

	@Override
	public boolean ObjectEqualsSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		return ObjectTraversed(object).equalsSetType(aSetType);
	}

	@Override
	public boolean ObjectEqualsTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		return ObjectTraversed(object).equalsTupleType(aTupleType);
	}

	@Override
	public boolean ObjectEqualsTwoByteString (
			final AvailObject object,
			final AvailObject aTwoByteString)
	{
		return ObjectTraversed(object).equalsTwoByteString(aTwoByteString);
	}

	@Override
	public void ObjectExecutionMode (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).executionMode(value);
	}

	@Override
	public void ObjectExecutionState (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).executionState(value);
	}

	@Override
	public byte ObjectExtractNybbleFromTupleAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).extractNybbleFromTupleAt(index);
	}

	@Override
	public void ObjectFieldMap (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).fieldMap(value);
	}

	@Override
	public void ObjectFieldTypeMap (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).fieldTypeMap(value);
	}

	@Override
	public List<AvailObject> ObjectFilterByTypes (
			final AvailObject object,
			final List<AvailObject> argTypes)
	{
		return ObjectTraversed(object).filterByTypes(argTypes);
	}

	@Override
	public void ObjectFilteredBundleTree (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).filteredBundleTree(value);
	}

	@Override
	public void ObjectFirstTupleType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).firstTupleType(value);
	}

	@Override
	public AvailObject ObjectForZoneSetSubtupleStartSubtupleIndexEndOfZone (
			final AvailObject object,
			final int zone,
			final AvailObject newSubtuple,
			final int startSubtupleIndex,
			final int endOfZone)
	{
		return ObjectTraversed(object).forZoneSetSubtupleStartSubtupleIndexEndOfZone(
			zone,
			newSubtuple,
			startSubtupleIndex,
			endOfZone);
	}

	@Override
	public boolean ObjectGreaterThanInteger (
			final AvailObject object,
			final AvailObject another)
	{
		return ObjectTraversed(object).greaterThanInteger(another);
	}

	@Override
	public boolean ObjectGreaterThanSignedInfinity (
			final AvailObject object,
			final AvailObject another)
	{
		return ObjectTraversed(object).greaterThanSignedInfinity(another);
	}

	@Override
	public boolean ObjectHasElement (
			final AvailObject object,
			final AvailObject elementObject)
	{
		return ObjectTraversed(object).hasElement(elementObject);
	}

	@Override
	public void ObjectHash (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).hash(value);
	}

	@Override
	public int ObjectHashFromTo (
			final AvailObject object,
			final int startIndex,
			final int endIndex)
	{
		return ObjectTraversed(object).hashFromTo(
			startIndex,
			endIndex);
	}

	@Override
	public void ObjectHashOrZero (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).hashOrZero(value);
	}

	@Override
	public boolean ObjectHasKey (
			final AvailObject object,
			final AvailObject keyObject)
	{
		return ObjectTraversed(object).hasKey(keyObject);
	}

	@Override
	public boolean ObjectHasObjectInstance (
			final AvailObject object,
			final AvailObject potentialInstance)
	{
		return ObjectTraversed(object).hasObjectInstance(potentialInstance);
	}

	@Override
	public void ObjectHiLevelTwoChunkLowOffset (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).hiLevelTwoChunkLowOffset(value);
	}

	@Override
	public void ObjectHiNumLocalsLowNumArgs (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).hiNumLocalsLowNumArgs(value);
	}

	@Override
	public void ObjectHiPrimitiveLowNumArgsAndLocalsAndStack (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).hiPrimitiveLowNumArgsAndLocalsAndStack(value);
	}

	@Override
	public void ObjectHiStartingChunkIndexLowNumOuters (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).hiStartingChunkIndexLowNumOuters(value);
	}

	@Override
	public ArrayList<AvailObject> ObjectImplementationsAtOrBelow (
			final AvailObject object,
			final ArrayList<AvailObject> argTypes)
	{
		return ObjectTraversed(object).implementationsAtOrBelow(argTypes);
	}

	@Override
	public void ObjectImplementationsTuple (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).implementationsTuple(value);
	}

	@Override
	public AvailObject ObjectIncludeBundleAtMessageParts (
			final AvailObject object,
			final AvailObject message,
			final AvailObject parts)
	{
		return ObjectTraversed(object).includeBundleAtMessageParts(
			message,
			parts);
	}

	@Override
	public boolean ObjectIncludes (
			final AvailObject object,
			final AvailObject imp)
	{
		return ObjectTraversed(object).includes(imp);
	}

	@Override
	public void ObjectInclusiveFlags (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).inclusiveFlags(value);
	}

	@Override
	public void ObjectIncomplete (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).incomplete(value);
	}

	@Override
	public void ObjectIndex (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).index(value);
	}

	@Override
	public void ObjectInnerType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).innerType(value);
	}

	@Override
	public void ObjectInstance (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).instance(value);
	}

	@Override
	public void ObjectInternalHash (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).internalHash(value);
	}

	@Override
	public void ObjectInterruptRequestFlag (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).interruptRequestFlag(value);
	}

	@Override
	public void ObjectInvocationCount (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).invocationCount(value);
	}

	@Override
	public boolean ObjectIsBetterRepresentationThan (
			final AvailObject object,
			final AvailObject anotherObject)
	{
		return ObjectTraversed(object).isBetterRepresentationThan(anotherObject);
	}

	@Override
	public boolean ObjectIsBetterRepresentationThanTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		return ObjectTraversed(object).isBetterRepresentationThanTupleType(aTupleType);
	}

	@Override
	public boolean ObjectIsBinSubsetOf (
			final AvailObject object,
			final AvailObject potentialSuperset)
	{
		return ObjectTraversed(object).isBinSubsetOf(potentialSuperset);
	}

	@Override
	public boolean ObjectIsInstanceOfSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		return ObjectTraversed(object).isInstanceOfSubtypeOf(aType);
	}

	@Override
	public void ObjectIsSaved (
			final AvailObject object,
			final boolean aBoolean)
	{
		ObjectTraversed(object).isSaved(aBoolean);
	}

	@Override
	public boolean ObjectIsSubsetOf (
			final AvailObject object,
			final AvailObject another)
	{
		return ObjectTraversed(object).isSubsetOf(another);
	}

	@Override
	public boolean ObjectIsSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		return ObjectTraversed(object).isSubtypeOf(aType);
	}

	@Override
	public boolean ObjectIsSupertypeOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		return ObjectTraversed(object).isSupertypeOfClosureType(aClosureType);
	}

	@Override
	public boolean ObjectIsSupertypeOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		return ObjectTraversed(object).isSupertypeOfContainerType(aContainerType);
	}

	@Override
	public boolean ObjectIsSupertypeOfContinuationType (
			final AvailObject object,
			final AvailObject aContinuationType)
	{
		return ObjectTraversed(object).isSupertypeOfContinuationType(aContinuationType);
	}

	@Override
	public boolean ObjectIsSupertypeOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		return ObjectTraversed(object).isSupertypeOfCyclicType(aCyclicType);
	}

	@Override
	public boolean ObjectIsSupertypeOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		return ObjectTraversed(object).isSupertypeOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public boolean ObjectIsSupertypeOfIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		return ObjectTraversed(object).isSupertypeOfIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public boolean ObjectIsSupertypeOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		return ObjectTraversed(object).isSupertypeOfListType(aListType);
	}

	@Override
	public boolean ObjectIsSupertypeOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		return ObjectTraversed(object).isSupertypeOfMapType(aMapType);
	}

	@Override
	public boolean ObjectIsSupertypeOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		return ObjectTraversed(object).isSupertypeOfObjectMeta(anObjectMeta);
	}

	@Override
	public boolean ObjectIsSupertypeOfObjectMetaMeta (
			final AvailObject object,
			final AvailObject anObjectMetaMeta)
	{
		return ObjectTraversed(object).isSupertypeOfObjectMetaMeta(anObjectMetaMeta);
	}

	@Override
	public boolean ObjectIsSupertypeOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		return ObjectTraversed(object).isSupertypeOfObjectType(anObjectType);
	}

	@Override
	public boolean ObjectIsSupertypeOfPrimitiveType (
			final AvailObject object,
			final AvailObject aPrimitiveType)
	{
		return ObjectTraversed(object).isSupertypeOfPrimitiveType(aPrimitiveType);
	}

	@Override
	public boolean ObjectIsSupertypeOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		return ObjectTraversed(object).isSupertypeOfSetType(aSetType);
	}

	@Override
	public boolean ObjectIsSupertypeOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		return ObjectTraversed(object).isSupertypeOfTupleType(aTupleType);
	}

	@Override
	public void ObjectIsValid (
			final AvailObject object,
			final boolean aBoolean)
	{
		ObjectTraversed(object).isValid(aBoolean);
	}

	@Override
	public boolean ObjectIsValidForArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final AvailInterpreter interpreter)
	{
		return ObjectTraversed(object).isValidForArgumentTypesInterpreter(
			argTypes,
			interpreter);
	}

	@Override
	public AvailObject ObjectKeyAtIndex (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).keyAtIndex(index);
	}

	@Override
	public void ObjectKeyAtIndexPut (
			final AvailObject object,
			final int index,
			final AvailObject keyObject)
	{
		ObjectTraversed(object).keyAtIndexPut(
			index,
			keyObject);
	}

	@Override
	public void ObjectKeyType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).keyType(value);
	}

	@Override
	public boolean ObjectLessOrEqual (
			final AvailObject object,
			final AvailObject another)
	{
		return ObjectTraversed(object).lessOrEqual(another);
	}

	@Override
	public boolean ObjectLessThan (
			final AvailObject object,
			final AvailObject another)
	{
		return ObjectTraversed(object).lessThan(another);
	}

	@Override
	public void ObjectLevelTwoChunkIndexOffset (
			final AvailObject object,
			final int index,
			final int offset)
	{
		ObjectTraversed(object).levelTwoChunkIndexOffset(
			index,
			offset);
	}

	@Override
	public AvailObject ObjectLiteralAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).literalAt(index);
	}

	@Override
	public void ObjectLiteralAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		ObjectTraversed(object).literalAtPut(
			index,
			value);
	}

	@Override
	public AvailObject ObjectLocalOrArgOrStackAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).localOrArgOrStackAt(index);
	}

	@Override
	public void ObjectLocalOrArgOrStackAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		ObjectTraversed(object).localOrArgOrStackAtPut(
			index,
			value);
	}

	@Override
	public AvailObject ObjectLocalTypeAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).localTypeAt(index);
	}

	@Override
	public AvailObject ObjectLookupByTypesFromArray (
			final AvailObject object,
			final List<AvailObject> argumentTypeArray)
	{
		return ObjectTraversed(object).lookupByTypesFromArray(argumentTypeArray);
	}

	@Override
	public AvailObject ObjectLookupByTypesFromContinuationStackp (
			final AvailObject object,
			final AvailObject continuation,
			final int stackp)
	{
		return ObjectTraversed(object).lookupByTypesFromContinuationStackp(
			continuation,
			stackp);
	}

	@Override
	public AvailObject ObjectLookupByTypesFromTuple (
			final AvailObject object,
			final AvailObject argumentTypeTuple)
	{
		return ObjectTraversed(object).lookupByTypesFromTuple(argumentTypeTuple);
	}

	@Override
	public AvailObject ObjectLookupByValuesFromArray (
			final AvailObject object,
			final List<AvailObject> argumentArray)
	{
		return ObjectTraversed(object).lookupByValuesFromArray(argumentArray);
	}

	@Override
	public AvailObject ObjectLookupByValuesFromContinuationStackp (
			final AvailObject object,
			final AvailObject continuation,
			final int stackp)
	{
		return ObjectTraversed(object).lookupByValuesFromContinuationStackp(
			continuation,
			stackp);
	}

	@Override
	public AvailObject ObjectLookupByValuesFromTuple (
			final AvailObject object,
			final AvailObject argumentTuple)
	{
		return ObjectTraversed(object).lookupByValuesFromTuple(argumentTuple);
	}

	@Override
	public void ObjectLowerBound (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).lowerBound(value);
	}

	@Override
	public void ObjectLowerInclusiveUpperInclusive (
			final AvailObject object,
			final boolean lowInc,
			final boolean highInc)
	{
		ObjectTraversed(object).lowerInclusiveUpperInclusive(
			lowInc,
			highInc);
	}

	@Override
	public AvailObject ObjectMapAt (
			final AvailObject object,
			final AvailObject keyObject)
	{
		return ObjectTraversed(object).mapAt(keyObject);
	}

	@Override
	public AvailObject ObjectMapAtPuttingCanDestroy (
			final AvailObject object,
			final AvailObject keyObject,
			final AvailObject newValueObject,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).mapAtPuttingCanDestroy(
			keyObject,
			newValueObject,
			canDestroy);
	}

	@Override
	public void ObjectMapSize (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).mapSize(value);
	}

	@Override
	public AvailObject ObjectMapWithoutKeyCanDestroy (
			final AvailObject object,
			final AvailObject keyObject,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).mapWithoutKeyCanDestroy(
			keyObject,
			canDestroy);
	}

	@Override
	public void ObjectMessage (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).message(value);
	}

	@Override
	public void ObjectMessageParts (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).messageParts(value);
	}

	@Override
	public void ObjectMethods (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).methods(value);
	}

	@Override
	public AvailObject ObjectMinusCanDestroy (
			final AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).minusCanDestroy(
			aNumber,
			canDestroy);
	}

	@Override
	public AvailObject ObjectMultiplyByInfinityCanDestroy (
			final AvailObject object,
			final AvailObject anInfinity,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).multiplyByInfinityCanDestroy(
			anInfinity,
			canDestroy);
	}

	@Override
	public AvailObject ObjectMultiplyByIntegerCanDestroy (
			final AvailObject object,
			final AvailObject anInteger,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).multiplyByIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	public void ObjectMyObjectMeta (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).myObjectMeta(value);
	}

	@Override
	public void ObjectMyObjectType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).myObjectType(value);
	}

	@Override
	public void ObjectMyRestrictions (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).myRestrictions(value);
	}

	@Override
	public void ObjectMyType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).myType(value);
	}

	@Override
	public void ObjectName (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).name(value);
	}

	@Override
	public void ObjectNames (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).names(value);
	}

	@Override
	public boolean ObjectNameVisible (
			final AvailObject object,
			final AvailObject trueName)
	{
		return ObjectTraversed(object).nameVisible(trueName);
	}

	@Override
	public void ObjectNecessaryImplementationSetChanged (
			final AvailObject object,
			final AvailObject anImplementationSet)
	{
		ObjectTraversed(object).necessaryImplementationSetChanged(anImplementationSet);
	}

	@Override
	public void ObjectNewNames (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).newNames(value);
	}

	@Override
	public void ObjectNext (
			final AvailObject object,
			final AvailObject nextChunk)
	{
		ObjectTraversed(object).next(nextChunk);
	}

	@Override
	public void ObjectNextIndex (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).nextIndex(value);
	}

	@Override
	public void ObjectNumBlanks (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).numBlanks(value);
	}

	@Override
	public void ObjectNumFloats (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).numFloats(value);
	}

	@Override
	public void ObjectNumIntegers (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).numIntegers(value);
	}

	@Override
	public void ObjectNumObjects (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).numObjects(value);
	}

	@Override
	public void ObjectNybbles (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).nybbles(value);
	}

	@Override
	public boolean ObjectOptionallyNilOuterVar (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).optionallyNilOuterVar(index);
	}

	@Override
	public AvailObject ObjectOuterTypeAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).outerTypeAt(index);
	}

	@Override
	public void ObjectOuterTypesLocalTypes (
			final AvailObject object,
			final AvailObject tupleOfOuterTypes,
			final AvailObject tupleOfLocalContainerTypes)
	{
		ObjectTraversed(object).outerTypesLocalTypes(
			tupleOfOuterTypes,
			tupleOfLocalContainerTypes);
	}

	@Override
	public AvailObject ObjectOuterVarAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).outerVarAt(index);
	}

	@Override
	public void ObjectOuterVarAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		ObjectTraversed(object).outerVarAtPut(
			index,
			value);
	}

	@Override
	public void ObjectPad1 (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).pad1(value);
	}

	@Override
	public void ObjectPad2 (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).pad2(value);
	}

	@Override
	public void ObjectParent (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).parent(value);
	}

	@Override
	public void ObjectPc (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).pc(value);
	}

	@Override
	public AvailObject ObjectPlusCanDestroy (
			final AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).plusCanDestroy(
			aNumber,
			canDestroy);
	}

	@Override
	public int ObjectPopulateTupleStartingAt (
			final AvailObject object,
			final AvailObject mutableTuple,
			final int startingIndex)
	{
		return ObjectTraversed(object).populateTupleStartingAt(
			mutableTuple,
			startingIndex);
	}

	@Override
	public void ObjectPrevious (
			final AvailObject object,
			final AvailObject previousChunk)
	{
		ObjectTraversed(object).previous(previousChunk);
	}

	@Override
	public void ObjectPreviousIndex (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).previousIndex(value);
	}

	@Override
	public void ObjectPriority (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).priority(value);
	}

	@Override
	public AvailObject ObjectPrivateAddElement (
			final AvailObject object,
			final AvailObject element)
	{
		return ObjectTraversed(object).privateAddElement(element);
	}

	@Override
	public AvailObject ObjectPrivateExcludeElement (
			final AvailObject object,
			final AvailObject element)
	{
		return ObjectTraversed(object).privateExcludeElement(element);
	}

	@Override
	public AvailObject ObjectPrivateExcludeElementKnownIndex (
			final AvailObject object,
			final AvailObject element,
			final int knownIndex)
	{
		return ObjectTraversed(object).privateExcludeElementKnownIndex(
			element,
			knownIndex);
	}

	@Override
	public AvailObject ObjectPrivateExcludeKey (
			final AvailObject object,
			final AvailObject keyObject)
	{
		return ObjectTraversed(object).privateExcludeKey(keyObject);
	}

	@Override
	public AvailObject ObjectPrivateMapAtPut (
			final AvailObject object,
			final AvailObject keyObject,
			final AvailObject valueObject)
	{
		return ObjectTraversed(object).privateMapAtPut(
			keyObject,
			valueObject);
	}

	@Override
	public void ObjectPrivateNames (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).privateNames(value);
	}

	@Override
	public void ObjectPrivateTestingTree (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).privateTestingTree(value);
	}

	@Override
	public void ObjectProcessGlobals (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).processGlobals(value);
	}

	@Override
	public short ObjectRawByteAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).rawByteAt(index);
	}

	@Override
	public void ObjectRawByteAtPut (
			final AvailObject object,
			final int index,
			final short anInteger)
	{
		ObjectTraversed(object).rawByteAtPut(
			index,
			anInteger);
	}

	@Override
	public short ObjectRawByteForCharacterAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).rawByteForCharacterAt(index);
	}

	@Override
	public void ObjectRawByteForCharacterAtPut (
			final AvailObject object,
			final int index,
			final short anInteger)
	{
		ObjectTraversed(object).rawByteForCharacterAtPut(
			index,
			anInteger);
	}

	@Override
	public byte ObjectRawNybbleAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).rawNybbleAt(index);
	}

	@Override
	public void ObjectRawNybbleAtPut (
			final AvailObject object,
			final int index,
			final byte aNybble)
	{
		ObjectTraversed(object).rawNybbleAtPut(
			index,
			aNybble);
	}

	@Override
	public void ObjectRawQuad1 (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).rawQuad1(value);
	}

	@Override
	public void ObjectRawQuad2 (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).rawQuad2(value);
	}

	@Override
	public int ObjectRawQuadAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).rawQuadAt(index);
	}

	@Override
	public void ObjectRawQuadAtPut (
			final AvailObject object,
			final int index,
			final int value)
	{
		ObjectTraversed(object).rawQuadAtPut(
			index,
			value);
	}

	@Override
	public short ObjectRawShortForCharacterAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).rawShortForCharacterAt(index);
	}

	@Override
	public void ObjectRawShortForCharacterAtPut (
			final AvailObject object,
			final int index,
			final short anInteger)
	{
		ObjectTraversed(object).rawShortForCharacterAtPut(
			index,
			anInteger);
	}

	@Override
	public int ObjectRawSignedIntegerAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).rawSignedIntegerAt(index);
	}

	@Override
	public void ObjectRawSignedIntegerAtPut (
			final AvailObject object,
			final int index,
			final int value)
	{
		ObjectTraversed(object).rawSignedIntegerAtPut(
			index,
			value);
	}

	@Override
	public long ObjectRawUnsignedIntegerAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).rawUnsignedIntegerAt(index);
	}

	@Override
	public void ObjectRawUnsignedIntegerAtPut (
			final AvailObject object,
			final int index,
			final int value)
	{
		ObjectTraversed(object).rawUnsignedIntegerAtPut(
			index,
			value);
	}

	@Override
	public void ObjectRemoveDependentChunkId (
			final AvailObject object,
			final int aChunkIndex)
	{
		ObjectTraversed(object).removeDependentChunkId(aChunkIndex);
	}

	@Override
	public void ObjectRemoveFrom (
		final AvailObject object,
		final AvailInterpreter anInterpreter)
	{
		ObjectTraversed(object).removeFrom(anInterpreter);
	}

	@Override
	public void ObjectRemoveImplementation (
			final AvailObject object,
			final AvailObject implementation)
	{
		ObjectTraversed(object).removeImplementation(implementation);
	}

	@Override
	public boolean ObjectRemoveMessageParts (
		final AvailObject object,
		final AvailObject message,
		final AvailObject parts)
	{
		return ObjectTraversed(object).removeMessageParts(
			message,
			parts);
	}

	@Override
	public void ObjectRemoveRestrictions (
		final AvailObject object,
		final AvailObject obsoleteRestrictions)
	{
		ObjectTraversed(object).removeRestrictions(obsoleteRestrictions);
	}

	@Override
	public void ObjectRequiresBlock (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).requiresBlock(value);
	}

	@Override
	public void ObjectResolvedForwardWithName (
			final AvailObject object,
			final AvailObject forwardImplementation,
			final AvailObject methodName)
	{
		ObjectTraversed(object).resolvedForwardWithName(
			forwardImplementation,
			methodName);
	}

	@Override
	public void ObjectRestrictions (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).restrictions(value);
	}

	@Override
	public void ObjectReturnsBlock (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).returnsBlock(value);
	}

	@Override
	public void ObjectReturnType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).returnType(value);
	}

	@Override
	public void ObjectRootBin (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).rootBin(value);
	}

	@Override
	public void ObjectSecondTupleType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).secondTupleType(value);
	}

	@Override
	public AvailObject ObjectSetIntersectionCanDestroy (
			final AvailObject object,
			final AvailObject otherSet,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).setIntersectionCanDestroy(
			otherSet,
			canDestroy);
	}

	@Override
	public AvailObject ObjectSetMinusCanDestroy (
			final AvailObject object,
			final AvailObject otherSet,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).setMinusCanDestroy(
			otherSet,
			canDestroy);
	}

	@Override
	public void ObjectSetSize (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).setSize(value);
	}

	@Override
	public void ObjectSetSubtupleForZoneTo (
			final AvailObject object,
			final int zoneIndex,
			final AvailObject newTuple)
	{
		ObjectTraversed(object).setSubtupleForZoneTo(
			zoneIndex,
			newTuple);
	}

	@Override
	public AvailObject ObjectSetUnionCanDestroy (
			final AvailObject object,
			final AvailObject otherSet,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).setUnionCanDestroy(
			otherSet,
			canDestroy);
	}

	@Override
	public void ObjectSetValue (
			final AvailObject object,
			final AvailObject newValue)
	{
		ObjectTraversed(object).setValue(newValue);
	}

	@Override
	public AvailObject ObjectSetWithElementCanDestroy (
			final AvailObject object,
			final AvailObject newElementObject,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).setWithElementCanDestroy(
			newElementObject,
			canDestroy);
	}

	@Override
	public AvailObject ObjectSetWithoutElementCanDestroy (
			final AvailObject object,
			final AvailObject elementObjectToExclude,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).setWithoutElementCanDestroy(
			elementObjectToExclude,
			canDestroy);
	}

	@Override
	public void ObjectSignature (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).signature(value);
	}

	@Override
	public void ObjectSize (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).size(value);
	}

	@Override
	public int ObjectSizeOfZone (
			final AvailObject object,
			final int zone)
	{
		return ObjectTraversed(object).sizeOfZone(zone);
	}

	@Override
	public void ObjectSizeRange (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).sizeRange(value);
	}

	@Override
	public void ObjectSpecialActions (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).specialActions(value);
	}

	@Override
	public AvailObject ObjectStackAt (
			final AvailObject object,
			final int slotIndex)
	{
		return ObjectTraversed(object).stackAt(slotIndex);
	}

	@Override
	public void ObjectStackAtPut (
			final AvailObject object,
			final int slotIndex,
			final AvailObject anObject)
	{
		ObjectTraversed(object).stackAtPut(
			slotIndex,
			anObject);
	}

	@Override
	public void ObjectStackp (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).stackp(value);
	}

	@Override
	public void ObjectStartingChunkIndex (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).startingChunkIndex(value);
	}

	@Override
	public int ObjectStartOfZone (
			final AvailObject object,
			final int zone)
	{
		return ObjectTraversed(object).startOfZone(zone);
	}

	@Override
	public int ObjectStartSubtupleIndexInZone (
			final AvailObject object,
			final int zone)
	{
		return ObjectTraversed(object).startSubtupleIndexInZone(zone);
	}

	@Override
	public AvailObject ObjectSubtractFromInfinityCanDestroy (
			final AvailObject object,
			final AvailObject anInfinity,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).subtractFromInfinityCanDestroy(
			anInfinity,
			canDestroy);
	}

	@Override
	public AvailObject ObjectSubtractFromIntegerCanDestroy (
			final AvailObject object,
			final AvailObject anInteger,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).subtractFromIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	public AvailObject ObjectSubtupleForZone (
			final AvailObject object,
			final int zone)
	{
		return ObjectTraversed(object).subtupleForZone(zone);
	}

	@Override
	public AvailObject ObjectTimesCanDestroy (
			final AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).timesCanDestroy(
			aNumber,
			canDestroy);
	}

	@Override
	public int ObjectTranslateToZone (
			final AvailObject object,
			final int tupleIndex,
			final int zoneIndex)
	{
		return ObjectTraversed(object).translateToZone(
			tupleIndex,
			zoneIndex);
	}

	@Override
	public AvailObject ObjectTrueNamesForStringName (
			final AvailObject object,
			final AvailObject stringName)
	{
		return ObjectTraversed(object).trueNamesForStringName(stringName);
	}

	@Override
	public AvailObject ObjectTruncateTo (
			final AvailObject object,
			final int newTupleSize)
	{
		return ObjectTraversed(object).truncateTo(newTupleSize);
	}

	@Override
	public void ObjectTuple (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).tuple(value);
	}

	@Override
	public AvailObject ObjectTupleAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).tupleAt(index);
	}

	@Override
	public void ObjectTupleAtPut (
			final AvailObject object,
			final int index,
			final AvailObject aNybbleObject)
	{
		ObjectTraversed(object).tupleAtPut(
			index,
			aNybbleObject);
	}

	@Override
	public AvailObject ObjectTupleAtPuttingCanDestroy (
			final AvailObject object,
			final int index,
			final AvailObject newValueObject,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).tupleAtPuttingCanDestroy(
			index,
			newValueObject,
			canDestroy);
	}

	@Override
	public int ObjectTupleIntAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).tupleIntAt(index);
	}

	@Override
	public void ObjectTupleType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).tupleType(value);
	}

	@Override
	public void ObjectType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).type(value);
	}

	@Override
	public AvailObject ObjectTypeAtIndex (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).typeAtIndex(index);
	}

	@Override
	public boolean ObjectTypeEquals (
			final AvailObject object,
			final AvailObject aType)
	{
		return ObjectTraversed(object).typeEquals(aType);
	}

	@Override
	public AvailObject ObjectTypeIntersection (
			final AvailObject object,
			final AvailObject another)
	{
		return ObjectTraversed(object).typeIntersection(another);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		return ObjectTraversed(object).typeIntersectionOfClosureType(aClosureType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfClosureTypeCanDestroy (
			final AvailObject object,
			final AvailObject aClosureType,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).typeIntersectionOfClosureTypeCanDestroy(
			aClosureType,
			canDestroy);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		return ObjectTraversed(object).typeIntersectionOfContainerType(aContainerType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfContinuationType (
			final AvailObject object,
			final AvailObject aContinuationType)
	{
		return ObjectTraversed(object).typeIntersectionOfContinuationType(aContinuationType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		return ObjectTraversed(object).typeIntersectionOfCyclicType(aCyclicType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		return ObjectTraversed(object).typeIntersectionOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfGeneralizedClosureTypeCanDestroy (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).typeIntersectionOfGeneralizedClosureTypeCanDestroy(
			aGeneralizedClosureType,
			canDestroy);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		return ObjectTraversed(object).typeIntersectionOfIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		return ObjectTraversed(object).typeIntersectionOfListType(aListType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		return ObjectTraversed(object).typeIntersectionOfMapType(aMapType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfMeta (
			final AvailObject object,
			final AvailObject someMeta)
	{
		return ObjectTraversed(object).typeIntersectionOfMeta(someMeta);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		return ObjectTraversed(object).typeIntersectionOfObjectMeta(anObjectMeta);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfObjectMetaMeta (
			final AvailObject object,
			final AvailObject anObjectMetaMeta)
	{
		return ObjectTraversed(object).typeIntersectionOfObjectMetaMeta(anObjectMetaMeta);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		return ObjectTraversed(object).typeIntersectionOfObjectType(anObjectType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		return ObjectTraversed(object).typeIntersectionOfSetType(aSetType);
	}

	@Override
	public AvailObject ObjectTypeIntersectionOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		return ObjectTraversed(object).typeIntersectionOfTupleType(aTupleType);
	}

	@Override
	public void ObjectTypeTuple (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).typeTuple(value);
	}

	@Override
	public AvailObject ObjectTypeUnion (
			final AvailObject object,
			final AvailObject another)
	{
		return ObjectTraversed(object).typeUnion(another);
	}

	@Override
	public AvailObject ObjectTypeUnionOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		return ObjectTraversed(object).typeUnionOfClosureType(aClosureType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfClosureTypeCanDestroy (
			final AvailObject object,
			final AvailObject aClosureType,
			final boolean canDestroy)
	{
		return ObjectTraversed(object).typeUnionOfClosureTypeCanDestroy(
			aClosureType,
			canDestroy);
	}

	@Override
	public AvailObject ObjectTypeUnionOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		return ObjectTraversed(object).typeUnionOfContainerType(aContainerType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfContinuationType (
			final AvailObject object,
			final AvailObject aContinuationType)
	{
		return ObjectTraversed(object).typeUnionOfContinuationType(aContinuationType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		return ObjectTraversed(object).typeUnionOfCyclicType(aCyclicType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		return ObjectTraversed(object).typeUnionOfGeneralizedClosureType(aGeneralizedClosureType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		return ObjectTraversed(object).typeUnionOfIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		return ObjectTraversed(object).typeUnionOfListType(aListType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		return ObjectTraversed(object).typeUnionOfMapType(aMapType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		return ObjectTraversed(object).typeUnionOfObjectMeta(anObjectMeta);
	}

	@Override
	public AvailObject ObjectTypeUnionOfObjectMetaMeta (
			final AvailObject object,
			final AvailObject anObjectMetaMeta)
	{
		return ObjectTraversed(object).typeUnionOfObjectMetaMeta(anObjectMetaMeta);
	}

	@Override
	public AvailObject ObjectTypeUnionOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		return ObjectTraversed(object).typeUnionOfObjectType(anObjectType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		return ObjectTraversed(object).typeUnionOfSetType(aSetType);
	}

	@Override
	public AvailObject ObjectTypeUnionOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		return ObjectTraversed(object).typeUnionOfTupleType(aTupleType);
	}

	@Override
	public void ObjectUnclassified (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).unclassified(value);
	}

	@Override
	public AvailObject ObjectUnionOfTypesAtThrough (
			final AvailObject object,
			final int startIndex,
			final int endIndex)
	{
		return ObjectTraversed(object).unionOfTypesAtThrough(
			startIndex,
			endIndex);
	}

	@Override
	public int ObjectUntranslatedDataAt (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).untranslatedDataAt(index);
	}

	@Override
	public void ObjectUntranslatedDataAtPut (
			final AvailObject object,
			final int index,
			final int value)
	{
		ObjectTraversed(object).untranslatedDataAtPut(
			index,
			value);
	}

	@Override
	public void ObjectUpperBound (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).upperBound(value);
	}

	@Override
	public AvailObject ObjectValidateArgumentTypesInterpreterIfFail (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final AvailInterpreter anAvailInterpreter,
			final Continuation1<Generator<String>> failBlock)
	{
		return ObjectTraversed(object).validateArgumentTypesInterpreterIfFail(
			argTypes,
			anAvailInterpreter,
			failBlock);
	}

	@Override
	public void ObjectValidity (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).validity(value);
	}

	@Override
	public void ObjectValue (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).value(value);
	}

	@Override
	public AvailObject ObjectValueAtIndex (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).valueAtIndex(index);
	}

	@Override
	public void ObjectValueAtIndexPut (
			final AvailObject object,
			final int index,
			final AvailObject valueObject)
	{
		ObjectTraversed(object).valueAtIndexPut(
			index,
			valueObject);
	}

	@Override
	public void ObjectValueType (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).valueType(value);
	}

	@Override
	public void ObjectVariableBindings (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).variableBindings(value);
	}

	@Override
	public void ObjectVectors (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).vectors(value);
	}

	@Override
	public void ObjectVisibleNames (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).visibleNames(value);
	}

	@Override
	public void ObjectWhichOne (
			final AvailObject object,
			final int value)
	{
		ObjectTraversed(object).whichOne(value);
	}

	@Override
	public void ObjectWordcodes (
			final AvailObject object,
			final AvailObject value)
	{
		ObjectTraversed(object).wordcodes(value);
	}

	@Override
	public int ObjectZoneForIndex (
			final AvailObject object,
			final int index)
	{
		return ObjectTraversed(object).zoneForIndex(index);
	}

	@Override
	public String ObjectAsNativeString (
			final AvailObject object)
	{
		return ObjectTraversed(object).asNativeString();
	}

	@Override
	public AvailObject ObjectAsObject (
			final AvailObject object)
	{
		return ObjectTraversed(object).asObject();
	}

	@Override
	public AvailObject ObjectAsSet (
			final AvailObject object)
	{
		return ObjectTraversed(object).asSet();
	}

	@Override
	public AvailObject ObjectAsTuple (
			final AvailObject object)
	{
		return ObjectTraversed(object).asTuple();
	}

	@Override
	public AvailObject ObjectBecomeExactType (
			final AvailObject object)
	{
		return ObjectTraversed(object).becomeExactType();
	}

	@Override
	public void ObjectBecomeRealTupleType (
			final AvailObject object)
	{
		ObjectTraversed(object).becomeRealTupleType();
	}

	@Override
	public int ObjectBinHash (
			final AvailObject object)
	{
		return ObjectTraversed(object).binHash();
	}

	@Override
	public int ObjectBinSize (
			final AvailObject object)
	{
		return ObjectTraversed(object).binSize();
	}

	@Override
	public AvailObject ObjectBinUnionType (
			final AvailObject object)
	{
		return ObjectTraversed(object).binUnionType();
	}

	@Override
	public int ObjectBitsPerEntry (
			final AvailObject object)
	{
		return ObjectTraversed(object).bitsPerEntry();
	}

	@Override
	public int ObjectBitVector (
			final AvailObject object)
	{
		return ObjectTraversed(object).bitVector();
	}

	@Override
	public AvailObject ObjectBodyBlock (
			final AvailObject object)
	{
		return ObjectTraversed(object).bodyBlock();
	}

	@Override
	public AvailObject ObjectBodySignature (
			final AvailObject object)
	{
		return ObjectTraversed(object).bodySignature();
	}

	@Override
	public AvailObject ObjectBreakpointBlock (
			final AvailObject object)
	{
		return ObjectTraversed(object).breakpointBlock();
	}

	@Override
	public AvailObject ObjectCaller (
			final AvailObject object)
	{
		return ObjectTraversed(object).caller();
	}

	@Override
	public boolean ObjectCanComputeHashOfType (
			final AvailObject object)
	{
		return ObjectTraversed(object).canComputeHashOfType();
	}

	@Override
	public int ObjectCapacity (
			final AvailObject object)
	{
		return ObjectTraversed(object).capacity();
	}

	@Override
	public void ObjectCleanUpAfterCompile (
			final AvailObject object)
	{
		ObjectTraversed(object).cleanUpAfterCompile();
	}

	@Override
	public void ObjectClearModule (
			final AvailObject object)
	{
		ObjectTraversed(object).clearModule();
	}

	@Override
	public void ObjectClearValue (
			final AvailObject object)
	{
		ObjectTraversed(object).clearValue();
	}

	@Override
	public AvailObject ObjectClosure (
			final AvailObject object)
	{
		return ObjectTraversed(object).closure();
	}

	@Override
	public AvailObject ObjectClosureType (
			final AvailObject object)
	{
		return ObjectTraversed(object).closureType();
	}

	@Override
	public AvailObject ObjectCode (
			final AvailObject object)
	{
		return ObjectTraversed(object).code();
	}

	@Override
	public int ObjectCodePoint (
			final AvailObject object)
	{
		return ObjectTraversed(object).codePoint();
	}

	@Override
	public AvailObject ObjectComplete (
			final AvailObject object)
	{
		return ObjectTraversed(object).complete();
	}

	@Override
	public AvailObject ObjectConstantBindings (
			final AvailObject object)
	{
		return ObjectTraversed(object).constantBindings();
	}

	@Override
	public AvailObject ObjectContentType (
			final AvailObject object)
	{
		return ObjectTraversed(object).contentType();
	}

	@Override
	public AvailObject ObjectContingentImpSets (
			final AvailObject object)
	{
		return ObjectTraversed(object).contingentImpSets();
	}

	@Override
	public AvailObject ObjectContinuation (
			final AvailObject object)
	{
		return ObjectTraversed(object).continuation();
	}

	@Override
	public AvailObject ObjectCopyAsMutableContinuation (
			final AvailObject object)
	{
		return ObjectTraversed(object).copyAsMutableContinuation();
	}

	@Override
	public AvailObject ObjectCopyAsMutableObjectTuple (
			final AvailObject object)
	{
		return ObjectTraversed(object).copyAsMutableObjectTuple();
	}

	@Override
	public AvailObject ObjectCopyAsMutableSpliceTuple (
			final AvailObject object)
	{
		return ObjectTraversed(object).copyAsMutableSpliceTuple();
	}

	@Override
	public AvailObject ObjectCopyMutable (
			final AvailObject object)
	{
		return ObjectTraversed(object).copyMutable();
	}

	@Override
	public AvailObject ObjectDefaultType (
			final AvailObject object)
	{
		return ObjectTraversed(object).defaultType();
	}

	@Override
	public AvailObject ObjectDependentChunks (
			final AvailObject object)
	{
		return ObjectTraversed(object).dependentChunks();
	}

	@Override
	public int ObjectDepth (
			final AvailObject object)
	{
		return ObjectTraversed(object).depth();
	}

	@Override
	public void ObjectDisplayTestingTree (
			final AvailObject object)
	{
		ObjectTraversed(object).displayTestingTree();
	}

	@Override
	public void ObjectEnsureMetacovariant (
			final AvailObject object)
	{
		ObjectTraversed(object).ensureMetacovariant();
	}

	@Override
	public AvailObject ObjectEnsureMutable (
			final AvailObject object)
	{
		return ObjectTraversed(object).ensureMutable();
	}

	@Override
	public boolean ObjectEqualsBlank (
			final AvailObject object)
	{
		return ObjectTraversed(object).equalsBlank();
	}

	@Override
	public boolean ObjectEqualsFalse (
			final AvailObject object)
	{
		return ObjectTraversed(object).equalsFalse();
	}

	@Override
	public boolean ObjectEqualsTrue (
			final AvailObject object)
	{
		return ObjectTraversed(object).equalsTrue();
	}

	@Override
	public boolean ObjectEqualsVoid (
			final AvailObject object)
	{
		return ObjectTraversed(object).equalsVoid();
	}

	@Override
	public boolean ObjectEqualsVoidOrBlank (
			final AvailObject object)
	{
		return ObjectTraversed(object).equalsVoidOrBlank();
	}

	@Override
	public void ObjectEvictedByGarbageCollector (
			final AvailObject object)
	{
		ObjectTraversed(object).evictedByGarbageCollector();
	}

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		return ObjectTraversed(object).exactType();
	}

	@Override
	public int ObjectExecutionMode (
			final AvailObject object)
	{
		return ObjectTraversed(object).executionMode();
	}

	@Override
	public int ObjectExecutionState (
			final AvailObject object)
	{
		return ObjectTraversed(object).executionState();
	}

	@Override
	public AvailObject ObjectExpand (
			final AvailObject object)
	{
		return ObjectTraversed(object).expand();
	}

	@Override
	public boolean ObjectExtractBoolean (
			final AvailObject object)
	{
		return ObjectTraversed(object).extractBoolean();
	}

	@Override
	public short ObjectExtractByte (
			final AvailObject object)
	{
		return ObjectTraversed(object).extractByte();
	}

	@Override
	public double ObjectExtractDouble (
			final AvailObject object)
	{
		return ObjectTraversed(object).extractDouble();
	}

	@Override
	public float ObjectExtractFloat (
			final AvailObject object)
	{
		return ObjectTraversed(object).extractFloat();
	}

	@Override
	public int ObjectExtractInt (
			final AvailObject object)
	{
		return ObjectTraversed(object).extractInt();
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public long ObjectExtractLong (final @NotNull AvailObject object)
	{
		return ObjectTraversed(object).extractLong();
	}

	@Override
	public byte ObjectExtractNybble (
			final AvailObject object)
	{
		return ObjectTraversed(object).extractNybble();
	}

	@Override
	public AvailObject ObjectFieldMap (
			final AvailObject object)
	{
		return ObjectTraversed(object).fieldMap();
	}

	@Override
	public AvailObject ObjectFieldTypeMap (
			final AvailObject object)
	{
		return ObjectTraversed(object).fieldTypeMap();
	}

	@Override
	public AvailObject ObjectFilteredBundleTree (
			final AvailObject object)
	{
		return ObjectTraversed(object).filteredBundleTree();
	}

	@Override
	public AvailObject ObjectFirstTupleType (
			final AvailObject object)
	{
		return ObjectTraversed(object).firstTupleType();
	}

	@Override
	public int ObjectGetInteger (
			final AvailObject object)
	{
		return ObjectTraversed(object).getInteger();
	}

	@Override
	public AvailObject ObjectGetValue (
			final AvailObject object)
	{
		return ObjectTraversed(object).getValue();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		return ObjectTraversed(object).hash();
	}

	@Override
	public int ObjectHashOfType (
			final AvailObject object)
	{
		return ObjectTraversed(object).hashOfType();
	}

	@Override
	public int ObjectHashOrZero (
			final AvailObject object)
	{
		return ObjectTraversed(object).hashOrZero();
	}

	@Override
	public boolean ObjectHasRestrictions (
			final AvailObject object)
	{
		return ObjectTraversed(object).hasRestrictions();
	}

	@Override
	public int ObjectHiLevelTwoChunkLowOffset (
			final AvailObject object)
	{
		return ObjectTraversed(object).hiLevelTwoChunkLowOffset();
	}

	@Override
	public int ObjectHiNumLocalsLowNumArgs (
			final AvailObject object)
	{
		return ObjectTraversed(object).hiNumLocalsLowNumArgs();
	}

	@Override
	public int ObjectHiPrimitiveLowNumArgsAndLocalsAndStack (
			final AvailObject object)
	{
		return ObjectTraversed(object).hiPrimitiveLowNumArgsAndLocalsAndStack();
	}

	@Override
	public int ObjectHiStartingChunkIndexLowNumOuters (
			final AvailObject object)
	{
		return ObjectTraversed(object).hiStartingChunkIndexLowNumOuters();
	}

	@Override
	public AvailObject ObjectImplementationsTuple (
			final AvailObject object)
	{
		return ObjectTraversed(object).implementationsTuple();
	}

	@Override
	public int ObjectInclusiveFlags (
			final AvailObject object)
	{
		return ObjectTraversed(object).inclusiveFlags();
	}

	@Override
	public AvailObject ObjectIncomplete (
			final AvailObject object)
	{
		return ObjectTraversed(object).incomplete();
	}

	@Override
	public int ObjectIndex (
			final AvailObject object)
	{
		return ObjectTraversed(object).index();
	}

	@Override
	public AvailObject ObjectInnerType (
			final AvailObject object)
	{
		return ObjectTraversed(object).innerType();
	}

	@Override
	public AvailObject ObjectInstance (
			final AvailObject object)
	{
		return ObjectTraversed(object).instance();
	}

	@Override
	public int ObjectInternalHash (
			final AvailObject object)
	{
		return ObjectTraversed(object).internalHash();
	}

	@Override
	public int ObjectInterruptRequestFlag (
			final AvailObject object)
	{
		return ObjectTraversed(object).interruptRequestFlag();
	}

	@Override
	public int ObjectInvocationCount (
			final AvailObject object)
	{
		return ObjectTraversed(object).invocationCount();
	}

	@Override
	public boolean ObjectIsAbstract (
			final AvailObject object)
	{
		return ObjectTraversed(object).isAbstract();
	}

	@Override
	public boolean ObjectIsBoolean (
			final AvailObject object)
	{
		return ObjectTraversed(object).isBoolean();
	}

	@Override
	public boolean ObjectIsByte (
			final AvailObject object)
	{
		return ObjectTraversed(object).isByte();
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public boolean ObjectIsByteTuple (final @NotNull AvailObject object)
	{
		return ObjectTraversed(object).isByteTuple();
	}

	@Override
	public boolean ObjectIsCharacter (
			final AvailObject object)
	{
		return ObjectTraversed(object).isCharacter();
	}

	@Override
	public boolean ObjectIsClosure (
			final AvailObject object)
	{
		return ObjectTraversed(object).isClosure();
	}

	@Override
	public boolean ObjectIsCyclicType (
			final AvailObject object)
	{
		return ObjectTraversed(object).isCyclicType();
	}

	@Override
	public boolean ObjectIsExtendedInteger (
			final AvailObject object)
	{
		return ObjectTraversed(object).isExtendedInteger();
	}

	@Override
	public boolean ObjectIsFinite (
			final AvailObject object)
	{
		return ObjectTraversed(object).isFinite();
	}

	@Override
	public boolean ObjectIsForward (
			final AvailObject object)
	{
		return ObjectTraversed(object).isForward();
	}

	@Override
	public boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		return ObjectTraversed(object).isHashAvailable();
	}

	@Override
	public boolean ObjectIsImplementation (
			final AvailObject object)
	{
		return ObjectTraversed(object).isImplementation();
	}

	@Override
	public boolean ObjectIsIntegerRangeType (
			final AvailObject object)
	{
		return ObjectTraversed(object).isIntegerRangeType();
	}

	@Override
	public boolean ObjectIsList (
			final AvailObject object)
	{
		return ObjectTraversed(object).isList();
	}

	@Override
	public boolean ObjectIsListType (
			final AvailObject object)
	{
		return ObjectTraversed(object).isListType();
	}

	@Override
	public boolean ObjectIsMap (
			final AvailObject object)
	{
		return ObjectTraversed(object).isMap();
	}

	@Override
	public boolean ObjectIsMapType (
			final AvailObject object)
	{
		return ObjectTraversed(object).isMapType();
	}

	@Override
	public boolean ObjectIsNybble (
			final AvailObject object)
	{
		return ObjectTraversed(object).isNybble();
	}

	@Override
	public boolean ObjectIsPositive (
			final AvailObject object)
	{
		return ObjectTraversed(object).isPositive();
	}

	@Override
	public boolean ObjectIsSaved (
			final AvailObject object)
	{
		return ObjectTraversed(object).isSaved();
	}

	@Override
	public boolean ObjectIsSet (
			final AvailObject object)
	{
		return ObjectTraversed(object).isSet();
	}

	@Override
	public boolean ObjectIsSetType (
			final AvailObject object)
	{
		return ObjectTraversed(object).isSetType();
	}

	@Override
	public boolean ObjectIsSplice (
			final AvailObject object)
	{
		return ObjectTraversed(object).isSplice();
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public boolean ObjectIsString (final @NotNull AvailObject object)
	{
		return ObjectTraversed(object).isString();
	}

	@Override
	public boolean ObjectIsSupertypeOfTerminates (
			final AvailObject object)
	{
		return ObjectTraversed(object).isSupertypeOfTerminates();
	}

	@Override
	public boolean ObjectIsSupertypeOfVoid (
			final AvailObject object)
	{
		return ObjectTraversed(object).isSupertypeOfVoid();
	}

	@Override
	public boolean ObjectIsTuple (
			final AvailObject object)
	{
		return ObjectTraversed(object).isTuple();
	}

	@Override
	public boolean ObjectIsTupleType (
			final AvailObject object)
	{
		return ObjectTraversed(object).isTupleType();
	}

	@Override
	public boolean ObjectIsType (
			final AvailObject object)
	{
		return ObjectTraversed(object).isType();
	}

	@Override
	public boolean ObjectIsValid (
			final AvailObject object)
	{
		return ObjectTraversed(object).isValid();
	}

	@Override
	public List<AvailObject> ObjectKeysAsArray (
			final AvailObject object)
	{
		return ObjectTraversed(object).keysAsArray();
	}

	@Override
	public AvailObject ObjectKeysAsSet (
			final AvailObject object)
	{
		return ObjectTraversed(object).keysAsSet();
	}

	@Override
	public AvailObject ObjectKeyType (
			final AvailObject object)
	{
		return ObjectTraversed(object).keyType();
	}

	@Override
	public int ObjectLevelTwoChunkIndex (
			final AvailObject object)
	{
		return ObjectTraversed(object).levelTwoChunkIndex();
	}

	@Override
	public int ObjectLevelTwoOffset (
			final AvailObject object)
	{
		return ObjectTraversed(object).levelTwoOffset();
	}

	@Override
	public AvailObject ObjectLowerBound (
			final AvailObject object)
	{
		return ObjectTraversed(object).lowerBound();
	}

	@Override
	public boolean ObjectLowerInclusive (
			final AvailObject object)
	{
		return ObjectTraversed(object).lowerInclusive();
	}

	@Override
	public void ObjectMakeSubobjectsImmutable (
			final AvailObject object)
	{
		ObjectTraversed(object).makeSubobjectsImmutable();
	}

	@Override
	public int ObjectMapSize (
			final AvailObject object)
	{
		return ObjectTraversed(object).mapSize();
	}

	@Override
	public short ObjectMaxStackDepth (
			final AvailObject object)
	{
		return ObjectTraversed(object).maxStackDepth();
	}

	@Override
	public AvailObject ObjectMessage (
			final AvailObject object)
	{
		return ObjectTraversed(object).message();
	}

	@Override
	public AvailObject ObjectMessageParts (
			final AvailObject object)
	{
		return ObjectTraversed(object).messageParts();
	}

	@Override
	public AvailObject ObjectMethods (
			final AvailObject object)
	{
		return ObjectTraversed(object).methods();
	}

	@Override
	public void ObjectMoveToHead (
			final AvailObject object)
	{
		ObjectTraversed(object).moveToHead();
	}

	@Override
	public AvailObject ObjectMyObjectMeta (
			final AvailObject object)
	{
		return ObjectTraversed(object).myObjectMeta();
	}

	@Override
	public AvailObject ObjectMyObjectType (
			final AvailObject object)
	{
		return ObjectTraversed(object).myObjectType();
	}

	@Override
	public AvailObject ObjectMyRestrictions (
			final AvailObject object)
	{
		return ObjectTraversed(object).myRestrictions();
	}

	@Override
	public AvailObject ObjectMyType (
			final AvailObject object)
	{
		return ObjectTraversed(object).myType();
	}

	@Override
	public AvailObject ObjectName (
			final AvailObject object)
	{
		return ObjectTraversed(object).name();
	}

	@Override
	public AvailObject ObjectNames (
			final AvailObject object)
	{
		return ObjectTraversed(object).names();
	}

	@Override
	public AvailObject ObjectNewNames (
			final AvailObject object)
	{
		return ObjectTraversed(object).newNames();
	}

	@Override
	public AvailObject ObjectNext (
			final AvailObject object)
	{
		return ObjectTraversed(object).next();
	}

	@Override
	public int ObjectNextIndex (
			final AvailObject object)
	{
		return ObjectTraversed(object).nextIndex();
	}

	@Override
	public short ObjectNumArgs (
			final AvailObject object)
	{
		return ObjectTraversed(object).numArgs();
	}

	@Override
	public short ObjectNumArgsAndLocalsAndStack (
			final AvailObject object)
	{
		return ObjectTraversed(object).numArgsAndLocalsAndStack();
	}

	@Override
	public int ObjectNumberOfZones (
			final AvailObject object)
	{
		return ObjectTraversed(object).numberOfZones();
	}

	@Override
	public int ObjectNumBlanks (
			final AvailObject object)
	{
		return ObjectTraversed(object).numBlanks();
	}

	@Override
	public int ObjectNumDoubles (
			final AvailObject object)
	{
		return ObjectTraversed(object).numDoubles();
	}

	@Override
	public int ObjectNumIntegers (
			final AvailObject object)
	{
		return ObjectTraversed(object).numIntegers();
	}

	@Override
	public short ObjectNumLiterals (
			final AvailObject object)
	{
		return ObjectTraversed(object).numLiterals();
	}

	@Override
	public short ObjectNumLocals (
			final AvailObject object)
	{
		return ObjectTraversed(object).numLocals();
	}

	@Override
	public int ObjectNumLocalsOrArgsOrStack (
			final AvailObject object)
	{
		return ObjectTraversed(object).numLocalsOrArgsOrStack();
	}

	@Override
	public int ObjectNumObjects (
			final AvailObject object)
	{
		return ObjectTraversed(object).numObjects();
	}

	@Override
	public short ObjectNumOuters (
			final AvailObject object)
	{
		return ObjectTraversed(object).numOuters();
	}

	@Override
	public int ObjectNumOuterVars (
			final AvailObject object)
	{
		return ObjectTraversed(object).numOuterVars();
	}

	@Override
	public AvailObject ObjectNybbles (
			final AvailObject object)
	{
		return ObjectTraversed(object).nybbles();
	}

	@Override
	public AvailObject ObjectPad1 (
			final AvailObject object)
	{
		return ObjectTraversed(object).pad1();
	}

	@Override
	public AvailObject ObjectPad2 (
			final AvailObject object)
	{
		return ObjectTraversed(object).pad2();
	}

	@Override
	public AvailObject ObjectParent (
			final AvailObject object)
	{
		return ObjectTraversed(object).parent();
	}

	@Override
	public int ObjectPc (
			final AvailObject object)
	{
		return ObjectTraversed(object).pc();
	}

	@Override
	public void ObjectPostFault (
			final AvailObject object)
	{
		ObjectTraversed(object).postFault();
	}

	@Override
	public AvailObject ObjectPrevious (
			final AvailObject object)
	{
		return ObjectTraversed(object).previous();
	}

	@Override
	public int ObjectPreviousIndex (
			final AvailObject object)
	{
		return ObjectTraversed(object).previousIndex();
	}

	@Override
	public short ObjectPrimitiveNumber (
			final AvailObject object)
	{
		return ObjectTraversed(object).primitiveNumber();
	}

	@Override
	public int ObjectPriority (
			final AvailObject object)
	{
		return ObjectTraversed(object).priority();
	}

	@Override
	public AvailObject ObjectPrivateNames (
			final AvailObject object)
	{
		return ObjectTraversed(object).privateNames();
	}

	@Override
	public AvailObject ObjectPrivateTestingTree (
			final AvailObject object)
	{
		return ObjectTraversed(object).privateTestingTree();
	}

	@Override
	public AvailObject ObjectProcessGlobals (
			final AvailObject object)
	{
		return ObjectTraversed(object).processGlobals();
	}

	@Override
	public int ObjectRawQuad1 (
			final AvailObject object)
	{
		return ObjectTraversed(object).rawQuad1();
	}

	@Override
	public int ObjectRawQuad2 (
			final AvailObject object)
	{
		return ObjectTraversed(object).rawQuad2();
	}

	@Override
	public void ObjectReadBarrierFault (
			final AvailObject object)
	{
		ObjectTraversed(object).readBarrierFault();
	}

	@Override
	public void ObjectReleaseVariableOrMakeContentsImmutable (
			final AvailObject object)
	{
		ObjectTraversed(object).releaseVariableOrMakeContentsImmutable();
	}

	@Override
	public void ObjectRemoveFromQueue (
			final AvailObject object)
	{
		ObjectTraversed(object).removeFromQueue();
	}

	@Override
	public void ObjectRemoveRestrictions (
		final AvailObject object)
	{
		ObjectTraversed(object).removeRestrictions();
	}

	@Override
	public AvailObject ObjectRequiresBlock (
			final AvailObject object)
	{
		return ObjectTraversed(object).requiresBlock();
	}

	@Override
	public AvailObject ObjectRestrictions (
			final AvailObject object)
	{
		return ObjectTraversed(object).restrictions();
	}

	@Override
	public AvailObject ObjectReturnsBlock (
			final AvailObject object)
	{
		return ObjectTraversed(object).returnsBlock();
	}

	@Override
	public AvailObject ObjectReturnType (
			final AvailObject object)
	{
		return ObjectTraversed(object).returnType();
	}

	@Override
	public AvailObject ObjectRootBin (
			final AvailObject object)
	{
		return ObjectTraversed(object).rootBin();
	}

	@Override
	public AvailObject ObjectSecondTupleType (
			final AvailObject object)
	{
		return ObjectTraversed(object).secondTupleType();
	}

	@Override
	public int ObjectSetSize (
			final AvailObject object)
	{
		return ObjectTraversed(object).setSize();
	}

	@Override
	public AvailObject ObjectSignature (
			final AvailObject object)
	{
		return ObjectTraversed(object).signature();
	}

	@Override
	public AvailObject ObjectSizeRange (
			final AvailObject object)
	{
		return ObjectTraversed(object).sizeRange();
	}

	@Override
	public AvailObject ObjectSpecialActions (
			final AvailObject object)
	{
		return ObjectTraversed(object).specialActions();
	}

	@Override
	public int ObjectStackp (
			final AvailObject object)
	{
		return ObjectTraversed(object).stackp();
	}

	@Override
	public int ObjectStartingChunkIndex (
			final AvailObject object)
	{
		return ObjectTraversed(object).startingChunkIndex();
	}

	@Override
	public void ObjectStep (
			final AvailObject object)
	{
		ObjectTraversed(object).step();
	}

	@Override
	public AvailObject ObjectTestingTree (
			final AvailObject object)
	{
		return ObjectTraversed(object).testingTree();
	}

	@Override
	public void ObjectTrimExcessLongs (
			final AvailObject object)
	{
		ObjectTraversed(object).trimExcessLongs();
	}

	@Override
	public AvailObject ObjectTuple (
			final AvailObject object)
	{
		return ObjectTraversed(object).tuple();
	}

	@Override
	public int ObjectTupleSize (
			final AvailObject object)
	{
		return ObjectTraversed(object).tupleSize();
	}

	@Override
	public AvailObject ObjectTupleType (
			final AvailObject object)
	{
		return ObjectTraversed(object).tupleType();
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		return ObjectTraversed(object).type();
	}

	@Override
	public AvailObject ObjectTypeTuple (
			final AvailObject object)
	{
		return ObjectTraversed(object).typeTuple();
	}

	@Override
	public AvailObject ObjectUnclassified (
			final AvailObject object)
	{
		return ObjectTraversed(object).unclassified();
	}

	@Override
	public AvailObject ObjectUpperBound (
			final AvailObject object)
	{
		return ObjectTraversed(object).upperBound();
	}

	@Override
	public boolean ObjectUpperInclusive (
			final AvailObject object)
	{
		return ObjectTraversed(object).upperInclusive();
	}

	@Override
	public int ObjectValidity (
			final AvailObject object)
	{
		return ObjectTraversed(object).validity();
	}

	@Override
	public AvailObject ObjectValue (
			final AvailObject object)
	{
		return ObjectTraversed(object).value();
	}

	@Override
	public AvailObject ObjectValuesAsTuple (
			final AvailObject object)
	{
		return ObjectTraversed(object).valuesAsTuple();
	}

	@Override
	public AvailObject ObjectValueType (
			final AvailObject object)
	{
		return ObjectTraversed(object).valueType();
	}

	@Override
	public AvailObject ObjectVariableBindings (
			final AvailObject object)
	{
		return ObjectTraversed(object).variableBindings();
	}

	@Override
	public AvailObject ObjectVectors (
			final AvailObject object)
	{
		return ObjectTraversed(object).vectors();
	}

	@Override
	public void ObjectVerify (
			final AvailObject object)
	{
		ObjectTraversed(object).verify();
	}

	@Override
	public AvailObject ObjectVisibleNames (
			final AvailObject object)
	{
		return ObjectTraversed(object).visibleNames();
	}

	@Override
	public int ObjectWhichOne (
			final AvailObject object)
	{
		return ObjectTraversed(object).whichOne();
	}

	@Override
	public AvailObject ObjectWordcodes (
			final AvailObject object)
	{
		return ObjectTraversed(object).wordcodes();
	}



	@Override
	public boolean allowsImmutableToMutableReferenceInField (
			final Enum<?> e)
	{
		if (e == ObjectSlots.TARGET)
		{
			return true;
		}
		return false;
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
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

	@Override
	public void ObjectScanSubobjects (
			final AvailObject object,
			final AvailSubobjectVisitor visitor)
	{
		//  Manually constructed scanning method.

		visitor.invoke(object, object.target());
	}

	@Override
	public AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.  If I was mutable I have to make my
		//  target immutable as well (recursively down to immutable descendants).

		if (isMutable)
		{
			object.descriptor(IndirectionDescriptor.immutableDescriptor());
			object.target().makeImmutable();
		}
		return object;
	}

	// operations-indirections

	@Override
	public AvailObject ObjectTraversed (
			final AvailObject object)
	{
		//  Answer a non-indirection pointed to (transitively) by object.

		final AvailObject finalObject = object.target().traversed();
		//  Shorten the path to one step to reduce amortized traversal costs to approximately inv_Ackermann(N).
		object.target(finalObject);
		return finalObject;
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public Iterator<AvailObject> ObjectIterator (final @NotNull AvailObject object)
	{
		return ObjectTraversed(object).iterator();
	}

	/**
	 * Construct a new {@link IndirectionDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected IndirectionDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link IndirectionDescriptor}.
	 */
	private final static IndirectionDescriptor mutableDescriptor = new IndirectionDescriptor(true);

	/**
	 * Answer the mutable {@link IndirectionDescriptor}.
	 *
	 * @return The mutable {@link IndirectionDescriptor}.
	 */
	public static IndirectionDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link IndirectionDescriptor}.
	 */
	private final static IndirectionDescriptor immutableDescriptor = new IndirectionDescriptor(false);

	/**
	 * Answer the immutable {@link IndirectionDescriptor}.
	 *
	 * @return The immutable {@link IndirectionDescriptor}.
	 */
	public static IndirectionDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}

}
