/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package psd2.email;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.internet.*;
import javax.mail.*;

/**
 *
 * @author Anwar
 */
public class SMTPMail {
    
    private String from;
    private String username;
    private String password;
    Properties emailProps;
    public String Recipients;
    private Map<String,String> emailConfig;
    private String emailSubj;
    private Boolean enableAuth;
    
    public SMTPMail(Map <String , ?> YmlConfiguration){
        emailConfig = (Map<String,String>) YmlConfiguration.get("smtpEmailAccount");
        from = emailConfig.get("from");
        username = emailConfig.get("username");
        password = emailConfig.get("password");
        Recipients = emailConfig.get("emailRecipients");
        emailSubj = emailConfig.get("emailSubject");
        enableAuth = Boolean.parseBoolean(emailConfig.get("enableAuth"));
        emailProps = new Properties();
        
        emailProps.put("mail.smtp.host", emailConfig.get("host"));
        emailProps.put("mail.smtp.auth", emailConfig.get("enableAuth"));
        emailProps.put("mail.smtp.ssl.trust","*");
        emailProps.put("mail.smtp.port", emailConfig.get("port"));
        emailProps.put("mail.smtp.starttls.enable", emailConfig.get("enableTls"));
    }
    
    //return jar file runtime path
    private String getFilePointerPath(String fileName) throws URISyntaxException {
        return new File(SMTPMail.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath() + fileName;
    }
    
    
    // parse email recipients from yml configuration file
    public InternetAddress[] prepareReciepientsList() throws AddressException {
       String RecipeintsListString = (String) emailConfig.get("emailRecipients");
       String[] retrivedReciepientsList = RecipeintsListString.split(",",0);
       InternetAddress[] toAddress = new InternetAddress[retrivedReciepientsList.length];
        
       
       for( int i = 0; i < retrivedReciepientsList.length; i++ ) {
               toAddress[i] = new InternetAddress(retrivedReciepientsList[i]);
        }
       return toAddress;
    }
    
    public void sendEmail(String Body) {
    
    /// check if login is enabled or not
          Session session;
          if (enableAuth) {
              session = Session.getInstance(emailProps,
                    new javax.mail.Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username, password);
                        }
                    });
          } else {
          session = Session.getDefaultInstance(emailProps);
          }

        try {
            // Create a default MimeMessage object
            Message message = new MimeMessage(session);

            message.setFrom(new InternetAddress(from));

            message.setRecipients(Message.RecipientType.TO,prepareReciepientsList());
            
            
            // Set Subject
            message.setSubject(emailSubj);

            // Put the content of your message
            message.setContent(Body,"text/html");

            
            // Send message
            Transport.send(message);

        } catch (Exception e) {
           System.err.println(e);
        }
    }
}
