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

import java.util.Arrays;
import java.util.List;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.BooleanDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.ListTypeDescriptor;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.ObjectTypeDescriptor;
import com.avail.descriptor.SetTypeDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;

/**
 * TODO: [TLS] Document this type!
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class AvailRuntime
{
	/**
	 * The {@linkplain AvailObject special objects} of the {@linkplain
	 * AvailRuntime runtime}.
	 */
	private List<AvailObject> specialObjects;

	/*
	 * Set up the special objects.
	 */
	{
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
		specialObjects.set(13,
			IntegerRangeTypeDescriptor.extendedIntegers().makeImmutable());
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
		specialObjects.set(23,
			ObjectTypeDescriptor.objectTypeFromMap(MapDescriptor.empty()).
				type().type().makeImmutable());
		specialObjects.set(24,
			ObjectTypeDescriptor.objectTypeFromMap(MapDescriptor.empty()).
				type().type().type().makeImmutable());
		specialObjects.set(25,
			ObjectTypeDescriptor.objectTypeFromMap(MapDescriptor.empty()).
				type().makeImmutable());
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
		specialObjects.set(34,
			TupleTypeDescriptor.mostGeneralTupleType().makeImmutable());
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
	
	/**
	 * Answer the {@linkplain AvailObject special objects} of the {@linkplain
	 * AvailRuntime runtime}.
	 * 
	 * @return The {@linkplain AvailObject special objects}.
	 */
	public List<AvailObject> specialObjects ()
	{
		return specialObjects;
	}
	
	// TODO: [TLS] Finish separating AvailInterpreter and AvailRuntime!
}
