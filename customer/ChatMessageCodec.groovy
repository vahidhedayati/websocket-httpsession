package grails.plugin.wschat.customer

import grails.converters.JSON;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

public class ChatMessageCodec implements Encoder.BinaryStream<CustomerChatMessage>, Decoder.BinaryStream<CustomerChatMessage> {

    @Override
    public void encode(CustomerChatMessage chatMessage, OutputStream outputStream) throws EncodeException, IOException {
        try {
            CustomerChatMessage cm = new CustomerChatMessage(JSON.parse(chatMessage))
        }  catch(Exception e)  {
            throw new EncodeException(chatMessage, e.getMessage(), e)
        }
    }

    @Override
    public CustomerChatMessage decode(InputStream inputStream) throws DecodeException, IOException {
        try {
        	CustomerChatMessage cm = new   CustomerChatMessage(JSON.parse(inputStream))
        	return cm
        }  catch(Exception e) {
            throw new DecodeException((ByteBuffer)null, e.getMessage(), e)
        }
    }

    @Override
    public void init(EndpointConfig endpointConfig) { }

    @Override
    public void destroy() { }
}
