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
import com.avail.compiler.Continuation2;
import com.avail.compiler.Generator;
import com.avail.interpreter.AvailInterpreter;
import com.avail.newcompiler.TokenDescriptor;
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
	@SuppressWarnings("unchecked")
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
				break;
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

	
	
	public abstract boolean o_AcceptsArgTypesFromClosureType (
		final AvailObject object,
		final AvailObject closureType);

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	public abstract boolean o_AcceptsArgumentsFromContinuationStackp (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp);

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	public abstract boolean o_AcceptsArgumentTypesFromContinuationStackp (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract boolean o_AcceptsArrayOfArgTypes (
		final AvailObject object,
		final List<AvailObject> argTypes);

	/**
	 * @param object
	 * @param argValues
	 * @return
	 */
	public abstract boolean o_AcceptsArrayOfArgValues (
		final AvailObject object,
		final List<AvailObject> argValues);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final AvailObject argTypes);

	/**
	 * @param object
	 * @param arguments
	 * @return
	 */
	public abstract boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final AvailObject arguments);

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	public abstract void o_AddDependentChunkId (
		final AvailObject object,
		final int aChunkIndex);

	/**
	 * @param object
	 * @param implementation
	 */
	public abstract void o_AddImplementation (
		final AvailObject object,
		final AvailObject implementation);

	/**
	 * @param object
	 * @param restrictions
	 */
	public abstract void o_AddRestrictions (
		final AvailObject object,
		final AvailObject restrictions);

	/**
	 * @param object
	 * @param anInfinity
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_AddToInfinityCanDestroy (
		final AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_AddToIntegerCanDestroy (
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
	public abstract void o_ArgsLocalsStackOutersPrimitive (
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
	public abstract AvailObject o_ArgTypeAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_ArgTypeAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	public abstract void o_AtAddMessageRestrictions (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject illegalArgMsgs);

	/**
	 * @param object
	 * @param methodName
	 * @param implementation
	 */
	public abstract void o_AtAddMethodImplementation (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject implementation);

	/**
	 * @param object
	 * @param message
	 * @param bundle
	 */
	public abstract void o_AtMessageAddBundle (
		final AvailObject object,
		final AvailObject message,
		final AvailObject bundle);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	public abstract void o_AtNameAdd (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	public abstract void o_AtNewNamePut (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	public abstract void o_AtPrivateNameAdd (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_BinElementAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_BinElementAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_BinHash (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_BinSize (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_BinUnionType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_BitVector (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_BodyBlock (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param bb
	 * @param rqb
	 * @param rtb
	 */
	public abstract void o_BodyBlockRequiresBlockReturnsBlock (
		final AvailObject object,
		final AvailObject bb,
		final AvailObject rqb,
		final AvailObject rtb);

	/**
	 * @param object
	 * @param signature
	 */
	public abstract void o_BodySignature (
		final AvailObject object,
		final AvailObject signature);

	/**
	 * @param object
	 * @param bs
	 * @param rqb
	 * @param rtb
	 */
	public abstract void o_BodySignatureRequiresBlockReturnsBlock (
		final AvailObject object,
		final AvailObject bs,
		final AvailObject rqb,
		final AvailObject rtb);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_BreakpointBlock (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param bundleTree
	 */
	public abstract void o_BuildFilteredBundleTreeFrom (
		final AvailObject object,
		final AvailObject bundleTree);

	/**
	 * @param object
	 * @param message
	 * @param parts
	 * @param instructions
	 * @return
	 */
	public abstract AvailObject o_BundleAtMessageParts (
		final AvailObject object,
		final AvailObject message,
		final AvailObject parts,
		final AvailObject instructions);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Caller (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Closure (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ClosureType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Code (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_CodePoint (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param anotherObject
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean o_CompareFromToWithStartingAt (
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
	public abstract boolean o_CompareFromToWithAnyTupleStartingAt (
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
	public abstract boolean o_CompareFromToWithByteStringStartingAt (
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
	public abstract boolean o_CompareFromToWithByteTupleStartingAt (
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
	public abstract boolean o_CompareFromToWithNybbleTupleStartingAt (
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
	public abstract boolean o_CompareFromToWithObjectTupleStartingAt (
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
	public abstract boolean o_CompareFromToWithTwoByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTwoByteString,
		final int startIndex2);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Complete (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param start
	 * @param end
	 * @return
	 */
	public abstract int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end);

	/**
	 * @param object
	 * @param argTypes
	 * @param anAvailInterpreter
	 * @return
	 */
	public abstract AvailObject o_ComputeReturnTypeFromArgumentTypesInterpreter (
		final AvailObject object,
		final List<AvailObject> argTypes,
		final AvailInterpreter anAvailInterpreter);

	/**
	 * @param object
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_ConcatenateTuplesCanDestroy (
		final AvailObject object,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ConstantBindings (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ContentType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ContingentImpSets (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Continuation (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param filteredBundleTree
	 * @param visibleNames
	 */
	public abstract void o_CopyToRestrictedTo (
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
	public abstract AvailObject o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final ArrayList<AvailObject> argTypes);

	/**
	 * @param object
	 * @param positiveTuple
	 * @param possibilities
	 * @return
	 */
	public abstract AvailObject o_CreateTestingTreeWithPositiveMatchesRemainingPossibilities (
		final AvailObject object,
		final AvailObject positiveTuple,
		final AvailObject possibilities);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_DataAtIndex (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_DataAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_DefaultType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_DependentChunks (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ParsingPc (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param aNumber
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_DivideCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInfinity
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_DivideIntoIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_ElementAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_ElementAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int o_EndOfZone (final AvailObject object, final int zone);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int o_EndSubtupleIndexInZone (
		final AvailObject object,
		final int zone);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ExecutionMode (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ExecutionState (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract byte o_ExtractNybbleFromTupleAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_FieldMap (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_FieldTypeMap (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract List<AvailObject> o_FilterByTypes (
		final AvailObject object,
		final List<AvailObject> argTypes);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_FilteredBundleTree (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_FirstTupleType (
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
	public abstract AvailObject o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone (
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
	public abstract boolean o_GreaterThanInteger (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_GreaterThanSignedInfinity (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param elementObject
	 * @return
	 */
	public abstract boolean o_HasElement (
		final AvailObject object,
		final AvailObject elementObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Hash (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public abstract int o_HashFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_HashOrZero (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	public abstract boolean o_HasKey (
		final AvailObject object,
		final AvailObject keyObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_HiLevelTwoChunkLowOffset (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_HiNumLocalsLowNumArgs (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_HiPrimitiveLowNumArgsAndLocalsAndStack (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_HiStartingChunkIndexLowNumOuters (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract ArrayList<AvailObject> o_ImplementationsAtOrBelow (
		final AvailObject object,
		final ArrayList<AvailObject> argTypes);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ImplementationsTuple (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param messageBundle
	 * @return
	 */
	public abstract AvailObject o_IncludeBundle (
		final AvailObject object,
		final AvailObject bundleToFind);

	/**
	 * @param object
	 * @param imp
	 * @return
	 */
	public abstract boolean o_Includes (
		final AvailObject object,
		final AvailObject imp);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_InclusiveFlags (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Incomplete (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Index (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_InnerType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Instance (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_InternalHash (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_InterruptRequestFlag (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_InvocationCount (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param aBoolean
	 */
	public abstract void o_IsSaved (final AvailObject object, final boolean aBoolean);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_IsSubsetOf (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_IsSubtypeOf (
		final AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType);

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCyclicType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfCyclicType (
		final AvailObject object,
		final AvailObject aCyclicType);

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aListType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfListType (
		final AvailObject object,
		final AvailObject aListType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType);

	/**
	 * @param object
	 * @param anObjectMeta
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfObjectMeta (
		final AvailObject object,
		final AvailObject anObjectMeta);

	/**
	 * @param object
	 * @param anObjectMetaMeta
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfObjectMetaMeta (
		final AvailObject object,
		final AvailObject anObjectMetaMeta);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType);

	/**
	 * @param object
	 * @param aPrimitiveType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfPrimitiveType (
		final AvailObject object,
		final AvailObject aPrimitiveType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param aBoolean
	 */
	public abstract void o_IsValid (final AvailObject object, final boolean aBoolean);

	/**
	 * @param object
	 * @param argTypes
	 * @param interpreter
	 * @return
	 */
	public abstract boolean o_IsValidForArgumentTypesInterpreter (
		final AvailObject object,
		final List<AvailObject> argTypes,
		final AvailInterpreter interpreter);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_KeyAtIndex (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param keyObject
	 */
	public abstract void o_KeyAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject keyObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_KeyType (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_LessOrEqual (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_LessThan (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param index
	 * @param offset
	 */
	public abstract void o_LevelTwoChunkIndexOffset (
		final AvailObject object,
		final int index,
		final int offset);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Literal (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_LiteralAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_LiteralAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_LocalOrArgOrStackAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_LocalOrArgOrStackAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_LocalTypeAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param argumentTypeArray
	 * @return
	 */
	public abstract AvailObject o_LookupByTypesFromArray (
		final AvailObject object,
		final List<AvailObject> argumentTypeArray);

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	public abstract AvailObject o_LookupByTypesFromContinuationStackp (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp);

	/**
	 * @param object
	 * @param argumentTypeTuple
	 * @return
	 */
	public abstract AvailObject o_LookupByTypesFromTuple (
		final AvailObject object,
		final AvailObject argumentTypeTuple);

	/**
	 * @param object
	 * @param argumentArray
	 * @return
	 */
	public abstract AvailObject o_LookupByValuesFromArray (
		final AvailObject object,
		final List<AvailObject> argumentArray);

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	public abstract AvailObject o_LookupByValuesFromContinuationStackp (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp);

	/**
	 * @param object
	 * @param argumentTuple
	 * @return
	 */
	public abstract AvailObject o_LookupByValuesFromTuple (
		final AvailObject object,
		final AvailObject argumentTuple);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_LowerBound (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param lowInc
	 * @param highInc
	 */
	public abstract void o_LowerInclusiveUpperInclusive (
		final AvailObject object,
		final boolean lowInc,
		final boolean highInc);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	public abstract AvailObject o_MapAt (
		final AvailObject object,
		final AvailObject keyObject);

	/**
	 * @param object
	 * @param keyObject
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_MapAtPuttingCanDestroy (
		final AvailObject object,
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_MapSize (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param keyObject
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_MapWithoutKeyCanDestroy (
		final AvailObject object,
		final AvailObject keyObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Message (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_MessageParts (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Methods (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param aNumber
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_MinusCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInfinity
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_MyObjectMeta (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_MyObjectType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_MyRestrictions (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_MyType (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Name (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Names (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param trueName
	 * @return
	 */
	public abstract boolean o_NameVisible (
		final AvailObject object,
		final AvailObject trueName);

	/**
	 * @param object
	 * @param anImplementationSet
	 */
	public abstract void o_NecessaryImplementationSetChanged (
		final AvailObject object,
		final AvailObject anImplementationSet);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_NewNames (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param nextChunk
	 */
	public abstract void o_Next (
		final AvailObject object,
		final AvailObject nextChunk);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_NextIndex (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_NumBlanks (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_NumFloats (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_NumIntegers (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_NumObjects (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Nybbles (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract boolean o_OptionallyNilOuterVar (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_OuterTypeAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param tupleOfOuterTypes
	 * @param tupleOfLocalContainerTypes
	 */
	public abstract void o_OuterTypesLocalTypes (
		final AvailObject object,
		final AvailObject tupleOfOuterTypes,
		final AvailObject tupleOfLocalContainerTypes);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_OuterVarAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_OuterVarAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Pad1 (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Pad2 (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Parent (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Pc (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param aNumber
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_PlusCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param previousChunk
	 */
	public abstract void o_Previous (
		final AvailObject object,
		final AvailObject previousChunk);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_PreviousIndex (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Priority (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	public abstract AvailObject o_PrivateAddElement (
		final AvailObject object,
		final AvailObject element);

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	public abstract AvailObject o_PrivateExcludeElement (
		final AvailObject object,
		final AvailObject element);

	/**
	 * @param object
	 * @param element
	 * @param knownIndex
	 * @return
	 */
	public abstract AvailObject o_PrivateExcludeElementKnownIndex (
		final AvailObject object,
		final AvailObject element,
		final int knownIndex);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	public abstract AvailObject o_PrivateExcludeKey (
		final AvailObject object,
		final AvailObject keyObject);

	/**
	 * @param object
	 * @param keyObject
	 * @param valueObject
	 * @return
	 */
	public abstract AvailObject o_PrivateMapAtPut (
		final AvailObject object,
		final AvailObject keyObject,
		final AvailObject valueObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_PrivateNames (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_PrivateTestingTree (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ProcessGlobals (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract short o_RawByteAt (final AvailObject object, final int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	public abstract void o_RawByteAtPut (
		final AvailObject object,
		final int index,
		final short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract short o_RawByteForCharacterAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	public abstract void o_RawByteForCharacterAtPut (
		final AvailObject object,
		final int index,
		final short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract byte o_RawNybbleAt (final AvailObject object, final int index);

	/**
	 * @param object
	 * @param index
	 * @param aNybble
	 */
	public abstract void o_RawNybbleAtPut (
		final AvailObject object,
		final int index,
		final byte aNybble);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_RawQuad1 (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_RawQuad2 (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int o_RawQuadAt (final AvailObject object, final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_RawQuadAtPut (
		final AvailObject object,
		final int index,
		final int value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract short o_RawShortForCharacterAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	public abstract void o_RawShortForCharacterAtPut (
		final AvailObject object,
		final int index,
		final short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int o_RawSignedIntegerAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_RawSignedIntegerAtPut (
		final AvailObject object,
		final int index,
		final int value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract long o_RawUnsignedIntegerAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_RawUnsignedIntegerAtPut (
		final AvailObject object,
		final int index,
		final int value);

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	public abstract void o_RemoveDependentChunkId (
		final AvailObject object,
		final int aChunkIndex);

	/**
	 * @param object
	 * @param anInterpreter
	 */
	public abstract void o_RemoveFrom (
		final AvailObject object,
		final AvailInterpreter anInterpreter);

	/**
	 * @param object
	 * @param implementation
	 */
	public abstract void o_RemoveImplementation (
		final AvailObject object,
		final AvailObject implementation);

	/**
	 * @param object
	 * @param bundle
	 * @return
	 */
	public abstract boolean o_RemoveBundle (
		final AvailObject object,
		AvailObject bundle);

	/**
	 * @param object
	 * @param obsoleteRestrictions
	 */
	public abstract void o_RemoveRestrictions (
		final AvailObject object,
		final AvailObject obsoleteRestrictions);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_RequiresBlock (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param forwardImplementation
	 * @param methodName
	 */
	public abstract void o_ResolvedForwardWithName (
		final AvailObject object,
		final AvailObject forwardImplementation,
		final AvailObject methodName);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Restrictions (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ReturnsBlock (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ReturnType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_RootBin (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_SecondTupleType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_SetIntersectionCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_SetMinusCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_SetSize (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param zoneIndex
	 * @param newTuple
	 */
	public abstract void o_SetSubtupleForZoneTo (
		final AvailObject object,
		final int zoneIndex,
		final AvailObject newTuple);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_SetUnionCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param newValue
	 */
	public abstract void o_SetValue (
		final AvailObject object,
		final AvailObject newValue);

	/**
	 * @param object
	 * @param newElementObject
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_SetWithElementCanDestroy (
		final AvailObject object,
		final AvailObject newElementObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param elementObjectToExclude
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_SetWithoutElementCanDestroy (
		final AvailObject object,
		final AvailObject elementObjectToExclude,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Signature (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Size (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int o_SizeOfZone (final AvailObject object, final int zone);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_SizeRange (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_SpecialActions (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param slotIndex
	 * @return
	 */
	public abstract AvailObject o_StackAt (
		final AvailObject object,
		final int slotIndex);

	/**
	 * @param object
	 * @param slotIndex
	 * @param anObject
	 */
	public abstract void o_StackAtPut (
		final AvailObject object,
		final int slotIndex,
		final AvailObject anObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Stackp (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Start (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_StartingChunkIndex (
		final AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int o_StartOfZone (final AvailObject object, final int zone);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int o_StartSubtupleIndexInZone (
		final AvailObject object,
		final int zone);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_String (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param anInfinity
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final AvailObject anInfinity,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract AvailObject o_SubtupleForZone (
		final AvailObject object,
		final int zone);

	/**
	 * @param object
	 * @param aNumber
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_TimesCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 * @return
	 */
	public abstract void o_TokenType (
		final AvailObject object,
		final TokenDescriptor.TokenType value);

	/**
	 * @param object
	 * @param tupleIndex
	 * @param zoneIndex
	 * @return
	 */
	public abstract int o_TranslateToZone (
		final AvailObject object,
		final int tupleIndex,
		final int zoneIndex);

	/**
	 * @param object
	 * @param stringName
	 * @return
	 */
	public abstract AvailObject o_TrueNamesForStringName (
		final AvailObject object,
		final AvailObject stringName);

	/**
	 * @param object
	 * @param newTupleSize
	 * @return
	 */
	public abstract AvailObject o_TruncateTo (
		final AvailObject object,
		final int newTupleSize);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Tuple (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_TupleAt (final AvailObject object, final int index);

	/**
	 * @param object
	 * @param index
	 * @param aNybbleObject
	 */
	public abstract void o_TupleAtPut (
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
	public abstract AvailObject o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int o_TupleIntAt (final AvailObject object, final int index);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_TupleType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Type (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_TypeAtIndex (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract AvailObject o_TypeIntersection (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType);

	/**
	 * @param object
	 * @param aClosureType
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfClosureTypeCanDestroy (
		final AvailObject object,
		final AvailObject aClosureType,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCyclicType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfCyclicType (
		final AvailObject object,
		final AvailObject aCyclicType);

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType);

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfGeneralizedClosureTypeCanDestroy (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aListType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfListType (
		final AvailObject object,
		final AvailObject aListType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfMapType (
		final AvailObject object,
		final AvailObject aMapType);

	/**
	 * @param object
	 * @param someMeta
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfMeta (
		final AvailObject object,
		final AvailObject someMeta);

	/**
	 * @param object
	 * @param anObjectMeta
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfObjectMeta (
		final AvailObject object,
		final AvailObject anObjectMeta);

	/**
	 * @param object
	 * @param anObjectMetaMeta
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfObjectMetaMeta (
		final AvailObject object,
		final AvailObject anObjectMetaMeta);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfSetType (
		final AvailObject object,
		final AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_TypeTuple (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract AvailObject o_TypeUnion (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType);

	/**
	 * @param object
	 * @param aClosureType
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfClosureTypeCanDestroy (
		final AvailObject object,
		final AvailObject aClosureType,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCyclicType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfCyclicType (
		final AvailObject object,
		final AvailObject aCyclicType);

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aListType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfListType (
		final AvailObject object,
		final AvailObject aListType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfMapType (
		final AvailObject object,
		final AvailObject aMapType);

	/**
	 * @param object
	 * @param anObjectMeta
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfObjectMeta (
		final AvailObject object,
		final AvailObject anObjectMeta);

	/**
	 * @param object
	 * @param anObjectMetaMeta
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfObjectMetaMeta (
		final AvailObject object,
		final AvailObject anObjectMetaMeta);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfSetType (
		final AvailObject object,
		final AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Unclassified (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public abstract AvailObject o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int o_UntranslatedDataAt (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_UntranslatedDataAtPut (
		final AvailObject object,
		final int index,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_UpperBound (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param argTypes
	 * @param anAvailInterpreter
	 * @param failBlock
	 * @return
	 */
	public abstract AvailObject o_ValidateArgumentTypesInterpreterIfFail (
		final AvailObject object,
		final List<AvailObject> argTypes,
		final AvailInterpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Validity (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Value (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_ValueAtIndex (
		final AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param valueObject
	 */
	public abstract void o_ValueAtIndexPut (
		final AvailObject object,
		final int index,
		final AvailObject valueObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ValueType (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_VariableBindings (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Vectors (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_VisibleNames (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_WhichOne (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Wordcodes (
		final AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int o_ZoneForIndex (final AvailObject object, final int index);

	/**
	 * @param object
	 * @return
	 */
	public abstract String o_AsNativeString (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_AsObject (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_AsSet (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_AsTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_BecomeExactType (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_BecomeRealTupleType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_BitsPerEntry (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_BitVector (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_BodyBlock (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_BodySignature (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_BreakpointBlock (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Caller (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Capacity (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_CleanUpAfterCompile (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_ClearModule (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_ClearValue (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Closure (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ClosureType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Code (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_CodePoint (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Complete (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ConstantBindings (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ContentType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ContingentImpSets (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Continuation (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_CopyAsMutableContinuation (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_CopyAsMutableObjectTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_CopyAsMutableSpliceTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_CopyMutable (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_DefaultType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_DependentChunks (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_ParsingPc (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_DisplayTestingTree (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_EnsureMetacovariant (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_EnsureMutable (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_EvictedByGarbageCollector (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_ExecutionMode (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_ExecutionState (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Expand (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_ExtractBoolean (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short o_ExtractByte (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract double o_ExtractDouble (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract float o_ExtractFloat (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_ExtractInt (final AvailObject object);

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
	public abstract long o_ExtractLong (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract byte o_ExtractNybble (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_FieldMap (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_FieldTypeMap (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_FilteredBundleTree (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_FirstTupleType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_GetInteger (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_GetValue (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_HashOrZero (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_HasRestrictions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_HiLevelTwoChunkLowOffset (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_HiNumLocalsLowNumArgs (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_HiPrimitiveLowNumArgsAndLocalsAndStack (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_HiStartingChunkIndexLowNumOuters (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ImplementationsTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_InclusiveFlags (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Incomplete (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Index (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_InnerType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Instance (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_InternalHash (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_InterruptRequestFlag (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_InvocationCount (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsAbstract (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsFinite (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsForward (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsImplementation (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsPositive (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsSaved (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsSplice (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfTerminates (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfVoid (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsValid (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract List<AvailObject> o_KeysAsArray (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_KeysAsSet (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_KeyType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_LevelTwoChunkIndex (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_LevelTwoOffset (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Literal (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_LowerBound (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_LowerInclusive (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_MapSize (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short o_MaxStackDepth (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Message (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_MessageParts (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Methods (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_MoveToHead (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_MyObjectMeta (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_MyObjectType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_MyRestrictions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_MyType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Name (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Names (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_NewNames (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Next (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NextIndex (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short o_NumArgs (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short o_NumArgsAndLocalsAndStack (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumberOfZones (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumBlanks (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumDoubles (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumIntegers (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short o_NumLiterals (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short o_NumLocals (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumLocalsOrArgsOrStack (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumObjects (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short o_NumOuters (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumOuterVars (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Nybbles (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Pad1 (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Pad2 (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Parent (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Pc (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Previous (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_PreviousIndex (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short o_PrimitiveNumber (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Priority (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_PrivateNames (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_PrivateTestingTree (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ProcessGlobals (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_RawQuad1 (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_RawQuad2 (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_ReleaseVariableOrMakeContentsImmutable (
		final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_RemoveFromQueue (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_RemoveRestrictions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_RequiresBlock (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Restrictions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ReturnsBlock (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ReturnType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_RootBin (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_SecondTupleType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_SetSize (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Signature (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_SizeRange (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_SpecialActions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Stackp (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Start (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_StartingChunkIndex (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_Step (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_String (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_TestingTree (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract TokenDescriptor.TokenType o_TokenType (
		final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_TrimExcessLongs (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Tuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_TupleSize (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_TupleType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_TypeTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Unclassified (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_UpperBound (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_UpperInclusive (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Validity (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Value (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ValuesAsTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ValueType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_VariableBindings (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Vectors (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_Verify (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_VisibleNames (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_WhichOne (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Wordcodes (final AvailObject object);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_Equals (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	public abstract boolean o_EqualsAnyTuple (
		final AvailObject object,
		final AvailObject aTuple);

	/**
	 * @param object
	 * @param aString
	 * @return
	 */
	public abstract boolean o_EqualsByteString (
		final AvailObject object,
		final AvailObject aString);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	public abstract boolean o_EqualsByteTuple (
		final AvailObject object,
		final AvailObject aTuple);

	/**
	 * @param object
	 * @param otherCodePoint
	 * @return
	 */
	public abstract boolean o_EqualsCharacterWithCodePoint (
		final AvailObject object,
		final int otherCodePoint);

	/**
	 * @param object
	 * @param aClosure
	 * @return
	 */
	public abstract boolean o_EqualsClosure (
		final AvailObject object,
		final AvailObject aClosure);

	/**
	 * @param object
	 * @param aClosureType
	 * @return
	 */
	public abstract boolean o_EqualsClosureType (
		final AvailObject object,
		final AvailObject aClosureType);

	/**
	 * @param object
	 * @param aCompiledCode
	 * @return
	 */
	public abstract boolean o_EqualsCompiledCode (
		final AvailObject object,
		final AvailObject aCompiledCode);

	/**
	 * @param object
	 * @param aContainer
	 * @return
	 */
	public abstract boolean o_EqualsContainer (
		final AvailObject object,
		final AvailObject aContainer);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_EqualsContainerType (
		final AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @param aContinuation
	 * @return
	 */
	public abstract boolean o_EqualsContinuation (
		final AvailObject object,
		final AvailObject aContinuation);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_EqualsContinuationType (
		final AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @param aDoubleObject
	 * @return
	 */
	public abstract boolean o_EqualsDouble (
		final AvailObject object,
		final AvailObject aDoubleObject);

	/**
	 * @param object
	 * @param aFloatObject
	 * @return
	 */
	public abstract boolean o_EqualsFloat (
		final AvailObject object,
		final AvailObject aFloatObject);

	/**
	 * @param object
	 * @param aGeneralizedClosureType
	 * @return
	 */
	public abstract boolean o_EqualsGeneralizedClosureType (
		final AvailObject object,
		final AvailObject aGeneralizedClosureType);

	/**
	 * @param object
	 * @param anInfinity
	 * @return
	 */
	public abstract boolean o_EqualsInfinity (
		final AvailObject object,
		final AvailObject anInfinity);

	/**
	 * @param object
	 * @param anAvailInteger
	 * @return
	 */
	public abstract boolean o_EqualsInteger (
		final AvailObject object,
		final AvailObject anAvailInteger);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_EqualsIntegerRangeType (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aList
	 * @return
	 */
	public abstract boolean o_EqualsList (
		final AvailObject object,
		final AvailObject aList);

	/**
	 * @param object
	 * @param aListType
	 * @return
	 */
	public abstract boolean o_EqualsListType (
		final AvailObject object,
		final AvailObject aListType);

	/**
	 * @param object
	 * @param aMap
	 * @return
	 */
	public abstract boolean o_EqualsMap (
		final AvailObject object,
		final AvailObject aMap);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	public abstract boolean o_EqualsMapType (
		final AvailObject object,
		final AvailObject aMapType);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	public abstract boolean o_EqualsNybbleTuple (
		final AvailObject object,
		final AvailObject aTuple);

	/**
	 * @param object
	 * @param anObject
	 * @return
	 */
	public abstract boolean o_EqualsObject (
		final AvailObject object,
		final AvailObject anObject);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	public abstract boolean o_EqualsObjectTuple (
		final AvailObject object,
		final AvailObject aTuple);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_EqualsPrimitiveType (
		final AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @param aSet
	 * @return
	 */
	public abstract boolean o_EqualsSet (
		final AvailObject object,
		final AvailObject aSet);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	public abstract boolean o_EqualsSetType (
		final AvailObject object,
		final AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract boolean o_EqualsTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param aString
	 * @return
	 */
	public abstract boolean o_EqualsTwoByteString (
		final AvailObject object,
		final AvailObject aString);

	/**
	 * @param object
	 * @param potentialInstance
	 * @return
	 */
	public abstract boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance);

	/**
	 * @param object
	 * @param anotherObject
	 * @return
	 */
	public abstract boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final AvailObject anotherObject);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract boolean o_IsBetterRepresentationThanTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_IsInstanceOfSubtypeOf (
		final AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_CanComputeHashOfType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_EqualsBlank (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_EqualsFalse (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_EqualsTrue (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_EqualsVoid (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_EqualsVoidOrBlank (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ExactType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Hash (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsClosure (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsHashAvailable (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_MakeImmutable (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_MakeSubobjectsImmutable (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Type (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsBoolean (final AvailObject object);

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
	public abstract boolean o_IsByteTuple (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsCharacter (final AvailObject object);

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
	public abstract boolean o_IsString (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param aClosure
	 * @return
	 */
	public abstract boolean o_ContainsBlock (
		final AvailObject object,
		final AvailObject aClosure);

	/**
	 * @param object
	 */
	public abstract void o_PostFault (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_ReadBarrierFault (final AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Target (final AvailObject object, final AvailObject value);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Target (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Traversed (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsList (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsMap (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsByte (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsNybble (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsSet (final AvailObject object);

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @param myLevel
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_BinAddingElementHashLevelCanDestroy (
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
	public abstract boolean o_BinHasElementHash (
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
	public abstract AvailObject o_BinRemoveElementHashCanDestroy (
		final AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param potentialSuperset
	 * @return
	 */
	public abstract boolean o_IsBinSubsetOf (
		final AvailObject object,
		final AvailObject potentialSuperset);

	/**
	 * @param object
	 * @param mutableTuple
	 * @param startingIndex
	 * @return
	 */
	public abstract int o_PopulateTupleStartingAt (
		final AvailObject object,
		final AvailObject mutableTuple,
		final int startingIndex);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_BinHash (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_BinSize (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_BinUnionType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsTuple (final AvailObject object);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_TypeEquals (
		final AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_HashOfType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsCyclicType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsExtendedInteger (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsIntegerRangeType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsListType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsMapType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsSetType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsTupleType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsType (final AvailObject object);

	/**
	 * @param object
	 * @param visitor
	 */
	public abstract void o_ScanSubobjects (
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
	Iterator<AvailObject> o_Iterator (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ParsingInstructions (
		AvailObject object,
		AvailObject instructionsTuple);

	/**
	 * @param object
	 * @param value
	 */
	public abstract AvailObject o_ParsingInstructions (
		AvailObject object);

	/**
	 * @param object
	 * @param continuation
	 */
	public abstract void o_mapDo (
		final AvailObject object,
		final Continuation2<AvailObject, AvailObject> continuation);

}
