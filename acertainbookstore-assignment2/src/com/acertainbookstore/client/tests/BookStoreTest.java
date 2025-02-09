package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.SingleLockConcurrentCertainBookStore;
import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.business.TwoLevelLockingConcurrentCertainBookStore;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * {@link BookStoreTest} tests the {@link BookStore} interface.
 * 
 * @see BookStore
 */
public class BookStoreTest {

	/** The Constant TEST_ISBN. */
	private static final int TEST_ISBN = 3044560;

	/** The Constant NUM_COPIES. */
	private static final int NUM_COPIES = 5;

	/** The local test. */
	private static boolean localTest = true;

	/** Single lock test */
	private static boolean singleLock = true;

	
	/** The store manager. */
	private static StockManager storeManager;

	/** The client. */
	private static BookStore client;

	/**
	 * Sets the up before class.
	 */
	@BeforeClass
	public static void setUpBeforeClass() {
		try {
			String localTestProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
			localTest = (localTestProperty != null) ? Boolean.parseBoolean(localTestProperty) : localTest;
			
			String singleLockProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_SINGLE_LOCK);
			singleLock = (singleLockProperty != null) ? Boolean.parseBoolean(singleLockProperty) : singleLock;
			System.out.println("Testing local test: " + localTest);
			System.out.println("Testing single lock: " + singleLock);
			if (localTest) {
				if (singleLock) {
					SingleLockConcurrentCertainBookStore store = new SingleLockConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				} else {
					TwoLevelLockingConcurrentCertainBookStore store = new TwoLevelLockingConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				}
			} else {
				storeManager = new StockManagerHTTPProxy("http://localhost:8081/stock");
				client = new BookStoreHTTPProxy("http://localhost:8081");
			}

			storeManager.removeAllBooks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to add some books.
	 *
	 * @param isbn
	 *            the isbn
	 * @param copies
	 *            the copies
	 * @throws BookStoreException
	 *             the book store exception
	 */
	public void addBooks(int isbn, int copies) throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook book = new ImmutableStockBook(isbn, "Test of Thrones", "George RR Testin'", (float) 10, copies, 0, 0,
				0, false);
		booksToAdd.add(book);
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Helper method to get the default book used by initializeBooks.
	 *
	 * @return the default book
	 */
	public StockBook getDefaultBook() {
		return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit", "JK Unit", (float) 10, NUM_COPIES, 0, 0, 0,
				false);
	}

