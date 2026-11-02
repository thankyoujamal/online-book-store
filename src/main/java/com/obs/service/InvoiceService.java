package com.obs.service;

import com.obs.dao.OrderDao;
import com.obs.db.Database;
import com.obs.exception.ConflictException;
import com.obs.exception.NotFoundException;
import com.obs.model.Order;
import com.obs.model.OrderItem;
import com.obs.model.OrderStatus;
import com.obs.model.PaymentMethod;
import com.obs.model.PaymentStatus;
import com.obs.model.SessionUser;
import com.obs.util.Pdf;
import com.obs.web.Download;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Builds the PDF invoice for a paid order. Customers get their own orders; admins get any. */
public final class InvoiceService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static final int INK = 0x1d2430;
    private static final int MUTED = 0x5b6472;
    private static final int ACCENT = 0x1f3a5f;
    private static final int RULE = 0xd5dbe3;
    private static final int SHADE = 0xf3f5f8;
    private static final int PAID = 0x2f7d4f;
    private static final int PAID_TINT = 0xe3f1e8;

    private static final float LEFT = 48;
    private static final float RIGHT = Pdf.WIDTH - 48;
    private static final float BOTTOM_LIMIT = Pdf.HEIGHT - 110;

    private final Database db;
    private final OrderDao orders;
    private final String storeName;
    private final String storeAddress;
    private final String storeEmail;

    public InvoiceService(Database db, OrderDao orders, String storeName, String storeAddress, String storeEmail) {
        this.db = db;
        this.orders = orders;
        this.storeName = storeName;
        this.storeAddress = storeAddress;
        this.storeEmail = storeEmail;
    }

    public Download invoice(SessionUser user, int orderId, boolean download) throws SQLException {
        Order order = db.withConnection(c -> orders.findById(c, orderId))
                .filter(o -> user.isAdmin() || o.userId() == user.id())
                .orElseThrow(() -> new NotFoundException("Order #" + orderId + " was not found."));
        if (order.paymentStatus() != PaymentStatus.PAID) {
            throw new ConflictException(order.paymentMethod() == PaymentMethod.COD
                    ? "The invoice is ready once the order is delivered and paid."
                    : "The invoice is ready once the payment is received.");
        }
        String fileName = "Invoice-" + orderNo(order.id()) + ".pdf";
        return new Download(fileName, "application/pdf", render(order), !download);
    }

    static String invoiceNo(Order order) {
        return "INV-" + paidOn(order).getYear() + "-" + String.format("%05d", order.id());
    }

    private byte[] render(Order order) {
        Pdf pdf = new Pdf("Invoice " + invoiceNo(order));
        header(pdf, order);

        // Seller and customer on the left, invoice details on the right.
        float y = 132;
        float left = block(pdf, LEFT, y, "SOLD BY", storeName, storeAddress, storeEmail);
        left = block(pdf, LEFT, left + 18, "BILL TO", order.shippingName(), order.shippingAddress(),
                "Phone " + order.shippingPhone());

        float right = y;
        float labelX = 330;
        pdf.text(labelX, right, "INVOICE DETAILS", 8, true, MUTED);
        right += 16;
        String[][] details = {
                {"Invoice no.", invoiceNo(order)},
                {"Invoice date", DATE.format(paidOn(order))},
                {"Order no.", orderNo(order.id())},
                {"Order date", DATE.format(order.createdAt())},
                {"Payment", paymentText(order)},
                {"Payment ref.", order.paymentRef() == null ? "-" : order.paymentRef()},
        };
        for (String[] row : details) {
            pdf.text(labelX, right, row[0], 9, false, MUTED);
            pdf.textRight(RIGHT, right, Pdf.fit(row[1], RIGHT - labelX - 80, 9, true), 9, true, INK);
            right += 15;
        }

        y = Math.max(left, right) + 22;
        y = items(pdf, order, y);
        totals(pdf, order, y);
        return pdf.toBytes();
    }

    private void header(Pdf pdf, Order order) {
        pdf.box(0, 0, Pdf.WIDTH, 92, ACCENT);
        pdf.text(LEFT, 50, Pdf.fit(storeName, 300, 20, true), 20, true, 0xffffff);
        pdf.text(LEFT, 68, Pdf.fit(storeEmail, 300, 9, false), 9, false, 0xc9d5e4);
        pdf.textRight(RIGHT, 50, "INVOICE", 20, true, 0xffffff);
        pdf.textRight(RIGHT, 68, invoiceNo(order), 9, false, 0xc9d5e4);
        footer(pdf);
    }

    private void footer(Pdf pdf) {
        float y = Pdf.HEIGHT - 52;
        pdf.line(LEFT, y, RIGHT, y, 0.5f, RULE);
        pdf.text(LEFT, y + 16, "Thank you for shopping with " + storeName + ".", 8.5f, true, INK);
        pdf.text(LEFT, y + 28, "This is a computer-generated invoice and does not need a signature.", 8, false, MUTED);
        pdf.textRight(RIGHT, y + 16, "Page " + pdf.pageCount(), 8, false, MUTED);
    }

    /** Draws a labelled block of wrapped lines and returns the y below it. */
    private static float block(Pdf pdf, float x, float y, String label, String title, String... lines) {
        float width = 250;
        pdf.text(x, y, label, 8, true, MUTED);
        y += 16;
        pdf.text(x, y, Pdf.fit(title, width, 10.5f, true), 10.5f, true, INK);
        for (String line : lines) {
            for (String part : Pdf.wrap(line, width, 9, false)) {
                y += 13;
                pdf.text(x, y, part, 9, false, INK);
            }
        }
        return y;
    }

    private float items(Pdf pdf, Order order, float y) {
        y = tableHeader(pdf, y);
        int n = 0;
        for (OrderItem item : order.items()) {
            if (y + 34 > BOTTOM_LIMIT) {
                pdf.newPage();
                footer(pdf);
                y = tableHeader(pdf, 56);
            }
            n++;
            float base = y + 15;
            pdf.text(LEFT + 6, base, String.valueOf(n), 9, false, MUTED);
            pdf.text(LEFT + 30, base, Pdf.fit(item.title(), 262, 9.5f, true), 9.5f, true, INK);
            pdf.text(LEFT + 30, base + 12, Pdf.fit(item.author(), 262, 8.5f, false), 8.5f, false, MUTED);
            pdf.textRight(375, base, String.valueOf(item.quantity()), 9.5f, false, INK);
            pdf.textRight(460, base, money(item.unitPrice()), 9.5f, false, INK);
            pdf.textRight(RIGHT - 6, base,
                    money(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))), 9.5f, true, INK);
            y += 34;
            pdf.line(LEFT, y, RIGHT, y, 0.5f, RULE);
        }
        return y;
    }

    private static float tableHeader(Pdf pdf, float y) {
        pdf.box(LEFT, y, RIGHT - LEFT, 22, SHADE);
        float base = y + 14.5f;
        pdf.text(LEFT + 6, base, "#", 8.5f, true, MUTED);
        pdf.text(LEFT + 30, base, "ITEM", 8.5f, true, MUTED);
        pdf.textRight(375, base, "QTY", 8.5f, true, MUTED);
        pdf.textRight(460, base, "UNIT PRICE", 8.5f, true, MUTED);
        pdf.textRight(RIGHT - 6, base, "AMOUNT", 8.5f, true, MUTED);
        return y + 22;
    }

    private void totals(Pdf pdf, Order order, float y) {
        if (y + 120 > BOTTOM_LIMIT) {
            pdf.newPage();
            footer(pdf);
            y = 56;
        }
        float labelX = 360;
        y += 22;
        pdf.text(labelX, y, "Subtotal", 9.5f, false, MUTED);
        pdf.textRight(RIGHT - 6, y, money(order.subtotal()), 9.5f, false, INK);
        y += 16;
        pdf.text(labelX, y, "Delivery", 9.5f, false, MUTED);
        pdf.textRight(RIGHT - 6, y, order.shipping().signum() == 0 ? "Free" : money(order.shipping()), 9.5f, false, INK);
        y += 10;
        pdf.line(labelX, y, RIGHT, y, 0.75f, INK);
        y += 18;
        pdf.text(labelX, y, "Total", 12, true, INK);
        pdf.textRight(RIGHT - 6, y, money(order.total()), 12, true, INK);
        y += 16;
        pdf.text(labelX, y, "Amount paid", 9.5f, false, PAID);
        pdf.textRight(RIGHT - 6, y, money(order.total()), 9.5f, true, PAID);

        // Paid (or cancelled) stamp on the left of the totals.
        boolean cancelled = order.status() == OrderStatus.CANCELLED;
        String stamp = cancelled ? "CANCELLED" : "PAID";
        float stampTop = y - 60;
        pdf.box(LEFT, stampTop, Pdf.width(stamp, 16, true) + 28, 30, cancelled ? SHADE : PAID_TINT);
        pdf.text(LEFT + 14, stampTop + 21, stamp, 16, true, cancelled ? MUTED : PAID);
        float note = stampTop + 46;
        pdf.text(LEFT, note, "Paid on " + DATE.format(paidOn(order)) + ".", 8.5f, false, MUTED);
        if (cancelled) {
            note += 12;
            pdf.text(LEFT, note, "This order was cancelled after payment.", 8.5f, false, MUTED);
        }
        pdf.text(LEFT, note + 12, "Amounts in Indian rupees (Rs.).", 8.5f, false, MUTED);
    }

    private static String paymentText(Order order) {
        if (order.paymentMethod() == PaymentMethod.COD) {
            return "Cash on delivery";
        }
        String gateway = order.paymentGateway() == null ? "" : switch (order.paymentGateway()) {
            case "DEMO" -> " (DemoPay test)";
            case "RAZORPAY" -> " (Razorpay)";
            default -> " (" + order.paymentGateway() + ")";
        };
        return "Online" + gateway;
    }

    private static LocalDateTime paidOn(Order order) {
        return order.paidAt() != null ? order.paidAt() : order.createdAt();
    }

    private static String orderNo(int id) {
        return "OBS-" + String.format("%05d", id);
    }

    private static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "Rs. " + format.format(amount);
    }
}
