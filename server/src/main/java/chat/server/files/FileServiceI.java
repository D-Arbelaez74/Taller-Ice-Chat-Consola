package chat.server.files;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.zeroc.Ice.Current;

import chat.contract.ChatException;
import chat.contract.ClientCallbackPrx;
import chat.contract.FileChunk;
import chat.contract.FileMeta;
import chat.contract.FileService;
import chat.contract.FileTransferException;
import chat.contract.NotLoggedInException;
import chat.contract.RoomException;
import chat.contract.UserNotFoundException;
import chat.server.messaging.RoomManager;
import chat.server.session.SessionManager;

/**
 * RF-04: reenvia los chunks de un archivo al usuario o sala destino (FileRelay).
 * El servidor no guarda el archivo: solo valida, recuerda a quien se lo debe entregar
 * y va pasando cada chunk a medida que llega, asi nunca tiene el archivo completo en memoria.
 * Responsable: P3
 */
public class FileServiceI implements FileService {

    // Un chunk mas grande que esto no es de nuestro cliente (usa 64 KB); queda muy por debajo de Ice.MessageSizeMax
    private static final int MAX_CHUNK_BYTES = 256 * 1024;
    private static final int MAX_TOTAL_CHUNKS = 1_000_000;
    // Si una transferencia no recibe nada en este tiempo se considera abandonada (el emisor se cayo)
    private static final long STALE_AFTER_MS = 2 * 60 * 1000;

    private final SessionManager sessions;
    private final RoomManager rooms;

    // transferId -> estado. Concurrente porque Ice despacha varias transferencias en hilos distintos
    private final Map<String, Transfer> transfers = new ConcurrentHashMap<>();

    public FileServiceI(SessionManager sessions, RoomManager rooms) {
        this.sessions = sessions;
        this.rooms = rooms;
    }

    /** Estado de una transferencia en curso. Sus chunks llegan en orden, un hilo a la vez. */
    private static final class Transfer {
        final FileMeta meta;
        final List<String> recipients;
        int nextIndex = 0;
        long receivedBytes = 0;
        volatile long lastActivity = System.currentTimeMillis();

        Transfer(FileMeta meta, List<String> recipients) {
            this.meta = meta;
            this.recipients = recipients;
        }
    }

    @Override
    public void beginTransfer(FileMeta meta, Current current) throws ChatException {
        purgeStaleTransfers();

        if (meta == null || isBlank(meta.transferId) || isBlank(meta.sender) || isBlank(meta.target)) {
            throw new FileTransferException("Faltan metadatos del archivo (transferId, remitente o destino)");
        }
        if (!sessions.isOnline(meta.sender)) {
            throw new NotLoggedInException("Debes iniciar sesion antes de enviar archivos");
        }
        validateFileName(meta.fileName);
        if (meta.totalSize <= 0 || meta.totalChunks <= 0 || meta.totalChunks > MAX_TOTAL_CHUNKS) {
            throw new FileTransferException("Metadatos invalidos: tamano o numero de fragmentos fuera de rango");
        }

        Transfer transfer = new Transfer(meta, resolveRecipients(meta));
        if (transfers.putIfAbsent(meta.transferId, transfer) != null) {
            throw new FileTransferException("Ya existe una transferencia con el id " + meta.transferId);
        }

        // Avisamos a cada destinatario; si alguno no responde lo quitamos de esta transferencia
        forward(transfer, cb -> cb.onFileStart(meta));
        requireRecipients(transfer);
        System.out.println("[FILES] " + meta.sender + " -> " + (meta.toRoom ? "#" : "") + meta.target + ": "
                + meta.fileName + " (" + meta.totalSize + " bytes, " + meta.totalChunks + " chunks)");
    }

    @Override
    public void sendChunk(FileChunk chunk, Current current) throws ChatException {
        Transfer transfer = requireTransfer(chunk == null ? null : chunk.transferId);

        // Un solo hilo por transferencia a la vez: asi los chunks salen hacia los clientes en el mismo orden en que llegaron
        synchronized (transfer) {
            if (chunk.data == null || chunk.data.length == 0 || chunk.data.length > MAX_CHUNK_BYTES) {
                abort(transfer);
                throw new FileTransferException("Chunk " + chunk.index + " con tamano invalido");
            }
            if (chunk.index != transfer.nextIndex || chunk.index >= transfer.meta.totalChunks) {
                abort(transfer);
                throw new FileTransferException("Chunk fuera de orden: llego " + chunk.index
                        + " y se esperaba " + transfer.nextIndex);
            }
            transfer.nextIndex++;
            transfer.receivedBytes += chunk.data.length;
            transfer.lastActivity = System.currentTimeMillis();

            forward(transfer, cb -> cb.onFileChunk(chunk));
            requireRecipients(transfer);
        }
    }

