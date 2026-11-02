package com.obs.web.controller;

import com.obs.exception.ValidationException;
import com.obs.service.CartService;
import com.obs.service.DemoGateway;
import com.obs.service.InvoiceService;
import com.obs.service.OrderService;
import com.obs.service.PaymentService;
import com.obs.web.Access;
import com.obs.web.Reply;
import com.obs.web.Router;

/** Cart and order history for the signed-in customer. */
public final class CartController {

    private final CartService cart;
    private final OrderService orders;
    private final PaymentService payments;
    private final InvoiceService invoices;
    /** Null unless the demo gateway is the active gateway. */
    private final DemoGateway demoGateway;

    public CartController(CartService cart, OrderService orders, PaymentService payments, InvoiceService invoices,
                          DemoGateway demoGateway) {
        this.cart = cart;
        this.orders = orders;
        this.payments = payments;
        this.invoices = invoices;
        this.demoGateway = demoGateway;
    }

    public void register(Router router) {
        router.get("/api/cart", Access.USER, req -> cart.get(req.user().id()));
        router.post("/api/cart/items", Access.USER, req -> {
            int bookId = req.intParam("bookId", 0);
            if (bookId < 1) {
                throw new ValidationException("Choose a book to add.");
            }
            return cart.add(req.user().id(), bookId, req.intParam("quantity", 1));
        });
        router.put("/api/cart/items/{bookId}", Access.USER, req ->
                cart.setQuantity(req.user().id(), req.pathInt("bookId"), req.intParam("quantity", 1)));
        router.delete("/api/cart/items/{bookId}", Access.USER, req ->
                cart.remove(req.user().id(), req.pathInt("bookId")));

        router.get("/api/orders", Access.USER, req -> orders.history(req.user().id()));
        router.post("/api/orders", Access.USER, req -> Reply.created(orders.place(
                req.user().id(),
                req.param("paymentMethod"),
                req.param("shippingName"),
                req.param("shippingPhone"),
                req.param("shippingAddress"))));
        // Paid orders only; ?download=1 saves the PDF instead of opening it in the browser.
        router.get("/api/orders/{id}/invoice", Access.USER, req ->
                invoices.invoice(req.user(), req.pathInt("id"), "1".equals(req.param("download"))));

        router.get("/api/payments/config", Access.PUBLIC, req -> payments.config());
        router.post("/api/orders/{id}/payment", Access.USER, req -> payments.start(req.user(), req.pathInt("id")));
        router.post("/api/orders/{id}/payment/verify", Access.USER, req -> payments.verify(
                req.user().id(),
                req.pathInt("id"),
                req.param("gatewayOrderId"),
                req.param("paymentId"),
                req.param("signature")));

        if (demoGateway != null) {
            // Plays the part of the gateway's own servers; the store still verifies the signature it returns.
            router.post("/api/demo-gateway/charge", Access.USER, req ->
                    demoGateway.charge(req.param("gatewayOrderId"), req.param("method"), req.formFields()));
        }
    }
}
