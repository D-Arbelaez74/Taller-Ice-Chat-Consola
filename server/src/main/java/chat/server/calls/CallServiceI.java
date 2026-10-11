package chat.server.calls;

import com.zeroc.Ice.Current;
import chat.contract.*;
import chat.server.session.SessionManager;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CallServiceI implements CallService {

    private final SessionManager sessionManager;
    private final Map<String, Map<String, VoiceEndpoint>> roomEndpoints = new ConcurrentHashMap<>();

    public CallServiceI(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void startCall(String from, String to, VoiceEndpoint endpoint, Current current) throws ChatException {
        // Solo puede llamar alguien que ya hizo login, y no tiene sentido llamarse a uno mismo
        if (!sessionManager.isOnline(from)) {
            throw new NotLoggedInException("Debes iniciar sesion antes de llamar");
        }
        if (from.equals(to)) {
            throw new CallException("No puedes llamarte a ti mismo");
        }

        Optional<ClientCallbackPrx> targetObserver = sessionManager.callbackOf(to);

        if (!targetObserver.isPresent()) {
            UserNotFoundException ex = new UserNotFoundException();
            ex.reason = "El usuario " + to + " no esta disponible.";
            throw ex;
        }

        targetObserver.get().onIncomingCall(from, endpoint);
    }

    @Override
    public void answerCall(String callee, String caller, boolean accepted, VoiceEndpoint endpoint, Current current) throws ChatException {
        Optional<ClientCallbackPrx> callerObserver = sessionManager.callbackOf(caller);

        if (callerObserver.isPresent()) {
            if (accepted) {
                callerObserver.get().onCallAccepted(callee, endpoint);
            } else {
                callerObserver.get().onCallRejected(callee);
            }
        }
    }

    @Override
    public void hangup(String from, String peer, Current current) throws ChatException {
        Optional<ClientCallbackPrx> observer = sessionManager.callbackOf(peer);
        if (observer.isPresent()) {
            observer.get().onCallEnded(from);
        }
    }

    @Override
    public void joinGroupCall(String nickname, String room, VoiceEndpoint endpoint, Current current) throws ChatException {
        roomEndpoints.putIfAbsent(room, new ConcurrentHashMap<>());
        Map<String, VoiceEndpoint> usersInRoom = roomEndpoints.get(room);
        
        usersInRoom.put(nickname, endpoint);

        syncGroupCall(room, usersInRoom);
    }

    @Override
    public void leaveGroupCall(String nickname, String room, Current current) throws ChatException {
        Map<String, VoiceEndpoint> usersInRoom = roomEndpoints.get(room);
        if (usersInRoom != null) {
            usersInRoom.remove(nickname);
            syncGroupCall(room, usersInRoom);
        }
    }

    // Le manda a cada participante la lista de los demas. No incluimos su propia direccion
    // porque si no se enviaria el audio a si mismo y escucharia su propia voz (eco)
    private void syncGroupCall(String room, Map<String, VoiceEndpoint> usersInRoom) {
        for (String participant : usersInRoom.keySet()) {
            Optional<ClientCallbackPrx> obs = sessionManager.callbackOf(participant);
            if (obs.isPresent()) {
                VoiceEndpoint[] others = usersInRoom.entrySet().stream()
                        .filter(entry -> !entry.getKey().equals(participant))
                        .map(Map.Entry::getValue)
                        .toArray(VoiceEndpoint[]::new);
                obs.get().onGroupCallSync(room, others);
            }
        }
    }
}

