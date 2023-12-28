public class Book {
	private final String title, author;
	private double price;

    public Book(String title, String author, double price) {
    	this.title = title;
    	this.author = author;
    	this.price = price;
    }
    
    public String getTitle() {
    	return title;
    }
    public String getAuthor() {
    	return author;
    }
    public double getPrice() {
    	return price;
    }
    
    public void setPrice(double newPrice) {
    	price = newPrice;
    }
    
    @Override
    public String toString() {
    	return String.format("Book[@:%02x;title=%s;Author=%s;Price=%.2f]",hashCode(), title,author,price);
    }
    
}