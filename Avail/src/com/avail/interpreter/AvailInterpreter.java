/**
 * interpreter/AvailInterpreter.java
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
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.avail.annotations.NotNull;
import com.avail.compiler.Continuation1;
import com.avail.compiler.Generator;
import com.avail.compiler.scanner.AvailScanner;
import com.avail.descriptor.AbstractSignatureDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.BooleanDescriptor;
import com.avail.descriptor.ByteStringDescriptor;
import com.avail.descriptor.ClosureDescriptor;
import com.avail.descriptor.CyclicTypeDescriptor;
import com.avail.descriptor.ForwardSignatureDescriptor;
import com.avail.descriptor.ImplementationSetDescriptor;
import com.avail.descriptor.InfinityDescriptor;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.ListTypeDescriptor;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.MethodSignatureDescriptor;
import com.avail.descriptor.ObjectTypeDescriptor;
import com.avail.descriptor.ProcessDescriptor;
import com.avail.descriptor.SetDescriptor;
import com.avail.descriptor.SetTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.UnexpandedMessageBundleTreeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelOne.L1Instruction;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;

public abstract class AvailInterpreter
{
	private AvailObject methods = MapDescriptor.empty();
	private AvailObject methodNamesForParsing =
		UnexpandedMessageBundleTreeDescriptor.newDepth(1);
	private AvailObject pendingForwards = SetDescriptor.empty();
	private AvailObject modules = MapDescriptor.empty();
	private AvailObject module;
	private List<AvailObject> specialObjects;
	
	/**
	 * A mapping from {@linkplain CyclicTypeDescriptor keys} to {@link
	 * RandomAccessFile}s open for reading.
	 */
	private Map<AvailObject, RandomAccessFile> openReadableFiles =
		new HashMap<AvailObject, RandomAccessFile>();
	
	/**
	 * A mapping from {@linkplain CyclicTypeDescriptor keys} to {@link
	 * RandomAccessFile}s open for writing.
	 */
	private Map<AvailObject, RandomAccessFile> openWritableFiles =
		new HashMap<AvailObject, RandomAccessFile>();
	
	private AvailObject isJumping = BooleanDescriptor.objectFromBoolean(true);
	private AvailObject typeNames = MapDescriptor.empty();
	private AvailObject openLibraries;
	protected volatile int interruptRequestFlag;
	protected AvailObject process;
	protected AvailObject primitiveResult;


	// translate-accessing

	public void addModule (
		final AvailObject aModule)
	{

		modules = modules.mapAtPuttingCanDestroy(
			aModule.name(),
			aModule,
			true);
	}

	public int argCountForPrimitive (
		final short n)
	{
		//  Answer how many arguments this primitive expects (not counting the array of arguments
		//  used for reference maintenance).

		return Primitive.byPrimitiveNumber(n).argCount();
	}

	public void atAddForwardStubFor (
		final AvailObject methodName,
		final AvailObject bodySignature)
	{
		//  This is a forward declaration of a method.  Insert an appropriately stubbed implementation
		//  in the method dictionary, and add it to the list of methods needing to be declared later in
		//  this module.

		methodName.makeImmutable();
		bodySignature.makeImmutable();
		//  Add the stubbed method implementation.
		final AvailObject newImp = AvailObject.newIndexedDescriptor(0, ForwardSignatureDescriptor.mutableDescriptor());
		newImp.bodySignature(bodySignature);
		newImp.makeImmutable();
		module.atAddMethodImplementation(methodName, newImp);
		AvailObject imps;
		if (methods.hasKey(methodName))
		{
			imps = methods.mapAt(methodName);
		}
		else
		{
			imps = ImplementationSetDescriptor.newImplementationSetWithName(methodName);
			methods = methods.mapAtPuttingCanDestroy(
				methodName,
				imps,
				true);
		}
		final AvailObject impsTuple = imps.implementationsTuple();
		for (int i = 1, _end1 = impsTuple.tupleSize(); i <= _end1; i++)
		{
			final AvailObject existingType = impsTuple.tupleAt(i).bodySignature();
			boolean same = true;
			for (int k = 1, _end2 = bodySignature.numArgs(); k <= _end2; k++)
			{
				if (! existingType.argTypeAt(k).equals(bodySignature.argTypeAt(k)))
				{
					same = false;
				}
			}
			if (same)
			{
				error("Attempted to redefine (as forward) a method with the same argument types");
				return;
			}
			if (existingType.acceptsArgTypesFromClosureType(bodySignature))
			{
				if (! bodySignature.returnType().isSubtypeOf(existingType.returnType()))
				{
					error("Specialized method should return at least as special a result as more general method");
					return;
				}
			}
		}
		imps.addImplementation(newImp);
		pendingForwards = pendingForwards.setWithElementCanDestroy(newImp, true);
		final AvailObject parts = splitMethodName(methodName);
		methodNamesForParsing.includeBundleAtMessageParts(methodName, parts);
		module.filteredBundleTree().includeBundleAtMessageParts(methodName, parts);
	}

	public void atAddMethodBody (
		final AvailObject methodName,
		final AvailObject method)
	{
		//  Add the method implementation.  The precedence rules can not change after the first
		//  implementation is encountered, so set them to 'no restrictions' if they're not set already.

		final short numArgs = method.type().numArgs();
		final AvailObject returnsBlock = ClosureDescriptor.newStubForNumArgsConstantResult(numArgs, method.type().returnType());
		final AvailObject requiresBlock = ClosureDescriptor.newStubForNumArgsConstantResult(numArgs, BooleanDescriptor.objectFromBoolean(true));
		atAddMethodBodyRequiresBlockReturnsBlock(
			methodName,
			method,
			requiresBlock,
			returnsBlock);
	}

	public void atAddMethodBodyRequiresBlockReturnsBlock (
		final AvailObject methodName,
		final AvailObject bodyBlock,
		final AvailObject requiresBlock,
		final AvailObject returnsBlock)
	{
		//  Add the method implementation.  The precedence rules can change at any time.
		//  The requiresBlock is run at compile time to ensure the method is being used in an
		//  appropriate way.  The returnsBlock lets us home in on the type returned by a general
		//  method by transforming the call-site-specific argument type information into a
		//  return type for the method.

		final int numArgs = countUnderscoresIn(methodName.name());
		assert (bodyBlock.code().numArgs() == numArgs) : "Wrong number of arguments in method definition";
		assert (requiresBlock.code().numArgs() == numArgs) : "Wrong number of arguments in method type verifier";
		assert (returnsBlock.code().numArgs() == numArgs) : "Wrong number of arguments in method result type generator";
		//  Make it so we can safely hold onto these things in the VM
		methodName.makeImmutable();
		bodyBlock.makeImmutable();
		requiresBlock.makeImmutable();
		returnsBlock.makeImmutable();
		//  Add the method implementation.
		final AvailObject newImp = AvailObject.newIndexedDescriptor(0, MethodSignatureDescriptor.mutableDescriptor());
		newImp.bodyBlockRequiresBlockReturnsBlock(
			bodyBlock,
			requiresBlock,
			returnsBlock);
		newImp.makeImmutable();
		module.atAddMethodImplementation(methodName, newImp);
		AvailObject imps;
		if (methods.hasKey(methodName))
		{
			imps = methods.mapAt(methodName);
		}
		else
		{
			imps = ImplementationSetDescriptor.newImplementationSetWithName(methodName);
			methods = methods.mapAtPuttingCanDestroy(
				methodName,
				imps,
				true);
		}
		final AvailObject bodySignature = bodyBlock.type();
		AvailObject forward = null;
		final AvailObject impsTuple = imps.implementationsTuple();
		for (int i = 1, _end1 = impsTuple.tupleSize(); i <= _end1; i++)
		{
			final AvailObject existingImp = impsTuple.tupleAt(i);
			final AvailObject existingType = existingImp.bodySignature();
			boolean same = true;
			for (int k = 1, _end2 = bodySignature.numArgs(); k <= _end2; k++)
			{
				if (! existingType.argTypeAt(k).equals(bodySignature.argTypeAt(k)))
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
					error("Attempted to redefine method with same argument types");
					return;
				}
			}
			if (existingImp.bodySignature().acceptsArgTypesFromClosureType(bodySignature))
			{
				if (! bodySignature.returnType().isSubtypeOf(existingImp.bodySignature().returnType()))
				{
					error("Specialized method should return at least as special a result as more general method");
					return;
				}
			}
		}
		if (forward != null)
		{
			resolvedForwardWithName(forward, methodName);
		}
		imps.addImplementation(newImp);
		final AvailObject parts = splitMethodName(methodName);
		methodNamesForParsing.includeBundleAtMessageParts(methodName, parts);
		module.filteredBundleTree().includeBundleAtMessageParts(methodName, parts);
	}

	public void atDeclareAbstractSignatureRequiresBlockReturnsBlock (
		final AvailObject methodName,
		final AvailObject bodySignature,
		final AvailObject requiresBlock,
		final AvailObject returnsBlock)
	{
		//  Add the abstract method signature.  A class is considered abstract if there are any
		//  abstract methods that haven't been overridden with implementations for it.  The
		//  requiresBlock is called at compile time at each call site (i.e., link time) to ensure the
		//  method is being used in an appropriate way.  The returnsBlock lets us home in on the
		//  type returned by a general method by transforming the call-site-specific argument
		//  type information into a return type for the method.

		final int numArgs = countUnderscoresIn(methodName.name());
		assert (bodySignature.numArgs() == numArgs) : "Wrong number of arguments in abstract method signature";
		assert (requiresBlock.code().numArgs() == numArgs) : "Wrong number of arguments in abstract method type verifier";
		assert (returnsBlock.code().numArgs() == numArgs) : "Wrong number of arguments in abstract method result type specializer";
		//  Make it so we can safely hold onto these things in the VM
		methodName.makeImmutable();
		bodySignature.makeImmutable();
		requiresBlock.makeImmutable();
		returnsBlock.makeImmutable();
		//  Add the method implementation.
		final AvailObject newImp = AvailObject.newIndexedDescriptor(0, AbstractSignatureDescriptor.mutableDescriptor());
		newImp.bodySignatureRequiresBlockReturnsBlock(
			bodySignature,
			requiresBlock,
			returnsBlock);
		newImp.makeImmutable();
		module.atAddMethodImplementation(methodName, newImp);
		AvailObject imps;
		if (methods.hasKey(methodName))
		{
			imps = methods.mapAt(methodName);
		}
		else
		{
			imps = ImplementationSetDescriptor.newImplementationSetWithName(methodName);
			methods = methods.mapAtPuttingCanDestroy(
				methodName,
				imps,
				true);
		}
		AvailObject forward = null;
		final AvailObject impsTuple = imps.implementationsTuple();
		for (int i = 1, _end1 = impsTuple.tupleSize(); i <= _end1; i++)
		{
			final AvailObject existingImp = impsTuple.tupleAt(i);
			final AvailObject existingType = existingImp.bodySignature();
			boolean same = true;
			for (int k = 1, _end2 = bodySignature.numArgs(); k <= _end2; k++)
			{
				if (! existingType.argTypeAt(k).equals(bodySignature.argTypeAt(k)))
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
					error("Attempted to redefine method with same argument types");
					return;
				}
			}
			if (existingImp.bodySignature().acceptsArgTypesFromClosureType(bodySignature))
			{
				if (! bodySignature.returnType().isSubtypeOf(existingImp.bodySignature().returnType()))
				{
					error("Specialized method should return at least as special a result as more general method");
					return;
				}
			}
		}
		if (forward != null)
		{
			resolvedForwardWithName(forward, methodName);
		}
		imps.addImplementation(newImp);
		final AvailObject parts = splitMethodName(methodName);
		methodNamesForParsing.includeBundleAtMessageParts(methodName, parts);
		module.filteredBundleTree().includeBundleAtMessageParts(methodName, parts);
	}

	public void atDisallowArgumentMessages (
		final AvailObject methodName,
		final AvailObject illegalArgMsgs)
	{
		//  The modularity scheme should prevent all intermodular method conflicts.  Precedence is
		//  specified as an array of message sets that are not allowed to be messages generating the
		//  arguments of this message.  For example, <{'_+_'} , {'_+_' , '_*_'}> for the '_*_' operator
		//  makes * bind tighter than + and also groups multiple *'s left-to-right.

		methodName.makeImmutable();
		//  So we can safely hold onto it in the VM
		illegalArgMsgs.makeImmutable();
		//  So we can safely hold this data in the VM
		final int numArgs = countUnderscoresIn(methodName.name());
		assert (numArgs == illegalArgMsgs.tupleSize()) : "Wrong number of entries in restriction tuple.";
		final AvailObject parts = splitMethodName(methodName);
		//  Fix the global precedence...
		AvailObject bundle = methodNamesForParsing.includeBundleAtMessageParts(methodName, parts);
		bundle.addRestrictions(illegalArgMsgs);
		//  Fix the module-scoped precedence, if different...
		bundle = module.filteredBundleTree().includeBundleAtMessageParts(methodName, parts);
		bundle.addRestrictions(illegalArgMsgs);
		module.atAddMessageRestrictions(methodName, illegalArgMsgs);
	}

	public void atRemoveRestrictions (
		final AvailObject messageName,
		final AvailObject messageRestrictions)
	{
		//  Only for rolling back the compilation of a module.

		final AvailObject parts = splitMethodName(messageName);
		final AvailObject bundle = methodNamesForParsing.bundleAtMessageParts(messageName, parts);
		bundle.removeRestrictions(messageRestrictions);
		if (! bundle.hasRestrictions())
		{
			if (! methods.hasKey(messageName))
			{
				methodNamesForParsing.removeMessageParts(messageName, splitMethodName(messageName));
			}
		}
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
		//  Define the special defining method.

		assert module != null;
		AvailObject newClosure;
		L1InstructionWriter writer = new L1InstructionWriter();
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(VoidDescriptor.voidObject())));
		writer.argumentTypes(
			TupleTypeDescriptor.stringTupleType(), Types.closure.object());
		writer.primitiveNumber(253);
		writer.returnType(Types.voidType.object());
		newClosure = ClosureDescriptor.newMutableObjectWithCodeAndCopiedTuple(
			writer.compiledCode(),
			TupleDescriptor.empty());
		newClosure.makeImmutable();
		final AvailObject nameTuple =
			ByteStringDescriptor.mutableObjectFromNativeString(
				defineMethodName);
		final AvailObject realName = CyclicTypeDescriptor.newCyclicTypeWithName(
			nameTuple);
		module.atNameAdd(nameTuple, realName);
		module.atNewNamePut(nameTuple, realName);
		atAddMethodBody(realName, newClosure);
	}

	/**
	 * Create the one-argument {@linkplain #specialObjects() special object}
	 * method. The parameter is the {@linkplain IntegerDescriptor ordinal} of
	 * the special object.
	 * 
	 * @param specialObjectName The name of the {@linkplain #specialObjects()
	 *                          special object} method.
	 */
	public void bootstrapSpecialObject (
		final @NotNull String specialObjectName)
	{
		//  Define the special object method.

		assert module != null;
		final AvailObject naturalNumbers =
			IntegerRangeTypeDescriptor.lowerBoundInclusiveUpperBoundInclusive(
				IntegerDescriptor.one(),
				true,
				InfinityDescriptor.positiveInfinity(),
				false);
		AvailObject newClosure;
		L1InstructionWriter writer = new L1InstructionWriter();
		writer.write(
			new L1Instruction(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(VoidDescriptor.voidObject())));
		writer.argumentTypes(naturalNumbers);
		writer.primitiveNumber(240);
		writer.returnType(Types.all.object());
		newClosure = ClosureDescriptor.newMutableObjectWithCodeAndCopiedTuple(
			writer.compiledCode(),
			TupleDescriptor.empty());
		newClosure.makeImmutable();
		final AvailObject nameTuple =
			ByteStringDescriptor.mutableObjectFromNativeString(
				specialObjectName);
		final AvailObject realName = CyclicTypeDescriptor.newCyclicTypeWithName(
			nameTuple);
		module.atNameAdd(nameTuple, realName);
		module.atNewNamePut(nameTuple, realName);
		atAddMethodBody(realName, newClosure);
	}

	public void checkUnresolvedForwards ()
	{
		//  Make sure all forward declarations have been resolved.

		if (pendingForwards.setSize() == 0)
		{
			return;
		};
		error("Some forward declarations were not resolved within this module.");
	}

	public AvailObject completeBundlesStartingWith (
		final AvailObject firstPiece)
	{
		//  Answer the map whose sole token-component is firstPiece.  The map is
		//  from message (cyclicType) to messageBundle.  Filter selectors based
		//  on the visibility of names in the current module.

		final AvailObject all = module.filteredBundleTree().incomplete();
		if (! all.hasKey(firstPiece))
		{
			return MapDescriptor.empty();
		}
		return all.mapAt(firstPiece).complete();
	}

	public int countUnderscoresIn (
		final AvailObject anAvailString)
	{
		//  Answer how many underscore characters are in the given Avail string.

		int count = 0;
		for (int i = 1, _end1 = anAvailString.tupleSize(); i <= _end1; i++)
		{
			if ((((char)(anAvailString.tupleAt(i).codePoint())) == '_'))
			{
				++count;
			}
		}
		return count;
	}

	/**
	 * Answer the open readable {@linkplain RandomAccessFile file} associated
	 * with the specified {@linkplain CyclicTypeDescriptor handle}.
	 * 
	 * @param handle A {@linkplain CyclicTypeDescriptor handle}.
	 * @return The open {@linkplain RandomAccessFile file} associated with the
	 *         {@linkplain CyclicTypeDescriptor cycle}, or {@code null} if no
	 *         such association exists.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public RandomAccessFile getReadableFile (final @NotNull AvailObject handle)
	{
		assert handle.isCyclicType();
		return openReadableFiles.get(handle);
	}
	
	/**
	 * Associate the specified {@linkplain CyclicTypeDescriptor handle} with the
	 * open readable {@linkplain RandomAccessFile file}.
	 * 
	 * @param handle A {@linkplain CyclicTypeDescriptor handle}.
	 * @param file An open {@linkplain RandomAccessFile file}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public void putReadableFile (
		final @NotNull AvailObject handle,
		final @NotNull RandomAccessFile file)
	{
		assert handle.isCyclicType();
		openReadableFiles.put(handle, file);
	}
	
	/**
	 * Remove the association between the specified {@linkplain
	 * CyclicTypeDescriptor handle} and its open readable {@linkplain
	 * RandomAccessFile file}.
	 * 
	 * @param handle A {@linkplain CyclicTypeDescriptor handle}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public void forgetReadableFile (final @NotNull AvailObject handle)
	{
		assert handle.isCyclicType();
		openReadableFiles.remove(handle);
	}
	
	/**
	 * Answer the open writable {@linkplain RandomAccessFile file} associated
	 * with the specified {@linkplain CyclicTypeDescriptor handle}.
	 * 
	 * @param handle A {@linkplain CyclicTypeDescriptor handle}.
	 * @return The open {@linkplain RandomAccessFile file} associated with the
	 *         {@linkplain CyclicTypeDescriptor cycle}, or {@code null} if no
	 *         such association exists.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public RandomAccessFile getWritableFile (final @NotNull AvailObject handle)
	{
		assert handle.isCyclicType();
		return openWritableFiles.get(handle);
	}
	
	/**
	 * Associate the specified {@linkplain CyclicTypeDescriptor handle} with the
	 * open writable {@linkplain RandomAccessFile file}.
	 * 
	 * @param handle A {@linkplain CyclicTypeDescriptor handle}.
	 * @param file An open {@linkplain RandomAccessFile file}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public void putWritableFile (
		final @NotNull AvailObject handle,
		final @NotNull RandomAccessFile file)
	{
		assert handle.isCyclicType();
		openWritableFiles.put(handle, file);
	}
	
	/**
	 * Remove the association between the specified {@linkplain
	 * CyclicTypeDescriptor handle} and its open writable {@linkplain
	 * RandomAccessFile file}.
	 * 
	 * @param handle A {@linkplain CyclicTypeDescriptor handle}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public void forgetWritableFile (final @NotNull AvailObject handle)
	{
		assert handle.isCyclicType();
		openWritableFiles.remove(handle);
	}
	
	/**
	 * Answer the open {@linkplain RandomAccessFile file} associated with the
	 * specified {@linkplain CyclicTypeDescriptor handle}.
	 * 
	 * @param handle A {@linkplain CyclicTypeDescriptor handle}.
	 * @return The open {@linkplain RandomAccessFile file} associated with the
	 *         {@linkplain CyclicTypeDescriptor cycle}, or {@code null} if no
	 *         such association exists.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public RandomAccessFile getOpenFile (final @NotNull AvailObject handle)
	{
		assert handle.isCyclicType();
		final RandomAccessFile file = openReadableFiles.get(handle);
		if (file != null)
		{
			return file;
		}
		
		return openWritableFiles.get(handle);
	}
	
	public boolean hasMethodsAt (
		final AvailObject selector)
	{
		//  Answer whether there are any methods associated with the given selector (a cyclicType).

		return methods.hasKey(selector);
	}

	public boolean includesModuleNamed (
		final AvailObject moduleName)
	{
		assert moduleName.isTuple();
		return modules.hasKey(moduleName);
	}

	public AvailObject incompleteBundlesStartingWith (
		final AvailObject firstPiece)
	{
		//  Answer the map whose first (but not only) token-component is firstPiece.
		//  The map is from the second piece to bundle tree.  Filter selectors based
		//  on the visibility of names in the current module.

		final AvailObject all = module.filteredBundleTree().incomplete();
		if (! all.hasKey(firstPiece))
		{
			return MapDescriptor.empty();
		}
		return all.mapAt(firstPiece).incomplete();
	}

	public void initializeSpecialObjects ()
	{
		//  Set up the Array of special objects.

		// Basic types
		specialObjects = Arrays.<AvailObject>asList(new AvailObject[100]);
		specialObjects.set(1, Types.all.object());
		specialObjects.set(2, Types.booleanType.object());
		specialObjects.set(3, Types.character.object());
		specialObjects.set(4, Types.closure.object());
		specialObjects.set(5, Types.closureType.object());
		specialObjects.set(6, Types.compiledCode.object());
		specialObjects.set(7, Types.container.object());
		specialObjects.set(8, Types.containerType.object());
		specialObjects.set(9, Types.continuation.object());
		specialObjects.set(10, Types.continuationType.object());
		specialObjects.set(11, Types.cyclicType.object());
		specialObjects.set(12, Types.doubleObject.object());
		specialObjects.set(13, IntegerRangeTypeDescriptor.extendedIntegers().makeImmutable());
		specialObjects.set(14, Types.falseType.object());
		specialObjects.set(15, Types.floatObject.object());
		specialObjects.set(16, Types.generalizedClosureType.object());
		specialObjects.set(17, IntegerRangeTypeDescriptor.integers().makeImmutable());
		specialObjects.set(18, Types.integerType.object());
		specialObjects.set(19, ListTypeDescriptor.listTypeForTupleType(
					TupleTypeDescriptor.mostGeneralTupleType()).makeImmutable());
		specialObjects.set(20, Types.listType.object());
		specialObjects.set(21, Types.mapType.object());
		specialObjects.set(22, Types.meta.object());
		specialObjects.set(23, ObjectTypeDescriptor.objectTypeFromMap(MapDescriptor.empty()).type().type().makeImmutable());
		specialObjects.set(24, ObjectTypeDescriptor.objectTypeFromMap(MapDescriptor.empty()).type().type().type().makeImmutable());
		specialObjects.set(25, ObjectTypeDescriptor.objectTypeFromMap(MapDescriptor.empty()).type().makeImmutable());
		specialObjects.set(26, Types.primType.object());
		specialObjects.set(27, Types.process.object());
		specialObjects.set(28, SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					Types.all.object()).makeImmutable());
		specialObjects.set(29, Types.setType.object());
		specialObjects.set(30, TupleTypeDescriptor.stringTupleType());
		specialObjects.set(31, Types.terminates.object());
		specialObjects.set(32, Types.terminatesType.object());
		specialObjects.set(33, Types.trueType.object());
		specialObjects.set(34, TupleTypeDescriptor.mostGeneralTupleType().makeImmutable());
		specialObjects.set(35, Types.tupleType.object());
		specialObjects.set(36, Types.type.object());
		specialObjects.set(37, Types.voidType.object());

		// Code reflection
		specialObjects.set(40, Types.messageBundle.object());
		specialObjects.set(41, Types.signature.object());
		specialObjects.set(42, Types.abstractSignature.object());
		specialObjects.set(43, Types.forwardSignature.object());
		specialObjects.set(44, Types.methodSignature.object());
		specialObjects.set(45, Types.messageBundleTree.object());
		specialObjects.set(46, Types.implementationSet.object());

		// Parse nodes types
		specialObjects.set(50, Types.assignmentNode.object());
		specialObjects.set(51, Types.blockNode.object());
		specialObjects.set(52, Types.constantDeclarationNode.object());
		specialObjects.set(53, Types.initializingDeclarationNode.object());
		specialObjects.set(54, Types.labelNode.object());
		specialObjects.set(55, Types.listNode.object());
		specialObjects.set(56, Types.literalNode.object());
		specialObjects.set(57, Types.parseNode.object());
		specialObjects.set(58, Types.referenceNode.object());
		specialObjects.set(59, Types.sendNode.object());
		specialObjects.set(60, Types.superCastNode.object());
		specialObjects.set(61, Types.syntheticConstantNode.object());
		specialObjects.set(62, Types.syntheticDeclarationNode.object());
		specialObjects.set(63, Types.variableDeclarationNode.object());
		specialObjects.set(64, Types.variableUseNode.object());

		// Booleans
		specialObjects.set(70, BooleanDescriptor.objectFromBoolean(true));
		specialObjects.set(71, BooleanDescriptor.objectFromBoolean(false));
	}

	public boolean isCharacterUnderscoreOrSpaceOrOperator (
		final char aCharacter)
	{
		//  Answer whether the given character is an operator character or space or underscore.

		if ((aCharacter == '_'))
		{
			return true;
		}
		if ((aCharacter == ' '))
		{
			return true;
		}
		return AvailScanner.isOperatorCharacter(aCharacter);
	}

	public AvailObject lookupName (
		final AvailObject stringName)
	{
		//  Look up the given string (tuple) in the current module's namespace.  Answer the true name
		//  associated with the string.  A local true name always hides other true names.


		assert stringName.isTuple();
		//  Check if it's already defined somewhere...
		final AvailObject who = module.trueNamesForStringName(stringName);
		AvailObject trueName;
		if ((who.setSize() == 0))
		{
			trueName = CyclicTypeDescriptor.newCyclicTypeWithName(stringName);
			trueName.makeImmutable();
			module.atPrivateNameAdd(stringName, trueName);
			return trueName;
		}
		if ((who.setSize() == 1))
		{
			return who.asTuple().tupleAt(1);
		}
		error("There are multiple true method names that this name could represent.");
		return VoidDescriptor.voidObject();
	}

	public AvailObject methods ()
	{
		//  Answer the global mapping from name (CyclicType) to ImplementationSet.

		return methods;
	}

	public AvailObject methodsAt (
		final AvailObject selector)
	{
		//  Extract the ImplementationSet associated with the given selector (a cyclicType).

		return methods.mapAt(selector);
	}

	public AvailObject module ()
	{
		return module;
	}

	public void module (
		final AvailObject aModule)
	{
		module = aModule;
	}

	public AvailObject moduleAt (
		final AvailObject moduleName)
	{
		assert moduleName.isTuple();
		return modules.mapAt(moduleName);
	}

	public AvailObject nameForType (
		final AvailObject anObjectType)
	{
		//  Answer an Avail string that is the name of the given type.  If this type
		//  has not been given a name (via primitive 68), answer nil.

		return (typeNames.hasKey(anObjectType) ? typeNames.mapAt(anObjectType) : null);
	}

	public AvailObject process ()
	{
		return process;
	}

	public void removeMethodNamedImplementation (
		final AvailObject methodName,
		final AvailObject implementation)
	{
		if (implementation.isForward())
		{
			pendingForwards = pendingForwards.setWithoutElementCanDestroy(implementation, true);
		}
		final AvailObject impSet = methods.mapAt(methodName);
		impSet.removeImplementation(implementation);
		if ((impSet.implementationsTuple().tupleSize() == 0))
		{
			methods = methods.mapWithoutKeyCanDestroy(methodName, true);
			methodNamesForParsing.removeMessageParts(methodName, splitMethodName(methodName));
		}
	}

	public void removeModuleNamed (
		final AvailObject moduleName)
	{
		assert moduleName.isTuple();
		if (! modules.hasKey(moduleName))
		{
			return;
		}
		final AvailObject mod = modules.mapAt(moduleName);
		mod.removeFrom(this);
		modules = modules.mapWithoutKeyCanDestroy(moduleName, true);
	}

	public void resolvedForwardWithName (
		final AvailObject aForward,
		final AvailObject methodName)
	{
		//  The given forward is in the process of being resolved.  A real implementation is about to be
		//  added to the method tables, so remove the forward now.

		if (! hasMethodsAt(methodName))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		final AvailObject impSet = methodsAt(methodName);
		if (! pendingForwards.hasElement(aForward))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		if (! impSet.includes(aForward))
		{
			error("Inconsistent forward declaration handling code");
			return;
		}
		pendingForwards = pendingForwards.setWithoutElementCanDestroy(aForward, true);
		impSet.removeImplementation(aForward);
		module.resolvedForwardWithName(aForward, methodName);
	}

	public AvailObject rootBundleTree ()
	{
		//  Answer the root (unfiltered) bundle tree.

		return methodNamesForParsing;
	}

	public List<AvailObject> specialObjects ()
	{
		return specialObjects;
	}

	public AvailObject splitMethodName (
		final AvailObject methodName)
	{
		//  Break a selector down into the substrings that will be expected as tokens.
		//  Each underscore also becomes an entry.  Spaces are dropped, except as a
		//  means to separate keywords; e.g., '_and a_' matches '1 and a 2'.  I already
		//  canonize selectors with a *single* space between consecutive *alphanumeric*
		//  tokens.  Answer a tuple of Avail strings.

		final AvailObject in = methodName.name();
		if ((in.tupleSize() == 0))
		{
			return TupleDescriptor.empty();
		}
		int inPos = 1;
		List<AvailObject> out;
		out = new ArrayList<AvailObject>(10);
		while (! ((inPos > in.tupleSize()))) {
			final char ch = ((char)(in.tupleAt(inPos).codePoint()));
			if ((ch == ' '))
			{
				if (((out.size() == 0) || isCharacterUnderscoreOrSpaceOrOperator(((char)(in.tupleAt((inPos - 1)).codePoint())))))
				{
					error("Illegally canonized method name (problem before space)");
					return VoidDescriptor.voidObject();
				}
				//  Skip the space.
				++inPos;
				if (((inPos > in.tupleSize()) || isCharacterUnderscoreOrSpaceOrOperator(((char)(in.tupleAt(inPos).codePoint())))))
				{
					error("Illegally canonized method name (problem after space)");
					return VoidDescriptor.voidObject();
				}
			}
			else if (((ch == '_') || AvailScanner.isOperatorCharacter(ch)))
			{
				out.add(in.copyTupleFromToCanDestroy(
					inPos,
					inPos,
					false));
				++inPos;
			}
			else
			{
				final int start = inPos;
				while (! (((inPos > in.tupleSize()) || isCharacterUnderscoreOrSpaceOrOperator(((char)(in.tupleAt(inPos).codePoint()))))))
					++inPos;
				out.add(in.copyTupleFromToCanDestroy(
					start,
					(inPos - 1),
					false));
			}
		}
		return TupleDescriptor.mutableObjectFromArray(out).makeImmutable();
	}

	public boolean supportsPrimitive (
		final short n)
	{
		//  Answer whether the given primitive number is supported.

		return Primitive.byPrimitiveNumber(n) != null;
	}

	public void validateRequiresClausesOfMessageSendArgumentTypes (
		final AvailObject msg,
		final List<AvailObject> argTypes)
	{
		//  Attempt to run the requires clauses applicable to this message send.  Execute the failBlock if
		//  a requires clause returns false.

		final AvailObject implementations = methodsAt(msg);
		final List<AvailObject> matching = implementations.filterByTypes(argTypes);
		if ((matching.size() == 0))
		{
			error("Problem - there were no matching implementations");
			return;
		}
		for (int i = 1, _end1 = matching.size(); i <= _end1; i++)
		{
			final AvailObject imp = matching.get((i - 1));
			if (! imp.isValidForArgumentTypesInterpreter(argTypes, this))
			{
				error("A requires clause rejected the arguments");
				return;
			}
		}
	}

	public AvailObject validateTypesOfMessageSendArgumentTypesIfFail (
		final AvailObject msg,
		final List<AvailObject> argTypes,
		final Continuation1<Generator<String>> failBlock)
	{
		//  Answers the return type.  Fails if no applicable implementation (or more than one).

		final AvailObject impSet = methodsAt(msg);
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
		initializeSpecialObjects();
		process = AvailObject.newIndexedDescriptor(0, ProcessDescriptor.mutableDescriptor());
		process.priority(50);
		process.continuation(VoidDescriptor.voidObject());
		process.executionMode(ExecutionMode.noDebug);
		process.executionState(ExecutionState.running);
		process.interruptRequestFlag(InterruptRequestFlag.noInterrupt);
		process.breakpointBlock(VoidDescriptor.voidObject());
		process.processGlobals(MapDescriptor.empty());
	}

	AvailObject typeNames ()
	{
		return typeNames;
	}

	void typeNames (AvailObject aMap)
	{
		typeNames = aMap;
	}

	public void primitiveResult (AvailObject result)
	{
		primitiveResult = result;
	}

	public AvailObject primitiveResult()
	{
		return primitiveResult;
	}

	public Result attemptPrimitive (
		short primitiveNumber,
		List<AvailObject> args)
	{
		//  Invoke an Avail primitive.  The primitive number and arguments are passed.
		//  If the primitive fails, return primitiveFailed immediately.  If the primitive causes
		//  the continuation to change (e.g., through block invocation, continuation restart,
		//  exception throwing, etc), answer continuationChanged.  Otherwise the primitive
		//  succeeded, and we simply store the resulting value in args.result and return
		//  primitiveSucceeded.

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
		short primitiveNumber,
		List<AvailObject> args)
	{
		//TODO: [MvG] Phase this out without ever implementing it.
		error("Can't call back to Smalltalk -- not supported.");
		return Result.FAILURE;
	}
}
