package chat.client.files;

import chat.client.ClientContext;
import chat.contract.FileChunk;
import chat.contract.FileMeta;

/**
 * Reconstruye en disco (Chat.Downloads.Dir) los archivos recibidos y verifica su integridad.
 * Responsable: P3
 */
public class FileAssembler {

    private final ClientContext ctx;

    public FileAssembler(ClientContext ctx) {
        this.ctx = ctx;
    }

    public void start(FileMeta meta) {
        // TODO (P3): abrir archivo temporal por transferId (mapa concurrente)
    }

    public void append(FileChunk chunk) {
        // TODO (P3): escribir chunk en su posicion (index * CHUNK_SIZE)
    }

    public void finish(String transferId, String sha256) {
        // TODO (P3): cerrar, comparar SHA-256 y avisar por consola
    }
}
