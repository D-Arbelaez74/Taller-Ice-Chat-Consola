// RF-02 Mensajeria privada y RF-03 Salas de chat.
// Responsable: P2
#pragma once

#include "Types.ice"

[["java:package:chat"]]
module contract
{
    exception RoomException extends ChatException {}

    struct TextMessage
    {
        string sender;
        string room;       // vacio si es un mensaje privado
        string text;
        long timestamp;    // epoch millis
    }

    // Eventos que el servidor empuja al cliente
    interface MessageObserver
    {
        void onPrivateMessage(TextMessage msg);
        void onRoomMessage(TextMessage msg);
    }

    interface MessagingService
    {
        // Retorna normalmente = entregado al servidor; excepcion = error descriptivo
        void sendPrivateMessage(string from, string to, string text) throws ChatException;

        void createRoom(string nickname, string room) throws ChatException;
        idempotent StringSeq listRooms();
        void joinRoom(string nickname, string room) throws ChatException;
        void leaveRoom(string nickname, string room) throws ChatException;
        void sendRoomMessage(string from, string room, string text) throws ChatException;
    }
}
