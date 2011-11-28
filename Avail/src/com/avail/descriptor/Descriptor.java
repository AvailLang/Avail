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

import static com.avail.descriptor.AvailObject.error;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.compiler.*;
import com.avail.compiler.node.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.compiler.node.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.compiler.scanning.TokenDescriptor;
import com.avail.descriptor.ProcessDescriptor.ExecutionState;
import com.avail.exceptions.*;
import com.avail.exceptions.ArithmeticException;
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
	public void o_ScanSubobjects (
		final @NotNull AvailObject object,
		final AvailSubobjectVisitor visitor)
	{
		for (int i = object.objectSlotsCount(); i >= 1; i--)
		{
			final AvailObject child = object.objectSlotAt(
				FakeObjectSlotsForScanning.ALL_OBJECT_SLOTS_,
				i);
			visitor.invoke(object, child);
		}
	}


	@Override
	public boolean o_AcceptsArgTypesFromFunctionType (
		final @NotNull AvailObject object,
		final AvailObject functionType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	@Override
	public boolean o_AcceptsArgumentTypesFromContinuation (
		final @NotNull AvailObject object,
		final AvailObject continuation,
		final int stackp,
		final int numArgs)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param argValues
	 * @return
	 */
	@Override
	public boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final List<AvailObject> argValues)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final AvailObject argTypes)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param arguments
	 * @return
	 */
	@Override
	public boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final AvailObject arguments)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	@Override
	public void o_AddDependentChunkIndex (
		final @NotNull AvailObject object,
		final int aChunkIndex)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param implementation
	 */
	@Override
	public void o_AddImplementation (
		final @NotNull AvailObject object,
		final AvailObject implementation)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param restrictions
	 */
	@Override
	public void o_AddRestrictions (
		final @NotNull AvailObject object,
		final AvailObject restrictions)
	{
		unsupportedOperation();
		return;
	}

	@Override
	public @NotNull AvailObject o_AddToInfinityCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	public @NotNull AvailObject o_AddToIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	@Override
	public void o_AddGrammaticalMessageRestrictions (
		final @NotNull AvailObject object,
		final AvailObject methodName,
		final AvailObject illegalArgMsgs)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param methodName
	 * @param implementation
	 */
	@Override
	public void o_AddMethodImplementation (
		final @NotNull AvailObject object,
		final AvailObject methodName,
		final AvailObject implementation)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param message
	 * @param bundle
	 */
	@Override
	public void o_AtMessageAddBundle (
		final @NotNull AvailObject object,
		final AvailObject message,
		final AvailObject bundle)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	@Override
	public void o_AtNameAdd (
		final @NotNull AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	@Override
	public void o_AtNewNamePut (
		final @NotNull AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	@Override
	public void o_AtPrivateNameAdd (
		final @NotNull AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_BinElementAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_BinElementAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_BinHash (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_BinSize (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_BinUnionTypeOrTop (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_BitVector (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_BreakpointBlock (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param bundleTree
	 */
	@Override
	public void o_BuildFilteredBundleTreeFrom (
		final @NotNull AvailObject object,
		final AvailObject bundleTree)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Caller (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Function (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Code (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_CodePoint (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
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
	public boolean o_CompareFromToWithStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anotherObject,
		final int startIndex2)
	{
		unsupportedOperation();
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
	public boolean o_CompareFromToWithAnyTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTuple,
		final int startIndex2)
	{
		unsupportedOperation();
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
	public boolean o_CompareFromToWithByteStringStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteString,
		final int startIndex2)
	{
		unsupportedOperation();
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
	public boolean o_CompareFromToWithByteTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteTuple,
		final int startIndex2)
	{
		unsupportedOperation();
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
	public boolean o_CompareFromToWithNybbleTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aNybbleTuple,
		final int startIndex2)
	{
		unsupportedOperation();
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
	public boolean o_CompareFromToWithObjectTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anObjectTuple,
		final int startIndex2)
	{
		unsupportedOperation();
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
	public boolean o_CompareFromToWithTwoByteStringStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTwoByteString,
		final int startIndex2)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_LazyComplete (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param start
	 * @param end
	 * @return
	 */
	@Override
	public int o_ComputeHashFromTo (
		final @NotNull AvailObject object,
		final int start,
		final int end)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ConcatenateTuplesCanDestroy (
		final @NotNull AvailObject object,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Continuation (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param filteredBundleTree
	 * @param visibleNames
	 */
	@Override
	public void o_CopyToRestrictedTo (
		final @NotNull AvailObject object,
		final AvailObject filteredBundleTree,
		final AvailObject visibleNames)
	{
		unsupportedOperation();
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
	public @NotNull AvailObject o_CopyTupleFromToCanDestroy (
		final @NotNull AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_DataAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_DataAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	public @NotNull AvailObject o_DivideCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	public @NotNull AvailObject o_DivideIntoInfinityCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	public @NotNull AvailObject o_DivideIntoIntegerCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInteger,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ElementAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_ElementAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public int o_EndOfZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public int o_EndSubtupleIndexInZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_ExecutionState (
		final @NotNull AvailObject object,
		final ExecutionState value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public byte o_ExtractNybbleFromTupleAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public List<AvailObject> o_FilterByTypes (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		unsupportedOperation();
		return null;
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
	public @NotNull AvailObject o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone (
		final @NotNull AvailObject object,
		final int zone,
		final AvailObject newSubtuple,
		final int startSubtupleIndex,
		final int endOfZone)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean o_GreaterThanInteger (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean o_GreaterThanSignedInfinity (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param elementObject
	 * @return
	 */
	@Override
	public boolean o_HasElement (
		final @NotNull AvailObject object,
		final AvailObject elementObject)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Hash (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	@Override
	public int o_HashFromTo (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_HashOrZero (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	@Override
	public boolean o_HasKey (
		final @NotNull AvailObject object,
		final AvailObject keyObject)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public List<AvailObject> o_ImplementationsAtOrBelow (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @param messageBundle
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_IncludeBundle (
		final @NotNull AvailObject object,
		final AvailObject messageBundle)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param imp
	 * @return
	 */
	@Override
	public boolean o_IncludesImplementation (
		final @NotNull AvailObject object,
		final AvailObject imp)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_LazyIncomplete (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Index (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_InternalHash (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_InterruptRequestFlag (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_InvocationCount (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param aBoolean
	 */
	@Override
	public void o_IsSaved (
		final @NotNull AvailObject object,
		final boolean aBoolean)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean o_IsSubsetOf (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfContainerType (
		final @NotNull AvailObject object,
		final AvailObject aContainerType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final AvailObject aMapType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final AvailObject anObjectType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aPrimitiveType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final AvailObject aPrimitiveType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final AvailObject aSetType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aUnionMeta
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfUnionMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aUnionMeta)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_KeyAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param keyObject
	 */
	@Override
	public void o_KeyAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject keyObject)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean o_LessOrEqual (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean o_LessThan (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param chunk
	 * @param offset
	 */
	@Override
	public void o_LevelTwoChunkOffset (
		final @NotNull AvailObject object,
		final AvailObject chunk,
		final int offset)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Literal (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LiteralAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ArgOrLocalOrStackAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_ArgOrLocalOrStackAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LocalTypeAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param argumentTypeList
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LookupByTypesFromList (
		final @NotNull AvailObject object,
		final List<AvailObject> argumentTypeList)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LookupByTypesFromContinuationStackp (
		final @NotNull AvailObject object,
		final AvailObject continuation,
		final int stackp)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param argumentTypeTuple
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LookupByTypesFromTuple (
		final @NotNull AvailObject object,
		final AvailObject argumentTypeTuple)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param argumentList
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LookupByValuesFromList (
		final @NotNull AvailObject object,
		final List<AvailObject> argumentList)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param argumentTuple
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LookupByValuesFromTuple (
		final @NotNull AvailObject object,
		final AvailObject argumentTuple)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_MapAt (
		final @NotNull AvailObject object,
		final AvailObject keyObject)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param keyObject
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_MapAtPuttingCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_MapSize (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param keyObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_MapWithoutKeyCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject keyObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Message (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_MessageParts (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	public @NotNull AvailObject o_MinusCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	public @NotNull AvailObject o_MultiplyByInfinityCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	public @NotNull AvailObject o_MultiplyByIntegerCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInteger,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_MyRestrictions (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_MyType (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Name (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param trueName
	 * @return
	 */
	@Override
	public boolean o_NameVisible (
		final @NotNull AvailObject object,
		final AvailObject trueName)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_NumBlanks (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public boolean o_OptionallyNilOuterVar (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_OuterTypeAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_OuterVarAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_OuterVarAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Parent (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Pc (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	public @NotNull AvailObject o_PlusCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Priority (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_PrivateAddElement (
		final @NotNull AvailObject object,
		final AvailObject element)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_PrivateExcludeElement (
		final @NotNull AvailObject object,
		final AvailObject element)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param element
	 * @param knownIndex
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_PrivateExcludeElementKnownIndex (
		final @NotNull AvailObject object,
		final AvailObject element,
		final int knownIndex)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_PrivateExcludeKey (
		final @NotNull AvailObject object,
		final AvailObject keyObject)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param keyObject
	 * @param valueObject
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_PrivateMapAtPut (
		final @NotNull AvailObject object,
		final AvailObject keyObject,
		final AvailObject valueObject)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_ProcessGlobals (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public short o_RawByteAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	@Override
	public void o_RawByteAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public short o_RawByteForCharacterAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	@Override
	public void o_RawByteForCharacterAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public byte o_RawNybbleAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param aNybble
	 */
	@Override
	public void o_RawNybbleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final byte aNybble)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public short o_RawShortForCharacterAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	@Override
	public void o_RawShortForCharacterAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public int o_RawSignedIntegerAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_RawSignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public long o_RawUnsignedIntegerAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_RawUnsignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	@Override
	public void o_RemoveDependentChunkIndex (
		final @NotNull AvailObject object,
		final int aChunkIndex)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param anInterpreter
	 */
	@Override
	public void o_RemoveFrom (
		final @NotNull AvailObject object,
		final L2Interpreter anInterpreter)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param implementation
	 */
	@Override
	public void o_RemoveImplementation (
		final @NotNull AvailObject object,
		final AvailObject implementation)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_RemoveBundle (
		final @NotNull AvailObject object,
		final AvailObject bundle)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param obsoleteRestrictions
	 */
	@Override
	public void o_RemoveRestrictions (
		final @NotNull AvailObject object,
		final AvailObject obsoleteRestrictions)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param forwardImplementation
	 * @param methodName
	 */
	@Override
	public void o_ResolvedForwardWithName (
		final @NotNull AvailObject object,
		final AvailObject forwardImplementation,
		final AvailObject methodName)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_ReturnType (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_RootBin (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SetIntersectionCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SetMinusCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param zoneIndex
	 * @param newTuple
	 */
	@Override
	public void o_SetSubtupleForZoneTo (
		final @NotNull AvailObject object,
		final int zoneIndex,
		final AvailObject newTuple)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SetUnionCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param newValue
	 */
	@Override
	public void o_SetValue (
		final @NotNull AvailObject object,
		final AvailObject newValue)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param newElementObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SetWithElementCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject newElementObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param elementObjectToExclude
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SetWithoutElementCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject elementObjectToExclude,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Size (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public int o_SizeOfZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_LazySpecialActions (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param slotIndex
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_StackAt (
		final @NotNull AvailObject object,
		final int slotIndex)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param slotIndex
	 * @param anObject
	 */
	@Override
	public void o_StackAtPut (
		final @NotNull AvailObject object,
		final int slotIndex,
		final AvailObject anObject)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Stackp (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Start (
		final @NotNull AvailObject object,
		final int value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_StartingChunk (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public int o_StartOfZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public int o_StartSubtupleIndexInZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_String (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	@Override
	public @NotNull AvailObject o_SubtractFromInfinityCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SubtractFromIntegerCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SubtupleForZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	@Override
	public @NotNull AvailObject o_TimesCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_TokenType (
		final @NotNull AvailObject object,
		final TokenDescriptor.TokenType value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param tupleIndex
	 * @param zoneIndex
	 * @return
	 */
	@Override
	public int o_TranslateToZone (
		final @NotNull AvailObject object,
		final int tupleIndex,
		final int zoneIndex)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param stringName
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TrueNamesForStringName (
		final @NotNull AvailObject object,
		final AvailObject stringName)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param newTupleSize
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TruncateTo (
		final @NotNull AvailObject object,
		final int newTupleSize)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TupleAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param aNybbleObject
	 */
	@Override
	public void o_TupleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject aNybbleObject)
	{
		unsupportedOperation();
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
	public @NotNull AvailObject o_TupleAtPuttingCanDestroy (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public int o_TupleIntAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Type (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContainerType (
		final @NotNull AvailObject object,
		final AvailObject aContainerType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final AvailObject aMapType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param someMeta
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final AvailObject someMeta)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final AvailObject anObjectType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final AvailObject aSetType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfContainerType (
		final @NotNull AvailObject object,
		final AvailObject aContainerType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final AvailObject aMapType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final AvailObject anObjectType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final AvailObject aSetType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Unclassified (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public int o_UntranslatedDataAt (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_UntranslatedDataAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		unsupportedOperation();
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
	public @NotNull AvailObject o_ValidateArgumentTypesInterpreterIfFail (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes,
		final Interpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Value (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ValueAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param valueObject
	 */
	@Override
	public void o_ValueAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject valueObject)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public int o_ZoneForIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public String o_AsNativeString (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return "";
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_AsObject (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_AsSet (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_AsTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_BitsPerEntry (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_BitVector (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_BodyBlock (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_BodySignature (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_BreakpointBlock (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Caller (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Capacity (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_CleanUpAfterCompile (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_ClearValue (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Function (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_FunctionType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Code (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_CodePoint (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LazyComplete (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ConstantBindings (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ContentType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Continuation (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_CopyAsMutableContinuation (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_CopyAsMutableObjectTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_CopyAsMutableSpliceTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_ParsingPc (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_DisplayTestingTree (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_EnsureMutable (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public ExecutionState o_ExecutionState (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public void o_Expand (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_ExtractBoolean (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public short o_ExtractByte (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public double o_ExtractDouble (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0.0d;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public float o_ExtractFloat (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0.0f;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_ExtractInt (
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
	/**
	 * @param object
	 * @return
	 */
	@Override
	public long o_ExtractLong (final @NotNull AvailObject object)
	{
		error(
			"Subclass responsiblity: o_ExtractLong() in "
			+ getClass().getCanonicalName());
		return 0L;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public byte o_ExtractNybble (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_FieldMap (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_FilteredBundleTree (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_GetInteger (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_GetValue (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_HashOrZero (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_HasRestrictions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ImplementationsTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LazyIncomplete (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Index (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_InternalHash (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_InterruptRequestFlag (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_InvocationCount (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsAbstract (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsFinite (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsForward (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsMethod (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsPositive (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsSaved (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsSplice (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfBottom (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsValid (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public List<AvailObject> o_KeysAsArray (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_KeysAsSet (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_KeyType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LevelTwoChunk (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_LevelTwoOffset (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Literal (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_LowerInclusive (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_MapSize (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_MaxStackDepth (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Message (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_MessageParts (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Methods (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_MyRestrictions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Name (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Names (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_NewNames (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumArgs (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumArgsAndLocalsAndStack (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumberOfZones (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumBlanks (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumDoubles (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumIntegers (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumLiterals (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumLocals (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumObjects (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumOuters (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumOuterVars (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Nybbles (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Parent (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Pc (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_PrimitiveNumber (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject o_Priority (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_PrivateNames (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ProcessGlobals (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 */
	@Override
	public void o_ReleaseVariableOrMakeContentsImmutable (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_RemoveRestrictions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_GrammaticalRestrictions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ReturnType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_RootBin (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_SetSize (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Signature (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LazySpecialActions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Stackp (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Start (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject o_StartingChunk (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_Step (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_String (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TestingTree (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public TokenDescriptor.TokenType o_TokenType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_TrimExcessInts (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_TupleSize (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Unclassified (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_UpperInclusive (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Value (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ValuesAsTuple (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ValueType (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_VariableBindings (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Vectors (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 */
	@Override
	public void o_Verify (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_VisibleNames (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_InfinitySign (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Wordcodes (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return NullDescriptor.nullObject();
	}



	// GENERATED special mutable slots

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	@Override
	public boolean o_EqualsAnyTuple (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsByteString (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsByteTuple (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsCharacterWithCodePoint (
		final @NotNull AvailObject object,
		final int otherCodePoint)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aFunction
	 * @return
	 */
	@Override
	public boolean o_EqualsFunction (
		final @NotNull AvailObject object,
		final AvailObject aFunction)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	@Override
	public boolean o_EqualsFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aCompiledCode
	 * @return
	 */
	@Override
	public boolean o_EqualsCompiledCode (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsContainer (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsContainerType (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsContinuation (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	@Override
	public boolean o_EqualsCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aDoubleObject
	 * @return
	 */
	@Override
	public boolean o_EqualsDouble (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsFloat (
		final @NotNull AvailObject object,
		final AvailObject aFloatObject)
	{
		return false;
	}

	/**
	 * @param object
	 * @param anInfinity
	 * @return
	 */
	@Override
	public boolean o_EqualsInfinity (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsInteger (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aMap
	 * @return
	 */
	@Override
	public boolean o_EqualsMap (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsMapType (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsNybbleTuple (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsObject (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsObjectTuple (
		final @NotNull AvailObject object,
		final AvailObject aTuple)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	@Override
	public boolean o_EqualsParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	@Override
	public boolean o_EqualsPrimitiveType (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsSet (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsSetType (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsTupleType (
		final @NotNull AvailObject object,
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
	public boolean o_EqualsTwoByteString (
		final @NotNull AvailObject object,
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
	public boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
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
	public boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final AvailObject anotherObject)
	{
		//  Given two objects that are known to be equal, is the first one in a better form (more
		//  compact, more efficient, older generation) than the second one?

		return object.objectSlotsCount() + object.integerSlotsCount() < anotherObject.objectSlotsCount() + anotherObject.integerSlotsCount();
	}

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	@Override
	public boolean o_IsBetterRepresentationThanTupleType (
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

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	@Override
	public boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		return object.kind().isSubtypeOf(aType);
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_EqualsBlank (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_EqualsNull (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_EqualsNullOrBlank (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer a 32-bit long that is always the same for equal objects, but
		//  statistically different for different objects.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsFunction (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_MakeImmutable (
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
	public void o_MakeSubobjectsImmutable (
		final @NotNull AvailObject object)
	{
		object.scanSubobjects(beImmutableSubobjectVisitor);
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		unsupportedOperation();
		return NullDescriptor.nullObject();
	}



	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsBoolean (
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
	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsByteTuple (final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsCharacter (
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
	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsString (final @NotNull AvailObject object)
	{
		return false;
	}


	/**
	 * @param object
	 * @param aFunction
	 * @return
	 */
	@Override
	public boolean o_ContainsBlock (
		final @NotNull AvailObject object,
		final AvailObject aFunction)
	{
		//  Answer true if either I am aFunction or I contain aFunction.  I only follow
		//  the trail of literal compiledCode and function objects, so this is a dead end.

		return false;
	}



	/**
	 * @param object
	 */
	@Override
	public void o_PostFault (
		final @NotNull AvailObject object)
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
	public void o_ReadBarrierFault (
		final @NotNull AvailObject object)
	{
		//  The object is in ToSpace, and its fields already refer to ToSpace objects.  Do nothing,
		//  as there is no read barrier.  See also implementation in GCReadBarrierDescriptor.
		//
		//  do nothing


	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Traversed (
		final @NotNull AvailObject object)
	{
		//  Overridden in IndirectionDescriptor to skip over indirections.

		return object;
	}



	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsMap (
		final @NotNull AvailObject object)
	{
		return false;
	}



	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsByte (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsNybble (
		final @NotNull AvailObject object)
	{
		return false;
	}



	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsSet (
		final @NotNull AvailObject object)
	{
		return false;
	}



	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @param myLevel
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_BinAddingElementHashLevelCanDestroy (
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

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @return
	 */
	@Override
	public boolean o_BinHasElementHash (
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
	public @NotNull AvailObject o_BinRemoveElementHashCanDestroy (
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
	public boolean o_IsBinSubsetOf (
		final @NotNull AvailObject object,
		final AvailObject potentialSuperset)
	{
		return potentialSuperset.hasElement(object);
	}

	/**
	 * @param object
	 * @param mutableTuple
	 * @param startingIndex
	 * @return
	 */
	@Override
	public int o_PopulateTupleStartingAt (
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

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_BinHash (
		final @NotNull AvailObject object)
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
	public int o_BinSize (
		final @NotNull AvailObject object)
	{
		//  Answer how many elements this bin contains.  I act as a bin of size one.

		return 1;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_BinUnionTypeOrTop (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsTuple (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	@Override
	public boolean o_TypeEquals (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		//  Answer whether object's type is equal to aType (known to be a type).
		//  The current message may only be sent if the subclass receiving it has
		//  overridden o_CanComputeHashOfType to answer true.

		//  only provide if subclass canComputeHashOfType.
		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_HashOfType (
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

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsAtom (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsExtendedInteger (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsMapType (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsSetType (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsTupleType (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsType (
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
	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull Iterator<AvailObject> o_Iterator (
		final @NotNull AvailObject object)
	{
		error(
			"Subclass responsibility: o_Iterator() in "
			+ getClass().getCanonicalName(),
			object);
		return null;
	}


	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_ParsingInstructions (
		final @NotNull AvailObject object,
		final AvailObject instructionsTuple)
	{
		unsupportedOperation();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public @NotNull AvailObject o_ParsingInstructions (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public void o_Expression (
		final @NotNull AvailObject object,
		final AvailObject expression)
	{
		unsupportedOperation();
	}

	@Override
	public @NotNull AvailObject o_Expression (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public void o_Variable (
		final @NotNull AvailObject object,
		final AvailObject variable)
	{
		unsupportedOperation();
	}

	@Override
	public @NotNull AvailObject o_Variable (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ArgumentsTuple (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_StatementsTuple (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ResultType (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	/**
	 * @param object
	 * @param neededVariables
	 */
	@Override
	public void o_NeededVariables (
		final @NotNull AvailObject object,
		final AvailObject neededVariables)
	{
		unsupportedOperation();
	}


	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_NeededVariables (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Primitive (final AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}


	@Override
	public @NotNull AvailObject o_DeclaredType (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public DeclarationKind o_DeclarationKind (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public @NotNull AvailObject o_InitializationExpression (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_InitializationExpression (
		final @NotNull AvailObject object,
		final AvailObject initializationExpression)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_LiteralObject (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public @NotNull AvailObject o_Token (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public @NotNull AvailObject o_MarkerValue (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_MarkerValue (final @NotNull AvailObject object, final @NotNull AvailObject markerValue)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_Arguments (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_Arguments (
		final @NotNull AvailObject object,
		final @NotNull AvailObject arguments)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_ImplementationSet(final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_ImplementationSet (
		final @NotNull AvailObject object,
		final AvailObject implementationSet)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_SuperCastType (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_SuperCastType (
		final @NotNull AvailObject object,
		final AvailObject superCastType)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_ExpressionsTuple (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_ExpressionsTuple (
		final @NotNull AvailObject object,
		final AvailObject expressionsTuple)
	{
		unsupportedOperation();
	}


	@Override
	public void o_TupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_TupleType (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_Declaration (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_ExpressionType (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public void o_EmitEffectOn (
		 final @NotNull AvailObject object,
		 final AvailCodeGenerator codeGenerator)
	{
		unsupportedOperation();
	}


	@Override
	public void o_EmitValueOn (
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
	public void o_ChildrenMap (
		final @NotNull AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		unsupportedOperation();
	}


	/**
	 * Visit my child parse nodes with aBlock.
	 */
	@Override
	public void o_ChildrenDo (
		final @NotNull AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		unsupportedOperation();
	}


	@Override
	public void o_ValidateLocally (
		 final @NotNull AvailObject object,
		 final AvailObject parent,
		 final List<AvailObject> outerBlocks,
		 final L2Interpreter anAvailInterpreter)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_Generate (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public @NotNull AvailObject o_CopyWith (
		final @NotNull AvailObject object,
		final AvailObject newParseNode)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_IsLastUse (
		final @NotNull AvailObject object,
		final boolean isLastUse)
	{
		unsupportedOperation();
	}


	@Override
	public boolean o_IsLastUse (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}


	@Override
	public boolean o_IsMacro (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}


	@Override
	public void o_Macros (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_Macros (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_CopyMutableParseNode (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public AvailObject o_BinUnionKind (final AvailObject object)
	{
		// Ordinary (non-bin, non-void) objects act as set bins of size one.
		return object.kind();
	}


	@Override
	public void o_MacroName (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_MacroName (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_OutputParseNode (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_OutputParseNode (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public @NotNull AvailObject o_ApparentSendName (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_Statements (
		final @NotNull AvailObject object,
		final AvailObject statementsTuple)
	{
		unsupportedOperation();
	}


	@Override
	public AvailObject o_Statements (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_FlattenStatementsInto (
		final @NotNull AvailObject object,
		final List<AvailObject> accumulatedStatements)
	{
		unsupportedOperation();
	}


	@Override
	public void o_LineNumber (final AvailObject object, final int value)
	{
		unsupportedOperation();
	}


	@Override
	public int o_LineNumber (final AvailObject object)
	{
		unsupportedOperation();
		return 0;
	}


	@Override
	public AvailObject o_AllBundles (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public boolean o_IsSetBin (final AvailObject object)
	{
		return false;
	}


	@Override
	public MapDescriptor.MapIterable o_MapIterable (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public AvailObject o_Complete (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public AvailObject o_Incomplete (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public AvailObject o_SpecialActions (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_CheckedExceptions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public boolean o_IsInt (
		final @NotNull AvailObject object)
	{
		return false;
	}


	@Override
	public boolean o_IsLong (
		final @NotNull AvailObject object)
	{
		return false;
	}


	@Override
	public AvailObject o_ArgsTupleType (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public boolean o_EqualsInstanceTypeFor (
		final @NotNull AvailObject object,
		final AvailObject anObject)
	{
		return false;
	}


	@Override
	public AvailObject o_Instances (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public boolean o_EqualsUnionTypeWithSet (
		final @NotNull AvailObject object,
		final AvailObject set)
	{
		return false;
	}


	@Override
	public boolean o_IsAbstractUnionType (final AvailObject object)
	{
		return false;
	}


	@Override
	public boolean o_IsInstanceOf (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		if (aType.isAbstractUnionType())
		{
			return aType.abstractUnionTypeIncludesInstance(object);
		}
		return object.isInstanceOfKind(aType);
	}


	@Override
	public boolean o_AbstractUnionTypeIncludesInstance (
		final @NotNull AvailObject object,
		final AvailObject potentialInstance)
	{
		unsupportedOperation();
		return false;
	}


	@Override
	public AvailObject o_ComputeSuperkind (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public void o_SetAtomProperty (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final @NotNull AvailObject value)
	{
		unsupportedOperation();
	}


	@Override
	public AvailObject o_GetAtomProperty (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key)
	{
		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject o_InnerKind (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean o_EqualsUnionMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsUnionMeta (final @NotNull AvailObject object)
	{
		return false;
	}


	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ReadType (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_WriteType (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Versions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		unsupportedOperation();
	}


	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Versions (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public ParseNodeKind o_ParseNodeKind (final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public boolean o_ParseNodeKindIsUnder (
		final @NotNull AvailObject object,
		final @NotNull ParseNodeKind expectedParseNodeKind)
	{
		unsupportedOperation();
		return false;
	}


	@Override
	public void o_AddTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject restrictionSignature)
	{
		unsupportedOperation();
	}


	@Override
	public void o_RemoveTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject restrictionSignature)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_TypeRestrictions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_AddSealedArgumentsType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType)
	{
		unsupportedOperation();
	}


	@Override
	public void o_RemoveSealedArgumentsType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType)
	{
		unsupportedOperation();
	}


	@Override
	public AvailObject o_SealedArgumentsTypesTuple (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_AddTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodNameAtom,
		final @NotNull AvailObject typeRestrictionFunction)
	{
		unsupportedOperation();
	}


	@Override
	public void o_AddConstantBinding (
		final @NotNull AvailObject object,
		final @NotNull AvailObject name,
		final @NotNull AvailObject constantBinding)
	{
		unsupportedOperation();
	}


	@Override
	public void o_AddVariableBinding (
		final @NotNull AvailObject object,
		final @NotNull AvailObject name,
		final @NotNull AvailObject variableBinding)
	{
		unsupportedOperation();
	}


	@Override
	public boolean o_IsImplementationSetEmpty (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return false;
	}
}
