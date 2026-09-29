package chat.server.messaging;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Salas de chat y sus miembros (thread-safe).
 * Responsable: P2
 */
public class RoomManager {

    // sala -> miembros
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();

    // TODO (P2): create, join, leave, members, list, removeUserFromAll (lo usa el logout de P1)
}
