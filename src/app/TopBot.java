package app;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;

import javax.security.auth.login.LoginException;

import wiki.Wiki;
import wiki.WikiPage;

class Category {
	private String name;
	private String[] fileMembers;
	private Category[] children;
	public static final String CATEGORY_PREFIX = "Category:";

	/**
	 * Construct a category
	 * 
	 * @param name
	 *            the name of the category which may or may not contain the
	 *            namespace prefix
	 * @param children
	 *            the children of the category or null
	 * @param fileMembers
	 *            the members of this category which are files
	 */
	Category(String name, Category[] children, String[] fileMembers) {
		this.name = CATEGORY_PREFIX
				+ WikiPage.firstCharToUpperCase(name.replaceFirst("(?i)^"
						+ CATEGORY_PREFIX, ""));
		this.fileMembers = fileMembers == null ? new String[] {} : fileMembers;
		this.children = children == null ? new Category[] {} : children;

	}

	/**
	 * Return the name of the category with the namespace prefix
	 * 
	 * @return the name
	 */
	String getName() {
		return name;
	}

	/**
	 * Return the children of this category
	 * 
	 * @return the array of children
	 */
	Category[] getChildren() {
		return children;
	}

	/**
	 * Return the members of this category which are in the file namespace
	 * 
	 * @return the file members
	 */
	String[] getFileMembers() {
		return fileMembers;
	}

	/**
	 * Create a String representation of this category in the form of
	 * prefix+name+suffix+numberOfFiles
	 * 
	 * @param shortString
	 *            determines if a very compressed String is returned
	 * @param prefix
	 *            the prefix of the returned string
	 * @param suffix
	 *            comes after the name
	 * @return the string
	 */
	String toString(boolean shortString, String prefix, String suffix) {
		if (shortString)
			return this.getName().substring(CATEGORY_PREFIX.length())
					.replaceAll("(?i)" + TopBot.ROOT_CATEGORY, "")
					+ "(" + this.getFileMembers().length + " files)";
		else
			return prefix + this.getName() + suffix + " ("
					+ this.getFileMembers().length + " files)";
	}
}

class CategoryTree extends Category {
	private int recursiveCountFileMembers;
	private boolean recursiveFileMembersUpdated;
	private LinkedList<Category> reportCategories;
	/**
	 * Create report when this many recursive members (or more)
	 */
	static final int THRESHOLD_CATEGORY_SIZE = 800;

	/**
	 * Construct a category with extended capabilities of recursively grouping
	 * subcategories
	 * 
	 * @param name
	 *            the name of the category which may or may not contain the
	 *            namespace prefix
	 * @param children
	 *            the children of the category or null
	 * @param fileMembers
	 *            the members of this category which are files
	 */
	CategoryTree(String name, CategoryTree[] children, String[] fileMembers) {
		super(name, children == null ? new CategoryTree[] {} : children,
				fileMembers);
		this.recursiveCountFileMembers = 0;
		this.recursiveFileMembersUpdated = false;
	}

	/**
	 * Return the number of members in the file namespace of this category and
	 * all subcategories which go into the same report
	 * 
	 * @return the number of file members
	 */
	int getRecursiveCountFileMembers() {
		if (!recursiveFileMembersUpdated)
			updateCount();

		return recursiveCountFileMembers;
	}

	/**
	 * Update the recursiveFileMembers variable
	 */
	private void updateCount() {
		recursiveCountFileMembers = getFileMembers().length;
		if (getChildren() == null)
			return;

		for (CategoryTree cat : getChildren()) {
			if (!cat.createsReport()) {
				recursiveCountFileMembers += cat.getRecursiveCountFileMembers();
			}
		}
		recursiveFileMembersUpdated = true;
	}

	@Override
	CategoryTree[] getChildren() {
		return (CategoryTree[]) super.getChildren();
	}

	/**
	 * Return if a report will be created
	 * 
	 * @return if a report will be created
	 */
	boolean createsReport() {
		return getRecursiveCountFileMembers() >= THRESHOLD_CATEGORY_SIZE;
	}

