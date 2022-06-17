/*
 * AvailObject.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.representation

import avail.compiler.scanning.LexingState
import avail.descriptor.atoms.A_Atom
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_BundleTree
import avail.descriptor.character.A_Character
import avail.descriptor.fiber.A_Fiber
import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.numNybbles
import avail.descriptor.functions.A_RegisterDump
import avail.descriptor.functions.CompiledCodeDescriptor
import avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_MapBin
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_GrammaticalRestriction
import avail.descriptor.methods.A_Macro
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_SemanticRestriction
import avail.descriptor.methods.A_Styler
import avail.descriptor.methods.DefinitionDescriptor
import avail.descriptor.methods.MethodDescriptor
import avail.descriptor.module.A_Module
import avail.descriptor.numbers.A_Number
import avail.descriptor.parsing.A_DefinitionParsingPlan
import avail.descriptor.parsing.A_Lexer
import avail.descriptor.parsing.A_ParsingPlanInProgress
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots.DUMMY_DEBUGGER_SLOT
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_SetBin
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.tokens.A_Token
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.isBetterRepresentationThan
import avail.descriptor.tuples.ByteStringDescriptor
import avail.descriptor.tuples.ByteTupleDescriptor
import avail.descriptor.tuples.IntTupleDescriptor
import avail.descriptor.tuples.IntegerIntervalTupleDescriptor
import avail.descriptor.tuples.LongTupleDescriptor
import avail.descriptor.tuples.RepeatedElementTupleDescriptor
import avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor
import avail.descriptor.tuples.StringDescriptor
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import avail.exceptions.AvailException
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.levelTwo.L2Chunk
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.utility.StackPrinter
import avail.utility.Strings.traceFor
import avail.utility.cast
import org.availlang.json.JSONWriter
import org.jetbrains.annotations.Debug.Renderer
import java.util.IdentityHashMap
import java.util.Spliterator

/**
 * `AvailObject` is the fully realized, and mostly machine generated,
 * implementation of an Avail object. An `AvailObject` must keep track of its
 * [descriptor][AbstractDescriptor], its integer data, and its references to
 * other `AvailObjects`. It specifies the complete complement of messages that
 * can be sent to an `AvailObject`, and delegates most of those to its
 * descriptor, passing the `AvailObject` as an additional first argument. The
 * redirected messages in `AbstractDescriptor` have the prefix "o_", both to
 * make them stand out better and to indicate the additional first argument.
 *
 * @constructor
 * @param descriptor
 *   This object's {@link AbstractDescriptor}.
 * @param objectSlotsSize
 *   The number of object slots to allocate.
 * @param intSlotsSize
 *   The number of integer slots to allocate.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Renderer(
	text = "nameForDebugger",
	childrenArray = "describeForDebugger")
class AvailObject private constructor(
	descriptor: AbstractDescriptor,
	objectSlotsSize: Int,
	intSlotsSize: Int
) : AvailObjectRepresentation(
		descriptor,
		objectSlotsSize,
		intSlotsSize),
	A_BasicObject,
	A_Atom,
	A_Bundle,
	A_BundleTree,
	A_Character,
	A_Continuation,
	A_Definition,
	A_DefinitionParsingPlan,
	A_Fiber,
	A_Function,
	A_GrammaticalRestriction,
	A_Lexer,
	A_Macro,
	A_Map,
	A_MapBin,
	A_Method,
	A_Module,
	A_Number,
	A_ParsingPlanInProgress,
	A_Phrase,
	A_RawFunction,
	A_RegisterDump,
	A_SemanticRestriction,
	A_Set,
	A_SetBin,
	A_String,
	A_Styler,
	A_Token,
	A_Tuple,
	A_Type,
	A_Variable
{
	/**
	 * Recursively print the receiver to the [StringBuilder], unless it is
	 * already present in the recursion [Map]. Printing will begin at the
	 * specified indent level, measured in horizontal tab characters.
	 *
	 * This operation exists primarily to provide useful representations of
	 * `AvailObject`s for Java-side debugging.
	 *
	 * @param builder
	 *   A [StringBuilder].
	 * @param recursionMap
	 *   An [IdentityHashMap] whose keys are [A_BasicObject]s already visited
	 *   during the recursive print.  The values are unused.
	 * @param indent
	 *   The indent level, in horizontal tabs, at which the `AvailObject` should
	 *   be printed.
	 */
	override fun printOnAvoidingIndent(
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	): Unit = with(builder) {
		try
		{
			when {
				isDestroyed -> append("*** A DESTROYED OBJECT ***")
				indent > descriptor().maximumIndent() -> append("*** DEPTH ***")
				recursionMap.containsKey(this@AvailObject) ->
					append("**RECURSION**")
				else ->
					try
					{
						recursionMap[this@AvailObject] = null
						descriptor().printObjectOnAvoidingIndent(
							this@AvailObject, builder, recursionMap, indent)
					}
					finally
					{
						recursionMap.remove(this@AvailObject)
					}
			}
		}
		catch (e: Exception)
		{
			append("EXCEPTION while printing.${StackPrinter.trace(e)}")
		}
		catch (e: AssertionError)
		{
			append("ASSERTION ERROR while printing.${StackPrinter.trace(e)}")
		}
	}

	/**
	 * Utility method for decomposing this object in the debugger.  See
	 * [AvailObjectFieldHelper] for instructions to enable this functionality in
	 * IntelliJ.
	 *
	 * @return
	 *   An array of `AvailObjectFieldHelper` objects that help describe the
	 *   logical structure of the receiver to the debugger.
	 */
	override fun describeForDebugger(): Array<AvailObjectFieldHelper> =
		try
		{
			descriptor().o_DescribeForDebugger(this)
		}
		catch (e: Throwable)
		{
			arrayOf(
				AvailObjectFieldHelper(
					this,
					DUMMY_DEBUGGER_SLOT,
					-1,
					e,
					slotName = "Error",
					forcedName = e.toString(),
					forcedChildren = arrayOf(
						AvailObjectFieldHelper(
							this,
							DUMMY_DEBUGGER_SLOT,
							-1,
							arrayOf(traceFor(e)),
							slotName = "Stack trace",
							forcedName = "Stack trace"))))
		}

	/**
	 * Answer a name suitable for labeling a field containing this object.
	 *
	 * @return An Avail [string][StringDescriptor].
	 */
	override fun nameForDebugger(): String =
		try
		{
			descriptor().o_NameForDebugger(this)
		}
		catch (e: Throwable)
		{
			"(Error: ${e.message})"
		}

	/**
	 * Answer whether to show value-specific content in the field name in the
	 * debugger.
	 *
	 * @return Whether to show the value.
	 */
	override fun showValueInNameForDebugger(): Boolean =
		descriptor().o_ShowValueInNameForDebugger(this)

	override fun toString() = buildString {
		val recursionMap = IdentityHashMap<A_BasicObject, Void>(10)
		printOnAvoidingIndent(this@buildString, recursionMap, 1)
		assert(recursionMap.size == 0)
	}

	/**
	 * Helper method for transferring this object's longSlots into an
	 * [L1InstructionDecoder].  The receiver's descriptor must be a
	 * [CompiledCodeDescriptor].
	 *
	 * @param instructionDecoder
	 *   The [L1InstructionDecoder] to populate.
	 */
	override fun setUpInstructionDecoder(
		instructionDecoder: L1InstructionDecoder
	) {
		super.setUpInstructionDecoder(instructionDecoder)
		val finalPc = numNybbles + 1
		instructionDecoder.finalLongIndex =
			L1InstructionDecoder.baseIndexInArray + (finalPc shr 4)
		instructionDecoder.finalShift = finalPc and 0xF shl 2
	}

	/**
	 * Set up the object to report nice obvious errors if anyone ever accesses
	 * it again.
	 */
	fun assertObjectUnreachableIfMutable()
	{
		checkValidAddress()
		if (!descriptor().isMutable) return
		// Recursively invoke the iterator on the subobjects of self...
		lateinit var marker: (AvailObject) -> AvailObject
		marker = { childObject: AvailObject ->
			when
			{
				!childObject.descriptor().isMutable -> childObject
				else ->
				{
					// Recursively invoke the iterator on the subobjects of
					// subobject.
					childObject.scanSubobjects(marker)
					// Indicate the object is no longer valid and should not
					// ever be used again.
					childObject.destroy()
					childObject
				}
			}
		}
		scanSubobjects(marker)
		destroy()
	}

	/**
	 * Replace my descriptor field with a [FillerDescriptor].  This blows up for
	 * most messages, catching incorrect (all, by definition) further accidental
	 * uses of this object.
	 */
	override fun setToInvalidDescriptor() {
		assert(currentDescriptor.isMutable)
		currentDescriptor = FillerDescriptor.mutable
	}

	/**
	 * Compute the 32-bit hash of the receiver.
	 *
	 * @return
	 *   An [Int] hash value.
	 */
	override fun hash() = descriptor().o_Hash(this)

	/**
	 * Add the [chunk][L2Chunk] with the given index to the receiver's list of
	 * chunks that depend on it.  The receiver is a [method][MethodDescriptor].
	 * A change in the method's membership (e.g., adding a new method
	 * definition) will cause the chunk to be invalidated.
	 */
	override fun addDependentChunk(chunk: L2Chunk) =
		descriptor().o_AddDependentChunk(this, chunk)

	/**
	 * Construct a Java [string][String] from the receiver, an Avail
	 * [string][StringDescriptor].
	 *
	 * @return
	 *   The corresponding Java string.
	 */
	override fun asNativeString() = descriptor().o_AsNativeString(this)

	override fun clearValue() = descriptor().o_ClearValue(this)

	override fun function() = descriptor().o_Function(this)

	/**
	 * A convenience method that exposes the fact that a subtuple of a string is
	 * also a string.
	 *
	 * @param start
	 *   The start of the range to extract.
	 * @param end
	 *   The end of the range to extract.
	 * @param canDestroy
	 *   Whether the original object may be destroyed if mutable.
	 * @return
	 *   The substring.
	 */
	override fun copyStringFromToCanDestroy(
		start: Int, end: Int, canDestroy: Boolean
	): A_String = descriptor()
		.o_CopyTupleFromToCanDestroy(this, start, end, canDestroy)
		.cast()

	/**
	 * Answer whether the receiver and the argument, both [AvailObject]s, are
	 * equal in value.
	 *
	 * Note that the argument is of type [A_BasicObject] so that correctly typed
	 * uses (where the argument is statically known to be an AvailObject)
	 * proceed normally. Incorrectly typed uses (where the argument is an
	 * arbitrary Java [Object] should show up as calling a deprecated method,
	 * and should fail at runtime if the argument is not actually an
	 * AvailObject.
	 *
	 * @param another
	 *   The object to be compared to the receiver.
	 * @return
	 *   `true` if the two objects are of equal value, `false` otherwise.
	 */
	@Suppress("CovariantEquals")
	override fun equals(another: A_BasicObject): Boolean {
		if (this === another) return true
		if (!descriptor().o_Equals(this, another)) return false
		// They're equal.  Try to turn one into an indirection to the other.
		val traversed1 = traversed()
		val traversed2 = another.traversed()
		if (traversed1 === traversed2) return true
		if (!traversed1.descriptor().isShared) {
			if (!traversed2.descriptor().isShared
				&& traversed1.isBetterRepresentationThan(traversed2)) {
				traversed2.becomeIndirectionTo(traversed1.makeImmutable())
			}
			else
			{
				traversed1.becomeIndirectionTo(traversed2.makeImmutable())
			}
		}
		else if (!traversed2.descriptor().isShared)
		{
			traversed2.becomeIndirectionTo(traversed1.makeImmutable())
		}
		return true
	}

	/**
	 * Answer whether the receiver, an [AvailObject], and the argument, a
	 * [tuple][TupleDescriptor], are equal in value.
	 *
	 * @param aTuple
	 *   The tuple to be compared to the receiver.
	 * @return
	 *   `true` if the receiver is a tuple and of value equal to the argument,
	 *   `false` otherwise.
	 */
	override fun equalsAnyTuple(aTuple: A_Tuple) =
		descriptor().o_EqualsAnyTuple(this, aTuple)

	/**
	 * Answer whether the receiver, an [AvailObject], and the argument, a
	 * [byte&#32;string][ByteStringDescriptor], are equal in value.
	 *
	 * @param aByteString
	 *   The byte string to be compared to the receiver.
	 * @return
	 *   `true` if the receiver is a byte string and of value equal to the
	 *   argument, `false` otherwise.
	 */
	override fun equalsByteString(aByteString: A_String) =
		descriptor().o_EqualsByteString(this, aByteString)

	/**
	 * Answer whether the receiver, an [AvailObject], and the argument, a
	 * [byte&#32;tuple][ByteTupleDescriptor], are equal in value.
	 *
	 * @param aByteTuple
	 *   The byte tuple to be compared to the receiver.
	 * @return
	 *   `true` if the receiver is a byte tuple and of value equal to the
	 *   argument, `false` otherwise.
	 */
	override fun equalsByteTuple(aByteTuple: A_Tuple) =
		descriptor().o_EqualsByteTuple(this, aByteTuple)

	/**
	 * Answer whether the receiver, an [AvailObject], and the argument, an
	 * [integer&#32;interval][IntegerIntervalTupleDescriptor], are equal in
	 * value.
	 *
	 * @param anIntegerIntervalTuple
	 *   The integer interval tuple to be compared to the receiver.
	 * @return
	 *   `true` if the receiver is an integer interval tuple and of value equal
	 *   to the argument, `false` otherwise.
	 */
	override fun equalsIntegerIntervalTuple(anIntegerIntervalTuple: A_Tuple) =
		descriptor().o_EqualsIntegerIntervalTuple(this, anIntegerIntervalTuple)

	/**
	 * Answer whether the receiver, an [AvailObject], and the argument, an
	 * [int&#32;tuple][IntTupleDescriptor], are equal in value.
	 *
	 * @param anIntTuple
	 *   The int tuple to be compared to the receiver.
	 * @return
	 *   `true` if the receiver is a tuple equal to the argument, `false`
	 *   otherwise.
	 */
	override fun equalsIntTuple(anIntTuple: A_Tuple) =
		descriptor().o_EqualsIntTuple(this, anIntTuple)

	/**
	 * Answer whether the receiver, an [AvailObject], and the argument, a
	 * [long&#32;tuple][LongTupleDescriptor], are equal in value.
	 *
	 * @param aLongTuple
	 *   The long tuple to be compared to the receiver.
	 * @return
	 *   `true` if the receiver is a tuple equal to the argument, `false`
	 *   otherwise.
	 */
	override fun equalsLongTuple(aLongTuple: A_Tuple) =
		descriptor().o_EqualsLongTuple(this, aLongTuple)

	/**
	 * Answer whether the receiver, an [AvailObject], and the argument, a
	 * small integer [interval&#32;tuple][SmallIntegerIntervalTupleDescriptor],
	 * are equal in value.
	 *
	 * @param aSmallIntegerIntervalTuple
	 *   The integer interval tuple to be compared to the receiver.
	 * @return
	 *   `true` if the receiver is a small integer interval tuple and of value
	 *   equal to the argument, `false` otherwise.
	 */
	override fun equalsSmallIntegerIntervalTuple(
		aSmallIntegerIntervalTuple: A_Tuple
	) = descriptor().o_EqualsSmallIntegerIntervalTuple(this, aSmallIntegerIntervalTuple)

	/**
	 * Answer whether the receiver, an [AvailObject], and the argument, a
	 * [repeated&#32;element&#32;tuple][RepeatedElementTupleDescriptor], are
	 * equal in value.
	 *
	 * @param aRepeatedElementTuple
	 *   The repeated element tuple to be compared to the receiver.
	 * @return
	 *   `true` if the receiver is a repeated element tuple and of value equal
	 *   to the argument, `false` otherwise.
	 */
	override fun equalsRepeatedElementTuple(aRepeatedElementTuple: A_Tuple) =
		descriptor().o_EqualsRepeatedElementTuple(this, aRepeatedElementTuple)

	override fun equalsFiberType(aFiberType: A_Type) =
		descriptor().o_EqualsFiberType(this, aFiberType)

	override fun equalsFunction(aFunction: A_Function) =
		descriptor().o_EqualsFunction(this, aFunction)

	/**
	 * Answer whether the receiver, an [AvailObject], and the
	 * argument, a [function&#32;type][FunctionTypeDescriptor], are equal.
	 *
	 * @param aFunctionType
	 *   The function type used in the comparison.
	 * @return
	 *   `true` IFF the receiver is also a function type and:
	 *   * The [argument&#32;types][A_Type.argsTupleType] correspond,
	 *   * The [return&#32;types][A_Type.returnType] correspond, and
	 *   * The [declared&#32;exceptions][A_Type.declaredExceptions] correspond.
	 */
	override fun equalsFunctionType(aFunctionType: A_Type) =
		descriptor().o_EqualsFunctionType(this, aFunctionType)

	/**
	 * Answer whether the receiver, an [AvailObject], and a
	 * [compiled&#32;code][CompiledCodeDescriptor], are equal.
	 *
	 * @param aCompiledCode
	 *   The compiled code used in the comparison.
	 * @return
	 *   `true` if the receiver is a compiled code and of value equal to the
	 *   argument, `false` otherwise.
	 */
	override fun equalsCompiledCode(aCompiledCode: A_RawFunction) =
		descriptor().o_EqualsCompiledCode(this, aCompiledCode)

	override fun equalsVariableType(aVariableType: A_Type) =
		descriptor().o_EqualsVariableType(this, aVariableType)

	override fun equalsContinuation(aContinuation: A_Continuation) =
		descriptor().o_EqualsContinuation(this, aContinuation)

	override fun equalsContinuationType(aContinuationType: A_Type) =
		descriptor().o_EqualsContinuationType(this, aContinuationType)

	override fun equalsIntegerRangeType(anIntegerRangeType: A_Type) =
		descriptor().o_EqualsIntegerRangeType(this, anIntegerRangeType)

	override fun equalsMap(aMap: A_Map) = descriptor().o_EqualsMap(this, aMap)

	override fun equalsMapType(aMapType: A_Type) =
		descriptor().o_EqualsMapType(this, aMapType)

	override fun equalsNybbleTuple(aNybbleTuple: A_Tuple) =
		descriptor().o_EqualsNybbleTuple(this, aNybbleTuple)

	override fun equalsObject(anObject: AvailObject) =
		descriptor().o_EqualsObject(this, anObject)

	override fun equalsObjectTuple(anObjectTuple: A_Tuple) =
		descriptor().o_EqualsObjectTuple(this, anObjectTuple)

	override fun equalsPhraseType(aPhraseType: A_Type) =
		descriptor().o_EqualsPhraseType(this, aPhraseType)

	override fun equalsPojo(aPojo: AvailObject) =
		descriptor().o_EqualsPojo(this, aPojo)

	override fun equalsPojoType(aPojoType: AvailObject) =
		descriptor().o_EqualsPojoType(this, aPojoType)

	override fun equalsPrimitiveType(aPrimitiveType: A_Type) =
		descriptor().o_EqualsPrimitiveType(this, aPrimitiveType)

	override fun equalsRawPojoFor(
		otherRawPojo: AvailObject,
		otherJavaObject: Any?
	) = descriptor().o_EqualsRawPojoFor(this, otherRawPojo, otherJavaObject)

	/**
	 * Answer whether the receiver and the argument tuple, both [AvailObject]s,
	 * are equal in value.
	 *
	 * Note that the argument is of type [AvailObject] so that correctly typed
	 * uses (where the argument is statically known to be an AvailObject)
	 * proceed normally. Incorrectly typed uses (where the argument is an
	 * arbitrary Java [Object] should show up as calling a deprecated method,
	 * and should fail at runtime if the argument is not actually an
	 * AvailObject.
	 *
	 * @param aTuple
	 *   The object to be compared to the receiver.
	 * @return
	 *   `true` if the two objects are of equal value, `false` otherwise.
	 */
	override fun equalsReverseTuple(aTuple: A_Tuple) =
		descriptor().o_EqualsReverseTuple(this, aTuple)

	override fun equalsSetType(aSetType: A_Type) =
		descriptor().o_EqualsSetType(this, aSetType)

	override fun equalsTupleType(aTupleType: A_Type) =
		descriptor().o_EqualsTupleType(this, aTupleType)

	override fun equalsTwoByteString(aTwoByteString: A_String) =
		descriptor().o_EqualsTwoByteString(this, aTwoByteString)

	override fun fieldMap() = descriptor().o_FieldMap(this)

	@Throws(VariableGetException::class)
	override fun getValue() = descriptor().o_GetValue(this)

	@Throws(VariableGetException::class)
	override fun getValueClearing() = descriptor().o_GetValueClearing(this)

	override fun getValueForDebugger() =
		descriptor().o_GetValueForDebugger(this)

	override fun hashOrZero() = descriptor().o_HashOrZero(this)

	override fun setHashOrZero(value: Int) =
		descriptor().o_SetHashOrZero(this, value)

	override val isAbstract get() = descriptor().o_IsAbstract(this)

	override fun representationCostOfTupleType() =
		descriptor().o_RepresentationCostOfTupleType(this)

	/**
	 * Is the receiver an Avail boolean?
	 *
	 * @return
	 *   `true` if the receiver is a boolean, `false` otherwise.
	 */
	override val isBoolean get() = descriptor().o_IsBoolean(this)

	/**
	 * Is the receiver an Avail unsigned byte?
	 *
	 * @return
	 *   `true` if the argument is an unsigned byte, `false` otherwise.
	 */
	override val isUnsignedByte get() = descriptor().o_IsUnsignedByte(this)

	/**
	 * Is the receiver an Avail byte tuple?
	 *
	 * @return
	 *   `true` if the receiver is a byte tuple, `false` otherwise.
	 */
	override val isByteTuple get() = descriptor().o_IsByteTuple(this)

	/**
	 * Is the receiver an Avail function?
	 *
	 * @return
	 *   `true` if the receiver is a function, `false` otherwise.
	 */
	override val isFunction get() = descriptor().o_IsFunction(this)

	/**
	 * Is the receiver an Avail atom?
	 *
	 * @return
	 *   `true` if the receiver is an atom, `false` otherwise.
	 */
	override val isAtom get() = descriptor().o_IsAtom(this)

	/**
	 * Is the receiver an Avail extended integer?
	 *
	 * @return
	 *   `true` if the receiver is an extended integer, `false` otherwise.
	 */
	override val isExtendedInteger get() =
		descriptor().o_IsExtendedInteger(this)

	override val isFinite get() = descriptor().o_IsFinite(this)

	override fun isInstanceOf(aType: A_Type) =
		descriptor().o_IsInstanceOf(this, aType)

	override fun isInstanceOfKind(aType: A_Type) =
		descriptor().o_IsInstanceOfKind(this, aType)

	override val isIntegerIntervalTuple get() =
		descriptor().o_IsIntegerIntervalTuple(this)

	override val isIntTuple get() = descriptor().o_IsIntTuple(this)

	override val isLongTuple get() = descriptor().o_IsLongTuple(this)

	override val isSmallIntegerIntervalTuple get() =
		descriptor().o_IsSmallIntegerIntervalTuple(this)

	override val isRepeatedElementTuple get() =
		descriptor().o_IsRepeatedElementTuple(this)

	override val isIntegerRangeType get() =
		descriptor().o_IsIntegerRangeType(this)

	/**
	 * Is the receiver an Avail map?
	 *
	 * @return
	 *   `true` if the receiver is a map, `false` otherwise.
	 */
	override val isMap get() = descriptor().o_IsMap(this)

	override val isMapType get() = descriptor().o_IsMapType(this)

	/**
	 * Is the receiver an Avail nybble?
	 *
	 * @return
	 *   `true` if the receiver is a nybble, `false` otherwise.
	 */
	override val isNybble get() = descriptor().o_IsNybble(this)

	override val isSetType get() = descriptor().o_IsSetType(this)

	/**
	 * Is the receiver an Avail string?
	 *
	 * @return
	 *   `true` if the receiver is an Avail string, `false` otherwise.
	 */
	override val isString get() = descriptor().o_IsString(this)

	/**
	 * Is the receiver an Avail tuple?
	 *
	 * @return
	 *   `true` if the receiver is a tuple, `false` otherwise.
	 */
	override val isTuple get() = descriptor().o_IsTuple(this)

	override val isTupleType get() = descriptor().o_IsTupleType(this)

	override val isType get() = descriptor().o_IsType(this)

	/**
	 * Answer an [iterator][Iterator] suitable for traversing the
	 * elements of the receiver with a Java *foreach* construct.
	 *
	 * @return
	 *   An [iterator][Iterator].
	 */
	@ReferencedInGeneratedCode
	override fun iterator(): Iterator<AvailObject> =
		descriptor().o_Iterator(this)

	override fun spliterator(): Spliterator<AvailObject> =
		descriptor().o_Spliterator(this)

	override fun literal() = descriptor().o_Literal(this)

	override fun makeImmutable() =
		descriptor().let {
			when(it.mutability) {
				Mutability.MUTABLE -> it.o_MakeImmutable(this)
				else -> this
			}
		}

	override fun makeShared() =
		descriptor().let {
			when(it.mutability) {
				Mutability.SHARED -> this
				else -> it.o_MakeShared(this)
			}
		}

	override fun makeSubobjectsImmutable() =
		descriptor().o_MakeSubobjectsImmutable(this)

	override fun makeSubobjectsShared() =
		descriptor().o_MakeSubobjectsShared(this)

	override fun removeDependentChunk(chunk: L2Chunk) =
		descriptor().o_RemoveDependentChunk(this, chunk)

	override fun scanSubobjects(visitor: (AvailObject) -> AvailObject) =
		descriptor().o_ScanSubobjects(this, visitor)

	@Throws(VariableSetException::class)
	override fun setValue(newValue: A_BasicObject) =
		descriptor().o_SetValue(this, newValue)

	override fun setValueNoCheck(newValue: A_BasicObject) =
		descriptor().o_SetValueNoCheck(this, newValue)

	override fun start() = descriptor().o_Start(this)

	override fun string() = descriptor().o_String(this)

	override fun tokenType(): TokenType = descriptor().o_TokenType(this)

	override fun traversed() = descriptor().o_Traversed(this)

	override fun kind() = descriptor().o_Kind(this)

	override fun value() = descriptor().o_Value(this)

	override fun resultType() = descriptor().o_ResultType(this)

	override fun declarationKind() = descriptor().o_DeclarationKind(this)

	override fun lineNumber() = descriptor().o_LineNumber(this)

	override fun equalsInstanceTypeFor(anInstanceType: AvailObject) =
		descriptor().o_EqualsInstanceTypeFor(this, anInstanceType)

	/**
	 * Determine whether the receiver is an
	 * [enumeration][AbstractEnumerationTypeDescriptor] with the given
	 * [set][SetDescriptor] of instances.
	 *
	 * @param aSet
	 *   A set of objects.
	 * @return
	 *   Whether the receiver is an enumeration with the given membership.
	 */
	override fun equalsEnumerationWithSet(aSet: A_Set) =
		descriptor().o_EqualsEnumerationWithSet(this, aSet)

	override val isEnumeration get() = descriptor().o_IsEnumeration(this)

	override fun enumerationIncludesInstance(potentialInstance: AvailObject) =
		descriptor().o_EnumerationIncludesInstance(this, potentialInstance)

	override fun equalsCompiledCodeType(aCompiledCodeType: A_Type) =
		descriptor().o_EqualsCompiledCodeType(this, aCompiledCodeType)

	override fun equalsEnumerationType(anEnumerationType: A_BasicObject) =
		descriptor().o_EqualsEnumerationType(this, anEnumerationType)

	override val isRawPojo get() = descriptor().o_IsRawPojo(this)

	override val isPojoSelfType get() = descriptor().o_IsPojoSelfType(this)

	override fun pojoSelfType() = descriptor().o_PojoSelfType(this)

	override fun javaClass() = descriptor().o_JavaClass(this)

	override fun rawPojo() = descriptor().o_RawPojo(this)

	override val isPojo get() = descriptor().o_IsPojo(this)

	override val isPojoType get() = descriptor().o_IsPojoType(this)

	override fun serializerOperation() =
		descriptor().o_SerializerOperation(this)

	override val isPojoFusedType get() = descriptor().o_IsPojoFusedType(this)

	override fun equalsPojoBottomType() =
		descriptor().o_EqualsPojoBottomType(this)

	override fun javaAncestors() = descriptor().o_JavaAncestors(this)

	override val isPojoArrayType get() = descriptor().o_IsPojoArrayType(this)

	override fun marshalToJava(classHint: Class<*>?) =
		descriptor().o_MarshalToJava(this, classHint)

	override fun equalsPojoField(field: AvailObject, receiver: AvailObject) =
		descriptor().o_EqualsPojoField(this, field, receiver)

	override fun equalsEqualityRawPojoFor(
		otherEqualityRawPojo: AvailObject,
		otherJavaObject: Any?
	) = descriptor().o_EqualsEqualityRawPojo(
		this, otherEqualityRawPojo, otherJavaObject)

	override fun <T : Any> javaObject(): T? = descriptor().o_JavaObject(this)

	override fun <T : Any> javaObjectNotNull(): T =
		descriptor().o_JavaObject(this)!!

	override fun lowerCaseString() = descriptor().o_LowerCaseString(this)

	override fun fieldTuple() = descriptor().o_FieldTuple(this)

	override val isTokenType get() = descriptor().o_IsTokenType(this)

	override val isLiteralTokenType
		get() = descriptor().o_IsLiteralTokenType(this)

	override fun isLiteralToken() = descriptor().o_IsLiteralToken(this)

	override fun equalsTokenType(aTokenType: A_Type) =
		descriptor().o_EqualsTokenType(this, aTokenType)

	override fun equalsLiteralTokenType(aLiteralTokenType: A_Type) =
		descriptor().o_EqualsLiteralTokenType(this, aLiteralTokenType)

	override fun equalsObjectType(anObjectType: AvailObject) =
		descriptor().o_EqualsObjectType(this, anObjectType)

	override fun equalsToken(aToken: A_Token) =
		descriptor().o_EqualsToken(this, aToken)

	override val isInstanceMeta get() = descriptor().o_IsInstanceMeta(this)

	override fun equalsPhrase(aPhrase: A_Phrase) =
		descriptor().o_EqualsPhrase(this, aPhrase)

	/**
	 * Answer the [method][MethodDescriptor] that this
	 * [definition][DefinitionDescriptor] is for.
	 *
	 * @return
	 *   The definition's method.
	 */
	override fun definitionMethod() = descriptor().o_DefinitionMethod(this)

	override fun prefixFunctions() = descriptor().o_PrefixFunctions(this)

	override fun equalsByteArrayTuple(aByteArrayTuple: A_Tuple) =
		descriptor().o_EqualsByteArrayTuple(this, aByteArrayTuple)

	override val isByteArrayTuple get() = descriptor().o_IsByteArrayTuple(this)

	override fun <T> lock(body: () -> T): T = descriptor().o_Lock(this, body)

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun getAndSetValue(newValue: A_BasicObject) =
		descriptor().o_GetAndSetValue(this, newValue)

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun compareAndSwapValues(
		reference: A_BasicObject,
		newValue: A_BasicObject
	) = descriptor().o_CompareAndSwapValues(this, reference, newValue)

	@Throws(VariableSetException::class)
	override fun compareAndSwapValuesNoCheck(
		reference: A_BasicObject,
		newValue: A_BasicObject
	) = descriptor().o_CompareAndSwapValuesNoCheck(this, reference, newValue)

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun fetchAndAddValue(addend: A_Number) =
		descriptor().o_FetchAndAddValue(this, addend)

	override fun equalsByteBufferTuple(aByteBufferTuple: A_Tuple) =
		descriptor().o_EqualsByteBufferTuple(this, aByteBufferTuple)

	override val isByteBufferTuple get() =
		descriptor().o_IsByteBufferTuple(this)

	override fun definitionBundle(): A_Bundle =
		descriptor().o_DefinitionBundle(this)

	override fun definitionModule() = descriptor().o_DefinitionModule(this)

	override fun argumentRestrictionSets() =
		descriptor().o_ArgumentRestrictionSets(this)

	override fun restrictedBundle() = descriptor().o_RestrictedBundle(this)

	override val isByteString get() = descriptor().o_IsByteString(this)

	override val isTwoByteString get() = descriptor().o_IsTwoByteString(this)

	override fun addWriteReactor(key: A_Atom, reactor: VariableAccessReactor) =
		descriptor().o_AddWriteReactor(this, key, reactor)

	@Throws(AvailException::class)
	override fun removeWriteReactor(key: A_Atom) =
		descriptor().o_RemoveWriteReactor(this, key)

	override fun validWriteReactorFunctions() =
		descriptor().o_ValidWriteReactorFunctions(this)

	override val isBottom get() = descriptor().o_IsBottom(this)

	override val isVacuousType get() = descriptor().o_IsVacuousType(this)

	override val isTop get() = descriptor().o_IsTop(this)

	override fun hasValue() = descriptor().o_HasValue(this)

	override val isInitializedWriteOnceVariable get() =
		descriptor().o_IsInitializedWriteOnceVariable(this)

	override fun writeTo(writer: JSONWriter) =
		descriptor().o_WriteTo(this, writer)

	override fun writeSummaryTo(writer: JSONWriter) =
		descriptor().o_WriteSummaryTo(this, writer)

	override fun valueWasStablyComputed() =
		descriptor().o_ValueWasStablyComputed(this)

	override fun setValueWasStablyComputed(wasStablyComputed: Boolean) =
		descriptor().o_SetValueWasStablyComputed(this, wasStablyComputed)

	override fun equalsListNodeType(listNodeType: A_Type) =
		descriptor().o_EqualsListNodeType(this, listNodeType)

	@ReferencedInGeneratedCode
	override fun fieldAt(field: A_Atom) = descriptor().o_FieldAt(this, field)

	@ReferencedInGeneratedCode
	override fun fieldAtIndex(index: Int): AvailObject =
		descriptor().o_FieldAtIndex(this, index)

	override fun fieldAtOrNull(field: A_Atom) =
		descriptor().o_FieldAtOrNull(this, field)

	override fun fieldAtPuttingCanDestroy(
		field: A_Atom,
		value: A_BasicObject,
		canDestroy: Boolean
	) = descriptor().o_FieldAtPuttingCanDestroy(this, field, value, canDestroy)

	@ReferencedInGeneratedCode
	override fun fieldTypeAt(field: A_Atom) =
		descriptor().o_FieldTypeAt(this, field)

	@ReferencedInGeneratedCode
	override fun fieldTypeAtIndex(index: Int): A_Type =
		descriptor().o_FieldTypeAtIndex(this, index)

	override fun fieldTypeAtOrNull(field: A_Atom) =
		descriptor().o_FieldTypeAtOrNull(this, field)

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun atomicAddToMap(key: A_BasicObject, value: A_BasicObject) =
		descriptor().o_AtomicAddToMap(this, key, value)

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun atomicRemoveFromMap(key: A_BasicObject) =
		descriptor().o_AtomicRemoveFromMap(this, key)

	@Throws(VariableGetException::class)
	override fun variableMapHasKey(key: A_BasicObject) =
		descriptor().o_VariableMapHasKey(this, key)

	override fun isGlobal() = descriptor().o_IsGlobal(this)

	override fun globalModule() = descriptor().o_GlobalModule(this)

	override fun globalName() = descriptor().o_GlobalName(this)

	override fun nextLexingState(): LexingState =
		descriptor().o_NextLexingState(this)

	override fun nextLexingStatePojo(): AvailObject =
		descriptor().o_NextLexingStatePojo(this)

	override fun setNextLexingStateFromPrior(priorLexingState: LexingState) =
		descriptor().o_SetNextLexingStateFromPrior(this, priorLexingState)

	override fun clearLexingState() = descriptor().o_ClearLexingState(this)

	override fun extractDumpedObjectAt(index: Int): AvailObject =
		descriptor().o_ExtractDumpedObjectAt(this, index)

	override fun extractDumpedLongAt(index: Int): Long =
		descriptor().o_ExtractDumpedLongAt(this, index)

	override fun synthesizeCurrentLexingState(): LexingState =
		descriptor().o_SynthesizeCurrentLexingState(this)

	companion object {
		/**
		 * A good multiplier for a multiplicative random generator.  This
		 * constant is a primitive element of the group (Z`[`2^32], *),
		 * specifically 1664525, as taken from Knuth, <cite>The Art of Computer
		 * Programming</cite>, Vol. 2, 2<sup>nd</sup> ed., page 102, row 26. See
		 * also pages 19, 20, theorems B and C. The period of the cycle based on
		 * this multiplicative generator is 2^30.
		 */
		const val multiplier = 1664525

		private fun stir13(i1: Int): Int = Integer.rotateRight(i1, 13) + i1

		private fun stir7(i1: Int): Int = Integer.rotateRight(i1, 7) + i1

		private fun stir21(i1: Int): Int = Integer.rotateRight(i1, 21) + i1

		/**
		 * Combine two hash values into one.  If two values are truly being
		 * combined, to avoid systematic collisions it might be best to use
		 * combine3() instead, with a usage-specific constant,
		 */
		fun combine2(i1: Int, i2: Int): Int
		{
			var h = stir13(i1 xor -0x36436f02)
			h *= multiplier
			h += stir7(i2 xor 0x5f610978)
			return stir21(h)
		}

		/**
		 * Combine multiple hash values into one.  To avoid systematic
		 * collisions, one of these should be a usage-specific constant salt.
		 */
		fun combine3(i1: Int, i2: Int, i3: Int): Int
		{
			var h = stir13(i1 xor 0x2AE0A942)
			h *= multiplier
			h += stir7(i2 xor 0x717B4F2A)
			h *= multiplier
			h -= stir21(i3 xor -0x7086C805)
			return h
		}

		/**
		 * Combine multiple hash values into one.  To avoid systematic
		 * collisions, one of these should be a usage-specific constant salt.
		 */
		fun combine4(i1: Int, i2: Int, i3: Int, i4: Int): Int
		{
			var h = stir13(i1 xor 0x441f144e)
			h *= multiplier
			h += stir7(i2 xor -0x583ce1ac)
			h *= multiplier
			h -= stir21(i3 xor 0x45f73694)
			h *= multiplier
			h -= stir13(i4 xor -0x7ce5d9e4)
			return h
		}

		/**
		 * Combine multiple hash values into one.  To avoid systematic
		 * collisions, one of these should be a usage-specific constant salt.
		 */
		fun combine5(i1: Int, i2: Int, i3: Int, i4: Int, i5: Int): Int
		{
			var h = stir7(i1 xor -0x32b495be)
			h *= multiplier
			h += stir21(i2 xor 0x7e1ed873)
			h *= multiplier
			h -= stir21(i3 xor 0x7b9570ca)
			h *= multiplier
			h += stir13(i4 xor 0x13f07f25)
			h *= multiplier
			h -= stir7(i5 xor 0x45c8582b)
			return h
		}

		/**
		 * Combine multiple hash values into one.  To avoid systematic
		 * collisions, one of these should be a usage-specific constant salt.
		 */
		fun combine6(i1: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int): Int
		{
			var h = stir13(i1 xor 0x4e2152da)
			h *= multiplier
			h += stir13(i2 xor 0x21cb94b3)
			h *= multiplier
			h -= stir7(i3 xor 0x796c44ab)
			h *= multiplier
			h += stir21(i4 xor -0x7fdd4667)
			h *= multiplier
			h -= stir7(i5 xor -0x24bc2bbe)
			h *= multiplier
			h += stir21(i6 xor -0x35385f57)
			return h
		}

		/**
		 * Combine multiple hash values into one.  To avoid systematic
		 * collisions, one of these should be a usage-specific constant salt.
		 */
		fun combine7(
			i1: Int,
			i2: Int,
			i3: Int,
			i4: Int,
			i5: Int,
			i6: Int,
			i7: Int
		): Int
		{
			var h = stir7(i1 xor -0x6b1ed68b)
			h *= multiplier
			h += stir21(i2 xor -0x78a9cea1)
			h *= multiplier
			h -= stir7(i3 xor -0x53358435)
			h *= multiplier
			h += stir21(i4 xor 0x00d3f4af)
			h *= multiplier
			h -= stir7(i5 xor 0x6de53ff9)
			h *= multiplier
			h += stir13(i6 xor 0x5cbd6b80)
			h *= multiplier
			h += stir21(i7 xor -0x09dd1b59)
			return h
		}

		/**
		 * Report a virtual machine problem.
		 *
		 * @param messagePattern
		 *   A [String] describing the problem.
		 * @param arguments
		 *   The arguments to insert into the `messagePattern`.
		 */
		fun error(messagePattern: String?, vararg arguments: Any?): Nothing =
			throw RuntimeException(String.format(messagePattern!!, *arguments))

		/**
		 * Create a new `AvailObject` with the specified
		 * [descriptor][AbstractDescriptor] and the same number of variable
		 * object and integer slots.
		 *
		 * @param size
		 *   The number of variable object and integer slots.
		 * @param descriptor
		 *   A descriptor.
		 * @return
		 *   A new uninitialized [AvailObject].
		 */
		fun newIndexedDescriptor(
			size: Int,
			descriptor: AbstractDescriptor
		): AvailObject {
			var objectSlotCount = descriptor.numberOfFixedObjectSlots
			if (descriptor.hasVariableObjectSlots()) {
				objectSlotCount += size
			}
			var integerSlotCount = descriptor.numberOfFixedIntegerSlots
			if (descriptor.hasVariableIntegerSlots()) {
				integerSlotCount += size
			}
			return AvailObject(descriptor, objectSlotCount, integerSlotCount)
		}

		/**
		 * Create a new `AvailObject` with the specified
		 * [descriptor][AbstractDescriptor], the specified number of object
		 * slots, and the specified number of integer slots.
		 *
		 * @param variableObjectSlots
		 *   The number of object slots.
		 * @param variableIntegerSlots
		 *   The number of integer slots
		 * @param descriptor
		 *   A descriptor.
		 * @return
		 *   A new [AvailObject].
		 */
		fun newObjectIndexedIntegerIndexedDescriptor(
			variableObjectSlots: Int,
			variableIntegerSlots: Int,
			descriptor: AbstractDescriptor
		): AvailObject = with(descriptor) {
			assert(hasVariableObjectSlots() || variableObjectSlots == 0)
			assert(hasVariableIntegerSlots() || variableIntegerSlots == 0)
			return AvailObject(
				descriptor,
				numberOfFixedObjectSlots + variableObjectSlots,
				numberOfFixedIntegerSlots + variableIntegerSlots)
		}

		/** The [CheckedMethod] for [iterator]. */
		@Suppress("unused")
		val iteratorMethod = instanceMethod(
			AvailObject::class.java,
			AvailObject::iterator.name,
			Iterator::class.java)

		@ReferencedInGeneratedCode
		@JvmStatic
		fun frameAtStatic(self: AvailObject, index: Int): AvailObject =
			self.descriptor().o_FrameAt(self, index)

		/** Access the [frameAtStatic] method. */
		val frameAtMethod = staticMethod(
			AvailObject::class.java,
			::frameAtStatic.name,
			AvailObject::class.java,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!)

		@ReferencedInGeneratedCode
		@JvmStatic
		fun frameAtPutStatic(
			self: AvailObject,
			index: Int,
			value: AvailObject
		): AvailObject = self.descriptor().o_FrameAtPut(self, index, value)

		/** Access the [frameAtPutStatic] method. */
		val frameAtPutMethod = staticMethod(
			AvailObject::class.java,
			::frameAtPutStatic.name,
			AvailObject::class.java,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!,
			AvailObject::class.java)

		@ReferencedInGeneratedCode
		@JvmStatic
		fun registerDumpStatic(self: AvailObject): AvailObject =
			self.descriptor().o_RegisterDump(self)

		/** Access the [registerDumpStatic] method. */
		val registerDumpMethod = staticMethod(
			AvailObject::class.java,
			::registerDumpStatic.name,
			AvailObject::class.java,
			AvailObject::class.java)

		/** Access the [fieldAt] method. */
		val fieldAtMethod = instanceMethod(
			AvailObject::class.java,
			AvailObject::fieldAt.name,
			AvailObject::class.java,
			A_Atom::class.java)

		/** Access the [fieldAtIndex] method for objects. */
		val fieldAtIndexMethod = instanceMethod(
			AvailObject::class.java,
			AvailObject::fieldAtIndex.name,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!)

		/** Access the [fieldTypeAt] method. */
		val fieldTypeAtMethod = instanceMethod(
			AvailObject::class.java,
			AvailObject::fieldTypeAt.name,
			A_Type::class.java,
			A_Atom::class.java)

		/** Access the [fieldTypeAtIndex] method for object types. */
		val fieldTypeAtIndexMethod = instanceMethod(
			AvailObject::class.java,
			AvailObject::fieldTypeAtIndex.name,
			A_Type::class.java,
			Int::class.javaPrimitiveType!!)
	}
}