    @Override
    public void endTransfer(String transferId, String sha256, Current current) throws ChatException {
        Transfer transfer = requireTransfer(transferId);

        synchronized (transfer) {
            transfers.remove(transferId);
            FileMeta meta = transfer.meta;

            // Verificacion de lo que el servidor alcanzo a ver; el hash lo comprueba cada receptor contra el archivo real
            if (transfer.nextIndex != meta.totalChunks || transfer.receivedBytes != meta.totalSize) {
                notifyAbort(transfer);
                throw new FileTransferException("Transferencia incompleta: llegaron " + transfer.nextIndex + " de "
                        + meta.totalChunks + " chunks (" + transfer.receivedBytes + " de " + meta.totalSize + " bytes)");
            }
            if (isBlank(sha256)) {
                notifyAbort(transfer);
                throw new FileTransferException("Falta el SHA-256 del archivo");
            }

            forward(transfer, cb -> cb.onFileEnd(transferId, sha256));
            System.out.println("[FILES] Transferencia completa: " + meta.fileName + " (" + meta.sender + " -> "
                    + (meta.toRoom ? "#" : "") + meta.target + ")");
        }
    }

    // --- Destinatarios

    // Usuario: debe estar conectado. Sala: debe existir y el emisor debe pertenecer a ella (aislamiento de trafico)
    private List<String> resolveRecipients(FileMeta meta) throws ChatException {
        List<String> recipients = new CopyOnWriteArrayList<>();
        if (meta.toRoom) {
            if (!rooms.exists(meta.target)) {
                throw new RoomException("La sala " + meta.target + " no existe");
            }
            if (!rooms.isMember(meta.target, meta.sender)) {
                throw new RoomException("Debes unirte a la sala " + meta.target + " antes de enviar archivos");
            }
            for (String member : rooms.members(meta.target)) {
                if (!member.equals(meta.sender) && sessions.isOnline(member)) {
                    recipients.add(member);
                }
            }
            if (recipients.isEmpty()) {
                throw new RoomException("No hay otros miembros conectados en la sala " + meta.target);
            }
        } else {
            if (meta.sender.equals(meta.target)) {
                throw new FileTransferException("No puedes enviarte un archivo a ti mismo");
            }
            if (!sessions.isOnline(meta.target)) {
                throw new UserNotFoundException("El usuario " + meta.target + " no existe o no esta conectado");
            }
            recipients.add(meta.target);
        }
        return recipients;
    }

    // --- Reenvio

    @FunctionalInterface
    private interface Delivery {
        void deliver(ClientCallbackPrx callback) throws Exception;
    }

    // Entrega sincrona a cada destinatario: da contrapresion al emisor (no se acumulan 80 MB en memoria
    // del servidor si el receptor es lento) y detecta enseguida al que se desconecto
    private void forward(Transfer transfer, Delivery delivery) {
        for (String nickname : List.copyOf(transfer.recipients)) {
            ClientCallbackPrx callback = sessions.callbackOf(nickname).orElse(null);
            if (callback == null) {
                transfer.recipients.remove(nickname);
                continue;
            }
            try {
                delivery.deliver(callback);
            } catch (Exception e) {
                System.out.println("[FILES] " + nickname + " dejo de recibir " + transfer.meta.fileName + ": "
                        + e.getClass().getSimpleName());
                transfer.recipients.remove(nickname);
            }
        }
    }

    // Si ya nadie puede recibir, cancelamos y se lo decimos al emisor para que deje de enviar
    private void requireRecipients(Transfer transfer) throws FileTransferException {
        if (transfer.recipients.isEmpty()) {
            transfers.remove(transfer.meta.transferId);
            throw new FileTransferException("Los destinatarios se desconectaron; transferencia cancelada");
        }
    }

    private void abort(Transfer transfer) {
        transfers.remove(transfer.meta.transferId);
        notifyAbort(transfer);
    }

    // Convencion: onFileEnd con sha256 vacio significa "transferencia cancelada", el receptor borra el parcial
    private void notifyAbort(Transfer transfer) {
        forward(transfer, cb -> cb.onFileEnd(transfer.meta.transferId, ""));
    }

    private void purgeStaleTransfers() {
        long now = System.currentTimeMillis();
        for (Transfer t : List.copyOf(transfers.values())) {
            if (now - t.lastActivity > STALE_AFTER_MS && transfers.remove(t.meta.transferId, t)) {
                System.out.println("[FILES] Transferencia abandonada: " + t.meta.fileName + " de " + t.meta.sender);
                notifyAbort(t);
            }
        }
    }

    // --- Validaciones

    private Transfer requireTransfer(String transferId) throws FileTransferException {
        Transfer transfer = transferId == null ? null : transfers.get(transferId);
        if (transfer == null) {
            throw new FileTransferException("No existe una transferencia activa con ese id (cancelada o ya terminada)");
        }
        return transfer;
    }

    // El nombre viaja a otros clientes y termina como archivo en disco: nada de rutas ni nombres vacios
    private void validateFileName(String name) throws FileTransferException {
        if (isBlank(name) || name.length() > 200 || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..")) {
            throw new FileTransferException("Nombre de archivo invalido");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
