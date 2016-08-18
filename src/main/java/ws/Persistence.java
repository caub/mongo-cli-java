package ws;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.bson.types.BasicBSONList;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
//import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;

@WebListener
public class Persistence implements ServletContextListener{

	static String MONGO_URI = "mongodb://user:pass@ds053978.mongolab.com:53978/jfx";
	static MongoClient m;
	static DB db = null; // is already a pool of connections through this
							// singleton
	
	public void contextInitialized(ServletContextEvent event) {
		System.out.println("init api ----------------------");
		try {
			m = new MongoClient(new MongoClientURI(MONGO_URI));
			db = m.getDB("jfx");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	public void contextDestroyed(ServletContextEvent event) {
		m.close();
	}

}
