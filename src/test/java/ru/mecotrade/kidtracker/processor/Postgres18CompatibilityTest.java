package ru.mecotrade.kidtracker.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import ru.mecotrade.kidtracker.dao.model.UserInfo;
import ru.mecotrade.kidtracker.dao.model.DeviceInfo;
import ru.mecotrade.kidtracker.dao.model.KidInfo;
import ru.mecotrade.kidtracker.dao.model.Assignment;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in disposable PostgreSQL smoke; never uses application datasource credentials. */
@ContextConfiguration(classes = Postgres18CompatibilityTest.PersistenceOnly.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "KIDTRACKER_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
class Postgres18CompatibilityTest {
    @Configuration
    @EntityScan("ru.mecotrade.kidtracker.dao.model")
    @EnableJpaRepositories("ru.mecotrade.kidtracker.dao.repository")
    static class PersistenceOnly { }

    @Autowired EntityManager em;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("KIDTRACKER_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> "kidtracker_test");
        registry.add("spring.datasource.password", () -> "disposable-test-only");
        registry.add("spring.datasource.driverClassName", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
    @Test void quotedUserAndOwnershipMappingRoundTrip() {
        UserInfo user = UserInfo.builder().username("pg18-owner").password("test-hash").admin(true).build();
        em.persist(user);
        DeviceInfo device = DeviceInfo.of("1234567890"); em.persist(device);
        KidInfo kid = KidInfo.builder().id(new Assignment()).device(device).user(user).name("Test child").build();
        em.persist(kid); em.flush(); em.clear();
        UserInfo loaded = em.find(UserInfo.class, user.getId());
        assertEquals("test-hash", loaded.getPassword());
        assertEquals(1, loaded.getKids().size());
        assertEquals("1234567890", loaded.getKids().iterator().next().getDevice().getId());
    }
}
