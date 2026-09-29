package chat.server.calls;

import chat.contract.CallService;
import chat.contract.ChatException;
import chat.contract.VoiceEndpoint;
import chat.server.session.SessionManager;
import com.zeroc.Ice.Current;

/**
 * RF-05: senalizacion de llamadas 1 a 1. El servidor solo intercambia endpoints UDP;
 * el audio viaja directo entre clientes.
 * Responsable: P4
 */
public class CallServiceI implements CallService {

    private final SessionManager sessions;

    public CallServiceI(SessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public void startCall(String from, String to, VoiceEndpoint endpoint, Current current) throws ChatException {
        // TODO (P4): validar que el destino este conectado y libre, notificar onIncomingCall
        throw new ChatException("startCall: no implementado");
    }

    @Override
    public void answerCall(String callee, String caller, boolean accepted, VoiceEndpoint endpoint, Current current)
            throws ChatException {
        throw new ChatException("answerCall: no implementado");
    }

    @Override
    public void hangup(String from, String peer, Current current) throws ChatException {
        throw new ChatException("hangup: no implementado");
    }
}
