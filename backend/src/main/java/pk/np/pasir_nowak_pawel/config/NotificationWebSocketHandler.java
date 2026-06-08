package pk.np.pasir_nowak_pawel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pk.np.pasir_nowak_pawel.dto.GroupExpenseNotification;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Principal principal = session.getPrincipal();
        if (principal != null) {
            sessions.put(principal.getName(), session);
            System.out.println("PoĹ‚Ä…czono WebSocket dla: " + principal.getName());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Principal principal = session.getPrincipal();
        if (principal != null) {
            sessions.remove(principal.getName());
        }
    }

    public void sendNotificationToUser(String email, GroupExpenseNotification notification) {
        WebSocketSession session = sessions.get(email);
        if (session != null && session.isOpen()) {
            try {
                String payload = objectMapper.writeValueAsString(notification);
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}