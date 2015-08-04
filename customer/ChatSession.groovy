package grails.plugin.wschat.customer

import javax.websocket.Session

public class ChatSession {
    Long sessionId
    String customerUsername
    Session customer
    String representativeUsername
    Session representative
    CustomerChatMessage creationMessage
    final List<CustomerChatMessage> chatLog = new ArrayList<>()

    public void setCreationMessage(CustomerChatMessage creationMessage) {
        this.creationMessage = creationMessage
    }

    public void log(CustomerChatMessage message) {
        this.chatLog.add(message)
    }
}
