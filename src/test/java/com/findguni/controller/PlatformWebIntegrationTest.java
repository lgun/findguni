package com.findguni.controller;

import com.findguni.model.Difficulty;
import com.findguni.model.EscapeGame;
import com.findguni.model.GameTheme;
import com.findguni.model.GameItem;
import com.findguni.model.GameStage;
import com.findguni.model.GameFlowMode;
import com.findguni.model.PuzzleType;
import com.findguni.model.Role;
import com.findguni.model.UserAccount;
import com.findguni.repository.PlaySessionRepository;
import com.findguni.service.AccountService;
import com.findguni.service.AnonymousDeviceService;
import com.findguni.service.GameAuthoringService;
import com.findguni.service.PublishingService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "findguni.seed.admin.enabled=false",
        "findguni.seed.demo.enabled=false",
        "findguni.answers.hmac-secret=test-answer-secret-with-enough-length",
        "spring.datasource.url=jdbc:h2:mem:findguni-web-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.thymeleaf.cache=false",
        "spring.jpa.show-sql=false",
        "debug=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@AutoConfigureMockMvc
@Transactional
class PlatformWebIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private GameAuthoringService authoring;

    @Autowired
    private PublishingService publishing;

    @Autowired
    private PlaySessionRepository playSessions;

    @Test
    void publicPagesAreOpenButMakerDashboardRedirectsToLogin() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
        mvc.perform(get("/"))
                .andExpect(status().isOk());
        mvc.perform(get("/games"))
                .andExpect(status().isOk());
        mvc.perform(get("/signup"))
                .andExpect(status().isOk());
        mvc.perform(get("/login"))
                .andExpect(status().isOk());

        mvc.perform(get("/maker"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void makerCanSignUpLogInAndOpenDashboard() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "web-maker+" + suffix + "@example.com";
        MockHttpSession browser = new MockHttpSession();
        CsrfToken signupCsrf = csrf("/signup", browser);

        mvc.perform(post("/signup")
                        .session(browser)
                        .param(signupCsrf.getParameterName(), signupCsrf.getToken())
                        .param("email", email)
                        .param("displayName", "웹 메이커")
                        .param("password", "password-123")
                        .param("confirmPassword", "password-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"));

        CsrfToken loginCsrf = csrf("/login", browser);
        MvcResult login = mvc.perform(post("/login")
                        .session(browser)
                        .param(loginCsrf.getParameterName(), loginCsrf.getToken())
                        .param("email", email)
                        .param("password", "password-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maker"))
                .andReturn();

        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(authenticated).isNotNull();
        mvc.perform(get("/maker").session(authenticated))
                .andExpect(status().isOk());
    }

    @Test
    void makerCannotOpenAnotherMakersBuilder() throws Exception {
        UserAccount owner = signup("mvc-owner");
        UserAccount intruder = signup("mvc-intruder");
        EscapeGame game = createQuickGame(owner, "private-builder");
        MockHttpSession ownerBrowser = login(owner.getEmail(), "password-123", "/maker");
        MockHttpSession intruderBrowser = login(intruder.getEmail(), "password-123");

        mvc.perform(get("/maker/games/{id}/edit", game.getId()).session(ownerBrowser))
                .andExpect(status().isOk());
        mvc.perform(get("/maker/games/{id}/edit", game.getId()).session(intruderBrowser))
                .andExpect(status().isNotFound());
    }

    @Test
    void platformDashboardRendersForAdminWithPublishedActivity() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount admin = accounts.ensureSeedAccount("admin+" + suffix + "@example.com",
                "admin-password-123", "테스트 관리자", Role.ADMIN);
        UserAccount maker = signup("platform-maker");
        EscapeGame game = createQuickGame(maker, "platform-published");
        publishing.publish(game.getId(), maker);
        MockHttpSession adminBrowser = login(admin.getEmail(), "admin-password-123", "/platform");

        mvc.perform(get("/platform").session(adminBrowser))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("stats", "makers", "games", "recentActivity"));
    }

    @Test
    void anonymousCookieResumesPublishedPlayAndCorrectAnswerAdvancesStage() throws Exception {
        UserAccount owner = signup("mvc-player-owner");
        EscapeGame game = createQuickGame(owner, "cookie-resume");
        GameItem reward = authoring.addItem(game.getId(), owner, "은빛 열쇠", "마지막 문을 여는 열쇠", "🗝️");
        GameStage lock = authoring.stages(game.getId(), owner).stream()
                .filter(stage -> stage.getPuzzleType() == PuzzleType.NUMBER_LOCK)
                .findFirst()
                .orElseThrow();
        authoring.updateStage(lock.getId(), owner, new GameAuthoringService.StageDraft(
                lock.getTitle(), lock.getStory(), lock.getInstruction(), lock.getHint(),
                lock.getPuzzleType(), "1234", lock.getOptionsText(), lock.getLockLength(),
                lock.getRequiredItem(), reward.getStableKey()));
        publishing.publish(game.getId(), owner);
        MockHttpSession browser = new MockHttpSession();
        CsrfToken csrf = csrf("/play/" + game.getSlug(), browser);

        mvc.perform(get("/play/{slug}", game.getSlug()).session(browser))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasActiveSession", false));

        MvcResult start = mvc.perform(post("/play/{slug}/start", game.getSlug())
                        .session(browser)
                        .param(csrf.getParameterName(), csrf.getToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/play/" + game.getSlug() + "/stage"))
                .andExpect(cookie().httpOnly(AnonymousDeviceService.COOKIE_NAME, true))
                .andReturn();

        Cookie device = start.getResponse().getCookie(AnonymousDeviceService.COOKIE_NAME);
        assertThat(device).isNotNull();
        assertThat(start.getResponse().getHeader("Set-Cookie")).contains("SameSite=Lax");

        mvc.perform(get("/play/{slug}/stage", game.getSlug())
                        .session(browser).cookie(device))
                .andExpect(status().isOk())
                .andExpect(model().attribute("stageNumber", 1))
                .andExpect(model().attribute("stageCount", 3))
                .andExpect(model().attributeExists("stage"));

        long sessionsBeforeResume = playSessions.count();
        mvc.perform(post("/play/{slug}/start", game.getSlug())
                        .session(browser).cookie(device)
                        .param(csrf.getParameterName(), csrf.getToken()))
                .andExpect(status().is3xxRedirection());
        assertThat(playSessions.count()).isEqualTo(sessionsBeforeResume);

        mvc.perform(post("/play/{slug}/solve", game.getSlug())
                        .session(browser).cookie(device)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("answer", ""))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/play/{slug}/stage", game.getSlug())
                        .session(browser).cookie(device))
                .andExpect(status().isOk())
                .andExpect(model().attribute("stageNumber", 2))
                .andExpect(model().attribute("stage", org.hamcrest.Matchers.hasProperty(
                        "puzzleType", org.hamcrest.Matchers.is(PuzzleType.NUMBER_LOCK))));

        mvc.perform(post("/play/{slug}/solve", game.getSlug())
                        .session(browser).cookie(device)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("code", "12-34"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/play/{slug}/stage", game.getSlug())
                        .session(browser).cookie(device))
                .andExpect(status().isOk())
                .andExpect(model().attribute("stageNumber", 3))
                .andExpect(model().attribute("inventory", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.hasProperty("stableKey", org.hamcrest.Matchers.is(reward.getStableKey())),
                                org.hamcrest.Matchers.hasProperty("emoji", org.hamcrest.Matchers.is("🗝️"))))));

        mvc.perform(post("/play/{slug}/solve", game.getSlug())
                        .session(browser).cookie(device)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("answer", ""))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/play/{slug}/stage", game.getSlug())
                        .session(browser).cookie(device))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("playSummary"));
    }

    @Test
    void qrExplorationStartsAtHuntHubAndProblemQrOpensThatProblem() throws Exception {
        UserAccount owner = signup("qr-hunt-owner");
        EscapeGame game = authoring.create(owner, "QR 탐색 게임", "qr-hunt-" + UUID.randomUUID(),
                "BLANK", GameTheme.FOREST, Difficulty.NORMAL, 30, GameFlowMode.QR_EXPLORATION);
        GameStage stage = authoring.stages(game.getId(), owner).get(0);
        publishing.publish(game.getId(), owner);
        MockHttpSession browser = new MockHttpSession();
        CsrfToken csrf = csrf("/play/" + game.getSlug(), browser);

        MvcResult start = mvc.perform(post("/play/{slug}/start", game.getSlug())
                        .session(browser).param(csrf.getParameterName(), csrf.getToken()))
                .andExpect(status().is3xxRedirection()).andReturn();
        Cookie device = start.getResponse().getCookie(AnonymousDeviceService.COOKIE_NAME);

        mvc.perform(get("/play/{slug}/stage", game.getSlug()).session(browser).cookie(device))
                .andExpect(status().isOk())
                .andExpect(view().name("player/hunt"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("주변의 QR을 찾아보세요")));

        mvc.perform(get("/play/{slug}/puzzle/{key}", game.getSlug(), stage.getStableKey())
                        .session(browser).cookie(device))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/play/" + game.getSlug() + "/stage"));
        mvc.perform(get("/play/{slug}/stage", game.getSlug()).session(browser).cookie(device))
                .andExpect(status().isOk())
                .andExpect(view().name("player/stage"))
                .andExpect(model().attribute("stage", org.hamcrest.Matchers.hasProperty(
                        "stableKey", org.hamcrest.Matchers.is(stage.getStableKey()))));
    }

    private CsrfToken csrf(String page, MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get(page).session(session))
                .andExpect(status().isOk())
                .andReturn();
        Object token = result.getRequest().getAttribute("_csrf");
        assertThat(token).isInstanceOf(CsrfToken.class);
        return (CsrfToken) token;
    }

    private MockHttpSession login(String email, String password) throws Exception {
        return login(email, password, "/maker");
    }

    private MockHttpSession login(String email, String password, String expectedRedirect) throws Exception {
        MockHttpSession browser = new MockHttpSession();
        CsrfToken token = csrf("/login", browser);
        MvcResult result = mvc.perform(post("/login")
                        .session(browser)
                        .param(token.getParameterName(), token.getToken())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expectedRedirect))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UserAccount signup(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return accounts.signupMaker(prefix + "+" + suffix + "@example.com",
                "password-123", "password-123", prefix);
    }

    private EscapeGame createQuickGame(UserAccount owner, String slugPrefix) {
        return authoring.create(owner, "테스트 방탈출", slugPrefix + "-" + UUID.randomUUID(),
                "QUICK", GameTheme.MIDNIGHT, Difficulty.NORMAL, 30);
    }
}
