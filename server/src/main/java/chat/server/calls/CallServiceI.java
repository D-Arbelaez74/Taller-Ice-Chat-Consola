package chat.server.calls;

import com.zeroc.Ice.Current;
import chat.contract.*;
import chat.server.messaging.RoomManager;
import chat.server.session.SessionManager;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CallServiceI implements CallService {

    private final SessionManager sessionManager;
    private final RoomManager rooms;

    // Sala -> llamada grupal que tiene activa en este momento
    private final Map<String, GroupCall> groupCalls = new ConcurrentHashMap<>();
    private final AtomicInteger nextPort = new AtomicInteger(20000);
    private final AtomicInteger nextSenderId = new AtomicInteger(1);

    public CallServiceI(SessionManager sessionManager, RoomManager rooms) {
        this.sessionManager = sessionManager;
        this.rooms = rooms;
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

    // ==========================================
    // LLAMADAS GRUPALES POR BROADCAST (RF-06)
    // ==========================================

    // El servidor no toca el audio. Solo decide quien participa: a cada uno que entra le entrega
    // el puerto UDP de la llamada, y solo los que tienen ese puerto abierto reciben el broadcast.
    // Usamos synchronized para que solo entre o salga una persona a la vez. Si dos lo hicieran
    // al mismo tiempo podrian crearse dos llamadas distintas para la misma sala
    @Override
    public synchronized GroupCallInfo joinGroupCall(String nickname, String room, Current current) throws ChatException {
        if (!sessionManager.isOnline(nickname)) {
            throw new NotLoggedInException("Debes iniciar sesion antes de llamar");
        }
        if (!rooms.exists(room)) {
            throw new CallException("La sala " + room + " no existe");
        }
        // Solo los miembros de la sala pueden entrar a su llamada, asi el audio no sale del grupo
        if (!rooms.isMember(room, nickname)) {
            throw new CallException("Debes unirte a la sala " + room + " antes de entrar a su llamada");
        }

        GroupCall call = groupCalls.get(room);
        boolean isNewCall = (call == null);
        if (isNewCall) {
            // Cada llamada usa un puerto distinto para que no se mezcle el audio de dos salas
            call = new GroupCall(nextPort.getAndIncrement());
            groupCalls.put(room, call);
        }

        // Quitamos a los que se desconectaron sin salir de la llamada
        call.participants.removeIf(participant -> !sessionManager.isOnline(participant));

        if (!call.participants.add(nickname)) {
            throw new CallException("Ya estas en la llamada de la sala " + room);
        }

        if (isNewCall) {
            // Le timbramos a los demas miembros de la sala, cada uno decide si contesta
            for (String member : rooms.members(room)) {
                if (!member.equals(nickname)) {
                    sessionManager.callbackOf(member).ifPresent(cb -> cb.onGroupCallInviteAsync(room, nickname));
                }
            }
        } else {
            // A los que ya estaban hablando les avisamos quien entro
            for (String participant : call.participants) {
                if (!participant.equals(nickname)) {
                    sessionManager.callbackOf(participant).ifPresent(cb -> cb.onGroupCallJoinedAsync(room, nickname));
                }
            }
        }

        return new GroupCallInfo(call.port, nextSenderId.getAndIncrement());
    }

    @Override
    public synchronized void leaveGroupCall(String nickname, String room, Current current) throws ChatException {
        GroupCall call = groupCalls.get(room);
        if (call == null || !call.participants.remove(nickname)) {
            return;
        }

        // Los demas siguen en la llamada, solo les avisamos que alguien salio
        for (String participant : call.participants) {
            sessionManager.callbackOf(participant).ifPresent(cb -> cb.onGroupCallLeftAsync(room, nickname));
        }

        // Si ya no queda nadie se termina la llamada de esa sala
        if (call.participants.isEmpty()) {
            groupCalls.remove(room);
        }
    }

    // Datos de la llamada grupal de una sala
    private static final class GroupCall {
        final int port;
        final Set<String> participants = ConcurrentHashMap.newKeySet();

        GroupCall(int port) {
            this.port = port;
        }
    }
}