	/**
	 * Method to add a book, executed before every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Before
	public void initializeBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(getDefaultBook());
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Method to clean up the book store, execute after every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@After
	public void cleanupBooks() throws BookStoreException {
		storeManager.removeAllBooks();
	}

	/**
	 * Tests basic buyBook() functionality.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyAllCopiesDefaultBook() throws BookStoreException {
		// Set of books to buy
		Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

		// Try to buy books
		client.buyBooks(booksToBuy);

		List<StockBook> listBooks = storeManager.getBooks();
		assertTrue(listBooks.size() == 1);
		StockBook bookInList = listBooks.get(0);
		StockBook addedBook = getDefaultBook();

		assertTrue(bookInList.getISBN() == addedBook.getISBN() && bookInList.getTitle().equals(addedBook.getTitle())
				&& bookInList.getAuthor().equals(addedBook.getAuthor()) && bookInList.getPrice() == addedBook.getPrice()
				&& bookInList.getNumSaleMisses() == addedBook.getNumSaleMisses()
				&& bookInList.getAverageRating() == addedBook.getAverageRating()
				&& bookInList.getNumTimesRated() == addedBook.getNumTimesRated()
				&& bookInList.getTotalRating() == addedBook.getTotalRating()
				&& bookInList.isEditorPick() == addedBook.isEditorPick());
	}

	/**
	 * Tests that books with invalid ISBNs cannot be bought.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyInvalidISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with invalid ISBN.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(-1, 1)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that books can only be bought if they are in the book store.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNonExistingISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with ISBN which does not exist.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(100000, 10)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy more books than there are copies.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyTooManyBooks() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy more copies than there are in store.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy a negative number of books.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a negative number of copies.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that all books can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetBooks() throws BookStoreException {
		Set<StockBook> booksAdded = new HashSet<StockBook>();
		booksAdded.add(getDefaultBook());

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		booksAdded.addAll(booksToAdd);

		storeManager.addBooks(booksToAdd);

		// Get books in store.
		List<StockBook> listBooks = storeManager.getBooks();

		// Make sure the lists equal each other.
		assertTrue(listBooks.containsAll(booksAdded) && listBooks.size() == booksAdded.size());
	}

	/**
	 * Tests that a list of books with a certain feature can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetCertainBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		storeManager.addBooks(booksToAdd);

		// Get a list of ISBNs to retrieved.
		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);

		// Get books with that ISBN.
		List<Book> books = client.getBooks(isbnList);

		// Make sure the lists equal each other
		assertTrue(books.containsAll(booksToAdd) && books.size() == booksToAdd.size());
	}

	/**
	 * Tests that books cannot be retrieved if ISBN is invalid.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetInvalidIsbn() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Make an invalid ISBN.
		HashSet<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN); // valid
		isbnList.add(-1); // invalid

		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.getBooks(isbnList);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tear down after class.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws BookStoreException {
		storeManager.removeAllBooks();

		if (!localTest) {
			((BookStoreHTTPProxy) client).stop();
			((StockManagerHTTPProxy) storeManager).stop();
		}
	}

	@Test
	public void testConcurrentBuyAndAddCopies() throws BookStoreException, InterruptedException {
		if (!localTest) { // only implement concurrency in local test
			return;
		}

		final int numOperations = 10000;  // should be greater than 2
		final int initialCopies = NUM_COPIES * numOperations; // sufficient stock
		final Set<BookCopy> booksToBuy = new HashSet<>();
		final Set<BookCopy> booksToAddCopies = new HashSet<>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1));
		booksToAddCopies.add(new BookCopy(TEST_ISBN, 1));

		// add initial stock
		storeManager.addCopies(booksToAddCopies.stream().map(
				copy -> new BookCopy(copy.getISBN(), initialCopies - NUM_COPIES)
				).collect(Collectors.toSet()));

		Thread client1 = new Thread(() -> { // consumer
			try {
				for (int i = 0; i < numOperations; i++) {
					client.buyBooks(booksToBuy);
				}
			} catch (BookStoreException e) {
				fail("Client 1 encountered an exception: " + e.getMessage());
			}
		});

		Thread client2 = new Thread(() -> { // producer
			try {
				for (int i = 0; i < numOperations; i++) {
					storeManager.addCopies(booksToAddCopies);
				}
			} catch (BookStoreException e) {
				fail("Client 2 encountered an exception: " + e.getMessage());
			}
		});

		client1.start();
		client2.start();

		client1.join();
		client2.join();

		List<StockBook> booksInStore = storeManager.getBooks();
        assertEquals("Books in store should contain exactly one book", 1, booksInStore.size());
		StockBook book = booksInStore.get(0);

        assertEquals("Final number of copies should match the initial number", initialCopies, book.getNumCopies());
	}

	/**
	 * One read one write
	 * @throws InterruptedException
	 */
	@Test
	public void testConcurrentSnapshot() throws InterruptedException {
		if (!localTest) { // only implement concurrency in local test
			return;
		}

		final int numOperations = 10000;
		final int initialCopies = NUM_COPIES; // by the function with @Before
		final Set<BookCopy> booksToBuy = new HashSet<>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

		Thread client1 = new Thread(() -> {
			for (int i = 0; i < numOperations; i++) {
				try {
					client.buyBooks(booksToBuy);
					storeManager.addCopies(booksToBuy);
				} catch (BookStoreException ignored) { }
			}
		});

		Thread client2 = new Thread(() -> {
			for (int i = 0; i < numOperations; i++) {
				try {
					List<StockBook> books = storeManager.getBooks();
					assertEquals(1, books.size());
					int copies = books.get(0).getNumCopies();
					assertTrue("In-consistent snapshot: " + copies,
						copies == initialCopies
							  || copies == initialCopies - NUM_COPIES );
				} catch (BookStoreException ignored) { }
			}
		});

		client1.start();
		client2.start();

		client1.join();
		client2.join();
	}

