package com.nexuswms.config;

import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.sql.Connection;

/** Cheap MySQL liveness probe used by the header status pill every 30 s. */
@Service
public class HealthService {
    private final DataSource ds;
    public HealthService(DataSource ds) { this.ds = ds; }
    public boolean ping() {
        try (Connection c = ds.getConnection()) { return c.isValid(2); } catch (Exception e) { return false; }
    }
}
