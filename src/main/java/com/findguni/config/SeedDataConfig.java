package com.findguni.config;

import com.findguni.model.*;
import com.findguni.repository.EscapeGameRepository;
import com.findguni.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedDataConfig {
    @Bean
    ApplicationRunner seedPlatformData(
            AccountService accounts,
            GameAuthoringService authoring,
            PublishingService publishing,
            DemoGameSeedService demoGames,
            DubuHousewarmingSeedService dubuHousewarming,
            EscapeGameRepository games,
            @Value("${findguni.seed.admin.enabled:true}") boolean adminEnabled,
            @Value("${findguni.seed.admin.email}") String adminEmail,
            @Value("${findguni.seed.admin.password}") String adminPassword,
            @Value("${findguni.seed.admin.display-name}") String adminName,
            @Value("${findguni.seed.demo.enabled:true}") boolean demoEnabled) {
        return args -> {
            if (adminEnabled) accounts.ensureSeedAccount(adminEmail, adminPassword, adminName, Role.ADMIN);
            if (demoEnabled) {
                UserAccount demo = accounts.ensureDemoSeedAccount("demo@findguni.local", "test",
                        "미드나잇 스튜디오");
                if (games.findBySlug("midnight-archive").isEmpty()) {
                    EscapeGame game = authoring.create(demo, "미드나잇 아카이브", "midnight-archive",
                            "MYSTERY", GameTheme.MIDNIGHT, Difficulty.NORMAL, 25);
                    authoring.updateSettings(game.getId(), demo, game.getTitle(), game.getSlug(),
                            "사라진 기록 보관사의 마지막 암호를 찾아 봉인된 문을 여세요.",
                            "자정이 되면 존재하지 않는 13번 서고의 문이 열린다는 소문이 있습니다. " +
                                    "손전등 하나를 들고 기록 보관사의 흔적을 따라가 보세요.",
                            "", "#8B5CF6", GameTheme.MIDNIGHT, Difficulty.NORMAL, 25, GameVisibility.PUBLIC);
                    publishing.publish(game.getId(), demo);
                }
                demoGames.ensureGuniRescueDemo();
                dubuHousewarming.ensureDubuHousewarming();
            }
        };
    }
}
