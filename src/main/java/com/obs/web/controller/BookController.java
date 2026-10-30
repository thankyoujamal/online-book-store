package com.obs.web.controller;

import com.obs.exception.ValidationException;
import com.obs.model.Book;
import com.obs.model.BookQuery;
import com.obs.model.Page;
import com.obs.service.BookService;
import com.obs.web.Access;
import com.obs.web.Request;
import com.obs.web.Router;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BookController {

    private static final int MAX_PAGE_SIZE = 48;

    private final BookService books;

    public BookController(BookService books) {
        this.books = books;
    }

    public void register(Router router) {
        router.get("/api/categories", Access.PUBLIC, req -> books.categories());
        router.get("/api/books", Access.PUBLIC, req -> page(books.search(parseQuery(req, 12))));
        router.get("/api/books/{id}", Access.PUBLIC, req -> books.get(req.pathInt("id")));
    }

    /**
     * Parses catalogue filters: q, category (repeatable id), price ("500-1000", "-500", "1000-"),
     * stock=1, sort, page, size.
     */
    static BookQuery parseQuery(Request req, int defaultSize) {
        String q = req.param("q");
        q = q == null || q.isBlank() ? null : q.strip();
        if (q != null && q.length() > 100) {
            throw new ValidationException("Search text is too long. Use 100 characters or fewer.");
        }

        List<Integer> categoryIds = new ArrayList<>();
        for (String raw : req.params("category")) {
            try {
                categoryIds.add(Integer.parseInt(raw));
            } catch (NumberFormatException e) {
                throw new ValidationException("'" + raw + "' is not a valid category.");
            }
        }

        BigDecimal min = null;
        BigDecimal max = null;
        String price = req.param("price");
        if (price != null && !price.isBlank()) {
            String[] parts = price.split("-", -1);
            try {
                if (parts.length != 2) {
                    throw new NumberFormatException();
                }
                min = parts[0].isBlank() ? null : new BigDecimal(parts[0]);
                max = parts[1].isBlank() ? null : new BigDecimal(parts[1]);
            } catch (NumberFormatException e) {
                throw new ValidationException("Price filter must look like 500-1000.");
            }
        }

        int page = Math.max(1, req.intParam("page", 1));
        int size = Math.min(MAX_PAGE_SIZE, Math.max(1, req.intParam("size", defaultSize)));
        return new BookQuery(q, categoryIds, min, max, "1".equals(req.param("stock")),
                BookQuery.Sort.parse(req.param("sort")), page, size);
    }

    static Map<String, Object> page(Page<Book> page) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.items());
        body.put("total", page.total());
        body.put("page", page.page());
        body.put("pageSize", page.pageSize());
        body.put("totalPages", page.totalPages());
        return body;
    }
}
