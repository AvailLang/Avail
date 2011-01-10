/**
 * interpreter/Interpreter.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.interpreter;

import static com.avail.descriptor.AvailObject.error;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.compiler.*;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.*;
import com.avail.utility.*;

/**
 * This is the abstraction for execution Avail code.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public abstract class Interpreter
{
	/** An {@link AvailRuntime}. */
	private final @NotNull AvailRuntime runtime;

	/**
	 * Answer the {@link AvailRuntime} that the {@linkplain Interpreter
	 * receiver} uses to locate and store Avail runtime elements.
	 *
	 * @return An {@link AvailRuntime}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public @NotNull AvailRuntime runtime ()
	{
		return runtime;
	}

	/**
	 * The {@linkplain ModuleDescriptor module} currently under {@linkplain
	 * AvailCompiler compilation}.
	 */
	private AvailObject module;

	/**
	 * Set the {@linkplain ModuleDescriptor module} context of the
	 * {@linkplain Interpreter interpreter}. This feature is used by the
	 * compiler to establish transaction boundaries for module parsing.
	 *
	 * @param module A {@linkplain ModuleDescriptor module}, or {@code
	 *               null} to disestablish the transaction.
	 */
	public void setModule (final @NotNull AvailObject module)
	{
		this.module = module;
	}

	/** The unresolved forward method declarations. */
	private @NotNull AvailObject pendingForwards = SetDescriptor.empty();

	/**
	 * A collection of bit flags indicating the reason for pausing the
	 * interpreter between nybblecodes.
	 */
	protected volatile int interruptRequestFlag;

	/**
	 * The {@link ProcessDescriptor} being executed by this interpreter.
	 */
	public AvailObject process;

	/**
	 * A place to store the result of a primitive when the primitive
	 * succeeds by returning {@link Result#SUCCESS}.
	 */
	public AvailObject primitiveResult;


	/**
	 * Construct a new {@link Interpreter}.
	 *
	 * @param runtime An {@link AvailRuntime}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	protected Interpreter (final @NotNull AvailRuntime runtime)
	{
		this.runtime = runtime;
	}


	/**
	 * Answer whether the specified primitive accepts the specified number of
	 * arguments.  Note that some primitives expect a variable number of
	 * arguments.
	 *
	 * @param primitiveNumber Which primitive.
	 * @param argCount The number of arguments that we should check is legal for
	 *                 this primitive.
	 * @return Whether the primitive accepts the specified number of arguments.
	 */
	public boolean primitiveAcceptsThisManyArguments (
		final short primitiveNumber,
		final int argCount)
	{
		final int expected = Primitive.byPrimitiveNumber(primitiveNumber).argCount();
		return expected == -1 || expected == argCount;
	}

	/**
	 * This is a forward declaration of a method. Insert an appropriately
	 * stubbed implementation in the module's method dictionary, and add it to
	 * the list of methods needing to be declared later in this module.
	 *
	 * @param methodName A {@linkplain CyclicTypeDescriptor method name}.
	 * @param bodySignature A {@linkplain MethodSignatureDescriptor method
	 *                      signature}.
	 */
	public void atAddForwardStubFor (
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject bodySignature)
	{
		methodName.makeImmutable();
		bodySignature.makeImmutable();
		//  Add the stubbed method implementation.
		final AvailObject newImp = ForwardSignatureDescriptor.mutable().create();
		newImp.bodySignature(bodySignature);
		newImp.makeImmutable();
		module.atAddMethodImplementation(methodName, newImp);
		final AvailObject imps = runtime.implementationSetFor(methodName);
		final AvailObject impsTuple = imps.implementationsTuple();
		for (int i = 1, _end1 = impsTuple.tupleSize(); i <= _end1; i++)
		{
			final AvailObject existingType =
				impsTuple.tupleAt(i).bodySignature();
			boolean same = true;
			for (int k = 1, _end2 = bodySignature.numArgs(); k <= _end2; k++)
			{
				if (!existingType.argTypeAt(k).equals(
					bodySignature.argTypeAt(k)))
				{
					same = false;
				}
			}
			if (same)
			{
				error(
					"Attempted to redefine (as forward) a method with the same"
					+ " argument types");
				return;
			}
			if (existingType.acceptsArgTypesFromClosureType(bodySignature))
			{
				if (!bodySignature.returnType().isSubtypeOf(
					existingType.returnType()))
				{
					error(
						"Specialized method should return at least as special a"
						+ " result as more general method");
					return;
				}
			}
		}
		imps.addImplementation(newImp);
		pendingForwards = pendingForwards.setWithElementCanDestroy(
			newImp,
			true);
		assert methodName.isCyclicType();
		final AvailObject message = methodName.name();
		final MessageSplitter splitter = new MessageSplitter(message);
		module.filteredBundleTree().includeBundle(
			MessageBundleDescriptor.newBundle(
				methodName,
				splitter.messageParts(),
				splitter.instructionsTuple()));
	}

	/**
	 * Add the method implementation.  The precedence rules can not change after
	 * the first implementation is encountered, so set them to 'no restrictions'
	 * if they're not set already.
	 *
	 * @param methodName The method's name, a {@link CyclicTypeDescriptor cyclic
	 *                   type}.
	 * @param method A {@link ClosureDescriptor method}.
	 */
	public void atAddMethodBody (
		final AvailObject methodName,
		final AvailObject method)
	{
		final short numArgs = method.type().numArgs();
		final AvailObject returnsBlock =
			ClosureDescriptor.createStubForNumArgsConstantResult(
				numArgs,
				method.type().returnType());
		final AvailObject requiresBlock =
			ClosureDescriptor.createStubForNumArgsConstantResult(
				numArgs,
				BooleanDescriptor.objectFrom(true));
		atAddMethodBodyRequiresBlockReturnsBlock(
			methodName,
			method,
			requiresBlock,
			returnsBlock);
	}

	/**
	 * Add the method implementation. The precedence rules can change at any
	 * time. The <em>requires block</em> is run at compile time to ensure that
	 * the method is being used in an appropriate way. The <em>returns
	 * block</em> lets us home in on the type returned by a general method by
	 * transforming the call-site-specific argument type information into a
	 * return type for the method.
	 *
	 * @param methodName A {@linkplain CyclicTypeDescriptor method name}.
	 * @param bodyBlock The {@linkplain ClosureDescriptor body block}.
	 * @param requiresBlock The {@linkplain ClosureDescriptor requires block}.
	 * @param returnsBlock The {@linkplain ClosureDescriptor returns block}.
	 */
	public void atAddMethodBodyRequiresBlockReturnsBlock (
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject bodyBlock,
		final @NotNull AvailObject requiresBlock,
		final @NotNull AvailObject returnsBlock)
	{
		assert methodName.isCyclicType();
		assert bodyBlock.isClosure();
		assert requiresBlock.isClosure();
		assert returnsBlock.isClosure();

		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfArguments();
		assert bodyBlock.code().numArgs() == numArgs
		: "Wrong number of arguments in method definition";
		assert requiresBlock.code().numArgs() == numArgs
		: "Wrong number of arguments in method type verifier";
		assert returnsBlock.code().numArgs() == numArgs
		: "Wrong number of arguments in method result type generator";
		//  Make it so we can safely hold onto these things in the VM
		methodName.makeImmutable();
		bodyBlock.makeImmutable();
		requiresBlock.makeImmutable();
		returnsBlock.makeImmutable();
		//  Add the method implementation.
		final AvailObject newImp = MethodSignatureDescriptor.mutable().create();
		newImp.bodyBlockRequiresBlockReturnsBlock(
			bodyBlock,
			requiresBlock,
			returnsBlock);
		newImp.makeImmutable();
		module.atAddMethodImplementation(methodName, newImp);
		final AvailObject imps = runtime.implementationSetFor(methodName);
		final AvailObject bodySignature = bodyBlock.type();
		AvailObject forward = null;
		final AvailObject impsTuple = imps.implementationsTuple();
		for (final AvailObject existingImp : impsTuple)
		{
			final AvailObject existingType = existingImp.bodySignature();
			boolean same = true;
			for (int k = 1, _end2 = bodySignature.numArgs(); k <= _end2; k++)
			{
				if (!existingType.argTypeAt(k).equals(
					bodySignature.argTypeAt(k)))
				{
					same = false;
				}
			}
			if (same)
			{
				if (existingImp.isForward())
				{
					forward = existingImp;
				}
				else
				{
					error(
						"Attempted to redefine method with same argument "
						+ "types");
					return;
				}
			}
			if (existingImp.bodySignature().acceptsArgTypesFromClosureType(
				bodySignature))
			{
				if (!bodySignature.returnType().isSubtypeOf(
					existingImp.bodySignature().returnType()))
				{
					error(
						"Specialized method should return at least as special "
						+ "a result as more general method");
					return;
				}
			}
		}
		if (forward != null)
		{
			resolvedForwardWithName(forward, methodName);
		}
		imps.addImplementation(newImp);
		assert methodName.isCyclicType();
		module.filteredBundleTree().includeBundle(
			MessageBundleDescriptor.newBundle(
				methodName,
				splitter.messageParts(),
				splitter.instructionsTuple()));
	}

	/**
	 * Add the abstract method signature. A class is considered abstract if
	 * there are any abstract methods that haven't been overridden with
	 * implementations for it. The <em>requires block</em> is called at compile
	 * time at each call site (i.e., link time) to ensure the method is being
	 * used in an appropriate way. The <em>returns block</em> lets us home in on
	 * the type returned by a general method by transforming the
	 * call-site-specific argument type information into a return type for the
	 * method.
	 *
	 * @param methodName A {@linkplain CyclicTypeDescriptor method name}.
	 * @param bodySignature The {@linkplain MethodSignatureDescriptor method
	 *                      signature}.
	 * @param requiresBlock The {@linkplain ClosureDescriptor requires block}.
	 * @param returnsBlock The {@linkplain ClosureDescriptor returns block}.
	 */
	public void atDeclareAbstractSignatureRequiresBlockReturnsBlock (
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject bodySignature,
		final @NotNull AvailObject requiresBlock,
		final @NotNull AvailObject returnsBlock)
	{
		assert methodName.isCyclicType();
		assert requiresBlock.isClosure();
		assert returnsBlock.isClosure();

		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfArguments();
		assert bodySignature.numArgs() == numArgs
		: "Wrong number of arguments in abstract method signature";
		assert requiresBlock.code().numArgs() == numArgs
		: "Wrong number of arguments in abstract method type verifier";
		assert returnsBlock.code().numArgs() == numArgs
		: "Wrong number of arguments in abstract method result type "
			+ "specializer";
		//  Make it so we can safely hold onto these things in the VM
		methodName.makeImmutable();
		bodySignature.makeImmutable();
		requiresBlock.makeImmutable();
		returnsBlock.makeImmutable();
		//  Add the method implementation.
		final AvailObject newImp = AbstractSignatureDescriptor.mutable().create();
		newImp.bodySignatureRequiresBlockReturnsBlock(
			bodySignature,
			requiresBlock,
			returnsBlock);
		newImp.makeImmutable();
		module.atAddMethodImplementation(methodName, newImp);
		final AvailObject imps = runtime.implementationSetFor(methodName);
		AvailObject forward = null;
		final AvailObject impsTuple = imps.implementationsTuple();
		for (int i = 1, _end1 = impsTuple.tupleSize(); i <= _end1; i++)
		{
			final AvailObject existingImp = impsTuple.tupleAt(i);
			final AvailObject existingType = existingImp.bodySignature();
			boolean same = true;
			for (int k = 1, _end2 = bodySignature.numArgs(); k <= _end2; k++)
			{
				if (!existingType.argTypeAt(k).equals(
					bodySignature.argTypeAt(k)))
				{
					same = false;
				}
			}
			if (same)
			{
				if (existingImp.isForward())
				{
					forward = existingImp;
				}
				else
				{
					error(
						"Attempted to redefine method with same argument "
						+ "types");
					return;
				}
			}
			if (existingImp.bodySignature().acceptsArgTypesFromClosureType(
				bodySignature))
			{
				if (!bodySignature.returnType().isSubtypeOf(
					existingImp.bodySignature().returnType()))
				{
					error(
						"Specialized method should return at least as special "
						+ "a result as more general method");
					return;
				}
			}
		}
		if (forward != null)
		{
			resolvedForwardWithName(forward, methodName);
		}
		imps.addImplementation(newImp);
		module.filteredBundleTree().includeBundle(
			MessageBundleDescriptor.newBundle(
				methodName,
				splitter.messageParts(),
				splitter.instructionsTuple()));
	}

	/**
	 * The modularity scheme should prevent all intermodular method conflicts.
	 * Precedence is specified as an array of message sets that are not allowed
	 * to be messages generating the arguments of this message.  For example,
	 * <{'_+_'} , {'_+_' , '_*_'}> for the '_*_' operator makes * bind tighter
	 * than + and also groups multiple *'s left-to-right.
	 *
	 * @param methodName A {@linkplain CyclicTypeDescriptor method name}.
	 * @param illegalArgMsgs The {@linkplain TupleDescriptor restrictions}.
	 */
	public void atDisallowArgumentMessages (
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject illegalArgMsgs)
	{
		methodName.makeImmutable();
		//  So we can safely hold onto it in the VM
		illegalArgMsgs.makeImmutable();
		//  So we can safely hold this data in the VM
		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfArguments();
		assert numArgs == illegalArgMsgs.tupleSize()
			: "Wrong number of entries in restriction tuple.";
		assert methodName.isCyclicType();
		//  Fix precedence.
		final AvailObject bundle =
			module.filteredBundleTree().includeBundle(
				MessageBundleDescriptor.newBundle(
					methodName,
					splitter.messageParts(),
					splitter.instructionsTuple()));
		bundle.addRestrictions(illegalArgMsgs);
		module.atAddMessageRestrictions(methodName, illegalArgMsgs);
	}

	/**
	 * Create the two-argument defining method. The first parameter of the
	 * method is the name, the second parameter is the {@linkplain
	 * ClosureDescriptor block}.
	 *
	 * @param defineMethodName The name of the defining method.
	 */
	public void bootstrapDefiningMethod (
		final @NotNull String defineMethodName)
	{
		assert module != null;
		final L1InstructionWriter writer = new L1InstructionWriter();
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(VoidDescriptor.voidObject())));
		writer.argumentTypes(
			TupleTypeDescriptor.stringTupleType(), Types.closure.object());
		writer.primitiveNumber(
			Primitive.prim253_SimpleMethodDeclaration_string_block
			.primitiveNumber);
		writer.returnType(Types.voidType.object());
		final AvailObject newClosure =
			ClosureDescriptor.create(
				writer.compiledCode(),
				TupleDescriptor.empty());
		newClosure.makeImmutable();
		final AvailObject nameTuple =
			ByteStringDescriptor.from(
				defineMethodName);
		final AvailObject realName = CyclicTypeDescriptor.create(
			nameTuple);
		module.atNameAdd(nameTuple, realName);
		module.atNewNamePut(nameTuple, realName);
		atAddMethodBody(realName, newClosure);
	}

	/**
	 * Create the one-argument {@linkplain AvailRuntime#specialObject(int)
	 * special object} method. The parameter is the {@linkplain
	 * IntegerDescriptor ordinal} of the special object.
	 *
	 * @param specialObjectName
	 *        The name of the {@linkplain AvailRuntime#specialObject(int)
	 *        special object} method.
	 */
	public void bootstrapSpecialObject (
		final @NotNull String specialObjectName)
	{
		//  Define the special object method.

		assert module != null;
		final AvailObject naturalNumbers =
			IntegerRangeTypeDescriptor.create(
				IntegerDescriptor.one(),
				true,
				InfinityDescriptor.positiveInfinity(),
				false);
		AvailObject newClosure;
		final L1InstructionWriter writer = new L1InstructionWriter();
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(VoidDescriptor.voidObject())));
		writer.argumentTypes(naturalNumbers);
		writer.primitiveNumber(
			Primitive.prim240_SpecialObject_index.primitiveNumber);
		writer.returnType(Types.all.object());
		newClosure = ClosureDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty());
		newClosure.makeImmutable();
		final AvailObject nameTuple =
			ByteStringDescriptor.from(
				specialObjectName);
		final AvailObject realName = CyclicTypeDescriptor.create(
			nameTuple);
		module.atNameAdd(nameTuple, realName);
		module.atNewNamePut(nameTuple, realName);
		atAddMethodBody(realName, newClosure);
	}

	/**
	 * Ensure that all forward declarations have been resolved.
	 */
	public void checkUnresolvedForwards ()
	{
		if (pendingForwards.setSize() != 0)
		{
			error(
				"Some forward declarations were not resolved within this "
				+ "module.");
		}
	}

	/**
	 * Answer the map whose sole token-component is firstPiece.  The map is from
	 * message (cyclicType) to messageBundle.  Filter selectors based on the
	 * visibility of names in the current module.
	 *
	 * @param firstPiece An Avail {@link ByteStringDescriptor string}.
	 * @return A map from
	 */
	public AvailObject completeBundlesStartingWith (
		final AvailObject firstPiece)
	{

		final AvailObject all = module.filteredBundleTree().incomplete();
		if (!all.hasKey(firstPiece))
		{
			return MapDescriptor.empty();
		}
		return all.mapAt(firstPiece).complete();
	}

	/**
	 * Answer the map whose first (but not only) token-component is firstPiece.
	 * The map is from the second piece to bundle tree.  Filter selectors based
	 * on the visibility of names in the current module.
	 *
	 * @param firstPiece The first Avail {@link ByteStringDescriptor string}
	 *                   token by which to filter messages.
	 * @return A map from
	 */
	public AvailObject incompleteBundlesStartingWith (
		final AvailObject firstPiece)
	{
		final AvailObject all = module.filteredBundleTree().incomplete();
		if (!all.hasKey(firstPiece))
		{
			return MapDescriptor.empty();
		}
		return all.mapAt(firstPiece).incomplete();
	}

	/**
	 * Answer the current module's filtered bundle tree used for parsing.  It
	 * only includes bundles visible in the current module.
	 *
	 * @return The filtered root bundle tree.
	 */
	public AvailObject rootBundleTree ()
	{
		return module.filteredBundleTree();
	}

	/**
	 * Look up the given {@linkplain TupleDescriptor string} in the current
	 * {@linkplain ModuleDescriptor module}'s namespace. Answer the
	 * {@linkplain CyclicTypeDescriptor true name} associated with the string,
	 * creating the true name if necessary. A local true name always hides other
	 * true names.
	 *
	 * @param stringName An Avail {@linkplain TupleDescriptor string}.
	 * @return A {@linkplain CyclicTypeDescriptor true name}.
	 */
	public @NotNull AvailObject lookupName (
		final @NotNull AvailObject stringName)
	{
		assert stringName.isString();
		//  Check if it's already defined somewhere...
		final AvailObject who = module.trueNamesForStringName(stringName);
		AvailObject trueName;
		if (who.setSize() == 0)
		{
			trueName = CyclicTypeDescriptor.create(stringName);
			trueName.makeImmutable();
			module.atPrivateNameAdd(stringName, trueName);
			return trueName;
		}
		if (who.setSize() == 1)
		{
			return who.asTuple().tupleAt(1);
		}
		error(
			"There are multiple true method names that this name could "
			+ "represent.");
		return VoidDescriptor.voidObject();
	}

	/**
	 * Return the current {@link ProcessDescriptor process}.
	 *
	 * @return The current executing process.
	 */
	public AvailObject process ()
	{
		return process;
	}

	/**
	 * The given forward is in the process of being resolved. A real
	 * implementation is about to be added to the method tables, so remove the
	 * forward now.
	 *
	 * @param aForward A forward declaration.
	 * @param methodName A {@linkplain CyclicTypeDescriptor method name}.
	 */
	public void resolvedForwardWithName (
		final @NotNull AvailObject aForward,
		final @NotNull AvailObject methodName)
	{
		assert methodName.isCyclicType();

		if (!runtime.hasMethodsAt(methodName))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		final AvailObject impSet = runtime.methodsAt(methodName);
		if (!pendingForwards.hasElement(aForward))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		if (!impSet.includes(aForward))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		pendingForwards = pendingForwards.setWithoutElementCanDestroy(
			aForward, true);
		impSet.removeImplementation(aForward);
		module.resolvedForwardWithName(aForward, methodName);
	}

	/**
	 * Unbind the specified implementation from the {@linkplain
	 * CyclicTypeDescriptor selector}.
	 *
	 * @param methodName A {@linkplain CyclicTypeDescriptor selector}.
	 * @param implementation An implementation.
	 */
	public void removeMethodNamedImplementation (
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject implementation)
	{
		assert methodName.isCyclicType();

		if (implementation.isForward())
		{
			pendingForwards = pendingForwards.setWithoutElementCanDestroy(
				implementation,
				true);
		}

		runtime.removeMethod(methodName, implementation);
	}

	/**
	 * Does the {@linkplain Interpreter interpreter} provide a {@linkplain
	 * Primitive primitive} with the specified ordinal?
	 *
	 * @param ordinal An ordinal.
	 * @return {@code true} if there is a {@linkplain Primitive primitive} with
	 *         the specified ordinal, {@code false} otherwise.
	 */
	public boolean supportsPrimitive (final short ordinal)
	{
		return Primitive.byPrimitiveNumber(ordinal) != null;
	}

	/**
	 * Attempt to run the requires clauses applicable to this message send.
	 *
	 * @param methodName A {@linkplain CyclicTypeDescriptor method name}.
	 * @param argTypes The {@linkplain TypeDescriptor types} of the arguments
	 *                 of the message send.
	 * @return A message {@linkPlain String} or null if successful.
	 */
	public String validateRequiresClauses (
		final @NotNull AvailObject methodName,
		final @NotNull List<AvailObject> argTypes)
	{
		assert methodName.isCyclicType();
		final AvailObject implementations = runtime.methodsAt(methodName);
		final List<AvailObject> matching =
			implementations.filterByTypes(argTypes);
		if (matching.size() == 0)
		{
			return "matching implementations for method " + methodName;
		}
		for (final AvailObject imp : matching)
		{
			if (!imp.isValidForArgumentTypesInterpreter(argTypes, this))
			{
				return "message send of " + methodName
					+ " to pass its requires clause";
			}
		}
		return null;
	}

	/**
	 * Answers the return type. Fails if no applicable implementation (or more
	 * than one).
	 *
	 * @param methodName A {@linkplain CyclicTypeDescriptor method name}.
	 * @param argTypes The {@linkplain TypeDescriptor types} of the arguments
	 *                 of the message send.
	 * @param failBlock A {@linkplain Continuation1 continuation} to invoke on
	 *                  failure.
	 * @return The return {@linkplain TypeDescriptor type}.
	 */
	public @NotNull AvailObject validateSendArgumentTypesIfFail (
		final @NotNull AvailObject methodName,
		final @NotNull List<AvailObject> argTypes,
		final @NotNull Continuation1<Generator<String>> failBlock)
	{
		final AvailObject impSet = runtime.methodsAt(methodName);
		return impSet.validateArgumentTypesInterpreterIfFail(
			argTypes,
			this,
			failBlock);
	}

	public class ExecutionMode
	{
		// Process is not running in debug mode.
		public static final int noDebug = 0x0000;

		// Interrupt between *nybblecodes*, and avoid optimized code.
		public static final int singleStep = 0x0001;
	}

	public class ExecutionState
	{
		// Process is running or waiting for another process to yield.
		public static final int running = 0x0001;

		// Process has been suspended (always on a semaphore)
		public static final int suspended = 0x0002;

		// Process has terminated.  This state is final.
		public static final int terminated = 0x0004;
	}

	public class InterruptRequestFlag
	{
		// No interrupt is pending.
		public static final int noInterrupt = 0x0000;

		// Out of gas.
		public static final int outOfGas = 0x0001;

		// Another process should run instead.
		public static final int higherPriorityReady = 0x0002;
	}

	{
		process = ProcessDescriptor.mutable().create();
		process.priority(50);
		process.continuation(VoidDescriptor.voidObject());
		process.executionMode(ExecutionMode.noDebug);
		process.executionState(ExecutionState.running);
		process.interruptRequestFlag(InterruptRequestFlag.noInterrupt);
		process.breakpointBlock(VoidDescriptor.voidObject());
		process.processGlobals(MapDescriptor.empty());
	}

	/**
	 * Set the resulting value of a primitive invocation.  This value will only
	 * be used if the primitive subsequently returns {@link
	 * Result#SUCCESS}.
	 *
	 * @param result The {@link AvailObject} that a primitive has produced.
	 */
	public void primitiveResult (final AvailObject result)
	{
		primitiveResult = result;
	}


	/**
	 * Answer the result that a primitive invocation has produced.
	 *
	 * @return The result that was {@link #primitiveResult(AvailObject)
	 * recorded} during primitive execution.
	 */
	public AvailObject primitiveResult()
	{
		return primitiveResult;
	}


	/**
	 * Invoke an Avail primitive.  The primitive number and arguments are
	 * passed.  If the primitive fails, return primitiveFailed immediately.  If
	 * the primitive causes the continuation to change (e.g., through block
	 * invocation, continuation restart, exception throwing, etc), answer
	 * continuationChanged.  Otherwise the primitive succeeded, and we simply
	 * capture the resulting value with {@link
	 * Interpreter#primitiveResult(AvailObject)} and return {@link
	 * Primitive.Result#SUCCESS}.
	 *
	 * @param primitiveNumber The number of the primitive to invoke.
	 * @param args The list of arguments to supply to the primitive.
	 * @return The resulting status of the primitive attempt.
	 */
	public Result attemptPrimitive (
		final short primitiveNumber,
		final List<AvailObject> args)
	{
		return Primitive.byPrimitiveNumber(primitiveNumber).attempt(args, this);
	}

	public abstract Result invokeClosureArguments (
		AvailObject aClosure,
		List<AvailObject> args);

	public abstract void prepareToExecuteContinuation (
		AvailObject continuation);

	public abstract Result searchForExceptionHandler (
		AvailObject exceptionValue,
		List<AvailObject> args);

	public abstract void invokeWithoutPrimitiveClosureArguments (
		AvailObject aClosure,
		List<AvailObject> args);

	public abstract AvailObject runClosureArguments (
		AvailObject aClosure,
		List<AvailObject> arguments);

	@Deprecated
	Result callBackSmalltalkPrimitive (
		final short primitiveNumber,
		final List<AvailObject> args)
	{
		//TODO: [MvG] Phase this out without ever implementing it.
		error("Can't call back to Smalltalk -- not supported.");
		return Result.FAILURE;
	}
}
