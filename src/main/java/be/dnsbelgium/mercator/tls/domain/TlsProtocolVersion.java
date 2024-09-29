package be.dnsbelgium.mercator.tls.domain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public enum TlsProtocolVersion {

  SSL_2   (new byte[] { (byte) 0x00, (byte) 0x02 }, "SSLv2"),
  SSL_3   (new byte[] { (byte) 0x03, (byte) 0x00 }, "SSLv3"),
  TLS_1_0  (new byte[] { (byte) 0x03, (byte) 0x01 }, "TLSv1"),
  TLS_1_1  (new byte[] { (byte) 0x03, (byte) 0x02 }, "TLSv1.1"),
  TLS_1_2  (new byte[] { (byte) 0x03, (byte) 0x03 }, "TLSv1.2"),
  TLS_1_3  (new byte[] { (byte) 0x03, (byte) 0x04 }, "TLSv1.3");

  private final byte[] value;
  private final String name;

  public static final int BITS_IN_A_BYTE = 8;

  // The name passed to the constructor is used in calls to socket.setEnabledProtocols() so we cannot freely choose them
  // and some of these values are not valid java identifiers so they do not match with name()
  TlsProtocolVersion(byte[] value, String name) {
    this.value = value;
    this.name = name;
  }

  public String getName() {
    return name;
  }

  private static final Map<Integer, TlsProtocolVersion> MAP;
  static {
    MAP = new HashMap<>();
    for (TlsProtocolVersion c : TlsProtocolVersion.values()) {
      MAP.put(c.valueAsInt(), c);
    }
  }

  private static int valueAsInt(byte[] value) {
    if (value.length == 2) {
      return (value[0] & 0xff) << BITS_IN_A_BYTE | (value[1] & 0xff);
    }
    throw new IllegalStateException("value should consist of two bytes but has " +  value.length + " bytes.");
  }

  int valueAsInt() {
    return valueAsInt(this.value);
  }

  public static TlsProtocolVersion from(byte[] bytes) {
    int key = valueAsInt(bytes);
    return MAP.get(key);
  }

  public byte[] getValue() {
    return value;
  }

  public static TlsProtocolVersion of(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Arrays.stream(TlsProtocolVersion.values()).filter(s -> s.getName().equals(value)).findFirst().orElseThrow();
    } catch (NoSuchElementException e) {
      return TlsProtocolVersion.valueOf(value);
    }
  }
}
