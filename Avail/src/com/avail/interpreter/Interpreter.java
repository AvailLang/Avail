/**
 * Interpreter.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.exceptions.*;
import com.avail.interpreter.Primitive.*;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.primitive.*;

/**
 * This is the abstraction for execution Avail code.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public abstract class Interpreter
{
	/** A {@linkplain Logger logger}. */
	public static final @NotNull Logger logger =
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
	 * Answer the {@linkplain ModuleDescriptor module} context of the
	 * {@linkplain Interpreter interpreter}.
	 *
	 * @return The module undergoing compilation, or {@code null} if the module
	 *         is no longer undergoing compilation.
	 */
	public AvailObject module ()
	{
		return module;
	}

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
	protected volatile boolean interruptRequestFlag;

	/**
	 * The {@link FiberDescriptor} being executed by this interpreter.
	 */
	public AvailObject fiber;

	/**
	 * A place to store the result of a primitive when the primitive
	 * succeeds by returning {@link Result#SUCCESS}.
	 */
	public AvailObject primitiveResult;

	/**
	 * A place to store the primitive {@linkplain CompiledCodeDescriptor
	 * compiled code} being attempted.  That allows {@linkplain
	 * P_340_PushConstant} to get to the first literal in order to return it
	 * from the primitive.
	 */
	private AvailObject primitiveCompiledCodeBeingAttempted;

	/**
	 * Answer the primitive {@linkplain CompiledCodeDescriptor compiled code}
	 * that is currently being attempted.  That allows {@linkplain
	 * P_340_PushConstant} to get to the first literal in order to return it
	 * from the primitive.
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
	 * Answer true if an interrupt has been requested for this fiber.
	 *
	 * @return If an interrupt is pending.
	 */
	public final boolean isInterruptRequested ()
	{
		return fiber.interruptRequestFlags() != 0;
	}

	/**
	 * Exit the current {@linkplain FiberDescriptor fiber} with the
	 * specified result.
	 *
	 * @param finalObject
	 *            The {@link AvailObject} that is the final result of running
	 *            the {@linkplain FiberDescriptor fiber}.
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

		// Also initialize the fiber field.
		fiber = FiberDescriptor.mutable().create();
		fiber.name(StringDescriptor.from(String.format(
			"unnamed, creation time = %d, hash = %d",
			System.currentTimeMillis(),
			fiber.hash())));
		fiber.priority(IntegerDescriptor.fromUnsignedByte((short)50));
		fiber.continuation(NullDescriptor.nullObject());
		fiber.executionState(ExecutionState.RUNNING);
		fiber.clearInterruptRequestFlags();
		fiber.breakpointBlock(NullDescriptor.nullObject());
		fiber.fiberGlobals(MapDescriptor.empty());
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
	 * @param methodName
	 *        A {@linkplain AtomDescriptor method name}.
	 * @param bodyBlock
	 *        The {@linkplain FunctionDescriptor body block}.
	 * @param extendGrammar
	 *        {@code true} if the method name should be added to the current
	 *        module's bundle tree, {@code false} otherwise.
	 * @throws SignatureException
	 *         If the signature is invalid.
	 */
	public void addMethodBody (
			final @NotNull AvailObject methodName,
			final @NotNull AvailObject bodyBlock,
			final boolean extendGrammar)
		throws SignatureException
	{
		assert methodName.isAtom();
		assert bodyBlock.isFunction();

		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		splitter.checkImplementationSignature(bodyBlock.kind());
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
		if (extendGrammar)
		{
			module.filteredBundleTree().includeBundle(
				MessageBundleDescriptor.newBundle(methodName));
		}
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
			methodName,
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
	 *        A {@linkplain AtomDescriptor method name}.
	 * @param bodySignature
	 *        The {@linkplain MethodImplementationDescriptor method signature}.
	 * @param extendGrammar
	 *        {@code true} if the method name should be added to the current
	 *        module's bundle tree, {@code false} otherwise.
	 * @throws SignatureException
	 *         If the signature is malformed.
	 */
	public void addAbstractSignature (
			final @NotNull AvailObject methodName,
			final @NotNull AvailObject bodySignature,
			final boolean extendGrammar)
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
		if (extendGrammar)
		{
			module.filteredBundleTree().includeBundle(
				MessageBundleDescriptor.newBundle(methodName));
		}
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
	 * Add a type restriction to the method associated with the given method
	 * name.
	 *
	 * @param methodName
	 *        The method name, an {@linkplain AtomDescriptor atom}.
	 * @param typeRestrictionFunction
	 *        A {@linkplain FunctionDescriptor function} that validates the
	 *        static types of arguments at call sites.
	 * @throws SignatureException
	 *         If the signature is invalid.
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
		methodName.makeImmutable();
		typeRestrictionFunction.makeImmutable();
		runtime.addTypeRestriction(methodName, typeRestrictionFunction);
		module.addTypeRestriction(methodName, typeRestrictionFunction);
		module.filteredBundleTree().includeBundle(
			MessageBundleDescriptor.newBundle(methodName));
	}

	/**
	 * Add a seal to the method associated with the given method name.
	 *
	 * @param methodName
	 *        The method name, an {@linkplain AtomDescriptor atom}.
	 * @param seal
	 *        The signature at which to seal the method.
	 * @throws SignatureException
	 *         If the signature is invalid.
	 */
	public void addSeal (
			final @NotNull AvailObject methodName,
			final @NotNull AvailObject seal)
		throws SignatureException
	{
		assert methodName.isAtom();
		assert seal.isTuple();
		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		if (seal.tupleSize() != splitter.numberOfArguments())
		{
			throw new SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		methodName.makeImmutable();
		seal.makeImmutable();
		runtime.addSeal(methodName, seal);
		module.addSeal(methodName, seal);
	}

	/**
	 * The modularity scheme should prevent all intermodular method conflicts.
	 * Precedence is specified as an array of message sets that are not allowed
	 * to be messages generating the arguments of this message.  For example,
	 * &lt;&#123;'_+_'&#125; , &#123;'_+_' , '_*_'&#125;&gt; for the '_*_'
	 * operator makes * bind tighter than + and also groups multiple *'s
	 * left-to-right.
	 *
	 * @param methodName
	 *            An {@linkplain AtomDescriptor atom} that names a method.
	 * @param illegalArgMsgs
	 *            The {@linkplain TupleDescriptor tuple} of {@linkplain
	 *            SetDescriptor sets} of {@linkplain AtomDescriptor atoms} that
	 *            name methods.
	 * @throws SignatureException
	 *            If one of the specified names is inappropriate as a method
	 *            name.
	 */
	public void atDisallowArgumentMessages (
		final @NotNull AvailObject methodName,
		final @NotNull AvailObject illegalArgMsgs)
	throws SignatureException
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
		module.filteredBundleTree().removeBundle(bundle);
		bundle.addGrammaticalRestrictions(illegalArgMsgs);
		module.addGrammaticalRestrictions(methodName, illegalArgMsgs);
		module.filteredBundleTree().includeBundle(bundle);
	}

	/**
	 * Create a new atom with the given name.  The name does not have to be
	 * unique, as it is only used as a visual hint about the purpose of an atom.
	 *
	 * @param nameString
	 *            A {@linkplain StringDescriptor string} describing the atom.
	 * @return
	 *            A new atom.
	 */
	public @NotNull AvailObject createAtom (
		final @NotNull AvailObject nameString)
	{
		return AtomDescriptor.create(nameString, module);
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
		L1InstructionWriter writer = new L1InstructionWriter();
		writer.primitiveNumber(
			P_253_SimpleMethodDeclaration.instance.primitiveNumber);
		writer.argumentTypes(
			TupleTypeDescriptor.stringTupleType(),
			FunctionTypeDescriptor.mostGeneralType());
		writer.returnType(TOP.o());
		// Declare the local that holds primitive failure information.
		int failureLocal = writer.createLocal(
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
		final AvailObject fromStringFunction = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty());
		fromStringFunction.makeImmutable();
		writer = new L1InstructionWriter();
		writer.primitiveNumber(
			P_228_MethodDeclarationFromAtom.instance.primitiveNumber);
		writer.argumentTypes(
			ATOM.o(),
			FunctionTypeDescriptor.mostGeneralType());
		writer.returnType(TOP.o());
		// Declare the local that holds primitive failure information.
		failureLocal = writer.createLocal(
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
		final AvailObject fromAtomFunction = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty());
		final AvailObject name = StringDescriptor.from(
			defineMethodName);
		try
		{
			final AvailObject trueName = lookupName(name);
			addMethodBody(trueName, fromStringFunction, true);
			addMethodBody(trueName, fromAtomFunction, true);
		}
		catch (final AmbiguousNameException e)
		{
			assert false
				: "This bootstrap method should not interfere with anything";
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
		writer.primitiveNumber(P_240_SpecialObject.instance.primitiveNumber);
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
		try
		{
			addMethodBody(lookupName(nameTuple), newFunction, true);
		}
		catch (final AmbiguousNameException e)
		{
			assert false
				: "This bootstrap method should not interfere with anything";
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
			trueName = AtomDescriptor.create(stringName, module);
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
	 * Return the current {@linkplain FiberDescriptor fiber}.
	 *
	 * @return The current executing fiber.
	 */
	public AvailObject fiber ()
	{
		return fiber;
	}

	/**
	 * The given forward is in the fiber of being resolved. A real
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
		final AvailObject method = runtime.methodsAt(methodName);
		assert !method.equalsNull();
		if (!pendingForwards.hasElement(aForward))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		if (!method.includesImplementation(aForward))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		pendingForwards = pendingForwards.setWithoutElementCanDestroy(
			aForward, true);
		method.removeImplementation(aForward);
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
	 * Primitive primitive} with the specified primitive number?
	 *
	 * @param primitiveNumber
	 *            The primitive number.
	 * @return
	 *            {@code true} if there is a {@linkplain Primitive primitive}
	 *            with the specified primitive number, {@code false} otherwise.
	 */
	public boolean supportsPrimitive (final int primitiveNumber)
	{
		final Primitive primitive = Primitive.byPrimitiveNumber(
			primitiveNumber);
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
		final Primitive primitive =
			Primitive.byPrimitiveNumber(primitiveNumber);
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
			final String failPart = success == FAILURE
				? " (" + AvailErrorCode.byNumericCode(
					primitiveResult.extractInt())
					+ ")"
				: "";
			logger.finer(String.format(
				"... completed primitive (%s) => %s%s",
				primitive,
				success.name(),
				failPart));
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
	 * Resume execution of the passed {@linkplain ContinuationDescriptor
	 * continuation}.
	 *
	 * @param continuation The continuation to resume.
	 */
	public abstract void prepareToResumeContinuation (
		AvailObject continuation);

	/**
	 * Prepare to restart the given continuation.  Its new arguments, if any,
	 * have already been supplied, and all other data has been wiped.  Do not
	 * tally this as an invocation of the method.
	 *
	 * @param continuationToRestart
	 */
	public abstract void prepareToRestartContinuation (
		final AvailObject continuationToRestart);

	/**
	 * Raise an exception. Scan the stack of continuations until one is found
	 * for a function whose code specifies {@linkplain P_200_CatchException}.
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
	 * the given arguments.  <em>Do not</em> set up the new function's locals.
	 * In the case of level one code simulated in level two, a preamble level
	 * two instruction will set them up.  In the case of level two, the code to
	 * initialize them is a sequence of variable creation instructions that can
	 * be interleaved, hoisted or even elided, just like any other instructions.
	 *
	 * <p>
	 * Assume the current context has already been reified.  If the function is
	 * a primitive, then it was already attempted and must have failed, so the
	 * failure value must be in {@link #primitiveResult}.  Move it somewhere
	 * more appropriate for the specialization of {@link Interpreter}, but not
	 * into the primitive failure variable (since that local has not been
	 * created yet).
	 *
	 * @param aFunction The function to invoke.
	 * @param args The arguments to pass to the function.
	 * @param caller The calling continuation.
	 */
	public abstract void invokeWithoutPrimitiveFunctionArguments (
		AvailObject aFunction,
		List<AvailObject> args,
		final @NotNull AvailObject caller);

	/**
	 * Run the given function with the provided arguments as a top-level action.
	 * Run until the entire fiber completes, then return the result.
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
			fiber().name());
	}
}
