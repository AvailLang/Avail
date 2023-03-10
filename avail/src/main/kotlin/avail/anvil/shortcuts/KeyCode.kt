/*
 * KeyCode.kt
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

package avail.anvil.shortcuts

import java.awt.event.KeyEvent

/**
 * The enumeration of virtual key codes in [KeyEvent] that are used to represent
 * key strokes.
 *
 * @author Richard Arriaga
 *
 * @property code
 *   The numeric key code that represents the key.
 * @property lookupKey
 *   The key used to lookup this [KeyCode].
 * @property displayRepresentation
 *   The platform-specific String that represents this key. Defaults to
 *   [lookupKey].
 */
enum class KeyCode constructor(
	val code: Int,
	val lookupKey: String,
	val displayRepresentation: String = lookupKey,
	vararg mappedChars: Char)
{
	/** The [Enter][KeyEvent.VK_ENTER] key. */
	VK_ENTER(KeyEvent.VK_ENTER, "Enter", "⏎"),

	/** The [Backspace][KeyEvent.VK_BACK_SPACE] key. */
	VK_BACK_SPACE(KeyEvent.VK_BACK_SPACE, "Backspace", "⌫"),

	/** The [Tab][KeyEvent.VK_TAB] key. */
	VK_TAB(KeyEvent.VK_TAB, "Tab", "⇥"),

	/** The [Cancel][KeyEvent.VK_CANCEL] key. */
	VK_CANCEL(KeyEvent.VK_CANCEL, "Cancel"),

	/** The [Enter][KeyEvent.VK_CLEAR] key. */
	VK_CLEAR(KeyEvent.VK_CLEAR, "Clear"),

	/** The [Virtual Shift][KeyEvent.VK_SHIFT] key. */
	VK_SHIFT(KeyEvent.VK_SHIFT, "Virtual Shift"),

	/** The [Virtual Control][KeyEvent.VK_CONTROL] key. */
	VK_CONTROL(KeyEvent.VK_CONTROL, "Virtual Control"),

	/** The [Virtual Alt][KeyEvent.VK_ALT] key. */
	VK_ALT(KeyEvent.VK_ALT, "Virtual Alt"),

	/** The [Pause][KeyEvent.VK_PAUSE] key. */
	VK_PAUSE(KeyEvent.VK_PAUSE, "Pause"),

	/** The [Caps Lock][KeyEvent.VK_CAPS_LOCK] key. */
	VK_CAPS_LOCK(KeyEvent.VK_CAPS_LOCK, "Caps Lock"),

	/** The [Enter][KeyEvent.VK_ESCAPE] key. */
	VK_ESCAPE(KeyEvent.VK_ESCAPE, "Escape"),

	/** The [Space][KeyEvent.VK_SPACE] key. */
	VK_SPACE(KeyEvent.VK_SPACE, "Space"),

	/** The [Page Up][KeyEvent.VK_PAGE_UP] key. */
	VK_PAGE_UP(KeyEvent.VK_PAGE_UP, "Page Up"),

	/** The [Page Down][KeyEvent.VK_PAGE_DOWN] key. */
	VK_PAGE_DOWN(KeyEvent.VK_PAGE_DOWN, "Page Down"),

	/** The [End][KeyEvent.VK_END] key. */
	VK_END(KeyEvent.VK_END, "End"),

	/** The [Home][KeyEvent.VK_HOME] key. */
	VK_HOME(KeyEvent.VK_HOME, "Home"),

	/** The [Left Arrow][KeyEvent.VK_BACK_SPACE] key. */
	VK_LEFT(KeyEvent.VK_LEFT, "Left Arrow", "←"),

	/** The [Up Arrow][KeyEvent.VK_UP] key. */
	VK_UP(KeyEvent.VK_UP, "Up Arrow", "↑"),

	/** The [Right Arrow][KeyEvent.VK_RIGHT] key. */
	VK_RIGHT(KeyEvent.VK_RIGHT, "Right Arrow", "→"),

	/** The [Down Arrow][KeyEvent.VK_DOWN] key. */
	VK_DOWN(KeyEvent.VK_DOWN, "Down Arrow", "↓"),

	/** The [Comma][KeyEvent.VK_COMMA] key. */
	VK_COMMA(KeyEvent.VK_COMMA, ",", ",", '<'),

	/** The [Minus][KeyEvent.VK_MINUS] key. */
	VK_MINUS(KeyEvent.VK_MINUS, "-", "-", '_'),

	/** The [Period][KeyEvent.VK_PERIOD] key. */
	VK_PERIOD(KeyEvent.VK_PERIOD, ".", ".", '>'),

	/** The [Forward Slash][KeyEvent.VK_SLASH] key. */
	VK_SLASH(KeyEvent.VK_SLASH, "/", "/", '?'),

	/** The [0][KeyEvent.VK_0] key. */
	VK_0(KeyEvent.VK_0, "0", "0", ')'),

	/** The [1][KeyEvent.VK_1] key. */
	VK_1(KeyEvent.VK_1, "1", "1", '!'),

	/** The [2][KeyEvent.VK_2] key. */
	VK_2(KeyEvent.VK_2, "2", "2", '@'),

	/** The [3][KeyEvent.VK_3] key. */
	VK_3(KeyEvent.VK_3, "3", "3", '#'),

	/** The [4][KeyEvent.VK_4] key. */
	VK_4(KeyEvent.VK_4, "4", "4", '$'),

	/** The [5][KeyEvent.VK_5] key. */
	VK_5(KeyEvent.VK_5, "5", "5", '%'),

	/** The [6][KeyEvent.VK_6] key. */
	VK_6(KeyEvent.VK_6, "6", "6", '^'),

	/** The [7][KeyEvent.VK_7] key. */
	VK_7(KeyEvent.VK_7, "7", "7", '&'),

	/** The [8][KeyEvent.VK_8] key. */
	VK_8(KeyEvent.VK_8, "8", "8 '*"),

	/** The [9][KeyEvent.VK_9] key. */
	VK_9(KeyEvent.VK_9, "9", "9", '('),

	/** The [Semicolon][KeyEvent.VK_SEMICOLON] key. */
	VK_SEMICOLON(KeyEvent.VK_SEMICOLON, ";", ";", ':'),

	/** The [Equals][KeyEvent.VK_EQUALS] key. */
	VK_EQUALS(KeyEvent.VK_EQUALS, "=", "=", '+'),

	/** The [A][KeyEvent.VK_A] key. */
	VK_A(KeyEvent.VK_A, "A", "A", 'a'),

	/** The [B][KeyEvent.VK_B] key. */
	VK_B(KeyEvent.VK_B, "B", "B", 'b'),

	/** The [C][KeyEvent.VK_C] key. */
	VK_C(KeyEvent.VK_C, "C", "C", 'c'),

	/** The [D][KeyEvent.VK_D] key. */
	VK_D(KeyEvent.VK_D, "D", "D", 'd'),

	/** The [E][KeyEvent.VK_E] key. */
	VK_E(KeyEvent.VK_E, "E", "E", 'e'),

	/** The [F][KeyEvent.VK_F] key. */
	VK_F(KeyEvent.VK_F, "F", "F", 'f'),

	/** The [G][KeyEvent.VK_G] key. */
	VK_G(KeyEvent.VK_G, "G", "G", 'g'),

	/** The [H][KeyEvent.VK_H] key. */
	VK_H(KeyEvent.VK_H, "H", "H", 'h'),

	/** The [I][KeyEvent.VK_I] key. */
	VK_I(KeyEvent.VK_I, "I", "I", 'i'),

	/** The [J][KeyEvent.VK_J] key. */
	VK_J(KeyEvent.VK_J, "J", "J", 'j'),

	/** The [K][KeyEvent.VK_K] key. */
	VK_K(KeyEvent.VK_K, "K", "K", 'k'),

	/** The [L][KeyEvent.VK_L] key. */
	VK_L(KeyEvent.VK_L, "L", "L", 'l'),

	/** The [M][KeyEvent.VK_BACK_SPACE] key. */
	VK_M(KeyEvent.VK_M, "M", "M", 'm'),

	/** The [N][KeyEvent.VK_N] key. */
	VK_N(KeyEvent.VK_N, "N", "N", 'n'),

	/** The [O][KeyEvent.VK_O] key. */
	VK_O(KeyEvent.VK_O, "O", "O", 'o'),

	/** The [P][KeyEvent.VK_P] key. */
	VK_P(KeyEvent.VK_P, "P", "P", 'p'),

	/** The [Q][KeyEvent.VK_Q] key. */
	VK_Q(KeyEvent.VK_Q, "Q", "Q", 'q'),

	/** The [R][KeyEvent.VK_R] key. */
	VK_R(KeyEvent.VK_R, "R", "R", 'r'),

	/** The [S][KeyEvent.VK_S] key. */
	VK_S(KeyEvent.VK_S, "S", "S", 's'),

	/** The [T][KeyEvent.VK_T] key. */
	VK_T(KeyEvent.VK_T, "T", "T", 't'),

	/** The [U][KeyEvent.VK_U] key. */
	VK_U(KeyEvent.VK_U, "U", "U", 'u'),

	/** The [V][KeyEvent.VK_ENTER] key. */
	VK_V(KeyEvent.VK_V, "V", "V", 'v'),

	/** The [W][KeyEvent.VK_W] key. */
	VK_W(KeyEvent.VK_W, "W", "W", 'w'),

	/** The [X][KeyEvent.VK_X] key. */
	VK_X(KeyEvent.VK_X, "X", "X", 'x'),

	/** The [Y][KeyEvent.VK_Y] key. */
	VK_Y(KeyEvent.VK_Y, "Y", "Y", 'y'),

	/** The [Z][KeyEvent.VK_ENTER] key. */
	VK_Z(KeyEvent.VK_Z, "Z", "Z", 'z'),

	/** The [Open Square Bracket][KeyEvent.VK_OPEN_BRACKET] key. */
	VK_OPEN_BRACKET(KeyEvent.VK_OPEN_BRACKET, "[", "[", '{', '“'),

	/** The [BackSlash][KeyEvent.VK_BACK_SLASH] key. */
	VK_BACK_SLASH(KeyEvent.VK_BACK_SLASH, "\\", "\\", '|', '«'),

	/** The [Close Square Bracket][KeyEvent.VK_CLOSE_BRACKET] key. */
	VK_CLOSE_BRACKET(KeyEvent.VK_CLOSE_BRACKET, "]", "]", '}', '‘'),

	/** The [Numpad 0][KeyEvent.VK_NUMPAD0] key. */
	VK_NUMPAD0(KeyEvent.VK_NUMPAD0, "Num 0"),

	/** The [Numpad 1][KeyEvent.VK_NUMPAD1] key. */
	VK_NUMPAD1(KeyEvent.VK_NUMPAD1, "Num 1"),

	/** The [Numpad 2][KeyEvent.VK_NUMPAD2] key. */
	VK_NUMPAD2(KeyEvent.VK_NUMPAD2, "Num 2"),

	/** The [Numpad 3][KeyEvent.VK_NUMPAD3] key. */
	VK_NUMPAD3(KeyEvent.VK_NUMPAD3, "Num 3"),

	/** The [Numpad 4][KeyEvent.VK_NUMPAD4] key. */
	VK_NUMPAD4(KeyEvent.VK_NUMPAD4, "Num 4"),

	/** The [Numpad 5][KeyEvent.VK_NUMPAD5] key. */
	VK_NUMPAD5(KeyEvent.VK_NUMPAD5, "Num 5"),

	/** The [Numpad 6][KeyEvent.VK_NUMPAD6] key. */
	VK_NUMPAD6(KeyEvent.VK_NUMPAD6, "Num 6"),

	/** The [Numpad 7][KeyEvent.VK_NUMPAD7] key. */
	VK_NUMPAD7(KeyEvent.VK_NUMPAD7, "Num 7"),

	/** The [Numpad 8][KeyEvent.VK_NUMPAD8] key. */
	VK_NUMPAD8(KeyEvent.VK_NUMPAD8, "Num 8"),

	/** The [Numpad 9][KeyEvent.VK_NUMPAD9] key. */
	VK_NUMPAD9(KeyEvent.VK_NUMPAD9, "Num 9"),

	/** The [Numpad Multiply][KeyEvent.VK_MULTIPLY] key. */
	VK_MULTIPLY(KeyEvent.VK_MULTIPLY, "Num *"),

	/** The [Numpad Add][KeyEvent.VK_ADD] key. */
	VK_ADD(KeyEvent.VK_ADD, "Num +"),

	/** The [Numpad Subract][KeyEvent.VK_SUBTRACT] key. */
	VK_SUBTRACT(KeyEvent.VK_SUBTRACT, "Num -"),

	/** The [Numpad Decimal][KeyEvent.VK_DECIMAL] key. */
	VK_DECIMAL(KeyEvent.VK_DECIMAL, "Num ."),

	/** The [Numpad Divide][KeyEvent.VK_DIVIDE] key. */
	VK_DIVIDE(KeyEvent.VK_DIVIDE, "Num /"),

	/** The [Delete][KeyEvent.VK_DELETE] key. */
	VK_DELETE(KeyEvent.VK_DELETE, "DEL"),

	/** The [Num Lock][KeyEvent.VK_NUM_LOCK] key. */
	VK_NUM_LOCK(KeyEvent.VK_NUM_LOCK, "Num Lock"),

	/** The [Scroll Lock][KeyEvent.VK_SCROLL_LOCK] key. */
	VK_SCROLL_LOCK(KeyEvent.VK_SCROLL_LOCK, "Scroll Lock"),

	/** The [F1][KeyEvent.VK_F1] key. */
	VK_F1(KeyEvent.VK_F1, "F1"),

	/** The [F2][KeyEvent.VK_F2] key. */
	VK_F2(KeyEvent.VK_F2, "F2"),

	/** The [F3][KeyEvent.VK_F3] key. */
	VK_F3(KeyEvent.VK_F3, "F3"),

	/** The [F4][KeyEvent.VK_F4] key. */
	VK_F4(KeyEvent.VK_F4, "F4"),

	/** The [F5][KeyEvent.VK_F5] key. */
	VK_F5(KeyEvent.VK_F5, "F5"),

	/** The [F6][KeyEvent.VK_F6] key. */
	VK_F6(KeyEvent.VK_F6, "F6"),

	/** The [F7][KeyEvent.VK_F7] key. */
	VK_F7(KeyEvent.VK_F7, "F7"),

	/** The [F8][KeyEvent.VK_F8] key. */
	VK_F8(KeyEvent.VK_F8, "F8"),

	/** The [F9][KeyEvent.VK_F9] key. */
	VK_F9(KeyEvent.VK_F9, "F9"),

	/** The [F10][KeyEvent.VK_F10] key. */
	VK_F10(KeyEvent.VK_F10, "F10"),

	/** The [F11][KeyEvent.VK_F11] key. */
	VK_F11(KeyEvent.VK_F11, "F11"),

	/** The [F12][KeyEvent.VK_F12] key. */
	VK_F12(KeyEvent.VK_F12, "F12"),

	/** The [F13][KeyEvent.VK_F13] key. */
	VK_F13(KeyEvent.VK_F13, "F13"),

	/** The [F14][KeyEvent.VK_F14] key. */
	VK_F14(KeyEvent.VK_F14, "F14"),

	/** The [F15][KeyEvent.VK_F15] key. */
	VK_F15(KeyEvent.VK_F15, "F15"),

	/** The [F16][KeyEvent.VK_F16] key. */
	VK_F16(KeyEvent.VK_F16, "F16"),

	/** The [F7][KeyEvent.VK_F17] key. */
	VK_F17(KeyEvent.VK_F17, "F17"),

	/** The [F18][KeyEvent.VK_F18] key. */
	VK_F18(KeyEvent.VK_F18, "F18"),

	/** The [F9][KeyEvent.VK_F19] key. */
	VK_F19(KeyEvent.VK_F19, "F19"),

	/** The [F20][KeyEvent.VK_F20] key. */
	VK_F20(KeyEvent.VK_F20, "F20"),

	/** The [F21][KeyEvent.VK_F21] key. */
	VK_F21(KeyEvent.VK_F21, "F21"),

	/** The [F22][KeyEvent.VK_F22] key. */
	VK_F22(KeyEvent.VK_F22, "F22"),

	/** The [F23][KeyEvent.VK_F23] key. */
	VK_F23(KeyEvent.VK_F23, "F23"),

	/** The [F24][KeyEvent.VK_F24] key. */
	VK_F24(KeyEvent.VK_F24, "F24"),

	/** The [Print Screen][KeyEvent.VK_PRINTSCREEN] key. */
	VK_PRINTSCREEN(KeyEvent.VK_PRINTSCREEN, "Print Screen"),

	/** The [Insert][KeyEvent.VK_INSERT] key. */
	VK_INSERT(KeyEvent.VK_INSERT, "Insert"),

	/** The [Help][KeyEvent.VK_HELP] key. */
	VK_HELP(KeyEvent.VK_HELP, "Help"),

	/** The [Virtual Meta][KeyEvent.VK_META] key. */
	VK_META(KeyEvent.VK_META, "Virtual Meta"),

	/** The [Backtick][KeyEvent.VK_BACK_QUOTE] key. */
	VK_BACK_QUOTE(KeyEvent.VK_BACK_QUOTE, "`", "`", '~'),

	/** The [][KeyEvent.VK_QUOTE] key. */
	VK_QUOTE(KeyEvent.VK_QUOTE, "'", "'", '"'),

	/** The [Numpad Up][KeyEvent.VK_KP_UP] key. */
	VK_KP_UP(KeyEvent.VK_KP_UP, "Num Up"),

	/** The [Numpad Down][KeyEvent.VK_KP_DOWN] key. */
	VK_KP_DOWN(KeyEvent.VK_KP_DOWN, "Num Down"),

	/** The [Numpad Left][KeyEvent.VK_KP_LEFT] key. */
	VK_KP_LEFT(KeyEvent.VK_KP_LEFT, "Num Left"),

	/** The [Numpad Right][KeyEvent.VK_KP_RIGHT] key. */
	VK_KP_RIGHT(KeyEvent.VK_KP_RIGHT, "Num Right");

	/**
	 * The set of characters that may also be mapped to this [KeyCode] on a
	 * keyboard.
	 */
	val overLapCharacters = mutableSetOf<Char>().apply {
		if (lookupKey.length == 1) add(lookupKey[0])
		addAll(mappedChars.toList())
	}

	/**
	 * Answer the [Key] represented by this [KeyCode] and the provided
	 * [ModifierKey]s.
	 *
	 * @param modifiers
	 *   The [ModifierKey]s for the [Key.modifiers].
	 * @return
	 *   The resulting [Key].
	 */
	fun with(vararg modifiers: ModifierKey) = Key(this, modifiers.toSet())

	companion object
	{
		/**
		 * The immutable list of [KeyCode]s. This is used in lieu of [values]
		 * as every time [values] is called it creates a copy of the backing
		 * array of [KeyCode]s; this is done so that copy is only made once.
		 */
		private val keyCodes = values().toList()

		/**
		 * Answer the [KeyCode] that matches the provided
		 * [KeyCode.lookupKey].
		 *
		 * @param lookupKey
		 *   The String lookup key to retrieve.
		 * @return
		 *   The matching [KeyCode] of `null` if not found.
		 */
		fun lookup (lookupKey: String): KeyCode? =
			keyCodes.firstOrNull { lookupKey == it.lookupKey }

		/**
		 * Answer the [KeyCode] that matches the provided Char in the
		 * [KeyCode.overLapCharacters].
		 *
		 * @param char
		 *   The char in the overLapCharacters to retrieve.
		 * @return
		 *   The matching [KeyCode] of `null` if not found.
		 */
		fun lookup (char: Char): KeyCode? =
			keyCodes.firstOrNull { it.overLapCharacters.contains(char) }

		/**
		 * Answer the [KeyCode] that matches the provided [code].
		 *
		 * @param code
		 *   The key code to retrieve.
		 * @return
		 *   The matching [KeyCode] of `null` if not found.
		 */
		fun lookupByCode (code: Int): KeyCode? =
			keyCodes.firstOrNull { code == it.code }

		/**
		 * The [KeyCode.lookupKey]s of all the valid [KeyCode]s.
		 */
		val validLookups: String get() =
			keyCodes.joinToString(", ") { it.lookupKey }
	}
}
