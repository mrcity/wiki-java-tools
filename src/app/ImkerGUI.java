package app;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Dialog.ModalityType;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import wiki.Wiki;

class State {
	protected static final int PRE_INIT = 11;
	protected static final int PRE_DOWNLOAD = 12;
	protected static final int TERMINATED = 13;

	private int state;

	protected State(int state) {
		this.setState(state);
	}

	protected int getState() {
		return state;
	}

	protected void setState(int state) {
		this.state = state;
	}
}

public class ImkerGUI extends ImkerBase {
	private static File inputFile = null;
	private static String inputPage = null;
	private static String inputCategory = null;
	private static final JFrame FRAME = new JFrame(PROGRAM_NAME);
	private static final JButton MAIN_BUTTON = new JButton("");
	private static final JRadioButton FILE_BUTTON = new JRadioButton(
			msgs.getString("Text_From_File"));
	private static final JRadioButton PAGE_BUTTON = new JRadioButton(
			msgs.getString("Text_Page"));
	private static final JRadioButton CATEGORY_BUTTON = new JRadioButton(
			msgs.getString("Text_Category"));
	private static final JTextField STATUS_TEXT_FIELD = new JTextField(45);
	private static State state = new State(State.PRE_INIT);
	private static boolean hasSource = false;
	private static boolean hasTarget = false;

	private static final int GAP = 12;

	protected static void handleAction() {
		switch (state.getState()) {
		case State.PRE_INIT:
			initialize();
			break;
		case State.PRE_DOWNLOAD:
			download();
			// TODO verifyCheckSum();
			break;
		case State.TERMINATED:
			preInit(true);
			break;
		// default: // should never happen
		}
	}

	private static void downloadLoop() throws FileNotFoundException,
			IOException {
		for (int i = 0; i < fileNames.length; i++) {
			String fileName = fileNames[i].substring("File:".length());
			// progressBar.setValue(i);
			STATUS_TEXT_FIELD.setText("(" + (i + 1) + "/" + fileNames.length
					+ "): " + fileName);
			File outputFile = new File(outputFolder.getPath() + File.separator
					+ fileName);
			if (outputFile.exists()) {
				STATUS_TEXT_FIELD.setText(STATUS_TEXT_FIELD.getText() + " ... "
						+ msgs.getString("Status_File_Exists"));
				continue;
			}
			boolean downloaded = wiki.getImage(fileName, outputFile);
			if (downloaded == false) {
				STATUS_TEXT_FIELD.setText(STATUS_TEXT_FIELD.getText() + " ... "
						+ msgs.getString("Status_File_Not_Found"));
				continue;
			}
			STATUS_TEXT_FIELD.setText(STATUS_TEXT_FIELD.getText() + " ... "
					+ msgs.getString("Status_File_Saved"));
		}
	}

	private static void initialize() {
		if (!verifyInput()) {
			STATUS_TEXT_FIELD.setText(msgs.getString("Error_Invalid_Input"));
			MAIN_BUTTON.setText(msgs.getString("Button_Reset"));
			state.setState(State.TERMINATED);
			return;
		}
		try {
			STATUS_TEXT_FIELD.setText(msgs.getString("Status_Wait_For_List"));
			tracker(msgs.getString("Status_Crawling"),
					msgs.getString("Hint_Crawling"),
					msgs.getString("Text_Exit"), new SwingWorker<Void, Void>() {

						@Override
						protected Void doInBackground()
								throws FileNotFoundException, IOException {
							getFileNames();
							return null;
						}
					});
		} catch (Exception e) {
			terminate(e);
			return;
		}

		if (fileNames.length == 0) {
			JOptionPane
					.showMessageDialog(FRAME, msgs.getString("Error_No_Files"),
							msgs.getString("Text_Warning"),
							JOptionPane.WARNING_MESSAGE);
			STATUS_TEXT_FIELD.setText(msgs.getString("Error_No_Files"));
			MAIN_BUTTON.setText(msgs.getString("Button_Reset"));
			state.setState(State.TERMINATED);
			return;
		}
		STATUS_TEXT_FIELD.setText(String.format(
				msgs.getString("Prompt_Download"), fileNames.length));
		MAIN_BUTTON.setText(String.format(msgs.getString("Button_Download"),
				fileNames.length));
		state.setState(State.PRE_DOWNLOAD);
	}

