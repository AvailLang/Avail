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

import java.beans.MethodDescriptor;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailModuleDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.BooleanDescriptor;
import com.avail.descriptor.CyclicTypeDescriptor;
import com.avail.descriptor.ImplementationSetDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.ListTypeDescriptor;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.ObjectTypeDescriptor;
import com.avail.descriptor.SetTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.Primitive;

/**
 * An {@code AvailRuntime} comprises the {@linkplain AvailModuleDescriptor
 * modules}, {@linkplain MethodDescriptor methods}, and {@linkplain
 * #specialObject(int) special objects} that define an Avail system. It also
 * manages global resources, such as file connections.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class AvailRuntime
{
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
		specialObjects[1] = Types.all.object();
		specialObjects[2] = Types.booleanType.object();
		specialObjects[3] = Types.character.object();
		specialObjects[4] = Types.closure.object();
		specialObjects[5] = Types.closureType.object();
		specialObjects[6] = Types.compiledCode.object();
		specialObjects[7] = Types.container.object();
		specialObjects[8] = Types.containerType.object();
		specialObjects[9] = Types.continuation.object();
		specialObjects[10] = Types.continuationType.object();
		specialObjects[11] = Types.cyclicType.object();
		specialObjects[12] = Types.doubleObject.object();
		specialObjects[13] =
			IntegerRangeTypeDescriptor.extendedIntegers().makeImmutable();
		specialObjects[14] = Types.falseType.object();
		specialObjects[15] = Types.floatObject.object();
		specialObjects[16] = Types.generalizedClosureType.object();
		specialObjects[17] =
			IntegerRangeTypeDescriptor.integers().makeImmutable();
		specialObjects[18] = Types.integerType.object();
		specialObjects[19] = ListTypeDescriptor.listTypeForTupleType(
			TupleTypeDescriptor.mostGeneralTupleType()).makeImmutable();
		specialObjects[20] = Types.listType.object();
		specialObjects[21] = Types.mapType.object();
		specialObjects[22] = Types.meta.object();
		specialObjects[23] = ObjectTypeDescriptor.objectTypeFromMap(
			MapDescriptor.empty()).type().type().makeImmutable();
		specialObjects[24] = ObjectTypeDescriptor.objectTypeFromMap(
			MapDescriptor.empty()).type().type().type().makeImmutable();
		specialObjects[25] = ObjectTypeDescriptor.objectTypeFromMap(
			MapDescriptor.empty()).type().makeImmutable();
		specialObjects[26] = Types.primType.object();
		specialObjects[27] = Types.process.object();
		specialObjects[28] = SetTypeDescriptor.setTypeForSizesContentType(
			IntegerRangeTypeDescriptor.wholeNumbers(), Types.all.object())
				.makeImmutable();
		specialObjects[29] = Types.setType.object();
		specialObjects[30] = TupleTypeDescriptor.stringTupleType();
		specialObjects[31] = Types.terminates.object();
		specialObjects[32] = Types.terminatesType.object();
		specialObjects[33] = Types.trueType.object();
		specialObjects[34] =
			TupleTypeDescriptor.mostGeneralTupleType().makeImmutable();
		specialObjects[35] = Types.tupleType.object();
		specialObjects[36] = Types.type.object();
		specialObjects[37] = Types.voidType.object();

		// Code reflection
		specialObjects[40] = Types.messageBundle.object();
		specialObjects[41] = Types.signature.object();
		specialObjects[42] = Types.abstractSignature.object();
		specialObjects[43] = Types.forwardSignature.object();
		specialObjects[44] = Types.methodSignature.object();
		specialObjects[45] = Types.messageBundleTree.object();
		specialObjects[46] = Types.implementationSet.object();

		// Parse nodes types
		specialObjects[50] = Types.assignmentNode.object();
		specialObjects[51] = Types.blockNode.object();
		specialObjects[52] = Types.constantDeclarationNode.object();
		specialObjects[53] = Types.initializingDeclarationNode.object();
		specialObjects[54] = Types.labelNode.object();
		specialObjects[55] = Types.listNode.object();
		specialObjects[56] = Types.literalNode.object();
		specialObjects[57] = Types.parseNode.object();
		specialObjects[58] = Types.referenceNode.object();
		specialObjects[59] = Types.sendNode.object();
		specialObjects[60] = Types.superCastNode.object();
		specialObjects[61] = Types.syntheticConstantNode.object();
		specialObjects[62] = Types.syntheticDeclarationNode.object();
		specialObjects[63] = Types.variableDeclarationNode.object();
		specialObjects[64] = Types.variableUseNode.object();

		// Booleans
		specialObjects[70] = BooleanDescriptor.objectFromBoolean(true);
		specialObjects[71] = BooleanDescriptor.objectFromBoolean(false);
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
	public AvailObject specialObject (final int ordinal)
		throws ArrayIndexOutOfBoundsException
	{
		return specialObjects[ordinal];
	}
	
	/** The loaded Avail {@linkplain AvailModuleDescriptor modules}. */
	private @NotNull AvailObject modules = MapDescriptor.empty();
	
	/**
	 * Answer the {@linkplain AvailModuleDescriptor modules} currently loaded by
	 * the {@linkplain AvailRuntime runtime}.
	 * 
	 * @return A {@linkplain MapDescriptor map} from {@linkplain
	 *         TupleDescriptor module names} to {@linkplain
	 *         AvailModuleDescriptor modules}.
	 */
	public @NotNull AvailObject modules ()
	{
		return modules;
	}
	
	/**
	 * Add the specified {@linkplain AvailModuleDescriptor module} to the
	 * {@linkplain AvailRuntime runtime}.
	 * 
	 * @param aModule A {@linkplain AvailModuleDescriptor module}.
	 */
	public void addModule (final @NotNull AvailObject aModule)
	{
		modules = modules.mapAtPuttingCanDestroy(aModule.name(), aModule, true);
	}
	
	/**
	 * Does the {@linkplain AvailRuntime runtime} define a {@linkplain
	 * AvailModuleDescriptor module} with the specified {@linkplain
	 * TupleDescriptor name}?
	 * 
	 * @param moduleName A {@linkplain TupleDescriptor name}.
	 * @return {@code true} if the {@linkplain AvailRuntime runtime} defines a
	 *          {@linkplain AvailModuleDescriptor module} with the specified
	 *          {@linkplain TupleDescriptor name}, {@code false} otherwise.
	 */
	public boolean includesModuleNamed (final @NotNull AvailObject moduleName)
	{
		assert moduleName.isString();
		return modules.hasKey(moduleName);
	}
	
	/**
	 * Answer the {@linkplain AvailModuleDescriptor module} with the specified
	 * {@linkplain TupleDescriptor name}.
	 * 
	 * @param moduleName A {@linkplain TupleDescriptor name}.
	 * @return A {@linkplain AvailModuleDescriptor module}.
	 */
	public @NotNull AvailObject moduleAt (final @NotNull AvailObject moduleName)
	{
		assert moduleName.isString();
		assert includesModuleNamed(moduleName);
		return modules.mapAt(moduleName);
	}
	
	/**
	 * The {@linkplain MethodDescriptor methods} currently known to the
	 * {@linkplain AvailRuntime runtime}: a {@linkplain MapDescriptor map} from
	 * {@linkplain CyclicTypeDescriptor method name} to {@linkplain
	 * ImplementationSetDescriptor implementation set}.
	 */
	private @NotNull AvailObject methods = MapDescriptor.empty();
	
	/**
	 * Answer the {@linkplain MethodDescriptor methods} current known to the
	 * {@linkplain AvailRuntime runtime}.
	 * 
	 * @return A {@linkplain MapDescriptor map} from {@linkplain
	 *         CyclicTypeDescriptor selector} to {@linkplain
	 *         ImplementationSetDescriptor implementation set}.
	 */
	public @NotNull AvailObject methods ()
	{
		return methods;
	}
	
	/**
	 * Are there any {@linkplain ImplementationSetDescriptor methods} bound to
	 * the specified {@linkplain CyclicTypeDescriptor selector}?
	 * 
	 * @param selector A {@linkplain CyclicTypeDescriptor selector}.
	 * @return {@code true} if there are {@linkplain ImplementationSetDescriptor
	 *         methods} bound to the specified {@linkplain CyclicTypeDescriptor
	 *         selector}, {@code false} otherwise.
	 */
	public boolean hasMethodsAt (final @NotNull AvailObject selector)
	{
		assert selector.isCyclicType();
		return methods.hasKey(selector);
	}
	
	/**
	 * Answer the {@linkplain ImplementationSetDescriptor implementation set}
	 * bound to the specified {@linkplain CyclicTypeDescriptor selector}.
	 * 
	 * @param selector A {@linkplain CyclicTypeDescriptor selector}.
	 * @return An {@linkplain ImplementationSetDescriptor implementation set}.
	 */
	public @NotNull AvailObject methodsAt (final @NotNull AvailObject selector)
	{
		assert selector.isCyclicType();
		return methods.mapAt(selector);
	}
	
	/**
	 * Map the specified {@linkplain CyclicTypeDescriptor selector} to the
	 * specified {@linkplain ImplementationSetDescriptor implementation set}.
	 *  
	 * @param selector A {@linkplain CyclicTypeDescriptor selector}.
	 * @param implementationSet An {@linkplain ImplementationSetDescriptor
	 *                          implementation set}.
	 */
	public void methodsAtPut (
		final @NotNull AvailObject selector,
		final @NotNull AvailObject implementationSet)
	{
		assert selector.isCyclicType();
		methods = methods.mapAtPuttingCanDestroy(
			selector, implementationSet, true);
	}
	
	/**
	 * Unbind all {@linkplain ImplementationSetDescriptor methods} from the
	 * specified {@linkplain CyclicTypeDescriptor selector}.
	 * 
	 * @param selector A {@linkplain CyclicTypeDescriptor selector}.
	 */
	public void removeMethodsAt (final @NotNull AvailObject selector)
	{
		assert selector.isCyclicType();
		methods = methods.mapWithoutKeyCanDestroy(selector, true);
	}
	
	/**
	 * A {@linkplain MapDescriptor map} from {@linkplain ObjectTypeDescriptor
	 * user-defined object types} to user-assigned names.
	 */
	private AvailObject typeNames = MapDescriptor.empty();

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
	private Map<AvailObject, RandomAccessFile> openReadableFiles =
		new HashMap<AvailObject, RandomAccessFile>();
	
	/**
	 * A mapping from {@linkplain CyclicTypeDescriptor keys} to {@link
	 * RandomAccessFile}s open for writing.
	 */
	private Map<AvailObject, RandomAccessFile> openWritableFiles =
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
	
	// TODO: [TLS] Finish separating AvailInterpreter and AvailRuntime!
}
