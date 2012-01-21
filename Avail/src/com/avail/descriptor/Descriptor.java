/**
 * Descriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.compiler.*;
import com.avail.descriptor.AbstractNumberDescriptor.*;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.ProcessDescriptor.ExecutionState;
import com.avail.exceptions.UnsupportedOperationException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;
import com.avail.visitor.*;

/**
 * This is the primary subclass of {@linkplain AbstractDescriptor}.  It has the
 * sibling IndirectionDescriptor.
 *
 * <p>When a new method is added in a subclass, it should be added with the
 * {@linkplain Override @Override} annotation.  That way the project will
 * indicate errors until an abstract declaration is added to {@linkplain
 * AbstractDescriptor}, a default implementation is added to {@linkplain
 * Descriptor}, and a redirecting implementation is added to {@linkplain
 * IndirectionDescriptor}.  Any code attempting to send the corresponding
 * message to an {@linkplain AvailObject} will also indicate a problem until a
 * suitable implementation is added to AvailObject.</p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class Descriptor
extends AbstractDescriptor
{
	/**
	 * Construct a new {@link Descriptor}.
	 *
	 * @param isMutable Whether an instance of the descriptor can be modified.
	 */
	public Descriptor (final boolean isMutable)
	{
		super(isMutable);
	}


	/**
	 * Raise a {@linkplain RuntimeException runtime exception} to indicate that
	 * the specified argument's {@linkplain Descriptor descriptor} does not
	 * meaningfully implement the caller of this method. This may be an
	 * indication that the wrong kind of object is being used somewhere.
	 *
	 * @throws UnsupportedOperationException
	 *         Always thrown.
	 */
	public void unsupportedOperation () throws UnsupportedOperationException
	{
		final String callerName;
		try
		{
			throw new Exception("just want the caller's frame");
		}
		catch (final Exception e)
		{
			callerName = e.getStackTrace()[1].getMethodName();
		}
		throw new UnsupportedOperationException(getClass(), callerName);
	}

	/**
	 * A special enumeration used to visit all object slots within an instance
	 * of the receiver.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	enum FakeObjectSlotsForScanning implements ObjectSlotsEnum
	{
		/**
		 * An indexed object slot that makes it easy to visit all object slots.
		 */
		ALL_OBJECT_SLOTS_
	}


	/**
	 * Visit all of the object's object slots, passing the parent and child
	 * objects to the provided visitor.
	 *
	 * @param object The object to scan.
	 * @param visitor The visitor to invoke.
	 */
	@Override
	void o_ScanSubobjects (
		final @NotNull AvailObject object,
		final AvailSubobjectVisitor visitor)
	{
		for (int i = object.objectSlotsCount(); i >= 1; i--)
		{
			final AvailObject child = object.slot(
				FakeObjectSlotsForScanning.ALL_OBJECT_SLOTS_,
				i);
			visitor.invoke(object, child);
		}
	}


	@Override
	boolean o_AcceptsArgTypesFromFunctionType (
		final @NotNull AvailObject object,
		final AvailObject functionType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_AcceptsArgumentTypesFromContinuation (
		final @NotNull AvailObject object,
		final AvailObject continuation,
		final int stackp,
		final int numArgs)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final List<AvailObject> argValues)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final AvailObject argTypes)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final AvailObject arguments)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	void o_AddDependentChunkIndex (
		final @NotNull AvailObject object,
		final int aChunkIndex)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_AddImplementation (
		final @NotNull AvailObject object,
		final AvailObject implementation)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_AddRestrictions (
		final @NotNull AvailObject object,
		final AvailObject restrictions)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_AddToInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull Sign sign,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_AddToIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_AddGrammaticalMessageRestrictions (
		final @NotNull AvailObject object,
		final AvailObject methodName,
		final AvailObject illegalArgMsgs)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_AddMethodImplementation (
		final @NotNull AvailObject object,
		final AvailObject methodName,
		final AvailObject implementation)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_AtMessageAddBundle (
		final @NotNull AvailObject object,
		final AvailObject message,
		final AvailObject bundle)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_AtNameAdd (
		final @NotNull AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_AtNewNamePut (
		final @NotNull AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_AtPrivateNameAdd (
		final @NotNull AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_BinElementAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_BinElementAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_BinHash (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_BinSize (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_BinUnionTypeOrTop (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_BitVector (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_BreakpointBlock (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_BuildFilteredBundleTreeFrom (
		final @NotNull AvailObject object,
		final AvailObject bundleTree)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_Caller (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_Function (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_Code (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_CodePoint (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	boolean o_CompareFromToWithStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anotherObject,
		final int startIndex2)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_CompareFromToWithAnyTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTuple,
		final int startIndex2)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_CompareFromToWithByteStringStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteString,
		final int startIndex2)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_CompareFromToWithByteTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteTuple,
		final int startIndex2)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_CompareFromToWithNybbleTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aNybbleTuple,
		final int startIndex2)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_CompareFromToWithObjectTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anObjectTuple,
		final int startIndex2)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_CompareFromToWithTwoByteStringStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTwoByteString,
		final int startIndex2)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	void o_LazyComplete (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	int o_ComputeHashFromTo (
		final @NotNull AvailObject object,
		final int start,
		final int end)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	@NotNull AvailObject o_ConcatenateTuplesCanDestroy (
		final @NotNull AvailObject object,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_Continuation (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_CopyToRestrictedTo (
		final @NotNull AvailObject object,
		final AvailObject filteredBundleTree,
		final AvailObject visibleNames)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_CopyTupleFromToCanDestroy (
		final @NotNull AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	@NotNull AvailObject o_DataAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_DataAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_DivideCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_DivideIntoInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull Sign sign,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_DivideIntoIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_ElementAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_ElementAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	int o_EndOfZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_EndSubtupleIndexInZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_ExecutionState (
		final @NotNull AvailObject object,
		final ExecutionState value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	byte o_ExtractNybbleFromTupleAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	List<AvailObject> o_FilterByTypes (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	@NotNull AvailObject o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone (
		final @NotNull AvailObject object,
		final int zone,
		final AvailObject newSubtuple,
		final int startSubtupleIndex,
		final int endOfZone)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	boolean o_HasElement (
		final @NotNull AvailObject object,
		final AvailObject elementObject)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	void o_Hash (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	int o_HashFromTo (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_HashOrZero (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	boolean o_HasKey (
		final @NotNull AvailObject object,
		final AvailObject keyObject)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	List<AvailObject> o_ImplementationsAtOrBelow (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	@NotNull AvailObject o_IncludeBundle (
		final @NotNull AvailObject object,
		final AvailObject messageBundle)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	boolean o_IncludesImplementation (
		final @NotNull AvailObject object,
		final AvailObject imp)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	void o_LazyIncomplete (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_Index (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_InternalHash (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_InterruptRequestFlag (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_InvocationCount (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_IsSaved (
		final @NotNull AvailObject object,
		final boolean aBoolean)
	{
		unsupportedOperation();
		return;
	}

	@Override
	boolean o_IsSubsetOf (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfVariableType (
		final @NotNull AvailObject object,
		final AvailObject aVariableType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final AvailObject aMapType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final AvailObject anObjectType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final AvailObject aPrimitiveType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final AvailObject aSetType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfEnumerationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anEnumerationType)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	@NotNull AvailObject o_KeyAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_KeyAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject keyObject)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_LevelTwoChunkOffset (
		final @NotNull AvailObject object,
		final AvailObject chunk,
		final int offset)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_Literal (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_LiteralAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_ArgOrLocalOrStackAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_ArgOrLocalOrStackAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_LocalTypeAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_LookupByTypesFromList (
		final @NotNull AvailObject object,
		final List<AvailObject> argumentTypeList)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_LookupByTypesFromContinuationStackp (
		final @NotNull AvailObject object,
		final AvailObject continuation,
		final int stackp)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_LookupByTypesFromTuple (
		final @NotNull AvailObject object,
		final AvailObject argumentTypeTuple)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_LookupByValuesFromList (
		final @NotNull AvailObject object,
		final List<AvailObject> argumentList)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_LookupByValuesFromTuple (
		final @NotNull AvailObject object,
		final AvailObject argumentTuple)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_MapAt (
		final @NotNull AvailObject object,
		final AvailObject keyObject)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_MapAtPuttingCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_MapSize (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_MapWithoutKeyCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject keyObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_MinusCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_MultiplyByInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull Sign sign,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_MultiplyByIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_MyType (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_Name (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	boolean o_NameVisible (
		final @NotNull AvailObject object,
		final AvailObject trueName)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	void o_NumBlanks (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	boolean o_OptionallyNilOuterVar (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	@NotNull AvailObject o_OuterTypeAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_OuterVarAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_OuterVarAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_Parent (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_Pc (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_PlusCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_Priority (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_PrivateAddElement (
		final @NotNull AvailObject object,
		final AvailObject element)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_PrivateExcludeElement (
		final @NotNull AvailObject object,
		final AvailObject element)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_PrivateExcludeElementKnownIndex (
		final @NotNull AvailObject object,
		final AvailObject element,
		final int knownIndex)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_PrivateExcludeKey (
		final @NotNull AvailObject object,
		final AvailObject keyObject)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_PrivateMapAtPut (
		final @NotNull AvailObject object,
		final AvailObject keyObject,
		final AvailObject valueObject)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_ProcessGlobals (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	short o_RawByteAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_RawByteAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		unsupportedOperation();
		return;
	}

	@Override
	short o_RawByteForCharacterAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_RawByteForCharacterAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		unsupportedOperation();
		return;
	}

	@Override
	byte o_RawNybbleAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_RawNybbleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final byte aNybble)
	{
		unsupportedOperation();
		return;
	}

	@Override
	short o_RawShortForCharacterAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_RawShortForCharacterAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		unsupportedOperation();
		return;
	}

	@Override
	int o_RawSignedIntegerAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_RawSignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	long o_RawUnsignedIntegerAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_RawUnsignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_RemoveDependentChunkIndex (
		final @NotNull AvailObject object,
		final int aChunkIndex)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_RemoveFrom (
		final @NotNull AvailObject object,
		final L2Interpreter anInterpreter)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_RemoveImplementation (
		final @NotNull AvailObject object,
		final AvailObject implementation)
	{
		unsupportedOperation();
		return;
	}

	@Override
	boolean o_RemoveBundle (
		final @NotNull AvailObject object,
		final AvailObject bundle)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	void o_RemoveRestrictions (
		final @NotNull AvailObject object,
		final AvailObject obsoleteRestrictions)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_ResolvedForwardWithName (
		final @NotNull AvailObject object,
		final AvailObject forwardImplementation,
		final AvailObject methodName)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_ReturnType (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_RootBin (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_SetIntersectionCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_SetMinusCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_SetSubtupleForZoneTo (
		final @NotNull AvailObject object,
		final int zoneIndex,
		final AvailObject newTuple)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_SetUnionCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_SetValue (
		final @NotNull AvailObject object,
		final AvailObject newValue)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_SetWithElementCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject newElementObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_SetWithoutElementCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject elementObjectToExclude,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_Size (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	int o_SizeOfZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_LazySpecialActions (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_StackAt (
		final @NotNull AvailObject object,
		final int slotIndex)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_StackAtPut (
		final @NotNull AvailObject object,
		final int slotIndex,
		final AvailObject anObject)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_Stackp (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_Start (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_StartingChunk (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	int o_StartOfZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_StartSubtupleIndexInZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_String (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_SubtractFromInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull Sign sign,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_SubtractFromIntegerCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_SubtupleForZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TimesCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_TokenType (
		final @NotNull AvailObject object,
		final TokenDescriptor.TokenType value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	int o_TranslateToZone (
		final @NotNull AvailObject object,
		final int tupleIndex,
		final int zoneIndex)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	@NotNull AvailObject o_TrueNamesForStringName (
		final @NotNull AvailObject object,
		final AvailObject stringName)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TruncateTo (
		final @NotNull AvailObject object,
		final int newTupleSize)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TupleAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_TupleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject aNybbleObject)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_TupleAtPuttingCanDestroy (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_TupleIntAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_Type (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfVariableType (
		final @NotNull AvailObject object,
		final AvailObject aVariableType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final AvailObject aMapType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final AvailObject someMeta)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final AvailObject anObjectType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final AvailObject aSetType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfVariableType (
		final @NotNull AvailObject object,
		final AvailObject aVariableType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final AvailObject aMapType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final AvailObject anObjectType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final AvailObject aSetType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_Unclassified (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_UntranslatedDataAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_UntranslatedDataAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_ValidateArgumentTypesInterpreterIfFail (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes,
		final Interpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_Value (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_ValueAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_ValueAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject valueObject)
	{
		unsupportedOperation();
		return;
	}

	@Override
	int o_ZoneForIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	String o_AsNativeString (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return "";
	}

	@Override
	@NotNull AvailObject o_AsObject (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_AsSet (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_AsTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_BitsPerEntry (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_BitVector (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	@NotNull AvailObject o_BodyBlock (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_BodySignature (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_BreakpointBlock (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_Caller (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_Capacity (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_CleanUpAfterCompile (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_ClearValue (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_Function (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_FunctionType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_Code (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_CodePoint (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	@NotNull AvailObject o_LazyComplete (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_ConstantBindings (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_ContentType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_Continuation (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_CopyAsMutableContinuation (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_CopyAsMutableObjectTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_CopyAsMutableSpliceTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_ParsingPc (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	void o_DisplayTestingTree (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_EnsureMutable (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	ExecutionState o_ExecutionState (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	void o_Expand (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
	}

	@Override
	boolean o_ExtractBoolean (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	short o_ExtractByte (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	double o_ExtractDouble (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0.0d;
	}

	@Override
	float o_ExtractFloat (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0.0f;
	}

	@Override
	int o_ExtractInt (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
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
	@Override
	long o_ExtractLong (final @NotNull AvailObject object)
	{
		error(
			"Subclass responsiblity: o_ExtractLong() in "
			+ getClass().getCanonicalName());
		return 0L;
	}

	@Override
	byte o_ExtractNybble (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	@NotNull AvailObject o_FieldMap (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_FilteredBundleTree (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_GetInteger (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	@NotNull AvailObject o_GetValue (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_HashOrZero (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	boolean o_HasRestrictions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	@NotNull AvailObject o_ImplementationsTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_LazyIncomplete (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_Index (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_InternalHash (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_InterruptRequestFlag (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_InvocationCount (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	boolean o_IsAbstract (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsFinite (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsForward (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsMethod (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsPositive (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSaved (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSplice (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsSupertypeOfBottom (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsValid (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	List<AvailObject> o_KeysAsArray (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	@NotNull AvailObject o_KeysAsSet (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_KeyType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_LevelTwoChunk (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	int o_LevelTwoOffset (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	@NotNull AvailObject o_Literal (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	boolean o_LowerInclusive (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	int o_MapSize (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_MaxStackDepth (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	@NotNull AvailObject o_Message (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_MessageParts (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_Methods (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_MyRestrictions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_Names (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_NewNames (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_NumArgs (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_NumArgsAndLocalsAndStack (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_NumberOfZones (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_NumBlanks (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_NumDoubles (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_NumIntegers (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_NumLiterals (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_NumLocals (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_NumObjects (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_NumOuters (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_NumOuterVars (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	@NotNull AvailObject o_Nybbles (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_Parent (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_Pc (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_PrimitiveNumber (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	AvailObject o_Priority (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	@NotNull AvailObject o_PrivateNames (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_ProcessGlobals (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_ReleaseVariableOrMakeContentsImmutable (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	@Override
	void o_RemoveRestrictions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_GrammaticalRestrictions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_ReturnType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_RootBin (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_SetSize (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	@NotNull AvailObject o_Signature (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_LazySpecialActions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	int o_Stackp (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	int o_Start (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	AvailObject o_StartingChunk (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	void o_Step (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_String (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_TestingTree (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	TokenDescriptor.TokenType o_TokenType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	void o_TrimExcessInts (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	@Override
	int o_TupleSize (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	@NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_Unclassified (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	boolean o_UpperInclusive (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	@NotNull AvailObject o_Value (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_ValuesAsTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_ValueType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_VariableBindings (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_Vectors (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	void o_Verify (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	@Override
	@NotNull AvailObject o_VisibleNames (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	@NotNull AvailObject o_Wordcodes (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}



	// GENERATED special mutable slots

	@Override
	boolean o_Equals (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_EqualsAnyTuple (
		final @NotNull AvailObject object,
		final AvailObject aTuple)
	{
		return false;
	}

	@Override
	boolean o_EqualsByteString (
		final @NotNull AvailObject object,
		final AvailObject aString)
	{
		return false;
	}

	@Override
	boolean o_EqualsByteTuple (
		final @NotNull AvailObject object,
		final AvailObject aTuple)
	{
		return false;
	}

	@Override
	boolean o_EqualsCharacterWithCodePoint (
		final @NotNull AvailObject object,
		final int otherCodePoint)
	{
		return false;
	}

	@Override
	boolean o_EqualsFunction (
		final @NotNull AvailObject object,
		final AvailObject aFunction)
	{
		return false;
	}

	@Override
	boolean o_EqualsFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType)
	{
		return false;
	}

	@Override
	boolean o_EqualsCompiledCode (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCode)
	{
		return false;
	}

	@Override
	boolean o_EqualsVariable (
		final @NotNull AvailObject object,
		final AvailObject aVariable)
	{
		return false;
	}

	@Override
	boolean o_EqualsVariableType (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		return false;
	}

	@Override
	boolean o_EqualsContinuation (
		final @NotNull AvailObject object,
		final AvailObject aContinuation)
	{
		return false;
	}

	@Override
	boolean o_EqualsContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		return false;
	}

	@Override
	boolean o_EqualsCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		return false;
	}

	@Override
	boolean o_EqualsDouble (
		final @NotNull AvailObject object,
		final double aDouble)
	{
		return false;
	}

	@Override
	boolean o_EqualsFloat (
		final @NotNull AvailObject object,
		final float aFloat)
	{
		return false;
	}

	@Override
	boolean o_EqualsInfinity (
		final @NotNull AvailObject object,
		final @NotNull Sign sign)
	{
		return false;
	}

	@Override
	boolean o_EqualsInteger (
		final @NotNull AvailObject object,
		final AvailObject anAvailInteger)
	{
		return false;
	}

	@Override
	boolean o_EqualsIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return false;
	}

	@Override
	boolean o_EqualsMap (
		final @NotNull AvailObject object,
		final AvailObject aMap)
	{
		return false;
	}

	@Override
	boolean o_EqualsMapType (
		final @NotNull AvailObject object,
		final AvailObject aMapType)
	{
		return false;
	}

	@Override
	boolean o_EqualsNybbleTuple (
		final @NotNull AvailObject object,
		final AvailObject aTuple)
	{
		return false;
	}

	@Override
	boolean o_EqualsObject (
		final @NotNull AvailObject object,
		final AvailObject anObject)
	{
		return false;
	}

	@Override
	boolean o_EqualsObjectTuple (
		final @NotNull AvailObject object,
		final AvailObject aTuple)
	{
		return false;
	}

	@Override
	boolean o_EqualsParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType)
	{
		return false;
	}

	@Override
	boolean o_EqualsPojo (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojo)
	{
		return false;
	}

	@Override
	boolean o_EqualsPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return false;
	}

	@Override
	boolean o_EqualsPrimitiveType (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		return false;
	}

	@Override
	boolean o_EqualsRawPojo (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aRawPojo)
	{
		return false;
	}

	@Override
	boolean o_EqualsSet (
		final @NotNull AvailObject object,
		final AvailObject aSet)
	{
		return false;
	}

	@Override
	boolean o_EqualsSetType (
		final @NotNull AvailObject object,
		final AvailObject aSetType)
	{
		return false;
	}

	@Override
	boolean o_EqualsTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType)
	{
		return false;
	}

	@Override
	boolean o_EqualsTwoByteString (
		final @NotNull AvailObject object,
		final AvailObject aString)
	{
		return false;
	}

	@Override
	boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final AvailObject potentialInstance)
	{
		//  The potentialInstance is a user-defined object.  See if it is an instance of me.

		return false;
	}

	@Override
	boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final AvailObject anotherObject)
	{
		//  Given two objects that are known to be equal, is the first one in a better form (more
		//  compact, more efficient, older generation) than the second one?

		return object.objectSlotsCount() + object.integerSlotsCount() < anotherObject.objectSlotsCount() + anotherObject.integerSlotsCount();
	}

	@Override
	boolean o_IsBetterRepresentationThanTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType)
	{
		//  Given two objects that are known to be equal, the second of which is in the form of
		//  a tuple type, is the first one in a better form than the second one?

		//  Explanation: This must be called with a tuple type as the second argument, but
		//  the two arguments must also be equal.  All alternative implementations of tuple
		//  types should reimplement this method.
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		return object.kind().isSubtypeOf(aType);
	}

	@Override
	boolean o_EqualsBlank (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_EqualsNull (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_EqualsNullOrBlank (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer a 32-bit long that is always the same for equal objects, but
		//  statistically different for different objects.

		unsupportedOperation();
		return 0;
	}

	@Override
	boolean o_IsFunction (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	@NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
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
	 * A statically cached visitor instance to avoid having to create it at
	 * runtime.
	 */
	final static AvailBeImmutableSubobjectVisitor beImmutableSubobjectVisitor =
		new AvailBeImmutableSubobjectVisitor();

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Make my subobjects be immutable.  Don't change my own mutability state.
	 * Also, ignore my mutability state, as it should be tested (and sometimes
	 * set preemptively to immutable) prior to invoking this method.
	 * </p>
	 */
	@Override
	void o_MakeSubobjectsImmutable (
		final @NotNull AvailObject object)
	{
		object.scanSubobjects(beImmutableSubobjectVisitor);
	}

	@Override
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		unsupportedOperation();
		return NullDescriptor.nullObject();
	}



	@Override
	boolean o_IsBoolean (
		final @NotNull AvailObject object)
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
	@Override
	boolean o_IsByteTuple (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_IsCharacter (
		final @NotNull AvailObject object)
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
	@Override
	boolean o_IsString (final @NotNull AvailObject object)
	{
		return false;
	}


	@Override
	boolean o_ContainsBlock (
		final @NotNull AvailObject object,
		final AvailObject aFunction)
	{
		//  Answer true if either I am aFunction or I contain aFunction.  I only follow
		//  the trail of literal compiledCode and function objects, so this is a dead end.

		return false;
	}



	@Override
	void o_PostFault (
		final @NotNull AvailObject object)
	{
		//  The object was just scanned, and its pointers converted into valid ToSpace pointers.
		//  Do any follow-up activities specific to the kind of object it is.
		//
		//  do nothing


	}

	@Override
	void o_ReadBarrierFault (
		final @NotNull AvailObject object)
	{
		//  The object is in ToSpace, and its fields already refer to ToSpace objects.  Do nothing,
		//  as there is no read barrier.  See also implementation in GCReadBarrierDescriptor.
		//
		//  do nothing


	}

	@Override
	@NotNull AvailObject o_Traversed (
		final @NotNull AvailObject object)
	{
		//  Overridden in IndirectionDescriptor to skip over indirections.

		return object;
	}



	@Override
	boolean o_IsMap (
		final @NotNull AvailObject object)
	{
		return false;
	}



	@Override
	boolean o_IsByte (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_IsNybble (
		final @NotNull AvailObject object)
	{
		return false;
	}



	@Override
	boolean o_IsSet (
		final @NotNull AvailObject object)
	{
		return false;
	}



	@Override
	@NotNull AvailObject o_BinAddingElementHashLevelCanDestroy (
		final @NotNull AvailObject object,
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
		final AvailObject result =
			LinearSetBinDescriptor.isMutableLevel(true, myLevel)
				.create(2);
		result.binHash(object.hash() + elementObject.hash());
		result.binElementAtPut(1, object);
		result.binElementAtPut(2, elementObject);
		if (!canDestroy)
		{
			result.makeImmutable();
		}
		return result;
	}

	@Override
	boolean o_BinHasElementHash (
		final @NotNull AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash)
	{
		//  Elements are treated as bins to save space, since bins are not
		//  entirely first-class objects (i.e., they can't be added to sets.

		return object.equals(elementObject);
	}

	/**
	 * Remove elementObject from the bin object, if present. Answer the
	 * resulting bin. The bin may be modified if it's mutable and canDestroy.
	 * In particular, an element is masquerading as a bin of size one, so the
	 * answer must be either the object or null object (to indicate a size zero
	 * bin).
	 *
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @param canDestroy
	 * @return
	 */
	@Override
	@NotNull AvailObject o_BinRemoveElementHashCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{

		if (object.equals(elementObject))
		{
			return NullDescriptor.nullObject();
		}
		if (!canDestroy)
		{
			object.makeImmutable();
		}
		return object;
	}

	/**
	 * Sets only use explicit bins for collisions, otherwise they store the
	 * element itself. This works because a bin can't be an element of a set.
	 * Likewise, the null object can't be a member of a set and is treated like
	 * an empty bin.
	 *
	 * @param object
	 * @param potentialSuperset
	 * @return
	 */
	@Override
	boolean o_IsBinSubsetOf (
		final @NotNull AvailObject object,
		final AvailObject potentialSuperset)
	{
		return potentialSuperset.hasElement(object);
	}

	@Override
	int o_PopulateTupleStartingAt (
		final @NotNull AvailObject object,
		final AvailObject mutableTuple,
		final int startingIndex)
	{
		//  Write set bin elements into the tuple, starting at the given startingIndex.  Answer
		//  the next available index in which to write.  Regular objects act as set bins
		//  of size 1, so treat them that way.

		assert mutableTuple.descriptor().isMutable();
		mutableTuple.tupleAtPut(startingIndex, object);
		return startingIndex + 1;
	}

	@Override
	int o_BinHash (
		final @NotNull AvailObject object)
	{
		//  An object masquerading as a size one bin has a bin hash which is the sum of
		//  the elements' hashes, which in this case is just the object's hash.

		return object.hash();
	}

	@Override
	int o_BinSize (
		final @NotNull AvailObject object)
	{
		//  Answer how many elements this bin contains.  I act as a bin of size one.

		return 1;
	}

	@Override
	@NotNull AvailObject o_BinUnionTypeOrTop (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	boolean o_IsTuple (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	int o_HashOfType (
		final @NotNull AvailObject object)
	{
		//  We are computing the hash value of some ApproximateType, and it has
		//  delegated responsibility back to this descriptor, the one that created the
		//  ApproximateType that we're now trying to hash.  Only subclasses that
		//  answer true to the query canComputeHashOfType need to implement
		//  this method.

		//  only provide if subclass canComputeHashOfType.
		unsupportedOperation();
		return 0;
	}

	@Override
	boolean o_IsAtom (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_IsExtendedInteger (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_IsMapType (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_IsSetType (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_IsTupleType (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_IsType (
		final @NotNull AvailObject object)
	{
		return false;
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
	@Override
	@NotNull Iterator<AvailObject> o_Iterator (
		final @NotNull AvailObject object)
	{
		error(
			"Subclass responsibility: o_Iterator() in "
			+ getClass().getCanonicalName(),
			object);
		return null;
	}

	@Override
	@NotNull AvailObject o_ParsingInstructions (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	void o_Expression (
		final @NotNull AvailObject object,
		final AvailObject expression)
	{
		unsupportedOperation();
	}

	@Override
	@NotNull AvailObject o_Expression (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	void o_Variable (
		final @NotNull AvailObject object,
		final AvailObject variable)
	{
		unsupportedOperation();
	}

	@Override
	@NotNull AvailObject o_Variable (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	@NotNull AvailObject o_ArgumentsTuple (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	@NotNull AvailObject o_StatementsTuple (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	@NotNull AvailObject o_ResultType (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_NeededVariables (
		final @NotNull AvailObject object,
		final AvailObject neededVariables)
	{
		unsupportedOperation();
	}


	@Override
	@NotNull AvailObject o_NeededVariables (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	int o_Primitive (final AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}


	@Override
	@NotNull AvailObject o_DeclaredType (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	DeclarationKind o_DeclarationKind (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	@NotNull AvailObject o_InitializationExpression (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_InitializationExpression (
		final @NotNull AvailObject object,
		final AvailObject initializationExpression)
	{
		unsupportedOperation();
	}


	@Override
	@NotNull AvailObject o_LiteralObject (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	@NotNull AvailObject o_Token (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	@NotNull AvailObject o_MarkerValue (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_MarkerValue (final @NotNull AvailObject object, final @NotNull AvailObject markerValue)
	{
		unsupportedOperation();
	}


	@Override
	@NotNull AvailObject o_Arguments (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_Arguments (
		final @NotNull AvailObject object,
		final @NotNull AvailObject arguments)
	{
		unsupportedOperation();
	}


	@Override
	@NotNull AvailObject o_Method(final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_Method (
		final @NotNull AvailObject object,
		final AvailObject method)
	{
		unsupportedOperation();
	}


	@Override
	@NotNull AvailObject o_SuperCastType (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_SuperCastType (
		final @NotNull AvailObject object,
		final AvailObject superCastType)
	{
		unsupportedOperation();
	}


	@Override
	@NotNull AvailObject o_ExpressionsTuple (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	@NotNull AvailObject o_Declaration (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	@NotNull AvailObject o_ExpressionType (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	void o_EmitEffectOn (
		 final @NotNull AvailObject object,
		 final AvailCodeGenerator codeGenerator)
	{
		unsupportedOperation();
	}


	@Override
	void o_EmitValueOn (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		unsupportedOperation();
	}


	/**
	 * Map my children through the (destructive) transformation specified by
	 * aBlock.
	 */
	@Override
	void o_ChildrenMap (
		final @NotNull AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		unsupportedOperation();
	}


	/**
	 * Visit my child parse nodes with aBlock.
	 */
	@Override
	void o_ChildrenDo (
		final @NotNull AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		unsupportedOperation();
	}


	@Override
	void o_ValidateLocally (
		 final @NotNull AvailObject object,
		 final AvailObject parent,
		 final List<AvailObject> outerBlocks,
		 final L2Interpreter anAvailInterpreter)
	{
		unsupportedOperation();
	}


	@Override
	@NotNull AvailObject o_Generate (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	@NotNull AvailObject o_CopyWith (
		final @NotNull AvailObject object,
		final AvailObject newParseNode)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_IsLastUse (
		final @NotNull AvailObject object,
		final boolean isLastUse)
	{
		unsupportedOperation();
	}


	@Override
	boolean o_IsLastUse (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}


	@Override
	boolean o_IsMacro (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}


	@Override
	void o_Macros (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
	}


	@Override
	@NotNull AvailObject o_Macros (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	@NotNull AvailObject o_CopyMutableParseNode (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	AvailObject o_BinUnionKind (final AvailObject object)
	{
		// Ordinary (non-bin, non-void) objects act as set bins of size one.
		return object.kind();
	}


	@Override
	void o_MacroName (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
	}


	@Override
	@NotNull AvailObject o_MacroName (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_OutputParseNode (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
	}


	@Override
	@NotNull AvailObject o_OutputParseNode (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	@NotNull AvailObject o_ApparentSendName (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_Statements (
		final @NotNull AvailObject object,
		final AvailObject statementsTuple)
	{
		unsupportedOperation();
	}


	@Override
	AvailObject o_Statements (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_FlattenStatementsInto (
		final @NotNull AvailObject object,
		final List<AvailObject> accumulatedStatements)
	{
		unsupportedOperation();
	}


	@Override
	void o_LineNumber (final AvailObject object, final int value)
	{
		unsupportedOperation();
	}


	@Override
	int o_LineNumber (final AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}


	@Override
	AvailObject o_AllBundles (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	boolean o_IsSetBin (final AvailObject object)
	{
		return false;
	}


	@Override
	MapDescriptor.MapIterable o_MapIterable (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	AvailObject o_Complete (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	AvailObject o_Incomplete (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	AvailObject o_SpecialActions (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	@NotNull AvailObject o_CheckedExceptions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	boolean o_IsInt (
		final @NotNull AvailObject object)
	{
		return false;
	}


	@Override
	boolean o_IsLong (
		final @NotNull AvailObject object)
	{
		return false;
	}


	@Override
	AvailObject o_ArgsTupleType (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	boolean o_EqualsInstanceTypeFor (
		final @NotNull AvailObject object,
		final AvailObject anObject)
	{
		return false;
	}


	@Override
	AvailObject o_Instances (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	boolean o_EqualsEnumerationWithSet (
		final @NotNull AvailObject object,
		final AvailObject set)
	{
		return false;
	}


	@Override
	boolean o_IsEnumeration (final AvailObject object)
	{
		return false;
	}


	@Override
	boolean o_IsInstanceOf (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		if (aType.isEnumeration())
		{
			return aType.enumerationIncludesInstance(object);
		}
		return object.isInstanceOfKind(aType);
	}


	@Override
	boolean o_EnumerationIncludesInstance (
		final @NotNull AvailObject object,
		final AvailObject potentialInstance)
	{
		unsupportedOperation();
		return false;
	}


	@Override
	AvailObject o_ComputeSuperkind (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	void o_SetAtomProperty (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final @NotNull AvailObject value)
	{
		unsupportedOperation();
	}


	@Override
	AvailObject o_GetAtomProperty (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	AvailObject o_InnerKind (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	boolean o_EqualsEnumerationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return false;
	}

	@Override
	boolean o_IsEnumerationType (final @NotNull AvailObject object)
	{
		return false;
	}


	@Override
	@NotNull AvailObject o_ReadType (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	@NotNull AvailObject o_WriteType (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_Versions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		unsupportedOperation();
	}


	@Override
	@NotNull AvailObject o_Versions (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	ParseNodeKind o_ParseNodeKind (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	boolean o_ParseNodeKindIsUnder (
		final @NotNull AvailObject object,
		final @NotNull ParseNodeKind expectedParseNodeKind)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsRawPojo (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	void o_AddTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject restrictionSignature)
	{
		unsupportedOperation();
	}


	@Override
	void o_RemoveTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject restrictionSignature)
	{
		unsupportedOperation();
	}


	@Override
	@NotNull AvailObject o_TypeRestrictions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_AddSealedArgumentsType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType)
	{
		unsupportedOperation();
	}


	@Override
	void o_RemoveSealedArgumentsType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType)
	{
		unsupportedOperation();
	}


	@Override
	AvailObject o_SealedArgumentsTypesTuple (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	void o_AddTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodNameAtom,
		final @NotNull AvailObject typeRestrictionFunction)
	{
		unsupportedOperation();
	}


	@Override
	void o_AddConstantBinding (
		final @NotNull AvailObject object,
		final @NotNull AvailObject name,
		final @NotNull AvailObject constantBinding)
	{
		unsupportedOperation();
	}


	@Override
	void o_AddVariableBinding (
		final @NotNull AvailObject object,
		final @NotNull AvailObject name,
		final @NotNull AvailObject variableBinding)
	{
		unsupportedOperation();
	}


	@Override
	boolean o_IsMethodEmpty (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	@Override
	boolean o_IsPojoSelfType (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	@NotNull AvailObject o_PojoSelfType (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	@NotNull AvailObject o_JavaClass (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	boolean o_IsShort (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	int o_ExtractShort (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	@Override
	boolean o_IsFloat (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_IsDouble (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	@NotNull AvailObject o_RawPojo (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	boolean o_IsPojo (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	boolean o_IsPojoType (final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	public AvailObject o_UpperBoundMap (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public void o_UpperBoundMap (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMap)
	{
		unsupportedOperation();
	}

	@Override
	@NotNull Order o_NumericCompare (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	@NotNull Order o_NumericCompareToInfinity (
		final @NotNull AvailObject object,
		final @NotNull Sign sign)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	@NotNull Order o_NumericCompareToDouble (
		final AvailObject object,
		final double aDouble)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	@NotNull Order o_NumericCompareToInteger (
		final @NotNull AvailObject object,
		final AvailObject anInteger)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	@NotNull AvailObject o_AddToDoubleCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject doubleObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	AvailObject o_AddToFloatCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject floatObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	AvailObject o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	AvailObject o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	AvailObject o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	AvailObject o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	AvailObject o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	AvailObject o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return null;
	}
}
