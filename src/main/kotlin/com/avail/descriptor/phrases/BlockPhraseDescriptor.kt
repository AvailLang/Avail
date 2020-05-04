/*
 * BlockPhraseDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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
package com.avail.descriptor.phrases

import com.avail.AvailRuntime
import com.avail.annotations.AvailMethod
import com.avail.annotations.EnumField
import com.avail.compiler.AvailCodeGenerator
import com.avail.compiler.AvailCodeGenerator.Companion.generateFunction
import com.avail.descriptor.A_Module
import com.avail.descriptor.ModuleDescriptor
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import com.avail.descriptor.phrases.BlockPhraseDescriptor.IntegerSlots.Companion.PRIMITIVE
import com.avail.descriptor.phrases.BlockPhraseDescriptor.IntegerSlots.Companion.STARTING_LINE_NUMBER
import com.avail.descriptor.phrases.BlockPhraseDescriptor.ObjectSlots.*
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.representation.*
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.generateObjectTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromList
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.*
import com.avail.descriptor.types.TypeTag
import com.avail.exceptions.AvailErrorCode.E_BLOCK_MUST_NOT_CONTAIN_OUTERS
import com.avail.exceptions.AvailRuntimeException
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Companion.byNumber
import com.avail.interpreter.Primitive.Flag
import com.avail.serialization.SerializerOperation
import com.avail.utility.Strings.newlineTab
import com.avail.utility.evaluation.Combinator.recurse
import com.avail.utility.evaluation.Continuation1NotNull
import com.avail.utility.json.JSONWriter
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.UnaryOperator

/**
 * My instances represent occurrences of blocks (functions) encountered in code.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class BlockPhraseDescriptor
private constructor(mutability: Mutability) : PhraseDescriptor(
	mutability,
	TypeTag.BLOCK_PHRASE_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * My slots of type [int][Integer].
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * A slot containing multiple [BitField]s.
		 */
		PRIMITIVE_AND_STARTING_LINE_NUMBER;

		companion object {
			/**
			 * The [primitive][Primitive] number to invoke for this block. This
			 * is the ephemeral number that was assigned as primitives were
			 * loaded, found in [Primitive.primitiveNumber], not a numbering
			 * that exists outside of a particular [AvailRuntime].  Note that
			 * this number is never serialized or exposed to the Avail language.
			 */
			@EnumField(
				describedBy = Primitive::class,
				lookupMethodName = "byPrimitiveNumberOrNull")
			val PRIMITIVE = BitField(
				PRIMITIVE_AND_STARTING_LINE_NUMBER, 0, 32)

			/**
			 * The line number on which this block starts.
			 */
			val STARTING_LINE_NUMBER = BitField(
				PRIMITIVE_AND_STARTING_LINE_NUMBER, 32, 32)
		}
	}

	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The block's tuple of argument declarations.
		 */
		ARGUMENTS_TUPLE,

		/**
		 * The tuple of statements contained in this block.
		 */
		STATEMENTS_TUPLE,

		/**
		 * The type this block is expected to return an instance of.
		 */
		RESULT_TYPE,

		/**
		 * A tuple of variables needed by this block.  This is set after the
		 * [block][BlockPhraseDescriptor] phrase has already been created.
		 */
		NEEDED_VARIABLES,

		/**
		 * The block's set of exception types that may be raised.  This set *has
		 * not yet been normalized* (e.g., removing types that are subtypes of
		 * types that are also present in the set).
		 */
		DECLARED_EXCEPTIONS,

		/**
		 * The tuple of tokens forming this block phrase, if any.
		 */
		TOKENS
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		// Optimize for one-liners...
		val argumentsTuple = self.argumentsTuple()
		val argCount = argumentsTuple.tupleSize()
		val primitive = self.primitive()
		val statementsTuple = self.statementsTuple()
		val statementsSize = statementsTuple.tupleSize()
		var explicitResultType: A_Type?  = self.resultType()
		if (statementsSize >= 1
			&& statementsTuple.tupleAt(statementsSize).expressionType()
				.equals(explicitResultType!!)) {
			explicitResultType = null
		}
		val declaredExceptions: A_Set? = self.declaredExceptions().let {
			if (it.setSize() == 0) null else it
		}
		val endsWithStatement = (statementsSize < 1
			|| statementsTuple.tupleAt(statementsSize).expressionType().isTop)
		if (argCount == 0
			&& primitive == null
			&& statementsSize == 1
			&& explicitResultType === null
			&& declaredExceptions === null
		) {
			// See if the lone statement fits on a line.
			val tempBuilder = StringBuilder()
			statementsTuple.tupleAt(1).printOnAvoidingIndent(
				tempBuilder,
				recursionMap,
				indent)
			if (!tempBuilder.contains('\n') && tempBuilder.length < 100) {
				builder.append('[')
				builder.append(tempBuilder)
				if (endsWithStatement) {
					builder.append(';')
				}
				builder.append(']')
				return
			}
		}

		// Use multiple lines instead...
		builder.append('[')
		var wroteAnything = false
		if (argCount > 0) {
			wroteAnything = true
			for (argIndex in 1..argCount) {
				if (argIndex > 1) {
					builder.append(',')
				}
				newlineTab(builder, indent)
				argumentsTuple.tupleAt(argIndex).printOnAvoidingIndent(
					builder, recursionMap, indent)
			}
			newlineTab(builder, indent - 1)
			builder.append('|')
		}
		var skipFailureDeclaration = false
		if (primitive != null && !primitive.hasFlag(Flag.SpecialForm)) {
			wroteAnything = true
			newlineTab(builder, indent)
			builder.append("Primitive ")
			builder.append(primitive.fieldName())
			if (!primitive.hasFlag(Flag.CannotFail)) {
				builder.append(" (")
				statementsTuple.tupleAt(1).printOnAvoidingIndent(
					builder, recursionMap, indent)
				builder.append(')')
				skipFailureDeclaration = true
			}
			builder.append(';')
		}
		for (index in 1..statementsSize) {
			val statement: A_Phrase = statementsTuple.tupleAt(index)
			if (skipFailureDeclaration) {
				assert(statement.isInstanceOf(
					DECLARATION_PHRASE.mostGeneralType()))
				skipFailureDeclaration = false
			} else {
				wroteAnything = true
				newlineTab(builder, indent)
				statement.printOnAvoidingIndent(
					builder, recursionMap, indent)
				if (index < statementsSize || endsWithStatement) {
					builder.append(';')
				}
			}
		}
		if (wroteAnything) {
			newlineTab(builder, indent - 1)
		}
		builder.append(']')
		if (explicitResultType !== null) {
			builder.append(" : ")
			builder.append(explicitResultType)
		}
		if (declaredExceptions !== null) {
			builder.append(" ^ ")
			builder.append(declaredExceptions)
		}
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = e === NEEDED_VARIABLES

	@AvailMethod
	override fun o_ArgumentsTuple(self: AvailObject): A_Tuple =
		self.slot(ARGUMENTS_TUPLE)

	@AvailMethod
	override fun o_ChildrenDo(
		self: AvailObject,
		action: Consumer<A_Phrase>
	) {
		self.argumentsTuple().forEach(action::accept)
		self.statementsTuple().forEach(action::accept)
	}

	@AvailMethod
	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: UnaryOperator<A_Phrase>
	) {
		var arguments: A_Tuple = self.slot(ARGUMENTS_TUPLE)
		arguments = generateObjectTupleFrom(arguments.tupleSize()) {
			transformer.apply(arguments.tupleAt(it))
		}
		self.setSlot(ARGUMENTS_TUPLE, arguments)
		var statements = self.slot(STATEMENTS_TUPLE)
		statements = generateObjectTupleFrom(statements.tupleSize()) {
			transformer.apply(statements.tupleAt(it))
		}
		self.setSlot(STATEMENTS_TUPLE, statements)
	}

	@AvailMethod
	override fun o_DeclaredExceptions(self: AvailObject): A_Set =
		self.slot(DECLARED_EXCEPTIONS)

	/**
	 * The expression `[`someExpression`]` has no effect, only a value (the
	 * function itself).
	 */
	@AvailMethod
	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		// No effect.
	}

	@AvailMethod
	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		val compiledBlock = self.generateInModule(codeGenerator.module)
		val neededVariables = self.neededVariables()
		if (neededVariables.tupleSize() == 0) {
			val function = createFunction(compiledBlock, emptyTuple())
			codeGenerator.emitPushLiteral(
				self.tokens(), function.makeImmutable())
		} else {
			codeGenerator.emitCloseCode(
				self.tokens(), compiledBlock, neededVariables)
		}
	}

	@AvailMethod
	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean {
		return (!aPhrase.isMacroSubstitutionNode()
			&& self.phraseKind() == aPhrase.phraseKind()
			&& self.argumentsTuple().equals(aPhrase.argumentsTuple())
			&& self.statementsTuple().equals(aPhrase.statementsTuple())
			&& self.resultType().equals(aPhrase.resultType())
			&& self.primitive() === aPhrase.primitive())
	}

	@AvailMethod
	override fun o_ExpressionType(self: AvailObject): A_Type =
		functionType(
			tupleFromList(self.argumentsTuple().map(AvailObject::declaredType)),
			self.resultType())

	/**
	 * Answer an Avail compiled block compiled from the given block phrase,
	 * using the given [AvailCodeGenerator].
	 *
	 * @param self
	 *   The block phrase.
	 * @param module
	 *   The [module][ModuleDescriptor] which is intended to hold the resulting
	 *   code.
	 * @return
	 *   An [A_RawFunction].
	 */
	@AvailMethod
	override fun o_GenerateInModule(
		self: AvailObject,
		module: A_Module
	): A_RawFunction = generateFunction(module, self)

	@AvailMethod
	override fun o_Hash(self: AvailObject): Int {
		var h = self.argumentsTuple().hash()
		h = h * multiplier + self.statementsTuple().hash()
		h = h * multiplier + self.resultType().hash()
		h = h * multiplier + (self.primitive()?.fieldName()?.hashCode() ?: 0)
		h = h * multiplier xor 0x05E6A04A
		return h
	}

	@AvailMethod
	override fun o_NeededVariables(self: AvailObject): A_Tuple =
		self.mutableSlot(NEEDED_VARIABLES)

	@AvailMethod
	override fun o_NeededVariables(
		self: AvailObject,
		neededVariables: A_Tuple
	) = self.setMutableSlot(NEEDED_VARIABLES, neededVariables)

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		BLOCK_PHRASE

	@AvailMethod
	override fun o_Primitive(self: AvailObject): Primitive? =
		byNumber(self.slot(PRIMITIVE))

	@AvailMethod
	override fun o_ResultType(self: AvailObject): A_Type =
		self.slot(RESULT_TYPE)

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.BLOCK_PHRASE

	@AvailMethod
	override fun o_StartingLineNumber(self: AvailObject): Int =
		self.slot(STARTING_LINE_NUMBER)

	@AvailMethod
	override fun o_StatementsTuple(self: AvailObject): A_Tuple =
		self.slot(STATEMENTS_TUPLE)

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: Continuation1NotNull<A_Phrase>
	) = throw unsupportedOperationException()

	override fun o_Tokens(self: AvailObject): A_Tuple = self.slot(TOKENS)

	@AvailMethod
	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) {
		// Make sure our neededVariables list has up-to-date information about
		// the outer variables that are accessed in me, because they have to be
		// captured when a function is made for me.
		collectNeededVariablesOfOuterBlocks(self)
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("block phrase")
		writer.write("primitive")
		writer.write(self.primitive()?.fieldName() ?: "")
		writer.write("starting line")
		writer.write(self.slot(STARTING_LINE_NUMBER))
		writer.write("arguments")
		self.slot(ARGUMENTS_TUPLE).writeSummaryTo(writer)
		writer.write("statements")
		self.slot(STATEMENTS_TUPLE).writeSummaryTo(writer)
		writer.write("result type")
		self.slot(RESULT_TYPE).writeSummaryTo(writer)
		writer.write("needed variables")
		self.slot(NEEDED_VARIABLES).writeSummaryTo(writer)
		writer.write("declared exceptions")
		self.slot(DECLARED_EXCEPTIONS).writeSummaryTo(writer)
		writer.endObject()
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("block phrase")
		writer.write("primitive")
		writer.write(self.primitive()?.fieldName() ?: "")
		writer.write("starting line")
		writer.write(self.slot(STARTING_LINE_NUMBER))
		writer.write("arguments")
		self.slot(ARGUMENTS_TUPLE).writeTo(writer)
		writer.write("statements")
		self.slot(STATEMENTS_TUPLE).writeTo(writer)
		writer.write("result type")
		self.slot(RESULT_TYPE).writeTo(writer)
		writer.write("needed variables")
		self.slot(NEEDED_VARIABLES).writeTo(writer)
		writer.write("declared exceptions")
		self.slot(DECLARED_EXCEPTIONS).writeTo(writer)
		writer.write("tokens")
		self.slot(TOKENS).writeTo(writer)
		writer.endObject()
	}

	override fun mutable(): BlockPhraseDescriptor = mutable

	override fun shared(): BlockPhraseDescriptor = shared

	companion object {
		/**
		 * Return a [List] of all [declaration][DeclarationPhraseDescriptor]
		 * phrases defined by this block. This includes arguments, locals,
		 * constants, and labels.
		 *
		 * @param self
		 *   The Avail block phrase to scan.
		 * @return
		 *   The list of declarations.
		 */
		private fun allLocallyDefinedVariables(
			self: A_Phrase
		): List<A_Phrase> {
			val declarations = mutableListOf<A_Phrase>()
			declarations.addAll(self.argumentsTuple())
			declarations.addAll(locals(self))
			declarations.addAll(constants(self))
			declarations.addAll(labels(self))
			return declarations
		}

		/**
		 * Answer the labels present in this block's list of statements. There
		 * is either zero or one label, and it must be the first statement.
		 *
		 * @param self
		 *   The block phrase to examine.
		 * @return
		 *   A list of between zero and one labels.
		 */
		fun labels(self: A_Phrase): List<A_Phrase> {
			for (phrase in self.statementsTuple()) {
				if (phrase.isInstanceOfKind(LABEL_PHRASE.mostGeneralType())) {
					assert(phrase.declarationKind() === DeclarationKind.LABEL)
					return listOf(phrase)
				}
			}
			return emptyList()
		}

		/**
		 * Answer the declarations of this block's local variables.  Do not
		 * include the label declaration if present, nor argument declarations,
		 * nor local constants.
		 *
		 * Include the primitive failure reason variable, if present.
		 *
		 * @param self
		 *   The block phrase to examine.
		 * @return
		 *   This block's local variable declarations.
		 */
		fun locals(self: A_Phrase): List<A_Phrase> {
			val locals = mutableListOf<A_Phrase>()
			for (phrase in self.statementsTuple()) {
				if (phrase.isInstanceOfKind(
						DECLARATION_PHRASE.mostGeneralType()))
				{
					val kind = phrase.declarationKind()
					if (kind === DeclarationKind.LOCAL_VARIABLE
						|| kind === DeclarationKind.PRIMITIVE_FAILURE_REASON)
					{
						locals.add(phrase)
					}
				}
			}
			return locals
		}

		/**
		 * Answer the declarations of this block's local constants.  Do not
		 * include the label declaration if present, nor argument declarations,
		 * nor local variables.
		 *
		 * @param self
		 *   The block phrase to examine.
		 * @return
		 *   This block's local constant declarations.
		 */
		fun constants(self: A_Phrase): List<A_Phrase> {
			val constants = mutableListOf<A_Phrase>()
			for (phrase in self.statementsTuple()) {
				if (phrase.isInstanceOfKind(
						DECLARATION_PHRASE.mostGeneralType())
					&& phrase.declarationKind()
						=== DeclarationKind.LOCAL_CONSTANT) {
					constants.add(phrase)
				}
			}
			return constants
		}

		/**
		 * Construct a block phrase.
		 *
		 * @param arguments
		 *   The [tuple][TupleDescriptor] of argument
		 *   [declarations][DeclarationPhraseDescriptor].
		 * @param primitive
		 *   The index of the primitive that the resulting block will invoke, or
		 *   zero if this is not a primitive.
		 * @param statements
		 *   The [tuple][A_Tuple] of statement [phrases][PhraseDescriptor].
		 * @param resultType
		 *   The [type][A_Type] that will be returned by the block.
		 * @param declaredExceptions
		 *   The [set][A_Set] of exception types that may be raised by this
		 *   block.  *This is not yet normalized.*
		 * @param lineNumber
		 *   The line number in the current module at which this block begins.
		 * @param tokens
		 *   The [A_Tuple] of [A_Token]s contributing to this block phrase.
		 * @return
		 *   A block phrase.
		 */
		fun newBlockNode(
			arguments: A_Tuple,
			primitive: Int,
			statements: A_Tuple,
			resultType: A_Type,
			declaredExceptions: A_Set,
			lineNumber: Int,
			tokens: A_Tuple
		): AvailObject {
			val flattenedStatements = mutableListOf<A_Phrase>()
			statements.forEach {
				it.flattenStatementsInto(flattenedStatements)
			}
			// Remove useless statements that are just literals, other than the
			// final statement.
			for (index in flattenedStatements.size - 2 downTo 0) {
				val statement = flattenedStatements[index]
				if (statement.isInstanceOfKind(
						LITERAL_PHRASE.mostGeneralType()))
				{
					flattenedStatements.removeAt(index)
				}
			}
			return mutable.create().apply {
				setSlot(ARGUMENTS_TUPLE, arguments)
				setSlot(PRIMITIVE, primitive)
				setSlot(STATEMENTS_TUPLE, tupleFromList(flattenedStatements))
				setSlot(RESULT_TYPE, resultType)
				setSlot(NEEDED_VARIABLES, nil)
				setSlot(DECLARED_EXCEPTIONS, declaredExceptions)
				setSlot(TOKENS, tokens)
				setSlot(STARTING_LINE_NUMBER, lineNumber)
				makeShared()
			}
		}

		/**
		 * Ensure that the block phrase is valid.  Throw an appropriate
		 * exception if it is not.
		 *
		 * @param blockNode
		 *   The block phrase to validate.
		 */
		fun recursivelyValidate(blockNode: A_Phrase) {
			treeDoWithParent(
				blockNode,
				BiConsumer {
					obj: A_Phrase?, parent: A_Phrase? ->
					obj!!.validateLocally(parent)
				},
				null)
			if (blockNode.neededVariables().tupleSize() != 0) {
				throw AvailRuntimeException(E_BLOCK_MUST_NOT_CONTAIN_OUTERS)
			}
		}

		/**
		 * Figure out what outer variables will need to be captured when a
		 * function for me is built.
		 *
		 * @param self
		 *   The block phrase to analyze.
		 */
		private fun collectNeededVariablesOfOuterBlocks(self: A_Phrase) {
			val providedByMe = allLocallyDefinedVariables(self).toSet()
			val neededDeclarationsSet = mutableSetOf<A_Phrase>()
			val neededDeclarations = mutableListOf<A_Phrase>()
			recurse(self) {
				parent: A_Phrase, again: Continuation1NotNull<A_Phrase> ->
				parent.childrenDo(Consumer {
					child: A_Phrase ->
					when {
						child.phraseKindIsUnder(BLOCK_PHRASE) ->
							for (declaration in child.neededVariables()) {
								if (!providedByMe.contains(declaration)
									&& !neededDeclarationsSet.contains(
										declaration))
								{
									neededDeclarationsSet.add(declaration)
									neededDeclarations.add(declaration)
								}
							}
						child.phraseKindIsUnder(VARIABLE_USE_PHRASE) -> {
							val declaration = child.declaration()
							if (!providedByMe.contains(declaration)
								&& declaration.declarationKind()
									!== DeclarationKind.MODULE_VARIABLE
								&& declaration.declarationKind()
									!== DeclarationKind.MODULE_CONSTANT
								&& !neededDeclarationsSet.contains(declaration))
							{
								neededDeclarationsSet.add(declaration)
								neededDeclarations.add(declaration)
							}
							// Avoid visiting the declaration explicitly,
							// otherwise uses of declarations that have
							// initializations will cause variables used in
							// those initializations to accidentally be captured
							// as well.
						}
						else -> again.value(child)
					}
				})
			}
			self.neededVariables(tupleFromList(neededDeclarations))
		}

		/** The mutable [BlockPhraseDescriptor].  */
		private val mutable = BlockPhraseDescriptor(Mutability.MUTABLE)

		/** The shared [BlockPhraseDescriptor].  */
		private val shared = BlockPhraseDescriptor(Mutability.SHARED)
	}
}