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
import com.avail.annotations.NotNull;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.node.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.compiler.scanning.TokenDescriptor;
import com.avail.descriptor.ProcessDescriptor.ExecutionState;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;
import com.avail.visitor.*;

/**
 * This is the base type for all Objects in Avail.  An AvailObject must keep
 * track of its {@link Descriptor}, its integer data, and its references to
 * other AvailObjects.  It specifies the complete complement of messages that
 * can be sent to an {@code AvailObject}, and delegates most of those to its
 * {@code Descriptor}, passing the AvailObject as an additional first argument.
 * The redirected messages in {@code Descriptor} have the prefix "o_", both to
 * make them stand out better and to indicate the additional first argument.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public abstract class AvailObject
implements Iterable<AvailObject>
{
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
			if (indent > descriptor().maximumIndent())
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
				descriptor().printObjectOnAvoidingIndent(
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




	// message redirection - translate

	public boolean greaterThan (
		final AvailObject another)
	{
		//  Translate >= into an inverted lessThan:.  This manual method simplifies renaming rules
		//  greatly, and avoids the need for an Object:greaterThan: in the Descriptors.

		// Reverse the arguments and dispatch to the argument's descriptor.
		return another.descriptor().o_LessThan(another, this);
	}

	public boolean greaterOrEqual (
		final AvailObject another)
	{
		//  Translate >= into an inverted lessOrEqual:.  This manual method simplifies renaming rules
		//  greatly, and avoids the need for an Object:greaterOrEqual: in the Descriptors.

		// Reverse the arguments and dispatch to the argument's descriptor.
		return another.descriptor().o_LessOrEqual(another, this);
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
		// Set up the object to report nice obvious errors if anyone ever
		// accesses it again.

		checkValidAddress();
		if (!descriptor().isMutable())
		{
			return;
		}
		if (!CanDestroyObjects)
		{
			error("Don't invoke this if destructions are disallowed");
		}
		if (sameAddressAs(exceptMe))
		{
			error("What happened?  This object is also the excluded one.");
		}

		// Recursively invoke the iterator on the subobjects of self...
		final AvailSubobjectVisitor vis = new AvailMarkUnreachableSubobjectVisitor(exceptMe);
		scanSubobjects(vis);
		setToInvalidDescriptor();
		return;
	}

	/**
	 * Turn me into an indirection to anotherObject.  WARNING: This alters my
	 * slots and descriptor.
	 */
	abstract public void becomeIndirectionTo (
		final AvailObject anotherObject);


	/**
	 * Extract the byte at the given one-based byte subscript within the
	 * specified field.  Always use little endian encoding.
	 *
	 * @param e An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @return The unsigned byte as a short.
	 */
	abstract public short byteSlotAt (
		final Enum<?> e,
		final int byteSubscript);


	/**
	 * Replace the byte at the given one-based byte subscript within the
	 * specified field.  Always use little endian encoding.
	 *
	 * @param e An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @param aByte The unsigned byte to write, passed as a short.
	 */
	abstract public void byteSlotAtPut (
		final Enum<?> e,
		final int byteSubscript,
		final short aByte);


	/**
	 * Sanity check the object's address.  Fail if it's outside all the current
	 * pages.  Does nothing for AvailObjectUsingArrays.
	 */
	abstract public void checkValidAddress ();


	public void checkWriteForField (
		final Enum<?> e)
	{
		descriptor().checkWriteForField(e);
	}


	abstract public AbstractDescriptor descriptor ();


	abstract public void descriptor (
		final AbstractDescriptor aDescriptor);


	abstract public short descriptorId ();


	abstract public void descriptorId (
		final short anInteger);


	abstract public int integerSlotsCount ();



	/**
	 * Extract the (signed 32-bit) integer for the given field enum value.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @return An int extracted from this AvailObject.
	 */
	abstract public int integerSlot (
		final Enum<?> e);


	/**
	 * Store the (signed 32-bit) integer in the four bytes starting at the
	 * given field enum value.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @param anInteger An int to store in the indicated slot of the receiver.
	 */
	abstract public void integerSlotPut (
		final Enum<?> e,
		final int anInteger);


	/**
	 * Extract the (signed 32-bit) integer at the given field enum value.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return An int extracted from this AvailObject.
	 */
	abstract public int integerSlotAt (
		final Enum<?> e,
		final int subscript);


	/**
	 * Store the (signed 32-bit) integer in the four bytes starting at the
	 * given field enum value.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anInteger An int to store in the indicated slot of the receiver.
	 */
	abstract public void integerSlotAtPut (
		final Enum<?> e,
		final int subscript,
		final int anInteger);


	abstract public int objectSlotsCount ();



	/**
	 * Store the AvailObject in the receiver at the given byte-index.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @return The AvailObject found at the specified slot in the receiver.
	 */
	abstract public AvailObject objectSlot (
		final Enum<?> e);


	/**
	 * Store the AvailObject in the specified slot of the receiver.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @param anAvailObject The AvailObject to store at the specified slot.
	 */
	abstract public void objectSlotPut (
		final Enum<?> e,
		final AvailObject anAvailObject);


	/**
	 * Extract the AvailObject at the specified slot of the receiver.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return The AvailObject found at the specified slot in the receiver.
	 */
	abstract public AvailObject objectSlotAt (
		final Enum<?> e,
		final int subscript);


	/**
	 * Store the AvailObject in the specified slot of the receiver.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anAvailObject The AvailObject to store at the specified slot.
	 */
	abstract public void objectSlotAtPut (
		final Enum<?> e,
		final int subscript,
		final AvailObject anAvailObject);



	public boolean isDestroyed ()
	{
		checkValidAddress();
		return descriptorId() == FillerDescriptor.mutable().id();
	}



	/**
	 * Answer whether the objects occupy the same memory addresses.
	 *
	 * @param anotherObject The object to compare the receiver's address to.
	 * @return Whether the objects occupy the same storage.
	 */
	abstract public boolean sameAddressAs (
		final AvailObject anotherObject);


	/**
	 * Replace my descriptor field with a FillerDescriptor.  This blows up for
	 * most messages, catching incorrect (all, by definition) further
	 * accidental uses of this object.
	 */
	public void setToInvalidDescriptor ()
	{
		// verifyToSpaceAddress();
		descriptor(FillerDescriptor.mutable());
	}


	/**
	 * Extract a (16-bit signed) short at the given short-index of the receiver.
	 *
	 * @param e The enumeration value that identifies the base field.
	 * @param shortIndex The index in bytes (must be even).
	 * @return The short found at the given short-index.
	 */
	abstract public short shortSlotAt (
		final Enum<?> e,
		final int shortIndex);

	/**
	 * Store the (16-bit signed) short at the given short-index of the receiver.
	 *
	 * @param e The enumeration value that identifies the base field.
	 * @param shortIndex The index in bytes (must be even).
	 * @param aShort The short to store at the given short-index.
	 */
	abstract public void shortSlotAtPut (
		final Enum<?> e,
		final int shortIndex,
		final short aShort);


	/**
	 * Slice the current object into two objects, the left one (at the same
	 * starting address as the input), and the right one (a Filler object that
	 * nobody should ever create a pointer to).  The new Filler can have zero
	 * post-header slots (i.e., just the header), but the left object must not,
	 * since it may turn into an Indirection some day and will require at least
	 * one slot for the target pointer.
	 *
	 * @param newSlotsCount The number of slots in the left object.
	 */
	abstract public void truncateWithFillerForNewIntegerSlotsCount (
		final int newSlotsCount);


	/**
	 * Slice the current object into two parts, one of which is a Filler object
	 * and is never referred to directly (so doesn't need any slots for becoming
	 * an indirection).
	 */
	abstract public void truncateWithFillerForNewObjectSlotsCount (
		final int newSlotsCount);


	/**
	 * Check that my address is a valid pointer to ToSpace.
	 */
	public final void verifyToSpaceAddress ()
	{
		return;
	}



	/**
	 * Compute the 32-bit hash of the receiver.
	 *
	 * @return An {@code int} hash value.
	 */
	public final int hash ()
	{
		return descriptor().o_Hash(this);
	}


	/**
	 * Set the 32-bit hash in the receiver.
	 *
	 * @param value The {@code int} to store as the receiver's hash value.
	 */
	public final void hash (
		final int value)
	{
		descriptor().o_Hash(this, value);
	}

	/**
	 * The receiver implements {@link #hashCode()} so Java can use an {@link
	 * AvailObject} in a Java {@link Set} or {@link Map}.
	 */
	@Override
	public final int hashCode ()
	{
		return descriptor().o_Hash(this);
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
		IntegerRangeTypeDescriptor.createWellKnownObjects();
		L2ChunkDescriptor.createWellKnownObjects();
	}

	/**
	 * A good multiplier for a multiplicative random generator.  This constant
	 * is a primitive element of (Z[2^32],*), specifically 1664525, as taken
	 * from Knuth, The Art of Computer Programming, Vol. 2, 2nd ed., page 102,
	 * row 26. See also pages 19, 20, theorems B and C. The period of the
	 * cycle based on this multiplicative generator is 2^30.
	 */
	public static final int Multiplier = 1664525;

	public static void error(final Object... args)
	{
		throw new RuntimeException((String)args[0]);
	};

	public static AvailObject newIndexedDescriptor(
		final int size,
		final AbstractDescriptor descriptor)
	{
		return AvailObjectUsingArrays.newIndexedDescriptor(size, descriptor);
	};

	public static AvailObject newObjectIndexedIntegerIndexedDescriptor(
		final int variableObjectSlots,
		final int variableIntegerSlots,
		final AbstractDescriptor descriptor)
	{
		return AvailObjectUsingArrays.newObjectIndexedIntegerIndexedDescriptor(
			variableObjectSlots,
			variableIntegerSlots,
			descriptor);
	};

	@Deprecated
	static int scanAnObject()
	{
		// Scan the next object, saving its subobjects and removing its barrier.
		return 0;   // AvailObjectUsingArrays.scanAnObject();
	};


	/* Synchronization with GC.  Locked objects can be moved, but not coalesced. */

	private final static List<AvailObject> LockedObjects =
		new ArrayList<AvailObject>(10);

	public static void lock (final AvailObject obj)
	{
		LockedObjects.add(obj);
	};
	public static void unlock (final AvailObject obj)
	{
		final AvailObject popped = LockedObjects.remove(LockedObjects.size() - 1);
		assert popped == obj;
	};

	private static boolean CanAllocateObjects = true;
	private static boolean CanDestroyObjects = true;

	public static void CanAllocateObjects (final boolean flag)
	{
		CanAllocateObjects = flag;
	}

	public static boolean CanAllocateObjects ()
	{
		return CanAllocateObjects;
	}

	public static void CanDestroyObjects (final boolean flag)
	{
		CanDestroyObjects = flag;
	}

	public static boolean CanDestroyObjects ()
	{
		return CanDestroyObjects;
	}



	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsArgTypesFromClosureType (
		final AvailObject closureType)
	{
		return descriptor().o_AcceptsArgTypesFromClosureType(this, closureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsArgumentsFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		return descriptor().o_AcceptsArgumentsFromContinuationStackp(
			this,
			continuation,
			stackp);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsArgumentTypesFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		return descriptor().o_AcceptsArgumentTypesFromContinuationStackp(
			this,
			continuation,
			stackp);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsArrayOfArgTypes (
		final List<AvailObject> argTypes)
	{
		return descriptor().o_AcceptsArrayOfArgTypes(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsArrayOfArgValues (
		final List<AvailObject> argValues)
	{
		return descriptor().o_AcceptsArrayOfArgValues(this, argValues);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsTupleOfArgTypes (
		final AvailObject argTypes)
	{
		return descriptor().o_AcceptsTupleOfArgTypes(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean acceptsTupleOfArguments (
		final AvailObject arguments)
	{
		return descriptor().o_AcceptsTupleOfArguments(this, arguments);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void addDependentChunkId (
		final int aChunkIndex)
	{
		descriptor().o_AddDependentChunkId(this, aChunkIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void addImplementation (
		final AvailObject implementation)
	{
		descriptor().o_AddImplementation(this, implementation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void addRestrictions (
		final AvailObject restrictions)
	{
		descriptor().o_AddRestrictions(this, restrictions);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject addToInfinityCanDestroy (
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		return descriptor().o_AddToInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject addToIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor().o_AddToIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void argsLocalsStackOutersPrimitive (
		final int args,
		final int locals,
		final int stack,
		final int outers,
		final int primitive)
	{
		descriptor().o_ArgsLocalsStackOutersPrimitive(
			this,
			args,
			locals,
			stack,
			outers,
			primitive);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject argTypeAt (
		final int index)
	{
		return descriptor().o_ArgTypeAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void argTypeAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().o_ArgTypeAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public String asNativeString ()
	{
		return descriptor().o_AsNativeString(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject asObject ()
	{
		return descriptor().o_AsObject(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject asSet ()
	{
		return descriptor().o_AsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject asTuple ()
	{
		return descriptor().o_AsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void atAddMessageRestrictions (
		final AvailObject methodName,
		final AvailObject illegalArgMsgs)
	{
		descriptor().o_AtAddMessageRestrictions(
			this,
			methodName,
			illegalArgMsgs);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void atAddMethodImplementation (
		final AvailObject methodName,
		final AvailObject implementation)
	{
		descriptor().o_AtAddMethodImplementation(
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
		descriptor().o_AtMessageAddBundle(
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
		descriptor().o_AtNameAdd(
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
		descriptor().o_AtNewNamePut(
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
		descriptor().o_AtPrivateNameAdd(
			this,
			stringName,
			trueName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject becomeExactType ()
	{
		return descriptor().o_BecomeExactType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void becomeRealTupleType ()
	{
		descriptor().o_BecomeRealTupleType(this);
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
		return descriptor().o_BinAddingElementHashLevelCanDestroy(
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
		return descriptor().o_BinElementAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binElementAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().o_BinElementAtPut(
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
		return descriptor().o_BinHasElementHash(
			this,
			elementObject,
			elementObjectHash);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int binHash ()
	{
		return descriptor().o_BinHash(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binHash (
		final int value)
	{
		descriptor().o_BinHash(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject binRemoveElementHashCanDestroy (
		final AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{
		return descriptor().o_BinRemoveElementHashCanDestroy(
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
		return descriptor().o_BinSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binSize (
		final int value)
	{
		descriptor().o_BinSize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject binUnionType ()
	{
		return descriptor().o_BinUnionType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binUnionType (
		final AvailObject value)
	{
		descriptor().o_BinUnionType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int bitsPerEntry ()
	{
		return descriptor().o_BitsPerEntry(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int bitVector ()
	{
		return descriptor().o_BitVector(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void bitVector (
		final int value)
	{
		descriptor().o_BitVector(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject bodyBlock ()
	{
		return descriptor().o_BodyBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void bodyBlock (
		final AvailObject value)
	{
		descriptor().o_BodyBlock(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void bodyBlockRequiresBlockReturnsBlock (
		final AvailObject bb,
		final AvailObject rqb,
		final AvailObject rtb)
	{
		descriptor().o_BodyBlockRequiresBlockReturnsBlock(
			this,
			bb,
			rqb,
			rtb);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject bodySignature ()
	{
		return descriptor().o_BodySignature(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void bodySignature (
		final AvailObject signature)
	{
		descriptor().o_BodySignature(this, signature);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void bodySignatureRequiresBlockReturnsBlock (
		final AvailObject bs,
		final AvailObject rqb,
		final AvailObject rtb)
	{
		descriptor().o_BodySignatureRequiresBlockReturnsBlock(
			this,
			bs,
			rqb,
			rtb);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject breakpointBlock ()
	{
		return descriptor().o_BreakpointBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void breakpointBlock (
		final AvailObject value)
	{
		descriptor().o_BreakpointBlock(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void buildFilteredBundleTreeFrom (
		final AvailObject bundleTree)
	{
		descriptor().o_BuildFilteredBundleTreeFrom(this, bundleTree);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject bundleAtMessageParts (
		final AvailObject message,
		final AvailObject parts,
		final AvailObject instructions)
	{
		return descriptor().o_BundleAtMessageParts(
			this,
			message,
			parts,
			instructions);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject caller ()
	{
		return descriptor().o_Caller(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void caller (
		final AvailObject value)
	{
		descriptor().o_Caller(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean canComputeHashOfType ()
	{
		return descriptor().o_CanComputeHashOfType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int capacity ()
	{
		return descriptor().o_Capacity(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void cleanUpAfterCompile ()
	{
		descriptor().o_CleanUpAfterCompile(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void clearValue ()
	{
		descriptor().o_ClearValue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject closure ()
	{
		return descriptor().o_Closure(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void closure (
		final AvailObject value)
	{
		descriptor().o_Closure(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject closureType ()
	{
		return descriptor().o_ClosureType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void closureType (
		final AvailObject value)
	{
		descriptor().o_ClosureType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject code ()
	{
		return descriptor().o_Code(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void code (
		final AvailObject value)
	{
		descriptor().o_Code(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int codePoint ()
	{
		return descriptor().o_CodePoint(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void codePoint (
		final int value)
	{
		descriptor().o_CodePoint(this, value);
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
		return descriptor().o_CompareFromToWithStartingAt(
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
		return descriptor().o_CompareFromToWithAnyTupleStartingAt(
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
		return descriptor().o_CompareFromToWithByteStringStartingAt(
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
		return descriptor().o_CompareFromToWithByteTupleStartingAt(
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
		return descriptor().o_CompareFromToWithNybbleTupleStartingAt(
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
		return descriptor().o_CompareFromToWithObjectTupleStartingAt(
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
		return descriptor().o_CompareFromToWithTwoByteStringStartingAt(
			this,
			startIndex1,
			endIndex1,
			aTwoByteString,
			startIndex2);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject complete ()
	{
		return descriptor().o_Complete(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void complete (
		final AvailObject value)
	{
		descriptor().o_Complete(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int computeHashFromTo (
		final int start,
		final int end)
	{
		return descriptor().o_ComputeHashFromTo(
			this,
			start,
			end);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject computeReturnTypeFromArgumentTypes (
		final List<AvailObject> argTypes,
		final Interpreter anAvailInterpreter)
	{
		return descriptor().o_ComputeReturnTypeFromArgumentTypesInterpreter(
			this,
			argTypes,
			anAvailInterpreter);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject concatenateTuplesCanDestroy (
		final boolean canDestroy)
	{
		return descriptor().o_ConcatenateTuplesCanDestroy(this, canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject constantBindings ()
	{
		return descriptor().o_ConstantBindings(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void constantBindings (
		final AvailObject value)
	{
		descriptor().o_ConstantBindings(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean containsBlock (
		final AvailObject aClosure)
	{
		return descriptor().o_ContainsBlock(this, aClosure);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject contentType ()
	{
		return descriptor().o_ContentType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void contentType (
		final AvailObject value)
	{
		descriptor().o_ContentType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject contingentImpSets ()
	{
		return descriptor().o_ContingentImpSets(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void contingentImpSets (
		final AvailObject value)
	{
		descriptor().o_ContingentImpSets(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject continuation ()
	{
		return descriptor().o_Continuation(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void continuation (
		final AvailObject value)
	{
		descriptor().o_Continuation(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyAsMutableContinuation ()
	{
		return descriptor().o_CopyAsMutableContinuation(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyAsMutableObjectTuple ()
	{
		return descriptor().o_CopyAsMutableObjectTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyAsMutableSpliceTuple ()
	{
		return descriptor().o_CopyAsMutableSpliceTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyMutable ()
	{
		return descriptor().o_CopyMutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void copyToRestrictedTo (
		final AvailObject filteredBundleTree,
		final AvailObject visibleNames)
	{
		descriptor().o_CopyToRestrictedTo(
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
		return descriptor().o_CopyTupleFromToCanDestroy(
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
		return descriptor().o_CouldEverBeInvokedWith(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject createTestingTreeWithPositiveMatchesRemainingPossibilities (
		final AvailObject positiveTuple,
		final AvailObject possibilities)
	{
		return descriptor().o_CreateTestingTreeWithPositiveMatchesRemainingPossibilities(
			this,
			positiveTuple,
			possibilities);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject dataAtIndex (
		final int index)
	{
		return descriptor().o_DataAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void dataAtIndexPut (
		final int index,
		final AvailObject value)
	{
		descriptor().o_DataAtIndexPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject defaultType ()
	{
		return descriptor().o_DefaultType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void defaultType (
		final AvailObject value)
	{
		descriptor().o_DefaultType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject dependentChunks ()
	{
		return descriptor().o_DependentChunks(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void dependentChunks (
		final AvailObject value)
	{
		descriptor().o_DependentChunks(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int parsingPc ()
	{
		return descriptor().o_ParsingPc(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void parsingPc (
		final int value)
	{
		descriptor().o_ParsingPc(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void displayTestingTree ()
	{
		descriptor().o_DisplayTestingTree(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject divideCanDestroy (
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return descriptor().o_DivideCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject divideIntoInfinityCanDestroy (
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		return descriptor().o_DivideIntoInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject divideIntoIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor().o_DivideIntoIntegerCanDestroy(
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
		return descriptor().o_ElementAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void elementAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().o_ElementAtPut(
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
		return descriptor().o_EndOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int endSubtupleIndexInZone (
		final int zone)
	{
		return descriptor().o_EndSubtupleIndexInZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void ensureMetacovariant ()
	{
		descriptor().o_EnsureMetacovariant(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject ensureMutable ()
	{
		return descriptor().o_EnsureMutable(this);
	}

	/**
	 * This comparison operation takes an {@link Object} as its argument to
	 * avoid accidentally calling this with, say, a {@link String} literal.
	 * We mark it as deprecated to ensure we don't accidentally invoke
	 * this method when we really mean the version that takes an {@code
	 * AvailObject} as an argument.  That's a convenient warning for the
	 * programmer, but we also fail if this method actually gets invoked AND
	 * the argument is not an {@code AvailObject}.  We have to still allow this
	 * to run for things like looking up an {@code AvailObject} in a {@code
	 * List}, but we can fail fast if we discover the receiver is being compared
	 * to something other than an {@code AvailObject}.
	 */
	@Override
	@Deprecated
	public boolean equals (
		final Object another)
	{
		assert another instanceof AvailObject;
		return descriptor().o_Equals(this, (AvailObject)another);
	}

	/**
	 * Dispatch to the descriptor.  Note that its argument is of type {@link
	 * Object} to ensure we don't accidentally invoke the version defined in
	 * {@code Object}.
	 */
	public boolean equals (
		final AvailObject another)
	{
		return descriptor().o_Equals(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsAnyTuple (
		final AvailObject anotherTuple)
	{
		return descriptor().o_EqualsAnyTuple(this, anotherTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsBlank ()
	{
		return descriptor().o_EqualsBlank(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsByteString (
		final AvailObject aByteString)
	{
		return descriptor().o_EqualsByteString(this, aByteString);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsByteTuple (
		final AvailObject aByteTuple)
	{
		return descriptor().o_EqualsByteTuple(this, aByteTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsCharacterWithCodePoint (
		final int otherCodePoint)
	{
		return descriptor().o_EqualsCharacterWithCodePoint(this, otherCodePoint);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsClosure (
		final AvailObject aClosure)
	{
		return descriptor().o_EqualsClosure(this, aClosure);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsClosureType (
		final AvailObject aClosureType)
	{
		return descriptor().o_EqualsClosureType(this, aClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsCompiledCode (
		final AvailObject aCompiledCode)
	{
		return descriptor().o_EqualsCompiledCode(this, aCompiledCode);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContainer (
		final AvailObject aContainer)
	{
		return descriptor().o_EqualsContainer(this, aContainer);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContainerType (
		final AvailObject aContainerType)
	{
		return descriptor().o_EqualsContainerType(this, aContainerType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContinuation (
		final AvailObject aContinuation)
	{
		return descriptor().o_EqualsContinuation(this, aContinuation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContinuationType (
		final AvailObject aType)
	{
		return descriptor().o_EqualsContinuationType(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsDouble (
		final AvailObject aDoubleObject)
	{
		return descriptor().o_EqualsDouble(this, aDoubleObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsFalse ()
	{
		return descriptor().o_EqualsFalse(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsFloat (
		final AvailObject aFloatObject)
	{
		return descriptor().o_EqualsFloat(this, aFloatObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsGeneralizedClosureType (
		final AvailObject aType)
	{
		return descriptor().o_EqualsGeneralizedClosureType(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsInfinity (
		final AvailObject anInfinity)
	{
		return descriptor().o_EqualsInfinity(this, anInfinity);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsInteger (
		final AvailObject anAvailInteger)
	{
		return descriptor().o_EqualsInteger(this, anAvailInteger);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor().o_EqualsIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsMap (
		final AvailObject aMap)
	{
		return descriptor().o_EqualsMap(this, aMap);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsMapType (
		final AvailObject aMapType)
	{
		return descriptor().o_EqualsMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsNybbleTuple (
		final AvailObject aNybbleTuple)
	{
		return descriptor().o_EqualsNybbleTuple(this, aNybbleTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsObject (
		final AvailObject anObject)
	{
		return descriptor().o_EqualsObject(this, anObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsObjectTuple (
		final AvailObject anObjectTuple)
	{
		return descriptor().o_EqualsObjectTuple(this, anObjectTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsPrimitiveType (
		final AvailObject aPrimitiveType)
	{
		return descriptor().o_EqualsPrimitiveType(this, aPrimitiveType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsSet (
		final AvailObject aSet)
	{
		return descriptor().o_EqualsSet(this, aSet);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsSetType (
		final AvailObject aSetType)
	{
		return descriptor().o_EqualsSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsTrue ()
	{
		return descriptor().o_EqualsTrue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsTupleType (
		final AvailObject aTupleType)
	{
		return descriptor().o_EqualsTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsTwoByteString (
		final AvailObject aTwoByteString)
	{
		return descriptor().o_EqualsTwoByteString(this, aTwoByteString);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsVoid ()
	{
		return descriptor().o_EqualsVoid(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsVoidOrBlank ()
	{
		return descriptor().o_EqualsVoidOrBlank(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void evictedByGarbageCollector ()
	{
		descriptor().o_EvictedByGarbageCollector(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject exactType ()
	{
		return descriptor().o_ExactType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public ExecutionState executionState ()
	{
		return descriptor().o_ExecutionState(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void executionState (
		final ExecutionState value)
	{
		descriptor().o_ExecutionState(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject expand ()
	{
		return descriptor().o_Expand(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean extractBoolean ()
	{
		return descriptor().o_ExtractBoolean(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short extractByte ()
	{
		return descriptor().o_ExtractByte(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public double extractDouble ()
	{
		return descriptor().o_ExtractDouble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public float extractFloat ()
	{
		return descriptor().o_ExtractFloat(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int extractInt ()
	{
		return descriptor().o_ExtractInt(this);
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
		return descriptor().o_ExtractLong(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public byte extractNybble ()
	{
		return descriptor().o_ExtractNybble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public byte extractNybbleFromTupleAt (
		final int index)
	{
		return descriptor().o_ExtractNybbleFromTupleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject fieldMap ()
	{
		return descriptor().o_FieldMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void fieldMap (
		final AvailObject value)
	{
		descriptor().o_FieldMap(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject fieldTypeMap ()
	{
		return descriptor().o_FieldTypeMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void fieldTypeMap (
		final AvailObject value)
	{
		descriptor().o_FieldTypeMap(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public List<AvailObject> filterByTypes (
		final List<AvailObject> argTypes)
		{
		return descriptor().o_FilterByTypes(this, argTypes);
		}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject filteredBundleTree ()
	{
		return descriptor().o_FilteredBundleTree(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void filteredBundleTree (
		final AvailObject value)
	{
		descriptor().o_FilteredBundleTree(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject firstTupleType ()
	{
		return descriptor().o_FirstTupleType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void firstTupleType (
		final AvailObject value)
	{
		descriptor().o_FirstTupleType(this, value);
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
		return descriptor().o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone(
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
		return descriptor().o_GetInteger(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject getValue ()
	{
		return descriptor().o_GetValue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean greaterThanInteger (
		final AvailObject another)
	{
		return descriptor().o_GreaterThanInteger(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean greaterThanSignedInfinity (
		final AvailObject another)
	{
		return descriptor().o_GreaterThanSignedInfinity(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasElement (
		final AvailObject elementObject)
	{
		return descriptor().o_HasElement(this, elementObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hashFromTo (
		final int startIndex,
		final int endIndex)
	{
		return descriptor().o_HashFromTo(
			this,
			startIndex,
			endIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hashOfType ()
	{
		return descriptor().o_HashOfType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hashOrZero ()
	{
		return descriptor().o_HashOrZero(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hashOrZero (
		final int value)
	{
		descriptor().o_HashOrZero(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasKey (
		final AvailObject keyObject)
	{
		return descriptor().o_HasKey(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasObjectInstance (
		final AvailObject potentialInstance)
	{
		return descriptor().o_HasObjectInstance(this, potentialInstance);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasRestrictions ()
	{
		return descriptor().o_HasRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hiLevelTwoChunkLowOffset ()
	{
		return descriptor().o_HiLevelTwoChunkLowOffset(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hiLevelTwoChunkLowOffset (
		final int value)
	{
		descriptor().o_HiLevelTwoChunkLowOffset(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hiNumLocalsLowNumArgs ()
	{
		return descriptor().o_HiNumLocalsLowNumArgs(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hiNumLocalsLowNumArgs (
		final int value)
	{
		descriptor().o_HiNumLocalsLowNumArgs(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hiPrimitiveLowNumArgsAndLocalsAndStack ()
	{
		return descriptor().o_HiPrimitiveLowNumArgsAndLocalsAndStack(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hiPrimitiveLowNumArgsAndLocalsAndStack (
		final int value)
	{
		descriptor().o_HiPrimitiveLowNumArgsAndLocalsAndStack(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int hiStartingChunkIndexLowNumOuters ()
	{
		return descriptor().o_HiStartingChunkIndexLowNumOuters(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hiStartingChunkIndexLowNumOuters (
		final int value)
	{
		descriptor().o_HiStartingChunkIndexLowNumOuters(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public List<AvailObject> implementationsAtOrBelow (
		final List<AvailObject> argTypes)
		{
		return descriptor().o_ImplementationsAtOrBelow(this, argTypes);
		}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject implementationsTuple ()
	{
		return descriptor().o_ImplementationsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void implementationsTuple (
		final AvailObject value)
	{
		descriptor().o_ImplementationsTuple(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject includeBundle (
		final AvailObject messageBundle)
	{
		return descriptor().o_IncludeBundle(
			this,
			messageBundle);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean includes (
		final AvailObject imp)
	{
		return descriptor().o_Includes(this, imp);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int inclusiveFlags ()
	{
		return descriptor().o_InclusiveFlags(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void inclusiveFlags (
		final int value)
	{
		descriptor().o_InclusiveFlags(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject incomplete ()
	{
		return descriptor().o_Incomplete(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void incomplete (
		final AvailObject value)
	{
		descriptor().o_Incomplete(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int index ()
	{
		return descriptor().o_Index(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void index (
		final int value)
	{
		descriptor().o_Index(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject innerType ()
	{
		return descriptor().o_InnerType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void innerType (
		final AvailObject value)
	{
		descriptor().o_InnerType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject instance ()
	{
		return descriptor().o_Instance(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void instance (
		final AvailObject value)
	{
		descriptor().o_Instance(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int internalHash ()
	{
		return descriptor().o_InternalHash(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void internalHash (
		final int value)
	{
		descriptor().o_InternalHash(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int interruptRequestFlag ()
	{
		return descriptor().o_InterruptRequestFlag(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void interruptRequestFlag (
		final int value)
	{
		descriptor().o_InterruptRequestFlag(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int invocationCount ()
	{
		return descriptor().o_InvocationCount(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void invocationCount (
		final int value)
	{
		descriptor().o_InvocationCount(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isAbstract ()
	{
		return descriptor().o_IsAbstract(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBetterRepresentationThan (
		final AvailObject anotherObject)
	{
		return descriptor().o_IsBetterRepresentationThan(this, anotherObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBetterRepresentationThanTupleType (
		final AvailObject aTupleType)
	{
		return descriptor().o_IsBetterRepresentationThanTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBinSubsetOf (
		final AvailObject potentialSuperset)
	{
		return descriptor().o_IsBinSubsetOf(this, potentialSuperset);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBoolean ()
	{
		return descriptor().o_IsBoolean(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isByte ()
	{
		return descriptor().o_IsByte(this);
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
		return descriptor().o_IsByteTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isCharacter ()
	{
		return descriptor().o_IsCharacter(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isClosure ()
	{
		return descriptor().o_IsClosure(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isCyclicType ()
	{
		return descriptor().o_IsCyclicType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isExtendedInteger ()
	{
		return descriptor().o_IsExtendedInteger(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isFinite ()
	{
		return descriptor().o_IsFinite(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isForward ()
	{
		return descriptor().o_IsForward(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isHashAvailable ()
	{
		return descriptor().o_IsHashAvailable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isMethod ()
	{
		return descriptor().o_IsMethod(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isInstanceOfSubtypeOf (
		final AvailObject aType)
	{
		return descriptor().o_IsInstanceOfSubtypeOf(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isIntegerRangeType ()
	{
		return descriptor().o_IsIntegerRangeType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isMap ()
	{
		return descriptor().o_IsMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isMapType ()
	{
		return descriptor().o_IsMapType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isNybble ()
	{
		return descriptor().o_IsNybble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isPositive ()
	{
		return descriptor().o_IsPositive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSaved ()
	{
		return descriptor().o_IsSaved(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void isSaved (
		final boolean aBoolean)
	{
		descriptor().o_IsSaved(this, aBoolean);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSet ()
	{
		return descriptor().o_IsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSetType ()
	{
		return descriptor().o_IsSetType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSplice ()
	{
		return descriptor().o_IsSplice(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSubsetOf (
		final AvailObject another)
	{
		return descriptor().o_IsSubsetOf(this, another);
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
		return descriptor().o_IsString(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSubtypeOf (
		final AvailObject aType)
	{
		return descriptor().o_IsSubtypeOf(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfClosureType (
		final AvailObject aClosureType)
	{
		return descriptor().o_IsSupertypeOfClosureType(this, aClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfContainerType (
		final AvailObject aContainerType)
	{
		return descriptor().o_IsSupertypeOfContainerType(this, aContainerType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfContinuationType (
		final AvailObject aContinuationType)
	{
		return descriptor().o_IsSupertypeOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfCyclicType (
		final AvailObject aCyclicType)
	{
		return descriptor().o_IsSupertypeOfCyclicType(this, aCyclicType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfGeneralizedClosureType (
		final AvailObject aGeneralizedClosureType)
	{
		return descriptor().o_IsSupertypeOfGeneralizedClosureType(this, aGeneralizedClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor().o_IsSupertypeOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfMapType (
		final AvailObject aMapType)
	{
		return descriptor().o_IsSupertypeOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfObjectMeta (
		final AvailObject anObjectMeta)
	{
		return descriptor().o_IsSupertypeOfObjectMeta(this, anObjectMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfObjectMetaMeta (
		final AvailObject anObjectMetaMeta)
	{
		return descriptor().o_IsSupertypeOfObjectMetaMeta(this, anObjectMetaMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor().o_IsSupertypeOfObjectType(this, anObjectType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfPrimitiveType (
		final AvailObject aPrimitiveType)
	{
		return descriptor().o_IsSupertypeOfPrimitiveType(this, aPrimitiveType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfSetType (
		final AvailObject aSetType)
	{
		return descriptor().o_IsSupertypeOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfTerminates ()
	{
		return descriptor().o_IsSupertypeOfTerminates(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor().o_IsSupertypeOfTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfVoid ()
	{
		return descriptor().o_IsSupertypeOfVoid(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isTuple ()
	{
		return descriptor().o_IsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isTupleType ()
	{
		return descriptor().o_IsTupleType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isType ()
	{
		return descriptor().o_IsType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isValid ()
	{
		return descriptor().o_IsValid(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void isValid (
		final boolean aBoolean)
	{
		descriptor().o_IsValid(this, aBoolean);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isValidForArgumentTypesInterpreter (
		final List<AvailObject> argTypes,
		final Interpreter interpreter)
	{
		return descriptor().o_IsValidForArgumentTypesInterpreter(
			this,
			argTypes,
			interpreter);
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
		return descriptor().o_Iterator(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject keyAtIndex (
		final int index)
	{
		return descriptor().o_KeyAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void keyAtIndexPut (
		final int index,
		final AvailObject keyObject)
	{
		descriptor().o_KeyAtIndexPut(
			this,
			index,
			keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public List<AvailObject> keysAsArray ()
	{
		return descriptor().o_KeysAsArray(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject keysAsSet ()
	{
		return descriptor().o_KeysAsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject keyType ()
	{
		return descriptor().o_KeyType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void keyType (
		final AvailObject value)
	{
		descriptor().o_KeyType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean lessOrEqual (
		final AvailObject another)
	{
		return descriptor().o_LessOrEqual(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean lessThan (
		final AvailObject another)
	{
		return descriptor().o_LessThan(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int levelTwoChunkIndex ()
	{
		return descriptor().o_LevelTwoChunkIndex(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void levelTwoChunkIndexOffset (
		final int index,
		final int offset)
	{
		descriptor().o_LevelTwoChunkIndexOffset(
			this,
			index,
			offset);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int levelTwoOffset ()
	{
		return descriptor().o_LevelTwoOffset(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject literal ()
	{
		return descriptor().o_Literal(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void literal (
		final AvailObject value)
	{
		descriptor().o_Literal(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject literalAt (
		final int index)
	{
		return descriptor().o_LiteralAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void literalAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().o_LiteralAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject localOrArgOrStackAt (
		final int index)
	{
		return descriptor().o_LocalOrArgOrStackAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void localOrArgOrStackAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().o_LocalOrArgOrStackAtPut(
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
		return descriptor().o_LocalTypeAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByTypesFromArray (
		final List<AvailObject> argumentTypeArray)
	{
		return descriptor().o_LookupByTypesFromArray(this, argumentTypeArray);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByTypesFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		return descriptor().o_LookupByTypesFromContinuationStackp(
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
		return descriptor().o_LookupByTypesFromTuple(this, argumentTypeTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByValuesFromArray (
		final List<AvailObject> argumentArray)
	{
		return descriptor().o_LookupByValuesFromArray(this, argumentArray);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByValuesFromContinuationStackp (
		final AvailObject continuation,
		final int stackp)
	{
		return descriptor().o_LookupByValuesFromContinuationStackp(
			this,
			continuation,
			stackp);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByValuesFromTuple (
		final AvailObject argumentTuple)
	{
		return descriptor().o_LookupByValuesFromTuple(this, argumentTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lowerBound ()
	{
		return descriptor().o_LowerBound(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void lowerBound (
		final AvailObject value)
	{
		descriptor().o_LowerBound(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean lowerInclusive ()
	{
		return descriptor().o_LowerInclusive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void lowerInclusiveUpperInclusive (
		final boolean lowInc,
		final boolean highInc)
	{
		descriptor().o_LowerInclusiveUpperInclusive(
			this,
			lowInc,
			highInc);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject makeImmutable ()
	{
		return descriptor().o_MakeImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void makeSubobjectsImmutable ()
	{
		descriptor().o_MakeSubobjectsImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject mapAt (
		final AvailObject keyObject)
	{
		return descriptor().o_MapAt(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject mapAtPuttingCanDestroy (
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		return descriptor().o_MapAtPuttingCanDestroy(
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
		return descriptor().o_MapSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void mapSize (
		final int value)
	{
		descriptor().o_MapSize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject mapWithoutKeyCanDestroy (
		final AvailObject keyObject,
		final boolean canDestroy)
	{
		return descriptor().o_MapWithoutKeyCanDestroy(
			this,
			keyObject,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short maxStackDepth ()
	{
		return descriptor().o_MaxStackDepth(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject message ()
	{
		return descriptor().o_Message(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void message (
		final AvailObject value)
	{
		descriptor().o_Message(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject messageParts ()
	{
		return descriptor().o_MessageParts(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void messageParts (
		final AvailObject value)
	{
		descriptor().o_MessageParts(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject methods ()
	{
		return descriptor().o_Methods(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void methods (
		final AvailObject value)
	{
		descriptor().o_Methods(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject minusCanDestroy (
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return descriptor().o_MinusCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void moveToHead ()
	{
		descriptor().o_MoveToHead(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject multiplyByInfinityCanDestroy (
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		return descriptor().o_MultiplyByInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject multiplyByIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor().o_MultiplyByIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject myObjectMeta ()
	{
		return descriptor().o_MyObjectMeta(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void myObjectMeta (
		final AvailObject value)
	{
		descriptor().o_MyObjectMeta(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject myObjectType ()
	{
		return descriptor().o_MyObjectType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void myObjectType (
		final AvailObject value)
	{
		descriptor().o_MyObjectType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject myRestrictions ()
	{
		return descriptor().o_MyRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void myRestrictions (
		final AvailObject value)
	{
		descriptor().o_MyRestrictions(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject myType ()
	{
		return descriptor().o_MyType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void myType (
		final AvailObject value)
	{
		descriptor().o_MyType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject name ()
	{
		return descriptor().o_Name(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void name (
		final AvailObject value)
	{
		descriptor().o_Name(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject names ()
	{
		return descriptor().o_Names(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void names (
		final AvailObject value)
	{
		descriptor().o_Names(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean nameVisible (
		final AvailObject trueName)
	{
		return descriptor().o_NameVisible(this, trueName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void necessaryImplementationSetChanged (
		final AvailObject anImplementationSet)
	{
		descriptor().o_NecessaryImplementationSetChanged(this, anImplementationSet);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject newNames ()
	{
		return descriptor().o_NewNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void newNames (
		final AvailObject value)
	{
		descriptor().o_NewNames(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject next ()
	{
		return descriptor().o_Next(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void next (
		final AvailObject nextChunk)
	{
		descriptor().o_Next(this, nextChunk);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int nextIndex ()
	{
		return descriptor().o_NextIndex(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void nextIndex (
		final int value)
	{
		descriptor().o_NextIndex(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short numArgs ()
	{
		return descriptor().o_NumArgs(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short numArgsAndLocalsAndStack ()
	{
		return descriptor().o_NumArgsAndLocalsAndStack(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numberOfZones ()
	{
		return descriptor().o_NumberOfZones(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numBlanks ()
	{
		return descriptor().o_NumBlanks(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void numBlanks (
		final int value)
	{
		descriptor().o_NumBlanks(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numDoubles ()
	{
		return descriptor().o_NumDoubles(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void numFloats (
		final int value)
	{
		descriptor().o_NumFloats(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numIntegers ()
	{
		return descriptor().o_NumIntegers(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void numIntegers (
		final int value)
	{
		descriptor().o_NumIntegers(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short numLiterals ()
	{
		return descriptor().o_NumLiterals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short numLocals ()
	{
		return descriptor().o_NumLocals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numLocalsOrArgsOrStack ()
	{
		return descriptor().o_NumLocalsOrArgsOrStack(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numObjects ()
	{
		return descriptor().o_NumObjects(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void numObjects (
		final int value)
	{
		descriptor().o_NumObjects(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short numOuters ()
	{
		return descriptor().o_NumOuters(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numOuterVars ()
	{
		return descriptor().o_NumOuterVars(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject nybbles ()
	{
		return descriptor().o_Nybbles(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void nybbles (
		final AvailObject value)
	{
		descriptor().o_Nybbles(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean optionallyNilOuterVar (
		final int index)
	{
		return descriptor().o_OptionallyNilOuterVar(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject outerTypeAt (
		final int index)
	{
		return descriptor().o_OuterTypeAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void outerTypesLocalTypes (
		final AvailObject tupleOfOuterTypes,
		final AvailObject tupleOfLocalContainerTypes)
	{
		descriptor().o_OuterTypesLocalTypes(
			this,
			tupleOfOuterTypes,
			tupleOfLocalContainerTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject outerVarAt (
		final int index)
	{
		return descriptor().o_OuterVarAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void outerVarAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().o_OuterVarAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject pad1 ()
	{
		return descriptor().o_Pad1(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject pad2 ()
	{
		return descriptor().o_Pad2(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void pad1 (
		final AvailObject value)
	{
		descriptor().o_Pad1(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void pad2 (
		final AvailObject value)
	{
		descriptor().o_Pad2(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject parent ()
	{
		return descriptor().o_Parent(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void parent (
		final AvailObject value)
	{
		descriptor().o_Parent(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int pc ()
	{
		return descriptor().o_Pc(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void pc (
		final int value)
	{
		descriptor().o_Pc(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject plusCanDestroy (
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return descriptor().o_PlusCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int populateTupleStartingAt (
		final AvailObject mutableTuple,
		final int startingIndex)
	{
		return descriptor().o_PopulateTupleStartingAt(
			this,
			mutableTuple,
			startingIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void postFault ()
	{
		descriptor().o_PostFault(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject previous ()
	{
		return descriptor().o_Previous(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void previous (
		final AvailObject previousChunk)
	{
		descriptor().o_Previous(this, previousChunk);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int previousIndex ()
	{
		return descriptor().o_PreviousIndex(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void previousIndex (
		final int value)
	{
		descriptor().o_PreviousIndex(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short primitiveNumber ()
	{
		return descriptor().o_PrimitiveNumber(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int priority ()
	{
		return descriptor().o_Priority(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void priority (
		final int value)
	{
		descriptor().o_Priority(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateAddElement (
		final AvailObject element)
	{
		return descriptor().o_PrivateAddElement(this, element);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateExcludeElement (
		final AvailObject element)
	{
		return descriptor().o_PrivateExcludeElement(this, element);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateExcludeElementKnownIndex (
		final AvailObject element,
		final int knownIndex)
	{
		return descriptor().o_PrivateExcludeElementKnownIndex(
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
		return descriptor().o_PrivateExcludeKey(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateMapAtPut (
		final AvailObject keyObject,
		final AvailObject valueObject)
	{
		return descriptor().o_PrivateMapAtPut(
			this,
			keyObject,
			valueObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateNames ()
	{
		return descriptor().o_PrivateNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void privateNames (
		final AvailObject value)
	{
		descriptor().o_PrivateNames(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateTestingTree ()
	{
		return descriptor().o_PrivateTestingTree(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void privateTestingTree (
		final AvailObject value)
	{
		descriptor().o_PrivateTestingTree(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject processGlobals ()
	{
		return descriptor().o_ProcessGlobals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void processGlobals (
		final AvailObject value)
	{
		descriptor().o_ProcessGlobals(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short rawByteAt (
		final int index)
	{
		return descriptor().o_RawByteAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawByteAtPut (
		final int index,
		final short anInteger)
	{
		descriptor().o_RawByteAtPut(
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
		return descriptor().o_RawByteForCharacterAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawByteForCharacterAtPut (
		final int index,
		final short anInteger)
	{
		descriptor().o_RawByteForCharacterAtPut(
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
		return descriptor().o_RawNybbleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawNybbleAtPut (
		final int index,
		final byte aNybble)
	{
		descriptor().o_RawNybbleAtPut(
			this,
			index,
			aNybble);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int rawQuad1 ()
	{
		return descriptor().o_RawQuad1(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawQuad1 (
		final int value)
	{
		descriptor().o_RawQuad1(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int rawQuad2 ()
	{
		return descriptor().o_RawQuad2(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawQuad2 (
		final int value)
	{
		descriptor().o_RawQuad2(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int rawQuadAt (
		final int index)
	{
		return descriptor().o_RawQuadAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawQuadAtPut (
		final int index,
		final int value)
	{
		descriptor().o_RawQuadAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short rawShortForCharacterAt (
		final int index)
	{
		return descriptor().o_RawShortForCharacterAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawShortForCharacterAtPut (
		final int index,
		final short anInteger)
	{
		descriptor().o_RawShortForCharacterAtPut(
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
		return descriptor().o_RawSignedIntegerAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawSignedIntegerAtPut (
		final int index,
		final int value)
	{
		descriptor().o_RawSignedIntegerAtPut(
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
		return descriptor().o_RawUnsignedIntegerAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rawUnsignedIntegerAtPut (
		final int index,
		final int value)
	{
		descriptor().o_RawUnsignedIntegerAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void readBarrierFault ()
	{
		descriptor().o_ReadBarrierFault(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void releaseVariableOrMakeContentsImmutable ()
	{
		descriptor().o_ReleaseVariableOrMakeContentsImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeDependentChunkId (
		final int aChunkIndex)
	{
		descriptor().o_RemoveDependentChunkId(this, aChunkIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeFrom (
		final Interpreter anInterpreter)
	{
		descriptor().o_RemoveFrom(this, anInterpreter);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeFromQueue ()
	{
		descriptor().o_RemoveFromQueue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeImplementation (
		final AvailObject implementation)
	{
		descriptor().o_RemoveImplementation(this, implementation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean removeBundle (
		final AvailObject bundle)
	{
		return descriptor().o_RemoveBundle(
			this,
			bundle);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeRestrictions ()
	{
		descriptor().o_RemoveRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeRestrictions (
		final AvailObject obsoleteRestrictions)
	{
		descriptor().o_RemoveRestrictions(this, obsoleteRestrictions);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject requiresBlock ()
	{
		return descriptor().o_RequiresBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void requiresBlock (
		final AvailObject value)
	{
		descriptor().o_RequiresBlock(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void resolvedForwardWithName (
		final AvailObject forwardImplementation,
		final AvailObject methodName)
	{
		descriptor().o_ResolvedForwardWithName(
			this,
			forwardImplementation,
			methodName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject restrictions ()
	{
		return descriptor().o_Restrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void restrictions (
		final AvailObject value)
	{
		descriptor().o_Restrictions(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject returnsBlock ()
	{
		return descriptor().o_ReturnsBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void returnsBlock (
		final AvailObject value)
	{
		descriptor().o_ReturnsBlock(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject returnType ()
	{
		return descriptor().o_ReturnType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void returnType (
		final AvailObject value)
	{
		descriptor().o_ReturnType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject rootBin ()
	{
		return descriptor().o_RootBin(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void rootBin (
		final AvailObject value)
	{
		descriptor().o_RootBin(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void scanSubobjects (
		final AvailSubobjectVisitor visitor)
	{
		descriptor().o_ScanSubobjects(this, visitor);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject secondTupleType ()
	{
		return descriptor().o_SecondTupleType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void secondTupleType (
		final AvailObject value)
	{
		descriptor().o_SecondTupleType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setIntersectionCanDestroy (
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		return descriptor().o_SetIntersectionCanDestroy(
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
		return descriptor().o_SetMinusCanDestroy(
			this,
			otherSet,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int setSize ()
	{
		return descriptor().o_SetSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void setSize (
		final int value)
	{
		descriptor().o_SetSize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void setSubtupleForZoneTo (
		final int zoneIndex,
		final AvailObject newTuple)
	{
		descriptor().o_SetSubtupleForZoneTo(
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
		return descriptor().o_SetUnionCanDestroy(
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
		descriptor().o_SetValue(this, newValue);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setWithElementCanDestroy (
		final AvailObject newElementObject,
		final boolean canDestroy)
	{
		return descriptor().o_SetWithElementCanDestroy(
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
		return descriptor().o_SetWithoutElementCanDestroy(
			this,
			elementObjectToExclude,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject signature ()
	{
		return descriptor().o_Signature(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void signature (
		final AvailObject value)
	{
		descriptor().o_Signature(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void size (
		final int value)
	{
		descriptor().o_Size(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int sizeOfZone (
		final int zone)
	{
		return descriptor().o_SizeOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject sizeRange ()
	{
		return descriptor().o_SizeRange(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void sizeRange (
		final AvailObject value)
	{
		descriptor().o_SizeRange(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject specialActions ()
	{
		return descriptor().o_SpecialActions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void specialActions (
		final AvailObject value)
	{
		descriptor().o_SpecialActions(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject stackAt (
		final int slotIndex)
	{
		return descriptor().o_StackAt(this, slotIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void stackAtPut (
		final int slotIndex,
		final AvailObject anObject)
	{
		descriptor().o_StackAtPut(
			this,
			slotIndex,
			anObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int stackp ()
	{
		return descriptor().o_Stackp(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void stackp (
		final int value)
	{
		descriptor().o_Stackp(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int start ()
	{
		return descriptor().o_Start(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void start (
		final int value)
	{
		descriptor().o_Start(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int startingChunkIndex ()
	{
		return descriptor().o_StartingChunkIndex(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void startingChunkIndex (
		final int value)
	{
		descriptor().o_StartingChunkIndex(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int startOfZone (
		final int zone)
	{
		return descriptor().o_StartOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int startSubtupleIndexInZone (
		final int zone)
	{
		return descriptor().o_StartSubtupleIndexInZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void step ()
	{
		descriptor().o_Step(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject string ()
	{
		return descriptor().o_String(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void string (
		final AvailObject value)
	{
		descriptor().o_String(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject subtractFromInfinityCanDestroy (
		final AvailObject anInfinity,
		final boolean canDestroy)
	{
		return descriptor().o_SubtractFromInfinityCanDestroy(
			this,
			anInfinity,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject subtractFromIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor().o_SubtractFromIntegerCanDestroy(
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
		return descriptor().o_SubtupleForZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject target ()
	{
		return descriptor().o_Target(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void target (
		final AvailObject value)
	{
		descriptor().o_Target(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject testingTree ()
	{
		return descriptor().o_TestingTree(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject timesCanDestroy (
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return descriptor().o_TimesCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public TokenDescriptor.TokenType tokenType ()
	{
		return descriptor().o_TokenType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void tokenType (
		final TokenDescriptor.TokenType value)
	{
		descriptor().o_TokenType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int translateToZone (
		final int tupleIndex,
		final int zoneIndex)
	{
		return descriptor().o_TranslateToZone(
			this,
			tupleIndex,
			zoneIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject traversed ()
	{
		return descriptor().o_Traversed(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void trimExcessLongs ()
	{
		descriptor().o_TrimExcessLongs(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject trueNamesForStringName (
		final AvailObject stringName)
	{
		return descriptor().o_TrueNamesForStringName(this, stringName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject truncateTo (
		final int newTupleSize)
	{
		return descriptor().o_TruncateTo(this, newTupleSize);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject tupleAt (
		final int index)
	{
		return descriptor().o_TupleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void tupleAtPut (
		final int index,
		final AvailObject aNybbleObject)
	{
		descriptor().o_TupleAtPut(
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
		return descriptor().o_TupleAtPuttingCanDestroy(
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
		return descriptor().o_TupleIntAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int tupleSize ()
	{
		return descriptor().o_TupleSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject type ()
	{
		return descriptor().o_Type(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void type (
		final AvailObject value)
	{
		descriptor().o_Type(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeAtIndex (
		final int index)
	{
		return descriptor().o_TypeAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean typeEquals (
		final AvailObject aType)
	{
		return descriptor().o_TypeEquals(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersection (
		final AvailObject another)
	{
		return descriptor().o_TypeIntersection(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfClosureType (
		final AvailObject aClosureType)
	{
		return descriptor().o_TypeIntersectionOfClosureType(this, aClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfClosureTypeCanDestroy (
		final AvailObject aClosureType,
		final boolean canDestroy)
	{
		return descriptor().o_TypeIntersectionOfClosureTypeCanDestroy(
			this,
			aClosureType,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfContainerType (
		final AvailObject aContainerType)
	{
		return descriptor().o_TypeIntersectionOfContainerType(this, aContainerType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfContinuationType (
		final AvailObject aContinuationType)
	{
		return descriptor().o_TypeIntersectionOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfCyclicType (
		final AvailObject aCyclicType)
	{
		return descriptor().o_TypeIntersectionOfCyclicType(this, aCyclicType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfGeneralizedClosureType (
		final AvailObject aGeneralizedClosureType)
	{
		return descriptor().o_TypeIntersectionOfGeneralizedClosureType(this, aGeneralizedClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfGeneralizedClosureTypeCanDestroy (
		final AvailObject aGeneralizedClosureType,
		final boolean canDestroy)
	{
		return descriptor().o_TypeIntersectionOfGeneralizedClosureTypeCanDestroy(
			this,
			aGeneralizedClosureType,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor().o_TypeIntersectionOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfMapType (
		final AvailObject aMapType)
	{
		return descriptor().o_TypeIntersectionOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfMeta (
		final AvailObject someMeta)
	{
		return descriptor().o_TypeIntersectionOfMeta(this, someMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfObjectMeta (
		final AvailObject anObjectMeta)
	{
		return descriptor().o_TypeIntersectionOfObjectMeta(this, anObjectMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfObjectMetaMeta (
		final AvailObject anObjectMetaMeta)
	{
		return descriptor().o_TypeIntersectionOfObjectMetaMeta(this, anObjectMetaMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor().o_TypeIntersectionOfObjectType(this, anObjectType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfSetType (
		final AvailObject aSetType)
	{
		return descriptor().o_TypeIntersectionOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor().o_TypeIntersectionOfTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeTuple ()
	{
		return descriptor().o_TypeTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void typeTuple (
		final AvailObject value)
	{
		descriptor().o_TypeTuple(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnion (
		final AvailObject another)
	{
		return descriptor().o_TypeUnion(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfClosureType (
		final AvailObject aClosureType)
	{
		return descriptor().o_TypeUnionOfClosureType(this, aClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfClosureTypeCanDestroy (
		final AvailObject aClosureType,
		final boolean canDestroy)
	{
		return descriptor().o_TypeUnionOfClosureTypeCanDestroy(
			this,
			aClosureType,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfContainerType (
		final AvailObject aContainerType)
	{
		return descriptor().o_TypeUnionOfContainerType(this, aContainerType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfContinuationType (
		final AvailObject aContinuationType)
	{
		return descriptor().o_TypeUnionOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfCyclicType (
		final AvailObject aCyclicType)
	{
		return descriptor().o_TypeUnionOfCyclicType(this, aCyclicType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfGeneralizedClosureType (
		final AvailObject aGeneralizedClosureType)
	{
		return descriptor().o_TypeUnionOfGeneralizedClosureType(this, aGeneralizedClosureType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor().o_TypeUnionOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfMapType (
		final AvailObject aMapType)
	{
		return descriptor().o_TypeUnionOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfObjectMeta (
		final AvailObject anObjectMeta)
	{
		return descriptor().o_TypeUnionOfObjectMeta(this, anObjectMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfObjectMetaMeta (
		final AvailObject anObjectMetaMeta)
	{
		return descriptor().o_TypeUnionOfObjectMetaMeta(this, anObjectMetaMeta);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor().o_TypeUnionOfObjectType(this, anObjectType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfSetType (
		final AvailObject aSetType)
	{
		return descriptor().o_TypeUnionOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor().o_TypeUnionOfTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject unclassified ()
	{
		return descriptor().o_Unclassified(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void unclassified (
		final AvailObject value)
	{
		descriptor().o_Unclassified(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject unionOfTypesAtThrough (
		final int startIndex,
		final int endIndex)
	{
		return descriptor().o_UnionOfTypesAtThrough(
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
		return descriptor().o_UntranslatedDataAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void untranslatedDataAtPut (
		final int index,
		final int value)
	{
		descriptor().o_UntranslatedDataAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject upperBound ()
	{
		return descriptor().o_UpperBound(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void upperBound (
		final AvailObject value)
	{
		descriptor().o_UpperBound(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean upperInclusive ()
	{
		return descriptor().o_UpperInclusive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject validateArgumentTypesInterpreterIfFail (
		final List<AvailObject> argTypes,
		final Interpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock)
	{
		return descriptor().o_ValidateArgumentTypesInterpreterIfFail(
			this,
			argTypes,
			anAvailInterpreter,
			failBlock);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int validity ()
	{
		return descriptor().o_Validity(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void validity (
		final int value)
	{
		descriptor().o_Validity(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject value ()
	{
		return descriptor().o_Value(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void value (
		final AvailObject value)
	{
		descriptor().o_Value(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject valueAtIndex (
		final int index)
	{
		return descriptor().o_ValueAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void valueAtIndexPut (
		final int index,
		final AvailObject valueObject)
	{
		descriptor().o_ValueAtIndexPut(
			this,
			index,
			valueObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject valuesAsTuple ()
	{
		return descriptor().o_ValuesAsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject valueType ()
	{
		return descriptor().o_ValueType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void valueType (
		final AvailObject value)
	{
		descriptor().o_ValueType(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject variableBindings ()
	{
		return descriptor().o_VariableBindings(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void variableBindings (
		final AvailObject value)
	{
		descriptor().o_VariableBindings(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject vectors ()
	{
		return descriptor().o_Vectors(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void vectors (
		final AvailObject value)
	{
		descriptor().o_Vectors(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void verify ()
	{
		descriptor().o_Verify(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject visibleNames ()
	{
		return descriptor().o_VisibleNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void visibleNames (
		final AvailObject value)
	{
		descriptor().o_VisibleNames(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int whichOne ()
	{
		return descriptor().o_WhichOne(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void whichOne (
		final int value)
	{
		descriptor().o_WhichOne(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject wordcodes ()
	{
		return descriptor().o_Wordcodes(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void wordcodes (
		final AvailObject value)
	{
		descriptor().o_Wordcodes(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int zoneForIndex (
		final int index)
	{
		return descriptor().o_ZoneForIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void parsingInstructions (
		final AvailObject instructionsTuple)
	{
		descriptor().o_ParsingInstructions(this, instructionsTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject parsingInstructions ()
	{
		return descriptor().o_ParsingInstructions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void mapDo (
		final Continuation2<AvailObject, AvailObject> continuation)
	{
		descriptor().o_mapDo(this,continuation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void expression (final AvailObject expression)
	{
		descriptor().o_Expression(this, expression);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject expression ()
	{
		return descriptor().o_Expression(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void variable (final AvailObject variable)
	{
		descriptor().o_Variable(this, variable);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject variable ()
	{
		return descriptor().o_Variable(this);
	}


	/**
	 * @param argumentsTuple
	 */
	public void argumentsTuple (final AvailObject argumentsTuple)
	{
		descriptor().o_ArgumentsTuple(this, argumentsTuple);
	}


	/**
	 * @return
	 */
	public AvailObject argumentsTuple ()
	{
		return descriptor().o_ArgumentsTuple(this);
	}


	/**
	 * @param statementsTuple
	 */
	public void statementsTuple (final AvailObject statementsTuple)
	{
		descriptor().o_StatementsTuple(this, statementsTuple);
	}


	/**
	 * @return
	 */
	public AvailObject statementsTuple ()
	{
		return descriptor().o_StatementsTuple(this);
	}


	/**
	 * @param resultType
	 */
	public void resultType (final AvailObject resultType)
	{
		descriptor().o_ResultType(this, resultType);
	}


	/**
	 * @return
	 */
	public AvailObject resultType ()
	{
		return descriptor().o_ResultType(this);
	}


	/**
	 * @param neededVariables
	 */
	public void neededVariables (final AvailObject neededVariables)
	{
		descriptor().o_NeededVariables(this, neededVariables);
	}


	/**
	 * @return
	 */
	public AvailObject neededVariables ()
	{
		return descriptor().o_NeededVariables(this);
	}


	/**
	 * @param primitive
	 */
	public void primitive (final int primitive)
	{
		descriptor().o_Primitive(this, primitive);
	}


	/**
	 * @return
	 */
	public int primitive ()
	{
		return descriptor().o_Primitive(this);
	}


	/**
	 * @param declaredType
	 */
	public void declaredType (final AvailObject declaredType)
	{
		descriptor().o_DeclaredType(this, declaredType);
	}


	/**
	 * @return
	 */
	public AvailObject declaredType ()
	{
		return descriptor().o_DeclaredType(this);
	}


	/**
	 * @param declarationKind
	 */
	public void declarationKind (final DeclarationKind declarationKind)
	{
		descriptor().o_DeclarationKind(this, declarationKind);
	}


	/**
	 * @return
	 */
	public DeclarationKind declarationKind ()
	{
		return descriptor().o_DeclarationKind(this);
	}


	/**
	 * @return
	 */
	public AvailObject initializationExpression ()
	{
		return descriptor().o_InitializationExpression(this);
	}


	/**
	 * @param initializationExpression
	 */
	public void initializationExpression (final AvailObject initializationExpression)
	{
		descriptor().o_InitializationExpression(this, initializationExpression);
	}


	/**
	 * @return
	 */
	public AvailObject literalObject ()
	{
		return descriptor().o_LiteralObject(this);
	}


	/**
	 * @param literalObject
	 */
	public void literalObject (final AvailObject literalObject)
	{
		descriptor().o_LiteralObject(this, literalObject);
	}


	/**
	 * @return
	 */
	public AvailObject token ()
	{
		return descriptor().o_Token(this);
	}


	/**
	 * @param token
	 */
	public void token (final AvailObject token)
	{
		descriptor().o_Token(this, token);
	}


	/**
	 * @return
	 */
	public AvailObject markerValue ()
	{
		return descriptor().o_MarkerValue(this);
	}


	/**
	 * @param markerValue
	 */
	public void markerValue (final AvailObject markerValue)
	{
		descriptor().o_MarkerValue(this, markerValue);
	}


	/**
	 * @return
	 */
	public AvailObject arguments ()
	{
		return descriptor().o_Arguments(this);
	}


	/**
	 * @param arguments
	 */
	public void arguments (final AvailObject arguments)
	{
		descriptor().o_Arguments(this, arguments);
	}


	/**
	 * @return
	 */
	public AvailObject implementationSet ()
	{
		return descriptor().o_ImplementationSet(this);
	}


	/**
	 * @param implementationSet
	 */
	public void implementationSet (final AvailObject implementationSet)
	{
		descriptor().o_ImplementationSet(this, implementationSet);
	}


	/**
	 * @return
	 */
	public AvailObject superCastType ()
	{
		return descriptor().o_SuperCastType(this);
	}


	/**
	 * @param superCastType
	 */
	public void superCastType (final AvailObject superCastType)
	{
		descriptor().o_SuperCastType(this, superCastType);
	}


	/**
	 * @return
	 */
	public AvailObject expressionsTuple ()
	{
		return descriptor().o_ExpressionsTuple(this);
	}


	/**
	 * @param expressionsTuple
	 */
	public void expressionsTuple (final AvailObject expressionsTuple)
	{
		descriptor().o_ExpressionsTuple(this, expressionsTuple);
	}


	/**
	 * @return
	 */
	public AvailObject tupleType ()
	{
		return descriptor().o_TupleType(this);
	}


	/**
	 * @param tupleType
	 */
	public void tupleType (final AvailObject tupleType)
	{
		descriptor().o_TupleType(this, tupleType);
	}


	/**
	 * @return
	 */
	public AvailObject declaration ()
	{
		return descriptor().o_Declaration(this);
	}


	/**
	 * @param declaration
	 */
	public void declaration (final AvailObject declaration)
	{
		descriptor().o_Declaration(this, declaration);
	}


	/**
	 * @return
	 */
	public AvailObject expressionType ()
	{
		return descriptor().o_ExpressionType(this);
	}


	/**
	 * @param codeGenerator
	 */
	public void emitEffectOn (final AvailCodeGenerator codeGenerator)
	{
		descriptor().o_EmitEffectOn(this, codeGenerator);
	}


	/**
	 * @param codeGenerator
	 */
	public void emitValueOn (final AvailCodeGenerator codeGenerator)
	{
		descriptor().o_EmitValueOn(this, codeGenerator);
	}


	/**
	 * @param object
	 * @param aBlock
	 */
	public void childrenMap (
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		descriptor().o_ChildrenMap(this, aBlock);
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
		descriptor().o_ValidateLocally(
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
		return descriptor().o_Generate(this, codeGenerator);
	}


	/**
	 * @param newParseNode
	 * @return
	 */
	public AvailObject copyWith (final AvailObject newParseNode)
	{
		return descriptor().o_CopyWith(this, newParseNode);
	}


	/**
	 * @param isLastUse
	 */
	public void isLastUse (final boolean isLastUse)
	{
		descriptor().o_IsLastUse(this, isLastUse);
	}


	/**
	 * @return
	 */
	public boolean isLastUse ()
	{
		return descriptor().o_IsLastUse(this);
	}


	/**
	 * @return
	 */
	public boolean isMacro ()
	{
		return descriptor().o_IsMacro(this);
	}


	/**
	 * @param value
	 */
	public void macros (final AvailObject value)
	{
		descriptor().o_Macros(this, value);
	}


	/**
	 * @return
	 */
	public AvailObject macros ()
	{
		return descriptor().o_Macros(this);
	}


	/**
	 * @return
	 */
	public AvailObject copyMutableParseNode ()
	{
		return descriptor().o_CopyMutableParseNode(this);
	}


	/**
	 * @param value
	 */
	public void macroName (final AvailObject value)
	{
		descriptor().o_MacroName(this, value);
	}


	/**
	 * @return
	 */
	public AvailObject macroName ()
	{
		return descriptor().o_MacroName(this);
	}


	/**
	 * @param value
	 */
	public void outputParseNode (final AvailObject value)
	{
		descriptor().o_OutputParseNode(this, value);
	}


	/**
	 * @return
	 */
	public AvailObject outputParseNode ()
	{
		return descriptor().o_OutputParseNode(this);
	}


	/**
	 * @return
	 */
	public AvailObject apparentSendName ()
	{
		return descriptor().o_ApparentSendName(this);
	}
}