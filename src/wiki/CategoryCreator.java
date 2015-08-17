package wiki;

import java.io.IOException;
import java.util.HashSet;

import javax.security.auth.login.LoginException;

public class CategoryCreator {
	private HashSet<String> createdCategories;
	private Wiki wiki;

	/**
	 * Construct the CategoryCreator and pass in any categories that should be
	 * considered existing
	 * 
	 * @param wiki
	 *            the wiki where new cateogies should be created
	 * @param createdCategories
	 *            the already existing categories
	 */
	public CategoryCreator(Wiki wiki, String[] createdCategories) {
		this.wiki = wiki;
		if (createdCategories == null)
			createdCategories = new String[] {};
		this.createdCategories = new HashSet<String>();
		for (String cat : createdCategories)
			try {
				addCategory(cat, null, false);
			} catch (Exception e) {
				// never happens
				e.printStackTrace();
				System.exit(-1);
			}
	}

	/**
	 * Check if the given category already exists and create it if not
	 * 
	 * @param categoryName
	 *            the category to check; Method immediately returns if this is
	 *            null or empty
	 * @param text
	 *            the text of the category when created
	 * @param mayNotExist
	 *            set this to true! If this is false, NO check is done if the
	 *            category exists on-wiki
	 * @throws LoginException
	 * @throws IOException
	 *             if a network error occurs
	 */
	public void addCategory(String categoryName, String text,
			boolean mayNotExist) throws LoginException, IOException {
		if (categoryName == null || categoryName.isEmpty())
			return;
		categoryName = "Category:"
				+ categoryName.replaceFirst("^([cC]ategory:)", "");
		if (createdCategories.add(categoryName) && mayNotExist) {
			if (!wiki.exists(new String[] { categoryName })[0])
				wiki.edit(categoryName, text, "Creating category");
		}
	}
}
