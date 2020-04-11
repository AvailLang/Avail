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
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AbstractSlotsEnum;
import com.avail.descriptor.representation.AvailObject;
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
import java.util.function.BinaryOperator;
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
	public void o_ScanSubobjects (
		final AvailObject object,
		final AvailSubobjectVisitor visitor)
	{
		visitor.invoke(object.slot(INDIRECTION_TARGET));
	}

	@Override
	public AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			object.setDescriptor(immutable(typeTag));
			return object.slot(INDIRECTION_TARGET).makeImmutable();
		}
		return object.slot(INDIRECTION_TARGET);
	}

	@Override
	public AvailObject o_MakeShared (final AvailObject object)
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
	public AvailObject o_Traversed (final AvailObject object)
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
	 * @param typeTag
	 *        The {@link TypeTag} that's in use at the target of this
	 *        indirection.  Note that this will never change, even if the target
	 *        is mutable.
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

	/**
	 * Answer a shared {@code IndirectionDescriptor} suitable for pointing to
	 * an object having the given {@link TypeTag}.
	 *
	 * @param typeTag
	 *        The target's {@link TypeTag}.
	 * @return An {@code IndirectionDescriptor}.
	 */
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
	public boolean o_AcceptsArgTypesFromFunctionType (
		final AvailObject object,
		final A_Type functionType)
	{
		return o_Traversed(object).acceptsArgTypesFromFunctionType(
			functionType);
	}

	@Override
	public boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		return o_Traversed(object).acceptsListOfArgTypes(argTypes);
	}

	@Override
	public boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<? extends A_BasicObject> argValues)
	{
		return o_Traversed(object).acceptsListOfArgValues(argValues);
	}

	@Override
	public boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final A_Tuple argTypes)
	{
		return o_Traversed(object).acceptsTupleOfArgTypes(argTypes);
	}

	@Override
	public boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final A_Tuple arguments)
	{
		return o_Traversed(object).acceptsTupleOfArguments(arguments);
	}

	@Override
	public void o_AddDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		o_Traversed(object).addDependentChunk(chunk);
	}

	@Override
	public void o_MethodAddDefinition (
		final AvailObject object,
		final A_Definition definition)
	throws SignatureException
	{
		o_Traversed(object).methodAddDefinition(definition);
	}

	@Override
	public void o_AddGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		o_Traversed(object).addGrammaticalRestriction(grammaticalRestriction);
	}

	@Override
	public A_Number o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	public A_Number o_AddToIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object)
				.addToIntegerCanDestroy(anInteger, canDestroy);
	}

	@Override
	public void o_ModuleAddGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		o_Traversed(object).moduleAddGrammaticalRestriction(
			grammaticalRestriction);
	}

	@Override
	public void o_ModuleAddDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		o_Traversed(object).moduleAddDefinition(definition);
	}

	@Override
	public void o_AddDefinitionParsingPlan (
		final AvailObject object,
		final A_DefinitionParsingPlan plan)
	{
		o_Traversed(object).addDefinitionParsingPlan(plan);
	}

	@Override
	public void o_AddImportedName (
		final AvailObject object,
		final A_Atom trueName)
	{
		o_Traversed(object).addImportedName(trueName);
	}

	@Override
	public void o_AddImportedNames (
		final AvailObject object,
		final A_Set trueNames)
	{
		o_Traversed(object).addImportedNames(trueNames);
	}

	@Override
	public void o_IntroduceNewName (
		final AvailObject object,
		final A_Atom trueName)
	{
		o_Traversed(object).introduceNewName(trueName);
	}

	@Override
	public void o_AddPrivateName (
		final AvailObject object,
		final A_Atom trueName)
	{
		o_Traversed(object).addPrivateName(trueName);
	}

	@Override
	public void o_AddPrivateNames (
		final AvailObject object,
		final A_Set trueNames)
	{
		o_Traversed(object).addPrivateNames(trueNames);
	}

	@Override
	public A_BasicObject o_SetBinAddingElementHashLevelCanDestroy (
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
	public AvailObject o_BinElementAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).binElementAt(index);
	}

	@Override
	public boolean o_BinHasElementWithHash (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash)
	{
		return o_Traversed(object).binHasElementWithHash(
			elementObject,
			elementObjectHash);
	}

	@Override
	public AvailObject o_BinRemoveElementHashLevelCanDestroy (
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
	public void o_BreakpointBlock (
		final AvailObject object,
		final AvailObject value)
	{
		o_Traversed(object).breakpointBlock(value);
	}

	@Override
	public A_BundleTree o_BuildFilteredBundleTree (
		final AvailObject object)
	{
		return o_Traversed(object).buildFilteredBundleTree();
	}

	@Override
	public boolean o_CompareFromToWithStartingAt (
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
	public boolean o_CompareFromToWithAnyTupleStartingAt (
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
	public boolean o_CompareFromToWithByteStringStartingAt (
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
	public boolean o_CompareFromToWithByteTupleStartingAt (
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
	public boolean o_CompareFromToWithIntegerIntervalTupleStartingAt (
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
	public boolean o_CompareFromToWithSmallIntegerIntervalTupleStartingAt (
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
	public boolean o_CompareFromToWithRepeatedElementTupleStartingAt (
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
	public boolean o_CompareFromToWithNybbleTupleStartingAt (
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
	public boolean o_CompareFromToWithObjectTupleStartingAt (
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
	public boolean o_CompareFromToWithTwoByteStringStartingAt (
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
	public int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		return o_Traversed(object).computeHashFromTo(start, end);
	}

	@Override
	public A_Tuple o_ConcatenateTuplesCanDestroy (
		final AvailObject object,
		final boolean canDestroy)
	{
		return o_Traversed(object).concatenateTuplesCanDestroy(canDestroy);
	}

	@Override
	public void o_Continuation (
		final AvailObject object,
		final A_Continuation value)
	{
		o_Traversed(object).continuation(value);
	}

	@Override
	public A_Tuple o_CopyTupleFromToCanDestroy (
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
	public boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<TypeRestriction> argRestrictions)
	{
		return o_Traversed(object).couldEverBeInvokedWith(argRestrictions);
	}

	@Override
	public A_Number o_DivideCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideCanDestroy(aNumber, canDestroy);
	}

	@Override
	public A_Number o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	public A_Number o_DivideIntoIntegerCanDestroy (
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
	public boolean o_EqualsAnyTuple (
		final AvailObject object,
		final A_Tuple anotherTuple)
	{
		return o_Traversed(object).equalsAnyTuple(anotherTuple);
	}

	@Override
	public boolean o_EqualsByteString (
		final AvailObject object,
		final A_String aByteString)
	{
		return o_Traversed(object).equalsByteString(aByteString);
	}

	@Override
	public boolean o_EqualsByteTuple (
		final AvailObject object,
		final A_Tuple aByteTuple)
	{
		return o_Traversed(object).equalsByteTuple(aByteTuple);
	}

	@Override
	public boolean o_EqualsCharacterWithCodePoint (
		final AvailObject object,
		final int otherCodePoint)
	{
		return o_Traversed(object).equalsCharacterWithCodePoint(otherCodePoint);
	}

	@Override
	public boolean o_EqualsFiberType (final AvailObject object, final A_Type aType)
	{
		return o_Traversed(object).equalsFiberType(aType);
	}

	@Override
	public boolean o_EqualsFunction (
		final AvailObject object,
		final A_Function aFunction)
	{
		return o_Traversed(object).equalsFunction(aFunction);
	}

	@Override
	public boolean o_EqualsFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return o_Traversed(object).equalsFunctionType(aFunctionType);
	}

	@Override
	public boolean o_EqualsIntegerIntervalTuple (
		final AvailObject object,
		final A_Tuple anIntegerIntervalTuple)
	{
		return o_Traversed(object).equalsIntegerIntervalTuple(
			anIntegerIntervalTuple);
	}

	@Override
	public boolean o_EqualsSmallIntegerIntervalTuple (
		final AvailObject object,
		final A_Tuple aSmallIntegerIntervalTuple)
	{
		return o_Traversed(object).equalsSmallIntegerIntervalTuple(
			aSmallIntegerIntervalTuple);
	}

	@Override
	public boolean o_EqualsRepeatedElementTuple (
		final AvailObject object,
		final A_Tuple aRepeatedElementTuple)
	{
		return o_Traversed(object).equalsRepeatedElementTuple(
			aRepeatedElementTuple);
	}

	@Override
	public boolean o_EqualsCompiledCode (
		final AvailObject object,
		final A_RawFunction aCompiledCode)
	{
		return o_Traversed(object).equalsCompiledCode(aCompiledCode);
	}

	@Override
	public boolean o_EqualsVariable (
		final AvailObject object,
		final AvailObject aVariable)
	{
		return o_Traversed(object).equalsVariable(aVariable);
	}

	@Override
	public boolean o_EqualsVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		return o_Traversed(object).equalsVariableType(aVariableType);
	}

	@Override
	public boolean o_EqualsContinuation (
		final AvailObject object,
		final A_Continuation aContinuation)
	{
		return o_Traversed(object).equalsContinuation(aContinuation);
	}

	@Override
	public boolean o_EqualsContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return o_Traversed(object).equalsContinuationType(aContinuationType);
	}

	@Override
	public boolean o_EqualsCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return o_Traversed(object).equalsCompiledCodeType(aCompiledCodeType);
	}

	@Override
	public boolean o_EqualsDouble (
		final AvailObject object,
		final double aDouble)
	{
		return o_Traversed(object).equalsDouble(aDouble);
	}

	@Override
	public boolean o_EqualsFloat (
		final AvailObject object,
		final float aFloat)
	{
		return o_Traversed(object).equalsFloat(aFloat);
	}

	@Override
	public boolean o_EqualsInfinity (
		final AvailObject object,
		final Sign sign)
	{
		return o_Traversed(object).equalsInfinity(sign);
	}

	@Override
	public boolean o_EqualsInteger (
		final AvailObject object,
		final AvailObject anAvailInteger)
	{
		return o_Traversed(object).equalsInteger(anAvailInteger);
	}

	@Override
	public boolean o_EqualsIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return o_Traversed(object).equalsIntegerRangeType(anIntegerRangeType);
	}

	@Override
	public boolean o_EqualsMap (
		final AvailObject object,
		final A_Map aMap)
	{
		return o_Traversed(object).equalsMap(aMap);
	}

	@Override
	public boolean o_EqualsMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		return o_Traversed(object).equalsMapType(aMapType);
	}

	@Override
	public boolean o_EqualsNybbleTuple (
		final AvailObject object,
		final A_Tuple aNybbleTuple)
	{
		return o_Traversed(object).equalsNybbleTuple(aNybbleTuple);
	}

	@Override
	public boolean o_EqualsObject (
		final AvailObject object,
		final AvailObject anObject)
	{
		return o_Traversed(object).equalsObject(anObject);
	}

	@Override
	public boolean o_EqualsObjectTuple (
		final AvailObject object,
		final A_Tuple anObjectTuple)
	{
		return o_Traversed(object).equalsObjectTuple(anObjectTuple);
	}

	@Override
	public boolean o_EqualsPojo (
		final AvailObject object,
		final AvailObject aRawPojo)
	{
		return o_Traversed(object).equalsPojo(aRawPojo);
	}

	@Override
	public boolean o_EqualsPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		return o_Traversed(object).equalsPojoType(aPojoType);
	}

	@Override
	public boolean o_EqualsPrimitiveType (
		final AvailObject object,
		final A_Type aPrimitiveType)
	{
		return o_Traversed(object).equalsPrimitiveType(aPrimitiveType);
	}

	@Override
	public boolean o_EqualsRawPojoFor (
		final AvailObject object,
		final AvailObject otherRawPojo,
		final @Nullable Object otherJavaObject)
	{
		return o_Traversed(object).equalsRawPojoFor(
			otherRawPojo,
			otherJavaObject);
	}

	@Override
	public boolean o_EqualsReverseTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		return o_Traversed(object).equalsReverseTuple(aTuple);
	}

	@Override
	public boolean o_EqualsSet (
		final AvailObject object,
		final A_Set aSet)
	{
		return o_Traversed(object).equalsSet(aSet);
	}

	@Override
	public boolean o_EqualsSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		return o_Traversed(object).equalsSetType(aSetType);
	}

	@Override
	public boolean o_EqualsTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return o_Traversed(object).equalsTupleType(aTupleType);
	}

	@Override
	public boolean o_EqualsTwoByteString (
		final AvailObject object,
		final A_String aTwoByteString)
	{
		return o_Traversed(object).equalsTwoByteString(aTwoByteString);
	}

	@Override
	public void o_ExecutionState (
		final AvailObject object,
		final ExecutionState value)
	{
		o_Traversed(object).executionState(value);
	}

	@Override
	public byte o_ExtractNybbleFromTupleAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).extractNybbleFromTupleAt(index);
	}

	@Override
	public List<A_Definition> o_FilterByTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		return o_Traversed(object).filterByTypes(argTypes);
	}

	@Override
	public Order o_NumericCompareToInteger (
		final AvailObject object,
		final AvailObject anInteger)
	{
		return o_Traversed(object).numericCompareToInteger(anInteger);
	}

	@Override
	public Order o_NumericCompareToInfinity (
		final AvailObject object,
		final Sign sign)
	{
		return o_Traversed(object).numericCompareToInfinity(sign);
	}

	@Override
	public boolean o_HasElement (
		final AvailObject object,
		final A_BasicObject elementObject)
	{
		return o_Traversed(object).hasElement(elementObject);
	}

	@Override
	public int o_HashFromTo (
		final AvailObject object,
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
		final AvailObject object,
		final A_BasicObject keyObject)
	{
		return o_Traversed(object).hasKey(keyObject);
	}

	@Override
	public boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return o_Traversed(object).hasObjectInstance(potentialInstance);
	}

	@Override
	public List<A_Definition> o_DefinitionsAtOrBelow (
		final AvailObject object,
		final List<TypeRestriction> argRestrictions)
	{
		return o_Traversed(object).definitionsAtOrBelow(argRestrictions);
	}

	@Override
	public boolean o_IncludesDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		return o_Traversed(object).includesDefinition(definition);
	}

	@Override
	public void o_SetInterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		o_Traversed(object).setInterruptRequestFlag(flag);
	}

	@Override
	public void o_CountdownToReoptimize (final AvailObject object, final int value)
	{
		o_Traversed(object).countdownToReoptimize(value);
	}

	@Override
	public boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final A_BasicObject anotherObject)
	{
		return o_Traversed(object).isBetterRepresentationThan(anotherObject);
	}

	@Override
	public int o_RepresentationCostOfTupleType (
		final AvailObject object)
	{
		return o_Traversed(object).representationCostOfTupleType();
	}

	@Override
	public boolean o_IsBinSubsetOf (
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
	public boolean o_IsSubsetOf (
		final AvailObject object,
		final A_Set another)
	{
		return o_Traversed(object).isSubsetOf(another);
	}

	@Override
	public boolean o_IsSubtypeOf (
		final AvailObject object,
		final A_Type aType)
	{
		return o_Traversed(object).isSubtypeOf(aType);
	}

	@Override
	public boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		return o_Traversed(object).isSupertypeOfVariableType(aVariableType);
	}

	@Override
	public boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return o_Traversed(object).isSupertypeOfContinuationType(
			aContinuationType);
	}

	@Override
	public boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return o_Traversed(object).isSupertypeOfCompiledCodeType(
			aCompiledCodeType);
	}

	@Override
	public boolean o_IsSupertypeOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return o_Traversed(object).isSupertypeOfFiberType(aFiberType);
	}

	@Override
	public boolean o_IsSupertypeOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return o_Traversed(object).isSupertypeOfFunctionType(aFunctionType);
	}

	@Override
	public boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return o_Traversed(object).isSupertypeOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	public boolean o_IsSupertypeOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return o_Traversed(object).isSupertypeOfListNodeType(aListNodeType);
	}

	@Override
	public boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		return o_Traversed(object).isSupertypeOfMapType(aMapType);
	}

	@Override
	public boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return o_Traversed(object).isSupertypeOfObjectType(anObjectType);
	}

	@Override
	public boolean o_IsSupertypeOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		return o_Traversed(object).isSupertypeOfPhraseType(aPhraseType);
	}

	@Override
	public boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return o_Traversed(object).isSupertypeOfPojoType(aPojoType);
	}

	@Override
	public boolean o_IsSupertypeOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return o_Traversed(object).isSupertypeOfPrimitiveTypeEnum(
			primitiveTypeEnum);
	}

	@Override
	public boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		return o_Traversed(object).isSupertypeOfSetType(aSetType);
	}

	@Override
	public boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		return o_Traversed(object).isSupertypeOfTupleType(aTupleType);
	}

	@Override
	public boolean o_IsSupertypeOfEnumerationType (
		final AvailObject object,
		final A_BasicObject anEnumerationType)
	{
		return o_Traversed(object).isSupertypeOfEnumerationType(
			anEnumerationType);
	}

	@Override
	public IteratorNotNull<AvailObject> o_Iterator (final AvailObject object)
	{
		return o_Traversed(object).iterator();
	}

	@Override
	public Spliterator<AvailObject> o_Spliterator (final AvailObject object)
	{
		return o_Traversed(object).spliterator();
	}

	@Override
	public Stream<AvailObject> o_Stream (final AvailObject object)
	{
		return o_Traversed(object).stream();
	}

	@Override
	public Stream<AvailObject> o_ParallelStream (final AvailObject object)
	{
		return o_Traversed(object).parallelStream();
	}

	@Override
	public void o_LevelTwoChunkOffset (
		final AvailObject object,
		final L2Chunk chunk,
		final int offset)
	{
		o_Traversed(object).levelTwoChunkOffset(chunk, offset);
	}

	@Override
	public AvailObject o_LiteralAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).literalAt(index);
	}

	@Override
	public AvailObject o_FrameAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).frameAt(index);
	}

	@Override
	public AvailObject o_FrameAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		return o_Traversed(object).frameAtPut(index, value);
	}

	@Override
	public A_Type o_LocalTypeAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).localTypeAt(index);
	}

	@Override
	public A_Definition o_LookupByTypesFromTuple (
			final AvailObject object,
			final A_Tuple argumentTypeTuple)
		throws MethodDefinitionException
	{
		return o_Traversed(object).lookupByTypesFromTuple(argumentTypeTuple);
	}

	@Override
	public A_Definition o_LookupByValuesFromList (
		final AvailObject object,
		final List<? extends A_BasicObject> argumentList)
	throws MethodDefinitionException
	{
		return o_Traversed(object).lookupByValuesFromList(argumentList);
	}

	@Override
	public AvailObject o_MapAt (
		final AvailObject object,
		final A_BasicObject keyObject)
	{
		return o_Traversed(object).mapAt(keyObject);
	}

	@Override
	public A_Map o_MapAtPuttingCanDestroy (
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
	public A_Map o_MapAtReplacingCanDestroy (
		final AvailObject object,
		final A_BasicObject key,
		final A_BasicObject notFoundValue,
		final BinaryOperator<A_BasicObject> transformer,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapAtReplacingCanDestroy(
			key, notFoundValue, transformer, canDestroy);
	}

	@Override
	public A_Map o_MapWithoutKeyCanDestroy (
		final AvailObject object,
		final A_BasicObject keyObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapWithoutKeyCanDestroy(
			keyObject,
			canDestroy);
	}

	@Override
	public A_Number o_MinusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).minusCanDestroy(aNumber, canDestroy);
	}

	@Override
	public A_Number o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	public A_Number o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	public boolean o_NameVisible (
		final AvailObject object,
		final A_Atom trueName)
	{
		return o_Traversed(object).nameVisible(trueName);
	}

	@Override
	public boolean o_OptionallyNilOuterVar (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).optionallyNilOuterVar(index);
	}

	@Override
	public A_Type o_OuterTypeAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).outerTypeAt(index);
	}

	@Override
	public AvailObject o_OuterVarAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).outerVarAt(index);
	}

	@Override
	public void o_OuterVarAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		o_Traversed(object).outerVarAtPut(index, value);
	}

	@Override
	public A_Number o_PlusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).plusCanDestroy(aNumber, canDestroy);
	}

	@Override
	public void o_Priority (
		final AvailObject object,
		final int value)
	{
		o_Traversed(object).priority(value);
	}

	@Override
	public void o_FiberGlobals (
		final AvailObject object,
		final A_Map value)
	{
		o_Traversed(object).fiberGlobals(value);
	}

	@Override
	public short o_RawByteForCharacterAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawByteForCharacterAt(index);
	}

	@Override
	public int o_RawShortForCharacterAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawShortForCharacterAt(index);
	}

	@Override
	public void o_RawShortForCharacterAtPut (
		final AvailObject object,
		final int index,
		final int anInteger)
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
		final AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).rawSignedIntegerAtPut(index, value);
	}

	@Override
	public long o_RawUnsignedIntegerAt (
		final AvailObject object,
		final int index)
	{
		return o_Traversed(object).rawUnsignedIntegerAt(index);
	}

	@Override
	public void o_RawUnsignedIntegerAtPut (
		final AvailObject object,
		final int index,
		final int value)
	{
		o_Traversed(object).rawUnsignedIntegerAtPut(index, value);
	}

	@Override
	public void o_RemoveDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		o_Traversed(object).removeDependentChunk(chunk);
	}

	@Override
	public void o_RemoveFrom (
		final AvailObject object,
		final AvailLoader loader,
		final Continuation0 afterRemoval)
	{
		o_Traversed(object).removeFrom(loader, afterRemoval);
	}

	@Override
	public void o_RemoveDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		o_Traversed(object).removeDefinition(definition);
	}

	@Override
	public void o_RemoveGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction obsoleteRestriction)
	{
		o_Traversed(object).removeGrammaticalRestriction(obsoleteRestriction);
	}

	@Override
	public void o_ResolveForward (
		final AvailObject object,
		final A_BasicObject forwardDefinition)
	{
		o_Traversed(object).resolveForward(
			forwardDefinition);
	}

	@Override
	public A_Set o_SetIntersectionCanDestroy (
		final AvailObject object,
		final A_Set otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setIntersectionCanDestroy(
			otherSet,
			canDestroy);
	}

	@Override
	public A_Set o_SetMinusCanDestroy (
		final AvailObject object,
		final A_Set otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setMinusCanDestroy(otherSet, canDestroy);
	}

	@Override
	public A_Set o_SetUnionCanDestroy (
		final AvailObject object,
		final A_Set otherSet,
		final boolean canDestroy)
	{
		return o_Traversed(object).setUnionCanDestroy(otherSet, canDestroy);
	}

	@Override
	public void o_SetValue (
			final AvailObject object,
			final A_BasicObject newValue)
		throws VariableSetException
	{
		o_Traversed(object).setValue(newValue);
	}

	@Override
	public void o_SetValueNoCheck (
		final AvailObject object,
		final A_BasicObject newValue)
	{
		o_Traversed(object).setValueNoCheck(newValue);
	}

	@Override
	public A_Set o_SetWithElementCanDestroy (
		final AvailObject object,
		final A_BasicObject newElementObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).setWithElementCanDestroy(
			newElementObject,
			canDestroy);
	}

	@Override
	public A_Set o_SetWithoutElementCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObjectToExclude,
		final boolean canDestroy)
	{
		return o_Traversed(object).setWithoutElementCanDestroy(
			elementObjectToExclude,
			canDestroy);
	}

	@Override
	public AvailObject o_StackAt (final AvailObject object, final int slotIndex)
	{
		return o_Traversed(object).stackAt(slotIndex);
	}

	@Override
	public void o_SetStartingChunkAndReoptimizationCountdown (
		final AvailObject object,
		final L2Chunk chunk,
		final long countdown)
	{
		o_Traversed(object).setStartingChunkAndReoptimizationCountdown(
			chunk, countdown);
	}

	@Override
	public A_Number o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromInfinityCanDestroy(
			sign,
			canDestroy);
	}

	@Override
	public A_Number o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromIntegerCanDestroy(
			anInteger,
			canDestroy);
	}

	@Override
	public A_Number o_TimesCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return o_Traversed(object).timesCanDestroy(aNumber, canDestroy);
	}

	@Override
	public A_Set o_TrueNamesForStringName (
		final AvailObject object,
		final A_String stringName)
	{
		return o_Traversed(object).trueNamesForStringName(stringName);
	}

	@Override
	public AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).tupleAt(index);
	}

	@Override
	public A_Tuple o_TupleAtPuttingCanDestroy (
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
	public int o_TupleIntAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).tupleIntAt(index);
	}

	@Override
	public A_Type o_TypeAtIndex (final AvailObject object, final int index)
	{
		return o_Traversed(object).typeAtIndex(index);
	}

	@Override
	public A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		return o_Traversed(object).typeIntersection(another);
	}

	@Override
	public A_Type o_TypeIntersectionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return o_Traversed(object).typeIntersectionOfCompiledCodeType(
			aCompiledCodeType);
	}

	@Override
	public A_Type o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return o_Traversed(object).typeIntersectionOfContinuationType(
			aContinuationType);
	}

	@Override
	public A_Type o_TypeIntersectionOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return o_Traversed(object).typeIntersectionOfFiberType(
			aFiberType);
	}

	@Override
	public A_Type o_TypeIntersectionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return o_Traversed(object).typeIntersectionOfFunctionType(
			aFunctionType);
	}

	@Override
	public A_Type o_TypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return o_Traversed(object).typeIntersectionOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	public A_Type o_TypeIntersectionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return o_Traversed(object).typeIntersectionOfListNodeType(
			aListNodeType);
	}

	@Override
	public A_Type o_TypeIntersectionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		return o_Traversed(object).typeIntersectionOfMapType(aMapType);
	}

	@Override
	public A_Type o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return o_Traversed(object).typeIntersectionOfObjectType(anObjectType);
	}

	@Override
	public A_Type o_TypeIntersectionOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		return o_Traversed(object).typeIntersectionOfPhraseType(
			aPhraseType);
	}

	@Override
	public A_Type o_TypeIntersectionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return o_Traversed(object).typeIntersectionOfPojoType(aPojoType);
	}

	@Override
	public A_Type o_TypeIntersectionOfSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		return o_Traversed(object).typeIntersectionOfSetType(aSetType);
	}

	@Override
	public A_Type o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return o_Traversed(object).typeIntersectionOfTupleType(aTupleType);
	}

	@Override
	public A_Type o_TypeIntersectionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		return o_Traversed(object).typeIntersectionOfVariableType(
			aVariableType);
	}

	@Override
	public A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		return o_Traversed(object).typeUnion(another);
	}

	@Override
	public A_Type o_TypeUnionOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return o_Traversed(object).typeUnionOfFiberType(aFiberType);
	}

	@Override
	public A_Type o_TypeUnionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return o_Traversed(object).typeUnionOfFunctionType(aFunctionType);
	}

	@Override
	public A_Type o_TypeUnionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		return o_Traversed(object).typeUnionOfVariableType(aVariableType);
	}

	@Override
	public A_Type o_TypeUnionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return o_Traversed(object).typeUnionOfContinuationType(
			aContinuationType);
	}

	@Override
	public A_Type o_TypeUnionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return o_Traversed(object).typeUnionOfCompiledCodeType(
			aCompiledCodeType);
	}

	@Override
	public A_Type o_TypeUnionOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return o_Traversed(object).typeUnionOfIntegerRangeType(
			anIntegerRangeType);
	}

	@Override
	public A_Type o_TypeUnionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		return o_Traversed(object).typeUnionOfMapType(aMapType);
	}

	@Override
	public A_Type o_TypeUnionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return o_Traversed(object).typeUnionOfObjectType(anObjectType);
	}

	@Override
	public A_Type o_TypeUnionOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		return o_Traversed(object).typeUnionOfPhraseType(
			aPhraseType);
	}

	@Override
	public A_Type o_TypeUnionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return o_Traversed(object).typeUnionOfPojoType(aPojoType);
	}

	@Override
	public A_Type o_TypeUnionOfSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		return o_Traversed(object).typeUnionOfSetType(aSetType);
	}

	@Override
	public A_Type o_TypeUnionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return o_Traversed(object).typeUnionOfTupleType(aTupleType);
	}

	@Override
	public A_Type o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		return o_Traversed(object).unionOfTypesAtThrough(startIndex, endIndex);
	}

	@Override
	public void o_Value (
		final AvailObject object,
		final A_BasicObject value)
	{
		o_Traversed(object).value(value);
	}

	@Override
	public String o_AsNativeString (final AvailObject object)
	{
		return o_Traversed(object).asNativeString();
	}

	@Override
	public A_Set o_AsSet (final AvailObject object)
	{
		return o_Traversed(object).asSet();
	}

	@Override
	public A_Tuple o_AsTuple (final AvailObject object)
	{
		return o_Traversed(object).asTuple();
	}

	@Override
	public int o_BitsPerEntry (final AvailObject object)
	{
		return o_Traversed(object).bitsPerEntry();
	}

	@Override
	public A_Function o_BodyBlock (final AvailObject object)
	{
		return o_Traversed(object).bodyBlock();
	}

	@Override
	public A_Type o_BodySignature (final AvailObject object)
	{
		return o_Traversed(object).bodySignature();
	}

	@Override
	public A_BasicObject o_BreakpointBlock (final AvailObject object)
	{
		return o_Traversed(object).breakpointBlock();
	}

	@Override
	public A_Continuation o_Caller (final AvailObject object)
	{
		return o_Traversed(object).caller();
	}

	@Override
	public void o_ClearValue (final AvailObject object)
	{
		o_Traversed(object).clearValue();
	}

	@Override
	public A_Function o_Function (final AvailObject object)
	{
		return o_Traversed(object).function();
	}

	@Override
	public A_Type o_FunctionType (final AvailObject object)
	{
		return o_Traversed(object).functionType();
	}

	@Override
	public A_RawFunction o_Code (final AvailObject object)
	{
		return o_Traversed(object).code();
	}

	@Override
	public int o_CodePoint (final AvailObject object)
	{
		return o_Traversed(object).codePoint();
	}

	@Override
	public A_Set o_LazyComplete (final AvailObject object)
	{
		return o_Traversed(object).lazyComplete();
	}

	@Override
	public A_Map o_ConstantBindings (final AvailObject object)
	{
		return o_Traversed(object).constantBindings();
	}

	@Override
	public A_Type o_ContentType (final AvailObject object)
	{
		return o_Traversed(object).contentType();
	}

	@Override
	public A_Continuation o_Continuation (final AvailObject object)
	{
		return o_Traversed(object).continuation();
	}

	@Override
	public A_Tuple o_CopyAsMutableIntTuple (final AvailObject object)
	{
		return o_Traversed(object).copyAsMutableIntTuple();
	}

	@Override
	public A_Tuple o_CopyAsMutableObjectTuple (final AvailObject object)
	{
		return o_Traversed(object).copyAsMutableObjectTuple();
	}

	@Override
	public A_Type o_DefaultType (final AvailObject object)
	{
		return o_Traversed(object).defaultType();
	}

	@Override
	public A_Continuation o_EnsureMutable (final AvailObject object)
	{
		return o_Traversed(object).ensureMutable();
	}

	@Override
	public ExecutionState o_ExecutionState (final AvailObject object)
	{
		return o_Traversed(object).executionState();
	}

	@Override
	public void o_Expand (
		final AvailObject object,
		final A_Module module)
	{
		o_Traversed(object).expand(module);
	}

	@Override
	public boolean o_ExtractBoolean (final AvailObject object)
	{
		return A_Atom.Companion.extractBoolean(o_Traversed(object));
	}

	@Override
	public short o_ExtractUnsignedByte (final AvailObject object)
	{
		return o_Traversed(object).extractUnsignedByte();
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
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	public long o_ExtractLong (final AvailObject object)
	{
		return o_Traversed(object).extractLong();
	}

	@Override
	public byte o_ExtractNybble (final AvailObject object)
	{
		return o_Traversed(object).extractNybble();
	}

	@Override
	public A_Map o_FieldMap (final AvailObject object)
	{
		return o_Traversed(object).fieldMap();
	}

	@Override
	public A_Map o_FieldTypeMap (final AvailObject object)
	{
		return o_Traversed(object).fieldTypeMap();
	}

	@Override
	public AvailObject o_GetValue (final AvailObject object)
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
	public int o_HashOrZero (final AvailObject object)
	{
		return o_Traversed(object).hashOrZero();
	}

	@Override
	public boolean o_HasGrammaticalRestrictions (final AvailObject object)
	{
		return o_Traversed(object).hasGrammaticalRestrictions();
	}

	@Override
	public A_Tuple o_DefinitionsTuple (final AvailObject object)
	{
		return o_Traversed(object).definitionsTuple();
	}

	@Override
	public A_Map o_LazyIncomplete (final AvailObject object)
	{
		return o_Traversed(object).lazyIncomplete();
	}

	@Override
	public void o_DecrementCountdownToReoptimize (
		final AvailObject object,
		final Continuation1NotNull<Boolean> continuation)
	{
		o_Traversed(object).decrementCountdownToReoptimize(continuation);
	}

	@Override
	public boolean o_IsAbstractDefinition (final AvailObject object)
	{
		return o_Traversed(object).isAbstractDefinition();
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
	public boolean o_IsUnsignedByte (final AvailObject object)
	{
		return o_Traversed(object).isUnsignedByte();
	}

	/**
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	public boolean o_IsByteTuple (final AvailObject object)
	{
		return o_Traversed(object).isByteTuple();
	}

	@Override
	public boolean o_IsCharacter (final AvailObject object)
	{
		return o_Traversed(object).isCharacter();
	}

	@Override
	public boolean o_IsFunction (final AvailObject object)
	{
		return o_Traversed(object).isFunction();
	}

	@Override
	public boolean o_IsAtom (final AvailObject object)
	{
		return o_Traversed(object).isAtom();
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
	public boolean o_IsForwardDefinition (final AvailObject object)
	{
		return o_Traversed(object).isForwardDefinition();
	}

	@Override
	public boolean o_IsInstanceMeta (final AvailObject object)
	{
		return o_Traversed(object).isInstanceMeta();
	}

	@Override
	public boolean o_IsMethodDefinition (final AvailObject object)
	{
		return o_Traversed(object).isMethodDefinition();
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
	public boolean o_IsSet (final AvailObject object)
	{
		return o_Traversed(object).isSet();
	}

	@Override
	public boolean o_IsSetType (final AvailObject object)
	{
		return o_Traversed(object).isSetType();
	}

	/**
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	public boolean o_IsString (final AvailObject object)
	{
		return o_Traversed(object).isString();
	}

	@Override
	public boolean o_IsSupertypeOfBottom (final AvailObject object)
	{
		return o_Traversed(object).isSupertypeOfBottom();
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
	public A_Set o_KeysAsSet (final AvailObject object)
	{
		return o_Traversed(object).keysAsSet();
	}

	@Override
	public A_Type o_KeyType (final AvailObject object)
	{
		return o_Traversed(object).keyType();
	}

	@Override
	public L2Chunk o_LevelTwoChunk (final AvailObject object)
	{
		return o_Traversed(object).levelTwoChunk();
	}

	@Override
	public int o_LevelTwoOffset (final AvailObject object)
	{
		return o_Traversed(object).levelTwoOffset();
	}

	@Override
	public AvailObject o_Literal (final AvailObject object)
	{
		return o_Traversed(object).literal();
	}

	@Override
	public A_Number o_LowerBound (final AvailObject object)
	{
		return o_Traversed(object).lowerBound();
	}

	@Override
	public boolean o_LowerInclusive (final AvailObject object)
	{
		return o_Traversed(object).lowerInclusive();
	}

	@Override
	public AvailObject o_MakeSubobjectsImmutable (final AvailObject object)
	{
		return o_Traversed(object).makeSubobjectsImmutable();
	}

	@Override
	public void o_MakeSubobjectsShared (final AvailObject object)
	{
		o_Traversed(object).makeSubobjectsShared();
	}

	@Override
	public int o_MapSize (final AvailObject object)
	{
		return o_Traversed(object).mapSize();
	}

	@Override
	public int o_MaxStackDepth (final AvailObject object)
	{
		return o_Traversed(object).maxStackDepth();
	}

	@Override
	public A_Atom o_Message (final AvailObject object)
	{
		return o_Traversed(object).message();
	}

	@Override
	public A_Tuple o_MessageParts (final AvailObject object)
	{
		return o_Traversed(object).messageParts();
	}

	@Override
	public A_Set o_MethodDefinitions (final AvailObject object)
	{
		return o_Traversed(object).methodDefinitions();
	}

	@Override
	public A_Map o_ImportedNames (final AvailObject object)
	{
		return o_Traversed(object).importedNames();
	}

	@Override
	public A_Map o_NewNames (final AvailObject object)
	{
		return o_Traversed(object).newNames();
	}

	@Override
	public int o_NumArgs (final AvailObject object)
	{
		return o_Traversed(object).numArgs();
	}

	@Override
	public int o_NumSlots (final AvailObject object)
	{
		return o_Traversed(object).numSlots();
	}

	@Override
	public int o_NumLiterals (final AvailObject object)
	{
		return o_Traversed(object).numLiterals();
	}

	@Override
	public int o_NumLocals (final AvailObject object)
	{
		return o_Traversed(object).numLocals();
	}

	@Override
	public int o_NumOuters (final AvailObject object)
	{
		return o_Traversed(object).numOuters();
	}

	@Override
	public int o_NumOuterVars (final AvailObject object)
	{
		return o_Traversed(object).numOuterVars();
	}

	@Override
	public A_Tuple o_Nybbles (final AvailObject object)
	{
		return o_Traversed(object).nybbles();
	}

	@Override
	public A_BasicObject o_Parent (final AvailObject object)
	{
		return o_Traversed(object).parent();
	}

	@Override
	public int o_Pc (final AvailObject object)
	{
		return o_Traversed(object).pc();
	}

	@Override
	public int o_Priority (final AvailObject object)
	{
		return o_Traversed(object).priority();
	}

	@Override
	public A_Map o_PrivateNames (final AvailObject object)
	{
		return o_Traversed(object).privateNames();
	}

	@Override
	public A_Map o_FiberGlobals (final AvailObject object)
	{
		return o_Traversed(object).fiberGlobals();
	}

	@Override
	public A_Set o_GrammaticalRestrictions (final AvailObject object)
	{
		return o_Traversed(object).grammaticalRestrictions();
	}

	@Override
	public A_Type o_ReturnType (final AvailObject object)
	{
		return o_Traversed(object).returnType();
	}

	@Override
	public int o_SetBinHash (final AvailObject object)
	{
		return o_Traversed(object).setBinHash();
	}

	@Override
	public int o_SetBinSize (final AvailObject object)
	{
		return o_Traversed(object).setBinSize();
	}

	@Override
	public int o_SetSize (final AvailObject object)
	{
		return o_Traversed(object).setSize();
	}

	@Override
	public A_Type o_SizeRange (final AvailObject object)
	{
		return o_Traversed(object).sizeRange();
	}

	@Override
	public A_Map o_LazyActions (final AvailObject object)
	{
		return o_Traversed(object).lazyActions();
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
	public L2Chunk o_StartingChunk (final AvailObject object)
	{
		return o_Traversed(object).startingChunk();
	}

	@Override
	public A_String o_String (final AvailObject object)
	{
		return o_Traversed(object).string();
	}

	@Override
	public TokenType o_TokenType (final AvailObject object)
	{
		return o_Traversed(object).tokenType();
	}

	@Override
	public void o_TrimExcessInts (final AvailObject object)
	{
		o_Traversed(object).trimExcessInts();
	}

	@Override
	public A_Tuple o_TupleReverse (final AvailObject object)
	{
		return o_Traversed(object).tupleReverse();
	}

	@Override
	public int o_TupleSize (final AvailObject object)
	{
		return o_Traversed(object).tupleSize();
	}

	@Override
	public A_Type o_Kind (final AvailObject object)
	{
		return o_Traversed(object).kind();
	}

	@Override
	public A_Tuple o_TypeTuple (final AvailObject object)
	{
		return o_Traversed(object).typeTuple();
	}

	@Override
	public A_Number o_UpperBound (final AvailObject object)
	{
		return o_Traversed(object).upperBound();
	}

	@Override
	public boolean o_UpperInclusive (final AvailObject object)
	{
		return o_Traversed(object).upperInclusive();
	}

	@Override
	public AvailObject o_Value (final AvailObject object)
	{
		return o_Traversed(object).value();
	}

	@Override
	public A_Tuple o_ValuesAsTuple (final AvailObject object)
	{
		return o_Traversed(object).valuesAsTuple();
	}

	@Override
	public A_Type o_ValueType (final AvailObject object)
	{
		return o_Traversed(object).valueType();
	}

	@Override
	public A_Map o_VariableBindings (final AvailObject object)
	{
		return o_Traversed(object).variableBindings();
	}

	@Override
	public A_Set o_VisibleNames (final AvailObject object)
	{
		return o_Traversed(object).visibleNames();
	}

	@Override
	public A_Tuple o_ParsingInstructions (final AvailObject object)
	{
		return o_Traversed(object).parsingInstructions();
	}

	@Override
	public A_Phrase o_Expression (final AvailObject object)
	{
		return o_Traversed(object).expression();
	}

	@Override
	public A_Phrase o_Variable (final AvailObject object)
	{
		return o_Traversed(object).variable();
	}

	@Override
	public A_Tuple o_ArgumentsTuple (final AvailObject object)
	{
		return o_Traversed(object).argumentsTuple();
	}

	@Override
	public A_Tuple o_StatementsTuple (final AvailObject object)
	{
		return o_Traversed(object).statementsTuple();
	}

	@Override
	public A_Type o_ResultType (final AvailObject object)
	{
		return o_Traversed(object).resultType();
	}

	@Override
	public void o_NeededVariables (
		final AvailObject object,
		final A_Tuple neededVariables)
	{
		o_Traversed(object).neededVariables(neededVariables);
	}

	@Override
	public A_Tuple o_NeededVariables (final AvailObject object)
	{
		return o_Traversed(object).neededVariables();
	}

	@Override
	@Nullable
	public Primitive o_Primitive (final AvailObject object)
	{
		return o_Traversed(object).primitive();
	}

	@Override
	public int o_PrimitiveNumber (final AvailObject object)
	{
		return o_Traversed(object).primitiveNumber();
	}

	@Override
	public A_Type o_DeclaredType (final AvailObject object)
	{
		return o_Traversed(object).declaredType();
	}

	@Override
	public DeclarationKind o_DeclarationKind (final AvailObject object)
	{
		return o_Traversed(object).declarationKind();
	}

	@Override
	public A_Phrase o_TypeExpression (final AvailObject object)
	{
		return o_Traversed(object).typeExpression();
	}

	@Override
	public AvailObject o_InitializationExpression (final AvailObject object)
	{
		return o_Traversed(object).initializationExpression();
	}

	@Override
	public AvailObject o_LiteralObject (final AvailObject object)
	{
		return o_Traversed(object).literalObject();
	}

	@Override
	public A_Token o_Token (final AvailObject object)
	{
		return o_Traversed(object).token();
	}

	@Override
	public AvailObject o_MarkerValue (final AvailObject object)
	{
		return o_Traversed(object).markerValue();
	}

	@Override
	public A_Phrase o_ArgumentsListNode (
		final AvailObject object)
	{
		return o_Traversed(object).argumentsListNode();
	}

	@Override
	public A_Bundle o_Bundle (final AvailObject object)
	{
		return o_Traversed(object).bundle();
	}

	@Override
	public A_Tuple o_ExpressionsTuple (final AvailObject object)
	{
		return o_Traversed(object).expressionsTuple();
	}

	@Override
	public A_Phrase o_Declaration (final AvailObject object)
	{
		return o_Traversed(object).declaration();
	}

	@Override
	public A_Type o_ExpressionType (final AvailObject object)
	{
		return o_Traversed(object).expressionType();
	}

	@Override
	public void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		o_Traversed(object).emitEffectOn(codeGenerator);
	}

	@Override
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		o_Traversed(object).emitValueOn(codeGenerator);
	}

	@Override
	public void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		o_Traversed(object).childrenMap(transformer);
	}

	@Override
	public void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		o_Traversed(object).childrenDo(action);
	}

	@Override
	public void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		o_Traversed(object).validateLocally(parent);
	}

	@Override
	public A_RawFunction o_GenerateInModule (
		final AvailObject object,
		final A_Module module)
	{
		return o_Traversed(object).generateInModule(module);
	}

	@Override
	public A_Phrase o_CopyWith (
		final AvailObject object,
		final A_Phrase newPhrase)
	{
		return o_Traversed(object).copyWith(newPhrase);
	}

	@Override
	public A_Phrase o_CopyConcatenating (
		final AvailObject object,
		final A_Phrase newListPhrase)
	{
		return o_Traversed(object).copyConcatenating(newListPhrase);
	}

	@Override
	public void o_IsLastUse (
		final AvailObject object,
		final boolean isLastUse)
	{
		o_Traversed(object).isLastUse(isLastUse);
	}

	@Override
	public boolean o_IsLastUse (
		final AvailObject object)
	{
		return o_Traversed(object).isLastUse();
	}

	@Override
	public boolean o_IsMacroDefinition (
		final AvailObject object)
	{
		return o_Traversed(object).isMacroDefinition();
	}

	@Override
	public A_Phrase o_CopyMutablePhrase (
		final AvailObject object)
	{
		return o_Traversed(object).copyMutablePhrase();
	}

	@Override
	public A_Type o_BinUnionKind (
		final AvailObject object)
	{
		return o_Traversed(object).binUnionKind();
	}

	@Override
	public A_Phrase o_OutputPhrase (
		final AvailObject object)
	{
		return o_Traversed(object).outputPhrase();
	}

	@Override
	public A_Atom o_ApparentSendName (
		final AvailObject object)
	{
		return o_Traversed(object).apparentSendName();
	}

	@Override
	public A_Tuple o_Statements (final AvailObject object)
	{
		return o_Traversed(object).statements();
	}

	@Override
	public void o_FlattenStatementsInto (
		final AvailObject object,
		final List<A_Phrase> accumulatedStatements)
	{
		o_Traversed(object).flattenStatementsInto(accumulatedStatements);
	}

	@Override
	public int o_LineNumber (final AvailObject object)
	{
		return o_Traversed(object).lineNumber();
	}

	@Override
	public A_Map o_AllParsingPlansInProgress (final AvailObject object)
	{
		return o_Traversed(object).allParsingPlansInProgress();
	}

	@Override
	public boolean o_IsSetBin (final AvailObject object)
	{
		return o_Traversed(object).isSetBin();
	}

	@Override
	public MapIterable o_MapIterable (
		final AvailObject object)
	{
		return o_Traversed(object).mapIterable();
	}

	@Override
	public A_Set o_DeclaredExceptions (
		final AvailObject object)
	{
		return o_Traversed(object).declaredExceptions();
	}

	@Override
	public boolean o_IsInt (
		final AvailObject object)
	{
		return o_Traversed(object).isInt();
	}

	@Override
	public boolean o_IsLong (
		final AvailObject object)
	{
		return o_Traversed(object).isLong();
	}

	@Override
	public A_Type o_ArgsTupleType (final AvailObject object)
	{
		return o_Traversed(object).argsTupleType();
	}

	@Override
	public boolean o_EqualsInstanceTypeFor (
		final AvailObject object,
		final AvailObject anObject)
	{
		return o_Traversed(object).equalsInstanceTypeFor(anObject);
	}

	@Override
	public A_Set o_Instances (final AvailObject object)
	{
		return o_Traversed(object).instances();
	}

	@Override
	public boolean o_EqualsEnumerationWithSet (
		final AvailObject object,
		final A_Set aSet)
	{
		return o_Traversed(object).equalsEnumerationWithSet(aSet);
	}

	@Override
	public boolean o_IsEnumeration (final AvailObject object)
	{
		return o_Traversed(object).isEnumeration();
	}

	@Override
	public boolean o_IsInstanceOf (
		final AvailObject object,
		final A_Type aType)
	{
		return o_Traversed(object).isInstanceOf(aType);
	}

	@Override
	public boolean o_EnumerationIncludesInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return o_Traversed(object).enumerationIncludesInstance(
			potentialInstance);
	}

	@Override
	public A_Type o_ComputeSuperkind (final AvailObject object)
	{
		return o_Traversed(object).computeSuperkind();
	}

	@Override
	public void o_SetAtomProperty (
		final AvailObject object,
		final A_Atom key,
		final A_BasicObject value)
	{
		A_Atom.Companion.setAtomProperty(o_Traversed(object), key, value);
	}

	@Override
	public AvailObject o_GetAtomProperty (
		final AvailObject object,
		final A_Atom key)
	{
		return A_Atom.Companion.getAtomProperty(o_Traversed(object), key);
	}

	@Override
	public boolean o_EqualsEnumerationType (
		final AvailObject object,
		final A_BasicObject another)
	{
		return o_Traversed(object).equalsEnumerationType(another);
	}

	@Override
	public A_Type o_ReadType (final AvailObject object)
	{
		return o_Traversed(object).readType();
	}

	@Override
	public A_Type o_WriteType (final AvailObject object)
	{
		return o_Traversed(object).writeType();
	}

	@Override
	public void o_Versions (
		final AvailObject object,
		final A_Set versionStrings)
	{
		o_Traversed(object).versions(versionStrings);
	}

	@Override
	public A_Set o_Versions (final AvailObject object)
	{
		return o_Traversed(object).versions();
	}

	@Override
	public boolean o_EqualsPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		return o_Traversed(object).equalsPhraseType(aPhraseType);
	}

	@Override
	public PhraseKind o_PhraseKind (final AvailObject object)
	{
		return o_Traversed(object).phraseKind();
	}

	@Override
	public boolean o_PhraseKindIsUnder (
		final AvailObject object,
		final PhraseKind expectedPhraseKind)
	{
		return o_Traversed(object).phraseKindIsUnder(expectedPhraseKind);
	}

	@Override
	public boolean o_IsRawPojo (final AvailObject object)
	{
		return o_Traversed(object).isRawPojo();
	}

	@Override
	public void o_AddSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction restrictionSignature)
	{
		o_Traversed(object).addSemanticRestriction(restrictionSignature);
	}

	@Override
	public void o_RemoveSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction restriction)
	{
		o_Traversed(object).removeSemanticRestriction(restriction);
	}

	@Override
	public A_Set o_SemanticRestrictions (
		final AvailObject object)
	{
		return o_Traversed(object).semanticRestrictions();
	}

	@Override
	public void o_AddSealedArgumentsType (
		final AvailObject object,
		final A_Tuple typeTuple)
	{
		o_Traversed(object).addSealedArgumentsType(typeTuple);
	}

	@Override
	public void o_RemoveSealedArgumentsType (
		final AvailObject object,
		final A_Tuple typeTuple)
	{
		o_Traversed(object).removeSealedArgumentsType(typeTuple);
	}

	@Override
	public A_Tuple o_SealedArgumentsTypesTuple (
		final AvailObject object)
	{
		return o_Traversed(object).sealedArgumentsTypesTuple();
	}

	@Override
	public void o_ModuleAddSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction semanticRestriction)
	{
		o_Traversed(object).moduleAddSemanticRestriction(
			semanticRestriction);
	}

	@Override
	public void o_AddConstantBinding (
		final AvailObject object,
		final A_String name,
		final A_Variable constantBinding)
	{
		o_Traversed(object).addConstantBinding(
			name,
			constantBinding);
	}

	@Override
	public void o_AddVariableBinding (
		final AvailObject object,
		final A_String name,
		final A_Variable variableBinding)
	{
		o_Traversed(object).addVariableBinding(
			name,
			variableBinding);
	}

	@Override
	public boolean o_IsMethodEmpty (
		final AvailObject object)
	{
		return o_Traversed(object).isMethodEmpty();
	}

	@Override
	public boolean o_IsPojoSelfType (final AvailObject object)
	{
		return o_Traversed(object).isPojoSelfType();
	}

	@Override
	public A_Type o_PojoSelfType (final AvailObject object)
	{
		return o_Traversed(object).pojoSelfType();
	}

	@Override
	public AvailObject o_JavaClass (final AvailObject object)
	{
		return o_Traversed(object).javaClass();
	}

	@Override
	public boolean o_IsUnsignedShort (final AvailObject object)
	{
		return o_Traversed(object).isUnsignedShort();
	}

	@Override
	public int o_ExtractUnsignedShort (final AvailObject object)
	{
		return o_Traversed(object).extractUnsignedShort();
	}

	@Override
	public boolean o_IsFloat (final AvailObject object)
	{
		return o_Traversed(object).isFloat();
	}

	@Override
	public boolean o_IsDouble (final AvailObject object)
	{
		return o_Traversed(object).isDouble();
	}

	@Override
	public AvailObject o_RawPojo (final AvailObject object)
	{
		return o_Traversed(object).rawPojo();
	}

	@Override
	public boolean o_IsPojo (final AvailObject object)
	{
		return o_Traversed(object).isPojo();
	}

	@Override
	public boolean o_IsPojoType (final AvailObject object)
	{
		return o_Traversed(object).isPojoType();
	}

	@Override
	public Order o_NumericCompare (
		final AvailObject object,
		final A_Number another)
	{
		return o_Traversed(object).numericCompare(another);
	}

	@Override
	public Order o_NumericCompareToDouble (
		final AvailObject object,
		final double aDouble)
	{
		return o_Traversed(object).numericCompareToDouble(aDouble);
	}

	@Override
	public A_Number o_AddToDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	public A_Number o_AddToFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).addToFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	public A_Number o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	public A_Number o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).subtractFromFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	public A_Number o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	public A_Number o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).multiplyByFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	public A_Number o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoDoubleCanDestroy(
			doubleObject,
			canDestroy);
	}

	@Override
	public A_Number o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return o_Traversed(object).divideIntoFloatCanDestroy(
			floatObject,
			canDestroy);
	}

	@Override
	public A_Map o_LazyPrefilterMap (
		final AvailObject object)
	{
		return o_Traversed(object).lazyPrefilterMap();
	}

	@Override
	public SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return o_Traversed(object).serializerOperation();
	}

	@Override
	public A_MapBin o_MapBinAtHashPutLevelCanDestroy (
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
	public A_MapBin o_MapBinRemoveKeyHashCanDestroy (
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
	public A_MapBin o_MapBinAtHashReplacingLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash,
		final A_BasicObject notFoundValue,
		final BinaryOperator<A_BasicObject> transformer,
		final byte myLevel,
		final boolean canDestroy)
	{
		return o_Traversed(object).mapBinAtHashReplacingLevelCanDestroy(
			key, keyHash, notFoundValue, transformer, myLevel, canDestroy);
	}


	@Override
	public int o_MapBinSize (final AvailObject object)
	{
		return o_Traversed(object).mapBinSize();
	}

	@Override
	public A_Type o_MapBinKeyUnionKind (
		final AvailObject object)
	{
		return o_Traversed(object).mapBinKeyUnionKind();
	}

	@Override
	public A_Type o_MapBinValueUnionKind (
		final AvailObject object)
	{
		return o_Traversed(object).mapBinValueUnionKind();
	}

	@Override
	public boolean o_IsHashedMapBin (
		final AvailObject object)
	{
		return o_Traversed(object).isHashedMapBin();
	}

	@Override
	public @Nullable AvailObject o_MapBinAtHash (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash)
	{
		return o_Traversed(object).mapBinAtHash(key, keyHash);
	}

	@Override
	public int o_MapBinKeysHash (
		final AvailObject object)
	{
		return o_Traversed(object).mapBinKeysHash();
	}

	@Override
	public int o_MapBinValuesHash (final AvailObject object)
	{
		return o_Traversed(object).mapBinValuesHash();
	}

	@Override
	public A_Module o_IssuingModule (
		final AvailObject object)
	{
		return A_Atom.Companion.issuingModule(o_Traversed(object));
	}

	@Override
	public boolean o_IsPojoFusedType (final AvailObject object)
	{
		return o_Traversed(object).isPojoFusedType();
	}

	@Override
	public boolean o_IsSupertypeOfPojoBottomType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return o_Traversed(object).isSupertypeOfPojoBottomType(aPojoType);
	}

	@Override
	public boolean o_EqualsPojoBottomType (final AvailObject object)
	{
		return o_Traversed(object).equalsPojoBottomType();
	}

	@Override
	public AvailObject o_JavaAncestors (final AvailObject object)
	{
		return o_Traversed(object).javaAncestors();
	}

	@Override
	public A_Type o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		return o_Traversed(object).typeIntersectionOfPojoFusedType(
			aFusedPojoType);
	}

	@Override
	public A_Type o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		return o_Traversed(object).typeIntersectionOfPojoUnfusedType(
			anUnfusedPojoType);
	}

	@Override
	public A_Type o_TypeUnionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		return o_Traversed(object).typeUnionOfPojoFusedType(
			aFusedPojoType);
	}

	@Override
	public A_Type o_TypeUnionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		return o_Traversed(object).typeUnionOfPojoUnfusedType(
			anUnfusedPojoType);
	}

	@Override
	public boolean o_IsPojoArrayType (final AvailObject object)
	{
		return o_Traversed(object).isPojoArrayType();
	}

	@Override
	public @Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> classHint)
	{
		return o_Traversed(object).marshalToJava(classHint);
	}

	@Override
	public A_Map o_TypeVariables (final AvailObject object)
	{
		return o_Traversed(object).typeVariables();
	}

	@Override
	public boolean o_EqualsPojoField (
		final AvailObject object,
		final AvailObject field,
		final AvailObject receiver)
	{
		return o_Traversed(object).equalsPojoField(field, receiver);
	}

	@Override
	public boolean o_IsSignedByte (final AvailObject object)
	{
		return o_Traversed(object).isSignedByte();
	}

	@Override
	public boolean o_IsSignedShort (final AvailObject object)
	{
		return o_Traversed(object).isSignedShort();
	}

	@Override
	public byte o_ExtractSignedByte (final AvailObject object)
	{
		return o_Traversed(object).extractSignedByte();
	}

	@Override
	public short o_ExtractSignedShort (final AvailObject object)
	{
		return o_Traversed(object).extractSignedShort();
	}

	@Override
	public boolean o_EqualsEqualityRawPojo (
		final AvailObject object,
		final AvailObject otherEqualityRawPojo,
		final @Nullable Object otherJavaObject)
	{
		return o_Traversed(object).equalsEqualityRawPojoFor(object, otherJavaObject);
	}

	@Override
	public @Nullable <T> T o_JavaObject (final AvailObject object)
	{
		return o_Traversed(object).javaObject();
	}

	@Override
	public BigInteger o_AsBigInteger (
		final AvailObject object)
	{
		return o_Traversed(object).asBigInteger();
	}

	@Override
	public A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		return o_Traversed(object).appendCanDestroy(newElement, canDestroy);
	}

	@Override
	public A_Map o_LazyIncompleteCaseInsensitive (
		final AvailObject object)
	{
		return o_Traversed(object).lazyIncompleteCaseInsensitive();
	}

	@Override
	public A_String o_LowerCaseString (final AvailObject object)
	{
		return o_Traversed(object).lowerCaseString();
	}

	@Override
	public A_Number o_InstanceCount (final AvailObject object)
	{
		return o_Traversed(object).instanceCount();
	}

	@Override
	public long o_TotalInvocations (final AvailObject object)
	{
		return o_Traversed(object).totalInvocations();
	}

	@Override
	public void o_TallyInvocation (final AvailObject object)
	{
		o_Traversed(object).tallyInvocation();
	}

	@Override
	public A_Tuple o_FieldTypeTuple (final AvailObject object)
	{
		return o_Traversed(object).fieldTypeTuple();
	}

	@Override
	public A_Tuple o_FieldTuple (final AvailObject object)
	{
		return o_Traversed(object).fieldTuple();
	}

	@Override
	public A_Type o_LiteralType (final AvailObject object)
	{
		return o_Traversed(object).literalType();
	}

	@Override
	public A_Type o_TypeIntersectionOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return o_Traversed(object).typeIntersectionOfTokenType(aTokenType);
	}

	@Override
	public A_Type o_TypeIntersectionOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return o_Traversed(object).typeIntersectionOfLiteralTokenType(
			aLiteralTokenType);
	}

	@Override
	public A_Type o_TypeUnionOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return o_Traversed(object).typeUnionOfTokenType(aTokenType);
	}

	@Override
	public A_Type o_TypeUnionOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return o_Traversed(object).typeUnionOfLiteralTokenType(
			aLiteralTokenType);
	}

	@Override
	public boolean o_IsTokenType (final AvailObject object)
	{
		return o_Traversed(object).isTokenType();
	}

	@Override
	public boolean o_IsLiteralTokenType (final AvailObject object)
	{
		return o_Traversed(object).isLiteralTokenType();
	}

	@Override
	public boolean o_IsLiteralToken (final AvailObject object)
	{
		return o_Traversed(object).isLiteralToken();
	}

	@Override
	public boolean o_IsSupertypeOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return o_Traversed(object).isSupertypeOfTokenType(
			aTokenType);
	}

	@Override
	public boolean o_IsSupertypeOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return o_Traversed(object).isSupertypeOfLiteralTokenType(
			aLiteralTokenType);
	}

	@Override
	public boolean o_EqualsTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return o_Traversed(object).equalsTokenType(aTokenType);
	}

	@Override
	public boolean o_EqualsLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return o_Traversed(object).equalsLiteralTokenType(aLiteralTokenType);
	}

	@Override
	public boolean o_EqualsObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return o_Traversed(object).equalsObjectType(anObjectType);
	}

	@Override
	public boolean o_EqualsToken (
		final AvailObject object,
		final A_Token aToken)
	{
		return o_Traversed(object).equalsToken(aToken);
	}

	@Override
	public A_Number o_BitwiseAnd (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitwiseAnd(anInteger, canDestroy);
	}

	@Override
	public A_Number o_BitwiseOr (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitwiseOr(anInteger, canDestroy);
	}

	@Override
	public A_Number o_BitwiseXor (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitwiseXor(anInteger, canDestroy);
	}

	@Override
	public void o_AddSeal (
		final AvailObject object,
		final A_Atom methodName,
		final A_Tuple argumentTypes)
	{
		o_Traversed(object).addSeal(methodName, argumentTypes);
	}

	@Override
	public AvailObject o_Instance (
		final AvailObject object)
	{
		return o_Traversed(object).instance();
	}

	@Override
	public void o_SetMethodName (
		final AvailObject object,
		final A_String methodName)
	{
		o_Traversed(object).setMethodName(methodName);
	}

	@Override
	public int o_StartingLineNumber (
		final AvailObject object)
	{
		return o_Traversed(object).startingLineNumber();
	}

	@Override
	public A_Module o_Module (final AvailObject object)
	{
		return o_Traversed(object).module();
	}

	@Override
	public A_String o_MethodName (final AvailObject object)
	{
		return o_Traversed(object).methodName();
	}

	@Override
	public String o_NameForDebugger (final AvailObject object)
	{
		final String name = o_Traversed(object).nameForDebugger();
		return "IND→" + name;
	}

	@Override
	public boolean o_BinElementsAreAllInstancesOfKind (
		final AvailObject object,
		final A_Type kind)
	{
		return o_Traversed(object).binElementsAreAllInstancesOfKind(kind);
	}

	@Override
	public boolean o_SetElementsAreAllInstancesOfKind (
		final AvailObject object,
		final AvailObject kind)
	{
		return o_Traversed(object).setElementsAreAllInstancesOfKind(kind);
	}

	@Override
	public MapIterable o_MapBinIterable (
		final AvailObject object)
	{
		return o_Traversed(object).mapBinIterable();
	}

	@Override
	public boolean o_RangeIncludesInt (
		final AvailObject object,
		final int anInt)
	{
		return o_Traversed(object).rangeIncludesInt(anInt);
	}

	@Override
	public A_Number o_BitShiftLeftTruncatingToBits (
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
	public SetIterator o_SetBinIterator (
		final AvailObject object)
	{
		return o_Traversed(object).setBinIterator();
	}

	@Override
	public A_Number o_BitShift (
		final AvailObject object,
		final A_Number shiftFactor,
		final boolean canDestroy)
	{
		return o_Traversed(object).bitShift(shiftFactor, canDestroy);
	}

	@Override
	public boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return o_Traversed(object).equalsPhrase(aPhrase);
	}

	@Override
	public A_Phrase o_StripMacro (
		final AvailObject object)
	{
		return o_Traversed(object).stripMacro();
	}

	@Override
	public A_Method o_DefinitionMethod (
		final AvailObject object)
	{
		return o_Traversed(object).definitionMethod();
	}

	@Override
	public A_Tuple o_PrefixFunctions (
		final AvailObject object)
	{
		return o_Traversed(object).prefixFunctions();
	}

	@Override
	public boolean o_EqualsByteArrayTuple (
		final AvailObject object,
		final A_Tuple aByteArrayTuple)
	{
		return o_Traversed(object).equalsByteArrayTuple(aByteArrayTuple);
	}

	@Override
	public boolean o_CompareFromToWithByteArrayTupleStartingAt (
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
	public byte[] o_ByteArray (final AvailObject object)
	{
		return o_Traversed(object).byteArray();
	}

	@Override
	public boolean o_IsByteArrayTuple (final AvailObject object)
	{
		return o_Traversed(object).isByteArrayTuple();
	}

	@Override
	public void o_UpdateForNewGrammaticalRestriction (
		final AvailObject object,
		final A_ParsingPlanInProgress planInProgress,
		final Deque<Pair<A_BundleTree, A_ParsingPlanInProgress>> treesToVisit)
	{
		o_Traversed(object).updateForNewGrammaticalRestriction(
			planInProgress,
			treesToVisit);
	}

	@Override
	public void o_Lock (final AvailObject object, final Continuation0 critical)
	{
		o_Traversed(object).lock(critical);
	}

	@Override
	public <T> T o_Lock (final AvailObject object, final Supplier<T> supplier)
	{
		return o_Traversed(object).lock(supplier);
	}

	@Override
	public A_String o_ModuleName (final AvailObject object)
	{
		return o_Traversed(object).moduleName();
	}

	@Override
	public A_Method o_BundleMethod (final AvailObject object)
	{
		return o_Traversed(object).bundleMethod();
	}

	@Override
	public AvailObject o_GetAndSetValue (
			final AvailObject object,
			final A_BasicObject newValue)
		throws VariableGetException, VariableSetException
	{
		return o_Traversed(object).getAndSetValue(newValue);
	}

	@Override
	public boolean o_CompareAndSwapValues (
			final AvailObject object,
			final A_BasicObject reference,
			final A_BasicObject newValue)
		throws VariableGetException, VariableSetException
	{
		return o_Traversed(object).compareAndSwapValues(reference, newValue);
	}

	@Override
	public A_Number o_FetchAndAddValue (
			final AvailObject object,
			final A_Number addend)
		throws VariableGetException, VariableSetException
	{
		return o_Traversed(object).fetchAndAddValue(addend);
	}

	@Override
	public Continuation1NotNull<Throwable> o_FailureContinuation (final AvailObject object)
	{
		return o_Traversed(object).failureContinuation();
	}

	@Override
	public Continuation1NotNull<AvailObject> o_ResultContinuation (final AvailObject object)
	{
		return o_Traversed(object).resultContinuation();
	}

	@Override
	public @Nullable AvailLoader o_AvailLoader (final AvailObject object)
	{
		return o_Traversed(object).availLoader();
	}

	@Override
	public void o_AvailLoader (final AvailObject object, @Nullable final AvailLoader loader)
	{
		o_Traversed(object).availLoader(loader);
	}

	@Override
	public boolean o_InterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		return o_Traversed(object).interruptRequestFlag(flag);
	}

	@Override
	public boolean o_GetAndClearInterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		return o_Traversed(object).getAndClearInterruptRequestFlag(flag);
	}

	@Override
	public boolean o_GetAndSetSynchronizationFlag (
		final AvailObject object,
		final SynchronizationFlag flag,
		final boolean newValue)
	{
		return o_Traversed(object).getAndSetSynchronizationFlag(flag, newValue);
	}

	@Override
	public AvailObject o_FiberResult (final AvailObject object)
	{
		return o_Traversed(object).fiberResult();
	}

	@Override
	public void o_FiberResult (final AvailObject object, final A_BasicObject result)
	{
		o_Traversed(object).fiberResult(result);
	}

	@Override
	public A_Set o_JoiningFibers (final AvailObject object)
	{
		return o_Traversed(object).joiningFibers();
	}

	@Override
	public @Nullable TimerTask o_WakeupTask (final AvailObject object)
	{
		return o_Traversed(object).wakeupTask();
	}

	@Override
	public void o_WakeupTask (final AvailObject object, @Nullable final TimerTask task)
	{
		o_Traversed(object).wakeupTask(task);
	}

	@Override
	public void o_JoiningFibers (final AvailObject object, final A_Set joiners)
	{
		o_Traversed(object).joiningFibers(joiners);
	}

	@Override
	public A_Map o_HeritableFiberGlobals (final AvailObject object)
	{
		return o_Traversed(object).heritableFiberGlobals();
	}

	@Override
	public void o_HeritableFiberGlobals (
		final AvailObject object,
		final A_Map globals)
	{
		o_Traversed(object).heritableFiberGlobals(globals);
	}

	@Override
	public boolean o_GeneralFlag (final AvailObject object, final GeneralFlag flag)
	{
		return o_Traversed(object).generalFlag(flag);
	}

	@Override
	public void o_SetGeneralFlag (final AvailObject object, final GeneralFlag flag)
	{
		o_Traversed(object).setGeneralFlag(flag);
	}

	@Override
	public void o_ClearGeneralFlag (final AvailObject object, final GeneralFlag flag)
	{
		o_Traversed(object).clearGeneralFlag(flag);
	}

	@Override
	public ByteBuffer o_ByteBuffer (final AvailObject object)
	{
		return o_Traversed(object).byteBuffer();
	}

	@Override
	public boolean o_EqualsByteBufferTuple (
		final AvailObject object,
		final A_Tuple aByteBufferTuple)
	{
		return o_Traversed(object).equalsByteBufferTuple(aByteBufferTuple);
	}

	@Override
	public boolean o_CompareFromToWithByteBufferTupleStartingAt (
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
	public boolean o_IsByteBufferTuple (final AvailObject object)
	{
		return o_Traversed(object).isByteBufferTuple();
	}

	@Override
	public A_String o_FiberName (final AvailObject object)
	{
		return o_Traversed(object).fiberName();
	}

	@Override
	public void o_FiberNameSupplier (
		final AvailObject object,
		final Supplier<A_String> supplier)
	{
		o_Traversed(object).fiberNameSupplier(supplier);
	}

	@Override
	public A_Set o_Bundles (final AvailObject object)
	{
		return o_Traversed(object).bundles();
	}

	@Override
	public void o_MethodAddBundle (final AvailObject object, final A_Bundle bundle)
	{
		o_Traversed(object).methodAddBundle(bundle);
	}

	@Override
	public A_Module o_DefinitionModule (final AvailObject object)
	{
		return o_Traversed(object).definitionModule();
	}

	@Override
	public A_String o_DefinitionModuleName (final AvailObject object)
	{
		return o_Traversed(object).definitionModuleName();
	}

	@Override
	public A_Bundle o_BundleOrCreate (final AvailObject object)
		throws MalformedMessageException
	{
		return A_Atom.Companion.bundleOrCreate(o_Traversed(object));
	}
	@Override
	public A_Bundle o_BundleOrNil (final AvailObject object)
	{
		return A_Atom.Companion.bundleOrNil(o_Traversed(object));
	}

	@Override
	public A_Map o_EntryPoints (final AvailObject object)
	{
		return o_Traversed(object).entryPoints();
	}

	@Override
	public void o_AddEntryPoint (
		final AvailObject object,
		final A_String stringName,
		final A_Atom trueName)
	{
		o_Traversed(object).addEntryPoint(stringName, trueName);
	}

	@Override
	public A_Set o_AllAncestors (final AvailObject object)
	{
		return o_Traversed(object).allAncestors();
	}

	@Override
	public void o_AddAncestors (final AvailObject object, final A_Set moreAncestors)
	{
		o_Traversed(object).addAncestors(moreAncestors);
	}

	@Override
	public A_Tuple o_ArgumentRestrictionSets (final AvailObject object)
	{
		return o_Traversed(object).argumentRestrictionSets();
	}

	@Override
	public A_Bundle o_RestrictedBundle (final AvailObject object)
	{
		return o_Traversed(object).restrictedBundle();
	}

	@Override
	public A_String o_AtomName (final AvailObject object)
	{
		return A_Atom.Companion.atomName(o_Traversed(object));
	}

	@Override
	public void o_AdjustPcAndStackp (
		final AvailObject object,
		final int pc,
		final int stackp)
	{
		o_Traversed(object).adjustPcAndStackp(pc, stackp);
	}

	@Override
	public int o_TreeTupleLevel (final AvailObject object)
	{
		return o_Traversed(object).treeTupleLevel();
	}

	@Override
	public int o_ChildCount (final AvailObject object)
	{
		return o_Traversed(object).childCount();
	}

	@Override
	public A_Tuple o_ChildAt (final AvailObject object, final int childIndex)
	{
		return o_Traversed(object).childAt(childIndex);
	}

	@Override
	public A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		return o_Traversed(object).concatenateWith(otherTuple, canDestroy);
	}

	@Override
	public A_Tuple o_ReplaceFirstChild (
		final AvailObject object,
		final A_Tuple newFirst)
	{
		return o_Traversed(object).replaceFirstChild(newFirst);
	}

	@Override
	public boolean o_IsByteString (final AvailObject object)
	{
		return o_Traversed(object).isByteString();
	}

	@Override
	public boolean o_IsTwoByteString (final AvailObject object)
	{
		return o_Traversed(object).isTwoByteString();
	}

	@Override
	public boolean o_IsIntegerIntervalTuple (final AvailObject object)
	{
		return o_Traversed(object).isIntegerIntervalTuple();
	}

	@Override
	public boolean o_IsSmallIntegerIntervalTuple (final AvailObject object)
	{
		return o_Traversed(object).isSmallIntegerIntervalTuple();
	}

	@Override
	public boolean o_IsRepeatedElementTuple (final AvailObject object)
	{
		return o_Traversed(object).isRepeatedElementTuple();
	}

	@Override
	public void o_AddWriteReactor (
		final AvailObject object,
		final A_Atom key,
		final VariableAccessReactor reactor)
	{
		o_Traversed(object).addWriteReactor(key, reactor);
	}

	@Override
	public void o_RemoveWriteReactor (final AvailObject object, final A_Atom key)
		throws AvailException
	{
		o_Traversed(object).removeWriteReactor(key);
	}

	@Override
	public boolean o_TraceFlag (final AvailObject object, final TraceFlag flag)
	{
		return o_Traversed(object).traceFlag(flag);
	}

	@Override
	public void o_SetTraceFlag (final AvailObject object, final TraceFlag flag)
	{
		o_Traversed(object).setTraceFlag(flag);
	}

	@Override
	public void o_ClearTraceFlag (final AvailObject object, final TraceFlag flag)
	{
		o_Traversed(object).clearTraceFlag(flag);
	}

	@Override
	public void o_RecordVariableAccess (
		final AvailObject object,
		final A_Variable var,
		final boolean wasRead)
	{
		o_Traversed(object).recordVariableAccess(var, wasRead);
	}

	@Override
	public A_Set o_VariablesReadBeforeWritten (final AvailObject object)
	{
		return o_Traversed(object).variablesReadBeforeWritten();
	}

	@Override
	public A_Set o_VariablesWritten (final AvailObject object)
	{
		return o_Traversed(object).variablesWritten();
	}

	@Override
	public A_Set o_ValidWriteReactorFunctions (final AvailObject object)
	{
		return o_Traversed(object).validWriteReactorFunctions();
	}

	@Override
	public A_Continuation o_ReplacingCaller(
		final AvailObject object,
		final A_Continuation newCaller)
	{
		return o_Traversed(object).replacingCaller(newCaller);
	}

	@Override
	public void o_WhenContinuationIsAvailableDo (
		final AvailObject object,
		final Continuation1NotNull<A_Continuation> whenReified)
	{
		o_Traversed(object).whenContinuationIsAvailableDo(whenReified);
	}

	@Override
	public A_Set o_GetAndClearReificationWaiters (final AvailObject object)
	{
		return o_Traversed(object).getAndClearReificationWaiters();
	}

	@Override
	public boolean o_IsBottom (final AvailObject object)
	{
		return o_Traversed(object).isBottom();
	}

	@Override
	public boolean o_IsVacuousType (final AvailObject object)
	{
		return o_Traversed(object).isVacuousType();
	}

	@Override
	public boolean o_IsTop (final AvailObject object)
	{
		return o_Traversed(object).isTop();
	}

	@Override
	public boolean o_IsAtomSpecial (final AvailObject object)
	{
		return A_Atom.Companion.isAtomSpecial(o_Traversed(object));
	}

	@Override
	public boolean o_HasValue (final AvailObject object)
	{
		return o_Traversed(object).hasValue();
	}

	@Override
	public void o_AddUnloadFunction (
		final AvailObject object,
		final A_Function unloadFunction)
	{
		o_Traversed(object).addUnloadFunction(unloadFunction);
	}

	@Override
	public A_Set o_ExportedNames (final AvailObject object)
	{
		return o_Traversed(object).exportedNames();
	}

	@Override
	public boolean o_IsInitializedWriteOnceVariable (final AvailObject object)
	{
		return o_Traversed(object).isInitializedWriteOnceVariable();
	}

	@Override
	public void o_TransferIntoByteBuffer (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final ByteBuffer outputByteBuffer)
	{
		o_Traversed(object).transferIntoByteBuffer(
			startIndex, endIndex, outputByteBuffer);
	}

	@Override
	public boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		return o_Traversed(object).tupleElementsInRangeAreInstancesOf(
			startIndex, endIndex, type);
	}

	@Override
	public boolean o_IsNumericallyIntegral (final AvailObject object)
	{
		return o_Traversed(object).isNumericallyIntegral();
	}

	@Override
	public TextInterface o_TextInterface (final AvailObject object)
	{
		return o_Traversed(object).textInterface();
	}

	@Override
	public void o_TextInterface (
		final AvailObject object,
		final TextInterface textInterface)
	{
		o_Traversed(object).textInterface(textInterface);
	}

	@Override
	public void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		o_Traversed(object).writeTo(writer);
	}

	@Override
	public void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		o_Traversed(object).writeSummaryTo(writer);
	}

	@Override
	public A_Type o_TypeIntersectionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return o_Traversed(object).typeIntersectionOfPrimitiveTypeEnum(
			primitiveTypeEnum);
	}

	@Override
	public A_Type o_TypeUnionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return o_Traversed(object).typeUnionOfPrimitiveTypeEnum(
			primitiveTypeEnum);
	}

	@Override
	public A_Tuple o_TupleOfTypesFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		return o_Traversed(object).tupleOfTypesFromTo(startIndex, endIndex);
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (
		final AvailObject object)
	{
		return o_Traversed(object).showValueInNameForDebugger();
	}

	@Override
	public A_Phrase o_List (final AvailObject object)
	{
		return o_Traversed(object).list();
	}

	@Override
	public A_Tuple o_Permutation (final AvailObject object)
	{
		return o_Traversed(object).permutation();
	}

	@Override
	public void o_EmitAllValuesOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		o_Traversed(object).emitAllValuesOn(codeGenerator);
	}

	@Override
	public A_Type o_SuperUnionType (final AvailObject object)
	{
		return o_Traversed(object).superUnionType();
	}

	@Override
	public boolean o_HasSuperCast (final AvailObject object)
	{
		return o_Traversed(object).hasSuperCast();
	}

	@Override
	public A_Tuple o_MacroDefinitionsTuple (final AvailObject object)
	{
		return o_Traversed(object).macroDefinitionsTuple();
	}

	@Override
	public A_Tuple o_LookupMacroByPhraseTuple (
		final AvailObject object,
		final A_Tuple argumentPhraseTuple)
	{
		return o_Traversed(object).lookupMacroByPhraseTuple(
			argumentPhraseTuple);
	}

	@Override
	public A_Phrase o_ExpressionAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).expressionAt(index);
	}

	@Override
	public int o_ExpressionsSize (final AvailObject object)
	{
		return o_Traversed(object).expressionsSize();
	}

	@Override
	public int o_ParsingPc (final AvailObject object)
	{
		return o_Traversed(object).parsingPc();
	}

	@Override
	public boolean o_IsMacroSubstitutionNode (final AvailObject object)
	{
		return o_Traversed(object).isMacroSubstitutionNode();
	}

	@Override
	public MessageSplitter o_MessageSplitter (final AvailObject object)
	{
		return o_Traversed(object).messageSplitter();
	}

	@Override
	public void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		o_Traversed(object).statementsDo(continuation);
	}

	@Override
	public A_Phrase o_MacroOriginalSendNode (final AvailObject object)
	{
		return o_Traversed(object).macroOriginalSendNode();
	}

	@Override
	public boolean o_EqualsInt (
		final AvailObject object,
		final int theInt)
	{
		return o_Traversed(object).equalsInt(theInt);
	}

	@Override
	public A_Tuple o_Tokens (final AvailObject object)
	{
		return o_Traversed(object).tokens();
	}

	@Override
	public A_Bundle o_ChooseBundle (
		final AvailObject object,
		final A_Module currentModule)
	{
		return o_Traversed(object).chooseBundle(currentModule);
	}

	@Override
	public boolean o_ValueWasStablyComputed (final AvailObject object)
	{
		return o_Traversed(object).valueWasStablyComputed();
	}

	@Override
	public void o_ValueWasStablyComputed (
		final AvailObject object,
		final boolean wasStablyComputed)
	{
		o_Traversed(object).valueWasStablyComputed(wasStablyComputed);
	}

	@Override
	public long o_UniqueId (final AvailObject object)
	{
		return o_Traversed(object).uniqueId();
	}

	@Override
	public A_Definition o_Definition (final AvailObject object)
	{
		return o_Traversed(object).definition();
	}

	@Override
	public String o_NameHighlightingPc (final AvailObject object)
	{
		return o_Traversed(object).nameHighlightingPc();
	}

	@Override
	public boolean o_SetIntersects (final AvailObject object, final A_Set otherSet)
	{
		return o_Traversed(object).setIntersects(otherSet);
	}

	@Override
	public void o_RemovePlanForDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		o_Traversed(object).removePlanForDefinition(definition);
	}

	@Override
	public A_Map o_DefinitionParsingPlans (final AvailObject object)
	{
		return o_Traversed(object).definitionParsingPlans();
	}

	@Override
	public boolean o_EqualsListNodeType (
		final AvailObject object,
		final A_Type listNodeType)
	{
		return o_Traversed(object).equalsListNodeType(listNodeType);
	}

	@Override
	public A_Type o_SubexpressionsTupleType (final AvailObject object)
	{
		return o_Traversed(object).subexpressionsTupleType();
	}

	@Override
	public A_Type o_TypeUnionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return o_Traversed(object).typeUnionOfListNodeType(aListNodeType);
	}

	@Override
	public A_BasicObject o_LazyTypeFilterTreePojo (final AvailObject object)
	{
		return o_Traversed(object).lazyTypeFilterTreePojo();
	}

	@Override
	public void o_AddPlanInProgress (
		final AvailObject object,
		final A_ParsingPlanInProgress planInProgress)
	{
		o_Traversed(object).addPlanInProgress(planInProgress);
	}

	@Override
	public A_Type o_ParsingSignature (final AvailObject object)
	{
		return o_Traversed(object).parsingSignature();
	}

	@Override
	public void o_RemovePlanInProgress (
		final AvailObject object, final A_ParsingPlanInProgress planInProgress)
	{
		o_Traversed(object).removePlanInProgress(planInProgress);
	}

	@Override
	public A_Set o_ModuleSemanticRestrictions (final AvailObject object)
	{
		return o_Traversed(object).moduleSemanticRestrictions();
	}

	@Override
	public A_Set o_ModuleGrammaticalRestrictions (final AvailObject object)
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
	public AvailObject o_FieldAt (
		final AvailObject object, final A_Atom field)
	{
		return o_Traversed(object).fieldAt(field);
	}

	@Override
	public A_BasicObject o_FieldAtPuttingCanDestroy (
		final AvailObject object,
		final A_Atom field,
		final A_BasicObject value,
		final boolean canDestroy)
	{
		return o_Traversed(object).fieldAtPuttingCanDestroy(
			field, value, canDestroy);
	}

	@Override
	public A_Type o_FieldTypeAt (
		final AvailObject object, final A_Atom field)
	{
		return o_Traversed(object).fieldTypeAt(field);
	}

	@Override
	public A_DefinitionParsingPlan o_ParsingPlan (final AvailObject object)
	{
		return o_Traversed(object).parsingPlan();
	}

	@Override
	public boolean o_CompareFromToWithIntTupleStartingAt (
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
	public boolean o_IsIntTuple (final AvailObject object)
	{
		return o_Traversed(object).isIntTuple();
	}

	@Override
	public boolean o_EqualsIntTuple (
		final AvailObject object, final A_Tuple anIntTuple)
	{
		return o_Traversed(object).equalsIntTuple(anIntTuple);
	}

	@Override
	public void o_AtomicAddToMap (
		final AvailObject object,
		final A_BasicObject key,
		final A_BasicObject value)
	throws VariableGetException, VariableSetException
	{
		o_Traversed(object).atomicAddToMap(key, value);
	}

	@Override
	public boolean o_VariableMapHasKey (
		final AvailObject object, final A_BasicObject key)
	throws VariableGetException
	{
		return o_Traversed(object).variableMapHasKey(key);
	}

	@Override
	public A_Method o_LexerMethod (final AvailObject object)
	{
		return o_Traversed(object).lexerMethod();
	}

	@Override
	public A_Function o_LexerFilterFunction (final AvailObject object)
	{
		return o_Traversed(object).lexerFilterFunction();
	}

	@Override
	public A_Function o_LexerBodyFunction (final AvailObject object)
	{
		return o_Traversed(object).lexerBodyFunction();
	}

	@Override
	public void o_SetLexer (
		final AvailObject object, final A_Lexer lexer)
	{
		o_Traversed(object).setLexer(lexer);
	}

	@Override
	public void o_AddLexer (
		final AvailObject object, final A_Lexer lexer)
	{
		o_Traversed(object).addLexer(lexer);
	}

	@Override
	public LexingState o_NextLexingState (
		final AvailObject object)
	{
		return o_Traversed(object).nextLexingState();
	}

	@Override
	public void o_SetNextLexingStateFromPrior (
		final AvailObject object, final LexingState priorLexingState)
	{
		o_Traversed(object).setNextLexingStateFromPrior(priorLexingState);
	}

	@Override
	public int o_TupleCodePointAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).tupleCodePointAt(index);
	}

	@Override @AvailMethod
	public A_Phrase o_OriginatingPhrase (final AvailObject object)
	{
		return o_Traversed(object).originatingPhrase();
	}

	@Override
	public boolean o_IsGlobal (final AvailObject object)
	{
		return o_Traversed(object).isGlobal();
	}

	@Override
	public A_Module o_GlobalModule (final AvailObject object)
	{
		return o_Traversed(object).globalModule();
	}

	@Override
	public A_String o_GlobalName (final AvailObject object)
	{
		return o_Traversed(object).globalName();
	}

	@Override
	public LexicalScanner o_CreateLexicalScanner (final AvailObject object)
	{
		return o_Traversed(object).createLexicalScanner();
	}

	@Override
	public A_Lexer o_Lexer (final AvailObject object)
	{
		return o_Traversed(object).lexer();
	}

	@Override
	public void o_SuspendingFunction (
		final AvailObject object,
		final A_Function suspendingFunction)
	{
		o_Traversed(object).suspendingFunction(suspendingFunction);
	}

	@Override
	public A_Function o_SuspendingFunction (final AvailObject object)
	{
		return o_Traversed(object).suspendingFunction();
	}

	@Override
	public boolean o_IsBackwardJump (final AvailObject object)
	{
		return o_Traversed(object).isBackwardJump();
	}

	@Override
	public A_BundleTree o_LatestBackwardJump (
		final AvailObject object)
	{
		return o_Traversed(object).latestBackwardJump();
	}

	@Override
	public boolean o_HasBackwardJump (final AvailObject object)
	{
		return o_Traversed(object).hasBackwardJump();
	}

	@Override
	public boolean o_IsSourceOfCycle (final AvailObject object)
	{
		return o_Traversed(object).isSourceOfCycle();
	}

	@Override
	public void o_IsSourceOfCycle (
		final AvailObject object,
		final boolean isSourceOfCycle)
	{
		o_Traversed(object).isSourceOfCycle(isSourceOfCycle);
	}

	@Override
	public StringBuilder o_DebugLog (final AvailObject object)
	{
		return o_Traversed(object).debugLog();
	}

	@Override
	public int o_NumConstants (final AvailObject object)
	{
		return o_Traversed(object).numConstants();
	}

	@Override
	public A_Type o_ConstantTypeAt (final AvailObject object, final int index)
	{
		return o_Traversed(object).constantTypeAt(index);
	}

	@Override
	public Statistic o_ReturnerCheckStat (final AvailObject object)
	{
		return o_Traversed(object).returnerCheckStat();
	}

	@Override
	public Statistic o_ReturneeCheckStat (final AvailObject object)
	{
		return o_Traversed(object).returneeCheckStat();
	}

	@Override
	public int o_NumNybbles (final AvailObject object)
	{
		return o_Traversed(object).numNybbles();
	}

	@Override
	public A_Tuple o_LineNumberEncodedDeltas (final AvailObject object)
	{
		return o_Traversed(object).lineNumberEncodedDeltas();
	}

	@Override
	public int o_CurrentLineNumber (final AvailObject object)
	{
		return o_Traversed(object).currentLineNumber();
	}

	@Override
	public A_Type o_FiberResultType (final AvailObject object)
	{
		return o_Traversed(object).fiberResultType();
	}

	@Override
	public LookupTree<A_Definition, A_Tuple> o_TestingTree (
		final AvailObject object)
	{
		return o_Traversed(object).testingTree();
	}

	@Override
	public void o_ForEach (
		final AvailObject object,
		final BiConsumer<? super AvailObject, ? super AvailObject> action)
	{
		o_Traversed(object).forEach(action);
	}

	@Override
	public void o_ForEachInMapBin (
		final AvailObject object,
		final BiConsumer<? super AvailObject, ? super AvailObject> action)
	{
		o_Traversed(object).forEachInMapBin(action);
	}

	@Override
	public void o_SetSuccessAndFailureContinuations (
		final AvailObject object,
		final Continuation1NotNull<AvailObject> onSuccess,
		final Continuation1NotNull<Throwable> onFailure)
	{
		o_Traversed(object).setSuccessAndFailureContinuations(
			onSuccess, onFailure);
	}

	@Override
	public void o_ClearLexingState (final AvailObject object)
	{
		o_Traversed(object).clearLexingState();
	}

	@Override
	public A_Phrase o_LastExpression (final AvailObject object)
	{
		return o_Traversed(object).lastExpression();
	}

	@Override
	public AvailObject o_RegisterDump (final AvailObject object)
	{
		return o_Traversed(object).registerDump();
	}
}
