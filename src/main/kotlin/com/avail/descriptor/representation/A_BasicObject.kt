/*
 * A_BasicObject.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.representation

import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.functions.A_Continuation
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.objects.ObjectDescriptor
import com.avail.descriptor.objects.ObjectTypeDescriptor
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ByteStringDescriptor
import com.avail.descriptor.tuples.ByteTupleDescriptor
import com.avail.descriptor.tuples.IntTupleDescriptor
import com.avail.descriptor.tuples.LongTupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TwoByteStringDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.declaredExceptions
import com.avail.descriptor.types.A_Type.Companion.returnType
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor
import com.avail.descriptor.types.FiberTypeDescriptor
import com.avail.descriptor.types.FunctionTypeDescriptor
import com.avail.descriptor.types.ListPhraseTypeDescriptor
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONFriendly
import com.avail.utility.json.JSONWriter
import com.avail.utility.visitor.AvailSubobjectVisitor
import java.util.IdentityHashMap
import java.util.function.Supplier

/**
 * `A_BasicObject` is an interface that specifies all generally applicable
 * operations that an [AvailObject] must implement.  Its purpose is to
 * declare that only the most basic protocol of some object will be used.  Its
 * sub-interfaces define behavior that's applicable to tuples, sets, etc., and
 * AvailObject simply implements all of those interfaces.
 *
 * The purpose for A_BasicObject and its sub-interfaces is to allow sincere
 * type annotations about the basic kinds of objects that support or may be
 * passed as arguments to various operations.  The VM implementor is free to
 * always declare variables as AvailObject, but in cases where it's clear that
 * a particular object should always be a tuple (say), a declaration of A_Tuple
 * ensures that only the basic object capabilities plus tuple-like capabilities
 * are allowed to be used on it.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_BasicObject : JSONFriendly {
	/**
	 * Retrieve the object's {@linkplain AbstractDescriptor descriptor}.
	 *
	 * When Avail moves off the JVM for its object storage, this read will be
	 * volatile only for objects in shared space.  A non-volatile read is
	 * sufficient to get a descriptor which can then be checked to see if it's
	 * one that is allowed in shared space, and if so, a second volatile read
	 * can be performed.
	 *
	 * @return A descriptor.
	 */
	fun descriptor(): AbstractDescriptor

	/**
	 * Replace the object's {@linkplain AbstractDescriptor descriptor}.
	 *
	 * When Avail moves off the JVM for its object storage, this will only be a
	 * volatile write for objects in shared space.
	 *
	 * @return A descriptor.
	 */
	fun setDescriptor(newDescriptor: AbstractDescriptor)

	/**
	 * Answer whether the [objects][AvailObject] occupy the same
	 * memory addresses.
	 *
	 * @param anotherObject Another object.
	 * @return Whether the objects occupy the same storage.
	 */
	fun sameAddressAs(anotherObject: A_BasicObject): Boolean

	/**
	 * Turn the receiver into an [indirection][IndirectionDescriptor] to the
	 * specified [object][AvailObject].
	 *
	 * **WARNING:** This alters the receiver's slots and descriptor.
	 *
	 * **WARNING:** A [shared][Mutability.SHARED] object may not become an
	 * indirection. The caller must ensure that this method is not sent to a
	 * shared object.
	 *
	 * @param anotherObject
	 *   An object.
	 */
	fun becomeIndirectionTo(anotherObject: A_BasicObject)

	/**
	 * Answer the number of integer slots. All variable integer slots occur
	 * following the last fixed integer slot.
	 *
	 * @return The number of integer slots.
	 */
	fun integerSlotsCount(): Int

	/**
	 * Answer the number of variable integer slots in this object. This does not
	 * include the fixed integer slots.
	 *
	 * @return The number of variable integer slots.
	 */
	fun variableIntegerSlotsCount(): Int

	/**
	 * Answer the number of object slots in this [AvailObject]. All
	 * variable object slots occur following the last fixed object slot.
	 *
	 * @return The number of object slots.
	 */
	fun objectSlotsCount(): Int

	/**
	 * Answer the number of variable object slots in this [AvailObject].
	 * This does not include the fixed object slots.
	 *
	 * @return The number of variable object slots.
	 */
	fun variableObjectSlotsCount(): Int

	/**
	 * Recursively print the [receiver][AvailObject] to the [builder] unless it
	 * is already present in the [recursionMap]. Printing will begin at the
	 * specified [indent] level, measured in horizontal tab characters.
	 *
	 * This operation exists primarily to provide useful representations of
	 * `AvailObject`s for Java-side debugging.
	 *
	 * @param builder
	 *   A [StringBuilder].
	 * @param recursionMap
	 *   An [IdentityHashMap] whose keys are [AvailObject]s already visited (but
	 *   not yet completed) during the recursive print.  The associated values
	 *   are unused.
	 * @param indent
	 *   The indent level, in horizontal tabs, at which new lines should be
	 *   written.
	 */
	fun printOnAvoidingIndent(
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)

	/**
	 * Utility method for decomposing this object in the debugger.
	 *
	 * @return
	 *   An array of [AvailObjectFieldHelper] objects that help describe the
	 *   logical structure of the receiver to the debugger.
	 */
	fun describeForDebugger(): Array<AvailObjectFieldHelper>

	/**
	 * Answer a name suitable for labeling a field containing this object.
	 *
	 * @return A Kotlin [String].
	 */
	fun nameForDebugger(): String

	/**
	 * Answer whether to show value-specific content in the file name for the
	 * debugger.
	 *
	 * @return Whether to show the value.
	 */
	fun showValueInNameForDebugger(): Boolean

	/**
	 * Replace my descriptor field with a [FillerDescriptor].  This blows
	 * up for most messages, catching incorrect (all, by definition) further
	 * accidental uses of this object.
	 */
	fun setToInvalidDescriptor()

	/**
	 * Compute the 32-bit hash of the receiver.
	 *
	 * @return An [Int] hash value.
	 */
	fun hash(): Int
	override fun hashCode(): Int

	/**
	 * {@inheritDoc}
	 *
	 * This comparison operation takes an [Object] as its argument to avoid
	 * accidentally calling this with, say, a [String] literal. We mark it as
	 * deprecated to ensure we don't accidentally invoke this method when we
	 * really mean the version that takes an `AvailObject` as an argument.
	 * Eclipse conveniently shows such invocations with a <span
	 * style="text-decoration: line-through">strike-out</span>.  That's a
	 * convenient warning for the programmer, but we also fail if this method
	 * actually gets invoked AND the argument is not an `AvailObject`.  That
	 * means we don't allow AvailObjects to be added to Java [sets][Set] and
	 * such, at least when they're intermixed with things that are not
	 * AvailObjects.
	 */
	@Deprecated("")
	override fun equals(other: Any?): Boolean

	/**
	 * Answer whether the receiver and the argument, both [A_BasicObject]s, are
	 * equal in value.
	 *
	 * Note that the argument is of type [AvailObject] so that correctly
	 * typed uses (where the argument is statically known to be an AvailObject)
	 * proceed normally. Incorrectly typed uses (where the argument is an
	 * arbitrary Java [Object] should show up as calling a deprecated
	 * method, and should fail at runtime if the argument is not actually an
	 * AvailObject.
	 *
	 * @param another The object to be compared to the receiver.
	 * @return `true` if the two objects are of equal value, `false`
	 * otherwise.
	 */
	@ReferencedInGeneratedCode
	fun equals(another: A_BasicObject): Boolean

	/**
	 * Answer whether the receiver, an [object][AvailObject], and the
	 * argument, a [tuple][TupleDescriptor], are equal in value.
	 *
	 * @param aTuple The tuple to be compared to the receiver.
	 * @return `true` if the receiver is a tuple and of value equal to the
	 * argument, `false` otherwise.
	 */
	fun equalsAnyTuple(aTuple: A_Tuple): Boolean

	/**
	 * Answer whether the receiver, an [object][AvailObject], and the
	 * argument, a [byte&#32;string][ByteStringDescriptor], are equal in
	 * value.
	 *
	 * @param aByteString The byte string to be compared to the receiver.
	 * @return `true` if the receiver is a byte string and of value equal
	 * to the argument, `false` otherwise.
	 */
	fun equalsByteString(aByteString: A_String): Boolean

	/**
	 * Answer whether the receiver, an [object][AvailObject], and the
	 * argument, a [byte&#32;tuple][ByteTupleDescriptor], are equal in
	 * value.
	 *
	 * @param aByteTuple The byte tuple to be compared to the receiver.
	 * @return `true` if the receiver is a byte tuple and of value equal
	 * to the argument, `false` otherwise.
	 */
	fun equalsByteTuple(aByteTuple: A_Tuple): Boolean

	/**
	 * Answer whether the receiver, an [object][AvailObject], and the
	 * argument, a [function][FunctionDescriptor], are equal in value.
	 *
	 * @param aFunction The function used in the comparison.
	 * @return `true` if the receiver is a function and of value equal to
	 * the argument, `false` otherwise.
	 */
	fun equalsFunction(aFunction: A_Function): Boolean

	/**
	 * Answer whether the receiver, an [object][AvailObject], and the
	 * argument, a [fiber&#32;type][FiberTypeDescriptor], are equal in
	 * value.
	 *
	 * @param aFiberType A fiber type.
	 * @return `true` if the receiver is a fiber type and of value equal
	 * to the argument, `false` otherwise.
	 */
	fun equalsFiberType(aFiberType: A_Type): Boolean

	/**
	 * Answer whether the receiver, an [object][AvailObject], and the
	 * argument, a [function&#32;type][FunctionTypeDescriptor], are equal.
	 *
	 * @param aFunctionType The function type used in the comparison.
	 * @return `true` IFF the receiver is also a function type and:
	 *  * The [argument&#32;types][A_Type.argsTupleType] correspond,
	 *  * The [return&#32;types][A_Type.returnType] correspond, and
	 *  * The [raise&#32;types][A_Type.declaredExceptions] correspond.
	 */
	fun equalsFunctionType(aFunctionType: A_Type): Boolean

	/**
	 * Answer whether the arguments, an [object][AvailObject] and a
	 * [compiled&#32;code][CompiledCodeDescriptor], are equal.
	 *
	 * @param aCompiledCode The compiled code used in the comparison.
	 * @return `true` if the receiver is a compiled code and of value
	 * equal to the argument, `false` otherwise.
	 */
	fun equalsCompiledCode(aCompiledCode: A_RawFunction): Boolean

	/**
	 * Answer whether the arguments, an [object][AvailObject] and a
	 * [variable][VariableDescriptor], are the exact same object,
	 * comparing by address (Java object identity). There's no need to traverse
	 * the objects before comparing addresses, because this message was a
	 * double-dispatch that would have skipped (and stripped) the indirection
	 * objects in either path.
	 *
	 * @param aVariable The variable used in the comparison.
	 * @return `true` if the receiver is a variable with the same identity
	 * as the argument, `false` otherwise.
	 */
	fun equalsVariable(aVariable: A_Variable): Boolean

	/**
	 * Answer whether the receiver equals the argument.
	 *
	 * @param aVariableType
	 *   A variable type.
	 * @return
	 *   The result of comparing the receiver and aVariableType.
	 */
	fun equalsVariableType(aVariableType: A_Type): Boolean

	/**
	 * Answer whether the receiver equals the argument.
	 *
	 * @param aContinuation
	 *   A continuation.
	 * @return
	 *   The result of comparing the receiver and aContinuation.
	 */
	fun equalsContinuation(aContinuation: A_Continuation): Boolean

	/**
	 * Answer whether the receiver equals the argument.
	 *
	 * @param aContinuationType
	 *   A continuation type.
	 * @return
	 *   The result of comparing the receiver and aContinuationType.
	 */
	fun equalsContinuationType(aContinuationType: A_Type): Boolean

	/**
	 * Answer whether the receiver equals the argument.
	 *
	 * @param anIntegerRangeType An integer range type.
	 * @return The result of comparing the receiver and anIntegerRangeType.
	 */
	fun equalsIntegerRangeType(
		anIntegerRangeType: A_Type): Boolean

	/**
	 * Answer whether the receiver equals the argument.
	 *
	 * @param aMap
	 *   An Avail map.
	 * @return
	 *   The result of comparing the receiver and aMap.
	 */
	fun equalsMap(aMap: A_Map): Boolean

	/**
	 * Answer whether the receiver equals the argument.
	 *
	 * @param aMapType
	 *   A map type.
	 * @return
	 *   The result of comparing the receiver and aMapType.
	 */
	fun equalsMapType(aMapType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	fun equalsNybbleTuple(aNybbleTuple: A_Tuple): Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	fun equalsObject(anObject: AvailObject): Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	fun equalsObjectTuple(anObjectTuple: A_Tuple): Boolean

	/**
	 * @param aPhraseType
	 * @return
	 */
	fun equalsPhraseType(aPhraseType: A_Type): Boolean

	/**
	 * @param aPojo
	 * @return
	 */
	fun equalsPojo(aPojo: AvailObject): Boolean

	/**
	 * @param aPojoType
	 * @return
	 */
	fun equalsPojoType(aPojoType: AvailObject): Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	fun equalsPrimitiveType(aPrimitiveType: A_Type): Boolean

	/**
	 * @param otherRawPojo
	 * @param otherJavaObject
	 * @return
	 */
	fun equalsRawPojoFor(
		otherRawPojo: AvailObject,
		otherJavaObject: Any?): Boolean

	/**
	 * @param aTuple
	 * @return boolean
	 */
	fun equalsReverseTuple(aTuple: A_Tuple): Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	fun equalsSetType(aSetType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	fun equalsTupleType(aTupleType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	fun equalsTwoByteString(aTwoByteString: A_String): Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	fun equalsNil(): Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	fun fieldMap(): A_Map

	/**
	 * Dispatch to the descriptor.
	 */
	fun hashOrZero(): Int

	/**
	 * Dispatch to the descriptor.
	 */
	fun setHashOrZero(value: Int)

	/**
	 * Dispatch to the descriptor.
	 */
	val isAbstract: Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	fun representationCostOfTupleType(): Int

	/**
	 * Is the [receiver][AvailObject] an Avail boolean?
	 *
	 * @return `true` if the receiver is a boolean, `false`
	 * otherwise.
	 */
	val isBoolean: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail unsigned byte?
	 *
	 * @return `true` if the argument is an unsigned byte, `false`
	 * otherwise.
	 */
	val isUnsignedByte: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail
	 * [byte&#32;string][ByteStringDescriptor]?
	 *
	 * @return
	 *   `true` if the receiver is a byte string, `false` otherwise.
	 */
	val isByteString: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail
	 * [byte&#32;tuple][ByteTupleDescriptor]?
	 *
	 * @return
	 *   `true` if the receiver is a byte tuple, `false` otherwise.
	 */
	val isByteTuple: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail function?
	 *
	 * @return `true` if the receiver is a function, `false`
	 * otherwise.
	 */
	val isFunction: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail atom?
	 *
	 * @return `true` if the receiver is an atom, `false`
	 * otherwise.
	 */
	val isAtom: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail extended integer?
	 *
	 * @return `true` if the receiver is an extended integer, `false` otherwise.
	 */
	val isExtendedInteger: Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	val isFinite: Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	@ReferencedInGeneratedCode
	fun isInstanceOf(aType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	fun isInstanceOfKind(aType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	val isIntegerRangeType: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail [IntTupleDescriptor]?  This is
	 * conservative, in that some object tuples *may* only contain ints but not
	 * be reported as being int tuples.
	 *
	 * @return
	 *   `true` if the receiver is easily determined to be an int tuple, `false`
	 *   otherwise.
	 */
	val isIntTuple: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail [LongTupleDescriptor]?  This is
	 * conservative, in that some object tuples *may* only contain longs but not
	 * be reported as being long tuples.
	 *
	 * @return
	 *   `true` if the receiver is easily determined to be a long tuple, `false`
	 *   otherwise.
	 */
	val isLongTuple: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail map?
	 *
	 * @return `true` if the receiver is a map, `false` otherwise.
	 */
	val isMap: Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	val isMapType: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail nybble?
	 *
	 * @return `true` if the receiver is a nybble, `false`
	 * otherwise.
	 */
	val isNybble: Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	val isSetType: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail string?
	 *
	 * @return `true` if the receiver is an Avail string, `false`
	 * otherwise.
	 */
	val isString: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail tuple?
	 *
	 * @return `true` if the receiver is a tuple, `false` otherwise.
	 */
	val isTuple: Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	val isTupleType: Boolean

	/**
	 * Is the [receiver][AvailObject] an Avail
	 * [two-byte&#32;string][TwoByteStringDescriptor]?
	 *
	 * @return
	 *   `true` if the receiver is a two-byte string, `false` otherwise.
	 */
	val isTwoByteString: Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	val isType: Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	@ReferencedInGeneratedCode
	fun makeImmutable(): AvailObject

	/**
	 * Dispatch to the descriptor.
	 */
	fun makeShared(): AvailObject

	/**
	 * Dispatch to the descriptor.
	 * @return
	 */
	@ReferencedInGeneratedCode
	fun makeSubobjectsImmutable(): AvailObject

	/**
	 * Dispatch to the descriptor.
	 */
	fun makeSubobjectsShared(): AvailObject

	/**
	 * Dispatch to the descriptor.
	 */
	fun scanSubobjects(visitor: AvailSubobjectVisitor)

	/**
	 * Dispatch to the descriptor.
	 */
	@ReferencedInGeneratedCode
	fun traversed(): AvailObject

	/**
	 * Dispatch to the descriptor.
	 */
	fun kind(): A_Type

	/**
	 * @return
	 */
	fun resultType(): A_Type

	/**
	 * @return
	 */
	fun declarationKind(): DeclarationKind

	/**
	 * @return
	 */
	@get:ReferencedInGeneratedCode
	val isInt: Boolean

	/**
	 * @return
	 */
	val isLong: Boolean

	/**
	 * @param anInstanceType
	 * @return
	 */
	fun equalsInstanceTypeFor(anInstanceType: AvailObject): Boolean

	/**
	 * Determine whether the receiver is an
	 * [enumeration][AbstractEnumerationTypeDescriptor] with the given
	 * [set][A_Set] of instances.
	 *
	 * @param aSet A set of objects.
	 * @return Whether the receiver is an enumeration with the given
	 * membership.
	 */
	fun equalsEnumerationWithSet(aSet: A_Set): Boolean

	/**
	 * @param potentialInstance
	 * @return
	 */
	fun enumerationIncludesInstance(
		potentialInstance: AvailObject): Boolean

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	fun equalsCompiledCodeType(
		aCompiledCodeType: A_Type): Boolean

	/**
	 * @param anEnumerationType
	 * @return
	 */
	fun equalsEnumerationType(anEnumerationType: A_BasicObject): Boolean

	/**
	 * @return
	 */
	val isRawPojo: Boolean

	/**
	 * @return
	 */
	val isPojoSelfType: Boolean

	/**
	 * @return
	 */
	fun pojoSelfType(): A_Type

	/**
	 * @return
	 */
	fun javaClass(): A_BasicObject

	/**
	 * @return
	 */
	val isUnsignedShort: Boolean

	/**
	 * @return
	 */
	val isFloat: Boolean

	/**
	 * @return
	 */
	@get:ReferencedInGeneratedCode
	val isDouble: Boolean

	/**
	 * @return
	 */
	fun rawPojo(): AvailObject

	/**
	 * @return
	 */
	val isPojo: Boolean

	/**
	 * @return
	 */
	val isPojoType: Boolean

	/**
	 * @return
	 */
	fun serializerOperation(): SerializerOperation

	/**
	 * @return
	 */
	val isPojoFusedType: Boolean

	/**
	 * @return
	 */
	fun equalsPojoBottomType(): Boolean

	/**
	 * @return
	 */
	fun javaAncestors(): AvailObject

	/**
	 * @return
	 */
	val isPojoArrayType: Boolean

	/**
	 * @param classHint
	 * @return
	 */
	fun marshalToJava(classHint: Class<*>?): Any?

	/**
	 * @param field
	 * @param receiver
	 * @return
	 */
	fun equalsPojoField(
		field: AvailObject,
		receiver: AvailObject): Boolean

	/**
	 * @param otherEqualityRawPojo
	 * @param otherJavaObject
	 * @return
	 */
	fun equalsEqualityRawPojoFor(
		otherEqualityRawPojo: AvailObject,
		otherJavaObject: Any?): Boolean

	/**
	 * @return
	 */
	fun <T : Any> javaObject(): T?

	/**
	 * @return
	 */
	fun <T : Any> javaObjectNotNull(): T

	/**
	 * @return
	 */
	fun fieldTuple(): A_Tuple

	/**
	 * @return
	 */
	val isTokenType: Boolean

	/**
	 * @return
	 */
	val isLiteralTokenType: Boolean

	/**
	 * @param aTokenType
	 * @return
	 */
	fun equalsTokenType(
		aTokenType: A_Type): Boolean

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	fun equalsLiteralTokenType(
		aLiteralTokenType: A_Type): Boolean

	/**
	 * @param anObjectType
	 * @return
	 */
	fun equalsObjectType(anObjectType: AvailObject): Boolean

	/**
	 * @param aToken
	 * @return
	 */
	fun equalsToken(aToken: A_Token): Boolean

	/**
	 * @return
	 */
	val isInstanceMeta: Boolean

	/**
	 * @param aPhrase
	 * @return
	 */
	fun equalsPhrase(aPhrase: A_Phrase): Boolean

	/**
	 * @return
	 */
	val isByteArrayTuple: Boolean

	/**
	 * @return
	 */
	val isSignedByte: Boolean

	/**
	 * @return
	 */
	val isSignedShort: Boolean

	/**
	 * @param aByteArrayTuple
	 * @return
	 */
	fun equalsByteArrayTuple(aByteArrayTuple: A_Tuple): Boolean

	/**
	 * @param aByteBufferTuple
	 * @return
	 */
	fun equalsByteBufferTuple(aByteBufferTuple: A_Tuple): Boolean

	/**
	 * @return
	 */
	val isByteBufferTuple: Boolean

	/**
	 * @return
	 */
	val isIntegerIntervalTuple: Boolean

	/**
	 * @return
	 */
	val isSmallIntegerIntervalTuple: Boolean

	/**
	 * @return
	 */
	val isRepeatedElementTuple: Boolean

	/**
	 * @param anIntegerIntervalTuple
	 * @return
	 */
	fun equalsIntegerIntervalTuple(anIntegerIntervalTuple: A_Tuple): Boolean

	/**
	 * @param anIntTuple
	 * @return
	 */
	fun equalsIntTuple(anIntTuple: A_Tuple): Boolean

	/**
	 * @param aLongTuple
	 * @return
	 */
	fun equalsLongTuple(aLongTuple: A_Tuple): Boolean

	/**
	 * @param aSmallIntegerIntervalTuple
	 * @return
	 */
	fun equalsSmallIntegerIntervalTuple(
		aSmallIntegerIntervalTuple: A_Tuple): Boolean

	/**
	 * @param aRepeatedElementTuple
	 * @return
	 */
	fun equalsRepeatedElementTuple(aRepeatedElementTuple: A_Tuple): Boolean

	/**
	 * Lock the fiber during evaluation of the [Supplier], and return the
	 * produced value.
	 *
	 * @param T
	 *   The type of value to produce while holding the lock.
	 * @param body
	 *   The body to evaluate.
	 * @return
	 *   The produced value.
	 */
	fun <T> lock(body: () -> T): T

	/**
	 * @return
	 */
	val isInitializedWriteOnceVariable: Boolean

	/**
	 * @param writer
	 */
	fun writeSummaryTo(writer: JSONWriter)

	/**
	 * Answer whether this value equals the given
	 * [list&#32;phrase&#32;type][ListPhraseTypeDescriptor].
	 *
	 * @param listNodeType
	 *   The list phrase type to compare against.
	 * @return
	 *   Whether the receiver equals the given list phrase type.
	 */
	fun equalsListNodeType(listNodeType: A_Type): Boolean

	/**
	 * Extract a field from an [object][ObjectDescriptor].
	 *
	 * @param field
	 *   The field to look up.
	 * @return
	 *   The field's value.
	 */
	fun fieldAt(field: A_Atom): AvailObject

	/**
	 * Extract a field from an [object][ObjectDescriptor], or answer null if
	 * it's not present.
	 *
	 * @param field
	 *   The field to look up.
	 * @return
	 *   The field's value or null.
	 */
	fun fieldAtOrNull(field: A_Atom): AvailObject?

	/**
	 * Add or replace a field of an [object][ObjectDescriptor].
	 *
	 * @param field
	 * @param value
	 * @param canDestroy
	 * @return
	 */
	fun fieldAtPuttingCanDestroy(
		field: A_Atom,
		value: A_BasicObject,
		canDestroy: Boolean): A_BasicObject

	/**
	 * Extract a field type from an [object&#32;type][ObjectTypeDescriptor].
	 *
	 * @param field
	 *   The field to look up.
	 * @return
	 *   The field's type.
	 */
	fun fieldTypeAt(field: A_Atom): A_Type

	/**
	 * Extract a field type from an [object&#32;type][ObjectTypeDescriptor],
	 * or `null` if it's not present.
	 *
	 * @param field
	 *   The field to look up.
	 * @return
	 *   The field's type or null.
	 */
	fun fieldTypeAtOrNull(field: A_Atom): A_Type?

	companion object {
		/**
		 * Dispatcher helper function for routing messages to the descriptor.
		 *
		 * @param R
		 *   The result type of this call.
		 * @param f
		 *   The [AbstractDescriptor] method to invoke, with the receiver cast
		 *   to [AvailObject] as an additional first argument.
		 */
		inline fun <R> A_BasicObject.dispatch(
			f: AbstractDescriptor.(AvailObject) -> R): R =
				descriptor().f(this as AvailObject)

		/**
		 * If the provided condition is true, synchronize with the receiver's
		 * monitor around the execution of the body function.  Otherwise, run
		 * the body function without synchronization.
		 *
		 * @param R
		 *   The type of result produced by the body, if any.
		 * @param syncCondition
		 *   Whether to synchronize on the receiver's monitor.
		 * @param body
		 *   The body to run, either synchronized or unsynchronized.
		 * @receiver
		 *   The result of running the body.
		 */
		inline fun <R> A_BasicObject.synchronizeIf(
			syncCondition: Boolean,
			body: A_BasicObject.() -> R
		): R = when {
			syncCondition -> synchronized(this) { body() }
			else -> body()
		}

		/** The [CheckedMethod] for [.equals].  */
		@JvmField
		val equalsMethod: CheckedMethod = instanceMethod(
			A_BasicObject::class.java,
			A_BasicObject::equals.name,
			Boolean::class.javaPrimitiveType!!,
			A_BasicObject::class.java)

		/** The [CheckedMethod] for [.isInstanceOf].  */
		@JvmField
		val isInstanceOfMethod: CheckedMethod = instanceMethod(
			A_BasicObject::class.java,
			A_BasicObject::isInstanceOf.name,
			Boolean::class.javaPrimitiveType!!,
			A_Type::class.java)

		/** The [CheckedMethod] for [.makeImmutable].  */
		@JvmField
		val makeImmutableMethod: CheckedMethod = instanceMethod(
			A_BasicObject::class.java,
			A_BasicObject::makeImmutable.name,
			AvailObject::class.java)

		/** The [CheckedMethod] for [.makeSubobjectsImmutable].  */
		@Suppress("unused")
		@JvmField
		val makeSubobjectsImmutableMethod: CheckedMethod = instanceMethod(
			A_BasicObject::class.java,
			A_BasicObject::makeSubobjectsImmutable.name,
			AvailObject::class.java)

		/** The [CheckedMethod] for [.traversed].  */
		@JvmField
		val traversedMethod: CheckedMethod = instanceMethod(
			A_BasicObject::class.java,
			A_BasicObject::traversed.name,
			AvailObject::class.java)

		/** The [CheckedMethod] for [.isInt].  */
		@JvmField
		val isIntMethod: CheckedMethod = instanceMethod(
			A_BasicObject::class.java,
			A_BasicObject::isInt.name,
			Boolean::class.javaPrimitiveType!!)

		/** The [CheckedMethod] for [.isDouble].  */
		@JvmField
		val isDoubleMethod: CheckedMethod = instanceMethod(
			A_BasicObject::class.java,
			A_BasicObject::isDouble.name,
			Boolean::class.javaPrimitiveType!!)
	}
}
