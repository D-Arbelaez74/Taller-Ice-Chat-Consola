package chat.client.files;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import chat.client.ClientContext;
import chat.client.cli.CommandShell;
import chat.contract.ChatException;
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

    // El envio de un archivo grande tarda; va en hilos aparte para que la consola siga libre.
    // Hilos daemon: no impiden que la JVM cierre al hacer /exit.
    private final ExecutorService sendPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "file-sender");
        t.setDaemon(true);
        return t;
    });

    public FileController(ClientContext ctx) {
        this.ctx = ctx;
        this.sender = new FileSender(ctx);
        this.assembler = new FileAssembler(ctx);
    }

    public void registerCommands(CommandShell shell) {
        shell.register("/sendfile", "/sendfile <usuario|#sala> <ruta>", "Envia un archivo a un usuario o a una sala", args -> {
            // Primer token = destino; el resto es la ruta (puede llevar espacios y comillas)
            String[] parts = args.trim().split("\\s+", 2);
            if (parts.length < 2 || parts[1].isBlank()) {
                System.out.println("Uso: /sendfile <usuario> <ruta>  o  /sendfile #<sala> <ruta>");
                return;
            }
            boolean toRoom = parts[0].startsWith("#");
            String target = toRoom ? parts[0].substring(1) : parts[0];
            if (target.isEmpty()) {
                System.out.println("Falta el nombre de la sala. Uso: /sendfile #<sala> <ruta>");
                return;
            }
            Path path = Paths.get(stripQuotes(parts[1].trim()));

            sendPool.submit(() -> {
                try {
                    sender.send(target, toRoom, path);
                } catch (ChatException e) {
                    System.out.println("[archivo] No se pudo enviar '" + path.getFileName() + "': " + e.reason);
                } catch (Exception e) {
                    System.out.println("[archivo] No se pudo enviar '" + path.getFileName() + "': " + e.getMessage());
                }
            });
        });
    }

    // Permite arrastrar el archivo a la consola o escribir la ruta entre comillas
    private static String stripQuotes(String s) {
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
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
