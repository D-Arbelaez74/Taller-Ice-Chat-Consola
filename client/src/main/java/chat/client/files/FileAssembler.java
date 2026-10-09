package chat.client.files;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import chat.client.ClientContext;
import chat.contract.FileChunk;
import chat.contract.FileMeta;

/**
 * Reconstruye en disco (Chat.Downloads.Dir) los archivos recibidos y verifica su integridad.
 * Escribe en un archivo temporal ".part" y solo lo renombra al nombre final cuando el
 * tamano y el SHA-256 coinciden, asi nunca queda en downloads/ un archivo truncado o corrupto.
 * Responsable: P3
 */
public class FileAssembler {

    /** Una descarga en curso. Los chunks llegan en orden desde el servidor, un hilo a la vez. */
    private static final class Download {
        final FileMeta meta;
        final Path partFile;
        final OutputStream out;
        final MessageDigest digest = newDigest();
        int nextIndex = 0;
        long receivedBytes = 0;

        Download(FileMeta meta, Path partFile, OutputStream out) {
            this.meta = meta;
            this.partFile = partFile;
            this.out = out;
        }
    }

    private final ClientContext ctx;
    // transferId -> descarga en curso. Concurrente porque pueden llegar varias transferencias a la vez
    private final Map<String, Download> downloads = new ConcurrentHashMap<>();

    public FileAssembler(ClientContext ctx) {
        this.ctx = ctx;
    }

    public void start(FileMeta meta) {
        try {
            Path dir = downloadsDir();
            Files.createDirectories(dir);
            // El transferId en el nombre evita choques entre dos transferencias del mismo archivo a la vez
            Path part = dir.resolve(safeName(meta.fileName) + "." + meta.transferId.substring(0, 8) + ".part");
            OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            downloads.put(meta.transferId, new Download(meta, part, out));
            System.out.println("[archivo] " + meta.sender + (meta.toRoom ? " (en #" + meta.target + ")" : "")
                    + " te esta enviando '" + meta.fileName + "' (" + humanSize(meta.totalSize) + ")...");
        } catch (IOException e) {
            System.out.println("[archivo] No se pudo preparar la descarga de '" + meta.fileName + "': " + e.getMessage());
        }
    }

    public void append(FileChunk chunk) {
        Download d = downloads.get(chunk.transferId);
        if (d == null) {
            return; // transferencia que no pudimos iniciar o ya cancelada
        }
        try {
            if (chunk.index != d.nextIndex) {
                throw new IOException("fragmento fuera de orden (llego " + chunk.index + ", esperaba " + d.nextIndex + ")");
            }
            d.out.write(chunk.data);
            d.digest.update(chunk.data);
            d.nextIndex++;
            d.receivedBytes += chunk.data.length;
        } catch (IOException e) {
            discard(chunk.transferId, d, "error al escribir: " + e.getMessage());
        }
    }

    public void finish(String transferId, String sha256) {
        Download d = downloads.remove(transferId);
        if (d == null) {
            return;
        }
        closeQuietly(d);
        FileMeta meta = d.meta;

        // Convencion con el servidor: hash vacio = el emisor o el servidor cancelaron la transferencia
        if (sha256 == null || sha256.isEmpty()) {
            deleteQuietly(d.partFile);
            System.out.println("[archivo] Transferencia de '" + meta.fileName + "' cancelada (" + meta.sender + ")");
            return;
        }
        if (d.nextIndex != meta.totalChunks || d.receivedBytes != meta.totalSize) {
            deleteQuietly(d.partFile);
            System.out.println("[archivo] '" + meta.fileName + "' llego incompleto (" + d.receivedBytes + " de "
                    + meta.totalSize + " bytes). Se descarto.");
            return;
        }
        String actual = HexFormat.of().formatHex(d.digest.digest());
        if (!actual.equalsIgnoreCase(sha256)) {
            deleteQuietly(d.partFile);
            System.out.println("[archivo] '" + meta.fileName + "' llego corrupto (SHA-256 no coincide). Se descarto.");
            return;
        }
        try {
            Path finalPath = moveToFinalName(d.partFile, safeName(meta.fileName));
            System.out.println("[archivo] Recibido '" + meta.fileName + "' de " + meta.sender + " ("
                    + humanSize(meta.totalSize) + ") -> " + finalPath + "  [SHA-256 verificado]");
        } catch (IOException e) {
            System.out.println("[archivo] No se pudo guardar '" + meta.fileName + "': " + e.getMessage());
        }
    }

    // --- Helpers

    private Path downloadsDir() {
        return Paths.get(ctx.communicator.getProperties().getPropertyWithDefault("Chat.Downloads.Dir", "downloads"));
    }

    // Si ya existe un archivo con ese nombre no lo pisamos: "foto.png" -> "foto (1).png"
    private static Path moveToFinalName(Path part, String name) throws IOException {
        Path dir = part.getParent();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        Path target = dir.resolve(name);
        for (int n = 1; ; n++) {
            try {
                // Sin opciones: Files.move falla si el destino existe (ATOMIC_MOVE lo pisaria en Windows)
                return Files.move(part, target);
            } catch (FileAlreadyExistsException e) {
                target = dir.resolve(base + " (" + n + ")" + ext);
            }
        }
    }

    // El nombre lo manda otro cliente: nos quedamos solo con el ultimo componente y quitamos caracteres de ruta
    private static String safeName(String raw) {
        String name = raw == null ? "" : raw;
        int cut = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(cut + 1).replaceAll("[<>:\"|?*\\p{Cntrl}]", "_").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "archivo";
        }
        return name;
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return mb < 1024 ? String.format("%.1f MB", mb) : String.format("%.2f GB", mb / 1024.0);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private void discard(String transferId, Download d, String reason) {
        downloads.remove(transferId);
        closeQuietly(d);
        deleteQuietly(d.partFile);
        System.out.println("[archivo] Se cancelo la descarga de '" + d.meta.fileName + "': " + reason);
    }

    private static void closeQuietly(Download d) {
        try {
            d.out.close();
        } catch (IOException ignored) {
            // si no cierra, el archivo parcial se borra o se descarta igual
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // un .part huerfano no afecta a los archivos ya recibidos
        }
    }
}
