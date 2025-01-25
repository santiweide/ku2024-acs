package com.acertainbookstore.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.utils.BookStoreUtility;
import com.acertainbookstore.utils.LockManager;

/** {@link TwoLevelLockingConcurrentCertainBookStore} implements the {@link BookStore} and
 * {@link StockManager} functionalities.
 * 
 * @see BookStore
 * @see StockManager
 */
public class TwoLevelLockingConcurrentCertainBookStore implements BookStore, StockManager {

	/** The mapping of books from ISBN to {@link BookStoreBook}. */
	private Map<Integer, BookStoreBook> bookMap = null;

	private final LockManager lockManager = new LockManager();

	/**
	 * Instantiates a new {@link TwoLevelLockingConcurrentCertainBookStore}.
	 */
	public TwoLevelLockingConcurrentCertainBookStore() {
		// Constructors are not synchronized
		bookMap = new HashMap<>();
	}
	
	private void validate(StockBook book) throws BookStoreException {
		int isbn = book.getISBN();
		String bookTitle = book.getTitle();
		String bookAuthor = book.getAuthor();
		int noCopies = book.getNumCopies();
		float bookPrice = book.getPrice();

		if (BookStoreUtility.isInvalidISBN(isbn)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookTitle)) { // Check if the book has valid title
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookAuthor)) { // Check if the book has valid author
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isInvalidNoCopies(noCopies)) { // Check if the book has at least one copy
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookPrice < 0.0) { // Check if the price of the book is valid
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		// all validate are wrapped with read or write locks already
		if (bookMap.containsKey(isbn)) {// Check if the book is not in stock
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.DUPLICATED);
		}
	}	
	
	private void validate(BookCopy bookCopy) throws BookStoreException {
		int isbn = bookCopy.getISBN();
		int numCopies = bookCopy.getNumCopies();

		validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock

		if (BookStoreUtility.isInvalidNoCopies(numCopies)) { // Check if the number of the book copy is larger than zero
			throw new BookStoreException(BookStoreConstants.NUM_COPIES + numCopies + BookStoreConstants.INVALID);
		}
	}
	
	private void validate(BookEditorPick editorPickArg) throws BookStoreException {
		int isbn = editorPickArg.getISBN();
		validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock
	}

	private void validateISBNInStock(Integer ISBN) throws BookStoreException {
		if (BookStoreUtility.isInvalidISBN(ISBN)) {
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
		}

		if (!bookMap.containsKey(ISBN)) { // no extra locks, all locks are outside.
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addBooks(java.util.Set)
	 */
	public void addBooks(Set<StockBook> bookSet) throws BookStoreException {
		if (bookSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		lockManager.acquireGlobalWriteLock();
		try {
			// Check if all are there
			for (StockBook book : bookSet) {
				validate(book);
			}

			for (StockBook book : bookSet) {
				int isbn = book.getISBN();
				bookMap.put(isbn, new BookStoreBook(book));
			}
		} finally {
			lockManager.releaseGlobalWriteLock();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addCopies(java.util.Set)
	 */
	public void addCopies(Set<BookCopy> bookCopiesSet) throws BookStoreException {
		int isbn;
		int numCopies;

		if (bookCopiesSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		for (BookCopy bookCopy : bookCopiesSet) {
			isbn = bookCopy.getISBN();
			try {
				lockManager.acquireRowReadLock(isbn);  // Shared lock for validation
				validate(bookCopy);
			} finally {
				lockManager.releaseRowReadLock(isbn);  // Release shared lock
			}
		}

		BookStoreBook book;

		// Update the number of copies
		for (BookCopy bookCopy : bookCopiesSet) {
			isbn = bookCopy.getISBN();
			numCopies = bookCopy.getNumCopies();
			try {
				lockManager.acquireRowWriteLock(isbn);
				book = bookMap.get(isbn);
				book.addCopies(numCopies);
			} finally {
				lockManager.releaseRowWriteLock(isbn);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooks()
	 */
	public List<StockBook> getBooks() {
		List<StockBook> stockBooks = new ArrayList<>();

		try {
			lockManager.acquireGlobalReadLock();  // Intent Shared (IS) lock at table level.

			for (BookStoreBook book : bookMap.values()) {
				stockBooks.add(book.immutableStockBook());
			}
		} finally {
			// Release the table-level shared lock.
			lockManager.releaseGlobalReadLock();
		}

		return stockBooks;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#updateEditorPicks(java.util
	 * .Set)
	 */
	public void updateEditorPicks(Set<BookEditorPick> editorPicks) throws BookStoreException {
		// Check that all ISBNs that we add/remove are there first.
		if (editorPicks == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		for (BookEditorPick editorPickArg : editorPicks) {
			int isbn = editorPickArg.getISBN();
			try {
				lockManager.acquireRowReadLock(isbn);  // Acquire Shared lock for validation
				validate(editorPickArg);  // Validation ensures ISBN is valid and in stock
			} finally {
				lockManager.releaseRowReadLock(isbn);  // Release Shared lock
			}
		}

		for (BookEditorPick editorPickArg : editorPicks) {
			int isbn = editorPickArg.getISBN();
			try {
				lockManager.acquireRowWriteLock(isbn);  // Acquire Exclusive lock for update
				BookStoreBook book = bookMap.get(isbn);
				book.setEditorPick(editorPickArg.isEditorPick());  // Update the editor pick status
			} finally {
				lockManager.releaseRowWriteLock(isbn);  // Release Exclusive lock
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#buyBooks(java.util.Set)
	 */
	public void buyBooks(Set<BookCopy> bookCopiesToBuy) throws BookStoreException {
		if (bookCopiesToBuy == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		// Check that all ISBNs that we buy are there first.
		int isbn;
		BookStoreBook book;
		Boolean saleMiss = false;

		Map<Integer, Integer> salesMisses = new HashMap<>();

		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
			isbn = bookCopyToBuy.getISBN();
			lockManager.acquireRowWriteLock(isbn);
			try {
				// a kind of acting read in the write lock; can be better
				validate(bookCopyToBuy);
				book = bookMap.get(isbn);

				if (!book.areCopiesInStore(bookCopyToBuy.getNumCopies())) {
					// If we cannot sell the copies of the book, it is a miss.
					salesMisses.put(isbn, bookCopyToBuy.getNumCopies() - book.getNumCopies());
					saleMiss = true;
				}
			} finally {
				lockManager.releaseRowWriteLock(isbn);
			}
		}

		// We throw exception now since we want to see how many books in the
		// order incurred misses which is used by books in demand
		if (saleMiss) {
			for (Map.Entry<Integer, Integer> saleMissEntry : salesMisses.entrySet()) {
				lockManager.acquireRowWriteLock(saleMissEntry.getKey());
				try {
					book = bookMap.get(saleMissEntry.getKey());
					book.addSaleMiss(saleMissEntry.getValue());
				} finally {
					lockManager.releaseRowWriteLock(saleMissEntry.getKey());
				}
			}
			throw new BookStoreException(BookStoreConstants.BOOK + BookStoreConstants.NOT_AVAILABLE);
		}

		// Then make the purchase.
		for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
			isbn = bookCopyToBuy.getISBN();
			lockManager.acquireRowWriteLock(isbn);
			try {
				book = bookMap.get(isbn);
				book.buyCopies(bookCopyToBuy.getNumCopies());
			} finally {
				lockManager.releaseRowWriteLock(isbn);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#getBooksByISBN(java.util.
	 * Set)
	 */
	public List<StockBook> getBooksByISBN(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}


		List<StockBook> result = new ArrayList<>();
		for (Integer isbn : isbnSet) {
			try {
				lockManager.acquireRowReadLock(isbn);
				validateISBNInStock(isbn);
				BookStoreBook book = bookMap.get(isbn);
				result.add(book.immutableStockBook());
			} finally {
				lockManager.releaseRowReadLock(isbn);
			}
		}

		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getBooks(java.util.Set)
	 */
	public List<Book> getBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		List<Book> books = new ArrayList<>();
		Set<Integer> isbnToUnlock = new HashSet<>(); // TODO explain in doc
		try {
			for (Integer isbn : isbnSet) {
				lockManager.acquireRowReadLock(isbn);
				validateISBNInStock(isbn);
				isbnToUnlock.add(isbn);
				books.add(bookMap.get(isbn).immutableBook());
			}
		} finally {
			for (Integer isbn : isbnToUnlock) {
				lockManager.releaseRowReadLock(isbn);
			}
		}

		return books;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getEditorPicks(int)
	 */
	public List<Book> getEditorPicks(int numBooks) throws BookStoreException {
		if (numBooks < 0) {
			throw new BookStoreException("numBooks = " + numBooks + ", but it must be positive");
		}

		List<BookStoreBook> listAllEditorPicks = new ArrayList<>();

		for (Map.Entry<Integer, BookStoreBook> entry : bookMap.entrySet()) {
			int isbn = entry.getKey();
			lockManager.acquireRowReadLock(isbn);

			try {
				BookStoreBook book = entry.getValue();
				if (book.isEditorPick()) {
					listAllEditorPicks.add(book);
				}
			} finally {
				lockManager.releaseRowReadLock(isbn);
			}
		}

		// Find numBooks random indices of books that will be picked.
		Random rand = new Random();
		Set<Integer> tobePicked = new HashSet<>();
		int rangePicks = listAllEditorPicks.size();

		if (rangePicks <= numBooks) {

			// We need to add all books.
			for (int i = 0; i < listAllEditorPicks.size(); i++) {
				tobePicked.add(i);
			}
		} else {

			// We need to pick randomly the books that need to be returned.
			int randNum;

			while (tobePicked.size() < numBooks) {
				randNum = rand.nextInt(rangePicks);
				tobePicked.add(randNum);
			}
		}

		// Return all the books by the randomly chosen indices.
		return tobePicked.stream()
				.map(index -> listAllEditorPicks.get(index).immutableBook())
				.collect(Collectors.toList());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getTopRatedBooks(int)
	 */
	@Override
	public List<Book> getTopRatedBooks(int numBooks) throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooksInDemand()
	 */
	@Override
	public List<StockBook> getBooksInDemand() throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#rateBooks(java.util.Set)
	 */
	@Override
	public void rateBooks(Set<BookRating> bookRating) throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#removeAllBooks()
	 */
	public void removeAllBooks() throws BookStoreException {
		lockManager.acquireGlobalWriteLock();
		try {
			bookMap.clear();
		} finally {
			lockManager.releaseGlobalWriteLock();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#removeBooks(java.util.Set)
	 */
	public void removeBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		for (Integer ISBN : isbnSet) {
			if (BookStoreUtility.isInvalidISBN(ISBN)) {
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
			}

			if (!bookMap.containsKey(ISBN)) {
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
			}
		}

		for (int isbn : isbnSet) {
			lockManager.acquireRowWriteLock(isbn);
			try {
				bookMap.remove(isbn);
			} finally {
				lockManager.releaseRowWriteLock(isbn);
			}
		}
	}
}
