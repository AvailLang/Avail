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

import java.io.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.ProcessDescriptor.ExecutionState;
import com.avail.exceptions.ArithmeticException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;
import com.avail.visitor.*;

/**
 * {@code AvailObject} is the fully realized, and mostly machine generated,
 * implementation of an Avail object. An {@code AvailObject} must keep track of
 * its {@linkplain AbstractDescriptor descriptor}, its integer data, and its
 * references to other {@code AvailObjects}. It specifies the complete
 * complement of messages that can be sent to an {@code AvailObject}, and
 * delegates most of those to its descriptor, passing the {@code AvailObject} as
 * an additional first argument. The redirected messages in {@code
 * AbstractDescriptor} have the prefix "o_", both to make them stand out better
 * and to indicate the additional first argument.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class AvailObject
extends AvailObjectRepresentation
implements Iterable<AvailObject>
{
	/**
	 * Release all {@linkplain AvailObject Avail objects} statically known to
	 * the VM.
	 */
	public static void clearAllWellKnownObjects ()
	{
		NullDescriptor.clearWellKnownObjects();
		BottomTypeDescriptor.clearWellKnownObjects();
		TupleDescriptor.clearWellKnownObjects();
		BlankDescriptor.clearWellKnownObjects();
		TypeDescriptor.clearWellKnownObjects();
		MapDescriptor.clearWellKnownObjects();
		ObjectTypeDescriptor.clearWellKnownObjects();
		CharacterDescriptor.clearWellKnownObjects();
		SetDescriptor.clearWellKnownObjects();
		AtomDescriptor.clearWellKnownObjects();
		UnionTypeDescriptor.clearWellKnownObjects();
		InfinityDescriptor.clearWellKnownObjects();
		IntegerDescriptor.clearWellKnownObjects();
		IntegerRangeTypeDescriptor.clearWellKnownObjects();
		FunctionTypeDescriptor.clearWellKnownObjects();
		ContinuationTypeDescriptor.clearWellKnownObjects();
		CompiledCodeTypeDescriptor.clearWellKnownObjects();
		MapTypeDescriptor.clearWellKnownObjects();
		SetTypeDescriptor.clearWellKnownObjects();
		TupleTypeDescriptor.clearWellKnownObjects();
		L2ChunkDescriptor.clearWellKnownObjects();
		ContainerTypeDescriptor.clearWellKnownObjects();
		UnionMetaDescriptor.clearWellKnownObjects();
		ParseNodeTypeDescriptor.clearWellKnownObjects();
		RawPojoDescriptor.clearWellKnownObjects();
		PojoTypeDescriptor.clearWellKnownObjects();
		PojoSelfTypeDescriptor.clearWellKnownObjects();
		PojoDescriptor.clearWellKnownObjects();
	}

	/**
	 * Create all Avail objects statically known to the VM.
	 */
	public static void createAllWellKnownObjects ()
	{
		NullDescriptor.createWellKnownObjects();
		BottomTypeDescriptor.createWellKnownObjects();
		TupleDescriptor.createWellKnownObjects();
		BlankDescriptor.createWellKnownObjects();
		TypeDescriptor.createWellKnownObjects();
		MapDescriptor.createWellKnownObjects();
		ObjectTypeDescriptor.createWellKnownObjects();
		CharacterDescriptor.createWellKnownObjects();
		SetDescriptor.createWellKnownObjects();
		AtomDescriptor.createWellKnownObjects();
		UnionTypeDescriptor.createWellKnownObjects();
		InfinityDescriptor.createWellKnownObjects();
		IntegerDescriptor.createWellKnownObjects();
		IntegerRangeTypeDescriptor.createWellKnownObjects();
		FunctionTypeDescriptor.createWellKnownObjects();
		ContinuationTypeDescriptor.createWellKnownObjects();
		CompiledCodeTypeDescriptor.createWellKnownObjects();
		MapTypeDescriptor.createWellKnownObjects();
		SetTypeDescriptor.createWellKnownObjects();
		TupleTypeDescriptor.createWellKnownObjects();
		L2ChunkDescriptor.createWellKnownObjects();
		ContainerTypeDescriptor.createWellKnownObjects();
		UnionMetaDescriptor.createWellKnownObjects();
		ParseNodeTypeDescriptor.createWellKnownObjects();
		RawPojoDescriptor.createWellKnownObjects();
		PojoTypeDescriptor.createWellKnownObjects();
		PojoSelfTypeDescriptor.createWellKnownObjects();
		PojoDescriptor.createWellKnownObjects();
	}

	public static void error (final Object... args)
	{
		throw new RuntimeException((String)args[0]);
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
		try
		{
			if (isDestroyed())
			{
				builder.append("*** A DESTROYED OBJECT ***");
				return;
			}
			if (indent > descriptor.maximumIndent())
			{
				builder.append("*** DEPTH ***");
				return;
			}
			for (final AvailObject candidate : recursionList)
			{
				if (candidate == this)
				{
					builder.append("**RECURSION**");
					return;
				}
			}
			recursionList.add(this);
			try
			{
				descriptor.printObjectOnAvoidingIndent(
					this,
					builder,
					recursionList,
					indent);
			}
			finally
			{
				recursionList.remove(recursionList.size() - 1);
			}
		}
		catch (final Exception e)
		{
			builder.append("EXCEPTION while printing.");
			final CharArrayWriter inner = new CharArrayWriter();
			final PrintWriter outer = new PrintWriter(inner);
			e.printStackTrace(outer);
			builder.append(inner);
		}
		catch (final AssertionError e)
		{
			e.printStackTrace();
			throw e;
		}
	}


	@Override
	public String toString ()
	{
		final StringBuilder stringBuilder = new StringBuilder(100);
		final List<AvailObject> recursionList = new ArrayList<AvailObject>(10);
		printOnAvoidingIndent(stringBuilder, recursionList, 1);
		// assert recursionList.size() == 0;
		return stringBuilder.toString();
	}


	/**
	 * Create a new {@link AvailObject} with the specified {@linkplain
	 * AbstractDescriptor descriptor} and the same number of variable object and
	 * integer slots.
	 *
	 * @param size The number of variable object and integer slots.
	 * @param descriptor A descriptor.
	 * @return A new object.
	 */
	static AvailObject newIndexedDescriptor (
		final int size,
		final @NotNull AbstractDescriptor descriptor)
	{
//		assert canAllocateObjects();
		int objectSlotCount = descriptor.numberOfFixedObjectSlots();
		if (descriptor.hasVariableObjectSlots())
		{
			objectSlotCount += size;
		}
		int integerSlotCount = descriptor.numberOfFixedIntegerSlots();
		if (descriptor.hasVariableIntegerSlots())
		{
			integerSlotCount += size;
		}
		return new AvailObject(
			descriptor,
			objectSlotCount,
			integerSlotCount);
	}


	/**
	 * Create a new {@link AvailObject} with the specified {@linkplain
	 * AbstractDescriptor descriptor}, the specified number of object slots, and
	 * the specified number of integer slots.
	 *
	 * @param variableObjectSlots The number of object slots.
	 * @param variableIntegerSlots The number of integer slots
	 * @param descriptor A descriptor.
	 * @return A new object.
	 */
	static AvailObject newObjectIndexedIntegerIndexedDescriptor (
		final int variableObjectSlots,
		final int variableIntegerSlots,
		final @NotNull AbstractDescriptor descriptor)
	{
//		assert canAllocateObjects();
		assert descriptor.hasVariableObjectSlots()
			|| variableObjectSlots == 0;
		assert descriptor.hasVariableIntegerSlots()
			|| variableIntegerSlots == 0;
		return new AvailObject(
			descriptor,
			descriptor.numberOfFixedObjectSlots() + variableObjectSlots,
			descriptor.numberOfFixedIntegerSlots() + variableIntegerSlots);
	}

	public boolean greaterThan (final @NotNull AvailObject another)
	{
		// Translate >= into an inverted lessThan:. This manual method
		// simplifies renaming rules greatly, and avoids the need for an
		// Object:greaterThan: in the Descriptors. Reverse the arguments and
		// dispatch to the argument's descriptor.
		return another.descriptor.o_LessThan(another, this);
	}

	public boolean greaterOrEqual (final @NotNull AvailObject another)
	{
		// Translate >= into an inverted lessOrEqual:. This manual method
		// simplifies renaming rules greatly, and avoids the need for an
		// Object:greaterOrEqual: in the Descriptors.
		// Reverse the arguments and dispatch to the argument's descriptor.
		return another.descriptor.o_LessOrEqual(another, this);
	}


	void assertObjectUnreachableIfMutable ()
	{
		// Set up the object to report nice obvious errors if anyone ever
		// accesses it again.
		assertObjectUnreachableIfMutableExcept(NullDescriptor.nullObject());
	}

	void assertObjectUnreachableIfMutableExcept (
		final @NotNull AvailObject exceptMe)
	{
		// Set up the object to report nice obvious errors if anyone ever
		// accesses it again.

		checkValidAddress();
		if (!descriptor().isMutable())
		{
			return;
		}
//		if (!canDestroyObjects())
//		{
//			error("Don't invoke this if destructions are disallowed");
//		}
		if (sameAddressAs(exceptMe))
		{
			error("What happened?  This object is also the excluded one.");
		}

		// Recursively invoke the iterator on the subobjects of self...
		final AvailSubobjectVisitor vis = new AvailMarkUnreachableSubobjectVisitor(exceptMe);
		scanSubobjects(vis);
		destroy();
		return;
	}


	/**
	 * Replace my descriptor field with a {@link FillerDescriptor}.  This blows
	 * up for most messages, catching incorrect (all, by definition) further
	 * accidental uses of this object.
	 */
	public void setToInvalidDescriptor ()
	{
		// verifyToSpaceAddress();
		descriptor = FillerDescriptor.mutable();
	}

	/**
	 * Compute the 32-bit hash of the receiver.
	 *
	 * @return An {@code int} hash value.
	 */
	public final int hash ()
	{
		return descriptor.o_Hash(this);
	}

	/**
	 * Set the 32-bit hash in the receiver.
	 *
	 * @param value The {@code int} to store as the receiver's hash value.
	 */
	public final void hash (final int value)
	{
		descriptor.o_Hash(this, value);
	}

	/**
	 * The receiver implements {@link #hashCode()} so Java can use an {@link
	 * AvailObject} in a Java {@link Set} or {@link Map}.
	 */
	@Override
	public final int hashCode ()
	{
		return descriptor.o_Hash(this);
	}

	/**
	 * A good multiplier for a multiplicative random generator.  This constant
	 * is a primitive element of (Z[2^32],*), specifically 1664525, as taken
	 * from Knuth, The Art of Computer Programming, Vol. 2, 2nd ed., page 102,
	 * row 26. See also pages 19, 20, theorems B and C. The period of the
	 * cycle based on this multiplicative generator is 2^30.
	 */
	public static final int Multiplier = 1664525;

	/**
	 * Construct a new {@link AvailObjectRepresentation}.
	 *
	 * @param descriptor This object's {@link AbstractDescriptor}.
	 * @param objectSlotsSize The number of object slots to allocate.
	 * @param intSlotsSize The number of integer slots to allocate.
	 */
	private AvailObject (
		final @NotNull AbstractDescriptor descriptor,
		final int objectSlotsSize,
		final int intSlotsSize)
	{
		super(descriptor, objectSlotsSize, intSlotsSize);
	}


	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsArgTypesFromFunctionType (
		final AvailObject functionType)
	{
		return descriptor.o_AcceptsArgTypesFromFunctionType(this, functionType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsArgumentTypesFromContinuation (
		final AvailObject continuation,
		final int stackp,
		final int numArgs)
	{
		return descriptor.o_AcceptsArgumentTypesFromContinuation(
			this,
			continuation,
			stackp,
			numArgs);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsListOfArgTypes (
		final List<AvailObject> argTypes)
	{
		return descriptor.o_AcceptsListOfArgTypes(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsListOfArgValues (
		final List<AvailObject> argValues)
	{
		return descriptor.o_AcceptsListOfArgValues(this, argValues);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsTupleOfArgTypes (
		final AvailObject argTypes)
	{
		return descriptor.o_AcceptsTupleOfArgTypes(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsTupleOfArguments (
		final AvailObject arguments)
	{
		return descriptor.o_AcceptsTupleOfArguments(this, arguments);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void addDependentChunkIndex (
		final int aChunkIndex)
	{
		descriptor.o_AddDependentChunkIndex(this, aChunkIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void addImplementation (
		final AvailObject implementation)
	{
		descriptor.o_AddImplementation(this, implementation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void addRestrictions (
		final AvailObject restrictions)
	{
		descriptor.o_AddRestrictions(this, restrictions);
	}

	/**
	 * Add the receiver and the argument {@code anInfinity} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #plusCanDestroy(AvailObject, boolean) plusCanDestroy}. It exists for
	 * double-dispatch only.</p>
	 *
	 * @param anInfinity
	 *        An {@linkplain InfinityDescriptor infinity}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         InfinityDescriptor infinities} of differing signs.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@NotNull AvailObject addToInfinityCanDestroy (
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return descriptor.o_AddToInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	/**
	 * Add the receiver and the argument {@code anInteger} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #plusCanDestroy(AvailObject, boolean) plusCanDestroy}. It exists for
	 * double-dispatch only.</p>
	 *
	 * @param anInteger
	 *        An {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@NotNull AvailObject addToIntegerCanDestroy (
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor.o_AddToIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public String asNativeString ()
	{
		return descriptor.o_AsNativeString(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject asObject ()
	{
		return descriptor.o_AsObject(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject asSet ()
	{
		return descriptor.o_AsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject asTuple ()
	{
		return descriptor.o_AsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void addGrammaticalRestrictions (
		final AvailObject methodName,
		final AvailObject illegalArgMsgs)
	{
		descriptor.o_AddGrammaticalMessageRestrictions(
			this,
			methodName,
			illegalArgMsgs);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void addMethodImplementation (
		final AvailObject methodName,
		final AvailObject implementation)
	{
		descriptor.o_AddMethodImplementation(
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
		descriptor.o_AtMessageAddBundle(
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
		descriptor.o_AtNameAdd(
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
		descriptor.o_AtNewNamePut(
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
		descriptor.o_AtPrivateNameAdd(
			this,
			stringName,
			trueName);
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
		return descriptor.o_BinAddingElementHashLevelCanDestroy(
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
		return descriptor.o_BinElementAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binElementAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor.o_BinElementAtPut(
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
		return descriptor.o_BinHasElementHash(
			this,
			elementObject,
			elementObjectHash);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int binHash ()
	{
		return descriptor.o_BinHash(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binHash (
		final int value)
	{
		descriptor.o_BinHash(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject binRemoveElementHashCanDestroy (
		final AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{
		return descriptor.o_BinRemoveElementHashCanDestroy(
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
		return descriptor.o_BinSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binSize (
		final int value)
	{
		descriptor.o_BinSize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject binUnionTypeOrTop ()
	{
		return descriptor.o_BinUnionTypeOrTop(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binUnionTypeOrTop (
		final AvailObject value)
	{
		descriptor.o_BinUnionTypeOrTop(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int bitsPerEntry ()
	{
		return descriptor.o_BitsPerEntry(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int bitVector ()
	{
		return descriptor.o_BitVector(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void bitVector (
		final int value)
	{
		descriptor.o_BitVector(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject bodyBlock ()
	{
		return descriptor.o_BodyBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject bodySignature ()
	{
		return descriptor.o_BodySignature(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject breakpointBlock ()
	{
		return descriptor.o_BreakpointBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void breakpointBlock (
		final AvailObject value)
	{
		descriptor.o_BreakpointBlock(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void buildFilteredBundleTreeFrom (
		final AvailObject bundleTree)
	{
		descriptor.o_BuildFilteredBundleTreeFrom(this, bundleTree);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject caller ()
	{
		return descriptor.o_Caller(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void caller (
		final AvailObject value)
	{
		descriptor.o_Caller(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int capacity ()
	{
		return descriptor.o_Capacity(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void cleanUpAfterCompile ()
	{
		descriptor.o_CleanUpAfterCompile(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void clearValue ()
	{
		descriptor.o_ClearValue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject function ()
	{
		return descriptor.o_Function(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void function (
		final AvailObject value)
	{
		descriptor.o_Function(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject functionType ()
	{
		return descriptor.o_FunctionType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject code ()
	{
		return descriptor.o_Code(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void code (
		final AvailObject value)
	{
		descriptor.o_Code(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int codePoint ()
	{
		return descriptor.o_CodePoint(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void codePoint (
		final int value)
	{
		descriptor.o_CodePoint(this, value);
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
		return descriptor.o_CompareFromToWithStartingAt(
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
		return descriptor.o_CompareFromToWithAnyTupleStartingAt(
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
		return descriptor.o_CompareFromToWithByteStringStartingAt(
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
		return descriptor.o_CompareFromToWithByteTupleStartingAt(
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
		return descriptor.o_CompareFromToWithNybbleTupleStartingAt(
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
		return descriptor.o_CompareFromToWithObjectTupleStartingAt(
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
		return descriptor.o_CompareFromToWithTwoByteStringStartingAt(
			this,
			startIndex1,
			endIndex1,
			aTwoByteString,
			startIndex2);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lazyComplete ()
	{
		return descriptor.o_LazyComplete(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void lazyComplete (
		final AvailObject value)
	{
		descriptor.o_LazyComplete(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int computeHashFromTo (
		final int start,
		final int end)
	{
		return descriptor.o_ComputeHashFromTo(
			this,
			start,
			end);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject concatenateTuplesCanDestroy (
		final boolean canDestroy)
	{
		return descriptor.o_ConcatenateTuplesCanDestroy(this, canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject constantBindings ()
	{
		return descriptor.o_ConstantBindings(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean containsBlock (
		final AvailObject aFunction)
	{
		return descriptor.o_ContainsBlock(this, aFunction);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject contentType ()
	{
		return descriptor.o_ContentType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject continuation ()
	{
		return descriptor.o_Continuation(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void continuation (
		final AvailObject value)
	{
		descriptor.o_Continuation(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyAsMutableContinuation ()
	{
		return descriptor.o_CopyAsMutableContinuation(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyAsMutableObjectTuple ()
	{
		return descriptor.o_CopyAsMutableObjectTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyAsMutableSpliceTuple ()
	{
		return descriptor.o_CopyAsMutableSpliceTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void copyToRestrictedTo (
		final AvailObject filteredBundleTree,
		final AvailObject visibleNames)
	{
		descriptor.o_CopyToRestrictedTo(
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
		return descriptor.o_CopyTupleFromToCanDestroy(
			this,
			start,
			end,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean couldEverBeInvokedWith (
		final List<AvailObject> argTypes)
	{
		return descriptor.o_CouldEverBeInvokedWith(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject dataAtIndex (
		final int index)
	{
		return descriptor.o_DataAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void dataAtIndexPut (
		final int index,
		final AvailObject value)
	{
		descriptor.o_DataAtIndexPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject defaultType ()
	{
		return descriptor.o_DefaultType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int parsingPc ()
	{
		return descriptor.o_ParsingPc(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void displayTestingTree ()
	{
		descriptor.o_DisplayTestingTree(this);
	}

	/**
	 * Divide the receiver by the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #divideIntoIntegerCanDestroy(AvailObject, boolean)
	 * divideIntoIntegerCanDestroy} or {@link
	 * #divideIntoInfinityCanDestroy(AvailObject, boolean)
	 * divideIntoInfinityCanDestroy}, where actual implementations of the
	 * division operation should reside.</p>
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject divisor} was {@linkplain
	 *         IntegerDescriptor#zero() zero}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	public @NotNull AvailObject divideCanDestroy (
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return descriptor.o_DivideCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Divide the receiver by the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}. The operation is not allowed to fail,
	 * so the caller must ensure that the arguments are valid, i.e. the divisor
	 * is not {@linkplain IntegerDescriptor#zero() zero}.
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	public @NotNull AvailObject noFailDivideCanDestroy (
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		try
		{
			return descriptor.o_DivideCanDestroy(
				this,
				aNumber,
				canDestroy);
		}
		catch (final ArithmeticException e)
		{
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			assert false;
		}

		error("noFailDivideCanDestroy failed!");
		return NullDescriptor.nullObject();
	}

	/**
	 * Divide the argument {@code anInfinity} by the receiver and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #divideCanDestroy(AvailObject, boolean) divideCanDestroy}. It exists for
	 * double-dispatch only.</p>
	 *
	 * @param anInfinity
	 *        An {@linkplain InfinityDescriptor infinity}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject dividend} was {@linkplain
	 *         InfinityDescriptor infinity} or the divisor was {@linkplain
	 *         IntegerDescriptor#zero() zero}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@NotNull AvailObject divideIntoInfinityCanDestroy (
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return descriptor.o_DivideIntoInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	/**
	 * Divide the argument {@code anInteger} by the receiver and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #divideCanDestroy(AvailObject, boolean) divideCanDestroy}. It exists for
	 * double-dispatch only.</p>
	 *
	 * @param anInteger
	 *        An {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject divisor} was {@linkplain
	 *         IntegerDescriptor#zero() zero}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@NotNull AvailObject divideIntoIntegerCanDestroy (
			final @NotNull AvailObject anInteger,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return descriptor.o_DivideIntoIntegerCanDestroy(
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
		return descriptor.o_ElementAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void elementAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor.o_ElementAtPut(
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
		return descriptor.o_EndOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int endSubtupleIndexInZone (
		final int zone)
	{
		return descriptor.o_EndSubtupleIndexInZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject ensureMutable ()
	{
		return descriptor.o_EnsureMutable(this);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * This comparison operation takes an {@link Object} as its argument to
	 * avoid accidentally calling this with, say, a {@link String} literal.
	 * We mark it as deprecated to ensure we don't accidentally invoke
	 * this method when we really mean the version that takes an {@code
	 * AvailObject} as an argument.  Eclipse conveniently shows such invocations
	 * with a <strike>strike-out</strike>.  That's a convenient warning for the
	 * programmer, but we also fail if this method actually gets invoked AND
	 * the argument is not an {@code AvailObject}.  That means we don't allow
	 * AvailObjects to be added to Java {@link Set sets} and such intermixed
	 * with things that are not AvailObjects.
	 * </p>
	 */
	@Override
	@Deprecated
	public boolean equals (
		final Object another)
	{
		assert another instanceof AvailObject;
		return descriptor.o_Equals(this, (AvailObject)another);
	}

	/**
	 * Dispatch to the descriptor.  Note that its argument is of type {@link
	 * AvailObject} so that correctly typed uses (where the argument is
	 * statically known to be an AvailObject) proceed normally.  Incorrectly
	 * typed uses (where the argument is an arbitrary Java {@link Object} should
	 * show up as calling a deprecated method, and should fail at runtime if the
	 * argument is not actually an AvailObject.
	 */
	public boolean equals (
		final AvailObject another)
	{
		return descriptor.o_Equals(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsAnyTuple (
		final AvailObject anotherTuple)
	{
		return descriptor.o_EqualsAnyTuple(this, anotherTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsBlank ()
	{
		return descriptor.o_EqualsBlank(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsByteString (
		final AvailObject aByteString)
	{
		return descriptor.o_EqualsByteString(this, aByteString);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsByteTuple (
		final AvailObject aByteTuple)
	{
		return descriptor.o_EqualsByteTuple(this, aByteTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsCharacterWithCodePoint (
		final int otherCodePoint)
	{
		return descriptor.o_EqualsCharacterWithCodePoint(this, otherCodePoint);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsFunction (
		final AvailObject aFunction)
	{
		return descriptor.o_EqualsFunction(this, aFunction);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsFunctionType (
		final AvailObject aFunctionType)
	{
		return descriptor.o_EqualsFunctionType(this, aFunctionType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsCompiledCode (
		final AvailObject aCompiledCode)
	{
		return descriptor.o_EqualsCompiledCode(this, aCompiledCode);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContainer (
		final AvailObject aContainer)
	{
		return descriptor.o_EqualsContainer(this, aContainer);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContainerType (
		final AvailObject aContainerType)
	{
		return descriptor.o_EqualsContainerType(this, aContainerType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContinuation (
		final AvailObject aContinuation)
	{
		return descriptor.o_EqualsContinuation(this, aContinuation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContinuationType (
		final AvailObject aType)
	{
		return descriptor.o_EqualsContinuationType(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsDouble (
		final AvailObject aDoubleObject)
	{
		return descriptor.o_EqualsDouble(this, aDoubleObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsFloat (
		final AvailObject aFloatObject)
	{
		return descriptor.o_EqualsFloat(this, aFloatObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsInfinity (
		final AvailObject anInfinity)
	{
		return descriptor.o_EqualsInfinity(this, anInfinity);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsInteger (
		final AvailObject anAvailInteger)
	{
		return descriptor.o_EqualsInteger(this, anAvailInteger);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor.o_EqualsIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsMap (
		final AvailObject aMap)
	{
		return descriptor.o_EqualsMap(this, aMap);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsMapType (
		final AvailObject aMapType)
	{
		return descriptor.o_EqualsMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsNybbleTuple (
		final AvailObject aNybbleTuple)
	{
		return descriptor.o_EqualsNybbleTuple(this, aNybbleTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsObject (
		final AvailObject anObject)
	{
		return descriptor.o_EqualsObject(this, anObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsObjectTuple (
		final AvailObject anObjectTuple)
	{
		return descriptor.o_EqualsObjectTuple(this, anObjectTuple);
	}

	/**
	 * @param aParseNodeType
	 * @return
	 */
	public boolean equalsParseNodeType (
		final @NotNull AvailObject aParseNodeType)
	{
		return descriptor.o_EqualsParseNodeType(this, aParseNodeType);
	}

	/**
	 * @param aPojo
	 * @return
	 */
	public boolean equalsPojo (final @NotNull AvailObject aPojo)
	{
		return descriptor.o_EqualsPojo(this, aPojo);
	}

	/**
	 * @param aPojoType
	 * @return
	 */
	public boolean equalsPojoType (final @NotNull AvailObject aPojoType)
	{
		return descriptor.o_EqualsPojoType(this, aPojoType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsPrimitiveType (
		final AvailObject aPrimitiveType)
	{
		return descriptor.o_EqualsPrimitiveType(this, aPrimitiveType);
	}

	/**
	 * @param aRawPojo
	 * @return
	 */
	public boolean equalsRawPojo (final @NotNull AvailObject aRawPojo)
	{
		return descriptor.o_EqualsRawPojo(this, aRawPojo);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsSet (
		final AvailObject aSet)
	{
		return descriptor.o_EqualsSet(this, aSet);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsSetType (
		final AvailObject aSetType)
	{
		return descriptor.o_EqualsSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsTupleType (
		final AvailObject aTupleType)
	{
		return descriptor.o_EqualsTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsTwoByteString (
		final AvailObject aTwoByteString)
	{
		return descriptor.o_EqualsTwoByteString(this, aTwoByteString);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsNull ()
	{
		return descriptor.o_EqualsNull(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsNullOrBlank ()
	{
		return descriptor.o_EqualsNullOrBlank(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public ExecutionState executionState ()
	{
		return descriptor.o_ExecutionState(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void executionState (
		final ExecutionState value)
	{
		descriptor.o_ExecutionState(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void expand ()
	{
		descriptor.o_Expand(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean extractBoolean ()
	{
		return descriptor.o_ExtractBoolean(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short extractByte ()
	{
		return descriptor.o_ExtractByte(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public double extractDouble ()
	{
		return descriptor.o_ExtractDouble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public float extractFloat ()
	{
		return descriptor.o_ExtractFloat(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int extractInt ()
	{
		return descriptor.o_ExtractInt(this);
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
		return descriptor.o_ExtractLong(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public byte extractNybble ()
	{
		return descriptor.o_ExtractNybble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public byte extractNybbleFromTupleAt (
		final int index)
	{
		return descriptor.o_ExtractNybbleFromTupleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject fieldMap ()
	{
		return descriptor.o_FieldMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject fieldTypeMap ()
	{
		return descriptor.o_FieldTypeMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public List<AvailObject> filterByTypes (
		final List<AvailObject> argTypes)
	{
		return descriptor.o_FilterByTypes(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject filteredBundleTree ()
	{
		return descriptor.o_FilteredBundleTree(this);
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
		return descriptor.o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone(
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
		return descriptor.o_GetInteger(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject getValue ()
	{
		return descriptor.o_GetValue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean greaterThanInteger (
		final AvailObject another)
	{
		return descriptor.o_GreaterThanInteger(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean greaterThanSignedInfinity (
		final AvailObject another)
	{
		return descriptor.o_GreaterThanSignedInfinity(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasElement (
		final AvailObject elementObject)
	{
		return descriptor.o_HasElement(this, elementObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hashFromTo (
		final int startIndex,
		final int endIndex)
	{
		return descriptor.o_HashFromTo(
			this,
			startIndex,
			endIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hashOfType ()
	{
		return descriptor.o_HashOfType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hashOrZero ()
	{
		return descriptor.o_HashOrZero(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hashOrZero (
		final int value)
	{
		descriptor.o_HashOrZero(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasKey (
		final AvailObject keyObject)
	{
		return descriptor.o_HasKey(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasObjectInstance (
		final AvailObject potentialInstance)
	{
		return descriptor.o_HasObjectInstance(this, potentialInstance);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasRestrictions ()
	{
		return descriptor.o_HasRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public List<AvailObject> implementationsAtOrBelow (
		final List<AvailObject> argTypes)
	{
		return descriptor.o_ImplementationsAtOrBelow(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject implementationsTuple ()
	{
		return descriptor.o_ImplementationsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject includeBundle (
		final AvailObject messageBundle)
	{
		return descriptor.o_IncludeBundle(
			this,
			messageBundle);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean includesImplementation (
		final AvailObject imp)
	{
		return descriptor.o_IncludesImplementation(this, imp);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lazyIncomplete ()
	{
		return descriptor.o_LazyIncomplete(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void lazyIncomplete (
		final AvailObject value)
	{
		descriptor.o_LazyIncomplete(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int index ()
	{
		return descriptor.o_Index(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void index (
		final int value)
	{
		descriptor.o_Index(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int internalHash ()
	{
		return descriptor.o_InternalHash(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void internalHash (
		final int value)
	{
		descriptor.o_InternalHash(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int interruptRequestFlag ()
	{
		return descriptor.o_InterruptRequestFlag(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void interruptRequestFlag (
		final int value)
	{
		descriptor.o_InterruptRequestFlag(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int invocationCount ()
	{
		return descriptor.o_InvocationCount(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void invocationCount (
		final int value)
	{
		descriptor.o_InvocationCount(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isAbstract ()
	{
		return descriptor.o_IsAbstract(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBetterRepresentationThan (
		final AvailObject anotherObject)
	{
		return descriptor.o_IsBetterRepresentationThan(this, anotherObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBetterRepresentationThanTupleType (
		final AvailObject aTupleType)
	{
		return descriptor.o_IsBetterRepresentationThanTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBinSubsetOf (
		final AvailObject potentialSuperset)
	{
		return descriptor.o_IsBinSubsetOf(this, potentialSuperset);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBoolean ()
	{
		return descriptor.o_IsBoolean(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isByte ()
	{
		return descriptor.o_IsByte(this);
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
		return descriptor.o_IsByteTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isCharacter ()
	{
		return descriptor.o_IsCharacter(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isFunction ()
	{
		return descriptor.o_IsFunction(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isAtom ()
	{
		return descriptor.o_IsAtom(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isExtendedInteger ()
	{
		return descriptor.o_IsExtendedInteger(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isFinite ()
	{
		return descriptor.o_IsFinite(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isForward ()
	{
		return descriptor.o_IsForward(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isMethod ()
	{
		return descriptor.o_IsMethod(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isInstanceOf (
		final AvailObject aType)
	{
		return descriptor.o_IsInstanceOf(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isInstanceOfKind (
		final AvailObject aType)
	{
		return descriptor.o_IsInstanceOfKind(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isIntegerRangeType ()
	{
		return descriptor.o_IsIntegerRangeType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isMap ()
	{
		return descriptor.o_IsMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isMapType ()
	{
		return descriptor.o_IsMapType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isNybble ()
	{
		return descriptor.o_IsNybble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isPositive ()
	{
		return descriptor.o_IsPositive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSaved ()
	{
		return descriptor.o_IsSaved(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void isSaved (
		final boolean aBoolean)
	{
		descriptor.o_IsSaved(this, aBoolean);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSet ()
	{
		return descriptor.o_IsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSetType ()
	{
		return descriptor.o_IsSetType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSplice ()
	{
		return descriptor.o_IsSplice(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSubsetOf (
		final AvailObject another)
	{
		return descriptor.o_IsSubsetOf(this, another);
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
		return descriptor.o_IsString(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSubtypeOf (
		final AvailObject aType)
	{
		return descriptor.o_IsSubtypeOf(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfContainerType (
		final AvailObject aContainerType)
	{
		return descriptor.o_IsSupertypeOfContainerType(this, aContainerType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfContinuationType (
		final AvailObject aContinuationType)
	{
		return descriptor.o_IsSupertypeOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfFunctionType (
		final AvailObject aFunctionType)
	{
		return descriptor.o_IsSupertypeOfFunctionType(this, aFunctionType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor.o_IsSupertypeOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfMapType (
		final AvailObject aMapType)
	{
		return descriptor.o_IsSupertypeOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor.o_IsSupertypeOfObjectType(this, anObjectType);
	}

	/**
	 * @param aParseNodeType
	 * @return
	 */
	public boolean isSupertypeOfParseNodeType (
		final @NotNull AvailObject aParseNodeType)
	{
		return descriptor.o_IsSupertypeOfParseNodeType(this, aParseNodeType);
	}

	/**
	 * Dispatch to the descriptor
	 */
	public boolean isSupertypeOfPojoType (
		final @NotNull AvailObject aPojoType)
	{
		return descriptor.o_IsSupertypeOfPojoType(this, aPojoType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfPrimitiveType (
		final AvailObject aPrimitiveType)
	{
		return descriptor.o_IsSupertypeOfPrimitiveType(this, aPrimitiveType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfSetType (
		final AvailObject aSetType)
	{
		return descriptor.o_IsSupertypeOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfBottom ()
	{
		return descriptor.o_IsSupertypeOfBottom(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor.o_IsSupertypeOfTupleType(this, aTupleType);
	}

	/**
	 * @param aUnionMeta
	 * @return
	 */
	public boolean isSupertypeOfUnionMeta (
		final @NotNull AvailObject aUnionMeta)
	{
		return descriptor.o_IsSupertypeOfUnionMeta(this, aUnionMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isTuple ()
	{
		return descriptor.o_IsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isTupleType ()
	{
		return descriptor.o_IsTupleType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isType ()
	{
		return descriptor.o_IsType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isValid ()
	{
		return descriptor.o_IsValid(this);
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
		return descriptor.o_Iterator(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject keyAtIndex (
		final int index)
	{
		return descriptor.o_KeyAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void keyAtIndexPut (
		final int index,
		final AvailObject keyObject)
	{
		descriptor.o_KeyAtIndexPut(
			this,
			index,
			keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public List<AvailObject> keysAsArray ()
	{
		return descriptor.o_KeysAsArray(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject keysAsSet ()
	{
		return descriptor.o_KeysAsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject keyType ()
	{
		return descriptor.o_KeyType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean lessOrEqual (
		final AvailObject another)
	{
		return descriptor.o_LessOrEqual(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean lessThan (
		final AvailObject another)
	{
		return descriptor.o_LessThan(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject levelTwoChunk ()
	{
		return descriptor.o_LevelTwoChunk(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void levelTwoChunkOffset (
		final AvailObject chunk,
		final int offset)
	{
		descriptor.o_LevelTwoChunkOffset(
			this,
			chunk,
			offset);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int levelTwoOffset ()
	{
		return descriptor.o_LevelTwoOffset(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject literal ()
	{
		return descriptor.o_Literal(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void literal (
		final AvailObject value)
	{
		descriptor.o_Literal(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject literalAt (
		final int index)
	{
		return descriptor.o_LiteralAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject argOrLocalOrStackAt (
		final int index)
	{
		return descriptor.o_ArgOrLocalOrStackAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void argOrLocalOrStackAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor.o_ArgOrLocalOrStackAtPut(
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
		return descriptor.o_LocalTypeAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByTypesFromList (
		final List<AvailObject> argumentTypeList)
	{
		return descriptor.o_LookupByTypesFromList(this, argumentTypeList);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByTypesFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		return descriptor.o_LookupByTypesFromContinuationStackp(
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
		return descriptor.o_LookupByTypesFromTuple(this, argumentTypeTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByValuesFromList (
		final List<AvailObject> argumentList)
	{
		return descriptor.o_LookupByValuesFromList(this, argumentList);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByValuesFromTuple (
		final AvailObject argumentTuple)
	{
		return descriptor.o_LookupByValuesFromTuple(this, argumentTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lowerBound ()
	{
		return descriptor.o_LowerBound(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean lowerInclusive ()
	{
		return descriptor.o_LowerInclusive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject makeImmutable ()
	{
		return descriptor.o_MakeImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void makeSubobjectsImmutable ()
	{
		descriptor.o_MakeSubobjectsImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject mapAt (
		final AvailObject keyObject)
	{
		return descriptor.o_MapAt(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject mapAtPuttingCanDestroy (
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		return descriptor.o_MapAtPuttingCanDestroy(
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
		return descriptor.o_MapSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void mapSize (
		final int value)
	{
		descriptor.o_MapSize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject mapWithoutKeyCanDestroy (
		final AvailObject keyObject,
		final boolean canDestroy)
	{
		return descriptor.o_MapWithoutKeyCanDestroy(
			this,
			keyObject,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int maxStackDepth ()
	{
		return descriptor.o_MaxStackDepth(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject message ()
	{
		return descriptor.o_Message(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void message (
		final AvailObject value)
	{
		descriptor.o_Message(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject messageParts ()
	{
		return descriptor.o_MessageParts(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void messageParts (
		final AvailObject value)
	{
		descriptor.o_MessageParts(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject methods ()
	{
		return descriptor.o_Methods(this);
	}

	/**
	 * Subtract the argument {@code aNumber} from a receiver and answer
	 * the {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #subtractFromIntegerCanDestroy(AvailObject, boolean)
	 * subtractFromIntegerCanDestroy} or {@link
	 * #subtractFromInfinityCanDestroy(AvailObject, boolean)
	 * subtractFromInfinityCanDestroy}, where actual implementations of the
	 * subtraction operation should reside.</p>
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         InfinityDescriptor infinities} of like signs.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	public @NotNull AvailObject minusCanDestroy (
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return descriptor.o_MinusCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Difference the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}. The operation is not allowed to fail,
	 * so the caller must ensure that the arguments are valid, i.e. not
	 * {@linkplain InfinityDescriptor infinities} of like sign.
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         InfinityDescriptor infinities} of like signs.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	public @NotNull AvailObject noFailMinusCanDestroy (
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		try
		{
			return descriptor.o_MinusCanDestroy(
				this,
				aNumber,
				canDestroy);
		}
		catch (final ArithmeticException e)
		{
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			assert false;
		}

		error("noFailMinusCanDestroy failed!");
		return NullDescriptor.nullObject();
	}

	/**
	 * Multiply the receiver and the argument {@code anInfinity} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #timesCanDestroy(AvailObject, boolean) timesCanDestroy}. It exists for
	 * double-dispatch only.</p>
	 *
	 * @param anInfinity
	 *        An {@linkplain InfinityDescriptor infinity}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of multiplying the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         IntegerDescriptor#zero() zero} and {@linkplain InfinityDescriptor
	 *         infinity}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@NotNull AvailObject multiplyByInfinityCanDestroy (
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return descriptor.o_MultiplyByInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	/**
	 * Multiply the receiver and the argument {@code anInteger} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #timesCanDestroy(AvailObject, boolean) timesCanDestroy}. It exists for
	 * double-dispatch only.</p>
	 *
	 * @param anInteger
	 *        An {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of multiplying the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         IntegerDescriptor#zero() zero} and {@linkplain InfinityDescriptor
	 *         infinity}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@NotNull AvailObject multiplyByIntegerCanDestroy (
			final @NotNull AvailObject anInteger,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return descriptor.o_MultiplyByIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject myRestrictions ()
	{
		return descriptor.o_MyRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void myRestrictions (
		final AvailObject value)
	{
		descriptor.o_MyRestrictions(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void myType (
		final AvailObject value)
	{
		descriptor.o_MyType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject name ()
	{
		return descriptor.o_Name(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void name (
		final AvailObject value)
	{
		descriptor.o_Name(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject names ()
	{
		return descriptor.o_Names(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean nameVisible (
		final AvailObject trueName)
	{
		return descriptor.o_NameVisible(this, trueName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject newNames ()
	{
		return descriptor.o_NewNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numArgs ()
	{
		return descriptor.o_NumArgs(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numArgsAndLocalsAndStack ()
	{
		return descriptor.o_NumArgsAndLocalsAndStack(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numberOfZones ()
	{
		return descriptor.o_NumberOfZones(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numBlanks ()
	{
		return descriptor.o_NumBlanks(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void numBlanks (
		final int value)
	{
		descriptor.o_NumBlanks(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numDoubles ()
	{
		return descriptor.o_NumDoubles(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numIntegers ()
	{
		return descriptor.o_NumIntegers(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numLiterals ()
	{
		return descriptor.o_NumLiterals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numLocals ()
	{
		return descriptor.o_NumLocals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numObjects ()
	{
		return descriptor.o_NumObjects(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numOuters ()
	{
		return descriptor.o_NumOuters(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numOuterVars ()
	{
		return descriptor.o_NumOuterVars(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject nybbles ()
	{
		return descriptor.o_Nybbles(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean optionallyNilOuterVar (
		final int index)
	{
		return descriptor.o_OptionallyNilOuterVar(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject outerTypeAt (
		final int index)
	{
		return descriptor.o_OuterTypeAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject outerVarAt (
		final int index)
	{
		return descriptor.o_OuterVarAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void outerVarAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor.o_OuterVarAtPut(
			this,
			index,
			value);
	}
	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject parent ()
	{
		return descriptor.o_Parent(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void parent (
		final AvailObject value)
	{
		descriptor.o_Parent(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int pc ()
	{
		return descriptor.o_Pc(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void pc (
		final int value)
	{
		descriptor.o_Pc(this, value);
	}

	/**
	 * Add the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #addToIntegerCanDestroy(AvailObject, boolean) addToIntegerCanDestroy} or
	 * {@link #addToInfinityCanDestroy(AvailObject, boolean)
	 * addToInfinityCanDestroy}, where actual implementations of the addition
	 * operation should reside.</p>
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         InfinityDescriptor infinities} of differing signs.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	public @NotNull AvailObject plusCanDestroy (
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return descriptor.o_PlusCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Add the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}. The operation is not allowed to fail,
	 * so the caller must ensure that the arguments are valid, i.e. not
	 * {@linkplain InfinityDescriptor infinities} of unlike sign.
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         InfinityDescriptor infinities} of differing signs.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	public @NotNull AvailObject noFailPlusCanDestroy (
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		try
		{
			return descriptor.o_PlusCanDestroy(
				this,
				aNumber,
				canDestroy);
		}
		catch (final ArithmeticException e)
		{
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			assert false;
		}

		error("noFailPlusCanDestroy failed!");
		return NullDescriptor.nullObject();
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int populateTupleStartingAt (
		final AvailObject mutableTuple,
		final int startingIndex)
	{
		return descriptor.o_PopulateTupleStartingAt(
			this,
			mutableTuple,
			startingIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void postFault ()
	{
		descriptor.o_PostFault(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int primitiveNumber ()
	{
		return descriptor.o_PrimitiveNumber(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject priority ()
	{
		return descriptor.o_Priority(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void priority (
		final @NotNull AvailObject value)
	{
		descriptor.o_Priority(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateAddElement (
		final AvailObject element)
	{
		return descriptor.o_PrivateAddElement(this, element);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateExcludeElement (
		final AvailObject element)
	{
		return descriptor.o_PrivateExcludeElement(this, element);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateExcludeElementKnownIndex (
		final AvailObject element,
		final int knownIndex)
	{
		return descriptor.o_PrivateExcludeElementKnownIndex(
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
		return descriptor.o_PrivateExcludeKey(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateMapAtPut (
		final AvailObject keyObject,
		final AvailObject valueObject)
	{
		return descriptor.o_PrivateMapAtPut(
			this,
			keyObject,
			valueObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateNames ()
	{
		return descriptor.o_PrivateNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject processGlobals ()
	{
		return descriptor.o_ProcessGlobals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void processGlobals (
		final AvailObject value)
	{
		descriptor.o_ProcessGlobals(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short rawByteAt (
		final int index)
	{
		return descriptor.o_RawByteAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawByteAtPut (
		final int index,
		final short anInteger)
	{
		descriptor.o_RawByteAtPut(
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
		return descriptor.o_RawByteForCharacterAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawByteForCharacterAtPut (
		final int index,
		final short anInteger)
	{
		descriptor.o_RawByteForCharacterAtPut(
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
		return descriptor.o_RawNybbleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawNybbleAtPut (
		final int index,
		final byte aNybble)
	{
		descriptor.o_RawNybbleAtPut(
			this,
			index,
			aNybble);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short rawShortForCharacterAt (
		final int index)
	{
		return descriptor.o_RawShortForCharacterAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawShortForCharacterAtPut (
		final int index,
		final short anInteger)
	{
		descriptor.o_RawShortForCharacterAtPut(
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
		return descriptor.o_RawSignedIntegerAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawSignedIntegerAtPut (
		final int index,
		final int value)
	{
		descriptor.o_RawSignedIntegerAtPut(
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
		return descriptor.o_RawUnsignedIntegerAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawUnsignedIntegerAtPut (
		final int index,
		final int value)
	{
		descriptor.o_RawUnsignedIntegerAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void readBarrierFault ()
	{
		descriptor.o_ReadBarrierFault(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void releaseVariableOrMakeContentsImmutable ()
	{
		descriptor.o_ReleaseVariableOrMakeContentsImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeDependentChunkIndex (
		final int aChunkIndex)
	{
		descriptor.o_RemoveDependentChunkIndex(this, aChunkIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeFrom (
		final L2Interpreter anInterpreter)
	{
		descriptor.o_RemoveFrom(this, anInterpreter);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeImplementation (
		final AvailObject implementation)
	{
		descriptor.o_RemoveImplementation(this, implementation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean removeBundle (
		final AvailObject bundle)
	{
		return descriptor.o_RemoveBundle(
			this,
			bundle);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeRestrictions ()
	{
		descriptor.o_RemoveRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeRestrictions (
		final AvailObject obsoleteRestrictions)
	{
		descriptor.o_RemoveRestrictions(this, obsoleteRestrictions);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void resolvedForwardWithName (
		final AvailObject forwardImplementation,
		final AvailObject methodName)
	{
		descriptor.o_ResolvedForwardWithName(
			this,
			forwardImplementation,
			methodName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject grammaticalRestrictions ()
	{
		return descriptor.o_GrammaticalRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject returnType ()
	{
		return descriptor.o_ReturnType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void returnType (
		final AvailObject value)
	{
		descriptor.o_ReturnType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject rootBin ()
	{
		return descriptor.o_RootBin(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rootBin (
		final AvailObject value)
	{
		descriptor.o_RootBin(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void scanSubobjects (
		final AvailSubobjectVisitor visitor)
	{
		descriptor.o_ScanSubobjects(this, visitor);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setIntersectionCanDestroy (
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		return descriptor.o_SetIntersectionCanDestroy(
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
		return descriptor.o_SetMinusCanDestroy(
			this,
			otherSet,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int setSize ()
	{
		return descriptor.o_SetSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void setSubtupleForZoneTo (
		final int zoneIndex,
		final AvailObject newTuple)
	{
		descriptor.o_SetSubtupleForZoneTo(
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
		return descriptor.o_SetUnionCanDestroy(
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
		descriptor.o_SetValue(this, newValue);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setWithElementCanDestroy (
		final AvailObject newElementObject,
		final boolean canDestroy)
	{
		return descriptor.o_SetWithElementCanDestroy(
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
		return descriptor.o_SetWithoutElementCanDestroy(
			this,
			elementObjectToExclude,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject signature ()
	{
		return descriptor.o_Signature(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void size (
		final int value)
	{
		descriptor.o_Size(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int sizeOfZone (
		final int zone)
	{
		return descriptor.o_SizeOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject sizeRange ()
	{
		return descriptor.o_SizeRange(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lazySpecialActions ()
	{
		return descriptor.o_LazySpecialActions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void lazySpecialActions (
		final AvailObject value)
	{
		descriptor.o_LazySpecialActions(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject stackAt (
		final int slotIndex)
	{
		return descriptor.o_StackAt(this, slotIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void stackAtPut (
		final int slotIndex,
		final AvailObject anObject)
	{
		descriptor.o_StackAtPut(
			this,
			slotIndex,
			anObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int stackp ()
	{
		return descriptor.o_Stackp(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void stackp (
		final int value)
	{
		descriptor.o_Stackp(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int start ()
	{
		return descriptor.o_Start(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void start (
		final int value)
	{
		descriptor.o_Start(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject startingChunk ()
	{
		return descriptor.o_StartingChunk(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void startingChunk (
		final AvailObject value)
	{
		descriptor.o_StartingChunk(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int startOfZone (
		final int zone)
	{
		return descriptor.o_StartOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int startSubtupleIndexInZone (
		final int zone)
	{
		return descriptor.o_StartSubtupleIndexInZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void step ()
	{
		descriptor.o_Step(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject string ()
	{
		return descriptor.o_String(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void string (
		final AvailObject value)
	{
		descriptor.o_String(this, value);
	}

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * #minusCanDestroy(AvailObject, boolean) minusCanDestroy}. It
	 * exists for double-dispatch only.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param anInfinity
	 *        An {@linkplain InfinityDescriptor infinity}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         InfinityDescriptor infinities} of like signs.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@NotNull AvailObject subtractFromInfinityCanDestroy (
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return descriptor.o_SubtractFromInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * #minusCanDestroy(AvailObject, boolean) minusCanDestroy}. It
	 * exists for double-dispatch only.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param anInteger
	 *        An {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@NotNull AvailObject subtractFromIntegerCanDestroy (
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor.o_SubtractFromIntegerCanDestroy(
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
		return descriptor.o_SubtupleForZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject testingTree ()
	{
		return descriptor.o_TestingTree(this);
	}

	/**
	 * Multiply the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #multiplyByIntegerCanDestroy(AvailObject, boolean)
	 * multiplyByIntegerCanDestroy} or {@link
	 * #multiplyByInfinityCanDestroy(AvailObject, boolean)
	 * multiplyByInfinityCanDestroy}, where actual implementations of the
	 * multiplication operation should reside.</p>
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of multiplying the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         IntegerDescriptor#zero() zero} and {@linkplain InfinityDescriptor
	 *         infinity}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	public @NotNull AvailObject timesCanDestroy (
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException
	{
		return descriptor.o_TimesCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Multiply the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}. The operation is not allowed to fail,
	 * so the caller must ensure that the arguments are valid, i.e. not
	 * {@linkplain IntegerDescriptor#zero() zero} and {@linkplain
	 * InfinityDescriptor infinity}.
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	public @NotNull AvailObject noFailTimesCanDestroy (
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		try
		{
			return descriptor.o_TimesCanDestroy(
				this,
				aNumber,
				canDestroy);
		}
		catch (final ArithmeticException e)
		{
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			assert false;
		}

		error("noFailTimesCanDestroy failed!");
		return NullDescriptor.nullObject();
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public TokenDescriptor.TokenType tokenType ()
	{
		return descriptor.o_TokenType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void tokenType (
		final TokenDescriptor.TokenType value)
	{
		descriptor.o_TokenType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int translateToZone (
		final int tupleIndex,
		final int zoneIndex)
	{
		return descriptor.o_TranslateToZone(
			this,
			tupleIndex,
			zoneIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject traversed ()
	{
		return descriptor.o_Traversed(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void trimExcessInts ()
	{
		descriptor.o_TrimExcessInts(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject trueNamesForStringName (
		final AvailObject stringName)
	{
		return descriptor.o_TrueNamesForStringName(this, stringName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject truncateTo (
		final int newTupleSize)
	{
		return descriptor.o_TruncateTo(this, newTupleSize);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject tupleAt (
		final int index)
	{
		return descriptor.o_TupleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void tupleAtPut (
		final int index,
		final AvailObject aNybbleObject)
	{
		descriptor.o_TupleAtPut(
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
		return descriptor.o_TupleAtPuttingCanDestroy(
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
		return descriptor.o_TupleIntAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int tupleSize ()
	{
		return descriptor.o_TupleSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject kind ()
	{
		return descriptor.o_Kind(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void type (
		final AvailObject value)
	{
		descriptor.o_Type(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeAtIndex (
		final int index)
	{
		return descriptor.o_TypeAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean typeEquals (
		final AvailObject aType)
	{
		return descriptor.o_TypeEquals(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersection (
		final AvailObject another)
	{
		return descriptor.o_TypeIntersection(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfFunctionType (
		final AvailObject aFunctionType)
	{
		return descriptor.o_TypeIntersectionOfFunctionType(
			this,
			aFunctionType);
	}

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	public AvailObject typeIntersectionOfCompiledCodeType (
		final AvailObject aCompiledCodeType)
	{
		return descriptor.o_TypeIntersectionOfCompiledCodeType(
			this,
			aCompiledCodeType);
	}


	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfContainerType (
		final AvailObject aContainerType)
	{
		return descriptor.o_TypeIntersectionOfContainerType(this, aContainerType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfContinuationType (
		final AvailObject aContinuationType)
	{
		return descriptor.o_TypeIntersectionOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor.o_TypeIntersectionOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfMapType (
		final AvailObject aMapType)
	{
		return descriptor.o_TypeIntersectionOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfMeta (
		final AvailObject someMeta)
	{
		return descriptor.o_TypeIntersectionOfMeta(this, someMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor.o_TypeIntersectionOfObjectType(this, anObjectType);
	}

	/**
	 * @param aParseNodeType
	 * @return
	 */
	public AvailObject typeIntersectionOfParseNodeType (
		final AvailObject aParseNodeType)
	{
		return descriptor.o_TypeIntersectionOfParseNodeType(
			this,
			aParseNodeType);
	}

	/**
	 * @param aPojoType
	 * @return
	 */
	public @NotNull AvailObject typeIntersectionOfPojoType (
		final AvailObject aPojoType)
	{
		return descriptor.o_TypeIntersectionOfPojoType(this, aPojoType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfSetType (
		final AvailObject aSetType)
	{
		return descriptor.o_TypeIntersectionOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor.o_TypeIntersectionOfTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeTuple ()
	{
		return descriptor.o_TypeTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnion (
		final AvailObject another)
	{
		return descriptor.o_TypeUnion(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfFunctionType (
		final AvailObject aFunctionType)
	{
		return descriptor.o_TypeUnionOfFunctionType(this, aFunctionType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfContainerType (
		final AvailObject aContainerType)
	{
		return descriptor.o_TypeUnionOfContainerType(this, aContainerType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfContinuationType (
		final AvailObject aContinuationType)
	{
		return descriptor.o_TypeUnionOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor.o_TypeUnionOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfMapType (
		final @NotNull AvailObject aMapType)
	{
		return descriptor.o_TypeUnionOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfObjectType (
		final @NotNull AvailObject anObjectType)
	{
		return descriptor.o_TypeUnionOfObjectType(this, anObjectType);
	}

	/**
	 * @param aParseNodeType
	 * @return
	 */
	public AvailObject typeUnionOfParseNodeType (
		final @NotNull AvailObject aParseNodeType)
	{
		return descriptor.o_TypeUnionOfParseNodeType(
			this,
			aParseNodeType);
	}

	/**
	 * @param aPojoType
	 * @return
	 */
	public @NotNull AvailObject typeUnionOfPojoType (
		final AvailObject aPojoType)
	{
		return descriptor.o_TypeUnionOfPojoType(this, aPojoType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfSetType (
		final @NotNull AvailObject aSetType)
	{
		return descriptor.o_TypeUnionOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor.o_TypeUnionOfTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject unclassified ()
	{
		return descriptor.o_Unclassified(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void unclassified (
		final AvailObject value)
	{
		descriptor.o_Unclassified(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject unionOfTypesAtThrough (
		final int startIndex,
		final int endIndex)
	{
		return descriptor.o_UnionOfTypesAtThrough(
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
		return descriptor.o_UntranslatedDataAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void untranslatedDataAtPut (
		final int index,
		final int value)
	{
		descriptor.o_UntranslatedDataAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject upperBound ()
	{
		return descriptor.o_UpperBound(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean upperInclusive ()
	{
		return descriptor.o_UpperInclusive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject validateArgumentTypesInterpreterIfFail (
		final List<AvailObject> argTypes,
		final Interpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock)
	{
		return descriptor.o_ValidateArgumentTypesInterpreterIfFail(
			this,
			argTypes,
			anAvailInterpreter,
			failBlock);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject value ()
	{
		return descriptor.o_Value(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void value (
		final AvailObject value)
	{
		descriptor.o_Value(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject valueAtIndex (
		final int index)
	{
		return descriptor.o_ValueAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void valueAtIndexPut (
		final int index,
		final AvailObject valueObject)
	{
		descriptor.o_ValueAtIndexPut(
			this,
			index,
			valueObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject valuesAsTuple ()
	{
		return descriptor.o_ValuesAsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject variableBindings ()
	{
		return descriptor.o_VariableBindings(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject vectors ()
	{
		return descriptor.o_Vectors(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void verify ()
	{
		descriptor.o_Verify(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject visibleNames ()
	{
		return descriptor.o_VisibleNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int infinitySign ()
	{
		return descriptor.o_InfinitySign(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject wordcodes ()
	{
		return descriptor.o_Wordcodes(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int zoneForIndex (
		final int index)
	{
		return descriptor.o_ZoneForIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void parsingInstructions (
		final AvailObject instructionsTuple)
	{
		descriptor.o_ParsingInstructions(this, instructionsTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject parsingInstructions ()
	{
		return descriptor.o_ParsingInstructions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void expression (final AvailObject expression)
	{
		descriptor.o_Expression(this, expression);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject expression ()
	{
		return descriptor.o_Expression(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void variable (final AvailObject variable)
	{
		descriptor.o_Variable(this, variable);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject variable ()
	{
		return descriptor.o_Variable(this);
	}


	/**
	 * @return
	 */
	public AvailObject argumentsTuple ()
	{
		return descriptor.o_ArgumentsTuple(this);
	}


	/**
	 * @return
	 */
	public AvailObject statementsTuple ()
	{
		return descriptor.o_StatementsTuple(this);
	}


	/**
	 * @return
	 */
	public AvailObject resultType ()
	{
		return descriptor.o_ResultType(this);
	}


	/**
	 * @param neededVariables
	 */
	public void neededVariables (final AvailObject neededVariables)
	{
		descriptor.o_NeededVariables(this, neededVariables);
	}


	/**
	 * @return
	 */
	public AvailObject neededVariables ()
	{
		return descriptor.o_NeededVariables(this);
	}


	/**
	 * @return
	 */
	public int primitive ()
	{
		return descriptor.o_Primitive(this);
	}


	/**
	 * @return
	 */
	public AvailObject declaredType ()
	{
		return descriptor.o_DeclaredType(this);
	}


	/**
	 * @return
	 */
	public DeclarationKind declarationKind ()
	{
		return descriptor.o_DeclarationKind(this);
	}


	/**
	 * @return
	 */
	public AvailObject initializationExpression ()
	{
		return descriptor.o_InitializationExpression(this);
	}


	/**
	 * @param initializationExpression
	 */
	public void initializationExpression (final AvailObject initializationExpression)
	{
		descriptor.o_InitializationExpression(this, initializationExpression);
	}


	/**
	 * @return
	 */
	public AvailObject literalObject ()
	{
		return descriptor.o_LiteralObject(this);
	}


	/**
	 * @return
	 */
	public AvailObject token ()
	{
		return descriptor.o_Token(this);
	}


	/**
	 * @return
	 */
	public AvailObject markerValue ()
	{
		return descriptor.o_MarkerValue(this);
	}


	/**
	 * @param markerValue
	 */
	public void markerValue (final AvailObject markerValue)
	{
		descriptor.o_MarkerValue(this, markerValue);
	}


	/**
	 * @return
	 */
	public AvailObject arguments ()
	{
		return descriptor.o_Arguments(this);
	}


	/**
	 * @param arguments
	 */
	public void arguments (final AvailObject arguments)
	{
		descriptor.o_Arguments(this, arguments);
	}


	/**
	 * @return
	 */
	public AvailObject implementationSet ()
	{
		return descriptor.o_ImplementationSet(this);
	}


	/**
	 * @param implementationSet
	 */
	public void implementationSet (final AvailObject implementationSet)
	{
		descriptor.o_ImplementationSet(this, implementationSet);
	}


	/**
	 * @return
	 */
	public AvailObject superCastType ()
	{
		return descriptor.o_SuperCastType(this);
	}


	/**
	 * @param superCastType
	 */
	public void superCastType (final AvailObject superCastType)
	{
		descriptor.o_SuperCastType(this, superCastType);
	}


	/**
	 * @return
	 */
	public AvailObject expressionsTuple ()
	{
		return descriptor.o_ExpressionsTuple(this);
	}


	/**
	 * @param expressionsTuple
	 */
	public void expressionsTuple (final AvailObject expressionsTuple)
	{
		descriptor.o_ExpressionsTuple(this, expressionsTuple);
	}


	/**
	 * @return
	 */
	public AvailObject tupleType ()
	{
		return descriptor.o_TupleType(this);
	}


	/**
	 * @param tupleType
	 */
	public void tupleType (final AvailObject tupleType)
	{
		descriptor.o_TupleType(this, tupleType);
	}


	/**
	 * @return
	 */
	public AvailObject declaration ()
	{
		return descriptor.o_Declaration(this);
	}


	/**
	 * @return
	 */
	public AvailObject expressionType ()
	{
		return descriptor.o_ExpressionType(this);
	}


	/**
	 * @param codeGenerator
	 */
	public void emitEffectOn (final AvailCodeGenerator codeGenerator)
	{
		descriptor.o_EmitEffectOn(this, codeGenerator);
	}


	/**
	 * @param codeGenerator
	 */
	public void emitValueOn (final AvailCodeGenerator codeGenerator)
	{
		descriptor.o_EmitValueOn(this, codeGenerator);
	}


	/**
	 * @param object
	 * @param aBlock
	 */
	public void childrenMap (
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		descriptor.o_ChildrenMap(this, aBlock);
	}


	/**
	 * @param object
	 * @param aBlock
	 */
	public void childrenDo (
		final Continuation1<AvailObject> aBlock)
	{
		descriptor.o_ChildrenDo(this, aBlock);
	}


	/**
	 * @param object
	 * @param parent
	 * @param outerBlocks
	 * @param anAvailInterpreter
	 */
	public void validateLocally (
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		descriptor.o_ValidateLocally(
			this,
			parent,
			outerBlocks,
			anAvailInterpreter);
	}


	/**
	 * @param object
	 * @param codeGenerator
	 * @return
	 */
	public AvailObject generate (
		final AvailCodeGenerator codeGenerator)
	{
		return descriptor.o_Generate(this, codeGenerator);
	}


	/**
	 * @param newParseNode
	 * @return
	 */
	public AvailObject copyWith (final AvailObject newParseNode)
	{
		return descriptor.o_CopyWith(this, newParseNode);
	}


	/**
	 * @param isLastUse
	 */
	public void isLastUse (final boolean isLastUse)
	{
		descriptor.o_IsLastUse(this, isLastUse);
	}


	/**
	 * @return
	 */
	public boolean isLastUse ()
	{
		return descriptor.o_IsLastUse(this);
	}


	/**
	 * @return
	 */
	public boolean isMacro ()
	{
		return descriptor.o_IsMacro(this);
	}


	/**
	 * @param value
	 */
	public void macros (final AvailObject value)
	{
		descriptor.o_Macros(this, value);
	}


	/**
	 * @return
	 */
	public AvailObject macros ()
	{
		return descriptor.o_Macros(this);
	}


	/**
	 * @return
	 */
	public AvailObject copyMutableParseNode ()
	{
		return descriptor.o_CopyMutableParseNode(this);
	}


	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject binUnionKind ()
	{
		return descriptor.o_BinUnionKind(this);
	}


	/**
	 * @param value
	 */
	public void macroName (final AvailObject value)
	{
		descriptor.o_MacroName(this, value);
	}


	/**
	 * @return
	 */
	public AvailObject macroName ()
	{
		return descriptor.o_MacroName(this);
	}


	/**
	 * @param value
	 */
	public void outputParseNode (final AvailObject value)
	{
		descriptor.o_OutputParseNode(this, value);
	}


	/**
	 * @return
	 */
	public AvailObject outputParseNode ()
	{
		return descriptor.o_OutputParseNode(this);
	}


	/**
	 * @return
	 */
	public AvailObject apparentSendName ()
	{
		return descriptor.o_ApparentSendName(this);
	}


	/**
	 * @param statementsTuple
	 */
	public void statements (final AvailObject statementsTuple)
	{
		descriptor.o_Statements(this, statementsTuple);
	}


	/**
	 * @return
	 */
	public AvailObject statements ()
	{
		return descriptor.o_Statements(this);
	}


	/**
	 * @param accumulatedStatements
	 */
	public void flattenStatementsInto (final List<AvailObject> accumulatedStatements)
	{
		descriptor.o_FlattenStatementsInto(this, accumulatedStatements);
	}


	/**
	 * @param value
	 */
	public void lineNumber (final int value)
	{
		descriptor.o_LineNumber(this, value);
	}


	/**
	 * @return
	 */
	public int lineNumber ()
	{
		return descriptor.o_LineNumber(this);
	}


	/**
	 * @return
	 */
	public AvailObject allBundles ()
	{
		return descriptor.o_AllBundles(this);
	}


	/**
	 * @return
	 */
	public boolean isSetBin ()
	{
		return descriptor.o_IsSetBin(this);
	}


	/**
	 * @return
	 */
	public MapDescriptor.MapIterable mapIterable ()
	{
		return descriptor.o_MapIterable(this);
	}


	/**
	 * @return
	 */
	public AvailObject complete ()
	{
		return descriptor.o_Complete(this);
	}


	/**
	 * @return
	 */
	public AvailObject incomplete ()
	{
		return descriptor.o_Incomplete(this);
	}


	/**
	 * @return
	 */
	public AvailObject specialActions ()
	{
		return descriptor.o_SpecialActions(this);
	}


	/**
	 * @return
	 */
	public @NotNull AvailObject checkedExceptions ()
	{
		return descriptor.o_CheckedExceptions(this);
	}


	/**
	 * @return
	 */
	public boolean isInt ()
	{
		return descriptor.o_IsInt(this);
	}


	/**
	 * @return
	 */
	public boolean isLong ()
	{
		return descriptor.o_IsLong(this);
	}


	/**
	 */
	public AvailObject argsTupleType ()
	{
		return descriptor.o_ArgsTupleType(this);
	}


	/**
	 * @param anInstanceType
	 * @return
	 */
	public boolean equalsInstanceTypeFor (final AvailObject anInstanceType)
	{
		return descriptor.o_EqualsInstanceTypeFor(this, anInstanceType);
	}


	/**
	 * @return
	 */
	public AvailObject instances ()
	{
		return descriptor.o_Instances(this);
	}


	/**
	 * Determine whether the receiver is a {@linkplain
	 * AbstractUnionTypeDescriptor union type} with the given {@linkplain
	 * SetDescriptor set} of instances.
	 *
	 * @param aSet A set of objects.
	 * @return Whether the receiver is a union type with the given membership.
	 */
	public boolean equalsUnionTypeWithSet (final AvailObject aSet)
	{
		return descriptor.o_EqualsUnionTypeWithSet(this, aSet);
	}


	/**
	 * @return
	 */
	public boolean isAbstractUnionType ()
	{
		return descriptor.o_IsAbstractUnionType(this);
	}


	/**
	 * @param availObject
	 * @return
	 */
	public boolean abstractUnionTypeIncludesInstance (
		final AvailObject potentialInstance)
	{
		return descriptor.o_AbstractUnionTypeIncludesInstance(
			this,
			potentialInstance);
	}


	/**
	 * @return
	 */
	public AvailObject valueType ()
	{
		return descriptor.o_ValueType(this);
	}


	/**
	 * Compute a {@linkplain TypeDescriptor type} that is an ancestor of the
	 * receiver, but is not an {@linkplain AbstractUnionTypeDescriptor abstract
	 * union type}.  Choose the most specific such type.  Fail if the receiver
	 * is not itself an abstract union type.  Also fail if the receiver is
	 * {@linkplain BottomTypeDescriptor bottom}.
	 *
	 * @return The must specific non-union supertype.
	 */
	public AvailObject computeSuperkind ()
	{
		return descriptor.o_ComputeSuperkind(this);
	}


	/**
	 * @param aType
	 * @return
	 */
	public boolean equalsCompiledCodeType (final AvailObject aCompiledCodeType)
	{
		return descriptor.o_EqualsCompiledCodeType(this, aCompiledCodeType);
	}


	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	public boolean isSupertypeOfCompiledCodeType (
		final AvailObject aCompiledCodeType)
	{
		return descriptor.o_IsSupertypeOfCompiledCodeType(
			this,
			aCompiledCodeType);
	}


	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	public AvailObject typeUnionOfCompiledCodeType (
		final AvailObject aCompiledCodeType)
	{
		return descriptor.o_TypeUnionOfCompiledCodeType(
 			this,
 			aCompiledCodeType);
	}


	/**
	 * @param key
	 * @param value
	 * @return
	 */
	public void setAtomProperty (
		final @NotNull AvailObject key,
		final @NotNull AvailObject value)
	{
		descriptor.o_SetAtomProperty(this, key, value);
	}


	/**
	 * @param key
	 * @return
	 */
	public @NotNull AvailObject getAtomProperty (
		final @NotNull AvailObject key)
	{
		return descriptor.o_GetAtomProperty(this, key);
	}


	/**
	 * @return
	 */
	public @NotNull AvailObject innerKind ()
	{
		return descriptor.o_InnerKind(this);
	}


	/**
	 * @param object
	 * @return
	 */
	public @NotNull boolean equalsUnionMeta (final @NotNull AvailObject object)
	{
		return descriptor.o_EqualsUnionMeta(this, object);
	}


	/**
	 * @return
	 */
	public boolean isUnionMeta ()
	{
		return descriptor.o_IsUnionMeta(this);
	}


	/**
	 * @return
	 */
	public @NotNull AvailObject readType ()
	{
		return descriptor.o_ReadType(this);
	}


	/**
	 * @return
	 */
	public @NotNull AvailObject writeType ()
	{
		return descriptor.o_WriteType(this);
	}


	/**
	 * @param value
	 * @return
	 */
	public void versions (final @NotNull AvailObject value)
	{
		descriptor.o_Versions(this, value);
	}


	/**
	 * @return
	 */
	public @NotNull AvailObject versions ()
	{
		return descriptor.o_Versions(this);
	}


	/**
	 * @return
	 */
	public @NotNull ParseNodeKind parseNodeKind ()
	{
		return descriptor.o_ParseNodeKind(this);
	}


	/**
	 * @return
	 */
	public boolean parseNodeKindIsUnder (
		final @NotNull ParseNodeKind expectedParseNodeKind)
	{
		return descriptor.o_ParseNodeKindIsUnder(this, expectedParseNodeKind);
	}

	/**
	 * @return
	 */
	public boolean isRawPojo ()
	{
		return descriptor.o_IsRawPojo(this);
	}

	/**
	 * @param restrictionSignature
	 */
	public void addTypeRestriction (final AvailObject restrictionSignature)
	{
		descriptor.o_AddTypeRestriction(this, restrictionSignature);
	}


	/**
	 * @param restrictionSignature
	 */
	public void removeTypeRestriction (final AvailObject restrictionSignature)
	{
		descriptor.o_RemoveTypeRestriction(this, restrictionSignature);
	}


	/**
	 * @return
	 */
	public @NotNull AvailObject typeRestrictions ()
	{
		return descriptor.o_TypeRestrictions(this);
	}


	/**
	 * @param tupleType
	 */
	public void addSealedArgumentsType (final @NotNull AvailObject tupleType)
	{
		descriptor.o_AddSealedArgumentsType(this, tupleType);
	}


	/**
	 * @param tupleType
	 */
	public void removeSealedArgumentsTup (final AvailObject tupleType)
	{
		descriptor.o_RemoveSealedArgumentsType(this, tupleType);
	}


	/**
	 * @return
	 */
	public AvailObject sealedArgumentsTypesTuple ()
	{
		return descriptor.o_SealedArgumentsTypesTuple(this);
	}


	/**
	 * @param methodNameAtom
	 * @param typeRestrictionFunction
	 */
	public void addTypeRestriction (
		final @NotNull AvailObject methodNameAtom,
		final @NotNull AvailObject typeRestrictionFunction)
	{
		descriptor.o_AddTypeRestriction(
			this,
			methodNameAtom,
			typeRestrictionFunction);
	}


	/**
	 * @param name
	 * @param constantBinding
	 */
	public void addConstantBinding (
		final @NotNull AvailObject name,
		final @NotNull AvailObject constantBinding)
	{
		descriptor.o_AddConstantBinding(
			this,
			name,
			constantBinding);
	}


	/**
	 * @param name
	 * @param variableBinding
	 */
	public void addVariableBinding (
		final @NotNull AvailObject name,
		final @NotNull AvailObject variableBinding)
	{
		descriptor.o_AddVariableBinding(
			this,
			name,
			variableBinding);
	}

	/**
	 * @return
	 */
	public boolean isImplementationSetEmpty ()
	{
		return descriptor.o_IsImplementationSetEmpty(this);
	}

	/**
	 * @return
	 */
	public boolean isPojoSelfType ()
	{
		return descriptor.o_IsPojoSelfType(this);
	}

	/**
	 * @return
	 */
	public @NotNull AvailObject pojoSelfType ()
	{
		return descriptor.o_PojoSelfType(this);
	}
}