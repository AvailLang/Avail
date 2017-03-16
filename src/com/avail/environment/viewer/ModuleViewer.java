package com.avail.environment.viewer;

import com.avail.builder.ResolvedModuleName;
import com.avail.compiler.scanning.AvailScanner;
import com.avail.compiler.scanning.AvailScanner.BasicCommentPosition;
import com.avail.compiler.scanning.AvailScannerException;
import com.avail.compiler.scanning.AvailScannerResult;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.AvailObject;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.actions.BuildAction;
import com.avail.environment.tasks.ViewModuleTask;
import javafx.beans.NamedArg;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.RichTextChange;
import org.fxmisc.richtext.model.StyledText;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A {@code ModuleViewer} is a {@link Scene} used to open a source module for
 * viewing and editing.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class ModuleViewer
extends Scene
{
	/**
	 * An {@link AvailScannerResult} from scanning the file.
	 */
	private AvailScannerResult scannerResult;

	/**
	 * A reference to the workbench.
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
	private final Semaphore semaphore = new Semaphore(1);

	/**
	 * The {@link Consumer} run in the event of a {@link RichTextChange}.
	 */
	private final Consumer<RichTextChange<
			Collection<String>,
			StyledText<Collection<String>>,
			Collection<String>>>
	changeTracker = change ->
	{
		//We only want to scan one at a time, but we don't care about
		//losing opportunities to scan if a lot of typing is happening;
		//we just want to do our best to scan on the last change.
		if (semaphore.tryAcquire())
		{
			scanAndStyle();
			semaphore.release();
		}
	};

	/**
	 * The {@link ResolvedModuleName} for the module being viewed.
	 */
	private final ResolvedModuleName resolvedModuleName;

	/**
	 * The {@link CodeArea} where code is displayed.
	 */
	private final CodeArea codeArea;

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
	 * Close this {@link ModuleViewer}, throwing away any changes, and open a
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
			new ViewModuleTask(workbench, resolvedModuleName)
				.execute();
		}
	}

	/**
	 * Answer a new {@link ModuleViewer}.
	 *
	 * @param module
	 *        The {@link ResolvedModuleName} that represents the module to open.
	 * @return A {@code ModuleViewer}.
	 */
	public static ModuleViewer moduleViewer (
		final ResolvedModuleName module,
		final AvailWorkbench workbench)
	{
		final CodeArea codeArea = new CodeArea();
		codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
		codeArea.getStyle();

		final VirtualizedScrollPane vsp = new VirtualizedScrollPane<>(codeArea);
		VBox.setVgrow(vsp, Priority.ALWAYS);
		MenuBar menuBar = new MenuBar();
		VBox vbox = new VBox(menuBar, vsp);
		ModuleViewer viewer = new ModuleViewer(
			vbox, 1000, 600, workbench, module, codeArea);

		viewer.enableScanOnChange();

		// File menu - new, save, exit
		Menu fileMenu = new Menu("File");
//		MenuItem newMenuItem = new MenuItem("New");
		MenuItem reloadhMenuItem = new MenuItem("Reload");
		reloadhMenuItem.setOnAction(actionEvent -> viewer.reloadModule());
		MenuItem saveMenuItem = new MenuItem("Save");
		saveMenuItem.setOnAction(actionEvent -> viewer.writeFile());

		MenuItem saveAndBuildMenuItem = new MenuItem("Save & Build");
		saveAndBuildMenuItem.setOnAction(actionEvent -> viewer.saveAndBuild());

		fileMenu.getItems().addAll(
			saveMenuItem, saveAndBuildMenuItem, reloadhMenuItem);
		menuBar.getMenus().addAll(fileMenu);

		viewer.getStylesheets().add(ModuleViewer.class.getResource(
			"/workbench/module_viewer_styles.css").toExternalForm());
		viewer.readFile();

		return viewer;
	}

	/**
	 * Write the contents of the {@link ModuleViewer} to disk.
	 *
	 * <p>
	 * This operation blocks until it can acquire the {@link #semaphore}. This
	 * prevents styling during save.
	 * </p>
	 */
	private void writeFile ()
	{
		try
		{
			semaphore.acquireUninterruptibly();
			List<String> input = new ArrayList<>();
			input.add(codeArea.getText());
			File file = resolvedModuleName.sourceReference();
			file.canWrite();
			Files.write(
				file.toPath(),
				input,
				StandardCharsets.UTF_8);
			semaphore.release();
		}
		catch (IOException e)
		{
			//Do not much
		}
	}

	/**
	 * Read the {@link #resolvedModuleName} {@linkplain File file} from disk
	 * and {@linkplain #scanAndStyle() style} it.
	 */
	private void readFile ()
	{
		File sourceFile = resolvedModuleName.sourceReference();
		try
		{
			StringBuilder sb = new StringBuilder();
			List<String> lines =
				Files.lines(sourceFile.toPath()).collect(Collectors.toList());
			codeArea.clear();

			int size = lines.size();

			for (int i = 0; i < size - 1; i++)
			{
				sb.append(lines.get(i));
				sb.append('\n');
			}

			sb.append(lines.get(size - 1));

			codeArea.replaceText(0, 0, sb.toString());
		}
		catch (IOException e)
		{
			System.err.println("Failed to read file");
		}
	}

	/**
	 * Scan the text in the {@link #codeArea} and style the document
	 * appropriately.
	 */
	private void scanAndStyle ()
	{
		final String source = codeArea.getText();
		try
		{
			scannerResult = AvailScanner.scanString(
				source.toString(),
				resolvedModuleName.localName(),
				false);

			final List<A_Token> outputTokens = scannerResult.outputTokens();
			for (int i = 0; i < outputTokens.size(); i++)
			{
				final A_Token token = outputTokens.get(i);
				if (token.isLiteralToken())
				{
					//There has to be a better way to do this for numbers
					AvailObject availObject = token.literal();

					codeArea.setStyleClass(
						token.start() - 1,
						token.start() + token.string().tupleSize() - 1,
						availObject.isExtendedInteger() ? "number" : "literal");
				}
			}

			final List<A_Token> commentTokens = scannerResult.commentTokens();
			for (int i = 0; i < commentTokens.size(); i++)
			{
				final A_Token token = commentTokens.get(i);
				codeArea.setStyleClass(
					token.start() - 1,
					token.start() + token.string().tupleSize() - 1,
					"stacks-comment");

			}

			final List<BasicCommentPosition> positions =
				scannerResult.basicCommentPositions();

			for (int i = 0; i < positions.size(); i++)
			{
				final BasicCommentPosition position = positions.get(i);
				codeArea.setStyleClass(
					position.start(),
					position.start() + position.length(),
					"comment");
			}
		}
		catch (AvailScannerException e)
		{
			scannerResult = null;
		}
	}

	/**
	 * Construct a {@link ModuleViewer}.
	 *
	 * @param root
	 *        The root node of the {@link Scene} graph.
	 * @param width
	 *        The width of the {@code Scene}.
	 * @param height
	 *        The height of the {@code Scene}.
	 */
	private ModuleViewer (
		@NamedArg("root") final Parent root,
		@NamedArg("width") final double width,
		@NamedArg("height") final double height,
		final AvailWorkbench workbench,
		final ResolvedModuleName resolvedModuleName,
		final CodeArea codeArea)
	{
		super(root, width, height);
		this.resolvedModuleName = resolvedModuleName;
		this.codeArea = codeArea;
		this.workbench = workbench;
	}
}
