/**
 * Interpreter.java
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
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.Primitive.Result.*;
import static com.avail.exceptions.AvailErrorCode.*;
import java.util.*;
import java.util.logging.*;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.compiler.*;
import com.avail.descriptor.*;
import com.avail.descriptor.ProcessDescriptor.ExecutionState;
import com.avail.exceptions.*;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.*;

/**
 * This is the abstraction for execution Avail code.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public abstract class Interpreter
{
	/** A {@linkplain Logger logger}. */
	protected static final @NotNull Logger logger =
		Logger.getLogger(Interpreter.class.getCanonicalName());

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
	 * AbstractAvailCompiler compilation}.
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
	 * A place to store the primitive {@linkplain CompiledCodeDescriptor
	 * compiled code} being attempted.  That allows {@linkplain
	 * Primitive#prim340_PushConstant} to get to the first literal
	 * in order to return it from the primitive.
	 */
	private AvailObject primitiveCompiledCodeBeingAttempted;

	/**
	 * Answer the primitive {@linkplain CompiledCodeDescriptor compiled code}
	 * that is currently being attempted.  That allows {@linkplain
	 * Primitive#prim340_PushConstant} to get to the first literal
	 * in order to return it from the primitive.
	 *
	 * @return
	 *            The {@linkplain CompiledCodeDescriptor compiled code} whose
	 *            primitive is being attempted.
	 */
	public final AvailObject primitiveCompiledCodeBeingAttempted ()
	{
		return primitiveCompiledCodeBeingAttempted;
	}



	/**
	 * Exit the current {@linkplain ProcessDescriptor process} with the
	 * specified result.
	 *
	 * @param finalObject
	 *            The {@link AvailObject} that is the final result of running
	 *            the {@linkplain ProcessDescriptor process}.
	 *
	 */
	public abstract void exitProcessWith (final AvailObject finalObject);


	/**
	 * Construct a new {@link Interpreter}.
	 *
	 * @param runtime An {@link AvailRuntime}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	protected Interpreter (final @NotNull AvailRuntime runtime)
	{
		this.runtime = runtime;

		// Also initialize the process field.
		process = ProcessDescriptor.mutable().create();
		process.name(StringDescriptor.from(String.format(
			"unnamed, creation time = %d, hash = %d",
			System.currentTimeMillis(),
			process.hash())));
		process.priority(IntegerDescriptor.fromUnsignedByte((short)50));
		process.continuation(NullDescriptor.nullObject());
		process.executionState(ExecutionState.RUNNING);
		process.interruptRequestFlag(0);
		process.breakpointBlock(NullDescriptor.nullObject());
		process.processGlobals(MapDescriptor.empty());
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
		final int primitiveNumber,
		final int argCount)
	{
		final int expected =
			Primitive.byPrimitiveNumber(primitiveNumber).argCount();
		return expected == -1 || expected == argCount;
	}


	/**
	 * Add the method implementation. The precedence rules can change at any
	 * time. The <em>requires block</em> is run at compile time to ensure that
	 * the method is being used in an appropriate way. The <em>returns
	 * block</em> lets us home in on the type returned by a general method by
	 * transforming the call-site-specific argument type information into a
	 * return type for the method.
	 *
	 * @param methodName A {@linkplain AtomDescriptor method name}.
	 * @param bodyBlock The {@linkplain FunctionDescriptor body block}.
	 * @throws SignatureException If the signature is invalid.
	 */
	public void addMethodBody (
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject bodyBlock)
	throws SignatureException
	{
		assert methodName.isAtom();
		assert bodyBlock.isFunction();

		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfArguments();
		assert bodyBlock.code().numArgs() == numArgs
			: "Wrong number of arguments in method definition";
		//  Make it so we can safely hold onto these things in the VM
		methodName.makeImmutable();
		bodyBlock.makeImmutable();
		//  Add the method implementation.
		final AvailObject newImp = MethodImplementationDescriptor.create(
			bodyBlock);
		module.addMethodImplementation(methodName, newImp);
		final AvailObject imps = runtime.methodFor(methodName);
		final AvailObject bodySignature = bodyBlock.kind();
		AvailObject forward = null;
		final AvailObject impsTuple = imps.implementationsTuple();
		for (final AvailObject existingImp : impsTuple)
		{
			final AvailObject existingType = existingImp.bodySignature();
			final boolean same = existingType.argsTupleType().equals(
				bodySignature.argsTupleType());
			if (same)
			{
				if (existingImp.isForward())
				{
					if (existingType.returnType().equals(
						bodySignature.returnType()))
					{
						forward = existingImp;
					}
					else
					{
						throw new SignatureException(
							E_METHOD_RETURN_TYPE_NOT_AS_FORWARD_DECLARED);
					}
				}
				else
				{
					throw new SignatureException(
						E_REDEFINED_WITH_SAME_ARGUMENT_TYPES);
				}
			}
			if (existingType.acceptsArgTypesFromFunctionType(bodySignature))
			{
				if (!bodySignature.returnType().isSubtypeOf(
					existingType.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
			if (bodySignature.acceptsArgTypesFromFunctionType(existingType))
			{
				if (!existingType.returnType().isSubtypeOf(
					bodySignature.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
		}
		if (forward != null)
		{
			resolvedForwardWithName(forward, methodName);
		}
		imps.addImplementation(newImp);
		assert methodName.isAtom();
		module.filteredBundleTree().includeBundle(
			MessageBundleDescriptor.newBundle(methodName));
	}


	/**
	 * This is a forward declaration of a method. Insert an appropriately
	 * stubbed implementation in the module's method dictionary, and add it to
	 * the list of methods needing to be declared later in this module.
	 *
	 * @param methodName A {@linkplain AtomDescriptor method name}.
	 * @param bodySignature A {@linkplain MethodImplementationDescriptor method
	 *                      signature}.
	 * @throws SignatureException If the signature is malformed.
	 */
	public void addForwardStub (
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject bodySignature)
	throws SignatureException
	{
		methodName.makeImmutable();
		bodySignature.makeImmutable();
		//  Add the stubbed method implementation.
		final AvailObject newImp = ForwardDeclarationDescriptor.create(
			bodySignature);
		module.addMethodImplementation(methodName, newImp);
		final AvailObject imps = runtime.methodFor(methodName);
		final AvailObject impsTuple = imps.implementationsTuple();
		for (int i = 1, end = impsTuple.tupleSize(); i <= end; i++)
		{
			final AvailObject existingType =
				impsTuple.tupleAt(i).bodySignature();
			final boolean same = existingType.argsTupleType().equals(
				bodySignature.argsTupleType());
			if (same)
			{
				throw new SignatureException(
					E_REDEFINED_WITH_SAME_ARGUMENT_TYPES);
			}
			if (existingType.acceptsArgTypesFromFunctionType(bodySignature))
			{
				if (!bodySignature.returnType().isSubtypeOf(
					existingType.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
			if (bodySignature.acceptsArgTypesFromFunctionType(existingType))
			{
				if (!existingType.returnType().isSubtypeOf(
					bodySignature.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
		}
		imps.addImplementation(newImp);
		pendingForwards = pendingForwards.setWithElementCanDestroy(
			newImp,
			true);
		assert methodName.isAtom();
		module.filteredBundleTree().includeBundle(
			MessageBundleDescriptor.newBundle(methodName));
	}

	/**
	 * Add the abstract method signature. A class is considered abstract if
	 * there are any abstract methods that haven't been overridden with
	 * implementations for it.
	 *
	 * @param methodName
	 *            A {@linkplain AtomDescriptor method name}.
	 * @param bodySignature
	 *            The {@linkplain MethodImplementationDescriptor method signature}.
	 * @throws SignatureException
	 *            If the signature is malformed.
	 */
	public void addAbstractSignature (
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject bodySignature)
	throws SignatureException
	{
		assert methodName.isAtom();

		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfArguments();
		final AvailObject bodyArgsSizes =
			bodySignature.argsTupleType().sizeRange();
		assert bodyArgsSizes.lowerBound().equals(
				IntegerDescriptor.fromInt(numArgs))
			: "Wrong number of arguments in abstract method signature";
		assert bodyArgsSizes.upperBound().equals(
				IntegerDescriptor.fromInt(numArgs))
			: "Wrong number of arguments in abstract method signature";
		//  Make it so we can safely hold onto these things in the VM
		methodName.makeImmutable();
		bodySignature.makeImmutable();
		//  Add the method implementation.
		final AvailObject newImp = AbstractDeclarationDescriptor.create(
			bodySignature);
		module.addMethodImplementation(methodName, newImp);
		final AvailObject imps = runtime.methodFor(methodName);
		AvailObject forward = null;
		for (final AvailObject existingImp : imps.implementationsTuple())
		{
			final AvailObject existingType = existingImp.bodySignature();
			final boolean same = existingType.argsTupleType().equals(
				bodySignature.argsTupleType());
			if (same)
			{
				if (existingImp.isForward())
				{
					forward = existingImp;
				}
				else
				{
					throw new SignatureException(
						E_REDEFINED_WITH_SAME_ARGUMENT_TYPES);
				}
			}
			if (existingType.acceptsArgTypesFromFunctionType(bodySignature))
			{
				if (!bodySignature.returnType().isSubtypeOf(
					existingType.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
			if (bodySignature.acceptsArgTypesFromFunctionType(existingType))
			{
				if (!existingType.returnType().isSubtypeOf(
					bodySignature.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
		}
		if (forward != null)
		{
			resolvedForwardWithName(forward, methodName);
		}
		imps.addImplementation(newImp);
		module.filteredBundleTree().includeBundle(
			MessageBundleDescriptor.newBundle(methodName));
	}

	/**
	 * Add the macro implementation.  The precedence rules can not change after
	 * the first implementation is encountered, so set them to 'no restrictions'
	 * if they're not set already.
	 *
	 * @param methodName
	 *            The macro's name, an {@linkplain AtomDescriptor atom}.
	 * @param macroBody
	 *            A {@linkplain FunctionDescriptor function} that manipulates parse
	 *            nodes.
	 * @throws SignatureException if the macro signature is invalid.
	 */
	public void addMacroBody (
		final AvailObject methodName,
		final AvailObject macroBody)
	throws SignatureException
	{
		assert methodName.isAtom();
		assert macroBody.isFunction();

		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfArguments();
		assert macroBody.code().numArgs() == numArgs
			: "Wrong number of arguments in macro definition";
		//  Make it so we can safely hold onto these things in the VM
		methodName.makeImmutable();
		macroBody.makeImmutable();
		//  Add the macro implementation.
		final AvailObject newImp =
			MacroImplementationDescriptor.create(macroBody);
		module.addMethodImplementation(methodName, newImp);
		final AvailObject imps = runtime.methodFor(methodName);
		final AvailObject macroBodyType = macroBody.kind();
		for (final AvailObject existingImp : imps.implementationsTuple())
		{
			final AvailObject existingType = existingImp.bodySignature();
			final boolean same = existingType.argsTupleType().equals(
				macroBodyType.argsTupleType());
			if (same)
			{
				throw new SignatureException(
					E_REDEFINED_WITH_SAME_ARGUMENT_TYPES);
			}
			if (existingType.acceptsArgTypesFromFunctionType(macroBodyType))
			{
				if (!macroBodyType.returnType().isSubtypeOf(
					existingType.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
			if (macroBodyType.acceptsArgTypesFromFunctionType(existingType))
			{
				if (!existingType.returnType().isSubtypeOf(
					macroBodyType.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
		}
		imps.addImplementation(newImp);
		module.filteredBundleTree().includeBundle(
			MessageBundleDescriptor.newBundle(methodName));
	}



	/**
	 * Add a type restriction to the method associated with the
	 * given method name.
	 *
	 * @param methodName
	 *            The method name, an {@linkplain AtomDescriptor atom}.
	 * @param typeRestrictionFunction
	 *            A {@linkplain FunctionDescriptor function} that validates the
	 *            static types of arguments at call sites.
	 * @throws SignatureException if the signature is invalid.
	 */
	public void addTypeRestriction (
		final AvailObject methodName,
		final AvailObject typeRestrictionFunction)
	throws SignatureException
	{
		assert methodName.isAtom();
		assert typeRestrictionFunction.isFunction();

		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfArguments();
		if (typeRestrictionFunction.code().numArgs() != numArgs)
		{
			throw new SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		//  Make it so we can safely hold onto these things in the VM
		methodName.makeImmutable();
		typeRestrictionFunction.makeImmutable();
		runtime.addTypeRestriction(methodName, typeRestrictionFunction);
		module.addTypeRestriction(methodName, typeRestrictionFunction);
		module.filteredBundleTree().includeBundle(
			MessageBundleDescriptor.newBundle(methodName));
	}



	/**
	 * The modularity scheme should prevent all intermodular method conflicts.
	 * Precedence is specified as an array of message sets that are not allowed
	 * to be messages generating the arguments of this message.  For example,
	 * <{'_+_'} , {'_+_' , '_*_'}> for the '_*_' operator makes * bind tighter
	 * than + and also groups multiple *'s left-to-right.
	 *
	 * @param methodName A {@linkplain AtomDescriptor method name}.
	 * @param illegalArgMsgs The {@linkplain TupleDescriptor restrictions}.
	 */
	public void atDisallowArgumentMessages (
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject illegalArgMsgs)
	{
		methodName.makeImmutable();
		// So we can safely hold onto it in the VM
		illegalArgMsgs.makeImmutable();
		// So we can safely hold this data in the VM
		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfUnderscores();
		assert numArgs == illegalArgMsgs.tupleSize()
			: "Wrong number of entries in restriction tuple.";
		assert methodName.isAtom();
		// Fix precedence.
		final AvailObject bundle =
			module.filteredBundleTree().includeBundle(
				MessageBundleDescriptor.newBundle(methodName));
		bundle.addRestrictions(illegalArgMsgs);
		module.addGrammaticalRestrictions(methodName, illegalArgMsgs);
	}

	/**
	 * Create the two-argument defining method. The first parameter of the
	 * method is the name, the second parameter is the {@linkplain
	 * FunctionDescriptor block}.
	 *
	 * @param defineMethodName
	 *            The name of the defining method.
	 */
	public void bootstrapDefiningMethod (
		final @NotNull String defineMethodName)
	{
		assert module != null;
		final L1InstructionWriter writer = new L1InstructionWriter();
		writer.primitiveNumber(
			Primitive.prim253_SimpleMethodDeclaration.primitiveNumber);
		writer.argumentTypes(
			TupleTypeDescriptor.stringTupleType(),
			FunctionTypeDescriptor.mostGeneralType());
		writer.returnType(TOP.o());
		// Declare the local that holds primitive failure information.
		final int failureLocal = writer.createLocal(
			VariableTypeDescriptor.wrapInnerType(
				IntegerRangeTypeDescriptor.naturalNumbers()));
		writer.write(
			new L1Instruction(
				L1Operation.L1_doGetLocal,
				failureLocal));
		writer.write(
			new L1Instruction(
				L1Operation.L1_doCall,
				writer.addLiteral(
					MethodDescriptor.vmCrashMethod()),
				writer.addLiteral(BottomTypeDescriptor.bottom())));
		final AvailObject newFunction = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty());
		newFunction.makeImmutable();
		final AvailObject nameTuple = StringDescriptor.from(
			defineMethodName);
		final AvailObject realName = AtomDescriptor.create(nameTuple);
		module.atNameAdd(nameTuple, realName);
		module.atNewNamePut(nameTuple, realName);
		try
		{
			addMethodBody(realName, newFunction);
		}
		catch (final SignatureException e)
		{
			assert false
			: "This boostrap method should not interfere with anything";
		}
	}

	/**
	 * Create the one-argument {@linkplain AvailRuntime#specialObject(int)
	 * special object} method. The parameter is the {@linkplain
	 * IntegerDescriptor ordinal} of the special object.
	 *
	 * @param specialObjectName
	 *            The name of the {@linkplain AvailRuntime#specialObject(int)
	 *            special object} method.
	 */
	public void bootstrapSpecialObject (
		final @NotNull String specialObjectName)
	{
		//  Define the method for extracting special objects known to the VM.
		assert module != null;
		final L1InstructionWriter writer = new L1InstructionWriter();
		writer.primitiveNumber(
			Primitive.prim240_SpecialObject.primitiveNumber);
		writer.argumentTypes(IntegerRangeTypeDescriptor.naturalNumbers());
		writer.returnType(ANY.o());
		// Declare the local that holds primitive failure information.
		writer.createLocal(
			VariableTypeDescriptor.wrapInnerType(
				IntegerRangeTypeDescriptor.naturalNumbers()));
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(
					StringDescriptor.from("no such special object"))));
		// Push the argument.
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLocal,
				1));
		writer.write(
			new L1Instruction(
				L1Operation.L1_doMakeTuple,
				2));
		writer.write(
			new L1Instruction(
				L1Operation.L1_doCall,
				writer.addLiteral(
					MethodDescriptor.vmCrashMethod()),
				writer.addLiteral(BottomTypeDescriptor.bottom())));
		final AvailObject newFunction = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty());
		newFunction.makeImmutable();
		final AvailObject nameTuple =
			StringDescriptor.from(specialObjectName);
		final AvailObject realName = AtomDescriptor.create(nameTuple);
		module.atNameAdd(nameTuple, realName);
		module.atNewNamePut(nameTuple, realName);
		try
		{
			addMethodBody(realName, newFunction);
		}
		catch (final SignatureException e)
		{
			assert false
			: "This boostrap method should not interfere with anything";
		}
	}

	/**
	 * Answer the Avail {@linkplain SetDescriptor set} of outstanding unresolved
	 * {@linkplain ForwardDeclarationDescriptor forward declarations} within the
	 * current module.
	 *
	 * @return The set of unresolved forward declarations.
	 */
	public AvailObject unresolvedForwards ()
	{
		return pendingForwards;
	}

	/**
	 * Answer the map whose sole token-component is firstPiece.  The map is from
	 * message to messageBundle.  Filter selectors based on the
	 * visibility of names in the current module.
	 *
	 * @param firstPiece
	 *            An Avail {@linkplain StringDescriptor string}.
	 * @return A map from TODO
	 */
	public AvailObject completeBundlesStartingWith (
		final AvailObject firstPiece)
	{
		final AvailObject incomplete = module.filteredBundleTree().incomplete();
		if (!incomplete.hasKey(firstPiece))
		{
			return MapDescriptor.empty();
		}
		return incomplete.mapAt(firstPiece).complete();
	}

	/**
	 * Answer the map whose first (but not only) token-component is firstPiece.
	 * The map is from the second piece to bundle tree.  Filter selectors based
	 * on the visibility of names in the current module.
	 *
	 * @param firstPiece
	 *            The first Avail {@linkplain StringDescriptor string} token by
	 *            which to filter messages.
	 * @return A map from TODO
	 */
	public AvailObject incompleteBundlesStartingWith (
		final AvailObject firstPiece)
	{
		final AvailObject incomplete = module.filteredBundleTree().incomplete();
		if (!incomplete.hasKey(firstPiece))
		{
			return MapDescriptor.empty();
		}
		return incomplete.mapAt(firstPiece).incomplete();
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
	 * {@linkplain AtomDescriptor true name} associated with the string,
	 * creating the true name if necessary. A local true name always hides other
	 * true names.
	 *
	 * @param stringName
	 *            An Avail {@linkplain TupleDescriptor string}.
	 * @return
	 *            A {@linkplain AtomDescriptor true name}.
	 * @throws AmbiguousNameException
	 *            If the string could represent several different true names.
	 */
	public @NotNull AvailObject lookupName (
			final @NotNull AvailObject stringName)
		throws AmbiguousNameException
	{
		assert stringName.isString();
		//  Check if it's already defined somewhere...
		final AvailObject who = module.trueNamesForStringName(stringName);
		AvailObject trueName;
		if (who.setSize() == 0)
		{
			trueName = AtomDescriptor.create(stringName);
			trueName.makeImmutable();
			module.atPrivateNameAdd(stringName, trueName);
			return trueName;
		}
		if (who.setSize() == 1)
		{
			return who.asTuple().tupleAt(1);
		}
		throw new AmbiguousNameException();
	}

	/**
	 * Return the current {@linkplain ProcessDescriptor process}.
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
	 * @param methodName A {@linkplain AtomDescriptor method name}.
	 */
	public void resolvedForwardWithName (
		final @NotNull AvailObject aForward,
		final @NotNull AvailObject methodName)
	{
		assert methodName.isAtom();

		if (!runtime.hasMethodsAt(methodName))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		final AvailObject impSet = runtime.methodsAt(methodName);
		assert !impSet.equalsNull();
		if (!pendingForwards.hasElement(aForward))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		if (!impSet.includesImplementation(aForward))
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
	 * AtomDescriptor method name}.
	 *
	 * @param methodName
	 *            The {@linkplain AtomDescriptor true name} of a method.
	 * @param implementation
	 *            An {@linkplain ImplementationDescriptor implementation}.
	 */
	public void removeMethodNamedImplementation (
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject implementation)
	{
		assert methodName.isAtom();

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
	 * @param ordinal
	 *            An ordinal.
	 * @return
	 *            {@code true} if there is a {@linkplain Primitive primitive}
	 *            with the specified ordinal, {@code false} otherwise.
	 */
	public boolean supportsPrimitive (final int ordinal)
	{
		final Primitive primitive = Primitive.byPrimitiveNumber(ordinal);
		return primitive != null && !primitive.hasFlag(Flag.Private);
	}

	/**
	 * Set the resulting value of a primitive invocation. Answer primitive
	 * {@linkplain Result#SUCCESS success}.
	 *
	 * @param result
	 *        The result of performing a {@linkplain Primitive primitive}.
	 * @return Primitive {@linkplain Result#SUCCESS success}.
	 */
	public @NotNull Result primitiveSuccess (final @NotNull AvailObject result)
	{
		primitiveResult = result;
		return SUCCESS;
	}

	/**
	 * Set the resulting value of a primitive invocation to the {@linkplain
	 * AvailErrorCode#numericCode() numeric code} of the specified {@link
	 * AvailErrorCode}. Answer primitive {@linkplain Result#FAILURE failure}.
	 *
	 * @param code
	 *        An {@link AvailErrorCode}.
	 * @return Primitive {@linkplain Result#FAILURE failure}.
	 */
	public @NotNull Result primitiveFailure (final @NotNull AvailErrorCode code)
	{
		primitiveResult = code.numericCode();
		return FAILURE;
	}

	/**
	 * Set the resulting value of a primitive invocation to the {@linkplain
	 * AvailErrorCode#numericCode() numeric code} of the {@link AvailErrorCode}
	 * embedded within the specified {@linkplain AvailException exception}.
	 * Answer primitive {@linkplain Result#FAILURE failure}.
	 *
	 * @param exception
	 *        An {@linkplain AvailException exception}.
	 * @return Primitive {@linkplain Result#FAILURE failure}.
	 */
	public @NotNull Result primitiveFailure (
		final @NotNull AvailException exception)
	{
		primitiveResult = exception.numericCode();
		return FAILURE;
	}

	/**
	 * Set the resulting value of a primitive invocation to the {@linkplain
	 * AvailErrorCode#numericCode() numeric code} of the {@link AvailErrorCode}
	 * embedded within the specified {@linkplain AvailRuntimeException
	 * runtime exception}.  Answer primitive {@linkplain Result#FAILURE
	 * failure}.
	 *
	 * @param exception
	 *        A {@linkplain AvailRuntimeException runtime exception}.
	 * @return Primitive {@linkplain Result#FAILURE failure}.
	 */
	public @NotNull Result primitiveFailure (
		final @NotNull AvailRuntimeException exception)
	{
		primitiveResult = exception.numericCode();
		return FAILURE;
	}

	/**
	 * Set the resulting value of a primitive invocation. Answer primitive
	 * {@linkplain Result#FAILURE failure}.
	 *
	 * @param result
	 *        The result of performing a {@linkplain Primitive primitive}.
	 * @return Primitive {@linkplain Result#FAILURE failure}.
	 */
	public @NotNull Result primitiveFailure (final @NotNull AvailObject result)
	{
		primitiveResult = result;
		return FAILURE;
	}

	/**
	 * Answer the result that a primitive invocation has produced.
	 *
	 * @return The result that was {@link #primitiveSuccess(AvailObject)
	 * recorded} during primitive execution.
	 */
	public AvailObject primitiveResult ()
	{
		return primitiveResult;
	}

	/**
	 * Invoke an Avail primitive.  The primitive number and arguments are
	 * passed.  If the primitive fails, use {@link
	 * Interpreter#primitiveSuccess(AvailObject)} to set the primitiveResult to
	 * some object indicating what the problem was, and return primitiveFailed
	 * immediately.  If the primitive causes the continuation to change (e.g.,
	 * through block invocation, continuation restart, exception throwing, etc),
	 * answer continuationChanged.  Otherwise the primitive succeeded, and we
	 * simply capture the resulting value with {@link
	 * Interpreter#primitiveSuccess(AvailObject)} and return {@link
	 * Result#SUCCESS}.
	 *
	 * @param primitiveNumber The number of the primitive to invoke.
	 * @param compiledCode The compiled code whose primitive is being attempted.
	 * @param args The list of arguments to supply to the primitive.
	 * @return The resulting status of the primitive attempt.
	 */
	public final Result attemptPrimitive (
		final int primitiveNumber,
		final @NotNull AvailObject compiledCode,
		final List<AvailObject> args)
	{
		final Primitive primitive = Primitive.byPrimitiveNumber(primitiveNumber);
		if (logger.isLoggable(Level.FINER))
		{
			logger.finer(String.format(
				"attempting primitive %d (%s) ...",
				primitiveNumber,
				primitive));
		}

		primitiveResult = null;
		primitiveCompiledCodeBeingAttempted = compiledCode;
		final Result success = primitive.attempt(args, this);
		assert success != FAILURE || !primitive.hasFlag(Flag.CannotFail);
		primitiveCompiledCodeBeingAttempted = null;
		if (logger.isLoggable(Level.FINER))
		{
			logger.finer(String.format(
				"... completed primitive %d (%s) => %s",
				primitiveNumber,
				primitive,
				success.name()));
		}

		return success;
	}

	/**
	 * Start execution of the given {@linkplain FunctionDescriptor function}
	 * with the given {@link List} of arguments.
	 *
	 * @param aFunction
	 *            The function to begin executing.
	 * @param args
	 *            The arguments to pass to the function.
	 * @return
	 *            The success state indicating if the function was a primitive
	 *            that succeeded or failed or replaced the current continuation.
	 *            If the function was not a primitive, always indicate that the
	 *            current continuation was replaced.
	 */
	public abstract Result invokeFunctionArguments (
		AvailObject aFunction,
		List<AvailObject> args);

	/**
	 * Ensure any cached interpreter state is represented in the current
	 * continuation.
	 */
	public abstract void reifyContinuation ();

	/**
	 * Resume execution of the passed {@linkplain ContinuationDescriptor
	 * continuation}.
	 *
	 * @param continuation The continuation to resume.
	 */
	public abstract void prepareToExecuteContinuation (
		AvailObject continuation);

	/**
	 * Raise an exception. Scan the stack of continuations until one is found
	 * for a function whose code specifies {@linkplain
	 * Primitive#prim200_CatchException primitive 200}.
	 * Get that continuation's second argument (a handler block of one
	 * argument), and check if that handler block will accept the
	 * exceptionValue. If not, keep looking. If it will accept it, unwind the
	 * stack so that the primitive 200 method is the top entry, and invoke the
	 * handler block with exceptionValue. If there is no suitable handler block,
	 * fail the primitive.
	 *
	 * @param exceptionValue The exception object being raised.
	 * @return An indication of success of the raising of the exception.
	 */
	public abstract Result searchForExceptionHandler (
		AvailObject exceptionValue);

	/**
	 * Prepare the interpreter to deal with executing the given function, using
	 * the given arguments.  Also set up the new function's locals.  Assume the
	 * current context has already been reified.  If the function is a
	 * primitive, then it was already attempted and must have failed, so the
	 * failure value must be in {@link #primitiveResult}.  The (Java) caller
	 * will deal with that.
	 *
	 * @param aFunction The function to invoke.
	 * @param args The arguments to pass to the function.
	 */
	public abstract void invokeWithoutPrimitiveFunctionArguments (
		AvailObject aFunction,
		List<AvailObject> args);

	/**
	 * Run the given function with the provided arguments as a top-level action.
	 * Run until the entire process completes, then return the result.
	 *
	 * @param aFunction A {@linkplain FunctionDescriptor function} to run.
	 * @param arguments The arguments for the function.
	 * @return The result of running the specified function to completion.
	 */
	public abstract AvailObject runFunctionArguments (
		AvailObject aFunction,
		List<AvailObject> arguments);

	@Override
	public String toString ()
	{
		return String.format(
			"%s [%s]",
			getClass().getSimpleName(),
			process().name());
	}
}
