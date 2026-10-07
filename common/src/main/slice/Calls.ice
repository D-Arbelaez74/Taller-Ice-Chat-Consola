module VoiceCall {
    struct CallEndpoint {
        string ip;
        int port;
    };

    interface CallCallback {
        void incomingCall(string callerId, CallEndpoint callerEndpoint);
        void callAccepted(string calleeId, CallEndpoint calleeEndpoint);
        void callRejected(string calleeId);
        void callEnded(string userId);
        void userJoinedVoiceGroup(string userId, CallEndpoint endpoint);
        void userLeftVoiceGroup(string userId);
    };

    interface CallService {
        bool initiateCall(string targetUserId, CallEndpoint myEndpoint);
        bool respondCall(string callerId, bool accept, CallEndpoint myEndpoint);
        void endCall(string targetUserId);

        // Soporte para llamadas grupales (RF-06)
        void joinGroupVoice(string groupId, CallEndpoint myEndpoint);
        void leaveGroupVoice(string groupId, string userId);
    };
};