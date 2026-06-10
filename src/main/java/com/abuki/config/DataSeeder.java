package com.abuki.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedData(JdbcTemplate jdbc) {
        return args -> {

            // ── Ensure stock_history table exists ─────────────
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS stock_history (
                    id bigserial PRIMARY KEY,
                    created_at timestamp,
                    date date,
                    new_stock int NOT NULL,
                    previous_stock int NOT NULL,
                    quantity_change int NOT NULL,
                    reason varchar(500),
                    reference varchar(255),
                    time time,
                    type varchar(30) NOT NULL,
                    recorded_by varchar(100) NOT NULL,
                    product_id bigint NOT NULL,
                    CONSTRAINT fk_sh_product FOREIGN KEY (product_id) REFERENCES products(id)
                )
            """);

            // ── Only seed if users table is empty ─────────────
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            if (count != null && count == 0) {

                // Admin user (password: Admin1234)
                jdbc.execute("""
                    INSERT INTO users (created_at, email, last_login, name, password, role, status)
                    VALUES ('2026-05-09 22:08:06', 'admin@stokio.com', '2026-05-20 18:54:10',
                    'Admin', '$2b$10$eB8AjFC56Zneg0YBECOfCeEcXnaaYjCPPzBFUDaVIAB0Hw7KLsNdO', 'ADMIN', 'ACTIVE')
                """);

                // Products
                jdbc.execute("""
                    INSERT INTO products (category, cost, created_at, description, min_stock, name, price, sku, status, stock, updated_at) VALUES
                    ('Electronics', 80,   '2026-05-07 19:48:45', '', 15,  'mofi mouse',          100,  '1', 'IN_STOCK',  2910, '2026-05-16 21:42:59'),
                    ('',           500,  '2026-05-09 22:35:39', '', 30,  'headset',              1000, '2', 'LOW_STOCK',   30, '2026-05-16 21:40:15'),
                    ('Electronics', 1000, '2026-05-12 17:02:05', '', 400, 'speaker',             1250, '3', 'LOW_STOCK',    3, '2026-05-16 21:40:41'),
                    ('Electronics', 800,  '2026-05-13 19:33:35', '', 30,  'micraphone',          1200, '5', 'IN_STOCK',    83, '2026-05-16 21:42:15'),
                    ('Electronics', 2599, '2026-05-13 20:01:21', '', 30,  'airpod',              3400, '4', 'LOW_STOCK',    3, '2026-05-16 21:28:10'),
                    ('',           1000, '2026-05-16 07:34:39', '', 29,  'printer cable 1.2 m', 2500, '6', 'IN_STOCK',    50, '2026-05-16 07:34:39'),
                    ('Electronics', 2000, '2026-05-16 21:35:25', '', 30,  'miki mose',           2500, '7', 'IN_STOCK',    48, '2026-05-16 21:38:53')
                """);

                // Expenses
                jdbc.execute("""
                    INSERT INTO expenses (amount, category, created_at, date, description) VALUES
                    (2000,     'Utilities', '2026-05-09 16:01:20', '2026-05-09', NULL),
                    (50000,    'Utilities', '2026-05-12 08:14:12', '2026-05-12', NULL),
                    (25000000, 'Transport', '2026-05-13 04:29:53', '2026-05-13', NULL)
                """);

                System.out.println("[SEED] Database seeded with initial data.");
            }
        };
    }
}
