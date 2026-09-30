
#pragma once

#include "Types.ice"
#include "Messaging.ice"
#include "Files.ice"
#include "Calls.ice"

[["java:package:chat"]]
module contract
{
    interface PresenceObserver //Sirve para ver si el usuario esta conectado o desconectado
    {
        void onUserConnected(string nickname);
        void onUserDisconnected(string nickname);
    }

    // Un solo callback que tiene los eventos de los cuatro integrantes
    interface ClientCallback extends PresenceObserver, MessageObserver, FileObserver, CallObserver {}

    interface SessionService
    {
        StringSeq login(string nickname, ClientCallback* callback) throws ChatException; //Registra al usuario con la dirección para hacer el callback
        void logout(string nickname); //Cierra la sesión
        idempotent StringSeq getOnlineUsers();
        //El idempotent nos permite leer datos sin alterar info. En este caso revisa si hay usuario online.
    }
}
