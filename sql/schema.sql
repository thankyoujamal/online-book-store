-- Online Book Store schema (MySQL 8.0+)
-- Usage: mysql -u root -p < sql/schema.sql

CREATE DATABASE IF NOT EXISTS online_book_store
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE online_book_store;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS order_items, orders, cart_items, books, categories, users;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
    id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(80)  NOT NULL,
    email         VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM('CUSTOMER', 'ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE categories (
    id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(60) NOT NULL,
    slug       VARCHAR(60) NOT NULL,
    sort_order SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_categories_name UNIQUE (name),
    CONSTRAINT uq_categories_slug UNIQUE (slug)
) ENGINE = InnoDB;

CREATE TABLE books (
    id             INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    isbn           VARCHAR(13)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    subtitle       VARCHAR(200) NULL,
    author         VARCHAR(200) NOT NULL,
    edition        VARCHAR(40)  NULL,
    category_id    INT UNSIGNED NOT NULL,
    description    TEXT NULL,
    price          DECIMAL(10, 2) NOT NULL,
    list_price     DECIMAL(10, 2) NULL,
    stock          INT NOT NULL DEFAULT 0,
    rating         DECIMAL(2, 1) NOT NULL DEFAULT 0.0,
    rating_count   INT UNSIGNED NOT NULL DEFAULT 0,
    cover_color    CHAR(7) NOT NULL DEFAULT '#2d5d8a',
    published_year SMALLINT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_books_isbn UNIQUE (isbn),
    CONSTRAINT fk_books_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT chk_books_price CHECK (price > 0),
    CONSTRAINT chk_books_list_price CHECK (list_price IS NULL OR list_price > price),
    CONSTRAINT chk_books_stock CHECK (stock >= 0),
    CONSTRAINT chk_books_rating CHECK (rating BETWEEN 0 AND 5),
    INDEX idx_books_title (title),
    INDEX idx_books_author (author),
    INDEX idx_books_price (price),
    INDEX idx_books_popularity (rating_count)
) ENGINE = InnoDB;

CREATE TABLE cart_items (
    user_id  INT UNSIGNED NOT NULL,
    book_id  INT UNSIGNED NOT NULL,
    quantity SMALLINT UNSIGNED NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, book_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE,
    CONSTRAINT chk_cart_quantity CHECK (quantity BETWEEN 1 AND 10)
) ENGINE = InnoDB;

CREATE TABLE orders (
    id               INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id          INT UNSIGNED NOT NULL,
    status           ENUM('PLACED', 'SHIPPED', 'DELIVERED', 'CANCELLED') NOT NULL DEFAULT 'PLACED',
    payment_method   ENUM('COD', 'ONLINE') NOT NULL DEFAULT 'COD',
    payment_gateway  VARCHAR(20) NULL,  -- DEMO or RAZORPAY, for online payments
    payment_status   ENUM('UNPAID', 'PAID') NOT NULL DEFAULT 'UNPAID',
    gateway_order_id VARCHAR(64) NULL,  -- gateway's order id
    payment_ref      VARCHAR(64) NULL,  -- gateway's payment id
    paid_at          TIMESTAMP NULL,
    subtotal         DECIMAL(10, 2) NOT NULL,
    shipping         DECIMAL(10, 2) NOT NULL,
    total            DECIMAL(10, 2) NOT NULL,
    shipping_name    VARCHAR(80)  NOT NULL,
    shipping_phone   VARCHAR(16)  NOT NULL,
    shipping_address VARCHAR(400) NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_orders_gateway_order UNIQUE (gateway_order_id),
    INDEX idx_orders_user (user_id, created_at)
) ENGINE = InnoDB;

