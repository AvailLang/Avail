/**
 * A_BasicObject.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import com.avail.annotations.Nullable;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.InfinityDescriptor.IntegerSlots;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.SetDescriptor.SetIterator;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.Continuation0;
import com.avail.visitor.AvailSubobjectVisitor;

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
	 * Extract the byte at the given one-based byte subscript within the
	 * specified field. Always use little-endian encoding.
	 *
	 * @param field An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @return The unsigned byte as a short.
	 */
	short byteSlotAt (
		final IntegerSlotsEnum field,
		final int byteSubscript);

	/**
	 * Replace the byte at the given one-based byte subscript within the
	 * specified field. Always use little endian encoding.
	 *
	 * @param field An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @param aByte The unsigned byte to write, passed as a short.
	 */
	void byteSlotAtPut (
		final IntegerSlotsEnum field,
		final int byteSubscript,
		final short aByte);

	int mutableSlot (final IntegerSlotsEnum field);

	int mutableSlot (
		final IntegerSlotsEnum field,
		final int subscript);

	AvailObject mutableSlot (final ObjectSlotsEnum field);

	AvailObject mutableSlot (
		final ObjectSlotsEnum field,
		final int subscript);

	void setMutableSlot (
		final IntegerSlotsEnum field,
		final int anInteger);

	void setMutableSlot (
		final IntegerSlotsEnum field,
		final int subscript,
		final int anInteger);

	void setMutableSlot (
		final ObjectSlotsEnum field,
		final A_BasicObject anAvailObject);

	void setMutableSlot (
		final ObjectSlotsEnum field,
		final int subscript,
		final AvailObject anAvailObject);

	void setSlot (
		final BitField bitField,
		final int anInteger);

	void setSlot (
		final IntegerSlotsEnum field,
		final int anInteger);

	void setSlot (
		final IntegerSlotsEnum field,
		final int subscript,
		final int anInteger);

	void setSlot (
		final ObjectSlotsEnum field,
		final A_BasicObject anAvailObject);

	void setSlot (
		final ObjectSlotsEnum field,
		final int subscript,
		final A_BasicObject anAvailObject);

	int shortSlotAt (
		final IntegerSlotsEnum field,
		final int shortIndex);

	void shortSlotAtPut (
		final IntegerSlotsEnum field,
		final int shortIndex,
		final int aShort);

	int slot (
		final BitField bitField);

	int slot (final IntegerSlotsEnum field);

	int slot (
		final IntegerSlotsEnum field,
		final int subscript);

	AvailObject slot (
		final ObjectSlotsEnum field);

	AvailObject slot (
		final ObjectSlotsEnum field,
		final int subscript);


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
	 * @param recursionList A {@linkplain List list} containing {@link
	 *                      AvailObject}s already visited during the recursive
	 *                      print.
	 * @param indent The indent level, in horizontal tabs, at which the {@link
	 *               AvailObject} should be printed.
	 */
	void printOnAvoidingIndent (
		StringBuilder builder,
		List<A_BasicObject> recursionList,
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
	 * A good multiplier for a multiplicative random generator.  This constant
	 * is a primitive element of (Z[2^32],*), specifically 1664525, as taken
	 * from Knuth, The Art of Computer Programming, Vol. 2, 2nd ed., page 102,
	 * row 26. See also pages 19, 20, theorems B and C. The period of the
	 * cycle based on this multiplicative generator is 2^30.
	 */
	static final int multiplier = 1664525;

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
	void binElementAtPut (int index, A_BasicObject value);

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
	void binHash (int value);

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject binRemoveElementHashCanDestroy (
		A_BasicObject elementObject,
		int elementObjectHash,
		boolean canDestroy);

	/**
	 * Dispatch to the descriptor.
	 */
	int binSize ();

	/**
	 * Dispatch to the descriptor.
	 */
	void binSize (int value);

	/**
	 * Dispatch to the descriptor.
	 */
	void bitVector (int value);

	/**
	 * Dispatch to the descriptor.
	 */
	int codePoint ();

	/**
	 * Dispatch to the descriptor.
	 */
	void codePoint (int value);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean couldEverBeInvokedWith (List<? extends A_Type> argTypes);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type defaultType ();

	/**
	 * Dispatch to the descriptor.
	 */
	void displayTestingTree ();

	/**
	 * Answer the element at the given index of the receiver.
	 *
	 * @param index An integer.
	 * @return The element at the given index.
	 */
	A_BasicObject elementAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void elementAtPut (int index, A_BasicObject value);

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * This comparison operation takes an {@link Object} as its argument to
	 * avoid accidentally calling this with, say, a {@link String} literal.
	 * We mark it as deprecated to ensure we don't accidentally invoke
	 * this method when we really mean the version that takes an {@code
	 * AvailObject} as an argument.  Eclipse conveniently shows such invocations
	 * with a <strike>strike-out</strike>.  That's a convenient warning for the
	 * programmer, but we also fail if this method actually gets invoked AND
	 * the argument is not an {@code AvailObject}.  That means we don't allow
	 * AvailObjects to be added to Java {@linkplain Set sets} and such, at least
	 * when they're intermixed with things that are not AvailObjects.
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
	 * <p><ul>
	 * <li>The {@linkplain AvailObject#argsTupleType() argument types}
	 * correspond,</li>
	 * <li>The {@linkplain AvailObject#returnType() return types}
	 * correspond, and</li>
	 * <li>The {@linkplain AvailObject#declaredExceptions() raise types}
	 * correspond.</li>
	 * </ul></p>
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
	 * Dispatch to the descriptor.
	 */
	boolean equalsVariableType (A_Type aVariableType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsContinuation (A_Continuation aContinuation);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsContinuationType (A_Type aType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsIntegerRangeType (
		A_Type anIntegerRangeType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsMap (A_Map aMap);

	/**
	 * Dispatch to the descriptor.
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
	 * @param aRawPojo
	 * @return
	 */
	boolean equalsRawPojo (AvailObject aRawPojo);

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
	 * InfinityDescriptor infinity} with the specified {@link
	 * IntegerSlots#SIGN}.
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
	A_Map fieldTypeMap ();

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
	boolean hasObjectInstance (AvailObject potentialInstance);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isAbstract ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isBetterRepresentationThanTupleType (
		A_Type aTupleType);

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
	 * Is the {@linkplain AvailObject receiver} an Avail byte tuple?
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
	 * Dispatch to the descriptor.
	 */
	boolean isPositive ();

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
	 * Dispatch to the descriptor.
	 */
	boolean isType ();

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject literal ();

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
	 *
	 * TODO[MvG] - Break this accidental polymorphism for better clarity of
	 * intention.
	 */
	AvailObject name ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject parent ();

	/**
	 * Dispatch to the descriptor.
	 */
	void parent (AvailObject value);

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject privateAddElement (A_BasicObject element);

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject privateExcludeElement (A_BasicObject element);

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject privateExcludeElementKnownIndex (
		A_BasicObject element,
		int knownIndex);

	/**
	 * Dispatch to the descriptor.
	 */
	int rawSignedIntegerAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void rawSignedIntegerAtPut (int index, int value);

	/**
	 * Dispatch to the descriptor.
	 */
	long rawUnsignedIntegerAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void rawUnsignedIntegerAtPut (int index, int value);

	/**
	 * Dispatch to the descriptor.
	 */
	void scanSubobjects (AvailSubobjectVisitor visitor);

	/**
	 * Dispatch to the descriptor.
	 */
	void size (int value);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type sizeRange ();

	/**
	 * Dispatch to the descriptor.
	 */
	int start ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_String string ();

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject traversed ();

	/**
	 * Dispatch to the descriptor.
	 */
	void trimExcessInts ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type kind ();

	/**
	 * Dispatch to the descriptor.
	 */
	void type (A_BasicObject value);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeAtIndex (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple typeTuple ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type unionOfTypesAtThrough (
		int startIndex,
		int endIndex);

	/**
	 * Dispatch to the descriptor.
	 */
	int untranslatedDataAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void untranslatedDataAtPut (int index, int value);

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
	 * @return
	 */
	AvailObject initializationExpression ();

	/**
	 * @return
	 */
	A_BasicObject literalObject ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type binUnionKind ();

	/**
	 * @return
	 */
	int lineNumber ();

	/**
	 * @return
	 */
	boolean isSetBin ();

	/**
	 * @return
	 */
	MapDescriptor.MapIterable mapIterable ();

	/**
	 * @return
	 */
	boolean isInt ();

	/**
	 * @return
	 */
	boolean isLong ();

	/**
	 * @return
	 */
	A_Type argsTupleType ();

	/**
	 * @param anInstanceType
	 * @return
	 */
	boolean equalsInstanceTypeFor (AvailObject anInstanceType);

	/**
	 * @return
	 */
	A_Set instances ();

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
	 * @return
	 */
	boolean isEnumeration ();

	/**
	 * @param potentialInstance
	 * @return
	 */
	boolean enumerationIncludesInstance (
		AvailObject potentialInstance);

	/**
	 * @return
	 */
	A_Type valueType ();

	/**
	 * Compute a {@linkplain TypeDescriptor type} that is an ancestor of the
	 * receiver, but is not an {@linkplain AbstractEnumerationTypeDescriptor
	 * enumeration}.  Choose the most specific such type.  Fail if the
	 * receiver is not itself an enumeration.  Also fail if the receiver is
	 * {@linkplain BottomTypeDescriptor bottom}.
	 *
	 * @return The must specific non-union supertype.
	 */
	A_Type computeSuperkind ();

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
	A_Type readType ();

	/**
	 * @return
	 */
	A_Type writeType ();

	/**
	 * @return
	 */
	boolean parseNodeKindIsUnder (
		ParseNodeKind expectedParseNodeKind);

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
	int extractUnsignedShort ();

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
	A_BasicObject upperBoundMap ();

	/**
	 * @param aMap
	 */
	void upperBoundMap (A_BasicObject aMap);

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
	Object marshalToJava (@Nullable Class<?> classHint);

	/**
	 * @param field
	 * @param receiver
	 * @return
	 */
	boolean equalsPojoField (
		AvailObject field,
		AvailObject receiver);

	/**
	 * @param aRawPojo
	 * @return
	 */
	boolean equalsEqualityRawPojo (AvailObject aRawPojo);

	/**
	 * @return
	 */
	Object javaObject ();

	/**
	 * @return
	 */
	BigInteger asBigInteger ();

	/**
	 * @return
	 */
	A_String lowerCaseString ();

	/**
	 * @return
	 */
	A_Number instanceCount ();

	/**
	 * @return
	 */
	A_Tuple fieldTypeTuple ();

	/**
	 * @return
	 */
	A_Tuple fieldTuple ();

	/**
	 * @return
	 */
	A_Type literalType ();

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
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	A_Number bitwiseAnd (
		A_Number anInteger,
		boolean canDestroy);

	/**
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	A_Number bitwiseOr (
		A_Number anInteger,
		boolean canDestroy);

	/**
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	A_Number bitwiseXor (
		A_Number anInteger,
		boolean canDestroy);

	/**
	 * @return
	 */
	boolean isInstanceMeta ();

	/**
	 * @return
	 */
	AvailObject instance ();

	/**
	 * @param kind
	 * @return
	 */
	boolean binElementsAreAllInstancesOfKind (A_Type kind);

	/**
	 * @return
	 */
	MapDescriptor.MapIterable mapBinIterable ();

	/**
	 * @param anInt
	 * @return
	 */
	boolean rangeIncludesInt (int anInt);

	/**
	 * @param shiftFactor
	 * @param truncationBits
	 * @param canDestroy
	 * @return
	 */
	A_Number bitShiftLeftTruncatingToBits (
		A_Number shiftFactor,
		A_Number truncationBits,
		boolean canDestroy);

	/**
	 * @return
	 */
	SetIterator setBinIterator ();

	/**
	 * @param shiftFactor
	 * @param canDestroy
	 * @return
	 */
	A_Number bitShift (
		A_Number shiftFactor,
		boolean canDestroy);

	/**
	 * @param aParseNode
	 * @return
	 */
	boolean equalsParseNode (A_Phrase aParseNode);

	/**
	 * @return
	 */
	byte[] byteArray ();

	/**
	 * @return
	 */
	boolean isByteArrayTuple ();

	/**
	 * @param critical
	 */
	void lock (Continuation0 critical);

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
	 * @param newName
	 */
	void name (A_String newName);

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
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteBufferTuple
	 * @param startIndex2
	 * @return
	 */
	boolean compareFromToWithByteBufferTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple aByteBufferTuple,
		int startIndex2);
}
