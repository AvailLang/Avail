/**
 * AvailLoader.java
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

package com.avail.interpreter;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.exceptions.AvailErrorCode.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.compiler.AbstractAvailCompiler.ParserState;
import com.avail.descriptor.*;
import com.avail.exceptions.*;
import com.avail.utility.*;

/**
 * An {@code AvailLoader} is responsible for orchestrating module-level
 * side-effects, such as those caused by {@linkplain MethodDefinitionDescriptor
 * method}, {@linkplain AbstractDefinitionDescriptor abstract}, and {@linkplain
 * ForwardDefinitionDescriptor forward} definitions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailLoader
{
	/**
	 * The {@link AvailRuntime} for the loader. Since a {@linkplain AvailLoader
	 * loader} cannot migrate between two {@linkplain AvailRuntime runtimes}, it
	 * is safe to cache it for efficient access.
	 */
	private final AvailRuntime runtime = AvailRuntime.current();

	/**
	 * The Avail {@linkplain ModuleDescriptor module} undergoing {@linkplain
	 * AvailLoader loader}.
	 */
	private final AvailObject module;

	/**
	 * Answer the {@linkplain ModuleDescriptor module} undergoing loading by
	 * this {@linkplain AvailLoader loader}.
	 *
	 * @return A module.
	 */
	public AvailObject module ()
	{
		return module;
	}

	/**
	 * Construct a new {@link AvailLoader}.
	 *
	 * @param module
	 *        The Avail {@linkplain ModuleDescriptor module} undergoing loading
	 *        by this {@linkplain AvailLoader loader}.
	 */
	public AvailLoader (final AvailObject module)
	{
		this.module = module;
	}

	/** The unresolved forward method declarations. */
	public AvailObject pendingForwards = SetDescriptor.empty();

	/**
	 * The given forward is in the process of being resolved. A real
	 * definition is about to be added to the method tables, so remove the
	 * forward now.
	 *
	 * @param aForward A forward declaration.
	 * @param methodName A {@linkplain AtomDescriptor method name}.
	 */
	@InnerAccess final void resolvedForwardWithName (
		final AvailObject aForward,
		final AvailObject methodName)
	{
		assert methodName.isAtom();

		if (!runtime.hasMethodsAt(methodName))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		final AvailObject method = runtime.methodsAt(methodName);
		assert !method.equalsNil();
		if (!pendingForwards.hasElement(aForward))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		if (!method.includesDefinition(aForward))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		pendingForwards = pendingForwards.setWithoutElementCanDestroy(
			aForward, true);
		method.removeDefinition(aForward);
		module.resolvedForwardWithName(aForward, methodName);
	}

	/**
	 * Add the method definition. The precedence rules can change at any
	 * time.
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
	public final void addMethodBody (
			final AvailObject methodName,
			final AvailObject bodyBlock,
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
		methodName.makeShared();
		bodyBlock.makeShared();
		//  Add the method definition.
		final AvailObject method = runtime.methodFor(methodName);
		final AvailObject newMethodDefinition =
			MethodDefinitionDescriptor.create(method, bodyBlock);
		final AvailObject bodySignature = bodyBlock.kind();
		AvailObject forward = null;
		final AvailObject impsTuple = method.definitionsTuple();
		for (final AvailObject existingImp : impsTuple)
		{
			final AvailObject existingType = existingImp.bodySignature();
			final boolean same = existingType.argsTupleType().equals(
				bodySignature.argsTupleType());
			if (same)
			{
				if (existingImp.isForwardDefinition())
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
		method.addDefinition(newMethodDefinition);
		assert methodName.isAtom();
		final AvailObject newBundle =
			MessageBundleDescriptor.newBundle(methodName);
		final AvailObject theModule = module;
		theModule.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				theModule.addMethodDefinition(newMethodDefinition);
				if (extendGrammar)
				{
					theModule.filteredBundleTree().includeBundle(newBundle);
				}
			}
		});
	}

	/**
	 * This is a forward declaration of a method. Insert an appropriately
	 * stubbed definition in the module's method dictionary, and add it to
	 * the list of methods needing to be declared later in this module.
	 *
	 * @param methodName
	 *        A {@linkplain AtomDescriptor method name}.
	 * @param bodySignature
	 *        A {@linkplain MethodDefinitionDescriptor method signature}.
	 * @throws SignatureException
	 *         If the signature is malformed.
	 */
	public final void addForwardStub (
			final AvailObject methodName,
			final AvailObject bodySignature)
		throws SignatureException
	{
		methodName.makeShared();
		bodySignature.makeShared();
		//  Add the stubbed method definition.
		final AvailObject method = runtime.methodFor(methodName);
		for (final AvailObject definition :
			method.definitionsTuple())
		{
			final AvailObject existingType =
				definition.bodySignature();
			final boolean same =
				existingType.argsTupleType().equals(
					bodySignature.argsTupleType());
			if (same)
			{
				throw new SignatureException(
					E_REDEFINED_WITH_SAME_ARGUMENT_TYPES);
			}
			if (existingType.acceptsArgTypesFromFunctionType(
				bodySignature))
			{
				if (!bodySignature.returnType().isSubtypeOf(
					existingType.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
			if (bodySignature.acceptsArgTypesFromFunctionType(
				existingType))
			{
				if (!existingType.returnType().isSubtypeOf(
					bodySignature.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
		}
		final AvailObject newForward = ForwardDefinitionDescriptor.create(
			method,
			bodySignature);
		method.addDefinition(newForward);
		final AvailObject newBundle =
			MessageBundleDescriptor.newBundle(methodName);
		final AvailObject theModule = module;
		theModule.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				theModule.addMethodDefinition(newForward);
				pendingForwards = pendingForwards.setWithElementCanDestroy(
					newForward,
					true);
				assert methodName.isAtom();
				theModule.filteredBundleTree().includeBundle(newBundle);
			}
		});
	}

	/**
	 * Add the abstract method signature. A class is considered abstract if
	 * there are any abstract methods that haven't been overridden with
	 * definitions for it.
	 *
	 * @param methodName
	 *        A {@linkplain AtomDescriptor method name}.
	 * @param bodySignature
	 *        The {@linkplain MethodDefinitionDescriptor method signature}.
	 * @param extendGrammar
	 *        {@code true} if the method name should be added to the current
	 *        module's bundle tree, {@code false} otherwise.
	 * @throws SignatureException
	 *         If the signature is malformed.
	 */
	public final void addAbstractSignature (
			final AvailObject methodName,
			final AvailObject bodySignature,
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
		methodName.makeShared();
		bodySignature.makeShared();
		//  Add the method definition.
		final AvailObject method = runtime.methodFor(methodName);
		@Nullable AvailObject forward = null;
		for (final AvailObject existingDefinition :
			method.definitionsTuple())
		{
			final AvailObject existingType =
				existingDefinition.bodySignature();
			final boolean same =
				existingType.argsTupleType().equals(
					bodySignature.argsTupleType());
			if (same)
			{
				if (existingDefinition.isForwardDefinition())
				{
					forward = existingDefinition;
				}
				else
				{
					throw new SignatureException(
						E_REDEFINED_WITH_SAME_ARGUMENT_TYPES);
				}
			}
			if (existingType.acceptsArgTypesFromFunctionType(
				bodySignature))
			{
				if (!bodySignature.returnType().isSubtypeOf(
					existingType.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
			if (bodySignature.acceptsArgTypesFromFunctionType(
				existingType))
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
		final AvailObject newDefinition = AbstractDefinitionDescriptor.create(
			method,
			bodySignature);
		method.addDefinition(newDefinition);
		final AvailObject newBundle =
			MessageBundleDescriptor.newBundle(methodName);
		final AvailObject theModule = module;
		theModule.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				theModule.addMethodDefinition(newDefinition);
				if (extendGrammar)
				{
					theModule.filteredBundleTree().includeBundle(newBundle);
				}
			}
		});
	}

	/**
	 * Add the macro definition. The precedence rules can not change after
	 * the first definition is encountered, so set them to 'no restrictions'
	 * if they're not set already.
	 *
	 * @param methodName
	 *        The macro's name, an {@linkplain AtomDescriptor atom}.
	 * @param macroBody
	 *        A {@linkplain FunctionDescriptor function} that manipulates parse
	 *        nodes.
	 * @throws SignatureException
	 *         If the macro signature is invalid.
	 */
	public final void addMacroBody (
			final AvailObject methodName,
			final AvailObject macroBody)
		throws SignatureException
	{
		assert methodName.isAtom();
		assert macroBody.isFunction();

		final AvailObject method = runtime.methodFor(methodName);
		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfArguments();
		assert macroBody.code().numArgs() == numArgs
			: "Wrong number of arguments in macro definition";
		// Make it so we can safely hold onto these things in the VM.
		methodName.makeShared();
		macroBody.makeShared();
		final AvailObject macroBodyType = macroBody.kind();
		for (final AvailObject existingDefinition :
			method.definitionsTuple())
		{
			final AvailObject existingType =
				existingDefinition.bodySignature();
			final boolean same =
				existingType.argsTupleType().equals(
					macroBodyType.argsTupleType());
			if (same)
			{
				throw new SignatureException(
					E_REDEFINED_WITH_SAME_ARGUMENT_TYPES);
			}
			if (existingType.acceptsArgTypesFromFunctionType(
				macroBodyType))
			{
				if (!macroBodyType.returnType().isSubtypeOf(
					existingType.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
			if (macroBodyType.acceptsArgTypesFromFunctionType(
				existingType))
			{
				if (!existingType.returnType().isSubtypeOf(
					macroBodyType.returnType()))
				{
					throw new SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS);
				}
			}
		}
		// Add the macro definition.
		final AvailObject macroDefinition =
			MacroDefinitionDescriptor.create(method, macroBody);
		method.addDefinition(macroDefinition);
		final AvailObject newBundle =
			MessageBundleDescriptor.newBundle(methodName);
		final AvailObject theModule = module;
		theModule.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				theModule.addMethodDefinition(macroDefinition);
				theModule.filteredBundleTree().includeBundle(newBundle);
			}
		});
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
	public final void addTypeRestriction (
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
		methodName.makeShared();
		typeRestrictionFunction.makeShared();
		runtime.addTypeRestriction(methodName, typeRestrictionFunction);
		final AvailObject newBundle =
			MessageBundleDescriptor.newBundle(methodName);
		final AvailObject theModule = module;
		theModule.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				theModule.addTypeRestriction(
					methodName, typeRestrictionFunction);
				theModule.filteredBundleTree().includeBundle(newBundle);
			}
		});
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
	public final void addSeal (
			final AvailObject methodName,
			final AvailObject seal)
		throws SignatureException
	{
		assert methodName.isAtom();
		assert seal.isTuple();
		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		if (seal.tupleSize() != splitter.numberOfArguments())
		{
			throw new SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		methodName.makeShared();
		seal.makeShared();
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
	 *        An {@linkplain AtomDescriptor atom} that names a method.
	 * @param illegalArgMsgs
	 *        The {@linkplain TupleDescriptor tuple} of {@linkplain
	 *        SetDescriptor sets} of {@linkplain AtomDescriptor atoms} that name
	 *        methods.
	 * @throws SignatureException
	 *         If one of the specified names is inappropriate as a method name.
	 */
	public void addGrammaticalRestrictions (
			final AvailObject methodName,
			final AvailObject illegalArgMsgs)
		throws SignatureException
	{
		methodName.makeShared();
		illegalArgMsgs.makeShared();
		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfUnderscores();
		assert numArgs == illegalArgMsgs.tupleSize()
			: "Wrong number of entries in restriction tuple.";
		assert methodName.isAtom();
		// Fix precedence.
		final AvailObject newBundle =
			MessageBundleDescriptor.newBundle(methodName);
		final AvailObject theModule = module;
		theModule.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				final AvailObject bundle =
					theModule.filteredBundleTree().includeBundle(newBundle);
				theModule.filteredBundleTree().removeBundle(bundle);
				bundle.addGrammaticalRestrictions(illegalArgMsgs);
				theModule.addGrammaticalRestrictions(
					methodName, illegalArgMsgs);
				theModule.filteredBundleTree().includeBundle(bundle);
			}
		});
	}

	/**
	 * Unbind the specified definition from the {@linkplain
	 * AtomDescriptor method name}.
	 *
	 * @param methodName
	 *        The {@linkplain AtomDescriptor true name} of a method.
	 * @param definition
	 *        An {@linkplain DefinitionDescriptor definition}.
	 */
	public final void removeDefinition (
		final AvailObject methodName,
		final AvailObject definition)
	{
		assert methodName.isAtom();
		if (definition.isForwardDefinition())
		{
			pendingForwards = pendingForwards.setWithoutElementCanDestroy(
				definition,
				true);
		}
		runtime.removeMethod(methodName, definition);
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
	public final AvailObject lookupName (final AvailObject stringName)
		throws AmbiguousNameException
	{
		assert stringName.isString();
		//  Check if it's already defined somewhere...
		final Mutable<AvailObject> atom = new Mutable<AvailObject>();
		final AvailObject theModule = module;
		theModule.lock(
			new Continuation0()
			{
				@Override
				public void value ()
				{
					final AvailObject who = theModule.trueNamesForStringName(
						stringName);
					AvailObject trueName;
					if (who.setSize() == 0)
					{
						trueName = AtomDescriptor.create(stringName, theModule);
						trueName.makeImmutable();
						theModule.atPrivateNameAdd(stringName, trueName);
						atom.value = trueName;
					}
					if (who.setSize() == 1)
					{
						atom.value = who.asTuple().tupleAt(1);
					}
				}
			});
		if (atom.value == null)
		{
			throw new AmbiguousNameException();
		}
		return atom.value;
	}

	/**
	 * The {@link ParserState}, if any, at which parsing is currently taking
	 * place.  Since parsing can happen in parallel, it indicates the location
	 * of the current compilation step directly leading to execution of code in
	 * this {@code Interpreter}.
	 */
	public ParserState currentParserState = null;
}
