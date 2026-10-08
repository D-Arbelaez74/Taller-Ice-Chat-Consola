package chat.client.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

import chat.client.ClientContext;
import chat.contract.ChatException;
import chat.contract.FileChunk;
import chat.contract.FileMeta;

/**
 * Lee un archivo del disco y lo envia en chunks via FileService.
 * Nunca carga el archivo completo en memoria: lee y envia un bloque a la vez.
 * Responsable: P3
 */
public class FileSender {

    public static final int CHUNK_SIZE = 64 * 1024;

    private final ClientContext ctx;

    public FileSender(ClientContext ctx) {
        this.ctx = ctx;
    }

    /** @param target nickname del usuario o nombre de la sala */
    public void send(String target, boolean toRoom, Path path) throws IOException, ChatException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("No existe el archivo: " + path);
        }
        long totalSize = Files.size(path);
        if (totalSize == 0) {
            throw new IOException("El archivo esta vacio, no hay nada que enviar");
        }
        int totalChunks = (int) ((totalSize + CHUNK_SIZE - 1) / CHUNK_SIZE);
        String fileName = path.getFileName().toString();
        String transferId = UUID.randomUUID().toString();

        FileMeta meta = new FileMeta(transferId, fileName, totalSize, totalChunks, ctx.nickname(), target, toRoom);
        MessageDigest digest = newDigest();

        // Si el destino no existe, falla aqui antes de leer el archivo
        ctx.files.beginTransfer(meta);
        System.out.println("[archivo] Enviando '" + fileName + "' (" + totalChunks + " fragmentos) a "
                + (toRoom ? "#" : "") + target + "...");

        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            for (int index = 0; index < totalChunks; index++) {
                int read = in.readNBytes(buffer, 0, CHUNK_SIZE);
                if (read <= 0) {
                    throw new IOException("El archivo cambio mientras se enviaba");
                }
                // El ultimo bloque suele ser mas corto: se recorta para no enviar basura
                byte[] data = Arrays.copyOf(buffer, read);
                digest.update(data);
                ctx.files.sendChunk(new FileChunk(transferId, index, data));
            }
        } catch (IOException | ChatException | RuntimeException e) {
            cancel(transferId); // el servidor cancela y los receptores borran lo parcial
            throw e;
        }

        ctx.files.endTransfer(transferId, HexFormat.of().formatHex(digest.digest()));
        System.out.println("[archivo] '" + fileName + "' enviado.");
    }

    // endTransfer con hash vacio = cancelar
    private void cancel(String transferId) {
        try {
            ctx.files.endTransfer(transferId, "");
        } catch (Exception ignored) {
            // ya estaba cancelada o el servidor no responde
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
