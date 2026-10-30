package com.obs.service;

import com.obs.dao.OrderDao;
import com.obs.db.Database;
import com.obs.exception.ConflictException;
import com.obs.exception.NotFoundException;
import com.obs.exception.ValidationException;
import com.obs.model.Order;
import com.obs.model.PaymentMethod;
import com.obs.model.PaymentStatus;
import com.obs.model.SessionUser;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Online payment for an order that has already been placed:
 * 1. start()  creates (or reuses) a gateway order and returns what checkout needs;
 * 2. verify() checks the gateway's signature server-side and marks the order paid.
 * The browser never decides that an order is paid.
 */
public final class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class.getName());

    private final Database db;
    private final OrderDao orders;
    /** Null when online payment is turned off. */
    private final PaymentGateway gateway;

    public PaymentService(Database db, OrderDao orders, PaymentGateway gateway) {
        this.db = db;
        this.orders = orders;
        this.gateway = gateway;
    }

    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("onlineEnabled", gateway != null);
        body.put("gateway", gateway == null ? null : gateway.name());
        body.put("testMode", gateway != null && gateway.isTestMode());
        return body;
    }

    public Map<String, Object> start(SessionUser user, int orderId) throws SQLException {
        requireGateway();
        Order order = payableOrder(user.id(), orderId);
        long paise = order.total().movePointRight(2).longValueExact();

        String gatewayOrderId = order.gatewayOrderId();
        if (gatewayOrderId == null) {
            // Outside a transaction on purpose: don't hold row locks during a gateway call.
            gatewayOrderId = gateway.createOrder(paise, "OBS-" + String.format("%05d", order.id()));
            String created = gatewayOrderId;
            db.withConnection(c -> {
                orders.setGatewayOrderId(c, orderId, created);
                return null;
            });
        }

        Map<String, Object> prefill = new LinkedHashMap<>();
        prefill.put("name", order.shippingName());
        prefill.put("email", user.email());
        prefill.put("contact", order.shippingPhone().replace(" ", ""));

        Map<String, Object> checkout = new LinkedHashMap<>();
        checkout.put("gateway", gateway.name());
        checkout.put("keyId", gateway.publicKey());
        checkout.put("gatewayOrderId", gatewayOrderId);
        checkout.put("amount", paise);
        checkout.put("currency", "INR");
        checkout.put("orderId", order.id());
        checkout.put("prefill", prefill);
        checkout.put("testMode", gateway.isTestMode());
        return checkout;
    }

    public Order verify(int userId, int orderId, String gatewayOrderId, String paymentId, String signature)
            throws SQLException {
        requireGateway();
        if (paymentId == null || signature == null || gatewayOrderId == null) {
            throw new ValidationException("Payment details are missing. If money was deducted, contact support with your order number.");
        }
        return db.inTransaction(c -> {
            Order order = orders.findById(c, orderId)
                    .filter(o -> o.userId() == userId)
                    .orElseThrow(() -> new NotFoundException("Order #" + orderId + " was not found."));
            if (order.paymentStatus() == PaymentStatus.PAID) {
                return order; // Already confirmed, e.g. a double submit.
            }
            if (!gatewayOrderId.equals(order.gatewayOrderId())
                    || !gateway.verifySignature(order.gatewayOrderId(), paymentId, signature)) {
                LOG.warning("Rejected payment signature for order " + orderId + " (payment " + paymentId + ")");
                throw new ValidationException("We couldn't verify this payment. If money was deducted, it will be refunded by the bank.");
            }
            orders.markPaid(c, orderId, paymentId);
            return orders.findById(c, orderId).orElseThrow();
        });
    }

    private Order payableOrder(int userId, int orderId) throws SQLException {
        Order order = db.withConnection(c -> orders.findById(c, orderId))
                .filter(o -> o.userId() == userId)
                .orElseThrow(() -> new NotFoundException("Order #" + orderId + " was not found."));
        if (order.paymentMethod() != PaymentMethod.ONLINE) {
            throw new ConflictException("This order is cash on delivery.");
        }
        if (order.paymentStatus() == PaymentStatus.PAID) {
            throw new ConflictException("This order is already paid.");
        }
        if (!order.awaitingOnlinePayment()) {
            throw new ConflictException("This order can no longer be paid because it was " + order.status().name().toLowerCase() + ".");
        }
        if (!gateway.name().equals(order.paymentGateway())) {
            throw new ConflictException("This order was started with a different payment gateway. Ask the store to cancel it and order again.");
        }
        return order;
    }

    private void requireGateway() {
        if (gateway == null) {
            throw new ConflictException("Online payment isn't set up on this store.");
        }
    }
}
