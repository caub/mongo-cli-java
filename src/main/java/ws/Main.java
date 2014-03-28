package ws;

//import org.apache.catalina.startup.Tomcat;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;


public class Main {



    public static void main(String[] args) throws Exception {


        //db
        Persistence.m = new MongoClient(new MongoClientURI(Persistence.MONGO_URI));
        Persistence.db = Persistence.m.getDB("jfx");

        //jetty embedded
        int port = System.getenv("PORT")!=null? Integer.valueOf(System.getenv("PORT")): 8080;
        System.out.println("port is "+port);
        Server server = new Server(port);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        ServerContainer wsContainer = WebSocketServerContainerInitializer.configureContext(context);

        wsContainer.addEndpoint(WsServlet.class);

        ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
        holderPwd.setInitParameter("resourceBase","./src/main/webapp/");
        holderPwd.setInitParameter("dirAllowed", "true");
        context.addServlet(holderPwd, "/");

        context.addServlet(ws.OpenIdServlet.class,"/openid/*");

        server.start();
        //context.dumpStdErr();
        server.join();

    }

}