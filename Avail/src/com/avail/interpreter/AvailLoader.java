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
import java.util.ArrayList;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.compiler.*;
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
	private final A_Module module;

	/**
	 * Answer the {@linkplain ModuleDescriptor module} undergoing loading by
	 * this {@linkplain AvailLoader loader}.
	 *
	 * @return A module.
	 */
	public A_Module module ()
	{
		return module;
	}

	/**
	 * The {@linkplain MessageBundleTreeDescriptor message bundle tree} that
	 * this {@linkplain AvailLoader loader} is using to parse its {@linkplain
	 * ModuleDescriptor module}.
	 */
	@InnerAccess @Nullable A_BundleTree rootBundleTree;

	/**
	 * Answer the {@linkplain MessageBundleTreeDescriptor message bundle tree}
	 * that this {@linkplain AvailLoader loader} is using to parse its
	 * {@linkplain ModuleDescriptor module}.
	 *
	 * @return A message bundle tree.
	 */
	public A_BundleTree rootBundleTree ()
	{
		final A_BundleTree tree = rootBundleTree;
		assert tree != null;
		return tree;
	}

	/**
	 * Construct a new {@link AvailLoader}.
	 *
	 * @param module
	 *        The Avail {@linkplain ModuleDescriptor module} undergoing loading
	 *        by this {@linkplain AvailLoader loader}.
	 */
	public AvailLoader (final A_Module module)
	{
		this.module = module;
	}

	/**
	 * Create the {@link #rootBundleTree} now that the module's imports and new
	 * public names have been declared.
	 */
	public void createFilteredBundleTree ()
	{
		rootBundleTree = module.buildFilteredBundleTree();
	}

	/** The unresolved forward method declarations. */
	public A_Set pendingForwards = SetDescriptor.empty();

	/**
	 * The given forward is in the process of being resolved. A real
	 * definition is about to be added to the method tables, so remove the
	 * forward now.
	 *
	 * @param forwardDefinition A forward declaration.
	 * @param methodName A {@linkplain AtomDescriptor method name}.
	 */
	@InnerAccess final void resolvedForwardWithName (
		final A_Definition forwardDefinition,
		final A_Atom methodName)
	{
		assert methodName.isAtom();

		final A_Method method = forwardDefinition.definitionMethod();
		if (!pendingForwards.hasElement(forwardDefinition))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		if (!method.includesDefinition(forwardDefinition))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		pendingForwards = pendingForwards.setWithoutElementCanDestroy(
			forwardDefinition, true);
		method.removeDefinition(forwardDefinition);
		module.resolveForward(forwardDefinition);
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
			final A_Atom methodName,
			final A_Function bodyBlock,
			final boolean extendGrammar)
		throws SignatureException
	{
		assert methodName.isAtom();
		assert bodyBlock.isFunction();

		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		splitter.checkImplementationSignature(bodyBlock.kind());
		final int numArgs = splitter.numberOfArguments();
		if (bodyBlock.code().numArgs() != numArgs)
		{
			throw new SignatureException(
				E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		//  Make it so we can safely hold onto these things in the VM
		methodName.makeShared();
		bodyBlock.makeShared();
		//  Add the method definition.
		final A_Bundle bundle = methodName.bundleOrCreate();
		final A_Method method = bundle.bundleMethod();
		final A_Definition newMethodDefinition =
			MethodDefinitionDescriptor.create(method, module, bodyBlock);
		final A_Type bodySignature = bodyBlock.kind();
		A_Definition forward = null;
		final A_Tuple impsTuple = method.definitionsTuple();
		for (final A_Definition existingImp : impsTuple)
		{
			final A_Type existingType = existingImp.bodySignature();
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
		method.methodAddDefinition(newMethodDefinition);
		final A_Module theModule = module;
		final A_BundleTree root = rootBundleTree();
		theModule.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				root.addBundle(bundle);
				root.flushForNewOrChangedBundle(bundle);
				theModule.moduleAddDefinition(newMethodDefinition);
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
			final A_Atom methodName,
			final A_Type bodySignature)
		throws SignatureException
	{
		methodName.makeShared();
		bodySignature.makeShared();
		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		splitter.checkImplementationSignature(bodySignature);
		final A_Type bodyArgsTupleType = bodySignature.argsTupleType();
		//  Add the stubbed method definition.
		final A_Bundle bundle = methodName.bundleOrCreate();
		final A_Method method = bundle.bundleMethod();
		for (final A_Definition definition : method.definitionsTuple())
		{
			final A_Type existingType = definition.bodySignature();
			if (existingType.argsTupleType().equals(bodyArgsTupleType))
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
		final A_Definition newForward = ForwardDefinitionDescriptor.create(
			method,
			module,
			bodySignature);
		method.methodAddDefinition(newForward);
		final A_Module theModule = module;
		final A_BundleTree root = rootBundleTree();
		theModule.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				theModule.moduleAddDefinition(newForward);
				pendingForwards = pendingForwards.setWithElementCanDestroy(
					newForward,
					true);
				root.addBundle(bundle);
				root.flushForNewOrChangedBundle(bundle);
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
	 * @throws SignatureException
	 *         If the signature is malformed.
	 */
	public final void addAbstractSignature (
			final A_Atom methodName,
			final A_Type bodySignature)
		throws SignatureException
	{
		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfArguments();
		final A_Type bodyArgsSizes = bodySignature.argsTupleType().sizeRange();
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
		final A_Bundle bundle = methodName.bundleOrCreate();
		final A_Method method = bundle.bundleMethod();
		final A_Definition newDefinition = AbstractDefinitionDescriptor.create(
			method,
			module,
			bodySignature);
		module().moduleAddDefinition(newDefinition);
		@Nullable AvailObject forward = null;
		for (final AvailObject existingDefinition : method.definitionsTuple())
		{
			final A_Type existingType = existingDefinition.bodySignature();
			final boolean same = existingType.argsTupleType().equals(
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
		method.methodAddDefinition(newDefinition);
		final A_Module theModule = module;
		final A_BundleTree root = rootBundleTree();
		theModule.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				root.addBundle(bundle);
				root.flushForNewOrChangedBundle(bundle);
				theModule.moduleAddDefinition(newDefinition);
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
	 * @param prefixFunctions
	 *            The tuple of prefix functions.
	 * @param macroBody
	 *        A {@linkplain FunctionDescriptor function} that manipulates parse
	 *        nodes.
	 * @throws SignatureException
	 *         If the macro signature is invalid.
	 */
	public void addMacroBody (
		final A_Atom methodName,
		final A_Tuple prefixFunctions,
		final A_Function macroBody)
	throws SignatureException
	{
		assert methodName.isAtom();
		assert macroBody.isFunction();

		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfArguments();
		assert macroBody.code().numArgs() == numArgs
			: "Wrong number of arguments in macro definition";
		final A_Bundle bundle = methodName.bundleOrCreate();
		final A_Method method = bundle.bundleMethod();
		// Make it so we can safely hold onto these things in the VM.
		methodName.makeShared();
		prefixFunctions.makeShared();
		macroBody.makeShared();
		// Add the macro definition.
		final AvailObject macroDefinition = MacroDefinitionDescriptor.create(
			method,
			module,
			prefixFunctions,
			macroBody);
		module().moduleAddDefinition(macroDefinition);
		final A_Type macroBodyType = macroBody.kind();
		for (final A_Definition existingDefinition : method.definitionsTuple())
		{
			final A_Type existingType = existingDefinition.bodySignature();
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
		method.methodAddDefinition(macroDefinition);
		final A_Module theModule = module;
		final A_BundleTree root = rootBundleTree();
		theModule.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				root.addBundle(bundle);
				root.flushForNewOrChangedBundle(bundle);
				theModule.moduleAddDefinition(macroDefinition);
			}
		});

	}

	/**
	 * Add a semantic restriction to its associated method.
	 *
	 * @param restriction
	 *        A {@linkplain SemanticRestrictionDescriptor semantic restriction}
	 *        that validates the static types of arguments at call sites.
	 *
	 * @throws SignatureException
	 *         If the signature is invalid.
	 */
	public final void addSemanticRestriction (
		final A_SemanticRestriction restriction)
	throws SignatureException
	{
		final A_Method method = restriction.definitionMethod();
		final int numArgs = method.numArgs();
		if (restriction.function().code().numArgs() != numArgs)
		{
			throw new SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		runtime.addSemanticRestriction(restriction);
		final A_Module theModule = module;
		final A_BundleTree root = rootBundleTree();
		theModule.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				theModule.moduleAddSemanticRestriction(restriction);
				for (final A_Bundle bundle : method.bundles())
				{
					// Update the bundle tree if the bundle is visible
					if (root.allBundles().hasKey(bundle.message()))
					{
						root.flushForNewOrChangedBundle(bundle);
					}
				}
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
			final A_Atom methodName,
			final A_Tuple seal)
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
			final A_Atom methodName,
			final A_Tuple illegalArgMsgs)
		throws SignatureException
	{
		methodName.makeShared();
		illegalArgMsgs.makeShared();
		final MessageSplitter splitter = new MessageSplitter(methodName.name());
		final int numArgs = splitter.numberOfUnderscores();
		assert numArgs == illegalArgMsgs.tupleSize()
			: "Wrong number of entries in restriction tuple.";
		final A_Bundle bundle = methodName.bundleOrCreate();
		final A_Module theModule = module;
		final List<A_Set> bundleSets =
			new ArrayList<>(illegalArgMsgs.tupleSize());
		for (final A_Set atomsSet : illegalArgMsgs)
		{
			A_Set bundleSet = SetDescriptor.empty();
			for (final A_Atom atom : atomsSet)
			{
				bundleSet = bundleSet.setWithElementCanDestroy(
					atom.bundleOrCreate(),
					true);
			}
			bundleSets.add(bundleSet.makeShared());
		}
		final A_GrammaticalRestriction grammaticalRestriction =
			GrammaticalRestrictionDescriptor.create(
				TupleDescriptor.fromList(bundleSets),
				bundle,
				theModule);
		final A_BundleTree root = rootBundleTree();
		theModule.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				bundle.addGrammaticalRestriction(grammaticalRestriction);
				root.addBundle(bundle);
				theModule.addGrammaticalRestrictions(
					methodName, illegalArgMsgs);
				root.flushForNewOrChangedBundle(bundle);
			}
		});
	}

	/**
	 * Attempt to add the declaration to the compiler scope information within
	 * the client data stored in this interpreter's current fiber.
	 *
	 * @param declaration A {@link DeclarationNodeDescriptor declaration}.
	 * @return {@code Null} if successful, otherwise an {@link AvailErrorCode}
	 *         indicating the problem.
	 */
	public final @Nullable AvailErrorCode addDeclaration (
		final A_Phrase declaration)
	{
		final A_Atom clientDataGlobalKey =
			AtomDescriptor.clientDataGlobalKey();
		final A_Atom compilerScopeMapKey =
			AtomDescriptor.compilerScopeMapKey();
		final A_Fiber fiber = FiberDescriptor.current();
		A_Map fiberGlobals = fiber.fiberGlobals();
		A_Map clientData = fiberGlobals.mapAt(clientDataGlobalKey);
		A_Map bindings = clientData.mapAt(compilerScopeMapKey);
		final A_String declarationName = declaration.token().string();
		if (bindings.hasKey(declarationName))
		{
			return E_LOCAL_DECLARATION_SHADOWS_ANOTHER;
		}
		bindings = bindings.mapAtPuttingCanDestroy(
			declarationName,
			declaration,
			true);
		clientData = clientData.mapAtPuttingCanDestroy(
			compilerScopeMapKey,
			bindings,
			true);
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			clientDataGlobalKey,
			clientData,
			true);
		fiberGlobals.makeImmutable();
		fiber.fiberGlobals(fiberGlobals);
		return null;
	}

	/**
	 * Unbind the specified method definition from this loader and runtime.
	 *
	 * @param definition
	 *        A {@linkplain DefinitionDescriptor definition}.
	 */
	public final void removeDefinition (
		final A_Definition definition)
	{
		if (definition.isForwardDefinition())
		{
			pendingForwards = pendingForwards.setWithoutElementCanDestroy(
				definition,
				true);
		}
		runtime.removeDefinition(definition);
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
	public final A_Atom lookupName (final A_String stringName)
		throws AmbiguousNameException
	{
		assert stringName.isString();
		//  Check if it's already defined somewhere...
		final MutableOrNull<A_Atom> atom = new MutableOrNull<A_Atom>();
		final A_Module theModule = module;
		theModule.lock(
			new Continuation0()
			{
				@Override
				public void value ()
				{
					final A_Set who = theModule.trueNamesForStringName(
						stringName);
					if (who.setSize() == 0)
					{
						final A_Atom trueName = AtomDescriptor.create(
							stringName, theModule);
						trueName.makeImmutable();
						theModule.addPrivateName(stringName, trueName);
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
		return atom.value();
	}
}
