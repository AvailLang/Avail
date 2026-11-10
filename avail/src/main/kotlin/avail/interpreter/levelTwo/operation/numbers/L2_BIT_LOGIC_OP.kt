/*
 * L2_BIT_LOGIC_OP.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
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

package avail.interpreter.levelTwo.operation.numbers

import avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Number.Companion.extractLong
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.typeIntersection
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i63
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.JavaLibrary.long
import avail.interpreter.JavaLibrary.longMaxMethod
import avail.interpreter.JavaLibrary.longMinMethod
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.Pattern.BinaryOp
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.Pattern.C
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.Pattern.Constant
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.Pattern.Constant.K1
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.Pattern.Constant.K2
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.Pattern.Constant.K3
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.Pattern.Constant.K4
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.Pattern.Variable
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.Pattern.Variable.W
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.Pattern.Variable.X
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.Pattern.Variable.Y
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.Pattern.Variable.Z
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.primitive.Primitive
import avail.interpreter.primitive.integers.P_BitShiftLeft
import avail.interpreter.primitive.integers.P_BitShiftRight
import avail.interpreter.primitive.integers.P_BitwiseAnd
import avail.interpreter.primitive.integers.P_BitwiseOr
import avail.interpreter.primitive.integers.P_BitwiseXor
import avail.interpreter.primitive.numbers.P_Addition
import avail.interpreter.primitive.numbers.P_Division
import avail.interpreter.primitive.numbers.P_Division.positiveI63
import avail.interpreter.primitive.numbers.P_Multiplication
import avail.interpreter.primitive.numbers.P_Subtraction
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.values.L2SemanticValue.Companion.constant
import avail.utility.addOrSubtractOverflows
import org.objectweb.asm.Opcodes
import kotlin.math.max
import kotlin.math.min

/**
 * My instances are logic operations that take two [Int]s and produce an [Int].
 * They must not overflow.
 *
 * @constructor
 *   Instantiate a two-argument [i32] logic operation for a particular purpose,
 *   such as addition or exclusive-or.  Some operations require stronger
 *   restrictions on the inputs.
 * @property bitOperation
 *   The [BitOperation] to perform on the [i32]s.
 * @property input1
 *   The source of the first operand.
 * @property input2
 *   The source of the second operand.
 * @property output
 *   Where to write the result of the logical operation.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_BIT_LOGIC_OP
constructor(
	operation: BitOperation,
	var input1: L2ReadIntOperand,
	var input2: L2ReadIntOperand,
	var output: L2WriteIntOperand
): L2Instruction()
{
	var bitOperation = L2ArbitraryConstantOperand(operation)

	override val name get() = "${super.name}(${bitOperation.constant.name})"

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(output.registerString())
		append(" ← ")
		append(input1.registerString())
		append(" $bitOperation ")
		append(input2.registerString())
	}

	override fun isBitLogicOperation(op: BitOperation) =
		bitOperation.constant == op

	override fun instructionWasAdded(manifest: L2ValueManifest)
	{
		bitOperation.constant.run {
			manifest.assertInputsAreValid(input1, input2)
		}
		super.instructionWasAdded(manifest)
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		// Handle fresh folding opportunities that arise from passes like code
		// splitting.
		input1.constantOrNull?.let { constant1 ->
			input2.constantOrNull?.let { constant2 ->
				// Calculate it now (i.e., fold the constants).
				val folded = bitOperation.constant.folder(
					constant1.extractLong,
					constant2.extractLong)
				currentManifest.agglomerateSynonym(
					output.semanticValues(),
					restrictionForConstant(fromLong(folded)))
				return
			}
		}
		val newBound =
			bitOperation.constant.bounder(input1.type, input2.type)
		+L2_BIT_LOGIC_OP(
			bitOperation.constant,
			input1,
			input2,
			L2WriteIntOperand(
				output.semanticValues(),
				output.restriction().intersectionWithType(newBound)))
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: output = input1 op input2;
		loadLong(input1)
		loadLong(input2)
		bitOperation.constant.jvmGenerator(this)
		storeLong(output.register())
	}

	class RulesSyntax constructor(
		val thisOperation: BitOperation,
		val rules: MutableList<Rule> = mutableListOf())
	{
		fun rule(
			left: Pattern,
			right: Pattern,
			condition: RuleEvaluationContext.() -> Boolean = { true },
			producer: RuleEvaluationContext.() -> Pattern)
		{
			val pattern = thisOperation(left, right)
			rules.add(Rule(pattern, condition, producer))
		}
	}

	/**
	 * A [RuleEvaluationContext] is created when attempting to apply rules to an
	 * actual control flow graph.  The corresponding matched [Pattern] is used
	 * to determine the sources ([L2ReadIntOperand]s) of [Variable]s, and the
	 * [Long] values of [Constant]s.  This context is then used as an implied
	 * receiver during the rule evaluation, allowing access to [k1]..[k4] for
	 * the values that were captured due to appearing as [K1]..[K4] in the
	 * match [Pattern], as well as the analagous [L2ReadIntOperand]s in [w]..[z]
	 * corresponding with variables [W]..[Z] in the pattern.  The rule lambda
	 * then produces a new transformed [Pattern], or `null` if it was determined
	 * by the lambda to be inapplicable.
	 *
	 * @constructor
	 *   Constructed by an optimizer performing transformations on the
	 *   [L2ControlFlowGraph], for the purpose of rewriting [Int] calculations
	 *   to be more efficient.
	 *
	 * @property variablesMap
	 *   A [Map] from [Variable] to [L2ReadIntOperand], populated for all
	 *   [Variable]s that occured in the match [Pattern].
	 * @property constantsMap
	 *   A [Map] from [Constant] to [Long] from the actual [L2ControlFlowGraph],
	 *   populated for each [Constant] that occured in the match [Pattern].
	 */
	class RuleEvaluationContext(
		private val variablesMap: Map<Variable, L2ReadIntOperand>,
		private val constantsMap: Map<Constant, Long>,
		private val typesMap: Map<L2ReadIntOperand, A_Type>)
	{
		val k1: Long get() = constantsMap[K1]!!
		val k2: Long get() = constantsMap[K2]!!
		val k3: Long get() = constantsMap[K3]!!
		val k4: Long get() = constantsMap[K4]!!
		val w: L2ReadIntOperand get() = variablesMap[W]!!
		val x: L2ReadIntOperand get() = variablesMap[X]!!
		val y: L2ReadIntOperand get() = variablesMap[Y]!!
		val z: L2ReadIntOperand get() = variablesMap[Z]!!
		val L2ReadIntOperand.type: A_Type get() = typesMap[this]!!
		val L2ReadIntOperand.low: Long get() = type.lowerBound.extractLong
		val L2ReadIntOperand.high: Long get() = type.upperBound.extractLong

		/**
		 * Whether shifting every possible value of this read left by [shift]
		 * stays within a [Long], as [BitOperation.Shl] requires.
		 *
		 * @param shift
		 *   The number of bit positions to shift left.
		 * @return
		 *   Whether the shift is in 0..63 and can't overflow a [Long].
		 */
		fun L2ReadIntOperand.shlFitsLong(shift: Int): Boolean =
			shift in 0..63
				&& low >= Long.MIN_VALUE shr shift
				&& high <= Long.MAX_VALUE shr shift
	}

	/**
	 * The meat of an [L2_BIT_LOGIC_OP], controlling what JVM code gets
	 * generated, and what reductions are allowed.
	 *
	 * @constructor
	 *   Create a new [BitOperation] enumeration value.
	 * @property jvmGenerator
	 *   A function that emits JVM code to process the two `long` values that
	 *   have already been pushed.  It should have the effect of removing them
	 *   from the JVM operand stack and leaving a single `long` as the result.
	 *   [Add], [Sub], [Mul], and [Shl] produce the exact result, which must be
	 *   known to fit in a `long`.  [And], [Or], [Xor], [Max], [Min], [Shr],
	 *   and [SelectBit] produce the exact result for any `long` inputs, as do
	 *   [Div] and [Mod] for a non-negative dividend and a positive divisor.
	 *   Only [Ushr] has 32-bit semantics: its first input is a sign-extended
	 *   int, and its result is the sign-extended int that the 32-bit unsigned
	 *   shift produces.
	 * @property folder
	 *   A function that folds this operation on two constants, each a
	 *   sign-extended int.  It must produce the same value as the code that
	 *   [jvmGenerator] emits.
	 * @property bounder
	 *   A function for computing a type bound on the result, given type bounds
	 *   for the two arguments.
	 * @property ruleCreator
	 *   A lambda that is able to produce rules, via [RulesSyntax], once all the
	 *   enum values have been created, since rules for an operation refer to
	 *   other operations, sometimes cyclically.
	 */
	enum class BitOperation private constructor(
		val jvmGenerator: JVMTranslator.()->Unit,
		val folder: (Long, Long)->Long,
		val bounder: (A_Type, A_Type)->A_Type,
		private val ruleCreator: RulesSyntax.()->Unit)
	{
		/**
		 * Compute the bit-wise [Int.and] of two [Int]s.
		 */
		And(Opcodes.LAND, Long::and, P_BitwiseAnd::binaryBound, {
			rule(C(0), X) { C(0) }
			rule(X, C(0)) { C(0) }
			rule(C(-1), X) { X }
			rule(X, C(-1)) { X }
			rule(X, X) { X }
			rule(And(K1, X), K2) { And(X, C(k1 and k2)) }
			rule(And(X, K1), K2) { And(X, C(k1 and k2)) }
			rule(K1, And(K2, X)) { And(X, C(k1 and k2)) }
			rule(K1, And(X, K2)) { And(X, C(k1 and k2)) }
		}),

		/**
		 * Compute the bit-wise [Int.or] of two [Int]s.
		 */
		Or(Opcodes.LOR, Long::or, P_BitwiseOr::binaryBound, {
			rule(C(0), X) { X }
			rule(X, C(0)) { X }
			rule(C(-1), X) { C(-1) }
			rule(X, C(-1)) { C(-1) }
			rule(X, X) { X }
			rule(Or(K1, X), K2) { Or(X, C(k1 or k2)) }
			rule(Or(X, K1), K2) { Or(X, C(k1 or k2)) }
			rule(K1, Or(K2, X)) { Or(X, C(k1 or k2)) }
			rule(K1, Or(X, K2)) { Or(X, C(k1 or k2)) }
		}),

		/**
		 * Compute the bit-wise [Int.xor] of two [Int]s.
		 */
		Xor(Opcodes.LXOR, Long::xor, P_BitwiseXor::binaryBound, {
			rule(C(0), X) { X }
			rule(X, C(0)) { X }
			rule(X, X) { C(0) }
			rule(X, Xor(X, Y)) { Y }
			rule(X, Xor(Y, X)) { Y }
			rule(Xor(X, Y), X) { Y }
			rule(Xor(Y, X), X) { Y }
		}),

		/**
		 * Compute the sum of two integers.  This must only be used where the
		 * sum is known to fit in a [Long].
		 */
		Add(Opcodes.LADD, Long::plus, P_Addition::binaryBound, {
			rule(C(0), X) { X }
			rule(X, C(0)) { X }
			// Bring together constants, unless the combined constant would
			// overflow.
			rule(K1, Add(X, K2), {!addOrSubtractOverflows(k1, k2)}) {
				Add(X, C(k1 + k2))
			}
			rule(Add(X, K1), K2, {!addOrSubtractOverflows(k1, k2)}) {
				Add(X, C(k1 + k2))
			}
			rule(X, X, {x.shlFitsLong(1)}) { Shl(X, C(1)) }
			rule(X, Mul(X, K1), {!addOrSubtractOverflows(k1, 1)}) {
				Mul(X, C(k1 + 1))
			}
			rule(Mul(X, K1), X, {!addOrSubtractOverflows(k1, 1)}) {
				Mul(X, C(k1 + 1))
			}
			rule(Mul(X, K1), Mul(X, K2), {!addOrSubtractOverflows(k1, k2)}) {
				Mul(X, C(k1 + k2))
			}
		}),

		/**
		 * Compute the difference of two integers (the first minus the second).
		 * This must only be used where the difference is known to fit in a
		 * [Long].
		 */
		Sub(Opcodes.LSUB, Long::minus, P_Subtraction::binaryBound, {
			rule(X, C(0)) { X }
			// Negating the most negative value overflows.
			rule(X, K1, {!addOrSubtractOverflows(0, k1, -1)}) {
				Add(X, C(-k1))
			}
			rule(
				Sub(X, K1),
				K2,
				{
					!addOrSubtractOverflows(0, k1, -1)
						&& !addOrSubtractOverflows(-k1, k2, -1)
				}
			) {
				Add(X, C(-k1 - k2))
			}
			rule(Add(X, K1), K2, {!addOrSubtractOverflows(k1, k2, -1)}) {
				Add(X, C(k1 - k2))
			}
			rule(K1, Sub(X, K2), {!addOrSubtractOverflows(k1, k2)}) {
				Sub(C(k1 + k2), X)
			}
			rule(K1, Add(X, K2), {!addOrSubtractOverflows(k1, k2, -1)}) {
				Sub(C(k1 - k2), X)
			}
			// The new intermediate X + Z must not overflow, even though the
			// original intermediate Y - Z didn't.
			rule(
				X,
				Sub(Y, Z),
				{
					!addOrSubtractOverflows(x.low, z.low)
						&& !addOrSubtractOverflows(x.high, z.high)
				}
			) {
				Sub(Add(X, Z), Y)
			}
			// x - (y * (x / y))  --> x mod y
			rule(X, Mul(Y, Div(X, Y))) { Mod(X, Y) }
			rule(X, Mul(Div(X, Y), Y)) { Mod(X, Y) }
		}),

		/**
		 * Compute the product of two integers.  This must only be used where
		 * the product is known to fit in a [Long].
		 */
		Mul(Opcodes.LMUL, Long::times, P_Multiplication::binaryBound, {
			rule(X, C(-1)) { Sub(C(0), X) }
			rule(C(-1), X) { Sub(C(0), X) }
			rule(X, C(0)) { C(0) }
			rule(C(0), X) { C(0) }
			rule(X, C(1)) { X }
			rule(C(1), X) { X }
			// Only positive powers of two.  Zero has no set bit to count, and
			// the most negative value is only a power of two by wrapping.
			// The shifted result must fit in a long, as Shl requires.
			rule(
				K1,
				X,
				{
					k1 > 0 && k1.takeLowestOneBit() == k1
						&& x.shlFitsLong(k1.countTrailingZeroBits())
				}
			) {
				Shl(X, C(k1.countTrailingZeroBits().toLong()))
			}
			rule(
				X,
				K1,
				{
					k1 > 0 && k1.takeLowestOneBit() == k1
						&& x.shlFitsLong(k1.countTrailingZeroBits())
				}
			) {
				Shl(X, C(k1.countTrailingZeroBits().toLong()))
			}
		}),

		/**
		 * Compute the ratio of two [Long]s (i.e., a / b).  The numerator must
		 * be ≥ 0, and the denominator must be > 0, since the JVM's division
		 * truncates, but Avail's rounds toward negative infinity.  These
		 * agree only when neither operand is negative.
		 */
		Div(Opcodes.LDIV, Long::div, P_Division::binaryBound, {
			rule(C(0), X) { C(0) }
			rule(X, C(1)) { X }
			rule(X, K1, {k1 > 0 && k1.takeLowestOneBit() == k1}) {
				Shr(X, C(k1.countTrailingZeroBits().toLong()))
			}
		})
		{
			override fun L2ValueManifest.assertInputsAreValid(
				input1: L2ReadIntOperand,
				input2: L2ReadIntOperand
			) = assertNonnegativeDividendAndPositiveDivisor(input1, input2)
		},

		/**
		 * Compute the remainder after dividing two [Long]s (i.e., `a - b *
		 * (a / b)`).  The numerator must be ≥ 0, and the denominator must be >
		 * 0, as for [Div].
		 */
		Mod(Opcodes.LREM, Long::rem, ::modBound, {
			rule(C(0), X) { C(0) }
			// 1 mod 1 is 0, so the divisor must exceed 1.
			rule(C(1), X, {x.low > 1}) { C(1) }
			rule(X, X) { C(0) }
			rule(Mul(X, Y), X) { C(0) }
			rule(Mul(Y, X), X) { C(0) }
			rule(X, K1, {k1 > 0 && k1.takeLowestOneBit() == k1}) {
				And(X, C(k1 - 1))
			}
			// X mod (k<<z) where k=2^n --> X & ((k<<z)-1)
			rule(X, Shl(K1, Z), {k1 > 0 && k1.takeLowestOneBit() == k1}) {
				And(X, Sub(Shl(K1, Z), C(1)))
			}
		})
		{
			override fun L2ValueManifest.assertInputsAreValid(
				input1: L2ReadIntOperand,
				input2: L2ReadIntOperand
			) = assertNonnegativeDividendAndPositiveDivisor(input1, input2)
		},

		/**
		 * Shift an [Int] rightward by the specified number of bit positions,
		 * treating it as unsigned.  The second operand should be between 0 and
		 * 31, otherwise only the bottom five bits will be used.  The result can
		 * be negative if the first argument is negative and the shift is zero.
		 */
		Ushr(
			// The 32-bit unsigned shift needs the value zero-extended, but it
			// lies under the shift count on the stack, so use a helper.
			jvmGenerator = { ushr32Method.generateCall(this) },
			folder = ::ushr32,
			bounder = ::ushrBound,
			ruleCreator = {
				// Shift amounts outside 0..31 would be masked, so exclude them.
				rule(X, C(0)) { X }
				rule(
					Ushr(X, K1),
					K2,
					{k1 in 0L..31L && k2 in 0L..31L && k1 + k2 <= 31}
				) {
					Ushr(X, C(k1 + k2))
				}
				rule(
					Ushr(X, K1),
					K2,
					{k1 in 0L..31L && k2 in 0L..31L && k1 + k2 > 31}
				) {
					C(0)
				}
			}),

		/**
		 * Shift a [Long] rightward by the specified number of bit positions,
		 * respecting its sign.  The second operand should be between 0 and 63,
		 * otherwise only the bottom six bits will be used.
		 */
		Shr(
			jvmGenerator = { shiftLong(Opcodes.LSHR) },
			folder = { a, b -> a shr (b.toInt() and 63) },
			bounder = P_BitShiftRight::binaryBound,
			ruleCreator = {
				// Shift amounts outside 0..63 would be masked, so exclude them.
				rule(X, C(0)) { X }
				rule(
					Shr(X, K1),
					K2,
					{k1 in 0L..63L && k2 in 0L..63L && k1 + k2 <= 63}
				) {
					Shr(X, C(k1 + k2))
				}
				rule(X, Y, {x.type.isSubtypeOf(inclusive(-1, 0))}) { X }
			}),

		/**
		 * Shift a [Long] leftward by the specified number of bit positions.
		 * The second operand should be between 0 and 63, otherwise only the
		 * bottom six bits will be used.  This must only be used where the
		 * result is known to fit in a [Long].
		 */
		Shl(
			jvmGenerator = { shiftLong(Opcodes.LSHL) },
			folder = { a, b -> a shl (b.toInt() and 63) },
			bounder = P_BitShiftLeft::binaryBound,
			ruleCreator = {
				// Shift amounts outside 0..63 would be masked, so exclude them.
				rule(X, C(0)) { X }
				rule(
					Shl(X, K1),
					K2,
					{k1 in 0L..63L && k2 in 0L..63L && k1 + k2 <= 63}
				) {
					Shl(X, C(k1 + k2))
				}
				rule(Shr(X, K1), K1, {k1 in 0L..63L}) {
					And(X, C(-1L shl k1.toInt()))
				}
				// Ushr is a 32-bit operation, so it leaves only the low 32 bits
				// of X, zero-extended.  A shift of 0 is handled by Ushr's rule.
				rule(Ushr(X, K1), K1, {k1 in 1L..31L}) {
					And(X, C(0xFFFF_FFFFL and (-1L shl k1.toInt())))
				}
				// Only zero is unchanged by a left shift.  (-1 shl y is -2^y.)
				rule(X, Y, {x.type.isSubtypeOf(inclusive(0, 0))}) { X }
			}),

		/**
		 * Select the larger of the two [Int]s.
		 */
		Max(longMaxMethod::generateCall, Math::max, ::maxBound, {
			rule(X, X) { X }
			rule(K1, K2) { C(max(k1, k2)) }
			rule(X, Y, {x.high <= y.low}) { Y }
			rule(X, Y, {x.low >= y.high}) { X }
		}),

		/**
		 * Select the smaller of the two [Int]s.
		 */
		Min(longMinMethod::generateCall, Math::min, ::minBound, {
			rule(X, X) { X }
			rule(K1, K2) { C(min(k1, k2)) }
			rule(X, Y, {x.high <= y.low}) { X }
			rule(X, Y, {x.low >= y.high}) { Y }
		}),

		/**
		 * Select a single bit into either 0 or 1 as the output.  This is done
		 * as a shift and mask.  B must be non-negative, but may exceed 31, at
		 * which point the replicated sign bit of A is used.  Since A is
		 * sign-extended to a `long`, clamping B to 63 selects the same bit as
		 * clamping it to 31.
		 */
		SelectBit(
			jvmGenerator = {
				// :: a, b
				method.visitLdcInsn(63L)
				// :: a, b, 63
				longMinMethod.generateCall(this)
				// :: a, min(b,63)
				method.visitInsn(Opcodes.L2I)
				// :: a, (int) min(b,63)
				method.visitInsn(Opcodes.LUSHR)
				// :: a>>>min(b,63)
				method.visitInsn(Opcodes.LCONST_1)
				// :: a>>>min(b,63), 1
				method.visitInsn(Opcodes.LAND)
				// :: (a>>>min(b,63)) & 1
			},
			folder = { a, b -> (a ushr min(b, 63L).toInt()) and 1L },
			bounder = { a, b -> enumerationWith(set(zero, one)) },
			ruleCreator = {
				rule(X, C(0)) { And(X, C(1)) }
			})

		;

		/**
		 * Construct a [BitOperation] using a simple JVM opcode.
		 *
		 * @constructor
		 *   Create a new [BitOperation] enumeration value.
		 * @param jvmOpcode
		 *   A `long` opcode to emit for this logic operation, once the two
		 *   `long` values have been pushed.  It should have the effect of
		 *   removing them from the JVM operand stack and leaving a single
		 *   `long` as the result.
		 * @param folder
		 *   A function that folds this operation on two constants, matching
		 *   the [jvmOpcode].
		 * @param bounder
		 *   A function for computing a type bound on the result, given type
		 *   bounds for the two arguments.
		 * @param ruleCreator
		 *   A lambda that is able to produce rules, via [RulesSyntax], once all
		 *   the enum values have been created, since rules for an operation
		 *   refer to other operations, sometimes cyclically.
		 */
		private constructor(
			jvmOpcode: Int,
			folder: (Long, Long)->Long,
			bounder: (A_Type, A_Type)->A_Type,
			ruleCreator: RulesSyntax.()->Unit
		) : this(
			{ method.visitInsn(jvmOpcode) },
			folder,
			bounder,
			ruleCreator)

		/**
		 * The [List] of [Rule]s by which an [L2ControlFlowGraph] can be
		 * transformed.  This happens when an [L2_BIT_LOGIC_OP] is asked to
		 * optimize itself, visiting its [L2_BIT_LOGIC_OP.bitOperation] and
		 * pattern matching its [BitOperation.rules].
		 */
		val rules: List<Rule> by lazy {
			val syntax = RulesSyntax(this)
			syntax.ruleCreator()
			syntax.rules
		}

		/**
		 * A syntactic convenience within pattern and rule definitions, allowing
		 * the [BitOperation] itself to appear to be a function applied to two
		 * arguments to construct a [Pattern] that's a binary [BinaryOp].
		 */
		operator fun invoke(left: Pattern, right: Pattern): BinaryOp =
			BinaryOp(this, left, right)

		/**
		 * Assert that the inputs of an [L2_BIT_LOGIC_OP] using this operation
		 * meet the operation's preconditions on its inputs, according to the
		 * receiver, the [L2ValueManifest] where the instruction is being added.
		 * Operations with no such preconditions do nothing.
		 *
		 * @param input1
		 *   The first input.
		 * @param input2
		 *   The second input.
		 */
		open fun L2ValueManifest.assertInputsAreValid(
			input1: L2ReadIntOperand,
			input2: L2ReadIntOperand
		) = Unit

		/**
		 * Generate code (via the [callSiteHelper] that performs this operation,
		 * and ensures the boxed form of the result is available as the result
		 * of the call site.
		 *
		 * @param primitive
		 *   The [Primitive] being effectively invoked.
		 * @param arguments
		 *   The two boxed input arguments.
		 * @param argumentTypes
		 *   The [List] of [A_Type]s of the arguments.
		 * @param callSiteHelper
		 *   The [CallSiteHelper] that assists creation of the current method
		 *   call site.
		 * @param typeGuaranteeFunction
		 *   A lambda that produces an output type bound, given two input types.
		 * @param fallbackBody
		 *   How to generate the boxed form of this call along a path where the
		 *   inputs could not both be unboxed to values in the [range].
		 * @param range
		 *   The range of integers to unbox the inputs into.  This operation
		 *   must always produce a result in the range when given inputs in the
		 *   range, as bitwise and, or, and xor do.
		 * @return
		 *   Whether any code was output.  If no code was output, the caller is
		 *   responsible for producing a more general primitive invocation or
		 *   call.
		 */
		fun L2GeneratorInterface.generateBinaryIntOperation(
			primitive: Primitive,
			arguments: List<L2ReadBoxedOperand>,
			argumentTypes: List<A_Type>,
			callSiteHelper: CallSiteHelper,
			typeGuaranteeFunction: (List<A_Type>) -> A_Type,
			fallbackBody: L1Translator.() -> Unit,
			range: A_Type = i32
		): Boolean
		{
			val (a, b) = arguments
			val (aType, bType) = argumentTypes
			// If either of the argument types does not intersect with the
			// range, then fall back to the primitive invocation.
			if (aType.typeIntersection(range).isBottom
				|| bType.typeIntersection(range).isBottom)
			{
				return false
			}

			// Attempt to unbox the arguments.
			val fallback = createBasicBlock("fall back to boxed logic")
			try
			{
				val (intA, intB) = primitive.run {
					readAsTwoInts(
						a.semanticValue(),
						b.semanticValue(),
						fallback,
						range
					) ?: return false
				}
				if (currentlyReachable())
				{
					// The happy path is reachable.  In this region, the output
					// is guaranteed to be in the range.
					val semanticPrimitive = primitive.semanticInvocation(
						a.semanticValue(), b.semanticValue())
					val typeGuarantee = typeGuaranteeFunction(
						listOf(
							aType.typeIntersection(range),
							bType.typeIntersection(range))
					).typeIntersection(range)
					// See if we've already computed an equivalent value in
					// either the boxed or unboxed form.
					currentManifest.equivalentSemanticValue(
						semanticPrimitive
					)?.let {
						move(it, listOf(semanticPrimitive))
						currentManifest.updateRestriction(semanticPrimitive) {
							intersectionWithType(typeGuarantee)
						}
						callSiteHelper.useAnswer(
							readBoxed(semanticPrimitive),
							false)
						return true
					}
					if (currentManifest
						.hasLiveSemanticValue(semanticPrimitive, INTEGER_KIND))
					{
						move(semanticPrimitive, listOf(semanticPrimitive))
						currentManifest.updateRestriction(semanticPrimitive) {
							intersectionWithType(typeGuarantee)
						}
						callSiteHelper.useAnswer(
							readBoxed(semanticPrimitive),
							false)
						return true
					}
					when (val lower = typeGuarantee.lowerBound)
					{
						// It's a constant value.
						typeGuarantee.upperBound ->
						{
							+INTEGER_KIND.moveConstant(
								lower,
								setOf(semanticPrimitive, constant(lower)))
						}
						else ->
						{
							val tempWriter = intWrite(
								setOf(semanticPrimitive),
								restrictionForType(typeGuarantee))
							+L2_BIT_LOGIC_OP(
								this@BitOperation, intA, intB, tempWriter)
						}
					}
					// Even though we're just using the boxed value again, the
					// unboxed form is also still available in the manifest for
					// use by subsequent primitives, which might allow the
					// boxing instruction to evaporate.
					callSiteHelper.useAnswer(
						readBoxed(semanticPrimitive),
						// Bit logic can't endanger escaped locals.
						false)
				}
				return true
			}
			finally
			{
				if (fallback.currentlyReachable())
				{
					// The fallback block is reachable, so generate the slow
					// case within it.
					startBlock(fallback)
					callSiteHelper.translator.fallbackBody()
				}
			}
		}
	}

	companion object
	{
		/**
		 * Shift the 32-bit value in [a] rightward by [b] bit positions,
		 * treating it as unsigned, exactly as [Int.ushr] would.  Both
		 * arguments and the result are sign-extended ints.
		 *
		 * @param a
		 *   The value to shift, a sign-extended int.
		 * @param b
		 *   The shift amount.  Only its bottom five bits are used.
		 * @return
		 *   The shifted value, as a sign-extended int.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun ushr32(a: Long, b: Long): Long =
			(a.toInt() ushr b.toInt()).toLong()

		/** The [CheckedMethod] for [ushr32]. */
		val ushr32Method = staticMethod(::ushr32, long, long)

		/**
		 * Calculate a type bound for `a mod b`, given the types for a and b.
		 * A must be ≥ 0, and b must be > 0.  Both must be < 2^63.
		 */
		fun modBound(a: A_Type, b: A_Type): A_Type
		{
			val aLow = a.lowerBound.extractLong
			val aHigh = a.upperBound.extractLong
			val bLow = b.lowerBound.extractLong
			val bHigh = b.upperBound.extractLong
			assert(aLow >= 0)
			assert(bLow > 0)

			val lowQuo = aLow / bHigh
			val highQuo = aHigh / bLow
			return if (lowQuo == highQuo)
			{
				// The modulus doesn't wrap past the denominator, so we can say
				// it only spans the obvious limit cases.
				inclusive(aLow % bHigh, aHigh % bLow)
			}
			else
			{
				// The modulus wraps past the denominator (to 0), so we have to
				// say the modulus is between 0 and one less than the largest
				// denominator.
				inclusive(0L, bHigh - 1)
			}
		}

		/**
		 * Calculate a bound for `a ushr b`, given the types for a and b.
		 * Only the bottom 5 bits of b are used.  If the a is negative and b is
		 * zero, the result should be treated as negative.
		 */
		fun ushrBound(a: A_Type, b: A_Type): A_Type
		{
			val aLow = a.lowerBound.extractLong
			val aHigh = a.upperBound.extractLong
			var bLow = b.lowerBound.extractInt
			var bHigh = b.upperBound.extractInt

			if ((bLow and 31.inv()) != (bHigh and 31.inv()))
			{
				// b wraps past 31 and 0, so we should consider that whole range
				// as possible.
				bLow = 0
				bHigh = 31
			}
			else
			{
				bLow = bLow and 31
				bHigh = bHigh and 31
			}
			val boundaryValues = mutableSetOf<Int>()
			listOf(aLow, -1L, 0L, 1L, aHigh).forEach { aSampleLong ->
				val aSampleInt = aSampleLong.toInt()
				if (aSampleInt !in aLow..aHigh) return@forEach
				boundaryValues.add(aSampleInt ushr bLow)
				boundaryValues.add(aSampleInt ushr bHigh)
			}
			return inclusive(boundaryValues.min(), boundaryValues.max())
		}

		/**
		 * Calculate a bound for `max(a, b)`, given the types for a and b.
		 */
		fun maxBound(a: A_Type, b: A_Type): A_Type
		{
			val aLow = a.lowerBound.extractLong
			val aHigh = a.upperBound.extractLong
			val bLow = b.lowerBound.extractLong
			val bHigh = b.upperBound.extractLong
			return inclusive(max(aLow, bLow), max(aHigh, bHigh))
		}

		/**
		 * Calculate a bound for `min(a, b)`, given the types for a and b.
		 */
		fun minBound(a: A_Type, b: A_Type): A_Type
		{
			val aLow = a.lowerBound.extractLong
			val aHigh = a.upperBound.extractLong
			val bLow = b.lowerBound.extractLong
			val bHigh = b.upperBound.extractLong
			return inclusive(min(aLow, bLow), min(aHigh, bHigh))
		}
	}
	class Rule(
		val pattern: Pattern,
		val condition: RuleEvaluationContext.()->Boolean,
		val producer: RuleEvaluationContext.()->Pattern)

	sealed class Pattern
	{
		open fun subString(): String = toString()

		open val allVariables: List<Variable> get() = emptyList()

		open val allConstants: List<Constant> get() = emptyList()

		abstract class Constant(): Pattern()
		{
			override fun toString(): String = this::class.simpleName!!

			override val allConstants get() = listOf(this)

			object K1 : Constant()
			object K2 : Constant()
			object K3 : Constant()
			object K4 : Constant()
		}

		/** A *specific* constant value. */
		class C(val n: Long): Pattern()
		{
			override fun toString() = n.toString()
		}

		abstract class Variable(): Pattern()
		{
			override fun toString(): String = this::class.simpleName!!

			override val allVariables get() = listOf(this)

			object W : Variable()
			object X : Variable()
			object Y : Variable()
			object Z : Variable()
		}

		class BinaryOp(
			val operation: BitOperation,
			val arg1: Pattern,
			val arg2: Pattern
		): Pattern()
		{
			override fun subString(): String = "(${toString()})"

			override fun toString() =
				"${arg1.subString()} $operation ${arg2.subString()}"

			override val allVariables
				get() = arg1.allVariables + arg2.allVariables

			override val allConstants
				get() = arg1.allConstants + arg2.allConstants
		}
	}
}

