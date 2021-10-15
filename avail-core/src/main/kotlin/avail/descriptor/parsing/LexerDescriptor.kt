/*
 * LexerDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package avail.descriptor.parsing

import avail.compiler.scanning.LexingState
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.functions.A_Function
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_Method.Companion.bundles
import avail.descriptor.methods.A_Method.Companion.lexer
import avail.descriptor.methods.MacroDescriptor
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.addLexer
import avail.descriptor.parsing.A_Lexer.Companion.lexerMethod
import avail.descriptor.parsing.LexerDescriptor.IntegerSlots.Companion.HASH
import avail.descriptor.parsing.LexerDescriptor.IntegerSlots.LATIN1_BIT_VECTORS_
import avail.descriptor.parsing.LexerDescriptor.ObjectSlots.DEFINITION_MODULE
import avail.descriptor.parsing.LexerDescriptor.ObjectSlots.LEXER_BODY_FUNCTION
import avail.descriptor.parsing.LexerDescriptor.ObjectSlots.LEXER_FILTER_FUNCTION
import avail.descriptor.parsing.LexerDescriptor.ObjectSlots.LEXER_METHOD
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractSlotsEnum
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine5
import avail.descriptor.representation.BitField
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tokens.A_Token
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.LEXER
import avail.descriptor.types.TypeTag
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * A method maintains all definitions that have the same name.  At compile time
 * a name is looked up and the corresponding method is stored as a literal in
 * the object code for a call site.  At runtime the actual function is located
 * within the method and then invoked.  The methods also keep track of
 * bidirectional dependencies, so that a change of membership causes an
 * immediate invalidation of optimized level two code that depends on the
 * previous membership.
 *
 * Methods and macros are stored in separate lists.  Note that macros may be
 * polymorphic (multiple [definitions][MacroDescriptor]), and a lookup
 * structure is used at compile time to decide which macro is most specific.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class LexerDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability,
	TypeTag.LEXER_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * [BitField]s for the hash and the argument count.  See below.
		 */
		HASH_AND_MORE,

		/**
		 * Eight longs, indicating applicability of the Latin-1 characters (the
		 * single-byte Unicode codepoints).  See [o_LexerApplicability] and
		 * [o_SetLexerApplicability] for details of which bits encode the
		 * filter values and whether the filter has run.
		 */
		LATIN1_BIT_VECTORS_;

		companion object {
			/**
			 * The hash of this lexer.  Set during construction.
			 */
			val HASH = BitField(HASH_AND_MORE, 0, 32)
		}
	}

	/**
	 * The fields that are of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/** The [A_Method] in which this lexer is defined. */
		LEXER_METHOD,

		/**
		 * The module in which this lexer was defined.  Other modules cannot see
		 * this lexer (i.e., it doesn't get invoke to produce tuples) unless the
		 * lexer's definition module is an ancestor of the module being
		 * compiled.
		 */
		DEFINITION_MODULE,

		/**
		 * The function to run (as the base call of a fiber), with the character
		 * at the current lexing point, to determine if the body function should
		 * be attempted.
		 */
		LEXER_FILTER_FUNCTION,

		/**
		 * The function to run (as the base call of a fiber) to generate some
		 * tokens from the source string and position.  The function should
		 * produce a tuple of potential [A_Token]s at this position, as produced
		 * by this lexer.  Each token may be seeded with the potential tokens
		 * that follow it.  Since each token also records the [LexingState]
		 * after it, there's no need to produce that separately.
		 */
		LEXER_BODY_FUNCTION
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	): Boolean = e === LATIN1_BIT_VECTORS_

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		self.lexerMethod.bundles.joinTo(
			buffer = builder,
			separator = " a.k.a. ",
			prefix = "Lexer for "
		) {
			it.message.toString()
		}
	}

	override fun o_DefinitionModule(self: AvailObject): A_Module =
		self.slot(DEFINITION_MODULE)

	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean = another.traversed().sameAddressAs(self)

	override fun o_Hash(self: AvailObject): Int = self.slot(HASH)

	override fun o_Kind(self: AvailObject): A_Type = LEXER.o

	/**
	 * Answer either `null` if the filter has not yet run for this Latin1
	 * (`[0..255]`) code point, `true` if it has passed, or `false` if it has
	 * failed.  These are cached in the 8 [Long]s of [LATIN1_BIT_VECTORS_], in
	 * pairs of bits where the upper is the validity bit, and the lower is the
	 * previous result of the filter.
	 *
	 * @param self
	 *   The [A_Lexer].
	 * @param codePoint
	 *   The Latin1 (`[0..255]`) Unicode code point to look up.
	 * @return
	 *   Either `null` for undecided, or a [Boolean] indicating a previously
	 *   cached result of having run the lexer's filter for this code point.
	 */
	override fun o_LexerApplicability(
		self: AvailObject,
		codePoint: Int
	): Boolean?
	{
		assert(codePoint and 255.inv() == 0)
		val offset = (codePoint ushr 5) + 1
		val shift = (codePoint and 31) shl 1
		val long = self.mutableSlot(LATIN1_BIT_VECTORS_, offset)
		return when (long ushr shift and 3)
		{
			0b00L -> null
			0b10L -> false
			0b11L -> true
			else -> throw RuntimeException("Invalid filter cache encoding")
		}
	}

	override fun o_LexerBodyFunction(self: AvailObject): A_Function =
		self.slot(LEXER_BODY_FUNCTION)

	override fun o_LexerFilterFunction(self: AvailObject): A_Function =
		self.slot(LEXER_FILTER_FUNCTION)

	override fun o_LexerMethod(self: AvailObject): A_Method =
		self.slot(LEXER_METHOD)

	// A method is always shared. Never make it immutable.
	override fun o_MakeImmutable(self: AvailObject): AvailObject =
		when {
			isMutable -> self.makeShared()
			else -> self
		}

	/**
	 * @see [o_LexerApplicability].
	 */
	override fun o_SetLexerApplicability(
		self: AvailObject,
		codePoint: Int,
		applicability: Boolean)
	{
		assert(codePoint and 255.inv() == 0)
		val offset = (codePoint ushr 5) + 1
		val shift = (codePoint and 31) shl 1
		self.atomicUpdateSlot(LATIN1_BIT_VECTORS_, offset) {
			this or ((0b10L + if (applicability) 0b01L else 0b00L) shl shift)
		}
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("lexer") }
			at("filter") { self.slot(LEXER_FILTER_FUNCTION).writeTo(writer) }
			at("body") { self.slot(LEXER_BODY_FUNCTION).writeTo(writer) }
			at("method") { self.slot(LEXER_METHOD).writeTo(writer) }
			at("module") { self.slot(DEFINITION_MODULE).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("lexer") }
			at("filter") {
				self.slot(LEXER_FILTER_FUNCTION).writeSummaryTo(writer)
			}
			at("body") { self.slot(LEXER_BODY_FUNCTION).writeSummaryTo(writer) }
			at("method") { self.slot(LEXER_METHOD).writeSummaryTo(writer) }
			at("module") { self.slot(DEFINITION_MODULE).writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	// There is no immutable descriptor. Use the shared one.
	override fun immutable() = shared

	override fun shared() = shared

	companion object
	{
		private val lexerFilterFunctionType: A_Type = functionType(
			tuple(Types.CHARACTER.o),
			booleanType
		).makeShared()

		fun lexerFilterFunctionType(): A_Type = lexerFilterFunctionType

		private val lexerBodyFunctionType: A_Type = functionType(
			tuple(
				stringType,
				naturalNumbers,
				naturalNumbers),
			setTypeForSizesContentType(
				wholeNumbers,
				oneOrMoreOf(Types.TOKEN.o))
		).makeShared()

		fun lexerBodyFunctionType(): A_Type = lexerBodyFunctionType

		/**
		 * Answer a new, fully populated lexer.  Also install it in the given
		 * module and method.  Note that the references from the lexer to the
		 * module and method should be considered back-pointers.
		 *
		 * @param lexerFilterFunction
		 *   A function that tests the character at the current lexing point to
		 *   determine whether to run the body of this lexer.
		 * @param lexerBodyFunction
		 *   The function that creates runs of tokens from source code.
		 * @param lexerMethod
		 *   The method associated with the lexer.
		 * @param definitionModule
		 *   The module in which the lexer is defined.
		 * @return
		 *   A new method with no name.
		 */
		fun newLexer(
			lexerFilterFunction: A_Function,
			lexerBodyFunction: A_Function?,
			lexerMethod: A_Method,
			definitionModule: A_Module
		): A_Lexer
		{
			val lexer = mutable.createShared(8) {
				setSlot(LEXER_FILTER_FUNCTION, lexerFilterFunction)
				setSlot(LEXER_BODY_FUNCTION, lexerBodyFunction!!)
				setSlot(LEXER_METHOD, lexerMethod)
				setSlot(DEFINITION_MODULE, definitionModule)
				val hash = combine5(
					lexerFilterFunction.hash(),
					lexerFilterFunction.hash(),
					lexerMethod.hash(),
					definitionModule.hash(),
					-0x463e0e17)
				setSlot(HASH, hash)
			}
			lexerMethod.lexer = lexer
			if (definitionModule.notNil)
			{
				definitionModule.addLexer(lexer)
			}
			return lexer
		}

		/** The mutable [LexerDescriptor]. */
		private val mutable = LexerDescriptor(Mutability.MUTABLE)

		/** The shared [LexerDescriptor]. */
		private val shared = LexerDescriptor(Mutability.SHARED)
	}
}
