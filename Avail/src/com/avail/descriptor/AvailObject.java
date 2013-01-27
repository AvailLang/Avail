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
implements Iterable<AvailObject>
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
			for (final AvailObject candidate : recursionList)
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
	public AvailObjectFieldHelper[] describeForDebugger()
	{
		return descriptor.o_DescribeForDebugger(this);
	}

	/**
	 * Answer a name suitable for labeling a field containing this object.
	 *
	 * @return An Avail {@linkplain StringDescriptor string}.
	 */
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
	public final boolean greaterThan (
		final AvailObject another)
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
	public final boolean greaterOrEqual (
		final AvailObject another)
	{
		return numericCompare(another).isMoreOrEqual();
	}

	/**
	 * Answer whether the receiver is numerically less than the argument.
	 *
	 * @param another A {@linkplain AbstractNumberDescriptor numeric object}.
	 * @return Whether the receiver is strictly less than the argument.
	 */
	public final boolean lessThan (
		final AvailObject another)
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
	public final boolean lessOrEqual (
		final AvailObject another)
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
	 * A good multiplier for a multiplicative random generator.  This constant
	 * is a primitive element of (Z[2^32],*), specifically 1664525, as taken
	 * from Knuth, The Art of Computer Programming, Vol. 2, 2nd ed., page 102,
	 * row 26. See also pages 19, 20, theorems B and C. The period of the
	 * cycle based on this multiplicative generator is 2^30.
	 */
	public static final int multiplier = 1664525;

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
	public boolean acceptsArgTypesFromFunctionType (
		final AvailObject functionType)
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
	public boolean acceptsListOfArgTypes (
		final List<AvailObject> argTypes)
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
	public boolean acceptsListOfArgValues (
		final List<AvailObject> argValues)
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
	public boolean acceptsTupleOfArgTypes (
		final AvailObject argTypes)
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
	public boolean acceptsTupleOfArguments (
		final AvailObject arguments)
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
	public void addGrammaticalRestrictions (
		final AvailObject restrictions)
	{
		descriptor.o_AddGrammaticalRestrictions(this, restrictions);
	}

	/**
	 * Add the receiver and the argument {@code anInfinity} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>This method should only be called from {@link
	 * #plusCanDestroy(AvailObject, boolean) plusCanDestroy}. It exists for
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
	AvailObject addToInfinityCanDestroy (
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
	 * #plusCanDestroy(AvailObject, boolean) plusCanDestroy}. It exists for
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
	AvailObject addToIntegerCanDestroy (
		final AvailObject anInteger,
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
	public AvailObject asSet ()
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
	public AvailObject asTuple ()
	{
		return descriptor.o_AsTuple(this);
	}

	/**
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	public void addGrammaticalRestrictions (
		final AvailObject methodName,
		final AvailObject illegalArgMsgs)
	{
		descriptor.o_AddGrammaticalMessageRestrictions(
			this,
			methodName,
			illegalArgMsgs);
	}

	/**
	 * @param definition
	 */
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
	public void addImportedName (
		final AvailObject stringName,
		final AvailObject trueName)
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
	public void introduceNewName (
		final AvailObject stringName,
		final AvailObject trueName)
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
	public void addPrivateName (
		final AvailObject stringName,
		final AvailObject trueName)
	{
		descriptor.o_AddPrivateName(
			this,
			stringName,
			trueName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setBinAddingElementHashLevelCanDestroy (
		final AvailObject elementObject,
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
	public AvailObject binElementAt (
		final int index)
	{
		return descriptor.o_BinElementAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binElementAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor.o_BinElementAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean binHasElementWithHash (
		final AvailObject elementObject,
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
	public int binHash ()
	{
		return descriptor.o_BinHash(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binHash (
		final int value)
	{
		descriptor.o_BinHash(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject binRemoveElementHashCanDestroy (
		final AvailObject elementObject,
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
	public int binSize ()
	{
		return descriptor.o_BinSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void binSize (
		final int value)
	{
		descriptor.o_BinSize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int bitsPerEntry ()
	{
		return descriptor.o_BitsPerEntry(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void bitVector (
		final int value)
	{
		descriptor.o_BitVector(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject bodyBlock ()
	{
		return descriptor.o_BodyBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject bodySignature ()
	{
		return descriptor.o_BodySignature(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject breakpointBlock ()
	{
		return descriptor.o_BreakpointBlock(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void breakpointBlock (
		final AvailObject value)
	{
		descriptor.o_BreakpointBlock(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void buildFilteredBundleTreeFrom (
		final AvailObject bundleTree)
	{
		descriptor.o_BuildFilteredBundleTreeFrom(this, bundleTree);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject caller ()
	{
		return descriptor.o_Caller(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void caller (
		final AvailObject value)
	{
		descriptor.o_Caller(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void cleanUpAfterCompile ()
	{
		descriptor.o_CleanUpAfterCompile(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void clearValue ()
	{
		descriptor.o_ClearValue(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject function ()
	{
		return descriptor.o_Function(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void function (
		final AvailObject value)
	{
		descriptor.o_Function(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject functionType ()
	{
		return descriptor.o_FunctionType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject code ()
	{
		return descriptor.o_Code(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void code (
		final AvailObject value)
	{
		descriptor.o_Code(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int codePoint ()
	{
		return descriptor.o_CodePoint(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public boolean compareFromToWithStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject anotherObject,
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
	public boolean compareFromToWithAnyTupleStartingAt (
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTuple,
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
	public AvailObject lazyComplete ()
	{
		return descriptor.o_LazyComplete(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public AvailObject concatenateTuplesCanDestroy (
		final boolean canDestroy)
	{
		return descriptor.o_ConcatenateTuplesCanDestroy(this, canDestroy);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject constantBindings ()
	{
		return descriptor.o_ConstantBindings(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean containsBlock (
		final AvailObject aFunction)
	{
		return descriptor.o_ContainsBlock(this, aFunction);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject contentType ()
	{
		return descriptor.o_ContentType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject continuation ()
	{
		return descriptor.o_Continuation(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void continuation (
		final AvailObject value)
	{
		descriptor.o_Continuation(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyAsMutableContinuation ()
	{
		return descriptor.o_CopyAsMutableContinuation(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyAsMutableObjectTuple ()
	{
		return descriptor.o_CopyAsMutableObjectTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyAsMutableSpliceTuple ()
	{
		return descriptor.o_CopyAsMutableSpliceTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void copyToRestrictedTo (
		final AvailObject filteredBundleTree,
		final AvailObject visibleNames)
	{
		descriptor.o_CopyToRestrictedTo(
			this,
			filteredBundleTree,
			visibleNames);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject copyTupleFromToCanDestroy (
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
	public boolean couldEverBeInvokedWith (
		final List<AvailObject> argTypes)
	{
		return descriptor.o_CouldEverBeInvokedWith(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject defaultType ()
	{
		return descriptor.o_DefaultType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void displayTestingTree ()
	{
		descriptor.o_DisplayTestingTree(this);
	}

	/**
	 * Divide the receiver by the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #divideIntoIntegerCanDestroy(AvailObject, boolean)
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
	public AvailObject divideCanDestroy (
		final AvailObject aNumber,
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
	public AvailObject noFailDivideCanDestroy (
		final AvailObject aNumber,
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
	 * #divideCanDestroy(AvailObject, boolean) divideCanDestroy}. It exists for
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
	AvailObject divideIntoInfinityCanDestroy (
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
	 * #divideCanDestroy(AvailObject, boolean) divideCanDestroy}. It exists for
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
	AvailObject divideIntoIntegerCanDestroy (
		final AvailObject anInteger,
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
	public AvailObject elementAt (
		final int index)
	{
		return descriptor.o_ElementAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void elementAtPut (
		final int index,
		final AvailObject value)
	{
		descriptor.o_ElementAtPut(
			this,
			index,
			value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int endOfZone (
		final int zone)
	{
		return descriptor.o_EndOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int endSubtupleIndexInZone (
		final int zone)
	{
		return descriptor.o_EndSubtupleIndexInZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public boolean equals (final AvailObject another)
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
	public boolean equalsAnyTuple (
		final AvailObject aTuple)
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
	public boolean equalsFunctionType (
		final AvailObject aFunctionType)
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
	public boolean equalsVariable (
		final AvailObject aVariable)
	{
		return descriptor.o_EqualsVariable(this, aVariable);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsVariableType (
		final AvailObject aVariableType)
	{
		return descriptor.o_EqualsVariableType(this, aVariableType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContinuation (
		final AvailObject aContinuation)
	{
		return descriptor.o_EqualsContinuation(this, aContinuation);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsContinuationType (
		final AvailObject aType)
	{
		return descriptor.o_EqualsContinuationType(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsDouble (
		final double aDouble)
	{
		return descriptor.o_EqualsDouble(this, aDouble);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public boolean equalsInfinity (
		final Sign sign)
	{
		return descriptor.o_EqualsInfinity(this, sign);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsInteger (
		final AvailObject anAvailInteger)
	{
		return descriptor.o_EqualsInteger(this, anAvailInteger);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor.o_EqualsIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsMap (
		final AvailObject aMap)
	{
		return descriptor.o_EqualsMap(this, aMap);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsMapType (
		final AvailObject aMapType)
	{
		return descriptor.o_EqualsMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsNybbleTuple (
		final AvailObject aNybbleTuple)
	{
		return descriptor.o_EqualsNybbleTuple(this, aNybbleTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsObject (
		final AvailObject anObject)
	{
		return descriptor.o_EqualsObject(this, anObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsObjectTuple (
		final AvailObject anObjectTuple)
	{
		return descriptor.o_EqualsObjectTuple(this, anObjectTuple);
	}

	/**
	 * @param aParseNodeType
	 * @return
	 */
	public boolean equalsParseNodeType (
		final AvailObject aParseNodeType)
	{
		return descriptor.o_EqualsParseNodeType(this, aParseNodeType);
	}

	/**
	 * @param aPojo
	 * @return
	 */
	public boolean equalsPojo (final AvailObject aPojo)
	{
		return descriptor.o_EqualsPojo(this, aPojo);
	}

	/**
	 * @param aPojoType
	 * @return
	 */
	public boolean equalsPojoType (final AvailObject aPojoType)
	{
		return descriptor.o_EqualsPojoType(this, aPojoType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsPrimitiveType (
		final AvailObject aPrimitiveType)
	{
		return descriptor.o_EqualsPrimitiveType(this, aPrimitiveType);
	}

	/**
	 * @param aRawPojo
	 * @return
	 */
	public boolean equalsRawPojo (final AvailObject aRawPojo)
	{
		return descriptor.o_EqualsRawPojo(this, aRawPojo);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsSet (
		final AvailObject aSet)
	{
		return descriptor.o_EqualsSet(this, aSet);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsSetType (
		final AvailObject aSetType)
	{
		return descriptor.o_EqualsSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsTupleType (
		final AvailObject aTupleType)
	{
		return descriptor.o_EqualsTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsTwoByteString (
		final AvailObject aTwoByteString)
	{
		return descriptor.o_EqualsTwoByteString(this, aTwoByteString);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean equalsNil ()
	{
		return descriptor.o_EqualsNil(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public ExecutionState executionState ()
	{
		return descriptor.o_ExecutionState(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void executionState (
		final ExecutionState value)
	{
		descriptor.o_ExecutionState(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void expand ()
	{
		descriptor.o_Expand(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean extractBoolean ()
	{
		return descriptor.o_ExtractBoolean(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short extractUnsignedByte ()
	{
		return descriptor.o_ExtractUnsignedByte(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public double extractDouble ()
	{
		return descriptor.o_ExtractDouble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public float extractFloat ()
	{
		return descriptor.o_ExtractFloat(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public long extractLong ()
	{
		return descriptor.o_ExtractLong(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public byte extractNybble ()
	{
		return descriptor.o_ExtractNybble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public byte extractNybbleFromTupleAt (
		final int index)
	{
		return descriptor.o_ExtractNybbleFromTupleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject fieldMap ()
	{
		return descriptor.o_FieldMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject fieldTypeMap ()
	{
		return descriptor.o_FieldTypeMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public List<AvailObject> filterByTypes (
		final List<AvailObject> argTypes)
	{
		return descriptor.o_FilterByTypes(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject filteredBundleTree ()
	{
		return descriptor.o_FilteredBundleTree(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject forZoneSetSubtupleStartSubtupleIndexEndOfZone (
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
	public int getInteger ()
	{
		return descriptor.o_GetInteger(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public boolean hasElement (
		final AvailObject elementObject)
	{
		return descriptor.o_HasElement(this, elementObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public int hashOrZero ()
	{
		return descriptor.o_HashOrZero(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void hashOrZero (
		final int value)
	{
		descriptor.o_HashOrZero(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasKey (
		final AvailObject keyObject)
	{
		return descriptor.o_HasKey(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasObjectInstance (
		final AvailObject potentialInstance)
	{
		return descriptor.o_HasObjectInstance(this, potentialInstance);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean hasGrammaticalRestrictions ()
	{
		return descriptor.o_HasGrammaticalRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public List<AvailObject> definitionsAtOrBelow (
		final List<AvailObject> argTypes)
	{
		return descriptor.o_DefinitionsAtOrBelow(this, argTypes);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject definitionsTuple ()
	{
		return descriptor.o_DefinitionsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject includeBundle (
		final AvailObject messageBundle)
	{
		return descriptor.o_IncludeBundle(
			this,
			messageBundle);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject includeBundleNamed (
		final AvailObject message)
	throws SignatureException
	{
		return descriptor.o_IncludeBundleNamed(
			this,
			message);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean includesDefinition (
		final AvailObject imp)
	{
		return descriptor.o_IncludesDefinition(this, imp);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lazyIncomplete ()
	{
		return descriptor.o_LazyIncomplete(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int index ()
	{
		return descriptor.o_Index(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void index (
		final int value)
	{
		descriptor.o_Index(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int interruptRequestFlags ()
	{
		return descriptor.o_InterruptRequestFlags(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void setInterruptRequestFlag (
		final BitField value)
	{
		descriptor.o_SetInterruptRequestFlag(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void clearInterruptRequestFlags ()
	{
		descriptor.o_ClearInterruptRequestFlags(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int countdownToReoptimize ()
	{
		return descriptor.o_CountdownToReoptimize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void countdownToReoptimize (
		final int value)
	{
		descriptor.o_CountdownToReoptimize(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isAbstract ()
	{
		return descriptor.o_IsAbstract(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isAbstractDefinition ()
	{
		return descriptor.o_IsAbstractDefinition(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBetterRepresentationThan (
		final AvailObject anotherObject)
	{
		return descriptor.o_IsBetterRepresentationThan(this, anotherObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBetterRepresentationThanTupleType (
		final AvailObject aTupleType)
	{
		return descriptor.o_IsBetterRepresentationThanTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isBinSubsetOf (
		final AvailObject potentialSuperset)
	{
		return descriptor.o_IsBinSubsetOf(this, potentialSuperset);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail boolean?
	 *
	 * @return {@code true} if the receiver is a boolean, {@code false}
	 *         otherwise.
	 */
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
	public boolean isExtendedInteger ()
	{
		return descriptor.o_IsExtendedInteger(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public boolean isMethodDefinition ()
	{
		return descriptor.o_IsMethodDefinition(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isInstanceOf (
		final AvailObject aType)
	{
		return descriptor.o_IsInstanceOf(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isInstanceOfKind (
		final AvailObject aType)
	{
		return descriptor.o_IsInstanceOfKind(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isIntegerRangeType ()
	{
		return descriptor.o_IsIntegerRangeType(this);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail map?
	 *
	 * @return {@code true} if the receiver is a map, {@code false} otherwise.
	 */
	public boolean isMap ()
	{
		return descriptor.o_IsMap(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public boolean isNybble ()
	{
		return descriptor.o_IsNybble(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isPositive ()
	{
		return descriptor.o_IsPositive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	@Deprecated
	public boolean isSaved ()
	{
		return descriptor.o_IsSaved(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public boolean isSet ()
	{
		return descriptor.o_IsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSetType ()
	{
		return descriptor.o_IsSetType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSplice ()
	{
		return descriptor.o_IsSplice(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSubsetOf (
		final AvailObject another)
	{
		return descriptor.o_IsSubsetOf(this, another);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail string?
	 *
	 * @return {@code true} if the receiver is an Avail string, {@code false}
	 *         otherwise.
	 */
	public boolean isString ()
	{
		return descriptor.o_IsString(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSubtypeOf (
		final AvailObject aType)
	{
		return descriptor.o_IsSubtypeOf(this, aType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfVariableType (
		final AvailObject aVariableType)
	{
		return descriptor.o_IsSupertypeOfVariableType(this, aVariableType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfContinuationType (
		final AvailObject aContinuationType)
	{
		return descriptor.o_IsSupertypeOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfFunctionType (
		final AvailObject aFunctionType)
	{
		return descriptor.o_IsSupertypeOfFunctionType(this, aFunctionType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor.o_IsSupertypeOfIntegerRangeType(
			this,
			anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfLiteralTokenType (
		final AvailObject aLiteralTokenType)
	{
		return descriptor.o_IsSupertypeOfLiteralTokenType(
			this,
			aLiteralTokenType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfMapType (
		final AvailObject aMapType)
	{
		return descriptor.o_IsSupertypeOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor.o_IsSupertypeOfObjectType(this, anObjectType);
	}

	/**
	 */
	public boolean isSupertypeOfParseNodeType (
		final AvailObject aParseNodeType)
	{
		return descriptor.o_IsSupertypeOfParseNodeType(this, aParseNodeType);
	}

	/**
	 * Dispatch to the descriptor
	 */
	public boolean isSupertypeOfPojoType (
		final AvailObject aPojoType)
	{
		return descriptor.o_IsSupertypeOfPojoType(this, aPojoType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public boolean isSupertypeOfSetType (
		final AvailObject aSetType)
	{
		return descriptor.o_IsSupertypeOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfBottom ()
	{
		return descriptor.o_IsSupertypeOfBottom(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isSupertypeOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor.o_IsSupertypeOfTupleType(this, aTupleType);
	}

	/**
	 */
	public boolean isSupertypeOfEnumerationType (
		final AvailObject anEnumerationType)
	{
		return descriptor.o_IsSupertypeOfEnumerationType(
			this, anEnumerationType);
	}

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail tuple?
	 *
	 * @return {@code true} if the receiver is a tuple, {@code false} otherwise.
	 */
	public boolean isTuple ()
	{
		return descriptor.o_IsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isTupleType ()
	{
		return descriptor.o_IsTupleType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean isType ()
	{
		return descriptor.o_IsType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public AvailObject keysAsSet ()
	{
		return descriptor.o_KeysAsSet(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject keyType ()
	{
		return descriptor.o_KeyType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject levelTwoChunk ()
	{
		return descriptor.o_LevelTwoChunk(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void levelTwoChunkOffset (
		final AvailObject chunk,
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
	public int levelTwoOffset ()
	{
		return descriptor.o_LevelTwoOffset(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject literal ()
	{
		return descriptor.o_Literal(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject literalAt (
		final int index)
	{
		return descriptor.o_LiteralAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject argOrLocalOrStackAt (
		final int index)
	{
		return descriptor.o_ArgOrLocalOrStackAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public AvailObject localTypeAt (
		final int index)
	{
		return descriptor.o_LocalTypeAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByTypesFromTuple (
		final AvailObject argumentTypeTuple)
	{
		return descriptor.o_LookupByTypesFromTuple(this, argumentTypeTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByValuesFromList (
		final List<AvailObject> argumentList)
	{
		return descriptor.o_LookupByValuesFromList(this, argumentList);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lookupByValuesFromTuple (
		final AvailObject argumentTuple)
	{
		return descriptor.o_LookupByValuesFromTuple(this, argumentTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lowerBound ()
	{
		return descriptor.o_LowerBound(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean lowerInclusive ()
	{
		return descriptor.o_LowerInclusive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject makeImmutable ()
	{
		return descriptor.o_MakeImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject makeShared ()
	{
		return descriptor.o_MakeShared(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void makeSubobjectsImmutable ()
	{
		descriptor.o_MakeSubobjectsImmutable(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void makeSubobjectsShared ()
	{
		descriptor.o_MakeSubobjectsShared(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject mapAt (
		final AvailObject keyObject)
	{
		return descriptor.o_MapAt(this, keyObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject mapAtPuttingCanDestroy (
		final AvailObject keyObject,
		final AvailObject newValueObject,
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
	public int mapSize ()
	{
		return descriptor.o_MapSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject mapWithoutKeyCanDestroy (
		final AvailObject keyObject,
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
	public int maxStackDepth ()
	{
		return descriptor.o_MaxStackDepth(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject message ()
	{
		return descriptor.o_Message(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject messageParts ()
	{
		return descriptor.o_MessageParts(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject methods ()
	{
		return descriptor.o_Methods(this);
	}

	/**
	 * Subtract the argument {@code aNumber} from a receiver and answer
	 * the {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #subtractFromIntegerCanDestroy(AvailObject, boolean)
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
	public AvailObject minusCanDestroy (
		final AvailObject aNumber,
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
	public AvailObject noFailMinusCanDestroy (
		final AvailObject aNumber,
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
	 * #timesCanDestroy(AvailObject, boolean) timesCanDestroy}. It exists for
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
	AvailObject multiplyByInfinityCanDestroy (
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
	 * #timesCanDestroy(AvailObject, boolean) timesCanDestroy}. It exists for
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
	AvailObject multiplyByIntegerCanDestroy (
		final AvailObject anInteger,
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
	public AvailObject name ()
	{
		return descriptor.o_Name(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void name (
		final AvailObject value)
	{
		descriptor.o_Name(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject importedNames ()
	{
		return descriptor.o_ImportedNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean nameVisible (
		final AvailObject trueName)
	{
		return descriptor.o_NameVisible(this, trueName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject newNames ()
	{
		return descriptor.o_NewNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numArgs ()
	{
		return descriptor.o_NumArgs(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numArgsAndLocalsAndStack ()
	{
		return descriptor.o_NumArgsAndLocalsAndStack(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numberOfZones ()
	{
		return descriptor.o_NumberOfZones(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numDoubles ()
	{
		return descriptor.o_NumDoubles(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numIntegers ()
	{
		return descriptor.o_NumIntegers(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numLiterals ()
	{
		return descriptor.o_NumLiterals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numLocals ()
	{
		return descriptor.o_NumLocals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numObjects ()
	{
		return descriptor.o_NumObjects(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numOuters ()
	{
		return descriptor.o_NumOuters(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int numOuterVars ()
	{
		return descriptor.o_NumOuterVars(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject nybbles ()
	{
		return descriptor.o_Nybbles(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean optionallyNilOuterVar (
		final int index)
	{
		return descriptor.o_OptionallyNilOuterVar(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject outerTypeAt (
		final int index)
	{
		return descriptor.o_OuterTypeAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject outerVarAt (
		final int index)
	{
		return descriptor.o_OuterVarAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public AvailObject parent ()
	{
		return descriptor.o_Parent(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void parent (
		final AvailObject value)
	{
		descriptor.o_Parent(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int pc ()
	{
		return descriptor.o_Pc(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	 * #addToIntegerCanDestroy(AvailObject, boolean) addToIntegerCanDestroy} or
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
	public AvailObject plusCanDestroy (
		final AvailObject aNumber,
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
	public AvailObject noFailPlusCanDestroy (
		final AvailObject aNumber,
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
	@Deprecated
	public void postFault ()
	{
		descriptor.o_PostFault(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int primitiveNumber ()
	{
		return descriptor.o_PrimitiveNumber(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject priority ()
	{
		return descriptor.o_Priority(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void priority (
		final AvailObject value)
	{
		descriptor.o_Priority(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateAddElement (
		final AvailObject element)
	{
		return descriptor.o_PrivateAddElement(this, element);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateExcludeElement (
		final AvailObject element)
	{
		return descriptor.o_PrivateExcludeElement(this, element);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject privateExcludeElementKnownIndex (
		final AvailObject element,
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
	public AvailObject privateNames ()
	{
		return descriptor.o_PrivateNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject fiberGlobals ()
	{
		return descriptor.o_FiberGlobals(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void fiberGlobals (final AvailObject value)
	{
		descriptor.o_FiberGlobals(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public short rawByteAt (
		final int index)
	{
		return descriptor.o_RawByteAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public short rawByteForCharacterAt (
		final int index)
	{
		return descriptor.o_RawByteForCharacterAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public byte rawNybbleAt (
		final int index)
	{
		return descriptor.o_RawNybbleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public int rawShortForCharacterAt (
		final int index)
	{
		return descriptor.o_RawShortForCharacterAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public int rawSignedIntegerAt (
		final int index)
	{
		return descriptor.o_RawSignedIntegerAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public long rawUnsignedIntegerAt (
		final int index)
	{
		return descriptor.o_RawUnsignedIntegerAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public void readBarrierFault ()
	{
		descriptor.o_ReadBarrierFault(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeDependentChunkIndex (
		final int aChunkIndex)
	{
		descriptor.o_RemoveDependentChunkIndex(this, aChunkIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeFrom (
		final L2Interpreter anInterpreter)
	{
		descriptor.o_RemoveFrom(this, anInterpreter);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeDefinition (
		final AvailObject definition)
	{
		descriptor.o_RemoveDefinition(this, definition);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean removeBundleNamed (
		final AvailObject message)
	{
		return descriptor.o_RemoveBundleNamed(
			this,
			message);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void removeGrammaticalRestrictions (
		final AvailObject obsoleteRestrictions)
	{
		descriptor.o_RemoveGrammaticalRestrictions(this, obsoleteRestrictions);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void resolvedForwardWithName (
		final AvailObject forwardImplementation,
		final AvailObject methodName)
	{
		descriptor.o_ResolvedForwardWithName(
			this,
			forwardImplementation,
			methodName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject grammaticalRestrictions ()
	{
		return descriptor.o_GrammaticalRestrictions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject returnType ()
	{
		return descriptor.o_ReturnType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void scanSubobjects (
		final AvailSubobjectVisitor visitor)
	{
		descriptor.o_ScanSubobjects(this, visitor);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setIntersectionCanDestroy (
		final AvailObject otherSet,
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
	public AvailObject setMinusCanDestroy (
		final AvailObject otherSet,
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
	public int setSize ()
	{
		return descriptor.o_SetSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void setSubtupleForZoneTo (
		final int zoneIndex,
		final AvailObject newTuple)
	{
		descriptor.o_SetSubtupleForZoneTo(
			this,
			zoneIndex,
			newTuple);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setUnionCanDestroy (
		final AvailObject otherSet,
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
	public void setValue (
		final AvailObject newValue)
	{
		descriptor.o_SetValue(this, newValue);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void setValueNoCheck (
		final AvailObject newValue)
	{
		descriptor.o_SetValueNoCheck(this, newValue);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject setWithElementCanDestroy (
		final AvailObject newElementObject,
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
	public AvailObject setWithoutElementCanDestroy (
		final AvailObject elementObjectToExclude,
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
	public void size (
		final int value)
	{
		descriptor.o_Size(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int sizeOfZone (
		final int zone)
	{
		return descriptor.o_SizeOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject sizeRange ()
	{
		return descriptor.o_SizeRange(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject lazyActions ()
	{
		return descriptor.o_LazyActions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject stackAt (
		final int slotIndex)
	{
		return descriptor.o_StackAt(this, slotIndex);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void stackAtPut (
		final int slotIndex,
		final AvailObject anObject)
	{
		descriptor.o_StackAtPut(
			this,
			slotIndex,
			anObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int stackp ()
	{
		return descriptor.o_Stackp(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void stackp (
		final int value)
	{
		descriptor.o_Stackp(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int start ()
	{
		return descriptor.o_Start(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject startingChunk ()
	{
		return descriptor.o_StartingChunk(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void setStartingChunkAndReoptimizationCountdown (
		final AvailObject chunk,
		final int countdown)
	{
		descriptor.o_SetStartingChunkAndReoptimizationCountdown(
			this, chunk, countdown);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int startOfZone (
		final int zone)
	{
		return descriptor.o_StartOfZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int startSubtupleIndexInZone (
		final int zone)
	{
		return descriptor.o_StartSubtupleIndexInZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void step ()
	{
		descriptor.o_Step(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject string ()
	{
		return descriptor.o_String(this);
	}

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * #minusCanDestroy(AvailObject, boolean) minusCanDestroy}. It
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
	AvailObject subtractFromInfinityCanDestroy (
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
	 * #minusCanDestroy(AvailObject, boolean) minusCanDestroy}. It
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
	AvailObject subtractFromIntegerCanDestroy (
		final AvailObject anInteger,
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
	public AvailObject subtupleForZone (
		final int zone)
	{
		return descriptor.o_SubtupleForZone(this, zone);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject testingTree ()
	{
		return descriptor.o_TestingTree(this);
	}

	/**
	 * Multiply the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #multiplyByIntegerCanDestroy(AvailObject, boolean)
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
	public AvailObject timesCanDestroy (
		final AvailObject aNumber,
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
	public AvailObject noFailTimesCanDestroy (
		final AvailObject aNumber,
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
	public TokenDescriptor.TokenType tokenType ()
	{
		return descriptor.o_TokenType(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public AvailObject traversed ()
	{
		return descriptor.o_Traversed(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void trimExcessInts ()
	{
		descriptor.o_TrimExcessInts(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject trueNamesForStringName (
		final AvailObject stringName)
	{
		return descriptor.o_TrueNamesForStringName(this, stringName);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject truncateTo (
		final int newTupleSize)
	{
		return descriptor.o_TruncateTo(this, newTupleSize);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject tupleAt (
		final int index)
	{
		return descriptor.o_TupleAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void tupleAtPut (
		final int index,
		final AvailObject aNybbleObject)
	{
		descriptor.o_TupleAtPut(
			this,
			index,
			aNybbleObject);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject tupleAtPuttingCanDestroy (
		final int index,
		final AvailObject newValueObject,
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
	public int tupleIntAt (
		final int index)
	{
		return descriptor.o_TupleIntAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int tupleSize ()
	{
		return descriptor.o_TupleSize(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject kind ()
	{
		return descriptor.o_Kind(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void type (
		final AvailObject value)
	{
		descriptor.o_Type(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeAtIndex (
		final int index)
	{
		return descriptor.o_TypeAtIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersection (
		final AvailObject another)
	{
		return descriptor.o_TypeIntersection(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfFunctionType (
		final AvailObject aFunctionType)
	{
		return descriptor.o_TypeIntersectionOfFunctionType(
			this,
			aFunctionType);
	}

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	public AvailObject typeIntersectionOfCompiledCodeType (
		final AvailObject aCompiledCodeType)
	{
		return descriptor.o_TypeIntersectionOfCompiledCodeType(
			this,
			aCompiledCodeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfVariableType (
		final AvailObject aVariableType)
	{
		return descriptor.o_TypeIntersectionOfVariableType(this, aVariableType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfContinuationType (
		final AvailObject aContinuationType)
	{
		return descriptor.o_TypeIntersectionOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor.o_TypeIntersectionOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfMapType (
		final AvailObject aMapType)
	{
		return descriptor.o_TypeIntersectionOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor.o_TypeIntersectionOfObjectType(this, anObjectType);
	}

	/**
	 * @param aParseNodeType
	 * @return
	 */
	public AvailObject typeIntersectionOfParseNodeType (
		final AvailObject aParseNodeType)
	{
		return descriptor.o_TypeIntersectionOfParseNodeType(
			this,
			aParseNodeType);
	}

	/**
	 * @param aPojoType
	 * @return
	 */
	public AvailObject typeIntersectionOfPojoType (
		final AvailObject aPojoType)
	{
		return descriptor.o_TypeIntersectionOfPojoType(this, aPojoType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfSetType (
		final AvailObject aSetType)
	{
		return descriptor.o_TypeIntersectionOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeIntersectionOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor.o_TypeIntersectionOfTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeTuple ()
	{
		return descriptor.o_TypeTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnion (
		final AvailObject another)
	{
		return descriptor.o_TypeUnion(this, another);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfFunctionType (
		final AvailObject aFunctionType)
	{
		return descriptor.o_TypeUnionOfFunctionType(this, aFunctionType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfVariableType (
		final AvailObject aVariableType)
	{
		return descriptor.o_TypeUnionOfVariableType(this, aVariableType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfContinuationType (
		final AvailObject aContinuationType)
	{
		return descriptor.o_TypeUnionOfContinuationType(this, aContinuationType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfIntegerRangeType (
		final AvailObject anIntegerRangeType)
	{
		return descriptor.o_TypeUnionOfIntegerRangeType(this, anIntegerRangeType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfMapType (
		final AvailObject aMapType)
	{
		return descriptor.o_TypeUnionOfMapType(this, aMapType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfObjectType (
		final AvailObject anObjectType)
	{
		return descriptor.o_TypeUnionOfObjectType(this, anObjectType);
	}

	/**
	 * @param aParseNodeType
	 * @return
	 */
	public AvailObject typeUnionOfParseNodeType (
		final AvailObject aParseNodeType)
	{
		return descriptor.o_TypeUnionOfParseNodeType(
			this,
			aParseNodeType);
	}

	/**
	 * @param aPojoType
	 * @return
	 */
	public AvailObject typeUnionOfPojoType (
		final AvailObject aPojoType)
	{
		return descriptor.o_TypeUnionOfPojoType(this, aPojoType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfSetType (
		final AvailObject aSetType)
	{
		return descriptor.o_TypeUnionOfSetType(this, aSetType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject typeUnionOfTupleType (
		final AvailObject aTupleType)
	{
		return descriptor.o_TypeUnionOfTupleType(this, aTupleType);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject unionOfTypesAtThrough (
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
	public int untranslatedDataAt (
		final int index)
	{
		return descriptor.o_UntranslatedDataAt(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
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
	public AvailObject upperBound ()
	{
		return descriptor.o_UpperBound(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public boolean upperInclusive ()
	{
		return descriptor.o_UpperInclusive(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject validateArgumentTypesInterpreterIfFail (
		final List<AvailObject> argTypes,
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
	public AvailObject value ()
	{
		return descriptor.o_Value(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void value (
		final AvailObject value)
	{
		descriptor.o_Value(this, value);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject valuesAsTuple ()
	{
		return descriptor.o_ValuesAsTuple(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject variableBindings ()
	{
		return descriptor.o_VariableBindings(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject vectors ()
	{
		return descriptor.o_Vectors(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public void verify ()
	{
		descriptor.o_Verify(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject visibleNames ()
	{
		return descriptor.o_VisibleNames(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject wordcodes ()
	{
		return descriptor.o_Wordcodes(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public int zoneForIndex (
		final int index)
	{
		return descriptor.o_ZoneForIndex(this, index);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject parsingInstructions ()
	{
		return descriptor.o_ParsingInstructions(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject expression ()
	{
		return descriptor.o_Expression(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject variable ()
	{
		return descriptor.o_Variable(this);
	}

	/**
	 * @return
	 */
	public AvailObject argumentsTuple ()
	{
		return descriptor.o_ArgumentsTuple(this);
	}

	/**
	 * @return
	 */
	public AvailObject statementsTuple ()
	{
		return descriptor.o_StatementsTuple(this);
	}

	/**
	 * @return
	 */
	public AvailObject resultType ()
	{
		return descriptor.o_ResultType(this);
	}

	/**
	 * @param neededVariables
	 */
	public void neededVariables (final AvailObject neededVariables)
	{
		descriptor.o_NeededVariables(this, neededVariables);
	}

	/**
	 * @return
	 */
	public AvailObject neededVariables ()
	{
		return descriptor.o_NeededVariables(this);
	}

	/**
	 * @return
	 */
	public int primitive ()
	{
		return descriptor.o_Primitive(this);
	}

	/**
	 * @return
	 */
	public AvailObject declaredType ()
	{
		return descriptor.o_DeclaredType(this);
	}

	/**
	 * @return
	 */
	public DeclarationKind declarationKind ()
	{
		return descriptor.o_DeclarationKind(this);
	}

	/**
	 * @return
	 */
	public AvailObject initializationExpression ()
	{
		return descriptor.o_InitializationExpression(this);
	}

	/**
	 * @param initializationExpression
	 */
	public void initializationExpression (final AvailObject initializationExpression)
	{
		descriptor.o_InitializationExpression(this, initializationExpression);
	}

	/**
	 * @return
	 */
	public AvailObject literalObject ()
	{
		return descriptor.o_LiteralObject(this);
	}

	/**
	 * @return
	 */
	public AvailObject token ()
	{
		return descriptor.o_Token(this);
	}

	/**
	 * @return
	 */
	public AvailObject markerValue ()
	{
		return descriptor.o_MarkerValue(this);
	}

	/**
	 * @return
	 */
	public AvailObject argumentsListNode ()
	{
		return descriptor.o_ArgumentsListNode(this);
	}

	/**
	 * @return
	 */
	public AvailObject method ()
	{
		return descriptor.o_Method(this);
	}

	/**
	 * @return
	 */
	public AvailObject expressionsTuple ()
	{
		return descriptor.o_ExpressionsTuple(this);
	}

	/**
	 * @return
	 */
	public AvailObject declaration ()
	{
		return descriptor.o_Declaration(this);
	}

	/**
	 * @return
	 */
	public AvailObject expressionType ()
	{
		return descriptor.o_ExpressionType(this);
	}

	/**
	 * @param codeGenerator
	 */
	public void emitEffectOn (final AvailCodeGenerator codeGenerator)
	{
		descriptor.o_EmitEffectOn(this, codeGenerator);
	}

	/**
	 * @param codeGenerator
	 */
	public void emitValueOn (final AvailCodeGenerator codeGenerator)
	{
		descriptor.o_EmitValueOn(this, codeGenerator);
	}

	/**
	 * @param aBlock
	 */
	public void childrenMap (
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		descriptor.o_ChildrenMap(this, aBlock);
	}

	/**
	 * @param aBlock
	 */
	public void childrenDo (
		final Continuation1<AvailObject> aBlock)
	{
		descriptor.o_ChildrenDo(this, aBlock);
	}

	/**
	 * @param parent
	 */
	public void validateLocally (final @Nullable AvailObject parent)
	{
		descriptor.o_ValidateLocally(
			this,
			parent);
	}

	/**
	 * @param codeGenerator
	 * @return
	 */
	public AvailObject generate (
		final AvailCodeGenerator codeGenerator)
	{
		return descriptor.o_Generate(this, codeGenerator);
	}

	/**
	 * @param newParseNode
	 * @return
	 */
	public AvailObject copyWith (final AvailObject newParseNode)
	{
		return descriptor.o_CopyWith(this, newParseNode);
	}

	/**
	 * @param isLastUse
	 */
	public void isLastUse (final boolean isLastUse)
	{
		descriptor.o_IsLastUse(this, isLastUse);
	}

	/**
	 * @return
	 */
	public boolean isLastUse ()
	{
		return descriptor.o_IsLastUse(this);
	}

	/**
	 * @return
	 */
	public boolean isMacroDefinition ()
	{
		return descriptor.o_IsMacroDefinition(this);
	}

	/**
	 * @return
	 */
	public AvailObject copyMutableParseNode ()
	{
		return descriptor.o_CopyMutableParseNode(this);
	}

	/**
	 * Dispatch to the descriptor.
	 */
	public AvailObject binUnionKind ()
	{
		return descriptor.o_BinUnionKind(this);
	}

	/**
	 * @return
	 */
	public AvailObject outputParseNode ()
	{
		return descriptor.o_OutputParseNode(this);
	}

	/**
	 * @return
	 */
	public AvailObject apparentSendName ()
	{
		return descriptor.o_ApparentSendName(this);
	}

	/**
	 * @return
	 */
	public AvailObject statements ()
	{
		return descriptor.o_Statements(this);
	}

	/**
	 * @param accumulatedStatements
	 */
	public void flattenStatementsInto (
		final List<AvailObject> accumulatedStatements)
	{
		descriptor.o_FlattenStatementsInto(this, accumulatedStatements);
	}

	/**
	 * @return
	 */
	public int lineNumber ()
	{
		return descriptor.o_LineNumber(this);
	}


	/**
	 * @return
	 */
	public AvailObject allBundles ()
	{
		return descriptor.o_AllBundles(this);
	}


	/**
	 * @return
	 */
	public boolean isSetBin ()
	{
		return descriptor.o_IsSetBin(this);
	}

	/**
	 * @return
	 */
	public MapDescriptor.MapIterable mapIterable ()
	{
		return descriptor.o_MapIterable(this);
	}

	/**
	 * @return
	 */
	public AvailObject complete ()
	{
		return descriptor.o_Complete(this);
	}

	/**
	 * @return
	 */
	public AvailObject incomplete ()
	{
		return descriptor.o_Incomplete(this);
	}

	/**
	 * @return
	 */
	public AvailObject declaredExceptions ()
	{
		return descriptor.o_DeclaredExceptions(this);
	}

	/**
	 * @return
	 */
	public boolean isInt ()
	{
		return descriptor.o_IsInt(this);
	}

	/**
	 * @return
	 */
	public boolean isLong ()
	{
		return descriptor.o_IsLong(this);
	}

	/**
	 * @return
	 */
	public AvailObject argsTupleType ()
	{
		return descriptor.o_ArgsTupleType(this);
	}

	/**
	 * @param anInstanceType
	 * @return
	 */
	public boolean equalsInstanceTypeFor (
		final AvailObject anInstanceType)
	{
		return descriptor.o_EqualsInstanceTypeFor(this, anInstanceType);
	}

	/**
	 * @return
	 */
	public AvailObject instances ()
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
	public boolean equalsEnumerationWithSet (final AvailObject aSet)
	{
		return descriptor.o_EqualsEnumerationWithSet(this, aSet);
	}

	/**
	 * @return
	 */
	public boolean isEnumeration ()
	{
		return descriptor.o_IsEnumeration(this);
	}

	/**
	 * @param potentialInstance
	 * @return
	 */
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
	public AvailObject valueType ()
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
	public AvailObject computeSuperkind ()
	{
		return descriptor.o_ComputeSuperkind(this);
	}

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	public boolean equalsCompiledCodeType (final AvailObject aCompiledCodeType)
	{
		return descriptor.o_EqualsCompiledCodeType(this, aCompiledCodeType);
	}

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	public boolean isSupertypeOfCompiledCodeType (
		final AvailObject aCompiledCodeType)
	{
		return descriptor.o_IsSupertypeOfCompiledCodeType(
			this,
			aCompiledCodeType);
	}

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	public AvailObject typeUnionOfCompiledCodeType (
		final AvailObject aCompiledCodeType)
	{
		return descriptor.o_TypeUnionOfCompiledCodeType(
 			this,
 			aCompiledCodeType);
	}

	/**
	 * @param key
	 * @param value
	 */
	public void setAtomProperty (
		final AvailObject key,
		final AvailObject value)
	{
		descriptor.o_SetAtomProperty(this, key, value);
	}

	/**
	 * @param key
	 * @return
	 */
	public AvailObject getAtomProperty (
		final AvailObject key)
	{
		return descriptor.o_GetAtomProperty(this, key);
	}

	/**
	 * @param anEnumerationType
	 * @return
	 */
	public boolean equalsEnumerationType (
		final AvailObject anEnumerationType)
	{
		return descriptor.o_EqualsEnumerationType(this, anEnumerationType);
	}

	/**
	 * @return
	 */
	public AvailObject readType ()
	{
		return descriptor.o_ReadType(this);
	}

	/**
	 * @return
	 */
	public AvailObject writeType ()
	{
		return descriptor.o_WriteType(this);
	}

	/**
	 * @param value
	 */
	public void versions (final AvailObject value)
	{
		descriptor.o_Versions(this, value);
	}

	/**
	 * @return
	 */
	public AvailObject versions ()
	{
		return descriptor.o_Versions(this);
	}

	/**
	 * @return
	 */
	public ParseNodeKind parseNodeKind ()
	{
		return descriptor.o_ParseNodeKind(this);
	}

	/**
	 * @return
	 */
	public boolean parseNodeKindIsUnder (
		final ParseNodeKind expectedParseNodeKind)
	{
		return descriptor.o_ParseNodeKindIsUnder(this, expectedParseNodeKind);
	}

	/**
	 * @return
	 */
	public boolean isRawPojo ()
	{
		return descriptor.o_IsRawPojo(this);
	}

	/**
	 * @param restrictionSignature
	 */
	public void addTypeRestriction (final AvailObject restrictionSignature)
	{
		descriptor.o_AddTypeRestriction(this, restrictionSignature);
	}

	/**
	 * @param restrictionSignature
	 */
	public void removeTypeRestriction (final AvailObject restrictionSignature)
	{
		descriptor.o_RemoveTypeRestriction(this, restrictionSignature);
	}

	/**
	 * @return
	 */
	public AvailObject typeRestrictions ()
	{
		return descriptor.o_TypeRestrictions(this);
	}

	/**
	 * @param tupleType
	 */
	public void addSealedArgumentsType (final AvailObject tupleType)
	{
		descriptor.o_AddSealedArgumentsType(this, tupleType);
	}

	/**
	 * @param tupleType
	 */
	public void removeSealedArgumentsType (final AvailObject tupleType)
	{
		descriptor.o_RemoveSealedArgumentsType(this, tupleType);
	}

	/**
	 * @return
	 */
	public AvailObject sealedArgumentsTypesTuple ()
	{
		return descriptor.o_SealedArgumentsTypesTuple(this);
	}

	/**
	 * @param methodNameAtom
	 * @param typeRestrictionFunction
	 */
	public void addTypeRestriction (
		final AvailObject methodNameAtom,
		final AvailObject typeRestrictionFunction)
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
	public void addConstantBinding (
		final AvailObject name,
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
	public void addVariableBinding (
		final AvailObject name,
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
	public boolean isMethodEmpty ()
	{
		return descriptor.o_IsMethodEmpty(this);
	}

	/**
	 * @return
	 */
	public boolean isPojoSelfType ()
	{
		return descriptor.o_IsPojoSelfType(this);
	}

	/**
	 * @return
	 */
	public AvailObject pojoSelfType ()
	{
		return descriptor.o_PojoSelfType(this);
	}

	/**
	 * @return
	 */
	public AvailObject javaClass ()
	{
		return descriptor.o_JavaClass(this);
	}

	/**
	 * @return
	 */
	public boolean isUnsignedShort ()
	{
		return descriptor.o_IsUnsignedShort(this);
	}

	/**
	 * @return
	 */
	public int extractUnsignedShort ()
	{
		return descriptor.o_ExtractUnsignedShort(this);
	}

	/**
	 * @return
	 */
	public boolean isFloat ()
	{
		return descriptor.o_IsFloat(this);
	}

	/**
	 * @return
	 */
	public boolean isDouble ()
	{
		return descriptor.o_IsDouble(this);
	}

	/**
	 * @return
	 */
	public AvailObject rawPojo ()
	{
		return descriptor.o_RawPojo(this);
	}

	/**
	 * @return
	 */
	public boolean isPojo ()
	{
		return descriptor.o_IsPojo(this);
	}

	/**
	 * @return
	 */
	public boolean isPojoType ()
	{
		return descriptor.o_IsPojoType(this);
	}

	/**
	 * @return
	 */
	public AvailObject upperBoundMap ()
	{
		return descriptor.o_UpperBoundMap(this);
	}

	/**
	 * @param aMap
	 */
	public void upperBoundMap (final AvailObject aMap)
	{
		descriptor.o_UpperBoundMap(this, aMap);
	}

	/**
	 * @param another
	 * @return
	 */
	public Order numericCompare (final AvailObject another)
	{
		return  descriptor.o_NumericCompare(this, another);
	}

	/**
	 * @param sign
	 * @return
	 */
	public Order numericCompareToInfinity (
		final Sign sign)
	{
		return descriptor.o_NumericCompareToInfinity(this, sign);
	}

	/**
	 * @param aDouble
	 * @return
	 */
	public Order numericCompareToDouble (final double aDouble)
	{
		return descriptor.o_NumericCompareToDouble(this, aDouble);
	}

	/**
	 * @param anInteger
	 * @return
	 */
	public Order numericCompareToInteger (final AvailObject anInteger)
	{
		return descriptor.o_NumericCompareToInteger(this, anInteger);
	}

	/**
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	public AvailObject addToDoubleCanDestroy (
		final AvailObject doubleObject,
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
	public AvailObject addToFloatCanDestroy (
		final AvailObject floatObject,
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
	public AvailObject subtractFromDoubleCanDestroy (
		final AvailObject doubleObject,
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
	public AvailObject subtractFromFloatCanDestroy (
		final AvailObject floatObject,
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
	public AvailObject multiplyByDoubleCanDestroy (
		final AvailObject doubleObject,
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
	public AvailObject multiplyByFloatCanDestroy (
		final AvailObject floatObject,
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
	public AvailObject divideIntoDoubleCanDestroy (
		final AvailObject doubleObject,
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
	public AvailObject divideIntoFloatCanDestroy (
		final AvailObject floatObject,
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
	public AvailObject lazyPrefilterMap ()
	{
		return descriptor.o_LazyPrefilterMap(this);
	}

	/**
	 * @return
	 */
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
	public AvailObject mapBinAtHashPutLevelCanDestroy (
		final AvailObject key,
		final int keyHash,
		final AvailObject value,
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
	AvailObject mapBinRemoveKeyHashCanDestroy (
		final AvailObject key,
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
	AvailObject mapBinKeyUnionKind ()
	{
		return descriptor.o_MapBinKeyUnionKind(this);
	}

	/**
	 * @return
	 */
	AvailObject mapBinValueUnionKind ()
	{
		return descriptor.o_MapBinValueUnionKind(this);
	}

	/**
	 * @return
	 */
	boolean isHashedMapBin ()
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
	AvailObject mapBinAtHash (
		final AvailObject key,
		final int keyHash)
	{
		return descriptor.o_MapBinAtHash(this, key, keyHash);
	}

	/**
	 * @return
	 */
	int mapBinKeysHash ()
	{
		return descriptor.o_MapBinKeysHash(this);
	}

	/**
	 * @return
	 */
	int mapBinValuesHash ()
	{
		return descriptor.o_MapBinValuesHash(this);
	}

	/**
	 * @return
	 */
	public AvailObject issuingModule ()
	{
		return descriptor.o_IssuingModule(this);
	}

	/**
	 * @return
	 */
	public boolean isPojoFusedType ()
	{
		return descriptor.o_IsPojoFusedType(this);
	}

	/**
	 * @param aPojoType
	 * @return
	 */
	public boolean isSupertypeOfPojoBottomType (
		final AvailObject aPojoType)
	{
		return descriptor.o_IsSupertypeOfPojoBottomType(this, aPojoType);
	}

	/**
	 * @return
	 */
	public boolean equalsPojoBottomType ()
	{
		return descriptor.o_EqualsPojoBottomType(this);
	}

	/**
	 * @return
	 */
	public AvailObject javaAncestors ()
	{
		return descriptor.o_JavaAncestors(this);
	}

	/**
	 * @param aFusedPojoType
	 * @return
	 */
	public AvailObject typeIntersectionOfPojoFusedType (
		final AvailObject aFusedPojoType)
	{
		return descriptor.o_TypeIntersectionOfPojoFusedType(
			this, aFusedPojoType);
	}

	/**
	 * @param anUnfusedPojoType
	 * @return
	 */
	public AvailObject typeIntersectionOfPojoUnfusedType (
		final AvailObject anUnfusedPojoType)
	{
		return descriptor.o_TypeIntersectionOfPojoUnfusedType(
			this, anUnfusedPojoType);
	}

	/**
	 * @param aFusedPojoType
	 * @return
	 */
	public AvailObject typeUnionOfPojoFusedType (
		final AvailObject aFusedPojoType)
	{
		return descriptor.o_TypeUnionOfPojoFusedType(
			this, aFusedPojoType);
	}

	/**
	 * @param anUnfusedPojoType
	 * @return
	 */
	public AvailObject typeUnionOfPojoUnfusedType (
		final AvailObject anUnfusedPojoType)
	{
		return descriptor.o_TypeUnionOfPojoUnfusedType(
			this, anUnfusedPojoType);
	}

	/**
	 * @return
	 */
	public boolean isPojoArrayType ()
	{
		return descriptor.o_IsPojoArrayType(this);
	}

	/**
	 * @param classHint
	 * @return
	 */
	public Object marshalToJava (final @Nullable Class<?> classHint)
	{
		return descriptor.o_MarshalToJava(this, classHint);
	}

	/**
	 * @return
	 */
	public AvailObject typeVariables ()
	{
		return descriptor.o_TypeVariables(this);
	}

	/**
	 * @param field
	 * @param receiver
	 * @return
	 */
	public boolean equalsPojoField (
		final AvailObject field,
		final AvailObject receiver)
	{
		return descriptor.o_EqualsPojoField(this, field, receiver);
	}

	/**
	 * @return
	 */
	boolean isSignedByte ()
	{
		return descriptor.o_IsSignedByte(this);
	}

	/**
	 * @return
	 */
	boolean isSignedShort ()
	{
		return descriptor.o_IsSignedShort(this);
	}

	/**
	 * @return
	 */
	byte extractSignedByte ()
	{
		return descriptor.o_ExtractSignedByte(this);
	}

	/**
	 * @return
	 */
	short extractSignedShort ()
	{
		return descriptor.o_ExtractSignedShort(this);
	}

	/**
	 * @param aRawPojo
	 * @return
	 */
	public boolean equalsEqualityRawPojo (final AvailObject aRawPojo)
	{
		return descriptor.o_EqualsEqualityRawPojo(this, aRawPojo);
	}

	/**
	 * @return
	 */
	public Object javaObject ()
	{
		return descriptor.o_JavaObject(this);
	}

	/**
	 * @return
	 */
	public BigInteger asBigInteger ()
	{
		return descriptor.o_AsBigInteger(this);
	}

	/**
	 * @param newElement
	 * @param canDestroy
	 * @return
	 */
	public AvailObject appendCanDestroy (
		final AvailObject newElement,
		final boolean canDestroy)
	{
		return descriptor.o_AppendCanDestroy(this, newElement, canDestroy);
	}

	/**
	 * @return
	 */
	public AvailObject lazyIncompleteCaseInsensitive ()
	{
		return descriptor.o_LazyIncompleteCaseInsensitive(this);
	}

	/**
	 * @return
	 */
	public AvailObject lowerCaseString ()
	{
		return descriptor.o_LowerCaseString(this);
	}

	/**
	 * @return
	 */
	public AvailObject instanceCount ()
	{
		return descriptor.o_InstanceCount(this);
	}

	/**
	 * @return
	 */
	public long totalInvocations ()
	{
		return descriptor.o_TotalInvocations(this);
	}

	/**
	 *
	 */
	public void tallyInvocation ()
	{
		descriptor.o_TallyInvocation(this);
	}

	/**
	 * @return
	 */
	public AvailObject fieldTypeTuple ()
	{
		return descriptor.o_FieldTypeTuple(this);
	}

	/**
	 * @return
	 */
	public AvailObject fieldTuple ()
	{
		return descriptor.o_FieldTuple(this);
	}

	/**
	 * @return
	 */
	public boolean isSystemModule ()
	{
		return descriptor.o_IsSystemModule(this);
	}

	/**
	 * @return
	 */
	public AvailObject literalType ()
	{
		return descriptor.o_LiteralType(this);
	}

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	public AvailObject typeIntersectionOfLiteralTokenType (
		final AvailObject aLiteralTokenType)
	{
		return descriptor.o_TypeIntersectionOfLiteralTokenType(
			this,
			aLiteralTokenType);
	}

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	public AvailObject typeUnionOfLiteralTokenType (
		final AvailObject aLiteralTokenType)
	{
		return descriptor.o_TypeUnionOfLiteralTokenType(
			this,
			aLiteralTokenType);
	}

	/**
	 * @return
	 */
	public boolean isLiteralTokenType ()
	{
		return descriptor.o_IsLiteralTokenType(this);
	}

	/**
	 * @return
	 */
	public boolean isLiteralToken ()
	{
		return descriptor.o_IsLiteralToken(this);
	}

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	public boolean equalsLiteralTokenType (
		final AvailObject aLiteralTokenType)
	{
		return descriptor.o_EqualsLiteralTokenType(this, aLiteralTokenType);
	}

	/**
	 * @param anObjectType
	 * @return
	 */
	public boolean equalsObjectType (
		final AvailObject anObjectType)
	{
		return descriptor.o_EqualsObjectType(this, anObjectType);
	}

	/**
	 * @param aToken
	 * @return
	 */
	public boolean equalsToken (
		final AvailObject aToken)
	{
		return descriptor.o_EqualsToken(this, aToken);
	}

	/**
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	public AvailObject bitwiseAnd (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor.o_BitwiseAnd(this, anInteger, canDestroy);
	}

	/**
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	public AvailObject bitwiseOr (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor.o_BitwiseOr(this, anInteger, canDestroy);
	}

	/**
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	public AvailObject bitwiseXor (
		final AvailObject anInteger,
		final boolean canDestroy)
	{
		return descriptor.o_BitwiseXor(this, anInteger, canDestroy);
	}

	/**
	 * @param methodName
	 * @param sealSignature
	 */
	public void addSeal (
		final AvailObject methodName,
		final AvailObject sealSignature)
	{
		descriptor.o_AddSeal(this, methodName, sealSignature);
	}

	/**
	 * @return
	 */
	public boolean isInstanceMeta ()
	{
		return descriptor.o_IsInstanceMeta(this);
	}

	/**
	 * @return
	 */
	public AvailObject instance ()
	{
		return descriptor.o_Instance(this);
	}

	/**
	 * @return
	 */
	public int allocateFromCounter ()
	{
		return descriptor.o_AllocateFromCounter(this);
	}

	/**
	 * @param methodName
	 */
	public void setMethodName (
		final AvailObject methodName)
	{
		descriptor.o_SetMethodName(this, methodName);
	}

	/**
	 * @return
	 */
	public int startingLineNumber ()
	{
		return descriptor.o_StartingLineNumber(this);
	}

	/**
	 * @return
	 */
	public AvailObject module ()
	{
		return descriptor.o_Module(this);
	}

	/**
	 * @return
	 */
	public AvailObject methodName ()
	{
		return descriptor.o_MethodName(this);
	}

	/**
	 * @param kind
	 * @return
	 */
	public boolean binElementsAreAllInstancesOfKind (
		final AvailObject kind)
	{
		return descriptor.o_BinElementsAreAllInstancesOfKind(this, kind);
	}

	/**
	 * @param kind
	 * @return
	 */
	public boolean setElementsAreAllInstancesOfKind (
		final AvailObject kind)
	{
		return descriptor.o_SetElementsAreAllInstancesOfKind(this, kind);
	}

	/**
	 * @return
	 */
	public MapDescriptor.MapIterable mapBinIterable ()
	{
		return descriptor.o_MapBinIterable(this);
	}

	/**
	 * @param anInt
	 * @return
	 */
	public boolean rangeIncludesInt (final int anInt)
	{
		return descriptor.o_RangeIncludesInt(this, anInt);
	}

	/**
	 * @param isSystemModule
	 */
	public void isSystemModule (
		final boolean isSystemModule)
	{
		descriptor.o_IsSystemModule(this, isSystemModule);
	}

	/**
	 * @return
	 */
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
	public AvailObject bitShiftLeftTruncatingToBits (
		final AvailObject shiftFactor,
		final AvailObject truncationBits,
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
	public SetIterator setBinIterator ()
	{
		return descriptor.o_SetBinIterator(this);
	}

	/**
	 * @param shiftFactor
	 * @param canDestroy
	 * @return
	 */
	public AvailObject bitShift (
		final AvailObject shiftFactor,
		final boolean canDestroy)
	{
		return descriptor.o_BitShift(this, shiftFactor, canDestroy);
	}

	/**
	 * @param aParseNode
	 * @return
	 */
	public boolean equalsParseNode (final AvailObject aParseNode)
	{
		return descriptor.o_EqualsParseNode(this, aParseNode);
	}

	/**
	 * @return
	 */
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
	public AvailObject definitionMethod ()
	{
		return descriptor.o_DefinitionMethod(this);
	}

	/**
	 * @return
	 */
	public AvailObject prefixFunctions ()
	{
		return descriptor.o_PrefixFunctions(this);
	}

	/**
	 * @param aByteArrayTuple
	 * @return
	 */
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
	public byte[] byteArray ()
	{
		return descriptor.o_ByteArray(this);
	}

	/**
	 * @return
	 */
	public boolean isByteArrayTuple ()
	{
		return descriptor.o_IsByteArrayTuple(this);
	}

	/**
	 * @param message
	 */
	public void flushForNewOrChangedBundleNamed (
		final AvailObject message)
	{
		descriptor.o_FlushForNewOrChangedBundleNamed(this, message);
	}

	/**
	 * @param critical
	 * @return
	 */
	public void lock (final Continuation0 critical)
	{
		descriptor.o_Lock(this, critical);
	}

	/**
	 * @return
	 */
	public AvailObject moduleName ()
	{
		return descriptor.o_ModuleName(this);
	}

	/**
	 * @return
	 */
	public AvailObject namesSet ()
	{
		return descriptor.o_NamesSet(this);
	}
}
