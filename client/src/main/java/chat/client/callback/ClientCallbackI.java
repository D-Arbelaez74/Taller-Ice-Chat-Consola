package chat.client.callback;

import chat.client.files.FileController;
import chat.client.messaging.MessagingController;
import chat.client.session.SessionController;
import chat.client.voice.CallController;
import chat.contract.ClientCallback;
import chat.contract.FileChunk;
import chat.contract.FileMeta;
import chat.contract.TextMessage;
import chat.contract.VoiceEndpoint;
import com.zeroc.Ice.Current;

/**
 * Servant que el servidor invoca de forma asincrona. Solo delega en el controller
 * de cada modulo, asi nadie necesita modificar esta clase.
 */
public class ClientCallbackI implements ClientCallback {

    private final SessionController session;
    private final MessagingController messaging;
    private final FileController files;
    private final CallController calls;

    public ClientCallbackI(SessionController session, MessagingController messaging,
                           FileController files, CallController calls) {
        this.session = session;
        this.messaging = messaging;
        this.files = files;
        this.calls = calls;
    }

    // --- Presencia (P1)
    @Override
    public void onUserConnected(String nickname, Current current) {
        session.onUserConnected(nickname);
    }

    @Override
    public void onUserDisconnected(String nickname, Current current) {
        session.onUserDisconnected(nickname);
    }

    // --- Mensajes (P2)
    @Override
    public void onPrivateMessage(TextMessage msg, Current current) {
        messaging.onPrivateMessage(msg);
    }

    @Override
    public void onRoomMessage(TextMessage msg, Current current) {
        messaging.onRoomMessage(msg);
    }

    // --- Archivos (P3)
    @Override
    public void onFileStart(FileMeta meta, Current current) {
        files.onFileStart(meta);
    }

    @Override
    public void onFileChunk(FileChunk chunk, Current current) {
        files.onFileChunk(chunk);
    }

    @Override
    public void onFileEnd(String transferId, String sha256, Current current) {
        files.onFileEnd(transferId, sha256);
    }

    // --- Llamadas (P4)
    @Override
    public void onIncomingCall(String caller, VoiceEndpoint callerEndpoint, Current current) {
        calls.onIncomingCall(caller, callerEndpoint);
    }

    @Override
    public void onCallAccepted(String callee, VoiceEndpoint calleeEndpoint, Current current) {
        calls.onCallAccepted(callee, calleeEndpoint);
    }

    @Override
    public void onCallRejected(String callee, Current current) {
        calls.onCallRejected(callee);
    }

    @Override
    public void onCallEnded(String peer, Current current) {
        calls.onCallEnded(peer);
    }
}
