// RF-01 Sesion, presencia y registro del callback del cliente.
// Responsable: P1
#pragma once

#include "Types.ice"
#include "Messaging.ice"
#include "Files.ice"
#include "Calls.ice"

[["java:package:chat"]]
module contract
{
    interface PresenceObserver
    {
        void onUserConnected(string nickname);
        void onUserDisconnected(string nickname);
    }

    // Objeto callback unico que el cliente registra al hacer login.
    // Hereda los eventos de cada modulo.
    interface ClientCallback extends PresenceObserver, MessageObserver, FileObserver, CallObserver {}

    interface SessionService
    {
        // Retorna los usuarios conectados en el momento del login
        StringSeq login(string nickname, ClientCallback* callback) throws ChatException;
        void logout(string nickname);
        idempotent StringSeq getOnlineUsers();
    }
}
