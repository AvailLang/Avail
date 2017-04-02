/**
 * Option.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.tools.options;

import java.util.LinkedHashSet;
import com.avail.utility.evaluation.*;

/**
 * An {@code Option} comprises an {@linkplain Enum enumerated type} which
 * defines the domain of the option, the keywords which parsers may use to
 * identify the option, an end-user friendly description of the option, and an
 * {@linkplain Continuation2 action} that should be performed each time that the
 * option is set.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @param <OptionKeyType> The type of the option.
 */
public interface Option<OptionKeyType extends Enum<OptionKeyType>>
{
	/**
	 * Answer the option key, a member of the {@linkplain Enum enumeration}
	 * which defines this option space.
	 *
	 * @return The option key.
	 */
	public OptionKeyType key ();

	/**
	 * Answer the {@linkplain LinkedHashSet set} of keywords that indicate this
	 * {@linkplain GenericOption option}.
	 *
	 * @return A {@linkplain LinkedHashSet set} of keywords.
	 */
	public LinkedHashSet<String> keywords ();

	/**
	 * Answer an end-user comprehensible description of the {@linkplain
	 * GenericOption option}.
	 *
	 * @return A description of the {@linkplain GenericOption option}.
	 */
	public String description ();

	/**
	 * Answer the {@linkplain Continuation2 action} that should be performed
	 * upon setting of this {@linkplain GenericOption option}.
	 *
	 * @return An action that accepts an option keyword and its associated
	 *         value.
	 */
	public Continuation2<String, String> action ();
}
