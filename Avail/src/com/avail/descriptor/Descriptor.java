/**
 * descriptor/Descriptor.java
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
import com.avail.annotations.ThreadSafe;
import com.avail.compiler.Continuation1;
import com.avail.compiler.Generator;
import com.avail.descriptor.AbstractSignatureDescriptor;
import com.avail.descriptor.ApproximateTypeDescriptor;
import com.avail.descriptor.AvailModuleDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.BlankDescriptor;
import com.avail.descriptor.ByteStringDescriptor;
import com.avail.descriptor.ByteTupleDescriptor;
import com.avail.descriptor.CharacterDescriptor;
import com.avail.descriptor.ClosureDescriptor;
import com.avail.descriptor.ClosureTypeDescriptor;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.descriptor.ConcatenatedTupleTypeDescriptor;
import com.avail.descriptor.ContainerDescriptor;
import com.avail.descriptor.ContainerTypeDescriptor;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.descriptor.ContinuationTypeDescriptor;
import com.avail.descriptor.CyclicTypeDescriptor;
import com.avail.descriptor.Descriptor;
import com.avail.descriptor.DoubleDescriptor;
import com.avail.descriptor.ExpandedMessageBundleTreeDescriptor;
import com.avail.descriptor.FalseDescriptor;
import com.avail.descriptor.FillerDescriptor;
import com.avail.descriptor.FloatDescriptor;
import com.avail.descriptor.ForwardSignatureDescriptor;
import com.avail.descriptor.GeneralizedClosureTypeDescriptor;
import com.avail.descriptor.HashedSetBinDescriptor;
import com.avail.descriptor.ImplementationSetDescriptor;
import com.avail.descriptor.IndirectionDescriptor;
import com.avail.descriptor.InfinityDescriptor;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.L2ChunkDescriptor;
import com.avail.descriptor.LinearSetBinDescriptor;
import com.avail.descriptor.ListDescriptor;
import com.avail.descriptor.ListTypeDescriptor;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.MapTypeDescriptor;
import com.avail.descriptor.MessageBundleDescriptor;
import com.avail.descriptor.MethodSignatureDescriptor;
import com.avail.descriptor.NybbleTupleDescriptor;
import com.avail.descriptor.ObjectDescriptor;
import com.avail.descriptor.ObjectMetaDescriptor;
import com.avail.descriptor.ObjectMetaMetaDescriptor;
import com.avail.descriptor.ObjectTupleDescriptor;
import com.avail.descriptor.ObjectTypeDescriptor;
import com.avail.descriptor.PrimitiveTypeDescriptor;
import com.avail.descriptor.ProcessDescriptor;
import com.avail.descriptor.SetDescriptor;
import com.avail.descriptor.SetTypeDescriptor;
import com.avail.descriptor.SpliceTupleDescriptor;
import com.avail.descriptor.TerminatesMetaDescriptor;
import com.avail.descriptor.TerminatesTypeDescriptor;
import com.avail.descriptor.TrueDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.descriptor.TwoByteStringDescriptor;
import com.avail.descriptor.UnexpandedMessageBundleTreeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import com.avail.descriptor.VoidTypeDescriptor;
import com.avail.interpreter.AvailInterpreter;
import com.avail.visitor.AvailBeImmutableSubobjectVisitor;
import com.avail.visitor.AvailSubobjectVisitor;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

public abstract class Descriptor
{
	short _myId;
	boolean _isMutable;
	int _numberOfFixedObjectSlots;
	int _numberOfFixedIntegerSlots;
	boolean _hasVariableObjectSlots;
	boolean _hasVariableIntegerSlots;


	// accessing

	Descriptor asReadBarrier ()
	{
		//  Answer a read barrier descriptor that remembers which descriptor the current one is.

		return allDescriptors[_myId + (numActualDescriptors>>1)];
	}

	short id ()
	{
		//  Answer my unique id, assigning one if necessary.

		return _myId;
	}

	void id (
			final short anInteger)
	{
		//  Set my unique id.

		/* Let Smalltalk deal with its own AllDescriptors array.  Java doesn't care */
		_myId = anInteger;
	}

	public boolean isMutable ()
	{
		return _isMutable;
	}

	void isMutable (
			final boolean aBoolean)
	{
		_isMutable = aBoolean;
	}



	// GENERATED pure methods

	boolean ObjectAcceptsArgTypesFromClosureType (
			final AvailObject object, 
			final AvailObject closureType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:acceptsArgTypesFromClosureType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectAcceptsArgumentsFromContinuationStackp (
			final AvailObject object, 
			final AvailObject continuation, 
			final int stackp)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:acceptsArgumentsFromContinuation:stackp: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectAcceptsArgumentTypesFromContinuationStackp (
			final AvailObject object, 
			final AvailObject continuation, 
			final int stackp)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:acceptsArgumentTypesFromContinuation:stackp: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectAcceptsArrayOfArgTypes (
			final AvailObject object, 
			final List<AvailObject> argTypes)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:acceptsArrayOfArgTypes: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectAcceptsArrayOfArgValues (
			final AvailObject object, 
			final List<AvailObject> argValues)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:acceptsArrayOfArgValues: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectAcceptsTupleOfArgTypes (
			final AvailObject object, 
			final AvailObject argTypes)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:acceptsTupleOfArgTypes: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectAcceptsTupleOfArguments (
			final AvailObject object, 
			final AvailObject arguments)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:acceptsTupleOfArguments: in Avail.Descriptor", object);
		return false;
	}

	void ObjectAddDependentChunkId (
			final AvailObject object, 
			final int aChunkIndex)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:addDependentChunkId: in Avail.Descriptor", object);
		return;
	}

	void ObjectAddImplementation (
			final AvailObject object, 
			final AvailObject implementation)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:addImplementation: in Avail.Descriptor", object);
		return;
	}

	void ObjectAddRestrictions (
			final AvailObject object, 
			final AvailObject restrictions)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:addRestrictions: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectAddToInfinityCanDestroy (
			final AvailObject object, 
			final AvailObject anInfinity, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:addToInfinity:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectAddToIntegerCanDestroy (
			final AvailObject object, 
			final AvailObject anInteger, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:addToInteger:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectArgsLocalsStackOutersPrimitive (
			final AvailObject object, 
			final int args, 
			final int locals, 
			final int stack, 
			final int outers, 
			final int primitive)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:args:locals:stack:outers:primitive: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectArgTypeAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:argTypeAt: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectArgTypeAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:argTypeAt:put: in Avail.Descriptor", object);
		return;
	}

	void ObjectAtAddMessageRestrictions (
			final AvailObject object, 
			final AvailObject methodName, 
			final AvailObject illegalArgMsgs)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:at:addMessageRestrictions: in Avail.Descriptor", object);
		return;
	}

	void ObjectAtAddMethodImplementation (
			final AvailObject object, 
			final AvailObject methodName, 
			final AvailObject implementation)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:at:addMethodImplementation: in Avail.Descriptor", object);
		return;
	}

	void ObjectAtMessageAddBundle (
			final AvailObject object, 
			final AvailObject message, 
			final AvailObject bundle)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:atMessage:addBundle: in Avail.Descriptor", object);
		return;
	}

	void ObjectAtNameAdd (
			final AvailObject object, 
			final AvailObject stringName, 
			final AvailObject trueName)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:atName:add: in Avail.Descriptor", object);
		return;
	}

	void ObjectAtNewNamePut (
			final AvailObject object, 
			final AvailObject stringName, 
			final AvailObject trueName)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:atNewName:put: in Avail.Descriptor", object);
		return;
	}

	void ObjectAtPrivateNameAdd (
			final AvailObject object, 
			final AvailObject stringName, 
			final AvailObject trueName)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:atPrivateName:add: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectBinElementAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:binElementAt: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectBinElementAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:binElementAt:put: in Avail.Descriptor", object);
		return;
	}

	void ObjectBinHash (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:binHash: in Avail.Descriptor", object);
		return;
	}

	void ObjectBinSize (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:binSize: in Avail.Descriptor", object);
		return;
	}

	void ObjectBinUnionType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:binUnionType: in Avail.Descriptor", object);
		return;
	}

	void ObjectBitVector (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:bitVector: in Avail.Descriptor", object);
		return;
	}

	void ObjectBodyBlock (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:bodyBlock: in Avail.Descriptor", object);
		return;
	}

	void ObjectBodyBlockRequiresBlockReturnsBlock (
			final AvailObject object, 
			final AvailObject bb, 
			final AvailObject rqb, 
			final AvailObject rtb)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:bodyBlock:requiresBlock:returnsBlock: in Avail.Descriptor", object);
		return;
	}

	void ObjectBodySignature (
			final AvailObject object, 
			final AvailObject signature)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:bodySignature: in Avail.Descriptor", object);
		return;
	}

	void ObjectBodySignatureRequiresBlockReturnsBlock (
			final AvailObject object, 
			final AvailObject bs, 
			final AvailObject rqb, 
			final AvailObject rtb)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:bodySignature:requiresBlock:returnsBlock: in Avail.Descriptor", object);
		return;
	}

	void ObjectBreakpointBlock (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:breakpointBlock: in Avail.Descriptor", object);
		return;
	}

	void ObjectBuildFilteredBundleTreeFrom (
			final AvailObject object, 
			final AvailObject bundleTree)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:buildFilteredBundleTreeFrom: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectBundleAtMessageParts (
			final AvailObject object, 
			final AvailObject message, 
			final AvailObject parts)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:bundleAtMessage:parts: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectCaller (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:caller: in Avail.Descriptor", object);
		return;
	}

	void ObjectClosure (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:closure: in Avail.Descriptor", object);
		return;
	}

	void ObjectClosureType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:closureType: in Avail.Descriptor", object);
		return;
	}

	void ObjectCode (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:code: in Avail.Descriptor", object);
		return;
	}

	void ObjectCodePoint (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:codePoint: in Avail.Descriptor", object);
		return;
	}

	boolean ObjectCompareFromToWithStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject anotherObject, 
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:compareFrom:to:with:startingAt: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectCompareFromToWithAnyTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aTuple, 
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:compareFrom:to:withAnyTuple:startingAt: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectCompareFromToWithByteStringStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aByteString, 
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:compareFrom:to:withByteString:startingAt: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectCompareFromToWithByteTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aByteTuple, 
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:compareFrom:to:withByteTuple:startingAt: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectCompareFromToWithNybbleTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aNybbleTuple, 
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:compareFrom:to:withNybbleTuple:startingAt: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectCompareFromToWithObjectTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject anObjectTuple, 
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:compareFrom:to:withObjectTuple:startingAt: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectCompareFromToWithTwoByteStringStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject aTwoByteString, 
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:compareFrom:to:withTwoByteString:startingAt: in Avail.Descriptor", object);
		return false;
	}

	void ObjectComplete (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:complete: in Avail.Descriptor", object);
		return;
	}

	int ObjectComputeHashFromTo (
			final AvailObject object, 
			final int start, 
			final int end)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:computeHashFrom:to: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectComputeReturnTypeFromArgumentTypesInterpreter (
			final AvailObject object, 
			final List<AvailObject> argTypes, 
			final AvailInterpreter anAvailInterpreter)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:computeReturnTypeFromArgumentTypes:interpreter: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectConcatenateTuplesCanDestroy (
			final AvailObject object, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:concatenateTuplesCanDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectConstantBindings (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:constantBindings: in Avail.Descriptor", object);
		return;
	}

	void ObjectContentType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:contentType: in Avail.Descriptor", object);
		return;
	}

	void ObjectContingentImpSets (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:contingentImpSets: in Avail.Descriptor", object);
		return;
	}

	void ObjectContinuation (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:continuation: in Avail.Descriptor", object);
		return;
	}

	void ObjectCopyToRestrictedTo (
			final AvailObject object, 
			final AvailObject filteredBundleTree, 
			final AvailObject visibleNames)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:copyTo:restrictedTo: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectCopyTupleFromToCanDestroy (
			final AvailObject object, 
			final int start, 
			final int end, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:copyTupleFrom:to:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	boolean ObjectCouldEverBeInvokedWith (
			final AvailObject object, 
			final ArrayList<AvailObject> argTypes)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:couldEverBeInvokedWith: in Avail.Descriptor", object);
		return false;
	}

	AvailObject ObjectCreateTestingTreeWithPositiveMatchesRemainingPossibilities (
			final AvailObject object, 
			final AvailObject positiveTuple, 
			final AvailObject possibilities)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:createTestingTreeWithPositiveMatches:remainingPossibilities: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectDataAtIndex (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:dataAtIndex: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectDataAtIndexPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:dataAtIndex:put: in Avail.Descriptor", object);
		return;
	}

	void ObjectDefaultType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:defaultType: in Avail.Descriptor", object);
		return;
	}

	void ObjectDependentChunks (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:dependentChunks: in Avail.Descriptor", object);
		return;
	}

	void ObjectDepth (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:depth: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectDivideCanDestroy (
			final AvailObject object, 
			final AvailObject aNumber, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:divide:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectDivideIntoInfinityCanDestroy (
			final AvailObject object, 
			final AvailObject anInfinity, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:divideIntoInfinity:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectDivideIntoIntegerCanDestroy (
			final AvailObject object, 
			final AvailObject anInteger, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:divideIntoInteger:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectElementAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:elementAt: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectElementAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:elementAt:put: in Avail.Descriptor", object);
		return;
	}

	int ObjectEndOfZone (
			final AvailObject object, 
			final int zone)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:endOfZone: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectEndSubtupleIndexInZone (
			final AvailObject object, 
			final int zone)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:endSubtupleIndexInZone: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectExecutionMode (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:executionMode: in Avail.Descriptor", object);
		return;
	}

	void ObjectExecutionState (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:executionState: in Avail.Descriptor", object);
		return;
	}

	byte ObjectExtractNybbleFromTupleAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:extractNybbleFromTupleAt: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectFieldMap (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:fieldMap: in Avail.Descriptor", object);
		return;
	}

	void ObjectFieldTypeMap (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:fieldTypeMap: in Avail.Descriptor", object);
		return;
	}

	List<AvailObject> ObjectFilterByTypes (
			final AvailObject object, 
			final List<AvailObject> argTypes)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:filterByTypes: in Avail.Descriptor", object);
		return null;
	}

	void ObjectFilteredBundleTree (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:filteredBundleTree: in Avail.Descriptor", object);
		return;
	}

	void ObjectFirstTupleType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:firstTupleType: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectForZoneSetSubtupleStartSubtupleIndexEndOfZone (
			final AvailObject object, 
			final int zone, 
			final AvailObject newSubtuple, 
			final int startSubtupleIndex, 
			final int endOfZone)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:forZone:setSubtuple:startSubtupleIndex:endOfZone: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	boolean ObjectGreaterThanInteger (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:greaterThanInteger: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectGreaterThanSignedInfinity (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:greaterThanSignedInfinity: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectHasElement (
			final AvailObject object, 
			final AvailObject elementObject)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:hasElement: in Avail.Descriptor", object);
		return false;
	}

	void ObjectHash (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:hash: in Avail.Descriptor", object);
		return;
	}

	int ObjectHashFromTo (
			final AvailObject object, 
			final int startIndex, 
			final int endIndex)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:hashFrom:to: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectHashOrZero (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:hashOrZero: in Avail.Descriptor", object);
		return;
	}

	boolean ObjectHasKey (
			final AvailObject object, 
			final AvailObject keyObject)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:hasKey: in Avail.Descriptor", object);
		return false;
	}

	void ObjectHiLevelTwoChunkLowOffset (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:hiLevelTwoChunkLowOffset: in Avail.Descriptor", object);
		return;
	}

	void ObjectHiNumLocalsLowNumArgs (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:hiNumLocalsLowNumArgs: in Avail.Descriptor", object);
		return;
	}

	void ObjectHiPrimitiveLowNumArgsAndLocalsAndStack (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:hiPrimitiveLowNumArgsAndLocalsAndStack: in Avail.Descriptor", object);
		return;
	}

	void ObjectHiStartingChunkIndexLowNumOuters (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:hiStartingChunkIndexLowNumOuters: in Avail.Descriptor", object);
		return;
	}

	ArrayList<AvailObject> ObjectImplementationsAtOrBelow (
			final AvailObject object, 
			final ArrayList<AvailObject> argTypes)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:implementationsAtOrBelow: in Avail.Descriptor", object);
		return null;
	}

	void ObjectImplementationsTuple (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:implementationsTuple: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectIncludeBundleAtMessageParts (
			final AvailObject object, 
			final AvailObject message, 
			final AvailObject parts)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:includeBundleAtMessage:parts: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	boolean ObjectIncludes (
			final AvailObject object, 
			final AvailObject imp)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:includes: in Avail.Descriptor", object);
		return false;
	}

	void ObjectInclusiveFlags (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:inclusiveFlags: in Avail.Descriptor", object);
		return;
	}

	void ObjectIncomplete (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:incomplete: in Avail.Descriptor", object);
		return;
	}

	void ObjectIndex (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:index: in Avail.Descriptor", object);
		return;
	}

	void ObjectInnerType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:innerType: in Avail.Descriptor", object);
		return;
	}

	void ObjectInstance (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:instance: in Avail.Descriptor", object);
		return;
	}

	void ObjectInternalHash (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:internalHash: in Avail.Descriptor", object);
		return;
	}

	void ObjectInterruptRequestFlag (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:interruptRequestFlag: in Avail.Descriptor", object);
		return;
	}

	void ObjectInvocationCount (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:invocationCount: in Avail.Descriptor", object);
		return;
	}

	void ObjectIsSaved (
			final AvailObject object, 
			final boolean aBoolean)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSaved: in Avail.Descriptor", object);
		return;
	}

	boolean ObjectIsSubsetOf (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSubsetOf: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSubtypeOf: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfClosureType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfContainerType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfContinuationType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfCyclicType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfGeneralizedClosureType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfIntegerRangeType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfListType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfMapType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfObjectMeta: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfObjectMetaMeta: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfObjectType (
			final AvailObject object, 
			final AvailObject anObjectType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfObjectType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfPrimitiveType (
			final AvailObject object, 
			final AvailObject aPrimitiveType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfPrimitiveType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfSetType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isSupertypeOfTupleType: in Avail.Descriptor", object);
		return false;
	}

	void ObjectIsValid (
			final AvailObject object, 
			final boolean aBoolean)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isValid: in Avail.Descriptor", object);
		return;
	}

	boolean ObjectIsValidForArgumentTypesInterpreter (
			final AvailObject object, 
			final List<AvailObject> argTypes, 
			final AvailInterpreter interpreter)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:isValidForArgumentTypes:interpreter: in Avail.Descriptor", object);
		return false;
	}

	AvailObject ObjectKeyAtIndex (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:keyAtIndex: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectKeyAtIndexPut (
			final AvailObject object, 
			final int index, 
			final AvailObject keyObject)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:keyAtIndex:put: in Avail.Descriptor", object);
		return;
	}

	void ObjectKeyType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:keyType: in Avail.Descriptor", object);
		return;
	}

	boolean ObjectLessOrEqual (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:lessOrEqual: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectLessThan (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:lessThan: in Avail.Descriptor", object);
		return false;
	}

	void ObjectLevelTwoChunkIndexOffset (
			final AvailObject object, 
			final int index, 
			final int offset)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:levelTwoChunkIndex:offset: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectLiteralAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:literalAt: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectLiteralAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:literalAt:put: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectLocalOrArgOrStackAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:localOrArgOrStackAt: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectLocalOrArgOrStackAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:localOrArgOrStackAt:put: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectLocalTypeAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:localTypeAt: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectLookupByTypesFromArray (
			final AvailObject object, 
			final List<AvailObject> argumentTypeArray)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:lookupByTypesFromArray: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectLookupByTypesFromContinuationStackp (
			final AvailObject object, 
			final AvailObject continuation, 
			final int stackp)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:lookupByTypesFromContinuation:stackp: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectLookupByTypesFromTuple (
			final AvailObject object, 
			final AvailObject argumentTypeTuple)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:lookupByTypesFromTuple: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectLookupByValuesFromArray (
			final AvailObject object, 
			final List<AvailObject> argumentArray)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:lookupByValuesFromArray: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectLookupByValuesFromContinuationStackp (
			final AvailObject object, 
			final AvailObject continuation, 
			final int stackp)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:lookupByValuesFromContinuation:stackp: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectLookupByValuesFromTuple (
			final AvailObject object, 
			final AvailObject argumentTuple)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:lookupByValuesFromTuple: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectLowerBound (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:lowerBound: in Avail.Descriptor", object);
		return;
	}

	void ObjectLowerInclusiveUpperInclusive (
			final AvailObject object, 
			final boolean lowInc, 
			final boolean highInc)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:lowerInclusive:upperInclusive: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectMapAt (
			final AvailObject object, 
			final AvailObject keyObject)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:mapAt: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectMapAtPuttingCanDestroy (
			final AvailObject object, 
			final AvailObject keyObject, 
			final AvailObject newValueObject, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:mapAt:putting:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectMapSize (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:mapSize: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectMapWithoutKeyCanDestroy (
			final AvailObject object, 
			final AvailObject keyObject, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:mapWithoutKey:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectMessage (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:message: in Avail.Descriptor", object);
		return;
	}

	void ObjectMessageParts (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:messageParts: in Avail.Descriptor", object);
		return;
	}

	void ObjectMethods (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:methods: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectMinusCanDestroy (
			final AvailObject object, 
			final AvailObject aNumber, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:minus:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectMultiplyByInfinityCanDestroy (
			final AvailObject object, 
			final AvailObject anInfinity, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:multiplyByInfinity:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectMultiplyByIntegerCanDestroy (
			final AvailObject object, 
			final AvailObject anInteger, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:multiplyByInteger:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectMyObjectMeta (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:myObjectMeta: in Avail.Descriptor", object);
		return;
	}

	void ObjectMyObjectType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:myObjectType: in Avail.Descriptor", object);
		return;
	}

	void ObjectMyRestrictions (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:myRestrictions: in Avail.Descriptor", object);
		return;
	}

	void ObjectMyType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:myType: in Avail.Descriptor", object);
		return;
	}

	void ObjectName (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:name: in Avail.Descriptor", object);
		return;
	}

	void ObjectNames (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:names: in Avail.Descriptor", object);
		return;
	}

	boolean ObjectNameVisible (
			final AvailObject object, 
			final AvailObject trueName)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:nameVisible: in Avail.Descriptor", object);
		return false;
	}

	void ObjectNecessaryImplementationSetChanged (
			final AvailObject object, 
			final AvailObject anImplementationSet)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:necessaryImplementationSetChanged: in Avail.Descriptor", object);
		return;
	}

	void ObjectNewNames (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:newNames: in Avail.Descriptor", object);
		return;
	}

	void ObjectNext (
			final AvailObject object, 
			final AvailObject nextChunk)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:next: in Avail.Descriptor", object);
		return;
	}

	void ObjectNextIndex (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:nextIndex: in Avail.Descriptor", object);
		return;
	}

	void ObjectNumBlanks (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:numBlanks: in Avail.Descriptor", object);
		return;
	}

	void ObjectNumFloats (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:numFloats: in Avail.Descriptor", object);
		return;
	}

	void ObjectNumIntegers (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:numIntegers: in Avail.Descriptor", object);
		return;
	}

	void ObjectNumObjects (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:numObjects: in Avail.Descriptor", object);
		return;
	}

	void ObjectNybbles (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:nybbles: in Avail.Descriptor", object);
		return;
	}

	boolean ObjectOptionallyNilOuterVar (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:optionallyNilOuterVar: in Avail.Descriptor", object);
		return false;
	}

	AvailObject ObjectOuterTypeAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:outerTypeAt: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectOuterTypesLocalTypes (
			final AvailObject object, 
			final AvailObject tupleOfOuterTypes, 
			final AvailObject tupleOfLocalContainerTypes)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:outerTypes:localTypes: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectOuterVarAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:outerVarAt: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectOuterVarAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:outerVarAt:put: in Avail.Descriptor", object);
		return;
	}

	void ObjectPad (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:pad: in Avail.Descriptor", object);
		return;
	}

	void ObjectParent (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:parent: in Avail.Descriptor", object);
		return;
	}

	void ObjectPc (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:pc: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectPlusCanDestroy (
			final AvailObject object, 
			final AvailObject aNumber, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:plus:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectPrevious (
			final AvailObject object, 
			final AvailObject previousChunk)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:previous: in Avail.Descriptor", object);
		return;
	}

	void ObjectPreviousIndex (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:previousIndex: in Avail.Descriptor", object);
		return;
	}

	void ObjectPriority (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:priority: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectPrivateAddElement (
			final AvailObject object, 
			final AvailObject element)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:privateAddElement: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectPrivateExcludeElement (
			final AvailObject object, 
			final AvailObject element)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:privateExcludeElement: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectPrivateExcludeElementKnownIndex (
			final AvailObject object, 
			final AvailObject element, 
			final int knownIndex)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:privateExcludeElement:knownIndex: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectPrivateExcludeKey (
			final AvailObject object, 
			final AvailObject keyObject)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:privateExcludeKey: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectPrivateMapAtPut (
			final AvailObject object, 
			final AvailObject keyObject, 
			final AvailObject valueObject)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:privateMapAt:put: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectPrivateNames (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:privateNames: in Avail.Descriptor", object);
		return;
	}

	void ObjectPrivateTestingTree (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:privateTestingTree: in Avail.Descriptor", object);
		return;
	}

	void ObjectProcessGlobals (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:processGlobals: in Avail.Descriptor", object);
		return;
	}

	short ObjectRawByteAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawByteAt: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectRawByteAtPut (
			final AvailObject object, 
			final int index, 
			final short anInteger)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawByteAt:put: in Avail.Descriptor", object);
		return;
	}

	short ObjectRawByteForCharacterAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawByteForCharacterAt: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectRawByteForCharacterAtPut (
			final AvailObject object, 
			final int index, 
			final short anInteger)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawByteForCharacterAt:put: in Avail.Descriptor", object);
		return;
	}

	byte ObjectRawNybbleAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawNybbleAt: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectRawNybbleAtPut (
			final AvailObject object, 
			final int index, 
			final byte aNybble)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawNybbleAt:put: in Avail.Descriptor", object);
		return;
	}

	void ObjectRawQuad1 (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawQuad1: in Avail.Descriptor", object);
		return;
	}

	void ObjectRawQuad2 (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawQuad2: in Avail.Descriptor", object);
		return;
	}

	int ObjectRawQuadAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawQuadAt: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectRawQuadAtPut (
			final AvailObject object, 
			final int index, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawQuadAt:put: in Avail.Descriptor", object);
		return;
	}

	short ObjectRawShortForCharacterAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawShortForCharacterAt: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectRawShortForCharacterAtPut (
			final AvailObject object, 
			final int index, 
			final short anInteger)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawShortForCharacterAt:put: in Avail.Descriptor", object);
		return;
	}

	int ObjectRawSignedIntegerAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawSignedIntegerAt: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectRawSignedIntegerAtPut (
			final AvailObject object, 
			final int index, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawSignedIntegerAt:put: in Avail.Descriptor", object);
		return;
	}

	long ObjectRawUnsignedIntegerAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawUnsignedIntegerAt: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectRawUnsignedIntegerAtPut (
			final AvailObject object, 
			final int index, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rawUnsignedIntegerAt:put: in Avail.Descriptor", object);
		return;
	}

	void ObjectRemoveDependentChunkId (
			final AvailObject object, 
			final int aChunkIndex)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:removeDependentChunkId: in Avail.Descriptor", object);
		return;
	}

	void ObjectRemoveFrom (
			final AvailObject object, 
			final AvailInterpreter anInterpreter)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:removeFrom: in Avail.Descriptor", object);
		return;
	}

	void ObjectRemoveImplementation (
			final AvailObject object, 
			final AvailObject implementation)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:removeImplementation: in Avail.Descriptor", object);
		return;
	}

	boolean ObjectRemoveMessageParts (
			final AvailObject object, 
			final AvailObject message, 
			final AvailObject parts)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:removeMessage:parts: in Avail.Descriptor", object);
		return false;
	}

	void ObjectRemoveRestrictions (
			final AvailObject object, 
			final AvailObject obsoleteRestrictions)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:removeRestrictions: in Avail.Descriptor", object);
		return;
	}

	void ObjectRequiresBlock (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:requiresBlock: in Avail.Descriptor", object);
		return;
	}

	void ObjectResolvedForwardWithName (
			final AvailObject object, 
			final AvailObject forwardImplementation, 
			final AvailObject methodName)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:resolvedForward:withName: in Avail.Descriptor", object);
		return;
	}

	void ObjectRestrictions (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:restrictions: in Avail.Descriptor", object);
		return;
	}

	void ObjectReturnsBlock (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:returnsBlock: in Avail.Descriptor", object);
		return;
	}

	void ObjectReturnType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:returnType: in Avail.Descriptor", object);
		return;
	}

	void ObjectRootBin (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:rootBin: in Avail.Descriptor", object);
		return;
	}

	void ObjectSecondTupleType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:secondTupleType: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectSetIntersectionCanDestroy (
			final AvailObject object, 
			final AvailObject otherSet, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:setIntersection:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectSetMinusCanDestroy (
			final AvailObject object, 
			final AvailObject otherSet, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:setMinus:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectSetSize (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:setSize: in Avail.Descriptor", object);
		return;
	}

	void ObjectSetSubtupleForZoneTo (
			final AvailObject object, 
			final int zoneIndex, 
			final AvailObject newTuple)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:setSubtupleForZone:to: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectSetUnionCanDestroy (
			final AvailObject object, 
			final AvailObject otherSet, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:setUnion:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectSetValue (
			final AvailObject object, 
			final AvailObject newValue)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:setValue: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectSetWithElementCanDestroy (
			final AvailObject object, 
			final AvailObject newElementObject, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:setWithElement:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectSetWithoutElementCanDestroy (
			final AvailObject object, 
			final AvailObject elementObjectToExclude, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:setWithoutElement:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectSignature (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:signature: in Avail.Descriptor", object);
		return;
	}

	void ObjectSize (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:size: in Avail.Descriptor", object);
		return;
	}

	int ObjectSizeOfZone (
			final AvailObject object, 
			final int zone)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:sizeOfZone: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectSizeRange (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:sizeRange: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectStackAt (
			final AvailObject object, 
			final int slotIndex)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:stackAt: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectStackAtPut (
			final AvailObject object, 
			final int slotIndex, 
			final AvailObject anObject)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:stackAt:put: in Avail.Descriptor", object);
		return;
	}

	void ObjectStackp (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:stackp: in Avail.Descriptor", object);
		return;
	}

	void ObjectStartingChunkIndex (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:startingChunkIndex: in Avail.Descriptor", object);
		return;
	}

	int ObjectStartOfZone (
			final AvailObject object, 
			final int zone)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:startOfZone: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectStartSubtupleIndexInZone (
			final AvailObject object, 
			final int zone)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:startSubtupleIndexInZone: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectSubtractFromInfinityCanDestroy (
			final AvailObject object, 
			final AvailObject anInfinity, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:subtractFromInfinity:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectSubtractFromIntegerCanDestroy (
			final AvailObject object, 
			final AvailObject anInteger, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:subtractFromInteger:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectSubtupleForZone (
			final AvailObject object, 
			final int zone)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:subtupleForZone: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTimesCanDestroy (
			final AvailObject object, 
			final AvailObject aNumber, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:times:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectTranslateToZone (
			final AvailObject object, 
			final int tupleIndex, 
			final int zoneIndex)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:translate:toZone: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectTrueNamesForStringName (
			final AvailObject object, 
			final AvailObject stringName)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:trueNamesForStringName: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTruncateTo (
			final AvailObject object, 
			final int newTupleSize)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:truncateTo: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectTuple (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:tuple: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectTupleAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:tupleAt: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectTupleAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject aNybbleObject)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:tupleAt:put: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectTupleAtPuttingCanDestroy (
			final AvailObject object, 
			final int index, 
			final AvailObject newValueObject, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:tupleAt:putting:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectTupleIntAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:tupleIntAt: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectTupleType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:tupleType: in Avail.Descriptor", object);
		return;
	}

	void ObjectType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:type: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectTypeAtIndex (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeAtIndex: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersection (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersection: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfClosureType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfClosureTypeCanDestroy (
			final AvailObject object, 
			final AvailObject aClosureType, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfClosureType:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfContainerType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfContinuationType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfCyclicType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfGeneralizedClosureType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfGeneralizedClosureTypeCanDestroy (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfGeneralizedClosureType:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfIntegerRangeType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfListType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfMapType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfMeta (
			final AvailObject object, 
			final AvailObject someMeta)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfMeta: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfObjectMeta: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfObjectMetaMeta: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfObjectType (
			final AvailObject object, 
			final AvailObject anObjectType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfObjectType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfSetType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeIntersectionOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeIntersectionOfTupleType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectTypeTuple (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeTuple: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectTypeUnion (
			final AvailObject object, 
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnion: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfClosureType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfClosureTypeCanDestroy (
			final AvailObject object, 
			final AvailObject aClosureType, 
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfClosureType:canDestroy: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfContainerType (
			final AvailObject object, 
			final AvailObject aContainerType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfContainerType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfContinuationType (
			final AvailObject object, 
			final AvailObject aContinuationType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfContinuationType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfCyclicType (
			final AvailObject object, 
			final AvailObject aCyclicType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfCyclicType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfGeneralizedClosureType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfIntegerRangeType (
			final AvailObject object, 
			final AvailObject anIntegerRangeType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfIntegerRangeType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfListType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfMapType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfObjectMeta (
			final AvailObject object, 
			final AvailObject anObjectMeta)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfObjectMeta: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfObjectMetaMeta (
			final AvailObject object, 
			final AvailObject anObjectMetaMeta)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfObjectMetaMeta: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfObjectType (
			final AvailObject object, 
			final AvailObject anObjectType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfObjectType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfSetType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeUnionOfTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:typeUnionOfTupleType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectUnclassified (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:unclassified: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectUnionOfTypesAtThrough (
			final AvailObject object, 
			final int startIndex, 
			final int endIndex)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:unionOfTypesAt:through: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectUntranslatedDataAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:untranslatedDataAt: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectUntranslatedDataAtPut (
			final AvailObject object, 
			final int index, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:untranslatedDataAt:put: in Avail.Descriptor", object);
		return;
	}

	void ObjectUpperBound (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:upperBound: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectValidateArgumentTypesInterpreterIfFail (
			final AvailObject object, 
			final List<AvailObject> argTypes, 
			final AvailInterpreter anAvailInterpreter, 
			final Continuation1<Generator<String>> failBlock)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:validateArgumentTypes:interpreter:ifFail: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectValidity (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:validity: in Avail.Descriptor", object);
		return;
	}

	void ObjectValue (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:value: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectValueAtIndex (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:valueAtIndex: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectValueAtIndexPut (
			final AvailObject object, 
			final int index, 
			final AvailObject valueObject)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:valueAtIndex:put: in Avail.Descriptor", object);
		return;
	}

	void ObjectValueType (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:valueType: in Avail.Descriptor", object);
		return;
	}

	void ObjectVariableBindings (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:variableBindings: in Avail.Descriptor", object);
		return;
	}

	void ObjectVectors (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:vectors: in Avail.Descriptor", object);
		return;
	}

	void ObjectVisibleNames (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:visibleNames: in Avail.Descriptor", object);
		return;
	}

	void ObjectWhichOne (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:whichOne: in Avail.Descriptor", object);
		return;
	}

	void ObjectWordcodes (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:wordcodes: in Avail.Descriptor", object);
		return;
	}

	int ObjectZoneForIndex (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:zoneForIndex: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectZzzDONOTUSETupleChunkRawZoneAtPut (
			final AvailObject object, 
			final int index, 
			final int value)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: Object:zzzDONOTUSETupleChunkRawZoneAt:put: in Avail.Descriptor", object);
		return;
	}

	String ObjectAsNativeString (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectAsNativeString: in Avail.Descriptor", object);
		return "";
	}

	AvailObject ObjectAsObject (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectAsObject: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectAsSet (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectAsSet: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectAsTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectAsTuple: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectBecomeExactType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectBecomeExactType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectBecomeRealTupleType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectBecomeRealTupleType: in Avail.Descriptor", object);
		return;
	}

	int ObjectBitsPerEntry (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectBitsPerEntry: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectBitVector (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectBitVector: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectBodyBlock (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectBodyBlock: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectBodySignature (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectBodySignature: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectBreakpointBlock (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectBreakpointBlock: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectCaller (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectCaller: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectCapacity (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectCapacity: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectCleanUpAfterCompile (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectCleanUpAfterCompile: in Avail.Descriptor", object);
		return;
	}

	void ObjectClearModule (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectClearModule: in Avail.Descriptor", object);
		return;
	}

	void ObjectClearValue (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectClearValue: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectClosure (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectClosure: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectClosureType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectClosureType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectCode (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectCode: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectCodePoint (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectCodePoint: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectComplete (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectComplete: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectConstantBindings (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectConstantBindings: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectContentType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectContentType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectContingentImpSets (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectContingentImpSets: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectContinuation (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectContinuation: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectCopyAsMutableContinuation (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectCopyAsMutableContinuation: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectCopyAsMutableObjectTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectCopyAsMutableObjectTuple: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectCopyAsMutableSpliceTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectCopyAsMutableSpliceTuple: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectCopyMutable (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectCopyMutable: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectDefaultType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectDefaultType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectDependentChunks (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectDependentChunks: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectDepth (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectDepth: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectDisplayTestingTree (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectDisplayTestingTree: in Avail.Descriptor", object);
		return;
	}

	void ObjectEnsureMetacovariant (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectEnsureMetacovariant: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectEnsureMutable (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectEnsureMutable: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectEvictedByGarbageCollector (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectEvictedByGarbageCollector: in Avail.Descriptor", object);
		return;
	}

	int ObjectExecutionMode (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectExecutionMode: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectExecutionState (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectExecutionState: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectExpand (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectExpand: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	boolean ObjectExtractBoolean (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectExtractBoolean: in Avail.Descriptor", object);
		return false;
	}

	short ObjectExtractByte (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectExtractByte: in Avail.Descriptor", object);
		return 0;
	}

	double ObjectExtractDouble (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectExtractDouble: in Avail.Descriptor", object);
		return 0.0d;
	}

	float ObjectExtractFloat (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectExtractFloat: in Avail.Descriptor", object);
		return 0.0f;
	}

	int ObjectExtractInt (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectExtractInt: in Avail.Descriptor", object);
		return 0;
	}

	byte ObjectExtractNybble (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectExtractNybble: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectFieldMap (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectFieldMap: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectFieldTypeMap (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectFieldTypeMap: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectFilteredBundleTree (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectFilteredBundleTree: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectFirstTupleType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectFirstTupleType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectGetInteger (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectGetInteger: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectGetValue (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectGetValue: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectHashOrZero (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectHashOrZero: in Avail.Descriptor", object);
		return 0;
	}

	boolean ObjectHasRestrictions (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectHasRestrictions: in Avail.Descriptor", object);
		return false;
	}

	int ObjectHiLevelTwoChunkLowOffset (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectHiLevelTwoChunkLowOffset: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectHiNumLocalsLowNumArgs (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectHiNumLocalsLowNumArgs: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectHiPrimitiveLowNumArgsAndLocalsAndStack (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectHiPrimitiveLowNumArgsAndLocalsAndStack: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectHiStartingChunkIndexLowNumOuters (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectHiStartingChunkIndexLowNumOuters: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectImplementationsTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectImplementationsTuple: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectInclusiveFlags (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectInclusiveFlags: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectIncomplete (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectIncomplete: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectIndex (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectIndex: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectInnerType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectInnerType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectInstance (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectInstance: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectInternalHash (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectInternalHash: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectInterruptRequestFlag (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectInterruptRequestFlag: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectInvocationCount (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectInvocationCount: in Avail.Descriptor", object);
		return 0;
	}

	boolean ObjectIsAbstract (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectIsAbstract: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsFinite (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectIsFinite: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsForward (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectIsForward: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsImplementation (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectIsImplementation: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsPositive (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectIsPositive: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSaved (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectIsSaved: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSplice (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectIsSplice: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfTerminates (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectIsSupertypeOfTerminates: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsSupertypeOfVoid (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectIsSupertypeOfVoid: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsValid (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectIsValid: in Avail.Descriptor", object);
		return false;
	}

	List<AvailObject> ObjectKeysAsArray (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectKeysAsArray: in Avail.Descriptor", object);
		return null;
	}

	AvailObject ObjectKeysAsSet (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectKeysAsSet: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectKeyType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectKeyType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectLevelTwoChunkIndex (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectLevelTwoChunkIndex: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectLevelTwoOffset (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectLevelTwoOffset: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectLowerBound (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectLowerBound: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	boolean ObjectLowerInclusive (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectLowerInclusive: in Avail.Descriptor", object);
		return false;
	}

	int ObjectMapSize (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectMapSize: in Avail.Descriptor", object);
		return 0;
	}

	short ObjectMaxStackDepth (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectMaxStackDepth: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectMessage (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectMessage: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectMessageParts (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectMessageParts: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectMethods (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectMethods: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectMoveToHead (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectMoveToHead: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectMyObjectMeta (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectMyObjectMeta: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectMyObjectType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectMyObjectType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectMyRestrictions (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectMyRestrictions: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectMyType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectMyType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectName (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectName: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectNames (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNames: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectNewNames (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNewNames: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectNext (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNext: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectNextIndex (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNextIndex: in Avail.Descriptor", object);
		return 0;
	}

	short ObjectNumArgs (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNumArgs: in Avail.Descriptor", object);
		return 0;
	}

	short ObjectNumArgsAndLocalsAndStack (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNumArgsAndLocalsAndStack: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectNumberOfZones (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNumberOfZones: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectNumBlanks (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNumBlanks: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectNumFloats (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNumFloats: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectNumIntegers (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNumIntegers: in Avail.Descriptor", object);
		return 0;
	}

	short ObjectNumLiterals (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNumLiterals: in Avail.Descriptor", object);
		return 0;
	}

	short ObjectNumLocals (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNumLocals: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectNumLocalsOrArgsOrStack (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNumLocalsOrArgsOrStack: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectNumObjects (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNumObjects: in Avail.Descriptor", object);
		return 0;
	}

	short ObjectNumOuters (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNumOuters: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectNumOuterVars (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNumOuterVars: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectNybbles (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectNybbles: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectPad (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectPad: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectParent (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectParent: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectPc (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectPc: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectPrevious (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectPrevious: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectPreviousIndex (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectPreviousIndex: in Avail.Descriptor", object);
		return 0;
	}

	short ObjectPrimitiveNumber (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectPrimitiveNumber: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectPriority (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectPriority: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectPrivateNames (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectPrivateNames: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectPrivateTestingTree (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectPrivateTestingTree: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectProcessGlobals (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectProcessGlobals: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectRawQuad1 (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectRawQuad1: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectRawQuad2 (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectRawQuad2: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectReleaseVariableOrMakeContentsImmutable (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectReleaseVariableOrMakeContentsImmutable: in Avail.Descriptor", object);
		return;
	}

	void ObjectRemoveFromQueue (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectRemoveFromQueue: in Avail.Descriptor", object);
		return;
	}

	void ObjectRemoveRestrictions (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectRemoveRestrictions: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectRequiresBlock (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectRequiresBlock: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectRestrictions (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectRestrictions: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectReturnsBlock (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectReturnsBlock: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectReturnType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectReturnType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectRootBin (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectRootBin: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectSecondTupleType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectSecondTupleType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectSetSize (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectSetSize: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectSignature (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectSignature: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectSize (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectSize: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectSizeRange (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectSizeRange: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectStackp (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectStackp: in Avail.Descriptor", object);
		return 0;
	}

	int ObjectStartingChunkIndex (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectStartingChunkIndex: in Avail.Descriptor", object);
		return 0;
	}

	void ObjectStep (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectStep: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectTestingTree (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectTestingTree: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectTrimExcessLongs (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectTrimExcessLongs: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectTuple: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectTupleSize (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectTupleSize: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectTupleType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectTupleType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTypeTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectTypeTuple: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectUnclassified (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectUnclassified: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectUpperBound (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectUpperBound: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	boolean ObjectUpperInclusive (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectUpperInclusive: in Avail.Descriptor", object);
		return false;
	}

	int ObjectValidity (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectValidity: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectValue (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectValue: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectValuesAsTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectValuesAsTuple: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectValueType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectValueType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectVariableBindings (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectVariableBindings: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectVectors (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectVectors: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	void ObjectVerify (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectVerify: in Avail.Descriptor", object);
		return;
	}

	AvailObject ObjectVisibleNames (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectVisibleNames: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectWhichOne (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectWhichOne: in Avail.Descriptor", object);
		return 0;
	}

	AvailObject ObjectWordcodes (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		error("Subclass responsibility: ObjectWordcodes: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}



	// GENERATED special mutable slots

	boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		return false;
	}



	// java printing

	int maximumIndent ()
	{
		//  Answer the deepest a recursive print can go before summarizing.

		return 5;
	}

	/**
	 * Recursively print the specified {@link AvailObject} to the {@link
	 * StringBuilder} unless it is already present in the {@linkplain List
	 * recursion list}. Printing will begin at the specified indent level,
	 * measured in horizontal tab characters.
	 * 
	 * @param object An {@link AvailObject}.
	 * @param builder A {@link StringBuilder}.
	 * @param recursionList A {@linkplain List list} containing {@link
	 *                      AvailObject}s already visited during the recursive
	 *                      print.
	 * @param indent The indent level, in horizontal tabs, at which the {@link
	 *               AvailObject} should be printed.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@ThreadSafe
	void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object, 
		final @NotNull StringBuilder builder, 
		final @NotNull List<AvailObject> recursionList, 
		final int indent)
	{
		builder.append('a');
		String className = getClass().getSimpleName();
		String shortenedName = className.substring(0, className.length() - 10);
		switch (shortenedName.codePointAt(0))
		{
			case 'A':
			case 'E':
			case 'I':
			case 'O':
			case 'U':
				builder.append('n');
				break;
			default:
				// Do nothing.
		}
		builder.append(' ');
		builder.append(shortenedName);
		IntegerSlots integerSlotAnnotation =
			getClass().getAnnotation(IntegerSlots.class);
		for (int i = 1, limit = object.integerSlotsCount(); i <= limit; i++)
		{
			builder.append('\n');
			for (int tab = 0; tab < indent; tab++)
			{
				builder.append('\t');
			}
			int n = Math.min(i, integerSlotAnnotation.value().length) - 1;
			String slotName = integerSlotAnnotation.value()[n];
			if (slotName.charAt(slotName.length() - 1) == '#')
			{
				builder.append(slotName, 0, slotName.length() - 1);
				builder.append('[');
				builder.append(i - integerSlotAnnotation.value().length + 1);
				builder.append(']');
			}
			else
			{
				builder.append(slotName);
			}
			builder.append(" = ");
			builder.append(object.integerSlotAtByteIndex(i << 2));
		}
		ObjectSlots objectSlotAnnotation =
			getClass().getAnnotation(ObjectSlots.class);
		for (int i = 1, limit = object.objectSlotsCount(); i <= limit; i++)
		{
			builder.append('\n');
			for (int tab = 0; tab < indent; tab++)
			{
				builder.append('\t');
			}
			int n = Math.min(i, objectSlotAnnotation.value().length) - 1;
			String slotName = objectSlotAnnotation.value()[n];
			if (slotName.charAt(slotName.length() - 1) == '#')
			{
				builder.append(slotName, 0, slotName.length() - 1);
				builder.append('[');
				builder.append(i - objectSlotAnnotation.value().length + 1);
				builder.append(']');
			}
			else
			{
				builder.append(slotName);
			}
			builder.append(" = ");
			(object.objectSlotAtByteIndex(-(i << 2))).printOnAvoidingIndent(
				builder, recursionList, indent + 1);
		}
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		error("Subclass responsibility: Object:equals: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectEqualsAnyTuple (
			final AvailObject object, 
			final AvailObject aTuple)
	{
		return false;
	}

	boolean ObjectEqualsByteString (
			final AvailObject object, 
			final AvailObject aString)
	{
		return false;
	}

	boolean ObjectEqualsByteTuple (
			final AvailObject object, 
			final AvailObject aTuple)
	{
		return false;
	}

	boolean ObjectEqualsCharacterWithCodePoint (
			final AvailObject object, 
			final int otherCodePoint)
	{
		return false;
	}

	boolean ObjectEqualsClosure (
			final AvailObject object, 
			final AvailObject aClosure)
	{
		return false;
	}

	boolean ObjectEqualsClosureType (
			final AvailObject object, 
			final AvailObject aClosureType)
	{
		return false;
	}

	boolean ObjectEqualsCompiledCode (
			final AvailObject object, 
			final AvailObject aCompiledCode)
	{
		return false;
	}

	boolean ObjectEqualsContainer (
			final AvailObject object, 
			final AvailObject aContainer)
	{
		return false;
	}

	boolean ObjectEqualsContainerType (
			final AvailObject object, 
			final AvailObject aType)
	{
		return false;
	}

	boolean ObjectEqualsContinuation (
			final AvailObject object, 
			final AvailObject aContinuation)
	{
		return false;
	}

	boolean ObjectEqualsContinuationType (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  GENERATED pure (abstract) method.

		return false;
	}

	boolean ObjectEqualsDouble (
			final AvailObject object, 
			final AvailObject aDoubleObject)
	{
		return false;
	}

	boolean ObjectEqualsFloat (
			final AvailObject object, 
			final AvailObject aFloatObject)
	{
		return false;
	}

	boolean ObjectEqualsGeneralizedClosureType (
			final AvailObject object, 
			final AvailObject aGeneralizedClosureType)
	{
		return false;
	}

	boolean ObjectEqualsInfinity (
			final AvailObject object, 
			final AvailObject anInfinity)
	{
		return false;
	}

	boolean ObjectEqualsInteger (
			final AvailObject object, 
			final AvailObject anAvailInteger)
	{
		return false;
	}

	boolean ObjectEqualsIntegerRangeType (
			final AvailObject object, 
			final AvailObject another)
	{
		return false;
	}

	boolean ObjectEqualsList (
			final AvailObject object, 
			final AvailObject aList)
	{
		return false;
	}

	boolean ObjectEqualsListType (
			final AvailObject object, 
			final AvailObject aListType)
	{
		return false;
	}

	boolean ObjectEqualsMap (
			final AvailObject object, 
			final AvailObject aMap)
	{
		return false;
	}

	boolean ObjectEqualsMapType (
			final AvailObject object, 
			final AvailObject aMapType)
	{
		return false;
	}

	boolean ObjectEqualsNybbleTuple (
			final AvailObject object, 
			final AvailObject aTuple)
	{
		return false;
	}

	boolean ObjectEqualsObject (
			final AvailObject object, 
			final AvailObject anObject)
	{
		return false;
	}

	boolean ObjectEqualsObjectTuple (
			final AvailObject object, 
			final AvailObject aTuple)
	{
		return false;
	}

	boolean ObjectEqualsPrimitiveType (
			final AvailObject object, 
			final AvailObject aType)
	{
		return false;
	}

	boolean ObjectEqualsSet (
			final AvailObject object, 
			final AvailObject aSet)
	{
		return false;
	}

	boolean ObjectEqualsSetType (
			final AvailObject object, 
			final AvailObject aSetType)
	{
		return false;
	}

	boolean ObjectEqualsTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		return false;
	}

	boolean ObjectEqualsTwoByteString (
			final AvailObject object, 
			final AvailObject aString)
	{
		return false;
	}

	boolean ObjectHasObjectInstance (
			final AvailObject object, 
			final AvailObject potentialInstance)
	{
		//  The potentialInstance is a user-defined object.  See if it is an instance of me.

		return false;
	}

	boolean ObjectIsBetterRepresentationThan (
			final AvailObject object, 
			final AvailObject anotherObject)
	{
		//  Given two objects that are known to be equal, is the first one in a better form (more
		//  compact, more efficient, older generation) than the second one?

		return ((object.objectSlotsCount() + object.integerSlotsCount()) < (anotherObject.objectSlotsCount() + anotherObject.integerSlotsCount()));
	}

	boolean ObjectIsBetterRepresentationThanTupleType (
			final AvailObject object, 
			final AvailObject aTupleType)
	{
		//  Given two objects that are known to be equal, the second of which is in the form of
		//  a tuple type, is the first one in a better form than the second one?

		//  Explanation: This must be called with a tuple type as the second argument, but
		//  the two arguments must also be equal.  All alternative implementations of tuple
		//  types should reimplement this method.
		error("Subclass responsibility: Object:isBetterRepresentationThanTupleType: in Avail.Descriptor", object);
		return false;
	}

	boolean ObjectIsInstanceOfSubtypeOf (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Answer whether object is an instance of a subtype of aType.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		return object.exactType().isSubtypeOf(aType);
	}

	boolean ObjectCanComputeHashOfType (
			final AvailObject object)
	{
		//  Answer whether object supports the #hashOfType protocol.

		return false;
	}

	boolean ObjectEqualsBlank (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectEqualsFalse (
			final AvailObject object)
	{
		//  Answer true if this is the Avail false object, which it isn't.

		return false;
	}

	boolean ObjectEqualsTrue (
			final AvailObject object)
	{
		//  Answer true if this is the Avail true object, which it isn't.

		return false;
	}

	boolean ObjectEqualsVoid (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectEqualsVoidOrBlank (
			final AvailObject object)
	{
		return false;
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		error("Subclass responsibility: ObjectExactType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit long that is always the same for equal objects, but
		//  statistically different for different objects.

		error("Subclass responsibility: ObjectHash: in Avail.Descriptor", object);
		return 0;
	}

	boolean ObjectIsClosure (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return true;
	}

	AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.  If I was mutable I have to scan
		//  my children and make them immutable as well (recursively down to immutable descendants).

		if (_isMutable)
		{
			object.descriptorId((short)(object.descriptorId() | 1));
			object.makeSubobjectsImmutable();
		}
		return object;
	}

	void ObjectMakeSubobjectsImmutable (
			final AvailObject object)
	{
		//  Make my subobjects be immutable.  Don't change my own mutability state.
		//  Also, ignore my mutability state, as it should be tested (and sometimes set
		//  preemptively to immutable) prior to invoking this method.

		object.scanSubobjects(new AvailBeImmutableSubobjectVisitor());
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		error("Subclass responsibility: ObjectType: in Avail.Descriptor", object);
		return VoidDescriptor.voidObject();
	}



	// operations-booleans

	boolean ObjectIsBoolean (
			final AvailObject object)
	{
		return false;
	}



	// operations-characters

	boolean ObjectIsCharacter (
			final AvailObject object)
	{
		return false;
	}



	// operations-closure

	boolean ObjectContainsBlock (
			final AvailObject object, 
			final AvailObject aClosure)
	{
		//  Answer true if either I am aClosure or I contain aClosure.  I only follow
		//  the trail of literal compiledCode and closure objects, so this is a dead end.

		return false;
	}



	// operations-faulting

	void ObjectPostFault (
			final AvailObject object)
	{
		//  The object was just scanned, and its pointers converted into valid ToSpace pointers.
		//  Do any follow-up activities specific to the kind of object it is.
		//
		//  do nothing


	}

	void ObjectReadBarrierFault (
			final AvailObject object)
	{
		//  The object is in ToSpace, and its fields already refer to ToSpace objects.  Do nothing,
		//  as there is no read barrier.  See also implementation in GCReadBarrierDescriptor.
		//
		//  do nothing


	}



	// operations-indirections

	void ObjectTarget (
			final AvailObject object, 
			final AvailObject value)
	{
		//  From IndirectionObjectDescriptor.  Fail if we're not an indirection object.

		error("This isn't an indirection object", object);
		return;
	}

	AvailObject ObjectTarget (
			final AvailObject object)
	{
		//  From IndirectionObjectDescriptor.  Fail if we're not an indirection object.

		error("This isn't an indirection object", object);
		return VoidDescriptor.voidObject();
	}

	AvailObject ObjectTraversed (
			final AvailObject object)
	{
		//  Overidden in IndirectionDescriptor to skip over indirections.

		return object;
	}



	// operations-lists

	boolean ObjectIsList (
			final AvailObject object)
	{
		return false;
	}



	// operations-maps

	boolean ObjectIsMap (
			final AvailObject object)
	{
		return false;
	}



	// operations-numbers

	boolean ObjectIsByte (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectIsNybble (
			final AvailObject object)
	{
		return false;
	}



	// operations-set

	boolean ObjectIsSet (
			final AvailObject object)
	{
		return false;
	}



	// operations-set bins

	AvailObject ObjectBinAddingElementHashLevelCanDestroy (
			final AvailObject object, 
			final AvailObject elementObject, 
			final int elementObjectHash, 
			final byte myLevel, 
			final boolean canDestroy)
	{
		//  Add the given element to this bin, potentially modifying it if canDestroy and it's
		//  mutable.  Answer the new bin.  Note that the client is responsible for marking
		//  elementObject as immutable if another reference exists.  In particular, the
		//  object is masquerading as a bin of size one.

		if (object.equals(elementObject))
		{
			return object;
		}
		//  Create a linear bin with two slots.
		final AvailObject result = AvailObject.newIndexedDescriptor(2, LinearSetBinDescriptor.isMutableLevel(true, myLevel));
		result.binHash(((object.hash() + elementObject.hash()) & HashMask));
		result.binElementAtPut(1, object);
		result.binElementAtPut(2, elementObject);
		if (! canDestroy)
		{
			result.makeImmutable();
		}
		return result;
	}

	boolean ObjectBinHasElementHash (
			final AvailObject object, 
			final AvailObject elementObject, 
			final int elementObjectHash)
	{
		//  Elements are treated as bins to save space, since bins are not
		//  entirely first-class objects (i.e., they can't be added to sets.

		return object.equals(elementObject);
	}

	AvailObject ObjectBinRemoveElementHashCanDestroy (
			final AvailObject object, 
			final AvailObject elementObject, 
			final int elementObjectHash, 
			final boolean canDestroy)
	{
		//  Remove elementObject from the bin object, if present.  Answer the resulting bin.  The bin
		//  may be modified if it's mutable and canDestroy.  In particular, an element is masquerading
		//  as a bin of size one, so the answer must be either the object or voidObject (to indicate a size
		//  zero bin).

		if (object.equals(elementObject))
		{
			return VoidDescriptor.voidObject();
		}
		if (! canDestroy)
		{
			object.makeImmutable();
		}
		return object;
	}

	boolean ObjectIsBinSubsetOf (
			final AvailObject object, 
			final AvailObject potentialSuperset)
	{
		//  Sets only use explicit bins for collisions, otherwise they store the element
		//  itself.  This works because a bin can't be an element of a set.  Likewise,
		//  the voidObject can't be a member of a set and is treated like an empty bin.

		return potentialSuperset.hasElement(object);
	}

	int ObjectPopulateTupleStartingAt (
			final AvailObject object, 
			final AvailObject mutableTuple, 
			final int startingIndex)
	{
		//  Write set bin elements into the tuple, starting at the given startingIndex.  Answer
		//  the next available index in which to write.  Regular objects act as set bins
		//  of size 1, so treat them that way.

		assert mutableTuple.descriptor().isMutable();
		mutableTuple.tupleAtPut(startingIndex, object);
		return (startingIndex + 1);
	}

	int ObjectBinHash (
			final AvailObject object)
	{
		//  An object masquerading as a size one bin has a bin hash which is the sum of
		//  the elements' hashes, which in this case is just the object's hash.

		return object.hash();
	}

	int ObjectBinSize (
			final AvailObject object)
	{
		//  Answer how many elements this bin contains.  I act as a bin of size one.

		return 1;
	}

	AvailObject ObjectBinUnionType (
			final AvailObject object)
	{
		//  Answer the union of the types of this bin's elements.  I act as a bin of size one.

		return object.type();
	}



	// operations-tuples

	boolean ObjectIsTuple (
			final AvailObject object)
	{
		return false;
	}

	AvailObject privateMutableObjectOfSize (
			final int size)
	{
		//  This method is really only appropriate for ByteTupleDescriptors and NybbleTupleDescriptors,
		//  but the type can't be strengthened enough without an unchecked cast (or a dynamic_cast with
		//  all the rtti baggage that entails).

		error("Subclass responsibility: privateMutableObjectOfSize: in Avail.Descriptor");
		return VoidDescriptor.voidObject();
	}



	// operations-types

	boolean ObjectTypeEquals (
			final AvailObject object, 
			final AvailObject aType)
	{
		//  Answer whether object's type is equal to aType (known to be a type).
		//  The current message may only be sent if the subclass receiving it has
		//  overidden ObjectCanComputeHashOfType to answer true.

		//  only provide if subclass canComputeHashOfType.
		error("Subclass responsibility: Object:typeEquals: in Avail.Descriptor", object);
		return false;
	}

	int ObjectHashOfType (
			final AvailObject object)
	{
		//  We are computing the hash value of some ApproximateType, and it has
		//  delegated responsibility back to this descriptor, the one that created the
		//  ApproximateType that we're now trying to hash.  Only subclasses that
		//  answer true to the query canComputeHashOfType need to implement
		//  this method.

		//  only provide if subclass canComputeHashOfType.
		error("Subclass responsibility: ObjectHashOfType: in Avail.Descriptor", object);
		return 0;
	}

	boolean ObjectIsCyclicType (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectIsExtendedInteger (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectIsIntegerRangeType (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectIsListType (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectIsMapType (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectIsSetType (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectIsTupleType (
			final AvailObject object)
	{
		return false;
	}

	boolean ObjectIsType (
			final AvailObject object)
	{
		return false;
	}



	// scanning

	void ObjectScanSubobjects (
			final AvailObject object, 
			final AvailSubobjectVisitor visitor)
	{
		for (int byteIndex = -4, _end1 = (object.objectSlotsCount() * -4); byteIndex >= _end1; byteIndex -= 4)
		{
			visitor.invokeWithParentIndex(object, byteIndex);
		}
	}



	// slots

	void checkWriteAtByteIndex (
			final int index)
	{
		if (isMutable())
		{
			return;
		}
		if (allowsImmutableToMutableReferenceAtByteIndex(index))
		{
			return;
		}
		error("Illegal write into immutable object");
		return;
	}

	boolean hasVariableIntegerSlots ()
	{
		//  Answer whether I have a variable number of integer slots.

		return _hasVariableIntegerSlots;
	}

	boolean hasVariableObjectSlots ()
	{
		//  Answer whether I have a variable number of object slots.

		return _hasVariableObjectSlots;
	}

	int numberOfFixedIntegerSlots ()
	{
		//  Answer how many named integer slots I have, excluding the indexed slots that may be at the end.

		return _numberOfFixedIntegerSlots;
	}

	int numberOfFixedObjectSlots ()
	{
		//  Answer how many named object slots I have, excluding the indexed slots that may be at the end.

		return _numberOfFixedObjectSlots;
	}

	void numberOfFixedObjectSlotsNumberOfFixedIntegerSlotsHasVariableObjectSlotsHasVariableIntegerSlots (
			final int theNumberOfFixedObjectSlots, 
			final int theNumberOfFixedIntegerSlots, 
			final boolean variableObjectSlots, 
			final boolean variableIntegerSlots)
	{
		assert theNumberOfFixedObjectSlots >= 1;
		_numberOfFixedObjectSlots = theNumberOfFixedObjectSlots;
		_numberOfFixedIntegerSlots = theNumberOfFixedIntegerSlots;
		_hasVariableObjectSlots = variableObjectSlots;
		_hasVariableIntegerSlots = variableIntegerSlots;
	}




	// Startup/shutdown
	static void createWellKnownObjects ()
	{
		//  Default implementation - subclasses may need more variations.
		//
		//  do nothing


	}

	static void clearWellKnownObjects ()
	{
		//  Default implementation - subclasses may need more variations.
		//
		//  do nothing


	}



	static final int HashMask = 0x00FFFFFF;

	static final int numActualDescriptors = 166;

	static final Descriptor [] allDescriptors = {
		new AbstractSignatureDescriptor().initDescriptor(0, true, 3, 0, false, false),
		new AbstractSignatureDescriptor().initDescriptor(1, false, 3, 0, false, false),
		new ApproximateTypeDescriptor().initDescriptor(2, true, 1, 0, false, false),
		new ApproximateTypeDescriptor().initDescriptor(3, false, 1, 0, false, false),
		new AvailModuleDescriptor().initDescriptor(4, true, 10, 0, false, false),
		new AvailModuleDescriptor().initDescriptor(5, false, 10, 0, false, false),
		new BlankDescriptor().initDescriptor(6, true, 0, 0, false, false),
		new BlankDescriptor().initDescriptor(7, false, 0, 0, false, false),
		new ByteStringDescriptor().initDescriptorWithUnusedBytes(8, true, 0, 1, false, true, 0),
		new ByteStringDescriptor().initDescriptorWithUnusedBytes(9, false, 0, 1, false, true, 0),
		new ByteStringDescriptor().initDescriptorWithUnusedBytes(10, true, 0, 1, false, true, 3),
		new ByteStringDescriptor().initDescriptorWithUnusedBytes(11, false, 0, 1, false, true, 3),
		new ByteStringDescriptor().initDescriptorWithUnusedBytes(12, true, 0, 1, false, true, 2),
		new ByteStringDescriptor().initDescriptorWithUnusedBytes(13, false, 0, 1, false, true, 2),
		new ByteStringDescriptor().initDescriptorWithUnusedBytes(14, true, 0, 1, false, true, 1),
		new ByteStringDescriptor().initDescriptorWithUnusedBytes(15, false, 0, 1, false, true, 1),
		new ByteTupleDescriptor().initDescriptorWithUnusedBytes(16, true, 0, 1, false, true, 0),
		new ByteTupleDescriptor().initDescriptorWithUnusedBytes(17, false, 0, 1, false, true, 0),
		new ByteTupleDescriptor().initDescriptorWithUnusedBytes(18, true, 0, 1, false, true, 3),
		new ByteTupleDescriptor().initDescriptorWithUnusedBytes(19, false, 0, 1, false, true, 3),
		new ByteTupleDescriptor().initDescriptorWithUnusedBytes(20, true, 0, 1, false, true, 2),
		new ByteTupleDescriptor().initDescriptorWithUnusedBytes(21, false, 0, 1, false, true, 2),
		new ByteTupleDescriptor().initDescriptorWithUnusedBytes(22, true, 0, 1, false, true, 1),
		new ByteTupleDescriptor().initDescriptorWithUnusedBytes(23, false, 0, 1, false, true, 1),
		new CharacterDescriptor().initDescriptor(24, true, 0, 1, false, false),
		new CharacterDescriptor().initDescriptor(25, false, 0, 1, false, false),
		new ClosureDescriptor().initDescriptor(26, true, 1, 0, true, false),
		new ClosureDescriptor().initDescriptor(27, false, 1, 0, true, false),
		new ClosureTypeDescriptor().initDescriptor(28, true, 1, 1, true, false),
		new ClosureTypeDescriptor().initDescriptor(29, false, 1, 1, true, false),
		new CompiledCodeDescriptor().initDescriptor(30, true, 2, 5, true, false),
		new CompiledCodeDescriptor().initDescriptor(31, false, 2, 5, true, false),
		new ConcatenatedTupleTypeDescriptor().initDescriptor(32, true, 2, 0, false, false),
		new ConcatenatedTupleTypeDescriptor().initDescriptor(33, false, 2, 0, false, false),
		new ContainerDescriptor().initDescriptor(34, true, 2, 1, false, false),
		new ContainerDescriptor().initDescriptor(35, false, 2, 1, false, false),
		new ContainerTypeDescriptor().initDescriptor(36, true, 1, 0, false, false),
		new ContainerTypeDescriptor().initDescriptor(37, false, 1, 0, false, false),
		new ContinuationDescriptor().initDescriptor(38, true, 2, 3, true, false),
		new ContinuationDescriptor().initDescriptor(39, false, 2, 3, true, false),
		new ContinuationTypeDescriptor().initDescriptor(40, true, 1, 0, false, false),
		new ContinuationTypeDescriptor().initDescriptor(41, false, 1, 0, false, false),
		new CyclicTypeDescriptor().initDescriptor(42, true, 1, 1, false, false),
		new CyclicTypeDescriptor().initDescriptor(43, false, 1, 1, false, false),
		new DoubleDescriptor().initDescriptor(44, true, 0, 2, false, false),
		new DoubleDescriptor().initDescriptor(45, false, 0, 2, false, false),
		new ExpandedMessageBundleTreeDescriptor().initDescriptor(46, true, 2, 1, false, false),
		new ExpandedMessageBundleTreeDescriptor().initDescriptor(47, false, 2, 1, false, false),
		new FalseDescriptor().initDescriptor(48, true, 0, 0, false, false),
		new FalseDescriptor().initDescriptor(49, false, 0, 0, false, false),
		new FillerDescriptor().initDescriptor(50, true, 0, 0, false, false),
		new FillerDescriptor().initDescriptor(51, false, 0, 0, false, false),
		new FloatDescriptor().initDescriptor(52, true, 0, 1, false, false),
		new FloatDescriptor().initDescriptor(53, false, 0, 1, false, false),
		new ForwardSignatureDescriptor().initDescriptor(54, true, 1, 0, false, false),
		new ForwardSignatureDescriptor().initDescriptor(55, false, 1, 0, false, false),
		new GeneralizedClosureTypeDescriptor().initDescriptor(56, true, 1, 0, false, false),
		new GeneralizedClosureTypeDescriptor().initDescriptor(57, false, 1, 0, false, false),
		new HashedSetBinDescriptor().initDescriptorWithLevel(58, true, 1, 3, true, false, 0),
		new HashedSetBinDescriptor().initDescriptorWithLevel(59, false, 1, 3, true, false, 0),
		new HashedSetBinDescriptor().initDescriptorWithLevel(60, true, 1, 3, true, false, 1),
		new HashedSetBinDescriptor().initDescriptorWithLevel(61, false, 1, 3, true, false, 1),
		new HashedSetBinDescriptor().initDescriptorWithLevel(62, true, 1, 3, true, false, 2),
		new HashedSetBinDescriptor().initDescriptorWithLevel(63, false, 1, 3, true, false, 2),
		new HashedSetBinDescriptor().initDescriptorWithLevel(64, true, 1, 3, true, false, 3),
		new HashedSetBinDescriptor().initDescriptorWithLevel(65, false, 1, 3, true, false, 3),
		new HashedSetBinDescriptor().initDescriptorWithLevel(66, true, 1, 3, true, false, 4),
		new HashedSetBinDescriptor().initDescriptorWithLevel(67, false, 1, 3, true, false, 4),
		new HashedSetBinDescriptor().initDescriptorWithLevel(68, true, 1, 3, true, false, 5),
		new HashedSetBinDescriptor().initDescriptorWithLevel(69, false, 1, 3, true, false, 5),
		new HashedSetBinDescriptor().initDescriptorWithLevel(70, true, 1, 3, true, false, 6),
		new HashedSetBinDescriptor().initDescriptorWithLevel(71, false, 1, 3, true, false, 6),
		new ImplementationSetDescriptor().initDescriptor(72, true, 4, 0, false, false),
		new ImplementationSetDescriptor().initDescriptor(73, false, 4, 0, false, false),
		new IndirectionDescriptor().initDescriptor(74, true, 1, 0, false, false),
		new IndirectionDescriptor().initDescriptor(75, false, 1, 0, false, false),
		new InfinityDescriptor().initDescriptor(76, true, 0, 1, false, false),
		new InfinityDescriptor().initDescriptor(77, false, 0, 1, false, false),
		new IntegerDescriptor().initDescriptor(78, true, 0, 0, false, true),
		new IntegerDescriptor().initDescriptor(79, false, 0, 0, false, true),
		new IntegerRangeTypeDescriptor().initDescriptor(80, true, 2, 1, false, false),
		new IntegerRangeTypeDescriptor().initDescriptor(81, false, 2, 1, false, false),
		new L2ChunkDescriptor().initDescriptor(82, true, 3, 7, true, false),
		new L2ChunkDescriptor().initDescriptor(83, false, 3, 7, true, false),
		new LinearSetBinDescriptor().initDescriptorWithLevel(84, true, 0, 1, true, false, 0),
		new LinearSetBinDescriptor().initDescriptorWithLevel(85, false, 0, 1, true, false, 0),
		new LinearSetBinDescriptor().initDescriptorWithLevel(86, true, 0, 1, true, false, 1),
		new LinearSetBinDescriptor().initDescriptorWithLevel(87, false, 0, 1, true, false, 1),
		new LinearSetBinDescriptor().initDescriptorWithLevel(88, true, 0, 1, true, false, 2),
		new LinearSetBinDescriptor().initDescriptorWithLevel(89, false, 0, 1, true, false, 2),
		new LinearSetBinDescriptor().initDescriptorWithLevel(90, true, 0, 1, true, false, 3),
		new LinearSetBinDescriptor().initDescriptorWithLevel(91, false, 0, 1, true, false, 3),
		new LinearSetBinDescriptor().initDescriptorWithLevel(92, true, 0, 1, true, false, 4),
		new LinearSetBinDescriptor().initDescriptorWithLevel(93, false, 0, 1, true, false, 4),
		new LinearSetBinDescriptor().initDescriptorWithLevel(94, true, 0, 1, true, false, 5),
		new LinearSetBinDescriptor().initDescriptorWithLevel(95, false, 0, 1, true, false, 5),
		new LinearSetBinDescriptor().initDescriptorWithLevel(96, true, 0, 1, true, false, 6),
		new LinearSetBinDescriptor().initDescriptorWithLevel(97, false, 0, 1, true, false, 6),
		new LinearSetBinDescriptor().initDescriptorWithLevel(98, true, 0, 1, true, false, 7),
		new LinearSetBinDescriptor().initDescriptorWithLevel(99, false, 0, 1, true, false, 7),
		new ListDescriptor().initDescriptor(100, true, 1, 0, false, false),
		new ListDescriptor().initDescriptor(101, false, 1, 0, false, false),
		new ListTypeDescriptor().initDescriptor(102, true, 1, 0, false, false),
		new ListTypeDescriptor().initDescriptor(103, false, 1, 0, false, false),
		new MapDescriptor().initDescriptor(104, true, 0, 3, true, false),
		new MapDescriptor().initDescriptor(105, false, 0, 3, true, false),
		new MapTypeDescriptor().initDescriptor(106, true, 3, 0, false, false),
		new MapTypeDescriptor().initDescriptor(107, false, 3, 0, false, false),
		new MessageBundleDescriptor().initDescriptor(108, true, 3, 0, false, false),
		new MessageBundleDescriptor().initDescriptor(109, false, 3, 0, false, false),
		new MethodSignatureDescriptor().initDescriptor(110, true, 3, 0, false, false),
		new MethodSignatureDescriptor().initDescriptor(111, false, 3, 0, false, false),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(112, true, 0, 1, false, true, 0),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(113, false, 0, 1, false, true, 0),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(114, true, 0, 1, false, true, 7),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(115, false, 0, 1, false, true, 7),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(116, true, 0, 1, false, true, 6),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(117, false, 0, 1, false, true, 6),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(118, true, 0, 1, false, true, 5),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(119, false, 0, 1, false, true, 5),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(120, true, 0, 1, false, true, 4),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(121, false, 0, 1, false, true, 4),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(122, true, 0, 1, false, true, 3),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(123, false, 0, 1, false, true, 3),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(124, true, 0, 1, false, true, 2),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(125, false, 0, 1, false, true, 2),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(126, true, 0, 1, false, true, 1),
		new NybbleTupleDescriptor().initDescriptorWithUnusedNybbles(127, false, 0, 1, false, true, 1),
		new ObjectDescriptor().initDescriptor(128, true, 1, 0, false, false),
		new ObjectDescriptor().initDescriptor(129, false, 1, 0, false, false),
		new ObjectMetaDescriptor().initDescriptor(130, true, 1, 0, false, false),
		new ObjectMetaDescriptor().initDescriptor(131, false, 1, 0, false, false),
		new ObjectMetaMetaDescriptor().initDescriptor(132, true, 1, 0, false, false),
		new ObjectMetaMetaDescriptor().initDescriptor(133, false, 1, 0, false, false),
		new ObjectTupleDescriptor().initDescriptor(134, true, 0, 1, true, false),
		new ObjectTupleDescriptor().initDescriptor(135, false, 0, 1, true, false),
		new ObjectTypeDescriptor().initDescriptor(136, true, 1, 0, false, false),
		new ObjectTypeDescriptor().initDescriptor(137, false, 1, 0, false, false),
		new PrimitiveTypeDescriptor().initDescriptor(138, true, 3, 1, false, false),
		new PrimitiveTypeDescriptor().initDescriptor(139, false, 3, 1, false, false),
		new ProcessDescriptor().initDescriptor(140, true, 3, 5, false, false),
		new ProcessDescriptor().initDescriptor(141, false, 3, 5, false, false),
		new SetDescriptor().initDescriptor(142, true, 1, 0, false, false),
		new SetDescriptor().initDescriptor(143, false, 1, 0, false, false),
		new SetTypeDescriptor().initDescriptor(144, true, 2, 0, false, false),
		new SetTypeDescriptor().initDescriptor(145, false, 2, 0, false, false),
		new SpliceTupleDescriptor().initDescriptor(146, true, 0, 1, true, true),
		new SpliceTupleDescriptor().initDescriptor(147, false, 0, 1, true, true),
		new TerminatesMetaDescriptor().initDescriptor(148, true, 3, 1, false, false),
		new TerminatesMetaDescriptor().initDescriptor(149, false, 3, 1, false, false),
		new TerminatesTypeDescriptor().initDescriptor(150, true, 3, 1, false, false),
		new TerminatesTypeDescriptor().initDescriptor(151, false, 3, 1, false, false),
		new TrueDescriptor().initDescriptor(152, true, 0, 0, false, false),
		new TrueDescriptor().initDescriptor(153, false, 0, 0, false, false),
		new TupleTypeDescriptor().initDescriptor(154, true, 3, 0, false, false),
		new TupleTypeDescriptor().initDescriptor(155, false, 3, 0, false, false),
		new TwoByteStringDescriptor().initDescriptor(156, true, 0, 1, false, true),
		new TwoByteStringDescriptor().initDescriptor(157, false, 0, 1, false, true),
		new TwoByteStringDescriptor().initDescriptor(158, true, 0, 1, false, true),
		new TwoByteStringDescriptor().initDescriptor(159, false, 0, 1, false, true),
		new UnexpandedMessageBundleTreeDescriptor().initDescriptor(160, true, 2, 1, false, false),
		new UnexpandedMessageBundleTreeDescriptor().initDescriptor(161, false, 2, 1, false, false),
		new VoidDescriptor().initDescriptor(162, true, 0, 0, false, false),
		new VoidDescriptor().initDescriptor(163, false, 0, 0, false, false),
		new VoidTypeDescriptor().initDescriptor(164, true, 3, 1, false, false),
		new VoidTypeDescriptor().initDescriptor(165, false, 3, 1, false, false)
	};

	enum LinkNames
	{
		zeroOffsetLink(0),
		abstractSignatureLink(1),
		allLink(2),
		booleanTypeLink(3),
		closureLink(4),
		closureTypeLink(5),
		compiledCodeLink(6),
		containerLink(7),
		containerTypeLink(8),
		continuationLink(9),
		continuationTypeLink(10),
		cyclicTypeLink(11),
		doubleObjectLink(12),
		falseTypeLink(13),
		floatObjectLink(14),
		forwardSignatureLink(15),
		generalizedClosureTypeLink(16),
		implementationSetLink(17),
		integerTypeLink(18),
		listTypeLink(19),
		mapTypeLink(20),
		messageBundleLink(21),
		messageBundleTreeLink(22),
		metaLink(23),
		methodSignatureLink(24),
		objectMetaMetaLink(25),
		primTypeLink(26),
		processLink(27),
		setTypeLink(28),
		signatureLink(29),
		terminatesLink(30),
		terminatesTypeLink(31),
		trueTypeLink(32),
		tupleTypeLink(33),
		typeLink(34),
		voidTypeLink(35),
		emptyTupleLink(36),
		underscoreTupleLink(37),
		emptySetLink(38),
		emptyMapLink(39),
		positiveInfinityLink(40),
		negativeInfinityLink(41),
		bytesRangeLink(42),
		nybblesRangeLink(43),
		trueObjectLink(44),
		falseObjectLink(45),
		voidObjectLink(46),
		blankObjectLink(47),
		headOfChunkRingLink(48),
		levelTwoChunksLink(49),
		characterLink(50),
		numLinks(51);

		LinkNames (int ordinalCheck)
		{
			assert (ordinalCheck == ordinal());
		};
	}

	static final AbstractMap<LinkNames, AvailObject> LinkObjects =
		new EnumMap<LinkNames, AvailObject> (LinkNames.class);

	/* Arithmetic utilities (until a refactor) */
	static int bitShift (int value, int leftShift)
	{
		// Note:  This is an arithmetic shift without an implicit modulus on the shift amount.
		if (leftShift >= 32) return 0;
		if (leftShift >= 0) return value << leftShift;
		if (leftShift > -32) return value >>> -leftShift;
		return 0;
	}

	//  Explicit default constructor to avoid mild warnings about member initialization
	Descriptor ()
	{
		_myId = -1;
		_isMutable = false;
	};

	//  Descriptor initialization
	Descriptor initDescriptor(
			int theId,
			boolean mut,
			int numFixedObjectSlots,
			int numFixedIntegerSlots,
			boolean hasVariableObjectSlots,
			boolean hasVariableIntegerSlots)
	{
		_myId = (short)theId;
		_isMutable = mut;
		_numberOfFixedObjectSlots = numFixedObjectSlots;
		_numberOfFixedIntegerSlots = numFixedIntegerSlots;
		_hasVariableObjectSlots = hasVariableObjectSlots;
		_hasVariableIntegerSlots = hasVariableIntegerSlots;
		return this;
	};

}