	private static void terminate(Exception e) {
		JTextArea ep = new JTextArea(e.toString() + "\n"
				+ msgs.getString("Hint_Github_Issue") + "\n"
				+ githubIssueTracker);
		ep.setEditable(false);
		ep.setFocusable(true);
		ep.setFont(new JLabel().getFont());
		ep.setBackground(new JLabel().getBackground());

		JOptionPane.showMessageDialog(FRAME, ep,
				msgs.getString("Status_Exception_Caught"),
				JOptionPane.ERROR_MESSAGE);
		STATUS_TEXT_FIELD.setText(msgs.getString("Status_Exception_Caught"));
		MAIN_BUTTON.setText(msgs.getString("Button_Reset"));
		state.setState(State.TERMINATED);
	}

	private static void tracker(String dialogTitle, String infoText,
			String exitButton, final SwingWorker<Void, Void> worker)
			throws IOException {
		// modal dialog -> disable interaction
		final JDialog dialog = new JDialog(FRAME, dialogTitle + " - "
				+ PROGRAM_NAME, ModalityType.APPLICATION_MODAL);
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

		dialog.setResizable(false);

		JProgressBar progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		progressBar.setStringPainted(true);
		progressBar.setString(" ");
		JPanel panel = new JPanel(new BorderLayout(GAP, GAP));
		panel.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
		panel.add(new JLabel(infoText), BorderLayout.PAGE_START);
		panel.add(progressBar, BorderLayout.CENTER);
		Button exit = new Button(exitButton);
		panel.add(exit, BorderLayout.PAGE_END);
		dialog.add(panel);
		dialog.pack();
		dialog.setLocationRelativeTo(FRAME);

		worker.addPropertyChangeListener(new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getPropertyName().equals("state")
						&& evt.getNewValue() == SwingWorker.StateValue.DONE) {
					dialog.dispose();
				}
			}
		});
		exit.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				System.exit(-1); // TODO
			}
		});

		worker.execute();
		// pop up modal dialog -> block until task is done
		dialog.setVisible(true);

		// task is done and we can get the exception

		try {
			worker.get();
		} catch (ExecutionException e) {
			IOException ioe = (IOException) e.getCause();
			throw ioe;
		} catch (InterruptedException e) {
			// should not happen
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static boolean verifyInput() {
		if (CATEGORY_BUTTON.isSelected()) {
			if (inputCategory == null || inputCategory.length() == 0) {
				JOptionPane.showMessageDialog(FRAME,
						msgs.getString("Error_Invalid_Name_Category") + " \""
								+ inputCategory + "\"",
						msgs.getString("Title_Invalid_Name"),
						JOptionPane.WARNING_MESSAGE);
				return false;
			}
		} else if (PAGE_BUTTON.isSelected()) {
			if (inputPage == null || inputPage.length() == 0) {
				JOptionPane.showMessageDialog(FRAME,
						msgs.getString("Error_Invalid_Name_Page") + " \""
								+ inputPage + "\"",
						msgs.getString("Title_Invalid_Name"),
						JOptionPane.WARNING_MESSAGE);
				return false;
			}
		}
		return true;
	}

	private static void download() {
		try {
			tracker(msgs.getString("Status_Downloading"),
					msgs.getString("Hint_Downloading"),
					msgs.getString("Text_Exit"), new SwingWorker<Void, Void>() {

						@Override
						protected Void doInBackground()
								throws FileNotFoundException, IOException {
							downloadLoop();
							return null;
						}
					});
			STATUS_TEXT_FIELD.setText(msgs.getString("Status_Run_Complete"));
			//
			MAIN_BUTTON.setText(msgs.getString("Button_Reset"));
			state.setState(State.TERMINATED);
		} catch (Exception e) {
			terminate(e);
			return;
		}
	}

	private static void preInit(boolean force) {
		if (!force && state.getState() == State.TERMINATED)
			return;
		STATUS_TEXT_FIELD.setText(msgs.getString("Status_Select_InOut"));
		MAIN_BUTTON.setText(msgs.getString("Button_GetList"));
		fileNames = null;
		state.setState(State.PRE_INIT);
	}

	protected static void getFileNames() throws FileNotFoundException,
			IOException {
		if (CATEGORY_BUTTON.isSelected()) {
			STATUS_TEXT_FIELD.setText(msgs.getString("Status_Get_Category")
					+ " ...");
			boolean subcat = false;
			// TODO: add argument to scan subcats
			fileNames = wiki.getCategoryMembers(inputCategory, subcat,
					Wiki.FILE_NAMESPACE);
		} else if (PAGE_BUTTON.isSelected()) {
			STATUS_TEXT_FIELD.setText(msgs.getString("Status_Get_Page")
					+ " ...");
			fileNames = wiki.getImagesOnPage(inputPage);
		} else { // fileButton.isSelected()
			STATUS_TEXT_FIELD.setText(msgs.getString("Status_Parse_File")
					+ " ...");
			fileNames = parseFileNames(inputFile.getPath());
		}
	}

	protected static void parseText(JTextField textField, boolean updateCategory) {
		String newValue = textField.getText();
		if (updateCategory) {
			newValue = newValue.replaceFirst("(?i)^\\s*(category\\s*:)?\\s*",
					"");
			inputCategory = newValue;
			textField.setText(newValue);
		} else { // updatePage
			newValue = newValue.replaceFirst("\\s*", "");
			inputPage = newValue;
			textField.setText(newValue);
		}

		hasSource = true;
		MAIN_BUTTON.setEnabled(hasSource && hasTarget);
		// Also reset because text was replaced!
		preInit(false);
	}

	private static void addOutputBox(Container boxPane) {
		JPanel output = new JPanel(new BorderLayout(GAP, GAP));
		output.setBorder(new TitledBorder(msgs.getString("Title_Output_Folder")));
		JPanel outputOptions = new JPanel(new BorderLayout(GAP, GAP));
		outputOptions.setBorder(new EmptyBorder(GAP, GAP, GAP, GAP));
		final JTextField currFolder;
		currFolder = new JTextField(null, 28);
		currFolder.setEditable(false);
		outputOptions.add(currFolder, BorderLayout.CENTER);
		JButton folderSelect = new JButton(msgs.getString("Button_Choose"));
		folderSelect.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser folderChooser = new JFileChooser(outputFolder);
				folderChooser
						.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				folderChooser.setAcceptAllFileFilterUsed(false);
				folderChooser.showOpenDialog(null);

				outputFolder = folderChooser.getSelectedFile();
				if (outputFolder == null) {
					currFolder.setText(null);
					hasTarget = false;
					MAIN_BUTTON.setEnabled(false);
				} else {
					currFolder.setText(outputFolder.toString());
					hasTarget = true;
					MAIN_BUTTON.setEnabled(hasSource && hasTarget);
				}
			}
		});
		outputOptions.add(folderSelect, BorderLayout.LINE_END);

		output.add(outputOptions);
		boxPane.add(output);
	}

	private static void addInputBox(Container boxPane) {
		JPanel input = new JPanel(new BorderLayout(GAP, GAP));
		input.setBorder(new TitledBorder(msgs.getString("Title_Source")));

		JPanel inputContainer = new JPanel(new BorderLayout(GAP, GAP));
		inputContainer.setBorder(new EmptyBorder(GAP, GAP, GAP, GAP));
		JPanel radioButtons = new JPanel(new GridLayout(3, 1, GAP / 2, GAP / 2));
		JPanel values = new JPanel(new GridLayout(3, 1, GAP / 2, GAP / 2));
		JPanel fileChoosePanel = new JPanel(new BorderLayout(GAP, GAP));

		CATEGORY_BUTTON.setSelected(true);
		CATEGORY_BUTTON.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent arg0) {
				// Reset internal state
				if (CATEGORY_BUTTON.isSelected())
					preInit(false);
			}
		});
		radioButtons.add(CATEGORY_BUTTON);
		CATEGORY_BUTTON.setToolTipText(msgs.getString("Hint_Src_Category"));
		final JTextField categoryTextField = new JTextField(10);
		categoryTextField.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				parseText(categoryTextField, true);
			}
		});
		categoryTextField.addFocusListener(new FocusListener() {

			@Override
			public void focusLost(FocusEvent arg0) {
				parseText(categoryTextField, true);
			}

			@Override
			public void focusGained(FocusEvent e) {
				CATEGORY_BUTTON.setSelected(true);
			}

		});
		values.add(categoryTextField);
		categoryTextField.setToolTipText(msgs.getString("Hint_Src_Category"));

		PAGE_BUTTON.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent arg0) {
				// Reset
				if (PAGE_BUTTON.isSelected())
					preInit(false);
			}
		});
		radioButtons.add(PAGE_BUTTON);
		PAGE_BUTTON.setToolTipText(msgs.getString("Hint_Src_Page"));
		final JTextField pageTextField = new JTextField(10);
		pageTextField.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				parseText(pageTextField, false);

			}
		});
		pageTextField.addFocusListener(new FocusListener() {

			@Override
			public void focusLost(FocusEvent e) {

				parseText(pageTextField, false);
			}

			@Override
			public void focusGained(FocusEvent e) {

				PAGE_BUTTON.setSelected(true);
			}
		});
		values.add(pageTextField);
		pageTextField.setToolTipText(msgs.getString("Hint_Src_Page"));

		FILE_BUTTON.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent arg0) {
				// Reset
				if (FILE_BUTTON.isSelected())
					preInit(false);
			}
		});
		radioButtons.add(FILE_BUTTON);
		FILE_BUTTON.setToolTipText(msgs.getString("Hint_File_Syntax"));
		FILE_BUTTON.setEnabled(false);
		final JTextField currFile;

		currFile = new JTextField(null, 28);
		currFile.setEditable(false);
		currFile.setToolTipText(msgs.getString("Hint_File_Syntax"));
		fileChoosePanel.add(currFile, BorderLayout.CENTER);

		JButton infileSelect = new JButton(msgs.getString("Button_Choose"));
		infileSelect.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fileChooser = new JFileChooser();
				fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
				fileChooser.showOpenDialog(null);

				inputFile = fileChooser.getSelectedFile();
				if (inputFile == null) {
					hasSource = false;
					currFile.setText(null);
					FILE_BUTTON.setEnabled(false);
					// Select one of the other buttons instead
					if (FILE_BUTTON.isSelected())
						CATEGORY_BUTTON.setSelected(true);
				} else {
					hasSource = true;
					currFile.setText(inputFile.toString());
					FILE_BUTTON.setSelected(true);
					FILE_BUTTON.setEnabled(true);
					MAIN_BUTTON.setEnabled(hasSource && hasTarget);
				}
			}
		});
		infileSelect.setToolTipText(msgs.getString("Hint_File_Syntax"));
		fileChoosePanel.add(infileSelect, BorderLayout.LINE_END);

		values.add(fileChoosePanel);

		ButtonGroup inputGroup = new ButtonGroup();
		inputGroup.add(CATEGORY_BUTTON);
		inputGroup.add(PAGE_BUTTON);
		inputGroup.add(FILE_BUTTON);

		inputContainer.add(radioButtons, BorderLayout.LINE_START);
		inputContainer.add(values, BorderLayout.CENTER);

		input.add(inputContainer, BorderLayout.CENTER);
		boxPane.add(input);
	}

	private static void addStatusPanel(Container boxPane) {
		JPanel statusPanel = new JPanel(new BorderLayout(GAP, GAP));
		statusPanel.setBorder(new TitledBorder(msgs.getString("Title_Status")));

		STATUS_TEXT_FIELD.setEditable(false);
		STATUS_TEXT_FIELD.setBorder(new EmptyBorder(GAP, GAP, GAP, GAP));
		STATUS_TEXT_FIELD.setHorizontalAlignment(JTextField.CENTER);
		statusPanel.add(STATUS_TEXT_FIELD);
		boxPane.add(statusPanel);
	}

	private static void addActionButton(Container boxPane) {
		MAIN_BUTTON.setFont(new Font("Sans", Font.BOLD, 16));
		MAIN_BUTTON.setAlignmentX(Component.CENTER_ALIGNMENT);
		MAIN_BUTTON.setEnabled(false);
		MAIN_BUTTON.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				handleAction();
			}
		});
		boxPane.add(MAIN_BUTTON);
	}

	private static void addHeader(Container boxPane) {
		Container logo = new Container();
		logo.setLayout(new BoxLayout(logo, BoxLayout.X_AXIS));

		JLabel picLabel = new JLabel(new ImageIcon("src/pics/logo-60.png"));
		picLabel.setFont(new Font("Serif", Font.ITALIC, 40));
		picLabel.setForeground(Color.BLUE);
		logo.add(picLabel);

		logo.add(Box.createHorizontalStrut(4 * GAP));

		JLabel versionLabel = new JLabel(VERSION);
		versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN));
		versionLabel.setAlignmentY(0);
		logo.add(versionLabel);
		boxPane.add(logo);

		Container description = new Container();
		description.setLayout(new FlowLayout(FlowLayout.CENTER, GAP, GAP));
		description.add(new JLabel(msgs.getString("Description_Program")));
		boxPane.add(description);
	}

	private static void createAndShowGUI() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			// who cares?
		}
		FRAME.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		List<Image> icons = new ArrayList<Image>();
		for (int px = 512; px >= 16; px = px / 2) {
			icons.add(new ImageIcon("src/pics/icon-" + px + ".png").getImage());
		}
		FRAME.setIconImages(icons);

		// Set up the content pane.
		Container pane = FRAME.getContentPane();

		pane.setLayout(new BoxLayout(pane, BoxLayout.X_AXIS));
		pane.add(Box.createHorizontalStrut(GAP)); // left space

		Container boxPane = new JPanel();
		boxPane.setLayout(new BoxLayout(boxPane, BoxLayout.Y_AXIS));

		// LOGO + version + description
		addHeader(boxPane);

		// input -- output
		addInputBox(boxPane);
		boxPane.add(Box.createVerticalStrut(GAP));
		addOutputBox(boxPane);
		boxPane.add(Box.createVerticalStrut(GAP));

		// Action button
		addActionButton(boxPane);

		// Status
		addStatusPanel(boxPane);

		boxPane.add(Box.createVerticalStrut(3 * GAP));

		pane.add(boxPane);
		pane.add(Box.createHorizontalStrut(GAP)); // right space

		// Display the window.
		FRAME.pack();
		FRAME.setVisible(true);

		// System.out.println(frame.getSize());
	}

	public static void main(String[] args) {

		wiki = new Wiki("commons.wikimedia.org");
		wiki.setMaxLag(3);
		wiki.setLogLevel(Level.WARNING);

		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				createAndShowGUI();
				preInit(true);
			}
		});

	}
}