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
import com.avail.compiler.Continuation1;
import com.avail.compiler.Generator;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.AvailInterpreter;
import com.avail.visitor.AvailBeImmutableSubobjectVisitor;
import com.avail.visitor.AvailSubobjectVisitor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

public abstract class Descriptor extends AbstractDescriptor
{
	
	/**
	 * Construct a new {@link Descriptor}.
	 *
	 * @param isMutable
	 */
	public Descriptor (boolean isMutable)
	{
		super(isMutable);
	}


	public void subclassResponsibility(Object... args)
	{
		error(args);
	}
	
	@Override
	public boolean ObjectAcceptsArgTypesFromClosureType (
			final AvailObject object,
			final AvailObject closureType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:acceptsArgTypesFromClosureType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	@Override
	public boolean ObjectAcceptsArgumentsFromContinuationStackp (
			final AvailObject object,
			final AvailObject continuation,
			final int stackp)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:acceptsArgumentsFromContinuation:stackp:", object);
		return false;
	}

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	@Override
	public boolean ObjectAcceptsArgumentTypesFromContinuationStackp (
			final AvailObject object,
			final AvailObject continuation,
			final int stackp)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:acceptsArgumentTypesFromContinuation:stackp:", object);
		return false;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public boolean ObjectAcceptsArrayOfArgTypes (
			final AvailObject object,
			final List<AvailObject> argTypes)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:acceptsArrayOfArgTypes:", object);
		return false;
	}

	/**
	 * @param object
	 * @param argValues
	 * @return
	 */
	@Override
	public boolean ObjectAcceptsArrayOfArgValues (
			final AvailObject object,
			final List<AvailObject> argValues)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:acceptsArrayOfArgValues:", object);
		return false;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public boolean ObjectAcceptsTupleOfArgTypes (
			final AvailObject object,
			final AvailObject argTypes)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:acceptsTupleOfArgTypes:", object);
		return false;
	}

	/**
	 * @param object
	 * @param arguments
	 * @return
	 */
	@Override
	public boolean ObjectAcceptsTupleOfArguments (
			final AvailObject object,
			final AvailObject arguments)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:acceptsTupleOfArguments:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	@Override
	public void ObjectAddDependentChunkId (
			final AvailObject object,
			final int aChunkIndex)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:addDependentChunkId:", object);
		return;
	}

	/**
	 * @param object
	 * @param implementation
	 */
	@Override
	public void ObjectAddImplementation (
			final AvailObject object,
			final AvailObject implementation)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:addImplementation:", object);
		return;
	}

	/**
	 * @param object
	 * @param restrictions
	 */
	@Override
	public void ObjectAddRestrictions (
			final AvailObject object,
			final AvailObject restrictions)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:addRestrictions:", object);
		return;
	}

	/**
	 * @param object
	 * @param anInfinity
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectAddToInfinityCanDestroy (
			final AvailObject object,
			final AvailObject anInfinity,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:addToInfinity:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectAddToIntegerCanDestroy (
			final AvailObject object,
			final AvailObject anInteger,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:addToInteger:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param args
	 * @param locals
	 * @param stack
	 * @param outers
	 * @param primitive
	 */
	@Override
	public void ObjectArgsLocalsStackOutersPrimitive (
			final AvailObject object,
			final int args,
			final int locals,
			final int stack,
			final int outers,
			final int primitive)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:args:locals:stack:outers:primitive:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectArgTypeAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:argTypeAt:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void ObjectArgTypeAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:argTypeAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	@Override
	public void ObjectAtAddMessageRestrictions (
			final AvailObject object,
			final AvailObject methodName,
			final AvailObject illegalArgMsgs)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:at:addMessageRestrictions:", object);
		return;
	}

	/**
	 * @param object
	 * @param methodName
	 * @param implementation
	 */
	@Override
	public void ObjectAtAddMethodImplementation (
			final AvailObject object,
			final AvailObject methodName,
			final AvailObject implementation)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:at:addMethodImplementation:", object);
		return;
	}

	/**
	 * @param object
	 * @param message
	 * @param bundle
	 */
	@Override
	public void ObjectAtMessageAddBundle (
			final AvailObject object,
			final AvailObject message,
			final AvailObject bundle)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:atMessage:addBundle:", object);
		return;
	}

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	@Override
	public void ObjectAtNameAdd (
			final AvailObject object,
			final AvailObject stringName,
			final AvailObject trueName)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:atName:add:", object);
		return;
	}

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	@Override
	public void ObjectAtNewNamePut (
			final AvailObject object,
			final AvailObject stringName,
			final AvailObject trueName)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:atNewName:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	@Override
	public void ObjectAtPrivateNameAdd (
			final AvailObject object,
			final AvailObject stringName,
			final AvailObject trueName)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:atPrivateName:add:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectBinElementAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:binElementAt:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void ObjectBinElementAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:binElementAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectBinHash (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:binHash:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectBinSize (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:binSize:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectBinUnionType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:binUnionType:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectBitVector (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:bitVector:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectBodyBlock (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:bodyBlock:", object);
		return;
	}

	/**
	 * @param object
	 * @param bb
	 * @param rqb
	 * @param rtb
	 */
	@Override
	public void ObjectBodyBlockRequiresBlockReturnsBlock (
			final AvailObject object,
			final AvailObject bb,
			final AvailObject rqb,
			final AvailObject rtb)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:bodyBlock:requiresBlock:returnsBlock:", object);
		return;
	}

	/**
	 * @param object
	 * @param signature
	 */
	@Override
	public void ObjectBodySignature (
			final AvailObject object,
			final AvailObject signature)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:bodySignature:", object);
		return;
	}

	/**
	 * @param object
	 * @param bs
	 * @param rqb
	 * @param rtb
	 */
	@Override
	public void ObjectBodySignatureRequiresBlockReturnsBlock (
			final AvailObject object,
			final AvailObject bs,
			final AvailObject rqb,
			final AvailObject rtb)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:bodySignature:requiresBlock:returnsBlock:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectBreakpointBlock (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:breakpointBlock:", object);
		return;
	}

	/**
	 * @param object
	 * @param bundleTree
	 */
	@Override
	public void ObjectBuildFilteredBundleTreeFrom (
			final AvailObject object,
			final AvailObject bundleTree)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:buildFilteredBundleTreeFrom:", object);
		return;
	}

	/**
	 * @param object
	 * @param message
	 * @param parts
	 * @return
	 */
	@Override
	public AvailObject ObjectBundleAtMessageParts (
			final AvailObject object,
			final AvailObject message,
			final AvailObject parts)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:bundleAtMessage:parts:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectCaller (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:caller:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectClosure (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:closure:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectClosureType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:closureType:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectCode (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:code:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectCodePoint (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:codePoint:", object);
		return;
	}

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param anotherObject
	 * @param startIndex2
	 * @return
	 */
	@Override
	public boolean ObjectCompareFromToWithStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject anotherObject,
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:compareFrom:to:with:startingAt:", object);
		return false;
	}

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aTuple
	 * @param startIndex2
	 * @return
	 */
	@Override
	public boolean ObjectCompareFromToWithAnyTupleStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aTuple,
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:compareFrom:to:withAnyTuple:startingAt:", object);
		return false;
	}

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteString
	 * @param startIndex2
	 * @return
	 */
	@Override
	public boolean ObjectCompareFromToWithByteStringStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aByteString,
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:compareFrom:to:withByteString:startingAt:", object);
		return false;
	}

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteTuple
	 * @param startIndex2
	 * @return
	 */
	@Override
	public boolean ObjectCompareFromToWithByteTupleStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aByteTuple,
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:compareFrom:to:withByteTuple:startingAt:", object);
		return false;
	}

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aNybbleTuple
	 * @param startIndex2
	 * @return
	 */
	@Override
	public boolean ObjectCompareFromToWithNybbleTupleStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aNybbleTuple,
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:compareFrom:to:withNybbleTuple:startingAt:", object);
		return false;
	}

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param anObjectTuple
	 * @param startIndex2
	 * @return
	 */
	@Override
	public boolean ObjectCompareFromToWithObjectTupleStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject anObjectTuple,
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:compareFrom:to:withObjectTuple:startingAt:", object);
		return false;
	}

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aTwoByteString
	 * @param startIndex2
	 * @return
	 */
	@Override
	public boolean ObjectCompareFromToWithTwoByteStringStartingAt (
			final AvailObject object,
			final int startIndex1,
			final int endIndex1,
			final AvailObject aTwoByteString,
			final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:compareFrom:to:withTwoByteString:startingAt:", object);
		return false;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectComplete (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:complete:", object);
		return;
	}

	/**
	 * @param object
	 * @param start
	 * @param end
	 * @return
	 */
	@Override
	public int ObjectComputeHashFromTo (
			final AvailObject object,
			final int start,
			final int end)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:computeHashFrom:to:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @param anAvailInterpreter
	 * @return
	 */
	@Override
	public AvailObject ObjectComputeReturnTypeFromArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final AvailInterpreter anAvailInterpreter)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:computeReturnTypeFromArgumentTypes:interpreter:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectConcatenateTuplesCanDestroy (
			final AvailObject object,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:concatenateTuplesCanDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectConstantBindings (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:constantBindings:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectContentType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:contentType:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectContingentImpSets (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:contingentImpSets:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectContinuation (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:continuation:", object);
		return;
	}

	/**
	 * @param object
	 * @param filteredBundleTree
	 * @param visibleNames
	 */
	@Override
	public void ObjectCopyToRestrictedTo (
			final AvailObject object,
			final AvailObject filteredBundleTree,
			final AvailObject visibleNames)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:copyTo:restrictedTo:", object);
		return;
	}

	/**
	 * @param object
	 * @param start
	 * @param end
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectCopyTupleFromToCanDestroy (
			final AvailObject object,
			final int start,
			final int end,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:copyTupleFrom:to:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public boolean ObjectCouldEverBeInvokedWith (
			final AvailObject object,
			final ArrayList<AvailObject> argTypes)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:couldEverBeInvokedWith:", object);
		return false;
	}

	/**
	 * @param object
	 * @param positiveTuple
	 * @param possibilities
	 * @return
	 */
	@Override
	public AvailObject ObjectCreateTestingTreeWithPositiveMatchesRemainingPossibilities (
			final AvailObject object,
			final AvailObject positiveTuple,
			final AvailObject possibilities)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:createTestingTreeWithPositiveMatches:remainingPossibilities:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectDataAtIndex (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:dataAtIndex:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void ObjectDataAtIndexPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:dataAtIndex:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectDefaultType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:defaultType:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectDependentChunks (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:dependentChunks:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectDepth (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:depth:", object);
		return;
	}

	/**
	 * @param object
	 * @param aNumber
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectDivideCanDestroy (
			final AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:divide:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anInfinity
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectDivideIntoInfinityCanDestroy (
			final AvailObject object,
			final AvailObject anInfinity,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:divideIntoInfinity:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectDivideIntoIntegerCanDestroy (
			final AvailObject object,
			final AvailObject anInteger,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:divideIntoInteger:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectElementAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:elementAt:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void ObjectElementAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:elementAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public int ObjectEndOfZone (
			final AvailObject object,
			final int zone)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:endOfZone:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public int ObjectEndSubtupleIndexInZone (
			final AvailObject object,
			final int zone)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:endSubtupleIndexInZone:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectExecutionMode (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:executionMode:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectExecutionState (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:executionState:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public byte ObjectExtractNybbleFromTupleAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:extractNybbleFromTupleAt:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectFieldMap (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:fieldMap:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectFieldTypeMap (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:fieldTypeMap:", object);
		return;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public List<AvailObject> ObjectFilterByTypes (
			final AvailObject object,
			final List<AvailObject> argTypes)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:filterByTypes:", object);
		return null;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectFilteredBundleTree (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:filteredBundleTree:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectFirstTupleType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:firstTupleType:", object);
		return;
	}

	/**
	 * @param object
	 * @param zone
	 * @param newSubtuple
	 * @param startSubtupleIndex
	 * @param endOfZone
	 * @return
	 */
	@Override
	public AvailObject ObjectForZoneSetSubtupleStartSubtupleIndexEndOfZone (
			final AvailObject object,
			final int zone,
			final AvailObject newSubtuple,
			final int startSubtupleIndex,
			final int endOfZone)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:forZone:setSubtuple:startSubtupleIndex:endOfZone:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean ObjectGreaterThanInteger (
			final AvailObject object,
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:greaterThanInteger:", object);
		return false;
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean ObjectGreaterThanSignedInfinity (
			final AvailObject object,
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:greaterThanSignedInfinity:", object);
		return false;
	}

	/**
	 * @param object
	 * @param elementObject
	 * @return
	 */
	@Override
	public boolean ObjectHasElement (
			final AvailObject object,
			final AvailObject elementObject)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:hasElement:", object);
		return false;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectHash (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:hash:", object);
		return;
	}

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	@Override
	public int ObjectHashFromTo (
			final AvailObject object,
			final int startIndex,
			final int endIndex)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:hashFrom:to:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectHashOrZero (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:hashOrZero:", object);
		return;
	}

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	@Override
	public boolean ObjectHasKey (
			final AvailObject object,
			final AvailObject keyObject)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:hasKey:", object);
		return false;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectHiLevelTwoChunkLowOffset (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:hiLevelTwoChunkLowOffset:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectHiNumLocalsLowNumArgs (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:hiNumLocalsLowNumArgs:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectHiPrimitiveLowNumArgsAndLocalsAndStack (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:hiPrimitiveLowNumArgsAndLocalsAndStack:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectHiStartingChunkIndexLowNumOuters (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:hiStartingChunkIndexLowNumOuters:", object);
		return;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public ArrayList<AvailObject> ObjectImplementationsAtOrBelow (
			final AvailObject object,
			final ArrayList<AvailObject> argTypes)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:implementationsAtOrBelow:", object);
		return null;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectImplementationsTuple (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:implementationsTuple:", object);
		return;
	}

	/**
	 * @param object
	 * @param message
	 * @param parts
	 * @return
	 */
	@Override
	public AvailObject ObjectIncludeBundleAtMessageParts (
			final AvailObject object,
			final AvailObject message,
			final AvailObject parts)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:includeBundleAtMessage:parts:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param imp
	 * @return
	 */
	@Override
	public boolean ObjectIncludes (
			final AvailObject object,
			final AvailObject imp)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:includes:", object);
		return false;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectInclusiveFlags (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:inclusiveFlags:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectIncomplete (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:incomplete:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectIndex (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:index:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectInnerType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:innerType:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectInstance (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:instance:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectInternalHash (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:internalHash:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectInterruptRequestFlag (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:interruptRequestFlag:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectInvocationCount (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:invocationCount:", object);
		return;
	}

	/**
	 * @param object
	 * @param aBoolean
	 */
	@Override
	public void ObjectIsSaved (
			final AvailObject object,
			final boolean aBoolean)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSaved:", object);
		return;
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean ObjectIsSubsetOf (
			final AvailObject object,
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSubsetOf:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	@Override
	public boolean ObjectIsSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSubtypeOf:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfClosureType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfContainerType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfContinuationType (
			final AvailObject object,
			final AvailObject aContinuationType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfContinuationType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aCyclicType
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfCyclicType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfGeneralizedClosureType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfIntegerRangeType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aListType
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfListType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfMapType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param anObjectMeta
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfObjectMeta:", object);
		return false;
	}

	/**
	 * @param object
	 * @param anObjectMetaMeta
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfObjectMetaMeta (
			final AvailObject object,
			final AvailObject anObjectMetaMeta)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfObjectMetaMeta:", object);
		return false;
	}

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfObjectType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aPrimitiveType
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfPrimitiveType (
			final AvailObject object,
			final AvailObject aPrimitiveType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfPrimitiveType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfSetType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isSupertypeOfTupleType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aBoolean
	 */
	@Override
	public void ObjectIsValid (
			final AvailObject object,
			final boolean aBoolean)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isValid:", object);
		return;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @param interpreter
	 * @return
	 */
	@Override
	public boolean ObjectIsValidForArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final AvailInterpreter interpreter)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:isValidForArgumentTypes:interpreter:", object);
		return false;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectKeyAtIndex (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:keyAtIndex:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param keyObject
	 */
	@Override
	public void ObjectKeyAtIndexPut (
			final AvailObject object,
			final int index,
			final AvailObject keyObject)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:keyAtIndex:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectKeyType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:keyType:", object);
		return;
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean ObjectLessOrEqual (
			final AvailObject object,
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:lessOrEqual:", object);
		return false;
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean ObjectLessThan (
			final AvailObject object,
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:lessThan:", object);
		return false;
	}

	/**
	 * @param object
	 * @param index
	 * @param offset
	 */
	@Override
	public void ObjectLevelTwoChunkIndexOffset (
			final AvailObject object,
			final int index,
			final int offset)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:levelTwoChunkIndex:offset:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectLiteralAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:literalAt:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void ObjectLiteralAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:literalAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectLocalOrArgOrStackAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:localOrArgOrStackAt:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void ObjectLocalOrArgOrStackAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:localOrArgOrStackAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectLocalTypeAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:localTypeAt:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param argumentTypeArray
	 * @return
	 */
	@Override
	public AvailObject ObjectLookupByTypesFromArray (
			final AvailObject object,
			final List<AvailObject> argumentTypeArray)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:lookupByTypesFromArray:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	@Override
	public AvailObject ObjectLookupByTypesFromContinuationStackp (
			final AvailObject object,
			final AvailObject continuation,
			final int stackp)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:lookupByTypesFromContinuation:stackp:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param argumentTypeTuple
	 * @return
	 */
	@Override
	public AvailObject ObjectLookupByTypesFromTuple (
			final AvailObject object,
			final AvailObject argumentTypeTuple)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:lookupByTypesFromTuple:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param argumentArray
	 * @return
	 */
	@Override
	public AvailObject ObjectLookupByValuesFromArray (
			final AvailObject object,
			final List<AvailObject> argumentArray)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:lookupByValuesFromArray:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	@Override
	public AvailObject ObjectLookupByValuesFromContinuationStackp (
			final AvailObject object,
			final AvailObject continuation,
			final int stackp)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:lookupByValuesFromContinuation:stackp:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param argumentTuple
	 * @return
	 */
	@Override
	public AvailObject ObjectLookupByValuesFromTuple (
			final AvailObject object,
			final AvailObject argumentTuple)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:lookupByValuesFromTuple:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectLowerBound (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:lowerBound:", object);
		return;
	}

	/**
	 * @param object
	 * @param lowInc
	 * @param highInc
	 */
	@Override
	public void ObjectLowerInclusiveUpperInclusive (
			final AvailObject object,
			final boolean lowInc,
			final boolean highInc)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:lowerInclusive:upperInclusive:", object);
		return;
	}

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	@Override
	public AvailObject ObjectMapAt (
			final AvailObject object,
			final AvailObject keyObject)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:mapAt:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param keyObject
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectMapAtPuttingCanDestroy (
			final AvailObject object,
			final AvailObject keyObject,
			final AvailObject newValueObject,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:mapAt:putting:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectMapSize (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:mapSize:", object);
		return;
	}

	/**
	 * @param object
	 * @param keyObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectMapWithoutKeyCanDestroy (
			final AvailObject object,
			final AvailObject keyObject,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:mapWithoutKey:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectMessage (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:message:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectMessageParts (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:messageParts:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectMethods (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:methods:", object);
		return;
	}

	/**
	 * @param object
	 * @param aNumber
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectMinusCanDestroy (
			final AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:minus:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anInfinity
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectMultiplyByInfinityCanDestroy (
			final AvailObject object,
			final AvailObject anInfinity,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:multiplyByInfinity:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectMultiplyByIntegerCanDestroy (
			final AvailObject object,
			final AvailObject anInteger,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:multiplyByInteger:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectMyObjectMeta (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:myObjectMeta:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectMyObjectType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:myObjectType:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectMyRestrictions (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:myRestrictions:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectMyType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:myType:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectName (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:name:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectNames (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:names:", object);
		return;
	}

	/**
	 * @param object
	 * @param trueName
	 * @return
	 */
	@Override
	public boolean ObjectNameVisible (
			final AvailObject object,
			final AvailObject trueName)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:nameVisible:", object);
		return false;
	}

	/**
	 * @param object
	 * @param anImplementationSet
	 */
	@Override
	public void ObjectNecessaryImplementationSetChanged (
			final AvailObject object,
			final AvailObject anImplementationSet)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:necessaryImplementationSetChanged:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectNewNames (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:newNames:", object);
		return;
	}

	/**
	 * @param object
	 * @param nextChunk
	 */
	@Override
	public void ObjectNext (
			final AvailObject object,
			final AvailObject nextChunk)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:next:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectNextIndex (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:nextIndex:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectNumBlanks (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:numBlanks:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectNumFloats (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:numFloats:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectNumIntegers (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:numIntegers:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectNumObjects (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:numObjects:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectNybbles (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:nybbles:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public boolean ObjectOptionallyNilOuterVar (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:optionallyNilOuterVar:", object);
		return false;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectOuterTypeAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:outerTypeAt:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param tupleOfOuterTypes
	 * @param tupleOfLocalContainerTypes
	 */
	@Override
	public void ObjectOuterTypesLocalTypes (
			final AvailObject object,
			final AvailObject tupleOfOuterTypes,
			final AvailObject tupleOfLocalContainerTypes)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:outerTypes:localTypes:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectOuterVarAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:outerVarAt:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void ObjectOuterVarAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:outerVarAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectPad1 (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:pad1:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectPad2 (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:pad2:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectParent (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:parent:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectPc (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:pc:", object);
		return;
	}

	/**
	 * @param object
	 * @param aNumber
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectPlusCanDestroy (
			final AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:plus:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param previousChunk
	 */
	@Override
	public void ObjectPrevious (
			final AvailObject object,
			final AvailObject previousChunk)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:previous:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectPreviousIndex (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:previousIndex:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectPriority (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:priority:", object);
		return;
	}

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	@Override
	public AvailObject ObjectPrivateAddElement (
			final AvailObject object,
			final AvailObject element)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:privateAddElement:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	@Override
	public AvailObject ObjectPrivateExcludeElement (
			final AvailObject object,
			final AvailObject element)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:privateExcludeElement:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param element
	 * @param knownIndex
	 * @return
	 */
	@Override
	public AvailObject ObjectPrivateExcludeElementKnownIndex (
			final AvailObject object,
			final AvailObject element,
			final int knownIndex)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:privateExcludeElement:knownIndex:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	@Override
	public AvailObject ObjectPrivateExcludeKey (
			final AvailObject object,
			final AvailObject keyObject)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:privateExcludeKey:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param keyObject
	 * @param valueObject
	 * @return
	 */
	@Override
	public AvailObject ObjectPrivateMapAtPut (
			final AvailObject object,
			final AvailObject keyObject,
			final AvailObject valueObject)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:privateMapAt:put:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectPrivateNames (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:privateNames:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectPrivateTestingTree (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:privateTestingTree:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectProcessGlobals (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:processGlobals:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public short ObjectRawByteAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawByteAt:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	@Override
	public void ObjectRawByteAtPut (
			final AvailObject object,
			final int index,
			final short anInteger)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawByteAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public short ObjectRawByteForCharacterAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawByteForCharacterAt:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	@Override
	public void ObjectRawByteForCharacterAtPut (
			final AvailObject object,
			final int index,
			final short anInteger)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawByteForCharacterAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public byte ObjectRawNybbleAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawNybbleAt:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param aNybble
	 */
	@Override
	public void ObjectRawNybbleAtPut (
			final AvailObject object,
			final int index,
			final byte aNybble)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawNybbleAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectRawQuad1 (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawQuad1:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectRawQuad2 (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawQuad2:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public int ObjectRawQuadAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawQuadAt:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void ObjectRawQuadAtPut (
			final AvailObject object,
			final int index,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawQuadAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public short ObjectRawShortForCharacterAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawShortForCharacterAt:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	@Override
	public void ObjectRawShortForCharacterAtPut (
			final AvailObject object,
			final int index,
			final short anInteger)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawShortForCharacterAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public int ObjectRawSignedIntegerAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawSignedIntegerAt:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void ObjectRawSignedIntegerAtPut (
			final AvailObject object,
			final int index,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawSignedIntegerAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public long ObjectRawUnsignedIntegerAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawUnsignedIntegerAt:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void ObjectRawUnsignedIntegerAtPut (
			final AvailObject object,
			final int index,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rawUnsignedIntegerAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	@Override
	public void ObjectRemoveDependentChunkId (
			final AvailObject object,
			final int aChunkIndex)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:removeDependentChunkId:", object);
		return;
	}

	/**
	 * @param object
	 * @param anInterpreter
	 */
	@Override
	public void ObjectRemoveFrom (
		final AvailObject object,
		final AvailInterpreter anInterpreter)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:removeFrom:", object);
		return;
	}

	/**
	 * @param object
	 * @param implementation
	 */
	@Override
	public void ObjectRemoveImplementation (
			final AvailObject object,
			final AvailObject implementation)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:removeImplementation:", object);
		return;
	}

	/**
	 * @param object
	 * @param message
	 * @param parts
	 * @return
	 */
	@Override
	public boolean ObjectRemoveMessageParts (
		final AvailObject object,
		final AvailObject message,
		final AvailObject parts)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:removeMessage:parts:", object);
		return false;
	}

	/**
	 * @param object
	 * @param obsoleteRestrictions
	 */
	@Override
	public void ObjectRemoveRestrictions (
		final AvailObject object,
		final AvailObject obsoleteRestrictions)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:removeRestrictions:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectRequiresBlock (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:requiresBlock:", object);
		return;
	}

	/**
	 * @param object
	 * @param forwardImplementation
	 * @param methodName
	 */
	@Override
	public void ObjectResolvedForwardWithName (
			final AvailObject object,
			final AvailObject forwardImplementation,
			final AvailObject methodName)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:resolvedForward:withName:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectRestrictions (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:restrictions:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectReturnsBlock (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:returnsBlock:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectReturnType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:returnType:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectRootBin (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:rootBin:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectSecondTupleType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:secondTupleType:", object);
		return;
	}

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectSetIntersectionCanDestroy (
			final AvailObject object,
			final AvailObject otherSet,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:setIntersection:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectSetMinusCanDestroy (
			final AvailObject object,
			final AvailObject otherSet,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:setMinus:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectSetSize (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:setSize:", object);
		return;
	}

	/**
	 * @param object
	 * @param zoneIndex
	 * @param newTuple
	 */
	@Override
	public void ObjectSetSubtupleForZoneTo (
			final AvailObject object,
			final int zoneIndex,
			final AvailObject newTuple)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:setSubtupleForZone:to:", object);
		return;
	}

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectSetUnionCanDestroy (
			final AvailObject object,
			final AvailObject otherSet,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:setUnion:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param newValue
	 */
	@Override
	public void ObjectSetValue (
			final AvailObject object,
			final AvailObject newValue)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:setValue:", object);
		return;
	}

	/**
	 * @param object
	 * @param newElementObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectSetWithElementCanDestroy (
			final AvailObject object,
			final AvailObject newElementObject,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:setWithElement:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param elementObjectToExclude
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectSetWithoutElementCanDestroy (
			final AvailObject object,
			final AvailObject elementObjectToExclude,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:setWithoutElement:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectSignature (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:signature:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectSize (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:size:", object);
		return;
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public int ObjectSizeOfZone (
			final AvailObject object,
			final int zone)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:sizeOfZone:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectSizeRange (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:sizeRange:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectSpecialActions (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:specialActions:", object);
		return;
	}

	/**
	 * @param object
	 * @param slotIndex
	 * @return
	 */
	@Override
	public AvailObject ObjectStackAt (
			final AvailObject object,
			final int slotIndex)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:stackAt:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param slotIndex
	 * @param anObject
	 */
	@Override
	public void ObjectStackAtPut (
			final AvailObject object,
			final int slotIndex,
			final AvailObject anObject)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:stackAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectStackp (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:stackp:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectStartingChunkIndex (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:startingChunkIndex:", object);
		return;
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public int ObjectStartOfZone (
			final AvailObject object,
			final int zone)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:startOfZone:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public int ObjectStartSubtupleIndexInZone (
			final AvailObject object,
			final int zone)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:startSubtupleIndexInZone:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param anInfinity
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectSubtractFromInfinityCanDestroy (
			final AvailObject object,
			final AvailObject anInfinity,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:subtractFromInfinity:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectSubtractFromIntegerCanDestroy (
			final AvailObject object,
			final AvailObject anInteger,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:subtractFromInteger:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public AvailObject ObjectSubtupleForZone (
			final AvailObject object,
			final int zone)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:subtupleForZone:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aNumber
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectTimesCanDestroy (
			final AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:times:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param tupleIndex
	 * @param zoneIndex
	 * @return
	 */
	@Override
	public int ObjectTranslateToZone (
			final AvailObject object,
			final int tupleIndex,
			final int zoneIndex)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:translate:toZone:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param stringName
	 * @return
	 */
	@Override
	public AvailObject ObjectTrueNamesForStringName (
			final AvailObject object,
			final AvailObject stringName)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:trueNamesForStringName:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param newTupleSize
	 * @return
	 */
	@Override
	public AvailObject ObjectTruncateTo (
			final AvailObject object,
			final int newTupleSize)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:truncateTo:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectTuple (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:tuple:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectTupleAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:tupleAt:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param aNybbleObject
	 */
	@Override
	public void ObjectTupleAtPut (
			final AvailObject object,
			final int index,
			final AvailObject aNybbleObject)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:tupleAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectTupleAtPuttingCanDestroy (
			final AvailObject object,
			final int index,
			final AvailObject newValueObject,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:tupleAt:putting:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public int ObjectTupleIntAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:tupleIntAt:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectTupleType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:tupleType:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:type:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeAtIndex (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeAtIndex:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersection (
			final AvailObject object,
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersection:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfClosureType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aClosureType
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfClosureTypeCanDestroy (
			final AvailObject object,
			final AvailObject aClosureType,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfClosureType:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfContainerType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfContinuationType (
			final AvailObject object,
			final AvailObject aContinuationType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfContinuationType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aCyclicType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfCyclicType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfGeneralizedClosureType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfGeneralizedClosureTypeCanDestroy (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfGeneralizedClosureType:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfIntegerRangeType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aListType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfListType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfMapType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param someMeta
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfMeta (
			final AvailObject object,
			final AvailObject someMeta)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfMeta:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anObjectMeta
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfObjectMeta:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anObjectMetaMeta
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfObjectMetaMeta (
			final AvailObject object,
			final AvailObject anObjectMetaMeta)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfObjectMetaMeta:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfObjectType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfSetType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeIntersectionOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeIntersectionOfTupleType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectTypeTuple (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeTuple:", object);
		return;
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnion (
			final AvailObject object,
			final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnion:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfClosureType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aClosureType
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfClosureTypeCanDestroy (
			final AvailObject object,
			final AvailObject aClosureType,
			final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfClosureType:canDestroy:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfContainerType (
			final AvailObject object,
			final AvailObject aContainerType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfContainerType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfContinuationType (
			final AvailObject object,
			final AvailObject aContinuationType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfContinuationType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aCyclicType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfCyclicType (
			final AvailObject object,
			final AvailObject aCyclicType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfCyclicType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfGeneralizedClosureType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfIntegerRangeType (
			final AvailObject object,
			final AvailObject anIntegerRangeType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfIntegerRangeType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aListType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfListType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfMapType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anObjectMeta
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfObjectMeta (
			final AvailObject object,
			final AvailObject anObjectMeta)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfObjectMeta:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anObjectMetaMeta
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfObjectMetaMeta (
			final AvailObject object,
			final AvailObject anObjectMetaMeta)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfObjectMetaMeta:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfObjectType (
			final AvailObject object,
			final AvailObject anObjectType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfObjectType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfSetType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeUnionOfTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:typeUnionOfTupleType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectUnclassified (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:unclassified:", object);
		return;
	}

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	@Override
	public AvailObject ObjectUnionOfTypesAtThrough (
			final AvailObject object,
			final int startIndex,
			final int endIndex)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:unionOfTypesAt:through:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public int ObjectUntranslatedDataAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:untranslatedDataAt:", object);
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void ObjectUntranslatedDataAtPut (
			final AvailObject object,
			final int index,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:untranslatedDataAt:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectUpperBound (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:upperBound:", object);
		return;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @param anAvailInterpreter
	 * @param failBlock
	 * @return
	 */
	@Override
	public AvailObject ObjectValidateArgumentTypesInterpreterIfFail (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final AvailInterpreter anAvailInterpreter,
			final Continuation1<Generator<String>> failBlock)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:validateArgumentTypes:interpreter:ifFail:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectValidity (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:validity:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectValue (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:value:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public AvailObject ObjectValueAtIndex (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:valueAtIndex:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param valueObject
	 */
	@Override
	public void ObjectValueAtIndexPut (
			final AvailObject object,
			final int index,
			final AvailObject valueObject)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:valueAtIndex:put:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectValueType (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:valueType:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectVariableBindings (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:variableBindings:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectVectors (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:vectors:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectVisibleNames (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:visibleNames:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectWhichOne (
			final AvailObject object,
			final int value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:whichOne:", object);
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectWordcodes (
			final AvailObject object,
			final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:wordcodes:", object);
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public int ObjectZoneForIndex (
			final AvailObject object,
			final int index)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("Object:zoneForIndex:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public String ObjectAsNativeString (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectAsNativeString:", object);
		return "";
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectAsObject (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectAsObject:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectAsSet (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectAsSet:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectAsTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectAsTuple:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectBecomeExactType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectBecomeExactType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectBecomeRealTupleType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectBecomeRealTupleType:", object);
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectBitsPerEntry (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectBitsPerEntry:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectBitVector (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectBitVector:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectBodyBlock (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectBodyBlock:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectBodySignature (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectBodySignature:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectBreakpointBlock (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectBreakpointBlock:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectCaller (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectCaller:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectCapacity (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectCapacity:", object);
		return 0;
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectCleanUpAfterCompile (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectCleanUpAfterCompile:", object);
		return;
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectClearModule (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectClearModule:", object);
		return;
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectClearValue (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectClearValue:", object);
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectClosure (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectClosure:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectClosureType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectClosureType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectCode (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectCode:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectCodePoint (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectCodePoint:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectComplete (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectComplete:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectConstantBindings (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectConstantBindings:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectContentType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectContentType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectContingentImpSets (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectContingentImpSets:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectContinuation (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectContinuation:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectCopyAsMutableContinuation (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectCopyAsMutableContinuation:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectCopyAsMutableObjectTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectCopyAsMutableObjectTuple:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectCopyAsMutableSpliceTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectCopyAsMutableSpliceTuple:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectCopyMutable (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectCopyMutable:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectDefaultType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectDefaultType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectDependentChunks (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectDependentChunks:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectDepth (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectDepth:", object);
		return 0;
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectDisplayTestingTree (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectDisplayTestingTree:", object);
		return;
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectEnsureMetacovariant (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectEnsureMetacovariant:", object);
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectEnsureMutable (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectEnsureMutable:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectEvictedByGarbageCollector (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectEvictedByGarbageCollector:", object);
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectExecutionMode (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectExecutionMode:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectExecutionState (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectExecutionState:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectExpand (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectExpand:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectExtractBoolean (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectExtractBoolean:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public short ObjectExtractByte (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectExtractByte:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public double ObjectExtractDouble (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectExtractDouble:", object);
		return 0.0d;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public float ObjectExtractFloat (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectExtractFloat:", object);
		return 0.0f;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectExtractInt (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectExtractInt:", object);
		return 0;
	}

	/**
	 * Extract a 64-bit signed Java {@code long} from the specified Avail
	 * {@linkplain IntegerDescriptor integer}.
	 *
	 * @param object An {@link AvailObject}.
	 * @return A 64-bit signed Java {@code long}
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	/**
	 * @param object
	 * @return
	 */
	@Override
	public long ObjectExtractLong (final @NotNull AvailObject object)
	{
		error(
			"Subclass responsiblity: ObjectExtractLong() in "
			+ getClass().getCanonicalName());
		return 0L;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public byte ObjectExtractNybble (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectExtractNybble:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectFieldMap (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectFieldMap:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectFieldTypeMap (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectFieldTypeMap:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectFilteredBundleTree (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectFilteredBundleTree:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectFirstTupleType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectFirstTupleType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectGetInteger (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectGetInteger:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectGetValue (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectGetValue:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectHashOrZero (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectHashOrZero:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectHasRestrictions (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectHasRestrictions:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectHiLevelTwoChunkLowOffset (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectHiLevelTwoChunkLowOffset:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectHiNumLocalsLowNumArgs (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectHiNumLocalsLowNumArgs:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectHiPrimitiveLowNumArgsAndLocalsAndStack (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectHiPrimitiveLowNumArgsAndLocalsAndStack:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectHiStartingChunkIndexLowNumOuters (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectHiStartingChunkIndexLowNumOuters:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectImplementationsTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectImplementationsTuple:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectInclusiveFlags (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectInclusiveFlags:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectIncomplete (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectIncomplete:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectIndex (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectIndex:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectInnerType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectInnerType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectInstance (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectInstance:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectInternalHash (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectInternalHash:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectInterruptRequestFlag (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectInterruptRequestFlag:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectInvocationCount (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectInvocationCount:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsAbstract (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectIsAbstract:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsFinite (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectIsFinite:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsForward (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectIsForward:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsImplementation (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectIsImplementation:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsPositive (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectIsPositive:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsSaved (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectIsSaved:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsSplice (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectIsSplice:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfTerminates (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectIsSupertypeOfTerminates:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsSupertypeOfVoid (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectIsSupertypeOfVoid:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsValid (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectIsValid:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public List<AvailObject> ObjectKeysAsArray (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectKeysAsArray:", object);
		return null;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectKeysAsSet (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectKeysAsSet:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectKeyType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectKeyType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectLevelTwoChunkIndex (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectLevelTwoChunkIndex:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectLevelTwoOffset (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectLevelTwoOffset:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectLowerBound (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectLowerBound:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectLowerInclusive (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectLowerInclusive:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectMapSize (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectMapSize:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public short ObjectMaxStackDepth (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectMaxStackDepth:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectMessage (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectMessage:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectMessageParts (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectMessageParts:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectMethods (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectMethods:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectMoveToHead (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectMoveToHead:", object);
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectMyObjectMeta (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectMyObjectMeta:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectMyObjectType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectMyObjectType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectMyRestrictions (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectMyRestrictions:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectMyType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectMyType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectName (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectName:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectNames (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNames:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectNewNames (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNewNames:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectNext (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNext:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectNextIndex (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNextIndex:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public short ObjectNumArgs (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNumArgs:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public short ObjectNumArgsAndLocalsAndStack (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNumArgsAndLocalsAndStack:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectNumberOfZones (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNumberOfZones:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectNumBlanks (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNumBlanks:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectNumFloats (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNumFloats:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectNumIntegers (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNumIntegers:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public short ObjectNumLiterals (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNumLiterals:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public short ObjectNumLocals (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNumLocals:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectNumLocalsOrArgsOrStack (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNumLocalsOrArgsOrStack:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectNumObjects (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNumObjects:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public short ObjectNumOuters (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNumOuters:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectNumOuterVars (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNumOuterVars:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectNybbles (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectNybbles:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectPad1 (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectPad1:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectPad2 (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectPad2:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectParent (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectParent:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectPc (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectPc:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectPrevious (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectPrevious:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectPreviousIndex (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectPreviousIndex:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public short ObjectPrimitiveNumber (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectPrimitiveNumber:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectPriority (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectPriority:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectPrivateNames (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectPrivateNames:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectPrivateTestingTree (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectPrivateTestingTree:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectProcessGlobals (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectProcessGlobals:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectRawQuad1 (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectRawQuad1:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectRawQuad2 (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectRawQuad2:", object);
		return 0;
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectReleaseVariableOrMakeContentsImmutable (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectReleaseVariableOrMakeContentsImmutable:", object);
		return;
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectRemoveFromQueue (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectRemoveFromQueue:", object);
		return;
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectRemoveRestrictions (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectRemoveRestrictions:", object);
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectRequiresBlock (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectRequiresBlock:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectRestrictions (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectRestrictions:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectReturnsBlock (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectReturnsBlock:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectReturnType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectReturnType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectRootBin (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectRootBin:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectSecondTupleType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectSecondTupleType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectSetSize (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectSetSize:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectSignature (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectSignature:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectSize (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectSize:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectSizeRange (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectSizeRange:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectSpecialActions (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectSpecialActions:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectStackp (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectStackp:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectStartingChunkIndex (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectStartingChunkIndex:", object);
		return 0;
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectStep (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectStep:", object);
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectTestingTree (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectTestingTree:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectTrimExcessLongs (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectTrimExcessLongs:", object);
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectTuple:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectTupleSize (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectTupleSize:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectTupleType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectTupleType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectTypeTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectTypeTuple:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectUnclassified (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectUnclassified:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectUpperBound (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectUpperBound:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectUpperInclusive (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectUpperInclusive:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectValidity (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectValidity:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectValue (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectValue:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectValuesAsTuple (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectValuesAsTuple:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectValueType (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectValueType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectVariableBindings (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectVariableBindings:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectVectors (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectVectors:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectVerify (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectVerify:", object);
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectVisibleNames (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectVisibleNames:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectWhichOne (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectWhichOne:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectWordcodes (
			final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		subclassResponsibility("ObjectWordcodes:", object);
		return VoidDescriptor.voidObject();
	}



	// GENERATED special mutable slots

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean ObjectEquals (
			final AvailObject object,
			final AvailObject another)
	{
		subclassResponsibility("Object:equals:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	@Override
	public boolean ObjectEqualsAnyTuple (
			final AvailObject object,
			final AvailObject aTuple)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aString
	 * @return
	 */
	@Override
	public boolean ObjectEqualsByteString (
			final AvailObject object,
			final AvailObject aString)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	@Override
	public boolean ObjectEqualsByteTuple (
			final AvailObject object,
			final AvailObject aTuple)
	{
		return false;
	}

	/**
	 * @param object
	 * @param otherCodePoint
	 * @return
	 */
	@Override
	public boolean ObjectEqualsCharacterWithCodePoint (
			final AvailObject object,
			final int otherCodePoint)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aClosure
	 * @return
	 */
	@Override
	public boolean ObjectEqualsClosure (
			final AvailObject object,
			final AvailObject aClosure)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	@Override
	public boolean ObjectEqualsClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aCompiledCode
	 * @return
	 */
	@Override
	public boolean ObjectEqualsCompiledCode (
			final AvailObject object,
			final AvailObject aCompiledCode)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aContainer
	 * @return
	 */
	@Override
	public boolean ObjectEqualsContainer (
			final AvailObject object,
			final AvailObject aContainer)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	@Override
	public boolean ObjectEqualsContainerType (
			final AvailObject object,
			final AvailObject aType)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aContinuation
	 * @return
	 */
	@Override
	public boolean ObjectEqualsContinuation (
			final AvailObject object,
			final AvailObject aContinuation)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	@Override
	public boolean ObjectEqualsContinuationType (
			final AvailObject object,
			final AvailObject aType)
	{
		//  GENERATED pure (abstract) method.

		return false;
	}

	/**
	 * @param object
	 * @param aDoubleObject
	 * @return
	 */
	@Override
	public boolean ObjectEqualsDouble (
			final AvailObject object,
			final AvailObject aDoubleObject)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aFloatObject
	 * @return
	 */
	@Override
	public boolean ObjectEqualsFloat (
			final AvailObject object,
			final AvailObject aFloatObject)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	@Override
	public boolean ObjectEqualsGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		return false;
	}

	/**
	 * @param object
	 * @param anInfinity
	 * @return
	 */
	@Override
	public boolean ObjectEqualsInfinity (
			final AvailObject object,
			final AvailObject anInfinity)
	{
		return false;
	}

	/**
	 * @param object
	 * @param anAvailInteger
	 * @return
	 */
	@Override
	public boolean ObjectEqualsInteger (
			final AvailObject object,
			final AvailObject anAvailInteger)
	{
		return false;
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean ObjectEqualsIntegerRangeType (
			final AvailObject object,
			final AvailObject another)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aList
	 * @return
	 */
	@Override
	public boolean ObjectEqualsList (
			final AvailObject object,
			final AvailObject aList)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aListType
	 * @return
	 */
	@Override
	public boolean ObjectEqualsListType (
			final AvailObject object,
			final AvailObject aListType)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aMap
	 * @return
	 */
	@Override
	public boolean ObjectEqualsMap (
			final AvailObject object,
			final AvailObject aMap)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	@Override
	public boolean ObjectEqualsMapType (
			final AvailObject object,
			final AvailObject aMapType)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	@Override
	public boolean ObjectEqualsNybbleTuple (
			final AvailObject object,
			final AvailObject aTuple)
	{
		return false;
	}

	/**
	 * @param object
	 * @param anObject
	 * @return
	 */
	@Override
	public boolean ObjectEqualsObject (
			final AvailObject object,
			final AvailObject anObject)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	@Override
	public boolean ObjectEqualsObjectTuple (
			final AvailObject object,
			final AvailObject aTuple)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	@Override
	public boolean ObjectEqualsPrimitiveType (
			final AvailObject object,
			final AvailObject aType)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aSet
	 * @return
	 */
	@Override
	public boolean ObjectEqualsSet (
			final AvailObject object,
			final AvailObject aSet)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	@Override
	public boolean ObjectEqualsSetType (
			final AvailObject object,
			final AvailObject aSetType)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	@Override
	public boolean ObjectEqualsTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aString
	 * @return
	 */
	@Override
	public boolean ObjectEqualsTwoByteString (
			final AvailObject object,
			final AvailObject aString)
	{
		return false;
	}

	/**
	 * @param object
	 * @param potentialInstance
	 * @return
	 */
	@Override
	public boolean ObjectHasObjectInstance (
			final AvailObject object,
			final AvailObject potentialInstance)
	{
		//  The potentialInstance is a user-defined object.  See if it is an instance of me.

		return false;
	}

	/**
	 * @param object
	 * @param anotherObject
	 * @return
	 */
	@Override
	public boolean ObjectIsBetterRepresentationThan (
			final AvailObject object,
			final AvailObject anotherObject)
	{
		//  Given two objects that are known to be equal, is the first one in a better form (more
		//  compact, more efficient, older generation) than the second one?

		return ((object.objectSlotsCount() + object.integerSlotsCount()) < (anotherObject.objectSlotsCount() + anotherObject.integerSlotsCount()));
	}

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	@Override
	public boolean ObjectIsBetterRepresentationThanTupleType (
			final AvailObject object,
			final AvailObject aTupleType)
	{
		//  Given two objects that are known to be equal, the second of which is in the form of
		//  a tuple type, is the first one in a better form than the second one?

		//  Explanation: This must be called with a tuple type as the second argument, but
		//  the two arguments must also be equal.  All alternative implementations of tuple
		//  types should reimplement this method.
		subclassResponsibility("Object:isBetterRepresentationThanTupleType:", object);
		return false;
	}

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	@Override
	public boolean ObjectIsInstanceOfSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Answer whether object is an instance of a subtype of aType.  Don't generate
		//  an approximate type and do the comparison, because the approximate type
		//  will just send this message recursively.

		return object.exactType().isSubtypeOf(aType);
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectCanComputeHashOfType (
			final AvailObject object)
	{
		//  Answer whether object supports the #hashOfType protocol.

		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectEqualsBlank (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectEqualsFalse (
			final AvailObject object)
	{
		//  Answer true if this is the Avail false object, which it isn't.

		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectEqualsTrue (
			final AvailObject object)
	{
		//  Answer true if this is the Avail true object, which it isn't.

		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectEqualsVoid (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectEqualsVoidOrBlank (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		subclassResponsibility("ObjectExactType:", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit long that is always the same for equal objects, but
		//  statistically different for different objects.

		subclassResponsibility("ObjectHash:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsClosure (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		return true;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.  If I was mutable I have to scan
		//  my children and make them immutable as well (recursively down to immutable descendants).

		if (isMutable)
		{
			object.descriptorId((short)(object.descriptorId() | 1));
			object.makeSubobjectsImmutable();
		}
		return object;
	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectMakeSubobjectsImmutable (
			final AvailObject object)
	{
		//  Make my subobjects be immutable.  Don't change my own mutability state.
		//  Also, ignore my mutability state, as it should be tested (and sometimes set
		//  preemptively to immutable) prior to invoking this method.

		object.scanSubobjects(new AvailBeImmutableSubobjectVisitor());
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		subclassResponsibility("ObjectType:", object);
		return VoidDescriptor.voidObject();
	}



	// operations-booleans

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsBoolean (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * Is the specified {@link AvailObject} an Avail byte tuple?
	 *
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is a byte tuple, {@code false}
	 *         otherwise.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsByteTuple (final @NotNull AvailObject object)
	{
		return false;
	}

	// operations-characters

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsCharacter (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * Is the specified {@link AvailObject} an Avail string?
	 *
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is an Avail string, {@code false}
	 *         otherwise.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsString (final @NotNull AvailObject object)
	{
		return false;
	}


	// operations-closure

	/**
	 * @param object
	 * @param aClosure
	 * @return
	 */
	@Override
	public boolean ObjectContainsBlock (
			final AvailObject object,
			final AvailObject aClosure)
	{
		//  Answer true if either I am aClosure or I contain aClosure.  I only follow
		//  the trail of literal compiledCode and closure objects, so this is a dead end.

		return false;
	}



	// operations-faulting

	/**
	 * @param object
	 */
	@Override
	public void ObjectPostFault (
			final AvailObject object)
	{
		//  The object was just scanned, and its pointers converted into valid ToSpace pointers.
		//  Do any follow-up activities specific to the kind of object it is.
		//
		//  do nothing


	}

	/**
	 * @param object
	 */
	@Override
	public void ObjectReadBarrierFault (
			final AvailObject object)
	{
		//  The object is in ToSpace, and its fields already refer to ToSpace objects.  Do nothing,
		//  as there is no read barrier.  See also implementation in GCReadBarrierDescriptor.
		//
		//  do nothing


	}



	// operations-indirections

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void ObjectTarget (
			final AvailObject object,
			final AvailObject value)
	{
		//  From IndirectionObjectDescriptor.  Fail if we're not an indirection object.

		error("This isn't an indirection object", object);
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectTarget (
			final AvailObject object)
	{
		//  From IndirectionObjectDescriptor.  Fail if we're not an indirection object.

		error("This isn't an indirection object", object);
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectTraversed (
			final AvailObject object)
	{
		//  Overidden in IndirectionDescriptor to skip over indirections.

		return object;
	}



	// operations-lists

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsList (
			final AvailObject object)
	{
		return false;
	}



	// operations-maps

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsMap (
			final AvailObject object)
	{
		return false;
	}



	// operations-numbers

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsByte (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsNybble (
			final AvailObject object)
	{
		return false;
	}



	// operations-set

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsSet (
			final AvailObject object)
	{
		return false;
	}



	// operations-set bins

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @param myLevel
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectBinAddingElementHashLevelCanDestroy (
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
		result.binHash(object.hash() + elementObject.hash());
		result.binElementAtPut(1, object);
		result.binElementAtPut(2, elementObject);
		if (!canDestroy)
		{
			result.makeImmutable();
		}
		return result;
	}

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @return
	 */
	@Override
	public boolean ObjectBinHasElementHash (
			final AvailObject object,
			final AvailObject elementObject,
			final int elementObjectHash)
	{
		//  Elements are treated as bins to save space, since bins are not
		//  entirely first-class objects (i.e., they can't be added to sets.

		return object.equals(elementObject);
	}

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @param canDestroy
	 * @return
	 */
	@Override
	public AvailObject ObjectBinRemoveElementHashCanDestroy (
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
		if (!canDestroy)
		{
			object.makeImmutable();
		}
		return object;
	}

	/**
	 * @param object
	 * @param potentialSuperset
	 * @return
	 */
	@Override
	public boolean ObjectIsBinSubsetOf (
			final AvailObject object,
			final AvailObject potentialSuperset)
	{
		//  Sets only use explicit bins for collisions, otherwise they store the element
		//  itself.  This works because a bin can't be an element of a set.  Likewise,
		//  the voidObject can't be a member of a set and is treated like an empty bin.

		return potentialSuperset.hasElement(object);
	}

	/**
	 * @param object
	 * @param mutableTuple
	 * @param startingIndex
	 * @return
	 */
	@Override
	public int ObjectPopulateTupleStartingAt (
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

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectBinHash (
			final AvailObject object)
	{
		//  An object masquerading as a size one bin has a bin hash which is the sum of
		//  the elements' hashes, which in this case is just the object's hash.

		return object.hash();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectBinSize (
			final AvailObject object)
	{
		//  Answer how many elements this bin contains.  I act as a bin of size one.

		return 1;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject ObjectBinUnionType (
			final AvailObject object)
	{
		//  Answer the union of the types of this bin's elements.  I act as a bin of size one.

		return object.type();
	}



	// operations-tuples

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsTuple (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	@Override
	public boolean ObjectTypeEquals (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Answer whether object's type is equal to aType (known to be a type).
		//  The current message may only be sent if the subclass receiving it has
		//  overidden ObjectCanComputeHashOfType to answer true.

		//  only provide if subclass canComputeHashOfType.
		subclassResponsibility("Object:typeEquals:", object);
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int ObjectHashOfType (
			final AvailObject object)
	{
		//  We are computing the hash value of some ApproximateType, and it has
		//  delegated responsibility back to this descriptor, the one that created the
		//  ApproximateType that we're now trying to hash.  Only subclasses that
		//  answer true to the query canComputeHashOfType need to implement
		//  this method.

		//  only provide if subclass canComputeHashOfType.
		subclassResponsibility("ObjectHashOfType:", object);
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsCyclicType (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsExtendedInteger (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsIntegerRangeType (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsListType (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsMapType (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsSetType (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsTupleType (
			final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean ObjectIsType (
			final AvailObject object)
	{
		return false;
	}



	// scanning

	/**
	 * @param object
	 * @param visitor
	 */
	@Override
	public void ObjectScanSubobjects (
			final AvailObject object,
			final AvailSubobjectVisitor visitor)
	{
		for (int byteIndex = -4, _end1 = object.objectSlotsCount() * -4; byteIndex >= _end1; byteIndex -= 4)
		{
			visitor.invokeWithParentIndex(object, byteIndex);
		}
	}

	/**
	 * Answer an {@linkplain Iterator iterator} suitable for traversing the
	 * elements of the {@linkplain AvailObject object} with a Java
	 * <em>foreach</em> construct.
	 *
	 * @param object An {@link AvailObject}.
	 * @return An {@linkplain Iterator iterator}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull Iterator<AvailObject> ObjectIterator (
		final @NotNull AvailObject object)
	{
		error(
			"Subclass responsibility: ObjectIterator() in "
			+ getClass().getCanonicalName(),
			object);
		return null;
	}
}
