import java.util.*;

public class Library {
	public static void main(String[] args) {
		Book[] books = new Book[10];
		books[0] = new Book("A Tale of Two Cities", "Charles Dickens", 35);
		books[1] = new Book("Harry Potter", "J.K. Rowling", 30);
    	books[2] = new Book("Talking to Strangers", "Malcom Gladwell", 42);
    	books[3] = new Book("Three Women", "Lisa Taddeo", 17);
    	books[4] = new Book("Catch and Kill", "Ronan Farrow", 25);
    	
    	System.out.println("books: " + Arrays.toString(books));
    	System.out.printf("%d books exceed the price of $30\n", exceedsPrice(books, 30));
    	System.out.printf("The title of the most expensive book is %s\n", mostExpensiveBook(books));
    	System.out.println(getBookAuthor(books, "Harry Potter"));
    	System.out.println(averagePrice(books));
    	startsWith(books, 'T');
    	System.out.println(Arrays.toString(books));
    	addBook(books, "", "", 4.3);
    	System.out.println(Arrays.toString(books));
    	_fillVoids(books, "", "", 4.3);
    	addBook(books, new Book("e", "e", 42.3));
	}
	private static void _fillVoids(Book[] b) {
		for (int i = 0; i < b.length; i++)
			if (b[i] == null)
				b[i] = new Book("","",-1);
	}
	
	private static int exceedsPrice(Book[] b, double price) {
		return (int) 
			(Arrays.stream(b)
			.filter(x -> x != null)
			.mapToDouble(x -> x.getPrice())
				.filter(x -> x > price)
					.count());
	}
	private static String mostExpensiveBook(Book[] b) {
		return Arrays.stream(b).filter(x -> x != null).max(Comparator.comparingDouble(x -> x.getPrice())).orElse(new Book("", "", -1)).getTitle();
	}
	private static String getBookAuthor(Book[] b, String title) {
		for (Book book : b) 
			if (book.getTitle().equals(title)) return book.getAuthor();
		return null;
	}
	private static double averagePrice(Book[] b) {
		return Arrays.stream(b).filter(x -> x != null).mapToDouble(x -> x.getPrice()).average().orElse(0);
	}
	private static void startsWith(Book[] b, char c) {
		for (Book book : b) {
			if (book == null) continue;
			if (book.getTitle().charAt(0) == c) {
				System.out.println(book);
			}
		}
	}
	private static void addBook(Book[] b, String title, String author, double price) {
		Book toAdd = new Book(title, author, price);
		for (int i = 0; i < b.length; i++) {
			if (b[i] == null) {
				b[i] = toAdd;
				return;
			}
		}
		System.out.println("Warning: Could not add book " + toAdd + " : Insufficient buffer space available.");
	}
}