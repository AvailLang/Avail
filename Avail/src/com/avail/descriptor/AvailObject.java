/**
 * AvailObject.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.descriptor.AbstractNumberDescriptor.Order;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.InfinityDescriptor.IntegerSlots;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.SetDescriptor.SetIterator;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.exceptions.*;
import com.avail.exceptions.ArithmeticException;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.serialization.*;
import com.avail.utility.*;
import com.avail.visitor.*;

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
		A_Function,
		A_Map,
		A_Number,
		A_Set,
		A_Token,
		A_Tuple,
			A_String,
		A_Type
{
	/**
	 * Create all Avail objects statically known to the VM.
	 */
	public static void createAllWellKnownObjects ()
	{
		NilDescriptor.createWellKnownObjects();
		CharacterDescriptor.createWellKnownObjects();
		BottomTypeDescriptor.createWellKnownObjects();
		TupleDescriptor.createWellKnownObjects();
		ListNodeDescriptor.createWellKnownObjects();
		StringDescriptor.createWellKnownObjects();
		TypeDescriptor.createWellKnownObjects();
		InstanceMetaDescriptor.createWellKnownObjects();
		LiteralTokenTypeDescriptor.createWellKnownObjects();
		MapDescriptor.createWellKnownObjects();
		AtomDescriptor.createWellKnownObjects();
		ObjectTypeDescriptor.createWellKnownObjects();
		SetDescriptor.createWellKnownObjects();
		EnumerationTypeDescriptor.createWellKnownObjects();
		InfinityDescriptor.createWellKnownObjects();
		IntegerDescriptor.createWellKnownObjects();
		IntegerRangeTypeDescriptor.createWellKnownObjects();
		FunctionTypeDescriptor.createWellKnownObjects();
		ContinuationTypeDescriptor.createWellKnownObjects();
		ContinuationDescriptor.createWellKnownObjects();
		CompiledCodeTypeDescriptor.createWellKnownObjects();
		MapTypeDescriptor.createWellKnownObjects();
		SetTypeDescriptor.createWellKnownObjects();
		TupleTypeDescriptor.createWellKnownObjects();
		L2ChunkDescriptor.createWellKnownObjects();
		VariableTypeDescriptor.createWellKnownObjects();
		ParseNodeTypeDescriptor.createWellKnownObjects();
		DeclarationNodeDescriptor.DeclarationKind.createWellKnownObjects();
		RawPojoDescriptor.createWellKnownObjects();
		PojoTypeDescriptor.createWellKnownObjects();
		PojoDescriptor.createWellKnownObjects();
		CompiledCodeDescriptor.createWellKnownObjects();
		MethodDescriptor.createWellKnownObjects();
		FloatDescriptor.createWellKnownObjects();
		DoubleDescriptor.createWellKnownObjects();
		MessageBundleDescriptor.createWellKnownObjects();

		AvailRuntime.createWellKnownObjects();
		Serializer.createWellKnownObjects();
		AbstractAvailCompiler.ExpectedToken.createWellKnownObjects();
	}

	/**
	 * Release all {@linkplain AvailObject Avail objects} statically known to
	 * the VM.
	 */
	public static void clearAllWellKnownObjects ()
	{
		Primitive.clearCachedData();
		NilDescriptor.clearWellKnownObjects();
		CharacterDescriptor.clearWellKnownObjects();
		BottomTypeDescriptor.clearWellKnownObjects();
		TupleDescriptor.clearWellKnownObjects();
		ListNodeDescriptor.clearWellKnownObjects();
		StringDescriptor.clearWellKnownObjects();
		TypeDescriptor.clearWellKnownObjects();
		InstanceMetaDescriptor.clearWellKnownObjects();
		LiteralTokenTypeDescriptor.clearWellKnownObjects();
		MapDescriptor.clearWellKnownObjects();
		ObjectTypeDescriptor.clearWellKnownObjects();
		SetDescriptor.clearWellKnownObjects();
		AtomDescriptor.clearWellKnownObjects();
		EnumerationTypeDescriptor.clearWellKnownObjects();
		InfinityDescriptor.clearWellKnownObjects();
		IntegerDescriptor.clearWellKnownObjects();
		IntegerRangeTypeDescriptor.clearWellKnownObjects();
		FunctionTypeDescriptor.clearWellKnownObjects();
		ContinuationTypeDescriptor.clearWellKnownObjects();
		ContinuationDescriptor.clearWellKnownObjects();
		CompiledCodeTypeDescriptor.clearWellKnownObjects();
		MapTypeDescriptor.clearWellKnownObjects();
		SetTypeDescriptor.clearWellKnownObjects();
		TupleTypeDescriptor.clearWellKnownObjects();
		L2ChunkDescriptor.clearWellKnownObjects();
		VariableTypeDescriptor.clearWellKnownObjects();
		ParseNodeTypeDescriptor.clearWellKnownObjects();
		DeclarationNodeDescriptor.DeclarationKind.clearWellKnownObjects();
		RawPojoDescriptor.clearWellKnownObjects();
		PojoTypeDescriptor.clearWellKnownObjects();
		PojoDescriptor.clearWellKnownObjects();
		CompiledCodeDescriptor.clearWellKnownObjects();
		MethodDescriptor.clearWellKnownObjects();
		FloatDescriptor.clearWellKnownObjects();
		DoubleDescriptor.clearWellKnownObjects();
		MessageBundleDescriptor.clearWellKnownObjects();

		AvailRuntime.clearWellKnownObjects();
		Serializer.clearWellKnownObjects();
		AbstractAvailCompiler.ExpectedToken.clearWellKnownObjects();
	}

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
		final List<AvailObject> recursionList,
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
		final List<AvailObject> recursionList = new ArrayList<AvailObject>(10);
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
//		assert canAllocateObjects();
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
		// assert canAllocateObjects();
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
//		if (!canDestroyObjects())
//		{
//			error("Don't invoke this if destructions are disallowed");
//		}
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
		// verifyToSpaceAddress();
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

	@Override
	public final int hashCode ()
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
		final List<A_Type> argTypes)
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
		final A_BasicObject arguments)
	{
		return descriptor.o_AcceptsTupleOfArguments(this, arguments);
	}

	/**
	 * Add the {@linkplain L2ChunkDescriptor chunk} with the given index to the
	 * receiver's list of chunks that depend on it.  The receiver is a
	 * {@linkplain MethodDescriptor method}.  A change in the method's
	 * membership (e.g., adding a new method definition) will cause the chunk
	 * to be invalidated.
	 *
	 * @param aChunkIndex
	 */
	@Override
	public void addDependentChunkIndex (
		final int aChunkIndex)
	{
		descriptor.o_AddDependentChunkIndex(this, aChunkIndex);
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
			final AvailObject definition)
		throws SignatureException
	{
		descriptor.o_MethodAddDefinition(this, definition);
	}

	/**
	 * Add a set of {@linkplain MessageBundleDescriptor grammatical
	 * restrictions} to the receiver.
	 *
	 * @param restrictions The set of grammatical restrictions to be added.
	 */
	@Override
	public void addGrammaticalRestrictions (
		final A_Tuple restrictions)
	{
		descriptor.o_AddGrammaticalRestrictions(this, restrictions);
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
		descriptor.o_AddGrammaticalMessageRestrictions(
			this,
			methodName,
			illegalArgMsgs);
	}

	/**
	 * @param definition
	 */
	@Override
	public void moduleAddDefinition (
		final AvailObject definition)
	{
		descriptor.o_ModuleAddDefinition(
			this,
			definition);
	}

	/**
	 * @param message
	 * @param bundle
	 */
	@Override
	public void atMessageAddBundle (
		final AvailObject message,
		final AvailObject bundle)
	{
		descriptor.o_AtMessageAddBundle(
			this,
			message,
			bundle);
	}

	/**
	 * @param stringName
	 * @param trueName
	 */
	@Override
	public void addImportedName (
		final A_String stringName,
		final A_Atom trueName)
	{
		descriptor.o_AddImportedName(
			this,
			stringName,
			trueName);
	}

	/**
	 * @param stringName
	 * @param trueName
	 */
	@Override
	public void introduceNewName (
		final A_String stringName,
		final A_Atom trueName)
	{
		descriptor.o_IntroduceNewName(
			this,
			stringName,
			trueName);
	}

	/**
	 * @param stringName
	 * @param trueName
	 */
	@Override
	public void addPrivateName (
		final A_String stringName,
		final A_Atom trueName)
	{
		descriptor.o_AddPrivateName(
			this,
			stringName,
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
	public AvailObject binRemoveElementHashCanDestroy (
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{
		return descriptor.o_BinRemoveElementHashCanDestroy(
			this,
			elementObject,
			elementObjectHash,
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
	public void bitVector (
		final int value)
	{
		descriptor.o_BitVector(this, value);
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
	public void buildFilteredBundleTreeFrom (
		final A_BasicObject bundleTree)
	{
		descriptor.o_BuildFilteredBundleTreeFrom(this, bundleTree);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject caller ()
	{
		return descriptor.o_Caller(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void caller (
		final AvailObject value)
	{
		descriptor.o_Caller(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void cleanUpAfterCompile ()
	{
		descriptor.o_CleanUpAfterCompile(this);
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
	public void function (
		final AvailObject value)
	{
		descriptor.o_Function(this, value);
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
	public AvailObject code ()
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
	 * Dispatch to the descriptor.
	 */
	@Override
	public void codePoint (
		final int value)
	{
		descriptor.o_CodePoint(this, value);
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
		final AvailObject aByteString,
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
		final AvailObject aByteTuple,
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
		final AvailObject aNybbleTuple,
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
		final AvailObject anObjectTuple,
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
		final AvailObject aTwoByteString,
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
	public boolean containsBlock (
		final AvailObject aFunction)
	{
		return descriptor.o_ContainsBlock(this, aFunction);
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
	public AvailObject continuation ()
	{
		return descriptor.o_Continuation(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void continuation (
		final AvailObject value)
	{
		descriptor.o_Continuation(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject copyAsMutableContinuation ()
	{
		return descriptor.o_CopyAsMutableContinuation(this);
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
	public A_Tuple copyAsMutableSpliceTuple ()
	{
		return descriptor.o_CopyAsMutableSpliceTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void copyToRestrictedTo (
		final A_BasicObject filteredBundleTree,
		final A_Set visibleNames)
	{
		descriptor.o_CopyToRestrictedTo(
			this,
			filteredBundleTree,
			visibleNames);
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
	 * Dispatch to the descriptor.
	 */
	@Override
	public void displayTestingTree ()
	{
		descriptor.o_DisplayTestingTree(this);
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
	 * Answer the element at the given index of the receiver.
	 *
	 * @param index An integer.
	 * @return The element at the given index.
	 */
	@Override
	public A_BasicObject elementAt (
		final int index)
	{
		return descriptor.o_ElementAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void elementAtPut (
		final int index,
		final A_BasicObject value)
	{
		descriptor.o_ElementAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int endOfZone (
		final int zone)
	{
		return descriptor.o_EndOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int endSubtupleIndexInZone (
		final int zone)
	{
		return descriptor.o_EndSubtupleIndexInZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject ensureMutable ()
	{
		return descriptor.o_EnsureMutable(this);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * This comparison operation takes an {@link Object} as its argument to
	 * avoid accidentally calling this with, say, a {@link String} literal.
	 * We mark it as deprecated to ensure we don't accidentally invoke
	 * this method when we really mean the version that takes an {@code
	 * AvailObject} as an argument.  Eclipse conveniently shows such invocations
	 * with a <strike>strike-out</strike>.  That's a convenient warning for the
	 * programmer, but we also fail if this method actually gets invoked AND
	 * the argument is not an {@code AvailObject}.  That means we don't allow
	 * AvailObjects to be added to Java {@linkplain Set sets} and such, at least
	 * when they're intermixed with things that are not AvailObjects.
	 * </p>
	 */
	@Override
	@Deprecated
	public boolean equals (final @Nullable Object another)
	{
		assert another instanceof AvailObject;
		return descriptor.o_Equals(this, (AvailObject)another);
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
		final AvailObject aByteString)
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
		final AvailObject aByteTuple)
	{
		return descriptor.o_EqualsByteTuple(this, aByteTuple);
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

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
	 * argument, a {@linkplain FunctionDescriptor function}, are equal in value.
	 *
	 * @param aFunction The function used in the comparison.
	 * @return {@code true} if the receiver is a function and of value equal to
	 *         the argument, {@code false} otherwise.
	 * @see AvailObject#equalsFunction(AvailObject)
	 */
	@Override
	public boolean equalsFunction (
		final AvailObject aFunction)
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
		final AvailObject aCompiledCode)
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
		final AvailObject aContinuation)
	{
		return descriptor.o_EqualsContinuation(this, aContinuation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsContinuationType (
		final A_Type aType)
	{
		return descriptor.o_EqualsContinuationType(this, aType);
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
		final AvailObject anAvailInteger)
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
		final AvailObject aMapType)
	{
		return descriptor.o_EqualsMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsNybbleTuple (
		final AvailObject aNybbleTuple)
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
		final AvailObject anObjectTuple)
	{
		return descriptor.o_EqualsObjectTuple(this, anObjectTuple);
	}

	/**
	 * @param aParseNodeType
	 * @return
	 */
	@Override
	public boolean equalsParseNodeType (
		final AvailObject aParseNodeType)
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
		final AvailObject aPrimitiveType)
	{
		return descriptor.o_EqualsPrimitiveType(this, aPrimitiveType);
	}

	/**
	 * @param aRawPojo
	 * @return
	 */
	@Override
	public boolean equalsRawPojo (final AvailObject aRawPojo)
	{
		return descriptor.o_EqualsRawPojo(this, aRawPojo);
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
		final AvailObject aSetType)
	{
		return descriptor.o_EqualsSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsTupleType (
		final AvailObject aTupleType)
	{
		return descriptor.o_EqualsTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean equalsTwoByteString (
		final AvailObject aTwoByteString)
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
	public void expand ()
	{
		descriptor.o_Expand(this);
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
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
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
	public List<AvailObject> filterByTypes (
		final List<A_Type> argTypes)
	{
		return descriptor.o_FilterByTypes(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_BasicObject filteredBundleTree ()
	{
		return descriptor.o_FilteredBundleTree(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_BasicObject forZoneSetSubtupleStartSubtupleIndexEndOfZone (
		final int zone,
		final AvailObject newSubtuple,
		final int startSubtupleIndex,
		final int endOfZone)
	{
		return descriptor.o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone(
			this,
			zone,
			newSubtuple,
			startSubtupleIndex,
			endOfZone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int getInteger ()
	{
		return descriptor.o_GetInteger(this);
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
	public List<AvailObject> definitionsAtOrBelow (
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
	public AvailObject includeBundleNamed (
		final A_Atom messageName)
	{
		return descriptor.o_IncludeBundleNamed(
			this,
			messageName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean includesDefinition (
		final AvailObject imp)
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
	public int index ()
	{
		return descriptor.o_Index(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void index (
		final int value)
	{
		descriptor.o_Index(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int interruptRequestFlags ()
	{
		return descriptor.o_InterruptRequestFlags(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void setInterruptRequestFlag (
		final BitField value)
	{
		descriptor.o_SetInterruptRequestFlag(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void clearInterruptRequestFlags ()
	{
		descriptor.o_ClearInterruptRequestFlags(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int countdownToReoptimize ()
	{
		return descriptor.o_CountdownToReoptimize(this);
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
		final A_BasicObject aTupleType)
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
	 * Dispatch to the descriptor.
	 */
	@Override
	@Deprecated
	public boolean isSaved ()
	{
		return descriptor.o_IsSaved(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	@Deprecated
	public void isSaved (
		final boolean aBoolean)
	{
		descriptor.o_IsSaved(this, aBoolean);
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
	public boolean isSplice ()
	{
		return descriptor.o_IsSplice(this);
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
		final A_BasicObject aVariableType)
	{
		return descriptor.o_IsSupertypeOfVariableType(this, aVariableType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isSupertypeOfContinuationType (
		final A_BasicObject aContinuationType)
	{
		return descriptor.o_IsSupertypeOfContinuationType(this, aContinuationType);
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
		final A_BasicObject aLiteralTokenType)
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
		final A_BasicObject anObjectType)
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
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean isValid ()
	{
		return descriptor.o_IsValid(this);
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
	public AvailObject levelTwoChunk ()
	{
		return descriptor.o_LevelTwoChunk(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void levelTwoChunkOffset (
		final A_BasicObject chunk,
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
	public AvailObject lookupByTypesFromTuple (
		final A_Tuple argumentTypeTuple)
	{
		return descriptor.o_LookupByTypesFromTuple(this, argumentTypeTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject lookupByValuesFromList (
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
		return descriptor.o_MakeImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject makeShared ()
	{
		return descriptor.o_MakeShared(this);
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
	public A_BasicObject methods ()
	{
		return descriptor.o_Methods(this);
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
	public A_Map importedNames ()
	{
		return descriptor.o_ImportedNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public boolean nameVisible (
		final AvailObject trueName)
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
	public int numberOfZones ()
	{
		return descriptor.o_NumberOfZones(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int numDoubles ()
	{
		return descriptor.o_NumDoubles(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int numIntegers ()
	{
		return descriptor.o_NumIntegers(this);
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
	public int numObjects ()
	{
		return descriptor.o_NumObjects(this);
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
	 * Dispatch to the descriptor.
	 */
	@Override
	public void pc (
		final int value)
	{
		descriptor.o_Pc(this, value);
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
	@Deprecated
	public void postFault ()
	{
		descriptor.o_PostFault(this);
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
	public AvailObject priority ()
	{
		return descriptor.o_Priority(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void priority (
		final A_Number value)
	{
		descriptor.o_Priority(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_BasicObject privateAddElement (
		final A_BasicObject element)
	{
		return descriptor.o_PrivateAddElement(this, element);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_BasicObject privateExcludeElement (
		final A_BasicObject element)
	{
		return descriptor.o_PrivateExcludeElement(this, element);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_BasicObject privateExcludeElementKnownIndex (
		final A_BasicObject element,
		final int knownIndex)
	{
		return descriptor.o_PrivateExcludeElementKnownIndex(
			this,
			element,
			knownIndex);
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
	public void rawByteForCharacterAtPut (
		final int index,
		final short anInteger)
	{
		descriptor.o_RawByteForCharacterAtPut(
			this,
			index,
			anInteger);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public byte rawNybbleAt (
		final int index)
	{
		return descriptor.o_RawNybbleAt(this, index);
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
	public void readBarrierFault ()
	{
		descriptor.o_ReadBarrierFault(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void removeDependentChunkIndex (
		final int aChunkIndex)
	{
		descriptor.o_RemoveDependentChunkIndex(this, aChunkIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void removeFrom (
		final L2Interpreter anInterpreter)
	{
		descriptor.o_RemoveFrom(this, anInterpreter);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void removeDefinition (
		final A_BasicObject definition)
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
	public void removeGrammaticalRestrictions (
		final A_Tuple obsoleteRestrictions)
	{
		descriptor.o_RemoveGrammaticalRestrictions(this, obsoleteRestrictions);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void resolveForward (
		final AvailObject forwardDefinition)
	{
		descriptor.o_ResolveForward(this, forwardDefinition);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Tuple grammaticalRestrictions ()
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
	public void setSubtupleForZoneTo (
		final int zoneIndex,
		final A_Tuple newTuple)
	{
		descriptor.o_SetSubtupleForZoneTo(
			this,
			zoneIndex,
			newTuple);
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
	public void size (
		final int value)
	{
		descriptor.o_Size(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int sizeOfZone (
		final int zone)
	{
		return descriptor.o_SizeOfZone(this, zone);
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
	public void stackp (
		final int value)
	{
		descriptor.o_Stackp(this, value);
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
	public AvailObject startingChunk ()
	{
		return descriptor.o_StartingChunk(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void setStartingChunkAndReoptimizationCountdown (
		final A_BasicObject chunk,
		final int countdown)
	{
		descriptor.o_SetStartingChunkAndReoptimizationCountdown(
			this, chunk, countdown);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int startOfZone (
		final int zone)
	{
		return descriptor.o_StartOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int startSubtupleIndexInZone (
		final int zone)
	{
		return descriptor.o_StartSubtupleIndexInZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void step ()
	{
		descriptor.o_Step(this);
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
	public AvailObject subtupleForZone (
		final int zone)
	{
		return descriptor.o_SubtupleForZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public A_Tuple testingTree ()
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
	public int translateToZone (
		final int tupleIndex,
		final int zoneIndex)
	{
		return descriptor.o_TranslateToZone(
			this,
			tupleIndex,
			zoneIndex);
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
	public A_Tuple truncateTo (
		final int newTupleSize)
	{
		return descriptor.o_TruncateTo(this, newTupleSize);
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
	public void tupleAtPut (
		final int index,
		final AvailObject anObject)
	{
		descriptor.o_TupleAtPut(
			this,
			index,
			anObject);
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
	public void type (
		final A_BasicObject value)
	{
		descriptor.o_Type(this, value);
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
	public int untranslatedDataAt (
		final int index)
	{
		return descriptor.o_UntranslatedDataAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void untranslatedDataAtPut (
		final int index,
		final int value)
	{
		descriptor.o_UntranslatedDataAtPut(
			this,
			index,
			value);
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
	public A_Type validateArgumentTypesInterpreterIfFail (
		final List<A_Type> argTypes,
		final Interpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock)
	{
		return descriptor.o_ValidateArgumentTypesInterpreterIfFail(
			this,
			argTypes,
			anAvailInterpreter,
			failBlock);
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
	public A_Tuple vectors ()
	{
		return descriptor.o_Vectors(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public void verify ()
	{
		descriptor.o_Verify(this);
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
	public A_Tuple wordcodes ()
	{
		return descriptor.o_Wordcodes(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public int zoneForIndex (
		final int index)
	{
		return descriptor.o_ZoneForIndex(this, index);
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
	public AvailObject expression ()
	{
		return descriptor.o_Expression(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Override
	public AvailObject variable ()
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
	public AvailObject resultType ()
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
	public AvailObject neededVariables ()
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
	 * @param initializationExpression
	 */
	@Override
	public void initializationExpression (final AvailObject initializationExpression)
	{
		descriptor.o_InitializationExpression(this, initializationExpression);
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
	public AvailObject argumentsListNode ()
	{
		return descriptor.o_ArgumentsListNode(this);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject method ()
	{
		return descriptor.o_Method(this);
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
	public AvailObject declaration ()
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
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		descriptor.o_ChildrenMap(this, aBlock);
	}

	/**
	 * @param aBlock
	 */
	@Override
	public void childrenDo (
		final Continuation1<AvailObject> aBlock)
	{
		descriptor.o_ChildrenDo(this, aBlock);
	}

	/**
	 * @param parent
	 */
	@Override
	public void validateLocally (final @Nullable A_BasicObject parent)
	{
		descriptor.o_ValidateLocally(
			this,
			parent);
	}

	/**
	 * @param codeGenerator
	 * @return
	 */
	@Override
	public AvailObject generate (
		final AvailCodeGenerator codeGenerator)
	{
		return descriptor.o_Generate(this, codeGenerator);
	}

	/**
	 * @param newParseNode
	 * @return
	 */
	@Override
	public AvailObject copyWith (final AvailObject newParseNode)
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
	public AvailObject copyMutableParseNode ()
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
	public AvailObject apparentSendName ()
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
		final List<AvailObject> accumulatedStatements)
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
	public A_Map complete ()
	{
		return descriptor.o_Complete(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Map incomplete ()
	{
		return descriptor.o_Incomplete(this);
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
	 * @param value
	 */
	@Override
	public void versions (final A_BasicObject value)
	{
		descriptor.o_Versions(this, value);
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
	 * @param restrictionSignature
	 */
	@Override
	public void addTypeRestriction (final A_Function restrictionSignature)
	{
		descriptor.o_AddTypeRestriction(this, restrictionSignature);
	}

	/**
	 * @param function
	 */
	@Override
	public void removeTypeRestriction (final A_Function function)
	{
		descriptor.o_RemoveTypeRestriction(this, function);
	}

	/**
	 * @return
	 */
	@Override
	public A_Tuple typeRestrictions ()
	{
		return descriptor.o_TypeRestrictions(this);
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
	public AvailObject sealedArgumentsTypesTuple ()
	{
		return descriptor.o_SealedArgumentsTypesTuple(this);
	}

	/**
	 * @param methodNameAtom
	 * @param typeRestrictionFunction
	 */
	@Override
	public void addTypeRestriction (
		final A_Atom methodNameAtom,
		final A_Function typeRestrictionFunction)
	{
		descriptor.o_AddTypeRestriction(
			this,
			methodNameAtom,
			typeRestrictionFunction);
	}

	/**
	 * @param name
	 * @param constantBinding
	 */
	@Override
	public void addConstantBinding (
		final A_String name,
		final AvailObject constantBinding)
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
		final AvailObject variableBinding)
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
	 * @return
	 */
	@Override
	public A_BasicObject upperBoundMap ()
	{
		return descriptor.o_UpperBoundMap(this);
	}

	/**
	 * @param aMap
	 */
	@Override
	public void upperBoundMap (final A_BasicObject aMap)
	{
		descriptor.o_UpperBoundMap(this, aMap);
	}

	/**
	 * @param another
	 * @return
	 */
	@Override
	public Order numericCompare (final A_Number another)
	{
		return  descriptor.o_NumericCompare(this, another);
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
	public AvailObject issuingModule ()
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
		final A_BasicObject aPojoType)
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
	public Object marshalToJava (final @Nullable Class<?> classHint)
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
	 * @param aRawPojo
	 * @return
	 */
	@Override
	public boolean equalsEqualityRawPojo (final AvailObject aRawPojo)
	{
		return descriptor.o_EqualsEqualityRawPojo(this, aRawPojo);
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
		final A_BasicObject aLiteralTokenType)
	{
		return descriptor.o_EqualsLiteralTokenType(this, aLiteralTokenType);
	}

	/**
	 * @param anObjectType
	 * @return
	 */
	@Override
	public boolean equalsObjectType (
		final AvailObject anObjectType)
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
	public A_BasicObject module ()
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
	 * @return
	 */
	@Override
	public boolean isMarkerNode ()
	{
		return descriptor.o_IsMarkerNode(this);
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
	public boolean equalsParseNode (final A_BasicObject aParseNode)
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
	public AvailObject definitionMethod ()
	{
		return descriptor.o_DefinitionMethod(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_BasicObject prefixFunctions ()
	{
		return descriptor.o_PrefixFunctions(this);
	}

	/**
	 * @param aByteArrayTuple
	 * @return
	 */
	@Override
	public boolean equalsByteArrayTuple (
		final AvailObject aByteArrayTuple)
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
		final AvailObject aByteArrayTuple,
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
	 * @param message
	 */
	@Override
	public void flushForNewOrChangedBundleNamed (
		final A_Atom message)
	{
		descriptor.o_FlushForNewOrChangedBundleNamed(this, message);
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
	 * @return
	 */
	@Override
	public AvailObject moduleName ()
	{
		return descriptor.o_ModuleName(this);
	}

	/**
	 * @return
	 */
	@Override
	public A_Set namesSet ()
	{
		return descriptor.o_NamesSet(this);
	}

	/**
	 * @return
	 */
	@Override
	public AvailObject originalName ()
	{
		return descriptor.o_OriginalName(this);
	}
}
