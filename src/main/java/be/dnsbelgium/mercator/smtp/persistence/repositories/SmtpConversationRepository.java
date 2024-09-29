package be.dnsbelgium.mercator.smtp.persistence.repositories;

import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpConversation;
import org.springframework.stereotype.Component;

@Component
public class SmtpConversationRepository  {
  
  public void save(SmtpConversation conversation) {
    
  }


  //  @Query(value = "select * from smtp_conversation c " +
  //      "inner join smtp_host h on c.id = h.conversation " +
  //      "where h.id = ?1", nativeQuery = true)

}