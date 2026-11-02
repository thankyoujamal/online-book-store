# Online Book Store

A full-stack bookstore built with **Java, JDBC and MySQL** on the backend and plain **HTML, CSS and JavaScript** on the frontend. It uses no framework: the HTTP layer is the JDK's built-in `HttpServer`, and all data access is hand-written JDBC with prepared statements.

## Features

- **Accounts.** Registration and login with PBKDF2-hashed passwords, HttpOnly session cookies, and customer and admin roles.
- **Catalogue.** Search by title, author or ISBN; filter by category, price band and stock; sort; paginate. The filters live in the URL, so any view can be shared.
- **Cart.** Add books, change quantities and remove them. Quantities are checked against stock (at most 10 copies per title), and delivery is free over ₹999.
- **Checkout.** One transaction locks the cart and book rows (`SELECT … FOR UPDATE`), reserves stock, writes the order and its items, then empties the cart. Two buyers can't both get the last copy.
- **Order history.** Each order keeps a copy of the title, author and price paid, so history survives later edits or deletion of the book.
- **Online payment.** Customers choose between paying online (UPI, card or netbanking) and cash on delivery. Payments go through a `PaymentGateway` interface with two implementations: a built-in **DemoPay** gateway (no account, no real money) and **Razorpay**. Both follow the same flow: the store creates a gateway order, the gateway signs each payment with HMAC-SHA256, and the server verifies that signature before marking the order paid, so the browser can't fake a payment. Unpaid online orders show a **Pay** button in My orders and can't be shipped. Cash-on-delivery orders become paid when they're marked delivered.
- **Invoices.** Once an order is paid, customers can view its invoice or download it as a PDF from My orders; admins can open any paid order's invoice. Online orders get an invoice as soon as the payment is verified, cash-on-delivery orders once they are delivered. The PDF is generated with a small built-in writer (`util/Pdf.java`), so no PDF library is needed. Seller details come from `store.*` in `app.properties`.
- **Admin.** Add, edit and delete books (ISBN-10/13 checksum, unique ISBN, price rules) with a live cover preview. Orders move from Placed to Shipped to Delivered, or Placed to Cancelled; cancelling puts the stock back.
- **Validation and errors.** Every input is validated on the server, and the client mirrors those checks. Errors map to HTTP status codes (400, 401, 403, 404, 409, 503) and come back as JSON with per-field messages. Internal details are logged and never sent to the browser.

## Run it

Requirements: JDK 17+, MySQL 8.0+.

```bash
# 1. Create the database, tables and seed books
mysql -u root -p < sql/schema.sql

# 2. Set your MySQL password in app.properties (or: export DB_PASSWORD=...)

# 3. Start the app
./run.sh            # downloads MySQL Connector/J on first run, compiles, starts
# or, with Maven:
mvn compile exec:java
```

Open <http://localhost:8080>.

### Online payments

`payment.gateway` in `app.properties` chooses the gateway:

| Value | What happens |
| --- | --- |
| `demo` (default) | Built-in DemoPay window. No account, works offline, no real money. |
| `razorpay` | Razorpay Checkout. Needs `payment.razorpay.keyId` and `keySecret`; test keys charge nothing. |
| `off` | Cash on delivery only. |

DemoPay accepts any valid-looking details. To show a failed payment, use one of these:

| Method | Succeeds | Declined |
| --- | --- | --- |
| UPI | any ID, e.g. `name@okbank` | `failure@demo` |
| Card | `4111 1111 1111 1111`, any future expiry, any CVV | `4000 0000 0000 0002` |
| Netbanking | any listed bank | Declining Demo Bank |

To add the payment columns to a database created before this feature, run `sql/migrations/001_add_payments.sql` and then `002_payment_gateway.sql`.

On first start the app creates an admin account from `app.properties`:
`admin@bookstore.local` / `Admin@1234`. Change these before deploying anywhere.

## Project layout

```
sql/schema.sql                  Tables, constraints, indexes and seed data
src/main/java/com/obs/
  App.java                      Wiring and server start-up
  config/                       app.properties + environment overrides
  db/Database.java              Connections and transaction helper
  model/                        Records: Book, Cart, Order, User …
  dao/                          All SQL (prepared statements only)
  service/                      Business rules and validation
  web/                          Router, request parsing, sessions, error mapping
  web/controller/               REST endpoints
  util/                         Password hashing, validator, JSON writer, PDF writer
web/                            Frontend (HTML, CSS, ES modules)
```

## API

| Method | Path | Access |
| --- | --- | --- |
| POST | `/api/auth/register`, `/api/auth/login`, `/api/auth/logout` | public |
| GET | `/api/auth/me`, `/api/categories`, `/api/books`, `/api/books/{id}` | public |
| GET, POST | `/api/cart`, `/api/cart/items` | customer |
| PUT, DELETE | `/api/cart/items/{bookId}` | customer |
| GET, POST | `/api/orders` | customer |
| POST | `/api/orders/{id}/payment`, `/api/orders/{id}/payment/verify` | customer |
| GET | `/api/orders/{id}/invoice` (PDF; add `?download=1` to save it) | customer (own paid orders), admin |
| GET | `/api/payments/config` | public |
| POST | `/api/demo-gateway/charge` (only when `payment.gateway=demo`) | customer |
| GET, POST | `/api/admin/books` | admin |
| PUT, DELETE | `/api/admin/books/{id}` | admin |
| GET | `/api/admin/orders` | admin |
| PUT | `/api/admin/orders/{id}/status` | admin |

Request bodies are `application/x-www-form-urlencoded`, and responses are JSON.

## Notes

- Sessions are kept in memory, so restarting the server signs everyone out.
- Each request opens its own JDBC connection. For real traffic, put a pool such as HikariCP behind `Database`.
