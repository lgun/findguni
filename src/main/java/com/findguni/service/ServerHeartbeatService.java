package com.findguni.service;

import com.findguni.model.ServerHeartbeat;
import com.findguni.repository.ServerHeartbeatRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServerHeartbeatService {
    private final ServerHeartbeatRepository heartbeats;
    private final String serverName;

    public ServerHeartbeatService(
            ServerHeartbeatRepository heartbeats,
            @Value("${findguni.server-name:}") String serverName,
            @Value("${HOSTNAME:}") String hostName) {
        this.heartbeats = heartbeats;
        String preferred = serverName != null && !serverName.isBlank() ? serverName : (hostName != null ? hostName : null);
        this.serverName = (preferred == null || preferred.isBlank()) ? "server" : preferred;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @Transactional
    public void recordHeartbeat() {
        heartbeats.save(new ServerHeartbeat(serverName));
    }
}
