/**
 * TypeConsistencyTest.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.test;

import static org.junit.Assert.*;
import static com.avail.descriptor.TypeDescriptor.Types;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.util.*;
import org.junit.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.interpreter.Primitive;


/**
 * Test various consistency properties for {@linkplain TypeDescriptor types} in
 * Avail.  The type system is really pretty complex, so these tests are quite
 * important.
 *
 * <p>
 * Here are some things to test.  T is the set of types, T(x) means the type of
 * x, Co(x) is some relation between a type and its parameters that's supposed
 * to be covariant, Con(x) is some relation that's supposed to be contravariant,
 * &cup; is type union, and &cap; is type intersection.
 *
 * <table border=1 cellspacing=0>
 * <tr>
 *     <td>Subtype reflexivity</td>
 *     <td>&forall;<sub>x&isin;T</sub>&thinsp;x&sube;x</td>
 * </tr><tr>
 *     <td>Subtype transitivity</td>
 *     <td>&forall;<sub>x,y,z&isin;T</sub>&thinsp;(x&sube;y&thinsp;&and;&thinsp;y&sube;z
 *             &rarr; x&sube;z)</td>
 * </tr><tr>
 *     <td>Subtype asymmetry</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&sub;y &rarr; &not;y&sub;x)
 *         <br>
 *         <em>or alternatively,</em>
 *         <br>
 *         &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y&thinsp;&and;&thinsp;y&sube;x
 *         = (x=y))</td>
 * </tr><tr>
 *     <td>Union closure</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&cup;y&thinsp;&isin;&thinsp;T)</td>
 * </tr><tr>
 *     <td>Union reflexivity</td>
 *     <td>&forall;<sub>x&isin;T</sub>&thinsp;(x&cup;x&thinsp;=&thinsp;x)</td>
 * </tr><tr>
 *     <td>Union commutativity</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&cup;y = y&cup;x)</td>
 * </tr><tr>
 *     <td>Union associativity</td>
 *     <td>&forall;<sub>x,y,z&isin;T</sub>&thinsp;(x&cup;y)&cup;z = x&cup;(y&cup;z)</td>
 * </tr><tr>
 *     <td>Intersection closure</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&cap;y&thinsp;&isin;&thinsp;T)</td>
 * </tr><tr>
 *     <td>Intersection reflexivity</td>
 *     <td>&forall;<sub>x&isin;T</sub>&thinsp;(x&cap;x&thinsp;=&thinsp;x)</td>
 * </tr><tr>
 *     <td>Intersection commutativity</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&cap;y = y&cap;x)</td>
 * </tr><tr>
 *     <td>Intersection associativity</td>
 *     <td>&forall;<sub>x,y,z&isin;T</sub>&thinsp;(x&cap;y)&cap;z = x&cap;(y&cap;z)</td>
 * </tr><tr>
 *     <td>Various covariance relationships (Co)</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; Co(x)&sube;Co(y))</td>
 * </tr><tr>
 *     <td>Various contravariance relationships (Con)</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; Con(y)&sube;Con(x))</td>
 * </tr><tr>
 *     <td>Metacovariance</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; T(x)&sube;T(y))</td>
 * </tr><tr>
 *     <td>Type union metainvariance</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(T(x)&cup;T(y) = T(x&cup;y))</td>
 * </tr>
 * </tr><tr>
 *     <td>Type intersection metainvariance</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(T(x)&cap;T(y) = T(x&cap;y))</td>
 * </tr><tr>
 *     <td>Instantiation metainvariance</td>
 *     <td>&forall;<sub>b&isin;T,a</sub>&thinsp;(a&isin;b = T(a)&isin;T(b))</td>
 * </tr>
 * </table>
 * </p>
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class TypeConsistencyTest
{
	/**
	 * {@code Node} records its instances upon creation.  They must be created
	 * in top-down order (i.e., supertypes before subtypes), as the {@link
	 * Node#Node(String, Node...) constructor} takes a variable number of
	 * supertype nodes.  The node supertype declarations are checked against the
	 * actual properties of the underlying types as one of the fundamental
	 * {@linkplain TypeConsistencyTest consistency checks}.
	 *
	 * <p>
	 * All {@link com.avail.descriptor.TypeDescriptor.Types} are included, as
	 * well as a few simple representative samples, such as the one-element
	 * string type and the type of whole numbers.
	 * </p>
	 */
	public abstract static class Node
	{
		/**
		 * The list of all currently defined {@linkplain Node type nodes}.
		 */
		static final List<Node> values = new ArrayList<>();

		/**
		 * A mapping from {@link com.avail.descriptor.TypeDescriptor.Types} to
		 * their corresponding {@link Node}s.
		 */
		private static final EnumMap<Types, Node> primitiveTypes =
			new EnumMap<>(Types.class);

		static
		{
			// Include all primitive types.
			for (final Types type : Types.all())
			{
				if (!primitiveTypes.containsKey(type))
				{
					final Types typeParent = type.parent;
					final Node [] parents =
						new Node[typeParent == null ? 0 : 1];
					if (typeParent != null)
					{
						parents[0] = primitiveTypes.get(typeParent);
					}
					final Node node = new Node(type.name(), parents)
					{
						@Override A_Type get ()
						{
							return type.o();
						}
					};
					primitiveTypes.put(type, node);
				}
			}
		}

		/** The most general metatype. */
		static final Node TOP_META = new Node(
			"TOP_META",
			primitiveTypes.get(Types.ANY))
		{
			@Override A_Type get ()
			{
				return InstanceMetaDescriptor.topMeta();
			}
		};

		/** The type of {@code any}. */
		static final Node ANY_META = new Node(
			"ANY_META",
			TOP_META)
		{
			@Override A_Type get ()
			{
				return InstanceMetaDescriptor.anyMeta();
			}
		};

		/** The type of {@code nontype}. */
		static final Node NONTYPE_META = new Node(
			"NONTYPE_META",
			ANY_META)
		{
			@Override A_Type get ()
			{
				return InstanceMetaDescriptor.on(Types.NONTYPE.o());
			}
		};

		/** The type {@code tuple} */
		static final Node TUPLE = new Node(
			"TUPLE",
			primitiveTypes.get(Types.NONTYPE))
		{
			@Override A_Type get ()
			{
				return TupleTypeDescriptor.mostGeneralType();
			}
		};

		/**
		 * The type {@code string}, which is the same as {@code tuple of
		 * character}
		 */
		static final Node STRING = new Node("STRING", TUPLE)
		{
			@Override A_Type get ()
			{
				return TupleTypeDescriptor.stringType();
			}
		};

		/** The type {@code tuple [1..1] of character} */
		static final Node UNIT_STRING = new Node("UNIT_STRING", STRING)
		{
			@Override A_Type get ()
			{
				return StringDescriptor.from("x").kind();
			}
		};

		/** The type {@code type of <>} */
		static final Node EMPTY_TUPLE = new Node("EMPTY_TUPLE", TUPLE, STRING)
		{
			@Override A_Type get ()
			{
				return TupleDescriptor.empty().kind();
			}
		};

		/** The type {@code set} */
		static final Node SET = new Node(
			"SET",
			primitiveTypes.get(Types.NONTYPE))
		{
			@Override A_Type get ()
			{
				return SetTypeDescriptor.mostGeneralType();
			}
		};

		/** The most general fiber type. */
		static final Node FIBER = new Node(
			"FIBER",
			primitiveTypes.get(Types.NONTYPE))
		{
			@Override A_Type get ()
			{
				return FiberTypeDescriptor.mostGeneralType();
			}
		};

		/** The most general function type. */
		static final Node MOST_GENERAL_FUNCTION = new Node(
			"MOST_GENERAL_FUNCTION",
			primitiveTypes.get(Types.NONTYPE))
		{
			@Override A_Type get ()
			{
				return FunctionTypeDescriptor.mostGeneralType();
			}
		};

		/**
		 * The type for functions that accept no arguments and return an integer.
		 */
		static final Node NOTHING_TO_INT_FUNCTION = new Node(
			"NOTHING_TO_INT_FUNCTION",
			MOST_GENERAL_FUNCTION)
		{
			@Override A_Type get ()
			{
				return FunctionTypeDescriptor.create(
					TupleDescriptor.empty(),
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/**
		 * The type for functions that accept an integer and return an integer.
		 */
		static final Node INT_TO_INT_FUNCTION = new Node(
			"INT_TO_INT_FUNCTION",
			MOST_GENERAL_FUNCTION)
		{
			@Override A_Type get ()
			{
				return FunctionTypeDescriptor.create(
					TupleDescriptor.from(IntegerRangeTypeDescriptor.integers()),
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/**
		 * The type for functions that accept two integers and return an integer.
		 */
		static final Node INTS_TO_INT_FUNCTION = new Node(
			"INTS_TO_INT_FUNCTION",
			MOST_GENERAL_FUNCTION)
		{
			@Override A_Type get ()
			{
				return FunctionTypeDescriptor.create(
					TupleDescriptor.from(
						IntegerRangeTypeDescriptor.integers(),
						IntegerRangeTypeDescriptor.integers()),
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/** The most specific function type, other than bottom. */
		static final Node MOST_SPECIFIC_FUNCTION = new Node(
			"MOST_SPECIFIC_FUNCTION",
			NOTHING_TO_INT_FUNCTION,
			INT_TO_INT_FUNCTION,
			INTS_TO_INT_FUNCTION)
		{
			@Override A_Type get ()
			{
				return FunctionTypeDescriptor.createWithArgumentTupleType(
					TupleTypeDescriptor.mostGeneralType(),
					BottomTypeDescriptor.bottom(),
					SetDescriptor.empty());
			}
		};

		/** The primitive type representing the extended integers [-∞..∞]. */
		static final Node EXTENDED_INTEGER = new Node(
			"EXTENDED_INTEGER",
			primitiveTypes.get(Types.NUMBER))
		{
			@Override A_Type get ()
			{
				return IntegerRangeTypeDescriptor.extendedIntegers();
			}
		};

		/** The primitive type representing whole numbers [0..∞). */
		static final Node WHOLE_NUMBER = new Node(
			"WHOLE_NUMBER",
			EXTENDED_INTEGER)
		{
			@Override A_Type get ()
			{
				return IntegerRangeTypeDescriptor.wholeNumbers();
			}
		};

		/** Some {@linkplain AtomDescriptor atom}'s instance type. */
		static final Node SOME_ATOM_TYPE = new Node(
			"SOME_ATOM_TYPE",
			primitiveTypes.get(Types.ATOM))
		{
			@Override A_Type get ()
			{
				return InstanceTypeDescriptor.on(
					AtomDescriptor.create(
						StringDescriptor.from("something"),
						NilDescriptor.nil()));
			}
		};

		/**
		 * The instance type of an {@linkplain AtomDescriptor atom} different
		 * from {@link #SOME_ATOM_TYPE}.
		 */
		static final Node ANOTHER_ATOM_TYPE = new Node(
			"ANOTHER_ATOM_TYPE",
			primitiveTypes.get(Types.ATOM))
		{
			@Override A_Type get ()
			{
				return InstanceTypeDescriptor.on(
					AtomDescriptor.create(
						StringDescriptor.from("another"),
						NilDescriptor.nil()));
			}
		};

		/**
		 * The base {@linkplain ObjectTypeDescriptor object type}.
		 */
		static final Node OBJECT_TYPE = new Node(
			"OBJECT_TYPE",
			primitiveTypes.get(Types.NONTYPE))
		{
			@Override A_Type get ()
			{
				return ObjectTypeDescriptor.mostGeneralType();
			}
		};

		/**
		 * A simple non-root {@linkplain ObjectTypeDescriptor object type}.
		 */
		static final Node NON_ROOT_OBJECT_TYPE = new Node(
			"NON_ROOT_OBJECT_TYPE",
			OBJECT_TYPE)
		{
			@Override A_Type get ()
			{
				return ObjectTypeDescriptor.objectTypeFromMap(
					MapDescriptor.empty().mapAtPuttingCanDestroy(
						SOME_ATOM_TYPE.t(),
						TypeDescriptor.Types.ANY.o(),
						false));
			}
		};

		/**
		 * A simple non-root {@linkplain ObjectTypeDescriptor object type}.
		 */
		static final Node NON_ROOT_OBJECT_TYPE_WITH_INTEGERS = new Node(
			"NON_ROOT_OBJECT_TYPE_WITH_INTEGERS",
			NON_ROOT_OBJECT_TYPE)
		{
			@Override A_Type get ()
			{
				return ObjectTypeDescriptor.objectTypeFromMap(
					MapDescriptor.empty().mapAtPuttingCanDestroy(
						SOME_ATOM_TYPE.t(),
						IntegerRangeTypeDescriptor.integers(),
						false));
			}
		};

		/**
		 * A simple non-root {@linkplain ObjectTypeDescriptor object type}.
		 */
		static final Node NON_ROOT_OBJECT_TYPE_WITH_DIFFERENT_KEY = new Node(
			"NON_ROOT_OBJECT_TYPE_WITH_DIFFERENT_KEY",
			OBJECT_TYPE)
		{
			@Override A_Type get ()
			{
				return ObjectTypeDescriptor.objectTypeFromMap(
					MapDescriptor.empty().mapAtPuttingCanDestroy(
						ANOTHER_ATOM_TYPE.t(),
						TypeDescriptor.Types.ANY.o(),
						false));
			}
		};

		/**
		 * The most general {@linkplain PojoTypeDescriptor pojo type}.
		 */
		static final Node MOST_GENERAL_POJO = new Node(
			"MOST_GENERAL_POJO",
			primitiveTypes.get(Types.NONTYPE))
		{
			@Override
			A_Type get ()
			{
				return PojoTypeDescriptor.mostGeneralType();
			}
		};

		/**
		 * The pojo type representing {@link Comparable}&lt;{@link Object}&gt;.
		 */
		static final Node COMPARABLE_OF_JAVA_OBJECT_POJO = new Node(
			"COMPARABLE_OF_JAVA_OBJECT_POJO",
			MOST_GENERAL_POJO)
		{
			@Override
			A_Type get ()
			{
				return PojoTypeDescriptor.forClassWithTypeArguments(
					Comparable.class,
					TupleDescriptor.from(
						PojoTypeDescriptor.mostGeneralType()));
			}
		};

		/**
		 * The pojo type representing {@link Comparable}&lt;{@link Integer}&gt;.
		 */
		static final Node COMPARABLE_OF_JAVA_INTEGER_POJO = new Node(
			"COMPARABLE_OF_JAVA_INTEGER_POJO",
			COMPARABLE_OF_JAVA_OBJECT_POJO)
		{
			@Override
			A_Type get ()
			{
				return PojoTypeDescriptor.forClassWithTypeArguments(
					Comparable.class,
					TupleDescriptor.from(
						PojoTypeDescriptor.forClass(Integer.class)));
			}
		};

		/**
		 * The pojo type representing {@link Integer}.
		 */
		static final Node JAVA_INTEGER_POJO = new Node(
			"JAVA_INTEGER_POJO",
			COMPARABLE_OF_JAVA_INTEGER_POJO)
		{
			@Override
			A_Type get ()
			{
				return PojoTypeDescriptor.forClass(Integer.class);
			}
		};

		/**
		 * The pojo type representing {@link Comparable}&lt;{@link String}&gt;.
		 */
		static final Node COMPARABLE_OF_JAVA_STRING_POJO = new Node(
			"COMPARABLE_OF_JAVA_STRING_POJO",
			COMPARABLE_OF_JAVA_OBJECT_POJO)
		{
			@Override
			A_Type get ()
			{
				return PojoTypeDescriptor.forClassWithTypeArguments(
					Comparable.class,
					TupleDescriptor.from(
						PojoTypeDescriptor.forClass(String.class)));
			}
		};

		/**
		 * The pojo type representing {@link String}.
		 */
		static final Node JAVA_STRING_POJO = new Node(
			"JAVA_STRING_POJO",
			COMPARABLE_OF_JAVA_STRING_POJO)
		{
			@Override
			A_Type get ()
			{
				return PojoTypeDescriptor.forClass(String.class);
			}
		};

		/**
		 * The pojo type representing {@link Enum}&lt;<em>self type</em>&gt;.
		 * Note that this type isn't actually supported by Java directly, since
		 * it would look like
		 * Enum&lt;Enum&lt;Enum&lt;Enum&lt;...&gt;&gt;&gt;&gt;, which cannot
		 * actually be written as a Java type expression.  This pojo type is the
		 * most general Java enumeration type.
		 */
		final static Node JAVA_ENUM_POJO = new Node(
			"JAVA_ENUM_POJO",
			COMPARABLE_OF_JAVA_OBJECT_POJO)
		{
			@Override
			A_Type get ()
			{
				return PojoTypeDescriptor.forClassWithTypeArguments(
					Enum.class,
					TupleDescriptor.from(
						PojoTypeDescriptor.selfTypeForClass(
							Enum.class)));
			}
		};

		/**
		 * The pojo type representing the Java enumeration {@link
		 * com.avail.interpreter.Primitive.Result}.
		 */
		final static Node AVAIL_PRIMITIVE_RESULT_ENUM_POJO = new Node(
			"AVAIL_PRIMITIVE_RESULT_ENUM_POJO",
			JAVA_ENUM_POJO)
		{
			@Override
			A_Type get ()
			{
				return PojoTypeDescriptor.forClass(Primitive.Result.class);
			}
		};

		/**
		 * The pojo type representing {@link Comparable}&lt;<em>Avail's integer
		 * type</em>&gt;.  Note that this is a Java type parameterized by an
		 * Avail type.
		 */
		static final Node COMPARABLE_OF_AVAIL_INTEGER_POJO = new Node(
			"COMPARABLE_OF_AVAIL_INTEGER_POJO",
			MOST_GENERAL_POJO)
		{
			@Override
			A_Type get ()
			{
				return PojoTypeDescriptor.forClassWithTypeArguments(
					Comparable.class,
					TupleDescriptor.from(
						IntegerRangeTypeDescriptor.integers()));
			}
		};

		/**
		 * The pojo type representing the Java {@link Array} type {@link
		 * Object}[].
		 */
		static final Node JAVA_OBJECT_ARRAY_POJO = new Node(
			"JAVA_OBJECT_ARRAY_POJO",
			MOST_GENERAL_POJO)
		{
			@Override
			A_Type get ()
			{
				return PojoTypeDescriptor.forArrayTypeWithSizeRange(
					PojoTypeDescriptor.mostGeneralType(),
					IntegerRangeTypeDescriptor.wholeNumbers());
			}
		};

		/**
		 * The pojo type representing the Java {@link Array} type {@link
		 * String}[].
		 */
		static final Node JAVA_STRING_ARRAY_POJO = new Node(
			"JAVA_STRING_ARRAY_POJO",
			JAVA_OBJECT_ARRAY_POJO)
		{
			@Override
			A_Type get ()
			{
				return PojoTypeDescriptor.forArrayTypeWithSizeRange(
					JAVA_STRING_POJO.t(),
					IntegerRangeTypeDescriptor.wholeNumbers());
			}
		};

		/**
		 * {@linkplain PojoTypeDescriptor Pojo bottom}.
		 */
		static final Node POJO_BOTTOM = new Node(
			"POJO_BOTTOM",
			JAVA_INTEGER_POJO,
			JAVA_STRING_POJO,
			AVAIL_PRIMITIVE_RESULT_ENUM_POJO,
			COMPARABLE_OF_AVAIL_INTEGER_POJO,
			JAVA_STRING_ARRAY_POJO)
		{
			@Override
			A_Type get ()
			{
				return BottomPojoTypeDescriptor.pojoBottom();
			}
		};

		/**
		 * The metatype for function types.
		 */
		static final Node FUNCTION_META = new Node(
			"FUNCTION_META",
			NONTYPE_META)
		{
			@Override A_Type get ()
			{
				return FunctionTypeDescriptor.meta();
			}
		};

		/**
		 * The metatype for continuation types.
		 */
		static final Node CONTINUATION_META = new Node(
			"CONTINUATION_META",
			NONTYPE_META)
		{
			@Override A_Type get ()
			{
				return ContinuationTypeDescriptor.meta();
			}
		};

		/**
		 * The metatype for integer types.
		 */
		static final Node INTEGER_META = new Node(
			"INTEGER_META",
			NONTYPE_META)
		{
			@Override A_Type get ()
			{
				return IntegerRangeTypeDescriptor.meta();
			}
		};

		/** The primitive type representing the metatype of whole numbers [0..∞). */
		static final Node WHOLE_NUMBER_META = new Node(
			"WHOLE_NUMBER_META",
			INTEGER_META)
		{
			@Override A_Type get ()
			{
				return InstanceMetaDescriptor.on(
					IntegerRangeTypeDescriptor.wholeNumbers());
			}
		};

		/**
		 * The primitive type representing the metametatype of the metatype of
		 * whole numbers [0..∞).
		 */
		static final Node WHOLE_NUMBER_META_META = new Node(
			"WHOLE_NUMBER_META_META",
			ANY_META,
			TOP_META)
		{
			@Override A_Type get ()
			{
				return InstanceMetaDescriptor.on(
					InstanceMetaDescriptor.on(
						IntegerRangeTypeDescriptor.wholeNumbers()));
			}
		};

		/**
		 * The most general {@linkplain VariableTypeDescriptor variable type}.
		 */
		static final Node ROOT_VARIABLE = new Node(
			"ROOT_VARIABLE",
			primitiveTypes.get(Types.NONTYPE))
		{
			@Override A_Type get ()
			{
				return VariableTypeDescriptor.mostGeneralType();
			}
		};

		/**
		 * The {@linkplain VariableTypeDescriptor type of variable} which
		 * holds {@linkplain IntegerDescriptor integers}.
		 */
		static final Node INT_VARIABLE = new Node(
			"INT_VARIABLE",
			ROOT_VARIABLE)
		{
			@Override A_Type get ()
			{
				return VariableTypeDescriptor.wrapInnerType(
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/**
		 * The {@linkplain VariableTypeDescriptor type of variable} which
		 * holds only a particular atom.
		 */
		static final Node SOME_ATOM_VARIABLE = new Node(
			"SOME_ATOM_VARIABLE",
			ROOT_VARIABLE)
		{
			@Override A_Type get ()
			{
				return VariableTypeDescriptor.wrapInnerType(SOME_ATOM_TYPE.t());
			}
		};

		/**
		 * The most specific {@linkplain VariableTypeDescriptor type of
		 * variable}, other than {@linkplain BottomTypeDescriptor bottom}.
		 */
		static final Node BOTTOM_VARIABLE = new Node(
			"BOTTOM_VARIABLE",
			INT_VARIABLE,
			SOME_ATOM_VARIABLE)
		{
			@Override A_Type get ()
			{
				return VariableTypeDescriptor.fromReadAndWriteTypes(
					BottomTypeDescriptor.bottom(),
					Types.TOP.o());
			}
		};

		/**
		 * The {@linkplain LiteralTokenTypeDescriptor literal token type} whose
		 * literal type is {@link Types#ANY}.
		 */
		static final Node ANY_LITERAL_TOKEN = new Node(
			"ANY_LITERAL_TOKEN",
			primitiveTypes.get(Types.TOKEN))
		{
			@Override A_Type get ()
			{
				return LiteralTokenTypeDescriptor.mostGeneralType();
			}
		};

		/**
		 * The {@linkplain LiteralTokenTypeDescriptor literal token type} whose
		 * literal must be an {@linkplain IntegerDescriptor integer}.
		 */
		static final Node INT_LITERAL_TOKEN = new Node(
			"INT_LITERAL_TOKEN",
			ANY_LITERAL_TOKEN)
		{
			@Override A_Type get ()
			{
				return LiteralTokenTypeDescriptor.create(
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/**
		 * The {@linkplain LiteralTokenTypeDescriptor literal token type} whose
		 * literal must be a particular {@linkplain AtomDescriptor atom}.
		 */
		static final Node SOME_ATOM_LITERAL_TOKEN = new Node(
			"SOME_ATOM_LITERAL_TOKEN",
			ANY_LITERAL_TOKEN)
		{
			@Override A_Type get ()
			{
				return LiteralTokenTypeDescriptor.create(SOME_ATOM_TYPE.t());
			}
		};

		/**
		 * The most specific {@linkplain LiteralTokenTypeDescriptor literal
		 * token type}, other than {@linkplain BottomTypeDescriptor bottom}.
		 */
		static final Node BOTTOM_LITERAL_TOKEN = new Node(
			"BOTTOM_LITERAL_TOKEN",
			INT_LITERAL_TOKEN,
			SOME_ATOM_LITERAL_TOKEN)
		{
			@Override A_Type get ()
			{
				return LiteralTokenTypeDescriptor.create(
					BottomTypeDescriptor.bottom());
			}
		};

		/**
		 * The metatype for map types.
		 */
		static final Node MAP_META = new Node(
			"MAP_META",
			NONTYPE_META)
		{
			@Override A_Type get ()
			{
				return MapTypeDescriptor.meta();
			}
		};

		/**
		 * The metatype for set types.
		 */
		static final Node SET_META = new Node(
			"SET_META",
			NONTYPE_META)
		{
			@Override A_Type get ()
			{
				return SetTypeDescriptor.meta();
			}
		};

		/**
		 * The metatype for tuple types.
		 */
		static final Node TUPLE_META = new Node(
			"TUPLE_META",
			NONTYPE_META)
		{
			@Override A_Type get ()
			{
				return TupleTypeDescriptor.meta();
			}
		};

		/**
		 * The metatype for fiber types.
		 */
		static final Node FIBER_META = new Node(
			"FIBER_META",
			NONTYPE_META)
		{
			@Override A_Type get ()
			{
				return FiberTypeDescriptor.meta();
			}
		};

		/** The type of {@code bottom}.  This is the most specific meta. */
		static final Node BOTTOM_TYPE = new Node(
			"BOTTOM_TYPE",
			FIBER_META,
			FUNCTION_META,
			CONTINUATION_META,
			WHOLE_NUMBER_META,
			WHOLE_NUMBER_META_META,
			MAP_META,
			SET_META,
			TUPLE_META)
		{
			@Override A_Type get ()
			{
				return InstanceMetaDescriptor.on(
					BottomTypeDescriptor.bottom());
			}
		};

		/**
		 * A two tiered map from parse node kind to inner Node (or null) to
		 * parse node type Node.  This is used to construct the lattice of
		 * parse node type nodes incrementally.  A null indicates the inner
		 * type should be {@link #BOTTOM}, even though it hasn't been defined
		 * yet.
		 */
		static final Map<ParseNodeKind, Map<Node, Node>> parseNodeTypeMap =
			new HashMap<>();

		/**
		 * Create a parse node type Node with the given name, parse node kind,
		 * Node indicating the expressionType, and the array of Nodes that are
		 * supertypes of the expressionType.  Passing null for the
		 * expressionType causes {@linkplain BottomTypeDescriptor#bottom()
		 * the bottom type} to be used.  We can't use the node {@link #BOTTOM}
		 * because of circular dependency.
		 *
		 * @param nodeName
		 *            A {@link String} naming this node for diagnostics.
		 * @param parseNodeKind
		 *            The {@linkplain ParseNodeKind kind} of parse node type.
		 * @param innerNode
		 *            The expressionType of the resulting parse node type, or
		 *            null to indicate {@linkplain BottomTypeDescriptor#bottom()
		 *            bottom}.
		 * @param parentInnerNodes
		 *            An array of parent nodes of the innerNode.
		 */
		static void addHelper (
			final String nodeName,
			final ParseNodeKind parseNodeKind,
			final @Nullable Node innerNode,
			final Node... parentInnerNodes)
		{
			final Map<Node, Node> submap;
			if (parseNodeTypeMap.containsKey(parseNodeKind))
			{
				submap = parseNodeTypeMap.get(parseNodeKind);
			}
			else
			{
				submap = new HashMap<Node, Node>();
				parseNodeTypeMap.put(parseNodeKind, submap);
			}
			final List<Node> parents = new ArrayList<>();
			if (parseNodeKind.parentKind() == null)
			{
				parents.add(primitiveTypes.get(Types.NONTYPE));
			}
			else
			{
				parents.add(parseNodeTypeMap.get(
					parseNodeKind.parentKind()).get(innerNode));
			}
			for (final Node parentInnerNode : parentInnerNodes)
			{
				parents.add(submap.get(parentInnerNode));
			}
			final Node newNode = new Node(
				nodeName,
				parents.toArray(new Node[parents.size()]))
			{
				@Override
				A_Type get ()
				{
					final A_Type innerType =
						innerNode == null
							? BottomTypeDescriptor.bottom()
							: innerNode.t;
					final A_Type newType = parseNodeKind.create(innerType);
					assert newType.expressionType().equals(innerType)
						: "parse node kind was not parameterized as expected";
					return newType;
				}
			};
			submap.put(innerNode, newNode);
		}

		/**
		 * Deduce the relationships among the inner nodes of the kind, adding a
		 * parse node kind node for each inner node.
		 *
		 * @param kind
		 *        A {@linkplain ParseNodeKind parse node kind}.
		 * @param innerNodes
		 *        The nodes by which to parameterize this parse node kind.
		 */
		static void addMultiHelper (
			final ParseNodeKind kind,
			final Node... innerNodes)
		{
			for (final Node node : innerNodes)
			{
				final List<Node> ancestors = new ArrayList<>();
				if (node == null)
				{
					ancestors.addAll(Arrays.asList(innerNodes));
					ancestors.remove(null);
				}
				else
				{
					for (final Node possibleAncestor : innerNodes)
					{
						if (possibleAncestor != null
							&& node.allAncestors.contains(possibleAncestor))
						{
							ancestors.add(possibleAncestor);
						}
					}
				}
				assert !ancestors.contains(null);
				addHelper(
					String.format(
						"%s (%s)",
						kind.name(),
						node == null ? "BOTTOM" : node.name),
					kind,
					node,
					ancestors.toArray(new Node[ancestors.size()]));
			}
		}

		static
		{
			// Include all parse node types.  Include a minimal diamond of types
			// for each parse node kind.
			final Node topNode = primitiveTypes.get(Types.TOP);
			final Node anyNode = primitiveTypes.get(Types.ANY);
			final Node nontypeNode = primitiveTypes.get(Types.NONTYPE);
			final Node atomNode = SOME_ATOM_TYPE;
			final Node anotherAtomNode = ANOTHER_ATOM_TYPE;
			for (final ParseNodeKind kind : ParseNodeKind.all())
			{
				// This is future-proofing (for total coverage of parse node
				// kinds).
				switch (kind)
				{
					case BLOCK_NODE:
						addMultiHelper(
							kind,
							MOST_GENERAL_FUNCTION,
							NOTHING_TO_INT_FUNCTION,
							INT_TO_INT_FUNCTION,
							INTS_TO_INT_FUNCTION,
							MOST_SPECIFIC_FUNCTION,
							null);
						break;
					case EXPRESSION_NODE:
						addMultiHelper(
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
							ROOT_VARIABLE,
							INT_VARIABLE,
							SOME_ATOM_VARIABLE,
							BOTTOM_VARIABLE,
							UNIT_STRING,
							EMPTY_TUPLE,
							null);
						break;
					case MARKER_NODE:
						break;
					case ARGUMENT_NODE:
					case DECLARATION_NODE:
					case LABEL_NODE:
					case LOCAL_CONSTANT_NODE:
					case LOCAL_VARIABLE_NODE:
					case MODULE_CONSTANT_NODE:
					case PRIMITIVE_FAILURE_REASON_NODE:
					case MODULE_VARIABLE_NODE:
						addMultiHelper(
							kind,
							topNode,
							null);
						break;
					case REFERENCE_NODE:
						addMultiHelper(
							kind,
							ROOT_VARIABLE,
							INT_VARIABLE,
							SOME_ATOM_VARIABLE,
							BOTTOM_VARIABLE,
							null);
						break;
					case PARSE_NODE:
					case SEND_NODE:
					case SEQUENCE_NODE:
						addMultiHelper(
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
							ROOT_VARIABLE,
							INT_VARIABLE,
							SOME_ATOM_VARIABLE,
							BOTTOM_VARIABLE,
							UNIT_STRING,
							EMPTY_TUPLE,
							null);
						break;
					case FIRST_OF_SEQUENCE_NODE:
						addMultiHelper(
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
							ROOT_VARIABLE,
							INT_VARIABLE,
							SOME_ATOM_VARIABLE,
							BOTTOM_VARIABLE,
							UNIT_STRING,
							EMPTY_TUPLE,
							null);
						break;
					case LIST_NODE:
						addMultiHelper(
							kind,
							TUPLE,
							STRING,
							UNIT_STRING,
							EMPTY_TUPLE,
							null);
						break;
					case PERMUTED_LIST_NODE:
						addMultiHelper(
							kind,
							TUPLE,
							STRING,
							UNIT_STRING,
							EMPTY_TUPLE,
							null);
						break;
					case VARIABLE_USE_NODE:
					case ASSIGNMENT_NODE:
					case LITERAL_NODE:
						addMultiHelper(
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
							ROOT_VARIABLE,
							INT_VARIABLE,
							SOME_ATOM_VARIABLE,
							BOTTOM_VARIABLE,
							null);
						break;
					case MACRO_SUBSTITUTION:
						addMultiHelper(
							kind,
							topNode,
							anyNode);
				}
			}
		}


		/**
		 * The list of all {@link Node}s except BOTTOM.
		 */
		private static final List<Node> nonBottomTypes =
			new ArrayList<>();

		static
		{
			for (final Node existingType : values)
			{
				nonBottomTypes.add(existingType);
			}
		}

		/** The type {@code bottom} */
		static final Node BOTTOM = new Node(
			"BOTTOM",
			nonBottomTypes.toArray(new Node[0]))
		{
			@Override A_Type get ()
			{
				return BottomTypeDescriptor.bottom();
			}
		};




		/** The name of this type node, used for error diagnostics. */
		final String name;

		/** The Avail {@linkplain TypeDescriptor type} I represent in the graph. */
		@Nullable A_Type t;

		/**
		 * Answer the actual type that this Node represents.
		 *
		 * @return The {@link TypeDescriptor} type held by this node.
		 */
		final A_Type t ()
		{
			final A_Type type = t;
			assert type != null;
			return type;
		}

		/** A unique 0-based index for this {@code Node}. */
		final int index;

		/** The supernodes in the graph. */
		final Node [] supernodes;

		/** The set of subnodes in the graph. */
		private final Set<Node> subnodes = new HashSet<>();

		/** Every node from which this node descends. */
		final Set<Node> allAncestors;

		/** Every node descended from this one. */
		final Set<Node> allDescendants = new HashSet<>();

		/**
		 * A cache of type unions where I'm the left participant and the right
		 * participant (a Node) supplies its index for accessing the array.
		 */
		private A_Type[] unionCache = new A_Type[0];

		/**
		 * A cache of type intersections where I'm the left participant and the
		 * right participant (a Node) supplies its index for accessing the
		 * array.
		 */
		private A_Type[] intersectionCache = new A_Type[0];

		/**
		 * A cache of subtype tests where I'm the proposed subtype and the
		 * argument is the proposed supertype.  The value stored indicates if
		 * I am a subtype of the argument.
		 */
		private Boolean[] subtypeCache = new Boolean[0];

		/**
		 * Construct a new {@link Node}, capturing a varargs list of known
		 * supertypes.
		 *
		 * @param name
		 *            The printable name of this {@link Node}.
		 * @param supernodes
		 *            The array of {@linkplain Node nodes} that this node is
		 *            asserted to descend from.  Transitive ancestors may be
		 *            elided.
		 */
		Node (final String name, final Node... supernodes)
		{
			this.name = name;
			this.supernodes = supernodes;
			this.index = values.size();
			final Set<Node> ancestors = new HashSet<>();
			for (final Node supernode : supernodes)
			{
				ancestors.addAll(supernode.allAncestors);
			}
			ancestors.addAll(Arrays.asList(supernodes));
			allAncestors = Collections.unmodifiableSet(ancestors);
			assert !allAncestors.contains(null);
			values.add(this);
		}


		/* The nodes' slots have to be initialized here because they pass
		 * the Node.class to the EnumSet factory, which attempts to
		 * determine the number of enumeration values, which isn't known yet
		 * when the constructors are still running.
		 *
		 * Also build the inverse and (downwards) transitive function at each
		 * node of the graph, since they're independent of how the actual types
		 * are related.  Discrepancies between the graph information and the
		 * actual types is resolved in {@link
		 * TypeConsistencyTest#testGraphModel()}.
		 */
		static
		{
			for (final Node node : values)
			{
				for (final Node supernode : node.supernodes)
				{
					supernode.subnodes.add(node);
				}
			}
			for (final Node node : values)
			{
				node.allDescendants.add(node);
				node.allDescendants.addAll(node.subnodes);
			}
			boolean changed;
			do
			{
				changed = false;
				for (final Node node : values)
				{
					for (final Node subnode : node.subnodes)
					{
						changed |= node.allDescendants.addAll(
							subnode.allDescendants);
					}
				}
			}
			while (changed);
		}


		/**
		 * Enumeration instances are required to implement this to construct the
		 * actual Avail {@linkplain TypeDescriptor type} that this {@link Node}
		 * represents.
		 *
		 * @return The {@link AvailObject} that is the {@linkplain
		 *         TypeDescriptor type} that this {@link Node} represents.
		 */
		abstract A_Type get ();


		/**
		 * Lookup or compute and cache the type union of the receiver's {@link
		 * #t} and the argument's {@code t}.
		 *
		 * @param rightNode
		 *            The {@linkplain Node} for the right side of the union.
		 * @return
		 *            The {@linkplain AvailObject#typeUnion(A_Type) type
		 *            union} of the receiver's {@link #t} and the argument's
		 *            {@code t}.
		 */
		A_Type union (final Node rightNode)
		{
			final int rightIndex = rightNode.index;
			A_Type union = unionCache[rightIndex];
			if (union == null)
			{
				union = t().typeUnion(rightNode.t()).makeImmutable();
				assertTrue(t().isSubtypeOf(union));
				assertTrue(rightNode.t().isSubtypeOf(union));
				unionCache[rightIndex] = union;
			}
			return union;
		}

		/**
		 * Lookup or compute and cache the type intersection of the receiver's
		 * {@link #t} and the argument's {@code t}.
		 *
		 * @param rightNode
		 *            The {@linkplain Node} for the right side of the
		 *            intersection.
		 * @return
		 *            The {@linkplain AvailObject#typeIntersection(A_Type)
		 *            type intersection} of the receiver's {@link #t} and the
		 *            argument's {@code t}.
		 */
		A_Type intersect (final Node rightNode)
		{
			final int rightIndex = rightNode.index;
			A_Type intersection = intersectionCache[rightIndex];
			if (intersection == null)
			{
				intersection =
					t().typeIntersection(rightNode.t()).makeImmutable();
				assertTrue(intersection.isSubtypeOf(t()));
				assertTrue(intersection.isSubtypeOf(rightNode.t()));
				intersectionCache[rightIndex] = intersection;
			}
			return intersection;
		}

		/**
		 * Lookup or compute and cache whether the receiver's {@link #t} is a
		 * subtype of the argument's {@code t}.
		 *
		 * @param rightNode
		 *            The {@linkplain Node} for the right side of the subtype
		 *            test.
		 * @return
		 *            Whether the receiver's {@link #t} is a subtype of the
		 *            argument's {@code t}.
		 */
		boolean subtype (final Node rightNode)
		{
			final int rightIndex = rightNode.index;
			Boolean subtype = subtypeCache[rightIndex];
			if (subtype == null)
			{
				subtype = t().isSubtypeOf(rightNode.t());
				subtypeCache[rightIndex] = subtype;
			}
			return subtype;
		}

		@Override
		public String toString()
		{
			return name;
		}

		/**
		 * Record the actual type information into the graph.
		 */
		static void createTypes ()
		{
			final int n = values.size();
			for (final Node node : values)
			{
				node.t = node.get();
				node.unionCache = new AvailObject[n];
				node.intersectionCache = new AvailObject[n];
				node.subtypeCache = new Boolean[n];
			}
		}

		/**
		 * Remove all type information from the graph, leaving the shape intact.
		 */
		static void eraseTypes ()
		{
			for (final Node node : values)
			{
				node.t = null;
				node.unionCache = new A_Type[0];
				node.intersectionCache = new A_Type[0];
				node.subtypeCache = new Boolean[0];
			}
		}

	}

	/**
	 * Test fixture: clear and then create all special objects well-known to the
	 * Avail runtime, then set up the graph of types.
	 */
	@BeforeClass
	public static void initializeAllWellKnownObjects ()
	{
		// Force early initialization of the Avail runtime in order to prevent
		// initialization errors.
		AvailRuntime.specialAtoms();
		Node.createTypes();
		System.out.format("Checking %d types%n", Node.values.size());

		// dumpGraphTo(System.out);
	}



	/**
	 * Output a machine-readable representation of the graph as a sequence of
	 * lines of text.  First output the number of nodes, then the single-quoted
	 * node names in some order.  Then output all edges as parenthesis-enclosed
	 * space-separated pairs of zero-based indices into the list of nodes.  The
	 * first element is the subtype, the second is the supertype.  The graph has
	 * not been reduced to eliminate redundant edges.
	 *
	 * <p>
	 * The nodes include everything in {Node.values}, as well as all type unions
	 * and type intersections of two or three of these base elements, including
	 * the left and right associative versions in case the type system is
	 * incorrect.
	 * </p>
	 *
	 * @param out
	 *            A PrintStream on which to dump a representation of the current
	 *            type graph.
	 */
	public static void dumpGraphTo (final PrintStream out)
	{
		final Set<A_Type> allTypes = new HashSet<>();
		for (final Node node : Node.values)
		{
			allTypes.add(node.t);
		}
		for (final Node t1 : Node.values)
		{
			for (final Node t2 : Node.values)
			{
				final A_Type union12 = t1.union(t2);
				allTypes.add(union12);
				final A_Type inter12 = t1.intersect(t2);
				allTypes.add(inter12);
				for (final Node t3 : Node.values)
				{
					allTypes.add(union12.typeUnion(t3.t()));
					allTypes.add(t3.t().typeUnion(union12));
					allTypes.add(inter12.typeIntersection(t3.t()));
					allTypes.add(t3.t().typeIntersection(inter12));
				}
			}
		}
		final List<A_Type> allTypesList = new ArrayList<>(allTypes);
		final Map<A_Type,Integer> inverse = new HashMap<>();
		final String[] names = new String[allTypes.size()];
		for (int i = 0; i < allTypesList.size(); i++)
		{
			inverse.put(allTypesList.get(i), i);
		}
		for (final Node node : Node.values)
		{
			names[inverse.get(node.t)] = "#" + node.name;
		}
		for (int i = 0; i < allTypesList.size(); i++)
		{
			if (names[i] == null)
			{
				names[i] = allTypesList.get(i).toString();
			}
		}

		out.println(allTypesList.size());
		for (int i1 = 0; i1 < allTypesList.size(); i1++)
		{
			out.println("\'" + names[i1] + "\'");
		}
		for (int i1 = 0; i1 < allTypes.size(); i1++)
		{
			for (int i2 = 0; i2 < allTypes.size(); i2++)
			{
				if (allTypesList.get(i1).isSubtypeOf(allTypesList.get(i2)))
				{
					out.println("(" + i1 + " " + i2 + ")");
				}

			}
		}
	}



	/**
	 * Test fixture: clear all special objects, wiping each {@link Node}'s type.
	 */
	@AfterClass
	public static void clearAllWellKnownObjects ()
	{
		Node.eraseTypes();
	}


	/**
	 * Compare the first two arguments for {@linkplain Object#equals(Object)
	 * equality}.  If unequal, use the supplied message pattern and message
	 * arguments to construct an error message, then fail with it.
	 *
	 * @param a The first object to compare.
	 * @param b The second object to compare.
	 * @param messagePattern
	 *            A format string for producing an error message in the event
	 *            that the objects are not equal.
	 * @param messageArguments
	 *            A variable number of objects to describe via the
	 *            messagePattern.
	 */
	void assertEQ (
		final Object a,
		final Object b,
		final String messagePattern,
		final Object... messageArguments)
	{
		if (!a.equals(b))
		{
			fail(String.format(messagePattern, messageArguments));
		}
	}

	/**
	 * Examine the first (boolean) argument.  If false, use the supplied message
	 * pattern and message arguments to construct an error message, then fail
	 * with it.
	 *
	 * @param bool
	 *            The boolean which should be true for success.
	 * @param messagePattern
	 *            A format string for producing an error message in the event
	 *            that the supplied boolean was false.
	 * @param messageArguments
	 *            A variable number of objects to describe via the
	 *            messagePattern.
	 */
	void assertT (
		final boolean bool,
		final String messagePattern,
		final Object... messageArguments)
	{
		if (!bool)
		{
			fail(String.format(messagePattern, messageArguments));
		}
	}

	/**
	 * Test that the {@linkplain Node#supernodes declared} subtype relations
	 * actually hold the way the graph says they should.
	 */
	@Test
	public void testGraphModel ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				assertEQ(
					y.allDescendants.contains(x),
					x.subtype(y),
					"graph model (not as declared): %s, %s",
					x,
					y);
				assertEQ(
					x == y,
					x.t().equals(y.t()),
					"graph model (not unique) %s, %s",
					x,
					y);
			}
		}
	}

	/**
	 * Test that the subtype relationship is reflexive.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x&isin;T</sub>&thinsp;x&sube;x
	 * </nobr></span>
	 */
	@Test
	public void testSubtypeReflexivity ()
	{
		for (final Node x : Node.values)
		{
			if (!x.subtype(x))
			{
				// Breakpoint the following statement to debug test failures.
				x.subtype(x);
			}

			assertT(
				x.subtype(x),
				"subtype reflexivity: %s",
				x);
		}
	}

	/**
	 * Test that the subtype relationship is transitive.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y,z&isin;T</sub>&thinsp;(x&sube;y&thinsp;&and;&thinsp;y&sube;z
	 *     &rarr; x&sube;z)
	 * </nobr></span>
	 */
	@Test
	public void testSubtypeTransitivity ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				final boolean xSubY = x.subtype(y);
				for (final Node z : Node.values)
				{
					assertT(
						(!(xSubY && y.subtype(z)))
							|| x.subtype(z),
						"subtype transitivity: %s, %s, %s",
						x,
						y,
						z);
				}
			}
		}
	}

	/**
	 * Test that the subtype relationship is asymmetric.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sub;y &rarr; &not;y&sub;x)
	 * </nobr></span>
	 */
	@Test
	public void testSubtypeAsymmetry ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				assertEQ(
					x.subtype(y) && y.subtype(x),
					x == y,
					"subtype asymmetry: %s, %s",
					x,
					y);
			}
		}
	}

	/**
	 * Test that types are closed with respect to the type union operator.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&cup;y&thinsp;&isin;&thinsp;T)
	 * </nobr></span>
	 */
	@Test
	public void testUnionClosure ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				assertT(
					x.union(y).isInstanceOf(InstanceMetaDescriptor.topMeta()),
					"union closure: %s, %s",
					x,
					y);
			}
		}
	}

	/**
	 * Test that the type union operator is reflexive.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x&isin;T</sub>&thinsp;(x&cup;x&thinsp;=&thinsp;x)
	 * </nobr></span>
	 */
	@Test
	public void testUnionReflexivity ()
	{
		for (final Node x : Node.values)
		{
			assertEQ(
				x.union(x),
				x.t(),
				"union reflexivity: %s",
				x);
		}
	}

	/**
	 * Test that the type union operator is commutative.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&cup;y = y&cup;x)
	 * </nobr></span>
	 */
	@Test
	public void testUnionCommutativity ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				if (!x.union(y).equals(y.union(x)))
				{
					// These are useful trace points. Leave them in.
					x.t().typeUnion(y.t());
					y.t().typeUnion(x.t());
					assertEQ(
						x.union(y),
						y.union(x),
						"union commutativity: %s, %s",
						x,
						y);
				}
			}
		}
	}

	/**
	 * Test that the type union operator is associative.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y,z&isin;T</sub>&thinsp;(x&cup;y)&cup;z = x&cup;(y&cup;z)
	 * </nobr></span>
	 */
	@Test
	public void testUnionAssociativity ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				final A_Type xy = x.union(y);
				for (final Node z : Node.values)
				{
					final A_Type xyUz = xy.typeUnion(z.t());
					final A_Type yz = y.union(z);
					final A_Type xUyz = x.t().typeUnion(yz);
					if (!xyUz.equals(xUyz))
					{
						// These are useful trace points. Leave them in.
						xy.typeUnion(z.t());
						x.t().typeUnion(yz);
						xyUz.equals(xUyz);
						assertEQ(
							xyUz,
							xUyz,
							"union associativity: %s, %s, %s",
							x,
							y,
							z);
					}
				}
			}
		}
	}

	/**
	 * Test that types are closed with respect to the type intersection
	 * operator.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&cap;y&thinsp;&isin;&thinsp;T)
	 * </nobr></span>
	 */
	@Test
	public void testIntersectionClosure ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				assertT(
					x.intersect(y).isInstanceOf(
						InstanceMetaDescriptor.topMeta()),
					"intersection closure: %s, %s",
					x,
					y);
			}
		}
	}

	/**
	 * Test that the type intersection operator is reflexive.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x&isin;T</sub>&thinsp;(x&cap;x&thinsp;=&thinsp;x)
	 * </nobr></span>
	 */
	@Test
	public void testIntersectionReflexivity ()
	{
		for (final Node x : Node.values)
		{
			assertEQ(
				x.intersect(x),
				x.t(),
				"intersection reflexivity: %s",
				x);
		}
	}

	/**
	 * Test that the type intersection operator is commutative.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&cap;y = y&cap;x)
	 * </nobr></span>
	 */
	@Test
	public void testIntersectionCommutativity ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				final A_Type xy = x.intersect(y);
				final A_Type yx = y.intersect(x);
				if (!xy.equals(yx))
				{
					// These are useful trace points. Leave them in.
					x.t().typeIntersection(y.t());
					y.t().typeIntersection(x.t());
					assertEQ(
						xy,
						yx,
						"intersection commutativity: %s, %s",
						x,
						y);
				}
			}
		}
	}

	/**
	 * Test that the type intersection operator is associative.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y,z&isin;T</sub>&thinsp;(x&cap;y)&cap;z = x&cap;(y&cap;z)
	 * </nobr></span>
	 */
	@Test
	public void testIntersectionAssociativity ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				final A_Type xy = x.intersect(y);
				for (final Node z : Node.values)
				{
					final A_Type xyIz = xy.typeIntersection(z.t());
					final A_Type yz = y.intersect(z);
					final A_Type xIyz = x.t().typeIntersection(yz);
					if (!xyIz.equals(xIyz))
					{
						// These are useful trace points. Leave them in.
						x.t().typeIntersection(y.t());
						y.t().typeIntersection(z.t());
						xy.typeIntersection(z.t());
						x.t().typeIntersection(yz);
						xyIz.equals(xIyz);
						assertEQ(
							xyIz,
							xIyz,
							"intersection associativity: %s, %s, %s",
							x,
							y,
							z);
					}
				}
			}
		}
	}

	/**
	 * A {@code TypeRelation} that relates a type to another type that should
	 * either covary or contravary with respect to it, depending on the specific
	 * {@code TypeRelation}.
	 */
	public static abstract class TypeRelation
	{
		/**
		 * Transform any {@linkplain TypeDescriptor type} into another type (in
		 * a way specific to an implementation) that should either covary or
		 * contravary with respect to it, depending on the specific class.
		 *
		 * @param type The type to transform.
		 * @return The transformed type.
		 */
		abstract A_Type transform(A_Type type);

		/**
		 * The name of the {@code TypeRelation}.
		 */
		final String name;

		/**
		 * Construct a new {@link TypeRelation}, supplying the relation name.
		 *
		 * @param name What to call the new relation.
		 */
		TypeRelation (final String name)
		{
			this.name = name;
		}
	}

	/**
	 * Check the covariance of some {@link TypeRelation}.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; Co(x)&sube;Co(y))
	 * </nobr></span>
	 *
	 * @param relation The covariant {@linkplain TypeRelation} to check.
	 */
	public void checkCovariance (
		final TypeRelation relation)
	{
		for (final Node x : Node.values)
		{
			final A_Type CoX = relation.transform(x.t());
			for (final Node y : Node.values)
			{
				final A_Type CoY = relation.transform(y.t());
				assertT(
					!x.subtype(y) || CoX.isSubtypeOf(CoY),
					"covariance (%s): %s, %s",
					relation.name,
					x,
					y);
			}
		}
	}

	/**
	 * Check that the subtype relation <em>contravaries</em> with the given
	 * {@link TypeRelation}.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; Con(y)&sube;Con(x))
	 * </nobr></span>
	 *
	 * @param relation The contravariant {@linkplain TypeRelation} to check.
	 */
	public void checkContravariance (
		final TypeRelation relation)
	{
		for (final Node x : Node.values)
		{
			final A_Type ConX = relation.transform(x.t());
			for (final Node y : Node.values)
			{
				final A_Type ConY = relation.transform(y.t());
				assertT(
					!x.subtype(y) || ConY.isSubtypeOf(ConX),
					"contravariance (%s): %s, %s",
					relation.name,
					x,
					y);
			}
		}
	}

	/**
	 * Test that the subtype relation covaries with fiber result type.
	 *
	 * @see #checkCovariance(TypeRelation)
	 */
	@Test
	public void testFiberResultCovariance ()
	{
		checkCovariance(new TypeRelation("fiber result")
		{
			@Override
			public A_Type transform (final A_Type type)
			{
				return FiberTypeDescriptor.forResultType(type);
			}
		});
	}

	/**
	 * Test that the subtype relation covaries with function return type.
	 *
	 * @see #checkCovariance(TypeRelation)
	 */
	@Test
	public void testFunctionResultCovariance ()
	{
		checkCovariance(new TypeRelation("function result")
		{
			@Override
			public A_Type transform (final A_Type type)
			{
				return FunctionTypeDescriptor.create(
					TupleDescriptor.empty(),
					type);
			}
		});
	}

	/**
	 * Test that the subtype relation covaries with (homogeneous) tuple element
	 * type.
	 *
	 * @see #checkCovariance(TypeRelation)
	 */
	@Test
	public void testTupleEntryCovariance ()
	{
		checkCovariance(new TypeRelation("tuple entries")
		{
			@Override
			A_Type transform (final A_Type type)
			{
				return TupleTypeDescriptor.zeroOrMoreOf(
					type);
			}
		});
	}

	/**
	 * Test that the subtype relation covaries with type parameters.
	 *
	 * @see #checkCovariance(TypeRelation)
	 */
	@Test
	public void testAbstractPojoTypeParametersCovariance ()
	{
		checkCovariance(new TypeRelation("pojo type parameters")
		{
			@Override
			A_Type transform (final A_Type type)
			{
				return PojoTypeDescriptor.forClassWithTypeArguments(
					Comparable.class,
					TupleDescriptor.from(type));
			}
		});
	}

	/**
	 * Test that the subtype relation <em>contravaries</em> with function
	 * argument type.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; Con(y)&sube;Con(x))
	 * </nobr></span>
	 */
	@Test
	public void testFunctionArgumentContravariance ()
	{
		checkContravariance(new TypeRelation("function argument")
		{
			@Override
			A_Type transform (final A_Type type)
			{
				return FunctionTypeDescriptor.create(
					TupleDescriptor.from(type),
					Types.TOP.o());
			}
		});
	}

	/**
	 * Check that the subtype relation covaries under the "type-of" mapping.
	 * This is simply covariance of metatypes, which is abbreviated as
	 * metacovariance.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; T(x)&sube;T(y))
	 * </nobr></span>
	 */
	@Test
	public void testMetacovariance ()
	{
		checkCovariance(new TypeRelation("metacovariance")
		{
			@Override
			A_Type transform (final A_Type type)
			{
				return InstanceMetaDescriptor.on(type);
			}
		});
	}

	/**
	 * Check that the type union of two types' types is the same as the type of
	 * their type union.  Namely,
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(T(x)&cup;T(y) = T(x&cup;y))
	 * </nobr></span>
	 */
	@Test
	public void testTypeUnionMetainvariance ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				final A_Type Tx = InstanceMetaDescriptor.on(x.t());
				final A_Type Ty = InstanceMetaDescriptor.on(y.t());
				final A_Type xuy = x.t().typeUnion(y.t());
				final A_BasicObject T_xuy = InstanceMetaDescriptor.on(xuy);
				final A_BasicObject TxuTy = Tx.typeUnion(Ty);
				assertEQ(
					T_xuy,
					TxuTy,
					"type union metainvariance: x=%s, y=%s, T(x∪y)=%s, T(x)∪T(y)=%s",
					x,
					y,
					T_xuy,
					TxuTy);
			}
		}
	}


	/**
	 * Check that the type intersection of two types' types is the same as the
	 * type of their type intersection.  Namely,
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(T(x)&cap;T(y) = T(x&cap;y))
	 * </nobr></span>
	 */
	@Test
	public void testTypeIntersectionMetainvariance ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				final A_Type Tx = InstanceMetaDescriptor.on(x.t());
				final A_Type Ty = InstanceMetaDescriptor.on(y.t());
				final A_Type xny = x.t().typeIntersection(y.t());
				final A_Type T_xny = InstanceMetaDescriptor.on(xny);
				final A_Type TxnTy = Tx.typeIntersection(Ty);
				assertEQ(
					T_xny,
					TxnTy,
					"type intersection metainvariance: x=%s, y=%s, T(x∩y)=%s, T(x)∩T(y)=%s",
					x,
					y,
					T_xny,
					TxnTy);
			}
		}
	}
}
