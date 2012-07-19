/**
 * IndirectionDescriptor.java
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

import java.math.BigInteger;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.AbstractNumberDescriptor.Order;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.*;
import com.avail.visitor.AvailSubobjectVisitor;

/**
 * An {@link AvailObject} with an {@link IndirectionDescriptor} keeps track of
 * its target, that which it is pretending to be.  Almost all messages are
 * routed to the target, making it an ideal proxy.
 * <p>
 * When some kinds of objects are compared to each other, say {@linkplain
 * StringDescriptor strings}, a check is first made to see if the objects
 * are at the same location in memory -- the same AvailObject in the current
 * version that uses {@link AvailObjectRepresentation}.  If so, it immediately
 * returns true.  If not, a more detailed, potentially expensive comparison
 * takes place.  If the objects are found to be equal, one of them is mutated
 * into an indirection (by replacing its descriptor with an {@link
 * IndirectionDescriptor}) to cause subsequent comparisons to be faster.
 * </p>
 * <p>
 * When Avail has had its own garbage collector over the years, it has been
 * possible to strip off indirections during a suitable level of garbage
 * collection.  When combined with the comparison optimization above, this has
 * the effect of collapsing together equal objects.  There was even once a
 * mechanism that collected objects at some garbage collection generation into
 * a set, causing <em>all</em> equal objects in that generation to be compared
 * against each other.  So not only does this mechanism save time, it also saves
 * space.
 * </p>
 * <p>
 * Of course, the cost of traversing indirections, and even just of descriptors
 * may be significant.  That's a complexity price that's paid once, with
 * many mechanisms depending on it to effect higher level optimizations.  My bet
 * is this that will have a net payoff.  Especially since the low level
 * optimizations can be replaced with expression folding, dynamic inlining,
 * object escape analysis, instance-specific optimizations, and a plethora of
 * other just-in-time optimizations.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public final class IndirectionDescriptor
extends AbstractDescriptor
{

	/**
	 * The object slots of my {@link AvailObject} instances.  In particular, an
	 * {@linkplain IndirectionDescriptor indirection} has just a {@link
	 * #INDIRECTION_TARGET}, which is the object that the current object is
	 * equivalent to.  There may be other slots, depending on our mechanism for
	 * conversion to an indirection object, but they should be ignored.
	 */
	enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The target {@linkplain AvailObject object} to which my instance is
		 * delegating all behavior.
		 */
		INDIRECTION_TARGET,

		/**
		 * All other object slots should be ignored.
		 */
		@HideFieldInDebugger
		IGNORED_OBJECT_SLOT_;
	}

	/**
	 * The integer slots of my {@link AvailObject} instances.  Always ignored
	 * for an indirection object.
	 */
	enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * Ignore all integer slots.
		 */
		@HideFieldInDebugger
		IGNORED_INTEGER_SLOT_;
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == ObjectSlots.INDIRECTION_TARGET;
	}

	@Override
	void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		object.traversed().printOnAvoidingIndent(
			aStream,
			recursionList,
			indent);
	}

	@Override
	void o_ScanSubobjects (
		final @NotNull AvailObject object,
		final @NotNull AvailSubobjectVisitor visitor)
	{
		// Manually constructed scanning method.

		visitor.invoke(object, object.slot(ObjectSlots.INDIRECTION_TARGET));
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Make the object immutable so it can be shared safely. If it was mutable
	 * then we have to make my target immutable as well (recursively down to
	 * immutable descendants).
	 * </p>
	 */
	@Override
	@NotNull AvailObject o_MakeImmutable (final @NotNull AvailObject object)
	{
		if (isMutable)
		{
			object.descriptor = immutable();
			object.slot(ObjectSlots.INDIRECTION_TARGET).makeImmutable();
		}
		return object;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Answer the non-indirection pointed to (transitively) by object.  Also
	 * changes the object to point directly at the ultimate target to save hops
	 * next time if possible.
	 * </p>
	 */
	@Override
	@NotNull AvailObject o_Traversed (final @NotNull AvailObject object)
	{
		final AvailObject next = object.slot(ObjectSlots.INDIRECTION_TARGET);
		final AvailObject finalObject = next.traversed();
		object.setSlot(ObjectSlots.INDIRECTION_TARGET, finalObject);
		return finalObject;
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	Iterator<AvailObject> o_Iterator (final @NotNull AvailObject object)
	{
		return o_Traversed(object).iterator();
	}

	/**
	 * Construct a new {@link IndirectionDescriptor}.
	 *
	 * @param isMutable
	 *            Does the {@linkplain Descriptor descriptor} represent a
	 *            mutable object?
	 */
	protected IndirectionDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link IndirectionDescriptor}.
	 */
	private static final IndirectionDescriptor mutable = new IndirectionDescriptor(
		true);

	/**
	 * Answer the mutable {@link IndirectionDescriptor}.
	 *
	 * @return The mutable {@link IndirectionDescriptor}.
	 */
	public static IndirectionDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link IndirectionDescriptor}.
	 */
	private static final IndirectionDescriptor immutable = new IndirectionDescriptor(
		false);

	/**
	 * Answer the immutable {@link IndirectionDescriptor}.
	 *
	 * @return The immutable {@link IndirectionDescriptor}.
	 */
	public static IndirectionDescriptor immutable ()
	{
		return immutable;
	}

	@Override
	boolean o_AcceptsArgTypesFromFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject functionType)
	{
		return o_Traversed(object).acceptsArgTypesFromFunctionType(functionType);
	}

	@Override
	boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		return o_Traversed(object).acceptsListOfArgTypes(argTypes);
	}

	@Override
	boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argValues)
	{
		return o_Traversed(object).acceptsListOfArgValues(argValues);
	}

	@Override
	boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argTypes)
	{
		return o_Traversed(object).acceptsTupleOfArgTypes(argTypes);
	}

	@Override
	boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final @NotNull AvailObject arguments)
	{
		return o_Traversed(object).acceptsTupleOfArguments(arguments);
	}

	@Override
	void o_AddDependentChunkIndex (
		final @NotNull AvailObject object,
		final int aChunkIndex)
	{
		o_Traversed(object).addDependentChunkIndex(aChunkIndex);
	}

	@Override
	void o_AddImplementation (
			final @NotNull AvailObject object,
			final @NotNull AvailObject implementation)
		throws SignatureException
	{
		o_Traversed(object).addImplementation(implementation);
	}

	@Override
	void o_AddGrammaticalRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject restrictions)
	{
		o_Traversed(object).addGrammaticalRestrictions(restrictions);
	}

	@Override
	@NotNull AvailObject o_AddToInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_AddToIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object)
				.addToIntegerCanDestroy(anInteger, canDestroy);
	}

	@Override
	void o_AddGrammaticalMessageRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject illegalArgMsgs)
	{
		o_Traversed(object).addGrammaticalRestrictions(
			methodName,
			illegalArgMsgs);
	}

	@Override
	void o_AddMethodImplementation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject implementation)
	{
		o_Traversed(object).addMethodImplementation(
			methodName,
			implementation);
	}

	@Override
	void o_AtMessageAddBundle (
		final @NotNull AvailObject object,
		final @NotNull AvailObject message,
		final @NotNull AvailObject bundle)
	{
		o_Traversed(object).atMessageAddBundle(message, bundle);
	}

	@Override
	void o_AtNameAdd (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName,
		final @NotNull AvailObject trueName)
	{
		o_Traversed(object).atNameAdd(stringName, trueName);
	}

	@Override
	void o_AtNewNamePut (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName,
		final @NotNull AvailObject trueName)
	{
		o_Traversed(object).atNewNamePut(stringName, trueName);
	}

	@Override
	void o_AtPrivateNameAdd (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName,
		final @NotNull AvailObject trueName)
	{
		o_Traversed(object).atPrivateNameAdd(stringName, trueName);
	}

	@Override
	@NotNull AvailObject o_SetBinAddingElementHashLevelCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		return o_Traversed(object).setBinAddingElementHashLevelCanDestroy(
			elementObject,
			elementObjectHash,
			myLevel,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_BinElementAt (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).binElementAt(index);
	}

	@Override
	void o_BinElementAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).binElementAtPut(index, value);
	}

	@Override
	boolean o_BinHasElementWithHash (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash)
	{
		return o_Traversed(object).binHasElementWithHash(
			elementObject,
			elementObjectHash);
	}

	@Override
	void o_BinHash (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).binHash(value);
	}

	@Override
	@NotNull AvailObject o_BinRemoveElementHashCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{
		return o_Traversed(object).binRemoveElementHashCanDestroy(
			elementObject,
			elementObjectHash,
			canDestroy);
	}

	@Override
	void o_BinSize (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).binSize(value);
	}

	@Override
	void o_BinUnionTypeOrNull (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).binUnionTypeOrNull(value);
	}

	@Override
	void o_BitVector (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).bitVector(value);
	}

	@Override
	void o_BreakpointBlock (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).breakpointBlock(value);
	}

	@Override
	void o_BuildFilteredBundleTreeFrom (
		final @NotNull AvailObject object,
		final @NotNull AvailObject bundleTree)
	{
		o_Traversed(object).buildFilteredBundleTreeFrom(bundleTree);
	}

	@Override
	void o_Caller (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).caller(value);
	}

	@Override
	void o_Function (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).function(value);
	}

	@Override
	void o_Code (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).code(value);
	}

	@Override
	void o_CodePoint (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).codePoint(value);
	}

	@Override
	boolean o_CompareFromToWithStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject anotherObject,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithStartingAt(
			startIndex1,
			endIndex1,
			anotherObject,
			startIndex2);
	}

	@Override
	boolean o_CompareFromToWithAnyTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject aTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithAnyTupleStartingAt(
			startIndex1,
			endIndex1,
			aTuple,
			startIndex2);
	}

	@Override
	boolean o_CompareFromToWithByteStringStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject aByteString,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithByteStringStartingAt(
			startIndex1,
			endIndex1,
			aByteString,
			startIndex2);
	}

	@Override
	boolean o_CompareFromToWithByteTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject aByteTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithByteTupleStartingAt(
			startIndex1,
			endIndex1,
			aByteTuple,
			startIndex2);
	}

	@Override
	boolean o_CompareFromToWithNybbleTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject aNybbleTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithNybbleTupleStartingAt(
			startIndex1,
			endIndex1,
			aNybbleTuple,
			startIndex2);
	}

	@Override
	boolean o_CompareFromToWithObjectTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject anObjectTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithObjectTupleStartingAt(
			startIndex1,
			endIndex1,
			anObjectTuple,
			startIndex2);
	}

	@Override
	boolean o_CompareFromToWithTwoByteStringStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final @NotNull AvailObject aTwoByteString,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithTwoByteStringStartingAt(
			startIndex1,
			endIndex1,
			aTwoByteString,
			startIndex2);
	}

	@Override
	int o_ComputeHashFromTo (
		final @NotNull AvailObject object,
		final int start,
		final int end)
	{
		return o_Traversed(object).computeHashFromTo(start, end);
	}

	@Override
	@NotNull AvailObject o_ConcatenateTuplesCanDestroy (
		final @NotNull AvailObject object,
		final boolean canDestroy)
	{
		return o_Traversed(object).concatenateTuplesCanDestroy(canDestroy);
	}

	@Override
	boolean o_ContainsBlock (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunction)
	{
		return o_Traversed(object).containsBlock(aFunction);
	}

	@Override
	void o_Continuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).continuation(value);
	}

	@Override
	void o_CopyToRestrictedTo (
		final @NotNull AvailObject object,
		final @NotNull AvailObject filteredBundleTree,
		final @NotNull AvailObject visibleNames)
	{
		o_Traversed(object)
				.copyToRestrictedTo(filteredBundleTree, visibleNames);
	}

	@Override
	@NotNull AvailObject o_CopyTupleFromToCanDestroy (
		final @NotNull AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		return o_Traversed(object).copyTupleFromToCanDestroy(
			start,
			end,
			canDestroy);
	}

	@Override
	boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		return o_Traversed(object).couldEverBeInvokedWith(argTypes);
	}

	@Override
	@NotNull AvailObject o_DivideCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideCanDestroy(aNumber, canDestroy);
	}

	@Override
	@NotNull AvailObject o_DivideIntoInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_DivideIntoIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_ElementAt (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).elementAt(index);
	}

	@Override
	void o_ElementAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).elementAtPut(index, value);
	}

	@Override
	int o_EndOfZone (final @NotNull AvailObject object, final int zone)
	{
		return o_Traversed(object).endOfZone(zone);
	}

	@Override
	int o_EndSubtupleIndexInZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		return o_Traversed(object).endSubtupleIndexInZone(zone);
	}

	@Override
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).equals(another);
	}

	@Override
	boolean o_EqualsAnyTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherTuple)
	{
		return o_Traversed(object).equalsAnyTuple(anotherTuple);
	}

	@Override
	boolean o_EqualsByteString (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aByteString)
	{
		return o_Traversed(object).equalsByteString(aByteString);
	}

	@Override
	boolean o_EqualsByteTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aByteTuple)
	{
		return o_Traversed(object).equalsByteTuple(aByteTuple);
	}

	@Override
	boolean o_EqualsCharacterWithCodePoint (
		final @NotNull AvailObject object,
		final int otherCodePoint)
	{
		return o_Traversed(object).equalsCharacterWithCodePoint(otherCodePoint);
	}

	@Override
	boolean o_EqualsFunction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunction)
	{
		return o_Traversed(object).equalsFunction(aFunction);
	}

	@Override
	boolean o_EqualsFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		return o_Traversed(object).equalsFunctionType(aFunctionType);
	}

	@Override
	boolean o_EqualsCompiledCode (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCode)
	{
		return o_Traversed(object).equalsCompiledCode(aCompiledCode);
	}

	@Override
	boolean o_EqualsVariable (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariable)
	{
		return o_Traversed(object).equalsVariable(aVariable);
	}

	@Override
	boolean o_EqualsVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
	{
		return o_Traversed(object).equalsVariableType(aVariableType);
	}

	@Override
	boolean o_EqualsContinuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuation)
	{
		return o_Traversed(object).equalsContinuation(aContinuation);
	}

	@Override
	boolean o_EqualsContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		return o_Traversed(object).equalsContinuationType(aContinuationType);
	}

	@Override
	boolean o_EqualsCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		return o_Traversed(object).equalsCompiledCodeType(aCompiledCodeType);
	}

	@Override
	boolean o_EqualsDouble (
		final @NotNull AvailObject object,
		final double aDouble)
	{
		return o_Traversed(object).equalsDouble(aDouble);
	}

	@Override
	boolean o_EqualsFloat (
		final @NotNull AvailObject object,
		final float aFloat)
	{
		return o_Traversed(object).equalsFloat(aFloat);
	}

	@Override
	boolean o_EqualsInfinity (
		final @NotNull AvailObject object,
		final @NotNull Sign sign)
	{
		return o_Traversed(object).equalsInfinity(sign);
	}

	@Override
	boolean o_EqualsInteger (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anAvailInteger)
	{
		return o_Traversed(object).equalsInteger(anAvailInteger);
	}

	@Override
	boolean o_EqualsIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return o_Traversed(object).equalsIntegerRangeType(anIntegerRangeType);
	}

	@Override
	boolean o_EqualsMap (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMap)
	{
		return o_Traversed(object).equalsMap(aMap);
	}

	@Override
	boolean o_EqualsMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return o_Traversed(object).equalsMapType(aMapType);
	}

	@Override
	boolean o_EqualsNybbleTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNybbleTuple)
	{
		return o_Traversed(object).equalsNybbleTuple(aNybbleTuple);
	}

	@Override
	boolean o_EqualsObject (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObject)
	{
		return o_Traversed(object).equalsObject(anObject);
	}

	@Override
	boolean o_EqualsObjectTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectTuple)
	{
		return o_Traversed(object).equalsObjectTuple(anObjectTuple);
	}

	@Override
	boolean o_EqualsPojo (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aRawPojo)
	{
		return o_Traversed(object).equalsPojo(aRawPojo);
	}

	@Override
	boolean o_EqualsPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return o_Traversed(object).equalsPojoType(aPojoType);
	}

	@Override
	boolean o_EqualsPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType)
	{
		return o_Traversed(object).equalsPrimitiveType(aPrimitiveType);
	}

	@Override
	boolean o_EqualsRawPojo (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojo)
	{
		return o_Traversed(object).equalsRawPojo(aPojo);
	}

	@Override
	boolean o_EqualsSet (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSet)
	{
		return o_Traversed(object).equalsSet(aSet);
	}

	@Override
	boolean o_EqualsSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return o_Traversed(object).equalsSetType(aSetType);
	}

	@Override
	boolean o_EqualsTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return o_Traversed(object).equalsTupleType(aTupleType);
	}

	@Override
	boolean o_EqualsTwoByteString (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTwoByteString)
	{
		return o_Traversed(object).equalsTwoByteString(aTwoByteString);
	}

	@Override
	void o_ExecutionState (
		final @NotNull AvailObject object,
		final @NotNull ExecutionState value)
	{
		o_Traversed(object).executionState(value);
	}

	@Override
	byte o_ExtractNybbleFromTupleAt (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).extractNybbleFromTupleAt(index);
	}

	@Override
	List<AvailObject> o_FilterByTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		return o_Traversed(object).filterByTypes(argTypes);
	}

	@Override
	@NotNull AvailObject o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone (
		final @NotNull AvailObject object,
		final int zone,
		final @NotNull AvailObject newSubtuple,
		final int startSubtupleIndex,
		final int endOfZone)
	{
		return o_Traversed(object)
				.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
					zone,
					newSubtuple,
					startSubtupleIndex,
					endOfZone);
	}

	@Override
	@NotNull Order o_NumericCompareToInteger (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger)
	{
		return o_Traversed(object).numericCompareToInteger(anInteger);
	}

	@Override
	@NotNull Order o_NumericCompareToInfinity (
		final @NotNull AvailObject object,
		final Sign sign)
	{
		return o_Traversed(object).numericCompareToInfinity(sign);
	}

	@Override
	boolean o_HasElement (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject)
	{
		return o_Traversed(object).hasElement(elementObject);
	}

	@Override
	void o_Hash (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).hash(value);
	}

	@Override
	int o_HashFromTo (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		return o_Traversed(object).hashFromTo(startIndex, endIndex);
	}

	@Override
	void o_HashOrZero (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).hashOrZero(value);
	}

	@Override
	boolean o_HasKey (
		final @NotNull AvailObject object,
		final @NotNull AvailObject keyObject)
	{
		return o_Traversed(object).hasKey(keyObject);
	}

	@Override
	boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance)
	{
		return o_Traversed(object).hasObjectInstance(potentialInstance);
	}

	@Override
	List<AvailObject> o_ImplementationsAtOrBelow (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		return o_Traversed(object).implementationsAtOrBelow(argTypes);
	}

	@Override
	@NotNull AvailObject o_IncludeBundle (
		final @NotNull AvailObject object,
		final @NotNull AvailObject messageBundle)
	{
		return o_Traversed(object).includeBundle(
			messageBundle);
	}

	@Override
	boolean o_IncludesImplementation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject imp)
	{
		return o_Traversed(object).includesImplementation(imp);
	}

	@Override
	void o_Index (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).index(value);
	}

	@Override
	void o_SetInterruptRequestFlag (
		final @NotNull AvailObject object,
		final BitField value)
	{
		o_Traversed(object).setInterruptRequestFlag(value);
	}

	@Override
	void o_CountdownToReoptimize (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).countdownToReoptimize(value);
	}

	@Override
	boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherObject)
	{
		return o_Traversed(object).isBetterRepresentationThan(anotherObject);
	}

	@Override
	boolean o_IsBetterRepresentationThanTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return o_Traversed(object).isBetterRepresentationThanTupleType(
			aTupleType);
	}

	@Override
	boolean o_IsBinSubsetOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialSuperset)
	{
		return o_Traversed(object).isBinSubsetOf(potentialSuperset);
	}

	@Override
	boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return o_Traversed(object).isInstanceOfKind(aType);
	}

	@Override
	void o_IsSaved (final @NotNull AvailObject object, final boolean aBoolean)
	{
		o_Traversed(object).isSaved(aBoolean);
	}

	@Override
	boolean o_IsSubsetOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).isSubsetOf(another);
	}

	@Override
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return o_Traversed(object).isSubtypeOf(aType);
	}

	@Override
	boolean o_IsSupertypeOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
	{
		return o_Traversed(object).isSupertypeOfVariableType(aVariableType);
	}

	@Override
	boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		return o_Traversed(object).isSupertypeOfContinuationType(
			aContinuationType);
	}

	@Override
	boolean o_IsSupertypeOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		return o_Traversed(object).isSupertypeOfCompiledCodeType(
			aCompiledCodeType);
	}

	@Override
	boolean o_IsSupertypeOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		return o_Traversed(object).isSupertypeOfFunctionType(aFunctionType);
	}

	@Override
	boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return o_Traversed(object).isSupertypeOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return o_Traversed(object).isSupertypeOfMapType(aMapType);
	}

	@Override
	boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		return o_Traversed(object).isSupertypeOfObjectType(anObjectType);
	}

	@Override
	boolean o_IsSupertypeOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		return o_Traversed(object).isSupertypeOfParseNodeType(aParseNodeType);
	}

	@Override
	boolean o_IsSupertypeOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return o_Traversed(object).isSupertypeOfPojoType(aPojoType);
	}

	@Override
	boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType)
	{
		return o_Traversed(object).isSupertypeOfPrimitiveType(aPrimitiveType);
	}

	@Override
	boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return o_Traversed(object).isSupertypeOfSetType(aSetType);
	}

	@Override
	boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return o_Traversed(object).isSupertypeOfTupleType(aTupleType);
	}

	@Override
	boolean o_IsSupertypeOfEnumerationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anEnumerationType)
	{
		return o_Traversed(object).isSupertypeOfEnumerationType(
			anEnumerationType);
	}

	@Override
	void o_LevelTwoChunkOffset (
		final @NotNull AvailObject object,
		final @NotNull AvailObject chunk,
		final int offset)
	{
		o_Traversed(object).levelTwoChunkOffset(chunk, offset);
	}

	@Override
	@NotNull AvailObject o_LiteralAt (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).literalAt(index);
	}

	@Override
	@NotNull AvailObject o_ArgOrLocalOrStackAt (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).argOrLocalOrStackAt(index);
	}

	@Override
	void o_ArgOrLocalOrStackAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).argOrLocalOrStackAtPut(index, value);
	}

	@Override
	@NotNull AvailObject o_LocalTypeAt (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).localTypeAt(index);
	}

	@Override
	@NotNull AvailObject o_LookupByTypesFromTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argumentTypeTuple)
	{
		return o_Traversed(object).lookupByTypesFromTuple(argumentTypeTuple);
	}

	@Override
	@NotNull AvailObject o_LookupByValuesFromList (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argumentList)
	{
		return o_Traversed(object).lookupByValuesFromList(argumentList);
	}

	@Override
	@NotNull AvailObject o_LookupByValuesFromTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argumentTuple)
	{
		return o_Traversed(object).lookupByValuesFromTuple(argumentTuple);
	}

	@Override
	@NotNull AvailObject o_MapAt (
		final @NotNull AvailObject object,
		final @NotNull AvailObject keyObject)
	{
		return o_Traversed(object).mapAt(keyObject);
	}

	@Override
	@NotNull AvailObject o_MapAtPuttingCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject keyObject,
		final @NotNull AvailObject newValueObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapAtPuttingCanDestroy(
			keyObject,
			newValueObject,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_MapWithoutKeyCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject keyObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapWithoutKeyCanDestroy(
			keyObject,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_MinusCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).minusCanDestroy(aNumber, canDestroy);
	}

	@Override
	@NotNull AvailObject o_MultiplyByInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_MultiplyByIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	void o_Name (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).name(value);
	}

	@Override
	boolean o_NameVisible (
		final @NotNull AvailObject object,
		final @NotNull AvailObject trueName)
	{
		return o_Traversed(object).nameVisible(trueName);
	}

	@Override
	boolean o_OptionallyNilOuterVar (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).optionallyNilOuterVar(index);
	}

	@Override
	@NotNull AvailObject o_OuterTypeAt (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).outerTypeAt(index);
	}

	@Override
	@NotNull AvailObject o_OuterVarAt (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).outerVarAt(index);
	}

	@Override
	void o_OuterVarAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).outerVarAtPut(index, value);
	}

	@Override
	void o_Parent (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).parent(value);
	}

	@Override
	void o_Pc (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).pc(value);
	}

	@Override
	@NotNull AvailObject o_PlusCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).plusCanDestroy(aNumber, canDestroy);
	}

	@Override
	void o_Priority (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).priority(value);
	}

	@Override
	@NotNull AvailObject o_PrivateAddElement (
		final @NotNull AvailObject object,
		final @NotNull AvailObject element)
	{
		return o_Traversed(object).privateAddElement(element);
	}

	@Override
	@NotNull AvailObject o_PrivateExcludeElement (
		final @NotNull AvailObject object,
		final @NotNull AvailObject element)
	{
		return o_Traversed(object).privateExcludeElement(element);
	}

	@Override
	@NotNull AvailObject o_PrivateExcludeElementKnownIndex (
		final @NotNull AvailObject object,
		final @NotNull AvailObject element,
		final int knownIndex)
	{
		return o_Traversed(object).privateExcludeElementKnownIndex(
			element,
			knownIndex);
	}

	@Override
	void o_FiberGlobals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).fiberGlobals(value);
	}

	@Override
	short o_RawByteAt (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).rawByteAt(index);
	}

	@Override
	void o_RawByteAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		o_Traversed(object).rawByteAtPut(index, anInteger);
	}

	@Override
	short o_RawByteForCharacterAt (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawByteForCharacterAt(index);
	}

	@Override
	void o_RawByteForCharacterAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		o_Traversed(object).rawByteForCharacterAtPut(index, anInteger);
	}

	@Override
	byte o_RawNybbleAt (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).rawNybbleAt(index);
	}

	@Override
	void o_RawNybbleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final byte aNybble)
	{
		o_Traversed(object).rawNybbleAtPut(index, aNybble);
	}

	@Override
	int o_RawShortForCharacterAt (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawShortForCharacterAt(index);
	}

	@Override
	void o_RawShortForCharacterAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int anInteger)
	{
		o_Traversed(object).rawShortForCharacterAtPut(index, anInteger);
	}

	@Override
	int o_RawSignedIntegerAt (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).rawSignedIntegerAt(index);
	}

	@Override
	void o_RawSignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).rawSignedIntegerAtPut(index, value);
	}

	@Override
	long o_RawUnsignedIntegerAt (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawUnsignedIntegerAt(index);
	}

	@Override
	void o_RawUnsignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).rawUnsignedIntegerAtPut(index, value);
	}

	@Override
	void o_RemoveDependentChunkIndex (
		final @NotNull AvailObject object,
		final int aChunkIndex)
	{
		o_Traversed(object).removeDependentChunkIndex(aChunkIndex);
	}

	@Override
	void o_RemoveFrom (
		final @NotNull AvailObject object,
		final @NotNull L2Interpreter anInterpreter)
	{
		o_Traversed(object).removeFrom(anInterpreter);
	}

	@Override
	void o_RemoveImplementation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject implementation)
	{
		o_Traversed(object).removeImplementation(implementation);
	}

	@Override
	boolean o_RemoveBundle (
		final @NotNull AvailObject object,
		final @NotNull AvailObject bundle)
	{
		return o_Traversed(object).removeBundle(
			bundle);
	}

	@Override
	void o_RemoveGrammaticalRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject obsoleteRestrictions)
	{
		o_Traversed(object).removeGrammaticalRestrictions(obsoleteRestrictions);
	}

	@Override
	void o_ResolvedForwardWithName (
		final @NotNull AvailObject object,
		final @NotNull AvailObject forwardImplementation,
		final @NotNull AvailObject methodName)
	{
		o_Traversed(object).resolvedForwardWithName(
			forwardImplementation,
			methodName);
	}

	@Override
	@NotNull AvailObject o_SetIntersectionCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setIntersectionCanDestroy(
			otherSet,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_SetMinusCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setMinusCanDestroy(otherSet, canDestroy);
	}

	@Override
	void o_SetSubtupleForZoneTo (
		final @NotNull AvailObject object,
		final int zoneIndex,
		final @NotNull AvailObject newTuple)
	{
		o_Traversed(object).setSubtupleForZoneTo(zoneIndex, newTuple);
	}

	@Override
	@NotNull AvailObject o_SetUnionCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setUnionCanDestroy(otherSet, canDestroy);
	}

	@Override
	void o_SetValue (
		final @NotNull AvailObject object,
		final @NotNull AvailObject newValue)
	{
		o_Traversed(object).setValue(newValue);
	}

	@Override
	@NotNull AvailObject o_SetWithElementCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject newElementObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).setWithElementCanDestroy(
			newElementObject,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_SetWithoutElementCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObjectToExclude,
		final boolean canDestroy)
	{
		return o_Traversed(object).setWithoutElementCanDestroy(
			elementObjectToExclude,
			canDestroy);
	}

	@Override
	void o_Size (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).size(value);
	}

	@Override
	int o_SizeOfZone (final @NotNull AvailObject object, final int zone)
	{
		return o_Traversed(object).sizeOfZone(zone);
	}

	@Override
	@NotNull AvailObject o_StackAt (final @NotNull AvailObject object, final int slotIndex)
	{
		return o_Traversed(object).stackAt(slotIndex);
	}

	@Override
	void o_StackAtPut (
		final @NotNull AvailObject object,
		final int slotIndex,
		final @NotNull AvailObject anObject)
	{
		o_Traversed(object).stackAtPut(slotIndex, anObject);
	}

	@Override
	void o_Stackp (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).stackp(value);
	}

	@Override
	void o_Start (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).start(value);
	}

	@Override
	void o_StartingChunk (
		final @NotNull AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).startingChunk(value);
	}

	@Override
	int o_StartOfZone (final @NotNull AvailObject object, final int zone)
	{
		return o_Traversed(object).startOfZone(zone);
	}

	@Override
	int o_StartSubtupleIndexInZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		return o_Traversed(object).startSubtupleIndexInZone(zone);
	}

	@Override
	void o_String (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).string(value);
	}

	@Override
	@NotNull AvailObject o_SubtractFromInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_SubtractFromIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_SubtupleForZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		return o_Traversed(object).subtupleForZone(zone);
	}

	@Override
	@NotNull AvailObject o_TimesCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).timesCanDestroy(aNumber, canDestroy);
	}

	@Override
	void o_TokenType (final @NotNull AvailObject object, final TokenDescriptor.TokenType value)
	{
		o_Traversed(object).tokenType(value);
	}

	@Override
	int o_TranslateToZone (
		final @NotNull AvailObject object,
		final int tupleIndex,
		final int zoneIndex)
	{
		return o_Traversed(object).translateToZone(tupleIndex, zoneIndex);
	}

	@Override
	@NotNull AvailObject o_TrueNamesForStringName (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName)
	{
		return o_Traversed(object).trueNamesForStringName(stringName);
	}

	@Override
	@NotNull AvailObject o_TruncateTo (
		final @NotNull AvailObject object,
		final int newTupleSize)
	{
		return o_Traversed(object).truncateTo(newTupleSize);
	}

	@Override
	@NotNull AvailObject o_TupleAt (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).tupleAt(index);
	}

	@Override
	void o_TupleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject aNybbleObject)
	{
		o_Traversed(object).tupleAtPut(index, aNybbleObject);
	}

	@Override
	@NotNull AvailObject o_TupleAtPuttingCanDestroy (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject newValueObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).tupleAtPuttingCanDestroy(
			index,
			newValueObject,
			canDestroy);
	}

	@Override
	int o_TupleIntAt (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).tupleIntAt(index);
	}

	@Override
	void o_Type (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).type(value);
	}

	@Override
	@NotNull AvailObject o_TypeAtIndex (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).typeAtIndex(index);
	}

	@Override
	@NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).typeIntersection(another);
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		return o_Traversed(object).typeIntersectionOfFunctionType(
			aFunctionType);
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
	{
		return o_Traversed(object).typeIntersectionOfVariableType(
			aVariableType);
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		return o_Traversed(object).typeIntersectionOfContinuationType(
			aContinuationType);
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		return o_Traversed(object).typeIntersectionOfCompiledCodeType(
			aCompiledCodeType);
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return o_Traversed(object).typeIntersectionOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return o_Traversed(object).typeIntersectionOfMapType(aMapType);
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		return o_Traversed(object).typeIntersectionOfObjectType(anObjectType);
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		return o_Traversed(object).typeIntersectionOfParseNodeType(
			aParseNodeType);
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return o_Traversed(object).typeIntersectionOfPojoType(aPojoType);
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return o_Traversed(object).typeIntersectionOfSetType(aSetType);
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return o_Traversed(object).typeIntersectionOfTupleType(aTupleType);
	}

	@Override
	@NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).typeUnion(another);
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		return o_Traversed(object).typeUnionOfFunctionType(aFunctionType);
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
	{
		return o_Traversed(object).typeUnionOfVariableType(aVariableType);
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		return o_Traversed(object).typeUnionOfContinuationType(
			aContinuationType);
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType)
	{
		return o_Traversed(object).typeUnionOfCompiledCodeType(
			aCompiledCodeType);
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return o_Traversed(object).typeUnionOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return o_Traversed(object).typeUnionOfMapType(aMapType);
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		return o_Traversed(object).typeUnionOfObjectType(anObjectType);
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType)
	{
		return o_Traversed(object).typeUnionOfParseNodeType(
			aParseNodeType);
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return o_Traversed(object).typeUnionOfPojoType(aPojoType);
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return o_Traversed(object).typeUnionOfSetType(aSetType);
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return o_Traversed(object).typeUnionOfTupleType(aTupleType);
	}

	@Override
	@NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		return o_Traversed(object).unionOfTypesAtThrough(startIndex, endIndex);
	}

	@Override
	int o_UntranslatedDataAt (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).untranslatedDataAt(index);
	}

	@Override
	void o_UntranslatedDataAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).untranslatedDataAtPut(index, value);
	}

	@Override
	@NotNull AvailObject o_ValidateArgumentTypesInterpreterIfFail (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull Interpreter anAvailInterpreter,
		final @NotNull Continuation1<Generator<String>> failBlock)
	{
		return o_Traversed(object).validateArgumentTypesInterpreterIfFail(
			argTypes,
			anAvailInterpreter,
			failBlock);
	}

	@Override
	void o_Value (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).value(value);
	}

	@Override
	int o_ZoneForIndex (final @NotNull AvailObject object, final int index)
	{
		return o_Traversed(object).zoneForIndex(index);
	}

	@Override
	String o_AsNativeString (final @NotNull AvailObject object)
	{
		return o_Traversed(object).asNativeString();
	}

	@Override
	@NotNull AvailObject o_AsSet (final @NotNull AvailObject object)
	{
		return o_Traversed(object).asSet();
	}

	@Override
	@NotNull AvailObject o_AsTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).asTuple();
	}

	@Override
	int o_BinHash (final @NotNull AvailObject object)
	{
		return o_Traversed(object).binHash();
	}

	@Override
	int o_BinSize (final @NotNull AvailObject object)
	{
		return o_Traversed(object).binSize();
	}

	@Override
	@NotNull AvailObject o_BinUnionTypeOrNull (final @NotNull AvailObject object)
	{
		return o_Traversed(object).binUnionTypeOrNull();
	}

	@Override
	int o_BitsPerEntry (final @NotNull AvailObject object)
	{
		return o_Traversed(object).bitsPerEntry();
	}

	@Override
	@NotNull AvailObject o_BodyBlock (final @NotNull AvailObject object)
	{
		return o_Traversed(object).bodyBlock();
	}

	@Override
	@NotNull AvailObject o_BodySignature (final @NotNull AvailObject object)
	{
		return o_Traversed(object).bodySignature();
	}

	@Override
	@NotNull AvailObject o_BreakpointBlock (final @NotNull AvailObject object)
	{
		return o_Traversed(object).breakpointBlock();
	}

	@Override
	@NotNull AvailObject o_Caller (final @NotNull AvailObject object)
	{
		return o_Traversed(object).caller();
	}

	@Override
	void o_CleanUpAfterCompile (final @NotNull AvailObject object)
	{
		o_Traversed(object).cleanUpAfterCompile();
	}

	@Override
	void o_ClearValue (final @NotNull AvailObject object)
	{
		o_Traversed(object).clearValue();
	}

	@Override
	@NotNull AvailObject o_Function (final @NotNull AvailObject object)
	{
		return o_Traversed(object).function();
	}

	@Override
	@NotNull AvailObject o_FunctionType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).functionType();
	}

	@Override
	@NotNull AvailObject o_Code (final @NotNull AvailObject object)
	{
		return o_Traversed(object).code();
	}

	@Override
	int o_CodePoint (final @NotNull AvailObject object)
	{
		return o_Traversed(object).codePoint();
	}

	@Override
	@NotNull AvailObject o_LazyComplete (final @NotNull AvailObject object)
	{
		return o_Traversed(object).lazyComplete();
	}

	@Override
	@NotNull AvailObject o_ConstantBindings (final @NotNull AvailObject object)
	{
		return o_Traversed(object).constantBindings();
	}

	@Override
	@NotNull AvailObject o_ContentType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).contentType();
	}

	@Override
	@NotNull AvailObject o_Continuation (final @NotNull AvailObject object)
	{
		return o_Traversed(object).continuation();
	}

	@Override
	@NotNull AvailObject o_CopyAsMutableContinuation (final @NotNull AvailObject object)
	{
		return o_Traversed(object).copyAsMutableContinuation();
	}

	@Override
	@NotNull AvailObject o_CopyAsMutableObjectTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).copyAsMutableObjectTuple();
	}

	@Override
	@NotNull AvailObject o_CopyAsMutableSpliceTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).copyAsMutableSpliceTuple();
	}

	@Override
	@NotNull AvailObject o_DefaultType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).defaultType();
	}

	@Override
	int o_ParsingPc (final @NotNull AvailObject object)
	{
		return o_Traversed(object).parsingPc();
	}

	@Override
	void o_DisplayTestingTree (final @NotNull AvailObject object)
	{
		o_Traversed(object).displayTestingTree();
	}

	@Override
	@NotNull AvailObject o_EnsureMutable (final @NotNull AvailObject object)
	{
		return o_Traversed(object).ensureMutable();
	}

	@Override
	boolean o_EqualsNull (final @NotNull AvailObject object)
	{
		return o_Traversed(object).equalsNull();
	}

	@Override
	ExecutionState o_ExecutionState (final @NotNull AvailObject object)
	{
		return o_Traversed(object).executionState();
	}

	@Override
	void o_Expand (final @NotNull AvailObject object)
	{
		o_Traversed(object).expand();
	}

	@Override
	boolean o_ExtractBoolean (final @NotNull AvailObject object)
	{
		return o_Traversed(object).extractBoolean();
	}

	@Override
	short o_ExtractUnsignedByte (final @NotNull AvailObject object)
	{
		return o_Traversed(object).extractUnsignedByte();
	}

	@Override
	double o_ExtractDouble (final @NotNull AvailObject object)
	{
		return o_Traversed(object).extractDouble();
	}

	@Override
	float o_ExtractFloat (final @NotNull AvailObject object)
	{
		return o_Traversed(object).extractFloat();
	}

	@Override
	int o_ExtractInt (final @NotNull AvailObject object)
	{
		return o_Traversed(object).extractInt();
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	long o_ExtractLong (final @NotNull AvailObject object)
	{
		return o_Traversed(object).extractLong();
	}

	@Override
	byte o_ExtractNybble (final @NotNull AvailObject object)
	{
		return o_Traversed(object).extractNybble();
	}

	@Override
	@NotNull AvailObject o_FieldMap (final @NotNull AvailObject object)
	{
		return o_Traversed(object).fieldMap();
	}

	@Override
	@NotNull AvailObject o_FieldTypeMap (final @NotNull AvailObject object)
	{
		return o_Traversed(object).fieldTypeMap();
	}

	@Override
	@NotNull AvailObject o_FilteredBundleTree (final @NotNull AvailObject object)
	{
		return o_Traversed(object).filteredBundleTree();
	}

	@Override
	int o_GetInteger (final @NotNull AvailObject object)
	{
		return o_Traversed(object).getInteger();
	}

	@Override
	@NotNull AvailObject o_GetValue (final @NotNull AvailObject object)
	{
		return o_Traversed(object).getValue();
	}

	@Override
	int o_Hash (final @NotNull AvailObject object)
	{
		return o_Traversed(object).hash();
	}

	@Override
	int o_HashOrZero (final @NotNull AvailObject object)
	{
		return o_Traversed(object).hashOrZero();
	}

	@Override
	boolean o_HasGrammaticalRestrictions (final @NotNull AvailObject object)
	{
		return o_Traversed(object).hasGrammaticalRestrictions();
	}

	@Override
	@NotNull AvailObject o_ImplementationsTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).implementationsTuple();
	}

	@Override
	@NotNull AvailObject o_LazyIncomplete (final @NotNull AvailObject object)
	{
		return o_Traversed(object).lazyIncomplete();
	}

	@Override
	int o_Index (final @NotNull AvailObject object)
	{
		return o_Traversed(object).index();
	}

	@Override
	int o_InterruptRequestFlags (final @NotNull AvailObject object)
	{
		return o_Traversed(object).interruptRequestFlags();
	}

	@Override
	int o_InvocationCount (final @NotNull AvailObject object)
	{
		return o_Traversed(object).invocationCount();
	}

	@Override
	boolean o_IsAbstract (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isAbstract();
	}

	@Override
	boolean o_IsBoolean (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isBoolean();
	}

	@Override
	boolean o_IsUnsignedByte (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isUnsignedByte();
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	boolean o_IsByteTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isByteTuple();
	}

	@Override
	boolean o_IsCharacter (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isCharacter();
	}

	@Override
	boolean o_IsFunction (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isFunction();
	}

	@Override
	boolean o_IsAtom (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isAtom();
	}

	@Override
	boolean o_IsExtendedInteger (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isExtendedInteger();
	}

	@Override
	boolean o_IsFinite (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isFinite();
	}

	@Override
	boolean o_IsForward (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isForward();
	}

	@Override
	boolean o_IsInstanceMeta (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isInstanceMeta();
	}

	@Override
	boolean o_IsMethod (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isMethod();
	}

	@Override
	boolean o_IsIntegerRangeType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isIntegerRangeType();
	}

	@Override
	boolean o_IsMap (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isMap();
	}

	@Override
	boolean o_IsMapType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isMapType();
	}

	@Override
	boolean o_IsNybble (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isNybble();
	}

	@Override
	boolean o_IsPositive (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isPositive();
	}

	@Override
	boolean o_IsSaved (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isSaved();
	}

	@Override
	boolean o_IsSet (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isSet();
	}

	@Override
	boolean o_IsSetType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isSetType();
	}

	@Override
	boolean o_IsSplice (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isSplice();
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	boolean o_IsString (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isString();
	}

	@Override
	boolean o_IsSupertypeOfBottom (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isSupertypeOfBottom();
	}

	@Override
	boolean o_IsTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isTuple();
	}

	@Override
	boolean o_IsTupleType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isTupleType();
	}

	@Override
	boolean o_IsType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isType();
	}

	@Override
	boolean o_IsValid (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isValid();
	}

	@Override
	@NotNull AvailObject o_KeysAsSet (final @NotNull AvailObject object)
	{
		return o_Traversed(object).keysAsSet();
	}

	@Override
	@NotNull AvailObject o_KeyType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).keyType();
	}

	@Override
	@NotNull AvailObject o_LevelTwoChunk (final @NotNull AvailObject object)
	{
		return o_Traversed(object).levelTwoChunk();
	}

	@Override
	int o_LevelTwoOffset (final @NotNull AvailObject object)
	{
		return o_Traversed(object).levelTwoOffset();
	}

	@Override
	@NotNull AvailObject o_Literal (final @NotNull AvailObject object)
	{
		return o_Traversed(object).literal();
	}

	@Override
	@NotNull AvailObject o_LowerBound (final @NotNull AvailObject object)
	{
		return o_Traversed(object).lowerBound();
	}

	@Override
	boolean o_LowerInclusive (final @NotNull AvailObject object)
	{
		return o_Traversed(object).lowerInclusive();
	}

	@Override
	void o_MakeSubobjectsImmutable (final @NotNull AvailObject object)
	{
		o_Traversed(object).makeSubobjectsImmutable();
	}

	@Override
	int o_MapSize (final @NotNull AvailObject object)
	{
		return o_Traversed(object).mapSize();
	}

	@Override
	int o_MaxStackDepth (final @NotNull AvailObject object)
	{
		return o_Traversed(object).maxStackDepth();
	}

	@Override
	@NotNull AvailObject o_Message (final @NotNull AvailObject object)
	{
		return o_Traversed(object).message();
	}

	@Override
	@NotNull AvailObject o_MessageParts (final @NotNull AvailObject object)
	{
		return o_Traversed(object).messageParts();
	}

	@Override
	@NotNull AvailObject o_Methods (final @NotNull AvailObject object)
	{
		return o_Traversed(object).methods();
	}

	@Override
	@NotNull AvailObject o_Name (final @NotNull AvailObject object)
	{
		return o_Traversed(object).name();
	}

	@Override
	@NotNull AvailObject o_Names (final @NotNull AvailObject object)
	{
		return o_Traversed(object).names();
	}

	@Override
	@NotNull AvailObject o_NewNames (final @NotNull AvailObject object)
	{
		return o_Traversed(object).newNames();
	}

	@Override
	int o_NumArgs (final @NotNull AvailObject object)
	{
		return o_Traversed(object).numArgs();
	}

	@Override
	int o_NumArgsAndLocalsAndStack (final @NotNull AvailObject object)
	{
		return o_Traversed(object).numArgsAndLocalsAndStack();
	}

	@Override
	int o_NumberOfZones (final @NotNull AvailObject object)
	{
		return o_Traversed(object).numberOfZones();
	}

	@Override
	int o_NumDoubles (final @NotNull AvailObject object)
	{
		return o_Traversed(object).numDoubles();
	}

	@Override
	int o_NumIntegers (final @NotNull AvailObject object)
	{
		return o_Traversed(object).numIntegers();
	}

	@Override
	int o_NumLiterals (final @NotNull AvailObject object)
	{
		return o_Traversed(object).numLiterals();
	}

	@Override
	int o_NumLocals (final @NotNull AvailObject object)
	{
		return o_Traversed(object).numLocals();
	}

	@Override
	int o_NumObjects (final @NotNull AvailObject object)
	{
		return o_Traversed(object).numObjects();
	}

	@Override
	int o_NumOuters (final @NotNull AvailObject object)
	{
		return o_Traversed(object).numOuters();
	}

	@Override
	int o_NumOuterVars (final @NotNull AvailObject object)
	{
		return o_Traversed(object).numOuterVars();
	}

	@Override
	@NotNull AvailObject o_Nybbles (final @NotNull AvailObject object)
	{
		return o_Traversed(object).nybbles();
	}

	@Override
	@NotNull AvailObject o_Parent (final @NotNull AvailObject object)
	{
		return o_Traversed(object).parent();
	}

	@Override
	int o_Pc (final @NotNull AvailObject object)
	{
		return o_Traversed(object).pc();
	}

	@Override @Deprecated
	void o_PostFault (final @NotNull AvailObject object)
	{
		o_Traversed(object).postFault();
	}

	@Override
	int o_PrimitiveNumber (final @NotNull AvailObject object)
	{
		return o_Traversed(object).primitiveNumber();
	}

	@Override
	AvailObject o_Priority (final @NotNull AvailObject object)
	{
		return o_Traversed(object).priority();
	}

	@Override
	@NotNull AvailObject o_PrivateNames (final @NotNull AvailObject object)
	{
		return o_Traversed(object).privateNames();
	}

	@Override
	@NotNull AvailObject o_FiberGlobals (final @NotNull AvailObject object)
	{
		return o_Traversed(object).fiberGlobals();
	}

	@Override
	void o_ReadBarrierFault (final @NotNull AvailObject object)
	{
		o_Traversed(object).readBarrierFault();
	}

	@Override
	@NotNull AvailObject o_GrammaticalRestrictions (final @NotNull AvailObject object)
	{
		return o_Traversed(object).grammaticalRestrictions();
	}

	@Override
	@NotNull AvailObject o_ReturnType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).returnType();
	}

	@Override
	int o_SetSize (final @NotNull AvailObject object)
	{
		return o_Traversed(object).setSize();
	}

	@Override
	@NotNull AvailObject o_Signature (final @NotNull AvailObject object)
	{
		return o_Traversed(object).signature();
	}

	@Override
	@NotNull AvailObject o_SizeRange (final @NotNull AvailObject object)
	{
		return o_Traversed(object).sizeRange();
	}

	@Override
	@NotNull AvailObject o_LazyActions (final @NotNull AvailObject object)
	{
		return o_Traversed(object).lazyActions();
	}

	@Override
	int o_Stackp (final @NotNull AvailObject object)
	{
		return o_Traversed(object).stackp();
	}

	@Override
	int o_Start (final @NotNull AvailObject object)
	{
		return o_Traversed(object).start();
	}

	@Override
	AvailObject o_StartingChunk (final @NotNull AvailObject object)
	{
		return o_Traversed(object).startingChunk();
	}

	@Override
	void o_Step (final @NotNull AvailObject object)
	{
		o_Traversed(object).step();
	}

	@Override
	@NotNull AvailObject o_String (final @NotNull AvailObject object)
	{
		return o_Traversed(object).string();
	}

	@Override
	@NotNull AvailObject o_TestingTree (final @NotNull AvailObject object)
	{
		return o_Traversed(object).testingTree();
	}

	@Override
	TokenDescriptor.TokenType o_TokenType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).tokenType();
	}

	@Override
	void o_TrimExcessInts (final @NotNull AvailObject object)
	{
		o_Traversed(object).trimExcessInts();
	}

	@Override
	int o_TupleSize (final @NotNull AvailObject object)
	{
		return o_Traversed(object).tupleSize();
	}

	@Override
	@NotNull AvailObject o_Kind (final @NotNull AvailObject object)
	{
		return o_Traversed(object).kind();
	}

	@Override
	@NotNull AvailObject o_TypeTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).typeTuple();
	}

	@Override
	@NotNull AvailObject o_Unclassified (final @NotNull AvailObject object)
	{
		return o_Traversed(object).unclassified();
	}

	@Override
	@NotNull AvailObject o_UpperBound (final @NotNull AvailObject object)
	{
		return o_Traversed(object).upperBound();
	}

	@Override
	boolean o_UpperInclusive (final @NotNull AvailObject object)
	{
		return o_Traversed(object).upperInclusive();
	}

	@Override
	@NotNull AvailObject o_Value (final @NotNull AvailObject object)
	{
		return o_Traversed(object).value();
	}

	@Override
	@NotNull AvailObject o_ValuesAsTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).valuesAsTuple();
	}

	@Override
	@NotNull AvailObject o_ValueType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).valueType();
	}

	@Override
	@NotNull AvailObject o_VariableBindings (final @NotNull AvailObject object)
	{
		return o_Traversed(object).variableBindings();
	}

	@Override
	@NotNull AvailObject o_Vectors (final @NotNull AvailObject object)
	{
		return o_Traversed(object).vectors();
	}

	@Override
	void o_Verify (final @NotNull AvailObject object)
	{
		o_Traversed(object).verify();
	}

	@Override
	@NotNull AvailObject o_VisibleNames (final @NotNull AvailObject object)
	{
		return o_Traversed(object).visibleNames();
	}

	@Override
	@NotNull AvailObject o_Wordcodes (final @NotNull AvailObject object)
	{
		return o_Traversed(object).wordcodes();
	}

	@Override
	@NotNull AvailObject o_ParsingInstructions (final @NotNull AvailObject object)
	{
		return o_Traversed(object).parsingInstructions();
	}

	@Override
	void o_Expression (
		final @NotNull AvailObject object,
		final @NotNull AvailObject expression)
	{
		o_Traversed(object).expression(expression);
	}

	@Override
	@NotNull AvailObject o_Expression (final @NotNull AvailObject object)
	{
		return o_Traversed(object).expression();
	}

	@Override
	void o_Variable (
		final @NotNull AvailObject object,
		final @NotNull AvailObject variable)
	{
		o_Traversed(object).variable(variable);
	}

	@Override
	@NotNull AvailObject o_Variable (final @NotNull AvailObject object)
	{
		return o_Traversed(object).variable();
	}

	@Override
	@NotNull AvailObject o_ArgumentsTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).argumentsTuple();
	}

	@Override
	@NotNull AvailObject o_StatementsTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).statementsTuple();
	}

	@Override
	@NotNull AvailObject o_ResultType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).resultType();
	}

	@Override
	void o_NeededVariables (
		final @NotNull AvailObject object,
		final @NotNull AvailObject neededVariables)
	{
		o_Traversed(object).neededVariables(neededVariables);
	}

	@Override
	@NotNull AvailObject o_NeededVariables (final @NotNull AvailObject object)
	{
		return o_Traversed(object).neededVariables();
	}

	@Override
	int o_Primitive (final @NotNull AvailObject object)
	{
		return o_Traversed(object).primitive();
	}

	@Override
	@NotNull AvailObject o_DeclaredType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).declaredType();
	}

	@Override
	DeclarationKind o_DeclarationKind (final @NotNull AvailObject object)
	{
		return o_Traversed(object).declarationKind();
	}

	@Override
	@NotNull AvailObject o_InitializationExpression (final @NotNull AvailObject object)
	{
		return o_Traversed(object).initializationExpression();
	}

	@Override
	void o_InitializationExpression (
		final @NotNull AvailObject object,
		final @NotNull AvailObject initializationExpression)
	{
		o_Traversed(object).initializationExpression(initializationExpression);
	}

	@Override
	@NotNull AvailObject o_LiteralObject (final @NotNull AvailObject object)
	{
		return o_Traversed(object).literalObject();
	}

	@Override
	@NotNull AvailObject o_Token (final @NotNull AvailObject object)
	{
		return o_Traversed(object).token();
	}

	@Override
	@NotNull AvailObject o_MarkerValue (final @NotNull AvailObject object)
	{
		return o_Traversed(object).markerValue();
	}

	@Override
	void o_MarkerValue (
		final @NotNull AvailObject object,
		final @NotNull AvailObject markerValue)
	{
		o_Traversed(object).markerValue(markerValue);
	}

	@Override
	@NotNull AvailObject o_ArgumentsListNode (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).argumentsListNode();
	}

	@Override
	@NotNull AvailObject o_Method (final @NotNull AvailObject object)
	{
		return o_Traversed(object).method();
	}

	@Override
	@NotNull AvailObject o_ExpressionsTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).expressionsTuple();
	}

	@Override
	@NotNull AvailObject o_Declaration (final @NotNull AvailObject object)
	{
		return o_Traversed(object).declaration();
	}

	@Override
	@NotNull AvailObject o_ExpressionType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).expressionType();
	}

	@Override
	void o_EmitEffectOn (
		final @NotNull AvailObject object,
		final @NotNull AvailCodeGenerator codeGenerator)
	{
		o_Traversed(object).emitEffectOn(codeGenerator);
	}

	@Override
	void o_EmitValueOn (
		final @NotNull AvailObject object,
		final @NotNull AvailCodeGenerator codeGenerator)
	{
		o_Traversed(object).emitValueOn(codeGenerator);
	}

	@Override
	void o_ChildrenMap (
		final @NotNull AvailObject object,
		final @NotNull Transformer1<AvailObject, AvailObject> aBlock)
	{
		o_Traversed(object).childrenMap(aBlock);
	}

	@Override
	void o_ChildrenDo (
		final @NotNull AvailObject object,
		final @NotNull Continuation1<AvailObject> aBlock)
	{
		o_Traversed(object).childrenDo(aBlock);
	}

	@Override
	void o_ValidateLocally (
		final @NotNull AvailObject object,
		final @NotNull AvailObject parent)
	{
		o_Traversed(object).validateLocally(parent);
	}

	@Override
	@NotNull AvailObject o_Generate (
		final @NotNull AvailObject object,
		final @NotNull AvailCodeGenerator codeGenerator)
	{
		return o_Traversed(object).generate(codeGenerator);
	}

	@Override
	@NotNull AvailObject o_CopyWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject newParseNode)
	{
		return o_Traversed(object).copyWith(newParseNode);
	}

	@Override
	void o_IsLastUse (
		final @NotNull AvailObject object,
		final boolean isLastUse)
	{
		o_Traversed(object).isLastUse(isLastUse);
	}

	@Override
	boolean o_IsLastUse (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).isLastUse();
	}

	@Override
	boolean o_IsMacro (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).isMacro();
	}

	@Override
	void o_Macros (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).macros(value);
	}

	@Override
	@NotNull AvailObject o_Macros (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).macros();
	}

	@Override
	@NotNull AvailObject o_CopyMutableParseNode (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).copyMutableParseNode();
	}

	@Override
	@NotNull AvailObject o_BinUnionKind (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).binUnionKind();
	}

	@Override
	@NotNull AvailObject o_MacroName (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).macroName();
	}

	@Override
	@NotNull AvailObject o_OutputParseNode (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).outputParseNode();
	}

	@Override
	@NotNull AvailObject o_ApparentSendName (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).apparentSendName();
	}

	@Override
	void o_Statements (
		final @NotNull AvailObject object,
		final AvailObject statementsTuple)
	{
		o_Traversed(object).statements(statementsTuple);
	}

	@Override
	AvailObject o_Statements (final @NotNull AvailObject object)
	{
		return o_Traversed(object).statements();
	}

	@Override
	void o_FlattenStatementsInto (
		final @NotNull AvailObject object,
		final List<AvailObject> accumulatedStatements)
	{
		o_Traversed(object).flattenStatementsInto(accumulatedStatements);
	}

	@Override
	void o_LineNumber (final @NotNull AvailObject object, final int value)
	{
		o_Traversed(object).lineNumber(value);
	}

	@Override
	int o_LineNumber (final @NotNull AvailObject object)
	{
		return o_Traversed(object).lineNumber();
	}

	@Override
	AvailObject o_AllBundles (final @NotNull AvailObject object)
	{
		return o_Traversed(object).allBundles();
	}

	@Override
	boolean o_IsSetBin (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isSetBin();
	}

	@Override
	MapDescriptor.MapIterable o_MapIterable (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).mapIterable();
	}

	@Override
	AvailObject o_Complete (final @NotNull AvailObject object)
	{
		return o_Traversed(object).complete();
	}

	@Override
	AvailObject o_Incomplete (final @NotNull AvailObject object)
	{
		return o_Traversed(object).incomplete();
	}

	@Override
	AvailObject o_Actions (final @NotNull AvailObject object)
	{
		return o_Traversed(object).actions();
	}

	@Override
	@NotNull AvailObject o_DeclaredExceptions (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).declaredExceptions();
	}

	@Override
	boolean o_IsInt (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).isInt();
	}

	@Override
	boolean o_IsLong (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).isLong();
	}

	@Override
	AvailObject o_ArgsTupleType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).argsTupleType();
	}

	@Override
	boolean o_EqualsInstanceTypeFor (
		final @NotNull AvailObject object,
		final AvailObject anObject)
	{
		return o_Traversed(object).equalsInstanceTypeFor(anObject);
	}

	@Override
	AvailObject o_Instances (final @NotNull AvailObject object)
	{
		return o_Traversed(object).instances();
	}

	@Override
	boolean o_EqualsEnumerationWithSet (
		final @NotNull AvailObject object,
		final AvailObject aSet)
	{
		return o_Traversed(object).equalsEnumerationWithSet(aSet);
	}

	@Override
	boolean o_IsEnumeration (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isEnumeration();
	}

	@Override
	boolean o_IsInstanceOf (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		return o_Traversed(object).isInstanceOf(aType);
	}

	@Override
	boolean o_EnumerationIncludesInstance (
		final @NotNull AvailObject object,
		final AvailObject potentialInstance)
	{
		return o_Traversed(object).enumerationIncludesInstance(
			potentialInstance);
	}

	@Override
	AvailObject o_ComputeSuperkind (final @NotNull AvailObject object)
	{
		return o_Traversed(object).computeSuperkind();
	}

	@Override
	void o_SetAtomProperty (
		final @NotNull AvailObject object,
		final AvailObject key,
		final AvailObject value)
	{
		o_Traversed(object).setAtomProperty(key, value);
	}

	@Override
	AvailObject o_GetAtomProperty (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key)
	{
		return o_Traversed(object).getAtomProperty(key);
	}

	@Override
	boolean o_EqualsEnumerationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).equalsEnumerationType(another);
	}

	@Override
	@NotNull AvailObject o_ReadType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).readType();
	}

	@Override
	@NotNull AvailObject o_WriteType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).writeType();
	}

	@Override
	void o_Versions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).versions(value);
	}

	@Override
	@NotNull AvailObject o_Versions (final @NotNull AvailObject object)
	{
		return o_Traversed(object).versions();
	}

	@Override
	boolean o_EqualsParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType)
	{
		return o_Traversed(object).equalsParseNodeType(aParseNodeType);
	}

	@Override
	ParseNodeKind o_ParseNodeKind (final @NotNull AvailObject object)
	{
		return o_Traversed(object).parseNodeKind();
	}

	@Override
	boolean o_ParseNodeKindIsUnder (
		final @NotNull AvailObject object,
		final @NotNull ParseNodeKind expectedParseNodeKind)
	{
		return o_Traversed(object).parseNodeKindIsUnder(expectedParseNodeKind);
	}

	@Override
	boolean o_IsRawPojo (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isRawPojo();
	}

	@Override
	void o_AddTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject restrictionSignature)
	{
		o_Traversed(object).addTypeRestriction(restrictionSignature);
	}

	@Override
	void o_RemoveTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject restrictionSignature)
	{
		o_Traversed(object).removeTypeRestriction(restrictionSignature);
	}

	@Override
	@NotNull AvailObject o_TypeRestrictions (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).typeRestrictions();
	}

	@Override
	void o_AddSealedArgumentsType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType)
	{
		o_Traversed(object).addSealedArgumentsType(tupleType);
	}

	@Override
	void o_RemoveSealedArgumentsType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType)
	{
		o_Traversed(object).removeSealedArgumentsType(tupleType);
	}

	@Override
	final AvailObject o_SealedArgumentsTypesTuple (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).sealedArgumentsTypesTuple();
	}

	@Override
	void o_AddTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodNameAtom,
		final @NotNull AvailObject typeRestrictionFunction)
	{
		o_Traversed(object).addTypeRestriction(
			methodNameAtom,
			typeRestrictionFunction);
	}

	@Override
	void o_AddConstantBinding (
		final @NotNull AvailObject object,
		final @NotNull AvailObject name,
		final @NotNull AvailObject constantBinding)
	{
		o_Traversed(object).addConstantBinding(
			name,
			constantBinding);
	}

	@Override
	void o_AddVariableBinding (
		final @NotNull AvailObject object,
		final @NotNull AvailObject name,
		final @NotNull AvailObject variableBinding)
	{
		o_Traversed(object).addVariableBinding(
			name,
			variableBinding);
	}

	@Override
	boolean o_IsMethodEmpty (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).isMethodEmpty();
	}

	@Override
	boolean o_IsPojoSelfType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isPojoSelfType();
	}

	@Override
	@NotNull AvailObject o_PojoSelfType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).pojoSelfType();
	}

	@Override
	@NotNull AvailObject o_JavaClass (final @NotNull AvailObject object)
	{
		return o_Traversed(object).javaClass();
	}

	@Override
	boolean o_IsUnsignedShort (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isUnsignedShort();
	}

	@Override
	int o_ExtractUnsignedShort (final @NotNull AvailObject object)
	{
		return o_Traversed(object).extractUnsignedShort();
	}

	@Override
	boolean o_IsFloat (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isFloat();
	}

	@Override
	boolean o_IsDouble (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isDouble();
	}

	@Override
	@NotNull AvailObject o_RawPojo (final @NotNull AvailObject object)
	{
		return o_Traversed(object).rawPojo();
	}

	@Override
	boolean o_IsPojo (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isPojo();
	}

	@Override
	boolean o_IsPojoType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isPojoType();
	}

	@Override
	public AvailObject o_UpperBoundMap (final @NotNull AvailObject object)
	{
		return o_Traversed(object).upperBoundMap();
	}

	@Override
	public void o_UpperBoundMap (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMap)
	{
		o_Traversed(object).upperBoundMap(aMap);
	}

	@Override
	@NotNull Order o_NumericCompare (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).numericCompare(another);
	}

	@Override
	@NotNull Order o_NumericCompareToDouble (
		final @NotNull AvailObject object,
		final double aDouble)
	{
		return o_Traversed(object).numericCompareToDouble(aDouble);
	}

	@Override
	@NotNull AvailObject o_AddToDoubleCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_AddToFloatCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_SubtractFromDoubleCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_SubtractFromFloatCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_MultiplyByDoubleCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_MultiplyByFloatCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_DivideIntoDoubleCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_DivideIntoFloatCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	@NotNull AvailObject o_LazyPrefilterMap (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).lazyPrefilterMap();
	}

	@Override
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).serializerOperation();
	}

	/**
	 * @param object
	 * @param key
	 * @param keyHash
	 * @param value
	 * @param myLevel
	 * @param canDestroy
	 * @return
	 */
	@Override
	AvailObject o_MapBinAtHashPutLevelCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final int keyHash,
		final @NotNull AvailObject value,
		final byte myLevel,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapBinAtHashPutLevelCanDestroy(
			key,
			keyHash,
			value,
			myLevel,
			canDestroy);
	}

	/**
	 * @param object
	 * @param key
	 * @param keyHash
	 * @param canDestroy
	 * @return
	 */
	@Override
	@NotNull AvailObject o_MapBinRemoveKeyHashCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final int keyHash,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapBinRemoveKeyHashCanDestroy(
			key,
			keyHash,
			canDestroy);
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	@NotNull AvailObject o_MapBinKeyUnionKind (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).mapBinKeyUnionKind();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	@NotNull AvailObject o_MapBinValueUnionKind (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).mapBinValueUnionKind();
	}

	@Override
	boolean o_IsHashedMapBin (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).isHashedMapBin();
	}

	@Override
	@NotNull AvailObject o_MapBinAtHash (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final int keyHash)
	{
		return o_Traversed(object).mapBinAtHash(key, keyHash);
	}

	@Override
	int o_MapBinKeysHash (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).mapBinKeysHash();
	}

	@Override
	int o_MapBinValuesHash (final @NotNull AvailObject object)
	{
		return o_Traversed(object).mapBinValuesHash();
	}

	@Override
	AvailObject o_IssuingModule (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).issuingModule();
	}

	@Override
	boolean o_IsPojoFusedType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isPojoFusedType();
	}

	@Override
	boolean o_IsSupertypeOfPojoBottomType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return o_Traversed(object).isSupertypeOfPojoBottomType(aPojoType);
	}

	@Override
	boolean o_EqualsPojoBottomType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).equalsPojoBottomType();
	}

	@Override
	@NotNull AvailObject o_JavaAncestors (final @NotNull AvailObject object)
	{
		return o_Traversed(object).javaAncestors();
	}

	/**
	 * @param object
	 * @param aFusedPojoType
	 * @return
	 */
	@Override
	@NotNull AvailObject o_TypeIntersectionOfPojoFusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFusedPojoType)
	{
		return o_Traversed(object).typeIntersectionOfPojoFusedType(
			aFusedPojoType);
	}

	/**
	 * @param object
	 * @param anUnfusedPojoType
	 * @return
	 */
	@Override
	@NotNull AvailObject o_TypeIntersectionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType)
	{
		return o_Traversed(object).typeIntersectionOfPojoUnfusedType(
			anUnfusedPojoType);
	}

	/**
	 * @param object
	 * @param aFusedPojoType
	 * @return
	 */
	@Override
	@NotNull AvailObject o_TypeUnionOfPojoFusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFusedPojoType)
	{
		return o_Traversed(object).typeUnionOfPojoFusedType(
			aFusedPojoType);
	}

	/**
	 * @param object
	 * @param anUnfusedPojoType
	 * @return
	 */
	@Override
	@NotNull AvailObject o_TypeUnionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType)
	{
		return o_Traversed(object).typeUnionOfPojoUnfusedType(
			anUnfusedPojoType);
	}

	@Override
	boolean o_IsPojoArrayType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isPojoArrayType();
	}

	@Override
	Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> classHint)
	{
		return o_Traversed(object).marshalToJava(classHint);
	}

	@Override
	@NotNull AvailObject o_TypeVariables (final @NotNull AvailObject object)
	{
		return o_Traversed(object).typeVariables();
	}

	@Override
	boolean o_EqualsPojoField (
		final @NotNull AvailObject object,
		final @NotNull AvailObject field,
		final @NotNull AvailObject receiver)
	{
		return o_Traversed(object).equalsPojoField(field, receiver);
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	boolean o_IsSignedByte (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isSignedByte();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	boolean o_IsSignedShort (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isSignedShort();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	byte o_ExtractSignedByte (final @NotNull AvailObject object)
	{
		return o_Traversed(object).extractSignedByte();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	short o_ExtractSignedShort (final @NotNull AvailObject object)
	{
		return o_Traversed(object).extractSignedShort();
	}

	/**
	 * @param object
	 * @param aRawPojo
	 * @return
	 */
	@Override
	boolean o_EqualsEqualityRawPojo (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aRawPojo)
	{
		return o_Traversed(object).equalsEqualityRawPojo(aRawPojo);
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	Object o_JavaObject (final @NotNull AvailObject object)
	{
		return o_Traversed(object).javaObject();
	}

	@Override
	@NotNull BigInteger o_AsBigInteger (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).asBigInteger();
	}

	@Override
	@NotNull AvailObject o_AppendCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject newElement,
		final boolean canDestroy)
	{
		return o_Traversed(object).appendCanDestroy(newElement, canDestroy);
	}

	@Override
	@NotNull AvailObject o_LazyIncompleteCaseInsensitive (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).lazyIncompleteCaseInsensitive();
	}

	@Override
	@NotNull AvailObject o_LowerCaseString (final @NotNull AvailObject object)
	{
		return o_Traversed(object).lowerCaseString();
	}

	@Override
	@NotNull AvailObject o_InstanceCount (final @NotNull AvailObject object)
	{
		return o_Traversed(object).instanceCount();
	}

	@Override
	long o_TotalInvocations (final @NotNull AvailObject object)
	{
		return o_Traversed(object).totalInvocations();
	}

	@Override
	void o_TallyInvocation (final @NotNull AvailObject object)
	{
		o_Traversed(object).tallyInvocation();
	}

	@Override
	@NotNull AvailObject o_FieldTypeTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).fieldTypeTuple();
	}

	@Override
	@NotNull AvailObject o_FieldTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).fieldTuple();
	}

	@Override
	void o_ClearInterruptRequestFlags (final @NotNull AvailObject object)
	{
		o_Traversed(object).clearInterruptRequestFlags();
	}

	@Override
	boolean o_IsSystemModule (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isSystemModule();
	}

	@Override
	AvailObject o_LiteralType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).literalType();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfLiteralTokenType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aLiteralTokenType)
	{
		return o_Traversed(object).typeIntersectionOfLiteralTokenType(
			aLiteralTokenType);
	}

	@Override
	AvailObject o_TypeUnionOfLiteralTokenType (
		final @NotNull AvailObject object,
		final AvailObject aLiteralTokenType)
	{
		return o_Traversed(object).typeUnionOfLiteralTokenType(
			aLiteralTokenType);
	}

	@Override
	boolean o_IsLiteralTokenType (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isLiteralTokenType();
	}

	@Override
	boolean o_IsLiteralToken (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isLiteralToken();
	}

	@Override
	boolean o_IsSupertypeOfLiteralTokenType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aLiteralTokenType)
	{
		return o_Traversed(object).isSupertypeOfLiteralTokenType(
			aLiteralTokenType);
	}

	@Override
	boolean o_EqualsLiteralTokenType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aLiteralTokenType)
	{
		return o_Traversed(object).equalsLiteralTokenType(aLiteralTokenType);
	}

	@Override
	boolean o_EqualsObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		return o_Traversed(object).equalsObjectType(anObjectType);
	}

	@Override
	boolean o_EqualsToken (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aToken)
	{
		return o_Traversed(object).equalsToken(aToken);
	}

	@Override
	@NotNull AvailObject o_BitwiseAnd (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitwiseAnd(anInteger, canDestroy);
	}

	@Override
	@NotNull AvailObject o_BitwiseOr (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitwiseOr(anInteger, canDestroy);
	}

	@Override
	@NotNull AvailObject o_BitwiseXor (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitwiseXor(anInteger, canDestroy);
	}

	@Override
	void o_AddSeal (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject sealSignature)
	{
		o_Traversed(object).addSeal(methodName, sealSignature);
	}

	@Override
	@NotNull AvailObject o_Instance (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).instance();
	}

	@Override
	int o_AllocateFromCounter (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).allocateFromCounter();
	}

	@Override
	void o_SetMethodName (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodName)
	{
		o_Traversed(object).setMethodName(methodName);
	}

	@Override
	int o_StartingLineNumber (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).startingLineNumber();
	}

	@Override
	@NotNull AvailObject o_Module (final @NotNull AvailObject object)
	{
		return o_Traversed(object).module();
	}

	@Override
	@NotNull AvailObject o_MethodName (final @NotNull AvailObject object)
	{
		return o_Traversed(object).methodName();
	}

	@Override
	String o_NameForDebugger (final @NotNull AvailObject object)
	{
		final String name = o_Traversed(object).nameForDebugger();
		return "IND→" + name;
	}

	@Override
	@NotNull boolean o_BinElementsAreAllInstancesOfKind (
		final @NotNull AvailObject object,
		final @NotNull AvailObject kind)
	{
		return o_Traversed(object).binElementsAreAllInstancesOfKind(kind);
	}
}
