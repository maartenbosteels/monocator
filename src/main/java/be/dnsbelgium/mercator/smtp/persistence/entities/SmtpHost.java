package be.dnsbelgium.mercator.smtp.persistence.entities;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SmtpHost {

  private Long id;

  @ToString.Exclude
  private SmtpVisit visit;

  private boolean fromMx;

  private String hostName;

  private int priority;

  @Setter
  @ToString.Exclude
  private SmtpConversation conversation;

  public SmtpHost(String hostName){
    this.hostName = hostName;
  }

}
