// RF-05 y RF-06: senalizacion de llamadas (el audio viaja por UDP, NO por Ice).
// Responsable: P4
#pragma once

#include "Types.ice"

[["java:package:chat"]]
module contract
{
    exception CallException extends ChatException {}

    struct VoiceEndpoint
    {
        string host;
        int port;           // puerto UDP de recepcion de audio
    }

    // Lo que el servidor le entrega a quien entra a una llamada grupal
    struct GroupCallInfo
    {
        int port;           // puerto UDP de broadcast de esa llamada, el mismo para todos los participantes
        int senderId;       // numero que identifica a este participante dentro de los paquetes de audio
    }

    interface CallObserver
    {
        void onIncomingCall(string caller, VoiceEndpoint callerEndpoint);
        void onCallAccepted(string callee, VoiceEndpoint calleeEndpoint);
        void onCallRejected(string callee);
        void onCallEnded(string peer);

        // RF-06: Llamadas grupales
        void onGroupCallInvite(string room, string caller);     // alguien inicio una llamada en una sala a la que pertenezco
        void onGroupCallJoined(string room, string nickname);   // alguien entro a la llamada en la que estoy
        void onGroupCallLeft(string room, string nickname);     // alguien salio de la llamada en la que estoy
    }

    interface CallService
    {
        void startCall(string from, string to, VoiceEndpoint endpoint) throws ChatException;
        void answerCall(string callee, string caller, bool accepted, VoiceEndpoint endpoint) throws ChatException;
        void hangup(string from, string peer) throws ChatException;

        // RF-06: Llamadas grupales por broadcast UDP.
        // Si la sala no tiene llamada la crea y avisa a los demas miembros; si ya tiene, solo entra
        GroupCallInfo joinGroupCall(string nickname, string room) throws ChatException;
        void leaveGroupCall(string nickname, string room) throws ChatException;
    }
}
