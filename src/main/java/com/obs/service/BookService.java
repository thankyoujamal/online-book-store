package com.obs.service;

import com.obs.dao.BookDao;
import com.obs.dao.CategoryDao;
import com.obs.db.Database;
import com.obs.exception.ConflictException;
import com.obs.exception.NotFoundException;
import com.obs.exception.ValidationException;
import com.obs.model.Book;
import com.obs.model.BookForm;
import com.obs.model.BookQuery;
import com.obs.model.Category;
import com.obs.model.Page;
import com.obs.util.Validator;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Year;
import java.util.List;
import java.util.Map;

public final class BookService {

    private final Database db;
    private final BookDao books;
    private final CategoryDao categories;

    public BookService(Database db, BookDao books, CategoryDao categories) {
        this.db = db;
        this.books = books;
        this.categories = categories;
    }

    public List<Category> categories() throws SQLException {
        return db.withConnection(categories::findAllWithCounts);
    }

    public Page<Book> search(BookQuery query) throws SQLException {
        return db.withConnection(c -> books.search(c, query));
    }

    public Book get(int id) throws SQLException {
        return db.withConnection(c -> books.findById(c, id))
                .orElseThrow(() -> new NotFoundException("This book is no longer in the catalogue."));
    }

    public Book create(Map<String, String> input) throws SQLException {
        BookForm form = parse(input);
        return db.withConnection(c -> {
            checkReferences(c, form, null);
            int id = books.insert(c, form);
            return books.findById(c, id).orElseThrow();
        });
    }

    public Book update(int id, Map<String, String> input) throws SQLException {
        BookForm form = parse(input);
        return db.withConnection(c -> {
            if (books.findById(c, id).isEmpty()) {
                throw new NotFoundException("Book #" + id + " does not exist. It may have been deleted.");
            }
            checkReferences(c, form, id);
            books.update(c, id, form);
            return books.findById(c, id).orElseThrow();
        });
    }

    public void delete(int id) throws SQLException {
        boolean deleted = db.withConnection(c -> books.delete(c, id));
        if (!deleted) {
            throw new NotFoundException("Book #" + id + " does not exist. It may have been deleted already.");
        }
    }

    private void checkReferences(java.sql.Connection c, BookForm form, Integer excludeId) throws SQLException {
        if (!categories.exists(c, form.categoryId())) {
            throw new ValidationException("Please correct the highlighted fields.",
                    Map.of("categoryId", "Choose a category from the list."));
        }
        if (books.isbnTaken(c, form.isbn(), excludeId)) {
            throw new ConflictException("Another book already uses ISBN " + form.isbn() + ".");
        }
    }

    private static BookForm parse(Map<String, String> in) {
        Validator v = new Validator();
        String isbn = v.isbn("isbn", in.get("isbn"));
        String title = v.text("title", in.get("title"), 1, 200, true);
        String subtitle = v.text("subtitle", in.get("subtitle"), 0, 200, false);
        String author = v.text("author", in.get("author"), 2, 200, true);
        String edition = v.text("edition", in.get("edition"), 0, 40, false);
        Integer categoryId = v.integer("categoryId", in.get("categoryId"), 1, Integer.MAX_VALUE, true);
        String description = v.text("description", in.get("description"), 0, 4000, false);
        BigDecimal price = v.money("price", in.get("price"), true);
        BigDecimal listPrice = v.money("listPrice", in.get("listPrice"), false);
        Integer stock = v.integer("stock", in.get("stock"), 0, 100_000, true);
        String color = v.hexColor("coverColor", in.getOrDefault("coverColor", "#2d5d8a"));
        Integer year = v.integer("publishedYear", in.get("publishedYear"), 1450, Year.now().getValue() + 1, false);
        if (price != null && listPrice != null) {
            v.check(listPrice.compareTo(price) > 0, "listPrice",
                    "List price must be higher than the selling price, or leave it empty.");
        }
        v.throwIfInvalid();
        return new BookForm(isbn, title, subtitle, author, edition, categoryId, description,
                price, listPrice, stock, color, year);
    }
}
