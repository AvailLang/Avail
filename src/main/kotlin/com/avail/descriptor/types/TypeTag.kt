/*
 * TypeTag.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.descriptor.types

/**
 * `TypeTag` is an enumeration that corresponds with the basic type
 * structure of Avail's type lattice.  Even though the type lattice contains an
 * infinite collection of infinitely long chains of types, some of which have
 * an infinite number of direct ancestors and direct descendants, we're still
 * able to extract a pragmatic tree of types from the lattice.
 *
 * Since this restricted set of types form a tree, they're defined in such an
 * order that all of a type's descendants follow it.  Since this is recursively
 * true, the types are effectively listed in depth-last order.  The ordinals are
 * assigned in the order of definition, but each type keeps track of the maximum
 * ordinal of all of its descendants (which occupy a contiguous span of ordinals
 * just after the type's ordinal).  We can test if type A is a subtype of B by
 * checking if a.ordinal ≥ b.ordinal and a.highOrdinal ≤ b.highOrdinal.  For a
 * proper subtype test, we turn the first condition into an inequality.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
enum class TypeTag
{
	UNKNOWN_TAG,
	TOP_TAG,
	NIL_TAG(TOP_TAG),
	ATOM_TAG(TOP_TAG),
	BOOLEAN_TAG(ATOM_TAG),
	TRUE_TAG(BOOLEAN_TAG),
	FALSE_TAG(BOOLEAN_TAG),
	BUNDLE_TAG(TOP_TAG),
	BUNDLE_TREE_TAG(TOP_TAG),
	CHARACTER_TAG(TOP_TAG),
	CONTINUATION_TAG(TOP_TAG),
	DEFINITION_TAG(TOP_TAG),
	FIBER_TAG(TOP_TAG),
	FUNCTION_TAG(TOP_TAG),
	GRAMMATICAL_RESTRICTION_TAG(TOP_TAG),
	LEXER_TAG(TOP_TAG),
	MACRO_TAG(TOP_TAG),
	MAP_TAG(TOP_TAG),
	MAP_LINEAR_BIN_TAG(TOP_TAG),
	MAP_HASHED_BIN_TAG(TOP_TAG),
	METHOD_TAG(TOP_TAG),
	MODULE_TAG(TOP_TAG),
	NUMBER_TAG(TOP_TAG),
	EXTENDED_INTEGER_TAG(NUMBER_TAG),
	INTEGER_TAG(EXTENDED_INTEGER_TAG),
	WHOLE_NUMBER_TAG(INTEGER_TAG),
	NATURAL_NUMBER_TAG(WHOLE_NUMBER_TAG),
	NEGATIVE_INFINITY_TAG(EXTENDED_INTEGER_TAG),
	POSITIVE_INFINITY_TAG(EXTENDED_INTEGER_TAG),
	FLOAT_TAG(NUMBER_TAG),
	DOUBLE_TAG(NUMBER_TAG),
	OBJECT_TAG(TOP_TAG),
	PARSING_PLAN_TAG(TOP_TAG),
	PARSING_PLAN_IN_PROGRESS_TAG(TOP_TAG),
	PHRASE_TAG(TOP_TAG),
	MARKER_PHRASE_TAG(PHRASE_TAG),
	EXPRESSION_PHRASE_TAG(PHRASE_TAG),
	ASSIGNMENT_PHRASE_TAG(EXPRESSION_PHRASE_TAG),
	BLOCK_PHRASE_TAG(EXPRESSION_PHRASE_TAG),
	LITERAL_PHRASE_TAG(EXPRESSION_PHRASE_TAG),
	REFERENCE_PHRASE_TAG(EXPRESSION_PHRASE_TAG),
	SUPER_CAST_PHRASE_TAG(EXPRESSION_PHRASE_TAG),
	SEND_PHRASE_TAG(EXPRESSION_PHRASE_TAG),
	LIST_PHRASE_TAG(EXPRESSION_PHRASE_TAG),
	PERMUTED_LIST_PHRASE_TAG(LIST_PHRASE_TAG),
	VARIABLE_USE_PHRASE_TAG(EXPRESSION_PHRASE_TAG),
	STATEMENT_PHRASE_TAG(PHRASE_TAG),
	SEQUENCE_PHRASE_TAG(STATEMENT_PHRASE_TAG),
	FIRST_OF_SEQUENCE_PHRASE_TAG(STATEMENT_PHRASE_TAG),
	DECLARATION_PHRASE_TAG(STATEMENT_PHRASE_TAG),
	ARGUMENT_PHRASE_TAG(DECLARATION_PHRASE_TAG),
	LABEL_PHRASE_TAG(DECLARATION_PHRASE_TAG),
	LOCAL_VARIABLE_PHRASE_TAG(DECLARATION_PHRASE_TAG),
	LOCAL_CONSTANT_PHRASE_TAG(DECLARATION_PHRASE_TAG),
	MODULE_VARIABLE_PHRASE_TAG(DECLARATION_PHRASE_TAG),
	MODULE_CONSTANT_PHRASE_TAG(DECLARATION_PHRASE_TAG),
	PRIMITIVE_FAILURE_REASON_PHRASE_TAG(DECLARATION_PHRASE_TAG),
	EXPRESSION_AS_STATEMENT_PHRASE_TAG(STATEMENT_PHRASE_TAG),
	MACRO_SUBSTITUTION_PHRASE_TAG(PHRASE_TAG),
	POJO_TAG(TOP_TAG),
	RAW_FUNCTION_TAG(TOP_TAG),
	SEMANTIC_RESTRICTION_TAG(TOP_TAG),
	SET_TAG(TOP_TAG),
	SET_LINEAR_BIN_TAG(TOP_TAG),
	SET_HASHED_BIN_TAG(TOP_TAG),
	TOKEN_TAG(TOP_TAG),
	LITERAL_TOKEN_TAG(TOKEN_TAG),
	TUPLE_TAG(TOP_TAG),
	STRING_TAG(TUPLE_TAG),
	VARIABLE_TAG(TOP_TAG),
	TOP_TYPE_TAG(TOP_TAG, TOP_TAG),
	ANY_TYPE_TAG(TOP_TYPE_TAG),
	NONTYPE_TYPE_TAG(ANY_TYPE_TAG),
	SET_TYPE_TAG(NONTYPE_TYPE_TAG, SET_TAG),
	POJO_TYPE_TAG(NONTYPE_TYPE_TAG, POJO_TAG),
	NUMBER_TYPE_TAG(NONTYPE_TYPE_TAG, NUMBER_TAG),
	EXTENDED_INTEGER_TYPE_TAG(NUMBER_TYPE_TAG, EXTENDED_INTEGER_TAG),
	PHRASE_TYPE_TAG(NONTYPE_TYPE_TAG, PHRASE_TAG),
	LIST_PHRASE_TYPE_TAG(PHRASE_TYPE_TAG, LIST_PHRASE_TAG),
	VARIABLE_TYPE_TAG(NONTYPE_TYPE_TAG, VARIABLE_TAG),
	PRIMITIVE_TYPE_TAG(NONTYPE_TYPE_TAG),
	FUNCTION_TYPE_TAG(NONTYPE_TYPE_TAG, FUNCTION_TAG),
	OBJECT_TYPE_TAG(NONTYPE_TYPE_TAG, OBJECT_TAG),
	MAP_TYPE_TAG(NONTYPE_TYPE_TAG, MAP_TAG),
	TUPLE_TYPE_TAG(NONTYPE_TYPE_TAG, TUPLE_TAG),
	CONTINUATION_TYPE_TAG(NONTYPE_TYPE_TAG, CONTINUATION_TAG),
	RAW_FUNCTION_TYPE_TAG(NONTYPE_TYPE_TAG, RAW_FUNCTION_TAG),
	FIBER_TYPE_TAG(NONTYPE_TYPE_TAG, FIBER_TAG),
	META_TAG(ANY_TYPE_TAG, TOP_TYPE_TAG),
	BOTTOM_TYPE_TAG(ANY_TYPE_TAG);

	// Special case
	constructor ()
	{
		depth = 0
		parent = null
		highOrdinal = ordinal
	}

	constructor (parent: TypeTag)
	{
		assert(parent.metaTag === null) {
			"Children of tags with metaTags should also have metaTags"
		}
		depth = parent.depth + 1
		this.parent = parent
		highOrdinal = ordinal
		parent.addDescendant(this)
	}

	constructor (parent: TypeTag, instance: TypeTag)
	{
		depth = parent.depth + 1
		this.parent = parent
		highOrdinal = ordinal
		parent.addDescendant(this)
		instance.metaTag = this
	}

	fun addDescendant (descendant: TypeTag)
	{
		assert(descendant.ordinal == highOrdinal + 1)
		descendants.add(descendant)
		highOrdinal++
		parent?.addDescendant(descendant)
	}

	val parent: TypeTag?

	private var metaTag: TypeTag? = null

	val depth: Int

	private val descendants = mutableListOf<TypeTag>()

	private var highOrdinal: Int

	fun metaTag (): TypeTag = metaTag!!

	@Suppress("unused")
	fun isSubtagOf (otherTag: TypeTag): Boolean =
		(ordinal >= otherTag.ordinal && highOrdinal <= otherTag.highOrdinal)

	fun commonAncestorWith (other: TypeTag?): TypeTag
	{
		if (this == other)
		{
			return this
		}
		val myParent = parent
		if (depth > other!!.depth)
		{
			assert(myParent !== null)
			return myParent!!.commonAncestorWith(other)
		}
		val otherParent = other.parent
		if (other.depth > depth)
		{
			assert(otherParent !== null)
			return otherParent!!.commonAncestorWith(this)
		}
		assert(myParent !== null && otherParent !== null)
		assert(this != UNKNOWN_TAG && other != UNKNOWN_TAG)
		return myParent!!.commonAncestorWith(otherParent)
	}

	companion object
	{
		init
		{
			for (tag in values())
			{
				if (tag.metaTag === null && tag != UNKNOWN_TAG)
				{
					tag.metaTag = tag.parent!!.metaTag
				}
			}
			BOTTOM_TYPE_TAG.highOrdinal = ANY_TYPE_TAG.ordinal
			for (tag in TOP_TYPE_TAG.descendants)
			{
				if (!tag.descendants.contains(BOTTOM_TYPE_TAG))
				{
					tag.descendants.add(BOTTOM_TYPE_TAG)
				}
			}
		}
	}
}
