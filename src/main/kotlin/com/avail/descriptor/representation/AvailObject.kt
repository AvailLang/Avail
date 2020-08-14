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
import com.avail.descriptor.character.CharacterDescriptor
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
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.A_MapBin
import com.avail.descriptor.maps.MapBinDescriptor
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
import com.avail.descriptor.numbers.AbstractNumberDescriptor
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Sign
import com.avail.descriptor.numbers.InfinityDescriptor
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.parsing.A_DefinitionParsingPlan
import com.avail.descriptor.parsing.A_Lexer
import com.avail.descriptor.parsing.A_ParsingPlanInProgress
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.A_Set
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
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor
import com.avail.descriptor.types.FunctionTypeDescriptor
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.TypeDescriptor
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import com.avail.dispatch.LookupTree
import com.avail.exceptions.ArithmeticException
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
	A_Token,
	A_Tuple,
	A_String,
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
			dispatch { it::o_DescribeForDebugger }
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
			dispatch { it::o_NameForDebugger }
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
		dispatch { it::o_ShowValueInNameForDebugger }

	override fun toString() = buildString {
		val recursionMap = IdentityHashMap<A_BasicObject, Void>(10)
		printOnAvoidingIndent(this@buildString, recursionMap, 1)
		assert(recursionMap.size == 0)
	}

	/**
	 * Answer whether the receiver is numerically greater than the argument.
	 *
	 * @param another
	 *   A [numeric&#32;object][AbstractNumberDescriptor].
	 * @return
	 *   Whether the receiver is strictly greater than the argument.
	 */
	override fun greaterThan(another: A_Number) = numericCompare(another).isMore()

	/**
	 * Answer whether the receiver is numerically greater than or equivalent to
	 * the argument.
	 *
	 * @param another
	 *   A [numeric&#32;object][AbstractNumberDescriptor].
	 * @return
	 *   Whether the receiver is greater than or equivalent to the argument.
	 */
	override fun greaterOrEqual(another: A_Number) =
		numericCompare(another).isMoreOrEqual()

	/**
	 * Answer whether the receiver is numerically less than the argument.
	 *
	 * @param another
	 *   A [numeric&#32;object][AbstractNumberDescriptor].
	 * @return
	 *   Whether the receiver is strictly less than the argument.
	 */
	override fun lessThan(another: A_Number) = numericCompare(another).isLess()

	/**
	 * Answer whether the receiver is numerically less than or equivalent to
	 * the argument.
	 *
	 * @param another
	 *   A [numeric&#32;object][AbstractNumberDescriptor].
	 * @return
	 *   Whether the receiver is less than or equivalent to the argument.
	 */
	override fun lessOrEqual(another: A_Number) =
		numericCompare(another).isLessOrEqual()

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
	override fun hash() = dispatch { it::o_Hash }

	/**
	 * Answer whether the [argument&#32;types][AvailObject#argsTupleType]
	 * supported by the specified [function&#32;type][FunctionTypeDescriptor]
	 * are acceptable argument types for invoking a
	 * [function][FunctionDescriptor] whose type is the receiver.
	 *
	 * @param functionType
	 *   A function type.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than those of `functionType`, `false` otherwise.
	 */
	override fun acceptsArgTypesFromFunctionType(functionType: A_Type) =
		dispatch(functionType) { it::o_AcceptsArgTypesFromFunctionType }

	/**
	 * Answer whether these are acceptable [argument&#32;types][TypeDescriptor]
	 * for invoking a [function][FunctionDescriptor] whose type is the receiver.
	 *
	 * @param argTypes
	 *   A list containing the argument types to be checked.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than those within the `argTypes` list, `false` otherwise.
	 */
	override fun acceptsListOfArgTypes(argTypes: List<A_Type>): Boolean =
		dispatch(argTypes) { it::o_AcceptsListOfArgTypes }

	/**
	 * Answer whether these are acceptable arguments for invoking a
	 * [function][FunctionDescriptor] whose type is the receiver.
	 *
	 * @param argValues
	 *   A list containing the argument values to be checked.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than the types of the values within the `argValues` list, `false`
	 *   otherwise.
	 */
	override fun acceptsListOfArgValues(argValues: List<A_BasicObject>) =
		dispatch(argValues) { it::o_AcceptsListOfArgValues }

	/**
	 * Answer whether these are acceptable [argument&#32;types][TypeDescriptor]
	 * for invoking a [function][FunctionDescriptor] that is an instance of the
	 * receiver. There may be more entries in the [tuple][TupleDescriptor] than
	 * are required by the [function&#32;type][FunctionTypeDescriptor].
	 *
	 * @param argTypes
	 *   A tuple containing the argument types to be checked.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than the corresponding elements of the `argTypes` tuple, `false`
	 *   otherwise.
	 */
	override fun acceptsTupleOfArgTypes(argTypes: A_Tuple) =
		dispatch(argTypes) { it::o_AcceptsTupleOfArgTypes }

	/**
	 * Answer whether these are acceptable arguments for invoking a
	 * [function][FunctionDescriptor] that is an instance of the receiver. There
	 * may be more entries in the [tuple][TupleDescriptor] than are required by
	 * the [function&#32;type][FunctionTypeDescriptor].
	 *
	 * @param arguments
	 *   A tuple containing the argument values to be checked.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than the types of the corresponding elements of the `arguments` tuple,
	 *   `false` otherwise.
	 */
	override fun acceptsTupleOfArguments(arguments: A_Tuple) =
		dispatch(arguments) { it::o_AcceptsTupleOfArguments }

	/**
	 * Add the [chunk][L2Chunk] with the given index to the receiver's list of
	 * chunks that depend on it.  The receiver is a [method][MethodDescriptor].
	 * A change in the method's membership (e.g., adding a new method
	 * definition) will cause the chunk to be invalidated.
	 */
	override fun addDependentChunk(chunk: L2Chunk) =
		dispatch(chunk) { it::o_AddDependentChunk }

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
		dispatch(definition) { it::o_MethodAddDefinition }

	/**
	 * Add a [grammatical&32;restriction][GrammaticalRestrictionDescriptor] to
	 * this [module][ModuleDescriptor]
	 *
	 * @param grammaticalRestriction
	 *   The set of grammatical restrictions to be added.
	 */
	override fun moduleAddGrammaticalRestriction(
		grammaticalRestriction: A_GrammaticalRestriction
	) = dispatch(grammaticalRestriction) {
		it::o_ModuleAddGrammaticalRestriction
	}

	/**
	 * Add the receiver and the argument `anInfinity` and answer the
	 * `AvailObject result`.
	 *
	 * This method should only be called from [plusCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param sign
	 *   The [Sign] of the infinity.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The result of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun addToInfinityCanDestroy(
		sign: Sign,
		canDestroy: Boolean
	) = dispatch(sign, canDestroy) { it::o_AddToInfinityCanDestroy }

	/**
	 * Add the receiver and the argument [anInteger] and answer the
	 * result.
	 *
	 * This method should only be called from [plusCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param anInteger
	 *   An [integer][IntegerDescriptor].
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The result of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun addToIntegerCanDestroy(
		anInteger: AvailObject,
		canDestroy: Boolean
	) = dispatch(anInteger, canDestroy) { it::o_AddToIntegerCanDestroy }

	/**
	 * Construct a Java [string][String] from the receiver, an Avail
	 * [string][StringDescriptor].
	 *
	 * @return
	 *   The corresponding Java string.
	 */
	override fun asNativeString() = dispatch { it::o_AsNativeString }

	/**
	 * Construct a [tuple][TupleDescriptor] from the receiver, a
	 * [set][SetDescriptor]. Element ordering in the tuple is arbitrary and
	 * unstable.
	 *
	 * @return
	 *   A tuple containing each element of the set.
	 */
	override fun asTuple() = dispatch { it::o_AsTuple }

	/**
	 * Add a [definition][DefinitionDescriptor] to this
	 * [module][ModuleDescriptor].
	 *
	 * @param definition
	 *   The definition to add to the module.
	 */
	override fun moduleAddDefinition(definition: A_Definition) =
		dispatch(definition) { it::o_ModuleAddDefinition }

	override fun addImportedName(trueName: A_Atom) =
		dispatch(trueName) { it::o_AddImportedName }

	override fun addImportedNames(trueNames: A_Set) =
		dispatch(trueNames) { it::o_AddImportedNames }

	override fun introduceNewName(trueName: A_Atom) =
		dispatch(trueName) { it::o_IntroduceNewName }

	override fun addPrivateName(trueName: A_Atom) =
		dispatch(trueName) { it::o_AddPrivateName }

	override fun setBinAddingElementHashLevelCanDestroy(
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean
	): A_BasicObject =
		dispatch(elementObject, elementObjectHash, myLevel, canDestroy) {
			it::o_SetBinAddingElementHashLevelCanDestroy }

	override fun setBinSize() = dispatch { it::o_SetBinSize }

	override fun binElementAt(index: Int) =
		dispatch(index) { it::o_BinElementAt }

	override fun binHasElementWithHash(
		elementObject: A_BasicObject,
		elementObjectHash: Int
	): Boolean = dispatch(elementObject, elementObjectHash) {
		it::o_BinHasElementWithHash
	}

	override fun setBinHash() = dispatch { it::o_SetBinHash }

	override fun binRemoveElementHashLevelCanDestroy(
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean
	) = dispatch(elementObject, elementObjectHash, myLevel, canDestroy) {
		it::o_BinRemoveElementHashLevelCanDestroy
	}

	override fun bodyBlock() = dispatch { it::o_BodyBlock }

	override fun bodySignature() = dispatch { it::o_BodySignature }

	override fun breakpointBlock() = dispatch { it::o_BreakpointBlock }

	override fun setBreakpointBlock(value: AvailObject) =
		dispatch(value) { it::o_SetBreakpointBlock }

	override fun buildFilteredBundleTree() =
		dispatch { it::o_BuildFilteredBundleTree }

	override fun caller() = dispatch { it::o_Caller }

	override fun clearValue() = dispatch { it::o_ClearValue }

	override fun function() = dispatch { it::o_Function }

	override fun functionType() = dispatch { it::o_FunctionType }

	override fun code() = dispatch { it::o_Code }

	override fun constantBindings() = dispatch { it::o_ConstantBindings }

	override fun contentType() = dispatch { it::o_ContentType }

	override fun continuation() = dispatch { it::o_Continuation }

	override fun setContinuation(value: A_Continuation) =
		dispatch(value) { it::o_SetContinuation }

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
	): A_String = dispatch(start, end, canDestroy) { it::o_CopyTupleFromToCanDestroy }.cast()

	override fun couldEverBeInvokedWith(
		argRestrictions: List<TypeRestriction>
	) = dispatch(argRestrictions) { it::o_CouldEverBeInvokedWith }

	override fun defaultType() = dispatch { it::o_DefaultType }

	/**
	 * Divide the receiver by the argument `aNumber` and answer the result.
	 *
	 * Implementations may double-dispatch to [divideIntoIntegerCanDestroy] or
	 * similar methods, where actual implementations of the division operation
	 * should reside.
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The `AvailObject result` of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun divideCanDestroy(aNumber: A_Number, canDestroy: Boolean) =
		dispatch(aNumber, canDestroy) { it::o_DivideCanDestroy }

	/**
	 * Divide the receiver by the argument `aNumber` and answer the result. The
	 * operation is not allowed to fail, so the caller must ensure that the
	 * arguments are valid, i.e. the divisor is not
	 * [zero][IntegerDescriptor.zero].
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either [number][A_Number], `false`
	 *   otherwise.
	 * @return
	 *   The `AvailObject result` of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun noFailDivideCanDestroy(
		aNumber: A_Number, canDestroy: Boolean
	) = try {
		dispatch(aNumber, canDestroy) { it::o_DivideCanDestroy }
	} catch (e: ArithmeticException) {
		// This had better not happen, otherwise the caller has violated the
		// intention of this method.
		error("noFailDivideCanDestroy failed!")
	}

	/**
	 * Divide an infinity with the given [sign][Sign] by the receiver and answer
	 * the result.
	 *
	 * This method should only be called from [divideCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param sign
	 *   The sign of the infinity.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The result of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun divideIntoInfinityCanDestroy(sign: Sign, canDestroy: Boolean) =
		dispatch(sign, canDestroy) { it::o_DivideIntoInfinityCanDestroy }

	/**
	 * Divide the argument `anInteger` by the receiver and answer the result.
	 *
	 * This method should only be called from [divideCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param anInteger
	 *   An [integer][IntegerDescriptor].
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The `AvailObject result` of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun divideIntoIntegerCanDestroy(
		anInteger: AvailObject, canDestroy: Boolean
	) = dispatch(anInteger, canDestroy) { it::o_DivideIntoIntegerCanDestroy }

	override fun ensureMutable() = dispatch { it::o_EnsureMutable }

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
		dispatch(aTuple) { it::o_EqualsAnyTuple }

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
		dispatch(aByteString) { it::o_EqualsByteString }

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
		dispatch(aByteTuple) { it::o_EqualsByteTuple }

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
		dispatch(anIntegerIntervalTuple) { it::o_EqualsIntegerIntervalTuple }

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
		dispatch(anIntTuple) { it::o_EqualsIntTuple }

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
		dispatch(aLongTuple) { it::o_EqualsLongTuple }

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
	) = dispatch(aSmallIntegerIntervalTuple) {
		it::o_EqualsSmallIntegerIntervalTuple
	}

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
		dispatch(aRepeatedElementTuple) { it::o_EqualsRepeatedElementTuple }

	/**
	 * Answer whether the receiver, an [AvailObject], is a
	 * [character][CharacterDescriptor] with a code point equal to the integer
	 * argument.
	 *
	 * @param aCodePoint
	 *   The code point to be compared to the receiver's.
	 * @return
	 *   `true` if the receiver is a character with a code point equal to the
	 *   argument, `false` otherwise.
	 */
	override fun equalsCharacterWithCodePoint(aCodePoint: Int) =
		dispatch(aCodePoint) { it::o_EqualsCharacterWithCodePoint }

	override fun equalsFiberType(aFiberType: A_Type) =
		dispatch(aFiberType) { it::o_EqualsFiberType }

	override fun equalsFunction(aFunction: A_Function) =
		dispatch(aFunction) { it::o_EqualsFunction }

	/**
	 * Answer whether the receiver, an [AvailObject], and the
	 * argument, a [function&#32;type][FunctionTypeDescriptor], are equal.
	 *
	 * @param aFunctionType
	 *   The function type used in the comparison.
	 * @return
	 *   `true` IFF the receiver is also a function type and:
	 *   * The [argument&#32;types][argsTupleType] correspond,
	 *   * The [return&#32;types][returnType] correspond, and
	 *   * The [declared&#32;exceptions][declaredExceptions] correspond.
	 */
	override fun equalsFunctionType(aFunctionType: A_Type) =
		dispatch(aFunctionType) { it::o_EqualsFunctionType }

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
		dispatch(aCompiledCode) { it::o_EqualsCompiledCode }

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
		dispatch(aVariable) { it::o_EqualsVariable }

	override fun equalsVariableType(aVariableType: A_Type) =
		dispatch(aVariableType) { it::o_EqualsVariableType }

	override fun equalsContinuation(aContinuation: A_Continuation) =
		dispatch(aContinuation) { it::o_EqualsContinuation }

	override fun equalsContinuationType(aContinuationType: A_Type) =
		dispatch(aContinuationType) { it::o_EqualsContinuationType }

	override fun equalsDouble(aDouble: Double) =
		dispatch(aDouble) { it::o_EqualsDouble }

	override fun equalsFloat(aFloat: Float) =
		dispatch(aFloat) { it::o_EqualsFloat }

	/**
	 * Answer whether the receiver is an [infinity][InfinityDescriptor] with the
	 * specified [Sign].
	 *
	 * @param sign
	 *   The type of infinity for comparison.
	 * @return
	 *   `true` if the receiver is an infinity of the specified sign, `false`
	 *   otherwise.
	 */
	override fun equalsInfinity(sign: Sign) =
		dispatch(sign) { it::o_EqualsInfinity }

	override fun equalsInteger(anAvailInteger: AvailObject) =
		dispatch(anAvailInteger) { it::o_EqualsInteger }

	override fun equalsIntegerRangeType(anIntegerRangeType: A_Type) =
		dispatch(anIntegerRangeType) { it::o_EqualsIntegerRangeType }

	override fun equalsMap(aMap: A_Map) = dispatch(aMap) { it::o_EqualsMap }

	override fun equalsMapType(aMapType: A_Type) =
		dispatch(aMapType) { it::o_EqualsMapType }

	override fun equalsNybbleTuple(aNybbleTuple: A_Tuple) =
		dispatch(aNybbleTuple) { it::o_EqualsNybbleTuple }

	override fun equalsObject(anObject: AvailObject) =
		dispatch(anObject) { it::o_EqualsObject }

	override fun equalsObjectTuple(anObjectTuple: A_Tuple) =
		dispatch(anObjectTuple) { it::o_EqualsObjectTuple }

	override fun equalsPhraseType(aPhraseType: A_Type) =
		dispatch(aPhraseType) { it::o_EqualsPhraseType }

	override fun equalsPojo(aPojo: AvailObject) =
		dispatch(aPojo) { it::o_EqualsPojo }

	override fun equalsPojoType(aPojoType: AvailObject) =
		dispatch(aPojoType) { it::o_EqualsPojoType }

	override fun equalsPrimitiveType(aPrimitiveType: A_Type) =
		dispatch(aPrimitiveType) { it::o_EqualsPrimitiveType }

	override fun equalsRawPojoFor(
		otherRawPojo: AvailObject,
		otherJavaObject: Any?
	) = dispatch(otherRawPojo, otherJavaObject) { it::o_EqualsRawPojoFor }

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
		dispatch(aTuple) { it::o_EqualsReverseTuple }

	override fun equalsSet(aSet: A_Set) = dispatch(aSet) { it::o_EqualsSet }

	override fun equalsSetType(aSetType: A_Type) =
		dispatch(aSetType) { it::o_EqualsSetType }

	override fun equalsTupleType(aTupleType: A_Type) =
		dispatch(aTupleType) { it::o_EqualsTupleType }

	override fun equalsTwoByteString(aTwoByteString: A_String) =
		dispatch(aTwoByteString) { it::o_EqualsTwoByteString }

	/** Note: [nil] is never the target of an indirection. */
	override fun equalsNil() = this === nil

	override fun executionState() = dispatch { it::o_ExecutionState }

	override fun setExecutionState(value: ExecutionState) =
		dispatch(value) { it::o_SetExecutionState }

	override fun extractUnsignedByte() =
		dispatch { it::o_ExtractUnsignedByte }

	override fun extractDouble() = dispatch { it::o_ExtractDouble }

	override fun extractFloat() = dispatch { it::o_ExtractFloat }

	override fun extractInt() = dispatch { it::o_ExtractInt }

	/**
	 * Extract a 64-bit signed Kotlin [Long] from the receiver
	 *
	 * @return A 64-bit signed Kotlin [Long].
	 */
	override fun extractLong() = dispatch { it::o_ExtractLong }

	override fun extractNybble() = dispatch { it::o_ExtractNybble }

	override fun fieldMap() = dispatch { it::o_FieldMap }

	override fun fieldTypeMap() = dispatch { it::o_FieldTypeMap }

	override fun filterByTypes(argTypes: List<A_Type>) =
		dispatch(argTypes) { it::o_FilterByTypes }

	@Throws(VariableGetException::class)
	override fun getValue() = dispatch { it::o_GetValue }

	/**
	 * Answer whether the receiver, a [set][SetDescriptor], contains the
	 * specified element.
	 *
	 * @param elementObject
	 *   The element.
	 * @return
	 *   `true` if the receiver contains the element, `false` otherwise.
	 */
	override fun hasElement(elementObject: A_BasicObject) =
		dispatch(elementObject) { it::o_HasElement }

	override fun hashOrZero() = dispatch { it::o_HashOrZero }

	override fun setHashOrZero(value: Int) =
		dispatch(value) { it::o_SetHashOrZero }

	override fun hasKey(keyObject: A_BasicObject) =
		dispatch(keyObject) { it::o_HasKey }

	override fun hasObjectInstance(potentialInstance: AvailObject) =
		dispatch(potentialInstance) { it::o_HasObjectInstance }

	override fun definitionsAtOrBelow(argRestrictions: List<TypeRestriction>) =
		dispatch(argRestrictions) { it::o_DefinitionsAtOrBelow }

	override fun definitionsTuple() = dispatch { it::o_DefinitionsTuple }

	override fun includesDefinition(imp: A_Definition) =
		dispatch(imp) { it::o_IncludesDefinition }

	override fun setInterruptRequestFlag(flag: InterruptRequestFlag) =
		dispatch(flag) { it::o_SetInterruptRequestFlag }

	override fun decrementCountdownToReoptimize(
		continuation: (Boolean) -> Unit
	) = dispatch(continuation) { it::o_DecrementCountdownToReoptimize }

	override fun countdownToReoptimize(value: Long) =
		dispatch(value) { it::o_CountdownToReoptimize }

	override val isAbstract get() = dispatch { it::o_IsAbstract }

	override fun isAbstractDefinition() =
		dispatch { it::o_IsAbstractDefinition }

	override fun representationCostOfTupleType() =
		dispatch { it::o_RepresentationCostOfTupleType }

	override fun isBinSubsetOf(potentialSuperset: A_Set) =
		dispatch(potentialSuperset) { it::o_IsBinSubsetOf }

	/**
	 * Is the receiver an Avail boolean?
	 *
	 * @return
	 *   `true` if the receiver is a boolean, `false` otherwise.
	 */
	override val isBoolean get() = dispatch { it::o_IsBoolean }

	/**
	 * Is the receiver an Avail unsigned byte?
	 *
	 * @return
	 *   `true` if the argument is an unsigned byte, `false` otherwise.
	 */
	override val isUnsignedByte get() = dispatch { it::o_IsUnsignedByte }

	/**
	 * Is the receiver an Avail byte tuple?
	 *
	 * @return
	 *   `true` if the receiver is a byte tuple, `false` otherwise.
	 */
	override val isByteTuple get() = dispatch { it::o_IsByteTuple }

	/**
	 * Is the receiver an Avail character?
	 *
	 * @return
	 *   `true` if the receiver is a character, `false` otherwise.
	 */
	override val isCharacter get() = dispatch { it::o_IsCharacter }

	/**
	 * Is the receiver an Avail function?
	 *
	 * @return
	 *   `true` if the receiver is a function, `false` otherwise.
	 */
	override val isFunction get() = dispatch { it::o_IsFunction }

	/**
	 * Is the receiver an Avail atom?
	 *
	 * @return
	 *   `true` if the receiver is an atom, `false` otherwise.
	 */
	override val isAtom get() = dispatch { it::o_IsAtom }

	/**
	 * Is the receiver an Avail extended integer?
	 *
	 * @return
	 *   `true` if the receiver is an extended integer, `false` otherwise.
	 */
	override val isExtendedInteger get() =
		dispatch { it::o_IsExtendedInteger }

	override val isFinite get() = dispatch { it::o_IsFinite }

	/**
	 * Is the receiver a [forward&#32;declaration][ForwardDefinitionDescriptor]?
	 *
	 * @return
	 *   `true` if the receiver is a forward declaration site.
	 */
	override fun isForwardDefinition() =
		dispatch { it::o_IsForwardDefinition }

	/**
	 * Is the receiver a [method&#32;definition][MethodDefinitionDescriptor]?
	 *
	 * @return `true` if the receiver is a method definition.
	 */
	override fun isMethodDefinition() = dispatch { it::o_IsMethodDefinition }

	override fun isInstanceOf(aType: A_Type) =
		dispatch(aType) { it::o_IsInstanceOf }

	override fun isInstanceOfKind(aType: A_Type) =
		dispatch(aType) { it::o_IsInstanceOfKind }

	override val isIntegerIntervalTuple get() =
		dispatch { it::o_IsIntegerIntervalTuple }

	override val isIntTuple get() = dispatch { it::o_IsIntTuple }

	override val isLongTuple get() = dispatch { it::o_IsLongTuple }

	override val isSmallIntegerIntervalTuple get() =
		dispatch { it::o_IsSmallIntegerIntervalTuple }

	override val isRepeatedElementTuple get() =
		dispatch { it::o_IsRepeatedElementTuple }

	override val isIntegerRangeType get() =
		dispatch { it::o_IsIntegerRangeType }

	/**
	 * Is the receiver an Avail map?
	 *
	 * @return
	 *   `true` if the receiver is a map, `false` otherwise.
	 */
	override val isMap get() = dispatch { it::o_IsMap }

	override val isMapType get() = dispatch { it::o_IsMapType }

	/**
	 * Is the receiver an Avail nybble?
	 *
	 * @return
	 *   `true` if the receiver is a nybble, `false` otherwise.
	 */
	override val isNybble get() = dispatch { it::o_IsNybble }

	override fun isPositive() = dispatch { it::o_IsPositive }

	/**
	 * Is the receiver an Avail set?
	 *
	 * @return
	 *   `true` if the receiver is a set, `false` otherwise.
	 */
	override val isSet get() = dispatch { it::o_IsSet }

	override val isSetType get() = dispatch { it::o_IsSetType }

	override fun isSubsetOf(another: A_Set) =
		dispatch(another) { it::o_IsSubsetOf }

	/**
	 * Is the receiver an Avail string?
	 *
	 * @return
	 *   `true` if the receiver is an Avail string, `false` otherwise.
	 */
	override val isString get() = dispatch { it::o_IsString }

	override fun isSubtypeOf(aType: A_Type) =
		dispatch(aType) { it::o_IsSubtypeOf }

	override fun isSupertypeOfVariableType(aVariableType: A_Type) =
		dispatch(aVariableType) { it::o_IsSupertypeOfVariableType }

	override fun isSupertypeOfContinuationType(aContinuationType: A_Type) =
		dispatch(aContinuationType) { it::o_IsSupertypeOfContinuationType }

	override fun isSupertypeOfFiberType(aFiberType: A_Type) =
		dispatch(aFiberType) { it::o_IsSupertypeOfFiberType }

	override fun isSupertypeOfFunctionType(aFunctionType: A_Type) =
		dispatch(aFunctionType) { it::o_IsSupertypeOfFunctionType }

	override fun isSupertypeOfIntegerRangeType(anIntegerRangeType: A_Type) =
		dispatch(anIntegerRangeType) { it::o_IsSupertypeOfIntegerRangeType }

	override fun isSupertypeOfListNodeType(aListNodeType: A_Type) =
		dispatch(aListNodeType) { it::o_IsSupertypeOfListNodeType }

	override fun isSupertypeOfTokenType(aTokenType: A_Type) =
		dispatch(aTokenType) { it::o_IsSupertypeOfTokenType }

	override fun isSupertypeOfLiteralTokenType(aLiteralTokenType: A_Type) =
		dispatch(aLiteralTokenType) { it::o_IsSupertypeOfLiteralTokenType }

	override fun isSupertypeOfMapType(aMapType: AvailObject) =
		dispatch(aMapType) { it::o_IsSupertypeOfMapType }

	override fun isSupertypeOfObjectType(anObjectType: AvailObject) =
		dispatch(anObjectType) { it::o_IsSupertypeOfObjectType }

	override fun isSupertypeOfPhraseType(aPhraseType: A_Type) =
		dispatch(aPhraseType) { it::o_IsSupertypeOfPhraseType }

	override fun isSupertypeOfPojoType(aPojoType: A_Type) =
		dispatch(aPojoType) { it::o_IsSupertypeOfPojoType }

	override fun isSupertypeOfPrimitiveTypeEnum(primitiveTypeEnum: Types) =
		dispatch(primitiveTypeEnum) { it::o_IsSupertypeOfPrimitiveTypeEnum }

	override fun isSupertypeOfSetType(aSetType: A_Type) =
		dispatch(aSetType) { it::o_IsSupertypeOfSetType }

	override val isSupertypeOfBottom
		get() = dispatch { it::o_IsSupertypeOfBottom }

	override fun isSupertypeOfTupleType(aTupleType: A_Type) =
		dispatch(aTupleType) { it::o_IsSupertypeOfTupleType }

	override fun isSupertypeOfEnumerationType(
		anEnumerationType: A_Type
	) = dispatch(anEnumerationType) { it::o_IsSupertypeOfEnumerationType }

	/**
	 * Is the receiver an Avail tuple?
	 *
	 * @return
	 *   `true` if the receiver is a tuple, `false` otherwise.
	 */
	override val isTuple get() = dispatch { it::o_IsTuple }

	override val isTupleType get() = dispatch { it::o_IsTupleType }

	override val isType get() = dispatch { it::o_IsType }

	/**
	 * Answer an [iterator][Iterator] suitable for traversing the
	 * elements of the receiver with a Java *foreach* construct.
	 *
	 * @return
	 *   An [iterator][Iterator].
	 */
	@ReferencedInGeneratedCode
	override fun iterator(): Iterator<AvailObject> =
		dispatch { it::o_Iterator }

	override fun spliterator(): Spliterator<AvailObject> =
		dispatch { it::o_Spliterator }

	override fun keysAsSet() = dispatch { it::o_KeysAsSet }

	override fun keyType() = dispatch { it::o_KeyType }

	override fun levelTwoChunk() = dispatch { it::o_LevelTwoChunk }

	override fun levelTwoChunkOffset(chunk: L2Chunk, offset: Int) =
		dispatch(chunk, offset) { it::o_LevelTwoChunkOffset }

	override fun levelTwoOffset() = dispatch { it::o_LevelTwoOffset }

	override fun literal() = dispatch { it::o_Literal }

	override fun literalAt(index: Int) = dispatch(index) { it::o_LiteralAt }

	@ReferencedInGeneratedCode
	override fun frameAt(index: Int) =
		dispatch(index) { it::o_FrameAt }

	@ReferencedInGeneratedCode
	override fun frameAtPut(index: Int, value: AvailObject): AvailObject =
		dispatch(index, value) { it::o_FrameAtPut }

	override fun localTypeAt(index: Int) =
		dispatch(index) { it::o_LocalTypeAt }

	@Throws(MethodDefinitionException::class)
	override fun lookupByTypesFromTuple(argumentTypeTuple: A_Tuple) =
		dispatch(argumentTypeTuple) { it::o_LookupByTypesFromTuple }

	@Throws(MethodDefinitionException::class)
	override fun lookupByValuesFromList(argumentList: List<A_BasicObject>) =
		dispatch(argumentList) { it::o_LookupByValuesFromList }

	override fun lowerBound() = dispatch { it::o_LowerBound }

	override fun lowerInclusive() = dispatch { it::o_LowerInclusive }

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
		dispatch { it::o_MakeSubobjectsImmutable }

	override fun makeSubobjectsShared() =
		dispatch { it::o_MakeSubobjectsShared }

	override fun mapAt(keyObject: A_BasicObject) =
		dispatch(keyObject) { it::o_MapAt }

	override fun mapAtPuttingCanDestroy(
		keyObject: A_BasicObject,
		newValueObject: A_BasicObject,
		canDestroy: Boolean
	) = dispatch(keyObject, newValueObject, canDestroy) {
		it::o_MapAtPuttingCanDestroy
	}

	override fun mapAtReplacingCanDestroy(
		key: A_BasicObject,
		notFoundValue: A_BasicObject,
		transformer: (AvailObject, AvailObject) -> A_BasicObject,
		canDestroy: Boolean
	): A_Map = dispatch(key, notFoundValue, transformer, canDestroy) {
		it::o_MapAtReplacingCanDestroy
	}

	override fun mapBinSize() = dispatch { it::o_MapBinSize }

	override fun mapSize() = dispatch { it::o_MapSize }

	override fun mapWithoutKeyCanDestroy(
		keyObject: A_BasicObject,
		canDestroy: Boolean
	) = dispatch(keyObject, canDestroy) { it::o_MapWithoutKeyCanDestroy }

	override fun maxStackDepth() = dispatch { it::o_MaxStackDepth }

	override fun methodDefinitions() = dispatch { it::o_MethodDefinitions }

	/**
	 * Subtract the argument [aNumber] from the receiver and answer the result.
	 *
	 * Implementations may double-dispatch to methods like
	 * [subtractFromIntegerCanDestroy] or [subtractFromInfinityCanDestroy],
	 * where actual implementations of the subtraction operation should reside.
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The result of subtracting the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun minusCanDestroy(aNumber: A_Number, canDestroy: Boolean) =
		dispatch(aNumber, canDestroy) { it::o_MinusCanDestroy }

	/**
	 * Difference the receiver and the argument [aNumber] and answer the result.
	 * The operation is not allowed to fail, so the caller must ensure that the
	 * arguments are valid, i.e. not [infinities][InfinityDescriptor] of like
	 * [Sign].
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The result of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun noFailMinusCanDestroy(aNumber: A_Number, canDestroy: Boolean) =
		try {
			dispatch(aNumber, canDestroy) { it::o_MinusCanDestroy }
		} catch (e: ArithmeticException) {
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			error("noFailMinusCanDestroy failed!")
		}

	/**
	 * Multiply the receiver and an infinity with the given [Sign], and answer
	 * the result.
	 *
	 * This method should only be called from [timesCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param sign
	 *   The [Sign] of an [infinity][InfinityDescriptor].
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The result of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun multiplyByInfinityCanDestroy(sign: Sign, canDestroy: Boolean) =
		dispatch(sign, canDestroy) { it::o_MultiplyByInfinityCanDestroy }

	/**
	 * Multiply the receiver and the argument [anInteger] and answer the result.
	 *
	 * This method should only be called from [timesCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param anInteger
	 *   An [integer][IntegerDescriptor].
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The result of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun multiplyByIntegerCanDestroy(
		anInteger: AvailObject,
		canDestroy: Boolean
	) = dispatch(anInteger, canDestroy) { it::o_MultiplyByIntegerCanDestroy }

	override fun importedNames() = dispatch { it::o_ImportedNames }

	override fun nameVisible(trueName: A_Atom) =
		dispatch(trueName) { it::o_NameVisible }

	override fun newNames() = dispatch { it::o_NewNames }

	override fun numArgs() = dispatch { it::o_NumArgs }

	override fun numSlots() = dispatch { it::o_NumSlots }

	override fun numLiterals() = dispatch { it::o_NumLiterals }

	override fun numLocals() = dispatch { it::o_NumLocals }

	override fun numConstants() = dispatch { it::o_NumConstants }

	override fun numOuters() = dispatch { it::o_NumOuters }

	override fun numOuterVars() = dispatch { it::o_NumOuterVars }

	override fun nybbles() = dispatch { it::o_Nybbles }

	override fun optionallyNilOuterVar(index: Int) =
		dispatch(index) { it::o_OptionallyNilOuterVar }

	override fun outerTypeAt(index: Int) =
		dispatch(index) { it::o_OuterTypeAt }

	override fun outerVarAt(index: Int) = dispatch(index) { it::o_OuterVarAt }

	override fun outerVarAtPut(index: Int, value: AvailObject) =
		dispatch(index, value) { it::o_OuterVarAtPut }

	override fun parent() = dispatch { it::o_Parent }

	override fun pc() = dispatch { it::o_Pc }

	/**
	 * Add the receiver and the argument [aNumber] and answer the result.
	 *
	 * Implementations may double-dispatch to [addToIntegerCanDestroy] or
	 * [addToInfinityCanDestroy], where actual implementations of the addition
	 * operation should reside.
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The result of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun plusCanDestroy(aNumber: A_Number, canDestroy: Boolean) =
		dispatch(aNumber, canDestroy) { it::o_PlusCanDestroy }

	/**
	 * Add the receiver and the argument [aNumber] and answer the result. The
	 * operation is not allowed to fail, so the caller must ensure that the
	 * arguments are valid, i.e. not [infinities][InfinityDescriptor] of unlike
	 * [Sign].
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The result of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun noFailPlusCanDestroy(aNumber: A_Number, canDestroy: Boolean) =
		try {
			dispatch(aNumber, canDestroy) { it::o_PlusCanDestroy }
		} catch (e: ArithmeticException) {
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			error("noFailPlusCanDestroy failed!")
		}

	override fun primitiveNumber() = dispatch { it::o_PrimitiveNumber }

	override fun priority() = dispatch { it::o_Priority }

	override fun setPriority(value: Int) =
		dispatch(value) { it::o_SetPriority }

	override fun privateNames() = dispatch { it::o_PrivateNames }

	override fun fiberGlobals() = dispatch { it::o_FiberGlobals }

	override fun setFiberGlobals(value: A_Map) =
		dispatch(value) { it::o_SetFiberGlobals }

	override fun rawSignedIntegerAt(index: Int) =
		dispatch(index) { it::o_RawSignedIntegerAt }

	override fun rawSignedIntegerAtPut(index: Int, value: Int) =
		dispatch(index, value) { it::o_RawSignedIntegerAtPut }

	override fun rawUnsignedIntegerAt(index: Int) =
		dispatch(index) { it::o_RawUnsignedIntegerAt }

	override fun rawUnsignedIntegerAtPut(index: Int, value: Int) =
		dispatch(index, value) { it::o_RawUnsignedIntegerAtPut }

	override fun removeDependentChunk(chunk: L2Chunk) =
		dispatch(chunk) { it::o_RemoveDependentChunk }

	override fun removeFrom(loader: AvailLoader, afterRemoval: () -> Unit) =
		dispatch(loader, afterRemoval) { it::o_RemoveFrom }

	override fun removeDefinition(definition: A_Definition) =
		dispatch(definition) { it::o_RemoveDefinition }

	override fun resolveForward(forwardDefinition: A_BasicObject) =
		dispatch(forwardDefinition) { it::o_ResolveForward }

	override fun returnType() = dispatch { it::o_ReturnType }

	override fun scanSubobjects(visitor: AvailSubobjectVisitor) =
		dispatch(visitor) { it::o_ScanSubobjects }

	override fun setIntersectionCanDestroy(
		otherSet: A_Set,
		canDestroy: Boolean
	) = dispatch(otherSet, canDestroy) { it::o_SetIntersectionCanDestroy }

	override fun setMinusCanDestroy(otherSet: A_Set, canDestroy: Boolean) =
		dispatch(otherSet, canDestroy) { it::o_SetMinusCanDestroy }

	override fun setSize() = dispatch { it::o_SetSize }

	override fun setUnionCanDestroy(otherSet: A_Set, canDestroy: Boolean) =
		dispatch(otherSet, canDestroy) { it::o_SetUnionCanDestroy }

	@Throws(VariableSetException::class)
	override fun setValue(newValue: A_BasicObject) =
		dispatch(newValue) { it::o_SetValue }

	override fun setValueNoCheck(newValue: A_BasicObject) =
		dispatch(newValue) { it::o_SetValueNoCheck }

	override fun setWithElementCanDestroy(
		newElementObject: A_BasicObject,
		canDestroy: Boolean
	) = dispatch(newElementObject, canDestroy) {
		it::o_SetWithElementCanDestroy
	}

	override fun setWithoutElementCanDestroy(
		elementObjectToExclude: A_BasicObject,
		canDestroy: Boolean
	) = dispatch(elementObjectToExclude, canDestroy) {
		it::o_SetWithoutElementCanDestroy
	}

	override fun sizeRange() = dispatch { it::o_SizeRange }

	override fun stackAt(slotIndex: Int) =
		dispatch(slotIndex) { it::o_StackAt }

	override fun stackp() = dispatch { it::o_Stackp }

	override fun start() = dispatch { it::o_Start }

	override fun startingChunk() = dispatch { it::o_StartingChunk }

	override fun setStartingChunkAndReoptimizationCountdown(
		chunk: L2Chunk,
		countdown: Long
	) = dispatch(chunk, countdown) {
		it::o_SetStartingChunkAndReoptimizationCountdown
	}

	override fun string() = dispatch { it::o_String }

	/**
	 * Difference the operands and answer the result.
	 *
	 * This method should only be called from [minusCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param sign
	 *   The sign of the [infinity][InfinityDescriptor] from which to subtract.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The result of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun subtractFromInfinityCanDestroy(
		sign: Sign,
		canDestroy: Boolean
	) = dispatch(sign, canDestroy) { it::o_SubtractFromInfinityCanDestroy }

	/**
	 * Difference the operands and answer the result.
	 *
	 * This method should only be called from [minusCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param anInteger
	 *   An [integer][IntegerDescriptor].
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The result of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun subtractFromIntegerCanDestroy(
		anInteger: AvailObject,
		canDestroy: Boolean
	) = dispatch(anInteger, canDestroy) { it::o_SubtractFromIntegerCanDestroy }

	/**
	 * Multiply the receiver and the argument [aNumber] and answer the result.
	 *
	 * Implementations may double-dispatch to methods like
	 * [multiplyByIntegerCanDestroy] or [multiplyByInfinityCanDestroy], where
	 * actual implementations of the multiplication operation should reside.
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The result of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun timesCanDestroy(aNumber: A_Number, canDestroy: Boolean) =
		dispatch(aNumber, canDestroy) { it::o_TimesCanDestroy }

	/**
	 * Multiply the receiver and the argument [aNumber] and answer the result.
	 * The operation is not allowed to fail, so the caller must ensure that the
	 * arguments are valid, i.e. not [zero][IntegerDescriptor.zero] and
	 * [infinity][InfinityDescriptor].
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The `AvailObject result` of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun noFailTimesCanDestroy(
		aNumber: A_Number,
		canDestroy: Boolean
	) =
		try
		{
			dispatch(aNumber, canDestroy) { it::o_TimesCanDestroy }
		}
		catch (e: ArithmeticException)
		{
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			error("noFailTimesCanDestroy failed!")
		}

	override fun tokenType(): TokenType = dispatch { it::o_TokenType }

	override fun traversed() = dispatch { it::o_Traversed }

	override fun trimExcessInts() = dispatch { it::o_TrimExcessInts }

	override fun trueNamesForStringName(stringName: A_String) =
		dispatch(stringName) { it::o_TrueNamesForStringName }

	override fun kind() = dispatch { it::o_Kind }

	override fun typeAtIndex(index: Int) =
		dispatch(index) { it::o_TypeAtIndex }

	override fun typeIntersection(another: A_Type) =
		dispatch(another) { it::o_TypeIntersection }

	override fun typeIntersectionOfCompiledCodeType(aCompiledCodeType: A_Type) =
		dispatch(aCompiledCodeType) {
			it::o_TypeIntersectionOfCompiledCodeType
		}

	override fun typeIntersectionOfContinuationType(aContinuationType: A_Type) =
		dispatch(aContinuationType) {
			it::o_TypeIntersectionOfContinuationType
		}

	override fun typeIntersectionOfFiberType(aFiberType: A_Type) =
		dispatch(aFiberType) { it::o_TypeIntersectionOfFiberType }

	override fun typeIntersectionOfFunctionType(aFunctionType: A_Type) =
		dispatch(aFunctionType) { it::o_TypeIntersectionOfFunctionType }

	override fun typeIntersectionOfIntegerRangeType(
		anIntegerRangeType: A_Type
	) = dispatch(anIntegerRangeType) {
		it::o_TypeIntersectionOfIntegerRangeType
	}

	override fun typeIntersectionOfListNodeType(aListNodeType: A_Type) =
		dispatch(aListNodeType) { it::o_TypeIntersectionOfListNodeType }

	override fun typeIntersectionOfMapType(aMapType: A_Type) =
		dispatch(aMapType) { it::o_TypeIntersectionOfMapType }

	override fun typeIntersectionOfObjectType(anObjectType: AvailObject) =
		dispatch(anObjectType) { it::o_TypeIntersectionOfObjectType }

	override fun typeIntersectionOfPhraseType(aPhraseType: A_Type) =
		dispatch(aPhraseType) { it::o_TypeIntersectionOfPhraseType }

	override fun typeIntersectionOfPojoType(aPojoType: A_Type) =
		dispatch(aPojoType) { it::o_TypeIntersectionOfPojoType }

	override fun typeIntersectionOfSetType(aSetType: A_Type) =
		dispatch(aSetType) { it::o_TypeIntersectionOfSetType }

	override fun typeIntersectionOfTupleType(aTupleType: A_Type) =
		dispatch(aTupleType) { it::o_TypeIntersectionOfTupleType }

	override fun typeIntersectionOfVariableType(aVariableType: A_Type) =
		dispatch(aVariableType) { it::o_TypeIntersectionOfVariableType }

	override fun typeTuple() = dispatch { it::o_TypeTuple }

	override fun typeUnion(another: A_Type) =
		dispatch(another) { it::o_TypeUnion }

	override fun typeUnionOfFiberType(aFiberType: A_Type) =
		dispatch(aFiberType) { it::o_TypeUnionOfFiberType }

	override fun typeUnionOfFunctionType(aFunctionType: A_Type) =
		dispatch(aFunctionType) { it::o_TypeUnionOfFunctionType }

	override fun typeUnionOfVariableType(aVariableType: A_Type) =
		dispatch(aVariableType) { it::o_TypeUnionOfVariableType }

	override fun typeUnionOfContinuationType(aContinuationType: A_Type) =
		dispatch(aContinuationType) { it::o_TypeUnionOfContinuationType }

	override fun typeUnionOfIntegerRangeType(anIntegerRangeType: A_Type) =
		dispatch(anIntegerRangeType) { it::o_TypeUnionOfIntegerRangeType }

	override fun typeUnionOfListNodeType(aListNodeType: A_Type) =
		dispatch(aListNodeType) { it::o_TypeUnionOfListNodeType }

	override fun typeUnionOfMapType(aMapType: A_Type) =
		dispatch(aMapType) { it::o_TypeUnionOfMapType }

	override fun typeUnionOfObjectType(anObjectType: AvailObject) =
		dispatch(anObjectType) { it::o_TypeUnionOfObjectType }

	override fun typeUnionOfPhraseType(aPhraseType: A_Type) =
		dispatch(aPhraseType) { it::o_TypeUnionOfPhraseType }

	override fun typeUnionOfPojoType(aPojoType: A_Type) =
		dispatch(aPojoType) { it::o_TypeUnionOfPojoType }

	override fun typeUnionOfSetType(aSetType: A_Type) =
		dispatch(aSetType) { it::o_TypeUnionOfSetType }

	override fun typeUnionOfTupleType(aTupleType: A_Type) =
		dispatch(aTupleType) { it::o_TypeUnionOfTupleType }

	override fun unionOfTypesAtThrough(startIndex: Int, endIndex: Int) =
		dispatch(startIndex, endIndex) { it::o_UnionOfTypesAtThrough }

	override fun upperBound() = dispatch { it::o_UpperBound }

	override fun upperInclusive() = dispatch { it::o_UpperInclusive }

	override fun value() = dispatch { it::o_Value }

	override fun valuesAsTuple() = dispatch { it::o_ValuesAsTuple }

	override fun variableBindings() = dispatch { it::o_VariableBindings }

	override fun visibleNames() = dispatch { it::o_VisibleNames }

	override fun resultType() = dispatch { it::o_ResultType }

	override fun primitive(): Primitive? = dispatch { it::o_Primitive }

	override fun declarationKind() = dispatch { it::o_DeclarationKind }

	override fun expressionType() = dispatch { it::o_ExpressionType }

	override fun binUnionKind() = dispatch { it::o_BinUnionKind }

	override fun lineNumber() = dispatch { it::o_LineNumber }

	override val isSetBin get() = dispatch { it::o_IsSetBin }

	override fun mapIterable() = dispatch { it::o_MapIterable }

	override fun declaredExceptions() = dispatch { it::o_DeclaredExceptions }

	override val isInt get() = dispatch { it::o_IsInt }

	override val isLong get() = dispatch { it::o_IsLong }

	override fun argsTupleType() = dispatch { it::o_ArgsTupleType }

	override fun equalsInstanceTypeFor(anInstanceType: AvailObject) =
		dispatch(anInstanceType) { it::o_EqualsInstanceTypeFor }

	override fun instances() = dispatch { it::o_Instances }

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
		dispatch(aSet) { it::o_EqualsEnumerationWithSet }

	override val isEnumeration get() = dispatch { it::o_IsEnumeration }

	override fun enumerationIncludesInstance(potentialInstance: AvailObject) =
		dispatch(potentialInstance) { it::o_EnumerationIncludesInstance }

	override fun valueType() = dispatch { it::o_ValueType }

	override fun computeSuperkind() = dispatch { it::o_ComputeSuperkind }

	override fun equalsCompiledCodeType(aCompiledCodeType: A_Type) =
		dispatch(aCompiledCodeType) { it::o_EqualsCompiledCodeType }

	override fun isSupertypeOfCompiledCodeType(aCompiledCodeType: A_Type) =
		dispatch(aCompiledCodeType) { it::o_IsSupertypeOfCompiledCodeType }

	override fun typeUnionOfCompiledCodeType(aCompiledCodeType: A_Type) =
		dispatch(aCompiledCodeType) { it::o_TypeUnionOfCompiledCodeType }

	override fun equalsEnumerationType(anEnumerationType: A_BasicObject) =
		dispatch(anEnumerationType) { it::o_EqualsEnumerationType }

	override fun readType() = dispatch { it::o_ReadType }

	override fun writeType() = dispatch { it::o_WriteType }

	override fun setVersions(versionStrings: A_Set) =
		dispatch(versionStrings) { it::o_SetVersions }

	override fun versions() = dispatch { it::o_Versions }

	override fun phraseKind() = dispatch { it::o_PhraseKind }

	override fun phraseKindIsUnder(expectedPhraseKind: PhraseKind) =
		dispatch(expectedPhraseKind) { it::o_PhraseKindIsUnder }

	override val isRawPojo get() = dispatch { it::o_IsRawPojo }

	override fun addSemanticRestriction(restriction: A_SemanticRestriction) =
		dispatch(restriction) { it::o_AddSemanticRestriction }

	override fun removeSemanticRestriction(restriction: A_SemanticRestriction) =
		dispatch(restriction) { it::o_RemoveSemanticRestriction }

	override fun semanticRestrictions() =
		dispatch { it::o_SemanticRestrictions }

	override fun addSealedArgumentsType(typeTuple: A_Tuple) =
		dispatch(typeTuple) { it::o_AddSealedArgumentsType }

	override fun removeSealedArgumentsType(typeTuple: A_Tuple) =
		dispatch(typeTuple) { it::o_RemoveSealedArgumentsType }

	override fun sealedArgumentsTypesTuple() =
		dispatch { it::o_SealedArgumentsTypesTuple }

	override fun moduleAddSemanticRestriction(
		semanticRestriction: A_SemanticRestriction
	) = dispatch(semanticRestriction) { it::o_ModuleAddSemanticRestriction }

	override fun addConstantBinding(
		name: A_String,
		constantBinding: A_Variable
	) = dispatch(name, constantBinding) { it::o_AddConstantBinding }

	override fun addVariableBinding(
		name: A_String,
		variableBinding: A_Variable
	) = dispatch(name, variableBinding) { it::o_AddVariableBinding }

	override fun isMethodEmpty() = dispatch { it::o_IsMethodEmpty }

	override val isPojoSelfType get() = dispatch { it::o_IsPojoSelfType }

	override fun pojoSelfType() = dispatch { it::o_PojoSelfType }

	override fun javaClass() = dispatch { it::o_JavaClass }

	override val isUnsignedShort get() = dispatch { it::o_IsUnsignedShort }

	override fun extractUnsignedShort() =
		dispatch { it::o_ExtractUnsignedShort }

	override val isFloat get() = dispatch { it::o_IsFloat }

	override val isDouble get() = dispatch { it::o_IsDouble }

	override fun rawPojo() = dispatch { it::o_RawPojo }

	override val isPojo get() = dispatch { it::o_IsPojo }

	override val isPojoType get() = dispatch { it::o_IsPojoType }

	override fun numericCompare(another: A_Number) =
		dispatch(another) { it::o_NumericCompare }

	override fun numericCompareToInfinity(sign: Sign) =
		dispatch(sign) { it::o_NumericCompareToInfinity }

	override fun numericCompareToDouble(aDouble: Double) =
		dispatch(aDouble) { it::o_NumericCompareToDouble }

	override fun numericCompareToInteger(anInteger: AvailObject) =
		dispatch(anInteger) { it::o_NumericCompareToInteger }

	override fun addToDoubleCanDestroy(
		doubleObject: A_Number,
		canDestroy: Boolean
	) = dispatch(doubleObject, canDestroy) { it::o_AddToDoubleCanDestroy }

	override fun addToFloatCanDestroy(
		floatObject: A_Number,
		canDestroy: Boolean
	) = dispatch(floatObject, canDestroy) { it::o_AddToFloatCanDestroy }

	override fun subtractFromDoubleCanDestroy(
		doubleObject: A_Number,
		canDestroy: Boolean
	) = dispatch(doubleObject, canDestroy) {
		it::o_SubtractFromDoubleCanDestroy
	}

	override fun subtractFromFloatCanDestroy(
		floatObject: A_Number,
		canDestroy: Boolean
	) = dispatch(floatObject, canDestroy) { it::o_SubtractFromFloatCanDestroy }

	override fun multiplyByDoubleCanDestroy(
		doubleObject: A_Number,
		canDestroy: Boolean
	) = dispatch(doubleObject, canDestroy) {
		it:: o_MultiplyByDoubleCanDestroy
	}

	override fun multiplyByFloatCanDestroy(
		floatObject: A_Number,
		canDestroy: Boolean
	) = dispatch(floatObject, canDestroy) { it::o_MultiplyByFloatCanDestroy }

	override fun divideIntoDoubleCanDestroy(
		doubleObject: A_Number,
		canDestroy: Boolean
	) = dispatch(doubleObject, canDestroy) { it::o_DivideIntoDoubleCanDestroy }

	override fun divideIntoFloatCanDestroy(
		floatObject: A_Number,
		canDestroy: Boolean
	) = dispatch(floatObject, canDestroy) { it::o_DivideIntoFloatCanDestroy }

	override fun serializerOperation() =
		dispatch { it::o_SerializerOperation }

	override fun mapBinAtHashPutLevelCanDestroy(
		key: A_BasicObject,
		keyHash: Int,
		value: A_BasicObject,
		myLevel: Int,
		canDestroy: Boolean
	) = dispatch(key, keyHash, value, myLevel, canDestroy) {
		it::o_MapBinAtHashPutLevelCanDestroy
	}

	override fun mapBinRemoveKeyHashCanDestroy(
		key: A_BasicObject,
		keyHash: Int,
		canDestroy: Boolean
	) = dispatch(key, keyHash, canDestroy) {
		it::o_MapBinRemoveKeyHashCanDestroy
	}

	override fun mapBinAtHashReplacingLevelCanDestroy(
		key: A_BasicObject,
		keyHash: Int,
		notFoundValue: A_BasicObject,
		transformer: (AvailObject, AvailObject) -> A_BasicObject,
		myLevel: Int,
		canDestroy: Boolean
	): A_MapBin = dispatch(key, keyHash, notFoundValue, transformer, myLevel, canDestroy) { it::o_MapBinAtHashReplacingLevelCanDestroy }

	override fun mapBinKeyUnionKind() = dispatch { it::o_MapBinKeyUnionKind }

	override fun mapBinValueUnionKind() =
		dispatch { it::o_MapBinValueUnionKind }

	override fun isHashedMapBin() = dispatch { it::o_IsHashedMapBin }

	/**
	 * Look up the key in this [map&#32;bin][MapBinDescriptor].  If not found,
	 * answer `null`.  Use the provided hash of the key.
	 *
	 * @param key
	 *   The key to look up in this map.
	 * @param keyHash
	 *   The conveniently already computed hash of the key.
	 * @return
	 *   The value under that key in the map, or null if not found.
	 */
	override fun mapBinAtHash(key: A_BasicObject, keyHash: Int): AvailObject? =
		dispatch(key, keyHash) { it::o_MapBinAtHash }

	override fun mapBinKeysHash() = dispatch { it::o_MapBinKeysHash }

	override fun mapBinValuesHash() = dispatch { it::o_MapBinValuesHash }

	override val isPojoFusedType get() = dispatch { it::o_IsPojoFusedType }

	override fun isSupertypeOfPojoBottomType(aPojoType: A_Type) =
		dispatch(aPojoType) { it::o_IsSupertypeOfPojoBottomType }

	override fun equalsPojoBottomType() =
		dispatch { it::o_EqualsPojoBottomType }

	override fun javaAncestors() = dispatch { it::o_JavaAncestors }

	override fun typeIntersectionOfPojoFusedType(aFusedPojoType: A_Type) =
		dispatch(aFusedPojoType) { it::o_TypeIntersectionOfPojoFusedType }

	override fun typeIntersectionOfPojoUnfusedType(anUnfusedPojoType: A_Type) =
		dispatch(anUnfusedPojoType) {it::o_TypeIntersectionOfPojoUnfusedType }

	override fun typeUnionOfPojoFusedType(aFusedPojoType: A_Type) =
		dispatch(aFusedPojoType) { it::o_TypeUnionOfPojoFusedType }

	override fun typeUnionOfPojoUnfusedType(anUnfusedPojoType: A_Type) =
		dispatch(anUnfusedPojoType) { it::o_TypeUnionOfPojoUnfusedType }

	override val isPojoArrayType get() = dispatch { it::o_IsPojoArrayType }

	override fun marshalToJava(classHint: Class<*>?) =
		dispatch(classHint) { it::o_MarshalToJava }

	override fun typeVariables() = dispatch { it::o_TypeVariables }

	override fun equalsPojoField(field: AvailObject, receiver: AvailObject) =
		dispatch(field, receiver) { it::o_EqualsPojoField }

	override val isSignedByte get() = dispatch { it::o_IsSignedByte }

	override val isSignedShort get() = dispatch { it::o_IsSignedShort }

	override fun extractSignedByte() = dispatch { it::o_ExtractSignedByte }

	override fun extractSignedShort() = dispatch { it::o_ExtractSignedShort }

	override fun equalsEqualityRawPojoFor(
		otherEqualityRawPojo: AvailObject,
		otherJavaObject: Any?
	) = dispatch(otherEqualityRawPojo, otherJavaObject) {
		it::o_EqualsEqualityRawPojo
	}

	override fun <T : Any> javaObject() = dispatch<T?> { it::o_JavaObject }

	override fun <T : Any> javaObjectNotNull(): T = javaObject()!!

	override fun asBigInteger() = dispatch { it::o_AsBigInteger }

	override fun lowerCaseString() = dispatch { it::o_LowerCaseString }

	override fun instanceCount() = dispatch { it::o_InstanceCount }

	override fun totalInvocations() = dispatch { it::o_TotalInvocations }

	override fun tallyInvocation() = dispatch { it::o_TallyInvocation }

	override fun fieldTypeTuple() = dispatch { it::o_FieldTypeTuple }

	override fun fieldTuple() = dispatch { it::o_FieldTuple }

	override fun literalType() = dispatch { it::o_LiteralType }

	override fun typeIntersectionOfTokenType(aTokenType: A_Type) =
		dispatch(aTokenType) { it::o_TypeIntersectionOfTokenType }

	override fun typeIntersectionOfLiteralTokenType(aLiteralTokenType: A_Type) =
		dispatch(aLiteralTokenType) { it::o_TypeIntersectionOfLiteralTokenType }

	override fun typeUnionOfTokenType(aTokenType: A_Type) =
		dispatch(aTokenType) { it::o_TypeUnionOfTokenType }

	override fun typeUnionOfLiteralTokenType(aLiteralTokenType: A_Type) =
		dispatch(aLiteralTokenType) { it::o_TypeUnionOfLiteralTokenType }

	override val isTokenType get() = dispatch { it::o_IsTokenType }

	override val isLiteralTokenType
		get() = dispatch { it::o_IsLiteralTokenType }

	override fun isLiteralToken() = dispatch { it::o_IsLiteralToken }

	override fun equalsTokenType(aTokenType: A_Type) =
		dispatch(aTokenType) { it::o_EqualsTokenType }

	override fun equalsLiteralTokenType(aLiteralTokenType: A_Type) =
		dispatch(aLiteralTokenType) { it::o_EqualsLiteralTokenType }

	override fun equalsObjectType(anObjectType: AvailObject) =
		dispatch(anObjectType) { it::o_EqualsObjectType }

	override fun equalsToken(aToken: A_Token) =
		dispatch(aToken) { it::o_EqualsToken }

	override fun bitwiseAnd(anInteger: A_Number, canDestroy: Boolean) =
		dispatch(anInteger, canDestroy) { it::o_BitwiseAnd }

	override fun bitwiseOr(anInteger: A_Number, canDestroy: Boolean) =
		dispatch(anInteger, canDestroy) { it::o_BitwiseOr }

	override fun bitwiseXor(anInteger: A_Number, canDestroy: Boolean) =
		dispatch(anInteger, canDestroy) { it::o_BitwiseXor }

	override fun addSeal(methodName: A_Atom, sealSignature: A_Tuple) =
		dispatch(methodName, sealSignature) { it::o_AddSeal }

	override val isInstanceMeta get() = dispatch { it::o_IsInstanceMeta }

	override fun instance() = dispatch { it::o_Instance }

	override fun setMethodName(methodName: A_String) =
		dispatch(methodName) { it::o_SetMethodName }

	override fun startingLineNumber() = dispatch { it::o_StartingLineNumber }

	override fun module() = dispatch { it::o_Module }

	override fun methodName() = dispatch { it::o_MethodName }

	override fun binElementsAreAllInstancesOfKind(kind: A_Type) =
		dispatch(kind) { it::o_BinElementsAreAllInstancesOfKind }

	override fun setElementsAreAllInstancesOfKind(kind: AvailObject) =
		dispatch(kind) { it::o_SetElementsAreAllInstancesOfKind }

	override fun mapBinIterable() = dispatch { it::o_MapBinIterable }

	override fun rangeIncludesLong(aLong: Long) =
		dispatch(aLong) { it::o_RangeIncludesLong }

	override fun bitShiftLeftTruncatingToBits(
		shiftFactor: A_Number,
		truncationBits: A_Number,
		canDestroy: Boolean
	) = dispatch(shiftFactor, truncationBits, canDestroy) {
		it::o_BitShiftLeftTruncatingToBits
	}

	override fun setBinIterator() = dispatch { it::o_SetBinIterator }

	override fun bitShift(shiftFactor: A_Number, canDestroy: Boolean) =
		dispatch(shiftFactor, canDestroy) { it::o_BitShift }

	override fun equalsPhrase(aPhrase: A_Phrase) =
		dispatch(aPhrase) { it::o_EqualsPhrase }

	/**
	 * Answer the [method][MethodDescriptor] that this
	 * [definition][DefinitionDescriptor] is for.
	 *
	 * @return
	 *   The definition's method.
	 */
	override fun definitionMethod() = dispatch { it::o_DefinitionMethod }

	override fun prefixFunctions() = dispatch { it::o_PrefixFunctions }

	override fun equalsByteArrayTuple(aByteArrayTuple: A_Tuple) =
		dispatch(aByteArrayTuple) { it::o_EqualsByteArrayTuple }

	override val isByteArrayTuple get() = dispatch { it::o_IsByteArrayTuple }

	override fun <T> lock(body: () -> T): T =
		dispatch<T, () -> T>(body) { it::o_Lock }

	/**
	 * Answer the [loader][AvailLoader] bound to the
	 * [receiver][FiberDescriptor], or `null` if the receiver is not a loader
	 * fiber.
	 *
	 * @return
	 *   An Avail loader, or `null` if no Avail loader is associated with the
	 *   specified fiber.
	 */
	override fun availLoader(): AvailLoader? = dispatch { it::o_AvailLoader }

	override fun setAvailLoader(loader: AvailLoader?) =
		dispatch(loader) { it::o_SetAvailLoader }

	override fun moduleName() = dispatch { it::o_ModuleName }

	/**
	 * Answer the continuation that accepts the result produced by the
	 * [receiver][FiberDescriptor]'s successful completion.
	 *
	 * @return
	 *   A continuation.
	 */
	override fun resultContinuation(): (AvailObject) -> Unit =
		dispatch { it::o_ResultContinuation }

	/**
	 * Answer the continuation that accepts the [throwable][Throwable]
	 * responsible for abnormal termination of the [receiver][FiberDescriptor].
	 *
	 * @return
	 *   A continuation.
	 */
	override fun failureContinuation(): (Throwable) -> Unit =
		dispatch { it::o_FailureContinuation }


	override fun setSuccessAndFailureContinuations(
		onSuccess: (AvailObject) -> Unit,
		onFailure: (Throwable) -> Unit
	) = dispatch(onSuccess, onFailure) {
		it::o_SetSuccessAndFailureContinuations
	}

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
		dispatch(flag) { it::o_InterruptRequestFlag }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun getAndSetValue(newValue: A_BasicObject) =
		dispatch(newValue) { it::o_GetAndSetValue }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun compareAndSwapValues(
		reference: A_BasicObject,
		newValue: A_BasicObject
	) = dispatch(reference, newValue) { it::o_CompareAndSwapValues }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun fetchAndAddValue(addend: A_Number) =
		dispatch(addend) { it::o_FetchAndAddValue }

	override fun getAndClearInterruptRequestFlag(flag: InterruptRequestFlag) =
		dispatch(flag) { it::o_GetAndClearInterruptRequestFlag }

	override fun getAndSetSynchronizationFlag(
		flag: SynchronizationFlag,
		value: Boolean
	) = dispatch(flag, value) { it::o_GetAndSetSynchronizationFlag }

	override fun fiberResult() = dispatch { it::o_FiberResult }

	override fun setFiberResult(result: A_BasicObject) =
		dispatch(result) { it::o_SetFiberResult }

	override fun joiningFibers() = dispatch { it::o_JoiningFibers }

	override fun wakeupTask(): TimerTask? = dispatch { it::o_WakeupTask }

	override fun setWakeupTask(task: TimerTask?) =
		dispatch(task) { it::o_SetWakeupTask }

	override fun setJoiningFibers(joiners: A_Set) =
		dispatch(joiners) { it::o_SetJoiningFibers }

	override fun heritableFiberGlobals() =
		dispatch { it::o_HeritableFiberGlobals }

	override fun setHeritableFiberGlobals(globals: A_Map) =
		dispatch(globals) { it::o_SetHeritableFiberGlobals }

	override fun generalFlag(flag: GeneralFlag) =
		dispatch(flag) { it::o_GeneralFlag }

	override fun setGeneralFlag(flag: GeneralFlag) =
		dispatch(flag) { it::o_SetGeneralFlag }

	override fun clearGeneralFlag(flag: GeneralFlag) =
		dispatch(flag) { it::o_ClearGeneralFlag }

	override fun equalsByteBufferTuple(aByteBufferTuple: A_Tuple) =
		dispatch(aByteBufferTuple) { it::o_EqualsByteBufferTuple }

	override val isByteBufferTuple get() =
		dispatch { it::o_IsByteBufferTuple }

	override fun fiberName() = dispatch { it::o_FiberName }

	override fun fiberNameSupplier(supplier: () -> A_String) =
		dispatch(supplier) { it::o_FiberNameSupplier }

	override fun bundles() = dispatch { it::o_Bundles }

	override fun methodAddBundle(bundle: A_Bundle) =
		dispatch(bundle) { it::o_MethodAddBundle }

	override fun methodRemoveBundle(bundle: A_Bundle) =
		dispatch(bundle) { it::o_MethodRemoveBundle }

	override fun definitionBundle(): A_Bundle =
		dispatch { it::o_DefinitionBundle }

	override fun definitionModule() = dispatch { it::o_DefinitionModule }

	override fun definitionModuleName() =
		dispatch { it::o_DefinitionModuleName }

	override fun entryPoints() = dispatch { it::o_EntryPoints }

	override fun addEntryPoint(stringName: A_String, trueName: A_Atom) =
		dispatch(stringName, trueName) { it::o_AddEntryPoint }

	override fun allAncestors() = dispatch { it::o_AllAncestors }

	override fun addAncestors(moreAncestors: A_Set) =
		dispatch(moreAncestors) { it::o_AddAncestors }

	override fun argumentRestrictionSets() =
		dispatch { it::o_ArgumentRestrictionSets }

	override fun restrictedBundle() = dispatch { it::o_RestrictedBundle }

	override fun adjustPcAndStackp(pc: Int, stackp: Int) =
		dispatch(pc, stackp) { it::o_AdjustPcAndStackp }

	override val isByteString get() = dispatch { it::o_IsByteString }

	override val isTwoByteString get() = dispatch { it::o_IsTwoByteString }

	override fun addWriteReactor(key: A_Atom, reactor: VariableAccessReactor) =
		dispatch(key, reactor) { it::o_AddWriteReactor }

	@Throws(AvailException::class)
	override fun removeWriteReactor(key: A_Atom) =
		dispatch(key) { it::o_RemoveWriteReactor }

	override fun traceFlag(flag: TraceFlag) =
		dispatch(flag) { it::o_TraceFlag }

	override fun setTraceFlag(flag: TraceFlag) =
		dispatch(flag) { it::o_SetTraceFlag }

	override fun clearTraceFlag(flag: TraceFlag) =
		dispatch(flag) { it::o_ClearTraceFlag }

	override fun recordVariableAccess(variable: A_Variable, wasRead: Boolean) =
		dispatch(variable, wasRead) { it::o_RecordVariableAccess }

	override fun variablesReadBeforeWritten() =
		dispatch { it::o_VariablesReadBeforeWritten }

	override fun variablesWritten() = dispatch { it::o_VariablesWritten }

	override fun validWriteReactorFunctions() =
		dispatch { it::o_ValidWriteReactorFunctions }

	override fun replacingCaller(newCaller: A_Continuation) =
		dispatch(newCaller) { it::o_ReplacingCaller }

	override fun whenContinuationIsAvailableDo(
		whenReified: (A_Continuation) -> Unit
	) = dispatch(whenReified) { it::o_WhenContinuationIsAvailableDo }

	override fun getAndClearReificationWaiters() =
		dispatch { it::o_GetAndClearReificationWaiters }

	override val isBottom get() = dispatch { it::o_IsBottom }

	override val isVacuousType get() = dispatch { it::o_IsVacuousType }

	override val isTop get() = dispatch { it::o_IsTop }

	override fun addPrivateNames(trueNames: A_Set) =
		dispatch(trueNames) { it::o_AddPrivateNames }

	override fun hasValue() = dispatch { it::o_HasValue }

	override fun addUnloadFunction(unloadFunction: A_Function) =
		dispatch(unloadFunction) { it::o_AddUnloadFunction }

	override fun exportedNames() = dispatch { it::o_ExportedNames }

	override val isInitializedWriteOnceVariable get() =
		dispatch { it::o_IsInitializedWriteOnceVariable }

	override fun isNumericallyIntegral() =
		dispatch { it::o_IsNumericallyIntegral }

	override fun textInterface() = dispatch { it::o_TextInterface }

	override fun setTextInterface(textInterface: TextInterface) =
		dispatch(textInterface) { it::o_SetTextInterface }

	override fun writeTo(writer: JSONWriter) =
		dispatch(writer) { it::o_WriteTo }

	override fun writeSummaryTo(writer: JSONWriter) =
		dispatch(writer) { it::o_WriteSummaryTo }

	override fun typeIntersectionOfPrimitiveTypeEnum(primitiveTypeEnum: Types) =
		dispatch(primitiveTypeEnum) {
			it::o_TypeIntersectionOfPrimitiveTypeEnum
		}

	override fun typeUnionOfPrimitiveTypeEnum(primitiveTypeEnum: Types) =
		dispatch(primitiveTypeEnum) { it::o_TypeUnionOfPrimitiveTypeEnum }

	override fun tupleOfTypesFromTo(startIndex: Int, endIndex: Int) =
		dispatch(startIndex, endIndex) { it::o_TupleOfTypesFromTo }

	override fun macrosTuple() =
		dispatch { it::o_MacrosTuple }

	override fun equalsInt(theInt: Int) = dispatch(theInt) { it::o_EqualsInt }

	override fun chooseBundle(currentModule: A_Module) =
		dispatch(currentModule) { it::o_ChooseBundle }

	override fun valueWasStablyComputed() =
		dispatch { it::o_ValueWasStablyComputed }

	override fun setValueWasStablyComputed(wasStablyComputed: Boolean) =
		dispatch(wasStablyComputed) { it::o_SetValueWasStablyComputed }

	override fun uniqueId() = dispatch { it::o_UniqueId }

	override fun setIntersects(otherSet: A_Set) =
		dispatch(otherSet) { it::o_SetIntersects }

	override fun equalsListNodeType(listNodeType: A_Type) =
		dispatch(listNodeType) { it::o_EqualsListNodeType }

	override fun subexpressionsTupleType() =
		dispatch { it::o_SubexpressionsTupleType }

	override fun parsingSignature() = dispatch { it::o_ParsingSignature }

	override fun moduleSemanticRestrictions() =
		dispatch { it::o_ModuleSemanticRestrictions }

	override fun moduleGrammaticalRestrictions() =
		dispatch { it::o_ModuleGrammaticalRestrictions }

	@ReferencedInGeneratedCode
	override fun fieldAt(field: A_Atom) = dispatch(field) { it::o_FieldAt }

	override fun fieldAtOrNull(field: A_Atom) =
		dispatch(field) { it::o_FieldAtOrNull }

	override fun fieldAtPuttingCanDestroy(
		field: A_Atom,
		value: A_BasicObject,
		canDestroy: Boolean
	) = dispatch(field, value, canDestroy) { it::o_FieldAtPuttingCanDestroy }

	override fun fieldTypeAt(field: A_Atom) =
		dispatch(field) { it::o_FieldTypeAt }

	override fun fieldTypeAtOrNull(field: A_Atom) =
		dispatch(field) { it::o_FieldTypeAtOrNull }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun atomicAddToMap(key: A_BasicObject, value: A_BasicObject) =
		dispatch(key, value) { it::o_AtomicAddToMap }

	@Throws(VariableGetException::class)
	override fun variableMapHasKey(key: A_BasicObject) =
		dispatch(key) { it::o_VariableMapHasKey }

	override fun setLexer(lexer: A_Lexer) = dispatch(lexer) { it::o_SetLexer }

	override fun addLexer(lexer: A_Lexer) = dispatch(lexer) { it::o_AddLexer }

	override fun originatingPhrase() = dispatch { it::o_OriginatingPhrase }

	override fun isGlobal() = dispatch { it::o_IsGlobal }

	override fun globalModule() = dispatch { it::o_GlobalModule }

	override fun globalName() = dispatch { it::o_GlobalName }

	override fun nextLexingState(): LexingState =
		dispatch { it::o_NextLexingState }

	override fun nextLexingStatePojo(): AvailObject =
		dispatch { it::o_NextLexingStatePojo }

	override fun setNextLexingStateFromPrior(priorLexingState: LexingState) =
		dispatch(priorLexingState) { it::o_SetNextLexingStateFromPrior }

	override fun createLexicalScanner() =
		dispatch { it::o_CreateLexicalScanner }

	override fun lexer() = dispatch { it::o_Lexer }

	override fun setSuspendingFunction(suspendingFunction: A_Function) =
		dispatch(suspendingFunction) { it::o_SetSuspendingFunction }

	override fun suspendingFunction() = dispatch { it::o_SuspendingFunction }

	override fun debugLog() = dispatch { it::o_DebugLog }

	override fun constantTypeAt(index: Int) =
		dispatch(index) { it::o_ConstantTypeAt }

	override fun returnerCheckStat() = dispatch { it::o_ReturnerCheckStat }

	override fun returneeCheckStat() = dispatch { it::o_ReturneeCheckStat }

	override fun numNybbles() = dispatch { it::o_NumNybbles }

	override fun lineNumberEncodedDeltas() =
		dispatch { it::o_LineNumberEncodedDeltas }

	override fun currentLineNumber() = dispatch { it::o_CurrentLineNumber }

	override fun fiberResultType() = dispatch { it::o_FiberResultType }

	override fun testingTree(): LookupTree<A_Definition, A_Tuple> =
		dispatch { it::o_TestingTree }

	override fun forEach(action: (AvailObject, AvailObject) -> Unit) =
		dispatch(action) { it::o_ForEach }

	override fun forEachInMapBin(action: (AvailObject, AvailObject) -> Unit) =
		dispatch(action) { it::o_ForEachInMapBin }

	override fun clearLexingState() = dispatch { it::o_ClearLexingState }

	@ReferencedInGeneratedCode
	override fun registerDump() = dispatch { it::o_RegisterDump }

	override fun isOpen() = dispatch { it::o_IsOpen }

	override fun closeModule() = dispatch { it::o_CloseModule }

	override fun membershipChanged() = dispatch { it::o_MembershipChanged }

	override fun moduleAddMacro(macro: A_Macro) =
		dispatch(macro) { it::o_ModuleAddMacro }

	override fun moduleMacros(): A_Set = dispatch { it::o_ModuleMacros }

	override fun addBundle(bundle: A_Bundle) =
		dispatch(bundle) { it::o_AddBundle  }

	override fun moduleBundles() = dispatch { it::o_ModuleBundles }

	override fun returnTypeIfPrimitiveFails(): A_Type =
		dispatch { it::o_ReturnTypeIfPrimitiveFails }

	override fun extractDumpedObjectAt(index: Int): AvailObject =
		dispatch(index) { it::o_ExtractDumpedObjectAt }

	override fun extractDumpedLongAt(index: Int): Long =
		dispatch(index) { it::o_ExtractDumpedLongAt }

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

		/**
		 * Dispatcher helper function for routing 0-argument messages to the
		 * descriptor, inserting the receiver as the first argument.
		 *
		 * @param R
		 *   The result type of this call.
		 * @param f
		 *   A lambda that produces a method reference to invoke with the
		 *   receiver cast to an [AvailObject].
		 */
		inline fun <R> AvailObject.dispatch(
			f: (AbstractDescriptor) -> (AvailObject) -> R): R =
				f(descriptor())(this)

		/**
		 * Dispatcher helper function for routing 1-argument messages to the
		 * descriptor, inserting the receiver as the first argument.
		 *
		 * @param R
		 *   The result type of this call.
		 * @param A1
		 *   The type of the first supplied argument.
		 * @param arg1
		 *   The first supplied argument for the method.
		 * @param f
		 *   A lambda that produces a method reference to invoke with the
		 *   receiver cast to an [AvailObject], followed by the supplied
		 *   arguments.
		 */
		inline fun <R, A1> AvailObject.dispatch(
			arg1: A1,
			f: (AbstractDescriptor) -> (AvailObject, A1) -> R): R =
				f(descriptor())(this, arg1)

		/**
		 * Dispatcher helper function for routing 2-argument messages to the
		 * descriptor, inserting the receiver as the first argument.
		 *
		 * @param R
		 *   The result type of this call.
		 * @param A1
		 *   The type of the first supplied argument.
		 * @param A2
		 *   The type of the second supplied argument.
		 * @param arg1
		 *   The first supplied argument for the method.
		 * @param arg2
		 *   The second supplied argument for the method.
		 * @param f
		 *   A lambda that produces a method reference to invoke with the
		 *   receiver cast to an [AvailObject], followed by the supplied
		 *   arguments.
		 */
		inline fun <R, A1, A2> AvailObject.dispatch(
			arg1: A1,
			arg2: A2,
			f: (AbstractDescriptor) -> (AvailObject, A1, A2) -> R): R =
				f(descriptor())(this, arg1, arg2)

		/**
		 * Dispatcher helper function for routing 3-argument messages to the
		 * descriptor, inserting the receiver as the first argument.
		 *
		 * @param R
		 *   The result type of this call.
		 * @param A1
		 *   The type of the first supplied argument.
		 * @param A2
		 *   The type of the second supplied argument.
		 * @param A3
		 *   The type of the third supplied argument.
		 * @param arg1
		 *   The first supplied argument for the method.
		 * @param arg2
		 *   The second supplied argument for the method.
		 * @param arg3
		 *   The third supplied argument for the method.
		 * @param f
		 *   A lambda that produces a method reference to invoke with the
		 *   receiver cast to an [AvailObject], followed by the supplied
		 *   arguments.
		 */
		inline fun <R, A1, A2, A3> AvailObject.dispatch(
			arg1: A1,
			arg2: A2,
			arg3: A3,
			f: (AbstractDescriptor) -> (AvailObject, A1, A2, A3) -> R): R =
				f(descriptor())(this, arg1, arg2, arg3)

		/**
		 * Dispatcher helper function for routing 4-argument messages to the
		 * descriptor, inserting the receiver as the first argument.
		 *
		 * @param R
		 *   The result type of this call.
		 * @param A1
		 *   The type of the first supplied argument.
		 * @param A2
		 *   The type of the second supplied argument.
		 * @param A3
		 *   The type of the third supplied argument.
		 * @param A4
		 *   The type of the fourth supplied argument.
		 * @param arg1
		 *   The first supplied argument for the method.
		 * @param arg2
		 *   The second supplied argument for the method.
		 * @param arg3
		 *   The third supplied argument for the method.
		 * @param arg4
		 *   The fourth supplied argument for the method.
		 * @param f
		 *   A lambda that produces a method reference to invoke with the
		 *   receiver cast to an [AvailObject], followed by the supplied
		 *   arguments.
		 */
		inline fun <R, A1, A2, A3, A4> AvailObject.dispatch(
			arg1: A1,
			arg2: A2,
			arg3: A3,
			arg4: A4,
			f: (AbstractDescriptor) -> (AvailObject, A1, A2, A3, A4) -> R): R =
				f(descriptor())(this, arg1, arg2, arg3, arg4)

		/**
		 * Dispatcher helper function for routing 5-argument messages to the
		 * descriptor, inserting the receiver as the first argument.
		 *
		 * @param R
		 *   The result type of this call.
		 * @param A1
		 *   The type of the first supplied argument.
		 * @param A2
		 *   The type of the second supplied argument.
		 * @param A3
		 *   The type of the third supplied argument.
		 * @param A4
		 *   The type of the fourth supplied argument.
		 * @param A5
		 *   The type of the fifth supplied argument.
		 * @param arg1
		 *   The first supplied argument for the method.
		 * @param arg2
		 *   The second supplied argument for the method.
		 * @param arg3
		 *   The third supplied argument for the method.
		 * @param arg4
		 *   The fourth supplied argument for the method.
		 * @param arg5
		 *   The fifth supplied argument for the method.
		 * @param f
		 *   A lambda that produces a method reference to invoke with the
		 *   receiver cast to an [AvailObject], followed by the supplied
		 *   arguments.
		 */
		inline fun <R, A1, A2, A3, A4, A5> AvailObject.dispatch(
			arg1: A1,
			arg2: A2,
			arg3: A3,
			arg4: A4,
			arg5: A5,
			f: (AbstractDescriptor) -> (AvailObject, A1, A2, A3, A4, A5) -> R
		): R = f(descriptor())(this, arg1, arg2, arg3, arg4, arg5)

		/**
		 * Dispatcher helper function for routing 6-argument messages to the
		 * descriptor, inserting the receiver as the first argument.
		 *
		 * @param R
		 *   The result type of this call.
		 * @param A1
		 *   The type of the first supplied argument.
		 * @param A2
		 *   The type of the second supplied argument.
		 * @param A3
		 *   The type of the third supplied argument.
		 * @param A4
		 *   The type of the fourth supplied argument.
		 * @param A5
		 *   The type of the fifth supplied argument.
		 * @param A6
		 *   The type of the sixth supplied argument.
		 * @param arg1
		 *   The first supplied argument for the method.
		 * @param arg2
		 *   The second supplied argument for the method.
		 * @param arg3
		 *   The third supplied argument for the method.
		 * @param arg4
		 *   The fourth supplied argument for the method.
		 * @param arg5
		 *   The fifth supplied argument for the method.
		 * @param arg6
		 *   The sixth supplied argument for the method.
		 * @param f
		 *   A lambda that produces a method reference to invoke with the
		 *   receiver cast to an [AvailObject], followed by the supplied
		 *   arguments.
		 */
		inline fun <R, A1, A2, A3, A4, A5, A6> AvailObject.dispatch(
			arg1: A1,
			arg2: A2,
			arg3: A3,
			arg4: A4,
			arg5: A5,
			arg6: A6,
			f: (AbstractDescriptor) ->
				(AvailObject, A1, A2, A3, A4, A5, A6) -> R
		): R = f(descriptor())(this, arg1, arg2, arg3, arg4, arg5, arg6)

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
