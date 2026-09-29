package chat.server.session;

import chat.contract.ClientCallbackPrx;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro thread-safe de usuarios conectados y sus proxies de callback.
 * Lo usan todos los servants para localizar al destinatario de un evento.
 * Responsable: P1
 */
public class SessionManager {

    private final Map<String, ClientCallbackPrx> clients = new ConcurrentHashMap<>();

    /** @return false si el nickname ya estaba en uso (operacion atomica). */
    public boolean register(String nickname, ClientCallbackPrx callback) {
        return clients.putIfAbsent(nickname, callback) == null;
    }

    public boolean unregister(String nickname) {
        return clients.remove(nickname) != null;
    }

    public Optional<ClientCallbackPrx> callbackOf(String nickname) {
        return Optional.ofNullable(clients.get(nickname));
    }

    public boolean isOnline(String nickname) {
        return clients.containsKey(nickname);
    }

    public String[] onlineUsers() {
        return clients.keySet().toArray(new String[0]);
    }

    public Map<String, ClientCallbackPrx> snapshot() {
        return Map.copyOf(clients);
    }

    // TODO (P1): notificar conexion/desconexion a todos (onUserConnected / onUserDisconnected)
    // TODO (P1): si un callback lanza excepcion de conexion -> remover al cliente (tolerancia a fallos)
}
