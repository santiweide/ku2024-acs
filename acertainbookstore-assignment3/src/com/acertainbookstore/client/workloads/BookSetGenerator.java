package com.acertainbookstore.client.workloads;

import java.util.*;

import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;

/**
 * Helper class to generate stockbooks and isbns modelled similar to Random
 * class
 */
public class BookSetGenerator {
	private Random random;

	public BookSetGenerator() {
		// TODO Auto-generated constructor stub
		this.random = new Random();
	}

	/**
	 * Returns num randomly selected isbns from the input set
	 * 
	 * @param num
	 * @return
	 */
	public Set<Integer> sampleFromSetOfISBNs(Set<Integer> isbns, int num) {
		if (num > isbns.size()) {
			throw new IllegalArgumentException("Requested more ISBNs than available in the input set.");
		}

		List<Integer> isbnList = new ArrayList<>(isbns);
		Collections.shuffle(isbnList, random);

		return new HashSet<>(isbnList.subList(0, num));
	}


	/**
	 * Return num stock books. For now return an ImmutableStockBook
	 * 
	 * @param num
	 * @return
	 */
	public Set<StockBook> nextSetOfStockBooks(int num) {
		Set<StockBook> stockBooks = new HashSet<>();
		Set<Integer> usedIsbns = new HashSet<>();

		while (usedIsbns.size() < num) {
			int isbn = 1000000 + random.nextInt(9000000); // Generate random 7-digit ISBN

			// Ensure ISBN is unique
			if (!usedIsbns.contains(isbn)) {
				String title = "Book " + isbn;
				String author = "Author " + isbn;
				float price = 10 + random.nextFloat() * 90; // Price between 10 and 100
				int numCopies = 350 + random.nextInt(50); // Between 300+0 and +100 copies

				StockBook book = new ImmutableStockBook(isbn, title, author, price, numCopies, 0, 0, 0, false);
				stockBooks.add(book);
				usedIsbns.add(isbn); // Mark the ISBN as used
			}
		}

		return stockBooks;
	}

}
