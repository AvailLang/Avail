/*
 * AvailObject.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.descriptor

import com.avail.compiler.AvailCodeGenerator
import com.avail.compiler.scanning.LexingState
import com.avail.descriptor.FiberDescriptor.*
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.bundles.MessageBundleDescriptor
import com.avail.descriptor.functions.*
import com.avail.descriptor.functions.CompiledCodeDescriptor.L1InstructionDecoder
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.A_MapBin
import com.avail.descriptor.methods.*
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.AbstractNumberDescriptor
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Sign
import com.avail.descriptor.numbers.InfinityDescriptor
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.parsing.A_DefinitionParsingPlan
import com.avail.descriptor.parsing.A_Lexer
import com.avail.descriptor.parsing.A_ParsingPlanInProgress
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObjectFieldHelper
import com.avail.descriptor.representation.AvailObjectRepresentation
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tokens.TokenDescriptor.TokenType
import com.avail.descriptor.tuples.*
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
import com.avail.exceptions.*
import com.avail.interpreter.AvailLoader
import com.avail.interpreter.Primitive
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.io.TextInterface
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.instanceMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.utility.Casts
import com.avail.utility.IteratorNotNull
import com.avail.utility.Pair
import com.avail.utility.StackPrinter
import com.avail.utility.evaluation.Continuation0
import com.avail.utility.evaluation.Continuation1NotNull
import com.avail.utility.evaluation.Transformer1
import com.avail.utility.json.JSONWriter
import com.avail.utility.visitor.AvailSubobjectVisitor
import com.avail.utility.visitor.MarkUnreachableSubobjectVisitor
import java.nio.ByteBuffer
import java.util.*
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Supplier
import java.util.stream.Stream

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
	A_Map,
	A_MapBin,
	A_Method,
	A_Module,
	A_Number,
	A_ParsingPlanInProgress,
	A_Phrase,
	A_RawFunction,
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
				else -> try {
					recursionMap[this@AvailObject] = null
					descriptor().printObjectOnAvoidingIndent(
						this@AvailObject,
						builder,
						recursionMap,
						indent)
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
		descriptor().o_DescribeForDebugger(this)

	/**
	 * Answer a name suitable for labeling a field containing this object.
	 *
	 * @return An Avail [string][StringDescriptor].
	 */
	override fun nameForDebugger(): String =
		descriptor().o_NameForDebugger(this)

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
	 * Answer whether the receiver is numerically greater than the argument.
	 *
	 * @param another
	 *   A [numeric object][AbstractNumberDescriptor].
	 * @return
	 *   Whether the receiver is strictly greater than the argument.
	 */
	override fun greaterThan(another: A_Number) = numericCompare(another).isMore

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
		numericCompare(another).isMoreOrEqual

	/**
	 * Answer whether the receiver is numerically less than the argument.
	 *
	 * @param another
	 *   A [numeric object][AbstractNumberDescriptor].
	 * @return
	 *   Whether the receiver is strictly less than the argument.
	 */
	override fun lessThan(another: A_Number) = numericCompare(another).isLess

	/**
	 * Answer whether the receiver is numerically less than or equivalent to
	 * the argument.
	 *
	 * @param another
	 *   A [numeric object][AbstractNumberDescriptor].
	 * @return
	 *   Whether the receiver is less than or equivalent to the argument.
	 */
	override fun lessOrEqual(another: A_Number) =
		numericCompare(another).isLessOrEqual

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
	 *   An `int` hash value.
	 */
	override fun hash() = descriptor().o_Hash(this)

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
		descriptor().o_AcceptsArgTypesFromFunctionType(this, functionType)

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
		descriptor().o_AcceptsListOfArgTypes(this, argTypes)

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
		descriptor().o_AcceptsListOfArgValues(this, argValues)

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
		descriptor().o_AcceptsTupleOfArgTypes(this, argTypes)

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
		descriptor().o_AcceptsTupleOfArguments(this, arguments)

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
	) = descriptor().o_ModuleAddGrammaticalRestriction(
		this, grammaticalRestriction)

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
	) = descriptor().o_AddToInfinityCanDestroy(this, sign, canDestroy)

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
	) = descriptor().o_AddToIntegerCanDestroy(this, anInteger, canDestroy)

	/**
	 * Construct a Java [string][String] from the receiver, an Avail
	 * [string][StringDescriptor].
	 *
	 * @return
	 *   The corresponding Java string.
	 */
	override fun asNativeString() = descriptor().o_AsNativeString(this)

	/**
	 * Construct an Avail [set][SetDescriptor] from the receiver, a
	 * [tuple][TupleDescriptor].
	 *
	 * @return
	 *   A set containing each element of the tuple.
	 */
	override fun asSet() = descriptor().o_AsSet(this)

	/**
	 * Construct a [tuple][TupleDescriptor] from the receiver, a
	 * [set][SetDescriptor]. Element ordering in the tuple is arbitrary and
	 * unstable.
	 *
	 * @return
	 *   A tuple containing each element of the set.
	 */
	override fun asTuple() = descriptor().o_AsTuple(this)

	/**
	 * Add a [grammatical&32;restriction][GrammaticalRestrictionDescriptor] to
	 * this [message&#32;bundle][MessageBundleDescriptor].
	 *
	 * @param grammaticalRestriction
	 *   The set of grammatical restrictions to be added.
	 */
	override fun addGrammaticalRestriction(
		grammaticalRestriction: A_GrammaticalRestriction
	) = descriptor().o_AddGrammaticalRestriction(this, grammaticalRestriction)

	/**
 	 * Add a [definition][DefinitionDescriptor] to this
 	 * [module][ModuleDescriptor].
	 *
	 * @param definition
	 *   The definition to add to the module.
	 */
	override fun moduleAddDefinition(definition: A_Definition) =
		descriptor().o_ModuleAddDefinition(this, definition)

	override fun addDefinitionParsingPlan(plan: A_DefinitionParsingPlan) =
		descriptor().o_AddDefinitionParsingPlan(this, plan)

	override fun addImportedName(trueName: A_Atom) =
		descriptor().o_AddImportedName(this, trueName)

	override fun addImportedNames(trueNames: A_Set) =
		descriptor().o_AddImportedNames(this, trueNames)

	override fun introduceNewName(trueName: A_Atom) =
		descriptor().o_IntroduceNewName(this,trueName)

	override fun addPrivateName(trueName: A_Atom) =
		descriptor().o_AddPrivateName(this, trueName)

	override fun setBinAddingElementHashLevelCanDestroy(
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Byte,
		canDestroy: Boolean
	): A_BasicObject =
		descriptor().o_SetBinAddingElementHashLevelCanDestroy(
			this,
			elementObject,
			elementObjectHash,
			myLevel,
			canDestroy)

	override fun setBinSize() = descriptor().o_SetBinSize(this)

	override fun binElementAt(index: Int) =
		descriptor().o_BinElementAt(this, index)

	override fun binHasElementWithHash(
		elementObject: A_BasicObject,
		elementObjectHash: Int
	): Boolean = descriptor().o_BinHasElementWithHash(
		this, elementObject, elementObjectHash)

	override fun setBinHash() = descriptor().o_SetBinHash(this)

	override fun binRemoveElementHashLevelCanDestroy(
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Byte,
		canDestroy: Boolean
	) = descriptor().o_BinRemoveElementHashLevelCanDestroy(
		this, elementObject, elementObjectHash, myLevel, canDestroy)

	override fun bitsPerEntry() = descriptor().o_BitsPerEntry(this)

	override fun bodyBlock() = descriptor().o_BodyBlock(this)

	override fun bodySignature() = descriptor().o_BodySignature(this)

	override fun breakpointBlock() = descriptor().o_BreakpointBlock(this)

	override fun breakpointBlock(value: AvailObject) =
		descriptor().o_BreakpointBlock(this, value)

	override fun buildFilteredBundleTree() =
		descriptor().o_BuildFilteredBundleTree(this)

	override fun caller() = descriptor().o_Caller(this)

	override fun clearValue() = descriptor().o_ClearValue(this)

	override fun function() = descriptor().o_Function(this)

	override fun functionType() = descriptor().o_FunctionType(this)

	override fun code() = descriptor().o_Code(this)

	override fun codePoint() = descriptor().o_CodePoint(this)

	/**
	 * Compare a subrange of the receiver with a subrange of another
	 * [tuple][TupleDescriptor]. The size of the subrange of both objects is
	 * determined by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param anotherObject
	 *   The other object used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the other object's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 */
	override fun compareFromToWithStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithStartingAt(
		this, startIndex1, endIndex1, anotherObject, startIndex2)

	/**
	 * Compare a subrange of the receiver with a subrange of the given
	 * [tuple][TupleDescriptor]. The size of the subrange of both objects is
	 * determined by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aTuple
	 *   The tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 */
	override fun compareFromToWithAnyTupleStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		aTuple: A_Tuple,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithAnyTupleStartingAt(
		this, startIndex1, endIndex1, aTuple, startIndex2)

	/**
	 * Compare a subrange of the receiver with a subrange of the given
	 * [byte&#32;string][ByteStringDescriptor]. The size of the subrange of both
	 * objects is determined by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aByteString
	 *   The byte string used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the byte string's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 */
	override fun compareFromToWithByteStringStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		aByteString: A_String,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithByteStringStartingAt(
		this, startIndex1, endIndex1, aByteString, startIndex2)

	/**
	 * Compare a subrange of the receiver with a subrange of the given
	 * [byte&#32;tuple][ByteTupleDescriptor]. The size of the subrange of both
	 * objects is determined by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aByteTuple
	 *   The byte tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the byte tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 */
	override fun compareFromToWithByteTupleStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		aByteTuple: A_Tuple,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithByteTupleStartingAt(
		this, startIndex1, endIndex1, aByteTuple, startIndex2)

	/**
	 * Compare a subrange of the receiver with a subrange of the given
	 * [integer&#32;interval&#32;tuple][IntegerIntervalTupleDescriptor]. The
	 * size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 * The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 * The inclusive upper bound of the receiver's subrange.
	 * @param anIntegerIntervalTuple
	 * The integer interval tuple used in the comparison.
	 * @param startIndex2
	 * The inclusive lower bound of the byte tuple's subrange.
	 * @return `true` if the contents of the subranges match exactly,
	 * `false` otherwise.
	 */
	override fun compareFromToWithIntegerIntervalTupleStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		anIntegerIntervalTuple: A_Tuple,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithIntegerIntervalTupleStartingAt(
		this, startIndex1, endIndex1, anIntegerIntervalTuple, startIndex2)

	/**
	 * Compare a subrange of the receiver with a subrange of the given
	 * [int&#32;tuple][IntTupleDescriptor]. The size of the subrange of both
	 * objects is determined by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param anIntTuple
	 *   The int tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the int tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 */
	override fun compareFromToWithIntTupleStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		anIntTuple: A_Tuple,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithIntTupleStartingAt(
		this, startIndex1, endIndex1, anIntTuple, startIndex2)

	/**
	 * Compare a subrange of the receiver with a subrange of the given small
	 * integer [interval&#32;tuple][SmallIntegerIntervalTupleDescriptor]. The
	 * size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aSmallIntegerIntervalTuple
	 *   The small integer interval tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the byte tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 */
	override fun compareFromToWithSmallIntegerIntervalTupleStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		aSmallIntegerIntervalTuple: A_Tuple,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithSmallIntegerIntervalTupleStartingAt(
		this, startIndex1, endIndex1, aSmallIntegerIntervalTuple, startIndex2)

	/**
	 * Compare a subrange of the receiver with a subrange of the given
	 * [repeated&#32;element&#32;tuple][RepeatedElementTupleDescriptor]. The
	 * size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aRepeatedElementTuple
	 *   The repeated element tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the repeated element tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 */
	override fun compareFromToWithRepeatedElementTupleStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		aRepeatedElementTuple: A_Tuple,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithRepeatedElementTupleStartingAt(
		this, startIndex1, endIndex1, aRepeatedElementTuple, startIndex2)

	/**
	 * Compare a subrange of the receiver with a subrange of the given
	 * [nybble&#32;tuple][NybbleTupleDescriptor]. The size of the subrange of
	 * both objects is determined by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aNybbleTuple
	 *   The nybble tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the nybble tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 */
	override fun compareFromToWithNybbleTupleStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		aNybbleTuple: A_Tuple,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithNybbleTupleStartingAt(
		this, startIndex1, endIndex1, aNybbleTuple, startIndex2)

	/**
	 * Compare a subrange of the receiver with a subrange of the given
	 * [object&#32;tuple][ObjectTupleDescriptor]. The size of the subrange of
	 * both objects is determined by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param anObjectTuple
	 *   The object tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the object tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 */
	override fun compareFromToWithObjectTupleStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		anObjectTuple: A_Tuple,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithObjectTupleStartingAt(
		this, startIndex1, endIndex1, anObjectTuple, startIndex2)

	/**
	 * Compare a subrange of the receiver with a subrange of the given
	 * [two-byte&#32;string][TwoByteStringDescriptor]. The size of the subrange
	 * of both objects is determined by the index range supplied for the
	 * receiver.
	 *
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aTwoByteString
	 *   The two-byte string used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the two-byte string's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 */
	override fun compareFromToWithTwoByteStringStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		aTwoByteString: A_String,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithTwoByteStringStartingAt(
		this, startIndex1, endIndex1, aTwoByteString, startIndex2)

	override fun lazyComplete() = descriptor().o_LazyComplete(this)

	override fun computeHashFromTo(start: Int, end: Int) =
		descriptor().o_ComputeHashFromTo(this, start, end)

	override fun concatenateTuplesCanDestroy(canDestroy: Boolean) =
		descriptor().o_ConcatenateTuplesCanDestroy(this, canDestroy)

	override fun constantBindings() = descriptor().o_ConstantBindings(this)

	override fun contentType() = descriptor().o_ContentType(this)

	override fun continuation() = descriptor().o_Continuation(this)

	override fun continuation(value: A_Continuation) =
		descriptor().o_Continuation(this, value)

	override fun copyAsMutableIntTuple() =
		descriptor().o_CopyAsMutableIntTuple(this)

	override fun copyAsMutableObjectTuple() =
		descriptor().o_CopyAsMutableObjectTuple(this)

	override fun copyTupleFromToCanDestroy(
		start: Int, end: Int, canDestroy: Boolean
	) = descriptor().o_CopyTupleFromToCanDestroy(this, start, end, canDestroy)

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
	): A_String = Casts.cast(
		descriptor().o_CopyTupleFromToCanDestroy(this, start, end, canDestroy))

	override fun couldEverBeInvokedWith(
		argRestrictions: List<TypeRestriction>
	) = descriptor().o_CouldEverBeInvokedWith(this, argRestrictions)

	override fun defaultType() = descriptor().o_DefaultType(this)

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
		descriptor().o_DivideCanDestroy(this, aNumber, canDestroy)

	/**
	 * Divide the receiver by the argument `aNumber` and answer the result. The
	 * operation is not allowed to fail, so the caller must ensure that the
	 * arguments are valid, i.e. the divisor is not
	 * [zero][IntegerDescriptor.zero].
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either [        ], `false`
	 *   otherwise.
	 * @return
	 *   The `AvailObject result` of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	override fun noFailDivideCanDestroy(
		aNumber: A_Number, canDestroy: Boolean
	) = try {
		descriptor().o_DivideCanDestroy(this, aNumber, canDestroy)
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
		descriptor().o_DivideIntoInfinityCanDestroy(this, sign, canDestroy)

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
	) = descriptor().o_DivideIntoIntegerCanDestroy(this, anInteger, canDestroy)

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
	) = descriptor().o_EqualsSmallIntegerIntervalTuple(
		this, aSmallIntegerIntervalTuple)

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
		descriptor().o_EqualsRepeatedElementTuple(this,aRepeatedElementTuple)

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
		descriptor().o_EqualsCharacterWithCodePoint(this, aCodePoint)

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
	 *   * The [argument&#32;types][argsTupleType] correspond,
	 *   * The [return&#32;types][returnType] correspond, and
	 *   * The [declared&#32;exceptions][declaredExceptions] correspond.
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
	override fun equalsVariable(aVariable: AvailObject) =
		descriptor().o_EqualsVariable(this, aVariable)

	override fun equalsVariableType(aVariableType: A_Type) =
		descriptor().o_EqualsVariableType(this, aVariableType)

	override fun equalsContinuation(aContinuation: A_Continuation) =
		descriptor().o_EqualsContinuation(this, aContinuation)

	override fun equalsContinuationType(aContinuationType: A_Type) =
		descriptor().o_EqualsContinuationType(this, aContinuationType)

	override fun equalsDouble(aDouble: Double) =
		descriptor().o_EqualsDouble(this, aDouble)

	override fun equalsFloat(aFloat: Float) =
		descriptor().o_EqualsFloat(this, aFloat)

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
		descriptor().o_EqualsInfinity(this, sign)

	override fun equalsInteger(anAvailInteger: AvailObject) =
		descriptor().o_EqualsInteger(this, anAvailInteger)

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
		otherRawPojo: AvailObject, otherJavaObject: Any?
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

	override fun equalsSet(aSet: A_Set) = descriptor().o_EqualsSet(this, aSet)

	override fun equalsSetType(aSetType: A_Type) =
		descriptor().o_EqualsSetType(this, aSetType)

	override fun equalsTupleType(aTupleType: A_Type) =
		descriptor().o_EqualsTupleType(this, aTupleType)

	override fun equalsTwoByteString(aTwoByteString: A_String) =
		descriptor().o_EqualsTwoByteString(this, aTwoByteString)

	/** Note: [nil] is never the target of an indirection. */
	override fun equalsNil() = this === nil

	override fun executionState() = descriptor().o_ExecutionState(this)

	override fun executionState(value: ExecutionState) =
		descriptor().o_ExecutionState(this, value)

	override fun expand(module: A_Module) = descriptor().o_Expand(this, module)

	override fun extractBoolean() = descriptor().o_ExtractBoolean(this)

	override fun extractUnsignedByte() =
		descriptor().o_ExtractUnsignedByte(this)

	override fun extractDouble() = descriptor().o_ExtractDouble(this)

	override fun extractFloat() = descriptor().o_ExtractFloat(this)

	override fun extractInt() = descriptor().o_ExtractInt(this)

	/**
	 * Extract a 64-bit signed Kotlin [Long] from the receiver
	 *
	 * @return A 64-bit signed Kotlin [Long].
	 */
	override fun extractLong() = descriptor().o_ExtractLong(this)

	override fun extractNybble() = descriptor().o_ExtractNybble(this)

	override fun extractNybbleFromTupleAt(index: Int) =
		descriptor().o_ExtractNybbleFromTupleAt(this, index)

	override fun fieldMap() = descriptor().o_FieldMap(this)

	override fun fieldTypeMap() = descriptor().o_FieldTypeMap(this)

	override fun filterByTypes(argTypes: List<A_Type>) =
		descriptor().o_FilterByTypes(this, argTypes)

	@Throws(VariableGetException::class)
	override fun getValue() = descriptor().o_GetValue(this)

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
		descriptor().o_HasElement(this, elementObject)

	override fun hashFromTo(startIndex: Int, endIndex: Int) =
		descriptor().o_HashFromTo(this, startIndex, endIndex)

	override fun hashOrZero() = descriptor().o_HashOrZero(this)

	override fun hashOrZero(value: Int) = descriptor().o_HashOrZero(this, value)

	override fun hasKey(keyObject: A_BasicObject) =
		descriptor().o_HasKey(this, keyObject)

	override fun hasObjectInstance(potentialInstance: AvailObject) =
		descriptor().o_HasObjectInstance(this, potentialInstance)

	override fun hasGrammaticalRestrictions() =
		descriptor().o_HasGrammaticalRestrictions(this)

	override fun definitionsAtOrBelow(argRestrictions: List<TypeRestriction>) =
		descriptor().o_DefinitionsAtOrBelow(this, argRestrictions)

	override fun definitionsTuple() = descriptor().o_DefinitionsTuple(this)

	override fun includesDefinition(imp: A_Definition) =
		descriptor().o_IncludesDefinition(this, imp)

	override fun lazyIncomplete() = descriptor().o_LazyIncomplete(this)

	override fun setInterruptRequestFlag(flag: InterruptRequestFlag) =
		descriptor().o_SetInterruptRequestFlag(this, flag)

	override fun decrementCountdownToReoptimize(
		continuation: Continuation1NotNull<Boolean>
	) = descriptor().o_DecrementCountdownToReoptimize(this, continuation)

	override fun countdownToReoptimize(value: Int) =
		descriptor().o_CountdownToReoptimize(this, value)

	override val isAbstract get() = descriptor().o_IsAbstract(this)

	override fun isAbstractDefinition() =
		descriptor().o_IsAbstractDefinition(this)

	override fun isBetterRepresentationThan(anotherObject: A_BasicObject) =
		descriptor().o_IsBetterRepresentationThan(this, anotherObject)

	override fun representationCostOfTupleType() =
		descriptor().o_RepresentationCostOfTupleType(this)

	override fun isBinSubsetOf(potentialSuperset: A_Set) =
		descriptor().o_IsBinSubsetOf(this, potentialSuperset)

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
	 * Is the receiver an Avail character?
	 *
	 * @return
	 *   `true` if the receiver is a character, `false` otherwise.
	 */
	override val isCharacter get() = descriptor().o_IsCharacter(this)

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

	override fun isPositive() = descriptor().o_IsPositive(this)

	/**
	 * Is the receiver an Avail set?
	 *
	 * @return
	 *   `true` if the receiver is a set, `false` otherwise.
	 */
	override val isSet get() = descriptor().o_IsSet(this)

	override val isSetType get() = descriptor().o_IsSetType(this)

	override fun isSubsetOf(another: A_Set) =
		descriptor().o_IsSubsetOf(this, another)

	/**
	 * Is the receiver an Avail string?
	 *
	 * @return
	 *   `true` if the receiver is an Avail string, `false` otherwise.
	 */
	override val isString get() = descriptor().o_IsString(this)

	override fun isSubtypeOf(aType: A_Type) =
		descriptor().o_IsSubtypeOf(this, aType)

	override fun isSupertypeOfVariableType(aVariableType: A_Type) =
		descriptor().o_IsSupertypeOfVariableType(this, aVariableType)

	override fun isSupertypeOfContinuationType(aContinuationType: A_Type) =
		descriptor().o_IsSupertypeOfContinuationType(this, aContinuationType)

	override fun isSupertypeOfFiberType(aFiberType: A_Type) =
		descriptor().o_IsSupertypeOfFiberType(this, aFiberType)

	override fun isSupertypeOfFunctionType(aFunctionType: A_Type) =
		descriptor().o_IsSupertypeOfFunctionType(this, aFunctionType)

	override fun isSupertypeOfIntegerRangeType(anIntegerRangeType: A_Type) =
		descriptor().o_IsSupertypeOfIntegerRangeType(this, anIntegerRangeType)

	override fun isSupertypeOfListNodeType(aListNodeType: A_Type) =
		descriptor().o_IsSupertypeOfListNodeType(this, aListNodeType)

	override fun isSupertypeOfTokenType(aTokenType: A_Type) =
		descriptor().o_IsSupertypeOfTokenType(this, aTokenType)

	override fun isSupertypeOfLiteralTokenType(aLiteralTokenType: A_Type) =
		descriptor().o_IsSupertypeOfLiteralTokenType(this, aLiteralTokenType)

	override fun isSupertypeOfMapType(aMapType: AvailObject) =
		descriptor().o_IsSupertypeOfMapType(this, aMapType)

	override fun isSupertypeOfObjectType(anObjectType: AvailObject) =
		descriptor().o_IsSupertypeOfObjectType(this, anObjectType)

	override fun isSupertypeOfPhraseType(aPhraseType: A_Type) =
		descriptor().o_IsSupertypeOfPhraseType(this, aPhraseType)

	override fun isSupertypeOfPojoType(aPojoType: A_Type) =
		descriptor().o_IsSupertypeOfPojoType(this, aPojoType)

	override fun isSupertypeOfPrimitiveTypeEnum(primitiveTypeEnum: Types) =
		descriptor().o_IsSupertypeOfPrimitiveTypeEnum(this, primitiveTypeEnum)

	override fun isSupertypeOfSetType(aSetType: AvailObject) =
		descriptor().o_IsSupertypeOfSetType(this, aSetType)

	override fun isSupertypeOfBottom() =
		descriptor().o_IsSupertypeOfBottom(this)

	override fun isSupertypeOfTupleType(aTupleType: AvailObject) =
		descriptor().o_IsSupertypeOfTupleType(this, aTupleType)

	override fun isSupertypeOfEnumerationType(
		anEnumerationType: A_BasicObject
	) = descriptor().o_IsSupertypeOfEnumerationType(this, anEnumerationType)

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
	override fun iterator(): IteratorNotNull<AvailObject> =
		descriptor().o_Iterator(this)

	override fun spliterator(): Spliterator<AvailObject> =
		descriptor().o_Spliterator(this)

	override fun stream(): Stream<AvailObject> =
		descriptor().o_Stream(this)

	override fun parallelStream(): Stream<AvailObject> =
		descriptor().o_ParallelStream(this)

	override fun keysAsSet() = descriptor().o_KeysAsSet(this)

	override fun keyType() = descriptor().o_KeyType(this)

	override fun levelTwoChunk() = descriptor().o_LevelTwoChunk(this)

	override fun levelTwoChunkOffset(chunk: L2Chunk, offset: Int) =
		descriptor().o_LevelTwoChunkOffset(this, chunk, offset)

	override fun levelTwoOffset() = descriptor().o_LevelTwoOffset(this)

	override fun literal() = descriptor().o_Literal(this)

	override fun literalAt(index: Int) = descriptor().o_LiteralAt(this, index)

	@ReferencedInGeneratedCode
	override fun argOrLocalOrStackAt(index: Int) =
		descriptor().o_ArgOrLocalOrStackAt(this, index)

	@ReferencedInGeneratedCode
	override fun argOrLocalOrStackAtPut(index: Int, value: AvailObject) =
		descriptor().o_ArgOrLocalOrStackAtPut(this, index, value)

	override fun localTypeAt(index: Int) =
		descriptor().o_LocalTypeAt(this, index)

	@Throws(MethodDefinitionException::class)
	override fun lookupByTypesFromTuple(argumentTypeTuple: A_Tuple) =
		descriptor().o_LookupByTypesFromTuple(this, argumentTypeTuple)

	@Throws(MethodDefinitionException::class)
	override fun lookupByValuesFromList(argumentList: List<A_BasicObject>) =
		descriptor().o_LookupByValuesFromList(this, argumentList)

	override fun lowerBound() = descriptor().o_LowerBound(this)

	override fun lowerInclusive() = descriptor().o_LowerInclusive(this)

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

	override fun makeSubobjectsShared() {
		descriptor().o_MakeSubobjectsShared(this)
	}

	override fun mapAt(keyObject: A_BasicObject) =
		descriptor().o_MapAt(this, keyObject)

	override fun mapAtPuttingCanDestroy(
		keyObject: A_BasicObject,
		newValueObject: A_BasicObject,
		canDestroy: Boolean
	) = descriptor().o_MapAtPuttingCanDestroy(
		this, keyObject, newValueObject, canDestroy)

	override fun mapAtReplacingCanDestroy(
		key: A_BasicObject,
		notFoundValue: A_BasicObject,
		transformer: BinaryOperator<A_BasicObject>,
		canDestroy: Boolean
	): A_Map = descriptor().o_MapAtReplacingCanDestroy(
		this, key, notFoundValue, transformer, canDestroy)

	override fun mapBinSize() = descriptor().o_MapBinSize(this)

	override fun mapSize() = descriptor().o_MapSize(this)

	override fun mapWithoutKeyCanDestroy(
		keyObject: A_BasicObject,
		canDestroy: Boolean
	) = descriptor().o_MapWithoutKeyCanDestroy(this, keyObject, canDestroy)

	override fun maxStackDepth() = descriptor().o_MaxStackDepth(this)

	override fun message() = descriptor().o_Message(this)

	override fun messageParts() = descriptor().o_MessageParts(this)

	override fun methodDefinitions() = descriptor().o_MethodDefinitions(this)

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
		descriptor().o_MinusCanDestroy(this, aNumber, canDestroy)

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
			descriptor().o_MinusCanDestroy(this, aNumber, canDestroy)
		} catch (e: ArithmeticException) {
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			error("noFailMinusCanDestroy failed!")
		}

	/**
	 * Multiply the receiver and the argument [anInfinity] and answer the
	 * result.
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
		descriptor().o_MultiplyByInfinityCanDestroy(this, sign, canDestroy)

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
	) = descriptor().o_MultiplyByIntegerCanDestroy(this, anInteger, canDestroy)

	override fun atomName() = descriptor().o_AtomName(this)

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

	override fun parent() = descriptor().o_Parent(this)

	override fun pc() = descriptor().o_Pc(this)

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
		descriptor().o_PlusCanDestroy(this, aNumber, canDestroy)

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
			descriptor().o_PlusCanDestroy(this, aNumber, canDestroy)
		} catch (e: ArithmeticException) {
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			error("noFailPlusCanDestroy failed!")
		}

	override fun primitiveNumber() = descriptor().o_PrimitiveNumber(this)

	override fun priority() = descriptor().o_Priority(this)

	override fun priority(value: Int) = descriptor().o_Priority(this, value)

	override fun privateNames() = descriptor().o_PrivateNames(this)

	override fun fiberGlobals() = descriptor().o_FiberGlobals(this)

	override fun fiberGlobals(value: A_Map) =
		descriptor().o_FiberGlobals(this, value)

	override fun rawByteForCharacterAt(index: Int) =
		descriptor().o_RawByteForCharacterAt(this, index)

	override fun rawShortForCharacterAt(index: Int) =
		descriptor().o_RawShortForCharacterAt(this, index)

	override fun rawShortForCharacterAtPut(index: Int, anInteger: Int) =
		descriptor().o_RawShortForCharacterAtPut(this, index, anInteger)

	override fun rawSignedIntegerAt(index: Int) =
		descriptor().o_RawSignedIntegerAt(this, index)

	override fun rawSignedIntegerAtPut(index: Int, value: Int) =
		descriptor().o_RawSignedIntegerAtPut(this, index, value)

	override fun rawUnsignedIntegerAt(index: Int) =
		descriptor().o_RawUnsignedIntegerAt(this, index)

	override fun rawUnsignedIntegerAtPut(index: Int, value: Int) =
		descriptor().o_RawUnsignedIntegerAtPut(this, index, value)

	override fun removeDependentChunk(chunk: L2Chunk) =
		descriptor().o_RemoveDependentChunk(this, chunk)

	override fun removeFrom(loader: AvailLoader, afterRemoval: Continuation0) =
		descriptor().o_RemoveFrom(this, loader, afterRemoval)

	override fun removeDefinition(definition: A_Definition) =
		descriptor().o_RemoveDefinition(this, definition)

	override fun removeGrammaticalRestriction(
		obsoleteRestriction: A_GrammaticalRestriction
	) = descriptor().o_RemoveGrammaticalRestriction(this, obsoleteRestriction)

	override fun resolveForward(forwardDefinition: A_BasicObject) =
		descriptor().o_ResolveForward(this, forwardDefinition)

	override fun grammaticalRestrictions() =
		descriptor().o_GrammaticalRestrictions(this)

	override fun returnType() = descriptor().o_ReturnType(this)

	override fun scanSubobjects(visitor: AvailSubobjectVisitor) =
		descriptor().o_ScanSubobjects(this, visitor)

	override fun setIntersectionCanDestroy(
		otherSet: A_Set,
		canDestroy: Boolean
	) = descriptor().o_SetIntersectionCanDestroy(this, otherSet, canDestroy)

	override fun setMinusCanDestroy(otherSet: A_Set, canDestroy: Boolean) =
		descriptor().o_SetMinusCanDestroy(this, otherSet, canDestroy)

	override fun setSize() = descriptor().o_SetSize(this)

	override fun setUnionCanDestroy(otherSet: A_Set, canDestroy: Boolean) =
		descriptor().o_SetUnionCanDestroy(this, otherSet, canDestroy)

	@Throws(VariableSetException::class)
	override fun setValue(newValue: A_BasicObject) =
		descriptor().o_SetValue(this, newValue)

	override fun setValueNoCheck(newValue: A_BasicObject) =
		descriptor().o_SetValueNoCheck(this, newValue)

	override fun setWithElementCanDestroy(
		newElementObject: A_BasicObject,
		canDestroy: Boolean
	) = descriptor().o_SetWithElementCanDestroy(
		this, newElementObject, canDestroy)

	override fun setWithoutElementCanDestroy(
		elementObjectToExclude: A_BasicObject,
		canDestroy: Boolean
	) = descriptor().o_SetWithoutElementCanDestroy(
		this, elementObjectToExclude, canDestroy)

	override fun sizeRange() = descriptor().o_SizeRange(this)

	override fun lazyActions() = descriptor().o_LazyActions(this)

	override fun stackAt(slotIndex: Int) =
		descriptor().o_StackAt(this, slotIndex)

	override fun stackp() = descriptor().o_Stackp(this)

	override fun start() = descriptor().o_Start(this)

	override fun startingChunk() = descriptor().o_StartingChunk(this)

	override fun setStartingChunkAndReoptimizationCountdown(
		chunk: L2Chunk,
		countdown: Long
	) =
		descriptor().o_SetStartingChunkAndReoptimizationCountdown(
			this, chunk, countdown)

	override fun string() = descriptor().o_String(this)

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
	) = descriptor().o_SubtractFromInfinityCanDestroy(this, sign, canDestroy)

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
	) = descriptor().o_SubtractFromIntegerCanDestroy(
		this, anInteger, canDestroy)

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
		descriptor().o_TimesCanDestroy(this, aNumber, canDestroy)

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
	) = try {
			descriptor().o_TimesCanDestroy(this, aNumber, canDestroy)
		} catch (e: ArithmeticException) {
			// This had better not happen, otherwise the caller has violated the
			// intention of this method.
			error("noFailTimesCanDestroy failed!")
		}

	override fun tokenType(): TokenType = descriptor().o_TokenType(this)

	override fun traversed() = descriptor().o_Traversed(this)

	override fun trimExcessInts() = descriptor().o_TrimExcessInts(this)

	override fun trueNamesForStringName(stringName: A_String) =
		descriptor().o_TrueNamesForStringName(this, stringName)

	override fun tupleAt(index: Int) = descriptor().o_TupleAt(this, index)

	override fun tupleAtPuttingCanDestroy(
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean
	) = descriptor().o_TupleAtPuttingCanDestroy(
		this, index, newValueObject, canDestroy)

	override fun tupleIntAt(index: Int) = descriptor().o_TupleIntAt(this, index)

	override fun tupleReverse() = descriptor().o_TupleReverse(this)

	override fun tupleSize() = descriptor().o_TupleSize(this)

	override fun kind() = descriptor().o_Kind(this)

	override fun typeAtIndex(index: Int) =
		descriptor().o_TypeAtIndex(this, index)

	override fun typeIntersection(another: A_Type) =
		descriptor().o_TypeIntersection(this, another)

	override fun typeIntersectionOfCompiledCodeType(aCompiledCodeType: A_Type) =
		descriptor().o_TypeIntersectionOfCompiledCodeType(
			this, aCompiledCodeType)

	override fun typeIntersectionOfContinuationType(aContinuationType: A_Type) =
		descriptor().o_TypeIntersectionOfContinuationType(
			this, aContinuationType)

	override fun typeIntersectionOfFiberType(aFiberType: A_Type) =
		descriptor().o_TypeIntersectionOfFiberType(this, aFiberType)

	override fun typeIntersectionOfFunctionType(aFunctionType: A_Type) =
		descriptor().o_TypeIntersectionOfFunctionType(this, aFunctionType)

	override fun typeIntersectionOfIntegerRangeType(
		anIntegerRangeType: A_Type
	) = descriptor().o_TypeIntersectionOfIntegerRangeType(
		this, anIntegerRangeType)

	override fun typeIntersectionOfListNodeType(aListNodeType: A_Type) =
		descriptor().o_TypeIntersectionOfListNodeType(this, aListNodeType)

	override fun typeIntersectionOfMapType(aMapType: A_Type) =
		descriptor().o_TypeIntersectionOfMapType(this, aMapType)

	override fun typeIntersectionOfObjectType(anObjectType: AvailObject) =
		descriptor().o_TypeIntersectionOfObjectType(this, anObjectType)

	override fun typeIntersectionOfPhraseType(aPhraseType: A_Type) =
		descriptor().o_TypeIntersectionOfPhraseType(this, aPhraseType)

	override fun typeIntersectionOfPojoType(aPojoType: A_Type) =
		descriptor().o_TypeIntersectionOfPojoType(this, aPojoType)

	override fun typeIntersectionOfSetType(aSetType: A_Type) =
		descriptor().o_TypeIntersectionOfSetType(this, aSetType)

	override fun typeIntersectionOfTupleType(aTupleType: A_Type) =
		descriptor().o_TypeIntersectionOfTupleType(this, aTupleType)

	override fun typeIntersectionOfVariableType(aVariableType: A_Type) =
		descriptor().o_TypeIntersectionOfVariableType(this, aVariableType)

	override fun typeTuple() = descriptor().o_TypeTuple(this)

	override fun typeUnion(another: A_Type) =
		descriptor().o_TypeUnion(this, another)

	override fun typeUnionOfFiberType(aFiberType: A_Type) =
		descriptor().o_TypeUnionOfFiberType(this, aFiberType)

	override fun typeUnionOfFunctionType(aFunctionType: A_Type) =
		descriptor().o_TypeUnionOfFunctionType(this, aFunctionType)

	override fun typeUnionOfVariableType(aVariableType: A_Type) =
		descriptor().o_TypeUnionOfVariableType(this, aVariableType)

	override fun typeUnionOfContinuationType(aContinuationType: A_Type) =
		descriptor().o_TypeUnionOfContinuationType(this, aContinuationType)

	override fun typeUnionOfIntegerRangeType(anIntegerRangeType: A_Type) =
		descriptor().o_TypeUnionOfIntegerRangeType(this, anIntegerRangeType)

	override fun typeUnionOfListNodeType(aListNodeType: A_Type) =
		descriptor().o_TypeUnionOfListNodeType(this, aListNodeType)

	override fun typeUnionOfMapType(aMapType: A_Type) =
		descriptor().o_TypeUnionOfMapType(this, aMapType)

	override fun typeUnionOfObjectType(anObjectType: AvailObject) =
		descriptor().o_TypeUnionOfObjectType(this, anObjectType)

	override fun typeUnionOfPhraseType(aPhraseType: A_Type) =
		descriptor().o_TypeUnionOfPhraseType(this, aPhraseType)

	override fun typeUnionOfPojoType(aPojoType: A_Type) =
		descriptor().o_TypeUnionOfPojoType(this, aPojoType)

	override fun typeUnionOfSetType(aSetType: A_Type) =
		descriptor().o_TypeUnionOfSetType(this, aSetType)

	override fun typeUnionOfTupleType(aTupleType: A_Type) =
		descriptor().o_TypeUnionOfTupleType(this, aTupleType)

	override fun unionOfTypesAtThrough(startIndex: Int, endIndex: Int) =
		descriptor().o_UnionOfTypesAtThrough(this, startIndex, endIndex)

	override fun upperBound() = descriptor().o_UpperBound(this)

	override fun upperInclusive() = descriptor().o_UpperInclusive(this)

	override fun value() = descriptor().o_Value(this)

	override fun value(value: A_BasicObject) = descriptor().o_Value(this, value)

	override fun valuesAsTuple() = descriptor().o_ValuesAsTuple(this)

	override fun variableBindings() = descriptor().o_VariableBindings(this)

	override fun visibleNames() = descriptor().o_VisibleNames(this)

	override fun parsingInstructions() = descriptor().o_ParsingInstructions(this)

	/**
	 * Extract the expression from the [PhraseKind.ASSIGNMENT_PHRASE] or
	 * [PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE].
	 */
	override fun expression() = descriptor().o_Expression(this)

	override fun variable() = descriptor().o_Variable(this)

	override fun argumentsTuple() = descriptor().o_ArgumentsTuple(this)

	override fun statementsTuple() = descriptor().o_StatementsTuple(this)

	override fun resultType() = descriptor().o_ResultType(this)

	override fun neededVariables(neededVariables: A_Tuple) {
		descriptor().o_NeededVariables(this, neededVariables)
	}

	override fun neededVariables() = descriptor().o_NeededVariables(this)

	override fun primitive(): Primitive? = descriptor().o_Primitive(this)

	override fun declaredType() = descriptor().o_DeclaredType(this)

	override fun declarationKind() = descriptor().o_DeclarationKind(this)

	override fun initializationExpression() =
		descriptor().o_InitializationExpression(this)

	override fun literalObject() = descriptor().o_LiteralObject(this)

	override fun token() = descriptor().o_Token(this)

	override fun markerValue() = descriptor().o_MarkerValue(this)

	override fun argumentsListNode() = descriptor().o_ArgumentsListNode(this)

	override fun bundle() = descriptor().o_Bundle(this)

	override fun expressionsTuple() = descriptor().o_ExpressionsTuple(this)

	override fun declaration() = descriptor().o_Declaration(this)

	override fun expressionType() = descriptor().o_ExpressionType(this)

	override fun emitEffectOn(codeGenerator: AvailCodeGenerator) =
		descriptor().o_EmitEffectOn(this, codeGenerator)

	override fun emitValueOn(codeGenerator: AvailCodeGenerator) =
		descriptor().o_EmitValueOn(this, codeGenerator)

	override fun childrenMap(aBlock: Transformer1<A_Phrase, A_Phrase>) =
		descriptor().o_ChildrenMap(this, aBlock)

	override fun childrenDo(action: Continuation1NotNull<A_Phrase>) =
		descriptor().o_ChildrenDo(this, action)

	override fun validateLocally(parent: A_Phrase?) =
		descriptor().o_ValidateLocally(this, parent)

	override fun generateInModule(module: A_Module) =
		descriptor().o_GenerateInModule(this, module)

	override fun copyWith(newPhrase: A_Phrase) =
		descriptor().o_CopyWith(this, newPhrase)

	override fun copyConcatenating(newListPhrase: A_Phrase) =
		descriptor().o_CopyConcatenating(this, newListPhrase)

	override fun isLastUse(isLastUse: Boolean) =
		descriptor().o_IsLastUse(this, isLastUse)

	override fun isLastUse() = descriptor().o_IsLastUse(this)

	override fun isMacroDefinition() = descriptor().o_IsMacroDefinition(this)

	override fun copyMutablePhrase() = descriptor().o_CopyMutablePhrase(this)

	override fun binUnionKind() = descriptor().o_BinUnionKind(this)

	override fun outputPhrase() = descriptor().o_OutputPhrase(this)

	override fun apparentSendName() = descriptor().o_ApparentSendName(this)

	override fun statements() = descriptor().o_Statements(this)

	override fun flattenStatementsInto(accumulatedStatements: List<A_Phrase>) =
		descriptor().o_FlattenStatementsInto(this, accumulatedStatements)

	override fun lineNumber() = descriptor().o_LineNumber(this)

	override fun allParsingPlansInProgress() =
		descriptor().o_AllParsingPlansInProgress(this)

	override val isSetBin get() = descriptor().o_IsSetBin(this)

	override fun mapIterable() = descriptor().o_MapIterable(this)

	override fun declaredExceptions() = descriptor().o_DeclaredExceptions(this)

	override val isInt get() = descriptor().o_IsInt(this)

	override val isLong get() = descriptor().o_IsLong(this)

	override fun argsTupleType() = descriptor().o_ArgsTupleType(this)

	override fun equalsInstanceTypeFor(anInstanceType: AvailObject) =
		descriptor().o_EqualsInstanceTypeFor(this, anInstanceType)

	override fun instances() = descriptor().o_Instances(this)

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

	override fun isEnumeration() = descriptor().o_IsEnumeration(this)

	override fun enumerationIncludesInstance(potentialInstance: AvailObject) =
		descriptor().o_EnumerationIncludesInstance(this, potentialInstance)

	override fun valueType() = descriptor().o_ValueType(this)

	override fun computeSuperkind() = descriptor().o_ComputeSuperkind(this)

	override fun equalsCompiledCodeType(aCompiledCodeType: A_Type) =
		descriptor().o_EqualsCompiledCodeType(this, aCompiledCodeType)

	override fun isSupertypeOfCompiledCodeType(aCompiledCodeType: A_Type) =
		descriptor().o_IsSupertypeOfCompiledCodeType(this, aCompiledCodeType)

	override fun typeUnionOfCompiledCodeType(aCompiledCodeType: A_Type) =
		descriptor().o_TypeUnionOfCompiledCodeType(this, aCompiledCodeType)

	override fun setAtomProperty(key: A_Atom, value: A_BasicObject) =
		descriptor().o_SetAtomProperty(this, key, value)

	override fun getAtomProperty(key: A_Atom) =
		descriptor().o_GetAtomProperty(this, key)

	override fun equalsEnumerationType(anEnumerationType: A_BasicObject) =
		descriptor().o_EqualsEnumerationType(this, anEnumerationType)

	override fun readType() = descriptor().o_ReadType(this)

	override fun writeType() = descriptor().o_WriteType(this)

	override fun versions(versionStrings: A_Set) =
		descriptor().o_Versions(this, versionStrings)

	override fun versions() = descriptor().o_Versions(this)

	override fun phraseKind() = descriptor().o_PhraseKind(this)

	override fun phraseKindIsUnder(expectedPhraseKind: PhraseKind) =
		descriptor().o_PhraseKindIsUnder(this, expectedPhraseKind)

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

	override fun extractUnsignedShort() =
		descriptor().o_ExtractUnsignedShort(this)

	override val isFloat get() = descriptor().o_IsFloat(this)

	override val isDouble get() = descriptor().o_IsDouble(this)

	override fun rawPojo() = descriptor().o_RawPojo(this)

	override val isPojo get() = descriptor().o_IsPojo(this)

	override val isPojoType get() = descriptor().o_IsPojoType(this)

	override fun numericCompare(another: A_Number) =
		descriptor().o_NumericCompare(this, another)

	override fun numericCompareToInfinity(sign: Sign) =
		descriptor().o_NumericCompareToInfinity(this, sign)

	override fun numericCompareToDouble(aDouble: Double) =
		descriptor().o_NumericCompareToDouble(this, aDouble)

	override fun numericCompareToInteger(anInteger: AvailObject) =
		descriptor().o_NumericCompareToInteger(this, anInteger)

	override fun addToDoubleCanDestroy(
		doubleObject: A_Number,
		canDestroy: Boolean
	) = descriptor().o_AddToDoubleCanDestroy(this, doubleObject, canDestroy)

	override fun addToFloatCanDestroy(
		floatObject: A_Number,
		canDestroy: Boolean
	) = descriptor().o_AddToFloatCanDestroy(this, floatObject, canDestroy)

	override fun subtractFromDoubleCanDestroy(
		doubleObject: A_Number,
		canDestroy: Boolean
	) = descriptor().o_SubtractFromDoubleCanDestroy(
		this, doubleObject, canDestroy)

	override fun subtractFromFloatCanDestroy(
		floatObject: A_Number,
		canDestroy: Boolean
	) = descriptor().o_SubtractFromFloatCanDestroy(
		this, floatObject, canDestroy)

	override fun multiplyByDoubleCanDestroy(
		doubleObject: A_Number,
		canDestroy: Boolean
	) = descriptor().o_MultiplyByDoubleCanDestroy(
		this, doubleObject, canDestroy)

	override fun multiplyByFloatCanDestroy(
		floatObject: A_Number,
		canDestroy: Boolean
	) = descriptor().o_MultiplyByFloatCanDestroy(this, floatObject, canDestroy)

	override fun divideIntoDoubleCanDestroy(
		doubleObject: A_Number,
		canDestroy: Boolean
	) = descriptor().o_DivideIntoDoubleCanDestroy(
		this, doubleObject, canDestroy)

	override fun divideIntoFloatCanDestroy(
		floatObject: A_Number,
		canDestroy: Boolean
	) = descriptor().o_DivideIntoFloatCanDestroy(this, floatObject, canDestroy)

	override fun lazyPrefilterMap() = descriptor().o_LazyPrefilterMap(this)

	override fun serializerOperation() =
		descriptor().o_SerializerOperation(this)

	override fun mapBinAtHashPutLevelCanDestroy(
		key: A_BasicObject,
		keyHash: Int,
		value: A_BasicObject,
		myLevel: Byte,
		canDestroy: Boolean
	) = descriptor().o_MapBinAtHashPutLevelCanDestroy(
		this, key, keyHash, value, myLevel, canDestroy)

	override fun mapBinRemoveKeyHashCanDestroy(
		key: A_BasicObject,
		keyHash: Int,
		canDestroy: Boolean
	) = descriptor().o_MapBinRemoveKeyHashCanDestroy(
		this, key, keyHash, canDestroy)

	override fun mapBinAtHashReplacingLevelCanDestroy(
		key: A_BasicObject,
		keyHash: Int,
		notFoundValue: A_BasicObject,
		transformer: BinaryOperator<A_BasicObject>,
		myLevel: Byte,
		canDestroy: Boolean
	): A_MapBin = descriptor().o_MapBinAtHashReplacingLevelCanDestroy(
		this, key, keyHash, notFoundValue, transformer, myLevel, canDestroy)

	override fun mapBinKeyUnionKind() = descriptor().o_MapBinKeyUnionKind(this)

	override fun mapBinValueUnionKind() =
		descriptor().o_MapBinValueUnionKind(this)

	override fun isHashedMapBin() = descriptor().o_IsHashedMapBin(this)

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
	override fun mapBinAtHash(key: A_BasicObject, keyHash: Int) =
		descriptor().o_MapBinAtHash(this, key, keyHash)

	override fun mapBinKeysHash() = descriptor().o_MapBinKeysHash(this)

	override fun mapBinValuesHash() = descriptor().o_MapBinValuesHash(this)

	override fun issuingModule() = descriptor().o_IssuingModule(this)

	override val isPojoFusedType get() = descriptor().o_IsPojoFusedType(this)

	override fun isSupertypeOfPojoBottomType(aPojoType: A_Type) =
		descriptor().o_IsSupertypeOfPojoBottomType(this, aPojoType)

	override fun equalsPojoBottomType() =
		descriptor().o_EqualsPojoBottomType(this)

	override fun javaAncestors() = descriptor().o_JavaAncestors(this)

	override fun typeIntersectionOfPojoFusedType(aFusedPojoType: A_Type) =
		descriptor().o_TypeIntersectionOfPojoFusedType(this, aFusedPojoType)

	override fun typeIntersectionOfPojoUnfusedType(anUnfusedPojoType: A_Type) =
		descriptor().o_TypeIntersectionOfPojoUnfusedType(
			this, anUnfusedPojoType)

	override fun typeUnionOfPojoFusedType(aFusedPojoType: A_Type) =
		descriptor().o_TypeUnionOfPojoFusedType(this, aFusedPojoType)

	override fun typeUnionOfPojoUnfusedType(anUnfusedPojoType: A_Type) =
		descriptor().o_TypeUnionOfPojoUnfusedType(this, anUnfusedPojoType)

	override val isPojoArrayType get() = descriptor().o_IsPojoArrayType(this)

	override fun marshalToJava(classHint: Class<*>?) =
		descriptor().o_MarshalToJava(this, classHint)

	override fun typeVariables() = descriptor().o_TypeVariables(this)

	override fun equalsPojoField(field: AvailObject, receiver: AvailObject) =
		descriptor().o_EqualsPojoField(this, field, receiver)

	override val isSignedByte get() = descriptor().o_IsSignedByte(this)

	override val isSignedShort get() = descriptor().o_IsSignedShort(this)

	override fun extractSignedByte() = descriptor().o_ExtractSignedByte(this)

	override fun extractSignedShort() = descriptor().o_ExtractSignedShort(this)

	override fun equalsEqualityRawPojoFor(
		otherEqualityRawPojo: AvailObject,
		otherJavaObject: Any?
	) = descriptor().o_EqualsEqualityRawPojo(
		this, otherEqualityRawPojo, otherJavaObject)

	override fun <T> javaObject(): T? = descriptor().o_JavaObject(this)

	override fun <T> javaObjectNotNull(): T = descriptor().o_JavaObject(this)!!

	override fun asBigInteger() = descriptor().o_AsBigInteger(this)

	override fun appendCanDestroy(
		newElement: A_BasicObject,
		canDestroy: Boolean
	) = descriptor().o_AppendCanDestroy(this, newElement, canDestroy)

	override fun lazyIncompleteCaseInsensitive() =
		descriptor().o_LazyIncompleteCaseInsensitive(this)

	override fun lowerCaseString() = descriptor().o_LowerCaseString(this)

	override fun instanceCount() = descriptor().o_InstanceCount(this)

	override fun totalInvocations() = descriptor().o_TotalInvocations(this)

	override fun tallyInvocation() = descriptor().o_TallyInvocation(this)

	override fun fieldTypeTuple() = descriptor().o_FieldTypeTuple(this)

	override fun fieldTuple() = descriptor().o_FieldTuple(this)

	override fun literalType() = descriptor().o_LiteralType(this)

	override fun typeIntersectionOfTokenType(aTokenType: A_Type) =
		descriptor().o_TypeIntersectionOfTokenType(this, aTokenType)

	override fun typeIntersectionOfLiteralTokenType(aLiteralTokenType: A_Type) =
		descriptor().o_TypeIntersectionOfLiteralTokenType(
			this, aLiteralTokenType)

	override fun typeUnionOfTokenType(aTokenType: A_Type) =
		descriptor().o_TypeUnionOfTokenType(this, aTokenType)

	override fun typeUnionOfLiteralTokenType(aLiteralTokenType: A_Type) =
		descriptor().o_TypeUnionOfLiteralTokenType(this, aLiteralTokenType)

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

	override fun bitwiseAnd(anInteger: A_Number, canDestroy: Boolean) =
		descriptor().o_BitwiseAnd(this, anInteger, canDestroy)

	override fun bitwiseOr(anInteger: A_Number, canDestroy: Boolean) =
		descriptor().o_BitwiseOr(this, anInteger, canDestroy)

	override fun bitwiseXor(anInteger: A_Number, canDestroy: Boolean) =
		descriptor().o_BitwiseXor(this, anInteger, canDestroy)

	override fun addSeal(methodName: A_Atom, sealSignature: A_Tuple) =
		descriptor().o_AddSeal(this, methodName, sealSignature)

	override val isInstanceMeta get() = descriptor().o_IsInstanceMeta(this)

	override fun instance() = descriptor().o_Instance(this)

	override fun setMethodName(methodName: A_String) =
		descriptor().o_SetMethodName(this, methodName)

	override fun startingLineNumber() = descriptor().o_StartingLineNumber(this)

	override fun module() = descriptor().o_Module(this)

	override fun methodName() = descriptor().o_MethodName(this)

	override fun binElementsAreAllInstancesOfKind(kind: A_Type) =
		descriptor().o_BinElementsAreAllInstancesOfKind(this, kind)

	override fun setElementsAreAllInstancesOfKind(kind: AvailObject) =
		descriptor().o_SetElementsAreAllInstancesOfKind(this, kind)

	override fun mapBinIterable() = descriptor().o_MapBinIterable(this)

	override fun rangeIncludesInt(anInt: Int) =
		descriptor().o_RangeIncludesInt(this, anInt)

	override fun bitShiftLeftTruncatingToBits(
		shiftFactor: A_Number,
		truncationBits: A_Number,
		canDestroy: Boolean
	) = descriptor().o_BitShiftLeftTruncatingToBits(
		this, shiftFactor, truncationBits, canDestroy)

	override fun setBinIterator() = descriptor().o_SetBinIterator(this)

	override fun bitShift(shiftFactor: A_Number, canDestroy: Boolean) =
		descriptor().o_BitShift(this, shiftFactor, canDestroy)

	override fun equalsPhrase(aPhrase: A_Phrase) =
		descriptor().o_EqualsPhrase(this, aPhrase)

	override fun stripMacro() = descriptor().o_StripMacro(this)

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

	override fun compareFromToWithByteArrayTupleStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		aByteArrayTuple: A_Tuple,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithByteArrayTupleStartingAt(
		this, startIndex1, endIndex1, aByteArrayTuple, startIndex2)

	override fun byteArray() = descriptor().o_ByteArray(this)

	override val isByteArrayTuple get() = descriptor().o_IsByteArrayTuple(this)

	override fun updateForNewGrammaticalRestriction(
		planInProgress: A_ParsingPlanInProgress,
		treesToVisit: Deque<Pair<A_BundleTree, A_ParsingPlanInProgress>>
	) = descriptor().o_UpdateForNewGrammaticalRestriction(
		this, planInProgress, treesToVisit)

	override fun lock(critical: Continuation0) =
		descriptor().o_Lock(this, critical)

	override fun <T : Any> lock(supplier: Supplier<T>): T =
		descriptor().o_Lock(this, supplier)

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

	override fun availLoader(loader: AvailLoader?) =
		descriptor().o_AvailLoader(this, loader)

	override fun moduleName() = descriptor().o_ModuleName(this)

	override fun bundleMethod() = descriptor().o_BundleMethod(this)

	/**
	 * Answer the [continuation][Continuation1NotNull] that accepts the result
	 * produced by the [receiver][FiberDescriptor]'s successful completion.
	 *
	 * @return
	 *   A continuation.
	 */
	override fun resultContinuation(): Continuation1NotNull<AvailObject> =
		descriptor().o_ResultContinuation(this)

	/**
	 * Answer the [continuation][Continuation1NotNull] that accepts
	 * the [throwable][Throwable] responsible for abnormal termination
	 * of the [receiver][FiberDescriptor].
	 *
	 * @return
	 *   A continuation.
	 */
	override fun failureContinuation(): Continuation1NotNull<Throwable> =
		descriptor().o_FailureContinuation(this)

	override fun setSuccessAndFailureContinuations(
		onSuccess: Continuation1NotNull<AvailObject>,
		onFailure: Continuation1NotNull<Throwable>
	) = descriptor().o_SetSuccessAndFailureContinuations(
		this, onSuccess, onFailure)

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

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun fetchAndAddValue(addend: A_Number) =
		descriptor().o_FetchAndAddValue(this, addend)

	override fun getAndClearInterruptRequestFlag(flag: InterruptRequestFlag) =
		descriptor().o_GetAndClearInterruptRequestFlag(this, flag)

	override fun getAndSetSynchronizationFlag(
		flag: SynchronizationFlag,
		newValue: Boolean
	) = descriptor().o_GetAndSetSynchronizationFlag(this, flag, newValue)

	override fun fiberResult() = descriptor().o_FiberResult(this)

	override fun fiberResult(result: A_BasicObject) =
		descriptor().o_FiberResult(this, result)

	override fun joiningFibers() = descriptor().o_JoiningFibers(this)

	override fun wakeupTask(): TimerTask? = descriptor().o_WakeupTask(this)

	override fun wakeupTask(task: TimerTask?) =
		descriptor().o_WakeupTask(this, task)

	override fun joiningFibers(joiners: A_Set) =
		descriptor().o_JoiningFibers(this, joiners)

	override fun heritableFiberGlobals() =
		descriptor().o_HeritableFiberGlobals(this)

	override fun heritableFiberGlobals(globals: A_Map) =
		descriptor().o_HeritableFiberGlobals(this, globals)

	override fun generalFlag(flag: GeneralFlag) =
		descriptor().o_GeneralFlag(this, flag)

	override fun setGeneralFlag(flag: GeneralFlag) =
		descriptor().o_SetGeneralFlag(this, flag)

	override fun clearGeneralFlag(flag: GeneralFlag) =
		descriptor().o_ClearGeneralFlag(this, flag)

	override fun byteBuffer() = descriptor().o_ByteBuffer(this)

	override fun equalsByteBufferTuple(aByteBufferTuple: A_Tuple) =
		descriptor().o_EqualsByteBufferTuple(this, aByteBufferTuple)

	override fun compareFromToWithByteBufferTupleStartingAt(
		startIndex1: Int,
		endIndex1: Int,
		aByteBufferTuple: A_Tuple,
		startIndex2: Int
	) = descriptor().o_CompareFromToWithByteBufferTupleStartingAt(
		this, startIndex1, endIndex1, aByteBufferTuple, startIndex2)

	override val isByteBufferTuple get() =
		descriptor().o_IsByteBufferTuple(this)

	override fun fiberName() = descriptor().o_FiberName(this)

	override fun fiberNameSupplier(supplier: Supplier<A_String>) =
		descriptor().o_FiberNameSupplier(this, supplier)

	override fun bundles() = descriptor().o_Bundles(this)

	override fun methodAddBundle(bundle: A_Bundle) =
		descriptor().o_MethodAddBundle(this, bundle)

	override fun definitionModule() = descriptor().o_DefinitionModule(this)

	override fun definitionModuleName() =
		descriptor().o_DefinitionModuleName(this)

	@Throws(MalformedMessageException::class)
	override fun bundleOrCreate() = descriptor().o_BundleOrCreate(this)

	override fun bundleOrNil() = descriptor().o_BundleOrNil(this)

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

	override fun treeTupleLevel() = descriptor().o_TreeTupleLevel(this)

	override fun childCount() = descriptor().o_ChildCount(this)

	override fun childAt(childIndex: Int) =
		descriptor().o_ChildAt(this, childIndex)

	override fun concatenateWith(otherTuple: A_Tuple, canDestroy: Boolean) =
		descriptor().o_ConcatenateWith(this, otherTuple, canDestroy)

	override fun replaceFirstChild(newFirst: A_Tuple) =
		descriptor().o_ReplaceFirstChild(this, newFirst)

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
		whenReified: Continuation1NotNull<A_Continuation>
	) = descriptor().o_WhenContinuationIsAvailableDo(this, whenReified)

	override fun getAndClearReificationWaiters() =
		descriptor().o_GetAndClearReificationWaiters(this)

	override fun isBottom() = descriptor().o_IsBottom(this)

	override fun isVacuousType() = descriptor().o_IsVacuousType(this)

	override fun isTop() = descriptor().o_IsTop(this)

	override val isAtomSpecial get() = descriptor().o_IsAtomSpecial(this)

	override fun addPrivateNames(trueNames: A_Set) =
		descriptor().o_AddPrivateNames(this, trueNames)

	override fun hasValue() = descriptor().o_HasValue(this)

	override fun addUnloadFunction(unloadFunction: A_Function) =
		descriptor().o_AddUnloadFunction(this, unloadFunction)

	override fun exportedNames() = descriptor().o_ExportedNames(this)

	override val isInitializedWriteOnceVariable get() =
		descriptor().o_IsInitializedWriteOnceVariable(this)

	override fun transferIntoByteBuffer(
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer
	) = descriptor().o_TransferIntoByteBuffer(
		this, startIndex, endIndex, outputByteBuffer)

	override fun tupleElementsInRangeAreInstancesOf(
		startIndex: Int,
		endIndex: Int,
		type: A_Type
	) = descriptor().o_TupleElementsInRangeAreInstancesOf(
		this, startIndex, endIndex, type)

	override fun isNumericallyIntegral() =
		descriptor().o_IsNumericallyIntegral(this)

	override fun textInterface() = descriptor().o_TextInterface(this)

	override fun textInterface(textInterface: TextInterface) =
		descriptor().o_TextInterface(this, textInterface)

	override fun writeTo(writer: JSONWriter) =
		descriptor().o_WriteTo(this, writer)

	override fun writeSummaryTo(writer: JSONWriter) =
		descriptor().o_WriteSummaryTo(this, writer)

	override fun typeIntersectionOfPrimitiveTypeEnum(primitiveTypeEnum: Types) =
		descriptor().o_TypeIntersectionOfPrimitiveTypeEnum(
			this, primitiveTypeEnum)

	override fun typeUnionOfPrimitiveTypeEnum(primitiveTypeEnum: Types) =
		descriptor().o_TypeUnionOfPrimitiveTypeEnum(this, primitiveTypeEnum)

	override fun tupleOfTypesFromTo(startIndex: Int, endIndex: Int) =
		descriptor().o_TupleOfTypesFromTo(this, startIndex, endIndex)

	override fun list() = descriptor().o_List(this)

	override fun permutation() = descriptor().o_Permutation(this)

	override fun emitAllValuesOn(codeGenerator: AvailCodeGenerator) =
		descriptor().o_EmitAllValuesOn(this, codeGenerator)

	override fun superUnionType() = descriptor().o_SuperUnionType(this)

	override fun hasSuperCast() = descriptor().o_HasSuperCast(this)

	override fun macroDefinitionsTuple() =
		descriptor().o_MacroDefinitionsTuple(this)

	override fun lookupMacroByPhraseTuple(argumentPhraseTuple: A_Tuple) =
		descriptor().o_LookupMacroByPhraseTuple(this, argumentPhraseTuple)

	override fun expressionAt(index: Int) =
		descriptor().o_ExpressionAt(this, index)

	override fun expressionsSize() = descriptor().o_ExpressionsSize(this)

	override fun lastExpression() = descriptor().o_LastExpression(this)

	override fun parsingPc() = descriptor().o_ParsingPc(this)

	override fun isMacroSubstitutionNode() =
		descriptor().o_IsMacroSubstitutionNode(this)

	override fun messageSplitter() = descriptor().o_MessageSplitter(this)

	override fun statementsDo(continuation: Continuation1NotNull<A_Phrase>) =
		descriptor().o_StatementsDo(this, continuation)

	override fun macroOriginalSendNode() =
		descriptor().o_MacroOriginalSendNode(this)

	override fun equalsInt(theInt: Int) = descriptor().o_EqualsInt(this, theInt)

	override fun tokens() = descriptor().o_Tokens(this)

	override fun chooseBundle(currentModule: A_Module) =
		descriptor().o_ChooseBundle(this, currentModule)

	override fun valueWasStablyComputed() =
		descriptor().o_ValueWasStablyComputed(this)

	override fun valueWasStablyComputed(wasStablyComputed: Boolean) =
		descriptor().o_ValueWasStablyComputed(this, wasStablyComputed)

	override fun uniqueId() = descriptor().o_UniqueId(this)

	override fun definition() = descriptor().o_Definition(this)

	override fun nameHighlightingPc() =
		descriptor().o_NameHighlightingPc(this)

	override fun setIntersects(otherSet: A_Set) =
		descriptor().o_SetIntersects(this, otherSet)

	override fun removePlanForDefinition(definition: A_Definition) =
		descriptor().o_RemovePlanForDefinition(this, definition)

	override fun definitionParsingPlans() =
		descriptor().o_DefinitionParsingPlans(this)

	override fun equalsListNodeType(listNodeType: A_Type) =
		descriptor().o_EqualsListNodeType(this, listNodeType)

	override fun subexpressionsTupleType() =
		descriptor().o_SubexpressionsTupleType(this)

	override fun lazyTypeFilterTreePojo() =
		descriptor().o_LazyTypeFilterTreePojo(this)

	override fun addPlanInProgress(planInProgress: A_ParsingPlanInProgress) =
		descriptor().o_AddPlanInProgress(this, planInProgress)

	override fun parsingSignature() = descriptor().o_ParsingSignature(this)

	override fun removePlanInProgress(planInProgress: A_ParsingPlanInProgress) =
		descriptor().o_RemovePlanInProgress(this, planInProgress)

	override fun moduleSemanticRestrictions() =
		descriptor().o_ModuleSemanticRestrictions(this)

	override fun moduleGrammaticalRestrictions() =
		descriptor().o_ModuleGrammaticalRestrictions(this)

	@ReferencedInGeneratedCode
	override fun fieldAt(field: A_Atom) = descriptor().o_FieldAt(this, field)

	override fun fieldAtPuttingCanDestroy(
		field: A_Atom,
		value: A_BasicObject,
		canDestroy: Boolean
	) = descriptor().o_FieldAtPuttingCanDestroy(this, field, value, canDestroy)

	override fun fieldTypeAt(field: A_Atom) =
		descriptor().o_FieldTypeAt(this, field)

	override fun parsingPlan() = descriptor().o_ParsingPlan(this)

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun atomicAddToMap(key: A_BasicObject, value: A_BasicObject) =
		descriptor().o_AtomicAddToMap(this, key, value)

	@Throws(VariableGetException::class)
	override fun variableMapHasKey(key: A_BasicObject) =
		descriptor().o_VariableMapHasKey(this, key)

	override fun lexerMethod() = descriptor().o_LexerMethod(this)

	override fun lexerFilterFunction() =
		descriptor().o_LexerFilterFunction(this)

	override fun lexerBodyFunction() = descriptor().o_LexerBodyFunction(this)

	override fun setLexer(lexer: A_Lexer) = descriptor().o_SetLexer(this, lexer)

	override fun addLexer(lexer: A_Lexer) = descriptor().o_AddLexer(this, lexer)

	override fun originatingPhrase() = descriptor().o_OriginatingPhrase(this)

	override fun typeExpression() = descriptor().o_TypeExpression(this)

	override fun isGlobal() = descriptor().o_IsGlobal(this)

	override fun globalModule() = descriptor().o_GlobalModule(this)

	override fun globalName() = descriptor().o_GlobalName(this)

	override fun nextLexingState() = descriptor().o_NextLexingState(this)

	override fun setNextLexingStateFromPrior(priorLexingState: LexingState) =
		descriptor().o_SetNextLexingStateFromPrior(this, priorLexingState)

	override fun tupleCodePointAt(index: Int) =
		descriptor().o_TupleCodePointAt(this, index)

	override fun createLexicalScanner() =
		descriptor().o_CreateLexicalScanner(this)

	override fun lexer() = descriptor().o_Lexer(this)

	override fun suspendingFunction(suspendingFunction: A_Function) =
		descriptor().o_SuspendingFunction(this, suspendingFunction)

	override fun suspendingFunction() = descriptor().o_SuspendingFunction(this)

	override fun isBackwardJump() = descriptor().o_IsBackwardJump(this)

	override fun latestBackwardJump() = descriptor().o_LatestBackwardJump(this)

	override fun hasBackwardJump() = descriptor().o_HasBackwardJump(this)

	override val isSourceOfCycle get() = descriptor().o_IsSourceOfCycle(this)

	override fun isSourceOfCycle(isSourceOfCycle: Boolean) =
		descriptor().o_IsSourceOfCycle(this, isSourceOfCycle)

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

	override fun forEach(action: BiConsumer<in AvailObject, in AvailObject>) =
		descriptor().o_ForEach(this, action)

	override fun forEachInMapBin(
		action: BiConsumer<in AvailObject, in AvailObject>
	) = descriptor().o_ForEachInMapBin(this, action)

	override fun clearLexingState() = descriptor().o_ClearLexingState(this)

	@ReferencedInGeneratedCode
	override fun registerDump() = descriptor().o_RegisterDump(this)

	override fun component1() = tupleAt(1)

	override fun component2() = tupleAt(2)

	override fun component3() = tupleAt(3)

	override fun component4() = tupleAt(4)

	override fun component5() = tupleAt(5)

	override fun component6() = tupleAt(6)

	override fun component7() = tupleAt(7)

	override fun component8() = tupleAt(8)

	override fun component9() = tupleAt(9)

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
			assert(hasVariableObjectSlots || variableObjectSlots == 0)
			assert(hasVariableIntegerSlots || variableIntegerSlots == 0)
			return AvailObject(
				descriptor,
				numberOfFixedObjectSlots() + variableObjectSlots,
				numberOfFixedIntegerSlots() + variableIntegerSlots)
		}

		/** The [CheckedMethod] for [iterator]. */
		val iteratorMethod = instanceMethod(
			AvailObject::class.java,
			"iterator",
			IteratorNotNull::class.java)

		/** Access the [argOrLocalOrStackAt] method.  */
		@JvmField
		val argOrLocalOrStackAtMethod: CheckedMethod = instanceMethod(
			AvailObject::class.java,
			"argOrLocalOrStackAt",
			AvailObject::class.java,
			Int::class.javaPrimitiveType)

		/** Access the [argOrLocalOrStackAtPut] method.  */
		@JvmField
		val argOrLocalOrStackAtPutMethod: CheckedMethod = instanceMethod(
			AvailObject::class.java,
			"argOrLocalOrStackAtPut",
			Void.TYPE,
			Int::class.javaPrimitiveType,
			AvailObject::class.java)

		/** Access the [registerDump] method.  */
		@JvmField
		val registerDumpMethod: CheckedMethod = instanceMethod(
			AvailObject::class.java,
			"registerDump",
			AvailObject::class.java)

		/** Access the [fieldAt] method.  */
		@JvmField
		val fieldAtMethod: CheckedMethod = instanceMethod(
			AvailObject::class.java,
			"fieldAt",
			AvailObject::class.java,
			A_Atom::class.java)
	}
}