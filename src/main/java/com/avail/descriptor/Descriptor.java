/*
 * Descriptor.java
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
import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.scanning.LexingState;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.FiberDescriptor.GeneralFlag;
import com.avail.descriptor.FiberDescriptor.InterruptRequestFlag;
import com.avail.descriptor.FiberDescriptor.SynchronizationFlag;
import com.avail.descriptor.FiberDescriptor.TraceFlag;
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
import com.avail.descriptor.numbers.IntegerDescriptor;
import com.avail.descriptor.parsing.A_DefinitionParsingPlan;
import com.avail.descriptor.parsing.A_Lexer;
import com.avail.descriptor.parsing.A_ParsingPlanInProgress;
import com.avail.descriptor.phrases.A_Phrase;
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind;
import com.avail.descriptor.representation.IntegerSlotsEnum;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.representation.ObjectSlotsEnum;
import com.avail.descriptor.sets.A_Set;
import com.avail.descriptor.sets.SetDescriptor.SetIterator;
import com.avail.descriptor.tokens.A_Token;
import com.avail.descriptor.tokens.TokenDescriptor.TokenType;
import com.avail.descriptor.tuples.A_String;
import com.avail.descriptor.tuples.A_Tuple;
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
import com.avail.utility.visitor.BeImmutableSubobjectVisitor;
import com.avail.utility.visitor.BeSharedSubobjectVisitor;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.avail.descriptor.sets.LinearSetBinDescriptor.createLinearSetBinPair;
import static com.avail.descriptor.sets.LinearSetBinDescriptor.emptyLinearSetBin;
import static java.lang.String.format;

/**
 * This is the primary subclass of {@linkplain AbstractDescriptor}. It has the
 * sibling IndirectionDescriptor.
 *
 * <p>
 * When a new method is added in a subclass, it should be added with the
 * {@linkplain Override @Override} annotation. That way the project will
 * indicate errors until an abstract declaration is added to
 * {@linkplain AbstractDescriptor}, a default implementation is added to
 * {@code Descriptor}, and a redirecting implementation is added to
 * {@linkplain IndirectionDescriptor}. Any code attempting to send the
 * corresponding message to an {@linkplain AvailObject} will also indicate a
 * problem until a suitable implementation is added to AvailObject.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class Descriptor
extends AbstractDescriptor
{
	/**
	 * Construct a new {@code Descriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param typeTag
	 *        The {@link TypeTag} to embed in the new descriptor.
	 * @param objectSlotsEnumClass
	 *        The Java {@link Class} which is a subclass of {@link
	 *        ObjectSlotsEnum} and defines this object's object slots
	 *        layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *        The Java {@link Class} which is a subclass of {@link
	 *        IntegerSlotsEnum} and defines this object's object slots
	 *        layout, or null if there are no integer slots.
	 */
	protected Descriptor (
		final Mutability mutability,
		final TypeTag typeTag,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	/**
	 * A special enumeration used to visit all object slots within an instance
	 * of the receiver.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	enum FakeObjectSlotsForScanning implements ObjectSlotsEnumJava
	{
		/**
		 * An indexed object slot that makes it easy to visit all object slots.
		 */
		ALL_OBJECT_SLOTS_;
	}

	@Override
	protected boolean o_AcceptsArgTypesFromFunctionType (
		final AvailObject object,
		final A_Type functionType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<? extends A_BasicObject> argValues)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final A_Tuple argTypes)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final A_Tuple arguments)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddAncestors (
		final AvailObject object,
		final A_Set moreAncestors)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddEntryPoint (
		final AvailObject object,
		final A_String stringName,
		final A_Atom trueName)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddUnloadFunction (
		final AvailObject object,
		final A_Function unloadFunction)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AdjustPcAndStackp (
		final AvailObject object,
		final int pc,
		final int stackp)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_AllAncestors (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_ArgumentRestrictionSets (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_String o_AtomName (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddDefinitionParsingPlan (
		final AvailObject object, final A_DefinitionParsingPlan plan)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddImportedName (final AvailObject object, final A_Atom trueName)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddImportedNames (
		final AvailObject object,
		final A_Set trueNames)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddPrivateName (final AvailObject object, final A_Atom trueName)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_ArgOrLocalOrStackAt (
		final AvailObject object,
		final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ArgOrLocalOrStackAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected String o_AsNativeString (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_AsSet (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_AsTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_ArgumentsTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Atom o_ApparentSendName (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_AllParsingPlansInProgress (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_ArgsTupleType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction restrictionSignature)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddSealedArgumentsType (
		final AvailObject object,
		final A_Tuple typeTuple)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddConstantBinding (
		final AvailObject object,
		final A_String name,
		final A_Variable constantBinding)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddVariableBinding (
		final AvailObject object,
		final A_String name,
		final A_Variable variableBinding)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_AddToDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_AddToFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected BigInteger o_AsBigInteger (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_ArgumentsListNode (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddSeal (
		final AvailObject object,
		final A_Atom methodName,
		final A_Tuple argumentTypes)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	@Nullable
	protected AvailLoader o_AvailLoader (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	protected void o_AvailLoader (
		final AvailObject object,
		@Nullable final AvailLoader loader)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddWriteReactor (
		final AvailObject object,
		final A_Atom key,
		final VariableAccessReactor reactor)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddPrivateNames (
		final AvailObject object,
		final A_Set trueNames)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_MethodAddDefinition (
		final AvailObject object,
		final A_Definition definition) throws SignatureException
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_AddToInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_AddToIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ModuleAddGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ModuleAddDefinition (
		final AvailObject object,
		final A_BasicObject definition)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_IntroduceNewName (final AvailObject object, final A_Atom trueName)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_BinElementAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_BreakpointBlock (final AvailObject object, final AvailObject value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_BundleTree o_BuildFilteredBundleTree (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_CompareFromToWithAnyTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aTuple,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_CompareFromToWithByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_String aByteString,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_CompareFromToWithByteTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteTuple,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_CompareFromToWithIntegerIntervalTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anIntegerIntervalTuple,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_CompareFromToWithSmallIntegerIntervalTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aSmallIntegerIntervalTuple,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_CompareFromToWithRepeatedElementTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aRepeatedElementTuple,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_CompareFromToWithNybbleTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aNybbleTuple,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_CompareFromToWithObjectTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anObjectTuple,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_CompareFromToWithTwoByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_String aTwoByteString,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_ConcatenateTuplesCanDestroy (
		final AvailObject object,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_ConstantTypeAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_Continuation (final AvailObject object, final A_Continuation value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<TypeRestriction> argRestrictions)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected StringBuilder o_DebugLog (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_DivideCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_DivideIntoInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_DivideIntoIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ExecutionState (
		final AvailObject object, final ExecutionState value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected byte o_ExtractNybbleFromTupleAt (
		final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected List<A_Definition> o_FilterByTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_HasElement (
		final AvailObject object,
		final A_BasicObject elementObject)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_HashFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_HashOrZero (final AvailObject object, final int value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_HasKey (final AvailObject object, final A_BasicObject keyObject)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected List<A_Definition> o_DefinitionsAtOrBelow (
		final AvailObject object,
		final List<TypeRestriction> argRestrictions)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IncludesDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_SetInterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_CountdownToReoptimize (final AvailObject object, final int value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSubsetOf (final AvailObject object, final A_Set another)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfFiberType (
		final AvailObject object,
		final A_Type aType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfEnumerationType (
		final AvailObject object,
		final A_BasicObject anEnumerationType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_LevelTwoChunkOffset (
		final AvailObject object,
		final L2Chunk chunk,
		final int offset)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_LiteralAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_LocalTypeAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Definition o_LookupByTypesFromTuple (
		final AvailObject object,
		final A_Tuple argumentTypeTuple)
	throws MethodDefinitionException
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Definition o_LookupByValuesFromList (
		final AvailObject object,
		final List<? extends A_BasicObject> argumentList)
	throws MethodDefinitionException
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_MapAt (
		final AvailObject object,
		final A_BasicObject keyObject)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_MapAtPuttingCanDestroy (
		final AvailObject object,
		final A_BasicObject keyObject,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_MapWithoutKeyCanDestroy (
		final AvailObject object,
		final A_BasicObject keyObject,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_MinusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_MultiplyByInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_MultiplyByIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_NameVisible (final AvailObject object, final A_Atom trueName)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_OptionallyNilOuterVar (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_OuterTypeAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_OuterVarAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_OuterVarAtPut (
		final AvailObject object,
		final int index,
		final AvailObject value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_PlusCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_Priority (final AvailObject object, final int value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_FiberGlobals (final AvailObject object, final A_Map value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected short o_RawByteForCharacterAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_RawShortForCharacterAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_RawShortForCharacterAtPut (
		final AvailObject object,
		final int index,
		final int anInteger)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_RawSignedIntegerAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_RawSignedIntegerAtPut (
		final AvailObject object,
		final int index,
		final int value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected long o_RawUnsignedIntegerAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_RawUnsignedIntegerAtPut (
		final AvailObject object,
		final int index,
		final int value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_RemoveDependentChunk (
		final AvailObject object,
		final L2Chunk chunk)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_RemoveFrom (
		final AvailObject object,
		final AvailLoader loader,
		final Continuation0 afterRemoval)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_RemoveDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_RemoveGrammaticalRestriction (
		final AvailObject object,
		final A_GrammaticalRestriction obsoleteRestriction)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ResolveForward (
		final AvailObject object,
		final A_BasicObject forwardDefinition)
	{
		throw unsupportedOperationException();
	}

	/**
	 * Visit all of the object's object slots, passing the parent and child
	 * objects to the provided visitor.
	 *
	 * @param object
	 *        The object to scan.
	 * @param visitor
	 *        The visitor to invoke.
	 */
	@Override
	protected void o_ScanSubobjects (
		final AvailObject object,
		final AvailSubobjectVisitor visitor)
	{
		final int limit = object.objectSlotsCount();
		for (int i = 1; i <= limit; i++)
		{
			final AvailObject child = object.slot(
				FakeObjectSlotsForScanning.ALL_OBJECT_SLOTS_,
				i);
			final AvailObject replacementChild = visitor.invoke(child);
			if (replacementChild != child)
			{
				object.writeBackSlot(
					FakeObjectSlotsForScanning.ALL_OBJECT_SLOTS_,
					i,
					replacementChild);
			}
		}
	}

	@Override
	protected A_Set o_SetIntersectionCanDestroy (
		final AvailObject object,
		final A_Set otherSet,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_SetMinusCanDestroy (
		final AvailObject object,
		final A_Set otherSet,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_SetUnionCanDestroy (
		final AvailObject object,
		final A_Set otherSet,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_SetValue (final AvailObject object, final A_BasicObject newValue)
	throws VariableSetException
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_SetValueNoCheck (
		final AvailObject object,
		final A_BasicObject newValue)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_SetWithElementCanDestroy (
		final AvailObject object,
		final A_BasicObject newElementObject,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_SetWithoutElementCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObjectToExclude,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_StackAt (final AvailObject object, final int slotIndex)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_SetStartingChunkAndReoptimizationCountdown (
		final AvailObject object,
		final L2Chunk chunk,
		final long countdown)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_SubtractFromInfinityCanDestroy (
		final AvailObject object,
		final Sign sign,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_SubtractFromIntegerCanDestroy (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_TimesCanDestroy (
		final AvailObject object,
		final A_Number aNumber,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_TrueNamesForStringName (
		final AvailObject object,
		final A_String stringName)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_TupleReverse (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_TupleIntAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeAtIndex (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersection (final AvailObject object, final A_Type another)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnion (final AvailObject object, final A_Type another)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_Value (final AvailObject object, final A_BasicObject value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_BitsPerEntry (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Function o_BodyBlock (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_BodySignature (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_BasicObject o_BreakpointBlock (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Continuation o_Caller (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AtomicAddToMap (
		final AvailObject object,
		final A_BasicObject key,
		final A_BasicObject value)
	throws VariableGetException, VariableSetException
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_VariableMapHasKey (
		final AvailObject object,
		final A_BasicObject key)
	throws VariableGetException
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ClearValue (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Function o_Function (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_FunctionType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_RawFunction o_Code (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_CodePoint (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_LazyComplete (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_ConstantBindings (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_ContentType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Continuation o_Continuation (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_CopyAsMutableIntTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_CopyAsMutableObjectTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_DefaultType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Continuation o_EnsureMutable (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	ExecutionState o_ExecutionState (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_Expand (
		final AvailObject object,
		final A_Module module)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_ExtractBoolean (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected short o_ExtractUnsignedByte (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected double o_ExtractDouble (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected float o_ExtractFloat (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_ExtractInt (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	/**
	 * Extract a 64-bit signed Java {@code long} from the specified Avail
	 * {@linkplain IntegerDescriptor integer}.
	 *
	 * @param object
	 *        An {@link AvailObject}.
	 * @return A 64-bit signed Java {@code long}
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	protected long o_ExtractLong (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected byte o_ExtractNybble (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_FieldMap (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_FieldTypeMap (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_GetValue (final AvailObject object)
	throws VariableGetException
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_HashOrZero (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_HasGrammaticalRestrictions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_DefinitionsTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_LazyIncomplete (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_DecrementCountdownToReoptimize (
		final AvailObject object,
		final Continuation1NotNull<Boolean> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsAbstract (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsAbstractDefinition (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsFinite (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsForwardDefinition (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	protected boolean o_IsInstanceMeta (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsMethodDefinition (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsPositive (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfBottom (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_KeysAsSet (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_KeyType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected L2Chunk o_LevelTwoChunk (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_LevelTwoOffset (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_Literal (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_LowerBound (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_LowerInclusive (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_MapSize (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_MaxStackDepth (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Atom o_Message (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_MessageParts (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_MethodDefinitions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_ImportedNames (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_NewNames (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_NumArgs (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_NumConstants (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_NumSlots (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_NumLiterals (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_NumLocals (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_NumOuters (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_NumOuterVars (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_Nybbles (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_BasicObject o_Parent (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_Pc (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_PrimitiveNumber (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_Priority (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_PrivateNames (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_FiberGlobals (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_GrammaticalRestrictions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_ReturnType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_SetSize (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_SizeRange (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_LazyActions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_Stackp (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_Start (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected L2Chunk o_StartingChunk (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_String o_String (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected TokenType o_TokenType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_TrimExcessInts (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_TupleSize (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_TypeTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_UpperBound (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_UpperInclusive (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_Value (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_ValuesAsTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_ValueType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_VariableBindings (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_VisibleNames (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	public boolean o_Equals (
		final AvailObject object, final A_BasicObject another)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_EqualsAnyTuple (final AvailObject object, final A_Tuple aTuple)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsByteString (
		final AvailObject object,
		final A_String aString)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsByteTuple (final AvailObject object, final A_Tuple aTuple)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsCharacterWithCodePoint (
		final AvailObject object,
		final int otherCodePoint)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsFunction (
		final AvailObject object,
		final A_Function aFunction)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsIntegerIntervalTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsIntTuple (
		final AvailObject object,
		final A_Tuple anIntTuple)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsSmallIntegerIntervalTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsRepeatedElementTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsCompiledCode (
		final AvailObject object,
		final A_RawFunction aCompiledCode)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsVariable (
		final AvailObject object,
		final AvailObject aVariable)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsVariableType (final AvailObject object, final A_Type aType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsContinuation (
		final AvailObject object,
		final A_Continuation aContinuation)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsCompiledCodeType (
		final AvailObject object,
		final A_Type aType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsDouble (final AvailObject object, final double aDouble)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsFloat (final AvailObject object, final float aFloat)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsInfinity (final AvailObject object, final Sign sign)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsInteger (
		final AvailObject object,
		final AvailObject anAvailInteger)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsIntegerRangeType (
		final AvailObject object,
		final A_Type another)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsMap (final AvailObject object, final A_Map aMap)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsMapType (final AvailObject object, final A_Type aMapType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsNybbleTuple (final AvailObject object, final A_Tuple aTuple)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsObject (
		final AvailObject object,
		final AvailObject anObject)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsObjectTuple (final AvailObject object, final A_Tuple aTuple)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsPojo (final AvailObject object, final AvailObject aPojo)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsPrimitiveType (
		final AvailObject object,
		final A_Type aPrimitiveType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsRawPojoFor (
		final AvailObject object,
		final AvailObject otherRawPojo,
		final @Nullable Object aRawPojo)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsReverseTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsSet (final AvailObject object, final A_Set aSet)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsSetType (final AvailObject object, final A_Type aSetType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsTwoByteString (
		final AvailObject object,
		final A_String aTwoByteString)
	{
		return false;
	}

	@Override
	protected boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final A_BasicObject anotherObject)
	{
		// Given two objects that are known to be equal, is the first one in a
		// better form (more compact, more efficient, older generation) than the
		// second one?

		final int objectCost = object.objectSlotsCount()
			+ object.integerSlotsCount();
		final int anotherCost = anotherObject.objectSlotsCount()
			+ anotherObject.integerSlotsCount();
		return objectCost < anotherCost;
	}

	@Override
	protected int o_RepresentationCostOfTupleType (
		final AvailObject object)
	{
		// Given two objects that are known to be equal, the second of which is
		// in the form of a tuple type, is the first one in a better form than
		// the second one?

		// Explanation: This must be called with a tuple type as the second
		// argument, but the two arguments must also be equal. All alternative
		// implementations of tuple types should re-implement this method.
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsInstanceOfKind (
		final AvailObject object, final A_Type aType)
	{
		return object.kind().isSubtypeOf(aType);
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		// Answer a 32-bit long that is always the same for equal objects, but
		// statistically different for different objects.

		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsFunction (final AvailObject object)
	{
		return false;
	}

	@Override
	protected AvailObject o_MakeImmutable (final AvailObject object)
	{
		// Make the object immutable. If I was mutable I have to scan my
		// children and make them immutable as well (recursively down to
		// immutable descendants).
		if (isMutable())
		{
			object.setDescriptor(object.descriptor().immutable());
			object.makeSubobjectsImmutable();
		}
		return object;
	}

	@Override
	protected AvailObject o_MakeShared (final AvailObject object)
	{
		// Make the object shared. If I wasn't shared I have to scan my
		// children and make them shared as well (recursively down to
		// shared descendants).
		if (!isShared())
		{
			object.setDescriptor(object.descriptor().shared());
			object.makeSubobjectsShared();
		}
		return object;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Make my subobjects be immutable. Don't change my own mutability state.
	 * Also, ignore my mutability state, as it should be tested (and sometimes
	 * set preemptively to immutable) prior to invoking this method.
	 * </p>
	 * @return
	 */
	@Override
	protected final AvailObject o_MakeSubobjectsImmutable (final AvailObject object)
	{
		object.scanSubobjects(BeImmutableSubobjectVisitor.INSTANCE);
		return object;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Make my subobjects be shared. Don't change my own mutability state. Also,
	 * ignore my mutability state, as it should be tested (and sometimes set
	 * preemptively to shared) prior to invoking this method.
	 * </p>
	 */
	@Override
	protected final void o_MakeSubobjectsShared (final AvailObject object)
	{
		object.scanSubobjects(BeSharedSubobjectVisitor.INSTANCE);
	}

	@Override
	protected A_Type o_Kind (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsBoolean (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsByteTuple (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsCharacter (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsIntTuple (final AvailObject object)
	{
		return false;
	}

	/**
	 * Is the specified {@link AvailObject} an Avail string?
	 *
	 * @param object
	 *        An {@link AvailObject}.
	 * @return {@code true} if the argument is an Avail string, {@code false}
	 * otherwise.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	protected boolean o_IsString (final AvailObject object)
	{
		return false;
	}

	@Override
	protected AvailObject o_Traversed (final AvailObject object)
	{
		// Overridden in IndirectionDescriptor to skip over indirections.

		return object;
	}

	@Override
	protected boolean o_IsMap (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsUnsignedByte (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsNybble (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsSet (final AvailObject object)
	{
		return false;
	}

	@Override
	protected A_BasicObject o_SetBinAddingElementHashLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		// Add the given element to this bin, potentially modifying it if
		// canDestroy and it's mutable. Answer the new bin. Note that the
		// client is responsible for marking elementObject as immutable if
		// another reference exists. In particular, the object is masquerading
		// as a bin of size one.
		if (object.equals(elementObject))
		{
			return object;
		}
		if (!canDestroy)
		{
			object.makeImmutable();
			elementObject.makeImmutable();
		}
		// Create a linear bin with two slots.
		return createLinearSetBinPair(myLevel, object, elementObject);
	}

	@Override
	protected boolean o_BinHasElementWithHash (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash)
	{
		// Elements are treated as bins to save space, since bins are not
		// entirely first-class objects (i.e., they can't be added to sets.

		return object.equals(elementObject);
	}

	/**
	 * Remove elementObject from the bin object, if present. Answer the
	 * resulting bin. The bin may be modified if it's mutable and canDestroy. In
	 * particular, an element is masquerading as a bin of size one, so the
	 * answer must be either the object or nil (to indicate a size zero bin).
	 *
	 * @param object
	 *        The set bin from which to remove the element.
	 * @param elementObject
	 *        The element to remove.
	 * @param elementObjectHash
	 *        The already-computed hash of the element to remove
	 * @param canDestroy
	 *        Whether this set bin can be destroyed or reused by this operation
	 *        if it's also mutable.
	 * @return A set bin like the given object, but without the given
	 *         elementObject, if it was present.
	 */
	@Override
	protected AvailObject o_BinRemoveElementHashLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		if (object.equals(elementObject))
		{
			return emptyLinearSetBin(myLevel);
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
	 *
	 * @param object
	 *        The set bin, or single value in this case, to test for being
	 *        within the given set.
	 * @param potentialSuperset
	 *        The set inside which to look for the given object.
	 * @return Whether the object (acting as a singleton bin) was in the set.
	 */
	@Override
	protected boolean o_IsBinSubsetOf (
		final AvailObject object,
		final A_Set potentialSuperset)
	{
		return potentialSuperset.hasElement(object);
	}

	@Override
	protected int o_SetBinHash (final AvailObject object)
	{
		// An object masquerading as a size one bin has a setBinHash which is
		// the sum of the elements' hashes, which in this case is just the
		// object's hash.
		return object.hash();
	}

	@Override
	protected int o_SetBinSize (final AvailObject object)
	{
		// Answer how many elements this bin contains. By default, the object
		// acts as a bin of size one.
		return 1;
	}

	@Override
	protected boolean o_IsTuple (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsAtom (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsExtendedInteger (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsIntegerRangeType (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsMapType (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsSetType (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsTupleType (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsType (final AvailObject object)
	{
		return false;
	}

	/**
	 * Answer an {@linkplain Iterator iterator} suitable for traversing the
	 * elements of the {@linkplain AvailObject object} with a Java
	 * <em>foreach</em> construct.
	 *
	 * @param object
	 *        An {@link AvailObject}.
	 * @return An {@linkplain Iterator iterator}.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	@Override
	protected IteratorNotNull<AvailObject> o_Iterator (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected Spliterator<AvailObject> o_Spliterator (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected Stream<AvailObject> o_Stream (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected Stream<AvailObject> o_ParallelStream (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_ParsingInstructions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_Expression (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_Variable (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_StatementsTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_ResultType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_NeededVariables (
		final AvailObject object,
		final A_Tuple neededVariables)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_NeededVariables (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	@Nullable
	protected Primitive o_Primitive (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_DeclaredType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected DeclarationKind o_DeclarationKind (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_TypeExpression (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_InitializationExpression (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_LiteralObject (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Token o_Token (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_MarkerValue (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Bundle o_Bundle (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_ExpressionsTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_Declaration (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_ExpressionType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		throw unsupportedOperationException();
	}

	/**
	 * Map my children through the (destructive) transformation specified by
	 * aBlock.
	 */
	@Override
	protected void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		throw unsupportedOperationException();
	}

	/**
	 * Visit my child phrases with the action.
	 */
	@Override
	protected void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_RawFunction o_GenerateInModule (
		final AvailObject object,
		final A_Module module)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_CopyWith (
		final AvailObject object, final A_Phrase newPhrase)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_CopyConcatenating (
		final AvailObject object, final A_Phrase newListPhrase)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_IsLastUse (
		final AvailObject object, final boolean isLastUse)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsLastUse (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsMacroDefinition (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_CopyMutablePhrase (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_BinUnionKind (final AvailObject object)
	{
		// Ordinary (non-bin, non-void) objects act as set bins of size one.
		return object.kind();
	}

	@Override
	protected A_Phrase o_OutputPhrase (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_Statements (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_FlattenStatementsInto (
		final AvailObject object,
		final List<A_Phrase> accumulatedStatements)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_LineNumber (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSetBin (final AvailObject object)
	{
		return false;
	}

	@Override
	protected MapIterable o_MapIterable (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_DeclaredExceptions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsInt (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsLong (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsInstanceTypeFor (
		final AvailObject object,
		final AvailObject anObject)
	{
		return false;
	}

	@Override
	protected A_Set o_Instances (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_EqualsEnumerationWithSet (
		final AvailObject object,
		final A_Set set)
	{
		return false;
	}

	@Override
	protected boolean o_IsEnumeration (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsInstanceOf (final AvailObject object, final A_Type aType)
	{
		if (aType.isEnumeration())
		{
			return aType.enumerationIncludesInstance(object);
		}
		return object.isInstanceOfKind(aType);
	}

	@Override
	protected boolean o_EnumerationIncludesInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_ComputeSuperkind (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_SetAtomProperty (
		final AvailObject object,
		final A_Atom key,
		final A_BasicObject value)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_GetAtomProperty (final AvailObject object, final A_Atom key)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_EqualsEnumerationType (
		final AvailObject object,
		final A_BasicObject another)
	{
		return false;
	}

	@Override
	protected A_Type o_ReadType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_WriteType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_Versions (final AvailObject object, final A_Set versionStrings)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_Versions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected PhraseKind o_PhraseKind (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_PhraseKindIsUnder (
		final AvailObject object,
		final PhraseKind expectedPhraseKind)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsRawPojo (final AvailObject object)
	{
		return false;
	}

	@Override
	protected void o_RemoveSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction restriction)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_SemanticRestrictions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_RemoveSealedArgumentsType (
		final AvailObject object,
		final A_Tuple typeTuple)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_SealedArgumentsTypesTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ModuleAddSemanticRestriction (
		final AvailObject object,
		final A_SemanticRestriction semanticRestriction)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsMethodEmpty (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsPojoSelfType (final AvailObject object)
	{
		return false;
	}

	@Override
	protected A_Type o_PojoSelfType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_JavaClass (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsUnsignedShort (final AvailObject object)
	{
		return false;
	}

	@Override
	protected int o_ExtractUnsignedShort (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsFloat (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsDouble (final AvailObject object)
	{
		return false;
	}

	@Override
	protected AvailObject o_RawPojo (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsPojo (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsPojoType (final AvailObject object)
	{
		return false;
	}

	@Override
	protected Order o_NumericCompare (final AvailObject object, final A_Number another)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected Order o_NumericCompareToInfinity (final AvailObject object, final Sign sign)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected Order o_NumericCompareToDouble (
		final AvailObject object,
		final double aDouble)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected Order o_NumericCompareToInteger (
		final AvailObject object,
		final AvailObject anInteger)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_LazyPrefilterMap (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		throw unsupportedOperationException();
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
		throw unsupportedOperationException();
	}

	@Override
	protected A_MapBin o_MapBinRemoveKeyHashCanDestroy (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_MapBinKeyUnionKind (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_MapBinValueUnionKind (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsHashedMapBin (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected @Nullable AvailObject o_MapBinAtHash (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_MapBinKeysHash (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_MapBinSize (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_MapBinValuesHash (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Module o_IssuingModule (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsPojoFusedType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfPojoBottomType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_EqualsPojoBottomType (final AvailObject object)
	{
		return false;
	}

	@Override
	protected AvailObject o_JavaAncestors (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsPojoArrayType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected @Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		// Treat AvailObjects as opaque for most purposes. Pass them to Java
		// unmarshaled, but made shared.
		return object.makeShared();
	}

	@Override
	protected A_Map o_TypeVariables (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_EqualsPojoField (
		final AvailObject object,
		final AvailObject field,
		final AvailObject receiver)
	{
		return false;
	}

	@Override
	protected boolean o_IsSignedByte (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSignedShort (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected byte o_ExtractSignedByte (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected short o_ExtractSignedShort (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_EqualsEqualityRawPojo (
		final AvailObject object,
		final AvailObject otherEqualityRawPojo,
		final @Nullable Object otherJavaObject)
	{
		return false;
	}

	@Override
	protected @Nullable <T> T o_JavaObject (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_LazyIncompleteCaseInsensitive (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_String o_LowerCaseString (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_InstanceCount (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected long o_TotalInvocations (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_TallyInvocation (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_FieldTypeTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_FieldTuple (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_LiteralType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeIntersectionOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsTokenType (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsLiteralTokenType (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsLiteralToken (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsSupertypeOfTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_EqualsTokenType (
		final AvailObject object,
		final A_Type aTokenType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsObjectType (
		final AvailObject object,
		final AvailObject anObjectType)
	{
		return false;
	}

	@Override
	protected boolean o_EqualsToken (final AvailObject object, final A_Token aToken)
	{
		return false;
	}

	@Override
	protected A_Number o_BitwiseAnd (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_BitwiseOr (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_BitwiseXor (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_Instance (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_SetMethodName (final AvailObject object, final A_String methodName)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_StartingLineNumber (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_OriginatingPhrase (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Module o_Module (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_String o_MethodName (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected String o_NameForDebugger (final AvailObject object)
	{
		String typeName = getClass().getSimpleName();
		if (typeName.endsWith("Descriptor"))
		{
			typeName = typeName.substring(0, typeName.length() - 10);
		}
		if (isMutable())
		{
			typeName += "\u2133";
		}
		if (object.showValueInNameForDebugger())
		{
			return format("(%s) = %s", typeName, object);
		}
		return format("(%s)", typeName);
	}

	@Override
	protected boolean o_BinElementsAreAllInstancesOfKind (
		final AvailObject object,
		final A_Type kind)
	{
		// Actual bins (instances of SetBinDescriptor's subclasses) and nil will
		// override this, but single non-null values act as a singleton bin.
		return object.isInstanceOfKind(kind);
	}

	@Override
	protected boolean o_SetElementsAreAllInstancesOfKind (
		final AvailObject object,
		final AvailObject kind)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected MapIterable o_MapBinIterable (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_RangeIncludesInt (final AvailObject object, final int anInt)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Number o_BitShiftLeftTruncatingToBits (
		final AvailObject object,
		final A_Number shiftFactor,
		final A_Number truncationBits,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected SetIterator o_SetBinIterator (final AvailObject object)
	{
		// By default an object acts like a bin of size one.
		return new SetIterator()
		{
			/** Whether there are more elements. */
			private boolean hasNext = true;

			@Override
			public AvailObject next ()
			{
				if (!hasNext)
				{
					throw new NoSuchElementException();
				}
				hasNext = false;
				return object;
			}

			@Override
			public boolean hasNext ()
			{
				return hasNext;
			}
		};
	}

	@Override
	protected A_Number o_BitShift (
		final AvailObject object,
		final A_Number shiftFactor,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return false;
	}

	@Override
	protected A_Phrase o_StripMacro (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Method o_DefinitionMethod (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_PrefixFunctions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_EqualsByteArrayTuple (
		final AvailObject object,
		final A_Tuple aByteArrayTuple)
	{
		return false;
	}

	@Override
	protected boolean o_CompareFromToWithByteArrayTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteArrayTuple,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected byte[] o_ByteArray (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsByteArrayTuple (final AvailObject object)
	{
		return false;
	}

	@Override
	protected void o_UpdateForNewGrammaticalRestriction (
		final AvailObject object,
		final A_ParsingPlanInProgress planInProgress,
		final Deque<Pair<A_BundleTree, A_ParsingPlanInProgress>> treesToVisit)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_Lock (final AvailObject object, final Continuation0 critical)
	{
		// Only bother to acquire the monitor if it's shared.
		if (isShared())
		{
			synchronized (object)
			{
				critical.value();
			}
		}
		else
		{
			critical.value();
		}
	}

	@Override
	<T> T o_Lock (final AvailObject object, final Supplier<T> supplier)
	{
		// Only bother to acquire the monitor if it's shared.
		if (isShared())
		{
			synchronized (object)
			{
				return supplier.get();
			}
		}
		else
		{
			return supplier.get();
		}
	}

	@Override
	protected A_String o_ModuleName (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Method o_BundleMethod (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_GetAndSetValue (
		final AvailObject object,
		final A_BasicObject newValue)
	throws VariableGetException, VariableSetException
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	protected boolean o_CompareAndSwapValues (
		final AvailObject object,
		final A_BasicObject reference,
		final A_BasicObject newValue)
	throws VariableGetException, VariableSetException
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	protected A_Number o_FetchAndAddValue (
		final AvailObject object,
		final A_Number addend)
	throws VariableGetException, VariableSetException
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	Continuation1NotNull<Throwable> o_FailureContinuation (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	Continuation1NotNull<AvailObject> o_ResultContinuation (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	boolean o_InterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	boolean o_GetAndClearInterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	boolean o_GetAndSetSynchronizationFlag (
		final AvailObject object,
		final SynchronizationFlag flag,
		final boolean newValue)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	AvailObject o_FiberResult (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	void o_FiberResult (final AvailObject object, final A_BasicObject result)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	A_Set o_JoiningFibers (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	@Nullable
	TimerTask o_WakeupTask (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	void o_WakeupTask (final AvailObject object, @Nullable final TimerTask task)
	{
		throw unsupportedOperationException();
	}

	@Override
	@AvailMethod
	void o_JoiningFibers (final AvailObject object, final A_Set joiners)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_HeritableFiberGlobals (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_HeritableFiberGlobals (final AvailObject object, final A_Map globals)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_GeneralFlag (final AvailObject object, final GeneralFlag flag)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_SetGeneralFlag (final AvailObject object, final GeneralFlag flag)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ClearGeneralFlag (final AvailObject object, final GeneralFlag flag)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected ByteBuffer o_ByteBuffer (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_EqualsByteBufferTuple (
		final AvailObject object,
		final A_Tuple aByteBufferTuple)
	{
		return false;
	}

	@Override
	protected boolean o_CompareFromToWithByteBufferTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteBufferTuple,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsByteBufferTuple (final AvailObject object)
	{
		return false;
	}

	@Override
	protected A_String o_FiberName (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_FiberNameSupplier (
		final AvailObject object,
		final Supplier<A_String> supplier)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_Bundles (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_MethodAddBundle (final AvailObject object, final A_Bundle bundle)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Module o_DefinitionModule (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_String o_DefinitionModuleName (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Bundle o_BundleOrCreate (final AvailObject object)
	throws MalformedMessageException
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Bundle o_BundleOrNil (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_EntryPoints (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Bundle o_RestrictedBundle (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_TreeTupleLevel (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_ChildCount (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_ChildAt (final AvailObject object, final int childIndex)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_ReplaceFirstChild (
		final AvailObject object,
		final A_Tuple newFirst)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsByteString (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsTwoByteString (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsIntegerIntervalTuple (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsSmallIntegerIntervalTuple (final AvailObject object)
	{
		return false;
	}

	@Override
	protected boolean o_IsRepeatedElementTuple (final AvailObject object)
	{
		return false;
	}

	@Override
	protected void o_RemoveWriteReactor (final AvailObject object, final A_Atom key)
	throws AvailException
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_TraceFlag (final AvailObject object, final TraceFlag flag)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_SetTraceFlag (final AvailObject object, final TraceFlag flag)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ClearTraceFlag (final AvailObject object, final TraceFlag flag)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_RecordVariableAccess (
		final AvailObject object,
		final A_Variable var,
		final boolean wasRead)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_VariablesReadBeforeWritten (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_VariablesWritten (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_ValidWriteReactorFunctions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Continuation o_ReplacingCaller (
		final AvailObject object,
		final A_Continuation newCaller)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_WhenContinuationIsAvailableDo (
		final AvailObject object,
		final Continuation1NotNull<A_Continuation> whenReified)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_GetAndClearReificationWaiters (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsBottom (
		final AvailObject object)
	{
		// Only types should be tested for being bottom.
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsVacuousType (
		final AvailObject object)
	{
		// Only types should be tested for being vacuous.
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsTop (
		final AvailObject object)
	{
		// Only types should be tested for being top.
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsAtomSpecial (
		final AvailObject object)
	{
		// Only atoms should be tested for being special.
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_HasValue (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_ExportedNames (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsInitializedWriteOnceVariable (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_TransferIntoByteBuffer (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final ByteBuffer outputByteBuffer)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsNumericallyIntegral (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected TextInterface o_TextInterface (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_TextInterface (
		final AvailObject object,
		final TextInterface textInterface)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_WriteTo (
		final AvailObject object,
		final JSONWriter writer)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		object.writeTo(writer);
	}

	@Override
	protected A_Type o_TypeIntersectionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_TupleOfTypesFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_List (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_Permutation (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_EmitAllValuesOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_SuperUnionType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_HasSuperCast (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_MacroDefinitionsTuple (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_LookupMacroByPhraseTuple (
		final AvailObject object,
		final A_Tuple argumentPhraseTuple)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_ExpressionAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_ExpressionsSize (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_ParsingPc (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsMacroSubstitutionNode (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_LastExpression (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected MessageSplitter o_MessageSplitter (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Phrase o_MacroOriginalSendNode (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_EqualsInt (final AvailObject object, final int theInt)
	{
		return false;
	}

	@Override
	protected A_Tuple o_Tokens (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Bundle o_ChooseBundle (
		final AvailObject object,
		final A_Module currentModule)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ValueWasStablyComputed (
		final AvailObject object,
		final boolean wasStablyComputed)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_ValueWasStablyComputed (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected long o_UniqueId (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Definition o_Definition (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected String o_NameHighlightingPc (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_SetIntersects (final AvailObject object, final A_Set otherSet)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_RemovePlanForDefinition (
		final AvailObject object,
		final A_Definition definition)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Map o_DefinitionParsingPlans (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_EqualsListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return false;
	}

	@Override
	protected A_Type o_SubexpressionsTupleType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSupertypeOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_TypeUnionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_BasicObject o_LazyTypeFilterTreePojo (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddPlanInProgress (
		final AvailObject object,
		final A_ParsingPlanInProgress planInProgress)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_ParsingSignature (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_RemovePlanInProgress (
		final AvailObject object, final A_ParsingPlanInProgress planInProgress)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_ModuleSemanticRestrictions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Set o_ModuleGrammaticalRestrictions (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	public TypeTag o_ComputeTypeTag (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_FieldAt (
		final AvailObject object, final A_Atom field)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_BasicObject o_FieldAtPuttingCanDestroy (
		final AvailObject object,
		final A_Atom field,
		final A_BasicObject value,
		final boolean canDestroy)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_FieldTypeAt (
		final AvailObject object, final A_Atom field)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_DefinitionParsingPlan o_ParsingPlan (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_CompareFromToWithIntTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anIntTuple,
		final int startIndex2)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Method o_LexerMethod (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Function o_LexerFilterFunction (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Function o_LexerBodyFunction (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_SetLexer (
		final AvailObject object,
		final A_Lexer lexer)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_AddLexer (
		final AvailObject object,
		final A_Lexer lexer)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected LexingState o_NextLexingState (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_SetNextLexingStateFromPrior (
		final AvailObject object,
		final LexingState priorLexingState)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_TupleCodePointAt (final AvailObject object, final int index)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsGlobal (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Module o_GlobalModule (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_String o_GlobalName (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected LexicalScanner o_CreateLexicalScanner (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Lexer o_Lexer (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_SuspendingFunction (
		final AvailObject object,
		final A_Function suspendingFunction)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Function o_SuspendingFunction (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsBackwardJump (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_BundleTree o_LatestBackwardJump (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_HasBackwardJump (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected boolean o_IsSourceOfCycle (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_IsSourceOfCycle (
		final AvailObject object,
		final boolean isSourceOfCycle)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected Statistic o_ReturnerCheckStat (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected Statistic o_ReturneeCheckStat (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_NumNybbles (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Tuple o_LineNumberEncodedDeltas (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected int o_CurrentLineNumber (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_FiberResultType (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected LookupTree<A_Definition, A_Tuple> o_TestingTree (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ForEach (
		final AvailObject object,
		final BiConsumer<? super AvailObject, ? super AvailObject> action)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ForEachInMapBin (
		final AvailObject object,
		final BiConsumer<? super AvailObject, ? super AvailObject> action)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_SetSuccessAndFailureContinuations (
		final AvailObject object,
		final Continuation1NotNull<AvailObject> onSuccess,
		final Continuation1NotNull<Throwable> onFailure)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected void o_ClearLexingState (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected AvailObject o_RegisterDump (
		final AvailObject object)
	{
		throw unsupportedOperationException();
	}
}
