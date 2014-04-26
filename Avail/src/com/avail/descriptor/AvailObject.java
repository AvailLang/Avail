/**
 * AvailObject.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.descriptor.AbstractNumberDescriptor.Order;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.FiberDescriptor.GeneralFlag;
import com.avail.descriptor.FiberDescriptor.InterruptRequestFlag;
import com.avail.descriptor.FiberDescriptor.SynchronizationFlag;
import com.avail.descriptor.FiberDescriptor.TraceFlag;
import com.avail.descriptor.InfinityDescriptor.IntegerSlots;
import com.avail.descriptor.MethodDescriptor.LookupTree;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.SetDescriptor.SetIterator;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.exceptions.*;
import com.avail.exceptions.ArithmeticException;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.serialization.*;
import com.avail.utility.evaluation.*;
import com.avail.utility.visitor.*;

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
		A_Fiber,
		A_Function,
		A_GrammaticalRestriction,
		A_Map,
		A_Method,
		A_Module,
		A_Number,
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
	 */
	@Override
	public void printOnAvoidingIndent (
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
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
			for (final A_BasicObject candidate : recursionList)
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

	/**
	 * Utility method for decomposing this object in the debugger.  See {@link
	 * AvailObjectFieldHelper} for instructions to enable this functionality in
	 * Eclipse.
	 *
	 * @return An array of {@link AvailObjectFieldHelper} objects that help
	 *         describe the logical structure of the receiver to the debugger.
	 */
	@Override
	public AvailObjectFieldHelper[] describeForDebugger()
	{
		return descriptor.o_DescribeForDebugger(this);
	}

	/**
	 * Answer a name suitable for labeling a field containing this object.
	 *
	 * @return An Avail {@linkplain StringDescriptor string}.
	 */
	@Override
	public String nameForDebugger()
	{
		return descriptor.o_NameForDebugger(this);
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
		return descriptor.o_ShowValueInNameForDebugger(this);
	}

	@Override
	public String toString ()
	{
		final StringBuilder stringBuilder = new StringBuilder(100);
		final List<A_BasicObject> recursionList =
			new ArrayList<>(10);
		printOnAvoidingIndent(stringBuilder, recursionList, 1);
		assert recursionList.size() == 0;
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
	public final boolean greaterThan (
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
	public final boolean greaterOrEqual (
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
	public final boolean lessThan (
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
	public final boolean lessOrEqual (
		final A_Number another)
	{
		return numericCompare(another).isLessOrEqual();
	}

	/**
	 * Set up the object to report nice obvious errors if anyone ever accesses
	 * it again.
	 */
	void assertObjectUnreachableIfMutable ()
	{
		assertObjectUnreachableIfMutableExcept(NilDescriptor.nil());
	}

	/**
	 * Set up the object to report nice obvious errors if anyone ever accesses
	 * it again.
	 *
	 * @param exceptMe
	 *            An optional sub-object not to destroy even if it's mutable.
	 */
	void assertObjectUnreachableIfMutableExcept (
		final AvailObject exceptMe)
	{

		checkValidAddress();
		if (!descriptor().isMutable())
		{
			return;
		}
		if (sameAddressAs(exceptMe))
		{
			error("What happened?  This object is also the excluded one.");
		}

		// Recursively invoke the iterator on the subobjects of self...
		final AvailSubobjectVisitor vis =
			new MarkUnreachableSubobjectVisitor(exceptMe);
		scanSubobjects(vis);
		destroy();
		return;
	}

	/**
	 * Replace my descriptor field with a {@link FillerDescriptor}.  This blows
	 * up for most messages, catching incorrect (all, by definition) further
	 * accidental uses of this object.
	 */
	@Override
	public void setToInvalidDescriptor ()
	{
		descriptor = FillerDescriptor.shared;
	}

	/**
	 * Compute the 32-bit hash of the receiver.
	 *
	 * @return An {@code int} hash value.
	 */
	@Override
	public final int hash ()
	{
		return descriptor.o_Hash(this);
	}

	/**
	 * Construct a new {@link AvailObjectRepresentation}.
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
	 * Answer whether the {@linkplain AvailObject#argsTupleType() argument
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
		return descriptor.o_AcceptsArgTypesFromFunctionType(this, functionType);
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
		return descriptor.o_AcceptsListOfArgTypes(this, argTypes);
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
		return descriptor.o_AcceptsListOfArgValues(this, argValues);
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
		return descriptor.o_AcceptsTupleOfArgTypes(this, argTypes);
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
		return descriptor.o_AcceptsTupleOfArguments(this, arguments);
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
		descriptor.o_AddDependentChunk(this, chunk);
	}

	/**
	 * Add the {@linkplain DefinitionDescriptor definition} to the receiver, a
	 * {@linkplain MethodDefinitionDescriptor method}.  Causes dependent chunks
	 * to be invalidated.
	 *
	 * Macro signatures and non-macro signatures should not be combined in the
	 * same method.
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
		descriptor.o_MethodAddDefinition(this, definition);
	}

	/**
	 * Add a set of {@linkplain GrammaticalRestrictionDescriptor grammatical
	 * restrictions} to this {@linkplain MessageBundleDescriptor message
	 * bundle}.
	 *
	 * @param grammaticalRestriction The set of grammatical restrictions to be added.
	 */
	@Override
	public void addGrammaticalRestriction (
		final A_GrammaticalRestriction grammaticalRestriction)
	{
		descriptor.o_AddGrammaticalRestriction(this, grammaticalRestriction);
	}

	/**
	 * Add the receiver and the argument {@code anInfinity} and answer the
	 * {@linkplain AvailObject result}.
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
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number addToInfinityCanDestroy (
		final Sign sign,
		final boolean canDestroy)
	{
		return descriptor.o_AddToInfinityCanDestroy(
			this,
			sign,
			canDestroy);
	}

	/**
	 * Add the receiver and the argument {@code anInteger} and answer the
	 * {@linkplain AvailObject result}.
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
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number addToIntegerCanDestroy (
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return descriptor.o_AddToIntegerCanDestroy(
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
		return descriptor.o_AsNativeString(this);
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
		return descriptor.o_AsSet(this);
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
		return descriptor.o_AsTuple(this);
	}

	/**
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	@Override
	public void addGrammaticalRestrictions (
		final A_Atom methodName,
		final A_Tuple illegalArgMsgs)
	{
		descriptor.o_AddGrammaticalRestrictions(
			this,
			methodName,
			illegalArgMsgs);
	}

	/**
	 * @param definition
	 */
	@Override
	public void moduleAddDefinition (
		final A_BasicObject definition)
	{
		descriptor.o_ModuleAddDefinition(
			this,
			definition);
	}

	/**
	 * @param bundle
	 */
	@Override
	public void addBundle (
		final A_Bundle bundle)
	{
		descriptor.o_AddBundle(
			this,
			bundle);
	}

	/**
	 * @param trueName
	 */
	@Override
	public void addImportedName (
		final A_Atom trueName)
	{
		descriptor.o_AddImportedName(
			this,
			trueName);
	}

	/**
	 * @param trueName
	 */
	@Override
	public void introduceNewName (
		final A_Atom trueName)
	{
		descriptor.o_IntroduceNewName(
			this,
			trueName);
	}

	/**
	 * @param trueName
	 */
	@Override
	public void addPrivateName (
		final A_Atom trueName)
	{
		descriptor.o_AddPrivateName(
			this,
			trueName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_BasicObject setBinAddingElementHashLevelCanDestroy (
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		return descriptor.o_SetBinAddingElementHashLevelCanDestroy(
			this,
			elementObject,
			elementObjectHash,
			myLevel,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject binElementAt (
		final int index)
	{
		return descriptor.o_BinElementAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void binElementAtPut (
		final int index,
		final A_BasicObject value)
	{
		descriptor.o_BinElementAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean binHasElementWithHash (
		final A_BasicObject elementObject,
		final int elementObjectHash)
	{
		return descriptor.o_BinHasElementWithHash(
			this,
			elementObject,
			elementObjectHash);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int binHash ()
	{
		return descriptor.o_BinHash(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void binHash (
		final int value)
	{
		descriptor.o_BinHash(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject binRemoveElementHashLevelCanDestroy (
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		return descriptor.o_BinRemoveElementHashLevelCanDestroy(
			this,
			elementObject,
			elementObjectHash,
			myLevel,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int binSize ()
	{
		return descriptor.o_BinSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void binSize (
		final int value)
	{
		descriptor.o_BinSize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int bitsPerEntry ()
	{
		return descriptor.o_BitsPerEntry(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Function bodyBlock ()
	{
		return descriptor.o_BodyBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type bodySignature ()
	{
		return descriptor.o_BodySignature(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_BasicObject breakpointBlock ()
	{
		return descriptor.o_BreakpointBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void breakpointBlock (
		final AvailObject value)
	{
		descriptor.o_BreakpointBlock(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_BundleTree buildFilteredBundleTree ()
	{
		return descriptor.o_BuildFilteredBundleTree(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Continuation caller ()
	{
		return descriptor.o_Caller(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void clearValue ()
	{
		descriptor.o_ClearValue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Function function ()
	{
		return descriptor.o_Function(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type functionType ()
	{
		return descriptor.o_FunctionType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_RawFunction code ()
	{
		return descriptor.o_Code(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int codePoint ()
	{
		return descriptor.o_CodePoint(this);
	}

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
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
		return descriptor.o_CompareFromToWithStartingAt(
			this,
			startIndex1,
			endIndex1,
			anotherObject,
			startIndex2);
	}

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
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
		return descriptor.o_CompareFromToWithAnyTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aTuple,
			startIndex2);
	}

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
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
		return descriptor.o_CompareFromToWithByteStringStartingAt(
			this,
			startIndex1,
			endIndex1,
			aByteString,
			startIndex2);
	}

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
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
		return descriptor.o_CompareFromToWithByteTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aByteTuple,
			startIndex2);
	}

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
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
		return descriptor.o_CompareFromToWithIntegerIntervalTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			anIntegerIntervalTuple,
			startIndex2);
	}

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
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
		return descriptor.o_CompareFromToWithSmallIntegerIntervalTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aSmallIntegerIntervalTuple,
			startIndex2);
	}

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
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
		return descriptor.o_CompareFromToWithNybbleTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aNybbleTuple,
			startIndex2);
	}

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
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
		return descriptor.o_CompareFromToWithObjectTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			anObjectTuple,
			startIndex2);
	}

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
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
	@Override
	public A_Map lazyComplete ()
	{
		return descriptor.o_LazyComplete(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
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
	@Override
	public A_Tuple concatenateTuplesCanDestroy (
		final boolean canDestroy)
	{
		return descriptor.o_ConcatenateTuplesCanDestroy(this, canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Map constantBindings ()
	{
		return descriptor.o_ConstantBindings(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type contentType ()
	{
		return descriptor.o_ContentType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Continuation continuation ()
	{
		return descriptor.o_Continuation(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void continuation (
		final A_Continuation value)
	{
		descriptor.o_Continuation(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Tuple copyAsMutableObjectTuple ()
	{
		return descriptor.o_CopyAsMutableObjectTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Tuple copyTupleFromToCanDestroy (
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
	@Override
	public boolean couldEverBeInvokedWith (
		final List<? extends A_Type> argTypes)
	{
		return descriptor.o_CouldEverBeInvokedWith(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type defaultType ()
	{
		return descriptor.o_DefaultType(this);
	}

	/**
	 * Divide the receiver by the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #divideIntoIntegerCanDestroy(A_Number, boolean)
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
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number divideCanDestroy (
		final A_Number aNumber,
		final boolean canDestroy)
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
	@Override
	public A_Number noFailDivideCanDestroy (
		final A_Number aNumber,
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
			error("noFailDivideCanDestroy failed!");
			return NilDescriptor.nil();
		}
	}

	/**
	 * Divide an infinity with the given {@linkplain Sign sign} by the receiver
	 * and answer the {@linkplain AvailObject result}.
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
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number divideIntoInfinityCanDestroy (
		final Sign sign,
		final boolean canDestroy)
	{
		return descriptor.o_DivideIntoInfinityCanDestroy(
			this,
			sign,
			canDestroy);
	}

	/**
	 * Divide the argument {@code anInteger} by the receiver and answer the
	 * {@linkplain AvailObject result}.
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
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number divideIntoIntegerCanDestroy (
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return descriptor.o_DivideIntoIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Continuation ensureMutable ()
	{
		return descriptor.o_EnsureMutable(this);
	}

	/**
	 * Answer whether the receiver and the argument, both {@linkplain
	 * AvailObject objects}, are equal in value.
	 *
	 * Note that the argument is of type {@link AvailObject} so that correctly
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
		return descriptor.o_Equals(this, another);
	}

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
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
		return descriptor.o_EqualsAnyTuple(this, aTuple);
	}

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
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
		return descriptor.o_EqualsByteString(this, aByteString);
	}

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
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
		return descriptor.o_EqualsByteTuple(this, aByteTuple);
	}

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
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
		return descriptor.o_EqualsIntegerIntervalTuple(
			this,
			anIntegerIntervalTuple);
	}

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
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
		return descriptor.o_EqualsSmallIntegerIntervalTuple(
			this,
			aSmallIntegerIntervalTuple);
	}
	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, is a
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
		return descriptor.o_EqualsCharacterWithCodePoint(this, aCodePoint);
	}

	@Override
	public boolean equalsFiberType (final A_Type aFiberType)
	{
		return descriptor.o_EqualsFiberType(this, aFiberType);
	}

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
	 * argument, a {@linkplain FunctionDescriptor function}, are equal in value.
	 *
	 * @param aFunction The function used in the comparison.
	 * @return {@code true} if the receiver is a function and of value equal to
	 *         the argument, {@code false} otherwise.
	 * @see AvailObject#equalsFunction(A_Function)
	 */
	@Override
	public boolean equalsFunction (
		final A_Function aFunction)
	{
		return descriptor.o_EqualsFunction(this, aFunction);
	}

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
	 * argument, a {@linkplain FunctionTypeDescriptor function type}, are equal.
	 *
	 * @param aFunctionType The function type used in the comparison.
	 * @return {@code true} IFF the receiver is also a function type and:
	 *
	 * <p><ul>
	 * <li>The {@linkplain AvailObject#argsTupleType() argument types}
	 * correspond,</li>
	 * <li>The {@linkplain AvailObject#returnType() return types}
	 * correspond, and</li>
	 * <li>The {@linkplain AvailObject#declaredExceptions() raise types}
	 * correspond.</li>
	 * </ul></p>
	 */
	@Override
	public boolean equalsFunctionType (
		final A_Type aFunctionType)
	{
		return descriptor.o_EqualsFunctionType(this, aFunctionType);
	}

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
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
		return descriptor.o_EqualsCompiledCode(this, aCompiledCode);
	}

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
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
		return descriptor.o_EqualsVariable(this, aVariable);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsVariableType (
		final A_Type aVariableType)
	{
		return descriptor.o_EqualsVariableType(this, aVariableType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsContinuation (
		final A_Continuation aContinuation)
	{
		return descriptor.o_EqualsContinuation(this, aContinuation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsContinuationType (
		final A_Type aContinuationType)
	{
		return descriptor.o_EqualsContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsDouble (
		final double aDouble)
	{
		return descriptor.o_EqualsDouble(this, aDouble);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsFloat (
		final float aFloat)
	{
		return descriptor.o_EqualsFloat(this, aFloat);
	}

	/**
	 * Answer whether the {@linkplain AvailObject receiver} is an {@linkplain
	 * InfinityDescriptor infinity} with the specified {@link
	 * IntegerSlots#SIGN}.
	 *
	 * @param sign The type of infinity for comparison.
	 * @return {@code true} if the receiver is an infinity of the specified
	 *         sign, {@code false} otherwise.
	 */
	@Override
	public boolean equalsInfinity (
		final Sign sign)
	{
		return descriptor.o_EqualsInfinity(this, sign);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsInteger (
		final A_Number anAvailInteger)
	{
		return descriptor.o_EqualsInteger(this, anAvailInteger);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsIntegerRangeType (
		final A_Type anIntegerRangeType)
	{
		return descriptor.o_EqualsIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsMap (
		final A_Map aMap)
	{
		return descriptor.o_EqualsMap(this, aMap);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsMapType (
		final A_Type aMapType)
	{
		return descriptor.o_EqualsMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsNybbleTuple (
		final A_Tuple aNybbleTuple)
	{
		return descriptor.o_EqualsNybbleTuple(this, aNybbleTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsObject (
		final AvailObject anObject)
	{
		return descriptor.o_EqualsObject(this, anObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsObjectTuple (
		final A_Tuple anObjectTuple)
	{
		return descriptor.o_EqualsObjectTuple(this, anObjectTuple);
	}

	/**
	 * @param aParseNodeType
	 * @return
	 */
	@Override
	public boolean equalsParseNodeType (
		final A_Type aParseNodeType)
	{
		return descriptor.o_EqualsParseNodeType(this, aParseNodeType);
	}

	/**
	 * @param aPojo
	 * @return
	 */
	@Override
	public boolean equalsPojo (final AvailObject aPojo)
	{
		return descriptor.o_EqualsPojo(this, aPojo);
	}

	/**
	 * @param aPojoType
	 * @return
	 */
	@Override
	public boolean equalsPojoType (final AvailObject aPojoType)
	{
		return descriptor.o_EqualsPojoType(this, aPojoType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsPrimitiveType (
		final A_Type aPrimitiveType)
	{
		return descriptor.o_EqualsPrimitiveType(this, aPrimitiveType);
	}

	/**
	 * @param otherRawPojo
	 * @param otherJavaObject
	 * @return
	 */
	@Override
	public boolean equalsRawPojoFor (
		final AvailObject otherRawPojo,
		final @Nullable Object otherJavaObject)
	{
		return descriptor.o_EqualsRawPojoFor(
			this,
			otherRawPojo,
			otherJavaObject);
	}

	/**
	 * Answer whether the receiver and the argument tuple,
	 * both {@linkplain AvailObject objects}, are equal in value.
	 *
	 * Note that the argument is of type {@link AvailObject} so that correctly
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
		return descriptor.o_EqualsReverseTuple(this, aTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsSet (
		final A_Set aSet)
	{
		return descriptor.o_EqualsSet(this, aSet);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsSetType (
		final A_Type aSetType)
	{
		return descriptor.o_EqualsSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsTupleType (
		final A_Type aTupleType)
	{
		return descriptor.o_EqualsTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsTwoByteString (
		final A_String aTwoByteString)
	{
		return descriptor.o_EqualsTwoByteString(this, aTwoByteString);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsNil ()
	{
		return descriptor.o_EqualsNil(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public ExecutionState executionState ()
	{
		return descriptor.o_ExecutionState(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void executionState (
		final ExecutionState value)
	{
		descriptor.o_ExecutionState(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void expand (final A_Module module)
	{
		descriptor.o_Expand(this, module);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean extractBoolean ()
	{
		return descriptor.o_ExtractBoolean(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public short extractUnsignedByte ()
	{
		return descriptor.o_ExtractUnsignedByte(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public double extractDouble ()
	{
		return descriptor.o_ExtractDouble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public float extractFloat ()
	{
		return descriptor.o_ExtractFloat(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int extractInt ()
	{
		return descriptor.o_ExtractInt(this);
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
		return descriptor.o_ExtractLong(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public byte extractNybble ()
	{
		return descriptor.o_ExtractNybble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public byte extractNybbleFromTupleAt (
		final int index)
	{
		return descriptor.o_ExtractNybbleFromTupleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Map fieldMap ()
	{
		return descriptor.o_FieldMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Map fieldTypeMap ()
	{
		return descriptor.o_FieldTypeMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public List<A_Definition> filterByTypes (
		final List<? extends A_Type> argTypes)
	{
		return descriptor.o_FilterByTypes(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject getValue ()
	{
		return descriptor.o_GetValue(this);
	}

	/**
	 * Answer whether the {@linkplain AvailObject receiver} contains the
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
		return descriptor.o_HasElement(this, elementObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
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
	@Override
	public int hashOrZero ()
	{
		return descriptor.o_HashOrZero(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void hashOrZero (
		final int value)
	{
		descriptor.o_HashOrZero(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean hasKey (
		final A_BasicObject keyObject)
	{
		return descriptor.o_HasKey(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean hasObjectInstance (
		final AvailObject potentialInstance)
	{
		return descriptor.o_HasObjectInstance(this, potentialInstance);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean hasGrammaticalRestrictions ()
	{
		return descriptor.o_HasGrammaticalRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public List<A_Definition> definitionsAtOrBelow (
		final List<? extends A_Type> argTypes)
	{
		return descriptor.o_DefinitionsAtOrBelow(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Tuple definitionsTuple ()
	{
		return descriptor.o_DefinitionsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean includesDefinition (
		final A_Definition imp)
	{
		return descriptor.o_IncludesDefinition(this, imp);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Map lazyIncomplete ()
	{
		return descriptor.o_LazyIncomplete(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void setInterruptRequestFlag (
		final InterruptRequestFlag flag)
	{
		descriptor.o_SetInterruptRequestFlag(this, flag);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void decrementCountdownToReoptimize (
		final Continuation0 continuation)
	{
		descriptor.o_DecrementCountdownToReoptimize(this, continuation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void countdownToReoptimize (
		final int value)
	{
		descriptor.o_CountdownToReoptimize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isAbstract ()
	{
		return descriptor.o_IsAbstract(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isAbstractDefinition ()
	{
		return descriptor.o_IsAbstractDefinition(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isBetterRepresentationThan (
		final A_BasicObject anotherObject)
	{
		return descriptor.o_IsBetterRepresentationThan(this, anotherObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isBetterRepresentationThanTupleType (
		final A_Type aTupleType)
	{
		return descriptor.o_IsBetterRepresentationThanTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isBinSubsetOf (
		final A_Set potentialSuperset)
	{
		return descriptor.o_IsBinSubsetOf(this, potentialSuperset);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail boolean?
	 *
	 * @return {@code true} if the receiver is a boolean, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isBoolean ()
	{
		return descriptor.o_IsBoolean(this);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail unsigned byte?
	 *
	 * @return {@code true} if the argument is an unsigned byte, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isUnsignedByte ()
	{
		return descriptor.o_IsUnsignedByte(this);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail byte tuple?
	 *
	 * @return {@code true} if the receiver is a byte tuple, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isByteTuple ()
	{
		return descriptor.o_IsByteTuple(this);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail character?
	 *
	 * @return {@code true} if the receiver is a character, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isCharacter ()
	{
		return descriptor.o_IsCharacter(this);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail function?
	 *
	 * @return {@code true} if the receiver is a function, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isFunction ()
	{
		return descriptor.o_IsFunction(this);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail atom?
	 *
	 * @return {@code true} if the receiver is an atom, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isAtom ()
	{
		return descriptor.o_IsAtom(this);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail extended integer?
	 *
	 * @return {@code true} if the receiver is an extended integer, {@code
	 *         false} otherwise.
	 */
	@Override
	public boolean isExtendedInteger ()
	{
		return descriptor.o_IsExtendedInteger(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isFinite ()
	{
		return descriptor.o_IsFinite(this);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} a {@linkplain
	 * ForwardDefinitionDescriptor forward declaration site}?
	 *
	 * @return {@code true} if the receiver is a forward declaration site.
	 */
	@Override
	public boolean isForwardDefinition ()
	{
		return descriptor.o_IsForwardDefinition(this);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} a {@linkplain
	 * MethodDefinitionDescriptor method definition}?
	 *
	 * @return {@code true} if the receiver is a method definition.
	 */
	@Override
	public boolean isMethodDefinition ()
	{
		return descriptor.o_IsMethodDefinition(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isInstanceOf (
		final A_Type aType)
	{
		return descriptor.o_IsInstanceOf(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isInstanceOfKind (
		final A_Type aType)
	{
		return descriptor.o_IsInstanceOfKind(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isIntegerIntervalTuple ()
	{
		return descriptor.o_IsIntegerIntervalTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSmallIntegerIntervalTuple ()
	{
		return descriptor.o_IsSmallIntegerIntervalTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isIntegerRangeType ()
	{
		return descriptor.o_IsIntegerRangeType(this);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail map?
	 *
	 * @return {@code true} if the receiver is a map, {@code false} otherwise.
	 */
	@Override
	public boolean isMap ()
	{
		return descriptor.o_IsMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isMapType ()
	{
		return descriptor.o_IsMapType(this);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail nybble?
	 *
	 * @return {@code true} if the receiver is a nybble, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isNybble ()
	{
		return descriptor.o_IsNybble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isPositive ()
	{
		return descriptor.o_IsPositive(this);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail set?
	 *
	 * @return {@code true} if the receiver is a set, {@code false} otherwise.
	 */
	@Override
	public boolean isSet ()
	{
		return descriptor.o_IsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSetType ()
	{
		return descriptor.o_IsSetType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSubsetOf (
		final A_Set another)
	{
		return descriptor.o_IsSubsetOf(this, another);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail string?
	 *
	 * @return {@code true} if the receiver is an Avail string, {@code false}
	 *         otherwise.
	 */
	@Override
	public boolean isString ()
	{
		return descriptor.o_IsString(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSubtypeOf (
		final A_Type aType)
	{
		return descriptor.o_IsSubtypeOf(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfVariableType (
		final A_Type aVariableType)
	{
		return descriptor.o_IsSupertypeOfVariableType(this, aVariableType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfContinuationType (
		final A_Type aContinuationType)
	{
		return descriptor.o_IsSupertypeOfContinuationType(
			this,
			aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfFiberType (final A_Type aFiberType)
	{
		return descriptor.o_IsSupertypeOfFiberType(this, aFiberType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfFunctionType (
		final A_Type aFunctionType)
	{
		return descriptor.o_IsSupertypeOfFunctionType(this, aFunctionType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfIntegerRangeType (
		final A_Type anIntegerRangeType)
	{
		return descriptor.o_IsSupertypeOfIntegerRangeType(
			this,
			anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfLiteralTokenType (
		final A_Type aLiteralTokenType)
	{
		return descriptor.o_IsSupertypeOfLiteralTokenType(
			this,
			aLiteralTokenType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfMapType (
		final AvailObject aMapType)
	{
		return descriptor.o_IsSupertypeOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfObjectType (
		final A_Type anObjectType)
	{
		return descriptor.o_IsSupertypeOfObjectType(this, anObjectType);
	}

	/**
	 */
	@Override
	public boolean isSupertypeOfParseNodeType (
		final AvailObject aParseNodeType)
	{
		return descriptor.o_IsSupertypeOfParseNodeType(this, aParseNodeType);
	}

	/**
	 * Dispatch to the descriptor
	 */
	@Override
	public boolean isSupertypeOfPojoType (
		final A_BasicObject aPojoType)
	{
		return descriptor.o_IsSupertypeOfPojoType(this, aPojoType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfPrimitiveTypeEnum (
		final Types primitiveTypeEnum)
	{
		return descriptor.o_IsSupertypeOfPrimitiveTypeEnum(
			this,
			primitiveTypeEnum);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfSetType (
		final AvailObject aSetType)
	{
		return descriptor.o_IsSupertypeOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfBottom ()
	{
		return descriptor.o_IsSupertypeOfBottom(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor.o_IsSupertypeOfTupleType(this, aTupleType);
	}

	/**
	 */
	@Override
	public boolean isSupertypeOfEnumerationType (
		final A_BasicObject anEnumerationType)
	{
		return descriptor.o_IsSupertypeOfEnumerationType(
			this, anEnumerationType);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail tuple?
	 *
	 * @return {@code true} if the receiver is a tuple, {@code false} otherwise.
	 */
	@Override
	public boolean isTuple ()
	{
		return descriptor.o_IsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isTupleType ()
	{
		return descriptor.o_IsTupleType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isType ()
	{
		return descriptor.o_IsType(this);
	}

	/**
	 * Answer an {@linkplain Iterator iterator} suitable for traversing the
	 * elements of the {@linkplain AvailObject receiver} with a Java
	 * <em>foreach</em> construct.
	 *
	 * @return An {@linkplain Iterator iterator}.
	 */
	@Override
	public Iterator<AvailObject> iterator ()
	{
		return descriptor.o_Iterator(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Set keysAsSet ()
	{
		return descriptor.o_KeysAsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type keyType ()
	{
		return descriptor.o_KeyType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public L2Chunk levelTwoChunk ()
	{
		return descriptor.o_LevelTwoChunk(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void levelTwoChunkOffset (
		final L2Chunk chunk,
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
	@Override
	public int levelTwoOffset ()
	{
		return descriptor.o_LevelTwoOffset(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject literal ()
	{
		return descriptor.o_Literal(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject literalAt (
		final int index)
	{
		return descriptor.o_LiteralAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject argOrLocalOrStackAt (
		final int index)
	{
		return descriptor.o_ArgOrLocalOrStackAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
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
	@Override
	public A_Type localTypeAt (
		final int index)
	{
		return descriptor.o_LocalTypeAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Definition lookupByTypesFromTuple (
			final A_Tuple argumentTypeTuple)
		throws MethodDefinitionException
	{
		return descriptor.o_LookupByTypesFromTuple(this, argumentTypeTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Definition lookupByValuesFromList (
		final List<? extends A_BasicObject> argumentList)
	{
		return descriptor.o_LookupByValuesFromList(this, argumentList);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Number lowerBound ()
	{
		return descriptor.o_LowerBound(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean lowerInclusive ()
	{
		return descriptor.o_LowerInclusive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject makeImmutable ()
	{
		final Mutability mutability = descriptor.mutability;
		if (mutability != Mutability.MUTABLE)
		{
			return this;
		}
		return descriptor.o_MakeImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject makeShared ()
	{
		if (descriptor.mutability == Mutability.SHARED)
		{
			return this;
		}
		final AvailObject shared = descriptor.o_MakeShared(this);
		// Force a write barrier. Use a fresh object to avoid deadlocks -- or
		// even contention.  Create the object right there to spoon-feed HotSpot
		// a trivial lock elision opportunity.
		synchronized (new Object())
		{
			return shared;
		}
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void makeSubobjectsImmutable ()
	{
		descriptor.o_MakeSubobjectsImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void makeSubobjectsShared ()
	{
		descriptor.o_MakeSubobjectsShared(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject mapAt (
		final A_BasicObject keyObject)
	{
		return descriptor.o_MapAt(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Map mapAtPuttingCanDestroy (
		final A_BasicObject keyObject,
		final A_BasicObject newValueObject,
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
	@Override
	public int mapSize ()
	{
		return descriptor.o_MapSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Map mapWithoutKeyCanDestroy (
		final A_BasicObject keyObject,
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
	@Override
	public int maxStackDepth ()
	{
		return descriptor.o_MaxStackDepth(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Atom message ()
	{
		return descriptor.o_Message(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Tuple messageParts ()
	{
		return descriptor.o_MessageParts(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Set methodDefinitions ()
	{
		return descriptor.o_MethodDefinitions(this);
	}

	/**
	 * Subtract the argument {@code aNumber} from a receiver and answer
	 * the {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #subtractFromIntegerCanDestroy(A_Number, boolean)
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
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number minusCanDestroy (
		final A_Number aNumber,
		final boolean canDestroy)
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
			error("noFailMinusCanDestroy failed!");
			return NilDescriptor.nil();
		}
	}

	/**
	 * Multiply the receiver and the argument {@code anInfinity} and answer the
	 * {@linkplain AvailObject result}.
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
	 * @return The {@linkplain AvailObject result} of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number multiplyByInfinityCanDestroy (
		final Sign sign,
		final boolean canDestroy)
	{
		return descriptor.o_MultiplyByInfinityCanDestroy(
			this,
			sign,
			canDestroy);
	}

	/**
	 * Multiply the receiver and the argument {@code anInteger} and answer the
	 * {@linkplain AvailObject result}.
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
	 * @return The {@linkplain AvailObject result} of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number multiplyByIntegerCanDestroy (
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return descriptor.o_MultiplyByIntegerCanDestroy(
			this,
			anInteger,
			canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject name ()
	{
		return descriptor.o_Name(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_String atomName ()
	{
		return descriptor.o_AtomName(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Map importedNames ()
	{
		return descriptor.o_ImportedNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean nameVisible (
		final A_Atom trueName)
	{
		return descriptor.o_NameVisible(this, trueName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Map newNames ()
	{
		return descriptor.o_NewNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int numArgs ()
	{
		return descriptor.o_NumArgs(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int numArgsAndLocalsAndStack ()
	{
		return descriptor.o_NumArgsAndLocalsAndStack(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int numLiterals ()
	{
		return descriptor.o_NumLiterals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int numLocals ()
	{
		return descriptor.o_NumLocals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int numOuters ()
	{
		return descriptor.o_NumOuters(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int numOuterVars ()
	{
		return descriptor.o_NumOuterVars(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Tuple nybbles ()
	{
		return descriptor.o_Nybbles(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean optionallyNilOuterVar (
		final int index)
	{
		return descriptor.o_OptionallyNilOuterVar(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type outerTypeAt (
		final int index)
	{
		return descriptor.o_OuterTypeAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject outerVarAt (
		final int index)
	{
		return descriptor.o_OuterVarAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
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
	@Override
	public A_BasicObject parent ()
	{
		return descriptor.o_Parent(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void parent (
		final AvailObject value)
	{
		descriptor.o_Parent(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int pc ()
	{
		return descriptor.o_Pc(this);
	}

	/**
	 * Add the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #addToIntegerCanDestroy(A_Number, boolean) addToIntegerCanDestroy} or
	 * {@link #addToInfinityCanDestroy(Sign, boolean)
	 * addToInfinityCanDestroy}, where actual implementations of the addition
	 * operation should reside.</p>
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
	@Override
	public A_Number plusCanDestroy (
		final A_Number aNumber,
		final boolean canDestroy)
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
			error("noFailPlusCanDestroy failed!");
			return NilDescriptor.nil();
		}
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int primitiveNumber ()
	{
		return descriptor.o_PrimitiveNumber(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int priority ()
	{
		return descriptor.o_Priority(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void priority (final int value)
	{
		descriptor.o_Priority(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Map privateNames ()
	{
		return descriptor.o_PrivateNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Map fiberGlobals ()
	{
		return descriptor.o_FiberGlobals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void fiberGlobals (final A_Map value)
	{
		descriptor.o_FiberGlobals(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public short rawByteAt (
		final int index)
	{
		return descriptor.o_RawByteAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
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
	@Override
	public short rawByteForCharacterAt (
		final int index)
	{
		return descriptor.o_RawByteForCharacterAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
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
	@Override
	public int rawShortForCharacterAt (
		final int index)
	{
		return descriptor.o_RawShortForCharacterAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void rawShortForCharacterAtPut (
		final int index,
		final int anInteger)
	{
		descriptor.o_RawShortForCharacterAtPut(
			this,
			index,
			anInteger);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int rawSignedIntegerAt (
		final int index)
	{
		return descriptor.o_RawSignedIntegerAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
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
	@Override
	public long rawUnsignedIntegerAt (
		final int index)
	{
		return descriptor.o_RawUnsignedIntegerAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
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
	@Override
	public void removeDependentChunk (final L2Chunk chunk)
	{
		descriptor.o_RemoveDependentChunk(this, chunk);
	}

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param loader
	 */
	@Override
	public void removeFrom (
		final AvailLoader loader,
		final Continuation0 afterRemoval)
	{
		descriptor.o_RemoveFrom(this, loader, afterRemoval);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void removeDefinition (
		final A_Definition definition)
	{
		descriptor.o_RemoveDefinition(this, definition);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean removeBundleNamed (
		final A_Atom message)
	{
		return descriptor.o_RemoveBundleNamed(
			this,
			message);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void removeGrammaticalRestriction (
		final A_GrammaticalRestriction obsoleteRestriction)
	{
		descriptor.o_RemoveGrammaticalRestriction(this, obsoleteRestriction);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void resolveForward (
		final A_BasicObject forwardDefinition)
	{
		descriptor.o_ResolveForward(this, forwardDefinition);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Set grammaticalRestrictions ()
	{
		return descriptor.o_GrammaticalRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type returnType ()
	{
		return descriptor.o_ReturnType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void scanSubobjects (
		final AvailSubobjectVisitor visitor)
	{
		descriptor.o_ScanSubobjects(this, visitor);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Set setIntersectionCanDestroy (
		final A_Set otherSet,
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
	@Override
	public A_Set setMinusCanDestroy (
		final A_Set otherSet,
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
	@Override
	public int setSize ()
	{
		return descriptor.o_SetSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Set setUnionCanDestroy (
		final A_Set otherSet,
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
	@Override
	public void setValue (
		final A_BasicObject newValue)
	{
		descriptor.o_SetValue(this, newValue);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void setValueNoCheck (
		final AvailObject newValue)
	{
		descriptor.o_SetValueNoCheck(this, newValue);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Set setWithElementCanDestroy (
		final A_BasicObject newElementObject,
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
	@Override
	public A_Set setWithoutElementCanDestroy (
		final A_BasicObject elementObjectToExclude,
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
	@Override
	public A_Type sizeRange ()
	{
		return descriptor.o_SizeRange(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Map lazyActions ()
	{
		return descriptor.o_LazyActions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject stackAt (
		final int slotIndex)
	{
		return descriptor.o_StackAt(this, slotIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void stackAtPut (
		final int slotIndex,
		final A_BasicObject anObject)
	{
		descriptor.o_StackAtPut(
			this,
			slotIndex,
			anObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int stackp ()
	{
		return descriptor.o_Stackp(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int start ()
	{
		return descriptor.o_Start(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public L2Chunk startingChunk ()
	{
		return descriptor.o_StartingChunk(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void setStartingChunkAndReoptimizationCountdown (
		final L2Chunk chunk,
		final long countdown)
	{
		descriptor.o_SetStartingChunkAndReoptimizationCountdown(
			this, chunk, countdown);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_String string ()
	{
		return descriptor.o_String(this);
	}

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
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
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number subtractFromInfinityCanDestroy (
		final Sign sign,
		final boolean canDestroy)
	{
		return descriptor.o_SubtractFromInfinityCanDestroy(
			this,
			sign,
			canDestroy);
	}

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
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
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number subtractFromIntegerCanDestroy (
		final A_Number anInteger,
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
	@Override
	public LookupTree testingTree ()
	{
		return descriptor.o_TestingTree(this);
	}

	/**
	 * Multiply the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #multiplyByIntegerCanDestroy(A_Number, boolean)
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
	 * @return The {@linkplain AvailObject result} of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@Override
	public A_Number timesCanDestroy (
		final A_Number aNumber,
		final boolean canDestroy)
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
	@Override
	public A_Number noFailTimesCanDestroy (
		final A_Number aNumber,
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
			error("noFailTimesCanDestroy failed!");
			return NilDescriptor.nil();
		}
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public TokenDescriptor.TokenType tokenType ()
	{
		return descriptor.o_TokenType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject traversed ()
	{
		return descriptor.o_Traversed(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void trimExcessInts ()
	{
		descriptor.o_TrimExcessInts(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Set trueNamesForStringName (
		final A_String stringName)
	{
		return descriptor.o_TrueNamesForStringName(this, stringName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject tupleAt (
		final int index)
	{
		return descriptor.o_TupleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void objectTupleAtPut (
		final int index,
		final A_BasicObject anObject)
	{
		descriptor.o_ObjectTupleAtPut(
			this,
			index,
			anObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Tuple tupleAtPuttingCanDestroy (
		final int index,
		final A_BasicObject newValueObject,
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
	@Override
	public int tupleIntAt (
		final int index)
	{
		return descriptor.o_TupleIntAt(this, index);
	}

	/**
	 *Dispatch to the descriptor
	 */
	@Override
	public A_Tuple tupleReverse()
	{
		return descriptor.o_TupleReverse(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int tupleSize ()
	{
		return descriptor.o_TupleSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type kind ()
	{
		return descriptor.o_Kind(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeAtIndex (
		final int index)
	{
		return descriptor.o_TypeAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeIntersection (
		final A_Type another)
	{
		return descriptor.o_TypeIntersection(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeIntersectionOfFiberType (
		final A_Type aFiberType)
	{
		return descriptor.o_TypeIntersectionOfFiberType(
			this,
			aFiberType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeIntersectionOfFunctionType (
		final A_Type aFunctionType)
	{
		return descriptor.o_TypeIntersectionOfFunctionType(
			this,
			aFunctionType);
	}

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	@Override
	public A_Type typeIntersectionOfCompiledCodeType (
		final A_Type aCompiledCodeType)
	{
		return descriptor.o_TypeIntersectionOfCompiledCodeType(
			this,
			aCompiledCodeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeIntersectionOfVariableType (
		final A_Type aVariableType)
	{
		return descriptor.o_TypeIntersectionOfVariableType(this, aVariableType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeIntersectionOfContinuationType (
		final A_Type aContinuationType)
	{
		return descriptor.o_TypeIntersectionOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeIntersectionOfIntegerRangeType (
		final A_Type anIntegerRangeType)
	{
		return descriptor.o_TypeIntersectionOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeIntersectionOfMapType (
		final A_Type aMapType)
	{
		return descriptor.o_TypeIntersectionOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeIntersectionOfObjectType (
		final A_Type anObjectType)
	{
		return descriptor.o_TypeIntersectionOfObjectType(this, anObjectType);
	}

	/**
	 * @param aParseNodeType
	 * @return
	 */
	@Override
	public A_Type typeIntersectionOfParseNodeType (
		final A_Type aParseNodeType)
	{
		return descriptor.o_TypeIntersectionOfParseNodeType(
			this,
			aParseNodeType);
	}

	/**
	 * @param aPojoType
	 * @return
	 */
	@Override
	public A_Type typeIntersectionOfPojoType (
		final A_Type aPojoType)
	{
		return descriptor.o_TypeIntersectionOfPojoType(this, aPojoType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeIntersectionOfSetType (
		final A_Type aSetType)
	{
		return descriptor.o_TypeIntersectionOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeIntersectionOfTupleType (
		final A_Type aTupleType)
	{
		return descriptor.o_TypeIntersectionOfTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Tuple typeTuple ()
	{
		return descriptor.o_TypeTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeUnion (
		final A_Type another)
	{
		return descriptor.o_TypeUnion(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeUnionOfFiberType (
		final A_Type aFiberType)
	{
		return descriptor.o_TypeUnionOfFiberType(
			this,
			aFiberType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeUnionOfFunctionType (
		final A_Type aFunctionType)
	{
		return descriptor.o_TypeUnionOfFunctionType(this, aFunctionType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeUnionOfVariableType (
		final A_Type aVariableType)
	{
		return descriptor.o_TypeUnionOfVariableType(this, aVariableType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeUnionOfContinuationType (
		final A_Type aContinuationType)
	{
		return descriptor.o_TypeUnionOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeUnionOfIntegerRangeType (
		final A_Type anIntegerRangeType)
	{
		return descriptor.o_TypeUnionOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeUnionOfMapType (
		final A_Type aMapType)
	{
		return descriptor.o_TypeUnionOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeUnionOfObjectType (
		final A_Type anObjectType)
	{
		return descriptor.o_TypeUnionOfObjectType(this, anObjectType);
	}

	/**
	 * @param aParseNodeType
	 * @return
	 */
	@Override
	public A_Type typeUnionOfParseNodeType (
		final A_Type aParseNodeType)
	{
		return descriptor.o_TypeUnionOfParseNodeType(
			this,
			aParseNodeType);
	}

	/**
	 * @param aPojoType
	 * @return
	 */
	@Override
	public A_Type typeUnionOfPojoType (
		final A_Type aPojoType)
	{
		return descriptor.o_TypeUnionOfPojoType(this, aPojoType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeUnionOfSetType (
		final A_Type aSetType)
	{
		return descriptor.o_TypeUnionOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type typeUnionOfTupleType (
		final A_Type aTupleType)
	{
		return descriptor.o_TypeUnionOfTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type unionOfTypesAtThrough (
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
	@Override
	public A_Number upperBound ()
	{
		return descriptor.o_UpperBound(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean upperInclusive ()
	{
		return descriptor.o_UpperInclusive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject value ()
	{
		return descriptor.o_Value(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void value (
		final A_BasicObject value)
	{
		descriptor.o_Value(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Tuple valuesAsTuple ()
	{
		return descriptor.o_ValuesAsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Map variableBindings ()
	{
		return descriptor.o_VariableBindings(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Set visibleNames ()
	{
		return descriptor.o_VisibleNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Tuple parsingInstructions ()
	{
		return descriptor.o_ParsingInstructions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Phrase expression ()
	{
		return descriptor.o_Expression(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Phrase variable ()
	{
		return descriptor.o_Variable(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Tuple argumentsTuple ()
	{
		return descriptor.o_ArgumentsTuple(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Tuple statementsTuple ()
	{
		return descriptor.o_StatementsTuple(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Type resultType ()
	{
		return descriptor.o_ResultType(this);
	}

	/**
	 * @param neededVariables
	 */
	@Override
	public void neededVariables (final A_Tuple neededVariables)
	{
		descriptor.o_NeededVariables(this, neededVariables);
	}

	/**
	 * @return
	 */
	@Override
	public A_Tuple neededVariables ()
	{
		return descriptor.o_NeededVariables(this);
	}

	/**
	 * @return
	 */
	@Override
	public int primitive ()
	{
		return descriptor.o_Primitive(this);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject declaredType ()
	{
		return descriptor.o_DeclaredType(this);
	}

	/**
	 * @return
	 */
	@Override
	public DeclarationKind declarationKind ()
	{
		return descriptor.o_DeclarationKind(this);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject initializationExpression ()
	{
		return descriptor.o_InitializationExpression(this);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject literalObject ()
	{
		return descriptor.o_LiteralObject(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Token token ()
	{
		return descriptor.o_Token(this);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject markerValue ()
	{
		return descriptor.o_MarkerValue(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Phrase argumentsListNode ()
	{
		return descriptor.o_ArgumentsListNode(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Bundle bundle ()
	{
		return descriptor.o_Bundle(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Tuple expressionsTuple ()
	{
		return descriptor.o_ExpressionsTuple(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Phrase declaration ()
	{
		return descriptor.o_Declaration(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Type expressionType ()
	{
		return descriptor.o_ExpressionType(this);
	}

	/**
	 * @param codeGenerator
	 */
	@Override
	public void emitEffectOn (final AvailCodeGenerator codeGenerator)
	{
		descriptor.o_EmitEffectOn(this, codeGenerator);
	}

	/**
	 * @param codeGenerator
	 */
	@Override
	public void emitValueOn (final AvailCodeGenerator codeGenerator)
	{
		descriptor.o_EmitValueOn(this, codeGenerator);
	}

	/**
	 * @param aBlock
	 */
	@Override
	public void childrenMap (
		final Transformer1<A_Phrase, A_Phrase> aBlock)
	{
		descriptor.o_ChildrenMap(this, aBlock);
	}

	/**
	 * @param aBlock
	 */
	@Override
	public void childrenDo (
		final Continuation1<A_Phrase> aBlock)
	{
		descriptor.o_ChildrenDo(this, aBlock);
	}

	/**
	 * @param parent
	 */
	@Override
	public void validateLocally (final @Nullable A_Phrase parent)
	{
		descriptor.o_ValidateLocally(
			this,
			parent);
	}

	/**
	 * @param module
	 * @return
	 */
	@Override
	public A_RawFunction generateInModule (
		final A_Module module)
	{
		return descriptor.o_GenerateInModule(this, module);
	}

	/**
	 * @param newParseNode
	 * @return
	 */
	@Override
	public A_Phrase copyWith (final A_Phrase newParseNode)
	{
		return descriptor.o_CopyWith(this, newParseNode);
	}

	/**
	 * @param isLastUse
	 */
	@Override
	public void isLastUse (final boolean isLastUse)
	{
		descriptor.o_IsLastUse(this, isLastUse);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isLastUse ()
	{
		return descriptor.o_IsLastUse(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isMacroDefinition ()
	{
		return descriptor.o_IsMacroDefinition(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Phrase copyMutableParseNode ()
	{
		return descriptor.o_CopyMutableParseNode(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Type binUnionKind ()
	{
		return descriptor.o_BinUnionKind(this);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject outputParseNode ()
	{
		return descriptor.o_OutputParseNode(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Atom apparentSendName ()
	{
		return descriptor.o_ApparentSendName(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Tuple statements ()
	{
		return descriptor.o_Statements(this);
	}

	/**
	 * @param accumulatedStatements
	 */
	@Override
	public void flattenStatementsInto (
		final List<A_Phrase> accumulatedStatements)
	{
		descriptor.o_FlattenStatementsInto(this, accumulatedStatements);
	}

	/**
	 * @return
	 */
	@Override
	public int lineNumber ()
	{
		return descriptor.o_LineNumber(this);
	}


	/**
	 * @return
	 */
	@Override
	public A_Map allBundles ()
	{
		return descriptor.o_AllBundles(this);
	}


	/**
	 * @return
	 */
	@Override
	public boolean isSetBin ()
	{
		return descriptor.o_IsSetBin(this);
	}

	/**
	 * @return
	 */
	@Override
	public MapDescriptor.MapIterable mapIterable ()
	{
		return descriptor.o_MapIterable(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Set declaredExceptions ()
	{
		return descriptor.o_DeclaredExceptions(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isInt ()
	{
		return descriptor.o_IsInt(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isLong ()
	{
		return descriptor.o_IsLong(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Type argsTupleType ()
	{
		return descriptor.o_ArgsTupleType(this);
	}

	/**
	 * @param anInstanceType
	 * @return
	 */
	@Override
	public boolean equalsInstanceTypeFor (
		final AvailObject anInstanceType)
	{
		return descriptor.o_EqualsInstanceTypeFor(this, anInstanceType);
	}

	/**
	 * @return
	 */
	@Override
	public A_Set instances ()
	{
		return descriptor.o_Instances(this);
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
		return descriptor.o_EqualsEnumerationWithSet(this, aSet);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isEnumeration ()
	{
		return descriptor.o_IsEnumeration(this);
	}

	/**
	 * @param potentialInstance
	 * @return
	 */
	@Override
	public boolean enumerationIncludesInstance (
		final AvailObject potentialInstance)
	{
		return descriptor.o_EnumerationIncludesInstance(
			this,
			potentialInstance);
	}

	/**
	 * @return
	 */
	@Override
	public A_Type valueType ()
	{
		return descriptor.o_ValueType(this);
	}

	/**
	 * Compute a {@linkplain TypeDescriptor type} that is an ancestor of the
	 * receiver, but is not an {@linkplain AbstractEnumerationTypeDescriptor
	 * enumeration}.  Choose the most specific such type.  Fail if the
	 * receiver is not itself an enumeration.  Also fail if the receiver is
	 * {@linkplain BottomTypeDescriptor bottom}.
	 *
	 * @return The must specific non-union supertype.
	 */
	@Override
	public A_Type computeSuperkind ()
	{
		return descriptor.o_ComputeSuperkind(this);
	}

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	@Override
	public boolean equalsCompiledCodeType (final A_Type aCompiledCodeType)
	{
		return descriptor.o_EqualsCompiledCodeType(this, aCompiledCodeType);
	}

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	@Override
	public boolean isSupertypeOfCompiledCodeType (
		final A_Type aCompiledCodeType)
	{
		return descriptor.o_IsSupertypeOfCompiledCodeType(
			this,
			aCompiledCodeType);
	}

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	@Override
	public A_Type typeUnionOfCompiledCodeType (
		final A_Type aCompiledCodeType)
	{
		return descriptor.o_TypeUnionOfCompiledCodeType(
 			this,
 			aCompiledCodeType);
	}

	/**
	 * @param key
	 * @param value
	 */
	@Override
	public void setAtomProperty (
		final A_Atom key,
		final A_BasicObject value)
	{
		descriptor.o_SetAtomProperty(this, key, value);
	}

	/**
	 * @param key
	 * @return
	 */
	@Override
	public AvailObject getAtomProperty (
		final A_Atom key)
	{
		return descriptor.o_GetAtomProperty(this, key);
	}

	/**
	 * @param anEnumerationType
	 * @return
	 */
	@Override
	public boolean equalsEnumerationType (
		final A_BasicObject anEnumerationType)
	{
		return descriptor.o_EqualsEnumerationType(this, anEnumerationType);
	}

	/**
	 * @return
	 */
	@Override
	public A_Type readType ()
	{
		return descriptor.o_ReadType(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Type writeType ()
	{
		return descriptor.o_WriteType(this);
	}

	/**
	 * @param versionStrings
	 */
	@Override
	public void versions (final A_Set versionStrings)
	{
		descriptor.o_Versions(this, versionStrings);
	}

	/**
	 * @return
	 */
	@Override
	public A_Set versions ()
	{
		return descriptor.o_Versions(this);
	}

	/**
	 * @return
	 */
	@Override
	public ParseNodeKind parseNodeKind ()
	{
		return descriptor.o_ParseNodeKind(this);
	}

	/**
	 * @param expectedParseNodeKind
	 * @return
	 */
	@Override
	public boolean parseNodeKindIsUnder (
		final ParseNodeKind expectedParseNodeKind)
	{
		return descriptor.o_ParseNodeKindIsUnder(this, expectedParseNodeKind);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isRawPojo ()
	{
		return descriptor.o_IsRawPojo(this);
	}

	/**
	 * @param restriction
	 */
	@Override
	public void addSemanticRestriction (
		final A_SemanticRestriction restriction)
	{
		descriptor.o_AddSemanticRestriction(this, restriction);
	}

	/**
	 * @param restriction
	 */
	@Override
	public void removeSemanticRestriction (
		final A_SemanticRestriction restriction)
	{
		descriptor.o_RemoveSemanticRestriction(this, restriction);
	}

	/**
	 * @return
	 */
	@Override
	public A_Set semanticRestrictions ()
	{
		return descriptor.o_SemanticRestrictions(this);
	}

	/**
	 * @param typeTuple
	 */
	@Override
	public void addSealedArgumentsType (final A_Tuple typeTuple)
	{
		descriptor.o_AddSealedArgumentsType(this, typeTuple);
	}

	/**
	 * @param typeTuple
	 */
	@Override
	public void removeSealedArgumentsType (final A_Tuple typeTuple)
	{
		descriptor.o_RemoveSealedArgumentsType(this, typeTuple);
	}

	/**
	 * @return
	 */
	@Override
	public A_Tuple sealedArgumentsTypesTuple ()
	{
		return descriptor.o_SealedArgumentsTypesTuple(this);
	}

	/**
	 * @param semanticRestriction
	 */
	@Override
	public void moduleAddSemanticRestriction (
		final A_SemanticRestriction semanticRestriction)
	{
		descriptor.o_ModuleAddSemanticRestriction(
			this,
			semanticRestriction);
	}

	/**
	 * @param name
	 * @param constantBinding
	 */
	@Override
	public void addConstantBinding (
		final A_String name,
		final A_BasicObject constantBinding)
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
	@Override
	public void addVariableBinding (
		final A_String name,
		final A_BasicObject variableBinding)
	{
		descriptor.o_AddVariableBinding(
			this,
			name,
			variableBinding);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isMethodEmpty ()
	{
		return descriptor.o_IsMethodEmpty(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isPojoSelfType ()
	{
		return descriptor.o_IsPojoSelfType(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Type pojoSelfType ()
	{
		return descriptor.o_PojoSelfType(this);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject javaClass ()
	{
		return descriptor.o_JavaClass(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isUnsignedShort ()
	{
		return descriptor.o_IsUnsignedShort(this);
	}

	/**
	 * @return
	 */
	@Override
	public int extractUnsignedShort ()
	{
		return descriptor.o_ExtractUnsignedShort(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isFloat ()
	{
		return descriptor.o_IsFloat(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isDouble ()
	{
		return descriptor.o_IsDouble(this);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject rawPojo ()
	{
		return descriptor.o_RawPojo(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isPojo ()
	{
		return descriptor.o_IsPojo(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isPojoType ()
	{
		return descriptor.o_IsPojoType(this);
	}

	/**
	 * @param another
	 * @return
	 */
	@Override
	public Order numericCompare (final A_Number another)
	{
		return descriptor.o_NumericCompare(this, another);
	}

	/**
	 * @param sign
	 * @return
	 */
	@Override
	public Order numericCompareToInfinity (
		final Sign sign)
	{
		return descriptor.o_NumericCompareToInfinity(this, sign);
	}

	/**
	 * @param aDouble
	 * @return
	 */
	@Override
	public Order numericCompareToDouble (final double aDouble)
	{
		return descriptor.o_NumericCompareToDouble(this, aDouble);
	}

	/**
	 * @param anInteger
	 * @return
	 */
	@Override
	public Order numericCompareToInteger (final A_Number anInteger)
	{
		return descriptor.o_NumericCompareToInteger(this, anInteger);
	}

	/**
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number addToDoubleCanDestroy (
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return descriptor.o_AddToDoubleCanDestroy (
			this,
			doubleObject,
			canDestroy);
	}

	/**
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number addToFloatCanDestroy (
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return descriptor.o_AddToFloatCanDestroy (
			this,
			floatObject,
			canDestroy);
	}

	/**
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number subtractFromDoubleCanDestroy (
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return descriptor.o_SubtractFromDoubleCanDestroy (
			this,
			doubleObject,
			canDestroy);
	}

	/**
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number subtractFromFloatCanDestroy (
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return descriptor.o_SubtractFromFloatCanDestroy (
			this,
			floatObject,
			canDestroy);
	}

	/**
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number multiplyByDoubleCanDestroy (
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return descriptor .o_MultiplyByDoubleCanDestroy (
			this,
			doubleObject,
			canDestroy);
	}

	/**
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number multiplyByFloatCanDestroy (
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return descriptor.o_MultiplyByFloatCanDestroy (
			this,
			floatObject,
			canDestroy);
	}

	/**
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number divideIntoDoubleCanDestroy (
		final A_Number doubleObject,
		final boolean canDestroy)
	{
		return descriptor.o_DivideIntoDoubleCanDestroy (
			this,
			doubleObject,
			canDestroy);
	}

	/**
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number divideIntoFloatCanDestroy (
		final A_Number floatObject,
		final boolean canDestroy)
	{
		return descriptor.o_DivideIntoFloatCanDestroy (
			this,
			floatObject,
			canDestroy);
	}

	/**
	 * @return
	 */
	@Override
	public A_Map lazyPrefilterMap ()
	{
		return descriptor.o_LazyPrefilterMap(this);
	}

	/**
	 * @return
	 */
	@Override
	public SerializerOperation serializerOperation ()
	{
		return descriptor.o_SerializerOperation(this);
	}

	/**
	 * @param key
	 * @param keyHash
	 * @param value
	 * @param myLevel
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_BasicObject mapBinAtHashPutLevelCanDestroy (
		final A_BasicObject key,
		final int keyHash,
		final A_BasicObject value,
		final byte myLevel,
		final boolean canDestroy)
	{
		return descriptor.o_MapBinAtHashPutLevelCanDestroy(
			this,
			key,
			keyHash,
			value,
			myLevel,
			canDestroy);
	}

	/**
	 * @param key
	 * @param keyHash
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_BasicObject mapBinRemoveKeyHashCanDestroy (
		final A_BasicObject key,
		final int keyHash,
		final boolean canDestroy)
	{
		return descriptor.o_MapBinRemoveKeyHashCanDestroy(
			this,
			key,
			keyHash,
			canDestroy);
	}

	/**
	 * @return
	 */
	@Override
	public A_Type mapBinKeyUnionKind ()
	{
		return descriptor.o_MapBinKeyUnionKind(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Type mapBinValueUnionKind ()
	{
		return descriptor.o_MapBinValueUnionKind(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isHashedMapBin ()
	{
		return descriptor.o_IsHashedMapBin(this);
	}

	/**
	 * Look up the key in this {@linkplain MapBinDescriptor map bin}.  If not
	 * found, answer the {@linkplain NilDescriptor#nil()}.  Use the
	 * provided hash of the key.
	 *
	 * @param key The key to look up in this map.
	 * @param keyHash The conveniently already computed hash of the key.
	 * @return The value under that key in the map, or nil if not
	 *         found.
	 */
	@Override
	public AvailObject mapBinAtHash (
		final A_BasicObject key,
		final int keyHash)
	{
		return descriptor.o_MapBinAtHash(this, key, keyHash);
	}

	/**
	 * @return
	 */
	@Override
	public int mapBinKeysHash ()
	{
		return descriptor.o_MapBinKeysHash(this);
	}

	/**
	 * @return
	 */
	@Override
	public int mapBinValuesHash ()
	{
		return descriptor.o_MapBinValuesHash(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Module issuingModule ()
	{
		return descriptor.o_IssuingModule(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isPojoFusedType ()
	{
		return descriptor.o_IsPojoFusedType(this);
	}

	/**
	 * @param aPojoType
	 * @return
	 */
	@Override
	public boolean isSupertypeOfPojoBottomType (
		final A_Type aPojoType)
	{
		return descriptor.o_IsSupertypeOfPojoBottomType(this, aPojoType);
	}

	/**
	 * @return
	 */
	@Override
	public boolean equalsPojoBottomType ()
	{
		return descriptor.o_EqualsPojoBottomType(this);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject javaAncestors ()
	{
		return descriptor.o_JavaAncestors(this);
	}

	/**
	 * @param aFusedPojoType
	 * @return
	 */
	@Override
	public A_Type typeIntersectionOfPojoFusedType (
		final A_Type aFusedPojoType)
	{
		return descriptor.o_TypeIntersectionOfPojoFusedType(
			this, aFusedPojoType);
	}

	/**
	 * @param anUnfusedPojoType
	 * @return
	 */
	@Override
	public A_Type typeIntersectionOfPojoUnfusedType (
		final A_Type anUnfusedPojoType)
	{
		return descriptor.o_TypeIntersectionOfPojoUnfusedType(
			this, anUnfusedPojoType);
	}

	/**
	 * @param aFusedPojoType
	 * @return
	 */
	@Override
	public A_Type typeUnionOfPojoFusedType (
		final A_Type aFusedPojoType)
	{
		return descriptor.o_TypeUnionOfPojoFusedType(
			this, aFusedPojoType);
	}

	/**
	 * @param anUnfusedPojoType
	 * @return
	 */
	@Override
	public A_Type typeUnionOfPojoUnfusedType (
		final A_Type anUnfusedPojoType)
	{
		return descriptor.o_TypeUnionOfPojoUnfusedType(
			this, anUnfusedPojoType);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isPojoArrayType ()
	{
		return descriptor.o_IsPojoArrayType(this);
	}

	/**
	 * @param classHint
	 * @return
	 */
	@Override
	public @Nullable Object marshalToJava (final @Nullable Class<?> classHint)
	{
		return descriptor.o_MarshalToJava(this, classHint);
	}

	/**
	 * @return
	 */
	@Override
	public A_Map typeVariables ()
	{
		return descriptor.o_TypeVariables(this);
	}

	/**
	 * @param field
	 * @param receiver
	 * @return
	 */
	@Override
	public boolean equalsPojoField (
		final AvailObject field,
		final AvailObject receiver)
	{
		return descriptor.o_EqualsPojoField(this, field, receiver);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isSignedByte ()
	{
		return descriptor.o_IsSignedByte(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isSignedShort ()
	{
		return descriptor.o_IsSignedShort(this);
	}

	/**
	 * @return
	 */
	@Override
	public byte extractSignedByte ()
	{
		return descriptor.o_ExtractSignedByte(this);
	}

	/**
	 * @return
	 */
	@Override
	public short extractSignedShort ()
	{
		return descriptor.o_ExtractSignedShort(this);
	}

	/**
	 * @param otherJavaObject
	 * @return
	 */
	@Override
	public boolean equalsEqualityRawPojoFor (
		final AvailObject otherEqualityRawPojo,
		final @Nullable Object otherJavaObject)
	{
		return descriptor.o_EqualsEqualityRawPojo(
			this,
			otherEqualityRawPojo,
			otherJavaObject);
	}

	/**
	 * @return
	 */
	@Override
	public Object javaObject ()
	{
		return descriptor.o_JavaObject(this);
	}

	/**
	 * @return
	 */
	@Override
	public BigInteger asBigInteger ()
	{
		return descriptor.o_AsBigInteger(this);
	}

	/**
	 * @param newElement
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Tuple appendCanDestroy (
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		return descriptor.o_AppendCanDestroy(this, newElement, canDestroy);
	}

	/**
	 * @return
	 */
	@Override
	public A_Map lazyIncompleteCaseInsensitive ()
	{
		return descriptor.o_LazyIncompleteCaseInsensitive(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_String lowerCaseString ()
	{
		return descriptor.o_LowerCaseString(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Number instanceCount ()
	{
		return descriptor.o_InstanceCount(this);
	}

	/**
	 * @return
	 */
	@Override
	public long totalInvocations ()
	{
		return descriptor.o_TotalInvocations(this);
	}

	/**
	 *
	 */
	@Override
	public void tallyInvocation ()
	{
		descriptor.o_TallyInvocation(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Tuple fieldTypeTuple ()
	{
		return descriptor.o_FieldTypeTuple(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Tuple fieldTuple ()
	{
		return descriptor.o_FieldTuple(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isSystemModule ()
	{
		return descriptor.o_IsSystemModule(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Type literalType ()
	{
		return descriptor.o_LiteralType(this);
	}

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	@Override
	public A_Type typeIntersectionOfLiteralTokenType (
		final A_Type aLiteralTokenType)
	{
		return descriptor.o_TypeIntersectionOfLiteralTokenType(
			this,
			aLiteralTokenType);
	}

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	@Override
	public A_Type typeUnionOfLiteralTokenType (
		final A_Type aLiteralTokenType)
	{
		return descriptor.o_TypeUnionOfLiteralTokenType(
			this,
			aLiteralTokenType);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isLiteralTokenType ()
	{
		return descriptor.o_IsLiteralTokenType(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isLiteralToken ()
	{
		return descriptor.o_IsLiteralToken(this);
	}

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	@Override
	public boolean equalsLiteralTokenType (
		final A_Type aLiteralTokenType)
	{
		return descriptor.o_EqualsLiteralTokenType(this, aLiteralTokenType);
	}

	/**
	 * @param anObjectType
	 * @return
	 */
	@Override
	public boolean equalsObjectType (
		final A_Type anObjectType)
	{
		return descriptor.o_EqualsObjectType(this, anObjectType);
	}

	/**
	 * @param aToken
	 * @return
	 */
	@Override
	public boolean equalsToken (
		final A_Token aToken)
	{
		return descriptor.o_EqualsToken(this, aToken);
	}

	/**
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number bitwiseAnd (
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return descriptor.o_BitwiseAnd(this, anInteger, canDestroy);
	}

	/**
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number bitwiseOr (
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return descriptor.o_BitwiseOr(this, anInteger, canDestroy);
	}

	/**
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number bitwiseXor (
		final A_Number anInteger,
		final boolean canDestroy)
	{
		return descriptor.o_BitwiseXor(this, anInteger, canDestroy);
	}

	/**
	 * @param methodName
	 * @param sealSignature
	 */
	@Override
	public void addSeal (
		final A_Atom methodName,
		final A_Tuple sealSignature)
	{
		descriptor.o_AddSeal(this, methodName, sealSignature);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isInstanceMeta ()
	{
		return descriptor.o_IsInstanceMeta(this);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject instance ()
	{
		return descriptor.o_Instance(this);
	}

	/**
	 * @return
	 */
	@Override
	public int allocateFromCounter ()
	{
		return descriptor.o_AllocateFromCounter(this);
	}

	/**
	 * @param methodName
	 */
	@Override
	public void setMethodName (
		final A_String methodName)
	{
		descriptor.o_SetMethodName(this, methodName);
	}

	/**
	 * @return
	 */
	@Override
	public int startingLineNumber ()
	{
		return descriptor.o_StartingLineNumber(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Module module ()
	{
		return descriptor.o_Module(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_String methodName ()
	{
		return descriptor.o_MethodName(this);
	}

	/**
	 * @param kind
	 * @return
	 */
	@Override
	public boolean binElementsAreAllInstancesOfKind (
		final A_Type kind)
	{
		return descriptor.o_BinElementsAreAllInstancesOfKind(this, kind);
	}

	/**
	 * @param kind
	 * @return
	 */
	@Override
	public boolean setElementsAreAllInstancesOfKind (
		final AvailObject kind)
	{
		return descriptor.o_SetElementsAreAllInstancesOfKind(this, kind);
	}

	/**
	 * @return
	 */
	@Override
	public MapDescriptor.MapIterable mapBinIterable ()
	{
		return descriptor.o_MapBinIterable(this);
	}

	/**
	 * @param anInt
	 * @return
	 */
	@Override
	public boolean rangeIncludesInt (final int anInt)
	{
		return descriptor.o_RangeIncludesInt(this, anInt);
	}

	/**
	 * @param isSystemModule
	 */
	@Override
	public void isSystemModule (
		final boolean isSystemModule)
	{
		descriptor.o_IsSystemModule(this, isSystemModule);
	}

	/**
	 * @param shiftFactor
	 * @param truncationBits
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number bitShiftLeftTruncatingToBits (
		final A_Number shiftFactor,
		final A_Number truncationBits,
		final boolean canDestroy)
	{
		return descriptor.o_BitShiftLeftTruncatingToBits(
			this,
			shiftFactor,
			truncationBits,
			canDestroy);
	}

	/**
	 * @return
	 */
	@Override
	public SetIterator setBinIterator ()
	{
		return descriptor.o_SetBinIterator(this);
	}

	/**
	 * @param shiftFactor
	 * @param canDestroy
	 * @return
	 */
	@Override
	public A_Number bitShift (
		final A_Number shiftFactor,
		final boolean canDestroy)
	{
		return descriptor.o_BitShift(this, shiftFactor, canDestroy);
	}

	/**
	 * @param aParseNode
	 * @return
	 */
	@Override
	public boolean equalsParseNode (final A_Phrase aParseNode)
	{
		return descriptor.o_EqualsParseNode(this, aParseNode);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject stripMacro ()
	{
		return descriptor.o_StripMacro(this);
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
		return descriptor.o_DefinitionMethod(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Tuple prefixFunctions ()
	{
		return descriptor.o_PrefixFunctions(this);
	}

	/**
	 * @param aByteArrayTuple
	 * @return
	 */
	@Override
	public boolean equalsByteArrayTuple (
		final A_Tuple aByteArrayTuple)
	{
		return descriptor.o_EqualsByteArrayTuple(this, aByteArrayTuple);
	}

	/**
	 * @param i
	 * @param tupleSize
	 * @param aByteArrayTuple
	 * @param j
	 * @return
	 */
	@Override
	public boolean compareFromToWithByteArrayTupleStartingAt (
		final int i,
		final int tupleSize,
		final A_Tuple aByteArrayTuple,
		final int j)
	{
		return descriptor.o_CompareFromToWithByteArrayTupleStartingAt(
			this, i, tupleSize, aByteArrayTuple, j);
	}

	/**
	 * @return
	 */
	@Override
	public byte[] byteArray ()
	{
		return descriptor.o_ByteArray(this);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isByteArrayTuple ()
	{
		return descriptor.o_IsByteArrayTuple(this);
	}

	/**
	 * @param bundle
	 */
	@Override
	public void flushForNewOrChangedBundle (
		final A_Bundle bundle)
	{
		descriptor.o_FlushForNewOrChangedBundle(this, bundle);
	}

	/**
	 * @param critical
	 */
	@Override
	public void lock (final Continuation0 critical)
	{
		descriptor.o_Lock(this, critical);
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
		return descriptor.o_AvailLoader(this);
	}

	/**
	 * @param loader
	 */
	@Override
	public void availLoader (final @Nullable AvailLoader loader)
	{
		descriptor.o_AvailLoader(this, loader);
	}

	/**
	 * @return
	 */
	@Override
	public A_String moduleName ()
	{
		return descriptor.o_ModuleName(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Method bundleMethod ()
	{
		return descriptor.o_BundleMethod(this);
	}

	/**
	 * Answer the {@linkplain Continuation1 continuation} that accepts the
	 * result produced by the {@linkplain FiberDescriptor receiver}'s successful
	 * completion.
	 *
	 * @return A continuation.
	 */
	@Override
	public Continuation1<AvailObject> resultContinuation ()
	{
		return descriptor.o_ResultContinuation(this);
	}

	/**
	 * Set the {@linkplain Continuation1 continuation} that accepts the result
	 * produced by the {@linkplain FiberDescriptor receiver}'s successful
	 * completion.
	 *
	 * @param continuation The result.
	 */
	@Override
	public void resultContinuation (
		final Continuation1<AvailObject> continuation)
	{
		descriptor.o_ResultContinuation(this, continuation);
	}

	/**
	 * Answer the {@linkplain Continuation1 continuation} that accepts the
	 * {@linkplain Throwable throwable} responsible for abnormal termination of
	 * the {@linkplain FiberDescriptor receiver}.
	 *
	 * @return A continuation.
	 */
	@Override
	public Continuation1<Throwable> failureContinuation ()
	{
		return descriptor.o_FailureContinuation(this);
	}

	/**
	 * Set the {@linkplain Continuation1 continuation} that accepts the
	 * {@linkplain Throwable throwable} responsible for abnormal termination of
	 * the {@linkplain FiberDescriptor receiver}.
	 *
	 * @param onFailure
	 *        The continuation to invoke with the responsible throwable.
	 */
	@Override
	public void failureContinuation (
		final Continuation1<Throwable> onFailure)
	{
		descriptor.o_FailureContinuation(this, onFailure);
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
		return descriptor.o_InterruptRequestFlag(this, flag);
	}

	/**
	 * @param newValue
	 * @return
	 */
	@Override
	public AvailObject getAndSetValue (final AvailObject newValue)
	{
		return descriptor.o_GetAndSetValue(this, newValue);
	}

	/**
	 * @param reference
	 * @param newValue
	 * @return
	 */
	@Override
	public boolean compareAndSwapValues (
		final AvailObject reference,
		final AvailObject newValue)
	{
		return descriptor.o_CompareAndSwapValues(this, reference, newValue);
	}

	/**
	 * @param addend
	 * @return
	 */
	@Override
	public A_Number fetchAndAddValue (final A_Number addend)
	{
		return descriptor.o_FetchAndAddValue(this, addend);
	}

	/**
	 * @param flag
	 * @return
	 */
	@Override
	public boolean getAndClearInterruptRequestFlag (
		final InterruptRequestFlag flag)
	{
		return descriptor.o_GetAndClearInterruptRequestFlag(this, flag);
	}

	/**
	 * @param flag
	 * @param newValue
	 * @return
	 */
	@Override
	public boolean getAndSetSynchronizationFlag (
		final SynchronizationFlag flag,
		final boolean newValue)
	{
		return descriptor.o_GetAndSetSynchronizationFlag(this, flag, newValue);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject fiberResult ()
	{
		return descriptor.o_FiberResult(this);
	}

	/**
	 * @param result
	 */
	@Override
	public void fiberResult (final A_BasicObject result)
	{
		descriptor.o_FiberResult(this, result);
	}

	/**
	 * @return
	 */
	@Override
	public A_Set joiningFibers ()
	{
		return descriptor.o_JoiningFibers(this);
	}

	/**
	 * @return
	 */
	@Override
	public @Nullable TimerTask wakeupTask ()
	{
		return descriptor.o_WakeupTask(this);
	}

	/**
	 * @param task
	 */
	@Override
	public void wakeupTask (final @Nullable TimerTask task)
	{
		descriptor.o_WakeupTask(this, task);
	}

	/**
	 * @param joiners
	 */
	@Override
	public void joiningFibers (final A_Set joiners)
	{
		descriptor.o_JoiningFibers(this, joiners);
	}

	/**
	 * @return
	 */
	@Override
	public A_Map heritableFiberGlobals ()
	{
		return descriptor.o_HeritableFiberGlobals(this);
	}

	/**
	 * @param globals
	 */
	@Override
	public void heritableFiberGlobals (final A_Map globals)
	{
		descriptor.o_HeritableFiberGlobals(this, globals);
	}

	/**
	 * @param flag
	 * @return
	 */
	@Override
	public boolean generalFlag (final GeneralFlag flag)
	{
		return descriptor.o_GeneralFlag(this, flag);
	}

	/**
	 * @param flag
	 */
	@Override
	public void setGeneralFlag (final GeneralFlag flag)
	{
		descriptor.o_SetGeneralFlag(this, flag);
	}

	/**
	 * @param flag
	 */
	@Override
	public void clearGeneralFlag (final GeneralFlag flag)
	{
		descriptor.o_ClearGeneralFlag(this, flag);
	}

	/**
	 * @return
	 */
	@Override
	public ByteBuffer byteBuffer ()
	{
		return descriptor.o_ByteBuffer(this);
	}

	/**
	 * @param aByteBufferTuple
	 * @return
	 */
	@Override
	public boolean equalsByteBufferTuple (final A_Tuple aByteBufferTuple)
	{
		return descriptor.o_EqualsByteBufferTuple(this, aByteBufferTuple);
	}

	/**
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteBufferTuple
	 * @param startIndex2
	 * @return
	 */
	@Override
	public boolean compareFromToWithByteBufferTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteBufferTuple,
		final int startIndex2)
	{
		return descriptor.o_CompareFromToWithByteBufferTupleStartingAt(
			this,
			startIndex1,
			endIndex1,
			aByteBufferTuple,
			startIndex2);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isByteBufferTuple ()
	{
		return descriptor.o_IsByteBufferTuple(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_String fiberName ()
	{
		return descriptor.o_FiberName(this);
	}

	/**
	 * @param value
	 */
	@Override
	public void fiberName (final A_String value)
	{
		descriptor.o_FiberName(this, value);
	}

	@Override
	public A_Set bundles ()
	{
		return descriptor.o_Bundles(this);
	}

	@Override
	public void methodAddBundle (final A_Bundle bundle)
	{
		descriptor.o_MethodAddBundle(this, bundle);
	}

	@Override
	public A_Module definitionModule ()
	{
		return descriptor.o_DefinitionModule(this);
	}

	@Override
	public A_Bundle bundleOrCreate () throws SignatureException
	{
		return descriptor.o_BundleOrCreate(this);
	}

	@Override
	public A_Bundle bundleOrNil ()
	{
		return descriptor.o_BundleOrNil(this);
	}

	@Override
	public A_Map entryPoints ()
	{
		return descriptor.o_EntryPoints(this);
	}

	/**
	 * @param stringName
	 * @param trueName
	 */
	@Override
	public void addEntryPoint (
		final A_String stringName,
		final A_Atom trueName)
	{
		descriptor.o_AddEntryPoint(this, stringName, trueName);
	}

	@Override
	public A_Set allAncestors ()
	{
		return descriptor.o_AllAncestors(this);
	}

	/**
	 * @param moreAncestors
	 */
	@Override
	public void addAncestors (final A_Set moreAncestors)
	{
		descriptor.o_AddAncestors(this, moreAncestors);
	}

	@Override
	public A_Tuple argumentRestrictionSets ()
	{
		return descriptor.o_ArgumentRestrictionSets(this);
	}

	@Override
	public A_Bundle restrictedBundle ()
	{
		return descriptor.o_RestrictedBundle(this);
	}

	@Override
	public void adjustPcAndStackp (final int pc, final int stackp)
	{
		descriptor.o_AdjustPcAndStackp(this, pc, stackp);
	}

	@Override
	public int treeTupleLevel ()
	{
		return descriptor.o_TreeTupleLevel(this);
	}

	@Override
	public int childCount ()
	{
		return descriptor.o_ChildCount(this);
	}

	@Override
	public A_Tuple childAt (final int childIndex)
	{
		return descriptor.o_ChildAt(this, childIndex);
	}

	@Override
	public A_Tuple concatenateWith (
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		return descriptor.o_ConcatenateWith(this, otherTuple, canDestroy);
	}

	@Override
	public A_Tuple replaceFirstChild (final A_Tuple newFirst)
	{
		return descriptor.o_ReplaceFirstChild(this, newFirst);
	}

	@Override
	public boolean isByteString ()
	{
		return descriptor.o_IsByteString(this);
	}

	@Override
	public boolean isTwoByteString ()
	{
		return descriptor.o_IsTwoByteString(this);
	}

	@Override
	public boolean skipReturnFlag ()
	{
		return descriptor.o_SkipReturnFlag(this);
	}

	/**
	 * @param key
	 * @param reactor
	 * @return
	 */
	@Override
	public A_Variable addWriteReactor (
		final A_Atom key,
		final VariableAccessReactor reactor)
	{
		return descriptor.o_AddWriteReactor(this, key, reactor);
	}

	/**
	 * @param key
	 */
	@Override
	public void removeWriteReactor (final A_Atom key) throws AvailException
	{
		descriptor.o_RemoveWriteReactor(this, key);
	}

	/**
	 * @param flag
	 * @return
	 */
	@Override
	public boolean traceFlag (final TraceFlag flag)
	{
		return descriptor.o_TraceFlag(this, flag);
	}

	/**
	 * @param flag
	 */
	@Override
	public void setTraceFlag (final TraceFlag flag)
	{
		descriptor.o_SetTraceFlag(this, flag);
	}

	/**
	 * @param flag
	 */
	@Override
	public void clearTraceFlag (final TraceFlag flag)
	{
		descriptor.o_ClearTraceFlag(this, flag);
	}

	/**
	 * @param var
	 * @param wasRead
	 */
	@Override
	public void recordVariableAccess (
		final A_Variable var,
		final boolean wasRead)
	{
		descriptor.o_RecordVariableAccess(this, var, wasRead);
	}

	/**
	 * @return
	 */
	@Override
	public A_Set variablesReadBeforeWritten ()
	{
		return descriptor.o_VariablesReadBeforeWritten(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Set variablesWritten ()
	{
		return descriptor.o_VariablesWritten(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Set validWriteReactorFunctions ()
	{
		return descriptor.o_ValidWriteReactorFunctions(this);
	}

	@Override
	public A_Continuation replacingCaller (final A_Continuation newCaller)
	{
		return descriptor.o_ReplacingCaller(this, newCaller);
	}

	@Override
	public void whenContinuationIsAvailableDo (
		final Continuation1<A_Continuation> whenReified)
	{
		descriptor.o_WhenContinuationIsAvailableDo(this, whenReified);
	}

	@Override
	public A_Set getAndClearReificationWaiters ()
	{
		return descriptor.o_GetAndClearReificationWaiters(this);
	}

	@Override
	public boolean isBottom ()
	{
		return descriptor.o_IsBottom(this);
	}

	@Override
	public boolean isTop ()
	{
		return descriptor.o_IsTop(this);
	}

	@Override
	public boolean isAtomSpecial ()
	{
		return descriptor.o_IsAtomSpecial(this);
	}

	@Override
	public void recordLatestPrimitive (final short primitiveNumber)
	{
		descriptor.o_RecordLatestPrimitive(this, primitiveNumber);
	}

	@Override
	public void addPrivateNames (final A_Set trueNames)
	{
		descriptor.o_AddPrivateNames(this, trueNames);
	}

	@Override
	public boolean hasValue ()
	{
		return descriptor.o_HasValue(this);
	}

	@Override
	public void addUnloadFunction (final A_Function unloadFunction)
	{
		descriptor.o_AddUnloadFunction(this, unloadFunction);
	}

	@Override
	public A_Set exportedNames ()
	{
		return descriptor.o_ExportedNames(this);
	}

	@Override
	public A_String leadingWhitespace ()
	{
		return descriptor.o_LeadingWhitespace(this);
	}

	@Override
	public A_String trailingWhitespace ()
	{
		return descriptor.o_TrailingWhitespace(this);
	}

	@Override
	public void trailingWhitespace (final A_String trailingWhitespace)
	{
		descriptor.o_TrailingWhitespace(this, trailingWhitespace);
	}

	@Override
	public boolean isInitializedWriteOnceVariable ()
	{
		return descriptor.o_IsInitializedWriteOnceVariable(this);
	}

	@Override
	public void transferIntoByteBuffer (
		final int startIndex,
		final int endIndex,
		final ByteBuffer outputByteBuffer)
	{
		descriptor.o_TransferIntoByteBuffer(
			this, startIndex, endIndex, outputByteBuffer);
	}

	@Override
	public boolean tupleElementsInRangeAreInstancesOf (
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		return descriptor.o_TupleElementsInRangeAreInstancesOf(
			this, startIndex, endIndex, type);
	}
}
