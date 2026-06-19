/*
 * DeclarationPhraseDescriptor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.phrases
import avail.compiler.AvailCodeGenerator
import avail.compiler.CompilationContext
import avail.descriptor.methods.StylerDescriptor.SystemStyle
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.ARGUMENT
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LABEL
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_CONSTANT
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_VARIABLE
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.MODULE_CONSTANT
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.MODULE_VARIABLE
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.PRIMITIVE_FAILURE_REASON
import avail.descriptor.phrases.DeclarationPhraseDescriptor.ObjectSlots.DECLARED_TYPE
import avail.descriptor.phrases.DeclarationPhraseDescriptor.ObjectSlots.INITIALIZATION_EXPRESSION
import avail.descriptor.phrases.DeclarationPhraseDescriptor.ObjectSlots.LITERAL_OBJECT
import avail.descriptor.phrases.DeclarationPhraseDescriptor.ObjectSlots.TOKEN
import avail.descriptor.phrases.DeclarationPhraseDescriptor.ObjectSlots.TYPE_EXPRESSION
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Phrase
import avail.descriptor.representation.A_Phrase.Companion.declaredType
import avail.descriptor.representation.A_Phrase.Companion.emitEffectOn
import avail.descriptor.representation.A_Phrase.Companion.emitValueOn
import avail.descriptor.representation.A_Phrase.Companion.initializationExpression
import avail.descriptor.representation.A_Phrase.Companion.literalObject
import avail.descriptor.representation.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.representation.A_Phrase.Companion.phraseKind
import avail.descriptor.representation.A_Phrase.Companion.token
import avail.descriptor.representation.A_Phrase.Companion.tokens
import avail.descriptor.representation.A_Phrase.Companion.typeExpression
import avail.descriptor.representation.A_String
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Token
import avail.descriptor.representation.A_Tuple
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.functionType
import avail.descriptor.representation.A_Type.Companion.readType
import avail.descriptor.representation.A_Type.Companion.returnType
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.error
import avail.descriptor.representation.IntegerEnumSlotDescriptionEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tokens.TokenDescriptor
import avail.descriptor.tuples.StringDescriptor
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.ContinuationTypeDescriptor
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TypeDescriptor
import avail.descriptor.variables.VariableDescriptor
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * My instances represent variable and constant declaration statements.  There
 * are several [kinds][DeclarationKind] of declarations, some with initializing
 * expressions and some with type expressions.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @property declarationKind
 *   The [kind][DeclarationKind] of declaration using this descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class DeclarationPhraseDescriptor(
	mutability: Mutability,
	private val declarationKind: DeclarationKind
) : PhraseDescriptor(
	mutability,
	declarationKind.phraseKind().typeTag,
	ObjectSlots::class.java)
{
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [token][TokenDescriptor] containing the name of the entity being
		 * declared.
		 */
		TOKEN,

		/**
		 * The [type][TypeDescriptor] of the variable being declared.
		 */
		DECLARED_TYPE,

		/**
		 * The [expression][PhraseDescriptor] that produced the type for the
		 * entity being declared, or [nil] if there was no such expression.
		 */
		TYPE_EXPRESSION,

		/**
		 * The optional [initialization][PhraseDescriptor], or [nil] otherwise.
		 * Not applicable to all kinds of declarations.
		 */
		INITIALIZATION_EXPRESSION,

		/**
		 * The [AvailObject] held directly by this declaration. It can be either
		 * a module constant value or a module variable.
		 */
		LITERAL_OBJECT
	}

	/**
	 * These are the kinds of arguments, variables, constants, and labels that
	 * can be declared.  There are also optional initializing expressions, fixed
	 * values (for module constants), and fixed variable objects (for module
	 * variables).
	 *
	 * @constructor
	 * @property nativeKindName
	 *   A Java [String] describing this kind of declaration.
	 * @property isVariable
	 *   Whether this entity can be modified.
	 * @property isModuleScoped
	 *   Whether this entity occurs at the module scope.
	 * @property kindEnumeration
	 *   The instance of the enumeration [PhraseKind] that is associated with
	 *   this kind of declaration.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	enum class DeclarationKind(
			private val nativeKindName: String,
			val isVariable: Boolean,
			val isModuleScoped: Boolean,
			private val kindEnumeration: PhraseKind,
			val defaultDefinitionStyle: SystemStyle,
			val defaultUseStyle: SystemStyle
	) : IntegerEnumSlotDescriptionEnum
	{
		/** An argument to a block. */
		ARGUMENT(
			nativeKindName = "argument",
			isVariable = false,
			isModuleScoped = false,
			kindEnumeration = PhraseKind.ARGUMENT_PHRASE,
			defaultDefinitionStyle = SystemStyle.PARAMETER_DEFINITION,
			defaultUseStyle = SystemStyle.PARAMETER_USE)
		{
			override fun emitVariableValueForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) = codeGenerator.emitPushLocalOrOuter(tokens, declarationNode)

			override fun print(
				self: A_Phrase,
				builder: StringBuilder,
				recursionMap: IdentityHashMap<A_BasicObject, Unit>,
				indent: Int
			) {
				builder.append(self.token.string().asNativeString())
				builder.append(" : ")
				printTypePartOf(self, builder, recursionMap, indent + 1)
			}
		},

		/** A label declaration at the start of a block. */
		LABEL(
			nativeKindName = "label",
			isVariable = false,
			isModuleScoped = false,
			kindEnumeration = PhraseKind.LABEL_PHRASE,
			defaultDefinitionStyle = SystemStyle.LABEL_DEFINITION,
			defaultUseStyle = SystemStyle.LABEL_USE)
		{
			/**
			 * Let the code generator know that the label occurs at the current
			 * code position.
			 */
			override fun emitEffectForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) = codeGenerator.emitLabelDeclaration(declarationNode)

			override fun emitVariableValueForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) = codeGenerator.emitPushLocalOrOuter(tokens, declarationNode)

			override fun print(
				self: A_Phrase,
				builder: StringBuilder,
				recursionMap: IdentityHashMap<A_BasicObject, Unit>,
				indent: Int
			) {
				builder.append('$')
				builder.append(self.token.string().asNativeString())
				builder.append(" : ")
				val typeExpression: A_Phrase = self.typeExpression
				if (typeExpression.notNil) {
					typeExpression.printOnAvoidingIndent(
						builder, recursionMap, indent + 1)
				}
				else
				{
					// Output the continuation type's return type, since that's
					// what get specified syntactically.
					val functionType = self.declaredType.functionType
					functionType.returnType.printOnAvoidingIndent(
						builder, recursionMap, indent + 1)
				}
			}
		},

		/** A local variable, declared within a block. */
		LOCAL_VARIABLE(
			nativeKindName = "local variable",
			isVariable = true,
			isModuleScoped = false,
			kindEnumeration = PhraseKind.LOCAL_VARIABLE_PHRASE,
			defaultDefinitionStyle = SystemStyle.LOCAL_VARIABLE_DEFINITION,
			defaultUseStyle = SystemStyle.LOCAL_VARIABLE_USE)
		{
			override fun emitEffectForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) {
				val expr: A_Phrase = declarationNode.initializationExpression
				if (expr.notNil) {
					expr.emitValueOn(codeGenerator)
					codeGenerator.emitSetLocalOrOuter(tokens, declarationNode)
				}
			}

			override fun emitVariableAssignmentForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) = codeGenerator.emitSetLocalOrOuter(tokens, declarationNode)

			override fun emitVariableReferenceForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) = codeGenerator.emitPushLocalOrOuter(tokens, declarationNode)

			override fun emitVariableValueForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) = codeGenerator.emitGetLocalOrOuter(tokens, declarationNode)

			override fun print(
				self: A_Phrase,
				builder: StringBuilder,
				recursionMap: IdentityHashMap<A_BasicObject, Unit>,
				indent: Int
			) {
				builder.append(self.token.string().asNativeString())
				builder.append(" : ")
				printTypePartOf(self, builder, recursionMap, indent + 1)
				if (self.initializationExpression.notNil) {
					builder.append(" := ")
					self.initializationExpression.printOnAvoidingIndent(
						builder, recursionMap, indent + 1)
				}
			}
		},

		/** A local constant, declared within a block. */
		LOCAL_CONSTANT(
			nativeKindName = "local constant",
			isVariable = false,
			isModuleScoped = false,
			kindEnumeration = PhraseKind.LOCAL_CONSTANT_PHRASE,
			defaultDefinitionStyle = SystemStyle.LOCAL_CONSTANT_DEFINITION,
			defaultUseStyle = SystemStyle.LOCAL_CONSTANT_USE)
		{
			override fun emitEffectForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) {
				declarationNode.initializationExpression
					.emitValueOn(codeGenerator)
				codeGenerator.emitSetLocalFrameSlot(tokens, declarationNode)
			}

			override fun emitVariableValueForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) = codeGenerator.emitPushLocalOrOuter(tokens, declarationNode)

			override fun print(
				self: A_Phrase,
				builder: StringBuilder,
				recursionMap: IdentityHashMap<A_BasicObject, Unit>,
				indent: Int
			) {
				builder.append(self.token.string().asNativeString())
				builder.append(" ::= ")
				self.initializationExpression.printOnAvoidingIndent(
					builder, recursionMap, indent + 1)
			}
		},

		/** A variable declared at the outermost (module) scope. */
		MODULE_VARIABLE(
			nativeKindName = "module variable",
			isVariable = true,
			isModuleScoped = true,
			kindEnumeration = PhraseKind.MODULE_VARIABLE_PHRASE,
			defaultDefinitionStyle = SystemStyle.MODULE_VARIABLE_DEFINITION,
			defaultUseStyle = SystemStyle.MODULE_VARIABLE_USE)
		{
			override fun emitVariableAssignmentForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) = codeGenerator.emitSetLiteral(
				tokens, declarationNode.literalObject)

			override fun emitVariableReferenceForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) = codeGenerator.emitPushLiteral(
				tokens, declarationNode.literalObject)

			override fun emitVariableValueForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) {
				// Technically, that's the declaration, not the use, but it
				// should do for now.
				codeGenerator.emitGetLiteral(
					declarationNode.tokens, declarationNode.literalObject)
			}

			override fun print(
				self: A_Phrase,
				builder: StringBuilder,
				recursionMap: IdentityHashMap<A_BasicObject, Unit>,
				indent: Int
			) {
				builder.append(self.token.string().asNativeString())
				builder.append(" : ")
				printTypePartOf(self, builder, recursionMap, indent + 1)
				if (self.initializationExpression.notNil) {
					builder.append(" := ")
					self.initializationExpression.printOnAvoidingIndent(
						builder, recursionMap, indent + 1)
				}
			}
		},

		/** A constant declared at the outermost (module) scope. */
		MODULE_CONSTANT(
			nativeKindName = "module constant",
			isVariable = false,
			isModuleScoped = true,
			kindEnumeration = PhraseKind.MODULE_CONSTANT_PHRASE,
			defaultDefinitionStyle = SystemStyle.MODULE_CONSTANT_DEFINITION,
			defaultUseStyle = SystemStyle.MODULE_CONSTANT_USE)
		{
			override fun emitVariableValueForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) = codeGenerator.emitGetLiteral(
				tokens, declarationNode.literalObject)

			override fun print(
				self: A_Phrase,
				builder: StringBuilder,
				recursionMap: IdentityHashMap<A_BasicObject, Unit>,
				indent: Int
			) {
				builder.append(self.token.string().asNativeString())
				builder.append(" ::= ")
				self.initializationExpression.printOnAvoidingIndent(
					builder, recursionMap, indent + 1)
			}
		},

		/** A local constant, declared within a block. */
		PRIMITIVE_FAILURE_REASON(
			nativeKindName = "primitive failure reason",
			isVariable = false,
			isModuleScoped = false,
			kindEnumeration = PhraseKind.PRIMITIVE_FAILURE_REASON_PHRASE,
			defaultDefinitionStyle =
				SystemStyle.PRIMITIVE_FAILURE_REASON_DEFINITION,
			defaultUseStyle = SystemStyle.PRIMITIVE_FAILURE_REASON_USE)
		{
			override fun emitVariableValueForOn(
				tokens: A_Tuple,
				declarationNode: A_Phrase,
				codeGenerator: AvailCodeGenerator
			) = codeGenerator.emitPushLocalOrOuter(tokens, declarationNode)

			override fun print(
				self: A_Phrase,
				builder: StringBuilder,
				recursionMap: IdentityHashMap<A_BasicObject, Unit>,
				indent: Int
			) {
				builder.append(self.token.string().asNativeString())
				builder.append(" : ")
				printTypePartOf(self, builder, recursionMap, indent + 1)
			}
		};

		override val fieldName get() = name

		override val fieldOrdinal get() = ordinal

		/**
		 * An Avail [string][StringDescriptor] describing this kind of
		 * declaration.
		 */
		private val kindName: A_String = stringFrom(nativeKindName).makeShared()

		/**
		 * Return the instance of the enumeration [PhraseKind] that is
		 * associated with this kind of declaration.
		 *
		 * @return
		 *   The associated [PhraseKind] enumeration value.
		 */
		fun phraseKind(): PhraseKind = kindEnumeration

		/**
		 * Answer a Java [String] describing this kind of declaration.
		 *
		 * @return
		 *   A Java String.
		 */
		fun nativeKindName(): String = nativeKindName

		/**
		 * Return an Avail [string][StringDescriptor] describing this kind
		 * of declaration.
		 *
		 * @return
		 *   The description of this [PhraseKind].
		 */
		fun kindName(): A_String = kindName

		/**
		 * Emit an assignment to this variable.
		 *
		 * @param tokens
		 *   The [A_Tuple] of [A_Token]s associated with this call.
		 * @param declarationNode
		 *   The declaration that has this declarationKind.
		 * @param codeGenerator
		 *   Where to generate the assignment.
		 */
		open fun emitVariableAssignmentForOn(
			tokens: A_Tuple,
			declarationNode: A_Phrase,
			codeGenerator: AvailCodeGenerator
		): Unit = error("Cannot assign to this $name")

		/**
		 * Emit a reference to this variable.
		 *
		 * @param tokens
		 *   The [A_Tuple] of [A_Token]s associated with this call.
		 * @param declarationNode
		 *   The declaration that has this declarationKind.
		 * @param codeGenerator
		 *   Where to emit the reference to this variable.
		 */
		open fun emitVariableReferenceForOn(
			tokens: A_Tuple,
			declarationNode: A_Phrase,
			codeGenerator: AvailCodeGenerator
		): Unit = error("Cannot take a reference to this $name")

		/**
		 * Emit a use of this variable.
		 *
		 * @param tokens
		 *   The [A_Tuple] of [A_Token]s associated with this call.
		 * @param declarationNode
		 *   The declaration that has this [DeclarationKind].
		 * @param codeGenerator
		 *   Where to emit the use of this variable.
		 */
		open fun emitVariableValueForOn(
			tokens: A_Tuple,
			declarationNode: A_Phrase,
			codeGenerator: AvailCodeGenerator
		): Unit = error("Cannot extract the value of this $name")

		/**
		 * If this is an ordinary declaration then it was handled on a separate
		 * pass.  Do nothing by default.
		 *
		 * @param tokens
		 *   The [A_Tuple] of [A_Token]s associated with this call.
		 * @param declarationNode
		 *   The declaration phrase.
		 * @param codeGenerator
		 *   Where to emit the declaration.
		 */
		open fun emitEffectForOn(
			tokens: A_Tuple,
			declarationNode: A_Phrase,
			codeGenerator: AvailCodeGenerator
		) {
			// Declarations emit no instructions.
		}

		/**
		 * Print a declaration of this kind.
		 *
		 * @param self
		 *   The declaration.
		 * @param builder
		 *   Where to print.
		 * @param recursionMap
		 *   An [IdentityHashMap] of parent objects that are printing.
		 * @param indent
		 *   The indentation depth.
		 */
		abstract fun print(
			self: A_Phrase,
			builder: StringBuilder,
			recursionMap: IdentityHashMap<A_BasicObject, Unit>,
			indent: Int)

		companion object
		{
			/**
			 * Stash a copy of the array of all [DeclarationKind] enum values.
			 */
			private val all = entries.toTypedArray()

			/**
			 * Answer the previously stashed copy of the array of all
			 * [DeclarationKind] enum values.
			 *
			 * @param ordinal
			 *   The ordinal of the declaration kind to look up.
			 * @return
			 *   The requested [DeclarationKind].
			 */
			fun lookup(ordinal: Int): DeclarationKind = all[ordinal]

			/**
			 * Print the part of the given declaration of this kind which
			 * indicates the content type of the entity that is being declared.
			 *
			 * @param self
			 *   The declaration.
			 * @param builder
			 *   Where to print.
			 * @param recursionMap
			 *   An [IdentityHashMap] of parent objects that are printing.
			 * @param indent
			 *   The indentation depth.
			 */
			fun printTypePartOf(
				self: A_Phrase,
				builder: StringBuilder,
				recursionMap: IdentityHashMap<A_BasicObject, Unit>,
				indent: Int
			) = when (val typeExpression = self.typeExpression) {
				nil -> self.declaredType
				else -> typeExpression
			}.printOnAvoidingIndent(builder, recursionMap, indent)
		}
	}

	override fun o_ApplyStylesThen(
		self: AvailObject,
		context: CompilationContext,
		visitedSet: MutableSet<A_Phrase>,
		then: ()->Unit)
	{
		if (!visitedSet.add(self)) return then()
		styleDescendantsThen(self, context, visitedSet) {
			// The parser can't produce declarations of its own, but the
			// bootstrap (and other) macros can, so when we visit the output of
			// one of those macros, we can find declarations to style.  Which is
			// here.
			val style = self.declarationKind().defaultDefinitionStyle
			val token = self.token
			context.loader.styleToken(token, style.kotlinString)
			context.loader.addDeclarationToken(token)
			then()
		}
	}

	override fun o_Token(self: AvailObject): A_Token = self[TOKEN]

	override fun o_DeclaredType(self: AvailObject): A_Type =
		self[DECLARED_TYPE]

	override fun o_TypeExpression(self: AvailObject): A_Phrase =
		self[TYPE_EXPRESSION]

	override fun o_InitializationExpression(self: AvailObject): AvailObject =
		self[INITIALIZATION_EXPRESSION]

	override fun o_LiteralObject(self: AvailObject): A_BasicObject =
		self[LITERAL_OBJECT]

	override fun o_DeclarationKind(self: AvailObject): DeclarationKind =
		declarationKind

	override fun o_PhraseExpressionType(self: AvailObject): A_Type = TOP()

	/**
	 * This is a declaration, so it was handled on a separate pass.  Do nothing.
	 *
	 * @param codeGenerator
	 *   Where to emit the declaration.
	 */
	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self.declarationKind().emitEffectForOn(
		self.tokens, self, codeGenerator)

	/**
	 * This is a declaration, so it shouldn't generally produce a value.
	 */
	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		self.emitEffectOn(codeGenerator)
		codeGenerator.emitPushLiteral(emptyTuple, nil)
	}

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	) = self.sameAddressAs(aPhrase.traversed())

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase)->A_Phrase)
	{
		self.updateSlot(TYPE_EXPRESSION) { mapNotNil(transformer) }
		self.updateSlot(INITIALIZATION_EXPRESSION) { mapNotNil(transformer) }
	}

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase)->Unit)
	{
		self.typeExpression.ifNotNil(action)
		self.initializationExpression.ifNotNil(action)
	}

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	) = continuation(self)

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		self.declarationKind().phraseKind()

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.DECLARATION_PHRASE

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") {
				write(self.declarationKind().kindName().toString() + " phrase")
			}
			at("token") { self[TOKEN].writeTo(writer) }
			at("declared type") { self[DECLARED_TYPE].writeTo(writer) }
			val typeExpression = self[TYPE_EXPRESSION]
			if (typeExpression.notNil)
			{
				at("type expression") { typeExpression.writeTo(writer) }
			}
			val initializationExpression = self[INITIALIZATION_EXPRESSION]
			if (initializationExpression.notNil)
			{
				at("initialization expression") {
					initializationExpression.writeTo(writer)
				}
			}
			val literal = self[LITERAL_OBJECT]
			if (literal.notNil)
			{
				at("literal") { literal.writeTo(writer) }
			}
		}

	override fun o_NameForDebugger(self: AvailObject): String =
		super.o_NameForDebugger(self) + ": " + self.phraseKind

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Unit>,
		indent: Int
	) = self.declarationKind().print(self, builder, recursionMap, indent)

	companion object {
		/**
		 * Construct a declaration phrase of some [kind][DeclarationKind].
		 *
		 * @param declarationKind
		 *   The [kind][DeclarationKind] of
		 *   [declaration][DeclarationPhraseDescriptor] to create.
		 * @param token
		 *   The [token][TokenDescriptor] that is the defining occurrence of the
		 *   name of the entity being declared.
		 * @param declaredType
		 *   The [type][TypeDescriptor] of the entity being declared.
		 * @param typeExpression
		 *   The [expression][PhraseDescriptor] that produced the type for the
		 *   entity being declared, or [nil] if there was no such expression.
		 * @param initializationExpression
		 *   An [expression][PhraseDescriptor] used for initializing the entity
		 *   being declared, or [nil] if none.
		 * @param literalObject
		 *   An [AvailObject] that is the actual variable or constant being
		 *   defined, or [nil] if none.
		 * @return
		 *   The new declaration phrase.
		 */
		fun newDeclaration(
			declarationKind: DeclarationKind,
			token: A_Token,
			declaredType: A_Type,
			typeExpression: A_Phrase,
			initializationExpression: A_Phrase,
			literalObject: A_BasicObject
		): A_Phrase
		{
			assert(declaredType.isType)
			assert(token.isInstanceOf(Types.TOKEN()))
			assert(initializationExpression.isNil
				|| initializationExpression.isInstanceOfKind(
					PhraseKind.EXPRESSION_PHRASE.create(Types.ANY())))
			assert(literalObject.isNil
				|| declarationKind === MODULE_VARIABLE
				|| declarationKind === MODULE_CONSTANT)
			return mutables[declarationKind.ordinal].createShared {
				setSlot(TOKEN, token)
				setSlot(DECLARED_TYPE, declaredType)
				setSlot(TYPE_EXPRESSION, typeExpression)
				setSlot(INITIALIZATION_EXPRESSION, initializationExpression)
				setSlot(LITERAL_OBJECT, literalObject)
				initHash()
			}
		}

		/**
		 * Construct a new declaration of a block [DeclarationKind.ARGUMENT].
		 *
		 * @param token
		 *   The [token][TokenDescriptor] that is the defining occurrence of the
		 *   name of the entity being declared.
		 * @param declaredType
		 *   The [type][TypeDescriptor] of the entity being declared.
		 * @param typeExpression
		 *   The [expression][PhraseDescriptor] that produced the type for the
		 *   entity being declared, or [nil] if there was no such expression.
		 * @return
		 *   The argument declaration.
		 */
		fun newArgument(
			token: A_Token,
			declaredType: A_Type,
			typeExpression: A_Phrase
		): A_Phrase = newDeclaration(
			ARGUMENT,
			token,
			declaredType,
			typeExpression,
			nil,
			nil)

		/**
		 * Construct a new declaration of a [DeclarationKind.LOCAL_VARIABLE].
		 *
		 * @param token
		 *   The [token][TokenDescriptor] that is the defining occurrence of the
		 *   name of the local variable being declared.
		 * @param declaredType
		 *   The inner [type][TypeDescriptor] of the local variable being
		 *   declared.
		 * @param typeExpression
		 *   The [expression][PhraseDescriptor] that produced the type for the
		 *   entity being declared, or [nil] if there was no such expression.
		 * @param initializationExpression
		 *   An [expression][PhraseDescriptor] used for initializing the local
		 *   variable, or [nil] if none.
		 * @return
		 *   The new local variable declaration.
		 */
		fun newVariable(
			token: A_Token,
			declaredType: A_Type,
			typeExpression: A_Phrase,
			initializationExpression: A_Phrase
		): A_Phrase = newDeclaration(
			LOCAL_VARIABLE,
			token,
			declaredType,
			typeExpression,
			initializationExpression,
			nil)

		/**
		 * Construct a new declaration of a [DeclarationKind.LOCAL_CONSTANT].
		 *
		 * @param token
		 *   The [token][TokenDescriptor] that is the defining occurrence of the
		 *   name of the local constant being declared.
		 * @param initializationExpression
		 *   An [expression][PhraseDescriptor] used to provide the value of the
		 *   local constant.
		 * @return
		 *   The new local constant declaration.
		 */
		fun newConstant(
			token: A_Token,
			initializationExpression: A_Phrase
		): A_Phrase = newDeclaration(
			LOCAL_CONSTANT,
			token,
			initializationExpression.phraseExpressionType,
			nil,
			initializationExpression,
			nil)

		/**
		 * Construct a new declaration of a [PRIMITIVE_FAILURE_REASON]. This is
		 * set up automatically when a primitive fails, and the statements of
		 * the block cannot write to it.  It must occur as the first local
		 * constant, which is just after the arguments and local variables.
		 *
		 * @param token
		 *   The [token][TokenDescriptor] that is the defining occurrence of the
		 *   name of the local constant being declared.
		 * @param typeExpression
		 *   The [expression][PhraseDescriptor] that produced the type for the
		 *   entity being declared, or [nil] if there was no such expression.
		 * @param type
		 *   The [type][TypeDescriptor] of the primitive failure constant being
		 *   declared.
		 * @return
		 *   The new local constant declaration that will be automatically set
		 *   to the reason for a failed primitive.
		 */
		fun newPrimitiveFailureConstant(
			token: A_Token,
			typeExpression: A_Phrase,
			type: A_Type
		): A_Phrase = newDeclaration(
			PRIMITIVE_FAILURE_REASON,
			token,
			type,
			typeExpression,
			nil,
			nil)

		/**
		 * Construct a new declaration of a [DeclarationKind.LABEL].
		 *
		 * @param token
		 *   The [token][TokenDescriptor] that is the defining occurrence of the
		 *   name of the label being declared.
		 * @param returnTypeExpression
		 *   The [expression][PhraseDescriptor] that produced the type for the
		 *   entity being declared, or [nil] if there was no such expression.
		 *   Note that this expression produced the return type of the
		 *   continuation type, not the continuation type itself.
		 * @param declaredType
		 *   The [type][TypeDescriptor] of the label being declared, which must
		 *   be a [continuation&#32;type][ContinuationTypeDescriptor] whose
		 *   contained [function&#32;type][FunctionTypeDescriptor] agrees with
		 *   the block in which the label occurs.
		 * @return The new label declaration.
		 */
		fun newLabel(
			token: A_Token,
			returnTypeExpression: A_Phrase,
			declaredType: A_Type
		): A_Phrase = newDeclaration(
			LABEL,
			token,
			declaredType,
			returnTypeExpression,
			nil,
			nil)

		/**
		 * Construct a new declaration of a [DeclarationKind.MODULE_VARIABLE]
		 * with or without an initialization expression.
		 *
		 * @param token
		 *   The [token][TokenDescriptor] that is the defining occurrence of the
		 *   name of the module variable being declared.
		 * @param literalVariable
		 *   The actual [variable][VariableDescriptor] to be used as a module
		 *   variable.
		 * @param typeExpression
		 *   The [expression][PhraseDescriptor] that produced the type for the
		 *   entity being declared, or [nil] if there was no such expression.
		 * @param initializationExpression
		 *   The expression (or [nil]) used to initialize this module variable.
		 * @return
		 *   The new module variable declaration.
		 */
		fun newModuleVariable(
			token: A_Token,
			literalVariable: A_BasicObject,
			typeExpression: A_Phrase,
			initializationExpression: A_Phrase
		): A_Phrase = newDeclaration(
			MODULE_VARIABLE,
			token,
			literalVariable.kind().readType,
			typeExpression,
			initializationExpression,
			literalVariable)

		/**
		 * Construct a new declaration of a [DeclarationKind.MODULE_CONSTANT].
		 *
		 * @param token
		 *   The [token][TokenDescriptor] that is the defining occurrence of the
		 *   name of the module constant being declared.
		 * @param literalVariable
		 *   An actual [variable][VariableDescriptor] that the new module
		 *   constant uses to hold its value.
		 * @param initializationExpression
		 *   The expression used to initialize this module constant.
		 * @return
		 *   The new module constant declaration.
		 */
		fun newModuleConstant(
			token: A_Token,
			literalVariable: A_BasicObject,
			initializationExpression: A_Phrase
		): A_Phrase = newDeclaration(
			MODULE_CONSTANT,
			token,
			literalVariable.kind().readType,
			nil,
			initializationExpression,
			literalVariable)

		/** The mutable [DeclarationPhraseDescriptor]s. */
		private val mutables = DeclarationKind.entries.map {
			DeclarationPhraseDescriptor(Mutability.MUTABLE, it)
		}.toTypedArray()

		/** The immutable [DeclarationPhraseDescriptor]s. */
		private val immutables = DeclarationKind.entries.map {
			DeclarationPhraseDescriptor(Mutability.IMMUTABLE, it)
		}.toTypedArray()

		/** The shared [DeclarationPhraseDescriptor]s. */
		private val shareds = DeclarationKind.entries.map {
			DeclarationPhraseDescriptor(Mutability.SHARED, it)
		}.toTypedArray()
	}

	override fun mutable() = mutables[declarationKind.ordinal]

	override fun immutable() = immutables[declarationKind.ordinal]

	override fun shared() = shareds[declarationKind.ordinal]
}
