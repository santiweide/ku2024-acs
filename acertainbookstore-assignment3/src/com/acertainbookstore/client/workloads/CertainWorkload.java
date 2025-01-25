/**
 * 
 */
package com.acertainbookstore.client.workloads;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.BookEditorPick;
import com.acertainbookstore.business.CertainBookStore;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * 
 * CertainWorkload class runs the workloads by different workers concurrently.
 * It configures the environment for the workers using WorkloadConfiguration
 * objects and reports the metrics
 * 
 */
public class CertainWorkload {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		int numConcurrentWorkloadThreads = 10;
		String serverAddress = "http://localhost:8081";
		boolean localTest = true;
		List<WorkerRunResult> workerRunResults = new ArrayList<WorkerRunResult>();
		List<Future<WorkerRunResult>> runResults = new ArrayList<>();

		// Initialize the RPC interfaces if its not a localTest, the variable is
		// override if the property is set
		String localTestProperty = System
				.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
		localTest = (localTestProperty != null) ? Boolean
				.parseBoolean(localTestProperty) : localTest;
		System.out.println("Local test: " + localTest);

		BookStore bookStore = null;
		StockManager stockManager = null;
		if (localTest) {
			CertainBookStore store = new CertainBookStore();
			bookStore = store;
			stockManager = store;
		} else {
			stockManager = new StockManagerHTTPProxy(serverAddress + "/stock");
			bookStore = new BookStoreHTTPProxy(serverAddress);
		}

		// Generate data in the bookstore before running the workload
		initializeBookStoreData(bookStore, stockManager);

		ExecutorService exec = Executors
				.newFixedThreadPool(numConcurrentWorkloadThreads);

		for (int i = 0; i < numConcurrentWorkloadThreads; i++) {
			WorkloadConfiguration config = new WorkloadConfiguration(bookStore,
					stockManager);
			Worker workerTask = new Worker(config);
			// Keep the futures to wait for the result from the thread
			runResults.add(exec.submit(workerTask));
		}

		// Get the results from the threads using the futures returned
		for (Future<WorkerRunResult> futureRunResult : runResults) {
			WorkerRunResult runResult = futureRunResult.get(); // blocking call
			workerRunResults.add(runResult);
		}

		exec.shutdownNow(); // shutdown the executor

		// Finished initialization, stop the clients if not localTest
		if (!localTest) {
			((BookStoreHTTPProxy) bookStore).stop();
			((StockManagerHTTPProxy) stockManager).stop();
		}

		reportMetric(workerRunResults);
	}

	/**
	 * Computes the metrics and prints them
	 * 
	 * @param workerRunResults
	 */
	public static void reportMetric(List<WorkerRunResult> workerRunResults) {
		int totalSuccessfulInteractions = 0;
		int totalInteractions = 0;
		int totalFrequentCustomerInteractions = 0;

		List<Integer> successfulInteractionsList = new ArrayList<>();
		List<Double> latencyList = new ArrayList<>(); // Latency per worker
		List<Double> deviationLatencyList = new ArrayList<>();

		for (WorkerRunResult result : workerRunResults) {
			List<Double> cur_latencyList = new ArrayList<>(); // Latency per worker

			int successfulInteractions = result.getSuccessfulInteractions();
			long elapsedTimeInNanoSecs = result.getElapsedTimeInNanoSecs();

			successfulInteractionsList.add(successfulInteractions);

			// Calculate latency for this worker (in ms)
			if (successfulInteractions > 0) {
				double workerLatency = (double) elapsedTimeInNanoSecs / successfulInteractions / 1_000_000.0;
				cur_latencyList.add(workerLatency);
			}

			totalSuccessfulInteractions += successfulInteractions;
			totalInteractions += result.getTotalRuns();
			totalFrequentCustomerInteractions += result.getTotalFrequentBookStoreInteractionRuns();
			// Average latency across all workers
			double cur_avgLatency = computeAverage(cur_latencyList);
			double cur_deviationLatency = computeStandardDeviation(cur_latencyList, cur_avgLatency);
			latencyList.add(cur_avgLatency);
			deviationLatencyList.add(cur_deviationLatency);
		}

		double avgLatency = computeAverage(latencyList);
		double avgDeviationLatency = computeAverage(deviationLatencyList);

		// Calculate average throughput
		double totalRunTimeInSeconds = workerRunResults.stream()
				.mapToLong(WorkerRunResult::getElapsedTimeInNanoSecs)
				.sum() / 1_000_000_000.0;
		double throughput = (double) totalSuccessfulInteractions / totalRunTimeInSeconds; // successful interactions per second


		// Compute averages and standard deviations for successful interactions
		double avgSuccessfulInteractions = computeAverage(successfulInteractionsList);
		double deviationSuccessfulInteractions = computeStandardDeviation(successfulInteractionsList, avgSuccessfulInteractions);

		// Compute percentages
		double customerInteractionPercentage = (double) totalFrequentCustomerInteractions / totalInteractions * 100.0;

		// Print metrics
		System.out.println("===== Performance Metrics =====");
		System.out.println("Throughput (successful interactions/second): " + throughput);
		System.out.println("Average Latency (ms): " + avgLatency);
		System.out.println("Deviation of Latency (ms): " + avgDeviationLatency);
		System.out.println("Average Successful Interactions: " + avgSuccessfulInteractions);
		System.out.println("Deviation of Successful Interactions: " + deviationSuccessfulInteractions);
		System.out.println("Total Interactions: " + totalInteractions);
		System.out.println("Total Successful Interactions: " + totalSuccessfulInteractions);
		System.out.println("Customer Interaction Percentage: " + customerInteractionPercentage + "%");
		System.out.println("================================");
	}


	/**
	 * Computes the average of a list of numbers.
	 */
	private static double computeAverage(List<? extends Number> values) {
		return values.stream().mapToDouble(Number::doubleValue).average().orElse(0.0);
	}

	/**
	 * Computes the standard deviation of a list of numbers.
	 */
	private static double computeStandardDeviation(List<? extends Number> values, double mean) {
		double variance = values.stream()
				.mapToDouble(value -> Math.pow(value.doubleValue() - mean, 2))
				.average()
				.orElse(0.0);
		return Math.sqrt(variance);
	}

	/**
	 * Generate the data in bookstore before the workload interactions are run
	 * 
	 * Ignores the serverAddress if its a localTest
	 * 
	 */
	public static void initializeBookStoreData(BookStore bookStore, StockManager stockManager) throws BookStoreException {
		// Define the number of initial books
		final int initialNumBooks = 120;

		BookSetGenerator generator = new BookSetGenerator();

		Set<StockBook> initialStockBooks = generator.nextSetOfStockBooks(initialNumBooks);

		stockManager.addBooks(initialStockBooks);

		// add editor picks
		List<StockBook> listBooks;
		listBooks = stockManager.getBooks();
		int editorPickCount = (int) (listBooks.size() * 0.30);
		Random random = new Random();
		Set<BookEditorPick> editorPicksVals = new HashSet<>();
		for (int i = 0; i < editorPickCount; i++) {
			int randomIndex = random.nextInt(listBooks.size());
			StockBook randomBook = listBooks.get(randomIndex);

			BookEditorPick editorPick = new BookEditorPick(randomBook.getISBN(), true);
			editorPicksVals.add(editorPick);
		}
		stockManager.updateEditorPicks(editorPicksVals);
	}
}
