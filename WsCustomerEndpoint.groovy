package grails.plugin.wschat


import static java.util.Calendar.*
import grails.plugin.wschat.customer.ChatMessageCodec
import grails.plugin.wschat.customer.ChatSession
import grails.plugin.wschat.customer.CustomerChatMessage

import javax.servlet.annotation.WebListener
import javax.servlet.http.HttpSession
import javax.servlet.http.HttpSessionEvent
import javax.servlet.http.HttpSessionListener
import javax.websocket.CloseReason
import javax.websocket.EncodeException
import javax.websocket.HandshakeResponse
import javax.websocket.OnClose
import javax.websocket.OnError
import javax.websocket.OnMessage
import javax.websocket.OnOpen
import javax.websocket.Session
import javax.websocket.server.HandshakeRequest
import javax.websocket.server.PathParam
import javax.websocket.server.ServerEndpoint
import javax.websocket.server.ServerEndpointConfig

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@WebListener
@ServerEndpoint(value = "/WsCustomerEndpoint/{room}",  encoders=ChatMessageCodec.class, decoders=ChatMessageCodec.class, configurator= EndpointConfigurator.class)
class WsCustomerEndpoint extends ChatUtils implements  HttpSessionListener {
	private final Logger log = LoggerFactory.getLogger(getClass().name)
	private static final String HTTP_SESSION_PROPERTY = "grails.plugin.wschat.HTTP_SESSION"
	private static final String WS_SESSION_PROPERTY = "grails.plugin.wschat.http.WS_SESSION"
	private static long sessionIdSequence = 1L
	private static final Object sessionIdSequenceLock = new Object()

	private static final Map<Long, ChatSession> chatSessions = new Hashtable<>()
	private static final Map<Session, ChatSession> sessions = new Hashtable<>()
	private static final Map<Session, HttpSession> httpSessions =new Hashtable<>()
	public static final List<ChatSession> pendingSessions = new ArrayList<>()

	@OnOpen
	public void onOpen(Session session, @PathParam("sessionId") long sessionId){
		HttpSession httpSession = (HttpSession)session.userProperties.get(HTTP_SESSION_PROPERTY)
		try {
			if(!httpSession|| !httpSession.getAttribute("username"))	{
				session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY,"You are not logged in!"))
				return
			}

			String username = (String)httpSession.getAttribute("username")
			session.userProperties.put("username", username)
			CustomerChatMessage message = new CustomerChatMessage()
			message.setTimestamp(new Date())
			message.setUser(username)
			ChatSession chatSession
			if(sessionId < 1){
				message.setType(message.Type.STARTED)
				message.setContent(username + " started the chat session.")
				chatSession = new ChatSession()
				synchronized(sessionIdSequenceLock)	{
					chatSession.setSessionId(sessionIdSequence++)
				}
				chatSession.setCustomer(session)
				chatSession.setCustomerUsername(username)
				chatSession.setCreationMessage(message)
				pendingSessions.add(chatSession)
				chatSessions.put(chatSession.getSessionId(), chatSession)
			} else 	{
				message.setType(message.Type.JOINED)
				message.setContent(username + " joined the chat session.")
				chatSession = chatSessions.get(sessionId)
				chatSession.setRepresentative(session)
				chatSession.setRepresentativeUsername(username)
				pendingSessions.remove(chatSession)
				session.basicRemote.sendObject(chatSession.getCreationMessage())
				session.basicRemote.sendObject(message)
			}

