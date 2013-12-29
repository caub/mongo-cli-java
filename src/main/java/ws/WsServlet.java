package ws;

import static ws.OpenIdServlet.tokens;
import static ws.Persistence.db;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

import com.mongodb.*;
import com.mongodb.util.JSON;
import org.bson.types.ObjectId;

@ServerEndpoint("/api")
public class WsServlet {

    DBCollection coll;

    String token; //token
    String email; //user represented by token
    //maybe needs final

    String prefix = "tests.";

    Session ws;

    static Map<String, Session> conns = new HashMap<>(); //sessions per user name
	
	public void broadcast(Object d, String fn, int _i) throws IOException {

        //if (d  instanceof BasicDBList){
         //   for (Object o : (BasicDBList)d) { broadcast(o, fn, _i); }
        //}else {
            BasicDBObject o = (BasicDBObject) d;
            String reply = JSON.serialize(new BasicDBObject("msg", d).append("fn", fn));
            if (o.containsField("_canRead")){
                for (Object i : (BasicDBList)o.get("_canRead")){
                    Session wsi = conns.get(i);
                    if (!wsi.equals(ws))
                        wsi.getBasicRemote().sendText(reply);
                }
            }else{
                for (Session s : ws.getOpenSessions())
                    if (!s.equals(ws))
                        s.getBasicRemote().sendText(reply);
            }
        //}

	}


    @OnOpen
    public void onOpen(Session session, EndpointConfig c ) throws IOException, ParseException {
    	//session.setMaxIdleTimeout(0);

        this.ws = session;

        System.out.println("qs "+session.getQueryString());
        Map<String, List<String>> qs = session.getRequestParameterMap();
        this.coll = db.getCollection(prefix + qs.get("coll").get(0));
        this.token = qs.get("token").get(0);
        System.out.println("token " + token+" "+coll.getName());
        email = tokens.get(token);
        if (email!=null){
            conns.put(email, session);
        }
        //session.getBasicRemote().sendText(JSON.serialize(new BasicDBObject("type", "auth").append("doc", userToken)));
    }

    @OnClose
    public void onClose(Session session) {
        conns.remove(email);
    }
    
    @OnMessage
    public String onMessage(String message) {

    	try{
            BasicDBObject o = (BasicDBObject) JSON.parse(message);
            String fn = o.getString("fn");

            if (fn.equals("auth")){
                return JSON.serialize(new BasicDBObject("msg", email).append("_i", o.getInt("_i")));
            }else if (fn.equals("echo")){
                return message;
            }else{
                Control c = Access.valueOf(fn);
                Object[] args = ((BasicDBList) o.get("args")).toArray();

                //auto wrap _id.. no
                /*if (args[0] instanceof BasicDBObject){
                    BasicDBObject q = (BasicDBObject) args[0];
                    if (q.containsField("_id") && q.get("_id") instanceof String)
                        q.put("_id", new ObjectId(q.getString("_id")));
                }*/

                c.control(args, email);

                Class[] types = new Class[args.length];
                for (int i=0; i<args.length; i++){
                    if ( args[i]==null) types[i] = DBObject.class;
                    else {Class cls = args[i].getClass();
                        types[i] = cls.equals(BasicDBObject.class)?DBObject.class : cls.equals(Boolean.class)?boolean.class : cls;//(Class) args.get(i).getClass().getGenericInterfaces()[0];
                    }
                }


                int _i = o.getInt("_i");

                Object obj = coll.getClass().getMethod(fn, types).invoke(coll, args);
                if (fn.equals("find")){
                    DBCursor cursor = (DBCursor)obj;
                    List<Object> result =new ArrayList<Object>();
                    while(cursor.hasNext())
                        result.add(cursor.next());
                    cursor.close();
                    System.out.println("find"+result);
                    return JSON.serialize(new BasicDBObject("msg", result).append("_i", _i));

                }else if(obj instanceof DBObject){//worth broadcasting
                    broadcast(obj, fn , _i);
                    return JSON.serialize(new BasicDBObject("msg", obj).append("fn",fn).append("_i",_i));
                }else if (obj instanceof WriteResult)
                    return JSON.serialize(new BasicDBObject("msg", ((WriteResult)obj).getN()).append("_i", _i));
                else
                    return JSON.serialize(new BasicDBObject("msg", obj).append("_i", _i));

            }
    		
    	}catch(Exception e){
    		e.printStackTrace();
    		return JSON.serialize(new BasicDBObject("error", e.getMessage()));
    	}
    }

}


