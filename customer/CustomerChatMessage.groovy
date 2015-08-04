package grails.plugin.wschat.customer


public class CustomerChatMessage {
    Date timestamp
    Type type
    String user
    String content
   
    public static enum Type {
        STARTED, JOINED, ERROR, LEFT, TEXT
    }
}
