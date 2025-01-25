/**
 * 
 */
package com.acertainbookstore.client.workloads;

import java.util.*;
import java.util.concurrent.Callable;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreException;

/**
 * 
 * Worker represents the workload runner which runs the workloads with
 * parameters using WorkloadConfiguration and then reports the results
 * 
 */
public class Worker implements Callable<WorkerRunResult> {
    private WorkloadConfiguration configuration = null;
    private int numSuccessfulFrequentBookStoreInteraction = 0;
    private int numTotalFrequentBookStoreInteraction = 0;

    public Worker(WorkloadConfiguration config) {
	configuration = config;
    }

    /**
     * Run the appropriate interaction while trying to maintain the configured
     * distributions
     * 
     * Updates the counts of total runs and successful runs for customer
     * interaction
     * 
     * @param chooseInteraction
     * @return
     */
    private boolean runInteraction(float chooseInteraction) {
		try {
			float percentRareStockManagerInteraction = configuration.getPercentRareStockManagerInteraction();
			float percentFrequentStockManagerInteraction = configuration.getPercentFrequentStockManagerInteraction();

			if (chooseInteraction < percentRareStockManagerInteraction) { // 10
				runRareStockManagerInteraction();
				} else if (chooseInteraction < percentRareStockManagerInteraction // 30
					+ percentFrequentStockManagerInteraction) {
					runFrequentStockManagerInteraction();
				} else { // 100
					numTotalFrequentBookStoreInteraction++;
					runFrequentBookStoreInteraction();
					numSuccessfulFrequentBookStoreInteraction++;
				}
		} catch (BookStoreException ex) {
			System.out.println("Error: " + ex.getMessage());
			return false;
		}
		return true;
    }

    /**
     * Run the workloads trying to respect the distributions of the interactions
     * and return result in the end
     */
    public WorkerRunResult call() throws Exception {
		int count = 1;
		long startTimeInNanoSecs = 0;
		long endTimeInNanoSecs = 0;
		int successfulInteractions = 0;
		long timeForRunsInNanoSecs = 0;

		Random rand = new Random();
		float chooseInteraction;

		// Perform the warmup runs
		while (count++ <= configuration.getWarmUpRuns()) {
			chooseInteraction = rand.nextFloat() * 100f;
			runInteraction(chooseInteraction);
	}

	count = 1;
	numTotalFrequentBookStoreInteraction = 0;
	numSuccessfulFrequentBookStoreInteraction = 0;

		// Perform the actual runs
		startTimeInNanoSecs = System.nanoTime();
		while (count++ <= configuration.getNumActualRuns()) {
			chooseInteraction = rand.nextFloat() * 100f;
			if (runInteraction(chooseInteraction)) { // 随机选择一种interaction来测试
				successfulInteractions++;
			}
		}
		endTimeInNanoSecs = System.nanoTime();
		timeForRunsInNanoSecs += (endTimeInNanoSecs - startTimeInNanoSecs);
		return new WorkerRunResult(successfulInteractions, timeForRunsInNanoSecs, configuration.getNumActualRuns(),
			numSuccessfulFrequentBookStoreInteraction, numTotalFrequentBookStoreInteraction);
    }

    /**
     * Runs the new stock acquisition interaction
     * 
     * @throws BookStoreException
     */
	private void runRareStockManagerInteraction() throws BookStoreException {
		StockManager stockManager = configuration.getStockManager();
		BookSetGenerator generator = configuration.getBookSetGenerator();

		// Fetch all books from the store
		List<StockBook> currentBooks = stockManager.getBooks();

		// Generate a random set of new stock books
		Set<StockBook> newBooks = generator.nextSetOfStockBooks(configuration.getNumBooksToAdd());

		// Extract the ISBNs of current books
		Set<Integer> currentISBNs = new HashSet<>();
		for (StockBook book : currentBooks) {
			currentISBNs.add(book.getISBN());
		}

		// Identify books not already in the store
		Set<StockBook> booksToAdd = new HashSet<>();
		for (StockBook book : newBooks) {
			if (!currentISBNs.contains(book.getISBN())) {
				booksToAdd.add(book);
			}
		}

		// Add the new books to the store
		stockManager.addBooks(booksToAdd);
	}

    /**
     * Runs the stock replenishment interaction
     * 
     * @throws BookStoreException
     */
	private void runFrequentStockManagerInteraction() throws BookStoreException {
		StockManager stockManager = configuration.getStockManager();

		// Fetch all books from the store
		List<StockBook> currentBooks = stockManager.getBooks();

		// Select books with the smallest quantities in stock
		int numBooksToRestock = configuration.getNumBooksWithLeastCopies();
		currentBooks.sort(Comparator.comparingInt(StockBook::getNumCopies));

		Set<BookCopy> booksToRestock = new HashSet<>();
		for (int i = 0; i < Math.min(numBooksToRestock, currentBooks.size()); i++) {
			StockBook book = currentBooks.get(i);
			booksToRestock.add(new BookCopy(book.getISBN(), configuration.getNumBookCopiesToBuy()));
		}

		// Restock the selected books
		stockManager.addCopies(booksToRestock);
	}

    /**
     * Runs the customer interaction
     * 
     * @throws BookStoreException
     */
	private void runFrequentBookStoreInteraction() throws BookStoreException {
		BookStore bookStore = configuration.getBookStore();
		BookSetGenerator generator = configuration.getBookSetGenerator();

		List<Book> editorPicks = bookStore.getEditorPicks(configuration.getNumEditorPicksToGet());

		Set<Integer> editorPickISBNs = new HashSet<>();
		for (Book book : editorPicks) {
			editorPickISBNs.add(book.getISBN());
		}

		int numBooksToBuy = configuration.getNumBooksToBuy();
		Set<Integer> booksToBuy = generator.sampleFromSetOfISBNs(editorPickISBNs, numBooksToBuy);

		Set<BookCopy> bookCopiesToBuy = new HashSet<>();
		for (Integer isbn : booksToBuy) {
			bookCopiesToBuy.add(new BookCopy(isbn, configuration.getNumBookCopiesToBuy()));
		}

		bookStore.buyBooks(bookCopiesToBuy);
	}
}
