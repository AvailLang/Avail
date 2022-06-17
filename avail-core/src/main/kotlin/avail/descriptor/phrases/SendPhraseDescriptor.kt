/*
 * SendPhraseDescriptor.kt
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
package avail.descriptor.phrases

import avail.compiler.AvailCodeGenerator
import avail.descriptor.atoms.A_Atom
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.bundles.A_Bundle.Companion.messageSplitter
import avail.descriptor.bundles.MessageBundleDescriptor
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_Method.Companion.numArgs
import avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import avail.descriptor.phrases.A_Phrase.Companion.bundle
import avail.descriptor.phrases.A_Phrase.Companion.emitAllValuesOn
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.superUnionType
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.phrases.SendPhraseDescriptor.ObjectSlots.ARGUMENTS_LIST_NODE
import avail.descriptor.phrases.SendPhraseDescriptor.ObjectSlots.BUNDLE
import avail.descriptor.phrases.SendPhraseDescriptor.ObjectSlots.RETURN_TYPE
import avail.descriptor.phrases.SendPhraseDescriptor.ObjectSlots.TOKENS
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine4
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tokens.A_Token
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.TypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.MESSAGE_BUNDLE
import avail.descriptor.types.TypeTag
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * My instances represent invocations of multi-methods in Avail code.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class SendPhraseDescriptor private constructor(
	mutability: Mutability
) : PhraseDescriptor(
	mutability,
	TypeTag.SEND_PHRASE_TAG,
	ObjectSlots::class.java,
	null
) {
	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [tuple][A_Tuple] of [tokens][A_Token] that comprise this
		 * [send][SendPhraseDescriptor].
		 */
		TOKENS,

		/**
		 * A [list&#32;phrase][ListPhraseDescriptor] containing the expressions
		 * that yield the arguments of the method invocation.
		 */
		ARGUMENTS_LIST_NODE,

		/**
		 * The [message&#32;bundle][MessageBundleDescriptor] that this send was
		 * intended to invoke.  Technically, it's the [A_Method] inside the
		 * bundle that will be invoked, so the bundle gets stripped off when
		 * generating a raw function from a
		 * [block&#32;phrase][BlockPhraseDescriptor] containing this send.
		 */
		BUNDLE,

		/**
		 * What [type][TypeDescriptor] of value this method invocation must
		 * return.
		 */
		RETURN_TYPE
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		self.bundle.messageSplitter.printSendNodeOnIndent(
			self, builder, indent)
	}

	override fun o_ApparentSendName(self: AvailObject): A_Atom =
		self.slot(BUNDLE).message

	override fun o_ArgumentsListNode(self: AvailObject): A_Phrase =
		self.slot(ARGUMENTS_LIST_NODE)

	override fun o_Bundle(self: AvailObject): A_Bundle = self.slot(BUNDLE)

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase) -> Unit
	) = action(self.slot(ARGUMENTS_LIST_NODE))

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase
	) = self.setSlot(ARGUMENTS_LIST_NODE,
		transformer(self.slot(ARGUMENTS_LIST_NODE)))

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) {
		val bundle: A_Bundle = self.slot(BUNDLE)
		val argCount: Int = bundle.bundleMethod.numArgs
		val arguments: A_Phrase = self.slot(ARGUMENTS_LIST_NODE)
		arguments.emitAllValuesOn(codeGenerator)
		val superUnionType = arguments.superUnionType
		when {
			superUnionType.isBottom -> codeGenerator.emitCall(
				self.tokens, argCount, bundle, self.phraseExpressionType)
			else -> codeGenerator.emitSuperCall(
				self.tokens,
				argCount,
				bundle,
				self.phraseExpressionType,
				superUnionType)
		}
	}

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean = (!aPhrase.isMacroSubstitutionNode
		&& self.phraseKind == aPhrase.phraseKind
		&& self.slot(BUNDLE).equals(aPhrase.bundle)
		&& self.slot(ARGUMENTS_LIST_NODE).equals(aPhrase.argumentsListNode)
		&& self.slot(RETURN_TYPE).equals(aPhrase.phraseExpressionType))

	override fun o_PhraseExpressionType(self: AvailObject): A_Type =
		self.slot(RETURN_TYPE)

	override fun o_Hash(self: AvailObject): Int = combine4(
		self.slot(ARGUMENTS_LIST_NODE).hash(),
		self.slot(BUNDLE).hash(),
		self.slot(RETURN_TYPE).hash(),
		-0x6f1c64b3)

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		PhraseKind.SEND_PHRASE

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.SEND_PHRASE

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	): Unit = unsupported

	override fun o_Tokens(self: AvailObject): A_Tuple = self.slot(TOKENS)

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) {
		// Do nothing.
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("send phrase") }
			at("tokens") { self.slot(TOKENS).writeTo(writer) }
			at("arguments") { self.slot(ARGUMENTS_LIST_NODE).writeTo(writer) }
			at("bundle") { self.slot(BUNDLE).writeTo(writer) }
			at("return type") { self.slot(RETURN_TYPE).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("send phrase") }
			at("arguments") {
				self.slot(ARGUMENTS_LIST_NODE).writeSummaryTo(writer)
			}
			at("bundle") { self.slot(BUNDLE).writeSummaryTo(writer) }
			at("return type") { self.slot(RETURN_TYPE).writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	override fun shared() = shared

	companion object {
		/**
		 * Create a new [send#&#32;phrase][SendPhraseDescriptor] from the
		 * specified [A_Bundle], [list#&#32;phrase][ListPhraseDescriptor] of
		 * argument expressions, and return [type][TypeDescriptor].  Also take
		 * a [tuple][A_Tuple] of [tokens][A_Token].
		 *
		 * @param tokens
		 *   The [tuple][A_Tuple] of [tokens][A_Token] that comprise the
		 *   [send][SendPhraseDescriptor].
		 * @param bundle
		 *   The method bundle for which this represents an invocation.
		 * @param argsListNode
		 *   A [list][ListPhraseDescriptor] phrase of argument expressions.
		 * @param returnType
		 *   The target method's expected return type.
		 * @return
		 *   A new send phrase.
		 */
		fun newSendNode(
			tokens: A_Tuple,
			bundle: A_Bundle,
			argsListNode: A_Phrase,
			returnType: A_Type
		): A_Phrase {
			assert(bundle.isInstanceOfKind(MESSAGE_BUNDLE.o))
			assert(argsListNode.phraseKindIsUnder(PhraseKind.LIST_PHRASE))
			return mutable.createShared {
				setSlot(TOKENS, tokens)
				setSlot(ARGUMENTS_LIST_NODE, argsListNode)
				setSlot(BUNDLE, bundle)
				setSlot(RETURN_TYPE, returnType)
			}
		}

		/** The mutable [SendPhraseDescriptor]. */
		private val mutable = SendPhraseDescriptor(Mutability.MUTABLE)

		/** The shared [SendPhraseDescriptor]. */
		private val shared = SendPhraseDescriptor(Mutability.SHARED)
	}
}
