/*
 * ModuleEditor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC. All rights reserved.
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

package com.avail.environment.editor;

import com.avail.builder.ResolvedModuleName;
import com.avail.compiler.ExpectedToken;
import com.avail.compiler.scanning.AvailScanner;
import com.avail.compiler.scanning.AvailScanner.BasicCommentPosition;
import com.avail.compiler.scanning.AvailScannerException;
import com.avail.compiler.scanning.AvailScannerResult;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.actions.BuildAction;
import com.avail.environment.editor.fx.AvailArea;
import com.avail.environment.tasks.EditModuleTask;
import com.avail.utility.Pair;
import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.RichTextChange;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.richtext.model.StyledText;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.avail.environment.editor.ModuleEditorStyle.*;

/**
 * A {@code ModuleEditor} is a {@link Scene} used to open a source module for
 * viewing and editing.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public final class ModuleEditor
	extends Scene
{
	/**
	 * An {@link AvailScannerResult} from scanning the file.
	 */
	private AvailScannerResult scannerResult;

	/**
	 * A reference to the {@link AvailWorkbench}..
	 */
	private final AvailWorkbench workbench;

	/**
	 * The single-permit {@link Semaphore} used as the gateway for allowing the
	 * {@link #scanAndStyle()} of the document on a change.
	 *
	 * <p>
	 * If subsequent updates come in, we don't care to process style changes for
	 * all of them, we just want to try our best to only do the last one.
	 * </p>
	 */
	private final Semaphore stylingSemaphore = new Semaphore(1);

	/**
	 * The {@link ResolvedModuleName} for the module being viewed.
	 */
	private final ResolvedModuleName resolvedModuleName;

	/**
	 * The {@link AvailArea} where code is displayed.
	 */
	private final AvailArea codeArea;

	/**
	 * The {@link Consumer} run in the event of a {@link RichTextChange}.
	 */
	private final Consumer<RichTextChange<
		Collection<String>,
		StyledText<Collection<String>>,
		Collection<String>>>
		changeTracker =
		change ->
		{
			// We only want to scan one at a time, but we don't care about
			// losing opportunities to scan if a lot of typing is happening;
			// we just want to do our best to scan on the last change.
			if (stylingSemaphore.tryAcquire())
			{
				ForkJoinPool.commonPool().execute(() ->
				{
					scanAndStyle();
					stylingSemaphore.release();
				});
			}
		};

	/**
	 * Enable the {@link #changeTracker} on {@link RichTextChange text changes}.
	 */
	private void enableScanOnChange ()
	{
		codeArea.richChanges()
			.filter(ch -> !ch.getInserted().equals(ch.getRemoved()))
			.subscribe(changeTracker);
	}

	/**
	 * Save the module then initiate a build.
	 */
	private void saveAndBuild ()
	{
		writeFile();
		BuildAction.build(resolvedModuleName, workbench);
	}

	/**
	 * Close this {@link ModuleEditor}, throwing away any changes, and open a
	 * fresh copy from the file system.
	 */
	private void reloadModule ()
	{
		final JFrame frame = workbench.openedSourceModules
			.remove(resolvedModuleName);
		if (frame != null)
		{
			frame.setVisible(false);
			frame.dispose();
			new EditModuleTask(workbench, resolvedModuleName)
				.execute();
		}
	}

	/**
	 * Answer a new {@link ModuleEditor}.
	 *
	 * @param module
	 *        The {@link ResolvedModuleName} that represents the module to open.
	 * @param workbench
	 *        The {@link AvailWorkbench} that is opening the editor.
	 * @param frame
	 *        The {@link JFrame} that the editor is in.
	 * @return A {@code ModuleEditor}.
	 */
	public static ModuleEditor moduleViewer (
		final @Nonnull ResolvedModuleName module,
		final @Nonnull AvailWorkbench workbench,
		final @Nonnull JFrame frame)
	{
		final AvailArea availArea = new AvailArea(workbench);
		availArea.setParagraphGraphicFactory(
			LineNumberFactory.get(availArea, digits -> "%1$" + digits + "s"));
		availArea.getStyle();
		final VirtualizedScrollPane<AvailArea> vsp =
			new VirtualizedScrollPane<>(availArea);
		VBox.setVgrow(vsp, Priority.ALWAYS);
		final MenuBar menuBar = new MenuBar();
		final VBox vbox = new VBox(menuBar, vsp);
		final Rectangle dimensions = frame.getBounds();
		final ModuleEditor viewer = new ModuleEditor(
			vbox,
			dimensions.width,
			dimensions.height,
			workbench,
			module,
			availArea);

		viewer.enableScanOnChange();

		// File menu - save, exit
		final Menu fileMenu = new Menu("File");
		final MenuItem reloadMenuItem = new MenuItem("Reload");
		reloadMenuItem.setOnAction(actionEvent -> viewer.reloadModule());
		final MenuItem saveMenuItem = new MenuItem("Save");
		saveMenuItem.setOnAction(actionEvent -> viewer.writeFile());

		final MenuItem saveAndBuildMenuItem = new MenuItem("Save & Build");
		saveAndBuildMenuItem.setOnAction(actionEvent -> viewer.saveAndBuild());

		fileMenu.getItems().addAll(
			saveMenuItem, saveAndBuildMenuItem, reloadMenuItem);
		menuBar.getMenus().addAll(fileMenu);

		viewer.getStylesheets().add(editorStyleSheet);
		viewer.readFile();

		return viewer;
	}

	/**
	 * Write the content of the {@link ModuleEditor} to disk.
	 *
	 * <p>
	 * This operation blocks until it can acquire the {@link #stylingSemaphore}. This
	 * prevents styling during save.
	 * </p>
	 */
	private void writeFile ()
	{
		try
		{
			stylingSemaphore.acquireUninterruptibly();
			final List<String> input = new ArrayList<>();
			input.add(codeArea.getText());
			final File file = resolvedModuleName.sourceReference();
			assert file.canWrite();
			Files.write(
				file.toPath(),
				input,
				StandardCharsets.UTF_8);
			stylingSemaphore.release();
		}
		catch (final IOException e)
		{
			//Do not much
		}
	}

	/**
	 * Read the {@link #resolvedModuleName} {@linkplain File file} from disk
	 * and {@linkplain #scanAndStyle() style} it.
	 *
	 * <p>
	 * This wipes the undo history.
	 * </p>
	 */
	private void readFile ()
	{
		final File sourceFile = resolvedModuleName.sourceReference();
		try
		{
			final StringBuilder sb = new StringBuilder();
			final List<String> lines =
				Files.lines(sourceFile.toPath()).collect(Collectors.toList());
			codeArea.clear();

			final int size = lines.size();

			for (int i = 0; i < size - 1; i++)
			{
				sb.append(lines.get(i));
				sb.append('\n');
			}

			sb.append(lines.get(size - 1));

			codeArea.replaceText(0, 0, sb.toString());
			codeArea.getUndoManager().forgetHistory();
		}
		catch (final IOException e)
		{
			System.err.println("Failed to read file");
		}
	}

	/**
	 * A {@linkplain Map map} from Avail header keywords to the associated
	 * {@link ExpectedToken}s.
	 */
	private static final @Nonnull Map<A_String, ExpectedToken> headerKeywords;

	static
	{
		final ExpectedToken[] tokens =
			{
				ExpectedToken.MODULE,
				ExpectedToken.VERSIONS,
				ExpectedToken.PRAGMA,
				ExpectedToken.EXTENDS,
				ExpectedToken.USES,
				ExpectedToken.NAMES,
				ExpectedToken.ENTRIES,
				ExpectedToken.BODY,
				ExpectedToken.OPEN_PARENTHESIS,
				ExpectedToken.CLOSE_PARENTHESIS
			};
		final Map<A_String, ExpectedToken> map = new HashMap<>();
		for (final ExpectedToken t : tokens)
		{
			map.put(t.lexeme().makeShared(), t);
		}
		headerKeywords = map;
	}

	/**
	 * Style the specified {@linkplain A_Token} by giving it the provided
	 * style class.
	 *
	 * @param token
	 *        The token to style.
	 * @param style
	 *        The {@linkplain ModuleEditorStyle style}.
	 */
	private void styleToken (
		final A_Token token,
		final ModuleEditorStyle style)
	{
		codeArea.setStyleClass(
			token.start() - 1,
			token.start() + token.string().tupleSize() - 1,
			style.styleClass);
		resetView();
	}

	/**
	 * Asynchronously style the specified {@linkplain A_Token} by giving it the
	 * provided style class.
	 *
	 * @param token
	 *        The token to style.
	 * @param style
	 *        The {@linkplain ModuleEditorStyle style}.
	 * @param semaphore
	 *        The stylingSemaphore to signal when styling is complete.
	 */
	private void asyncStyleToken  (
		final A_Token token,
		final ModuleEditorStyle style,
		final Semaphore semaphore)
	{
		Platform.runLater(() ->
		{
			styleToken(token, style);
			semaphore.release();
		});
	}

	/**
	 * Asynchronously style the specified {@linkplain
	 * AvailScannerResult#outputTokens() output tokens}, signaling the provided
	 * {@linkplain Semaphore stylingSemaphore} when each styling is complete.
	 *
	 * @param outputTokens
	 *        The output tokens to style.
	 * @param tokenStyles
	 *        The general token styles, as a {@linkplain Map map} from tokens to
	 *        {@linkplain ModuleEditorStyle styles}.
	 * @param nameStyles
	 *        The name token styles, as a map from lexemes to styles.
	 * @param semaphore
	 *        The stylingSemaphore to {@linkplain Semaphore#release() signal} whenever
	 *        a styling operation completes.
	 */
	private void asyncStyleOutputTokens (
		final @Nonnull List<A_Token> outputTokens,
		final @Nonnull Map<A_Token, ModuleEditorStyle> tokenStyles,
		final @Nonnull Map<A_String, ModuleEditorStyle> nameStyles,
		final @Nonnull Semaphore semaphore)
	{
		// Override the general style for each ordinary token.
		outputTokens.parallelStream().forEach(token ->
		{
			final ModuleEditorStyle tokenStyle = tokenStyles.get(token);
			if (tokenStyle != null)
			{
				asyncStyleToken(token, tokenStyle, semaphore);
			}
			else
			{
				final ModuleEditorStyle nameStyle =
					nameStyles.get(token.string().makeShared());
				if (nameStyle != null)
				{
					asyncStyleToken(token, nameStyle, semaphore);
				}
				else if (token.isLiteralToken())
				{
					final AvailObject availObject = token.literal();
					asyncStyleToken(
						token,
						availObject.isExtendedInteger()
							? NUMBER
							: availObject.isString()
								? STRING
								: LITERAL,
						semaphore);
				}
				else
				{
					// No styling should be applied, but we must still release
					// the stylingSemaphore!
					semaphore.release();
				}
			}
		});
	}

	/**
	 * The {@linkplain Pattern pattern} that describes Stacks keywords.
	 */
	private static final @Nonnull Pattern stacksKeywordPattern =
		Pattern.compile("@\\w+");

	/**
	 * Asynchronously style the specified {@linkplain
	 * AvailScannerResult#commentTokens() comment tokens}, signaling the
	 * provided {@linkplain Semaphore stylingSemaphore} when each styling is complete.
	 *
	 * @param commentTokens
	 *        The output tokens to style.
	 * @param semaphore
	 *        The stylingSemaphore to {@linkplain Semaphore#release() signal} whenever
	 *        a styling operation completes.
	 */
	private void asyncStyleCommentTokens (
		final List<A_Token> commentTokens,
		final Semaphore semaphore)
	{
		commentTokens.parallelStream().forEach(token ->
		{
			final Matcher matcher = stacksKeywordPattern.matcher(
				token.string().asNativeString());
			final List<Pair<Integer, Integer>> ranges = new LinkedList<>();
			while (matcher.find())
			{
				ranges.add(new Pair<>(matcher.start(), matcher.end()));
			}
			Platform.runLater(() ->
			{
				styleToken(token, STACKS_COMMENT);
				ranges.forEach(range ->
					codeArea.setStyleClass(
						token.start() + range.first() - 1,
						token.start() + range.second() - 1,
						STACKS_KEYWORD.styleClass));
				codeArea.setEstimatedScrollY(scrollBefore);
				semaphore.release();
			});
			// TODO: [RAA] I had to disable this because the performance of
			// scanning Stacks comments was way too slow — multi-second
			// delays.
//			final StacksScanner stacksScanner = new StacksScanner(
//				token, resolvedModuleName.qualifiedName());
//			try
//			{
//				stacksScanner.scan();
//				styleToken(token, STACKS_COMMENT);
//				if (stacksScanner.sectionStartLocations.isEmpty())
//				{
//                // This comment is busted — style it appropriately.
//                styleToken(token, MALFORMED_STACKS_COMMENT);
//                continue;
//				}
//			}
//			catch (final @Nonnull StacksScannerException e)
//			{
//				// This comment is busted — style it appropriately.
//				styleToken(token, MALFORMED_STACKS_COMMENT);
//				continue;
//			}
//			for (final AbstractStacksToken t : stacksScanner.outputTokens)
//			{
//				if (t instanceof SectionKeywordStacksToken)
//				{
//					// TODO: [RAA] It appears that these tokens are using
//					// line number to record the position.
//					final int position =
//						t.lineNumber() - t.lexeme().length();
//					codeArea.setStyleClass(
//						position - 1,
//						t.lineNumber() - 1,
//						STACKS_KEYWORD.styleClass);
//				}
//				else if (t instanceof BracketedStacksToken)
//				{
//					final int position = t.position() - t.lexeme().length();
//					codeArea.setStyleClass(
//						position,
//						t.position() - 1,
//						STACKS_KEYWORD.styleClass);
//				}
//			}
		});
	}

	/**
	 * Asynchronously style the ordinary comments at the specified {@linkplain
	 * AvailScannerResult#basicCommentPositions() positions}, signaling the
	 * provided {@linkplain Semaphore stylingSemaphore} when each styling is complete.
	 *
	 * @param positions
	 *        The start positions of the ordinary comments to style.
	 * @param semaphore
	 *        The stylingSemaphore to {@linkplain Semaphore#release() signal} whenever
	 *        a styling operation completes.
	 */
	private void asyncStyleOrdinaryComments (
		final @Nonnull List<BasicCommentPosition> positions,
		final @Nonnull Semaphore semaphore)
	{
		positions.forEach(position ->
			Platform.runLater(() ->
			{
				codeArea.setStyleClass(
					position.start(),
					position.start() + position.length(),
					COMMENT.styleClass);
//				codeArea.setEstimatedScrollY(scrollBefore);
				semaphore.release();
			}));
	}

	Double scrollBefore = 0.0;

	void resetView ()
	{
		Platform.runLater(() -> codeArea.setEstimatedScrollY(scrollBefore));
	}

	/** Accumulate a sequence of styles as tokens are processed. */
	private class StyleSpansAccumulator
	{
		final StyleSpansBuilder spansBuilder = new StyleSpansBuilder(1000);

		int lastPosition = 0;

		void setToken (final A_Token token, final ModuleEditorStyle style)
		{
			final int tokenStart = token.start();
			// Probably not right with Java's crazy UTF-16 encoding.
			final int tokenEnd = tokenStart + token.string().tupleSize();
			if (tokenStart != lastPosition)
			{
				assert tokenStart > lastPosition;
				spansBuilder.add(
					new StyleSpan(
						GENERAL.styleClass, tokenStart - lastPosition));
				lastPosition = tokenStart;
			}
			spansBuilder.add(new StyleSpan(style, tokenEnd - tokenStart));
			lastPosition = tokenEnd;
		}
	}

	/**
	 * Scan the text in the {@link #codeArea} and style the document
	 * appropriately.
	 */
	private void scanAndStyle ()
	{
		final String source = codeArea.getText();
		scrollBefore = codeArea.getEstimatedScrollY();
		try
		{
			// Scan the source module to produce all tokens.
			scannerResult = AvailScanner.scanString(
				source,
				resolvedModuleName.localName(),
				false);

			// Tag each token of the header with the appropriate style.
			final List<A_Token> outputTokens = scannerResult.outputTokens();
			final int outputTokenCount = outputTokens.size();
			final Map<A_Token, ModuleEditorStyle> tokenStyles =	new HashMap<>();
			final Map<A_String, ModuleEditorStyle> nameStyles = new HashMap<>();
			ModuleEditorStyle activeStyle = null;
			for (final A_Token token : outputTokens)
			{
				final A_String lexeme = token.string().makeShared();
				final ExpectedToken expected = headerKeywords.get(lexeme);
				if (expected != null && token.tokenType() == TokenType.KEYWORD)
				{
					// This is a header keyword, so set its own style and
					// establish a new active style appropriate for its section.
					tokenStyles.put(token, HEADER_KEYWORD);
					activeStyle = styleFor(expected);
					// There are guaranteed not to be any header keywords after
					// "Body", so stop looking!
					if (expected == ExpectedToken.BODY)
					{
						assert activeStyle == null;
						break;
					}
				}
				else if (expected == ExpectedToken.OPEN_PARENTHESIS)
				{
					// Activate the subsection style.
					activeStyle = activeStyle.subsectionStyleClass;
				}
				else if (expected == ExpectedToken.CLOSE_PARENTHESIS)
				{
					// Deactivate the subsection style.
					activeStyle = activeStyle.supersectionStyleClass;
				}
				else if (token.isLiteralToken())
				{
					// This is a literal token, so give it the active style for
					// its section.
					if (activeStyle != null)
					{
						tokenStyles.put(token, activeStyle);
						if (activeStyle.denotesName())
						{
							// Since this is a name visible in the module
							// header, associate the active style with the
							// lexeme, not just the token. This will give the
							// lexeme the same appearance in the body as in the
							// header. There may be some false positives, but
							// relatively few.
							nameStyles.put(lexeme, activeStyle);
						}
					}
				}
			}

			// Use a semaphore to synchronize the requesting thread with the
			// UI thread. Give the semaphore a deficit equal to the number of
			// regions to style in parallel; only when every work unit has
			// executed and released the semaphore is the requesting thread
			// eligible to return control to its caller.
			final List<A_Token> commentTokens = scannerResult.commentTokens();
			final int commentTokenCount = commentTokens.size();
			final List<BasicCommentPosition> positions =
				scannerResult.basicCommentPositions();
			final int positionCount = positions.size();
			final Semaphore semaphore = new Semaphore(
				-outputTokenCount - positionCount - commentTokenCount);
			Platform.runLater(() ->
			{
				// Apply the general style. Because the UI thread maintains a
				// queue of pending rendezvouses, even though this is
				// asynchronous to the requesting thread it is still guaranteed
				// to happen before any of the other styling requests. This is
				// essential, because this style must be applied first in order
				// to be properly overridden for select regions.
				codeArea.setStyleClass(
					0,
					source.length(),
					GENERAL.styleClass);

				semaphore.release();
			});
			asyncStyleOutputTokens(
				outputTokens, tokenStyles, nameStyles, semaphore);
			asyncStyleCommentTokens(commentTokens, semaphore);
			asyncStyleOrdinaryComments(positions, semaphore);
			semaphore.acquire();
//			codeArea.requestFollowCaret();
		}
		catch (final @Nonnull AvailScannerException|InterruptedException e)
		{
			scannerResult = null;
		}
	}

	/**
	 * Construct a {@link ModuleEditor}.
	 *
	 * @param root
	 *        The root node of the {@link Scene} graph.
	 * @param width
	 *        The width of the {@code Scene}.
	 * @param height
	 *        The height of the {@code Scene}.
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 * @param resolvedModuleName
	 *        The {@link ResolvedModuleName} of the module displayed.
	 * @param codeArea
	 *        The {@link AvailArea} where code is displayed/edited.
	 */
	private ModuleEditor (
		@NamedArg("root") final Parent root,
		@NamedArg("width") final double width,
		@NamedArg("height") final double height,
		final AvailWorkbench workbench,
		final ResolvedModuleName resolvedModuleName,
		final AvailArea codeArea)
	{
		super(root, width, height);
		this.resolvedModuleName = resolvedModuleName;
		this.codeArea = codeArea;
		this.workbench = workbench;
	}
}
