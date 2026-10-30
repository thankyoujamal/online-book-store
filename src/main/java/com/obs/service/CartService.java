package com.obs.service;

import com.obs.dao.BookDao;
import com.obs.dao.CartDao;
import com.obs.db.Database;
import com.obs.exception.ConflictException;
import com.obs.exception.NotFoundException;
import com.obs.exception.ValidationException;
import com.obs.model.Book;
import com.obs.model.Cart;
import com.obs.model.CartItem;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public final class CartService {

    public static final int MAX_PER_TITLE = 10;
    static final BigDecimal FREE_SHIPPING_FROM = new BigDecimal("999");
    static final BigDecimal SHIPPING_FEE = new BigDecimal("49");

    private final Database db;
    private final CartDao cart;
    private final BookDao books;

    public CartService(Database db, CartDao cart, BookDao books) {
        this.db = db;
        this.cart = cart;
        this.books = books;
    }

    public Cart get(int userId) throws SQLException {
        return summarise(db.withConnection(c -> cart.findByUser(c, userId)));
    }

    public Cart add(int userId, int bookId, int quantity) throws SQLException {
        requirePositive(quantity);
        return db.inTransaction(c -> {
            Book book = books.findById(c, bookId)
                    .orElseThrow(() -> new NotFoundException("This book is no longer in the catalogue."));
            int wanted = cart.quantityOf(c, userId, bookId) + quantity;
            cart.upsert(c, userId, bookId, checkAvailable(book, wanted));
            return summarise(cart.findByUser(c, userId));
        });
    }

    public Cart setQuantity(int userId, int bookId, int quantity) throws SQLException {
        if (quantity == 0) {
            return remove(userId, bookId);
        }
        requirePositive(quantity);
        return db.inTransaction(c -> {
            if (cart.quantityOf(c, userId, bookId) == 0) {
                throw new NotFoundException("That book is not in your cart.");
            }
            Book book = books.findById(c, bookId)
                    .orElseThrow(() -> new NotFoundException("This book is no longer in the catalogue."));
            cart.upsert(c, userId, bookId, checkAvailable(book, quantity));
            return summarise(cart.findByUser(c, userId));
        });
    }

    public Cart remove(int userId, int bookId) throws SQLException {
        return db.withConnection(c -> {
            cart.remove(c, userId, bookId);
            return summarise(cart.findByUser(c, userId));
        });
    }

    static Cart summarise(List<CartItem> items) {
        BigDecimal subtotal = BigDecimal.ZERO;
        int count = 0;
        for (CartItem item : items) {
            subtotal = subtotal.add(item.lineTotal());
            count += item.quantity();
        }
        BigDecimal shipping = shippingFor(subtotal, count);
        return new Cart(items, count, subtotal, shipping, subtotal.add(shipping), FREE_SHIPPING_FROM);
    }

    static BigDecimal shippingFor(BigDecimal subtotal, int itemCount) {
        if (itemCount == 0 || subtotal.compareTo(FREE_SHIPPING_FROM) >= 0) {
            return BigDecimal.ZERO;
        }
        return SHIPPING_FEE;
    }

    private static int checkAvailable(Book book, int wanted) {
        if (book.stock() == 0) {
            throw new ConflictException("“" + book.title() + "” is out of stock.");
        }
        if (wanted > MAX_PER_TITLE) {
            throw new ConflictException("You can order up to " + MAX_PER_TITLE + " copies of a title.");
        }
        if (wanted > book.stock()) {
            throw new ConflictException("Only " + book.stock() + " "
                    + (book.stock() == 1 ? "copy" : "copies") + " of “" + book.title() + "” left.");
        }
        return wanted;
    }

    private static void requirePositive(int quantity) {
        if (quantity < 1) {
            throw new ValidationException("Quantity must be at least 1.");
        }
    }
}