	/**
	 * Create the groups of all categories which will create a report; Those
	 * categories may have category children which should go into the same
	 * report; This method also adds this category in all cases; Duplicates are
	 * removed
	 * 
	 * @return the category groups as a LinkedList holding the root nodes
	 */
	LinkedList<Category> getReportCategories() {
		if (reportCategories == null) {
			LinkedList<Category>[] result = determineReportCategories();
			reportCategories = result[0];
			if (!this.createsReport()) {
				// create report anyway for this category
				reportCategories.add(new Category(this.getName(), result[1]
						.toArray(new Category[] {}), this.getFileMembers()));
			}
			// remove duplicates
			HashSet<String> uniqueSet = new HashSet<String>();
			LinkedList<Category> noDupes = new LinkedList<Category>();
			for (Category cat : reportCategories) {
				if (!uniqueSet.contains(cat.getName())) {
					noDupes.add(cat);
					uniqueSet.add(cat.getName());
				} else {
					System.out.println("Warning: Duplicate " + cat.getName());
					// TODO logger
				}
			}
			reportCategories = noDupes;
		}
		return reportCategories;
	}

	/**
	 * Recursively determine which categories should be grouped into reports and
	 * create a LinkedList with all categories which will produce a report;
	 * Create another LinkedList of the remaining
	 * 
	 * @return the LinkedList array with the first entry being the LinkedList
	 *         which produces the reports and the second entry being the
	 *         remaining
	 */
	private LinkedList<Category>[] determineReportCategories() {
		if (getChildren() == null || getChildren().length == 0) {
			LinkedList<Category> leafNode = new LinkedList<Category>();
			leafNode.add(this);

			if (this.createsReport())
				return (LinkedList<Category>[]) new LinkedList<?>[] { leafNode,
						new LinkedList<Category>() };
			else
				return (LinkedList<Category>[]) new LinkedList<?>[] {
						new LinkedList<Category>(), leafNode };
		}

		LinkedList<Category> kidNodesReport = new LinkedList<Category>();
		LinkedList<Category> kidNodesNoReport = new LinkedList<Category>();
		for (CategoryTree kid : getChildren()) {
			LinkedList<Category>[] kidResult = kid.determineReportCategories();
			// Just copy all previously determined reports
			kidNodesReport.addAll((LinkedList<Category>) kidResult[0]);
			// Just save all other for later
			kidNodesNoReport.addAll(kidResult[1]);
		}
		if (this.createsReport()) {
			// create new category with all kids in one array
			Category pruned = new Category(this.getName(),
					kidNodesNoReport.toArray(new Category[] {}),
					this.getFileMembers());
			kidNodesReport.add(pruned);
			// start from scratch with grouping
			kidNodesNoReport = new LinkedList<Category>();
		}
		return (LinkedList<Category>[]) new LinkedList<?>[] { kidNodesReport,
				kidNodesNoReport };
	}
}

class TopBotThread extends Thread {

	private Wiki wiki;
	private String categoryName;
	private Category category;
	private int exceptions;

	/**
	 * Create a thread which can crawl a category and count each member's global
	 * usage; Also print which categories will be crawled
	 * 
	 * @param wiki
	 *            the wiki to connect to
	 * @param category
	 *            the category to crawl (may or may not start with "Category:")
	 */
	public TopBotThread(Wiki wiki, Category category) {
		this.wiki = wiki;
		this.categoryName = category.getName().replaceFirst(
				"(?i)^" + Category.CATEGORY_PREFIX, "");
		this.category = category;
		exceptions = 0;

		System.out.println(getInfo(true));
	}

