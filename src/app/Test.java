package app;

import java.io.IOException;

import javax.security.auth.login.LoginException;

import wiki.CategoryCreator;
import wiki.Commons;
import wiki.Wiki;
import wiki.WikiPage;

public class Test {

	public static void main(String[] args) throws IOException, LoginException {
		System.out.println("YaCBot Test");

		Wiki commons = new Wiki("commons.wikimedia.org");
		commons.setThrottle(1000);
		commons.login(Credentials.YACBOT_TEST.getUsername(),
				Credentials.YACBOT_TEST.getPassword());
		String[] testFileQA = {
				"File:Alto de Mezkiriz (54616436).jpg",
				"File:Alexander Island Washington.jpg",
				"File:Adamawa Turtle-dove (Streptopelia hypopyrrha) in tree from side.jpg",
				"File:Alex Levac.jpg" };

		WikiPage target;
		final CategoryCreator catGen = new CategoryCreator(commons,
				Commons.FLICKR_TRACKING_CATEGORY_OPT_OUT);
		for (String f : testFileQA) {
			target = new WikiPage(commons, f, catGen);
			target.cleanupWikitext();
			if (!target.getPlainText().matches(
					"(?is)" + ".*?\\[\\[category:.+?<!--.*")
					&& !target.getPlainText().matches(
							"(?is)" + ".*?\\{\\{\\s*#.+"))
				target.cleanupOvercat(0, true);
			target.cleanupUndercat();
			target.writeText();
		}
	}

}
