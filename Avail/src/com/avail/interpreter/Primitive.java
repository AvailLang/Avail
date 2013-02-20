/**
 * Primitive.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter;

import static com.avail.descriptor.AvailObject.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.operation.L2_ATTEMPT_INLINE_PRIMITIVE;
import com.avail.interpreter.primitive.*;
import com.avail.optimizer.*;


/**
 * This enumeration represents the interface between Avail's Level One
 * nybblecode interpreter and the underlying interfaces of the built-in objects,
 * providing functionality that is (generally) inexpressible within Level One
 * in terms of other Level One operations.  A conforming Avail implementation
 * must provide these primitives with equivalent semantics.
 *
 * <p>The enumeration defines an {@link #attempt(List, Interpreter)
 * attempt} operation that takes a {@linkplain List list} of arguments of type
 * {@link AvailObject}, as well as the {@link Interpreter} on whose behalf the
 * primitive attempt is being made.  The specific enumeration values override
 * the {@code attempt} method with behavior specific to that primitive.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class Primitive
implements IntegerEnumSlotDescriptionEnum
{
	/**
	 * The success state of a primitive attempt.
	 */
	public enum Result
	{
		/**
		 * The primitive succeeded, and the result, if any, has been stored for
		 * subsequent use.
		 */
		SUCCESS,

		/**
		 * The primitive failed.  The backup Avail code should be executed
		 * instead.
		 */
		FAILURE,

		/**
		 * The continuation was replaced as a consequence of the primitive.
		 * This is a specific form of success, but no result can be produced due
		 * to the fact that the new continuation does not have a place to write
		 * it.
		 */
		CONTINUATION_CHANGED,

		/**
		 * The current fiber has been suspended as a consequence of this
		 * primitive executing, so the {@linkplain Interpreter interpreter}
		 * should switch processes now.
		 */
		FIBER_SUSPENDED;
	}

	/**
	 * These flags are used by the execution machinery and optimizer to indicate
	 * the potential mischief that the corresponding primitives may get into.
	 */
	public enum Flag
	{
		/**
		 * The primitive can be attempted by the {@code L2Translator Level Two
		 * translator} at re-optimization time if the arguments are known
		 * constants. The result should be stable, such that invoking the
		 * primitive again with the same arguments should produce the same
		 * value. The primitive should not have side-effects.
		 */
		CanFold,

		/**
		 * The primitive can be safely inlined. In particular, it simply
		 * computes a value or changes the state of something and does not
		 * replace the current continuation in unusual ways. Thus, it is
		 * suitable for directly embedding in {@linkplain Interpreter Level
		 * Two} code by the {@linkplain L2Translator Level Two translator},
		 * without the need to reify the current continuation.
		 *
		 * <p>The primitive may still fail at runtime, but that's dealt with by
		 * a conditional branch in the {@link L2_ATTEMPT_INLINE_PRIMITIVE}
		 * wordcode itself.</p>
		 */
		CanInline,

		/**
		 * The primitive has a side-effect, such as writing to a file, modifying
		 * a variable, or defining a new method.
		 */
		HasSideEffect,

		/**
		 * The primitive can invoke a function. If the function is a
		 * non-primitive (or a primitive that fails), the current continuation
		 * must be reified before the call.
		 */
		Invokes,

		/**
		 * The primitive can replace the current continuation, and care should
		 * be taken to ensure the current continuation is fully reified prior to
		 * attempting this primitive.
		 */
		SwitchesContinuation,

		/**
		 * The primitive returns some constant. Currently this is only used for
		 * {@link P_340_PushConstant}, which always returns the first literal of
		 * the {@linkplain CompiledCodeDescriptor compiled code}.
		 */
		SpecialReturnConstant,

		/**
		 * The primitive returns the only argument.  This is only used for
		 * {@link P_341_PushArgument}, which is detected automatically at code
		 * generation time.
		 */
		SpecialReturnSoleArgument,

		/**
		 * The primitive cannot fail. Hence, there is no need for Avail code
		 * to run in the event of a primitive failure. Hence, such code is
		 * forbidden (because it would be unreachable).
		 */
		CannotFail,

		/**
		 * The primitive is not exposed to an Avail program. The compiler
		 * forbids direct compilation of primitive linkages to such primitives.
		 * {@linkplain P_188_CreateCompiledCode} also forbids creation of
		 * {@linkplain CompiledCodeDescriptor compiled code} that links a {@code
		 * Private} primitive.
		 */
		Private,

		/**
		 * This is a bootstrap primitive. It must be made available to the
		 * origin module of an Avail system via a special pragma.
		 */
		Bootstrap,

		/**
		 * The primitive is the special exception catching primitive. Its sole
		 * purpose is to fail, causing an actual continuation to be built. The
		 * exception raising mechanism searches for such a continuation to find
		 * a suitable handler function.
		 */
		CatchException,

		/**
		 * The primitive failure variable should not be cleared after its last
		 * usage.
		 */
		PreserveFailureVariable,

		/**
		 * The primitive arguments should not be cleared after their last
		 * usages.
		 */
		PreserveArguments,

		/**
		 * The semantics of the primitive fall outside the usual capacity of the
		 * {@linkplain L2Translator Level Two translator}. The current
		 * continuation should be reified prior to attempting the primitive. Do
		 * not attempt to fold or inline this primitive.
		 */
		Unknown
	}

	/**
	 * Attempt this primitive with the given arguments, and the {@linkplain
	 * Interpreter interpreter} on whose behalf to attempt the primitive.
	 * If the primitive fails, it should set the primitive failure code by
	 * calling {@link Interpreter#primitiveFailure(A_BasicObject)} and returning
	 * its result from the primitive.  Otherwise it should set the interpreter's
	 * primitive result by calling {@link
	 * Interpreter#primitiveSuccess(A_BasicObject)} and then return its result
	 * from the primitive.  For unusual primitives that replace the current
	 * continuation, {@link Result#CONTINUATION_CHANGED} is more appropriate,
	 * and the latestResult need not be set.  For primitives that need to
	 * cause a context switch, {@link Result#FIBER_SUSPENDED} should be
	 * returned.
	 *
	 * @param args The {@linkplain List list} of arguments to the primitive.
	 * @param interpreter The {@link Interpreter} that is executing.
	 * @return The {@link Result} code indicating success or failure (or special
	 *         circumstance).
	 */
	public abstract Result attempt (
		List<AvailObject> args,
		Interpreter interpreter);

	/**
	 * Return a function type that restricts actual primitive blocks defined
	 * using that primitive.  The actual block's argument types must be at least
	 * as specific as this function type's argument types, and the actual
	 * block's return type must be at least as general as this function type's
	 * return type.  That's equivalent to the condition that the actual block's
	 * type is a subtype of this function type.
	 *
	 * @return
	 *             A function type that restricts the type of a block that uses
	 *             this primitive.
	 */
	protected abstract A_Type privateBlockTypeRestriction ();

	/**
	 * A {@linkplain FunctionTypeDescriptor function type} that restricts the
	 * type of block that can use this primitive.  This is initialized lazily to
	 * the value provided by {@link #privateBlockTypeRestriction()}, to avoid
	 * having to compute this function type multiple times.
	 */
	private A_Type cachedBlockTypeRestriction;

	/**
	 * Return a function type that restricts actual primitive blocks defined
	 * using that primitive.  The actual block's argument types must be at least
	 * as specific as this function type's argument types, and the actual
	 * block's return type must be at least as general as this function type's
	 * return type.  That's equivalent to the condition that the actual block's
	 * type is a subtype of this function type.
	 *
	 * <p>
	 * Cache the value in this {@linkplain Primitive} so subsequent requests are
	 * fast.
	 * </p>
	 *
	 * @return
	 *            A function type that restricts the type of a block that uses
	 *            this primitive.
	 */
	public final A_Type blockTypeRestriction ()
	{
		if (cachedBlockTypeRestriction == null)
		{
			cachedBlockTypeRestriction =
				privateBlockTypeRestriction().makeShared();
			final A_Type argsTupleType =
				cachedBlockTypeRestriction.argsTupleType();
			final A_Type sizeRange = argsTupleType.sizeRange();
			assert cachedBlockTypeRestriction.equals(
					BottomTypeDescriptor.bottom())
				|| (sizeRange.lowerBound().extractInt() == argCount()
					&& sizeRange.upperBound().extractInt() == argCount());
		}
		return cachedBlockTypeRestriction;
	}

	/**
	 * Answer the type of the result that will be produced by a call site with
	 * the given argument types.  Don't include semantic restrictions defined
	 * in the Avail code, but if convenient answer something stronger than the
	 * return type in the primitive's basic function type.
	 *
	 * @param argumentTypes
	 *            A {@link List} of argument {@linkplain TypeDescriptor types}.
	 * @return
	 *            The return type guaranteed by the VM at some call site.
	 */
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		return blockTypeRestriction().returnType();
	}

	/**
	 * Return an Avail {@linkplain TypeDescriptor type} that a failure variable
	 * must accept in order to be compliant with this primitive.  A more general
	 * type is acceptable for the variable.  This type is cached upon first
	 * request and should be accessed via {@link #failureVariableType()}.
	 *
	 * <p>
	 * By default, expect the primitive to fail with a string.
	 * </p>
	 *
	 * @return
	 *             A type which is at least as specific as the type of the
	 *             failure variable declared in a block using this primitive.
	 */
	protected A_Type privateFailureVariableType ()
	{
		return IntegerRangeTypeDescriptor.naturalNumbers();
	}

	/**
	 * A {@linkplain TypeDescriptor type} to constrain the {@linkplain
	 * AvailObject#writeType() content type} of the variable declaration within
	 * the primitive declaration of a block.  The actual variable's inner type
	 * must this or a supertype.
	 */
	private A_Type cachedFailureVariableType;

	/**
	 * Return an Avail {@linkplain TypeDescriptor type} that a failure variable
	 * must accept in order to be compliant with this primitive.  A more general
	 * type is acceptable for the variable.  The type is cached for performance.
	 *
	 * @return
	 *             A type which is at least as specific as the type of the
	 *             failure variable declared in a block using this primitive.
	 */
	public final A_Type failureVariableType ()
	{
		if (cachedFailureVariableType == null)
		{
			cachedFailureVariableType = privateFailureVariableType();
			assert cachedFailureVariableType.isType();
		}
		return cachedFailureVariableType;
	}

	/**
	 * Clear all cached block type restrictions and failure variable types.
	 */
	public static void clearCachedData ()
	{
		for (final Primitive primitive : byPrimitiveNumber)
		{
			if (primitive != null)
			{
				primitive.cachedBlockTypeRestriction = null;
				primitive.cachedFailureVariableType = null;
			}
		}
	}

	/**
	 * This primitive's number.  The Avail source code refers to this primitive
	 * by number.
	 */
	public short primitiveNumber;

	/**
	 * The number of arguments this primitive expects.
	 */
	int argCount;

	/**
	 * The flags that indicate to the {@link L2Translator} how an invocation of
	 * this primitive should be handled.
	 */
	private EnumSet<Flag> primitiveFlags;

	/**
	 * Test whether the specified {@link Flag} is set for this primitive.
	 *
	 * @param flag The {@code Flag} to test.
	 * @return Whether that {@code Flag} is set for this primitive.
	 */
	public final boolean hasFlag (final Flag flag)
	{
		return primitiveFlags.contains(flag);
	}

	/**
	 * The number of arguments this primitive expects.
	 *
	 * @return The count of arguments for this primitive.
	 */
	public final int argCount()
	{
		return argCount;
	}

	/**
	 * A number at least as large as the highest used primitive number.
	 */
	public final static int maxPrimitiveNumber = 1000;

	/**
	 * Whether an attempt has already been made to load the primitive whose
	 * {@link #primitiveNumber} is the index into this array of {@code boolean}.
	 */
	private final static boolean[] searchedByPrimitiveNumber =
		new boolean[maxPrimitiveNumber + 1];

	/**
	 * An array of all primitives, indexed by primitive number.
	 */
	private final static Primitive[] byPrimitiveNumber =
		new Primitive[maxPrimitiveNumber + 1];

	/**
	 * The cached mapping from primitive numbers to absolute class names.
	 */
	private static Map<Short, String> primitiveNames;

	/**
	 * Find all primitive names by scanning the relevant jars or classpath
	 * directories, populating {@link #primitiveNames}.
	 */
	private static void findPrimitives()
	{
		assert primitiveNames == null;
		final String packageName = P_001_Addition.class.getPackage().getName();
		try
		{
			final ClassLoader classLoader = Primitive.class.getClassLoader();
			assert classLoader != null;
			final Enumeration<URL> resources = classLoader.getResources(
				packageName.replace('.', File.separatorChar));
			primitiveNames = new HashMap<Short, String>();
			final Pattern pattern =
				Pattern.compile("P_(\\d+)_(\\w+)\\.class");
			for (final URL resource : Collections.list(resources))
			{
				final File dir = new File(
					URLDecoder.decode(resource.getFile(), "UTF-8"));
				assert dir.exists();
				assert dir.isDirectory();
				for (final File file : dir.listFiles())
				{
					final String name = file.getName();
					final Matcher matcher = pattern.matcher(name);
					if (matcher.matches())
					{
						final String primNumString = matcher.group(1);
						final String primNameString = matcher.group(2);
						final Short primNum = Short.valueOf(primNumString);
						assert !primitiveNames.containsKey(primNum);
						primitiveNames.put(
							primNum,
							packageName
								+ ".P_" + primNumString
								+ "_" + primNameString);
					}
				}
			}
		}
		catch (final UnsupportedEncodingException e)
		{
			throw new RuntimeException(e);
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Answer the primitive with the given number.
	 *
	 * @param primitiveNumber The primitive number to look up.  Must be between
	 *           0 and {@link #maxPrimitiveNumber}.
	 * @return The primitive.
	 *
	 * @throws RuntimeException if the primitive is not valid.
	 */
	public static Primitive byPrimitiveNumberOrFail (
		final int primitiveNumber)
	{
		final Primitive primitive = byPrimitiveNumberOrNull(primitiveNumber);
		if (primitive == null)
		{
			throw new RuntimeException(
				"Illegal primitive number: " + primitiveNumber);
		}
		return primitive;
	}

	/**
	 * Locate the primitive that has the specified primitive number.
	 *
	 * @param primitiveNumber The primitive number for which to search.
	 * @return The primitive with the specified primitive number.
	 */
	public static @Nullable Primitive byPrimitiveNumberOrNull (
		final int primitiveNumber)
	{
		assert primitiveNumber >= 0 && primitiveNumber <= maxPrimitiveNumber;
		if (primitiveNumber == 0)
		{
			return null;
		}
		if (!searchedByPrimitiveNumber[primitiveNumber])
		{
			synchronized (Primitive.class)
			{
				if (!searchedByPrimitiveNumber[primitiveNumber])
				{
					if (primitiveNames == null)
					{
						findPrimitives();
					}
					assert primitiveNames != null;
					final String className = primitiveNames.get(
						Short.valueOf((short)primitiveNumber));
					if (className != null)
					{
						final ClassLoader loader =
							Primitive.class.getClassLoader();
						try
						{
							final Class<?> primClass =
								loader.loadClass(className);
							// Trigger the linker.
							primClass.getField("instance").get(null);
							assert byPrimitiveNumber[primitiveNumber] != null;
						}
						catch (final ClassNotFoundException e)
						{
							// Ignore if no such primitive.
						}
						catch (final NoSuchFieldException e)
						{
							throw new RuntimeException(e);
						}
						catch (final IllegalAccessException e)
						{
							throw new RuntimeException(e);
						}
					}
				}
			}
			// The two synchronized blocks around this comment must not be
			// combined, since then the assignment to searchedByPrimitiveNumber
			// might leak (to the non-synchronized testing read) before the
			// corresponding write to byPrimitiveNumber.  A volatile write would
			// also work, but it's an array... and this is Java.
			synchronized (Primitive.class)
			{
				searchedByPrimitiveNumber[primitiveNumber] = true;
			}
		}
		return byPrimitiveNumber[primitiveNumber];
	}

	/**
	 * Does the {@linkplain Interpreter interpreter} provide a {@linkplain
	 * Primitive primitive} with the specified primitive number?
	 *
	 * @param primitiveNumber
	 *        The primitive number.
	 * @return {@code true} if there is a {@linkplain Primitive primitive}
	 *         with the specified primitive number, {@code false} otherwise.
	 */
	public static boolean supportsPrimitive (final int primitiveNumber)
	{
		final Primitive primitive = Primitive.byPrimitiveNumberOrNull(
			primitiveNumber);
		return primitive != null && !primitive.hasFlag(Flag.Private);
	}

	/**
	 * Answer whether the specified primitive accepts the specified number of
	 * arguments.  Note that some primitives expect a variable number of
	 * arguments.
	 *
	 * @param primitiveNumber Which primitive.
	 * @param argCount The number of arguments that we should check is legal for
	 *                 this primitive.
	 * @return Whether the primitive accepts the specified number of arguments.
	 */
	public static boolean primitiveAcceptsThisManyArguments (
		final int primitiveNumber,
		final int argCount)
	{
		final Primitive primitive =
			Primitive.byPrimitiveNumberOrNull(primitiveNumber);
		assert primitive != null;
		final int expected = primitive.argCount();
		return expected == -1 || expected == argCount;
	}

	/**
	 * Answer the name of this primitive, which is just the class name.
	 *
	 * @return The name of this primitive.
	 */
	@Override
	public String name ()
	{
		return this.getClass().getSimpleName();
	}

	/**
	 * Be compliant with {@link IntegerEnumSlotDescriptionEnum} – although it
	 * shouldn't really be used, since we want to index by primitive number
	 * instead of ordinal.
	 *
	 * @return The ordinal of this primitive, in theory.
	 */
	@Override
	public int ordinal ()
	{
		error("Primitive ordinal() should not be used.");
		return 0;
	}

	/**
	 * Initialize a newly constructed {@link Primitive}.  The first argument is
	 * a primitive number, the second is the number of arguments with which the
	 * primitive expects to be invoked, and the remaining arguments are
	 * {@linkplain Flag flags}.
	 *
	 * @param theArgCount The number of arguments the primitive expects.
	 * @param flags The flags that describe how the {@link L2Translator} should
	 *              deal with this primitive.
	 * @return The initialized primitive.
	 */
	protected Primitive init (
		final int theArgCount,
		final Flag ... flags)
	{
		assert primitiveNumber == 0;
		final String className = this.getClass().getName();
		final String numericPart = className.replaceAll(
			"^.*P_(\\d+)_(\\w+)$",
			"$1");
		primitiveNumber = Short.valueOf(numericPart);
		argCount = theArgCount;
		primitiveFlags = EnumSet.noneOf(Flag.class);
		for (final Flag flag : flags)
		{
			primitiveFlags.add(flag);
		}

		// Register this instance.
		assert primitiveNumber >= 1 && primitiveNumber <= maxPrimitiveNumber;
		assert byPrimitiveNumber[primitiveNumber] == null;
		byPrimitiveNumber[primitiveNumber] = this;
		return this;
	}

	/**
	 * A performance metric indicating how long was spent checking the return
	 * result for all invocations of this primitive in level two code.  An
	 * excessively large value indicates a profitable opportunity for {@link
	 * #returnTypeGuaranteedByVM(List)} to return a stronger
	 * type, perhaps allowing the level two optimizer to skip more checks.
	 */
	@InnerAccess long debugMicrosecondsCheckingResultType = 0L;

	/**
	 * Record that some number of microseconds were just expended checking the
	 * type of the value returned by this primitive.
	 *
	 * @param deltaMicroseconds
	 */
	public void addMicrosecondsCheckingResultType (
		final long deltaMicroseconds)
	{
		debugMicrosecondsCheckingResultType += deltaMicroseconds;
	}

	/**
	 * Produce a report showing which primitives cost the most time to have
	 * their result types checked.
	 *
	 * @return The report, a potentially long string.
	 */
	public static String reportReturnCheckTimes ()
	{
		final StringBuilder builder = new StringBuilder();
		final List<Primitive> prims = new ArrayList<Primitive>();
		for (int i = 1; i <= maxPrimitiveNumber; i++)
		{
			final Primitive prim = byPrimitiveNumberOrNull(i);
			if (prim != null && prim.debugMicrosecondsCheckingResultType != 0)
			{
				prims.add(prim);
			}
		}
		Collections.sort(prims, new Comparator<Primitive>()
		{
			@Override
			public int compare (
				final @Nullable Primitive p1,
				final @Nullable Primitive p2)
			{
				assert p1 != null;
				assert p2 != null;
				return Long.signum(
					p2.debugMicrosecondsCheckingResultType
						- p1.debugMicrosecondsCheckingResultType);
			}
		});
		for (final Primitive prim : prims)
		{
			final long microseconds = prim.debugMicrosecondsCheckingResultType;
			builder.append(String.format(
				microseconds > 1000000
					? "%1$ 7.3f s  "
					: microseconds > 1000
						? "%2$ 7.3f ms "
						: "%3$ 3d     µs ",
					microseconds / 1.0e6,
					microseconds / 1.0e3,
					microseconds));
			builder.append(prim.name());
			builder.append('\n');
		}
		return builder.toString();
	}
}
