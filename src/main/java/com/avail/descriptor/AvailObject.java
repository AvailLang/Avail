/*
 * AvailObject.java
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

import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.scanning.LexingState;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.AbstractNumberDescriptor.Order;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.CompiledCodeDescriptor.L1InstructionDecoder;
import com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind;
import com.avail.descriptor.FiberDescriptor.*;
import com.avail.descriptor.MapDescriptor.MapIterable;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.descriptor.SetDescriptor.SetIterator;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.descriptor.bundles.A_BundleTree;
import com.avail.descriptor.bundles.MessageBundleDescriptor;
import com.avail.descriptor.methods.A_Definition;
import com.avail.descriptor.methods.A_GrammaticalRestriction;
import com.avail.descriptor.methods.A_Method;
import com.avail.descriptor.methods.A_SemanticRestriction;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.descriptor.parsing.A_Lexer;
import com.avail.descriptor.parsing.A_ParsingPlanInProgress;
import com.avail.descriptor.parsing.A_Phrase;
import com.avail.descriptor.tuples.A_String;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.dispatch.LookupTree;
import com.avail.exceptions.ArithmeticException;
import com.avail.exceptions.*;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.AvailLoader.LexicalScanner;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.io.TextInterface;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.performance.Statistic;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.IteratorNotNull;
import com.avail.utility.Pair;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;
import com.avail.utility.visitor.AvailSubobjectVisitor;
import com.avail.utility.visitor.MarkUnreachableSubobjectVisitor;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.utility.Casts.cast;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.StackPrinter.trace;

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
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailObject
extends AvailObjectRepresentation
implements
	A_BasicObject,
	A_Atom,
	A_Bundle,
	A_BundleTree,
		A_Character,
		A_Continuation,
	A_Definition,
		A_DefinitionParsingPlan,
		A_Fiber,
		A_Function,
	A_GrammaticalRestriction,
	A_Lexer,
		A_Map,
		A_MapBin,
	A_Method,
		A_Module,
		A_Number,
	A_ParsingPlanInProgress,
	A_Phrase,
		A_RawFunction,
	A_SemanticRestriction,
		A_Set,
		A_Token,
	A_Tuple,
	A_String,
		A_Type,
		A_Variable
{
	/**
	 * A good multiplier for a multiplicative random generator.  This constant
	 * is a primitive element of the group (Z[2<sup>32</sup>],*), specifically
	 * 1664525, as taken from Knuth, <cite>The Art of Computer
	 * Programming</cite>, Vol. 2, 2<sup>nd</sup> ed., page 102, row 26. See
	 * also pages 19, 20, theorems B and C. The period of the cycle based on
	 * this multiplicative generator is 2<sup>30</sup>.
	 */
	public static final int multiplier = 1664525;

	/**
	 * Report a virtual machine problem.
	 *
	 * @param messagePattern A {@link String} describing the problem.
	 * @param arguments The arguments to insert into the {@code messagePattern}.
	 */
	public static void error (
		final String messagePattern,
		final Object... arguments)
	{
		throw new RuntimeException(String.format(messagePattern, arguments));
	}

	/**
	 * Recursively print the {@code AvailObject receiver} to the {@link
	 * StringBuilder} unless it is already present in the {@linkplain List
	 * recursion list}. Printing will begin at the specified indent level,
	 * measured in horizontal tab characters.
	 *
	 * <p>This operation exists primarily to provide useful representations of
	 * {@code AvailObject}s for Java-side debugging.</p>
	 *
	 * @param builder A {@link StringBuilder}.
	 * @param recursionMap An {@linkplain IdentityHashMap} whose keys are {@link
	 *                     A_BasicObject}s already visited during the recursive
	 *                     print.  The values are unused.
	 * @param indent The indent level, in horizontal tabs, at which the {@link
	 *               AvailObject} should be printed.
	 */
	@Override
	public void printOnAvoidingIndent (
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
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
			if (recursionMap.containsKey(this))
			{
				builder.append("**RECURSION**");
				return;
			}
			recursionMap.put(this, null);
			try
			{
				descriptor().printObjectOnAvoidingIndent(
					this,
					builder,
					recursionMap,
					indent);
			}
			finally
			{
				recursionMap.remove(this);
			}
		}
		catch (final Exception e)
		{
			builder.append("EXCEPTION while printing.");
			builder.append(trace(e));
		}
		catch (final AssertionError e)
		{
			builder.append("ASSERTION ERROR while printing.");
			builder.append(trace(e));
		}
	}

	/**
	 * Utility method for decomposing this object in the debugger.  See {@link
	 * AvailObjectFieldHelper} for instructions to enable this functionality in
	 * Eclipse.
	 *
	 * @return An array of {@code AvailObjectFieldHelper} objects that help
	 *         describe the logical structure of the receiver to the debugger.
	 */
	@Override
	public AvailObjectFieldHelper[] describeForDebugger()
	{
		return descriptor().o_DescribeForDebugger(this);
	}

	/**
	 * Answer a name suitable for labeling a field containing this object.
	 *
	 * @return An Avail {@linkplain StringDescriptor string}.
	 */
	@Override
	public String nameForDebugger()
	{
		return descriptor().o_NameForDebugger(this);
	}

	/**
	 * Answer whether to show value-specific content in the file name for the
	 * debugger.
	 *
	 * @return Whether to show the value.
	 */
	@Override
	public boolean showValueInNameForDebugger()
	{
		return descriptor().o_ShowValueInNameForDebugger(this);
	}

	@Override
	public String toString ()
	{
		final StringBuilder stringBuilder = new StringBuilder(100);
		final IdentityHashMap<A_BasicObject, Void> recursionMap =
			new IdentityHashMap<>(10);
		printOnAvoidingIndent(stringBuilder, recursionMap, 1);
		assert recursionMap.size() == 0;
		return stringBuilder.toString();
	}

	/**
	 * Create a new {@code AvailObject} with the specified {@linkplain
	 * AbstractDescriptor descriptor} and the same number of variable object and
	 * integer slots.
	 *
	 * @param size The number of variable object and integer slots.
	 * @param descriptor A descriptor.
	 * @return A new object.
	 */
	static AvailObject newIndexedDescriptor (
		final int size,
		final AbstractDescriptor descriptor)
	{
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
	 * Create a new {@code AvailObject} with the specified {@linkplain
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
		final AbstractDescriptor descriptor)
	{
		assert descriptor.hasVariableObjectSlots || variableObjectSlots == 0;
		assert descriptor.hasVariableIntegerSlots || variableIntegerSlots == 0;
		return new AvailObject(
			descriptor,
			descriptor.numberOfFixedObjectSlots() + variableObjectSlots,
			descriptor.numberOfFixedIntegerSlots() + variableIntegerSlots);
	}

	/**
	 * Answer whether the receiver is numerically greater than the argument.
	 *
	 * @param another A {@linkplain AbstractNumberDescriptor numeric object}.
	 * @return Whether the receiver is strictly greater than the argument.
	 */
	@Override
	public boolean greaterThan (
		final A_Number another)
	{
		return numericCompare(another).isMore();
	}

	/**
	 * Answer whether the receiver is numerically greater than or equivalent to
	 * the argument.
	 *
	 * @param another A {@linkplain AbstractNumberDescriptor numeric object}.
	 * @return Whether the receiver is greater than or equivalent to the
	 *         argument.
	 */
	@Override
	public boolean greaterOrEqual (
		final A_Number another)
	{
		return numericCompare(another).isMoreOrEqual();
	}

	/**
	 * Answer whether the receiver is numerically less than the argument.
	 *
	 * @param another A {@linkplain AbstractNumberDescriptor numeric object}.
	 * @return Whether the receiver is strictly less than the argument.
	 */
	@Override
	public boolean lessThan (
		final A_Number another)
	{
		return numericCompare(another).isLess();
	}

	/**
	 * Answer whether the receiver is numerically less than or equivalent to
	 * the argument.
	 *
	 * @param another A {@linkplain AbstractNumberDescriptor numeric object}.
	 * @return Whether the receiver is less than or equivalent to the argument.
	 */
	@Override
	public boolean lessOrEqual (
		final A_Number another)
	{
		return numericCompare(another).isLessOrEqual();
	}

	/**
	 * 	Helper method for transferring this object's longSlots into an
	 * 	{@link L1InstructionDecoder}.  The receiver's descriptor must be a
	 * 	{@link CompiledCodeDescriptor}.
	 *
	 * @param instructionDecoder The {@link L1InstructionDecoder} to populate.
	 */
	@Override
	public void setUpInstructionDecoder (
		final L1InstructionDecoder instructionDecoder)
	{
		super.setUpInstructionDecoder(instructionDecoder);
		final int finalPc = numNybbles() + 1;
		instructionDecoder.finalLongIndex =
			L1InstructionDecoder.baseIndexInArray + (finalPc >> 4);
		instructionDecoder.finalShift = (finalPc & 0xF) << 2;
	}

	/**
	 * Set up the object to report nice obvious errors if anyone ever accesses
	 * it again.
	 */
	void assertObjectUnreachableIfMutable ()
	{
		checkValidAddress();
		if (!descriptor().isMutable())
		{
			return;
		}
		if (sameAddressAs(nil))
		{
			error("What happened?  This object is also the excluded one.");
		}

		// Recursively invoke the iterator on the subobjects of self...
		final AvailSubobjectVisitor vis =
			new MarkUnreachableSubobjectVisitor(nil);
		scanSubobjects(vis);
		destroy();
	}

	/**
	 * Replace my descriptor field with a {@link FillerDescriptor}.  This blows
	 * up for most messages, catching incorrect (all, by definition) further
	 * accidental uses of this object.
	 */
	@Override
	public void setToInvalidDescriptor ()
	{
		setDescriptor(FillerDescriptor.shared);
	}

	/**
	 * Compute the 32-bit hash of the receiver.
	 *
	 * @return An {@code int} hash value.
	 */
	@Override
	public int hash ()
	{
		return descriptor().o_Hash(this);
	}

	/**
	 * Construct a new {@code AvailObjectRepresentation}.
	 *
	 * @param descriptor This object's {@link AbstractDescriptor}.
	 * @param objectSlotsSize The number of object slots to allocate.
	 * @param intSlotsSize The number of integer slots to allocate.
	 */
	private AvailObject (
		final AbstractDescriptor descriptor,
		final int objectSlotsSize,
		final int intSlotsSize)
	{
		super(descriptor, objectSlotsSize, intSlotsSize);
	}

	/**
	 * Answer whether the {@code AvailObject#argsTupleType() argument
	 * types} supported by the specified {@linkplain FunctionTypeDescriptor
	 * function type} are acceptable argument types for invoking a {@linkplain
	 * FunctionDescriptor function} whose type is the receiver.
	 *
	 * @param functionType A function type.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than those of {@code functionType}, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean acceptsArgTypesFromFunctionType (
		final A_Type functionType)
	{
		return descriptor().o_AcceptsArgTypesFromFunctionType(this, functionType);
	}

	/**
	 * Answer whether these are acceptable {@linkplain TypeDescriptor argument
	 * types} for invoking a {@linkplain FunctionDescriptor function} whose type
	 * is the receiver.
	 *
	 * @param argTypes A list containing the argument types to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than those within the {@code argTypes} list, {@code
	 *         false} otherwise.
	 */
	@Override
	public boolean acceptsListOfArgTypes (
		final List<? extends A_Type> argTypes)
	{
		return descriptor().o_AcceptsListOfArgTypes(this, argTypes);
	}

	/**
	 * Answer whether these are acceptable arguments for invoking a {@linkplain
	 * FunctionDescriptor function} whose type is the receiver.
	 *
	 * @param argValues A list containing the argument values to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than the types of the values within the {@code argValues}
	 *         list, {@code false} otherwise.
	 */
	@Override
	public boolean acceptsListOfArgValues (
		final List<? extends A_BasicObject> argValues)
	{
		return descriptor().o_AcceptsListOfArgValues(this, argValues);
	}

	/**
	 * Answer whether these are acceptable {@linkplain TypeDescriptor argument
	 * types} for invoking a {@linkplain FunctionDescriptor function} that is an
	 * instance of the receiver. There may be more entries in the {@linkplain
	 * TupleDescriptor tuple} than are required by the {@linkplain
	 * FunctionTypeDescriptor function type}.
	 *
	 * @param argTypes A tuple containing the argument types to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than the corresponding elements of the {@code argTypes}
	 *         tuple, {@code false} otherwise.
	 */
	@Override
	public boolean acceptsTupleOfArgTypes (
		final A_Tuple argTypes)
	{
		return descriptor().o_AcceptsTupleOfArgTypes(this, argTypes);
	}

	/**
	 * Answer whether these are acceptable arguments for invoking a {@linkplain
	 * FunctionDescriptor function} that is an instance of the receiver. There
	 * may be more entries in the {@linkplain TupleDescriptor tuple} than are
	 * required by the {@linkplain FunctionTypeDescriptor function type}.
	 *
	 * @param arguments A tuple containing the argument values to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than the types of the corresponding elements of the
	 *         {@code arguments} tuple, {@code false} otherwise.
	 */
	@Override
	public boolean acceptsTupleOfArguments (
		final A_Tuple arguments)
	{
		return descriptor().o_AcceptsTupleOfArguments(this, arguments);
	}

	/**
	 * Add the {@linkplain L2Chunk chunk} with the given index to the receiver's
	 * list of chunks that depend on it.  The receiver is a {@linkplain
	 * MethodDescriptor method}.  A change in the method's membership (e.g.,
	 * adding a new method definition) will cause the chunk to be invalidated.
	 */
	@Override
	public void addDependentChunk (final L2Chunk chunk)
	{
		descriptor().o_AddDependentChunk(this, chunk);
	}

	/**
	 * Add the {@linkplain DefinitionDescriptor definition} to the receiver, a
	 * {@linkplain MethodDefinitionDescriptor method}.  Causes dependent chunks
	 * to be invalidated.  Answer the {@link A_DefinitionParsingPlan}s that
	 * were created for the new definition.
	 *
	 * @param definition The definition to be added.
	 * @throws SignatureException
	 *         If the definition could not be added.
	 */
	@Override
	public void methodAddDefinition (
			final A_Definition definition)
		throws SignatureException
	{
		descriptor().o_MethodAddDefinition(this, definition);
	}

	/**
	 * Add a set of {@linkplain GrammaticalRestrictionDescriptor grammatical
	 * restrictions} to this {@linkplain MessageBundleDescriptor message
	 * bundle}.
	 *
	 * @param grammaticalRestriction
	 *        The set of grammatical restrictions to be added.
	 */
	@Override
	public void moduleAddGrammaticalRestriction (
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		descriptor().o_ModuleAddGrammaticalRestriction(
			this, grammaticalRestriction);
	}

	/**
	 * Add the receiver and the argument {@code anInfinity} and answer the
	 * {@code AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #plusCanDestroy(A_Number, boolean) plusCanDestroy}. It exists for
	 * double-dispatch only.</p>
	 *
	 * @param sign
	 *        The {@linkplain Sign sign} of the infinity.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number addToInfinityCanDestroy (
		final Sign sign,
		final boolean canDestroy)
	{
		return descriptor().o_AddToInfinityCanDestroy(
			this,
			sign,
			canDestroy);
	}

	/**
	 * Add the receiver and the argument {@code anInteger} and answer the
	 * {@code AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #plusCanDestroy(A_Number, boolean) plusCanDestroy}. It exists for
	 * double-dispatch only.</p>
	 *
	 * @param anInteger
	 *        An {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number addToIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor().o_AddToIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	/**
	 * Construct a Java {@linkplain String string} from the receiver, an Avail
	 * {@linkplain StringDescriptor string}.
	 *
	 * @return The corresponding Java string.
	 */
	@Override
	public String asNativeString ()
	{
		return descriptor().o_AsNativeString(this);
	}

	/**
	 * Construct a Java {@linkplain Set set} from the receiver, a {@linkplain
	 * TupleDescriptor tuple}.
	 *
	 * @return A set containing each element in the tuple.
	 */
	@Override
	public A_Set asSet ()
	{
		return descriptor().o_AsSet(this);
	}

	/**
	 * Construct a {@linkplain TupleDescriptor tuple} from the receiver, a
	 * {@linkplain SetDescriptor set}. Element ordering in the tuple will be
	 * arbitrary and unstable.
	 *
	 * @return A tuple containing each element in the set.
	 */
	@Override
	public A_Tuple asTuple ()
	{
		return descriptor().o_AsTuple(this);
	}

	@Override
	public void addGrammaticalRestriction (
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		descriptor().o_AddGrammaticalRestriction(
			this, grammaticalRestriction);
	}

	@Override
	public void moduleAddDefinition (
		final A_BasicObject definition)
	{
		descriptor().o_ModuleAddDefinition(
			this,
			definition);
	}

	@Override
	public void addDefinitionParsingPlan (
		final A_DefinitionParsingPlan plan)
	{
		descriptor().o_AddDefinitionParsingPlan(
			this,
			plan);
	}

	@Override
	public void addImportedName (
		final A_Atom trueName)
	{
		descriptor().o_AddImportedName(
			this,
			trueName);
	}

	@Override
	public void addImportedNames (
		final A_Set trueNames)
	{
		descriptor().o_AddImportedNames(
			this,
			trueNames);
	}

	@Override
	public void introduceNewName (
		final A_Atom trueName)
	{
		descriptor().o_IntroduceNewName(
			this,
			trueName);
	}

	@Override
	public void addPrivateName (
		final A_Atom trueName)
	{
		descriptor().o_AddPrivateName(
			this,
			trueName);
	}

	@Override
	public A_BasicObject setBinAddingElementHashLevelCanDestroy (
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		return descriptor().o_SetBinAddingElementHashLevelCanDestroy(
			this,
			elementObject,
			elementObjectHash,
			myLevel,
			canDestroy);
	}

	@Override
	public int setBinSize ()
	{
		return descriptor().o_SetBinSize(this);
	}

	@Override
	public AvailObject binElementAt (
		final int index)
	{
		return descriptor().o_BinElementAt(this, index);
	}

	@Override
	public boolean binHasElementWithHash (
		final A_BasicObject elementObject,
		final int elementObjectHash)
	{
		return descriptor().o_BinHasElementWithHash(
			this,
			elementObject,
			elementObjectHash);
	}

	@Override
	public int setBinHash ()
	{
		return descriptor().o_SetBinHash(this);
	}

	@Override
	public AvailObject binRemoveElementHashLevelCanDestroy (
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		return descriptor().o_BinRemoveElementHashLevelCanDestroy(
			this,
			elementObject,
			elementObjectHash,
			myLevel,
			canDestroy);
	}

	@Override
	public int bitsPerEntry ()
	{
		return descriptor().o_BitsPerEntry(this);
	}

	@Override
	public A_Function bodyBlock ()
	{
		return descriptor().o_BodyBlock(this);
	}

	@Override
	public A_Type bodySignature ()
	{
		return descriptor().o_BodySignature(this);
	}

	@Override
	public A_BasicObject breakpointBlock ()
	{
		return descriptor().o_BreakpointBlock(this);
	}

	@Override
	public void breakpointBlock (
		final AvailObject value)
	{
		descriptor().o_BreakpointBlock(this, value);
	}

	@Override
	public A_BundleTree buildFilteredBundleTree ()
	{
		return descriptor().o_BuildFilteredBundleTree(this);
	}

	@Override
	public A_Continuation caller ()
	{
		return descriptor().o_Caller(this);
	}

	@Override
	public void clearValue ()
	{
		descriptor().o_ClearValue(this);
	}

	@Override
	public A_Function function ()
	{
		return descriptor().o_Function(this);
	}

	@Override
	public A_Type functionType ()
	{
		return descriptor().o_FunctionType(this);
	}

	@Override
	public A_RawFunction code ()
	{
		return descriptor().o_Code(this);
	}

	@Override
	public int codePoint ()
	{
		return descriptor().o_CodePoint(this);
	}

	/**
	 * Compare a subrange of the {@code AvailObject receiver} with a
	 * subrange of another object. The size of the subrange of both objects is
	 * determined by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anotherObject
	 *        The other object used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the other object's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean compareFromToWithStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
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
	 * Compare a subrange of the {@code AvailObject receiver} with a
	 * subrange of the given {@linkplain TupleDescriptor tuple}. The size of the
	 * subrange of both objects is determined by the index range supplied for
	 * the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aTuple
	 *        The tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean compareFromToWithAnyTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aTuple,
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
	 * Compare a subrange of the {@code AvailObject receiver} with a
	 * subrange of the given {@linkplain ByteStringDescriptor byte string}. The
	 * size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aByteString
	 *        The byte string used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte string's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean compareFromToWithByteStringStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_String aByteString,
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
	 * Compare a subrange of the {@code AvailObject receiver} with a
	 * subrange of the given {@linkplain ByteTupleDescriptor byte tuple}. The
	 * size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aByteTuple
	 *        The byte tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean compareFromToWithByteTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteTuple,
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
	 * Compare a subrange of the {@code AvailObject receiver} with a
	 * subrange of the given {@linkplain IntegerIntervalTupleDescriptor integer
	 * interval tuple}. The size of the subrange of both objects is determined
	 * by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anIntegerIntervalTuple
	 *        The integer interval tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean compareFromToWithIntegerIntervalTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anIntegerIntervalTuple,
		final int startIndex2)
	{
		return descriptor().o_CompareFromToWithIntegerIntervalTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			anIntegerIntervalTuple,
			startIndex2);
	}

	/**
	 * Compare a subrange of the {@code AvailObject receiver} with a
	 * subrange of the given {@linkplain IntTupleDescriptor int tuple}. The
	 * size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anIntTuple
	 *        The int tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the int tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean compareFromToWithIntTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anIntTuple,
		final int startIndex2)
	{
		return descriptor().o_CompareFromToWithIntTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			anIntTuple,
			startIndex2);
	}

	/**
	 * Compare a subrange of the {@code AvailObject receiver} with a
	 * subrange of the given {@linkplain SmallIntegerIntervalTupleDescriptor
	 * small integer interval tuple}. The size of the subrange of both objects
	 * is determined by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aSmallIntegerIntervalTuple
	 *        The small integer interval tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean compareFromToWithSmallIntegerIntervalTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aSmallIntegerIntervalTuple,
		final int startIndex2)
	{
		return descriptor().o_CompareFromToWithSmallIntegerIntervalTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aSmallIntegerIntervalTuple,
			startIndex2);
	}

	/**
	 * Compare a subrange of the {@code AvailObject receiver} with a
	 * subrange of the given {@linkplain RepeatedElementTupleDescriptor repeated
	 * element tuple}. The size of the subrange of both objects is determined
	 * by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aRepeatedElementTuple
	 *        The repeated element tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the repeated element tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean compareFromToWithRepeatedElementTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aRepeatedElementTuple,
		final int startIndex2)
	{
		return descriptor().o_CompareFromToWithRepeatedElementTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aRepeatedElementTuple,
			startIndex2);
	}

	/**
	 * Compare a subrange of the {@code AvailObject receiver} with a
	 * subrange of the given {@linkplain NybbleTupleDescriptor nybble tuple}.
	 * The size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aNybbleTuple
	 *        The nybble tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the nybble tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean compareFromToWithNybbleTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aNybbleTuple,
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
	 * Compare a subrange of the {@code AvailObject receiver} with a
	 * subrange of the given {@linkplain ObjectTupleDescriptor object tuple}.
	 * The size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anObjectTuple
	 *        The object tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the object tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
     */
	@Override
	public boolean compareFromToWithObjectTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anObjectTuple,
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
	 * Compare a subrange of the {@code AvailObject receiver} with a
	 * subrange of the given {@linkplain TwoByteStringDescriptor two-byte
	 * string}. The size of the subrange of both objects is determined by the
	 * index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aTwoByteString
	 *        The two-byte string used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the two-byte string's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
     */
	@Override
	public boolean compareFromToWithTwoByteStringStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_String aTwoByteString,
		final int startIndex2)
	{
		return descriptor().o_CompareFromToWithTwoByteStringStartingAt(
			this,
			startIndex1,
			endIndex1,
			aTwoByteString,
			startIndex2);
	}

	@Override
	public A_Set lazyComplete ()
	{
		return descriptor().o_LazyComplete(this);
	}

	@Override
	public int computeHashFromTo (
		final int start,
		final int end)
	{
		return descriptor().o_ComputeHashFromTo(
			this,
			start,
			end);
	}

	@Override
	public A_Tuple concatenateTuplesCanDestroy (
		final boolean canDestroy)
	{
		return descriptor().o_ConcatenateTuplesCanDestroy(this, canDestroy);
	}

	@Override
	public A_Map constantBindings ()
	{
		return descriptor().o_ConstantBindings(this);
	}

	@Override
	public A_Type contentType ()
	{
		return descriptor().o_ContentType(this);
	}

	@Override
	public A_Continuation continuation ()
	{
		return descriptor().o_Continuation(this);
	}

	@Override
	public void continuation (
		final A_Continuation value)
	{
		descriptor().o_Continuation(this, value);
	}

	@Override
	public A_Tuple copyAsMutableIntTuple ()
	{
		return descriptor().o_CopyAsMutableIntTuple(this);
	}

	@Override
	public A_Tuple copyAsMutableObjectTuple ()
	{
		return descriptor().o_CopyAsMutableObjectTuple(this);
	}

	@Override
	public A_Tuple copyTupleFromToCanDestroy (
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
	 * A convenience method that exposes the fact that a subtuple of a string is
	 * also a string.
	 *
	 * @param start
	 *        The start of the range to extract.
	 * @param end
	 *        The end of the range to extract.
	 * @param canDestroy
	 *        Whether the original object may be destroyed if mutable.
	 * @return The substring.
	 */
	@Override
	public A_String copyStringFromToCanDestroy (
		final int start,
		final int end,
		final boolean canDestroy)
	{
		return cast(
			descriptor().o_CopyTupleFromToCanDestroy(
				this, start, end, canDestroy));
	}

	@Override
	public boolean couldEverBeInvokedWith (
		final List<TypeRestriction> argRestrictions)
	{
		return descriptor().o_CouldEverBeInvokedWith(this, argRestrictions);
	}

	@Override
	public A_Type defaultType ()
	{
		return descriptor().o_DefaultType(this);
	}

	/**
	 * Divide the receiver by the argument {@code aNumber} and answer the
	 * {@code AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * A_Number#divideIntoIntegerCanDestroy(AvailObject, boolean)
	 * divideIntoIntegerCanDestroy} or {@link
	 * #divideIntoInfinityCanDestroy(Sign, boolean)
	 * divideIntoInfinityCanDestroy}, where actual implementations of the
	 * division operation should reside.</p>
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number divideCanDestroy (
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return descriptor().o_DivideCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Divide the receiver by the argument {@code aNumber} and answer the
	 * {@code AvailObject result}. The operation is not allowed to fail,
	 * so the caller must ensure that the arguments are valid, i.e. the divisor
	 * is not {@linkplain IntegerDescriptor#zero() zero}.
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number noFailDivideCanDestroy (
		final A_Number aNumber,
		final boolean canDestroy)
	{
		try
		{
			return descriptor().o_DivideCanDestroy(
				this,
				aNumber,
				canDestroy);
		}
		catch (final ArithmeticException e)
		{
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			assert false;
			error("noFailDivideCanDestroy failed!");
			return nil;
		}
	}

	/**
	 * Divide an infinity with the given {@linkplain Sign sign} by the receiver
	 * and answer the {@code AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #divideCanDestroy(A_Number, boolean) divideCanDestroy}. It exists for
	 * double-dispatch only.</p>
	 *
	 * @param sign
	 *        The sign of the infinity.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number divideIntoInfinityCanDestroy (
		final Sign sign,
		final boolean canDestroy)
	{
		return descriptor().o_DivideIntoInfinityCanDestroy(
			this, sign, canDestroy);
	}

	/**
	 * Divide the argument {@code anInteger} by the receiver and answer the
	 * {@code AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #divideCanDestroy(A_Number, boolean) divideCanDestroy}. It exists for
	 * double-dispatch only.</p>
	 *
	 * @param anInteger
	 *        An {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number divideIntoIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor().o_DivideIntoIntegerCanDestroy(
			this, anInteger, canDestroy);
	}

	@Override
	public A_Continuation ensureMutable ()
	{
		return descriptor().o_EnsureMutable(this);
	}

	/**
	 * Answer whether the receiver and the argument, both {@linkplain
	 * AvailObject objects}, are equal in value.
	 *
	 * Note that the argument is of type {@link A_BasicObject} so that correctly
	 * typed uses (where the argument is statically known to be an AvailObject)
	 * proceed normally. Incorrectly typed uses (where the argument is an
	 * arbitrary Java {@link Object} should show up as calling a deprecated
	 * method, and should fail at runtime if the argument is not actually an
	 * AvailObject.
	 *
	 * @param another The object to be compared to the receiver.
	 * @return {@code true} if the two objects are of equal value, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean equals (final A_BasicObject another)
	{
		if (this == another)
		{
			return true;
		}
		if (!descriptor().o_Equals(this, another))
		{
			return false;
		}
		// They're equal.  Try to turn one into an indirection to the other.
		final AvailObject traversed1 = traversed();
		final AvailObject traversed2 = another.traversed();
		if (traversed1 == traversed2)
		{
			return true;
		}
		if (!traversed1.descriptor().isShared())
		{
			if (!traversed2.descriptor().isShared()
				&& traversed1.isBetterRepresentationThan(traversed2))
			{
				traversed2.becomeIndirectionTo(traversed1.makeImmutable());
			}
			else
			{
				traversed1.becomeIndirectionTo(traversed2.makeImmutable());
			}
		}
		else if (!traversed2.descriptor().isShared())
		{
			traversed2.becomeIndirectionTo(traversed1.makeImmutable());
		}
		return true;
	}

	/**
	 * Answer whether the receiver, an {@code AvailObject object}, and the
	 * argument, a {@linkplain TupleDescriptor tuple}, are equal in value.
	 *
	 * @param aTuple The tuple to be compared to the receiver.
	 * @return {@code true} if the receiver is a tuple and of value equal to the
	 *         argument, {@code false} otherwise.
	 */
	@Override
	public boolean equalsAnyTuple (
		final A_Tuple aTuple)
	{
		return descriptor().o_EqualsAnyTuple(this, aTuple);
	}

	/**
	 * Answer whether the receiver, an {@code AvailObject object}, and the
	 * argument, a {@linkplain ByteStringDescriptor byte string}, are equal in
	 * value.
	 *
	 * @param aByteString The byte string to be compared to the receiver.
	 * @return {@code true} if the receiver is a byte string and of value equal
	 *         to the argument, {@code false} otherwise.
	 */
	@Override
	public boolean equalsByteString (
		final A_String aByteString)
	{
		return descriptor().o_EqualsByteString(this, aByteString);
	}

	/**
	 * Answer whether the receiver, an {@code AvailObject object}, and the
	 * argument, a {@linkplain ByteTupleDescriptor byte tuple}, are equal in
	 * value.
	 *
	 * @param aByteTuple The byte tuple to be compared to the receiver.
	 * @return {@code true} if the receiver is a byte tuple and of value equal
	 *         to the argument, {@code false} otherwise.
	 */
	@Override
	public boolean equalsByteTuple (
		final A_Tuple aByteTuple)
	{
		return descriptor().o_EqualsByteTuple(this, aByteTuple);
	}

	/**
	 * Answer whether the receiver, an {@code AvailObject object}, and the
	 * argument, an {@linkplain IntegerIntervalTupleDescriptor integer interval
	 * tuple}, are equal in value.
	 *
	 * @param anIntegerIntervalTuple The integer interval tuple to be compared
	 *                               to the receiver.
	 * @return {@code true} if the receiver is an integer interval tuple and of
	 *         value equal to the argument, {@code false} otherwise.
	 */
	@Override
	public boolean equalsIntegerIntervalTuple (
		final A_Tuple anIntegerIntervalTuple)
	{
		return descriptor().o_EqualsIntegerIntervalTuple(
			this,
			anIntegerIntervalTuple);
	}

	/**
	 * Answer whether the receiver, an {@code AvailObject object}, and the
	 * argument, an {@linkplain IntTupleDescriptor int tuple}, are equal in
	 * value.
	 *
	 * @param anIntTuple The int tuple to be compared to the receiver.
	 * @return {@code true} if the receiver is a tuple equal to the argument,
	 *         {@code false} otherwise.
	 */
	@Override
	public boolean equalsIntTuple (
		final A_Tuple anIntTuple)
	{
		return descriptor().o_EqualsIntTuple(this, anIntTuple);
	}

	/**
	 * Answer whether the receiver, an {@code AvailObject object}, and the
	 * argument, a {@linkplain SmallIntegerIntervalTupleDescriptor small integer
	 * interval tuple}, are equal in value.
	 *
	 * @param aSmallIntegerIntervalTuple The integer interval tuple to be compared
	 *                               to the receiver.
	 * @return {@code true} if the receiver is a small integer interval tuple
	 *         and of value equal to the argument, {@code false} otherwise.
	 */
	@Override
	public boolean equalsSmallIntegerIntervalTuple (
		final A_Tuple aSmallIntegerIntervalTuple)
	{
		return descriptor().o_EqualsSmallIntegerIntervalTuple(
			this,
			aSmallIntegerIntervalTuple);
	}

	/**
	 * Answer whether the receiver, an {@code AvailObject object}, and the
	 * argument, a {@linkplain RepeatedElementTupleDescriptor repeated element
	 * tuple}, are equal in value.
	 *
	 * @param aRepeatedElementTuple The repeated element tuple to be compared
	 *                               to the receiver.
	 * @return {@code true} if the receiver is a repeated element tuple and of
	 *         value equal to the argument, {@code false} otherwise.
	 */
	@Override
	public boolean equalsRepeatedElementTuple (
		final A_Tuple aRepeatedElementTuple)
	{
		return descriptor().o_EqualsRepeatedElementTuple(
			this,
			aRepeatedElementTuple);
	}

	/**
	 * Answer whether the receiver, an {@code AvailObject object}, is a
	 * character with a code point equal to the integer argument.
	 *
	 * @param aCodePoint The code point to be compared to the receiver.
	 * @return {@code true} if the receiver is a character with a code point
	 *         equal to the argument, {@code false} otherwise.
	 */
	@Override
	public boolean equalsCharacterWithCodePoint (
		final int aCodePoint)
	{
		return descriptor().o_EqualsCharacterWithCodePoint(this, aCodePoint);
	}

	@Override
	public boolean equalsFiberType (final A_Type aFiberType)
	{
		return descriptor().o_EqualsFiberType(this, aFiberType);
	}

	@Override
	public boolean equalsFunction (
		final A_Function aFunction)
	{
		return descriptor().o_EqualsFunction(this, aFunction);
	}

	/**
	 * Answer whether the receiver, an {@code AvailObject object}, and the
	 * argument, a {@linkplain FunctionTypeDescriptor function type}, are equal.
	 *
	 * @param aFunctionType The function type used in the comparison.
	 * @return {@code true} IFF the receiver is also a function type and:
	 *
	 * <ul>
	 * <li>The {@code AvailObject#argsTupleType() argument types}
	 * correspond,</li>
	 * <li>The {@code AvailObject#returnType() return types}
	 * correspond, and</li>
	 * <li>The {@code AvailObject#declaredExceptions() raise types}
	 * correspond.</li>
	 * </ul>
	 */
	@Override
	public boolean equalsFunctionType (
		final A_Type aFunctionType)
	{
		return descriptor().o_EqualsFunctionType(this, aFunctionType);
	}

	/**
	 * Answer whether the arguments, an {@code AvailObject object} and a
	 * {@linkplain CompiledCodeDescriptor compiled code}, are equal.
	 *
	 * @param aCompiledCode The compiled code used in the comparison.
	 * @return {@code true} if the receiver is a compiled code and of value
	 *         equal to the argument, {@code false} otherwise.
	 */
	@Override
	public boolean equalsCompiledCode (
		final A_RawFunction aCompiledCode)
	{
		return descriptor().o_EqualsCompiledCode(this, aCompiledCode);
	}

	/**
	 * Answer whether the arguments, an {@code AvailObject object} and a
	 * {@linkplain VariableDescriptor variable}, are the exact same object,
	 * comparing by address (Java object identity). There's no need to traverse
	 * the objects before comparing addresses, because this message was a
	 * double-dispatch that would have skipped (and stripped) the indirection
	 * objects in either path.
	 *
	 * @param aVariable The variable used in the comparison.
	 * @return {@code true} if the receiver is a variable with the same identity
	 *         as the argument, {@code false} otherwise.
	 */
	@Override
	public boolean equalsVariable (
		final AvailObject aVariable)
	{
		return descriptor().o_EqualsVariable(this, aVariable);
	}

	@Override
	public boolean equalsVariableType (
		final A_Type aVariableType)
	{
		return descriptor().o_EqualsVariableType(this, aVariableType);
	}

	@Override
	public boolean equalsContinuation (
		final A_Continuation aContinuation)
	{
		return descriptor().o_EqualsContinuation(this, aContinuation);
	}

	@Override
	public boolean equalsContinuationType (
		final A_Type aContinuationType)
	{
		return descriptor().o_EqualsContinuationType(this, aContinuationType);
	}

	@Override
	public boolean equalsDouble (
		final double aDouble)
	{
		return descriptor().o_EqualsDouble(this, aDouble);
	}

	@Override
	public boolean equalsFloat (
		final float aFloat)
	{
		return descriptor().o_EqualsFloat(this, aFloat);
	}

	/**
	 * Answer whether the {@code AvailObject receiver} is an {@linkplain
	 * InfinityDescriptor infinity} with the specified {@link
	 * Sign}.
	 *
	 * @param sign The type of infinity for comparison.
	 * @return {@code true} if the receiver is an infinity of the specified
	 *         sign, {@code false} otherwise.
	 */
	@Override
	public boolean equalsInfinity (
		final Sign sign)
	{
		return descriptor().o_EqualsInfinity(this, sign);
	}

	@Override
	public boolean equalsInteger (
		final AvailObject anAvailInteger)
	{
		return descriptor().o_EqualsInteger(this, anAvailInteger);
	}

	@Override
	public boolean equalsIntegerRangeType (
		final A_Type anIntegerRangeType)
	{
		return descriptor().o_EqualsIntegerRangeType(this, anIntegerRangeType);
	}

	@Override
	public boolean equalsMap (
		final A_Map aMap)
	{
		return descriptor().o_EqualsMap(this, aMap);
	}

	@Override
	public boolean equalsMapType (
		final A_Type aMapType)
	{
		return descriptor().o_EqualsMapType(this, aMapType);
	}

	@Override
	public boolean equalsNybbleTuple (
		final A_Tuple aNybbleTuple)
	{
		return descriptor().o_EqualsNybbleTuple(this, aNybbleTuple);
	}

	@Override
	public boolean equalsObject (
		final AvailObject anObject)
	{
		return descriptor().o_EqualsObject(this, anObject);
	}

	@Override
	public boolean equalsObjectTuple (
		final A_Tuple anObjectTuple)
	{
		return descriptor().o_EqualsObjectTuple(this, anObjectTuple);
	}

	@Override
	public boolean equalsPhraseType (
		final A_Type aPhraseType)
	{
		return descriptor().o_EqualsPhraseType(this, aPhraseType);
	}

	@Override
	public boolean equalsPojo (final AvailObject aPojo)
	{
		return descriptor().o_EqualsPojo(this, aPojo);
	}

	@Override
	public boolean equalsPojoType (final AvailObject aPojoType)
	{
		return descriptor().o_EqualsPojoType(this, aPojoType);
	}

	@Override
	public boolean equalsPrimitiveType (
		final A_Type aPrimitiveType)
	{
		return descriptor().o_EqualsPrimitiveType(this, aPrimitiveType);
	}

	@Override
	public boolean equalsRawPojoFor (
		final AvailObject otherRawPojo,
		final @Nullable Object otherJavaObject)
	{
		return descriptor().o_EqualsRawPojoFor(
			this,
			otherRawPojo,
			otherJavaObject);
	}

	/**
	 * Answer whether the receiver and the argument tuple,
	 * both {@code AvailObject objects}, are equal in value.
	 *
	 * Note that the argument is of type {@code AvailObject} so that correctly
	 * typed uses (where the argument is statically known to be an AvailObject)
	 * proceed normally. Incorrectly typed uses (where the argument is an
	 * arbitrary Java {@link Object} should show up as calling a deprecated
	 * method, and should fail at runtime if the argument is not actually an
	 * AvailObject.
	 *
	 * @param aTuple The object to be compared to the receiver.
	 * @return {@code true} if the two objects are of equal value, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean equalsReverseTuple (final A_Tuple aTuple)
	{
		return descriptor().o_EqualsReverseTuple(this, aTuple);
	}

	@Override
	public boolean equalsSet (
		final A_Set aSet)
	{
		return descriptor().o_EqualsSet(this, aSet);
	}

	@Override
	public boolean equalsSetType (
		final A_Type aSetType)
	{
		return descriptor().o_EqualsSetType(this, aSetType);
	}

	@Override
	public boolean equalsTupleType (
		final A_Type aTupleType)
	{
		return descriptor().o_EqualsTupleType(this, aTupleType);
	}

	@Override
	public boolean equalsTwoByteString (
		final A_String aTwoByteString)
	{
		return descriptor().o_EqualsTwoByteString(this, aTwoByteString);
	}

	@Override
	public boolean equalsNil ()
	{
		// Was a dispatch that took indirections into account, but even when we
		// rebuild the memory substrate we can keep nil from moving around.
		return this == nil;
	}

	@Override
	public ExecutionState executionState ()
	{
		return descriptor().o_ExecutionState(this);
	}

	@Override
	public void executionState (
		final ExecutionState value)
	{
		descriptor().o_ExecutionState(this, value);
	}

	@Override
	public void expand (
		final A_Module module)
	{
		descriptor().o_Expand(this, module);
	}

	@Override
	public boolean extractBoolean ()
	{
		return descriptor().o_ExtractBoolean(this);
	}

	@Override
	public short extractUnsignedByte ()
	{
		return descriptor().o_ExtractUnsignedByte(this);
	}

	@Override
	public double extractDouble ()
	{
		return descriptor().o_ExtractDouble(this);
	}

	@Override
	public float extractFloat ()
	{
		return descriptor().o_ExtractFloat(this);
	}

	@Override
	public int extractInt ()
	{
		return descriptor().o_ExtractInt(this);
	}

	/**
	 * Extract a 64-bit signed Java {@code long} from the {@linkplain
	 * AvailObject receiver}.
	 *
	 * @return A 64-bit signed Java {@code long}
	 */
	@Override
	public long extractLong ()
	{
		return descriptor().o_ExtractLong(this);
	}

	@Override
	public byte extractNybble ()
	{
		return descriptor().o_ExtractNybble(this);
	}

	@Override
	public byte extractNybbleFromTupleAt (
		final int index)
	{
		return descriptor().o_ExtractNybbleFromTupleAt(this, index);
	}

	@Override
	public A_Map fieldMap ()
	{
		return descriptor().o_FieldMap(this);
	}

	@Override
	public A_Map fieldTypeMap ()
	{
		return descriptor().o_FieldTypeMap(this);
	}

	@Override
	public List<A_Definition> filterByTypes (
		final List<? extends A_Type> argTypes)
	{
		return descriptor().o_FilterByTypes(this, argTypes);
	}

	@Override
	public AvailObject getValue () throws VariableGetException
	{
		return descriptor().o_GetValue(this);
	}

	/**
	 * Answer whether the {@code AvailObject receiver} contains the
	 * specified element.
	 *
	 * @param elementObject The element.
	 * @return {@code true} if the receiver contains the element, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean hasElement (
		final A_BasicObject elementObject)
	{
		return descriptor().o_HasElement(this, elementObject);
	}

	@Override
	public int hashFromTo (
		final int startIndex,
		final int endIndex)
	{
		return descriptor().o_HashFromTo(
			this,
			startIndex,
			endIndex);
	}

	@Override
	public int hashOrZero ()
	{
		return descriptor().o_HashOrZero(this);
	}

	@Override
	public void hashOrZero (
		final int value)
	{
		descriptor().o_HashOrZero(this, value);
	}

	@Override
	public boolean hasKey (
		final A_BasicObject keyObject)
	{
		return descriptor().o_HasKey(this, keyObject);
	}

	@Override
	public boolean hasObjectInstance (
		final AvailObject potentialInstance)
	{
		return descriptor().o_HasObjectInstance(this, potentialInstance);
	}

	@Override
	public boolean hasGrammaticalRestrictions ()
	{
		return descriptor().o_HasGrammaticalRestrictions(this);
	}

	@Override
	public List<A_Definition> definitionsAtOrBelow (
		final List<TypeRestriction> argRestrictions)
	{
		return descriptor().o_DefinitionsAtOrBelow(this, argRestrictions);
	}

	@Override
	public A_Tuple definitionsTuple ()
	{
		return descriptor().o_DefinitionsTuple(this);
	}

	@Override
	public boolean includesDefinition (
		final A_Definition imp)
	{
		return descriptor().o_IncludesDefinition(this, imp);
	}

	@Override
	public A_Map lazyIncomplete ()
	{
		return descriptor().o_LazyIncomplete(this);
	}

	@Override
	public void setInterruptRequestFlag (
		final InterruptRequestFlag flag)
	{
		descriptor().o_SetInterruptRequestFlag(this, flag);
	}

	@Override
	public void decrementCountdownToReoptimize (
		final Continuation1NotNull<Boolean> continuation)
	{
		descriptor().o_DecrementCountdownToReoptimize(this, continuation);
	}

	@Override
	public void countdownToReoptimize (
		final int value)
	{
		descriptor().o_CountdownToReoptimize(this, value);
	}

	@Override
	public boolean isAbstract ()
	{
		return descriptor().o_IsAbstract(this);
	}

	@Override
	public boolean isAbstractDefinition ()
	{
		return descriptor().o_IsAbstractDefinition(this);
	}

	@Override
	public boolean isBetterRepresentationThan (
		final A_BasicObject anotherObject)
	{
		return descriptor().o_IsBetterRepresentationThan(this, anotherObject);
	}

	@Override
	public int representationCostOfTupleType ()
	{
		return descriptor().o_RepresentationCostOfTupleType(this);
	}

	@Override
	public boolean isBinSubsetOf (
		final A_Set potentialSuperset)
	{
		return descriptor().o_IsBinSubsetOf(this, potentialSuperset);
	}

	/**
	 * Is the {@code AvailObject receiver} an Avail boolean?
	 *
	 * @return {@code true} if the receiver is a boolean, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isBoolean ()
	{
		return descriptor().o_IsBoolean(this);
	}

	/**
	 * Is the {@code AvailObject receiver} an Avail unsigned byte?
	 *
	 * @return {@code true} if the argument is an unsigned byte, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isUnsignedByte ()
	{
		return descriptor().o_IsUnsignedByte(this);
	}

	/**
	 * Is the {@code AvailObject receiver} an Avail byte tuple?
	 *
	 * @return {@code true} if the receiver is a byte tuple, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isByteTuple ()
	{
		return descriptor().o_IsByteTuple(this);
	}

	/**
	 * Is the {@code AvailObject receiver} an Avail character?
	 *
	 * @return {@code true} if the receiver is a character, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isCharacter ()
	{
		return descriptor().o_IsCharacter(this);
	}

	/**
	 * Is the {@code AvailObject receiver} an Avail function?
	 *
	 * @return {@code true} if the receiver is a function, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isFunction ()
	{
		return descriptor().o_IsFunction(this);
	}

	/**
	 * Is the {@code AvailObject receiver} an Avail atom?
	 *
	 * @return {@code true} if the receiver is an atom, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isAtom ()
	{
		return descriptor().o_IsAtom(this);
	}

	/**
	 * Is the {@code AvailObject receiver} an Avail extended integer?
	 *
	 * @return {@code true} if the receiver is an extended integer, {@code
	 *         false} otherwise.
	 */
	@Override
	public boolean isExtendedInteger ()
	{
		return descriptor().o_IsExtendedInteger(this);
	}

	@Override
	public boolean isFinite ()
	{
		return descriptor().o_IsFinite(this);
	}

	/**
	 * Is the {@code AvailObject receiver} a {@linkplain
	 * ForwardDefinitionDescriptor forward declaration site}?
	 *
	 * @return {@code true} if the receiver is a forward declaration site.
	 */
	@Override
	public boolean isForwardDefinition ()
	{
		return descriptor().o_IsForwardDefinition(this);
	}

	/**
	 * Is the {@code AvailObject receiver} a {@linkplain
	 * MethodDefinitionDescriptor method definition}?
	 *
	 * @return {@code true} if the receiver is a method definition.
	 */
	@Override
	public boolean isMethodDefinition ()
	{
		return descriptor().o_IsMethodDefinition(this);
	}

	@Override
	public boolean isInstanceOf (
		final A_Type aType)
	{
		return descriptor().o_IsInstanceOf(this, aType);
	}

	@Override
	public boolean isInstanceOfKind (
		final A_Type aType)
	{
		return descriptor().o_IsInstanceOfKind(this, aType);
	}

	@Override
	public boolean isIntegerIntervalTuple ()
	{
		return descriptor().o_IsIntegerIntervalTuple(this);
	}

	@Override
	public boolean isIntTuple ()
	{
		return descriptor().o_IsIntTuple(this);
	}

	@Override
	public boolean isSmallIntegerIntervalTuple ()
	{
		return descriptor().o_IsSmallIntegerIntervalTuple(this);
	}

	@Override
	public boolean isRepeatedElementTuple ()
	{
		return descriptor().o_IsRepeatedElementTuple(this);
	}

	@Override
	public boolean isIntegerRangeType ()
	{
		return descriptor().o_IsIntegerRangeType(this);
	}

	/**
	 * Is the {@code AvailObject receiver} an Avail map?
	 *
	 * @return {@code true} if the receiver is a map, {@code false} otherwise.
	 */
	@Override
	public boolean isMap ()
	{
		return descriptor().o_IsMap(this);
	}

	@Override
	public boolean isMapType ()
	{
		return descriptor().o_IsMapType(this);
	}

	/**
	 * Is the {@code AvailObject receiver} an Avail nybble?
	 *
	 * @return {@code true} if the receiver is a nybble, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isNybble ()
	{
		return descriptor().o_IsNybble(this);
	}

	@Override
	public boolean isPositive ()
	{
		return descriptor().o_IsPositive(this);
	}

	/**
	 * Is the {@code AvailObject receiver} an Avail set?
	 *
	 * @return {@code true} if the receiver is a set, {@code false} otherwise.
	 */
	@Override
	public boolean isSet ()
	{
		return descriptor().o_IsSet(this);
	}

	@Override
	public boolean isSetType ()
	{
		return descriptor().o_IsSetType(this);
	}

	@Override
	public boolean isSubsetOf (
		final A_Set another)
	{
		return descriptor().o_IsSubsetOf(this, another);
	}

	/**
	 * Is the {@code AvailObject receiver} an Avail string?
	 *
	 * @return {@code true} if the receiver is an Avail string, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isString ()
	{
		return descriptor().o_IsString(this);
	}

	@Override
	public boolean isSubtypeOf (
		final A_Type aType)
	{
		return descriptor().o_IsSubtypeOf(this, aType);
	}

	@Override
	public boolean isSupertypeOfVariableType (
		final A_Type aVariableType)
	{
		return descriptor().o_IsSupertypeOfVariableType(this, aVariableType);
	}

	@Override
	public boolean isSupertypeOfContinuationType (
		final A_Type aContinuationType)
	{
		return descriptor().o_IsSupertypeOfContinuationType(
			this,
			aContinuationType);
	}

	@Override
	public boolean isSupertypeOfFiberType (final A_Type aFiberType)
	{
		return descriptor().o_IsSupertypeOfFiberType(this, aFiberType);
	}

	@Override
	public boolean isSupertypeOfFunctionType (
		final A_Type aFunctionType)
	{
		return descriptor().o_IsSupertypeOfFunctionType(this, aFunctionType);
	}

	@Override
	public boolean isSupertypeOfIntegerRangeType (
		final A_Type anIntegerRangeType)
	{
		return descriptor().o_IsSupertypeOfIntegerRangeType(
			this,
			anIntegerRangeType);
	}

	@Override
	public boolean isSupertypeOfListNodeType (
		final A_Type aListNodeType)
	{
		return descriptor().o_IsSupertypeOfListNodeType(
			this,
			aListNodeType);
	}

	@Override
	public boolean isSupertypeOfTokenType (
		final A_Type aTokenType)
	{
		return descriptor().o_IsSupertypeOfTokenType(
			this,
			aTokenType);
	}

	@Override
	public boolean isSupertypeOfLiteralTokenType (
		final A_Type aLiteralTokenType)
	{
		return descriptor().o_IsSupertypeOfLiteralTokenType(
			this,
			aLiteralTokenType);
	}

	@Override
	public boolean isSupertypeOfMapType (
		final AvailObject aMapType)
	{
		return descriptor().o_IsSupertypeOfMapType(this, aMapType);
	}

	@Override
	public boolean isSupertypeOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor().o_IsSupertypeOfObjectType(this, anObjectType);
	}

	@Override
	public boolean isSupertypeOfPhraseType (
		final A_Type aPhraseType)
	{
		return descriptor().o_IsSupertypeOfPhraseType(this, aPhraseType);
	}

	@Override
	public boolean isSupertypeOfPojoType (
		final A_Type aPojoType)
	{
		return descriptor().o_IsSupertypeOfPojoType(this, aPojoType);
	}

	@Override
	public boolean isSupertypeOfPrimitiveTypeEnum (
		final Types primitiveTypeEnum)
	{
		return descriptor().o_IsSupertypeOfPrimitiveTypeEnum(
			this,
			primitiveTypeEnum);
	}

	@Override
	public boolean isSupertypeOfSetType (
		final AvailObject aSetType)
	{
		return descriptor().o_IsSupertypeOfSetType(this, aSetType);
	}

	@Override
	public boolean isSupertypeOfBottom ()
	{
		return descriptor().o_IsSupertypeOfBottom(this);
	}

	@Override
	public boolean isSupertypeOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor().o_IsSupertypeOfTupleType(this, aTupleType);
	}

	@Override
	public boolean isSupertypeOfEnumerationType (
		final A_BasicObject anEnumerationType)
	{
		return descriptor().o_IsSupertypeOfEnumerationType(
			this, anEnumerationType);
	}

	/**
	 * Is the {@code AvailObject receiver} an Avail tuple?
	 *
	 * @return {@code true} if the receiver is a tuple, {@code false} otherwise.
	 */
	@Override
	public boolean isTuple ()
	{
		return descriptor().o_IsTuple(this);
	}

	@Override
	public boolean isTupleType ()
	{
		return descriptor().o_IsTupleType(this);
	}

	@Override
	public boolean isType ()
	{
		return descriptor().o_IsType(this);
	}

	/**
	 * Answer an {@linkplain Iterator iterator} suitable for traversing the
	 * elements of the {@code AvailObject receiver} with a Java
	 * <em>foreach</em> construct.
	 *
	 * @return An {@linkplain Iterator iterator}.
	 */
	@Override
	@ReferencedInGeneratedCode
	public IteratorNotNull<AvailObject> iterator ()
	{
		return descriptor().o_Iterator(this);
	}

	@Override
	public Spliterator<AvailObject> spliterator ()
	{
		return descriptor().o_Spliterator(this);
	}

	@Override
	public Stream<AvailObject> stream ()
	{
		return descriptor().o_Stream(this);
	}

	@Override
	public Stream<AvailObject> parallelStream ()
	{
		return descriptor().o_ParallelStream(this);
	}

	@Override
	public A_Set keysAsSet ()
	{
		return descriptor().o_KeysAsSet(this);
	}

	@Override
	public A_Type keyType ()
	{
		return descriptor().o_KeyType(this);
	}

	@Override
	public L2Chunk levelTwoChunk ()
	{
		return descriptor().o_LevelTwoChunk(this);
	}

	@Override
	public void levelTwoChunkOffset (
		final L2Chunk chunk,
		final int offset)
	{
		descriptor().o_LevelTwoChunkOffset(
			this,
			chunk,
			offset);
	}

	@Override
	public int levelTwoOffset ()
	{
		return descriptor().o_LevelTwoOffset(this);
	}

	@Override
	public AvailObject literal ()
	{
		return descriptor().o_Literal(this);
	}

	@Override
	public AvailObject literalAt (
		final int index)
	{
		return descriptor().o_LiteralAt(this, index);
	}

	@Override
	public AvailObject argOrLocalOrStackAt (
		final int index)
	{
		return descriptor().o_ArgOrLocalOrStackAt(this, index);
	}

	@Override
	public void argOrLocalOrStackAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().o_ArgOrLocalOrStackAtPut(
			this,
			index,
			value);
	}

	@Override
	public A_Type localTypeAt (
		final int index)
	{
		return descriptor().o_LocalTypeAt(this, index);
	}

	@Override
	public A_Definition lookupByTypesFromTuple (
			final A_Tuple argumentTypeTuple)
		throws MethodDefinitionException
	{
		return descriptor().o_LookupByTypesFromTuple(this, argumentTypeTuple);
	}

	@Override
	public A_Definition lookupByValuesFromList (
		final List<? extends A_BasicObject> argumentList)
	throws MethodDefinitionException
	{
		return descriptor().o_LookupByValuesFromList(this, argumentList);
	}

	@Override
	public A_Number lowerBound ()
	{
		return descriptor().o_LowerBound(this);
	}

	@Override
	public boolean lowerInclusive ()
	{
		return descriptor().o_LowerInclusive(this);
	}

	@Override
	public AvailObject makeImmutable ()
	{
		final Mutability mutability = descriptor().mutability;
		if (mutability != Mutability.MUTABLE)
		{
			return this;
		}
		return descriptor().o_MakeImmutable(this);
	}

	@Override
	public AvailObject makeShared ()
	{
		if (descriptor().mutability == Mutability.SHARED)
		{
			return this;
		}
		return descriptor().o_MakeShared(this);
	}

	@Override
	public AvailObject makeSubobjectsImmutable ()
	{
		return descriptor().o_MakeSubobjectsImmutable(this);
	}

	@Override
	public void makeSubobjectsShared ()
	{
		descriptor().o_MakeSubobjectsShared(this);
	}

	@Override
	public AvailObject mapAt (
		final A_BasicObject keyObject)
	{
		return descriptor().o_MapAt(this, keyObject);
	}

	@Override
	public A_Map mapAtPuttingCanDestroy (
		final A_BasicObject keyObject,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		return descriptor().o_MapAtPuttingCanDestroy(
			this,
			keyObject,
			newValueObject,
			canDestroy);
	}

	@Override
	public int mapBinSize ()
	{
		return descriptor().o_MapBinSize(this);
	}

	@Override
	public int mapSize ()
	{
		return descriptor().o_MapSize(this);
	}

	@Override
	public A_Map mapWithoutKeyCanDestroy (
		final A_BasicObject keyObject,
		final boolean canDestroy)
	{
		return descriptor().o_MapWithoutKeyCanDestroy(
			this,
			keyObject,
			canDestroy);
	}

	@Override
	public int maxStackDepth ()
	{
		return descriptor().o_MaxStackDepth(this);
	}

	@Override
	public A_Atom message ()
	{
		return descriptor().o_Message(this);
	}

	@Override
	public A_Tuple messageParts ()
	{
		return descriptor().o_MessageParts(this);
	}

	@Override
	public A_Set methodDefinitions ()
	{
		return descriptor().o_MethodDefinitions(this);
	}

	/**
	 * Subtract the argument {@code aNumber} from a receiver and answer
	 * the {@code AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * A_Number#subtractFromIntegerCanDestroy(AvailObject, boolean)
	 * subtractFromIntegerCanDestroy} or {@link
	 * #subtractFromInfinityCanDestroy(Sign, boolean)
	 * subtractFromInfinityCanDestroy}, where actual implementations of the
	 * subtraction operation should reside.</p>
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number minusCanDestroy (
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return descriptor().o_MinusCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Difference the receiver and the argument {@code aNumber} and answer the
	 * {@code AvailObject result}. The operation is not allowed to fail,
	 * so the caller must ensure that the arguments are valid, i.e. not
	 * {@linkplain InfinityDescriptor infinities} of like sign.
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number noFailMinusCanDestroy (
		final A_Number aNumber,
		final boolean canDestroy)
	{
		try
		{
			return descriptor().o_MinusCanDestroy(
				this,
				aNumber,
				canDestroy);
		}
		catch (final ArithmeticException e)
		{
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			assert false;
			error("noFailMinusCanDestroy failed!");
			return nil;
		}
	}

	/**
	 * Multiply the receiver and the argument {@code anInfinity} and answer the
	 * {@code AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #timesCanDestroy(A_Number, boolean) timesCanDestroy}. It exists for
	 * double-dispatch only.</p>
	 *
	 * @param sign
	 *        The {@link Sign} of an {@linkplain InfinityDescriptor infinity}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number multiplyByInfinityCanDestroy (
		final Sign sign,
		final boolean canDestroy)
	{
		return descriptor().o_MultiplyByInfinityCanDestroy(
			this,
			sign,
			canDestroy);
	}

	/**
	 * Multiply the receiver and the argument {@code anInteger} and answer the
	 * {@code AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #timesCanDestroy(A_Number, boolean) timesCanDestroy}. It exists for
	 * double-dispatch only.</p>
	 *
	 * @param anInteger
	 *        An {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number multiplyByIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor().o_MultiplyByIntegerCanDestroy(
			this, anInteger, canDestroy);
	}

	@Override
	public A_String atomName ()
	{
		return descriptor().o_AtomName(this);
	}

	@Override
	public A_Map importedNames ()
	{
		return descriptor().o_ImportedNames(this);
	}

	@Override
	public boolean nameVisible (
		final A_Atom trueName)
	{
		return descriptor().o_NameVisible(this, trueName);
	}

	@Override
	public A_Map newNames ()
	{
		return descriptor().o_NewNames(this);
	}

	@Override
	public int numArgs ()
	{
		return descriptor().o_NumArgs(this);
	}

	@Override
	public int numSlots ()
	{
		return descriptor().o_NumSlots(this);
	}

	@Override
	public int numLiterals ()
	{
		return descriptor().o_NumLiterals(this);
	}

	@Override
	public int numLocals ()
	{
		return descriptor().o_NumLocals(this);
	}

	@Override
	public int numConstants ()
	{
		return descriptor().o_NumConstants(this);
	}

	@Override
	public int numOuters ()
	{
		return descriptor().o_NumOuters(this);
	}

	@Override
	public int numOuterVars ()
	{
		return descriptor().o_NumOuterVars(this);
	}

	@Override
	public A_Tuple nybbles ()
	{
		return descriptor().o_Nybbles(this);
	}

	@Override
	public boolean optionallyNilOuterVar (
		final int index)
	{
		return descriptor().o_OptionallyNilOuterVar(this, index);
	}

	@Override
	public A_Type outerTypeAt (
		final int index)
	{
		return descriptor().o_OuterTypeAt(this, index);
	}

	@Override
	public AvailObject outerVarAt (
		final int index)
	{
		return descriptor().o_OuterVarAt(this, index);
	}

	@Override
	public void outerVarAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor().o_OuterVarAtPut(this, index, value);
	}
	@Override
	public A_BasicObject parent ()
	{
		return descriptor().o_Parent(this);
	}

	@Override
	public int pc ()
	{
		return descriptor().o_Pc(this);
	}

	/**
	 * Add the receiver and the argument {@code aNumber} and answer the
	 * {@code AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * A_Number#addToIntegerCanDestroy(AvailObject, boolean) addToIntegerCanDestroy} or
	 * {@link #addToInfinityCanDestroy(Sign, boolean)
	 * addToInfinityCanDestroy}, where actual implementations of the addition
	 * operation should reside.</p>
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number plusCanDestroy (
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return descriptor().o_PlusCanDestroy(
			this,
			aNumber,
			canDestroy);
	}

	/**
	 * Add the receiver and the argument {@code aNumber} and answer the
	 * {@code AvailObject result}. The operation is not allowed to fail,
	 * so the caller must ensure that the arguments are valid, i.e. not
	 * {@linkplain InfinityDescriptor infinities} of unlike sign.
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number noFailPlusCanDestroy (
		final A_Number aNumber,
		final boolean canDestroy)
	{
		try
		{
			return descriptor().o_PlusCanDestroy(
				this,
				aNumber,
				canDestroy);
		}
		catch (final ArithmeticException e)
		{
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			assert false;
			error("noFailPlusCanDestroy failed!");
			return nil;
		}
	}

	@Override
	public int primitiveNumber ()
	{
		return descriptor().o_PrimitiveNumber(this);
	}

	@Override
	public int priority ()
	{
		return descriptor().o_Priority(this);
	}

	@Override
	public void priority (final int value)
	{
		descriptor().o_Priority(this, value);
	}

	@Override
	public A_Map privateNames ()
	{
		return descriptor().o_PrivateNames(this);
	}

	@Override
	public A_Map fiberGlobals ()
	{
		return descriptor().o_FiberGlobals(this);
	}

	@Override
	public void fiberGlobals (final A_Map value)
	{
		descriptor().o_FiberGlobals(this, value);
	}

	@Override
	public short rawByteForCharacterAt (
		final int index)
	{
		return descriptor().o_RawByteForCharacterAt(this, index);
	}

	@Override
	public int rawShortForCharacterAt (
		final int index)
	{
		return descriptor().o_RawShortForCharacterAt(this, index);
	}

	@Override
	public void rawShortForCharacterAtPut (
		final int index,
		final int anInteger)
	{
		descriptor().o_RawShortForCharacterAtPut(
			this,
			index,
			anInteger);
	}

	@Override
	public int rawSignedIntegerAt (
		final int index)
	{
		return descriptor().o_RawSignedIntegerAt(this, index);
	}

	@Override
	public void rawSignedIntegerAtPut (
		final int index,
		final int value)
	{
		descriptor().o_RawSignedIntegerAtPut(
			this,
			index,
			value);
	}

	@Override
	public long rawUnsignedIntegerAt (
		final int index)
	{
		return descriptor().o_RawUnsignedIntegerAt(this, index);
	}

	@Override
	public void rawUnsignedIntegerAtPut (
		final int index,
		final int value)
	{
		descriptor().o_RawUnsignedIntegerAtPut(
			this,
			index,
			value);
	}

	@Override
	public void removeDependentChunk (final L2Chunk chunk)
	{
		descriptor().o_RemoveDependentChunk(this, chunk);
	}

	@Override
	public void removeFrom (
		final AvailLoader loader,
		final Continuation0 afterRemoval)
	{
		descriptor().o_RemoveFrom(this, loader, afterRemoval);
	}

	@Override
	public void removeDefinition (
		final A_Definition definition)
	{
		descriptor().o_RemoveDefinition(this, definition);
	}

	@Override
	public void removeGrammaticalRestriction (
		final A_GrammaticalRestriction obsoleteRestriction)
	{
		descriptor().o_RemoveGrammaticalRestriction(this, obsoleteRestriction);
	}

	@Override
	public void resolveForward (
		final A_BasicObject forwardDefinition)
	{
		descriptor().o_ResolveForward(this, forwardDefinition);
	}

	@Override
	public A_Set grammaticalRestrictions ()
	{
		return descriptor().o_GrammaticalRestrictions(this);
	}

	@Override
	public A_Type returnType ()
	{
		return descriptor().o_ReturnType(this);
	}

	@Override
	public void scanSubobjects (
		final AvailSubobjectVisitor visitor)
	{
		descriptor().o_ScanSubobjects(this, visitor);
	}

	@Override
	public A_Set setIntersectionCanDestroy (
		final A_Set otherSet,
		final boolean canDestroy)
	{
		return descriptor().o_SetIntersectionCanDestroy(
			this,
			otherSet,
			canDestroy);
	}

	@Override
	public A_Set setMinusCanDestroy (
		final A_Set otherSet,
		final boolean canDestroy)
	{
		return descriptor().o_SetMinusCanDestroy(
			this,
			otherSet,
			canDestroy);
	}

	@Override
	public int setSize ()
	{
		return descriptor().o_SetSize(this);
	}

	@Override
	public A_Set setUnionCanDestroy (
		final A_Set otherSet,
		final boolean canDestroy)
	{
		return descriptor().o_SetUnionCanDestroy(
			this,
			otherSet,
			canDestroy);
	}

	@Override
	public void setValue (final A_BasicObject newValue)
		throws VariableSetException
	{
		descriptor().o_SetValue(this, newValue);
	}

	@Override
	public void setValueNoCheck (final A_BasicObject newValue)
	{
		descriptor().o_SetValueNoCheck(this, newValue);
	}

	@Override
	public A_Set setWithElementCanDestroy (
		final A_BasicObject newElementObject,
		final boolean canDestroy)
	{
		return descriptor().o_SetWithElementCanDestroy(
			this,
			newElementObject,
			canDestroy);
	}

	@Override
	public A_Set setWithoutElementCanDestroy (
		final A_BasicObject elementObjectToExclude,
		final boolean canDestroy)
	{
		return descriptor().o_SetWithoutElementCanDestroy(
			this,
			elementObjectToExclude,
			canDestroy);
	}

	@Override
	public A_Type sizeRange ()
	{
		return descriptor().o_SizeRange(this);
	}

	@Override
	public A_Map lazyActions ()
	{
		return descriptor().o_LazyActions(this);
	}

	@Override
	public AvailObject stackAt (
		final int slotIndex)
	{
		return descriptor().o_StackAt(this, slotIndex);
	}

	@Override
	public int stackp ()
	{
		return descriptor().o_Stackp(this);
	}

	@Override
	public int start ()
	{
		return descriptor().o_Start(this);
	}

	@Override
	public L2Chunk startingChunk ()
	{
		return descriptor().o_StartingChunk(this);
	}

	@Override
	public void setStartingChunkAndReoptimizationCountdown (
		final L2Chunk chunk,
		final long countdown)
	{
		descriptor().o_SetStartingChunkAndReoptimizationCountdown(
			this, chunk, countdown);
	}

	@Override
	public A_String string ()
	{
		return descriptor().o_String(this);
	}

	/**
	 * Difference the {@code AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * #minusCanDestroy(A_Number, boolean) minusCanDestroy}. It
	 * exists for double-dispatch only.</p>
	 *
	 * @param sign
	 *        The sign of the {@linkplain InfinityDescriptor infinity} from
	 *        which to subtract.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number subtractFromInfinityCanDestroy (
		final Sign sign,
		final boolean canDestroy)
	{
		return descriptor().o_SubtractFromInfinityCanDestroy(
			this, sign, canDestroy);
	}

	/**
	 * Difference the {@code AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * #minusCanDestroy(A_Number, boolean) minusCanDestroy}. It
	 * exists for double-dispatch only.</p>
	 *
	 * @param anInteger
	 *        An {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number subtractFromIntegerCanDestroy (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor().o_SubtractFromIntegerCanDestroy(
			this, anInteger, canDestroy);
	}

	/**
	 * Multiply the receiver and the argument {@code aNumber} and answer the
	 * {@code AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * A_Number#multiplyByIntegerCanDestroy(AvailObject, boolean)
	 * multiplyByIntegerCanDestroy} or {@linkplain
	 * #multiplyByInfinityCanDestroy(Sign, boolean)
	 * multiplyByInfinityCanDestroy}, where actual implementations of the
	 * multiplication operation should reside.</p>
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number timesCanDestroy (
		final A_Number aNumber,
		final boolean canDestroy)
	{
		return descriptor().o_TimesCanDestroy(
			this, aNumber, canDestroy);
	}

	/**
	 * Multiply the receiver and the argument {@code aNumber} and answer the
	 * {@code AvailObject result}. The operation is not allowed to fail,
	 * so the caller must ensure that the arguments are valid, i.e. not
	 * {@linkplain IntegerDescriptor#zero() zero} and {@linkplain
	 * InfinityDescriptor infinity}.
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@code AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number noFailTimesCanDestroy (
		final A_Number aNumber,
		final boolean canDestroy)
	{
		try
		{
			return descriptor().o_TimesCanDestroy(
				this,
				aNumber,
				canDestroy);
		}
		catch (final ArithmeticException e)
		{
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			assert false;
			error("noFailTimesCanDestroy failed!");
			return nil;
		}
	}

	@Override
	public TokenType tokenType ()
	{
		return descriptor().o_TokenType(this);
	}

	@Override
	public AvailObject traversed ()
	{
		return descriptor().o_Traversed(this);
	}

	@Override
	public void trimExcessInts ()
	{
		descriptor().o_TrimExcessInts(this);
	}

	@Override
	public A_Set trueNamesForStringName (
		final A_String stringName)
	{
		return descriptor().o_TrueNamesForStringName(this, stringName);
	}

	@Override
	public AvailObject tupleAt (
		final int index)
	{
		return descriptor().o_TupleAt(this, index);
	}

	@Override
	public A_Tuple tupleAtPuttingCanDestroy (
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		return descriptor().o_TupleAtPuttingCanDestroy(
			this,
			index,
			newValueObject,
			canDestroy);
	}

	@Override
	public int tupleIntAt (
		final int index)
	{
		return descriptor().o_TupleIntAt(this, index);
	}

	/**
	 *Dispatch to the descriptor
	 */
	@Override
	public A_Tuple tupleReverse()
	{
		return descriptor().o_TupleReverse(this);
	}

	@Override
	public int tupleSize ()
	{
		return descriptor().o_TupleSize(this);
	}

	@Override
	public A_Type kind ()
	{
		return descriptor().o_Kind(this);
	}

	@Override
	public A_Type typeAtIndex (
		final int index)
	{
		return descriptor().o_TypeAtIndex(this, index);
	}

	@Override
	public A_Type typeIntersection (
		final A_Type another)
	{
		return descriptor().o_TypeIntersection(this, another);
	}

	@Override
	public A_Type typeIntersectionOfCompiledCodeType (
		final A_Type aCompiledCodeType)
	{
		return descriptor().o_TypeIntersectionOfCompiledCodeType(
			this,
			aCompiledCodeType);
	}

	@Override
	public A_Type typeIntersectionOfContinuationType (
		final A_Type aContinuationType)
	{
		return descriptor().o_TypeIntersectionOfContinuationType(
			this,
			aContinuationType);
	}

	@Override
	public A_Type typeIntersectionOfFiberType (
		final A_Type aFiberType)
	{
		return descriptor().o_TypeIntersectionOfFiberType(
			this,
			aFiberType);
	}

	@Override
	public A_Type typeIntersectionOfFunctionType (
		final A_Type aFunctionType)
	{
		return descriptor().o_TypeIntersectionOfFunctionType(
			this,
			aFunctionType);
	}

	@Override
	public A_Type typeIntersectionOfIntegerRangeType (
		final A_Type anIntegerRangeType)
	{
		return descriptor().o_TypeIntersectionOfIntegerRangeType(
			this,
			anIntegerRangeType);
	}

	@Override
	public A_Type typeIntersectionOfListNodeType (
		final A_Type aListNodeType)
	{
		return descriptor().o_TypeIntersectionOfListNodeType(
			this,
			aListNodeType);
	}

	@Override
	public A_Type typeIntersectionOfMapType (
		final A_Type aMapType)
	{
		return descriptor().o_TypeIntersectionOfMapType(this, aMapType);
	}

	@Override
	public A_Type typeIntersectionOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor().o_TypeIntersectionOfObjectType(this, anObjectType);
	}

	@Override
	public A_Type typeIntersectionOfPhraseType (
		final A_Type aPhraseType)
	{
		return descriptor().o_TypeIntersectionOfPhraseType(
			this,
			aPhraseType);
	}

	@Override
	public A_Type typeIntersectionOfPojoType (
		final A_Type aPojoType)
	{
		return descriptor().o_TypeIntersectionOfPojoType(this, aPojoType);
	}

	@Override
	public A_Type typeIntersectionOfSetType (
		final A_Type aSetType)
	{
		return descriptor().o_TypeIntersectionOfSetType(this, aSetType);
	}

	@Override
	public A_Type typeIntersectionOfTupleType (
		final A_Type aTupleType)
	{
		return descriptor().o_TypeIntersectionOfTupleType(this, aTupleType);
	}

	@Override
	public A_Type typeIntersectionOfVariableType (
		final A_Type aVariableType)
	{
		return descriptor().o_TypeIntersectionOfVariableType(this, aVariableType);
	}

	@Override
	public A_Tuple typeTuple ()
	{
		return descriptor().o_TypeTuple(this);
	}

	@Override
	public A_Type typeUnion (
		final A_Type another)
	{
		return descriptor().o_TypeUnion(this, another);
	}

	@Override
	public A_Type typeUnionOfFiberType (
		final A_Type aFiberType)
	{
		return descriptor().o_TypeUnionOfFiberType(
			this,
			aFiberType);
	}

	@Override
	public A_Type typeUnionOfFunctionType (
		final A_Type aFunctionType)
	{
		return descriptor().o_TypeUnionOfFunctionType(this, aFunctionType);
	}

	@Override
	public A_Type typeUnionOfVariableType (
		final A_Type aVariableType)
	{
		return descriptor().o_TypeUnionOfVariableType(this, aVariableType);
	}

	@Override
	public A_Type typeUnionOfContinuationType (
		final A_Type aContinuationType)
	{
		return descriptor().o_TypeUnionOfContinuationType(
			this, aContinuationType);
	}

	@Override
	public A_Type typeUnionOfIntegerRangeType (
		final A_Type anIntegerRangeType)
	{
		return descriptor().o_TypeUnionOfIntegerRangeType(
			this, anIntegerRangeType);
	}

	@Override
	public A_Type typeUnionOfListNodeType (
		final A_Type aListNodeType)
	{
		return descriptor().o_TypeUnionOfListNodeType(
			this,
			aListNodeType);
	}

	@Override
	public A_Type typeUnionOfMapType (
		final A_Type aMapType)
	{
		return descriptor().o_TypeUnionOfMapType(this, aMapType);
	}

	@Override
	public A_Type typeUnionOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor().o_TypeUnionOfObjectType(this, anObjectType);
	}

	@Override
	public A_Type typeUnionOfPhraseType (
		final A_Type aPhraseType)
	{
		return descriptor().o_TypeUnionOfPhraseType(
			this,
			aPhraseType);
	}

	@Override
	public A_Type typeUnionOfPojoType (
		final A_Type aPojoType)
	{
		return descriptor().o_TypeUnionOfPojoType(this, aPojoType);
	}

	@Override
	public A_Type typeUnionOfSetType (
		final A_Type aSetType)
	{
		return descriptor().o_TypeUnionOfSetType(this, aSetType);
	}

	@Override
	public A_Type typeUnionOfTupleType (
		final A_Type aTupleType)
	{
		return descriptor().o_TypeUnionOfTupleType(this, aTupleType);
	}

	@Override
	public A_Type unionOfTypesAtThrough (
		final int startIndex,
		final int endIndex)
	{
		return descriptor().o_UnionOfTypesAtThrough(
			this,
			startIndex,
			endIndex);
	}

	@Override
	public A_Number upperBound ()
	{
		return descriptor().o_UpperBound(this);
	}

	@Override
	public boolean upperInclusive ()
	{
		return descriptor().o_UpperInclusive(this);
	}

	@Override
	public AvailObject value ()
	{
		return descriptor().o_Value(this);
	}

	@Override
	public void value (
		final A_BasicObject value)
	{
		descriptor().o_Value(this, value);
	}

	@Override
	public A_Tuple valuesAsTuple ()
	{
		return descriptor().o_ValuesAsTuple(this);
	}

	@Override
	public A_Map variableBindings ()
	{
		return descriptor().o_VariableBindings(this);
	}

	@Override
	public A_Set visibleNames ()
	{
		return descriptor().o_VisibleNames(this);
	}

	@Override
	public A_Tuple parsingInstructions ()
	{
		return descriptor().o_ParsingInstructions(this);
	}

	/**
	 * Extract the expression from the {@linkplain
	 * PhraseKind#ASSIGNMENT_PHRASE assignment phrase} or {@linkplain
	 * PhraseKind#EXPRESSION_AS_STATEMENT_PHRASE expression-as-statement
	 * phrase}.
	 */
	@Override
	public A_Phrase expression ()
	{
		return descriptor().o_Expression(this);
	}

	@Override
	public A_Phrase variable ()
	{
		return descriptor().o_Variable(this);
	}

	@Override
	public A_Tuple argumentsTuple ()
	{
		return descriptor().o_ArgumentsTuple(this);
	}

	@Override
	public A_Tuple statementsTuple ()
	{
		return descriptor().o_StatementsTuple(this);
	}

	@Override
	public A_Type resultType ()
	{
		return descriptor().o_ResultType(this);
	}

	@Override
	public void neededVariables (final A_Tuple neededVariables)
	{
		descriptor().o_NeededVariables(this, neededVariables);
	}

	@Override
	public A_Tuple neededVariables ()
	{
		return descriptor().o_NeededVariables(this);
	}

	@Override
	public @Nullable Primitive primitive ()
	{
		return descriptor().o_Primitive(this);
	}

	@Override
	public A_Type declaredType ()
	{
		return descriptor().o_DeclaredType(this);
	}

	@Override
	public DeclarationKind declarationKind ()
	{
		return descriptor().o_DeclarationKind(this);
	}

	@Override
	public AvailObject initializationExpression ()
	{
		return descriptor().o_InitializationExpression(this);
	}

	@Override
	public AvailObject literalObject ()
	{
		return descriptor().o_LiteralObject(this);
	}

	@Override
	public A_Token token ()
	{
		return descriptor().o_Token(this);
	}

	@Override
	public AvailObject markerValue ()
	{
		return descriptor().o_MarkerValue(this);
	}

	@Override
	public A_Phrase argumentsListNode ()
	{
		return descriptor().o_ArgumentsListNode(this);
	}

	@Override
	public A_Bundle bundle ()
	{
		return descriptor().o_Bundle(this);
	}

	@Override
	public A_Tuple expressionsTuple ()
	{
		return descriptor().o_ExpressionsTuple(this);
	}

	@Override
	public A_Phrase declaration ()
	{
		return descriptor().o_Declaration(this);
	}

	@Override
	public A_Type expressionType ()
	{
		return descriptor().o_ExpressionType(this);
	}

	@Override
	public void emitEffectOn (final AvailCodeGenerator codeGenerator)
	{
		descriptor().o_EmitEffectOn(this, codeGenerator);
	}

	@Override
	public void emitValueOn (final AvailCodeGenerator codeGenerator)
	{
		descriptor().o_EmitValueOn(this, codeGenerator);
	}

	@Override
	public void childrenMap (
		final Transformer1<A_Phrase, A_Phrase> aBlock)
	{
		descriptor().o_ChildrenMap(this, aBlock);
	}

	@Override
	public void childrenDo (
		final Continuation1NotNull<A_Phrase> action)
	{
		descriptor().o_ChildrenDo(this, action);
	}

	@Override
	public void validateLocally (final @Nullable A_Phrase parent)
	{
		descriptor().o_ValidateLocally(
			this,
			parent);
	}

	@Override
	public A_RawFunction generateInModule (
		final A_Module module)
	{
		return descriptor().o_GenerateInModule(this, module);
	}

	@Override
	public A_Phrase copyWith (final A_Phrase newPhrase)
	{
		return descriptor().o_CopyWith(this, newPhrase);
	}

	@Override
	public A_Phrase copyConcatenating (final A_Phrase newListPhrase)
	{
		return descriptor().o_CopyConcatenating(this, newListPhrase);
	}

	@Override
	public void isLastUse (final boolean isLastUse)
	{
		descriptor().o_IsLastUse(this, isLastUse);
	}

	@Override
	public boolean isLastUse ()
	{
		return descriptor().o_IsLastUse(this);
	}

	@Override
	public boolean isMacroDefinition ()
	{
		return descriptor().o_IsMacroDefinition(this);
	}

	@Override
	public A_Phrase copyMutablePhrase ()
	{
		return descriptor().o_CopyMutablePhrase(this);
	}

	@Override
	public A_Type binUnionKind ()
	{
		return descriptor().o_BinUnionKind(this);
	}

	@Override
	public A_Phrase outputPhrase ()
	{
		return descriptor().o_OutputPhrase(this);
	}

	@Override
	public A_Atom apparentSendName ()
	{
		return descriptor().o_ApparentSendName(this);
	}

	@Override
	public A_Tuple statements ()
	{
		return descriptor().o_Statements(this);
	}

	@Override
	public void flattenStatementsInto (
		final List<A_Phrase> accumulatedStatements)
	{
		descriptor().o_FlattenStatementsInto(this, accumulatedStatements);
	}

	@Override
	public int lineNumber ()
	{
		return descriptor().o_LineNumber(this);
	}

	@Override
	public A_Map allParsingPlansInProgress ()
	{
		return descriptor().o_AllParsingPlansInProgress(this);
	}

	@Override
	public boolean isSetBin ()
	{
		return descriptor().o_IsSetBin(this);
	}

	@Override
	public MapIterable mapIterable ()
	{
		return descriptor().o_MapIterable(this);
	}

	@Override
	public A_Set declaredExceptions ()
	{
		return descriptor().o_DeclaredExceptions(this);
	}

	@Override
	public boolean isInt ()
	{
		return descriptor().o_IsInt(this);
	}

	@Override
	public boolean isLong ()
	{
		return descriptor().o_IsLong(this);
	}

	@Override
	public A_Type argsTupleType ()
	{
		return descriptor().o_ArgsTupleType(this);
	}

	@Override
	public boolean equalsInstanceTypeFor (
		final AvailObject anInstanceType)
	{
		return descriptor().o_EqualsInstanceTypeFor(this, anInstanceType);
	}

	@Override
	public A_Set instances ()
	{
		return descriptor().o_Instances(this);
	}

	/**
	 * Determine whether the receiver is an {@linkplain
	 * AbstractEnumerationTypeDescriptor enumeration} with the given {@linkplain
	 * SetDescriptor set} of instances.
	 *
	 * @param aSet A set of objects.
	 * @return Whether the receiver is an enumeration with the given
	 *         membership.
	 */
	@Override
	public boolean equalsEnumerationWithSet (final A_Set aSet)
	{
		return descriptor().o_EqualsEnumerationWithSet(this, aSet);
	}

	@Override
	public boolean isEnumeration ()
	{
		return descriptor().o_IsEnumeration(this);
	}

	@Override
	public boolean enumerationIncludesInstance (
		final AvailObject potentialInstance)
	{
		return descriptor().o_EnumerationIncludesInstance(
			this,
			potentialInstance);
	}

	@Override
	public A_Type valueType ()
	{
		return descriptor().o_ValueType(this);
	}

	@Override
	public A_Type computeSuperkind ()
	{
		return descriptor().o_ComputeSuperkind(this);
	}

	@Override
	public boolean equalsCompiledCodeType (final A_Type aCompiledCodeType)
	{
		return descriptor().o_EqualsCompiledCodeType(this, aCompiledCodeType);
	}

	@Override
	public boolean isSupertypeOfCompiledCodeType (
		final A_Type aCompiledCodeType)
	{
		return descriptor().o_IsSupertypeOfCompiledCodeType(
			this,
			aCompiledCodeType);
	}

	@Override
	public A_Type typeUnionOfCompiledCodeType (
		final A_Type aCompiledCodeType)
	{
		return descriptor().o_TypeUnionOfCompiledCodeType(
 			this,
 			aCompiledCodeType);
	}

	@Override
	public void setAtomProperty (
		final A_Atom key,
		final A_BasicObject value)
	{
		descriptor().o_SetAtomProperty(this, key, value);
	}

	@Override
	public AvailObject getAtomProperty (
		final A_Atom key)
	{
		return descriptor().o_GetAtomProperty(this, key);
	}

	@Override
	public boolean equalsEnumerationType (
		final A_BasicObject anEnumerationType)
	{
		return descriptor().o_EqualsEnumerationType(this, anEnumerationType);
	}

	@Override
	public A_Type readType ()
	{
		return descriptor().o_ReadType(this);
	}

	@Override
	public A_Type writeType ()
	{
		return descriptor().o_WriteType(this);
	}

	@Override
	public void versions (final A_Set versionStrings)
	{
		descriptor().o_Versions(this, versionStrings);
	}

	@Override
	public A_Set versions ()
	{
		return descriptor().o_Versions(this);
	}

	@Override
	public PhraseKind phraseKind ()
	{
		return descriptor().o_PhraseKind(this);
	}

	@Override
	public boolean phraseKindIsUnder (
		final PhraseKind expectedPhraseKind)
	{
		return descriptor().o_PhraseKindIsUnder(this, expectedPhraseKind);
	}

	@Override
	public boolean isRawPojo ()
	{
		return descriptor().o_IsRawPojo(this);
	}

	@Override
	public void addSemanticRestriction (
		final A_SemanticRestriction restriction)
	{
		descriptor().o_AddSemanticRestriction(this, restriction);
	}

	@Override
	public void removeSemanticRestriction (
		final A_SemanticRestriction restriction)
	{
		descriptor().o_RemoveSemanticRestriction(this, restriction);
	}

	@Override
	public A_Set semanticRestrictions ()
	{
		return descriptor().o_SemanticRestrictions(this);
	}

	@Override
	public void addSealedArgumentsType (final A_Tuple typeTuple)
	{
		descriptor().o_AddSealedArgumentsType(this, typeTuple);
	}

	@Override
	public void removeSealedArgumentsType (final A_Tuple typeTuple)
	{
		descriptor().o_RemoveSealedArgumentsType(this, typeTuple);
	}

	@Override
	public A_Tuple sealedArgumentsTypesTuple ()
	{
		return descriptor().o_SealedArgumentsTypesTuple(this);
	}

	@Override
	public void moduleAddSemanticRestriction (
		final A_SemanticRestriction semanticRestriction)
	{
		descriptor().o_ModuleAddSemanticRestriction(
			this,
			semanticRestriction);
	}

	@Override
	public void addConstantBinding (
		final A_String name,
		final A_Variable constantBinding)
	{
		descriptor().o_AddConstantBinding(
			this,
			name,
			constantBinding);
	}

	@Override
	public void addVariableBinding (
		final A_String name,
		final A_Variable variableBinding)
	{
		descriptor().o_AddVariableBinding(
			this,
			name,
			variableBinding);
	}

	@Override
	public boolean isMethodEmpty ()
	{
		return descriptor().o_IsMethodEmpty(this);
	}

	@Override
	public boolean isPojoSelfType ()
	{
		return descriptor().o_IsPojoSelfType(this);
	}

	@Override
	public A_Type pojoSelfType ()
	{
		return descriptor().o_PojoSelfType(this);
	}

	@Override
	public AvailObject javaClass ()
	{
		return descriptor().o_JavaClass(this);
	}

	@Override
	public boolean isUnsignedShort ()
	{
		return descriptor().o_IsUnsignedShort(this);
	}

	@Override
	public int extractUnsignedShort ()
	{
		return descriptor().o_ExtractUnsignedShort(this);
	}

	@Override
	public boolean isFloat ()
	{
		return descriptor().o_IsFloat(this);
	}

	@Override
	public boolean isDouble ()
	{
		return descriptor().o_IsDouble(this);
	}

	@Override
	public AvailObject rawPojo ()
	{
		return descriptor().o_RawPojo(this);
	}

	@Override
	public boolean isPojo ()
	{
		return descriptor().o_IsPojo(this);
	}

	@Override
	public boolean isPojoType ()
	{
		return descriptor().o_IsPojoType(this);
	}

	@Override
	public Order numericCompare (final A_Number another)
	{
		return descriptor().o_NumericCompare(this, another);
	}

	@Override
	public Order numericCompareToInfinity (
		final Sign sign)
	{
		return descriptor().o_NumericCompareToInfinity(this, sign);
	}

	@Override
	public Order numericCompareToDouble (final double aDouble)
	{
		return descriptor().o_NumericCompareToDouble(this, aDouble);
	}

	@Override
	public Order numericCompareToInteger (final AvailObject anInteger)
	{
		return descriptor().o_NumericCompareToInteger(this, anInteger);
	}

	@Override
	public A_Number addToDoubleCanDestroy (
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return descriptor().o_AddToDoubleCanDestroy (
			this,
			doubleObject,
			canDestroy);
	}

	@Override
	public A_Number addToFloatCanDestroy (
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return descriptor().o_AddToFloatCanDestroy (
			this,
			floatObject,
			canDestroy);
	}

	@Override
	public A_Number subtractFromDoubleCanDestroy (
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return descriptor().o_SubtractFromDoubleCanDestroy (
			this,
			doubleObject,
			canDestroy);
	}

	@Override
	public A_Number subtractFromFloatCanDestroy (
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return descriptor().o_SubtractFromFloatCanDestroy (
			this,
			floatObject,
			canDestroy);
	}

	@Override
	public A_Number multiplyByDoubleCanDestroy (
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return descriptor().o_MultiplyByDoubleCanDestroy (
			this,
			doubleObject,
			canDestroy);
	}

	@Override
	public A_Number multiplyByFloatCanDestroy (
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return descriptor().o_MultiplyByFloatCanDestroy (
			this,
			floatObject,
			canDestroy);
	}

	@Override
	public A_Number divideIntoDoubleCanDestroy (
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return descriptor().o_DivideIntoDoubleCanDestroy (
			this,
			doubleObject,
			canDestroy);
	}

	@Override
	public A_Number divideIntoFloatCanDestroy (
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return descriptor().o_DivideIntoFloatCanDestroy (
			this,
			floatObject,
			canDestroy);
	}

	@Override
	public A_Map lazyPrefilterMap ()
	{
		return descriptor().o_LazyPrefilterMap(this);
	}

	@Override
	public SerializerOperation serializerOperation ()
	{
		return descriptor().o_SerializerOperation(this);
	}

	@Override
	public A_MapBin mapBinAtHashPutLevelCanDestroy (
		final A_BasicObject key,
		final int keyHash,
		final A_BasicObject value,
		final byte myLevel,
		final boolean canDestroy)
	{
		return descriptor().o_MapBinAtHashPutLevelCanDestroy(
			this,
			key,
			keyHash,
			value,
			myLevel,
			canDestroy);
	}

	@Override
	public A_MapBin mapBinRemoveKeyHashCanDestroy (
		final A_BasicObject key,
		final int keyHash,
		final boolean canDestroy)
	{
		return descriptor().o_MapBinRemoveKeyHashCanDestroy(
			this,
			key,
			keyHash,
			canDestroy);
	}

	@Override
	public A_Type mapBinKeyUnionKind ()
	{
		return descriptor().o_MapBinKeyUnionKind(this);
	}

	@Override
	public A_Type mapBinValueUnionKind ()
	{
		return descriptor().o_MapBinValueUnionKind(this);
	}

	@Override
	public boolean isHashedMapBin ()
	{
		return descriptor().o_IsHashedMapBin(this);
	}

	/**
	 * Look up the key in this {@linkplain MapBinDescriptor map bin}.  If not
	 * found, answer {@code null}.  Use the provided hash of the key.
	 *
	 * @param key The key to look up in this map.
	 * @param keyHash The conveniently already computed hash of the key.
	 * @return The value under that key in the map, or null if not found.
	 */
	@Override
	public @Nullable AvailObject mapBinAtHash (
		final A_BasicObject key,
		final int keyHash)
	{
		return descriptor().o_MapBinAtHash(this, key, keyHash);
	}

	@Override
	public int mapBinKeysHash ()
	{
		return descriptor().o_MapBinKeysHash(this);
	}

	@Override
	public int mapBinValuesHash ()
	{
		return descriptor().o_MapBinValuesHash(this);
	}

	@Override
	public A_Module issuingModule ()
	{
		return descriptor().o_IssuingModule(this);
	}

	@Override
	public boolean isPojoFusedType ()
	{
		return descriptor().o_IsPojoFusedType(this);
	}

	@Override
	public boolean isSupertypeOfPojoBottomType (
		final A_Type aPojoType)
	{
		return descriptor().o_IsSupertypeOfPojoBottomType(this, aPojoType);
	}

	@Override
	public boolean equalsPojoBottomType ()
	{
		return descriptor().o_EqualsPojoBottomType(this);
	}

	@Override
	public AvailObject javaAncestors ()
	{
		return descriptor().o_JavaAncestors(this);
	}

	@Override
	public A_Type typeIntersectionOfPojoFusedType (
		final A_Type aFusedPojoType)
	{
		return descriptor().o_TypeIntersectionOfPojoFusedType(
			this, aFusedPojoType);
	}

	@Override
	public A_Type typeIntersectionOfPojoUnfusedType (
		final A_Type anUnfusedPojoType)
	{
		return descriptor().o_TypeIntersectionOfPojoUnfusedType(
			this, anUnfusedPojoType);
	}

	@Override
	public A_Type typeUnionOfPojoFusedType (
		final A_Type aFusedPojoType)
	{
		return descriptor().o_TypeUnionOfPojoFusedType(
			this, aFusedPojoType);
	}

	@Override
	public A_Type typeUnionOfPojoUnfusedType (
		final A_Type anUnfusedPojoType)
	{
		return descriptor().o_TypeUnionOfPojoUnfusedType(
			this, anUnfusedPojoType);
	}

	@Override
	public boolean isPojoArrayType ()
	{
		return descriptor().o_IsPojoArrayType(this);
	}

	@Override
	public @Nullable Object marshalToJava (final @Nullable Class<?> classHint)
	{
		return descriptor().o_MarshalToJava(this, classHint);
	}

	@Override
	public A_Map typeVariables ()
	{
		return descriptor().o_TypeVariables(this);
	}

	@Override
	public boolean equalsPojoField (
		final AvailObject field,
		final AvailObject receiver)
	{
		return descriptor().o_EqualsPojoField(this, field, receiver);
	}

	@Override
	public boolean isSignedByte ()
	{
		return descriptor().o_IsSignedByte(this);
	}

	@Override
	public boolean isSignedShort ()
	{
		return descriptor().o_IsSignedShort(this);
	}

	@Override
	public byte extractSignedByte ()
	{
		return descriptor().o_ExtractSignedByte(this);
	}

	@Override
	public short extractSignedShort ()
	{
		return descriptor().o_ExtractSignedShort(this);
	}

	@Override
	public boolean equalsEqualityRawPojoFor (
		final AvailObject otherEqualityRawPojo,
		final @Nullable Object otherJavaObject)
	{
		return descriptor().o_EqualsEqualityRawPojo(
			this,
			otherEqualityRawPojo,
			otherJavaObject);
	}

	@Override
	public @Nullable <T> T javaObject ()
	{
		return descriptor().o_JavaObject(this);
	}

	@Override
	public <T> T javaObjectNotNull ()
	{
		return stripNull(descriptor().o_JavaObject(this));
	}

	@Override
	public BigInteger asBigInteger ()
	{
		return descriptor().o_AsBigInteger(this);
	}

	@Override
	public A_Tuple appendCanDestroy (
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		return descriptor().o_AppendCanDestroy(this, newElement, canDestroy);
	}

	@Override
	public A_Map lazyIncompleteCaseInsensitive ()
	{
		return descriptor().o_LazyIncompleteCaseInsensitive(this);
	}

	@Override
	public A_String lowerCaseString ()
	{
		return descriptor().o_LowerCaseString(this);
	}

	@Override
	public A_Number instanceCount ()
	{
		return descriptor().o_InstanceCount(this);
	}

	@Override
	public long totalInvocations ()
	{
		return descriptor().o_TotalInvocations(this);
	}

	/**
	 *
	 */
	@Override
	public void tallyInvocation ()
	{
		descriptor().o_TallyInvocation(this);
	}

	@Override
	public A_Tuple fieldTypeTuple ()
	{
		return descriptor().o_FieldTypeTuple(this);
	}

	@Override
	public A_Tuple fieldTuple ()
	{
		return descriptor().o_FieldTuple(this);
	}

	@Override
	public A_Type literalType ()
	{
		return descriptor().o_LiteralType(this);
	}

	@Override
	public A_Type typeIntersectionOfTokenType (
		final A_Type aTokenType)
	{
		return descriptor().o_TypeIntersectionOfTokenType(
			this,
			aTokenType);
	}

	@Override
	public A_Type typeIntersectionOfLiteralTokenType (
		final A_Type aLiteralTokenType)
	{
		return descriptor().o_TypeIntersectionOfLiteralTokenType(
			this,
			aLiteralTokenType);
	}

	@Override
	public A_Type typeUnionOfTokenType (
		final A_Type aTokenType)
	{
		return descriptor().o_TypeUnionOfTokenType(
			this,
			aTokenType);
	}

	@Override
	public A_Type typeUnionOfLiteralTokenType (
		final A_Type aLiteralTokenType)
	{
		return descriptor().o_TypeUnionOfLiteralTokenType(
			this,
			aLiteralTokenType);
	}

	@Override
	public boolean isTokenType ()
	{
		return descriptor().o_IsTokenType(this);
	}

	@Override
	public boolean isLiteralTokenType ()
	{
		return descriptor().o_IsLiteralTokenType(this);
	}

	@Override
	public boolean isLiteralToken ()
	{
		return descriptor().o_IsLiteralToken(this);
	}

	@Override
	public boolean equalsTokenType (
		final A_Type aTokenType)
	{
		return descriptor().o_EqualsTokenType(this, aTokenType);
	}

	@Override
	public boolean equalsLiteralTokenType (
		final A_Type aLiteralTokenType)
	{
		return descriptor().o_EqualsLiteralTokenType(this, aLiteralTokenType);
	}

	@Override
	public boolean equalsObjectType (
		final AvailObject anObjectType)
	{
		return descriptor().o_EqualsObjectType(this, anObjectType);
	}

	@Override
	public boolean equalsToken (
		final A_Token aToken)
	{
		return descriptor().o_EqualsToken(this, aToken);
	}

	@Override
	public A_Number bitwiseAnd (
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return descriptor().o_BitwiseAnd(this, anInteger, canDestroy);
	}

	@Override
	public A_Number bitwiseOr (
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return descriptor().o_BitwiseOr(this, anInteger, canDestroy);
	}

	@Override
	public A_Number bitwiseXor (
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return descriptor().o_BitwiseXor(this, anInteger, canDestroy);
	}

	@Override
	public void addSeal (
		final A_Atom methodName,
		final A_Tuple sealSignature)
	{
		descriptor().o_AddSeal(this, methodName, sealSignature);
	}

	@Override
	public boolean isInstanceMeta ()
	{
		return descriptor().o_IsInstanceMeta(this);
	}

	@Override
	public AvailObject instance ()
	{
		return descriptor().o_Instance(this);
	}

	@Override
	public void setMethodName (
		final A_String methodName)
	{
		descriptor().o_SetMethodName(this, methodName);
	}

	@Override
	public int startingLineNumber ()
	{
		return descriptor().o_StartingLineNumber(this);
	}

	@Override
	public A_Module module ()
	{
		return descriptor().o_Module(this);
	}

	@Override
	public A_String methodName ()
	{
		return descriptor().o_MethodName(this);
	}

	@Override
	public boolean binElementsAreAllInstancesOfKind (
		final A_Type kind)
	{
		return descriptor().o_BinElementsAreAllInstancesOfKind(this, kind);
	}

	@Override
	public boolean setElementsAreAllInstancesOfKind (
		final AvailObject kind)
	{
		return descriptor().o_SetElementsAreAllInstancesOfKind(this, kind);
	}

	@Override
	public MapIterable mapBinIterable ()
	{
		return descriptor().o_MapBinIterable(this);
	}

	@Override
	public boolean rangeIncludesInt (final int anInt)
	{
		return descriptor().o_RangeIncludesInt(this, anInt);
	}

	@Override
	public A_Number bitShiftLeftTruncatingToBits (
		final A_Number shiftFactor,
		final A_Number truncationBits,
		final boolean canDestroy)
	{
		return descriptor().o_BitShiftLeftTruncatingToBits(
			this,
			shiftFactor,
			truncationBits,
			canDestroy);
	}

	@Override
	public SetIterator setBinIterator ()
	{
		return descriptor().o_SetBinIterator(this);
	}

	@Override
	public A_Number bitShift (
		final A_Number shiftFactor,
		final boolean canDestroy)
	{
		return descriptor().o_BitShift(this, shiftFactor, canDestroy);
	}

	@Override
	public boolean equalsPhrase (final A_Phrase aPhrase)
	{
		return descriptor().o_EqualsPhrase(this, aPhrase);
	}

	@Override
	public A_Phrase stripMacro ()
	{
		return descriptor().o_StripMacro(this);
	}

	/**
	 * Answer the {@link MethodDescriptor method} that this {@link
	 * DefinitionDescriptor definition} is for.
	 *
	 * @return The definition's method.
	 */
	@Override
	public A_Method definitionMethod ()
	{
		return descriptor().o_DefinitionMethod(this);
	}

	@Override
	public A_Tuple prefixFunctions ()
	{
		return descriptor().o_PrefixFunctions(this);
	}

	@Override
	public boolean equalsByteArrayTuple (
		final A_Tuple aByteArrayTuple)
	{
		return descriptor().o_EqualsByteArrayTuple(this, aByteArrayTuple);
	}

	@Override
	public boolean compareFromToWithByteArrayTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteArrayTuple,
		final int startIndex2)
	{
		return descriptor().o_CompareFromToWithByteArrayTupleStartingAt(
			this, startIndex1, endIndex1, aByteArrayTuple, startIndex2);
	}

	@Override
	public byte[] byteArray ()
	{
		return descriptor().o_ByteArray(this);
	}

	@Override
	public boolean isByteArrayTuple ()
	{
		return descriptor().o_IsByteArrayTuple(this);
	}

	@Override
	public void updateForNewGrammaticalRestriction (
		final A_ParsingPlanInProgress planInProgress,
		final Collection<Pair<A_BundleTree, A_ParsingPlanInProgress>>
			treesToVisit)
	{
		descriptor().o_UpdateForNewGrammaticalRestriction(
			this, planInProgress, treesToVisit);
	}

	@Override
	public void lock (final Continuation0 critical)
	{
		descriptor().o_Lock(this, critical);
	}

	@Override
	public <T> T lock (final Supplier<T> supplier)
	{
		return descriptor().o_Lock(this, supplier);
	}

	/**
	 * Answer the {@linkplain AvailLoader loader} bound to the {@linkplain
	 * FiberDescriptor receiver}, or {@code null} if the receiver is not a
	 * loader fiber.
	 *
	 * @return An Avail loader, or {@code null} if no Avail loader is
	 *         associated with the specified fiber.
	 */
	@Override
	public @Nullable AvailLoader availLoader ()
	{
		return descriptor().o_AvailLoader(this);
	}

	@Override
	public void availLoader (final @Nullable AvailLoader loader)
	{
		descriptor().o_AvailLoader(this, loader);
	}

	@Override
	public A_String moduleName ()
	{
		return descriptor().o_ModuleName(this);
	}

	@Override
	public A_Method bundleMethod ()
	{
		return descriptor().o_BundleMethod(this);
	}

	/**
	 * Answer the {@linkplain Continuation1NotNull continuation} that accepts
	 * the result produced by the {@linkplain FiberDescriptor receiver}'s
	 * successful completion.
	 *
	 * @return A continuation.
	 */
	@Override
	public Continuation1NotNull<AvailObject> resultContinuation ()
	{
		return descriptor().o_ResultContinuation(this);
	}

	/**
	 * Answer the {@linkplain Continuation1NotNull continuation} that accepts
	 * the {@linkplain Throwable throwable} responsible for abnormal termination
	 * of the {@linkplain FiberDescriptor receiver}.
	 *
	 * @return A continuation.
	 */
	@Override
	public Continuation1NotNull<Throwable> failureContinuation ()
	{
		return descriptor().o_FailureContinuation(this);
	}

	@Override
	public void setSuccessAndFailureContinuations (
		final Continuation1NotNull<AvailObject> onSuccess,
		final Continuation1NotNull<Throwable> onFailure)
	{
		descriptor().o_SetSuccessAndFailureContinuations(
			this, onSuccess, onFailure);
	}

	/**
	 * Is the specified {@linkplain InterruptRequestFlag interrupt request flag}
	 * set for the {@linkplain FiberDescriptor receiver}?
	 *
	 * @param flag An interrupt request flag.
	 * @return {@code true} if the interrupt request flag is set, {@code
	 *         false} otherwise.
	 */
	@Override
	public boolean interruptRequestFlag (final InterruptRequestFlag flag)
	{
		return descriptor().o_InterruptRequestFlag(this, flag);
	}

	@Override
	public AvailObject getAndSetValue (final A_BasicObject newValue)
		throws VariableGetException, VariableSetException
	{
		return descriptor().o_GetAndSetValue(this, newValue);
	}

	@Override
	public boolean compareAndSwapValues (
			final A_BasicObject reference,
			final A_BasicObject newValue)
		throws VariableGetException, VariableSetException
	{
		return descriptor().o_CompareAndSwapValues(this, reference, newValue);
	}

	@Override
	public A_Number fetchAndAddValue (final A_Number addend)
		throws VariableGetException, VariableSetException
	{
		return descriptor().o_FetchAndAddValue(this, addend);
	}

	@Override
	public boolean getAndClearInterruptRequestFlag (
		final InterruptRequestFlag flag)
	{
		return descriptor().o_GetAndClearInterruptRequestFlag(this, flag);
	}

	@Override
	public boolean getAndSetSynchronizationFlag (
		final SynchronizationFlag flag,
		final boolean newValue)
	{
		return descriptor().o_GetAndSetSynchronizationFlag(this, flag, newValue);
	}

	@Override
	public AvailObject fiberResult ()
	{
		return descriptor().o_FiberResult(this);
	}

	@Override
	public void fiberResult (final A_BasicObject result)
	{
		descriptor().o_FiberResult(this, result);
	}

	@Override
	public A_Set joiningFibers ()
	{
		return descriptor().o_JoiningFibers(this);
	}

	@Override
	public @Nullable TimerTask wakeupTask ()
	{
		return descriptor().o_WakeupTask(this);
	}

	@Override
	public void wakeupTask (final @Nullable TimerTask task)
	{
		descriptor().o_WakeupTask(this, task);
	}

	@Override
	public void joiningFibers (final A_Set joiners)
	{
		descriptor().o_JoiningFibers(this, joiners);
	}

	@Override
	public A_Map heritableFiberGlobals ()
	{
		return descriptor().o_HeritableFiberGlobals(this);
	}

	@Override
	public void heritableFiberGlobals (final A_Map globals)
	{
		descriptor().o_HeritableFiberGlobals(this, globals);
	}

	@Override
	public boolean generalFlag (final GeneralFlag flag)
	{
		return descriptor().o_GeneralFlag(this, flag);
	}

	@Override
	public void setGeneralFlag (final GeneralFlag flag)
	{
		descriptor().o_SetGeneralFlag(this, flag);
	}

	@Override
	public void clearGeneralFlag (final GeneralFlag flag)
	{
		descriptor().o_ClearGeneralFlag(this, flag);
	}

	@Override
	public ByteBuffer byteBuffer ()
	{
		return descriptor().o_ByteBuffer(this);
	}

	@Override
	public boolean equalsByteBufferTuple (final A_Tuple aByteBufferTuple)
	{
		return descriptor().o_EqualsByteBufferTuple(this, aByteBufferTuple);
	}

	@Override
	public boolean compareFromToWithByteBufferTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteBufferTuple,
		final int startIndex2)
	{
		return descriptor().o_CompareFromToWithByteBufferTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aByteBufferTuple,
			startIndex2);
	}

	@Override
	public boolean isByteBufferTuple ()
	{
		return descriptor().o_IsByteBufferTuple(this);
	}

	@Override
	public A_String fiberName ()
	{
		return descriptor().o_FiberName(this);
	}

	@Override
	public void fiberNameSupplier (final Supplier<A_String> supplier)
	{
		descriptor().o_FiberNameSupplier(this, supplier);
	}

	@Override
	public A_Set bundles ()
	{
		return descriptor().o_Bundles(this);
	}

	@Override
	public void methodAddBundle (final A_Bundle bundle)
	{
		descriptor().o_MethodAddBundle(this, bundle);
	}

	@Override
	public A_Module definitionModule ()
	{
		return descriptor().o_DefinitionModule(this);
	}

	@Override
	public A_String definitionModuleName ()
	{
		return descriptor().o_DefinitionModuleName(this);
	}

	@Override
	public A_Bundle bundleOrCreate () throws MalformedMessageException
	{
		return descriptor().o_BundleOrCreate(this);
	}

	@Override
	public A_Bundle bundleOrNil ()
	{
		return descriptor().o_BundleOrNil(this);
	}

	@Override
	public A_Map entryPoints ()
	{
		return descriptor().o_EntryPoints(this);
	}

	@Override
	public void addEntryPoint (
		final A_String stringName,
		final A_Atom trueName)
	{
		descriptor().o_AddEntryPoint(this, stringName, trueName);
	}

	@Override
	public A_Set allAncestors ()
	{
		return descriptor().o_AllAncestors(this);
	}

	@Override
	public void addAncestors (final A_Set moreAncestors)
	{
		descriptor().o_AddAncestors(this, moreAncestors);
	}

	@Override
	public A_Tuple argumentRestrictionSets ()
	{
		return descriptor().o_ArgumentRestrictionSets(this);
	}

	@Override
	public A_Bundle restrictedBundle ()
	{
		return descriptor().o_RestrictedBundle(this);
	}

	@Override
	public void adjustPcAndStackp (final int pc, final int stackp)
	{
		descriptor().o_AdjustPcAndStackp(this, pc, stackp);
	}

	@Override
	public int treeTupleLevel ()
	{
		return descriptor().o_TreeTupleLevel(this);
	}

	@Override
	public int childCount ()
	{
		return descriptor().o_ChildCount(this);
	}

	@Override
	public A_Tuple childAt (final int childIndex)
	{
		return descriptor().o_ChildAt(this, childIndex);
	}

	@Override
	public A_Tuple concatenateWith (
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		return descriptor().o_ConcatenateWith(this, otherTuple, canDestroy);
	}

	@Override
	public A_Tuple replaceFirstChild (final A_Tuple newFirst)
	{
		return descriptor().o_ReplaceFirstChild(this, newFirst);
	}

	@Override
	public boolean isByteString ()
	{
		return descriptor().o_IsByteString(this);
	}

	@Override
	public boolean isTwoByteString ()
	{
		return descriptor().o_IsTwoByteString(this);
	}

	@Override
	public void addWriteReactor (
		final A_Atom key,
		final VariableAccessReactor reactor)
	{
		descriptor().o_AddWriteReactor(this, key, reactor);
	}

	@Override
	public void removeWriteReactor (final A_Atom key) throws AvailException
	{
		descriptor().o_RemoveWriteReactor(this, key);
	}

	@Override
	public boolean traceFlag (final TraceFlag flag)
	{
		return descriptor().o_TraceFlag(this, flag);
	}

	@Override
	public void setTraceFlag (final TraceFlag flag)
	{
		descriptor().o_SetTraceFlag(this, flag);
	}

	@Override
	public void clearTraceFlag (final TraceFlag flag)
	{
		descriptor().o_ClearTraceFlag(this, flag);
	}

	@Override
	public void recordVariableAccess (
		final A_Variable var,
		final boolean wasRead)
	{
		descriptor().o_RecordVariableAccess(this, var, wasRead);
	}

	@Override
	public A_Set variablesReadBeforeWritten ()
	{
		return descriptor().o_VariablesReadBeforeWritten(this);
	}

	@Override
	public A_Set variablesWritten ()
	{
		return descriptor().o_VariablesWritten(this);
	}

	@Override
	public A_Set validWriteReactorFunctions ()
	{
		return descriptor().o_ValidWriteReactorFunctions(this);
	}

	@Override
	public A_Continuation replacingCaller (final A_Continuation newCaller)
	{
		return descriptor().o_ReplacingCaller(this, newCaller);
	}

	@Override
	public void whenContinuationIsAvailableDo (
		final Continuation1NotNull<A_Continuation> whenReified)
	{
		descriptor().o_WhenContinuationIsAvailableDo(this, whenReified);
	}

	@Override
	public A_Set getAndClearReificationWaiters ()
	{
		return descriptor().o_GetAndClearReificationWaiters(this);
	}

	@Override
	public boolean isBottom ()
	{
		return descriptor().o_IsBottom(this);
	}

	@Override
	public boolean isVacuousType ()
	{
		return descriptor().o_IsVacuousType(this);
	}

	@Override
	public boolean isTop ()
	{
		return descriptor().o_IsTop(this);
	}

	@Override
	public boolean isAtomSpecial ()
	{
		return descriptor().o_IsAtomSpecial(this);
	}

	@Override
	public void addPrivateNames (final A_Set trueNames)
	{
		descriptor().o_AddPrivateNames(this, trueNames);
	}

	@Override
	public boolean hasValue ()
	{
		return descriptor().o_HasValue(this);
	}

	@Override
	public void addUnloadFunction (final A_Function unloadFunction)
	{
		descriptor().o_AddUnloadFunction(this, unloadFunction);
	}

	@Override
	public A_Set exportedNames ()
	{
		return descriptor().o_ExportedNames(this);
	}

	@Override
	public boolean isInitializedWriteOnceVariable ()
	{
		return descriptor().o_IsInitializedWriteOnceVariable(this);
	}

	@Override
	public void transferIntoByteBuffer (
		final int startIndex,
		final int endIndex,
		final ByteBuffer outputByteBuffer)
	{
		descriptor().o_TransferIntoByteBuffer(
			this, startIndex, endIndex, outputByteBuffer);
	}

	@Override
	public boolean tupleElementsInRangeAreInstancesOf (
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		return descriptor().o_TupleElementsInRangeAreInstancesOf(
			this, startIndex, endIndex, type);
	}

	@Override
	public boolean isNumericallyIntegral ()
	{
		return descriptor().o_IsNumericallyIntegral(this);
	}

	@Override
	public TextInterface textInterface ()
	{
		return descriptor().o_TextInterface(this);
	}

	@Override
	public void textInterface (final TextInterface textInterface)
	{
		descriptor().o_TextInterface(this, textInterface);
	}

	@Override
	public void writeTo (final JSONWriter writer)
	{
		descriptor().o_WriteTo(this, writer);
	}

	@Override
	public void writeSummaryTo (final JSONWriter writer)
	{
		descriptor().o_WriteSummaryTo(this, writer);
	}

	@Override
	public A_Type typeIntersectionOfPrimitiveTypeEnum (
		final Types primitiveTypeEnum)
	{
		return descriptor().o_TypeIntersectionOfPrimitiveTypeEnum(
			this, primitiveTypeEnum);
	}

	@Override
	public A_Type typeUnionOfPrimitiveTypeEnum (final Types primitiveTypeEnum)
	{
		return descriptor().o_TypeUnionOfPrimitiveTypeEnum(
			this, primitiveTypeEnum);
	}

	@Override
	public A_Tuple tupleOfTypesFromTo (
		final int startIndex,
		final int endIndex)
	{
		return descriptor().o_TupleOfTypesFromTo(this, startIndex, endIndex);
	}

	@Override
	public A_Phrase list ()
	{
		return descriptor().o_List(this);
	}

	@Override
	public A_Tuple permutation ()
	{
		return descriptor().o_Permutation(this);
	}

	@Override
	public void emitAllValuesOn (final AvailCodeGenerator codeGenerator)
	{
		descriptor().o_EmitAllValuesOn(this, codeGenerator);
	}

	@Override
	public A_Type superUnionType ()
	{
		return descriptor().o_SuperUnionType(this);
	}

	@Override
	public boolean hasSuperCast ()
	{
		return descriptor().o_HasSuperCast(this);
	}

	@Override
	public A_Tuple macroDefinitionsTuple ()
	{
		return descriptor().o_MacroDefinitionsTuple(this);
	}

	@Override
	public A_Tuple lookupMacroByPhraseTuple (
		final A_Tuple argumentPhraseTuple)
	{
		return descriptor().o_LookupMacroByPhraseTuple(
			this, argumentPhraseTuple);
	}

	@Override
	public A_Phrase expressionAt (final int index)
	{
		return descriptor().o_ExpressionAt(this, index);
	}

	@Override
	public int expressionsSize ()
	{
		return descriptor().o_ExpressionsSize(this);
	}

	@Override
	public A_Phrase lastExpression () {
		return descriptor().o_LastExpression(this);
	}

	@Override
	public int parsingPc ()
	{
		return descriptor().o_ParsingPc(this);
	}

	@Override
	public boolean isMacroSubstitutionNode ()
	{
		return descriptor().o_IsMacroSubstitutionNode(this);
	}

	@Override
	public MessageSplitter messageSplitter ()
	{
		return descriptor().o_MessageSplitter(this);
	}

	@Override
	public void statementsDo (final Continuation1NotNull<A_Phrase> continuation)
	{
		descriptor().o_StatementsDo(this, continuation);
	}

	@Override
	public A_Phrase macroOriginalSendNode ()
	{
		return descriptor().o_MacroOriginalSendNode(this);
	}

	@Override
	public boolean equalsInt (final int theInt)
	{
		return descriptor().o_EqualsInt(this, theInt);
	}

	@Override
	public A_Tuple tokens ()
	{
		return descriptor().o_Tokens(this);
	}

	@Override
	public A_Bundle chooseBundle (final A_Module currentModule)
	{
		return descriptor().o_ChooseBundle(this, currentModule);
	}

	@Override
	public boolean valueWasStablyComputed ()
	{
		return descriptor().o_ValueWasStablyComputed(this);
	}

	@Override
	public void valueWasStablyComputed (final boolean wasStablyComputed)
	{
		descriptor().o_ValueWasStablyComputed(this, wasStablyComputed);
	}

	@Override
	public long uniqueId ()
	{
		return descriptor().o_UniqueId(this);
	}

	@Override
	public A_Definition definition ()
	{
		return descriptor().o_Definition(this);
	}

	@Override
	public String nameHighlightingPc ()
	{
		return descriptor().o_NameHighlightingPc(this);
	}

	@Override
	public boolean setIntersects (final A_Set otherSet)
	{
		return descriptor().o_SetIntersects(this, otherSet);
	}

	@Override
	public void removePlanForDefinition (final A_Definition definition)
	{
		descriptor().o_RemovePlanForDefinition(this, definition);
	}

	@Override
	public A_Map definitionParsingPlans ()
	{
		return descriptor().o_DefinitionParsingPlans(this);
	}

	@Override
	public boolean equalsListNodeType (final A_Type aListNodeType)
	{
		return descriptor().o_EqualsListNodeType(this, aListNodeType);
	}

	@Override
	public A_Type subexpressionsTupleType ()
	{
		return descriptor().o_SubexpressionsTupleType(this);
	}

	@Override
	public A_BasicObject lazyTypeFilterTreePojo ()
	{
		return descriptor().o_LazyTypeFilterTreePojo(this);
	}

	@Override
	public void addPlanInProgress (final A_ParsingPlanInProgress planInProgress)
	{
		descriptor().o_AddPlanInProgress(this, planInProgress);
	}

	@Override
	public A_Type parsingSignature ()
	{
		return descriptor().o_ParsingSignature(this);
	}

	@Override
	public void removePlanInProgress (
		final A_ParsingPlanInProgress planInProgress)
	{
		descriptor().o_RemovePlanInProgress(this, planInProgress);
	}

	@Override
	public A_Set moduleSemanticRestrictions ()
	{
		return descriptor().o_ModuleSemanticRestrictions(this);
	}

	@Override
	public A_Set moduleGrammaticalRestrictions ()
	{
		return descriptor().o_ModuleGrammaticalRestrictions(this);
	}

	@Override
	public AvailObject fieldAt (final A_Atom field)
	{
		return descriptor().o_FieldAt(this, field);
	}

	@Override
	public A_BasicObject fieldAtPuttingCanDestroy (
		final A_Atom field,
		final A_BasicObject value,
		final boolean canDestroy)
	{
		return descriptor().o_FieldAtPuttingCanDestroy(
			this, field, value, canDestroy);
	}

	@Override
	public A_Type fieldTypeAt (final A_Atom field)
	{
		return descriptor().o_FieldTypeAt(this, field);
	}

	@Override
	public A_DefinitionParsingPlan parsingPlan ()
	{
		return descriptor().o_ParsingPlan(this);
	}

	@Override
	public void atomicAddToMap (
		final A_BasicObject key,
		final A_BasicObject value)
	throws VariableGetException, VariableSetException
	{
		descriptor().o_AtomicAddToMap(this, key, value);
	}

	@Override
	public boolean variableMapHasKey (final A_BasicObject key)
	throws VariableGetException
	{
		return descriptor().o_VariableMapHasKey(this, key);
	}

	@Override
	public A_Method lexerMethod ()
	{
		return descriptor().o_LexerMethod(this);
	}

	@Override
	public A_Function lexerFilterFunction ()
	{
		return descriptor().o_LexerFilterFunction(this);
	}

	@Override
	public A_Function lexerBodyFunction ()
	{
		return descriptor().o_LexerBodyFunction(this);
	}

	@Override
	public void setLexer (final A_Lexer lexer)
	{
		descriptor().o_SetLexer(this, lexer);
	}

	@Override
	public void addLexer (final A_Lexer lexer)
	{
		descriptor().o_AddLexer(this, lexer);
	}

	@Override
	public A_Phrase originatingPhrase ()
	{
		return descriptor().o_OriginatingPhrase(this);
	}

	@Override
	public A_Phrase typeExpression ()
	{
		return descriptor().o_TypeExpression(this);
	}

	@Override
	public boolean isGlobal ()
	{
		return descriptor().o_IsGlobal(this);
	}

	@Override
	public A_Module globalModule ()
	{
		return descriptor().o_GlobalModule(this);
	}

	@Override
	public A_String globalName ()
	{
		return descriptor().o_GlobalName(this);
	}

	@Override
	public LexingState nextLexingState ()
	{
		return descriptor().o_NextLexingState(this);
	}

	@Override
	public void setNextLexingStateFromPrior (
		final LexingState priorLexingState)
	{
		descriptor().o_SetNextLexingStateFromPrior(this, priorLexingState);
	}

	@Override
	public int tupleCodePointAt (final int index)
	{
		return descriptor().o_TupleCodePointAt(this, index);
	}

	@Override
	public LexicalScanner createLexicalScanner ()
	{
		return descriptor().o_CreateLexicalScanner(this);
	}

	@Override
	public A_Lexer lexer ()
	{
		return descriptor().o_Lexer(this);
	}

	@Override
	public void suspendingFunction (
		final A_Function suspendingFunction)
	{
		descriptor().o_SuspendingFunction(this, suspendingFunction);
	}

	@Override
	public A_Function suspendingFunction ()
	{
		return descriptor().o_SuspendingFunction(this);
	}

	@Override
	public boolean isBackwardJump ()
	{
		return descriptor().o_IsBackwardJump(this);
	}

	@Override
	public A_BundleTree latestBackwardJump ()
	{
		return descriptor().o_LatestBackwardJump(this);
	}

	@Override
	public boolean hasBackwardJump ()
	{
		return descriptor().o_HasBackwardJump(this);
	}

	@Override
	public boolean isSourceOfCycle ()
	{
		return descriptor().o_IsSourceOfCycle(this);
	}

	@Override
	public void isSourceOfCycle (
		final boolean isSourceOfCycle)
	{
		descriptor().o_IsSourceOfCycle(this, isSourceOfCycle);
	}

	@Override
	public StringBuilder debugLog ()
	{
		return descriptor().o_DebugLog(this);
	}

	@Override
	public A_Type constantTypeAt (final int index)
	{
		return descriptor().o_ConstantTypeAt(this, index);
	}

	@Override
	public Statistic returnerCheckStat ()
	{
		return descriptor().o_ReturnerCheckStat(this);
	}

	@Override
	public Statistic returneeCheckStat ()
	{
		return descriptor().o_ReturneeCheckStat(this);
	}

	@Override
	public int numNybbles ()
	{
		return descriptor().o_NumNybbles(this);
	}

	@Override
	public A_Tuple lineNumberEncodedDeltas ()
	{
		return descriptor().o_LineNumberEncodedDeltas(this);
	}

	@Override
	public int currentLineNumber ()
	{
		return descriptor().o_CurrentLineNumber(this);
	}

	@Override
	public A_Type fiberResultType ()
	{
		return descriptor().o_FiberResultType(this);
	}

	@Override
	public LookupTree<A_Definition, A_Tuple, Boolean> testingTree ()
	{
		return descriptor().o_TestingTree(this);
	}

	@Override
	public void forEach (
		final BiConsumer<? super AvailObject, ? super AvailObject> action)
	{
		descriptor().o_ForEach(this, action);
	}

	@Override
	public void forEachInMapBin (
		final BiConsumer<? super AvailObject, ? super AvailObject> action)
	{
		descriptor().o_ForEachInMapBin(this, action);
	}

	@Override
	public void clearLexingState ()
	{
		descriptor().o_ClearLexingState(this);
	}
}
