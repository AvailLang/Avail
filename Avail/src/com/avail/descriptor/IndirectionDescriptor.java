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

import static com.avail.descriptor.IndirectionDescriptor.ObjectSlots.*;
import java.math.BigInteger;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.AbstractNumberDescriptor.Order;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.MapDescriptor.MapIterable;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.SetDescriptor.SetIterator;
import com.avail.descriptor.TypeDescriptor.Types;
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
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
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
	enum IntegerSlots
	implements IntegerSlotsEnum
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
		return e == INDIRECTION_TARGET;
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		object.traversed().printOnAvoidingIndent(
			aStream,
			recursionList,
			indent);
	}

	@Override
	void o_ScanSubobjects (
		final AvailObject object,
		final AvailSubobjectVisitor visitor)
	{
		// Manually constructed scanning method.

		visitor.invoke(object, object.slot(INDIRECTION_TARGET));
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
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable)
		{
			object.descriptor = immutable();
			return object.slot(INDIRECTION_TARGET).makeImmutable();
		}
		return object.slot(INDIRECTION_TARGET);
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
	AvailObject o_Traversed (final AvailObject object)
	{
		final AvailObject next = object.slot(INDIRECTION_TARGET);
		final AvailObject finalObject = next.traversed();
		if (!next.sameAddressAs(object))
		{
			object.setSlot(INDIRECTION_TARGET, finalObject);
		}
		return finalObject;
	}

	/**
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	Iterator<AvailObject> o_Iterator (final AvailObject object)
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
		final AvailObject object,
		final AvailObject functionType)
	{
		return o_Traversed(object).acceptsArgTypesFromFunctionType(functionType);
	}

	@Override
	boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		return o_Traversed(object).acceptsListOfArgTypes(argTypes);
	}

	@Override
	boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<AvailObject> argValues)
	{
		return o_Traversed(object).acceptsListOfArgValues(argValues);
	}

	@Override
	boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final AvailObject argTypes)
	{
		return o_Traversed(object).acceptsTupleOfArgTypes(argTypes);
	}

	@Override
	boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final AvailObject arguments)
	{
		return o_Traversed(object).acceptsTupleOfArguments(arguments);
	}

	@Override
	void o_AddDependentChunkIndex (
		final AvailObject object,
		final int aChunkIndex)
	{
		o_Traversed(object).addDependentChunkIndex(aChunkIndex);
	}

	@Override
	void o_AddDefinition (
		final AvailObject object,
		final AvailObject definition)
	throws SignatureException
	{
		o_Traversed(object).addDefinition(definition);
	}

	@Override
	void o_AddGrammaticalRestrictions (
		final AvailObject object,
		final AvailObject restrictions)
	{
		o_Traversed(object).addGrammaticalRestrictions(restrictions);
	}

	@Override
	AvailObject o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	AvailObject o_AddToIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object)
				.addToIntegerCanDestroy(anInteger, canDestroy);
	}

	@Override
	void o_AddGrammaticalMessageRestrictions (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject illegalArgMsgs)
	{
		o_Traversed(object).addGrammaticalRestrictions(
			methodName,
			illegalArgMsgs);
	}

	@Override
	void o_AddMethodDefinition (
		final AvailObject object,
		final AvailObject definition)
	{
		o_Traversed(object).addMethodDefinition(
			definition);
	}

	@Override
	void o_AtMessageAddBundle (
		final AvailObject object,
		final AvailObject message,
		final AvailObject bundle)
	{
		o_Traversed(object).atMessageAddBundle(message, bundle);
	}

	@Override
	void o_AtNameAdd (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		o_Traversed(object).atNameAdd(stringName, trueName);
	}

	@Override
	void o_AtNewNamePut (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		o_Traversed(object).atNewNamePut(stringName, trueName);
	}

	@Override
	void o_AtPrivateNameAdd (
		final AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName)
	{
		o_Traversed(object).atPrivateNameAdd(stringName, trueName);
	}

	@Override
	AvailObject o_SetBinAddingElementHashLevelCanDestroy (
		final AvailObject object,
		final AvailObject elementObject,
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
	AvailObject o_BinElementAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).binElementAt(index);
	}

	@Override
	void o_BinElementAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		o_Traversed(object).binElementAtPut(index, value);
	}

	@Override
	boolean o_BinHasElementWithHash (
		final AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash)
	{
		return o_Traversed(object).binHasElementWithHash(
			elementObject,
			elementObjectHash);
	}

	@Override
	void o_BinHash (final AvailObject object, final int value)
	{
		o_Traversed(object).binHash(value);
	}

	@Override
	AvailObject o_BinRemoveElementHashCanDestroy (
		final AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{
		return o_Traversed(object).binRemoveElementHashCanDestroy(
			elementObject,
			elementObjectHash,
			canDestroy);
	}

	@Override
	void o_BinSize (final AvailObject object, final int value)
	{
		o_Traversed(object).binSize(value);
	}

	@Override
	void o_BinUnionTypeOrNull (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).binUnionTypeOrNull(value);
	}

	@Override
	void o_BitVector (final AvailObject object, final int value)
	{
		o_Traversed(object).bitVector(value);
	}

	@Override
	void o_BreakpointBlock (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).breakpointBlock(value);
	}

	@Override
	void o_BuildFilteredBundleTreeFrom (
		final AvailObject object,
		final AvailObject bundleTree)
	{
		o_Traversed(object).buildFilteredBundleTreeFrom(bundleTree);
	}

	@Override
	void o_Caller (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).caller(value);
	}

	@Override
	void o_Function (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).function(value);
	}

	@Override
	void o_Code (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).code(value);
	}

	@Override
	void o_CodePoint (final AvailObject object, final int value)
	{
		o_Traversed(object).codePoint(value);
	}

	@Override
	boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anotherObject,
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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTuple,
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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteString,
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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteTuple,
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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aNybbleTuple,
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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anObjectTuple,
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
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTwoByteString,
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
		final AvailObject object,
		final int start,
		final int end)
	{
		return o_Traversed(object).computeHashFromTo(start, end);
	}

	@Override
	AvailObject o_ConcatenateTuplesCanDestroy (
		final AvailObject object,
		final boolean canDestroy)
	{
		return o_Traversed(object).concatenateTuplesCanDestroy(canDestroy);
	}

	@Override
	boolean o_ContainsBlock (
		final AvailObject object,
		final AvailObject aFunction)
	{
		return o_Traversed(object).containsBlock(aFunction);
	}

	@Override
	void o_Continuation (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).continuation(value);
	}

	@Override
	void o_CopyToRestrictedTo (
		final AvailObject object,
		final AvailObject filteredBundleTree,
		final AvailObject visibleNames)
	{
		o_Traversed(object)
				.copyToRestrictedTo(filteredBundleTree, visibleNames);
	}

	@Override
	AvailObject o_CopyTupleFromToCanDestroy (
		final AvailObject object,
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
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		return o_Traversed(object).couldEverBeInvokedWith(argTypes);
	}

	@Override
	AvailObject o_DivideCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideCanDestroy(aNumber, canDestroy);
	}

	@Override
	AvailObject o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	AvailObject o_DivideIntoIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	AvailObject o_ElementAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).elementAt(index);
	}

	@Override
	void o_ElementAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		o_Traversed(object).elementAtPut(index, value);
	}

	@Override
	int o_EndOfZone (final AvailObject object, final int zone)
	{
		return o_Traversed(object).endOfZone(zone);
	}

	@Override
	int o_EndSubtupleIndexInZone (
		final AvailObject object,
		final int zone)
	{
		return o_Traversed(object).endSubtupleIndexInZone(zone);
	}

	@Override
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return o_Traversed(object).equals(another);
	}

	@Override
	boolean o_EqualsAnyTuple (
		final AvailObject object,
		final AvailObject anotherTuple)
	{
		return o_Traversed(object).equalsAnyTuple(anotherTuple);
	}

	@Override
	boolean o_EqualsByteString (
		final AvailObject object,
		final AvailObject aByteString)
	{
		return o_Traversed(object).equalsByteString(aByteString);
	}

	@Override
	boolean o_EqualsByteTuple (
		final AvailObject object,
		final AvailObject aByteTuple)
	{
		return o_Traversed(object).equalsByteTuple(aByteTuple);
	}

	@Override
	boolean o_EqualsCharacterWithCodePoint (
		final AvailObject object,
		final int otherCodePoint)
	{
		return o_Traversed(object).equalsCharacterWithCodePoint(otherCodePoint);
	}

	@Override
	boolean o_EqualsFunction (
		final AvailObject object,
		final AvailObject aFunction)
	{
		return o_Traversed(object).equalsFunction(aFunction);
	}

	@Override
	boolean o_EqualsFunctionType (
		final AvailObject object,
		final AvailObject aFunctionType)
	{
		return o_Traversed(object).equalsFunctionType(aFunctionType);
	}

	@Override
	boolean o_EqualsCompiledCode (
		final AvailObject object,
		final AvailObject aCompiledCode)
	{
		return o_Traversed(object).equalsCompiledCode(aCompiledCode);
	}

	@Override
	boolean o_EqualsVariable (
		final AvailObject object,
		final AvailObject aVariable)
	{
		return o_Traversed(object).equalsVariable(aVariable);
	}

	@Override
	boolean o_EqualsVariableType (
		final AvailObject object,
		final AvailObject aVariableType)
	{
		return o_Traversed(object).equalsVariableType(aVariableType);
	}

	@Override
	boolean o_EqualsContinuation (
		final AvailObject object,
		final AvailObject aContinuation)
	{
		return o_Traversed(object).equalsContinuation(aContinuation);
	}

	@Override
	boolean o_EqualsContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		return o_Traversed(object).equalsContinuationType(aContinuationType);
	}

	@Override
	boolean o_EqualsCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		return o_Traversed(object).equalsCompiledCodeType(aCompiledCodeType);
	}

	@Override
	boolean o_EqualsDouble (
		final AvailObject object,
		final double aDouble)
	{
		return o_Traversed(object).equalsDouble(aDouble);
	}

	@Override
	boolean o_EqualsFloat (
		final AvailObject object,
		final float aFloat)
	{
		return o_Traversed(object).equalsFloat(aFloat);
	}

	@Override
	boolean o_EqualsInfinity (
		final AvailObject object,
		final Sign sign)
	{
		return o_Traversed(object).equalsInfinity(sign);
	}

	@Override
	boolean o_EqualsInteger (
		final AvailObject object,
		final AvailObject anAvailInteger)
	{
		return o_Traversed(object).equalsInteger(anAvailInteger);
	}

	@Override
	boolean o_EqualsIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		return o_Traversed(object).equalsIntegerRangeType(anIntegerRangeType);
	}

	@Override
	boolean o_EqualsMap (
		final AvailObject object,
		final AvailObject aMap)
	{
		return o_Traversed(object).equalsMap(aMap);
	}

	@Override
	boolean o_EqualsMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		return o_Traversed(object).equalsMapType(aMapType);
	}

	@Override
	boolean o_EqualsNybbleTuple (
		final AvailObject object,
		final AvailObject aNybbleTuple)
	{
		return o_Traversed(object).equalsNybbleTuple(aNybbleTuple);
	}

	@Override
	boolean o_EqualsObject (
		final AvailObject object,
		final AvailObject anObject)
	{
		return o_Traversed(object).equalsObject(anObject);
	}

	@Override
	boolean o_EqualsObjectTuple (
		final AvailObject object,
		final AvailObject anObjectTuple)
	{
		return o_Traversed(object).equalsObjectTuple(anObjectTuple);
	}

	@Override
	boolean o_EqualsPojo (
		final AvailObject object,
		final AvailObject aRawPojo)
	{
		return o_Traversed(object).equalsPojo(aRawPojo);
	}

	@Override
	boolean o_EqualsPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		return o_Traversed(object).equalsPojoType(aPojoType);
	}

	@Override
	boolean o_EqualsPrimitiveType (
		final AvailObject object,
		final AvailObject aPrimitiveType)
	{
		return o_Traversed(object).equalsPrimitiveType(aPrimitiveType);
	}

	@Override
	boolean o_EqualsRawPojo (
		final AvailObject object,
		final AvailObject aPojo)
	{
		return o_Traversed(object).equalsRawPojo(aPojo);
	}

	@Override
	boolean o_EqualsSet (
		final AvailObject object,
		final AvailObject aSet)
	{
		return o_Traversed(object).equalsSet(aSet);
	}

	@Override
	boolean o_EqualsSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		return o_Traversed(object).equalsSetType(aSetType);
	}

	@Override
	boolean o_EqualsTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		return o_Traversed(object).equalsTupleType(aTupleType);
	}

	@Override
	boolean o_EqualsTwoByteString (
		final AvailObject object,
		final AvailObject aTwoByteString)
	{
		return o_Traversed(object).equalsTwoByteString(aTwoByteString);
	}

	@Override
	void o_ExecutionState (
		final AvailObject object,
		final ExecutionState value)
	{
		o_Traversed(object).executionState(value);
	}

	@Override
	byte o_ExtractNybbleFromTupleAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).extractNybbleFromTupleAt(index);
	}

	@Override
	List<AvailObject> o_FilterByTypes (
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		return o_Traversed(object).filterByTypes(argTypes);
	}

	@Override
	AvailObject o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone (
		final AvailObject object,
		final int zone,
		final AvailObject newSubtuple,
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
	Order o_NumericCompareToInteger (
		final AvailObject object,
		final AvailObject anInteger)
	{
		return o_Traversed(object).numericCompareToInteger(anInteger);
	}

	@Override
	Order o_NumericCompareToInfinity (
		final AvailObject object,
		final Sign sign)
	{
		return o_Traversed(object).numericCompareToInfinity(sign);
	}

	@Override
	boolean o_HasElement (
		final AvailObject object,
		final AvailObject elementObject)
	{
		return o_Traversed(object).hasElement(elementObject);
	}

	@Override
	void o_Hash (final AvailObject object, final int value)
	{
		o_Traversed(object).hash(value);
	}

	@Override
	int o_HashFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		return o_Traversed(object).hashFromTo(startIndex, endIndex);
	}

	@Override
	void o_HashOrZero (final AvailObject object, final int value)
	{
		o_Traversed(object).hashOrZero(value);
	}

	@Override
	boolean o_HasKey (
		final AvailObject object,
		final AvailObject keyObject)
	{
		return o_Traversed(object).hasKey(keyObject);
	}

	@Override
	boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return o_Traversed(object).hasObjectInstance(potentialInstance);
	}

	@Override
	List<AvailObject> o_DefinitionsAtOrBelow (
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		return o_Traversed(object).definitionsAtOrBelow(argTypes);
	}

	@Override
	AvailObject o_IncludeBundle (
		final AvailObject object,
		final AvailObject messageBundle)
	{
		return o_Traversed(object).includeBundle(
			messageBundle);
	}

	@Override
	boolean o_IncludesDefinition (
		final AvailObject object,
		final AvailObject definition)
	{
		return o_Traversed(object).includesDefinition(definition);
	}

	@Override
	void o_Index (final AvailObject object, final int value)
	{
		o_Traversed(object).index(value);
	}

	@Override
	void o_SetInterruptRequestFlag (
		final AvailObject object,
		final BitField value)
	{
		o_Traversed(object).setInterruptRequestFlag(value);
	}

	@Override
	void o_CountdownToReoptimize (final AvailObject object, final int value)
	{
		o_Traversed(object).countdownToReoptimize(value);
	}

	@Override
	boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final AvailObject anotherObject)
	{
		return o_Traversed(object).isBetterRepresentationThan(anotherObject);
	}

	@Override
	boolean o_IsBetterRepresentationThanTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		return o_Traversed(object).isBetterRepresentationThanTupleType(
			aTupleType);
	}

	@Override
	boolean o_IsBinSubsetOf (
		final AvailObject object,
		final AvailObject potentialSuperset)
	{
		return o_Traversed(object).isBinSubsetOf(potentialSuperset);
	}

	@Override
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final AvailObject aType)
	{
		return o_Traversed(object).isInstanceOfKind(aType);
	}

	@Override
	void o_IsSaved (final AvailObject object, final boolean aBoolean)
	{
		o_Traversed(object).isSaved(aBoolean);
	}

	@Override
	boolean o_IsSubsetOf (
		final AvailObject object,
		final AvailObject another)
	{
		return o_Traversed(object).isSubsetOf(another);
	}

	@Override
	boolean o_IsSubtypeOf (
		final AvailObject object,
		final AvailObject aType)
	{
		return o_Traversed(object).isSubtypeOf(aType);
	}

	@Override
	boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final AvailObject aVariableType)
	{
		return o_Traversed(object).isSupertypeOfVariableType(aVariableType);
	}

	@Override
	boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		return o_Traversed(object).isSupertypeOfContinuationType(
			aContinuationType);
	}

	@Override
	boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		return o_Traversed(object).isSupertypeOfCompiledCodeType(
			aCompiledCodeType);
	}

	@Override
	boolean o_IsSupertypeOfFunctionType (
		final AvailObject object,
		final AvailObject aFunctionType)
	{
		return o_Traversed(object).isSupertypeOfFunctionType(aFunctionType);
	}

	@Override
	boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		return o_Traversed(object).isSupertypeOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		return o_Traversed(object).isSupertypeOfMapType(aMapType);
	}

	@Override
	boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return o_Traversed(object).isSupertypeOfObjectType(anObjectType);
	}

	@Override
	boolean o_IsSupertypeOfParseNodeType (
		final AvailObject object,
		final AvailObject aParseNodeType)
	{
		return o_Traversed(object).isSupertypeOfParseNodeType(aParseNodeType);
	}

	@Override
	boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		return o_Traversed(object).isSupertypeOfPojoType(aPojoType);
	}

	@Override
	boolean o_IsSupertypeOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return o_Traversed(object).isSupertypeOfPrimitiveTypeEnum(
			primitiveTypeEnum);
	}

	@Override
	boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		return o_Traversed(object).isSupertypeOfSetType(aSetType);
	}

	@Override
	boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		return o_Traversed(object).isSupertypeOfTupleType(aTupleType);
	}

	@Override
	boolean o_IsSupertypeOfEnumerationType (
		final AvailObject object,
		final AvailObject anEnumerationType)
	{
		return o_Traversed(object).isSupertypeOfEnumerationType(
			anEnumerationType);
	}

	@Override
	void o_LevelTwoChunkOffset (
		final AvailObject object,
		final AvailObject chunk,
		final int offset)
	{
		o_Traversed(object).levelTwoChunkOffset(chunk, offset);
	}

	@Override
	AvailObject o_LiteralAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).literalAt(index);
	}

	@Override
	AvailObject o_ArgOrLocalOrStackAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).argOrLocalOrStackAt(index);
	}

	@Override
	void o_ArgOrLocalOrStackAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		o_Traversed(object).argOrLocalOrStackAtPut(index, value);
	}

	@Override
	AvailObject o_LocalTypeAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).localTypeAt(index);
	}

	@Override
	AvailObject o_LookupByTypesFromTuple (
		final AvailObject object,
		final AvailObject argumentTypeTuple)
	{
		return o_Traversed(object).lookupByTypesFromTuple(argumentTypeTuple);
	}

	@Override
	AvailObject o_LookupByValuesFromList (
		final AvailObject object,
		final List<AvailObject> argumentList)
	{
		return o_Traversed(object).lookupByValuesFromList(argumentList);
	}

	@Override
	AvailObject o_LookupByValuesFromTuple (
		final AvailObject object,
		final AvailObject argumentTuple)
	{
		return o_Traversed(object).lookupByValuesFromTuple(argumentTuple);
	}

	@Override
	AvailObject o_MapAt (
		final AvailObject object,
		final AvailObject keyObject)
	{
		return o_Traversed(object).mapAt(keyObject);
	}

	@Override
	AvailObject o_MapAtPuttingCanDestroy (
		final AvailObject object,
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapAtPuttingCanDestroy(
			keyObject,
			newValueObject,
			canDestroy);
	}

	@Override
	AvailObject o_MapWithoutKeyCanDestroy (
		final AvailObject object,
		final AvailObject keyObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapWithoutKeyCanDestroy(
			keyObject,
			canDestroy);
	}

	@Override
	AvailObject o_MinusCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).minusCanDestroy(aNumber, canDestroy);
	}

	@Override
	AvailObject o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	AvailObject o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	void o_Name (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).name(value);
	}

	@Override
	boolean o_NameVisible (
		final AvailObject object,
		final AvailObject trueName)
	{
		return o_Traversed(object).nameVisible(trueName);
	}

	@Override
	boolean o_OptionallyNilOuterVar (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).optionallyNilOuterVar(index);
	}

	@Override
	AvailObject o_OuterTypeAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).outerTypeAt(index);
	}

	@Override
	AvailObject o_OuterVarAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).outerVarAt(index);
	}

	@Override
	void o_OuterVarAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		o_Traversed(object).outerVarAtPut(index, value);
	}

	@Override
	void o_Parent (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).parent(value);
	}

	@Override
	void o_Pc (final AvailObject object, final int value)
	{
		o_Traversed(object).pc(value);
	}

	@Override
	AvailObject o_PlusCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).plusCanDestroy(aNumber, canDestroy);
	}

	@Override
	void o_Priority (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).priority(value);
	}

	@Override
	AvailObject o_PrivateAddElement (
		final AvailObject object,
		final AvailObject element)
	{
		return o_Traversed(object).privateAddElement(element);
	}

	@Override
	AvailObject o_PrivateExcludeElement (
		final AvailObject object,
		final AvailObject element)
	{
		return o_Traversed(object).privateExcludeElement(element);
	}

	@Override
	AvailObject o_PrivateExcludeElementKnownIndex (
		final AvailObject object,
		final AvailObject element,
		final int knownIndex)
	{
		return o_Traversed(object).privateExcludeElementKnownIndex(
			element,
			knownIndex);
	}

	@Override
	void o_FiberGlobals (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).fiberGlobals(value);
	}

	@Override
	short o_RawByteAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).rawByteAt(index);
	}

	@Override
	void o_RawByteAtPut (
		final AvailObject object,
		final int index,
		final short anInteger)
	{
		o_Traversed(object).rawByteAtPut(index, anInteger);
	}

	@Override
	short o_RawByteForCharacterAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawByteForCharacterAt(index);
	}

	@Override
	void o_RawByteForCharacterAtPut (
		final AvailObject object,
		final int index,
		final short anInteger)
	{
		o_Traversed(object).rawByteForCharacterAtPut(index, anInteger);
	}

	@Override
	byte o_RawNybbleAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).rawNybbleAt(index);
	}

	@Override
	void o_RawNybbleAtPut (
		final AvailObject object,
		final int index,
		final byte aNybble)
	{
		o_Traversed(object).rawNybbleAtPut(index, aNybble);
	}

	@Override
	int o_RawShortForCharacterAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawShortForCharacterAt(index);
	}

	@Override
	void o_RawShortForCharacterAtPut (
		final AvailObject object,
		final int index,
		final int anInteger)
	{
		o_Traversed(object).rawShortForCharacterAtPut(index, anInteger);
	}

	@Override
	int o_RawSignedIntegerAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).rawSignedIntegerAt(index);
	}

	@Override
	void o_RawSignedIntegerAtPut (
		final AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).rawSignedIntegerAtPut(index, value);
	}

	@Override
	long o_RawUnsignedIntegerAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawUnsignedIntegerAt(index);
	}

	@Override
	void o_RawUnsignedIntegerAtPut (
		final AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).rawUnsignedIntegerAtPut(index, value);
	}

	@Override
	void o_RemoveDependentChunkIndex (
		final AvailObject object,
		final int aChunkIndex)
	{
		o_Traversed(object).removeDependentChunkIndex(aChunkIndex);
	}

	@Override
	void o_RemoveFrom (
		final AvailObject object,
		final L2Interpreter anInterpreter)
	{
		o_Traversed(object).removeFrom(anInterpreter);
	}

	@Override
	void o_RemoveImplementation (
		final AvailObject object,
		final AvailObject implementation)
	{
		o_Traversed(object).removeImplementation(implementation);
	}

	@Override
	boolean o_RemoveBundle (
		final AvailObject object,
		final AvailObject bundle)
	{
		return o_Traversed(object).removeBundle(
			bundle);
	}

	@Override
	void o_RemoveGrammaticalRestrictions (
		final AvailObject object,
		final AvailObject obsoleteRestrictions)
	{
		o_Traversed(object).removeGrammaticalRestrictions(obsoleteRestrictions);
	}

	@Override
	void o_ResolvedForwardWithName (
		final AvailObject object,
		final AvailObject forwardImplementation,
		final AvailObject methodName)
	{
		o_Traversed(object).resolvedForwardWithName(
			forwardImplementation,
			methodName);
	}

	@Override
	AvailObject o_SetIntersectionCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setIntersectionCanDestroy(
			otherSet,
			canDestroy);
	}

	@Override
	AvailObject o_SetMinusCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setMinusCanDestroy(otherSet, canDestroy);
	}

	@Override
	void o_SetSubtupleForZoneTo (
		final AvailObject object,
		final int zoneIndex,
		final AvailObject newTuple)
	{
		o_Traversed(object).setSubtupleForZoneTo(zoneIndex, newTuple);
	}

	@Override
	AvailObject o_SetUnionCanDestroy (
		final AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setUnionCanDestroy(otherSet, canDestroy);
	}

	@Override
	void o_SetValue (
		final AvailObject object,
		final AvailObject newValue)
	{
		o_Traversed(object).setValue(newValue);
	}

	@Override
	void o_SetValueNoCheck (
		final AvailObject object,
		final AvailObject newValue)
	{
		o_Traversed(object).setValueNoCheck(newValue);
	}

	@Override
	AvailObject o_SetWithElementCanDestroy (
		final AvailObject object,
		final AvailObject newElementObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).setWithElementCanDestroy(
			newElementObject,
			canDestroy);
	}

	@Override
	AvailObject o_SetWithoutElementCanDestroy (
		final AvailObject object,
		final AvailObject elementObjectToExclude,
		final boolean canDestroy)
	{
		return o_Traversed(object).setWithoutElementCanDestroy(
			elementObjectToExclude,
			canDestroy);
	}

	@Override
	void o_Size (final AvailObject object, final int value)
	{
		o_Traversed(object).size(value);
	}

	@Override
	int o_SizeOfZone (final AvailObject object, final int zone)
	{
		return o_Traversed(object).sizeOfZone(zone);
	}

	@Override
	AvailObject o_StackAt (final AvailObject object, final int slotIndex)
	{
		return o_Traversed(object).stackAt(slotIndex);
	}

	@Override
	void o_StackAtPut (
		final AvailObject object,
		final int slotIndex,
		final AvailObject anObject)
	{
		o_Traversed(object).stackAtPut(slotIndex, anObject);
	}

	@Override
	void o_Stackp (final AvailObject object, final int value)
	{
		o_Traversed(object).stackp(value);
	}

	@Override
	void o_Start (final AvailObject object, final int value)
	{
		o_Traversed(object).start(value);
	}

	@Override
	void o_StartingChunk (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).startingChunk(value);
	}

	@Override
	int o_StartOfZone (final AvailObject object, final int zone)
	{
		return o_Traversed(object).startOfZone(zone);
	}

	@Override
	int o_StartSubtupleIndexInZone (
		final AvailObject object,
		final int zone)
	{
		return o_Traversed(object).startSubtupleIndexInZone(zone);
	}

	@Override
	void o_String (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).string(value);
	}

	@Override
	AvailObject o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	AvailObject o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	AvailObject o_SubtupleForZone (
		final AvailObject object,
		final int zone)
	{
		return o_Traversed(object).subtupleForZone(zone);
	}

	@Override
	AvailObject o_TimesCanDestroy (
		final AvailObject object,
		final AvailObject aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).timesCanDestroy(aNumber, canDestroy);
	}

	@Override
	void o_TokenType (final AvailObject object, final TokenDescriptor.TokenType value)
	{
		o_Traversed(object).tokenType(value);
	}

	@Override
	int o_TranslateToZone (
		final AvailObject object,
		final int tupleIndex,
		final int zoneIndex)
	{
		return o_Traversed(object).translateToZone(tupleIndex, zoneIndex);
	}

	@Override
	AvailObject o_TrueNamesForStringName (
		final AvailObject object,
		final AvailObject stringName)
	{
		return o_Traversed(object).trueNamesForStringName(stringName);
	}

	@Override
	AvailObject o_TruncateTo (
		final AvailObject object,
		final int newTupleSize)
	{
		return o_Traversed(object).truncateTo(newTupleSize);
	}

	@Override
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).tupleAt(index);
	}

	@Override
	void o_TupleAtPut (
		final AvailObject object,
		final int index,
		final AvailObject aNybbleObject)
	{
		o_Traversed(object).tupleAtPut(index, aNybbleObject);
	}

	@Override
	AvailObject o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).tupleAtPuttingCanDestroy(
			index,
			newValueObject,
			canDestroy);
	}

	@Override
	int o_TupleIntAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).tupleIntAt(index);
	}

	@Override
	void o_Type (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).type(value);
	}

	@Override
	AvailObject o_TypeAtIndex (final AvailObject object, final int index)
	{
		return o_Traversed(object).typeAtIndex(index);
	}

	@Override
	AvailObject o_TypeIntersection (
		final AvailObject object,
		final AvailObject another)
	{
		return o_Traversed(object).typeIntersection(another);
	}

	@Override
	AvailObject o_TypeIntersectionOfFunctionType (
		final AvailObject object,
		final AvailObject aFunctionType)
	{
		return o_Traversed(object).typeIntersectionOfFunctionType(
			aFunctionType);
	}

	@Override
	AvailObject o_TypeIntersectionOfVariableType (
		final AvailObject object,
		final AvailObject aVariableType)
	{
		return o_Traversed(object).typeIntersectionOfVariableType(
			aVariableType);
	}

	@Override
	AvailObject o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		return o_Traversed(object).typeIntersectionOfContinuationType(
			aContinuationType);
	}

	@Override
	AvailObject o_TypeIntersectionOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		return o_Traversed(object).typeIntersectionOfCompiledCodeType(
			aCompiledCodeType);
	}

	@Override
	AvailObject o_TypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		return o_Traversed(object).typeIntersectionOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	AvailObject o_TypeIntersectionOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		return o_Traversed(object).typeIntersectionOfMapType(aMapType);
	}

	@Override
	AvailObject o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return o_Traversed(object).typeIntersectionOfObjectType(anObjectType);
	}

	@Override
	AvailObject o_TypeIntersectionOfParseNodeType (
		final AvailObject object,
		final AvailObject aParseNodeType)
	{
		return o_Traversed(object).typeIntersectionOfParseNodeType(
			aParseNodeType);
	}

	@Override
	AvailObject o_TypeIntersectionOfPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		return o_Traversed(object).typeIntersectionOfPojoType(aPojoType);
	}

	@Override
	AvailObject o_TypeIntersectionOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		return o_Traversed(object).typeIntersectionOfSetType(aSetType);
	}

	@Override
	AvailObject o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		return o_Traversed(object).typeIntersectionOfTupleType(aTupleType);
	}

	@Override
	AvailObject o_TypeUnion (
		final AvailObject object,
		final AvailObject another)
	{
		return o_Traversed(object).typeUnion(another);
	}

	@Override
	AvailObject o_TypeUnionOfFunctionType (
		final AvailObject object,
		final AvailObject aFunctionType)
	{
		return o_Traversed(object).typeUnionOfFunctionType(aFunctionType);
	}

	@Override
	AvailObject o_TypeUnionOfVariableType (
		final AvailObject object,
		final AvailObject aVariableType)
	{
		return o_Traversed(object).typeUnionOfVariableType(aVariableType);
	}

	@Override
	AvailObject o_TypeUnionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		return o_Traversed(object).typeUnionOfContinuationType(
			aContinuationType);
	}

	@Override
	AvailObject o_TypeUnionOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		return o_Traversed(object).typeUnionOfCompiledCodeType(
			aCompiledCodeType);
	}

	@Override
	AvailObject o_TypeUnionOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		return o_Traversed(object).typeUnionOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	AvailObject o_TypeUnionOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		return o_Traversed(object).typeUnionOfMapType(aMapType);
	}

	@Override
	AvailObject o_TypeUnionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return o_Traversed(object).typeUnionOfObjectType(anObjectType);
	}

	@Override
	AvailObject o_TypeUnionOfParseNodeType (
		final AvailObject object,
		final AvailObject aParseNodeType)
	{
		return o_Traversed(object).typeUnionOfParseNodeType(
			aParseNodeType);
	}

	@Override
	AvailObject o_TypeUnionOfPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		return o_Traversed(object).typeUnionOfPojoType(aPojoType);
	}

	@Override
	AvailObject o_TypeUnionOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		return o_Traversed(object).typeUnionOfSetType(aSetType);
	}

	@Override
	AvailObject o_TypeUnionOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		return o_Traversed(object).typeUnionOfTupleType(aTupleType);
	}

	@Override
	AvailObject o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		return o_Traversed(object).unionOfTypesAtThrough(startIndex, endIndex);
	}

	@Override
	int o_UntranslatedDataAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).untranslatedDataAt(index);
	}

	@Override
	void o_UntranslatedDataAtPut (
		final AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).untranslatedDataAtPut(index, value);
	}

	@Override
	AvailObject o_ValidateArgumentTypesInterpreterIfFail (
		final AvailObject object,
		final List<AvailObject> argTypes,
		final Interpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock)
	{
		return o_Traversed(object).validateArgumentTypesInterpreterIfFail(
			argTypes,
			anAvailInterpreter,
			failBlock);
	}

	@Override
	void o_Value (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).value(value);
	}

	@Override
	int o_ZoneForIndex (final AvailObject object, final int index)
	{
		return o_Traversed(object).zoneForIndex(index);
	}

	@Override
	String o_AsNativeString (final AvailObject object)
	{
		return o_Traversed(object).asNativeString();
	}

	@Override
	AvailObject o_AsSet (final AvailObject object)
	{
		return o_Traversed(object).asSet();
	}

	@Override
	AvailObject o_AsTuple (final AvailObject object)
	{
		return o_Traversed(object).asTuple();
	}

	@Override
	int o_BinHash (final AvailObject object)
	{
		return o_Traversed(object).binHash();
	}

	@Override
	int o_BinSize (final AvailObject object)
	{
		return o_Traversed(object).binSize();
	}

	@Override
	AvailObject o_BinUnionTypeOrNull (final AvailObject object)
	{
		return o_Traversed(object).binUnionTypeOrNull();
	}

	@Override
	int o_BitsPerEntry (final AvailObject object)
	{
		return o_Traversed(object).bitsPerEntry();
	}

	@Override
	AvailObject o_BodyBlock (final AvailObject object)
	{
		return o_Traversed(object).bodyBlock();
	}

	@Override
	AvailObject o_BodySignature (final AvailObject object)
	{
		return o_Traversed(object).bodySignature();
	}

	@Override
	AvailObject o_BreakpointBlock (final AvailObject object)
	{
		return o_Traversed(object).breakpointBlock();
	}

	@Override
	AvailObject o_Caller (final AvailObject object)
	{
		return o_Traversed(object).caller();
	}

	@Override
	void o_CleanUpAfterCompile (final AvailObject object)
	{
		o_Traversed(object).cleanUpAfterCompile();
	}

	@Override
	void o_ClearValue (final AvailObject object)
	{
		o_Traversed(object).clearValue();
	}

	@Override
	AvailObject o_Function (final AvailObject object)
	{
		return o_Traversed(object).function();
	}

	@Override
	AvailObject o_FunctionType (final AvailObject object)
	{
		return o_Traversed(object).functionType();
	}

	@Override
	AvailObject o_Code (final AvailObject object)
	{
		return o_Traversed(object).code();
	}

	@Override
	int o_CodePoint (final AvailObject object)
	{
		return o_Traversed(object).codePoint();
	}

	@Override
	AvailObject o_LazyComplete (final AvailObject object)
	{
		return o_Traversed(object).lazyComplete();
	}

	@Override
	AvailObject o_ConstantBindings (final AvailObject object)
	{
		return o_Traversed(object).constantBindings();
	}

	@Override
	AvailObject o_ContentType (final AvailObject object)
	{
		return o_Traversed(object).contentType();
	}

	@Override
	AvailObject o_Continuation (final AvailObject object)
	{
		return o_Traversed(object).continuation();
	}

	@Override
	AvailObject o_CopyAsMutableContinuation (final AvailObject object)
	{
		return o_Traversed(object).copyAsMutableContinuation();
	}

	@Override
	AvailObject o_CopyAsMutableObjectTuple (final AvailObject object)
	{
		return o_Traversed(object).copyAsMutableObjectTuple();
	}

	@Override
	AvailObject o_CopyAsMutableSpliceTuple (final AvailObject object)
	{
		return o_Traversed(object).copyAsMutableSpliceTuple();
	}

	@Override
	AvailObject o_DefaultType (final AvailObject object)
	{
		return o_Traversed(object).defaultType();
	}

	@Override
	int o_ParsingPc (final AvailObject object)
	{
		return o_Traversed(object).parsingPc();
	}

	@Override
	void o_DisplayTestingTree (final AvailObject object)
	{
		o_Traversed(object).displayTestingTree();
	}

	@Override
	AvailObject o_EnsureMutable (final AvailObject object)
	{
		return o_Traversed(object).ensureMutable();
	}

	@Override
	boolean o_EqualsNull (final AvailObject object)
	{
		return o_Traversed(object).equalsNull();
	}

	@Override
	ExecutionState o_ExecutionState (final AvailObject object)
	{
		return o_Traversed(object).executionState();
	}

	@Override
	void o_Expand (final AvailObject object)
	{
		o_Traversed(object).expand();
	}

	@Override
	boolean o_ExtractBoolean (final AvailObject object)
	{
		return o_Traversed(object).extractBoolean();
	}

	@Override
	short o_ExtractUnsignedByte (final AvailObject object)
	{
		return o_Traversed(object).extractUnsignedByte();
	}

	@Override
	double o_ExtractDouble (final AvailObject object)
	{
		return o_Traversed(object).extractDouble();
	}

	@Override
	float o_ExtractFloat (final AvailObject object)
	{
		return o_Traversed(object).extractFloat();
	}

	@Override
	int o_ExtractInt (final AvailObject object)
	{
		return o_Traversed(object).extractInt();
	}

	/**
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	long o_ExtractLong (final AvailObject object)
	{
		return o_Traversed(object).extractLong();
	}

	@Override
	byte o_ExtractNybble (final AvailObject object)
	{
		return o_Traversed(object).extractNybble();
	}

	@Override
	AvailObject o_FieldMap (final AvailObject object)
	{
		return o_Traversed(object).fieldMap();
	}

	@Override
	AvailObject o_FieldTypeMap (final AvailObject object)
	{
		return o_Traversed(object).fieldTypeMap();
	}

	@Override
	AvailObject o_FilteredBundleTree (final AvailObject object)
	{
		return o_Traversed(object).filteredBundleTree();
	}

	@Override
	int o_GetInteger (final AvailObject object)
	{
		return o_Traversed(object).getInteger();
	}

	@Override
	AvailObject o_GetValue (final AvailObject object)
	{
		return o_Traversed(object).getValue();
	}

	@Override
	int o_Hash (final AvailObject object)
	{
		return o_Traversed(object).hash();
	}

	@Override
	int o_HashOrZero (final AvailObject object)
	{
		return o_Traversed(object).hashOrZero();
	}

	@Override
	boolean o_HasGrammaticalRestrictions (final AvailObject object)
	{
		return o_Traversed(object).hasGrammaticalRestrictions();
	}

	@Override
	AvailObject o_DefinitionsTuple (final AvailObject object)
	{
		return o_Traversed(object).definitionsTuple();
	}

	@Override
	AvailObject o_LazyIncomplete (final AvailObject object)
	{
		return o_Traversed(object).lazyIncomplete();
	}

	@Override
	int o_Index (final AvailObject object)
	{
		return o_Traversed(object).index();
	}

	@Override
	int o_InterruptRequestFlags (final AvailObject object)
	{
		return o_Traversed(object).interruptRequestFlags();
	}

	@Override
	int o_InvocationCount (final AvailObject object)
	{
		return o_Traversed(object).invocationCount();
	}

	@Override
	boolean o_IsAbstractDefinition (final AvailObject object)
	{
		return o_Traversed(object).isAbstractDefinition();
	}

	@Override
	boolean o_IsAbstract (final AvailObject object)
	{
		return o_Traversed(object).isAbstract();
	}

	@Override
	boolean o_IsBoolean (final AvailObject object)
	{
		return o_Traversed(object).isBoolean();
	}

	@Override
	boolean o_IsUnsignedByte (final AvailObject object)
	{
		return o_Traversed(object).isUnsignedByte();
	}

	/**
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	boolean o_IsByteTuple (final AvailObject object)
	{
		return o_Traversed(object).isByteTuple();
	}

	@Override
	boolean o_IsCharacter (final AvailObject object)
	{
		return o_Traversed(object).isCharacter();
	}

	@Override
	boolean o_IsFunction (final AvailObject object)
	{
		return o_Traversed(object).isFunction();
	}

	@Override
	boolean o_IsAtom (final AvailObject object)
	{
		return o_Traversed(object).isAtom();
	}

	@Override
	boolean o_IsExtendedInteger (final AvailObject object)
	{
		return o_Traversed(object).isExtendedInteger();
	}

	@Override
	boolean o_IsFinite (final AvailObject object)
	{
		return o_Traversed(object).isFinite();
	}

	@Override
	boolean o_IsForwardDefinition (final AvailObject object)
	{
		return o_Traversed(object).isForwardDefinition();
	}

	@Override
	boolean o_IsInstanceMeta (final AvailObject object)
	{
		return o_Traversed(object).isInstanceMeta();
	}

	@Override
	boolean o_IsMethodDefinition (final AvailObject object)
	{
		return o_Traversed(object).isMethodDefinition();
	}

	@Override
	boolean o_IsIntegerRangeType (final AvailObject object)
	{
		return o_Traversed(object).isIntegerRangeType();
	}

	@Override
	boolean o_IsMap (final AvailObject object)
	{
		return o_Traversed(object).isMap();
	}

	@Override
	boolean o_IsMapType (final AvailObject object)
	{
		return o_Traversed(object).isMapType();
	}

	@Override
	boolean o_IsNybble (final AvailObject object)
	{
		return o_Traversed(object).isNybble();
	}

	@Override
	boolean o_IsPositive (final AvailObject object)
	{
		return o_Traversed(object).isPositive();
	}

	@Override
	boolean o_IsSaved (final AvailObject object)
	{
		return o_Traversed(object).isSaved();
	}

	@Override
	boolean o_IsSet (final AvailObject object)
	{
		return o_Traversed(object).isSet();
	}

	@Override
	boolean o_IsSetType (final AvailObject object)
	{
		return o_Traversed(object).isSetType();
	}

	@Override
	boolean o_IsSplice (final AvailObject object)
	{
		return o_Traversed(object).isSplice();
	}

	/**
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	boolean o_IsString (final AvailObject object)
	{
		return o_Traversed(object).isString();
	}

	@Override
	boolean o_IsSupertypeOfBottom (final AvailObject object)
	{
		return o_Traversed(object).isSupertypeOfBottom();
	}

	@Override
	boolean o_IsTuple (final AvailObject object)
	{
		return o_Traversed(object).isTuple();
	}

	@Override
	boolean o_IsTupleType (final AvailObject object)
	{
		return o_Traversed(object).isTupleType();
	}

	@Override
	boolean o_IsType (final AvailObject object)
	{
		return o_Traversed(object).isType();
	}

	@Override
	boolean o_IsValid (final AvailObject object)
	{
		return o_Traversed(object).isValid();
	}

	@Override
	AvailObject o_KeysAsSet (final AvailObject object)
	{
		return o_Traversed(object).keysAsSet();
	}

	@Override
	AvailObject o_KeyType (final AvailObject object)
	{
		return o_Traversed(object).keyType();
	}

	@Override
	AvailObject o_LevelTwoChunk (final AvailObject object)
	{
		return o_Traversed(object).levelTwoChunk();
	}

	@Override
	int o_LevelTwoOffset (final AvailObject object)
	{
		return o_Traversed(object).levelTwoOffset();
	}

	@Override
	AvailObject o_Literal (final AvailObject object)
	{
		return o_Traversed(object).literal();
	}

	@Override
	AvailObject o_LowerBound (final AvailObject object)
	{
		return o_Traversed(object).lowerBound();
	}

	@Override
	boolean o_LowerInclusive (final AvailObject object)
	{
		return o_Traversed(object).lowerInclusive();
	}

	@Override
	void o_MakeSubobjectsImmutable (final AvailObject object)
	{
		o_Traversed(object).makeSubobjectsImmutable();
	}

	@Override
	int o_MapSize (final AvailObject object)
	{
		return o_Traversed(object).mapSize();
	}

	@Override
	int o_MaxStackDepth (final AvailObject object)
	{
		return o_Traversed(object).maxStackDepth();
	}

	@Override
	AvailObject o_Message (final AvailObject object)
	{
		return o_Traversed(object).message();
	}

	@Override
	AvailObject o_MessageParts (final AvailObject object)
	{
		return o_Traversed(object).messageParts();
	}

	@Override
	AvailObject o_Methods (final AvailObject object)
	{
		return o_Traversed(object).methods();
	}

	@Override
	AvailObject o_Name (final AvailObject object)
	{
		return o_Traversed(object).name();
	}

	@Override
	AvailObject o_Names (final AvailObject object)
	{
		return o_Traversed(object).names();
	}

	@Override
	AvailObject o_NewNames (final AvailObject object)
	{
		return o_Traversed(object).newNames();
	}

	@Override
	int o_NumArgs (final AvailObject object)
	{
		return o_Traversed(object).numArgs();
	}

	@Override
	int o_NumArgsAndLocalsAndStack (final AvailObject object)
	{
		return o_Traversed(object).numArgsAndLocalsAndStack();
	}

	@Override
	int o_NumberOfZones (final AvailObject object)
	{
		return o_Traversed(object).numberOfZones();
	}

	@Override
	int o_NumDoubles (final AvailObject object)
	{
		return o_Traversed(object).numDoubles();
	}

	@Override
	int o_NumIntegers (final AvailObject object)
	{
		return o_Traversed(object).numIntegers();
	}

	@Override
	int o_NumLiterals (final AvailObject object)
	{
		return o_Traversed(object).numLiterals();
	}

	@Override
	int o_NumLocals (final AvailObject object)
	{
		return o_Traversed(object).numLocals();
	}

	@Override
	int o_NumObjects (final AvailObject object)
	{
		return o_Traversed(object).numObjects();
	}

	@Override
	int o_NumOuters (final AvailObject object)
	{
		return o_Traversed(object).numOuters();
	}

	@Override
	int o_NumOuterVars (final AvailObject object)
	{
		return o_Traversed(object).numOuterVars();
	}

	@Override
	AvailObject o_Nybbles (final AvailObject object)
	{
		return o_Traversed(object).nybbles();
	}

	@Override
	AvailObject o_Parent (final AvailObject object)
	{
		return o_Traversed(object).parent();
	}

	@Override
	int o_Pc (final AvailObject object)
	{
		return o_Traversed(object).pc();
	}

	@Override @Deprecated
	void o_PostFault (final AvailObject object)
	{
		o_Traversed(object).postFault();
	}

	@Override
	int o_PrimitiveNumber (final AvailObject object)
	{
		return o_Traversed(object).primitiveNumber();
	}

	@Override
	AvailObject o_Priority (final AvailObject object)
	{
		return o_Traversed(object).priority();
	}

	@Override
	AvailObject o_PrivateNames (final AvailObject object)
	{
		return o_Traversed(object).privateNames();
	}

	@Override
	AvailObject o_FiberGlobals (final AvailObject object)
	{
		return o_Traversed(object).fiberGlobals();
	}

	@Override
	void o_ReadBarrierFault (final AvailObject object)
	{
		o_Traversed(object).readBarrierFault();
	}

	@Override
	AvailObject o_GrammaticalRestrictions (final AvailObject object)
	{
		return o_Traversed(object).grammaticalRestrictions();
	}

	@Override
	AvailObject o_ReturnType (final AvailObject object)
	{
		return o_Traversed(object).returnType();
	}

	@Override
	int o_SetSize (final AvailObject object)
	{
		return o_Traversed(object).setSize();
	}

	@Override
	AvailObject o_SizeRange (final AvailObject object)
	{
		return o_Traversed(object).sizeRange();
	}

	@Override
	AvailObject o_LazyActions (final AvailObject object)
	{
		return o_Traversed(object).lazyActions();
	}

	@Override
	int o_Stackp (final AvailObject object)
	{
		return o_Traversed(object).stackp();
	}

	@Override
	int o_Start (final AvailObject object)
	{
		return o_Traversed(object).start();
	}

	@Override
	AvailObject o_StartingChunk (final AvailObject object)
	{
		return o_Traversed(object).startingChunk();
	}

	@Override
	void o_Step (final AvailObject object)
	{
		o_Traversed(object).step();
	}

	@Override
	AvailObject o_String (final AvailObject object)
	{
		return o_Traversed(object).string();
	}

	@Override
	AvailObject o_TestingTree (final AvailObject object)
	{
		return o_Traversed(object).testingTree();
	}

	@Override
	TokenDescriptor.TokenType o_TokenType (final AvailObject object)
	{
		return o_Traversed(object).tokenType();
	}

	@Override
	void o_TrimExcessInts (final AvailObject object)
	{
		o_Traversed(object).trimExcessInts();
	}

	@Override
	int o_TupleSize (final AvailObject object)
	{
		return o_Traversed(object).tupleSize();
	}

	@Override
	AvailObject o_Kind (final AvailObject object)
	{
		return o_Traversed(object).kind();
	}

	@Override
	AvailObject o_TypeTuple (final AvailObject object)
	{
		return o_Traversed(object).typeTuple();
	}

	@Override
	AvailObject o_Unclassified (final AvailObject object)
	{
		return o_Traversed(object).unclassified();
	}

	@Override
	AvailObject o_UpperBound (final AvailObject object)
	{
		return o_Traversed(object).upperBound();
	}

	@Override
	boolean o_UpperInclusive (final AvailObject object)
	{
		return o_Traversed(object).upperInclusive();
	}

	@Override
	AvailObject o_Value (final AvailObject object)
	{
		return o_Traversed(object).value();
	}

	@Override
	AvailObject o_ValuesAsTuple (final AvailObject object)
	{
		return o_Traversed(object).valuesAsTuple();
	}

	@Override
	AvailObject o_ValueType (final AvailObject object)
	{
		return o_Traversed(object).valueType();
	}

	@Override
	AvailObject o_VariableBindings (final AvailObject object)
	{
		return o_Traversed(object).variableBindings();
	}

	@Override
	AvailObject o_Vectors (final AvailObject object)
	{
		return o_Traversed(object).vectors();
	}

	@Override
	void o_Verify (final AvailObject object)
	{
		o_Traversed(object).verify();
	}

	@Override
	AvailObject o_VisibleNames (final AvailObject object)
	{
		return o_Traversed(object).visibleNames();
	}

	@Override
	AvailObject o_Wordcodes (final AvailObject object)
	{
		return o_Traversed(object).wordcodes();
	}

	@Override
	AvailObject o_ParsingInstructions (final AvailObject object)
	{
		return o_Traversed(object).parsingInstructions();
	}

	@Override
	void o_Expression (
		final AvailObject object,
		final AvailObject expression)
	{
		o_Traversed(object).expression(expression);
	}

	@Override
	AvailObject o_Expression (final AvailObject object)
	{
		return o_Traversed(object).expression();
	}

	@Override
	void o_Variable (
		final AvailObject object,
		final AvailObject variable)
	{
		o_Traversed(object).variable(variable);
	}

	@Override
	AvailObject o_Variable (final AvailObject object)
	{
		return o_Traversed(object).variable();
	}

	@Override
	AvailObject o_ArgumentsTuple (final AvailObject object)
	{
		return o_Traversed(object).argumentsTuple();
	}

	@Override
	AvailObject o_StatementsTuple (final AvailObject object)
	{
		return o_Traversed(object).statementsTuple();
	}

	@Override
	AvailObject o_ResultType (final AvailObject object)
	{
		return o_Traversed(object).resultType();
	}

	@Override
	void o_NeededVariables (
		final AvailObject object,
		final AvailObject neededVariables)
	{
		o_Traversed(object).neededVariables(neededVariables);
	}

	@Override
	AvailObject o_NeededVariables (final AvailObject object)
	{
		return o_Traversed(object).neededVariables();
	}

	@Override
	int o_Primitive (final AvailObject object)
	{
		return o_Traversed(object).primitive();
	}

	@Override
	AvailObject o_DeclaredType (final AvailObject object)
	{
		return o_Traversed(object).declaredType();
	}

	@Override
	DeclarationKind o_DeclarationKind (final AvailObject object)
	{
		return o_Traversed(object).declarationKind();
	}

	@Override
	AvailObject o_InitializationExpression (final AvailObject object)
	{
		return o_Traversed(object).initializationExpression();
	}

	@Override
	void o_InitializationExpression (
		final AvailObject object,
		final AvailObject initializationExpression)
	{
		o_Traversed(object).initializationExpression(initializationExpression);
	}

	@Override
	AvailObject o_LiteralObject (final AvailObject object)
	{
		return o_Traversed(object).literalObject();
	}

	@Override
	AvailObject o_Token (final AvailObject object)
	{
		return o_Traversed(object).token();
	}

	@Override
	AvailObject o_MarkerValue (final AvailObject object)
	{
		return o_Traversed(object).markerValue();
	}

	@Override
	AvailObject o_ArgumentsListNode (
		final AvailObject object)
	{
		return o_Traversed(object).argumentsListNode();
	}

	@Override
	AvailObject o_Method (final AvailObject object)
	{
		return o_Traversed(object).method();
	}

	@Override
	AvailObject o_ExpressionsTuple (final AvailObject object)
	{
		return o_Traversed(object).expressionsTuple();
	}

	@Override
	AvailObject o_Declaration (final AvailObject object)
	{
		return o_Traversed(object).declaration();
	}

	@Override
	AvailObject o_ExpressionType (final AvailObject object)
	{
		return o_Traversed(object).expressionType();
	}

	@Override
	void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		o_Traversed(object).emitEffectOn(codeGenerator);
	}

	@Override
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		o_Traversed(object).emitValueOn(codeGenerator);
	}

	@Override
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		o_Traversed(object).childrenMap(aBlock);
	}

	@Override
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		o_Traversed(object).childrenDo(aBlock);
	}

	@Override
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable AvailObject parent)
	{
		o_Traversed(object).validateLocally(parent);
	}

	@Override
	AvailObject o_Generate (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		return o_Traversed(object).generate(codeGenerator);
	}

	@Override
	AvailObject o_CopyWith (
		final AvailObject object,
		final AvailObject newParseNode)
	{
		return o_Traversed(object).copyWith(newParseNode);
	}

	@Override
	void o_IsLastUse (
		final AvailObject object,
		final boolean isLastUse)
	{
		o_Traversed(object).isLastUse(isLastUse);
	}

	@Override
	boolean o_IsLastUse (
		final AvailObject object)
	{
		return o_Traversed(object).isLastUse();
	}

	@Override
	boolean o_IsMacroDefinition (
		final AvailObject object)
	{
		return o_Traversed(object).isMacroDefinition();
	}

	@Override
	AvailObject o_CopyMutableParseNode (
		final AvailObject object)
	{
		return o_Traversed(object).copyMutableParseNode();
	}

	@Override
	AvailObject o_BinUnionKind (
		final AvailObject object)
	{
		return o_Traversed(object).binUnionKind();
	}

	@Override
	AvailObject o_OutputParseNode (
		final AvailObject object)
	{
		return o_Traversed(object).outputParseNode();
	}

	@Override
	AvailObject o_ApparentSendName (
		final AvailObject object)
	{
		return o_Traversed(object).apparentSendName();
	}

	@Override
	void o_Statements (
		final AvailObject object,
		final AvailObject statementsTuple)
	{
		o_Traversed(object).statements(statementsTuple);
	}

	@Override
	AvailObject o_Statements (final AvailObject object)
	{
		return o_Traversed(object).statements();
	}

	@Override
	void o_FlattenStatementsInto (
		final AvailObject object,
		final List<AvailObject> accumulatedStatements)
	{
		o_Traversed(object).flattenStatementsInto(accumulatedStatements);
	}

	@Override
	void o_LineNumber (final AvailObject object, final int value)
	{
		o_Traversed(object).lineNumber(value);
	}

	@Override
	int o_LineNumber (final AvailObject object)
	{
		return o_Traversed(object).lineNumber();
	}

	@Override
	AvailObject o_AllBundles (final AvailObject object)
	{
		return o_Traversed(object).allBundles();
	}

	@Override
	boolean o_IsSetBin (final AvailObject object)
	{
		return o_Traversed(object).isSetBin();
	}

	@Override
	MapDescriptor.MapIterable o_MapIterable (
		final AvailObject object)
	{
		return o_Traversed(object).mapIterable();
	}

	@Override
	AvailObject o_Complete (final AvailObject object)
	{
		return o_Traversed(object).complete();
	}

	@Override
	AvailObject o_Incomplete (final AvailObject object)
	{
		return o_Traversed(object).incomplete();
	}

	@Override
	AvailObject o_Actions (final AvailObject object)
	{
		return o_Traversed(object).actions();
	}

	@Override
	AvailObject o_DeclaredExceptions (
		final AvailObject object)
	{
		return o_Traversed(object).declaredExceptions();
	}

	@Override
	boolean o_IsInt (
		final AvailObject object)
	{
		return o_Traversed(object).isInt();
	}

	@Override
	boolean o_IsLong (
		final AvailObject object)
	{
		return o_Traversed(object).isLong();
	}

	@Override
	AvailObject o_ArgsTupleType (final AvailObject object)
	{
		return o_Traversed(object).argsTupleType();
	}

	@Override
	boolean o_EqualsInstanceTypeFor (
		final AvailObject object,
		final AvailObject anObject)
	{
		return o_Traversed(object).equalsInstanceTypeFor(anObject);
	}

	@Override
	AvailObject o_Instances (final AvailObject object)
	{
		return o_Traversed(object).instances();
	}

	@Override
	boolean o_EqualsEnumerationWithSet (
		final AvailObject object,
		final AvailObject aSet)
	{
		return o_Traversed(object).equalsEnumerationWithSet(aSet);
	}

	@Override
	boolean o_IsEnumeration (final AvailObject object)
	{
		return o_Traversed(object).isEnumeration();
	}

	@Override
	boolean o_IsInstanceOf (
		final AvailObject object,
		final AvailObject aType)
	{
		return o_Traversed(object).isInstanceOf(aType);
	}

	@Override
	boolean o_EnumerationIncludesInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return o_Traversed(object).enumerationIncludesInstance(
			potentialInstance);
	}

	@Override
	AvailObject o_ComputeSuperkind (final AvailObject object)
	{
		return o_Traversed(object).computeSuperkind();
	}

	@Override
	void o_SetAtomProperty (
		final AvailObject object,
		final AvailObject key,
		final AvailObject value)
	{
		o_Traversed(object).setAtomProperty(key, value);
	}

	@Override
	AvailObject o_GetAtomProperty (
		final AvailObject object,
		final AvailObject key)
	{
		return o_Traversed(object).getAtomProperty(key);
	}

	@Override
	boolean o_EqualsEnumerationType (
		final AvailObject object,
		final AvailObject another)
	{
		return o_Traversed(object).equalsEnumerationType(another);
	}

	@Override
	AvailObject o_ReadType (final AvailObject object)
	{
		return o_Traversed(object).readType();
	}

	@Override
	AvailObject o_WriteType (final AvailObject object)
	{
		return o_Traversed(object).writeType();
	}

	@Override
	void o_Versions (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).versions(value);
	}

	@Override
	AvailObject o_Versions (final AvailObject object)
	{
		return o_Traversed(object).versions();
	}

	@Override
	boolean o_EqualsParseNodeType (
		final AvailObject object,
		final AvailObject aParseNodeType)
	{
		return o_Traversed(object).equalsParseNodeType(aParseNodeType);
	}

	@Override
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		return o_Traversed(object).parseNodeKind();
	}

	@Override
	boolean o_ParseNodeKindIsUnder (
		final AvailObject object,
		final ParseNodeKind expectedParseNodeKind)
	{
		return o_Traversed(object).parseNodeKindIsUnder(expectedParseNodeKind);
	}

	@Override
	boolean o_IsRawPojo (final AvailObject object)
	{
		return o_Traversed(object).isRawPojo();
	}

	@Override
	void o_AddTypeRestriction (
		final AvailObject object,
		final AvailObject restrictionSignature)
	{
		o_Traversed(object).addTypeRestriction(restrictionSignature);
	}

	@Override
	void o_RemoveTypeRestriction (
		final AvailObject object,
		final AvailObject restrictionSignature)
	{
		o_Traversed(object).removeTypeRestriction(restrictionSignature);
	}

	@Override
	AvailObject o_TypeRestrictions (
		final AvailObject object)
	{
		return o_Traversed(object).typeRestrictions();
	}

	@Override
	void o_AddSealedArgumentsType (
		final AvailObject object,
		final AvailObject tupleType)
	{
		o_Traversed(object).addSealedArgumentsType(tupleType);
	}

	@Override
	void o_RemoveSealedArgumentsType (
		final AvailObject object,
		final AvailObject tupleType)
	{
		o_Traversed(object).removeSealedArgumentsType(tupleType);
	}

	@Override
	final AvailObject o_SealedArgumentsTypesTuple (
		final AvailObject object)
	{
		return o_Traversed(object).sealedArgumentsTypesTuple();
	}

	@Override
	void o_AddTypeRestriction (
		final AvailObject object,
		final AvailObject methodNameAtom,
		final AvailObject typeRestrictionFunction)
	{
		o_Traversed(object).addTypeRestriction(
			methodNameAtom,
			typeRestrictionFunction);
	}

	@Override
	void o_AddConstantBinding (
		final AvailObject object,
		final AvailObject name,
		final AvailObject constantBinding)
	{
		o_Traversed(object).addConstantBinding(
			name,
			constantBinding);
	}

	@Override
	void o_AddVariableBinding (
		final AvailObject object,
		final AvailObject name,
		final AvailObject variableBinding)
	{
		o_Traversed(object).addVariableBinding(
			name,
			variableBinding);
	}

	@Override
	boolean o_IsMethodEmpty (
		final AvailObject object)
	{
		return o_Traversed(object).isMethodEmpty();
	}

	@Override
	boolean o_IsPojoSelfType (final AvailObject object)
	{
		return o_Traversed(object).isPojoSelfType();
	}

	@Override
	AvailObject o_PojoSelfType (final AvailObject object)
	{
		return o_Traversed(object).pojoSelfType();
	}

	@Override
	AvailObject o_JavaClass (final AvailObject object)
	{
		return o_Traversed(object).javaClass();
	}

	@Override
	boolean o_IsUnsignedShort (final AvailObject object)
	{
		return o_Traversed(object).isUnsignedShort();
	}

	@Override
	int o_ExtractUnsignedShort (final AvailObject object)
	{
		return o_Traversed(object).extractUnsignedShort();
	}

	@Override
	boolean o_IsFloat (final AvailObject object)
	{
		return o_Traversed(object).isFloat();
	}

	@Override
	boolean o_IsDouble (final AvailObject object)
	{
		return o_Traversed(object).isDouble();
	}

	@Override
	AvailObject o_RawPojo (final AvailObject object)
	{
		return o_Traversed(object).rawPojo();
	}

	@Override
	boolean o_IsPojo (final AvailObject object)
	{
		return o_Traversed(object).isPojo();
	}

	@Override
	boolean o_IsPojoType (final AvailObject object)
	{
		return o_Traversed(object).isPojoType();
	}

	@Override
	public AvailObject o_UpperBoundMap (final AvailObject object)
	{
		return o_Traversed(object).upperBoundMap();
	}

	@Override
	public void o_UpperBoundMap (
		final AvailObject object,
		final AvailObject aMap)
	{
		o_Traversed(object).upperBoundMap(aMap);
	}

	@Override
	Order o_NumericCompare (
		final AvailObject object,
		final AvailObject another)
	{
		return o_Traversed(object).numericCompare(another);
	}

	@Override
	Order o_NumericCompareToDouble (
		final AvailObject object,
		final double aDouble)
	{
		return o_Traversed(object).numericCompareToDouble(aDouble);
	}

	@Override
	AvailObject o_AddToDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	AvailObject o_AddToFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	AvailObject o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	AvailObject o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	AvailObject o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	AvailObject o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	AvailObject o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	AvailObject o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	AvailObject o_LazyPrefilterMap (
		final AvailObject object)
	{
		return o_Traversed(object).lazyPrefilterMap();
	}

	@Override
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
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
		final AvailObject object,
		final AvailObject key,
		final int keyHash,
		final AvailObject value,
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
	AvailObject o_MapBinRemoveKeyHashCanDestroy (
		final AvailObject object,
		final AvailObject key,
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
	AvailObject o_MapBinKeyUnionKind (
		final AvailObject object)
	{
		return o_Traversed(object).mapBinKeyUnionKind();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	AvailObject o_MapBinValueUnionKind (
		final AvailObject object)
	{
		return o_Traversed(object).mapBinValueUnionKind();
	}

	@Override
	boolean o_IsHashedMapBin (
		final AvailObject object)
	{
		return o_Traversed(object).isHashedMapBin();
	}

	@Override
	AvailObject o_MapBinAtHash (
		final AvailObject object,
		final AvailObject key,
		final int keyHash)
	{
		return o_Traversed(object).mapBinAtHash(key, keyHash);
	}

	@Override
	int o_MapBinKeysHash (
		final AvailObject object)
	{
		return o_Traversed(object).mapBinKeysHash();
	}

	@Override
	int o_MapBinValuesHash (final AvailObject object)
	{
		return o_Traversed(object).mapBinValuesHash();
	}

	@Override
	AvailObject o_IssuingModule (
		final AvailObject object)
	{
		return o_Traversed(object).issuingModule();
	}

	@Override
	boolean o_IsPojoFusedType (final AvailObject object)
	{
		return o_Traversed(object).isPojoFusedType();
	}

	@Override
	boolean o_IsSupertypeOfPojoBottomType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		return o_Traversed(object).isSupertypeOfPojoBottomType(aPojoType);
	}

	@Override
	boolean o_EqualsPojoBottomType (final AvailObject object)
	{
		return o_Traversed(object).equalsPojoBottomType();
	}

	@Override
	AvailObject o_JavaAncestors (final AvailObject object)
	{
		return o_Traversed(object).javaAncestors();
	}

	/**
	 * @param object
	 * @param aFusedPojoType
	 * @return
	 */
	@Override
	AvailObject o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final AvailObject aFusedPojoType)
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
	AvailObject o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final AvailObject anUnfusedPojoType)
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
	AvailObject o_TypeUnionOfPojoFusedType (
		final AvailObject object,
		final AvailObject aFusedPojoType)
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
	AvailObject o_TypeUnionOfPojoUnfusedType (
		final AvailObject object,
		final AvailObject anUnfusedPojoType)
	{
		return o_Traversed(object).typeUnionOfPojoUnfusedType(
			anUnfusedPojoType);
	}

	@Override
	boolean o_IsPojoArrayType (final AvailObject object)
	{
		return o_Traversed(object).isPojoArrayType();
	}

	@Override
	Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> classHint)
	{
		return o_Traversed(object).marshalToJava(classHint);
	}

	@Override
	AvailObject o_TypeVariables (final AvailObject object)
	{
		return o_Traversed(object).typeVariables();
	}

	@Override
	boolean o_EqualsPojoField (
		final AvailObject object,
		final AvailObject field,
		final AvailObject receiver)
	{
		return o_Traversed(object).equalsPojoField(field, receiver);
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	boolean o_IsSignedByte (final AvailObject object)
	{
		return o_Traversed(object).isSignedByte();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	boolean o_IsSignedShort (final AvailObject object)
	{
		return o_Traversed(object).isSignedShort();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	byte o_ExtractSignedByte (final AvailObject object)
	{
		return o_Traversed(object).extractSignedByte();
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	short o_ExtractSignedShort (final AvailObject object)
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
		final AvailObject object,
		final AvailObject aRawPojo)
	{
		return o_Traversed(object).equalsEqualityRawPojo(aRawPojo);
	}

	/**
	 * @param object
	 * @return
	 */
	@Override
	Object o_JavaObject (final AvailObject object)
	{
		return o_Traversed(object).javaObject();
	}

	@Override
	BigInteger o_AsBigInteger (
		final AvailObject object)
	{
		return o_Traversed(object).asBigInteger();
	}

	@Override
	AvailObject o_AppendCanDestroy (
		final AvailObject object,
		final AvailObject newElement,
		final boolean canDestroy)
	{
		return o_Traversed(object).appendCanDestroy(newElement, canDestroy);
	}

	@Override
	AvailObject o_LazyIncompleteCaseInsensitive (
		final AvailObject object)
	{
		return o_Traversed(object).lazyIncompleteCaseInsensitive();
	}

	@Override
	AvailObject o_LowerCaseString (final AvailObject object)
	{
		return o_Traversed(object).lowerCaseString();
	}

	@Override
	AvailObject o_InstanceCount (final AvailObject object)
	{
		return o_Traversed(object).instanceCount();
	}

	@Override
	long o_TotalInvocations (final AvailObject object)
	{
		return o_Traversed(object).totalInvocations();
	}

	@Override
	void o_TallyInvocation (final AvailObject object)
	{
		o_Traversed(object).tallyInvocation();
	}

	@Override
	AvailObject o_FieldTypeTuple (final AvailObject object)
	{
		return o_Traversed(object).fieldTypeTuple();
	}

	@Override
	AvailObject o_FieldTuple (final AvailObject object)
	{
		return o_Traversed(object).fieldTuple();
	}

	@Override
	void o_ClearInterruptRequestFlags (final AvailObject object)
	{
		o_Traversed(object).clearInterruptRequestFlags();
	}

	@Override
	boolean o_IsSystemModule (final AvailObject object)
	{
		return o_Traversed(object).isSystemModule();
	}

	@Override
	AvailObject o_LiteralType (final AvailObject object)
	{
		return o_Traversed(object).literalType();
	}

	@Override
	AvailObject o_TypeIntersectionOfLiteralTokenType (
		final AvailObject object,
		final AvailObject aLiteralTokenType)
	{
		return o_Traversed(object).typeIntersectionOfLiteralTokenType(
			aLiteralTokenType);
	}

	@Override
	AvailObject o_TypeUnionOfLiteralTokenType (
		final AvailObject object,
		final AvailObject aLiteralTokenType)
	{
		return o_Traversed(object).typeUnionOfLiteralTokenType(
			aLiteralTokenType);
	}

	@Override
	boolean o_IsLiteralTokenType (final AvailObject object)
	{
		return o_Traversed(object).isLiteralTokenType();
	}

	@Override
	boolean o_IsLiteralToken (final AvailObject object)
	{
		return o_Traversed(object).isLiteralToken();
	}

	@Override
	boolean o_IsSupertypeOfLiteralTokenType (
		final AvailObject object,
		final AvailObject aLiteralTokenType)
	{
		return o_Traversed(object).isSupertypeOfLiteralTokenType(
			aLiteralTokenType);
	}

	@Override
	boolean o_EqualsLiteralTokenType (
		final AvailObject object,
		final AvailObject aLiteralTokenType)
	{
		return o_Traversed(object).equalsLiteralTokenType(aLiteralTokenType);
	}

	@Override
	boolean o_EqualsObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return o_Traversed(object).equalsObjectType(anObjectType);
	}

	@Override
	boolean o_EqualsToken (
		final AvailObject object,
		final AvailObject aToken)
	{
		return o_Traversed(object).equalsToken(aToken);
	}

	@Override
	AvailObject o_BitwiseAnd (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitwiseAnd(anInteger, canDestroy);
	}

	@Override
	AvailObject o_BitwiseOr (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitwiseOr(anInteger, canDestroy);
	}

	@Override
	AvailObject o_BitwiseXor (
		final AvailObject object,
		final AvailObject anInteger,
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
	AvailObject o_Instance (
		final AvailObject object)
	{
		return o_Traversed(object).instance();
	}

	@Override
	int o_AllocateFromCounter (
		final AvailObject object)
	{
		return o_Traversed(object).allocateFromCounter();
	}

	@Override
	void o_SetMethodName (
		final AvailObject object,
		final AvailObject methodName)
	{
		o_Traversed(object).setMethodName(methodName);
	}

	@Override
	int o_StartingLineNumber (
		final AvailObject object)
	{
		return o_Traversed(object).startingLineNumber();
	}

	@Override
	AvailObject o_Module (final AvailObject object)
	{
		return o_Traversed(object).module();
	}

	@Override
	AvailObject o_MethodName (final AvailObject object)
	{
		return o_Traversed(object).methodName();
	}

	@Override
	String o_NameForDebugger (final AvailObject object)
	{
		final String name = o_Traversed(object).nameForDebugger();
		return "IND→" + name;
	}

	@Override
	boolean o_BinElementsAreAllInstancesOfKind (
		final AvailObject object,
		final AvailObject kind)
	{
		return o_Traversed(object).binElementsAreAllInstancesOfKind(kind);
	}

	@Override
	boolean o_SetElementsAreAllInstancesOfKind (
		final AvailObject object,
		final AvailObject kind)
	{
		return o_Traversed(object).setElementsAreAllInstancesOfKind(kind);
	}

	@Override
	MapIterable o_MapBinIterable (
		final AvailObject object)
	{
		return o_Traversed(object).mapBinIterable();
	}

	@Override
	boolean o_RangeIncludesInt (
		final AvailObject object,
		final int anInt)
	{
		return o_Traversed(object).rangeIncludesInt(anInt);
	}

	@Override
	void o_IsSystemModule (
		final AvailObject object,
		final boolean isSystemModule)
	{
		o_Traversed(object).isSystemModule(isSystemModule);
	}

	@Override
	boolean o_IsMarkerNode (
		final AvailObject object)
	{
		return o_Traversed(object).isMarkerNode();
	}

	@Override
	AvailObject o_BitShiftLeftTruncatingToBits (
		final AvailObject object,
		final AvailObject shiftFactor,
		final AvailObject truncationBits,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitShiftLeftTruncatingToBits(
			shiftFactor,
			truncationBits,
			canDestroy);
	}

	@Override
	SetIterator o_SetBinIterator (
		final AvailObject object)
	{
		return o_Traversed(object).setBinIterator();
	}

	@Override
	AvailObject o_BitShift (
		final AvailObject object,
		final AvailObject shiftFactor,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitShift(shiftFactor, canDestroy);
	}

	@Override
	boolean o_EqualsParseNode (
		final AvailObject object,
		final AvailObject aParseNode)
	{
		return o_Traversed(object).equalsParseNodeType(aParseNode);
	}

	@Override
	AvailObject o_StripMacro (
		final AvailObject object)
	{
		return o_Traversed(object).stripMacro();
	}

	@Override
	AvailObject o_DefinitionMethod (
		final AvailObject object)
	{
		return o_Traversed(object).definitionMethod();
	}

	@Override
	boolean o_EqualsByteArrayTuple (
		final AvailObject object,
		final AvailObject aByteArrayTuple)
	{
		return o_Traversed(object).equalsByteArrayTuple(aByteArrayTuple);
	}

	@Override
	boolean o_CompareFromToWithByteArrayTupleStartingAt (
		final AvailObject object,
		final int i,
		final int tupleSize,
		final AvailObject aByteArrayTuple,
		final int j)
	{
		return o_Traversed(object).compareFromToWithByteArrayTupleStartingAt(
			i, tupleSize, aByteArrayTuple, j);
	}

	@Override
	byte[] o_ByteArray (final AvailObject object)
	{
		return o_Traversed(object).byteArray();
	}

	@Override
	boolean o_IsByteArrayTuple (final AvailObject object)
	{
		return o_Traversed(object).isByteArrayTuple();
	}
}