/**
 * Assert that, according to the receiver, the [dividend] is non-negative and
 * the [divisor] is positive, as [L2_BIT_LOGIC_OP.BitOperation.Div] and
 * [L2_BIT_LOGIC_OP.BitOperation.Mod] require.
 *
 * @param dividend
 *   The dividend's [L2ReadIntOperand].
 * @param divisor
 *   The divisor's [L2ReadIntOperand].
 */
private fun L2ValueManifest.assertNonnegativeDividendAndPositiveDivisor(
	dividend: L2ReadIntOperand,
	divisor: L2ReadIntOperand)
{
	assert(dividend.restriction.containedByType(i63))
	{
		"Dividend might be negative: ${dividend.restriction}"
	}
	assert(divisor.restriction.containedByType(positiveI63))
	{
		"Divisor might not be positive: ${divisor.restriction}"
	}
}

/**
 * Given a `long` value and a `long` shift amount on the stack, emit the
 * [opcode] (a `long` shift).  The JVM uses only the bottom six bits of the
 * amount.
 *
 * @param opcode
 *   The `long` shift opcode, which takes an `int` shift amount.
 */
private fun JVMTranslator.shiftLong(opcode: Int)
{
	// :: value, amount
	method.visitInsn(Opcodes.L2I)
	// :: value, (int) amount
	method.visitInsn(opcode)
}
