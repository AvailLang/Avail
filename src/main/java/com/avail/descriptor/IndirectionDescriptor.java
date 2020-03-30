/*
 * IndirectionDescriptor.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.scanning.LexingState;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.FiberDescriptor.GeneralFlag;
import com.avail.descriptor.FiberDescriptor.InterruptRequestFlag;
import com.avail.descriptor.FiberDescriptor.SynchronizationFlag;
import com.avail.descriptor.FiberDescriptor.TraceFlag;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.descriptor.bundles.A_BundleTree;
import com.avail.descriptor.functions.A_Continuation;
import com.avail.descriptor.functions.A_Function;
import com.avail.descriptor.functions.A_RawFunction;
import com.avail.descriptor.maps.A_Map;
import com.avail.descriptor.maps.A_MapBin;
import com.avail.descriptor.maps.MapDescriptor.MapIterable;
import com.avail.descriptor.methods.A_Definition;
import com.avail.descriptor.methods.A_GrammaticalRestriction;
import com.avail.descriptor.methods.A_Method;
import com.avail.descriptor.methods.A_SemanticRestriction;
import com.avail.descriptor.numbers.A_Number;
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order;
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.parsing.A_DefinitionParsingPlan;
import com.avail.descriptor.parsing.A_Lexer;
import com.avail.descriptor.parsing.A_ParsingPlanInProgress;
import com.avail.descriptor.phrases.A_Phrase;
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind;
import com.avail.descriptor.representation.AbstractSlotsEnum;
import com.avail.descriptor.representation.AvailObjectRepresentation;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.sets.A_Set;
import com.avail.descriptor.sets.SetDescriptor.SetIterator;
import com.avail.descriptor.tokens.A_Token;
import com.avail.descriptor.tokens.TokenDescriptor.TokenType;
import com.avail.descriptor.tuples.A_String;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.tuples.StringDescriptor;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind;
import com.avail.descriptor.types.TypeDescriptor.Types;
import com.avail.descriptor.types.TypeTag;
import com.avail.descriptor.variables.A_Variable;
import com.avail.descriptor.variables.VariableDescriptor.VariableAccessReactor;
import com.avail.dispatch.LookupTree;
import com.avail.exceptions.AvailException;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.exceptions.SignatureException;
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.AvailLoader.LexicalScanner;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.io.TextInterface;
import com.avail.performance.Statistic;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.IteratorNotNull;
import com.avail.utility.Pair;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;
import com.avail.utility.visitor.AvailSubobjectVisitor;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Spliterator;
import java.util.TimerTask;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.avail.descriptor.IndirectionDescriptor.ObjectSlots.INDIRECTION_TARGET;

/**
 * An {@link AvailObject} with an {@code IndirectionDescriptor} keeps track of
 * its target, that which it is pretending to be.  Almost all messages are
 * routed to the target, making it an ideal proxy.
 * <p>
 * When some kinds of objects are compared to each other, say {@linkplain
 * StringDescriptor strings}, a check is first made to see if the objects
 * are at the same location in memory -- the same AvailObject in the current
 * version that uses {@link AvailObjectRepresentation}.  If so, it immediately
 * returns true.  If not, a more detailed, potentially expensive comparison
 * takes place.  If the objects are found to be equal, one of them is mutated
 * into an indirection (by replacing its descriptor with an {@code
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
 * @author Todd L Smith &lt;todd@availlang.org&gt;
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
	enum ObjectSlots implements ObjectSlotsEnumJava
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
	enum IntegerSlots implements IntegerSlotsEnumJava
	{
		/**
		 * Ignore all integer slots.
		 */
		@HideFieldInDebugger
		IGNORED_INTEGER_SLOT_;
	}

	@Override
	protected boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == INDIRECTION_TARGET;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		object.traversed().printOnAvoidingIndent(
			aStream,
			recursionMap,
			indent);
	}

	@Override
	protected void o_ScanSubobjects (
		final AvailObject object,
		final AvailSubobjectVisitor visitor)
	{
		visitor.invoke(object.slot(INDIRECTION_TARGET));
	}

	@Override
	protected AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			object.setDescriptor(immutable(typeTag));
			return object.slot(INDIRECTION_TARGET).makeImmutable();
		}
		return object.slot(INDIRECTION_TARGET);
	}

	@Override
	AvailObject o_MakeShared (final AvailObject object)
	{
		if (!isShared())
		{
			object.setDescriptor(shared(typeTag));
			return object.slot(INDIRECTION_TARGET).makeShared();
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
		if (!finalObject.sameAddressAs(next))
		{
			object.setSlot(INDIRECTION_TARGET, finalObject);
		}
		return finalObject;
	}

	/**
	 * Construct a new {@code IndirectionDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private IndirectionDescriptor (
		final Mutability mutability,
		final TypeTag typeTag)
	{
		super(mutability, typeTag, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link IndirectionDescriptor}. */
	static final IndirectionDescriptor[] mutables =
		new IndirectionDescriptor[TypeTag.values().length];

	/** The immutable {@link IndirectionDescriptor}. */
	static final IndirectionDescriptor[] immutables =
		new IndirectionDescriptor[TypeTag.values().length];

	/** The shared {@link IndirectionDescriptor}. */
	static final IndirectionDescriptor[] shareds =
		new IndirectionDescriptor[TypeTag.values().length];

	static
	{
		for (final TypeTag typeTag : TypeTag.values())
		{
			mutables[typeTag.ordinal()] =
				new IndirectionDescriptor(Mutability.MUTABLE, typeTag);
			immutables[typeTag.ordinal()] =
				new IndirectionDescriptor(Mutability.IMMUTABLE, typeTag);
			shareds[typeTag.ordinal()] =
				new IndirectionDescriptor(Mutability.SHARED, typeTag);
		}
	}

	public static IndirectionDescriptor mutable (final TypeTag typeTag)
	{
		return mutables[typeTag.ordinal()];
	}

	public static IndirectionDescriptor immutable (final TypeTag typeTag)
	{
		return immutables[typeTag.ordinal()];
	}

	static IndirectionDescriptor shared (final TypeTag typeTag)
	{
		return shareds[typeTag.ordinal()];
	}

	@Override @Deprecated
	public IndirectionDescriptor mutable ()
	{
		return mutables[typeTag.ordinal()];
	}

	@Override @Deprecated
	public IndirectionDescriptor immutable ()
	{
		return immutables[typeTag.ordinal()];
	}

	@Override @Deprecated
	public IndirectionDescriptor shared ()
	{
		return shareds[typeTag.ordinal()];
	}

	@Override
	protected boolean o_AcceptsArgTypesFromFunctionType (
		final AvailObject object,
		final A_Type functionType)
	{
		return o_Traversed(object).acceptsArgTypesFromFunctionType(
			functionType);
	}

	@Override
	protected boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		return o_Traversed(object).acceptsListOfArgTypes(argTypes);
	}

	@Override
	protected boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<? extends A_BasicObject> argValues)
	{
		return o_Traversed(object).acceptsListOfArgValues(argValues);
	}

	@Override
	protected boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final A_Tuple argTypes)
	{
		return o_Traversed(object).acceptsTupleOfArgTypes(argTypes);
	}

	@Override
	protected boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final A_Tuple arguments)
	{
		return o_Traversed(object).acceptsTupleOfArguments(arguments);
	}

	@Override
	protected void o_AddDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		o_Traversed(object).addDependentChunk(chunk);
	}

	@Override
	protected void o_MethodAddDefinition (
		final AvailObject object,
		final A_Definition definition)
	throws SignatureException
	{
		o_Traversed(object).methodAddDefinition(definition);
	}

	@Override
	protected void o_AddGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		o_Traversed(object).addGrammaticalRestriction(grammaticalRestriction);
	}

	@Override
	protected A_Number o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	protected A_Number o_AddToIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object)
				.addToIntegerCanDestroy(anInteger, canDestroy);
	}

	@Override
	protected void o_ModuleAddGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		o_Traversed(object).moduleAddGrammaticalRestriction(
			grammaticalRestriction);
	}

	@Override
	protected void o_ModuleAddDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		o_Traversed(object).moduleAddDefinition(definition);
	}

	@Override
	protected void o_AddDefinitionParsingPlan (
		final AvailObject object,
		final A_DefinitionParsingPlan plan)
	{
		o_Traversed(object).addDefinitionParsingPlan(plan);
	}

	@Override
	protected void o_AddImportedName (
		final AvailObject object,
		final A_Atom trueName)
	{
		o_Traversed(object).addImportedName(trueName);
	}

	@Override
	protected void o_AddImportedNames (
		final AvailObject object,
		final A_Set trueNames)
	{
		o_Traversed(object).addImportedNames(trueNames);
	}

	@Override
	protected void o_IntroduceNewName (
		final AvailObject object,
		final A_Atom trueName)
	{
		o_Traversed(object).introduceNewName(trueName);
	}

	@Override
	protected void o_AddPrivateName (
		final AvailObject object,
		final A_Atom trueName)
	{
		o_Traversed(object).addPrivateName(trueName);
	}

	@Override
	protected void o_AddPrivateNames (
		final AvailObject object,
		final A_Set trueNames)
	{
		o_Traversed(object).addPrivateNames(trueNames);
	}

	@Override
	protected A_BasicObject o_SetBinAddingElementHashLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObject,
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
	protected boolean o_BinHasElementWithHash (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash)
	{
		return o_Traversed(object).binHasElementWithHash(
			elementObject,
			elementObjectHash);
	}

	@Override
	AvailObject o_BinRemoveElementHashLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		return o_Traversed(object).binRemoveElementHashLevelCanDestroy(
			elementObject,
			elementObjectHash,
			myLevel,
			canDestroy);
	}

	@Override
	protected void o_BreakpointBlock (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).breakpointBlock(value);
	}

	@Override
	protected A_BundleTree o_BuildFilteredBundleTree (
		final AvailObject object)
	{
		return o_Traversed(object).buildFilteredBundleTree();
	}

	@Override
	protected boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithStartingAt(
			startIndex1,
			endIndex1,
			anotherObject,
			startIndex2);
	}

	@Override
	protected boolean o_CompareFromToWithAnyTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithAnyTupleStartingAt(
			startIndex1,
			endIndex1,
			aTuple,
			startIndex2);
	}

	@Override
	protected boolean o_CompareFromToWithByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_String aByteString,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithByteStringStartingAt(
			startIndex1,
			endIndex1,
			aByteString,
			startIndex2);
	}

	@Override
	protected boolean o_CompareFromToWithByteTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithByteTupleStartingAt(
			startIndex1,
			endIndex1,
			aByteTuple,
			startIndex2);
	}

	@Override
	protected boolean o_CompareFromToWithIntegerIntervalTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anIntegerIntervalTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithIntegerIntervalTupleStartingAt(
			startIndex1,
			endIndex1,
			anIntegerIntervalTuple,
			startIndex2);
	}

	@Override
	protected boolean o_CompareFromToWithSmallIntegerIntervalTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aSmallIntegerIntervalTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithSmallIntegerIntervalTupleStartingAt(
			startIndex1,
			endIndex1,
			aSmallIntegerIntervalTuple,
			startIndex2);
	}

	@Override
	protected boolean o_CompareFromToWithRepeatedElementTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aRepeatedElementTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithRepeatedElementTupleStartingAt(
			startIndex1,
			endIndex1,
			aRepeatedElementTuple,
			startIndex2);
	}

	@Override
	protected boolean o_CompareFromToWithNybbleTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aNybbleTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithNybbleTupleStartingAt(
			startIndex1,
			endIndex1,
			aNybbleTuple,
			startIndex2);
	}

	@Override
	protected boolean o_CompareFromToWithObjectTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anObjectTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithObjectTupleStartingAt(
			startIndex1,
			endIndex1,
			anObjectTuple,
			startIndex2);
	}

	@Override
	protected boolean o_CompareFromToWithTwoByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_String aTwoByteString,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithTwoByteStringStartingAt(
			startIndex1,
			endIndex1,
			aTwoByteString,
			startIndex2);
	}

	@Override
	protected int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		return o_Traversed(object).computeHashFromTo(start, end);
	}

	@Override
	protected A_Tuple o_ConcatenateTuplesCanDestroy (
		final AvailObject object,
		final boolean canDestroy)
	{
		return o_Traversed(object).concatenateTuplesCanDestroy(canDestroy);
	}

	@Override
	protected void o_Continuation (
		final AvailObject object,
		final A_Continuation value)
	{
		o_Traversed(object).continuation(value);
	}

	@Override
	protected A_Tuple o_CopyTupleFromToCanDestroy (
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
	protected boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<TypeRestriction> argRestrictions)
	{
		return o_Traversed(object).couldEverBeInvokedWith(argRestrictions);
	}

	@Override
	protected A_Number o_DivideCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideCanDestroy(aNumber, canDestroy);
	}

	@Override
	protected A_Number o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	protected A_Number o_DivideIntoIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
	{
		return o_Traversed(object).equals(another);
	}

	@Override
	protected boolean o_EqualsAnyTuple (
		final AvailObject object,
		final A_Tuple anotherTuple)
	{
		return o_Traversed(object).equalsAnyTuple(anotherTuple);
	}

	@Override
	protected boolean o_EqualsByteString (
		final AvailObject object,
		final A_String aByteString)
	{
		return o_Traversed(object).equalsByteString(aByteString);
	}

	@Override
	protected boolean o_EqualsByteTuple (
		final AvailObject object,
		final A_Tuple aByteTuple)
	{
		return o_Traversed(object).equalsByteTuple(aByteTuple);
	}

	@Override
	protected boolean o_EqualsCharacterWithCodePoint (
		final AvailObject object,
		final int otherCodePoint)
	{
		return o_Traversed(object).equalsCharacterWithCodePoint(otherCodePoint);
	}

	@Override
	protected boolean o_EqualsFiberType (final AvailObject object, final A_Type aType)
	{
		return o_Traversed(object).equalsFiberType(aType);
	}

	@Override
	protected boolean o_EqualsFunction (
		final AvailObject object,
		final A_Function aFunction)
	{
		return o_Traversed(object).equalsFunction(aFunction);
	}

	@Override
	protected boolean o_EqualsFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return o_Traversed(object).equalsFunctionType(aFunctionType);
	}

	@Override
	protected boolean o_EqualsIntegerIntervalTuple (
		final AvailObject object,
		final A_Tuple anIntegerIntervalTuple)
	{
		return o_Traversed(object).equalsIntegerIntervalTuple(
			anIntegerIntervalTuple);
	}

	@Override
	protected boolean o_EqualsSmallIntegerIntervalTuple (
		final AvailObject object,
		final A_Tuple aSmallIntegerIntervalTuple)
	{
		return o_Traversed(object).equalsSmallIntegerIntervalTuple(
			aSmallIntegerIntervalTuple);
	}

	@Override
	protected boolean o_EqualsRepeatedElementTuple (
		final AvailObject object,
		final A_Tuple aRepeatedElementTuple)
	{
		return o_Traversed(object).equalsRepeatedElementTuple(
			aRepeatedElementTuple);
	}

	@Override
	protected boolean o_EqualsCompiledCode (
		final AvailObject object,
		final A_RawFunction aCompiledCode)
	{
		return o_Traversed(object).equalsCompiledCode(aCompiledCode);
	}

	@Override
	protected boolean o_EqualsVariable (
		final AvailObject object,
		final AvailObject aVariable)
	{
		return o_Traversed(object).equalsVariable(aVariable);
	}

	@Override
	protected boolean o_EqualsVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		return o_Traversed(object).equalsVariableType(aVariableType);
	}

	@Override
	protected boolean o_EqualsContinuation (
		final AvailObject object,
		final A_Continuation aContinuation)
	{
		return o_Traversed(object).equalsContinuation(aContinuation);
	}

	@Override
	protected boolean o_EqualsContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return o_Traversed(object).equalsContinuationType(aContinuationType);
	}

	@Override
	protected boolean o_EqualsCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return o_Traversed(object).equalsCompiledCodeType(aCompiledCodeType);
	}

	@Override
	protected boolean o_EqualsDouble (
		final AvailObject object,
		final double aDouble)
	{
		return o_Traversed(object).equalsDouble(aDouble);
	}

	@Override
	protected boolean o_EqualsFloat (
		final AvailObject object,
		final float aFloat)
	{
		return o_Traversed(object).equalsFloat(aFloat);
	}

	@Override
	protected boolean o_EqualsInfinity (
		final AvailObject object,
		final Sign sign)
	{
		return o_Traversed(object).equalsInfinity(sign);
	}

	@Override
	protected boolean o_EqualsInteger (
		final AvailObject object,
		final AvailObject anAvailInteger)
	{
		return o_Traversed(object).equalsInteger(anAvailInteger);
	}

	@Override
	protected boolean o_EqualsIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return o_Traversed(object).equalsIntegerRangeType(anIntegerRangeType);
	}

	@Override
	protected boolean o_EqualsMap (
		final AvailObject object,
		final A_Map aMap)
	{
		return o_Traversed(object).equalsMap(aMap);
	}

	@Override
	protected boolean o_EqualsMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		return o_Traversed(object).equalsMapType(aMapType);
	}

	@Override
	protected boolean o_EqualsNybbleTuple (
		final AvailObject object,
		final A_Tuple aNybbleTuple)
	{
		return o_Traversed(object).equalsNybbleTuple(aNybbleTuple);
	}

	@Override
	protected boolean o_EqualsObject (
		final AvailObject object,
		final AvailObject anObject)
	{
		return o_Traversed(object).equalsObject(anObject);
	}

	@Override
	protected boolean o_EqualsObjectTuple (
		final AvailObject object,
		final A_Tuple anObjectTuple)
	{
		return o_Traversed(object).equalsObjectTuple(anObjectTuple);
	}

	@Override
	protected boolean o_EqualsPojo (
		final AvailObject object,
		final AvailObject aRawPojo)
	{
		return o_Traversed(object).equalsPojo(aRawPojo);
	}

	@Override
	protected boolean o_EqualsPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		return o_Traversed(object).equalsPojoType(aPojoType);
	}

	@Override
	protected boolean o_EqualsPrimitiveType (
		final AvailObject object,
		final A_Type aPrimitiveType)
	{
		return o_Traversed(object).equalsPrimitiveType(aPrimitiveType);
	}

	@Override
	protected boolean o_EqualsRawPojoFor (
		final AvailObject object,
		final AvailObject otherRawPojo,
		final @Nullable Object otherJavaObject)
	{
		return o_Traversed(object).equalsRawPojoFor(
			otherRawPojo,
			otherJavaObject);
	}

	@Override
	protected boolean o_EqualsReverseTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		return o_Traversed(object).equalsReverseTuple(aTuple);
	}

	@Override
	protected boolean o_EqualsSet (
		final AvailObject object,
		final A_Set aSet)
	{
		return o_Traversed(object).equalsSet(aSet);
	}

	@Override
	protected boolean o_EqualsSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		return o_Traversed(object).equalsSetType(aSetType);
	}

	@Override
	protected boolean o_EqualsTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return o_Traversed(object).equalsTupleType(aTupleType);
	}

	@Override
	protected boolean o_EqualsTwoByteString (
		final AvailObject object,
		final A_String aTwoByteString)
	{
		return o_Traversed(object).equalsTwoByteString(aTwoByteString);
	}

	@Override
	protected void o_ExecutionState (
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
	List<A_Definition> o_FilterByTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		return o_Traversed(object).filterByTypes(argTypes);
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
	protected boolean o_HasElement (
		final AvailObject object,
		final A_BasicObject elementObject)
	{
		return o_Traversed(object).hasElement(elementObject);
	}

	@Override
	protected int o_HashFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		return o_Traversed(object).hashFromTo(startIndex, endIndex);
	}

	@Override
	protected void o_HashOrZero (final AvailObject object, final int value)
	{
		o_Traversed(object).hashOrZero(value);
	}

	@Override
	protected boolean o_HasKey (
		final AvailObject object,
		final A_BasicObject keyObject)
	{
		return o_Traversed(object).hasKey(keyObject);
	}

	@Override
	protected boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return o_Traversed(object).hasObjectInstance(potentialInstance);
	}

	@Override
	List<A_Definition> o_DefinitionsAtOrBelow (
		final AvailObject object,
		final List<TypeRestriction> argRestrictions)
	{
		return o_Traversed(object).definitionsAtOrBelow(argRestrictions);
	}

	@Override
	protected boolean o_IncludesDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		return o_Traversed(object).includesDefinition(definition);
	}

	@Override
	protected void o_SetInterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		o_Traversed(object).setInterruptRequestFlag(flag);
	}

	@Override
	protected void o_CountdownToReoptimize (final AvailObject object, final int value)
	{
		o_Traversed(object).countdownToReoptimize(value);
	}

	@Override
	protected boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final A_BasicObject anotherObject)
	{
		return o_Traversed(object).isBetterRepresentationThan(anotherObject);
	}

	@Override
	protected int o_RepresentationCostOfTupleType (
		final AvailObject object)
	{
		return o_Traversed(object).representationCostOfTupleType();
	}

	@Override
	protected boolean o_IsBinSubsetOf (
		final AvailObject object,
		final A_Set potentialSuperset)
	{
		return o_Traversed(object).isBinSubsetOf(potentialSuperset);
	}

	@Override
	public boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		return o_Traversed(object).isInstanceOfKind(aType);
	}

	@Override
	protected boolean o_IsSubsetOf (
		final AvailObject object,
		final A_Set another)
	{
		return o_Traversed(object).isSubsetOf(another);
	}

	@Override
	protected boolean o_IsSubtypeOf (
		final AvailObject object,
		final A_Type aType)
	{
		return o_Traversed(object).isSubtypeOf(aType);
	}

	@Override
	protected boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		return o_Traversed(object).isSupertypeOfVariableType(aVariableType);
	}

	@Override
	protected boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return o_Traversed(object).isSupertypeOfContinuationType(
			aContinuationType);
	}

	@Override
	protected boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return o_Traversed(object).isSupertypeOfCompiledCodeType(
			aCompiledCodeType);
	}

	@Override
	protected boolean o_IsSupertypeOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return o_Traversed(object).isSupertypeOfFiberType(aFiberType);
	}

	@Override
	protected boolean o_IsSupertypeOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return o_Traversed(object).isSupertypeOfFunctionType(aFunctionType);
	}

	@Override
	protected boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return o_Traversed(object).isSupertypeOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	protected boolean o_IsSupertypeOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return o_Traversed(object).isSupertypeOfListNodeType(aListNodeType);
	}

	@Override
	protected boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		return o_Traversed(object).isSupertypeOfMapType(aMapType);
	}

	@Override
	protected boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return o_Traversed(object).isSupertypeOfObjectType(anObjectType);
	}

	@Override
	protected boolean o_IsSupertypeOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		return o_Traversed(object).isSupertypeOfPhraseType(aPhraseType);
	}

	@Override
	protected boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return o_Traversed(object).isSupertypeOfPojoType(aPojoType);
	}

	@Override
	protected boolean o_IsSupertypeOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return o_Traversed(object).isSupertypeOfPrimitiveTypeEnum(
			primitiveTypeEnum);
	}

	@Override
	protected boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		return o_Traversed(object).isSupertypeOfSetType(aSetType);
	}

	@Override
	protected boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		return o_Traversed(object).isSupertypeOfTupleType(aTupleType);
	}

	@Override
	protected boolean o_IsSupertypeOfEnumerationType (
		final AvailObject object,
		final A_BasicObject anEnumerationType)
	{
		return o_Traversed(object).isSupertypeOfEnumerationType(
			anEnumerationType);
	}

	@Override
	protected IteratorNotNull<AvailObject> o_Iterator (final AvailObject object)
	{
		return o_Traversed(object).iterator();
	}

	@Override
	Spliterator<AvailObject> o_Spliterator (final AvailObject object)
	{
		return o_Traversed(object).spliterator();
	}

	@Override
	Stream<AvailObject> o_Stream (final AvailObject object)
	{
		return o_Traversed(object).stream();
	}

	@Override
	Stream<AvailObject> o_ParallelStream (final AvailObject object)
	{
		return o_Traversed(object).parallelStream();
	}

	@Override
	protected void o_LevelTwoChunkOffset (
		final AvailObject object,
		final L2Chunk chunk,
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
	protected void o_ArgOrLocalOrStackAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		o_Traversed(object).argOrLocalOrStackAtPut(index, value);
	}

	@Override
	protected A_Type o_LocalTypeAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).localTypeAt(index);
	}

	@Override
	protected A_Definition o_LookupByTypesFromTuple (
			final AvailObject object,
			final A_Tuple argumentTypeTuple)
		throws MethodDefinitionException
	{
		return o_Traversed(object).lookupByTypesFromTuple(argumentTypeTuple);
	}

	@Override
	protected A_Definition o_LookupByValuesFromList (
		final AvailObject object,
		final List<? extends A_BasicObject> argumentList)
	throws MethodDefinitionException
	{
		return o_Traversed(object).lookupByValuesFromList(argumentList);
	}

	@Override
	AvailObject o_MapAt (
		final AvailObject object,
		final A_BasicObject keyObject)
	{
		return o_Traversed(object).mapAt(keyObject);
	}

	@Override
	protected A_Map o_MapAtPuttingCanDestroy (
		final AvailObject object,
		final A_BasicObject keyObject,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapAtPuttingCanDestroy(
			keyObject,
			newValueObject,
			canDestroy);
	}

	@Override
	protected A_Map o_MapWithoutKeyCanDestroy (
		final AvailObject object,
		final A_BasicObject keyObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapWithoutKeyCanDestroy(
			keyObject,
			canDestroy);
	}

	@Override
	protected A_Number o_MinusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).minusCanDestroy(aNumber, canDestroy);
	}

	@Override
	protected A_Number o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	protected A_Number o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	protected boolean o_NameVisible (
		final AvailObject object,
		final A_Atom trueName)
	{
		return o_Traversed(object).nameVisible(trueName);
	}

	@Override
	protected boolean o_OptionallyNilOuterVar (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).optionallyNilOuterVar(index);
	}

	@Override
	protected A_Type o_OuterTypeAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).outerTypeAt(index);
	}

	@Override
	AvailObject o_OuterVarAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).outerVarAt(index);
	}

	@Override
	protected void o_OuterVarAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		o_Traversed(object).outerVarAtPut(index, value);
	}

	@Override
	protected A_Number o_PlusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).plusCanDestroy(aNumber, canDestroy);
	}

	@Override
	protected void o_Priority (
		final AvailObject object,
		final int value)
	{
		o_Traversed(object).priority(value);
	}

	@Override
	protected void o_FiberGlobals (
		final AvailObject object,
		final A_Map value)
	{
		o_Traversed(object).fiberGlobals(value);
	}

	@Override
	short o_RawByteForCharacterAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawByteForCharacterAt(index);
	}

	@Override
	protected int o_RawShortForCharacterAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawShortForCharacterAt(index);
	}

	@Override
	protected void o_RawShortForCharacterAtPut (
		final AvailObject object,
		final int index,
		final int anInteger)
	{
		o_Traversed(object).rawShortForCharacterAtPut(index, anInteger);
	}

	@Override
	protected int o_RawSignedIntegerAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).rawSignedIntegerAt(index);
	}

	@Override
	protected void o_RawSignedIntegerAtPut (
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
	protected void o_RawUnsignedIntegerAtPut (
		final AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).rawUnsignedIntegerAtPut(index, value);
	}

	@Override
	protected void o_RemoveDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		o_Traversed(object).removeDependentChunk(chunk);
	}

	@Override
	protected void o_RemoveFrom (
		final AvailObject object,
		final AvailLoader loader,
		final Continuation0 afterRemoval)
	{
		o_Traversed(object).removeFrom(loader, afterRemoval);
	}

	@Override
	protected void o_RemoveDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		o_Traversed(object).removeDefinition(definition);
	}

	@Override
	protected void o_RemoveGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction obsoleteRestriction)
	{
		o_Traversed(object).removeGrammaticalRestriction(obsoleteRestriction);
	}

	@Override
	protected void o_ResolveForward (
		final AvailObject object,
		final A_BasicObject forwardDefinition)
	{
		o_Traversed(object).resolveForward(
			forwardDefinition);
	}

	@Override
	protected A_Set o_SetIntersectionCanDestroy (
		final AvailObject object,
		final A_Set otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setIntersectionCanDestroy(
			otherSet,
			canDestroy);
	}

	@Override
	protected A_Set o_SetMinusCanDestroy (
		final AvailObject object,
		final A_Set otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setMinusCanDestroy(otherSet, canDestroy);
	}

	@Override
	protected A_Set o_SetUnionCanDestroy (
		final AvailObject object,
		final A_Set otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setUnionCanDestroy(otherSet, canDestroy);
	}

	@Override
	protected void o_SetValue (
			final AvailObject object,
			final A_BasicObject newValue)
		throws VariableSetException
	{
		o_Traversed(object).setValue(newValue);
	}

	@Override
	protected void o_SetValueNoCheck (
		final AvailObject object,
		final A_BasicObject newValue)
	{
		o_Traversed(object).setValueNoCheck(newValue);
	}

	@Override
	protected A_Set o_SetWithElementCanDestroy (
		final AvailObject object,
		final A_BasicObject newElementObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).setWithElementCanDestroy(
			newElementObject,
			canDestroy);
	}

	@Override
	protected A_Set o_SetWithoutElementCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObjectToExclude,
		final boolean canDestroy)
	{
		return o_Traversed(object).setWithoutElementCanDestroy(
			elementObjectToExclude,
			canDestroy);
	}

	@Override
	AvailObject o_StackAt (final AvailObject object, final int slotIndex)
	{
		return o_Traversed(object).stackAt(slotIndex);
	}

	@Override
	protected void o_SetStartingChunkAndReoptimizationCountdown (
		final AvailObject object,
		final L2Chunk chunk,
		final long countdown)
	{
		o_Traversed(object).setStartingChunkAndReoptimizationCountdown(
			chunk, countdown);
	}

	@Override
	protected A_Number o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	protected A_Number o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	protected A_Number o_TimesCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).timesCanDestroy(aNumber, canDestroy);
	}

	@Override
	protected A_Set o_TrueNamesForStringName (
		final AvailObject object,
		final A_String stringName)
	{
		return o_Traversed(object).trueNamesForStringName(stringName);
	}

	@Override
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).tupleAt(index);
	}

	@Override
	protected A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).tupleAtPuttingCanDestroy(
			index,
			newValueObject,
			canDestroy);
	}

	@Override
	protected int o_TupleIntAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).tupleIntAt(index);
	}

	@Override
	protected A_Type o_TypeAtIndex (final AvailObject object, final int index)
	{
		return o_Traversed(object).typeAtIndex(index);
	}

	@Override
	protected A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		return o_Traversed(object).typeIntersection(another);
	}

	@Override
	protected A_Type o_TypeIntersectionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return o_Traversed(object).typeIntersectionOfCompiledCodeType(
			aCompiledCodeType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return o_Traversed(object).typeIntersectionOfContinuationType(
			aContinuationType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return o_Traversed(object).typeIntersectionOfFiberType(
			aFiberType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return o_Traversed(object).typeIntersectionOfFunctionType(
			aFunctionType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return o_Traversed(object).typeIntersectionOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return o_Traversed(object).typeIntersectionOfListNodeType(
			aListNodeType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		return o_Traversed(object).typeIntersectionOfMapType(aMapType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return o_Traversed(object).typeIntersectionOfObjectType(anObjectType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		return o_Traversed(object).typeIntersectionOfPhraseType(
			aPhraseType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return o_Traversed(object).typeIntersectionOfPojoType(aPojoType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		return o_Traversed(object).typeIntersectionOfSetType(aSetType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return o_Traversed(object).typeIntersectionOfTupleType(aTupleType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		return o_Traversed(object).typeIntersectionOfVariableType(
			aVariableType);
	}

	@Override
	protected A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		return o_Traversed(object).typeUnion(another);
	}

	@Override
	protected A_Type o_TypeUnionOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return o_Traversed(object).typeUnionOfFiberType(aFiberType);
	}

	@Override
	protected A_Type o_TypeUnionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return o_Traversed(object).typeUnionOfFunctionType(aFunctionType);
	}

	@Override
	protected A_Type o_TypeUnionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		return o_Traversed(object).typeUnionOfVariableType(aVariableType);
	}

	@Override
	protected A_Type o_TypeUnionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return o_Traversed(object).typeUnionOfContinuationType(
			aContinuationType);
	}

	@Override
	protected A_Type o_TypeUnionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return o_Traversed(object).typeUnionOfCompiledCodeType(
			aCompiledCodeType);
	}

	@Override
	protected A_Type o_TypeUnionOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return o_Traversed(object).typeUnionOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	protected A_Type o_TypeUnionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		return o_Traversed(object).typeUnionOfMapType(aMapType);
	}

	@Override
	protected A_Type o_TypeUnionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return o_Traversed(object).typeUnionOfObjectType(anObjectType);
	}

	@Override
	protected A_Type o_TypeUnionOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		return o_Traversed(object).typeUnionOfPhraseType(
			aPhraseType);
	}

	@Override
	protected A_Type o_TypeUnionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return o_Traversed(object).typeUnionOfPojoType(aPojoType);
	}

	@Override
	protected A_Type o_TypeUnionOfSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		return o_Traversed(object).typeUnionOfSetType(aSetType);
	}

	@Override
	protected A_Type o_TypeUnionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return o_Traversed(object).typeUnionOfTupleType(aTupleType);
	}

	@Override
	protected A_Type o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		return o_Traversed(object).unionOfTypesAtThrough(startIndex, endIndex);
	}

	@Override
	protected void o_Value (
		final AvailObject object,
		final A_BasicObject value)
	{
		o_Traversed(object).value(value);
	}

	@Override
	protected String o_AsNativeString (final AvailObject object)
	{
		return o_Traversed(object).asNativeString();
	}

	@Override
	protected A_Set o_AsSet (final AvailObject object)
	{
		return o_Traversed(object).asSet();
	}

	@Override
	protected A_Tuple o_AsTuple (final AvailObject object)
	{
		return o_Traversed(object).asTuple();
	}

	@Override
	protected int o_BitsPerEntry (final AvailObject object)
	{
		return o_Traversed(object).bitsPerEntry();
	}

	@Override
	protected A_Function o_BodyBlock (final AvailObject object)
	{
		return o_Traversed(object).bodyBlock();
	}

	@Override
	protected A_Type o_BodySignature (final AvailObject object)
	{
		return o_Traversed(object).bodySignature();
	}

	@Override
	protected A_BasicObject o_BreakpointBlock (final AvailObject object)
	{
		return o_Traversed(object).breakpointBlock();
	}

	@Override
	protected A_Continuation o_Caller (final AvailObject object)
	{
		return o_Traversed(object).caller();
	}

	@Override
	protected void o_ClearValue (final AvailObject object)
	{
		o_Traversed(object).clearValue();
	}

	@Override
	protected A_Function o_Function (final AvailObject object)
	{
		return o_Traversed(object).function();
	}

	@Override
	protected A_Type o_FunctionType (final AvailObject object)
	{
		return o_Traversed(object).functionType();
	}

	@Override
	protected A_RawFunction o_Code (final AvailObject object)
	{
		return o_Traversed(object).code();
	}

	@Override
	protected int o_CodePoint (final AvailObject object)
	{
		return o_Traversed(object).codePoint();
	}

	@Override
	protected A_Set o_LazyComplete (final AvailObject object)
	{
		return o_Traversed(object).lazyComplete();
	}

	@Override
	protected A_Map o_ConstantBindings (final AvailObject object)
	{
		return o_Traversed(object).constantBindings();
	}

	@Override
	protected A_Type o_ContentType (final AvailObject object)
	{
		return o_Traversed(object).contentType();
	}

	@Override
	protected A_Continuation o_Continuation (final AvailObject object)
	{
		return o_Traversed(object).continuation();
	}

	@Override
	protected A_Tuple o_CopyAsMutableIntTuple (final AvailObject object)
	{
		return o_Traversed(object).copyAsMutableIntTuple();
	}

	@Override
	protected A_Tuple o_CopyAsMutableObjectTuple (final AvailObject object)
	{
		return o_Traversed(object).copyAsMutableObjectTuple();
	}

	@Override
	protected A_Type o_DefaultType (final AvailObject object)
	{
		return o_Traversed(object).defaultType();
	}

	@Override
	protected A_Continuation o_EnsureMutable (final AvailObject object)
	{
		return o_Traversed(object).ensureMutable();
	}

	@Override
	ExecutionState o_ExecutionState (final AvailObject object)
	{
		return o_Traversed(object).executionState();
	}

	@Override
	protected void o_Expand (
		final AvailObject object,
		final A_Module module)
	{
		o_Traversed(object).expand(module);
	}

	@Override
	protected boolean o_ExtractBoolean (final AvailObject object)
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
	protected int o_ExtractInt (final AvailObject object)
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
	protected byte o_ExtractNybble (final AvailObject object)
	{
		return o_Traversed(object).extractNybble();
	}

	@Override
	protected A_Map o_FieldMap (final AvailObject object)
	{
		return o_Traversed(object).fieldMap();
	}

	@Override
	protected A_Map o_FieldTypeMap (final AvailObject object)
	{
		return o_Traversed(object).fieldTypeMap();
	}

	@Override
	AvailObject o_GetValue (final AvailObject object)
		throws VariableGetException
	{
		return o_Traversed(object).getValue();
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return o_Traversed(object).hash();
	}

	@Override
	protected int o_HashOrZero (final AvailObject object)
	{
		return o_Traversed(object).hashOrZero();
	}

	@Override
	protected boolean o_HasGrammaticalRestrictions (final AvailObject object)
	{
		return o_Traversed(object).hasGrammaticalRestrictions();
	}

	@Override
	protected A_Tuple o_DefinitionsTuple (final AvailObject object)
	{
		return o_Traversed(object).definitionsTuple();
	}

	@Override
	protected A_Map o_LazyIncomplete (final AvailObject object)
	{
		return o_Traversed(object).lazyIncomplete();
	}

	@Override
	protected void o_DecrementCountdownToReoptimize (
		final AvailObject object,
		final Continuation1NotNull<Boolean> continuation)
	{
		o_Traversed(object).decrementCountdownToReoptimize(continuation);
	}

	@Override
	protected boolean o_IsAbstractDefinition (final AvailObject object)
	{
		return o_Traversed(object).isAbstractDefinition();
	}

	@Override
	protected boolean o_IsAbstract (final AvailObject object)
	{
		return o_Traversed(object).isAbstract();
	}

	@Override
	protected boolean o_IsBoolean (final AvailObject object)
	{
		return o_Traversed(object).isBoolean();
	}

	@Override
	protected boolean o_IsUnsignedByte (final AvailObject object)
	{
		return o_Traversed(object).isUnsignedByte();
	}

	/**
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	protected boolean o_IsByteTuple (final AvailObject object)
	{
		return o_Traversed(object).isByteTuple();
	}

	@Override
	protected boolean o_IsCharacter (final AvailObject object)
	{
		return o_Traversed(object).isCharacter();
	}

	@Override
	protected boolean o_IsFunction (final AvailObject object)
	{
		return o_Traversed(object).isFunction();
	}

	@Override
	protected boolean o_IsAtom (final AvailObject object)
	{
		return o_Traversed(object).isAtom();
	}

	@Override
	protected boolean o_IsExtendedInteger (final AvailObject object)
	{
		return o_Traversed(object).isExtendedInteger();
	}

	@Override
	protected boolean o_IsFinite (final AvailObject object)
	{
		return o_Traversed(object).isFinite();
	}

	@Override
	protected boolean o_IsForwardDefinition (final AvailObject object)
	{
		return o_Traversed(object).isForwardDefinition();
	}

	@Override
	protected boolean o_IsInstanceMeta (final AvailObject object)
	{
		return o_Traversed(object).isInstanceMeta();
	}

	@Override
	protected boolean o_IsMethodDefinition (final AvailObject object)
	{
		return o_Traversed(object).isMethodDefinition();
	}

	@Override
	protected boolean o_IsIntegerRangeType (final AvailObject object)
	{
		return o_Traversed(object).isIntegerRangeType();
	}

	@Override
	protected boolean o_IsMap (final AvailObject object)
	{
		return o_Traversed(object).isMap();
	}

	@Override
	protected boolean o_IsMapType (final AvailObject object)
	{
		return o_Traversed(object).isMapType();
	}

	@Override
	protected boolean o_IsNybble (final AvailObject object)
	{
		return o_Traversed(object).isNybble();
	}

	@Override
	protected boolean o_IsPositive (final AvailObject object)
	{
		return o_Traversed(object).isPositive();
	}

	@Override
	protected boolean o_IsSet (final AvailObject object)
	{
		return o_Traversed(object).isSet();
	}

	@Override
	protected boolean o_IsSetType (final AvailObject object)
	{
		return o_Traversed(object).isSetType();
	}

	/**
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	protected boolean o_IsString (final AvailObject object)
	{
		return o_Traversed(object).isString();
	}

	@Override
	protected boolean o_IsSupertypeOfBottom (final AvailObject object)
	{
		return o_Traversed(object).isSupertypeOfBottom();
	}

	@Override
	protected boolean o_IsTuple (final AvailObject object)
	{
		return o_Traversed(object).isTuple();
	}

	@Override
	protected boolean o_IsTupleType (final AvailObject object)
	{
		return o_Traversed(object).isTupleType();
	}

	@Override
	protected boolean o_IsType (final AvailObject object)
	{
		return o_Traversed(object).isType();
	}

	@Override
	protected A_Set o_KeysAsSet (final AvailObject object)
	{
		return o_Traversed(object).keysAsSet();
	}

	@Override
	protected A_Type o_KeyType (final AvailObject object)
	{
		return o_Traversed(object).keyType();
	}

	@Override
	L2Chunk o_LevelTwoChunk (final AvailObject object)
	{
		return o_Traversed(object).levelTwoChunk();
	}

	@Override
	protected int o_LevelTwoOffset (final AvailObject object)
	{
		return o_Traversed(object).levelTwoOffset();
	}

	@Override
	AvailObject o_Literal (final AvailObject object)
	{
		return o_Traversed(object).literal();
	}

	@Override
	protected A_Number o_LowerBound (final AvailObject object)
	{
		return o_Traversed(object).lowerBound();
	}

	@Override
	protected boolean o_LowerInclusive (final AvailObject object)
	{
		return o_Traversed(object).lowerInclusive();
	}

	@Override
	AvailObject o_MakeSubobjectsImmutable (final AvailObject object)
	{
		return o_Traversed(object).makeSubobjectsImmutable();
	}

	@Override
	protected void o_MakeSubobjectsShared (final AvailObject object)
	{
		o_Traversed(object).makeSubobjectsShared();
	}

	@Override
	protected int o_MapSize (final AvailObject object)
	{
		return o_Traversed(object).mapSize();
	}

	@Override
	protected int o_MaxStackDepth (final AvailObject object)
	{
		return o_Traversed(object).maxStackDepth();
	}

	@Override
	protected A_Atom o_Message (final AvailObject object)
	{
		return o_Traversed(object).message();
	}

	@Override
	protected A_Tuple o_MessageParts (final AvailObject object)
	{
		return o_Traversed(object).messageParts();
	}

	@Override
	protected A_Set o_MethodDefinitions (final AvailObject object)
	{
		return o_Traversed(object).methodDefinitions();
	}

	@Override
	protected A_Map o_ImportedNames (final AvailObject object)
	{
		return o_Traversed(object).importedNames();
	}

	@Override
	protected A_Map o_NewNames (final AvailObject object)
	{
		return o_Traversed(object).newNames();
	}

	@Override
	protected int o_NumArgs (final AvailObject object)
	{
		return o_Traversed(object).numArgs();
	}

	@Override
	protected int o_NumSlots (final AvailObject object)
	{
		return o_Traversed(object).numSlots();
	}

	@Override
	protected int o_NumLiterals (final AvailObject object)
	{
		return o_Traversed(object).numLiterals();
	}

	@Override
	protected int o_NumLocals (final AvailObject object)
	{
		return o_Traversed(object).numLocals();
	}

	@Override
	protected int o_NumOuters (final AvailObject object)
	{
		return o_Traversed(object).numOuters();
	}

	@Override
	protected int o_NumOuterVars (final AvailObject object)
	{
		return o_Traversed(object).numOuterVars();
	}

	@Override
	protected A_Tuple o_Nybbles (final AvailObject object)
	{
		return o_Traversed(object).nybbles();
	}

	@Override
	protected A_BasicObject o_Parent (final AvailObject object)
	{
		return o_Traversed(object).parent();
	}

	@Override
	protected int o_Pc (final AvailObject object)
	{
		return o_Traversed(object).pc();
	}

	@Override
	protected int o_Priority (final AvailObject object)
	{
		return o_Traversed(object).priority();
	}

	@Override
	protected A_Map o_PrivateNames (final AvailObject object)
	{
		return o_Traversed(object).privateNames();
	}

	@Override
	protected A_Map o_FiberGlobals (final AvailObject object)
	{
		return o_Traversed(object).fiberGlobals();
	}

	@Override
	protected A_Set o_GrammaticalRestrictions (final AvailObject object)
	{
		return o_Traversed(object).grammaticalRestrictions();
	}

	@Override
	protected A_Type o_ReturnType (final AvailObject object)
	{
		return o_Traversed(object).returnType();
	}

	@Override
	protected int o_SetBinHash (final AvailObject object)
	{
		return o_Traversed(object).setBinHash();
	}

	@Override
	protected int o_SetBinSize (final AvailObject object)
	{
		return o_Traversed(object).setBinSize();
	}

	@Override
	protected int o_SetSize (final AvailObject object)
	{
		return o_Traversed(object).setSize();
	}

	@Override
	protected A_Type o_SizeRange (final AvailObject object)
	{
		return o_Traversed(object).sizeRange();
	}

	@Override
	protected A_Map o_LazyActions (final AvailObject object)
	{
		return o_Traversed(object).lazyActions();
	}

	@Override
	protected int o_Stackp (final AvailObject object)
	{
		return o_Traversed(object).stackp();
	}

	@Override
	protected int o_Start (final AvailObject object)
	{
		return o_Traversed(object).start();
	}

	@Override
	L2Chunk o_StartingChunk (final AvailObject object)
	{
		return o_Traversed(object).startingChunk();
	}

	@Override
	protected A_String o_String (final AvailObject object)
	{
		return o_Traversed(object).string();
	}

	@Override
	TokenType o_TokenType (final AvailObject object)
	{
		return o_Traversed(object).tokenType();
	}

	@Override
	protected void o_TrimExcessInts (final AvailObject object)
	{
		o_Traversed(object).trimExcessInts();
	}

	@Override
	protected A_Tuple o_TupleReverse (final AvailObject object)
	{
		return o_Traversed(object).tupleReverse();
	}

	@Override
	protected int o_TupleSize (final AvailObject object)
	{
		return o_Traversed(object).tupleSize();
	}

	@Override
	protected A_Type o_Kind (final AvailObject object)
	{
		return o_Traversed(object).kind();
	}

	@Override
	protected A_Tuple o_TypeTuple (final AvailObject object)
	{
		return o_Traversed(object).typeTuple();
	}

	@Override
	protected A_Number o_UpperBound (final AvailObject object)
	{
		return o_Traversed(object).upperBound();
	}

	@Override
	protected boolean o_UpperInclusive (final AvailObject object)
	{
		return o_Traversed(object).upperInclusive();
	}

	@Override
	AvailObject o_Value (final AvailObject object)
	{
		return o_Traversed(object).value();
	}

	@Override
	protected A_Tuple o_ValuesAsTuple (final AvailObject object)
	{
		return o_Traversed(object).valuesAsTuple();
	}

	@Override
	protected A_Type o_ValueType (final AvailObject object)
	{
		return o_Traversed(object).valueType();
	}

	@Override
	protected A_Map o_VariableBindings (final AvailObject object)
	{
		return o_Traversed(object).variableBindings();
	}

	@Override
	protected A_Set o_VisibleNames (final AvailObject object)
	{
		return o_Traversed(object).visibleNames();
	}

	@Override
	protected A_Tuple o_ParsingInstructions (final AvailObject object)
	{
		return o_Traversed(object).parsingInstructions();
	}

	@Override
	protected A_Phrase o_Expression (final AvailObject object)
	{
		return o_Traversed(object).expression();
	}

	@Override
	protected A_Phrase o_Variable (final AvailObject object)
	{
		return o_Traversed(object).variable();
	}

	@Override
	protected A_Tuple o_ArgumentsTuple (final AvailObject object)
	{
		return o_Traversed(object).argumentsTuple();
	}

	@Override
	protected A_Tuple o_StatementsTuple (final AvailObject object)
	{
		return o_Traversed(object).statementsTuple();
	}

	@Override
	protected A_Type o_ResultType (final AvailObject object)
	{
		return o_Traversed(object).resultType();
	}

	@Override
	protected void o_NeededVariables (
		final AvailObject object,
		final A_Tuple neededVariables)
	{
		o_Traversed(object).neededVariables(neededVariables);
	}

	@Override
	protected A_Tuple o_NeededVariables (final AvailObject object)
	{
		return o_Traversed(object).neededVariables();
	}

	@Override
	@Nullable
	protected Primitive o_Primitive (final AvailObject object)
	{
		return o_Traversed(object).primitive();
	}

	@Override
	protected int o_PrimitiveNumber (final AvailObject object)
	{
		return o_Traversed(object).primitiveNumber();
	}

	@Override
	protected A_Type o_DeclaredType (final AvailObject object)
	{
		return o_Traversed(object).declaredType();
	}

	@Override
	DeclarationKind o_DeclarationKind (final AvailObject object)
	{
		return o_Traversed(object).declarationKind();
	}

	@Override
	protected A_Phrase o_TypeExpression (final AvailObject object)
	{
		return o_Traversed(object).typeExpression();
	}

	@Override
	AvailObject o_InitializationExpression (final AvailObject object)
	{
		return o_Traversed(object).initializationExpression();
	}

	@Override
	AvailObject o_LiteralObject (final AvailObject object)
	{
		return o_Traversed(object).literalObject();
	}

	@Override
	protected A_Token o_Token (final AvailObject object)
	{
		return o_Traversed(object).token();
	}

	@Override
	AvailObject o_MarkerValue (final AvailObject object)
	{
		return o_Traversed(object).markerValue();
	}

	@Override
	protected A_Phrase o_ArgumentsListNode (
		final AvailObject object)
	{
		return o_Traversed(object).argumentsListNode();
	}

	@Override
	protected A_Bundle o_Bundle (final AvailObject object)
	{
		return o_Traversed(object).bundle();
	}

	@Override
	protected A_Tuple o_ExpressionsTuple (final AvailObject object)
	{
		return o_Traversed(object).expressionsTuple();
	}

	@Override
	protected A_Phrase o_Declaration (final AvailObject object)
	{
		return o_Traversed(object).declaration();
	}

	@Override
	protected A_Type o_ExpressionType (final AvailObject object)
	{
		return o_Traversed(object).expressionType();
	}

	@Override
	protected void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		o_Traversed(object).emitEffectOn(codeGenerator);
	}

	@Override
	protected void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		o_Traversed(object).emitValueOn(codeGenerator);
	}

	@Override
	protected void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		o_Traversed(object).childrenMap(transformer);
	}

	@Override
	protected void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		o_Traversed(object).childrenDo(action);
	}

	@Override
	protected void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		o_Traversed(object).validateLocally(parent);
	}

	@Override
	protected A_RawFunction o_GenerateInModule (
		final AvailObject object,
		final A_Module module)
	{
		return o_Traversed(object).generateInModule(module);
	}

	@Override
	protected A_Phrase o_CopyWith (
		final AvailObject object,
		final A_Phrase newPhrase)
	{
		return o_Traversed(object).copyWith(newPhrase);
	}

	@Override
	protected A_Phrase o_CopyConcatenating (
		final AvailObject object,
		final A_Phrase newListPhrase)
	{
		return o_Traversed(object).copyConcatenating(newListPhrase);
	}

	@Override
	protected void o_IsLastUse (
		final AvailObject object,
		final boolean isLastUse)
	{
		o_Traversed(object).isLastUse(isLastUse);
	}

	@Override
	protected boolean o_IsLastUse (
		final AvailObject object)
	{
		return o_Traversed(object).isLastUse();
	}

	@Override
	protected boolean o_IsMacroDefinition (
		final AvailObject object)
	{
		return o_Traversed(object).isMacroDefinition();
	}

	@Override
	protected A_Phrase o_CopyMutablePhrase (
		final AvailObject object)
	{
		return o_Traversed(object).copyMutablePhrase();
	}

	@Override
	protected A_Type o_BinUnionKind (
		final AvailObject object)
	{
		return o_Traversed(object).binUnionKind();
	}

	@Override
	protected A_Phrase o_OutputPhrase (
		final AvailObject object)
	{
		return o_Traversed(object).outputPhrase();
	}

	@Override
	protected A_Atom o_ApparentSendName (
		final AvailObject object)
	{
		return o_Traversed(object).apparentSendName();
	}

	@Override
	protected A_Tuple o_Statements (final AvailObject object)
	{
		return o_Traversed(object).statements();
	}

	@Override
	protected void o_FlattenStatementsInto (
		final AvailObject object,
		final List<A_Phrase> accumulatedStatements)
	{
		o_Traversed(object).flattenStatementsInto(accumulatedStatements);
	}

	@Override
	protected int o_LineNumber (final AvailObject object)
	{
		return o_Traversed(object).lineNumber();
	}

	@Override
	protected A_Map o_AllParsingPlansInProgress (final AvailObject object)
	{
		return o_Traversed(object).allParsingPlansInProgress();
	}

	@Override
	protected boolean o_IsSetBin (final AvailObject object)
	{
		return o_Traversed(object).isSetBin();
	}

	@Override
	MapIterable o_MapIterable (
		final AvailObject object)
	{
		return o_Traversed(object).mapIterable();
	}

	@Override
	protected A_Set o_DeclaredExceptions (
		final AvailObject object)
	{
		return o_Traversed(object).declaredExceptions();
	}

	@Override
	protected boolean o_IsInt (
		final AvailObject object)
	{
		return o_Traversed(object).isInt();
	}

	@Override
	protected boolean o_IsLong (
		final AvailObject object)
	{
		return o_Traversed(object).isLong();
	}

	@Override
	protected A_Type o_ArgsTupleType (final AvailObject object)
	{
		return o_Traversed(object).argsTupleType();
	}

	@Override
	protected boolean o_EqualsInstanceTypeFor (
		final AvailObject object,
		final AvailObject anObject)
	{
		return o_Traversed(object).equalsInstanceTypeFor(anObject);
	}

	@Override
	protected A_Set o_Instances (final AvailObject object)
	{
		return o_Traversed(object).instances();
	}

	@Override
	protected boolean o_EqualsEnumerationWithSet (
		final AvailObject object,
		final A_Set aSet)
	{
		return o_Traversed(object).equalsEnumerationWithSet(aSet);
	}

	@Override
	protected boolean o_IsEnumeration (final AvailObject object)
	{
		return o_Traversed(object).isEnumeration();
	}

	@Override
	protected boolean o_IsInstanceOf (
		final AvailObject object,
		final A_Type aType)
	{
		return o_Traversed(object).isInstanceOf(aType);
	}

	@Override
	protected boolean o_EnumerationIncludesInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return o_Traversed(object).enumerationIncludesInstance(
			potentialInstance);
	}

	@Override
	protected A_Type o_ComputeSuperkind (final AvailObject object)
	{
		return o_Traversed(object).computeSuperkind();
	}

	@Override
	protected void o_SetAtomProperty (
		final AvailObject object,
		final A_Atom key,
		final A_BasicObject value)
	{
		o_Traversed(object).setAtomProperty(key, value);
	}

	@Override
	AvailObject o_GetAtomProperty (
		final AvailObject object,
		final A_Atom key)
	{
		return o_Traversed(object).getAtomProperty(key);
	}

	@Override
	protected boolean o_EqualsEnumerationType (
		final AvailObject object,
		final A_BasicObject another)
	{
		return o_Traversed(object).equalsEnumerationType(another);
	}

	@Override
	protected A_Type o_ReadType (final AvailObject object)
	{
		return o_Traversed(object).readType();
	}

	@Override
	protected A_Type o_WriteType (final AvailObject object)
	{
		return o_Traversed(object).writeType();
	}

	@Override
	protected void o_Versions (
		final AvailObject object,
		final A_Set versionStrings)
	{
		o_Traversed(object).versions(versionStrings);
	}

	@Override
	protected A_Set o_Versions (final AvailObject object)
	{
		return o_Traversed(object).versions();
	}

	@Override
	protected boolean o_EqualsPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		return o_Traversed(object).equalsPhraseType(aPhraseType);
	}

	@Override
	protected PhraseKind o_PhraseKind (final AvailObject object)
	{
		return o_Traversed(object).phraseKind();
	}

	@Override
	protected boolean o_PhraseKindIsUnder (
		final AvailObject object,
		final PhraseKind expectedPhraseKind)
	{
		return o_Traversed(object).phraseKindIsUnder(expectedPhraseKind);
	}

	@Override
	protected boolean o_IsRawPojo (final AvailObject object)
	{
		return o_Traversed(object).isRawPojo();
	}

	@Override
	protected void o_AddSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction restrictionSignature)
	{
		o_Traversed(object).addSemanticRestriction(restrictionSignature);
	}

	@Override
	protected void o_RemoveSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction restriction)
	{
		o_Traversed(object).removeSemanticRestriction(restriction);
	}

	@Override
	protected A_Set o_SemanticRestrictions (
		final AvailObject object)
	{
		return o_Traversed(object).semanticRestrictions();
	}

	@Override
	protected void o_AddSealedArgumentsType (
		final AvailObject object,
		final A_Tuple typeTuple)
	{
		o_Traversed(object).addSealedArgumentsType(typeTuple);
	}

	@Override
	protected void o_RemoveSealedArgumentsType (
		final AvailObject object,
		final A_Tuple typeTuple)
	{
		o_Traversed(object).removeSealedArgumentsType(typeTuple);
	}

	@Override
	protected A_Tuple o_SealedArgumentsTypesTuple (
		final AvailObject object)
	{
		return o_Traversed(object).sealedArgumentsTypesTuple();
	}

	@Override
	protected void o_ModuleAddSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction semanticRestriction)
	{
		o_Traversed(object).moduleAddSemanticRestriction(
			semanticRestriction);
	}

	@Override
	protected void o_AddConstantBinding (
		final AvailObject object,
		final A_String name,
		final A_Variable constantBinding)
	{
		o_Traversed(object).addConstantBinding(
			name,
			constantBinding);
	}

	@Override
	protected void o_AddVariableBinding (
		final AvailObject object,
		final A_String name,
		final A_Variable variableBinding)
	{
		o_Traversed(object).addVariableBinding(
			name,
			variableBinding);
	}

	@Override
	protected boolean o_IsMethodEmpty (
		final AvailObject object)
	{
		return o_Traversed(object).isMethodEmpty();
	}

	@Override
	protected boolean o_IsPojoSelfType (final AvailObject object)
	{
		return o_Traversed(object).isPojoSelfType();
	}

	@Override
	protected A_Type o_PojoSelfType (final AvailObject object)
	{
		return o_Traversed(object).pojoSelfType();
	}

	@Override
	AvailObject o_JavaClass (final AvailObject object)
	{
		return o_Traversed(object).javaClass();
	}

	@Override
	protected boolean o_IsUnsignedShort (final AvailObject object)
	{
		return o_Traversed(object).isUnsignedShort();
	}

	@Override
	protected int o_ExtractUnsignedShort (final AvailObject object)
	{
		return o_Traversed(object).extractUnsignedShort();
	}

	@Override
	protected boolean o_IsFloat (final AvailObject object)
	{
		return o_Traversed(object).isFloat();
	}

	@Override
	protected boolean o_IsDouble (final AvailObject object)
	{
		return o_Traversed(object).isDouble();
	}

	@Override
	AvailObject o_RawPojo (final AvailObject object)
	{
		return o_Traversed(object).rawPojo();
	}

	@Override
	protected boolean o_IsPojo (final AvailObject object)
	{
		return o_Traversed(object).isPojo();
	}

	@Override
	protected boolean o_IsPojoType (final AvailObject object)
	{
		return o_Traversed(object).isPojoType();
	}

	@Override
	Order o_NumericCompare (
		final AvailObject object,
		final A_Number another)
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
	protected A_Number o_AddToDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	protected A_Number o_AddToFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	protected A_Number o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	protected A_Number o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	protected A_Number o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	protected A_Number o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	protected A_Number o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	protected A_Number o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	protected A_Map o_LazyPrefilterMap (
		final AvailObject object)
	{
		return o_Traversed(object).lazyPrefilterMap();
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return o_Traversed(object).serializerOperation();
	}

	@Override
	protected A_MapBin o_MapBinAtHashPutLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash,
		final A_BasicObject value,
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

	@Override
	protected A_MapBin o_MapBinRemoveKeyHashCanDestroy (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapBinRemoveKeyHashCanDestroy(
			key,
			keyHash,
			canDestroy);
	}

	@Override
	protected int o_MapBinSize (final AvailObject object)
	{
		return o_Traversed(object).mapBinSize();
	}

	@Override
	protected A_Type o_MapBinKeyUnionKind (
		final AvailObject object)
	{
		return o_Traversed(object).mapBinKeyUnionKind();
	}

	@Override
	protected A_Type o_MapBinValueUnionKind (
		final AvailObject object)
	{
		return o_Traversed(object).mapBinValueUnionKind();
	}

	@Override
	protected boolean o_IsHashedMapBin (
		final AvailObject object)
	{
		return o_Traversed(object).isHashedMapBin();
	}

	@Override
	protected @Nullable AvailObject o_MapBinAtHash (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash)
	{
		return o_Traversed(object).mapBinAtHash(key, keyHash);
	}

	@Override
	protected int o_MapBinKeysHash (
		final AvailObject object)
	{
		return o_Traversed(object).mapBinKeysHash();
	}

	@Override
	protected int o_MapBinValuesHash (final AvailObject object)
	{
		return o_Traversed(object).mapBinValuesHash();
	}

	@Override
	protected A_Module o_IssuingModule (
		final AvailObject object)
	{
		return o_Traversed(object).issuingModule();
	}

	@Override
	protected boolean o_IsPojoFusedType (final AvailObject object)
	{
		return o_Traversed(object).isPojoFusedType();
	}

	@Override
	protected boolean o_IsSupertypeOfPojoBottomType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return o_Traversed(object).isSupertypeOfPojoBottomType(aPojoType);
	}

	@Override
	protected boolean o_EqualsPojoBottomType (final AvailObject object)
	{
		return o_Traversed(object).equalsPojoBottomType();
	}

	@Override
	AvailObject o_JavaAncestors (final AvailObject object)
	{
		return o_Traversed(object).javaAncestors();
	}

	@Override
	protected A_Type o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		return o_Traversed(object).typeIntersectionOfPojoFusedType(
			aFusedPojoType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		return o_Traversed(object).typeIntersectionOfPojoUnfusedType(
			anUnfusedPojoType);
	}

	@Override
	protected A_Type o_TypeUnionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		return o_Traversed(object).typeUnionOfPojoFusedType(
			aFusedPojoType);
	}

	@Override
	protected A_Type o_TypeUnionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		return o_Traversed(object).typeUnionOfPojoUnfusedType(
			anUnfusedPojoType);
	}

	@Override
	protected boolean o_IsPojoArrayType (final AvailObject object)
	{
		return o_Traversed(object).isPojoArrayType();
	}

	@Override
	protected @Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> classHint)
	{
		return o_Traversed(object).marshalToJava(classHint);
	}

	@Override
	protected A_Map o_TypeVariables (final AvailObject object)
	{
		return o_Traversed(object).typeVariables();
	}

	@Override
	protected boolean o_EqualsPojoField (
		final AvailObject object,
		final AvailObject field,
		final AvailObject receiver)
	{
		return o_Traversed(object).equalsPojoField(field, receiver);
	}

	@Override
	protected boolean o_IsSignedByte (final AvailObject object)
	{
		return o_Traversed(object).isSignedByte();
	}

	@Override
	protected boolean o_IsSignedShort (final AvailObject object)
	{
		return o_Traversed(object).isSignedShort();
	}

	@Override
	protected byte o_ExtractSignedByte (final AvailObject object)
	{
		return o_Traversed(object).extractSignedByte();
	}

	@Override
	short o_ExtractSignedShort (final AvailObject object)
	{
		return o_Traversed(object).extractSignedShort();
	}

	@Override
	protected boolean o_EqualsEqualityRawPojo (
		final AvailObject object,
		final AvailObject otherEqualityRawPojo,
		final @Nullable Object otherJavaObject)
	{
		return o_Traversed(object).equalsEqualityRawPojoFor(object, otherJavaObject);
	}

	@Override
	protected @Nullable <T> T o_JavaObject (final AvailObject object)
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
	protected A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		return o_Traversed(object).appendCanDestroy(newElement, canDestroy);
	}

	@Override
	protected A_Map o_LazyIncompleteCaseInsensitive (
		final AvailObject object)
	{
		return o_Traversed(object).lazyIncompleteCaseInsensitive();
	}

	@Override
	protected A_String o_LowerCaseString (final AvailObject object)
	{
		return o_Traversed(object).lowerCaseString();
	}

	@Override
	protected A_Number o_InstanceCount (final AvailObject object)
	{
		return o_Traversed(object).instanceCount();
	}

	@Override
	long o_TotalInvocations (final AvailObject object)
	{
		return o_Traversed(object).totalInvocations();
	}

	@Override
	protected void o_TallyInvocation (final AvailObject object)
	{
		o_Traversed(object).tallyInvocation();
	}

	@Override
	protected A_Tuple o_FieldTypeTuple (final AvailObject object)
	{
		return o_Traversed(object).fieldTypeTuple();
	}

	@Override
	protected A_Tuple o_FieldTuple (final AvailObject object)
	{
		return o_Traversed(object).fieldTuple();
	}

	@Override
	protected A_Type o_LiteralType (final AvailObject object)
	{
		return o_Traversed(object).literalType();
	}

	@Override
	protected A_Type o_TypeIntersectionOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return o_Traversed(object).typeIntersectionOfTokenType(aTokenType);
	}

	@Override
	protected A_Type o_TypeIntersectionOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return o_Traversed(object).typeIntersectionOfLiteralTokenType(
			aLiteralTokenType);
	}

	@Override
	protected A_Type o_TypeUnionOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return o_Traversed(object).typeUnionOfTokenType(aTokenType);
	}

	@Override
	protected A_Type o_TypeUnionOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return o_Traversed(object).typeUnionOfLiteralTokenType(
			aLiteralTokenType);
	}

	@Override
	protected boolean o_IsTokenType (final AvailObject object)
	{
		return o_Traversed(object).isTokenType();
	}

	@Override
	protected boolean o_IsLiteralTokenType (final AvailObject object)
	{
		return o_Traversed(object).isLiteralTokenType();
	}

	@Override
	protected boolean o_IsLiteralToken (final AvailObject object)
	{
		return o_Traversed(object).isLiteralToken();
	}

	@Override
	protected boolean o_IsSupertypeOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return o_Traversed(object).isSupertypeOfTokenType(
			aTokenType);
	}

	@Override
	protected boolean o_IsSupertypeOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return o_Traversed(object).isSupertypeOfLiteralTokenType(
			aLiteralTokenType);
	}

	@Override
	protected boolean o_EqualsTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return o_Traversed(object).equalsTokenType(aTokenType);
	}

	@Override
	protected boolean o_EqualsLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return o_Traversed(object).equalsLiteralTokenType(aLiteralTokenType);
	}

	@Override
	protected boolean o_EqualsObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return o_Traversed(object).equalsObjectType(anObjectType);
	}

	@Override
	protected boolean o_EqualsToken (
		final AvailObject object,
		final A_Token aToken)
	{
		return o_Traversed(object).equalsToken(aToken);
	}

	@Override
	protected A_Number o_BitwiseAnd (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitwiseAnd(anInteger, canDestroy);
	}

	@Override
	protected A_Number o_BitwiseOr (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitwiseOr(anInteger, canDestroy);
	}

	@Override
	protected A_Number o_BitwiseXor (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitwiseXor(anInteger, canDestroy);
	}

	@Override
	protected void o_AddSeal (
		final AvailObject object,
		final A_Atom methodName,
		final A_Tuple argumentTypes)
	{
		o_Traversed(object).addSeal(methodName, argumentTypes);
	}

	@Override
	AvailObject o_Instance (
		final AvailObject object)
	{
		return o_Traversed(object).instance();
	}

	@Override
	protected void o_SetMethodName (
		final AvailObject object,
		final A_String methodName)
	{
		o_Traversed(object).setMethodName(methodName);
	}

	@Override
	protected int o_StartingLineNumber (
		final AvailObject object)
	{
		return o_Traversed(object).startingLineNumber();
	}

	@Override
	protected A_Module o_Module (final AvailObject object)
	{
		return o_Traversed(object).module();
	}

	@Override
	protected A_String o_MethodName (final AvailObject object)
	{
		return o_Traversed(object).methodName();
	}

	@Override
	protected String o_NameForDebugger (final AvailObject object)
	{
		final String name = o_Traversed(object).nameForDebugger();
		return "IND→" + name;
	}

	@Override
	protected boolean o_BinElementsAreAllInstancesOfKind (
		final AvailObject object,
		final A_Type kind)
	{
		return o_Traversed(object).binElementsAreAllInstancesOfKind(kind);
	}

	@Override
	protected boolean o_SetElementsAreAllInstancesOfKind (
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
	protected boolean o_RangeIncludesInt (
		final AvailObject object,
		final int anInt)
	{
		return o_Traversed(object).rangeIncludesInt(anInt);
	}

	@Override
	protected A_Number o_BitShiftLeftTruncatingToBits (
		final AvailObject object,
		final A_Number shiftFactor,
		final A_Number truncationBits,
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
	protected A_Number o_BitShift (
		final AvailObject object,
		final A_Number shiftFactor,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitShift(shiftFactor, canDestroy);
	}

	@Override
	protected boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return o_Traversed(object).equalsPhrase(aPhrase);
	}

	@Override
	protected A_Phrase o_StripMacro (
		final AvailObject object)
	{
		return o_Traversed(object).stripMacro();
	}

	@Override
	protected A_Method o_DefinitionMethod (
		final AvailObject object)
	{
		return o_Traversed(object).definitionMethod();
	}

	@Override
	protected A_Tuple o_PrefixFunctions (
		final AvailObject object)
	{
		return o_Traversed(object).prefixFunctions();
	}

	@Override
	protected boolean o_EqualsByteArrayTuple (
		final AvailObject object,
		final A_Tuple aByteArrayTuple)
	{
		return o_Traversed(object).equalsByteArrayTuple(aByteArrayTuple);
	}

	@Override
	protected boolean o_CompareFromToWithByteArrayTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteArrayTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithByteArrayTupleStartingAt(
			startIndex1, endIndex1, aByteArrayTuple, startIndex2);
	}

	@Override
	protected byte[] o_ByteArray (final AvailObject object)
	{
		return o_Traversed(object).byteArray();
	}

	@Override
	protected boolean o_IsByteArrayTuple (final AvailObject object)
	{
		return o_Traversed(object).isByteArrayTuple();
	}

	@Override
	protected void o_UpdateForNewGrammaticalRestriction (
		final AvailObject object,
		final A_ParsingPlanInProgress planInProgress,
		final Deque<Pair<A_BundleTree, A_ParsingPlanInProgress>> treesToVisit)
	{
		o_Traversed(object).updateForNewGrammaticalRestriction(
			planInProgress,
			treesToVisit);
	}

	@Override
	protected void o_Lock (final AvailObject object, final Continuation0 critical)
	{
		o_Traversed(object).lock(critical);
	}

	@Override
	<T> T o_Lock (final AvailObject object, final Supplier<T> supplier)
	{
		return o_Traversed(object).lock(supplier);
	}

	@Override
	protected A_String o_ModuleName (final AvailObject object)
	{
		return o_Traversed(object).moduleName();
	}

	@Override
	protected A_Method o_BundleMethod (final AvailObject object)
	{
		return o_Traversed(object).bundleMethod();
	}

	@Override
	AvailObject o_GetAndSetValue (
			final AvailObject object,
			final A_BasicObject newValue)
		throws VariableGetException, VariableSetException
	{
		return o_Traversed(object).getAndSetValue(newValue);
	}

	@Override
	protected boolean o_CompareAndSwapValues (
			final AvailObject object,
			final A_BasicObject reference,
			final A_BasicObject newValue)
		throws VariableGetException, VariableSetException
	{
		return o_Traversed(object).compareAndSwapValues(reference, newValue);
	}

	@Override
	protected A_Number o_FetchAndAddValue (
			final AvailObject object,
			final A_Number addend)
		throws VariableGetException, VariableSetException
	{
		return o_Traversed(object).fetchAndAddValue(addend);
	}

	@Override
	Continuation1NotNull<Throwable> o_FailureContinuation (final AvailObject object)
	{
		return o_Traversed(object).failureContinuation();
	}

	@Override
	Continuation1NotNull<AvailObject> o_ResultContinuation (final AvailObject object)
	{
		return o_Traversed(object).resultContinuation();
	}

	@Override
	@Nullable AvailLoader o_AvailLoader (final AvailObject object)
	{
		return o_Traversed(object).availLoader();
	}

	@Override
	protected void o_AvailLoader (final AvailObject object, @Nullable final AvailLoader loader)
	{
		o_Traversed(object).availLoader(loader);
	}

	@Override
	protected boolean o_InterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		return o_Traversed(object).interruptRequestFlag(flag);
	}

	@Override
	protected boolean o_GetAndClearInterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		return o_Traversed(object).getAndClearInterruptRequestFlag(flag);
	}

	@Override
	protected boolean o_GetAndSetSynchronizationFlag (
		final AvailObject object,
		final SynchronizationFlag flag,
		final boolean newValue)
	{
		return o_Traversed(object).getAndSetSynchronizationFlag(flag, newValue);
	}

	@Override
	AvailObject o_FiberResult (final AvailObject object)
	{
		return o_Traversed(object).fiberResult();
	}

	@Override
	protected void o_FiberResult (final AvailObject object, final A_BasicObject result)
	{
		o_Traversed(object).fiberResult(result);
	}

	@Override
	protected A_Set o_JoiningFibers (final AvailObject object)
	{
		return o_Traversed(object).joiningFibers();
	}

	@Override
	@Nullable TimerTask o_WakeupTask (final AvailObject object)
	{
		return o_Traversed(object).wakeupTask();
	}

	@Override
	protected void o_WakeupTask (final AvailObject object, @Nullable final TimerTask task)
	{
		o_Traversed(object).wakeupTask(task);
	}

	@Override
	protected void o_JoiningFibers (final AvailObject object, final A_Set joiners)
	{
		o_Traversed(object).joiningFibers(joiners);
	}

	@Override
	protected A_Map o_HeritableFiberGlobals (final AvailObject object)
	{
		return o_Traversed(object).heritableFiberGlobals();
	}

	@Override
	protected void o_HeritableFiberGlobals (
		final AvailObject object,
		final A_Map globals)
	{
		o_Traversed(object).heritableFiberGlobals(globals);
	}

	@Override
	protected boolean o_GeneralFlag (final AvailObject object, final GeneralFlag flag)
	{
		return o_Traversed(object).generalFlag(flag);
	}

	@Override
	protected void o_SetGeneralFlag (final AvailObject object, final GeneralFlag flag)
	{
		o_Traversed(object).setGeneralFlag(flag);
	}

	@Override
	protected void o_ClearGeneralFlag (final AvailObject object, final GeneralFlag flag)
	{
		o_Traversed(object).clearGeneralFlag(flag);
	}

	@Override
	protected ByteBuffer o_ByteBuffer (final AvailObject object)
	{
		return o_Traversed(object).byteBuffer();
	}

	@Override
	protected boolean o_EqualsByteBufferTuple (
		final AvailObject object,
		final A_Tuple aByteBufferTuple)
	{
		return o_Traversed(object).equalsByteBufferTuple(aByteBufferTuple);
	}

	@Override
	protected boolean o_CompareFromToWithByteBufferTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteBufferTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithByteBufferTupleStartingAt(
			startIndex1,
			endIndex1,
			aByteBufferTuple,
			startIndex2);
	}

	@Override
	protected boolean o_IsByteBufferTuple (final AvailObject object)
	{
		return o_Traversed(object).isByteBufferTuple();
	}

	@Override
	protected A_String o_FiberName (final AvailObject object)
	{
		return o_Traversed(object).fiberName();
	}

	@Override
	protected void o_FiberNameSupplier (
		final AvailObject object,
		final Supplier<A_String> supplier)
	{
		o_Traversed(object).fiberNameSupplier(supplier);
	}

	@Override
	protected A_Set o_Bundles (final AvailObject object)
	{
		return o_Traversed(object).bundles();
	}

	@Override
	protected void o_MethodAddBundle (final AvailObject object, final A_Bundle bundle)
	{
		o_Traversed(object).methodAddBundle(bundle);
	}

	@Override
	protected A_Module o_DefinitionModule (final AvailObject object)
	{
		return o_Traversed(object).definitionModule();
	}

	@Override
	protected A_String o_DefinitionModuleName (final AvailObject object)
	{
		return o_Traversed(object).definitionModuleName();
	}

	@Override
	protected A_Bundle o_BundleOrCreate (final AvailObject object)
		throws MalformedMessageException
	{
		return o_Traversed(object).bundleOrCreate();
	}
	@Override
	protected A_Bundle o_BundleOrNil (final AvailObject object)
	{
		return o_Traversed(object).bundleOrNil();
	}

	@Override
	protected A_Map o_EntryPoints (final AvailObject object)
	{
		return o_Traversed(object).entryPoints();
	}

	@Override
	protected void o_AddEntryPoint (
		final AvailObject object,
		final A_String stringName,
		final A_Atom trueName)
	{
		o_Traversed(object).addEntryPoint(stringName, trueName);
	}

	@Override
	protected A_Set o_AllAncestors (final AvailObject object)
	{
		return o_Traversed(object).allAncestors();
	}

	@Override
	protected void o_AddAncestors (final AvailObject object, final A_Set moreAncestors)
	{
		o_Traversed(object).addAncestors(moreAncestors);
	}

	@Override
	protected A_Tuple o_ArgumentRestrictionSets (final AvailObject object)
	{
		return o_Traversed(object).argumentRestrictionSets();
	}

	@Override
	protected A_Bundle o_RestrictedBundle (final AvailObject object)
	{
		return o_Traversed(object).restrictedBundle();
	}

	@Override
	protected A_String o_AtomName (final AvailObject object)
	{
		return o_Traversed(object).atomName();
	}

	@Override
	protected void o_AdjustPcAndStackp (
		final AvailObject object,
		final int pc,
		final int stackp)
	{
		o_Traversed(object).adjustPcAndStackp(pc, stackp);
	}

	@Override
	protected int o_TreeTupleLevel (final AvailObject object)
	{
		return o_Traversed(object).treeTupleLevel();
	}

	@Override
	protected int o_ChildCount (final AvailObject object)
	{
		return o_Traversed(object).childCount();
	}

	@Override
	protected A_Tuple o_ChildAt (final AvailObject object, final int childIndex)
	{
		return o_Traversed(object).childAt(childIndex);
	}

	@Override
	protected A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		return o_Traversed(object).concatenateWith(otherTuple, canDestroy);
	}

	@Override
	protected A_Tuple o_ReplaceFirstChild (
		final AvailObject object,
		final A_Tuple newFirst)
	{
		return o_Traversed(object).replaceFirstChild(newFirst);
	}

	@Override
	protected boolean o_IsByteString (final AvailObject object)
	{
		return o_Traversed(object).isByteString();
	}

	@Override
	protected boolean o_IsTwoByteString (final AvailObject object)
	{
		return o_Traversed(object).isTwoByteString();
	}

	@Override
	protected boolean o_IsIntegerIntervalTuple (final AvailObject object)
	{
		return o_Traversed(object).isIntegerIntervalTuple();
	}

	@Override
	protected boolean o_IsSmallIntegerIntervalTuple (final AvailObject object)
	{
		return o_Traversed(object).isSmallIntegerIntervalTuple();
	}

	@Override
	protected boolean o_IsRepeatedElementTuple (final AvailObject object)
	{
		return o_Traversed(object).isRepeatedElementTuple();
	}

	@Override
	protected void o_AddWriteReactor (
		final AvailObject object,
		final A_Atom key,
		final VariableAccessReactor reactor)
	{
		o_Traversed(object).addWriteReactor(key, reactor);
	}

	@Override
	protected void o_RemoveWriteReactor (final AvailObject object, final A_Atom key)
		throws AvailException
	{
		o_Traversed(object).removeWriteReactor(key);
	}

	@Override
	protected boolean o_TraceFlag (final AvailObject object, final TraceFlag flag)
	{
		return o_Traversed(object).traceFlag(flag);
	}

	@Override
	protected void o_SetTraceFlag (final AvailObject object, final TraceFlag flag)
	{
		o_Traversed(object).setTraceFlag(flag);
	}

	@Override
	protected void o_ClearTraceFlag (final AvailObject object, final TraceFlag flag)
	{
		o_Traversed(object).clearTraceFlag(flag);
	}

	@Override
	protected void o_RecordVariableAccess (
		final AvailObject object,
		final A_Variable var,
		final boolean wasRead)
	{
		o_Traversed(object).recordVariableAccess(var, wasRead);
	}

	@Override
	protected A_Set o_VariablesReadBeforeWritten (final AvailObject object)
	{
		return o_Traversed(object).variablesReadBeforeWritten();
	}

	@Override
	protected A_Set o_VariablesWritten (final AvailObject object)
	{
		return o_Traversed(object).variablesWritten();
	}

	@Override
	protected A_Set o_ValidWriteReactorFunctions (final AvailObject object)
	{
		return o_Traversed(object).validWriteReactorFunctions();
	}

	@Override
	protected A_Continuation o_ReplacingCaller(
		final AvailObject object,
		final A_Continuation newCaller)
	{
		return o_Traversed(object).replacingCaller(newCaller);
	}

	@Override
	protected void o_WhenContinuationIsAvailableDo (
		final AvailObject object,
		final Continuation1NotNull<A_Continuation> whenReified)
	{
		o_Traversed(object).whenContinuationIsAvailableDo(whenReified);
	}

	@Override
	protected A_Set o_GetAndClearReificationWaiters (final AvailObject object)
	{
		return o_Traversed(object).getAndClearReificationWaiters();
	}

	@Override
	protected boolean o_IsBottom (final AvailObject object)
	{
		return o_Traversed(object).isBottom();
	}

	@Override
	protected boolean o_IsVacuousType (final AvailObject object)
	{
		return o_Traversed(object).isVacuousType();
	}

	@Override
	protected boolean o_IsTop (final AvailObject object)
	{
		return o_Traversed(object).isTop();
	}

	@Override
	protected boolean o_IsAtomSpecial (final AvailObject object)
	{
		return o_Traversed(object).isAtomSpecial();
	}

	@Override
	protected boolean o_HasValue (final AvailObject object)
	{
		return o_Traversed(object).hasValue();
	}

	@Override
	protected void o_AddUnloadFunction (
		final AvailObject object,
		final A_Function unloadFunction)
	{
		o_Traversed(object).addUnloadFunction(unloadFunction);
	}

	@Override
	protected A_Set o_ExportedNames (final AvailObject object)
	{
		return o_Traversed(object).exportedNames();
	}

	@Override
	protected boolean o_IsInitializedWriteOnceVariable (final AvailObject object)
	{
		return o_Traversed(object).isInitializedWriteOnceVariable();
	}

	@Override
	protected void o_TransferIntoByteBuffer (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final ByteBuffer outputByteBuffer)
	{
		o_Traversed(object).transferIntoByteBuffer(
			startIndex, endIndex, outputByteBuffer);
	}

	@Override
	protected boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		return o_Traversed(object).tupleElementsInRangeAreInstancesOf(
			startIndex, endIndex, type);
	}

	@Override
	protected boolean o_IsNumericallyIntegral (final AvailObject object)
	{
		return o_Traversed(object).isNumericallyIntegral();
	}

	@Override
	TextInterface o_TextInterface (final AvailObject object)
	{
		return o_Traversed(object).textInterface();
	}

	@Override
	protected void o_TextInterface (
		final AvailObject object,
		final TextInterface textInterface)
	{
		o_Traversed(object).textInterface(textInterface);
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		o_Traversed(object).writeTo(writer);
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		o_Traversed(object).writeSummaryTo(writer);
	}

	@Override
	protected A_Type o_TypeIntersectionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return o_Traversed(object).typeIntersectionOfPrimitiveTypeEnum(
			primitiveTypeEnum);
	}

	@Override
	protected A_Type o_TypeUnionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return o_Traversed(object).typeUnionOfPrimitiveTypeEnum(
			primitiveTypeEnum);
	}

	@Override
	protected A_Tuple o_TupleOfTypesFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		return o_Traversed(object).tupleOfTypesFromTo(startIndex, endIndex);
	}

	@Override
	protected boolean o_ShowValueInNameForDebugger (
		final AvailObject object)
	{
		return o_Traversed(object).showValueInNameForDebugger();
	}

	@Override
	protected A_Phrase o_List (final AvailObject object)
	{
		return o_Traversed(object).list();
	}

	@Override
	protected A_Tuple o_Permutation (final AvailObject object)
	{
		return o_Traversed(object).permutation();
	}

	@Override
	protected void o_EmitAllValuesOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		o_Traversed(object).emitAllValuesOn(codeGenerator);
	}

	@Override
	protected A_Type o_SuperUnionType (final AvailObject object)
	{
		return o_Traversed(object).superUnionType();
	}

	@Override
	protected boolean o_HasSuperCast (final AvailObject object)
	{
		return o_Traversed(object).hasSuperCast();
	}

	@Override
	protected A_Tuple o_MacroDefinitionsTuple (final AvailObject object)
	{
		return o_Traversed(object).macroDefinitionsTuple();
	}

	@Override
	protected A_Tuple o_LookupMacroByPhraseTuple (
		final AvailObject object,
		final A_Tuple argumentPhraseTuple)
	{
		return o_Traversed(object).lookupMacroByPhraseTuple(
			argumentPhraseTuple);
	}

	@Override
	protected A_Phrase o_ExpressionAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).expressionAt(index);
	}

	@Override
	protected int o_ExpressionsSize (final AvailObject object)
	{
		return o_Traversed(object).expressionsSize();
	}

	@Override
	protected int o_ParsingPc (final AvailObject object)
	{
		return o_Traversed(object).parsingPc();
	}

	@Override
	protected boolean o_IsMacroSubstitutionNode (final AvailObject object)
	{
		return o_Traversed(object).isMacroSubstitutionNode();
	}

	@Override
	MessageSplitter o_MessageSplitter (final AvailObject object)
	{
		return o_Traversed(object).messageSplitter();
	}

	@Override
	protected void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		o_Traversed(object).statementsDo(continuation);
	}

	@Override
	protected A_Phrase o_MacroOriginalSendNode (final AvailObject object)
	{
		return o_Traversed(object).macroOriginalSendNode();
	}

	@Override
	protected boolean o_EqualsInt (
		final AvailObject object,
		final int theInt)
	{
		return o_Traversed(object).equalsInt(theInt);
	}

	@Override
	protected A_Tuple o_Tokens (final AvailObject object)
	{
		return o_Traversed(object).tokens();
	}

	@Override
	protected A_Bundle o_ChooseBundle (
		final AvailObject object,
		final A_Module currentModule)
	{
		return o_Traversed(object).chooseBundle(currentModule);
	}

	@Override
	protected boolean o_ValueWasStablyComputed (final AvailObject object)
	{
		return o_Traversed(object).valueWasStablyComputed();
	}

	@Override
	protected void o_ValueWasStablyComputed (
		final AvailObject object,
		final boolean wasStablyComputed)
	{
		o_Traversed(object).valueWasStablyComputed(wasStablyComputed);
	}

	@Override
	long o_UniqueId (final AvailObject object)
	{
		return o_Traversed(object).uniqueId();
	}

	@Override
	protected A_Definition o_Definition (final AvailObject object)
	{
		return o_Traversed(object).definition();
	}

	@Override
	protected String o_NameHighlightingPc (final AvailObject object)
	{
		return o_Traversed(object).nameHighlightingPc();
	}

	@Override
	protected boolean o_SetIntersects (final AvailObject object, final A_Set otherSet)
	{
		return o_Traversed(object).setIntersects(otherSet);
	}

	@Override
	protected void o_RemovePlanForDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		o_Traversed(object).removePlanForDefinition(definition);
	}

	@Override
	protected A_Map o_DefinitionParsingPlans (final AvailObject object)
	{
		return o_Traversed(object).definitionParsingPlans();
	}

	@Override
	protected boolean o_EqualsListNodeType (
		final AvailObject object,
		final A_Type listNodeType)
	{
		return o_Traversed(object).equalsListNodeType(listNodeType);
	}

	@Override
	protected A_Type o_SubexpressionsTupleType (final AvailObject object)
	{
		return o_Traversed(object).subexpressionsTupleType();
	}

	@Override
	protected A_Type o_TypeUnionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return o_Traversed(object).typeUnionOfListNodeType(aListNodeType);
	}

	@Override
	protected A_BasicObject o_LazyTypeFilterTreePojo (final AvailObject object)
	{
		return o_Traversed(object).lazyTypeFilterTreePojo();
	}

	@Override
	protected void o_AddPlanInProgress (
		final AvailObject object,
		final A_ParsingPlanInProgress planInProgress)
	{
		o_Traversed(object).addPlanInProgress(planInProgress);
	}

	@Override
	protected A_Type o_ParsingSignature (final AvailObject object)
	{
		return o_Traversed(object).parsingSignature();
	}

	@Override
	protected void o_RemovePlanInProgress (
		final AvailObject object, final A_ParsingPlanInProgress planInProgress)
	{
		o_Traversed(object).removePlanInProgress(planInProgress);
	}

	@Override
	protected A_Set o_ModuleSemanticRestrictions (final AvailObject object)
	{
		return o_Traversed(object).moduleSemanticRestrictions();
	}

	@Override
	protected A_Set o_ModuleGrammaticalRestrictions (final AvailObject object)
	{
		return o_Traversed(object).moduleGrammaticalRestrictions();
	}

	@Override
	public TypeTag o_ComputeTypeTag (final AvailObject object)
	{
		final TypeTag tag = o_Traversed(object).typeTag();
		// Now that we know it, switch to a descriptor that has it cached...
		object.setDescriptor(mutability == Mutability.MUTABLE
			? mutable(tag)
			: mutability == Mutability.IMMUTABLE
				? immutable(tag)
				: shared(tag));
		return tag;
	}

	@Override
	AvailObject o_FieldAt (
		final AvailObject object, final A_Atom field)
	{
		return o_Traversed(object).fieldAt(field);
	}

	@Override
	protected A_BasicObject o_FieldAtPuttingCanDestroy (
		final AvailObject object,
		final A_Atom field,
		final A_BasicObject value,
		final boolean canDestroy)
	{
		return o_Traversed(object).fieldAtPuttingCanDestroy(
			field, value, canDestroy);
	}

	@Override
	protected A_Type o_FieldTypeAt (
		final AvailObject object, final A_Atom field)
	{
		return o_Traversed(object).fieldTypeAt(field);
	}

	@Override
	protected A_DefinitionParsingPlan o_ParsingPlan (final AvailObject object)
	{
		return o_Traversed(object).parsingPlan();
	}

	@Override
	protected boolean o_CompareFromToWithIntTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anIntTuple,
		final int startIndex2)
	{
		return o_Traversed(object).compareFromToWithIntTupleStartingAt(
			startIndex1, endIndex1, anIntTuple, startIndex2);
	}

	@Override
	protected boolean o_IsIntTuple (final AvailObject object)
	{
		return o_Traversed(object).isIntTuple();
	}

	@Override
	protected boolean o_EqualsIntTuple (
		final AvailObject object, final A_Tuple anIntTuple)
	{
		return o_Traversed(object).equalsIntTuple(anIntTuple);
	}

	@Override
	protected void o_AtomicAddToMap (
		final AvailObject object,
		final A_BasicObject key,
		final A_BasicObject value)
	throws VariableGetException, VariableSetException
	{
		o_Traversed(object).atomicAddToMap(key, value);
	}

	@Override
	protected boolean o_VariableMapHasKey (
		final AvailObject object, final A_BasicObject key)
	throws VariableGetException
	{
		return o_Traversed(object).variableMapHasKey(key);
	}

	@Override
	protected A_Method o_LexerMethod (final AvailObject object)
	{
		return o_Traversed(object).lexerMethod();
	}

	@Override
	protected A_Function o_LexerFilterFunction (final AvailObject object)
	{
		return o_Traversed(object).lexerFilterFunction();
	}

	@Override
	protected A_Function o_LexerBodyFunction (final AvailObject object)
	{
		return o_Traversed(object).lexerBodyFunction();
	}

	@Override
	protected void o_SetLexer (
		final AvailObject object, final A_Lexer lexer)
	{
		o_Traversed(object).setLexer(lexer);
	}

	@Override
	protected void o_AddLexer (
		final AvailObject object, final A_Lexer lexer)
	{
		o_Traversed(object).addLexer(lexer);
	}

	@Override
	LexingState o_NextLexingState (
		final AvailObject object)
	{
		return o_Traversed(object).nextLexingState();
	}

	@Override
	protected void o_SetNextLexingStateFromPrior (
		final AvailObject object, final LexingState priorLexingState)
	{
		o_Traversed(object).setNextLexingStateFromPrior(priorLexingState);
	}

	@Override
	protected int o_TupleCodePointAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).tupleCodePointAt(index);
	}

	@Override @AvailMethod
	protected A_Phrase o_OriginatingPhrase (final AvailObject object)
	{
		return o_Traversed(object).originatingPhrase();
	}

	@Override
	protected boolean o_IsGlobal (final AvailObject object)
	{
		return o_Traversed(object).isGlobal();
	}

	@Override
	protected A_Module o_GlobalModule (final AvailObject object)
	{
		return o_Traversed(object).globalModule();
	}

	@Override
	protected A_String o_GlobalName (final AvailObject object)
	{
		return o_Traversed(object).globalName();
	}

	@Override
	LexicalScanner o_CreateLexicalScanner (final AvailObject object)
	{
		return o_Traversed(object).createLexicalScanner();
	}

	@Override
	protected A_Lexer o_Lexer (final AvailObject object)
	{
		return o_Traversed(object).lexer();
	}

	@Override
	protected void o_SuspendingFunction (
		final AvailObject object,
		final A_Function suspendingFunction)
	{
		o_Traversed(object).suspendingFunction(suspendingFunction);
	}

	@Override
	protected A_Function o_SuspendingFunction (final AvailObject object)
	{
		return o_Traversed(object).suspendingFunction();
	}

	@Override
	protected boolean o_IsBackwardJump (final AvailObject object)
	{
		return o_Traversed(object).isBackwardJump();
	}

	@Override
	protected A_BundleTree o_LatestBackwardJump (
		final AvailObject object)
	{
		return o_Traversed(object).latestBackwardJump();
	}

	@Override
	protected boolean o_HasBackwardJump (final AvailObject object)
	{
		return o_Traversed(object).hasBackwardJump();
	}

	@Override
	protected boolean o_IsSourceOfCycle (final AvailObject object)
	{
		return o_Traversed(object).isSourceOfCycle();
	}

	@Override
	protected void o_IsSourceOfCycle (
		final AvailObject object,
		final boolean isSourceOfCycle)
	{
		o_Traversed(object).isSourceOfCycle(isSourceOfCycle);
	}

	@Override
	protected StringBuilder o_DebugLog (final AvailObject object)
	{
		return o_Traversed(object).debugLog();
	}

	@Override
	protected int o_NumConstants (final AvailObject object)
	{
		return o_Traversed(object).numConstants();
	}

	@Override
	protected A_Type o_ConstantTypeAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).constantTypeAt(index);
	}

	@Override
	Statistic o_ReturnerCheckStat (final AvailObject object)
	{
		return o_Traversed(object).returnerCheckStat();
	}

	@Override
	Statistic o_ReturneeCheckStat (final AvailObject object)
	{
		return o_Traversed(object).returneeCheckStat();
	}

	@Override
	protected int o_NumNybbles (final AvailObject object)
	{
		return o_Traversed(object).numNybbles();
	}

	@Override
	protected A_Tuple o_LineNumberEncodedDeltas (final AvailObject object)
	{
		return o_Traversed(object).lineNumberEncodedDeltas();
	}

	@Override
	protected int o_CurrentLineNumber (final AvailObject object)
	{
		return o_Traversed(object).currentLineNumber();
	}

	@Override
	protected A_Type o_FiberResultType (final AvailObject object)
	{
		return o_Traversed(object).fiberResultType();
	}

	@Override
	protected LookupTree<A_Definition, A_Tuple> o_TestingTree (
		final AvailObject object)
	{
		return o_Traversed(object).testingTree();
	}

	@Override
	protected void o_ForEach (
		final AvailObject object,
		final BiConsumer<? super AvailObject, ? super AvailObject> action)
	{
		o_Traversed(object).forEach(action);
	}

	@Override
	protected void o_ForEachInMapBin (
		final AvailObject object,
		final BiConsumer<? super AvailObject, ? super AvailObject> action)
	{
		o_Traversed(object).forEachInMapBin(action);
	}

	@Override
	protected void o_SetSuccessAndFailureContinuations (
		final AvailObject object,
		final Continuation1NotNull<AvailObject> onSuccess,
		final Continuation1NotNull<Throwable> onFailure)
	{
		o_Traversed(object).setSuccessAndFailureContinuations(
			onSuccess, onFailure);
	}

	@Override
	protected void o_ClearLexingState (final AvailObject object)
	{
		o_Traversed(object).clearLexingState();
	}

	@Override
	protected A_Phrase o_LastExpression (final AvailObject object)
	{
		return o_Traversed(object).lastExpression();
	}

	@Override
	AvailObject o_RegisterDump (final AvailObject object)
	{
		return o_Traversed(object).registerDump();
	}
}
