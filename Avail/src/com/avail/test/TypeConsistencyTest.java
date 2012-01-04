/**
 * TypeConsistencyTest.java
 * Copyright (c) 2011, Mark van Gulik.
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
import com.avail.annotations.NotNull;
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
 *     <td>Union function</td>
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
 *     <td>Intersection function</td>
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
 *     <td>Type intersect metainvariance</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(T(x)&cap;T(y) = T(x&cap;y))</td>
 * </tr>
 * </table>
 * </p>
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
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
	 * All {@link TypeDescriptor.Types} are included, as well as a few
	 * simple representative samples, such as the one-element string type and
	 * the type of whole numbers.
	 * </p>
	 */
	public abstract static class Node
	{
		/**
		 * The list of all currently defined {@linkplain Node type nodes}.
		 */
		static final List<Node> values = new ArrayList<Node>();

		/**
		 * A mapping from {@link TypeDescriptor.Types} to their corresponding
		 * {@link Node}s.
		 */
		private final static EnumMap<Types, Node> primitiveTypes =
			new EnumMap<Types, Node>(Types.class);

		static
		{
			// Include all primitive types.
			for (final Types type : Types.values())
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
						@Override AvailObject get ()
						{
							return type.o();
						}
					};
					primitiveTypes.put(type, node);
				}
			}
		}



		/** The type {@code tuple} */
		final static Node TUPLE = new Node(
			"TUPLE",
			primitiveTypes.get(Types.ANY))
		{
			@Override AvailObject get ()
			{
				return TupleTypeDescriptor.mostGeneralType();
			}
		};

		/**
		 * The type {@code string}, which is the same as {@code tuple of
		 * character}
		 */
		final static Node STRING = new Node("STRING", TUPLE)
		{
			@Override AvailObject get ()
			{
				return TupleTypeDescriptor.stringTupleType();
			}
		};

		/** The type {@code tuple [1..1] of character} */
		final static Node UNIT_STRING = new Node("UNIT_STRING", STRING)
		{
			@Override AvailObject get ()
			{
				return StringDescriptor.from("x").kind();
			}
		};

		/** The type {@code type of <>} */
		final static Node EMPTY_TUPLE = new Node("EMPTY_TUPLE", TUPLE, STRING)
		{
			@Override AvailObject get ()
			{
				return TupleDescriptor.empty().kind();
			}
		};

		/** The type {@code set} */
		final static Node SET = new Node(
			"SET",
			primitiveTypes.get(Types.ANY))
		{
			@Override AvailObject get ()
			{
				return SetTypeDescriptor.mostGeneralType();
			}
		};

		/**
		 * The most general {@linkplain EnumerationMetaDescriptor enumeration
		 * type}.
		 */
		final static Node UNION_META = new Node(
			"UNION_META",
			primitiveTypes.get(Types.TYPE))
		{
			@Override AvailObject get ()
			{
				return EnumerationMetaDescriptor.mostGeneralType();
			}
		};

		/**
		 * An {@linkplain EnumerationMetaDescriptor enumeration type}
		 * parameterized over integers.
		 */
		final static Node UNION_OF_INTEGER_META = new Node(
			"UNION_OF_INTEGER_META",
			UNION_META)
		{
			@Override AvailObject get ()
			{
				return EnumerationMetaDescriptor.of(
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/**
		 * A {@linkplain EnumerationMetaDescriptor enumeration type}
		 * parameterized over types.
		 */
		final static Node UNION_OF_TYPE_META = new Node(
			"UNION_OF_TYPE_META",
			UNION_META,
			primitiveTypes.get(Types.META))
		{
			@Override AvailObject get ()
			{
				return EnumerationMetaDescriptor.of(Types.TYPE.o());
			}
		};

		/** The most general function type. */
		final static Node MOST_GENERAL_FUNCTION = new Node(
			"MOST_GENERAL_FUNCTION",
			primitiveTypes.get(Types.ANY))
		{
			@Override AvailObject get ()
			{
				return FunctionTypeDescriptor.mostGeneralType();
			}
		};

		/**
		 * The type for functions that accept no arguments and return an integer.
		 */
		final static Node NOTHING_TO_INT_FUNCTION = new Node(
			"NOTHING_TO_INT_FUNCTION",
			MOST_GENERAL_FUNCTION)
		{
			@Override AvailObject get ()
			{
				return FunctionTypeDescriptor.create(
					TupleDescriptor.empty(),
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/**
		 * The type for functions that accept an integer and return an integer.
		 */
		final static Node INT_TO_INT_FUNCTION = new Node(
			"INT_TO_INT_FUNCTION",
			MOST_GENERAL_FUNCTION)
		{
			@Override AvailObject get ()
			{
				return FunctionTypeDescriptor.create(
					TupleDescriptor.from(IntegerRangeTypeDescriptor.integers()),
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/**
		 * The type for functions that accept two integers and return an integer.
		 */
		final static Node INTS_TO_INT_FUNCTION = new Node(
			"INTS_TO_INT_FUNCTION",
			MOST_GENERAL_FUNCTION)
		{
			@Override AvailObject get ()
			{
				return FunctionTypeDescriptor.create(
					TupleDescriptor.from(
						IntegerRangeTypeDescriptor.integers(),
						IntegerRangeTypeDescriptor.integers()),
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/** The most specific function type, other than bottom. */
		final static Node MOST_SPECIFIC_FUNCTION = new Node(
			"MOST_SPECIFIC_FUNCTION",
			NOTHING_TO_INT_FUNCTION,
			INT_TO_INT_FUNCTION,
			INTS_TO_INT_FUNCTION)
		{
			@Override AvailObject get ()
			{
				return FunctionTypeDescriptor.createWithArgumentTupleType(
					TupleTypeDescriptor.mostGeneralType(),
					BottomTypeDescriptor.bottom(),
					SetDescriptor.empty());
			}
		};

		/** The primitive type representing the extended integers [-∞..∞]. */
		final static Node EXTENDED_INTEGER = new Node(
			"EXTENDED_INTEGER",
			primitiveTypes.get(Types.NUMBER))
		{
			@Override AvailObject get ()
			{
				return IntegerRangeTypeDescriptor.extendedIntegers();
			}
		};

		/** The primitive type representing whole numbers [0..∞). */
		final static Node WHOLE_NUMBER = new Node(
			"WHOLE_NUMBER",
			EXTENDED_INTEGER)
		{
			@Override AvailObject get ()
			{
				return IntegerRangeTypeDescriptor.wholeNumbers();
			}
		};

		/** Some {@linkplain AtomDescriptor atom}'s instance type. */
		final static Node SOME_ATOM_TYPE = new Node(
			"SOME_ATOM_TYPE",
			primitiveTypes.get(Types.ATOM))
		{
			@Override AvailObject get ()
			{
				return InstanceTypeDescriptor.on(
					AtomDescriptor.create(
						StringDescriptor.from("something")));
			}
		};

		/**
		 * The instance type of an {@linkplain AtomDescriptor atom} different
		 * from {@link #SOME_ATOM_TYPE}.
		 */
		final static Node ANOTHER_ATOM_TYPE = new Node(
			"ANOTHER_ATOM_TYPE",
			primitiveTypes.get(Types.ATOM))
		{
			@Override AvailObject get ()
			{
				return InstanceTypeDescriptor.on(
					AtomDescriptor.create(
						StringDescriptor.from("another")));
			}
		};

		/**
		 * The base {@linkplain ObjectTypeDescriptor object type}.
		 */
		final static Node OBJECT_TYPE = new Node(
			"OBJECT_TYPE",
			primitiveTypes.get(Types.ANY))
		{
			@Override AvailObject get ()
			{
				return ObjectTypeDescriptor.mostGeneralType();
			}
		};

		/**
		 * A simple non-root {@linkplain ObjectTypeDescriptor object type}.
		 */
		final static Node NON_ROOT_OBJECT_TYPE = new Node(
			"NON_ROOT_OBJECT_TYPE",
			OBJECT_TYPE)
		{
			@Override AvailObject get ()
			{
				return ObjectTypeDescriptor.objectTypeFromMap(
					MapDescriptor.empty().mapAtPuttingCanDestroy(
						SOME_ATOM_TYPE.t,
						TypeDescriptor.Types.ANY.o(),
						false));
			}
		};

		/**
		 * A simple non-root {@linkplain ObjectTypeDescriptor object type}.
		 */
		final static Node NON_ROOT_OBJECT_TYPE_WITH_INTEGERS = new Node(
			"NON_ROOT_OBJECT_TYPE_WITH_INTEGERS",
			NON_ROOT_OBJECT_TYPE)
		{
			@Override AvailObject get ()
			{
				return ObjectTypeDescriptor.objectTypeFromMap(
					MapDescriptor.empty().mapAtPuttingCanDestroy(
						SOME_ATOM_TYPE.t,
						IntegerRangeTypeDescriptor.integers(),
						false));
			}
		};

		/**
		 * A simple non-root {@linkplain ObjectTypeDescriptor object type}.
		 */
		final static Node NON_ROOT_OBJECT_TYPE_WITH_DIFFERENT_KEY = new Node(
			"NON_ROOT_OBJECT_TYPE_WITH_DIFFERENT_KEY",
			OBJECT_TYPE)
		{
			@Override AvailObject get ()
			{
				return ObjectTypeDescriptor.objectTypeFromMap(
					MapDescriptor.empty().mapAtPuttingCanDestroy(
						ANOTHER_ATOM_TYPE.t,
						TypeDescriptor.Types.ANY.o(),
						false));
			}
		};

		/**
		 * The most general {@linkplain PojoTypeDescriptor pojo type}.
		 */
		final static Node MOST_GENERAL_POJO = new Node(
			"MOST_GENERAL_POJO",
			primitiveTypes.get(Types.ANY))
		{
			@Override
			AvailObject get ()
			{
				return PojoTypeDescriptor.mostGeneralType();
			}
		};

		/**
		 * The pojo type representing {@link Comparable}&lt;{@link Object}&gt;.
		 */
		final static Node COMPARABLE_OF_JAVA_OBJECT_POJO = new Node(
			"COMPARABLE_OF_JAVA_OBJECT_POJO",
			MOST_GENERAL_POJO)
		{
			@Override
			AvailObject get ()
			{
				return PojoTypeDescriptor.create(
					Comparable.class,
					TupleDescriptor.from(PojoTypeDescriptor.create(
						Object.class, TupleDescriptor.empty())));
			}
		};

		/**
		 * The pojo type representing {@link Comparable}&lt;{@link Integer}&gt;.
		 */
		final static Node COMPARABLE_OF_JAVA_INTEGER_POJO = new Node(
			"COMPARABLE_OF_JAVA_INTEGER_POJO",
			COMPARABLE_OF_JAVA_OBJECT_POJO)
		{
			@Override
			AvailObject get ()
			{
				return PojoTypeDescriptor.create(
					Comparable.class,
					TupleDescriptor.from(PojoTypeDescriptor.create(
						Integer.class, TupleDescriptor.empty())));
			}
		};

		/**
		 * The pojo type representing {@link Integer}.
		 */
		final static Node JAVA_INTEGER_POJO = new Node(
			"JAVA_INTEGER_POJO",
			COMPARABLE_OF_JAVA_INTEGER_POJO)
		{
			@Override
			AvailObject get ()
			{
				return PojoTypeDescriptor.create(
					Integer.class, TupleDescriptor.empty());
			}
		};

		/**
		 * The pojo type representing {@link Comparable}&lt;{@link String}&gt;.
		 */
		final static Node COMPARABLE_OF_JAVA_STRING_POJO = new Node(
			"COMPARABLE_OF_JAVA_STRING_POJO",
			COMPARABLE_OF_JAVA_OBJECT_POJO)
		{
			@Override
			AvailObject get ()
			{
				return PojoTypeDescriptor.create(
					Comparable.class,
					TupleDescriptor.from(PojoTypeDescriptor.create(
						String.class, TupleDescriptor.empty())));
			}
		};

		/**
		 * The pojo type representing {@link String}.
		 */
		final static Node JAVA_STRING_POJO = new Node(
			"JAVA_STRING_POJO",
			COMPARABLE_OF_JAVA_STRING_POJO)
		{
			@Override
			AvailObject get ()
			{
				return PojoTypeDescriptor.create(
					String.class, TupleDescriptor.empty());
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
			AvailObject get ()
			{
				return PojoTypeDescriptor.create(
					Enum.class,
					TupleDescriptor.from(PojoSelfTypeDescriptor.create(
						Enum.class)));
			}
		};

		/**
		 * The pojo type representing the Java enumeration {@link
		 * com.avail.interpreter.Primitive}.
		 */
		final static Node AVAIL_PRIMITIVE_ENUM_POJO = new Node(
			"AVAIL_PRIMITIVE_ENUM_POJO",
			JAVA_ENUM_POJO)
		{
			@Override
			AvailObject get ()
			{
				return PojoTypeDescriptor.create(
					Primitive.class, TupleDescriptor.empty());
			}
		};

		/**
		 * The pojo type representing {@link Comparable}&lt;<em>Avail's integer
		 * type</em>&gt;.  Note that this is a Java type parameterized by an
		 * Avail type.
		 */
		final static Node COMPARABLE_OF_AVAIL_INTEGER_POJO = new Node(
			"COMPARABLE_OF_AVAIL_INTEGER_POJO",
			MOST_GENERAL_POJO)
		{
			@Override
			AvailObject get ()
			{
				return PojoTypeDescriptor.create(
					Comparable.class,
					TupleDescriptor.from(
						IntegerRangeTypeDescriptor.integers()));
			}
		};

		/**
		 * The pojo type representing the Java {@link Array} type {@link
		 * Object}[].
		 */
		final static Node JAVA_OBJECT_ARRAY_POJO = new Node(
			"JAVA_OBJECT_ARRAY_POJO",
			MOST_GENERAL_POJO)
		{
			@Override
			AvailObject get ()
			{
				return PojoTypeDescriptor.create(
					PojoTypeDescriptor.pojoArrayClass(),
					TupleDescriptor.from(
						PojoTypeDescriptor.mostGeneralType()));
			}
		};

		/**
		 * The pojo type representing the Java {@link Array} type {@link
		 * String}[].
		 */
		final static Node JAVA_STRING_ARRAY_POJO = new Node(
			"JAVA_STRING_ARRAY_POJO",
			JAVA_OBJECT_ARRAY_POJO)
		{
			@Override
			AvailObject get ()
			{
				return PojoTypeDescriptor.create(
					PojoTypeDescriptor.pojoArrayClass(),
					TupleDescriptor.from(JAVA_STRING_POJO.t));
			}
		};

		/**
		 * The special {@link PojoTypeDescriptor#mostSpecificType() most
		 * specific pojo type},
		 */
		final static Node MOST_SPECIFIC_POJO = new Node(
			"MOST_SPECIFIC_POJO",
			JAVA_INTEGER_POJO,
			JAVA_STRING_POJO,
			AVAIL_PRIMITIVE_ENUM_POJO,
			COMPARABLE_OF_AVAIL_INTEGER_POJO,
			JAVA_STRING_ARRAY_POJO)
		{
			@Override
			AvailObject get ()
			{
				return PojoTypeDescriptor.mostSpecificType();
			}
		};

		/**
		 * The metatype for function types.
		 */
		final static Node FUNCTION_META = new Node(
			"FUNCTION_META",
			primitiveTypes.get(Types.TYPE))
		{
			@Override AvailObject get ()
			{
				return FunctionTypeDescriptor.meta();
			}
		};

		/**
		 * The metatype for continuation types.
		 */
		final static Node CONTINUATION_META = new Node(
			"CONTINUATION_META",
			primitiveTypes.get(Types.TYPE))
		{
			@Override AvailObject get ()
			{
				return ContinuationTypeDescriptor.meta();
			}
		};

		/**
		 * The metatype for integer types.
		 */
		final static Node INTEGER_META = new Node(
			"INTEGER_META",
			primitiveTypes.get(Types.TYPE))
		{
			@Override AvailObject get ()
			{
				return IntegerRangeTypeDescriptor.meta();
			}
		};

		/** The primitive type representing the metatype of whole numbers [0..∞). */
		final static Node WHOLE_NUMBER_META = new Node(
			"WHOLE_NUMBER_META",
			INTEGER_META,
			primitiveTypes.get(Types.TYPE))
		{
			@Override AvailObject get ()
			{
				return InstanceTypeDescriptor.on(
					IntegerRangeTypeDescriptor.wholeNumbers());
			}
		};

		/** The primitive type representing the metametatype of the metatype of whole numbers [0..∞). */
		final static Node WHOLE_NUMBER_META_META = new Node(
			"WHOLE_NUMBER_META_META",
			UNION_OF_TYPE_META,
			primitiveTypes.get(Types.META))
		{
			@Override AvailObject get ()
			{
				return InstanceTypeDescriptor.on(
					InstanceTypeDescriptor.on(
						IntegerRangeTypeDescriptor.wholeNumbers()));
			}
		};

		/**
		 * The most general {@linkplain VariableTypeDescriptor variable type}.
		 */
		final static Node ROOT_VARIABLE = new Node(
			"ROOT_VARIABLE",
			primitiveTypes.get(Types.ANY))
		{
			@Override AvailObject get ()
			{
				return VariableTypeDescriptor.mostGeneralType();
			}
		};

		/**
		 * The {@linkplain VariableTypeDescriptor type of variable} which
		 * holds {@linkplain IntegerDescriptor integers}.
		 */
		final static Node INT_VARIABLE = new Node(
			"INT_VARIABLE",
			ROOT_VARIABLE)
		{
			@Override AvailObject get ()
			{
				return VariableTypeDescriptor.wrapInnerType(
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/**
		 * The {@linkplain VariableTypeDescriptor type of variable} which
		 * holds only a particular atom.
		 */
		final static Node SOME_ATOM_VARIABLE = new Node(
			"SOME_ATOM_VARIABLE",
			ROOT_VARIABLE)
		{
			@Override AvailObject get ()
			{
				return VariableTypeDescriptor.wrapInnerType(SOME_ATOM_TYPE.t);
			}
		};

		/**
		 * The most specific {@linkplain VariableTypeDescriptor type of
		 * variable}, other than {@linkplain BottomTypeDescriptor bottom}.
		 */
		final static Node BOTTOM_VARIABLE = new Node(
			"BOTTOM_VARIABLE",
			INT_VARIABLE,
			SOME_ATOM_VARIABLE)
		{
			@Override AvailObject get ()
			{
				return VariableTypeDescriptor.fromReadAndWriteTypes(
					BottomTypeDescriptor.bottom(),
					Types.TOP.o());
			}
		};

		/**
		 * The metatype for map types.
		 */
		final static Node MAP_META = new Node(
			"MAP_META",
			primitiveTypes.get(Types.TYPE))
		{
			@Override AvailObject get ()
			{
				return MapTypeDescriptor.meta();
			}
		};

		/**
		 * The metatype for set types.
		 */
		final static Node SET_META = new Node(
			"SET_META",
			primitiveTypes.get(Types.TYPE))
		{
			@Override AvailObject get ()
			{
				return SetTypeDescriptor.meta();
			}
		};

		/**
		 * The metatype for tuple types.
		 */
		final static Node TUPLE_META = new Node(
			"TUPLE_META",
			primitiveTypes.get(Types.TYPE))
		{
			@Override AvailObject get ()
			{
				return TupleTypeDescriptor.meta();
			}
		};

		/** The type of {@code bottom}.  This is the most specific meta. */
		final static Node BOTTOM_TYPE = new Node(
			"BOTTOM_TYPE",
			FUNCTION_META,
			CONTINUATION_META,
			WHOLE_NUMBER_META,
			WHOLE_NUMBER_META_META,
			MAP_META,
			SET_META,
			TUPLE_META,
			UNION_OF_INTEGER_META,
			UNION_OF_TYPE_META,
			primitiveTypes.get(Types.META))
		{
			@Override AvailObject get ()
			{
				return InstanceTypeDescriptor.on(
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
			new HashMap<ParseNodeKind, Map<Node, Node>>();

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
			final @NotNull String nodeName,
			final @NotNull ParseNodeKind parseNodeKind,
			final Node innerNode,
			final @NotNull Node... parentInnerNodes)
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
			final List<Node> parents = new ArrayList<Node>();
			if (parseNodeKind.parentKind() == null)
			{
				parents.add(primitiveTypes.get(Types.ANY));
			}
			else
			{
				parents.add(parseNodeTypeMap.get(parseNodeKind.parentKind()).get(innerNode));
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
				AvailObject get ()
				{
					return parseNodeKind.create(
						innerNode == null
							? BottomTypeDescriptor.bottom()
							: innerNode.t);
				}
			};
			submap.put(innerNode, newNode);
		}

		static
		{
			// Include all parse node types.  Include a minimal diamond of types
			// for each parse node kind.
			final Node topNode = primitiveTypes.get(Types.TOP);
			final Node atomNode = SOME_ATOM_TYPE;
			final Node anotherAtomNode = ANOTHER_ATOM_TYPE;
			for (final ParseNodeKind kind : ParseNodeKind.values())
			{
				addHelper(
					kind.name() + "(TOP)",
					kind,
					topNode);
				addHelper(
					kind.name() + "(SOME_ATOM_TYPE)",
					kind,
					atomNode,
					topNode);
				addHelper(
					kind.name() + "(ANOTHER_ATOM_TYPE)",
					kind,
					anotherAtomNode,
					topNode);
				addHelper(
					kind.name() + "(BOTTOM)",
					kind,
					null,  // BOTTOM node is not available yet.
					atomNode,
					anotherAtomNode);
			}
		}


		/**
		 * The list of all {@link Node}s except BOTTOM.
		 */
		private final static List<Node> nonBottomTypes =
			new ArrayList<Node>();

		static
		{
			for (final Node existingType : values)
			{
				nonBottomTypes.add(existingType);
			}
		}

		/** The type {@code bottom} */
		final static Node BOTTOM = new Node(
			"BOTTOM",
			nonBottomTypes.toArray(new Node[0]))
		{
			@Override AvailObject get ()
			{
				return BottomTypeDescriptor.bottom();
			}
		};




		/** The name of this type node, used for error diagnostics. */
		final String name;

		/** The Avail {@linkplain TypeDescriptor type} I represent in the graph. */
		AvailObject t;

		/** A unique 0-based index for this {@code Node}. */
		final int index;

		/** The supernodes in the graph. */
		final Node [] supernodes;


		/** The subnodes in the graph, as an {@link EnumSet}. */
		private Set<Node> subnodes;


		/** Every node descended from this on, as an {@link EnumSet}. */
		Set<Node> allDescendants;

		/**
		 * A cache of type unions where I'm the left participant and the right
		 * participant (a Node) supplies its index for accessing the array.
		 */
		private AvailObject unionCache[];

		/**
		 * A cache of type intersections where I'm the left participant and the
		 * right participant (a Node) supplies its index for accessing the
		 * array.
		 */
		private AvailObject intersectionCache[];

		/**
		 * A cache of subtype tests where I'm the proposed subtype and the
		 * argument is the proposed supertype.  The value stored indicates if
		 * I am a subtype of the argument.
		 */
		private Boolean subtypeCache[];

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
				node.subnodes = new HashSet<Node>();
				node.allDescendants = new HashSet<Node>();
			}
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
		abstract AvailObject get ();


		/**
		 * Lookup or compute and cache the type union of the receiver's {@link
		 * #t} and the argument's {@code t}.
		 *
		 * @param rightNode
		 *            The {@linkplain Node} for the right side of the union.
		 * @return
		 *            The {@linkplain AvailObject#typeUnion(AvailObject) type
		 *            union} of the receiver's {@link #t} and the argument's
		 *            {@code t}.
		 */
		AvailObject union (final Node rightNode)
		{
			final int rightIndex = rightNode.index;
			AvailObject union = unionCache[rightIndex];
			if (union == null)
			{
				union = t.typeUnion(rightNode.t).makeImmutable();
				assertTrue(t.isSubtypeOf(union));
				assertTrue(rightNode.t.isSubtypeOf(union));
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
		 *            The {@linkplain AvailObject#typeIntersection(AvailObject)
		 *            type intersection} of the receiver's {@link #t} and the
		 *            argument's {@code t}.
		 */
		AvailObject intersect (final Node rightNode)
		{
			final int rightIndex = rightNode.index;
			AvailObject intersection = intersectionCache[rightIndex];
			if (intersection == null)
			{
				intersection = t.typeIntersection(rightNode.t).makeImmutable();
				assertTrue(intersection.isSubtypeOf(t));
				assertTrue(intersection.isSubtypeOf(rightNode.t));
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
				subtype = t.isSubtypeOf(rightNode.t);
				subtypeCache[rightIndex] = subtype;
			}
			return subtype;
		}

		@Override
		public String toString()
		{
			return name;
		};

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
				node.unionCache = null;
				node.intersectionCache = null;
				node.subtypeCache = null;
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
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
		Node.createTypes();
		System.out.format("Checking %d types%n", Node.values.size());

		// dumpGraphTo(System.out);
	}



	/**
	 * Output a machine-readable representation of the graph as a sequence of
	 * lines of text.  First output the number of nodes, then the single-quoted
	 * node names in some order.  Then output all edges as parethesis-enclosed
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
		final Set<AvailObject> allTypes = new HashSet<AvailObject>();
		for (final Node node : Node.values)
		{
			allTypes.add(node.t);
		}
		for (final Node t1 : Node.values)
		{
			for (final Node t2 : Node.values)
			{
				final AvailObject union12 = t1.union(t2);
				allTypes.add(union12);
				final AvailObject inter12 = t1.intersect(t2);
				allTypes.add(inter12);
				for (final Node t3 : Node.values)
				{
					allTypes.add(union12.typeUnion(t3.t));
					allTypes.add(t3.t.typeUnion(union12));
					allTypes.add(inter12.typeIntersection(t3.t));
					allTypes.add(t3.t.typeIntersection(inter12));
				}
			}
		}
		final List<AvailObject> allTypesList = new ArrayList<AvailObject>(allTypes);
		final Map<AvailObject,Integer> inverse = new HashMap<AvailObject,Integer>();
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
		AvailObject.clearAllWellKnownObjects();
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
				// TODO: Remove!
				if (y.allDescendants.contains(x) != x.subtype(y))
				{
					x.t.isSubtypeOf(y.t);
					assertEQ(
						y.allDescendants.contains(x),
						x.subtype(y),
						"graph model (not as declared): %s, %s",
						x,
						y);
				}
				assertEQ(
					x == y,
					x.t.equals(y.t),
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
				for (final Node z : Node.values)
				{
					assertT(
						(!(x.subtype(y) && y.subtype(z)))
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
	public void testUnionFunction ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				assertT(
					x.union(y).isInstanceOf(Types.TYPE.o()),
					"union function: %s, %s",
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
				x.t,
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
				// TODO: [TLS] Remove guard.
				if (!x.union(y).equals(y.union(x)))
				{
					x.t.typeUnion(y.t);
					y.t.typeUnion(x.t);
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
				final AvailObject xy = x.union(y);
				for (final Node z : Node.values)
				{
					final AvailObject xyUz = xy.typeUnion(z.t);
					final AvailObject yz = y.union(z);
					final AvailObject xUyz = x.t.typeUnion(yz);
					// TODO: [TLS] Remove guard after thorough debugging.
					if (!xyUz.equals(xUyz))
					{
						xy.typeUnion(z.t);
						x.t.typeUnion(yz);
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
	public void testIntersectionFunction ()
	{
		for (final Node x : Node.values)
		{
			for (final Node y : Node.values)
			{
				assertT(
					x.intersect(y).isInstanceOf(Types.TYPE.o()),
					"intersection function: %s, %s",
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
				x.t,
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
				final AvailObject xy = x.intersect(y);
				final AvailObject yx = y.intersect(x);
				// TODO: Remove this guard.
				if (!xy.equals(yx))
				{
					x.t.typeIntersection(y.t);
					y.t.typeIntersection(x.t);
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
				final AvailObject xy = x.intersect(y);
				for (final Node z : Node.values)
				{
					final AvailObject xyIz = xy.typeIntersection(z.t);
					final AvailObject yz = y.intersect(z);
					final AvailObject xIyz = x.t.typeIntersection(yz);
					// TODO: [TLS] Remove this guard after thorough debugging.
					if (!xyIz.equals(xIyz))
					{
						xy.typeIntersection(z.t);
						x.t.typeIntersection(yz);
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
	static abstract class TypeRelation
	{
		/**
		 * Transform any {@linkplain TypeDescriptor type} into another type (in
		 * a way specific to an implementation) that should either covary or
		 * contravary with respect to it, depending on the specific class.
		 *
		 * @param type The type to transform.
		 * @return The transformed type.
		 */
		abstract AvailObject transform(AvailObject type);

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
		final @NotNull TypeRelation relation)
	{
		for (final Node x : Node.values)
		{
			final AvailObject CoX = relation.transform(x.t);
			for (final Node y : Node.values)
			{
				final AvailObject CoY = relation.transform(y.t);
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
		final @NotNull TypeRelation relation)
	{
		for (final Node x : Node.values)
		{
			final AvailObject ConX = relation.transform(x.t);
			for (final Node y : Node.values)
			{
				final AvailObject ConY = relation.transform(y.t);
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
			public AvailObject transform (final AvailObject type)
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
			AvailObject transform (final AvailObject type)
			{
				return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
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
	public void testPojoTypeParametersCovariance ()
	{
		checkCovariance(new TypeRelation("pojo type parameters")
		{
			@Override
			AvailObject transform (final AvailObject type)
			{
				return PojoTypeDescriptor.create(
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
			AvailObject transform (final AvailObject type)
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
			AvailObject transform (final AvailObject type)
			{
				return InstanceTypeDescriptor.on(type);
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
				final AvailObject Tx = InstanceTypeDescriptor.on(x.t);
				final AvailObject Ty = InstanceTypeDescriptor.on(y.t);
				final AvailObject xuy = x.t.typeUnion(y.t);
				final AvailObject T_xuy = InstanceTypeDescriptor.on(xuy);
				final AvailObject TxuTy = Tx.typeUnion(Ty);
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
				final AvailObject Tx = InstanceTypeDescriptor.on(x.t);
				final AvailObject Ty = InstanceTypeDescriptor.on(y.t);
				final AvailObject xny = x.t.typeIntersection(y.t);
				final AvailObject T_xny = InstanceTypeDescriptor.on(xny);
				final AvailObject TxnTy = Tx.typeIntersection(Ty);
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