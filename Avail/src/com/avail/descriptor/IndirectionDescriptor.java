/**
 * descriptor/IndirectionDescriptor.java Copyright (c) 2010, Mark van Gulik. All
 * rights reserved.
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

import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.node.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.compiler.scanning.TokenDescriptor;
import com.avail.descriptor.ProcessDescriptor.ExecutionState;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;
import com.avail.visitor.AvailSubobjectVisitor;

/**
 * An {@link AvailObject} with an {@link IndirectionDescriptor} keeps track of
 * its target, that which it is pretending to be.  Almost all messages are
 * routed to the target, making it an ideal proxy.
 * <p>
 * When some kinds of objects are compared to each other, say {@link
 * ByteStringDescriptor strings}, a check is first made to see if the objects
 * are at the same location in memory -- the same AvailObject in the current
 * version that uses {@link AvailObjectUsingArrays}.  If so, it immediately
 * returns true.  If not, a more detailed, potentially expensive comparison
 * takes place.  If the objects are found to be equal, one of them is mutated
 * into an indirection (by replacing its descriptor with an {@link
 * IndirectionDescriptor}) to cause subsequent comparisons to be faster.
 * <p>
 * When Avail has had its own garbage collector over the years, it has been
 * possible to strip off indirections during a suitable level of garbage
 * collection.  When combined with the comparison optimization above, this has
 * the effect of collapsing together equal objects.  There was even once a
 * mechanism that collected objects at some garbage collection generation into
 * a set, causing <em>all</em> equal objects in that generation to be compared
 * against each other.  So not only does this mechanism save time, it also saves
 * space.
 * <p>
 * Of course, the cost of traversing indirections, and even just of descriptors
 * may be significant.  That's a complexity price that's paid once, with
 * many mechanisms depending on it to effect higher level optimizations.  My bet
 * is this that will have a net payoff.  Especially since the low level
 * optimizations can be replaced with expression folding, dynamic inlining,
 * object escape analysis, instance-specific optimizations, and a plethora of
 * other just-in-time optimizations.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class IndirectionDescriptor
extends AbstractDescriptor
{

	/**
	 * The slots
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The target {@link AvailObject object} to which my instance is
		 * delegating all behavior.
		 */
		TARGET
	}

	@Override
	public void o_Target (final AvailObject object, final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.TARGET, value);
	}

	@Override
	public @NotNull AvailObject o_Target (final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.TARGET);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (final Enum<?> e)
	{
		return e == ObjectSlots.TARGET;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		object.traversed()
				.printOnAvoidingIndent(aStream, recursionList, indent);
	}

	@Override
	public void o_ScanSubobjects (
		final @NotNull AvailObject object,
		final @NotNull AvailSubobjectVisitor visitor)
	{
		// Manually constructed scanning method.

		visitor.invoke(object, object.target());
	}

	@Override
	public @NotNull AvailObject o_MakeImmutable (final AvailObject object)
	{
		// Make the object immutable so it can be shared safely. If I was
		// mutable I have to make my
			if (isMutable)
		{
			object.descriptor(IndirectionDescriptor.immutable());
			object.target().makeImmutable();
		}
		return object;
	}

	@Override
	public @NotNull AvailObject o_Traversed (final AvailObject object)
	{
		// Answer a non-indirection pointed to (transitively) by object.

		final AvailObject finalObject = object.target().traversed();
		// Shorten the path to one step to reduce amortized traversal costs to
		// approximately inv_Ackermann(N).
		object.target(finalObject);
		return finalObject;
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public Iterator<AvailObject> o_Iterator (final @NotNull AvailObject object)
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
	private final static IndirectionDescriptor mutable = new IndirectionDescriptor(
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
	private final static IndirectionDescriptor immutable = new IndirectionDescriptor(
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
	public boolean o_AcceptsArgTypesFromClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject closureType)
	{
		return o_Traversed(object).acceptsArgTypesFromClosureType(closureType);
	}

	@Override
	public boolean o_AcceptsArgumentsFromContinuationStackp (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp)
	{
		return o_Traversed(object).acceptsArgumentsFromContinuationStackp(
			continuation,
			stackp);
	}

	@Override
	public boolean o_AcceptsArgumentTypesFromContinuationStackp (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp)
	{
		return o_Traversed(object).acceptsArgumentTypesFromContinuationStackp(
			continuation,
			stackp);
	}

	@Override
	public boolean o_AcceptsArrayOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		return o_Traversed(object).acceptsArrayOfArgTypes(argTypes);
	}

	@Override
	public boolean o_AcceptsArrayOfArgValues (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argValues)
	{
		return o_Traversed(object).acceptsArrayOfArgValues(argValues);
	}

	@Override
	public boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argTypes)
	{
		return o_Traversed(object).acceptsTupleOfArgTypes(argTypes);
	}

	@Override
	public boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final @NotNull AvailObject arguments)
	{
		return o_Traversed(object).acceptsTupleOfArguments(arguments);
	}

	@Override
	public void o_AddDependentChunkId (
		final @NotNull AvailObject object,
		final int aChunkIndex)
	{
		o_Traversed(object).addDependentChunkId(aChunkIndex);
	}

	@Override
	public void o_AddImplementation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject implementation)
	{
		o_Traversed(object).addImplementation(implementation);
	}

	@Override
	public void o_AddRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject restrictions)
	{
		o_Traversed(object).addRestrictions(restrictions);
	}

	@Override
	public @NotNull AvailObject o_AddToInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInfinity,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToInfinityCanDestroy(
			anInfinity,
			canDestroy);
	}

	@Override
	public @NotNull AvailObject o_AddToIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object)
				.addToIntegerCanDestroy(anInteger, canDestroy);
	}

	@Override
	public void o_ArgsLocalsStackOutersPrimitive (
		final @NotNull AvailObject object,
		final int args,
		final int locals,
		final int stack,
		final int outers,
		final int primitive)
	{
		o_Traversed(object).argsLocalsStackOutersPrimitive(
			args,
			locals,
			stack,
			outers,
			primitive);
	}

	@Override
	public @NotNull AvailObject o_ArgTypeAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).argTypeAt(index);
	}

	@Override
	public void o_ArgTypeAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).argTypeAtPut(index, value);
	}

	@Override
	public void o_AtAddMessageRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject illegalArgMsgs)
	{
		o_Traversed(object)
				.atAddMessageRestrictions(methodName, illegalArgMsgs);
	}

	@Override
	public void o_AtAddMethodImplementation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject implementation)
	{
		o_Traversed(object).atAddMethodImplementation(
			methodName,
			implementation);
	}

	@Override
	public void o_AtMessageAddBundle (
		final @NotNull AvailObject object,
		final @NotNull AvailObject message,
		final @NotNull AvailObject bundle)
	{
		o_Traversed(object).atMessageAddBundle(message, bundle);
	}

	@Override
	public void o_AtNameAdd (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName,
		final @NotNull AvailObject trueName)
	{
		o_Traversed(object).atNameAdd(stringName, trueName);
	}

	@Override
	public void o_AtNewNamePut (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName,
		final @NotNull AvailObject trueName)
	{
		o_Traversed(object).atNewNamePut(stringName, trueName);
	}

	@Override
	public void o_AtPrivateNameAdd (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName,
		final @NotNull AvailObject trueName)
	{
		o_Traversed(object).atPrivateNameAdd(stringName, trueName);
	}

	@Override
	public @NotNull AvailObject o_BinAddingElementHashLevelCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		return o_Traversed(object).binAddingElementHashLevelCanDestroy(
			elementObject,
			elementObjectHash,
			myLevel,
			canDestroy);
	}

	@Override
	public @NotNull AvailObject o_BinElementAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).binElementAt(index);
	}

	@Override
	public void o_BinElementAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).binElementAtPut(index, value);
	}

	@Override
	public boolean o_BinHasElementHash (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash)
	{
		return o_Traversed(object).binHasElementHash(
			elementObject,
			elementObjectHash);
	}

	@Override
	public void o_BinHash (final AvailObject object, final int value)
	{
		o_Traversed(object).binHash(value);
	}

	@Override
	public @NotNull AvailObject o_BinRemoveElementHashCanDestroy (
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
	public void o_BinSize (final AvailObject object, final int value)
	{
		o_Traversed(object).binSize(value);
	}

	@Override
	public void o_BinUnionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).binUnionType(value);
	}

	@Override
	public void o_BitVector (final AvailObject object, final int value)
	{
		o_Traversed(object).bitVector(value);
	}

	@Override
	public void o_BodyBlock (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).bodyBlock(value);
	}

	@Override
	public void o_BodyBlockRequiresBlockReturnsBlock (
		final @NotNull AvailObject object,
		final @NotNull AvailObject bb,
		final @NotNull AvailObject rqb,
		final @NotNull AvailObject rtb)
	{
		o_Traversed(object).bodyBlockRequiresBlockReturnsBlock(bb, rqb, rtb);
	}

	@Override
	public void o_BodySignature (
		final @NotNull AvailObject object,
		final @NotNull AvailObject signature)
	{
		o_Traversed(object).bodySignature(signature);
	}

	@Override
	public void o_BodySignatureRequiresBlockReturnsBlock (
		final @NotNull AvailObject object,
		final @NotNull AvailObject bs,
		final @NotNull AvailObject rqb,
		final @NotNull AvailObject rtb)
	{
		o_Traversed(object)
				.bodySignatureRequiresBlockReturnsBlock(bs, rqb, rtb);
	}

	@Override
	public void o_BreakpointBlock (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).breakpointBlock(value);
	}

	@Override
	public void o_BuildFilteredBundleTreeFrom (
		final @NotNull AvailObject object,
		final @NotNull AvailObject bundleTree)
	{
		o_Traversed(object).buildFilteredBundleTreeFrom(bundleTree);
	}

	@Override
	public @NotNull AvailObject o_BundleAtMessageParts (
		final @NotNull AvailObject object,
		final @NotNull AvailObject message,
		final @NotNull AvailObject parts,
		final @NotNull AvailObject instructions)
	{
		return o_Traversed(object).bundleAtMessageParts(
			message,
			parts,
			instructions);
	}

	@Override
	public void o_Caller (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).caller(value);
	}

	@Override
	public void o_Closure (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).closure(value);
	}

	@Override
	public void o_ClosureType (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).closureType(value);
	}

	@Override
	public void o_Code (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).code(value);
	}

	@Override
	public void o_CodePoint (final AvailObject object, final int value)
	{
		o_Traversed(object).codePoint(value);
	}

	@Override
	public boolean o_CompareFromToWithStartingAt (
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
	public boolean o_CompareFromToWithAnyTupleStartingAt (
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
	public boolean o_CompareFromToWithByteStringStartingAt (
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
	public boolean o_CompareFromToWithByteTupleStartingAt (
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
	public boolean o_CompareFromToWithNybbleTupleStartingAt (
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
	public boolean o_CompareFromToWithObjectTupleStartingAt (
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
	public boolean o_CompareFromToWithTwoByteStringStartingAt (
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
	public void o_Complete (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).complete(value);
	}

	@Override
	public int o_ComputeHashFromTo (
		final @NotNull AvailObject object,
		final int start,
		final int end)
	{
		return o_Traversed(object).computeHashFromTo(start, end);
	}

	@Override
	public @NotNull AvailObject o_ComputeReturnTypeFromArgumentTypesInterpreter (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull Interpreter anAvailInterpreter)
	{
		return o_Traversed(object)
				.computeReturnTypeFromArgumentTypes(
					argTypes,
					anAvailInterpreter);
	}

	@Override
	public @NotNull AvailObject o_ConcatenateTuplesCanDestroy (
		final @NotNull AvailObject object,
		final boolean canDestroy)
	{
		return o_Traversed(object).concatenateTuplesCanDestroy(canDestroy);
	}

	@Override
	public void o_ConstantBindings (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).constantBindings(value);
	}

	@Override
	public boolean o_ContainsBlock (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosure)
	{
		return o_Traversed(object).containsBlock(aClosure);
	}

	@Override
	public void o_ContentType (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).contentType(value);
	}

	@Override
	public void o_ContingentImpSets (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).contingentImpSets(value);
	}

	@Override
	public void o_Continuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).continuation(value);
	}

	@Override
	public void o_CopyToRestrictedTo (
		final @NotNull AvailObject object,
		final @NotNull AvailObject filteredBundleTree,
		final @NotNull AvailObject visibleNames)
	{
		o_Traversed(object)
				.copyToRestrictedTo(filteredBundleTree, visibleNames);
	}

	@Override
	public @NotNull AvailObject o_CopyTupleFromToCanDestroy (
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
	public boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		return o_Traversed(object).couldEverBeInvokedWith(argTypes);
	}

	@Override
	public @NotNull AvailObject o_CreateTestingTreeWithPositiveMatchesRemainingPossibilities (
		final @NotNull AvailObject object,
		final @NotNull AvailObject positiveTuple,
		final @NotNull AvailObject possibilities)
	{
		return o_Traversed(object)
				.createTestingTreeWithPositiveMatchesRemainingPossibilities(
					positiveTuple,
					possibilities);
	}

	@Override
	public @NotNull AvailObject o_DataAtIndex (final AvailObject object, final int index)
	{
		return o_Traversed(object).dataAtIndex(index);
	}

	@Override
	public void o_DataAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).dataAtIndexPut(index, value);
	}

	@Override
	public void o_DefaultType (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).defaultType(value);
	}

	@Override
	public void o_DependentChunks (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).dependentChunks(value);
	}

	@Override
	public void o_ParsingPc (final AvailObject object, final int value)
	{
		o_Traversed(object).parsingPc(value);
	}

	@Override
	public @NotNull AvailObject o_DivideCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideCanDestroy(aNumber, canDestroy);
	}

	@Override
	public @NotNull AvailObject o_DivideIntoInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInfinity,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoInfinityCanDestroy(
			anInfinity,
			canDestroy);
	}

	@Override
	public @NotNull AvailObject o_DivideIntoIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	public @NotNull AvailObject o_ElementAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).elementAt(index);
	}

	@Override
	public void o_ElementAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).elementAtPut(index, value);
	}

	@Override
	public int o_EndOfZone (final AvailObject object, final int zone)
	{
		return o_Traversed(object).endOfZone(zone);
	}

	@Override
	public int o_EndSubtupleIndexInZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		return o_Traversed(object).endSubtupleIndexInZone(zone);
	}

	@Override
	public boolean o_Equals (final AvailObject object, final AvailObject another)
	{
		return o_Traversed(object).equals(another);
	}

	@Override
	public boolean o_EqualsAnyTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherTuple)
	{
		return o_Traversed(object).equalsAnyTuple(anotherTuple);
	}

	@Override
	public boolean o_EqualsByteString (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aByteString)
	{
		return o_Traversed(object).equalsByteString(aByteString);
	}

	@Override
	public boolean o_EqualsByteTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aByteTuple)
	{
		return o_Traversed(object).equalsByteTuple(aByteTuple);
	}

	@Override
	public boolean o_EqualsCharacterWithCodePoint (
		final @NotNull AvailObject object,
		final int otherCodePoint)
	{
		return o_Traversed(object).equalsCharacterWithCodePoint(otherCodePoint);
	}

	@Override
	public boolean o_EqualsClosure (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosure)
	{
		return o_Traversed(object).equalsClosure(aClosure);
	}

	@Override
	public boolean o_EqualsClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		return o_Traversed(object).equalsClosureType(aClosureType);
	}

	@Override
	public boolean o_EqualsCompiledCode (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCode)
	{
		return o_Traversed(object).equalsCompiledCode(aCompiledCode);
	}

	@Override
	public boolean o_EqualsContainer (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainer)
	{
		return o_Traversed(object).equalsContainer(aContainer);
	}

	@Override
	public boolean o_EqualsContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		return o_Traversed(object).equalsContainerType(aContainerType);
	}

	@Override
	public boolean o_EqualsContinuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuation)
	{
		return o_Traversed(object).equalsContinuation(aContinuation);
	}

	@Override
	public boolean o_EqualsContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return o_Traversed(object).equalsContinuationType(aType);
	}

	@Override
	public boolean o_EqualsDouble (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aDoubleObject)
	{
		return o_Traversed(object).equalsDouble(aDoubleObject);
	}

	@Override
	public boolean o_EqualsFloat (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFloatObject)
	{
		return o_Traversed(object).equalsFloat(aFloatObject);
	}

	@Override
	public boolean o_EqualsGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return o_Traversed(object).equalsGeneralizedClosureType(aType);
	}

	@Override
	public boolean o_EqualsInfinity (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInfinity)
	{
		return o_Traversed(object).equalsInfinity(anInfinity);
	}

	@Override
	public boolean o_EqualsInteger (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anAvailInteger)
	{
		return o_Traversed(object).equalsInteger(anAvailInteger);
	}

	@Override
	public boolean o_EqualsIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return o_Traversed(object).equalsIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public boolean o_EqualsMap (final AvailObject object, final AvailObject aMap)
	{
		return o_Traversed(object).equalsMap(aMap);
	}

	@Override
	public boolean o_EqualsMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return o_Traversed(object).equalsMapType(aMapType);
	}

	@Override
	public boolean o_EqualsNybbleTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNybbleTuple)
	{
		return o_Traversed(object).equalsNybbleTuple(aNybbleTuple);
	}

	@Override
	public boolean o_EqualsObject (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObject)
	{
		return o_Traversed(object).equalsObject(anObject);
	}

	@Override
	public boolean o_EqualsObjectTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectTuple)
	{
		return o_Traversed(object).equalsObjectTuple(anObjectTuple);
	}

	@Override
	public boolean o_EqualsPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType)
	{
		return o_Traversed(object).equalsPrimitiveType(aPrimitiveType);
	}

	@Override
	public boolean o_EqualsSet (final AvailObject object, final AvailObject aSet)
	{
		return o_Traversed(object).equalsSet(aSet);
	}

	@Override
	public boolean o_EqualsSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return o_Traversed(object).equalsSetType(aSetType);
	}

	@Override
	public boolean o_EqualsTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return o_Traversed(object).equalsTupleType(aTupleType);
	}

	@Override
	public boolean o_EqualsTwoByteString (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTwoByteString)
	{
		return o_Traversed(object).equalsTwoByteString(aTwoByteString);
	}

	@Override
	public void o_ExecutionState (
		final @NotNull AvailObject object,
		final @NotNull ExecutionState value)
	{
		o_Traversed(object).executionState(value);
	}

	@Override
	public byte o_ExtractNybbleFromTupleAt (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).extractNybbleFromTupleAt(index);
	}

	@Override
	public void o_FieldMap (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).fieldMap(value);
	}

	@Override
	public void o_FieldTypeMap (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).fieldTypeMap(value);
	}

	@Override
	public List<AvailObject> o_FilterByTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		return o_Traversed(object).filterByTypes(argTypes);
	}

	@Override
	public void o_FilteredBundleTree (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).filteredBundleTree(value);
	}

	@Override
	public void o_FirstTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).firstTupleType(value);
	}

	@Override
	public @NotNull AvailObject o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone (
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
	public boolean o_GreaterThanInteger (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).greaterThanInteger(another);
	}

	@Override
	public boolean o_GreaterThanSignedInfinity (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).greaterThanSignedInfinity(another);
	}

	@Override
	public boolean o_HasElement (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject)
	{
		return o_Traversed(object).hasElement(elementObject);
	}

	@Override
	public void o_Hash (final AvailObject object, final int value)
	{
		o_Traversed(object).hash(value);
	}

	@Override
	public int o_HashFromTo (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		return o_Traversed(object).hashFromTo(startIndex, endIndex);
	}

	@Override
	public void o_HashOrZero (final AvailObject object, final int value)
	{
		o_Traversed(object).hashOrZero(value);
	}

	@Override
	public boolean o_HasKey (
		final @NotNull AvailObject object,
		final @NotNull AvailObject keyObject)
	{
		return o_Traversed(object).hasKey(keyObject);
	}

	@Override
	public boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance)
	{
		return o_Traversed(object).hasObjectInstance(potentialInstance);
	}

	@Override
	public void o_HiLevelTwoChunkLowOffset (
		final @NotNull AvailObject object,
		final int value)
	{
		o_Traversed(object).hiLevelTwoChunkLowOffset(value);
	}

	@Override
	public void o_HiNumLocalsLowNumArgs (
		final @NotNull AvailObject object,
		final int value)
	{
		o_Traversed(object).hiNumLocalsLowNumArgs(value);
	}

	@Override
	public void o_HiPrimitiveLowNumArgsAndLocalsAndStack (
		final @NotNull AvailObject object,
		final int value)
	{
		o_Traversed(object).hiPrimitiveLowNumArgsAndLocalsAndStack(value);
	}

	@Override
	public void o_HiStartingChunkIndexLowNumOuters (
		final @NotNull AvailObject object,
		final int value)
	{
		o_Traversed(object).hiStartingChunkIndexLowNumOuters(value);
	}

	@Override
	public List<AvailObject> o_ImplementationsAtOrBelow (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		return o_Traversed(object).implementationsAtOrBelow(argTypes);
	}

	@Override
	public void o_ImplementationsTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).implementationsTuple(value);
	}

	@Override
	public @NotNull AvailObject o_IncludeBundle (
		final @NotNull AvailObject object,
		final @NotNull AvailObject messageBundle)
	{
		return o_Traversed(object).includeBundle(
			messageBundle);
	}

	@Override
	public boolean o_Includes (final AvailObject object, final AvailObject imp)
	{
		return o_Traversed(object).includes(imp);
	}

	@Override
	public void o_InclusiveFlags (final AvailObject object, final int value)
	{
		o_Traversed(object).inclusiveFlags(value);
	}

	@Override
	public void o_Incomplete (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).incomplete(value);
	}

	@Override
	public void o_Index (final AvailObject object, final int value)
	{
		o_Traversed(object).index(value);
	}

	@Override
	public void o_InnerType (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).innerType(value);
	}

	@Override
	public void o_Instance (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).instance(value);
	}

	@Override
	public void o_InternalHash (final AvailObject object, final int value)
	{
		o_Traversed(object).internalHash(value);
	}

	@Override
	public void o_InterruptRequestFlag (
		final @NotNull AvailObject object,
		final int value)
	{
		o_Traversed(object).interruptRequestFlag(value);
	}

	@Override
	public void o_InvocationCount (final AvailObject object, final int value)
	{
		o_Traversed(object).invocationCount(value);
	}

	@Override
	public boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherObject)
	{
		return o_Traversed(object).isBetterRepresentationThan(anotherObject);
	}

	@Override
	public boolean o_IsBetterRepresentationThanTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return o_Traversed(object).isBetterRepresentationThanTupleType(
			aTupleType);
	}

	@Override
	public boolean o_IsBinSubsetOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialSuperset)
	{
		return o_Traversed(object).isBinSubsetOf(potentialSuperset);
	}

	@Override
	public boolean o_IsInstanceOfSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return o_Traversed(object).isInstanceOfSubtypeOf(aType);
	}

	@Override
	public void o_IsSaved (final AvailObject object, final boolean aBoolean)
	{
		o_Traversed(object).isSaved(aBoolean);
	}

	@Override
	public boolean o_IsSubsetOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).isSubsetOf(another);
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return o_Traversed(object).isSubtypeOf(aType);
	}

	@Override
	public boolean o_IsSupertypeOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		return o_Traversed(object).isSupertypeOfClosureType(aClosureType);
	}

	@Override
	public boolean o_IsSupertypeOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		return o_Traversed(object).isSupertypeOfContainerType(aContainerType);
	}

	@Override
	public boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		return o_Traversed(object).isSupertypeOfContinuationType(
			aContinuationType);
	}

	@Override
	public boolean o_IsSupertypeOfCyclicType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCyclicType)
	{
		return o_Traversed(object).isSupertypeOfCyclicType(aCyclicType);
	}

	@Override
	public boolean o_IsSupertypeOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		return o_Traversed(object).isSupertypeOfGeneralizedClosureType(
			aGeneralizedClosureType);
	}

	@Override
	public boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return o_Traversed(object).isSupertypeOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	public boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return o_Traversed(object).isSupertypeOfMapType(aMapType);
	}

	@Override
	public boolean o_IsSupertypeOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		return o_Traversed(object).isSupertypeOfObjectMeta(anObjectMeta);
	}

	@Override
	public boolean o_IsSupertypeOfObjectMetaMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMetaMeta)
	{
		return o_Traversed(object)
				.isSupertypeOfObjectMetaMeta(anObjectMetaMeta);
	}

	@Override
	public boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		return o_Traversed(object).isSupertypeOfObjectType(anObjectType);
	}

	@Override
	public boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType)
	{
		return o_Traversed(object).isSupertypeOfPrimitiveType(aPrimitiveType);
	}

	@Override
	public boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return o_Traversed(object).isSupertypeOfSetType(aSetType);
	}

	@Override
	public boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return o_Traversed(object).isSupertypeOfTupleType(aTupleType);
	}

	@Override
	public void o_IsValid (final AvailObject object, final boolean aBoolean)
	{
		o_Traversed(object).isValid(aBoolean);
	}

	@Override
	public boolean o_IsValidForArgumentTypesInterpreter (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull Interpreter interpreter)
	{
		return o_Traversed(object).isValidForArgumentTypesInterpreter(
			argTypes,
			interpreter);
	}

	@Override
	public @NotNull AvailObject o_KeyAtIndex (final AvailObject object, final int index)
	{
		return o_Traversed(object).keyAtIndex(index);
	}

	@Override
	public void o_KeyAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject keyObject)
	{
		o_Traversed(object).keyAtIndexPut(index, keyObject);
	}

	@Override
	public void o_KeyType (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).keyType(value);
	}

	@Override
	public boolean o_LessOrEqual (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).lessOrEqual(another);
	}

	@Override
	public boolean o_LessThan (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).lessThan(another);
	}

	@Override
	public void o_LevelTwoChunkIndexOffset (
		final @NotNull AvailObject object,
		final int index,
		final int offset)
	{
		o_Traversed(object).levelTwoChunkIndexOffset(index, offset);
	}

	@Override
	public void o_Literal (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).literal(value);
	}

	@Override
	public @NotNull AvailObject o_LiteralAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).literalAt(index);
	}

	@Override
	public void o_LiteralAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).literalAtPut(index, value);
	}

	@Override
	public @NotNull AvailObject o_LocalOrArgOrStackAt (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).localOrArgOrStackAt(index);
	}

	@Override
	public void o_LocalOrArgOrStackAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).localOrArgOrStackAtPut(index, value);
	}

	@Override
	public @NotNull AvailObject o_LocalTypeAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).localTypeAt(index);
	}

	@Override
	public @NotNull AvailObject o_LookupByTypesFromArray (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argumentTypeArray)
	{
		return o_Traversed(object).lookupByTypesFromArray(argumentTypeArray);
	}

	@Override
	public @NotNull AvailObject o_LookupByTypesFromContinuationStackp (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp)
	{
		return o_Traversed(object).lookupByTypesFromContinuationStackp(
			continuation,
			stackp);
	}

	@Override
	public @NotNull AvailObject o_LookupByTypesFromTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argumentTypeTuple)
	{
		return o_Traversed(object).lookupByTypesFromTuple(argumentTypeTuple);
	}

	@Override
	public @NotNull AvailObject o_LookupByValuesFromArray (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argumentArray)
	{
		return o_Traversed(object).lookupByValuesFromArray(argumentArray);
	}

	@Override
	public @NotNull AvailObject o_LookupByValuesFromContinuationStackp (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp)
	{
		return o_Traversed(object).lookupByValuesFromContinuationStackp(
			continuation,
			stackp);
	}

	@Override
	public @NotNull AvailObject o_LookupByValuesFromTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argumentTuple)
	{
		return o_Traversed(object).lookupByValuesFromTuple(argumentTuple);
	}

	@Override
	public void o_LowerBound (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).lowerBound(value);
	}

	@Override
	public void o_LowerInclusiveUpperInclusive (
		final @NotNull AvailObject object,
		final boolean lowInc,
		final boolean highInc)
	{
		o_Traversed(object).lowerInclusiveUpperInclusive(lowInc, highInc);
	}

	@Override
	public @NotNull AvailObject o_MapAt (
		final @NotNull AvailObject object,
		final @NotNull AvailObject keyObject)
	{
		return o_Traversed(object).mapAt(keyObject);
	}

	@Override
	public @NotNull AvailObject o_MapAtPuttingCanDestroy (
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
	public void o_MapSize (final AvailObject object, final int value)
	{
		o_Traversed(object).mapSize(value);
	}

	@Override
	public @NotNull AvailObject o_MapWithoutKeyCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject keyObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapWithoutKeyCanDestroy(
			keyObject,
			canDestroy);
	}

	@Override
	public void o_Message (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).message(value);
	}

	@Override
	public void o_MessageParts (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).messageParts(value);
	}

	@Override
	public void o_Methods (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).methods(value);
	}

	@Override
	public @NotNull AvailObject o_MinusCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).minusCanDestroy(aNumber, canDestroy);
	}

	@Override
	public @NotNull AvailObject o_MultiplyByInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInfinity,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByInfinityCanDestroy(
			anInfinity,
			canDestroy);
	}

	@Override
	public @NotNull AvailObject o_MultiplyByIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	public void o_MyObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).myObjectMeta(value);
	}

	@Override
	public void o_MyObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).myObjectType(value);
	}

	@Override
	public void o_MyRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).myRestrictions(value);
	}

	@Override
	public void o_MyType (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).myType(value);
	}

	@Override
	public void o_Name (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).name(value);
	}

	@Override
	public void o_Names (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).names(value);
	}

	@Override
	public boolean o_NameVisible (
		final @NotNull AvailObject object,
		final @NotNull AvailObject trueName)
	{
		return o_Traversed(object).nameVisible(trueName);
	}

	@Override
	public void o_NecessaryImplementationSetChanged (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anImplementationSet)
	{
		o_Traversed(object).necessaryImplementationSetChanged(
			anImplementationSet);
	}

	@Override
	public void o_NewNames (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).newNames(value);
	}

	@Override
	public void o_Next (final AvailObject object, final AvailObject nextChunk)
	{
		o_Traversed(object).next(nextChunk);
	}

	@Override
	public void o_NextIndex (final AvailObject object, final int value)
	{
		o_Traversed(object).nextIndex(value);
	}

	@Override
	public void o_NumBlanks (final AvailObject object, final int value)
	{
		o_Traversed(object).numBlanks(value);
	}

	@Override
	public void o_NumFloats (final AvailObject object, final int value)
	{
		o_Traversed(object).numFloats(value);
	}

	@Override
	public void o_NumIntegers (final AvailObject object, final int value)
	{
		o_Traversed(object).numIntegers(value);
	}

	@Override
	public void o_NumObjects (final AvailObject object, final int value)
	{
		o_Traversed(object).numObjects(value);
	}

	@Override
	public void o_Nybbles (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).nybbles(value);
	}

	@Override
	public boolean o_OptionallyNilOuterVar (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).optionallyNilOuterVar(index);
	}

	@Override
	public @NotNull AvailObject o_OuterTypeAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).outerTypeAt(index);
	}

	@Override
	public void o_OuterTypesLocalTypes (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleOfOuterTypes,
		final @NotNull AvailObject tupleOfLocalContainerTypes)
	{
		o_Traversed(object).outerTypesLocalTypes(
			tupleOfOuterTypes,
			tupleOfLocalContainerTypes);
	}

	@Override
	public @NotNull AvailObject o_OuterVarAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).outerVarAt(index);
	}

	@Override
	public void o_OuterVarAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).outerVarAtPut(index, value);
	}

	@Override
	public void o_Pad1 (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).pad1(value);
	}

	@Override
	public void o_Pad2 (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).pad2(value);
	}

	@Override
	public void o_Parent (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).parent(value);
	}

	@Override
	public void o_Pc (final AvailObject object, final int value)
	{
		o_Traversed(object).pc(value);
	}

	@Override
	public @NotNull AvailObject o_PlusCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).plusCanDestroy(aNumber, canDestroy);
	}

	@Override
	public int o_PopulateTupleStartingAt (
		final @NotNull AvailObject object,
		final @NotNull AvailObject mutableTuple,
		final int startingIndex)
	{
		return o_Traversed(object).populateTupleStartingAt(
			mutableTuple,
			startingIndex);
	}

	@Override
	public void o_Previous (
		final @NotNull AvailObject object,
		final @NotNull AvailObject previousChunk)
	{
		o_Traversed(object).previous(previousChunk);
	}

	@Override
	public void o_PreviousIndex (final AvailObject object, final int value)
	{
		o_Traversed(object).previousIndex(value);
	}

	@Override
	public void o_Priority (final AvailObject object, final int value)
	{
		o_Traversed(object).priority(value);
	}

	@Override
	public @NotNull AvailObject o_PrivateAddElement (
		final @NotNull AvailObject object,
		final @NotNull AvailObject element)
	{
		return o_Traversed(object).privateAddElement(element);
	}

	@Override
	public @NotNull AvailObject o_PrivateExcludeElement (
		final @NotNull AvailObject object,
		final @NotNull AvailObject element)
	{
		return o_Traversed(object).privateExcludeElement(element);
	}

	@Override
	public @NotNull AvailObject o_PrivateExcludeElementKnownIndex (
		final @NotNull AvailObject object,
		final @NotNull AvailObject element,
		final int knownIndex)
	{
		return o_Traversed(object).privateExcludeElementKnownIndex(
			element,
			knownIndex);
	}

	@Override
	public @NotNull AvailObject o_PrivateExcludeKey (
		final @NotNull AvailObject object,
		final @NotNull AvailObject keyObject)
	{
		return o_Traversed(object).privateExcludeKey(keyObject);
	}

	@Override
	public @NotNull AvailObject o_PrivateMapAtPut (
		final @NotNull AvailObject object,
		final @NotNull AvailObject keyObject,
		final @NotNull AvailObject valueObject)
	{
		return o_Traversed(object).privateMapAtPut(keyObject, valueObject);
	}

	@Override
	public void o_PrivateNames (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).privateNames(value);
	}

	@Override
	public void o_PrivateTestingTree (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).privateTestingTree(value);
	}

	@Override
	public void o_ProcessGlobals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).processGlobals(value);
	}

	@Override
	public short o_RawByteAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).rawByteAt(index);
	}

	@Override
	public void o_RawByteAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		o_Traversed(object).rawByteAtPut(index, anInteger);
	}

	@Override
	public short o_RawByteForCharacterAt (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawByteForCharacterAt(index);
	}

	@Override
	public void o_RawByteForCharacterAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		o_Traversed(object).rawByteForCharacterAtPut(index, anInteger);
	}

	@Override
	public byte o_RawNybbleAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).rawNybbleAt(index);
	}

	@Override
	public void o_RawNybbleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final byte aNybble)
	{
		o_Traversed(object).rawNybbleAtPut(index, aNybble);
	}

	@Override
	public void o_RawQuad1 (final AvailObject object, final int value)
	{
		o_Traversed(object).rawQuad1(value);
	}

	@Override
	public void o_RawQuad2 (final AvailObject object, final int value)
	{
		o_Traversed(object).rawQuad2(value);
	}

	@Override
	public int o_RawQuadAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).rawQuadAt(index);
	}

	@Override
	public void o_RawQuadAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).rawQuadAtPut(index, value);
	}

	@Override
	public short o_RawShortForCharacterAt (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawShortForCharacterAt(index);
	}

	@Override
	public void o_RawShortForCharacterAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger)
	{
		o_Traversed(object).rawShortForCharacterAtPut(index, anInteger);
	}

	@Override
	public int o_RawSignedIntegerAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).rawSignedIntegerAt(index);
	}

	@Override
	public void o_RawSignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).rawSignedIntegerAtPut(index, value);
	}

	@Override
	public long o_RawUnsignedIntegerAt (
		final @NotNull AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawUnsignedIntegerAt(index);
	}

	@Override
	public void o_RawUnsignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).rawUnsignedIntegerAtPut(index, value);
	}

	@Override
	public void o_RemoveDependentChunkId (
		final @NotNull AvailObject object,
		final int aChunkIndex)
	{
		o_Traversed(object).removeDependentChunkId(aChunkIndex);
	}

	@Override
	public void o_RemoveFrom (
		final @NotNull AvailObject object,
		final @NotNull Interpreter anInterpreter)
	{
		o_Traversed(object).removeFrom(anInterpreter);
	}

	@Override
	public void o_RemoveImplementation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject implementation)
	{
		o_Traversed(object).removeImplementation(implementation);
	}

	@Override
	public boolean o_RemoveBundle (
		final @NotNull AvailObject object,
		final @NotNull AvailObject bundle)
	{
		return o_Traversed(object).removeBundle(
			bundle);
	}

	@Override
	public void o_RemoveRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject obsoleteRestrictions)
	{
		o_Traversed(object).removeRestrictions(obsoleteRestrictions);
	}

	@Override
	public void o_RequiresBlock (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).requiresBlock(value);
	}

	@Override
	public void o_ResolvedForwardWithName (
		final @NotNull AvailObject object,
		final @NotNull AvailObject forwardImplementation,
		final @NotNull AvailObject methodName)
	{
		o_Traversed(object).resolvedForwardWithName(
			forwardImplementation,
			methodName);
	}

	@Override
	public void o_Restrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).restrictions(value);
	}

	@Override
	public void o_ReturnsBlock (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).returnsBlock(value);
	}

	@Override
	public void o_ReturnType (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).returnType(value);
	}

	@Override
	public void o_RootBin (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).rootBin(value);
	}

	@Override
	public void o_SecondTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).secondTupleType(value);
	}

	@Override
	public @NotNull AvailObject o_SetIntersectionCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setIntersectionCanDestroy(
			otherSet,
			canDestroy);
	}

	@Override
	public @NotNull AvailObject o_SetMinusCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setMinusCanDestroy(otherSet, canDestroy);
	}

	@Override
	public void o_SetSize (final AvailObject object, final int value)
	{
		o_Traversed(object).setSize(value);
	}

	@Override
	public void o_SetSubtupleForZoneTo (
		final @NotNull AvailObject object,
		final int zoneIndex,
		final @NotNull AvailObject newTuple)
	{
		o_Traversed(object).setSubtupleForZoneTo(zoneIndex, newTuple);
	}

	@Override
	public @NotNull AvailObject o_SetUnionCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setUnionCanDestroy(otherSet, canDestroy);
	}

	@Override
	public void o_SetValue (final AvailObject object, final AvailObject newValue)
	{
		o_Traversed(object).setValue(newValue);
	}

	@Override
	public @NotNull AvailObject o_SetWithElementCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject newElementObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).setWithElementCanDestroy(
			newElementObject,
			canDestroy);
	}

	@Override
	public @NotNull AvailObject o_SetWithoutElementCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObjectToExclude,
		final boolean canDestroy)
	{
		return o_Traversed(object).setWithoutElementCanDestroy(
			elementObjectToExclude,
			canDestroy);
	}

	@Override
	public void o_Signature (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).signature(value);
	}

	@Override
	public void o_Size (final AvailObject object, final int value)
	{
		o_Traversed(object).size(value);
	}

	@Override
	public int o_SizeOfZone (final AvailObject object, final int zone)
	{
		return o_Traversed(object).sizeOfZone(zone);
	}

	@Override
	public void o_SizeRange (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).sizeRange(value);
	}

	@Override
	public void o_SpecialActions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).specialActions(value);
	}

	@Override
	public @NotNull AvailObject o_StackAt (final AvailObject object, final int slotIndex)
	{
		return o_Traversed(object).stackAt(slotIndex);
	}

	@Override
	public void o_StackAtPut (
		final @NotNull AvailObject object,
		final int slotIndex,
		final @NotNull AvailObject anObject)
	{
		o_Traversed(object).stackAtPut(slotIndex, anObject);
	}

	@Override
	public void o_Stackp (final AvailObject object, final int value)
	{
		o_Traversed(object).stackp(value);
	}

	@Override
	public void o_Start (final AvailObject object, final int value)
	{
		o_Traversed(object).start(value);
	}

	@Override
	public void o_StartingChunkIndex (final AvailObject object, final int value)
	{
		o_Traversed(object).startingChunkIndex(value);
	}

	@Override
	public int o_StartOfZone (final AvailObject object, final int zone)
	{
		return o_Traversed(object).startOfZone(zone);
	}

	@Override
	public int o_StartSubtupleIndexInZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		return o_Traversed(object).startSubtupleIndexInZone(zone);
	}

	@Override
	public void o_String (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).string(value);
	}

	@Override
	public @NotNull AvailObject o_SubtractFromInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInfinity,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromInfinityCanDestroy(
			anInfinity,
			canDestroy);
	}

	@Override
	public @NotNull AvailObject o_SubtractFromIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	public @NotNull AvailObject o_SubtupleForZone (
		final @NotNull AvailObject object,
		final int zone)
	{
		return o_Traversed(object).subtupleForZone(zone);
	}

	@Override
	public @NotNull AvailObject o_TimesCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).timesCanDestroy(aNumber, canDestroy);
	}

	@Override
	public void o_TokenType (final AvailObject object, final TokenDescriptor.TokenType value)
	{
		o_Traversed(object).tokenType(value);
	}

	@Override
	public int o_TranslateToZone (
		final @NotNull AvailObject object,
		final int tupleIndex,
		final int zoneIndex)
	{
		return o_Traversed(object).translateToZone(tupleIndex, zoneIndex);
	}

	@Override
	public @NotNull AvailObject o_TrueNamesForStringName (
		final @NotNull AvailObject object,
		final @NotNull AvailObject stringName)
	{
		return o_Traversed(object).trueNamesForStringName(stringName);
	}

	@Override
	public @NotNull AvailObject o_TruncateTo (
		final @NotNull AvailObject object,
		final int newTupleSize)
	{
		return o_Traversed(object).truncateTo(newTupleSize);
	}

	@Override
	public @NotNull AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).tupleAt(index);
	}

	@Override
	public void o_TupleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject aNybbleObject)
	{
		o_Traversed(object).tupleAtPut(index, aNybbleObject);
	}

	@Override
	public @NotNull AvailObject o_TupleAtPuttingCanDestroy (
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
	public int o_TupleIntAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).tupleIntAt(index);
	}

	@Override
	public void o_Type (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).type(value);
	}

	@Override
	public @NotNull AvailObject o_TypeAtIndex (final AvailObject object, final int index)
	{
		return o_Traversed(object).typeAtIndex(index);
	}

	@Override
	public boolean o_TypeEquals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return o_Traversed(object).typeEquals(aType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).typeIntersection(another);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		return o_Traversed(object).typeIntersectionOfClosureType(aClosureType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfClosureTypeCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType,
		final boolean canDestroy)
	{
		return o_Traversed(object).typeIntersectionOfClosureTypeCanDestroy(
			aClosureType,
			canDestroy);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		return o_Traversed(object).typeIntersectionOfContainerType(
			aContainerType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		return o_Traversed(object).typeIntersectionOfContinuationType(
			aContinuationType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfCyclicType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCyclicType)
	{
		return o_Traversed(object).typeIntersectionOfCyclicType(aCyclicType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		return o_Traversed(object).typeIntersectionOfGeneralizedClosureType(
			aGeneralizedClosureType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfGeneralizedClosureTypeCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType,
		final boolean canDestroy)
	{
		return o_Traversed(object)
				.typeIntersectionOfGeneralizedClosureTypeCanDestroy(
					aGeneralizedClosureType,
					canDestroy);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return o_Traversed(object).typeIntersectionOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return o_Traversed(object).typeIntersectionOfMapType(aMapType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject someMeta)
	{
		return o_Traversed(object).typeIntersectionOfMeta(someMeta);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		return o_Traversed(object).typeIntersectionOfObjectMeta(anObjectMeta);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectMetaMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMetaMeta)
	{
		return o_Traversed(object).typeIntersectionOfObjectMetaMeta(
			anObjectMetaMeta);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		return o_Traversed(object).typeIntersectionOfObjectType(anObjectType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return o_Traversed(object).typeIntersectionOfSetType(aSetType);
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return o_Traversed(object).typeIntersectionOfTupleType(aTupleType);
	}

	@Override
	public void o_TypeTuple (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).typeTuple(value);
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return o_Traversed(object).typeUnion(another);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		return o_Traversed(object).typeUnionOfClosureType(aClosureType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfClosureTypeCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType,
		final boolean canDestroy)
	{
		return o_Traversed(object).typeUnionOfClosureTypeCanDestroy(
			aClosureType,
			canDestroy);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		return o_Traversed(object).typeUnionOfContainerType(aContainerType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		return o_Traversed(object).typeUnionOfContinuationType(
			aContinuationType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfCyclicType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCyclicType)
	{
		return o_Traversed(object).typeUnionOfCyclicType(aCyclicType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		return o_Traversed(object).typeUnionOfGeneralizedClosureType(
			aGeneralizedClosureType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return o_Traversed(object).typeUnionOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return o_Traversed(object).typeUnionOfMapType(aMapType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMeta)
	{
		return o_Traversed(object).typeUnionOfObjectMeta(anObjectMeta);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectMetaMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectMetaMeta)
	{
		return o_Traversed(object).typeUnionOfObjectMetaMeta(anObjectMetaMeta);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		return o_Traversed(object).typeUnionOfObjectType(anObjectType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return o_Traversed(object).typeUnionOfSetType(aSetType);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return o_Traversed(object).typeUnionOfTupleType(aTupleType);
	}

	@Override
	public void o_Unclassified (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).unclassified(value);
	}

	@Override
	public @NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		return o_Traversed(object).unionOfTypesAtThrough(startIndex, endIndex);
	}

	@Override
	public int o_UntranslatedDataAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).untranslatedDataAt(index);
	}

	@Override
	public void o_UntranslatedDataAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).untranslatedDataAtPut(index, value);
	}

	@Override
	public void o_UpperBound (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).upperBound(value);
	}

	@Override
	public @NotNull AvailObject o_ValidateArgumentTypesInterpreterIfFail (
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
	public void o_Validity (final AvailObject object, final int value)
	{
		o_Traversed(object).validity(value);
	}

	@Override
	public void o_Value (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).value(value);
	}

	@Override
	public @NotNull AvailObject o_ValueAtIndex (final AvailObject object, final int index)
	{
		return o_Traversed(object).valueAtIndex(index);
	}

	@Override
	public void o_ValueAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final @NotNull AvailObject valueObject)
	{
		o_Traversed(object).valueAtIndexPut(index, valueObject);
	}

	@Override
	public void o_ValueType (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).valueType(value);
	}

	@Override
	public void o_VariableBindings (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).variableBindings(value);
	}

	@Override
	public void o_Vectors (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).vectors(value);
	}

	@Override
	public void o_VisibleNames (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).visibleNames(value);
	}

	@Override
	public void o_WhichOne (final AvailObject object, final int value)
	{
		o_Traversed(object).whichOne(value);
	}

	@Override
	public void o_Wordcodes (final AvailObject object, final AvailObject value)
	{
		o_Traversed(object).wordcodes(value);
	}

	@Override
	public int o_ZoneForIndex (final AvailObject object, final int index)
	{
		return o_Traversed(object).zoneForIndex(index);
	}

	@Override
	public String o_AsNativeString (final AvailObject object)
	{
		return o_Traversed(object).asNativeString();
	}

	@Override
	public @NotNull AvailObject o_AsObject (final AvailObject object)
	{
		return o_Traversed(object).asObject();
	}

	@Override
	public @NotNull AvailObject o_AsSet (final AvailObject object)
	{
		return o_Traversed(object).asSet();
	}

	@Override
	public @NotNull AvailObject o_AsTuple (final AvailObject object)
	{
		return o_Traversed(object).asTuple();
	}

	@Override
	public @NotNull AvailObject o_BecomeExactType (final AvailObject object)
	{
		return o_Traversed(object).becomeExactType();
	}

	@Override
	public void o_BecomeRealTupleType (final AvailObject object)
	{
		o_Traversed(object).becomeRealTupleType();
	}

	@Override
	public int o_BinHash (final AvailObject object)
	{
		return o_Traversed(object).binHash();
	}

	@Override
	public int o_BinSize (final AvailObject object)
	{
		return o_Traversed(object).binSize();
	}

	@Override
	public @NotNull AvailObject o_BinUnionType (final AvailObject object)
	{
		return o_Traversed(object).binUnionType();
	}

	@Override
	public int o_BitsPerEntry (final AvailObject object)
	{
		return o_Traversed(object).bitsPerEntry();
	}

	@Override
	public int o_BitVector (final AvailObject object)
	{
		return o_Traversed(object).bitVector();
	}

	@Override
	public @NotNull AvailObject o_BodyBlock (final AvailObject object)
	{
		return o_Traversed(object).bodyBlock();
	}

	@Override
	public @NotNull AvailObject o_BodySignature (final AvailObject object)
	{
		return o_Traversed(object).bodySignature();
	}

	@Override
	public @NotNull AvailObject o_BreakpointBlock (final AvailObject object)
	{
		return o_Traversed(object).breakpointBlock();
	}

	@Override
	public @NotNull AvailObject o_Caller (final AvailObject object)
	{
		return o_Traversed(object).caller();
	}

	@Override
	public boolean o_CanComputeHashOfType (final AvailObject object)
	{
		return o_Traversed(object).canComputeHashOfType();
	}

	@Override
	public int o_Capacity (final AvailObject object)
	{
		return o_Traversed(object).capacity();
	}

	@Override
	public void o_CleanUpAfterCompile (final AvailObject object)
	{
		o_Traversed(object).cleanUpAfterCompile();
	}

	@Override
	public void o_ClearValue (final AvailObject object)
	{
		o_Traversed(object).clearValue();
	}

	@Override
	public @NotNull AvailObject o_Closure (final AvailObject object)
	{
		return o_Traversed(object).closure();
	}

	@Override
	public @NotNull AvailObject o_ClosureType (final AvailObject object)
	{
		return o_Traversed(object).closureType();
	}

	@Override
	public @NotNull AvailObject o_Code (final AvailObject object)
	{
		return o_Traversed(object).code();
	}

	@Override
	public int o_CodePoint (final AvailObject object)
	{
		return o_Traversed(object).codePoint();
	}

	@Override
	public @NotNull AvailObject o_Complete (final AvailObject object)
	{
		return o_Traversed(object).complete();
	}

	@Override
	public @NotNull AvailObject o_ConstantBindings (final AvailObject object)
	{
		return o_Traversed(object).constantBindings();
	}

	@Override
	public @NotNull AvailObject o_ContentType (final AvailObject object)
	{
		return o_Traversed(object).contentType();
	}

	@Override
	public @NotNull AvailObject o_ContingentImpSets (final AvailObject object)
	{
		return o_Traversed(object).contingentImpSets();
	}

	@Override
	public @NotNull AvailObject o_Continuation (final AvailObject object)
	{
		return o_Traversed(object).continuation();
	}

	@Override
	public @NotNull AvailObject o_CopyAsMutableContinuation (final AvailObject object)
	{
		return o_Traversed(object).copyAsMutableContinuation();
	}

	@Override
	public @NotNull AvailObject o_CopyAsMutableObjectTuple (final AvailObject object)
	{
		return o_Traversed(object).copyAsMutableObjectTuple();
	}

	@Override
	public @NotNull AvailObject o_CopyAsMutableSpliceTuple (final AvailObject object)
	{
		return o_Traversed(object).copyAsMutableSpliceTuple();
	}

	@Override
	public @NotNull AvailObject o_CopyMutable (final AvailObject object)
	{
		return o_Traversed(object).copyMutable();
	}

	@Override
	public @NotNull AvailObject o_DefaultType (final AvailObject object)
	{
		return o_Traversed(object).defaultType();
	}

	@Override
	public @NotNull AvailObject o_DependentChunks (final AvailObject object)
	{
		return o_Traversed(object).dependentChunks();
	}

	@Override
	public int o_ParsingPc (final AvailObject object)
	{
		return o_Traversed(object).parsingPc();
	}

	@Override
	public void o_DisplayTestingTree (final AvailObject object)
	{
		o_Traversed(object).displayTestingTree();
	}

	@Override
	public void o_EnsureMetacovariant (final AvailObject object)
	{
		o_Traversed(object).ensureMetacovariant();
	}

	@Override
	public @NotNull AvailObject o_EnsureMutable (final AvailObject object)
	{
		return o_Traversed(object).ensureMutable();
	}

	@Override
	public boolean o_EqualsBlank (final AvailObject object)
	{
		return o_Traversed(object).equalsBlank();
	}

	@Override
	public boolean o_EqualsFalse (final AvailObject object)
	{
		return o_Traversed(object).equalsFalse();
	}

	@Override
	public boolean o_EqualsTrue (final AvailObject object)
	{
		return o_Traversed(object).equalsTrue();
	}

	@Override
	public boolean o_EqualsVoid (final AvailObject object)
	{
		return o_Traversed(object).equalsVoid();
	}

	@Override
	public boolean o_EqualsVoidOrBlank (final AvailObject object)
	{
		return o_Traversed(object).equalsVoidOrBlank();
	}

	@Override
	public void o_EvictedByGarbageCollector (final AvailObject object)
	{
		o_Traversed(object).evictedByGarbageCollector();
	}

	@Override
	public @NotNull AvailObject o_ExactType (final AvailObject object)
	{
		return o_Traversed(object).exactType();
	}

	@Override
	public ExecutionState o_ExecutionState (final AvailObject object)
	{
		return o_Traversed(object).executionState();
	}

	@Override
	public @NotNull AvailObject o_Expand (final AvailObject object)
	{
		return o_Traversed(object).expand();
	}

	@Override
	public boolean o_ExtractBoolean (final AvailObject object)
	{
		return o_Traversed(object).extractBoolean();
	}

	@Override
	public short o_ExtractByte (final AvailObject object)
	{
		return o_Traversed(object).extractByte();
	}

	@Override
	public double o_ExtractDouble (final AvailObject object)
	{
		return o_Traversed(object).extractDouble();
	}

	@Override
	public float o_ExtractFloat (final AvailObject object)
	{
		return o_Traversed(object).extractFloat();
	}

	@Override
	public int o_ExtractInt (final AvailObject object)
	{
		return o_Traversed(object).extractInt();
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public long o_ExtractLong (final @NotNull AvailObject object)
	{
		return o_Traversed(object).extractLong();
	}

	@Override
	public byte o_ExtractNybble (final AvailObject object)
	{
		return o_Traversed(object).extractNybble();
	}

	@Override
	public @NotNull AvailObject o_FieldMap (final AvailObject object)
	{
		return o_Traversed(object).fieldMap();
	}

	@Override
	public @NotNull AvailObject o_FieldTypeMap (final AvailObject object)
	{
		return o_Traversed(object).fieldTypeMap();
	}

	@Override
	public @NotNull AvailObject o_FilteredBundleTree (final AvailObject object)
	{
		return o_Traversed(object).filteredBundleTree();
	}

	@Override
	public @NotNull AvailObject o_FirstTupleType (final AvailObject object)
	{
		return o_Traversed(object).firstTupleType();
	}

	@Override
	public int o_GetInteger (final AvailObject object)
	{
		return o_Traversed(object).getInteger();
	}

	@Override
	public @NotNull AvailObject o_GetValue (final AvailObject object)
	{
		return o_Traversed(object).getValue();
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return o_Traversed(object).hash();
	}

	@Override
	public int o_HashOfType (final AvailObject object)
	{
		return o_Traversed(object).hashOfType();
	}

	@Override
	public int o_HashOrZero (final AvailObject object)
	{
		return o_Traversed(object).hashOrZero();
	}

	@Override
	public boolean o_HasRestrictions (final AvailObject object)
	{
		return o_Traversed(object).hasRestrictions();
	}

	@Override
	public int o_HiLevelTwoChunkLowOffset (final AvailObject object)
	{
		return o_Traversed(object).hiLevelTwoChunkLowOffset();
	}

	@Override
	public int o_HiNumLocalsLowNumArgs (final AvailObject object)
	{
		return o_Traversed(object).hiNumLocalsLowNumArgs();
	}

	@Override
	public int o_HiPrimitiveLowNumArgsAndLocalsAndStack (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).hiPrimitiveLowNumArgsAndLocalsAndStack();
	}

	@Override
	public int o_HiStartingChunkIndexLowNumOuters (final AvailObject object)
	{
		return o_Traversed(object).hiStartingChunkIndexLowNumOuters();
	}

	@Override
	public @NotNull AvailObject o_ImplementationsTuple (final AvailObject object)
	{
		return o_Traversed(object).implementationsTuple();
	}

	@Override
	public int o_InclusiveFlags (final AvailObject object)
	{
		return o_Traversed(object).inclusiveFlags();
	}

	@Override
	public @NotNull AvailObject o_Incomplete (final AvailObject object)
	{
		return o_Traversed(object).incomplete();
	}

	@Override
	public int o_Index (final AvailObject object)
	{
		return o_Traversed(object).index();
	}

	@Override
	public @NotNull AvailObject o_InnerType (final AvailObject object)
	{
		return o_Traversed(object).innerType();
	}

	@Override
	public @NotNull AvailObject o_Instance (final AvailObject object)
	{
		return o_Traversed(object).instance();
	}

	@Override
	public int o_InternalHash (final AvailObject object)
	{
		return o_Traversed(object).internalHash();
	}

	@Override
	public int o_InterruptRequestFlag (final AvailObject object)
	{
		return o_Traversed(object).interruptRequestFlag();
	}

	@Override
	public int o_InvocationCount (final AvailObject object)
	{
		return o_Traversed(object).invocationCount();
	}

	@Override
	public boolean o_IsAbstract (final AvailObject object)
	{
		return o_Traversed(object).isAbstract();
	}

	@Override
	public boolean o_IsBoolean (final AvailObject object)
	{
		return o_Traversed(object).isBoolean();
	}

	@Override
	public boolean o_IsByte (final AvailObject object)
	{
		return o_Traversed(object).isByte();
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public boolean o_IsByteTuple (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isByteTuple();
	}

	@Override
	public boolean o_IsCharacter (final AvailObject object)
	{
		return o_Traversed(object).isCharacter();
	}

	@Override
	public boolean o_IsClosure (final AvailObject object)
	{
		return o_Traversed(object).isClosure();
	}

	@Override
	public boolean o_IsCyclicType (final AvailObject object)
	{
		return o_Traversed(object).isCyclicType();
	}

	@Override
	public boolean o_IsExtendedInteger (final AvailObject object)
	{
		return o_Traversed(object).isExtendedInteger();
	}

	@Override
	public boolean o_IsFinite (final AvailObject object)
	{
		return o_Traversed(object).isFinite();
	}

	@Override
	public boolean o_IsForward (final AvailObject object)
	{
		return o_Traversed(object).isForward();
	}

	@Override
	public boolean o_IsHashAvailable (final AvailObject object)
	{
		return o_Traversed(object).isHashAvailable();
	}

	@Override
	public boolean o_IsMethod (final AvailObject object)
	{
		return o_Traversed(object).isMethod();
	}

	@Override
	public boolean o_IsIntegerRangeType (final AvailObject object)
	{
		return o_Traversed(object).isIntegerRangeType();
	}

	@Override
	public boolean o_IsMap (final AvailObject object)
	{
		return o_Traversed(object).isMap();
	}

	@Override
	public boolean o_IsMapType (final AvailObject object)
	{
		return o_Traversed(object).isMapType();
	}

	@Override
	public boolean o_IsNybble (final AvailObject object)
	{
		return o_Traversed(object).isNybble();
	}

	@Override
	public boolean o_IsPositive (final AvailObject object)
	{
		return o_Traversed(object).isPositive();
	}

	@Override
	public boolean o_IsSaved (final AvailObject object)
	{
		return o_Traversed(object).isSaved();
	}

	@Override
	public boolean o_IsSet (final AvailObject object)
	{
		return o_Traversed(object).isSet();
	}

	@Override
	public boolean o_IsSetType (final AvailObject object)
	{
		return o_Traversed(object).isSetType();
	}

	@Override
	public boolean o_IsSplice (final AvailObject object)
	{
		return o_Traversed(object).isSplice();
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public boolean o_IsString (final @NotNull AvailObject object)
	{
		return o_Traversed(object).isString();
	}

	@Override
	public boolean o_IsSupertypeOfTerminates (final AvailObject object)
	{
		return o_Traversed(object).isSupertypeOfTerminates();
	}

	@Override
	public boolean o_IsSupertypeOfVoid (final AvailObject object)
	{
		return o_Traversed(object).isSupertypeOfVoid();
	}

	@Override
	public boolean o_IsTuple (final AvailObject object)
	{
		return o_Traversed(object).isTuple();
	}

	@Override
	public boolean o_IsTupleType (final AvailObject object)
	{
		return o_Traversed(object).isTupleType();
	}

	@Override
	public boolean o_IsType (final AvailObject object)
	{
		return o_Traversed(object).isType();
	}

	@Override
	public boolean o_IsValid (final AvailObject object)
	{
		return o_Traversed(object).isValid();
	}

	@Override
	public List<AvailObject> o_KeysAsArray (final AvailObject object)
	{
		return o_Traversed(object).keysAsArray();
	}

	@Override
	public @NotNull AvailObject o_KeysAsSet (final AvailObject object)
	{
		return o_Traversed(object).keysAsSet();
	}

	@Override
	public @NotNull AvailObject o_KeyType (final AvailObject object)
	{
		return o_Traversed(object).keyType();
	}

	@Override
	public int o_LevelTwoChunkIndex (final AvailObject object)
	{
		return o_Traversed(object).levelTwoChunkIndex();
	}

	@Override
	public int o_LevelTwoOffset (final AvailObject object)
	{
		return o_Traversed(object).levelTwoOffset();
	}

	@Override
	public @NotNull AvailObject o_Literal (final AvailObject object)
	{
		return o_Literal(object);
	}

	@Override
	public @NotNull AvailObject o_LowerBound (final AvailObject object)
	{
		return o_Traversed(object).lowerBound();
	}

	@Override
	public boolean o_LowerInclusive (final AvailObject object)
	{
		return o_Traversed(object).lowerInclusive();
	}

	@Override
	public void o_MakeSubobjectsImmutable (final AvailObject object)
	{
		o_Traversed(object).makeSubobjectsImmutable();
	}

	@Override
	public int o_MapSize (final AvailObject object)
	{
		return o_Traversed(object).mapSize();
	}

	@Override
	public short o_MaxStackDepth (final AvailObject object)
	{
		return o_Traversed(object).maxStackDepth();
	}

	@Override
	public @NotNull AvailObject o_Message (final AvailObject object)
	{
		return o_Traversed(object).message();
	}

	@Override
	public @NotNull AvailObject o_MessageParts (final AvailObject object)
	{
		return o_Traversed(object).messageParts();
	}

	@Override
	public @NotNull AvailObject o_Methods (final AvailObject object)
	{
		return o_Traversed(object).methods();
	}

	@Override
	public void o_MoveToHead (final AvailObject object)
	{
		o_Traversed(object).moveToHead();
	}

	@Override
	public @NotNull AvailObject o_MyObjectMeta (final AvailObject object)
	{
		return o_Traversed(object).myObjectMeta();
	}

	@Override
	public @NotNull AvailObject o_MyObjectType (final AvailObject object)
	{
		return o_Traversed(object).myObjectType();
	}

	@Override
	public @NotNull AvailObject o_MyRestrictions (final AvailObject object)
	{
		return o_Traversed(object).myRestrictions();
	}

	@Override
	public @NotNull AvailObject o_MyType (final AvailObject object)
	{
		return o_Traversed(object).myType();
	}

	@Override
	public @NotNull AvailObject o_Name (final AvailObject object)
	{
		return o_Traversed(object).name();
	}

	@Override
	public @NotNull AvailObject o_Names (final AvailObject object)
	{
		return o_Traversed(object).names();
	}

	@Override
	public @NotNull AvailObject o_NewNames (final AvailObject object)
	{
		return o_Traversed(object).newNames();
	}

	@Override
	public @NotNull AvailObject o_Next (final AvailObject object)
	{
		return o_Traversed(object).next();
	}

	@Override
	public int o_NextIndex (final AvailObject object)
	{
		return o_Traversed(object).nextIndex();
	}

	@Override
	public short o_NumArgs (final AvailObject object)
	{
		return o_Traversed(object).numArgs();
	}

	@Override
	public short o_NumArgsAndLocalsAndStack (final AvailObject object)
	{
		return o_Traversed(object).numArgsAndLocalsAndStack();
	}

	@Override
	public int o_NumberOfZones (final AvailObject object)
	{
		return o_Traversed(object).numberOfZones();
	}

	@Override
	public int o_NumBlanks (final AvailObject object)
	{
		return o_Traversed(object).numBlanks();
	}

	@Override
	public int o_NumDoubles (final AvailObject object)
	{
		return o_Traversed(object).numDoubles();
	}

	@Override
	public int o_NumIntegers (final AvailObject object)
	{
		return o_Traversed(object).numIntegers();
	}

	@Override
	public short o_NumLiterals (final AvailObject object)
	{
		return o_Traversed(object).numLiterals();
	}

	@Override
	public short o_NumLocals (final AvailObject object)
	{
		return o_Traversed(object).numLocals();
	}

	@Override
	public int o_NumLocalsOrArgsOrStack (final AvailObject object)
	{
		return o_Traversed(object).numLocalsOrArgsOrStack();
	}

	@Override
	public int o_NumObjects (final AvailObject object)
	{
		return o_Traversed(object).numObjects();
	}

	@Override
	public short o_NumOuters (final AvailObject object)
	{
		return o_Traversed(object).numOuters();
	}

	@Override
	public int o_NumOuterVars (final AvailObject object)
	{
		return o_Traversed(object).numOuterVars();
	}

	@Override
	public @NotNull AvailObject o_Nybbles (final AvailObject object)
	{
		return o_Traversed(object).nybbles();
	}

	@Override
	public @NotNull AvailObject o_Pad1 (final AvailObject object)
	{
		return o_Traversed(object).pad1();
	}

	@Override
	public @NotNull AvailObject o_Pad2 (final AvailObject object)
	{
		return o_Traversed(object).pad2();
	}

	@Override
	public @NotNull AvailObject o_Parent (final AvailObject object)
	{
		return o_Traversed(object).parent();
	}

	@Override
	public int o_Pc (final AvailObject object)
	{
		return o_Traversed(object).pc();
	}

	@Override
	public void o_PostFault (final AvailObject object)
	{
		o_Traversed(object).postFault();
	}

	@Override
	public @NotNull AvailObject o_Previous (final AvailObject object)
	{
		return o_Traversed(object).previous();
	}

	@Override
	public int o_PreviousIndex (final AvailObject object)
	{
		return o_Traversed(object).previousIndex();
	}

	@Override
	public short o_PrimitiveNumber (final AvailObject object)
	{
		return o_Traversed(object).primitiveNumber();
	}

	@Override
	public int o_Priority (final AvailObject object)
	{
		return o_Traversed(object).priority();
	}

	@Override
	public @NotNull AvailObject o_PrivateNames (final AvailObject object)
	{
		return o_Traversed(object).privateNames();
	}

	@Override
	public @NotNull AvailObject o_PrivateTestingTree (final AvailObject object)
	{
		return o_Traversed(object).privateTestingTree();
	}

	@Override
	public @NotNull AvailObject o_ProcessGlobals (final AvailObject object)
	{
		return o_Traversed(object).processGlobals();
	}

	@Override
	public int o_RawQuad1 (final AvailObject object)
	{
		return o_Traversed(object).rawQuad1();
	}

	@Override
	public int o_RawQuad2 (final AvailObject object)
	{
		return o_Traversed(object).rawQuad2();
	}

	@Override
	public void o_ReadBarrierFault (final AvailObject object)
	{
		o_Traversed(object).readBarrierFault();
	}

	@Override
	public void o_ReleaseVariableOrMakeContentsImmutable (
		final @NotNull AvailObject object)
	{
		o_Traversed(object).releaseVariableOrMakeContentsImmutable();
	}

	@Override
	public void o_RemoveFromQueue (final AvailObject object)
	{
		o_Traversed(object).removeFromQueue();
	}

	@Override
	public void o_RemoveRestrictions (final AvailObject object)
	{
		o_Traversed(object).removeRestrictions();
	}

	@Override
	public @NotNull AvailObject o_RequiresBlock (final AvailObject object)
	{
		return o_Traversed(object).requiresBlock();
	}

	@Override
	public @NotNull AvailObject o_Restrictions (final AvailObject object)
	{
		return o_Traversed(object).restrictions();
	}

	@Override
	public @NotNull AvailObject o_ReturnsBlock (final AvailObject object)
	{
		return o_Traversed(object).returnsBlock();
	}

	@Override
	public @NotNull AvailObject o_ReturnType (final AvailObject object)
	{
		return o_Traversed(object).returnType();
	}

	@Override
	public @NotNull AvailObject o_RootBin (final AvailObject object)
	{
		return o_Traversed(object).rootBin();
	}

	@Override
	public @NotNull AvailObject o_SecondTupleType (final AvailObject object)
	{
		return o_Traversed(object).secondTupleType();
	}

	@Override
	public int o_SetSize (final AvailObject object)
	{
		return o_Traversed(object).setSize();
	}

	@Override
	public @NotNull AvailObject o_Signature (final AvailObject object)
	{
		return o_Traversed(object).signature();
	}

	@Override
	public @NotNull AvailObject o_SizeRange (final AvailObject object)
	{
		return o_Traversed(object).sizeRange();
	}

	@Override
	public @NotNull AvailObject o_SpecialActions (final AvailObject object)
	{
		return o_Traversed(object).specialActions();
	}

	@Override
	public int o_Stackp (final AvailObject object)
	{
		return o_Traversed(object).stackp();
	}

	@Override
	public int o_Start (final AvailObject object)
	{
		return o_Traversed(object).start();
	}

	@Override
	public int o_StartingChunkIndex (final AvailObject object)
	{
		return o_Traversed(object).startingChunkIndex();
	}

	@Override
	public void o_Step (final AvailObject object)
	{
		o_Traversed(object).step();
	}

	@Override
	public @NotNull AvailObject o_String (final AvailObject object)
	{
		return o_Traversed(object).string();
	}

	@Override
	public @NotNull AvailObject o_TestingTree (final AvailObject object)
	{
		return o_Traversed(object).testingTree();
	}

	@Override
	public TokenDescriptor.TokenType o_TokenType (final AvailObject object)
	{
		return o_Traversed(object).tokenType();
	}

	@Override
	public void o_TrimExcessLongs (final AvailObject object)
	{
		o_Traversed(object).trimExcessLongs();
	}

	@Override
	public int o_TupleSize (final AvailObject object)
	{
		return o_Traversed(object).tupleSize();
	}

	@Override
	public @NotNull AvailObject o_Type (final AvailObject object)
	{
		return o_Traversed(object).type();
	}

	@Override
	public @NotNull AvailObject o_TypeTuple (final AvailObject object)
	{
		return o_Traversed(object).typeTuple();
	}

	@Override
	public @NotNull AvailObject o_Unclassified (final AvailObject object)
	{
		return o_Traversed(object).unclassified();
	}

	@Override
	public @NotNull AvailObject o_UpperBound (final AvailObject object)
	{
		return o_Traversed(object).upperBound();
	}

	@Override
	public boolean o_UpperInclusive (final AvailObject object)
	{
		return o_Traversed(object).upperInclusive();
	}

	@Override
	public int o_Validity (final AvailObject object)
	{
		return o_Traversed(object).validity();
	}

	@Override
	public @NotNull AvailObject o_Value (final AvailObject object)
	{
		return o_Traversed(object).value();
	}

	@Override
	public @NotNull AvailObject o_ValuesAsTuple (final AvailObject object)
	{
		return o_Traversed(object).valuesAsTuple();
	}

	@Override
	public @NotNull AvailObject o_ValueType (final AvailObject object)
	{
		return o_Traversed(object).valueType();
	}

	@Override
	public @NotNull AvailObject o_VariableBindings (final AvailObject object)
	{
		return o_Traversed(object).variableBindings();
	}

	@Override
	public @NotNull AvailObject o_Vectors (final AvailObject object)
	{
		return o_Traversed(object).vectors();
	}

	@Override
	public void o_Verify (final AvailObject object)
	{
		o_Traversed(object).verify();
	}

	@Override
	public @NotNull AvailObject o_VisibleNames (final AvailObject object)
	{
		return o_Traversed(object).visibleNames();
	}

	@Override
	public int o_WhichOne (final AvailObject object)
	{
		return o_Traversed(object).whichOne();
	}

	@Override
	public @NotNull AvailObject o_Wordcodes (final AvailObject object)
	{
		return o_Traversed(object).wordcodes();
	}

	@Override
	public void o_ParsingInstructions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject instructionsTuple)
	{
		o_Traversed(object).parsingInstructions(instructionsTuple);
	}

	@Override
	public @NotNull AvailObject o_ParsingInstructions (final AvailObject object)
	{
		return o_Traversed(object).parsingInstructions();
	}

	@Override
	public void o_mapDo (
		final @NotNull AvailObject object,
		final @NotNull Continuation2<AvailObject, AvailObject> continuation)
	{
		o_Traversed(object).mapDo(continuation);
	}

	@Override
	public void o_Expression (final AvailObject object, final AvailObject expression)
	{
		o_Traversed(object).expression(expression);
	}

	@Override
	public @NotNull AvailObject o_Expression (final AvailObject object)
	{
		return o_Traversed(object).expression();
	}

	@Override
	public void o_Variable (
		final @NotNull AvailObject object,
		final @NotNull AvailObject variable)
	{
		o_Traversed(object).variable(variable);
	}

	@Override
	public @NotNull AvailObject o_Variable (final AvailObject object)
	{
		return o_Traversed(object).variable();
	}

	@Override
	public void o_ArgumentsTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argumentsTuple)
	{
		o_Traversed(object).argumentsTuple(argumentsTuple);
	}

	@Override
	public @NotNull AvailObject o_ArgumentsTuple (final AvailObject object)
	{
		return o_Traversed(object).argumentsTuple();
	}

	@Override
	public void o_StatementsTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject statementsTuple)
	{
		o_Traversed(object).statementsTuple(statementsTuple);
	}

	@Override
	public @NotNull AvailObject o_StatementsTuple (final AvailObject object)
	{
		return o_Traversed(object).statementsTuple();
	}

	@Override
	public void o_ResultType (final AvailObject object, final AvailObject resultType)
	{
		o_Traversed(object).resultType(resultType);
	}

	@Override
	public @NotNull AvailObject o_ResultType (final AvailObject object)
	{
		return o_Traversed(object).resultType();
	}

	@Override
	public void o_NeededVariables (
		final @NotNull AvailObject object,
		final @NotNull AvailObject neededVariables)
	{
		o_Traversed(object).neededVariables(neededVariables);
	}

	@Override
	public @NotNull AvailObject o_NeededVariables (final AvailObject object)
	{
		return o_Traversed(object).neededVariables();
	}

	@Override
	public void o_Primitive (final AvailObject object, final int primitive)
	{
		o_Traversed(object).primitive(primitive);
	}

	@Override
	public int o_Primitive (final AvailObject object)
	{
		return o_Traversed(object).primitive();
	}

	@Override
	public void o_DeclaredType (final AvailObject object, final AvailObject declaredType)
	{
		o_Traversed(object).declaredType(declaredType);
	}

	@Override
	public @NotNull AvailObject o_DeclaredType (final AvailObject object)
	{
		return o_Traversed(object).declaredType();
	}

	@Override
	public void o_DeclarationKind (
		final @NotNull AvailObject object,
		final @NotNull DeclarationKind declarationKind)
	{
		o_Traversed(object).declarationKind(declarationKind);
	}

	@Override
	public DeclarationKind o_DeclarationKind (final AvailObject object)
	{
		return o_Traversed(object).declarationKind();
	}

	@Override
	public @NotNull AvailObject o_InitializationExpression (final AvailObject object)
	{
		return o_Traversed(object).initializationExpression();
	}

	@Override
	public void o_InitializationExpression (
		final @NotNull AvailObject object,
		final @NotNull AvailObject initializationExpression)
	{
		o_Traversed(object).initializationExpression(initializationExpression);
	}

	@Override
	public @NotNull AvailObject o_LiteralObject (final AvailObject object)
	{
		return o_Traversed(object).literalObject();
	}

	@Override
	public void o_LiteralObject (final AvailObject object, final AvailObject literalObject)
	{
		o_Traversed(object).literalObject(literalObject);
	}

	@Override
	public @NotNull AvailObject o_Token (final AvailObject object)
	{
		return o_Traversed(object).token();
	}

	@Override
	public void o_Token (final AvailObject object, final AvailObject token)
	{
		o_Traversed(object).token(token);
	}

	@Override
	public @NotNull AvailObject o_MarkerValue (final AvailObject object)
	{
		return o_Traversed(object).markerValue();
	}

	@Override
	public void o_MarkerValue (final AvailObject object, final AvailObject markerValue)
	{
		o_Traversed(object).markerValue(markerValue);
	}

	@Override
	public @NotNull AvailObject o_Arguments (final AvailObject object)
	{
		return o_Traversed(object).arguments();
	}

	@Override
	public void o_Arguments (final AvailObject object, final AvailObject arguments)
	{
		o_Traversed(object).arguments(arguments);
	}

	@Override
	public @NotNull AvailObject o_ImplementationSet (final AvailObject object)
	{
		return o_Traversed(object).implementationSet();
	}

	@Override
	public void o_ImplementationSet (
		final @NotNull AvailObject object,
		final @NotNull AvailObject implementationSet)
	{
		o_Traversed(object).implementationSet(implementationSet);
	}

	@Override
	public @NotNull AvailObject o_SuperCastType (final AvailObject object)
	{
		return o_Traversed(object).superCastType();
	}

	@Override
	public void o_SuperCastType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject superCastType)
	{
		o_Traversed(object).superCastType(superCastType);
	}

	@Override
	public @NotNull AvailObject o_ExpressionsTuple (final AvailObject object)
	{
		return o_Traversed(object).expressionsTuple();
	}

	@Override
	public void o_ExpressionsTuple (
		final @NotNull AvailObject object,
		final @NotNull AvailObject expressionsTuple)
	{
		o_Traversed(object).expressionsTuple(expressionsTuple);
	}

	@Override
	public @NotNull AvailObject o_TupleType (final AvailObject object)
	{
		return o_Traversed(object).tupleType();
	}

	@Override
	public void o_TupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType)
	{
		o_Traversed(object).tupleType(tupleType);
	}

	@Override
	public @NotNull AvailObject o_Declaration (final AvailObject object)
	{
		return o_Traversed(object).declaration();
	}

	@Override
	public void o_Declaration (final AvailObject object, final AvailObject declaration)
	{
		o_Traversed(object).declaration(declaration);
	}

	@Override
	public @NotNull AvailObject o_ExpressionType (final AvailObject object)
	{
		return o_Traversed(object).expressionType();
	}

	@Override
	public void o_EmitEffectOn (
		final @NotNull AvailObject object,
		final @NotNull AvailCodeGenerator codeGenerator)
	{
		o_Traversed(object).emitEffectOn(codeGenerator);
	}

	@Override
	public void o_EmitValueOn (
		final @NotNull AvailObject object,
		final @NotNull AvailCodeGenerator codeGenerator)
	{
		o_Traversed(object).emitValueOn(codeGenerator);
	}

	@Override
	public void o_ChildrenMap (
		final @NotNull AvailObject object,
		final @NotNull Transformer1<AvailObject, AvailObject> aBlock)
	{
		o_Traversed(object).childrenMap(aBlock);
	}

	@Override
	public void o_ChildrenDo (
		final @NotNull AvailObject object,
		final @NotNull Continuation1<AvailObject> aBlock)
	{
		o_Traversed(object).childrenDo(aBlock);
	}

	@Override
	public void o_ValidateLocally (
		final @NotNull AvailObject object,
		final @NotNull AvailObject parent,
		final @NotNull List<AvailObject> outerBlocks,
		final @NotNull L2Interpreter anAvailInterpreter)
	{
		o_Traversed(object).validateLocally(
			parent,
			outerBlocks,
			anAvailInterpreter);
	}

	@Override
	public @NotNull AvailObject o_Generate (
		final @NotNull AvailObject object,
		final @NotNull AvailCodeGenerator codeGenerator)
	{
		return o_Traversed(object).generate(codeGenerator);
	}

	@Override
	public @NotNull AvailObject o_CopyWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject newParseNode)
	{
		return o_Traversed(object).copyWith(newParseNode);
	}

	@Override
	public void o_IsLastUse (
		final @NotNull AvailObject object,
		final boolean isLastUse)
	{
		o_Traversed(object).isLastUse(isLastUse);
	}

	@Override
	public boolean o_IsLastUse (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).isLastUse();
	}

	@Override
	public boolean o_IsMacro (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).isMacro();
	}

	@Override
	public void o_Macros (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).macros(value);
	}

	@Override
	public @NotNull AvailObject o_Macros (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).macros();
	}

	@Override
	public @NotNull AvailObject o_CopyMutableParseNode (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).copyMutableParseNode();
	}

	@Override
	public void o_MacroName (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).macroName(value);
	}

	@Override
	public @NotNull AvailObject o_MacroName (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).macroName();
	}

	@Override
	public void o_OutputParseNode (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		o_Traversed(object).outputParseNode(value);
	}

	@Override
	public @NotNull AvailObject o_OutputParseNode (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).outputParseNode();
	}

	@Override
	public @NotNull AvailObject o_ApparentSendName (
		final @NotNull AvailObject object)
	{
		return o_Traversed(object).apparentSendName();
	}

	@Override
	public void o_Statements (
		final AvailObject object,
		final AvailObject statementsTuple)
	{
		o_Traversed(object).statements(statementsTuple);
	}

	@Override
	public AvailObject o_Statements (final AvailObject object)
	{
		return o_Traversed(object).statements();
	}

	@Override
	public void o_FlattenStatementsInto (
		final AvailObject object,
		final List<AvailObject> accumulatedStatements)
	{
		o_Traversed(object).flattenStatementsInto(accumulatedStatements);
	}
}
