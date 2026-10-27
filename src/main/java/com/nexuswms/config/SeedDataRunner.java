package com.nexuswms.config;

import com.nexuswms.repository.ProductRepository;
import org.slf4j.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;

/** Loads database/seed.sql once, only when the products table is empty. */
@Component
public class SeedDataRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);
    private final ProductRepository products; private final DataSource ds;
    public SeedDataRunner(ProductRepository products, DataSource ds) { this.products = products; this.ds = ds; }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (products.count() > 0) return;
        log.info("products is empty - loading demo seed data");
        try (Connection c = ds.getConnection()) { ScriptUtils.executeSqlScript(c, new ClassPathResource("database/seed.sql")); }
    }
}
