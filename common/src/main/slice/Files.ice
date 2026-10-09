// RF-04 Transferencia de archivos por chunks sobre Ice.
// Responsable: P3
#pragma once

#include "Types.ice"

[["java:package:chat"]]
module contract
{
    exception FileTransferException extends ChatException {}

    struct FileMeta
    {
        string transferId;
        string fileName;
        long totalSize;     // bytes
        int totalChunks;
        string sender;
        string target;      // nickname o nombre de sala
        bool toRoom;
    }

    struct FileChunk
    {
        string transferId;
        int index;          // 0 .. totalChunks-1
        ByteSeq data;       // 32-64 KB, siempre < Ice.MessageSizeMax
    }

    interface FileObserver
    {
        void onFileStart(FileMeta meta);
        void onFileChunk(FileChunk chunk);
        // sha256 vacio = transferencia cancelada: el receptor descarta lo recibido
        void onFileEnd(string transferId, string sha256);
    }

    interface FileService
    {
        // Flujo: beginTransfer -> sendChunk (index 0, 1, 2 ... en orden) -> endTransfer(sha256 hex del archivo completo)
        void beginTransfer(FileMeta meta) throws ChatException;
        void sendChunk(FileChunk chunk) throws ChatException;
        void endTransfer(string transferId, string sha256) throws ChatException;
    }
}
