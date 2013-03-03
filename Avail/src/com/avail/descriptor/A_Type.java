/**
 * A_Type.java
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

package com.avail.descriptor;

import java.util.List;
import com.avail.descriptor.TypeDescriptor.Types;

/**
 * {@code A_Type} is an interface that specifies the operations specific to all
 * of Avail's types.  It's a sub-interface of {@link A_BasicObject}, the
 * interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Type
extends A_BasicObject
{
	/**
	 * Answer whether the {@linkplain AvailObject#argsTupleType() argument
	 * types} supported by the specified {@linkplain FunctionTypeDescriptor
	 * function type} are acceptable argument types for invoking a {@linkplain
	 * FunctionDescriptor function} whose type is the receiver.
	 *
	 * @param functionType A function type.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than those of {@code functionType}, {@code false}
	 *         otherwise.
	 */
	boolean acceptsArgTypesFromFunctionType (
		A_Type functionType);

	/**
	 * Answer whether these are acceptable {@linkplain TypeDescriptor argument
	 * types} for invoking a {@linkplain FunctionDescriptor function} whose type
	 * is the receiver.
	 *
	 * @param argTypes A list containing the argument types to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than those within the {@code argTypes} list, {@code
	 *         false} otherwise.
	 */
	boolean acceptsListOfArgTypes (List<A_Type> argTypes);

	/**
	 * Answer whether these are acceptable {@linkplain TypeDescriptor argument
	 * types} for invoking a {@linkplain FunctionDescriptor function} that is an
	 * instance of the receiver. There may be more entries in the {@linkplain
	 * TupleDescriptor tuple} than are required by the {@linkplain
	 * FunctionTypeDescriptor function type}.
	 *
	 * @param argTypes A tuple containing the argument types to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than the corresponding elements of the {@code argTypes}
	 *         tuple, {@code false} otherwise.
	 */
	boolean acceptsTupleOfArgTypes (A_Tuple argTypes);

	/**
	 * Answer whether these are acceptable arguments for invoking a {@linkplain
	 * FunctionDescriptor function} that is an instance of the receiver. There
	 * may be more entries in the {@linkplain TupleDescriptor tuple} than are
	 * required by the {@linkplain FunctionTypeDescriptor function type}.
	 *
	 * @param arguments A tuple containing the argument values to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than the types of the corresponding elements of the
	 *         {@code arguments} tuple, {@code false} otherwise.
	 */
	boolean acceptsTupleOfArguments (A_Tuple arguments);

	/**
	 * Also declared in {@link A_Phrase} for {@linkplain BlockNodeDescriptor
	 * block phrases} and {@linkplain SendNodeDescriptor send phrases}.
	 *
	 * @return The set of declared exception types.
	 */
	A_Set declaredExceptions ();

	/**
	 * Also declared in {@link A_Phrase} for {@linkplain BlockNodeDescriptor
	 * block phrases} and {@linkplain SendNodeDescriptor send phrases}.
	 */
	A_Type returnType ();



	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSubtypeOf (A_Type aType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfVariableType (A_BasicObject aVariableType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfContinuationType (
		A_Type aContinuationType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfFiberType (A_Type aFiberType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfFunctionType (A_Type aFunctionType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfIntegerRangeType (
		A_Type anIntegerRangeType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfLiteralTokenType (
		A_BasicObject aLiteralTokenType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfMapType (AvailObject aMapType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfObjectType (A_BasicObject anObjectType);

	/**
	 */
	boolean isSupertypeOfParseNodeType (
		AvailObject aParseNodeType);

	/**
	 * Dispatch to the descriptor
	 */
	boolean isSupertypeOfPojoType (A_BasicObject aPojoType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfPrimitiveTypeEnum (
		Types primitiveTypeEnum);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfSetType (AvailObject aSetType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfBottom ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfTupleType (AvailObject aTupleType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSupertypeOfEnumerationType (
		A_BasicObject anEnumerationType);

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	boolean isSupertypeOfCompiledCodeType (
		A_Type aCompiledCodeType);

	/**
	 * @param aPojoType
	 * @return
	 */
	boolean isSupertypeOfPojoBottomType (A_Type aPojoType);

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	A_Type typeUnionOfCompiledCodeType (
		A_Type aCompiledCodeType);

	/**
	 * @param aFusedPojoType
	 * @return
	 */
	A_Type typeIntersectionOfPojoFusedType (
		A_Type aFusedPojoType);

	/**
	 * @param anUnfusedPojoType
	 * @return
	 */
	A_Type typeIntersectionOfPojoUnfusedType (
		A_Type anUnfusedPojoType);

	/**
	 * @param aFusedPojoType
	 * @return
	 */
	A_Type typeUnionOfPojoFusedType (
		A_Type aFusedPojoType);

	/**
	 * @param anUnfusedPojoType
	 * @return
	 */
	A_Type typeUnionOfPojoUnfusedType (
		A_Type anUnfusedPojoType);

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	A_Type typeIntersectionOfLiteralTokenType (
		A_Type aLiteralTokenType);

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	A_Type typeUnionOfLiteralTokenType (
		A_Type aLiteralTokenType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeIntersection (A_Type another);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeIntersectionOfFiberType (
		A_Type aFiberType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeIntersectionOfFunctionType (
		A_Type aFunctionType);

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	A_Type typeIntersectionOfCompiledCodeType (
		A_Type aCompiledCodeType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeIntersectionOfVariableType (
		A_Type aVariableType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeIntersectionOfContinuationType (
		A_Type aContinuationType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeIntersectionOfIntegerRangeType (
		A_Type anIntegerRangeType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeIntersectionOfMapType (A_Type aMapType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeIntersectionOfObjectType (
		A_Type anObjectType);

	/**
	 * @param aParseNodeType
	 * @return
	 */
	A_Type typeIntersectionOfParseNodeType (
		A_Type aParseNodeType);

	/**
	 * @param aPojoType
	 * @return
	 */
	A_Type typeIntersectionOfPojoType (
		A_Type aPojoType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeIntersectionOfSetType (A_Type aSetType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeIntersectionOfTupleType (
		A_Type aTupleType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeUnion (A_Type another);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeUnionOfFiberType (
		A_Type aFiberType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeUnionOfFunctionType (
		A_Type aFunctionType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeUnionOfVariableType (
		A_Type aVariableType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeUnionOfContinuationType (
		A_Type aContinuationType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeUnionOfIntegerRangeType (
		A_Type anIntegerRangeType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeUnionOfMapType (A_Type aMapType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeUnionOfObjectType (A_Type anObjectType);

	/**
	 * @param aParseNodeType
	 * @return
	 */
	A_Type typeUnionOfParseNodeType (A_Type aParseNodeType);

	/**
	 * @param aPojoType
	 * @return
	 */
	A_Type typeUnionOfPojoType (A_Type aPojoType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeUnionOfSetType (A_Type aSetType);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeUnionOfTupleType (A_Type aTupleType);

	/**
	 * Answer whether these are acceptable arguments for invoking a {@linkplain
	 * FunctionDescriptor function} whose type is the receiver.
	 *
	 * @param argValues A list containing the argument values to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than the types of the values within the {@code argValues}
	 *         list, {@code false} otherwise.
	 */
	boolean acceptsListOfArgValues (
		List<? extends A_BasicObject> argValues);

	/**
	 * Answer the type of elements that this set type's sets may hold.
	 *
	 * @return The set type's content type.
	 */
	A_Type contentType ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Number lowerBound ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean lowerInclusive ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Number upperBound ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean upperInclusive ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type keyType ();

	/**
	 * @return
	 */
	A_Map typeVariables ();

	/**
	 * @return
	 */
	A_Type functionType ();
}