	/**
	 * many threads writing on the same book map
	 * @throws InterruptedException
	 * @throws BookStoreException
	 */
	@Test
	public void testConcurrentAddAndRemoveBooksWithQPS() throws InterruptedException, BookStoreException {
		if (!localTest) {
			return;
		}

		final int qps = 1000;
		final int durationInSeconds = 1; // duration = 2 seconds
		final int numOperations = qps * durationInSeconds;

		Thread adder = new Thread(() -> {
			try {
				for (int i = 1; i <= numOperations; i++) {
					storeManager.addBooks(Set.of(new ImmutableStockBook(
							TEST_ISBN + i, "Book " + i, "Author", 50, NUM_COPIES, 0, 0, 0, false)));

					Thread.sleep(1000/qps);
				}
			} catch (BookStoreException | InterruptedException e) {
				fail("Adder encountered an exception: " + e.getMessage());
			}
		});

		Thread remover = new Thread(() -> {
			try {
				for (int i = 0; i < numOperations; i++) {
					storeManager.removeAllBooks();

					// Ensure QPS rate by sleeping for 10 ms between operations (1000 ms / 100 QPS)
					Thread.sleep(10);
				}
			} catch (BookStoreException | InterruptedException e) {
				fail("Remover encountered an exception: " + e.getMessage());
			}
		});

		adder.start();
		remover.start();

		adder.join();
		remover.join();

		List<StockBook> remainingBooks = storeManager.getBooks();
		assertTrue("No partial state should exist", remainingBooks.isEmpty() || remainingBooks.size() <= numOperations);
	}

	/**
	 * Tests potential deadlock by having multiple threads acquire locks in different orders.
	 *
	 * @throws InterruptedException
	 * @throws BookStoreException
	 */
	@Test
	public void testPotentialDeadlock() throws InterruptedException, BookStoreException {
		if (!localTest) {
			return;
		}

		final int isbn1 = TEST_ISBN + 1;
		final int isbn2 = TEST_ISBN + 2;
		addBooks(isbn1, NUM_COPIES);
		addBooks(isbn2, NUM_COPIES);

		Thread thread1 = new Thread(() -> {
			try {
				synchronized (storeManager) {
					Set<BookCopy> booksToBuy = new HashSet<>();
					booksToBuy.add(new BookCopy(isbn1, 1));
					client.buyBooks(booksToBuy);
					Thread.sleep(50); // Simulate some work
					booksToBuy.clear();
					booksToBuy.add(new BookCopy(isbn2, 1));
					client.buyBooks(booksToBuy);
				}
			} catch (Exception e) {
				fail("Thread 1 encountered an exception: " + e.getMessage());
			}
		});

		Thread thread2 = new Thread(() -> {
			try {
				synchronized (storeManager) {
					Set<BookCopy> booksToBuy = new HashSet<>();
					booksToBuy.add(new BookCopy(isbn2, 1));
					client.buyBooks(booksToBuy);
					Thread.sleep(50); // Simulate some work
					booksToBuy.clear();
					booksToBuy.add(new BookCopy(isbn1, 1));
					client.buyBooks(booksToBuy);
				}
			} catch (Exception e) {
				fail("Thread 2 encountered an exception: " + e.getMessage());
			}
		});

		thread1.start();
		thread2.start();

		thread1.join();
		thread2.join();

		// Verify final state consistency
		List<StockBook> books = storeManager.getBooks();
		for (StockBook book : books) {
			assertTrue("Book copies should be non-negative", book.getNumCopies() >= 0);
		}
	}

}
