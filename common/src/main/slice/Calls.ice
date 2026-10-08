// RF-05 Senalizacion de llamadas 1 a 1 (el audio viaja por UDP, NO por Ice).
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
    
    sequence<VoiceEndpoint> EndpointSeq;

    interface CallObserver
    {
        void onIncomingCall(string caller, VoiceEndpoint callerEndpoint);
        void onCallAccepted(string callee, VoiceEndpoint calleeEndpoint);
        void onCallRejected(string callee);
        void onCallEnded(string peer);
        
        // RF-06: Llamadas grupales
        void onGroupCallSync(string room, EndpointSeq endpoints);
    }

    interface CallService
    {
        void startCall(string from, string to, VoiceEndpoint endpoint) throws ChatException;
        void answerCall(string callee, string caller, bool accepted, VoiceEndpoint endpoint) throws ChatException;
        void hangup(string from, string peer) throws ChatException;
        
        // RF-06: Llamadas grupales
        void joinGroupCall(string nickname, string room, VoiceEndpoint endpoint) throws ChatException;
        void leaveGroupCall(string nickname, string room) throws ChatException;
    }
}
