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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.utils.BookStoreUtility;

/** {@link SingleLockConcurrentCertainBookStore} implements the {@link BookStore} and
 * {@link StockManager} functionalities.
 * 
 * @see BookStore
 * @see StockManager
 */
public class SingleLockConcurrentCertainBookStore implements BookStore, StockManager {

	/** The mapping of books from ISBN to {@link BookStoreBook}. */
	private Map<Integer, BookStoreBook> bookMap = null;
	private final ReentrantReadWriteLock lock;

	/**
	 * Instantiates a new {@link SingleLockConcurrentCertainBookStore}.
	 */
	public SingleLockConcurrentCertainBookStore() {
		// Constructors are not synchronized
		bookMap = new HashMap<>();
		lock = new ReentrantReadWriteLock();
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
		lock.readLock().lock();
		try {
			if (bookMap.containsKey(isbn)) {// Check if the book is not in stock
				throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.DUPLICATED);
			}
		} finally {
			lock.readLock().unlock();
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
		if (BookStoreUtility.isInvalidISBN(ISBN)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
		}
		lock.readLock().lock();
		try {
			if (!bookMap.containsKey(ISBN)) {// Check if the book is in stock
				throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
			}
		} finally {
			lock.readLock().unlock();
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
		for (StockBook book : bookSet) {
			validate(book); // read lock inside for bookMap related operations
		}
		lock.writeLock().lock();
		try {
			for (StockBook book : bookSet) {
				bookMap.put(book.getISBN(), new BookStoreBook(book));
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addCopies(java.util.Set)
	 */
	public void addCopies(Set<BookCopy> bookCopiesSet) throws BookStoreException {
		if (bookCopiesSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		int isbn;
		int numCopies;

		for (BookCopy bookCopy : bookCopiesSet) {
			validate(bookCopy);
		}
		lock.writeLock().lock();
		try {
			BookStoreBook book;

			// Update the number of copies
			for (BookCopy bookCopy : bookCopiesSet) {
				isbn = bookCopy.getISBN();
				numCopies = bookCopy.getNumCopies();
				book = bookMap.get(isbn);
				book.addCopies(numCopies);
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooks()
	 */
	public List<StockBook> getBooks() {
		lock.readLock().lock();
		try {
			return bookMap.values().stream()
					.map(book -> book.immutableStockBook())
					.collect(Collectors.toList());
		} finally {
			lock.readLock().unlock();
		}

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
		for (BookEditorPick pick : editorPicks) {
			validate(pick);
		}
		lock.writeLock().lock();
		try {
			for (BookEditorPick pick : editorPicks) {
				bookMap.get(pick.getISBN()).setEditorPick(pick.isEditorPick());
			}
		} finally {
			lock.writeLock().unlock();
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

			validate(bookCopyToBuy);
			lock.readLock().lock();
			try {
				book = bookMap.get(isbn);

				if (!book.areCopiesInStore(bookCopyToBuy.getNumCopies())) {
					// If we cannot sell the copies of the book, it is a miss.
					salesMisses.put(isbn, bookCopyToBuy.getNumCopies() - book.getNumCopies());
					saleMiss = true;
				}
			} finally {
				lock.readLock().unlock();
			}
		}

		lock.writeLock().lock();
		try {
			// We throw exception now since we want to see how many books in the
			// order incurred misses which is used by books in demand
			if (saleMiss) {
				for (Map.Entry<Integer, Integer> saleMissEntry : salesMisses.entrySet()) {
					book = bookMap.get(saleMissEntry.getKey());
					book.addSaleMiss(saleMissEntry.getValue());
				}
				throw new BookStoreException(BookStoreConstants.BOOK + BookStoreConstants.NOT_AVAILABLE);
			}

			// Then make the purchase.
			for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
				book = bookMap.get(bookCopyToBuy.getISBN());
				book.buyCopies(bookCopyToBuy.getNumCopies());
			}
		} finally {
			lock.writeLock().unlock();
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

		for (Integer ISBN : isbnSet) {
			validateISBNInStock(ISBN);
		}
		lock.readLock().lock();
		try {
			return isbnSet.stream()
					.map(isbn -> bookMap.get(isbn).immutableStockBook())
					.collect(Collectors.toList());
		} finally {
			lock.readLock().unlock();
		}
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

		// Check that all ISBNs that we rate are there to start with.
		for (Integer ISBN : isbnSet) {
			validateISBNInStock(ISBN);
		}

		lock.readLock().lock();
		try {
			return isbnSet.stream()
					.map(isbn -> bookMap.get(isbn).immutableBook())
					.collect(Collectors.toList());
		} finally {
			lock.readLock().unlock();
		}
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
		lock.readLock().lock();
		try {
			List<BookStoreBook> listAllEditorPicks = bookMap.entrySet().stream()
					.map(pair -> pair.getValue())
					.filter(book -> book.isEditorPick())
					.collect(Collectors.toList());

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
		} finally {
			lock.readLock().unlock();
		}
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
		lock.writeLock().lock();
		try {
			bookMap.clear();
		} finally {
			lock.writeLock().unlock();
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
		lock.readLock().lock();
		try {
			for (Integer ISBN : isbnSet) {
				if (BookStoreUtility.isInvalidISBN(ISBN)) {
					throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
				}

				if (!bookMap.containsKey(ISBN)) {
					throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
				}
			}
		} finally {
			lock.readLock().unlock();
		}

		lock.writeLock().lock();
		try {
			for (int isbn : isbnSet) {
				bookMap.remove(isbn);
			}
		} finally {
			lock.writeLock().unlock();
		}
	}
}
