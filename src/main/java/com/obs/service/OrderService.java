package com.obs.service;

import com.obs.dao.BookDao;
import com.obs.dao.CartDao;
import com.obs.dao.OrderDao;
import com.obs.db.Database;
import com.obs.exception.ConflictException;
import com.obs.exception.NotFoundException;
import com.obs.exception.ValidationException;
import com.obs.model.Cart;
import com.obs.model.CartItem;
import com.obs.model.Order;
import com.obs.model.OrderItem;
import com.obs.model.OrderStatus;
import com.obs.model.PaymentMethod;
import com.obs.model.PaymentStatus;
import com.obs.model.ShippingDetails;
import com.obs.util.Validator;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OrderService {

    /** Allowed status changes. Cancelled and delivered orders are final. */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.PLACED, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of());

    private final Database db;
    private final OrderDao orders;
    private final CartDao cart;
    private final BookDao books;
    /** Name of the active online gateway, or null when only cash on delivery is offered. */
    private final String onlineGateway;

    public OrderService(Database db, OrderDao orders, CartDao cart, BookDao books, String onlineGateway) {
        this.db = db;
        this.orders = orders;
        this.cart = cart;
        this.books = books;
        this.onlineGateway = onlineGateway;
    }

    /**
     * Turns the user's cart into an order in one transaction: lock cart and book rows,
     * check and reserve stock, write the order, then empty the cart. Online orders start
     * unpaid; {@link PaymentService} marks them paid once the gateway confirms.
     */
    public Order place(int userId, String rawMethod, String name, String phone, String address)
            throws SQLException {
        Validator v = new Validator();
        PaymentMethod method = parseMethod(rawMethod, v);
        ShippingDetails ship = new ShippingDetails(
                v.text("shippingName", name, 2, 80, true),
                v.phone("shippingPhone", phone),
                v.text("shippingAddress", address, 10, 400, true));
        v.throwIfInvalid();

        int orderId = db.inTransaction(c -> {
            List<CartItem> items = cart.findByUserForUpdate(c, userId);
            if (items.isEmpty()) {
                throw new ValidationException("Your cart is empty. Add a book before checking out.");
            }
            List<OrderItem> lines = new ArrayList<>();
            for (CartItem item : items) {
                if (!books.reserveStock(c, item.book().id(), item.quantity())) {
                    int left = item.book().stock();
                    throw new ConflictException(left == 0
                            ? "“" + item.book().title() + "” just sold out. Remove it to continue."
                            : "Only " + left + " of “" + item.book().title() + "” left. Lower the quantity to continue.");
                }
                lines.add(new OrderItem(item.book().id(), item.book().title(), item.book().author(),
                        item.book().price(), item.quantity(), null));
            }
            Cart summary = CartService.summarise(items);
            String gateway = method == PaymentMethod.ONLINE ? onlineGateway : null;
            int id = orders.insert(c, userId, method, gateway, summary.subtotal(), summary.shipping(), summary.total(), ship);
            orders.insertItems(c, id, lines);
            cart.clear(c, userId);
            return id;
        });
        return db.withConnection(c -> orders.findById(c, orderId)).orElseThrow();
    }

    private PaymentMethod parseMethod(String raw, Validator v) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("COD")) {
            return PaymentMethod.COD;
        }
        if (raw.equalsIgnoreCase("ONLINE")) {
            v.check(onlineGateway != null, "paymentMethod",
                    "Online payment isn't set up on this store. Choose cash on delivery.");
            return PaymentMethod.ONLINE;
        }
        v.error("paymentMethod", "Choose a payment method.");
        return PaymentMethod.COD;
    }

    public List<Order> history(int userId) throws SQLException {
        return db.withConnection(c -> orders.findByUser(c, userId));
    }

    public List<Order> recent(int limit) throws SQLException {
        return db.withConnection(c -> orders.findRecent(c, limit));
    }

    public Order changeStatus(int orderId, String rawStatus) throws SQLException {
        OrderStatus next;
        try {
            next = OrderStatus.valueOf(rawStatus == null ? "" : rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown order status: " + rawStatus);
        }
        return db.inTransaction(c -> {
            Order order = orders.findById(c, orderId)
                    .orElseThrow(() -> new NotFoundException("Order #" + orderId + " does not exist."));
            if (!TRANSITIONS.get(order.status()).contains(next)) {
                throw new ConflictException("An order that is " + order.status().name().toLowerCase()
                        + " cannot be marked " + next.name().toLowerCase() + ".");
            }
            if (next == OrderStatus.SHIPPED && order.awaitingOnlinePayment()) {
                throw new ConflictException("This order hasn't been paid yet. Ship it once the online payment arrives.");
            }
            // Cash on delivery is collected when the parcel is handed over.
            if (next == OrderStatus.DELIVERED && order.paymentMethod() == PaymentMethod.COD
                    && order.paymentStatus() == PaymentStatus.UNPAID) {
                orders.markPaid(c, orderId, null);
            }
            if (next == OrderStatus.CANCELLED) {
                for (OrderItem item : order.items()) {
                    if (item.bookId() != null) {
                        books.releaseStock(c, item.bookId(), item.quantity());
                    }
                }
            }
            orders.updateStatus(c, orderId, next);
            return orders.findById(c, orderId).orElseThrow();
        });
    }
}