enum Access implements Control {
    find {
        public void control(Object[] l, final String email){
            if(email!=null){
                BasicDBObject q = (BasicDBObject)l[0];
                BasicDBList or = (BasicDBList) q.get("$or");
                if (or==null) or = new BasicDBList();
                or.add(new BasicDBObject("_canRead", null));
                or.add(new BasicDBObject("_canRead", new BasicDBObject("$in", new BasicDBList() {{this.add(email);}})));
            }else{
                ((BasicDBObject)l[0]).put("_canRead", null);
            }
        }
    },
    insert {
        public void control(Object[] l, String email){
            if(email==null){
                if (l[0]  instanceof BasicDBList){//@TODO use a proper json lib
                    BasicDBList l_ = (BasicDBList) l[0];
                    for (Object o : l_)
                        removeSpecialFields(o);
                    l[0] = l_.toArray(new DBObject[l_.size()]);

                }else{
                    removeSpecialFields(l[0]);
                }
            }
        }
    },
    remove {
        public void control(Object[] l, final String email){
            if(email!=null){
                BasicDBObject q = (BasicDBObject)l[0];
                BasicDBList or = (BasicDBList) q.get("$or");
                if (or==null) or = new BasicDBList();
                or.add(new BasicDBObject("_canRemove", null));
                or.add(new BasicDBObject("_canRemove", new BasicDBObject("$in", new BasicDBList() {{this.add(email);}})));
            }else{
                ((BasicDBObject)l[0]).put("_canRead", null);
            }
        }
    },
    update {
        public void control(Object[] l, final String email){
            if (l[0]!=null){
                if(email!=null){
                    BasicDBObject q = (BasicDBObject)l[0];
                    BasicDBList or = (BasicDBList) q.get("$or");
                    if (or==null) or = new BasicDBList();
                    or.add(new BasicDBObject("_canUpsert", null));
                    or.add(new BasicDBObject("_canUpsert", new BasicDBObject("$in", new BasicDBList() {{this.add(email);}})));
                }else{
                    ((BasicDBObject)l[0]).put("_canUpsert", null);
                }
            }
            if ( email==null){
                if (l.length>3)
                    removeSpecialFields(l[l.length-3]);
                else if (l.length>1)
                    removeSpecialFields(l[l.length-1]);
            }
        }
    },
    findAndModify {
        public void control(Object[] l, String email){Access.update.control(l, email);}
    },
    findAndRemove {
        public void control(Object[] l, String email){ Access.remove.control(l, email); }
    },
    save {
        public void control(Object[] l, String email){ Access.insert.control(l, email); }
    };

    public void removeSpecialFields(Object o_) {
        BasicDBObject o = (BasicDBObject) o_;
        if (o.containsField("$set")) {
            BasicDBObject $set = (BasicDBObject)o.get("$set");
            $set.remove("_canUpsert");
            $set.remove("_canRemove");
            $set.remove("_canRead");
        }
        if (o.containsField("$push")) {
            BasicDBObject $push = (BasicDBObject)o.get("$push");
            $push.remove("_canUpsert");
            $push.remove("_canRemove");
            $push.remove("_canRead");
        }
        o.remove("_canUpsert");
        o.remove("_canRemove");
        o.remove("_canRead");
    }
}

interface Control {
    public void control(Object[] l, String email);
}