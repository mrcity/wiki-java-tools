package app;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.logging.Level;

import javax.security.auth.login.LoginException;

import wiki.Wiki;
import wiki.WikiPage;

class TopBotThread extends Thread {

	private Wiki wiki;
	private String category;
	private int exceptions;
	private boolean scanSubCats;
	final private int number = 200;

	public TopBotThread(Wiki wiki, String category, boolean scanSubCats) {
		this.wiki = wiki;
		this.category = category;
		this.scanSubCats = scanSubCats;
		exceptions = 0;
	}

	public void run() {
		if (exceptions == 2) {
			System.out.println(this.getName() + " shut down after "
					+ exceptions + " exceptions. (" + category + ")");
			return;
		}
		try {
			System.out.println(this.getName() + "   START");
			crawlCategory();
			System.out.println(this.getName() + "   END");
		} catch (LoginException | IOException e) {
			e.printStackTrace();
			exceptions++;
			run();
		}

	}

	/**
	 * Count usage and write to page
	 * 
	 * @param wiki
	 *            Target wiki
	 * @param category
	 *            Target category (Ex.: 'Flag images that should use vector
	 *            graphics')
	 * @param number
	 *            The number of top files to list
	 * @throws IOException
	 * @throws LoginException
	 */
	private void crawlCategory() throws IOException, LoginException {
		String[] members;
		if (!scanSubCats)
			members = wiki.getCategoryMembers(category, false, 6);
		else {
			ArrayList<String> membersTemp = new ArrayList<String>();
			String[] subCats = wiki.getCategoryMembers(category, false,
					Wiki.CATEGORY_NAMESPACE);
			for (String subCat : subCats) {
				membersTemp.addAll(Arrays.asList(wiki.getCategoryMembers(
						subCat, false, 6)));
			}
			members = membersTemp.toArray(new String[membersTemp.size()]);
		}
		Comparable[][] cArray = new Comparable[members.length][2];

		for (int i = 0; i < members.length; ++i) {
			if (i % 25 == 0)
				System.out.println(this.getName() + "   PROGRESS: " + i
						+ " of " + members.length + " done.");
			cArray[i][1] = wiki.getGlobalUsageCount(members[i]);
			cArray[i][0] = members[i];
		}

		java.util.Arrays.sort(cArray, new java.util.Comparator<Comparable[]>() {
			public int compare(Comparable[] a, Comparable[] b) {
				return b[1].compareTo(a[1]);
			}
		});

		String text = "Last update: "
				+ DateFormat.getDateInstance(DateFormat.FULL).format(
						Calendar.getInstance().getTime()) + "." + "\n"
				+ "<gallery showfilename=yes >\n";
		for (int i = 0; i < Math.min(number, members.length); i++) {
			text = text + cArray[i][0] + "|" + (i + 1) + ". Used "
					+ cArray[i][1] + " times.\n";
		}
		text = text + "</gallery>"
				+ "\n[[Category:Images that should use vector graphics]]"
				+ "\n[[Category:" + WikiPage.firstCharToUpperCase(category)
				+ "]]";
		String title = "Top " + number + " "
				+ WikiPage.firstCharToLowerCase(category);
		String separator = "<!-- Only text ABOVE this line will be preserved on updates -->";
		String[] splittedText = wiki.getPageText(title).split(separator);
		wiki.edit(title, (splittedText.length == 1 ? "" : splittedText[0])
				+ separator + "\n" + text, "Update");
	}

}

public class TopBot {

	public static void main(String[] args) {

		System.out.println("v14.10.14");

		String[] expectedArgs = { "username" };
		String[] expectedArgsDescription = { "username is your username on the wiki." };
		if (args.length != expectedArgs.length) {
			System.out.print("Usage: java -jar filename.jar");
			for (String i : expectedArgs)
				System.out.print(" [" + i + "]");
			System.out.println("");
			for (String i : expectedArgsDescription)
				System.out.println("Where " + i);
			System.exit(-1);
		}
		Wiki commons = new Wiki("commons.wikimedia.org");
		try {
			System.out.println("Please type in the password for " + args[0]
					+ ".");
			commons.login(args[0], "5f3wtJrqxGyB6K8PSaeRqhzL");

			commons.setThrottle(5 * 1000);
			commons.setMaxLag(3);
			commons.setMarkMinor(false);
			commons.setMarkBot(false);
			commons.setLogLevel(Level.WARNING);

			System.out.println("\n\n========\n\n"
					+ "Processing the following categories:" + "\n");
			String[] cats = {
					"chemical images that should use vector graphics",
					"map images that should use vector graphics",
					"coat of arms images that should use vector graphics",
					"symbol images that should use vector graphics",
					"diagram images that should use vector graphics",
					"logo images that should use vector graphics",
					"flag images that should use vector graphics" };
			String[] subCats = { "images that should use vector graphics" };
			for (String cat : cats) {
				System.out.println("Category:" + cat);
			}
			for (String cat : subCats) {
				System.out.println("Category:" + cat
						+ " (AND DIRECT SUBCATEGORIES)");
			}
			System.out.println("\n========\n\n");
			Thread.sleep(1000);
			// Create threads

			TopBotThread[] threads = new TopBotThread[cats.length
					+ subCats.length];
			int i = 0;
			for (; i < cats.length; i++) {
				threads[i] = new TopBotThread(commons, cats[i], false);
				threads[i].start();
			}
			for (; i < threads.length; i++) {
				threads[i] = new TopBotThread(commons,
						subCats[i - cats.length], true);
				threads[i].start();
			}

		} catch (LoginException | IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
}