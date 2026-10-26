package com.example.painting;

import com.example.painting.auth.AppUser;
import com.example.painting.auth.Role;
import com.example.painting.auth.UserRepository;
import com.example.painting.painting.Painting;
import com.example.painting.painting.PaintingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migrations and the real repositories against a genuine PostgreSQL, started in Docker for
 * the duration of this class. The other tests use H2, which is fast but not identical to PostgreSQL; this class
 * proves the production database accepts our schema and behaves the way the code assumes.
 *
 * {@code @ServiceConnection} points Spring's datasource at the container automatically (it overrides the H2 URL
 * from application-test.yml). With {@code disabledWithoutDocker} the class is skipped, not failed, on a machine
 * that has no Docker.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired PaintingRepository paintings;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    /** Same database for every test in the class, so start each one from empty tables. */
    @BeforeEach
    void clean() {
        paintings.deleteAll();
        users.deleteAll();
    }

    /** Flyway ran the schema migrations (and not the dev-only sample data) and both tables exist. */
    @Test
    void migrationsCreatedTheSchema() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name IN ('paintings', 'users')",
                Integer.class);

        assertThat(versions).containsExactly("1", "2");
        assertThat(tables).isEqualTo(2);
    }

    /** Data written through the repository is really stored, and the case-insensitive search works on PostgreSQL. */
    @Test
    void storesPaintingsAndSearchesByTitleIgnoringCase() {
        paintings.save(new Painting("Sunset Over Hills", "A", null, new BigDecimal("10.00"), 1, null));
        paintings.save(new Painting("Starry Night", "B", null, new BigDecimal("20.00"), 2, null));

        var found = paintings.findByTitleContainingIgnoreCase("SUN", PageRequest.of(0, 10));

        assertThat(found.getContent()).extracting(Painting::getTitle).containsExactly("Sunset Over Hills");
        assertThat(paintings.count()).isEqualTo(2);
    }

    /** NUMERIC(12,2) keeps money exact: the largest allowed value comes back unchanged. */
    @Test
    void keepsMoneyExact() {
        Painting saved = paintings.saveAndFlush(
                new Painting("Big", "A", null, new BigDecimal("9999999999.99"), 0, null));

        assertThat(paintings.findById(saved.getId()).orElseThrow().getPrice()).isEqualByComparingTo("9999999999.99");
    }

    /** The CHECK constraints in V1 are enforced by the database itself, even if application validation were bypassed. */
    @Test
    void databaseRejectsInvalidPriceAndStock() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO paintings (title, artist, price, stock) VALUES ('t', 'a', 0, 1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO paintings (title, artist, price, stock) VALUES ('t', 'a', 5, -1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** The unique constraint on usernames and the allowed-roles constraint are enforced by PostgreSQL. */
    @Test
    void databaseEnforcesUniqueUsernamesAndKnownRoles() {
        users.saveAndFlush(new AppUser("asha", "$2a$hash", Role.CUSTOMER));

        assertThatThrownBy(() -> users.saveAndFlush(new AppUser("asha", "$2a$other", Role.ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (username, password_hash, role) VALUES ('x', 'h', 'SUPERUSER')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
