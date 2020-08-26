/*
 * AvailObject.kt
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

import com.avail.compiler.scanning.LexingState
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.character.A_Character
import com.avail.descriptor.fiber.A_Fiber
import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.fiber.FiberDescriptor.ExecutionState
import com.avail.descriptor.fiber.FiberDescriptor.GeneralFlag
import com.avail.descriptor.fiber.FiberDescriptor.InterruptRequestFlag
import com.avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag
import com.avail.descriptor.fiber.FiberDescriptor.TraceFlag
import com.avail.descriptor.functions.A_Continuation
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.A_RegisterDump
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.A_MapBin
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_GrammaticalRestriction
import com.avail.descriptor.methods.A_Macro
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.A_SemanticRestriction
import com.avail.descriptor.methods.DefinitionDescriptor
import com.avail.descriptor.methods.ForwardDefinitionDescriptor
import com.avail.descriptor.methods.GrammaticalRestrictionDescriptor
import com.avail.descriptor.methods.MethodDefinitionDescriptor
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.parsing.A_DefinitionParsingPlan
import com.avail.descriptor.parsing.A_Lexer
import com.avail.descriptor.parsing.A_ParsingPlanInProgress
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.A_SetBin
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tokens.TokenDescriptor.TokenType
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.isBetterRepresentationThan
import com.avail.descriptor.tuples.ByteStringDescriptor
import com.avail.descriptor.tuples.ByteTupleDescriptor
import com.avail.descriptor.tuples.IntTupleDescriptor
import com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor
import com.avail.descriptor.tuples.LongTupleDescriptor
import com.avail.descriptor.tuples.RepeatedElementTupleDescriptor
import com.avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.declaredExceptions
import com.avail.descriptor.types.A_Type.Companion.returnType
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor
import com.avail.descriptor.types.FunctionTypeDescriptor
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import com.avail.dispatch.LookupTree
import com.avail.exceptions.AvailException
import com.avail.exceptions.MethodDefinitionException
import com.avail.exceptions.SignatureException
import com.avail.exceptions.VariableGetException
import com.avail.exceptions.VariableSetException
import com.avail.interpreter.Primitive
import com.avail.interpreter.execution.AvailLoader
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.io.TextInterface
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.utility.StackPrinter
import com.avail.utility.Strings.traceFor
import com.avail.utility.cast
import com.avail.utility.json.JSONWriter
import com.avail.utility.visitor.AvailSubobjectVisitor
import com.avail.utility.visitor.MarkUnreachableSubobjectVisitor
import org.jetbrains.annotations.Debug.Renderer
import java.util.IdentityHashMap
import java.util.Spliterator
import java.util.TimerTask

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
	text = "nameForDebugger()",
	childrenArray = "describeForDebugger()")
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
		try {
			when {
				isDestroyed -> append("*** A DESTROYED OBJECT ***")
				indent > descriptor().maximumIndent() -> append("*** DEPTH ***")
				recursionMap.containsKey(this@AvailObject) ->
					append("**RECURSION**")
				else ->
					try {
						recursionMap[this@AvailObject] = null
						descriptor().printObjectOnAvoidingIndent(
							this@AvailObject, builder, recursionMap, indent)
					} finally {
						recursionMap.remove(this@AvailObject)
					}
			}
		} catch (e: Exception) {
			append("EXCEPTION while printing.${StackPrinter.trace(e)}")
		} catch (e: AssertionError) {
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
		try {
			descriptor().o_DescribeForDebugger(this)
		}
		catch (e: Throwable)
		{
			arrayOf(
				AvailObjectFieldHelper(
					this,
					DebuggerObjectSlots("Error"),
					-1,
					e,
					forcedName = e.toString(),
					forcedChildren = arrayOf(
						AvailObjectFieldHelper(
							this,
							DebuggerObjectSlots("Stack trace"),
							-1,
							traceFor(e),
							forcedName = "Stack trace")
					)))
		}

	/**
	 * Answer a name suitable for labeling a field containing this object.
	 *
	 * @return An Avail [string][StringDescriptor].
	 */
	override fun nameForDebugger(): String =
		try {
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
		val finalPc = numNybbles() + 1
		instructionDecoder.finalLongIndex =
			L1InstructionDecoder.baseIndexInArray + (finalPc shr 4)
		instructionDecoder.finalShift = finalPc and 0xF shl 2
	}

	/**
	 * Set up the object to report nice obvious errors if anyone ever accesses
	 * it again.
	 */
	fun assertObjectUnreachableIfMutable() {
		checkValidAddress()
		when {
			!descriptor().isMutable -> return
			sameAddressAs(nil) ->
				error("What happened?  This object is also the excluded one.")
			else -> {
				// Recursively invoke the iterator on the subobjects of self...
				scanSubobjects(MarkUnreachableSubobjectVisitor(nil))
				destroy()
			}
		}
	}

	/**
	 * Replace my descriptor field with a [FillerDescriptor].  This blows up for
	 * most messages, catching incorrect (all, by definition) further accidental
	 * uses of this object.
	 */
	override fun setToInvalidDescriptor() {
		currentDescriptor = FillerDescriptor.shared
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
	 * Add the [definition][DefinitionDescriptor] to the receiver, a
	 * [method][MethodDefinitionDescriptor].  Causes dependent chunks to be
	 * invalidated.  Answer the [A_DefinitionParsingPlan]s that were created for
	 * the new definition.
	 *
	 * @param definition
	 *   The definition to be added.
	 * @throws SignatureException
	 *   If the definition could not be added.
	 */
	@Throws(SignatureException::class)
	override fun methodAddDefinition(definition: A_Definition) =
		descriptor().o_MethodAddDefinition(this, definition)

	/**
	 * Add a [grammatical&32;restriction][GrammaticalRestrictionDescriptor] to
	 * this [module][ModuleDescriptor]
	 *
	 * @param grammaticalRestriction
	 *   The set of grammatical restrictions to be added.
	 */
	override fun moduleAddGrammaticalRestriction(
		grammaticalRestriction: A_GrammaticalRestriction
	) = descriptor().o_ModuleAddGrammaticalRestriction(this, grammaticalRestriction)

	/**
	 * Construct a Java [string][String] from the receiver, an Avail
	 * [string][StringDescriptor].
	 *
	 * @return
	 *   The corresponding Java string.
	 */
	override fun asNativeString() = descriptor().o_AsNativeString(this)

	/**
	 * Add a [definition][DefinitionDescriptor] to this
	 * [module][ModuleDescriptor].
	 *
	 * @param definition
	 *   The definition to add to the module.
	 */
	override fun moduleAddDefinition(definition: A_Definition) =
		descriptor().o_ModuleAddDefinition(this, definition)

	override fun addImportedName(trueName: A_Atom) =
		descriptor().o_AddImportedName(this, trueName)

	override fun addImportedNames(trueNames: A_Set) =
		descriptor().o_AddImportedNames(this, trueNames)

	override fun introduceNewName(trueName: A_Atom) =
		descriptor().o_IntroduceNewName(this, trueName)

	override fun addPrivateName(trueName: A_Atom) =
		descriptor().o_AddPrivateName(this, trueName)

	override fun bodyBlock() = descriptor().o_BodyBlock(this)

	override fun bodySignature() = descriptor().o_BodySignature(this)

	override fun breakpointBlock() = descriptor().o_BreakpointBlock(this)

	override fun setBreakpointBlock(value: AvailObject) =
		descriptor().o_SetBreakpointBlock(this, value)

	override fun buildFilteredBundleTree() =
		descriptor().o_BuildFilteredBundleTree(this)

	override fun caller() = descriptor().o_Caller(this)

	override fun clearValue() = descriptor().o_ClearValue(this)

	override fun function() = descriptor().o_Function(this)

	override fun functionType() = descriptor().o_FunctionType(this)

	override fun code() = descriptor().o_Code(this)

	override fun constantBindings() = descriptor().o_ConstantBindings(this)

	override fun continuation() = descriptor().o_Continuation(this)

	override fun setContinuation(value: A_Continuation) =
		descriptor().o_SetContinuation(this, value)

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
	): A_String = descriptor().o_CopyTupleFromToCanDestroy(this, start, end, canDestroy).cast()

	override fun ensureMutable() = descriptor().o_EnsureMutable(this)

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
			} else {
				traversed1.becomeIndirectionTo(traversed2.makeImmutable())
			}
		} else if (!traversed2.descriptor().isShared) {
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

	/**
	 * Answer whether the receiver, an [AvailObject], and a
	 * [variable][VariableDescriptor], are the exact same object, comparing by
	 * address (Java object identity). There's no need to traverse the objects
	 * before comparing addresses, because this message was a double-dispatch
	 * that would have skipped (and stripped) the indirection objects in either
	 * path.
	 *
	 * @param aVariable
	 *   The variable used in the comparison.
	 * @return
	 *   `true` if the receiver is a variable with the same identity as the
	 *   argument, `false` otherwise.
	 */
	override fun equalsVariable(aVariable: A_Variable) =
		descriptor().o_EqualsVariable(this, aVariable)

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

	/** Note: [nil] is never the target of an indirection. */
	override fun equalsNil() = this === nil

	override fun executionState() = descriptor().o_ExecutionState(this)

	override fun setExecutionState(value: ExecutionState) =
		descriptor().o_SetExecutionState(this, value)

	override fun fieldMap() = descriptor().o_FieldMap(this)

	override fun filterByTypes(argTypes: List<A_Type>) =
		descriptor().o_FilterByTypes(this, argTypes)

	@Throws(VariableGetException::class)
	override fun getValue() = descriptor().o_GetValue(this)

	override fun hashOrZero() = descriptor().o_HashOrZero(this)

	override fun setHashOrZero(value: Int) =
		descriptor().o_SetHashOrZero(this, value)

	override fun definitionsAtOrBelow(argRestrictions: List<TypeRestriction>) =
		descriptor().o_DefinitionsAtOrBelow(this, argRestrictions)

	override fun definitionsTuple() = descriptor().o_DefinitionsTuple(this)

	override fun includesDefinition(imp: A_Definition) =
		descriptor().o_IncludesDefinition(this, imp)

	override fun setInterruptRequestFlag(flag: InterruptRequestFlag) =
		descriptor().o_SetInterruptRequestFlag(this, flag)

	override fun decrementCountdownToReoptimize(
		continuation: (Boolean) -> Unit
	) = descriptor().o_DecrementCountdownToReoptimize(this, continuation)

	override fun countdownToReoptimize(value: Long) =
		descriptor().o_CountdownToReoptimize(this, value)

	override val isAbstract get() = descriptor().o_IsAbstract(this)

	override fun isAbstractDefinition() =
		descriptor().o_IsAbstractDefinition(this)

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

	/**
	 * Is the receiver a [forward&#32;declaration][ForwardDefinitionDescriptor]?
	 *
	 * @return
	 *   `true` if the receiver is a forward declaration site.
	 */
	override fun isForwardDefinition() =
		descriptor().o_IsForwardDefinition(this)

	/**
	 * Is the receiver a [method&#32;definition][MethodDefinitionDescriptor]?
	 *
	 * @return `true` if the receiver is a method definition.
	 */
	override fun isMethodDefinition() = descriptor().o_IsMethodDefinition(this)

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

	override fun levelTwoChunk() = descriptor().o_LevelTwoChunk(this)

	override fun levelTwoChunkOffset(chunk: L2Chunk, offset: Int) =
		descriptor().o_LevelTwoChunkOffset(this, chunk, offset)

	override fun levelTwoOffset() = descriptor().o_LevelTwoOffset(this)

	override fun literal() = descriptor().o_Literal(this)

	override fun literalAt(index: Int) = descriptor().o_LiteralAt(this, index)

	@ReferencedInGeneratedCode
	override fun frameAt(index: Int) =
		descriptor().o_FrameAt(this, index)

	@ReferencedInGeneratedCode
	override fun frameAtPut(index: Int, value: AvailObject): AvailObject =
		descriptor().o_FrameAtPut(this, index, value)

	override fun localTypeAt(index: Int) =
		descriptor().o_LocalTypeAt(this, index)

	@Throws(MethodDefinitionException::class)
	override fun lookupByTypesFromTuple(argumentTypeTuple: A_Tuple) =
		descriptor().o_LookupByTypesFromTuple(this, argumentTypeTuple)

	@Throws(MethodDefinitionException::class)
	override fun lookupByValuesFromList(argumentList: List<A_BasicObject>) =
		descriptor().o_LookupByValuesFromList(this, argumentList)

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

	override fun maxStackDepth() = descriptor().o_MaxStackDepth(this)

	override fun methodDefinitions() = descriptor().o_MethodDefinitions(this)

	override fun importedNames() = descriptor().o_ImportedNames(this)

	override fun nameVisible(trueName: A_Atom) =
		descriptor().o_NameVisible(this, trueName)

	override fun newNames() = descriptor().o_NewNames(this)

	override fun numArgs() = descriptor().o_NumArgs(this)

	override fun numSlots() = descriptor().o_NumSlots(this)

	override fun numLiterals() = descriptor().o_NumLiterals(this)

	override fun numLocals() = descriptor().o_NumLocals(this)

	override fun numConstants() = descriptor().o_NumConstants(this)

	override fun numOuters() = descriptor().o_NumOuters(this)

	override fun numOuterVars() = descriptor().o_NumOuterVars(this)

	override fun nybbles() = descriptor().o_Nybbles(this)

	override fun optionallyNilOuterVar(index: Int) =
		descriptor().o_OptionallyNilOuterVar(this, index)

	override fun outerTypeAt(index: Int) =
		descriptor().o_OuterTypeAt(this, index)

	override fun outerVarAt(index: Int) = descriptor().o_OuterVarAt(this, index)

	override fun outerVarAtPut(index: Int, value: AvailObject) =
		descriptor().o_OuterVarAtPut(this, index, value)

	override fun pc() = descriptor().o_Pc(this)

	override fun priority() = descriptor().o_Priority(this)

	override fun setPriority(value: Int) =
		descriptor().o_SetPriority(this, value)

	override fun privateNames() = descriptor().o_PrivateNames(this)

	override fun fiberGlobals() = descriptor().o_FiberGlobals(this)

	override fun setFiberGlobals(value: A_Map) =
		descriptor().o_SetFiberGlobals(this, value)

	override fun removeDependentChunk(chunk: L2Chunk) =
		descriptor().o_RemoveDependentChunk(this, chunk)

	override fun removeFrom(loader: AvailLoader, afterRemoval: () -> Unit) =
		descriptor().o_RemoveFrom(this, loader, afterRemoval)

	override fun removeDefinition(definition: A_Definition) =
		descriptor().o_RemoveDefinition(this, definition)

	override fun resolveForward(forwardDefinition: A_BasicObject) =
		descriptor().o_ResolveForward(this, forwardDefinition)

	override fun scanSubobjects(visitor: AvailSubobjectVisitor) =
		descriptor().o_ScanSubobjects(this, visitor)

	@Throws(VariableSetException::class)
	override fun setValue(newValue: A_BasicObject) =
		descriptor().o_SetValue(this, newValue)

	override fun setValueNoCheck(newValue: A_BasicObject) =
		descriptor().o_SetValueNoCheck(this, newValue)

	override fun stackAt(slotIndex: Int) =
		descriptor().o_StackAt(this, slotIndex)

	override fun stackp() = descriptor().o_Stackp(this)

	override fun start() = descriptor().o_Start(this)

	override fun startingChunk() = descriptor().o_StartingChunk(this)

	override fun setStartingChunkAndReoptimizationCountdown(
		chunk: L2Chunk,
		countdown: Long
	) = descriptor().o_SetStartingChunkAndReoptimizationCountdown(this, chunk, countdown)

	override fun string() = descriptor().o_String(this)

	override fun tokenType(): TokenType = descriptor().o_TokenType(this)

	override fun traversed() = descriptor().o_Traversed(this)

	override fun trueNamesForStringName(stringName: A_String) =
		descriptor().o_TrueNamesForStringName(this, stringName)

	override fun kind() = descriptor().o_Kind(this)

	override fun value() = descriptor().o_Value(this)

	override fun variableBindings() = descriptor().o_VariableBindings(this)

	override fun visibleNames() = descriptor().o_VisibleNames(this)

	override fun resultType() = descriptor().o_ResultType(this)

	override fun primitive(): Primitive? = descriptor().o_Primitive(this)

	override fun declarationKind() = descriptor().o_DeclarationKind(this)

	override fun lineNumber() = descriptor().o_LineNumber(this)

	override val isInt get() = descriptor().o_IsInt(this)

	override val isLong get() = descriptor().o_IsLong(this)

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

	override fun setVersions(versionStrings: A_Set) =
		descriptor().o_SetVersions(this, versionStrings)

	override fun versions() = descriptor().o_Versions(this)

	override val isRawPojo get() = descriptor().o_IsRawPojo(this)

	override fun addSemanticRestriction(restriction: A_SemanticRestriction) =
		descriptor().o_AddSemanticRestriction(this, restriction)

	override fun removeSemanticRestriction(restriction: A_SemanticRestriction) =
		descriptor().o_RemoveSemanticRestriction(this, restriction)

	override fun semanticRestrictions() =
		descriptor().o_SemanticRestrictions(this)

	override fun addSealedArgumentsType(typeTuple: A_Tuple) =
		descriptor().o_AddSealedArgumentsType(this, typeTuple)

	override fun removeSealedArgumentsType(typeTuple: A_Tuple) =
		descriptor().o_RemoveSealedArgumentsType(this, typeTuple)

	override fun sealedArgumentsTypesTuple() =
		descriptor().o_SealedArgumentsTypesTuple(this)

	override fun moduleAddSemanticRestriction(
		semanticRestriction: A_SemanticRestriction
	) = descriptor().o_ModuleAddSemanticRestriction(this, semanticRestriction)

	override fun addConstantBinding(
		name: A_String,
		constantBinding: A_Variable
	) = descriptor().o_AddConstantBinding(this, name, constantBinding)

	override fun addVariableBinding(
		name: A_String,
		variableBinding: A_Variable
	) = descriptor().o_AddVariableBinding(this, name, variableBinding)

	override fun isMethodEmpty() = descriptor().o_IsMethodEmpty(this)

	override val isPojoSelfType get() = descriptor().o_IsPojoSelfType(this)

	override fun pojoSelfType() = descriptor().o_PojoSelfType(this)

	override fun javaClass() = descriptor().o_JavaClass(this)

	override val isUnsignedShort get() = descriptor().o_IsUnsignedShort(this)

	override val isFloat get() = descriptor().o_IsFloat(this)

	override val isDouble get() = descriptor().o_IsDouble(this)

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

	override val isSignedByte get() = descriptor().o_IsSignedByte(this)

	override val isSignedShort get() = descriptor().o_IsSignedShort(this)

	override fun equalsEqualityRawPojoFor(
		otherEqualityRawPojo: AvailObject,
		otherJavaObject: Any?
	) = descriptor().o_EqualsEqualityRawPojo(
		this, otherEqualityRawPojo, otherJavaObject)

	override fun <T : Any> javaObject(): T? = descriptor().o_JavaObject(this)

	override fun <T : Any> javaObjectNotNull(): T =
		descriptor().o_JavaObject(this)!!

	override fun lowerCaseString() = descriptor().o_LowerCaseString(this)

	override fun totalInvocations() = descriptor().o_TotalInvocations(this)

	override fun tallyInvocation() = descriptor().o_TallyInvocation(this)

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

	override fun addSeal(methodName: A_Atom, sealSignature: A_Tuple) =
		descriptor().o_AddSeal(this, methodName, sealSignature)

	override val isInstanceMeta get() = descriptor().o_IsInstanceMeta(this)

	override fun setMethodName(methodName: A_String) =
		descriptor().o_SetMethodName(this, methodName)

	override fun startingLineNumber() = descriptor().o_StartingLineNumber(this)

	override fun module() = descriptor().o_Module(this)

	override fun methodName() = descriptor().o_MethodName(this)

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

	/**
	 * Answer the [loader][AvailLoader] bound to the
	 * [receiver][FiberDescriptor], or `null` if the receiver is not a loader
	 * fiber.
	 *
	 * @return
	 *   An Avail loader, or `null` if no Avail loader is associated with the
	 *   specified fiber.
	 */
	override fun availLoader(): AvailLoader? = descriptor().o_AvailLoader(this)

	override fun setAvailLoader(loader: AvailLoader?) =
		descriptor().o_SetAvailLoader(this, loader)

	override fun moduleName() = descriptor().o_ModuleName(this)

	/**
	 * Answer the continuation that accepts the result produced by the
	 * [receiver][FiberDescriptor]'s successful completion.
	 *
	 * @return
	 *   A continuation.
	 */
	override fun resultContinuation(): (AvailObject) -> Unit =
		descriptor().o_ResultContinuation(this)

	/**
	 * Answer the continuation that accepts the [throwable][Throwable]
	 * responsible for abnormal termination of the [receiver][FiberDescriptor].
	 *
	 * @return
	 *   A continuation.
	 */
	override fun failureContinuation(): (Throwable) -> Unit =
		descriptor().o_FailureContinuation(this)


	override fun setSuccessAndFailureContinuations(
		onSuccess: (AvailObject) -> Unit,
		onFailure: (Throwable) -> Unit
	) = descriptor().o_SetSuccessAndFailureContinuations(this, onSuccess, onFailure)

	/**
	 * Is the specified [interrupt&#32;request&#32;flag][InterruptRequestFlag]
	 * set for the [receiver][FiberDescriptor]?
	 *
	 * @param flag
	 *   An interrupt request flag.
	 * @return
	 *   `true` if the interrupt request flag is set, `false` otherwise.
	 */
	override fun interruptRequestFlag(flag: InterruptRequestFlag) =
		descriptor().o_InterruptRequestFlag(this, flag)

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

	override fun getAndClearInterruptRequestFlag(flag: InterruptRequestFlag) =
		descriptor().o_GetAndClearInterruptRequestFlag(this, flag)

	override fun getAndSetSynchronizationFlag(
		flag: SynchronizationFlag,
		value: Boolean
	) = descriptor().o_GetAndSetSynchronizationFlag(this, flag, value)

	override fun fiberResult() = descriptor().o_FiberResult(this)

	override fun setFiberResult(result: A_BasicObject) =
		descriptor().o_SetFiberResult(this, result)

	override fun joiningFibers() = descriptor().o_JoiningFibers(this)

	override fun wakeupTask(): TimerTask? = descriptor().o_WakeupTask(this)

	override fun setWakeupTask(task: TimerTask?) =
		descriptor().o_SetWakeupTask(this, task)

	override fun setJoiningFibers(joiners: A_Set) =
		descriptor().o_SetJoiningFibers(this, joiners)

	override fun heritableFiberGlobals() =
		descriptor().o_HeritableFiberGlobals(this)

	override fun setHeritableFiberGlobals(globals: A_Map) =
		descriptor().o_SetHeritableFiberGlobals(this, globals)

	override fun generalFlag(flag: GeneralFlag) =
		descriptor().o_GeneralFlag(this, flag)

	override fun setGeneralFlag(flag: GeneralFlag) =
		descriptor().o_SetGeneralFlag(this, flag)

	override fun clearGeneralFlag(flag: GeneralFlag) =
		descriptor().o_ClearGeneralFlag(this, flag)

	override fun equalsByteBufferTuple(aByteBufferTuple: A_Tuple) =
		descriptor().o_EqualsByteBufferTuple(this, aByteBufferTuple)

	override val isByteBufferTuple get() =
		descriptor().o_IsByteBufferTuple(this)

	override fun fiberName() = descriptor().o_FiberName(this)

	override fun fiberNameSupplier(supplier: () -> A_String) =
		descriptor().o_FiberNameSupplier(this, supplier)

	override fun bundles() = descriptor().o_Bundles(this)

	override fun methodAddBundle(bundle: A_Bundle) =
		descriptor().o_MethodAddBundle(this, bundle)

	override fun methodRemoveBundle(bundle: A_Bundle) =
		descriptor().o_MethodRemoveBundle(this, bundle)

	override fun definitionBundle(): A_Bundle =
		descriptor().o_DefinitionBundle(this)

	override fun definitionModule() = descriptor().o_DefinitionModule(this)

	override fun definitionModuleName() =
		descriptor().o_DefinitionModuleName(this)

	override fun entryPoints() = descriptor().o_EntryPoints(this)

	override fun addEntryPoint(stringName: A_String, trueName: A_Atom) =
		descriptor().o_AddEntryPoint(this, stringName, trueName)

	override fun allAncestors() = descriptor().o_AllAncestors(this)

	override fun addAncestors(moreAncestors: A_Set) =
		descriptor().o_AddAncestors(this, moreAncestors)

	override fun argumentRestrictionSets() =
		descriptor().o_ArgumentRestrictionSets(this)

	override fun restrictedBundle() = descriptor().o_RestrictedBundle(this)

	override fun adjustPcAndStackp(pc: Int, stackp: Int) =
		descriptor().o_AdjustPcAndStackp(this, pc, stackp)

	override val isByteString get() = descriptor().o_IsByteString(this)

	override val isTwoByteString get() = descriptor().o_IsTwoByteString(this)

	override fun addWriteReactor(key: A_Atom, reactor: VariableAccessReactor) =
		descriptor().o_AddWriteReactor(this, key, reactor)

	@Throws(AvailException::class)
	override fun removeWriteReactor(key: A_Atom) =
		descriptor().o_RemoveWriteReactor(this, key)

	override fun traceFlag(flag: TraceFlag) =
		descriptor().o_TraceFlag(this, flag)

	override fun setTraceFlag(flag: TraceFlag) =
		descriptor().o_SetTraceFlag(this, flag)

	override fun clearTraceFlag(flag: TraceFlag) =
		descriptor().o_ClearTraceFlag(this, flag)

	override fun recordVariableAccess(variable: A_Variable, wasRead: Boolean) =
		descriptor().o_RecordVariableAccess(this, variable, wasRead)

	override fun variablesReadBeforeWritten() =
		descriptor().o_VariablesReadBeforeWritten(this)

	override fun variablesWritten() = descriptor().o_VariablesWritten(this)

	override fun validWriteReactorFunctions() =
		descriptor().o_ValidWriteReactorFunctions(this)

	override fun replacingCaller(newCaller: A_Continuation) =
		descriptor().o_ReplacingCaller(this, newCaller)

	override fun whenContinuationIsAvailableDo(
		whenReified: (A_Continuation) -> Unit
	) = descriptor().o_WhenContinuationIsAvailableDo(this, whenReified)

	override fun getAndClearReificationWaiters() =
		descriptor().o_GetAndClearReificationWaiters(this)

	override val isBottom get() = descriptor().o_IsBottom(this)

	override val isVacuousType get() = descriptor().o_IsVacuousType(this)

	override val isTop get() = descriptor().o_IsTop(this)

	override fun addPrivateNames(trueNames: A_Set) =
		descriptor().o_AddPrivateNames(this, trueNames)

	override fun hasValue() = descriptor().o_HasValue(this)

	override fun addUnloadFunction(unloadFunction: A_Function) =
		descriptor().o_AddUnloadFunction(this, unloadFunction)

	override fun exportedNames() = descriptor().o_ExportedNames(this)

	override val isInitializedWriteOnceVariable get() =
		descriptor().o_IsInitializedWriteOnceVariable(this)

	override fun textInterface() = descriptor().o_TextInterface(this)

	override fun setTextInterface(textInterface: TextInterface) =
		descriptor().o_SetTextInterface(this, textInterface)

	override fun writeTo(writer: JSONWriter) =
		descriptor().o_WriteTo(this, writer)

	override fun writeSummaryTo(writer: JSONWriter) =
		descriptor().o_WriteSummaryTo(this, writer)

	override fun macrosTuple() =
		descriptor().o_MacrosTuple(this)

	override fun chooseBundle(currentModule: A_Module) =
		descriptor().o_ChooseBundle(this, currentModule)

	override fun valueWasStablyComputed() =
		descriptor().o_ValueWasStablyComputed(this)

	override fun setValueWasStablyComputed(wasStablyComputed: Boolean) =
		descriptor().o_SetValueWasStablyComputed(this, wasStablyComputed)

	override fun uniqueId() = descriptor().o_UniqueId(this)

	override fun equalsListNodeType(listNodeType: A_Type) =
		descriptor().o_EqualsListNodeType(this, listNodeType)

	override fun parsingSignature() = descriptor().o_ParsingSignature(this)

	override fun moduleSemanticRestrictions() =
		descriptor().o_ModuleSemanticRestrictions(this)

	override fun moduleGrammaticalRestrictions() =
		descriptor().o_ModuleGrammaticalRestrictions(this)

	@ReferencedInGeneratedCode
	override fun fieldAt(field: A_Atom) = descriptor().o_FieldAt(this, field)

	override fun fieldAtOrNull(field: A_Atom) =
		descriptor().o_FieldAtOrNull(this, field)

	override fun fieldAtPuttingCanDestroy(
		field: A_Atom,
		value: A_BasicObject,
		canDestroy: Boolean
	) = descriptor().o_FieldAtPuttingCanDestroy(this, field, value, canDestroy)

	override fun fieldTypeAt(field: A_Atom) =
		descriptor().o_FieldTypeAt(this, field)

	override fun fieldTypeAtOrNull(field: A_Atom) =
		descriptor().o_FieldTypeAtOrNull(this, field)

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun atomicAddToMap(key: A_BasicObject, value: A_BasicObject) =
		descriptor().o_AtomicAddToMap(this, key, value)

	@Throws(VariableGetException::class)
	override fun variableMapHasKey(key: A_BasicObject) =
		descriptor().o_VariableMapHasKey(this, key)

	override fun setLexer(lexer: A_Lexer) = descriptor().o_SetLexer(this, lexer)

	override fun addLexer(lexer: A_Lexer) = descriptor().o_AddLexer(this, lexer)

	override fun originatingPhrase() = descriptor().o_OriginatingPhrase(this)

	override fun isGlobal() = descriptor().o_IsGlobal(this)

	override fun globalModule() = descriptor().o_GlobalModule(this)

	override fun globalName() = descriptor().o_GlobalName(this)

	override fun nextLexingState(): LexingState =
		descriptor().o_NextLexingState(this)

	override fun nextLexingStatePojo(): AvailObject =
		descriptor().o_NextLexingStatePojo(this)

	override fun setNextLexingStateFromPrior(priorLexingState: LexingState) =
		descriptor().o_SetNextLexingStateFromPrior(this, priorLexingState)

	override fun createLexicalScanner() =
		descriptor().o_CreateLexicalScanner(this)

	override fun lexer() = descriptor().o_Lexer(this)

	override fun setSuspendingFunction(suspendingFunction: A_Function) =
		descriptor().o_SetSuspendingFunction(this, suspendingFunction)

	override fun suspendingFunction() = descriptor().o_SuspendingFunction(this)

	override fun debugLog() = descriptor().o_DebugLog(this)

	override fun constantTypeAt(index: Int) =
		descriptor().o_ConstantTypeAt(this, index)

	override fun returnerCheckStat() = descriptor().o_ReturnerCheckStat(this)

	override fun returneeCheckStat() = descriptor().o_ReturneeCheckStat(this)

	override fun numNybbles() = descriptor().o_NumNybbles(this)

	override fun lineNumberEncodedDeltas() =
		descriptor().o_LineNumberEncodedDeltas(this)

	override fun currentLineNumber() = descriptor().o_CurrentLineNumber(this)

	override fun fiberResultType() = descriptor().o_FiberResultType(this)

	override fun testingTree(): LookupTree<A_Definition, A_Tuple> =
		descriptor().o_TestingTree(this)

	override fun clearLexingState() = descriptor().o_ClearLexingState(this)

	@ReferencedInGeneratedCode
	override fun registerDump() = descriptor().o_RegisterDump(this)

	override fun isOpen() = descriptor().o_IsOpen(this)

	override fun closeModule() = descriptor().o_CloseModule(this)

	override fun membershipChanged() = descriptor().o_MembershipChanged(this)

	override fun moduleAddMacro(macro: A_Macro) =
		descriptor().o_ModuleAddMacro(this, macro)

	override fun moduleMacros(): A_Set = descriptor().o_ModuleMacros(this)

	override fun addBundle(bundle: A_Bundle) =
		descriptor().o_AddBundle(this, bundle)

	override fun moduleBundles() = descriptor().o_ModuleBundles(this)

	override fun returnTypeIfPrimitiveFails(): A_Type =
		descriptor().o_ReturnTypeIfPrimitiveFails(this)

	override fun extractDumpedObjectAt(index: Int): AvailObject =
		descriptor().o_ExtractDumpedObjectAt(this, index)

	override fun extractDumpedLongAt(index: Int): Long =
		descriptor().o_ExtractDumpedLongAt(this, index)

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

		/**
		 * Report a virtual machine problem.
		 *
		 * @param messagePattern
		 *   A [String] describing the problem.
		 * @param arguments
		 *   The arguments to insert into the `messagePattern`.
		 */
		@JvmStatic
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
		@JvmStatic
		fun newIndexedDescriptor(
			size: Int,
			descriptor: AbstractDescriptor
		): AvailObject {
			var objectSlotCount = descriptor.numberOfFixedObjectSlots()
			if (descriptor.hasVariableObjectSlots()) {
				objectSlotCount += size
			}
			var integerSlotCount = descriptor.numberOfFixedIntegerSlots()
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
		@JvmStatic
		fun newObjectIndexedIntegerIndexedDescriptor(
			variableObjectSlots: Int,
			variableIntegerSlots: Int,
			descriptor: AbstractDescriptor
		): AvailObject = with(descriptor) {
			assert(hasVariableObjectSlots() || variableObjectSlots == 0)
			assert(hasVariableIntegerSlots() || variableIntegerSlots == 0)
			return AvailObject(
				descriptor,
				numberOfFixedObjectSlots() + variableObjectSlots,
				numberOfFixedIntegerSlots() + variableIntegerSlots)
		}

		/** The [CheckedMethod] for [iterator]. */
		@Suppress("unused")
		@JvmField
		val iteratorMethod: CheckedMethod = instanceMethod(
			AvailObject::class.java,
			AvailObject::iterator.name,
			Iterator::class.java)

		/** Access the [frameAt] method.  */
		@JvmField
		val frameAtMethod: CheckedMethod = instanceMethod(
			AvailObject::class.java,
			AvailObject::frameAt.name,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!)

		/** Access the [frameAtPut] method.  */
		@JvmField
		val frameAtPutMethod: CheckedMethod = instanceMethod(
			AvailObject::class.java,
			AvailObject::frameAtPut.name,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!,
			AvailObject::class.java)

		/** Access the [registerDump] method.  */
		@JvmField
		val registerDumpMethod: CheckedMethod = instanceMethod(
			AvailObject::class.java,
			AvailObject::registerDump.name,
			AvailObject::class.java)

		/** Access the [fieldAt] method.  */
		@JvmField
		val fieldAtMethod: CheckedMethod = instanceMethod(
			AvailObject::class.java,
			AvailObject::fieldAt.name,
			AvailObject::class.java,
			A_Atom::class.java)
	}
}
