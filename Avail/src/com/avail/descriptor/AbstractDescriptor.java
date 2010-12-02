/**
 * com.avail.descriptor/AbstractDescriptor.java Copyright (c) 2010, Mark van
 * Gulik. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.annotations.ThreadSafe;
import com.avail.compiler.Continuation1;
import com.avail.compiler.Generator;
import com.avail.interpreter.AvailInterpreter;
import com.avail.visitor.AvailSubobjectVisitor;

/**
 * {@linkplain AbstractDescriptor} is
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public abstract class AbstractDescriptor
{

	/**
	 * A unique short, monotonically allocated and set automatically by the
	 * constructor.  It equals the {@linkplain AbstractDescriptor descriptor's}
	 * index into {@link #allDescriptors}, which is also populated by the
	 * constructor.
	 */
	final short myId;

	/**
	 * A flag indicating whether instances of me can be modified in place.
	 * Generally, as soon as there are two references from {@link AvailObject
	 * Avail objects}.
	 */
	protected final boolean isMutable;

	/**
	 * The minimum number of object slots an {@link AvailObject} can have if it
	 * uses this descriptor.  Populated automatically by the constructor.
	 */
	protected final int numberOfFixedObjectSlots;

	/**
	 * The minimum number of integer slots an {@link AvailObject} can have if it
	 * uses this descriptor.  Populated automatically by the constructor.
	 */
	protected final int numberOfFixedIntegerSlots;

	/**
	 * Whether an {@link AvailObject} using this descriptor can have more than
	 * the minimum number of object slots.  Populated automatically by the
	 * constructor.
	 */
	final boolean hasVariableObjectSlots;

	/**
	 * Whether an {@link AvailObject} using this descriptor can have more than
	 * the minimum number of integer slots.  Populated automatically by the
	 * constructor.
	 */
	final boolean hasVariableIntegerSlots;


	protected static final List<AbstractDescriptor> allDescriptors =
		new ArrayList<AbstractDescriptor>(200);

	protected static int bitShift (int value, int leftShift)
	{
		// Note:  This is a logical shift *without* Java's implicit modulus on
		// the shift amount.
		if (leftShift >= 32) return 0;
		if (leftShift >= 0) return value << leftShift;
		if (leftShift > -32) return value >>> -leftShift;
		return 0;
	}

	/**
	 * Construct a new {@link AbstractDescriptor descriptor}.
	 * @param isMutable Does the {@linkplain AbstractDescriptor descriptor}
	 *                  represent a mutable object?
	 */
	@SuppressWarnings("unchecked")
	protected AbstractDescriptor (
		final boolean isMutable)
	{
		this.myId = (short)allDescriptors.size();
		allDescriptors.add(this);
		this.isMutable = isMutable;

		final Class<Descriptor> cls = (Class<Descriptor>)this.getClass();
		final ClassLoader loader = cls.getClassLoader();
		Class<Enum<?>> enumClass;
		Enum<?>[] instances;

		try
		{
			enumClass = (Class<Enum<?>>) loader.loadClass(
				cls.getCanonicalName() + "$ObjectSlots");
		}
		catch (ClassNotFoundException e)
		{
			enumClass = null;
		}
		instances = enumClass != null
		? enumClass.getEnumConstants()
				: new Enum<?>[0];
		this.hasVariableObjectSlots = instances.length > 0
		&& instances[instances.length-1].name().matches(".*_");
		this.numberOfFixedObjectSlots = instances.length
		- (this.hasVariableObjectSlots ? 1 : 0);

		try
		{
			enumClass = (Class<Enum<?>>) loader.loadClass(
				cls.getCanonicalName() + "$IntegerSlots");
		}
		catch (ClassNotFoundException e)
		{
			enumClass = null;
		}
		instances = enumClass != null
		? enumClass.getEnumConstants()
				: new Enum<?>[0];
		this.hasVariableIntegerSlots = instances.length > 0
		&& instances[instances.length-1].name().matches(".*_");
		this.numberOfFixedIntegerSlots = instances.length
		- (this.hasVariableIntegerSlots ? 1 : 0);


	}

	public abstract boolean ObjectAcceptsArgTypesFromClosureType (
		final AvailObject object,
		final AvailObject closureType);

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	public abstract boolean ObjectAcceptsArgumentsFromContinuationStackp (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp);

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	public abstract boolean ObjectAcceptsArgumentTypesFromContinuationStackp (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract boolean ObjectAcceptsArrayOfArgTypes (
		final AvailObject object,
		final List<AvailObject> argTypes);

	/**
	 * @param object
	 * @param argValues
	 * @return
	 */
	public abstract boolean ObjectAcceptsArrayOfArgValues (
		final AvailObject object,
		final List<AvailObject> argValues);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract boolean ObjectAcceptsTupleOfArgTypes (
		final AvailObject object,
		final AvailObject argTypes);

	/**
	 * @param object
	 * @param arguments
	 * @return
	 */
	public abstract boolean ObjectAcceptsTupleOfArguments (
		final AvailObject object,
		final AvailObject arguments);

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	public abstract void ObjectAddDependentChunkId (
		final AvailObject object,
		final int aChunkIndex);

	/**
	 * @param object
	 * @param implementation
	 */
	public abstract void ObjectAddImplementation (
		final AvailObject object,
		final AvailObject implementation);

	/**
	 * @param object
	 * @param restrictions
	 */
	public abstract void ObjectAddRestrictions (
		final AvailObject object,
		final AvailObject restrictions);

	/**
	 * @param object
	 * @param anInfinity
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectAddToInfinityCanDestroy (
		final AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectAddToIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param args
	 * @param locals
	 * @param stack
	 * @param outers
	 * @param primitive
	 */
	public abstract void ObjectArgsLocalsStackOutersPrimitive (
		final AvailObject object,
		final int args,
		final int locals,
		final int stack,
		final int outers,
		final int primitive);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectArgTypeAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void ObjectArgTypeAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	public abstract void ObjectAtAddMessageRestrictions (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject illegalArgMsgs);

	/**
	 * @param object
	 * @param methodName
	 * @param implementation
	 */
	public abstract void ObjectAtAddMethodImplementation (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject implementation);

	/**
	 * @param object
	 * @param message
	 * @param bundle
	 */
	public abstract void ObjectAtMessageAddBundle (
		final AvailObject object,
		final AvailObject message,
		final AvailObject bundle);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	public abstract void ObjectAtNameAdd (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	public abstract void ObjectAtNewNamePut (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	public abstract void ObjectAtPrivateNameAdd (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectBinElementAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void ObjectBinElementAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectBinHash (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectBinSize (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectBinUnionType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectBitVector (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectBodyBlock (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param bb
	 * @param rqb
	 * @param rtb
	 */
	public abstract void ObjectBodyBlockRequiresBlockReturnsBlock (
		final AvailObject object,
		final AvailObject bb,
		final AvailObject rqb,
		final AvailObject rtb);

	/**
	 * @param object
	 * @param signature
	 */
	public abstract void ObjectBodySignature (
		final AvailObject object,
		final AvailObject signature);

	/**
	 * @param object
	 * @param bs
	 * @param rqb
	 * @param rtb
	 */
	public abstract void ObjectBodySignatureRequiresBlockReturnsBlock (
		final AvailObject object,
		final AvailObject bs,
		final AvailObject rqb,
		final AvailObject rtb);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectBreakpointBlock (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param bundleTree
	 */
	public abstract void ObjectBuildFilteredBundleTreeFrom (
		final AvailObject object,
		final AvailObject bundleTree);

	/**
	 * @param object
	 * @param message
	 * @param parts
	 * @return
	 */
	public abstract AvailObject ObjectBundleAtMessageParts (
		final AvailObject object,
		final AvailObject message,
		final AvailObject parts);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectCaller (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectClosure (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectClosureType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectCode (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectCodePoint (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param anotherObject
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean ObjectCompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anotherObject,
		final int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aTuple
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean ObjectCompareFromToWithAnyTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTuple,
		final int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteString
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean ObjectCompareFromToWithByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteString,
		final int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteTuple
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean ObjectCompareFromToWithByteTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteTuple,
		final int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aNybbleTuple
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean ObjectCompareFromToWithNybbleTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aNybbleTuple,
		final int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param anObjectTuple
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean ObjectCompareFromToWithObjectTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anObjectTuple,
		final int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aTwoByteString
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean ObjectCompareFromToWithTwoByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTwoByteString,
		final int startIndex2);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectComplete (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param start
	 * @param end
	 * @return
	 */
	public abstract int ObjectComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end);

	/**
	 * @param object
	 * @param argTypes
	 * @param anAvailInterpreter
	 * @return
	 */
	public abstract AvailObject ObjectComputeReturnTypeFromArgumentTypesInterpreter (
		final AvailObject object,
		final List<AvailObject> argTypes,
		final AvailInterpreter anAvailInterpreter);

	/**
	 * @param object
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectConcatenateTuplesCanDestroy (
		final AvailObject object,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectConstantBindings (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectContentType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectContingentImpSets (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectContinuation (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param filteredBundleTree
	 * @param visibleNames
	 */
	public abstract void ObjectCopyToRestrictedTo (
		final AvailObject object,
		final AvailObject filteredBundleTree,
		final AvailObject visibleNames);

	/**
	 * @param object
	 * @param start
	 * @param end
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectCopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract boolean ObjectCouldEverBeInvokedWith (
		final AvailObject object,
		final ArrayList<AvailObject> argTypes);

	/**
	 * @param object
	 * @param positiveTuple
	 * @param possibilities
	 * @return
	 */
	public abstract AvailObject ObjectCreateTestingTreeWithPositiveMatchesRemainingPossibilities (
		final AvailObject object,
		final AvailObject positiveTuple,
		final AvailObject possibilities);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectDataAtIndex (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void ObjectDataAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectDefaultType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectDependentChunks (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectDepth (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param aNumber
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectDivideCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInfinity
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectDivideIntoInfinityCanDestroy (
		final AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectDivideIntoIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectElementAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void ObjectElementAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int ObjectEndOfZone (final AvailObject object, final int zone);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int ObjectEndSubtupleIndexInZone (
		final AvailObject object,
		final int zone);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectExecutionMode (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectExecutionState (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract byte ObjectExtractNybbleFromTupleAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectFieldMap (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectFieldTypeMap (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract List<AvailObject> ObjectFilterByTypes (
		final AvailObject object,
		final List<AvailObject> argTypes);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectFilteredBundleTree (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectFirstTupleType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param zone
	 * @param newSubtuple
	 * @param startSubtupleIndex
	 * @param endOfZone
	 * @return
	 */
	public abstract AvailObject ObjectForZoneSetSubtupleStartSubtupleIndexEndOfZone (
		final AvailObject object,
		final int zone,
		final AvailObject newSubtuple,
		final int startSubtupleIndex,
		final int endOfZone);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean ObjectGreaterThanInteger (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean ObjectGreaterThanSignedInfinity (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param elementObject
	 * @return
	 */
	public abstract boolean ObjectHasElement (
		final AvailObject object,
		final AvailObject elementObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectHash (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public abstract int ObjectHashFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectHashOrZero (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	public abstract boolean ObjectHasKey (
		final AvailObject object,
		final AvailObject keyObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectHiLevelTwoChunkLowOffset (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectHiNumLocalsLowNumArgs (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectHiPrimitiveLowNumArgsAndLocalsAndStack (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectHiStartingChunkIndexLowNumOuters (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract ArrayList<AvailObject> ObjectImplementationsAtOrBelow (
		final AvailObject object,
		final ArrayList<AvailObject> argTypes);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectImplementationsTuple (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param message
	 * @param parts
	 * @return
	 */
	public abstract AvailObject ObjectIncludeBundleAtMessageParts (
		final AvailObject object,
		final AvailObject message,
		final AvailObject parts);

	/**
	 * @param object
	 * @param imp
	 * @return
	 */
	public abstract boolean ObjectIncludes (
		final AvailObject object,
		final AvailObject imp);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectInclusiveFlags (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectIncomplete (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectIndex (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectInnerType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectInstance (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectInternalHash (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectInterruptRequestFlag (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectInvocationCount (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param aBoolean
	 */
	public abstract void ObjectIsSaved (final AvailObject object, final boolean aBoolean);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean ObjectIsSubsetOf (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean ObjectIsSubtypeOf (
		final AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType);

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCyclicType
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfCyclicType (
		final AvailObject object,
		final AvailObject aCyclicType);

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aListType
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfListType (
		final AvailObject object,
		final AvailObject aListType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType);

	/**
	 * @param object
	 * @param anObjectMeta
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfObjectMeta (
		final AvailObject object,
		final AvailObject anObjectMeta);

	/**
	 * @param object
	 * @param anObjectMetaMeta
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfObjectMetaMeta (
		final AvailObject object,
		final AvailObject anObjectMetaMeta);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType);

	/**
	 * @param object
	 * @param aPrimitiveType
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfPrimitiveType (
		final AvailObject object,
		final AvailObject aPrimitiveType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param aBoolean
	 */
	public abstract void ObjectIsValid (final AvailObject object, final boolean aBoolean);

	/**
	 * @param object
	 * @param argTypes
	 * @param interpreter
	 * @return
	 */
	public abstract boolean ObjectIsValidForArgumentTypesInterpreter (
		final AvailObject object,
		final List<AvailObject> argTypes,
		final AvailInterpreter interpreter);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectKeyAtIndex (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param keyObject
	 */
	public abstract void ObjectKeyAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject keyObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectKeyType (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean ObjectLessOrEqual (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean ObjectLessThan (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param index
	 * @param offset
	 */
	public abstract void ObjectLevelTwoChunkIndexOffset (
		final AvailObject object,
		final int index,
		final int offset);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectLiteral (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectLiteralAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void ObjectLiteralAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectLocalOrArgOrStackAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void ObjectLocalOrArgOrStackAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectLocalTypeAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param argumentTypeArray
	 * @return
	 */
	public abstract AvailObject ObjectLookupByTypesFromArray (
		final AvailObject object,
		final List<AvailObject> argumentTypeArray);

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	public abstract AvailObject ObjectLookupByTypesFromContinuationStackp (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp);

	/**
	 * @param object
	 * @param argumentTypeTuple
	 * @return
	 */
	public abstract AvailObject ObjectLookupByTypesFromTuple (
		final AvailObject object,
		final AvailObject argumentTypeTuple);

	/**
	 * @param object
	 * @param argumentArray
	 * @return
	 */
	public abstract AvailObject ObjectLookupByValuesFromArray (
		final AvailObject object,
		final List<AvailObject> argumentArray);

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	public abstract AvailObject ObjectLookupByValuesFromContinuationStackp (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp);

	/**
	 * @param object
	 * @param argumentTuple
	 * @return
	 */
	public abstract AvailObject ObjectLookupByValuesFromTuple (
		final AvailObject object,
		final AvailObject argumentTuple);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectLowerBound (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param lowInc
	 * @param highInc
	 */
	public abstract void ObjectLowerInclusiveUpperInclusive (
		final AvailObject object,
		final boolean lowInc,
		final boolean highInc);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	public abstract AvailObject ObjectMapAt (
		final AvailObject object,
		final AvailObject keyObject);

	/**
	 * @param object
	 * @param keyObject
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectMapAtPuttingCanDestroy (
		final AvailObject object,
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectMapSize (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param keyObject
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectMapWithoutKeyCanDestroy (
		final AvailObject object,
		final AvailObject keyObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectMessage (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectMessageParts (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectMethods (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param aNumber
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectMinusCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInfinity
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectMultiplyByInfinityCanDestroy (
		final AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectMultiplyByIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectMyObjectMeta (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectMyObjectType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectMyRestrictions (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectMyType (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectName (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectNames (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param trueName
	 * @return
	 */
	public abstract boolean ObjectNameVisible (
		final AvailObject object,
		final AvailObject trueName);

	/**
	 * @param object
	 * @param anImplementationSet
	 */
	public abstract void ObjectNecessaryImplementationSetChanged (
		final AvailObject object,
		final AvailObject anImplementationSet);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectNewNames (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param nextChunk
	 */
	public abstract void ObjectNext (
		final AvailObject object,
		final AvailObject nextChunk);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectNextIndex (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectNumBlanks (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectNumFloats (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectNumIntegers (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectNumObjects (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectNybbles (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract boolean ObjectOptionallyNilOuterVar (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectOuterTypeAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param tupleOfOuterTypes
	 * @param tupleOfLocalContainerTypes
	 */
	public abstract void ObjectOuterTypesLocalTypes (
		final AvailObject object,
		final AvailObject tupleOfOuterTypes,
		final AvailObject tupleOfLocalContainerTypes);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectOuterVarAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void ObjectOuterVarAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectPad1 (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectPad2 (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectParent (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectPc (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param aNumber
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectPlusCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param previousChunk
	 */
	public abstract void ObjectPrevious (
		final AvailObject object,
		final AvailObject previousChunk);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectPreviousIndex (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectPriority (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	public abstract AvailObject ObjectPrivateAddElement (
		final AvailObject object,
		final AvailObject element);

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	public abstract AvailObject ObjectPrivateExcludeElement (
		final AvailObject object,
		final AvailObject element);

	/**
	 * @param object
	 * @param element
	 * @param knownIndex
	 * @return
	 */
	public abstract AvailObject ObjectPrivateExcludeElementKnownIndex (
		final AvailObject object,
		final AvailObject element,
		final int knownIndex);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	public abstract AvailObject ObjectPrivateExcludeKey (
		final AvailObject object,
		final AvailObject keyObject);

	/**
	 * @param object
	 * @param keyObject
	 * @param valueObject
	 * @return
	 */
	public abstract AvailObject ObjectPrivateMapAtPut (
		final AvailObject object,
		final AvailObject keyObject,
		final AvailObject valueObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectPrivateNames (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectPrivateTestingTree (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectProcessGlobals (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract short ObjectRawByteAt (final AvailObject object, final int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	public abstract void ObjectRawByteAtPut (
		final AvailObject object,
		final int index,
		final short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract short ObjectRawByteForCharacterAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	public abstract void ObjectRawByteForCharacterAtPut (
		final AvailObject object,
		final int index,
		final short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract byte ObjectRawNybbleAt (final AvailObject object, final int index);

	/**
	 * @param object
	 * @param index
	 * @param aNybble
	 */
	public abstract void ObjectRawNybbleAtPut (
		final AvailObject object,
		final int index,
		final byte aNybble);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectRawQuad1 (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectRawQuad2 (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int ObjectRawQuadAt (final AvailObject object, final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void ObjectRawQuadAtPut (
		final AvailObject object,
		final int index,
		final int value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract short ObjectRawShortForCharacterAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	public abstract void ObjectRawShortForCharacterAtPut (
		final AvailObject object,
		final int index,
		final short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int ObjectRawSignedIntegerAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void ObjectRawSignedIntegerAtPut (
		final AvailObject object,
		final int index,
		final int value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract long ObjectRawUnsignedIntegerAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void ObjectRawUnsignedIntegerAtPut (
		final AvailObject object,
		final int index,
		final int value);

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	public abstract void ObjectRemoveDependentChunkId (
		final AvailObject object,
		final int aChunkIndex);

	/**
	 * @param object
	 * @param anInterpreter
	 */
	public abstract void ObjectRemoveFrom (
		final AvailObject object,
		final AvailInterpreter anInterpreter);

	/**
	 * @param object
	 * @param implementation
	 */
	public abstract void ObjectRemoveImplementation (
		final AvailObject object,
		final AvailObject implementation);

	/**
	 * @param object
	 * @param message
	 * @param parts
	 * @return
	 */
	public abstract boolean ObjectRemoveMessageParts (
		final AvailObject object,
		final AvailObject message,
		final AvailObject parts);

	/**
	 * @param object
	 * @param obsoleteRestrictions
	 */
	public abstract void ObjectRemoveRestrictions (
		final AvailObject object,
		final AvailObject obsoleteRestrictions);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectRequiresBlock (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param forwardImplementation
	 * @param methodName
	 */
	public abstract void ObjectResolvedForwardWithName (
		final AvailObject object,
		final AvailObject forwardImplementation,
		final AvailObject methodName);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectRestrictions (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectReturnsBlock (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectReturnType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectRootBin (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectSecondTupleType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectSetIntersectionCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectSetMinusCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectSetSize (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param zoneIndex
	 * @param newTuple
	 */
	public abstract void ObjectSetSubtupleForZoneTo (
		final AvailObject object,
		final int zoneIndex,
		final AvailObject newTuple);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectSetUnionCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param newValue
	 */
	public abstract void ObjectSetValue (
		final AvailObject object,
		final AvailObject newValue);

	/**
	 * @param object
	 * @param newElementObject
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectSetWithElementCanDestroy (
		final AvailObject object,
		final AvailObject newElementObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param elementObjectToExclude
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectSetWithoutElementCanDestroy (
		final AvailObject object,
		final AvailObject elementObjectToExclude,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectSignature (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectSize (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int ObjectSizeOfZone (final AvailObject object, final int zone);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectSizeRange (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectSpecialActions (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param slotIndex
	 * @return
	 */
	public abstract AvailObject ObjectStackAt (
		final AvailObject object,
		final int slotIndex);

	/**
	 * @param object
	 * @param slotIndex
	 * @param anObject
	 */
	public abstract void ObjectStackAtPut (
		final AvailObject object,
		final int slotIndex,
		final AvailObject anObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectStackp (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectStart (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectStartingChunkIndex (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int ObjectStartOfZone (final AvailObject object, final int zone);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int ObjectStartSubtupleIndexInZone (
		final AvailObject object,
		final int zone);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectString (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param anInfinity
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectSubtractFromInfinityCanDestroy (
		final AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectSubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract AvailObject ObjectSubtupleForZone (
		final AvailObject object,
		final int zone);

	/**
	 * @param object
	 * @param aNumber
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectTimesCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 * @return
	 */
	public abstract void ObjectTokenTypeCode (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param tupleIndex
	 * @param zoneIndex
	 * @return
	 */
	public abstract int ObjectTranslateToZone (
		final AvailObject object,
		final int tupleIndex,
		final int zoneIndex);

	/**
	 * @param object
	 * @param stringName
	 * @return
	 */
	public abstract AvailObject ObjectTrueNamesForStringName (
		final AvailObject object,
		final AvailObject stringName);

	/**
	 * @param object
	 * @param newTupleSize
	 * @return
	 */
	public abstract AvailObject ObjectTruncateTo (
		final AvailObject object,
		final int newTupleSize);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectTuple (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectTupleAt (final AvailObject object, final int index);

	/**
	 * @param object
	 * @param index
	 * @param aNybbleObject
	 */
	public abstract void ObjectTupleAtPut (
		final AvailObject object,
		final int index,
		final AvailObject aNybbleObject);

	/**
	 * @param object
	 * @param index
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectTupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int ObjectTupleIntAt (final AvailObject object, final int index);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectTupleType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectType (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectTypeAtIndex (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersection (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType);

	/**
	 * @param object
	 * @param aClosureType
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfClosureTypeCanDestroy (
		final AvailObject object,
		final AvailObject aClosureType,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCyclicType
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfCyclicType (
		final AvailObject object,
		final AvailObject aCyclicType);

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType);

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfGeneralizedClosureTypeCanDestroy (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aListType
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfListType (
		final AvailObject object,
		final AvailObject aListType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfMapType (
		final AvailObject object,
		final AvailObject aMapType);

	/**
	 * @param object
	 * @param someMeta
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfMeta (
		final AvailObject object,
		final AvailObject someMeta);

	/**
	 * @param object
	 * @param anObjectMeta
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfObjectMeta (
		final AvailObject object,
		final AvailObject anObjectMeta);

	/**
	 * @param object
	 * @param anObjectMetaMeta
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfObjectMetaMeta (
		final AvailObject object,
		final AvailObject anObjectMetaMeta);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfSetType (
		final AvailObject object,
		final AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract AvailObject ObjectTypeIntersectionOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectTypeTuple (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnion (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType);

	/**
	 * @param object
	 * @param aClosureType
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfClosureTypeCanDestroy (
		final AvailObject object,
		final AvailObject aClosureType,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCyclicType
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfCyclicType (
		final AvailObject object,
		final AvailObject aCyclicType);

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aListType
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfListType (
		final AvailObject object,
		final AvailObject aListType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfMapType (
		final AvailObject object,
		final AvailObject aMapType);

	/**
	 * @param object
	 * @param anObjectMeta
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfObjectMeta (
		final AvailObject object,
		final AvailObject anObjectMeta);

	/**
	 * @param object
	 * @param anObjectMetaMeta
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfObjectMetaMeta (
		final AvailObject object,
		final AvailObject anObjectMetaMeta);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfSetType (
		final AvailObject object,
		final AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract AvailObject ObjectTypeUnionOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectUnclassified (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public abstract AvailObject ObjectUnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int ObjectUntranslatedDataAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void ObjectUntranslatedDataAtPut (
		final AvailObject object,
		final int index,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectUpperBound (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param argTypes
	 * @param anAvailInterpreter
	 * @param failBlock
	 * @return
	 */
	public abstract AvailObject ObjectValidateArgumentTypesInterpreterIfFail (
		final AvailObject object,
		final List<AvailObject> argTypes,
		final AvailInterpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectValidity (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectValue (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject ObjectValueAtIndex (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param valueObject
	 */
	public abstract void ObjectValueAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject valueObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectValueType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectVariableBindings (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectVectors (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectVisibleNames (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectWhichOne (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectWordcodes (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int ObjectZoneForIndex (final AvailObject object, final int index);

	/**
	 * @param object
	 * @return
	 */
	public abstract String ObjectAsNativeString (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectAsObject (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectAsSet (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectAsTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectBecomeExactType (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectBecomeRealTupleType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectBitsPerEntry (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectBitVector (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectBodyBlock (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectBodySignature (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectBreakpointBlock (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectCaller (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectCapacity (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectCleanUpAfterCompile (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectClearModule (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectClearValue (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectClosure (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectClosureType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectCode (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectCodePoint (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectComplete (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectConstantBindings (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectContentType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectContingentImpSets (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectContinuation (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectCopyAsMutableContinuation (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectCopyAsMutableObjectTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectCopyAsMutableSpliceTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectCopyMutable (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectDefaultType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectDependentChunks (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectDepth (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectDisplayTestingTree (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectEnsureMetacovariant (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectEnsureMutable (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectEvictedByGarbageCollector (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectExecutionMode (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectExecutionState (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectExpand (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectExtractBoolean (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short ObjectExtractByte (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract double ObjectExtractDouble (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract float ObjectExtractFloat (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectExtractInt (final AvailObject object);

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
	public abstract long ObjectExtractLong (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract byte ObjectExtractNybble (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectFieldMap (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectFieldTypeMap (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectFilteredBundleTree (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectFirstTupleType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectGetInteger (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectGetValue (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectHashOrZero (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectHasRestrictions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectHiLevelTwoChunkLowOffset (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectHiNumLocalsLowNumArgs (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectHiPrimitiveLowNumArgsAndLocalsAndStack (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectHiStartingChunkIndexLowNumOuters (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectImplementationsTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectInclusiveFlags (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectIncomplete (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectIndex (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectInnerType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectInstance (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectInternalHash (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectInterruptRequestFlag (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectInvocationCount (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsAbstract (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsFinite (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsForward (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsImplementation (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsPositive (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsSaved (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsSplice (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfTerminates (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsSupertypeOfVoid (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsValid (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract List<AvailObject> ObjectKeysAsArray (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectKeysAsSet (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectKeyType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectLevelTwoChunkIndex (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectLevelTwoOffset (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectLiteral (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectLowerBound (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectLowerInclusive (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectMapSize (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short ObjectMaxStackDepth (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectMessage (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectMessageParts (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectMethods (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectMoveToHead (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectMyObjectMeta (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectMyObjectType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectMyRestrictions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectMyType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectName (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectNames (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectNewNames (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectNext (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectNextIndex (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short ObjectNumArgs (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short ObjectNumArgsAndLocalsAndStack (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectNumberOfZones (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectNumBlanks (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectNumDoubles (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectNumIntegers (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short ObjectNumLiterals (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short ObjectNumLocals (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectNumLocalsOrArgsOrStack (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectNumObjects (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short ObjectNumOuters (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectNumOuterVars (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectNybbles (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectPad1 (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectPad2 (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectParent (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectPc (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectPrevious (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectPreviousIndex (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short ObjectPrimitiveNumber (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectPriority (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectPrivateNames (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectPrivateTestingTree (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectProcessGlobals (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectRawQuad1 (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectRawQuad2 (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectReleaseVariableOrMakeContentsImmutable (
		final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectRemoveFromQueue (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectRemoveRestrictions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectRequiresBlock (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectRestrictions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectReturnsBlock (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectReturnType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectRootBin (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectSecondTupleType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectSetSize (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectSignature (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectSizeRange (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectSpecialActions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectStackp (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectStart (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectStartingChunkIndex (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectStep (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectString (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectTestingTree (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract int ObjectTokenTypeCode (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectTrimExcessLongs (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectTupleSize (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectTupleType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectTypeTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectUnclassified (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectUpperBound (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectUpperInclusive (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectValidity (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectValue (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectValuesAsTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectValueType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectVariableBindings (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectVectors (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectVerify (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectVisibleNames (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectWhichOne (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectWordcodes (final AvailObject object);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean ObjectEquals (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	public abstract boolean ObjectEqualsAnyTuple (
		final AvailObject object,
		final AvailObject aTuple);

	/**
	 * @param object
	 * @param aString
	 * @return
	 */
	public abstract boolean ObjectEqualsByteString (
		final AvailObject object,
		final AvailObject aString);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	public abstract boolean ObjectEqualsByteTuple (
		final AvailObject object,
		final AvailObject aTuple);

	/**
	 * @param object
	 * @param otherCodePoint
	 * @return
	 */
	public abstract boolean ObjectEqualsCharacterWithCodePoint (
		final AvailObject object,
		final int otherCodePoint);

	/**
	 * @param object
	 * @param aClosure
	 * @return
	 */
	public abstract boolean ObjectEqualsClosure (
		final AvailObject object,
		final AvailObject aClosure);

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	public abstract boolean ObjectEqualsClosureType (
		final AvailObject object,
		final AvailObject aClosureType);

	/**
	 * @param object
	 * @param aCompiledCode
	 * @return
	 */
	public abstract boolean ObjectEqualsCompiledCode (
		final AvailObject object,
		final AvailObject aCompiledCode);

	/**
	 * @param object
	 * @param aContainer
	 * @return
	 */
	public abstract boolean ObjectEqualsContainer (
		final AvailObject object,
		final AvailObject aContainer);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean ObjectEqualsContainerType (
		final AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @param aContinuation
	 * @return
	 */
	public abstract boolean ObjectEqualsContinuation (
		final AvailObject object,
		final AvailObject aContinuation);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean ObjectEqualsContinuationType (
		final AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @param aDoubleObject
	 * @return
	 */
	public abstract boolean ObjectEqualsDouble (
		final AvailObject object,
		final AvailObject aDoubleObject);

	/**
	 * @param object
	 * @param aFloatObject
	 * @return
	 */
	public abstract boolean ObjectEqualsFloat (
		final AvailObject object,
		final AvailObject aFloatObject);

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	public abstract boolean ObjectEqualsGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType);

	/**
	 * @param object
	 * @param anInfinity
	 * @return
	 */
	public abstract boolean ObjectEqualsInfinity (
		final AvailObject object,
		final AvailObject anInfinity);

	/**
	 * @param object
	 * @param anAvailInteger
	 * @return
	 */
	public abstract boolean ObjectEqualsInteger (
		final AvailObject object,
		final AvailObject anAvailInteger);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean ObjectEqualsIntegerRangeType (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aList
	 * @return
	 */
	public abstract boolean ObjectEqualsList (
		final AvailObject object,
		final AvailObject aList);

	/**
	 * @param object
	 * @param aListType
	 * @return
	 */
	public abstract boolean ObjectEqualsListType (
		final AvailObject object,
		final AvailObject aListType);

	/**
	 * @param object
	 * @param aMap
	 * @return
	 */
	public abstract boolean ObjectEqualsMap (
		final AvailObject object,
		final AvailObject aMap);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	public abstract boolean ObjectEqualsMapType (
		final AvailObject object,
		final AvailObject aMapType);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	public abstract boolean ObjectEqualsNybbleTuple (
		final AvailObject object,
		final AvailObject aTuple);

	/**
	 * @param object
	 * @param anObject
	 * @return
	 */
	public abstract boolean ObjectEqualsObject (
		final AvailObject object,
		final AvailObject anObject);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	public abstract boolean ObjectEqualsObjectTuple (
		final AvailObject object,
		final AvailObject aTuple);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean ObjectEqualsPrimitiveType (
		final AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @param aSet
	 * @return
	 */
	public abstract boolean ObjectEqualsSet (
		final AvailObject object,
		final AvailObject aSet);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	public abstract boolean ObjectEqualsSetType (
		final AvailObject object,
		final AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract boolean ObjectEqualsTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param aString
	 * @return
	 */
	public abstract boolean ObjectEqualsTwoByteString (
		final AvailObject object,
		final AvailObject aString);

	/**
	 * @param object
	 * @param potentialInstance
	 * @return
	 */
	public abstract boolean ObjectHasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance);

	/**
	 * @param object
	 * @param anotherObject
	 * @return
	 */
	public abstract boolean ObjectIsBetterRepresentationThan (
		final AvailObject object,
		final AvailObject anotherObject);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract boolean ObjectIsBetterRepresentationThanTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean ObjectIsInstanceOfSubtypeOf (
		final AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectCanComputeHashOfType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectEqualsBlank (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectEqualsFalse (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectEqualsTrue (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectEqualsVoid (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectEqualsVoidOrBlank (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectExactType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectHash (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsClosure (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsHashAvailable (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectMakeImmutable (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectMakeSubobjectsImmutable (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsBoolean (final AvailObject object);

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
	public abstract boolean ObjectIsByteTuple (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsCharacter (final AvailObject object);

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
	public abstract boolean ObjectIsString (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param aClosure
	 * @return
	 */
	public abstract boolean ObjectContainsBlock (
		final AvailObject object,
		final AvailObject aClosure);

	/**
	 * @param object
	 */
	public abstract void ObjectPostFault (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void ObjectReadBarrierFault (final AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void ObjectTarget (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectTarget (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectTraversed (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsList (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsMap (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsByte (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsNybble (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsSet (final AvailObject object);

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @param myLevel
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectBinAddingElementHashLevelCanDestroy (
		final AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @return
	 */
	public abstract boolean ObjectBinHasElementHash (
		final AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash);

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject ObjectBinRemoveElementHashCanDestroy (
		final AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param potentialSuperset
	 * @return
	 */
	public abstract boolean ObjectIsBinSubsetOf (
		final AvailObject object,
		final AvailObject potentialSuperset);

	/**
	 * @param object
	 * @param mutableTuple
	 * @param startingIndex
	 * @return
	 */
	public abstract int ObjectPopulateTupleStartingAt (
		final AvailObject object,
		final AvailObject mutableTuple,
		final int startingIndex);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectBinHash (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectBinSize (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject ObjectBinUnionType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsTuple (final AvailObject object);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean ObjectTypeEquals (
		final AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @return
	 */
	public abstract int ObjectHashOfType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsCyclicType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsExtendedInteger (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsIntegerRangeType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsListType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsMapType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsSetType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsTupleType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean ObjectIsType (final AvailObject object);

	/**
	 * @param object
	 * @param visitor
	 */
	public abstract void ObjectScanSubobjects (
		final AvailObject object,
		final AvailSubobjectVisitor visitor);

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
	public abstract @NotNull
	Iterator<AvailObject> ObjectIterator (final @NotNull AvailObject object);

	public final short id ()
	{
		return myId;
	}

	public final boolean isMutable ()
	{
		return isMutable;
	}

	protected boolean hasVariableIntegerSlots ()
	{
		//  Answer whether I have a variable number of integer slots.

		return hasVariableIntegerSlots;
	}

	protected boolean hasVariableObjectSlots ()
	{
		//  Answer whether I have a variable number of object slots.

		return hasVariableObjectSlots;
	}

	public int numberOfFixedIntegerSlots ()
	{
		//  Answer how many named integer slots I have, excluding the indexed slots that may be at the end.

		return numberOfFixedIntegerSlots;
	}

	public int numberOfFixedObjectSlots ()
	{
		//  Answer how many named object slots I have, excluding the indexed slots that may be at the end.

		return numberOfFixedObjectSlots;
	}


	/**
	 * Answer whether the field at the given offset is allowed to be modified
	 * even in an immutable object.
	 *
	 * @param e The byte offset of the field to check.
	 * @return Whether the specified field can be written even in an immutable
	 *         object.
	 */
	public boolean allowsImmutableToMutableReferenceInField (
		final Enum<?> e)
	{
		return false;
	}

	/**
	 * Answer how many levels of printing to allow before elision.
	 *
	 * @return The number of levels.
	 */
	public int maximumIndent ()
	{
		//  Answer the deepest a recursive print can go before summarizing.

		return 5;
	}

	/**
	 * Print the object to the {@link StringBuilder}.  By default show it as the
	 * descriptor name and a line-by-line list of fields.  If the indent is
	 * beyond maximumIndent, indicate it's too deep without recursing.  If the
	 * object is in recursionList, indicate a recursive print and return.
	 *
	 * @param object The object to print (its descriptor is me).
	 * @param builder Where to print the object.
	 * @param recursionList Which ancestor objects are currently being printed.
	 * @param indent What level to indent subsequent lines.
	 */
	@ThreadSafe
	public void printObjectOnAvoidingIndent (
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

		final Class<Descriptor> cls = (Class<Descriptor>)this.getClass();
		final ClassLoader loader = cls.getClassLoader();
		Class<Enum<?>> enumClass;
		Enum<?>[] instances;

		try
		{
			enumClass = (Class<Enum<?>>) loader.loadClass(
				cls.getCanonicalName() + "$IntegerSlots");
		}
		catch (ClassNotFoundException e)
		{
			enumClass = null;
		}
		instances = enumClass != null
		? enumClass.getEnumConstants()
				: new Enum<?>[0];

		for (int i = 1, limit = object.integerSlotsCount(); i <= limit; i++)
		{
			builder.append('\n');
			for (int tab = 0; tab < indent; tab++)
			{
				builder.append('\t');
			}
			int ordinal = Math.min(i, instances.length) - 1;
			Enum<?> slot = instances[ordinal];
			String slotName = slot.name();
			if (slotName.charAt(slotName.length() - 1) == '_')
			{
				int subscript = i - instances.length + 1;
				builder.append(slotName, 0, slotName.length() - 1);
				builder.append('[');
				builder.append(subscript);
				builder.append("] = ");
				builder.append(object.integerSlotAt(slot, subscript));
			}
			else
			{
				builder.append(slotName);
				builder.append(" = ");
				builder.append(object.integerSlot(slot));
			}
		}

		try
		{
			enumClass = (Class<Enum<?>>) loader.loadClass(
				cls.getCanonicalName() + "$ObjectSlots");
		}
		catch (ClassNotFoundException e)
		{
			enumClass = null;
		}
		instances = enumClass != null
		? enumClass.getEnumConstants()
				: new Enum<?>[0];

		for (int i = 1, limit = object.objectSlotsCount(); i <= limit; i++)
		{
			builder.append('\n');
			for (int tab = 0; tab < indent; tab++)
			{
				builder.append('\t');
			}
			int ordinal = Math.min(i, instances.length) - 1;
			Enum<?> slot = instances[ordinal];
			String slotName = slot.name();
			if (slotName.charAt(slotName.length() - 1) == '_')
			{
				int subscript = i - instances.length + 1;
				builder.append(slotName, 0, slotName.length() - 1);
				builder.append('[');
				builder.append(subscript);
				builder.append("] = ");
				object.objectSlotAt(slot, subscript).printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
			else
			{
				builder.append(slotName);
				builder.append(" = ");
				object.objectSlot(slot).printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
		}
	}

	public final void checkWriteForField (final Enum<?> e)
	{
		if (isMutable())
		{
			return;
		}
		if (allowsImmutableToMutableReferenceInField(e))
		{
			return;
		}
		error("Illegal write into immutable object");
		return;
	}

}
