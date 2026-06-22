package fscbridge_web.websocket;

import fscbridge_web.kafka.event.MigrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationProgressHandler {


    private final SimpMessagingTemplate messagingTemplate;


    public void sendProgressUpdate(MigrationEvent event) {
        String destination = "/topic/migration-progress/"
                + event.getJobId();

        log.debug("Sending WebSocket update to {}: {} | {}%",
                destination,
                event.getEventType(),
                event.getProgressPercent());

        try {
            messagingTemplate.convertAndSend(destination, event);
        } catch (Exception e) {
            log.error("WebSocket send failed for job {}: {}",
                    event.getJobId(), e.getMessage());
            // Never throw — WebSocket failure should not
            // affect the migration or Kafka processing
        }
    }


    public void broadcastSystemMessage(String message) {
        messagingTemplate.convertAndSend(
                "/topic/system",
                message
        );
    }
}