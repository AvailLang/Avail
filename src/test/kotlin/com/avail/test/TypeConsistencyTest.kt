/*
 * TypeConsistencyTest.kt
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
package com.avail.test

import com.avail.AvailRuntime.Companion.specialAtoms
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomDescriptor.Companion.createAtom
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.objects.ObjectTypeDescriptor
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.objectTypeFromMap
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.tokens.TokenDescriptor
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomPojoTypeDescriptor.Companion.pojoBottom
import com.avail.descriptor.types.BottomTypeDescriptor
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import com.avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationMeta
import com.avail.descriptor.types.FiberTypeDescriptor.Companion.fiberMeta
import com.avail.descriptor.types.FiberTypeDescriptor.Companion.fiberType
import com.avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionMeta
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeFromArgumentTupleType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import com.avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegersMeta
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.ListPhraseTypeDescriptor.Companion.createListNodeType
import com.avail.descriptor.types.LiteralTokenTypeDescriptor
import com.avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.literalTokenType
import com.avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.mostGeneralLiteralTokenType
import com.avail.descriptor.types.MapTypeDescriptor.Companion.mapMeta
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.PojoTypeDescriptor
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.mostGeneralPojoType
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.pojoArrayType
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.pojoTypeForClass
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.pojoTypeForClassWithTypeArguments
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.selfTypeForClass
import com.avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import com.avail.descriptor.types.SetTypeDescriptor.Companion.setMeta
import com.avail.descriptor.types.TokenTypeDescriptor
import com.avail.descriptor.types.TokenTypeDescriptor.Companion.tokenType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleMeta
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeFromTupleOfTypes
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.VariableTypeDescriptor
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.variableReadWriteType
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import com.avail.interpreter.Primitive
import com.avail.utility.structures.EnumMap
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.PrintStream

/**
 * Test various consistency properties for [A_Type]s in Avail.  The type system
 * is really pretty complex, so these tests are quite important.
 *
 * Here are some things to test.  T is the set of types, T(x) means the type of
 * x, Co(x) is some relation between a type and its parameters that's supposed
 * to be covariant, Con(x) is some relation that's supposed to be contravariant,
 * &cup; is type union, and &cap; is type intersection.
 *
 * ## Type consistency conditions: ##
 *
 * - **Subtype reflexivity**
 *
 *    &forall;x&isin;T : x&sube;x
 *
 * - **Subtype transitivity**
 *
 *    &forall;x,y,z&isin;T : x&sube;y&thinsp;&and;&thinsp;y&sube;z
 *    &rarr; x&sube;z
 *
 * - **Subtype asymmetry**
 *
 *    &forall;x,y&isin;T : x&sub;y &rarr; &not;y&sub;x
 *
 *    *or alternatively,*
 *
 *    &forall;x,y&isin;T : x&sube;y &and; y&sube;x &#x2194; (x=y)
 *
 * - **Union closure**
 *
 *    &forall;x,y&isin;T : x&cup;y &isin; T
 *
 * - **Union reflexivity**
 *
 *     &forall;x&isin;T : x&cup;x = x
 *
 * - **Union commutativity**
 *
 *     &forall;x,y&isin;T : x&cup;y = y&cup;x
 *
 * - **Union associativity**
 *
 *     &forall;x,y,z&isin;T : (x&cup;y)&cup;z = x&cup;(y&cup;z)
 *
 * - **Intersection closure**
 *
 *     &forall;x,y&isin;T : x&cap;y &isin; T
 *
 * - **Intersection reflexivity**
 *
 *     &forall;x&isin;T : x&cap;x = x
 *
 * - **Intersection commutativity**
 *
 *     &forall;x,y&isin;T : x&cap;y = y&cap;x
 *
 * - **Intersection associativity**
 *
 *     &forall;x,y,z&isin;T : (x&cap;y)&cap;z = x&cap;(y&cap;z)
 *
 * - **Various covariance relationships (Co)**
 *
 *     &forall;x,y&isin;T : x&sube;y &rarr; Co(x)&sube;Co(y)
 *
 * - **Various contravariance relationships (Con)**
 *
 *     &forall;x,y&isin;T : x&sube;y &rarr; Con(y)&sube;Con(x)
 *
 * - **Metacovariance**
 *
 *     &forall;x,y&isin;T : x&sube;y &rarr; T(x)&sube;T(y)
 *
 * - **Type union metainvariance**
 *
 *     &forall;x,y&isin;T : T(x)&cup;T(y) = T(x&cup;y)
 *
 * - **Type intersection metainvariance**
 *
 *     &forall;x,y&isin;T : T(x)&cap;T(y) = T(x&cap;y)
 *
 * - **Instantiation metainvariance**
 *
 *     &forall;b&isin;T,a&isin;Objects : a&isin;b = T(a)&isin;T(b)
 *
 * # Type consistency conditions: #
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("LocalVariableName")
class TypeConsistencyTest
{
	/**
	 * `Node` records its instances upon creation.  They must be created in
	 * top-down order (i.e., supertypes before subtypes), as the constructor
	 * takes a variable number of supertype nodes.  The node supertype
	 * declarations are checked against the actual properties of the underlying
	 * types as one of the fundamental consistency checks.
	 *
	 * All primitive [Types] are included, as well as a few simple
	 * representative samples, such as the one-element string type and the type
	 * of whole numbers.
	 *
	 * @property name
	 *   The name of this type node, used for error diagnostics.
	 *
	 * @constructor
	 *   Construct a new `Node`, capturing a varargs list of known supertypes.
	 *
	 * @param name
	 *   The printable name of this `Node`.
	 * @param t
	 *   The [A_Type] that this `Node` represents in the graph.
	 * @param varargSupernodes
	 *   The array of `Node`s that this node is asserted to descend from.
	 *   Transitive ancestors may be elided.
	 */
	@Suppress("SelfReferenceConstructorParameter")
	class Node internal constructor(
		val name: String,
		val t: A_Type,
		vararg varargSupernodes: Node)
	{
		@Suppress("unused")
		companion object
		{
			/**
			 * The list of all currently defined [type&#32;nodes][Node].
			 */
			val values = mutableListOf<Node>()

			/**
			 * A mapping from [Types] to their corresponding
			 * `Node`s.
			 */
			private val primitiveTypes: EnumMap<Types, Node> = run {
				val tempMap = mutableMapOf<Types, Node>()
				EnumMap.enumMap(Types.values()) { type ->
					val parents = when (val parent = type.parent)
					{
						null -> emptyArray()
						else -> arrayOf(tempMap[parent]!!)
					}
					Node(type.name, type.o, *parents).also {
						tempMap[type] = it
					}
				}
			}

			/** The most general metatype.  */
			private val TOP_META = Node(
				"TOP_META", topMeta(), primitiveTypes[Types.ANY])

			/** The type of `any`.  */
			private val ANY_META = Node("ANY_META", anyMeta(), TOP_META)

			/** The type of `nontype`.  */
			private val NONTYPE_META = Node(
				"NONTYPE_META",
				instanceMeta(Types.NONTYPE.o),
				ANY_META)

			/** The type `tuple`  */
			private val TUPLE = Node(
				"TUPLE",
				mostGeneralTupleType(),
				primitiveTypes[Types.NONTYPE])

			/**
			 * The type `string`, which is the same as `tuple of
			 * character`
			 */
			private val STRING = Node("STRING", stringType(), TUPLE)

			/** The type `tuple [1..1] of character`  */
			private val UNIT_STRING = Node(
				"UNIT_STRING", stringFrom("x").kind(), STRING)

			/** The type `type of <>`  */
			private val EMPTY_TUPLE = Node(
				"EMPTY_TUPLE", emptyTuple.kind(), TUPLE, STRING)

			/** The type `set`  */
			private val SET = Node(
				"SET", mostGeneralSetType(), primitiveTypes[Types.NONTYPE])

			/** The most general fiber type.  */
			private val FIBER = Node(
				"FIBER",
				mostGeneralFiberType(),
				primitiveTypes[Types.NONTYPE])

			/** The most general function type.  */
			private val MOST_GENERAL_FUNCTION = Node(
				"MOST_GENERAL_FUNCTION",
				mostGeneralFunctionType(),
				primitiveTypes[Types.NONTYPE])

			/**
			 * The type for functions that accept no arguments and return an
			 * integer.
			 */
			private val NOTHING_TO_INT_FUNCTION = Node(
				"NOTHING_TO_INT_FUNCTION",
				functionType(emptyTuple, integers),
				MOST_GENERAL_FUNCTION)

			/**
			 * The type for functions that accept an integer and return an
			 * integer.
			 */
			private val INT_TO_INT_FUNCTION = Node(
				"INT_TO_INT_FUNCTION",
				functionType(tuple(integers), integers),
				MOST_GENERAL_FUNCTION)

			/**
			 * The type for functions that accept two integers and return an
			 * integer.
			 */
			private val INTS_TO_INT_FUNCTION = Node(
				"INTS_TO_INT_FUNCTION",
				functionType(tuple(integers, integers), integers),
				MOST_GENERAL_FUNCTION)

			/** The most specific function type, other than bottom.  */
			private val MOST_SPECIFIC_FUNCTION = Node(
				"MOST_SPECIFIC_FUNCTION",
				functionTypeFromArgumentTupleType(
					mostGeneralTupleType(),
					bottom(),
					emptySet),
				NOTHING_TO_INT_FUNCTION,
				INT_TO_INT_FUNCTION,
				INTS_TO_INT_FUNCTION)

			/**
			 * The primitive type representing the extended integers `[-∞..∞]`.
			 */
			private val EXTENDED_INTEGER = Node(
				"EXTENDED_INTEGER",
				extendedIntegers,
				primitiveTypes[Types.NUMBER])

			/** The primitive type representing whole numbers `[0..∞)`. */
			private val WHOLE_NUMBER = Node(
				"WHOLE_NUMBER", wholeNumbers, EXTENDED_INTEGER)

			/** Some [atom][AtomDescriptor]'s instance type. */
			private val SOME_ATOM_TYPE = Node(
				"SOME_ATOM_TYPE",
				instanceType(
					createAtom(
						stringFrom("something"),
						NilDescriptor.nil)),
				primitiveTypes[Types.ATOM])

			/**
			 * The instance type of an [atom][AtomDescriptor] different from
			 * [SOME_ATOM_TYPE].
			 */
			private val ANOTHER_ATOM_TYPE = Node(
				"ANOTHER_ATOM_TYPE",
				instanceType(
					createAtom(
						stringFrom("another"),
						NilDescriptor.nil)),
				primitiveTypes[Types.ATOM])

			/**
			 * The base [object type][ObjectTypeDescriptor].
			 */
			private val OBJECT_TYPE = Node(
				"OBJECT_TYPE",
				mostGeneralObjectType(),
				primitiveTypes[Types.NONTYPE])

			/**
			 * A simple non-root [object type][ObjectTypeDescriptor].
			 */
			private val NON_ROOT_OBJECT_TYPE = Node(
				"NON_ROOT_OBJECT_TYPE",
				objectTypeFromMap(
					emptyMap.mapAtPuttingCanDestroy(
						SOME_ATOM_TYPE.t.instance(),
						Types.ANY.o,
						false)),
				OBJECT_TYPE)

			/**
			 * A simple non-root [object type][ObjectTypeDescriptor].
			 */
			private val NON_ROOT_OBJECT_TYPE_WITH_INTEGERS = Node(
				"NON_ROOT_OBJECT_TYPE_WITH_INTEGERS",
				objectTypeFromMap(emptyMap
					.mapAtPuttingCanDestroy(
						SOME_ATOM_TYPE.t.instance(),
						integers,
						false)),
				NON_ROOT_OBJECT_TYPE)

			/**
			 * A simple non-root [object type][ObjectTypeDescriptor].
			 */
			private val NON_ROOT_OBJECT_TYPE_WITH_DIFFERENT_KEY = Node(
				"NON_ROOT_OBJECT_TYPE_WITH_DIFFERENT_KEY",
				objectTypeFromMap(emptyMap
					.mapAtPuttingCanDestroy(
						ANOTHER_ATOM_TYPE.t.instance(),
						Types.ANY.o,
						false)),
				OBJECT_TYPE)

			/**
			 * The pojo type representing [Comparable]&lt;[Object]&gt;.
			 */
			private val COMPARABLE_OF_JAVA_OBJECT_POJO = Node(
				"COMPARABLE_OF_JAVA_OBJECT_POJO",
				pojoTypeForClassWithTypeArguments(
					Comparable::class.java,
					tuple(mostGeneralPojoType())),
				primitiveTypes[Types.NONTYPE])

			/**
			 * The pojo type representing [Comparable]&lt;[Int]&gt;.
			 */
			private val COMPARABLE_OF_JAVA_INTEGER_POJO = Node(
				"COMPARABLE_OF_JAVA_INTEGER_POJO",
				pojoTypeForClassWithTypeArguments(
					Comparable::class.java,
					tuple(pojoTypeForClass(Integer::class.java))),
				COMPARABLE_OF_JAVA_OBJECT_POJO)

			/**
			 * The pojo type representing [Int].
			 */
			private val JAVA_INTEGER_POJO = Node(
				"JAVA_INTEGER_POJO",
				pojoTypeForClass(Integer::class.java),
				COMPARABLE_OF_JAVA_INTEGER_POJO)

			/**
			 * The pojo type representing [Comparable]&lt;[String]&gt;.
			 */
			private val COMPARABLE_OF_JAVA_STRING_POJO = Node(
				"COMPARABLE_OF_JAVA_STRING_POJO",
				pojoTypeForClassWithTypeArguments(
					Comparable::class.java,
					tuple(pojoTypeForClass(String::class.java))),
				COMPARABLE_OF_JAVA_OBJECT_POJO)

			/**
			 * The pojo type representing [String].
			 */
			private val JAVA_STRING_POJO = Node(
				"JAVA_STRING_POJO",
				pojoTypeForClass(String::class.java),
				COMPARABLE_OF_JAVA_STRING_POJO)

			/**
			 * The pojo type representing [Enum]&lt;*self type*&gt;.
			 * Note that this type isn't actually supported by Java directly, since
			 * it would look like
			 * Enum&lt;Enum&lt;Enum&lt;Enum&lt;...&gt;&gt;&gt;&gt;, which cannot
			 * actually be written as a Java type expression.  This pojo type is the
			 * most general Java enumeration type.
			 */
			private val JAVA_ENUM_POJO = Node(
				"JAVA_ENUM_POJO",
				pojoTypeForClassWithTypeArguments(
					Enum::class.java,
					tuple(selfTypeForClass(Enum::class.java))),
				COMPARABLE_OF_JAVA_OBJECT_POJO)

			/**
			 * The pojo type representing the Java enumeration [Result].
			 */
			private val AVAIL_PRIMITIVE_RESULT_ENUM_POJO = Node(
				"AVAIL_PRIMITIVE_RESULT_ENUM_POJO",
				pojoTypeForClass(Primitive.Result::class.java),
				JAVA_ENUM_POJO)

			/**
			 * The pojo type representing [Comparable]&lt;*Avail's integer
			 * type*&gt;.  Note that this is a Java type parameterized by an
			 * Avail type.
			 */
			private val COMPARABLE_OF_AVAIL_INTEGER_POJO = Node(
				"COMPARABLE_OF_AVAIL_INTEGER_POJO",
				pojoTypeForClassWithTypeArguments(
					Comparable::class.java,
					tuple(integers)),
				primitiveTypes[Types.NONTYPE])

			/**
			 * The pojo type representing the Java [Array] type [Object]`[].
			 */
			private val JAVA_OBJECT_ARRAY_POJO = Node(
				"JAVA_OBJECT_ARRAY_POJO",
				pojoArrayType(mostGeneralPojoType(), wholeNumbers),
				primitiveTypes[Types.NONTYPE])

			/**
			 * The pojo type representing the Java [Array] type [String]`[].
			 */
			private val JAVA_STRING_ARRAY_POJO = Node(
				"JAVA_STRING_ARRAY_POJO",
				pojoArrayType(JAVA_STRING_POJO.t, wholeNumbers),
				JAVA_OBJECT_ARRAY_POJO)

			/**
			 * [Pojo bottom][PojoTypeDescriptor].
			 */
			private val POJO_BOTTOM = Node(
				"POJO_BOTTOM",
				pojoBottom(),
				JAVA_INTEGER_POJO,
				JAVA_STRING_POJO,
				AVAIL_PRIMITIVE_RESULT_ENUM_POJO,
				COMPARABLE_OF_AVAIL_INTEGER_POJO,
				JAVA_STRING_ARRAY_POJO)

			/**
			 * The metatype for function types.
			 */
			private val FUNCTION_META = Node(
				"FUNCTION_META", functionMeta(), NONTYPE_META)

			/**
			 * The metatype for continuation types.
			 */
			private val CONTINUATION_META = Node(
				"CONTINUATION_META", continuationMeta(), NONTYPE_META)

			/**
			 * The metatype for integer types.
			 */
			private val INTEGER_META = Node(
				"INTEGER_META", extendedIntegersMeta, NONTYPE_META)

			/**
			 * The primitive type representing the metatype of whole numbers
			 * `[0..∞)`.
			 */
			private val WHOLE_NUMBER_META = Node(
				"WHOLE_NUMBER_META", instanceMeta(wholeNumbers), INTEGER_META)

			/**
			 * The primitive type representing the metametatype of the metatype
			 * of whole numbers `[0..∞)`.
			 */
			private val WHOLE_NUMBER_META_META = Node(
				"WHOLE_NUMBER_META_META",
				instanceMeta(instanceMeta(wholeNumbers)),
				ANY_META,
				TOP_META)

			/**
			 * The most general [variable type][VariableTypeDescriptor].
			 */
			private val ROOT_VARIABLE = Node(
				"ROOT_VARIABLE",
				mostGeneralVariableType(),
				primitiveTypes[Types.NONTYPE])

			/**
			 * The [type of variable][VariableTypeDescriptor] which
			 * holds [integers][IntegerDescriptor].
			 */
			private val INT_VARIABLE = Node(
				"INT_VARIABLE", variableTypeFor(integers), ROOT_VARIABLE)

			/**
			 * The [type of variable][VariableTypeDescriptor] which holds only a
			 * particular atom.
			 */
			private val SOME_ATOM_VARIABLE = Node(
				"SOME_ATOM_VARIABLE",
				variableTypeFor(SOME_ATOM_TYPE.t),
				ROOT_VARIABLE)

			/**
			 * The most specific [type of variable][VariableTypeDescriptor],
			 * other than [bottom][BottomTypeDescriptor].
			 */
			private val BOTTOM_VARIABLE = Node(
				"BOTTOM_VARIABLE",
				variableReadWriteType(bottom(), Types.TOP.o),
				INT_VARIABLE,
				SOME_ATOM_VARIABLE)

			/**
			 * The [token type][TokenTypeDescriptor] whose
			 * [TokenDescriptor.TokenType] is
			 * [TokenDescriptor.TokenType.END_OF_FILE].
			 */
			private val END_OF_FILE_TOKEN = Node(
				"END_OF_FILE_TOKEN",
				tokenType(TokenDescriptor.TokenType.END_OF_FILE),
				primitiveTypes[Types.TOKEN])

			/**
			 * The [token type][TokenTypeDescriptor] whose
			 * [TokenDescriptor.TokenType] is
			 * [TokenDescriptor.TokenType.KEYWORD].
			 */
			private val KEYWORD_TOKEN = Node(
				"KEYWORD_TOKEN",
				tokenType(TokenDescriptor.TokenType.KEYWORD),
				primitiveTypes[Types.TOKEN])

			/**
			 * The [token type][TokenTypeDescriptor] whose
			 * [TokenDescriptor.TokenType] is
			 * [TokenDescriptor.TokenType.OPERATOR].
			 */
			private val OPERATOR_TOKEN = Node(
				"OPERATOR_TOKEN",
				tokenType(TokenDescriptor.TokenType.OPERATOR),
				primitiveTypes[Types.TOKEN])

			/**
			 * The [token type][TokenTypeDescriptor] whose
			 * [TokenDescriptor.TokenType] is
			 * [TokenDescriptor.TokenType.COMMENT].
			 */
			private val COMMENT_TOKEN = Node(
				"COMMENT_TOKEN",
				tokenType(TokenDescriptor.TokenType.COMMENT),
				primitiveTypes[Types.TOKEN])

			/**
			 * The [token type][TokenTypeDescriptor] whose
			 * [TokenDescriptor.TokenType] is
			 * [TokenDescriptor.TokenType.WHITESPACE].
			 */
			private val WHITESPACE_TOKEN = Node(
				"WHITESPACE_TOKEN",
				tokenType(TokenDescriptor.TokenType.WHITESPACE),
				primitiveTypes[Types.TOKEN])

			/**
			 * The [literal token type][LiteralTokenTypeDescriptor] whose
			 * literal type is [Types.ANY].
			 */
			private val ANY_LITERAL_TOKEN = Node(
				"ANY_LITERAL_TOKEN",
				mostGeneralLiteralTokenType(),
				primitiveTypes[Types.TOKEN])

			/**
			 * The [literal&#32;token&#32;type][LiteralTokenTypeDescriptor]
			 * whose literal must be an [integer][IntegerDescriptor].
			 */
			private val INT_LITERAL_TOKEN = Node(
				"INT_LITERAL_TOKEN",
				literalTokenType(integers),
				ANY_LITERAL_TOKEN)

			/**
			 * The [literal&#32;token&#32;type][LiteralTokenTypeDescriptor]
			 * whose literal must be a particular [atom][AtomDescriptor].
			 */
			private val SOME_ATOM_LITERAL_TOKEN = Node(
				"SOME_ATOM_LITERAL_TOKEN",
				literalTokenType(SOME_ATOM_TYPE.t),
				ANY_LITERAL_TOKEN)

			/**
			 * The most specific
			 * [literal&#32;token&#32;type][LiteralTokenTypeDescriptor], other
			 * than [bottom][BottomTypeDescriptor].
			 */
			private val BOTTOM_LITERAL_TOKEN = Node(
				"BOTTOM_LITERAL_TOKEN",
				literalTokenType(bottom()),
				INT_LITERAL_TOKEN,
				SOME_ATOM_LITERAL_TOKEN)

			/**
			 * The metatype for map types.
			 */
			private val MAP_META = Node("MAP_META", mapMeta(), NONTYPE_META)

			/**
			 * The metatype for set types.
			 */
			private val SET_META = Node("SET_META", setMeta(), NONTYPE_META)

			/**
			 * The metatype for tuple types.
			 */
			private val TUPLE_META = Node(
				"TUPLE_META", tupleMeta(), NONTYPE_META)

			/**
			 * The metatype for fiber types.
			 */
			private val FIBER_META = Node(
				"FIBER_META", fiberMeta(), NONTYPE_META)

			/** The type of `bottom`.  This is the most specific meta. */
			private val BOTTOM_TYPE = Node(
				"BOTTOM_TYPE",
				bottomMeta(),
				FIBER_META,
				FUNCTION_META,
				CONTINUATION_META,
				WHOLE_NUMBER_META,
				WHOLE_NUMBER_META_META,
				MAP_META,
				SET_META,
				TUPLE_META)

			/**
			 * A two tiered map from phrase kind to inner Node (or null) to
			 * phrase type Node.  This is used to construct the lattice of
			 * phrase type nodes incrementally.  A null indicates the inner type
			 * should be [BOTTOM], even though it hasn't been defined yet.
			 */
			private val phraseTypeMap =
				EnumMap.enumMap<PhraseKind, MutableMap<Node?, Node>>(
					PhraseKind.values()) { mutableMapOf() }

			/**
			 * Create a phrase type Node with the given name, phrase kind, Node
			 * indicating the expressionType, and the array of Nodes that are
			 * supertypes of the expressionType.  Passing null for the
			 * expressionType causes
			 * [the&#32;bottom&#32;type][BottomTypeDescriptor.bottom] to be
			 * used.  We can't use the node [BOTTOM] because of circular
			 * dependency.
			 *
			 * @param nodeName
			 *   A [String] naming this node for diagnostics.
			 * @param phraseKind
			 *   The [kind][PhraseKind] of phrase type.
			 * @param innerNode
			 *   The expressionType of the resulting phrase type, or `null` to
			 *   indicate [bottom][BottomTypeDescriptor.bottom].
			 * @param parentInnerNodes
			 *   An array of parent nodes of the innerNode.
			 */
			private fun addHelper(
				nodeName: String,
				phraseKind: PhraseKind,
				innerNode: Node?,
				parentInnerNodes: List<Node?>)
			{
				val submap: MutableMap<Node?, Node> =
					when
					{
						phraseTypeMap.containsKey(phraseKind) ->
							phraseTypeMap[phraseKind]
						else -> mutableMapOf<Node?, Node>().also {
							phraseTypeMap[phraseKind] = it
						}
					}
				val parents = mutableListOf<Node>()
				when (phraseKind.parentKind())
				{
					null ->
						primitiveTypes[Types.NONTYPE].also { parents.add(it) }
					else ->
					{
						val m: Map<Node?, Node> =
							phraseTypeMap[phraseKind.parentKind()!!]
						m[innerNode]?.let { parents.add(it) }
					}
				}
				for (parentInnerNode in parentInnerNodes)
				{
					parents.add(submap[parentInnerNode]!!)
				}
				val innerType = innerNode?.t ?: bottom()
				val newType: A_Type
				newType = when
				{
					phraseKind.isSubkindOf(PhraseKind.LIST_PHRASE) ->
					{
						val subexpressionsTupleType =
							tupleTypeFromTupleOfTypes(innerType) {
								PhraseKind.PARSE_PHRASE.create(it)
							}
						createListNodeType(
							phraseKind, innerType, subexpressionsTupleType)
					}
					else -> phraseKind.create(innerType)
				}
				assert(
					newType.expressionType().equals(innerType)) {
					"phrase kind was not parameterized as expected"
				}
				val newNode = Node(nodeName, newType, *parents.toTypedArray())
				submap[innerNode] = newNode
			}

			/**
			 * Deduce the relationships among the inner nodes of the kind,
			 * adding a phrase kind node for each inner node.
			 *
			 * @param kind
			 *   A [phrase kind][PhraseKind].
			 * @param innerNodes
			 *   The nodes by which to parameterize this phrase kind.
			 */
			private fun addMultiHelper(
				kind: PhraseKind,
				vararg innerNodes: Node?)
			{
				for (node in innerNodes)
				{
					val ancestors = mutableListOf<Node?>()
					when (node)
					{
						null ->
						{
							ancestors.addAll(listOf(*innerNodes))
							ancestors.remove(null)
						}
						else ->
							for (possibleAncestor in innerNodes)
							{
								if (possibleAncestor != null
									&& node.allAncestors.contains(
										possibleAncestor))
								{
									ancestors.add(possibleAncestor)
								}
							}
					}
					assert(!ancestors.contains(null))
					val nodeName: Any = node?.name ?: "BOTTOM"
					addHelper("${kind.name} ($nodeName)", kind, node, ancestors)
				}
			}

			/**
			 * Record the actual type information into the graph.
			 */
			fun createTypes()
			{
				val n = values.size
				for (node in values)
				{
					node.unionCache = arrayOfNulls(n)
					node.intersectionCache = arrayOfNulls(n)
					node.subtypeCache = arrayOfNulls(n)
				}
			}

			/**
			 * Remove all type information from the graph, leaving the shape
			 * intact.
			 */
			fun eraseTypes()
			{
				for (node in values)
				{
					node.unionCache = arrayOfNulls(0)
					node.intersectionCache = arrayOfNulls(0)
					node.subtypeCache = arrayOfNulls(0)
				}
			}

			init
			{
				// Include all phrase types.  Include a minimal diamond of types
				// for each phrase kind.
				val topNode = primitiveTypes[Types.TOP]
				val anyNode = primitiveTypes[Types.ANY]
				val nontypeNode = primitiveTypes[Types.NONTYPE]
				val atomNode = SOME_ATOM_TYPE
				val anotherAtomNode = ANOTHER_ATOM_TYPE
				for (kind in PhraseKind.all())
				{
					// This is future-proofing (for total coverage of phrase
					// kinds).
					when (kind)
					{
						PhraseKind.MARKER_PHRASE -> Unit
						PhraseKind.BLOCK_PHRASE -> addMultiHelper(
							kind,
							MOST_GENERAL_FUNCTION,
							NOTHING_TO_INT_FUNCTION,
							INT_TO_INT_FUNCTION,
							INTS_TO_INT_FUNCTION,
							MOST_SPECIFIC_FUNCTION,
							null)
						PhraseKind.REFERENCE_PHRASE -> addMultiHelper(
							kind,
							ROOT_VARIABLE,
							INT_VARIABLE,
							SOME_ATOM_VARIABLE,
							BOTTOM_VARIABLE,
							null)
						PhraseKind.ASSIGNMENT_PHRASE,
						PhraseKind.LITERAL_PHRASE,
						PhraseKind.SUPER_CAST_PHRASE,
						PhraseKind.VARIABLE_USE_PHRASE -> addMultiHelper(
							kind,
							anyNode,
							nontypeNode,
							atomNode,
							anotherAtomNode,
							FIBER,
							MOST_GENERAL_FUNCTION,
							NOTHING_TO_INT_FUNCTION,
							INT_TO_INT_FUNCTION,
							INTS_TO_INT_FUNCTION,
							MOST_SPECIFIC_FUNCTION,
							TUPLE,
							SET,
							STRING,
							EXTENDED_INTEGER,
							WHOLE_NUMBER,
							ROOT_VARIABLE,
							INT_VARIABLE,
							SOME_ATOM_VARIABLE,
							BOTTOM_VARIABLE,
							null)
						PhraseKind.STATEMENT_PHRASE,
						PhraseKind.SEQUENCE_PHRASE,
						PhraseKind.FIRST_OF_SEQUENCE_PHRASE,
						PhraseKind.DECLARATION_PHRASE,
						PhraseKind.ARGUMENT_PHRASE,
						PhraseKind.LABEL_PHRASE,
						PhraseKind.LOCAL_VARIABLE_PHRASE,
						PhraseKind.LOCAL_CONSTANT_PHRASE,
						PhraseKind.MODULE_VARIABLE_PHRASE,
						PhraseKind.MODULE_CONSTANT_PHRASE,
						PhraseKind.PRIMITIVE_FAILURE_REASON_PHRASE,
						PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE ->
							addMultiHelper(kind, topNode, null)
						PhraseKind.PARSE_PHRASE,
						PhraseKind.EXPRESSION_PHRASE,
						PhraseKind.SEND_PHRASE -> addMultiHelper(
							kind,
							topNode,
							anyNode,
							nontypeNode,
							atomNode,
							anotherAtomNode,
							FIBER,
							MOST_GENERAL_FUNCTION,
							NOTHING_TO_INT_FUNCTION,
							INT_TO_INT_FUNCTION,
							INTS_TO_INT_FUNCTION,
							MOST_SPECIFIC_FUNCTION,
							TUPLE,
							SET,
							STRING,
							EXTENDED_INTEGER,
							WHOLE_NUMBER,
							ROOT_VARIABLE,
							INT_VARIABLE,
							SOME_ATOM_VARIABLE,
							BOTTOM_VARIABLE,
							UNIT_STRING,
							EMPTY_TUPLE,
							null)
						PhraseKind.LIST_PHRASE,
						PhraseKind.PERMUTED_LIST_PHRASE -> addMultiHelper(
							kind,
							TUPLE,
							STRING,
							UNIT_STRING,
							EMPTY_TUPLE,
							null)
						PhraseKind.MACRO_SUBSTITUTION_PHRASE -> addMultiHelper(
							kind, topNode, anyNode)
					}
				}
			}

			/**
			 * The list of all `Node`s except BOTTOM.
			 */
			private val nonBottomTypes = mutableListOf<Node>()

			/* Set this up before creating the BOTTOM node. */
			init
			{
				nonBottomTypes.addAll(values)
			}

			/**
			 * The type `bottom`.  Defined after all the previous nodes have
			 * been processed.
			 */
			private val BOTTOM = Node(
				"BOTTOM",
				bottom(),
				*nonBottomTypes.toTypedArray())

			// The nodes' slots have to be initialized here because they pass
			// the Node.class to the EnumSet factory, which attempts to
			// determine the number of enumeration values, which isn't known yet
			// when the constructors are still running.
			//
			// Also build the inverse and (downwards) transitive function at
			// each node of the graph, since they're independent of how the
			// actual types are related.  Discrepancies between the graph
			// information and the actual types is resolved in testGraphModel().
			init
			{
				for (node in values)
				{
					for (supernode in node.supernodes)
					{
						supernode.subnodes.add(node)
					}
				}
				for (node in values)
				{
					node.allDescendants.add(node)
					node.allDescendants.addAll(node.subnodes)
				}
				var changed: Boolean
				do
				{
					changed = false
					for (node in values)
					{
						for (subnode in node.subnodes)
						{
							changed = changed or node.allDescendants.addAll(
								subnode.allDescendants)
						}
					}
				}
				while (changed)
			}
		}

		/** A unique 0-based index for this `Node`. */
		private val index: Int = values.size.also { values.add(this) }

		/** The supernodes in the graph. */
		private val supernodes = varargSupernodes.clone()

		/** The set of subnodes in the graph. */
		private val subnodes = mutableSetOf<Node>()

		/** Every node from which this node descends. */
		val allAncestors: Set<Node>

		/** Every node descended from this one. */
		val allDescendants = mutableSetOf<Node>()

		/**
		 * A cache of type unions where I'm the left participant and the right
		 * participant (a Node) supplies its index for accessing the array.
		 */
		private var unionCache = arrayOfNulls<A_Type>(0)

		/**
		 * A cache of type intersections where I'm the left participant and the
		 * right participant (a Node) supplies its index for accessing the
		 * array.
		 */
		private var intersectionCache = arrayOfNulls<A_Type>(0)

		/**
		 * A cache of subtype tests where I'm the proposed subtype and the
		 * argument is the proposed supertype.  The value stored indicates if I
		 * am a subtype of the argument.
		 */
		private var subtypeCache = arrayOfNulls<Boolean>(0)

		/**
		 * Lookup or compute and cache the type union of the receiver's [t] and
		 * the argument's `t`.
		 *
		 * @param rightNode
		 *   The `Node` for the right side of the union.
		 * @return
		 *   The [type&#32;union][AvailObject.typeUnion] of the receiver's [t]
		 *   and the argument's `t`.
		 */
		fun union(rightNode: Node): A_Type
		{
			val rightIndex = rightNode.index
			var union = unionCache[rightIndex]
			if (union === null)
			{
				union = t.typeUnion(rightNode.t).makeShared()
				Assertions.assertTrue(t.isSubtypeOf(union))
				Assertions.assertTrue(rightNode.t.isSubtypeOf(union))
				unionCache[rightIndex] = union
			}
			return union
		}

		/**
		 * Lookup or compute and cache the type intersection of the receiver's
		 * [t] and the argument's `t`.
		 *
		 * @param rightNode
		 *   The `Node` for the right side of the intersection.
		 * @return
		 *   The [type intersection][AvailObject.typeIntersection] of the
		 *   receiver's [t] and the argument's `t`.
		 */
		fun intersect(rightNode: Node): A_Type
		{
			val rightIndex = rightNode.index
			var intersection = intersectionCache[rightIndex]
			if (intersection === null)
			{
				intersection = t.typeIntersection(rightNode.t).makeShared()
				Assertions.assertTrue(intersection.isSubtypeOf(t))
				Assertions.assertTrue(intersection.isSubtypeOf(rightNode.t))
				intersectionCache[rightIndex] = intersection
			}
			return intersection
		}

		/**
		 * Lookup or compute and cache whether the receiver's [t] is a subtype
		 * of the argument's `t`.
		 *
		 * @param rightNode
		 *   The `Node` for the right side of the subtype  test.
		 * @return
		 *   Whether the receiver's [t] is a subtype of the argument's `t`.
		 */
		fun subtype(rightNode: Node): Boolean
		{
			val rightIndex = rightNode.index
			var subtype = subtypeCache[rightIndex]
			if (subtype === null)
			{
				subtype = t.isSubtypeOf(rightNode.t)
				subtypeCache[rightIndex] = subtype
			}
			return subtype
		}

		override fun toString(): String = name

		init
		{
			val ancestors = mutableSetOf<Node>()
			for (supernode in supernodes)
			{
				ancestors.addAll(supernode.allAncestors)
			}
			ancestors.addAll(listOf(*supernodes))
			allAncestors = ancestors.toSet()
		}
	}

	/**
	 * Test that the [declared][Node.supernodes] subtype relations actually hold
	 * the way the graph says they should.
	 */
	@Test
	fun testGraphModel()
	{
		for (x in Node.values)
		{
			for (y in Node.values)
			{
				assertEQ(
					y.allDescendants.contains(x),
					x.subtype(y),
					"graph model (not as declared): %s, %s",
					x,
					y)
				assertEQ(
					x === y,
					x.t.equals(y.t),
					"graph model (not unique) %s, %s",
					x,
					y)
			}
		}
	}

	/**
	 * Test that the subtype relationship is reflexive.
	 *
	 * &forall;x&isin;T : x&sube;x
	 */
	@Test
	fun testSubtypeReflexivity()
	{
		for (x in Node.values)
		{
			if (!x.subtype(x))
			{
				// Breakpoint the following statement to debug test failures.
				x.subtype(x)
			}
			assertT(
				x.subtype(x),
				"subtype reflexivity: %s",
				x)
		}
	}

	/**
	 * Test that the subtype relationship is transitive.
	 *
	 * &forall;<sub>x,y,z&isin;T</sub> :
	 * (x&sube;y &and; y&sube;z &rarr; x&sube;z)
	 */
	@Test
	fun testSubtypeTransitivity()
	{
		for (x in Node.values)
		{
			for (y in Node.values)
			{
				val xSubY = x.subtype(y)
				for (z in Node.values)
				{
					assertT(
						!(xSubY && y.subtype(z))
							|| x.subtype(z),
						"subtype transitivity: %s, %s, %s",
						x,
						y,
						z)
				}
			}
		}
	}

	/**
	 * Test that the subtype relationship is asymmetric.
	 *
	 * &forall;x,y&isin;T : x&sub;y &rarr; &not;y&sub;x
	 */
	@Test
	fun testSubtypeAsymmetry()
	{
		for (x in Node.values)
		{
			for (y in Node.values)
			{
				assertEQ(
					x.subtype(y) && y.subtype(x),
					x === y,
					"subtype asymmetry: %s, %s",
					x,
					y)
			}
		}
	}

	/**
	 * Test that types are closed with respect to the type union operator.
	 *
	 * &forall;x,y&isin;T : x&cup;y&thinsp;&isin;&thinsp;T
	 */
	@Test
	fun testUnionClosure()
	{
		for (x in Node.values)
		{
			for (y in Node.values)
			{
				assertT(
					x
						.union(y)
						.isInstanceOf(topMeta()),
					"union closure: %s, %s",
					x,
					y)
			}
		}
	}

	/**
	 * Test that the type union operator is reflexive.
	 *
	 * &forall;x&isin;T : x&cup;x = x
	 */
	@Test
	fun testUnionReflexivity()
	{
		for (x in Node.values)
		{
			assertEQ(
				x.union(x),
				x.t,
				"union reflexivity: %s",
				x)
		}
	}

	/**
	 * Test that the type union operator is commutative.
	 *
	 * &forall;x,y&isin;T : x&cup;y = y&cup;x
	 */
	@Test
	fun testUnionCommutativity()
	{
		for (x in Node.values)
		{
			for (y in Node.values)
			{
				if (!x.union(y).equals(y.union(x)))
				{
					// These are useful trace points. Leave them in.
					x.t.typeUnion(y.t)
					y.t.typeUnion(x.t)
					assertEQ(
						x.union(y),
						y.union(x),
						"union commutativity: %s, %s",
						x,
						y)
				}
			}
		}
	}

	/**
	 * Test that the type union operator is associative.
	 *
	 * &forall;x,y,z&isin;T : (x&cup;y)&cup;z = x&cup;(y&cup;z)
	 */
	@Test
	fun testUnionAssociativity()
	{
		Node.values
			.parallelStream()
			.forEach { x: Node ->
				for (y in Node.values)
				{
					// Force the cache to be populated.
					x.union(y)
				}
			}
		Node.values
			.parallelStream()
			.forEach { x: Node ->
				for (y in Node.values)
				{
					val xy = x.union(y)
					for (z in Node.values)
					{
						val xyUz = xy.typeUnion(z.t)
						val yz = y.union(z)
						val xUyz = x.t.typeUnion(yz)
						if (!xyUz.equals(xUyz))
						{
							// These are useful trace points. Leave them in.
							xy.typeUnion(z.t)
							x.t.typeUnion(yz)
							xyUz.equals(xUyz)
							assertEQ(
								xyUz,
								xUyz,
								"union associativity: %s, %s, %s",
								x,
								y,
								z)
						}
					}
				}
			}
	}

	/**
	 * Test that types are closed with respect to the type intersection
	 * operator.
	 *
	 * &forall;x,y&isin;T : x&cap;y &isin; T)
	 */
	@Test
	fun testIntersectionClosure()
	{
		for (x in Node.values)
		{
			for (y in Node.values)
			{
				assertT(
					x.intersect(y).isInstanceOf(
						topMeta()),
					"intersection closure: %s, %s",
					x,
					y)
			}
		}
	}

	/**
	 * Test that the type intersection operator is reflexive.
	 *
	 * &forall;x&isin;T : x&cap;x = x
	 */
	@Test
	fun testIntersectionReflexivity()
	{
		for (x in Node.values)
		{
			assertEQ(
				x.intersect(x),
				x.t,
				"intersection reflexivity: %s",
				x)
		}
	}

	/**
	 * Test that the type intersection operator is commutative.
	 *
	 * &forall;x,y&isin;T : x&cap;y = y&cap;x
	 */
	@Test
	fun testIntersectionCommutativity()
	{
		for (x in Node.values)
		{
			for (y in Node.values)
			{
				val xy = x.intersect(y)
				val yx = y.intersect(x)
				if (!xy.equals(yx))
				{
					// These are useful trace points. Leave them in.
					x.t.typeIntersection(y.t)
					y.t.typeIntersection(x.t)
					assertEQ(
						xy,
						yx,
						"intersection commutativity: %s, %s",
						x,
						y)
				}
			}
		}
	}

	/**
	 * Test that the type intersection operator is associative.
	 *
	 * &forall;x,y,z&isin;T : (x&cap;y)&cap;z = x&cap;(y&cap;z)
	 */
	@Test
	fun testIntersectionAssociativity()
	{
		Node.values
			.parallelStream()
			.forEach { x: Node ->
				for (y in Node.values)
				{
					// Force the cache to be populated.
					x.intersect(y)
				}
			}
		Node.values
			.parallelStream()
			.forEach { x: Node ->
				for (y in Node.values)
				{
					val xy = x.intersect(y)
					for (z in Node.values)
					{
						val xyIz = xy.typeIntersection(z.t)
						val yz = y.intersect(z)
						val xIyz = x.t.typeIntersection(yz)
						if (!xyIz.equals(xIyz))
						{
							// These are useful trace points. Leave them in.
							x.t.typeIntersection(y.t)
							y.t.typeIntersection(z.t)
							xy.typeIntersection(z.t)
							x.t.typeIntersection(yz)
							xyIz.equals(xIyz)
							assertEQ(
								xyIz,
								xIyz,
								"intersection associativity: %s, %s, %s",
								x,
								y,
								z)
						}
					}
				}
			}
	}

	/**
	 * Test that the subtype relation covaries with fiber result type.
	 *
	 * @see [checkCovariance]
	 */
	@Test
	fun testFiberResultCovariance() =
		checkCovariance("fiber result") { fiberType(it) }

	/**
	 * Test that the subtype relation covaries with function return type.
	 *
	 * @see [checkCovariance]
	 */
	@Test
	fun testFunctionResultCovariance() =
		checkCovariance("function result") { functionType(emptyTuple, it) }

	/**
	 * Test that the subtype relation covaries with (homogeneous) tuple element
	 * type.
	 *
	 * @see [checkCovariance]
	 */
	@Test
	fun testTupleEntryCovariance() =
		checkCovariance("tuple entries") { zeroOrMoreOf(it) }

	/**
	 * Test that the subtype relation covaries with type parameters.
	 *
	 * @see [checkCovariance]
	 */
	@Test
	fun testAbstractPojoTypeParametersCovariance() =
		checkCovariance("pojo type parameters") {
			pojoTypeForClassWithTypeArguments(
				Comparable::class.java,
				tuple(it)
			)
		}

	/**
	 * Test that the subtype relation *contravaries* with function argument
	 * type.
	 *
	 * &forall;x,y&isin;T : x&sube;y &rarr; Con(y)&sube;Con(x)
	 */
	@Test
	fun testFunctionArgumentContravariance() =
		checkContravariance("function argument") {
			functionType(
				tuple(it),
				Types.TOP.o
			)
		}

	/**
	 * Check that the subtype relation covaries under the "type-of" mapping.
	 * This is simply covariance of metatypes, which is abbreviated as
	 * metacovariance.
	 *
	 * &forall;x,y&isin;T : x&sube;y &rarr; T(x)&sube;T(y)
	 */
	@Test
	fun testMetacovariance() =
		checkCovariance("metacovariance") { instanceMeta(it) }

	/**
	 * Check that the type union of two types' types is the same as the type of
	 * their type union.  Namely,
	 *
	 * &forall;x,y&isin;T : (T(x)&thinsp;&cup;&thinsp;T(y)) = T(x&cup;y)
	 */
	@Test
	fun testTypeUnionMetainvariance()
	{
		for (x in Node.values)
		{
			for (y in Node.values)
			{
				val Tx = instanceMeta(x.t)
				val Ty = instanceMeta(y.t)
				val xuy = x.t.typeUnion(y.t)
				val T_xuy: A_BasicObject = instanceMeta(
					xuy)
				val TxuTy: A_BasicObject = Tx.typeUnion(Ty)
				assertEQ(
					T_xuy,
					TxuTy, "type union metainvariance: "
					+ "x=%s, y=%s, T(x∪y)=%s, T(x)∪T(y)=%s",
					x,
					y,
					T_xuy,
					TxuTy)
			}
		}
	}

	/**
	 * Check that the type intersection of two types' types is the same as the
	 * type of their type intersection.  Namely,
	 *
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(T(x)&cap;T(y) = T(x&cap;y))
	 */
	@Test
	fun testTypeIntersectionMetainvariance()
	{
		for (x in Node.values)
		{
			for (y in Node.values)
			{
				val Tx = instanceMeta(x.t)
				val Ty = instanceMeta(y.t)
				val xny = x.t.typeIntersection(y.t)
				val T_xny = instanceMeta(
					xny)
				val TxnTy = Tx.typeIntersection(Ty)
				assertEQ(
					T_xny,
					TxnTy,
					"type intersection metainvariance: " +
						"x=%s, y=%s, T(x∩y)=%s, T(x)∩T(y)=%s",
					x,
					y,
					T_xny,
					TxnTy)
			}
		}
	}

	companion object
	{
		/**
		 * Test fixture: clear and then create all special objects well-known to
		 * the Avail runtime, then set up the graph of types.
		 */
		@Suppress("unused")
		@BeforeAll
		@JvmStatic
		fun initializeAllWellKnownObjects()
		{
			// Force early initialization of the Avail runtime in order to
			// prevent initialization errors.
			specialAtoms()
			Node.createTypes()
			@Suppress("ConstantConditionIf")
			if (false)
			{
				System.out.format(
					"Checking %d types%n",
					Node.values.size)
				dumpGraphTo(System.out)
			}
		}

		/**
		 * Output a machine-readable representation of the graph as a sequence
		 * of lines of text.  First output the number of nodes, then the
		 * single-quoted node names in some order.  Then output all edges as
		 * parenthesis-enclosed space-separated pairs of zero-based indices into
		 * the list of nodes.  The first element is the subtype, the second is
		 * the supertype.  The graph has not been reduced to eliminate redundant
		 * edges.
		 *
		 * The nodes include everything in {Node.values}, as well as all type
		 * unions and type intersections of two or three of these base elements,
		 * including the left and right associative versions in case the type
		 * system is incorrect.
		 *
		 * @param out
		 *   A PrintStream on which to dump a representation of the current type
		 *   graph.
		 */
		private fun dumpGraphTo(out: PrintStream)
		{
			val allTypes = mutableSetOf<A_Type>()
			for (node in Node.values)
			{
				allTypes.add(node.t)
			}
			for (t1 in Node.values)
			{
				for (t2 in Node.values)
				{
					val union12 = t1.union(t2)
					allTypes.add(union12)
					val inter12 = t1.intersect(t2)
					allTypes.add(inter12)
					for (t3 in Node.values)
					{
						allTypes.add(union12.typeUnion(t3.t))
						allTypes.add(t3.t.typeUnion(union12))
						allTypes.add(inter12.typeIntersection(t3.t))
						allTypes.add(t3.t.typeIntersection(inter12))
					}
				}
			}
			val allTypesList = allTypes.toList()
			val inverse: MutableMap<A_Type?, Int> = HashMap()
			val names = arrayOfNulls<String>(allTypes.size)
			for (i in allTypesList.indices)
			{
				inverse[allTypesList[i]] = i
			}
			for (node in Node.values)
			{
				names[inverse[node.t]!!] = "#" + node.name
			}
			for (i in allTypesList.indices)
			{
				if (names[i] === null)
				{
					names[i] = allTypesList[i].toString()
				}
			}
			out.println(allTypesList.size)
			for (i1 in allTypesList.indices)
			{
				out.println("'" + names[i1] + "'")
			}
			for (i1 in allTypes.indices)
			{
				for (i2 in allTypes.indices)
				{
					if (allTypesList[i1].isSubtypeOf(allTypesList[i2]))
					{
						out.println("($i1 $i2)")
					}
				}
			}
		}

		/**
		 * Test fixture: clear all special objects, wiping each `Node`'s type.
		 */
		@Suppress("unused")
		@AfterAll
		fun clearAllWellKnownObjects()
		{
			Node.eraseTypes()
		}

		/**
		 * Compare the first two arguments for [equality][Object.equals].  If
		 * unequal, use the supplied message pattern and message  arguments to
		 * construct an error message, then fail with it.
		 *
		 * @param a
		 *   The first object to compare.
		 * @param b
		 *   The second object to compare.
		 * @param messagePattern
		 *   A format string for producing an error message in the event that
		 *   the objects are not equal.
		 * @param messageArguments
		 *   A variable number of objects to describe via the messagePattern.
		 */
		private fun assertEQ(
			a: Any,
			b: Any,
			messagePattern: String,
			vararg messageArguments: Any)
		{
			if (a != b)
			{
				Assertions.fail<Any>(
					String.format(messagePattern, *messageArguments))
			}
		}

		/**
		 * Examine the first (boolean) argument.  If false, use the supplied
		 * message pattern and message arguments to construct an error message,
		 * then fail with it.
		 *
		 * @param bool
		 *   The boolean which should be true for success.
		 * @param messagePattern
		 *   A format string for producing an error message in the event that
		 *   the supplied boolean was false.
		 * @param messageArguments
		 *   A variable number of objects to describe via the messagePattern.
		 */
		private fun assertT(
			bool: Boolean,
			messagePattern: String,
			vararg messageArguments: Any)
		{
			if (!bool)
			{
				Assertions.fail<Any>(
					String.format(messagePattern, *messageArguments))
			}
		}

		/**
		 * Check that the subtype relation *covaries* with the given function
		 * that maps each type to another type.
		 *
		 * &forall;x,y&isin;T : x&sube;y &rarr; Co(x)&sube;Co(y)
		 *
		 * @param name
		 *   The name of the relation being checked.
		 * @param transform
		 *   The covariant transformation function to check.
		 */
		private fun checkCovariance(
			name: String,
			transform: (A_Type) -> A_Type)
		{
			for (x in Node.values)
			{
				val CoX = transform(x.t)
				for (y in Node.values)
				{
					val CoY = transform(y.t)
					assertT(
						!x.subtype(y) || CoX.isSubtypeOf(CoY),
						"covariance (%s): %s, %s",
						name,
						x,
						y)
				}
			}
		}

		/**
		 * Check that the subtype relation *contravaries* with the given
		 * function that maps each type to another type.
		 *
		 * &forall;x,y&isin;T : x&sube;y &rarr; Con(y)&sube;Con(x)
		 *
		 * @param name
		 *   The name of the relation being checked.
		 * @param transform
		 *   The contravariant transformation function to check.
		 */
		private fun checkContravariance(
			name: String,
			transform: (A_Type) -> A_Type)
		{
			for (x in Node.values)
			{
				val ConX = transform(x.t)
				for (y in Node.values)
				{
					val ConY = transform(y.t)
					assertT(
						!x.subtype(y) || ConY.isSubtypeOf(ConX),
						"contravariance (%s): %s, %s",
						name,
						x,
						y)
				}
			}
		}
	}
}
