package be.dnsbelgium.mercator.tls.domain.ssl2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

public class ClientHandler extends ChannelInboundHandlerAdapter {

  private static final Logger logger = getLogger(ClientHandler.class);
  private static final byte[] NO_SESSION_ID = new byte[0];

  private final List<SSL2CipherSuite> cipherSuites;

  private boolean connectOK = false;
  private ServerHello serverHello;

  private String errorMessage;

  private final InetSocketAddress socketAddress;

  public ClientHandler(InetSocketAddress socketAddress, List<SSL2CipherSuite> cipherSuites) {
    this.socketAddress = socketAddress;
    this.cipherSuites = cipherSuites;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    logger.debug("Connected to {}", ctx.channel().remoteAddress());
    this.connectOK = true;
    byte[] challenge = RandomUtils.nextBytes(32);
    ClientHello clientHello = new ClientHello(
        2,
        cipherSuites,
        NO_SESSION_ID,
        challenge
    );
    logger.debug("Writing clientHello = {}", clientHello);
    ctx.channel().writeAndFlush(clientHello);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    logger.debug("{} => exceptionCaught: {}", ctx.channel().remoteAddress(), cause.getMessage());
    this.errorMessage = cause.getMessage();
    if (ctx.channel() != null) {
      logger.info("Closing the connection");
      ctx.channel().close();
    }
  }


  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    logger.debug("channelRead");
    if (msg instanceof ServerHello) {
      this.serverHello = (ServerHello) msg;
      logger.debug("Received ServerHello: {}", serverHello);
    } else {
      this.errorMessage = "Unexpected response";
    }
    if (ctx != null) {
      logger.info("Closing the connection");
      ctx.channel().close();
    }
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof IdleStateEvent e) {
      if (e.state() == IdleState.READER_IDLE) {
        logger.debug("Idle on inbound traffic => close");
        ctx.close();
      } else if (e.state() == IdleState.WRITER_IDLE) {
        logger.debug("Idle on outbound traffic => close");
        ctx.close();
      }
    }
  }

  public SSL2Scan result() {
    SSL2Scan scan = new SSL2Scan();
    scan.setAddress(socketAddress);
    scan.setConnectOK(this.connectOK);
    scan.setPeerVerified(false);
    scan.setServerHello(serverHello);
    if (this.serverHello != null) {
      scan.setSelectedProtocol(serverHello.selectedVersion());
      // Consider first in the list in ServerHello as the selected cipher
      if (!serverHello.getListSupportedCipherSuites().isEmpty()) {
        scan.setSelectedCipherSuite(serverHello.getListSupportedCipherSuites().get(0).name());
      }
      scan.setHandshakeOK(true);
      scan.setErrorMessage(null);
    } else {
      scan.setHandshakeOK(false);
      scan.setErrorMessage(errorMessage);
    }
    return scan;
  }

}
