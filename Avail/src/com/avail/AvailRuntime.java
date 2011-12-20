/**
 * AvailRuntime.java
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

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.beans.MethodDescriptor;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.descriptor.*;

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
	 * The {@linkplain ClassLoader class loader} that should be used to locate
	 * and load Java {@linkplain Class classes}.
	 */
	private final @NotNull ClassLoader classLoader;

	/**
	 * Answer the {@linkplain ClassLoader class loader} that should be used to
	 * locate and load Java {@linkplain Class classes}.
	 *
	 * @return A class loader.
	 */
	public @NotNull ClassLoader classLoader ()
	{
		// For now, just use our own class's class loader. In the future, the
		// AvailRuntime could be created with a specified class loader.
		return getClass().getClassLoader();
	}

	/**
	 * Construct a new {@link AvailRuntime}.
	 *
	 * @param moduleNameResolver
	 *        The {@linkplain ModuleNameResolver module name resolver} that this
	 *        {@linkplain AvailRuntime runtime} should use to resolve
	 *        unqualified {@linkplain ModuleDescriptor module} names.
	 * @param classLoader
	 *        The {@linkplain ClassLoader class loader} that should be used to
	 *        locate and dynamically load Java {@linkplain Class classes}.
	 */
	public AvailRuntime (
		final @NotNull ModuleNameResolver moduleNameResolver,
		final @NotNull ClassLoader classLoader)
	{
		this.moduleNameResolver = moduleNameResolver;
		this.classLoader = classLoader;
	}

	/**
	 * Construct a new {@link AvailRuntime}. Use the {@linkplain ClassLoader
	 * class loader} that loaded this {@linkplain Class class}.
	 *
	 * @param moduleNameResolver
	 *        The {@linkplain ModuleNameResolver module name resolver} that this
	 *        {@linkplain AvailRuntime runtime} should use to resolve
	 *        unqualified {@linkplain ModuleDescriptor module} names.
	 */
	public AvailRuntime (final @NotNull ModuleNameResolver moduleNameResolver)
	{
		this(moduleNameResolver, AvailRuntime.class.getClassLoader());
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
	private final @NotNull AvailObject[] specialObjects = new AvailObject[100];

	/**
	 * Answer the {@linkplain AvailObject special objects} of the {@linkplain
	 * AvailRuntime runtime} as an {@linkplain
	 * Collections#unmodifiableList(List) immutable} {@linkplain List list}.
	 *
	 * @return The special objects.
	 */
	public @NotNull List<AvailObject> specialObjects ()
	{
		return Collections.unmodifiableList(Arrays.asList(specialObjects));
	}

	/*
	 * Set up the special objects.
	 */
	{
		// Basic types
		specialObjects[1] = ANY.o();
		specialObjects[2] = EnumerationTypeDescriptor.booleanObject();
		specialObjects[3] = CHARACTER.o();
		specialObjects[4] = FunctionTypeDescriptor.mostGeneralType();
		specialObjects[5] = FunctionTypeDescriptor.meta();
		specialObjects[6] = CompiledCodeTypeDescriptor.mostGeneralType();
		specialObjects[7] = ContainerTypeDescriptor.mostGeneralType();
		specialObjects[8] = ContainerTypeDescriptor.meta();
		specialObjects[9] = ContinuationTypeDescriptor.mostGeneralType();
		specialObjects[10] = ContinuationTypeDescriptor.meta();
		specialObjects[11] = ATOM.o();
		specialObjects[12] = DOUBLE.o();
		specialObjects[13] = IntegerRangeTypeDescriptor.extendedIntegers();
		specialObjects[14] = InstanceTypeDescriptor.on(
			AtomDescriptor.falseObject());
		specialObjects[15] = FLOAT.o();
		//16
		specialObjects[17] = IntegerRangeTypeDescriptor.integers();
		specialObjects[18] = IntegerRangeTypeDescriptor.meta();
		specialObjects[19] = MapTypeDescriptor.meta();
		specialObjects[20] = META.o();
		specialObjects[21] = EnumerationMetaDescriptor.mostGeneralType();
		specialObjects[22] = ObjectTypeDescriptor.mostGeneralType();
		specialObjects[23] = ObjectTypeDescriptor.meta();
		//24 (PRIMITIVE_TYPE)
		specialObjects[25] = PROCESS.o();
		specialObjects[26] = SetTypeDescriptor.mostGeneralType();
		specialObjects[27] = SetTypeDescriptor.meta();
		specialObjects[28] = TupleTypeDescriptor.stringTupleType();
		specialObjects[29] = BottomTypeDescriptor.bottom();
		specialObjects[30] = InstanceTypeDescriptor.on(
			BottomTypeDescriptor.bottom());
		specialObjects[31] = InstanceTypeDescriptor.on(
			AtomDescriptor.trueObject());
		specialObjects[32] = TupleTypeDescriptor.mostGeneralType();
		specialObjects[33] = TupleTypeDescriptor.meta();
		specialObjects[34] = TYPE.o();
		specialObjects[35] = TOP.o();
		specialObjects[36] = IntegerRangeTypeDescriptor.wholeNumbers();
		specialObjects[37] = IntegerRangeTypeDescriptor.naturalNumbers();
		specialObjects[38] = IntegerRangeTypeDescriptor.characterCodePoints();

		// Code reflection
		specialObjects[40] = MESSAGE_BUNDLE.o();
		specialObjects[41] = SIGNATURE.o();
		specialObjects[42] = ABSTRACT_SIGNATURE.o();
		specialObjects[43] = FORWARD_SIGNATURE.o();
		specialObjects[44] = METHOD_SIGNATURE.o();
		specialObjects[45] = MESSAGE_BUNDLE_TREE.o();
		specialObjects[46] = IMPLEMENTATION_SET.o();

		// Parse nodes types
		specialObjects[50] = PARSE_NODE.mostGeneralType();
		specialObjects[51] = MARKER_NODE.mostGeneralType();
		specialObjects[52] = EXPRESSION_NODE.mostGeneralType();
		specialObjects[53] = ASSIGNMENT_NODE.mostGeneralType();
		specialObjects[54] = BLOCK_NODE.mostGeneralType();
		specialObjects[55] = LITERAL_NODE.mostGeneralType();
		specialObjects[56] = REFERENCE_NODE.mostGeneralType();
		specialObjects[57] = SEND_NODE.mostGeneralType();
		specialObjects[58] = SUPER_CAST_NODE.mostGeneralType();
		specialObjects[59] = TUPLE_NODE.mostGeneralType();
		specialObjects[60] = VARIABLE_USE_NODE.mostGeneralType();
		specialObjects[61] = DECLARATION_NODE.mostGeneralType();
		specialObjects[62] = ARGUMENT_NODE.mostGeneralType();
		specialObjects[63] = LABEL_NODE.mostGeneralType();
		specialObjects[64] = LOCAL_VARIABLE_NODE.mostGeneralType();
		specialObjects[65] = LOCAL_CONSTANT_NODE.mostGeneralType();
		specialObjects[66] = MODULE_VARIABLE_NODE.mostGeneralType();
		specialObjects[67] = MODULE_CONSTANT_NODE.mostGeneralType();
		specialObjects[68] = PRIMITIVE_FAILURE_REASON_NODE.mostGeneralType();

		// Special values.
		specialObjects[70] = AtomDescriptor.trueObject();
		specialObjects[71] = AtomDescriptor.falseObject();

		// Bootstrap helpers
		// tuple of string...
		specialObjects[72] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				TupleTypeDescriptor.stringTupleType());
		// tuple of type...
		specialObjects[73] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				TYPE.o());
		// tuple of set of string...
		specialObjects[74] =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleDescriptor.empty(),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleTypeDescriptor.stringTupleType()));
		// set of string...
		specialObjects[75] =
			SetTypeDescriptor.setTypeForSizesContentType(
				IntegerRangeTypeDescriptor.wholeNumbers(),
				TupleTypeDescriptor.stringTupleType());
		// function taking natural number and returning bottom...
		specialObjects[76] =
			FunctionTypeDescriptor.create(
				TupleDescriptor.from(
					IntegerRangeTypeDescriptor.naturalNumbers()),
				BottomTypeDescriptor.bottom());


		// Special values.
		specialObjects[78] = InfinityDescriptor.negativeInfinity();
		specialObjects[79] = InfinityDescriptor.positiveInfinity();

		// Pojo support.
		specialObjects[80] = PojoTypeDescriptor.mostGeneralType();
		specialObjects[81] = PojoTypeDescriptor.mostSpecificType();
		specialObjects[82] = PojoDescriptor.nullObject();
		specialObjects[83] = PojoSelfTypeDescriptor.selfType();

		for (final AvailObject object : specialObjects)
		{
			if (object != null)
			{
				object.makeImmutable();
			}
		}
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
				assert name.isAtom();
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
				rootBundle.addRestrictions(bundle.grammaticalRestrictions());
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
	 * {@linkplain AtomDescriptor method name} to {@linkplain
	 * ImplementationSetDescriptor implementation set}.
	 */
	private @NotNull AvailObject methods = MapDescriptor.empty();

	/**
	 * Are there any {@linkplain ImplementationSetDescriptor methods} bound to
	 * the specified {@linkplain AtomDescriptor selector}?
	 *
	 * @param selector A {@linkplain AtomDescriptor selector}.
	 * @return {@code true} if there are {@linkplain ImplementationSetDescriptor
	 *         methods} bound to the specified {@linkplain
	 *         AtomDescriptor selector}, {@code false} otherwise.
	 */
	@ThreadSafe
	public boolean hasMethodsAt (final @NotNull AvailObject selector)
	{
		assert selector.isAtom();

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
	 * bound to the specified {@linkplain AtomDescriptor method name}.
	 * If necessary, then create a new implementation set and bind it.
	 *
	 * @param methodName A {@linkplain AtomDescriptor method name}.
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
				implementationSet =
					ImplementationSetDescriptor.newImplementationSetWithName(
						methodName);
				methods = methods.mapAtPuttingCanDestroy(
					methodName,
					implementationSet,
					true);
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
	 * bound to the specified {@linkplain AtomDescriptor selector}.  If
	 * there is no implementation set with that selector, answer {@linkplain
	 * NullDescriptor the null object}.
	 *
	 * @param selector
	 *            A {@linkplain AtomDescriptor selector}.
	 * @return
	 *            An {@linkplain ImplementationSetDescriptor implementation set}
	 *            or {@linkplain NullDescriptor the null object}.
	 */
	@ThreadSafe
	public @NotNull AvailObject methodsAt (final @NotNull AvailObject selector)
	{
		assert selector.isAtom();

		runtimeLock.readLock().lock();
		try
		{
			if (methods.hasKey(selector))
			{
				return methods.mapAt(selector);
			}
			return NullDescriptor.nullObject();
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Unbind the specified implementation from the {@linkplain
	 * AtomDescriptor selector}. If no implementations remain in the
	 * {@linkplain ImplementationSetDescriptor implementation set}, then
	 * forget the selector from the method dictionary and the {@linkplain
	 * #rootBundleTree() root message bundle tree}.
	 *
	 * @param selector A {@linkplain AtomDescriptor selector}.
	 * @param implementation An implementation.
	 */
	@ThreadSafe
	public void removeMethod (
		final @NotNull AvailObject selector,
		final @NotNull AvailObject implementation)
	{
		assert selector.isAtom();

		runtimeLock.writeLock().lock();
		try
		{
			assert methods.hasKey(selector);
			final AvailObject implementationSet = methods.mapAt(selector);
			implementationSet.removeImplementation(implementation);
			if (implementationSet.isImplementationSetEmpty())
			{
				methods = methods.mapWithoutKeyCanDestroy(selector, true);
				final MessageSplitter splitter =
					new MessageSplitter(selector.name());
				rootBundleTree.removeBundle(
					MessageBundleDescriptor.newBundle(
						selector,
						splitter.messageParts(),
						splitter.instructionsTuple()));
			}
			if (implementationSet.isImplementationSetEmpty())
			{
				methods = methods.mapWithoutKeyCanDestroy(
					selector,
					true);
			}
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * A {@linkplain MapDescriptor map} from MessageBundle to a {@linkplain
	 * TupleDescriptor tuple} of {@linkplain SetDescriptor sets} of {@linkplain
	 * AtomDescriptor atoms} (the messages' true names).
	 */
	private @NotNull AvailObject grammaticalRestrictions =
		MapDescriptor.empty();

	/**
	 * Answer the {@linkplain MapDescriptor map} of {@linkplain
	 * MessageBundleDescriptor message bundle} grammatical restrictions.
	 *
	 * @return The restrictions map.
	 */
	private @NotNull AvailObject grammaticalRestrictions ()
	{
		return grammaticalRestrictions;
	}

	/**
	 * Add the specified grammatical restrictions for the specified message
	 * bundle.
	 *
	 * @param messageBundle
	 *            The message bundle to be restricted.
	 * @param restrictionsToAdd
	 *            The grammatical restrictions to associate with the message
	 *            bundle.
	 */
	private @NotNull void addGrammaticalRestriction (
		final @NotNull AvailObject messageBundle,
		final @NotNull AvailObject restrictionsToAdd)
	{
		runtimeLock.writeLock().lock();
		try
		{
			AvailObject tuple;
			if (grammaticalRestrictions.hasKey(messageBundle))
			{
				tuple = grammaticalRestrictions.mapAt(messageBundle);
				assert tuple.tupleSize() == restrictionsToAdd.tupleSize();
				for (int i = tuple.tupleSize(); i > 0; i--)
				{
					tuple = tuple.tupleAtPuttingCanDestroy(
						i,
						tuple.tupleAt(i).setUnionCanDestroy(
							restrictionsToAdd.tupleAt(i),
							true),
						true);
				}
			}
			else
			{
				tuple = restrictionsToAdd;
			}
			grammaticalRestrictions =
				grammaticalRestrictions.mapAtPuttingCanDestroy(
					messageBundle,
					tuple,
					true);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Remove the specified grammatical restrictions from the specified message
	 * bundle.
	 *
	 * @param messageBundle
	 *            The message bundle to be restricted.
	 * @param restrictionsToRemove
	 *            The restrictions to dissociate from the message bundle.
	 */
	private @NotNull void removeGrammaticalRestriction (
		final @NotNull AvailObject messageBundle,
		final @NotNull AvailObject restrictionsToRemove)
	{
		runtimeLock.writeLock().lock();
		try
		{
			if (!grammaticalRestrictions.hasKey(messageBundle))
			{
				return;
			}
			AvailObject tuple = grammaticalRestrictions.mapAt(messageBundle);
			assert tuple.tupleSize() == restrictionsToRemove.tupleSize();
			boolean allEmpty = true;
			for (int i = tuple.tupleSize(); i > 0; i--)
			{
				final AvailObject difference = tuple.tupleAt(i).setMinusCanDestroy(
					restrictionsToRemove.tupleAt(i),
					true);
				tuple = tuple.tupleAtPuttingCanDestroy(i, difference, true);
				if (difference.setSize() != 0)
				{
					allEmpty = false;
				}
			}
			if (allEmpty)
			{
				grammaticalRestrictions =
					grammaticalRestrictions.mapWithoutKeyCanDestroy(
						messageBundle,
						true);
			}
			else
			{
				grammaticalRestrictions =
					grammaticalRestrictions.mapAtPuttingCanDestroy(
						messageBundle,
						tuple,
						true);
			}
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}


	/**
	 * Add a type restriction to the implementation set associated with the
	 * given method name.
	 *
	 * @param methodName
	 *            The method name, an {@linkplain AtomDescriptor atom}.
	 * @param typeRestrictionFunction
	 *            A {@linkplain FunctionDescriptor function} that validates the
	 *            static types of arguments at call sites.
	 */
	public void addTypeRestriction (
		final AvailObject methodName,
		final AvailObject typeRestrictionFunction)
	{
		assert methodName.isAtom();
		assert typeRestrictionFunction.isFunction();

		runtimeLock.writeLock().lock();
		try
		{
			final AvailObject impSet = implementationSetFor(methodName);
			impSet.addTypeRestriction(typeRestrictionFunction);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}


	/**
	 * Remove a type restriction from the implementation set associated with the
	 * given method name.
	 *
	 * @param methodName
	 *            The method name, an {@linkplain AtomDescriptor atom}.
	 * @param typeRestrictionFunction
	 *            A {@linkplain FunctionDescriptor function} that validates the
	 *            static types of arguments at call sites.
	 */
	public void removeTypeRestriction (
		final AvailObject methodName,
		final AvailObject typeRestrictionFunction)
	{
		assert methodName.isAtom();
		assert typeRestrictionFunction.isFunction();

		runtimeLock.writeLock().lock();
		try
		{
			final AvailObject impSet = implementationSetFor(methodName);
			impSet.removeTypeRestriction(typeRestrictionFunction);
			if (impSet.isImplementationSetEmpty())
			{
				methods = methods.mapWithoutKeyCanDestroy(methodName, true);
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
	final AvailObject rootBundleTree = MessageBundleTreeDescriptor.newPc(1);

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
			final AvailObject copy = MessageBundleTreeDescriptor.newPc(1);
			rootBundleTree.copyToRestrictedTo(copy, methods.keysAsSet());
			return copy;
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * A mapping from {@linkplain AtomDescriptor keys} to {@link
	 * RandomAccessFile}s open for reading.
	 */
	private final Map<AvailObject, RandomAccessFile> openReadableFiles =
		new HashMap<AvailObject, RandomAccessFile>();

	/**
	 * A mapping from {@linkplain AtomDescriptor keys} to {@link
	 * RandomAccessFile}s open for writing.
	 */
	private final Map<AvailObject, RandomAccessFile> openWritableFiles =
		new HashMap<AvailObject, RandomAccessFile>();

	/**
	 * Answer the open readable {@linkplain RandomAccessFile file} associated
	 * with the specified {@linkplain AtomDescriptor handle}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 * @return The open {@linkplain RandomAccessFile file} associated with the
	 *         {@linkplain AtomDescriptor atom}, or {@code null} if no such
	 *         association exists.
	 */
	public RandomAccessFile getReadableFile (final @NotNull AvailObject handle)
	{
		assert handle.isAtom();
		return openReadableFiles.get(handle);
	}

	/**
	 * Associate the specified {@linkplain AtomDescriptor handle} with
	 * the open readable {@linkplain RandomAccessFile file}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 * @param file An open {@linkplain RandomAccessFile file}.
	 */
	public void putReadableFile (
		final @NotNull AvailObject handle,
		final @NotNull RandomAccessFile file)
	{
		assert handle.isAtom();
		openReadableFiles.put(handle, file);
	}

	/**
	 * Remove the association between the specified {@linkplain
	 * AtomDescriptor handle} and its open readable {@linkplain
	 * RandomAccessFile file}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 */
	public void forgetReadableFile (final @NotNull AvailObject handle)
	{
		assert handle.isAtom();
		openReadableFiles.remove(handle);
	}

	/**
	 * Answer the open writable {@linkplain RandomAccessFile file} associated
	 * with the specified {@linkplain AtomDescriptor handle}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 * @return The open {@linkplain RandomAccessFile file} associated with the
	 *         {@linkplain AtomDescriptor atom}, or {@code null} if no such
	 *         association exists.
	 */
	public RandomAccessFile getWritableFile (final @NotNull AvailObject handle)
	{
		assert handle.isAtom();
		return openWritableFiles.get(handle);
	}

	/**
	 * Associate the specified {@linkplain AtomDescriptor handle} with
	 * the open writable {@linkplain RandomAccessFile file}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 * @param file An open {@linkplain RandomAccessFile file}.
	 */
	public void putWritableFile (
		final @NotNull AvailObject handle,
		final @NotNull RandomAccessFile file)
	{
		assert handle.isAtom();
		openWritableFiles.put(handle, file);
	}

	/**
	 * Remove the association between the specified {@linkplain
	 * AtomDescriptor handle} and its open writable {@linkplain
	 * RandomAccessFile file}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 */
	public void forgetWritableFile (final @NotNull AvailObject handle)
	{
		assert handle.isAtom();
		openWritableFiles.remove(handle);
	}

	/**
	 * Answer the open {@linkplain RandomAccessFile file} associated with the
	 * specified {@linkplain AtomDescriptor handle}.
	 *
	 * @param handle A {@linkplain AtomDescriptor handle}.
	 * @return The open {@linkplain RandomAccessFile file} associated with the
	 *         {@linkplain AtomDescriptor atom}, or {@code null} if no such
	 *         association exists.
	 */
	public RandomAccessFile getOpenFile (final @NotNull AvailObject handle)
	{
		assert handle.isAtom();
		final RandomAccessFile file = openReadableFiles.get(handle);
		if (file != null)
		{
			return file;
		}

		return openWritableFiles.get(handle);
	}
}
