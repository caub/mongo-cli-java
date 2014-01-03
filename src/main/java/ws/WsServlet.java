package ws;

import static ws.OpenIdServlet.tokens;
import static ws.Persistence.db;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import com.mongodb.*;
import com.mongodb.util.JSON;
import org.bson.types.ObjectId;

@ServerEndpoint("/api/{collection}")
public class WsServlet {

    DBCollection coll;

    String token; //token
    String email; //user represented by token
    //maybe needs final

    String prefix = "tests.";

    Session ws;

    static ConcurrentHashMap<String, Set<Session>> conns = new ConcurrentHashMap<>(); //sessions per user name

    Rights rights;

    public void broadcast(Object d, String fn, int _i) throws IOException {

        //if (d  instanceof BasicDBList){ //insert doesn't return new documents unlike in nodejs
        //   for (Object o : (BasicDBList)d) { broadcast(o, fn, _i); }
        //}else {
        BasicDBObject o = (BasicDBObject) d;
        String reply = JSON.serialize(new BasicDBObject("msg", d).append("fn", fn));
        if (o.containsField("_canRead")){
            if (((BasicDBList)o.get("_canRead")).size()==0)
                ws.getBasicRemote().sendText(JSON.serialize(new BasicDBObject("msg", d).append("fn", fn).append("_i", _i)));
            else
                for (Object i : (BasicDBList)o.get("_canRead")){
                    if (conns.get(i)!=null && conns.get(i).size()>0)
                        for(Session wsi : conns.get(i))
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
    public void onOpen(Session session, EndpointConfig c,
                       @PathParam("collection") String collname ) throws IOException, ParseException {
        //session.setMaxIdleTimeout(0);

        this.ws = session;

        System.out.println("qs "+session.getQueryString());
        Map<String, List<String>> qs = session.getRequestParameterMap();
        this.coll = db.getCollection(prefix + collname /*qs.get("coll").get(0)*/);
        this.token = qs.get("token").get(0);
        System.out.println("token " + token+" "+this.coll.getName());
        email = tokens.get(token);

        rights = new Rights(email);

        if (email!=null){
            if (conns.containsKey(email))
                conns.get(email).add(session);
            else
                conns.put(email, new HashSet<Session>(){{this.add(ws);}});
        }
        //session.getBasicRemote().sendText(JSON.serialize(new BasicDBObject("type", "auth").append("doc", userToken)));
    }

    @OnClose
    public void onClose(Session session) {
        if (email!=null)
            conns.get(email).remove(session);
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

                c.control(args, rights);

                Class[] types = new Class[args.length];
                for (int i=0; i<args.length; i++){
                    if ( args[i]==null) types[i] = DBObject.class;
                    else {Class cls = args[i].getClass();
                        types[i] = cls.equals(BasicDBObject.class)?DBObject.class : cls.equals(Boolean.class)?boolean.class : cls;//(Class) args.get(i).getClass().getGenericInterfaces()[0];
                    }
                }

                //System.out.println("r "+args[0]);
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

                }else if(obj instanceof DBObject){//worth broadcasting //check null?
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

class Rights {
    String email;
    public Rights(final String email){
        this.email = email;
        if (email!=null){
            _canRead.add(new BasicDBObject("_canRead", new BasicDBObject("$in", new BasicDBList() {{this.add(email);}})));
            _canUpsert.add(new BasicDBObject("_canUpsert", new BasicDBObject("$in", new BasicDBList() {{this.add(email);}})));
            _canRemove.add(new BasicDBObject("_canRemove", new BasicDBObject("$in", new BasicDBList() {{this.add(email);}})));
        }
    }

    BasicDBList _canRead = new BasicDBList(){{
        this.add(new BasicDBObject("_canRead", null));
        this.add(new BasicDBObject("_canRead", new BasicDBObject("$size", 0)));
    }};
    BasicDBList _canUpsert = new BasicDBList(){{
        this.add(new BasicDBObject("_canUpsert", null));
        this.add(new BasicDBObject("_canUpsert", new BasicDBObject("$size", 0)));
    }};
    BasicDBList _canRemove = new BasicDBList(){{
        this.add(new BasicDBObject("_canRemove", null));
        this.add(new BasicDBObject("_canRemove", new BasicDBObject("$size", 0)));
    }};
}



enum Access implements Control {
    find {
        public void control(Object[] a, final Rights rights){
            BasicDBObject q = (BasicDBObject)a[0];
            if (q.containsField("$query")){
                BasicDBObject q2 = (BasicDBObject) q.get("$query");
                if (q2.containsField("$or")){
                    BasicDBList and=new BasicDBList();
                    and.add(q2);
                    and.add(new BasicDBObject("$or",rights._canRead));
                    q2=new BasicDBObject("$and", and);
                }else
                    q2.put("$or", rights._canRead);
            }else{
                if (q.containsField("$or")){
                    BasicDBList and=new BasicDBList();
                    and.add(q);
                    and.add(new BasicDBObject("$or",rights._canRead));
                    q=new BasicDBObject("$and", and);
                }else
                    q.put("$or", rights._canRead);
            }
        }
    },
    insert {
        public void control(Object[] a, Rights rights){
            if(rights.email==null){
                if (a[0]  instanceof BasicDBList){//@TODO use a proper json lib
                    BasicDBList a_ = (BasicDBList) a[0];
                    for (Object o : a_)
                        removeSpecialFields(o);
                    a[0] = a_.toArray(new DBObject[a_.size()]);

                }else{
                    removeSpecialFields(a[0]);
                }
            }
        }
    },
    remove {
        public void control(Object[] a, Rights rights){
            BasicDBObject q = (BasicDBObject)a[0];
            if (q.containsField("$or")){
                BasicDBList and=new BasicDBList();
                and.add(q);
                and.add(new BasicDBObject("$or",rights._canRemove));
                q=new BasicDBObject("$and", and);
            }else
                q.put("$or", rights._canRemove);
        }
    },
    update {
        public void control(Object[] a, Rights rights){
            BasicDBObject q = (BasicDBObject)a[0];
            if (q.containsField("$or")){
                BasicDBList and=new BasicDBList();
                and.add(q);
                and.add(new BasicDBObject("$or",rights._canUpsert));
                q=new BasicDBObject("$and", and);
            }else
                q.put("$or", rights._canUpsert);

            if ( rights.email==null){
                if (a.length<=3)
                    removeSpecialFields(a[a.length-1]);
                else
                    removeSpecialFields(a[a.length-3]);
            }
        }
    },
    findAndModify {
        public void control(Object[] a, Rights rights){Access.update.control(a, rights);}
    },
    findAndRemove {
        public void control(Object[] a,Rights rights){ Access.remove.control(a, rights); }
    },
    save {
        public void control(Object[] a,Rights rights){ Access.insert.control(a, rights); }
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
    public void control(Object[] l, Rights rights);
}