			sessions.put(session, chatSession)
			httpSessions.put(session, httpSession)
			this.getSessionsFor(httpSession).add(session)
			chatSession.log(message)
			chatSession.getCustomer().basicRemote.sendObject(message)
		} catch(IOException | EncodeException e) {
			this.onError(session, e)
		}
	}

	@OnMessage
	public void onMessage(Session session, ChatMessage message) {
		ChatSession c = sessions.get(session)
		Session other = this.getOtherSession(c, session)
		if(c != null && other != null) {
			c.log(message)
			try {
				session.basicRemote.sendObject(message)
				other.basicRemote.sendObject(message)
			} catch(IOException | EncodeException e) {
				this.onError(session, e)
			}
		}
	}

	@OnClose
	public void onClose(Session session, CloseReason reason) {
		if(reason.getCloseCode() == CloseReason.CloseCodes.NORMAL_CLOSURE) {
			CustomerChatMessage message = new CustomerChatMessage()
			message.setUser((String)session.userProperties.get("username"))
			message.setType(message.Type.LEFT)
			message.setTimestamp(new Date())
			message.setContent(message.getUser() + " left the chat.")
			try {
				Session other = this.close(session, message)
				if(other != null)
					other.close()
			} catch(IOException e) {
				e.printStackTrace()
			}
		}
	}

	@OnError
	public void onError(Session session, Throwable e) {
		CustomerChatMessage message = new CustomerChatMessage()
		message.setUser((String)session.userProperties.get("username"))
		message.setType(message.Type.ERROR)
		message.setTimestamp(new Date())
		message.setContent(message.getUser() + " left the chat due to an error.")
		try {
			Session other = this.close(session, message)
			if (other) {
				other.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, e.toString()))
			}
		} catch(IOException ignore) { 
		} finally {
			try {
				session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, e.toString()))
			} catch(IOException ignore) { }
		}
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		HttpSession httpSession = event.getSession()
		if (httpSession.getAttribute(WS_SESSION_PROPERTY) != null) {
			CustomerChatMessage message = new CustomerChatMessage()
			message.setUser((String)httpSession.getAttribute("username"))
			message.setType(message.Type.LEFT)
			message.setTimestamp(new Date())
			message.setContent(message.getUser() + " logged out.")
			for(Session session:new ArrayList<>(this.getSessionsFor(httpSession))) {
				try {
					session.basicRemote.sendObject(message)
					Session other = this.close(session, message)
					if(other != null)
						other.close()
				} catch(IOException | EncodeException e) {
					e.printStackTrace()
				} finally {
					try	{
						session.close()
					}
					catch(IOException ignore) { }
				}
			}
		}
	}

	@Override
	public void sessionCreated(HttpSessionEvent event) { /* do nothing */ }


	private synchronized ArrayList<Session> getSessionsFor(HttpSession session)	{
		try	{
			if (session.getAttribute(WS_SESSION_PROPERTY) == null) {
				session.setAttribute(WS_SESSION_PROPERTY, new ArrayList<>())
			}
			return (ArrayList<Session>)session.getAttribute(WS_SESSION_PROPERTY)
		} catch(IllegalStateException e) {
			return new ArrayList<>()
		}
	}


	@OnError
	public void handleError(Throwable t) {
		t.printStackTrace()
	}

	private Session close(Session s, ChatMessage message) {
		ChatSession c = sessions.get(s)
		Session other = this.getOtherSession(c, s)
		sessions.remove(s)
		HttpSession h = httpSessions.get(s)
		if (h) {
			this.getSessionsFor(h).remove(s)
		}	
		if(c) {
			c.log(message)
			pendingSessions.remove(c)
			chatSessions.remove(c.getSessionId())
			try {
				//c.writeChatLog(new File("chat." + c.getSessionId() + ".log"))
			}
			catch(Exception e)
			{
				System.err.println("Could not write chat log.")
				e.printStackTrace()
			}
		}
		if(other) {
			sessions.remove(other)
			h = httpSessions.get(other)
			if(h) {
				this.getSessionsFor(h).remove(s)
			}	
			try {
				other.basicRemote.sendObject(message)
			} catch(IOException | EncodeException e) {
				e.printStackTrace()
			}
		}
		return other
	}



	private Session getOtherSession(ChatSession c, Session s) {
		return c == null ? null :(s == c.getCustomer() ? c.getRepresentative() : c.getCustomer())
	}

	public static class EndpointConfigurator extends ServerEndpointConfig.Configurator {
		@Override
		public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request,HandshakeResponse response) {
			super.modifyHandshake(config, request, response)
			config.userProperties.put(HTTP_SESSION_PROPERTY, request.getHttpSession())
		}
	}
}

