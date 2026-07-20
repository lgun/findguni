package com.findguni.service;

import com.findguni.model.*;
import com.findguni.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class PlatformService {
    private final UserAccountRepository accounts;
    private final EscapeGameRepository games;
    private final PlaySessionRepository plays;
    private final GameReleaseRepository releases;

    public PlatformService(UserAccountRepository accounts, EscapeGameRepository games,
                           PlaySessionRepository plays, GameReleaseRepository releases) {
        this.accounts = accounts;
        this.games = games;
        this.plays = plays;
        this.releases = releases;
    }

    @Transactional(readOnly = true)
    public PlatformStats stats() {
        return new PlatformStats(accounts.countByRole(Role.MAKER),
                accounts.countByRoleAndStatus(Role.MAKER, AccountStatus.ACTIVE),
                games.count(), games.countByStatus(GameStatus.PUBLISHED),
                plays.count(), plays.countByStatus(PlayStatus.ACTIVE),
                plays.countByStatus(PlayStatus.COMPLETED));
    }

    @Transactional(readOnly = true)
    public List<Activity> recentActivity() {
        return releases.findTop20ByOrderByPublishedAtDesc().stream()
                .map(release -> new Activity(release.getGame().getTitle() + " 발행",
                        release.getGame().getOwner().getDisplayName() + " · 버전 " + release.getVersionNumber(),
                        java.time.format.DateTimeFormatter.ofPattern("MM.dd HH:mm")
                                .withZone(java.time.ZoneId.systemDefault()).format(release.getPublishedAt())))
                .toList();
    }

    public record PlatformStats(long totalMakers, long activeMakers, long totalGames,
                                long publishedGames, long totalPlays, long activePlays,
                                long completedPlays) {
        public long getTotalMakers() { return totalMakers; }
        public long getActiveMakers() { return activeMakers; }
        public long getTotalGames() { return totalGames; }
        public long getPublishedGames() { return publishedGames; }
        public long getTotalPlays() { return totalPlays; }
        public long getActivePlays() { return activePlays; }
        public long getCompletedPlays() { return completedPlays; }
        public long getMakerCount() { return totalMakers; }
        public long getGameCount() { return totalGames; }
        public long getPublishedCount() { return publishedGames; }
        public long getActivePlayCount() { return activePlays; }
    }

    public record Activity(String title, String detail, String timeDisplay) {
        public String getTitle() { return title; }
        public String getDetail() { return detail; }
        public String getTimeDisplay() { return timeDisplay; }
    }
}
