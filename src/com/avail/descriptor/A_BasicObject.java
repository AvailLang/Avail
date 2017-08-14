/**
 * A_BasicObject.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.avail.descriptor.MapDescriptor.MapIterable;
import org.jetbrains.annotations.Nullable;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.SetDescriptor.SetIterator;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.*;
import com.avail.utility.json.JSONFriendly;
import com.avail.utility.json.JSONWriter;
import com.avail.utility.visitor.AvailSubobjectVisitor;

/**
 * {@code A_BasicObject} is an interface that specifies all generally applicable
 * operations that an {@link AvailObject} must implement.  Its purpose is to
 * declare that only the most basic protocol of some object will be used.  Its
 * sub-interfaces define behavior that's applicable to tuples, sets, etc., and
 * AvailObject simply implements all of those interfaces.
 *
 * <p>The purpose for A_BasicObject and its sub-interfaces is to allow sincere
 * type annotations about the basic kinds of objects that support or may be
 * passed as arguments to various operations.  The VM implementor is free to
 * always declare variables as AvailObject, but in cases where it's clear that
 * a particular object should always be a tuple (say), a declaration of A_Tuple
 * ensures that only the basic object capabilities plus tuple-like capabilities
 * are allowed to be used on it.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_BasicObject
extends JSONFriendly
{
	/**
	 * Answer the object's {@linkplain AbstractDescriptor descriptor}.
	 *
	 * @return A descriptor.
	 */
	AbstractDescriptor descriptor ();

	/**
	 * Answer whether the {@linkplain AvailObject objects} occupy the same
	 * memory addresses.
	 *
	 * @param anotherObject Another object.
	 * @return Whether the objects occupy the same storage.
	 */
	boolean sameAddressAs (final A_BasicObject anotherObject);

	/**
	 * Turn the receiver into an {@linkplain IndirectionDescriptor indirection}
	 * to the specified {@linkplain AvailObject object}.
	 *
	 * <p><strong>WARNING:</strong> This alters the receiver's slots and
	 * descriptor.</p>
	 *
	 * <p><strong>WARNING:</strong> A {@linkplain Mutability#SHARED shared}
	 * object may not become an indirection. The caller must ensure that this
	 * method is not sent to a shared object.</p>
	 *
	 * @param anotherObject An object.
	 */
	void becomeIndirectionTo (final A_BasicObject anotherObject);


	/**
	 * Answer the number of integer slots. All variable integer slots occur
	 * following the last fixed integer slot.
	 *
	 * @return The number of integer slots.
	 */
	int integerSlotsCount ();

	/**
	 * Answer the number of variable integer slots in this object. This does not
	 * include the fixed integer slots.
	 *
	 * @return The number of variable integer slots.
	 */
	int variableIntegerSlotsCount ();

	/**
	 * Answer the number of object slots in this {@link AvailObject}. All
	 * variable object slots occur following the last fixed object slot.
	 *
	 * @return The number of object slots.
	 */
	int objectSlotsCount ();

	/**
	 * Answer the number of variable object slots in this {@link AvailObject}.
	 * This does not include the fixed object slots.
	 *
	 * @return The number of variable object slots.
	 */
	int variableObjectSlotsCount ();

	/**
	 * Recursively print the {@linkplain AvailObject receiver} to the {@link
	 * StringBuilder} unless it is already present in the {@linkplain List
	 * recursion list}. Printing will begin at the specified indent level,
	 * measured in horizontal tab characters.
	 *
	 * <p>This operation exists primarily to provide useful representations of
	 * {@code AvailObject}s for Java-side debugging.</p>
	 *
	 * @param builder A {@link StringBuilder}.
	 * @param recursionMap An {@link IdentityHashMap} whose keys are {@link
	 *                     AvailObject}s already visited during the recursive
	 *                     print.  The associated values are unused.
	 * @param indent The indent level, in horizontal tabs, at which the {@link
	 *               AvailObject} should be printed.
	 */
	void printOnAvoidingIndent (
		StringBuilder builder,
		IdentityHashMap<A_BasicObject, Void> recursionMap,
		int indent);

	/**
	 * Utility method for decomposing this object in the debugger.  See {@link
	 * AvailObjectFieldHelper} for instructions to enable this functionality in
	 * Eclipse.
	 *
	 * @return An array of {@link AvailObjectFieldHelper} objects that help
	 *         describe the logical structure of the receiver to the debugger.
	 */
	AvailObjectFieldHelper[] describeForDebugger ();

	/**
	 * Answer a name suitable for labeling a field containing this object.
	 *
	 * @return An Avail {@linkplain StringDescriptor string}.
	 */
	String nameForDebugger ();

	/**
	 * Answer whether to show value-specific content in the file name for the
	 * debugger.
	 *
	 * @return Whether to show the value.
	 */
	boolean showValueInNameForDebugger ();

	/**
	 * Replace my descriptor field with a {@link FillerDescriptor}.  This blows
	 * up for most messages, catching incorrect (all, by definition) further
	 * accidental uses of this object.
	 */
	void setToInvalidDescriptor ();

	/**
	 * Compute the 32-bit hash of the receiver.
	 *
	 * @return An {@code int} hash value.
	 */
	int hash ();

	@Override
	int hashCode ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject setBinAddingElementHashLevelCanDestroy (
		A_BasicObject elementObject,
		int elementObjectHash,
		byte myLevel,
		boolean canDestroy);

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject binElementAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean binHasElementWithHash (
		A_BasicObject elementObject,
		int elementObjectHash);

	/**
	 * Dispatch to the descriptor.
	 */
	int binHash ();

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject binRemoveElementHashLevelCanDestroy (
		A_BasicObject elementObject,
		int elementObjectHash,
		byte myLevel,
		boolean canDestroy);

	/**
	 * Dispatch to the descriptor.
	 */
	int binSize ();

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * This comparison operation takes an {@link Object} as its argument to
	 * avoid accidentally calling this with, say, a {@link String} literal.
	 * We mark it as deprecated to ensure we don't accidentally invoke
	 * this method when we really mean the version that takes an {@code
	 * AvailObject} as an argument.  Eclipse conveniently shows such invocations
	 * with a <span style="text-decoration: line-through">strike-out</span>.
	 * That's a convenient warning for the programmer, but we also fail if this
	 * method actually gets invoked AND the argument is not an {@code
	 * AvailObject}.  That means we don't allow AvailObjects to be added to Java
	 * {@linkplain Set sets} and such, at least when they're intermixed with
	 * things that are not AvailObjects.
	 * </p>
	 */
	@Override
	@Deprecated
	boolean equals (@Nullable Object another);

	/**
	 * Answer whether the receiver and the argument, both {@linkplain
	 * AvailObject objects}, are equal in value.
	 *
	 * Note that the argument is of type {@link AvailObject} so that correctly
	 * typed uses (where the argument is statically known to be an AvailObject)
	 * proceed normally. Incorrectly typed uses (where the argument is an
	 * arbitrary Java {@link Object} should show up as calling a deprecated
	 * method, and should fail at runtime if the argument is not actually an
	 * AvailObject.
	 *
	 * @param another The object to be compared to the receiver.
	 * @return {@code true} if the two objects are of equal value, {@code false}
	 *         otherwise.
	 */
	boolean equals (A_BasicObject another);

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
	 * argument, a {@linkplain TupleDescriptor tuple}, are equal in value.
	 *
	 * @param aTuple The tuple to be compared to the receiver.
	 * @return {@code true} if the receiver is a tuple and of value equal to the
	 *         argument, {@code false} otherwise.
	 */
	boolean equalsAnyTuple (A_Tuple aTuple);

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
	 * argument, a {@linkplain ByteStringDescriptor byte string}, are equal in
	 * value.
	 *
	 * @param aByteString The byte string to be compared to the receiver.
	 * @return {@code true} if the receiver is a byte string and of value equal
	 *         to the argument, {@code false} otherwise.
	 */
	boolean equalsByteString (A_String aByteString);

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
	 * argument, a {@linkplain ByteTupleDescriptor byte tuple}, are equal in
	 * value.
	 *
	 * @param aByteTuple The byte tuple to be compared to the receiver.
	 * @return {@code true} if the receiver is a byte tuple and of value equal
	 *         to the argument, {@code false} otherwise.
	 */
	boolean equalsByteTuple (A_Tuple aByteTuple);

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, is a
	 * character with a code point equal to the integer argument.
	 *
	 * @param aCodePoint The code point to be compared to the receiver.
	 * @return {@code true} if the receiver is a character with a code point
	 *         equal to the argument, {@code false} otherwise.
	 */
	boolean equalsCharacterWithCodePoint (int aCodePoint);

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
	 * argument, a {@linkplain FunctionDescriptor function}, are equal in value.
	 *
	 * @param aFunction The function used in the comparison.
	 * @return {@code true} if the receiver is a function and of value equal to
	 *         the argument, {@code false} otherwise.
	 */
	boolean equalsFunction (A_Function aFunction);

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
	 * argument, a {@linkplain FiberTypeDescriptor fiber type}, are equal in
	 * value.
	 *
	 * @param aFiberType A fiber type.
	 * @return {@code true} if the receiver is a fiber type and of value equal
	 *         to the argument, {@code false} otherwise.
	 */
	boolean equalsFiberType (A_Type aFiberType);

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
	 * argument, a {@linkplain FunctionTypeDescriptor function type}, are equal.
	 *
	 * @param aFunctionType The function type used in the comparison.
	 * @return {@code true} IFF the receiver is also a function type and:
	 *
	 * <ul>
	 * <li>The {@linkplain AvailObject#argsTupleType() argument types}
	 * correspond,</li>
	 * <li>The {@linkplain AvailObject#returnType() return types}
	 * correspond, and</li>
	 * <li>The {@linkplain AvailObject#declaredExceptions() raise types}
	 * correspond.</li>
	 * </ul>
	 */
	boolean equalsFunctionType (A_Type aFunctionType);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
	 * {@linkplain CompiledCodeDescriptor compiled code}, are equal.
	 *
	 * @param aCompiledCode The compiled code used in the comparison.
	 * @return {@code true} if the receiver is a compiled code and of value
	 *         equal to the argument, {@code false} otherwise.
	 */
	boolean equalsCompiledCode (A_RawFunction aCompiledCode);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
	 * {@linkplain VariableDescriptor variable}, are the exact same object,
	 * comparing by address (Java object identity). There's no need to traverse
	 * the objects before comparing addresses, because this message was a
	 * double-dispatch that would have skipped (and stripped) the indirection
	 * objects in either path.
	 *
	 * @param aVariable The variable used in the comparison.
	 * @return {@code true} if the receiver is a variable with the same identity
	 *         as the argument, {@code false} otherwise.
	 */
	boolean equalsVariable (AvailObject aVariable);

	/**
	 * Answer whether the receiver equals the argument, a {@linkplain
	 * VariableTypeDescriptor variable type}.
	 *
	 * @param aVariableType A variable type.
	 * @return The result of comparing the receiver and aVariableType.
	 */
	boolean equalsVariableType (A_Type aVariableType);

	/**
	 * Answer whether the receiver equals the argument, a {@linkplain
	 * ContinuationDescriptor continuation}.
	 *
	 * @param aContinuation A continuation.
	 * @return The result of comparing the receiver and aContinuation.
	 */
	boolean equalsContinuation (A_Continuation aContinuation);

	/**
	 * Answer whether the receiver equals the argument, a {@linkplain
	 * ContinuationTypeDescriptor continuation type}.
	 *
	 * @param aContinuationType A continuation type.
	 * @return The result of comparing the receiver and aContinuationType.
	 */
	boolean equalsContinuationType (A_Type aContinuationType);

	/**
	 * Answer whether the receiver equals the argument, an {@linkplain
	 * IntegerRangeTypeDescriptor integer range type}.
	 *
	 * @param anIntegerRangeType An integer range type.
	 * @return The result of comparing the receiver and anIntegerRangeType.
	 */
	boolean equalsIntegerRangeType (
		A_Type anIntegerRangeType);

	/**
	 * Answer whether the receiver equals the argument, an Avail {@linkplain
	 * MapDescriptor map}.
	 *
	 * @param aMap An Avail map.
	 * @return The result of comparing the receiver and aMap.
	 */
	boolean equalsMap (A_Map aMap);

	/**
	 * Answer whether the receiver equals the argument, a {@linkplain
	 * MapTypeDescriptor map type}.
	 *
	 * @param aMapType A map type.
	 * @return The result of comparing the receiver and aMapType.
	 */
	boolean equalsMapType (A_Type aMapType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsNybbleTuple (A_Tuple aNybbleTuple);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsObject (AvailObject anObject);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsObjectTuple (A_Tuple anObjectTuple);

	/**
	 * @param aParseNodeType
	 * @return
	 */
	boolean equalsParseNodeType (A_Type aParseNodeType);

	/**
	 * @param aPojo
	 * @return
	 */
	boolean equalsPojo (AvailObject aPojo);

	/**
	 * @param aPojoType
	 * @return
	 */
	boolean equalsPojoType (AvailObject aPojoType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsPrimitiveType (A_Type aPrimitiveType);

	/**
	 * @param otherRawPojo
	 * @param otherJavaObject
	 * @return
	 */
	boolean equalsRawPojoFor (
		AvailObject otherRawPojo,
		@Nullable Object otherJavaObject);

	/**
	 * @param aTuple
	 * @return boolean
	 */
	boolean equalsReverseTuple(A_Tuple aTuple);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsSet (A_Set aSet);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsDouble (double aDouble);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsFloat (float aFloat);

	/**
	 * Answer whether the {@linkplain AvailObject receiver} is an {@linkplain
	 * InfinityDescriptor infinity} with the specified {@link Sign}.
	 *
	 * @param sign The type of infinity for comparison.
	 * @return {@code true} if the receiver is an infinity of the specified
	 *         sign, {@code false} otherwise.
	 */
	boolean equalsInfinity (Sign sign);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsInteger (A_Number anAvailInteger);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsSetType (A_Type aSetType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsTupleType (A_Type aTupleType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsTwoByteString (A_String aTwoByteString);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsNil ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map fieldMap ();

	/**
	 * Dispatch to the descriptor.
	 */
	int hashOrZero ();

	/**
	 * Dispatch to the descriptor.
	 */
	void hashOrZero (int value);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isAbstract ();

	/**
	 * Dispatch to the descriptor.
	 */
	int representationCostOfTupleType ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isBinSubsetOf (A_Set potentialSuperset);

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail boolean?
	 *
	 * @return {@code true} if the receiver is a boolean, {@code false}
	 *         otherwise.
	 */
	boolean isBoolean ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail unsigned byte?
	 *
	 * @return {@code true} if the argument is an unsigned byte, {@code false}
	 *         otherwise.
	 */
	boolean isUnsignedByte ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail {@linkplain
	 * ByteStringDescriptor byte string}?
	 *
	 * @return {@code true} if the receiver is a byte string, {@code false}
	 *         otherwise.
	 */
	boolean isByteString ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail {@linkplain
	 * ByteTupleDescriptor byte tuple}?
	 *
	 * @return {@code true} if the receiver is a byte tuple, {@code false}
	 *         otherwise.
	 */
	boolean isByteTuple ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail character?
	 *
	 * @return {@code true} if the receiver is a character, {@code false}
	 *         otherwise.
	 */
	boolean isCharacter ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail function?
	 *
	 * @return {@code true} if the receiver is a function, {@code false}
	 *         otherwise.
	 */
	boolean isFunction ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail atom?
	 *
	 * @return {@code true} if the receiver is an atom, {@code false}
	 *         otherwise.
	 */
	boolean isAtom ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail extended integer?
	 *
	 * @return {@code true} if the receiver is an extended integer, {@code
	 *         false} otherwise.
	 */
	boolean isExtendedInteger ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isFinite ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isInstanceOf (A_Type aType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isInstanceOfKind (A_Type aType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isIntegerRangeType ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail {@linkplain
	 * IntTupleDescriptor int tuple}?  This is conservative, in that some object
	 * tuples <em>may</em> only contain ints but not be reported as being int
	 * tuples.
	 *
	 * @return {@code true} if the receiver is easily determined to be an int
	 *         tuple, {@code false} otherwise.
	 */
	boolean isIntTuple ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail map?
	 *
	 * @return {@code true} if the receiver is a map, {@code false} otherwise.
	 */
	boolean isMap ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isMapType ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail nybble?
	 *
	 * @return {@code true} if the receiver is a nybble, {@code false}
	 *         otherwise.
	 */
	boolean isNybble ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail set?
	 *
	 * @return {@code true} if the receiver is a set, {@code false} otherwise.
	 */
	boolean isSet ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isSetType ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail string?
	 *
	 * @return {@code true} if the receiver is an Avail string, {@code false}
	 *         otherwise.
	 */
	boolean isString ();
	/**
	 * Is the {@linkplain AvailObject receiver} an Avail tuple?
	 *
	 * @return {@code true} if the receiver is a tuple, {@code false} otherwise.
	 */
	boolean isTuple ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isTupleType ();

	/**
	 * Is the {@linkplain AvailObject receiver} an Avail {@linkplain
	 * TwoByteStringDescriptor two-byte string}?
	 *
	 * @return {@code true} if the receiver is a two-byte string, {@code false}
	 *         otherwise.
	 */
	boolean isTwoByteString ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isType ();

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject makeImmutable ();

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject makeShared ();

	/**
	 * Dispatch to the descriptor.
	 */
	void makeSubobjectsImmutable ();

	/**
	 * Dispatch to the descriptor.
	 */
	void makeSubobjectsShared ();

	/**
	 * Dispatch to the descriptor.
	 */
	void scanSubobjects (AvailSubobjectVisitor visitor);

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject traversed ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type kind ();

	/**
	 * Dispatch to the descriptor.
	 */
	void value (A_BasicObject value);

	/**
	 * @return
	 */
	A_Type resultType ();

	/**
	 * @return
	 */
	DeclarationKind declarationKind ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type binUnionKind ();

	/**
	 * @return
	 */
	boolean isSetBin ();

	/**
	 * @return
	 */
	boolean isInt ();

	/**
	 * @return
	 */
	boolean isLong ();

	/**
	 * @param anInstanceType
	 * @return
	 */
	boolean equalsInstanceTypeFor (AvailObject anInstanceType);

	/**
	 * Determine whether the receiver is an {@linkplain
	 * AbstractEnumerationTypeDescriptor enumeration} with the given {@linkplain
	 * SetDescriptor set} of instances.
	 *
	 * @param aSet A set of objects.
	 * @return Whether the receiver is an enumeration with the given
	 *         membership.
	 */
	boolean equalsEnumerationWithSet (A_Set aSet);

	/**
	 * @param potentialInstance
	 * @return
	 */
	boolean enumerationIncludesInstance (
		AvailObject potentialInstance);

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	boolean equalsCompiledCodeType (
		A_Type aCompiledCodeType);

	/**
	 * @param anEnumerationType
	 * @return
	 */
	boolean equalsEnumerationType (A_BasicObject anEnumerationType);

	/**
	 * @return
	 */
	boolean isRawPojo ();

	/**
	 * @return
	 */
	boolean isPojoSelfType ();

	/**
	 * @return
	 */
	A_Type pojoSelfType ();

	/**
	 * @return
	 */
	A_BasicObject javaClass ();

	/**
	 * @return
	 */
	boolean isUnsignedShort ();

	/**
	 * @return
	 */
	boolean isFloat ();

	/**
	 * @return
	 */
	boolean isDouble ();

	/**
	 * @return
	 */
	AvailObject rawPojo ();

	/**
	 * @return
	 */
	boolean isPojo ();

	/**
	 * @return
	 */
	boolean isPojoType ();

	/**
	 * @return
	 */
	SerializerOperation serializerOperation ();

	/**
	 * @param key
	 * @param keyHash
	 * @param value
	 * @param myLevel
	 * @param canDestroy
	 * @return
	 */
	A_BasicObject mapBinAtHashPutLevelCanDestroy (
		A_BasicObject key,
		int keyHash,
		A_BasicObject value,
		byte myLevel,
		boolean canDestroy);

	/**
	 * @return
	 */
	boolean isPojoFusedType ();

	/**
	 * @return
	 */
	boolean equalsPojoBottomType ();

	/**
	 * @return
	 */
	AvailObject javaAncestors ();

	/**
	 * @return
	 */
	boolean isPojoArrayType ();

	/**
	 * @param classHint
	 * @return
	 */
	@Nullable Object marshalToJava (@Nullable Class<?> classHint);

	/**
	 * @param field
	 * @param receiver
	 * @return
	 */
	boolean equalsPojoField (
		AvailObject field,
		AvailObject receiver);

	/**
	 * @param otherEqualityRawPojo
	 * @param otherJavaObject
	 * @return
	 */
	boolean equalsEqualityRawPojoFor (
		AvailObject otherEqualityRawPojo,
		@Nullable Object otherJavaObject);

	/**
	 * @return
	 */
	@Nullable Object javaObject ();

	/**
	 * @return
	 */
	Object javaObjectNotNull ();

	/**
	 * @return
	 */
	A_Tuple fieldTuple ();

	/**
	 * @return
	 */
	boolean isLiteralTokenType ();

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	boolean equalsLiteralTokenType (
		A_Type aLiteralTokenType);

	/**
	 * @param anObjectType
	 * @return
	 */
	boolean equalsObjectType (AvailObject anObjectType);

	/**
	 * @param aToken
	 * @return
	 */
	boolean equalsToken (A_Token aToken);

	/**
	 * @return
	 */
	boolean isInstanceMeta ();

	/**
	 * @param kind
	 * @return
	 */
	boolean binElementsAreAllInstancesOfKind (A_Type kind);

	/**
	 * @return
	 */
	MapIterable mapBinIterable ();

	/**
	 * @return
	 */
	SetIterator setBinIterator ();

	/**
	 * @param aParseNode
	 * @return
	 */
	boolean equalsParseNode (A_Phrase aParseNode);

	/**
	 * @return
	 */
	boolean isByteArrayTuple ();

	/**
	 * @return
	 */
	boolean isSignedByte ();

	/**
	 * @return
	 */
	boolean isSignedShort ();

	/**
	 * @param key
	 * @param keyHash
	 * @param canDestroy
	 * @return
	 */
	A_BasicObject mapBinRemoveKeyHashCanDestroy (
		A_BasicObject key,
		int keyHash,
		boolean canDestroy);

	/**
	 * @return
	 */
	A_Type mapBinKeyUnionKind ();

	/**
	 * @return
	 */
	A_Type mapBinValueUnionKind ();

	/**
	 * @return
	 */
	boolean isHashedMapBin ();

	/**
	 * @param key
	 * @param keyHash
	 * @return
	 */
	AvailObject mapBinAtHash (A_BasicObject key, int keyHash);

	/**
	 * @return
	 */
	int mapBinKeysHash ();

	/**
	 * @return
	 */
	int mapBinValuesHash ();

	/**
	 * @param aByteArrayTuple
	 * @return
	 */
	boolean equalsByteArrayTuple (A_Tuple aByteArrayTuple);

	/**
	 * @param aByteBufferTuple
	 * @return
	 */
	boolean equalsByteBufferTuple (A_Tuple aByteBufferTuple);

	/**
	 * @return
	 */
	boolean isByteBufferTuple ();

	/**
	 * @return
	 */
	boolean isIntegerIntervalTuple ();

	/**
	 * @return
	 */
	boolean isSmallIntegerIntervalTuple ();

	/**
	 * @return
	 */
	boolean isRepeatedElementTuple ();

	/**
	 * @param anIntegerIntervalTuple
	 * @return
	 */
	boolean equalsIntegerIntervalTuple (A_Tuple anIntegerIntervalTuple);

	/**
	 * @param anIntTuple
	 * @return
	 */
	boolean equalsIntTuple (A_Tuple anIntTuple);

	/**
	 * @param aSmallIntegerIntervalTuple
	 * @return
	 */
	boolean equalsSmallIntegerIntervalTuple (
		A_Tuple aSmallIntegerIntervalTuple);

	/**
	 * @param aRepeatedElementTuple
	 * @return
	 */
	boolean equalsRepeatedElementTuple (A_Tuple aRepeatedElementTuple);

	/**
	 * @param critical
	 */
	void lock (Continuation0 critical);

	/**
	 * @return
	 */
	boolean isInitializedWriteOnceVariable ();

	/**
	 * @param writer
	 */
	void writeSummaryTo (JSONWriter writer);

	/**
	 * Answer whether this value equals the given {@linkplain
	 * ListNodeTypeDescriptor list phrase type}.
	 *
	 * @param listNodeType The list phrase type to compare against.
	 * @return Whether the receiver equals the given list phrase type.
	 */
	boolean equalsListNodeType (A_Type listNodeType);

	/**
	 * Extract a field from an {@link ObjectDescriptor object}.
	 *
	 * @param field
	 * @return
	 */
	AvailObject fieldAt (A_Atom field);

	/**
	 * Add or replace a field of an {@link ObjectDescriptor object}.
	 *
	 * @param field
	 * @param value
	 * @param canDestroy
	 * @return
	 */
	A_BasicObject fieldAtPuttingCanDestroy (
		A_Atom field,
		A_BasicObject value,
		boolean canDestroy);
}