-- Title, author and price are copied at purchase time. book_id becomes NULL if the
-- book is later deleted, but the customer's order history stays intact.
CREATE TABLE order_items (
    id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id   INT UNSIGNED NOT NULL,
    book_id    INT UNSIGNED NULL,
    title      VARCHAR(200) NOT NULL,
    author     VARCHAR(200) NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    quantity   SMALLINT UNSIGNED NOT NULL,
    CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_items_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE SET NULL,
    CONSTRAINT chk_items_quantity CHECK (quantity > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- Seed data
-- The admin account is created by the application on first start, using
-- admin.email / admin.password from app.properties, so the password is hashed.
-- ---------------------------------------------------------------------------

INSERT INTO categories (id, name, slug, sort_order) VALUES
    (1, 'Programming',      'programming',      1),
    (2, 'Computer Science', 'computer-science', 2),
    (3, 'Academic',         'academic',         3),
    (4, 'Fiction',          'fiction',          4),
    (5, 'Self Help',        'self-help',        5);

INSERT INTO books (isbn, title, subtitle, author, edition, category_id, description,
                   price, list_price, stock, rating, rating_count, cover_color, published_year) VALUES
('9780134685991', 'Effective Java', NULL, 'Joshua Bloch', '3rd Edition', 1,
 'Ninety items of practical advice on writing Java that is correct, clear and fast, updated for lambdas, streams and modules.',
 650.00, 850.00, 14, 4.8, 1204, '#8e2a22', 2018),
('9780596009205', 'Head First Java', NULL, 'Kathy Sierra, Bert Bates', '2nd Edition', 1,
 'A visual, puzzle-heavy introduction to Java and object-oriented thinking for people who learn by doing.',
 599.00, 799.00, 22, 4.6, 876, '#b8642a', 2005),
('9780132350884', 'Clean Code', 'A Handbook of Agile Software Craftsmanship', 'Robert C. Martin', NULL, 1,
 'Case studies in turning messy code into readable code: naming, functions, comments, error handling and tests.',
 720.00, NULL, 3, 4.7, 2011, '#2d5d8a', 2008),
('9781260463415', 'Java: The Complete Reference', NULL, 'Herbert Schildt', '12th Edition', 1,
 'A thorough reference to the Java language and its core libraries, covering Java SE 17.',
 850.00, 1050.00, 9, 4.5, 640, '#2f6b3f', 2021),
('9781617297571', 'Spring in Action', NULL, 'Craig Walls', '6th Edition', 1,
 'Builds a working Spring Boot application chapter by chapter, from web MVC and data access to security and reactive APIs.',
 899.00, NULL, 12, 4.4, 512, '#5b3a8c', 2022),
('9781492057611', 'Learning SQL', 'Generate, Manipulate, and Retrieve Data', 'Alan Beaulieu', '3rd Edition', 1,
 'Joins, grouping, subqueries, window functions and transactions, explained with examples you can run.',
 760.00, 900.00, 18, 4.5, 287, '#8c2849', 2020),
('9780135957059', 'The Pragmatic Programmer', 'Your Journey to Mastery', 'David Thomas, Andrew Hunt', '20th Anniversary Edition', 1,
 'Short, practical lessons on responsibility, design, estimation and tooling for working programmers.',
 1150.00, 1400.00, 7, 4.8, 1630, '#d8b04a', 2019),
('9780134757599', 'Refactoring', 'Improving the Design of Existing Code', 'Martin Fowler', '2nd Edition', 1,
 'A catalogue of small, safe changes that improve code structure without changing behaviour.',
 1290.00, NULL, 0, 4.6, 702, '#1f2a36', 2018),

('9780078022159', 'Database System Concepts', NULL, 'Silberschatz, Korth, Sudarshan', '7th Edition', 2,
 'The standard university text on relational design, SQL, storage, indexing, transactions and recovery.',
 940.00, 1100.00, 11, 4.3, 398, '#2e6b62', 2019),
('9780262046305', 'Introduction to Algorithms', NULL, 'Cormen, Leiserson, Rivest, Stein', '4th Edition', 2,
 'Known as CLRS. Rigorous coverage of sorting, graphs, dynamic programming and complexity.',
 1450.00, 1695.00, 5, 4.8, 1560, '#2a3240', 2022),
('9781119800361', 'Operating System Concepts', NULL, 'Silberschatz, Galvin, Gagne', '10th Edition', 2,
 'Processes, threads, scheduling, memory, file systems and security, with Linux and Windows examples.',
 880.00, NULL, 16, 4.4, 455, '#3c5a7a', 2021),
('9780136681557', 'Computer Networking', 'A Top-Down Approach', 'James F. Kurose, Keith W. Ross', '8th Edition', 2,
 'Starts from the application layer and works down to links, covering HTTP, TCP, routing and wireless.',
 920.00, NULL, 8, 4.5, 330, '#7a3b2e', 2021),
('9780201633610', 'Design Patterns', 'Elements of Reusable Object-Oriented Software', 'Gamma, Helm, Johnson, Vlissides', NULL, 2,
 'The original catalogue of 23 object-oriented design patterns, from Factory to Visitor.',
 1199.00, NULL, 4, 4.6, 980, '#a33a2a', 1994),
('9780262510875', 'Structure and Interpretation of Computer Programs', NULL, 'Harold Abelson, Gerald Jay Sussman', '2nd Edition', 2,
 'A classic on abstraction, recursion, interpreters and the nature of computation, taught in Scheme.',
 1050.00, NULL, 2, 4.7, 410, '#4a2f5e', 1996),

('9788174091956', 'Higher Engineering Mathematics', NULL, 'B. S. Grewal', '44th Edition', 3,
 'Calculus, differential equations, linear algebra, complex analysis and numerical methods for engineering students.',
 795.00, NULL, 30, 4.4, 2240, '#1d4e89', 2017),
('9788177091878', 'Concepts of Physics', 'Volume 1', 'H. C. Verma', NULL, 3,
 'Mechanics, waves and thermodynamics for JEE and senior secondary students, with worked problems.',
 450.00, NULL, 40, 4.8, 3105, '#c44536', 2022),
('9789355321251', 'Indian Polity', NULL, 'M. Laxmikanth', '7th Edition', 3,
 'The Constitution, Parliament, judiciary and governance, written for civil services preparation.',
 825.00, 900.00, 25, 4.7, 2890, '#6b4f2a', 2023),
('9781259676512', 'Discrete Mathematics and Its Applications', NULL, 'Kenneth H. Rosen', '8th Edition', 3,
 'Logic, proofs, sets, combinatorics, graphs and trees with applications to computer science.',
 1095.00, NULL, 6, 4.4, 512, '#355c4a', 2018),

('9780006550686', 'The God of Small Things', NULL, 'Arundhati Roy', NULL, 4,
 'Twins in 1969 Kerala and the small events that change their family for ever. Winner of the Booker Prize.',
 399.00, NULL, 20, 4.3, 1810, '#3f6d4e', 1997),
('9781416562603', 'The White Tiger', NULL, 'Aravind Adiga', NULL, 4,
 'A driver from a village in Bihar tells how he became an entrepreneur in Bangalore. Winner of the Booker Prize.',
 350.00, 399.00, 15, 4.1, 1320, '#d9a441', 2008),
('9780451524935', 'Nineteen Eighty-Four', NULL, 'George Orwell', NULL, 4,
 'Winston Smith works for the Ministry of Truth in a state that watches everything, including thought.',
 299.00, NULL, 50, 4.6, 4020, '#b23a2b', 1949),
('9780099578512', 'Midnight''s Children', NULL, 'Salman Rushdie', NULL, 4,
 'Saleem Sinai, born at the stroke of India''s independence, finds his life bound up with the nation''s.',
 499.00, NULL, 0, 4.2, 860, '#2b3f6b', 1981),

('9780735211292', 'Atomic Habits', 'An Easy & Proven Way to Build Good Habits & Break Bad Ones', 'James Clear', NULL, 5,
 'How small, repeated changes compound, and a practical system for making habits stick.',
 499.00, 799.00, 35, 4.8, 5210, '#e8dfcf', 2018),
('9781455586691', 'Deep Work', 'Rules for Focused Success in a Distracted World', 'Cal Newport', NULL, 5,
 'An argument for long, undistracted stretches of work and a set of rules for getting them.',
 450.00, NULL, 12, 4.6, 1740, '#22303f', 2016),
('9789390166268', 'The Psychology of Money', 'Timeless Lessons on Wealth, Greed, and Happiness', 'Morgan Housel', NULL, 5,
 'Nineteen short stories about how people actually think about money, and why behaviour beats knowledge.',
 399.00, 499.00, 3, 4.7, 3880, '#2f6f6a', 2020);