	/**
	 * Create a long String of all categories to be crawled
	 * 
	 * @param compressed
	 *            set the format of the string
	 * @return the string
	 */
	private String getInfo(boolean compressed) {
		String info = "";
		String prefix = compressed ? "" : "[[:";
		String suffix = compressed ? "" : "]]";
		int total = 0;
		info += category.toString(compressed, "'''" + prefix, suffix + "'''")
				+ "\n";
		total += category.getFileMembers().length;
		for (Category kid : category.getChildren()) {
			info += "* " + kid.toString(compressed, prefix, suffix) + "\n";
			total += kid.getFileMembers().length;
		}
		return info + "\n" + "Total number of scanned files: " + total + "\n";
	}

	/**
	 * Try to crawl the category and return right after the second exception
	 * occurs
	 */
	public void run() {
		if (exceptions == 2) {
			System.out.println(this.getName() + " shut down after "
					+ exceptions + " exceptions. (" + categoryName + ")");
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
	 * @param categoryName
	 *            Target category (Ex.: 'Flag images that should use vector
	 *            graphics')
	 * @param number
	 *            The number of top files to list
	 * @throws IOException
	 * @throws LoginException
	 */
	private void crawlCategory() throws IOException, LoginException {
		LinkedList<String> members = getAllCategoryMembers(category);
		Comparable[][] cArray = new Comparable[members.size()][2];

		for (int i = 0; i < members.size(); ++i) {
			if (i % 25 == 0)
				System.out.println(this.getName() + "   PROGRESS: " + i
						+ " of " + members.size() + " done.");
			cArray[i][1] = getActualUsageCount(wiki.getGlobalUsage(members
					.get(i)));
			cArray[i][0] = members.get(i);
		}

		java.util.Arrays.sort(cArray, new java.util.Comparator<Comparable[]>() {
			public int compare(Comparable[] a, Comparable[] b) {
				return b[1].compareTo(a[1]);
			}
		});

		SimpleDateFormat timestamp = new SimpleDateFormat("yyyyMMddhhmmss");
		Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"),
				Locale.UK);
		Date twoWeeksFromNow = now.getTime();
		twoWeeksFromNow.setDate(now.getTime().getDate() + 14);
		String text = "{{#ifexpr:{{CURRENTTIMESTAMP}}>"
				+ timestamp.format(twoWeeksFromNow)
				+ "|{{speedy|Outdated report, which was replaced by "
				+ "a fresh one through [[user:{{subst:REVISIONUSER}}]].}}|}}"
				+ "\nLast update: "
				+ DateFormat.getDateInstance(DateFormat.FULL).format(
						now.getTime()) + "." + "\n"
				+ "\nThis report includes the follwing categories while "
				+ "counting only the usage of each file "
				+ "in the main namespace.\n\n" + getInfo(false)
				+ "<gallery showfilename=yes >\n";
		for (int i = 0; i < Math.min(TopBot.TARGET_COUNT, members.size()); i++) {
			text = text + cArray[i][0] + "|" + (i + 1) + ". Used "
					+ cArray[i][1] + " times.\n";
		}
		text = text
				+ "</gallery>"
				+ (members.size() < TopBot.TARGET_COUNT ? "" : "\n[[Category:"
						+ WikiPage.firstCharToUpperCase(TopBot.ROOT_CATEGORY)
						+ "]]") + "\n[[Category:"
				+ WikiPage.firstCharToUpperCase(categoryName) + "]]";
		String title = "Top " + TopBot.TARGET_COUNT + " "
				+ WikiPage.firstCharToLowerCase(categoryName);
		String[] splittedText = { "" };
		try {
			splittedText = wiki.getPageText(title).split(TopBot.SEPARATOR);
		} catch (FileNotFoundException ignore) {
		}
		wiki.edit(title, (splittedText.length == 1 ? "" : splittedText[0])
				+ TopBot.SEPARATOR + "\n" + text, "Update by Bot "
				+ TopBot.VERSION + " (Scanned " + members.size() + " files)");
	}

	/**
	 * Just return all file members of this tree
	 * 
	 * @param root
	 *            the root of the category tree to scan
	 * @return string array holding all members of the given category and
	 *         members of valid subcategories
	 * @throws IOException
	 *             if a network error occurs
	 */
	private LinkedList<String> getAllCategoryMembers(Category root)
			throws IOException {
		LinkedList<String> allCategoryMembers = new LinkedList<String>();

		for (Category cat : root.getChildren())
			allCategoryMembers.addAll(Arrays.asList(cat.getFileMembers()));
		allCategoryMembers.addAll(Arrays.asList(root.getFileMembers()));

		return allCategoryMembers;
	}

	/**
	 * Calculate the global usage only in the main namespace
	 * 
	 * @param globalUsage
	 *            the list of globalusage returned by the wiki java api
	 * @return the global usage count in the main namespace
	 */
	private int getActualUsageCount(String[][] globalUsage) {
		int count = 0;
		for (String[] usage : globalUsage) {
			String title = usage[1];
			if (!title.contains(":"))
				count++;
		}
		return count;
	}

}

public class TopBot {

	public static final String ROOT_CATEGORY = "images that should use vector graphics";

	/**
	 * Cap number of entries to this many
	 */
	public static final int TARGET_COUNT = 200;

	public static final String SEPARATOR = "<!-- Only text ABOVE this line will be preserved on updates -->";
	public static final String VERSION = "v15.05.27";

	public static void main(String[] args) {

		char[] password = passwordDialog(args);
		Wiki commons = new Wiki("commons.wikimedia.org");
		try {
			commons.login(args[0], password);
			password = null;

			commons.setThrottle(5 * 1000);
			commons.setMaxLag(3);
			commons.setMarkMinor(false);
			commons.setMarkBot(false);
			commons.setLogLevel(Level.WARNING);

			System.out.println("Fetching categories ...");
			CategoryTree root = generateCategoryTree(ROOT_CATEGORY, commons);
			LinkedList<Category> reportCats = root.getReportCategories();

			System.out.println("Creating threads ...");

			// Create threads
			TopBotThread[] threads = new TopBotThread[reportCats.size()];
			for (int j = 0; j < threads.length; j++) {
				threads[j] = new TopBotThread(commons, reportCats.get(j));
			}
			Thread.sleep(1000);
			for (TopBotThread t : threads) {
				t.start();
			}
		} catch (LoginException | IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Verify the command line arguments and print program information while
	 * asking for the user's password
	 * 
	 * @param args
	 *            the command line arguments
	 * @return the password
	 */
	private static char[] passwordDialog(String[] args) {
		System.out.println(VERSION);

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
		System.out.println("Please type in the password for " + args[0] + ".");
		return System.console().readPassword();
	}

	/**
	 * Determine the category tree which should be crawled; Only consider
	 * categories that end with ROOT_CATEGORY
	 * 
	 * @param root
	 *            the name of the category which represents the root of the tree
	 * @param wiki
	 *            the wiki to connect to
	 * @return the category tree
	 * @throws IOException
	 *             if a network error occurs
	 */
	private static CategoryTree generateCategoryTree(String root, Wiki wiki)
			throws IOException {
		String[] childrenStrings = wiki.getCategoryMembers(root, 0,
				new int[] { Wiki.CATEGORY_NAMESPACE });
		String[] rootMembers = wiki.getCategoryMembers(root, 0,
				new int[] { Wiki.FILE_NAMESPACE });

		// count number of kids
		int svgCategoriesCount = 0;
		for (String cat : childrenStrings) {
			if (cat.endsWith(ROOT_CATEGORY))
				svgCategoriesCount++;
		}

		if (svgCategoriesCount == 0)
			return new CategoryTree(root, null, rootMembers);

		// declare and initialize children array
		CategoryTree[] childrenCategories = new CategoryTree[svgCategoriesCount];
		CategoryTree rootCategory = new CategoryTree(root, childrenCategories,
				rootMembers);
		svgCategoriesCount = 0;
		for (String cat : childrenStrings) {
			if (cat.endsWith(ROOT_CATEGORY)) {
				// TODO fix potential category loop bug!
				childrenCategories[svgCategoriesCount++] = generateCategoryTree(
						cat, wiki);
			}
		}
		return rootCategory;
	}
}