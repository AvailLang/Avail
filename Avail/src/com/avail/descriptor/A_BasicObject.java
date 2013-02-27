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
import java.util.TimerTask;
import com.avail.annotations.Nullable;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.FiberDescriptor.GeneralFlag;
import com.avail.descriptor.FiberDescriptor.InterruptRequestFlag;
import com.avail.descriptor.FiberDescriptor.SynchronizationFlag;
import com.avail.descriptor.InfinityDescriptor.IntegerSlots;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.SetDescriptor.SetIterator;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.AvailLoader;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.Continuation0;
import com.avail.utility.Continuation1;
import com.avail.utility.Transformer1;
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
		List<AvailObject> recursionList,
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
	 * Add the {@linkplain L2ChunkDescriptor chunk} with the given index to the
	 * receiver's list of chunks that depend on it.  The receiver is a
	 * {@linkplain MethodDescriptor method}.  A change in the method's
	 * membership (e.g., adding a new method definition) will cause the chunk
	 * to be invalidated.
	 *
	 * @param aChunkIndex
	 */
	void addDependentChunkIndex (int aChunkIndex);

	/**
	 * Add the {@linkplain DefinitionDescriptor definition} to the receiver, a
	 * {@linkplain MethodDefinitionDescriptor method}.  Causes dependent chunks
	 * to be invalidated.
	 *
	 * Macro signatures and non-macro signatures should not be combined in the
	 * same method.
	 *
	 * @param definition The definition to be added.
	 * @throws SignatureException
	 *         If the definition could not be added.
	 */
	void methodAddDefinition (A_BasicObject definition)
		throws SignatureException;

	/**
	 * Add a set of {@linkplain MessageBundleDescriptor grammatical
	 * restrictions} to the receiver.
	 *
	 * @param restrictions The set of grammatical restrictions to be added.
	 */
	void addGrammaticalRestrictions (A_Tuple restrictions);

	/**
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	void addGrammaticalRestrictions (
		A_Atom methodName,
		A_Tuple illegalArgMsgs);

	/**
	 * @param definition
	 */
	void moduleAddDefinition (A_BasicObject definition);

	/**
	 * @param bundle
	 */
	void addBundle (
		A_BasicObject bundle);

	/**
	 * @param stringName
	 * @param trueName
	 */
	void addImportedName (
		A_String stringName,
		A_Atom trueName);

	/**
	 * @param stringName
	 * @param trueName
	 */
	void introduceNewName (
		A_String stringName,
		A_Atom trueName);

	/**
	 * @param stringName
	 * @param trueName
	 */
	void addPrivateName (
		A_String stringName,
		A_Atom trueName);

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
	int bitsPerEntry ();

	/**
	 * Dispatch to the descriptor.
	 */
	void bitVector (int value);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Function bodyBlock ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type bodySignature ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject breakpointBlock ();

	/**
	 * Dispatch to the descriptor.
	 */
	void breakpointBlock (AvailObject value);

	/**
	 * Dispatch to the descriptor.
	 */
	void buildFilteredBundleTreeFrom (A_Map bundleMap);

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject caller ();

	/**
	 * Dispatch to the descriptor.
	 */
	void caller (AvailObject value);

	/**
	 * Dispatch to the descriptor.
	 */
	void cleanUpAfterCompile ();

	/**
	 * Dispatch to the descriptor.
	 */
	void clearValue ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Function function ();

	/**
	 * Dispatch to the descriptor.
	 */
	void function (AvailObject value);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type functionType ();

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
	A_Map lazyComplete ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map constantBindings ();

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject continuation ();

	/**
	 * Dispatch to the descriptor.
	 */
	void continuation (A_BasicObject value);

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject copyAsMutableContinuation ();

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
	 * Dispatch to the descriptor.
	 */
	int endOfZone (int zone);

	/**
	 * Dispatch to the descriptor.
	 */
	int endSubtupleIndexInZone (int zone);

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject ensureMutable ();

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
	boolean equalsByteString (AvailObject aByteString);

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, and the
	 * argument, a {@linkplain ByteTupleDescriptor byte tuple}, are equal in
	 * value.
	 *
	 * @param aByteTuple The byte tuple to be compared to the receiver.
	 * @return {@code true} if the receiver is a byte tuple and of value equal
	 *         to the argument, {@code false} otherwise.
	 */
	boolean equalsByteTuple (AvailObject aByteTuple);

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
	 * @see AvailObject#equalsFunction(AvailObject)
	 */
	boolean equalsFunction (AvailObject aFunction);

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
	boolean equalsCompiledCode (AvailObject aCompiledCode);

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
	boolean equalsContinuation (AvailObject aContinuation);

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
	boolean equalsMapType (AvailObject aMapType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsNybbleTuple (AvailObject aNybbleTuple);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsObject (AvailObject anObject);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsObjectTuple (AvailObject anObjectTuple);

	/**
	 * @param aParseNodeType
	 * @return
	 */
	boolean equalsParseNodeType (AvailObject aParseNodeType);

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
	boolean equalsPrimitiveType (AvailObject aPrimitiveType);

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
	boolean equalsInteger (AvailObject anAvailInteger);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsSetType (AvailObject aSetType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsTupleType (AvailObject aTupleType);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsTwoByteString (AvailObject aTwoByteString);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean equalsNil ();

	/**
	 * Dispatch to the descriptor.
	 */
	ExecutionState executionState ();

	/**
	 * Dispatch to the descriptor.
	 */
	void executionState (ExecutionState value);

	/**
	 * Dispatch to the descriptor.
	 */
	void expand ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean extractBoolean ();

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
	List<AvailObject> filterByTypes (List<A_Type> argTypes);

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject filteredBundleTree ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject forZoneSetSubtupleStartSubtupleIndexEndOfZone (
		int zone,
		AvailObject newSubtuple,
		int startSubtupleIndex,
		int endOfZone);

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject getValue ();

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
	boolean hasGrammaticalRestrictions ();

	/**
	 * Dispatch to the descriptor.
	 */
	List<AvailObject> definitionsAtOrBelow (
		List<? extends A_Type> argTypes);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple definitionsTuple ();

	/**
	 * Dispatch to the descriptor.
	 * @param method
	 */
	AvailObject includeBundleNamed (A_Atom messageName, A_BasicObject method);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean includesDefinition (A_BasicObject imp);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map lazyIncomplete ();

	/**
	 * Dispatch to the descriptor.
	 */
	int index ();

	/**
	 * Dispatch to the descriptor.
	 */
	void index (int value);

	/**
	 * Dispatch to the descriptor.
	 */
	void clearInterruptRequestFlags ();

	/**
	 * Dispatch to the descriptor.
	 */
	void countdownToReoptimize (int value);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isAbstract ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isAbstractDefinition ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean isBetterRepresentationThanTupleType (
		A_BasicObject aTupleType);

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
	 * Is the {@linkplain AvailObject receiver} a {@linkplain
	 * ForwardDefinitionDescriptor forward declaration site}?
	 *
	 * @return {@code true} if the receiver is a forward declaration site.
	 */
	boolean isForwardDefinition ();

	/**
	 * Is the {@linkplain AvailObject receiver} a {@linkplain
	 * MethodDefinitionDescriptor method definition}?
	 *
	 * @return {@code true} if the receiver is a method definition.
	 */
	boolean isMethodDefinition ();

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
	 * Dispatch to the descriptor.
	 */
	boolean isSaved ();

	/**
	 * Dispatch to the descriptor.
	 */
	void isSaved (boolean aBoolean);

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
	 * Dispatch to the descriptor.
	 */
	boolean isSubsetOf (A_Set another);

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
	boolean isValid ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject levelTwoChunk ();

	/**
	 * Dispatch to the descriptor.
	 */
	void levelTwoChunkOffset (A_BasicObject chunk, int offset);

	/**
	 * Dispatch to the descriptor.
	 */
	int levelTwoOffset ();

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject literal ();

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject literalAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject argOrLocalOrStackAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void argOrLocalOrStackAtPut (int index, AvailObject value);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type localTypeAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject lookupByTypesFromTuple (
		A_Tuple argumentTypeTuple);

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject lookupByValuesFromList (
		List<? extends A_BasicObject> argumentList);

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
	int maxStackDepth ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Atom message ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple messageParts ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Set methodDefinitions ();

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
	A_Map importedNames ();

	/**
	 * Dispatch to the descriptor.
	 */
	boolean nameVisible (AvailObject trueName);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map newNames ();

	/**
	 * Dispatch to the descriptor.
	 */
	int numArgs ();

	/**
	 * Dispatch to the descriptor.
	 */
	int numArgsAndLocalsAndStack ();

	/**
	 * Dispatch to the descriptor.
	 */
	int numberOfZones ();

	/**
	 * Dispatch to the descriptor.
	 */
	int numDoubles ();

	/**
	 * Dispatch to the descriptor.
	 */
	int numIntegers ();

	/**
	 * Dispatch to the descriptor.
	 */
	int numLiterals ();

	/**
	 * Dispatch to the descriptor.
	 */
	int numLocals ();

	/**
	 * Dispatch to the descriptor.
	 */
	int numObjects ();

	/**
	 * Dispatch to the descriptor.
	 */
	int numOuters ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple nybbles ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type outerTypeAt (int index);

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
	int pc ();

	/**
	 * Dispatch to the descriptor.
	 */
	void pc (int value);

	/**
	 * Dispatch to the descriptor.
	 */
	void postFault ();

	/**
	 * Dispatch to the descriptor.
	 */
	int primitiveNumber ();

	/**
	 * Dispatch to the descriptor.
	 */
	int priority ();

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
	A_Map privateNames ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map fiberGlobals ();

	/**
	 * Dispatch to the descriptor.
	 */
	void fiberGlobals (A_Map value);

	/**
	 * Dispatch to the descriptor.
	 */
	short rawByteAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void rawByteAtPut (int index, short anInteger);

	/**
	 * Dispatch to the descriptor.
	 */
	short rawByteForCharacterAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void rawByteForCharacterAtPut (int index, short anInteger);

	/**
	 * Dispatch to the descriptor.
	 */
	byte rawNybbleAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void rawNybbleAtPut (int index, byte aNybble);

	/**
	 * Dispatch to the descriptor.
	 */
	int rawShortForCharacterAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void rawShortForCharacterAtPut (int index, int anInteger);

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
	void readBarrierFault ();

	/**
	 * Dispatch to the descriptor.
	 */
	void removeDependentChunkIndex (int aChunkIndex);

	/**
	 * Dispatch to the descriptor.
	 */
	void removeDefinition (A_BasicObject definition);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean removeBundleNamed (A_Atom message);

	/**
	 * Dispatch to the descriptor.
	 */
	void removeGrammaticalRestrictions (
		A_Tuple obsoleteRestrictions);

	/**
	 * Dispatch to the descriptor.
	 */
	void resolveForward (A_BasicObject forwardDefinition);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple grammaticalRestrictions ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type returnType ();

	/**
	 * Dispatch to the descriptor.
	 */
	void scanSubobjects (AvailSubobjectVisitor visitor);

	/**
	 * Dispatch to the descriptor.
	 */
	void setSubtupleForZoneTo (
		int zoneIndex,
		A_Tuple newTuple);

	/**
	 * Dispatch to the descriptor.
	 */
	void setValue (A_BasicObject newValue);

	/**
	 * Dispatch to the descriptor.
	 */
	void setValueNoCheck (AvailObject newValue);

	/**
	 * Dispatch to the descriptor.
	 */
	void size (int value);

	/**
	 * Dispatch to the descriptor.
	 */
	int sizeOfZone (int zone);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type sizeRange ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map lazyActions ();

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject stackAt (int slotIndex);

	/**
	 * Dispatch to the descriptor.
	 */
	void stackAtPut (int slotIndex, A_BasicObject anObject);

	/**
	 * Dispatch to the descriptor.
	 */
	int stackp ();

	/**
	 * Dispatch to the descriptor.
	 */
	void stackp (int value);

	/**
	 * Dispatch to the descriptor.
	 */
	int start ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject startingChunk ();

	/**
	 * Dispatch to the descriptor.
	 */
	void setStartingChunkAndReoptimizationCountdown (
		A_BasicObject chunk,
		int countdown);

	/**
	 * Dispatch to the descriptor.
	 */
	int startOfZone (int zone);

	/**
	 * Dispatch to the descriptor.
	 */
	int startSubtupleIndexInZone (int zone);

	/**
	 * Dispatch to the descriptor.
	 */
	void step ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_String string ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple testingTree ();

	/**
	 * Dispatch to the descriptor.
	 */
	int translateToZone (int tupleIndex, int zoneIndex);

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
	A_Set trueNamesForStringName (A_String stringName);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple truncateTo (int newTupleSize);

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
	A_Type typeIntersection (A_Type another);

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
	A_Tuple typeTuple ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type typeUnion (A_Type another);

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
	AvailObject value ();

	/**
	 * Dispatch to the descriptor.
	 */
	void value (A_BasicObject value);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Map variableBindings ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple vectors ();

	/**
	 * Dispatch to the descriptor.
	 */
	void verify ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Set visibleNames ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple wordcodes ();

	/**
	 * Dispatch to the descriptor.
	 */
	int zoneForIndex (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple parsingInstructions ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject expression ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_BasicObject variable ();

	/**
	 * @return
	 */
	A_BasicObject argumentsTuple ();

	/**
	 * @return
	 */
	A_Tuple statementsTuple ();

	/**
	 * @return
	 */
	A_BasicObject resultType ();

	/**
	 * @param neededVariables
	 */
	void neededVariables (A_Tuple neededVariables);

	/**
	 * @return
	 */
	A_BasicObject neededVariables ();

	/**
	 * @return
	 */
	int primitive ();

	/**
	 * @return
	 */
	AvailObject declaredType ();

	/**
	 * @return
	 */
	DeclarationKind declarationKind ();

	/**
	 * @return
	 */
	AvailObject initializationExpression ();

	/**
	 * @param initializationExpression
	 */
	void initializationExpression (
		AvailObject initializationExpression);

	/**
	 * @return
	 */
	A_BasicObject literalObject ();

	/**
	 * @return
	 */
	A_Token token ();

	/**
	 * @return
	 */
	A_BasicObject markerValue ();

	/**
	 * @return
	 */
	A_BasicObject argumentsListNode ();

	/**
	 * @return
	 */
	A_BasicObject method ();

	/**
	 * @return
	 */
	A_Tuple expressionsTuple ();

	/**
	 * @return
	 */
	A_BasicObject declaration ();

	/**
	 * @return
	 */
	A_Type expressionType ();

	/**
	 * @param codeGenerator
	 */
	void emitEffectOn (AvailCodeGenerator codeGenerator);

	/**
	 * @param codeGenerator
	 */
	void emitValueOn (AvailCodeGenerator codeGenerator);

	/**
	 * @param aBlock
	 */
	void childrenMap (
		Transformer1<AvailObject, AvailObject> aBlock);

	/**
	 * @param aBlock
	 */
	void childrenDo (Continuation1<AvailObject> aBlock);

	/**
	 * @param parent
	 */
	void validateLocally (@Nullable A_BasicObject parent);

	/**
	 * @param module
	 * @return
	 */
	A_BasicObject generateInModule (A_BasicObject module);

	/**
	 * @param newParseNode
	 * @return
	 */
	AvailObject copyWith (AvailObject newParseNode);

	/**
	 * @param isLastUse
	 */
	void isLastUse (boolean isLastUse);

	/**
	 * @return
	 */
	boolean isLastUse ();

	/**
	 * @return
	 */
	boolean isMacroDefinition ();

	/**
	 * @return
	 */
	AvailObject copyMutableParseNode ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Type binUnionKind ();

	/**
	 * @return
	 */
	A_BasicObject outputParseNode ();

	/**
	 * @return
	 */
	A_BasicObject apparentSendName ();

	/**
	 * @return
	 */
	A_Tuple statements ();

	/**
	 * @param accumulatedStatements
	 */
	void flattenStatementsInto (
		List<AvailObject> accumulatedStatements);

	/**
	 * @return
	 */
	int lineNumber ();

	/**
	 * @return
	 */
	A_Map allBundles ();

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
	A_Map complete ();

	/**
	 * @return
	 */
	A_Map incomplete ();

	/**
	 * @return
	 */
	A_Set declaredExceptions ();

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
	 * @param aCompiledCodeType
	 * @return
	 */
	boolean isSupertypeOfCompiledCodeType (
		A_Type aCompiledCodeType);

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	A_Type typeUnionOfCompiledCodeType (
		A_Type aCompiledCodeType);

	/**
	 * @param key
	 * @param value
	 */
	void setAtomProperty (A_Atom key, A_BasicObject value);

	/**
	 * @param key
	 * @return
	 */
	AvailObject getAtomProperty (A_Atom key);

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
	 * @param value
	 */
	void versions (A_BasicObject value);

	/**
	 * @return
	 */
	A_Set versions ();

	/**
	 * @return
	 */
	ParseNodeKind parseNodeKind ();

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
	 * @param restrictionSignature
	 */
	void addTypeRestriction (A_Function restrictionSignature);

	/**
	 * @param restrictionSignature
	 */
	void removeTypeRestriction (A_Function function);

	/**
	 * @return
	 */
	A_Tuple typeRestrictions ();

	/**
	 * @param tupleType
	 */
	void addSealedArgumentsType (A_Tuple typeTuple);

	/**
	 * @param tupleType
	 */
	void removeSealedArgumentsType (A_Tuple typeTuple);

	/**
	 * @return
	 */
	A_BasicObject sealedArgumentsTypesTuple ();

	/**
	 * @param methodNameAtom
	 * @param typeRestrictionFunction
	 */
	void addTypeRestriction (
		A_Atom methodNameAtom,
		A_Function typeRestrictionFunction);

	/**
	 * @param name
	 * @param constantBinding
	 */
	void addConstantBinding (
		A_String name,
		AvailObject constantBinding);

	/**
	 * @param name
	 * @param variableBinding
	 */
	void addVariableBinding (
		A_String name,
		AvailObject variableBinding);

	/**
	 * @return
	 */
	boolean isMethodEmpty ();

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
	A_Map lazyPrefilterMap ();

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
	A_BasicObject issuingModule ();

	/**
	 * @return
	 */
	boolean isPojoFusedType ();

	/**
	 * @param aPojoType
	 * @return
	 */
	boolean isSupertypeOfPojoBottomType (A_BasicObject aPojoType);

	/**
	 * @return
	 */
	boolean equalsPojoBottomType ();

	/**
	 * @return
	 */
	AvailObject javaAncestors ();

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
	A_Map lazyIncompleteCaseInsensitive ();

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
	long totalInvocations ();

	/**
	 *
	 */
	void tallyInvocation ();

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
	boolean isSystemModule ();

	/**
	 * @return
	 */
	A_Type literalType ();

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
	 * @return
	 */
	boolean isLiteralTokenType ();

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	boolean equalsLiteralTokenType (
		A_BasicObject aLiteralTokenType);

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
	 * @param methodName
	 * @param sealSignature
	 */
	void addSeal (
		A_Atom methodName,
		A_Tuple sealSignature);

	/**
	 * @return
	 */
	boolean isInstanceMeta ();

	/**
	 * @return
	 */
	AvailObject instance ();

	/**
	 * @return
	 */
	int allocateFromCounter ();

	/**
	 * @param methodName
	 */
	void setMethodName (A_String methodName);

	/**
	 * @return
	 */
	int startingLineNumber ();

	/**
	 * @return
	 */
	A_BasicObject module ();

	/**
	 * @return
	 */
	A_String methodName ();

	/**
	 * @param kind
	 * @return
	 */
	boolean binElementsAreAllInstancesOfKind (A_Type kind);

	/**
	 * @param kind
	 * @return
	 */
	boolean setElementsAreAllInstancesOfKind (AvailObject kind);

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
	 * @param isSystemModule
	 */
	void isSystemModule (boolean isSystemModule);

	/**
	 * @return
	 */
	boolean isMarkerNode ();

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
	boolean equalsParseNode (A_BasicObject aParseNode);

	/**
	 * @return
	 */
	AvailObject stripMacro ();

	/**
	 * Answer the {@link MethodDescriptor method} that this {@link
	 * DefinitionDescriptor definition} is for.
	 *
	 * @return The definition's method.
	 */
	A_BasicObject definitionMethod ();

	/**
	 * @return
	 */
	A_Tuple prefixFunctions ();

	/**
	 * @return
	 */
	byte[] byteArray ();

	/**
	 * @return
	 */
	boolean isByteArrayTuple ();

	/**
	 * @param message
	 */
	void flushForNewOrChangedBundleNamed (A_Atom message);

	/**
	 * @param critical
	 */
	void lock (Continuation0 critical);

	/**
	 * @return
	 */
	A_String moduleName ();

	/**
	 * @return
	 */
	A_Set namesSet ();

	/**
	 * @return
	 */
	A_Atom originalName ();

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
	 * @return
	 */
	AvailObject bundleMethod ();

	/**
	 * @return
	 */
	Continuation1<Throwable> failureContinuation ();

	/**
	 * @param scheduled
	 * @param b
	 * @return
	 */
	boolean getAndSetSynchronizationFlag (
		SynchronizationFlag scheduled,
		boolean b);

	/**
	 * @param onSuccess
	 */
	void resultContinuation (Continuation1<AvailObject> onSuccess);

	/**
	 * @param onFailure
	 */
	void failureContinuation (Continuation1<Throwable> onFailure);

	/**
	 * @param result
	 */
	void fiberResult (A_BasicObject result);

	/**
	 * @return
	 */
	Continuation1<AvailObject> resultContinuation ();

	/**
	 * @param flag
	 * @return
	 */
	boolean interruptRequestFlag (InterruptRequestFlag flag);

	/**
	 * @param newValue
	 * @return
	 */
	AvailObject getAndSetValue (AvailObject newValue);

	/**
	 * @param reference
	 * @param newValue
	 * @return
	 */
	boolean compareAndSwapValues (AvailObject reference, AvailObject newValue);

	/**
	 * @param addend
	 * @return
	 */
	A_Number fetchAndAddValue (A_Number addend);

	/**
	 * @param flag
	 * @return
	 */
	boolean getAndClearInterruptRequestFlag (InterruptRequestFlag flag);

	/**
	 * @return
	 */
	AvailObject fiberResult ();

	/**
	 * @return
	 */
	A_Set joiningFibers ();

	/**
	 * @return
	 */
	AvailObject joinee ();

	/**
	 * @param joinee
	 */
	void joinee (A_BasicObject joinee);

	/**
	 * @return
	 */
	@Nullable TimerTask wakeupTask ();

	/**
	 * @param task
	 */
	void wakeupTask (@Nullable TimerTask task);

	/**
	 * @param flag
	 */
	void setInterruptRequestFlag (InterruptRequestFlag flag);

	/**
	 * @param continuation
	 */
	void decrementCountdownToReoptimize (Continuation0 continuation);

	/**
	 * @param value
	 */
	void priority (int value);

	/**
	 * @return
	 */
	@Nullable AvailLoader availLoader ();

	/**
	 * @param loader
	 */
	void availLoader (@Nullable AvailLoader loader);

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
	 * @param empty
	 */
	void joiningFibers (A_Set empty);

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

	/**
	 * @return
	 */
	A_Map heritableFiberGlobals ();

	/**
	 * @param globals
	 */
	void heritableFiberGlobals (A_Map globals);

	/**
	 * @param flag
	 * @return
	 */
	boolean generalFlag (GeneralFlag flag);

	/**
	 * @param flag
	 */
	void setGeneralFlag (GeneralFlag flag);

	/**
	 * @param flag
	 */
	void clearGeneralFlag (GeneralFlag flag);
}
