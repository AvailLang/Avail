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
import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.node.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.compiler.scanning.TokenDescriptor;
import com.avail.descriptor.ProcessDescriptor.ExecutionState;
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
	enum FakeObjectSlotsForScanning
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
		final AvailObject object,
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
	public boolean o_AcceptsArgTypesFromClosureType (
		final AvailObject object,
		final AvailObject closureType)
	{
		//  GENERATED pure (abstract) method.

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
	public boolean o_AcceptsArgumentsFromContinuationStackp (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp)
	{
		//  GENERATED pure (abstract) method.

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
	public boolean o_AcceptsArgumentTypesFromContinuationStackp (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public boolean o_AcceptsArrayOfArgTypes (
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param argValues
	 * @return
	 */
	@Override
	public boolean o_AcceptsArrayOfArgValues (
		final AvailObject object,
		final List<AvailObject> argValues)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject argTypes)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject arguments)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	@Override
	public void o_AddDependentChunkIndex (
		final AvailObject object,
		final int aChunkIndex)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param implementation
	 */
	@Override
	public void o_AddImplementation (
		final AvailObject object,
		final AvailObject implementation)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param restrictions
	 */
	@Override
	public void o_AddRestrictions (
		final AvailObject object,
		final AvailObject restrictions)
	{
		//  GENERATED pure (abstract) method.

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
		return VoidDescriptor.voidObject();
	}

	@Override
	public @NotNull AvailObject o_AddToIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ArgTypeAt (
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_ArgTypeAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	@Override
	public void o_AtAddMessageRestrictions (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject illegalArgMsgs)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param methodName
	 * @param implementation
	 */
	@Override
	public void o_AtAddMethodImplementation (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject implementation)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject message,
		final AvailObject bundle)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_BinElementAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_BinHash (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_BinSize (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_BinUnionTypeOrVoid (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_BitVector (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_BodyBlock (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param bb
	 * @param rqb
	 * @param rtb
	 */
	@Override
	public void o_BodyBlockRequiresBlockReturnsBlock (
		final AvailObject object,
		final AvailObject bb,
		final AvailObject rqb,
		final AvailObject rtb)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param signature
	 */
	@Override
	public void o_BodySignature (
		final AvailObject object,
		final AvailObject signature)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param bs
	 * @param rqb
	 * @param rtb
	 */
	@Override
	public void o_BodySignatureRequiresBlockReturnsBlock (
		final AvailObject object,
		final AvailObject bs,
		final AvailObject rqb,
		final AvailObject rtb)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_BreakpointBlock (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param bundleTree
	 */
	@Override
	public void o_BuildFilteredBundleTreeFrom (
		final AvailObject object,
		final AvailObject bundleTree)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Caller (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Closure (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Code (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_CodePoint (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anotherObject,
		final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTuple,
		final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteString,
		final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteTuple,
		final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aNybbleTuple,
		final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anObjectTuple,
		final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTwoByteString,
		final int startIndex2)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_LazyComplete (
		final AvailObject object,
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
		final AvailObject object,
		final int start,
		final int end)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @param anAvailInterpreter
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ComputeReturnTypeFromArgumentTypesInterpreter (
		final AvailObject object,
		final List<AvailObject> argTypes,
		final Interpreter anAvailInterpreter)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ConcatenateTuplesCanDestroy (
		final AvailObject object,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_ConstantBindings (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_ContentType (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Continuation (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject filteredBundleTree,
		final AvailObject visibleNames)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param positiveTuple
	 * @param possibilities
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_CreateTestingTreeWithPositiveMatchesRemainingPossibilities (
		final AvailObject object,
		final AvailObject positiveTuple,
		final AvailObject possibilities)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_DataAtIndex (
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_DataAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_DefaultType (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_DependentChunkIndices (
		final AvailObject object,
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
	public void o_ParsingPc (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

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
		return VoidDescriptor.voidObject();
	}

	@Override
	public @NotNull AvailObject o_DivideIntoInfinityCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	@Override
	public @NotNull AvailObject o_DivideIntoIntegerCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInteger,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ElementAt (
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_ElementAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int zone)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int zone)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_ExecutionState (
		final AvailObject object,
		final ExecutionState value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_FieldMap (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_FieldTypeMap (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	@Override
	public List<AvailObject> o_FilterByTypes (
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_FilteredBundleTree (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_FirstTupleType (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
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
	public @NotNull AvailObject o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone (
		final AvailObject object,
		final int zone,
		final AvailObject newSubtuple,
		final int startSubtupleIndex,
		final int endOfZone)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean o_GreaterThanInteger (
		final AvailObject object,
		final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject elementObject)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Hash (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_HashOrZero (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject keyObject)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_ImplementationsTuple (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param messageBundle
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_IncludeBundle (
		final AvailObject object,
		final AvailObject messageBundle)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param imp
	 * @return
	 */
	@Override
	public boolean o_Includes (
		final AvailObject object,
		final AvailObject imp)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_LazyIncomplete (
		final AvailObject object,
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
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_InnerType (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Instance (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_InternalHash (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_InterruptRequestFlag (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_InvocationCount (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param aBoolean
	 */
	@Override
	public void o_IsSaved (
		final AvailObject object,
		final boolean aBoolean)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject aType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject aContainerType)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aCyclicType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfCyclicType (
		final AvailObject object,
		final AvailObject aCyclicType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject aMapType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param anObjectMeta
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfObjectMeta (
		final AvailObject object,
		final AvailObject anObjectMeta)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject anObjectType)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject aPrimitiveType)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject aSetType)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject aTupleType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param aBoolean
	 */
	@Override
	public void o_IsValid (
		final AvailObject object,
		final boolean aBoolean)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param argTypes
	 * @param interpreter
	 * @return
	 */
	@Override
	public boolean o_IsValidForArgumentTypesInterpreter (
		final AvailObject object,
		final List<AvailObject> argTypes,
		final Interpreter interpreter)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param keyObject
	 */
	@Override
	public void o_KeyAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject keyObject)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_KeyType (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
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
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_LiteralAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ArgOrLocalOrStackAt (
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_ArgOrLocalOrStackAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param argumentTypeList
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LookupByTypesFromList (
		final AvailObject object,
		final List<AvailObject> argumentTypeList)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LookupByTypesFromContinuationStackp (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param argumentTypeTuple
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LookupByTypesFromTuple (
		final AvailObject object,
		final AvailObject argumentTypeTuple)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param argumentList
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LookupByValuesFromList (
		final AvailObject object,
		final List<AvailObject> argumentList)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param argumentTuple
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LookupByValuesFromTuple (
		final AvailObject object,
		final AvailObject argumentTuple)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_LowerBound (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_MapAt (
		final AvailObject object,
		final AvailObject keyObject)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
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
	public @NotNull AvailObject o_MapAtPuttingCanDestroy (
		final AvailObject object,
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_MapSize (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject keyObject,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Message (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_MessageParts (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Methods (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		return VoidDescriptor.voidObject();
	}

	@Override
	public @NotNull AvailObject o_MultiplyByInfinityCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	@Override
	public @NotNull AvailObject o_MultiplyByIntegerCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInteger,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_MyObjectType (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_MyRestrictions (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_MyType (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Name (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Names (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject trueName)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_NewNames (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_NumBlanks (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_NumFloats (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_NumIntegers (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_NumObjects (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param tupleOfOuterTypes
	 * @param tupleOfLocalContainerTypes
	 */
	@Override
	public void o_OuterTypesLocalTypes (
		final AvailObject object,
		final AvailObject tupleOfOuterTypes,
		final AvailObject tupleOfLocalContainerTypes)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_OuterVarAt (
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	@Override
	public void o_OuterVarAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Parent (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Pc (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

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
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Priority (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject element)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_PrivateExcludeElement (
		final AvailObject object,
		final AvailObject element)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param element
	 * @param knownIndex
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_PrivateExcludeElementKnownIndex (
		final AvailObject object,
		final AvailObject element,
		final int knownIndex)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_PrivateExcludeKey (
		final AvailObject object,
		final AvailObject keyObject)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param keyObject
	 * @param valueObject
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_PrivateMapAtPut (
		final AvailObject object,
		final AvailObject keyObject,
		final AvailObject valueObject)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_PrivateNames (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_PrivateTestingTree (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_ProcessGlobals (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index,
		final short anInteger)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index,
		final short anInteger)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index,
		final byte aNybble)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index,
		final short anInteger)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index,
		final int value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	@Override
	public void o_RemoveDependentChunkIndex (
		final AvailObject object,
		final int aChunkIndex)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param anInterpreter
	 */
	@Override
	public void o_RemoveFrom (
		final AvailObject object,
		final Interpreter anInterpreter)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param implementation
	 */
	@Override
	public void o_RemoveImplementation (
		final AvailObject object,
		final AvailObject implementation)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_RemoveBundle (
		final AvailObject object,
		final AvailObject bundle)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @param obsoleteRestrictions
	 */
	@Override
	public void o_RemoveRestrictions (
		final AvailObject object,
		final AvailObject obsoleteRestrictions)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_RequiresBlock (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject forwardImplementation,
		final AvailObject methodName)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Restrictions (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_ReturnsBlock (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_ReturnType (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_RootBin (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_SecondTupleType (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SetMinusCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_SetSize (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param zoneIndex
	 * @param newTuple
	 */
	@Override
	public void o_SetSubtupleForZoneTo (
		final AvailObject object,
		final int zoneIndex,
		final AvailObject newTuple)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param newValue
	 */
	@Override
	public void o_SetValue (
		final AvailObject object,
		final AvailObject newValue)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject newElementObject,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param elementObjectToExclude
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SetWithoutElementCanDestroy (
		final AvailObject object,
		final AvailObject elementObjectToExclude,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Signature (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Size (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int zone)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_SizeRange (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_LazySpecialActions (
		final AvailObject object,
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
		final AvailObject object,
		final int slotIndex)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param slotIndex
	 * @param anObject
	 */
	@Override
	public void o_StackAtPut (
		final AvailObject object,
		final int slotIndex,
		final AvailObject anObject)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Stackp (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Start (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_StartingChunk (
		final AvailObject object,
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
		final AvailObject object,
		final int zone)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int zone)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_String (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SubtupleForZone (
		final AvailObject object,
		final int zone)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	@Override
	public @NotNull AvailObject o_TimesCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException
	{
		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_TokenType (
		final AvailObject object,
		final TokenDescriptor.TokenType value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int tupleIndex,
		final int zoneIndex)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final AvailObject stringName)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param newTupleSize
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TruncateTo (
		final AvailObject object,
		final int newTupleSize)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TupleAt (
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param aNybbleObject
	 */
	@Override
	public void o_TupleAtPut (
		final AvailObject object,
		final int index,
		final AvailObject aNybbleObject)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public int o_TupleIntAt (
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Type (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final AvailObject object,
		final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aClosureType
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfClosureTypeCanDestroy (
		final AvailObject object,
		final AvailObject aClosureType,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aCyclicType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfCyclicType (
		final AvailObject object,
		final AvailObject aCyclicType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfGeneralizedClosureTypeCanDestroy (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param someMeta
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMeta (
		final AvailObject object,
		final AvailObject someMeta)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anObjectMeta
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectMeta (
		final AvailObject object,
		final AvailObject anObjectMeta)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_TypeTuple (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnion (
		final AvailObject object,
		final AvailObject another)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aClosureType
	 * @param canDestroy
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfClosureTypeCanDestroy (
		final AvailObject object,
		final AvailObject aClosureType,
		final boolean canDestroy)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aCyclicType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfCyclicType (
		final AvailObject object,
		final AvailObject aCyclicType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anObjectMeta
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectMeta (
		final AvailObject object,
		final AvailObject anObjectMeta)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeUnionOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Unclassified (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	@Override
	public int o_UntranslatedDataAt (
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_UpperBound (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final List<AvailObject> argTypes,
		final Interpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Validity (
		final AvailObject object,
		final int value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Value (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @param index
	 * @param valueObject
	 */
	@Override
	public void o_ValueAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject valueObject)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_ValueType (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_VariableBindings (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Vectors (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_VisibleNames (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @param value
	 */
	@Override
	public void o_Wordcodes (
		final AvailObject object,
		final AvailObject value)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object,
		final int index)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public String o_AsNativeString (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return "";
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_AsObject (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_AsSet (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_AsTuple (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_BecomeExactType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 */
	@Override
	public void o_BecomeRealTupleType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_BitsPerEntry (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_BitVector (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_BodyBlock (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_BodySignature (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_BreakpointBlock (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Caller (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Capacity (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_CleanUpAfterCompile (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_ClearValue (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Closure (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ClosureType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Code (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_CodePoint (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LazyComplete (
		final AvailObject object)
	{
		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ConstantBindings (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ContentType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Continuation (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_CopyAsMutableContinuation (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_CopyAsMutableObjectTuple (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_CopyAsMutableSpliceTuple (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_CopyMutable (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_DefaultType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_DependentChunkIndices (
		final AvailObject object)
	{
		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_ParsingPc (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_DisplayTestingTree (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_EnsureMetacovariant (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_EnsureMutable (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public ExecutionState o_ExecutionState (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public void o_Expand (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_ExtractBoolean (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public short o_ExtractByte (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public double o_ExtractDouble (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0.0d;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public float o_ExtractFloat (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0.0f;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_ExtractInt (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

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
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_FieldMap (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_FieldTypeMap (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_FilteredBundleTree (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_FirstTupleType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_GetInteger (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_GetValue (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_HashOrZero (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_HasRestrictions (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ImplementationsTuple (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LazyIncomplete (
		final AvailObject object)
	{
		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Index (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_InnerType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Instance (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_InternalHash (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_InterruptRequestFlag (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_InvocationCount (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsAbstract (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsFinite (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsForward (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsMethod (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsPositive (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsSaved (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsSplice (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfTerminates (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsSupertypeOfVoid (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsValid (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public List<AvailObject> o_KeysAsArray (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_KeysAsSet (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_KeyType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LevelTwoChunk (
		final AvailObject object)
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
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Literal (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LowerBound (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_LowerInclusive (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_MapSize (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_MaxStackDepth (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Message (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_MessageParts (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Methods (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_MyObjectType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_MyRestrictions (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_MyType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Name (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Names (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_NewNames (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumArgs (
		final AvailObject object)
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
		final AvailObject object)
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
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumBlanks (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumDoubles (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumIntegers (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumLiterals (
		final AvailObject object)
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
		final AvailObject object)
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
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_NumOuters (
		final AvailObject object)
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
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Nybbles (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Parent (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Pc (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_PrimitiveNumber (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject o_Priority (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_PrivateNames (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_PrivateTestingTree (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ProcessGlobals (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 */
	@Override
	public void o_ReleaseVariableOrMakeContentsImmutable (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_RemoveRestrictions (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_RequiresBlock (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Restrictions (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ReturnsBlock (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ReturnType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_RootBin (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SecondTupleType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_SetSize (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Signature (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_SizeRange (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_LazySpecialActions (
		final AvailObject object)
	{
		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Stackp (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Start (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public AvailObject o_StartingChunk (
		final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_Step (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_String (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TestingTree (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public TokenDescriptor.TokenType o_TokenType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return null;
	}

	/**
	 * @param object
	 */
	@Override
	public void o_TrimExcessInts (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_TupleSize (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_TypeTuple (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Unclassified (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_UpperBound (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_UpperInclusive (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Validity (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Value (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ValuesAsTuple (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ValueType (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_VariableBindings (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Vectors (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 */
	@Override
	public void o_Verify (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_VisibleNames (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_InfinitySign (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return 0;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_Wordcodes (
		final AvailObject object)
	{
		//  GENERATED pure (abstract) method.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}



	// GENERATED special mutable slots

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	@Override
	public boolean o_Equals (
		final AvailObject object,
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
	public boolean o_EqualsByteString (
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
	public boolean o_EqualsByteTuple (
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
	public boolean o_EqualsCharacterWithCodePoint (
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
	public boolean o_EqualsClosure (
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
	public boolean o_EqualsClosureType (
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
	public boolean o_EqualsCompiledCode (
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
	public boolean o_EqualsContainer (
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
	public boolean o_EqualsContainerType (
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
	public boolean o_EqualsContinuation (
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
	public boolean o_EqualsContinuationType (
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
	public boolean o_EqualsDouble (
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
	public boolean o_EqualsFloat (
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
	public boolean o_EqualsGeneralizedClosureType (
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
	public boolean o_EqualsInfinity (
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
	public boolean o_EqualsInteger (
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
	public boolean o_EqualsIntegerRangeType (
		final AvailObject object,
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
	public boolean o_EqualsMapType (
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
	public boolean o_EqualsNybbleTuple (
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
	public boolean o_EqualsObject (
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
	public boolean o_EqualsObjectTuple (
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
	public boolean o_EqualsPrimitiveType (
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
	public boolean o_EqualsSet (
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
	public boolean o_EqualsSetType (
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
	public boolean o_EqualsTupleType (
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
	public boolean o_EqualsTwoByteString (
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
	public boolean o_HasObjectInstance (
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
	public boolean o_IsBetterRepresentationThan (
		final AvailObject object,
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
		final AvailObject object,
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
	public boolean o_IsInstanceOfSubtypeOf (
		final AvailObject object,
		final AvailObject aType)
	{
		// Answer whether object is an instance of a subtype of aType.  Don't
		// generate an approximate type and do the comparison, because the
		// approximate type will simply send this message to the object.
		return object.exactType().isSubtypeOf(aType);
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_CanComputeHashOfType (
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
	public boolean o_EqualsBlank (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_EqualsFalse (
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
	public boolean o_EqualsTrue (
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
	public boolean o_EqualsVoid (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_EqualsVoidOrBlank (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public @NotNull AvailObject o_ExactType (
		final AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public int o_Hash (
		final AvailObject object)
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
	public boolean o_IsClosure (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsHashAvailable (
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
	public @NotNull AvailObject o_MakeImmutable (
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
	public void o_MakeSubobjectsImmutable (
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
	public @NotNull AvailObject o_Type (
		final AvailObject object)
	{
		//  Answer the object's type.

		unsupportedOperation();
		return VoidDescriptor.voidObject();
	}



	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsBoolean (
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
	public boolean o_IsString (final @NotNull AvailObject object)
	{
		return false;
	}


	/**
	 * @param object
	 * @param aClosure
	 * @return
	 */
	@Override
	public boolean o_ContainsBlock (
		final AvailObject object,
		final AvailObject aClosure)
	{
		//  Answer true if either I am aClosure or I contain aClosure.  I only follow
		//  the trail of literal compiledCode and closure objects, so this is a dead end.

		return false;
	}



	/**
	 * @param object
	 */
	@Override
	public void o_PostFault (
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
	public void o_ReadBarrierFault (
		final AvailObject object)
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
		final AvailObject object)
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
		final AvailObject object)
	{
		return false;
	}



	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsByte (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsNybble (
		final AvailObject object)
	{
		return false;
	}



	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsSet (
		final AvailObject object)
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
	public @NotNull AvailObject o_BinRemoveElementHashCanDestroy (
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
	public boolean o_IsBinSubsetOf (
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
	public int o_PopulateTupleStartingAt (
		final AvailObject object,
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
	public int o_BinSize (
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
	public @NotNull AvailObject o_BinUnionTypeOrVoid (
		final AvailObject object)
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
	public boolean o_TypeEquals (
		final AvailObject object,
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
		final AvailObject object)
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
	public boolean o_IsCyclicType (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsExtendedInteger (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsIntegerRangeType (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsMapType (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsSetType (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsTupleType (
		final AvailObject object)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	public boolean o_IsType (
		final AvailObject object)
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
		final AvailObject object,
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
		final AvailObject object,
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
		final AvailObject object,
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
	 * @param argumentsTuple
	 */
	@Override
	public void o_ArgumentsTuple (
		final AvailObject object,
		final AvailObject argumentsTuple)
	{
		unsupportedOperation();
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
	 * @param statementsTuple
	 */
	@Override
	public void o_StatementsTuple (
		final AvailObject object,
		final AvailObject statementsTuple)
	{
		unsupportedOperation();
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
	 * @param resultType
	 */
	@Override
	public void o_ResultType (final AvailObject object, final AvailObject resultType)
	{
		unsupportedOperation();
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
		final AvailObject object,
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
	 * @param primitive
	 */
	@Override
	public void o_Primitive (final AvailObject object, final int primitive)
	{
		unsupportedOperation();
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
	public void o_DeclaredType (final AvailObject object, final AvailObject declaredType)
	{
		unsupportedOperation();
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
	public void o_DeclarationKind (
		final AvailObject object,
		final DeclarationKind declarationKind)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_InitializationExpression (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_InitializationExpression (
		final AvailObject object,
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
	public void o_LiteralObject (final AvailObject object, final AvailObject literalObject)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_Token (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_Token (final AvailObject object, final AvailObject token)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_MarkerValue (final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_MarkerValue (final AvailObject object, final AvailObject markerValue)
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
	public void o_Arguments (final AvailObject object, final AvailObject arguments)
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
		final AvailObject object,
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
		final AvailObject object,
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
		final AvailObject object,
		final AvailObject expressionsTuple)
	{
		unsupportedOperation();
	}


	@Override
	public void o_TupleType (final AvailObject object, final AvailObject tupleType)
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
	public void o_Declaration (final AvailObject object, final AvailObject declaration)
	{
		unsupportedOperation();
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
		 final AvailObject object,
		 final AvailCodeGenerator codeGenerator)
	{
		unsupportedOperation();
	}


	@Override
	public void o_EmitValueOn (
		final AvailObject object,
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
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		unsupportedOperation();
	}


	/**
	 * Visit my child parse nodes with aBlock.
	 */
	@Override
	public void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		unsupportedOperation();
	}


	@Override
	public void o_ValidateLocally (
		 final AvailObject object,
		 final AvailObject parent,
		 final List<AvailObject> outerBlocks,
		 final L2Interpreter anAvailInterpreter)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_Generate (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public @NotNull AvailObject o_CopyWith (
		final AvailObject object,
		final AvailObject newParseNode)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_IsLastUse (
		final AvailObject object,
		final boolean isLastUse)
	{
		unsupportedOperation();
	}


	@Override
	public boolean o_IsLastUse (
		final AvailObject object)
	{
		unsupportedOperation();
		return false;
	}


	@Override
	public boolean o_IsMacro (
		final AvailObject object)
	{
		unsupportedOperation();
		return false;
	}


	@Override
	public void o_Macros (
		final AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_Macros (
		final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public @NotNull AvailObject o_CopyMutableParseNode (
		final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public AvailObject o_BinUnionType (final AvailObject object)
	{
		// Ordinary (non-bin, non-void) objects act as set bins of size one.
		return object.type();
	}


	@Override
	public void o_MacroName (
		final AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_MacroName (
		final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_OutputParseNode (
		final AvailObject object,
		final AvailObject value)
	{
		unsupportedOperation();
	}


	@Override
	public @NotNull AvailObject o_OutputParseNode (
		final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public @NotNull AvailObject o_ApparentSendName (
		final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_Statements (
		final AvailObject object,
		final AvailObject statementsTuple)
	{
		unsupportedOperation();
	}


	@Override
	public AvailObject o_Statements (
		final AvailObject object)
	{
		unsupportedOperation();
		return null;
	}


	@Override
	public void o_FlattenStatementsInto (
		final AvailObject object,
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
	public void o_AllBundles (final AvailObject object, final AvailObject value)
	{
		unsupportedOperation();
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
	public @NotNull AvailObject o_ObjectMetaLevels (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public void o_ObjectMetaLevels (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		unsupportedOperation();
	}

	@Override
	public @NotNull AvailObject o_CheckedExceptions (
		final @NotNull AvailObject object)
	{
		unsupportedOperation();
		return null;
	}

	@Override
	public void o_CheckedExceptions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		unsupportedOperation();
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


	/**
	 * @param object
	 * @param lowInc
	 */
	@Override
	public void o_LowerInclusive (
		final AvailObject object,
		final boolean lowInc)
	{
		unsupportedOperation();
		return;
	}


	/**
	 * @param object
	 * @param lowInc
	 * @param highInc
	 */
	@Override
	public void o_UpperInclusive (
		final AvailObject object,
		final boolean highInc)
	{
		unsupportedOperation();
		return;
	}
}
