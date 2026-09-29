package chat.server.files;

import chat.contract.ChatException;
import chat.contract.FileChunk;
import chat.contract.FileMeta;
import chat.contract.FileService;
import chat.server.messaging.RoomManager;
import chat.server.session.SessionManager;
import com.zeroc.Ice.Current;

/**
 * RF-04: reenvia los chunks de un archivo al usuario o sala destino.
 * Responsable: P3
 */
public class FileServiceI implements FileService {

    private final SessionManager sessions;
    private final RoomManager rooms;

    public FileServiceI(SessionManager sessions, RoomManager rooms) {
        this.sessions = sessions;
        this.rooms = rooms;
    }

    @Override
    public void beginTransfer(FileMeta meta, Current current) throws ChatException {
        // TODO (P3): validar destino, recordar transferId -> destinatarios
        throw new ChatException("beginTransfer: no implementado");
    }

    @Override
    public void sendChunk(FileChunk chunk, Current current) throws ChatException {
        throw new ChatException("sendChunk: no implementado");
    }

    @Override
    public void endTransfer(String transferId, String sha256, Current current) throws ChatException {
        throw new ChatException("endTransfer: no implementado");
    }
}
