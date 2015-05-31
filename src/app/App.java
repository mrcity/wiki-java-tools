package app;

import java.io.IOException;

interface WikiAPI {

	/**
	 * Fetch something from the WikiAPI
	 * 
	 * @return the result from the API
	 * @throws IOException
	 *             if a network issue occurs
	 */
	Object fetch() throws IOException;
}

public class App {
	protected static final int MAX_FAILS = 3;
	protected static final int EXCEPTION_SLEEP_TIME = 30 * 1000; // ms

	/**
	 * Attempt to fetch from the given api a maximum of maxFails and wait some
	 * time (max EXCEPTION_SLEEP_TIME) between tries.
	 * 
	 * @param api
	 *            the API to fetch from
	 * @param maxFails
	 *            the maximum number of exceptions to tolerate
	 * @param maxExceptionSleepTime
	 *            the maximum time to sleep in ms when an exception occurs;
	 *            scaled by maxFails^-2
	 * @return the result from the API
	 * @throws IOException
	 *             when a network error occurs
	 */
	protected static Object attemptFetch(WikiAPI api, int maxFails,
			int maxExceptionSleepTime) throws IOException {
		maxFails--;
		try {
			return api.fetch();
		} catch (IOException e) {
			if (maxFails == 0) {
				throw e;
			} else {
				try {
					Thread.sleep(maxExceptionSleepTime / (maxFails * maxFails));
				} catch (InterruptedException ignore) {
				}
				return attemptFetch(api, maxFails, maxExceptionSleepTime);
			}
		}
	}
}
