/**
 * com.avail/AvailRuntime.java
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

package com.avail;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.beans.MethodDescriptor;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.descriptor.*;
import com.avail.interpreter.Primitive;

/**
 * An {@code AvailRuntime} comprises the {@linkplain ModuleDescriptor
 * modules}, {@linkplain MethodDescriptor methods}, and {@linkplain
 * #specialObject(int) special objects} that define an Avail system. It also
 * manages global resources, such as file connections.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class AvailRuntime
{

	/**
	 * The {@linkplain ModuleNameResolver module name resolver} that this
	 * {@linkplain AvailRuntime runtime} should use to resolve unqualified
	 * {@linkplain ModuleDescriptor module} names.
	 */
	private final @NotNull ModuleNameResolver moduleNameResolver;

	/**
	 * Answer the {@linkplain ModuleNameResolver module name resolver} that this
	 * {@linkplain AvailRuntime runtime} should use to resolve unqualified
	 * {@linkplain ModuleDescriptor module} names.
	 *
	 * @return A {@linkplain ModuleNameResolver module name resolver}.
	 */
	public @NotNull ModuleNameResolver moduleNameResolver ()
	{
		return moduleNameResolver;
	}

	/**
	 * Answer the Avail {@linkplain ModuleRoots module roots}.
	 *
	 * @return The Avail {@linkplain ModuleRoots module roots}.
	 */
	@ThreadSafe
	public @NotNull ModuleRoots moduleRoots ()
	{
		return moduleNameResolver.moduleRoots();
	}

	/**
	 * Construct a new {@link AvailRuntime}.
	 *
	 * @param moduleNameResolver
	 *        The {@linkplain ModuleNameResolver module name resolver} that this
	 *        {@linkplain AvailRuntime runtime} should use to resolve
	 *        unqualified {@linkplain ModuleDescriptor module} names.
	 */
	public AvailRuntime (final @NotNull ModuleNameResolver moduleNameResolver)
	{
		this.moduleNameResolver = moduleNameResolver;
	}

	/**
	 * The {@linkplain ReentrantReadWriteLock lock} that protects the
	 * {@linkplain AvailRuntime runtime} data structures against dangerous
	 * concurrent access.
	 */
	private final @NotNull ReentrantReadWriteLock runtimeLock =
		new ReentrantReadWriteLock();

	/**
	 * The {@linkplain AvailObject special objects} of the {@linkplain
	 * AvailRuntime runtime}.
	 */
	private final @NotNull AvailObject[] specialObjects = new AvailObject[72];

	/*
	 * Set up the special objects.
	 */
	{
		// Basic types
		specialObjects[1] = ALL.o();
		specialObjects[2] = BOOLEAN_TYPE.o();
		specialObjects[3] = CHARACTER.o();
		specialObjects[4] = CLOSURE.o();
		specialObjects[5] = CLOSURE_TYPE.o();
		specialObjects[6] = COMPILED_CODE.o();
		specialObjects[7] = CONTAINER.o();
		specialObjects[8] = CONTAINER_TYPE.o();
		specialObjects[9] = CONTINUATION.o();
		specialObjects[10] = CONTINUATION_TYPE.o();
		specialObjects[11] = CYCLIC_TYPE.o();
		specialObjects[12] = DOUBLE.o();
		specialObjects[13] =
			IntegerRangeTypeDescriptor.extendedIntegers().makeImmutable();
		specialObjects[14] = FALSE_TYPE.o();
		specialObjects[15] = FLOAT.o();
		specialObjects[16] = GENERALIZED_CLOSURE_TYPE.o();
		specialObjects[17] =
			IntegerRangeTypeDescriptor.integers().makeImmutable();
		specialObjects[18] = INTEGER_TYPE.o();
		specialObjects[19] = MAP_TYPE.o();
		specialObjects[20] = META.o();
		specialObjects[21] = ObjectTypeDescriptor.objectTypeFromMap(
			MapDescriptor.empty()).type().type().makeImmutable();
		specialObjects[22] = ObjectTypeDescriptor.objectTypeFromMap(
			MapDescriptor.empty()).type().type().type().makeImmutable();
		specialObjects[23] = ObjectTypeDescriptor.objectTypeFromMap(
			MapDescriptor.empty()).type().makeImmutable();
		specialObjects[24] = PRIMITIVE_TYPE.o();
		specialObjects[25] = PROCESS.o();
		specialObjects[26] = SetTypeDescriptor.setTypeForSizesContentType(
			IntegerRangeTypeDescriptor.wholeNumbers(), ALL.o())
				.makeImmutable();
		specialObjects[27] = SET_TYPE.o();
		specialObjects[28] = TupleTypeDescriptor.stringTupleType();
		specialObjects[29] = TERMINATES.o();
		specialObjects[30] = TERMINATES_TYPE.o();
		specialObjects[31] = TRUE_TYPE.o();
		specialObjects[32] =
			TupleTypeDescriptor.mostGeneralTupleType().makeImmutable();
		specialObjects[33] = TUPLE_TYPE.o();
		specialObjects[34] = TYPE.o();
		specialObjects[35] = VOID_TYPE.o();

		// Code reflection
		specialObjects[40] = MESSAGE_BUNDLE.o();
		specialObjects[41] = SIGNATURE.o();
		specialObjects[42] = ABSTRACT_SIGNATURE.o();
		specialObjects[43] = FORWARD_SIGNATURE.o();
		specialObjects[44] = METHOD_SIGNATURE.o();
		specialObjects[45] = MESSAGE_BUNDLE_TREE.o();
		specialObjects[46] = IMPLEMENTATION_SET.o();

		// Parse nodes types
		specialObjects[50] = PARSE_NODE.o();
		specialObjects[51] = MARKER_NODE.o();
		specialObjects[52] = EXPRESSION_NODE.o();
		specialObjects[53] = ASSIGNMENT_NODE.o();
		specialObjects[54] = BLOCK_NODE.o();
		specialObjects[55] = LITERAL_NODE.o();
		specialObjects[56] = REFERENCE_NODE.o();
		specialObjects[57] = SEND_NODE.o();
		specialObjects[58] = SUPER_CAST_NODE.o();
		specialObjects[59] = TUPLE_NODE.o();
		specialObjects[60] = VARIABLE_USE_NODE.o();
		specialObjects[61] = DECLARATION_NODE.o();
		specialObjects[62] = ARGUMENT_NODE.o();
		specialObjects[63] = LABEL_NODE.o();
		specialObjects[64] = LOCAL_VARIABLE_NODE.o();
		specialObjects[65] = LOCAL_CONSTANT_NODE.o();
		specialObjects[66] = MODULE_VARIABLE_NODE.o();
		specialObjects[67] = MODULE_CONSTANT_NODE.o();

		// Booleans
		specialObjects[70] = BooleanDescriptor.objectFrom(true);
		specialObjects[71] = BooleanDescriptor.objectFrom(false);
	}

	/**
	 * Answer the {@linkplain AvailObject special object} with the specified
	 * ordinal.
	 *
	 * @param ordinal The {@linkplain AvailObject special object} with the
	 *                specified ordinal.
	 * @return An {@link AvailObject}.
	 * @throws ArrayIndexOutOfBoundsException
	 *         If the ordinal is out of bounds.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@ThreadSafe
	public AvailObject specialObject (final int ordinal)
		throws ArrayIndexOutOfBoundsException
	{
		return specialObjects[ordinal];
	}

	/**
	 * The loaded Avail {@linkplain ModuleDescriptor modules}: a
	 * {@linkplain MapDescriptor map} from {@linkplain TupleDescriptor module
	 * names} to {@linkplain ModuleDescriptor modules}.
	 */
	private @NotNull AvailObject modules = MapDescriptor.empty();

	/**
	 * Add the specified {@linkplain ModuleDescriptor module} to the
	 * {@linkplain AvailRuntime runtime}.
	 *
	 * @param aModule A {@linkplain ModuleDescriptor module}.
	 */
	@ThreadSafe
	public void addModule (final @NotNull AvailObject aModule)
	{
		runtimeLock.writeLock().lock();
		try
		{
			// Add all visible message bundles to the root message bundle tree.
			for (final AvailObject name : aModule.visibleNames())
			{
				assert name.isCyclicType();
				final MessageSplitter splitter =
					new MessageSplitter(name.name());
				final AvailObject messageParts = splitter.messageParts();
				final AvailObject instructions = splitter.instructionsTuple();
				final AvailObject rootBundle = rootBundleTree.includeBundle(
					MessageBundleDescriptor.newBundle(
						name,
						messageParts,
						instructions));
				final AvailObject bundle =
					aModule.filteredBundleTree().includeBundle(
						MessageBundleDescriptor.newBundle(
							name,
							messageParts,
							instructions));
				rootBundle.addRestrictions(bundle.restrictions());
			}

			// Finally add the module to the map of loaded modules.
			modules = modules.mapAtPuttingCanDestroy(
				aModule.name(), aModule, true);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Does the {@linkplain AvailRuntime runtime} define a {@linkplain
	 * ModuleDescriptor module} with the specified {@linkplain
	 * TupleDescriptor name}?
	 *
	 * @param moduleName A {@linkplain TupleDescriptor name}.
	 * @return {@code true} if the {@linkplain AvailRuntime runtime} defines a
	 *          {@linkplain ModuleDescriptor module} with the specified
	 *          {@linkplain TupleDescriptor name}, {@code false} otherwise.
	 */
	@ThreadSafe
	public boolean includesModuleNamed (final @NotNull AvailObject moduleName)
	{
		assert moduleName.isString();

		runtimeLock.readLock().lock();
		try
		{
			return modules.hasKey(moduleName);
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Answer the {@linkplain ModuleDescriptor module} with the specified
	 * {@linkplain TupleDescriptor name}.
	 *
	 * @param moduleName A {@linkplain TupleDescriptor name}.
	 * @return A {@linkplain ModuleDescriptor module}.
	 */
	@ThreadSafe
	public @NotNull AvailObject moduleAt (final @NotNull AvailObject moduleName)
	{
		assert moduleName.isString();

		runtimeLock.readLock().lock();
		try
		{
			assert includesModuleNamed(moduleName);
			return modules.mapAt(moduleName);
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * The {@linkplain MethodDescriptor methods} currently known to the
	 * {@linkplain AvailRuntime runtime}: a {@linkplain MapDescriptor map} from
	 * {@linkplain CyclicTypeDescriptor method name} to {@linkplain
	 * ImplementationSetDescriptor implementation set}.
	 */
	private @NotNull AvailObject methods = MapDescriptor.empty();

	/**
	 * Are there any {@linkplain ImplementationSetDescriptor methods} bound to
	 * the specified {@linkplain CyclicTypeDescriptor selector}?
	 *
	 * @param selector A {@linkplain CyclicTypeDescriptor selector}.
	 * @return {@code true} if there are {@linkplain ImplementationSetDescriptor
	 *         methods} bound to the specified {@linkplain CyclicTypeDescriptor
	 *         selector}, {@code false} otherwise.
	 */
	@ThreadSafe
	public boolean hasMethodsAt (final @NotNull AvailObject selector)
	{
		assert selector.isCyclicType();

		runtimeLock.readLock().lock();
		try
		{
			return methods.hasKey(selector);
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Answer the {@linkplain ImplementationSetDescriptor implementation set}
	 * bound to the specified {@linkplain CyclicTypeDescriptor method name}.
	 * If necessary, then create a new implementation set and bind it.
	 *
	 * @param methodName A {@linkplain CyclicTypeDescriptor method name}.
	 * @return An {@linkplain ImplementationSetDescriptor implementation set}.
	 */
	@ThreadSafe
	public @NotNull AvailObject implementationSetFor (
		final @NotNull AvailObject methodName)
	{
		runtimeLock.writeLock().lock();
		try
		{
			final AvailObject implementationSet;
			if (methods.hasKey(methodName))
			{
				implementationSet = methods.mapAt(methodName);
			}
			else
			{
				implementationSet = ImplementationSetDescriptor
					.newImplementationSetWithName(methodName);
				methods = methods.mapAtPuttingCanDestroy(
					methodName, implementationSet, true);
			}
			return implementationSet;
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Answer the {@linkplain ImplementationSetDescriptor implementation set}
	 * bound to the specified {@linkplain CyclicTypeDescriptor selector}.
	 *
	 * @param selector A {@linkplain CyclicTypeDescriptor selector}.
	 * @return An {@linkplain ImplementationSetDescriptor implementation set}.
	 */
	@ThreadSafe
	public @NotNull AvailObject methodsAt (final @NotNull AvailObject selector)
	{
		assert selector.isCyclicType();

		runtimeLock.readLock().lock();
		try
		{
			return methods.mapAt(selector);
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Unbind the specified implementation from the {@linkplain
	 * CyclicTypeDescriptor selector}. If no implementations remain in the
	 * {@linkplain ImplementationSetDescriptor implementation set}, then
	 * forget the selector from the method dictionary and the {@linkplain
	 * #rootBundleTree() root message bundle tree}.
	 *
	 * @param selector A {@linkplain CyclicTypeDescriptor selector}.
	 * @param implementation An implementation.
	 */
	@ThreadSafe
	public void removeMethod (
		final @NotNull AvailObject selector,
		final @NotNull AvailObject implementation)
	{
		assert selector.isCyclicType();

		runtimeLock.writeLock().lock();
		try
		{
			assert methods.hasKey(selector);
			final AvailObject implementationSet = methods.mapAt(selector);
			implementationSet.removeImplementation(implementation);
			if (implementationSet.implementationsTuple().tupleSize() == 0)
			{
				methods = methods.mapWithoutKeyCanDestroy(selector, true);
				final MessageSplitter splitter = new MessageSplitter(selector.name());
				rootBundleTree.removeBundle(
					MessageBundleDescriptor.newBundle(
						selector,
						splitter.messageParts(),
						splitter.instructionsTuple()));
			}
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * The root {@linkplain MessageBundleTreeDescriptor message bundle tree}. It
	 * contains the {@linkplain MessageBundleDescriptor message bundles}
	 * exported by all loaded {@linkplain ModuleDescriptor modules}.
	 */
	private @NotNull
	final AvailObject rootBundleTree =
		UnexpandedMessageBundleTreeDescriptor.newPc(1);

	/**
	 * Answer a copy of the root {@linkplain MessageBundleTreeDescriptor message
	 * bundle tree}.
	 *
	 * @return A {@linkplain MessageBundleTreeDescriptor message bundle tree}
	 *         that contains the {@linkplain MessageBundleDescriptor message
	 *         bundles} exported by all loaded {@linkplain ModuleDescriptor
	 *         modules}.
	 */
	@ThreadSafe
	public @NotNull AvailObject rootBundleTree ()
	{
		runtimeLock.readLock().lock();
		try
		{
			final AvailObject copy =
				UnexpandedMessageBundleTreeDescriptor.newPc(1);
			rootBundleTree.copyToRestrictedTo(copy, methods.keysAsSet());
			return copy;
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * A {@linkplain MapDescriptor map} from {@linkplain ObjectTypeDescriptor
	 * user-defined object types} to user-assigned names.
	 */
	private @NotNull
	final AvailObject typeNames = MapDescriptor.empty();

	/**
	 * Answer the user-assigned name of the specified {@linkplain
	 * ObjectTypeDescriptor user-defined object type}.
	 *
	 * @param anObjectType A {@linkplain ObjectTypeDescriptor user-defined
	 *                     object type}.
	 * @return The name of the {@linkplain ObjectTypeDescriptor user-defined
	 *         object type}, or {@code null} if the user-defined object type
	 *         has not been assigned a name via {@linkplain
	 *         Primitive#prim68_RecordNewTypeName_userType_name primitive 68}.
	 */
	public AvailObject nameForType (final @NotNull AvailObject anObjectType)
	{
		return typeNames.hasKey(anObjectType)
			? typeNames.mapAt(anObjectType)
			: null;
	}

	/**
	 * Assign a name to the specified {@linkplain ObjectTypeDescriptor
	 * user-defined object type}.
	 *
	 * @param anObjectType A {@linkplain ObjectTypeDescriptor user-defined
	 *                     object type}.
	 * @param aString A name.
	 */
	public void setNameForType (
		final @NotNull AvailObject anObjectType,
		final @NotNull AvailObject aString)
	{
		assert aString.isString();
		typeNames.mapAtPuttingCanDestroy(anObjectType, aString, true);
	}

	/**
	 * A mapping from {@linkplain CyclicTypeDescriptor keys} to {@link
	 * RandomAccessFile}s open for reading.
	 */
	private final Map<AvailObject, RandomAccessFile> openReadableFiles =
		new HashMap<AvailObject, RandomAccessFile>();

	/**
	 * A mapping from {@linkplain CyclicTypeDescriptor keys} to {@link
	 * RandomAccessFile}s open for writing.
	 */
	private final Map<AvailObject, RandomAccessFile> openWritableFiles =
		new HashMap<AvailObject, RandomAccessFile>();

	/**
	 * Answer the open readable {@linkplain RandomAccessFile file} associated
	 * with the specified {@linkplain CyclicTypeDescriptor handle}.
	 *
	 * @param handle A {@linkplain CyclicTypeDescriptor handle}.
	 * @return The open {@linkplain RandomAccessFile file} associated with the
	 *         {@linkplain CyclicTypeDescriptor cycle}, or {@code null} if no
	 *         such association exists.
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
}
