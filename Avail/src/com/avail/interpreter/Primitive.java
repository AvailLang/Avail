/**
 * Primitive.java
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

package com.avail.interpreter;

import static com.avail.descriptor.AvailObject.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2ImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadWriteVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operation.L2_ATTEMPT_INLINE_PRIMITIVE;
import com.avail.interpreter.levelTwo.operation.L2_ATTEMPT_INLINE_PRIMITIVE_NO_CHECK;
import com.avail.interpreter.levelTwo.operation.L2_GET_INVALID_MESSAGE_RESULT_FUNCTION;
import com.avail.interpreter.levelTwo.operation.L2_INVOKE;
import com.avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE;
import com.avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE_NO_CHECK;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.interpreter.primitive.*;
import com.avail.optimizer.*;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;
import com.avail.performance.Statistic;


/**
 * This enumeration represents the interface between Avail's Level One
 * nybblecode interpreter and the underlying interfaces of the built-in objects,
 * providing functionality that is (generally) inexpressible within Level One
 * in terms of other Level One operations.  A conforming Avail implementation
 * must provide these primitives with equivalent semantics.
 *
 * <p>The enumeration defines an {@link #attempt(List, Interpreter, boolean)
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
		 * The primitive returns the value of some global variable.  This is
		 * only used for {@link P_342_GetGlobalVariableValue}, which is detected
		 * automatically at code generation time.
		 */
		SpecialReturnGlobalValue,

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
	 * The actual fallibility of a fallible {@linkplain Primitive} when invoked
	 * with arguments of specific {@linkplain TypeDescriptor types}.
	 */
	public enum Fallibility
	{
		/**
		 * The fallible {@linkplain Primitive primitive} cannot fail when
		 * invoked with arguments of the specified {@linkplain TypeDescriptor
		 * types}.
		 */
		CallSiteCannotFail,

		/**
		 * The fallible {@linkplain Primitive primitive} can indeed fail when
		 * invoked with arguments of the specified {@linkplain TypeDescriptor
		 * types}.
		 */
		CallSiteCanFail,

		/**
		 * The fallible {@linkplain Primitive primitive} must fail when invoked
		 * with arguments of the specified {@linkplain TypeDescriptor types}.
		 */
		CallSiteMustFail
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
	 * @param args
	 *            The {@linkplain List list} of arguments to the primitive.
	 * @param interpreter
	 *            The {@link Interpreter} that is executing.
	 * @param skipReturnCheck
	 *            Whether the type-check of the return value can be elided, due
	 *            to VM type guarantees.
	 * @return The {@link Result} code indicating success or failure (or special
	 *         circumstance).
	 */
	public abstract Result attempt (
		List<AvailObject> args,
		Interpreter interpreter,
		boolean skipReturnCheck);

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
	private @Nullable A_Type cachedBlockTypeRestriction;

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
		A_Type restriction = cachedBlockTypeRestriction;
		if (cachedBlockTypeRestriction == null)
		{
			restriction = privateBlockTypeRestriction().makeShared();
			cachedBlockTypeRestriction = restriction;
			final A_Type argsTupleType = restriction.argsTupleType();
			final A_Type sizeRange = argsTupleType.sizeRange();
			assert restriction.isBottom()
				|| (sizeRange.lowerBound().extractInt() == argCount()
					&& sizeRange.upperBound().extractInt() == argCount());
		}
		return restriction;
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
	private @Nullable A_Type cachedFailureVariableType;

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
		A_Type failureType = cachedFailureVariableType;
		if (failureType == null)
		{
			failureType = privateFailureVariableType().makeShared();
			assert failureType.isType();
			cachedFailureVariableType = failureType;
		}
		return failureType;
	}

	/**
	 * Answer the {@linkplain Fallibility fallibility} of the {@linkplain
	 * Primitive primitive} for a call site with the given argument {@linkplain
	 * TypeDescriptor types}.
	 *
	 * @param argumentTypes
	 *        A {@linkplain List list} of argument types.
	 * @return The fallibility of the call site.
	 */
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
		return hasFlag(Flag.CannotFail)
			? Fallibility.CallSiteCannotFail
			: Fallibility.CallSiteCanFail;
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
	private final EnumSet<Flag> primitiveFlags = EnumSet.noneOf(Flag.class);

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
	private static @Nullable Map<Short, String> primitiveNames;

	/**
	 * The name of a generated file which lists all primitive classes.  The file
	 * is generated by the build process and is included in build products as
	 * necessary.
	 */
	private final static String allPrimitivesFileName = "All_Primitives.txt";

	/**
	 * The pattern of the simple names of {@link Primitive} classes.
	 */
	private final static Pattern primitiveNamePattern =
		Pattern.compile("P_(\\d+)_(\\w+)");

	/**
	 * If we're running from the file system (i.e., development time), scan the
	 * relevant class path directories to locate all primitive files.  Record
	 * the list of primitive names, one per line, in All_Primitives.txt.
	 * Otherwise we're running from some packaged representation like a .jar, so
	 * expect the All_Primitives.txt file to be already present, so just use it
	 * instead of scanning.
	 */
	private static void findPrimitives()
	{
		final Map<Short, String> names = new HashMap<>();
		final ClassLoader classLoader = Primitive.class.getClassLoader();
		assert classLoader != null;
		try
		{
			// Always read the All_Primitives.txt file first, either because
			// that's all we'll do, or so that we can tell if it needs to be
			// updated.
			final URL resource = Primitive.class.getResource(
				allPrimitivesFileName);
			BufferedReader input = null;
			try
			{
				input = new BufferedReader(
					new InputStreamReader(
						resource.openStream(),
						StandardCharsets.UTF_8));
				String line;
				while ((line = input.readLine()) != null)
				{
					final String[] parts = line.split("\\.");
					final String lastPart = parts[parts.length - 1];
					final Matcher matcher =
						primitiveNamePattern.matcher(lastPart);
					if (matcher.matches())
					{
						final Short primNum = Short.valueOf(matcher.group(1));
						assert !names.containsKey(primNum);
						names.put(primNum, line);
					}
				}
				primitiveNames = names;
			}
			finally
			{
				if (input != null)
				{
					input.close();
				}
			}
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
					Map<Short, String> names = primitiveNames;
					if (names == null)
					{
						findPrimitives();
						names = primitiveNames;
						assert names != null;
					}
					final String className = names.get(
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
	 * Answer whether a raw function using this primitive can/should have
	 * nybblecode instructions.
	 *
	 * @return Whether this primitive has failure/alternative code.
	 */
	public boolean canHaveNybblecodes ()
	{
		return
			!hasFlag(Primitive.Flag.CannotFail)
				|| hasFlag(Primitive.Flag.SpecialReturnConstant)
				|| hasFlag(Primitive.Flag.SpecialReturnSoleArgument)
				|| hasFlag(Primitive.Flag.SpecialReturnGlobalValue);
	}

	/**
	 * Determine whether the specified primitive declaration is acceptable to be
	 * used with the given list of parameter declarations.  Answer null if they
	 * are acceptable, otherwise answer a suitable {@code String} that is
	 * expected to appear after the prefix "Expecting...".
	 *
	 * @param primitiveNumber Which primitive.
	 * @param arguments The argument declarations that we should check are legal
	 *                  for this primitive.
	 * @return Whether the primitive accepts arguments with types that conform
	 *         to the given argument declarations.
	 */
	public static @Nullable String validatePrimitiveAcceptsArguments (
		final int primitiveNumber,
		final List<A_Phrase> arguments)
	{
		final Primitive primitive =
			Primitive.byPrimitiveNumberOrNull(primitiveNumber);
		assert primitive != null;
		final int expected = primitive.argCount();
		if (expected == -1)
		{
			return null;
		}
		if (arguments.size() != expected)
		{
			return String.format(
				"number of declared arguments (%d) to agree with primitive's"
				+ " required number of arguments (%d).",
				arguments.size(),
				expected);
		}
		final A_Type expectedTypes =
			primitive.blockTypeRestriction().argsTupleType();
		assert expectedTypes.sizeRange().upperBound().extractInt() == expected;
		final StringBuilder builder = new StringBuilder();
		for (int i = 1; i <= expected; i++)
		{
			final A_Type declaredType = arguments.get(i - 1).declaredType();
			final A_Type expectedType = expectedTypes.typeAtIndex(i);
			if (!declaredType.isSubtypeOf(expectedType))
			{
				if (builder.length() > 0)
				{
					builder.append("\n");
				}
				builder.append(
					String.format(
						"argument #%d (%s) of primitive %s to be a subtype"
						+ " of %s, not %s.",
						i,
						arguments.get(i - 1).token().string(),
						primitive.name(),
						expectedType,
						declaredType));
			}
		}
		if (builder.length() == 0)
		{
			return null;
		}
		return builder.toString();
	}

	/** Capture the name of the primitive class once for performance. */
	final String name = getClass().getSimpleName();

	/**
	 * Answer the name of this primitive, which is just the class's simple name,
	 * as previously captured by the {@link #name} field.
	 *
	 * @return The name of this primitive.
	 */
	@Override
	public String name ()
	{
		return name;
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
	 * A performance metric indicating how long was spent executing each
	 * primitive.
	 */
	final Statistic runningNanos =
		new Statistic(getClass().getSimpleName() + " (running)");

	/**
	 * Record that some number of nanoseconds were just expended running this
	 * primitive.
	 *
	 * @param deltaNanoseconds
	 */
	public void addNanosecondsRunning (
		final long deltaNanoseconds)
	{
		runningNanos.record(deltaNanoseconds);
	}

	/**
	 * A performance metric indicating how long was spent checking the return
	 * result for all invocations of this primitive in level two code.  An
	 * excessively large value indicates a profitable opportunity for {@link
	 * #returnTypeGuaranteedByVM(List)} to return a stronger
	 * type, perhaps allowing the level two optimizer to skip more checks.
	 */
	final Statistic resultTypeCheckingNanos =
		new Statistic(getClass().getSimpleName() + " (checking result)");

	/**
	 * Record that some number of nanoseconds were just expended checking the
	 * type of the value returned by this primitive.
	 *
	 * @param deltaNanoseconds
	 */
	public void addNanosecondsCheckingResultType (
		final long deltaNanoseconds)
	{
		resultTypeCheckingNanos.record(deltaNanoseconds);
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
		assert primitiveFlags.isEmpty();
		for (final Flag flag : flags)
		{
			assert !primitiveFlags.contains(flag)
				: "Duplicate flag in " + getClass().getSimpleName();
			primitiveFlags.add(flag);
		}
		// Sanity check certain conditions.
		assert !primitiveFlags.contains(Flag.CanFold)
				| primitiveFlags.contains(Flag.CanInline)
			: "Primitive " + getClass().getSimpleName()
				+ "has CanFold without CanInline";

		// Register this instance.
		assert primitiveNumber >= 1 && primitiveNumber <= maxPrimitiveNumber;
		assert byPrimitiveNumber[primitiveNumber] == null;
		byPrimitiveNumber[primitiveNumber] = this;
		return this;
	}

	/**
	 * Produce a report showing which primitives cost the most time to run.
	 *
	 * @param builder The {@link StringBuilder} into which to write the report.
	 */
	public static void reportRunTimes (final StringBuilder builder)
	{
		builder.append("Primitive run times:\n");
		final List<Statistic> stats = new ArrayList<>();
		for (int i = 1; i <= maxPrimitiveNumber; i++)
		{
			final Primitive prim = byPrimitiveNumberOrNull(i);
			if (prim != null && prim.runningNanos.count() != 0)
			{
				stats.add(prim.runningNanos);
			}
		}
		Collections.sort(stats);
		for (final Statistic stat : stats)
		{
			stat.describeNanosecondsOn(builder);
			builder.append('\n');
		}
	}

	/**
	 * Produce a report showing which primitives cost the most time to have
	 * their result types checked.
	 *
	 * @param builder The {@link StringBuilder} into which to write the report.
	 */
	public static void reportReturnCheckTimes (final StringBuilder builder)
	{
		builder.append("Primitive return dynamic checks:\n");
		final List<Statistic> stats = new ArrayList<>();
		for (int i = 1; i <= maxPrimitiveNumber; i++)
		{
			final Primitive prim = byPrimitiveNumberOrNull(i);
			if (prim != null && prim.resultTypeCheckingNanos.count() != 0)
			{
				stats.add(prim.resultTypeCheckingNanos);
			}
		}
		Collections.sort(stats);
		for (final Statistic stat : stats)
		{
			stat.describeNanosecondsOn(builder);
			builder.append('\n');
		}
	}

	/**
	 * Clear all statistics related to primitives.
	 */
	public static void clearAllStats ()
	{
		for (int i = 1; i <= maxPrimitiveNumber; i++)
		{
			final Primitive prim = byPrimitiveNumberOrNull(i);
			if (prim != null)
			{
				prim.runningNanos.clear();
				prim.resultTypeCheckingNanos.clear();
			}
		}
	}

	/**
	 * This is an *inlineable* primitive, so it can only succeed with some
	 * value or fail.  The value can't be an instance of bottom, so the
	 * primitive's guaranteed return type can't be bottom.  If the primitive
	 * fails, the backup code can produce bottom, but since this primitive
	 * could have succeeded instead, the function itself must not be
	 * naturally bottom typed.  If a semantic restriction has strengthened
	 * the result type to bottom, only the backup code's return instruction
	 * would be at risk, but invalidly returning some value from there would
	 * have to check the value's type against the expected type -- and then
	 * fail to return.
	 *
	 * @param translator
	 *        The {@link L1NaiveTranslator} in which to generate an inlined
	 *        call to this primitive.
	 * @param primitiveFunction
	 *        The {@link Primitive} {@linkplain FunctionDescriptor function}
	 *        being invoked.
	 * @param args
	 *        The {@link L2RegisterVector} holding the registers that will
	 *        provide arguments at invocation time.
	 * @param resultRegister
	 *        The {@link L2ObjectRegister} into which the result should be
	 *        placed at invocation time.
	 * @param preserved
	 *        The {@link L2RegisterVector} holding the registers whose values
	 *        must be preserved whether the primitive succeeds or fails.
	 * @param expectedType
	 *        The type of value that this primitive invocation is expected to
	 *        produce at this call site.
	 * @param failureValueRegister
	 *        Where to write the primitive failure value in the case that the
	 *        primitive fails.
	 * @param successLabel
	 *        Where to jump if the primitive is successful. If the primitive
	 *        fails it simply falls through.
	 * @param canFailPrimitive
	 *        Whether it's possible for the primitive to fail with the provided
	 *        types of arguments.
	 * @param skipReturnCheck
	 *        Whether we can safely skip the check that the primitive's return
	 *        value matches the type expected at this call site.
	 */
	public void generateL2UnfoldableInlinePrimitive (
		final L1NaiveTranslator translator,
		final A_Function primitiveFunction,
		final L2RegisterVector args,
		final L2ObjectRegister resultRegister,
		final L2RegisterVector preserved,
		final A_Type expectedType,
		final L2ObjectRegister failureValueRegister,
		final L2Instruction successLabel,
		final boolean canFailPrimitive,
		final boolean skipReturnCheck)
	{
		if (!canFailPrimitive)
		{
			// Note that a primitive cannot have return type of bottom
			// unless it's non-inlineable (already excluded here).
			assert !expectedType.isBottom();
			if (skipReturnCheck)
			{
				translator.addInstruction(
					L2_RUN_INFALLIBLE_PRIMITIVE_NO_CHECK.instance,
					new L2PrimitiveOperand(this),
					new L2ReadVectorOperand(args),
					new L2WritePointerOperand(resultRegister));
			}
			else
			{
				translator.addInstruction(
					L2_RUN_INFALLIBLE_PRIMITIVE.instance,
					new L2PrimitiveOperand(this),
					new L2ReadVectorOperand(args),
					new L2ConstantOperand(expectedType),
					new L2WritePointerOperand(resultRegister),
					new L2PcOperand(successLabel));
				final L2Instruction unreachableLabel =
					translator.newLabel("unreachable");
				final L2ObjectRegister invalidResultFunction =
					translator.newObjectRegister();
				translator.addInstruction(
					L2_GET_INVALID_MESSAGE_RESULT_FUNCTION.instance,
					new L2WritePointerOperand(invalidResultFunction));
				final L2ObjectRegister reifiedRegister =
					translator.newObjectRegister();
				translator.reify(
					translator.continuationSlotsList(translator.numSlots()),
					reifiedRegister,
					unreachableLabel);
				translator.addInstruction(
					L2_INVOKE.instance,
					new L2ReadPointerOperand(reifiedRegister),
					new L2ReadPointerOperand(invalidResultFunction),
					new L2ReadVectorOperand(translator.createVector(
						Collections.<L2ObjectRegister>emptyList())),
					new L2ImmediateOperand(1));
				translator.unreachableCode(unreachableLabel);
				translator.addLabel(successLabel);
			}
		}
		else
		{
			if (skipReturnCheck)
			{
				translator.addInstruction(
					L2_ATTEMPT_INLINE_PRIMITIVE_NO_CHECK.instance,
					new L2PrimitiveOperand(this),
					new L2ConstantOperand(primitiveFunction),
					new L2ReadVectorOperand(args),
					new L2WritePointerOperand(resultRegister),
					new L2WritePointerOperand(failureValueRegister),
					new L2ReadWriteVectorOperand(preserved),
					new L2PcOperand(successLabel));
			}
			else
			{
				final L2Instruction failureLabel =
					translator.newLabel("failure");
				translator.addInstruction(
					L2_ATTEMPT_INLINE_PRIMITIVE.instance,
					new L2PrimitiveOperand(this),
					new L2ConstantOperand(primitiveFunction),
					new L2ReadVectorOperand(args),
					new L2ConstantOperand(expectedType),
					new L2WritePointerOperand(resultRegister),
					new L2WritePointerOperand(failureValueRegister),
					new L2ReadWriteVectorOperand(preserved),
					new L2PcOperand(successLabel),
					new L2PcOperand(failureLabel));
				final L2Instruction unreachableLabel =
					translator.newLabel("unreachable");
				final L2ObjectRegister invalidResultFunction =
					translator.newObjectRegister();
				translator.addInstruction(
					L2_GET_INVALID_MESSAGE_RESULT_FUNCTION.instance,
					new L2WritePointerOperand(invalidResultFunction));
				final L2ObjectRegister reifiedRegister =
					translator.newObjectRegister();
				translator.reify(
					translator.continuationSlotsList(translator.numSlots()),
					reifiedRegister,
					unreachableLabel);
				translator.addInstruction(
					L2_INVOKE.instance,
					new L2ReadPointerOperand(reifiedRegister),
					new L2ReadPointerOperand(invalidResultFunction),
					new L2ReadVectorOperand(translator.createVector(
						Collections.<L2ObjectRegister>emptyList())),
					new L2ImmediateOperand(1));
				translator.unreachableCode(unreachableLabel);
				translator.addLabel(failureLabel);
			}
		}
	}

}
