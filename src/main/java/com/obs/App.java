package com.obs;

import com.obs.config.AppConfig;
import com.obs.dao.BookDao;
import com.obs.dao.CartDao;
import com.obs.dao.CategoryDao;
import com.obs.dao.OrderDao;
import com.obs.dao.UserDao;
import com.obs.db.Database;
import com.obs.service.AuthService;
import com.obs.service.BookService;
import com.obs.service.CartService;
import com.obs.service.DemoGateway;
import com.obs.service.InvoiceService;
import com.obs.service.OrderService;
import com.obs.service.PaymentGateway;
import com.obs.service.PaymentService;
import com.obs.service.RazorpayClient;
import com.obs.web.ApiHandler;
import com.obs.web.Router;
import com.obs.web.SessionStore;
import com.obs.web.StaticHandler;
import com.obs.web.controller.AdminController;
import com.obs.web.controller.AuthController;
import com.obs.web.controller.BookController;
import com.obs.web.controller.CartController;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class App {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load(Path.of("app.properties"));

        Database db = new Database(
                config.get("db.url", "jdbc:mysql://localhost:3306/online_book_store?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=%2B00:00&forceConnectionTimeZoneToSession=true"),
                config.get("db.user", "root"),
                config.get("db.password", ""));
        try {
            db.verify();
        } catch (SQLException e) {
            System.err.println("""

                    Could not connect to MySQL: %s

                    Check that MySQL is running, that sql/schema.sql has been loaded,
                    and that db.url, db.user and db.password in app.properties are correct.
                    """.formatted(e.getMessage()));
            System.exit(1);
        }

        UserDao userDao = new UserDao();
        BookDao bookDao = new BookDao();
        CategoryDao categoryDao = new CategoryDao();
        CartDao cartDao = new CartDao();
        OrderDao orderDao = new OrderDao();

        AuthService authService = new AuthService(db, userDao);
        BookService bookService = new BookService(db, bookDao, categoryDao);
        CartService cartService = new CartService(db, cartDao, bookDao);
        DemoGateway demoGateway = null;
        PaymentGateway gateway;
        String gatewayName = config.get("payment.gateway", "demo").trim().toLowerCase();
        switch (gatewayName) {
            case "razorpay" -> {
                RazorpayClient razorpay = new RazorpayClient(
                        config.get("payment.razorpay.keyId", ""), config.get("payment.razorpay.keySecret", ""));
                if (razorpay.isConfigured()) {
                    gateway = razorpay;
                    LOG.info("Online payments: Razorpay " + (razorpay.isTestMode() ? "TEST mode" : "LIVE mode"));
                } else {
                    gateway = null;
                    LOG.warning("payment.gateway=razorpay but no keys are set. Online payments are off.");
                }
            }
            case "off" -> {
                gateway = null;
                LOG.info("Online payments: off");
            }
            default -> {
                demoGateway = new DemoGateway();
                gateway = demoGateway;
                LOG.info("Online payments: built-in DEMO gateway (no real money)");
            }
        }
        OrderService orderService = new OrderService(db, orderDao, cartDao, bookDao,
                gateway == null ? null : gateway.name());
        PaymentService paymentService = new PaymentService(db, orderDao, gateway);
        InvoiceService invoiceService = new InvoiceService(db, orderDao,
                config.get("store.name", "Online Book Store"),
                config.get("store.address", ""),
                config.get("store.email", ""));

        String adminEmail = config.get("admin.email", "admin@bookstore.local");
        if (authService.ensureAdmin(config.get("admin.name", "Store Admin"), adminEmail,
                config.get("admin.password", "Admin@1234"))) {
            LOG.info("Created admin account " + adminEmail + ". Change its password in production.");
        }

        SessionStore sessions = new SessionStore(Duration.ofHours(8));
        Router router = new Router();
        new AuthController(authService, sessions).register(router);
        new BookController(bookService).register(router);
        new CartController(cartService, orderService, paymentService, invoiceService, demoGateway).register(router);
        new AdminController(bookService, orderService).register(router);

        int port = config.getInt("server.port", 8080);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", new ApiHandler(router, sessions));
        server.createContext("/", new StaticHandler(Path.of(config.get("web.root", "web"))));
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
        LOG.info("Online Book Store running at http://localhost:" + port);
    }
}
