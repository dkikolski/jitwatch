/*
 * Copyright (c) 2013, 2014 Chris Newland.
 * Licensed under https://github.com/AdoptOpenJDK/jitwatch/blob/master/LICENSE-BSD
 * Instructions: https://github.com/AdoptOpenJDK/jitwatch/wiki
 */
package com.chrisnewland.jitwatch.ui.triview;

import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.chrisnewland.jitwatch.model.IMetaMember;
import com.chrisnewland.jitwatch.util.ParseUtil;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;

public class Viewer extends VBox
{
	private ScrollPane scrollPane;
	private VBox vBoxRows;

	public static final String COLOUR_BLACK = "black";
	public static final String COLOUR_RED = "red";
	public static final String COLOUR_GREEN = "green";
	public static final String COLOUR_BLUE = "blue";

	private int scrollIndex = 0;
	private int lastScrollIndex = -1;
	private String originalSource;

	public Viewer()
	{
		vBoxRows = new VBox();

		vBoxRows.heightProperty().addListener(new ChangeListener<Number>()
		{
			@Override
			public void changed(ObservableValue<? extends Number> arg0, Number oldValue, Number newValue)
			{
				setScrollBar();
			}
		});

		scrollPane = new ScrollPane();
		scrollPane.setContent(vBoxRows);
		scrollPane.setStyle("-fx-background-color:white");

		scrollPane.prefHeightProperty().bind(heightProperty());

		getChildren().add(scrollPane);

		setUpContextMenu();
	}

	public void setContent(String source, boolean showLineNumbers)
	{
		if (source == null)
		{
			source = "Empty";
		}

		originalSource = source;

		source = source.replace("\t", "  "); // 2 spaces

		String[] lines = source.split("\n");

		int maxWidth = Integer.toString(lines.length).length();

		List<Text> textItems = new ArrayList<>();

		for (int i = 0; i < lines.length; i++)
		{
			String row = lines[i];

			if (showLineNumbers)
			{
				lines[i] = padLineNumber(i + 1, maxWidth) + "  " + row;
			}

			Text lineText = new Text(lines[i]);

			String style = "-fx-font-family: monospace; -fx-font-size:12px; -fx-fill: black";

			lineText.setStyle(style);

			textItems.add(lineText);
		}

		setContent(textItems);
	}

	public void setContent(List<Text> items)
	{
		vBoxRows.getChildren().clear();
		vBoxRows.getChildren().addAll(items);
	}

	private String padLineNumber(int number, int maxWidth)
	{
		int len = Integer.toString(number).length();

		StringBuilder builder = new StringBuilder();

		for (int i = len; i < maxWidth; i++)
		{
			builder.append(' ');
		}

		builder.append(number);

		return builder.toString();
	}

	private void setUpContextMenu()
	{
		final ContextMenu contextMenu = new ContextMenu();

		MenuItem menuItemCopyToClipboard = new MenuItem("Copy to Clipboard");

		contextMenu.getItems().add(menuItemCopyToClipboard);

		vBoxRows.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>()
		{
			@Override
			public void handle(MouseEvent e)
			{
				if (e.getButton() == MouseButton.SECONDARY)
				{
					contextMenu.show(vBoxRows, e.getScreenX(), e.getScreenY());
				}
			}
		});

		menuItemCopyToClipboard.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				final Clipboard clipboard = Clipboard.getSystemClipboard();
				final ClipboardContent content = new ClipboardContent();

				StringBuilder builder = new StringBuilder();

				ObservableList<Node> items = vBoxRows.getChildren();

				for (Node item : items)
				{
					String line = null;

					if (item instanceof Text)
					{
						Text text = (Text) item;

						line = text.getText();
					}
					else if (item instanceof Label)
					{
						Label label = (Label) item;

						line = label.getText();
					}

					if (line != null)
					{
						builder.append(line).append("\n");
					}
				}

				content.putString(builder.toString());

				clipboard.setContent(content);
			}
		});
	}
	
	//TODO new idea - branch prediction stats using bci bytecode index
	// and taken / not taken from Journal :)

	public void jumpTo(IMetaMember member)
	{
		scrollIndex = 0;

		int regexPos = findPosForRegex(member.getSignatureRegEx());

		if (regexPos == -1)
		{
			List<String> lines = Arrays.asList(originalSource.split("\n"));

			scrollIndex = ParseUtil.findBestLineMatchForMemberSignature(member, lines);
		}
		else
		{
			scrollIndex = regexPos;
		}

		highlightLine(scrollIndex);
	}

	// ugh! dirty hack for highlighting
	private void highlightLine(int pos)
	{
		System.out.println("highlightLine: " + pos);
		
		if (pos != 0)
		{
			if (lastScrollIndex != -1)
			{
				// revert to black Text
				Label label = (Label) vBoxRows.getChildren().get(lastScrollIndex);
				Text text = new Text(label.getText());
				text.setFill(Color.BLACK);
				vBoxRows.getChildren().set(lastScrollIndex, text);
			}

			// replace new selected Text with background coloured label
			Text text = (Text) vBoxRows.getChildren().get(pos);
			Label label = new Label(text.getText());
			label.prefWidthProperty().bind(vBoxRows.widthProperty());
			label.setStyle("-fx-background-color:red");
			vBoxRows.getChildren().set(pos, label);

			lastScrollIndex = pos;

			setScrollBar();
		}
	}

	private int findPosForRegex(String regex)
	{
		int result = -1;

		ObservableList<Node> items = vBoxRows.getChildren();

		Pattern pattern = Pattern.compile(regex);

		int index = 0;

		for (Node item : items)
		{
			String line = null;

			if (item instanceof Text)
			{
				Text text = (Text) item;

				line = text.getText();
			}
			else if (item instanceof Label)
			{
				Label label = (Label) item;

				line = label.getText();
			}

			if (line != null)
			{
				Matcher matcher = pattern.matcher(line);
				if (matcher.find())
				{
					result = index;
					break;
				}
			}

			index++;
		}

		return result;
	}

	private void setScrollBar()
	{
		System.out.println("setScrollBar:");

		double scrollPos = (double) scrollIndex / (double) vBoxRows.getChildren().size()
				* (scrollPane.getVmax() - scrollPane.getVmin());
		scrollPane.setVvalue(scrollPos);
	}
}