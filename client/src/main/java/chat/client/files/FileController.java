package chat.client.files;

import chat.client.ClientContext;
import chat.client.cli.CommandShell;
import chat.contract.FileChunk;
import chat.contract.FileMeta;

/**
 * RF-04 lado cliente: /sendfile y recepcion de archivos.
 * Responsable: P3
 */
public class FileController {

    private final ClientContext ctx;
    private final FileSender sender;
    private final FileAssembler assembler;

    public FileController(ClientContext ctx) {
        this.ctx = ctx;
        this.sender = new FileSender(ctx);
        this.assembler = new FileAssembler(ctx);
    }

    public void registerCommands(CommandShell shell) {
        // TODO (P3): /sendfile <usuario> <ruta>  y  /sendfile #<sala> <ruta>  (en un hilo aparte)
    }

    public void onFileStart(FileMeta meta) {
        assembler.start(meta);
    }

    public void onFileChunk(FileChunk chunk) {
        assembler.append(chunk);
    }

    public void onFileEnd(String transferId, String sha256) {
        assembler.finish(transferId, sha256);
    }
}
