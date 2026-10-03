package ru.mecotrade.kidtracker.processor;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.mecotrade.kidtracker.dao.model.*;
import ru.mecotrade.kidtracker.dao.repository.*;
import ru.mecotrade.kidtracker.model.ContactType;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only against a separately provisioned, populated Hibernate 5 synthetic database.
 * No app bootstrap, sockets, production datasource, SMS or device connections are loaded.
 * The baseline seeding variant is generated in a disposable checkout (see migration notes).
 */
@ContextConfiguration(classes = PostgresUpgradeRehearsalTest.PersistenceOnly.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "KIDTRACKER_UPGRADE_POSTGRES_URL", matches = "jdbc:postgresql:.*")
class PostgresUpgradeRehearsalTest {
    @Configuration
    @EntityScan("ru.mecotrade.kidtracker.dao.model")
    @EnableJpaRepositories("ru.mecotrade.kidtracker.dao.repository")
    static class PersistenceOnly { }
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired MessageRepository messages;
    @Autowired MediaRepository media;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("KIDTRACKER_UPGRADE_POSTGRES_URL"));
        r.add("spring.datasource.username", () -> "kidtracker_test");
        r.add("spring.datasource.password", () -> "disposable-test-only");
        r.add("spring.datasource.driverClassName", () -> "org.postgresql.Driver");
        r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @Test void populatedHibernate5SchemaRemainsReadableAndWritable() {
        UserInfo admin = users.findByUsername("synthetic-admin").orElseThrow();
        UserInfo member = users.findByUsername("synthetic-member").orElseThrow();
        assertTrue(admin.isAdmin()); assertFalse(member.isAdmin());
        assertEquals(admin.getId(), member.getCreatedBy().getId());
        String hash = admin.getPassword();
        assertTrue(new BCryptPasswordEncoder().matches("synthetic-only-password", hash));
        assertEquals("Synthetic child", member.getKids().iterator().next().getName());
        assertEquals("synthetic-device", member.getKids().iterator().next().getDevice().getId());
        assertEquals("thumbnail-sentinel", member.getKids().iterator().next().getThumb());
        Message old = messages.findFirstByDeviceIdAndTypeInAndSourceOrderByIdDesc(
                "synthetic-device", Collections.singleton("TEXT"), Message.Source.DEVICE);
        assertEquals("persisted payload", old.getPayload());
        Media oldMedia = media.findAfter("synthetic-device", 0L).iterator().next();
        assertEquals(Media.Type.TEXT, oldMedia.getType());
        assertArrayEquals("synthetic media".getBytes(StandardCharsets.UTF_8), oldMedia.getContent());
        assertEquals(1, users.findUserKidsLastMessages(member.getId(), Collections.singleton("TEXT"), Message.Source.DEVICE).size());
        assertEquals(1, messages.lastMessages(Collections.singleton("synthetic-device"), Message.Source.DEVICE).size());
        ContactRecord contact = em.createQuery("from ContactRecord", ContactRecord.class).getSingleResult();
        assertEquals(ContactType.SOS, contact.getType());
        assertEquals("+37060000000", contact.getPhone());
        ConfigRecord config = em.createQuery("from ConfigRecord", ConfigRecord.class).getSingleResult();
        assertEquals("legacy-value", config.getValue());
        long previous = ((Number) em.createNativeQuery("select last_value from hibernate_sequence").getSingleResult()).longValue();
        UserInfo added = UserInfo.builder().username("post-upgrade-user").password(hash).admin(false).build();
        em.persist(added);
        Message addedMessage = Message.device("synthetic", "synthetic-device", "TEXT", "new payload");
        em.persist(addedMessage);
        Media addedMedia = Media.builder().message(addedMessage).type(Media.Type.TEXT)
                .contentType("text/plain").content(new byte[]{1,2,3}).build();
        em.persist(addedMedia);
        config.setValue("new-value"); contact.setName("Updated contact");
        em.flush(); em.clear();
        assertTrue(added.getId() > previous);
        assertTrue(addedMessage.getId() > added.getId());
        assertTrue(addedMedia.getId() > addedMessage.getId());
        assertArrayEquals(new byte[]{1,2,3}, em.find(Media.class, addedMedia.getId()).getContent());
        assertEquals(hash, em.find(UserInfo.class, admin.getId()).getPassword());
        assertTrue(em.find(UserInfo.class, admin.getId()).isAdmin());
        assertEquals("new-value", em.find(ConfigRecord.class, config.getId()).getValue());
        assertEquals("Updated contact", em.find(ContactRecord.class, contact.getId()).getName());
    }
}
