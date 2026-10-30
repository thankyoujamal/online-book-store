package com.obs.web.controller;

import com.obs.model.BookQuery;
import com.obs.service.BookService;
import com.obs.service.OrderService;
import com.obs.web.Access;
import com.obs.web.Reply;
import com.obs.web.Router;

public final class AdminController {

    private final BookService books;
    private final OrderService orders;

    public AdminController(BookService books, OrderService orders) {
        this.books = books;
        this.orders = orders;
    }

    public void register(Router router) {
        router.get("/api/admin/books", Access.ADMIN, req -> {
            BookQuery q = BookController.parseQuery(req, 20);
            // The admin table defaults to alphabetical, not popularity.
            if (req.param("sort") == null) {
                q = new BookQuery(q.search(), q.categoryIds(), q.minPrice(), q.maxPrice(), q.inStockOnly(),
                        BookQuery.Sort.TITLE, q.page(), q.pageSize());
            }
            return BookController.page(books.search(q));
        });
        router.post("/api/admin/books", Access.ADMIN, req -> Reply.created(books.create(req.formFields())));
        router.put("/api/admin/books/{id}", Access.ADMIN, req -> books.update(req.pathInt("id"), req.formFields()));
        router.delete("/api/admin/books/{id}", Access.ADMIN, req -> {
            books.delete(req.pathInt("id"));
            return Reply.noContent();
        });

        router.get("/api/admin/orders", Access.ADMIN, req -> orders.recent(100));
        router.put("/api/admin/orders/{id}/status", Access.ADMIN, req ->
                orders.changeStatus(req.pathInt("id"), req.param("status")));
    }
}
