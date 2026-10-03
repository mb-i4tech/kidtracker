package ru.mecotrade.kidtracker.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.mecotrade.kidtracker.config.SecurityConfig;
import ru.mecotrade.kidtracker.controller.UserController;
import ru.mecotrade.kidtracker.controller.DeviceController;
import ru.mecotrade.kidtracker.dao.model.*;
import ru.mecotrade.kidtracker.dao.service.KidService;
import ru.mecotrade.kidtracker.device.DeviceManager;
import ru.mecotrade.kidtracker.processor.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(controllers = {CsrfController.class, UserController.class, DeviceController.class},
        properties = {"remember.me.token.validity.seconds=3600", "kidtracker.device.confirmation.timeout.millis=1"})
@org.springframework.test.context.ContextConfiguration(classes = {SecurityConfig.class, TokenAttemptLimiter.class, SessionSecurityIntegrationTest.Users.class, CsrfController.class, UserController.class, DeviceController.class})
class SessionSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean DeviceProcessor devices;
    @MockBean UserProcessor users;
    @MockBean MediaProcessor media;
    @MockBean DeviceManager manager;
    @MockBean KidService kids;
    @TestConfiguration static class Users {
        @Bean UserDetailsService users(PasswordEncoder encoder) {
            String password = encoder.encode("test-password");
            return username -> new UserPrincipal(UserInfo.builder().id((long) username.hashCode())
                    .username(username).password(password).admin(username.equals("admin"))
                    .kids(List.of(KidInfo.builder().device(DeviceInfo.of("owned")).build())).build(),
                    List.of(new SimpleGrantedAuthority(username.equals("admin") ? "ADMIN" : "USER")));
        }
    }
    record Session(MockHttpSession session, String header, String token) { }
    Session csrf(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return new Session((MockHttpSession) result.getRequest().getSession(), body.get("headerName").asText(), body.get("token").asText());
    }
    Session login(String username) throws Exception {
        Session initial = csrf(new MockHttpSession());
        mvc.perform(post("/login").session(initial.session()).header(initial.header(), initial.token())
                .param("username", username).param("password", "test-password"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        return csrf(initial.session());
    }
    @Test void loginRequiresValidCsrfAndApiNeverRedirects() throws Exception {
        mvc.perform(get("/api/user/info")).andExpect(status().isUnauthorized());
        mvc.perform(post("/login").param("username", "u").param("password", "test-password"))
                .andExpect(status().isForbidden());
        Session s = csrf(new MockHttpSession());
        mvc.perform(post("/login").session(s.session()).header(s.header(), "wrong"))
                .andExpect(status().isForbidden());
        Session logged = login("login-test");
        mvc.perform(get("/api/user/info").session(logged.session())).andExpect(status().isOk());
    }
    @Test void mutationRequiresCsrfAndGetCannotMutate() throws Exception {
        Session s = login("mutations");
        for (String url : List.of("/api/user/token/123456", "/api/device/owned/execute/123456",
                "/api/device/owned/off/alarm", "/api/device/owned/off/notification", "/api/device/owned/command/PING")) {
            mvc.perform(get(url).session(s.session())).andExpect(status().isMethodNotAllowed());
            mvc.perform(post(url).session(s.session())).andExpect(status().isForbidden());
            mvc.perform(post(url).session(s.session()).header(s.header(), "wrong")).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/device/owned/off/alarm").session(s.session()).header(s.header(), s.token()))
                .andExpect(status().isNoContent());
        verify(manager).alarmOff("owned");
        verify(users, never()).execute(any());
    }
    @Test void ownershipAndAdminNegativesAreEnforced() throws Exception {
        Session s = login("ownership");
        mvc.perform(get("/api/device/foreign/config").session(s.session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/device/foreign/off/alarm").session(s.session()).header(s.header(), s.token()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").session(s.session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/device/owned/command/PING").session(s.session()).header(s.header(), s.token()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/device/owned/config").session(s.session())).andExpect(status().isOk());
        verify(manager, never()).alarmOff("foreign");
        Session admin = login("admin");
        mvc.perform(get("/api/device/foreign/config").session(admin.session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/device/owned/command/PING").session(admin.session()).header(admin.header(), admin.token()))
                .andExpect(status().isExpectationFailed()); // passed authorization; mock watch did not confirm
    }
    @Test void confirmationBudgetSharedAcrossEndpointsAndNotResetByForwardedIp() throws Exception {
        Session s = login("rate-limit");
        for (int i = 0; i < 5; i++) {
            String url = i % 2 == 0 ? "/api/user/token/123456" : "/api/device/owned/execute/123456";
            mvc.perform(post(url).session(s.session()).header(s.header(), s.token()).header("X-Forwarded-For", "192.0.2." + i))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(post("/api/user/token/654321").session(s.session()).header(s.header(), s.token())
                .header("X-Forwarded-For", "198.51.100.1")).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        Session other = login("different-user");
        mvc.perform(post("/api/user/token/123456").session(other.session()).header(other.header(), other.token()))
                .andExpect(status().isNoContent());
    }
}
