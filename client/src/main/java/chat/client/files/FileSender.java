package chat.client.files;

import chat.client.ClientContext;

/**
 * Lee un archivo del disco y lo envia en chunks via FileService.
 * Responsable: P3
 */
public class FileSender {

    public static final int CHUNK_SIZE = 64 * 1024;

    private final ClientContext ctx;

    public FileSender(ClientContext ctx) {
        this.ctx = ctx;
    }

    // TODO (P3): send(target, toRoom, path): beginTransfer -> sendChunk x N -> endTransfer(sha256)
}
