package ws;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.expressme.openid.Association;
import org.expressme.openid.Authentication;
import org.expressme.openid.Endpoint;
import org.expressme.openid.OpenIdException;
import org.expressme.openid.OpenIdManager;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;

@WebServlet("/openid/*")
public class OpenIdServlet extends HttpServlet {

	static final long ONE_HOUR = 3600000L;
    static final long TWO_HOUR = ONE_HOUR * 2L;
    static final String ATTR_MAC = "openid_mac";
    static final String ATTR_ALIAS = "openid_alias";
    
    static Map<String, String> tokens = new HashMap<>();
    static Map<String, String> emails = new HashMap<>(); //opposite mapping
    Map<String, Long> sessionTokensExpiration = new HashMap<>(); //TODO

    OpenIdManager manager;
    
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        manager = new OpenIdManager();
/*
        manager.setRealm("http://mongo-cli-java.cyril.eu.cloudbees.net");
        manager.setReturnTo("http://mongo-cli-java.cyril.eu.cloudbees.net/openid/verify");

        manager.setRealm("http://localhost:8080"); // change to your domain
        manager.setReturnTo("http://localhost:8080/openid/verify"); // change to your servlet url
        */

        manager.setRealm("http://mongo-cli-java.herokuapp.com");
        manager.setReturnTo("http://mongo-cli-java.herokuapp.com/openid/verify");

    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter writer = resp.getWriter();

        HttpSession httpSession = req.getSession();
        String op = req.getParameter("openid_identifier");

        if (req.getPathInfo().endsWith("authenticate")){

            Endpoint endpoint = manager.lookupEndpoint(op);
            Association association = manager.lookupAssociation(endpoint);
            httpSession.setAttribute(ATTR_MAC, association.getRawMacKey());
            httpSession.setAttribute(ATTR_ALIAS, endpoint.getAlias());

        	String url = manager.getAuthenticationUrl(endpoint, association);

            resp.sendRedirect(url);

       /* } else if(req.getParameter("echo_test")!=null) { //tests for popups corss origin communications

        	resp.setContentType("text/html");
            writer.println("<script>");
            writer.println("var authReply = {user: 'foo'};");
            writer.println("function receiveMessage(event){");
            writer.println("	event.source.postMessage(JSON.stringify(authReply), event.origin);window.close();");
            writer.println("}");
            writer.println("window.addEventListener('message', receiveMessage, false);");
            writer.println("</script>");*/

        	/*System.out.println(" ..test... ");
        	resp.setContentType("text/html");
        	writer.println("<html><body onload=\"window.opener.handleOpenIDResponse(window.location.href);window.close();\">");
        	writer.println("It should close..</body></html>");*/
        	
       
        } else if(req.getPathInfo().endsWith("verify")) {
            // check nonce:
            checkNonce(req.getParameter("openid.response_nonce"));
            // get authentication:
            byte[] mac_key = (byte[]) httpSession.getAttribute(ATTR_MAC);
            String alias = (String) httpSession.getAttribute(ATTR_ALIAS);
            try{
	            Authentication authentication = manager.getAuthentication(req, mac_key, alias);

            	String email = authentication.getEmail();
            	
            	String token  = emails.get(email);
            	if (token==null){
            		token = UUID.randomUUID().toString();
            		tokens.put(token, email);
    				emails.put(email, token);
            	}
  
                DBObject user = new BasicDBObject("email", email)
                	.append("token", token)
                	.append("fullname", authentication.getFullname())
                	.append("language", authentication.getLanguage())
                	.append("gender", authentication.getGender());

                
                System.out.println(email+" ....... "+user);
                
                resp.setContentType("text/html");
                writer.println("<script>");
                writer.println("var authReply = "+JSON.serialize(user)+";");
                writer.println("function receiveMessage(event){");
                writer.println("	event.source.postMessage(JSON.stringify(authReply), event.origin);window.close();");
                writer.println("}");
                writer.println("window.addEventListener('message', receiveMessage, false);");
                writer.println("</script>");
                
            	httpSession.setAttribute("user", user);
            	
            }catch(OpenIdException e){
            	e.printStackTrace();
            	writer.println(e);
            }
            
            
        }else{
            writer.println(req.getPathInfo());
        }

    }


    void checkNonce(String nonce) {
        // check response_nonce to prevent replay-attack:
        if (nonce==null || nonce.length()<20)
            throw new OpenIdException("Verify failed.");
        long nonceTime = getNonceTime(nonce);
        long diff = System.currentTimeMillis() - nonceTime;
        if (diff < 0)
            diff = (-diff);
        if (diff > ONE_HOUR)
            throw new OpenIdException("Bad nonce time.");
        if (isNonceExist(nonce))
            throw new OpenIdException("Verify nonce failed.");
        storeNonce(nonce, nonceTime + TWO_HOUR);
    }

    boolean isNonceExist(String nonce) {
        // TODO: check if nonce is exist in database:
        return false;
    }

    void storeNonce(String nonce, long expires) {
        // TODO: store nonce in database:
    }

    long getNonceTime(String nonce) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                    .parse(nonce.substring(0, 19) + "+0000")
                    .getTime();
        }
        catch(ParseException e) {
            throw new OpenIdException("Bad nonce time.");
        }
    }
}