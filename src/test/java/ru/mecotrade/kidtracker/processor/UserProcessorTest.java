package ru.mecotrade.kidtracker.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ru.mecotrade.kidtracker.config.PublicDeviceEndpoint;
import ru.mecotrade.kidtracker.dao.model.UserInfo;
import ru.mecotrade.kidtracker.dao.service.UserService;
import ru.mecotrade.kidtracker.exception.KidTrackerInvalidOperationException;
import ru.mecotrade.kidtracker.model.Credentials;
import ru.mecotrade.kidtracker.model.User;
import ru.mecotrade.kidtracker.security.UserPrincipal;
import java.util.Collections;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserProcessorTest {
    UserProcessor processor;
    UserService users;
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    UserInfo account;
    UserPrincipal principal;

    @BeforeEach void setup() {
        processor = new UserProcessor();
        users = mock(UserService.class);
        ReflectionTestUtils.setField(processor, "userService", users);
        ReflectionTestUtils.setField(processor, "passwordEncoder", encoder);
        account = UserInfo.builder().id(1L).username("operator").password(encoder.encode("old-secret"))
                .name("Operator").admin(true).kids(Collections.emptyList()).build();
        principal = new UserPrincipal(account, Collections.emptyList());
        when(users.get(1L)).thenReturn(Optional.of(account));
        when(users.getByUsername("operator")).thenReturn(Optional.of(account));
    }

    @Test void emptyDatabaseRequiresExplicitBootstrapPassword() {
        assertThrows(IllegalStateException.class, () -> processor.addAdminIfNoUsers("admin", ""));
        assertThrows(IllegalStateException.class, () -> processor.addAdminIfNoUsers("admin", "password"));
        verify(users, never()).save(any());
    }
    @Test void bootstrapHashesPasswordAndCreatesOnlyAdmin() {
        processor.addAdminIfNoUsers("admin", "a-distinct-bootstrap-secret");
        verify(users).save(argThat(u -> u.isAdmin() && "admin".equals(u.getUsername())
                && encoder.matches("a-distinct-bootstrap-secret", u.getPassword())));
    }
    @Test void existingDatabaseIsNeverRebootstrappedEvenWithoutAdminsOrPassword() {
        when(users.count()).thenReturn(3L);
        processor.addAdminIfNoUsers("admin", "");
        verify(users, never()).save(any());
    }
    @Test void bootstrapAdminCanSavePhoneWithoutChangingRoleOrPassword() throws Exception {
        String password = account.getPassword();
        processor.updateUser(principal, User.builder().name("Owner").phone("+37061234567").admin(false).build());
        assertEquals("+37061234567", account.getPhone());
        assertEquals(password, account.getPassword());
        assertTrue(account.isAdmin());
        verify(users).save(account);
    }
    @Test void existingPhoneCannotBeReplacedEvenWithAValidNewNumber() {
        account.setPhone("+37061234567");
        assertThrows(KidTrackerInvalidOperationException.class, () -> processor.updateUser(principal,
                User.builder().name("Changed").phone("+37069999999").build()));
        assertEquals("+37061234567", account.getPhone());
        assertEquals("Operator", account.getName());
        verify(users, never()).save(any());
    }
    @Test void rejectsInvalidPhone() {
        assertThrows(KidTrackerInvalidOperationException.class, () -> processor.updateUser(principal,
                User.builder().name("Owner").phone("bad").build()));
        verify(users, never()).save(any());
    }
    @Test void passwordChangeRequiresCurrentPassword() {
        assertThrows(KidTrackerInvalidOperationException.class, () -> processor.updateUser(principal,
                User.builder().name("Owner").phone("+37061234567")
                        .credentials(new Credentials(null, "wrong", "new-secret")).build()));
        verify(users, never()).save(any());
        assertTrue(encoder.matches("old-secret", account.getPassword()));
    }
    @Test void lastAdminCannotRemoveTheirAccount() {
        when(users.count(true)).thenReturn(1L);
        assertThrows(KidTrackerInvalidOperationException.class, () -> processor.removeUser(principal,
                User.builder().credentials(new Credentials(null, "old-secret", null)).build()));
        verify(users, never()).remove(any());
    }
    @Test void nonAdminCannotCreateAccountsEvenViaService() {
        account.setAdmin(false);
        assertThrows(AccessDeniedException.class, () -> processor.addUser(principal, User.builder().admin(true).build()));
        verify(users, never()).save(any());
    }
    @Test void adminCreatesNonAdminWithoutChangingExistingAccount() throws Exception {
        User requested = User.builder().name("Parent").phone("+37061234567").admin(false)
                .credentials(new Credentials("new-parent", "new-secret", null)).build();
        when(users.getByUsername("new-parent")).thenReturn(Optional.empty());
        processor.addUser(principal, requested);
        verify(users).save(argThat(u -> !u.isAdmin() && "new-parent".equals(u.getUsername())
                && encoder.matches("new-secret", u.getPassword()) && u.getCreatedBy() == account));
        assertNull(requested.getCredentials());
        assertTrue(account.isAdmin());
    }
    @Test void duplicateUsernameCannotOverwriteAnExistingAccount() {
        User requested = User.builder().name("Other").phone("+37061234567").admin(false)
                .credentials(new Credentials("operator", "replacement", null)).build();
        assertThrows(KidTrackerInvalidOperationException.class, () -> processor.addUser(principal, requested));
        verify(users, never()).save(any());
        assertTrue(encoder.matches("old-secret", account.getPassword()));
    }
    @Test void dtoSeparatesPublicDestinationAndListener() {
        ReflectionTestUtils.setField(processor, "publicDeviceEndpoint", new PublicDeviceEndpoint("watch.example.org", 9001));
        ReflectionTestUtils.setField(processor, "messagePort", 8001);
        assertEquals("watch.example.org", processor.serverConfig().getPublicHost());
        assertEquals(Integer.valueOf(9001), processor.serverConfig().getPublicPort());
        assertEquals(8001, processor.serverConfig().getMessagePort());
        assertEquals(0, processor.serverConfig().getDebugPort());
    }
}
